package io.github.l2terrain.utils;

import java.awt.image.BufferedImage;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;

/**
 * Shared utility methods for texture extraction and decompression.
 * 
 * <p>Provides DXT1/DXT3/DXT5 decompression, compact index parsing,
 * and other common texture operations used by multiple extractors.</p>
 */
public final class TextureUtils {
    
    private TextureUtils() {
        // Utility class - prevent instantiation
    }
    
    // ==================== Compact Index ====================
    
    /**
     * Read a compact index from a byte array.
     * Compact index is Unreal's variable-length integer encoding.
     * 
     * @param data the byte array
     * @param offset the offset to start reading from
     * @return the decoded integer value
     */
    public static int readCompactIndex(byte[] data, int offset) {
        if (offset >= data.length) return 0;
        
        int result = 0;
        int shift = 0;
        boolean negative = false;
        
        for (int i = 0; i < 5 && offset + i < data.length; i++) {
            int b = data[offset + i] & 0xFF;
            
            if (i == 0) {
                negative = (b & 0x80) != 0;
                result = b & 0x3F;
                if ((b & 0x40) == 0) {
                    return negative ? -result : result;
                }
                shift = 6;
            } else {
                result |= (b & 0x7F) << shift;
                if ((b & 0x80) == 0) {
                    return negative ? -result : result;
                }
                shift += 7;
            }
        }
        
        return negative ? -result : result;
    }
    
    /**
     * Get the byte length of a compact index encoding.
     * 
     * @param value the integer value
     * @return the number of bytes needed to encode this value
     */
    public static int compactIndexLength(int value) {
        if (value < 0) value = -value;
        if (value < 0x40) return 1;
        if (value < 0x2000) return 2;
        if (value < 0x100000) return 3;
        if (value < 0x8000000) return 4;
        return 5;
    }
    
    // ==================== Texture Data Location ====================
    
    /**
     * Find the texture data offset by searching for the size indicator.
     * 
     * @param data the raw export data
     * @param expectedSize the expected texture data size in bytes
     * @return the offset where texture data starts, or -1 if not found
     */
    public static int findTextureData(byte[] data, int expectedSize) {
        // Try to find compact index that matches expected size
        for (int i = 0; i < data.length - expectedSize - 10; i++) {
            int size = readCompactIndex(data, i);
            if (size == expectedSize && i + 5 + expectedSize <= data.length) {
                int indexLen = compactIndexLength(size);
                return i + indexLen;
            }
        }
        
        // Fallback: look for the data near the end
        int searchStart = Math.max(0, data.length - expectedSize - 100);
        for (int i = searchStart; i < data.length - expectedSize; i++) {
            int size = readCompactIndex(data, i);
            if (size == expectedSize) {
                int indexLen = compactIndexLength(size);
                return i + indexLen;
            }
        }
        
        return -1;
    }
    
    // ==================== DXT Decompression ====================
    
    /**
     * Decompress DXT1 texture data to an ARGB image.
     * 
     * @param dxtData the compressed DXT1 data
     * @param width texture width (must be multiple of 4)
     * @param height texture height (must be multiple of 4)
     * @return the decompressed image
     */
    public static BufferedImage decompressDXT1(byte[] dxtData, int width, int height) {
        BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
        ByteBuffer buffer = ByteBuffer.wrap(dxtData).order(ByteOrder.LITTLE_ENDIAN);
        
        int blockWidth = width / 4;
        int blockHeight = height / 4;
        
        for (int by = 0; by < blockHeight; by++) {
            for (int bx = 0; bx < blockWidth; bx++) {
                int c0 = buffer.getShort() & 0xFFFF;
                int c1 = buffer.getShort() & 0xFFFF;
                int indices = buffer.getInt();
                
                int[] colors = new int[4];
                colors[0] = rgb565ToArgb(c0);
                colors[1] = rgb565ToArgb(c1);
                
                if (c0 > c1) {
                    colors[2] = interpolateColor(colors[0], colors[1], 2, 1);
                    colors[3] = interpolateColor(colors[0], colors[1], 1, 2);
                } else {
                    colors[2] = interpolateColor(colors[0], colors[1], 1, 1);
                    colors[3] = 0x00000000; // transparent
                }
                
                for (int py = 0; py < 4; py++) {
                    for (int px = 0; px < 4; px++) {
                        int idx = (indices >> ((py * 4 + px) * 2)) & 0x3;
                        int x = bx * 4 + px;
                        int y = by * 4 + py;
                        if (x < width && y < height) {
                            image.setRGB(x, y, colors[idx]);
                        }
                    }
                }
            }
        }
        
        return image;
    }
    
