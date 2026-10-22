/-
Run-1 static GROUP protocol removal. This model uses the prior processed-origin
invariant. It keeps the scalar AP reducer and scalar call suffix. It removes
only an established group's interest/wait/reply protocol. No packed-suffix
additivity or AP self-application identity is assumed.

The same canonical input, original summary, method/call context, and pure
reducer are used on both paths for an aligned scalar semantic job. Old pending
CallKey coalescing can combine several offers; direct processing need not have
the same physical application count. Several stored headers can also yield
the same selected scalar. Reference traces normalize those jobs; complete
closure compares effect membership, not diagnostic multiplicity. The source
adapter must prove the scalar arguments match the actual Kotlin construction.
Effects include raw method summaries,
the exact normalized entry/exit/group demand pairs, sink seeds, pool supplies,
and the symbolic implicit static-group permission. Protocol rows are absent
from that handoff. No new ZERO exit/source eligibility policy is introduced.

The selected run-1 store has Empty field exclusions. The changed-exclusion
branch therefore cannot replace an existing entry; accepted roots only grow
by union. The second part proves exact selector persistence and constructs a
live scalar witness. It does not claim this law for later runs with nonempty
exclusions. Equal seed membership lifts to finite positive semantic closure
after stable/fair work, without asserting equal diagnostic partial traces.
-/
import ApSpec.Run1StaticPremiseAvailability

namespace ApSpec.Run1StaticGroupProtocolRemoval

set_option autoImplicit false


abbrev Atom := Run1StaticPremiseAvailability.Atom
abbrev StaticSlot := Run1StaticPremiseAvailability.StaticSlot

inductive Base where
  | zero | local (name : Nat) | returned | static
  deriving DecidableEq, Repr

inductive Tail where
  | exact | star | any | must
  deriving DecidableEq, Repr

inductive Mark where
  | flow (excludedGroups : List Nat)
  | group (id : Nat)
  | full (groupId markId : Nat)
  deriving DecidableEq, Repr

structure Pattern where
  base : Base
  path : List Nat
  tail : Tail
  mark : Mark
  exclusion : List Nat := []
  deriving DecidableEq, Repr

/-- The two tags abstract distinct class and field accessor shapes. They do
    not change the runtime AP field-count convention. -/
def slotPath : StaticSlot → List Nat
  | .field f => [0, f]
  | .exactClass c => [1, c]

def initialOf (a : Atom) : Pattern :=
  ⟨.static, slotPath a.slot,
    match a.slot with | .field _ => .star | .exactClass _ => .exact,
    .group a.group, []⟩

/-- factsOf maps concrete STAR to an ANY current fact. Exact class carriers
    stay exact. The runtime adapter additionally checks receiving ownership. -/
def materialize (p : Pattern) : Pattern :=
  { p with tail := if p.tail == .star then .any else p.tail, exclusion := [] }

structure CurrentFact where
  pattern : Pattern
  layer : Nat
  deriving DecidableEq, Repr

def demandLayer : Nat := 1

def poolAnswer (a : Atom) : CurrentFact := ⟨materialize (initialOf a), demandLayer⟩
def directInput (a : Atom) : CurrentFact := ⟨materialize (initialOf a), demandLayer⟩

theorem same_canonical_input (a : Atom) : poolAnswer a = directInput a := rfl

structure CallContext where
  owner : Nat
  statement : Nat
  stage : Nat
  callerLayer : Nat
  pendingDemotion : Bool
  aliasClassification : Bool
  deriving DecidableEq, Repr

structure Row where
  method : Nat
  original : Pattern
  conclusion : Pattern
  layer : Nat
  deriving DecidableEq, Repr

structure SinkSeed where
  method : Nat
  statement : Nat
  rule : Nat
  requirement : Pattern
  predicateOccurrence : Option Nat := none
  deriving DecidableEq, Repr

