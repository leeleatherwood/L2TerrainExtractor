package io.github.l2terrain.utils;

import net.shrimpworks.unreal.packages.Package;
import net.shrimpworks.unreal.packages.PackageReader;
import net.shrimpworks.unreal.packages.entities.ExportedObject;
import net.shrimpworks.unreal.packages.entities.objects.Object;

import java.io.IOException;
import java.lang.reflect.Field;

/**
 * Utility methods for working with Unreal packages.
 * 
 * <p>Provides helper methods for accessing package internals via reflection
 * when the unreal-package-lib doesn't expose the needed functionality.</p>
 */
public final class UnrealPackageUtils {
    
    private static Field packageReaderField;
    private static Field objectReaderField;
    
    static {
        try {
            packageReaderField = Package.class.getDeclaredField("reader");
            packageReaderField.setAccessible(true);
            
            objectReaderField = Object.class.getDeclaredField("reader");
            objectReaderField.setAccessible(true);
        } catch (NoSuchFieldException e) {
            throw new RuntimeException("Failed to initialize reflection fields", e);
        }
    }
    
    private UnrealPackageUtils() {
        // Utility class - prevent instantiation
    }
    
    /**
     * Get the PackageReader from a Package via reflection.
     * 
     * @param pkg the package
     * @return the package reader
     * @throws IOException if reflection fails
     */
    public static PackageReader getPackageReader(Package pkg) throws IOException {
        try {
            return (PackageReader) packageReaderField.get(pkg);
        } catch (IllegalAccessException e) {
            throw new IOException("Failed to access PackageReader via reflection", e);
        }
    }
    
    /**
     * Get the PackageReader from an Object via reflection.
     * 
     * @param obj the unreal object
     * @return the package reader
     * @throws IOException if reflection fails
     */
    public static PackageReader getObjectReader(Object obj) throws IOException {
        try {
            return (PackageReader) objectReaderField.get(obj);
        } catch (IllegalAccessException e) {
            throw new IOException("Failed to access PackageReader via reflection", e);
        }
    }
    
    /**
     * Read raw export data from a package.
     * 
     * @param pkg the package
     * @param obj the exported object
     * @return the raw export data bytes
     * @throws IOException if reading fails
     */
    public static byte[] readExportData(Package pkg, ExportedObject obj) throws IOException {
        PackageReader reader = getPackageReader(pkg);
        reader.moveTo(obj.pos);
        byte[] data = new byte[obj.size];
        reader.readBytes(data, 0, obj.size);
        return data;
    }
    
    /**
     * Read raw export data from an unreal object.
     * 
     * @param unrealObj the unreal object (e.g., Texture)
     * @param exportObj the exported object with position/size info
     * @return the raw export data bytes
     * @throws IOException if reading fails
     */
    public static byte[] readExportData(Object unrealObj, ExportedObject exportObj) throws IOException {
        PackageReader reader = getObjectReader(unrealObj);
        reader.moveTo(exportObj.pos);
        byte[] data = new byte[exportObj.size];
        reader.readBytes(data, 0, exportObj.size);
        return data;
    }
}
