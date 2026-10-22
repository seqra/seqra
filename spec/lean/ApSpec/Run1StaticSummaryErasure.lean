/-
Run-1 static/static summary erasure: a constructive operational model.

Local FLOW relations and computed GROUP outputs are producers. Static summary
storage is not their owner in this model. Old summary applications can relay a
local relation through first argument bindings and arbitrarily many wrappers.
The new system has no static-summary-application rule. It replays each finite
old application through its retained local producer and ordinary input bindings.

The program inventory contains genuine executed bounded local operations,
admitted source/GEN events and the existing ordinary rule kernel. It does not
contain an assumed final pool or a list of all old static summaries. The source
adapter must preserve that genuine inventory when storage is removed. In
particular, currentValues must not borrow an erased raw summary, and delayed
conditional producers must own their immutable complete scalar relation.

Complete AP geometry, layer/mx selectors, full marks, operation stages and
compiled GEN eligibility are opaque unchanged kernel data. This model does not
prove their Kotlin adapters or the later-run query-completion theorem. No new
runtime support label, source-grounding condition or field rule is introduced.
-/
import Std

namespace ApSpec.Run1StaticSummaryErasure

set_option autoImplicit false

inductive StaticSlot where
  | field (classId fieldId : Nat)
  | exactClass (classId : Nat)
  deriving DecidableEq, Repr

inductive InputSite where
  | static (slot : StaticSlot)
  | local (owner port : Nat)
  deriving DecidableEq, Repr

def InputSite.isStatic : InputSite → Bool
  | .static _ => true
  | .local _ _ => false

inductive Tail where
  | exact | star | any | must
  deriving DecidableEq, Repr

structure Atom where
  slot : StaticSlot
  group : Nat
  /-- Opaque unchanged scalar geometry/mark/header data. -/
  payload : Nat
  tail : Tail
  path : List Nat
  deriving DecidableEq, Repr

inductive OrdinaryObservation where
  | nonstatic (owner original output group layer : Nat)
  | sinkSeed (owner statement rule group occurrence : Nat)
  | sourceHit (owner statement action : Nat)
  deriving DecidableEq, Repr

inductive Event where
  | input (site : InputSite) (group : Nat)
  | pool (atom : Atom)
  | ordinary (observation : OrdinaryObservation)
  deriving DecidableEq, Repr

structure Writer where
  owner : Nat
  operation : Nat
  input : InputSite
  target : StaticSlot
  /-- Complete immutable transfer data, not a transient merged-store selector. -/
  descriptor : Nat
  deriving DecidableEq, Repr

structure WriterKey where
  owner : Nat
  input : InputSite
  target : StaticSlot
  descriptor : Nat
  deriving DecidableEq, Repr

def writerKey (w : Writer) : WriterKey :=
  ⟨w.owner, w.input, w.target, w.descriptor⟩

theorem operation_witness_is_not_a_storage_dimension (w : Writer) (site : Nat) :
    writerKey { w with operation := site } = writerKey w := rfl

structure Binding where
  caller : Nat
  statement : Nat
  stage : Nat
  callee : Nat
  port : Nat
  actual : InputSite
  deriving DecidableEq, Repr

def Binding.formal (b : Binding) : InputSite := .local b.callee b.port

/-- A current admitted source/GEN action or actual entry/control event.
    These are actual local roots, not the final static pool as an assumption. -/
structure RootOperation where
  owner : Nat
  operation : Nat
  event : Event
  deriving DecidableEq, Repr

/-- This unchanged rule can require a complete positive binding. A zero-arity
    rule is an existing compiled GEN/source root, not a new grounding policy. -/
structure LocalRule where
  owner : Nat
  stage : Nat
  premises : List Event
  output : Event
  deriving DecidableEq, Repr

/-- A local scalar AP adapter, not equality of completed analyzer answers.
    Its source correspondence must include geometry/mark correlation and every
    old canonical application at first binding and static relay. -/
structure SupplyNormalizer where
  apply : Writer → Nat → Atom → Atom
  idempotent : ∀ w group atom, apply w group (apply w group atom) = apply w group atom

structure Program where
  roots : List RootOperation
  writers : List Writer
  bindings : List Binding
  localRules : List LocalRule
  /-- The same deterministic scalar reducer is used by both systems. -/
  applyWriter : Writer → Nat → List Atom
  normalizer : SupplyNormalizer

/-- Construct old summaries from real local producers and explicit call bindings.
    No constructor admits an arbitrary precomputed old static summary. -/
inductive OldSummary (p : Program) : Nat → InputSite → Writer → Type where
  | local (w : Writer) (present : w ∈ p.writers) :
      OldSummary p w.owner w.input w
  | firstBinding (b : Binding) (present : b ∈ p.bindings) {w : Writer}
      (callee : OldSummary p b.callee b.formal w) :
      OldSummary p b.caller b.actual w
  | staticRelay {owner : Nat} {slot : StaticSlot} {w : Writer}
      (callee : OldSummary p owner (.static slot) w)
      (caller statement stage : Nat) : OldSummary p caller (.static slot) w


