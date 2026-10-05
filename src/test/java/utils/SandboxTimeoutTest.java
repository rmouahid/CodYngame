package utils;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * Wall-clock time limit on compilation and execution. These tests run commands
 * directly on the host ({@link Sandbox.Mode#NONE}), so they do not need Docker,
 * but they use POSIX tools ({@code sh}, {@code sleep}, {@code python3}).
 */
@Timeout(value = 60, unit = TimeUnit.SECONDS)
class SandboxTimeoutTest {

    /** One second to compile, one to run: enough for {@code sh}, short enough for the tests. */
    private static final Sandbox.Limits SHORT = Sandbox.Limits.defaults().withTimeouts(1, 1);

    private final Sandbox sandbox = new Sandbox(Sandbox.Mode.NONE, "unused", SHORT);

    @BeforeAll
    static void requirePosix() {
        assumeTrue(!System.getProperty("os.name").toLowerCase().contains("win"), "needs sh and sleep");
    }

    @Test
    void defaultLimitsBoundCompilationAndExecution() {
        Sandbox.Limits limits = Sandbox.Limits.defaults();

        assertEquals(30, limits.compileTimeoutSeconds());
        assertEquals(10, limits.runTimeoutSeconds());
    }

    @Test
    void aCommandThatNeverEndsIsStoppedAtTheTimeLimit(@TempDir Path dir) throws Exception {
        long start = System.nanoTime();

        Sandbox.Result result = sandbox.run(dir, List.of("sleep", "30"), false, Duration.ofSeconds(1));

        assertTrue(result.timedOut());
        assertEquals(Sandbox.TIMEOUT_EXIT_CODE, result.exitCode());
        assertTrue(Duration.ofNanos(System.nanoTime() - start).toSeconds() < 10, "should stop after ~1 s");
    }

    @Test
    void childProcessesAreStoppedAndOutputSoFarIsKept(@TempDir Path dir) throws Exception {
        long start = System.nanoTime();

        // `sleep` is a child of `sh` and holds the output pipe open: it must be killed too
        Sandbox.Result result = sandbox.run(dir, List.of("sh", "-c", "echo started; sleep 30; echo never"), false,
                Duration.ofSeconds(1));

        assertTrue(result.timedOut());
        assertEquals("started\n", result.stdout());
        assertTrue(Duration.ofNanos(System.nanoTime() - start).toSeconds() < 10, "should stop after ~1 s");
    }

    @Test
    void aCommandThatFinishesInTimeIsNotFlagged(@TempDir Path dir) throws Exception {
        Sandbox.Result result = sandbox.run(dir, List.of("sh", "-c", "echo ok; exit 3"), false,
                Duration.ofSeconds(5));

        assertFalse(result.timedOut());
        assertEquals(3, result.exitCode());
        assertEquals("ok\n", result.stdout());
    }

    @Test
    void anInfiniteLoopInASubmissionReportsTimeLimitExceeded() throws Exception {
        assumeTrue(commandExists("python3"), "needs python3");

        FusionneurCode3.ResultatExecution r = new FusionneurCode3(sandbox)
                .executerCode("python", "def f(a):\n    while True:\n        pass\n", "", -1);

        assertTrue(r.isTempsDepasse());
        assertEquals(Sandbox.TIMEOUT_EXIT_CODE, r.getCodeRetour());
        assertTrue(r.getSortieErreur().startsWith("Time limit exceeded"), r.getSortieErreur());
        assertTrue(r.getSortieErreur().contains("1 s"), r.getSortieErreur());
    }

    @Test
    void aSubmissionWithinTheLimitIsNotFlagged() throws Exception {
        assumeTrue(commandExists("python3"), "needs python3");

        FusionneurCode3.ResultatExecution r = new FusionneurCode3(sandbox)
                .executerCode("python", "def add(a, b):\n    return a + b\n", "", -1);

        assertFalse(r.isTempsDepasse());
        assertEquals(0, r.getCodeRetour(), r.getSortieErreur());
        assertEquals("5\n3\n", r.getSortieStandard());
    }

    @Test
    void dockerContainersAreNamedSoThatTheyCanBeKilled(@TempDir Path dir) {
        Sandbox docker = new Sandbox(Sandbox.Mode.DOCKER, "test-image", Sandbox.Limits.defaults());

        List<String> args = docker.dockerCommand(dir, List.of("true"), false, "1:1", "codyngame-test");

        int i = args.indexOf("--name");
        assertTrue(i > 0 && args.get(i + 1).equals("codyngame-test"), args.toString());
        assertTrue(i < args.indexOf("test-image"), "options must precede the image");
    }

    private static boolean commandExists(String command) {
        try {
            return new ProcessBuilder(command, "--version").start().waitFor() == 0;
        } catch (Exception e) {
            return false;
        }
    }
}
