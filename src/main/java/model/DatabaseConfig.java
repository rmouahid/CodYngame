package model;

import java.io.IOException;
import java.io.Reader;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.Properties;

/**
 * MySQL connection settings, kept out of the source code.
 *
 * <p>Each setting is read, in order of precedence, from:</p>
 * <ol>
 *     <li>a JVM system property ({@code -Dcodyngame.db.password=...})</li>
 *     <li>an environment variable ({@code CODYNGAME_DB_PASSWORD})</li>
 *     <li>the git-ignored {@code config.properties} file in the working directory
 *     ({@code db.password=...}, see {@code config.properties.example})</li>
 * </ol>
 *
 * <p>The URL and user have local-development defaults; the password has none, so a
 * missing password fails with an explanation instead of a cryptic access-denied error.</p>
 *
 * @param url      JDBC URL of the database
 * @param user     database user
 * @param password database password (may be empty if set explicitly)
 */
public record DatabaseConfig(String url, String user, String password) {

    public static final String DEFAULT_URL = "jdbc:mysql://localhost:3306/codingame";
    public static final String DEFAULT_USER = "root";
    public static final Path CONFIG_FILE = Path.of("config.properties");

    /** Reads the settings from the real system properties, environment and config file. */
    public static DatabaseConfig fromEnvironment() {
        return load(System.getProperties(), System.getenv(), CONFIG_FILE);
    }

    /**
     * Reads the settings from the given sources ({@code configFile} may be null or missing).
     *
     * @throws IllegalStateException if no password is set anywhere
     */
    public static DatabaseConfig load(Properties system, Map<String, String> env, Path configFile) {
        Properties file = readFile(configFile);
        String url = setting("url", system, env, file);
        String user = setting("user", system, env, file);
        String password = setting("password", system, env, file);
        if (password == null) {
            throw new IllegalStateException("No database password configured. Set the CODYNGAME_DB_PASSWORD "
                    + "environment variable, the codyngame.db.password system property, or db.password in "
                    + CONFIG_FILE + " (copy config.properties.example).");
        }
        return new DatabaseConfig(
                url == null || url.isBlank() ? DEFAULT_URL : url,
                user == null || user.isBlank() ? DEFAULT_USER : user,
                password);
    }

    private static String setting(String name, Properties system, Map<String, String> env, Properties file) {
        String value = system.getProperty("codyngame.db." + name);
        if (value == null) value = env.get("CODYNGAME_DB_" + name.toUpperCase());
        if (value == null) value = file.getProperty("db." + name);
        return value == null ? null : value.trim();
    }

    private static Properties readFile(Path configFile) {
        Properties file = new Properties();
        if (configFile == null || !Files.isRegularFile(configFile)) return file;
        try (Reader reader = Files.newBufferedReader(configFile, StandardCharsets.UTF_8)) {
            file.load(reader);
        } catch (IOException e) {
            throw new UncheckedIOException("Cannot read " + configFile, e);
        }
        return file;
    }

    /** Never prints the password (e.g. in logs or stack traces). */
    @Override
    public String toString() {
        return "DatabaseConfig[url=" + url + ", user=" + user + ", password=***]";
    }
}
