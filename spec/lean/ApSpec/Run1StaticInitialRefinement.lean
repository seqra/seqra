/-
  A minimal finite model for direct refinement of a conditional static-original
  FLOW edge. All AP and statement keys are opaque. The adapter must certify
  reachability and AP transfer. This file proves only group-preserving identity
  transport, the direct field-group join, and its storage laws.

  The imported Derivation constructors are forward-only: a validated source,
  followed by registered group-preserving writer/copy edges. The backward need
  actions from the imported protocol are not used by this model.
-/
import ApSpec.Run1StaticGroupProtocol

namespace ApSpec.Run1StaticInitialRefinement
set_option autoImplicit false


abbrev Field := Nat
abbrev Point := Nat
abbrev AP := Nat
abbrev Group := Nat

/-- No source group, taint value, remote method or method-entry replay key. -/
structure Template where
  originalField : Field
  point : Point
  current : AP
  deriving DecidableEq, Repr

structure Subscription where
  template : Template
  group : Group
  deriving DecidableEq, Repr

inductive Layer where
  | normal | demand
  deriving DecidableEq, Repr

structure Emitted where
  originalField : Field
  point : Point
  current : AP
  group : Group
  layer : Layer
  deriving DecidableEq, Repr

def needKey (subscription : Subscription) : Run1StaticGroupProtocol.Atom :=
  ⟨subscription.template.originalField, subscription.group⟩

def materialize (subscription : Subscription) : Emitted :=
  ⟨subscription.template.originalField, subscription.template.point,
    subscription.template.current, subscription.group, .demand⟩

def subscriptionOf (edge : Emitted) : Subscription :=
  ⟨⟨edge.originalField, edge.point, edge.current⟩, edge.group⟩

@[simp] theorem materialize_roundtrip (subscription : Subscription) :
    subscriptionOf (materialize subscription) = subscription := by
  cases subscription with | mk template group => cases template; rfl

@[simp] theorem materialize_demand (subscription : Subscription) :
    (materialize subscription).layer = .demand := rfl

/-- The geometry adapter certifies each primitive. There is no mark-transform
    operation in this restricted template language. -/
inductive IdentityTransport : AP → AP → Type where
  | same (ap : AP) : IdentityTransport ap ap
  | localCopy (source target : AP) : IdentityTransport source target
  | callBinding (source target : AP) : IdentityTransport source target

def advance (template : Template) (point : Point) (to : AP)
    (_transport : IdentityTransport template.current to) : Template :=
  { template with point := point, current := to }

def advanceEmitted (edge : Emitted) (point : Point) (to : AP) : Emitted :=
  { edge with point := point, current := to }

theorem advance_preserves_original (template : Template) (point : Point) (to : AP)
    (transport : IdentityTransport template.current to) :
    (advance template point to transport).originalField = template.originalField := rfl

theorem identity_refinement_commutes (template : Template) (point : Point) (to : AP)
    (transport : IdentityTransport template.current to) (group : Group) :
    materialize ⟨advance template point to transport, group⟩ =
      advanceEmitted (materialize ⟨template, group⟩) point to := rfl

/-- Ordinary forward work can create a long template. This cost is not removed. -/
def composeTrace (template : Template) : List (Point × AP) → Template
  | [] => template
  | (point, ap) :: tail =>
      composeTrace (advance template point ap (.localCopy template.current ap)) tail

theorem compose_preserves_original (template : Template) (trace : List (Point × AP)) :
    (composeTrace template trace).originalField = template.originalField := by
  induction trace generalizing template with
  | nil => rfl
  | cons step tail ih =>
      exact ih (advance template step.1 step.2 (.localCopy template.current step.2))

/-- The request protocol has a field lookup operation only. It contains no
    predecessor-walk or method-entry-replay operation. -/
inductive RequestOperation where
  | fieldLookup (key : Run1StaticGroupProtocol.Atom)
  deriving DecidableEq, Repr

def requestOperations (subscription : Subscription) : List RequestOperation :=
  [.fieldLookup (needKey subscription)]

theorem request_independent_of_forward_depth (template : Template)
    (trace : List (Point × AP)) (group : Group) :
    requestOperations ⟨composeTrace template trace, group⟩ =
      [.fieldLookup ⟨template.originalField, group⟩] := by
  simp only [requestOperations, needKey, compose_preserves_original]

theorem request_has_one_lookup (subscription : Subscription) :
    (requestOperations subscription).length = 1 := rfl

