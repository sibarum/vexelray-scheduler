package dev.vexelray.scheduler.solver;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Random;

import org.junit.jupiter.api.Test;

/**
 * Evidence corrupted on purpose, which the checker must refuse, and the solver's last gate refusing it too.
 *
 * <p>The checker that accepts too much is the dangerous one, and the Lean proof covers only the Lean checker; the Java
 * copy is covered by tests, and these are the tests that push it towards saying yes when it should not. Every
 * corruption here is one that is wrong whatever else the network holds: a point moved one tick across a constraint it
 * met exactly, a cycle with a link removed, a lag lowered until the cycle gains nothing.
 *
 * <p>Seeded like {@link PlantedTest}: a failure names its seed.
 */
class RejectionTest {

    private static final int RUNS = 500;

    @Test
    void aScheduleMovedOneTickAcrossATightConstraintIsRefused() {
        for (long seed = 0; seed < RUNS; seed++) {
            Random random = new Random(seed);
            Network network = Planted.draw(random).network();
            long[] good = Solver.solve(network).schedule().orElseThrow().earliest();
            List<Constraint> constraints = network.constraints();
            // In the earliest schedule some constraint is tight: every point's time is set by one.
            int tight = -1;
            for (int tries = 0; tight < 0; tries++) {
                int k = random.nextInt(constraints.size());
                Constraint c = constraints.get(k);
                if (c.from() != c.to() && good[c.from()] + c.lag() == good[c.to()]) tight = k;
                if (tries > 100_000) throw new AssertionError("seed " + seed + ": no tight constraint");
            }
            Constraint c = constraints.get(tight);

            long[] early = good.clone();
            early[c.to()]--;
            assertFalse(Checker.schedule(network, early), "seed " + seed + ": " + c + "'s end moved a tick early");
            long[] late = good.clone();
            late[c.from()]++;
            assertFalse(Checker.schedule(network, late), "seed " + seed + ": " + c + "'s start moved a tick late");

            int p = 1 + random.nextInt(network.points() - 1);
            long[] past = good.clone();
            past[p] = network.horizon() + 1 + random.nextInt(10);
            assertFalse(Checker.schedule(network, past), "seed " + seed + ": point " + p + " past the horizon");
            long[] before = good.clone();
            before[p] = -1 - random.nextInt(10);
            assertFalse(Checker.schedule(network, before), "seed " + seed + ": point " + p + " before the origin");
        }
    }

    @Test
    void aCycleWithALinkRemovedOrAMissingConstraintIsRefused() {
        int gaps = 0;
        for (long seed = 0; seed < RUNS; seed++) {
            Random random = new Random(seed);
            Network network = Planted.draw(random).broken(random);
            int[] cycle = Solver.solve(network).conflict().orElseThrow().cycle();
            List<Constraint> constraints = network.constraints();

            // Every rotation is the same cycle, so the checker must still accept it: the corruptions below are
            // refused for what they break, not for where the cycle starts.
            int r = random.nextInt(cycle.length);
            int[] rotated = new int[cycle.length];
            for (int i = 0; i < cycle.length; i++) rotated[i] = cycle[(i + r) % cycle.length];
            assertTrue(Checker.cycle(network, rotated), "seed " + seed + ": a rotation was refused");

            int drop = random.nextInt(cycle.length);
            Constraint dropped = constraints.get(cycle[drop]);
            if (cycle.length > 1 && dropped.from() != dropped.to()) {
                int[] gapped = new int[cycle.length - 1];
                for (int i = 0, j = 0; i < cycle.length; i++) if (i != drop) gapped[j++] = cycle[i];
                assertFalse(Checker.cycle(network, gapped), "seed " + seed + ": link " + drop + " removed");
                gaps++;
            }

            int[] missing = cycle.clone();
            missing[random.nextInt(cycle.length)] = random.nextBoolean()
                    ? constraints.size() + random.nextInt(10)
                    : -1 - random.nextInt(10);
            assertFalse(Checker.cycle(network, missing), "seed " + seed + ": names a constraint that is not there");
        }
        assertTrue(gaps > RUNS / 2, "only " + gaps + " of " + RUNS + " runs had a link to remove");
    }

    @Test
    void aCycleWhoseLagsSumToZeroIsRefused() {
        int checked = 0;
        for (long seed = 0; seed < RUNS; seed++) {
            Random random = new Random(seed);
            Planted planted = Planted.draw(random);
            Network network = planted.broken(random);
            Solution.Conflict conflict = Solver.solve(network).conflict().orElseThrow();
            int[] cycle = conflict.cycle();
            // Lower one of the cycle's lags by its whole excess, in a copy of the network, so the same cycle gains
            // exactly nothing. Only a constraint the test added can be rebuilt with another lag, and only one whose
            // lowered lag stays within the horizon.
            int implicit = 2 * (network.points() - 1);
            int lower = -1;
            for (int k : cycle) {
                if (k >= implicit && network.constraints().get(k).lag() - conflict.excess() >= -network.horizon()) {
                    lower = k;
                }
            }
            if (lower < 0) continue;
            Network flat = rebuild(network, lower, network.constraints().get(lower).lag() - conflict.excess());
            assertFalse(Checker.cycle(flat, cycle), "seed " + seed + ": a cycle gaining nothing was accepted");
            checked++;
        }
        // A run is skipped when no lag of its cycle can be lowered; most must not be, or this tests nothing.
        assertTrue(checked > RUNS / 2, "only " + checked + " of " + RUNS + " runs had a lag to lower");
    }

    @Test
    void theSolversLastGateRefusesEvidenceTheCheckerRefuses() {
        // m at least 3 after a: feasible, so a schedule can be good or bad.
        Network.Builder fb = new Network.Builder(10);
        fb.constrain(fb.point(), fb.point(), 3);
        Network feasible = fb.build();
        long[] good = {0, 0, 3};
        long[] bad = {0, 0, 2};
        assertTrue(Solver.feasible(feasible, good, good).schedule().isPresent());
        assertThrows(IllegalStateException.class, () -> Solver.feasible(feasible, bad, good), "bad earliest");
        assertThrows(IllegalStateException.class, () -> Solver.feasible(feasible, good, bad), "bad latest");

        // ...and at most 2 after it: infeasible, so a cycle can be good or bad.
        Network.Builder b = new Network.Builder(10);
        int a = b.point(), m = b.point();
        int ab = b.constrain(a, m, 3);
        int ba = b.constrain(m, a, -2);
        Network network = b.build();
        assertTrue(Solver.infeasible(network, new int[] {ab, ba}).conflict().isPresent());
        assertThrows(IllegalStateException.class, () -> Solver.infeasible(network, new int[] {ab}), "open walk");
        assertThrows(IllegalStateException.class, () -> Solver.infeasible(network, new int[0]), "empty");
    }

    /** The same network, the constraint at {@code k} given {@code lag}. Points first, as {@link Planted} adds them. */
    private static Network rebuild(Network network, int k, long lag) {
        Network.Builder b = new Network.Builder(network.horizon());
        for (int p = 1; p < network.points(); p++) b.point();
        List<Constraint> constraints = network.constraints();
        for (int i = 2 * (network.points() - 1); i < constraints.size(); i++) {
            Constraint c = constraints.get(i);
            b.constrain(c.from(), c.to(), i == k ? lag : c.lag());
        }
        Network copy = b.build();
        if (copy.constraints().size() != constraints.size()) {
            throw new AssertionError("the rebuilt network does not line up with the original");
        }
        return copy;
    }
}
