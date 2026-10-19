/-
Formula-free ordinary early-static effects. This finite executable model uses
scalar AP overlap cells. An Initial's scope is its represented ordinary AP
match region; a current scalar cell is retained by matching and FLOW images.
The Kotlin tail/E/mx compiler to these cells is an explicit adapter boundary.
Canonical marked initials may widen: there is no extra spatial writer check.

Complete ports and original lists are semantic model data. No guard formula,
fixed certificate, per-AP support token, or method-wide rooted-mark mask is
present. Derivation trees in the imported positive calculus are proof data,
not proposed runtime storage. All produced group values are DEMAND.
-/
import ApSpec.GroupedRun1StaticComposition
import Std.Tactic

namespace ApSpec.Run1OrdinaryStaticSupply
set_option autoImplicit false


inductive Slot where
  | base (id : Nat)
  | staticField (id : Nat)
  deriving DecidableEq, Repr

inductive Mark where
  | flow
  | group (id : Nat)
  deriving DecidableEq, Repr

structure Initial where
  slot : Slot
  scope : List Nat
  mark : Mark
  deriving DecidableEq, Repr

structure Original where
  owner : Nat
  initial : Initial
  deriving DecidableEq, Repr

/-- Empty originals denote ZERO; each nonzero original is kept explicitly. -/
abbrev Premise := List Original

structure Port where
  caller : Nat
  callee : Nat
  statement : Nat
  stage : Nat
  context : Nat
  callerLayer : Nat
  deriving DecidableEq, Repr

structure Value where
  slot : Slot
  cell : Nat
  mark : Mark
  excluded : List Nat := []
  deriving DecidableEq, Repr

structure Row where
  port : Port
  originals : Premise
  actual : Value
  deriving DecidableEq, Repr

structure Outcome where
  owner : Nat
  originals : Premise
  value : Value
  deriving DecidableEq, Repr

/-- Declared transforms are different from polymorphic mark-preserving FLOW.
    `marked` retains a complete input-dependent AP relation for TAINT summaries;
    its selected output group need not equal an input group. -/
inductive Body where
  | flow (images : List (Nat × Nat)) (excluded : List Nat)
  | generate (group : Nat) (cells : List Nat)
  | marked (group : Nat) (images : List (List Nat × Nat))
  deriving DecidableEq, Repr

structure Summary where
  owner : Nat
  operation : Nat
  reached : Bool
  premises : List Initial
  targetField : Nat
  body : Body
  deriving DecidableEq, Repr

def markMatches : Mark → Mark → Bool
  | .flow, _ => true
  | .group g, .group h => decide (g = h)
  | .group _, .flow => false

def valueMatches (initial : Initial) (actual : Value) : Bool :=
  decide (initial.slot = actual.slot) && initial.scope.contains actual.cell &&
    markMatches initial.mark actual.mark

/-- Recursion checks complete arity; there is no truncating zip. -/
def align : List Initial → List Value → Bool
  | [], [] => true
  | initial :: tail, actual :: rest => valueMatches initial actual && align tail rest
  | _, _ => false

def rowsAligned (port : Port) (initials : List Initial) (rows : List Row) : Bool :=
  rows.all (fun row => decide (row.port = port) &&
    row.originals.all (fun original => decide (original.owner = port.caller))) &&
    align initials (rows.map Row.actual)

def bodyLegal (summary : Summary) : Bool :=
  match summary.body, summary.premises with
  | .flow _ _, [initial] => decide (initial.mark = .flow)
  | .generate _ _, initials | .marked _ _, initials =>
      !initials.isEmpty && initials.all (fun initial => match initial.mark with
        | .group _ => true | .flow => false)
  | _, _ => false

def flowPermitted (input : Value) (excluded : List Nat) : Bool :=
  match input.mark with
  | .flow => true
  | .group group => !excluded.contains group

def evalBody (target : Nat) (body : Body) (inputs : List Value) : List Value :=
  match body, inputs with
  | .flow images excluded, [input] =>
      if flowPermitted input excluded then
        (images.filter (fun image => decide (image.1 = input.cell))).map (fun image =>
          ⟨.staticField target, image.2, input.mark,
            match input.mark with | .flow => (input.excluded ++ excluded).eraseDups | .group _ => []⟩)
      else []
  | .generate group cells, _ => cells.map (fun cell => ⟨.staticField target, cell, .group group, []⟩)
  | .marked group images, inputs =>
      (images.filter (fun image => decide (image.1 = inputs.map Value.cell))).map
        (fun image => ⟨.staticField target, image.2, .group group, []⟩)
  | _, _ => []

def originalsOf (rows : List Row) : Premise :=
  (rows.flatMap Row.originals).eraseDups

def callReady (summary : Summary) (port : Port) (rows : List Row) : Bool :=
  summary.reached && decide (summary.owner = port.callee) &&
    bodyLegal summary && rowsAligned port summary.premises rows

def applyCall (summary : Summary) (port : Port) (rows : List Row) : List Outcome :=
  if callReady summary port rows then
    (evalBody summary.targetField summary.body (rows.map Row.actual)).map
      (fun value => ⟨port.caller, originalsOf rows, value⟩)
  else []

theorem align_length (initials : List Initial) (values : List Value)
    (accepted : align initials values = true) : initials.length = values.length := by
  induction initials generalizing values with
  | nil => cases values <;> simp_all [align]
  | cons initial tail ih =>
      cases values with
      | nil => simp [align] at accepted
      | cons value rest =>
          have parts := Bool.and_eq_true_iff.mp (show (valueMatches initial value && align tail rest) = true from accepted)
          exact congrArg Nat.succ (ih rest parts.2)

def EveryMember : List Initial → List Value → Prop
  | [], [] => True
  | initial :: tail, actual :: rest => valueMatches initial actual = true ∧ EveryMember tail rest
  | _, _ => False

theorem align_complete_members (initials : List Initial) (values : List Value) :
    align initials values = true ↔
      EveryMember initials values := by
  induction initials generalizing values with
  | nil => cases values <;> simp [align, EveryMember]
  | cons initial tail ih =>
      cases values with
      | nil => simp [align, EveryMember]
      | cons value rest => simp [align, EveryMember, ih]

