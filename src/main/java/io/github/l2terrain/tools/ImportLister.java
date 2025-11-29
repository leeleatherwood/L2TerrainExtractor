package io.github.l2terrain.tools;

import io.github.l2terrain.crypto.L2Decryptor;
import net.shrimpworks.unreal.packages.Package;
import net.shrimpworks.unreal.packages.PackageReader;
import net.shrimpworks.unreal.packages.entities.Import;
import net.shrimpworks.unreal.packages.entities.Named;

import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Debug utility to list all imports from a package file.
 */
public class ImportLister {
    
    public static void main(String[] args) throws Exception {
        if (args.length < 1) {
            System.out.println("Usage: ImportLister <package_file> [filter]");
            System.exit(1);
        }
        
        Path inputPath = Path.of(args[0]);
        String filter = args.length > 1 ? args[1].toLowerCase() : null;
        
        Path tempFile = Files.createTempFile("l2pkg_", ".tmp");
        try {
            L2Decryptor.decryptFile(inputPath, tempFile);
            
            try (Package pkg = new Package(new PackageReader(tempFile))) {
                System.out.println("Imports (" + pkg.imports.length + " total):");
                for (int i = 0; i < pkg.imports.length; i++) {
                    Import imp = pkg.imports[i];
                    
                    // Build the full package path by following parent references
                    String fullPath = buildPackagePath(imp);
                    
                    String line = String.format("  [-%d] %s (%s) -> %s", 
                        i + 1, imp.name.name, imp.className.name, fullPath);
                    
                    if (filter == null || line.toLowerCase().contains(filter)) {
                        System.out.println(line);
                    }
                }
            }
        } finally {
            Files.deleteIfExists(tempFile);
        }
    }
    
    /**
     * Build the full package path by following parent references.
     */
    private static String buildPackagePath(Import imp) {
        StringBuilder path = new StringBuilder();
        Named parent = imp.packageIndex.get();
        
        while (parent != null) {
            if (path.length() > 0) {
                path.insert(0, ".");
            }
            path.insert(0, parent.name().name);
            
            if (parent instanceof Import parentImp) {
                parent = parentImp.packageIndex.get();
            } else {
                break;
            }
        }
        
        return path.length() > 0 ? path.toString() : "(none)";
    }
}
