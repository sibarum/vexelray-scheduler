package dev.vexelray.scheduler.app;

import dev.vexelray.canvas.Color;
import dev.vexelray.gui.core.Gui;
import dev.vexelray.gui.core.Node;
import dev.vexelray.gui.core.layout.LayoutEnums.AlignItems;
import dev.vexelray.gui.core.layout.Length;
import dev.vexelray.gui.widget.Button;
import dev.vexelray.scheduler.app.visits.Review;
import dev.vexelray.scheduler.app.visits.Times;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * "What's going on": the day in a sentence, and what to do about it.
 *
 * <p>A covered day says so, says who is where right now, and looks over the rest of the week. A day with a visit
 * that needs someone says which, why nobody can take it, and offers the ways to fix it the review found. Picking one
 * changes nothing: it shows what would move, and only the button under that makes the change, which can be undone.
 * That order — the effect shown before anything happens — is the suite's, and it is what lets someone who has never
 * heard of a constraint solver use one.
 */
final class Briefing {

    private static final Length WIDTH = Length.rem(25);

    private final Gui gui;
    private final Model model;
    private final Node node;
    private final Node badge;
    private final Node headline;
    private final Node detail;
    private final Node body;
    private final List<Node> built = new ArrayList<>();

    Briefing(Gui gui, Model model) {
        this.gui = gui;
        this.model = model;

        badge = gui.text("").font(Type.MONO).textSize(Type.MICRO).wordWrap(false);
        Node top = gui.row().width(Length.FILL).alignItems(AlignItems.CENTER).children(
                Ui.words(gui, "What's going on", Type.BODY, Look.INK).bold().wordWrap(false),
                gui.box().width(Length.grow(1f)), badge);
        headline = Ui.words(gui, "", Type.HEADLINE, Look.INK).bold().width(Length.FILL);
        gui.landmark(Landmarks.HEADLINE, headline);
        detail = Ui.words(gui, "", Type.BODY, Look.DIM).width(Length.FILL);
        body = gui.column().width(Length.FILL).gap(Type.INSET);

        node = gui.column().width(WIDTH).height(Length.FILL).background(Look.BRIEFING).padding(Type.MARGIN)
                .gap(Length.dp(16)).scroll(false, true)
                .children(top, gui.column().width(Length.FILL).gap(Type.GAP).children(headline, detail), body);
    }

    Node node() {
        return node;
    }

    void show(Doc doc, Review.Day review) {
        headline.text(review.headline());
        detail.text(review.detail());
        int problems = review.problems().size();
        badge.text(problems == 0 ? "checked " + Times.clock(doc.now()) : problems + " need" + (problems == 1 ? "s" : "")
                + " you");
        badge.textColor(problems == 0 ? Look.FAINT : Look.AMBER);

        Ui.clear(built);
        Optional<Review.Check> focus = doc.focus(review);
        if (focus.isPresent()) {
            fixes(doc, review, focus.get());
        } else {
            if (doc.day() == doc.today()) {
                List<Review.Now> now = Review.now(review.team(), doc.day(), doc.now());
                if (!now.isEmpty()) {
                    add(Ui.caps(gui, "Right now"));
                    for (Review.Now n : now) {
                        add(card(n.what(), n.detail(), Look.CARD, Look.RULE));
                    }
                }
            }
            if (!review.later().isEmpty()) {
                add(Ui.caps(gui, "Later this week"));
                for (String line : review.later()) {
                    add(Ui.words(gui, line, Type.BODY, Look.SOFT).width(Length.FILL));
                }
            }
        }
        if (!doc.history().isEmpty()) {
            Doc.Change last = doc.history().getLast();
            Button undo = new Button(gui, "Undo").kind(Button.Kind.GHOST).onPress(model::undo);
            gui.landmark(Landmarks.UNDO, undo.node());
            add(Ui.caps(gui, "Last change"));
            add(gui.row().width(Length.FILL).alignItems(AlignItems.CENTER).gap(Type.GAP).children(
                    gui.text(last.what()).font(Type.MONO).textSize(Type.MICRO).textColor(Look.SOFT)
                            .width(Length.grow(1f)),
                    undo.node()));
        }
    }

