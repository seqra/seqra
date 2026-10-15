/-
  Run-1 request publication and replay, with no callee supplier history/memo.

  Q is one logical (full MethodKey, normal slot, concrete mark) cell. Runtime
  stores pack cells into mark sets; lists here are executable finite sets.
  The caller's exact input relation is a separate existing owner. Histories
  below are proof observations, never fields of the runtime state.
-/
import ApSpec.RequestSets

namespace ApSpec.Run1RequestProtocol

variable {Q : Type} [DecidableEq Q]

def insert (q : Q) (xs : List Q) : List Q :=
  if q ∈ xs then xs else q :: xs

@[simp] theorem mem_insert (q x : Q) (xs : List Q) :
    x ∈ insert q xs ↔ x = q ∨ x ∈ xs := by
  by_cases h : q ∈ xs
  · simp only [insert, if_pos h]
    constructor
    · exact Or.inr
    · rintro (rfl | hx)
      · exact h
      · exact hx
  · simp [insert, h]

def merge : List Q → List Q → List Q
  | [], ys => ys
  | x :: xs, ys => insert x (merge xs ys)

@[simp] theorem mem_merge (xs ys : List Q) (q : Q) :
    q ∈ merge xs ys ↔ q ∈ xs ∨ q ∈ ys := by
  induction xs with
  | nil => simp [merge]
  | cons x xs ih => simp [merge, ih, or_assoc]

theorem insert_nodup (q : Q) (xs : List Q) (h : xs.Nodup) :
    (insert q xs).Nodup := by
  by_cases present : q ∈ xs
  · simpa [insert, present] using h
  · simpa [insert, present] using List.nodup_cons.mpr ⟨present, h⟩

theorem merge_nodup (xs ys : List Q) (h : ys.Nodup) :
    (merge xs ys).Nodup := by
  induction xs with
  | nil => exact h
  | cons x xs ih => exact insert_nodup x _ ih

def difference (offered admitted : List Q) : List Q :=
  offered.filter fun q => decide (q ∉ admitted)

@[simp] theorem mem_difference (offered admitted : List Q) (q : Q) :
    q ∈ difference offered admitted ↔ q ∈ offered ∧ q ∉ admitted := by
  simp [difference]

structure Stores (Q : Type) where
  admitted : List Q
  temporary : List Q
  deriving Repr

def empty : Stores Q := ⟨[], []⟩

def collect (s : Stores Q) (incoming : List Q) : Stores Q :=
  {s with temporary := merge incoming s.temporary}

def fresh (s : Stores Q) : List Q := difference s.temporary s.admitted

/-- Synchronous commit followed by owner-local replacement of temporary. -/
def commitAndReplace (s : Stores Q) : Stores Q :=
  ⟨merge s.temporary s.admitted, []⟩

def holds (s : Stores Q) (q : Q) : Prop :=
  q ∈ s.admitted ∨ q ∈ s.temporary

@[simp] theorem collect_holds (s : Stores Q) (incoming : List Q) (q : Q) :
    holds (collect s incoming) q ↔ q ∈ incoming ∨ holds s q := by
  simp only [holds, collect, mem_merge]
  constructor
  · rintro (ha | hi | hp)
    · exact Or.inr (Or.inl ha)
    · exact Or.inl hi
    · exact Or.inr (Or.inr hp)
  · rintro (hi | ha | hp)
    · exact Or.inr (Or.inl hi)
    · exact Or.inl ha
    · exact Or.inr (Or.inr hp)

@[simp] theorem commit_holds (s : Stores Q) (q : Q) :
    holds (commitAndReplace s) q ↔ holds s q := by
  simp [holds, commitAndReplace, or_comm]

theorem commit_membership (s : Stores Q) (q : Q) :
    q ∈ (commitAndReplace s).admitted ↔ q ∈ s.admitted ∨ q ∈ fresh s := by
  simp only [commitAndReplace, mem_merge, fresh, mem_difference]
  by_cases h : q ∈ s.admitted <;> simp [h]

@[simp] theorem commit_temporary_empty (s : Stores Q) :
    (commitAndReplace s).temporary = [] := rfl

theorem collect_temporary_nodup (s : Stores Q) (incoming : List Q)
    (h : s.temporary.Nodup) : (collect s incoming).temporary.Nodup :=
  merge_nodup incoming s.temporary h

