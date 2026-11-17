package dev.vexelray.scheduler.app;

import dev.vexelray.gui.core.Node;

/**
 * How anything on screen changes: never in one frame.
 *
 * <h2>The rule</h2>
 *
 * <p><b>Things fade in and out; everything else slides to make room.</b> Since 0.2 nearly all of that is the
 * framework's, given to every window it builds with nothing to call:
 *
 * <ul>
 *   <li>a <b>colour</b> changes (a bar picked out, a day chip, a card chosen) and it is blended;</li>
 *   <li>a node's <b>words</b> are replaced (a headline, a status slot, a button's label) and the old ones fade out
 *       under the new ones fading in. This was this application's own {@code Motion.Label} until the framework took
 *       it (FN-2);</li>
 *   <li>a node <b>moves</b> (a bar to another clinician's lane, a card pushed down by one arriving) and it slides
 *       from where it was drawn;</li>
 *   <li>a node <b>arrives</b>, appended or inserted, and it comes up from nothing, rising a little;</li>
 *   <li>a node <b>goes</b> ({@code remove()}) and it fades where it stands before its neighbours close up;</li>
 *   <li>the <b>zoom</b> steps, and it glides ({@code Gui.zoomGlide}). This was this application's own glide until
 *       the framework took it (FN-6).</li>
 * </ul>
 *
 * <p>What is left is here: a whole region's contents are replaced, and a {@link Stage} swaps them so the framework
 * sees a replacement (the old page lifted out where it stands and faded, then the new one brought in).
 *
 * <p>What this rules out is as important. Nothing here animates a <em>size</em>: a box growing every frame moves
 * everything after it every frame, and the slides would chase each of those moves, so two clocks would pull the
 * picture apart. A region makes room by the thing arriving taking its place at once, invisible, and the things after
 * it sliding: one clock. And nothing scrolls by itself: a scrollbar appears in a single frame and narrows what it
 * scrolls, so no region whose contents change scrolls while the window is at a size the design is for.
 */
final class Motion {

    private Motion() {
    }

    /**
     * A region whose contents are replaced whole: the day's lanes, the briefing's page. Two pages of a schedule are
     * mostly the same words in slightly different places, and seen through each other they are a smear, so they must
     * go one after the other.
     */
    static Stage stage(Node region) {
        return new Stage(region);
    }

    static final class Stage {

        private final Node region;
        private Node current;
        private String key;

        private Stage(Node region) {
            this.region = region;
        }

        /** Whether {@code key} is what is on stage now: then it is to be changed in place, not replaced. */
        boolean showing(String key) {
            return key.equals(this.key);
        }

        /**
         * Put {@code page} on stage under {@code key}, in place of what was there. The new page is appended and the
         * old one removed in the same batch, which is what the framework reads as a replacement: the old one is lifted
         * out of the flow where it stands and fades, and the new one comes up once it has gone. A page never scrolls
         * itself.
         */
        void show(String key, Node page) {
            Node old = current;
            this.key = key;
            this.current = page;
            region.append(page.scroll(false, false));
            if (old != null) {
                old.remove();
            }
        }
    }
}
