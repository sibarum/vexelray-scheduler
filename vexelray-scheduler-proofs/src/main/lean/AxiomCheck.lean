import Lean
import SchedulerProofs

/-!
# Every declaration uses only the standard axioms

Run with `lake env lean AxiomCheck.lean` after `lake build`; `LeanProofsTest` does both. It walks every
declaration in every `SchedulerProofs` module -- not a chosen list -- and fails if any depends on an axiom
other than `propext`, `Classical.choice` and `Quot.sound`. `sorry` is the axiom `sorryAx` and
`native_decide` is `Lean.ofReduceBool`, so either would fail it too. The same check as the sibling proof
repositories', which get `import Lean` through Mathlib; this project has no Mathlib, so it imports it itself.
-/

open Lean Elab Command

/-- The three axioms of Lean's standard foundations. -/
def standardAxioms : List Name := [``propext, ``Classical.choice, ``Quot.sound]

/-- Check every declaration of every module under `pfx`. -/
elab "#check_axioms_under " pfx:ident : command => do
  let env ← getEnv
  let mut modules : Nat := 0
  let mut decls : Nat := 0
  let mut used : NameSet := {}
  let mut bad : Array (Name × Array Name) := #[]
  for h : i in [:env.header.moduleNames.size] do
    let mod := env.header.moduleNames[i]
    unless pfx.getId.isPrefixOf mod do continue
    modules := modules + 1
    for c in env.header.moduleData[i]!.constNames do
      decls := decls + 1
      let axs ← collectAxioms c
      used := axs.foldl NameSet.insert used
      let extra := axs.filter (· ∉ standardAxioms)
      unless extra.isEmpty do bad := bad.push (c, extra)
  if modules = 0 then
    throwError "no modules under {pfx.getId}: nothing was checked"
  unless bad.isEmpty do
    throwError m!"{bad.size} declarations use a non-standard axiom:\n" ++
      m!"{bad.toList.map fun (c, axs) => m!"{c}: {axs.toList}"}"
  logInfo m!"{decls} declarations in {modules} modules; axioms used: {used.toList}"

#check_axioms_under SchedulerProofs
