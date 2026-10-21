/-!
# The temporal network

The model the solver's answers are about. A network is a list of difference constraints between time points,
each saying that one point is at least `lag` ticks after another. A schedule gives every point a time. Release
times, deadlines and the horizon are constraints against an origin point like any other, so nothing else is
needed to state what a schedule must satisfy.

This is the spec. `Checker.lean` proves that what the checkers accept means what this file says.
-/

namespace SchedulerProofs

/-- `dst` is at least `lag` ticks after `src`: `s src + lag ≤ s dst`. A negative lag bounds `dst` from above
relative to `src`, which is how a deadline is written. -/
structure Constraint where
  src : Nat
  dst : Nat
  lag : Int
deriving DecidableEq, Repr

/-- A time for every point, in integer ticks. -/
abbrev Schedule := Nat → Int

/-- `s` meets `c`. -/
def Constraint.Holds (s : Schedule) (c : Constraint) : Prop :=
  s c.src + c.lag ≤ s c.dst

/-- `s` meets every constraint of the network. -/
def Satisfies (cs : List Constraint) (s : Schedule) : Prop :=
  ∀ c ∈ cs, c.Holds s

/-- Some schedule meets the network. -/
def Feasible (cs : List Constraint) : Prop :=
  ∃ s, Satisfies cs s

/-- The total lag along a sequence of constraints. -/
def lagSum : List Constraint → Int
  | [] => 0
  | c :: p => c.lag + lagSum p

/-- The constraints `p`, in order, lead from point `u` to point `v`: each one starts where the last ended. -/
def walks : Nat → List Constraint → Nat → Bool
  | u, [], v => u == v
  | u, c :: p, v => c.src == u && walks c.dst p v

/-- Along a walk the schedule climbs by at least the lags: a schedule meeting every constraint of a walk from
`u` to `v` puts `v` at least the walk's total lag after `u`. -/
theorem walk_bound (s : Schedule) :
    ∀ (p : List Constraint) (u v : Nat), walks u p v = true → (∀ c ∈ p, c.Holds s) →
      s u + lagSum p ≤ s v := by
  intro p
  induction p with
  | nil =>
    intro u v h _
    simp [walks] at h
    simp [lagSum, h]
  | cons c p ih =>
    intro u v h hp
    simp only [walks, Bool.and_eq_true, beq_iff_eq] at h
    obtain ⟨hsrc, hrest⟩ := h
    have hc : c.Holds s := hp c (List.mem_cons_self ..)
    have hp' := ih c.dst v hrest (fun d hd => hp d (List.mem_cons_of_mem _ hd))
    simp only [Constraint.Holds] at hc
    simp only [lagSum]
    subst hsrc
    omega

end SchedulerProofs
