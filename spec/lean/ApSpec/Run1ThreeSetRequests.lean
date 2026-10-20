/- Three-set static requests. No request can supply a group. A reply remains
   owned by its actor until consumed, including when another group joins. -/
import ApSpec.Run1StaticGroupProtocol
namespace ApSpec.Run1ThreeSetRequests
set_option autoImplicit false
structure Cell where
  requested : Bool := false
  written : Bool := false
  deriving DecidableEq, Repr

def req (c : Cell) : Bool := c.requested && !c.written
def reach (c : Cell) : Bool := !c.requested && c.written
def processed (c : Cell) : Bool := c.requested && c.written
def request (c : Cell) : Cell := { c with requested := true }
def write (c : Cell) : Cell := { c with written := true }

theorem disjoint (c : Cell) :
    (req c && reach c) = false ∧ (req c && processed c) = false ∧
    (reach c && processed c) = false := by
  cases c with | mk r w => cases r <;> cases w <;> decide

theorem requested_partition (c : Cell) : (req c || processed c) = c.requested := by
  cases c with | mk r w => cases r <;> cases w <;> decide

theorem written_partition (c : Cell) : (reach c || processed c) = c.written := by
  cases c with | mk r w => cases r <;> cases w <;> decide

theorem request_cannot_supply (c : Cell) : (request c).written = c.written := rfl
theorem both_orders (c : Cell) : request (write c) = write (request c) := rfl
theorem request_idempotent (c : Cell) : request (request c) = request c := rfl

structure PairState where
  registered : Bool := false
  admitted : Bool := false
  deriving DecidableEq, Repr

def ask (c : Cell) (p : PairState) : PairState × Bool :=
  let fresh := c.requested && p.registered && !p.admitted
  ({ p with admitted := p.admitted || fresh }, fresh)

theorem ask_once (c : Cell) (p : PairState) : (ask c (ask c p).1).2 = false := by
  cases c with | mk r w => cases p with | mk reg a =>
    cases r <;> cases w <;> cases reg <;> cases a <;> decide

theorem requested_pair_closure (c : Cell) (p : PairState)
    (r : c.requested = true) (w : p.registered = true) :
    (ask c p).1.admitted = true := by simp [ask, r, w]

theorem unrequested_write_no_broadcast (c : Cell) (p : PairState)
    (r : c.requested = false) : (ask (write c) p).2 = false := by
  simp [ask, write, r]

/-- A supplied region is relative to one already matched (class, field) key.
    ANY includes the empty suffix; EXACT contains only its own point. -/
structure Region where
  path : List Nat
  descendants : Bool
  deriving DecidableEq, Repr

def Denotes (r : Region) (image : List Nat) : Prop :=
  ∃ rest, image = r.path ++ rest ∧ (r.descendants = true ∨ rest = [])

def canonical : Region := ⟨[], true⟩

def canonicalWitness (image : List Nat) :
    {rest : List Nat // image = canonical.path ++ rest ∧
      (canonical.descendants = true ∨ rest = [])} :=
  ⟨image, rfl, Or.inl rfl⟩

theorem canonical_covers (old : Region) (image : List Nat)
    (_matched : Denotes old image) : Denotes canonical image :=
  ⟨(canonicalWitness image).val, (canonicalWitness image).property⟩

/-- The initial has STAR, while its marked current image has ANY. This model
    tests the current denotation, so an exact write is included as well. -/
theorem canonical_contains_empty : Denotes canonical [] :=
  ⟨[], rfl, Or.inl rfl⟩

structure FullMark where
  group : Nat
  member : Nat
  deriving DecidableEq, Repr

structure GroupDemand where
  group : Nat
  region : Region
  deriving DecidableEq, Repr

def guided (d : GroupDemand) (m : FullMark) (image : List Nat) : Prop :=
  d.group = m.group ∧ Denotes d.region image