    /** The visit that needs you, why, and the ways out; and under the one picked, what it would move. */
    private void fixes(Doc doc, Review.Day review, Review.Check check) {
        Node problem = gui.column().width(Length.FILL).gap(Type.GAP).padding(Length.dp(14)).corner(Length.dp(10))
                .border(Length.dp(1), Look.AMBER).background(Look.AMBER_GROUND).children(
                        Ui.words(gui, check.problem(), Type.TITLE, Look.AMBER_TEXT).bold().width(Length.FILL),
                        Ui.words(gui, check.explanation(), Type.BODY, Look.AMBER_SOFT).width(Length.FILL));
        add(problem);
        if (check.fixes().isEmpty()) {
            add(Ui.words(gui, "Nothing in this schedule can take it as it stands: someone has to be found outside "
                    + "it.", Type.BODY, Look.SOFT).width(Length.FILL));
            return;
        }
        add(Ui.caps(gui, "Ways to fix it"));
        for (int i = 0; i < check.fixes().size(); i++) {
            Review.Fix fix = check.fixes().get(i);
            boolean chosen = i == doc.chosen();
            Node option = card(fix.title(), fix.detail(), chosen ? Look.BLUE_GROUND : Look.CARD,
                    chosen ? Look.BLUE : Look.EDGE).role("button");
            int index = i;
            gui.onClick(option, () -> model.choose(index));
            gui.landmark(Landmarks.fix(i), option);
            add(option);
        }
        Node leave = card("Leave it for now", "It stays at the top of " + review.team().weekday(doc.day())
                + " until someone takes it.", Look.CARD, Look.EDGE).role("button");
        gui.onClick(leave, () -> model.choose(Doc.NONE));
        add(leave);

        Optional<Review.Fix> fix = doc.fix(review);
        if (fix.isPresent()) {
            Button confirm = new Button(gui, fix.get().title()).kind(Button.Kind.PRIMARY).onPress(model::apply);
            Button notNow = new Button(gui, "Not now").onPress(() -> model.choose(Doc.NONE));
            gui.landmark(Landmarks.CONFIRM, confirm.node());
            gui.landmark(Landmarks.NOT_NOW, notNow.node());
            confirm.node().width(Length.grow(1f));
            Node preview = gui.column().width(Length.FILL).gap(Type.INSET).padding(Length.dp(16))
                    .corner(Length.dp(10)).border(Length.dp(1), Look.EDGE).background(Look.CARD).children(
                            gui.row().width(Length.FILL).alignItems(AlignItems.CENTER).children(
                                    Ui.words(gui, "1 visit moves", Type.BODY, Look.INK).bold().wordWrap(false),
                                    gui.box().width(Length.grow(1f)),
                                    gui.text("nothing has changed yet").font(Type.MONO).textSize(Type.MICRO)
                                            .textColor(Look.FAINT).wordWrap(false)),
                            gui.text(fix.get().change()).font(Type.MONO).textSize(Type.SMALL).textColor(Look.INK)
                                    .width(Length.FILL).padding(Length.dp(10)).corner(Type.ROUND)
                                    .background(Look.CHROME),
                            gui.row().width(Length.FILL).gap(Type.GAP).children(confirm.node(), notNow.node()),
                            Ui.words(gui, "You can undo this.", Type.SMALL, Look.FAINT));
            add(preview);
            preview.scrollIntoView();
        }
    }

    private Node card(String title, String text, Color ground, Color edge) {
        return gui.column().width(Length.FILL).gap(Length.dp(3)).padding(Length.dp(11), Length.dp(13))
                .corner(Type.ROUND).border(Length.dp(1), edge).background(ground).children(
                        Ui.words(gui, title, Type.BODY, Look.INK).bold().width(Length.FILL).hitInert(true),
                        Ui.words(gui, text, Type.SMALL, Look.DIM).width(Length.FILL).hitInert(true));
    }

    private void add(Node n) {
        body.append(n);
        built.add(n);
    }
}
