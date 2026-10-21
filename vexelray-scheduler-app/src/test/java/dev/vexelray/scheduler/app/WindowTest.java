package dev.vexelray.scheduler.app;

import dev.vexelray.framework.shell.Shell;
import dev.vexelray.framework.shell.VexelApplication;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

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

    @Test
    void itOpensOnACoveredToday() {
        Doc doc = wiring.model().doc();
        assertEquals(doc.today(), doc.day());
        assertEquals("Everything today is covered.", doc.review().headline());
    }

    @Test
    void thursdayIsFixedAndUndone() {
        Model model = wiring.model();
        model.day(THURSDAY);
        assertEquals("1 visit needs you.", model.doc().review().headline());

        model.choose(0);
        assertEquals("Move it to Friday at 10 am", model.doc().fix(model.doc().review()).orElseThrow().title());
        assertEquals("1 visit needs you.", model.doc().review().headline(), "choosing a fix changes nothing");

        model.apply();
        assertEquals("Everything Thursday is covered.", model.doc().review().headline());
        assertEquals(1, model.doc().history().size());

        model.undo();
        assertEquals("1 visit needs you.", model.doc().review().headline());
        assertTrue(model.doc().history().isEmpty());
    }

    @Test
    void pickingAVisitTwicePutsItBack() {
        Model model = wiring.model();
        String okafor = model.doc().team().visits(model.doc().today()).stream()
                .filter(v -> v.label().equals("Okafor")).findFirst().orElseThrow().id();
        model.toggle(okafor);
        assertEquals(Optional.of(okafor), model.doc().selected());
        model.toggle(okafor);
        assertFalse(model.doc().selected().isPresent());
    }
}