/-- Raw old summary output after each actual binding/relay AP normalization.
    Repetition is explicit: raw identity is not assumed. -/
def oldRowOutputs {p : Program} {owner : Nat} {site : InputSite} {w : Writer}
    (summary : OldSummary p owner site w) (group : Nat) : List Atom :=
  match summary with
  | .local _ _ => (p.applyWriter w group).map (p.normalizer.apply w group)
  | .firstBinding _ _ callee => (oldRowOutputs callee group).map (p.normalizer.apply w group)
  | .staticRelay callee _ _ _ => (oldRowOutputs callee group).map (p.normalizer.apply w group)

/-- Recover an executable local-output preimage. The only algebraic premise is
    the scalar normalizer's idempotence; old summary rows are not hypotheses. -/
theorem localOutputExists {p : Program} {owner : Nat} {site : InputSite} {w : Writer}
    (summary : OldSummary p owner site w) (group : Nat) (out : Atom)
    (present : out ∈ oldRowOutputs summary group) :
    ∃ raw ∈ p.applyWriter w group, p.normalizer.apply w group raw = out := by
  induction summary generalizing out with
  | «local» writer writerPresent =>
      simpa only [oldRowOutputs, List.mem_map] using present
  | firstBinding b bindingPresent callee ih =>
      simp only [oldRowOutputs, List.mem_map] at present
      obtain ⟨prior, priorPresent, equality⟩ := present
      obtain ⟨raw, rawPresent, normalized⟩ := ih prior priorPresent
      refine ⟨raw, rawPresent, ?_⟩
      calc
        p.normalizer.apply _ _ raw = p.normalizer.apply _ _ (p.normalizer.apply _ _ raw) :=
          (p.normalizer.idempotent _ _ raw).symm
        _ = p.normalizer.apply _ _ prior := congrArg (p.normalizer.apply _ _) normalized
        _ = out := equality
  | staticRelay callee caller statement stage ih =>
      simp only [oldRowOutputs, List.mem_map] at present
      obtain ⟨prior, priorPresent, equality⟩ := present
      obtain ⟨raw, rawPresent, normalized⟩ := ih prior priorPresent
      refine ⟨raw, rawPresent, ?_⟩
      calc
        p.normalizer.apply _ _ raw = p.normalizer.apply _ _ (p.normalizer.apply _ _ raw) :=
          (p.normalizer.idempotent _ _ raw).symm
        _ = p.normalizer.apply _ _ prior := congrArg (p.normalizer.apply _ _) normalized
        _ = out := equality

/-- Search the finite local output list. No witness is extracted computationally
    from a proposition, and no choice or noncomputable search is used. -/
