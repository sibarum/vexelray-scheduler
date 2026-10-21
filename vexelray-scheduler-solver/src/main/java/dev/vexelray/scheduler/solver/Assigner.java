package dev.vexelray.scheduler.solver;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Gives every demand a resource it is eligible for, the demands of a job different ones, or finds a {@link
 * Assignment.Shortage} proving that cannot be done.
 *
 * <p>Jobs are independent of each other (resources are not yet held exclusively), so each job is a bipartite matching
 * of its demands onto resources, by augmenting paths. When a demand cannot be matched, the search that failed is the
 * evidence: the demands it visited are eligible only for the resources it visited, every one of which is taken by
 * another demand it visited, so there is one resource fewer than demands (Hall's condition, failing).
 *
 * <p>As with {@link Solver}, nothing is returned untested: the assignment or the shortage goes through the {@link
 * Checker} first, and one that fails is a bug here, thrown as {@link IllegalStateException}.
 */
public final class Assigner {

    private Assigner() {
    }

    public static Assignment assign(Plan plan) {
        int resources = plan.resources().size();
        int[] resourceOf = new int[plan.demands().size()];
        int[] holder = new int[resources];  // within the current job: the demand holding each resource, or -1
        Arrays.fill(holder, -1);

        for (int j = 0; j < plan.jobs().size(); j++) {
            List<Integer> demands = plan.jobs().get(j).demands();
            for (int d : demands) {
                boolean[] seenResource = new boolean[resources];
                List<Integer> visited = new ArrayList<>();
                if (!augment(plan, d, holder, resourceOf, seenResource, visited)) {
                    int[] shortDemands = visited.stream().mapToInt(Integer::intValue).toArray();
                    int[] eligible = new int[resources];
                    int n = 0;
                    for (int r = 0; r < resources; r++) {
                        if (seenResource[r]) eligible[n++] = r;
                    }
                    return lacking(plan, j, shortDemands, Arrays.copyOf(eligible, n));
                }
            }
            for (int d : demands) {
                holder[resourceOf[d]] = -1;
            }
        }
        if (!Checker.assignment(plan, resourceOf)) {
            throw new IllegalStateException("the assigner's assignment failed the checker");
        }
        return Assignment.of(resourceOf);
    }

    /**
     * Finds {@code d} a resource, taking one from another demand of the job if that demand can move. Every demand the
     * search reaches goes in {@code visited}, every resource it tries in {@code seenResource}.
     */
    private static boolean augment(Plan plan, int d, int[] holder, int[] resourceOf, boolean[] seenResource,
            List<Integer> visited) {
        visited.add(d);
        for (int r = 0; r < holder.length; r++) {
            if (seenResource[r] || !plan.eligible(d, r)) {
                continue;
            }
            seenResource[r] = true;
            if (holder[r] < 0 || augment(plan, holder[r], holder, resourceOf, seenResource, visited)) {
                holder[r] = d;
                resourceOf[d] = r;
                return true;
            }
        }
        return false;
    }

    /** The last gate before a shortage leaves the assigner. Package-private so a test can hand it a bad one. */
    static Assignment lacking(Plan plan, int job, int[] demands, int[] resources) {
        if (!Checker.shortage(plan, job, demands, resources)) {
            throw new IllegalStateException("the assigner's shortage failed the checker");
        }
        return Assignment.lacking(new Assignment.Shortage(plan, job, demands, resources));
    }
}
