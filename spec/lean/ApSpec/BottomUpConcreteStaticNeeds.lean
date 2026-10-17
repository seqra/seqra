/-
  Request-free static need routing. Actual abstract FLOW call bindings compile
  a finite graph; need marks are stored only at their originating event. The
  fast router returns typed nonstatic Base requests, never TAINT or hypotheses.
  Its bounded visited/path search is equivalent to expanded ordinary closure.

  Scope: finite scalar geometry and supplied, actually admitted call bindings.
  The Kotlin binding/graph compiler, owner queues, fairness and actual rule
  event inventory remain adapter obligations. Conservative interest is not
  condition truth; concrete replay still performs the ordinary clean/kill.
-/
import ApSpec.BottomUpStaticRequests
import ApSpec.BottomUpStaticProtocol

namespace ApSpec.BottomUpConcreteStaticNeeds
open ApSpec
set_option autoImplicit false

abbrev Owner := Run1Requests.Owner
abbrev Slot := Run1Requests.Slot
abbrev Port := BottomUpStaticProtocol.Port
abbrev Graph := Run1Requests.Graph

inductive Node where
  | staticField (owner : Owner) (cls field : Nat)
  | base (owner : Owner) (id : Nat)
  deriving DecidableEq, Repr

def originalNode (owner : Owner) : Slot → Node
  | .base b => .base owner b
  | .field c f => .staticField owner c f

inductive FlowTail where
  | star | any
  deriving DecidableEq, Repr

structure FlowGeometry where
  point : Run1Requests.Point
  tail : FlowTail
  fieldE : Excl := .empty
  mx : List Mark := []
  deriving DecidableEq, Repr

def bounded (a : FlowGeometry) : Bool := match a.point with
  | .local _ path => decide (path.length ≤ 1)
  | _ => true

/-- Both legal FLOW tails supply a broad ancestor. Neither E nor mx filters
    an interest; they still belong to the full actual binding. -/
def geometryMatches (a : FlowGeometry) (c f : Nat) : Bool := match a.point with
  | .root => true
  | .cls c' => c == c'
  | .field c' f' => c == c' && f == f'
  | .local _ _ => false

theorem matching_ignores_annotations (a : FlowGeometry) (c f : Nat)
    (tail : FlowTail) (e : Excl) (mx : List Mark) :
    geometryMatches {a with tail := tail,fieldE := e,mx := mx} c f =
      geometryMatches a c f := rfl

structure FlowBinding where
  port : Port
  original : Slot
  current : FlowGeometry
  deriving DecidableEq, Repr

/-- Concrete and ZERO inputs are disjoint constructors, not possible origins
    of a FLOW route. An absent/killed input contributes no binding. -/
inductive Incoming where
  | flow (binding : FlowBinding)
  | concrete (port : Port)
  | zero (port : Port)
  | killed
  deriving DecidableEq, Repr

def routes (binding : FlowBinding) (upstream downstream : Node) : Bool :=
  bounded binding.current && (upstream == originalNode binding.port.caller binding.original) &&
    match downstream with
    | .staticField owner c f => owner == binding.port.callee && geometryMatches binding.current c f
    | .base _ _ => false

def incomingRoutes : Incoming → Node → Node → Bool
  | .flow binding, up, down => routes binding up down
  | _, _, _ => false

@[simp] theorem killed_has_no_route (up down : Node) :
    incomingRoutes .killed up down = false := rfl
@[simp] theorem concrete_has_no_route (port : Port) (up down : Node) :
    incomingRoutes (.concrete port) up down = false := rfl
@[simp] theorem zero_has_no_route (port : Port) (up down : Node) :
    incomingRoutes (.zero port) up down = false := rfl

theorem route_original (binding : FlowBinding) (up down : Node)
    (h : routes binding up down = true) :
    up = originalNode binding.port.caller binding.original := by
  exact of_decide_eq_true ((Bool.and_eq_true _ _).mp ((Bool.and_eq_true _ _).mp h).1).2

