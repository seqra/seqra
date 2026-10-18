/-
  Finite writer-consumer protocol. A node is an opaque local input, source,
  static coordinate, or reached reader. An edge is a registered, group-preserving
  dependency. Edge registration is an adapter obligation. An edge alone is not
  taint evidence. There are no implicit static-call relay nodes.

  The executable certificate type records proof witnesses only. The runtime
  representation needs node-local group sets, not stored paths or edge histories.
-/
import Std

namespace ApSpec.Run1StaticGroupProtocol
set_option autoImplicit false

abbrev Node := Nat
abbrev Group := Nat

structure Atom where
  node : Node
  group : Group
  deriving DecidableEq, Repr

structure Edge where
  producer : Node
  consumer : Node
  deriving DecidableEq, Repr

structure Network where
  keys : List Atom
  /-- Validated rule/model sources only. A provider callback or an abstract
      canonical entry is not a new seed. That adapter must justify this list. -/
  seeds : List Atom
  edges : List Edge
  deriving DecidableEq, Repr

structure State where
  wanted : List Atom := []
  answered : List Atom := []
  deriving DecidableEq, Repr

inductive Action where
  | backward (edge : Edge) (group : Group)
  | seed (atom : Atom)
  | forward (edge : Edge) (group : Group)
  deriving DecidableEq, Repr

def target : Action → Atom
  | .backward edge group => ⟨edge.producer, group⟩
  | .seed atom => atom
  | .forward edge group => ⟨edge.consumer, group⟩

/-- A request never occurs in the source constructors. -/
inductive Derivation (network : Network) : Atom → Type where
  | source (atom : Atom) (registered : atom ∈ network.seeds)
      (within : atom ∈ network.keys) :
      Derivation network atom
  | hop (edge : Edge) (group : Group) (registered : edge ∈ network.edges)
      (within : Atom.mk edge.consumer group ∈ network.keys)
      (input : Derivation network ⟨edge.producer, group⟩) :
      Derivation network ⟨edge.consumer, group⟩

/-- This is executable evidence, rather than an assumed coverage proposition. -/
def Safe (network : Network) (state : State) : Type :=
  (atom : Atom) → atom ∈ state.answered → Derivation network atom

def emptySafe (network : Network) : Safe network {} := by
  intro atom impossible
  exact False.elim (by simp at impossible)

def enabled (network : Network) (state : State) : Action → Bool
  | .backward edge group =>
      network.edges.contains edge &&
      network.keys.contains ⟨edge.producer, group⟩ &&
      state.wanted.contains ⟨edge.consumer, group⟩ &&
      !state.wanted.contains ⟨edge.producer, group⟩
  | .seed atom =>
      network.keys.contains atom && network.seeds.contains atom &&
      !state.answered.contains atom
  | .forward edge group =>
      network.edges.contains edge &&
      network.keys.contains ⟨edge.consumer, group⟩ &&
      state.answered.contains ⟨edge.producer, group⟩ &&
      !state.answered.contains ⟨edge.consumer, group⟩

def applyAction (state : State) : Action → State
  | .backward edge group =>
      { state with wanted := ⟨edge.producer, group⟩ :: state.wanted }
  | .seed atom => { state with answered := atom :: state.answered }
  | .forward edge group =>
      { state with answered := ⟨edge.consumer, group⟩ :: state.answered }

/-- Invalid, duplicate and unsupported attempts produce no state change. -/
def step (network : Network) (state : State) (action : Action) : State :=
  if enabled network state action then applyAction state action else state

