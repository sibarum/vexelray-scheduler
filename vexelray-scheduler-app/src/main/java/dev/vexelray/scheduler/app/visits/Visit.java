package dev.vexelray.scheduler.app.visits;

import java.util.List;
import java.util.Optional;

/**
 * One house visit: who it is for, when, what it needs, and who has it, if anyone yet.
 *
 * <p>Times are minutes after midnight on {@code day}. {@code earliest} and {@code due} are the window the visit may
 * be moved within, as absolute minutes from the start of the first day ({@link Team#at}); a visit with no say in the
 * matter has its own start and end as both.
 *
 * @param id        stable for the life of the visit
 * @param patient   "M. Okafor", "Mrs. Alvarez"
 * @param day       by index into {@link Team#days}
 * @param needs     what whoever takes it must have, in the order they are explained
 * @param clinician who has it, by name; empty when nobody does yet
 */
public record Visit(String id, String patient, String address, int day, int start, int end, List<Need> needs,
        Optional<String> clinician, int earliest, int due) {

    public Visit {
        needs = List.copyOf(needs);
    }

    public int length() {
        return end - start;
    }

    /** The kind of visit, from its first need that is a kind of care: "Wound care". */
    public String care() {
        for (Need need : needs) {
            if (need.care().isPresent()) {
                return need.care().get();
            }
        }
        return "Visit";
    }

    /** What a bar on the schedule says: the patient's surname. */
    public String label() {
        int space = patient.lastIndexOf(' ');
        return space < 0 ? patient : patient.substring(space + 1);
    }

    /** Taken by {@code name}. */
    Visit to(String name) {
        return new Visit(id, patient, address, day, start, end, needs, Optional.of(name), earliest, due);
    }

    /** At {@code start} on {@code day}, its length kept. */
    Visit at(int day, int start) {
        return new Visit(id, patient, address, day, start, start + length(), needs, clinician, earliest, due);
    }
}
