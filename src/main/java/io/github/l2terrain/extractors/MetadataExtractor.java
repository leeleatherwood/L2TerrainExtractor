package io.github.l2terrain.extractors;

import io.github.l2terrain.cache.TerrainDataCache;
import io.github.l2terrain.cache.TerrainDataCache.DecoLayerInfo;
import io.github.l2terrain.cache.TerrainDataCache.SplatmapInfo;

import java.io.IOException;
import java.io.PrintWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Extracts metadata from Lineage 2 map files to create tile info files.
 * 
 * Uses a two-pass approach:
 * 1. Build a global cache of all DecoTexture→StaticMesh and Splatmap→GroundTexture associations
 * 2. Generate metadata for each tile using the cache (handles cross-tile texture sharing)
 */
public class MetadataExtractor {
    
    private TerrainDataCache cache;
    
    /**
     * Build the global cache from all map files.
     */
    public void buildCache(Path mapsFolder) throws IOException {
        cache = new TerrainDataCache();
        cache.buildCache(mapsFolder);
    }
    
    /**
     * Set an externally built cache.
     */
    public void setCache(TerrainDataCache cache) {
        this.cache = cache;
    }
    
    /**
     * Get the cache (for reuse by other extractors).
     */
    public TerrainDataCache getCache() {
        return cache;
    }
    
    /**
     * Generate metadata for all tiles based on extracted files in the output folder.
     */
    public List<TileMetadata> generateAllMetadata(Path outputFolder) throws IOException {
        List<TileMetadata> results = new ArrayList<>();
        
        if (cache == null) {
            throw new IllegalStateException("Cache not built. Call buildCache() first.");
        }
        
        // Find all tile directories
        List<Path> tileDirs = new ArrayList<>();
        try (var stream = Files.list(outputFolder)) {
            stream.filter(p -> {
                if (!Files.isDirectory(p)) return false;
                String name = p.getFileName().toString();
                return name.matches("\\d+_\\d+");
            }).forEach(tileDirs::add);
        }
        
        System.out.println("Generating metadata for " + tileDirs.size() + " tile directories...");
        
        for (Path tileDir : tileDirs) {
            try {
                TileMetadata meta = generateTileMetadata(tileDir);
                if (meta != null) {
                    Path metaFile = tileDir.resolve(String.format("%d_%d_metadata.txt", meta.tileX, meta.tileY));
                    writeMetadata(meta, metaFile);
                    results.add(meta);
                }
            } catch (Exception e) {
                System.err.println("  Error generating metadata for " + tileDir.getFileName() + ": " + e.getMessage());
            }
        }
        
        return results;
    }
    
    /**
     * Generate metadata for a single tile based on its extracted files.
     */
    private TileMetadata generateTileMetadata(Path tileDir) throws IOException {
        String tileName = tileDir.getFileName().toString();
        Matcher coordMatcher = Pattern.compile("(\\d+)_(\\d+)").matcher(tileName);
        if (!coordMatcher.matches()) return null;
        
        int tileX = Integer.parseInt(coordMatcher.group(1));
        int tileY = Integer.parseInt(coordMatcher.group(2));
        
        TileMetadata meta = new TileMetadata(tileX, tileY);
        
        // Find all detailmap files and look up their associations from the global cache
        try (var stream = Files.list(tileDir)) {
            stream.filter(p -> p.getFileName().toString().contains("_detailmap_"))
                .forEach(p -> {
                    String filename = p.getFileName().toString();
                    // Extract the deco number from filename like "16_25_detailmap_3.png"
                    Matcher m = Pattern.compile("(\\d+)_(\\d+)_detailmap_(\\d+)\\.png").matcher(filename);
                    if (m.matches()) {
                        int decoX = Integer.parseInt(m.group(1));
                        int decoY = Integer.parseInt(m.group(2));
                        int layerNum = Integer.parseInt(m.group(3));
                        
                        // Try different texture name formats to look up in cache
                        DecoLayerInfo info = findDecoLayerInfo(decoX, decoY, layerNum);
                        
                        TileDecoLayerInfo deco = new TileDecoLayerInfo(layerNum);
                        if (info != null) {
                            deco.textureName = info.textureName;
                            deco.meshName = info.meshName;
                            deco.meshPackage = info.meshPackage;
                            deco.sourceTile = info.sourceTile;
                        }
                        meta.decoLayers.add(deco);
                    }
                });
        }
        
        // Find all splatmap files and look up their associations from the global cache
        try (var stream = Files.list(tileDir)) {
            stream.filter(p -> p.getFileName().toString().contains("_splatmap"))
                .forEach(p -> {
                    String filename = p.getFileName().toString();
                    // Extract info from filename like "16_25_splatmap0_layer0.png"
                    Matcher m = Pattern.compile("(\\d+)_(\\d+)_splatmap(\\d+)_layer(\\d+)\\.png").matcher(filename);
                    if (m.matches()) {
                        int splatX = Integer.parseInt(m.group(1));
                        int splatY = Integer.parseInt(m.group(2));
                        int splatIndex = Integer.parseInt(m.group(3));
                        
                        // Look up splatmaps for this tile from cache
                        String tileKey = splatX + "_" + splatY;
                        List<String> tileSplatmaps = cache.getTileSplatmaps(tileKey);
                        
                        TileSplatmapInfo layer = new TileSplatmapInfo(splatIndex);
                        
                        // Try to find matching splatmap by index
                        if (splatIndex < tileSplatmaps.size()) {
                            String splatmapName = tileSplatmaps.get(splatIndex);
                            SplatmapInfo info = cache.getSplatmapInfo(splatmapName);
                            if (info != null) {
                                layer.originalName = splatmapName;
                                layer.groundTexture = info.groundTexture;
                            }
                        }
                        
                        meta.splatmapLayers.add(layer);
                    }
                });
        }
        
        // Sort layers by index
        meta.decoLayers.sort(Comparator.comparingInt(d -> d.layerNum));
        meta.splatmapLayers.sort(Comparator.comparingInt(s -> s.index));
        
        return meta;
    }
    
