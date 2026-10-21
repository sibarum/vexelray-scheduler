package dev.vexelray.scheduler.app;

import dev.vexelray.canvas.Color;
import dev.vexelray.gui.core.Gui;
import dev.vexelray.gui.core.Node;
import dev.vexelray.gui.core.layout.LayoutEnums.AlignItems;
import dev.vexelray.gui.core.layout.LayoutEnums.Direction;
import dev.vexelray.gui.core.layout.Length;
import dev.vexelray.gui.core.style.Role;
import dev.vexelray.gui.widget.StatusBar;
import dev.vexelray.gui.widget.TitleBar;
import dev.vexelray.scheduler.app.visits.Review;
import dev.vexelray.scheduler.app.visits.Team;

import java.util.ArrayList;
import java.util.List;

/**
 * The window: the framework's title bar; a header with the team and its days; the {@link Schedule} beside the
 * {@link Briefing}; and a status line under them.
 *
 * <h2>It holds no state</h2>
 *
 * <p>{@link #show} takes a whole {@link Doc}, reviews it once, and writes everything derived from it. That is the
 * rule worth keeping as this grows: a label that remembers a value can disagree with the schedule, and a status line
 * that says a day is covered when it is not is the application lying — in a clinic, about compliance. What the
 * parts here do keep is their nodes, so that a change is something a node <em>does</em> — fades, slides — rather
 * than a node replaced by another in one frame. {@link Motion} says how.
 *
 * <p>{@code show} is called from the committing thread, which is a worker, one delivery at a time. That is correct
 * and is the framework's own idiom: a prop written off the GUI thread is queued and applied by the next drain, and
 * {@link Gui#batch} keeps a change from being drawn half made. What is <em>not</em> allowed is the other direction —
 * reading the model from inside the frame loop.
 */
final class Ui {

    private final Gui gui;
    private final Model model;
    private final Motion motion;
    private final TitleBar titleBar;
    private final Node team;
    private final Node days;
    private final Motion.Label date;
    private final List<Chip> chips = new ArrayList<>();
    private final Motion.Label count;
    private final Motion.Label state;
    private final Motion.Label where;
    private final Schedule schedule;
    private final Briefing briefing;

    /** A day's chip: the box, the mark that says it has something to decide, and its name. */
    private record Chip(Node box, Node mark, Node name) {
    }

    Ui(Gui gui, Model model, TitleBar titleBar, Motion motion) {
        this.gui = gui;
        this.model = model;
        this.motion = motion;
        this.titleBar = titleBar;

        team = words(gui, "", Type.BODY, Look.INK).bold().wordWrap(false);
        days = gui.row().gap(Type.TIGHT).padding(Length.dp(3)).border(Length.dp(1), Look.RULE).corner(Type.ROUND);
        Node dateNode = gui.text("").font(Type.MONO).textSize(Type.MICRO).textColor(Look.FAINT).wordWrap(false);
        date = motion.label(dateNode, dateNode::text);
        Node header = gui.row().width(Length.FILL).height(Length.rem(3.1f)).alignItems(AlignItems.CENTER)
                .gap(Type.INSET).padding(Length.ZERO, Length.dp(16)).background(Look.CHROME)
                .children(team, gui.box().width(Length.grow(1f)), days, dateNode);

        schedule = new Schedule(gui, model, motion);
        briefing = new Briefing(gui, model, motion);
        Node body = gui.row().width(Length.FILL).height(Length.grow(1f))
                .children(schedule.node(), rule(gui, false), briefing.node());

        StatusBar status = new StatusBar(gui)
                .slot(Landmarks.STATUS_COUNT, StatusBar.Side.LEFT, "")
                .slot(Landmarks.STATUS_STATE, StatusBar.Side.LEFT, "")
                .slot(Landmarks.STATUS_WHERE, StatusBar.Side.RIGHT, "");
        gui.landmark(Landmarks.STATUS, status.node());
        for (String slot : new String[] {Landmarks.STATUS_COUNT, Landmarks.STATUS_STATE, Landmarks.STATUS_WHERE}) {
            gui.landmark(slot, status.slot(slot));
        }
        count = motion.label(status.slot(Landmarks.STATUS_COUNT), s -> status.text(Landmarks.STATUS_COUNT, s));
        state = motion.label(status.slot(Landmarks.STATUS_STATE), s -> status.text(Landmarks.STATUS_STATE, s));
        where = motion.label(status.slot(Landmarks.STATUS_WHERE), s -> status.text(Landmarks.STATUS_WHERE, s));

        gui.root().direction(Direction.COLUMN)
                .background(Look.THEME.color(Role.PAGE))
                .children(titleBar.node(), header, rule(gui, true), body, status.node());
    }

