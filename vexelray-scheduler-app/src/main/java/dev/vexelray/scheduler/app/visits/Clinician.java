package dev.vexelray.scheduler.app.visits;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Someone who makes visits.
 *
 * @param name        "Dana Reyes"
 * @param credentials what the schedule shows under the name, in order: "RN", "WOUND", "ES"
 * @param tags        what the solver matches a visit's needs against; a superset of what the credentials mean
 * @param daysOff     the days, by index into {@link Team#days}, this clinician does not work
 */
public record Clinician(String name, List<String> credentials, Set<String> tags, Set<Integer> daysOff) {

    public Clinician {
        credentials = List.copyOf(credentials);
        tags = Set.copyOf(tags);
        daysOff = Set.copyOf(daysOff);
    }

    /** The name a sentence uses: "Dana". */
    public String first() {
        int space = name.indexOf(' ');
        return space < 0 ? name : name.substring(0, space);
    }

    public boolean works(int day) {
        return !daysOff.contains(day);
    }

    public boolean has(Need need) {
        return tags.contains(need.tag());
    }

    Clinician working(int day) {
        Set<Integer> off = new HashSet<>(daysOff);
        off.remove(day);
        return new Clinician(name, credentials, tags, off);
    }
}
