package io.github.l2terrain.cache;

import io.github.l2terrain.crypto.L2Decryptor;
import io.github.l2terrain.utils.TextureUtils;
import io.github.l2terrain.utils.UnrealPackageUtils;
import net.shrimpworks.unreal.packages.Package;
import net.shrimpworks.unreal.packages.PackageReader;
import net.shrimpworks.unreal.packages.entities.Export;
import net.shrimpworks.unreal.packages.entities.ExportedEntry;
import net.shrimpworks.unreal.packages.entities.ExportedObject;
import net.shrimpworks.unreal.packages.entities.properties.Property;
import net.shrimpworks.unreal.packages.entities.properties.StructProperty;

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
 * - Splatmap → GroundTexture associations (in correct Layers array order)
 * - Which tiles reference which textures
 * 
 * The cache uses raw byte scanning of TerrainInfo to find layer associations.
 * The splatmaps are stored in byte-offset order, which matches the Layers
 * array order in the binary data (layer 0 appears first, then layer 1, etc).
 */
public class TerrainDataCache {
    
    // Pattern to match splatmap texture names: XX_YY_suffix
    private static final Pattern SPLATMAP_PATTERN = Pattern.compile("(\\d+)_(\\d+)_([A-Za-z]\\w*)");
    
    // Pattern to match deco texture names: XX_YY_DecoNN
    private static final Pattern DECO_PATTERN = Pattern.compile("(\\d+)_(\\d+)_[Dd]eco(\\d+)");
    
    // DecoTexture name → DecoLayerInfo (mesh, package, source tile)
    private final Map<String, DecoLayerInfo> decoTextureCache = new HashMap<>();
    
    // Splatmap name → SplatmapInfo (ground texture, source tile)
    private final Map<String, SplatmapInfo> splatmapCache = new HashMap<>();
    
    // Tile coordinate → list of DecoTexture names used by that tile
    private final Map<String, List<String>> tileDecoLayers = new HashMap<>();
    
    // Tile coordinate → ORDERED list of Splatmap names (in layer order!)
    private final Map<String, List<String>> tileSplatmaps = new HashMap<>();
    
    // Tile coordinate → terrain world location
    private final Map<String, TerrainLocation> tileLocations = new HashMap<>();
    
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
                
                // Extract Location property using proper property parsing
                ExportedObject expObj = null;
                if (export instanceof ExportedObject eo) {
                    expObj = eo;
                } else if (export instanceof ExportedEntry ee) {
                    expObj = ee.asObject();
                }
                
                if (expObj != null) {
                    try {
                        var obj = pkg.object(expObj);
                        if (obj != null && obj.properties != null) {
                            for (Property prop : obj.properties) {
                                if (prop.name.name.equals("Location") && prop instanceof StructProperty.VectorProperty vec) {
                                    tileLocations.put(tileKey, new TerrainLocation(vec.x, vec.y, vec.z));
                                    break;
                                }
                            }
                        }
                    } catch (Exception e) {
                        // Ignore parsing errors for location, continue with byte scanning
                    }
                }
                
                // Also do raw byte scanning for splatmaps and deco layers
                PackageReader reader = UnrealPackageUtils.getPackageReader(pkg);
                reader.moveTo(export.pos);
                byte[] data = new byte[export.size];
                reader.readBytes(data, 0, export.size);
                
                // Scan for all object references
                List<RefInfo> refs = scanForReferences(data, refNames, refClasses, refPackages);
                
                // Extract splatmap associations - these come out in byte order which IS layer order
                extractSplatmapsInOrder(refs, tileKey);
                
                // Extract DecoLayer associations
                extractDecoLayers(refs, tileKey);
                
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
    
    // Maximum byte offset to scan for Layers array.
    // The Layers array is always at the beginning of TerrainInfo data.
    // Each LayerInfo struct is ~341 bytes, with max ~16 layers = ~5500 bytes.
    // Using 6000 as a safe upper bound to capture all layers but exclude false positives
    // from the heightmap data and other internal structures that appear later.
    private static final int LAYERS_ARRAY_MAX_OFFSET = 6000;
    
