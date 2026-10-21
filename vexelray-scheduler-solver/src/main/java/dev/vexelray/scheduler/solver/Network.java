package dev.vexelray.scheduler.solver;

import java.util.ArrayList;
import java.util.List;

/**
 * Time points and the difference constraints between them, within a horizon. Immutable; made by a {@link Builder}.
 *
 * <p>Point {@link #ORIGIN} is time zero. Release times, deadlines and the horizon are constraints against it like any
 * other, so a conflict can name them: every point the builder adds comes with a release at 0 and a deadline at the
 * horizon, and those two constraints have indices like the rest.
 *
 * <p>Times are relative to the origin's. A schedule {@link Checker#schedule accepted} by the checker stays accepted
 * when every time is shifted by the same amount; the solver's schedules put the origin at 0.
 */
public final class Network {

    /** The point that is time zero. */
    public static final int ORIGIN = 0;

    private final int points;
    private final long horizon;
    private final List<Constraint> constraints;

    private Network(int points, long horizon, List<Constraint> constraints) {
        this.points = points;
        this.horizon = horizon;
        this.constraints = List.copyOf(constraints);
    }

    /** The number of points, the origin included. */
    public int points() {
        return points;
    }

    /** Every point lies in {@code [0, horizon]}. */
    public long horizon() {
        return horizon;
    }

    /** Every constraint, in the order they were added; a conflict names them by index into this list. */
    public List<Constraint> constraints() {
        return constraints;
    }

    /** Adds points and constraints, then {@link #build}s the network. */
    public static final class Builder {

        private final long horizon;
        private final List<Constraint> constraints = new ArrayList<>();
        private int points = 1;  // the origin

        /**
         * A network whose points all lie in {@code [0, horizon]}.
         *
         * @throws IllegalArgumentException if {@code horizon} is negative
         */
        public Builder(long horizon) {
            if (horizon < 0) {
                throw new IllegalArgumentException("negative horizon " + horizon);
            }
            this.horizon = horizon;
        }

        /** A new point, released at 0 and due by the horizon. Returns its index. */
        public int point() {
            int p = points++;
            release(p, 0);
            deadline(p, horizon);
            return p;
        }

        /**
         * {@code to} at least {@code lag} ticks after {@code from}. Returns the constraint's index.
         *
         * @throws IllegalArgumentException if either point does not exist, or {@code |lag|} exceeds the horizon (such a
         *     constraint is either always met or never, and admitting it would let path sums overflow)
         */
        public int constrain(int from, int to, long lag) {
            checkPoint(from);
            checkPoint(to);
            if (lag > horizon || lag < -horizon) {
                throw new IllegalArgumentException("lag " + lag + " is outside the horizon " + horizon);
            }
            constraints.add(new Constraint(from, to, lag));
            return constraints.size() - 1;
        }

        /** {@code point} no earlier than {@code t}. Returns the constraint's index. */
        public int release(int point, long t) {
            return constrain(ORIGIN, point, t);
        }

        /** {@code point} no later than {@code t}. Returns the constraint's index. */
        public int deadline(int point, long t) {
            return constrain(point, ORIGIN, -t);
        }

        /**
         * The network.
         *
         * @throws IllegalArgumentException if {@code (points + 2) × horizon} does not fit a {@code long}: the solver's
         *     sums are bounded by it (a path's lags by {@code points × horizon}, the backward search's costs by three
         *     horizons), and this is what keeps its arithmetic exact
         */
        public Network build() {
            try {
                Math.multiplyExact(points + 2L, Math.max(horizon, 1));
            } catch (ArithmeticException overflow) {
                throw new IllegalArgumentException(points + " points over a horizon of " + horizon
                        + " ticks could overflow a path sum");
            }
            return new Network(points, horizon, constraints);
        }

        private void checkPoint(int p) {
            if (p < 0 || p >= points) {
                throw new IllegalArgumentException("no point " + p + " (there are " + points + ")");
            }
        }
    }
}
