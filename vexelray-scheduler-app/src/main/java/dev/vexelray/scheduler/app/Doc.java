package dev.vexelray.scheduler.app;

import dev.vexelray.scheduler.app.visits.Review;
import dev.vexelray.scheduler.app.visits.Team;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Everything the application knows, in one immutable value: the schedule, the day being looked at, the visit picked
 * out on it, the fix being considered, and the changes made so far, each with the schedule as it was before.
 *
 * <h2>Why a record, and why one</h2>
 *
 * <p>A snapshot rather than a set of fields somebody mutates. The window reads this from the committing thread while
 * handlers write it from workers, and a record handed over by a single reference swap is the whole synchronisation
 * story: there is no instant at which a reader sees half an edit. Add fields here rather than adding state
 * elsewhere.
 *
 * <p>What the day <em>means</em> (covered or not, and why) is not held here. It is {@link #review}, a function of
 * the schedule, so it can never disagree with it.
 *
 * @param selected the visit picked out, by id; empty when none is
 * @param chosen   the fix being considered, by index into the focused visit's fixes; {@link #NONE} when none is
 * @param history  every change made, oldest first
 */
record Doc(Team team, int day, int today, int now, Optional<String> selected, int chosen, List<Change> history) {

    /** No fix is being considered. */
    static final int NONE = -1;

    /** A change that was made, and the schedule it replaced. */
    record Change(String what, Team before) {
    }

    Doc {
        history = List.copyOf(history);
    }

    /** {@code team}, looked at on {@code today}, at minute {@code now}. */
    static Doc of(Team team, int today, int now) {
        return new Doc(team, today, today, now, Optional.empty(), NONE, List.of());
    }

    /** The day, reviewed. */
    Review.Day review() {
        return Review.of(team, day, today);
    }

    /**
     * The visit the briefing is about: the selected one, if it needs someone, or else the first that does. Empty
     * when the day is covered.
     */
    Optional<Review.Check> focus(Review.Day review) {
        Optional<Review.Check> picked = selected.flatMap(review::check).filter(c -> !c.covered());
        return picked.isPresent() ? picked : review.problems().stream().findFirst();
    }

    /** The fix being considered, if any. */
    Optional<Review.Fix> fix(Review.Day review) {
        return focus(review).filter(c -> chosen >= 0 && chosen < c.fixes().size()).map(c -> c.fixes().get(chosen));
    }

    Doc withDay(int value) {
        return new Doc(team, value, today, now, Optional.empty(), NONE, history);
    }

    /** Pick out {@code id}, or put it back if it was already picked out. */
    Doc toggled(String id) {
        Optional<String> next = selected.filter(id::equals).isPresent() ? Optional.empty() : Optional.of(id);
        return new Doc(team, day, today, now, next, NONE, history);
    }

    Doc deselected() {
        return new Doc(team, day, today, now, Optional.empty(), chosen, history);
    }

    Doc withChosen(int value) {
        return new Doc(team, day, today, now, selected, value, history);
    }

    /** The fix being considered, made: the schedule replaced, and the old one kept to undo to. */
    Doc applied() {
        Optional<Review.Fix> fix = fix(review());
        if (fix.isEmpty()) {
            return this;
        }
        List<Change> next = new ArrayList<>(history);
        next.add(new Change(fix.get().change(), team));
        return new Doc(fix.get().after(), day, today, now, Optional.empty(), NONE, next);
    }

    /** The last change, unmade. */
    Doc undone() {
        if (history.isEmpty()) {
            return this;
        }
        Change last = history.getLast();
        return new Doc(last.before(), day, today, now, Optional.empty(), NONE, history.subList(0, history.size() - 1));
    }
}
