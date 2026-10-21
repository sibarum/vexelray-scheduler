package dev.vexelray.scheduler.solver;

import java.util.List;
import java.util.PriorityQueue;

/**
 * Solves a {@link Network}: its earliest and latest schedules, or a cycle proving it has none.
 *
 * <p>A constraint {@code from + lag <= to} is an edge {@code from → to} of weight {@code lag}, a point's earliest time
 * is the longest path to it from the origin, and a cycle whose lags sum to more than zero, which has no longest path,
 * is exactly what makes the network infeasible.
 *
 * <p><b>Earliest times</b> are Bellman-Ford with a queue (Bellman-Ford-Moore): only the constraints out of a point
 * whose time just rose are looked at again. It works in passes, each the points queued during the last, and keeps
 * Bellman-Ford's bound: a network with no positive cycle settles within {@code n - 1} passes, so a pass {@code n}
 * in which a point still rises is the evidence of one. A chain whose points were created in its own order settles in
 * one pass however its constraints were listed; one created against its own order still takes a pass a link.
 *
 * <p><b>Latest times</b> are a longest path from every point to the origin. Once the earliest times {@code e} are
 * known, they make every constraint's reduced cost {@code e[to] - e[from] - lag} non-negative (that is what being a
 * schedule means), so the backward search is Dijkstra's, with no bad orders at all.
 *
 * <p>Nothing the solver returns is taken on trust: both schedules and every conflict go through the {@link Checker}
 * before they are returned, and one that fails is a bug in this class, thrown as {@link IllegalStateException}
 * rather than handed on.
 */
public final class Solver {

    private Solver() {
    }

    public static Solution solve(Network network) {
        int n = network.points();
        List<Constraint> constraints = network.constraints();
        int[][] out = adjacency(network, true);

        long[] earliest = new long[n];
        boolean[] reached = new boolean[n];
        int[] pred = new int[n];
        java.util.Arrays.fill(pred, -1);
        reached[Network.ORIGIN] = true;

        // A ring of at most n points, each queued at most once at a time.
        int[] queue = new int[n];
        boolean[] queued = new boolean[n];
        int head = 0;
        int size = 0;
        queue[0] = Network.ORIGIN;
        queued[Network.ORIGIN] = true;
        size = 1;

        for (int pass = 1; size > 0; pass++) {
            if (pass > n) {
                // A point rose in pass n: a positive cycle exists, and the predecessors hold one. (Pass n itself
                // having points proves nothing: a point that settled in pass n - 1 is still queued for it.)
                int[] cycle = predecessorCycle(network, pred);
                if (cycle != null) {
                    return infeasible(network, cycle);
                }
                // The cycle is not in the predecessors yet. It will be: they stay a tree only while every time is
                // bounded by the tree's paths, and a positive cycle raises its times without bound.
            }
            int inThisPass = size;
            for (int i = 0; i < inThisPass; i++) {
                int u = queue[head];
                head = (head + 1) % n;
                size--;
                queued[u] = false;
                for (int k : out[u]) {
                    Constraint c = constraints.get(k);
                    long candidate;
                    try {
                        candidate = Math.addExact(earliest[u], c.lag());
                    } catch (ArithmeticException overflow) {
                        throw new IllegalStateException("a path sum overflowed: the pass bound has failed", overflow);
                    }
                    if (!reached[c.to()] || candidate > earliest[c.to()]) {
                        earliest[c.to()] = candidate;
                        reached[c.to()] = true;
                        pred[c.to()] = k;
                        if (!queued[c.to()]) {
                            queue[(head + size) % n] = c.to();
                            size++;
                            queued[c.to()] = true;
                        }
                    }
                }
            }
        }
        return feasible(network, earliest);
    }

    private static Solution feasible(Network network, long[] earliest) {
        return feasible(network, earliest, latest(network, earliest));
    }

    /**
     * The last gate before a schedule leaves the solver. Package-private so a test can hand it schedules the search
     * would never produce and see it refuse them.
     */
    static Solution feasible(Network network, long[] earliest, long[] latest) {
        if (!Checker.schedule(network, earliest) || !Checker.schedule(network, latest)) {
            throw new IllegalStateException("the solver's schedule failed the checker");
        }
        return Solution.feasible(new Solution.Schedule(earliest, latest));
    }