theorem rowsAligned_length (port : Port) (initials : List Initial) (rows : List Row)
    (accepted : rowsAligned port initials rows = true) : initials.length = rows.length := by
  have h := align_length initials (rows.map Row.actual) (Bool.and_eq_true_iff.mp accepted).2
  simpa using h

theorem rowsAligned_full_port (port : Port) (initials : List Initial) (rows : List Row)
    (accepted : rowsAligned port initials rows = true) (row : Row) (present : row ∈ rows) :
    row.port = port := by
  have h := (List.all_eq_true.mp (Bool.and_eq_true_iff.mp accepted).1) row present
  exact of_decide_eq_true (Bool.and_eq_true_iff.mp h).1

theorem originals_exact (rows : List Row) (original : Original) :
    original ∈ originalsOf rows ↔ ∃ row, row ∈ rows ∧ original ∈ row.originals := by
  simp [originalsOf]

theorem applyCall_keeps_every_original (summary : Summary) (port : Port) (rows : List Row)
    (result : Outcome) (produced : result ∈ applyCall summary port rows)
    (original : Original) :
    original ∈ result.originals ↔ ∃ row, row ∈ rows ∧ original ∈ row.originals := by
  unfold applyCall at produced
  split at produced
  · obtain ⟨value, _, same⟩ := List.mem_map.mp produced
    subst result
    exact originals_exact rows original
  · exact False.elim (List.not_mem_nil produced)

theorem applyCall_full_binding (summary : Summary) (port : Port) (rows : List Row)
    (result : Outcome) (produced : result ∈ applyCall summary port rows) :
    summary.owner = port.callee ∧ summary.premises.length = rows.length ∧
      ∀ row, row ∈ rows → row.port = port := by
  have ready : callReady summary port rows = true := by
    by_cases h : callReady summary port rows = true
    · exact h
    · simp [applyCall, h] at produced
  have parts := Bool.and_eq_true_iff.mp ready
  have aligned := parts.2
  have head := Bool.and_eq_true_iff.mp parts.1
  exact ⟨of_decide_eq_true (Bool.and_eq_true_iff.mp head.1).2,
    rowsAligned_length port _ rows aligned, rowsAligned_full_port port _ rows aligned⟩

theorem arity_mismatch_rejected (summary : Summary) (port : Port) (rows : List Row)
    (different : summary.premises.length ≠ rows.length) : applyCall summary port rows = [] := by
  by_cases ready : callReady summary port rows = true
  · have aligned := (Bool.and_eq_true_iff.mp ready).2
    exact False.elim (different (rowsAligned_length port _ rows aligned))
  · simp [applyCall, ready]

theorem different_port_rejected (summary : Summary) (port : Port) (rows : List Row)
    (row : Row) (present : row ∈ rows) (different : row.port ≠ port) :
    applyCall summary port rows = [] := by
  by_cases ready : callReady summary port rows = true
  · exact False.elim (different (rowsAligned_full_port port _ rows
      (Bool.and_eq_true_iff.mp ready).2 row present))
  · simp [applyCall, ready]

theorem zero_result_requires_empty_originals (summary : Summary) (port : Port) (rows : List Row)
    (result : Outcome) (produced : result ∈ applyCall summary port rows)
    (zero : result.originals = []) : ∀ row, row ∈ rows → row.originals = [] := by
  intro row present
  cases h : row.originals with
  | nil => rfl
  | cons first tail =>
      have member : first ∈ result.originals :=
        (applyCall_keeps_every_original summary port rows result produced first).mpr
          ⟨row, present, by simp [h]⟩
      rw [zero] at member
      exact False.elim (List.not_mem_nil member)

theorem nonstatic_original_stays_conditional (summary : Summary) (port : Port) (rows : List Row)
    (result : Outcome) (produced : result ∈ applyCall summary port rows)
    (row : Row) (present : row ∈ rows) (original : Original)
    (member : original ∈ row.originals) (base : Nat)
    (ordinary : original.initial.slot = .base base) :
    result.originals ≠ [] ∧
      ¬ result.originals.all (fun item => match item.initial.slot with
        | .staticField _ => true | .base _ => false) = true := by
  have retained := (applyCall_keeps_every_original summary port rows result produced original).mpr
    ⟨row, present, member⟩
  constructor
  · intro empty
    rw [empty] at retained
    exact List.not_mem_nil retained
  · intro allStatic
    have selected := (List.all_eq_true.mp allStatic) original retained
    simp [ordinary] at selected

/-- Scalar cells are exact actual FLOW images, rather than the broad target. -/
theorem flow_output_preserves_mark (target : Nat) (images : List (Nat × Nat))
    (excluded : List Nat) (input output : Value)
    (produced : output ∈ evalBody target (.flow images excluded) [input]) :
    output.mark = input.mark ∧ ∃ image, image ∈ images ∧
      image.1 = input.cell ∧ image.2 = output.cell := by
  simp only [evalBody] at produced
  split at produced
  · obtain ⟨image, selected, same⟩ := List.mem_map.mp produced
    have selected := List.mem_filter.mp selected
    subst output
    exact ⟨rfl, image, selected.1, of_decide_eq_true selected.2, rfl⟩
  · exact False.elim (List.not_mem_nil produced)

theorem generated_output_uses_declared_group (target group : Nat) (cells : List Nat)
    (inputs : List Value) (output : Value)
    (produced : output ∈ evalBody target (.generate group cells) inputs) :
    output.mark = .group group := by
  obtain ⟨cell, _, same⟩ := List.mem_map.mp produced
  subst output
  rfl

/-- A TAINT relation compares the complete matched tuple, retaining its actual
    image. It does not replace an input-dependent relation by a fixed GEN. -/
theorem marked_output_exact_image (target group : Nat) (images : List (List Nat × Nat))
    (inputs : List Value) (output : Value)
    (produced : output ∈ evalBody target (.marked group images) inputs) :
    output.mark = .group group ∧ ∃ image, image ∈ images ∧
      image.1 = inputs.map Value.cell ∧ image.2 = output.cell := by
  obtain ⟨image, selected, same⟩ := List.mem_map.mp produced
  have selected := List.mem_filter.mp selected
  subst output
  exact ⟨rfl, image, selected.1, of_decide_eq_true selected.2, rfl⟩

