package io.github.l2terrain.cache;

import io.github.l2terrain.crypto.L2Decryptor;
import io.github.l2terrain.utils.TextureUtils;
import io.github.l2terrain.utils.UnrealPackageUtils;
import net.shrimpworks.unreal.packages.Package;
import net.shrimpworks.unreal.packages.PackageReader;
import net.shrimpworks.unreal.packages.entities.Export;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Global cache for terrain data collected from all map files.
 * 
 * This cache is built in a first pass over all .unr files to collect:
 * - DecoTexture → StaticMesh associations
 * - Splatmap → GroundTexture associations
 * - Which tiles reference which textures
 * 
 * The cache can then be used in a second pass to generate complete metadata
 * for each tile, even when textures are shared across tiles.
 */
public class TerrainDataCache {
    
    // Pattern to match deco texture names: XX_YY_DecoNN
    private static final Pattern DECO_PATTERN = Pattern.compile("(\\d+)_(\\d+)_[Dd]eco(\\d+)");
    
    // Pattern to match splatmap texture names: XX_YY_suffix
    private static final Pattern SPLATMAP_PATTERN = Pattern.compile("(\\d+)_(\\d+)_([A-Za-z]\\w*)");
    
    // DecoTexture name → DecoLayerInfo (mesh, package, source tile)
    private final Map<String, DecoLayerInfo> decoTextureCache = new HashMap<>();
    
    // Splatmap name → SplatmapInfo (ground texture, source tile)
    private final Map<String, SplatmapInfo> splatmapCache = new HashMap<>();
    
    // Tile coordinate → list of DecoTexture names used by that tile
    private final Map<String, List<String>> tileDecoLayers = new HashMap<>();
    
    // Tile coordinate → list of Splatmap names used by that tile  
    private final Map<String, List<String>> tileSplatmaps = new HashMap<>();
    
    // All tile coordinates found
    private final Set<String> allTiles = new TreeSet<>();
    
    /**
     * Build the cache by scanning all map files in the given directory.
     */
    public void buildCache(Path mapsFolder) throws IOException {
        List<Path> mapFiles = new ArrayList<>();
        try (var stream = Files.list(mapsFolder)) {
            stream.filter(p -> {
                String name = p.getFileName().toString().toLowerCase();
                return name.matches("\\d+_\\d+\\.unr");
            }).forEach(mapFiles::add);
        }
        
        System.out.println("Building terrain cache from " + mapFiles.size() + " map files...");
        
        int processed = 0;
        for (Path mapFile : mapFiles) {
            try {
                processMapFile(mapFile);
                processed++;
                if (processed % 20 == 0) {
                    System.out.println("  Processed " + processed + "/" + mapFiles.size() + " maps");
                }
            } catch (Exception e) {
                System.err.println("  Error processing " + mapFile.getFileName() + ": " + e.getMessage());
            }
        }
        
        System.out.println("Cache built: " + decoTextureCache.size() + " deco textures, " 
            + splatmapCache.size() + " splatmaps from " + allTiles.size() + " tiles");
    }
    
    private void processMapFile(Path mapFile) throws IOException {
        String filename = mapFile.getFileName().toString();
        Matcher coordMatcher = Pattern.compile("(\\d+)_(\\d+)\\.unr").matcher(filename.toLowerCase());
        if (!coordMatcher.matches()) return;
        
        int tileX = Integer.parseInt(coordMatcher.group(1));
        int tileY = Integer.parseInt(coordMatcher.group(2));
        String tileKey = tileX + "_" + tileY;
        allTiles.add(tileKey);
        
        Path tempFile = Files.createTempFile("l2map_", ".unr");
        try {
            L2Decryptor.decryptFile(mapFile, tempFile);
            extractAssociations(tempFile, tileKey);
        } finally {
            Files.deleteIfExists(tempFile);
        }
    }
    
