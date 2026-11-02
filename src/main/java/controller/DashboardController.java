package controller;

import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.stage.Stage;

import java.io.IOException;

/**
 * Controller class for the dashboard view.
 * Handles navigation to other parts of the application.
 */
public class DashboardController {

    @FXML
    private Button backButton;

    @FXML
    private Button includeButton;

    /**
     * Handles the "Back" button click.
     * Redirects the user to the home screen.
     */
    @FXML
    private void handleBack() {
        try {
            FXMLLoader loader = new FXMLLoader(getClass().getResource("/ui/home.fxml"));
            Parent root = loader.load();

            Stage stage = (Stage) backButton.getScene().getWindow();
            stage.setScene(new Scene(root));
            stage.setTitle("Home");
            stage.show();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    /**
     * Handles the "INCLUDE" button click.
     * Redirects the user to the language and exercise selection page.
     */
    @FXML
    private void handleIncludeClick() {
        try {
            FXMLLoader loader = new FXMLLoader(getClass().getResource("/ui/IncludeChoice.fxml"));
            Parent root = loader.load();

            Stage stage = (Stage) includeButton.getScene().getWindow();
            stage.setScene(new Scene(root));
            stage.setTitle("Choose an option");
            stage.show();

        } catch (IOException e) {
            e.printStackTrace();
            System.out.println("Error while loading include-choice.fxml page.");
        }
    }
}
