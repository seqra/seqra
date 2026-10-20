/-
Selected scope: typed immutable GROUP projection only. Field paths, layers,
rule evaluation and static request scheduling do not occur in this model.
`canonical` is the fixed-point invariant of the private exclusion factories.
The executable Kotlin reference tests check those factories and catalog IDs.
-/
namespace ApSpec.ImmutableGroupNormalization

inductive Atom where
  | zero
  | group (owner : Nat)
  | full (owner member : Nat)
  deriving DecidableEq, Repr

def project : Atom → Atom
  | .zero => .zero
  | .group g => .group g
  | .full g _ => .group g

def isFull : Atom → Bool
  | .full _ _ => true
  | _ => false

theorem project_idempotent (a : Atom) : project (project a) = project a := by
  cases a <;> rfl

theorem unchanged_iff_not_full (a : Atom) : project a = a ↔ isFull a = false := by
  cases a <;> simp [project, isFull]

def referenceAtoms (xs : List Atom) : List Atom := xs.map project

-- The runtime allocates its lazy array only after this condition is false.
def optimizedAtoms (xs : List Atom) : List Atom :=
  if xs.any isFull then referenceAtoms xs else xs

theorem stable_projection (xs : List Atom) (h : xs.any isFull = false) :
    referenceAtoms xs = xs := by
  induction xs with
  | nil => rfl
  | cons a xs ih =>
    have ha : isFull a = false := by simpa using (Bool.or_eq_false_iff.mp h).1
    have ht : xs.any isFull = false := by simpa using (Bool.or_eq_false_iff.mp h).2
    change project a :: referenceAtoms xs = a :: xs
    rw [(unchanged_iff_not_full a).mpr ha, ih ht]

theorem atom_list_equivalence (xs : List Atom) : optimizedAtoms xs = referenceAtoms xs := by
  unfold optimizedAtoms
  split
  · rfl
  · rename_i h
    have stable : xs.any isFull = false := Bool.eq_false_iff.mpr h
    exact (stable_projection xs stable).symm

theorem projected_list_idempotent (xs : List Atom) :
    referenceAtoms (referenceAtoms xs) = referenceAtoms xs := by
  simp [referenceAtoms, List.map_map, Function.comp_def, project_idempotent]

-- A symbolic group covers every member, including members never enumerated.
def covers (selector value : Atom) : Bool :=
  match selector, value with
  | .group g, .full h _ => decide (g = h)
  | a, b => decide (a = b)

theorem group_covers_late_member (g n : Nat) : covers (.group g) (.full g n) = true := by
  simp [covers]

theorem finite_member_is_not_group (g n : Nat) : covers (.full g n) (.group g) = false := by
  simp [covers]

-- The private factory removes only FULL selectors shadowed by a group.
-- Duplicate/sorted IDs are represented by List membership in this model.
def keepSelector (xs : List Atom) : Atom → Bool
  | .full g _ => decide (Atom.group g ∉ xs)
  | _ => true

def normalize (xs : List Atom) : List Atom := xs.filter (keepSelector xs)

theorem group_mem_normalize (xs : List Atom) (g : Nat) :
    Atom.group g ∈ normalize xs ↔ Atom.group g ∈ xs := by
  simp [normalize, keepSelector]

theorem keep_after_normalize (xs : List Atom) :
    keepSelector (normalize xs) = keepSelector xs := by
  funext a
  cases a <;> simp [keepSelector, group_mem_normalize]

theorem normalize_idempotent (xs : List Atom) : normalize (normalize xs) = normalize xs := by
  change (normalize xs).filter (keepSelector (normalize xs)) = normalize xs
  rw [keep_after_normalize]
  simp [normalize, List.filter_filter]

theorem full_coverage_implies_group (g n : Nat) (a : Atom)
    (h : covers (.full g n) a = true) : covers (.group g) a = true := by
  cases a <;> simp_all [covers]

def Excludes (xs : List Atom) (a : Atom) : Prop :=
  ∃ selector, selector ∈ xs ∧ covers selector a = true

theorem normalize_denotation (xs : List Atom) (a : Atom) :
    Excludes (normalize xs) a ↔ Excludes xs a := by
  constructor
  · rintro ⟨selector, present, hit⟩
    exact ⟨selector, (List.mem_filter.mp present).1, hit⟩
  · rintro ⟨selector, present, hit⟩
    cases selector with
    | zero => exact ⟨.zero, by simp [normalize, keepSelector, present], hit⟩
    | group g => exact ⟨.group g, (group_mem_normalize xs g).mpr present, hit⟩
    | full g n =>
      by_cases shadowed : Atom.group g ∈ xs
      · exact ⟨.group g, (group_mem_normalize xs g).mpr shadowed,
          full_coverage_implies_group g n a hit⟩
      · exact ⟨.full g n, by simp [normalize, keepSelector, shadowed, present], hit⟩

