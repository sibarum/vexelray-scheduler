package dev.vexelray.scheduler.app;

import dev.vexelray.canvas.Color;
import dev.vexelray.gui.core.Gui;
import dev.vexelray.gui.core.Node;
import dev.vexelray.gui.core.layout.LayoutEnums.AlignItems;
import dev.vexelray.gui.core.layout.Length;
import dev.vexelray.gui.icons.Icon;
import dev.vexelray.gui.icons.IconView;
import dev.vexelray.gui.widget.Button;
import dev.vexelray.gui.widget.IconButton;
import dev.vexelray.scheduler.app.visits.Clinician;
import dev.vexelray.scheduler.app.visits.Review;
import dev.vexelray.scheduler.app.visits.Times;
import dev.vexelray.scheduler.app.visits.Visit;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * "What's going on": the day in a sentence, and what to do about it.
 *
 * <p>Under the headline is one of three pages. A covered day says who is where right now and looks over the rest of
 * the week. A visit picked out on the schedule says why its clinician has it and who else could take it. A day
 * with a visit that needs someone says which, why nobody can take it, and offers the ways to fix it the review
 * found. Picking one changes nothing: it shows what would move, and only the button under that makes the change,
 * which can be undone. That order — the effect shown before anything happens — is the suite's, and it is what lets
 * someone who has never heard of a constraint solver use one.
 *
 * <p>Pages are put on a {@link Motion.Stage}, so going from one to another is a crossfade; within a page, choosing a
 * fix only recolours the cards and brings the confirmation up under them.
 */
final class Briefing {

    private static final Length WIDTH = Length.rem(25);

    private final Gui gui;
    private final Model model;
    private final Motion motion;
    private final Node node;
    private final Node badgeNode;
    private final Motion.Label badge;
    private final Motion.Label headline;
    private final Motion.Label detail;
    private final Motion.Stage stage;
    private Page page;

    /** What is on stage, kept up to date in place until the stage shows something else. */
    private interface Page {
        void update(Doc doc, Review.Day review);
    }

    Briefing(Gui gui, Model model, Motion motion) {
        this.gui = gui;
        this.model = model;
        this.motion = motion;

        badgeNode = gui.text("").font(Type.MONO).textSize(Type.MICRO).wordWrap(false);
        badge = motion.label(badgeNode, badgeNode::text);
        Node top = gui.row().width(Length.FILL).alignItems(AlignItems.CENTER).children(
                Ui.words(gui, "What's going on", Type.BODY, Look.INK).bold().wordWrap(false),
                gui.box().width(Length.grow(1f)), badgeNode);
        Node headlineNode = Ui.words(gui, "", Type.HEADLINE, Look.INK).bold().width(Length.FILL);
        gui.landmark(Landmarks.HEADLINE, headlineNode);
        headline = motion.label(headlineNode, headlineNode::text);
        Node detailNode = Ui.words(gui, "", Type.BODY, Look.DIM).width(Length.FILL);
        detail = motion.label(detailNode, detailNode::text);
        Node region = gui.column().width(Length.FILL).height(Length.grow(1f)).scroll(false, false);
        stage = motion.stage(region);

        // It scrolls only when the window is shorter than anything the design is drawn for; see Motion.
        node = gui.column().width(WIDTH).height(Length.FILL).background(Look.BRIEFING).padding(Type.MARGIN)
                .gap(Length.dp(16)).scroll(false, true).clip(true)
                .children(top, gui.column().width(Length.FILL).gap(Type.GAP).children(headlineNode, detailNode),
                        region);
    }

    Node node() {
        return node;
    }

    void show(Doc doc, Review.Day review) {
        headline.set(review.headline());
        detail.set(review.detail());
        int problems = review.problems().size();
        badge.set(problems == 0 ? "checked " + Times.clock(doc.now())
                : problems + " need" + (problems == 1 ? "s" : "") + " you");
        badgeNode.textColor(problems == 0 ? Look.FAINT : Look.AMBER);

        Optional<Review.Check> focus = doc.focus(review);
        Optional<Review.Check> picked = doc.selected().flatMap(review::check).filter(Review.Check::covered);
        String version = ":" + doc.history().size();
        String key;
        if (focus.isPresent() && picked.isEmpty()) {
            Review.Check f = focus.get();
            key = "fix:" + doc.day() + ":" + f.visit().id() + ":"
                    + String.join("|", f.fixes().stream().map(Review.Fix::title).toList()) + version;
        } else if (picked.isPresent()) {
            key = "why:" + picked.get().visit().id() + version;
        } else {
            key = "day:" + doc.day() + version;
        }
        if (page == null || !stage.showing(key)) {
            Node root = gui.column().width(Length.FILL).gap(Type.INSET);
            if (focus.isPresent() && picked.isEmpty()) {
                page = new Fixing(root, doc, review, focus.get());
            } else if (picked.isPresent()) {
                page = explaining(root, review, picked.get());
            } else {
                page = day(root, doc, review);
            }
            history(root, doc);
            page.update(doc, review);
            stage.show(key, root);
        } else {
            page.update(doc, review);
        }
    }