    private void extractAssociations(Path packagePath, String tileKey) throws IOException {
        try (Package pkg = new Package(new PackageReader(packagePath))) {
            // Build reference lookup tables
            Map<Integer, String> refNames = new HashMap<>();
            Map<Integer, String> refClasses = new HashMap<>();
            Map<Integer, String> refPackages = new HashMap<>();
            
            for (int i = 0; i < pkg.imports.length; i++) {
                refNames.put(-(i + 1), pkg.imports[i].name.name);
                refClasses.put(-(i + 1), pkg.imports[i].className.name);
                refPackages.put(-(i + 1), pkg.imports[i].classPackage.name);
            }
            for (int i = 0; i < pkg.exports.length; i++) {
                refNames.put(i + 1, pkg.exports[i].name.name);
                refClasses.put(i + 1, pkg.exports[i].classIndex.get().name().name);
            }
            
            // Find TerrainInfo export and parse its raw data
            for (Export export : pkg.exports) {
                String className = export.classIndex.get().name().name;
                if (!className.equals("TerrainInfo")) continue;
                
                PackageReader reader = UnrealPackageUtils.getPackageReader(pkg);
                reader.moveTo(export.pos);
                byte[] data = new byte[export.size];
                reader.readBytes(data, 0, export.size);
                
                // Scan for all object references
                List<RefInfo> refs = scanForReferences(data, refNames, refClasses, refPackages);
                
                // Extract DecoLayer associations
                extractDecoLayers(refs, tileKey);
                
                // Extract splatmap associations
                extractSplatmaps(refs, tileKey);
                
                break;
            }
        } catch (Exception e) {
            throw new IOException("Failed to extract associations: " + e.getMessage(), e);
        }
    }
    
    private List<RefInfo> scanForReferences(byte[] data, Map<Integer, String> refNames,
                                             Map<Integer, String> refClasses, 
                                             Map<Integer, String> refPackages) {
        List<RefInfo> refs = new ArrayList<>();
        Set<Integer> usedOffsets = new HashSet<>();
        
        for (int i = 0; i < data.length - 1; i++) {
            int idx = TextureUtils.readCompactIndex(data, i);
            int len = TextureUtils.compactIndexLength(idx);
            
            if (idx != 0 && refNames.containsKey(idx)) {
                boolean overlaps = false;
                for (int j = i; j < i + len; j++) {
                    if (usedOffsets.contains(j)) {
                        overlaps = true;
                        break;
                    }
                }
                if (!overlaps) {
                    for (int j = i; j < i + len; j++) {
                        usedOffsets.add(j);
                    }
                    refs.add(new RefInfo(i, idx, refNames.get(idx), 
                        refClasses.get(idx), refPackages.get(idx)));
                }
            }
        }
        
        return refs;
    }
    
    private void extractDecoLayers(List<RefInfo> refs, String sourceTile) {
        List<String> tileDecos = new ArrayList<>();
        
        for (int i = 0; i < refs.size(); i++) {
            RefInfo ref = refs.get(i);
            if (!"Texture".equals(ref.className)) continue;
            
            Matcher decoMatcher = DECO_PATTERN.matcher(ref.name);
            if (!decoMatcher.matches()) continue;
            
            String textureName = ref.name;
            tileDecos.add(textureName);
            
            // Look for the next StaticMesh reference within ~20 bytes
            String meshName = null;
            String meshPackage = null;
            for (int j = i + 1; j < refs.size(); j++) {
                RefInfo next = refs.get(j);
                if (next.offset > ref.offset + 20) break;
                
                if ("StaticMesh".equals(next.className)) {
                    meshName = next.name;
                    meshPackage = next.packageName;
                    break;
                }
            }
            
            // Only add/update if we found a mesh association
            if (meshName != null) {
                DecoLayerInfo existing = decoTextureCache.get(textureName);
                if (existing == null || existing.meshName == null) {
                    decoTextureCache.put(textureName, new DecoLayerInfo(textureName, meshName, meshPackage, sourceTile));
                }
            } else if (!decoTextureCache.containsKey(textureName)) {
                // Add without mesh for now
                decoTextureCache.put(textureName, new DecoLayerInfo(textureName, null, null, sourceTile));
            }
        }
        
        if (!tileDecos.isEmpty()) {
            tileDecoLayers.computeIfAbsent(sourceTile, k -> new ArrayList<>()).addAll(tileDecos);
        }
    }
    