def applySafe (network : Network) (state : State) (safe : Safe network state)
    (action : Action) (ready : enabled network state action = true) :
    Safe network (applyAction state action) := by
  cases action with
  | backward edge group => exact safe
  | seed seed =>
      have facts : seed ∈ network.keys ∧ seed ∈ network.seeds ∧ seed ∉ state.answered := by
        simpa [enabled, Bool.and_eq_true, and_assoc] using ready
      have registered : seed ∈ network.seeds := facts.2.1
      intro atom present
      have hm : atom = seed ∨ atom ∈ state.answered := by
        simpa [applyAction] using present
      if eq : atom = seed then
        exact eq ▸ Derivation.source seed registered facts.1
      else exact safe atom (hm.resolve_left eq)
  | forward edge group =>
      have facts : edge ∈ network.edges ∧
          Atom.mk edge.consumer group ∈ network.keys ∧
          Atom.mk edge.producer group ∈ state.answered ∧
          Atom.mk edge.consumer group ∉ state.answered := by
        simpa [enabled, Bool.and_eq_true, and_assoc] using ready
      intro atom present
      have hm : atom = ⟨edge.consumer, group⟩ ∨ atom ∈ state.answered := by
        simpa [applyAction] using present
      if eq : atom = ⟨edge.consumer, group⟩ then
        exact eq ▸ Derivation.hop edge group facts.1 facts.2.1 (safe _ facts.2.2.1)
      else exact safe atom (hm.resolve_left eq)

def stepSafe (network : Network) (state : State) (safe : Safe network state)
    (action : Action) : Safe network (step network state action) := by
  unfold step
  split
  next ready => exact applySafe network state safe action ready
  next _ => exact safe

