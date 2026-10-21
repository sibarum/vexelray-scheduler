package dev.vexelray.scheduler.solver;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Random;
import java.util.function.IntFunction;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;

/**
 * How the solver's time grows, on the networks that are worst for it. Opt-in, because it is a measurement rather
 * than a check: {@code mvn test -pl vexelray-scheduler-solver -Dtest=StressTest -Dsolver.stress=true}. It still
 * asserts every answer, so a run is also a test at sizes the other tests never reach.
 *
 * <p>The worst case for Bellman-Ford is not a large network but a badly ordered one: a long chain, settling one link a
 * round. Plain rounds over the constraint list were defeated by a chain listed last-first; the queue the solver uses
 * now is not, and is defeated instead by a chain whose points were created against its direction. Each family has a
 * zero-slack case, the chain in a horizon exactly as long as it is, and a case one tick short, whose conflict runs the
 * whole chain and back through the origin.
 */
@EnabledIfSystemProperty(named = "solver.stress", matches = "true")
class StressTest {

    private static final int[] SIZES = {1_000, 2_000, 5_000, 10_000, 20_000};

    @Test
    void timeAgainstSize() {
        family("forward chain (best order)", n -> chain(n, n - 1, false), true);
        family("reversed chain, zero slack", n -> chain(n, n - 1, true), true);
        family("reversed chain, one tick short", n -> chain(n, n - 2, true), false);
        family("chain against creation order", n -> againstCreation(n, n - 1), true);
        family("chain against creation order, one tick short", n -> againstCreation(n, n - 2), false);
        family("planted, 10 constraints a point", n -> planted(n, 10, n), true);
    }

    private static void family(String name, IntFunction<Network> make, boolean feasible) {
        System.out.printf("%n%s%n%10s %12s %12s %10s%n", name, "points", "constraints", "ms", "ns/edge²");
        for (int n : SIZES) {
            Network network = make.apply(n);
            Solver.solve(network);  // warm
            long t0 = System.nanoTime();
            Solution solution = Solver.solve(network);
            long ns = System.nanoTime() - t0;
            assertEquals(feasible, solution.schedule().isPresent(), name + " at " + n);
            if (!feasible) {
                assertTrue(Checker.cycle(network, solution.conflict().orElseThrow().cycle()));
            }
            int m = network.constraints().size();
            System.out.printf("%10d %12d %12.1f %10.4f%n", network.points(), m, ns / 1e6,
                    ns / ((double) network.points() * m));
        }
    }

    /** {@code n} points in a chain of one-tick links, in a horizon of {@code horizon}, listed first-first or last-first. */
    private static Network chain(int n, long horizon, boolean reversed) {
        Network.Builder b = new Network.Builder(horizon);
        int[] p = new int[n];
        for (int i = 0; i < n; i++) p[i] = b.point();
        for (int i = 0; i < n - 1; i++) {
            int k = reversed ? n - 2 - i : i;
            b.constrain(p[k], p[k + 1], 1);
        }
        return b.build();
    }

    /**
     * {@code n} points in a chain running from the last created to the first: the order that defeats the queue,
     * which visits points in the order they rose, so each pass settles one more link.
     */
    private static Network againstCreation(int n, long horizon) {
        Network.Builder b = new Network.Builder(horizon);
        int[] p = new int[n];
        for (int i = 0; i < n; i++) p[i] = b.point();
        for (int i = n - 1; i > 0; i--) {
            b.constrain(p[i], p[i - 1], 1);
        }
        return b.build();
    }

    /** {@code n} points planted at random times, and {@code perPoint × n} constraints they meet, a quarter tight. */
    private static Network planted(int n, int perPoint, long horizon) {
        Random random = new Random(n);
        Network.Builder b = new Network.Builder(horizon);
        long[] times = new long[n + 1];
        for (int i = 1; i <= n; i++) {
            b.point();
            times[i] = random.nextLong(horizon + 1);
        }
        for (int i = 0; i < perPoint * n; i++) {
            int from = 1 + random.nextInt(n);
            int to = 1 + random.nextInt(n);
            long gap = times[to] - times[from];
            long slack = random.nextInt(4) == 0 ? 0 : random.nextLong(gap + horizon + 1);
            b.constrain(from, to, gap - slack);
        }
        return b.build();
    }
}
