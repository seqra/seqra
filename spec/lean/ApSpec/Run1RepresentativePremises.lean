import Std

/-
Scope: a proposed run-1 semantic approximation, not an implementation proof.
A complete cube binding is required before its support is collapsed. Each
binding keeps one original initial, selected by a fixed policy order. ZERO is
represented by empty support. Positive Horn consequence inclusion is proved
for fixed discovered clauses. Neither backward guidance preservation, negative
conditions, field transfers, call-summary completeness, nor arbitrary removal
of ND results follows from this model.
-/
namespace Run1RepresentativePremises

inductive Kind where
  | argument | thisObject | otherNonStatic | staticField | classPoint
  deriving DecidableEq, Repr

def Kind.priority : Kind → Nat
  | .argument => 0 | .thisObject => 1 | .otherNonStatic => 2
  | .staticField => 3 | .classPoint => 4

/-- `bound` bounds stable IDs, not arrival indices. The order is fixed. -/
structure Original (bound : Nat) where
  kind : Kind
  stableId : Fin bound
  deriving DecidableEq, Repr

def code (x : Original n) : Nat := x.kind.priority * n + x.stableId.val

theorem preferred_kind_first {a b : Original n}
    (h : a.kind.priority < b.kind.priority) : code a < code b := by
  cases a with
  | mk ak ai =>
    cases b with
    | mk bk bi =>
      have ha := ai.isLt
      have hb := bi.isLt
      cases ak <;> cases bk <;> simp_all [code, Kind.priority] <;> omega

theorem kind_priority_injective {a b : Kind} (h : a.priority = b.priority) : a = b := by
  cases a <;> cases b <;> simp [Kind.priority] at h ⊢

theorem code_injective {a b : Original n} (h : code a = code b) : a = b := by
  have hp : a.kind.priority = b.kind.priority := by
    by_cases hab : a.kind.priority < b.kind.priority
    · have hc := preferred_kind_first hab
      omega
    · by_cases hba : b.kind.priority < a.kind.priority
      · have hc := preferred_kind_first hba
        omega
      · omega
  have hk := kind_priority_injective hp
  cases a with
  | mk ak ai =>
    cases b with
    | mk bk bi =>
      change ak = bk at hk
      cases hk
      change ak.priority * n + ai.val = ak.priority * n + bi.val at h
      have hi : ai = bi := Fin.ext (Nat.add_left_cancel h)
      cases hi
      rfl

def pick (a b : Original n) : Original n := if code a ≤ code b then a else b

theorem pick_member (a b : Original n) : pick a b = a ∨ pick a b = b := by
  unfold pick
  split <;> simp

theorem pick_le_left (a b : Original n) : code (pick a b) ≤ code a := by
  unfold pick
  split
  · exact Nat.le_refl _
  · omega

theorem pick_le_right (a b : Original n) : code (pick a b) ≤ code b := by
  unfold pick
  split
  · assumption
  · exact Nat.le_refl _

def choose (a : Original n) : List (Original n) → Original n
  | [] => a
  | b :: bs => pick a (choose b bs)

theorem choose_member (a : Original n) (xs : List (Original n)) :
    choose a xs ∈ a :: xs := by
  induction xs generalizing a with
  | nil => simp [choose]
  | cons b bs ih =>
    simp only [choose]
    cases pick_member a (choose b bs) with
    | inl h => rw [h]; exact List.mem_cons_self
    | inr h => rw [h]; exact List.mem_cons_of_mem a (ih b)

theorem choose_le_member (a : Original n) (xs : List (Original n))
    (x : Original n) (hx : x ∈ a :: xs) : code (choose a xs) ≤ code x := by
  induction xs generalizing a with
  | nil =>
    have e : x = a := by simpa using hx
    subst x
    exact Nat.le_refl _
  | cons b bs ih =>
    simp only [choose]
    cases List.mem_cons.mp hx with
    | inl h => subst x; exact pick_le_left _ _
    | inr h => exact Nat.le_trans (pick_le_right _ _) (ih b h)

