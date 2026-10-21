/-!
# Assigning resources by tags

Jobs, resources and tags. A job has one or more demands, each filled by one resource, and the demands of a job
take different resources. Tags are on demands (a demand carries its job's tags and any of its own) and on
resources, and rules over pairs of tags decide which resource a demand may take:

- `forbid a b`: a demand tagged `a` may not take a resource tagged `b`.
- `require a b`: a demand tagged `a` may only take a resource tagged `b`.

Tags are numbers here; the Java names them by strings, which only changes how two are compared.

The solver's evidence is an assignment, or a shortage: demands of one job that, between them, have fewer eligible
resources than there are demands. `checkAssignment_iff` and `checkShortage_sound` say what the checkers' acceptance
means. The second is a pigeonhole argument, `pigeonhole`.
-/

namespace SchedulerProofs

/-- A rule over a demand's tags and a resource's tags. -/
inductive Rule where
  | forbid (demandTag resourceTag : Nat)
  | require (demandTag resourceTag : Nat)
deriving DecidableEq, Repr

/-- The rule rules out a demand with tags `d` taking a resource with tags `r`. -/
def Rule.excludes : Rule → List Nat → List Nat → Bool
  | .forbid a b, d, r => d.contains a && r.contains b
  | .require a b, d, r => d.contains a && !r.contains b

/-- No rule rules it out. -/
def eligible (rules : List Rule) (d r : List Nat) : Bool :=
  rules.all fun x => !x.excludes d r

/-- What an assignment is checked against. Resources are numbered `0 … resources - 1`; a job is the list of its
demands. -/
structure Problem where
  rules : List Rule
  demandTags : Nat → List Nat
  resourceTags : Nat → List Nat
  resources : Nat
  jobs : List (List Nat)

/-- `a` gives every demand of every job a resource that exists and that it is eligible for, and gives the demands
of one job different resources. -/
def Valid (p : Problem) (a : Nat → Nat) : Prop :=
  ∀ J ∈ p.jobs,
    (∀ d ∈ J, a d < p.resources ∧ eligible p.rules (p.demandTags d) (p.resourceTags (a d)) = true) ∧
    (∀ d₁ ∈ J, ∀ d₂ ∈ J, a d₁ = a d₂ → d₁ = d₂)

/-- Some assignment is valid. -/
def Assignable (p : Problem) : Prop :=
  ∃ a, Valid p a

/-- The assignment checker: every demand's resource exists and is eligible, and no two demands of a job share one. -/
def checkAssignment (p : Problem) (a : Nat → Nat) : Bool :=
  p.jobs.all fun J =>
    J.all (fun d => decide (a d < p.resources) && eligible p.rules (p.demandTags d) (p.resourceTags (a d))) &&
    J.all (fun d₁ => J.all fun d₂ => !(a d₁ == a d₂) || d₁ == d₂)

/-- The assignment checker accepts exactly the valid assignments. -/
theorem checkAssignment_iff (p : Problem) (a : Nat → Nat) : checkAssignment p a = true ↔ Valid p a := by
  simp only [checkAssignment, Valid, List.all_eq_true, Bool.and_eq_true, decide_eq_true_eq, Bool.or_eq_true,
    Bool.not_eq_true', beq_eq_false_iff_ne, beq_iff_eq]
  constructor
  · intro h J hJ
    obtain ⟨h₁, h₂⟩ := h J hJ
    refine ⟨h₁, fun d₁ hd₁ d₂ hd₂ he => ?_⟩
    rcases h₂ d₁ hd₁ d₂ hd₂ with hne | heq
    · exact absurd he hne
    · exact heq
  · intro h J hJ
    obtain ⟨h₁, h₂⟩ := h J hJ
    refine ⟨h₁, fun d₁ hd₁ d₂ hd₂ => ?_⟩
    by_cases he : a d₁ = a d₂
    · exact Or.inr (h₂ d₁ hd₁ d₂ hd₂ he)
    · exact Or.inl he

/-- No element twice. -/
def distinct : List Nat → Bool
  | [] => true
  | x :: xs => !xs.contains x && distinct xs

theorem distinct_nodup : ∀ l : List Nat, distinct l = true → l.Nodup
  | [], _ => List.nodup_nil
  | x :: xs, h => by
    simp only [distinct, Bool.and_eq_true, Bool.not_eq_true'] at h
    have hx : x ∉ xs := fun hm => by
      have hc := List.contains_iff_mem.mpr hm
      rw [h.1] at hc
      exact Bool.false_ne_true hc
    exact List.nodup_cons.mpr ⟨hx, distinct_nodup xs h.2⟩

/-- Pigeonhole: a list without repeats, sent one-to-one into `N`, is no longer than `N`. -/
theorem pigeonhole (a : Nat → Nat) :
    ∀ (S N : List Nat), S.Nodup → (∀ x ∈ S, a x ∈ N) →
      (∀ x ∈ S, ∀ y ∈ S, a x = a y → x = y) → S.length ≤ N.length := by
  intro S
  induction S with
  | nil => intro N _ _ _; simp
  | cons x xs ih =>
    intro N hnd hin hinj
    have hx : a x ∈ N := hin x (List.mem_cons_self ..)
    have hnd' := List.nodup_cons.mp hnd
    have hrest : ∀ y ∈ xs, a y ∈ N.erase (a x) := by
      intro y hy
      have hne : a y ≠ a x := by
        intro he
        have := hinj y (List.mem_cons_of_mem _ hy) x (List.mem_cons_self ..) he
        subst this
        exact hnd'.1 hy
      exact (List.mem_erase_of_ne hne).mpr (hin y (List.mem_cons_of_mem _ hy))
    have hle := ih (N.erase (a x)) hnd'.2 hrest
      (fun u hu v hv => hinj u (List.mem_cons_of_mem _ hu) v (List.mem_cons_of_mem _ hv))
    rw [List.length_erase_of_mem hx] at hle
    have hpos : 0 < N.length := List.length_pos_of_mem hx
    simp only [List.length_cons]
    omega

/-- The shortage checker. `J` is one of the jobs, `S` are some of its demands, without repeats, and `N` holds every
resource any of them is eligible for, and is shorter than `S`. -/
def checkShortage (p : Problem) (J S N : List Nat) : Bool :=
  p.jobs.contains J && S.all (fun d => J.contains d) && distinct S &&
  S.all (fun d => (List.range p.resources).all fun r =>
    !eligible p.rules (p.demandTags d) (p.resourceTags r) || N.contains r) &&
  decide (N.length < S.length)

/-- A shortage the checker accepts proves that no assignment is valid. Every demand in it would need its own
eligible resource, all of them in `N`, and there are not enough. -/
theorem checkShortage_sound (p : Problem) (J S N : List Nat) (h : checkShortage p J S N = true) :
    ¬ Assignable p := by
  intro ⟨a, ha⟩
  simp only [checkShortage, Bool.and_eq_true, List.all_eq_true, List.contains_iff_mem, decide_eq_true_eq,
    Bool.or_eq_true, Bool.not_eq_true'] at h
  obtain ⟨⟨⟨⟨hJ, hSJ⟩, hdist⟩, hcover⟩, hshort⟩ := h
  obtain ⟨helig, hinj⟩ := ha J hJ
  have hin : ∀ d ∈ S, a d ∈ N := by
    intro d hd
    obtain ⟨hlt, hel⟩ := helig d (hSJ d hd)
    rcases hcover d hd (a d) (List.mem_range.mpr hlt) with hno | hyes
    · rw [hel] at hno; exact absurd hno (by decide)
    · exact hyes
  have := pigeonhole a S N (distinct_nodup S hdist) hin
    (fun x hx y hy he => hinj x (hSJ x hx) y (hSJ y hy) he)
  omega

end SchedulerProofs
