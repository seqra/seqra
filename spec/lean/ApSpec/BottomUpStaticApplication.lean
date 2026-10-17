/-
  Constructive scalar application bridge for bottom-up static FLOW.

  A relay shortcut is CHECKED by executing concrete scalar primitives, including
  strong writes, full AP effects, request creation and observable actions. No
  axiom says that a relay transfer is identity. An unknown instruction explicitly
  rejects certification and requires ordinary fallback. Checked programs execute
  to exactly the supplied state, with no request or observation events.

  The second bridge proves field-identity containment in the universal root
  identity by denotation. It is generic in the original premise and supplier:
  broad/may, concrete marks, mx and E are not replaced with canonical fields.
  This is containment of location relations, not equality of packed AP rows.

  Scope: finite straight-line scalar primitive sequences and AP denotation.
  The compiler from actual CFG/forms, spatially matched cleaner selection,
  packed splitting, source callback inventory, AtCallee support, ND and owner
  scheduling remain explicit frontend/protocol obligations. A successful checker
  is not permission to omit an action absent from that compiler's inventory.
-/
import ApSpec.BottomUpStaticDomain

namespace ApSpec.BottomUpStaticApplication
open ApSpec
set_option autoImplicit false

abbrev Initial := BottomUpStaticDomain.Initial
abbrev State := Run1StrongPreservation.Edge Initial
abbrev Owner := Run1Requests.Owner
abbrev Question := Run1Requests.Question
abbrev Layered := Run1DefaultSummaryApplication.Layered

structure Observation where
  occurrence : Nat
  original : Initial
  current : Layered
  deriving DecidableEq, Repr

structure Result where
  states : List State
  requests : List Question
  observations : List Observation
  deriving DecidableEq, Repr

def Result.empty : Result := ⟨[],[],[]⟩
def Result.identity (input : State) : Result := ⟨[input],[],[]⟩
def Result.append (a b : Result) : Result :=
  ⟨a.states ++ b.states,a.requests ++ b.requests,a.observations ++ b.observations⟩

theorem append_empty (r : Result) : r.append .empty = r := by
  cases r <;> simp [Result.append,Result.empty]

theorem empty_append (r : Result) : Result.empty.append r = r := by
  cases r <;> rfl

/-- An actual FLOW premise and mark shape; concrete TAINT is outside the
    shortcut, rather than being tested with a FLOW cleaner's semantics. -/
def flowReady (input : State) : Bool :=
  (BottomUpStaticDomain.abstractOrigin input.original).isSome &&
    match input.current.fact.mark with
    | .star | .starEx _ => true
    | .conc _ => false

inductive Primitive where
  | pass
  /-- RHS values and their events were evaluated BEFORE preservation. -/
  | definiteWrite (target : Run1StrongPreservation.FieldTarget) (preStore : Result)
  /-- The scalar component has actually matched the cleaner's spatial reach. -/
  | matchedMarkClean (mark : Mark)
  | apEffect (source target : PFact) (edgeDemand : Bool)
  | rootCalleeDefault
  | observableAction (occurrence : Nat)
  | unknownFallback
  deriving DecidableEq, Repr

def requestsAt (owner : Owner) (input : State) (marks : List Mark) : List Question :=
  marks.filterMap (BottomUpStaticDomain.requestAt owner input.original input.current.fact)

/-- F78 generic excluded-target creators run even before positional/mark tests.
    The old scalar AP kernel's ordinary request output is retained separately. -/
def excludedTargetMarks (target : PFact) : List Mark := match target.mark with
  | .starEx marks => marks
  | _ => []

