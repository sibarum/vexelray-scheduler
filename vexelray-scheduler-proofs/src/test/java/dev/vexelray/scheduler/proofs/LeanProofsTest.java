package dev.vexelray.scheduler.proofs;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.stream.Stream;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Builds the Lean project in {@code src/main/lean} and checks its axioms, so a proof that no longer goes through
 * fails {@code mvn test} like any other test; and shows, on a copy, that a proof leaning on {@code sorry} does fail.
 *
 * <p>{@code lake} comes from elan. It is looked for on {@code PATH} and then in {@code ~/.elan/bin}; with neither,
 * the test fails and says so rather than passing over proofs it never checked. elan reads the project's
 * {@code lean-toolchain} and installs that Lean on the first run.
 */
class LeanProofsTest {

    /** Long enough for elan to fetch the toolchain on a first run; a build of the proofs alone takes seconds. */
    private static final long TIMEOUT_MINUTES = 20;

    private static final Path PROJECT = Path.of(System.getProperty("lean.project", "src/main/lean"));

    @Test
    void theProofsBuildOnTheStandardAxioms() throws Exception {
        String lake = lake();
        run(PROJECT, lake, "build");
        String check = run(PROJECT, lake, "env", "lean", "AxiomCheck.lean");
        assertTrue(check.contains("axioms used"), "the axiom check reported nothing:\n" + check);
    }

    /**
     * {@code lake build} accepts a {@code sorry} with a warning, so it is the axiom check that has to catch one. A copy
     * of the project gains a module proving something false by {@code sorry}; the build passes and the check fails,
     * naming the declaration and {@code sorryAx}.
     */
    @Test
    void aProofBySorryFailsTheAxiomCheck(@TempDir Path copy) throws Exception {
        copyProject(PROJECT, copy);
        Files.writeString(copy.resolve("SchedulerProofs/Planted.lean"),
                "namespace SchedulerProofs\n\ntheorem planted_falsehood : (1 : Int) = 2 := by sorry\n\nend SchedulerProofs\n");
        Files.writeString(copy.resolve("SchedulerProofs.lean"), "import SchedulerProofs.Planted\n",
                StandardOpenOption.APPEND);

        String lake = lake();
        run(copy, lake, "build");
        Result check = exec(copy, lake, "env", "lean", "AxiomCheck.lean");
        assertNotEquals(0, check.exit(), "the axiom check passed a sorry:\n" + check.output());
        assertTrue(check.output().contains("planted_falsehood") && check.output().contains("sorryAx"),
                "the axiom check failed, but not by naming the sorry:\n" + check.output());
    }

    private record Result(int exit, String output) {
    }

    /** Runs {@code command} in {@code dir}, returning its output, and fails with that output on a nonzero exit. */
    private static String run(Path dir, String... command) throws IOException, InterruptedException {
        Result result = exec(dir, command);
        assertEquals(0, result.exit(), String.join(" ", command) + " failed:\n" + result.output());
        return result.output();
    }

    private static Result exec(Path dir, String... command) throws IOException, InterruptedException {
        Process process = new ProcessBuilder(command)
                .directory(dir.toFile())
                .redirectErrorStream(true)
                .start();
        process.getOutputStream().close();
        // Read before waiting: a full pipe would otherwise stall the build it is waiting for.
        String output = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
        if (!process.waitFor(TIMEOUT_MINUTES, TimeUnit.MINUTES)) {
            process.destroyForcibly();
            throw new AssertionError(String.join(" ", command) + " did not finish in " + TIMEOUT_MINUTES
                    + " minutes:\n" + output);
        }
        return new Result(process.exitValue(), output);
    }

    /** The project's sources, without its {@code .lake} build tree, which the copy builds afresh. */
    private static void copyProject(Path from, Path to) throws IOException {
        try (Stream<Path> paths = Files.walk(from)) {
            paths.filter(p -> !from.relativize(p).startsWith(".lake")).forEach(p -> {
                Path target = to.resolve(from.relativize(p).toString());
                try {
                    if (Files.isDirectory(p)) {
                        Files.createDirectories(target);
                    } else {
                        Files.copy(p, target);
                    }
                } catch (IOException e) {
                    throw new UncheckedIOException(e);
                }
            });
        }
    }

    /** The {@code lake} to run: the one on {@code PATH}, else elan's default install. */
    private static String lake() {
        String exe = System.getProperty("os.name").startsWith("Windows") ? "lake.exe" : "lake";
        List<Path> candidates = new ArrayList<>();
        String path = System.getenv("PATH");
        if (path != null) {
            for (String dir : path.split(java.io.File.pathSeparator)) {
                if (!dir.isBlank()) candidates.add(Path.of(dir, exe));
            }
        }
        candidates.add(Path.of(System.getProperty("user.home"), ".elan", "bin", exe));
        for (Path candidate : candidates) {
            if (Files.isExecutable(candidate)) return candidate.toString();
        }
        throw new AssertionError("lake is not installed: no " + exe + " on PATH or in ~/.elan/bin."
                + " Install elan (https://github.com/leanprover/elan), which installs lake and the Lean"
                + " that src/main/lean/lean-toolchain names; or skip this module's test with -DskipTests.");
    }
}
