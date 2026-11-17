package dev.vexelray.scheduler.app;

import dev.vexelray.scheduler.app.visits.Review;
import sibarum.atchung.Topic;

import java.util.ArrayList;
import java.util.List;

/**
 * Every message this application's parts send each other, each record beside its topic.
 *
 * <p>One round trip. What the person did (a bar clicked, a fix chosen, Ctrl+Z) is an {@link Edit}, which
 * {@link Revise} makes on the job lane: reviewing a week is the solver's work, ten or more milliseconds of it, and the
 * view's time is the frame's. The job answers with what the window should now say, {@link Seen}, and the
 * {@link Screen} draws it. The zoom chords are a {@link Zoom}, which needs no job, since the zoom is the window's.
 * Since 0.2 a click, a button or a chord sends a message rather than running code (vexelray-framework's
 * {@code docs/threading.md}, T7.2 and T7.5).
 */
final class Messages {

    private Messages() {
    }

    /**
     * Change the schedule's state: {@code kind}, with the day or fix it names ({@code n}) and the visit it names
     * ({@code visit}), where the kind takes one.
     */
    record Edit(Kind kind, int n, String visit) {
        static final Topic<Edit> TOPIC = Topic.edge(Edit.class);

        static Edit day(int day) {
            return new Edit(Kind.DAY, day, "");
        }

        static Edit toggle(String visit) {
            return new Edit(Kind.TOGGLE, 0, visit);
        }

        static Edit choose(int fix) {
            return new Edit(Kind.CHOOSE, fix, "");
        }

        static Edit of(Kind kind) {
            return new Edit(kind, 0, "");
        }

        /** What this edit does to the state: a function of whatever the state turns out to be. */
        Doc on(Doc doc) {
            return kind.on(doc, this);
        }
    }

    /** Each kind of edit is a method, so there is no switch to keep in step with this list. */
    enum Kind {
        /** Show another day. */
        DAY {
            @Override
            Doc on(Doc doc, Edit e) {
                return doc.withDay(e.n());
            }
        },
        /** Pick out a visit, or put it back. */
        TOGGLE {
            @Override
            Doc on(Doc doc, Edit e) {
                return doc.toggled(e.visit());
            }
        },
        /** Put the picked-out visit back. */
        DESELECT {
            @Override
            Doc on(Doc doc, Edit e) {
                return doc.deselected();
            }
        },
        /** Consider a fix, or none ({@link Doc#NONE}); nothing changes until {@link #APPLY}. */
        CHOOSE {
            @Override
            Doc on(Doc doc, Edit e) {
                return doc.withChosen(e.n());
            }
        },
        /** Make the fix being considered. */
        APPLY {
            @Override
            Doc on(Doc doc, Edit e) {
                return doc.applied();
            }
        },
        /** Unmake the last change. */
        UNDO {
            @Override
            Doc on(Doc doc, Edit e) {
                return doc.undone();
            }
        },
        /** Make the last change undone again. */
        REDO {
            @Override
            Doc on(Doc doc, Edit e) {
                return doc.redone();
            }
        };

        abstract Doc on(Doc doc, Edit e);
    }

    /**
     * The state at {@code version}, reviewed: everything the window shows, worked out off the frame. {@code attention}
     * says, per day, whether it has a visit that needs someone (the day chips' amber marks); {@code now} is who is
     * where right now, when the day shown is today.
     */
    record Seen(long version, Doc doc, Review.Day review, List<Boolean> attention, List<Review.Now> now) {
        static final Topic<Seen> TOPIC = Topic.edge(Seen.class);

        Seen {
            attention = List.copyOf(attention);
            now = List.copyOf(now);
        }

        /** Review {@code doc}: the solver's work, so never in the frame. */
        static Seen of(long version, Doc doc) {
            List<Boolean> attention = new ArrayList<>();
            for (int d = 0; d < doc.team().days().size(); d++) {
                attention.add(!Review.of(doc.team(), d, doc.today()).problems().isEmpty());
            }
            List<Review.Now> now = doc.day() == doc.today()
                    ? Review.now(doc.team(), doc.day(), doc.now())
                    : List.of();
            return new Seen(version, doc, doc.review(), attention, now);
        }
    }

    /** A zoom chord: in ({@code 1}), out ({@code -1}), or back to where it started ({@code 0}). */
    record Zoom(int direction) {
        static final Topic<Zoom> TOPIC = Topic.edge(Zoom.class);
    }
}
