package dev.vexelray.scheduler.app.visits;

import java.util.List;
import java.util.Optional;

/**
 * Something a visit asks of whoever takes it, said three ways: as the visit asks it, as a clinician who has it, and
 * as one who does not. The {@code tag} is what the solver sees; the sentences are what a person reads.
 *
 * @param tag    the tag on the visit and on every clinician who has it
 * @param asked  what the visit needs, as a heading: "A nurse certified in wound care"
 * @param has    what a clinician who has it is: "is certified in wound care"
 * @param lacks  what one who does not is: "isn't certified in wound care"
 * @param care   the kind of visit this makes it ("Wound care"), or empty for a need that is not a kind of care
 */
public record Need(String tag, String asked, String has, String lacks, Optional<String> care) {

    public static final Need NURSE = care("NURSE", "A nurse", "is a nurse", "isn't a nurse", "Medication check");
    public static final Need WOUND = care("WOUND", "A nurse certified in wound care", "is certified in wound care",
            "isn't certified in wound care", "Wound care");
    public static final Need PEDS = care("PEDS", "A nurse certified in pediatrics", "is certified in pediatrics",
            "isn't certified in pediatrics", "Pediatric visit");
    public static final Need PT = care("PT", "A physical therapist", "is a physical therapist",
            "isn't a physical therapist", "Physical therapy");
    public static final Need OT = care("OT", "An occupational therapist", "is an occupational therapist",
            "isn't an occupational therapist", "Occupational therapy");
    public static final Need HHA = care("HHA", "A home health aide", "is a home health aide",
            "isn't a home health aide", "Personal care");
    public static final Need SPANISH = new Need("ES", "Someone who speaks Spanish", "speaks Spanish",
            "doesn't speak Spanish", Optional.empty());

    /** Every need, in the order a visit's are explained. */
    public static final List<Need> ALL = List.of(WOUND, PEDS, NURSE, PT, OT, HHA, SPANISH);

    private static Need care(String tag, String asked, String has, String lacks, String care) {
        return new Need(tag, asked, has, lacks, Optional.of(care));
    }

    /** The need with {@code tag}. */
    public static Need of(String tag) {
        for (Need need : ALL) {
            if (need.tag.equals(tag)) {
                return need;
            }
        }
        throw new IllegalArgumentException("no need tagged " + tag);
    }
}