    // ------------------------------------------------------------------------------------------------ the day

    /** Right now, if the day is today; and the rest of the week. Nothing on it changes while it is shown. */
    private Page day(Node root, Doc doc, Review.Day review) {
        if (doc.day() == doc.today()) {
            List<Review.Now> now = Review.now(review.team(), doc.day(), doc.now());
            if (!now.isEmpty()) {
                root.append(Ui.caps(gui, "Right now"));
                for (Review.Now n : now) {
                    root.append(card(n.what(), n.detail(), Look.CARD, Look.RULE));
                }
            }
        }
        if (!review.later().isEmpty()) {
            root.append(Ui.caps(gui, "Later this week"));
            for (String line : review.later()) {
                root.append(Ui.words(gui, line, Type.BODY, Look.SOFT).width(Length.FILL));
            }
        }
        return (d, r) -> {
        };
    }

    // ------------------------------------------------------------------------------------------------ one visit

    /** The picked-out visit: why its clinician has it, and who else could take it. */
    private Page explaining(Node root, Review.Day review, Review.Check check) {
        Visit v = check.visit();
        Clinician c = check.clinician().orElseThrow();
        gui.landmark(Landmarks.DETAIL, root);

        IconButton close = new IconButton(gui, Icon.CLOSE, "Put it back").onPress(model::deselect);
        gui.landmark(Landmarks.DETAIL_CLOSE, close.node());
        root.append(gui.row().width(Length.FILL).alignItems(AlignItems.CENTER).gap(Type.GAP).children(
                gui.column().width(Length.grow(1f)).gap(Length.dp(2)).children(
                        Ui.words(gui, v.patient() + " · " + v.care().toLowerCase(), Type.TITLE, Look.INK).bold()
                                .width(Length.FILL),
                        gui.text(Times.span(v.start(), v.end()) + " · " + v.address()).font(Type.MONO)
                                .textSize(Type.MICRO).textColor(Look.FAINT).wordWrap(false)),
                close.node()));

        root.append(Ui.caps(gui, "Why " + c.first() + " has this visit"));
        for (Review.Reason r : check.why()) {
            root.append(item(true, r.need(), Look.INK, r.how()));
        }
        root.append(Ui.caps(gui, "Who else could take it"));
        for (Review.Other o : check.others()) {
            root.append(item(o.could(), o.who(), o.could() ? Look.INK : Look.SOFT, o.why()));
        }
        root.append(gui.text("Checked against " + review.team().name() + "'s visit rules").font(Type.MONO)
                .textSize(Type.MICRO).textColor(Look.FAINT).wordWrap(false));
        return (d, r) -> {
        };
    }

    // ------------------------------------------------------------------------------------------------ fixing

    /** The visit that needs you, why, and the ways out; under the one picked, what it would move. */
    private final class Fixing implements Page {

        private final Node root;
        private final List<Node> options = new ArrayList<>();
        /** Where the confirmation goes among the page's children: after the problem, the heading, the fixes and
         * "leave it". */
        private final int slot;
        private Node preview;
        private Motion.Label change;
        private Motion.Label confirmLabel;

        Fixing(Node root, Doc doc, Review.Day review, Review.Check check) {
            this.root = root;
            root.append(gui.column().width(Length.FILL).gap(Type.GAP).padding(Length.dp(14)).corner(Length.dp(10))
                    .border(Length.dp(1), Look.AMBER).background(Look.AMBER_GROUND).children(
                            Ui.words(gui, check.problem(), Type.TITLE, Look.AMBER_TEXT).bold().width(Length.FILL),
                            Ui.words(gui, check.explanation(), Type.BODY, Look.AMBER_SOFT).width(Length.FILL)));
            if (check.fixes().isEmpty()) {
                root.append(Ui.words(gui, "Nothing in this schedule can take it as it stands: someone has to be "
                        + "found outside it.", Type.BODY, Look.SOFT).width(Length.FILL));
                slot = 2;
                return;
            }
            root.append(Ui.caps(gui, "Ways to fix it"));
            for (int i = 0; i < check.fixes().size(); i++) {
                Review.Fix fix = check.fixes().get(i);
                Node option = card(fix.title(), fix.detail(), Look.CARD, Look.EDGE).role("button");
                int index = i;
                gui.onClick(option, () -> model.choose(index));
                gui.landmark(Landmarks.fix(i), option);
                root.append(option);
                options.add(option);
            }
            Node leave = card("Leave it for now", "It stays at the top of " + review.team().weekday(doc.day())
                    + " until someone takes it.", Look.CARD, Look.EDGE).role("button");
            gui.onClick(leave, () -> model.choose(Doc.NONE));
            root.append(leave);
            slot = options.size() + 3;
        }

