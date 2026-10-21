package dev.vexelray.scheduler.app;

import dev.vexelray.canvas.Color;
import dev.vexelray.gui.core.Gui;
import dev.vexelray.gui.core.Node;
import dev.vexelray.gui.core.layout.LayoutEnums.AlignItems;
import dev.vexelray.gui.core.layout.LayoutEnums.Justify;
import dev.vexelray.gui.core.layout.Length;
import dev.vexelray.gui.icons.Icon;
import dev.vexelray.gui.icons.IconView;
import dev.vexelray.gui.widget.IconButton;
import dev.vexelray.scheduler.app.visits.Away;
import dev.vexelray.scheduler.app.visits.Clinician;
import dev.vexelray.scheduler.app.visits.Review;
import dev.vexelray.scheduler.app.visits.Team;
import dev.vexelray.scheduler.app.visits.Times;
import dev.vexelray.scheduler.app.visits.Visit;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * The day, one lane a clinician: a bar for every visit at its time, time away drawn quieter, a blue line at now.
 * A visit that needs someone is amber — in a lane of its own when nobody has it. Clicking a bar picks the visit out,
 * and the panel under the lanes says why its clinician has it and who else could.
 *
 * <p>Bars are floating children of their lane, placed by percentage of the day, so the lanes stretch with the window
 * and nothing is measured. Each is a real node with a click handler and a landmark ({@code visit.okafor}), so a
 * script clicks the same thing a person does. Rebuilt whole on every change: a day has a few dozen visits.
 */
final class Schedule {

    /** The hours the lanes show. */
    private static final int FIRST = 8 * 60;
    private static final int LAST = 17 * 60;

    /** The column of names in front of the lanes. */
    private static final Length NAMES = Length.rem(10.5f);

    /** A lane's height, and a bar's within it. */
    private static final Length LANE = Length.rem(3.3f);
    private static final Length BAR = Length.rem(2.1f);
    private static final Length BAR_TOP = Length.rem(0.6f);

    private final Gui gui;
    private final Model model;
    private final Node node;
    private final Node dayName;
    private final Node working;
    private final Node lanes;
    private final Node detail;
    private final List<Node> built = new ArrayList<>();
    private final List<Node> explained = new ArrayList<>();

    Schedule(Gui gui, Model model) {
        this.gui = gui;
        this.model = model;

        dayName = Ui.words(gui, "", Type.TITLE, Look.INK).bold().wordWrap(false);
        working = Ui.words(gui, "", Type.SMALL, Look.FAINT).wordWrap(false);
        Node heading = gui.row().width(Length.FILL).alignItems(AlignItems.END).gap(Type.INSET)
                .padding(Length.dp(14), Type.MARGIN).children(dayName, working);

        Node ticks = gui.box().width(Length.grow(1f)).height(Length.rem(1.4f));
        for (int h = FIRST / 60; h < LAST / 60; h++) {
            ticks.append(gui.text(Times.hour(h)).font(Type.MONO).textSize(Type.MICRO).textColor(Look.FAINT)
                    .wordWrap(false).floatAt(Length.percent(x(h * 60)), Length.ZERO));
        }
        Node clock = gui.row().width(Length.FILL).gap(Type.INSET).padding(Length.ZERO, Type.MARGIN)
                .children(gui.box().width(NAMES), ticks);

        lanes = gui.column().width(Length.FILL);
        detail = gui.column().width(Length.FILL).background(Look.CHROME).visible(false);
        gui.landmark(Landmarks.DETAIL, detail);
        Node scroller = gui.column().width(Length.FILL).height(Length.grow(1f)).scroll(false, true)
                .children(lanes, detail);

        node = gui.column().width(Length.grow(1f)).height(Length.FILL).background(Look.LANES)
                .children(heading, clock, Ui.rule(gui, true), scroller);
    }

    Node node() {
        return node;
    }

