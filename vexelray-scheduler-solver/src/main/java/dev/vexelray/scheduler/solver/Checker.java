package dev.vexelray.scheduler.solver;

import java.util.List;

/**
 * Reads the evidence the solver and the assigner return. Each method is a function in {@code
 * vexelray-scheduler-proofs}, line for line, where it is proved that acceptance means what the javadoc says. Keep them
 * that way: a change here is a change there first.
 *
 * <ul>
 *   <li>{@link #schedule} and {@link #cycle}: {@code checkSchedule} and {@code checkCycle}, in {@code
 *       SchedulerProofs/Checker.lean}.
 *   <li>{@link #assignment} and {@link #shortage}: {@code checkAssignment} and {@code checkShortage}, in {@code
 *       SchedulerProofs/Assignment.lean}. Lean's jobs are lists of demands and its tags numbers; here a job is named by
 *       index and tags are strings.
 * </ul>
 *
 * <p>For the cycle, the one difference is that it names its constraints by index into the network, where the Lean
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

    /**
     * Every demand of every job has a resource that exists and that it is eligible for, and no two demands of a job
     * share one: the assignment is valid. {@code resourceOf} is indexed by demand.
     */
    public static boolean assignment(Plan plan, int[] resourceOf) {
        if (resourceOf.length != plan.demands().size()) {
            return false;
        }
        int resources = plan.resources().size();
        for (Plan.Job job : plan.jobs()) {
            for (int d : job.demands()) {
                int r = resourceOf[d];
                if (!(0 <= r && r < resources) || !plan.eligible(d, r)) {
                    return false;
                }
            }
            for (int d1 : job.demands()) {
                for (int d2 : job.demands()) {
                    if (resourceOf[d1] == resourceOf[d2] && d1 != d2) {
                        return false;
                    }
                }
            }
        }
        return true;
    }

    /**
     * {@code job} is a job, {@code demands} are some of its demands without repeats, every resource any of them is
     * eligible for is in {@code resources}, and there are fewer of those than demands: no assignment is valid.
     */
    public static boolean shortage(Plan plan, int job, int[] demands, int[] resources) {
        if (!(0 <= job && job < plan.jobs().size())) {
            return false;
        }
        List<Integer> ofJob = plan.jobs().get(job).demands();
        for (int d : demands) {
            if (!ofJob.contains(d)) {
                return false;
            }
        }
        for (int i = 0; i < demands.length; i++) {
            for (int k = i + 1; k < demands.length; k++) {
                if (demands[i] == demands[k]) {
                    return false;
                }
            }
        }
        for (int d : demands) {
            for (int r = 0; r < plan.resources().size(); r++) {
                if (plan.eligible(d, r) && !contains(resources, r)) {
                    return false;
                }
            }
        }
        return resources.length < demands.length;
    }

    private static boolean contains(int[] values, int value) {
        for (int v : values) {
            if (v == value) {
                return true;
            }
        }
        return false;
    }
}
