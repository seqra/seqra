/-
  Finite semantic progress for an AP-only run-1 owner scheduler.

  This is an executable serialized owner model, not a theorem about the JVM
  queues. A semantic key includes its owner and kind. The finite universe must
  cover the bounded AP/premise/stage relations, requests, summaries, static
  regions, supplies and delivery memberships of the selected program/catalog.
  Keys have no formula, revision, history or mutable object identity component.

  A fresh membership owns one finite unchanged-work burst. A burst visits each
  local repeat key at most once; fresh memberships are staged in its pending
  batch. Only an actual empty local queue commits that batch and secures child
  reservations before releasing its parent. Quantum yields do not commit.
  Unanswered interests are dormant metadata, not queued reservations.

  The bound covers work/commit transitions. Arbitrarily many external scheduler
  stalls are not productive transitions. The driver below always resumes work.
  AP transfer completeness, finite key encoding, actual Kotlin owner isolation,
  secured IO and fair scheduling are production refinement obligations.
-/
import ApSpec.BottomUpStaticTransport

namespace ApSpec.Run1SemanticProgress

set_option autoImplicit false

inductive Kind where
  | ap | request | summary | write | read | interest | supply | delivery | source | query
  deriving DecidableEq, Repr

/-- `slot` encodes a complete semantic relation, not just a group or field. -/
structure Key (owners slots : Nat) where
  kind : Kind
  owner : Fin owners
  slot : Fin slots
  deriving DecidableEq, Repr

/-- Production must encode the complete bounded relation faithfully. This is
    an adapter obligation, not permission to clip a fact or alias two stages. -/
structure Encoding (Actual : Type) (owners slots : Nat) where
  encode : Actual → Key owners slots
  decode : Key owners slots → Option Actual
  roundtrip : ∀ actual, decode (encode actual) = some actual

theorem encoding_does_not_alias {Actual : Type} {owners slots : Nat}
    (encoding : Encoding Actual owners slots) (a b : Actual)
    (equal : encoding.encode a = encoding.encode b) : a = b := by
  have observed := congrArg encoding.decode equal
  rw [encoding.roundtrip a, encoding.roundtrip b] at observed
  exact Option.some.inj observed

/-- Finite removal; it also handles a redundant encoding of the universe. -/
def removeAll {α : Type} [DecidableEq α] (key : α) : List α → List α
  | [] => []
  | x :: xs => if x = key then removeAll key xs else x :: removeAll key xs

theorem remove_all_length_le {α : Type} [DecidableEq α] (key : α) (xs : List α) :
    (removeAll key xs).length ≤ xs.length := by
  induction xs with
  | nil => simp [removeAll]
  | cons x xs ih =>
    by_cases same : x = key <;> simp [removeAll, same] <;> omega

theorem remove_all_shorter {α : Type} [DecidableEq α] (key : α) (xs : List α)
    (present : key ∈ xs) : (removeAll key xs).length < xs.length := by
  induction xs with
  | nil => simp at present
  | cons x xs ih =>
    by_cases same : x = key
    · simp only [removeAll, same, if_true, List.length_cons]
      have bound := remove_all_length_le key xs
      omega
    · have tail : key ∈ xs := by
        rcases List.mem_cons.mp present with equal | found
        · exact False.elim (same equal.symm)
        · exact found
      have shorter := ih tail
      simp [removeAll, same]
      omega

@[simp] theorem key_not_in_remove_all {α : Type} [DecidableEq α] (key : α) (xs : List α) :
    key ∉ removeAll key xs := by
  induction xs with
  | nil => simp [removeAll]
  | cons x xs ih =>
    by_cases same : x = key
    · simp [removeAll, same, ih]
    · simp [removeAll, same, ih, Ne.symm same]

@[simp] theorem mem_remove_all {α : Type} [DecidableEq α] (key removed : α) (xs : List α) :
    key ∈ removeAll removed xs ↔ key ∈ xs ∧ key ≠ removed := by
  induction xs with
  | nil => simp [removeAll]
  | cons x xs ih =>
    by_cases same : x = removed
    · subst x
      by_cases equal : key = removed
      · subst key; simp [removeAll]
      · simp [removeAll, ih, equal]
    · by_cases equal : key = x
      · subst key
        simp [removeAll, same, ih]
      · simp [removeAll, same, equal, ih]

/-- Output is (still available, newly admitted), with no duplicate admissions. -/
def admitMany {α : Type} [DecidableEq α] (available : List α) : List α → List α × List α
  | [] => (available, [])
  | key :: rest =>
    if key ∈ available then
      let tail := admitMany (removeAll key available) rest
      (tail.1, key :: tail.2)
    else admitMany available rest

