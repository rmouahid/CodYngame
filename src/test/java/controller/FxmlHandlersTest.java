package controller;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;

/**
 * Checks that every {@code onAction="#handler"} of an FXML view names a method
 * of its {@code fx:controller}, so a button can never point at a missing or
 * removed handler (FXMLLoader would only fail at runtime, when the view opens).
 */
class FxmlHandlersTest {

    private static final Path SOURCES = Paths.get("src", "main", "java");
    private static final Path UI_RESOURCES = Paths.get("src", "main", "resources", "ui");
    private static final Pattern CONTROLLER = Pattern.compile("fx:controller=\"([\\w.]+)\"");
    private static final Pattern HANDLER = Pattern.compile("on\\w+=\"#(\\w+)\"");

    @Test
    void everyFxmlHandlerExistsInItsController() throws IOException {
        List<String> missing = new ArrayList<>();
        try (Stream<Path> views = Files.list(UI_RESOURCES)) {
            for (Path view : views.filter(p -> p.toString().endsWith(".fxml")).toList()) {
                String fxml = Files.readString(view);
                Matcher controller = CONTROLLER.matcher(fxml);
                if (!controller.find()) {
                    continue;
                }
                Path source = SOURCES.resolve(controller.group(1).replace('.', '/') + ".java");
                String code = Files.readString(source);
                Matcher handler = HANDLER.matcher(fxml);
                while (handler.find()) {
                    String name = handler.group(1);
                    if (!Pattern.compile("\\bvoid\\s+" + name + "\\s*\\(").matcher(code).find()) {
                        missing.add(view.getFileName() + " -> " + controller.group(1) + "#" + name);
                    }
                }
            }
        }
        assertTrue(missing.isEmpty(), "Handlers missing from their controller: " + missing);
    }
}
