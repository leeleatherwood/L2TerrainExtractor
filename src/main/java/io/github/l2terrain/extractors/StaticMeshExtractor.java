package io.github.l2terrain.extractors;

import io.github.l2terrain.crypto.L2Decryptor;
import net.shrimpworks.unreal.packages.Package;
import net.shrimpworks.unreal.packages.PackageReader;
import net.shrimpworks.unreal.packages.entities.ExportedObject;
import net.shrimpworks.unreal.packages.entities.Import;
import net.shrimpworks.unreal.packages.entities.Named;
import net.shrimpworks.unreal.packages.entities.objects.Object;
import net.shrimpworks.unreal.packages.entities.properties.*;

import java.io.IOException;
import java.io.PrintWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Extracts static mesh actor placements from Lineage 2 map files.
 * Each XX_YY.unr map file produces a staticmeshes.json containing all
 * StaticMeshActor positions, rotations, scales, and mesh references.
 */
public class StaticMeshExtractor {
    
    private static final Pattern TILE_PATTERN = Pattern.compile("(\\d+)_(\\d+)\\.unr", Pattern.CASE_INSENSITIVE);
    
    private boolean verbose = false;
    
    public void setVerbose(boolean verbose) {
        this.verbose = verbose;
    }
    
    /**
     * Extract static mesh data from all map files.
     * @param mapsDir Directory containing .unr map files
     * @param outputDir Output directory (JSON files go into XX_YY subdirectories)
     * @return Number of tiles processed
     */
    public int extractAll(Path mapsDir, Path outputDir) throws IOException {
        List<Path> mapFiles = new ArrayList<>();
        
        try (var stream = Files.list(mapsDir)) {
            stream.filter(p -> {
                String name = p.getFileName().toString().toLowerCase();
                return name.endsWith(".unr") && TILE_PATTERN.matcher(name).matches();
            }).forEach(mapFiles::add);
        }
        
        System.out.println("Found " + mapFiles.size() + " map file(s)");
        
        int processed = 0;
        int totalMeshes = 0;
        
        for (Path mapFile : mapFiles) {
            try {
                String fileName = mapFile.getFileName().toString();
                Matcher m = TILE_PATTERN.matcher(fileName);
                if (!m.matches()) continue;
                
                String tileX = m.group(1);
                String tileY = m.group(2);
                String tileKey = tileX + "_" + tileY;
                
                // Create output directory for this tile
                Path tileDir = outputDir.resolve(tileKey);
                Files.createDirectories(tileDir);
                
                // Extract static meshes
                List<StaticMeshInfo> meshes = extractFromMap(mapFile);
                
                if (!meshes.isEmpty()) {
                    // Write JSON
                    Path jsonFile = tileDir.resolve("staticmeshes.json");
                    writeJson(meshes, jsonFile, tileKey);
                    
                    if (verbose) {
                        System.out.println("  " + tileKey + ": " + meshes.size() + " static meshes");
                    }
                    
                    totalMeshes += meshes.size();
                }
                
                processed++;
            } catch (Exception e) {
                System.err.println("  Error processing " + mapFile.getFileName() + ": " + e.getMessage());
            }
        }
        
        System.out.println("Extracted " + totalMeshes + " static meshes from " + processed + " tiles");
        return processed;
    }
    
    /**
     * Extract static mesh data from a single map file.
     */
    public List<StaticMeshInfo> extractFromMap(Path mapFile) throws IOException {
        List<StaticMeshInfo> meshes = new ArrayList<>();
        
        Path tempFile = Files.createTempFile("l2map_", ".tmp");
        try {
            L2Decryptor.decryptFile(mapFile, tempFile);
            
            try (Package pkg = new Package(new PackageReader(tempFile))) {
                // Build import lookup for resolving mesh references
                Map<Integer, Import> imports = new HashMap<>();
                for (int i = 0; i < pkg.imports.length; i++) {
                    imports.put(-(i + 1), pkg.imports[i]);
                }
                
                for (ExportedObject exp : pkg.objects) {
                    if (exp == null) continue;
                    
                    String className = exp.classIndex.get().name().name;
                    if (!"StaticMeshActor".equals(className)) continue;
                    
                    try {
                        Object obj = pkg.object(exp);
                        StaticMeshInfo info = parseStaticMeshActor(obj, exp.name.name, imports);
                        if (info != null) {
                            meshes.add(info);
                        }
                    } catch (Exception e) {
                        // Skip actors that can't be parsed
                    }
                }
            }
        } finally {
            Files.deleteIfExists(tempFile);
        }
        
        return meshes;
    }
    