theorem admit_many_count {α : Type} [DecidableEq α] (available offered : List α) :
    (admitMany available offered).1.length + (admitMany available offered).2.length ≤ available.length := by
  induction offered generalizing available with
  | nil => simp [admitMany]
  | cons key rest ih =>
    by_cases present : key ∈ available
    · have bound := ih (removeAll key available)
      have shorter := remove_all_shorter key available present
      simp only [admitMany, present, if_true, List.length_cons]
      omega
    · simpa [admitMany, present] using ih available

/-- Every offered previously absent membership is admitted; the list is not a
    clipping budget. The full semantic universe is proved complete below. -/
theorem admit_many_fresh_exact {α : Type} [DecidableEq α] (key : α)
    (available offered : List α) :
    key ∈ (admitMany available offered).2 ↔ key ∈ offered ∧ key ∈ available := by
  induction offered generalizing available with
  | nil => simp [admitMany]
  | cons x xs ih =>
    by_cases present : x ∈ available
    · by_cases same : key = x
      · subst key
        simp [admitMany, present]
      · simp [admitMany, present, ih, same]
    · by_cases same : key = x
      · subst key
        simp [admitMany, present, ih]
      · simp [admitMany, present, ih, same]

theorem admit_many_remaining_subset {α : Type} [DecidableEq α] (key : α)
    (available offered : List α) :
    key ∈ (admitMany available offered).1 → key ∈ available := by
  induction offered generalizing available with
  | nil => simp [admitMany]
  | cons x xs ih =>
    by_cases present : x ∈ available
    · intro found
      have next : key ∈ (admitMany (removeAll x available) xs).1 := by
        simpa [admitMany, present] using found
      exact (mem_remove_all key x available).mp (ih _ next) |>.1
    · simpa [admitMany, present] using ih available

theorem no_fresh_duplicate_schedule {α : Type} [DecidableEq α]
    (available offered : List α) (old : ∀ key ∈ offered, key ∉ available) :
    admitMany available offered = (available, []) := by
  induction offered with
  | nil => rfl
  | cons key rest ih =>
    have absent := old key (by simp)
    have tail : ∀ value ∈ rest, value ∉ available := fun value found => old value (by simp [found])
    simp [admitMany, absent, ih tail]

@[simp] theorem duplicate_offer_no_growth {α : Type} [DecidableEq α]
    (key : α) (available : List α) (absent : key ∉ available) :
    admitMany available [key] = (available, []) := by
  simp [admitMany, absent]

theorem repeated_singleton_admitted_once {α : Type} [DecidableEq α]
    (key : α) (available : List α) (present : key ∈ available) :
    admitMany available [key, key] = (removeAll key available, [key]) := by
  simp [admitMany, present]

structure Burst (owners slots workKeys : Nat) where
  cause : Key owners slots
  todo : List (Fin workKeys)
  available : List (Fin workKeys)
  pending : List (Key owners slots)
  deriving DecidableEq, Repr

structure State (owners slots workKeys : Nat) where
  available : List (Key owners slots)
  bursts : List (Burst owners slots workKeys)
  committed : List (Key owners slots)
  dormant : List (Key owners slots)
  failed : Bool := false
  deriving DecidableEq, Repr

def allKinds : List Kind := [.ap, .request, .summary, .write, .read, .interest,
  .supply, .delivery, .source, .query]

def allKeys (owners slots : Nat) : List (Key owners slots) :=
  allKinds.flatMap fun kind => (List.finRange owners).flatMap fun owner =>
    (List.finRange slots).map fun slot => ⟨kind, owner, slot⟩

theorem all_kinds_complete (kind : Kind) : kind ∈ allKinds := by
  cases kind <;> simp [allKinds]

theorem all_keys_complete {owners slots : Nat} (key : Key owners slots) : key ∈ allKeys owners slots := by
  exact List.mem_flatMap.mpr ⟨key.kind, all_kinds_complete key.kind,
    List.mem_flatMap.mpr ⟨key.owner, List.mem_finRange _,
      List.mem_map.mpr ⟨key.slot, List.mem_finRange _, rfl⟩⟩⟩

structure Program (owners slots workKeys : Nat) where
  entry : Key owners slots → List (Fin workKeys)
  next : State owners slots workKeys → Fin owners → Fin workKeys → List (Fin workKeys)
  offers : State owners slots workKeys → Fin owners → Fin workKeys → List (Key owners slots)

variable {o n w : Nat}

/-- The full finite type is enumerated, never a sampled/selected fact cutoff. -/
def Program.keys (_program : Program o n w) : List (Key o n) := allKeys o n

theorem program_keys_complete (program : Program o n w) (key : Key o n) :
    key ∈ program.keys := all_keys_complete key

