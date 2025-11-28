package io.github.l2terrain.model;

/**
 * Represents a single terrain tile with coordinates and height data.
 * 
 * <p>Height statistics are computed once at construction time for efficiency.</p>
 */
public class TerrainTile {
    
    private final TileCoordinates coordinates;
    private final int width;
    private final int height;
    private final int[] heightData;  // Unsigned 16-bit values stored as int
    private final String sourceName;
    private final int minHeight;
    private final int maxHeight;
    
    public TerrainTile(int x, int y, int width, int height, int[] heightData, String sourceName) {
        this(new TileCoordinates(x, y), width, height, heightData, sourceName);
    }
    
    public TerrainTile(TileCoordinates coords, int width, int height, int[] heightData, String sourceName) {
        this.coordinates = coords;
        this.width = width;
        this.height = height;
        this.heightData = heightData;
        this.sourceName = sourceName;
        
        // Compute height stats once at construction
        int min = Integer.MAX_VALUE;
        int max = Integer.MIN_VALUE;
        for (int h : heightData) {
            if (h < min) min = h;
            if (h > max) max = h;
        }
        this.minHeight = min;
        this.maxHeight = max;
    }
    
    /**
     * Get the tile's grid coordinates.
     */
    public TileCoordinates getCoordinates() {
        return coordinates;
    }
    
    public int getX() {
        return coordinates.x();
    }
    
    public int getY() {
        return coordinates.y();
    }
    
    public int getWidth() {
        return width;
    }
    
    public int getHeight() {
        return height;
    }
    
    public int[] getHeightData() {
        return heightData;
    }
    
    public String getSourceName() {
        return sourceName;
    }
    
    /**
     * Get height value at local tile coordinates.
     */
    public int getHeightAt(int localX, int localY) {
        return heightData[localY * width + localX];
    }
    
    /**
     * Get minimum height in this tile (cached).
     */
    public int getMinHeight() {
        return minHeight;
    }
    
    /**
     * Get maximum height in this tile (cached).
     */
    public int getMaxHeight() {
        return maxHeight;
    }
    
    @Override
    public String toString() {
        return String.format("TerrainTile[%s @ %s, %dx%d, height range: %d-%d]",
            sourceName, coordinates, width, height, minHeight, maxHeight);
    }
}
