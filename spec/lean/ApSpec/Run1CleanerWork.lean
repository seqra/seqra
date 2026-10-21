import Std
import ApSpec.ImmutableGroupNormalization

/-
Selected design model, not a proof of the mutable analyzer. Headers encode
complete production contexts; images encode exact scalar paths. The payload
list is an executable finite reference for the production per-group tries.
Replies drain scalar images, so no downstream interpreter distributivity is
assumed. Stage observations use idempotent membership, not callback counts.
The existing AP, static-service, field-transfer and cleaner laws still need
their production correspondence controls.
-/
namespace ApSpec.Run1CleanerWork

structure Header where
  owner : Nat
  site : Nat
  original : Nat
  base : Nat
  tail : Nat
  fieldExclusion : List Nat
  layer : Nat
  callerLayer : Option Nat
  identity : Option Bool
  pendingDemotion : Bool
  deriving DecidableEq, Repr

abbrev Image := List Nat
abbrev GroupImage := Nat × Image

structure ScalarPoint where
  header : Header
  group : Nat
  image : Image
  mx : List Nat
  deriving DecidableEq, Repr

abbrev Packed := List (Header × List GroupImage)

def pack (xs : List ScalarPoint) : Packed :=
  (xs.map ScalarPoint.header).eraseDups.map fun h =>
    (h, ((xs.filter fun x => x.header == h).map fun x => (x.group, x.image)).eraseDups)

def Has (rows : Packed) (h : Header) (g : Nat) (p : Image) : Prop :=
  ∃ payload, (h, payload) ∈ rows ∧ (g, p) ∈ payload

theorem pack_membership (xs : List ScalarPoint) (h : Header) (g : Nat) (p : Image) :
    Has (pack xs) h g p ↔ ∃ x ∈ xs, x.header = h ∧ x.group = g ∧ x.image = p := by
  constructor
  · rintro ⟨payload, row, present⟩
    obtain ⟨key, _, eq⟩ := List.mem_map.mp row
    have hk : key = h := congrArg Prod.fst eq
    subst key
    have hp : payload =
        ((xs.filter fun x => x.header == h).map fun x => (x.group, x.image)).eraseDups :=
      (congrArg Prod.snd eq).symm
    rw [hp] at present
    obtain ⟨x, hx, pair⟩ := List.mem_map.mp (List.mem_eraseDups.mp present)
    have q := List.mem_filter.mp hx
    exact ⟨x, q.1, by simpa using q.2, congrArg Prod.fst pair, congrArg Prod.snd pair⟩
  · rintro ⟨x, hx, hh, hg, hp⟩
    let payload := ((xs.filter fun y => y.header == h).map fun y => (y.group, y.image)).eraseDups
    refine ⟨payload, ?_, ?_⟩
    · apply List.mem_map.mpr
      refine ⟨h, List.mem_eraseDups.mpr ?_, rfl⟩
      exact List.mem_map.mpr ⟨x, hx, hh⟩
    · apply List.mem_eraseDups.mpr
      apply List.mem_map.mpr
      exact ⟨x, List.mem_filter.mpr ⟨hx, by simpa using hh⟩, by simp [hg, hp]⟩

theorem duplicate_offer_idempotent (xs : List ScalarPoint) (x : ScalarPoint)
    (h : x ∈ xs) (key : Header) (g : Nat) (p : Image) :
    Has (pack (x :: xs)) key g p ↔ Has (pack xs) key g p := by
  simp only [pack_membership, List.mem_cons]
  constructor
  · rintro ⟨y, hy, rest⟩
    exact ⟨y, hy.elim (fun e => e ▸ h) id, rest⟩
  · rintro ⟨y, hy, rest⟩
    exact ⟨y, Or.inr hy, rest⟩

theorem mx_is_not_payload (x : ScalarPoint) (otherMx : List Nat) :
    pack [x] = pack [{x with mx := otherMx}] := by
  simp [pack]

theorem no_group_image_cross_product (xs : List ScalarPoint) (h : Header) (g : Nat) (p : Image)
    (absent : ¬ ∃ x ∈ xs, x.header = h ∧ x.group = g ∧ x.image = p) :
    ¬ Has (pack xs) h g p := by
  simpa only [pack_membership] using absent

def answers (rows : Packed) (h : Header) (groups : List Nat) : List GroupImage :=
  rows.flatMap fun row =>
    if row.1 == h then row.2.filter fun gp => gp.1 ∈ groups else []

theorem answer_membership (rows : Packed) (h : Header) (groups : List Nat)
    (g : Nat) (p : Image) :
    (g, p) ∈ answers rows h groups ↔ Has rows h g p ∧ g ∈ groups := by
  constructor
  · intro present
    obtain ⟨⟨key, payload⟩, row, selected⟩ := List.mem_flatMap.mp present
    by_cases hk : key = h
    · subst key
      have hp : (g, p) ∈ payload ∧ g ∈ groups := by simpa using selected
      exact ⟨⟨payload, row, hp.1⟩, hp.2⟩
    · simp [hk] at selected
  · rintro ⟨⟨payload, row, selected⟩, ready⟩
    apply List.mem_flatMap.mpr
    exact ⟨(h, payload), row, by simpa using And.intro selected ready⟩