theorem semantic_offer_not_clipped (program : Program o n w) (state : State o n w)
    (owner : Fin o) (work : Fin w) (key : Key o n)
    (offered : key ∈ program.offers state owner work) :
    key ∈ (admitMany state.available (program.offers state owner work)).2 ∨
      (key ∈ program.keys ∧ key ∉ state.available) := by
  by_cases fresh : key ∈ state.available
  · exact Or.inl ((admit_many_fresh_exact _ _ _).mpr ⟨offered, fresh⟩)
  · exact Or.inr ⟨program_keys_complete program key, fresh⟩

def startBurst (program : Program o n w) (key : Key o n) : Burst o n w :=
  let selected := admitMany (List.finRange w) (program.entry key)
  ⟨key, selected.2, selected.1, []⟩

def burstWeight (burst : Burst o n w) : Nat :=
  burst.todo.length + burst.available.length + 1 + burst.pending.length * (w + 2)

def queueWeight (bursts : List (Burst o n w)) : Nat :=
  (bursts.map burstWeight).sum

def potential (state : State o n w) : Nat :=
  state.available.length * (w + 3) + queueWeight state.bursts

theorem start_burst_bound (program : Program o n w) (key : Key o n) :
    burstWeight (startBurst program key) ≤ w + 1 := by
  have bound := admit_many_count (List.finRange w) (program.entry key)
  simp only [List.length_finRange] at bound
  simp only [burstWeight, startBurst, List.length_nil, Nat.zero_mul, Nat.add_zero]
  omega

theorem child_queue_bound (program : Program o n w) (keys : List (Key o n)) :
    queueWeight (keys.map (startBurst program)) ≤ keys.length * (w + 1) := by
  induction keys with
  | nil => simp [queueWeight]
  | cons key keys ih =>
    have bound := start_burst_bound program key
    simp only [queueWeight, List.map_cons, List.sum_cons, List.length_cons, Nat.add_mul,
      Nat.one_mul] at *
    omega

/-- This is local unchanged-work processing. Pending values are not committed here. -/
def process (program : Program o n w) (state : State o n w) (burst : Burst o n w)
    (work : Fin w) (tail : List (Fin w)) (rest : List (Burst o n w)) : State o n w :=
  let localNext := admitMany burst.available (program.next state burst.cause.owner work)
  let fresh := admitMany state.available (program.offers state burst.cause.owner work)
  { state with
    available := fresh.1
    bursts := { burst with
      todo := tail ++ localNext.2
      available := localNext.1
      pending := burst.pending ++ fresh.2 } :: rest }

/-- All children are secured atomically in this model before parent release.
    A rejected actual send must take the separate failed/incomplete path. -/
def commit (program : Program o n w) (state : State o n w) (burst : Burst o n w)
    (rest : List (Burst o n w)) : State o n w :=
  { state with
    bursts := rest ++ burst.pending.map (startBurst program)
    committed := state.committed ++ burst.pending }

def step (program : Program o n w) (state : State o n w) : Option (State o n w) :=
  if state.failed then none else
  match state.bursts with
  | [] => none
  | burst :: rest => match burst.todo with
    | [] => some (commit program state burst rest)
    | work :: tail => some (process program state burst work tail rest)

theorem process_decreases (program : Program o n w) (state : State o n w)
    (burst : Burst o n w) (work : Fin w) (tail : List (Fin w))
    (rest : List (Burst o n w)) (queued : state.bursts = burst :: rest)
    (head : burst.todo = work :: tail) :
    potential (process program state burst work tail rest) < potential state := by
  have localBound := admit_many_count burst.available (program.next state burst.cause.owner work)
  have fresh := admit_many_count state.available (program.offers state burst.cause.owner work)
  have weighted := Nat.mul_le_mul_right (w + 3) fresh
  have cheaper := Nat.mul_le_mul_left
    (admitMany state.available (program.offers state burst.cause.owner work)).2.length
    (show w + 2 ≤ w + 3 by omega)
  simp only [Nat.add_mul] at weighted
  simp only [potential, process, queueWeight, List.map_cons, List.sum_cons, burstWeight,
    List.length_append, List.length_cons, queued, head, Nat.add_mul]
  omega

theorem commit_decreases (program : Program o n w) (state : State o n w)
    (burst : Burst o n w) (rest : List (Burst o n w))
    (queued : state.bursts = burst :: rest) (empty : burst.todo = []) :
    potential (commit program state burst rest) < potential state := by
  have children := child_queue_bound program burst.pending
  have cheaper := Nat.mul_le_mul_left burst.pending.length
    (show w + 1 ≤ w + 2 by omega)
  simp only [potential, commit, queueWeight, List.map_append, List.sum_append,
    List.map_cons, List.sum_cons, burstWeight, queued, empty, List.length_nil,
    Nat.zero_add] at *
  omega

