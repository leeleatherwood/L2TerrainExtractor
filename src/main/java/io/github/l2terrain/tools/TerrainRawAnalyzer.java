package io.github.l2terrain.tools;

import io.github.l2terrain.crypto.L2Decryptor;
import net.shrimpworks.unreal.packages.Package;
import net.shrimpworks.unreal.packages.PackageReader;
import net.shrimpworks.unreal.packages.entities.Export;
import net.shrimpworks.unreal.packages.entities.Import;

import java.lang.reflect.Field;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;

/**
 * Raw binary analysis of TerrainInfo to find Layer and DecoLayer associations.
 * This bypasses the property parsing and reads the raw struct data.
 */
public class TerrainRawAnalyzer {
    
    public static void main(String[] args) throws Exception {
        if (args.length < 1) {
            System.out.println("Usage: TerrainRawAnalyzer <mapfile.unr>");
            System.exit(1);
        }
        
        Path mapFile = Path.of(args[0]);
        System.out.println("Analyzing: " + mapFile);
        
        Path tempFile = Files.createTempFile("l2map_", ".unr");
        try {
            L2Decryptor.decryptFile(mapFile, tempFile);
            analyzeTerrainInfo(tempFile);
        } finally {
            Files.deleteIfExists(tempFile);
        }
    }
    
