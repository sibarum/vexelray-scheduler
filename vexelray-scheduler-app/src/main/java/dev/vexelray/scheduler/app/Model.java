package dev.vexelray.scheduler.app;

import dev.vexelray.scheduler.app.visits.Sample;
import dev.vexelray.scheduler.app.visits.Team;
import sibarum.atchung.Committer;
import sibarum.atchung.State;

import java.util.function.Consumer;
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
 * <h2>Handlers run on workers; this is the serialisation point</h2>
 *
 * <p>Nothing else in this application needs a lock, and if something starts to, that is the signal that state has
 * escaped this class.
 */
final class Model {

    private final State<Doc> state;
    private final Committer<Doc, UnaryOperator<Doc>> edit;

    /** The sample team, on its today. */
    Model() {
        this(Sample.north(), Sample.TODAY, Sample.NOW);
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
     * React to every change, in order, one at a time, always finishing on the newest.
     *
     * <p>{@code State.onCommit} delivers on the committing thread <em>after</em> its compare-and-set, and handlers
     * commit from a pool — so two finishing together reach a listener as version 6 then 5, and a listener that
     * redraws from the value would end on a stale one. {@code onCommitLatest} serialises delivery and drops a
     * snapshot older than the last delivered. It does so by making a committer wait, so never commit here while
     * holding a lock the listener also takes.
     */
    void onChange(Consumer<Doc> listener) {
        state.onCommitLatest(v -> listener.accept(v.value()));
    }

    /** The version counter — what a test waits on to know a change landed. */
    long version() {
        return state.version();
    }
}