theorem route_callee (binding : FlowBinding) (up : Node) (owner : Owner) (c f : Nat)
    (h : routes binding up (.staticField owner c f) = true) :
    owner = binding.port.callee ∧ geometryMatches binding.current c f = true := by
  exact ⟨of_decide_eq_true ((Bool.and_eq_true _ _).mp ((Bool.and_eq_true _ _).mp h).2).1,
    ((Bool.and_eq_true _ _).mp ((Bool.and_eq_true _ _).mp h).2).2⟩

theorem route_base_owner (binding : FlowBinding) (owner : Owner) (b : Nat) (down : Node)
    (h : routes binding (.base owner b) down = true) :
    owner = binding.port.caller ∧ binding.original = .base b := by
  have original := route_original binding _ _ h
  cases hslot : binding.original with
  | field c f => simp [hslot,originalNode] at original
  | base b' =>
    simp only [hslot,originalNode,Node.base.injEq] at original
    exact ⟨original.1,congrArg Run1Requests.Slot.base original.2.symm⟩

def compile {n : Nat} (labels : Fin n → Node) (inputs : List Incoming) : Graph n :=
  (List.finRange n).flatMap fun up => (List.finRange n).filterMap fun down =>
    if inputs.any (fun input => incomingRoutes input (labels up) (labels down))
    then some (up,down) else none

theorem compiled_edge_exact {n : Nat} (labels : Fin n → Node) (inputs : List Incoming)
    (up down : Fin n) :
    (up,down) ∈ compile labels inputs ↔
      ∃ input ∈ inputs, incomingRoutes input (labels up) (labels down) = true := by
  simp [compile,List.mem_filterMap,List.any_eq_true]

theorem compiled_base_is_endpoint {n : Nat} (labels : Fin n → Node) (inputs : List Incoming)
    (up down : Fin n) (owner : Owner) (b : Nat) (label : labels down = .base owner b) :
    (up,down) ∉ compile labels inputs := by
  intro edge
  obtain ⟨input,_,matched⟩ := (compiled_edge_exact _ _ _ _).mp edge
  cases input <;> simp [incomingRoutes,routes,label] at matched

structure BaseRequest where
  owner : Owner
  base : Nat
  mark : Mark
  deriving DecidableEq, Repr

def BaseRequest.question (request : BaseRequest) : Run1Requests.Question :=
  ⟨request.owner,.base request.base,request.mark⟩

@[simp] theorem only_base_request (request : BaseRequest) :
    request.question.slot = .base request.base := rfl

structure StaticNeedSource where
  owner : Owner
  cls : Nat
  field : Nat
  marks : List Mark
  deriving DecidableEq, Repr

inductive LocalNeed where
  | base (owner : Owner) (id : Nat) (marks : List Mark)
  | staticField (source : StaticNeedSource)
  | none
  deriving DecidableEq, Repr

/-- The actual original decides the partition; neither current geometry,
    old B nor a same-leaf test can suppress this already-emitted interest. -/
def produce (owner : Owner) (original : BottomUpStaticProtocol.Origin)
    (_current : FlowGeometry) (marks : List Mark) (_oldB : List Mark) : LocalNeed :=
  match original with
  | .flow (.base b) => .base owner b marks
  | .flow (.field c f) => .staticField ⟨owner,c,f,marks⟩
  | _ => .none

theorem producer_base (owner : Owner) (b : Nat) (current : FlowGeometry) (marks oldB : List Mark) :
    produce owner (.flow (.base b)) current marks oldB = .base owner b marks := rfl
theorem producer_static (owner : Owner) (c f : Nat) (current : FlowGeometry) (marks oldB : List Mark) :
    produce owner (.flow (.field c f)) current marks oldB = .staticField ⟨owner,c,f,marks⟩ := rfl
theorem producer_concrete_none (owner : Owner) (members : List PFact)
    (current : FlowGeometry) (marks oldB : List Mark) :
    produce owner (.concrete members) current marks oldB = .none := rfl
