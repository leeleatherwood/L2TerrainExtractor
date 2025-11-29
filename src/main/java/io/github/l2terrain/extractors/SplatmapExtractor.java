package io.github.l2terrain.extractors;

import io.github.l2terrain.crypto.L2Decryptor;
import io.github.l2terrain.utils.TextureUtils;
import io.github.l2terrain.utils.UnrealPackageUtils;
import net.shrimpworks.unreal.packages.Package;
import net.shrimpworks.unreal.packages.PackageReader;
import net.shrimpworks.unreal.packages.entities.Export;
import net.shrimpworks.unreal.packages.entities.ExportedEntry;
import net.shrimpworks.unreal.packages.entities.ExportedObject;
import net.shrimpworks.unreal.packages.entities.objects.Texture;
import net.shrimpworks.unreal.packages.entities.objects.TextureBase;
import net.shrimpworks.unreal.packages.entities.properties.IntegerProperty;
import net.shrimpworks.unreal.packages.entities.properties.Property;

import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Extractor for terrain splatmaps (alpha/blend maps) from T_XX_YY.utx packages.
 * 
 * <p>These textures (named XX_YY_suffix like 23_16_NG1, 23_16_C) control how
 * different terrain textures are blended together on each tile. They are
 * typically DXT1 compressed at 512x512 or 1024x1024.</p>
 */
public class SplatmapExtractor {
    
    /** Pattern to match tile package names: T_XX_YY.utx or t_XX_YY.utx */
    private static final Pattern TILE_PKG_PATTERN = Pattern.compile("[Tt]_(\\d+)_(\\d+)\\.utx", Pattern.CASE_INSENSITIVE);
    
    /** Pattern to match splatmap texture names: XX_YY_suffix (but not just XX_YY which is heightmap) */
    private static final Pattern SPLATMAP_PATTERN = Pattern.compile("(\\d+)_(\\d+)_([A-Za-z]\\w*)");
    
    /**
     * Information about an extracted splatmap.
     */
    public static class SplatmapInfo {
        public final String fileName;
        public final String originalName;
        public final String suffix;
        public final BufferedImage image;
        public final int width;
        public final int height;
        public final int layerIndex;
        
        public SplatmapInfo(String fileName, String originalName, String suffix, 
                           BufferedImage image, int width, int height, int layerIndex) {
            this.fileName = fileName;
            this.originalName = originalName;
            this.suffix = suffix;
            this.image = image;
            this.width = width;
            this.height = height;
            this.layerIndex = layerIndex;
        }
    }
    
    /**
     * Extract all splatmaps from T_XX_YY.utx packages in the given directory.
     * 
     * @param inputFolder directory containing T_XX_YY.utx packages
     * @return Map of tile name (e.g., "23_16") to list of splatmap info
     * @throws IOException if extraction fails
     */
    public Map<String, List<SplatmapInfo>> extractAll(Path inputFolder) throws IOException {
        Map<String, List<SplatmapInfo>> results = new TreeMap<>();
        
        // Find all T_XX_YY.utx packages
        List<Path> tilePackages = new ArrayList<>();
        try (var stream = Files.list(inputFolder)) {
            stream.filter(p -> {
                String name = p.getFileName().toString();
                return TILE_PKG_PATTERN.matcher(name).matches();
            }).forEach(tilePackages::add);
        }
        
        if (tilePackages.isEmpty()) {
            System.out.println("No T_XX_YY.utx packages found in " + inputFolder);
            return results;
        }
        
        System.out.println("Found " + tilePackages.size() + " tile packages");
        
        int processed = 0;
        for (Path pkg : tilePackages) {
            try {
                extractFromPackage(pkg, results);
                processed++;
                if (processed % 20 == 0) {
                    System.out.print(".");
                }
            } catch (Exception e) {
                System.out.println("\n  Error processing " + pkg.getFileName() + ": " + e.getMessage());
            }
        }
        System.out.println();
        
        return results;
    }
    
