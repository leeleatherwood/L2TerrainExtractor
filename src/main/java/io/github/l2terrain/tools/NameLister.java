package io.github.l2terrain.tools;

import io.github.l2terrain.crypto.L2Decryptor;
import net.shrimpworks.unreal.packages.Package;
import net.shrimpworks.unreal.packages.PackageReader;

import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Debug utility to list all names from a package file.
 */
public class NameLister {
    
    public static void main(String[] args) throws Exception {
        if (args.length < 1) {
            System.out.println("Usage: NameLister <package_file> [filter]");
            System.exit(1);
        }
        
        Path inputPath = Path.of(args[0]);
        String filter = args.length > 1 ? args[1].toLowerCase() : null;
        
        Path tempFile = Files.createTempFile("l2pkg_", ".tmp");
        try {
            L2Decryptor.decryptFile(inputPath, tempFile);
            
            try (Package pkg = new Package(new PackageReader(tempFile))) {
                System.out.println("Names (" + pkg.names.length + " total):");
                for (int i = 0; i < pkg.names.length; i++) {
                    String name = pkg.names[i].name;
                    if (filter == null || name.toLowerCase().contains(filter)) {
                        System.out.println("  [" + i + "] " + name);
                    }
                }
            }
        } finally {
            Files.deleteIfExists(tempFile);
        }
    }
}
