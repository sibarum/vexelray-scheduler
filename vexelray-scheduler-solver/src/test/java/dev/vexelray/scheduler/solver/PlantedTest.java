package dev.vexelray.scheduler.solver;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

import java.util.Random;

import org.junit.jupiter.api.Test;

/**
 * Networks built around a schedule chosen first, so a solution is known to exist before the solver looks.
 *
 * <p>Every constraint is drawn so the planted schedule meets it, some tight and some loose. Then an infeasible answer
 * is a bug, whatever the solver's checks say, and the planted times must lie inside the solver's windows. The second
 * test breaks each network with one constraint that closes a positive cycle, and the solver must find a conflict the
 * checker accepts.
 *
 * <p>Each run is seeded, and a failure names its seed: {@code new Random(seed)} rebuilds the network exactly.
 */
class PlantedTest {

    private static final int RUNS = 500;

    @Test
    void aPlantedScheduleIsAlwaysFoundAndAlwaysInsideTheWindows() {
        for (long run = 0; run < RUNS; run++) {
            long seed = run;
            Planted planted = Planted.draw(new Random(seed));
            Solution solution = Solver.solve(planted.network());
            Solution.Schedule schedule = solution.schedule().orElseThrow(() -> new AssertionError("seed " + seed
                    + ": infeasible, but a schedule was planted"));
            assertTrue(Checker.schedule(planted.network(), schedule.earliest()), "seed " + seed + ": earliest fails");
            assertTrue(Checker.schedule(planted.network(), schedule.latest()), "seed " + seed + ": latest fails");
            for (int p = 0; p < planted.network().points(); p++) {
                if (!schedule.window(p).contains(planted.times()[p])) {
                    fail("seed " + seed + ": point " + p + " was planted at " + planted.times()[p]
                            + ", outside its window " + schedule.window(p));
                }
            }
        }
    }

    @Test
    void oneConstraintClosingAPositiveCycleIsAlwaysCaught() {
        for (long run = 0; run < RUNS; run++) {
            long seed = run;
            Random random = new Random(seed);
            Network broken = Planted.draw(random).broken(random);

            Solution.Conflict conflict = Solver.solve(broken).conflict().orElseThrow(() -> new AssertionError(
                    "seed " + seed + ": a positive cycle was added, but the network solved"));
            assertTrue(Checker.cycle(broken, conflict.cycle()), "seed " + seed + ": the conflict fails the checker");
            assertTrue(conflict.excess() > 0, "seed " + seed);
        }
    }
}
