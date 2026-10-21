# Scheduler

The VexelRay scheduler application.

A VexelRay desktop application, generated from the `vexel-desktop` template, that answers one question for a team
of clinicians making house visits: *what's going on with my schedules?* It shows a day as one lane a clinician, a
bar a visit, and beside it says in plain sentences whether every visit has someone qualified who can get there in
time. When one does not, it says why, and offers ways to fix it that change nothing until you confirm. Each sentence
stands on a checked answer from `vexelray-scheduler-solver`; nobody using it sees a rule, a tag or a constraint.

For now it shows a made-up week of one team (`visits/Sample.java`). The design it follows is the "Scheduler Mockups"
canvas.

## Run it

```
mvn compile exec:exec
```

`exec:exec` rather than `exec:java`, so native access can be enabled for Panama FFM. The window comes up where you
last left it, at 1440x900 the first time, on the sample team's "today". The solver module has to be installed
first (`mvn install -pl vexelray-scheduler-solver` from the parent), or build from the parent.

```
mvn compile exec:exec -Dapp.args=120                      # 120 frames and quit
mvn compile exec:exec -Dautomation=0                      # a driving socket on a free port (see Taking a screenshot)
mvn test                                                  # the review's sentences, and the real tree headless
```

## The demo, in four clicks

1. **Today** (Tuesday) is covered: "Everything today is covered.", who is where right now, and a look at the week:
   Thursday has a visit that needs you.