theorem commit_admitted_nodup (s : Stores Q) (h : s.admitted.Nodup) :
    (commitAndReplace s).admitted.Nodup := merge_nodup _ _ h

theorem fresh_nodup (s : Stores Q) (h : s.temporary.Nodup) :
    (fresh s).Nodup := h.filter _

theorem fresh_not_admitted (s : Stores Q) (q : Q) (h : q ∈ fresh s) :
    q ∉ s.admitted := (mem_difference _ _ _).mp h |>.2

theorem second_commit_no_notification (s : Stores Q) :
    fresh (commitAndReplace s) = [] := by simp [fresh, difference, commitAndReplace]

/-- A stale destination read can add duplicates, never lose a required cell. -/
theorem filter_destination_safe (offered snapshot current : List Q)
    (snapshotOld : ∀ q, q ∈ snapshot → q ∈ current) (q : Q) :
    q ∈ merge (difference offered snapshot) current ↔ q ∈ offered ∨ q ∈ current := by
  simp only [mem_merge, mem_difference]
  constructor
  · rintro (⟨ho, _⟩ | hc)
    · exact Or.inl ho
    · exact Or.inr hc
  · rintro (ho | hc)
    · by_cases hs : q ∈ snapshot
      · exact Or.inr (snapshotOld q hs)
      · exact Or.inl ⟨ho, hs⟩
    · exact Or.inr hc

/-- Exclusion already collected the same cells. Filtering the later batch is
    therefore unnecessary, even while exclusion requests are still pending. -/
theorem excluded_forwarding_same_union (excluded offered previous : List Q) (q : Q) :
    q ∈ merge offered (merge excluded previous) ↔
      q ∈ merge (difference offered excluded) (merge excluded previous) := by
  simp only [mem_merge, mem_difference]
  by_cases hx : q ∈ excluded <;> simp [hx]

section ExclusionProvenance

variable {Origin Mark : Type} [DecidableEq Origin] [DecidableEq Mark]

/-- An executable finite derivation certificate. `needs` is a ghost observation
    of requests emitted/routed by the operations, not another runtime store.
    Origin ranges over normal abstract (full method, slot) origins, never roots.
    Every generic excluded target uses `exclude`, just like a selected cleaner.
    A call/summary read routes needs to the original caller origin. -/
inductive FlowDerivation : Origin → List Mark → List (Origin × Mark) → Type where
  | input (origin : Origin) : FlowDerivation origin [] []
  | exclude {origin excluded needs} (prior : FlowDerivation origin excluded needs)
      (added : List Mark) :
      FlowDerivation origin (merge added excluded)
        (merge (added.map fun t => (origin,t)) needs)
  | transfer {origin excluded needs} (prior : FlowDerivation origin excluded needs) :
      FlowDerivation origin excluded needs
  | call {callee excluded needs} (prior : FlowDerivation callee excluded needs)
      (caller : Origin) :
      FlowDerivation caller excluded (needs.map fun q => (caller,q.2))
  | join {origin leftX leftN rightX rightN}
      (left : FlowDerivation origin leftX leftN)
      (right : FlowDerivation origin rightX rightN) :
      FlowDerivation origin (leftX.filter fun t => decide (t ∈ rightX))
        (merge leftN rightN)

/-- Creator-to-caller induction, without assuming the desired provenance law. -/
theorem excluded_has_emitted_need {origin : Origin} {excluded : List Mark}
    {needs : List (Origin × Mark)} (derivation : FlowDerivation origin excluded needs)
    (t : Mark) (present : t ∈ excluded) : (origin,t) ∈ needs := by
  induction derivation with
  | input => simp at present
  | exclude prior added ih =>
    rcases (mem_merge _ _ _).mp present with newly | old
    · exact (mem_merge _ _ _).mpr (Or.inl (List.mem_map.mpr ⟨t,newly,rfl⟩))
    · exact (mem_merge _ _ _).mpr (Or.inr (ih old))
  | transfer prior ih => exact ih present
  | call prior caller ih =>
    exact List.mem_map.mpr ⟨_,ih present,rfl⟩
  | join left right ihLeft ihRight =>
    exact (mem_merge _ _ _).mpr (Or.inl (ihLeft (List.mem_filter.mp present).1))

