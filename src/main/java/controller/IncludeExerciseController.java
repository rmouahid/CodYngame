package controller;

import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.stage.Stage;
import model.Exercise;
import model.Language;
import utils.FusionneurCode3;
import model.User;
import model.UserDAO;
import model.Submission;
import model.SubmissionDAO;
import model.DatabaseConnection;
import utils.Session;

import java.sql.SQLException;

/**
 * Controller for solving INCLUDE-type exercises.
 * Loads the base code, collects user input, runs and evaluates the code,
 * and records the result in the database.
 */
public class IncludeExerciseController {

    @FXML private TextArea baseCodeArea;
    @FXML private Button backButton;
    @FXML private Label exerciseTitle;
    @FXML private TextArea exerciseDescription;
    @FXML private TextArea codeEditor;
    @FXML private TextArea outputArea;

    private Language selectedLanguage;
    private Exercise selectedExercise;

    /**
     * Initializes the view with the selected language and exercise.
     *
     * @param language The selected programming language.
     * @param exercise The selected exercise.
     */
    public void initData(Language language, Exercise exercise) {
        this.selectedLanguage = language;
        this.selectedExercise = exercise;

        exerciseTitle.setText(exercise.getTitle());
        exerciseDescription.setText(exercise.getDescription());
        baseCodeArea.setText(exercise.getBaseCode());
        codeEditor.clear();
        outputArea.setText("");
    }

    /**
     * Handles the back button click and returns to the exercise selection screen.
     */
    @FXML
    private void handleBack() {
        try {
            FXMLLoader loader = new FXMLLoader(getClass().getResource("/ui/IncludeChoice.fxml"));
            Parent root = loader.load();

            Stage stage = (Stage) backButton.getScene().getWindow();
            stage.setScene(new Scene(root));
            stage.setTitle("Choose an exercise");
            stage.show();

        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    /**
     * Handles the Run button click, compiles and executes user code,
     * evaluates the output, updates the score if successful, and records the submission.
     */
    @FXML
    private void handleRun() {
        String userCode = codeEditor.getText();
        if (userCode == null || userCode.trim().isEmpty()) {
            outputArea.setText("Please write some code before running.");
            return;
        }

        try {
            String result = executeUserCode(userCode, selectedLanguage, selectedExercise);
            outputArea.setText(result);

            User currentUser = Session.getInstance().getCurrentUser();
            if (currentUser != null) {
                Submission submission = new Submission();
                submission.setUserId(currentUser.getId());
                submission.setExerciseId(selectedExercise.getId());
                submission.setLanguageId(selectedLanguage.getId());
                submission.setCode(userCode);
                submission.setResult(result);
                submission.setSuccess(result.trim().equalsIgnoreCase("Correct function!"));
                submission.setCreatedAt(java.time.LocalDateTime.now());

                SubmissionDAO dao = new SubmissionDAO(DatabaseConnection.getConnection());
                dao.insertSubmission(submission);
            }

        } catch (Exception e) {
            outputArea.setText("Execution error:\n" + e.getMessage());
            e.printStackTrace();
        }
    }

    /**
     * Executes the user's code with predefined test cases and returns the output or error.
     *
     * @param code     The user's code.
     * @param lang     The selected programming language.
     * @param exercise The selected exercise.
     * @return The result as a string.
     */
    private String executeUserCode(String code, Language lang, Exercise exercise) {
        try {
            FusionneurCode3 fusionneur = new FusionneurCode3();
            String langName = lang.getName().toLowerCase();

            FusionneurCode3.ResultatExecution resultat;
            String tests = generateAutomaticTests(lang, exercise);
            String codeToExecute = code + "\n" + tests;

            if (langName.equals("python") || langName.equals("c") || langName.equals("java")) {
                resultat = fusionneur.executerCode(lang.getName(), codeToExecute, "", -1);
            } else {
                resultat = fusionneur.executerCode(lang.getName(), code, exercise.getBaseCode(), exercise.getLineCode());
            }

            if (resultat.isTempsDepasse()) {
                return resultat.getSortieErreur();
            }

            if (resultat.getCodeRetour() == 0) {
                String output = resultat.getSortieStandard().trim();
                String expectedOutput = generateExpectedOutput(lang, exercise);

                if (expectedOutput != null && outputsEqual(expectedOutput, output)) {
                    int points = selectedExercise.getDifficulty();
                    User currentUser = Session.getInstance().getCurrentUser();
                    if (currentUser != null) {
                        UserDAO userDAO = new UserDAO();
                        try {
                            userDAO.updateUserScore(currentUser.getId(), points);
                        } catch (SQLException e) {
                            System.err.println("Score update error: " + e.getMessage());
                        }
                    }
                    return "Correct function!";
                } else {
                    return "Incorrect function.\n\n"
                            + "Used test cases:\n" + tests + "\n\n"
                            + "Expected output:\n" + expectedOutput + "\n\n"
                            + "Actual output:\n" + output;
                }
            } else {
                return "Execution error:\n\n" + resultat.getSortieErreur();
            }

        } catch (Exception e) {
            return "System error: " + e.getMessage();
        }
    }

    /**
     * Compares two output strings, ignoring case and whitespace.
     */
    private boolean outputsEqual(String s1, String s2) {
        return s1.trim().equalsIgnoreCase(s2.trim());
    }

    /**
     * Generates automatic test calls based on the selected exercise and language.
     */
    private String generateAutomaticTests(Language lang, Exercise exercise) {
        int exoId = exercise.getId();
        String langName = lang.getName().toLowerCase();

        switch (langName) {
            case "python":
                if (exoId == 1) return "print(add(2, 3))\nprint(add(1, 2))";
                if (exoId == 2) return "print(is_even(2))\nprint(is_even(3))";
                break;
            case "java":
                if (exoId == 3) return "System.out.println(add(2, 3));\nSystem.out.println(add(1, 2));";
                if (exoId == 4) return "System.out.println(isEven(2));\nSystem.out.println(isEven(3));";
                break;
            case "php":
                if (exoId == 5) return "echo add(2, 3) . \"\\n\"; echo add(1, 2) . \"\\n\";";
                if (exoId == 6) return "echo is_even(2) ? 'true' : 'false'; echo \"\\n\"; echo is_even(3) ? 'true' : 'false';";
                break;
            case "c":
                if (exoId == 7 || exoId == 8) return ""; // To be implemented
                break;
            case "javascript":
                if (exoId == 9) return "console.log(add(2, 3)); console.log(add(1, 2));";
                if (exoId == 10) return "console.log(isEven(2)); console.log(isEven(3));";
                break;
        }
        return "";
    }

    /**
     * Returns the expected output for the selected exercise and language.
     */
    private String generateExpectedOutput(Language lang, Exercise exercise) {
        int exoId = exercise.getId();
        String langName = lang.getName().toLowerCase();

        switch (langName) {
            case "python":
            case "java":
            case "php":
            case "c":
            case "javascript":
                if (exoId == 1 || exoId == 3 || exoId == 5 || exoId == 7 || exoId == 9) return "5\n3";
                if (exoId == 2 || exoId == 4 || exoId == 6 || exoId == 8 || exoId == 10) {
                    if (langName.equals("php") || langName.equals("c")) return "1\n0";
                    if (langName.equals("python") || langName.equals("javascript")) return "True\nFalse";
                    if (langName.equals("java")) return "true\nfalse";
                }
                break;
        }
        return null;
    }
}
