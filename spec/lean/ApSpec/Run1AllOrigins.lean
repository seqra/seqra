import Std

/-
Selected implementation specification, 21 October 2026. This executable
finite model covers origin projection after complete compatible cube
admission. `none` is ZERO; `some n` is one complete canonical initial ID.
NNF compilation, AP transfer, mutable queues and store subsumption are
production correspondence obligations, not assumptions proved here.
The executable empty-slot-list case is vacuous Boolean conjunction; the
production adapter retains the existing unconditional GEN path separately.
-/
namespace ApSpec.Run1AllOrigins

abbrev Origin := Option Nat

theorem filter_empty {α : Type} (p : α → Bool) (xs : List α)
    (h : ∀ x ∈ xs, p x = false) : xs.filter p = [] := by
  induction xs with
  | nil => rfl
  | cons x rest ih =>
    have hx := h x (by simp)
    have ht := ih (fun y hy => h y (List.mem_cons_of_mem x hy))
    simp [hx, ht]

def split (binding : List Origin) : List Origin :=
  let nonzero := binding.filter Option.isSome
  if nonzero.isEmpty then [none] else nonzero.eraseDups

theorem some_mem_split (binding : List Origin) (i : Nat) :
    some i ∈ split binding ↔ some i ∈ binding := by
  unfold split
  dsimp only
  split
  · rename_i empty
    have h : binding.filter Option.isSome = [] := List.isEmpty_iff.mp empty
    have absent : some i ∉ binding := by
      intro present
      have : some i ∈ binding.filter Option.isSome := by simp [present]
      rw [h] at this
      contradiction
    simp [absent]
  · simp

theorem zero_mem_split (binding : List Origin) :
    none ∈ split binding ↔ ∀ x ∈ binding, x = none := by
  unfold split
  dsimp only
  split
  · rename_i empty
    have h : binding.filter Option.isSome = [] := List.isEmpty_iff.mp empty
    have allZero : ∀ x ∈ binding, x = none := by
      intro x hx
      cases x with
      | none => rfl
      | some n =>
        have : some n ∈ binding.filter Option.isSome := by simp [hx]
        rw [h] at this
        contradiction
    exact ⟨fun _ => allZero, fun _ => by simp⟩
  · rename_i nonempty
    have notAll : ¬ (∀ x ∈ binding, x = none) := by
      intro allZero
      have h : binding.filter Option.isSome = [] := by
        apply filter_empty
        intro x hx
        rw [allZero x hx]
        rfl
      exact nonempty (List.isEmpty_iff.mpr h)
    simp [notAll]

def scalar (accepted : List (List Origin)) : List Origin := accepted.flatMap split

theorem zero_requires_complete_binding (accepted : List (List Origin)) :
    none ∈ scalar accepted ↔ ∃ b ∈ accepted, ∀ x ∈ b, x = none := by
  simp [scalar, zero_mem_split]

theorem nonzero_has_complete_binding (accepted : List (List Origin)) (i : Nat) :
    some i ∈ scalar accepted ↔ ∃ b ∈ accepted, some i ∈ b := by
  simp [scalar, some_mem_split]

/-- Independent slot choices for one already fixed compatible cube. -/
def tuples : List (List Origin) → List (List Origin)
  | [] => [[]]
  | a :: rest => a.flatMap fun x => (tuples rest).map (x :: ·)

def ready : List (List Origin) → Bool
  | [] => true
  | a :: rest => !a.isEmpty && ready rest

def zeroReady : List (List Origin) → Bool
  | [] => true
  | a :: rest => a.contains none && zeroReady rest

