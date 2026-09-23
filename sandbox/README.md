# Code execution sandbox

Submissions are untrusted code. `utils.FusionneurCode3` never compiles or runs
them directly on the host: every command (`javac`, `gcc`, `python3`, `node`,
`php`, and the compiled program) goes through `utils.Sandbox`, which runs it
in a short-lived Docker container.

## Setup

Install Docker, then build the image once from the repository root:

```bash
docker build -t codyngame-sandbox:latest sandbox/
```

If Docker or the image is missing, the application **refuses to run
submissions** and shows the reason, instead of falling back to running them
unsandboxed.

## What a submission can and cannot do

Each command runs in a fresh container, removed when it exits, with:

| Protection | `docker run` option | Effect |
|---|---|---|
| No network | `--network none` | No outbound or inbound connections |
| Read-only system | `--read-only` | The image cannot be modified |
| Read-only submission | `--volume <dir>:/sandbox:ro` | The program cannot tamper with its own files (the directory is writable only while compiling) |
| Isolated filesystem | only the submission directory is mounted | Host files are not visible |
| Scratch space | `--tmpfs /tmp:size=64m` | The only writable location while running |
| No privileges | `--cap-drop ALL`, `--security-opt no-new-privileges`, non-root `--user` | No root, no privilege escalation |
| Memory | `--memory 512m --memory-swap 512m` | Killed beyond 512 MB (no swap) |
| CPU | `--cpus 1`, `--ulimit cpu=10` | One CPU, and at most 10 s of CPU time per process: busy loops are killed |
| Processes | `--pids-limit 128` | Fork bombs hit the limit |
| Files | `--ulimit fsize=10MB`, `--ulimit nofile=256` | No huge files, bounded open files |
| Output | captured by the application | Only the first 64 KB of stdout/stderr are kept |

These limits are defined in `Sandbox.Limits.defaults()`.

A program that sleeps or blocks without using CPU is not stopped by the CPU
time limit: a wall-clock timeout on execution is tracked separately (#3).

## Configuration

Read from a JVM system property, then from an environment variable:

| Property / variable | Values | Default |
|---|---|---|
| `codyngame.sandbox` / `CODYNGAME_SANDBOX` | `docker`, `none` | `docker` |
| `codyngame.sandbox.image` / `CODYNGAME_SANDBOX_IMAGE` | image name | `codyngame-sandbox:latest` |

`CODYNGAME_SANDBOX=none` runs submissions **directly on the host, without
any isolation**. It is only meant for local development on a trusted machine
without Docker, and prints a warning on every execution.

## Tests

`mvn test` runs `SandboxTest` (the generated `docker run` command, fail-closed
behaviour) and `SandboxIntegrationTest`, which executes real submissions in
the five languages as well as malicious ones: network access, reading or
writing host files, tampering with the submission, fork bomb, memory bomb,
busy loop, huge file and huge output. The integration tests are skipped when
Docker or the image is unavailable.
