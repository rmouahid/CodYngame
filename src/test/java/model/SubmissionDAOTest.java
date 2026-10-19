package model;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import utils.SubmissionStats;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Runs the submission statistics queries against an in-memory H2 database
 * in MySQL mode, so the SQL itself is exercised without a MySQL server.
 */
class SubmissionDAOTest {

    private static final int USER_ID = 1;
    private static final int EXERCISE_ID = 1;

    private Connection conn;
    private SubmissionDAO dao;

    @BeforeEach
    void setUp() throws SQLException {
        conn = DriverManager.getConnection("jdbc:h2:mem:submissions;MODE=MySQL");
        try (Statement stmt = conn.createStatement()) {
            stmt.execute("CREATE TABLE Exercise (id INT PRIMARY KEY, title TEXT NOT NULL)");
            stmt.execute("""
                CREATE TABLE Submission (
                    id INT PRIMARY KEY AUTO_INCREMENT,
                    user_id INT,
                    exercise_id INT,
                    language_id INT,
                    code TEXT NOT NULL,
                    result TEXT,
                    success BOOLEAN DEFAULT FALSE,
                    created_at DATETIME DEFAULT CURRENT_TIMESTAMP
                )""");
            stmt.execute("INSERT INTO Exercise VALUES (" + EXERCISE_ID + ", 'Sum')");
        }
        dao = new SubmissionDAO(conn);
    }

    @AfterEach
    void tearDown() throws SQLException {
        conn.close();
    }

    private void submit(String result, String createdAt) throws SQLException {
        try (Statement stmt = conn.createStatement()) {
            stmt.execute("INSERT INTO Submission (user_id, exercise_id, language_id, code, result, created_at) "
                    + "VALUES (" + USER_ID + ", " + EXERCISE_ID + ", 1, 'code', '" + result + "', '" + createdAt + "')");
        }
    }

    @Test
    void scoreCountsCorrectSubmissions() throws SQLException {
        submit("Incorrect function.", "2025-01-01 10:00:00");
        submit("Correct function!", "2025-01-01 10:01:00");
        submit("Correct function!", "2025-01-01 10:02:00");

        assertEquals(2, dao.getScoreByUser(USER_ID));
    }

    @Test
    void statsReportSuccessesAndFirstSuccessfulAttempt() throws SQLException {
        submit("Incorrect function.", "2025-01-01 10:00:00");
        submit("Incorrect function.", "2025-01-01 10:01:00");
        submit("Correct function!", "2025-01-01 10:02:00");

        List<SubmissionStats> stats = dao.getStatsByUserGroupedByExercise(USER_ID);

        assertEquals(1, stats.size());
        assertEquals(3, stats.get(0).getTotalCount());
        assertEquals(1, stats.get(0).getSuccessCount());
        assertEquals(3, stats.get(0).getFirstSuccessId());
    }
}