inductive Catalog where
  | detached
  | attached (identity : Nat)
  deriving DecidableEq, Repr

def validates (source : Catalog) (target : Nat) : Bool :=
  match source with
  | .detached => true
  | .attached n => n == target

def referenceAttach (normalize : List Atom → List Atom)
    (source : Catalog) (target : Nat) (xs : List Atom) : Option (List Atom) :=
  if validates source target then some (normalize xs) else none

def optimizedAttach (normalize : List Atom → List Atom)
    (source : Catalog) (target : Nat) (xs : List Atom) : Option (List Atom) :=
  if validates source target then
    if source == .attached target then some xs else some (normalize xs)
  else none

theorem attached_equivalence (normalize : List Atom → List Atom)
    (source : Catalog) (target : Nat) (xs : List Atom)
    (canonical : source = .attached target → normalize xs = xs) :
    optimizedAttach normalize source target xs = referenceAttach normalize source target xs := by
  unfold optimizedAttach referenceAttach
  split
  · split
    · rename_i h
      simp only [beq_iff_eq] at h
      rw [canonical h]
    · rfl
  · rfl

theorem foreign_attach_rejected (normalize : List Atom → List Atom)
    (a b : Nat) (xs : List Atom) (h : a ≠ b) :
    optimizedAttach normalize (.attached a) b xs = none := by
  simp [optimizedAttach, validates, h]

def referenceExclusions (normalize : List Atom → List Atom) (xs : List Atom) :=
  normalize (referenceAtoms xs)

-- GROUP mode keeps the old fallback for foreign and detached catalogs.
def optimizedExclusions (normalize : List Atom → List Atom)
    (source : Catalog) (target : Nat) (xs : List Atom) :=
  if source == .attached target && !xs.any isFull then xs
  else referenceExclusions normalize xs

theorem exclusions_equivalence (normalize : List Atom → List Atom)
    (source : Catalog) (target : Nat) (xs : List Atom)
    (canonical : source = .attached target → normalize xs = xs) :
    optimizedExclusions normalize source target xs = referenceExclusions normalize xs := by
  unfold optimizedExclusions
  split
  · rename_i h
    have both := Bool.and_eq_true_iff.mp h
    have owned : source = .attached target := by simpa using both.1
    have stable : xs.any isFull = false := by simpa using both.2
    unfold referenceExclusions
    rw [stable_projection xs stable, canonical owned]
  · rfl

theorem foreign_group_fallback (normalize : List Atom → List Atom)
    (a b : Nat) (xs : List Atom) (h : a ≠ b) :
    optimizedExclusions normalize (.attached a) b xs = referenceExclusions normalize xs := by
  simp [optimizedExclusions, h]

-- The concrete private factory establishes the input invariant itself.
theorem factory_attachment_equivalence (target : Nat) (xs : List Atom) :
    optimizedAttach normalize (.attached target) target (normalize xs) =
      referenceAttach normalize (.attached target) target (normalize xs) := by
  apply attached_equivalence
  intro _
  exact normalize_idempotent xs

theorem factory_projection_equivalence (target : Nat) (xs : List Atom) :
    optimizedExclusions normalize (.attached target) target (normalize xs) =
      referenceExclusions normalize (normalize xs) := by
  apply exclusions_equivalence
  intro _
  exact normalize_idempotent xs

-- Cost counts newly materialized element slots, excluding scans and interning.
def referenceSlots (xs : List Atom) : Nat := xs.length
def optimizedSlots (xs : List Atom) : Nat := if xs.any isFull then xs.length else 0

theorem no_replacement_array_for_stable (xs : List Atom) (h : xs.any isFull = false) :
    optimizedSlots xs = 0 := by simp [optimizedSlots, h]

theorem slots_never_increase (xs : List Atom) : optimizedSlots xs ≤ referenceSlots xs := by
  unfold optimizedSlots referenceSlots
  split <;> simp

def controls : List Bool := [
  optimizedAtoms [.group 1, .zero] == [.group 1, .zero],
  optimizedAtoms [.full 1 7, .group 2, .full 1 8] == [.group 1, .group 2, .group 1],
  covers (.group 1) (.full 1 999999),
  !covers (.full 1 7) (.group 1),
  optimizedAttach id (.attached 1) 2 [.group 1] == none,
  optimizedExclusions id (.attached 1) 2 [.full 1 7] == [.group 1],
  optimizedSlots [.group 1, .zero] == 0,
  optimizedSlots [.group 1, .full 1 7] == 2
]

theorem controls_pass : controls.all id = true := by decide

end ApSpec.ImmutableGroupNormalization