theorem marked_missing_image_rejected (target group : Nat) (images : List (List Nat × Nat))
    (inputs : List Value) (absent : ∀ image, image ∈ images → image.1 ≠ inputs.map Value.cell) :
    evalBody target (.marked group images) inputs = [] := by
  cases equation : evalBody target (.marked group images) inputs with
  | nil => rfl
  | cons output rest =>
      have produced : output ∈ evalBody target (.marked group images) inputs := by
        rw [equation]
        exact List.mem_cons_self
      obtain ⟨_, image, present, matched, _⟩ :=
        marked_output_exact_image target group images inputs output produced
      exact False.elim (absent image present matched)

def allStatic (initials : List Initial) : Bool := initials.all (fun initial =>
  match initial.slot with | .staticField _ => true | .base _ => false)

def staticGroup (value : Value) : Bool := match value.slot, value.mark with
  | .staticField _, .group _ => true
  | _, _ => false

def staticApply (summary : Summary) (inputs : List Value) : List Value :=
  if summary.reached && bodyLegal summary && allStatic summary.premises &&
      inputs.all staticGroup && align summary.premises inputs then
    evalBody summary.targetField summary.body inputs
  else []

theorem staticApply_complete_arity (summary : Summary) (inputs : List Value) (output : Value)
    (produced : output ∈ staticApply summary inputs) :
    summary.premises.length = inputs.length ∧
      EveryMember summary.premises inputs := by
  have active : (summary.reached && bodyLegal summary && allStatic summary.premises &&
      inputs.all staticGroup && align summary.premises inputs) = true := by
    by_cases h : (summary.reached && bodyLegal summary && allStatic summary.premises &&
        inputs.all staticGroup && align summary.premises inputs) = true
    · exact h
    · simp [staticApply, h] at produced
  have matched := (Bool.and_eq_true_iff.mp active).2
  exact ⟨align_length _ _ matched, (align_complete_members _ _).mp matched⟩

theorem nonstatic_premise_not_direct_pool (summary : Summary) (inputs : List Value)
    (initial : Initial) (member : initial ∈ summary.premises) (base : Nat)
    (ordinary : initial.slot = .base base) : staticApply summary inputs = [] := by
  have blocked : allStatic summary.premises = false := by
    cases equation : allStatic summary.premises with
    | false => rfl
    | true =>
        have selected := (List.all_eq_true.mp equation) initial member
        simp [ordinary] at selected
  simp [staticApply, blocked]

/-- A cell remains separate from its field. No hash or numeric pairing is used. -/
abbrev SupplyNode := GroupedRun1StaticComposition.Site × Nat

def embedFact (fact : Run1StaticGroupDerivation.Fact GroupedRun1StaticComposition.Site) :
    Run1StaticGroupDerivation.Fact SupplyNode := ⟨(fact.node, 0), fact.group⟩

def embedRule (rule : Run1StaticGroupDerivation.Rule GroupedRun1StaticComposition.Site) :
    Run1StaticGroupDerivation.Rule SupplyNode :=
  ⟨rule.premises.map embedFact, embedFact rule.conclusion⟩

def poolAtom (value : Value) : Option (Run1StaticGroupDerivation.Fact SupplyNode) :=
  match value.slot, value.mark with
  | .staticField field, .group group => some ⟨(.staticField field, value.cell), group⟩
  | _, _ => none

def poolInputs (inputs : List Value) : List (Run1StaticGroupDerivation.Fact SupplyNode) := inputs.filterMap poolAtom

/-- Every tuple is complete before checking or building a positive rule. -/
def tuples (values : List Value) : Nat → List (List Value)
  | 0 => [[]]
  | n + 1 => values.flatMap (fun value => (tuples values n).map (value :: ·))

theorem tuples_complete (values inputs : List Value)
    (available : ∀ value, value ∈ inputs → value ∈ values) :
    inputs ∈ tuples values inputs.length := by
  induction inputs with
  | nil => simp [tuples]
  | cons first rest ih =>
      apply List.mem_flatMap.mpr
      refine ⟨first, available first (by simp), ?_⟩
      apply List.mem_map.mpr
      exact ⟨rest, ih (fun value present => available value (List.mem_cons_of_mem _ present)), rfl⟩

def tupleRules (summary : Summary) (inputs : List Value) : List (Run1StaticGroupDerivation.Rule SupplyNode) :=
  let premises := poolInputs inputs
  if premises = [] then [] else
    (staticApply summary inputs).filterMap (fun output =>
      (poolAtom output).map (fun conclusion => ⟨premises, conclusion⟩))

def compileStatic (summaries : List Summary) (values : List Value) : List (Run1StaticGroupDerivation.Rule SupplyNode) :=
  summaries.flatMap (fun summary =>
    (tuples values summary.premises.length).flatMap (tupleRules summary))

theorem tupleRules_productive (summary : Summary) (inputs : List Value)
    (rule : Run1StaticGroupDerivation.Rule SupplyNode) (present : rule ∈ tupleRules summary inputs) : rule.premises ≠ [] := by
  simp only [tupleRules] at present
  split at present
  · exact False.elim (List.not_mem_nil present)
  · rename_i nonempty
    obtain ⟨output, _, selected⟩ := List.mem_filterMap.mp present
    cases equation : poolAtom output with
    | none => simp [equation] at selected
    | some conclusion =>
        simp only [equation, Option.map_some, Option.some.injEq] at selected
        subst rule
        exact nonempty

theorem compileStatic_productive (summaries : List Summary) (values : List Value)
    (rule : Run1StaticGroupDerivation.Rule SupplyNode) (present : rule ∈ compileStatic summaries values) : rule.premises ≠ [] := by
  obtain ⟨summary, _, selected⟩ := List.mem_flatMap.mp present
  obtain ⟨inputs, _, selected⟩ := List.mem_flatMap.mp selected
  exact tupleRules_productive summary inputs rule selected