    /**
     * The latest time of every point. With {@code e} the earliest schedule, a path from {@code p} to the origin has
     * total lag {@code -e[p] - Σ cost}, where each constraint's cost {@code e[to] - e[from] - lag} is non-negative. So
     * the latest time, minus the longest such path, is {@code e[p] + d[p]} with {@code d[p]} the cheapest path from
     * {@code p} to the origin: Dijkstra, from the origin, over the constraints backwards.
     *
     * <p>Every point reaches the origin through its deadline. Exact: a final {@code d} is at most the horizon (the
     * latest time is), a cost at most twice it, and the builder checked that a few horizons fit a long.
     */
    private static long[] latest(Network network, long[] e) {
        int n = network.points();
        List<Constraint> constraints = network.constraints();
        int[][] in = adjacency(network, false);
        long[] d = new long[n];
        boolean[] seen = new boolean[n];
        boolean[] settled = new boolean[n];
        PriorityQueue<long[]> frontier = new PriorityQueue<>((a, b) -> Long.compare(a[0], b[0]));
        seen[Network.ORIGIN] = true;
        frontier.add(new long[] {0, Network.ORIGIN});
        while (!frontier.isEmpty()) {
            long[] top = frontier.poll();
            int v = (int) top[1];
            if (settled[v]) {
                continue;
            }
            settled[v] = true;
            for (int k : in[v]) {
                Constraint c = constraints.get(k);
                long cost = e[c.to()] - e[c.from()] - c.lag();
                long candidate = d[v] + cost;
                if (!seen[c.from()] || candidate < d[c.from()]) {
                    d[c.from()] = candidate;
                    seen[c.from()] = true;
                    frontier.add(new long[] {candidate, c.from()});
                }
            }
        }
        long[] latest = new long[n];
        for (int p = 0; p < n; p++) {
            if (!settled[p]) {
                throw new IllegalStateException("point " + p + " does not reach the origin, but every point has a deadline");
            }
            latest[p] = e[p] + d[p];
        }
        return latest;
    }

    /**
     * A cycle among the predecessors, read in the order it is walked, or null if they hold none yet. Every point has
     * at most one predecessor, so this is a cycle in a functional graph: follow each chain, colouring it, until it
     * ends, reaches an older chain, or meets itself. A predecessor cycle always has a positive total lag, so it is
     * the conflict; the checker confirms it either way.
     */
    private static int[] predecessorCycle(Network network, int[] pred) {
        List<Constraint> constraints = network.constraints();
        int n = network.points();
        int[] colour = new int[n];  // 0 unvisited, else the chain number that visited it
        for (int startAt = 0; startAt < n; startAt++) {
            int chain = startAt + 1;
            int at = startAt;
            while (colour[at] == 0) {
                colour[at] = chain;
                int k = pred[at];
                if (k < 0) {
                    break;
                }
                at = constraints.get(k).from();
            }
            if (colour[at] != chain || pred[at] < 0) {
                continue;
            }
            // at is on a cycle this chain just closed. Walk it backwards, then reverse.
            int start = at;
            int length = 0;
            int[] backwards = new int[n];
            do {
                int k = pred[at];
                backwards[length++] = k;
                at = constraints.get(k).from();
            } while (at != start);
            int[] cycle = new int[length];
            for (int i = 0; i < length; i++) {
                cycle[i] = backwards[length - 1 - i];
            }
            return cycle;
        }
        return null;
    }

    /** The last gate before a conflict leaves the solver; package-private for the same reason as {@link #feasible}. */
    static Solution infeasible(Network network, int[] cycle) {
        if (!Checker.cycle(network, cycle)) {
            throw new IllegalStateException("the solver's conflict failed the checker");
        }
        return Solution.infeasible(new Solution.Conflict(network, cycle));
    }

    /** Each point's constraints, by index: those leaving it if {@code outgoing}, else those arriving. */
    private static int[][] adjacency(Network network, boolean outgoing) {
        int n = network.points();
        List<Constraint> constraints = network.constraints();
        int[] count = new int[n];
        for (Constraint c : constraints) {
            count[outgoing ? c.from() : c.to()]++;
        }
        int[][] edges = new int[n][];
        for (int p = 0; p < n; p++) {
            edges[p] = new int[count[p]];
        }
        int[] filled = new int[n];
        for (int k = 0; k < constraints.size(); k++) {
            int p = outgoing ? constraints.get(k).from() : constraints.get(k).to();
            edges[p][filled[p]++] = k;
        }
        return edges;
    }
}
