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
import java.util.regex.Pattern;

public class TerrainTextureExtractor {
    private static final Pattern TILE_PKG_PATTERN = Pattern.compile("[Tt]_(\\d+)_(\\d+)\\.utx");
    private static final Pattern REGIONAL_PKG_PATTERN = Pattern.compile("[Tt]_[A-Za-z]+\\d*\\.utx");

    public static class TextureInfo {
        public final String name;
        public final String sourcePackage;
        public final BufferedImage image;
        public final int width;
        public final int height;
        public TextureInfo(String name, String sourcePackage, BufferedImage image, int width, int height) {
            this.name = name; this.sourcePackage = sourcePackage; this.image = image; this.width = width; this.height = height;
        }
    }

    public Map<String, TextureInfo> extractAll(Path inputFolder, Set<String> filterSet) throws IOException {
        Map<String, TextureInfo> results = new TreeMap<>();
        List<Path> packages = new ArrayList<>();
        try (var stream = Files.list(inputFolder)) {
            stream.filter(p -> {
                String name = p.getFileName().toString();
                return REGIONAL_PKG_PATTERN.matcher(name).matches() && !TILE_PKG_PATTERN.matcher(name).matches();
            }).forEach(packages::add);
        }
        if (packages.isEmpty()) { System.out.println("No regional texture packages found in " + inputFolder); return results; }
        System.out.println("Found " + packages.size() + " regional texture packages");
        for (Path pkg : packages) {
            System.out.println("Processing: " + pkg.getFileName());
            extractFromPackage(pkg, results, filterSet);
        }
        return results;
    }

    public Set<String> collectTextureNamesFromMetadata(Path extractedFolder) throws IOException {
        Set<String> textureNames = new HashSet<>();
        try (var stream = Files.walk(extractedFolder)) {
            stream.filter(p -> p.getFileName().toString().endsWith("_metadata.txt")).forEach(metaFile -> {
                try {
                    for (String line : Files.readAllLines(metaFile)) {
                        if (line.startsWith("splatmap_") && line.contains(",")) {
                            String[] parts = line.split(",");
                            if (parts.length >= 2) { textureNames.add(parts[1].trim().toLowerCase()); }
                        }
                    }
                } catch (IOException e) { System.err.println("Error reading " + metaFile + ": " + e.getMessage()); }
            });
        }
        return textureNames;
    }

    private void extractFromPackage(Path packagePath, Map<String, TextureInfo> results, Set<String> filterSet) throws IOException {
        String pkgName = packagePath.getFileName().toString();
        Path tempFile = Files.createTempFile("l2tex_", ".utx");
        try {
            L2Decryptor.decryptFile(packagePath, tempFile);
            try (Package pkg = new Package(new PackageReader(tempFile))) {
                for (Export export : pkg.exports) {
                    String className = export.classIndex.get().name().name;
                    if (!className.equals("Texture")) continue;
                    String texName = export.name.name;
                    String texNameLower = texName.toLowerCase();
                    if (results.containsKey(texNameLower)) continue;
                    if (filterSet != null && !filterSet.contains(texNameLower)) continue;
                    try {
                        ExportedObject obj = null;
                        if (export instanceof ExportedObject eo) { obj = eo; }
                        else if (export instanceof ExportedEntry ee) { obj = ee.asObject(); }
                        if (obj == null) continue;
                        var texObj = pkg.object(obj);
                        if (!(texObj instanceof Texture tex)) continue;
                        TextureBase.Format format = tex.format();
                        int width = 256, height = 256;
                        for (Property prop : tex.properties) {
                            if (prop.name.name.equals("USize") && prop instanceof IntegerProperty ip) { width = ip.value; }
                            else if (prop.name.name.equals("VSize") && prop instanceof IntegerProperty ip) { height = ip.value; }
                        }
                        byte[] exportData = UnrealPackageUtils.readExportData(tex, obj);
                        BufferedImage image = extractTextureByFormat(exportData, format, width, height);
                        if (image == null) continue;
                        results.put(texNameLower, new TextureInfo(texName, pkgName, image, width, height));
                    } catch (Exception e) { /* Skip textures we can't extract */ }
                }
            }
        } finally { Files.deleteIfExists(tempFile); }
    }

    private BufferedImage extractTextureByFormat(byte[] exportData, TextureBase.Format format, int width, int height) {
        return switch (format) {
            case DXT1 -> TextureUtils.extractDXT1(exportData, width, height);
            case DXT3 -> TextureUtils.extractDXT3(exportData, width, height);
            case DXT5 -> TextureUtils.extractDXT5(exportData, width, height);
            case RGBA8 -> TextureUtils.extractRGBA8(exportData, width, height);
            case PALETTE_8_BIT -> TextureUtils.extractP8(exportData, width, height);
            default -> null;
        };
    }
}