theorem tupleRules_exact_application (summary : Summary) (inputs : List Value)
    (rule : Run1StaticGroupDerivation.Rule SupplyNode) (present : rule ∈ tupleRules summary inputs) :
    rule.premises = poolInputs inputs ∧ ∃ output, output ∈ staticApply summary inputs ∧
      poolAtom output = some rule.conclusion := by
  simp only [tupleRules] at present
  split at present
  · exact False.elim (List.not_mem_nil present)
  · obtain ⟨output, produced, selected⟩ := List.mem_filterMap.mp present
    cases equation : poolAtom output with
    | none => simp [equation] at selected
    | some conclusion =>
        simp only [equation, Option.map_some, Option.some.injEq] at selected
        subst rule
        exact ⟨rfl, output, produced, equation⟩

theorem tupleRules_complete_application (summary : Summary) (inputs : List Value)
    (output : Value) (produced : output ∈ staticApply summary inputs)
    (conclusion : Run1StaticGroupDerivation.Fact SupplyNode)
    (marked : poolAtom output = some conclusion) (nonempty : poolInputs inputs ≠ []) :
    (⟨poolInputs inputs, conclusion⟩ : Run1StaticGroupDerivation.Rule SupplyNode) ∈ tupleRules summary inputs := by
  simp only [tupleRules, if_neg nonempty]
  exact List.mem_filterMap.mpr ⟨output, produced, by simp only [marked, Option.map_some]⟩

theorem compileStatic_complete_application (summaries : List Summary) (values inputs : List Value)
    (summary : Summary) (registered : summary ∈ summaries)
    (available : ∀ value, value ∈ inputs → value ∈ values)
    (output : Value) (produced : output ∈ staticApply summary inputs)
    (conclusion : Run1StaticGroupDerivation.Fact SupplyNode)
    (marked : poolAtom output = some conclusion) (nonempty : poolInputs inputs ≠ []) :
    (⟨poolInputs inputs, conclusion⟩ : Run1StaticGroupDerivation.Rule SupplyNode) ∈ compileStatic summaries values := by
  apply List.mem_flatMap.mpr
  refine ⟨summary, registered, ?_⟩
  apply List.mem_flatMap.mpr
  have length := (staticApply_complete_arity summary inputs output produced).1
  refine ⟨inputs, ?_, tupleRules_complete_application summary inputs output produced conclusion marked nonempty⟩
  rw [length]
  exact tuples_complete values inputs available

/-- Root inputs come only from the reference's eligible source/boundary list.
    Requests and analyzed initials are deliberately separate fields. -/
structure State where
  sources : List GroupedRun1StaticComposition.Source := []
  ordinaryRules : List (Run1StaticGroupDerivation.Rule SupplyNode) := []
  summaries : List Summary := []
  values : List Value := []
  analyzedInitials : List Initial := []
  requested : List (Nat × Nat × Nat) := []

def stateRules (state : State) : List (Run1StaticGroupDerivation.Rule SupplyNode) :=
  state.ordinaryRules ++ compileStatic state.summaries state.values

def stateSeeds (state : State) : List (Run1StaticGroupDerivation.Fact SupplyNode) :=
  (GroupedRun1StaticComposition.seeds state.sources).map (embedFact ∘ Run1StaticDemandTreeCoverage.projectFact)

def supply (state : State) := Run1StaticGroupDerivation.solve (stateRules state) (stateSeeds state)

theorem requests_and_initials_do_not_supply (state : State)
    (initials : List Initial) (requests : List (Nat × Nat × Nat)) :
    (supply { state with analyzedInitials := initials, requested := requests }).facts =
      (supply state).facts := rfl

theorem no_sources_no_group (state : State) (absent : state.sources = []) :
    (supply state).facts = [] := by
  unfold supply stateSeeds
  rw [absent]
  exact Run1StaticGroupDerivation.solution_empty_without_seed (stateRules state)

