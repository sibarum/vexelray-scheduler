package dev.vexelray.scheduler.app;

import dev.vexelray.gui.core.Gui;
import dev.vexelray.gui.core.Node;
import dev.vexelray.gui.core.layout.Length;
import dev.vexelray.gui.krono.Fades;
import dev.vexelray.gui.krono.KronoGui;
import dev.vexelray.gui.krono.Transitions;
import sibarum.kronometer.Dur;
import sibarum.kronometer.anim.Ease;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;
import java.util.function.DoubleConsumer;

/**
 * How anything on screen changes: never in one frame.
 *
 * <h2>The rule</h2>
 *
 * <p><b>Things fade in and out; everything else slides to make room.</b> Every change the window makes is one of
 * five, and each has its instrument here:
 *
 * <ul>
 *   <li>a <b>colour</b> changes — a bar picked out, a day chip, a card chosen — and {@link Fades} blends it, for
 *       every node, with nothing to call;</li>
 *   <li>a node <b>moves</b> — a bar to another clinician's lane, a card pushed down by one arriving — and
 *       {@link Transitions} slides it from where it was drawn, for every node under a {@link #follow}ed root;</li>
 *   <li>a node <b>arrives</b>, and {@link #appear} brings it up from nothing, rising a little as it comes;</li>
 *   <li>a node <b>goes</b>, and {@link #leave} takes it down to nothing before removing it, so what was under it
 *       is uncovered rather than revealed;</li>
 *   <li>a label's <b>words</b> change, and a {@link Label} dips out, changes, and comes back.</li>
 * </ul>
 *
 * <p>And a whole region whose contents are replaced — the day's lanes, the briefing — is a {@link Stage}: the old
 * contents lifted out of the flow where they stand and faded out over the new ones fading in.
 *
 * <p>What this rules out is as important. Nothing here animates a <em>size</em> inside a followed region: a box
 * growing every frame moves everything after it every frame, and {@link Transitions} would chase each of those
 * moves with a lag of its own, so the two clocks would pull the picture apart. A region makes room by the thing
 * arriving taking its place at once, invisible, and the things after it sliding — one clock. And nothing scrolls by
 * itself: a scrollbar appears in a single frame and narrows what it scrolls, so no region whose contents change
 * scrolls while the window is at a size the design is for.
 *
 * <h2>Two tempos</h2>
 *
 * <p>A {@link #FADE} is short and linear: a colour has nowhere to arrive, and feedback that lags reads as a slow
 * application. A {@link #CHANGE} — a slide, an arrival — is longer and eased out, so it lands rather than stops.
 *
 * <h2>Slow motion</h2>
 *
 * <p>{@code -Dscheduler.slowmo=10} runs every transition ten times slower: what lets a photograph taken by
 * {@code ottermate} catch a change in the middle, and a run of them show that no step between two frames is large.
 * It is read once, at start; nothing else reads a property.
 */
final class Motion {

    /** What a colour change, an arrival's fade or a departure is worth. */
    static final Dur FADE = Dur.ms(140);

    /** What a slide is worth: a node moved, or making room. */
    static final Dur CHANGE = Dur.ms(200);

    /** How far an arriving node rises into place, in ems. */
    private static final float RISE = 0.35f;

    private final KronoGui krono;
    private final Transitions moves;
    private final double slow;

    /** Every node's opacity that something here is driving, by node id, so a departure starts from where an
     * arrival had got to rather than from fully there. */
    private final Map<Long, Glide> faders = new ConcurrentHashMap<>();

    Motion(Gui gui, KronoGui krono) {
        this.krono = krono;
        this.slow = Math.max(1d, Double.parseDouble(System.getProperty("scheduler.slowmo", "1")));
        this.moves = Transitions.on(krono);
        gui.paintMotion(Fades.on(krono, scaled(FADE), Ease.LINEAR));
        gui.motion(moves);
        // The focus ring's fade and the wheel's glide are the framework's, set for every window as it opens
        // (TactrollerInputBackend.pointerFeel): nothing to do here, and slow motion does not reach them.
    }

    /** Everything under {@code root}, at any depth, slides to where layout puts it. */
    void follow(Node root) {
        moves.followSubtree(root, scaled(CHANGE), Ease.OUT_CUBIC);
    }

    /** Bring {@code node} up from nothing, rising into place. Call in the batch that adds it. */
    Node appear(Node node) {
        node.opacity(0f).translate(0f, RISE);
        Glide g = new Glide(0f, v -> node.opacity((float) v).translate(0f, (float) (RISE * (1d - v))),
                FADE, Ease.OUT_CUBIC);
        faders.put(node.id(), g);
        g.to(1f, () -> faders.remove(node.id(), g));
        return node;
    }

    /** Take {@code node} down to nothing, then out of the tree. It stops taking the pointer at once. */
    void leave(Node node) {
        leave(node, () -> {
        });
    }

    /** {@link #leave}, and then {@code gone} once it has. */
    void leave(Node node, Runnable gone) {
        node.hitInert(true);
        Glide g = faders.remove(node.id());
        Glide out = new Glide(g == null ? 1f : g.value(), v -> node.opacity((float) v), FADE, Ease.LINEAR);
        if (g != null) {
            g.stop();
        }
        out.to(0f, () -> {
            node.remove();
            gone.run();
        });
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

        synchronized double value() {
            return value;
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

        /** Stop where it is: whatever ramp is running no longer writes. */
        synchronized void stop() {
            epoch++;
            target = value;
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
     * A region whose contents are replaced whole: the old lifted out where they stand and faded out, and then the
     * new faded in. One after the other rather than over each other, because two pages of a schedule are mostly the
     * same words in slightly different places, and seen through each other they are a smear.
     */
    Stage stage(Node region) {
        return new Stage(region);
    }

    final class Stage {

        private final Node region;
        private volatile Node current;
        private String key;

        private Stage(Node region) {
            this.region = region;
        }

        /** Whether {@code key} is what is on stage now: then it is to be changed in place, not replaced. */
        boolean showing(String key) {
            return key.equals(this.key);
        }

        /**
         * Put {@code page} on stage under {@code key}. The first page simply appears there, since the window is
         * being drawn for the first time; after that the old one floats where it is and fades out, and the new one,
         * already in the flow beneath it but unseen, comes up once it has gone — unless another page has taken the
         * stage meanwhile, in which case it is that one's turn. The region must have no padding, so that where the old page floats is
         * where it already was; and it must be at least as tall as the old page, since a float is clamped inside
         * its parent and a squeezed page grows a scrollbar — which is why a stage fills what its parent leaves.
         * A page never scrolls itself.
         */
        void show(String key, Node page) {
            Node old = current;
            this.key = key;
            this.current = page;
            region.append(page.scroll(false, false));
            if (old != null) {
                old.floatAt(Length.ZERO, Length.ZERO).width(Length.percent(100));
                // Unseen, and known to be: if it is replaced before its turn, it leaves from nothing.
                page.opacity(0f);
                faders.put(page.id(), new Glide(0f, v -> page.opacity((float) v), FADE, Ease.LINEAR));
                leave(old, () -> {
                    if (current == page) {
                        appear(page);
                    }
                });
            }
        }
    }
}
