package utils;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermissions;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

/**
 * Runs the compilation and execution of user-submitted code in an isolated,
 * resource-constrained environment.
 *
 * <p>By default ({@link Mode#DOCKER}), every command runs in a short-lived Docker
 * container started from the sandbox image (see {@code sandbox/Dockerfile}):</p>
 * <ul>
 *     <li>no network access ({@code --network none})</li>
 *     <li>read-only root filesystem; the only writable locations are a small
 *     {@code /tmp} tmpfs and, during compilation only, the submission directory</li>
 *     <li>all Linux capabilities dropped, privilege escalation disabled, non-root user</li>
 *     <li>limits on memory (no swap), CPU, number of processes, CPU time, file size
 *     and open files</li>
 * </ul>
 *
 * <p>In every mode, a command that exceeds its wall-clock time limit (see
 * {@link Limits}) is stopped, with its child processes and its container, and its
 * {@link Result} is flagged {@link Result#timedOut()}: a submission that loops or
 * blocks forever cannot hold the application.</p>
 *
 * <p>If Docker or the sandbox image is not available, execution is refused
 * (fail closed) with a {@link SandboxUnavailableException}. Running submissions
 * directly on the host ({@link Mode#NONE}) must be requested explicitly, and is
 * only meant for local development on a trusted machine.</p>
 *
 * <p>Configuration, read from a JVM system property first, then from an
 * environment variable:</p>
 * <ul>
 *     <li>{@code codyngame.sandbox} / {@code CODYNGAME_SANDBOX}: {@code docker} (default) or {@code none}</li>
 *     <li>{@code codyngame.sandbox.image} / {@code CODYNGAME_SANDBOX_IMAGE}: image name
 *     (default {@value #DEFAULT_IMAGE})</li>
 * </ul>
 */
public class Sandbox {

    /** How submissions are isolated from the host. */
    public enum Mode {
        /** One Docker container per command (default). */
        DOCKER,
        /** No isolation: commands run directly on the host. Trusted local development only. */
        NONE
    }

    /** Default name of the sandbox image, built from {@code sandbox/Dockerfile}. */
    public static final String DEFAULT_IMAGE = "codyngame-sandbox:latest";

    /** Mount point of the submission directory inside the container. */
    static final String CONTAINER_WORKDIR = "/sandbox";

    /** Unprivileged user the container runs as when the host user cannot be mapped. */
    static final String NOBODY = "65534:65534";

    /**
     * Resource limits applied to every sandboxed command.
     *
     * @param memoryMb        memory limit of the container, swap included (MB)
     * @param cpus            number of CPUs the container may use
     * @param maxProcesses    maximum number of processes/threads in the container
     * @param cpuTimeSeconds  CPU time limit per process (RLIMIT_CPU); stops busy loops
     * @param maxFileSizeMb   maximum size of a file written by the submission (MB)
     * @param maxOpenFiles    maximum number of open file descriptors
     * @param tmpSizeMb       size of the writable {@code /tmp} tmpfs (MB)
     * @param maxOutputBytes  bytes of stdout/stderr kept; the rest is discarded
     * @param compileTimeoutSeconds wall-clock time allowed to compile a submission
     * @param runTimeoutSeconds     wall-clock time allowed to run it; unlike the CPU time
     *                              limit, it also stops a program that sleeps or blocks
     */
    public record Limits(int memoryMb, double cpus, int maxProcesses, int cpuTimeSeconds,
                         int maxFileSizeMb, int maxOpenFiles, int tmpSizeMb, int maxOutputBytes,
                         int compileTimeoutSeconds, int runTimeoutSeconds) {

        /**
         * Limits suited to small exercise functions, including the JVM and javac
         * (a cold javac in a container takes a few seconds, hence 30 s to compile).
         */
        public static Limits defaults() {
            return new Limits(512, 1.0, 128, 10, 10, 256, 64, 64 * 1024, 30, 10);
        }

        /** Same limits with other wall-clock time limits. */
        public Limits withTimeouts(int compileTimeoutSeconds, int runTimeoutSeconds) {
            return new Limits(memoryMb, cpus, maxProcesses, cpuTimeSeconds, maxFileSizeMb, maxOpenFiles,
                    tmpSizeMb, maxOutputBytes, compileTimeoutSeconds, runTimeoutSeconds);
        }

        public Duration compileTimeout() { return Duration.ofSeconds(compileTimeoutSeconds); }

        public Duration runTimeout() { return Duration.ofSeconds(runTimeoutSeconds); }
    }

