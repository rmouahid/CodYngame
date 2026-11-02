package controller;

import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.TextField;
import javafx.scene.control.PasswordField;
import javafx.scene.control.Button;
import javafx.stage.Stage;
import javafx.scene.control.Alert;

import model.User;
import utils.PasswordUtils;
import utils.Session;
import model.DatabaseConnection;

import java.io.IOException;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

/**
 * Controller for editing the user's profile.
 * Allows updating username, email, and password after verifying the current password.
 */
public class EditProfileController {

    @FXML private Button confirmButton;
    @FXML private Button backButton;
    @FXML private TextField usernameField;
    @FXML private TextField emailField;
    @FXML private PasswordField currentPasswordField;
    @FXML private PasswordField newPasswordField;

    /**
     * Initializes the form with current user information from the session.
     */
    @FXML
    public void initialize() {
        User u = Session.getInstance().getCurrentUser();
        usernameField.setText(u.getUsername());
        emailField.setText(u.getEmail());
    }

    /**
     * Handles the "Back" button click.
     * Redirects the user back to the profile page.
     */
    @FXML
    private void handleBack() {
        try {
            Parent root = FXMLLoader.load(getClass().getResource("/ui/profil.fxml"));
            Stage stage = (Stage) backButton.getScene().getWindow();
            stage.setScene(new Scene(root));
            stage.setTitle("My Profile");
        } catch (IOException e) {
            e.printStackTrace();
            showAlert("Error", "Unable to return to the previous page.");
        }
    }

    /**
     * Handles the confirmation of profile changes.
     * Verifies the current password and updates only the changed fields.
     */
    @FXML
    private void handleConfirm() {
        User u = Session.getInstance().getCurrentUser();
        if (u == null) {
            showAlert("Error", "No user currently logged in.");
            return;
        }

        String newUsername = usernameField.getText().trim();
        String newEmail    = emailField.getText().trim();
        String currentPwd  = currentPasswordField.getText();
        String newPwd      = newPasswordField.getText();

        // Verify current password
        if (!PasswordUtils.verify(currentPwd, u.getPasswordHash())) {
            showAlert("Error", "Incorrect current password.");
            return;
        }

        // At least one field must change
        boolean changeUsername = !newUsername.isEmpty() && !newUsername.equals(u.getUsername());
        boolean changeEmail    = !newEmail.isEmpty()    && !newEmail.equals(u.getEmail());
        boolean changePwd      = !newPwd.isEmpty()      && !PasswordUtils.verify(newPwd, u.getPasswordHash());

        if (!changeUsername && !changeEmail && !changePwd) {
            showAlert("Warning", "Please modify at least one field.");
            return;
        }

        // Update in database
        try (Connection conn = DatabaseConnection.getConnection()) {

            // Check if new username is already taken
            if (changeUsername) {
                PreparedStatement ps = conn.prepareStatement(
                        "SELECT 1 FROM `User` WHERE username = ? AND id <> ?");
                ps.setString(1, newUsername);
                ps.setInt(2, u.getId());
                try (ResultSet rs = ps.executeQuery()) {
                    if (rs.next()) {
                        showAlert("Error", "This username is already taken.");
                        return;
                    }
                }
            }

            // Check if new email is already used
            if (changeEmail) {
                PreparedStatement ps = conn.prepareStatement(
                        "SELECT 1 FROM `User` WHERE email = ? AND id <> ?");
                ps.setString(1, newEmail);
                ps.setInt(2, u.getId());
                try (ResultSet rs = ps.executeQuery()) {
                    if (rs.next()) {
                        showAlert("Error", "This email is already registered.");
                        return;
                    }
                }
            }

            // Execute the update
            String sql = "UPDATE `User` SET username = ?, email = ?, password_hash = ? WHERE id = ?";
            PreparedStatement stmt = conn.prepareStatement(sql);
            stmt.setString(1, changeUsername ? newUsername : u.getUsername());
            stmt.setString(2, changeEmail    ? newEmail    : u.getEmail());

            String finalHash = changePwd ? PasswordUtils.hashPassword(newPwd) : u.getPasswordHash();
            stmt.setString(3, finalHash);
            stmt.setInt(4, u.getId());

            int updated = stmt.executeUpdate();
            if (updated > 0) {
                // Update user object and session
                if (changeUsername) u.setUsername(newUsername);
                if (changeEmail)    u.setEmail(newEmail);
                if (changePwd)      u.setPasswordHash(finalHash);
                Session.getInstance().setCurrentUser(u);

                showAlert("Success", "Profile successfully updated!");
            } else {
                showAlert("Error", "Failed to update profile.");
            }

        } catch (SQLException ex) {
            ex.printStackTrace();
            showAlert("Error", "Database error.");
        }
    }

    /**
     * Displays an alert dialog with a specified title and message.
     *
     * @param title   the title of the alert
     * @param message the message to display
     */
    private void showAlert(String title, String message) {
        Alert.AlertType type = Alert.AlertType.INFORMATION;
        if (title.equalsIgnoreCase("error")) {
            type = Alert.AlertType.ERROR;
        } else if (title.equalsIgnoreCase("warning")) {
            type = Alert.AlertType.WARNING;
        }
        Alert alert = new Alert(type);
        alert.setTitle(title);
        alert.setHeaderText(null);
        alert.setContentText(message);
        alert.showAndWait();
    }
}
