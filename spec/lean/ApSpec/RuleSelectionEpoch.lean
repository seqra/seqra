/- Selection-generation cache ownership. Scoped results are pure and ID-stable.
Compatibility rows retain broad roles/unsafe fallback IDs and their counter.
No Kotlin reader-join or whole-engine refinement is claimed. -/
import Std
namespace ApSpec.RuleSelectionEpoch
set_option autoImplicit false
inductive Mode where
  | normal | relevant
  deriving DecidableEq, Repr
inductive EndMode where
  | ordinary | withoutEnd
  deriving DecidableEq, Repr
inductive Subject where
  | method (id : Nat) | field (id : Nat)
  deriving DecidableEq, Repr
inductive Phase where
  | prescan | analysis | idle
  deriving DecidableEq, Repr
structure Key where
  owner : Nat
  view : Nat
  catalog : Nat
  generation : Nat
  selection : Option Nat
  subject : Subject
  role : Nat
  mode : Mode
  ends : EndMode
  deriving DecidableEq, Repr
structure Entry where
  key : Key
  value : List Nat
  deriving DecidableEq, Repr
/-- Broad fallback values are selection independent, not new selected caches. -/
structure RawKey where
  owner : Nat
  view : Nat
  catalog : Nat
  subject : Subject
  role : Nat
  mode : Mode
  ends : EndMode
  deriving DecidableEq, Repr
structure RawEntry where
  key : RawKey
  value : List Nat
  deriving DecidableEq, Repr
structure State where
  catalog : Nat
  generation : Nat
  selection : Option Nat
  phase : Phase
  prescanJoined : Bool
  readers : Nat
  liveArtifacts : Nat
  selectedCache : List Entry
  compatibility : List RawEntry
  fallbackCounter : Nat
  deriving DecidableEq, Repr

def lookup (key : Key) : List Entry → Option (List Nat)
  | [] => none
  | row :: rows => if row.key = key then some row.value else lookup key rows

def rawLookup (key : RawKey) : List RawEntry → Option (List Nat)
  | [] => none
  | row :: rows => if row.key = key then some row.value else rawLookup key rows

def current (s : State) (key : Key) : Prop :=
  key.catalog = s.catalog ∧ key.generation = s.generation ∧ key.selection = s.selection
instance (s : State) (key : Key) : Decidable (current s key) := inferInstanceAs
  (Decidable (key.catalog = s.catalog ∧ key.generation = s.generation ∧ key.selection = s.selection))
def readScoped (s : State) (key : Key) : Option (List Nat) :=
  if current s key then lookup key s.selectedCache else none
def remember (s : State) (entry : Entry) : State :=
  if current s entry.key then { s with selectedCache := entry :: s.selectedCache } else s
def WellFormed (s : State) : Prop := ∀ entry ∈ s.selectedCache, current s entry.key
def Sound (oracle : Key → List Nat) (rows : List Entry) : Prop :=
  ∀ entry ∈ rows, entry.value = oracle entry.key

theorem lookup_sound (oracle : Key → List Nat) (rows : List Entry) (key : Key)
    (sound : Sound oracle rows) (value : List Nat)
    (found : lookup key rows = some value) : value = oracle key := by
  induction rows with
  | nil => simp [lookup] at found
  | cons row rows ih =>
      simp only [lookup] at found
      split at found
      next same =>
        have valueEq : row.value = value := Option.some.inj found
        rw [← valueEq, sound row (by simp), same]
      next _ => exact ih (fun e h => sound e (by simp [h])) found

theorem remember_wellFormed (s : State) (entry : Entry) (wf : WellFormed s) :
    WellFormed (remember s entry) := by
  unfold remember
  split
  next h =>
    intro e present
    simp only [List.mem_cons] at present
    rcases present with rfl | present
    · exact h
    · exact wf e present
  next _ => exact wf

theorem remember_sound (s : State) (entry : Entry) (oracle : Key → List Nat)
    (sound : Sound oracle s.selectedCache) (right : entry.value = oracle entry.key) :
    Sound oracle (remember s entry).selectedCache := by
  unfold remember
  split
  · intro e present
    simp only [List.mem_cons] at present
    rcases present with rfl | present
    · exact right
    · exact sound e present
  · exact sound

