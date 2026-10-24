package dev.vexelray.scheduler.app;

import dev.vexelray.gui.core.Node;
import dev.vexelray.gui.krono.KronoGui;
import sibarum.kronometer.Dur;
import sibarum.kronometer.anim.Ease;

import java.util.function.Consumer;
import java.util.function.DoubleConsumer;

/**
 * How anything on screen changes: never in one frame.
 *
 * <h2>The rule</h2>
 *
 * <p><b>Things fade in and out; everything else slides to make room.</b> Most of that is the framework's, given to
 * every window it builds with nothing to call:
 *
 * <ul>
 *   <li>a <b>colour</b> changes — a bar picked out, a day chip, a card chosen — and it is blended;</li>
 *   <li>a node <b>moves</b> — a bar to another clinician's lane, a card pushed down by one arriving — and it slides
 *       from where it was drawn;</li>
 *   <li>a node <b>arrives</b> — appended or inserted — and it comes up from nothing, rising a little;</li>
 *   <li>a node <b>goes</b> — {@code remove()} — and it fades where it stands before its neighbours close up.</li>
 * </ul>
 *
 * <p>What is left is here: a label's <b>words</b> change, and a {@link Label} dips out, changes, and comes back; a
 * whole region's contents are replaced, and a {@link Stage} swaps them so the framework sees a replacement (the old
 * page lifted out where it stands and faded, then the new one brought in); and a {@link Glide}, which the zoom keys
 * drive.
 *
 * <p>What this rules out is as important. Nothing here animates a <em>size</em>: a box growing every frame moves
 * everything after it every frame, and the slides would chase each of those moves, so two clocks would pull the
 * picture apart. A region makes room by the thing arriving taking its place at once, invisible, and the things after
 * it sliding — one clock. And nothing scrolls by itself: a scrollbar appears in a single frame and narrows what it
 * scrolls, so no region whose contents change scrolls while the window is at a size the design is for.
 *
 * <h2>Two tempos</h2>
 *
 * <p>A {@link #FADE} is short and linear: words have nowhere to arrive, and feedback that lags reads as a slow
 * application. A {@link #CHANGE} — the zoom — is longer and eased out, so it lands rather than stops.
 *
 * <h2>Slow motion</h2>
 *
 * <p>{@code -Dscheduler.slowmo=10} runs what is here ten times slower: what lets a photograph taken by
 * {@code ottermate} catch a label or the zoom in the middle. The framework's own motion keeps real time. It is read
 * once, at start; nothing else reads a property.
 */
final class Motion {

    /** What a label's dip is worth. */
    static final Dur FADE = Dur.ms(140);

    /** What the zoom's glide is worth. */
    static final Dur CHANGE = Dur.ms(200);

    private final KronoGui krono;
    private final double slow;

    Motion(KronoGui krono) {
        this.krono = krono;
        this.slow = Math.max(1d, Double.parseDouble(System.getProperty("scheduler.slowmo", "1")));
    }

    /** A number that glides to each target it is given, writing itself on the way. */
    Glide glide(double initial, DoubleConsumer write, Dur over, Ease ease) {
        return new Glide(initial, write, over, ease);
    }

    Dur scaled(Dur d) {
        return slow == 1d ? d : d.times(slow);
    }

    // ------------------------------------------------------------------------------------------------ glide

    /**
     * A number that moves to each new target from wherever it is, writing itself on the way.
     *
     * <p>Interrupted, it turns round where it is: a new target starts from the value last written, not from the
     * old start or the old target, so there is no frame in which it is somewhere it was not a frame before. A
     * {@code done} runs only if the glide it was given to arrives; one overtaken by a newer target is dropped.
     */
    final class Glide {

        private final DoubleConsumer write;
        private final Dur over;
        private final Ease ease;
        private double value;
        private double target;
        private int epoch;

        Glide(double initial, DoubleConsumer write, Dur over, Ease ease) {
            this.write = write;
            this.over = over;
            this.ease = ease;
            this.value = initial;
            this.target = initial;
        }

        /** Where it is heading: where it is, once it has arrived. */
        synchronized double target() {
            return target;
        }

        /** Head for {@code to}; {@code done} when it gets there. Heading there already, the first done stands. */
        synchronized void to(double to, Runnable done) {
            if (to == target && epoch > 0) {
                return;
            }
            target = to;
            double from = value;
            int e = ++epoch;
            if (from == to) {
                done.run();
                return;
            }
            krono.ramp(scaled(over), ease, p -> step(e, from + (to - from) * p), () -> {
                if (current(e)) {
                    done.run();
                }
            });
        }

        void to(double to) {
            to(to, () -> {
            });
        }

        /**
         * At rest, take {@code actual} as where it is: for a value something else can also set, or clamp, between
         * glides. Mid-glide this does nothing, since the glide is what is setting it.
         */
        synchronized void resting(double actual) {
            if (value == target) {
                value = actual;
                target = actual;
            }
        }

        private synchronized void step(int e, double v) {
            if (e == epoch) {
                value = v;
                write.accept(v);
            }
        }

        private synchronized boolean current(int e) {
            return e == epoch;
        }
    }

    // ------------------------------------------------------------------------------------------------ label

    /** A label whose words change by dipping out and coming back, never in one frame. */
    Label label(Node fade, Consumer<String> write) {
        return new Label(fade, write);
    }

    final class Label {

        private final Node fade;
        private final Consumer<String> write;
        private final Glide opacity;
        private String text;
        private String shown;

        private Label(Node fade, Consumer<String> write) {
            this.fade = fade;
            this.write = write;
            this.opacity = new Glide(1f, v -> fade.opacity((float) v), FADE, Ease.LINEAR);
        }

        /** Say {@code value}. The first time at once, since there was nothing before it to change from. */
        synchronized void set(String value) {
            if (value.equals(text)) {
                return;
            }
            text = value;
            if (shown == null) {
                shown = value;
                write.accept(value);
                return;
            }
            if (value.equals(shown)) {
                opacity.to(1f);   // changed back before the old words had gone: just come back up
                return;
            }
            opacity.to(0f, this::swap);
        }

        private synchronized void swap() {
            shown = text;
            write.accept(text);
            opacity.to(1f);
        }

        Node node() {
            return fade;
        }
    }

    // ------------------------------------------------------------------------------------------------ stage

    /**
     * A region whose contents are replaced whole: the day's lanes, the briefing's page. Two pages of a schedule are
     * mostly the same words in slightly different places, and seen through each other they are a smear, so they must
     * go one after the other.
     */
    Stage stage(Node region) {
        return new Stage(region);
    }

    final class Stage {

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
