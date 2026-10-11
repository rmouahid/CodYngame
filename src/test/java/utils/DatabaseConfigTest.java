package utils;

import model.DatabaseConfig;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.Properties;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;

class DatabaseConfigTest {

    @Test
    void readsCredentialsFromEnvironmentVariables() {
        DatabaseConfig config = DatabaseConfig.load(new Properties(), Map.of(
                "CODYNGAME_DB_URL", "jdbc:mysql://db:3306/codingame",
                "CODYNGAME_DB_USER", "app",
                "CODYNGAME_DB_PASSWORD", "s3cret"), null);

        assertEquals("jdbc:mysql://db:3306/codingame", config.url());
        assertEquals("app", config.user());
        assertEquals("s3cret", config.password());
    }

    @Test
    void systemPropertiesOverrideEnvironmentVariables() {
        Properties system = new Properties();
        system.setProperty("codyngame.db.password", "from-property");

        DatabaseConfig config = DatabaseConfig.load(system, Map.of("CODYNGAME_DB_PASSWORD", "from-env"), null);

        assertEquals("from-property", config.password());
    }

    @Test
    void fallsBackToTheConfigFileThenToDefaults(@TempDir Path dir) throws Exception {
        Path file = dir.resolve("config.properties");
        Files.writeString(file, "db.password=from-file\ndb.user=filer\n");

        DatabaseConfig config = DatabaseConfig.load(new Properties(), Map.of(), file);

        assertEquals("from-file", config.password());
        assertEquals("filer", config.user());
        assertEquals(DatabaseConfig.DEFAULT_URL, config.url());
    }

    @Test
    void environmentOverridesTheConfigFile(@TempDir Path dir) throws Exception {
        Path file = dir.resolve("config.properties");
        Files.writeString(file, "db.password=from-file\n");

        DatabaseConfig config = DatabaseConfig.load(new Properties(), Map.of("CODYNGAME_DB_PASSWORD", "from-env"), file);

        assertEquals("from-env", config.password());
    }

    @Test
    void aMissingPasswordExplainsHowToProvideIt() {
        IllegalStateException e = assertThrows(IllegalStateException.class,
                () -> DatabaseConfig.load(new Properties(), Map.of(), null));

        assertTrue(e.getMessage().contains("CODYNGAME_DB_PASSWORD"), e.getMessage());
        assertTrue(e.getMessage().contains("config.properties"), e.getMessage());
    }

    @Test
    void anEmptyPasswordIsAllowedWhenSetExplicitly() {
        DatabaseConfig config = DatabaseConfig.load(new Properties(), Map.of("CODYNGAME_DB_PASSWORD", ""), null);

        assertEquals("", config.password());
    }

    @Test
    void noModelSourceHardcodesAPassword() throws Exception {
        // A string literal assigned to a password variable, e.g. PASSWORD = "..."
        Pattern literal = Pattern.compile("(?i)password\\s*=\\s*\"[^\"]+\"");
        try (Stream<Path> sources = Files.walk(Path.of("src/main/java/model"))) {
            for (Path source : (Iterable<Path>) sources.filter(p -> p.toString().endsWith(".java"))::iterator) {
                assertFalse(literal.matcher(Files.readString(source)).find(), source + " hardcodes a password");
            }
        }
    }
}
