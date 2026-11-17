package dev.vexelray.scheduler.app;

import dev.vexelray.canvas.Color;
import dev.vexelray.gui.core.Gui;
import dev.vexelray.gui.core.Node;
import dev.vexelray.gui.core.NodeEvent;
import dev.vexelray.gui.core.layout.LayoutEnums.AlignItems;
import dev.vexelray.gui.core.layout.LayoutEnums.Direction;
import dev.vexelray.gui.core.layout.Length;
import dev.vexelray.gui.core.style.Role;
import dev.vexelray.gui.widget.StatusBar;
import dev.vexelray.gui.widget.TitleBar;
import dev.vexelray.scheduler.app.Messages.Edit;
import dev.vexelray.scheduler.app.Messages.Seen;
import dev.vexelray.scheduler.app.visits.Review;
import dev.vexelray.scheduler.app.visits.Team;
import sibarum.atchung.Sender;

import java.util.ArrayList;
import java.util.List;

/**
 * The window: the framework's title bar; a header with the team and its days; the {@link Schedule} beside the
 * {@link Briefing}; and a status line under them.
 *
 * <h2>It holds no state</h2>
 *
 * <p>{@link #show} takes a whole {@link Seen}, the state and its review, and writes everything derived from it. That
 * is the rule worth keeping as this grows: a label that remembers a value can disagree with the schedule, and a
 * status line that says a day is covered when it is not is the application lying, in a clinic, about compliance. What
 * the parts here do keep is their nodes, so that a change is something a node <em>does</em> (fades, slides) rather
 * than a node replaced by another in one frame. {@link Motion} says how.
 *
 * <h2>It runs on the GUI thread</h2>
 *
 * <p>{@code show} is called by the {@link Screen}, in the frame, when a {@link Seen} arrives, and once while the tree
 * is built. A click is heard in the frame too ({@code gui.hear}), and all it does is send an {@link Edit}: the edit
 * and its review are {@link Revise}'s, on the job lane, because reviewing a week takes longer than a frame.
 */
final class Ui {

    private final Gui gui;
    private final TitleBar titleBar;
    private final Node team;
    private final Node days;
    private final Node date;
    private final List<Chip> chips = new ArrayList<>();
    private final StatusBar status;
    private final Schedule schedule;
    private final Briefing briefing;
    private Sender<Edit> edits;
    private long shown = -1;

    /** A day's chip: the box, the mark that says it has something to decide, and its name. */
    private record Chip(Node box, Node mark, Node name) {
    }

    Ui(Gui gui, TitleBar titleBar) {
        this.gui = gui;
        this.titleBar = titleBar;

        team = words(gui, "", Type.BODY, Look.INK).bold().wordWrap(false);
        days = gui.row().gap(Type.TIGHT).padding(Length.dp(3)).border(Length.dp(1), Look.RULE).corner(Type.ROUND);
        date = gui.text("").font(Type.MONO).textSize(Type.MICRO).textColor(Look.FAINT).wordWrap(false);
        Node header = gui.row().width(Length.FILL).height(Length.rem(3.1f)).alignItems(AlignItems.CENTER)
                .gap(Type.INSET).padding(Length.ZERO, Length.dp(16)).background(Look.CHROME)
                .children(team, gui.box().width(Length.grow(1f)), days, date);

        schedule = new Schedule(gui, this::send);
        briefing = new Briefing(gui, this::send);
        Node body = gui.row().width(Length.FILL).height(Length.grow(1f))
                .children(schedule.node(), rule(gui, false), briefing.node());

        status = new StatusBar(gui)
                .slot(Landmarks.STATUS_COUNT, StatusBar.Side.LEFT, "")
                .slot(Landmarks.STATUS_STATE, StatusBar.Side.LEFT, "")
                .slot(Landmarks.STATUS_WHERE, StatusBar.Side.RIGHT, "");
        gui.landmark(Landmarks.STATUS, status.node());
        for (String slot : new String[] {Landmarks.STATUS_COUNT, Landmarks.STATUS_STATE, Landmarks.STATUS_WHERE}) {
            gui.landmark(slot, status.slot(slot));
        }

        gui.root().direction(Direction.COLUMN)
                .background(Look.THEME.color(Role.PAGE))
                .children(titleBar.node(), header, rule(gui, true), body, status.node());
    }

    /** Where what the person does goes: handed over by the {@link Screen}, the part that is given a sender. */
    void declare(Sender<Edit> edits) {
        this.edits = edits;
    }

    /** Ask for {@code edit}. Before the screen exists there are no frames, so no click to lose. */
    private void send(Edit edit) {
        if (edits != null) {
            edits.send(edit);
        }
    }

    /** What a button's press asks for, if the button is one of this window's; null if it is not. */
    Edit pressed(String button) {
        return briefing.pressed(button);
    }

    /**
     * Write everything derived from the state. One method taking the whole value, so no two parts can disagree. An
     * answer older than the one on screen is dropped: two edits' reviews may come back in either order.
     */
    void show(Seen seen) {
        if (seen.version() <= shown) {
            return;
        }
        shown = seen.version();
        Doc doc = seen.doc();
        Review.Day review = seen.review();
        Team t = review.team();
        gui.batch(() -> {
            // The title names the team and nothing that changes as the day does: the title bar is the framework's.
            titleBar.title(t.name() + " — " + Scheduler.TITLE);
            team.text(t.name());
            showDays(seen, t);
            date.text(t.heading(doc.day()));
            schedule.show(doc, review);
            briefing.show(seen);
            int problems = review.problems().size();
            status.text(Landmarks.STATUS_COUNT, review.checks().size() + " visits");
            status.text(Landmarks.STATUS_STATE, problems == 0
                    ? "all covered · all within credentials"
                    : (review.checks().size() - problems) + " covered · " + problems + " need"
                            + (problems == 1 ? "s" : "") + " you");
            status.text(Landmarks.STATUS_WHERE, t.name() + " · " + t.heading(doc.day()));
        });
    }

    /**
     * The day chips: one per day, made once, the one shown filled, a day with something to decide marked amber. A
     * chip only ever changes colour, so the paint motion fades it.
     */
    private void showDays(Seen seen, Team t) {
        Doc doc = seen.doc();
        while (chips.size() < t.days().size()) {
            int day = chips.size();
            String name = day == doc.today() ? "Today" : t.weekday(day).substring(0, 3);
            Node mark = gui.box().size(Length.dp(3), Length.rem(0.7f)).corner(Length.dp(2))
                    .background(Color.TRANSPARENT);
            Node label = words(gui, name, Type.SMALL, Look.DIM).wordWrap(false).hitInert(true);
            Node box = gui.row().alignItems(AlignItems.CENTER).gap(Length.dp(6)).padding(Length.dp(5), Length.dp(12))
                    .corner(Length.dp(6)).background(Color.TRANSPARENT).role("button").children(mark, label);
            gui.hear(box, NodeEvent.CLICK, click -> send(Edit.day(day)));
            gui.landmark(Landmarks.day(t.heading(day)), box);
            days.append(box);
            chips.add(new Chip(box, mark, label));
        }
        for (int d = 0; d < chips.size(); d++) {
            Chip chip = chips.get(d);
            boolean showing = d == doc.day();
            boolean attention = d < seen.attention().size() && seen.attention().get(d);
            chip.box().background(showing ? Look.BLUE_PICKED : Color.TRANSPARENT);
            chip.name().textColor(showing ? Look.INK : Look.DIM);
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