structure DemandPair where
  method : Nat
  entry : Pattern
  exit : Pattern
  deriving DecidableEq, Repr

def located (p : Pattern) : Pattern :=
  if p.tail == .must then { p with tail := .any, exclusion := [] } else p

def normalizeMark (p : Pattern) : Pattern :=
  match p.mark with
  | .flow _ => { p with mark := .flow [] }
  | _ => p

def backwardEntry (p : Pattern) : Pattern :=
  let p := located p
  normalizeMark (if p.tail == .star then { p with tail := .any, exclusion := [] } else p)

def backwardExit (p : Pattern) : Pattern := normalizeMark (located p)

def demandOf (row : Row) : DemandPair :=
  ⟨row.method, backwardEntry row.conclusion, backwardExit row.original⟩

/-- This is DemandStore.implicitStaticPair's symbolic family. It is neither
    a method-by-field store nor a newly invented explicit summary. -/
def implicitStaticPair (actual : Pattern) : Option (Pattern × Pattern) :=
  match actual.base, actual.mark with
  | .static, .full group _ => some
      (⟨.static, [], .any, .group group, []⟩,
       ⟨.static, [], .star, .group group, []⟩)
  | _, _ => none

inductive Effect where
  | retain (row : Row)
  | sink (seed : SinkSeed)
  | supply (atom : Atom)
  deriving DecidableEq, Repr

inductive Observation where
  | raw (row : Row)
  | demand (pair : DemandPair)
  | sinkSeed (seed : SinkSeed)
  | supplied (atom : Atom)
  | implicitStaticGroups
  deriving DecidableEq, Repr

def effectObservations : Effect → List Observation
  | .retain row => [.raw row, .demand (demandOf row)]
  | .sink seed => [.sinkSeed seed]
  | .supply atom => [.supplied atom]

def handoff (effects : List Effect) : List Observation :=
  .implicitStaticGroups :: effects.flatMap effectObservations

/-- This is an unchanged pure reducer, not an assumed old/new equality.
    The caller suffix stays scalar. Every side effect is an explicit result. -/
structure ScalarReducer where
  call : CurrentFact → CallContext → Row → List Effect
  computed : CurrentFact → Row → List Effect

structure CallJob (n : Run1StaticPremiseAvailability.Network) where
  atom : Atom
  context : CallContext
  row : Row
  originalMatches : row.original = initialOf atom
  originResult : Run1StaticPremiseAvailability.Output
  originDerivation : Run1StaticPremiseAvailability.Edge n row.method (.staticGroup atom) originResult

def oldCallBody (reducer : ScalarReducer) {n : Run1StaticPremiseAvailability.Network} (job : CallJob n) : List Effect :=
  reducer.call (poolAnswer job.atom) job.context job.row

def newCallBody (reducer : ScalarReducer) {n : Run1StaticPremiseAvailability.Network} (job : CallJob n) : List Effect :=
  reducer.call (directInput job.atom) job.context job.row

theorem call_scalar_arguments_and_effects_equal (reducer : ScalarReducer)
    {n : Run1StaticPremiseAvailability.Network} (job : CallJob n) : oldCallBody reducer job = newCallBody reducer job := rfl

theorem call_has_prior_processed_witness {n : Run1StaticPremiseAvailability.Network} (job : CallJob n) :
    Nonempty (Run1StaticPremiseAvailability.Processed n job.atom) :=
  Run1StaticPremiseAvailability.published_static_premise_is_processed job.originDerivation

theorem call_repeat_request_wakes_no_writer {n : Run1StaticPremiseAvailability.Network} (_job : CallJob n) :
    (Run1StaticPremiseAvailability.request .processed).writerWake = false := rfl

inductive Event where
  | wait (atom : Atom)
  | interest (atom : Atom)
  | reply (atom : Atom)
  | effect (value : Effect)
  deriving DecidableEq, Repr

def semanticEffects (events : List Event) : List Effect := events.filterMap fun event =>
  match event with | .effect value => some value | _ => none

