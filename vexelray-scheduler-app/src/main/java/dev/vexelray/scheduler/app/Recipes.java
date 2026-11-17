package dev.vexelray.scheduler.app;

import dev.vexelray.framework.api.Configuration;
import dev.vexelray.framework.api.Provides;
import dev.vexelray.framework.shell.Appearance;
import dev.vexelray.gui.core.Gui;
import dev.vexelray.gui.core.layout.Length;
import dev.vexelray.gui.widget.TitleBar;

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
 * <h2>Where the rest comes from</h2>
 *
 * <p>Not every part is a recipe. The {@link Screen} is a {@code @View}: run on the GUI thread in each frame, built
 * from its one constructor, reached by messages, and it declares the chords. {@link Revise} is a {@code @Job}: each
 * edit is made, and the week reviewed, on the job lane. The automation sockets come from {@code AutomationStarter},
 * which {@code SchedulerApp} names in its {@code @VexelApp} (see {@link Scheduler}).
 *
 * <h2>What is not here is the point</h2>
 *
 * <p>Opening an input backend, installing a clipboard, remembering where the window was, attaching a clock, wiring
 * the frame loop with its wakes and its pacing, the window's motion, parsing the command line, installing the
 * dialogs, and closing everything in the right order are all {@code vexelray-framework}'s.
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
     * <p>The first drawing is done here, reviewed here, since nothing has been edited yet and no frame has run; every
     * later one is the {@link Screen}'s, when {@link Revise} answers.
     */
    @Provides
    Ui ui(Gui gui, Model model, TitleBar titleBar) {
        Ui ui = new Ui(gui, titleBar);
        ui.show(model.seen());
        return ui;
    }
}