    /**
     * Find DecoLayerInfo in cache, trying different texture name formats.
     */
    private DecoLayerInfo findDecoLayerInfo(int x, int y, int layerNum) {
        // Try different naming conventions
        String[] formats = {
            "%d_%d_Deco%02d",   // 16_25_Deco01
            "%d_%d_Deco%d",     // 16_25_Deco1
            "%d_%d_deco%02d",   // 16_25_deco01
            "%d_%d_deco%d"      // 16_25_deco1
        };
        
        for (String format : formats) {
            String textureName = String.format(format, x, y, layerNum);
            DecoLayerInfo info = cache.getDecoLayerInfo(textureName);
            if (info != null) {
                return info;
            }
        }
        
        return null;
    }
    
    /**
     * Write metadata to a file.
     */
    public void writeMetadata(TileMetadata meta, Path outputFile) throws IOException {
        try (PrintWriter writer = new PrintWriter(Files.newBufferedWriter(outputFile))) {
            writer.printf("# Terrain Metadata for tile %d_%d%n", meta.tileX, meta.tileY);
            writer.printf("# Generated by L2TerrainExtractor%n%n");
            
            writer.printf("tile_x=%d%n", meta.tileX);
            writer.printf("tile_y=%d%n", meta.tileY);
            writer.println();
            
            writer.println("# Splatmaps (terrain blend layers)");
            writer.println("# Format: splatmap_N=filename,ground_texture_name");
            for (TileSplatmapInfo layer : meta.splatmapLayers) {
                String splatFile = String.format("%d_%d_splatmap%d_layer%d.png", 
                    meta.tileX, meta.tileY, layer.index, layer.index);
                if (layer.groundTexture != null) {
                    writer.printf("splatmap_%d=%s,%s%n", layer.index, splatFile, layer.groundTexture);
                } else {
                    writer.printf("splatmap_%d=%s%n", layer.index, splatFile);
                }
            }
            writer.println();
            
            writer.println("# Detail Layers (DecoLayers)");
            writer.println("# Format: layer_N=detailmap_file,static_mesh_name[,source_tile]");
            for (TileDecoLayerInfo deco : meta.decoLayers) {
                String detailmapFile = String.format("%d_%d_detailmap_%d.png", meta.tileX, meta.tileY, deco.layerNum);
                if (deco.meshName != null) {
                    if (deco.sourceTile != null && !deco.sourceTile.equals(meta.tileX + "_" + meta.tileY)) {
                        // This deco is used by a different tile - include source info
                        writer.printf("layer_%d=%s,%s,from_%s%n", deco.layerNum, detailmapFile, deco.meshName, deco.sourceTile);
                    } else {
                        writer.printf("layer_%d=%s,%s%n", deco.layerNum, detailmapFile, deco.meshName);
                    }
                } else {
                    // Detailmap file exists but no mesh association found in any terrain
                    writer.printf("layer_%d=%s,<unknown>%n", deco.layerNum, detailmapFile);
                }
            }
        }
    }
    
    // --- Data classes ---
    
    /**
     * Represents metadata for a terrain tile.
     */
    public static class TileMetadata {
        public int tileX;
        public int tileY;
        public List<TileSplatmapInfo> splatmapLayers = new ArrayList<>();
        public List<TileDecoLayerInfo> decoLayers = new ArrayList<>();
        
        public TileMetadata(int x, int y) {
            this.tileX = x;
            this.tileY = y;
        }
    }
    
    /**
     * Information about a splatmap layer for a tile.
     */
    public static class TileSplatmapInfo {
        public int index;
        public String originalName;
        public String groundTexture;
        
        public TileSplatmapInfo(int index) {
            this.index = index;
        }
    }
    
    /**
     * Information about a decoration layer for a tile.
     */
    public static class TileDecoLayerInfo {
        public int layerNum;
        public String textureName;
        public String meshName;
        public String meshPackage;
        public String sourceTile;  // Which tile's TerrainInfo defines this association
        
        public TileDecoLayerInfo(int num) {
            this.layerNum = num;
        }
    }
}