        @Override
        public void update(Doc doc, Review.Day review) {
            for (int i = 0; i < options.size(); i++) {
                boolean chosen = i == doc.chosen();
                options.get(i).background(chosen ? Look.BLUE_GROUND : Look.CARD)
                        .border(Length.dp(1), chosen ? Look.BLUE : Look.EDGE);
            }
            Optional<Review.Fix> fix = doc.fix(review);
            if (fix.isPresent() && preview == null) {
                preview = preview(fix.get());
                root.insert(preview, slot);
            } else if (fix.isPresent()) {
                change.set(fix.get().change());
                confirmLabel.set(fix.get().title());
            } else if (preview != null) {
                preview.remove();
                preview = null;
            }
        }

        /** What the fix would move, and the button that moves it. */
        private Node preview(Review.Fix fix) {
            Button confirm = new Button(gui, fix.title()).kind(Button.Kind.PRIMARY).onPress(model::apply);
            Button notNow = new Button(gui, "Not now").onPress(() -> model.choose(Doc.NONE));
            gui.landmark(Landmarks.CONFIRM, confirm.node());
            gui.landmark(Landmarks.NOT_NOW, notNow.node());
            confirm.node().width(Length.grow(1f));
            confirmLabel = motion.label(confirm.node(), confirm::label);
            confirmLabel.set(fix.title());
            Node changeNode = gui.text("").font(Type.MONO).textSize(Type.SMALL).textColor(Look.INK)
                    .width(Length.FILL);
            change = motion.label(changeNode, changeNode::text);
            change.set(fix.change());
            return gui.column().width(Length.FILL).gap(Type.INSET).padding(Length.dp(16)).corner(Length.dp(10))
                    .border(Length.dp(1), Look.EDGE).background(Look.CARD).children(
                            gui.row().width(Length.FILL).alignItems(AlignItems.CENTER).children(
                                    Ui.words(gui, "1 visit moves", Type.BODY, Look.INK).bold().wordWrap(false),
                                    gui.box().width(Length.grow(1f)),
                                    gui.text("nothing has changed yet").font(Type.MONO).textSize(Type.MICRO)
                                            .textColor(Look.FAINT).wordWrap(false)),
                            gui.column().width(Length.FILL).padding(Length.dp(10)).corner(Type.ROUND)
                                    .background(Look.CHROME).append(changeNode),
                            gui.row().width(Length.FILL).gap(Type.GAP).children(confirm.node(), notNow.node()),
                            Ui.words(gui, "You can undo this.", Type.SMALL, Look.FAINT));
        }
    }

    // ------------------------------------------------------------------------------------------------ shapes

    /** The last change, and the way back from it, at the foot of any page once something has changed. */
    private void history(Node root, Doc doc) {
        if (doc.history().isEmpty()) {
            return;
        }
        Doc.Change last = doc.history().getLast();
        Button undo = new Button(gui, "Undo").kind(Button.Kind.GHOST).onPress(model::undo);
        gui.landmark(Landmarks.UNDO, undo.node());
        root.append(Ui.caps(gui, "Last change"));
        root.append(gui.row().width(Length.FILL).alignItems(AlignItems.CENTER).gap(Type.GAP).children(
                gui.text(last.what()).font(Type.MONO).textSize(Type.MICRO).textColor(Look.SOFT)
                        .width(Length.grow(1f)),
                undo.node()));
    }

    private Node card(String title, String text, Color ground, Color edge) {
        return gui.column().width(Length.FILL).gap(Length.dp(3)).padding(Length.dp(11), Length.dp(13))
                .corner(Type.ROUND).border(Length.dp(1), edge).background(ground).children(
                        Ui.words(gui, title, Type.BODY, Look.INK).bold().width(Length.FILL).hitInert(true),
                        Ui.words(gui, text, Type.SMALL, Look.DIM).width(Length.FILL).hitInert(true));
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
}
