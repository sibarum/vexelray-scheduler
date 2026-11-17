package dev.vexelray.scheduler.app;

import dev.vexelray.scheduler.app.visits.Sample;
import dev.vexelray.scheduler.app.visits.Team;
import sibarum.atchung.Committer;
import sibarum.atchung.State;
import sibarum.atchung.Versioned;

import java.time.LocalDateTime;

import java.util.function.UnaryOperator;

/**
 * The one authoritative {@link Doc}, and the only way to change it.
 *
 * <h2>Relative edits, not absolute writes</h2>
 *
 * <p>Every change here is a <em>function applied to whatever the current value turns out to be</em>, committed
 * through {@link State}. A click on a day and a fix being made on another worker both land, in some order, and
 * neither is lost. The alternative — read the value, compute a whole new one, write it back — produces something
 * perfectly coherent with one change missing whenever two handlers overlap, and nothing reports it.
 *
 * <h2>Edits are made on the job lane; this is the serialisation point</h2>
 *
 * <p>{@link Revise} makes each edit, and two may run at once. Nothing else in this application needs a lock, and if something starts to, that is the signal that state has
 * escaped this class.
 */
final class Model {

    private final State<Doc> state;
    private final Committer<Doc, UnaryOperator<Doc>> edit;

    /** The sample team, laid on this week, at the clock's today and now. */
    Model() {
        this(LocalDateTime.now());
    }

    /** The sample team, laid on the week of {@code at}, looked at then. */
    Model(LocalDateTime at) {
        this(Sample.north(at.toLocalDate()), at);
    }

    /** {@code team} looked at at {@code at}: today is the day it is, if the team's schedule covers it. */
    Model(Team team, LocalDateTime at) {
        this(team, team.day(at.toLocalDate()), at.getHour() * 60 + at.getMinute());
    }

    Model(Team team, int today, int now) {
        State.Builder<Doc> builder = State.of(Doc.of(team, today, now));
        // Declared before build, held as a handle: State refuses a committer it was not built with, which is
        // what stops an unrelated component minting its own way to write this.
        this.edit = builder.mutation("doc.edit", (current, change) -> change.apply(current));
        this.state = builder.build();
    }

    /** The latest coherent snapshot. Lock-free, safe from any thread. */
    Doc doc() {
        return state.value();
    }

    /** Apply a change to whatever the state currently is. */
    void change(UnaryOperator<Doc> change) {
        state.commit(edit, change);
    }

    void day(int day) {
        change(d -> d.withDay(day));
    }

    /** Pick out a visit, or put it back. */
    void toggle(String visit) {
        change(d -> d.toggled(visit));
    }

    void deselect() {
        change(Doc::deselected);
    }

    /** Consider fix {@code index}; nothing changes until {@link #apply}. */
    void choose(int index) {
        change(d -> d.withChosen(index));
    }

    void apply() {
        change(Doc::applied);
    }

    void undo() {
        change(Doc::undone);
    }

    /**
     * The state as it is now, with its version, reviewed. The solver's work, so it is called on the job lane
     * ({@link Revise}) or while the tree is built, and never in the frame.
     */
    Messages.Seen seen() {
        Versioned<Doc> current = state.current();
        return Messages.Seen.of(current.version(), current.value());
    }

    /** The version counter — what a test waits on to know a change landed. */
    long version() {
        return state.version();
    }
}
