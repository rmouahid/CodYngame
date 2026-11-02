package utils;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Utility class for reading files from the file system.
 */
public class FileUtils {

    /**
     * Reads the contents of a file located at the specified path
     * and returns it as a byte array.
     *
     * @param path the file path (e.g., "pdf/exo1.pdf")
     * @return a byte array containing the file's binary content
     * @throws IOException if the file cannot be found or read
     */
    public static byte[] readFileAsBytes(String path) throws IOException {
        // Files.readAllBytes keeps reading until end of file, unlike a single
        // InputStream.read call, which may return before the buffer is full.
        return Files.readAllBytes(Path.of(path));
    }
}