/-- The source root can be extracted from each answer certificate. -/
def sourceRoot {network : Network} {atom : Atom} (proof : Derivation network atom) :
    { source : Atom // source ∈ network.seeds ∧ source.group = atom.group } :=
  match proof with
  | .source seed registered _ => ⟨seed, registered, rfl⟩
  | .hop _ _ _ _ input =>
      let root := @sourceRoot network _ input
      ⟨root.val, root.property.1, root.property.2⟩

theorem no_source_no_answer {network : Network} (empty : network.seeds = [])
    {state : State} (safe : Safe network state) : state.answered = [] := by
  cases equation : state.answered with
  | nil => rfl
  | cons atom tail =>
      have present : atom ∈ state.answered := by simp [equation]
      have root := sourceRoot (safe atom present)
      have impossible := root.property.1
      have absent : root.val ∉ network.seeds := by simp [empty]
      exact False.elim (absent impossible)

def missing (keys seen : List Atom) : Nat :=
  (keys.filter (fun atom => !seen.contains atom)).length

theorem missing_cons_le (keys seen : List Atom) (atom : Atom) :
    missing keys (atom :: seen) ≤ missing keys seen := by
  induction keys with
  | nil => simp [missing]
  | cons head tail ih =>
      by_cases same : head = atom <;> by_cases present : head ∈ seen <;>
        simp_all [missing] <;> omega

theorem missing_cons_lt (keys seen : List Atom) (atom : Atom)
    (inside : atom ∈ keys) (fresh : atom ∉ seen) :
    missing keys (atom :: seen) < missing keys seen := by
  induction keys with
  | nil => simp at inside
  | cons head tail ih =>
      by_cases same : head = atom
      · subst head
        have le := missing_cons_le tail seen atom
        simp_all [missing]
        omega
      · have it : atom ∈ tail := (List.mem_cons.mp inside).resolve_left
          (fun eq => same eq.symm)
        have lt := ih it
        by_cases present : head ∈ seen <;> simp_all [missing] <;> omega

def potential (network : Network) (state : State) : Nat :=
  missing network.keys state.wanted + missing network.keys state.answered

theorem action_decreases (network : Network) (state : State) (action : Action)
    (ready : enabled network state action = true) :
    potential network (applyAction state action) < potential network state := by
  cases action with
  | backward edge group =>
      have facts : edge ∈ network.edges ∧
          Atom.mk edge.producer group ∈ network.keys ∧
          Atom.mk edge.consumer group ∈ state.wanted ∧
          Atom.mk edge.producer group ∉ state.wanted := by
        simpa [enabled, Bool.and_eq_true, and_assoc] using ready
      exact Nat.add_lt_add_right
        (missing_cons_lt _ _ _ facts.2.1 facts.2.2.2) _
  | seed atom =>
      have facts : atom ∈ network.keys ∧ atom ∈ network.seeds ∧
          atom ∉ state.answered := by
        simpa [enabled, Bool.and_eq_true, and_assoc] using ready
      exact Nat.add_lt_add_left (missing_cons_lt _ _ _ facts.1 facts.2.2) _
  | forward edge group =>
      have facts : edge ∈ network.edges ∧
          Atom.mk edge.consumer group ∈ network.keys ∧
          Atom.mk edge.producer group ∈ state.answered ∧
          Atom.mk edge.consumer group ∉ state.answered := by
        simpa [enabled, Bool.and_eq_true, and_assoc] using ready
      exact Nat.add_lt_add_left
        (missing_cons_lt _ _ _ facts.2.1 facts.2.2.2) _

/-- Duplicate group tags in keys cause duplicate candidates, but cannot cause a
    duplicate state transition. A bit-set implementation can avoid that scan. -/
def actions (network : Network) : List Action :=
  network.keys.map Action.seed ++ network.keys.flatMap (fun atom =>
    network.edges.flatMap (fun edge =>
      [Action.backward edge atom.group, Action.forward edge atom.group]))

def next (network : Network) (state : State) : Option Action :=
  (actions network).find? (enabled network state)

def close (network : Network) (state : State) : State :=
  match _chosen : next network state with
  | none => state
  | some action => close network (applyAction state action)
termination_by potential network state
decreasing_by
  exact action_decreases network state action (List.find?_some _chosen)

/-- This constructive function lifts all answer certificates through saturation. -/
def closeSafe (network : Network) (state : State) (safe : Safe network state) :
    Safe network (close network state) :=
  match chosen : next network state with
  | none => by
      rw [close, chosen]
      exact safe
  | some action => by
      rw [close, chosen]
      exact closeSafe network (applyAction state action)
        (applySafe network state safe action (List.find?_some chosen))
termination_by potential network state
decreasing_by
  exact action_decreases network state action (List.find?_some chosen)

theorem close_stable (network : Network) (state : State) :
    next network (close network state) = none :=
  match chosen : next network state with
  | none => by
      rw [close, chosen]
      exact chosen
  | some action => by
      rw [close, chosen]
      exact close_stable network (applyAction state action)
termination_by potential network state
decreasing_by
  exact action_decreases network state action (List.find?_some chosen)

theorem close_idempotent (network : Network) (state : State) :
    close network (close network state) = close network state := by
  rw [close, close_stable]

theorem request_cycle_cannot_bootstrap (network : Network) (state : State)
    (empty : network.seeds = []) (safe : Safe network state) :
    (close network state).answered = [] :=
  no_source_no_answer empty (closeSafe network state safe)

theorem seed_candidate (network : Network) (atom : Atom)
    (within : atom ∈ network.keys) : Action.seed atom ∈ actions network := by
  simp only [actions, List.mem_append, List.mem_map]
  exact Or.inl ⟨atom, within, rfl⟩

theorem forward_candidate (network : Network) (edge : Edge) (group : Group)
    (within : Atom.mk edge.consumer group ∈ network.keys)
    (registered : edge ∈ network.edges) :
    Action.forward edge group ∈ actions network := by
  simp only [actions, List.mem_append, List.mem_flatMap]
  exact Or.inr ⟨⟨edge.consumer, group⟩, within, edge, registered, by simp⟩

theorem backward_candidate (network : Network) (edge : Edge) (group : Group)
    (within : Atom.mk edge.producer group ∈ network.keys)
    (registered : edge ∈ network.edges) :
    Action.backward edge group ∈ actions network := by
  simp only [actions, List.mem_append, List.mem_flatMap]
  exact Or.inr ⟨⟨edge.producer, group⟩, within, edge, registered, by simp⟩

theorem stable_no_enabled (network : Network) (state : State)
    (stable : next network state = none) (action : Action)
    (candidate : action ∈ actions network) : enabled network state action = false := by
  have none := List.find?_eq_none.mp stable action candidate
  cases equation : enabled network state action <;> simp_all

/-- The finite closed state contains every rooted derivation in its key scope. -/
theorem derivation_answered (network : Network) (state : State)
    (stable : next network state = none) {atom : Atom}
    (proof : Derivation network atom) : atom ∈ state.answered := by
  induction proof with
  | source atom registered within =>
      by_cases old : atom ∈ state.answered
      · exact old
      · have idle := stable_no_enabled network state stable (.seed atom)
          (seed_candidate network atom within)
        simp [enabled, registered, within, old] at idle
  | hop edge group registered within input ih =>
      by_cases old : Atom.mk edge.consumer group ∈ state.answered
      · exact old
      · have idle := stable_no_enabled network state stable (.forward edge group)
          (forward_candidate network edge group within registered)
        simp [enabled, registered, within, old, ih] at idle

def closeCertificate (network : Network) (state : State) (safe : Safe network state)
    (atom : Atom) (present : atom ∈ (close network state).answered) :
    Derivation network atom := closeSafe network state safe atom present

/-- An executable answer query returns its derivation, or returns no answer. -/
def lookupCertificate (network : Network) (state : State) (safe : Safe network state)
    (atom : Atom) : Option (Derivation network atom) :=
  if present : atom ∈ (close network state).answered then
    some (closeCertificate network state safe atom present)
  else none

theorem close_complete (network : Network) (state : State) {atom : Atom}
    (proof : Derivation network atom) : atom ∈ (close network state).answered :=
  derivation_answered network (close network state) (close_stable network state) proof

/-- Existing frontiers can change scheduling, but not the final proven group set. -/
theorem closed_answer_sets_equal (network : Network) (left right : State)
    (leftSafe : Safe network left) (rightSafe : Safe network right) (atom : Atom) :
    atom ∈ (close network left).answered ↔ atom ∈ (close network right).answered := by
  constructor
  · intro present
    exact close_complete network right (closeCertificate network left leftSafe atom present)
  · intro present
    exact close_complete network left (closeCertificate network right rightSafe atom present)

structure Extension (before after : Network) : Prop where
  keys : ∀ atom, atom ∈ before.keys → atom ∈ after.keys
  seeds : ∀ atom, atom ∈ before.seeds → atom ∈ after.seeds
  edges : ∀ edge, edge ∈ before.edges → edge ∈ after.edges

/-- A late edge or source does not invalidate an earlier rooted answer. -/
def liftDerivation {before after : Network} (growth : Extension before after)
    {atom : Atom} (proof : Derivation before atom) : Derivation after atom :=
  match proof with
  | .source atom registered within =>
      .source atom (growth.seeds atom registered) (growth.keys atom within)
  | .hop edge group registered within input =>
      .hop edge group (growth.edges edge registered) (growth.keys _ within)
        (liftDerivation growth input)

def growSafe {before after : Network} (growth : Extension before after)
    (state : State) (safe : Safe before state) : Safe after state :=
  fun atom present => liftDerivation growth (safe atom present)

theorem late_registration_same_answers {before after : Network}
    (growth : Extension before after) (old : State) (safe : Safe before old)
    (atom : Atom) :
    atom ∈ (close after (close before old)).answered ↔
      atom ∈ (close after old).answered :=
  closed_answer_sets_equal after (close before old) old
    (growSafe growth _ (closeSafe before old safe)) (growSafe growth old safe) atom

def request (state : State) (atoms : List Atom) : State :=
  { state with wanted := (state.wanted ++ atoms).eraseDups }

def requestSafe (network : Network) (state : State) (safe : Safe network state)
    (atoms : List Atom) : Safe network (request state atoms) := safe

def pending (state : State) : List Atom :=
  state.wanted.filter (fun atom => !state.answered.contains atom)

theorem pending_iff (state : State) (atom : Atom) : atom ∈ pending state ↔
    atom ∈ state.wanted ∧ atom ∉ state.answered := by simp [pending]

theorem merged_request_membership (state : State) (atoms : List Atom) (atom : Atom) :
    atom ∈ (request state atoms).wanted ↔ atom ∈ state.wanted ∨ atom ∈ atoms := by
  simp [request]

theorem requests_commute (state : State) (left right : List Atom) (atom : Atom) :
    atom ∈ (request (request state left) right).wanted ↔
      atom ∈ (request (request state right) left).wanted := by
  simp [request, or_left_comm, or_comm]

theorem requests_idempotent (state : State) (batch : List Atom) (atom : Atom) :
    atom ∈ (request (request state batch) batch).wanted ↔
      atom ∈ (request state batch).wanted := by
  simp [request]

theorem request_orders_same_answer_set (network : Network) (state : State)
    (safe : Safe network state) (requests : List Atom) (atom : Atom) :
    atom ∈ (close network (request (close network state) requests)).answered ↔
      atom ∈ (close network (request state requests)).answered :=
  closed_answer_sets_equal network _ _
    (requestSafe network _ (closeSafe network state safe) requests)
    (requestSafe network state safe requests) atom

/-- Provider replies require already rooted evidence. The mere act of answering
    an ordinary input request cannot add a new source root. -/
def acceptProviderReply {network : Network} (state : State) (atom : Atom)
    (_proof : Derivation network atom) : State :=
  if state.answered.contains atom then state
  else { state with answered := atom :: state.answered }

def providerReplySafe (network : Network) (state : State) (safe : Safe network state)
    (atom : Atom) (proof : Derivation network atom) :
    Safe network (acceptProviderReply state atom proof) := by
  unfold acceptProviderReply
  split
  next _ => exact safe
  next _ =>
    intro candidate present
    if eq : candidate = atom then exact eq ▸ proof
    else
      have hm : candidate = atom ∨ candidate ∈ state.answered := by simpa using present
      exact safe candidate (hm.resolve_left eq)

/-- Generic FLOW, a rule interest and a canonical hypothesis carry no rooted
    group evidence. They cannot call acceptProviderReply without a certificate. -/
def observeUnknownFlow (state : State) (_node : Node) : State := state

def observeCanonicalHypothesis (state : State) (_atom : Atom) : State := state

theorem no_answer_from_unknown_or_hypothesis (state : State) (node : Node) (atom : Atom) :
    (observeCanonicalHypothesis (observeUnknownFlow state node) atom).answered =
      state.answered := rfl

theorem provider_reply_not_twice {network : Network} (state : State) (atom : Atom)
    (proof : Derivation network atom) :
    acceptProviderReply (acceptProviderReply state atom proof) atom proof =
      acceptProviderReply state atom proof := by
  by_cases old : atom ∈ state.answered <;> simp [acceptProviderReply, old]

theorem stable_backwards_need (network : Network) (state : State)
    (stable : next network state = none) (edge : Edge) (group : Group)
    (registered : edge ∈ network.edges)
    (within : Atom.mk edge.producer group ∈ network.keys)
    (wanted : Atom.mk edge.consumer group ∈ state.wanted) :
    Atom.mk edge.producer group ∈ state.wanted := by
  by_cases old : Atom.mk edge.producer group ∈ state.wanted
  · exact old
  · have idle := stable_no_enabled network state stable (.backward edge group)
      (backward_candidate network edge group within registered)
    simp [enabled, registered, within, wanted, old] at idle

theorem step_does_not_repeat_answer (network : Network) (state : State)
    (action : Action) (old : target action ∈ state.answered)
    (notBackward : ∀ edge group, action ≠ .backward edge group) :
    step network state action = state := by
  cases action with
  | backward edge group => exact False.elim (notBackward edge group rfl)
  | seed atom =>
      simp only [target] at old
      simp [step, enabled, old]
  | forward edge group =>
      simp only [target] at old
      simp [step, enabled, old]

def stepCount (network : Network) (state : State) : Nat :=
  match chosen : next network state with
  | none => 0
  | some action => 1 + stepCount network (applyAction state action)
termination_by potential network state
decreasing_by
  exact action_decreases network state action (List.find?_some chosen)

theorem step_count_bounded (network : Network) (state : State) :
    stepCount network state ≤ potential network state :=
  match chosen : next network state with
  | none => by rw [stepCount, chosen]; exact Nat.zero_le _
  | some action => by
      rw [stepCount, chosen]
      change 1 + stepCount network (applyAction state action) ≤ potential network state
      have decrease := action_decreases network state action (List.find?_some chosen)
      have recurse := step_count_bounded network (applyAction state action)
      omega
termination_by potential network state
decreasing_by
  exact action_decreases network state action (List.find?_some chosen)

theorem empty_state_step_bound (network : Network) :
    stepCount network {} ≤ 2 * network.keys.length := by
  have bound := step_count_bounded network {}
  have filtered : network.keys.filter (fun _ => true) = network.keys := by
    induction network.keys with
    | nil => rfl
    | cons head tail ih => simp [ih]
  simpa [potential, missing, Nat.two_mul, filtered] using bound

theorem apply_preserves_nodup (network : Network) (state : State)
    (wanted : state.wanted.Nodup) (answered : state.answered.Nodup)
    (action : Action) (ready : enabled network state action = true) :
    (applyAction state action).wanted.Nodup ∧
      (applyAction state action).answered.Nodup := by
  cases action with
  | backward edge group =>
      have facts : edge ∈ network.edges ∧
          Atom.mk edge.producer group ∈ network.keys ∧
          Atom.mk edge.consumer group ∈ state.wanted ∧
          Atom.mk edge.producer group ∉ state.wanted := by
        simpa [enabled, Bool.and_eq_true, and_assoc] using ready
      exact ⟨List.nodup_cons.mpr ⟨facts.2.2.2, wanted⟩, answered⟩
  | seed atom =>
      have facts : atom ∈ network.keys ∧ atom ∈ network.seeds ∧
          atom ∉ state.answered := by
        simpa [enabled, Bool.and_eq_true, and_assoc] using ready
      exact ⟨wanted, List.nodup_cons.mpr ⟨facts.2.2, answered⟩⟩
  | forward edge group =>
      have facts : edge ∈ network.edges ∧
          Atom.mk edge.consumer group ∈ network.keys ∧
          Atom.mk edge.producer group ∈ state.answered ∧
          Atom.mk edge.consumer group ∉ state.answered := by
        simpa [enabled, Bool.and_eq_true, and_assoc] using ready
      exact ⟨wanted, List.nodup_cons.mpr ⟨facts.2.2.2, answered⟩⟩

theorem close_preserves_nodup (network : Network) (state : State)
    (wanted : state.wanted.Nodup) (answered : state.answered.Nodup) :
    (close network state).wanted.Nodup ∧ (close network state).answered.Nodup :=
  match chosen : next network state with
  | none => by rw [close, chosen]; exact ⟨wanted, answered⟩
  | some action => by
      rw [close, chosen]
      have fresh := apply_preserves_nodup network state wanted answered action
        (List.find?_some chosen)
      exact close_preserves_nodup network (applyAction state action) fresh.1 fresh.2
termination_by potential network state
decreasing_by
  exact action_decreases network state action (List.find?_some chosen)

namespace Controls

def keys : List Atom :=
  [0, 1, 2, 3].flatMap (fun node => [Atom.mk node 7, Atom.mk node 9])

def writer : Edge := ⟨0, 1⟩
def reader : Edge := ⟨1, 2⟩

def network : Network := ⟨keys, [⟨0, 7⟩], [writer, reader]⟩
def emptyNetwork : Network := ⟨keys, [], [writer, reader]⟩
def disconnected : Network := ⟨keys, [⟨0, 7⟩], []⟩
def providerNeed : State := request {} [⟨2, 9⟩]
def cycle : Network := ⟨keys, [], [⟨1, 2⟩, ⟨2, 1⟩]⟩
def seededCycle : Network := { cycle with seeds := [⟨1, 7⟩] }
def copyNetwork : Network := ⟨keys, [⟨0, 7⟩], [writer, ⟨1, 3⟩, ⟨3, 2⟩]⟩
def bulkNetwork : Network := { network with seeds := [⟨0, 7⟩, ⟨0, 9⟩] }

def seedCertificate : Derivation network ⟨0, 7⟩ := .source _ (by decide) (by decide)

def controls : List (String × Bool) :=
  [ ("proven source reaches reader without request", (close network {}).answered.contains ⟨2, 7⟩)
  , ("request does not invent group", !(close network providerNeed).answered.contains ⟨2, 9⟩)
  , ("need reaches ordinary input provider", (close network providerNeed).wanted.contains ⟨0, 9⟩)
  , ("unknown FLOW is not evidence", (close emptyNetwork (observeUnknownFlow {} 0)).answered.isEmpty)
  , ("canonical input is not evidence", (close emptyNetwork (observeCanonicalHypothesis {} ⟨0, 7⟩)).answered.isEmpty)
  , ("request plus FLOW is not evidence", (close emptyNetwork (observeUnknownFlow providerNeed 0)).answered.isEmpty)
  , ("unseeded cycle stays empty", (close cycle (request {} [⟨2, 7⟩])).answered.isEmpty)
  , ("seeded cycle reaches both nodes", ((close seededCycle {}).answered.length == 2))
  , ("seed cannot cross absent writer", !(close disconnected {}).answered.contains ⟨2, 7⟩)
  , ("static copy has rooted result", (close copyNetwork {}).answered.contains ⟨2, 7⟩)
  , ("late writer consumes old source", (close network (close disconnected {})).answered.contains ⟨2, 7⟩)
  , ("old request consumes late source", (close network (close emptyNetwork providerNeed)).answered.contains ⟨2, 7⟩)
  , ("new request consumes old answer", !(pending (request (close network {}) [⟨2, 7⟩])).contains ⟨2, 7⟩)
  , ("duplicate request is one slot", (request {} [⟨2, 7⟩, ⟨2, 7⟩]).wanted.length == 1)
  , ("duplicate writer is no extra answer", (close { network with edges := [writer, reader, writer, reader] } {}).answered.length == 3)
  , ("duplicate source is no extra answer", (close { network with seeds := [⟨0, 7⟩, ⟨0, 7⟩] } {}).answered.length == 3)
  , ("two groups arrive in bulk", ((close bulkNetwork {}).answered.contains ⟨2, 7⟩ &&
      (close bulkNetwork {}).answered.contains ⟨2, 9⟩))
  , ("conditional writer without actual RHS stays empty", (close emptyNetwork providerNeed).answered.isEmpty)
  , ("unavailable request is retained", (pending (close network providerNeed)).contains ⟨2, 9⟩)
  , ("unavailable request does not hold barrier", (next network (close network providerNeed)).isNone)
  , ("seed callback cannot answer twice", step network (close network {}) (.seed ⟨0, 7⟩) == close network {})
  , ("relay callback cannot answer twice", step network (close network {}) (.forward reader 7) == close network {})
  , ("provider needs a real certificate", (acceptProviderReply {} ⟨0, 7⟩ seedCertificate).answered.contains ⟨0, 7⟩)
  , ("proved reply is not repeated", (acceptProviderReply
      (acceptProviderReply {} ⟨0, 7⟩ seedCertificate) ⟨0, 7⟩ seedCertificate).answered.length == 1)
  , ("at most two slots per key", stepCount network providerNeed ≤ 2 * network.keys.length)
  , ("final state is idempotent", close network (close network {}) == close network {})
  ]

theorem controls_pass : controls.all Prod.snd = true := by decide +kernel

end Controls

end ApSpec.Run1StaticGroupProtocol
