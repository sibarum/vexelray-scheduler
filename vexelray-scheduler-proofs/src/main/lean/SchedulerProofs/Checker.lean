import SchedulerProofs.Network

/-!
# The checkers, and what their acceptance means

The solver's answers are never trusted on their own: each comes with evidence, and one of these two checks
reads it. `Checker` in `vexelray-scheduler-solver` is the same two functions in Java, line for line, except
that the Java cycle names its constraints by index into the network, and resolving those indices is its first
step.

- A schedule is checked by testing every constraint. Accepted means it meets the network (`checkSchedule_iff`).
- Infeasibility is checked from a cycle: constraints of the network that lead from a point back to itself with a
  positive total lag. Accepted means no schedule meets the network (`checkCycle_sound`).
-/

namespace SchedulerProofs

/-- Every constraint holds under `s`. -/
def checkSchedule (cs : List Constraint) (s : Schedule) : Bool :=
  cs.all fun c => decide (s c.src + c.lag ≤ s c.dst)

/-- The schedule checker accepts exactly the schedules that meet the network. -/
theorem checkSchedule_iff (cs : List Constraint) (s : Schedule) :
    checkSchedule cs s = true ↔ Satisfies cs s := by
  simp [checkSchedule, Satisfies, Constraint.Holds]

/-- The cycle is non-empty, every one of its constraints is in the network, it leads from its first
constraint's source back to that point, and its lags sum to more than zero. -/
def checkCycle (cs : List Constraint) : List Constraint → Bool
  | [] => false
  | c :: p =>
    (c :: p).all (fun d => cs.contains d) && walks c.src (c :: p) c.src && decide (0 < lagSum (c :: p))

/-- A cycle the checker accepts proves the network infeasible. A schedule meeting it would put the cycle's
first point strictly after itself. -/
theorem checkCycle_sound (cs cyc : List Constraint) (h : checkCycle cs cyc = true) : ¬ Feasible cs := by
  intro ⟨s, hs⟩
  match cyc, h with
  | [], h => simp [checkCycle] at h
  | c :: p, h =>
    simp only [checkCycle, Bool.and_eq_true, decide_eq_true_eq] at h
    obtain ⟨⟨hmem, hwalk⟩, hpos⟩ := h
    have hall : ∀ d ∈ c :: p, d.Holds s := by
      intro d hd
      apply hs
      have := List.all_eq_true.mp hmem d hd
      simpa using this
    have := walk_bound s (c :: p) c.src c.src hwalk hall
    omega

end SchedulerProofs
