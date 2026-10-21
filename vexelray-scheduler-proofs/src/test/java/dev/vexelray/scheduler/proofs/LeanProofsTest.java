package dev.vexelray.scheduler.proofs;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.Test;

/**
 * Builds the Lean project in {@code src/main/lean} and checks its axioms, so a proof that no longer goes through
 * fails {@code mvn test} like any other test.
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
        run(lake, "build");
        String check = run(lake, "env", "lean", "AxiomCheck.lean");
        assertTrue(check.contains("axioms used"), "the axiom check reported nothing:\n" + check);
    }

    /** Runs {@code command} in the Lean project, returning its output, and fails with that output on a nonzero exit. */
    private static String run(String... command) throws IOException, InterruptedException {
        Process process = new ProcessBuilder(command)
                .directory(PROJECT.toFile())
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
        assertEquals(0, process.exitValue(), String.join(" ", command) + " failed:\n" + output);
        return output;
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
