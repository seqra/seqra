/-
  One-way DEFAULT-summary coverage for caller-owned input handling.

  Both sides use the SAME selected initial grammar, relative start layers,
  eager actual-static policy, local/publish/application kernels, and compaction.
  Complete method/context/entry keys and semantic objects are represented by
  injective opaque IDs, not lossy hashes. Payload support, E, mx, tails, ports
  and history are retained as values. AP/JVM compilation into these primitives
  and the validity of covered routing bindings remain explicit bridge duties.

  The optimized calculus receives canonical descriptors instead of adding a
  second callee supplier store. Its request rule uses the independently proved
  compressed routing relation, which may add interests. Therefore coverage,
  NOT reverse equality, is the selected conclusion. Proof trees and delivered
  pair lists are ghost certificates, not a proposed retained runtime history.
  Recursive finite derivations are covered; infinite-domain termination is not.
-/
import ApSpec.CallerOwnedRequestRouting
import ApSpec.BottomUpStaticTransport

namespace ApSpec.CallerOwnedSummaryCoverage

inductive Layer where
  | normal | demand
  deriving Repr, DecidableEq

inductive CurrentTail where
  | exact | star | any | must
  deriving Repr, DecidableEq

inductive InitialTail where
  | exact | star
  deriving Repr, DecidableEq

inductive Kind where
  | zero | flow | taint
  deriving Repr, DecidableEq

inductive Base where
  | zero | staticRoot | nonstatic (id : Nat) | field (cls field : Nat) | classPoint (cls : Nat)
  deriving Repr, DecidableEq

/-- All fields are part of the semantic value. Support includes full ND proof
    identity; no operation in this module flattens it to individual marks. -/
structure Payload where
  kind : Kind
  original : Nat
  premiseTag : Nat
  base : Base
  path : List Nat
  tail : CurrentTail
  marks : List Nat
  mx : List Nat
  fieldE : List Nat
  layer : Layer
  support : List Nat
  history : List Nat
  deriving Repr, DecidableEq

/-- Keys stand for complete MethodKey values, not just CommonMethod IDs. -/
structure Port where
  caller : Nat
  callee : Nat
  statement : Nat
  stageFrom : Nat
  stageTo : Nat
  headerFlags : Nat
  callerLayer : Layer
  deriving Repr, DecidableEq

/-- Structural compatibility excludes a contributing row's layer. Each row
    still retains that layer; the ordinary application kernel joins all of them. -/
def Port.coordinate (port : Port) : Nat × Nat × Nat × Nat × Nat × Nat :=
  (port.caller,port.callee,port.statement,port.stageFrom,port.stageTo,port.headerFlags)

structure Frame where
  owner : Nat
  point : Nat
  value : Payload
  deriving Repr, DecidableEq

structure Presence where
  owner : Nat
  metadata : Nat
  demandInput : Bool
  deriving Repr, DecidableEq

structure Input where
  port : Port
  value : Payload
  presence : Presence
  deriving Repr, DecidableEq

/-- There is no ANY initial and no static-root initial constructor. Broad/root
    and class projection must be normalized before this interface. -/
inductive Shape where
  | zero
  | abstractBase (base : Nat)
  | abstractField (cls field : Nat)
  | concreteBase (base mark : Nat) (tail : InitialTail)
  | concreteField (cls field mark : Nat) (tail : InitialTail)
  | concreteClass (cls mark : Nat) (tail : InitialTail)
  deriving Repr, DecidableEq

structure Initial where
  owner : Nat
  shape : Shape
  fullTag : Nat
  deriving Repr, DecidableEq

def initialLayer (i : Initial) : Layer :=
  match i.shape with
  | .abstractField .. | .concreteField .. | .concreteClass .. | .concreteBase _ _ .star => .demand
  | _ => .normal

/-- Exact/STAR original tags stay independent of current ANY geometry. -/
inductive Descriptor where
  | ordinary (initial : Initial)
  | field (owner cls field : Nat) (mark : Option Nat) (tail : InitialTail) (tag : Nat)
  | classPoint (owner cls mark : Nat) (tail : InitialTail) (tag : Nat)
  deriving Repr, DecidableEq

def encode (i : Initial) : Descriptor :=
  match i.shape with
  | .abstractField c f => .field i.owner c f none .star i.fullTag
  | .concreteField c f m tail => .field i.owner c f (some m) tail i.fullTag
  | .concreteClass c m tail => .classPoint i.owner c m tail i.fullTag
  | _ => .ordinary i

def decode : Descriptor → Initial
  | .ordinary initial => initial
  | .field owner cls field none _ tag => ⟨owner,.abstractField cls field,tag⟩
  | .field owner cls field (some mark) tail tag =>
      ⟨owner,.concreteField cls field mark tail,tag⟩
  | .classPoint owner cls mark tail tag => ⟨owner,.concreteClass cls mark tail,tag⟩

@[simp] theorem decode_encode (i : Initial) : decode (encode i) = i := by
  cases i with
  | mk owner shape tag => cases shape <;> rfl

@[simp] theorem descriptor_preserves_owner (i : Initial) :
    (decode (encode i)).owner = i.owner := by rw [decode_encode]

@[simp] theorem descriptor_preserves_full_initial (i : Initial) :
    (decode (encode i)).shape = i.shape ∧ (decode (encode i)).fullTag = i.fullTag := by
  simp


@[simp] theorem batch_decode_encode (initials : List Initial) :
    (initials.map encode).map decode = initials := by
  induction initials with
  | nil => rfl
  | cons i rest ih => simp [ih]

