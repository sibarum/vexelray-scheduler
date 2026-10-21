package dev.vexelray.scheduler.app.visits;

/**
 * Time a working clinician is not free for visits: a clinic shift, training, a meeting.
 *
 * @param what how a sentence says it: "at the Pediatrics clinic"
 */
public record Away(String clinician, int day, int start, int end, String what) {
}