def supplyRoot (state : State) (fact : Run1StaticGroupDerivation.Fact SupplyNode)
    (present : fact ∈ (supply state).facts) :
    { root : Run1StaticGroupDerivation.Fact SupplyNode // root ∈ stateSeeds state } :=
  Run1StaticGroupDerivation.solutionRoot (stateRules state) (stateSeeds state) fact present

def staticPool (state : State) : List (Run1StaticGroupDerivation.Fact SupplyNode) :=
  (supply state).facts.filter (fun fact => match fact.node.1 with
    | .staticField _ => true | .ordinary _ => false)

def eligibleSourceRoot (state : State) (fact : Run1StaticGroupDerivation.Fact SupplyNode)
    (present : fact ∈ (supply state).facts) :
    { source : GroupedRun1StaticComposition.Source // source ∈ state.sources ∧ source.eligible = true } :=
  let root := supplyRoot state fact present
  match found : Run1StaticGroupDerivation.select state.sources (fun source =>
    source.eligible = true ∧ embedFact (Run1StaticDemandTreeCoverage.projectFact source.fact) = root.val) with
  | some source => ⟨source.val, source.property.1, source.property.2.1⟩
  | none => False.elim (by
      obtain ⟨full, member, same⟩ := List.mem_map.mp root.property
      obtain ⟨source, active, sourceSame⟩ := List.mem_map.mp member
      obtain ⟨registered, eligible⟩ := List.mem_filter.mp active
      exact Run1StaticGroupDerivation.select_none state.sources _ found source registered
        ⟨eligible, by simpa only [Function.comp_apply, sourceSame] using same⟩)

theorem static_application_fires (state : State) (summary : Summary)
    (registered : summary ∈ state.summaries) (inputs : List Value)
    (available : ∀ value, value ∈ inputs → value ∈ state.values)
    (output : Value) (produced : output ∈ staticApply summary inputs)
    (conclusion : Run1StaticGroupDerivation.Fact SupplyNode)
    (marked : poolAtom output = some conclusion) (nonempty : poolInputs inputs ≠ [])
    (actual : ∀ fact, fact ∈ poolInputs inputs → fact ∈ (supply state).facts) :
    conclusion ∈ (supply state).facts := by
  have compiled := compileStatic_complete_application state.summaries state.values inputs summary registered
    available output produced conclusion marked nonempty
  exact (supply state).stable ⟨poolInputs inputs, conclusion⟩
    (List.mem_append_right _ compiled) ⟨nonempty, actual⟩

def addSource (state : State) (source : GroupedRun1StaticComposition.Source) : State :=
  { state with sources := source :: state.sources }

def addSummary (state : State) (summary : Summary) : State :=
  { state with summaries := summary :: state.summaries }

theorem summary_source_arrival_commutes (state : State)
    (source : GroupedRun1StaticComposition.Source) (summary : Summary) :
    (supply (addSummary (addSource state source) summary)).facts =
      (supply (addSource (addSummary state summary) source)).facts := rfl

structure BaseRequest where
  owner : Nat
  base : Nat
  group : Nat
  deriving DecidableEq, Repr

structure Interest where
  field : Nat
  cells : List Nat
  group : Nat
  deriving DecidableEq, Repr

/-- Ignore mx for interest. Do not invert a declared GEN as if it were FLOW. -/
def writerRequest (summary : Summary) (interest : Interest) : Option BaseRequest :=
  match summary.premises, summary.body with
  | [initial], .flow images _ =>
      match initial.slot, initial.mark with
      | .base base, .flow =>
          if summary.reached && decide (summary.targetField = interest.field) &&
              images.any (fun image => interest.cells.contains image.2) then
            some ⟨summary.owner, base, interest.group⟩
          else none
      | _, _ => none
  | _, _ => none

structure RequestState where
  writers : List Summary := []
  interests : List Interest := []
  deriving DecidableEq, Repr

def addWriter (state : RequestState) (writer : Summary) : RequestState :=
  { state with writers := writer :: state.writers }

def addInterest (state : RequestState) (interest : Interest) : RequestState :=
  { state with interests := interest :: state.interests }

def baseRequests (state : RequestState) : List BaseRequest :=
  (state.writers.flatMap (fun writer => state.interests.filterMap (writerRequest writer))).eraseDups

theorem writer_interest_arrival_commutes (state : RequestState) (writer : Summary) (interest : Interest) :
    baseRequests (addWriter (addInterest state interest) writer) =
      baseRequests (addInterest (addWriter state writer) interest) := rfl

theorem base_request_exact (state : RequestState) (request : BaseRequest) :
    request ∈ baseRequests state ↔ ∃ writer, writer ∈ state.writers ∧
      ∃ interest, interest ∈ state.interests ∧ writerRequest writer interest = some request := by
  simp [baseRequests]

theorem generated_writer_does_not_request_output_group (summary : Summary) (interest : Interest)
    (group : Nat) (cells : List Nat) (declared : summary.body = .generate group cells) :
    writerRequest summary interest = none := by
  cases h : summary.premises <;> simp [writerRequest, declared, h]

/-- Actual Base answers retain the real payload and mark; need alone answers nothing. -/
def answer (request : BaseRequest) (row : Row) : Option Value :=
  if row.port.callee = request.owner ∧ row.actual.slot = .base request.base ∧
      row.actual.mark = .group request.group then some row.actual else none

theorem answer_is_actual (request : BaseRequest) (row : Row) (value : Value)
    (answered : answer request row = some value) :
    value = row.actual ∧ value.mark = .group request.group := by
  unfold answer at answered
  split at answered
  · rename_i accepted
    have same := Option.some.inj answered
    subst value
    exact ⟨rfl, accepted.2.2⟩
  · contradiction

/-- Addition of static summary applications does not remove ordinary rules. -/
def fromReference (program : GroupedRun1StaticComposition.Program) (summaries : List Summary) (values : List Value) : State :=
  ⟨program.sources, program.groupRules.map embedRule, summaries, values, [], []⟩

def liftTree {Node : Type} [DecidableEq Node] (oldRules newRules : List (Run1StaticGroupDerivation.Rule Node))
    (seeds : List (Run1StaticGroupDerivation.Fact Node)) (preserved : ∀ rule, rule ∈ oldRules → rule ∈ newRules) :
    {fact : Run1StaticGroupDerivation.Fact Node} → Run1StaticGroupDerivation.Tree oldRules seeds fact → Run1StaticGroupDerivation.Tree newRules seeds fact
  | _, .source fact present => .source fact present
  | _, .operation rule declared productive children =>
      .operation rule (preserved rule declared) productive
        (fun fact present => liftTree oldRules newRules seeds preserved (children fact present))

def embedTree (rules : List (Run1StaticGroupDerivation.Rule GroupedRun1StaticComposition.Site))
    (seeds : List (Run1StaticGroupDerivation.Fact GroupedRun1StaticComposition.Site)) :
    {fact : Run1StaticGroupDerivation.Fact GroupedRun1StaticComposition.Site} →
    Run1StaticGroupDerivation.Tree rules seeds fact →
    Run1StaticGroupDerivation.Tree (rules.map embedRule) (seeds.map embedFact) (embedFact fact)
  | _, .source fact present => .source _ (List.mem_map.mpr ⟨fact, present, rfl⟩)
  | _, .operation rule declared productive children =>
      .operation (embedRule rule) (List.mem_map.mpr ⟨rule, declared, rfl⟩)
        (by simpa [embedRule] using productive)
        (fun input present =>
          match found : Run1StaticGroupDerivation.select rule.premises (fun prior => embedFact prior = input) with
          | some chosen => chosen.property.2 ▸ embedTree rules seeds (children chosen.val chosen.property.1)
          | none => False.elim (by
              obtain ⟨prior, registered, same⟩ := List.mem_map.mp present
              exact Run1StaticGroupDerivation.select_none rule.premises _ found prior registered same))

def referenceTree (program : GroupedRun1StaticComposition.Program) (summaries : List Summary) (values : List Value)
    {fact : Run1StaticDemandTreeCoverage.FullFact GroupedRun1StaticComposition.Site}
    (tree : Run1StaticGroupDerivation.Tree program.rules (GroupedRun1StaticComposition.seeds program.sources) fact) :
    Run1StaticGroupDerivation.Tree (stateRules (fromReference program summaries values))
      (stateSeeds (fromReference program summaries values)) (embedFact (Run1StaticDemandTreeCoverage.projectFact fact)) := by
  have embedded := embedTree program.groupRules program.groupSeeds
    (Run1StaticDemandTreeCoverage.projectTree program.rules (GroupedRun1StaticComposition.seeds program.sources) tree)
  have tree' := liftTree (program.groupRules.map embedRule)
    (stateRules (fromReference program summaries values)) (program.groupSeeds.map embedFact)
    (fun _rule present => List.mem_append_left _ present) embedded
  simpa [stateSeeds, fromReference, GroupedRun1StaticComposition.Program.groupSeeds, List.map_map, Function.comp_def] using tree'

/-- Semantic reference lemma only: rules are included explicitly, so this is
    not the new owner-pipeline compiler correspondence. New demand membership
    and exact B2 execution are constructed from a full
    witness. No target guide, helper permission, or precomputed answer is assumed.
    Unchanged ordinary positive primitives are the reference's finite compiler
    interface; compiling actual JVM/AP bodies to them is not proved here. -/
structure Coverage (program : GroupedRun1StaticComposition.Program) (summaries : List Summary) (values : List Value)
    (target : Run1StaticDemandTreeCoverage.FullFact GroupedRun1StaticComposition.Site) where
  supplied : embedFact (Run1StaticDemandTreeCoverage.projectFact target) ∈ (supply (fromReference program summaries values)).facts
  permission : embedFact (Run1StaticDemandTreeCoverage.projectFact target) ∈ Run1StaticDemandTreeCoverage.freezePointDemands
    (stateRules (fromReference program summaries values))
    (stateSeeds (fromReference program summaries values)) (program.targets.map (fun site => (site, 0)))
  backward : GroupedRun1StaticComposition.Replay program (.nonzero target.node.2) target

def completeWitness (program : GroupedRun1StaticComposition.Program) (summaries : List Summary) (values : List Value)
    {target : Run1StaticDemandTreeCoverage.FullFact GroupedRun1StaticComposition.Site}
    (tree : Run1StaticGroupDerivation.Tree program.rules (GroupedRun1StaticComposition.seeds program.sources) target)
    (observed : target.node.1 ∈ program.targets) : Coverage program summaries values target := by
  let projected := referenceTree program summaries values tree
  have observed' : (target.node.1, 0) ∈ program.targets.map (fun site => (site, 0)) :=
    List.mem_map.mpr ⟨target.node.1, observed, rfl⟩
  have permission := Run1StaticDemandTreeCoverage.treePointWitness _ _ _ projected observed'
  have same := permission.property.2
  exact ⟨Run1StaticGroupDerivation.solution_contains_tree _ _ projected, same ▸ permission.property.1,
    GroupedRun1StaticComposition.compileReplay program (.nonzero target.node.2) tree⟩

theorem finite_firing_bound (state : State) :
    (supply state).firings ≤ (stateRules state).length := Run1StaticGroupDerivation.solve_firing_bound _ _

/-- The exact source event remains the ordinary source adapter's responsibility.
    This function does not turn a query or ZERO-shaped data into a source. -/
def zeroEffect (source : GroupedRun1StaticComposition.Source) : Option Outcome :=
  if source.eligible && source.write.storeReached then
    some ⟨source.write.method, [], ⟨.staticField source.write.field, 0,
      .group source.write.mark.group, []⟩⟩
  else none

theorem zeroEffect_legitimate (source : GroupedRun1StaticComposition.Source) (output : Outcome)
    (produced : zeroEffect source = some output) :
    source.eligible = true ∧ source.write.storeReached = true ∧ output.originals = [] ∧
      output.value.mark = .group source.write.mark.group := by
  unfold zeroEffect at produced
  split at produced
  · rename_i active
    have flags := Bool.and_eq_true_iff.mp active
    have same := Option.some.inj produced
    subst output
    exact ⟨flags.1, flags.2, rfl, rfl⟩
  · contradiction

namespace Controls

def p : Port := ⟨1, 2, 10, 3, 7, 1⟩
def otherStage : Port := { p with stage := 4 }
def base (id : Nat) (mark : Mark) : Initial := ⟨.base id, [0,1,2], mark⟩
def stat (id : Nat) (mark : Mark) : Initial := ⟨.staticField id, [0,1,2], mark⟩
def original (id group : Nat) : Original := ⟨1, base id (.group group)⟩
def row (id group cell : Nat) (origins : Premise := []) : Row :=
  ⟨p, origins, ⟨.base id, cell, .group group, []⟩⟩
def writer : Summary := ⟨2, 20, true, [base 0 .flow], 8, .flow [(0,0),(1,1),(2,2)] []⟩
def fixed : Summary := ⟨2, 21, true, [base 0 (.group 5)], 8, .generate 7 [0]⟩
def nd : Summary := ⟨2, 22, true, [base 0 (.group 5),base 1 (.group 9)], 8, .generate 7 [0]⟩
def conditionalRows : List Row := [row 0 5 0 [original 0 5], row 1 9 0 [original 1 9]]
def copied : Summary := ⟨2, 23, true, [stat 8 .flow], 9, .flow [(0,0),(1,2)] []⟩
def input : Value := ⟨.staticField 8, 1, .group 5, []⟩
def inputOther : Value := ⟨.staticField 8, 1, .group 6, []⟩
def want : Interest := ⟨8,[0,1,2],5⟩
def requests : RequestState := ⟨[writer],[want]⟩
def cycleRules : List (Run1StaticGroupDerivation.Rule SupplyNode) :=
  [⟨[⟨(.staticField 8,0),5⟩],⟨(.staticField 9,0),5⟩⟩,
   ⟨[⟨(.staticField 9,0),5⟩],⟨(.staticField 8,0),5⟩⟩]

#guard decide (align nd.premises [(row 0 5 0).actual] = false)
#guard decide (applyCall nd p [row 0 5 0] = [])
#guard decide ((applyCall nd p conditionalRows).map Outcome.originals =
  [[original 0 5, original 1 9]])
#guard decide (applyCall nd p [row 0 5 0,
  { row 1 9 0 with port := otherStage }] = [])
