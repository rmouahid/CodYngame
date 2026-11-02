-- Drop and recreate the database
DROP DATABASE IF EXISTS codingame;
CREATE DATABASE codingame;
USE codingame;

-- User accounts
CREATE TABLE User (
    id INT PRIMARY KEY AUTO_INCREMENT,
    username VARCHAR(255) NOT NULL UNIQUE,
    email TEXT,
    password_hash TEXT,
    score INT DEFAULT 0,
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP
);

-- Admin users (linked to User table)
CREATE TABLE Admin (
    id INT PRIMARY KEY AUTO_INCREMENT,
    user_id INT,
    FOREIGN KEY (user_id) REFERENCES User(id)
);

-- Exercises table with base code and insertion line
CREATE TABLE Exercise (
    id INT PRIMARY KEY AUTO_INCREMENT,
    title TEXT NOT NULL,
    description TEXT NOT NULL,
    type VARCHAR(50) CHECK(type IN ('STDIN_STDOUT', 'INCLUDE')) NOT NULL,
    exercise_image BLOB,
    solution_image BLOB,
    solution TEXT,
    difficulty INT CHECK (difficulty IN (1, 2, 3)) NOT NULL,
    BaseCode TEXT,
    LineCode INT
);

-- Programming languages
CREATE TABLE Language (
    id INT PRIMARY KEY AUTO_INCREMENT,
    name VARCHAR(255) NOT NULL UNIQUE
);

-- Relationship between exercises and supported languages
CREATE TABLE ExerciseLanguage (
    exercise_id INT,
    language_id INT,
    PRIMARY KEY (exercise_id, language_id),
    FOREIGN KEY (exercise_id) REFERENCES Exercise(id),
    FOREIGN KEY (language_id) REFERENCES Language(id)
);

-- Code submissions
CREATE TABLE Submission (
    id INT PRIMARY KEY AUTO_INCREMENT,
    user_id INT,
    exercise_id INT,
    language_id INT,
    code TEXT NOT NULL,
    result TEXT,
    success BOOLEAN DEFAULT FALSE,
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (user_id) REFERENCES User(id),
    FOREIGN KEY (exercise_id) REFERENCES Exercise(id),
    FOREIGN KEY (language_id) REFERENCES Language(id)
);

-- Track started exercises for each user
CREATE TABLE MakeExercise (
    id INT PRIMARY KEY AUTO_INCREMENT,
    user_id INT NOT NULL,
    exercise_id INT NOT NULL,
    begins_the DATETIME DEFAULT CURRENT_TIMESTAMP,
    success BOOLEAN DEFAULT FALSE,
    FOREIGN KEY (user_id) REFERENCES User(id),
    FOREIGN KEY (exercise_id) REFERENCES Exercise(id)
);

-- Test cases of each exercise, per language. For "include" exercises in
-- Python, Java and C, test_code is appended to the user's function; the
-- program output must equal expected_output (case and surrounding whitespace
-- ignored). Several rows for the same exercise and language run in id order.
-- Example data: test_cases_example.sql
CREATE TABLE TestCase (
    id INT PRIMARY KEY AUTO_INCREMENT,
    exercise_id INT NOT NULL,
    language_id INT NOT NULL,
    test_code TEXT NOT NULL,
    expected_output TEXT NOT NULL,
    FOREIGN KEY (exercise_id) REFERENCES Exercise(id),
    FOREIGN KEY (language_id) REFERENCES Language(id)
);
