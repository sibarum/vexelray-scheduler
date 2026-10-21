package dev.vexelray.scheduler.solver;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * What {@link Solver#solve} found: a {@link Schedule}, or a {@link Conflict} proving there is none. Exactly one of
 * the two is present, and the solver has already passed it through the {@link Checker}.
 */
public final class Solution {

    private final Schedule schedule;
    private final Conflict conflict;

    private Solution(Schedule schedule, Conflict conflict) {
        this.schedule = schedule;
        this.conflict = conflict;
    }

    static Solution feasible(Schedule schedule) {
        return new Solution(schedule, null);
    }

    static Solution infeasible(Conflict conflict) {
        return new Solution(null, conflict);
    }

    /** The schedule, when the network has one. */
    public Optional<Schedule> schedule() {
        return Optional.ofNullable(schedule);
    }

    /** The conflict, when the network has no schedule. */
    public Optional<Conflict> conflict() {
        return Optional.ofNullable(conflict);
    }

    /**
     * Every point's window, and the earliest schedule.
     *
     * <p>{@link #earliest} and {@link #latest} are both schedules the checker accepts. Every point's time in
     * <em>every</em> schedule with the origin at 0 lies within its {@link #window}.
     */
    public static final class Schedule {

        private final long[] earliest;
        private final long[] latest;

        Schedule(long[] earliest, long[] latest) {
            this.earliest = earliest.clone();
            this.latest = latest.clone();
        }

        /** The earliest time of every point, as one schedule. A copy. */
        public long[] earliest() {
            return earliest.clone();
        }

        /** The latest time of every point, as one schedule. A copy. */
        public long[] latest() {
            return latest.clone();
        }

        /** The earliest start of {@code point}, which is its time in {@link #earliest}. */
        public long start(int point) {
            return earliest[point];
        }

        /** Where {@code point} can be in any schedule: from its earliest to its latest time. */
        public Span window(int point) {
            return new Span(earliest[point], latest[point]);
        }
    }

    /** Constraints that lead from a point back to itself and push it later than itself: the network's evidence of infeasibility. */
    public static final class Conflict {

        private final Network network;
        private final int[] cycle;

        Conflict(Network network, int[] cycle) {
            this.network = network;
            this.cycle = cycle.clone();
        }

        /** The cycle, as indices into {@link Network#constraints}, in the order they are walked. A copy. */
        public int[] cycle() {
            return cycle.clone();
        }

        /** The cycle's constraints, in the order they are walked. */
        public List<Constraint> constraints() {
            List<Constraint> out = new ArrayList<>(cycle.length);
            for (int k : cycle) {
                out.add(network.constraints().get(k));
            }
            return out;
        }

        /** How many ticks later than itself the cycle pushes its points: always more than zero. */
        public long excess() {
            long sum = 0;
            for (int k : cycle) {
                sum += network.constraints().get(k).lag();
            }
            return sum;
        }
    }
}
