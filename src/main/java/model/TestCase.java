package model;

import java.util.List;
import java.util.stream.Collectors;

/**
 * One test of an exercise for a given language: code appended to the user's
 * submission and the output it must produce.
 *
 * @param id             the test case ID
 * @param exerciseId     the exercise it belongs to
 * @param languageId     the language it is written in
 * @param testCode       code run after the user's function (e.g. {@code print(add(2, 3))})
 * @param expectedOutput the output the test code must print
 */
public record TestCase(int id, int exerciseId, int languageId, String testCode, String expectedOutput) {

    /**
     * Joins the test code of several test cases, in order, one per line.
     *
     * @param cases the test cases of an exercise and language
     * @return the code to append to the submission, empty if there is none
     */
    public static String joinTestCode(List<TestCase> cases) {
        return cases.stream().map(TestCase::testCode).collect(Collectors.joining("\n"));
    }

    /**
     * Joins the expected outputs of several test cases, in order, one per line.
     *
     * @param cases the test cases of an exercise and language
     * @return the full expected output, or {@code null} if there is no test case
     */
    public static String joinExpectedOutput(List<TestCase> cases) {
        if (cases.isEmpty()) {
            return null;
        }
        return cases.stream().map(c -> c.expectedOutput().strip()).collect(Collectors.joining("\n"));
    }
}
