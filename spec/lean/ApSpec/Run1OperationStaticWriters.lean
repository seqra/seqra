/-
Run-1 operation-only static writers. This is a finite, constructive protocol
model, not a model of the complete AP kernel or Kotlin engine.

Each local relation is an immutable, already bounded scalar FLOW transfer.
Its finite accepted-group domain stands for the result of complete AP
matching, including the selectors retained below. A promoted summary has no
authority to register another write point. It still belongs to its method's
summary store. Computed ZERO and complete all-static group supplies are a
separate channel; an ordinary hypothetical Base/group premise cannot supply
the pool by itself.

The caller-job simulation constructs a request/input/summary trace from a
finite ordinary Base-binding route to a local operation. It does not assume
equality of old and new answer sets. A runtime adapter must construct that
route from actual call bindings, retain complete AP images and all demand
pairs, and schedule both arrival orders. No claim here licenses a new AP,
field, layer, source, cleaner, or premise-matching policy.

FLOW inputs are nonzero Base/static slots. ZERO occurs only as the origin
of an already computed TAINT supply. Canonical field keys and complete AP
images are abstracted by scalar slots and finite accepted-group domains.
-/
import Std

namespace ApSpec.Run1OperationStaticWriters

set_option autoImplicit false

inductive Slot where
  | base (owner parameter : Nat)
  | field (name : Nat)
  deriving DecidableEq, Repr

structure Atom where
  slot : Slot
  group : Nat
  deriving DecidableEq, Repr

structure Arc where
  input : Atom
  output : Atom
  deriving DecidableEq, Repr

inductive Tail where
  | exact | star | any | must
  deriving DecidableEq, Repr

/-- These selectors remain part of the immutable relation. They are not erased
    by the alias-admission predicate specified in a different module. -/
structure Selectors where
  inputPath : List Nat := []
  outputPath : List Nat := []
  inputTail : Tail := .star
  outputTail : Tail := .star
  fieldExclusion : List Nat := []
  markExclusion : List Nat := []
  layer : Nat := 1
  mode : Nat := 1
  fieldLimit : Nat := 1
  deriving DecidableEq, Repr

structure LocalRelation where
  owner : Nat
  /-- Proof provenance of eligible production; not a writer-key dimension. -/
  operation : Nat
  input : Slot
  outputField : Nat
  acceptedGroups : List Nat
  selectors : Selectors := {}
  deriving DecidableEq, Repr

/-- An actor-relative complete immutable relation key, with the actor owner
    made explicit for this aggregate model. The accepted-group domain abstracts
    the AP selector's finite interpretation; it is not a new runtime catalog.
    In particular, the local operation witness is absent from this key. -/
structure WriterKey where
  owner : Nat
  input : Slot
  outputField : Nat
  acceptedGroups : List Nat
  selectors : Selectors
  deriving DecidableEq, Repr

def writerKey (r : LocalRelation) : WriterKey :=
  ⟨r.owner, r.input, r.outputField, r.acceptedGroups, r.selectors⟩

theorem operation_witness_not_key (r : LocalRelation) (operation : Nat) :
    writerKey { r with operation := operation } = writerKey r := rfl

def enabled (r : LocalRelation) (g : Nat) : Prop :=
  g ∈ r.acceptedGroups ∧ g ∉ r.selectors.markExclusion

instance (r : LocalRelation) (g : Nat) : Decidable (enabled r g) :=
  inferInstanceAs (Decidable (g ∈ r.acceptedGroups ∧ g ∉ r.selectors.markExclusion))

def localArc (r : LocalRelation) (g : Nat) : Arc :=
  ⟨⟨r.input, g⟩, ⟨.field r.outputField, g⟩⟩

structure Binding where
  caller : Slot
  calleeOwner : Nat
  parameter : Nat
  acceptedGroups : List Nat
  deriving DecidableEq, Repr

def destination (b : Binding) : Slot := .base b.calleeOwner b.parameter

structure Network where
  seeds : List Atom
  bindings : List Binding
  operations : List LocalRelation
  deriving DecidableEq, Repr

