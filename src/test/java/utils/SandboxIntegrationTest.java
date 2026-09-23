package utils;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * Runs real submissions, including malicious ones, through the Docker sandbox.
 * Skipped when Docker or the sandbox image ({@code docker build -t codyngame-sandbox:latest sandbox/})
 * is not available.
 */
@Timeout(value = 120, unit = TimeUnit.SECONDS)
class SandboxIntegrationTest {

    private static Sandbox sandbox;
    private static FusionneurCode3 fusionneur;

    @BeforeAll
    static void requireDocker() throws InterruptedException {
        sandbox = new Sandbox(Sandbox.Mode.DOCKER, Sandbox.DEFAULT_IMAGE, Sandbox.Limits.defaults());
        try {
            sandbox.checkAvailable();
        } catch (Sandbox.SandboxUnavailableException e) {
            assumeTrue(false, "Docker sandbox unavailable: " + e.getMessage());
        }
        fusionneur = new FusionneurCode3(sandbox);
    }

    private FusionneurCode3.ResultatExecution python(String body) throws Exception {
        return fusionneur.executerCode("python", "def f():\n" + body.indent(4) + "    return 0\n", "", -1);
    }

    // --- every supported language still works --------------------------------------------

    @Test
    void runsPython() throws Exception {
        var r = fusionneur.executerCode("python", "def add(a, b):\n    return a + b\n", "", -1);
        assertEquals(0, r.getCodeRetour(), r.getSortieErreur());
        assertEquals("5\n3\n", r.getSortieStandard());
    }

    @Test
    void compilesAndRunsC() throws Exception {
        var r = fusionneur.executerCode("c", "int add(int a, int b) { return a + b; }", "", -1);
        assertEquals(0, r.getCodeRetour(), r.getSortieErreur());
        assertEquals("5\n3\n", r.getSortieStandard());
    }

    @Test
    void compilesAndRunsJava() throws Exception {
        var r = fusionneur.executerCode("java", "public static int add(int a, int b) { return a + b; }", "", -1);
        assertEquals(0, r.getCodeRetour(), r.getSortieErreur());
        assertEquals("5\n3\n", r.getSortieStandard());
    }

    @Test
    void runsJavaScript() throws Exception {
        var r = fusionneur.executerCode("javascript", "function add(a, b) { return a + b; }", "", -1);
        assertEquals(0, r.getCodeRetour(), r.getSortieErreur());
        assertEquals("5\n3\n", r.getSortieStandard());
    }

    @Test
    void runsPhp() throws Exception {
        var r = fusionneur.executerCode("php", "function isPositive($a, $b) { return $a > 0; }", "", -1);
        assertEquals(0, r.getCodeRetour(), r.getSortieErreur());
        assertEquals("1\n1\n", r.getSortieStandard());
    }

    @Test
    void reportsCompilationErrors() throws Exception {
        var r = fusionneur.executerCode("c", "int add(int a, int b) { return a + ; }", "", -1);
        assertNotEquals(0, r.getCodeRetour());
        assertTrue(r.getSortieErreur().contains("error"), r.getSortieErreur());
    }

    // --- malicious submissions are contained ---------------------------------------------------

    @Test
    void hasNoNetworkAccess() throws Exception {
        var r = python("import socket\nsocket.create_connection(('1.1.1.1', 53), timeout=3)");
        assertNotEquals(0, r.getCodeRetour());
        assertTrue(r.getSortieErreur().contains("unreachable") || r.getSortieErreur().contains("OSError"),
                r.getSortieErreur());
    }

    @Test
    void cannotSeeOrWriteHostFiles(@TempDir Path hostDir) throws Exception {
        Path secret = hostDir.resolve("secret.txt");
        Files.writeString(secret, "host secret");

        var read = python("print(open('" + secret + "').read())");
        assertNotEquals(0, read.getCodeRetour());
        assertTrue(read.getSortieErreur().contains("No such file"), read.getSortieErreur());

        var write = python("open('/etc/pwned', 'w').write('x')");
        assertNotEquals(0, write.getCodeRetour());
        assertTrue(write.getSortieErreur().contains("Read-only file system")
                || write.getSortieErreur().contains("Permission denied"), write.getSortieErreur());
    }

    @Test
    void cannotModifyItsOwnSubmissionWhileRunning() throws Exception {
        var r = python("open('script.py', 'a').write('# tampered')");
        assertNotEquals(0, r.getCodeRetour());
        assertTrue(r.getSortieErreur().contains("Read-only file system")
                || r.getSortieErreur().contains("Permission denied"), r.getSortieErreur());
    }

    @Test
    void doesNotRunAsRoot() throws Exception {
        var r = python("import os\nprint(os.getuid())");
        assertEquals(0, r.getCodeRetour(), r.getSortieErreur());
        assertNotEquals("0", r.getSortieStandard().lines().findFirst().orElse(""));
    }

    @Test
    void forkBombIsCappedByThePidsLimit() throws Exception {
        var r = python("import os\nwhile True:\n    try:\n        os.fork()\n    except OSError:\n        os._exit(1)");
        assertNotEquals(0, r.getCodeRetour());
    }

    @Test
    void memoryBombIsKilled() throws Exception {
        var r = python("x = bytearray(2 * 1024 ** 3)");
        assertNotEquals(0, r.getCodeRetour());
    }

    @Test
    void busyLoopIsStoppedByTheCpuTimeLimit() throws Exception {
        long start = System.nanoTime();
        var r = python("while True:\n    pass");
        assertNotEquals(0, r.getCodeRetour());
        assertTrue(System.nanoTime() - start < TimeUnit.SECONDS.toNanos(60));
    }

    @Test
    void largeFilesAreRejected() throws Exception {
        var r = python("open('/tmp/big', 'wb').write(b'x' * 50 * 1024 * 1024)");
        assertNotEquals(0, r.getCodeRetour());
    }

    @Test
    void hugeOutputIsTruncatedWithoutBlocking() throws Exception {
        var r = python("print('x' * 10_000_000)");
        assertEquals(0, r.getCodeRetour(), r.getSortieErreur());
        assertTrue(r.getSortieStandard().endsWith("[output truncated]\n"));
        assertTrue(r.getSortieStandard().length() < 70_000);
    }
}