def localOutputPreimage {p : Program} {owner : Nat} {site : InputSite} {w : Writer}
    (summary : OldSummary p owner site w) (group : Nat) (out : Atom)
    (present : out ∈ oldRowOutputs summary group) :
    {raw : Atom // raw ∈ p.applyWriter w group ∧ p.normalizer.apply w group raw = out} :=
  match h : (p.applyWriter w group).find? (fun raw => decide (p.normalizer.apply w group raw = out)) with
  | some raw => ⟨raw, List.mem_of_find?_eq_some h,
      of_decide_eq_true (List.find?_some (p := fun candidate => decide (p.normalizer.apply w group candidate = out)) h)⟩
  | none => False.elim (by
      obtain ⟨raw, rawPresent, normalized⟩ := localOutputExists summary group out present
      have rejected := List.find?_eq_none.mp h raw rawPresent
      simp [normalized] at rejected)

/-- Existing ordinary input delivery. The optimized system retains these rules
    and the local writer; it does not retain or consult the erased summary. -/
inductive NewFact (p : Program) : Event → Type where
  | root (r : RootOperation) (present : r ∈ p.roots) : NewFact p r.event
  | staticInput (a : Atom) (supplied : NewFact p (.pool a)) :
      NewFact p (.input (.static a.slot) a.group)
  | binding (b : Binding) (present : b ∈ p.bindings) (group : Nat)
      (actual : NewFact p (.input b.actual group)) :
      NewFact p (.input b.formal group)
  | localWriter (w : Writer) (present : w ∈ p.writers) (group : Nat)
      (input : NewFact p (.input w.input group))
      (output : Atom) (produced : output ∈ p.applyWriter w group) :
      NewFact p (.pool (p.normalizer.apply w group output))
  | ordinaryRule (r : LocalRule) (present : r ∈ p.localRules)
      (inputs : (i : Fin r.premises.length) → NewFact p (r.premises[i])) :
      NewFact p r.output

inductive OldFact (p : Program) : Event → Type where
  | root (r : RootOperation) (present : r ∈ p.roots) : OldFact p r.event
  | staticInput (a : Atom) (supplied : OldFact p (.pool a)) :
      OldFact p (.input (.static a.slot) a.group)
  | binding (b : Binding) (present : b ∈ p.bindings) (group : Nat)
      (actual : OldFact p (.input b.actual group)) :
      OldFact p (.input b.formal group)
  | localWriter (w : Writer) (present : w ∈ p.writers) (group : Nat)
      (input : OldFact p (.input w.input group))
      (output : Atom) (produced : output ∈ p.applyWriter w group) :
      OldFact p (.pool (p.normalizer.apply w group output))
  | ordinaryRule (r : LocalRule) (present : r ∈ p.localRules)
      (inputs : (i : Fin r.premises.length) → OldFact p (r.premises[i])) :
      OldFact p r.output
  | staticSummaryApply {owner : Nat} {slot : StaticSlot} {w : Writer}
      (summary : OldSummary p owner (.static slot) w) (group : Nat)
      (actual : OldFact p (.input (.static slot) group))
      (output : Atom) (produced : output ∈ oldRowOutputs summary group) :
      OldFact p (.pool output)

/-- Replay is executable. A first binding delivers an actual input to its
    original formal. A pure static wrapper adds no producer or input step. -/
def replayInput {p : Program} {owner : Nat} {site : InputSite} {w : Writer}
    (summary : OldSummary p owner site w) (group : Nat)
    (actual : NewFact p (.input site group)) : NewFact p (.input w.input group) :=
  match summary with
  | .local _ _ => actual
  | .firstBinding b present callee =>
      replayInput callee group (.binding b present group actual)
  | .staticRelay callee _ _ _ => replayInput callee group actual

theorem localWriterWitness {p : Program} {owner : Nat} {site : InputSite} {w : Writer}
    (summary : OldSummary p owner site w) : w ∈ p.writers :=
  match summary with
  | .local _ present => present
  | .firstBinding _ _ callee => localWriterWitness callee
  | .staticRelay callee _ _ _ => localWriterWitness callee

/-- Replay one old call-owned static job with the original local producer.
    The constructor is a localWriter, never a recreated caller writer. -/
def replayStaticJob {p : Program} {owner : Nat} {slot : StaticSlot} {w : Writer}
    (summary : OldSummary p owner (.static slot) w) (group : Nat)
    (actual : NewFact p (.input (.static slot) group))
    (output : Atom) (produced : output ∈ oldRowOutputs summary group) :
    NewFact p (.pool output) :=
  let found := localOutputPreimage summary group output produced
  found.property.2 ▸ .localWriter _ (localWriterWitness summary) group
    (replayInput summary group actual) found.val found.property.1

/-- Finite old witness compression. The old summary-application case executes
    the retained local producer after constructive ordinary input replay. -/
def compress {p : Program} {event : Event} (proof : OldFact p event) : NewFact p event :=
  match proof with
  | .root r present => .root r present
  | .staticInput a supplied => .staticInput a (compress supplied)
  | .binding b present group actual => .binding b present group (compress actual)
  | .localWriter w present group input output produced =>
      .localWriter w present group (compress input) output produced
  | .ordinaryRule r present inputs => .ordinaryRule r present (fun i => compress (inputs i))
  | .staticSummaryApply summary group actual output produced =>
      replayStaticJob summary group (compress actual) output produced

/-- Every new execution is also an old execution without its optional relays. -/
def embed {p : Program} {event : Event} (proof : NewFact p event) : OldFact p event :=
  match proof with
  | .root r present => .root r present
  | .staticInput a supplied => .staticInput a (embed supplied)
  | .binding b present group actual => .binding b present group (embed actual)
  | .localWriter w present group input output produced =>
      .localWriter w present group (embed input) output produced
  | .ordinaryRule r present inputs => .ordinaryRule r present (fun i => embed (inputs i))

theorem pool_complete {p : Program} {a : Atom} (old : OldFact p (.pool a)) :
    Nonempty (NewFact p (.pool a)) := ⟨compress old⟩

theorem pool_sound {p : Program} {a : Atom} (now : NewFact p (.pool a)) :
    Nonempty (OldFact p (.pool a)) := ⟨embed now⟩

theorem nonstatic_and_sink_observations_preserved {p : Program}
    {observation : OrdinaryObservation} (old : OldFact p (.ordinary observation)) :
    Nonempty (NewFact p (.ordinary observation)) := ⟨compress old⟩

/-- A later actual-query generator can consume the same complete supplied
    atom. Its independent query-completion proof is not assumed here. -/
theorem actual_query_source_witness_preserved {p : Program} {a : Atom}
    (old : OldFact p (.pool a)) (queries : Atom → List Nat)
    {query : Nat} (actual : query ∈ queries a) :
    Nonempty (NewFact p (.pool a)) ∧ query ∈ queries a := ⟨⟨compress old⟩, actual⟩

/-- Only roots change. Binding/kernel/stage semantics stay definitionally equal. -/
def changeRoots {p : Program} (roots : List RootOperation)
    (covers : ∀ r ∈ p.roots, r ∈ roots) {event : Event}
    (proof : NewFact p event) : NewFact { p with roots := roots } event :=
  match proof with
  | .root r present => .root r (covers r present)
  | .staticInput a supplied => .staticInput a (changeRoots roots covers supplied)
  | .binding b present group actual =>
      @NewFact.binding { p with roots := roots } b present group (changeRoots roots covers actual)
  | .localWriter w present group input output produced =>
      @NewFact.localWriter { p with roots := roots } w present group
        (changeRoots roots covers input) output produced
  | .ordinaryRule r present inputs =>
      @NewFact.ordinaryRule { p with roots := roots } r present
        (fun i => changeRoots roots covers (inputs i))

theorem late_root_does_not_remove_supply {p : Program} (later : List RootOperation)
    {event : Event} (proof : NewFact p event) :
    Nonempty (NewFact { p with roots := p.roots ++ later } event) :=
  ⟨changeRoots _ (by intro r present; simp [present]) proof⟩

theorem root_arrival_order_equivalent (p : Program) (first second : RootOperation)
    {event : Event} :
    Nonempty (NewFact { p with roots := p.roots ++ [first, second] } event) ↔
    Nonempty (NewFact { p with roots := p.roots ++ [second, first] } event) := by
  constructor
  · rintro ⟨proof⟩
    exact ⟨changeRoots (p := { p with roots := p.roots ++ [first, second] }) _
      (by intro r present; simpa [or_comm, or_left_comm, or_assoc] using present) proof⟩
  · rintro ⟨proof⟩
    exact ⟨changeRoots (p := { p with roots := p.roots ++ [second, first] }) _
      (by intro r present; simpa [or_comm, or_left_comm, or_assoc] using present) proof⟩

theorem duplicate_root_is_idempotent (p : Program) (arrived : RootOperation)
    {event : Event} :
    Nonempty (NewFact { p with roots := p.roots ++ [arrived, arrived] } event) ↔
    Nonempty (NewFact { p with roots := p.roots ++ [arrived] } event) := by
  constructor
  · rintro ⟨proof⟩
    exact ⟨changeRoots (p := { p with roots := p.roots ++ [arrived, arrived] }) _
      (by intro r present; simpa using present) proof⟩
  · rintro ⟨proof⟩
    exact ⟨changeRoots (p := { p with roots := p.roots ++ [arrived] }) _
      (by intro r present; simpa using present) proof⟩

/-- The two complete positive closures agree, but their stored static summaries
    deliberately do not. This theorem does not assert equal callback counts. -/
theorem complete_operational_closure {p : Program} {event : Event} :
    Nonempty (OldFact p event) ↔ Nonempty (NewFact p event) :=
  ⟨fun ⟨proof⟩ => ⟨compress proof⟩, fun ⟨proof⟩ => ⟨embed proof⟩⟩

inductive ProducerEffect where
  | register (key : WriterKey)
  | computed (atom : Atom)
  | staticSummary (owner : Nat) (input : InputSite) (descriptor : Nat)
  | ordinarySummary (owner : Nat) (input : InputSite) (descriptor : Nat)
  deriving DecidableEq, Repr

def rowEffect (w : Writer) : ProducerEffect :=
  if w.input.isStatic then .staticSummary w.owner w.input w.descriptor
  else .ordinarySummary w.owner w.input w.descriptor

def oldProducerBatch (w : Writer) (computed : List Atom) : List ProducerEffect :=
  .register (writerKey w) :: computed.map ProducerEffect.computed ++ [rowEffect w]

def newProducerBatch (w : Writer) (computed : List Atom) : List ProducerEffect :=
  .register (writerKey w) :: computed.map ProducerEffect.computed ++
    (if w.input.isStatic then [] else [rowEffect w])

theorem local_registration_precedes_storage (w : Writer) (computed : List Atom) :
    (newProducerBatch w computed).head? = some (.register (writerKey w)) := rfl

theorem computed_supply_independent_of_storage (w : Writer) {a : Atom}
    {computed : List Atom} (present : a ∈ computed) :
    ProducerEffect.computed a ∈ newProducerBatch w computed := by
  simp [newProducerBatch, present]

theorem nonstatic_writer_summary_is_retained (w : Writer) (computed : List Atom)
    (nonstatic : w.input.isStatic = false) :
    newProducerBatch w computed = oldProducerBatch w computed := by
  simp [newProducerBatch, oldProducerBatch, nonstatic]

theorem static_writer_storage_erased (w : Writer) (computed : List Atom)
    (static : w.input.isStatic = true) :
    newProducerBatch w computed =
      .register (writerKey w) :: computed.map ProducerEffect.computed := by
  simp [newProducerBatch, static]

theorem no_static_row_in_new_local_batch (w : Writer) (computed : List Atom)
    (owner : Nat) (input : InputSite) (descriptor : Nat) :
    ProducerEffect.staticSummary owner input descriptor ∉ newProducerBatch w computed := by
  cases h : w.input <;> simp [newProducerBatch, rowEffect, InputSite.isStatic, h]

/-- A call-composed static/static result can keep an already computed supply,
    but creates neither a local writer nor a retained static row. -/
def oldPromotedBatch (owner : Nat) (input : InputSite) (descriptor : Nat)
    (computed : List Atom) : List ProducerEffect :=
  computed.map ProducerEffect.computed ++ [.staticSummary owner input descriptor]

def newPromotedBatch (computed : List Atom) : List ProducerEffect :=
  computed.map ProducerEffect.computed

theorem promoted_computed_supply_kept {computed : List Atom} {a : Atom}
    (present : a ∈ computed) : ProducerEffect.computed a ∈ newPromotedBatch computed := by
  simp [newPromotedBatch, present]

theorem promotion_creates_no_writer (computed : List Atom) (key : WriterKey) :
    ProducerEffect.register key ∉ newPromotedBatch computed := by
  simp [newPromotedBatch]

theorem promotion_retains_no_static_row (computed : List Atom)
    (owner : Nat) (input : InputSite) (descriptor : Nat) :
    ProducerEffect.staticSummary owner input descriptor ∉ newPromotedBatch computed := by
  simp [newPromotedBatch]

/-- Replay plans make the ordinary request/input adapter explicit. Bindings are
    requested from formal to actual; accepted inputs replay in the opposite order.
    These are executable proof witnesses, not new runtime stored support labels. -/
def inputBindings {p : Program} {owner : Nat} {site : InputSite} {w : Writer} :
    OldSummary p owner site w → List Binding
  | .local .. => []
  | .firstBinding b _ callee => b :: inputBindings callee
  | .staticRelay callee _ _ _ => inputBindings callee

def requestBindings {p : Program} {owner : Nat} {site : InputSite} {w : Writer}
    (summary : OldSummary p owner site w) : List Binding := (inputBindings summary).reverse

theorem request_then_actual_replay {p : Program} {owner : Nat} {site : InputSite} {w : Writer}
    (summary : OldSummary p owner site w) :
    (requestBindings summary).reverse = inputBindings summary := by
  simp [requestBindings]

def summaryHops {p : Program} {owner : Nat} {site : InputSite} {w : Writer} :
    OldSummary p owner site w → Nat
  | .local .. => 0
  | .firstBinding _ _ callee => summaryHops callee + 1
  | .staticRelay callee _ _ _ => summaryHops callee + 1

def withWrappers {p : Program} {owner : Nat} {slot : StaticSlot} {w : Writer}
    (summary : OldSummary p owner (.static slot) w) :
    (count : Nat) → OldSummary p owner (.static slot) w
  | 0 => summary
  | n + 1 => .staticRelay (withWrappers summary n) owner n n

theorem wrappers_add_old_summary_hops {p : Program} {owner : Nat} {slot : StaticSlot}
    {w : Writer} (summary : OldSummary p owner (.static slot) w) (count : Nat) :
    summaryHops (withWrappers summary count) = summaryHops summary + count := by
  induction count with
  | zero => simp [withWrappers]
  | succ count ih => simp [withWrappers, summaryHops, ih, Nat.add_assoc]

theorem wrappers_add_no_input_replay_bindings {p : Program} {owner : Nat}
    {slot : StaticSlot} {w : Writer} (summary : OldSummary p owner (.static slot) w)
    (count : Nat) : inputBindings (withWrappers summary count) = inputBindings summary := by
  induction count with
  | zero => rfl
  | succ count ih => simpa [withWrappers, inputBindings] using ih

/-- A source root or a zero-arity existing GEN is required. Pure writer/relay
    cycles and positive complete-binding rules cannot create a source by themselves. -/
inductive RootWitness (p : Program) where
  | root (r : RootOperation) (present : r ∈ p.roots)
  | emptyRule (r : LocalRule) (present : r ∈ p.localRules) (empty : r.premises = [])

def newRootWitness {p : Program} {event : Event} (proof : NewFact p event) : RootWitness p :=
  match proof with
  | .root r present => .root r present
  | .staticInput _ supplied => newRootWitness supplied
  | .binding _ _ _ actual => newRootWitness actual
  | .localWriter _ _ _ input _ _ => newRootWitness input
  | .ordinaryRule r present inputs =>
      match h : r.premises with
      | [] => .emptyRule r present h
      | _ :: _ => newRootWitness (inputs ⟨0, by simp [h]⟩)

theorem no_source_free_cycle {p : Program} {event : Event}
    (noRoots : p.roots = [])
    (noEmptyRules : ∀ r ∈ p.localRules, r.premises ≠ [])
    (proof : NewFact p event) : False := by
  cases newRootWitness proof with
  | root r present => simp [noRoots] at present
  | emptyRule r present empty => exact noEmptyRules r present empty

/-- The same result holds before erasure; no stronger current GEN eligibility
    has been added to obtain the optimized result. -/
theorem old_no_source_free_cycle {p : Program} {event : Event}
    (noRoots : p.roots = [])
    (noEmptyRules : ∀ r ∈ p.localRules, r.premises ≠ [])
    (proof : OldFact p event) : False :=
  no_source_free_cycle noRoots noEmptyRules (compress proof)

/- Executable controls. The exact AP reducer is represented by simple finite
   functions here. These controls do not execute the Kotlin engine. -/
namespace Controls

def cf : StaticSlot := .field 1 1
def cg : StaticSlot := .field 1 2
def ch : StaticSlot := .field 1 3
def g : Nat := 7

def direct : Writer := ⟨10, 100, .static cg, cf, 55⟩
def setter : Writer := ⟨20, 200, .local 20 0, cf, 66⟩
def source : RootOperation := ⟨1, 1, .pool ⟨cg, g, 9, .exact, []⟩⟩
def setSource : RootOperation := ⟨1, 2, .input (.local 1 0) g⟩
def staticCall : Binding := ⟨1, 3, 4, 20, 0, .static cg⟩
def sourceCall : Binding := ⟨1, 5, 6, 20, 0, .local 1 0⟩
def kernel (w : Writer) (group : Nat) : List Atom := [⟨w.target, group, w.descriptor, .exact, []⟩]
def canonicalSupply (a : Atom) : Atom :=
  { a with tail := match a.tail with | .star => .any | other => other }

def norm : SupplyNormalizer := ⟨fun _ _ a => canonicalSupply a, by
  intro w group a
  cases h : a.tail <;> simp [canonicalSupply, h]⟩

def p : Program := ⟨[source, setSource], [direct, setter], [staticCall, sourceCall], [], kernel, norm⟩

def directSummary : OldSummary p 10 (.static cg) direct := .local direct (by simp [p])
def staticSetterSummary : OldSummary p 1 (.static cg) setter :=
  .firstBinding staticCall (by simp [p]) (.local setter (by simp [p]))
def sourceSetterSummary : OldSummary p 1 (.local 1 0) setter :=
  .firstBinding sourceCall (by simp [p]) (.local setter (by simp [p]))
def sourcePool : OldFact p (.pool ⟨cg, g, 9, .exact, []⟩) := .root source (by simp [p])
def staticInput : OldFact p (.input (.static cg) g) := .staticInput _ sourcePool

def oldWrappedWrite : OldFact p (.pool ⟨cf, g, 55, .exact, []⟩) :=
  .staticSummaryApply (withWrappers directSummary 8) g staticInput _ (by decide)
def newWrappedWrite : NewFact p (.pool ⟨cf, g, 55, .exact, []⟩) := compress oldWrappedWrite

def oldStaticActualSetter : OldFact p (.pool ⟨cf, g, 66, .exact, []⟩) :=
  .staticSummaryApply (withWrappers staticSetterSummary 5) g staticInput _
    (by decide)
def newStaticActualSetter : NewFact p (.pool ⟨cf, g, 66, .exact, []⟩) := compress oldStaticActualSetter

def newSourceSetter : NewFact p (.pool ⟨cf, g, 66, .exact, []⟩) :=
  .localWriter setter (by simp [p]) g
    (replayInput sourceSetterSummary g (.root setSource (by simp [p]))) ⟨cf, g, 66, .exact, []⟩
    (by decide)

#guard summaryHops (withWrappers directSummary 8) == 8
#guard (inputBindings (withWrappers directSummary 8)).isEmpty
#guard (inputBindings (withWrappers staticSetterSummary 5)).length == 1
#guard requestBindings staticSetterSummary == [staticCall]
#guard writerKey { direct with operation := 999 } == writerKey direct
#guard newProducerBatch direct [⟨cf, g, 55, .exact, []⟩] ==
  [.register (writerKey direct), .computed ⟨cf, g, 55, .exact, []⟩]
#guard newProducerBatch setter [⟨cf, g, 66, .exact, []⟩] == oldProducerBatch setter [⟨cf, g, 66, .exact, []⟩]
#guard !(newProducerBatch direct []).contains (.staticSummary direct.owner direct.input direct.descriptor)
#guard (newProducerBatch direct []).contains (.register (writerKey direct))

/-- Complete guard support, including a cross-group output, survives without
    any stored static/static row. No grounded-input policy is added. -/
def conditional : LocalRule :=
  ⟨30, 2, [.pool ⟨cg, g, 9, .exact, []⟩, .pool ⟨cf, g, 55, .exact, []⟩], .pool ⟨ch, 99, 77, .exact, []⟩⟩
def joinProgram : Program := { p with localRules := [conditional] }
def joinSource : NewFact joinProgram (.pool ⟨cg, g, 9, .exact, []⟩) := .root source (by simp [joinProgram, p])
def joinWrite : NewFact joinProgram (.pool ⟨cf, g, 55, .exact, []⟩) :=
  .localWriter direct (by simp [joinProgram, p]) g (.staticInput _ joinSource) ⟨cf, g, 55, .exact, []⟩
    (by simp [joinProgram, p, kernel, direct])
def completeJoin : NewFact joinProgram conditional.output :=
  .ordinaryRule conditional (by simp [joinProgram]) (fun i =>
    match i with
    | ⟨0, _⟩ => joinSource
    | ⟨1, _⟩ => joinWrite)

#guard conditional.premises.length == 2
#guard conditional.output == .pool ⟨ch, 99, 77, .exact, []⟩


/-- Concrete supply reconstruction changes STAR to ANY once. It does not
    discard a MUST tag or change field/mark/path correlation. The actual AP
    reapplication adapter is proved independently by the scalar AP model. -/
theorem canonicalSupply_preserves_correlated_fields (a : Atom) :
    (canonicalSupply a).slot = a.slot ∧
    (canonicalSupply a).group = a.group ∧
    (canonicalSupply a).path = a.path ∧
    (canonicalSupply a).payload = a.payload := ⟨rfl, rfl, rfl, rfl⟩

#guard canonicalSupply ⟨cf, 91, 5, .must, [4, 8]⟩ == ⟨cf, 91, 5, .must, [4, 8]⟩
#guard canonicalSupply ⟨cf, 91, 5, .star, [4, 8]⟩ == ⟨cf, 91, 5, .any, [4, 8]⟩
#guard canonicalSupply ⟨.exactClass 8, 91, 5, .exact, []⟩ == ⟨.exactClass 8, 91, 5, .exact, []⟩
#guard canonicalSupply (canonicalSupply ⟨cf, 91, 5, .star, [4, 8]⟩) ==
  canonicalSupply ⟨cf, 91, 5, .star, [4, 8]⟩

/-- A getter, returning wrapper and dead-local sink use ordinary rules.
    Their exact method/original/output/seed labels survive compression. -/
def getter : LocalRule :=
  ⟨30, 1, [.pool ⟨cf, g, 55, .exact, []⟩], .ordinary (.nonstatic 30 700 701 g 1)⟩
def returningWrapper : LocalRule :=
  ⟨31, 2, [getter.output], .ordinary (.nonstatic 31 700 702 g 1)⟩
def deadSink : LocalRule :=
  ⟨32, 3, [returningWrapper.output], .ordinary (.sinkSeed 32 3 44 g 55)⟩
def consumerProgram : Program := { p with localRules := [getter, returningWrapper, deadSink] }
def consumerSource : OldFact consumerProgram (.pool ⟨cg, g, 9, .exact, []⟩) :=
  .root source (by simp [consumerProgram, p])
def consumerWrite : OldFact consumerProgram (.pool ⟨cf, g, 55, .exact, []⟩) :=
  .staticSummaryApply (withWrappers (.local direct (by simp [consumerProgram, p])) 8)
    g (.staticInput _ consumerSource) _ (by decide)
def oldGetter : OldFact consumerProgram getter.output :=
  .ordinaryRule getter (by simp [consumerProgram]) (fun i => match i with | ⟨0, _⟩ => consumerWrite)
def oldReturningWrapper : OldFact consumerProgram returningWrapper.output :=
  .ordinaryRule returningWrapper (by simp [consumerProgram]) (fun i => match i with | ⟨0, _⟩ => oldGetter)
def oldDeadSink : OldFact consumerProgram deadSink.output :=
  .ordinaryRule deadSink (by simp [consumerProgram]) (fun i => match i with | ⟨0, _⟩ => oldReturningWrapper)
def newDeadSink : NewFact consumerProgram deadSink.output := compress oldDeadSink

#guard deadSink.output == .ordinary (.sinkSeed 32 3 44 g 55)
#guard getter.output != returningWrapper.output
#guard oldRowOutputs (withWrappers directSummary 8) g == oldRowOutputs directSummary g
#guard oldRowOutputs (withWrappers staticSetterSummary 5) g ==
  [⟨cf, g, 66, .exact, []⟩]
#guard (localOutputPreimage (withWrappers directSummary 8) g
  ⟨cf, g, 55, .exact, []⟩ (by decide)).val == ⟨cf, g, 55, .exact, []⟩

