package model;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import utils.PasswordUtils;

/**
 * Runs the login query against an in-memory H2 database in MySQL mode.
 */
class UserDAOTest {

    private static final String LEGACY_SECRET =
            "2bb80d537b1da3e38bd30361aa855686bde0eacd7162fef6a25fe97bf527a25b";

    private Connection conn;
    private final UserDAO dao = new UserDAO();

    @BeforeEach
    void setUp() throws SQLException {
        conn = DriverManager.getConnection("jdbc:h2:mem:users;MODE=MySQL;NON_KEYWORDS=USER");
        try (Statement stmt = conn.createStatement()) {
            stmt.execute("""
                CREATE TABLE User (
                    id INT PRIMARY KEY AUTO_INCREMENT,
                    username VARCHAR(255) NOT NULL UNIQUE,
                    email TEXT,
                    password_hash TEXT
                )""");
        }
    }

    @AfterEach
    void tearDown() throws SQLException {
        try (Statement stmt = conn.createStatement()) {
            stmt.execute("DROP ALL OBJECTS");
        }
        conn.close();
    }

    @Test
    void acceptsSaltedHashWithoutRewritingIt() throws SQLException {
        String hash = PasswordUtils.hashPassword("secret");
        insert("alice", hash);

        assertTrue(dao.checkLogin(conn, "alice", "secret"));
        assertEquals(hash, storedHash("alice"));
    }

    @Test
    void rejectsWrongPasswordAndUnknownUser() throws SQLException {
        insert("alice", PasswordUtils.hashPassword("secret"));

        assertFalse(dao.checkLogin(conn, "alice", "wrong"));
        assertFalse(dao.checkLogin(conn, "bob", "secret"));
    }

    @Test
    void upgradesLegacyHashOnSuccessfulLogin() throws SQLException {
        insert("alice", LEGACY_SECRET);

        assertTrue(dao.checkLogin(conn, "alice", "secret"));

        String upgraded = storedHash("alice");
        assertTrue(upgraded.startsWith("pbkdf2$"));
        assertTrue(PasswordUtils.verify("secret", upgraded));
        assertTrue(dao.checkLogin(conn, "alice", "secret"));
    }

    @Test
    void keepsLegacyHashAfterFailedLogin() throws SQLException {
        insert("alice", LEGACY_SECRET);

        assertFalse(dao.checkLogin(conn, "alice", "wrong"));
        assertEquals(LEGACY_SECRET, storedHash("alice"));
    }

    private void insert(String username, String hash) throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement(
                "INSERT INTO User (username, email, password_hash) VALUES (?, ?, ?)")) {
            ps.setString(1, username);
            ps.setString(2, username + "@example.com");
            ps.setString(3, hash);
            ps.executeUpdate();
        }
    }

    private String storedHash(String username) throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement("SELECT password_hash FROM User WHERE username = ?")) {
            ps.setString(1, username);
            try (ResultSet rs = ps.executeQuery()) {
                rs.next();
                return rs.getString(1);
            }
        }
    }
}