theorem full_mark_guidance (old : Region) (m : FullMark) (image : List Nat)
    (matched : Denotes old image) : guided ⟨m.group, canonical⟩ m image :=
  ⟨rfl, canonical_covers old image matched⟩

/-- The source/hop constructors are the previously checked ordinary forward
    derivations. A requested traversal can use only these constructors; neither
    request admission nor a canonical hypothesis adds a source. -/
def resumeFlow (n : Run1StaticGroupProtocol.Network)
    (edge : Run1StaticGroupProtocol.Edge) (g : Nat)
    (registered : edge ∈ n.edges)
    (within : Run1StaticGroupProtocol.Atom.mk edge.consumer g ∈ n.keys)
    (input : Run1StaticGroupProtocol.Derivation n ⟨edge.producer, g⟩) :
    Run1StaticGroupProtocol.Derivation n ⟨edge.consumer, g⟩ :=
  .hop edge g registered within input

/-- An executable request-driven trace. A writer node records the output
    request, its registered writer, the matching input request, then the input
    replay and ordinary composition. The atom index preserves target and group.
    Registration of each ordinary FLOW relation is a JVM/actor adapter gate. -/
inductive Replay (n : Run1StaticGroupProtocol.Network) :
    Run1StaticGroupProtocol.Atom → Type where
  | source (atom : Run1StaticGroupProtocol.Atom) (seed : atom ∈ n.seeds)
      (within : atom ∈ n.keys) : Replay n atom
  | writer (edge : Run1StaticGroupProtocol.Edge) (group : Nat)
      (registered : edge ∈ n.edges)
      (within : Run1StaticGroupProtocol.Atom.mk edge.consumer group ∈ n.keys)
      (inputRequest : Replay n ⟨edge.producer, group⟩) :
      Replay n ⟨edge.consumer, group⟩

def requestReplay (n : Run1StaticGroupProtocol.Network)
    {a : Run1StaticGroupProtocol.Atom}
    (ordinary : Run1StaticGroupProtocol.Derivation n a) : Replay n a :=
  match ordinary with
  | .source atom seed within => .source atom seed within
  | .hop edge group registered within input =>
      .writer edge group registered within (requestReplay n input)

def replayOrdinary (n : Run1StaticGroupProtocol.Network)
    {a : Run1StaticGroupProtocol.Atom} (replay : Replay n a) :
    Run1StaticGroupProtocol.Derivation n a :=
  match replay with
  | .source atom seed within => .source atom seed within
  | .writer edge group registered within input =>
      .hop edge group registered within (replayOrdinary n input)

def ordinaryTrace (n : Run1StaticGroupProtocol.Network)
    {a : Run1StaticGroupProtocol.Atom}
    (ordinary : Run1StaticGroupProtocol.Derivation n a) :
    List (Run1StaticGroupProtocol.Edge × Nat) :=
  match ordinary with
  | .source _ _ _ => []
  | .hop edge group _ _ input => (edge, group) :: ordinaryTrace n input

def replayTrace (n : Run1StaticGroupProtocol.Network)
    {a : Run1StaticGroupProtocol.Atom} (replay : Replay n a) :
    List (Run1StaticGroupProtocol.Edge × Nat) :=
  match replay with
  | .source _ _ _ => []
  | .writer edge group _ _ input => (edge, group) :: replayTrace n input

theorem requested_transitive_closure (n : Run1StaticGroupProtocol.Network)
    {a : Run1StaticGroupProtocol.Atom}
    (ordinary : Run1StaticGroupProtocol.Derivation n a) :
    replayTrace n (requestReplay n ordinary) = ordinaryTrace n ordinary := by
  induction ordinary with
  | source => rfl
  | hop edge group registered within input ih =>
      simp only [requestReplay, replayTrace, ordinaryTrace, ih]

/-- Both directions yield executable witnesses, rather than a classical
    existence argument. No unrelated, unrequested writer need be replayed. -/