    /**
     * Resolve the top-level package name for an import.
     * Follows the packageIndex hierarchy up to find the root Package import.
     */
    private String resolvePackageName(Import imp) {
        if (imp == null) return null;
        
        // Follow parent references until we find a Package with no parent
        Named current = imp.packageIndex.get();
        Named previous = null;
        
        while (current instanceof Import parentImp) {
            previous = current;
            current = parentImp.packageIndex.get();
        }
        
        // If we found a parent, it's the package name
        if (previous != null) {
            return previous.name().name;
        }
        
        // If there's a parent at all, that's the package
        if (imp.packageIndex.get() != null) {
            return imp.packageIndex.get().name().name;
        }
        
        return null;
    }
    
    /**
     * Build the full path within the package (e.g., "group.subgroup.meshname").
     */
    private String buildMeshPath(Import imp) {
        if (imp == null) return null;
        
        List<String> parts = new ArrayList<>();
        parts.add(imp.name.name);
        
        // Walk up the hierarchy, collecting group names
        Named current = imp.packageIndex.get();
        while (current instanceof Import parentImp) {
            // Check if this parent itself has a parent (if not, it's the package root)
            Named grandParent = parentImp.packageIndex.get();
            if (grandParent == null) {
                // This is the root package - don't include it in the path
                break;
            }
            parts.add(0, parentImp.name.name);
            current = grandParent;
        }
        
        return String.join(".", parts);
    }
    
    private StaticMeshInfo parseStaticMeshActor(Object obj, String actorName,
                                                 Map<Integer, Import> imports) {
        StaticMeshInfo info = new StaticMeshInfo();
        info.actorName = actorName;
        
        for (Property prop : obj.properties) {
            String propName = prop.name.name;
            
            switch (propName) {
                case "StaticMesh":
                    if (prop instanceof ObjectProperty objProp) {
                        int idx = objProp.value.index;
                        Import meshImport = imports.get(idx);
                        if (meshImport != null) {
                            info.staticMesh = meshImport.name.name;
                            info.meshPackage = resolvePackageName(meshImport);
                            info.meshPath = buildMeshPath(meshImport);
                        } else {
                            info.staticMesh = "ref:" + idx;
                        }
                    }
                    break;
                    
                case "Location":
                    if (prop instanceof StructProperty.VectorProperty vec) {
                        info.locationX = vec.x;
                        info.locationY = vec.y;
                        info.locationZ = vec.z;
                    }
                    break;
                    
                case "Rotation":
                    if (prop instanceof StructProperty.RotatorProperty rot) {
                        // Convert from Unreal rotation units to degrees
                        info.rotationPitch = rot.pitch * 360.0 / 65536.0;
                        info.rotationYaw = rot.yaw * 360.0 / 65536.0;
                        info.rotationRoll = rot.roll * 360.0 / 65536.0;
                    }
                    break;
                    
                case "DrawScale":
                    if (prop instanceof FloatProperty floatProp) {
                        info.drawScale = floatProp.value;
                    }
                    break;
                    
                case "DrawScale3D":
                    if (prop instanceof StructProperty.VectorProperty vec) {
                        info.drawScale3DX = vec.x;
                        info.drawScale3DY = vec.y;
                        info.drawScale3DZ = vec.z;
                    }
                    break;
                    
                case "bHidden":
                    if (prop instanceof BooleanProperty boolProp) {
                        info.hidden = boolProp.value;
                    }
                    break;
                    
                case "bShadowCast":
                    if (prop instanceof BooleanProperty boolProp) {
                        info.shadowCast = boolProp.value;
                    }
                    break;
                    
                case "bCollideActors":
                    if (prop instanceof BooleanProperty boolProp) {
                        info.collideActors = boolProp.value;
                    }
                    break;
                    
                case "bBlockActors":
                    if (prop instanceof BooleanProperty boolProp) {
                        info.blockActors = boolProp.value;
                    }
                    break;
                    
                case "bBlockPlayers":
                    if (prop instanceof BooleanProperty boolProp) {
                        info.blockPlayers = boolProp.value;
                    }
                    break;
            }
        }
        
        // Must have at least a mesh reference
        if (info.staticMesh == null) {
            return null;
        }
        
        return info;
    }
    