def oldCallEvents (reducer : ScalarReducer) {n : Run1StaticPremiseAvailability.Network} (job : CallJob n) : List Event :=
  [.wait job.atom, .interest job.atom, .reply job.atom] ++
    (oldCallBody reducer job).map Event.effect

def newCallEvents (reducer : ScalarReducer) {n : Run1StaticPremiseAvailability.Network} (job : CallJob n) : List Event :=
  (newCallBody reducer job).map Event.effect

theorem handshake_has_no_handoff_effect (a : Atom) :
    semanticEffects [.wait a, .interest a, .reply a] = [] := rfl

theorem call_boundary_effect_simulation (reducer : ScalarReducer)
    {n : Run1StaticPremiseAvailability.Network} (job : CallJob n) :
    semanticEffects (oldCallEvents reducer job) = semanticEffects (newCallEvents reducer job) := by
  simp [semanticEffects, oldCallEvents, newCallEvents, oldCallBody, newCallBody,
    poolAnswer, directInput]

theorem call_boundary_all_demands_preserved (reducer : ScalarReducer)
    {n : Run1StaticPremiseAvailability.Network} (job : CallJob n) :
    handoff (semanticEffects (oldCallEvents reducer job)) =
      handoff (semanticEffects (newCallEvents reducer job)) := by
  rw [call_boundary_effect_simulation]

/-- The local input is a just-admitted immutable scalar delta. Retention is
    outside the transient supply join and remains on both paths. -/
structure ComputedJob (n : Run1StaticPremiseAvailability.Network) where
  atom : Atom
  row : Row
  originalMatches : row.original = initialOf atom
  originResult : Run1StaticPremiseAvailability.Output
  originDerivation : Run1StaticPremiseAvailability.Edge n row.method (.staticGroup atom) originResult

def oldComputedBody (reducer : ScalarReducer) {n : Run1StaticPremiseAvailability.Network} (job : ComputedJob n) : List Effect :=
  .retain job.row :: reducer.computed (poolAnswer job.atom) job.row

def newComputedBody (reducer : ScalarReducer) {n : Run1StaticPremiseAvailability.Network} (job : ComputedJob n) : List Effect :=
  .retain job.row :: reducer.computed (directInput job.atom) job.row

theorem immutable_computed_boundary_equal (reducer : ScalarReducer)
    {n : Run1StaticPremiseAvailability.Network} (job : ComputedJob n) :
    oldComputedBody reducer job = newComputedBody reducer job := rfl

theorem computed_original_raw_and_demand_kept (reducer : ScalarReducer)
    {n : Run1StaticPremiseAvailability.Network} (job : ComputedJob n) :
    Observation.raw job.row ∈ handoff (newComputedBody reducer job) ∧
    Observation.demand (demandOf job.row) ∈ handoff (newComputedBody reducer job) := by
  simp [handoff, newComputedBody, effectObservations]

inductive Command (n : Run1StaticPremiseAvailability.Network) where
  | common (effects : List Effect)
  | call (job : CallJob n)
  | computed (job : ComputedJob n)

def oldBoundary (reducer : ScalarReducer) {n : Run1StaticPremiseAvailability.Network} : Command n → List Effect
  | .common effects => effects
  | .call job => semanticEffects (oldCallEvents reducer job)
  | .computed job => oldComputedBody reducer job

def newBoundary (reducer : ScalarReducer) {n : Run1StaticPremiseAvailability.Network} : Command n → List Effect
  | .common effects => effects
  | .call job => semanticEffects (newCallEvents reducer job)
  | .computed job => newComputedBody reducer job

theorem each_boundary_simulates (reducer : ScalarReducer) {n : Run1StaticPremiseAvailability.Network} (command : Command n) :
    oldBoundary reducer command = newBoundary reducer command := by
  cases command with
  | common => rfl
  | call job => exact call_boundary_effect_simulation reducer job
  | computed => rfl