/-- Mark0 is not excluded. Marks are injective IDs for frontend-admitted marks. -/
def ActualStatic (row : Input) (i : Initial) : Prop :=
  i.owner = row.port.callee ∧ match i.shape with
  | .concreteField c f mark _ =>
      row.value.kind = .taint ∧ row.value.base = .field c f ∧ mark ∈ row.value.marks
  | .concreteClass c mark _ =>
      row.value.kind = .taint ∧ row.value.base = .classPoint c ∧ mark ∈ row.value.marks
  | .concreteBase .. => False
  | _ => True

instance (row : Input) (i : Initial) : Decidable (ActualStatic row i) := by
  unfold ActualStatic
  cases i.shape <;> infer_instance

/-- Interests ignore E/mx. Only the actual TAINT supplier proves concrete T;
    complete AP compatibility is additionally checked by the shared projector. -/
def RequestedActual (row : Input) (q : CallerOwnedRequestRouting.Request)
    (i : Initial) : Prop :=
  q.node.owner = row.port.callee ∧ i.owner = row.port.callee ∧
  row.value.kind = .taint ∧ match q.node.slot,i.shape with
  | .base base,.concreteBase other mark _ =>
      base = other ∧ mark = q.mark ∧ row.value.base = .nonstatic base ∧
      q.mark ∈ row.value.marks
  | _,_ => False

instance (row : Input) (q : CallerOwnedRequestRouting.Request) (i : Initial) :
    Decidable (RequestedActual row q i) := by
  unfold RequestedActual
  cases q.node.slot <;> cases i.shape <;> infer_instance

theorem requested_start_has_actual_mark {row : Input}
    {q : CallerOwnedRequestRouting.Request} {i : Initial}
    (h : RequestedActual row q i) : q.mark ∈ row.value.marks := by
  rcases h with ⟨_,_,_,h⟩
  split at h
  · exact h.2.2.2
  · contradiction

structure Summary where
  owner : Nat
  fullPremises : List Nat
  value : Payload
  deriving Repr, DecidableEq

inductive LocalResult where
  | fact (frame : Frame)
  | request (need : CallerOwnedRequestRouting.Request)
  | observation (owner identity : Nat) (value : Payload)
  deriving Repr, DecidableEq

inductive Atom where
  | fact (frame : Frame)
  | input (row : Input)
  | initial (initial : Initial)
  | presence (value : Presence)
  | request (need : CallerOwnedRequestRouting.Request)
  | summary (value : Summary)
  | observation (owner identity : Nat) (value : Payload)
  deriving Repr, DecidableEq

def LocalResult.atom : LocalResult → Atom
  | .fact f => .fact f
  | .request q => .request q
  | .observation owner identity value => .observation owner identity value

structure LocalClause where
  facts : List Frame
  presence : List Presence
  result : LocalResult
  deriving Repr, DecidableEq

structure PublishClause where
  facts : List Frame
  presence : List Presence
  result : Summary
  deriving Repr, DecidableEq

structure Program where
  world : CallerOwnedRequestRouting.World
  seeds : List Frame
  locals : List LocalClause
  publish : List PublishClause
  callInputs : Frame → List Input
  unconditional : Input → List Initial
  requested : Input → CallerOwnedRequestRouting.Request → List Initial
  start : Initial → List Frame
  bindingInput : CallerOwnedRequestRouting.Binding → Input
  memberMatches : Input → Nat → Bool
  apply : Port → List Input → Summary → List LocalResult

/-- One full call binding; every ND member is required. The complete ordinary
    matcher and layer join remain in the shared apply kernel. -/
def ApplicationOK (p : Program) (port : Port) (rows : List Input) (summary : Summary) : Prop :=
  summary.owner = port.callee ∧ rows.length = summary.fullPremises.length ∧
    (rows.zip summary.fullPremises).all (fun (row,member) => p.memberMatches row member) = true ∧
    ∀ row ∈ rows, row.port.coordinate = port.coordinate

instance (p : Program) (port : Port) (rows : List Input) (summary : Summary) :
    Decidable (ApplicationOK p port rows summary) := by
  unfold ApplicationOK
  infer_instance

inductive Ordinary (p : Program) : Atom → Type where
  | seed {f} (member : f ∈ p.seeds) : Ordinary p (.fact f)
  | start {i f} (initial : Ordinary p (.initial i)) (member : f ∈ p.start i)
      (relativeLayer : f.value.layer = initialLayer i) : Ordinary p (.fact f)
  | local {clause} (member : clause ∈ p.locals)
      (facts : ∀ f ∈ clause.facts, Ordinary p (.fact f))
      (presence : ∀ value ∈ clause.presence, Ordinary p (.presence value)) :
      Ordinary p clause.result.atom
  | callInput {f row} (fact : Ordinary p (.fact f)) (member : row ∈ p.callInputs f) :
      Ordinary p (.input row)
  | presence {row} (input : Ordinary p (.input row)) : Ordinary p (.presence row.presence)
  | project {row i} (input : Ordinary p (.input row)) (member : i ∈ p.unconditional row)
      (actual : ActualStatic row i) : Ordinary p (.initial i)
  | route {source target} (need : Ordinary p (.request source))
      (path : CallerOwnedRequestRouting.OrdinaryRoute p.world source target)
      (active : ∀ binding ∈ CallerOwnedRequestRouting.ordinaryBindings path.path,
        Ordinary p (.input (p.bindingInput binding))) : Ordinary p (.request target)
  | requested {row q i} (input : Ordinary p (.input row)) (need : Ordinary p (.request q))
      (member : i ∈ p.requested row q) (actual : RequestedActual row q i) :
      Ordinary p (.initial i)
  | publish {clause} (member : clause ∈ p.publish)
      (facts : ∀ f ∈ clause.facts, Ordinary p (.fact f))
      (presence : ∀ value ∈ clause.presence, Ordinary p (.presence value)) :
      Ordinary p (.summary clause.result)
  | apply {port rows summary output presence} (answer : Ordinary p (.summary summary))
      (inputs : ∀ row ∈ rows, Ordinary p (.input row))
      (invocation : Ordinary p (.presence presence))
      (owner : presence.owner = port.callee) (header : presence.metadata = port.headerFlags)
      (samePort : ApplicationOK p port rows summary) (member : output ∈ p.apply port rows summary) :
      Ordinary p output.atom

