package io.github.l2terrain.tools;

import io.github.l2terrain.crypto.L2Decryptor;
import net.shrimpworks.unreal.packages.Package;
import net.shrimpworks.unreal.packages.PackageReader;
import net.shrimpworks.unreal.packages.entities.Export;
import net.shrimpworks.unreal.packages.entities.ExportedEntry;
import net.shrimpworks.unreal.packages.entities.ExportedObject;
import net.shrimpworks.unreal.packages.entities.Import;
import net.shrimpworks.unreal.packages.entities.ObjectReference;
import net.shrimpworks.unreal.packages.entities.properties.ArrayProperty;
import net.shrimpworks.unreal.packages.entities.properties.ObjectProperty;
import net.shrimpworks.unreal.packages.entities.properties.Property;
import net.shrimpworks.unreal.packages.entities.properties.StructProperty;
import net.shrimpworks.unreal.packages.entities.objects.Texture;

import java.lang.reflect.Field;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;

/**
 * Tool to analyze Lineage 2 map files and understand terrain structure.
 * Run with: java -cp build/libs/l2terrain-1.0.0-all.jar io.github.l2terrain.tools.MapAnalyzer <mapfile>
 */
public class MapAnalyzer {
    
    public static void main(String[] args) throws Exception {
        if (args.length < 1) {
            System.out.println("Usage: MapAnalyzer <mapfile.unr>");
            System.exit(1);
        }
        
        Path mapFile = Path.of(args[0]);
        System.out.println("Analyzing: " + mapFile);
        
        // Decrypt to temp file
        Path tempFile = Files.createTempFile("l2map_", ".unr");
        try {
            L2Decryptor.decryptFile(mapFile, tempFile);
            analyzePackage(tempFile);
        } finally {
            Files.deleteIfExists(tempFile);
        }
    }
    
    private static void analyzePackage(Path packagePath) throws Exception {
        try (Package pkg = new Package(new PackageReader(packagePath))) {
            System.out.println("\n=== PACKAGE INFO ===");
            System.out.println("Version: " + pkg.version);
            System.out.println("Names: " + pkg.names.length);
            System.out.println("Exports: " + pkg.exports.length);
            System.out.println("Imports: " + pkg.imports.length);
            
            // Count exports by class
            Map<String, Integer> classCounts = new HashMap<>();
            for (Export export : pkg.exports) {
                String className = export.classIndex.get().name().name;
                classCounts.merge(className, 1, Integer::sum);
            }
            
            System.out.println("\n=== EXPORTS BY CLASS ===");
            classCounts.entrySet().stream()
                .sorted((a, b) -> b.getValue().compareTo(a.getValue()))
                .forEach(e -> System.out.printf("  %s: %d%n", e.getKey(), e.getValue()));
            
            // Look for terrain-related exports
            System.out.println("\n=== TERRAIN-RELATED EXPORTS ===");
            for (Export export : pkg.exports) {
                String className = export.classIndex.get().name().name;
                String name = export.name.name;
                
                if (className.equals("TerrainInfo")) {
                    
                    System.out.printf("\n[%s] %s (size: %d, pos: %d)%n", 
                        className, name, export.size, export.pos);
                    
                    try {
                        ExportedObject obj = null;
                        if (export instanceof ExportedObject eo) {
                            obj = eo;
                        } else if (export instanceof ExportedEntry ee) {
                            obj = ee.asObject();
                        }
                        if (obj != null) {
                            var unrealObj = pkg.object(obj);
                            if (unrealObj != null && unrealObj.properties != null) {
                                System.out.println("  Properties:");
                                for (Property prop : unrealObj.properties) {
                                    String pn = prop.name.name;
                                    // Show Layers with full detail including texture references
                                    if (pn.equals("Layers")) {
                                        System.out.printf("\n  === LAYERS (terrain blend layers) ===%n");
                                        // Try to get the struct details
                                        dumpLayerProperty(prop, pkg);
                                    } else if (pn.equals("DecoLayers")) {
                                        System.out.printf("\n  === DECOLAYERS (decoration layers) ===%n");
                                        if (prop instanceof ArrayProperty ap) {
                                            for (int i = 0; i < ap.values.size(); i++) {
                                                System.out.printf("      [%d] %s%n", i, ap.values.get(i));
                                            }
                                        }
                                    } else if (!pn.equals("QuadVisibilityBitmap") && 
                                               !pn.equals("EdgeTurnBitmap") &&
                                               !pn.equals("QuadVisibilityBitmapOrig") &&
                                               !pn.equals("EdgeTurnBitmapOrig")) {
                                        // Skip large bitmap arrays, show everything else
                                        System.out.printf("    %s = %s%n", pn, formatProperty(prop));
                                    }
                                }
                            }
                        }
                    } catch (Exception e) {
                        System.out.println("  (Could not read properties: " + e.getMessage() + ")");
                        e.printStackTrace();
                    }
                }
            }
            
            // Show texture imports organized by pattern
            System.out.println("\n=== TEXTURE IMPORTS ===");
            System.out.println("  Ground textures (GOS/GOST/GOG/etc):");
            for (Import imp : pkg.imports) {
                String name = imp.name.name;
                String className = imp.className.name;
                if (className.equals("Texture") && 
                    (name.startsWith("GO") || name.equals("layer0") || 
                     name.matches("[A-Za-z]{2,4}_\\d+.*"))) {
                    int idx = -getImportIndex(pkg, imp);
                    System.out.printf("    [%d] %s%n", idx, name);
                }
            }
            
            System.out.println("\n  Splatmaps (XX_YY_suffix):");
            for (Import imp : pkg.imports) {
                String name = imp.name.name;
                String className = imp.className.name;
                if (className.equals("Texture") && name.matches("\\d+_\\d+_.*")) {
                    int idx = -getImportIndex(pkg, imp);
                    System.out.printf("    [%d] %s%n", idx, name);
                }
            }
        }
    }
    