theorem ready_iff (slots : List (List Origin)) :
    ready slots = true ↔ ∃ b, b ∈ tuples slots := by
  induction slots with
  | nil => simp [ready, tuples]
  | cons a rest ih =>
    simp only [ready, Bool.and_eq_true, Bool.not_eq_true', ih, tuples,
      List.mem_flatMap, List.mem_map]
    constructor
    · rintro ⟨ha, b, hb⟩
      cases a with
      | nil => simp at ha
      | cons x xs => exact ⟨x :: b, x, by simp, b, hb, rfl⟩
    · rintro ⟨_, x, hx, b, hb, _⟩
      exact ⟨by cases a <;> simp_all, b, hb⟩

theorem zeroReady_iff (slots : List (List Origin)) :
    zeroReady slots = true ↔ ∃ b ∈ tuples slots, ∀ x ∈ b, x = none := by
  induction slots with
  | nil => simp [zeroReady, tuples]
  | cons a rest ih =>
    simp only [zeroReady, Bool.and_eq_true, List.contains_iff_mem, ih]
    constructor
    · rintro ⟨ha, b, hb, hz⟩
      refine ⟨none :: b, ?_, ?_⟩
      · exact List.mem_flatMap.mpr ⟨none, ha, List.mem_map.mpr ⟨b, hb, rfl⟩⟩
      · simpa using hz
    · rintro ⟨b, hb, hz⟩
      obtain ⟨x, hx, selected⟩ := List.mem_flatMap.mp hb
      obtain ⟨tail, ht, eq⟩ := List.mem_map.mp selected
      subst b
      have xx : x = none := hz x (by simp)
      exact ⟨xx ▸ hx, tail, ht, fun y hy => hz y (List.mem_cons_of_mem x hy)⟩

theorem tuple_member (slots : List (List Origin)) (b : List Origin)
    (hb : b ∈ tuples slots) (x : Origin) (hx : x ∈ b) : x ∈ slots.flatten := by
  induction slots generalizing b with
  | nil => simp [tuples] at hb; subst b; simp at hx
  | cons a rest ih =>
    obtain ⟨y, hy, selected⟩ := List.mem_flatMap.mp hb
    obtain ⟨bs, hbs, eq⟩ := List.mem_map.mp selected
    subst b
    rcases List.mem_cons.mp hx with e | ht
    · subst x; simp [hy]
    · exact List.mem_append.mpr (Or.inr (ih bs hbs ht))

theorem complete_with_member (slots : List (List Origin)) (x : Origin)
    (hr : ready slots = true) (hx : x ∈ slots.flatten) :
    ∃ b ∈ tuples slots, x ∈ b := by
  induction slots with
  | nil => simp at hx
  | cons a rest ih =>
    have rr : !a.isEmpty = true ∧ ready rest = true := by simpa [ready] using hr
    rcases List.mem_append.mp hx with ha | ht
    · obtain ⟨b, hb⟩ := (ready_iff rest).mp rr.2
      exact ⟨x :: b, List.mem_flatMap.mpr ⟨x, ha, List.mem_map.mpr ⟨b, hb, rfl⟩⟩,
        by simp⟩
    · obtain ⟨b, hb, present⟩ := ih rr.2 ht
      cases a with
      | nil => simp at rr
      | cons y ys =>
        exact ⟨y :: b, List.mem_flatMap.mpr ⟨y, by simp, List.mem_map.mpr ⟨b, hb, rfl⟩⟩,
          List.mem_cons_of_mem y present⟩

def factored (slots : List (List Origin)) : List Origin :=
  if ready slots then
    (slots.flatten.filter Option.isSome).eraseDups ++ (if zeroReady slots then [none] else [])
  else []

/-- One shared canonical leaf must agree in header and origin. -/
def shared (leftHeader rightHeader : Nat) (a b : List Origin) : List Origin :=
  if leftHeader == rightHeader then a.filter (b.contains ·) else []