    /** Exit code reported for a command stopped at its time limit (as GNU {@code timeout}). */
    public static final int TIMEOUT_EXIT_CODE = 124;

    /**
     * Result of a sandboxed command.
     *
     * @param stdout   standard output, truncated to {@link Limits#maxOutputBytes()}
     * @param stderr   standard error, truncated to {@link Limits#maxOutputBytes()}
     * @param exitCode exit code of the command, {@link #TIMEOUT_EXIT_CODE} if it timed out
     * @param timedOut whether the command was stopped at its time limit; the output is
     *                 then what it had written so far
     */
    public record Result(String stdout, String stderr, int exitCode, boolean timedOut) {

        public Result(String stdout, String stderr, int exitCode) {
            this(stdout, stderr, exitCode, false);
        }
    }

    /** Thrown when the sandbox cannot be used, so the submission is not executed. */
    public static class SandboxUnavailableException extends IOException {
        public SandboxUnavailableException(String message) {
            super(message);
        }
    }

    private final Mode mode;
    private final String image;
    private final Limits limits;

    public Sandbox(Mode mode, String image, Limits limits) {
        this.mode = mode;
        this.image = image;
        this.limits = limits;
    }

    /**
     * Builds a sandbox from the {@code codyngame.sandbox*} system properties or
     * {@code CODYNGAME_SANDBOX*} environment variables, with default limits.
     */
    public static Sandbox fromEnvironment() {
        String mode = setting("codyngame.sandbox", "CODYNGAME_SANDBOX", "docker").toLowerCase(Locale.ROOT);
        String image = setting("codyngame.sandbox.image", "CODYNGAME_SANDBOX_IMAGE", DEFAULT_IMAGE);
        return switch (mode) {
            case "docker" -> new Sandbox(Mode.DOCKER, image, Limits.defaults());
            case "none" -> new Sandbox(Mode.NONE, image, Limits.defaults());
            default -> throw new IllegalArgumentException(
                    "Unknown sandbox mode '" + mode + "': expected 'docker' or 'none'");
        };
    }

    private static String setting(String property, String envVariable, String defaultValue) {
        String value = System.getProperty(property);
        if (value == null || value.isBlank()) value = System.getenv(envVariable);
        return value == null || value.isBlank() ? defaultValue : value.trim();
    }

    public Mode getMode() { return mode; }

    public String getImage() { return image; }

    public Limits getLimits() { return limits; }

    /**
     * Checks that submissions can be executed with the configured mode: Docker is
     * reachable and the sandbox image exists.
     *
     * @throws SandboxUnavailableException if the sandbox cannot be used
     */
    public void checkAvailable() throws SandboxUnavailableException, InterruptedException {
        if (mode == Mode.NONE) return;
        Result result;
        try {
            result = execute(List.of("docker", "image", "inspect", "--format", "{{.Id}}", image), null,
                    DOCKER_COMMAND_TIMEOUT, null);
        } catch (IOException e) {
            throw new SandboxUnavailableException("Docker is required to run submissions safely but could not be "
                    + "started (" + e.getMessage() + "). Install Docker, or set CODYNGAME_SANDBOX=none for trusted "
                    + "local development only.");
        }
        if (result.exitCode() != 0) {
            throw new SandboxUnavailableException("The sandbox image '" + image + "' is not available ("
                    + result.stderr().trim() + "). Build it with: docker build -t " + DEFAULT_IMAGE + " sandbox/");
        }
    }

    /**
     * Runs {@code command} on the files of {@code workDir}, within the time limit of
     * its step: {@link Limits#compileTimeout()} when {@code writable} (compilation),
     * {@link Limits#runTimeout()} otherwise.
     *
     * @see #run(Path, List, boolean, Duration)
     */
    public Result run(Path workDir, List<String> command, boolean writable)
            throws IOException, InterruptedException {
        return run(workDir, command, writable, writable ? limits.compileTimeout() : limits.runTimeout());
    }