/-- Two explicit argument bindings, not wrapper subscriptions, remain in the
    request/replay plan. Pure static recursion adds no such binding. -/
def intermediate : Binding := ⟨40, 8, 9, 20, 0, .local 40 0⟩
def first : Binding := ⟨1, 9, 10, 40, 0, .static cg⟩
def nestedProgram : Program := { p with bindings := [intermediate, first] }
def nestedSummary : OldSummary nestedProgram 1 (.static cg) setter :=
  .firstBinding first (by simp [nestedProgram])
    (.firstBinding intermediate (by simp [nestedProgram])
      (.local setter (by simp [nestedProgram, p])))
#guard inputBindings nestedSummary == [first, intermediate]
#guard requestBindings nestedSummary == [intermediate, first]
#guard inputBindings (withWrappers nestedSummary 12) == [first, intermediate]
#guard oldRowOutputs (withWrappers nestedSummary 12) g == [⟨cf, g, 66, .exact, []⟩]

/- A complete conditional output can change group and path together; no
    same-group premise is smuggled into the erasure theorem. -/
#guard conditional.output == .pool ⟨ch, 99, 77, .exact, []⟩
#guard (oldRowOutputs directSummary g).length == 1
#guard (oldProducerBatch direct []).length == 2
#guard (newProducerBatch direct []).length == 1
#guard (newProducerBatch setter []).length == (oldProducerBatch setter []).length

