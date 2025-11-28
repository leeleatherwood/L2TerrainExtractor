package io.github.l2terrain;

import io.github.l2terrain.extractors.HeightmapExtractor;
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
import java.util.List;
import java.util.concurrent.Callable;

/**
 * L2TerrainExtractor - Extract terrain data from Lineage 2 packages
 * 
 * Extracts G16 heightmap textures from .utx terrain packages and saves
 * them as individual PNG and RAW files.
 */
@Command(
    name = "l2terrain",
    mixinStandardHelpOptions = true,
    version = "L2TerrainExtractor 1.0.0",
    description = "Extract terrain heightmaps from Lineage 2 packages"
)
public class L2TerrainExtractor implements Callable<Integer> {
    
    @Parameters(index = "0", description = "Input directory containing .utx terrain packages")
    private Path inputDir;
    
    @Option(names = {"-o", "--output"}, description = "Output directory (default: current directory)")
    private Path outputDir = Path.of(".");
    
    @Option(names = {"-p", "--pattern"}, description = "File pattern to match (default: t_*_*.utx)")
    private String pattern = "t_*_*.utx";
    
    @Option(names = {"-v", "--verbose"}, description = "Verbose output")
    private boolean verbose = false;
    
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
            System.err.println("Error: No files matching pattern '" + pattern + "' found in " + inputDir);
            return 1;
        }
        
        System.out.printf("Found %d terrain files%n", files.size());
        
        // Extract tiles
        HeightmapExtractor extractor = new HeightmapExtractor();
        
        int success = 0;
        int failed = 0;
        
        for (Path file : files) {
            try {
                TerrainTile tile = extractor.extract(file);
                
                // Generate output filenames: XX_YY_heightmap.png and XX_YY_heightmap.raw
                String baseName = String.format("%d_%d_heightmap", tile.getX(), tile.getY());
                Path pngPath = outputDir.resolve(baseName + ".png");
                Path rawPath = outputDir.resolve(baseName + ".raw");
                
                // Write PNG (normalized to 0-255)
                writePng(tile, pngPath);
                
                // Write RAW (16-bit little-endian)
                writeRaw(tile, rawPath);
                
                success++;
                if (verbose) {
                    System.out.printf("  Extracted: %s -> %s, %s%n", 
                        tile.getSourceName(), pngPath.getFileName(), rawPath.getFileName());
                }
            } catch (IOException e) {
                failed++;
                System.err.println("  Failed: " + file.getFileName() + " - " + e.getMessage());
            }
        }
        
        System.out.printf("Extracted %d tiles (%d failed) to %s%n", success, failed, outputDir.toAbsolutePath());
        
        return failed > 0 && success == 0 ? 1 : 0;
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