    /**
     * Runs {@code command} on the files of {@code workDir}.
     *
     * @param workDir  directory holding the submission; it is the working directory of the command
     * @param command  program and arguments, with paths relative to {@code workDir}
     *                 (an executable produced in {@code workDir} is written {@code ./name})
     * @param writable whether the command may write to {@code workDir} (true for compilation,
     *                 false to run the submission)
     * @param timeout  wall-clock time after which the command is stopped and its result
     *                 flagged {@link Result#timedOut()}
     */
    public Result run(Path workDir, List<String> command, boolean writable, Duration timeout)
            throws IOException, InterruptedException {
        if (mode == Mode.NONE) {
            System.err.println("WARNING: running user-submitted code WITHOUT sandbox (CODYNGAME_SANDBOX=none)");
            return execute(hostCommand(workDir, command), workDir, timeout, null);
        }
        checkAvailable();
        String container = "codyngame-" + UUID.randomUUID();
        return execute(dockerCommand(workDir, command, writable, containerUser(workDir, writable), container),
                workDir, timeout, container);
    }

    /**
     * Wraps {@code command} into the {@code docker run} invocation that isolates it.
     */
    List<String> dockerCommand(Path workDir, List<String> command, boolean writable, String user) {
        return dockerCommand(workDir, command, writable, user, null);
    }

    /**
     * Same, naming the container {@code name} (when not null) so that it can be
     * killed if the command times out.
     */
    List<String> dockerCommand(Path workDir, List<String> command, boolean writable, String user, String name) {
        List<String> args = new ArrayList<>(List.of(
                "docker", "run", "--rm", "--interactive=false",
                "--network", "none",
                "--read-only",
                "--cap-drop", "ALL",
                "--security-opt", "no-new-privileges",
                "--user", user,
                "--memory", limits.memoryMb() + "m",
                "--memory-swap", limits.memoryMb() + "m",
                "--cpus", String.valueOf(limits.cpus()),
                "--pids-limit", String.valueOf(limits.maxProcesses()),
                "--ulimit", "cpu=" + limits.cpuTimeSeconds(),
                "--ulimit", "fsize=" + (long) limits.maxFileSizeMb() * 1024 * 1024,
                "--ulimit", "nofile=" + limits.maxOpenFiles(),
                "--tmpfs", "/tmp:rw,nosuid,nodev,size=" + limits.tmpSizeMb() + "m",
                "--env", "HOME=/tmp",
                "--volume", workDir.toAbsolutePath() + ":" + CONTAINER_WORKDIR + (writable ? ":rw" : ":ro"),
                "--workdir", CONTAINER_WORKDIR));
        if (name != null) args.addAll(List.of("--name", name));
        args.add(image);
        args.addAll(command);
        return args;
    }

    /**
     * Resolves {@code ./executable} against {@code workDir}: without a sandbox the command
     * runs on the host, where a relative program path is not resolved against the
     * working directory on every OS.
     */
    static List<String> hostCommand(Path workDir, List<String> command) {
        List<String> resolved = new ArrayList<>(command);
        if (!resolved.isEmpty() && resolved.get(0).startsWith("./")) {
            resolved.set(0, workDir.resolve(resolved.get(0).substring(2)).toAbsolutePath().toString());
        }
        return resolved;
    }

    /**
     * Chooses the user the container runs as. On POSIX hosts it is the owner of the
     * submission directory, so the container can use it without widening its
     * permissions, unless that owner is root: the container then runs as
     * {@code nobody} and the directory is opened to it. On other hosts (Docker Desktop
     * on Windows), bind mounts are accessible to any container user.
     */
    private static String containerUser(Path workDir, boolean writable) throws IOException {
        Object uid;
        Object gid;
        try {
            uid = Files.getAttribute(workDir, "unix:uid");
            gid = Files.getAttribute(workDir, "unix:gid");
        } catch (UnsupportedOperationException e) {
            return NOBODY;
        }
        if (!Integer.valueOf(0).equals(uid)) return uid + ":" + gid;

        String dirPermissions = writable ? "rwxrwxrwx" : "rwxr-xr-x";
        Files.setPosixFilePermissions(workDir, PosixFilePermissions.fromString(dirPermissions));
        try (var files = Files.list(workDir)) {
            for (Path file : (Iterable<Path>) files::iterator) {
                Files.setPosixFilePermissions(file, PosixFilePermissions.fromString(
                        Files.isExecutable(file) ? "rwxr-xr-x" : "rw-r--r--"));
            }
        }
        return NOBODY;
    }

