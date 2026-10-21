package dev.vexelray.scheduler.app;

/**
 * Every name an automation script may write down, in one place.
 *
 * <p><b>A ref is minted per run; a landmark still means something tomorrow.</b> That is the whole distinction
 * (vexelray-gui/docs/automation.md 5), and it is why these are constants rather than string literals scattered
 * through the view: a landmark is a published contract, so renaming one breaks a script somebody else wrote, and
 * the compiler should be the thing that notices.
 *
 * <p>Where you can, carry <em>state in the accessible name</em>. {@link #HEADLINE} is the briefing's first sentence,
 * so {@code await headline covered} waits for a day with nothing to do and {@code await headline needs} for one
 * with something.
 */
final class Landmarks {

    /** The briefing's headline: "Everything today is covered.", "1 visit needs you." */
    static final String HEADLINE = "headline";

    /** The confirm and cancel buttons under the fix being considered. */
    static final String CONFIRM = "confirm";
    static final String NOT_NOW = "notnow";

    /** Undo the last change. */
    static final String UNDO = "undo";

    /** The panel explaining the selected visit, and its close button. */
    static final String DETAIL = "detail";
    static final String DETAIL_CLOSE = "detail.close";

    /** The status line, and its slots. */
    static final String STATUS = "status";
    static final String STATUS_COUNT = "status.count";
    static final String STATUS_STATE = "status.state";
    static final String STATUS_WHERE = "status.where";

    /** A day's tab, by its heading's first word: {@code day.thu}. */
    static String day(String heading) {
        return "day." + heading.substring(0, 3).toLowerCase();
    }

    /** A visit's bar on the schedule, by the patient's surname: {@code visit.okafor}. */
    static String visit(String label) {
        return "visit." + label.toLowerCase();
    }

    /** A way to fix the focused visit, counted from 1: {@code fix.1}. */
    static String fix(int index) {
        return "fix." + (index + 1);
    }

    private Landmarks() {
    }
}
