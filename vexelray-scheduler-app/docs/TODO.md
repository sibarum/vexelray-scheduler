# TODO

Work on **Scheduler** that is known about and not done. Distinct from
[framework-notes.md](framework-notes.md), which is about the framework rather than about this application —
if the fix belongs upstream, it goes there instead.

Keep an entry short enough that it does not need editing, and delete it when it is done rather than ticking it.

## Next

- [ ] Real data instead of `visits/Sample`: a team, its clinicians and its visits read from somewhere, and the
      application's "today" and "now" taken from the clock rather than fixed at Tue 10:40.
- [ ] The schedules list from the design (North team, South team, Pediatrics, Wound care), and the Week view.
- [ ] Remember the day and the team shown between runs, in the framework's settings store.
- [ ] A fix is checked against its own day only. Once resources are held exclusively in the solver, moving a visit
      should be checked against the whole week as one plan, not by `Review` looking one slot at a time.

## Later

- [ ] Drive times from addresses instead of one figure for the whole team (`Team.travel`).
- [ ] "Right now" estimates arrival ("arrives about 10:52") from the earliest schedule the solver already returns.
- [ ] The native build has not been tried since the editor was replaced.