/-- Inputs remain caller-owned. Canonical deliveries do not retain a callee raw
    supplier relation. Requests are semantic certificates, not relay R/P stores. -/
inductive Caller (p : Program) : Atom → Type where
  | seed {f} (member : f ∈ p.seeds) : Caller p (.fact f)
  | start {i f} (initial : Caller p (.initial i)) (member : f ∈ p.start i)
      (relativeLayer : f.value.layer = initialLayer i) : Caller p (.fact f)
  | local {clause} (member : clause ∈ p.locals)
      (facts : ∀ f ∈ clause.facts, Caller p (.fact f))
      (presence : ∀ value ∈ clause.presence, Caller p (.presence value)) :
      Caller p clause.result.atom
  | callInput {f row} (fact : Caller p (.fact f)) (member : row ∈ p.callInputs f) :
      Caller p (.input row)
  | presence {row} (input : Caller p (.input row)) : Caller p (.presence row.presence)
  | canonical {row descriptor} (input : Caller p (.input row))
      (member : descriptor ∈ (p.unconditional row).map encode)
      (actual : ActualStatic row (decode descriptor)) : Caller p (.initial (decode descriptor))
  | route {source target} (need : Caller p (.request source))
      (path : CallerOwnedRequestRouting.OptimizedRoute p.world source target)
      (active : ∀ binding ∈ CallerOwnedRequestRouting.productiveBindings path.path,
        Caller p (.input (p.bindingInput binding))) : Caller p (.request target)
  | requestedCanonical {row q descriptor} (input : Caller p (.input row))
      (need : Caller p (.request q)) (member : descriptor ∈ (p.requested row q).map encode)
      (actual : RequestedActual row q (decode descriptor)) : Caller p (.initial (decode descriptor))
  | publish {clause} (member : clause ∈ p.publish)
      (facts : ∀ f ∈ clause.facts, Caller p (.fact f))
      (presence : ∀ value ∈ clause.presence, Caller p (.presence value)) :
      Caller p (.summary clause.result)
  | apply {port rows summary output presence} (answer : Caller p (.summary summary))
      (inputs : ∀ row ∈ rows, Caller p (.input row))
      (invocation : Caller p (.presence presence))
      (owner : presence.owner = port.callee) (header : presence.metadata = port.headerFlags)
      (samePort : ApplicationOK p port rows summary) (member : output ∈ p.apply port rows summary) :
      Caller p output.atom

/-- An executable proof-tree translator. No constructor assumes summary coverage.
    The ONLY changed primitive is routing, supplied by constructive compression. -/
def promote {p : Program} (valid : CallerOwnedRequestRouting.WorldValid p.world) {atom : Atom}
    (proof : Ordinary p atom) : Caller p atom :=
  match proof with
  | .seed member => .seed member
  | .start initial member layer => .start (promote valid initial) member layer
  | .local member facts presence =>
      .local member (fun f hf => promote valid (facts f hf))
        (fun value hv => promote valid (presence value hv))
  | .callInput fact member => .callInput (promote valid fact) member
  | .presence input => .presence (promote valid input)
  | .project input member actual => by
      simpa only [decode_encode] using
        Caller.canonical (promote valid input) (List.mem_map.mpr ⟨_,member,rfl⟩)
          (by simpa only [decode_encode] using actual)
  | .route need path active =>
      .route (promote valid need) (CallerOwnedRequestRouting.compressRequestRoute valid path)
        (fun binding member => by
          have kept : binding ∈ CallerOwnedRequestRouting.productiveBindings
              (CallerOwnedRequestRouting.compress valid path.path) := member
          rw [CallerOwnedRequestRouting.compression_preserves_exact_productive_bindings] at kept
          exact promote valid (active binding (List.mem_filter.mp kept).1))
  | .requested input need member actual => by
      simpa only [decode_encode] using
        Caller.requestedCanonical (promote valid input) (promote valid need)
          (List.mem_map.mpr ⟨_,member,rfl⟩) (by simpa only [decode_encode] using actual)
  | .publish member facts presence =>
      .publish member (fun f hf => promote valid (facts f hf))
        (fun value hv => promote valid (presence value hv))
  | .apply answer inputs invocation owner header samePort member =>
      .apply (promote valid answer) (fun row hr => promote valid (inputs row hr))
        (promote valid invocation) owner header samePort member

theorem default_summary_coverage {p : Program} (valid : CallerOwnedRequestRouting.WorldValid p.world)
    (summary : Summary) : Nonempty (Ordinary p (.summary summary)) →
      Nonempty (Caller p (.summary summary)) := fun ⟨proof⟩ => ⟨promote valid proof⟩

theorem every_default_observation_covered {p : Program} (valid : CallerOwnedRequestRouting.WorldValid p.world)
    (atom : Atom) : Nonempty (Ordinary p atom) → Nonempty (Caller p atom) :=
  fun ⟨proof⟩ => ⟨promote valid proof⟩