    /**
     * Decompress DXT3 texture data to an ARGB image.
     * 
     * @param dxtData the compressed DXT3 data
     * @param width texture width (must be multiple of 4)
     * @param height texture height (must be multiple of 4)
     * @return the decompressed image
     */
    public static BufferedImage decompressDXT3(byte[] dxtData, int width, int height) {
        BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
        ByteBuffer buffer = ByteBuffer.wrap(dxtData).order(ByteOrder.LITTLE_ENDIAN);
        
        int blockWidth = width / 4;
        int blockHeight = height / 4;
        
        for (int by = 0; by < blockHeight; by++) {
            for (int bx = 0; bx < blockWidth; bx++) {
                // DXT3: 8 bytes explicit alpha, then 8 bytes DXT1 color block
                long alphaBlock = buffer.getLong();
                
                int c0 = buffer.getShort() & 0xFFFF;
                int c1 = buffer.getShort() & 0xFFFF;
                int indices = buffer.getInt();
                
                int[] colors = new int[4];
                colors[0] = rgb565ToRgb(c0);
                colors[1] = rgb565ToRgb(c1);
                colors[2] = interpolateColorNoAlpha(colors[0], colors[1], 2, 1);
                colors[3] = interpolateColorNoAlpha(colors[0], colors[1], 1, 2);
                
                for (int py = 0; py < 4; py++) {
                    for (int px = 0; px < 4; px++) {
                        int idx = (indices >> ((py * 4 + px) * 2)) & 0x3;
                        int alphaIdx = py * 4 + px;
                        int alpha = (int) ((alphaBlock >> (alphaIdx * 4)) & 0xF) * 17; // Scale 0-15 to 0-255
                        
                        int x = bx * 4 + px;
                        int y = by * 4 + py;
                        if (x < width && y < height) {
                            int argb = (alpha << 24) | colors[idx];
                            image.setRGB(x, y, argb);
                        }
                    }
                }
            }
        }
        
        return image;
    }
    
