package dev.vexelray.scheduler.app;

import dev.vexelray.canvas.Color;
import dev.vexelray.gui.core.Gui;
import dev.vexelray.gui.core.Node;
import dev.vexelray.gui.core.layout.LayoutEnums.AlignItems;
import dev.vexelray.gui.core.layout.LayoutEnums.Direction;
import dev.vexelray.gui.core.layout.Length;
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
 * that says a day is covered when it is not is the application lying — in a clinic, about compliance.
 *
 * <p>{@code show} is called from the committing thread, which is a worker. That is correct and is the framework's
 * own idiom: a prop written off the GUI thread is queued and applied by the next drain, and {@link Gui#batch} keeps a
 * rebuild from being drawn half done. What is <em>not</em> allowed is the other direction — reading the model from
 * inside the frame loop.
 */
final class Ui {

    private final Gui gui;
    private final Model model;
    private final TitleBar titleBar;
    private final StatusBar status;
    private final Node team;
    private final Node days;
    private final Node date;
    private final List<Node> dayNodes = new ArrayList<>();
    private final Schedule schedule;
    private final Briefing briefing;

    Ui(Gui gui, Model model, TitleBar titleBar) {
        this.gui = gui;
        this.model = model;
        this.titleBar = titleBar;

        team = words(gui, "", Type.BODY, Look.INK).bold();
        days = gui.row().gap(Type.TIGHT).padding(Length.dp(3)).border(Length.dp(1), Look.RULE).corner(Type.ROUND);
        date = words(gui, "", Type.MICRO, Look.FAINT).font(Type.MONO);
        Node header = gui.row().width(Length.FILL).height(Length.rem(3.1f)).alignItems(AlignItems.CENTER)
                .gap(Type.INSET).padding(Length.ZERO, Length.dp(16)).background(Look.CHROME)
                .children(team, gui.box().width(Length.grow(1f)), days, date);

        schedule = new Schedule(gui, model);
        briefing = new Briefing(gui, model);
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
                .background(Look.THEME.color(dev.vexelray.gui.core.style.Role.PAGE))
                .children(titleBar.node(), header, rule(gui, true), body, status.node());
    }

    /** Write everything derived from the state. One method taking the whole value, so no two parts can disagree. */
    void show(Doc doc) {
        Review.Day review = doc.review();
        Team t = review.team();
        gui.batch(() -> {
            titleBar.title(t.name() + " · " + t.weekday(doc.day()) + " — " + Scheduler.TITLE);
            team.text(t.name());
            showDays(doc, t);
            date.text(t.days().get(doc.day()));
            schedule.show(doc, review);
            briefing.show(doc, review);
            int problems = review.problems().size();
            status.text(Landmarks.STATUS_COUNT, review.checks().size() + " visits");
            status.text(Landmarks.STATUS_STATE, problems == 0
                    ? "all covered · all within credentials"
                    : (review.checks().size() - problems) + " covered · " + problems + " need"
                            + (problems == 1 ? "s" : "") + " you");
            status.text(Landmarks.STATUS_WHERE, t.name() + " · " + t.days().get(doc.day()));
        });
    }

    /** The day chips: one per day, the one shown filled, a day with something to decide marked amber. */
    private void showDays(Doc doc, Team t) {
        clear(dayNodes);
        for (int d = 0; d < t.days().size(); d++) {
            int day = d;
            boolean shown = d == doc.day();
            boolean attention = !Review.of(t, d, doc.today()).problems().isEmpty();
            String name = (d == doc.today() ? "Today" : t.weekday(d).substring(0, 3));
            Node chip = gui.row().alignItems(AlignItems.CENTER).gap(Length.dp(6)).padding(Length.dp(5), Length.dp(12))
                    .corner(Length.dp(6)).background(shown ? Look.BLUE_PICKED : Color.TRANSPARENT).role("button");
            if (attention) {
                chip.append(gui.box().size(Length.dp(3), Length.rem(0.7f)).corner(Length.dp(2)).background(Look.AMBER));
            }
            chip.append(words(gui, name, Type.SMALL, shown ? Look.INK : Look.DIM).wordWrap(false).hitInert(true));
            gui.onClick(chip, () -> model.day(day));
            gui.landmark(Landmarks.day(t.days().get(d)), chip);
            days.append(chip);
            dayNodes.add(chip);
        }
    }

    // ---------------------------------------------------------------------------------------------- shared shapes

    /** A run of text: wraps, unless told not to. */
    static Node words(Gui gui, String text, Length size, Color color) {
        return gui.text(text).font(Type.UI).textSize(size).textColor(color);
    }

    /** A section's small capitals: "RIGHT NOW". */
    static Node caps(Gui gui, String text) {
        return gui.text(text.toUpperCase()).font(Type.MONO).textSize(Type.MICRO).textColor(Look.FAINT)
                .wordWrap(false);
    }

    /** A one-pixel rule between two regions: across a column, or down a row. */
    static Node rule(Gui gui, boolean across) {
        return across
                ? gui.box().width(Length.FILL).height(Length.dp(1)).background(Look.RULE)
                : gui.box().width(Length.dp(1)).height(Length.FILL).background(Look.RULE);
    }

    /** Remove every node in {@code nodes}, and forget them. */
    static void clear(List<Node> nodes) {
        for (Node n : nodes) {
            n.remove();
        }
        nodes.clear();
    }
}
