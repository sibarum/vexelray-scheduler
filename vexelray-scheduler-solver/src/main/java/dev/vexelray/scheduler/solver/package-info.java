/**
 * An interval constraint solver: variables whose values are intervals, constraints between them, and the search
 * that finds an assignment satisfying all of them.
 *
 * <p>So far, the temporal network: time points, the difference constraints between them, and {@link
 * dev.vexelray.scheduler.solver.Solver}, which finds every point's window or a cycle proving there is none. Every
 * answer comes with evidence, and {@link dev.vexelray.scheduler.solver.Checker} reads it; what the checker's
 * acceptance means is proved in Lean, in {@code vexelray-scheduler-proofs}.
 *
 * <p>This module takes no runtime dependencies and uses nothing but the JDK, by rule. It has no {@code Gui} in it, so
 * it can be unit tested without a window, and nothing in it reflects, so it compiles into the application's native
 * image with no metadata of its own.
 */
package dev.vexelray.scheduler.solver;