def evalKnown (owner : Owner) (instruction : Primitive) (input : State) : Option Result :=
  match instruction with
  | .pass => some (.identity input)
  | .definiteWrite target preStore =>
      some ⟨Run1StrongPreservation.write .definite target input preStore.states,
        preStore.requests,preStore.observations⟩
  | .matchedMarkClean mark =>
      let cleaned := BottomUpStaticDomain.cleanMatchedFlow owner input.original input.current mark
      some ⟨[⟨input.original,cleaned.1⟩],cleaned.2.toList,[]⟩
  | .apEffect source target edgeDemand =>
      let ordinary := applySummary ⟨input.current.fact,false⟩ source ⟨target,false⟩
      let layered := Run1DefaultSummaryApplication.withEdgeLayers
        input.current.demand edgeDemand ordinary
      some ⟨layered.facts.map (fun fact => ⟨input.original,fact⟩),
        requestsAt owner input (excludedTargetMarks target ++ layered.reqs),[]⟩
  | .rootCalleeDefault =>
      let applied := BottomUpStaticDomain.applyDefault input.current
      some ⟨applied.facts.map (fun fact => ⟨input.original,fact⟩),
        requestsAt owner input applied.reqs,[]⟩
  | .observableAction occurrence =>
      some ⟨[input],[],[⟨occurrence,input.original,input.current⟩]⟩
  | .unknownFallback => none

def eval (owner : Owner) (instruction : Primitive) (input : State) : Option Result :=
  if flowReady input then evalKnown owner instruction input else none

def checkPrimitive (owner : Owner) (instruction : Primitive) (input : State) : Bool :=
  match eval owner instruction input with
  | none => false
  | some result => result == Result.identity input

theorem checked_primitive_exact (owner : Owner) (instruction : Primitive) (input : State)
    (checked : checkPrimitive owner instruction input = true) :
    eval owner instruction input = some (.identity input) := by
  cases h : eval owner instruction input with
  | none => simp [checkPrimitive,h] at checked
  | some result =>
      have same : result = Result.identity input := by simpa [checkPrimitive,h] using checked
      exact congrArg some same

theorem unknown_never_certified (owner : Owner) (input : State) :
    checkPrimitive owner .unknownFallback input = false := by
  cases h : flowReady input <;> simp [checkPrimitive,eval,evalKnown,h]

theorem observable_never_certified (owner : Owner) (input : State) (occurrence : Nat) :
    checkPrimitive owner (.observableAction occurrence) input = false := by
  cases h : flowReady input <;>
    simp [checkPrimitive,eval,evalKnown,h,Result.identity]

/-- Even an idempotent mx update cannot erase the request callback. -/
theorem matched_cleaner_never_certified (owner : Owner) (input : State) (mark : Mark)
    (ready : flowReady input = true) :
    checkPrimitive owner (.matchedMarkClean mark) input = false := by
  cases origin : input.original <;>
    simp [flowReady,origin,BottomUpStaticDomain.abstractOrigin] at ready
  all_goals
    simp [checkPrimitive,eval,evalKnown,flowReady,origin,
      BottomUpStaticDomain.cleanMatchedFlow,BottomUpStaticDomain.requestAt,
      BottomUpStaticDomain.abstractOrigin,Result.identity,ready]

def stepMany (owner : Owner) (instruction : Primitive) : List State → Option Result
  | [] => some .empty
  | input::rest => do
      let first ← eval owner instruction input
      let later ← stepMany owner instruction rest
      pure (first.append later)

def execute (owner : Owner) : List Primitive → Result → Option Result
  | [], current => some current
  | instruction::rest, current => do
      let next ← stepMany owner instruction current.states
      execute owner rest
        ⟨next.states,current.requests ++ next.requests,
          current.observations ++ next.observations⟩

/-- Each accepted primitive has the same state, so subsequent certificates
    are checked on that exact state. Split/value branches still run in execute. -/
def checkRelay (owner : Owner) : List Primitive → State → Bool
  | [], _ => true
  | instruction::rest, input =>
      checkPrimitive owner instruction input && checkRelay owner rest input

theorem checked_relay_executes_identity (owner : Owner) (instructions : List Primitive)
    (input : State) (checked : checkRelay owner instructions input = true) :
    execute owner instructions (.identity input) = some (.identity input) := by
  induction instructions with
  | nil => rfl
  | cons instruction rest ih =>
      have parts : checkPrimitive owner instruction input = true ∧ checkRelay owner rest input = true := by
        simpa only [checkRelay,Bool.and_eq_true] using checked
      obtain ⟨head,tail⟩ := parts
      have evaluated := checked_primitive_exact owner instruction input head
      simpa [execute,stepMany,evaluated,Result.identity,Result.append,Result.empty] using ih tail

