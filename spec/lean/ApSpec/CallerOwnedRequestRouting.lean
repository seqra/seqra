/-
  Constructive ordinary-to-caller-owned request coverage.

  Owners encode FULL method/entry/context keys. Bindings are actual ordinary
  input dependencies, not prescan calls. Only explicitly covered same-field
  static relays may be contracted. A fixed-CG path records their coverage;
  productive bindings (including changed fields and Base/static reentry)
  remain exact objects with their original descriptor/port/support snapshot.

  Canonical static descriptors arrive before this boundary. This model has
  no root/class normalization, no new initial shape, and no retagging; ANY
  current leaves have already acquired their selected STAR premise tag.

  This proves finite-path request coverage, not actual JVM binding/coverage
  validity, request truth, whole summary soundness or scheduling termination.
  CG coverage may add interests, so no reverse equality theorem is claimed.
-/
import ApSpec.IncrementalRequestTable

namespace ApSpec.CallerOwnedRequestRouting

inductive Slot where
  | base (id : Nat)
  | staticField (id : Nat)
  deriving Repr, DecidableEq

structure Node where
  owner : Nat
  slot : Slot
  deriving Repr, DecidableEq

inductive PremiseTag where
  | exact
  | star
  deriving Repr, DecidableEq

/-- Opaque lossless canonical snapshot. These IDs stand for complete existing
    owner-held objects, not lossy hashes or a second payload store. `fullPort`
    identifies structural callsite coordinates; each member's caller layer
    remains in its lossless payload/support snapshot, so ordinary ND matching
    can combine NORMAL and DEMAND members at the same structural callsite. -/
structure Descriptor where
  premiseTag : PremiseTag
  fullPort : Nat
  payload : Nat
  support : Nat
  deriving Repr, DecidableEq

structure Binding where
  origin : Node
  target : Node
  descriptor : Descriptor
  coveredRelay : Bool
  deriving Repr, DecidableEq

structure World where
  bindings : List Binding
  calls : List (Nat × Nat)
  deriving Repr

def SameField (binding : Binding) : Prop :=
  match binding.origin.slot,binding.target.slot with
  | .staticField f,.staticField g => f = g
  | _,_ => False

instance (binding : Binding) : Decidable (SameField binding) := by
  unfold SameField
  split <;> infer_instance

def Contractible (binding : Binding) : Prop :=
  binding.coveredRelay = true ∧ SameField binding

instance (binding : Binding) : Decidable (Contractible binding) :=
  inferInstanceAs (Decidable (binding.coveredRelay = true ∧ SameField binding))

/-- Request direction is callee -> caller. Calls have the opposite direction. -/
def WorldValid (world : World) : Prop :=
  ∀ binding, binding ∈ world.bindings → Contractible binding →
    (binding.target.owner,binding.origin.owner) ∈ world.calls

inductive CallPath (world : World) : Nat → Nat → Type where
  | here (owner : Nat) : CallPath world owner owner
  | hop {origin via target : Nat} (edge : (origin,via) ∈ world.calls)
      (rest : CallPath world via target) : CallPath world origin target

def CallPath.append {world : World} {origin via target : Nat}
    (first : CallPath world origin via) (rest : CallPath world via target) :
    CallPath world origin target :=
  match first with
  | .here _ => rest
  | .hop edge tail => .hop edge (tail.append rest)

/-- A shared callee/field view can represent all same-field intermediate
    request states by fixed-CG coverage, without retaining marks at relays. -/
inductive Coverage (world : World) : Node → Node → Type where
  | here (node : Node) : Coverage world node node
  | field (field sourceOwner entryOwner : Nat)
      (path : CallPath world entryOwner sourceOwner) :
      Coverage world ⟨sourceOwner,.staticField field⟩ ⟨entryOwner,.staticField field⟩

def Coverage.append {world : World} {origin via target : Node}
    (first : Coverage world origin via) (rest : Coverage world via target) :
    Coverage world origin target := by
  cases first with
  | here _ => exact rest
  | field f source entry calls =>
    cases rest with
    | here _ => exact .field f source entry calls
    | field _ _ target more => exact .field f source target (more.append calls)