theorem step_decreases (program : Program o n w) (state result : State o n w)
    (advanced : step program state = some result) : potential result < potential state := by
  unfold step at advanced
  split at advanced
  · contradiction
  · cases hq : state.bursts with
    | nil => simp [hq] at advanced
    | cons burst rest =>
      cases ht : burst.todo with
      | nil =>
        simp only [hq, ht] at advanced
        cases advanced
        exact commit_decreases program state burst rest hq ht
      | cons work tail =>
        simp only [hq, ht] at advanced
        cases advanced
        exact process_decreases program state burst work tail rest hq ht

/-- Traces of the head-selection driver. `AnyTrace` below also permits arbitrary
    queued-owner and pending-local-work selection. -/
inductive Trace (program : Program o n w) : State o n w → Nat → State o n w → Prop
  | done (state) : Trace program state 0 state
  | next {before middle after count} (first : step program before = some middle)
      (rest : Trace program middle count after) : Trace program before (count + 1) after

theorem trace_length_bound (program : Program o n w) {before after : State o n w}
    {count : Nat} (trace : Trace program before count after) :
    count + potential after ≤ potential before := by
  induction trace with
  | done state => simp
  | next first rest ih =>
    have smaller := step_decreases program _ _ first
    omega

theorem semantic_availability_only_shrinks (program : Program o n w)
    (state result : State o n w) (advanced : step program state = some result)
    (key : Key o n) : key ∈ result.available → key ∈ state.available := by
  unfold step at advanced
  split at advanced
  · contradiction
  · cases hq : state.bursts with
    | nil => simp [hq] at advanced
    | cons burst rest =>
      cases ht : burst.todo with
      | nil =>
        simp only [hq, ht] at advanced
        cases advanced
        exact fun present => present
      | cons work tail =>
        simp only [hq, ht] at advanced
        cases advanced
        exact admit_many_remaining_subset key state.available (program.offers state burst.cause.owner work)

/-- Bounded driver: no complete-run or termination assumption is an argument. -/
def run (program : Program o n w) : Nat → State o n w → State o n w
  | 0, state => state
  | fuel + 1, state => match step program state with
    | none => state
    | some next => run program fuel next

theorem run_terminal (program : Program o n w) (fuel : Nat) (state : State o n w)
    (enough : potential state ≤ fuel) : step program (run program fuel state) = none := by
  induction fuel generalizing state with
  | zero =>
    cases hs : step program state with
    | none => simp [run, hs]
    | some next =>
      have smaller := step_decreases program state next hs
      omega
  | succ fuel ih =>
    cases hs : step program state with
    | none => simp [run, hs]
    | some next =>
      have smaller := step_decreases program state next hs
      simpa [run, hs] using ih next (by omega)

def Quiescent (state : State o n w) : Prop := state.failed = false ∧ state.bursts = []

instance (state : State o n w) : Decidable (Quiescent state) := inferInstanceAs (Decidable (state.failed = false ∧ state.bursts = []))

theorem step_none_iff (program : Program o n w) (state : State o n w) :
    step program state = none ↔ state.failed = true ∨ state.bursts = [] := by
  cases hf : state.failed <;> cases hq : state.bursts <;> simp [step, hf, hq]
  rename_i burst rest
  cases burst.todo <;> simp

theorem step_preserves_failure (program : Program o n w) (state result : State o n w)
    (advanced : step program state = some result) : result.failed = state.failed := by
  unfold step at advanced
  split at advanced
  · contradiction
  · cases hq : state.bursts with
    | nil => simp [hq] at advanced
    | cons burst rest =>
      cases ht : burst.todo <;> simp only [hq, ht] at advanced <;> cases advanced <;> rfl

theorem run_preserves_failure (program : Program o n w) (fuel : Nat) (state : State o n w) :
    (run program fuel state).failed = state.failed := by
  induction fuel generalizing state with
  | zero => rfl
  | succ fuel ih =>
    cases hs : step program state with
    | none => simp [run, hs]
    | some next =>
      simp only [run, hs]
      exact (ih next).trans (step_preserves_failure program state next hs)

theorem bounded_quiescence (program : Program o n w) (state : State o n w)
    (secured : state.failed = false) :
    Quiescent (run program (potential state) state) := by
  have terminal := run_terminal program (potential state) state (Nat.le_refl _)
  have safe := (run_preserves_failure program (potential state) state).trans secured
  constructor
  · exact safe
  · rcases (step_none_iff program _).mp terminal with failed | empty
    · simp [safe] at failed
    · exact empty

def initial (program : Program o n w) (seeds : List (Key o n))
    (dormant : List (Key o n) := []) : State o n w :=
  let selected := admitMany program.keys seeds
  ⟨selected.1, selected.2.map (startBurst program), selected.2, dormant, false⟩

theorem initial_bound (program : Program o n w) (seeds dormant : List (Key o n)) :
    potential (initial program seeds dormant) ≤ program.keys.length * (w + 3) := by
  have selected := admit_many_count program.keys seeds
  have weighted := Nat.mul_le_mul_right (w + 3) selected
  have children := child_queue_bound program (admitMany program.keys seeds).2
  have cheaper := Nat.mul_le_mul_left (admitMany program.keys seeds).2.length
    (show w + 1 ≤ w + 3 by omega)
  simp only [Nat.add_mul] at weighted
  simp only [potential, initial]
  omega

