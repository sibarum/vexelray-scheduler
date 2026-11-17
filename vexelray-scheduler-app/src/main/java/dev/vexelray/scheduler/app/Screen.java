package dev.vexelray.scheduler.app;

import dev.vexelray.framework.api.Subscribe;
import dev.vexelray.gui.core.Gui;
import dev.vexelray.gui.core.input.Shortcut;
import dev.vexelray.gui.keymap.Keymap;
import dev.vexelray.gui.widget.Button;
import dev.vexelray.scheduler.app.Messages.Edit;
import dev.vexelray.scheduler.app.Messages.Kind;
import dev.vexelray.scheduler.app.Messages.Seen;
import dev.vexelray.scheduler.app.Messages.Zoom;
import sibarum.atchung.Sender;
import sibarum.tactroller.api.Key;
import sibarum.tactroller.api.Modifier;

/**
 * The view: what the person did, heard as messages in the frame, and what the week now looks like, drawn. A
 * {@code @View}, so it runs on the GUI thread, in each frame's widget tick (vexelray-framework's
 * {@code docs/threading.md}, T7.4).
 *
 * <h2>Why there is one</h2>
 *
 * <p>Since 0.2 a widget says what happened to it on a topic rather than calling code it was handed (T7.2): a button's
 * press is {@code Button.PRESSED}, carrying its address. And a chord names the message it sends, through a
 * {@link Sender} only the container hands out (T7.5). So the {@link Ui} still builds and draws the tree, and this is
 * where its messages come in. A click on a bar, a day or a card is heard by the {@code Ui} itself
 * ({@code gui.hear}), which sends an {@link Edit} with the sender handed to it here.
 *
 * <h2>The review is a job</h2>
 *
 * <p>Every edit goes to {@link Revise} on the job lane, because reviewing the week is the solver's work and longer
 * than a frame. Its answer, {@link Seen}, is drawn here, so every widget write happens on the GUI thread.
 */
@dev.vexelray.framework.api.View
final class Screen {

    private final Gui gui;
    private final Ui ui;
    private final Sender<Edit> edits;

    Screen(Gui gui, Keymap keys, Ui ui, Sender<Edit> edits, Sender<Zoom> zooms) {
        this.gui = gui;
        this.ui = ui;
        this.edits = edits;
        ui.declare(edits);
        commands(keys, edits, zooms);
    }

    /**
     * Escape puts the picked-out visit back; Ctrl+= / Ctrl+- / Ctrl+0 zoom. Declared, so the title bar's key button
     * lists them and the person can rebind them; the id is what a rebind is stored under, so it is the part to keep
     * stable. <b>How far the zoom goes is not decided here</b>: that is {@code Appearance.ZoomRange}, and the zoom's
     * glide is the framework's. Ctrl+Z is not here either: it is the window's history's, which a change made here is
     * recorded in ({@link Undoable}).
     */
    private static void commands(Keymap keys, Sender<Edit> edits, Sender<Zoom> zooms) {
        keys.action("visit.put-back", "Put the visit back", Shortcut.of(Key.ESCAPE))
                .sends(edits, Edit.of(Kind.DESELECT));
        keys.action("view.zoom-in", "Zoom in", Shortcut.of(Key.EQUAL, Modifier.CONTROL),
                Shortcut.of(Key.NUMPAD_ADD, Modifier.CONTROL)).sends(zooms, new Zoom(1));
        keys.action("view.zoom-out", "Zoom out", Shortcut.of(Key.MINUS, Modifier.CONTROL),
                Shortcut.of(Key.NUMPAD_SUBTRACT, Modifier.CONTROL)).sends(zooms, new Zoom(-1));
        keys.action("view.zoom-reset", "Reset zoom", Shortcut.of(Key.DIGIT_0, Modifier.CONTROL),
                Shortcut.of(Key.NUMPAD_0, Modifier.CONTROL)).sends(zooms, new Zoom(0));
    }

    /** An edit made and the week reviewed: draw it, unless a newer answer is already drawn. */
    @Subscribe
    public void seen(Seen m) {
        ui.show(m);
    }

    /**
     * A button on the briefing: Put it back, the fix's confirmation, Not now, Undo. Making a fix is performed through
     * the window's history, so Ctrl+Z undoes it; the Undo button undoes as Ctrl+Z does, so the two never disagree.
     */
    @Subscribe
    public void pressed(Button.Pressed m) {
        Edit edit = ui.pressed(m.button());
        if (edit == null) {
            return;
        }
        if (edit.kind() == Kind.APPLY) {
            gui.history().perform(new Undoable(Kind.APPLY, edits));
        } else if (edit.kind() == Kind.UNDO) {
            gui.undo(gui.history());
        } else {
            edits.send(edit);
        }
    }

    @Subscribe
    public void zoom(Zoom m) {
        if (m.direction() > 0) {
            gui.zoomIn();
        } else if (m.direction() < 0) {
            gui.zoomOut();
        } else {
            gui.resetZoom();
        }
    }
}