def oldProgram (reducer : ScalarReducer) {n : Run1StaticPremiseAvailability.Network} (commands : List (Command n)) : List Effect :=
  commands.flatMap (oldBoundary reducer)

def newProgram (reducer : ScalarReducer) {n : Run1StaticPremiseAvailability.Network} (commands : List (Command n)) : List Effect :=
  commands.flatMap (newBoundary reducer)

theorem finite_boundary_trace_effects_equal (reducer : ScalarReducer) {n : Run1StaticPremiseAvailability.Network}
    (commands : List (Command n)) : oldProgram reducer commands = newProgram reducer commands := by
  induction commands with
  | nil => rfl
  | cons command rest ih =>
    simp only [oldProgram, newProgram, List.flatMap_cons]
    rw [each_boundary_simulates]
    exact congrArg (List.append (newBoundary reducer command)) ih

theorem finite_handoff_method_entry_exit_group_seed_equality (reducer : ScalarReducer)
    {n : Run1StaticPremiseAvailability.Network} (commands : List (Command n)) :
    handoff (oldProgram reducer commands) = handoff (newProgram reducer commands) := by
  rw [finite_boundary_trace_effects_equal]

theorem repeated_equal_effects_do_not_change_handoff_membership (effects : List Effect)
    (observation : Observation) :
    observation ∈ handoff (effects ++ effects) ↔ observation ∈ handoff effects := by
  simp [handoff, List.flatMap_append, List.mem_append, or_self]

theorem duplicate_boundary_in_context_preserves_handoff_membership
    (before boundary after : List Effect) (observation : Observation) :
    observation ∈ handoff (before ++ boundary ++ boundary ++ after) ↔
      observation ∈ handoff (before ++ boundary ++ after) := by
  simp [handoff, List.flatMap_append, List.mem_append]

/-- A concrete backward authorization keeps its complete paired pattern or
    the existing symbolic family. A sink seed is separately required at its
    method/statement. The implicit family does not replace a return guide. -/
inductive Run2Guidance (observations : List Observation) where
  | explicit (pair : DemandPair) (present : .demand pair ∈ observations)
  | sink (seed : SinkSeed) (present : .sinkSeed seed ∈ observations)
  | implicitStatic (actual entry exit : Pattern)
      (pair : implicitStaticPair actual = some (entry, exit))
      (enabled : .implicitStaticGroups ∈ observations)

def preserveRun2Guidance (reducer : ScalarReducer) {n : Run1StaticPremiseAvailability.Network}
    (commands : List (Command n))
    (witness : Run2Guidance (handoff (oldProgram reducer commands))) :
    Run2Guidance (handoff (newProgram reducer commands)) :=
  finite_handoff_method_entry_exit_group_seed_equality reducer commands ▸ witness

theorem method_scope_and_complete_pair_retained (row : Row) :
    (demandOf row).method = row.method ∧
    (demandOf row).entry = backwardEntry row.conclusion ∧
    (demandOf row).exit = backwardExit row.original := ⟨rfl, rfl, rfl⟩

theorem concrete_group_not_erased_by_normalization (p : Pattern) (g : Nat)
    (mark : p.mark = .group g) : (normalizeMark p).mark = .group g := by
  simp [normalizeMark, mark]

theorem symbolic_static_permission_kept (effects : List Effect) :
    Observation.implicitStaticGroups ∈ handoff effects := by simp [handoff]

/- Exact selector persistence in the selected empty-exclusion run-1 store. -/

structure GroupLeaf where
  slot : StaticSlot
  group : Nat
  tail : Tail
  deriving DecidableEq, Repr

def poolProjection (leaf : GroupLeaf) : Atom := ⟨leaf.slot, leaf.group⟩

/-- One exact leaf-membership relation. It includes the path/mark/tail key,
    not merely the projected field/group. Root union is pointwise disjunction.
    This is a proof model, not a proposed runtime table. -/
