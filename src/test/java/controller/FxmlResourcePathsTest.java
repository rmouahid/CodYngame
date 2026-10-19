package controller;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Checks that every FXML view loaded by the controllers exists on the
 * classpath with the exact same casing. Classpath lookups are
 * case-sensitive on Linux, macOS and inside a JAR, so a casing mismatch
 * that works on Windows breaks navigation everywhere else.
 */
class FxmlResourcePathsTest {

    private static final Pattern FXML_PATH = Pattern.compile("\"(/ui/[^\"]+\\.fxml)\"");
    private static final Path SOURCES = Paths.get("src", "main", "java");
    private static final Path UI_RESOURCES = Paths.get("src", "main", "resources", "ui");

    @Test
    void everyReferencedFxmlExistsWithExactCasing() throws IOException {
        List<String> referenced = new ArrayList<>();
        try (Stream<Path> files = Files.walk(SOURCES)) {
            for (Path file : files.filter(p -> p.toString().endsWith(".java")).toList()) {
                Matcher matcher = FXML_PATH.matcher(Files.readString(file));
                while (matcher.find()) {
                    referenced.add(matcher.group(1));
                }
            }
        }
        assertFalse(referenced.isEmpty(), "no FXML path found in the controllers");

        List<String> onDisk;
        try (Stream<Path> files = Files.list(UI_RESOURCES)) {
            onDisk = files.map(p -> "/ui/" + p.getFileName()).toList();
        }
        for (String path : referenced) {
            // Compare names explicitly: Files.exists is case-insensitive on Windows.
            assertTrue(onDisk.contains(path), path + " does not match any file in " + UI_RESOURCES);
        }
    }
}
