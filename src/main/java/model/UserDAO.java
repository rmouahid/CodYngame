package model;

import java.sql.*;

import utils.PasswordUtils;

/**
 * DAO for handling CRUD operations related to the {@link User} entity.
 */
public class UserDAO {

    /**
     * Inserts a new user into the database.
     *
     * @param user the User object to insert
     * @return true if the insertion was successful, false otherwise
     */
    public boolean addUser(User user) {
        String sql = "INSERT INTO User (username, email, password_hash) VALUES (?, ?, ?)";

        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {

            stmt.setString(1, user.getUsername());
            stmt.setString(2, user.getEmail());
            stmt.setString(3, user.getPasswordHash());

            int rowsInserted = stmt.executeUpdate();
            System.out.println("User inserted (" + rowsInserted + " row(s))");
            return rowsInserted > 0;

        } catch (SQLException e) {
            System.err.println("SQL Error during user insertion: " + e.getMessage());
            return false;
        }
    }

    /**
     * Verifies user credentials. A legacy unsalted hash is replaced by a salted
     * PBKDF2 hash when the password matches.
     *
     * @param username the username entered
     * @param password the plaintext password entered
     * @return true if the username exists and the password matches, false otherwise
     */
    public boolean checkLogin(String username, String password) {
        try (Connection conn = DatabaseConnection.getConnection()) {
            return checkLogin(conn, username, password);
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return false;
    }

    /**
     * Same as {@link #checkLogin(String, String)} on a given connection.
     *
     * @param conn     the database connection
     * @param username the username entered
     * @param password the plaintext password entered
     * @return true if the username exists and the password matches, false otherwise
     * @throws SQLException if a query fails
     */
    boolean checkLogin(Connection conn, String username, String password) throws SQLException {
        int id;
        String stored;
        try (PreparedStatement ps = conn.prepareStatement(
                "SELECT id, password_hash FROM User WHERE username = ?")) {
            ps.setString(1, username);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) {
                    return false;
                }
                id = rs.getInt("id");
                stored = rs.getString("password_hash");
            }
        }
        if (!PasswordUtils.verify(password, stored)) {
            return false;
        }
        if (PasswordUtils.needsRehash(stored)) {
            try (PreparedStatement ps = conn.prepareStatement(
                    "UPDATE User SET password_hash = ? WHERE id = ?")) {
                ps.setString(1, PasswordUtils.hashPassword(password));
                ps.setInt(2, id);
                ps.executeUpdate();
            }
        }
        return true;
    }

    /**
     * Checks whether a username is already taken.
     *
     * @param username the username to check
     * @return true if the user exists, false otherwise
     */
    public boolean userExists(String username) {
        String sql = "SELECT id FROM User WHERE username = ?";
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {

            stmt.setString(1, username);
            ResultSet rs = stmt.executeQuery();
            return rs.next();

        } catch (SQLException e) {
            e.printStackTrace();
            return false;
        }
    }

    /**
     * Increases the score of the specified user.
     *
     * @param userId the user ID
     * @param points number of points to add to the user's score
     * @throws SQLException if a database error occurs
     */
    public void updateUserScore(int userId, int points) throws SQLException {
        String sql = "UPDATE User SET score = score + ? WHERE id = ?";
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {

            stmt.setInt(1, points);
            stmt.setInt(2, userId);
            stmt.executeUpdate();
        }
    }

    /**
     * Retrieves a User object based on the username.
     *
     * @param username the username to search
     * @return a populated User object or null if not found
     */
    public User getUserByUsername(String username) {
        String sql = "SELECT * FROM User WHERE username = ?";
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {

            stmt.setString(1, username);
            ResultSet rs = stmt.executeQuery();

            if (rs.next()) {
                User user = new User();
                user.setId(rs.getInt("id"));
                user.setUsername(rs.getString("username"));
                user.setEmail(rs.getString("email"));
                user.setPasswordHash(rs.getString("password_hash"));
                user.setScore(rs.getInt("score"));
                user.setCreatedAt(rs.getTimestamp("created_at"));
                return user;
            }

        } catch (Exception e) {
            e.printStackTrace();
        }
        return null;
    }
}
