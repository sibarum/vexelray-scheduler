package dev.vexelray.scheduler.app.visits;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The sample week, reviewed: what the schedule says about it, sentence by sentence. */
class ReviewTest {

    private static final Team NORTH = Sample.north();
    private static final int TUE = 0;
    private static final int THU = 2;
    private static final int FRI = 3;

    @Test
    void tuesdayIsCovered() {
        Review.Day day = Review.of(NORTH, TUE, Sample.TODAY);
        assertEquals("Everything today is covered.", day.headline());
        assertTrue(day.detail().startsWith("29 visits, 7 clinicians working."), day.detail());
        assertTrue(day.problems().isEmpty());
        assertEquals(List.of("Thursday has a visit that needs you: Mrs. Alvarez's wound care at 2 pm.",
                "Priya is back on Friday."), day.later());
    }

    @Test
    void okaforExplainsWhyDana() {
        Review.Check okafor = check(Review.of(NORTH, TUE, Sample.TODAY), "M. Okafor");
        assertTrue(okafor.covered());
        assertEquals("Dana Reyes", okafor.clinician().orElseThrow().name());
        assertEquals(List.of(
                new Review.Reason("A nurse certified in wound care", "Dana is certified in wound care."),
                new Review.Reason("Someone who speaks Spanish", "Dana speaks Spanish."),
                new Review.Reason("There by 11:00",
                        "Dana's visit before ends at 10:30, and the drive is about 20 minutes.")),
                okafor.why());
        Review.Other priya = other(okafor, "Priya Nair");
        assertFalse(priya.could());
        assertEquals("Is certified in wound care, but doesn't speak Spanish and is off today.", priya.why());
        assertEquals("Speaks Spanish, but isn't certified in wound care and is with I. Pena at 10:15.",
                other(okafor, "Ana Torres").why());
    }

    @Test
    void thursdayNeedsYouForAlvarez() {
        Review.Day day = Review.of(NORTH, THU, Sample.TODAY);
        assertEquals("1 visit needs you.", day.headline());
        Review.Check alvarez = day.problems().get(0);
        assertEquals("Mrs. Alvarez's wound care visit at 2 pm has nobody who can take it.", alvarez.problem());
        assertEquals("It needs a nurse certified in wound care. Dana is at the Pediatrics clinic then and Priya is "
                + "off Thursday.", alvarez.explanation());
        assertEquals(List.of("Move it to Friday at 10 am", "Ask Priya to take it"),
                alvarez.fixes().stream().map(Review.Fix::title).toList());
    }

    @Test
    void movingToFridayCoversIt() {
        Review.Fix move = check(Review.of(NORTH, THU, Sample.TODAY), "Mrs. Alvarez").fixes().get(0);
        assertEquals("Alvarez · Thu 2 pm → Fri 10 am, Dana", move.change());
        assertTrue(Review.of(move.after(), THU, Sample.TODAY).problems().isEmpty());
        Review.Check moved = check(Review.of(move.after(), FRI, Sample.TODAY), "Mrs. Alvarez");
        assertTrue(moved.covered());
        assertEquals("Dana Reyes", moved.clinician().orElseThrow().name());
    }

    @Test
    void askingPriyaCoversIt() {
        Review.Fix ask = check(Review.of(NORTH, THU, Sample.TODAY), "Mrs. Alvarez").fixes().get(1);
        Review.Day after = Review.of(ask.after(), THU, Sample.TODAY);
        assertTrue(after.problems().isEmpty());
        assertEquals("Priya Nair", check(after, "Mrs. Alvarez").clinician().orElseThrow().name());
        assertTrue(after.detail().contains("8 clinicians working"), after.detail());
    }

    @Test
    void aClinicianWhoCannotGetThereNeedsYou() {
        // Hale moved to start ten minutes after Okafor ends: no time for the drive.
        String hale = NORTH.visits(TUE, "Dana Reyes").get(4).id();
        Team late = NORTH.move(hale, TUE, 11 * 60 + 55, "Dana Reyes");
        Review.Check check = check(Review.of(late, TUE, Sample.TODAY), "L. Hale");
        assertFalse(check.covered());
        assertEquals("Dana is with M. Okafor at 11:00.", check.explanation());
    }

    @Test
    void nowSaysWhoIsWhere() {
        List<Review.Now> now = Review.now(NORTH, TUE, Sample.NOW);
        assertEquals(new Review.Now("Dana is on the way to M. Okafor", "Wound care at 11:00"), now.get(0));
        assertEquals(new Review.Now("Sam is with R. Chen", "Medication check · until 11:15"), now.get(1));
    }

    @Test
    void timesAreSaidPlainly() {
        assertEquals("2 pm", Times.spoken(14 * 60));
        assertEquals("10:30 am", Times.spoken(10 * 60 + 30));
        assertEquals("noon", Times.spoken(12 * 60));
        assertEquals("12 pm", Times.hour(12));
        assertEquals("9:05", Times.clock(9 * 60 + 5));
    }

    private static Review.Check check(Review.Day day, String patient) {
        return day.checks().stream().filter(c -> c.visit().patient().equals(patient)).findFirst().orElseThrow();
    }

    private static Review.Other other(Review.Check check, String who) {
        return check.others().stream().filter(o -> o.who().equals(who)).findFirst().orElseThrow();
    }
}