#guard newPromotedBatch [⟨cf, 91, 5, .must, [4, 8]⟩] ==
  [.computed ⟨cf, 91, 5, .must, [4, 8]⟩]
#guard (oldPromotedBatch 80 (.static cg) 55 []).length == 1
#guard (newPromotedBatch []).isEmpty

/-- Reusable finite control packet. These tests evaluate the same predicates as
    the individual guards above; the packet is not a whole-engine simulator. -/
def controls : List Bool := [
  (summaryHops (withWrappers directSummary 8) == 8),
  ((inputBindings (withWrappers directSummary 8)).isEmpty),
  ((inputBindings (withWrappers staticSetterSummary 5)).length == 1),
  (requestBindings staticSetterSummary == [staticCall]),
  (writerKey { direct with operation := 999 } == writerKey direct),
  (newProducerBatch direct [⟨cf, g, 55, .exact, []⟩] ==
  [.register (writerKey direct), .computed ⟨cf, g, 55, .exact, []⟩]),
  (newProducerBatch setter [⟨cf, g, 66, .exact, []⟩] == oldProducerBatch setter [⟨cf, g, 66, .exact, []⟩]),
  (!(newProducerBatch direct []).contains (.staticSummary direct.owner direct.input direct.descriptor)),
  ((newProducerBatch direct []).contains (.register (writerKey direct))),
  (conditional.premises.length == 2),
  (conditional.output == .pool ⟨ch, 99, 77, .exact, []⟩),
  (canonicalSupply ⟨cf, 91, 5, .must, [4, 8]⟩ == ⟨cf, 91, 5, .must, [4, 8]⟩),
  (canonicalSupply ⟨cf, 91, 5, .star, [4, 8]⟩ == ⟨cf, 91, 5, .any, [4, 8]⟩),
  (canonicalSupply ⟨.exactClass 8, 91, 5, .exact, []⟩ == ⟨.exactClass 8, 91, 5, .exact, []⟩),
  (canonicalSupply (canonicalSupply ⟨cf, 91, 5, .star, [4, 8]⟩) ==
  canonicalSupply ⟨cf, 91, 5, .star, [4, 8]⟩),
  (deadSink.output == .ordinary (.sinkSeed 32 3 44 g 55)),
  (getter.output != returningWrapper.output),
  (oldRowOutputs (withWrappers directSummary 8) g == oldRowOutputs directSummary g),
  (oldRowOutputs (withWrappers staticSetterSummary 5) g ==
  [⟨cf, g, 66, .exact, []⟩]),
  ((localOutputPreimage (withWrappers directSummary 8) g
  ⟨cf, g, 55, .exact, []⟩ (by decide)).val == ⟨cf, g, 55, .exact, []⟩),
  (inputBindings nestedSummary == [first, intermediate]),
  (requestBindings nestedSummary == [intermediate, first]),
  (inputBindings (withWrappers nestedSummary 12) == [first, intermediate]),
  (oldRowOutputs (withWrappers nestedSummary 12) g == [⟨cf, g, 66, .exact, []⟩]),
  (conditional.output == .pool ⟨ch, 99, 77, .exact, []⟩),
  ((oldRowOutputs directSummary g).length == 1),
  ((oldProducerBatch direct []).length == 2),
  ((newProducerBatch direct []).length == 1),
  ((newProducerBatch setter []).length == (oldProducerBatch setter []).length),
  (newPromotedBatch [⟨cf, 91, 5, .must, [4, 8]⟩] ==
  [.computed ⟨cf, 91, 5, .must, [4, 8]⟩]),
  ((oldPromotedBatch 80 (.static cg) 55 []).length == 1),
  ((newPromotedBatch []).isEmpty)
]

theorem controls_pass : controls.all id = true := by decide
#guard controls.length == 32
#guard controls.all id

end Controls

end ApSpec.Run1StaticSummaryErasure
