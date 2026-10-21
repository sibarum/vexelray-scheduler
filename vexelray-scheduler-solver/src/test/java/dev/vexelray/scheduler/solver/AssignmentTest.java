package dev.vexelray.scheduler.solver;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.junit.jupiter.api.Test;

/** Small plans worked out by hand, the checker's refusals, and the assigner's last gate. */
class AssignmentTest {

    @Test
    void aPurpleJobDoesNotGoOnTheYellowResource() {
        Plan.Builder b = new Plan.Builder(100);
        int job = b.job(5, "purple");
        int yellow = b.resource("yellow");
        int blue = b.resource("blue");
        b.forbid("purple", "yellow");
        Plan plan = b.build();

        int[] resources = plan.solve().assignment().resources().orElseThrow();
        assertEquals(blue, resources[plan.jobs().get(job).demands().get(0)]);
        assertNotEquals(yellow, resources[0]);
    }

    @Test
    void aPurpleJobWithOnlyYellowResourcesIsAShortageNamingTheRule() {
        Plan.Builder b = new Plan.Builder(100);
        int job = b.job(5, "purple");
        int yellow = b.resource("yellow");
        b.forbid("purple", "yellow");
        Plan plan = b.build();

        Assignment.Shortage shortage = plan.solve().assignment().shortage().orElseThrow();
        assertEquals(job, shortage.job());
        assertArrayEquals(new int[] {0}, shortage.demands());
        assertArrayEquals(new int[0], shortage.resources());
        assertEquals(List.of(Rule.forbid("purple", "yellow")), shortage.reasons(0, yellow));
    }

    @Test
    void requireSendsAJobOnlyToResourcesWithTheTag() {
        Plan.Builder b = new Plan.Builder(100);
        b.job(5, "fragile");
        b.job(5);
        b.resource("bare");
        int padded = b.resource("padded");
        b.require("fragile", "padded");
        Plan plan = b.build();

        int[] resources = plan.solve().assignment().resources().orElseThrow();
        assertEquals(padded, resources[0], "the fragile job");
        assertTrue(Checker.assignment(plan, new int[] {padded, 0}), "an untagged job may take either");
        assertFalse(Checker.assignment(plan, new int[] {0, 0}), "the fragile job on the bare resource");
    }

    @Test
    void aJobsDemandsTakeDifferentResources() {
        Plan.Builder b = new Plan.Builder(100);
        int job = b.job(5);
        int second = b.demand(job, "operator");
        b.resource("machine");
        b.resource("person");
        b.require("operator", "person");
        Plan plan = b.build();

        int[] resources = plan.solve().assignment().resources().orElseThrow();
        assertEquals(0, resources[0], "the machine, the only resource left for the first demand");
        assertEquals(1, resources[second]);
        assertFalse(Checker.assignment(plan, new int[] {1, 1}), "both demands on one resource");
    }

    @Test
    void twoDemandsAndOneEligibleResourceIsAShortageOfBoth() {
        Plan.Builder b = new Plan.Builder(100);
        int job = b.job(5, "heavy");
        b.demand(job);
        b.resource("crane");
        b.resource("trolley");
        b.require("heavy", "crane");
        Plan plan = b.build();

        Assignment.Shortage shortage = plan.solve().assignment().shortage().orElseThrow();
        assertEquals(2, shortage.demands().length);
        assertArrayEquals(new int[] {0}, shortage.resources());
    }

    @Test
    void aDemandCarriesItsJobsTagsAndItsOwn() {
        Plan.Builder b = new Plan.Builder(100);
        int job = b.job(5, "purple");
        int second = b.demand(job, "night");
        Plan plan = b.build();
        assertEquals(java.util.Set.of("purple"), plan.demands().get(0).tags());
        assertEquals(java.util.Set.of("purple", "night"), plan.demands().get(second).tags());
    }

    @Test
    void jobsAreIntervalsOfTheNetwork() {
        Plan.Builder b = new Plan.Builder(100);
        int first = b.job(10);
        int second = b.job(20);
        b.after(first, second, 5);
        b.release(first, 3);
        Plan plan = b.build();

        Solution.Schedule s = plan.solve().timing().schedule().orElseThrow();
        assertEquals(3, s.start(plan.jobs().get(first).start()));
        assertEquals(13, s.start(plan.jobs().get(first).end()));
        assertEquals(18, s.start(plan.jobs().get(second).start()));
        assertEquals(38, s.start(plan.jobs().get(second).end()));
        assertEquals(new Span(18, 80), s.window(plan.jobs().get(second).start()));
    }

    @Test
    void aPlanWorksOnlyWhenTheTimingAndTheAssignmentBothDo() {
        Plan.Builder late = new Plan.Builder(100);
        int job = late.job(10);
        late.deadline(job, 5);
        late.resource();
        assertFalse(late.build().solve().feasible(), "assignable, but too long for its deadline");

        Plan.Builder fits = new Plan.Builder(100);
        fits.job(10);
        fits.resource();
        assertTrue(fits.build().solve().feasible());
    }

    @Test
    void theShortageCheckerRefusesWhatIsNotAShortage() {
        Plan.Builder b = new Plan.Builder(100);
        int job = b.job(5);
        int d1 = b.demand(job);
        int other = b.job(5);
        b.resource();
        b.resource();
        Plan plan = b.build();
        int d0 = 0;
        int otherDemand = plan.jobs().get(other).demands().get(0);

        assertFalse(Checker.shortage(plan, job, new int[] {d0, d1}, new int[] {0}), "resource 1 is eligible too");
        assertFalse(Checker.shortage(plan, job, new int[] {d0, d1}, new int[] {0, 1}), "two resources, two demands");
        assertFalse(Checker.shortage(plan, job, new int[] {d0, d0}, new int[] {}), "a demand twice");
        assertFalse(Checker.shortage(plan, job, new int[] {d0, otherDemand}, new int[] {0}), "another job's demand");
        assertFalse(Checker.shortage(plan, 7, new int[] {d0}, new int[] {}), "no such job");
        assertFalse(Checker.shortage(plan, job, new int[] {}, new int[] {}), "no demands at all");
    }

    @Test
    void theAssignmentCheckerRefusesMissingAndUnknownResources() {
        Plan.Builder b = new Plan.Builder(100);
        b.job(5);
        b.resource();
        Plan plan = b.build();
        assertTrue(Checker.assignment(plan, new int[] {0}));
        assertFalse(Checker.assignment(plan, new int[] {1}), "no such resource");
        assertFalse(Checker.assignment(plan, new int[] {-1}), "no such resource");
        assertFalse(Checker.assignment(plan, new int[] {}), "a demand without one");
    }

    @Test
    void theAssignersLastGateRefusesAShortageTheCheckerRefuses() {
        Plan.Builder b = new Plan.Builder(100);
        b.job(5);
        b.resource();
        Plan plan = b.build();
        assertThrows(IllegalStateException.class, () -> Assigner.lacking(plan, 0, new int[] {0}, new int[] {}));
    }
}
