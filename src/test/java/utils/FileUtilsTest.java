package utils;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Random;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class FileUtilsTest {

    @TempDir
    Path dir;

    @Test
    void readsLargeFileCompletely() throws IOException {
        byte[] content = new byte[5 * 1024 * 1024];
        new Random(42).nextBytes(content);
        Path file = dir.resolve("exo.pdf");
        Files.write(file, content);

        assertArrayEquals(content, FileUtils.readFileAsBytes(file.toString()));
    }

    @Test
    void readsEmptyFile() throws IOException {
        Path file = Files.createFile(dir.resolve("empty.pdf"));

        assertArrayEquals(new byte[0], FileUtils.readFileAsBytes(file.toString()));
    }

    @Test
    void missingFileThrows() {
        assertThrows(IOException.class,
                () -> FileUtils.readFileAsBytes(dir.resolve("missing.pdf").toString()));
    }
}
