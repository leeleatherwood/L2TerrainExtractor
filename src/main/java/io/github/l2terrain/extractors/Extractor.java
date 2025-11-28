package io.github.l2terrain.extractors;

import io.github.l2terrain.model.TerrainTile;
import java.io.IOException;
import java.nio.file.Path;
import java.util.List;

/**
 * Interface for terrain data extractors.
 * Implementations handle different data types (heightmaps, splatmaps, etc.)
 */
public interface Extractor {
    
    /**
     * Get the name of this extractor type.
     */
    String getName();
    
    /**
     * Get file patterns this extractor handles.
     * e.g., ["*_tx.utx"] for heightmaps
     */
    List<String> getFilePatterns();
    
    /**
     * Check if this extractor can handle the given file.
     */
    boolean canHandle(Path file);
    
    /**
     * Extract terrain tile from a package file.
     * 
     * @param file the package file to extract from
     * @return the extracted terrain tile, or null if extraction failed
     * @throws IOException if file cannot be read
     */
    TerrainTile extract(Path file) throws IOException;
    
    /**
     * Extract terrain tiles from multiple package files.
     * 
     * @param files the package files to extract from
     * @return list of extracted tiles (may be smaller than input if some fail)
     * @throws IOException if critical error occurs
     */
    default List<TerrainTile> extractAll(List<Path> files) throws IOException {
        return files.stream()
            .map(f -> {
                try {
                    return extract(f);
                } catch (IOException e) {
                    System.err.println("Failed to extract " + f.getFileName() + ": " + e.getMessage());
                    return null;
                }
            })
            .filter(t -> t != null)
            .toList();
    }
}