2. **Click a visit** (Okafor, on Dana's lane): the briefing says why Dana has it — each thing it needs, and getting
   there in time — and who else could take it, and why not. Click it again, or Escape, to put it back.
3. **Thu**, marked amber: Mrs. Alvarez's wound-care visit has nobody who can take it, because the one wound nurse
   working is at a clinic that afternoon and the other is off. Two ways to fix it: move it to Friday at 10 am, when
   Dana is free and the visit may still happen, or ask Priya to come in.
4. **Pick a fix**, then confirm: the day turns covered, and the change can be undone (the button, or Ctrl+Z).

## A native executable

Two builds, made with GraalVM native-image (a GraalVM JDK as `JAVA_HOME`, and Visual Studio with the "Desktop
development with C++" workload):

```
native-build.cmd -Pnative-release package -DskipTests   # target/vexelray-scheduler-app.exe        what ships: no console
native-build.cmd -Pnative package -DskipTests           # target/vexelray-scheduler-app-debug.exe  a console: prints the automation port line
```

Both carry the automation sockets, off until asked for ([Automation](#automation)), so ottermate can drive either.

`native-build.cmd` is `mvn` inside a Visual Studio developer environment: native-image needs the MSVC `link.exe` and
the icon step needs `rc.exe`, and neither is on `PATH` in a plain shell. Under Git Bash it is worse, since coreutils'
own `link.exe` comes first. The script finds `vcvars64.bat` with `vswhere`, loads it, and runs `mvn` with the
arguments it was given (`./native-build.cmd ...` from Git Bash). Plain `mvn` works too from a developer prompt; from
anywhere else the icon step stops and says to use the script.

This program's own native-image metadata is `src/main/resources/META-INF/native-image/.../reachability-metadata.json`,
and it is short because almost nothing is this program's: the libraries register their own, and the processor
registers the icon. If a native run fails on something the JVM run did not (a `Missing...RegistrationError`), run it
on a JVM under `-agentlib:native-image-agent=config-output-dir=...`, do the thing that failed, and add
only the entries that name this program.

## The icon

`src/main/rc/vexelray-scheduler-app.ico` is the application's mark, and it is one file read three ways: the window and its
taskbar button wear it (`@VexelApp(icon = Scheduler.ICON)`; the pom puts it on the class path), and so does the
title bar the framework draws; the `.exe` links it (`src/main/rc/vexelray-scheduler-app.rc`), and so Explorer, a shortcut and a
pinned button show it too. It starts as the framework's "new app" mark, whose source is `vexelray-scheduler-app.svg` beside it.

A second window — a viewer, a settings window — wears the window mark, on the taskbar and in its title bar, with
nothing to write: declare it in a recipe with `windows.named(...)`, and the framework builds its tree and claims it
wearing `shell.windowMark()`. That mark is this same one unless
a `vexelray-scheduler-app-window.ico` sits beside it, which is then found by name and registered for the native image with
the mark. The VexelRay suite's applications ship one: a band of the application's colour over its glyph, so a
second window reads as its own and is still told apart from the main one.

To make it yours, draw a 64x64 SVG and render it at every size Windows asks for, 16 to 256 px, into that `.ico` —
`vex-suite-common`'s `tools/Ico.java` does exactly this, from each size's own rasterisation rather than shrinking the
largest. Replace the file, and the window and the executable change together. An `.ico` or `.png` that is missing is
a compile error; one that does not decode costs the window its mark, logged, and nothing else.

Run from `mvn exec:exec`, the process is `java.exe`, so the window and its taskbar button wear the mark but Windows
groups the button with other Java programs, and pinning it pins Java. The native executable has neither problem.

## Using it

Everything is a click: a day in the header (amber marks a day with something to decide), a visit's bar to have it
explained and again to put it back, a way to fix it to see what would move, and the button under that to make the
change.

| Keys | Does |
| --- | --- |
| Ctrl+Z | undo the last change |
| Escape | put the picked-out visit back |
| Ctrl+= / Ctrl+- / Ctrl+0 | zoom |

## How it is put together

Read these in this order.

| File | What belongs in it |
| --- | --- |
| `Recipes.java` | **what this application builds.** One `@Provides` method per part; `SchedulerAppWiring`, which builds them in order, is generated from it while the project compiles. A part's phase is the latest phase of anything it takes, so there is none to declare. New parts go here. |
| `Doc.java`, `Model.java` | the state — the schedule, the day shown, the visit picked out, the fix being considered, and every change made with the schedule it replaced — as one immutable value, changed only by functions of the current value, committed through atchung's `State`. |
| `Ui.java` | the window: the header and its days, the status line. Holds no state; `show(Doc)` reviews the day once and writes everything derived from it. |
| `Schedule.java` | the lanes: a bar a visit, floated by percentage of the day. A day is a page, kept while it is shown and changed in place. |
| `Briefing.java` | "What's going on": the day in a sentence; under it right now and later this week, or the visit picked out explained, or the visit that needs you and the ways to fix it. |
| `Motion.java` | how anything on screen changes, which is never in one frame. See *Motion* below. |
| `visits/` | **code with no `Gui` in it**, in a sub-package with public types: clinicians, visits, needs, the sample team, and `Review`, which asks the solver about a day and says what it answered in sentences. Testable in milliseconds. |

`Scheduler.java` is the entry point and the constants, and nothing else. The application edge — input, the
clipboard, window memory, the clock, the frame loop with its wakes and its pacing, the dialogs, the command line and
the shutdown order — is `vexelray-framework`'s. `Look.java`, `Type.java` and `Landmarks.java` are the three
vocabularies: colour, size, and the names an automation script is allowed to depend on.

### Phases

A phase is a **correctness** constraint rather than a scheduling detail, and having them as a type is what makes the
constraints structural instead of remembered:

| phase | what exists by then |
| --- | --- |
| `CONFIG` | the settings store and the look — values, before there is a `Gui` to apply them to |
| `MODEL` | what the application knows, before there is anything to draw it with |
| `GUI` | the `Gui` and the clock; the theme applied and the zoom range set, before the first widget |
| `TREE` | the widgets: the first phase a part is handed the `Gui`, so anything taking it lands here. Buildable with no window, which is what lets a test build it with no GPU |
| `WINDOW` | the device and the window handle. Main-thread from here on |
| `ATTACH` | anything that needed the handle: chrome controls, the close gate, the driving socket |

Open `target/generated-sources/annotations/.../SchedulerAppWiring.java` after a build to see which part landed where.

### Threading, in one paragraph

The GUI loop runs on its own thread. Every handler — a click, a key — runs on a worker. Workers meet at `Model`,
whose compare-and-set puts concurrent changes in an order. A listener registered with `Model.onChange` fires on the
committing thread, one delivery at a time; `Ui.show` runs the review there (the solver's work for a day is a few
milliseconds) and brings the lanes and the briefing up to date inside `Gui.batch`, which is correct because a prop
written off the GUI thread is applied by the next drain. Motion runs on the clock's timeline and writes props the same
way. The only locks are `Motion`'s, each guarding one animated value, and nothing commits to the model while holding
one. Nothing inside the frame loop reads the model.

### Motion

**Nothing on screen changes in a single frame.** Every change the window makes is one of five, and `Motion` has an
instrument for each:

| A change | How it is drawn |
| --- | --- |
| a colour: a bar picked out, a day chip, a fix chosen | blended in Oklab by the stack's `Fades`, for every node, with nothing to call |
| a node moved: a bar to another lane, a card pushed down | slid from where it was drawn by the stack's `Transitions` (`Motion.follow`) |
| a node arriving: a bar, the confirmation card, the "Needs someone" lane | `Motion.appear`: up from nothing, rising a little |
| a node going | `Motion.leave`: down to nothing first, then removed; whatever was below it slides up |
| a label's words | a `Motion.Label`: dips out, changes, comes back |

A region whose contents are replaced whole — the day's lanes, the briefing's page — is a `Motion.Stage`: the old
page fades out where it stands, then the new one fades in. The zoom keys glide. The focus ring's fade and the wheel's
glide are the framework's own.

Three things are designed out rather than animated, because the stack draws them in one frame. **Scrollbars**: one
appears the moment something overflows and narrows what it scrolls, so the lanes never scroll and every box that
can be smaller than its contents is `Ui.still` (no scrolling, clipped). **Scrolling into view**, which jumps, so
nothing scrolls by itself. **Border widths**, so a picked-out bar is marked by colour alone.

**Checking it.** A transition is over in a fifth of a second, too fast for a screenshot to catch. Slow everything
down, photograph a burst, and look for a step between two frames that is far bigger than the steps either side:

```
mvn compile exec:exec -Dautomation=0 "-Dapp.jvmArgs=-Dscheduler.slowmo=12"
```

Slide steps run in a long series of similar sizes; a jump stands alone. The two things slow motion does not slow
are the framework's: the focus ring (120 ms) and the wheel.

### How a day is reviewed

`visits/Review` turns a day into questions the solver can answer, and its answers back into sentences:

- **Can this clinician fit this visit in?** Their visits and time away that day, at their booked times, with the
  team's drive between each and the next, are a temporal network. No schedule means a cycle, and the constraint in
  it that touches the visit names what is in the way: "is with I. Pena at 10:15", "is at the Pediatrics clinic".
- **May this clinician take it?** A `Plan`: a visit's needs are `require` rules, a day off and a full diary are
  `forbid` rules. `Plan.excluding` gives back the rules that rule someone out, which become "isn't certified in
  wound care" and "is off Thursday".
- **Who could take a visit nobody has?** The `Assigner`, which finds someone or returns the shortage that proves
  there is nobody.
- **What would fix it?** Someone eligible to give it to; the first time in the visit's window when someone working
  can take it, nothing else moving; anyone whose only reason is a day off, asked to come in.

Everything the solver returns has been through its checker first, so a sentence is never a guess.

## The stack

Everything below is installed locally rather than downloaded, so `mvn install` in each of those projects has to have
happened at least once. If the build cannot resolve them, that is why.

| what | why it is here |
| --- | --- |
| `vexelray-framework-shell`, `-automation`, `-processor` | the application edge, the driving socket, and the processor that writes the wiring |
| `vexelray-gui-widget` | the widgets: `Tabs`, `TreeView`, `TextField`, `SplitPane`, `StatusBar`, `Cues` |
| `vexelray-gui-krono` | the clock: every transition and cue |
| `vexelray-gui-automation` | driving the running app the way a person does — a debugging instrument |
| `tactroller-atchung` | keyboard and pointer input onto an atchung bus (brings `atchung-core`) |
| `tactroller-clipboard` | the OS clipboard, with no input-subsystem coupling |
| `vexelray-os-*`, `tactroller-*` | picked by an OS-activated profile; exactly one activates |

## Automation

Scheduler can be driven and read from outside, by a script, a macro or an agent, through the framework's two loopback
sockets: **remote control** (the keys, clicks and reads a person has) and **diagnostics** (the flight recorder, the
log, the Inspector). Both are in every build, the release one too, and both start off.

- **Switched on by hand**: the plug in the title bar opens the switches, one per socket, each with a port (blank for
  a free one).
- **Switched on at launch**, for an agent that means to: `--automation=0` (both, on a free port),
  `--automation.control=<port>`, `--automation.diagnostics=<port>`, or `VEXELRAY_AUTOMATION=0` in the environment.
- **Not there at all**: `--automation.available=none` (or `control`, or `diagnostics`), or
  `VEXELRAY_AUTOMATION_AVAILABLE` in the environment. A socket that is not available cannot be switched on by
  anything, and with neither available the plug is gone.

Whatever turned it on, **the plug's lamps say what each socket is doing**, remote control's (red) left of the plug and
diagnostics' (blue) right:

| Lamp | Means |
| --- | --- |
| none | off, and nothing has connected to it this session |
| ring | open, and nothing has connected yet |
| filled ring | open, and something is connected now |
| ring with a grey dot | open; something was connected and has gone |
| grey dot | off, after something had connected to it this session |

The plug's tooltip and the switches' window say the same in words, with the port and how many are connected.

## Taking a screenshot

Screenshots are `ottermate`'s, and only its: it photographs the running window on the application's own device, so
the picture is right about content as well as chrome. There is no `--capture` flag in this project on purpose.

```
ottermate shot out.png --launch mvn compile exec:exec -Dautomation=0
```

Say what the picture is *of* with options, and `ottermate` sets it first and stops if it cannot. Everything before
`--launch` is ottermate's; everything after it is the application's command line (on Windows, `mvn.cmd`):

```
ottermate --zoom 1.5 --dpi 2 --size 1600x900 shot big.png --launch ...   # pixels
ottermate --size 60emx34em shot smallest.png --launch ...                # the minimum, in em
```

The landmarks in `Landmarks.java` are the names a script uses: `day.thu`, `visit.okafor`, `fix.1`, `confirm`,
`undo`, and `headline`, whose name is the briefing's first sentence, so `await headline needs` waits for a day with
something to decide and `await headline covered` for one without. The demo's whole path, as a script:

```
click day.thu
await headline needs
click fix.1
click confirm
await headline covered
```

`ottermate help` lists every verb.

## Logging

The framework configures logging for you, before anything else runs, and the defaults follow what kind of run it is:

| Run | Console | File |
| --- | --- | --- |
| a test | warnings | none |
| from your checkout (`mvn exec:exec`) | info | `target/logs/vexelray-scheduler-app.log`, debug |
| driven by `ottermate` or `-Dautomation` | debug | `…/vexelray-scheduler-app.log`, **trace**, with a probe trace beside it |
| a native image | warnings | `~/.vexelray-scheduler-app/logs/vexelray-scheduler-app.log`, info |

Write to it with a logger, not `System.out`, named for its area of this application:

```java
private static final Log LOG = Log.of("vexelray-scheduler-app.files");   // sibarum.probe.Log
LOG.warn("could not save {}", path, exception);               // the exception goes last; its stack reaches the file
```

`--log=debug` (or `off`) changes the level for one launch, and `-Dlog.level.vexelray-scheduler-app.files=trace` raises one
logger. Levels, settings and the rules for what belongs at which level are in `atchung/docs/logging.md`.

## The two documents

`docs/framework-notes.md` and `docs/TODO.md` start nearly empty and are meant to be filled in as you go. See the note
at the top of the first one — it is the more important of the two.