#guard decide (applyCall nd p [row 0 5 0,row 1 5 0] = [])
#guard decide ((applyCall writer p [row 0 5 1]).map (fun result => result.value.mark) = [.group 5])
#guard decide ((applyCall writer p [row 0 5 1]).map (fun result => result.value.cell) = [1])
#guard decide ((applyCall fixed p [row 0 5 1]).map (fun result => result.value.mark) = [.group 7])
#guard decide (staticApply copied [input] = [⟨.staticField 9,2,.group 5,[]⟩])
#guard decide (staticApply copied [inputOther] = [⟨.staticField 9,2,.group 6,[]⟩])
#guard decide (staticApply copied [] = [])
#guard decide (staticApply { copied with reached := false } [input] = [])
#guard decide (staticApply { copied with premises := [base 0 .flow] } [input] = [])
#guard decide (writerRequest writer want = some ⟨2,0,5⟩)
#guard decide (writerRequest { writer with body := .flow [(0,0)] [5] } want = some ⟨2,0,5⟩)
#guard decide (writerRequest fixed want = none)
#guard decide (writerRequest copied want = none)
#guard decide (baseRequests requests = [⟨2,0,5⟩])
#guard decide (answer ⟨2,0,5⟩ (row 0 5 1) = some (row 0 5 1).actual)
#guard decide (answer ⟨2,0,5⟩ (row 0 6 1) = none)
#guard decide ((supply { ordinaryRules := cycleRules }).facts = [])
#guard decide ((supply { ordinaryRules := cycleRules, analyzedInitials := [stat 8 (.group 5)], requested := [(2,0,5)] }).facts = [])
#guard decide ((compileStatic [copied] [input]).length = 1)
#guard decide ((tupleRules copied []).length = 0)