/-- Enumeration/arrival order of one fixed complete binding cannot change its choice. -/
theorem choose_permutation {a b : Original n} {xs ys : List (Original n)}
    (h : (a :: xs).Perm (b :: ys)) : choose a xs = choose b ys := by
  apply code_injective
  apply Nat.le_antisymm
  · exact choose_le_member a xs _ (h.mem_iff.mpr (choose_member b ys))
  · exact choose_le_member b ys _ (h.mem_iff.mp (choose_member a xs))

/-- Duplicate removal also preserves selection when the complete member set is unchanged. -/
theorem choose_same_members {a b : Original n} {xs ys : List (Original n)}
    (h : ∀ x, x ∈ a :: xs ↔ x ∈ b :: ys) : choose a xs = choose b ys := by
  apply code_injective
  apply Nat.le_antisymm
  · exact choose_le_member a xs _ ((h _).mpr (choose_member b ys))
  · exact choose_le_member b ys _ ((h _).mp (choose_member a xs))

/-- Empty support is ZERO. ZERO is neutral when another original is present. -/
def representative : List (Original n) → Option (Original n)
  | [] => none
  | a :: xs => some (choose a xs)

theorem representative_member {support : List (Original n)} {o : Original n}
    (h : representative support = some o) : o ∈ support := by
  cases support with
  | nil => simp [representative] at h
  | cons a xs =>
    have e : choose a xs = o := Option.some.inj h
    rw [← e]
    exact choose_member a xs

theorem representative_zero_iff (support : List (Original n)) :
    representative support = none ↔ support = [] := by
  cases support <;> simp [representative]

theorem zero_neutral (support : List (Original n)) :
    representative ([] ++ support) = representative support ∧
      representative (support ++ []) = representative support := by simp

def enabled (cube : List Nat) (available : Nat → Bool) : Bool := cube.all available

def emit (cube : List Nat) (available : Nat → Bool) (support : List (Original n)) :
    Option (Option (Original n)) :=
  if enabled cube available then some (representative support) else none

/-- No representative is emitted until every literal in the cube is available. -/
theorem emission_requires_complete_cube {cube : List Nat} {available : Nat → Bool}
    {support : List (Original n)} {label : Option (Original n)}
    (h : emit cube available support = some label) :
    ∀ literal ∈ cube, available literal = true := by
  unfold emit at h
  split at h
  · rename_i ready
    exact List.all_eq_true.mp ready
  · contradiction

theorem complete_cube_emits {cube : List Nat} {available : Nat → Bool}
    (support : List (Original n)) (h : ∀ literal ∈ cube, available literal = true) :
    emit cube available support = some (representative support) := by
  have ready : enabled cube available = true := List.all_eq_true.mpr h
  simp [emit, ready]

inductive Atom (n : Nat) where
  | initial : Original n → Atom n
  | result : Nat → Atom n
  deriving DecidableEq

structure Clause (n : Nat) where
  support : List (Original n)
  conclusion : Atom n

/-- Baseline conditional summaries: every support member is required. -/
inductive Baseline (clauses : List (Clause n)) (seed : Atom n → Prop) : Atom n → Prop
  | seed {a} : seed a → Baseline clauses seed a
  | fire (c) : c ∈ clauses →
      (∀ o ∈ c.support, Baseline clauses seed (.initial o)) →
      Baseline clauses seed c.conclusion

/-- Rewritten summaries require the retained original, or ZERO for empty support. -/
inductive Collapsed (clauses : List (Clause n)) (seed : Atom n → Prop) : Atom n → Prop
  | seed {a} : seed a → Collapsed clauses seed a
  | zero (c) : c ∈ clauses → representative c.support = none →
      Collapsed clauses seed c.conclusion
  | fire (c) (o) : c ∈ clauses → representative c.support = some o →
      Collapsed clauses seed (.initial o) → Collapsed clauses seed c.conclusion