theorem shared_leaf_exact (leftHeader rightHeader : Nat) (a b : List Origin) (o : Origin) :
    o ∈ shared leftHeader rightHeader a b ↔
      leftHeader = rightHeader ∧ o ∈ a ∧ o ∈ b := by
  by_cases h : leftHeader = rightHeader
  · have hh : (leftHeader == rightHeader) = true := by simpa using h
    simp only [shared, hh, ↓reduceIte, List.mem_filter, List.contains_iff_mem]
    exact ⟨fun pair => ⟨h, pair⟩, fun all => all.2⟩
  · have hh : (leftHeader == rightHeader) = false := by simpa using h
    simp only [shared, hh, Bool.false_eq_true, ↓reduceIte, List.not_mem_nil]
    exact ⟨False.elim, fun all => h all.1⟩

theorem factor_zero_exact (slots : List (List Origin)) :
    none ∈ factored slots ↔ none ∈ scalar (tuples slots) := by
  rw [zero_requires_complete_binding, ← zeroReady_iff]
  unfold factored
  split
  · simp
  · rename_i notReady
    have noZero : zeroReady slots ≠ true := by
      intro hz
      obtain ⟨b, hb, _⟩ := (zeroReady_iff slots).mp hz
      exact notReady ((ready_iff slots).mpr ⟨b, hb⟩)
    simp [noZero]

theorem factor_nonzero_exact (slots : List (List Origin)) (i : Nat) :
    some i ∈ factored slots ↔ some i ∈ scalar (tuples slots) := by
  rw [nonzero_has_complete_binding]
  unfold factored
  split
  · rename_i hr
    simp only [List.mem_append, List.mem_eraseDups, List.mem_filter, Option.isSome_some,
      and_true]
    have noZero : some i ∉ (if zeroReady slots then [none] else []) := by split <;> simp
    simp only [noZero, or_false]
    exact ⟨complete_with_member slots (some i) hr,
      fun ⟨b, hb, hx⟩ => tuple_member slots b hb (some i) hx⟩
  · rename_i hr
    simp only [List.not_mem_nil, false_iff]
    rintro ⟨b, hb, _⟩
    exact hr ((ready_iff slots).mpr ⟨b, hb⟩)

theorem factor_exact (slots : List (List Origin)) (o : Origin) :
    o ∈ factored slots ↔ o ∈ scalar (tuples slots) := by
  cases o with
  | none => exact factor_zero_exact slots
  | some i => exact factor_nonzero_exact slots i

/-- Both coordinates are retained. Entry-set equality alone is weaker. -/
def referencePairs (exit : Nat) (bindings : List (List Origin)) : List (Nat × Origin) :=
  bindings.flatMap fun b => (split b).map (exit, ·)

theorem handoff_pair_exact (exit : Nat) (bindings : List (List Origin)) (o : Origin) :
    (exit, o) ∈ referencePairs exit bindings ↔ o ∈ scalar bindings := by
  simp [referencePairs, scalar]

def fresh (old offered : List Origin) : List Origin :=
  offered.eraseDups.filter fun x => !old.contains x

theorem fresh_exact (old offered : List Origin) (o : Origin) :
    o ∈ fresh old offered ↔ o ∈ offered ∧ o ∉ old := by simp [fresh]

theorem no_fresh_for_duplicate (old : List Origin) : fresh old old = [] := by
  unfold fresh
  apply filter_empty
  intro o ho
  have present := List.mem_eraseDups.mp ho
  simp [present]

def controls : List Bool := [
  split [none, some 1] == [some 1],
  split [none, none] == [none],
  factored [[some 1, some 2], [some 3, some 4]] == [some 1, some 2, some 3, some 4],
  factored [[none], [some 1]] == [some 1],
  factored [[none], []] == [],
  (factored [[none, some 1], [none, some 2]]).contains none,
  !(scalar [[none, some 1], [some 2, none]]).contains none,
  shared 3 3 [none, some 1] [some 1, some 2] == [some 1],
  shared 3 4 [none] [none] == [],
  shared 3 3 [none] [none] == [none],
  fresh [some 1] [some 1, some 2] == [some 2]
]

theorem controls_pass : controls.all id = true := by decide

end ApSpec.Run1AllOrigins
