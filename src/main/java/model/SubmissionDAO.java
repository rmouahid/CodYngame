package model;

import utils.SubmissionStats;

import java.sql.*;
import java.util.*;

/**
 * DAO for the Submission table.
 * Handles insertions and queries related to user submissions.
 */
public class SubmissionDAO {

    private final Connection conn;

    /**
     * Constructor with active database connection.
     *
     * @param conn the database connection
     */
    public SubmissionDAO(Connection conn) {
        this.conn = conn;
    }

    /**
     * Inserts a new submission into the database.
     *
     * @param submission the Submission object to insert
     * @return true if insertion was successful, false otherwise
     */
    public boolean insertSubmission(Submission submission) {
        String sql = "INSERT INTO Submission " +
                "(user_id, exercise_id, language_id, code, result, success) " +
                "VALUES (?, ?, ?, ?, ?, ?)";

        try (PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setInt(1, submission.getUserId());
            stmt.setInt(2, submission.getExerciseId());
            stmt.setInt(3, submission.getLanguageId());
            stmt.setString(4, submission.getCode());
            stmt.setString(5, submission.getResult());
            stmt.setBoolean(6, submission.isSuccess());

            int rows = stmt.executeUpdate();
            System.out.println("Submission inserted, rows affected: " + rows);
            return rows > 0;

        } catch (SQLException e) {
            System.err.println("Submission insertion error: " + e.getMessage());
            return false;
        }
    }

    /**
     * Counts the number of submissions made by a specific user.
     *
     * @param userId the user ID
     * @return number of submissions
     */
    public int countByUserId(int userId) {
        String sql = "SELECT COUNT(*) FROM Submission WHERE user_id = ?";
        try (PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setInt(1, userId);
            ResultSet rs = stmt.executeQuery();
            return rs.next() ? rs.getInt(1) : 0;
        } catch (Exception e) {
            e.printStackTrace();
            return 0;
        }
    }

    /**
     * Counts the number of submissions per exercise for a given user.
     *
     * @param userId the user ID
     * @return map with exercise titles as keys and count as values
     */
    public Map<String, Integer> countByUserGroupedByExercise(int userId) {
        String sql = """
            SELECT e.title, COUNT(s.id) AS cnt
            FROM Submission s
            JOIN Exercise e ON s.exercise_id = e.id
            WHERE s.user_id = ?
            GROUP BY e.title
        """;
        Map<String, Integer> result = new LinkedHashMap<>();

        try (PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setInt(1, userId);
            ResultSet rs = stmt.executeQuery();

            // Fill the map with title and submission count
            while (rs.next()) {
                result.put(rs.getString("title"), rs.getInt("cnt"));
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }

        return result;
    }

    /**
     * Returns submission statistics per exercise for a user.
     * Includes total attempts, total successes, and the first success attempt number.
     *
     * @param userId the user ID
     * @return list of SubmissionStats objects
     * @throws SQLException in case of database access errors
     */
    public List<SubmissionStats> getStatsByUserGroupedByExercise(int userId) throws SQLException {
        String sql = """
            SELECT
              e.title AS exercise_title,
              COUNT(s.id) AS total_count,
              SUM(CASE WHEN LOWER(TRIM(s.result)) = 'correct function!' THEN 1 ELSE 0 END) AS success_count,
              MIN(CASE WHEN LOWER(TRIM(s.result)) = 'correct function!' THEN rn ELSE NULL END) AS first_success_attempt
            FROM (
              SELECT
                id,
                exercise_id,
                result,
                created_at,
                ROW_NUMBER() OVER (
                  PARTITION BY exercise_id
                  ORDER BY created_at
                ) AS rn
              FROM Submission
              WHERE user_id = ?
            ) s
            JOIN Exercise e ON s.exercise_id = e.id
            GROUP BY e.title
            ORDER BY e.title
        """;

        try (PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setInt(1, userId);
            ResultSet rs = stmt.executeQuery();
            List<SubmissionStats> list = new ArrayList<>();

            // Build statistics objects from result set
            while (rs.next()) {
                list.add(new SubmissionStats(
                        rs.getString("exercise_title"),
                        rs.getInt("total_count"),
                        rs.getInt("success_count"),
                        rs.getInt("first_success_attempt")
                ));
            }
            return list;
        }
    }

    /**
     * Computes the score for a user based on successful submissions.
     * Currently: 1 point per success.
     *
     * @param userId the user ID
     * @return the score
     */
    public int getScoreByUser(int userId) {
        String sql = """
            SELECT COUNT(*) * 1 AS score
            FROM Submission
            WHERE user_id = ? AND LOWER(TRIM(result)) = 'correct function!'
        """;

        try (PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setInt(1, userId);
            ResultSet rs = stmt.executeQuery();
            return rs.next() ? rs.getInt("score") : 0;
        } catch (SQLException e) {
            e.printStackTrace();
            return 0;
        }
    }
}