def covered_binding {world : World} (valid : WorldValid world) (binding : Binding)
    (actual : binding ∈ world.bindings) (covered : Contractible binding) :
    Coverage world binding.origin binding.target := by
  have edge := valid binding actual covered
  have same := covered.2
  rcases binding with ⟨⟨sourceOwner,sourceSlot⟩,⟨entryOwner,entrySlot⟩,descriptor,flag⟩
  cases sourceSlot with
  | base b => cases same
  | staticField f =>
    cases entrySlot with
    | base b => cases same
    | staticField g =>
      have equal : f = g := same
      cases equal
      exact .field f sourceOwner entryOwner (.hop edge (.here sourceOwner))

inductive OrdinaryPath (world : World) : Node → Node → Type where
  | here (node : Node) : OrdinaryPath world node node
  | hop (binding : Binding) (actual : binding ∈ world.bindings)
      {target : Node} (rest : OrdinaryPath world binding.target target) :
      OrdinaryPath world binding.origin target

/-- Each hop is a caller-owned view application: source need reaches the
    actual callee entry through same-field coverage, then follows one retained
    full input binding. No endpoint transitive closure is part of this type. -/
inductive OptimizedPath (world : World) : Node → Node → Type where
  | finish {origin target : Node} (covered : Coverage world origin target) :
      OptimizedPath world origin target
  | hop {origin target : Node} (binding : Binding)
      (covered : Coverage world origin binding.origin)
      (actual : binding ∈ world.bindings) (productive : ¬Contractible binding)
      (rest : OptimizedPath world binding.target target) : OptimizedPath world origin target

def prependCoverage {world : World} {origin via target : Node}
    (covered : Coverage world origin via) (path : OptimizedPath world via target) :
    OptimizedPath world origin target := by
  cases path with
  | finish final => exact .finish (covered.append final)
  | hop binding segment actual productive rest =>
    exact .hop binding (covered.append segment) actual productive rest

/-- Executable ordinary-path contraction. Recursion follows the supplied
    finite witness, so recursive call SCCs need no acyclicity premise. -/
def compress {world : World} (valid : WorldValid world) {source target : Node}
    (path : OrdinaryPath world source target) : OptimizedPath world source target :=
  match path with
  | .here node => .finish (.here node)
  | .hop binding actual rest =>
    if covered : Contractible binding then
      prependCoverage (covered_binding valid binding actual covered) (compress valid rest)
    else .hop binding (.here binding.origin) actual covered (compress valid rest)

def ordinaryBindings {world : World} {source target : Node} :
    OrdinaryPath world source target → List Binding
  | .here _ => []
  | .hop binding _ rest => binding :: ordinaryBindings rest

def productiveBindings {world : World} {source target : Node} :
    OptimizedPath world source target → List Binding
  | .finish _ => []
  | .hop binding _ _ _ rest => binding :: productiveBindings rest

theorem prepend_keeps_productive_bindings {world : World} {source via target : Node}
    (covered : Coverage world source via) (path : OptimizedPath world via target) :
    productiveBindings (prependCoverage covered path) = productiveBindings path := by
  cases path <;> rfl

theorem compression_preserves_exact_productive_bindings {world : World}
    (valid : WorldValid world) {source target : Node} (path : OrdinaryPath world source target) :
    productiveBindings (compress valid path) =
      (ordinaryBindings path).filter (fun binding => decide (¬Contractible binding)) := by
  induction path with
  | here _ => rfl
  | hop binding actual rest ih =>
    by_cases covered : Contractible binding
    · simp only [compress,dif_pos,prepend_keeps_productive_bindings,
        ordinaryBindings,List.filter_cons,covered,not_true_eq_false,decide_false,
        Bool.false_eq_true,if_false]
      exact ih
    · simp only [compress,dif_neg,productiveBindings,ordinaryBindings,
        List.filter_cons,covered,not_false_eq_true,decide_true,if_true]
      exact congrArg (List.cons binding) ih

theorem no_base_endpoint_from_coverage_alone {world : World} (source : Node)
    (owner base : Nat) (covered : Coverage world source ⟨owner,.base base⟩) :
    source = ⟨owner,.base base⟩ := by
  cases covered
  rfl

/-- Concrete entry-ancestry witness required by shared-view admission: an
    ACTUAL retained productive input, a same-field source/entry coverage path,
    and the remaining optimized route. No synthetic entry or CG-only input
    can supply this record. Runtime producers must retain the same witnesses. -/
