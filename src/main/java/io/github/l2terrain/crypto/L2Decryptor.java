package io.github.l2terrain.crypto;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Decryptor for Lineage 2 encrypted packages.
 * 
 * <p>L2 encrypted files have a 28-byte header "Lineage2VerXXX" (UTF-16LE)
 * followed by XOR-encrypted data.</p>
 * 
 * <p>Supported versions:</p>
 * <ul>
 *   <li>Ver 111: Fixed XOR key 0xAC</li>
 *   <li>Ver 121+: Filename-derived XOR key</li>
 * </ul>
 */
public final class L2Decryptor {
    
    /** L2 encryption header size (14 chars * 2 bytes = 28 bytes UTF-16LE) */
    public static final int HEADER_SIZE = 28;
    
    /** Fixed XOR key for Ver 111 */
    private static final int VER_111_KEY = 0xAC;
    
    private L2Decryptor() {
        // Utility class - prevent instantiation
    }
    
    /**
     * Derive XOR key from filename.
     * Key = sum of lowercase filename characters & 0xFF
     * 
     * @param filename the package filename (without path, with extension)
     * @return the XOR key byte (0-255)
     */
    public static int deriveKey(String filename) {
        int key = 0;
        for (char c : filename.toLowerCase().toCharArray()) {
            key += c;
        }
        return key & 0xFF;
    }
    
    /**
     * Check if data represents an L2 encrypted file.
     * 
     * @param data the file data (at least HEADER_SIZE bytes)
     * @return true if this is an L2 encrypted file
     */
    public static boolean isL2Encrypted(byte[] data) {
        if (data.length < HEADER_SIZE) return false;
        return parseHeader(data) != null;
    }
    
    /**
     * Parse the L2 header and return the version string.
     * 
     * @param data the file data
     * @return the header string (e.g., "Lineage2Ver121") or null if invalid
     */
    public static String parseHeader(byte[] data) {
        if (data.length < HEADER_SIZE) return null;
        String header = new String(data, 0, HEADER_SIZE, StandardCharsets.UTF_16LE);
        return header.matches("Lineage2Ver\\d{3}") ? header : null;
    }
    
    /**
     * Get the L2 encryption version from the header.
     * 
     * @param data the file data
     * @return the version number (e.g., 111, 121) or -1 if not L2 encrypted
     */
    public static int getVersion(byte[] data) {
        String header = parseHeader(data);
        if (header == null) return -1;
        return Integer.parseInt(header.substring(11));
    }
    
    /**
     * Get the XOR key for decryption based on version and filename.
     * 
     * @param version the L2 encryption version
     * @param filename the package filename
     * @return the XOR key to use
     */
    public static int getKeyForVersion(int version, String filename) {
        return version == 111 ? VER_111_KEY : deriveKey(filename);
    }
    
    /**
     * Decrypt L2 package data in-place.
     * Skips the 28-byte header and only decrypts the payload.
     * 
     * @param data the encrypted data (will be modified in-place)
     * @param key the XOR key
     */
    public static void decrypt(byte[] data, int key) {
        for (int i = HEADER_SIZE; i < data.length; i++) {
            data[i] = (byte) (data[i] ^ key);
        }
    }
    
    /**
     * Decrypt L2 package data in-place, auto-detecting version and deriving key.
     * 
     * @param data the encrypted data (will be modified in-place)
     * @param filename the package filename (used for key derivation)
     * @throws IllegalArgumentException if data is not a valid L2 encrypted file
     */
    public static void decrypt(byte[] data, String filename) {
        int version = getVersion(data);
        if (version < 0) {
            throw new IllegalArgumentException("Not a valid L2 encrypted file");
        }
        decrypt(data, getKeyForVersion(version, filename));
    }
    
    /**
     * Decrypt an L2 encrypted file to a new file.
     * The output file will not contain the L2 header.
     * 
     * @param input the encrypted input file
     * @param output the decrypted output file
     * @throws IOException if reading or writing fails
     * @throws IllegalArgumentException if input is not a valid L2 encrypted file
     */
    public static void decryptFile(Path input, Path output) throws IOException {
        try (InputStream in = new BufferedInputStream(Files.newInputStream(input));
             OutputStream out = new BufferedOutputStream(Files.newOutputStream(output))) {
            
            // Read and validate header
            byte[] header = new byte[HEADER_SIZE];
            if (in.read(header) != HEADER_SIZE) {
                throw new IOException("Failed to read L2 header");
            }
            
            String headerStr = parseHeader(header);
            if (headerStr == null) {
                throw new IllegalArgumentException("Not a valid L2 encrypted file");
            }
            
            int version = Integer.parseInt(headerStr.substring(11));
            int xorKey = getKeyForVersion(version, input.getFileName().toString());
            
            // Decrypt and write
            byte[] buffer = new byte[8192];
            int bytesRead;
            while ((bytesRead = in.read(buffer)) != -1) {
                for (int i = 0; i < bytesRead; i++) {
                    buffer[i] = (byte) (buffer[i] ^ xorKey);
                }
                out.write(buffer, 0, bytesRead);
            }
        }
    }
}