/-- ZERO activates a conditional summary. It does not discharge its premise. -/
def applyConditionalFromZero (summary : Template) (callPoint : Point) (boundAP : AP) :
    Template := advance summary callPoint boundAP (.callBinding summary.current boundAP)

theorem zero_keeps_static_premise (summary : Template) (point : Point) (boundAP : AP) :
    (applyConditionalFromZero summary point boundAP).originalField = summary.originalField := rfl

structure State where
  supplied : List Run1StaticGroupProtocol.Atom := []
  templates : List Template := []
  subscriptions : List Subscription := []
  deriving DecidableEq, Repr

/-- This function is a direct key lookup. It performs no request transfer. -/
def enabled (state : State) (subscription : Subscription) : Bool :=
  state.templates.contains subscription.template &&
    state.supplied.contains (needKey subscription)

def outputs (state : State) : List Emitted :=
  ((state.subscriptions.filter (enabled state)).map materialize).eraseDups

def decodeEnabled (state : State) (edge : Emitted) : Bool :=
  edge.layer == .demand &&
    state.subscriptions.contains (subscriptionOf edge) &&
    enabled state (subscriptionOf edge)

theorem enabled_iff (state : State) (subscription : Subscription) :
    enabled state subscription = true ↔
      subscription.template ∈ state.templates ∧ needKey subscription ∈ state.supplied := by
  simp [enabled, Bool.and_eq_true]

theorem output_iff (state : State) (edge : Emitted) : edge ∈ outputs state ↔
    edge.layer = .demand ∧ subscriptionOf edge ∈ state.subscriptions ∧
      (subscriptionOf edge).template ∈ state.templates ∧
      needKey (subscriptionOf edge) ∈ state.supplied := by
  constructor
  · intro present
    rcases List.mem_map.mp (List.mem_eraseDups.mp present) with ⟨subscription, valid, eq⟩
    subst edge
    have valid := List.mem_filter.mp valid
    exact ⟨rfl, by simpa using valid.1, by simpa using (enabled_iff _ _).mp valid.2⟩
  · intro valid
    apply List.mem_eraseDups.mpr
    apply List.mem_map.mpr
    refine ⟨subscriptionOf edge, List.mem_filter.mpr ⟨valid.2.1, ?_⟩, ?_⟩
    · exact (enabled_iff _ _).mpr valid.2.2
    · cases edge
      simp only [materialize, subscriptionOf, Emitted.mk.injEq, true_and]
      exact valid.1.symm

/-- Actual group evidence is separate from conditional FLOW and subscription data. -/
def Safe (network : Run1StaticGroupProtocol.Network) (state : State) : Type :=
  (atom : Run1StaticGroupProtocol.Atom) → atom ∈ state.supplied → Run1StaticGroupProtocol.Derivation network atom

def emptySafe (network : Run1StaticGroupProtocol.Network) : Safe network {} := by
  intro atom impossible
  exact False.elim (by simp at impossible)

def offer {network : Run1StaticGroupProtocol.Network} (state : State) (atom : Run1StaticGroupProtocol.Atom)
    (_proof : Run1StaticGroupProtocol.Derivation network atom) : State :=
  { state with supplied := (atom :: state.supplied).eraseDups }

def offerSafe (network : Run1StaticGroupProtocol.Network) (state : State) (safe : Safe network state)
    (atom : Run1StaticGroupProtocol.Atom) (proof : Run1StaticGroupProtocol.Derivation network atom) :
    Safe network (offer state atom proof) := by
  intro candidate present
  have present : candidate = atom ∨ candidate ∈ state.supplied := by simpa [offer] using present
  if eq : candidate = atom then exact eq ▸ proof
  else exact safe candidate (present.resolve_left eq)

/-- The static-origin writer publishes a grounded group by forward composition.
    It does not wait for a target-group request. -/
def forwardWriter {network : Run1StaticGroupProtocol.Network} (state : State) (edge : Run1StaticGroupProtocol.Edge) (group : Group)
    (registered : edge ∈ network.edges)
    (within : Run1StaticGroupProtocol.Atom.mk edge.consumer group ∈ network.keys)
    (proof : Run1StaticGroupProtocol.Derivation network ⟨edge.producer, group⟩) : State :=
  offer state ⟨edge.consumer, group⟩
    (.hop edge group registered within proof)

def forwardWriterSafe (network : Run1StaticGroupProtocol.Network) (state : State)
    (safe : Safe network state) (edge : Run1StaticGroupProtocol.Edge) (group : Group)
    (registered : edge ∈ network.edges)
    (within : Run1StaticGroupProtocol.Atom.mk edge.consumer group ∈ network.keys)
    (proof : Run1StaticGroupProtocol.Derivation network ⟨edge.producer, group⟩) :
    Safe network (forwardWriter state edge group registered within proof) :=
  offerSafe network state safe _ (.hop edge group registered within proof)