def VisibleOrdinary (p : Program) (compact : Summary → Bool) (summary : Summary) : Prop :=
  Nonempty (Ordinary p (.summary summary)) ∧ compact summary = false

def VisibleCaller (p : Program) (compact : Summary → Bool) (summary : Summary) : Prop :=
  Nonempty (Caller p (.summary summary)) ∧ compact summary = false

theorem same_compaction_visible_coverage {p : Program} (valid : CallerOwnedRequestRouting.WorldValid p.world)
    (compact : Summary → Bool) (summary : Summary) :
    VisibleOrdinary p compact summary → VisibleCaller p compact summary := by
  rintro ⟨proof,visible⟩
  exact ⟨default_summary_coverage valid summary proof,visible⟩

theorem exact_nary_port_guard {p port rows summary} (h : ApplicationOK p port rows summary)
    (row : Input) (member : row ∈ rows) : row.port.coordinate = port.coordinate := h.2.2.2 row member

theorem complete_nary_arity {p port rows summary} (h : ApplicationOK p port rows summary) :
    rows.length = summary.fullPremises.length := h.2.1

/- Positive finite frontiers are arrival-order independent. The candidate is
   the COMPLETE input (or complete ND bundle), never one support member. -/
theorem request_fact_all_arrival_orders
    (events : List (IncrementalRequestTable.Event CallerOwnedRequestRouting.Request Input)) (need : CallerOwnedRequestRouting.Request) (row : Input) :
    (need,row) ∈ (IncrementalRequestTable.drain (IncrementalRequestTable.run IncrementalRequestTable.empty events)).delivered ↔
      need ∈ IncrementalRequestTable.offeredRequests events ∧ row ∈ IncrementalRequestTable.offeredCandidates events :=
  IncrementalRequestTable.all_arrival_orders_exact events need row

def requestedAnswers (p : Program) (events : List (IncrementalRequestTable.Event CallerOwnedRequestRouting.Request Input)) :
    List Initial :=
  (IncrementalRequestTable.drain (IncrementalRequestTable.run IncrementalRequestTable.empty events)).delivered.flatMap
    (fun (need,row) => (p.requested row need).filter (fun i => decide (RequestedActual row need i)))

theorem requested_answers_exact (p : Program)
    (events : List (IncrementalRequestTable.Event CallerOwnedRequestRouting.Request Input)) (i : Initial) :
    i ∈ requestedAnswers p events ↔ ∃ need row,
      need ∈ IncrementalRequestTable.offeredRequests events ∧ row ∈ IncrementalRequestTable.offeredCandidates events ∧
      i ∈ p.requested row need ∧ RequestedActual row need i := by
  simp only [requestedAnswers,List.mem_flatMap,List.mem_filter,decide_eq_true_eq]
  constructor
  · rintro ⟨⟨need,row⟩,pair,member,actual⟩
    exact ⟨need,row,(request_fact_all_arrival_orders _ _ _).mp pair |>.1,
      (request_fact_all_arrival_orders _ _ _).mp pair |>.2,member,actual⟩
  · rintro ⟨need,row,hn,hr,member,actual⟩
    exact ⟨(need,row),(request_fact_all_arrival_orders _ _ _).mpr ⟨hn,hr⟩,member,actual⟩


/-- Finite product is an executable *oracle* for complete ND bundles. The
    implementation may index joins instead; it must realize exactly the same
    compatible full tuples, never pool members across ports. -/
def tuples {α : Type} : Nat → List α → List (List α)
  | 0,_ => [[]]
  | n+1,values => values.flatMap (fun value => (tuples n values).map (value :: ·))

theorem mem_tuples {α : Type} (n : Nat) (values rows : List α) :
    rows ∈ tuples n values ↔ rows.length = n ∧ ∀ row ∈ rows, row ∈ values := by
  induction n generalizing rows with
  | zero => cases rows <;> simp [tuples]
  | succ n ih =>
    cases rows with
    | nil =>
      constructor
      · intro member
        rcases List.mem_flatMap.mp member with ⟨value,_,mapped⟩
        rcases List.mem_map.mp mapped with ⟨tail,_,impossible⟩
        cases impossible
      · intro impossible
        exact False.elim (Nat.noConfusion impossible.1)
    | cons first rest =>
      simp only [tuples,List.mem_flatMap,List.mem_map]
      constructor
      · rintro ⟨value,hvalue,tail,htail,equal⟩
        cases equal
        have h := (ih rest).mp htail
        exact ⟨congrArg Nat.succ h.1,by simpa using And.intro hvalue h.2⟩
      · rintro ⟨length,members⟩
        have hfirst := members first (by simp)
        have hrest : ∀ row ∈ rest, row ∈ values := fun row hr => members row (by simp [hr])
        exact ⟨first,hfirst,rest,(ih rest).mpr ⟨Nat.succ.inj length,hrest⟩,rfl⟩

structure Bundle where
  port : Port
  rows : List Input
  presence : Presence
  deriving Repr, DecidableEq

def bundles (port : Port) (n : Nat) (inputs : List Input) (presence : Presence) : List Bundle :=
  ((tuples n inputs).filter (fun rows => rows.all (fun row => row.port.coordinate == port.coordinate))).map
    (fun rows => ⟨port,rows,presence⟩)

theorem complete_bundle_enumerated (port : Port) (rows inputs : List Input)
    (presence : Presence) (retained : ∀ row ∈ rows, row ∈ inputs)
    (samePort : ∀ row ∈ rows, row.port.coordinate = port.coordinate) :
    (⟨port,rows,presence⟩ : Bundle) ∈ bundles port rows.length inputs presence := by
  apply List.mem_map.mpr
  refine ⟨rows,List.mem_filter.mpr ⟨(mem_tuples _ _ _).mpr ⟨rfl,retained⟩,?_⟩,rfl⟩
  simpa only [List.all_eq_true,beq_iff_eq] using samePort