def requestedClosureWitness (n : Run1StaticGroupProtocol.Network)
    {a : Run1StaticGroupProtocol.Atom}
    (ordinary : Run1StaticGroupProtocol.Derivation n a) :
    {replay : Replay n a // replayTrace n replay = ordinaryTrace n ordinary} :=
  ⟨requestReplay n ordinary, requested_transitive_closure n ordinary⟩

/-- A complete fixed ND binding carries an input certificate for every member.
    This is a runtime adapter obligation, not permission to fire one member. -/
structure CompleteBinding (n : Run1StaticGroupProtocol.Network) where
  members : List Run1StaticGroupProtocol.Atom
  nonempty : members ≠ []
  evidence : (atom : Run1StaticGroupProtocol.Atom) → atom ∈ members →
    Run1StaticGroupProtocol.Derivation n atom

def rootOf (n : Run1StaticGroupProtocol.Network)
    {a : Run1StaticGroupProtocol.Atom}
    (proof : Run1StaticGroupProtocol.Derivation n a) :
    {root : Run1StaticGroupProtocol.Atom // root ∈ n.seeds} :=
  match proof with
  | .source atom registered _ => ⟨atom, registered⟩
  | .hop _ _ _ _ input => rootOf n input

def firstBindingRoot (n : Run1StaticGroupProtocol.Network)
    (binding : CompleteBinding n) :
    {root : Run1StaticGroupProtocol.Atom // root ∈ n.seeds} := by
  cases h : binding.members with
  | nil => exact False.elim (binding.nonempty h)
  | cons first rest =>
    exact rootOf n (binding.evidence first (by simp [h]))

theorem empty_cycle_cannot_supply (n : Run1StaticGroupProtocol.Network)
    (empty : n.seeds = []) {a : Run1StaticGroupProtocol.Atom}
    (proof : Run1StaticGroupProtocol.Derivation n a) : False := by
  have root := (rootOf n proof).property
  simp [empty] at root

theorem empty_cycle_cannot_complete_nd (n : Run1StaticGroupProtocol.Network)
    (empty : n.seeds = []) (binding : CompleteBinding n) : False := by
  have root := (firstBindingRoot n binding).property
  simp [empty] at root

structure Actor where
  waiting : List Nat := []
  deriving DecidableEq, Repr

def admit (a : Actor) (g : Nat) : Actor :=
  { waiting := (g :: a.waiting).eraseDups }
def consume (a : Actor) (g : Nat) : Actor :=
  { waiting := a.waiting.filter (· != g) }

theorem admit_idempotent (a : Actor) (g x : Nat) :
    x ∈ (admit (admit a g) g).waiting ↔ x ∈ (admit a g).waiting := by
  simp [admit]

theorem queued_reply_preserves_new_membership (a : Actor) (old new : Nat)
    (different : new ≠ old) : new ∈ (consume (admit a new) old).waiting := by
  simp [consume, admit, different]

theorem consume_only_answered (a : Actor) (old other : Nat)
    (different : other ≠ old) :
    other ∈ (consume a old).waiting ↔ other ∈ a.waiting := by
  simp [consume, different]

def runnable (_pending : List Nat) (effects : List Nat) : Bool := !effects.isEmpty

theorem unavailable_quiescent (pending : List Nat) : runnable pending [] = false := rfl
namespace Controls

def oldWriter : PairState := ⟨true, false⟩
def newRequest : Cell := request (write {})
def needOnly : Cell := request {}

def checks : List Bool :=
  [ reach (write {})
  , !(ask (write {}) oldWriter).2
  , processed newRequest
  , (ask newRequest oldWriter).2
  , !(ask newRequest (ask newRequest oldWriter).1).2
  , req needOnly
  , processed (write needOnly)
  , (consume (admit (admit {} 7) 9) 7).waiting.contains 9
  , !(consume (admit {} 7) 7).waiting.contains 7
  , !runnable [7, 9] []
  ]

theorem controls_pass : checks.all id = true := by decide
end Controls

end ApSpec.Run1ThreeSetRequests