    private void extractSplatmaps(List<RefInfo> refs, String sourceTile) {
        List<String> tileSplats = new ArrayList<>();
        
        for (int i = 0; i < refs.size(); i++) {
            RefInfo ref = refs.get(i);
            if (!"Texture".equals(ref.className)) continue;
            
            Matcher splatMatcher = SPLATMAP_PATTERN.matcher(ref.name);
            if (!splatMatcher.matches()) continue;
            
            String suffix = splatMatcher.group(3);
            if (suffix.toLowerCase().startsWith("deco")) continue;
            
            String splatmapName = ref.name;
            tileSplats.add(splatmapName);
            
            // Look backwards for the ground texture (within ~20 bytes before)
            String groundTexture = null;
            for (int j = i - 1; j >= 0; j--) {
                RefInfo prev = refs.get(j);
                if (ref.offset - prev.offset > 20) break;
                
                if ("Texture".equals(prev.className)) {
                    // Make sure it's not another splatmap
                    if (!prev.name.matches("\\d+_\\d+.*")) {
                        groundTexture = prev.name;
                        break;
                    }
                }
            }
            
            // Add/update cache
            if (groundTexture != null) {
                SplatmapInfo existing = splatmapCache.get(splatmapName);
                if (existing == null || existing.groundTexture == null) {
                    splatmapCache.put(splatmapName, new SplatmapInfo(splatmapName, groundTexture, sourceTile));
                }
            } else if (!splatmapCache.containsKey(splatmapName)) {
                splatmapCache.put(splatmapName, new SplatmapInfo(splatmapName, null, sourceTile));
            }
        }
        
        if (!tileSplats.isEmpty()) {
            tileSplatmaps.computeIfAbsent(sourceTile, k -> new ArrayList<>()).addAll(tileSplats);
        }
    }
    
    // --- Accessors ---
    
    public DecoLayerInfo getDecoLayerInfo(String textureName) {
        return decoTextureCache.get(textureName);
    }
    
    public SplatmapInfo getSplatmapInfo(String splatmapName) {
        return splatmapCache.get(splatmapName);
    }
    
    public List<String> getTileDecoLayers(String tileKey) {
        return tileDecoLayers.getOrDefault(tileKey, Collections.emptyList());
    }
    
    public List<String> getTileSplatmaps(String tileKey) {
        return tileSplatmaps.getOrDefault(tileKey, Collections.emptyList());
    }
    
    public Set<String> getAllTiles() {
        return Collections.unmodifiableSet(allTiles);
    }
    
    public Map<String, DecoLayerInfo> getAllDecoLayers() {
        return Collections.unmodifiableMap(decoTextureCache);
    }
    
    public Map<String, SplatmapInfo> getAllSplatmaps() {
        return Collections.unmodifiableMap(splatmapCache);
    }
    
    // --- Inner classes ---
    
    private static class RefInfo {
        int offset;
        String name;
        String className;
        String packageName;
        
        RefInfo(int offset, int index, String name, String className, String packageName) {
            this.offset = offset;
            this.name = name;
            this.className = className;
            this.packageName = packageName;
        }
    }
    
    /**
     * Information about a DecoLayer texture and its associated mesh.
     */
    public static class DecoLayerInfo {
        public final String textureName;
        public final String meshName;
        public final String meshPackage;
        public final String sourceTile;  // Which tile's TerrainInfo defined this association
        
        public DecoLayerInfo(String textureName, String meshName, String meshPackage, String sourceTile) {
            this.textureName = textureName;
            this.meshName = meshName;
            this.meshPackage = meshPackage;
            this.sourceTile = sourceTile;
        }
    }
    
    /**
     * Information about a splatmap texture and its associated ground texture.
     */
    public static class SplatmapInfo {
        public final String splatmapName;
        public final String groundTexture;
        public final String sourceTile;
        
        public SplatmapInfo(String splatmapName, String groundTexture, String sourceTile) {
            this.splatmapName = splatmapName;
            this.groundTexture = groundTexture;
            this.sourceTile = sourceTile;
        }
    }
}