def source : GroupedRun1StaticComposition.Source :=
  ⟨1, ⟨1,1000,8,⟨5,100⟩,true,true⟩,true⟩
def writeRule : Run1StaticGroupDerivation.Rule SupplyNode :=
  ⟨[⟨(.ordinary 1,0),5⟩],⟨(.staticField 8,1),5⟩⟩
def actualState : State :=
  { sources := [source], ordinaryRules := [writeRule], summaries := [copied], values := [input] }
def staticOutput : Run1StaticGroupDerivation.Fact SupplyNode := ⟨(.staticField 9,2),5⟩
def wrapperPort : Port := ⟨0,1,11,3,8,1⟩
def wrapperNd : Summary := { nd with owner := 1, premises := [base 0 (.group 5),base 1 (.group 9)] }
def leftCall : List Row :=
  [⟨wrapperPort,[],⟨.base 0,0,.group 5,[]⟩⟩,⟨wrapperPort,[],⟨.base 1,0,.flow,[]⟩⟩]
def rightCall : List Row :=
  [⟨wrapperPort,[],⟨.base 0,0,.flow,[]⟩⟩,⟨wrapperPort,[],⟨.base 1,0,.group 9,[]⟩⟩]
def fullCall : List Row :=
  [⟨wrapperPort,[],⟨.base 0,0,.group 5,[]⟩⟩,⟨wrapperPort,[],⟨.base 1,0,.group 9,[]⟩⟩]

#guard decide (staticOutput ∈ (supply actualState).facts)
#guard decide (staticOutput ∈ staticPool actualState)
#guard decide ((supply { actualState with sources := [] }).facts = [])
#guard decide ((supply { actualState with sources := [{ source with guardAccepted := false }] }).facts = [])
#guard decide (applyCall wrapperNd wrapperPort leftCall = [])
#guard decide (applyCall wrapperNd wrapperPort rightCall = [])
#guard decide ((applyCall wrapperNd wrapperPort fullCall).map Outcome.originals = [[]])
#guard decide (applyCall nd p [row 0 5 0, row 1 9 0, row 1 9 0] = [])
#guard decide (zeroEffect { source with guardAccepted := false } = none)
#guard decide (zeroEffect { source with write := { source.write with storeReached := false } } = none)
#guard decide ((zeroEffect source).map Outcome.originals = some [])
#guard decide (applyCall fixed p [row 0 5 1] ≠ []) -- canonical scope can admit a fixed output
#guard decide (valueMatches (base 0 (.group 5)) (row 0 5 1).actual = true)
#guard decide (valueMatches ⟨.base 0,[2],.group 5⟩ (row 0 5 1).actual = false)
#guard decide (staticApply { copied with body := .flow [(1,2)] [5] } [input] = [])

def markedCopy : Summary := ⟨2,24,true,[stat 8 (.group 5)],9,
  .marked 5 [([0],1),([1],2)]⟩
def markedNd : Summary := ⟨2,25,true,[stat 8 (.group 5),stat 9 (.group 6)],10,
  .marked 7 [([1,2],0),([2,1],2)]⟩
#guard decide (staticApply markedCopy [input] = [⟨.staticField 9,2,.group 5,[]⟩])
#guard decide (staticApply markedCopy [{ input with cell := 0 }] = [⟨.staticField 9,1,.group 5,[]⟩])
#guard decide (staticApply markedCopy [{ input with cell := 2 }] = [])
#guard decide (staticApply markedNd [input,⟨.staticField 9,2,.group 6,[]⟩] =
  [⟨.staticField 10,0,.group 7,[]⟩])
#guard decide (staticApply markedNd [input,⟨.staticField 9,1,.group 6,[]⟩] = [])
#guard decide (staticApply markedNd [input] = [])
#guard decide (staticApply { copied with body := .flow [(1,2)] [5] } [input] = [] ∧
  writerRequest { writer with body := .flow [(1,2)] [5] } ⟨8,[2],5⟩ = some ⟨2,0,5⟩ ∧
  staticApply markedCopy [input] = [⟨.staticField 9,2,.group 5,[]⟩])

def mixed : Summary := { nd with premises := [stat 8 (.group 5),base 1 (.group 9)] }
def mixedRows : List Row :=
  [⟨p,[⟨1,stat 8 (.group 5)⟩],⟨.staticField 8,1,.group 5,[]⟩⟩,
   row 1 9 0 [original 1 9]]
#guard decide ((applyCall mixed p mixedRows).map Outcome.originals =
  [[⟨1,stat 8 (.group 5)⟩,original 1 9]])
#guard decide (staticApply mixed [⟨.staticField 8,1,.group 5,[]⟩,(row 1 9 0).actual] = [])
#guard decide ((applyCall mixed p
  [⟨p,[],⟨.staticField 8,1,.group 5,[]⟩⟩,row 1 9 0]).map Outcome.originals = [[]])