    void show(Doc doc, Review.Day review) {
        Team team = review.team();
        int day = doc.day();
        dayName.text(team.weekday(day));
        working.text(team.working(day).size() + " of " + team.clinicians().size() + " clinicians working");

        Ui.clear(built);
        List<Review.Check> nobody = review.checks().stream().filter(c -> c.visit().clinician().isEmpty()).toList();
        if (!nobody.isEmpty()) {
            Node lane = lane(doc);
            for (Review.Check c : nobody) {
                lane.append(bar(c.visit(), c, doc));
            }
            now(lane, doc);
            add(row(Ui.words(gui, "Needs someone", Type.BODY, Look.AMBER_TEXT).bold().wordWrap(false),
                    caps(nobody.size() == 1 ? "1 visit" : nobody.size() + " visits", Look.AMBER), lane,
                    Look.AMBER_LANE));
        }
        for (Clinician c : team.clinicians()) {
            Node lane = lane(doc);
            if (!c.works(day)) {
                lane.append(Ui.words(gui, "Off " + (day == doc.today() ? "today" : team.weekday(day)), Type.BODY,
                        Look.FAINT).wordWrap(false).floatAt(Length.dp(8), Length.rem(1.05f)));
            }
            for (Away a : team.away(day, c.name())) {
                lane.append(away(a));
            }
            for (Visit v : team.visits(day, c.name())) {
                lane.append(bar(v, review.check(v.id()).orElseThrow(), doc));
            }
            now(lane, doc);
            Node name = Ui.words(gui, c.name(), Type.BODY, c.works(day) ? Look.INK : Look.FAINT).bold()
                    .wordWrap(false);
            add(row(name, caps(String.join(" · ", c.credentials()), Look.FAINT), lane, Color.TRANSPARENT));
        }

        Optional<Review.Check> picked = doc.selected().flatMap(review::check);
        explain(team, picked);
    }

    private void add(Node row) {
        lanes.append(row);
        built.add(row);
        Node seam = gui.box().width(Length.FILL).height(Length.dp(1)).background(Look.SEAM);
        lanes.append(seam);
        built.add(seam);
    }

    private Node row(Node name, Node sub, Node lane, Color ground) {
        Node names = gui.column().width(NAMES).height(Length.FILL).justify(Justify.CENTER).gap(Length.dp(2))
                .children(name, sub);
        return gui.row().width(Length.FILL).height(LANE).gap(Type.INSET).padding(Length.ZERO, Type.MARGIN)
                .background(ground).children(names, lane);
    }

    private Node lane(Doc doc) {
        return gui.box().width(Length.grow(1f)).height(Length.FILL);
    }

    /** The line at now, if the day is today. It goes on last, so it is drawn over the bars, and lets clicks through. */
    private void now(Node lane, Doc doc) {
        if (doc.day() == doc.today() && doc.now() > FIRST && doc.now() < LAST) {
            lane.append(gui.box().floatAt(Length.percent(x(doc.now())), Length.ZERO)
                    .size(Length.dp(2), Length.percent(100)).background(Look.BLUE).hitInert(true));
        }
    }

    private Node bar(Visit v, Review.Check check, Doc doc) {
        boolean picked = doc.selected().filter(v.id()::equals).isPresent();
        boolean done = doc.day() < doc.today() || (doc.day() == doc.today() && v.end() <= doc.now());
        Color ground;
        Color edge;
        Color ink;
        if (!check.covered()) {
            ground = Look.AMBER_GROUND;
            edge = Look.AMBER;
            ink = Look.AMBER_TEXT;
        } else if (picked) {
            ground = Look.BLUE_PICKED;
            edge = Look.BLUE;
            ink = Look.INK;
        } else if (done) {
            ground = Look.DONE;
            edge = Look.DONE_EDGE;
            ink = Look.FAINT;
        } else {
            ground = Look.CARD;
            edge = Look.EDGE;
            ink = Look.INK;
        }
        Node bar = block(v.start(), v.end(), ground, edge, picked ? 2 : 1).role("button");
        bar.append(Ui.words(gui, v.label(), Type.SMALL, ink).wordWrap(false).hitInert(true));
        gui.onClick(bar, () -> model.toggle(v.id()));
        gui.landmark(Landmarks.visit(v.label()), bar);
        return bar;
    }

    private Node away(Away a) {
        Node bar = block(a.start(), a.end(), Look.AWAY, Look.AWAY_EDGE, 1);
        bar.append(Ui.words(gui, Character.toUpperCase(a.what().charAt(0)) + a.what().substring(1), Type.SMALL,
                Look.FAINT).wordWrap(false));
        return bar;
    }