theorem producer_zero_none (owner : Owner) (current : FlowGeometry) (marks oldB : List Mark) :
    produce owner .zero current marks oldB = .none := rfl

def baseMatches (a : FlowGeometry) (b : Nat) : Bool := match a.point with
  | .local b' _ => b == b'
  | _ => false

/-- An ordinary Base request can map back to a static original. Reenter the
    fast graph there; do not insert a StaticField row into ordinary R/P. -/
def baseRequestReentry (binding : FlowBinding) (request : BaseRequest) : Option (Node × Mark) :=
  if request.owner == binding.port.callee && bounded binding.current &&
      baseMatches binding.current request.base then
    some (originalNode binding.port.caller binding.original,request.mark)
  else none

theorem base_request_reenters_static (port : Port) (c f b t : Nat) (tail : FlowTail)
    (e : Excl) (mx : List Mark) :
    baseRequestReentry ⟨port,.field c f,⟨.local b [],tail,e,mx⟩⟩ ⟨port.callee,b,t⟩ =
      some (.staticField port.caller c f,t) := by
  have owner : (port.callee == port.callee) = true := decide_eq_true rfl
  have base : (b == b) = true := decide_eq_true rfl
  change (if (port.callee == port.callee) && true && (b == b) then
    some (Node.staticField port.caller c f,t) else none) = _
  rw [owner,base]
  rfl

def emitAt (label : Node) (marks : List Mark) : List BaseRequest := match label with
  | .base owner b => marks.map fun t => ⟨owner,b,t⟩
  | .staticField _ _ _ => []

theorem mem_emitAt (label : Node) (marks : List Mark) (request : BaseRequest) :
    request ∈ emitAt label marks ↔
      label = .base request.owner request.base ∧ request.mark ∈ marks := by
  cases label <;> cases request <;> simp [emitAt,and_assoc,and_comm]

/-- IDs normally enumerate normalized (full owner, original field) sources.
    The oracle also accepts unnormalized IDs; same_origin_union below proves
    that merging such source rows changes no output membership. No mark set
    is stored at visited intermediate nodes. -/
abbrev SourceNeeds (m : Nat) := Fin m → List Mark

def register {m : Nat} (needs : SourceNeeds m) (source : Fin m)
    (marks : List Mark) (_oldB : List Mark) : SourceNeeds m := fun s =>
  if s = source then Run1RequestProtocol.merge marks (needs s) else needs s

@[simp] theorem registered_marks {m : Nat} (needs : SourceNeeds m) (source : Fin m)
    (marks oldB : List Mark) (t : Mark) :
    t ∈ register needs source marks oldB source ↔ t ∈ marks ∨ t ∈ needs source := by
  simp [register]

theorem no_catalog_suppression {m : Nat} (needs : SourceNeeds m) (source : Fin m)
    (marks oldB otherB : List Mark) :
    register needs source marks oldB = register needs source marks otherB := rfl

theorem unchanged_canonical_producer_registers {m : Nat} (needs : SourceNeeds m)
    (source : Fin m) (t : Mark) (oldB : List Mark) :
    t ∈ register needs source [t] oldB source := by simp

def collect {n m : Nat} (labels : Fin n → Node) (needs : SourceNeeds m)
    (visited : Fin m → List (Fin n)) : List BaseRequest :=
  Run1RequestProtocol.merge ((List.finRange m).flatMap fun source =>
    (visited source).flatMap fun node => emitAt (labels node) (needs source)) []

theorem mem_collect {n m : Nat} (labels : Fin n → Node) (needs : SourceNeeds m)
    (visited : Fin m → List (Fin n)) (request : BaseRequest) :
    request ∈ collect labels needs visited ↔
      ∃ source node, node ∈ visited source ∧
        labels node = .base request.owner request.base ∧ request.mark ∈ needs source := by
  simp [collect,mem_emitAt]

