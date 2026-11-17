package dev.vexelray.scheduler.app;

import dev.vexelray.framework.api.Job;
import dev.vexelray.scheduler.app.Messages.Edit;
import dev.vexelray.scheduler.app.Messages.Seen;

/**
 * An edit made, and the week reviewed, on the job lane: the review asks the solver about every day, which is longer
 * than a frame, so the {@link Screen} sends the edit here and draws what comes back.
 *
 * <p>Two edits may run at once and answer in either order. That is safe because the edit is committed as a function
 * of whatever the state is ({@link Model#change}), and the answer is the state read <em>after</em> the commit, with
 * its version: whichever job reads last reads the newest, and the screen draws only an answer newer than the last it
 * drew.
 */
@Job
public final class Revise {

    private final Model model;

    public Revise(Model model) {
        this.model = model;
    }

    public Seen run(Edit request) {
        model.change(request::on);
        return model.seen();
    }
}