/-- Only an actual source, ordinary input replay, or a local operation can
    derive a group. Requests and promoted summaries are not constructors. -/
inductive Derived (n : Network) : Atom → Type where
  | source (a : Atom) (present : a ∈ n.seeds) : Derived n a
  | binding (b : Binding) (g : Nat) (present : b ∈ n.bindings)
      (matched : g ∈ b.acceptedGroups) (input : Derived n ⟨b.caller, g⟩) :
      Derived n ⟨destination b, g⟩
  | operation (r : LocalRelation) (g : Nat) (present : r ∈ n.operations)
      (matched : enabled r g) (input : Derived n ⟨r.input, g⟩) :
      Derived n ⟨.field r.outputField, g⟩

def sourceWitness (n : Network) {a : Atom} (d : Derived n a) :
    {s : Atom // s ∈ n.seeds} :=
  match d with
  | .source atom present => ⟨atom, present⟩
  | .binding _ _ _ _ input => sourceWitness n input
  | .operation _ _ _ _ input => sourceWitness n input

theorem no_source_free_cycles (bindings : List Binding) (operations : List LocalRelation)
    {a : Atom} (d : Derived ⟨[], bindings, operations⟩ a) : False := by
  have h := (sourceWitness _ d).property
  simp at h

/-- A route contains actual registered Base-input bindings and complete
    per-hop matching evidence. It is not call-graph reachability. -/
inductive BaseRoute (n : Network) (g : Nat) : Slot → Slot → Type where
  | done (s : Slot) : BaseRoute n g s s
  | bind (b : Binding) (present : b ∈ n.bindings)
      (matched : g ∈ b.acceptedGroups) {target : Slot}
      (rest : BaseRoute n g (destination b) target) :
      BaseRoute n g b.caller target

def routeSlots {n : Network} {g : Nat} {start target : Slot}
    (route : BaseRoute n g start target) : List Slot :=
  match route with
  | .done s => [s]
  | .bind b _ _ rest => b.caller :: routeSlots rest

def replayInputs {n : Network} {g : Nat} {start target : Slot}
    (route : BaseRoute n g start target) (input : Derived n ⟨start, g⟩) :
    Derived n ⟨target, g⟩ :=
  match route with
  | .done _ => input
  | .bind b present matched rest =>
      replayInputs rest (.binding b g present matched input)

def routeLength {n : Network} {g : Nat} {start target : Slot}
    (route : BaseRoute n g start target) : Nat :=
  match route with
  | .done _ => 0
  | .bind _ _ _ rest => routeLength rest + 1

theorem route_slots_length {n : Network} {g : Nat} {start target : Slot}
    (route : BaseRoute n g start target) :
    (routeSlots route).length = routeLength route + 1 := by
  induction route with
  | done => rfl
  | bind b present matched rest ih => simp [routeSlots, routeLength, ih, Nat.add_comm]

/-- A former caller FLOW writer is a shortcut whose provenance is a finite
    ordinary input route followed by one local operation. The route and exact
    bounded local relation must be supplied by the production adapter. -/
structure CallerWriter (n : Network) where
  callerInput : Slot
  group : Nat
  operationRelation : LocalRelation
  registered : operationRelation ∈ n.operations
  matched : enabled operationRelation group
  route : BaseRoute n group callerInput operationRelation.input

def callerOutput {n : Network} (w : CallerWriter n) : Atom :=
  ⟨.field w.operationRelation.outputField, w.group⟩

def replayCallerWriter {n : Network} (w : CallerWriter n)
    (input : Derived n ⟨w.callerInput, w.group⟩) : Derived n (callerOutput w) :=
  .operation w.operationRelation w.group w.registered w.matched (replayInputs w.route input)

/-- The old graph additionally admits caller shortcuts. The new graph has no
    such constructor. Erasure below expands each old job constructively. -/
inductive OldDerived (n : Network) : Atom → Type where
  | source (a : Atom) (present : a ∈ n.seeds) : OldDerived n a
  | binding (b : Binding) (g : Nat) (present : b ∈ n.bindings)
      (matched : g ∈ b.acceptedGroups) (input : OldDerived n ⟨b.caller, g⟩) :
      OldDerived n ⟨destination b, g⟩
  | operation (r : LocalRelation) (g : Nat) (present : r ∈ n.operations)
      (matched : enabled r g) (input : OldDerived n ⟨r.input, g⟩) :
      OldDerived n ⟨.field r.outputField, g⟩
  | caller (w : CallerWriter n) (input : OldDerived n ⟨w.callerInput, w.group⟩) :
      OldDerived n (callerOutput w)

def eliminateCallerWriters {n : Network} {a : Atom} (d : OldDerived n a) : Derived n a :=
  match d with
  | .source atom present => .source atom present
  | .binding b g present matched input =>
      .binding b g present matched (eliminateCallerWriters input)
  | .operation r g present matched input =>
      .operation r g present matched (eliminateCallerWriters input)
  | .caller w input => replayCallerWriter w (eliminateCallerWriters input)

def includeNew {n : Network} {a : Atom} (d : Derived n a) : OldDerived n a :=
  match d with
  | .source atom present => .source atom present
  | .binding b g present matched input => .binding b g present matched (includeNew input)
  | .operation r g present matched input => .operation r g present matched (includeNew input)

theorem finite_closure_equivalence (n : Network) (a : Atom) :
    Nonempty (OldDerived n a) ↔ Nonempty (Derived n a) :=
  ⟨fun ⟨d⟩ => ⟨eliminateCallerWriters d⟩, fun ⟨d⟩ => ⟨includeNew d⟩⟩

inductive SummaryOrigin where
  | zero
  | staticField (field : Nat)
  | base (owner parameter : Nat)
  deriving DecidableEq, Repr

/-- This is eligibility, not grounding. The constructors below additionally
    require actual derivations. No Base/group-only supply constructor exists. -/
def poolEligible : SummaryOrigin → Bool
  | .zero | .staticField _ => true
  | .base _ _ => false

structure StaticBinding (n : Network) where
  first : Nat × Nat
  rest : List (Nat × Nat)
  actual : (p : Nat × Nat) → p ∈ first :: rest → Derived n ⟨.field p.1, p.2⟩

inductive ComputedSupply (n : Network) : SummaryOrigin → Atom → Type where
  | zero {field group : Nat} (value : Derived n ⟨.field field, group⟩) :
      ComputedSupply n .zero ⟨.field field, group⟩
  | allStatic {field group : Nat} (binding : StaticBinding n)
      (value : Derived n ⟨.field field, group⟩) :
      ComputedSupply n (.staticField binding.first.1) ⟨.field field, group⟩

def supplyDerivation {n : Network} {origin : SummaryOrigin} {a : Atom}
    (s : ComputedSupply n origin a) : Derived n a :=
  match s with
  | .zero value => value
  | .allStatic _ value => value

theorem computed_supply_requires_source (n : Network) {origin : SummaryOrigin} {a : Atom}
    (s : ComputedSupply n origin a) : ∃ root, root ∈ n.seeds :=
  let witness := sourceWitness n (supplyDerivation s)
  ⟨witness.val, witness.property⟩

theorem hypothetical_base_cannot_supply {n : Network} {a : Atom} (owner parameter : Nat)
    (s : ComputedSupply n (.base owner parameter) a) : False := by
  cases s

/-- Endpoint provenance is separate from an intermediate hypothetical Base
    premise. The zero case requires the adapter's actual ZERO caller binding;
    the static case requires the actual supplied field group below. -/
inductive Endpoint : Slot → Type where
  | zero (input : Slot) : Endpoint input
  | staticField (field : Nat) : Endpoint (.field field)

def supplyAtEndpoint {n : Network} {start : Slot} {g field : Nat}
    (endpoint : Endpoint start) (input : Derived n ⟨start, g⟩)
    (result : Derived n ⟨.field field, g⟩) :
    (origin : SummaryOrigin) × ComputedSupply n origin ⟨.field field, g⟩ := by
  cases endpoint with
  | zero => exact ⟨.zero, .zero result⟩
  | staticField source =>
      exact ⟨.staticField source, .allStatic ⟨(source, g), [], fun p h => by
        simp only [List.mem_cons, List.not_mem_nil, or_false] at h
        subst p
        exact input⟩ result⟩

structure ReplayWitness (n : Network) (w : CallerWriter n) where
  requests : List Slot
  inputs : List Slot
  promotedGroupSummaries : List Slot
  request_order : requests = (routeSlots w.route).reverse
  input_order : inputs = routeSlots w.route
  summary_order : promotedGroupSummaries = (routeSlots w.route).reverse
  localInput : Derived n ⟨w.operationRelation.input, w.group⟩
  result : Derived n (callerOutput w)
  origin : SummaryOrigin
  supply : ComputedSupply n origin (callerOutput w)

def replayJob {n : Network} (w : CallerWriter n) (endpoint : Endpoint w.callerInput)
    (input : Derived n ⟨w.callerInput, w.group⟩) : ReplayWitness n w := by
  let delivered := replayInputs w.route input
  let result := replayCallerWriter w input
  let supply := supplyAtEndpoint endpoint input result
  exact ⟨(routeSlots w.route).reverse, routeSlots w.route,
    (routeSlots w.route).reverse, rfl, rfl, rfl, delivered, result, supply.1, supply.2⟩

theorem replay_has_finite_requests {n : Network} (w : CallerWriter n)
    (endpoint : Endpoint w.callerInput) (input : Derived n ⟨w.callerInput, w.group⟩) :
    (replayJob w endpoint input).requests.length = routeLength w.route + 1 := by
  simp [replayJob, route_slots_length]

def insertUnique {α : Type} [DecidableEq α] (x : α) (xs : List α) : List α :=
  if x ∈ xs then xs else x :: xs

theorem inserted {α : Type} [DecidableEq α] (x : α) (xs : List α) :
    x ∈ insertUnique x xs := by
  by_cases h : x ∈ xs
  · simp [insertUnique, h]
  · simp [insertUnique, h]

theorem insert_idempotent {α : Type} [DecidableEq α] (x : α) (xs : List α) :
    insertUnique x (insertUnique x xs) = insertUnique x xs := by
  exact if_pos (inserted x xs)

structure SummaryRow where
  owner : Nat
  relation : LocalRelation
  deriving DecidableEq, Repr

/-- The summary's method owner is retained. The operation witness is not
    stored in either admission key. -/
def summaryKey (row : SummaryRow) : WriterKey :=
  { writerKey row.relation with owner := row.owner }

structure Stores where
  summaries : List WriterKey := []
  writers : List WriterKey := []
  deriving DecidableEq, Repr

def promote (s : Stores) (row : SummaryRow) : Stores :=
  { s with summaries := insertUnique (summaryKey row) s.summaries }

/-- Writer admission does not depend on the summary's fresh-delta test. -/
def localResult (s : Stores) (row : SummaryRow) : Stores :=
  ⟨insertUnique (summaryKey row) s.summaries, insertUnique (writerKey row.relation) s.writers⟩

theorem promoted_no_writer (s : Stores) (row : SummaryRow) :
    (promote s row).writers = s.writers := rfl

theorem promoted_summary_retained (s : Stores) (row : SummaryRow) :
    summaryKey row ∈ (promote s row).summaries := inserted _ _

theorem local_writer_even_after_promotion (s : Stores) (row : SummaryRow) :
    writerKey row.relation ∈ (localResult (promote s row) row).writers := inserted _ _

theorem local_admission_independent_of_freshness (s : Stores) (row : SummaryRow)
    (_alreadyStored : summaryKey row ∈ s.summaries) :
    writerKey row.relation ∈ (localResult s row).writers := inserted _ _

theorem equivalent_local_sites_one_writer (s : Stores) (owner : Nat)
    (r : LocalRelation) (operation : Nat) :
    (localResult (localResult s ⟨owner, r⟩)
      ⟨owner, { r with operation := operation }⟩).writers =
      (localResult s ⟨owner, r⟩).writers := by
  simp only [localResult, operation_witness_not_key, insert_idempotent]

theorem local_idempotent (s : Stores) (row : SummaryRow) :
    localResult (localResult s row) row = localResult s row := by
  simp [localResult, insert_idempotent]

theorem promote_idempotent (s : Stores) (row : SummaryRow) :
    promote (promote s row) row = promote s row := by
  simp [promote, insert_idempotent]

theorem local_promotion_both_orders (s : Stores) (row : SummaryRow) :
    localResult (promote s row) row = promote (localResult s row) row := by
  simp [localResult, promote, insert_idempotent]

/-- A semantic merge can change storage selectors. A local writer remains an
    immutable bounded value, with its original key and transfer exclusions. -/
def RelationCovered (r : LocalRelation) (summaryArcs : List Arc) : Prop :=
  ∀ g, enabled r g → localArc r g ∈ summaryArcs

theorem covered_after_merge (r : LocalRelation) (old added : List Arc)
    (valid : RelationCovered r old) : RelationCovered r (old ++ added) := by
  intro g accepted
  exact List.mem_append_left _ (valid g accepted)

theorem covered_after_semantic_replacement (r : LocalRelation) (old replacement : List Arc)
    (valid : RelationCovered r old)
    (covers : ∀ a, a ∈ old → a ∈ replacement) : RelationCovered r replacement := by
  intro g accepted
  exact covers _ (valid g accepted)

theorem excluded_group_cannot_transfer (r : LocalRelation) (g : Nat)
    (excluded : g ∈ r.selectors.markExclusion) : ¬ enabled r g := by
  intro matched
  exact matched.2 excluded

/-- A requested/written pair has four states. Arrival is grounded separately
    by ComputedSupply; a request cannot set the written bit. -/
structure PairState where
  requested : Bool := false
  arrived : Bool := false
  deriving DecidableEq, Repr

def request (s : PairState) : PairState := { s with requested := true }
def arrive (s : PairState) : PairState := { s with arrived := true }
def ready (s : PairState) : Bool := s.requested && s.arrived

theorem request_not_supply (s : PairState) : (request s).arrived = s.arrived := rfl
theorem arrival_both_orders (s : PairState) : request (arrive s) = arrive (request s) := rfl
theorem request_once (s : PairState) : request (request s) = request s := rfl
theorem arrive_once (s : PairState) : arrive (arrive s) = arrive s := rfl
theorem unrequested_arrival_not_ready (s : PairState) (h : s.requested = false) :
    ready (arrive s) = false := by simp [ready, arrive, h]

/- Executable controls. No runtime trace or historical performance result is
   used as a premise. All networks, slots, groups and routes are finite. -/
namespace Controls

def setter : LocalRelation := ⟨1, 7, .base 1 0, 10, [3], {}⟩
def row : SummaryRow := ⟨1, setter⟩

example : (localResult (promote {} row) row).writers = [writerKey setter] := by decide
example : (promote (localResult {} row) row).writers = [writerKey setter] := by decide
example : (promote (promote {} row) row).writers = [] := by decide
example : (localResult (localResult {} row)
    ⟨1, { setter with operation := 99 }⟩).writers = [writerKey setter] := by decide
example : (localResult (localResult {} row)
    ⟨1, { setter with operation := 99 }⟩).summaries.length = 1 := by decide

/-- Three method summaries, one lexical writer registration. -/
def wrapperStores : Stores :=
  promote (promote (localResult {} row) ⟨2, setter⟩) ⟨3, setter⟩

example : wrapperStores.summaries.length = 3 ∧ wrapperStores.writers.length = 1 := by decide

def sourceBinding : Binding := ⟨.base 0 0, 1, 0, [3]⟩
def sourceNetwork : Network := ⟨[⟨.base 0 0, 3⟩], [sourceBinding], [setter]⟩
def sourceRoute : BaseRoute sourceNetwork 3 (.base 0 0) setter.input :=
  .bind sourceBinding (by decide) (by decide) (.done _)
def sourceJob : CallerWriter sourceNetwork :=
  ⟨.base 0 0, 3, setter, by decide, by decide, sourceRoute⟩
def sourceInput : Derived sourceNetwork ⟨.base 0 0, 3⟩ := .source _ (by decide)
def sourceReplay := replayJob sourceJob (.zero _) sourceInput

example : sourceReplay.requests = [.base 1 0, .base 0 0] := by decide
example : sourceReplay.inputs = [.base 0 0, .base 1 0] := by decide
example : sourceReplay.promotedGroupSummaries = [.base 1 0, .base 0 0] := by decide
example : poolEligible .zero = true ∧ poolEligible (.base 1 0) = false := by decide

def staticBinding : Binding := ⟨.field 20, 1, 0, [3]⟩
def staticNetwork : Network := ⟨[⟨.field 20, 3⟩], [staticBinding], [setter]⟩
def staticRoute : BaseRoute staticNetwork 3 (.field 20) setter.input :=
  .bind staticBinding (by decide) (by decide) (.done _)
def staticJob : CallerWriter staticNetwork :=
  ⟨.field 20, 3, setter, by decide, by decide, staticRoute⟩
def staticInput : Derived staticNetwork ⟨.field 20, 3⟩ := .source _ (by decide)
def staticReplay := replayJob staticJob (.staticField 20) staticInput

example : staticReplay.requests = [.base 1 0, .field 20] := by decide
example : staticReplay.inputs = [.field 20, .base 1 0] := by decide
example : poolEligible (.staticField 20) = true := by decide

def wrappedBinding : Binding := ⟨.base 0 0, 2, 0, [3]⟩
def innerBinding : Binding := ⟨.base 2 0, 1, 0, [3]⟩
def wrappedNetwork : Network :=
  ⟨[⟨.base 0 0, 3⟩], [wrappedBinding, innerBinding], [setter]⟩
def wrappedRoute : BaseRoute wrappedNetwork 3 (.base 0 0) setter.input :=
  .bind wrappedBinding (by decide) (by decide)
    (.bind innerBinding (by decide) (by decide) (.done _))
def wrappedJob : CallerWriter wrappedNetwork :=
  ⟨.base 0 0, 3, setter, by decide, by decide, wrappedRoute⟩
def wrappedInput : OldDerived wrappedNetwork ⟨.base 0 0, 3⟩ := .source _ (by decide)
def oldWrappedResult : OldDerived wrappedNetwork (callerOutput wrappedJob) :=
  .caller wrappedJob wrappedInput
def newWrappedResult : Derived wrappedNetwork (callerOutput wrappedJob) :=
  eliminateCallerWriters oldWrappedResult

example : routeSlots wrappedRoute = [.base 0 0, .base 2 0, .base 1 0] := by decide
example : (sourceWitness _ newWrappedResult).val = ⟨.base 0 0, 3⟩ := rfl

def staticCopy : LocalRelation := { setter with input := .field 20 }
def copyNetwork : Network := ⟨[⟨.field 20, 3⟩], [], [staticCopy]⟩
def copyJob : CallerWriter copyNetwork :=
  ⟨.field 20, 3, staticCopy, by decide, by decide, .done _⟩
def copyReplay := replayJob copyJob (.staticField 20) (.source _ (by decide))

example : copyReplay.requests = [.field 20] := by decide
example : copyReplay.promotedGroupSummaries = [.field 20] := by decide

def narrowSummary : List Arc := [localArc setter 3]
def mergedSummary : List Arc := narrowSummary ++ [⟨⟨.base 1 0, 4⟩, ⟨.field 10, 4⟩⟩]

example : RelationCovered setter narrowSummary := by
  intro g h
  have hg : g = 3 := by simpa [enabled, setter] using h.1
  subst g
  decide

example : RelationCovered setter mergedSummary :=
  covered_after_merge _ _ _ (by
    intro g h
    have hg : g = 3 := by simpa [enabled, setter] using h.1
    subst g
    decide)

example : (request (arrive {})) = (arrive (request {})) := by decide
example : ready (request {}) = false ∧ ready (arrive {}) = false := by decide
example : ready (request (arrive {})) = true := by decide
example : ¬ enabled { setter with selectors := { markExclusion := [3] } } 3 := by decide
example : writerKey setter ≠
    writerKey { setter with selectors := { markExclusion := [3] } } := by decide

end Controls
end ApSpec.Run1OperationStaticWriters