    private void extractFromPackage(Path packagePath, Map<String, List<SplatmapInfo>> results) throws IOException {
        String pkgName = packagePath.getFileName().toString();
        Matcher pkgMatcher = TILE_PKG_PATTERN.matcher(pkgName);
        if (!pkgMatcher.matches()) return;
        
        int tileX = Integer.parseInt(pkgMatcher.group(1));
        int tileY = Integer.parseInt(pkgMatcher.group(2));
        String tileName = String.format("%d_%d", tileX, tileY);
        
        // Decrypt to temp file
        Path tempFile = Files.createTempFile("l2tile_", ".utx");
        
        try {
            L2Decryptor.decryptFile(packagePath, tempFile);
            
            try (Package pkg = new Package(new PackageReader(tempFile))) {
                List<SplatmapInfo> splatmaps = new ArrayList<>();
                int layerIndex = 0;
                
                for (Export export : pkg.exports) {
                    String className = export.classIndex.get().name().name;
                    if (!className.equals("Texture")) continue;
                    
                    String texName = export.name.name;
                    
                    // Skip the heightmap (just XX_YY without suffix)
                    if (texName.equals(tileName)) continue;
                    
                    // Match splatmap pattern: XX_YY_suffix
                    Matcher matcher = SPLATMAP_PATTERN.matcher(texName);
                    if (!matcher.matches()) continue;
                    
                    int x = Integer.parseInt(matcher.group(1));
                    int y = Integer.parseInt(matcher.group(2));
                    String suffix = matcher.group(3);
                    
                    // Verify it's for this tile
                    if (x != tileX || y != tileY) continue;
                    
                    try {
                        ExportedObject obj = null;
                        if (export instanceof ExportedObject eo) {
                            obj = eo;
                        } else if (export instanceof ExportedEntry ee) {
                            obj = ee.asObject();
                        }
                        
                        if (obj == null) continue;
                        
                        var texObj = pkg.object(obj);
                        if (!(texObj instanceof Texture tex)) continue;
                        
                        // Get texture format and dimensions
                        TextureBase.Format format = tex.format();
                        int width = 256, height = 256; // defaults
                        
                        for (Property prop : tex.properties) {
                            if (prop.name.name.equals("USize") && prop instanceof IntegerProperty ip) {
                                width = ip.value;
                            } else if (prop.name.name.equals("VSize") && prop instanceof IntegerProperty ip) {
                                height = ip.value;
                            }
                        }
                        
                        // Extract the texture using shared utilities
                        byte[] exportData = UnrealPackageUtils.readExportData(tex, obj);
                        BufferedImage image = extractTextureByFormat(exportData, format, width, height);
                        
                        if (image == null) {
                            System.out.println("\n    Warning: Could not extract " + texName);
                            continue;
                        }
                        
                        // Create output filename: XX_YY_splatmapN_layerN.png
                        String fileName = String.format("%d_%d_splatmap%d_layer%d.png", 
                            tileX, tileY, layerIndex, layerIndex);
                        
                        splatmaps.add(new SplatmapInfo(fileName, texName, suffix, image, width, height, layerIndex));
                        layerIndex++;
                        
                    } catch (Exception e) {
                        System.out.println("\n    Error extracting " + texName + ": " + e.getMessage());
                    }
                }
                
                if (!splatmaps.isEmpty()) {
                    results.computeIfAbsent(tileName, k -> new ArrayList<>()).addAll(splatmaps);
                }
            }
        } finally {
            Files.deleteIfExists(tempFile);
        }
    }
    
    /**
     * Extract texture image based on format using shared TextureUtils.
     */
    private BufferedImage extractTextureByFormat(byte[] exportData, TextureBase.Format format, 
                                                  int width, int height) {
        return switch (format) {
            case DXT1 -> TextureUtils.extractDXT1(exportData, width, height);
            case DXT3 -> TextureUtils.extractDXT3(exportData, width, height);
            case RGBA8 -> TextureUtils.extractRGBA8(exportData, width, height);
            case PALETTE_8_BIT -> TextureUtils.extractP8(exportData, width, height);
            case G16 -> TextureUtils.extractG16(exportData, width, height);
            default -> {
                System.out.println("    Unsupported format: " + format);
                yield null;
            }
        };
    }
}
