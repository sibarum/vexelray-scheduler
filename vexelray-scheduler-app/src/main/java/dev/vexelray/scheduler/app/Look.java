package dev.vexelray.scheduler.app;

import dev.vexelray.canvas.Color;
import dev.vexelray.gui.core.style.Oklab;
import dev.vexelray.gui.core.style.Palette;
import dev.vexelray.gui.core.style.Relief;
import dev.vexelray.gui.core.style.Shading;
import dev.vexelray.gui.core.style.Theme;

/**
 * The palette, as anchors, and the colours of the design the construction does not make.
 *
 * <h2>Where the colours come from</h2>
 *
 * <p>The design is the "Scheduler Mockups" canvas: a cool near-black page, a neutral ink, and one hue, blue, at
 * about 222°. Blue rather than green, because in medical work green and red already mean something; amber is kept
 * for "this needs your decision", as it is across the suite. The anchors below are the design's own hexes read into
 * Oklab, so the framework's widgets (the title bar, a button) derive their shades from the same decisions.
 *
 * <p>The design also has a handful of surfaces and text shades that the {@link Palette}'s even ladder does not land
 * on exactly — the schedule's lanes, the bars, the amber of a visit that needs someone. Those are declared below at
 * their authored values rather than rounded to the nearest derived step, so the window can be checked against the
 * design by eye.
 */
final class Look {

    // ---------------------------------------------------------------- anchors

    /** The page: the colour behind everything, and what the frame clears to. */
    private static final Oklab PAGE = hex(0x0c0e14);

    /** Primary text. */
    private static final Oklab TEXT = hex(0xe9edf5);

    /** The one chromatic decision: the blue of "now", of a picked-out visit, of the button that does the thing. */
    private static final Oklab ACCENT = hex(0x7aa2ff);

    /** The fill of a filled control. */
    private static final Oklab ACTION = hex(0x3d5fb8);

    /** The accent's chroma taken round to red, so a confirmation dialog belongs to this palette. */
    private static final Oklab DANGER = Oklab.polar(0.5894, 0.1448, 18.40);

    /** Shadows: near-black, at the page's hue rather than a neutral grey. */
    private static final Oklab DEPTH = Oklab.polar(0.1236, 0.0130, -86.65);

    /** How far one surface is from the next. Two steps from the page is a panel. */
    private static final double STEP = 0.0271;

    /** How fast the ink fades towards the page: text(1) is DIM, text(2) is FAINT. */
    private static final double FADE = 0.202;

    /** How dark a shadow is. */
    private static final double SHADOW_ALPHA = 0.60;

    static final Palette PALETTE =
            new Palette(PAGE, STEP, TEXT, FADE, ACCENT, ACTION, DANGER, DEPTH, SHADOW_ALPHA);

    /** Lit surfaces on, letterpress off. */
    static final Theme THEME = Theme.of(PALETTE, Shading.ON_DARK, Relief.STANDARD, true, false);

    // ---------------------------------------------------------------- the design's own

    /** The header, the schedules list and the status line. */
    static final Color CHROME = Color.rgb(0x11141b);

    /** The schedule's ground. */
    static final Color LANES = Color.rgb(0x13161e);

    /** The briefing's ground. */
    static final Color BRIEFING = Color.rgb(0x151922);

    /** A card, a bar, a chip. */
    static final Color CARD = Color.rgb(0x1c2130);

    /** A rule between regions, and the outline of a quiet card. */
    static final Color RULE = Color.rgb(0x262c3a);

    /** The outline of a bar or a button. */
    static final Color EDGE = Color.rgb(0x343c4e);

    /** The line between two lanes. */
    static final Color SEAM = Color.rgb(0x1a1f2a);

    /** Primary text, as the widgets' own ink is. */
    static final Color INK = TEXT.toColor();

    /** Text a step down from the ink, and a step further. */
    static final Color DIM = Color.rgb(0xb3bbcb);
    static final Color SOFT = Color.rgb(0xd2d8e4);
    static final Color FAINT = Color.rgb(0x8b94a7);

    /** The accent, and the grounds it claims: a picked-out bar, a selected chip. */
    static final Color BLUE = ACCENT.toColor();
    static final Color BLUE_GROUND = Color.rgb(0x1b2540);
    static final Color BLUE_PICKED = Color.rgb(0x22305a);
    static final Color ON_BLUE = Color.rgb(0x0c0e14);

    /** A visit already over: sunk into the lane. */
    static final Color DONE = Color.rgb(0x161a24);
    static final Color DONE_EDGE = Color.rgb(0x222838);

    /** Needs your decision. */
    static final Color AMBER = Color.rgb(0xe8a85c);
    static final Color AMBER_GROUND = Color.rgb(0x2a2014);
    static final Color AMBER_LANE = Color.rgb(0x1a160f);
    static final Color AMBER_TEXT = Color.rgb(0xf3d9a8);
    static final Color AMBER_SOFT = Color.rgb(0xe3cfa9);

    /** Time away: a bar that is not a visit. */
    static final Color AWAY = Color.rgb(0x151821);
    static final Color AWAY_EDGE = Color.rgb(0x2c3343);

    /** Someone who could not take it: the cross beside them. */
    static final Color NO = Color.rgb(0x6b7489);

    private static Oklab hex(int rgb) {
        return Oklab.of(Color.rgb(rgb));
    }

    private Look() {
    }
}