theorem initial_finite_quiescence (program : Program o n w) (seeds dormant : List (Key o n)) :
    Quiescent (run program (program.keys.length * (w + 3)) (initial program seeds dormant)) := by
  have terminal := run_terminal program _ (initial program seeds dormant) (initial_bound program seeds dormant)
  have safe := run_preserves_failure program (program.keys.length * (w + 3)) (initial program seeds dormant)
  constructor
  · exact safe
  · rcases (step_none_iff program _).mp terminal with failed | empty
    · have safeFalse : (run program (program.keys.length * (w + 3)) (initial program seeds dormant)).failed = false := safe
      rw [safeFalse] at failed
      contradiction
    · exact empty

/-- Reordering does not change the finite semantic credit. The permutation
    witness is constructive and carries no choice of a runtime scheduler. -/
theorem queue_weight_permutation {before after : List (Burst o n w)}
    (order : before.Perm after) : queueWeight before = queueWeight after := by
  exact (order.map burstWeight).sum_nat

theorem burst_weight_work_permutation (burst : Burst o n w) (todo : List (Fin w))
    (order : burst.todo.Perm todo) :
    burstWeight { burst with todo := todo } = burstWeight burst := by
  simp only [burstWeight]
  rw [order.length_eq]

/-- Move any queued burst to the head and select any ordering of its remaining
    local work. No key, pending notification or owner field is removed. -/
def selectState (state : State o n w) (burst : Burst o n w)
    (rest : List (Burst o n w)) (todo : List (Fin w)) : State o n w :=
  { state with bursts := { burst with todo := todo } :: rest }

inductive Selection (before : State o n w) : State o n w → Prop
  | selected (burst : Burst o n w) (rest : List (Burst o n w)) (todo : List (Fin w))
      (queued : before.bursts.Perm (burst :: rest)) (work : burst.todo.Perm todo) :
      Selection before (selectState before burst rest todo)

theorem selection_preserves_potential {before selected : State o n w}
    (selection : Selection before selected) : potential selected = potential before := by
  cases selection with
  | selected burst rest todo queued work =>
    have queue := queue_weight_permutation queued
    have localWeight := burst_weight_work_permutation burst todo work
    simp only [potential, selectState, queueWeight, List.map_cons, List.sum_cons] at *
    omega

theorem selection_preserves_failure {before selected : State o n w}
    (selection : Selection before selected) : selected.failed = before.failed := by
  cases selection
  rfl

theorem selection_preserves_available {before selected : State o n w}
    (selection : Selection before selected) : selected.available = before.available := by
  cases selection
  rfl

/-- Every productive selected owner/local-work step is allowed. Program callbacks
    receive the selected state, so no order-insensitivity premise is hidden here.
    This is still a serialized model; JVM/IO simulation is an adapter obligation. -/
inductive AnyStep (program : Program o n w) : State o n w → State o n w → Prop
  | selected {before chosen after} (selection : Selection before chosen)
      (advanced : step program chosen = some after) : AnyStep program before after

theorem any_step_decreases (program : Program o n w) {before after : State o n w}
    (advanced : AnyStep program before after) : potential after < potential before := by
  cases advanced with
  | selected selection first =>
    have smaller := step_decreases program _ _ first
    rw [selection_preserves_potential selection] at smaller
    exact smaller

theorem any_step_preserves_failure (program : Program o n w) {before after : State o n w}
    (advanced : AnyStep program before after) : after.failed = before.failed := by
  cases advanced with
  | selected selection first =>
    exact (step_preserves_failure program _ _ first).trans
      (selection_preserves_failure selection)

theorem any_semantic_availability_only_shrinks (program : Program o n w)
    {before after : State o n w} (advanced : AnyStep program before after) (key : Key o n) :
    key ∈ after.available → key ∈ before.available := by
  cases advanced with
  | selected selection first =>
    intro present
    have old := semantic_availability_only_shrinks program _ _ first key present
    simpa only [selection_preserves_available selection] using old

inductive AnyTrace (program : Program o n w) : State o n w → Nat → State o n w → Prop
  | done (state) : AnyTrace program state 0 state
  | next {before middle after count} (first : AnyStep program before middle)
      (rest : AnyTrace program middle count after) : AnyTrace program before (count + 1) after

/-- All represented productive schedules have the same finite upper bound,
    although their observations/results can differ for an order-sensitive Program. -/
theorem any_trace_length_bound (program : Program o n w) {before after : State o n w}
    {count : Nat} (trace : AnyTrace program before count after) :
    count + potential after ≤ potential before := by
  induction trace with
  | done state => simp
  | next first rest ih =>
    have smaller := any_step_decreases program first
    omega