theorem readScoped_sound (s : State) (oracle : Key → List Nat)
    (sound : Sound oracle s.selectedCache) (key : Key) (value : List Nat)
    (found : readScoped s key = some value) : value = oracle key := by
  unfold readScoped at found
  split at found
  · exact lookup_sound oracle s.selectedCache key sound value found
  · simp at found

theorem stale_generation_is_miss (s : State) (key : Key)
    (stale : key.generation ≠ s.generation) : readScoped s key = none := by
  simp [readScoped, current, stale]
theorem stale_catalog_is_miss (s : State) (key : Key)
    (stale : key.catalog ≠ s.catalog) : readScoped s key = none := by
  simp [readScoped, current, stale]
theorem stale_selection_is_miss (s : State) (key : Key)
    (stale : key.selection ≠ s.selection) : readScoped s key = none := by
  simp [readScoped, current, stale]

/-- No OK-status guard: joined timeout behavior remains a caller contract. -/
def canHandoff (s : State) : Bool :=
  s.prescanJoined && decide (s.readers = 0) && decide (s.liveArtifacts = 0) &&
    decide (s.phase ≠ .analysis)
def handoff (s : State) (selection : Nat) : State :=
  { s with
    generation := s.generation + 1
    selection := some selection
    phase := .analysis
    selectedCache := [] }
def tryHandoff (s : State) (selection : Nat) : Option State :=
  if canHandoff s then some (handoff s selection) else none

/-- Repeating the SAME fully identified snapshot during analysis is a no-op.
    A changed snapshot still needs the joined handoff. Token identity must
    denote the complete immutable policy/catalog content in the adapter. -/
def publish (s : State) (selection : Nat) : Option State :=
  if s.phase = .analysis ∧ s.selection = some selection then some s
  else tryHandoff s selection

theorem repeated_snapshot_is_identity (s : State) (selection : Nat)
    (active : s.phase = .analysis) (same : s.selection = some selection) :
    publish s selection = some s := by simp [publish,active,same]

theorem changed_snapshot_during_analysis_is_rejected (s : State) (selection : Nat)
    (active : s.phase = .analysis) (different : s.selection ≠ some selection) :
    publish s selection = none := by
  simp [publish,active,different,tryHandoff,canHandoff]

theorem handoff_wellFormed (s : State) (selection : Nat) :
    WellFormed (handoff s selection) := by
  intro e h
  simp [handoff] at h

theorem handoff_empty_scoped (s : State) (selection : Nat) :
    (handoff s selection).selectedCache = [] := rfl
theorem handoff_preserves_compatibility (s : State) (selection : Nat) :
    (handoff s selection).compatibility = s.compatibility := rfl
theorem handoff_preserves_fallback_counter (s : State) (selection : Nat) :
    (handoff s selection).fallbackCounter = s.fallbackCounter := rfl
theorem handoff_raw_reply_equal (s : State) (selection : Nat) (key : RawKey) :
    rawLookup key (handoff s selection).compatibility = rawLookup key s.compatibility := rfl

theorem old_scoped_key_cannot_hit (s : State) (selection : Nat) (key : Key)
    (old : key.generation = s.generation) : readScoped (handoff s selection) key = none := by
  apply stale_generation_is_miss
  simp only [handoff]
  omega

theorem old_query_cannot_publish (s : State) (selection : Nat) (entry : Entry)
    (old : entry.key.generation = s.generation) :
    remember (handoff s selection) entry = handoff s selection := by
  unfold remember
  have stale : ¬ current (handoff s selection) entry.key := by
    simp only [current, handoff]
    omega
  exact if_neg stale

theorem handoff_requires_join (s : State) (selection : Nat)
    (notJoined : s.prescanJoined = false) : tryHandoff s selection = none := by
  simp [tryHandoff, canHandoff, notJoined]
theorem handoff_waits_for_readers (s : State) (selection : Nat)
    (busy : s.readers ≠ 0) : tryHandoff s selection = none := by
  simp [tryHandoff, canHandoff, busy]
theorem handoff_waits_for_artifacts (s : State) (selection : Nat)
    (busy : s.liveArtifacts ≠ 0) : tryHandoff s selection = none := by
  simp [tryHandoff, canHandoff, busy]
theorem no_mid_analysis_reselection (s : State) (selection : Nat)
    (active : s.phase = .analysis) : tryHandoff s selection = none := by
  simp [tryHandoff, canHandoff, active]

