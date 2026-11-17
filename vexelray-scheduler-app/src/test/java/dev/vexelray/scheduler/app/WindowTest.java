package dev.vexelray.scheduler.app;

import dev.vexelray.framework.shell.Shell;
import dev.vexelray.framework.shell.VexelApplication;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The real tree, built headless by the generated wiring — no window, no GPU — and driven through the model a click
 * reaches: the main path of the demo, from a covered Tuesday to a fixed Thursday and back.
 */
class WindowTest {

    private static final int THURSDAY = 2;

    private Shell shell;
    private SchedulerAppWiring wiring;

    @BeforeEach
    void build() {
        wiring = new SchedulerAppWiring();
        shell = VexelApplication.tree(wiring, new String[0]);
    }

    @AfterEach
    void dispose() {
        shell.disposer().close();
    }

    /** The sample is laid on the clock's week, so today is the first day, or none on a weekend. */
    @Test
    void itOpensOnTheClocksToday() {
        Doc doc = wiring.model().doc();
        LocalDate date = LocalDate.now();
        boolean weekday = date.getDayOfWeek() != DayOfWeek.SATURDAY && date.getDayOfWeek() != DayOfWeek.SUNDAY;
        assertEquals(weekday ? 0 : -1, doc.today());
        assertEquals(0, doc.day());
        assertEquals(weekday ? "Everything today is covered." : "Everything " + doc.team().weekday(0) + " is covered.",
                doc.review().headline());
    }

    @Test
    void thursdayIsFixedAndUndone() {
        Model model = wiring.model();
        model.day(THURSDAY);
        assertEquals("1 visit needs you.", model.doc().review().headline());

        model.choose(0);
        assertEquals("Move it to " + model.doc().team().weekday(THURSDAY + 1) + " at 10 am",
                model.doc().fix(model.doc().review()).orElseThrow().title());
        assertEquals("1 visit needs you.", model.doc().review().headline(), "choosing a fix changes nothing");

        model.apply();
        assertEquals("Everything " + model.doc().team().weekday(THURSDAY) + " is covered.",
                model.doc().review().headline());
        assertEquals(1, model.doc().history().size());

        model.undo();
        assertEquals("1 visit needs you.", model.doc().review().headline());
        assertTrue(model.doc().history().isEmpty());

        model.change(Doc::redone);
        assertEquals("Everything " + model.doc().team().weekday(THURSDAY) + " is covered.",
                model.doc().review().headline(), "redo makes the same fix again");
        assertEquals(1, model.doc().history().size());
        assertTrue(model.doc().redoable().isEmpty());
    }

    @Test
    void pickingAVisitTwicePutsItBack() {
        Model model = wiring.model();
        String okafor = model.doc().team().visits(model.doc().day()).stream()
                .filter(v -> v.label().equals("Okafor")).findFirst().orElseThrow().id();
        model.toggle(okafor);
        assertEquals(Optional.of(okafor), model.doc().selected());
        model.toggle(okafor);
        assertFalse(model.doc().selected().isPresent());
    }
}