theorem checked_relay_has_no_events (owner : Owner) (instructions : List Primitive)
    (input : State) (output : Result) (checked : checkRelay owner instructions input = true)
    (executed : execute owner instructions (.identity input) = some output) :
    output.states = [input] ∧ output.requests = [] ∧ output.observations = [] := by
  rw [checked_relay_executes_identity owner instructions input checked] at executed
  cases executed
  exact ⟨rfl,rfl,rfl⟩

theorem disjoint_write_is_certified (owner : Owner) (input : State)
    (target : Run1StrongPreservation.FieldTarget) (ready : flowReady input = true)
    (apart : relate target.path input.current.fact.path = .apart) :
    checkPrimitive owner (.definiteWrite target .empty) input = true := by
  have kept := BottomUpStaticDomain.disjoint_write_unchanged target input apart
  simp [checkPrimitive,eval,ready,evalKnown,Run1StrongPreservation.write,kept,
    Result.empty,Result.identity]

theorem overwritten_old_is_not_certified (owner : Owner) (input : State)
    (target : Run1StrongPreservation.FieldTarget) (ready : flowReady input = true)
    (killed : Run1StrongPreservation.classify target input.current.fact = .kill) :
    checkPrimitive owner (.definiteWrite target .empty) input = false := by
  have gone := Run1StrongPreservation.definite_kill target input killed
  simp [checkPrimitive,eval,ready,evalKnown,Run1StrongPreservation.write,gone,
    Result.empty,Result.identity]

theorem self_assignment_is_certified (owner : Owner) (input : State)
    (target : Run1StrongPreservation.FieldTarget) (ready : flowReady input = true)
    (killed : Run1StrongPreservation.classify target input.current.fact = .kill) :
    checkPrimitive owner (.definiteWrite target (.identity input)) input = true := by
  have kept := BottomUpStaticDomain.self_assignment_uses_pre_store_value target input killed
  simp [checkPrimitive,eval,ready,evalKnown,Result.identity,kept]

theorem root_default_is_certified (owner : Owner) (input : State)
    (ready : flowReady input = true) (static : input.current.fact.base = 1)
    (demand : input.current.demand = true)
    (canonical : Run1DefaultSummaryApplication.CanonicalPayload input.current.fact) :
    checkPrimitive owner .rootCalleeDefault input = true := by
  have applied := BottomUpStaticDomain.default_full_payload input.current static canonical
  have rebuilt : (⟨input.original,⟨input.current.fact,true⟩⟩ : State) = input := by
    cases input with
    | mk original current =>
        cases current with
        | mk fact d => simp only at demand; subst d; rfl
  simp [checkPrimitive,eval,ready,evalKnown,applied,requestsAt,Result.identity,rebuilt]

/-! ## Generic location-level containment, including arbitrary broad suppliers. -/

def staticRoot : PFact := Run1DefaultSummaryApplication.rootSTAR 1
def fieldIdentity (c f : Nat) : PFact := (Run1Requests.Slot.field c f).initial

theorem root_identity_at_location (location : Loc) (static : location.base = 1) :
    den staticRoot staticRoot location location := by
  refine ⟨static,static,trivial,rfl,trivial,location.path,location.path,?_,?_,?_,?_,?_⟩
  · simp [staticRoot,Run1DefaultSummaryApplication.rootSTAR]
  · simp [staticRoot,Run1DefaultSummaryApplication.rootSTAR]
  · exact CoreAux.empty_admits _
  · rfl
  · exact CoreAux.empty_admits _

/-- The field identity cannot move location or mark. The root identity covers
    the same pair, regardless of which actual supplier supplied that location. -/