    /**
     * Decompress DXT5 texture data to an ARGB image.
     * 
     * @param dxtData the compressed DXT5 data
     * @param width texture width (must be multiple of 4)
     * @param height texture height (must be multiple of 4)
     * @return the decompressed image
     */
    public static BufferedImage decompressDXT5(byte[] dxtData, int width, int height) {
        BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
        ByteBuffer buffer = ByteBuffer.wrap(dxtData).order(ByteOrder.LITTLE_ENDIAN);
        
        int blockWidth = width / 4;
        int blockHeight = height / 4;
        
        for (int by = 0; by < blockHeight; by++) {
            for (int bx = 0; bx < blockWidth; bx++) {
                // DXT5: 2 bytes alpha endpoints + 6 bytes alpha indices + 8 bytes color
                int alpha0 = buffer.get() & 0xFF;
                int alpha1 = buffer.get() & 0xFF;
                
                // Read 6 bytes of alpha indices (48 bits for 16 pixels, 3 bits each)
                long alphaIndices = 0;
                for (int i = 0; i < 6; i++) {
                    alphaIndices |= ((long)(buffer.get() & 0xFF)) << (i * 8);
                }
                
                // Build alpha lookup table
                int[] alphas = new int[8];
                alphas[0] = alpha0;
                alphas[1] = alpha1;
                if (alpha0 > alpha1) {
                    for (int i = 1; i <= 6; i++) {
                        alphas[i + 1] = ((7 - i) * alpha0 + i * alpha1) / 7;
                    }
                } else {
                    for (int i = 1; i <= 4; i++) {
                        alphas[i + 1] = ((5 - i) * alpha0 + i * alpha1) / 5;
                    }
                    alphas[6] = 0;
                    alphas[7] = 255;
                }
                
                int c0 = buffer.getShort() & 0xFFFF;
                int c1 = buffer.getShort() & 0xFFFF;
                int indices = buffer.getInt();
                
                int[] colors = new int[4];
                colors[0] = rgb565ToRgb(c0);
                colors[1] = rgb565ToRgb(c1);
                colors[2] = interpolateColorNoAlpha(colors[0], colors[1], 2, 1);
                colors[3] = interpolateColorNoAlpha(colors[0], colors[1], 1, 2);
                
                for (int py = 0; py < 4; py++) {
                    for (int px = 0; px < 4; px++) {
                        int idx = (indices >> ((py * 4 + px) * 2)) & 0x3;
                        int alphaIdx = (int) ((alphaIndices >> ((py * 4 + px) * 3)) & 0x7);
                        
                        int x = bx * 4 + px;
                        int y = by * 4 + py;
                        if (x < width && y < height) {
                            image.setRGB(x, y, (alphas[alphaIdx] << 24) | colors[idx]);
                        }
                    }
                }
            }
        }
        
        return image;
    }
    
    // ==================== Other Format Extraction ====================
    
