package io.github.l2terrain.extractors;

import io.github.l2terrain.crypto.L2Decryptor;
import io.github.l2terrain.model.TerrainTile;
import io.github.l2terrain.model.TileCoordinates;
import net.shrimpworks.unreal.packages.Package;
import net.shrimpworks.unreal.packages.PackageReader;
import net.shrimpworks.unreal.packages.entities.ExportedObject;
import net.shrimpworks.unreal.packages.entities.objects.Texture;
import net.shrimpworks.unreal.packages.entities.properties.IntegerProperty;
import net.shrimpworks.unreal.packages.entities.properties.Property;

import java.io.IOException;
import java.lang.reflect.Field;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

/**
 * Extractor for G16 heightmap textures from Lineage 2 .utx packages.
 * 
 * <p>G16 is a raw 16-bit grayscale format used for terrain heightmaps.
 * Uses unreal-package-lib for proper package parsing.</p>
 */
public class HeightmapExtractor implements Extractor {
    
    /** Marker pattern that precedes G16 data: 00 40 80 10 */
    private static final byte[] G16_MARKER = {0x00, 0x40, (byte) 0x80, 0x10};
    
    /** Standard G16 tile size */
    private static final int TILE_SIZE = 256;
    
    /** Expected G16 data size: 256 * 256 * 2 bytes */
    private static final int EXPECTED_DATA_SIZE = TILE_SIZE * TILE_SIZE * 2;
    
    @Override
    public String getName() {
        return "Heightmap (G16)";
    }
    
    @Override
    public List<String> getFilePatterns() {
        return List.of("t_*_*.utx", "T_*_*.utx");
    }
    
    @Override
    public boolean canHandle(Path file) {
        String name = file.getFileName().toString().toLowerCase();
        return name.matches("t_\\d+_\\d+(_tx)?\\.utx");
    }
    
    @Override
    public TerrainTile extract(Path file) throws IOException {
        String filename = file.getFileName().toString();
        
        // Extract coordinates from filename
        TileCoordinates coords = TileCoordinates.fromFilename(filename);
        if (coords == null) {
            throw new IOException("Cannot parse coordinates from filename: " + filename);
        }
        
        // Decrypt L2 file to temp
        Path tempFile = Files.createTempFile("l2_", ".utx");
        try {
            L2Decryptor.decryptFile(file, tempFile);
            return extractFromDecryptedPackage(tempFile, coords, filename);
        } finally {
            Files.deleteIfExists(tempFile);
        }
    }
    
    /**
     * Extract G16 texture from a decrypted package.
     */
    private TerrainTile extractFromDecryptedPackage(Path packagePath, TileCoordinates coords, String sourceFilename) throws IOException {
        try (Package pkg = new Package(new PackageReader(packagePath))) {
            for (ExportedObject obj : pkg.objects) {
                if (obj == null) continue;
                
                String cls = obj.classIndex.get().name().name;
                if (!cls.equals("Texture")) continue;
                
                net.shrimpworks.unreal.packages.entities.objects.Object texObj = pkg.object(obj);
                if (!(texObj instanceof Texture tex)) continue;
                if (!tex.format().name().equals("G16")) continue;
                
                int[] dimensions = getTextureDimensions(tex);
                int[] heightData = extractHeightData(tex, obj, dimensions[0] * dimensions[1]);
                
                return new TerrainTile(coords, dimensions[0], dimensions[1], heightData, sourceFilename);
            }
        } catch (IOException e) {
            throw e;
        } catch (Exception e) {
            throw new IOException("Failed to parse package: " + sourceFilename, e);
        }
        
        throw new IOException("No G16 texture found in file: " + sourceFilename);
    }
    
    /**
     * Get texture dimensions from properties.
     * @return [width, height]
     */
    private int[] getTextureDimensions(Texture tex) {
        int width = TILE_SIZE;
        int height = TILE_SIZE;
        
        for (Property prop : tex.properties) {
            if (prop.name.name.equals("USize") && prop instanceof IntegerProperty ip) {
                width = ip.value;
            } else if (prop.name.name.equals("VSize") && prop instanceof IntegerProperty ip) {
                height = ip.value;
            }
        }
        
        return new int[] {width, height};
    }
    
    /**
     * Extract height data from texture export.
     * Uses reflection to access the package reader (unfortunately necessary).
     */
    private int[] extractHeightData(Texture tex, ExportedObject obj, int pixelCount) throws IOException {
        try {
            // Access reader via reflection (library doesn't expose raw data access)
            Field readerField = net.shrimpworks.unreal.packages.entities.objects.Object.class.getDeclaredField("reader");
            readerField.setAccessible(true);
            PackageReader reader = (PackageReader) readerField.get(tex);
            
            // Read export data
            reader.moveTo(obj.pos);
            byte[] exportData = new byte[obj.size];
            reader.readBytes(exportData, 0, obj.size);
            
            // Find G16 marker
            int dataOffset = findG16DataOffset(exportData);
            if (dataOffset < 0) {
                throw new IOException("Cannot find G16 marker in export");
            }
            
            // Parse height data as unsigned 16-bit values
            int[] heightData = new int[pixelCount];
            ByteBuffer buffer = ByteBuffer.wrap(exportData, dataOffset, pixelCount * 2);
            buffer.order(ByteOrder.LITTLE_ENDIAN);
            
            for (int i = 0; i < pixelCount; i++) {
                heightData[i] = buffer.getShort() & 0xFFFF;
            }
            
            return heightData;
            
        } catch (ReflectiveOperationException e) {
            throw new IOException("Failed to access texture data via reflection", e);
        }
    }
    
    /**
     * Find the offset of G16 data by searching for the marker pattern.
     * @return offset after marker, or -1 if not found
     */
    private int findG16DataOffset(byte[] data) {
        int searchLimit = data.length - G16_MARKER.length - EXPECTED_DATA_SIZE;
        
        for (int i = 0; i <= searchLimit; i++) {
            if (matchesMarker(data, i)) {
                return i + G16_MARKER.length;
            }
        }
        return -1;
    }
    
    /**
     * Check if data matches G16 marker at given offset.
     */
    private boolean matchesMarker(byte[] data, int offset) {
        for (int j = 0; j < G16_MARKER.length; j++) {
            if (data[offset + j] != G16_MARKER[j]) {
                return false;
            }
        }
        return true;
    }
}