structure EntryAncestryCertificate (world : World) (source endpoint : Node) where
  binding : Binding
  coverage : Coverage world source binding.origin
  actual : binding ∈ world.bindings
  productive : ¬Contractible binding
  continuation : OptimizedPath world binding.target endpoint

/-- Every ordinary static-to-Base path yields its first productive actual
    entry witness. Pure same-field cycles alone cannot create a Base endpoint. -/
def static_base_entry_ancestry {world : World} (valid : WorldValid world)
    (sourceOwner field owner base : Nat)
    (path : OrdinaryPath world ⟨sourceOwner,.staticField field⟩ ⟨owner,.base base⟩) :
    EntryAncestryCertificate world ⟨sourceOwner,.staticField field⟩ ⟨owner,.base base⟩ := by
  cases compress valid path with
  | finish covered =>
    have impossible := no_base_endpoint_from_coverage_alone _ owner base covered
    have slots := congrArg Node.slot impossible
    cases slots
  | hop binding covered actual productive rest =>
    exact ⟨binding,covered,actual,productive,rest⟩

structure Request where
  node : Node
  mark : Nat
  deriving Repr, DecidableEq

structure OrdinaryRoute (world : World) (source target : Request) where
  sameMark : target.mark = source.mark
  path : OrdinaryPath world source.node target.node

structure OptimizedRoute (world : World) (source target : Request) where
  sameMark : target.mark = source.mark
  path : OptimizedPath world source.node target.node

def compressRequestRoute {world : World} (valid : WorldValid world)
    {source target : Request} (ordinary : OrdinaryRoute world source target) :
    OptimizedRoute world source target := ⟨ordinary.sameMark,compress valid ordinary.path⟩

/-- Same full owner/base and SAME mark at the endpoint, with an actual
    optimized witness. It neither fabricates a mark nor asserts a supplier. -/
def every_base_endpoint_covered {world : World} (valid : WorldValid world)
    (source : Request) (owner base : Nat)
    (ordinary : OrdinaryRoute world source ⟨⟨owner,.base base⟩,source.mark⟩) :
    OptimizedRoute world source ⟨⟨owner,.base base⟩,source.mark⟩ :=
  compressRequestRoute valid ordinary

/-- Exact descriptors, premise tags, port and support objects on retained
    productive inputs are unchanged by request contraction. -/
theorem canonical_descriptors_pass_unchanged {world : World} (valid : WorldValid world)
    {source target : Node} (path : OrdinaryPath world source target) :
    (productiveBindings (compress valid path)).map Binding.descriptor =
      ((ordinaryBindings path).filter (fun binding => decide (¬Contractible binding))).map
        Binding.descriptor := by
  rw [compression_preserves_exact_productive_bindings]

/-- The grow-only P/A protocol is used at each CALLER view, not once per
    reader/entry pair. A candidate is one exact retained productive input ref;
    processed marks union all reader source contributions at that view. -/
theorem caller_view_both_arrival_orders
    (events : List (IncrementalRequestTable.Event Nat Binding)) (mark : Nat) (binding : Binding) :
    (mark,binding) ∈ (IncrementalRequestTable.drain
      (IncrementalRequestTable.run IncrementalRequestTable.empty events)).delivered ↔
      mark ∈ IncrementalRequestTable.offeredRequests events ∧
      binding ∈ IncrementalRequestTable.offeredCandidates events :=
  IncrementalRequestTable.all_arrival_orders_exact events mark binding

theorem caller_view_no_pair_twice
    (events : List (IncrementalRequestTable.Event Nat Binding)) :
    (IncrementalRequestTable.run IncrementalRequestTable.empty events).delivered.Nodup :=
  IncrementalRequestTable.no_pair_delivered_twice events

namespace Pipeline
open IncrementalRequestTable

/-- Fixed prescan eligibility. Completeness is stated on coverage witnesses,
    not on fabricated facts. Additional true answers may widen interest. -/
def GateComplete (world : World) (eligible : Request → Node → Bool) : Prop :=
  ∀ source view, Coverage world source.node view → eligible source view = true

/-- Structural callsite subscription identity. It refers to existing ordinary
    owners; the opaque port includes full method/entry/context, call statement
    and call-plan stage coordinates. Each member's caller layer stays in its
    Descriptor payload/support snapshot, rather than splitting subscriptions
    that ordinary mixed NORMAL/DEMAND ND matching must combine. -/
