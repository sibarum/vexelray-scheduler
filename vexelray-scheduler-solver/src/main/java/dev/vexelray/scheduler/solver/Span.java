package dev.vexelray.scheduler.solver;

/** The closed range of ticks {@code [lo, hi]}. Never empty: an empty range is an infeasible network, not a span. */
public record Span(long lo, long hi) {

    public Span {
        if (lo > hi) {
            throw new IllegalArgumentException("empty span [" + lo + ", " + hi + "]");
        }
    }

    public boolean contains(long t) {
        return lo <= t && t <= hi;
    }
}