theorem field_identity_contained_in_root (c f : Nat) (before after : Loc)
    (fieldPair : den (fieldIdentity c f) (fieldIdentity c f) before after) :
    den staticRoot staticRoot before after := by
  obtain ⟨base0,base1,_,mark,_,suffix,rest,path0,path1,_,tail⟩ := fieldPair
  have tails : rest = suffix := tail.1
  have samePath : after.path = before.path := by rw [path1,tails,← path0]
  refine ⟨base0,base1,trivial,mark,trivial,before.path,before.path,?_,?_,?_,?_,?_⟩
  · simp [staticRoot,Run1DefaultSummaryApplication.rootSTAR]
  · simpa [staticRoot,Run1DefaultSummaryApplication.rootSTAR] using samePath
  · exact CoreAux.empty_admits _
  · rfl
  · exact CoreAux.empty_admits _

/-- Existential concrete locations are the executable denotational bridge;
    no canonicalization/field projection is applied to the actual supplier. -/
def through (original supplier premise conclusion : PFact) (before after : Loc) : Prop :=
  ∃ supplied, den original supplier before supplied ∧ den premise conclusion supplied after

theorem arbitrary_supplier_field_identity_contained (original supplier : PFact)
    (c f : Nat) (before after : Loc)
    (fieldPath : through original supplier (fieldIdentity c f) (fieldIdentity c f) before after) :
    through original supplier staticRoot staticRoot before after := by
  obtain ⟨supplied,supplierPair,fieldPair⟩ := fieldPath
  exact ⟨supplied,supplierPair,field_identity_contained_in_root c f supplied after fieldPair⟩

abbrev FieldSummary := BottomUpStaticDomain.FieldSummary

/-- The mandatory root default is part of the relation, never an S initial.
    Layers remain independent and NORMAL effects are not covered by D. -/
def summaryRelation (edges : List FieldSummary) (demand : Bool) (before after : Loc) : Prop :=
  (demand = true ∧ den staticRoot staticRoot before after) ∨
    ∃ edge, edge ∈ edges ∧ edge.conclusion.demand = demand ∧
      den edge.premise.toAP edge.conclusion.fact before after

theorem compaction_preserves_full_relation (edges : List FieldSummary) (demand : Bool)
    (before after : Loc) :
    summaryRelation (BottomUpStaticDomain.compact edges) demand before after ↔
      summaryRelation edges demand before after := by
  constructor
  · intro compacted
    rcases compacted with root | ⟨edge,present,layer,transfer⟩
    · exact Or.inl root
    · exact Or.inr ⟨edge,(List.mem_filter.mp present).1,layer,transfer⟩
  · intro ordinary
    rcases ordinary with root | ⟨edge,present,layer,transfer⟩
    · exact Or.inl root
    · cases hc : BottomUpStaticDomain.compactable edge with
      | false =>
          refine Or.inr ⟨edge,?_,layer,transfer⟩
          exact (BottomUpStaticDomain.compaction_preserves_nonidentity edge edges hc).mpr present
      | true =>
          obtain ⟨c,f,premise,conclusion⟩ := BottomUpStaticDomain.compactable_exact edge hc
          rw [premise,conclusion] at transfer
          rw [conclusion] at layer
          exact Or.inl ⟨layer.symm,field_identity_contained_in_root c f before after transfer⟩

def appliedRelation (original supplier : PFact) (inputDemand : Bool)
    (edges : List FieldSummary) (resultDemand : Bool) (before after : Loc) : Prop :=
  ∃ supplied, den original supplier before supplied ∧
    ∃ summaryDemand, summaryRelation edges summaryDemand supplied after ∧
      resultDemand = (inputDemand || summaryDemand)

/-- Broad actual suppliers and concrete TAINT are included: only the middle
    summary relation is compacted. Input marks, mx/E/path/tail stay unchanged. -/
theorem compaction_preserves_arbitrary_supplier (original supplier : PFact)
    (inputDemand : Bool) (edges : List FieldSummary) (resultDemand : Bool) (before after : Loc) :
    appliedRelation original supplier inputDemand (BottomUpStaticDomain.compact edges)
      resultDemand before after ↔
    appliedRelation original supplier inputDemand edges resultDemand before after := by
  constructor
  · rintro ⟨supplied,h,summaryDemand,summary,layer⟩
    exact ⟨supplied,h,summaryDemand,
      (compaction_preserves_full_relation edges summaryDemand supplied after).mp summary,layer⟩
  · rintro ⟨supplied,h,summaryDemand,summary,layer⟩
    exact ⟨supplied,h,summaryDemand,
      (compaction_preserves_full_relation edges summaryDemand supplied after).mpr summary,layer⟩