/-- Named executable controls; exactly the cases checked above. -/
def controls : List (String × Bool) :=
  [ ("nd-alignment-missing-member", decide (align nd.premises [(row 0 5 0).actual] = false)),
    ("nd-application-missing-member", decide (applyCall nd p [row 0 5 0] = [])),
    ("complete-originals-preserved", decide ((applyCall nd p conditionalRows).map Outcome.originals =
        [[original 0 5, original 1 9]])),
    ("wrong-stage-rejected", decide (applyCall nd p [row 0 5 0,
        { row 1 9 0 with port := otherStage }] = [])),
    ("wrong-group-rejected", decide (applyCall nd p [row 0 5 0,row 1 5 0] = [])),
    ("flow-preserves-actual-group", decide ((applyCall writer p [row 0 5 1]).map (fun result => result.value.mark) = [.group 5])),
    ("flow-preserves-actual-cell", decide ((applyCall writer p [row 0 5 1]).map (fun result => result.value.cell) = [1])),
    ("gen-uses-declared-selector", decide ((applyCall fixed p [row 0 5 1]).map (fun result => result.value.mark) = [.group 7])),
    ("static-flow-image", decide (staticApply copied [input] = [⟨.staticField 9,2,.group 5,[]⟩])),
    ("static-flow-other-group", decide (staticApply copied [inputOther] = [⟨.staticField 9,2,.group 6,[]⟩])),
    ("no-input-no-static-copy", decide (staticApply copied [] = [])),
    ("unreached-copy-disabled", decide (staticApply { copied with reached := false } [input] = [])),
    ("base-premise-not-direct-pool", decide (staticApply { copied with premises := [base 0 .flow] } [input] = [])),
    ("base-bootstrap", decide (writerRequest writer want = some ⟨2,0,5⟩)),
    ("mx-does-not-block-interest", decide (writerRequest { writer with body := .flow [(0,0)] [5] } want = some ⟨2,0,5⟩)),
    ("gen-not-inverted", decide (writerRequest fixed want = none)),
    ("static-origin-not-base-request", decide (writerRequest copied want = none)),
    ("merged-base-request", decide (baseRequests requests = [⟨2,0,5⟩])),
    ("actual-base-answer", decide (answer ⟨2,0,5⟩ (row 0 5 1) = some (row 0 5 1).actual)),
    ("wrong-mark-not-answer", decide (answer ⟨2,0,5⟩ (row 0 6 1) = none)),
    ("rootless-cycle-no-supply", decide ((supply { ordinaryRules := cycleRules }).facts = [])),
    ("requests-initials-not-roots", decide ((supply { ordinaryRules := cycleRules, analyzedInitials := [stat 8 (.group 5)], requested := [(2,0,5)] }).facts = [])),
    ("actual-static-tuple-compiled", decide ((compileStatic [copied] [input]).length = 1)),
    ("empty-tuple-not-productive", decide ((tupleRules copied []).length = 0)),
    ("actual-source-feeds-static-copy", decide (staticOutput ∈ (supply actualState).facts)),
    ("actual-static-output-in-pool", decide (staticOutput ∈ staticPool actualState)),
    ("removed-source-no-supply", decide ((supply { actualState with sources := [] }).facts = [])),
    ("rejected-source-no-supply", decide ((supply { actualState with sources := [{ source with guardAccepted := false }] }).facts = [])),
    ("nd-left-half-not-complete", decide (applyCall wrapperNd wrapperPort leftCall = [])),
    ("nd-right-half-not-complete", decide (applyCall wrapperNd wrapperPort rightCall = [])),
    ("complete-nd-zero-output", decide ((applyCall wrapperNd wrapperPort fullCall).map Outcome.originals = [[]])),
    ("extra-member-rejected", decide (applyCall nd p [row 0 5 0, row 1 9 0, row 1 9 0] = [])),
    ("zero-shaped-unaccepted-source-not-root", decide (zeroEffect { source with guardAccepted := false } = none)),
    ("source-before-store-not-zero-effect", decide (zeroEffect { source with write := { source.write with storeReached := false } } = none)),
    ("valid-source-zero-effect", decide ((zeroEffect source).map Outcome.originals = some [])),
    ("ordinary-widening-allows-fixed-output", decide (applyCall fixed p [row 0 5 1] ≠ [])),
    ("broad-scope-keeps-actual-cell", decide (valueMatches (base 0 (.group 5)) (row 0 5 1).actual = true)),
    ("disjoint-represented-scope-rejected", decide (valueMatches ⟨.base 0,[2],.group 5⟩ (row 0 5 1).actual = false)),
    ("mx-blocks-flow-mark", decide (staticApply { copied with body := .flow [(1,2)] [5] } [input] = [])),
    ("marked-copy-actual-image", decide (staticApply markedCopy [input] = [⟨.staticField 9,2,.group 5,[]⟩])),
    ("marked-copy-different-image", decide (staticApply markedCopy [{ input with cell := 0 }] = [⟨.staticField 9,1,.group 5,[]⟩])),
    ("missing-marked-image-rejected", decide (staticApply markedCopy [{ input with cell := 2 }] = [])),
    ("marked-nd-complete-image", decide (staticApply markedNd [input,⟨.staticField 9,2,.group 6,[]⟩] =
        [⟨.staticField 10,0,.group 7,[]⟩])),
    ("wrong-nd-image-rejected", decide (staticApply markedNd [input,⟨.staticField 9,1,.group 6,[]⟩] = [])),
    ("marked-nd-short-tuple-rejected", decide (staticApply markedNd [input] = [])),
    ("cleaner-complement-interest-and-marked-image", decide (staticApply { copied with body := .flow [(1,2)] [5] } [input] = [] ∧
        writerRequest { writer with body := .flow [(1,2)] [5] } ⟨8,[2],5⟩ = some ⟨2,0,5⟩ ∧
        staticApply markedCopy [input] = [⟨.staticField 9,2,.group 5,[]⟩])),
    ("mixed-originals-all-retained", decide ((applyCall mixed p mixedRows).map Outcome.originals =
        [[⟨1,stat 8 (.group 5)⟩,original 1 9]])),
    ("mixed-premise-not-direct-pool", decide (staticApply mixed [⟨.staticField 8,1,.group 5,[]⟩,(row 1 9 0).actual] = [])),
    ("mixed-complete-root-binding-zero", decide ((applyCall mixed p
        [⟨p,[],⟨.staticField 8,1,.group 5,[]⟩⟩,row 1 9 0]).map Outcome.originals = [[]])) ]

end Controls
end ApSpec.Run1OrdinaryStaticSupply
