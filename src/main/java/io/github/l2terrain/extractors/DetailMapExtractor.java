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
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class DetailMapExtractor {
    private static final Pattern DECO_PATTERN = Pattern.compile("(\\d+)_(\\d+)_[Dd]eco(\\d+)", Pattern.CASE_INSENSITIVE);

    public Map<String, Map<Integer, BufferedImage>> extractAllWithLayerNumbers(Path inputFolder) throws IOException {
        Map<String, Map<Integer, BufferedImage>> results = new TreeMap<>();
        List<Path> decoPackages = new ArrayList<>();
        try (var stream = Files.list(inputFolder)) {
            stream.filter(p -> {
                String name = p.getFileName().toString().toLowerCase();
                return name.startsWith("l2decolayer") && name.endsWith(".utx");
            }).forEach(decoPackages::add);
        }
        if (decoPackages.isEmpty()) {
            System.out.println("No L2DecoLayer*.utx files found in " + inputFolder);
            return results;
        }
        System.out.println("Found " + decoPackages.size() + " DecoLayer package(s)");
        for (Path pkg : decoPackages) {
            System.out.println("Processing: " + pkg.getFileName());
            extractFromPackage(pkg, results);
        }
        return results;
    }

    private void extractFromPackage(Path packagePath, Map<String, Map<Integer, BufferedImage>> results) throws IOException {
        Path tempFile = Files.createTempFile("l2deco_", ".utx");
        try {
            L2Decryptor.decryptFile(packagePath, tempFile);
            try (Package pkg = new Package(new PackageReader(tempFile))) {
                for (Export export : pkg.exports) {
                    String className = export.classIndex.get().name().name;
                    if (!className.equals("Texture")) continue;
                    String texName = export.name.name;
                    Matcher matcher = DECO_PATTERN.matcher(texName);
                    if (!matcher.matches()) continue;
                    int tileX = Integer.parseInt(matcher.group(1));
                    int tileY = Integer.parseInt(matcher.group(2));
                    int layerNum = Integer.parseInt(matcher.group(3));
                    String tileName = String.format("%d_%d", tileX, tileY);
                    try {
                        ExportedObject obj = null;
                        if (export instanceof ExportedObject eo) { obj = eo; }
                        else if (export instanceof ExportedEntry ee) { obj = ee.asObject(); }
                        if (obj == null) continue;
                        var texObj = pkg.object(obj);
                        if (!(texObj instanceof Texture tex)) continue;
                        TextureBase.Format format = tex.format();
                        int width = 512, height = 512;
                        for (Property prop : tex.properties) {
                            if (prop.name.name.equals("USize") && prop instanceof IntegerProperty ip) { width = ip.value; }
                            else if (prop.name.name.equals("VSize") && prop instanceof IntegerProperty ip) { height = ip.value; }
                        }
                        byte[] exportData = UnrealPackageUtils.readExportData(tex, obj);
                        BufferedImage image = extractTextureByFormat(exportData, format, width, height);
                        if (image == null) { System.out.println("    Warning: Could not extract " + texName); continue; }
                        results.computeIfAbsent(tileName, k -> new TreeMap<>()).put(layerNum, image);
                    } catch (Exception e) { System.out.println("    Error extracting " + texName + ": " + e.getMessage()); }
                }
            }
        } finally { Files.deleteIfExists(tempFile); }
    }

    private BufferedImage extractTextureByFormat(byte[] exportData, TextureBase.Format format, int width, int height) {
        return switch (format) {
            case DXT1 -> TextureUtils.extractDXT1(exportData, width, height);
            case DXT3 -> TextureUtils.extractDXT3(exportData, width, height);
            case RGBA8 -> TextureUtils.extractRGBA8(exportData, width, height);
            case PALETTE_8_BIT -> TextureUtils.extractP8(exportData, width, height);
            default -> { System.out.println("    Unsupported format: " + format); yield null; }
        };
    }
}