/-- Replacing a discovered positive Horn clause by its representative is an over-approximation. -/
theorem positive_horn_overapprox {clauses : List (Clause n)} {seed : Atom n → Prop}
    {a : Atom n} (h : Baseline clauses seed a) : Collapsed clauses seed a := by
  induction h with
  | seed hs => exact Collapsed.seed hs
  | fire c hc _ ih =>
    cases hr : representative c.support with
    | none => exact Collapsed.zero c hc hr
    | some o => exact Collapsed.fire c o hc hr (ih o (representative_member hr))

/-- Call binding need not preserve policy order; only retained membership is stable. -/
theorem binding_keeps_member (f : Original n → Original m)
    {support : List (Original n)} {o : Original n} (h : o ∈ support) :
    f o ∈ support.map f := List.mem_map.mpr ⟨o, h, rfl⟩

theorem binding_into_union {support : List (Original n)} {o : Original n}
    (h : o ∈ support) (f : Original n → List (Original m))
    {retained : Original m} (hr : retained ∈ f o) : retained ∈ support.flatMap f :=
  List.mem_flatMap.mpr ⟨o, h, hr⟩

def premiseSize : Option (Original n) → Nat | none => 0 | some _ => 1

theorem singleton_invariant (label : Option (Original n)) : premiseSize label ≤ 1 := by
  cases label <;> simp [premiseSize]

def originalUniverse (n : Nat) : List (Original n) :=
  [Kind.argument, .thisObject, .otherNonStatic, .staticField, .classPoint].flatMap
    (fun k => (List.finRange n).map (fun i => Original.mk k i))

theorem original_in_universe (o : Original n) : o ∈ originalUniverse n := by
  cases o with
  | mk k i => cases k <;> simp [originalUniverse]

def labelUniverse (n : Nat) : List (Option (Original n)) := none :: (originalUniverse n).map some

theorem every_label_in_universe (label : Option (Original n)) : label ∈ labelUniverse n := by
  cases label with
  | none => simp [labelUniverse]
  | some o => exact List.mem_cons_of_mem _ (List.mem_map.mpr ⟨o, original_in_universe o, rfl⟩)

/-- At most five kinds times the stable-ID bound, plus ZERO; no powerset label domain. -/
theorem linear_label_bound (n : Nat) : (labelUniverse n).length = 5 * n + 1 := by
  simp [labelUniverse, originalUniverse]
  omega

/-- A handoff output keeps its method entry metadata. This does not model query bodies. -/
structure GuidedOutput (n : Nat) where
  entry : Nat
  support : List (Original n)

def oldGuides (out : GuidedOutput n) : List (Nat × Option (Original n)) :=
  match out.support with
  | [] => [(out.entry, none)]
  | a :: xs => (a :: xs).map (fun o => (out.entry, some o))

def collapsedGuides (out : GuidedOutput n) : List (Nat × Option (Original n)) :=
  [(out.entry, representative out.support)]

theorem old_guide_for_each_member {out : GuidedOutput n} {o : Original n}
    (h : o ∈ out.support) : (out.entry, some o) ∈ oldGuides out := by
  cases hs : out.support with
  | nil => simp [hs] at h
  | cons a xs =>
    simp only [oldGuides, hs]
    exact List.mem_map.mpr ⟨o, hs ▸ h, rfl⟩

theorem projected_old_entry (out : GuidedOutput n) (e : Nat) :
    e ∈ (oldGuides out).map Prod.fst ↔ e = out.entry := by
  cases hs : out.support with
  | nil => simp [oldGuides, hs]
  | cons a xs =>
    constructor
    · intro h
      have hmap := List.mem_map.mp h
      obtain ⟨guide, hg, he⟩ := hmap
      have hguide : guide ∈ (a :: xs).map (fun o => (out.entry, some o)) := by
        simpa [oldGuides, hs] using hg
      obtain ⟨o, _, ho⟩ := List.mem_map.mp hguide
      subst guide
      exact he.symm
    · intro h
      subst e
      exact List.mem_map.mpr ⟨(out.entry, some a), by simp [oldGuides, hs], rfl⟩

