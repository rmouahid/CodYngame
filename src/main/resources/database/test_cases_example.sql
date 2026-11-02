-- Test cases that used to be hard-coded in IncludeExerciseController,
-- for exercises 1 to 10. Run after creating these exercises and the
-- languages (see README):
--   mysql -u root -p codingame < src/main/resources/database/test_cases_example.sql
-- Exercises 7 and 8 (C) had no test case yet: add rows for them the same way.
USE codingame;

INSERT INTO TestCase (exercise_id, language_id, test_code, expected_output)
SELECT 1, id, 'print(add(2, 3))\nprint(add(1, 2))', '5\n3' FROM Language WHERE name = 'python';
INSERT INTO TestCase (exercise_id, language_id, test_code, expected_output)
SELECT 2, id, 'print(is_even(2))\nprint(is_even(3))', 'True\nFalse' FROM Language WHERE name = 'python';

INSERT INTO TestCase (exercise_id, language_id, test_code, expected_output)
SELECT 3, id, 'System.out.println(add(2, 3));\nSystem.out.println(add(1, 2));', '5\n3' FROM Language WHERE name = 'java';
INSERT INTO TestCase (exercise_id, language_id, test_code, expected_output)
SELECT 4, id, 'System.out.println(isEven(2));\nSystem.out.println(isEven(3));', 'true\nfalse' FROM Language WHERE name = 'java';

INSERT INTO TestCase (exercise_id, language_id, test_code, expected_output)
SELECT 5, id, 'echo add(2, 3) . "\\n"; echo add(1, 2) . "\\n";', '5\n3' FROM Language WHERE name = 'php';
INSERT INTO TestCase (exercise_id, language_id, test_code, expected_output)
SELECT 6, id, 'echo is_even(2) ? ''true'' : ''false''; echo "\\n"; echo is_even(3) ? ''true'' : ''false'';', '1\n0' FROM Language WHERE name = 'php';

INSERT INTO TestCase (exercise_id, language_id, test_code, expected_output)
SELECT 9, id, 'console.log(add(2, 3)); console.log(add(1, 2));', '5\n3' FROM Language WHERE name = 'javascript';
INSERT INTO TestCase (exercise_id, language_id, test_code, expected_output)
SELECT 10, id, 'console.log(isEven(2)); console.log(isEven(3));', 'True\nFalse' FROM Language WHERE name = 'javascript';
