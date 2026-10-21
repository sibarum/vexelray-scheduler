package dev.vexelray.scheduler.solver;

import java.util.Set;

/**
 * A rule over a demand's tags and a resource's tags, deciding which resources a demand may take. The Java form of
 * {@code SchedulerProofs.Rule}, whose {@code excludes} is {@link #excludes}; tags are strings here and numbers there,
 * which only changes how two are compared.
 *
 * @param demandTag a demand carrying this tag is what the rule is about. A demand carries its job's tags and its own.
 * @param resourceTag the tag the rule looks for on the resource
 */
public record Rule(Kind kind, String demandTag, String resourceTag) {

    public enum Kind {
        /** A demand tagged {@code demandTag} may not take a resource tagged {@code resourceTag}. */
        FORBID {
            @Override
            boolean excludes(boolean demandHas, boolean resourceHas) {
                return demandHas && resourceHas;
            }
        },
        /** A demand tagged {@code demandTag} may only take a resource tagged {@code resourceTag}. */
        REQUIRE {
            @Override
            boolean excludes(boolean demandHas, boolean resourceHas) {
                return demandHas && !resourceHas;
            }
        };

        abstract boolean excludes(boolean demandHas, boolean resourceHas);
    }

    public static Rule forbid(String demandTag, String resourceTag) {
        return new Rule(Kind.FORBID, demandTag, resourceTag);
    }

    public static Rule require(String demandTag, String resourceTag) {
        return new Rule(Kind.REQUIRE, demandTag, resourceTag);
    }

    /** This rule rules out a demand with {@code demandTags} taking a resource with {@code resourceTags}. */
    public boolean excludes(Set<String> demandTags, Set<String> resourceTags) {
        return kind.excludes(demandTags.contains(demandTag), resourceTags.contains(resourceTag));
    }
}
