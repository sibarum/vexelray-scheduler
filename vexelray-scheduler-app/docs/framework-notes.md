# Framework notes

Findings about VexelRay, vexelray-gui, Kronometer, tactroller and atchung that came out of building
**Scheduler** — things the framework does not have, does not document, or does in a way that cost time to
discover.

**Why this file exists.** An application built on a framework is the only place its gaps are visible, and they
are visible exactly once: at the moment they are worked around. A workaround with no note beside it becomes a
piece of application code nobody can tell from a design decision, and the framework never hears about it. So
the rule is to write the note *when the workaround is written*, not in a retrospective, and to write down what
was measured rather than what was assumed.

**Before writing a workaround, ask whether it is a component.** If the answer is "every project on this
framework will write these same four calls" — that is a finding, and the fix belongs upstream. Say so here
with that framing, so the retrospective has a candidate rather than a complaint.

## How to write one

    ## FN-1 · One line saying what is missing 🔬
    
    What was wanted, what the framework offers instead, and what was done about it.
    Then: what it costs, and what the framework could do about it.

The markers are a filter, not decoration:

| | |
| --- | --- |
| 🔬 | a framework gap — something upstream could fix |
| 💡 | an idea, not yet a finding |
| 📋 | carried over: needs re-verifying against a newer build before it is repeated |

## Findings

Asked of this application: nothing on screen may change in a single frame. The stack's `Fades` and `Transitions`
cover colour and movement completely and cost one line each. Everything below is what was left over.

## FN-1 · A node's arrival and removal cut 🔬

`LayoutMotion` reports nothing for a node's first appearance (by design: it came from nowhere), and `remove()` takes a
node out in the frame it lands. So every list that grows or shrinks pops. `Motion.appear` (opacity and `translate`
from nothing, on `KronoGui.ramp`) and `Motion.leave` (opacity to nothing, then `remove()`) are the workaround, with a
map of who is fading so a departure starts from where an arrival got to. Every application that wants the
`Transitions` look will write these same two methods: a framework `EnterMotion`/`ExitMotion` beside `LayoutMotion`,
enrolled the same way (`followSubtree`), and a `remove()` that waits for the exit, would make the whole class free.

## FN-2 · A label's words change in one frame 🔬

`Fades` blends a text colour but not the text. `Motion.Label` dips the node's opacity out, swaps the words, and
brings it back, interruption-safe. Same for `StatusBar.text` (faded through its slot's node) and `Button.label`. A
text crossfade in the renderer — old glyphs out, new in, in the node's own box — would be better than a dip, and is
only possible upstream. `TitleBar.title` is the one it cannot reach (the framework draws it), so the title no longer
names the day.

## FN-3 · Scrollbars appear in one frame, and every node scrolls by default 🔬

`Node.scroll` defaults to on for both axes, so any box whose content overflows, even for a frame of an animation,
grows a scrollbar, and the scrollbar takes width, so its content reflows in the same frame. The first sight of this
was a little scrollbar pill inside every visit bar too short for its name. Worked around with `Ui.still` (scrolling
off, clipped) on every box that can be smaller than its content, and by laying out the lanes so they never scroll.
Wanted: scrolling off by default, or a stable scrollbar gutter, or scrollbars that overlay and fade in.

## FN-4 · `scrollIntoView` jumps 🔬

Documented ("at once, not glided"), and right for its stated use. But there is nothing that glides a reveal, and
reading a node's rect to glide `scrollTo` by hand means a second clock against layout. This application stopped
revealing anything: the visit explanation moved from under the lanes, where it needed scrolling to, into the
briefing. A `reveal(Dur)` that rides the wheel's existing `ScrollGlide` would answer it.

## FN-5 · A float is clamped inside its parent, so a lifted-out page is squeezed

A page crossfade lifts the outgoing page out of the flow (`floatAt(0, 0)`) so the incoming one can take its place.
The float is clamped to its parent, which by then is only as tall as the incoming page, so a longer outgoing page
was squeezed and, scrolling being on by default (FN-3), grew a scrollbar for a frame. Documented on `floatAt`; it
cost a slow-motion run to find. `Motion.Stage` needs its region to fill what its parent leaves. An unclamped float,
or a stage component, would remove the trap.

## FN-6 · The zoom keys step in one frame 🔬

`Gui.zoomIn`/`zoomOut` set the factor at once, which re-lays the whole window between two frames. The keys here
drive `Gui.zoom(float)` from a glide instead, reading the framework's own value at rest since it restores and clamps
it. A `zoomGlide` beside `scrollGlide` and `focusFade` would fit the framework's existing pattern exactly.

## FN-7 · There is no slow motion for the whole window 💡

To prove nothing jumps, transitions have to be photographed mid-flight, which at 140 ms means slowing them down.
`Motion` reads `-Dscheduler.slowmo` and stretches its own durations, but the framework's own fades (the focus ring,
the wheel) keep real time, so they show up in a slow-motion burst as steps that are not there at full speed. A
framework-wide time scale on the clock, and an ottermate verb to step frames, would make "no frame jumps" a check a
script can make rather than one a person has to read.