/-- The concrete cell is computed from the supplied origin and mark; proof
    evidence does not select runtime data from an existential proposition. -/
def excludedNeedWitness {origin : Origin} {excluded : List Mark}
    {needs : List (Origin × Mark)} (derivation : FlowDerivation origin excluded needs)
    (t : Mark) (present : t ∈ excluded) : {q : Origin × Mark // q ∈ needs} :=
  ⟨(origin,t), excluded_has_emitted_need derivation t present⟩

/-- End-to-end exclusion-test erasure for a derived FLOW, including pending
    creator requests and call-origin remapping. Equality is logical membership. -/
theorem derived_flow_exclusion_erasure {origin : Origin} {excluded : List Mark}
    {needs : List (Origin × Mark)} (derivation : FlowDerivation origin excluded needs)
    (offered : List Mark) (previous : List (Origin × Mark)) (q : Origin × Mark) :
    q ∈ merge (offered.map fun t => (origin,t)) (merge needs previous) ↔
      q ∈ merge ((offered.filter fun t => decide (t ∉ excluded)).map fun t => (origin,t))
        (merge needs previous) := by
  simp only [mem_merge]
  constructor
  · rintro (added | old)
    · obtain ⟨t,ht,rfl⟩ := List.mem_map.mp added
      by_cases hx : t ∈ excluded
      · exact Or.inr (Or.inl (excluded_has_emitted_need derivation t hx))
      · exact Or.inl (List.mem_map.mpr ⟨t,List.mem_filter.mpr ⟨ht,by simp [hx]⟩,rfl⟩)
    · exact Or.inr old
  · rintro (added | old)
    · obtain ⟨t,ht,rfl⟩ := List.mem_map.mp added
      exact Or.inl (List.mem_map.mpr ⟨t,(List.mem_filter.mp ht).1,rfl⟩)
    · exact Or.inr old

end ExclusionProvenance

inductive Event (Q : Type) where
  | collect (incoming : List Q)
  | barrier
  deriving Repr

def step (s : Stores Q) : Event Q → Stores Q
  | .collect incoming => collect s incoming
  | .barrier => commitAndReplace s

def observe (history : List Q) : Event Q → List Q
  | .collect incoming => merge incoming history
  | .barrier => history

def Encodes (s : Stores Q) (history : List Q) : Prop :=
  ∀ q, holds s q ↔ q ∈ history

omit [DecidableEq Q] in
theorem empty_encodes : Encodes (empty : Stores Q) [] := by
  intro q
  simp [holds, empty]

theorem step_encodes (s : Stores Q) (history : List Q)
    (h : Encodes s history) (event : Event Q) :
    Encodes (step s event) (observe history event) := by
  intro q
  cases event with
  | collect incoming => simp [step, observe, h q]
  | barrier => simp [step, observe, h q]

def run (s : Stores Q) (events : List (Event Q)) : Stores Q := events.foldl step s
def history (events : List (Event Q)) : List Q := events.foldl observe []

theorem trace_encodes (s : Stores Q) (seen : List Q) (h : Encodes s seen)
    (events : List (Event Q)) :
    Encodes (run s events) (events.foldl observe seen) := by
  induction events generalizing s seen with
  | nil => exact h
  | cons event events ih => exact ih _ _ (step_encodes s seen h event)

theorem every_prefix_retains (events : List (Event Q)) (q : Q) :
    holds (run empty events) q ↔ q ∈ history events :=
  trace_encodes empty [] empty_encodes events q

/-- A successful final method barrier admits every collected request cell. -/
theorem completed_admits_all (events : List (Event Q)) (q : Q) :
    q ∈ (commitAndReplace (run empty events)).admitted ↔ q ∈ history events := by
  have h := every_prefix_retains events q
  simpa [commitAndReplace, holds, or_comm] using h

/-- Mark batching and barrier placement change timing, not final membership. -/
theorem two_batches_one_barrier (s : Stores Q) (a b : List Q) (q : Q) :
    q ∈ (commitAndReplace (collect (collect s a) b)).admitted ↔
      q ∈ (commitAndReplace (collect s (merge a b))).admitted := by
  simp only [commitAndReplace, collect, mem_merge]
  constructor <;> intro h <;> rcases h with h | h
  · rcases h with hb | ha | hp
    · exact Or.inl (Or.inl (Or.inr hb))
    · exact Or.inl (Or.inl (Or.inl ha))
    · exact Or.inl (Or.inr hp)
  · exact Or.inr h
  · rcases h with (ha | hb) | hp
    · exact Or.inl (Or.inr (Or.inl ha))
    · exact Or.inl (Or.inl hb)
    · exact Or.inl (Or.inr (Or.inr hp))
  · exact Or.inr h

theorem barrier_placement_same_union (s : Stores Q) (a b : List Q) (q : Q) :
    q ∈ (commitAndReplace (collect (commitAndReplace (collect s a)) b)).admitted ↔
      q ∈ (commitAndReplace (collect (collect s a) b)).admitted := by
  simp [commitAndReplace, collect, or_assoc]

theorem post_commit_collect_uses_new_temporary (s : Stores Q) (incoming : List Q) (q : Q) :
    q ∈ (collect (commitAndReplace s) incoming).temporary ↔ q ∈ incoming := by
  simp [collect, commitAndReplace]

/-- One finite input relation and one finite request list produce a computed
    witness for each concrete answer. No callee input relation is added. -/
def answers {I A : Type} (inputs : List I) (requests : List Q)
    (answer : I → Q → List A) : List A :=
  inputs.flatMap fun input => requests.flatMap fun q => answer input q

omit [DecidableEq Q] in
theorem answer_witness {I A : Type} (inputs : List I) (requests : List Q)
    (answer : I → Q → List A) (a : A) :
    a ∈ answers inputs requests answer ↔
      ∃ input, input ∈ inputs ∧ ∃ q, q ∈ requests ∧ a ∈ answer input q := by
  simp [answers, List.mem_flatMap]

inductive Arm where
  | inputRead
  | requestReplay
  deriving DecidableEq, Repr

/-- Times are logical linearization epochs. Input admission precedes its
    abstraction read; request commit precedes its queued replay. -/
def raceArm (inputRead requestCommit : Nat) : Arm :=
  if requestCommit ≤ inputRead then .inputRead else .requestReplay

def ArmCovers (arm : Arm) (admission inputRead requestCommit replay : Nat) : Prop :=
  match arm with
  | .inputRead => requestCommit ≤ inputRead
  | .requestReplay => admission < replay

/-- Executable choice of the arm that cannot miss the input/request pair. -/
theorem both_arrival_orders (admission inputRead requestCommit replay : Nat)
    (admittedFirst : admission < inputRead) (committedFirst : requestCommit < replay) :
    ArmCovers (raceArm inputRead requestCommit) admission inputRead requestCommit replay := by
  by_cases h : requestCommit ≤ inputRead
  · simp [raceArm, h, ArmCovers]
  · simp only [raceArm, if_neg h, ArmCovers]
    exact Nat.lt_trans admittedFirst (Nat.lt_trans (Nat.lt_of_not_ge h) committedFirst)

/-- Register before reading membership: either old publication is read, or
    subsequent notification sees the registered subscriber. -/
theorem registration_race (registration snapshot publication : Nat)
    (registerFirst : registration < snapshot) :
    publication ≤ snapshot ∨ registration < publication := by
  by_cases h : publication ≤ snapshot
  · exact Or.inl h
  · exact Or.inr (Nat.lt_trans registerFirst (Nat.lt_of_not_ge h))

/-- Completion includes the existing queue/publication/request/outgoing owners.
    This is a local protocol law, not a proof that all real programs terminate. -/
theorem completion_empty_owners (facts publications requests continuation outgoing : Bool) :
    RequestSets.workComplete facts publications requests continuation outgoing = true ↔
      facts = false ∧ publications = false ∧ requests = false ∧
      continuation = false ∧ outgoing = false :=
  RequestSets.work_complete_iff facts publications requests continuation outgoing

def batchVector : Stores Nat :=
  commitAndReplace (collect (collect empty [1, 1, 2]) [2, 3])

theorem batch_vector_members :
    batchVector.admitted = [3, 1, 2] ∧ batchVector.temporary = [] := by decide

end ApSpec.Run1RequestProtocol
