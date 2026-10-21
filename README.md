# vexelray-scheduler

A multi-module Maven build. The parent `pom.xml` holds the Java release and the VexelRay stack's versions; each
module names its own dependencies and plugins.

| Module | What it is |
| --- | --- |
| [vexelray-scheduler-app](vexelray-scheduler-app) | The desktop application. Its [README](vexelray-scheduler-app/README.md) and [CLAUDE.md](vexelray-scheduler-app/CLAUDE.md) are the guide to working in it |
| [vexelray-scheduler-solver](vexelray-scheduler-solver) | The interval constraint solver. JDK-only, by rule: no graphics, no stack, no reflection |
| [vexelray-scheduler-proofs](vexelray-scheduler-proofs) | Lean proofs about the solver, in `src/main/lean`. Its one test runs `lake build` and an axiom check, so it needs [elan](https://github.com/leanprover/elan) installed |

```bash
mvn install
```

```bash
cd vexelray-scheduler-app && mvn compile exec:exec
```

## Where the app module came from

It was generated from `vexelray-framework`'s `vexel-desktop` template, then given this build as its parent (its
`<groupId>`, `<version>` and the shared properties were removed in favour of the parent's):

```bash
java -cp ../vexelray-framework/vexelray-framework-template/target/vexelray-framework-template-0.1.2-SNAPSHOT.jar dev.vexelray.framework.template.Generate --in . --name vexelray-scheduler-app --group dev.vexelray.scheduler --packageName dev.vexelray.scheduler.app --className Scheduler --title Scheduler --summary "The VexelRay scheduler application."
```

Another application module is made the same way: generate it here, add a `<parent>` to its pom, and list it in the
parent's `<modules>`.
