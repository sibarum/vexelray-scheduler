package dev.vexelray.scheduler.solver;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.junit.jupiter.api.Test;

/** Small networks whose answers are worked out by hand, and the checker's and builder's refusals. */
class SolverTest {

    @Test
    void aChainStartsAsEarlyAndAsLateAsItsLagsAllow() {
        Network.Builder b = new Network.Builder(20);
        int a = b.point(), m = b.point(), c = b.point();
        b.constrain(a, m, 2);
        b.constrain(m, c, 3);
        Solution.Schedule s = Solver.solve(b.build()).schedule().orElseThrow();

        assertEquals(new Span(0, 15), s.window(a));
        assertEquals(new Span(2, 17), s.window(m));
        assertEquals(new Span(5, 20), s.window(c));
        assertEquals(0, s.start(Network.ORIGIN));
    }

    @Test
    void aReleaseAfterTheDeadlineIsAConflictOfThoseTwo() {
        Network.Builder b = new Network.Builder(10);
        int a = b.point();
        int release = b.release(a, 5);
        int deadline = b.deadline(a, 3);
        Network network = b.build();
        Solution.Conflict conflict = Solver.solve(network).conflict().orElseThrow();

        assertEquals(2, conflict.excess());
        assertEquals(List.of(release, deadline), sorted(conflict.cycle()));
        assertTrue(Checker.cycle(network, conflict.cycle()));
    }

    @Test
    void aCycleOfPrecedencesThatGainsTimeIsTheConflict() {
        Network.Builder b = new Network.Builder(10);
        int a = b.point(), m = b.point(), c = b.point();
        int ab = b.constrain(a, m, 2);
        int bc = b.constrain(m, c, 2);
        int ca = b.constrain(c, a, -3);
        Solution.Conflict conflict = Solver.solve(b.build()).conflict().orElseThrow();

        assertEquals(1, conflict.excess());
        assertEquals(List.of(ab, bc, ca), sorted(conflict.cycle()));
    }

    @Test
    void aChainLongerThanTheHorizonConflictsWithTheHorizon() {
        Network.Builder b = new Network.Builder(10);
        int a = b.point(), c = b.point();
        b.constrain(a, c, 6);
        int m = b.point();
        b.constrain(c, m, 6);
        Network network = b.build();
        Solution.Conflict conflict = Solver.solve(network).conflict().orElseThrow();

        assertEquals(2, conflict.excess());
        assertTrue(conflict.constraints().stream().anyMatch(k -> k.from() == Network.ORIGIN));
        assertTrue(conflict.constraints().stream().anyMatch(k -> k.to() == Network.ORIGIN));
    }

    @Test
    void anEmptyNetworkIsJustTheOrigin() {
        Solution.Schedule s = Solver.solve(new Network.Builder(0).build()).schedule().orElseThrow();
        assertArrayEquals(new long[] {0}, s.earliest());
    }

    @Test
    void theCheckerRefusesWhatIsNotACycleOrNotPositive() {
        Network.Builder b = new Network.Builder(10);
        int a = b.point(), m = b.point();
        int ab = b.constrain(a, m, 3);
        int ba = b.constrain(m, a, -3);
        int ab2 = b.constrain(a, m, 4);
        Network network = b.build();

        assertFalse(Checker.cycle(network, new int[0]), "empty");
        assertFalse(Checker.cycle(network, new int[] {ab}), "does not close");
        assertFalse(Checker.cycle(network, new int[] {ab, ba}), "closes, but gains nothing");
        assertFalse(Checker.cycle(network, new int[] {ab2, ab2}), "positive, but the walk is broken");
        assertTrue(Checker.cycle(network, new int[] {ab2, ba}), "closes and gains a tick");
        assertTrue(Checker.cycle(network, new int[] {ba, ab2}), "the same cycle from its other point");
        assertFalse(Checker.cycle(network, new int[] {ab2, 99}), "no such constraint");
        assertFalse(Checker.cycle(network, new int[] {-1}), "no such constraint");
        assertFalse(Checker.cycle(network, new int[] {ab2, ba, ab2, ba}), "longer than the network has points");
    }

    @Test
    void theCheckerRefusesASchedulOfTheWrongLengthOrBreakingAConstraint() {
        Network.Builder b = new Network.Builder(10);
        int a = b.point(), m = b.point();
        b.constrain(a, m, 3);
        Network network = b.build();

        assertTrue(Checker.schedule(network, new long[] {0, 1, 4}));
        assertFalse(Checker.schedule(network, new long[] {0, 1, 3}), "m too early");
        assertFalse(Checker.schedule(network, new long[] {0, 1}), "too short");
        assertFalse(Checker.schedule(network, new long[] {0, 1, 11}), "past the horizon");
    }

    @Test
    void holdsIsExactWhereTheSumWouldOverflow() {
        assertFalse(new Constraint(0, 1, 5).holds(new long[] {Long.MAX_VALUE - 1, Long.MAX_VALUE}));
        assertTrue(new Constraint(0, 1, -5).holds(new long[] {Long.MIN_VALUE + 1, Long.MIN_VALUE}));
        assertTrue(new Constraint(0, 1, 1).holds(new long[] {Long.MAX_VALUE - 1, Long.MAX_VALUE}));
    }

    @Test
    void theBuilderRefusesUnknownPointsAndLagsBeyondTheHorizon() {
        Network.Builder b = new Network.Builder(10);
        int a = b.point();
        assertThrows(IllegalArgumentException.class, () -> b.constrain(a, 7, 1));
        assertThrows(IllegalArgumentException.class, () -> b.constrain(-1, a, 1));
        assertThrows(IllegalArgumentException.class, () -> b.constrain(Network.ORIGIN, a, 11));
        assertThrows(IllegalArgumentException.class, () -> b.constrain(a, Network.ORIGIN, -11));
        assertThrows(IllegalArgumentException.class, () -> new Network.Builder(-1));
    }

    @Test
    void theBuilderRefusesAHorizonWhosePathSumsCouldOverflow() {
        Network.Builder b = new Network.Builder(Long.MAX_VALUE / 2);
        b.point();
        b.point();
        assertThrows(IllegalArgumentException.class, b::build);
    }

    private static List<Integer> sorted(int[] cycle) {
        return java.util.Arrays.stream(cycle).sorted().boxed().toList();
    }
}