def summaryAnswers (p : Program)
    (events : List (IncrementalRequestTable.Event Summary Bundle)) : List LocalResult :=
  (IncrementalRequestTable.drain (IncrementalRequestTable.run IncrementalRequestTable.empty events)).delivered.flatMap
    (fun (summary,bundle) => if ApplicationOK p bundle.port bundle.rows summary ∧
        bundle.presence.owner = bundle.port.callee ∧
        bundle.presence.metadata = bundle.port.headerFlags
      then p.apply bundle.port bundle.rows summary else [])

/-- Summary arrival and complete input-bundle arrival can occur in any order.
    Request/input joining above and this summary/bundle joining are separate
    positive frontiers. No per-member or cross-port ND answer is introduced. -/
theorem summary_answers_exact (p : Program)
    (events : List (IncrementalRequestTable.Event Summary Bundle)) (result : LocalResult) :
    result ∈ summaryAnswers p events ↔ ∃ summary bundle,
      summary ∈ IncrementalRequestTable.offeredRequests events ∧
      bundle ∈ IncrementalRequestTable.offeredCandidates events ∧
      ApplicationOK p bundle.port bundle.rows summary ∧
      bundle.presence.owner = bundle.port.callee ∧
      bundle.presence.metadata = bundle.port.headerFlags ∧
      result ∈ p.apply bundle.port bundle.rows summary := by
  simp only [summaryAnswers,List.mem_flatMap]
  constructor
  · rintro ⟨⟨summary,bundle⟩,pair,member⟩
    split at member
    · rename_i ok
      exact ⟨summary,bundle,(IncrementalRequestTable.all_arrival_orders_exact _ _ _).mp pair |>.1,
        (IncrementalRequestTable.all_arrival_orders_exact _ _ _).mp pair |>.2,
        ok.1,ok.2.1,ok.2.2,member⟩
    · simp at member
  · rintro ⟨summary,bundle,hs,hb,ok,owner,header,member⟩
    refine ⟨(summary,bundle),(IncrementalRequestTable.all_arrival_orders_exact _ _ _).mpr ⟨hs,hb⟩,?_⟩
    rw [if_pos (show ApplicationOK p bundle.port bundle.rows summary ∧
        bundle.presence.owner = bundle.port.callee ∧
        bundle.presence.metadata = bundle.port.headerFlags from ⟨ok,owner,header⟩)]
    exact member

/-- All finite recursive proof trees are covered. This induction is on proof
    construction, not a bounded call depth or an acyclic call graph. -/
theorem finite_recursive_closure_coverage {p : Program}
    (valid : CallerOwnedRequestRouting.WorldValid p.world) (atoms : List Atom)
    (proofs : ∀ atom ∈ atoms, Nonempty (Ordinary p atom)) :
    ∀ atom ∈ atoms, Nonempty (Caller p atom) :=
  fun atom member => every_default_observation_covered valid atom (proofs atom member)

namespace Witness

def payload : Payload :=
  ⟨.taint,10,20,.nonstatic 7,[],.exact,[5],[],[],.normal,[101],[201]⟩
def port : Port := ⟨1,2,3,4,5,99,.demand⟩
def present : Presence := ⟨2,99,true⟩
def seed : Frame := ⟨1,0,payload⟩
def seed2 : Frame := ⟨1,1,{payload with original := 11, premiseTag := 21, support := [102]}⟩
def row : Input := ⟨{port with callerLayer := .normal},payload,present⟩
def row2 : Input := ⟨port,seed2.value,present⟩
def absInitial : Initial := ⟨2,.abstractBase 7,20⟩
def taintInitial : Initial := ⟨2,.concreteBase 7 5 .exact,20⟩
def absFact : Frame := ⟨2,0,{payload with kind := .flow, marks := [], original := 30}⟩
def taintFact : Frame := ⟨2,1,payload⟩
def need : CallerOwnedRequestRouting.Request := ⟨⟨2,.base 7⟩,5⟩
def excludedNeed : CallerOwnedRequestRouting.Request := ⟨⟨1,.staticField 44⟩,8⟩
def needClause : LocalClause := ⟨[absFact],[],.request need⟩
def eventClause : LocalClause := ⟨[absFact],[],.observation 2 66 payload⟩
def summary : Summary := ⟨2,[20,21],{payload with layer := .demand, support := [101,102]}⟩
def publishClause : PublishClause := ⟨[taintFact],[present],summary⟩
def result : Frame := ⟨1,5,summary.value⟩
def applicationResults : List LocalResult :=
  [.fact result,.request excludedNeed,.observation 1 77 summary.value]

def program : Program where
  world := ⟨[],[]⟩
  seeds := [seed,seed2]
  locals := [needClause,eventClause]
  publish := [publishClause]
  callInputs := fun fact => if fact = seed then [row] else if fact = seed2 then [row2] else []
  unconditional := fun input => if input = row then [absInitial] else []
  requested := fun input request => if input = row ∧ request = need then [taintInitial] else []
  start := fun initial => if initial = absInitial then [absFact]
    else if initial = taintInitial then [taintFact] else []
  bindingInput := fun _ => row
  memberMatches := fun input member => input.value.premiseTag == member || (input == row && member == 22)
  apply := fun p inputs s => if p = port ∧ inputs = [row,row2] ∧ s = summary
    then applicationResults else []

theorem valid : CallerOwnedRequestRouting.WorldValid program.world := by
  intro b member
  simp [program] at member