theorem any_initial_trace_bound (program : Program o n w) (seeds dormant : List (Key o n))
    {after : State o n w} {count : Nat}
    (trace : AnyTrace program (initial program seeds dormant) count after) :
    count + potential after ≤ program.keys.length * (w + 3) := by
  exact Nat.le_trans (any_trace_length_bound program trace) (initial_bound program seeds dormant)

theorem any_sequence_segment (program : Program o n w) (states : Nat → State o n w)
    (productive : ∀ index, AnyStep program (states index) (states (index + 1)))
    (start count : Nat) : AnyTrace program (states start) count (states (start + count)) := by
  induction count generalizing start with
  | zero => simpa using AnyTrace.done (program := program) (states start)
  | succ count ih =>
    have tail := ih (start + 1)
    have segment := AnyTrace.next (productive start) tail
    simpa only [Nat.add_assoc, Nat.add_left_comm, Nat.add_comm] using segment

theorem no_infinite_productive_schedule (program : Program o n w)
    (states : Nat → State o n w) :
    ¬ (∀ index, AnyStep program (states index) (states (index + 1))) := by
  intro productive
  have segment := any_sequence_segment program states productive 0 (potential (states 0) + 1)
  have bound := any_trace_length_bound program segment
  omega

theorem any_trace_preserves_failure (program : Program o n w) {before after : State o n w}
    {count : Nat} (trace : AnyTrace program before count after) : after.failed = before.failed := by
  induction trace with
  | done state => rfl
  | next first rest ih => exact ih.trans (any_step_preserves_failure program first)

/-- The old head step is one allowed schedule, so selection introduces no deadlock. -/
theorem head_step_is_any (program : Program o n w) {before after : State o n w}
    (advanced : step program before = some after) : AnyStep program before after := by
  cases queued : before.bursts with
  | nil => simp [step, queued] at advanced
  | cons burst rest =>
    have selected : selectState before burst rest burst.todo = before := by
      cases before
      simp_all [selectState]
    apply AnyStep.selected (Selection.selected burst rest burst.todo
      (List.Perm.of_eq queued) (List.Perm.refl _))
    simpa only [selected] using advanced

theorem head_trace_is_any (program : Program o n w) {before after : State o n w}
    {count : Nat} (trace : Trace program before count after) : AnyTrace program before count after := by
  induction trace with
  | done state => exact .done state
  | next first rest ih => exact .next (head_step_is_any program first) ih

/-- A maximal represented schedule is quiescent, provided sends were secured.
    Infinite external stalls/yields are not productive `AnyStep`s. -/
theorem any_maximal_trace_quiescent (program : Program o n w)
    {before after : State o n w} {count : Nat}
    (trace : AnyTrace program before count after) (secured : before.failed = false)
    (maximal : ∀ next, ¬ AnyStep program after next) : Quiescent after := by
  have safe := (any_trace_preserves_failure program trace).trans secured
  have terminal : step program after = none := by
    cases advanced : step program after with
    | none => rfl
    | some next => exact False.elim (maximal next (head_step_is_any program advanced))
  constructor
  · exact safe
  · rcases (step_none_iff program after).mp terminal with failed | empty
    · simp [safe] at failed
    · exact empty

/-- A quantum processes only local work. Even if its last item emptied the queue,
    pending commits and child ownership stay until the explicit boundary step. -/
def quantum (program : Program o n w) : Nat → State o n w → State o n w
  | 0, state => state
  | fuel + 1, state => match state.bursts with
    | [] => state
    | burst :: rest => match burst.todo with
      | [] => state
      | work :: tail => quantum program fuel (process program state burst work tail rest)

theorem quantum_does_not_commit (program : Program o n w) (fuel : Nat) (state : State o n w) :
    (quantum program fuel state).committed = state.committed := by
  induction fuel generalizing state with
  | zero => rfl
  | succ fuel ih =>
    cases hq : state.bursts with
    | nil => simp [quantum, hq]
    | cons burst rest =>
      cases ht : burst.todo with
      | nil => simp [quantum, hq, ht]
      | cons work tail => simpa [quantum, hq, ht, process] using ih (process program state burst work tail rest)

theorem quantum_keeps_reservations (program : Program o n w) (fuel : Nat) (state : State o n w) :
    (quantum program fuel state).bursts.length = state.bursts.length := by
  induction fuel generalizing state with
  | zero => rfl
  | succ fuel ih =>
    cases hq : state.bursts with
    | nil => simp [quantum, hq]
    | cons burst rest =>
      cases ht : burst.todo with
      | nil => simp [quantum, hq, ht]
      | cons work tail =>
        simpa [quantum, hq, ht, process] using ih (process program state burst work tail rest)

