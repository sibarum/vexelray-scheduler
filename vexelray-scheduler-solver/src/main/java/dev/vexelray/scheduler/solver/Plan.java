package dev.vexelray.scheduler.solver;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Jobs, the resources they take, and the rules that decide which resource a job may take. Immutable; made by a
 * {@link Builder}.
 *
 * <p>A job is an interval of the {@link Network}: a start point and an end point a fixed duration apart. It has one
 * or more <em>demands</em>, each filled by one resource, and the demands of a job take different resources. Most jobs
 * have one; a job that needs a machine and an operator has two. Jobs, demands and resources all carry tags, and a
 * demand carries its job's tags as well as its own. {@link Rule}s over pairs of tags decide which resources a demand
 * is {@link #eligible} for.
 *
 * <p>Resources are not yet held exclusively: two jobs may take the same resource at the same time. So the timing and
 * the assignment are independent, and {@link #solve} answers each on its own.
 */
public final class Plan {

    /** A job: its two points in the network, its duration, its tags, and its demands by index. */
    public record Job(int start, int end, long duration, Set<String> tags, List<Integer> demands) {
    }

    /** A demand: the job it belongs to, and its tags, which include the job's. */
    public record Demand(int job, Set<String> tags) {
    }

    private final Network network;
    private final List<Job> jobs;
    private final List<Demand> demands;
    private final List<Set<String>> resources;
    private final List<Rule> rules;

    private Plan(Network network, List<Job> jobs, List<Demand> demands, List<Set<String>> resources, List<Rule> rules) {
        this.network = network;
        this.jobs = List.copyOf(jobs);
        this.demands = List.copyOf(demands);
        this.resources = List.copyOf(resources);
        this.rules = List.copyOf(rules);
    }

    public Network network() {
        return network;
    }

    public List<Job> jobs() {
        return jobs;
    }

    public List<Demand> demands() {
        return demands;
    }

    /** Every resource's tags, by index. */
    public List<Set<String>> resources() {
        return resources;
    }

    public List<Rule> rules() {
        return rules;
    }

    /** No rule rules out {@code demand} taking {@code resource}. {@code SchedulerProofs.eligible}. */
    public boolean eligible(int demand, int resource) {
        Set<String> d = demands.get(demand).tags();
        Set<String> r = resources.get(resource);
        for (Rule rule : rules) {
            if (rule.excludes(d, r)) {
                return false;
            }
        }
        return true;
    }

    /** The rules that rule out {@code demand} taking {@code resource}: the reasons, when it is not eligible. */
    public List<Rule> excluding(int demand, int resource) {
        Set<String> d = demands.get(demand).tags();
        Set<String> r = resources.get(resource);
        List<Rule> out = new ArrayList<>();
        for (Rule rule : rules) {
            if (rule.excludes(d, r)) {
                out.add(rule);
            }
        }
        return out;
    }

    /** The timing and the assignment, each solved on its own. */
    public Result solve() {
        return new Result(Solver.solve(network), Assigner.assign(this));
    }

    /** Both answers. The plan works when both have one. */
    public record Result(Solution timing, Assignment assignment) {

        public boolean feasible() {
            return timing.schedule().isPresent() && assignment.resources().isPresent();
        }
    }

    /** Adds jobs, demands, resources, rules and timing, then {@link #build}s the plan. */
    public static final class Builder {

        private final Network.Builder network;
        private final List<Job> jobs = new ArrayList<>();
        private final List<List<Integer>> jobDemands = new ArrayList<>();
        private final List<Demand> demands = new ArrayList<>();
        private final List<Set<String>> resources = new ArrayList<>();
        private final List<Rule> rules = new ArrayList<>();

        /** A plan whose jobs all lie in {@code [0, horizon]}. */
        public Builder(long horizon) {
            this.network = new Network.Builder(horizon);
        }

        /**
         * A job of {@code duration} ticks with {@code tags}, and its one demand. Returns its index.
         *
         * @throws IllegalArgumentException if the duration is negative or longer than the horizon
         */
        public int job(long duration, String... tags) {
            if (duration < 0) {
                throw new IllegalArgumentException("negative duration " + duration);
            }
            int start = network.point();
            int end = network.point();
            network.constrain(start, end, duration);
            network.constrain(end, start, -duration);
            int index = jobs.size();
            jobs.add(new Job(start, end, duration, Set.of(tags), List.of()));
            jobDemands.add(new ArrayList<>());
            demand(index);
            return index;
        }

        /**
         * Another demand of {@code job}, carrying the job's tags and {@code tags}: the job then needs one more resource,
         * different from its others. Returns the demand's index.
         */
        public int demand(int job, String... tags) {
            Set<String> all = new HashSet<>(jobs.get(job).tags());
            all.addAll(Set.of(tags));
            demands.add(new Demand(job, Set.copyOf(all)));
            jobDemands.get(job).add(demands.size() - 1);
            return demands.size() - 1;
        }

        /** A resource with {@code tags}. Returns its index. */
        public int resource(String... tags) {
            resources.add(Set.of(tags));
            return resources.size() - 1;
        }

        /** A demand tagged {@code demandTag} may not take a resource tagged {@code resourceTag}. */
        public Builder forbid(String demandTag, String resourceTag) {
            rules.add(Rule.forbid(demandTag, resourceTag));
            return this;
        }

        /** A demand tagged {@code demandTag} may only take a resource tagged {@code resourceTag}. */
        public Builder require(String demandTag, String resourceTag) {
            rules.add(Rule.require(demandTag, resourceTag));
            return this;
        }

        /** {@code later} starts at least {@code lag} ticks after {@code earlier} ends. Returns the constraint's index. */
        public int after(int earlier, int later, long lag) {
            return network.constrain(jobs.get(earlier).end(), jobs.get(later).start(), lag);
        }

        /** {@code job} starts no earlier than {@code t}. Returns the constraint's index. */
        public int release(int job, long t) {
            return network.release(jobs.get(job).start(), t);
        }

        /** {@code job} ends no later than {@code t}. Returns the constraint's index. */
        public int deadline(int job, long t) {
            return network.deadline(jobs.get(job).end(), t);
        }

        public Plan build() {
            List<Job> built = new ArrayList<>(jobs.size());
            for (int j = 0; j < jobs.size(); j++) {
                Job job = jobs.get(j);
                built.add(new Job(job.start(), job.end(), job.duration(), job.tags(), List.copyOf(jobDemands.get(j))));
            }
            return new Plan(network.build(), built, demands, resources, rules);
        }
    }
}