structure Subscriber where
  fullPort : Nat
  owner : Nat
  deriving Repr, DecidableEq

/-- Runtime producer certificate: this complete retained input really belongs
    to this view and full caller subscription. This is stronger than a field
    or method-name match. Binding validity itself remains a frontend premise. -/
def ActualSubscriberInput (world : World) (view : Node) (subscriber : Subscriber)
    (binding : Binding) : Prop :=
  binding ∈ world.bindings ∧ binding.origin = view ∧
  binding.descriptor.fullPort = subscriber.fullPort ∧ binding.target.owner = subscriber.owner

def finishTable {M C : Type} [DecidableEq M] [DecidableEq C]
    (events : List (Event M C)) : State M C := drain (run empty events)

def sourcePairs (events : List (Event Request Node)) : List (Request × Node) :=
  (finishTable events).delivered

/-- Union-by-view input batch, computed from successful source/view joins.
    Equal marks from DIFFERENT sources are later merged by the view's P/A. -/
def marksAtView (eligible : Request → Node → Bool) (view : Node)
    (delivered : List (Request × Node)) : List Nat :=
  (delivered.filter fun pair => decide (pair.2 = view) && eligible pair.1 pair.2).map
    (fun pair => pair.1.mark)

theorem source_projection_has_mark (eligible : Request → Node → Bool)
    (view : Node) (delivered : List (Request × Node)) (source : Request)
    (joined : (source,view) ∈ delivered) (qualified : eligible source view = true) :
    source.mark ∈ marksAtView eligible view delivered := by
  apply List.mem_map.mpr
  refine ⟨(source,view),?_,rfl⟩
  exact List.mem_filter.mpr ⟨joined,by simp [qualified]⟩

/-- Candidate registration may precede or follow the incoming batch. Repeated
    candidates and repeated marks are allowed. All intermediate states are
    computed by the same table protocol, not an endpoint-membership oracle. -/
def joinEvents {C : Type} (marks : List Nat) (before after : List C) : List (Event Nat C) :=
  [.candidates before,.requests marks,.candidates after]

theorem split_arrival_join_exact {C : Type} [DecidableEq C]
    (marks : List Nat) (before after : List C) (mark : Nat) (candidate : C) :
    (mark,candidate) ∈ (finishTable (joinEvents marks before after)).delivered ↔
      mark ∈ marks ∧ (candidate ∈ before ∨ candidate ∈ after) := by
  rw [show (mark,candidate) ∈ (finishTable (joinEvents marks before after)).delivered ↔
      mark ∈ offeredRequests (joinEvents marks before after) ∧
      candidate ∈ offeredCandidates (joinEvents marks before after) from
        all_arrival_orders_exact _ _ _]
  simp [joinEvents,offeredRequests,offeredCandidates]

def viewPairs (sourceEvents : List (Event Request Node))
    (eligible : Request → Node → Bool) (view : Node)
    (before after : List Subscriber) : List (Nat × Subscriber) :=
  (finishTable (joinEvents (marksAtView eligible view (sourcePairs sourceEvents)) before after)).delivered

def marksAtSubscriber (subscriber : Subscriber) (delivered : List (Nat × Subscriber)) : List Nat :=
  (delivered.filter fun pair => decide (pair.2 = subscriber)).map Prod.fst

theorem subscriber_projection_has_mark (subscriber : Subscriber)
    (delivered : List (Nat × Subscriber)) (mark : Nat)
    (joined : (mark,subscriber) ∈ delivered) :
    mark ∈ marksAtSubscriber subscriber delivered := by
  apply List.mem_map.mpr
  refine ⟨(mark,subscriber),?_,rfl⟩
  exact List.mem_filter.mpr ⟨joined,by simp⟩

def inputPairs (sourceEvents : List (Event Request Node))
    (eligible : Request → Node → Bool) (view : Node)
    (beforeSubscribers afterSubscribers : List Subscriber) (subscriber : Subscriber)
    (beforeInputs afterInputs : List Binding) : List (Nat × Binding) :=
  let delivered := viewPairs sourceEvents eligible view beforeSubscribers afterSubscribers
  (finishTable (joinEvents (marksAtSubscriber subscriber delivered) beforeInputs afterInputs)).delivered

