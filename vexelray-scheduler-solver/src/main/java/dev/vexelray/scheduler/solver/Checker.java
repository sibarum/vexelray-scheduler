package dev.vexelray.scheduler.solver;

import java.util.List;

/**
 * Reads the evidence the solver returns. These two methods are {@code checkSchedule} and {@code checkCycle} in
 * {@code vexelray-scheduler-proofs}' {@code SchedulerProofs/Checker.lean}, line for line, where it is proved that
 * acceptance means what the javadoc says. Keep them that way: a change here is a change there first.
 *
 * <p>The one difference is that a cycle here names its constraints by index into the network, where the Lean
 * cycle is the constraints themselves and checks that each is in the network. Resolving the indices, in bounds, is
 * that check. And a cycle longer than the network has points is rejected here: a cycle that long repeats a point, so
 * a shorter one is inside it, and the bound keeps the lag sum exact (each lag is within the horizon, and
 * {@code points × horizon} fits a {@code long}). Rejecting more than the Lean checker is still sound.
 */
public final class Checker {

    private Checker() {
    }

    /** Every constraint holds under {@code starts}, one time per point: the schedule meets the network. */
    public static boolean schedule(Network network, long[] starts) {
        if (starts.length != network.points()) {
            return false;
        }
        for (Constraint c : network.constraints()) {
            if (!c.holds(starts)) {
                return false;
            }
        }
        return true;
    }

    /**
     * The cycle is non-empty, names constraints of the network, leads from its first constraint's source back to that
     * point, and its lags sum to more than zero: no schedule meets the network.
     */
    public static boolean cycle(Network network, int[] cycle) {
        if (cycle.length == 0 || cycle.length > network.points()) {
            return false;
        }
        List<Constraint> constraints = network.constraints();
        for (int k : cycle) {
            if (k < 0 || k >= constraints.size()) {
                return false;
            }
        }
        // walks c.src (c :: p) c.src: each constraint starts where the last ended, and the last ends at the start.
        int start = constraints.get(cycle[0]).from();
        int at = start;
        long lagSum = 0;
        for (int k : cycle) {
            Constraint c = constraints.get(k);
            if (c.from() != at) {
                return false;
            }
            at = c.to();
            lagSum += c.lag();
        }
        return at == start && lagSum > 0;
    }
}
