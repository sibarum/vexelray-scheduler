package dev.vexelray.scheduler.solver;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Random;

import org.junit.jupiter.api.Test;

/**
 * Tiny random networks, nothing planted, decided by trying every schedule.
 *
 * <p>The planted tests only see networks the generator knows how to make. These see anything, and with every
 * schedule enumerated they check more than the checker can: that the solver says infeasible exactly when no schedule
 * exists, and that each window is tight, its ends the earliest and latest times the point takes in any schedule.
 * The Lean proofs do not cover tightness yet; this is what does.
 */
class BruteForceTest {

    private static final int RUNS = 3000;

    @Test
    void theSolverAgreesWithEverySchedule() {
        for (long seed = 0; seed < RUNS; seed++) {
            Random random = new Random(seed);
            long horizon = random.nextInt(5);
            int points = 1 + random.nextInt(3);
            Network.Builder b = new Network.Builder(horizon);
            for (int p = 0; p < points; p++) b.point();
            int constraints = random.nextInt(6);
            for (int i = 0; i < constraints; i++) {
                b.constrain(random.nextInt(points + 1), random.nextInt(points + 1),
                        random.nextLong(2 * horizon + 1) - horizon);
            }
            Network network = b.build();

            long[] lo = new long[points + 1];
            long[] hi = new long[points + 1];
            boolean any = enumerate(network, new long[points + 1], 1, lo, hi, false);
            Solution solution = Solver.solve(network);
            assertEquals(any, solution.schedule().isPresent(), "seed " + seed + ": feasibility");
            if (any) {
                Solution.Schedule s = solution.schedule().orElseThrow();
                for (int p = 0; p <= points; p++) {
                    assertEquals(new Span(lo[p], hi[p]), s.window(p), "seed " + seed + ": window of point " + p);
                }
            } else {
                assertTrue(Checker.cycle(network, solution.conflict().orElseThrow().cycle()), "seed " + seed);
            }
        }
    }

    /**
     * Tries every time in {@code [0, horizon]} for points {@code p} onwards, the origin at 0, widening {@code lo} and
     * {@code hi} over the schedules that pass. Returns whether any passed, {@code any} included.
     */
    private static boolean enumerate(Network network, long[] times, int p, long[] lo, long[] hi, boolean any) {
        if (p == times.length) {
            if (!Checker.schedule(network, times)) return any;
            for (int q = 0; q < times.length; q++) {
                lo[q] = any ? Math.min(lo[q], times[q]) : times[q];
                hi[q] = any ? Math.max(hi[q], times[q]) : times[q];
            }
            return true;
        }
        for (long t = 0; t <= network.horizon(); t++) {
            times[p] = t;
            any = enumerate(network, times, p + 1, lo, hi, any);
        }
        return any;
    }
}
