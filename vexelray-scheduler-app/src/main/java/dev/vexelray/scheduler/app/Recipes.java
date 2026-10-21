package dev.vexelray.scheduler.app;

import dev.vexelray.framework.api.Configuration;
import dev.vexelray.framework.api.Provides;
import dev.vexelray.framework.shell.Appearance;
import dev.vexelray.gui.core.Gui;
import dev.vexelray.gui.core.layout.Length;
import dev.vexelray.gui.widget.TitleBar;
import sibarum.tactroller.api.Key;
import sibarum.tactroller.api.Modifier;

/**
 * What this application builds — one recipe per part — and nothing about when.
 *
 * <h2>The wiring is generated from this</h2>
 *
 * <p>{@code SchedulerAppWiring} is written by {@code vexelray-framework-processor} while this project compiles, and
 * lands in {@code target/generated-sources/annotations}. It calls each method below once, in the phase its
 * parameters put it in: <b>a part's phase is the latest phase of anything it takes.</b> So the look and the model
 * exist before the {@code Gui} does, and the window's contents wait for the {@code Gui} and its title bar. There is
 * no phase to declare and none to get wrong. Open the generated file after a build to see the order.
 *
 * <p>The automation sockets are not here. They come from {@code AutomationStarter}, which {@code SchedulerApp}
 * names in its {@code @VexelApp} (see {@link Scheduler}).
 *
 * <h2>What is not here is the point</h2>
 *
 * <p>Opening an input backend, installing a clipboard, remembering where the window was, attaching a clock, wiring
 * the frame loop with its wakes and its pacing, parsing the command line, installing the dialogs, and closing
 * everything in the right order are all {@code vexelray-framework}'s.
 */
@Configuration
final class Recipes {

    /**
     * The look, and the smallest window this UI is still coherent in. Built from nothing the framework makes, so it
     * exists in the first phase and the framework applies it the moment it creates the {@code Gui}.
     */
    @Provides
    Appearance look() {
        return Appearance.of(Look.THEME, Length.rem(Scheduler.MIN_W_EM), Length.rem(Scheduler.MIN_H_EM));
    }

    /** The one authoritative state — the sample team, for now — built before the tree because the tree draws it. */
    @Provides
    Model model() {
        return new Model();
    }

    /**
     * The window's contents. Needs the {@code Gui} and its title bar, and no window — so a test can build the real
     * tree headless ({@code VexelApplication.tree}), which {@code WindowTest} does.
     *
     * <p>Every change to the state redraws what is derived from it, on the committing thread — a worker, because
     * every handler is. The first drawing is done here, since nothing has been committed yet.
     */
    @Provides
    Ui ui(Gui gui, Model model, TitleBar titleBar) {
        Ui ui = new Ui(gui, model, titleBar);
        model.onChange(ui::show);
        ui.show(model.doc());
        shortcuts(gui, model);
        return ui;
    }

    /**
     * Ctrl+Z undoes the last change; Escape puts the picked-out visit back; Ctrl+= / Ctrl+- / Ctrl+0 zoom.
     *
     * <p>An application decision, which is why it is here. <b>How far the zoom goes is not</b>: that is
     * {@code Appearance.ZoomRange}, applied by the framework before the first widget.
     */
    private static void shortcuts(Gui gui, Model model) {
        gui.shortcut(Key.Z, model::undo, Modifier.CONTROL);
        gui.shortcut(Key.ESCAPE, model::deselect);
        gui.shortcut(Key.EQUAL, gui::zoomIn, Modifier.CONTROL);
        gui.shortcut(Key.MINUS, gui::zoomOut, Modifier.CONTROL);
        gui.shortcut(Key.DIGIT_0, gui::resetZoom, Modifier.CONTROL);
        gui.shortcut(Key.NUMPAD_ADD, gui::zoomIn, Modifier.CONTROL);
        gui.shortcut(Key.NUMPAD_SUBTRACT, gui::zoomOut, Modifier.CONTROL);
        gui.shortcut(Key.NUMPAD_0, gui::resetZoom, Modifier.CONTROL);
    }
}