def fastNodes {n : Nat} (g : Graph n) (source : Fin n) : List (Fin n) :=
  let paths := (BottomUpStaticRequests.callClosure g source).entries
  (List.finRange n).filter fun node => (BottomUpStaticRequests.findPath paths node).isSome

/-- Independent exponential finite-set oracle versus bounded visited search. -/
theorem closure_iff_path {n : Nat} (g : Graph n) (source node : Fin n) :
    node ∈ Run1Requests.closure g source ↔
      (BottomUpStaticRequests.inheritedPath g source node).isSome = true := by
  let found := fastNodes g source
  have member (x : Fin n) : x ∈ found ↔
      (BottomUpStaticRequests.inheritedPath g source x).isSome = true := by
    simp [found,fastNodes,BottomUpStaticRequests.inheritedPath]
  constructor
  · intro included
    have closed : Run1Requests.closed g source found = true := by
      apply (Bool.and_eq_true _ _).mpr
      refine ⟨?_,?_⟩
      · apply decide_eq_true
        exact (member source).mpr (BottomUpStaticRequests.inheritedPath_seed g source)
      · apply List.all_eq_true.mpr
        intro edge present
        apply decide_eq_true
        intro callee
        exact (member edge.1).mpr (BottomUpStaticRequests.inheritedPath_call g source _ _ present
          ((member edge.2).mp callee))
    exact (member node).mp (Run1Requests.closure_least g source found closed node included)
  · intro path
    exact Run1Requests.recursive_call_coverage g source node
      ((BottomUpStaticRequests.inheritedPath_exact g source node).mp path)

theorem fastNodes_eq_closure {n : Nat} (g : Graph n) (source : Fin n) :
    fastNodes g source = Run1Requests.closure g source := by
  apply List.filter_congr
  intro node _
  have h := closure_iff_path g source node
  simp only [Run1Requests.closure,List.mem_filter,List.mem_finRange,true_and] at h
  have bool_eq (a b : Bool) (same : a = true ↔ b = true) : a = b := by
    cases a <;> cases b <;> simp_all
  exact (bool_eq _ _ h).symm

def ordinary {n m : Nat} (labels : Fin n → Node) (g : Graph n)
    (origins : Fin m → Fin n) (needs : SourceNeeds m) : List BaseRequest :=
  collect labels needs (fun source => Run1Requests.closure g (origins source))

def fast {n m : Nat} (labels : Fin n → Node) (g : Graph n)
    (origins : Fin m → Fin n) (needs : SourceNeeds m) : List BaseRequest :=
  collect labels needs (fun source => fastNodes g (origins source))

theorem fast_matches_expanded_requests {n m : Nat} (labels : Fin n → Node) (g : Graph n)
    (origins : Fin m → Fin n) (needs : SourceNeeds m) (request : BaseRequest) :
    request ∈ fast labels g origins needs ↔ request ∈ ordinary labels g origins needs := by
  simp only [fast,ordinary,mem_collect,fastNodes,List.mem_filter,List.mem_finRange,true_and]
  simp only [closure_iff_path,BottomUpStaticRequests.inheritedPath]

theorem fast_eq_ordinary {n m : Nat} (labels : Fin n → Node) (g : Graph n)
    (origins : Fin m → Fin n) (needs : SourceNeeds m) :
    fast labels g origins needs = ordinary labels g origins needs := by
  simp only [fast,ordinary,fastNodes_eq_closure]

theorem fast_nodup {n m : Nat} (labels : Fin n → Node) (g : Graph n)
    (origins : Fin m → Fin n) (needs : SourceNeeds m) :
    (fast labels g origins needs).Nodup := Run1RequestProtocol.merge_nodup _ [] (by simp)

theorem fast_witness {n m : Nat} (labels : Fin n → Node) (g : Graph n)
    (origins : Fin m → Fin n) (needs : SourceNeeds m) (request : BaseRequest) :
    request ∈ fast labels g origins needs ↔
      ∃ source node, labels node = .base request.owner request.base ∧ request.mark ∈ needs source ∧
        (BottomUpStaticRequests.inheritedPath g (origins source) node).isSome = true := by
  simp [fast,mem_collect,fastNodes,BottomUpStaticRequests.inheritedPath,and_assoc,and_left_comm,and_comm]

