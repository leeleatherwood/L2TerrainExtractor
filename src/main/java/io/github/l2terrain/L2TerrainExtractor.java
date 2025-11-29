package io.github.l2terrain;

import io.github.l2terrain.extractors.DetailMapExtractor;
import io.github.l2terrain.extractors.HeightmapExtractor;
import io.github.l2terrain.extractors.MetadataExtractor;
import io.github.l2terrain.extractors.MetadataExtractor.TileMetadata;
import io.github.l2terrain.extractors.SplatmapExtractor;
import io.github.l2terrain.extractors.SplatmapExtractor.SplatmapInfo;
import io.github.l2terrain.extractors.TerrainTextureExtractor;
import io.github.l2terrain.extractors.TerrainTextureExtractor.TextureInfo;
import io.github.l2terrain.model.TerrainTile;
import picocli.CommandLine;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;
import picocli.CommandLine.Parameters;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.Callable;

/**
 * L2TerrainExtractor - Extract terrain data from Lineage 2 packages
 * 
 * Extracts G16 heightmap textures from .utx terrain packages and saves
 * them as individual PNG and RAW files. Also extracts splatmaps, detail maps, and metadata.
 */
@Command(
    name = "l2terrain",
    mixinStandardHelpOptions = true,
    version = "L2TerrainExtractor 1.0.0",
    description = "Extract terrain data from Lineage 2 packages (heightmaps, splatmaps, detail maps, metadata)"
)
public class L2TerrainExtractor implements Callable<Integer> {
    
    @Parameters(index = "0", description = "Input directory containing .utx terrain packages (T_XX_YY.utx)")
    private Path inputDir;
    
    @Option(names = {"-o", "--output"}, description = "Output directory (default: current directory)")
    private Path outputDir = Path.of(".");
    
    @Option(names = {"-p", "--pattern"}, description = "File pattern to match (default: t_*_*.utx)")
    private String pattern = "t_*_*.utx";
    
    @Option(names = {"-v", "--verbose"}, description = "Verbose output")
    private boolean verbose = false;
    
    @Option(names = {"--detail-maps"}, description = "Directory containing L2DecoLayer*.utx detail map packages")
    private Path detailMapsDir;
    
    @Option(names = {"--maps"}, description = "Directory containing .unr map files for metadata extraction")
    private Path mapsDir;
    
    @Option(names = {"--terrain-textures"}, description = "Extract terrain tiling textures to terraintextures/ folder")
    private boolean extractTerrainTextures = false;
    
    @Option(names = {"--all-terrain-textures"}, description = "Extract ALL terrain textures (not just those in metadata)")
    private boolean extractAllTerrainTextures = false;
    
    @Option(names = {"--no-splatmaps"}, description = "Skip splatmap extraction")
    private boolean skipSplatmaps = false;
    
    public static void main(String[] args) {
        int exitCode = new CommandLine(new L2TerrainExtractor()).execute(args);
        System.exit(exitCode);
    }
    
    @Override
    public Integer call() throws Exception {
        // Validate input directory
        if (!Files.isDirectory(inputDir)) {
            System.err.println("Error: Input directory does not exist: " + inputDir);
            return 1;
        }
        
        // Create output directory if needed
        Files.createDirectories(outputDir);
        
        int totalSuccess = 0;
        int totalFailed = 0;
        
        // Extract heightmaps
        System.out.println("=== Extracting Heightmaps ===");
        int[] heightmapResults = extractHeightmaps();
        totalSuccess += heightmapResults[0];
        totalFailed += heightmapResults[1];
        
        // Extract splatmaps (from same T_XX_YY.utx packages)
        if (!skipSplatmaps) {
            System.out.println("\n=== Extracting Splatmaps ===");
            int[] splatResults = extractSplatmaps();
            totalSuccess += splatResults[0];
            totalFailed += splatResults[1];
        }
        
        // Extract detail maps if directory provided
        if (detailMapsDir != null) {
            System.out.println("\n=== Extracting Detail Maps ===");
            int[] detailResults = extractDetailMaps();
            totalSuccess += detailResults[0];
            totalFailed += detailResults[1];
        }
        
        // Extract metadata if maps directory provided
        if (mapsDir != null) {
            System.out.println("\n=== Extracting Metadata ===");
            int[] metaResults = extractMetadata();
            totalSuccess += metaResults[0];
            totalFailed += metaResults[1];
        }
        
        // Extract terrain textures if requested
        if (extractTerrainTextures || extractAllTerrainTextures) {
            System.out.println("\n=== Extracting Terrain Textures ===");
            int[] texResults = extractTerrainTextures();
            totalSuccess += texResults[0];
            totalFailed += texResults[1];
        }
        
        System.out.printf("\nTotal: %d items extracted (%d failed)%n", totalSuccess, totalFailed);
        
        return totalFailed > 0 && totalSuccess == 0 ? 1 : 0;
    }
    