    private static void analyzeTerrainInfo(Path packagePath) throws Exception {
        try (Package pkg = new Package(new PackageReader(packagePath))) {
            // Build reference lookup tables
            Map<Integer, String> refNames = new HashMap<>();
            Map<Integer, String> refClasses = new HashMap<>();
            for (int i = 0; i < pkg.imports.length; i++) {
                refNames.put(-(i + 1), pkg.imports[i].name.name);
                refClasses.put(-(i + 1), pkg.imports[i].className.name);
            }
            for (int i = 0; i < pkg.exports.length; i++) {
                refNames.put(i + 1, pkg.exports[i].name.name);
                refClasses.put(i + 1, pkg.exports[i].classIndex.get().name().name);
            }
            
            // Find TerrainInfo and read raw export data
            for (Export export : pkg.exports) {
                String className = export.classIndex.get().name().name;
                if (!className.equals("TerrainInfo")) continue;
                
                System.out.printf("\n=== TerrainInfo: %s ===%n", export.name.name);
                System.out.printf("Export pos: %d, size: %d%n", export.pos, export.size);
                
                // Read the raw export data
                PackageReader reader = getPackageReader(pkg);
                reader.moveTo(export.pos);
                byte[] data = new byte[export.size];
                reader.readBytes(data, 0, export.size);
                
                // Find all object references that point to known imports/exports
                System.out.println("\n=== Scanning for object references ===");
                
                List<RefInfo> allRefs = new ArrayList<>();
                Set<Integer> usedOffsets = new HashSet<>();
                
                for (int i = 0; i < data.length - 1; i++) {
                    int idx = readCompactIndex(data, i);
                    int len = compactIndexLength(idx);
                    
                    if (idx != 0 && (refClasses.containsKey(idx) || refNames.containsKey(idx))) {
                        // Avoid overlapping reads
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
                            allRefs.add(new RefInfo(i, idx, refNames.get(idx), refClasses.get(idx)));
                        }
                    }
                }
                
                // Group refs by type
                System.out.println("\nTexture references:");
                for (RefInfo ref : allRefs) {
                    if ("Texture".equals(ref.className)) {
                        System.out.printf("  offset %5d: [%4d] %s%n", ref.offset, ref.index, ref.name);
                    }
                }
                
                System.out.println("\nStaticMesh references:");
                for (RefInfo ref : allRefs) {
                    if ("StaticMesh".equals(ref.className)) {
                        System.out.printf("  offset %5d: [%4d] %s%n", ref.offset, ref.index, ref.name);
                    }
                }
                
                // Now try to find patterns: look for Deco textures and their associated meshes
                System.out.println("\n=== DecoLayer Associations ===");
                System.out.println("(Looking for Deco texture -> StaticMesh pairs)");
                
                for (int i = 0; i < allRefs.size(); i++) {
                    RefInfo ref = allRefs.get(i);
                    if (!"Texture".equals(ref.className)) continue;
                    if (ref.name == null || !ref.name.contains("Deco")) continue;
                    
                    // Found a Deco texture - look for the next StaticMesh within 200 bytes
                    for (int j = i + 1; j < allRefs.size(); j++) {
                        RefInfo next = allRefs.get(j);
                        if (next.offset > ref.offset + 200) break;
                        
                        if ("StaticMesh".equals(next.className)) {
                            int distance = next.offset - ref.offset;
                            System.out.printf("  %s (offset %d)%n", ref.name, ref.offset);
                            System.out.printf("    -> %s (offset %d, +%d bytes)%n", next.name, next.offset, distance);
                            break;
                        }
                    }
                }
                
                // Try to find Layer associations (ground texture -> splatmap)
                System.out.println("\n=== Layer Associations ===");
                System.out.println("(Looking for ground texture -> splatmap pairs)");
                
                // Ground textures are things like SL_R1, GOS_102, layer0
                // Splatmaps are XX_YY_suffix
                
                for (int i = 0; i < allRefs.size(); i++) {
                    RefInfo ref = allRefs.get(i);
                    if (!"Texture".equals(ref.className)) continue;
                    if (ref.name == null) continue;
                    
                    // Is this a splatmap? (matches XX_YY_suffix, not Deco)
                    if (!ref.name.matches("\\d+_\\d+_[A-Za-z].*") || ref.name.contains("Deco")) continue;
                    
                    // Look backwards for a ground texture (non-splatmap, non-heightmap)
                    String groundTex = null;
                    int groundOffset = -1;
                    for (int j = i - 1; j >= 0; j--) {
                        RefInfo prev = allRefs.get(j);
                        if (ref.offset - prev.offset > 100) break;
                        
                        if ("Texture".equals(prev.className) && prev.name != null) {
                            // Not a splatmap or heightmap
                            if (!prev.name.matches("\\d+_\\d+.*")) {
                                groundTex = prev.name;
                                groundOffset = prev.offset;
                                break;
                            }
                        }
                    }
                    
                    System.out.printf("  Splatmap: %s (offset %d)%n", ref.name, ref.offset);
                    if (groundTex != null) {
                        System.out.printf("    <- Ground: %s (offset %d, -%d bytes)%n", 
                            groundTex, groundOffset, ref.offset - groundOffset);
                    } else {
                        System.out.println("    <- Ground: (none found)");
                    }
                }
                
                // Show full reference list for debugging
                System.out.println("\n=== All references by offset (for manual inspection) ===");
                for (RefInfo ref : allRefs) {
                    System.out.printf("  %5d: [%4d] %-12s %s%n", 
                        ref.offset, ref.index, 
                        ref.className != null ? ref.className : "?",
                        ref.name != null ? ref.name : "?");
                }
            }
        }
    }
    
    static class RefInfo {
        int offset;
        int index;
        String name;
        String className;
        
        RefInfo(int offset, int index, String name, String className) {
            this.offset = offset;
            this.index = index;
            this.name = name;
            this.className = className;
        }
    }
    
    private static PackageReader getPackageReader(Package pkg) throws Exception {
        Field readerField = Package.class.getDeclaredField("reader");
        readerField.setAccessible(true);
        return (PackageReader) readerField.get(pkg);
    }
    
    private static int readCompactIndex(byte[] data, int offset) {
        if (offset >= data.length) return 0;
        
        int result = 0;
        int shift = 0;
        boolean negative = false;
        
        for (int i = 0; i < 5 && offset + i < data.length; i++) {
            int b = data[offset + i] & 0xFF;
            
            if (i == 0) {
                negative = (b & 0x80) != 0;
                result = b & 0x3F;
                if ((b & 0x40) == 0) return negative ? -result : result;
                shift = 6;
            } else {
                result |= (b & 0x7F) << shift;
                if ((b & 0x80) == 0) return negative ? -result : result;
                shift += 7;
            }
        }
        
        return negative ? -result : result;
    }
    
    private static int compactIndexLength(int value) {
        if (value < 0) value = -value;
        if (value < 0x40) return 1;
        if (value < 0x2000) return 2;
        if (value < 0x100000) return 3;
        if (value < 0x8000000) return 4;
        return 5;
    }
}
