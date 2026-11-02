package model;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

/**
 * Data Access Object (DAO) for the test cases of exercises.
 */
public class TestCaseDAO {

    private final Connection conn;

    /**
     * Constructor with an existing connection.
     *
     * @param conn an active database connection
     */
    public TestCaseDAO(Connection conn) {
        this.conn = conn;
    }

    /**
     * Returns the test cases of an exercise for a language, in ID order.
     *
     * @param exerciseId the exercise ID
     * @param languageId the language ID
     * @return the test cases, empty if none is defined
     * @throws SQLException if the query fails
     */
    public List<TestCase> findByExerciseAndLanguage(int exerciseId, int languageId) throws SQLException {
        String sql = "SELECT id, exercise_id, language_id, test_code, expected_output "
                + "FROM TestCase WHERE exercise_id = ? AND language_id = ? ORDER BY id";
        List<TestCase> cases = new ArrayList<>();
        try (PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setInt(1, exerciseId);
            stmt.setInt(2, languageId);
            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    cases.add(new TestCase(
                            rs.getInt("id"),
                            rs.getInt("exercise_id"),
                            rs.getInt("language_id"),
                            rs.getString("test_code"),
                            rs.getString("expected_output")));
                }
            }
        }
        return cases;
    }

    /**
     * Adds a test case.
     *
     * @param exerciseId     the exercise ID
     * @param languageId     the language ID
     * @param testCode       code appended to the submission
     * @param expectedOutput the output it must print
     * @return true if a row was inserted
     * @throws SQLException if the insertion fails
     */
    public boolean insert(int exerciseId, int languageId, String testCode, String expectedOutput)
            throws SQLException {
        String sql = "INSERT INTO TestCase (exercise_id, language_id, test_code, expected_output) VALUES (?, ?, ?, ?)";
        try (PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setInt(1, exerciseId);
            stmt.setInt(2, languageId);
            stmt.setString(3, testCode);
            stmt.setString(4, expectedOutput);
            return stmt.executeUpdate() > 0;
        }
    }
}