theorem no_invented_mark {n m : Nat} (labels : Fin n → Node) (g : Graph n)
    (origins : Fin m → Fin n) (needs : SourceNeeds m) (request : BaseRequest)
    (h : request ∈ fast labels g origins needs) : ∃ source, request.mark ∈ needs source := by
  obtain ⟨source,_,_,mark,_⟩ := (fast_witness _ _ _ _ _).mp h
  exact ⟨source,mark⟩

def scalarNeed {m : Nat} (source : Fin m) (mark : Mark) : SourceNeeds m := fun s =>
  if s = source then [mark] else []

theorem batch_distributes_to_scalar {n m : Nat} (labels : Fin n → Node) (g : Graph n)
    (origins : Fin m → Fin n) (needs : SourceNeeds m) (request : BaseRequest) :
    request ∈ fast labels g origins needs ↔ ∃ source, request.mark ∈ needs source ∧
      request ∈ fast labels g origins (scalarNeed source request.mark) := by
  constructor
  · intro h
    obtain ⟨source,node,label,mark,path⟩ := (fast_witness _ _ _ _ _).mp h
    refine ⟨source,mark,(fast_witness _ _ _ _ _).mpr ?_⟩
    exact ⟨source,node,label,by simp [scalarNeed],path⟩
  · rintro ⟨source,mark,present⟩
    obtain ⟨s,node,label,scalar,path⟩ := (fast_witness _ _ _ _ _).mp present
    by_cases same : s = source
    · subst s
      exact (fast_witness _ _ _ _ _).mpr ⟨source,node,label,mark,path⟩
    · simp [scalarNeed,same] at scalar

def unionNeeds {m : Nat} (left right : SourceNeeds m) : SourceNeeds m := fun source =>
  Run1RequestProtocol.merge (left source) (right source)

theorem source_union_distributes {n m : Nat} (labels : Fin n → Node) (g : Graph n)
    (origins : Fin m → Fin n) (left right : SourceNeeds m) (request : BaseRequest) :
    request ∈ fast labels g origins (unionNeeds left right) ↔
      request ∈ fast labels g origins left ∨ request ∈ fast labels g origins right := by
  simp only [fast_witness,unionNeeds,Run1RequestProtocol.mem_merge]
  constructor
  · rintro ⟨source,node,label,mark,path⟩
    cases mark with
    | inl h => exact Or.inl ⟨source,node,label,h,path⟩
    | inr h => exact Or.inr ⟨source,node,label,h,path⟩
  · rintro (⟨s,node,label,mark,path⟩ | ⟨s,node,label,mark,path⟩)
    · exact ⟨s,node,label,Or.inl mark,path⟩
    · exact ⟨s,node,label,Or.inr mark,path⟩

theorem same_origin_union {n : Nat} (labels : Fin n → Node) (g : Graph n)
    (origin : Fin n) (left right : List Mark) (request : BaseRequest) :
    request ∈ fast labels g (fun _ : Fin 2 => origin)
      (fun source => if source.val = 0 then left else right) ↔
    request ∈ fast labels g (fun _ : Fin 1 => origin)
      (fun _ => Run1RequestProtocol.merge left right) := by
  constructor
  · intro h
    obtain ⟨source,node,label,mark,path⟩ := (fast_witness _ _ _ _ _).mp h
    apply (fast_witness _ _ _ _ _).mpr
    refine ⟨0,node,label,?_,path⟩
    by_cases same : source.val = 0
    · exact (Run1RequestProtocol.mem_merge _ _ _).mpr (Or.inl (by simpa [same] using mark))
    · exact (Run1RequestProtocol.mem_merge _ _ _).mpr (Or.inr (by simpa [same] using mark))
  · intro h
    obtain ⟨_,node,label,mark,path⟩ := (fast_witness _ _ _ _ _).mp h
    cases (Run1RequestProtocol.mem_merge _ _ _).mp mark with
    | inl present => exact (fast_witness _ _ _ _ _).mpr ⟨0,node,label,present,path⟩
    | inr present => exact (fast_witness _ _ _ _ _).mpr ⟨1,node,label,present,path⟩