    private int[] extractHeightmaps() throws IOException {
        // Find matching files
        List<Path> files;
        try (var stream = Files.walk(inputDir, 1)) {
            files = stream
                .filter(Files::isRegularFile)
                .filter(p -> matchesPattern(p.getFileName().toString()))
                .sorted()
                .toList();
        }
        
        if (files.isEmpty()) {
            System.err.println("No files matching pattern '" + pattern + "' found in " + inputDir);
            return new int[]{0, 0};
        }
        
        System.out.printf("Found %d terrain files%n", files.size());
        
        HeightmapExtractor extractor = new HeightmapExtractor();
        
        int success = 0;
        int failed = 0;
        
        for (Path file : files) {
            try {
                TerrainTile tile = extractor.extract(file);
                
                // Create tile subdirectory: extracted/XX_YY/
                String tileDirName = String.format("%d_%d", tile.getX(), tile.getY());
                Path tileDir = outputDir.resolve(tileDirName);
                Files.createDirectories(tileDir);
                
                // Generate output filenames: XX_YY_heightmap.png and XX_YY_heightmap.raw
                String baseName = String.format("%d_%d_heightmap", tile.getX(), tile.getY());
                Path pngPath = tileDir.resolve(baseName + ".png");
                Path rawPath = tileDir.resolve(baseName + ".raw");
                
                writePng(tile, pngPath);
                writeRaw(tile, rawPath);
                
                success++;
                if (verbose) {
                    System.out.printf("  Extracted: %s -> %s/%s%n", 
                        tile.getSourceName(), tileDirName, baseName + ".*");
                }
            } catch (IOException e) {
                failed++;
                System.err.println("  Failed: " + file.getFileName() + " - " + e.getMessage());
            }
        }
        
        System.out.printf("Extracted %d heightmaps (%d failed)%n", success, failed);
        return new int[]{success, failed};
    }
    
    private int[] extractSplatmaps() throws IOException {
        SplatmapExtractor extractor = new SplatmapExtractor();
        Map<String, List<SplatmapInfo>> allSplatmaps = extractor.extractAll(inputDir);
        
        int success = 0;
        int failed = 0;
        
        for (Map.Entry<String, List<SplatmapInfo>> entry : allSplatmaps.entrySet()) {
            String tileName = entry.getKey();
            List<SplatmapInfo> splatmaps = entry.getValue();
            
            Path tileDir = outputDir.resolve(tileName);
            Files.createDirectories(tileDir);
            
            for (SplatmapInfo splat : splatmaps) {
                Path outputPath = tileDir.resolve(splat.fileName);
                
                try {
                    ImageIO.write(splat.image, "png", outputPath.toFile());
                    success++;
                    if (verbose) {
                        System.out.printf("  Extracted: %s%n", splat.fileName);
                    }
                } catch (IOException e) {
                    failed++;
                    System.err.println("  Failed: " + splat.fileName + " - " + e.getMessage());
                }
            }
        }
        
        System.out.printf("Extracted %d splatmaps (%d failed)%n", success, failed);
        return new int[]{success, failed};
    }
    
    private int[] extractDetailMaps() throws IOException {
        if (!Files.isDirectory(detailMapsDir)) {
            System.err.println("Error: Detail maps directory does not exist: " + detailMapsDir);
            return new int[]{0, 0};
        }
        
        DetailMapExtractor extractor = new DetailMapExtractor();
        Map<String, Map<Integer, BufferedImage>> allDetailMaps = extractor.extractAllWithLayerNumbers(detailMapsDir);
        
        int success = 0;
        int failed = 0;
        
        for (Map.Entry<String, Map<Integer, BufferedImage>> entry : allDetailMaps.entrySet()) {
            String tileName = entry.getKey();
            Map<Integer, BufferedImage> layers = entry.getValue();
            
            // Parse tile coordinates from tileName (e.g., "23_16")
            String[] parts = tileName.split("_");
            if (parts.length < 2) continue;
            
            String tileDirName = tileName;
            Path tileDir = outputDir.resolve(tileDirName);
            Files.createDirectories(tileDir);
            
            for (Map.Entry<Integer, BufferedImage> layerEntry : layers.entrySet()) {
                int layerNum = layerEntry.getKey();
                BufferedImage layer = layerEntry.getValue();
                // Use actual deco layer number in filename
                String fileName = String.format("%s_detailmap_%d.png", tileName, layerNum);
                Path outputPath = tileDir.resolve(fileName);
                
                try {
                    ImageIO.write(layer, "png", outputPath.toFile());
                    success++;
                    if (verbose) {
                        System.out.printf("  Extracted: %s%n", fileName);
                    }
                } catch (IOException e) {
                    failed++;
                    System.err.println("  Failed: " + fileName + " - " + e.getMessage());
                }
            }
        }
        
        System.out.printf("Extracted %d detail maps (%d failed)%n", success, failed);
        return new int[]{success, failed};
    }
    