    /** Time limit of the Docker client commands the sandbox issues itself (inspect, kill). */
    private static final Duration DOCKER_COMMAND_TIMEOUT = Duration.ofSeconds(30);

    /** How long to wait, once a command is stopped, for its output to be fully read. */
    private static final long DRAIN_MILLIS = 2_000;

    /**
     * Starts {@code command} and captures its output. Both streams are drained while
     * the process runs (a full pipe would otherwise block it), keeping at most
     * {@link Limits#maxOutputBytes()} bytes of each.
     *
     * <p>If it is still running after {@code timeout}, the process and its descendants
     * are killed, as well as the Docker container {@code container} when not null:
     * killing the {@code docker run} client alone would leave the container running.</p>
     */
    private Result execute(List<String> command, Path directory, Duration timeout, String container)
            throws IOException, InterruptedException {
        ProcessBuilder builder = new ProcessBuilder(command);
        if (directory != null) builder.directory(directory.toFile());
        Process process = builder.start();
        process.getOutputStream().close();

        OutputCollector stdout = new OutputCollector(process.getInputStream(), limits.maxOutputBytes());
        OutputCollector stderr = new OutputCollector(process.getErrorStream(), limits.maxOutputBytes());
        stdout.start();
        stderr.start();

        boolean finished = process.waitFor(timeout.toMillis(), TimeUnit.MILLISECONDS);
        if (!finished) {
            if (container != null) killContainer(container);
            // Descendants first: once the parent is gone they can no longer be found
            process.descendants().forEach(ProcessHandle::destroyForcibly);
            process.destroyForcibly();
            process.waitFor();
        }
        // A surviving grandchild could keep a pipe open: don't wait for it forever
        stdout.join(DRAIN_MILLIS);
        stderr.join(DRAIN_MILLIS);
        if (!finished) return new Result(stdout.text(), stderr.text(), TIMEOUT_EXIT_CODE, true);
        return new Result(stdout.text(), stderr.text(), process.exitValue());
    }

    /** Kills a sandbox container whose command timed out ({@code --rm} then removes it). */
    private void killContainer(String container) throws InterruptedException {
        try {
            Process kill = new ProcessBuilder("docker", "kill", container).redirectErrorStream(true)
                    .redirectOutput(ProcessBuilder.Redirect.DISCARD).start();
            if (!kill.waitFor(DOCKER_COMMAND_TIMEOUT.toMillis(), TimeUnit.MILLISECONDS)) kill.destroyForcibly();
        } catch (IOException e) {
            System.err.println("Could not kill sandbox container " + container + ": " + e.getMessage());
        }
    }

    /** Reads a stream to its end on a background thread, keeping only its first bytes. */
    private static final class OutputCollector extends Thread {
        private final InputStream stream;
        private final byte[] kept;
        private int length;
        private boolean truncated;

        OutputCollector(InputStream stream, int maxBytes) {
            this.stream = stream;
            this.kept = new byte[maxBytes];
            setDaemon(true);
        }

        @Override
        public void run() {
            byte[] buffer = new byte[8192];
            try (stream) {
                int read;
                while ((read = stream.read(buffer)) != -1) {
                    int copied = Math.min(read, kept.length - length);
                    System.arraycopy(buffer, 0, kept, length, copied);
                    length += copied;
                    if (copied < read) truncated = true;
                }
            } catch (IOException ignored) {
                // stream closed when the process ends: keep what was read
            }
        }

        String text() {
            String text = new String(kept, 0, length, StandardCharsets.UTF_8);
            return truncated ? text + "\n[output truncated]\n" : text;
        }
    }
}