def enterPrescan (s : State) : State := { s with phase := .prescan, prescanJoined := false }
theorem prescan_does_not_reset_selection (s : State) :
    (enterPrescan s).selection = s.selection := rfl
theorem handoff_scoped_entry_count (s : State) (selection : Nat) :
    (handoff s selection).selectedCache.length = 0 := rfl
theorem handoff_compatibility_entry_count (s : State) (selection : Nat) :
    (handoff s selection).compatibility.length = s.compatibility.length := rfl

/-- Executable Go-compatible broad sink memo. Cache hits do not consume IDs. -/
def unsafeSink (key : RawKey) (s : State) : List Nat × State :=
  match rawLookup key s.compatibility with
  | some value => (value, s)
  | none =>
      let next := s.fallbackCounter + 1
      ([next], { s with compatibility := ⟨key,[next]⟩ :: s.compatibility,
                        fallbackCounter := next })

/-- Preserving the unsafe memo AND counter commutes with every broad query,
    including new signatures which were not present during prescan. -/
theorem unsafe_query_handoff_commutes (key : RawKey) (s : State) (selection : Nat) :
    unsafeSink key (handoff s selection) =
      ((unsafeSink key s).1, handoff (unsafeSink key s).2 selection) := by
  unfold unsafeSink
  simp only [handoff_preserves_compatibility]
  cases h : rawLookup key s.compatibility <;> rfl

theorem unsafe_report_id_preserved (key : RawKey) (s : State) (selection : Nat) :
    (unsafeSink key (handoff s selection)).1 = (unsafeSink key s).1 := by
  rw [unsafe_query_handoff_commutes]

/-- This is the rejected reset: the global counter remains but cached IDs vanish. -/
def naiveClearCompatibility (s : State) : State := { s with compatibility := [] }

namespace Controls

def key : Key := ⟨1, 2, 3, 4, none, .method 7, 8, .normal, .ordinary⟩
def rawKey : RawKey := ⟨1, 2, 3, .method 7, 8, .normal, .ordinary⟩
def state : State :=
  ⟨3, 4, none, .prescan, true, 0, 0, [⟨key, [10, 11]⟩], [⟨rawKey, [42]⟩], 42⟩
def controls : List Bool := [
  decide (readScoped state key = some [10,11]),
  decide (readScoped state { key with view := 9 } = none),
  decide (readScoped state { key with owner := 9 } = none),
  decide (readScoped state { key with role := 9 } = none),
  decide (readScoped state { key with subject := .field 7 } = none),
  decide (readScoped state { key with mode := .relevant } = none),
  decide (readScoped state { key with ends := .withoutEnd } = none),
  decide (readScoped state { key with generation := 5 } = none),
  decide (readScoped state { key with selection := some 0 } = none),
  decide ((handoff state 8).selectedCache = []),
  decide ((handoff state 8).compatibility = state.compatibility),
  decide ((handoff state 8).fallbackCounter = 42),
  decide (rawLookup rawKey (handoff state 8).compatibility = some [42]),
  decide (tryHandoff { state with readers := 1 } 8 = none),
  decide (tryHandoff { state with liveArtifacts := 1 } 8 = none),
  decide (tryHandoff { state with prescanJoined := false } 8 = none),
  decide (tryHandoff { state with phase := .analysis } 8 = none),
  decide (tryHandoff state 8 = some (handoff state 8)),
  decide ((enterPrescan (handoff state 8)).selection = some 8),
  decide (remember (handoff state 8) ⟨key,[20]⟩ = handoff state 8),
  decide ((unsafeSink rawKey state).1 = [42]),
  decide ((unsafeSink rawKey (handoff state 8)).1 = [42]),
  decide ((unsafeSink rawKey (naiveClearCompatibility state)).1 = [43]),
  decide ((unsafeSink { rawKey with subject := .method 99 } state).1 = [43]),
  decide ((unsafeSink { rawKey with subject := .method 99 } (handoff state 8)).1 = [43]),
  decide (publish (handoff state 8) 8 = some (handoff state 8)),
  decide (publish { (handoff state 8) with readers := 2 } 8 =
      some { (handoff state 8) with readers := 2 }),
  decide (publish (handoff state 8) 9 = none)
]
theorem controls_pass : controls.all id = true := by decide
end Controls
end ApSpec.RuleSelectionEpoch