    private int[] extractMetadata() throws IOException {
        if (!Files.isDirectory(mapsDir)) {
            System.err.println("Error: Maps directory does not exist: " + mapsDir);
            return new int[]{0, 0};
        }
        
        MetadataExtractor extractor = new MetadataExtractor();
        
        // Pass 1: Build global cache from all map files
        extractor.buildCache(mapsDir);
        
        // Pass 2: Generate metadata for each tile using the cache
        List<TileMetadata> allMetadata = extractor.generateAllMetadata(outputDir);
        
        System.out.printf("Generated metadata for %d tiles%n", allMetadata.size());
        return new int[]{allMetadata.size(), 0};
    }
    
    private int[] extractTerrainTextures() throws IOException {
        TerrainTextureExtractor extractor = new TerrainTextureExtractor();
        
        Set<String> textureNames = null;
        
        if (!extractAllTerrainTextures) {
            // First, collect all unique texture names from metadata files
            textureNames = extractor.collectTextureNamesFromMetadata(outputDir);
            
            if (textureNames.isEmpty()) {
                System.out.println("No texture names found in metadata. Run with --maps first to generate metadata,");
                System.out.println("or use --all-terrain-textures to extract all textures from regional packages.");
                return new int[]{0, 0};
            }
            
            System.out.printf("Found %d unique texture names referenced in metadata%n", textureNames.size());
        } else {
            System.out.println("Extracting ALL textures from regional packages...");
        }
        
        // Create terraintextures output directory
        Path texOutputDir = outputDir.resolve("terraintextures");
        Files.createDirectories(texOutputDir);
        
        // Extract textures from regional packages
        Map<String, TextureInfo> textures = extractor.extractAll(inputDir, textureNames);
        
        int success = 0;
        int failed = 0;
        
        for (TextureInfo tex : textures.values()) {
            Path outputPath = texOutputDir.resolve(tex.name.toLowerCase() + ".png");
            
            try {
                ImageIO.write(tex.image, "PNG", outputPath.toFile());
                success++;
                if (verbose) {
                    System.out.printf("  Extracted: %s (%dx%d from %s)%n", 
                        tex.name, tex.width, tex.height, tex.sourcePackage);
                }
            } catch (IOException e) {
                failed++;
                System.err.println("  Failed: " + tex.name + " - " + e.getMessage());
            }
        }
        
        // Report any textures that were referenced but not found
        if (!extractAllTerrainTextures && textureNames != null) {
            Set<String> foundNames = new HashSet<>();
            for (TextureInfo tex : textures.values()) {
                foundNames.add(tex.name.toLowerCase());
            }
            
            Set<String> missingTextures = new HashSet<>(textureNames);
            missingTextures.removeAll(foundNames);
            
            if (!missingTextures.isEmpty() && verbose) {
                System.out.printf("  Warning: %d referenced textures not found in regional packages%n", missingTextures.size());
                for (String missing : missingTextures) {
                    System.out.println("    - " + missing);
                }
            }
            
            System.out.printf("Extracted %d terrain textures (%d failed, %d not found)%n", 
                success, failed, missingTextures.size());
        } else {
            System.out.printf("Extracted %d terrain textures (%d failed)%n", success, failed);
        }
        return new int[]{success, failed};
    }
    
    /**
     * Write tile as 8-bit PNG (normalized to 0-255 based on tile's own height range).
     */
    private void writePng(TerrainTile tile, Path output) throws IOException {
        int width = tile.getWidth();
        int height = tile.getHeight();
        int[] heights = tile.getHeightData();
        
        int minHeight = tile.getMinHeight();
        int maxHeight = tile.getMaxHeight();
        double range = Math.max(1, maxHeight - minHeight);
        
        BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_BYTE_GRAY);
        
        for (int y = 0; y < height; y++) {
            int rowOffset = y * width;
            for (int x = 0; x < width; x++) {
                int h = heights[rowOffset + x];
                int gray = (int) (((h - minHeight) / range) * 255.0);
                gray = Math.max(0, Math.min(255, gray));
                int rgb = (gray << 16) | (gray << 8) | gray;
                image.setRGB(x, y, rgb);
            }
        }
        
        ImageIO.write(image, "png", output.toFile());
    }
    
    /**
     * Write tile as 16-bit RAW (little-endian, preserves original values).
     */
    private void writeRaw(TerrainTile tile, Path output) throws IOException {
        int[] heights = tile.getHeightData();
        ByteBuffer buffer = ByteBuffer.allocate(heights.length * 2);
        buffer.order(ByteOrder.LITTLE_ENDIAN);
        
        for (int h : heights) {
            buffer.putShort((short) h);
        }
        
        Files.write(output, buffer.array());
    }
    
    private boolean matchesPattern(String filename) {
        // Simple glob matching for t_*_*.utx pattern
        String lower = filename.toLowerCase();
        String patternLower = pattern.toLowerCase();
        
        // Handle t_*_*.utx pattern (terrain tiles)
        if (patternLower.equals("t_*_*.utx")) {
            return lower.matches("t_\\d+_\\d+\\.utx");
        }
        
        if (patternLower.startsWith("*")) {
            String suffix = patternLower.substring(1);
            return lower.endsWith(suffix);
        }
        return lower.equals(patternLower);
    }
}