namespace Controls
def owner : Owner := ⟨9,2,[5]⟩
def field : PFact := fieldIdentity 2 3
def input : State := ⟨.abstractField 2 3,⟨field,true⟩⟩
def cleanInput : State := ⟨.abstractField 2 3,⟨{field with mark := .starEx [8]},true⟩⟩
def target : Run1StrongPreservation.FieldTarget := ⟨1,[4],7⟩
def sibling : Run1StrongPreservation.FieldTarget := ⟨1,[4],9⟩

def relay : List Primitive :=
  [.pass,.definiteWrite sibling .empty,.rootCalleeDefault,
    .definiteWrite target (.identity input),.apEffect staticRoot staticRoot true]

def checks : Bool :=
  checkRelay owner relay input &&
  (execute owner relay (.identity input) == some (.identity input)) &&
  !checkPrimitive owner (.definiteWrite target .empty) input &&
  !checkPrimitive owner (.matchedMarkClean 8) input &&
  !checkPrimitive owner (.matchedMarkClean 8) cleanInput &&
  !checkPrimitive owner (.observableAction 17) input &&
  !checkPrimitive owner .unknownFallback input &&
  !checkPrimitive owner (.apEffect field {field with mark := .starEx [8]} false) input

theorem checks_pass : checks = true := by decide

/-- A broad actual concrete supplier includes a field location while root
    application may keep a broad physical AP. Containment is location-based. -/
def broadConcrete : PFact := ⟨1,[],.any,.conc 8⟩
def sourceInitial : PFact := (BottomUpStaticDomain.Initial.baseExact 0 8).toAP
def sourceLocation : Loc := ⟨2,[],8⟩
def fieldLocation : Loc := ⟨1,[4,7,13],8⟩

theorem broad_concrete_witness :
    through sourceInitial broadConcrete field field sourceLocation fieldLocation := by
  refine ⟨fieldLocation,?_,?_⟩
  · exact ⟨rfl,rfl,rfl,rfl,trivial,[],[4,7,13],rfl,rfl,rfl,trivial⟩
  · exact ⟨rfl,rfl,trivial,rfl,trivial,[13],[13],rfl,rfl,rfl,rfl,rfl⟩

theorem broad_concrete_root_witness :
    through sourceInitial broadConcrete staticRoot staticRoot sourceLocation fieldLocation :=
  arbitrary_supplier_field_identity_contained _ _ 2 3 _ _ broad_concrete_witness

/-- The two operators can return different physical AP lists on the same
    broad actual TAINT supplier. The proof above concerns contained locations. -/
theorem broad_results_are_not_literal_AP_equality :
    (BottomUpStaticDomain.applyDefault ⟨broadConcrete,true⟩).facts ≠
      (Run1DefaultSummaryApplication.withEdgeLayers true true
        (applySummary ⟨broadConcrete,false⟩ field ⟨field,false⟩)).facts := by decide

#eval checks
#guard checks
end Controls

#print axioms checked_primitive_exact
#print axioms checked_relay_executes_identity
#print axioms checked_relay_has_no_events
#print axioms observable_never_certified
#print axioms matched_cleaner_never_certified
#print axioms disjoint_write_is_certified
#print axioms overwritten_old_is_not_certified
#print axioms self_assignment_is_certified
#print axioms root_default_is_certified
#print axioms field_identity_contained_in_root
#print axioms arbitrary_supplier_field_identity_contained
#print axioms compaction_preserves_full_relation
#print axioms compaction_preserves_arbitrary_supplier
#print axioms Controls.checks_pass
#print axioms Controls.broad_concrete_root_witness
#print axioms Controls.broad_results_are_not_literal_AP_equality

end ApSpec.BottomUpStaticApplication