    /**
     * Extract splatmaps in the order they appear in the binary data.
     * Only adds splatmaps that have an associated ground texture (within ~20 bytes before),
     * which indicates they are part of the Layers array structure.
     * Also limits scanning to the first portion of TerrainInfo where the Layers array lives.
     * This IS the layer order - layer 0 appears first, layer 1 second, etc.
     */
    private void extractSplatmapsInOrder(List<RefInfo> refs, String tileKey) {
        // Sort refs by offset to ensure we get them in byte order (which IS layer order)
        refs.sort(Comparator.comparingInt(r -> r.offset));
        
        List<String> orderedSplatmaps = new ArrayList<>();
        Set<String> seenSplatmaps = new HashSet<>();  // Track already-seen splatmaps
        
        for (int i = 0; i < refs.size(); i++) {
            RefInfo ref = refs.get(i);
            
            // CRITICAL: Only look in the first portion of TerrainInfo
            // The Layers array is always at the beginning. Later offsets are
            // heightmap data, layer blend data, etc. which cause false positives.
            if (ref.offset > LAYERS_ARRAY_MAX_OFFSET) continue;
            
            if (!"Texture".equals(ref.className)) continue;
            
            Matcher splatMatcher = SPLATMAP_PATTERN.matcher(ref.name);
            if (!splatMatcher.matches()) continue;
            
            String suffix = splatMatcher.group(3);
            if (suffix.toLowerCase().startsWith("deco")) continue;
            
            String splatmapName = ref.name;
            
            // Skip if we've already seen this splatmap - only count first occurrence!
            // This prevents duplicates from embedded data structures
            if (seenSplatmaps.contains(splatmapName)) continue;
            
            // Look backwards for the ground texture (within ~20 bytes before)
            // This is key - Layers array entries have (ground texture, splatmap) pairs
            String groundTexture = null;
            for (int j = i - 1; j >= 0; j--) {
                RefInfo prev = refs.get(j);
                if (ref.offset - prev.offset > 20) break;
                
                if ("Texture".equals(prev.className)) {
                    // Make sure it's not another splatmap or tile-related texture
                    if (!prev.name.matches("\\d+_\\d+.*")) {
                        groundTexture = prev.name;
                        break;
                    }
                }
            }
            
            // ONLY add splatmaps that have an associated ground texture
            // This filters out false positives from random byte matches
            if (groundTexture != null) {
                orderedSplatmaps.add(splatmapName);
                seenSplatmaps.add(splatmapName);
                
                // Update the splatmap cache with ground texture info
                SplatmapInfo existing = splatmapCache.get(splatmapName);
                if (existing == null || existing.groundTexture == null) {
                    splatmapCache.put(splatmapName, new SplatmapInfo(splatmapName, groundTexture, tileKey));
                }
            }
            // Don't add splatmaps without ground texture - they're likely false positives
        }
        
        if (!orderedSplatmaps.isEmpty()) {
            // Store in order - DO NOT use computeIfAbsent().addAll() which could mess up order
            tileSplatmaps.put(tileKey, orderedSplatmaps);
        }
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
                decoTextureCache.put(textureName, new DecoLayerInfo(textureName, null, null, sourceTile));
            }
        }
        
        if (!tileDecos.isEmpty()) {
            tileDecoLayers.put(sourceTile, tileDecos);
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
    
    public TerrainLocation getTileLocation(String tileKey) {
        return tileLocations.get(tileKey);
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
    
    /**
     * Information about a terrain tile's world location.
     */
    public static class TerrainLocation {
        public final float x;
        public final float y;
        public final float z;
        
        public TerrainLocation(float x, float y, float z) {
            this.x = x;
            this.y = y;
            this.z = z;
        }
    }
}
