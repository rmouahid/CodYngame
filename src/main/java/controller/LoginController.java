package controller;

import model.User;
import model.UserDAO;
import utils.PasswordUtils;
import utils.Session;

import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.PasswordField;
import javafx.scene.control.TextField;
import javafx.stage.Stage;

/**
 * Controller for handling user authentication (login and signup).
 * Manages UI toggle between modes and handles input validation and redirection.
 */
public class LoginController {

    @FXML private TextField usernameField;
    @FXML private TextField emailField;
    @FXML private PasswordField passwordField;
    @FXML private Label errorLabel;
    @FXML private Button actionButton;
    @FXML private Button switchModeButton;
    @FXML private Label modeLabel;

    private boolean isSignupMode = false;
    private final UserDAO dao = new UserDAO();

    /**
     * Initializes the login screen with the default mode.
     */
    @FXML
    public void initialize() {
        updateModeUI();
    }

    /**
     * Switches between login and signup modes.
     */
    @FXML
    private void toggleMode() {
        isSignupMode = !isSignupMode;
        updateModeUI();
    }

    /**
     * Updates the UI according to the current mode (login/signup).
     */
    private void updateModeUI() {
        if (isSignupMode) {
            modeLabel.setText("Create an account");
            emailField.setVisible(true);
            emailField.setManaged(true);
            actionButton.setText("Sign up");
            switchModeButton.setText("Already registered? Log in");
        } else {
            modeLabel.setText("Login");
            emailField.setVisible(false);
            emailField.setManaged(false);
            actionButton.setText("Login");
            switchModeButton.setText("Don't have an account? Sign up");
        }
        errorLabel.setText("");
    }

    /**
     * Handles user action: either attempts signup or login depending on the current mode.
     */
    @FXML
    private void handleAction() {
        String username = usernameField.getText().trim();
        String password = passwordField.getText();
        if (isSignupMode) {
            String email = emailField.getText().trim();

            if (username.isEmpty() || email.isEmpty() || password.isEmpty()) {
                errorLabel.setText("All fields are required!");
                return;
            }

            if (dao.userExists(username)) {
                errorLabel.setText("This username is already taken.");
                return;
            }

            User newUser = new User(username, email, PasswordUtils.hashPassword(password));
            if (dao.addUser(newUser)) {
                errorLabel.setText("Account created successfully!");
                toggleMode();  // Switch back to login mode
            } else {
                errorLabel.setText("Unknown error occurred during signup.");
            }

        } else {
            // Login mode
            if (dao.checkLogin(username, password)) {
                User user = dao.getUserByUsername(username);
                Session.getInstance().setCurrentUser(user);
                loadHome();
            } else {
                errorLabel.setText("Incorrect username or password.");
            }
        }
    }

    /**
     * Redirects the user to the home screen after successful login.
     */
    private void loadHome() {
        try {
            FXMLLoader loader = new FXMLLoader(getClass().getResource("/ui/home.fxml"));
            Parent root = loader.load();

            Stage stage = (Stage) actionButton.getScene().getWindow();
            stage.setScene(new Scene(root));
            stage.setTitle("Home");
            stage.show();

        } catch (Exception e) {
            e.printStackTrace();
            errorLabel.setText("Error while loading the home screen.");
        }
    }
}
