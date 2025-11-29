package io.github.l2terrain.tools;

import io.github.l2terrain.crypto.L2Decryptor;
import net.shrimpworks.unreal.packages.Package;
import net.shrimpworks.unreal.packages.PackageReader;
import net.shrimpworks.unreal.packages.entities.Export;
import net.shrimpworks.unreal.packages.entities.ExportedEntry;
import net.shrimpworks.unreal.packages.entities.ExportedObject;
import net.shrimpworks.unreal.packages.entities.Import;
import net.shrimpworks.unreal.packages.entities.properties.ArrayProperty;
import net.shrimpworks.unreal.packages.entities.properties.Property;

import java.lang.reflect.Field;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;

/**
 * Deep analysis of TerrainInfo to extract Layer and DecoLayer texture/mesh associations.
 */
public class TerrainInfoDumper {
    
    public static void main(String[] args) throws Exception {
        if (args.length < 1) {
            System.out.println("Usage: TerrainInfoDumper <mapfile.unr>");
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
            // Build import index lookup
            Map<Integer, String> importNames = new HashMap<>();
            for (int i = 0; i < pkg.imports.length; i++) {
                importNames.put(-(i + 1), pkg.imports[i].name.name);
            }
            for (int i = 0; i < pkg.exports.length; i++) {
                importNames.put(i + 1, pkg.exports[i].name.name);
            }
            
            // Find TerrainInfo
            for (Export export : pkg.exports) {
                String className = export.classIndex.get().name().name;
                if (!className.equals("TerrainInfo")) continue;
                
                System.out.printf("\n=== TerrainInfo: %s ===%n", export.name.name);
                
                ExportedObject obj = null;
                if (export instanceof ExportedObject eo) {
                    obj = eo;
                } else if (export instanceof ExportedEntry ee) {
                    obj = ee.asObject();
                }
                if (obj == null) continue;
                
                var unrealObj = pkg.object(obj);
                if (unrealObj == null || unrealObj.properties == null) continue;
                
                // Process Layers and DecoLayers
                for (Property prop : unrealObj.properties) {
                    String pn = prop.name.name;
                    
                    if (pn.equals("Layers")) {
                        System.out.println("\n=== LAYERS (Terrain Blend Layers) ===");
                        dumpArrayStructProperty(prop, importNames, pkg);
                    } else if (pn.equals("DecoLayers")) {
                        System.out.println("\n=== DECOLAYERS (Decoration Layers) ===");
                        dumpArrayStructProperty(prop, importNames, pkg);
                    }
                }
                
                // Also show all texture imports for reference
                System.out.println("\n=== ALL TEXTURE IMPORTS ===");
                for (Import imp : pkg.imports) {
                    if (imp.className.name.equals("Texture")) {
                        int idx = -getImportIndex(pkg, imp);
                        System.out.printf("  [%d] %s (from %s)%n", idx, imp.name.name, 
                            getPackageName(pkg, imp));
                    }
                }
                
                System.out.println("\n=== ALL STATICMESH IMPORTS ===");
                for (Import imp : pkg.imports) {
                    if (imp.className.name.equals("StaticMesh")) {
                        int idx = -getImportIndex(pkg, imp);
                        System.out.printf("  [%d] %s (from %s)%n", idx, imp.name.name,
                            getPackageName(pkg, imp));
                    }
                }
            }
        }
    }
    
    private static String getPackageName(Package pkg, Import imp) {
        // Walk up the package hierarchy
        if (imp.packageIndex.index == 0) return "(root)";
        if (imp.packageIndex.index < 0) {
            int pkgIdx = -imp.packageIndex.index - 1;
            if (pkgIdx >= 0 && pkgIdx < pkg.imports.length) {
                return pkg.imports[pkgIdx].name.name;
            }
        }
        return "(?)";
    }
    
    private static int getImportIndex(Package pkg, Import imp) {
        for (int i = 0; i < pkg.imports.length; i++) {
            if (pkg.imports[i] == imp) return i + 1;
        }
        return 0;
    }
    
    private static void dumpArrayStructProperty(Property prop, Map<Integer, String> nameMap, Package pkg) {
        if (!(prop instanceof ArrayProperty ap)) {
            System.out.println("  Not an ArrayProperty: " + prop.getClass().getSimpleName());
            return;
        }
        
        System.out.printf("  Array count: %d%n", ap.values.size());
        
        for (int i = 0; i < ap.values.size(); i++) {
            Object element = ap.values.get(i);
            System.out.printf("\n  [%d] %s%n", i, element.getClass().getSimpleName());
            
            if (element instanceof Property innerProp) {
                // Try to extract ObjectReferences using reflection
                dumpPropertyReferences(innerProp, nameMap, "    ");
            }
        }
    }
    
    private static void dumpPropertyReferences(Property prop, Map<Integer, String> nameMap, String indent) {
        try {
            // Check all fields including inherited ones
            List<Field> allFields = new ArrayList<>();
            Class<?> clazz = prop.getClass();
            while (clazz != null && clazz != Object.class) {
                allFields.addAll(Arrays.asList(clazz.getDeclaredFields()));
                clazz = clazz.getSuperclass();
            }
            
            for (Field f : allFields) {
                f.setAccessible(true);
                Object val = f.get(prop);
                
                String fn = f.getName();
                if (fn.equals("name") || fn.equals("arrayIndex")) continue;
                
                if (val == null) continue;
                
                // Check for ObjectReference type fields
                if (val.getClass().getSimpleName().equals("ObjectReference")) {
                    Field indexField = val.getClass().getDeclaredField("index");
                    indexField.setAccessible(true);
                    int index = (Integer) indexField.get(val);
                    String refName = nameMap.getOrDefault(index, "(unknown)");
                    System.out.printf("%s%s: ObjectRef[%d] -> %s%n", indent, fn, index, refName);
                } else if (val instanceof List<?> list) {
                    System.out.printf("%s%s: List<%d>%n", indent, fn, list.size());
                    for (int i = 0; i < Math.min(list.size(), 5); i++) {
                        Object item = list.get(i);
                        if (item instanceof Property p) {
                            System.out.printf("%s  [%d]:%n", indent, i);
                            dumpPropertyReferences(p, nameMap, indent + "    ");
                        }
                    }
                } else if (val instanceof Property p) {
                    System.out.printf("%s%s (nested property):%n", indent, fn);
                    dumpPropertyReferences(p, nameMap, indent + "  ");
                } else {
                    String valStr = val.toString();
                    if (valStr.length() > 80) valStr = valStr.substring(0, 80) + "...";
                    System.out.printf("%s%s: %s%n", indent, fn, valStr);
                }
            }
        } catch (Exception e) {
            System.out.printf("%sError: %s%n", indent, e.getMessage());
        }
    }
}
