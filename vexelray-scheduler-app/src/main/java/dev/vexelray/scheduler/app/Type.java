package dev.vexelray.scheduler.app;

import dev.vexelray.gui.core.layout.Length;

/**
 * The two faces, the type scale, and the gutters.
 *
 * <h2>Type is rem; gutters are dp</h2>
 *
 * <p>That is the framework's rule and it decides what zoom does. {@code rem} grows with the user's zoom because it
 * is proportional to text; {@code dp} does not, because tripling the frame around content you zoomed in to read
 * means seeing less of it. Getting this backwards is not a subtle bug -- it is an application whose zoom makes the
 * window emptier. The schedule's rows and bars hold text, so they are rem too.
 *
 * <h2>The faces are the atlas's, not yours</h2>
 *
 * <p>The framework's text atlas is baked at build time from the fonts in {@code vexelray-text}, so face 0 and face
 * 1 are whatever it shipped -- a sans and a mono -- and so is which characters each has. The sans has General
 * Punctuation (the dashes) and the Arrows (a fix's "→"), and not Geometric Shapes: check the charset in
 * {@code vexelray-text}'s pom before reaching for a symbol.
 */
final class Type {

    /** Face 0: the sans. Everything a person reads. */
    static final int UI = 0;

    /** Face 1: the mono. Times, counts, small capitals over a section. */
    static final int MONO = 1;

    // ------------------------------------------------------------------ type

    /** The briefing's headline. */
    static final Length HEADLINE = Length.rem(1.25f);

    /** A day's name, a card's title. */
    static final Length TITLE = Length.rem(1.05f);

    /** Body text. */
    static final Length BODY = Length.rem(0.875f);

    /** A bar's label, a second line. */
    static final Length SMALL = Length.rem(0.78f);

    /** Times, credentials, the small capitals over a section. */
    static final Length MICRO = Length.rem(0.7f);

    // --------------------------------------------------------------- gutters

    /** A tight gap. */
    static final Length TIGHT = Length.dp(4);

    /** Between items in a group. */
    static final Length GAP = Length.dp(8);

    /** Inside a card. */
    static final Length INSET = Length.dp(12);

    /** Around a region. */
    static final Length MARGIN = Length.dp(20);

    /** A card's corners. */
    static final Length ROUND = Length.dp(8);

    private Type() {
    }
}
