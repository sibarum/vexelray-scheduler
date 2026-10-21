package dev.vexelray.scheduler.solver;

/**
 * Point {@code to} is at least {@code lag} ticks after point {@code from}: {@code start[from] + lag <= start[to]}.
 * A negative lag bounds {@code to} from above relative to {@code from}, which is how a deadline is written.
 *
 * <p>The Java form of {@code SchedulerProofs.Constraint}; its {@code Holds} is {@link #holds}.
 */
public record Constraint(int from, int to, long lag) {

    /**
     * Whether {@code starts} meets this constraint, exactly, as over the integers. The sum can only overflow
     * upwards when the lag is positive, and then it exceeds every {@code long}; or downwards when it is negative,
     * and then every {@code long} exceeds it.
     */
    public boolean holds(long[] starts) {
        long earliest;
        try {
            earliest = Math.addExact(starts[from], lag);
        } catch (ArithmeticException overflow) {
            return lag < 0;
        }
        return earliest <= starts[to];
    }
}