def inputProof : Ordinary program (.input row) :=
  .callInput (f := seed) (.seed (by decide)) (by decide)
def inputProof2 : Ordinary program (.input row2) :=
  .callInput (f := seed2) (.seed (by decide)) (by decide)
def presenceProof : Ordinary program (.presence present) := .presence inputProof

def absProof : Ordinary program (.fact absFact) :=
  .start (i := absInitial) (.project inputProof (by decide) (by decide)) (by decide) rfl

def needProof : Ordinary program (.request need) :=
  .local (clause := needClause) (by decide)
    (by intro f member; have equal : f = absFact := by simpa [needClause] using member
        subst f; exact absProof)
    (by intro value member; exact False.elim (by simpa [needClause] using member))

def taintProof : Ordinary program (.fact taintFact) :=
  .start (i := taintInitial) (.requested inputProof needProof (by decide) (by decide)) (by decide) rfl

def summaryProof : Ordinary program (.summary summary) :=
  .publish (clause := publishClause) (by decide)
    (by intro f member; have equal : f = taintFact := by simpa [publishClause] using member
        subst f; exact taintProof)
    (by intro value member; have equal : value = present := by simpa [publishClause] using member
        subst value; exact presenceProof)

def allInputs : ∀ input ∈ [row,row2], Ordinary program (.input input) := by
  intro input member
  by_cases first : input = row
  · subst input; exact inputProof
  · have second : input = row2 := by simpa [first] using member
    subst input; exact inputProof2

def applicationProof (output : LocalResult) (member : output ∈ applicationResults) :
    Ordinary program output.atom :=
  .apply (port := port) summaryProof allInputs presenceProof rfl rfl (by decide)
    (by simpa [program] using member)

/-- Non-vacuous call→abstract start→need→actual concrete start→summary→ND
    application chain. Its final summary/result are translated constructively. -/
def callerSummary : Caller program (.summary summary) := promote valid summaryProof
def callerNDResult : Caller program (.fact result) :=
  promote valid (applicationProof (.fact result) (by decide))
def callerExcludedTargetNeed : Caller program (.request excludedNeed) :=
  promote valid (applicationProof (.request excludedNeed) (by decide))

/-- Local observation needs no publish/normal-return premise. -/
def observationWithoutReturn : Ordinary {program with publish := []}
    (.observation 2 66 payload) :=
  .local (clause := eventClause) (by decide)
    (by intro f member; have equal : f = absFact := by simpa [eventClause] using member
        subst f
        exact .start (i := absInitial) (.project (row := row)
          (.callInput (f := seed) (.seed (by decide)) (by decide))
          (by decide) (by decide)) (by decide) rfl)
    (by intro value member; exact False.elim (by simpa [eventClause] using member))

/-- One root semantic premise, despite no root INITIAL constructor. This is a
    structural default-publication witness; AP kernel compilation is external. -/
def defaultSummary : Summary := ⟨2,[900],{payload with
  kind := .flow
  original := 900
  premiseTag := 900
  base := .staticRoot
  tail := .star
  marks := []
  layer := .demand
  support := [900]}⟩
def defaultClause : PublishClause := ⟨[],[present],defaultSummary⟩
def presenceOnly : Program := {program with
  unconditional := fun _ => []
  requested := fun _ _ => []
  start := fun _ => []
  locals := []
  publish := [defaultClause]}

def defaultWithoutInitial : Ordinary presenceOnly (.summary defaultSummary) :=
  .publish (clause := defaultClause) (by decide)
    (by intro f member; exact False.elim (by simpa [defaultClause] using member))
    (by intro value member; have equal : value = present := by simpa [defaultClause] using member
        subst value; exact .presence (row := row) (.callInput (f := seed) (.seed (by decide)) (by decide)))

def bundle : Bundle := ⟨port,[row,row2],present⟩
def requestFirst : List (IncrementalRequestTable.Event CallerOwnedRequestRouting.Request Input) :=
  [.requests [need],.barrier,.candidates [row],.barrier,.requests [need]]
def factFirst : List (IncrementalRequestTable.Event CallerOwnedRequestRouting.Request Input) :=
  [.candidates [row],.barrier,.requests [need],.barrier,.candidates [row]]
def summaryFirst : List (IncrementalRequestTable.Event Summary Bundle) :=
  [.requests [summary],.barrier,.candidates [bundle],.barrier]
def inputsFirst : List (IncrementalRequestTable.Event Summary Bundle) :=
  [.candidates [bundle],.barrier,.requests [summary],.barrier]

