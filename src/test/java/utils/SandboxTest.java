package utils;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests of the sandbox configuration: the {@code docker run} command it builds
 * and how it fails when Docker cannot be used. They do not need Docker.
 */
class SandboxTest {

    private final Sandbox sandbox = new Sandbox(Sandbox.Mode.DOCKER, "test-image", Sandbox.Limits.defaults());

    @Test
    void dockerCommandAppliesEveryIsolationFlag(@TempDir Path dir) {
        List<String> args = sandbox.dockerCommand(dir, List.of("python3", "script.py"), false, "1000:1000");

        assertEquals(List.of("docker", "run", "--rm"), args.subList(0, 3));
        assertFlag(args, "--network", "none");
        assertTrue(args.contains("--read-only"));
        assertFlag(args, "--cap-drop", "ALL");
        assertFlag(args, "--security-opt", "no-new-privileges");
        assertFlag(args, "--user", "1000:1000");
        assertFlag(args, "--memory", "512m");
        assertFlag(args, "--memory-swap", "512m");
        assertFlag(args, "--pids-limit", "128");
        assertTrue(args.contains("cpu=10"));
        assertTrue(args.contains("fsize=" + 10L * 1024 * 1024));
        assertTrue(args.contains("nofile=256"));
        assertFlag(args, "--workdir", Sandbox.CONTAINER_WORKDIR);
        // the command itself comes last, right after the image
        assertEquals(List.of("test-image", "python3", "script.py"), args.subList(args.size() - 3, args.size()));
    }

    @Test
    void submissionDirectoryIsReadOnlyUnlessCompiling(@TempDir Path dir) {
        String mount = dir.toAbsolutePath() + ":" + Sandbox.CONTAINER_WORKDIR;

        assertFlag(sandbox.dockerCommand(dir, List.of("true"), false, "1:1"), "--volume", mount + ":ro");
        assertFlag(sandbox.dockerCommand(dir, List.of("true"), true, "1:1"), "--volume", mount + ":rw");
    }

    @Test
    void userArgumentsAreNeverSplitOrInterpretedByAShell(@TempDir Path dir) {
        List<String> args = sandbox.dockerCommand(dir, List.of("echo", "a b; rm -rf /"), false, "1:1");

        assertEquals("a b; rm -rf /", args.get(args.size() - 1));
    }

    @Test
    void hostCommandResolvesLocalExecutablesAgainstTheWorkDir(@TempDir Path dir) {
        assertEquals(dir.resolve("program.exe").toAbsolutePath().toString(),
                Sandbox.hostCommand(dir, List.of("./program.exe")).get(0));
        assertEquals(List.of("python3", "script.py"), Sandbox.hostCommand(dir, List.of("python3", "script.py")));
    }

    @Test
    void missingImageFailsClosed() {
        Sandbox missing = new Sandbox(Sandbox.Mode.DOCKER, "codyngame-sandbox-does-not-exist:none",
                Sandbox.Limits.defaults());

        Sandbox.SandboxUnavailableException e = assertThrows(Sandbox.SandboxUnavailableException.class,
                missing::checkAvailable);
        assertTrue(e.getMessage().contains("Docker") || e.getMessage().contains("docker build"), e.getMessage());
    }

    @Test
    void executionIsRefusedWhenTheSandboxIsUnavailable() {
        Sandbox missing = new Sandbox(Sandbox.Mode.DOCKER, "codyngame-sandbox-does-not-exist:none",
                Sandbox.Limits.defaults());

        assertThrows(Sandbox.SandboxUnavailableException.class,
                () -> new FusionneurCode3(missing).executerCode("python", "def f(a):\n    return a\n", "", -1));
    }

    @Test
    void modeAndImageAreConfigurable() {
        System.setProperty("codyngame.sandbox", "none");
        System.setProperty("codyngame.sandbox.image", "my-image:1");
        try {
            Sandbox configured = Sandbox.fromEnvironment();
            assertEquals(Sandbox.Mode.NONE, configured.getMode());
            assertEquals("my-image:1", configured.getImage());

            System.setProperty("codyngame.sandbox", "chroot");
            assertThrows(IllegalArgumentException.class, Sandbox::fromEnvironment);
        } finally {
            System.clearProperty("codyngame.sandbox");
            System.clearProperty("codyngame.sandbox.image");
        }
    }

    private static void assertFlag(List<String> args, String flag, String value) {
        int i = args.indexOf(flag);
        while (i >= 0 && !args.get(i + 1).equals(value)) {
            int next = args.subList(i + 1, args.size()).indexOf(flag);
            i = next < 0 ? -1 : i + 1 + next;
        }
        assertTrue(i >= 0, "expected " + flag + " " + value + " in " + args);
    }
}
