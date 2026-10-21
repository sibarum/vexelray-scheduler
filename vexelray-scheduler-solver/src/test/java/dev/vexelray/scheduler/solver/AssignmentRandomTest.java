package dev.vexelray.scheduler.solver;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

import org.junit.jupiter.api.Test;

/**
 * The assigner on random plans: tiny ones decided by trying every assignment, larger ones built around a planted
 * assignment, and both kinds of evidence corrupted for the checker to refuse. Seeded; a failure names its seed.
 */
class AssignmentRandomTest {

    private static final String[] TAGS = {"red", "green", "blue", "purple"};

    @Test
    void theAssignerAgreesWithEveryAssignment() {
        int shortages = 0;
        for (long seed = 0; seed < 3000; seed++) {
            Random random = new Random(seed);
            Plan plan = randomPlan(random, 1 + random.nextInt(3), 2, random.nextInt(4), random.nextInt(5));
            boolean any = anyValid(plan, new int[plan.demands().size()], 0);
            Assignment assignment = Assigner.assign(plan);
            assertEquals(any, assignment.resources().isPresent(), "seed " + seed);
            if (any) {
                assertTrue(Checker.assignment(plan, assignment.resources().orElseThrow()), "seed " + seed);
            } else {
                Assignment.Shortage s = assignment.shortage().orElseThrow();
                assertTrue(Checker.shortage(plan, s.job(), s.demands(), s.resources()), "seed " + seed);
                shortages++;
            }
        }
        assertTrue(shortages > 300, "only " + shortages + " infeasible plans: the generator is too kind");
    }

    @Test
    void aPlantedAssignmentIsAlwaysMatched() {
        for (long seed = 0; seed < 500; seed++) {
            Random random = new Random(seed);
            int resources = 3 + random.nextInt(20);
            Plan.Builder b = new Plan.Builder(1000);
            List<String[]> resourceTags = new ArrayList<>();
            for (int r = 0; r < resources; r++) {
                String[] tags = tags(random);
                resourceTags.add(tags);
                b.resource(tags);
            }
            // Plant: each job's demands on distinct resources.
            List<int[]> planted = new ArrayList<>();
            int jobs = 1 + random.nextInt(30);
            for (int j = 0; j < jobs; j++) {
                int job = b.job(random.nextInt(50), tags(random));
                int extra = random.nextInt(Math.min(3, resources));
                for (int e = 0; e < extra; e++) b.demand(job, tags(random));
                int[] taken = distinct(random, resources, 1 + extra);
                planted.add(taken);
            }
            Plan draft = b.build();
            int[] plant = new int[draft.demands().size()];
            for (int j = 0; j < jobs; j++) {
                List<Integer> ds = draft.jobs().get(j).demands();
                for (int i = 0; i < ds.size(); i++) plant[ds.get(i)] = planted.get(j)[i];
            }
            // Rules at random, keeping only those the plant does not break.
            for (int i = 0; i < 8; i++) {
                Rule rule = random.nextBoolean() ? Rule.forbid(tag(random), tag(random)) : Rule.require(tag(random), tag(random));
                boolean breaksPlant = false;
                for (int d = 0; d < plant.length && !breaksPlant; d++) {
                    breaksPlant = rule.excludes(draft.demands().get(d).tags(), draft.resources().get(plant[d]));
                }
                if (breaksPlant) continue;
                if (rule.kind() == Rule.Kind.FORBID) b.forbid(rule.demandTag(), rule.resourceTag());
                else b.require(rule.demandTag(), rule.resourceTag());
            }
            Plan plan = b.build();
            assertTrue(Checker.assignment(plan, plant), "seed " + seed + ": the generator broke its own plant");
            long s = seed;
            int[] found = Assigner.assign(plan).resources().orElseThrow(() -> new AssertionError(
                    "seed " + s + ": a planted assignment was not found"));
            assertTrue(Checker.assignment(plan, found), "seed " + seed);
        }
    }

