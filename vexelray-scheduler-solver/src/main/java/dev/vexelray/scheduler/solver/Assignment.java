package dev.vexelray.scheduler.solver;

import java.util.Optional;

/**
 * What {@link Assigner#assign} found: a resource for every demand, or a {@link Shortage} proving there is no such
 * assignment. Exactly one is present, and the assigner has already passed it through the {@link Checker}.
 */
public final class Assignment {

    private final int[] resources;
    private final Shortage shortage;

    private Assignment(int[] resources, Shortage shortage) {
        this.resources = resources;
        this.shortage = shortage;
    }

    static Assignment of(int[] resources) {
        return new Assignment(resources.clone(), null);
    }

    static Assignment lacking(Shortage shortage) {
        return new Assignment(null, shortage);
    }

    /** Each demand's resource, by demand index, when there is an assignment. A copy. */
    public Optional<int[]> resources() {
        return Optional.ofNullable(resources).map(int[]::clone);
    }

    public Optional<Shortage> shortage() {
        return Optional.ofNullable(shortage);
    }

    /**
     * Demands of one job that, between them, are eligible for fewer resources than there are demands: they cannot each
     * have a different one. With one demand, simply a demand no resource is eligible for.
     */
    public static final class Shortage {

        private final Plan plan;
        private final int job;
        private final int[] demands;
        private final int[] resources;

        Shortage(Plan plan, int job, int[] demands, int[] resources) {
            this.plan = plan;
            this.job = job;
            this.demands = demands.clone();
            this.resources = resources.clone();
        }

        public int job() {
            return job;
        }

        /** The demands short of resources. A copy. */
        public int[] demands() {
            return demands.clone();
        }

        /** Every resource any of them is eligible for: fewer than the demands. A copy. */
        public int[] resources() {
            return resources.clone();
        }

        /** Why {@code demand} cannot take {@code resource}: the rules that rule it out, none if it can. */
        public java.util.List<Rule> reasons(int demand, int resource) {
            return plan.excluding(demand, resource);
        }
    }
}
