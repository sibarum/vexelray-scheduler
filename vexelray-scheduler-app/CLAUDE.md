# Working in Scheduler

Scheduler is a VexelRay desktop application, generated from `vexelray-framework`'s `vexel-desktop` template. It
started as the template's reference application, a small tabbed text editor, and is now a home-visit team's
schedule that says what is going on with it in plain sentences, each standing on a checked answer from the solver
module. The design is the "Scheduler Mockups" canvas. [README.md](README.md) is the tour: read its *How it is put together* section before
changing anything, and `Recipes.java` before adding a part.

## Where things are

- **`Recipes.java`** is what the application is made of: one `@Provides` method per part. `SchedulerAppWiring`
  is generated from it by an annotation processor while the project compiles. Nobody writes it, so never create
  or edit a file of that name. After a build it is in `target/generated-sources/annotations/`, and it shows what is
  built in which phase.
- **`Model.java` and `Doc.java`** hold all of the application's state, as one immutable value that is replaced, never
  mutated. `Ui.java` holds no state.
- **Code with no `Gui` in it** goes in a sub-package with public types (as `visits/` does), so it can be unit tested
  without a window. `visits/Review` is where the solver's answers become sentences; a sentence a person reads never
  names a rule, a tag or a constraint.
- **`Landmarks.java`** names everything an automation script may refer to. A new control a test needs to reach gets
  a landmark there, not a string literal.
- **Icons** come from the suite's set, `dev.vexelray.gui.icons.Icon`, first. An icon it does not have, and only this
  application draws, goes in `src/main/icons/small` or `large` (SVG, or a black-on-white or transparent PNG), and the
  build bakes it into `SchedulerIcon`, which goes anywhere an `Icon` does. Never draw an icon in code or load an
  image for one. The pom's `app-icons` profile says how.
- **The stack** (vexelray, vexelray-gui, vexelray-framework, tactroller, atchung, kronometer, ...) is usually checked
  out in the folder above this one. **Treat it as read-only.** Its source and javadoc are the documentation; read
  them instead of guessing an API. If something you need is missing there, do not patch the stack. Write it up in
  `docs/framework-notes.md` (below) and say so.

## Rules the code does not show

Breaking one of these rarely fails a build. It shows up as a hang, a stutter or a broken native image, so hold to
them even when a shortcut looks harmless:

- **Nothing reflects.** No `Class.forName`, no runtime annotation reads, no reflective serialization. This compiles to
  a GraalVM native image, and the framework's wiring is generated at compile time so that nothing needs reflection.
- **Nothing in the frame loop allocates, logs or blocks.** No disk, no network, no locks there.
- **Vulkan, the window and present stay on the main thread.** Handlers run on workers, and disk work goes to the
  offload lane. README's *Threading, in one paragraph* is the whole model. Nothing holds a lock today; if something
  comes to, nothing may commit to the model while holding it, and nothing reached from a model listener may take it.
- **All input comes through tactroller.** No polling the OS keyboard, and no native listener of your own.
- **Log through `sibarum.probe.Log`**, as `Log.of("vexelray-scheduler-app.<area>")`. stdout is for lines another program parses
  (`automation: localhost:<port>`), and for nothing else, including diagnostics.
- **Nothing appears, moves or grows on hover**, and only command keys auto-repeat.
- **Nothing on screen changes in a single frame.** Keep nodes and change them; never rebuild what is on screen. A
  colour fades and a move slides by themselves; an arrival goes through `Motion.appear`, a departure through
  `Motion.leave`, changed words through a `Motion.Label`, a replaced region through a `Motion.Stage`. Never animate
  a size inside a followed region (two clocks), never let a box that changes grow a scrollbar (`Ui.still`), never
  `scrollIntoView`. README's *Motion* says how to check it in slow motion.
- **A compile error beats a startup error beats a runtime error.** Prefer the check that fails earliest.
- **No sealed `switch` and no `default` that throws.** Turn it into a method on the type, or a sink.
- **Every build carries the automation sockets**, the release one too: `SchedulerApp` names `AutomationStarter`.
  They start off, and the title bar's plug shows whether they are open and whether anything is or was connected.

## Building and checking

```
mvn compile exec:exec                       # run it
mvn test                                    # unit tests, and the real window tree built headless
native-build.cmd -Pnative package -DskipTests   # the native debug build, target/vexelray-scheduler-app-debug.exe
```

**Native builds go through `native-build.cmd`**, not plain `mvn -Pnative...` (`./native-build.cmd` from Git Bash).
It loads the Visual Studio environment that `rc.exe` and the MSVC `link.exe` need, which a plain shell lacks; under
Git Bash, coreutils' `link.exe` would otherwise be found instead of the linker. README's *A native executable* has
both builds.

**A change to the window is not done until it has been seen.** "It compiles" and "the tests pass" are not evidence
that it looks or behaves right. Drive it with `ottermate`, the stack's automation client (see the stack's
`vexelray-gui/docs/guides/ottermate.md`), and look at the screenshot before saying it works:

```
ottermate --script check.txt --launch mvn.cmd compile exec:exec -Dautomation=0
```

A script is one command per line: `settle`, `tree`, `click <landmark>`, `type <text>`, `key CTRL+S`,
`await <landmark> <text>`, `shot <file.png>`. It stops at the first failure. Screenshots come only from `ottermate`.
Never add a `--capture` flag, a robot or a screen grab to the application.

When a run fails, read `target/logs/vexelray-scheduler-app.log` before changing code. Under automation it is at trace level.

## The two documents

- **`docs/framework-notes.md`**: when you work around something the framework does not do, add an FN entry **at the
  moment you write the workaround**, in the format at the top of that file. That is how the gap gets fixed upstream
  instead of copied into every application.
- **`docs/TODO.md`**: work on this application that is known about and not done. Delete an entry when it is done.

## How to work here

- **One slice at a time**: a change from the model to the screen, with its test and its `ottermate` check. Not
  several features at once.
- **Copy a precedent.** The template's own files, and the applications generated from it (Vex, Calculator,
  MainFrame), have solved most problems once. Find how they did it before inventing a way.
- **For anything that touches `Recipes.java`, threads or the model's shape, propose the change before making it.**
