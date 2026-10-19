package controller;

import javafx.collections.FXCollections;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.control.cell.PropertyValueFactory;
import javafx.stage.Stage;
import model.Exercise;
import model.ExerciseDAO;
import model.Language;
import model.LanguageDAO;

import java.net.URL;
import java.util.List;

/**
 * Controller for selecting a programming language and an INCLUDE-type exercise.
 * Displays a list of available exercises for the selected language.
 */
public class IncludeChoiceController {

    @FXML private ComboBox<Language> languageComboBox;
    @FXML private TableView<Exercise> exerciseTable;
    @FXML private TableColumn<Exercise, String> titleColumn;
    @FXML private TableColumn<Exercise, String> typeColumn;
    @FXML private TableColumn<Exercise, Integer> difficultyColumn;
    @FXML private Button backButton;

    private final LanguageDAO languageDAO = new LanguageDAO();
    private final ExerciseDAO exerciseDAO = new ExerciseDAO();

    /**
     * Handles the "Back" button click.
     * Returns to the dashboard view.
     */
    @FXML
    private void handleBack() {
        try {
            FXMLLoader loader = new FXMLLoader(getClass().getResource("/ui/dashboard.fxml"));
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
     * Initializes the view with available languages and prepares table columns.
     * When a language is selected, exercises of type "INCLUDE" are displayed.
     */
    @FXML
    public void initialize() {
        List<Language> languages = languageDAO.getAllLanguages();
        languageComboBox.setItems(FXCollections.observableArrayList(languages));

        titleColumn.setCellValueFactory(cellData -> cellData.getValue().titleProperty());
        typeColumn.setCellValueFactory(cellData -> cellData.getValue().typeProperty());
        difficultyColumn.setCellValueFactory(cellData -> cellData.getValue().difficultyProperty().asObject());

        languageComboBox.setOnAction(event -> {
            Language selectedLang = languageComboBox.getValue();
            if (selectedLang != null) {
                List<Exercise> exercises = exerciseDAO.getExercisesByLanguageAndType(selectedLang.getId(), "INCLUDE");
                exerciseTable.setItems(FXCollections.observableArrayList(exercises));
            }
        });
    }

    /**
     * Handles the "Go" button click.
     * Opens the exercise solving view with the selected language and exercise.
     */
    @FXML
    private void handleGo() {
        Language selectedLang = languageComboBox.getValue();
        Exercise selectedExercise = exerciseTable.getSelectionModel().getSelectedItem();

        if (selectedLang == null || selectedExercise == null) {
            Alert alert = new Alert(Alert.AlertType.WARNING);
            alert.setTitle("Missing selection");
            alert.setHeaderText(null);
            alert.setContentText("Please select both a language and an exercise.");
            alert.showAndWait();
            return;
        }

        try {
            URL fxmlUrl = getClass().getResource("/ui/IncludeExercise.fxml");
            System.out.println("FXML URL: " + fxmlUrl);
            System.out.println("FXML Exists: " + (fxmlUrl != null));

            FXMLLoader loader = new FXMLLoader(fxmlUrl);
            Parent root = loader.load();

            IncludeExerciseController controller = loader.getController();
            controller.initData(selectedLang, selectedExercise);

            Stage stage = (Stage) languageComboBox.getScene().getWindow();
            stage.setScene(new Scene(root));
            stage.setTitle("Solve Exercise");
            stage.show();

        } catch (Exception e) {
            System.err.println("Error loading IncludeExercise.fxml: " + e.getMessage());
            e.printStackTrace();
        }
    }
}
