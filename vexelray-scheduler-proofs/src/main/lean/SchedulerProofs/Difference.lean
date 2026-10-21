/-!
# Difference constraints

The solver's temporal network is a set of constraints `x + k ≤ y` between start times, in integer ticks.
These are the two facts its shortest-path reasoning rests on: constraints chain by adding their lags, and a
cycle whose lags sum to more than zero cannot be satisfied.
-/

namespace SchedulerProofs

/-- Two difference constraints chain into one whose lag is the sum of theirs. -/
theorem chain {x y z a b : Int} (h₁ : x + a ≤ y) (h₂ : y + b ≤ z) : x + (a + b) ≤ z := by
  omega

/-- A two-constraint cycle with a positive total lag has no solution. -/
theorem cycle_infeasible {x y a b : Int} (h₁ : x + a ≤ y) (h₂ : y + b ≤ x) (hpos : 0 < a + b) : False := by
  omega

end SchedulerProofs