    /** Write everything derived from the state. One method taking the whole value, so no two parts can disagree. */
    void show(Doc doc) {
        Review.Day review = doc.review();
        Team t = review.team();
        gui.batch(() -> {
            // The title names the team and nothing that changes as the day does: the title bar is the framework's,
            // and its words would change in one frame.
            titleBar.title(t.name() + " — " + Scheduler.TITLE);
            team.text(t.name());
            showDays(doc, t);
            date.set(t.days().get(doc.day()));
            schedule.show(doc, review);
            briefing.show(doc, review);
            int problems = review.problems().size();
            count.set(review.checks().size() + " visits");
            state.set(problems == 0
                    ? "all covered · all within credentials"
                    : (review.checks().size() - problems) + " covered · " + problems + " need"
                            + (problems == 1 ? "s" : "") + " you");
            where.set(t.name() + " · " + t.days().get(doc.day()));
        });
    }

    /**
     * The day chips: one per day, made once, the one shown filled, a day with something to decide marked amber. A
     * chip only ever changes colour, so the paint motion fades it.
     */
    private void showDays(Doc doc, Team t) {
        while (chips.size() < t.days().size()) {
            int day = chips.size();
            String name = day == doc.today() ? "Today" : t.weekday(day).substring(0, 3);
            Node mark = gui.box().size(Length.dp(3), Length.rem(0.7f)).corner(Length.dp(2))
                    .background(Color.TRANSPARENT);
            Node label = words(gui, name, Type.SMALL, Look.DIM).wordWrap(false).hitInert(true);
            Node box = gui.row().alignItems(AlignItems.CENTER).gap(Length.dp(6)).padding(Length.dp(5), Length.dp(12))
                    .corner(Length.dp(6)).background(Color.TRANSPARENT).role("button").children(mark, label);
            gui.onClick(box, () -> model.day(day));
            gui.landmark(Landmarks.day(t.days().get(day)), box);
            days.append(box);
            chips.add(new Chip(box, mark, label));
        }
        for (int d = 0; d < chips.size(); d++) {
            Chip chip = chips.get(d);
            boolean shown = d == doc.day();
            boolean attention = !Review.of(t, d, doc.today()).problems().isEmpty();
            chip.box().background(shown ? Look.BLUE_PICKED : Color.TRANSPARENT);
            chip.name().textColor(shown ? Look.INK : Look.DIM);
            chip.mark().background(attention ? Look.AMBER : Color.TRANSPARENT);
        }
    }

    // ---------------------------------------------------------------------------------------------- shared shapes

    /** A run of text: wraps, unless told not to. */
    static Node words(Gui gui, String text, Length size, Color color) {
        return gui.text(text).font(Type.UI).textSize(size).textColor(color);
    }

    /** A section's small capitals: "RIGHT NOW". */
    static Node caps(Gui gui, String text) {
        return caps(gui, text, Look.FAINT);
    }

    static Node caps(Gui gui, String text, Color color) {
        return gui.text(text.toUpperCase()).font(Type.MONO).textSize(Type.MICRO).textColor(color).wordWrap(false);
    }

    /** A one-pixel rule between two regions: across a column, or down a row. */
    static Node rule(Gui gui, boolean across) {
        return across
                ? gui.box().width(Length.FILL).height(Length.dp(1)).background(Look.RULE)
                : gui.box().width(Length.dp(1)).height(Length.FILL).background(Look.RULE);
    }

    /** A box that never grows a scrollbar: one would appear in a single frame. Overflow is cut instead. */
    static Node still(Node node) {
        return node.scroll(false, false).clip(true);
    }
}
