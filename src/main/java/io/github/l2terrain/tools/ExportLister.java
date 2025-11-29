package io.github.l2terrain.tools;

import io.github.l2terrain.crypto.L2Decryptor;
import net.shrimpworks.unreal.packages.Package;
import net.shrimpworks.unreal.packages.PackageReader;
import net.shrimpworks.unreal.packages.entities.Export;

import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Debug utility to list all exports from a package file.
 */
public class ExportLister {
    
    public static void main(String[] args) throws Exception {
        if (args.length < 1) {
            System.out.println("Usage: ExportLister <package_file> [filter]");
            System.exit(1);
        }
        
        Path inputPath = Path.of(args[0]);
        String filter = args.length > 1 ? args[1].toLowerCase() : null;
        
        Path tempFile = Files.createTempFile("l2pkg_", ".tmp");
        try {
            L2Decryptor.decryptFile(inputPath, tempFile);
            
            try (Package pkg = new Package(new PackageReader(tempFile))) {
                System.out.println("Exports (" + pkg.exports.length + " total):");
                for (int i = 0; i < pkg.exports.length; i++) {
                    Export exp = pkg.exports[i];
                    String className = exp.classIndex.get().name().name;
                    String line = String.format("  [%d] %s (%s)", i + 1, exp.name.name, className);
                    
                    if (filter == null || line.toLowerCase().contains(filter)) {
                        System.out.println(line);
                    }
                }
            }
        } finally {
            Files.deleteIfExists(tempFile);
        }
    }
}
