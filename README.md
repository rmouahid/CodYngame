# CodYngame

A desktop coding-exercise platform inspired by CodinGame, built with JavaFX.
Users sign up, pick an exercise and a language, write their solution in an
editor, and get it compiled, run and checked against expected outputs. Scores
and submission statistics are tracked per user.

School project (CY Tech, group G18). The specification and the final report
are in [`doc/`](doc/).

## Features

- Accounts: sign up, log in, edit profile (passwords hashed with salted PBKDF2).
- Exercises in five languages: Python, Java, C, JavaScript and PHP.
- "Include" exercises: the user writes a function that is merged into base
  code (`utils.FusionneurCode3`) and run against generated tests.
- Sandboxed execution: every compilation and run happens in a throw-away
  Docker container with no network, read-only filesystem and CPU, memory and
  process limits (see [`sandbox/README.md`](sandbox/README.md)).
- Dashboard with score and submission history.

## Prerequisites

| Tool | Version | Used for |
|---|---|---|
| JDK | 21 | building and running the app |
| Maven | 3.9+ | build (`mvn`) |
| MySQL | 8 | users, exercises, submissions |
| Docker | any recent | running submissions in the sandbox |

The language toolchains (`javac`, `gcc`, `python3`, `node`, `php`) live in
the sandbox image, so they do not need to be installed on the host.

## Setup

### 1. Database

Create the schema (this drops any existing `codingame` database):

```bash
mysql -u root -p < src/main/resources/database/database.sql
```

The script creates the tables only. Add the languages and at least one
exercise before using the app, for example:

```sql
USE codingame;
INSERT INTO Language (name) VALUES ('python'), ('java'), ('c'), ('javascript'), ('php');
```

### 2. Database credentials

Credentials are never hard-coded. Copy the example file and fill it in
(`config.properties` is git-ignored):

```bash
cp config.properties.example config.properties
```

Each setting can also come from an environment variable
(`CODYNGAME_DB_URL`, `CODYNGAME_DB_USER`, `CODYNGAME_DB_PASSWORD`) or a JVM
system property (`-Dcodyngame.db.password=...`), which take precedence over
the file.

### 3. Sandbox image

Build it once from the repository root:

```bash
docker build -t codyngame-sandbox:latest sandbox/
```

Without Docker or this image, the app refuses to run submissions instead of
running untrusted code on the host.

## Run

```bash
mvn clean javafx:run
```

## Tests

```bash
mvn test
```

The suite covers the sandbox, configuration loading, password hashing and the
DAO queries (against an in-memory H2 database in MySQL mode, so no MySQL
server is needed). The sandbox integration tests compile and run real
programs when the sandbox image is present and are skipped otherwise.

## Project structure

```
src/main/java/
├── com/codyngame/Main.java   JavaFX entry point (opens the login view)
├── controller/               FXML controllers (login, home, exercise, dashboard, profile…)
├── model/                    entities and DAOs (User, Exercise, Language, Submission…)
└── utils/                    sandbox, code merging and execution, sessions, password hashing
src/main/resources/
├── ui/                       FXML views
├── styles/                   CSS
└── database/database.sql     MySQL schema
sandbox/                      Docker image used to run submissions
doc/                          specification and project report
```

## License

[MIT](LICENSE), © the CodYngame contributors.