theorem projected_collapsed_entry (out : GuidedOutput n) (e : Nat) :
    e ∈ (collapsedGuides out).map Prod.fst ↔ e = out.entry := by
  simp [collapsedGuides]

/-- Only projected entry membership is equal; guide identities and multiplicities may differ. -/
theorem handoff_entry_sets_equal (outputs : List (GuidedOutput n)) (e : Nat) :
    e ∈ outputs.flatMap (fun out => (oldGuides out).map Prod.fst) ↔
      e ∈ outputs.flatMap (fun out => (collapsedGuides out).map Prod.fst) := by
  constructor
  · intro h
    obtain ⟨out, ho, he⟩ := List.mem_flatMap.mp h
    exact List.mem_flatMap.mpr ⟨out, ho,
      (projected_collapsed_entry out e).mpr ((projected_old_entry out e).mp he)⟩
  · intro h
    obtain ⟨out, ho, he⟩ := List.mem_flatMap.mp h
    exact List.mem_flatMap.mpr ⟨out, ho,
      (projected_old_entry out e).mpr ((projected_collapsed_entry out e).mp he)⟩

namespace Counterexamples

def arg0 : Original 8 := ⟨.argument, 0⟩
def arg1 : Original 8 := ⟨.argument, 1⟩
def arg5 : Original 8 := ⟨.argument, 5⟩
def this0 : Original 8 := ⟨.thisObject, 0⟩
def local0 : Original 8 := ⟨.otherNonStatic, 0⟩
def static0 : Original 8 := ⟨.staticField, 0⟩
def class0 : Original 8 := ⟨.classPoint, 0⟩

example : representative [class0, static0, local0, this0, arg1] = some arg1 := by decide

example : representative ([none, some arg0, none].filterMap id) = some arg0 := by decide
example : representative ([none, none].filterMap (id : Option (Original 8) → _)) = none := by decide

/-- A missing cube literal cannot be ignored just because a representative exists. -/
theorem partial_cube_emits_nothing :
    emit [0, 1] (fun x => x == 0) [arg0, static0] = none := by decide

/-- Different complete bindings of one cube can require different representatives. -/
theorem complete_bindings_can_differ :
    emit [0, 1] (fun _ => true) [arg0, static0] = some (some arg0) ∧
    emit [0, 1] (fun _ => true) [this0, static0] = some (some this0) ∧
    arg0 ≠ this0 := by decide

def callBinding (x : Original 8) : Original 8 :=
  if x = arg0 then arg5 else if x = arg1 then arg0 else x

/-- Local minimum selection is not globally the minimum of the old caller union. -/
theorem minimum_does_not_commute_with_binding :
    callBinding (choose arg0 [arg1]) ≠
      choose (callBinding arg0) [callBinding arg1] := by decide

def callBindingWithZero (x : Original 8) : List (Original 8) :=
  if x = arg0 then [] else [x]

/-- A selected callee member can map to caller ZERO while the old union is nonzero. -/
theorem selected_member_can_bind_to_zero :
    representative (callBindingWithZero (choose arg0 [arg1])) = none ∧
    representative ([arg0, arg1].flatMap callBindingWithZero) = some arg1 := by decide

/-- Conditional warning only: a backward filter keyed solely by the representative loses arg1. -/
theorem representative_only_membership_loses_support :
    arg1 ∈ [arg0, arg1] ∧ arg1 ∉ [choose arg0 [arg1]] := by decide

/-- Keeping only the first binding per cube can lose the second unary prerequisite. -/
def summaryEnabled (support : List (Original 8)) (truth : Original 8 → Bool) : Bool :=
  match representative support with | none => true | some o => truth o

theorem one_per_cube_is_stricter_than_one_per_binding :
    summaryEnabled [arg0, static0] (fun o => o == this0) = false ∧
    summaryEnabled [this0, static0] (fun o => o == this0) = true := by decide

end Counterexamples
end Run1RepresentativePremises