    private static int getImportIndex(Package pkg, Import imp) {
        for (int i = 0; i < pkg.imports.length; i++) {
            if (pkg.imports[i] == imp) return i + 1;  // 1-indexed, negated for imports
        }
        return 0;
    }
    
    private static void dumpLayerProperty(Property prop, Package pkg) {
        System.out.printf("    Property type: %s%n", prop.getClass().getSimpleName());
        
        // Try to read internal structure via reflection
        try {
            // Dump all fields of the property for analysis
            System.out.println("    Raw fields:");
            for (Field f : prop.getClass().getDeclaredFields()) {
                f.setAccessible(true);
                Object val = f.get(prop);
                if (val instanceof ObjectReference or) {
                    String refName = resolveObjectRef(pkg, or.index);
                    System.out.printf("      %s: ObjectRef[%d] -> %s%n", f.getName(), or.index, refName);
                } else if (val != null && !f.getName().equals("name")) {
                    String valStr = val.toString();
                    if (valStr.length() > 100) valStr = valStr.substring(0, 100) + "...";
                    System.out.printf("      %s: %s%n", f.getName(), valStr);
                }
            }
            
            // Also check parent class fields
            Class<?> parentClass = prop.getClass().getSuperclass();
            if (parentClass != null && parentClass != Object.class) {
                System.out.println("    Parent class fields:");
                for (Field f : parentClass.getDeclaredFields()) {
                    f.setAccessible(true);
                    Object val = f.get(prop);
                    if (val instanceof ObjectReference or) {
                        String refName = resolveObjectRef(pkg, or.index);
                        System.out.printf("      %s: ObjectRef[%d] -> %s%n", f.getName(), or.index, refName);
                    }
                }
            }
        } catch (Exception e) {
            System.out.printf("    Error reading property: %s%n", e.getMessage());
        }
    }
    
    private static String resolveObjectRef(Package pkg, int index) {
        if (index == 0) return "(None)";
        if (index < 0) {
            // Import reference
            int impIdx = -index - 1;
            if (impIdx >= 0 && impIdx < pkg.imports.length) {
                return pkg.imports[impIdx].name.name + " (import)";
            }
        } else {
            // Export reference
            int expIdx = index - 1;
            if (expIdx >= 0 && expIdx < pkg.exports.length) {
                return pkg.exports[expIdx].name.name + " (export)";
            }
        }
        return "(unknown ref " + index + ")";
    }
    
    private static String formatProperty(Property prop) {
        String value = prop.toString();
        // Truncate long values but keep more context
        if (value.length() > 200) {
            return value.substring(0, 200) + "...";
        }
        return value;
    }
}