/-- Three-stage composition, with an executable computed batch at BOTH
    handoffs. No assumption says the final endpoint was already delivered.
    Source/view order is arbitrary; subscriber/input can occur on either side
    of their producer batch. Runtime fairness/concurrency is a separate bridge. -/
theorem source_view_callsite_input_coverage
    {world : World} (eligible : Request → Node → Bool) (complete : GateComplete world eligible)
    (sourceEvents : List (Event Request Node)) (source : Request) (view : Node)
    (covered : Coverage world source.node view)
    (sourceRegistered : source ∈ offeredRequests sourceEvents)
    (viewRegistered : view ∈ offeredCandidates sourceEvents)
    (beforeSubscribers afterSubscribers : List Subscriber) (subscriber : Subscriber)
    (subscribed : subscriber ∈ beforeSubscribers ∨ subscriber ∈ afterSubscribers)
    (beforeInputs afterInputs : List Binding) (binding : Binding)
    (inputRegistered : binding ∈ beforeInputs ∨ binding ∈ afterInputs) :
    (source.mark,binding) ∈ inputPairs sourceEvents eligible view
      beforeSubscribers afterSubscribers subscriber beforeInputs afterInputs := by
  have first : (source,view) ∈ sourcePairs sourceEvents :=
    (all_arrival_orders_exact sourceEvents source view).mpr ⟨sourceRegistered,viewRegistered⟩
  have viewMark : source.mark ∈ marksAtView eligible view (sourcePairs sourceEvents) :=
    source_projection_has_mark eligible view _ source first (complete source view covered)
  have second : (source.mark,subscriber) ∈ viewPairs sourceEvents eligible view
      beforeSubscribers afterSubscribers :=
    (split_arrival_join_exact _ _ _ _ _).mpr ⟨viewMark,subscribed⟩
  have callsiteMark := subscriber_projection_has_mark subscriber _ source.mark second
  exact (split_arrival_join_exact _ _ _ _ _).mpr ⟨callsiteMark,inputRegistered⟩

/-- The actual input endpoint is kept, with its exact caller-original node
    and mark. No other input port/premise can be substituted. -/
def deliver (mark : Nat) (binding : Binding) : Request := ⟨binding.target,mark⟩

theorem delivered_request_key (source : Request) (binding : Binding) :
    (deliver source.mark binding).node = binding.target ∧
    (deliver source.mark binding).mark = source.mark := ⟨rfl,rfl⟩

structure CertifiedDelivery (world : World) (source : Request) (binding : Binding)
    (pairs : List (Nat × Binding)) where
  delivered : (source.mark,binding) ∈ pairs
  route : OptimizedRoute world source (deliver source.mark binding)

/-- An actual productive binding + same-field entry-ancestry coverage gives
    BOTH a computed pipeline delivery and an optimized route with the exact
    same caller-original key/mark. Certificates never create concrete truth. -/
def certified_pipeline_hop
    {world : World} (eligible : Request → Node → Bool) (complete : GateComplete world eligible)
    (sourceEvents : List (Event Request Node)) (source : Request) (view : Node)
    (covered : Coverage world source.node view)
    (sourceRegistered : source ∈ offeredRequests sourceEvents)
    (viewRegistered : view ∈ offeredCandidates sourceEvents)
    (beforeSubscribers afterSubscribers : List Subscriber) (subscriber : Subscriber)
    (subscribed : subscriber ∈ beforeSubscribers ∨ subscriber ∈ afterSubscribers)
    (beforeInputs afterInputs : List Binding) (binding : Binding)
    (inputRegistered : binding ∈ beforeInputs ∨ binding ∈ afterInputs)
    (actual : ActualSubscriberInput world view subscriber binding)
    (productive : ¬Contractible binding) :
    CertifiedDelivery world source binding (inputPairs sourceEvents eligible view
      beforeSubscribers afterSubscribers subscriber beforeInputs afterInputs) := by
  refine ⟨source_view_callsite_input_coverage eligible complete sourceEvents source view covered
    sourceRegistered viewRegistered beforeSubscribers afterSubscribers subscriber subscribed
    beforeInputs afterInputs binding inputRegistered,?_⟩
  refine ⟨rfl,.hop binding ?_ actual.1 productive (.finish (.here binding.target))⟩
  rw [actual.2.1]
  exact covered