    private Node block(int start, int end, Color ground, Color edge, int line) {
        int from = Math.max(start, FIRST);
        int to = Math.min(end, LAST);
        return gui.row().floatAt(Length.percent(x(from)), BAR_TOP)
                .size(Length.percent(x(to) - x(from) - 0.15f), BAR).alignItems(AlignItems.CENTER)
                .padding(Length.ZERO, Length.dp(7)).corner(Length.dp(6)).border(Length.dp(line), edge)
                .background(ground).clip(true).scroll(false, false);
    }

    // ------------------------------------------------------------------------------------------------ the panel

    /** The picked-out visit, explained: why its clinician has it, or what it needs; and who else could take it. */
    private void explain(Team team, Optional<Review.Check> picked) {
        Ui.clear(explained);
        detail.visible(picked.isPresent());
        if (picked.isEmpty()) {
            return;
        }
        Review.Check check = picked.get();
        Visit v = check.visit();

        IconButton close = new IconButton(gui, Icon.CLOSE, "Close").onPress(model::deselect);
        gui.landmark(Landmarks.DETAIL_CLOSE, close.node());
        Node head = gui.row().width(Length.FILL).alignItems(AlignItems.CENTER).gap(Type.INSET)
                .padding(Length.dp(10), Type.MARGIN).children(
                        Ui.words(gui, v.patient() + " · " + v.care().toLowerCase(), Type.BODY, Look.INK).bold()
                                .wordWrap(false),
                        gui.text(Times.span(v.start(), v.end()) + " · " + v.address()).font(Type.MONO)
                                .textSize(Type.MICRO).textColor(Look.FAINT).wordWrap(false),
                        gui.box().width(Length.grow(1f)), close.node());

        Node left = gui.column().width(Length.grow(1f)).gap(Type.INSET);
        if (check.covered()) {
            Clinician c = check.clinician().orElseThrow();
            left.append(Ui.words(gui, "Why " + c.first() + " has this visit", Type.BODY, Look.INK).bold());
            for (Review.Reason r : check.why()) {
                left.append(item(true, r.need(), Look.INK, r.how()));
            }
        } else {
            left.append(Ui.words(gui, "What it needs", Type.BODY, Look.INK).bold());
            for (var need : v.needs()) {
                left.append(item(false, need.asked(), Look.INK, ""));
            }
            left.append(Ui.words(gui, check.explanation(), Type.SMALL, Look.AMBER_SOFT).width(Length.FILL));
        }
        Node right = gui.column().width(Length.grow(1f)).gap(Type.INSET)
                .append(Ui.words(gui, "Who else could take it", Type.BODY, Look.INK).bold());
        for (Review.Other o : check.others()) {
            right.append(item(o.could(), o.who(), o.could() ? Look.INK : Look.SOFT, o.why()));
        }
        Node columns = gui.row().width(Length.FILL).gap(Length.dp(24)).padding(Length.dp(14), Type.MARGIN)
                .children(left, right);
        Node foot = gui.text("Checked against " + team.name() + "'s visit rules").font(Type.MONO)
                .textSize(Type.MICRO).textColor(Look.FAINT).wordWrap(false).padding(Length.dp(8), Type.MARGIN);

        for (Node n : new Node[] {Ui.rule(gui, true), head, Ui.rule(gui, true), columns, foot}) {
            detail.append(n);
            explained.add(n);
        }
        detail.scrollIntoView();
    }

    /** A line with a tick or a cross in front, and a second line under it. */
    private Node item(boolean yes, String line, Color ink, String second) {
        Node mark = IconView.of(gui, yes ? Icon.CHECK : Icon.CLOSE).size(Length.rem(0.9f))
                .tint(yes ? Look.BLUE : Look.NO).node();
        Node words = gui.column().width(Length.grow(1f)).gap(Length.dp(2))
                .append(Ui.words(gui, line, Type.BODY, ink).width(Length.FILL));
        if (!second.isEmpty()) {
            words.append(Ui.words(gui, second, Type.SMALL, Look.DIM).width(Length.FILL));
        }
        return gui.row().width(Length.FILL).gap(Length.dp(10)).children(mark, words);
    }

    private Node caps(String text, Color color) {
        return gui.text(text.toUpperCase()).font(Type.MONO).textSize(Type.MICRO).textColor(color).wordWrap(false);
    }

    /** Where {@code minute} falls across the lane, in percent. */
    private static float x(int minute) {
        return 100f * (minute - FIRST) / (LAST - FIRST);
    }
}
