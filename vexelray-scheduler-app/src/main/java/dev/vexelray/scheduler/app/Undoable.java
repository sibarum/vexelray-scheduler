package dev.vexelray.scheduler.app;

import dev.vexelray.gui.core.edit.Change;
import dev.vexelray.scheduler.app.Messages.Edit;
import dev.vexelray.scheduler.app.Messages.Kind;
import sibarum.atchung.Sender;

/**
 * A change to the schedule, in the window's undo history: applied, it asks {@link Revise} to make it ({@code APPLY},
 * {@code REDO}) or unmake it ({@code UNDO}), and returns the entry that asks the reverse. The history's ruled pattern
 * (Vexplore's {@code Journaled}, the IDE's {@code Moving}): its entries send messages, and what a change did stays the
 * {@link Doc}'s.
 *
 * <p>Since 0.2 every window has a history, bound by the framework to Ctrl+Z, Ctrl+Shift+Z and Ctrl+Y, so the
 * scheduler's own changes go into it rather than claiming Ctrl+Z beside it: an application chord on Ctrl+Z is
 * outranked by the window's, and never fires.
 *
 * @param kind {@code APPLY}, {@code UNDO} or {@code REDO}
 */
record Undoable(Kind kind, Sender<Edit> edits) implements Change {

    @Override
    public Change apply() {
        edits.send(Edit.of(kind));
        return new Undoable(kind == Kind.UNDO ? Kind.REDO : Kind.UNDO, edits);
    }
}
