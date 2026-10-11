package model;

import java.sql.SQLException;

/**
 * Checks the database connection configured through {@link DatabaseConfig}.
 */
public class DatabaseTest {

    public static void main(String[] args) {
        try {
            DatabaseConnection.getConnection();
            System.out.println(" Connexion réussie à la base de données !");
        } catch (SQLException | IllegalStateException e) {
            System.out.println(" Connexion échouée : " + e.getMessage());
        } finally {
            DatabaseConnection.closeConnection();
        }
    }
}