theorem reaches_mono {n : Nat} (g h : Graph n) (edges : ∀ e ∈ g, e ∈ h)
    (source node : Fin n) (path : Run1Requests.Reaches g source node) :
    Run1Requests.Reaches h source node := by
  induction path with
  | seed => exact .seed
  | call edge _ ih => exact .call (edges _ edge) ih

theorem fast_mono {n m : Nat} (labels : Fin n → Node) (g h : Graph n)
    (origins : Fin m → Fin n) (old new : SourceNeeds m)
    (edges : ∀ e ∈ g, e ∈ h) (marks : ∀ s t, t ∈ old s → t ∈ new s)
    (request : BaseRequest) (present : request ∈ fast labels g origins old) :
    request ∈ fast labels h origins new := by
  obtain ⟨source,node,label,mark,path⟩ := (fast_witness _ _ _ _ _).mp present
  apply (fast_witness _ _ _ _ _).mpr
  exact ⟨source,node,label,marks source _ mark,
    (BottomUpStaticRequests.inheritedPath_exact h _ _).mpr
      (reaches_mono g h edges _ _ ((BottomUpStaticRequests.inheritedPath_exact g _ _).mp path))⟩

/-- Recompute uses the entire current source set on graph growth. It needs no
    permanent ever-visited set and no mark-delta history at relay nodes. -/
structure Snapshot (n m : Nat) where
  graph : Graph n
  needs : SourceNeeds m

def graphGrowth {n m : Nat} (s : Snapshot n m) (edges : Graph n) : Snapshot n m :=
  {s with graph := s.graph ++ edges}
def sourceGrowth {n m : Nat} (s : Snapshot n m) (source : Fin m)
    (marks : List Mark) : Snapshot n m := {s with needs := register s.needs source marks []}
def recompute {n m : Nat} (labels : Fin n → Node) (origins : Fin m → Fin n)
    (s : Snapshot n m) : List BaseRequest := fast labels s.graph origins s.needs

theorem both_arrival_orders {n m : Nat} (s : Snapshot n m) (edges : Graph n)
    (source : Fin m) (marks : List Mark) :
    graphGrowth (sourceGrowth s source marks) edges =
      sourceGrowth (graphGrowth s edges) source marks := rfl

theorem duplicate_source_membership {m : Nat} (needs : SourceNeeds m) (s x : Fin m)
    (marks : List Mark) (t : Mark) :
    t ∈ register (register needs s marks []) s marks [] x ↔
      t ∈ register needs s marks [] x := by
  by_cases same : x = s
  · subst x; simp
  · simp [register,same]

/- Literal request-storage cohort: h static relay slots each store m marks in
   the expanded layout. Source-once stores m marks plus h shared route slots.
   This counts cells, not heap bytes or the implementation's routing CPU. -/
def expandedNeedRows : Nat → List Mark → List (Nat × Mark)
  | 0, _ => []
  | h + 1, marks => marks.map (h,·) ++ expandedNeedRows h marks
structure SourceOnceLayout where
  sourceMarks : List Mark
  sharedRoute : List Nat
def sourceOnceLayout (h : Nat) (marks : List Mark) : SourceOnceLayout := ⟨marks,List.range h⟩
def SourceOnceLayout.slots (layout : SourceOnceLayout) : Nat :=
  layout.sourceMarks.length + layout.sharedRoute.length
theorem expanded_need_rows_length (h : Nat) (marks : List Mark) :
    (expandedNeedRows h marks).length = h * marks.length := by
  induction h with
  | zero => simp [expandedNeedRows]
  | succ h ih => simp [expandedNeedRows,ih,Nat.add_mul,Nat.add_comm]