def drain (rows : Packed) (h : Header) (groups : List Nat) : Packed :=
  rows.map fun row => (row.1, row.2.filter fun gp => !(row.1 == h && gp.1 ∈ groups))

theorem drain_membership (rows : Packed) (h key : Header) (groups : List Nat)
    (g : Nat) (p : Image) :
    Has (drain rows h groups) key g p ↔
      Has rows key g p ∧ ¬ (key = h ∧ g ∈ groups) := by
  constructor
  · rintro ⟨payload, row, selected⟩
    obtain ⟨⟨k, old⟩, oldRow, eq⟩ := List.mem_map.mp row
    have hk : k = key := congrArg Prod.fst eq
    subst k
    have hp : payload = (old.filter fun gp => !(key == h && gp.1 ∈ groups)) :=
      (congrArg Prod.snd eq).symm
    rw [hp] at selected
    have hs := List.mem_filter.mp selected
    refine ⟨⟨old, oldRow, hs.1⟩, ?_⟩
    by_cases kh : key = h <;> by_cases gg : g ∈ groups <;> simp_all
  · rintro ⟨⟨old, row, selected⟩, keep⟩
    refine ⟨old.filter (fun gp => !(key == h && gp.1 ∈ groups)), ?_, ?_⟩
    · exact List.mem_map.mpr ⟨(key, old), row, rfl⟩
    · refine List.mem_filter.mpr ⟨selected, ?_⟩
      by_cases kh : key = h <;> by_cases gg : g ∈ groups <;> simp_all

def pruneEmpty (rows : Packed) : Packed := rows.filter fun row => !row.2.isEmpty

theorem prune_empty_preserves_payload (rows : Packed) (h : Header) (g : Nat) (p : Image) :
    Has (pruneEmpty rows) h g p ↔ Has rows h g p := by
  constructor
  · rintro ⟨payload, row, hp⟩
    exact ⟨payload, (List.mem_filter.mp row).1, hp⟩
  · rintro ⟨payload, row, hp⟩
    refine ⟨payload, List.mem_filter.mpr ⟨row, ?_⟩, hp⟩
    cases payload with
    | nil => simp at hp
    | cons a rest => rfl

theorem scalar_and_packed_answers (xs : List ScalarPoint) (h : Header)
    (groups : List Nat) (g : Nat) (p : Image) :
    (g, p) ∈ answers (pack xs) h groups ↔
      (∃ x ∈ xs, x.header = h ∧ x.group = g ∧ x.image = p) ∧ g ∈ groups := by
  rw [answer_membership, pack_membership]

theorem partial_reply_keeps_other_groups (rows : Packed) (h : Header) (groups : List Nat)
    (g : Nat) (p : Image) (notReady : g ∉ groups) :
    Has (pruneEmpty (drain rows h groups)) h g p ↔ Has rows h g p := by
  simp [prune_empty_preserves_payload, drain_membership, notReady]

theorem late_offer_membership (old fresh : List ScalarPoint) (h : Header) (g : Nat) (p : Image) :
    Has (pack (old ++ fresh)) h g p ↔ Has (pack old) h g p ∨ Has (pack fresh) h g p := by
  simp only [pack_membership, List.mem_append]
  constructor
  · rintro ⟨x, hx, hit⟩
    exact hx.elim (fun q => Or.inl ⟨x, q, hit⟩) (fun q => Or.inr ⟨x, q, hit⟩)
  · intro either
    rcases either with ⟨x, hx, hit⟩ | ⟨x, hx, hit⟩
    · exact ⟨x, Or.inl hx, hit⟩
    · exact ⟨x, Or.inr hx, hit⟩

theorem scalar_callback_union [DecidableEq α] (materialize : GroupImage → List α)
    (rows : Packed) (h : Header) (groups : List Nat) (a : α) :
    a ∈ ((answers rows h groups).eraseDups.flatMap materialize) ↔
      a ∈ (answers rows h groups).flatMap materialize := by
  simp

/- C2: one eligible block, fixed base, original descriptors kept elsewhere. -/
abbrev CleanerKey := Nat × Nat

def blockGroups (base : Nat) (keys : List CleanerKey) : List Nat :=
  (keys.filter fun k => k.1 == base).map Prod.snd

theorem group_block_same_requests (base : Nat) (keys : List CleanerKey) (g : Nat) :
    g ∈ blockGroups base keys.eraseDups ↔ g ∈ blockGroups base keys := by
  simp [blockGroups]

theorem group_block_same_exclusions (base : Nat) (keys : List CleanerKey) (old : List Nat) (g : Nat) :
    g ∈ old ++ blockGroups base keys.eraseDups ↔ g ∈ old ++ blockGroups base keys := by
  simp [group_block_same_requests]

theorem first_descriptor_is_retained (key : CleanerKey) (rest : List CleanerKey) :
    (key :: rest).eraseDups.head? = some key := by
  rw [List.eraseDups_cons]
  rfl

