package model;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Runs the test-case queries against an in-memory H2 database in MySQL mode.
 */
class TestCaseDAOTest {

    private static final int PYTHON = 1;
    private static final int JAVA = 2;

    private Connection conn;
    private TestCaseDAO dao;

    @BeforeEach
    void setUp() throws SQLException {
        conn = DriverManager.getConnection("jdbc:h2:mem:testcases;MODE=MySQL");
        try (Statement stmt = conn.createStatement()) {
            stmt.execute("""
                CREATE TABLE TestCase (
                    id INT PRIMARY KEY AUTO_INCREMENT,
                    exercise_id INT NOT NULL,
                    language_id INT NOT NULL,
                    test_code TEXT NOT NULL,
                    expected_output TEXT NOT NULL
                )""");
        }
        dao = new TestCaseDAO(conn);
    }

    @AfterEach
    void tearDown() throws SQLException {
        try (Statement stmt = conn.createStatement()) {
            stmt.execute("DROP ALL OBJECTS");
        }
        conn.close();
    }

    @Test
    void returnsOnlyTheCasesOfTheExerciseAndLanguageInOrder() throws SQLException {
        dao.insert(1, PYTHON, "print(add(2, 3))", "5");
        dao.insert(1, JAVA, "System.out.println(add(2, 3));", "5");
        dao.insert(2, PYTHON, "print(is_even(2))", "True");
        dao.insert(1, PYTHON, "print(add(1, 2))", "3");

        List<TestCase> cases = dao.findByExerciseAndLanguage(1, PYTHON);

        assertEquals(2, cases.size());
        assertEquals("print(add(2, 3))\nprint(add(1, 2))", TestCase.joinTestCode(cases));
        assertEquals("5\n3", TestCase.joinExpectedOutput(cases));
    }

    @Test
    void multiLineCaseIsKeptAsOneRow() throws SQLException {
        dao.insert(1, PYTHON, "print(add(2, 3))\nprint(add(1, 2))", "5\n3\n");

        List<TestCase> cases = dao.findByExerciseAndLanguage(1, PYTHON);

        assertEquals(1, cases.size());
        assertEquals("5\n3", TestCase.joinExpectedOutput(cases));
    }

    @Test
    void exerciseWithoutCasesHasNoExpectedOutput() throws SQLException {
        List<TestCase> cases = dao.findByExerciseAndLanguage(42, PYTHON);

        assertTrue(cases.isEmpty());
        assertEquals("", TestCase.joinTestCode(cases));
        assertNull(TestCase.joinExpectedOutput(cases));
    }
}