theorem dormant_is_not_queued (available committed dormant : List (Key o n)) :
    Quiescent (⟨available, [], committed, dormant, false⟩ : State o n w) := ⟨rfl, rfl⟩

/-- No formula identity can wake an equal AP membership: only the semantic key
    is admitted. Labels supplied by an observer have no role in this function. -/
def observeEqualFact (available : List (Key o n)) (key : Key o n) (_observerLabel : Nat) :=
  admitMany available [key]

theorem changed_observer_no_new_work (available : List (Key o n)) (key : Key o n)
    (label₁ label₂ : Nat) :
    observeEqualFact available key label₁ = observeEqualFact available key label₂ := rfl

theorem equal_fact_after_admission_no_new_burst (available : List (Key o n)) (key : Key o n)
    (label : Nat) : observeEqualFact (removeAll key available) key label = (removeAll key available, []) := by
  simp [observeEqualFact, admitMany]

/- The existing reservation algebra is reused. The new bridge identifies the
   active burst's pending batch as work owned by that still-counted parent. -/
abbrev Accounting := ApSpec.BottomUpStaticTransport.Accounting
abbrev Accounted := ApSpec.BottomUpStaticTransport.Accounted

instance (state : Accounting) : Decidable (Accounted state) := inferInstanceAs (Decidable (state.reserved = state.queued + state.active))

def ownerAccounting (state : State o n w) : Accounting :=
  match state.bursts with
  | [] => ⟨0, 0, 0⟩
  | _ :: rest => ⟨rest.length, 1, rest.length + 1⟩

theorem owner_accounted (state : State o n w) : Accounted (ownerAccounting state) := by
  cases hq : state.bursts <;> simp [Accounted, ApSpec.BottomUpStaticTransport.Accounted, ownerAccounting, hq]

theorem pending_parent_is_counted (state : State o n w) (burst : Burst o n w)
    (rest : List (Burst o n w)) (parent : state.bursts = burst :: rest) :
    0 < (ownerAccounting state).reserved := by simp [ownerAccounting, parent]

theorem secured_children_before_release (state : Accounting) (children : Nat)
    (accounted : Accounted state) (parent : 0 < state.active) :
    Accounted (ApSpec.BottomUpStaticTransport.finishWork
      (ApSpec.BottomUpStaticTransport.enqueue state children)) ∧
      (0 < children → 0 < (ApSpec.BottomUpStaticTransport.finishWork
        (ApSpec.BottomUpStaticTransport.enqueue state children)).reserved) :=
  ApSpec.BottomUpStaticTransport.enqueue_before_release state children accounted parent

theorem commit_child_reservations (program : Program o n w) (state : State o n w)
    (burst : Burst o n w) (rest : List (Burst o n w)) :
    (commit program state burst rest).bursts.length = rest.length + burst.pending.length := by
  simp [commit]

theorem duplicate_batch_creates_no_child (program : Program o n w) (state : State o n w)
    (burst : Burst o n w) (rest : List (Burst o n w)) (empty : burst.pending = []) :
    (commit program state burst rest).bursts = rest := by simp [commit, empty]

theorem pending_commit_accounting (program : Program o n w) (state : State o n w)
    (burst : Burst o n w) (rest : List (Burst o n w)) :
    let before : Accounting := ⟨rest.length, 1, rest.length + 1⟩
    let after := ApSpec.BottomUpStaticTransport.finishWork
      (ApSpec.BottomUpStaticTransport.enqueue before burst.pending.length)
    Accounted after ∧ after.reserved = (commit program state burst rest).bursts.length := by
  simp [Accounted, ApSpec.BottomUpStaticTransport.Accounted, ApSpec.BottomUpStaticTransport.finishWork,
    ApSpec.BottomUpStaticTransport.enqueue, commit]

def rejectSend (state : State o n w) : State o n w := { state with failed := true }

theorem rejected_send_not_complete (state : State o n w) : ¬ Quiescent (rejectSend state) := by
  simp [Quiescent, rejectSend]

/-- Outside the selected finite-key contract, a history revision has no fixed point. -/
def historyStep (revision : Nat) : Nat := revision + 1

theorem history_identity_not_progress (revision : Nat) : historyStep revision ≠ revision := by
  exact Nat.ne_of_gt (Nat.lt_succ_self revision)

namespace Controls

def key (kind : Kind) (slot : Fin 6) : Key 1 6 := ⟨kind, 0, slot⟩
def apKey := key .ap 0
def requestKey := key .request 1
def summaryKey := key .summary 2
def supplyKey := key .supply 3
def deliveryKey := key .delivery 4
def interestKey := key .interest 5

/-- The positive marked-copy loop has one semantic AP membership and two
    local repeat keys; the self edge does not manufacture another burst. -/