    @Test
    void corruptedAssignmentsAreRefused() {
        int moved = 0;
        for (long seed = 0; seed < 3000; seed++) {
            Random random = new Random(seed);
            Plan plan = randomPlan(random, 1 + random.nextInt(4), 3, 1 + random.nextInt(5), 1 + random.nextInt(4));
            int[] good = Assigner.assign(plan).resources().orElse(null);
            if (good == null) continue;
            int d = random.nextInt(good.length);
            // Onto a resource a demand is not eligible for, if any demand has one.
            search:
            for (int dd = 0; dd < good.length; dd++) {
                for (int r = 0; r < plan.resources().size(); r++) {
                    if (!plan.eligible(dd, r)) {
                        int[] bad = good.clone();
                        bad[dd] = r;
                        assertFalse(Checker.assignment(plan, bad), "seed " + seed + ": demand " + dd + " on " + r);
                        moved++;
                        break search;
                    }
                }
            }
            // Onto another demand's resource, in the same job.
            List<Integer> same = plan.jobs().get(plan.demands().get(d).job()).demands();
            if (same.size() > 1) {
                int other = same.get(0) == d ? same.get(1) : same.get(0);
                int[] bad = good.clone();
                bad[d] = good[other];
                assertFalse(Checker.assignment(plan, bad), "seed " + seed + ": two demands of a job share");
            }
            int[] outOfRange = good.clone();
            outOfRange[d] = random.nextBoolean() ? plan.resources().size() : -1;
            assertFalse(Checker.assignment(plan, outOfRange), "seed " + seed + ": no such resource");
        }
        // About one random plan in five has an ineligible pair at all; most of the rest have no rule that bites.
        assertTrue(moved > 300, "only " + moved + " runs had an ineligible resource to move onto");
    }

    @Test
    void corruptedShortagesAreRefused() {
        int checked = 0;
        for (long seed = 0; seed < 3000; seed++) {
            Random random = new Random(seed);
            Plan plan = randomPlan(random, 1 + random.nextInt(3), 3, random.nextInt(4), random.nextInt(5));
            Assignment.Shortage s = Assigner.assign(plan).shortage().orElse(null);
            if (s == null) continue;
            int[] demands = s.demands();
            int[] resources = s.resources();
            checked++;
            if (resources.length > 0) {
                // Drop a resource that one of the demands is eligible for: the cover breaks.
                int[] fewer = java.util.Arrays.copyOf(resources, resources.length - 1);
                assertFalse(Checker.shortage(plan, s.job(), demands, fewer), "seed " + seed + ": a resource dropped");
            }
            // Pad the resources up to the demands: no longer short.
            int[] padded = java.util.Arrays.copyOf(resources, demands.length);
            for (int i = resources.length; i < padded.length; i++) padded[i] = resources.length > 0 ? resources[0] : 0;
            assertFalse(Checker.shortage(plan, s.job(), demands, padded), "seed " + seed + ": as many as demands");
            // Count a demand twice.
            int[] twice = java.util.Arrays.copyOf(demands, demands.length + 1);
            twice[demands.length] = demands[0];
            int[] wider = java.util.Arrays.copyOf(resources, resources.length);
            assertFalse(Checker.shortage(plan, s.job(), twice, wider), "seed " + seed + ": a demand twice");
            // Blame another job.
            if (plan.jobs().size() > 1) {
                int other = (s.job() + 1) % plan.jobs().size();
                assertFalse(Checker.shortage(plan, other, demands, resources), "seed " + seed + ": the wrong job");
            }
        }
        assertTrue(checked > 300, "only " + checked + " shortages to corrupt");
    }

    /** Up to {@code maxDemands} demands a job, tags from a small set so rules bite often. */
    private static Plan randomPlan(Random random, int jobs, int maxDemands, int resources, int rules) {
        Plan.Builder b = new Plan.Builder(100);
        for (int j = 0; j < jobs; j++) {
            int job = b.job(random.nextInt(10), tags(random));
            int extra = random.nextInt(maxDemands);
            for (int e = 0; e < extra; e++) b.demand(job, tags(random));
        }
        for (int r = 0; r < resources; r++) b.resource(tags(random));
        for (int i = 0; i < rules; i++) {
            if (random.nextBoolean()) b.forbid(tag(random), tag(random));
            else b.require(tag(random), tag(random));
        }
        return b.build();
    }

    /** Whether any assignment of demands {@code d} onwards passes the checker, which is proved to mean valid. */
    private static boolean anyValid(Plan plan, int[] resourceOf, int d) {
        if (d == resourceOf.length) return Checker.assignment(plan, resourceOf);
        for (int r = 0; r < plan.resources().size(); r++) {
            resourceOf[d] = r;
            if (anyValid(plan, resourceOf, d + 1)) return true;
        }
        return false;
    }

    private static String[] tags(Random random) {
        List<String> out = new ArrayList<>();
        for (String t : TAGS) if (random.nextInt(3) == 0) out.add(t);
        return out.toArray(String[]::new);
    }

    private static String tag(Random random) {
        return TAGS[random.nextInt(TAGS.length)];
    }

    private static int[] distinct(Random random, int bound, int count) {
        List<Integer> all = new ArrayList<>();
        for (int i = 0; i < bound; i++) all.add(i);
        java.util.Collections.shuffle(all, random);
        return all.subList(0, count).stream().mapToInt(Integer::intValue).toArray();
    }
}
