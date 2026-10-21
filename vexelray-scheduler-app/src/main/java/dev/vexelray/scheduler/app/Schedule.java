package dev.vexelray.scheduler.app;

import dev.vexelray.canvas.Color;
import dev.vexelray.gui.core.Gui;
import dev.vexelray.gui.core.Node;
import dev.vexelray.gui.core.layout.LayoutEnums.AlignItems;
import dev.vexelray.gui.core.layout.LayoutEnums.Justify;
import dev.vexelray.gui.core.layout.Length;
import dev.vexelray.scheduler.app.visits.Away;
import dev.vexelray.scheduler.app.visits.Clinician;
import dev.vexelray.scheduler.app.visits.Review;
import dev.vexelray.scheduler.app.visits.Team;
import dev.vexelray.scheduler.app.visits.Times;
import dev.vexelray.scheduler.app.visits.Visit;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * The day, one lane a clinician: a bar for every visit at its time, time away drawn quieter, a blue line at now.
 * A visit that needs someone is amber — in a lane of its own when nobody has it. Clicking a bar picks the visit out,
 * and the briefing explains it.
 *
 * <h2>A day is a page, and a page is kept</h2>
 *
 * <p>Each day is drawn once, as a page, and changed in place while it is shown: a bar is a node for the life of its
 * visit on that day, so picking it out is a change of colour, giving it to someone else is the same bar moving to
 * their lane, and a visit leaving the day is that bar fading out. Showing another day puts that day's page on the
 * stage over this one. Every one of those is a change {@link Motion} knows how to make without a jump.
 *
 * <p>Bars are floating children of their lane, placed by percentage of the day, so the lanes stretch with the window
 * and nothing is measured. Each is a real node with a click handler and a landmark ({@code visit.okafor}), so a
 * script clicks the same thing a person does. Nothing here scrolls: a scrollbar arrives in one frame.
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
    private final Motion motion;
    private final Node node;
    private final Motion.Label dayName;
    private final Motion.Label working;
    private final Motion.Stage stage;
    private Page page;

    Schedule(Gui gui, Model model, Motion motion) {
        this.gui = gui;
        this.model = model;
        this.motion = motion;

        Node dayNode = Ui.words(gui, "", Type.TITLE, Look.INK).bold().wordWrap(false);
        Node workingNode = Ui.words(gui, "", Type.SMALL, Look.FAINT).wordWrap(false);
        dayName = motion.label(dayNode, dayNode::text);
        working = motion.label(workingNode, workingNode::text);
        Node heading = gui.row().width(Length.FILL).alignItems(AlignItems.END).gap(Type.INSET)
                .padding(Length.dp(14), Type.MARGIN).children(dayNode, workingNode);

        Node ticks = gui.box().width(Length.grow(1f)).height(Length.rem(1.4f));
        for (int h = FIRST / 60; h < LAST / 60; h++) {
            ticks.append(gui.text(Times.hour(h)).font(Type.MONO).textSize(Type.MICRO).textColor(Look.FAINT)
                    .wordWrap(false).floatAt(Length.percent(x(h * 60)), Length.ZERO));
        }
        Node clock = gui.row().width(Length.FILL).gap(Type.INSET).padding(Length.ZERO, Type.MARGIN)
                .children(gui.box().width(NAMES), ticks);

        Node lanes = Ui.still(gui.column().width(Length.FILL).height(Length.grow(1f)));
        stage = motion.stage(lanes);

        node = Ui.still(gui.column().width(Length.grow(1f)).height(Length.FILL)).background(Look.LANES)
                .children(heading, clock, Ui.rule(gui, true), lanes);
    }

    Node node() {
        return node;
    }

    void show(Doc doc, Review.Day review) {
        Team team = review.team();
        dayName.set(team.weekday(doc.day()));
        working.set(team.working(doc.day()).size() + " of " + team.clinicians().size() + " clinicians working");
        String key = "day:" + doc.day();
        if (page == null || !stage.showing(key)) {
            page = new Page(doc, team);
            page.update(doc, review, false);
            stage.show(key, page.node);
        } else {
            page.update(doc, review, true);
        }
    }

    /** Where {@code minute} falls across the lane, in percent. */
    private static float x(int minute) {
        return 100f * (Math.clamp(minute, FIRST, LAST) - FIRST) / (LAST - FIRST);
    }

    // ------------------------------------------------------------------------------------------------ a day

    /** One day's lanes, kept while the day is shown. Only ever touched from {@link #show}, one call at a time. */
    private final class Page {

        final Node node;
        private final Map<String, Node> lanes = new HashMap<>();
        private final Map<String, Node> names = new HashMap<>();
        private final Map<String, Motion.Label> offs = new HashMap<>();
        private final Map<String, Bar> bars = new HashMap<>();
        private final Map<Node, Node> lines = new HashMap<>();
        private Node needs;
        private Node needsLane;
        private Motion.Label needsCount;

        /** A visit's bar, the lane it is in, and its label. */
        private static final class Bar {
            final Node box;
            final Node label;
            String lane;
            int start;

            Bar(Node box, Node label, String lane, int start) {
                this.box = box;
                this.label = label;
                this.lane = lane;
                this.start = start;
            }
        }

        Page(Doc doc, Team team) {
            int day = doc.day();
            node = gui.column().width(Length.FILL);
            for (Clinician c : team.clinicians()) {
                Node lane = lane(doc);
                for (Away a : team.away(day, c.name())) {
                    lane.append(away(a));
                }
                Node off = Ui.words(gui, "", Type.BODY, Look.FAINT).wordWrap(false)
                        .floatAt(Length.dp(8), Length.rem(1.05f));
                lane.append(off);
                offs.put(c.name(), motion.label(off, off::text));
                Node name = Ui.words(gui, c.name(), Type.BODY, Look.INK).bold().wordWrap(false);
                names.put(c.name(), name);
                lanes.put(c.name(), lane);
                node.append(row(name, Ui.caps(gui, String.join(" · ", c.credentials())), lane, Color.TRANSPARENT));
            }
            motion.follow(node);
        }

        /** Bring the page to {@code review}; {@code live} when it is already on screen, so arrivals fade in. */
        void update(Doc doc, Review.Day review, boolean live) {
            Team team = review.team();
            int day = doc.day();

            List<Review.Check> nobody = review.checks().stream()
                    .filter(c -> c.visit().clinician().isEmpty()).toList();
            if (!nobody.isEmpty() && needs == null) {
                needsLane = lane(doc);
                Node countNode = Ui.caps(gui, "", Look.AMBER);
                needsCount = motion.label(countNode, countNode::text);
                needs = row(Ui.words(gui, "Needs someone", Type.BODY, Look.AMBER_TEXT).bold().wordWrap(false),
                        countNode, needsLane, Look.AMBER_LANE);
                node.insert(needs, 0);
                if (live) {
                    motion.appear(needs);
                }
            }
            if (needsCount != null) {
                needsCount.set(nobody.size() == 1 ? "1 VISIT" : nobody.size() + " VISITS");
            }

            for (Clinician c : team.clinicians()) {
                names.get(c.name()).textColor(c.works(day) ? Look.INK : Look.FAINT);
                offs.get(c.name()).set(c.works(day) ? "" : "Off " + (day == doc.today() ? "today" : team.weekday(day)));
            }

            Set<String> here = new HashSet<>();
            for (Review.Check check : review.checks()) {
                Visit v = check.visit();
                here.add(v.id());
                String laneKey = v.clinician().orElse("");
                Node lane = laneKey.isEmpty() ? needsLane : lanes.get(laneKey);
                Bar bar = bars.get(v.id());
                if (bar == null) {
                    bar = bar(v);
                    bars.put(v.id(), bar);
                    lane.append(bar.box);
                    if (live) {
                        motion.appear(bar.box);
                    }
                } else if (!bar.lane.equals(laneKey)) {
                    // The same bar, in another lane: the move is a slide, from where it was drawn.
                    bar.lane = laneKey;
                    lane.append(bar.box);
                }
                if (bar.start != v.start()) {
                    bar.start = v.start();
                    place(bar.box, v.start(), v.end());
                }
                paint(bar, v, check, doc);
            }
            for (var it = bars.entrySet().iterator(); it.hasNext(); ) {
                var e = it.next();
                if (!here.contains(e.getKey())) {
                    motion.leave(e.getValue().box);
                    it.remove();
                }
            }
            raiseLines();

            if (nobody.isEmpty() && needs != null) {
                motion.leave(needs);
                lines.remove(needsLane);
                needs = null;
                needsLane = null;
                needsCount = null;
            }
        }

        private Bar bar(Visit v) {
            Node label = Ui.words(gui, v.label(), Type.SMALL, Look.INK).wordWrap(false).hitInert(true);
            Node box = Ui.still(gui.row()).alignItems(AlignItems.CENTER).padding(Length.ZERO, Length.dp(7))
                    .corner(Length.dp(6)).border(Length.dp(1), Look.EDGE).background(Look.CARD).role("button")
                    .append(label);
            place(box, v.start(), v.end());
            gui.onClick(box, () -> model.toggle(v.id()));
            gui.landmark(Landmarks.visit(v.label()), box);
            return new Bar(box, label, v.clinician().orElse(""), v.start());
        }

        /** A bar's colours: amber when the visit needs someone, blue when picked out, sunk when it is over. */
        private void paint(Bar bar, Visit v, Review.Check check, Doc doc) {
            boolean picked = doc.selected().filter(v.id()::equals).isPresent();
            boolean done = doc.day() < doc.today() || (doc.day() == doc.today() && v.end() <= doc.now());
            Color ground;
            Color edge;
            Color ink;
            if (!check.covered()) {
                ground = picked ? Look.AMBER_PICKED : Look.AMBER_GROUND;
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
            bar.box.background(ground).border(Length.dp(1), edge);
            bar.label.textColor(ink);
        }

        private Node away(Away a) {
            Node box = Ui.still(gui.row()).alignItems(AlignItems.CENTER).padding(Length.ZERO, Length.dp(7))
                    .corner(Length.dp(6)).border(Length.dp(1), Look.AWAY_EDGE).background(Look.AWAY);
            place(box, a.start(), a.end());
            return box.append(Ui.words(gui, Character.toUpperCase(a.what().charAt(0)) + a.what().substring(1),
                    Type.SMALL, Look.FAINT).wordWrap(false));
        }

        private void place(Node box, int start, int end) {
            box.floatAt(Length.percent(x(start)), BAR_TOP).size(Length.percent(x(end) - x(start) - 0.15f), BAR);
        }

        /** An empty lane, with the line at now on it if the day is today. */
        private Node lane(Doc doc) {
            Node lane = Ui.still(gui.box().width(Length.grow(1f)).height(Length.FILL));
            if (doc.day() == doc.today() && doc.now() > FIRST && doc.now() < LAST) {
                Node line = gui.box().floatAt(Length.percent(x(doc.now())), Length.ZERO)
                        .size(Length.dp(2), Length.percent(100)).background(Look.BLUE).hitInert(true);
                lane.append(line);
                lines.put(lane, line);
            }
            return lane;
        }

        /**
         * Put each line at now back at the end of its lane. A float draws over the siblings before it, so the line
         * stays over every bar only by being last, and a bar that arrived since was appended after it.
         */
        private void raiseLines() {
            lines.forEach(Node::append);
        }

        /** A lane with its name in front, and the seam under it. */
        private Node row(Node name, Node sub, Node lane, Color ground) {
            Node names = gui.column().width(NAMES).height(Length.FILL).justify(Justify.CENTER).gap(Length.dp(2))
                    .children(name, sub);
            Node row = gui.row().width(Length.FILL).height(LANE).gap(Type.INSET).padding(Length.ZERO, Type.MARGIN)
                    .background(ground).children(names, lane);
            return gui.column().width(Length.FILL).children(row,
                    gui.box().width(Length.FILL).height(Length.dp(1)).background(Look.SEAM));
        }
    }
}