def controls : List (String × Bool) := [
  ("request_before_fact", requestedAnswers program requestFirst == [taintInitial]),
  ("fact_before_request", requestedAnswers program factFirst == [taintInitial]),
  ("summary_before_all_inputs", summaryAnswers program summaryFirst == applicationResults),
  ("all_inputs_before_summary", summaryAnswers program inputsFirst == applicationResults),
  ("ND_mixed_layers_compatible", row.port.callerLayer != row2.port.callerLayer && decide (ApplicationOK program port [row,row2] summary)),
  ("caller_original_distinct_from_callee_premise", payload.original != payload.premiseTag && decide (ApplicationOK program port [row,row2] summary)),
  ("one_actual_row_multiple_member_matches", decide (ApplicationOK program port [row,row2] {summary with fullPremises := [22,21]})),
  ("ND_complete_port", decide (ApplicationOK program port [row,row2] summary)),
  ("ND_missing_member_rejected", !decide (ApplicationOK program port [row] summary)),
  ("ND_other_stage_rejected", !decide (ApplicationOK program port [row,{row2 with port := {port with stageTo := 6}}] summary)),
  ("ND_other_target_rejected", !decide (ApplicationOK program port [row,{row2 with port := {port with callee := 9}}] summary)),
  ("ND_premise_order_rejected", !decide (ApplicationOK program port [row2,row] summary)),
  ("actual_T_required", !decide (RequestedActual {row with value := {payload with marks := []}} need taintInitial)),
  ("FLOW_not_concrete_T", !decide (RequestedActual {row with value := {payload with kind := .flow}} need taintInitial)),
  ("request_ignores_E_mx", decide (RequestedActual {row with value := {payload with mx := [5], fieldE := [1]}} need taintInitial)),
  ("different_base_rejected", !decide (RequestedActual {row with value := {payload with base := .nonstatic 8}} need taintInitial)),
  ("base_STAR_starts_DEMAND", initialLayer ⟨2,.concreteBase 7 5 .star,20⟩ == .demand),
  ("base_EXACT_relative_NORMAL", initialLayer taintInitial == .normal),
  ("static_STAR_keeps_initial_tag", decode (encode ⟨2,.concreteField 3 4 0 .star,20⟩) == ⟨2,.concreteField 3 4 0 .star,20⟩),
  ("static_mark0_actual", decide (ActualStatic {row with value := {payload with base := .field 3 4, tail := .any, marks := [0]}} ⟨2,.concreteField 3 4 0 .star,20⟩)),
  ("presence_only_no_initial", presenceOnly.unconditional row == []),
  ("complete_bundle_enumeration", bundle ∈ bundles port 2 [row,row2] present)]

set_option maxRecDepth 4096 in
example : controls.all (fun control => control.2) = true := by decide

#eval controls

/-- A nonempty route with Base→static reentry. The covered relay is the only
    omitted input dependency; every productive input is derived from a seed. -/
def b1 : CallerOwnedRequestRouting.Binding :=
  ⟨⟨3,.staticField 44⟩,⟨4,.base 8⟩,⟨.star,1,100,101⟩,false⟩
def b2 : CallerOwnedRequestRouting.Binding :=
  ⟨⟨4,.base 8⟩,⟨5,.staticField 45⟩,⟨.star,2,200,202⟩,false⟩
def b3 : CallerOwnedRequestRouting.Binding :=
  ⟨⟨5,.staticField 45⟩,⟨6,.staticField 45⟩,⟨.star,3,300,303⟩,true⟩
def b4 : CallerOwnedRequestRouting.Binding :=
  ⟨⟨6,.staticField 45⟩,⟨2,.base 7⟩,⟨.star,4,400,404⟩,false⟩
def routeWorld : CallerOwnedRequestRouting.World := ⟨[b1,b2,b3,b4],[(6,5)]⟩
def sourceNeed : CallerOwnedRequestRouting.Request := ⟨⟨3,.staticField 44⟩,5⟩
def sourceInitial : Initial := ⟨3,.abstractField 0 44,1⟩
def sourceFact : Frame := ⟨3,0,{payload with
  kind := .flow
  original := 100
  base := .field 0 44
  marks := []
  layer := .demand}⟩
def sourceClause : LocalClause := ⟨[sourceFact],[],.request sourceNeed⟩
def bindingRow (binding : CallerOwnedRequestRouting.Binding) : Input :=
  {row with
    port := {row.port with
      caller := binding.target.owner
      callee := binding.origin.owner
      statement := binding.descriptor.fullPort}
    value := {payload with
      kind := .flow
      original := binding.descriptor.payload
      premiseTag := (match binding.descriptor.premiseTag with | .exact => 0 | .star => 1)
      base := (match binding.origin.slot with | .base b => .nonstatic b | .staticField f => .field 0 f)
      marks := []
      layer := .demand
      support := [binding.descriptor.support]}
    presence := {present with owner := binding.origin.owner}}

def bindingSeed (binding : CallerOwnedRequestRouting.Binding) : Frame :=
  ⟨binding.target.owner,binding.descriptor.fullPort,(bindingRow binding).value⟩

theorem binding_row_full_snapshot (binding : CallerOwnedRequestRouting.Binding) :
    (bindingRow binding).port.caller = binding.target.owner ∧
    (bindingRow binding).port.callee = binding.origin.owner ∧
    (bindingRow binding).port.statement = binding.descriptor.fullPort ∧
    (bindingRow binding).value.original = binding.descriptor.payload ∧
    (bindingRow binding).value.support = [binding.descriptor.support] := by
  exact ⟨rfl,rfl,rfl,rfl,rfl⟩

def routeProgram : Program := {program with
  world := routeWorld
  seeds := program.seeds ++ routeWorld.bindings.map bindingSeed
  callInputs := fun fact => program.callInputs fact ++
    ((routeWorld.bindings.filter (fun binding => bindingSeed binding == fact)).map bindingRow)
  bindingInput := bindingRow
  unconditional := fun input => program.unconditional input ++
    (if input = bindingRow b1 then [sourceInitial] else [])
  start := fun initial => program.start initial ++
    (if initial = sourceInitial then [sourceFact] else [])
  locals := [sourceClause,eventClause]}

theorem routeValid : CallerOwnedRequestRouting.WorldValid routeProgram.world := by
  intro binding member
  have cases : binding = b1 ∨ binding = b2 ∨ binding = b3 ∨ binding = b4 := by
    simpa [routeProgram,routeWorld] using member
  rcases cases with rfl | rfl | rfl | rfl <;> decide

def oldRoute : CallerOwnedRequestRouting.OrdinaryRoute routeWorld sourceNeed need :=
  ⟨rfl,.hop b1 (by decide) (.hop b2 (by decide) (.hop b3 (by decide)
    (.hop b4 (by decide) (.here need.node))))⟩

