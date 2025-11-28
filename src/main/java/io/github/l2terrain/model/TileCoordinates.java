package io.github.l2terrain.model;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Immutable value object representing tile grid coordinates.
 */
public record TileCoordinates(int x, int y) {
    
    // Pattern to extract tile coordinates from filename: t_17_21.utx or T_17_21_tx.utx -> (17, 21)
    private static final Pattern COORD_PATTERN = Pattern.compile("[Tt]_(\\d+)_(\\d+)");
    
    /**
     * Parse tile coordinates from a filename.
     * 
     * @param filename the filename to parse (e.g., "t_17_21.utx")
     * @return the parsed coordinates, or null if parsing failed
     */
    public static TileCoordinates fromFilename(String filename) {
        Matcher matcher = COORD_PATTERN.matcher(filename);
        if (matcher.find()) {
            return new TileCoordinates(
                Integer.parseInt(matcher.group(1)),
                Integer.parseInt(matcher.group(2))
            );
        }
        return null;
    }
    
    @Override
    public String toString() {
        return String.format("(%d,%d)", x, y);
    }
}