abbrev LeafSet := GroupLeaf → Bool

structure TreeEntry where
  rootId : Nat
  tail : Tail
  exclusion : List Nat
  leaves : LeafSet

/-- The only selected source branch that can remove an existing entry uses
    changed field exclusions for the same root and tail. Run-1 Empty E makes
    this branch impossible. Incoming subtraction is outside this function:
    accepted contains only a newly admitted delta, never discarded input. -/
def replaceExisting (old incoming : TreeEntry) : Bool :=
  old.rootId == incoming.rootId && old.tail == incoming.tail &&
    !(old.exclusion == incoming.exclusion)

def mergeEntry (old incoming : TreeEntry) : LeafSet :=
  if replaceExisting old incoming then incoming.leaves
  else fun leaf => old.leaves leaf || incoming.leaves leaf

theorem empty_exclusions_cannot_replace_existing (old incoming : TreeEntry)
    (oldE : old.exclusion = []) (incomingE : incoming.exclusion = []) :
    replaceExisting old incoming = false := by
  unfold replaceExisting
  rw [oldE, incomingE]
  exact Bool.and_false _

theorem run1_merge_is_union (old incoming : TreeEntry)
    (oldE : old.exclusion = []) (incomingE : incoming.exclusion = []) (leaf : GroupLeaf) :
    mergeEntry old incoming leaf = (old.leaves leaf || incoming.leaves leaf) := by
  simp [mergeEntry, empty_exclusions_cannot_replace_existing old incoming oldE incomingE]

theorem admitted_selector_persists_in_merge (old incoming : TreeEntry)
    (oldE : old.exclusion = []) (incomingE : incoming.exclusion = [])
    (leaf : GroupLeaf) (present : old.leaves leaf = true) :
    mergeEntry old incoming leaf = true := by
  rw [run1_merge_is_union old incoming oldE incomingE]
  simp [present]

def addLeaves (current accepted : LeafSet) : LeafSet := fun leaf => current leaf || accepted leaf

def storeAfter : List GroupLeaf → LeafSet
  | [] => fun _ => false
  | leaf :: history => addLeaves (storeAfter history) (fun key => key == leaf)

theorem exact_leaf_survives_finite_later_admissions (history : List GroupLeaf) (leaf : GroupLeaf) :
    storeAfter history leaf = true ↔ leaf ∈ history := by
  induction history with
  | nil => simp [storeAfter]
  | cons new history ih =>
    rw [List.mem_cons]
    change (storeAfter history leaf || (leaf == new)) = true ↔ leaf = new ∨ leaf ∈ history
    simp only [Bool.or_eq_true, beq_iff_eq, ih]
    exact or_comm

/-- The selected store does not erase an accepted scalar. Consequently the
    old callback's exact selector still has its immutable scalar witness.
    No semantic replacement-coverage hypothesis is needed in run-1. -/