def routeInput : Ordinary routeProgram (.input row) :=
  .callInput (f := seed) (.seed (by decide)) (by decide)
def routeAbstract : Ordinary routeProgram (.fact absFact) :=
  .start (i := absInitial) (.project routeInput (by decide) (by decide)) (by decide) rfl

def sourceInputProof : Ordinary routeProgram (.input (bindingRow b1)) :=
  .callInput (f := bindingSeed b1) (.seed (by decide)) (by decide)

def sourceLocalFact : Ordinary routeProgram (.fact sourceFact) :=
  .start (i := sourceInitial) (.project sourceInputProof (by decide) (by decide))
    (by decide) rfl

def sourceProof : Ordinary routeProgram (.request sourceNeed) :=
  .local (clause := sourceClause) (by decide)
    (by intro f member; have equal : f = sourceFact := by simpa [sourceClause] using member
        subst f; exact sourceLocalFact)
    (by intro value member; exact False.elim (by simpa [sourceClause] using member))

def routeInputsDerived : ∀ binding ∈ CallerOwnedRequestRouting.ordinaryBindings oldRoute.path,
    Ordinary routeProgram (.input (routeProgram.bindingInput binding)) := by
  intro binding hb
  have member : binding ∈ routeWorld.bindings := by
    simpa [oldRoute,CallerOwnedRequestRouting.ordinaryBindings,routeWorld] using hb
  apply Ordinary.callInput (f := bindingSeed binding)
  · apply Ordinary.seed
    apply List.mem_append_right
    exact List.mem_map.mpr ⟨binding,member,rfl⟩
  · apply List.mem_append_right
    exact List.mem_map.mpr ⟨binding,List.mem_filter.mpr ⟨member,by simp⟩,rfl⟩

def routedNeed : Ordinary routeProgram (.request need) :=
  .route sourceProof oldRoute routeInputsDerived

def routedTaint : Ordinary routeProgram (.fact taintFact) :=
  .start (i := taintInitial) (.requested routeInput routedNeed (by decide) (by decide))
    (by decide) rfl

def routedSummary : Ordinary routeProgram (.summary summary) :=
  .publish (clause := publishClause) (by decide)
    (by intro f member; have equal : f = taintFact := by simpa [publishClause] using member
        subst f; exact routedTaint)
    (by intro value member; have equal : value = present := by simpa [publishClause] using member
        subst value; exact .presence routeInput)

def callerRoutedSummary : Caller routeProgram (.summary summary) :=
  promote routeValid routedSummary

/-- A separate generic one-premise effect clause emits a request with no fact;
    it is not the universal default. Its DEMAND-only invocation is sufficient. -/
def effectSummary : Summary := ⟨2,[20],{payload with mx := [8], layer := .demand}⟩
def effectClause : PublishClause := ⟨[],[present],effectSummary⟩
def noFactProgram : Program := {presenceOnly with
  publish := [effectClause]
  apply := fun _ _ _ => [.request excludedNeed]}

def noFactSummary : Ordinary noFactProgram (.summary effectSummary) :=
  .publish (clause := effectClause) (by decide)
    (by intro f member; exact False.elim (by simpa [effectClause] using member))
    (by intro value member; have equal : value = present := by simpa [effectClause] using member
        subst value; exact .presence (row := row)
          (.callInput (f := seed) (.seed (by decide)) (by decide)))

def applicationNeedWithoutFact : Ordinary noFactProgram (.request excludedNeed) :=
  .apply (port := port) (rows := [row]) (output := .request excludedNeed) noFactSummary
    (by intro input member; have equal : input = row := by simpa using member
        subst input; exact .callInput (f := seed) (.seed (by decide)) (by decide))
    (.presence (row := row) (.callInput (f := seed) (.seed (by decide)) (by decide)))
    rfl rfl (by decide) (by decide)

def routingControls : List (String × Bool) := [
  ("mixed_Base_static_reentry_exact_productive_bindings",
    CallerOwnedRequestRouting.productiveBindings
      (CallerOwnedRequestRouting.compress routeValid oldRoute.path) == [b1,b2,b4]),
  ("whole_binding_port_payload_support_kept",
    (CallerOwnedRequestRouting.productiveBindings
      (CallerOwnedRequestRouting.compress routeValid oldRoute.path)).map
      CallerOwnedRequestRouting.Binding.descriptor == [b1.descriptor,b2.descriptor,b4.descriptor]),
  ("application_need_no_surviving_fact",
    noFactProgram.apply port [row] effectSummary == [.request excludedNeed]),
  ("DEMAND_only_presence_initialized", present.demandInput),
  ("D_caller_history_retained_but_callee_relative_NORMAL",
    port.callerLayer == .demand && initialLayer taintInitial == .normal)]

example : routingControls.all (fun control => control.2) = true := by decide
#eval routingControls

end Witness

/- Adapter boundary: this checked inclusion concerns the finite semantic
   calculus above, including recursion/ND/events. It does NOT prove AP or JVM
   constructor completeness, injectivity of opaque IDs, Kotlin storage
   subsumption, canonical broad/class normalization, field-limit cuts, exact
   local/application kernels, fair counted work scheduling or tuple-index
   realization. These are explicit implementation conformance obligations.
   World.bindings is only an inventory: every route's actual productive input
   has a derivation. No top-level premise says all summaries are covered.
   The tuple/pair lists and proof trees are executable ghost oracles, not a
   proposal to materialize Cartesian products or histories at runtime. -/

end ApSpec.CallerOwnedSummaryCoverage