def materializationCertificate (network : Run1StaticGroupProtocol.Network) (state : State)
    (safe : Safe network state) (edge : Emitted) (present : edge ∈ outputs state) :
    Run1StaticGroupProtocol.Derivation network ⟨edge.originalField, edge.group⟩ :=
  safe _ ((output_iff state edge).mp present).2.2.2

/-- The result contains a concrete root and the unchanged group identity. -/
def materializationRoot (network : Run1StaticGroupProtocol.Network) (state : State)
    (safe : Safe network state) (edge : Emitted) (present : edge ∈ outputs state) :
    { root : Run1StaticGroupProtocol.Atom // root ∈ network.seeds ∧ root.group = edge.group } :=
  Run1StaticGroupProtocol.sourceRoot (materializationCertificate network state safe edge present)

def register (state : State) (template : Template) : State :=
  { state with templates := (template :: state.templates).eraseDups }

def subscribe (state : State) (subscription : Subscription) : State :=
  { state with subscriptions := (subscription :: state.subscriptions).eraseDups }

def subscribeGroups (state : State) (template : Template) (groups : List Group) : State :=
  { state with subscriptions :=
      (state.subscriptions ++ groups.map (Subscription.mk template)).eraseDups }

theorem subscribe_groups_union (state : State) (template : Template) (groups : List Group)
    (group : Group) :
    Subscription.mk template group ∈ (subscribeGroups state template groups).subscriptions ↔
      Subscription.mk template group ∈ state.subscriptions ∨ group ∈ groups := by
  simp [subscribeGroups]

def registerSafe (network : Run1StaticGroupProtocol.Network) (state : State) (safe : Safe network state)
    (template : Template) : Safe network (register state template) := safe

def subscribeSafe (network : Run1StaticGroupProtocol.Network) (state : State) (safe : Safe network state)
    (subscription : Subscription) : Safe network (subscribe state subscription) := safe

theorem subscription_keeps_supply (state : State) (subscription : Subscription) :
    (subscribe state subscription).supplied = state.supplied := rfl

theorem registered_flow_keeps_supply (state : State) (template : Template) :
    (register state template).supplied = state.supplied := rfl

theorem no_source_no_output (network : Run1StaticGroupProtocol.Network) (state : State)
    (safe : Safe network state) (noSeed : network.seeds = []) : outputs state = [] := by
  have empty := Run1StaticGroupProtocol.no_source_no_answer noSeed
    (state := { answered := state.supplied }) safe
  change state.supplied = [] at empty
  cases equation : outputs state with
  | nil => rfl
  | cons edge tail =>
      have present : edge ∈ outputs state := by simp [equation]
      have supplied := ((output_iff state edge).mp present).2.2.2
      simp [empty] at supplied

def append (left right : State) : State :=
  ⟨left.supplied ++ right.supplied, left.templates ++ right.templates,
    left.subscriptions ++ right.subscriptions⟩

def pack (state : State) : State :=
  ⟨state.supplied.eraseDups, state.templates.eraseDups, state.subscriptions.eraseDups⟩

def merge (left right : State) : State := pack (append left right)

def mergeSafe (network : Run1StaticGroupProtocol.Network) (left right : State)
    (leftSafe : Safe network left) (rightSafe : Safe network right) :
    Safe network (merge left right) := by
  intro atom present
  have present : atom ∈ left.supplied ∨ atom ∈ right.supplied := by
    simpa [merge, pack, append] using present
  if inside : atom ∈ left.supplied then exact leftSafe atom inside
  else exact rightSafe atom (present.resolve_left inside)

def SameAtoms (left right : State) : Prop :=
  (∀ atom, atom ∈ left.supplied ↔ atom ∈ right.supplied) ∧
  (∀ template, template ∈ left.templates ↔ template ∈ right.templates) ∧
  (∀ subscription, subscription ∈ left.subscriptions ↔ subscription ∈ right.subscriptions)

def Equivalent (left right : State) : Prop :=
  ∀ edge, edge ∈ outputs left ↔ edge ∈ outputs right

theorem same_atoms_outputs {left right : State} (same : SameAtoms left right) :
    Equivalent left right := by
  intro edge
  simp only [output_iff]
  rw [same.1, same.2.1, same.2.2]

theorem packed_scalar_equivalent (state : State) : Equivalent (pack state) state := by
  apply same_atoms_outputs
  simp [SameAtoms, pack]

theorem merged_arrival_commutes (left right : State) :
    Equivalent (merge left right) (merge right left) := by
  apply same_atoms_outputs
  simp [SameAtoms, merge, pack, append, or_comm]

theorem merged_batch_associative (a b c : State) :
    Equivalent (merge (merge a b) c) (merge a (merge b c)) := by
  apply same_atoms_outputs
  simp [SameAtoms, merge, pack, append, or_assoc]

theorem duplicate_batch_same (state : State) : Equivalent (merge state state) state := by
  apply same_atoms_outputs
  simp [SameAtoms, merge, pack, append]

theorem offer_subscription_arrivals {network : Run1StaticGroupProtocol.Network} (state : State) (atom : Run1StaticGroupProtocol.Atom)
    (proof : Run1StaticGroupProtocol.Derivation network atom) (subscription : Subscription) :
    Equivalent (subscribe (offer state atom proof) subscription)
      (offer (subscribe state subscription) atom proof) := by
  apply same_atoms_outputs
  simp [SameAtoms, subscribe, offer]

theorem flow_subscription_arrivals (state : State) (template : Template)
    (subscription : Subscription) :
    Equivalent (subscribe (register state template) subscription)
      (register (subscribe state subscription) template) := by
  apply same_atoms_outputs
  simp [SameAtoms, subscribe, register]

def fresh (state : State) (delivered : List Emitted) : List Emitted :=
  (outputs state).filter (fun edge => !delivered.contains edge)

def commit (state : State) (delivered : List Emitted) : List Emitted :=
  (delivered ++ outputs state).eraseDups

theorem fresh_iff (state : State) (delivered : List Emitted) (edge : Emitted) :
    edge ∈ fresh state delivered ↔ edge ∈ outputs state ∧ edge ∉ delivered := by
  simp [fresh]

theorem fresh_after_commit_empty (state : State) (delivered : List Emitted) :
    fresh state (commit state delivered) = [] := by
  cases equation : fresh state (commit state delivered) with
  | nil => rfl
  | cons edge tail =>
      have present : edge ∈ fresh state (commit state delivered) := by simp [equation]
      have facts := (fresh_iff state _ edge).mp present
      exact False.elim (facts.2 (by simp [commit, facts.1]))

theorem no_normal_output (state : State) (edge : Emitted) (normal : edge.layer = .normal) :
    edge ∉ outputs state := by
  intro present
  have demand := ((output_iff state edge).mp present).1
  simp [normal] at demand

theorem output_at_subscribed_point (state : State) (subscription : Subscription)
    (_present : materialize subscription ∈ outputs state) :
    (materialize subscription).point = subscription.template.point ∧
      (materialize subscription).current = subscription.template.current := ⟨rfl, rfl⟩

theorem direct_lookup_only (state : State) (template : Template) (group : Group) :
    enabled state ⟨template, group⟩ =
      (state.templates.contains template &&
        state.supplied.contains ⟨template.originalField, group⟩) := rfl

theorem refinement_complete (state : State) (subscription : Subscription)
    (registered : subscription.template ∈ state.templates)
    (requested : subscription ∈ state.subscriptions)
    (supplied : needKey subscription ∈ state.supplied) :
    materialize subscription ∈ outputs state := by
  exact (output_iff state _).mpr ⟨rfl, by simpa using requested,
    by simpa using registered, by simpa using supplied⟩

namespace Controls

def network : Run1StaticGroupProtocol.Network :=
  ⟨[⟨0, 5⟩, ⟨0, 9⟩, ⟨7, 5⟩, ⟨7, 9⟩, ⟨8, 5⟩, ⟨8, 9⟩],
    [⟨0, 5⟩], [⟨0, 7⟩, ⟨7, 8⟩]⟩

def noSourceNetwork : Run1StaticGroupProtocol.Network :=
  { network with seeds := [] }

def sourceLessCopyCycle : State :=
  subscribe (subscribe (register (register {} ⟨7, 40, 8⟩) ⟨8, 41, 7⟩)
    ⟨⟨7, 40, 8⟩, 5⟩) ⟨⟨8, 41, 7⟩, 5⟩

def readInGet : Template := ⟨7, 10, 100⟩
def getSummary : Template := advance readInGet 11 101 (.localCopy 100 101)
def callerResult : Template := applyConditionalFromZero getSummary 20 200
def consumePoint : Template := advance callerResult 30 200 (.same 200)
def consumer : Subscription := ⟨consumePoint, 5⟩
def otherGroupConsumer : Subscription := ⟨consumePoint, 9⟩

def originalSource : Run1StaticGroupProtocol.Derivation network ⟨0, 5⟩ :=
  .source _ (by decide) (by decide)

def fieldSource : Run1StaticGroupProtocol.Derivation network ⟨7, 5⟩ :=
  .hop ⟨0, 7⟩ 5 (by decide) (by decide) originalSource

def copiedSource : Run1StaticGroupProtocol.Derivation network ⟨8, 5⟩ :=
  .hop ⟨7, 8⟩ 5 (by decide) (by decide) fieldSource

def awaiting : State := subscribe (register {} consumePoint) consumer
def suppliedFirst : State := offer {} ⟨7, 5⟩ fieldSource
def sourceLate : State := offer awaiting ⟨7, 5⟩ fieldSource
def subscriptionLate : State := subscribe (register suppliedFirst consumePoint) consumer
def flowLate : State := register (subscribe suppliedFirst consumer) consumePoint

def controls : List (String × Bool) :=
  [ ("get retains conditional static original", getSummary.originalField == 7)
  , ("ZERO call retains static original", callerResult.originalField == 7)
  , ("forward compose retains static original", consumePoint.originalField == 7)
  , ("conditional FLOW is not group supply", (register {} consumePoint).supplied.isEmpty)
  , ("ZERO activation is not group supply", (register {} callerResult).supplied.isEmpty)
  , ("group request is not supply", awaiting.supplied.isEmpty)
  , ("FLOW and group request do not answer", (outputs awaiting).isEmpty)
  , ("late source answers at consume point", (outputs sourceLate).contains (materialize consumer))
  , ("late subscription answers old source", (outputs subscriptionLate).contains (materialize consumer))
  , ("late FLOW template answers old request", (outputs flowLate).contains (materialize consumer))
  , ("arrival orders have same output", outputs sourceLate == outputs subscriptionLate)
  , ("injected edge stays DEMAND", (outputs sourceLate).all (fun edge => edge.layer == .demand))
  , ("injected edge starts at static original", (outputs sourceLate).all (fun edge => edge.originalField == 7))
  , ("injection is at current request point", (outputs sourceLate).all (fun edge => edge.point == 30))
  , ("injection retains current AP", (outputs sourceLate).all (fun edge => edge.current == 200))
  , ("no method-entry replay output", !(outputs sourceLate).any (fun edge => edge.point == 0))
  , ("no wrong group from request", (outputs (subscribe suppliedFirst otherGroupConsumer)).isEmpty)
  , ("registered wrong-group request remains unanswered", (outputs
      (subscribe (register suppliedFirst consumePoint) otherGroupConsumer)).isEmpty)
  , ("duplicate subscriptions use one slot", (subscribe awaiting consumer).subscriptions.length == 1)
  , ("duplicate source offers use one slot", (offer suppliedFirst ⟨7, 5⟩ fieldSource).supplied.length == 1)
  , ("answered subscription is not delivered twice", (fresh sourceLate (commit sourceLate [])).isEmpty)
  , ("copy writer publishes grounded group eagerly", (forwardWriter {} ⟨7, 8⟩ 5
      (by decide : Run1StaticGroupProtocol.Edge.mk 7 8 ∈ network.edges)
      (by decide : Run1StaticGroupProtocol.Atom.mk 8 5 ∈ network.keys) fieldSource).supplied.contains ⟨8, 5⟩)
  , ("copied field refines its local consumer", (outputs (subscribe
      (register (offer {} ⟨8, 5⟩ copiedSource) ⟨8, 40, 300⟩) ⟨⟨8, 40, 300⟩, 5⟩)).length == 1)
  , ("source-less network has no initial offers", noSourceNetwork.seeds.isEmpty)
  , ("source-less copy cycle has no output", (outputs sourceLessCopyCycle).isEmpty)
  , ("duplicate batch preserves one delivery", (outputs (merge sourceLate sourceLate)).length == 1)
  , ("request ignores forward-template depth", requestOperations
      ⟨composeTrace consumePoint [(31, 201), (32, 202), (33, 203)], 5⟩ ==
        [.fieldLookup ⟨7, 5⟩])
  , ("group batch merges duplicate requests", (subscribeGroups {} consumePoint [5, 9, 5]).subscriptions.length == 2)
  ]

theorem controls_pass : controls.all Prod.snd = true := by decide

end Controls

end ApSpec.Run1StaticInitialRefinement