    /**
     * Extract RGBA8 texture data to an image.
     * 
     * @param exportData the raw export data
     * @param width texture width
     * @param height texture height
     * @return the extracted image, or null if data not found
     */
    public static BufferedImage extractRGBA8(byte[] exportData, int width, int height) {
        int rgba8Size = width * height * 4;
        int dataOffset = findTextureData(exportData, rgba8Size);
        if (dataOffset < 0) return null;
        
        BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
        
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                int idx = dataOffset + (y * width + x) * 4;
                int b = exportData[idx] & 0xFF;
                int g = exportData[idx + 1] & 0xFF;
                int r = exportData[idx + 2] & 0xFF;
                int a = exportData[idx + 3] & 0xFF;
                image.setRGB(x, y, (a << 24) | (r << 16) | (g << 8) | b);
            }
        }
        
        return image;
    }
    
    /**
     * Extract P8 (palette 8-bit) texture data to a grayscale image.
     * 
     * @param exportData the raw export data
     * @param width texture width
     * @param height texture height
     * @return the extracted image, or null if data not found
     */
    public static BufferedImage extractP8(byte[] exportData, int width, int height) {
        int p8Size = width * height;
        int dataOffset = findTextureData(exportData, p8Size);
        if (dataOffset < 0) return null;
        
        BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_BYTE_GRAY);
        
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                int gray = exportData[dataOffset + y * width + x] & 0xFF;
                int rgb = (gray << 16) | (gray << 8) | gray;
                image.setRGB(x, y, rgb);
            }
        }
        
        return image;
    }
    
    /**
     * Extract G16 (16-bit grayscale) texture data to an image.
     * 
     * @param exportData the raw export data
     * @param width texture width
     * @param height texture height
     * @return the extracted image, or null if data not found
     */
    public static BufferedImage extractG16(byte[] exportData, int width, int height) {
        int g16Size = width * height * 2;
        int dataOffset = findTextureData(exportData, g16Size);
        if (dataOffset < 0) return null;
        
        BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_USHORT_GRAY);
        ByteBuffer buffer = ByteBuffer.wrap(exportData, dataOffset, g16Size);
        buffer.order(ByteOrder.LITTLE_ENDIAN);
        
        short[] raster = new short[width * height];
        for (int i = 0; i < raster.length; i++) {
            raster[i] = buffer.getShort();
        }
        image.getRaster().setDataElements(0, 0, width, height, raster);
        
        return image;
    }
    
    /**
     * Extract DXT1 texture data to an image.
     * 
     * @param exportData the raw export data
     * @param width texture width
     * @param height texture height
     * @return the extracted image, or null if data not found
     */
    public static BufferedImage extractDXT1(byte[] exportData, int width, int height) {
        int dxt1Size = (width / 4) * (height / 4) * 8;
        int dataOffset = findTextureData(exportData, dxt1Size);
        if (dataOffset < 0) return null;
        
        byte[] dxtData = new byte[dxt1Size];
        System.arraycopy(exportData, dataOffset, dxtData, 0, dxt1Size);
        
        return decompressDXT1(dxtData, width, height);
    }
    
    /**
     * Extract DXT3 texture data to an image.
     * 
     * @param exportData the raw export data
     * @param width texture width
     * @param height texture height
     * @return the extracted image, or null if data not found
     */
    public static BufferedImage extractDXT3(byte[] exportData, int width, int height) {
        int dxt3Size = (width / 4) * (height / 4) * 16;
        int dataOffset = findTextureData(exportData, dxt3Size);
        if (dataOffset < 0) return null;
        
        byte[] dxtData = new byte[dxt3Size];
        System.arraycopy(exportData, dataOffset, dxtData, 0, dxt3Size);
        
        return decompressDXT3(dxtData, width, height);
    }
    
    /**
     * Extract DXT5 texture data to an image.
     * 
     * @param exportData the raw export data
     * @param width texture width
     * @param height texture height
     * @return the extracted image, or null if data not found
     */
    public static BufferedImage extractDXT5(byte[] exportData, int width, int height) {
        int dxt5Size = (width / 4) * (height / 4) * 16;
        int dataOffset = findTextureData(exportData, dxt5Size);
        if (dataOffset < 0) return null;
        
        byte[] dxtData = new byte[dxt5Size];
        System.arraycopy(exportData, dataOffset, dxtData, 0, dxt5Size);
        
        return decompressDXT5(dxtData, width, height);
    }
    
    // ==================== Color Conversion Helpers ====================
    
    /**
     * Convert RGB565 color to ARGB (with full alpha).
     */
    public static int rgb565ToArgb(int color) {
        int r = ((color >> 11) & 0x1F) * 255 / 31;
        int g = ((color >> 5) & 0x3F) * 255 / 63;
        int b = (color & 0x1F) * 255 / 31;
        return 0xFF000000 | (r << 16) | (g << 8) | b;
    }
    
    /**
     * Convert RGB565 color to RGB (no alpha).
     */
    public static int rgb565ToRgb(int color) {
        int r = ((color >> 11) & 0x1F) * 255 / 31;
        int g = ((color >> 5) & 0x3F) * 255 / 63;
        int b = (color & 0x1F) * 255 / 31;
        return (r << 16) | (g << 8) | b;
    }
    
    /**
     * Interpolate between two ARGB colors.
     */
    public static int interpolateColor(int c0, int c1, int w0, int w1) {
        int total = w0 + w1;
        int r = (((c0 >> 16) & 0xFF) * w0 + ((c1 >> 16) & 0xFF) * w1) / total;
        int g = (((c0 >> 8) & 0xFF) * w0 + ((c1 >> 8) & 0xFF) * w1) / total;
        int b = ((c0 & 0xFF) * w0 + (c1 & 0xFF) * w1) / total;
        return 0xFF000000 | (r << 16) | (g << 8) | b;
    }
    
    /**
     * Interpolate between two RGB colors (no alpha).
     */
    public static int interpolateColorNoAlpha(int c0, int c1, int w0, int w1) {
        int total = w0 + w1;
        int r = (((c0 >> 16) & 0xFF) * w0 + ((c1 >> 16) & 0xFF) * w1) / total;
        int g = (((c0 >> 8) & 0xFF) * w0 + ((c1 >> 8) & 0xFF) * w1) / total;
        int b = ((c0 & 0xFF) * w0 + (c1 & 0xFF) * w1) / total;
        return (r << 16) | (g << 8) | b;
    }
}