    private void writeJson(List<StaticMeshInfo> meshes, Path jsonFile, String tileKey) throws IOException {
        try (PrintWriter out = new PrintWriter(Files.newBufferedWriter(jsonFile))) {
            out.println("{");
            out.println("  \"tile\": \"" + tileKey + "\",");
            out.println("  \"count\": " + meshes.size() + ",");
            out.println("  \"staticMeshes\": [");
            
            for (int i = 0; i < meshes.size(); i++) {
                StaticMeshInfo mesh = meshes.get(i);
                out.println("    {");
                out.println("      \"name\": \"" + escapeJson(mesh.actorName) + "\",");
                out.println("      \"mesh\": \"" + escapeJson(mesh.staticMesh) + "\",");
                if (mesh.meshPackage != null) {
                    out.println("      \"package\": \"" + escapeJson(mesh.meshPackage) + "\",");
                }
                if (mesh.meshPath != null) {
                    out.println("      \"meshPath\": \"" + escapeJson(mesh.meshPath) + "\",");
                }
                out.println("      \"location\": {");
                out.printf("        \"x\": %.4f,%n", mesh.locationX);
                out.printf("        \"y\": %.4f,%n", mesh.locationY);
                out.printf("        \"z\": %.4f%n", mesh.locationZ);
                out.println("      },");
                out.println("      \"rotation\": {");
                out.printf("        \"pitch\": %.4f,%n", mesh.rotationPitch);
                out.printf("        \"yaw\": %.4f,%n", mesh.rotationYaw);
                out.printf("        \"roll\": %.4f%n", mesh.rotationRoll);
                out.println("      },");
                
                // Scale
                out.println("      \"scale\": {");
                out.printf("        \"uniform\": %.4f,%n", mesh.drawScale);
                out.printf("        \"x\": %.4f,%n", mesh.drawScale3DX);
                out.printf("        \"y\": %.4f,%n", mesh.drawScale3DY);
                out.printf("        \"z\": %.4f%n", mesh.drawScale3DZ);
                out.println("      },");
                
                // Flags
                out.println("      \"flags\": {");
                out.println("        \"hidden\": " + mesh.hidden + ",");
                out.println("        \"shadowCast\": " + mesh.shadowCast + ",");
                out.println("        \"collideActors\": " + mesh.collideActors + ",");
                out.println("        \"blockActors\": " + mesh.blockActors + ",");
                out.println("        \"blockPlayers\": " + mesh.blockPlayers);
                out.println("      }");
                
                out.println("    }" + (i < meshes.size() - 1 ? "," : ""));
            }
            
            out.println("  ]");
            out.println("}");
        }
    }
    
    private String escapeJson(String s) {
        if (s == null) return "";
        return s.replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\n", "\\n")
                .replace("\r", "\\r")
                .replace("\t", "\\t");
    }
    
    /**
     * Data class for static mesh actor information.
     */
    public static class StaticMeshInfo {
        public String actorName;
        public String staticMesh;
        public String meshPackage;
        public String meshPath;  // Full path within package (group.mesh)
        
        // Location
        public float locationX = 0;
        public float locationY = 0;
        public float locationZ = 0;
        
        // Rotation (degrees)
        public double rotationPitch = 0;
        public double rotationYaw = 0;
        public double rotationRoll = 0;
        
        // Scale
        public float drawScale = 1.0f;
        public float drawScale3DX = 1.0f;
        public float drawScale3DY = 1.0f;
        public float drawScale3DZ = 1.0f;
        
        // Flags
        public boolean hidden = false;
        public boolean shadowCast = true;
        public boolean collideActors = false;
        public boolean blockActors = false;
        public boolean blockPlayers = false;
    }
}