def liveSelectorWitness (history : List GroupLeaf) (leaf : GroupLeaf) (present : leaf ∈ history) :
    {live : GroupLeaf // live = leaf ∧ storeAfter history live = true} :=
  ⟨leaf, rfl, (exact_leaf_survives_finite_later_admissions history leaf).mpr present⟩

def immediateSeeds (history : List GroupLeaf) : List Atom := history.map poolProjection

def stableCallbackSeeds (history : List GroupLeaf) : List Atom :=
  (history.filter fun leaf => storeAfter history leaf).map poolProjection

theorem stable_selector_filter_keeps_admitted (history : List GroupLeaf) :
    history.filter (fun leaf => storeAfter history leaf) = history := by
  apply List.filter_eq_self.mpr
  intro leaf present
  exact (exact_leaf_survives_finite_later_admissions history leaf).mpr present

theorem persistent_selector_seed_membership (history : List GroupLeaf) (atom : Atom) :
    atom ∈ immediateSeeds history ↔ atom ∈ stableCallbackSeeds history := by
  simp [immediateSeeds, stableCallbackSeeds, stable_selector_filter_keeps_admitted]

/-- Arbitrary unchanged positive closure rules can contain more than one
    prerequisite. This preserves conditional supplies without inventing marks.
    Rule effects may retain summaries or create sink seeds at their owner. -/
structure ClosureRule where
  inputs : List Atom
  outputs : List Atom
  effects : List Effect

inductive Derived (seeds : List Atom) (rules : List ClosureRule) : Atom → Type where
  | seed (atom : Atom) (present : atom ∈ seeds) : Derived seeds rules atom
  | fire (rule : ClosureRule) (present : rule ∈ rules)
      (supports : (a : Atom) → a ∈ rule.inputs → Derived seeds rules a)
      (atom : Atom) (emitted : atom ∈ rule.outputs) : Derived seeds rules atom

def replayDerived {old new : List Atom} {rules : List ClosureRule}
    (translate : (a : Atom) → a ∈ old → a ∈ new) {atom : Atom}
    (derived : Derived old rules atom) : Derived new rules atom :=
  match derived with
  | .seed atom present => .seed atom (translate atom present)
  | .fire rule present supports atom emitted =>
      .fire rule present (fun a required => replayDerived translate (supports a required)) atom emitted

def immediateToStable (history : List GroupLeaf) {rules : List ClosureRule} {atom : Atom}
    (derived : Derived (immediateSeeds history) rules atom) :
    Derived (stableCallbackSeeds history) rules atom :=
  replayDerived (fun a h => (persistent_selector_seed_membership history a).mp h) derived

def stableToImmediate (history : List GroupLeaf) {rules : List ClosureRule} {atom : Atom}
    (derived : Derived (stableCallbackSeeds history) rules atom) :
    Derived (immediateSeeds history) rules atom :=
  replayDerived (fun a h => (persistent_selector_seed_membership history a).mpr h) derived

theorem complete_positive_supply_closure_equal (history : List GroupLeaf)
    (rules : List ClosureRule) (atom : Atom) :
    Nonempty (Derived (immediateSeeds history) rules atom) ↔
      Nonempty (Derived (stableCallbackSeeds history) rules atom) :=
  ⟨fun ⟨d⟩ => ⟨immediateToStable history d⟩, fun ⟨d⟩ => ⟨stableToImmediate history d⟩⟩

structure Observed (seeds : List Atom) (rules : List ClosureRule) (effect : Effect) where
  rule : ClosureRule
  present : rule ∈ rules
  supports : (a : Atom) → a ∈ rule.inputs → Derived seeds rules a
  emitted : effect ∈ rule.effects

def replayObserved {old new : List Atom} {rules : List ClosureRule}
    (translate : (a : Atom) → a ∈ old → a ∈ new) {effect : Effect}
    (observed : Observed old rules effect) : Observed new rules effect :=
  ⟨observed.rule, observed.present,
    fun a required => replayDerived translate (observed.supports a required), observed.emitted⟩

theorem complete_semantic_effect_closure_equal (history : List GroupLeaf)
    (rules : List ClosureRule) (effect : Effect) :
    Nonempty (Observed (immediateSeeds history) rules effect) ↔
      Nonempty (Observed (stableCallbackSeeds history) rules effect) :=
  ⟨fun ⟨d⟩ => ⟨replayObserved (fun a h => (persistent_selector_seed_membership history a).mp h) d⟩,
   fun ⟨d⟩ => ⟨replayObserved (fun a h => (persistent_selector_seed_membership history a).mpr h) d⟩⟩

structure Work where
  waitingRows : Nat
  interests : Nat
  replies : Nat
  scalarApplications : Nat
  suffixes : Nat
  deriving DecidableEq, Repr

/-- Reference work for equal aligned semantic jobs. jobs is neither a measured
    offer count nor a count of old distinct waiting CallKeys. It does not bound
    actual actor applications/suffixes across batching schedules. -/
def oldWork (jobs : Nat) : Work := ⟨jobs, jobs, jobs, jobs, jobs⟩
def newWork (jobs : Nat) : Work := ⟨0, 0, 0, jobs, jobs⟩

theorem protocol_work_zero (jobs : Nat) :
    (newWork jobs).waitingRows + (newWork jobs).interests + (newWork jobs).replies = 0 := rfl

theorem aligned_reference_jobs_same_scalar_work (jobs : Nat) :
    (newWork jobs).scalarApplications = (oldWork jobs).scalarApplications ∧
    (newWork jobs).suffixes = (oldWork jobs).suffixes := ⟨rfl, rfl⟩

namespace Controls

def atom : Atom := ⟨.field 10, 3⟩
def world : Run1StaticPremiseAvailability.Network := ⟨[atom], []⟩
def processed : Run1StaticPremiseAvailability.Processed world atom := ⟨.source atom (by decide) 1, 0⟩
def result : Run1StaticPremiseAvailability.Output := ⟨5, 1, false⟩
def origin : Run1StaticPremiseAvailability.Edge world 2 (.staticGroup atom) result := .staticReply 2 atom processed result
def returned : Pattern := ⟨.returned, [], .exact, .group 3, []⟩
def zero : Pattern := ⟨.zero, [], .exact, .group 0, []⟩
def staticValue : Pattern := ⟨.static, slotPath atom.slot, .any, .group 3, []⟩
/-- This getter reads C.f, checks ContainsMark(value,G3), and returns value.
    The check is needed to obtain its GROUP premise. A markless getter is not
    assumed to publish this concrete summary on its own. -/
def getter : Row := ⟨2, initialOf atom, returned, 1⟩
def setter : Row := ⟨1, zero, staticValue, 1⟩
def mainWriter : Row := ⟨0, zero, staticValue, 1⟩
def sink : SinkSeed := ⟨3, 17, 7, ⟨.local 4, [], .any, .group 3, []⟩, some 8⟩
def context : CallContext := ⟨3, 16, 4, 1, true, false⟩
def job : CallJob world := ⟨atom, context, getter, rfl, result, origin⟩
def computed : ComputedJob world :=
  ⟨atom, ⟨2, initialOf atom, staticValue, 1⟩, rfl, result, origin⟩
def reducer : ScalarReducer :=
  ⟨fun _ context row => [.retain { row with method := context.owner }, .sink sink],
   fun _ _ => [.supply atom]⟩

def sinkOnlyReducer : ScalarReducer :=
  ⟨fun _ _ _ => [.sink sink], fun _ _ => [.supply atom]⟩

def outerContext : CallContext := ⟨4, 20, 4, 1, false, false⟩
def wrappedGetter : Row := { getter with method := 3 }
def wrappedJob : CallJob world :=
  ⟨atom, outerContext, wrappedGetter, rfl, result, .staticPromote origin 3⟩

/-- Returning getter wrappers keep their distinct method-owned summary and
    demand pairs. The separate deadLocalProgram below has no such return. -/
def program : List (Command world) :=
  [.common [.retain setter, .retain mainWriter, .retain getter], .call job, .call wrappedJob,
    .computed computed]

def deadLocalProgram : List (Command world) :=
  [.common [.retain setter, .retain mainWriter, .retain getter], .call job]

def oldResult := oldProgram reducer program
def newResult := newProgram reducer program

def exactLeaf : GroupLeaf := ⟨.field 10, 3, .exact⟩
def anyLeaf : GroupLeaf := ⟨.field 10, 3, .any⟩
def otherLeaf : GroupLeaf := ⟨.field 11, 4, .exact⟩
def admissionHistory := [otherLeaf, anyLeaf, exactLeaf]

def deadLocalResult := newProgram sinkOnlyReducer deadLocalProgram

def explicitGetterGuidance : Run2Guidance (handoff oldResult) :=
  .explicit (demandOf getter) (by decide)

def preservedGetterGuidance : Run2Guidance (handoff newResult) :=
  preserveRun2Guidance reducer program explicitGetterGuidance

def controls : List Bool :=
  [decide (poolAnswer atom = directInput atom),
   decide ((poolAnswer atom).pattern.tail = .any),
   decide ((directInput ⟨.exactClass 2, 3⟩).pattern.tail = .exact),
   decide (oldResult = newResult),
   decide (handoff oldResult = handoff newResult),
   decide (Observation.raw setter ∈ handoff newResult),
   decide (Observation.raw mainWriter ∈ handoff newResult),
   decide (Observation.demand (demandOf getter) ∈ handoff newResult),
   decide (Observation.sinkSeed sink ∈ handoff newResult),
   decide (Observation.implicitStaticGroups ∈ handoff newResult),
   decide ((demandOf getter).method = 2),
   decide ((demandOf getter).entry.mark = .group 3),
   decide ((demandOf getter).exit.mark = .group 3),
   decide ((demandOf getter).exit = initialOf atom),
   decide ((demandOf setter).entry.tail = .any),
   decide ((backwardEntry ⟨.local 2, [4], .must, .group 3, [5]⟩).tail = .any),
   decide ((backwardEntry ⟨.local 2, [4], .must, .group 3, [5]⟩).exclusion = []),
   decide ((backwardExit ⟨.local 2, [], .star, .flow [3, 4], []⟩).mark = .flow []),
   decide (implicitStaticPair ⟨.static, [0, 10], .exact, .full 3 99, []⟩ =
     some (⟨.static, [], .any, .group 3, []⟩, ⟨.static, [], .star, .group 3, []⟩)),
   decide (semanticEffects [.wait atom, .interest atom, .reply atom] = []),
   decide (storeAfter admissionHistory exactLeaf = true),
   decide (storeAfter admissionHistory anyLeaf = true),
   decide (atom ∈ stableCallbackSeeds admissionHistory),
   decide (poolProjection (liveSelectorWitness admissionHistory exactLeaf (by decide)).val = atom),
   decide ((newWork 8).interests = 0),
   decide ((oldWork 8).interests = 8),
   decide ((newWork 8).scalarApplications = 8),
   decide ((newWork 8).suffixes = 8),
   decide (Observation.demand (demandOf wrappedGetter) ∈ handoff newResult),
   decide (Observation.demand (demandOf { getter with method := 4 }) ∈ handoff newResult),
   decide (Observation.sinkSeed sink ∈ handoff deadLocalResult),
   decide (Observation.raw wrappedGetter ∉ handoff deadLocalResult),
   decide (Observation.demand (demandOf wrappedGetter) ∉ handoff deadLocalResult),
   decide (Observation.implicitStaticGroups ∈ handoff deadLocalResult),
   decide (oldProgram sinkOnlyReducer deadLocalProgram = deadLocalResult),
   decide (Observation.raw computed.row ∈ handoff (newComputedBody reducer computed)),
   decide (Observation.demand (demandOf computed.row) ∈ handoff (newComputedBody reducer computed)),
   decide (computed.row.original ≠ zero),
   decide ((poolAnswer atom).layer = demandLayer),
   decide ((directInput atom).layer = demandLayer),
   decide (Observation.sinkSeed sink ∈ handoff (deadLocalResult ++ deadLocalResult)),
   decide (handoff (deadLocalResult ++ deadLocalResult) ≠ handoff deadLocalResult)]

theorem controls_pass : controls.all id = true := by decide

#eval do
  if !(controls.all id) then throw (IO.userError "Static GROUP protocol removal controls failed")
  IO.println s!"Static GROUP protocol removal controls passed: {controls.length}."

end Controls
end ApSpec.Run1StaticGroupProtocolRemoval