theorem source_once_slots (h : Nat) (marks : List Mark) :
    (sourceOnceLayout h marks).slots = marks.length + h := by
  simp [sourceOnceLayout,SourceOnceLayout.slots]
theorem need_chain_storage_strict (extraH extraM : Nat) :
    (sourceOnceLayout (extraH + 3) (List.range (extraM + 2))).slots <
      (expandedNeedRows (extraH + 3) (List.range (extraM + 2))).length := by
  rw [source_once_slots,expanded_need_rows_length,List.length_range]
  simp only [Nat.add_mul,Nat.mul_add]
  omega

namespace Controls
def a : Owner := ⟨1,0,[1]⟩
def b : Owner := ⟨2,0,[2]⟩
def port : Port := ⟨a,7,b,3,true⟩
def labels : Fin 3 → Node := fun i => match i.val with
  | 0 => .base a 0
  | 1 => .staticField a 1 4
  | _ => .staticField b 1 5
def writeInput : Incoming := .flow ⟨port,.base 0,⟨.field 1 5,.star,.empty,[]⟩⟩
def changedInput : Incoming := .flow ⟨port,.field 1 4,⟨.field 1 5,.any,.set [9],[8]⟩⟩
def entryWrite : Incoming := .flow ⟨{port with callee := a},.base 0,⟨.field 1 4,.star,.empty,[]⟩⟩
def graph : Graph 3 := [(0,1),(1,2)]
def origins : Fin 1 → Fin 3 := fun _ => 2
def needs : SourceNeeds 1 := fun _ => [8,8]
def request : BaseRequest := ⟨a,0,8⟩
def initial : Snapshot 3 1 := ⟨[(1,2)],fun _ => []⟩
def checks : List Bool := [
  incomingRoutes writeInput (.base a 0) (.staticField b 1 5),
  incomingRoutes changedInput (.staticField a 1 4) (.staticField b 1 5),
  !incomingRoutes writeInput (.base {a with context := [9]} 0) (.staticField b 1 5),
  !incomingRoutes writeInput (.base a 0) (.staticField {b with entry := 3} 1 5),
  !incomingRoutes .killed (.base a 0) (.staticField b 1 5),
  !incomingRoutes (.concrete port) (.base a 0) (.staticField b 1 5),
  fast labels graph origins needs == [request],
  fast labels (compile labels [writeInput]) origins needs == [request],
  fast labels (compile labels [entryWrite,changedInput]) origins needs == [request],
  fast labels (compile labels []) origins needs == [],
  ordinary labels graph origins needs == [request],
  fast labels (graph ++ [(2,1)]) origins needs == [request],
  recompute labels origins (sourceGrowth initial 0 [8]) == [],
  recompute labels origins (graphGrowth (sourceGrowth initial 0 [8]) [(0,1)]) == [request],
  recompute labels origins (sourceGrowth (graphGrowth initial [(0,1)]) 0 [8]) == [request],
  register (fun _ : Fin 1 => []) 0 [8] [8] 0 == [8],
  produce b (.flow (.field 1 5)) ⟨.field 1 5,.star,.empty,[8]⟩ [8] [8] ==
    .staticField ⟨b,1,5,[8]⟩,
  baseRequestReentry ⟨port,.field 1 4,⟨.local 7 [],.star,.empty,[8]⟩⟩ ⟨b,7,8⟩ ==
    some (.staticField a 1 4,8),
  fast labels graph origins (register needs 0 [9] []) == [⟨a,0,9⟩,request],
  (BottomUpStaticRequests.inheritedPath graph 2 0).map BottomUpStaticRequests.CallPath.nodes == some [0,1,2]]
theorem controls_pass : checks.all id = true := by decide +kernel
#guard checks.all id
end Controls
end ApSpec.BottomUpConcreteStaticNeeds