def copyLoop : Program 1 6 2 :=
  ⟨fun _ => [0], fun _ _ work => if work = 0 then [1] else [1], fun _ _ _ => [apKey]⟩

def copyStart := initial copyLoop [apKey]
def copyEnd := run copyLoop 3 copyStart

def chain : Program 1 6 2 :=
  ⟨fun _ => [0], fun _ _ work => if work = 0 then [1] else [1],
    fun _ _ _ => [requestKey, summaryKey, supplyKey, deliveryKey, interestKey, apKey]⟩

def chainStart := initial chain [apKey] [interestKey]
def chainEnd := run chain (chain.keys.length * 5) chainStart

def afterOne := quantum chain 1 chainStart
def afterTwo := quantum chain 2 chainStart

def abandoned := rejectSend afterTwo

def crossRoot : Key 2 2 := ⟨.ap, 0, 0⟩
def crossChild : Key 2 2 := ⟨.delivery, 1, 1⟩
def crossOwner : Program 2 2 1 :=
  ⟨fun _ => [0], fun _ _ _ => [0],
    fun _ owner _ => if owner = 0 then [crossChild] else [crossRoot]⟩
def crossEnd := run crossOwner (crossOwner.keys.length * 4) (initial crossOwner [crossRoot])

def orderProgram : Program 2 2 2 :=
  ⟨fun _ => [0, 1], fun _ _ _ => [], fun _ _ _ => []⟩
def orderStart := initial orderProgram [crossRoot, crossChild]
def orderChosen := selectState orderStart (startBurst orderProgram crossChild)
  [startBurst orderProgram crossRoot] [1, 0]
def orderAfter := process orderProgram orderChosen
  { startBurst orderProgram crossChild with todo := [1, 0] }
  1 [0] [startBurst orderProgram crossRoot]

theorem arbitrary_selection_valid : Selection orderStart orderChosen := by
  apply Selection.selected
  · change [startBurst orderProgram crossRoot, startBurst orderProgram crossChild].Perm
      [startBurst orderProgram crossChild, startBurst orderProgram crossRoot]
    exact .swap _ _ []
  · change ([0, 1] : List (Fin 2)).Perm [1, 0]
    exact .swap _ _ []

theorem arbitrary_step_valid : AnyStep orderProgram orderStart orderAfter :=
  .selected arbitrary_selection_valid (by rfl)

theorem arbitrary_trace_valid : AnyTrace orderProgram orderStart 1 orderAfter :=
  .next arbitrary_step_valid (.done orderAfter)

def controls : List (String × Bool) := [
  ("positive marked-copy loop quiesces in two local steps plus commit", decide (Quiescent copyEnd)),
  ("copy loop commits no new semantic AP membership", decide (copyEnd.committed = [apKey])),
  ("copy loop consumes only its one semantic membership", copyEnd.available.length + 1 == copyLoop.keys.length),
  ("different observer labels cannot change admission", observeEqualFact ([] : List (Key 1 6)) apKey 1 == observeEqualFact [] apKey 100000),
  ("requests summaries supplies and deliveries close despite cycles", decide (Quiescent chainEnd)),
  ("all six distinct semantic memberships commit", chainEnd.committed.length == 6),
  ("duplicate seed creates only one burst", (initial copyLoop [apKey, apKey]).bursts.length == 1),
  ("first quantum retains pending updates", !afterOne.bursts.isEmpty && afterOne.committed.length == 1),
  ("quantum that empties local queue still has not committed", afterTwo.committed.length == 1 && !afterTwo.bursts.isEmpty),
  ("pending parent remains reserved", decide (0 < (ownerAccounting afterTwo).reserved)),
  ("real queue-empty commit secures five child bursts", match step chain afterTwo with | some next => next.bursts.length == 5 | none => false),
  ("dormant unanswered interest does not prevent completion", decide (Quiescent chainEnd) && !chainEnd.dormant.isEmpty),
  ("rejected send cannot be accepted complete", !decide (Quiescent abandoned)),
  ("history revision is a counterexample to semantic identity", historyStep 10 != 10),
  ("releasing parent before reserving child violates accounting", !decide (Accounted (⟨1,0,0⟩ : Accounting))),
  ("two owner callback cycle closes without losing either membership", decide (Quiescent crossEnd) && crossEnd.committed.length == 2),
  ("arbitrary selection chooses the second queued owner", ((orderChosen.bursts.head?).map fun burst => burst.cause.owner.val) == some 1),
  ("arbitrary local selection chooses the non-head pending key", ((orderChosen.bursts.head?).bind fun burst => burst.todo.head?) == some 1),
  ("owner and local permutations preserve semantic potential", potential orderChosen == potential orderStart),
  ("the non-head owner and work step strictly consumes potential", decide (potential orderAfter < potential orderStart))
]

theorem controls_pass : controls.all Prod.snd = true := by decide

end Controls
end ApSpec.Run1SemanticProgress
