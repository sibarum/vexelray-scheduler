package dev.vexelray.scheduler.solver;

import java.util.Random;

/** A network, the schedule planted in it, and its builder, still open for a test to add to. */
record Planted(Network network, Network.Builder builder, long[] times, long horizon) {

    static Planted draw(Random random) {
        long horizon = 1 + random.nextInt(1000);
        int points = 1 + random.nextInt(30);
        Network.Builder b = new Network.Builder(horizon);
        long[] times = new long[points + 1];
        for (int p = 1; p <= points; p++) {
            b.point();
            times[p] = random.nextLong(horizon + 1);
        }
        int precedences = random.nextInt(3 * points + 1);
        for (int i = 0; i < precedences; i++) {
            int from = 1 + random.nextInt(points);
            int to = 1 + random.nextInt(points);
            long gap = times[to] - times[from];
            // A lag at most the gap holds; at most the gap + horizon below it stays within the horizon.
            long slack = tight(random) ? 0 : random.nextLong(gap + horizon + 1);
            b.constrain(from, to, gap - slack);
        }
        for (int p = 1; p <= points; p++) {
            if (random.nextInt(3) == 0) b.release(p, times[p] - (tight(random) ? 0 : random.nextLong(times[p] + 1)));
            if (random.nextInt(3) == 0) {
                b.deadline(p, times[p] + (tight(random) ? 0 : random.nextLong(horizon - times[p] + 1)));
            }
        }
        Network network = b.build();
        if (!Checker.schedule(network, times)) {
            throw new AssertionError("the generator broke its own plant");
        }
        return new Planted(network, b, times, horizon);
    }

    /**
     * This network with one more constraint, closing a positive cycle: a constraint with lag {@code l} reversed by one
     * with lag {@code 1 - l} or more, within the horizon, so the two sum to more than zero. Builds from the same
     * builder, so call it once.
     */
    Network broken(Random random) {
        java.util.List<Constraint> existing = network.constraints();
        Constraint c = null;
        for (int tries = 0; c == null; tries++) {
            Constraint pick = existing.get(random.nextInt(existing.size()));
            if (1 - pick.lag() <= horizon) c = pick;
            if (tries > 10_000) throw new AssertionError("nothing to reverse");
        }
        long lag = 1 - c.lag() + random.nextLong(horizon - (1 - c.lag()) + 1);
        builder.constrain(c.to(), c.from(), lag);
        return builder.build();
    }

    private static boolean tight(Random random) {
        return random.nextInt(4) == 0;
    }
}
