package io.github.l2terrain.tools;

import io.github.l2terrain.crypto.L2Decryptor;
import net.shrimpworks.unreal.packages.Package;
import net.shrimpworks.unreal.packages.PackageReader;
import net.shrimpworks.unreal.packages.entities.ExportedObject;
import net.shrimpworks.unreal.packages.entities.objects.Object;
import net.shrimpworks.unreal.packages.entities.properties.ObjectProperty;
import net.shrimpworks.unreal.packages.entities.properties.Property;
import net.shrimpworks.unreal.packages.entities.properties.StructProperty;

import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Debug utility to inspect StaticMeshActor properties in a map file.
 * Shows Location, Rotation, DrawScale, and StaticMesh reference.
 */
public class StaticMeshActorInspector {
    
    public static void main(String[] args) throws Exception {
        if (args.length < 1) {
            System.out.println("Usage: StaticMeshActorInspector <map_file> [max_actors]");
            System.exit(1);
        }
        
        Path inputPath = Path.of(args[0]);
        int maxActors = args.length > 1 ? Integer.parseInt(args[1]) : 5;
        
        Path tempFile = Files.createTempFile("l2pkg_", ".tmp");
        try {
            L2Decryptor.decryptFile(inputPath, tempFile);
            
            try (Package pkg = new Package(new PackageReader(tempFile))) {
                int count = 0;
                
                for (ExportedObject exp : pkg.objects) {
                    if (exp == null) continue;
                    
                    String className = exp.classIndex.get().name().name;
                    if ("StaticMeshActor".equals(className)) {
                        System.out.println("\n=== " + exp.name.name + " ===");
                        
                        try {
                            Object obj = pkg.object(exp);
                            
                            // Print all properties
                            for (Property prop : obj.properties) {
                                System.out.println("  " + formatProperty(prop));
                            }
                        } catch (Exception e) {
                            System.out.println("  Error reading properties: " + e.getMessage());
                        }
                        
                        count++;
                        if (count >= maxActors) break;
                    }
                }
                
                if (count == 0) {
                    System.out.println("No StaticMeshActor exports found in this map.");
                } else {
                    System.out.println("\n(Showed " + count + " actors)");
                }
            }
        } finally {
            Files.deleteIfExists(tempFile);
        }
    }
    
    private static String formatProperty(Property prop) {
        StringBuilder sb = new StringBuilder();
        sb.append(prop.name.name).append(": ");
        
        if (prop instanceof StructProperty.VectorProperty vec) {
            sb.append(String.format("(X=%.2f, Y=%.2f, Z=%.2f)", vec.x, vec.y, vec.z));
        } else if (prop instanceof StructProperty.RotatorProperty rot) {
            sb.append(String.format("(Pitch=%.2f°, Yaw=%.2f°, Roll=%.2f°)", 
                rot.pitch * 360.0 / 65536.0, 
                rot.yaw * 360.0 / 65536.0, 
                rot.roll * 360.0 / 65536.0));
        } else if (prop instanceof StructProperty.ScaleProperty scale) {
            sb.append(String.format("(X=%.2f, Y=%.2f, Z=%.2f)", scale.x, scale.y, scale.z));
        } else if (prop instanceof ObjectProperty objProp) {
            try {
                sb.append(objProp.value.get().name().name);
            } catch (Exception e) {
                sb.append("ref:" + objProp.value.index);
            }
        } else {
            sb.append(prop.toString());
        }
        
        return sb.toString();
    }
}