end Pipeline

namespace Controls

def n0 : Node := ⟨30,.staticField 7⟩
def n1 : Node := ⟨20,.staticField 7⟩
def n2 : Node := ⟨10,.staticField 8⟩
def n3 : Node := ⟨5,.base 1⟩
def n4 : Node := ⟨8,.staticField 9⟩
def n5 : Node := ⟨1,.base 0⟩
def snapshot (port : Nat) : Descriptor := ⟨.star,port,100+port,200+port⟩
def e0 : Binding := ⟨n0,n1,snapshot 0,true⟩
def cycle : Binding := ⟨n1,n0,snapshot 1,true⟩
def e1 : Binding := ⟨n1,n2,snapshot 2,true⟩
def e2 : Binding := ⟨n2,n3,snapshot 3,false⟩
def e3 : Binding := ⟨n3,n4,snapshot 4,false⟩
def e4 : Binding := ⟨n4,n5,snapshot 5,false⟩
def world : World :=
  ⟨[e0,cycle,e1,e2,e3,e4],[(20,30),(30,20),(10,20),(5,10),(8,5),(1,8)]⟩

theorem valid : WorldValid world := by
  intro binding actual covered
  simp only [world,List.mem_cons,List.not_mem_nil,or_false] at actual
  rcases actual with rfl | rfl | rfl | rfl | rfl | rfl
  · decide
  · decide
  · cases (show ¬Contractible e1 by decide) covered
  · cases (show ¬Contractible e2 by decide) covered
  · cases (show ¬Contractible e3 by decide) covered
  · cases (show ¬Contractible e4 by decide) covered

def path : OrdinaryPath world n0 n5 :=
  .hop e0 (by decide) (.hop e1 (by decide) (.hop e2 (by decide)
    (.hop e3 (by decide) (.hop e4 (by decide) (.here n5)))))

def cyclicPath : OrdinaryPath world n0 n5 :=
  .hop e0 (by decide) (.hop cycle (by decide) path)

def uncovered : Binding := ⟨n0,n1,snapshot 6,false⟩
def source : Request := ⟨n0,42⟩
def subscriber : Pipeline.Subscriber := ⟨2,10⟩
def sourceFirst : List (IncrementalRequestTable.Event Request Node) :=
  [.requests [source],.candidates [n1]]
def viewFirst : List (IncrementalRequestTable.Event Request Node) :=
  [.candidates [n1],.requests [source]]
def eligible (_ : Request) (_ : Node) : Bool := true
theorem complete : Pipeline.GateComplete world eligible := fun _ _ _ => rfl

def sharedSubscriber : Pipeline.Subscriber := ⟨10,99⟩
def sharedBindings : List Binding := (List.range 4).map fun base =>
  ⟨n1,⟨99,.base base⟩,⟨.star,10,base,base⟩,false⟩
def sharedSources : List (IncrementalRequestTable.Event Request Node) :=
  [.requests ((List.range 4).map fun reader => ⟨⟨30+reader,.staticField 7⟩,42⟩),
    .candidates [n1]]

def checks : List Bool :=
  [ productiveBindings (compress valid path) == [e1,e2,e3,e4],
    productiveBindings (compress valid cyclicPath) == [e1,e2,e3,e4],
    decide (¬Contractible uncovered),
    decide (¬Contractible e1),
    ((productiveBindings (compress valid path)).map Binding.descriptor) ==
      [snapshot 2,snapshot 3,snapshot 4,snapshot 5],
    decide ((42,e1) ∈ Pipeline.inputPairs sourceFirst eligible n1
      [subscriber] [] subscriber [e1] []),
    decide ((42,e1) ∈ Pipeline.inputPairs viewFirst eligible n1
      [] [subscriber] subscriber [] [e1]),
    (Pipeline.deliver 42 e1).node == n2,
    (Pipeline.deliver 42 e1).mark == 42,
    (Pipeline.inputPairs sharedSources eligible n1 [sharedSubscriber] []
      sharedSubscriber sharedBindings []).length == 4 ]

theorem controls_pass : checks.all id = true := by decide

end Controls

end ApSpec.CallerOwnedRequestRouting