-- Raw previous positions use +1, with 0 as the no-previous sentinel. This
-- models the production Int encoding (-1 or an original step index).
def previousPlusOne (positions : List Nat) : Nat :=
  (positions.map Nat.succ).foldr Nat.max 0

theorem max_fold_le (xs : List Nat) (limit : Nat) :
    xs.foldr Nat.max 0 ≤ limit ↔ ∀ x ∈ xs, x ≤ limit := by
  induction xs with
  | nil => simp
  | cons a rest ih => simp [Nat.max_le, ih]

theorem previous_mask_is_suffix_selection (positions : List Nat) (firstStep : Nat) :
    previousPlusOne positions ≤ firstStep ↔ ∀ p ∈ positions, p < firstStep := by
  simp only [previousPlusOne, max_fold_le, List.mem_map, forall_exists_index, and_imp]
  constructor
  · intro h p hp
    have := h _ p hp rfl
    omega
  · intro h _ p hp eq
    have := h p hp
    subst eq
    omega

-- The selected compiler resets its prior-position directory at a barrier;
-- positions from an earlier block cannot enter this local decision.
theorem barrier_resets_previous (firstStep : Nat) :
    previousPlusOne [] ≤ firstStep := Nat.zero_le _

theorem duplicate_count_example :
    (([7, 7, 7, 7] : List Nat).eraseDups).length = 1 := by decide

-- A group reply is already TAINT. Every remaining eligible selected cleaner
-- preserves this value; this is the block-exit continuation relation.
def taintSuffix (image : Image) (group : Nat) (_keys : List CleanerKey) : GroupImage := (group, image)

theorem first_site_reply_covers_later_site (image : Image) (g : Nat)
    (earlySuffix lateSuffix : List CleanerKey) :
    taintSuffix image g earlySuffix = taintSuffix image g lateSuffix := rfl

/- C3: fixed invocation, complete input key; observations are idempotent sets. -/
theorem exact_step_dedup_preserves_results [DecidableEq α] [DecidableEq β]
    (next : α → List β) (xs : List α) (result : β) :
    result ∈ xs.eraseDups.flatMap next ↔ result ∈ xs.flatMap next := by simp

theorem exact_step_dedup_preserves_effects [DecidableEq α] [DecidableEq β]
    (effects : α → List β) (xs : List α) (effect : β) :
    effect ∈ xs.eraseDups.flatMap effects ↔ effect ∈ xs.flatMap effects := by simp

/- C4: existing constructive normalization model proves this invariant. -/
open ImmutableGroupNormalization

theorem canonical_duplicate_add (xs : List Atom) (a : Atom)
    (canonical : normalize xs = xs) (present : a ∈ xs) :
    normalize (if a ∈ xs then xs else xs ++ [a]) = xs := by
  simp [present, canonical]

theorem cached_full_flag_exact (xs : List Atom) (cached : Bool)
    (invariant : cached = xs.any isFull) :
    (cached = false ↔ xs.any isFull = false) := by rw [invariant]

theorem cached_flag_projection_safe (xs : List Atom) (cached : Bool)
    (invariant : cached = xs.any isFull) (stable : cached = false)
    (canonical : normalize xs = xs) : referenceExclusions normalize xs = xs := by
  unfold referenceExclusions
  rw [stable_projection xs (by simpa [invariant] using stable), canonical]

/- C5: no static descriptor is removed when both lists are empty. -/
structure SummaryValue where
  edges : List Nat
  filters : List Nat
  staticOperations : List Nat
  deriving DecidableEq, Repr

def referenceCopy (s : SummaryValue) (operations : List Nat) : SummaryValue :=
  {s with staticOperations := operations}

def skipEmptyCopy (s : SummaryValue) (operations : List Nat) : SummaryValue :=
  if s.staticOperations.isEmpty && operations.isEmpty then s else referenceCopy s operations

theorem empty_copy_same_value (s : SummaryValue) (operations : List Nat) :
    skipEmptyCopy s operations = referenceCopy s operations := by
  unfold skipEmptyCopy
  split
  · rename_i h
    have both := Bool.and_eq_true_iff.mp h
    have hs : s.staticOperations = [] := List.isEmpty_iff.mp both.1
    have ho : operations = [] := List.isEmpty_iff.mp both.2
    cases s
    simp_all [referenceCopy]
  · rfl

def fixtureHeader : Header := ⟨1, 2, 3, 4, 5, [], 1, some 1, some false, false⟩

def controls : List Bool := [
  (pack [⟨fixtureHeader, 7, [10], []⟩, ⟨fixtureHeader, 8, [20], [9]⟩]).length == 1,
  answers (pack [⟨fixtureHeader, 7, [10], []⟩, ⟨fixtureHeader, 8, [20], []⟩]) fixtureHeader [7] == [(7, [10])],
  (blockGroups 1 [(1,7), (1,7), (1,8), (1,7)]).eraseDups == [7,8],
  (([1,1,1,1] : List Nat).eraseDups).length == 1,
  normalize [.group 7, .full 7 10] == [.group 7]
]

theorem controls_pass : controls.all id = true := by decide

end ApSpec.Run1CleanerWork
