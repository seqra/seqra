import Std

/-
Selected run-1 alias admission, not a change to AP transfer. Classify each raw
CALLEE SUMMARY arm: equal nonzero bases and equal full field paths; FLOW needs
two STAR tails; TAINT needs equal actual marks and ignores tails. Layer and both
exclusion headers do not enter classification. ZERO summaries are effects.

Packed rows are an executable scalar view of trie payloads. The proofs apply
to this decoder, not to the Kotlin decoder or its storage. Both exclusion
headers, tails and layers stay on the original leaves. Summary/demand callbacks
stay unchanged; only aliases use the effect partition. This proves the SELECTED
contract, not equivalence to the current overly broad DEMAND bypass.

Stages below model one existing output branch at a time. Their AP transforms
are inputs, not a proof of cleaners, binding or cuts. The adapter must carry the
same saved state on every branch. New local sources/GEN are effects. UNRESOLVED
keeps the existing MicroEdge.isIdentity tag; constructor/no-op policy is an
explicit existing producer tag. No new unresolved classifier, FULL guard,
native-record, reversal, field-policy or static-publication contract is selected.
-/
namespace ApSpec.Run1SummaryAliasAdmission
set_option autoImplicit false

inductive Base where
  | zero | arg (index : Nat) | receiver | ret | local (index : Nat) | static
  deriving DecidableEq, BEq, Repr
inductive Tail where
  | star | exact | any | anyTaint
  deriving DecidableEq, BEq, Repr
inductive Layer where
  | normal | demand
  deriving DecidableEq, BEq, Repr
/-- FULL equality includes the owner AND the full name. -/
inductive Mark where
  | group (owner : Nat)
  | full (owner name : Nat)
  deriving DecidableEq, BEq, Repr
inductive Payload where
  | flow | taint (mark : Mark) | reach
  deriving DecidableEq, BEq, Repr
structure Header where
  base : Base
  path : List Nat
  tail : Tail
  layer : Layer
  fieldExclusions : List Nat
  markExclusions : List Mark
  deriving DecidableEq, BEq, Repr
structure Leaf where
  header : Header
  payload : Payload
  deriving DecidableEq, BEq, Repr

/-- No prefix, subset, layer or exclusion-header equality test is used. -/
def noMemoryEffect (premise result : Leaf) : Bool :=
  decide (premise.header.base ≠ .zero) &&
  decide (premise.header.base = result.header.base) &&
  decide (premise.header.path = result.header.path) &&
  match premise.payload, result.payload with
  | .flow, .flow => decide (premise.header.tail = .star ∧ result.header.tail = .star)
  | .taint first, .taint second => decide (first = second)
  | _, _ => false

/-- A proposition form of the exact selected scalar rule. -/
def SelectedScalar (p r : Leaf) : Prop :=
  p.header.base ≠ .zero ∧ p.header.base = r.header.base ∧ p.header.path = r.header.path ∧
    match p.payload, r.payload with
    | .flow, .flow => p.header.tail = .star ∧ r.header.tail = .star
    | .taint first, .taint second => first = second
    | _, _ => False

theorem scalar_classifier_exact (p r : Leaf) : noMemoryEffect p r = true ↔ SelectedScalar p r := by
  cases hp : p.payload <;> cases hr : r.payload <;>
    simp [noMemoryEffect, SelectedScalar, hp, hr, and_assoc]

@[simp] theorem zero_summary_is_effect (p r : Leaf) (h : p.header.base = .zero) :
    noMemoryEffect p r = false := by simp [noMemoryEffect, h]
@[simp] theorem different_bases_are_effects (p r : Leaf) (h : p.header.base ≠ r.header.base) :
    noMemoryEffect p r = false := by simp [noMemoryEffect, h]
@[simp] theorem different_paths_are_effects (p r : Leaf) (h : p.header.path ≠ r.header.path) :
    noMemoryEffect p r = false := by simp [noMemoryEffect, h]

def withAnnotations (leaf : Leaf) (layer : Layer) (fields : List Nat) (marks : List Mark) : Leaf :=
  {leaf with header := {leaf.header with layer := layer, fieldExclusions := fields, markExclusions := marks}}
def withTail (leaf : Leaf) (tail : Tail) : Leaf :=
  {leaf with header := {leaf.header with tail := tail}}

theorem classification_ignores_layer_and_headers (p r : Leaf)
    (pl rl : Layer) (pe re : List Nat) (pm rm : List Mark) :
    noMemoryEffect (withAnnotations p pl pe pm) (withAnnotations r rl re rm) =
      noMemoryEffect p r := rfl

theorem taint_classification_ignores_both_tails (p r : Leaf) (first second : Mark)
    (hp : p.payload = .taint first) (hr : r.payload = .taint second) (pt rt : Tail) :
    noMemoryEffect (withTail p pt) (withTail r rt) = noMemoryEffect p r := by
  simp [noMemoryEffect, withTail, hp, hr]

theorem distinct_actual_marks_are_effects (p r : Leaf) (first second : Mark)
    (hp : p.payload = .taint first) (hr : r.payload = .taint second) (h : first ≠ second) :
    noMemoryEffect p r = false := by simp [noMemoryEffect, hp, hr, h]

/-- One row has one complete path/tail/header and several payload atoms. A
    production decoder must separate exact/any payloads into their proper rows.
    Empty reference rows denote no fact; no runtime empty-tree factory is used. -/
structure PackedRow where
  header : Header
  payloads : List Payload
  deriving DecidableEq, BEq, Repr

def decodeRow (row : PackedRow) : List Leaf :=
  row.payloads.map fun payload => ⟨row.header, payload⟩
def decode (rows : List PackedRow) : List Leaf := rows.flatMap decodeRow

def selectRow (p : Leaf) (want : Bool) (row : PackedRow) : PackedRow :=
  {row with payloads := row.payloads.filter fun payload =>
    noMemoryEffect p ⟨row.header, payload⟩ == want}
def noEffectRows (p : Leaf) (rows : List PackedRow) : List PackedRow :=
  rows.map (selectRow p true)
def effectRows (p : Leaf) (rows : List PackedRow) : List PackedRow :=
  rows.map (selectRow p false)

@[simp] theorem selectRow_keeps_complete_header (p : Leaf) (want : Bool) (row : PackedRow) :
    (selectRow p want row).header = row.header := rfl
@[simp] theorem decode_select_noEffect (p : Leaf) (row : PackedRow) :
    decodeRow (selectRow p true row) = (decodeRow row).filter (noMemoryEffect p) := by
  simp [decodeRow, selectRow, List.filter_map, Function.comp_def]
@[simp] theorem decode_select_effect (p : Leaf) (row : PackedRow) :
    decodeRow (selectRow p false row) =
      (decodeRow row).filter (fun leaf => !noMemoryEffect p leaf) := by
  simp [decodeRow, selectRow, List.filter_map, Function.comp_def]

theorem packed_noEffect_exact (p : Leaf) (rows : List PackedRow) :
    decode (noEffectRows p rows) = (decode rows).filter (noMemoryEffect p) := by
  simp [decode, noEffectRows, List.flatMap_map, List.filter_flatMap]
theorem packed_effect_exact (p : Leaf) (rows : List PackedRow) :
    decode (effectRows p rows) = (decode rows).filter (fun leaf => !noMemoryEffect p leaf) := by
  simp [decode, effectRows, List.flatMap_map, List.filter_flatMap]

/-- Duplicate multiplicities also stay unchanged. Bucket concatenation need
    not keep the original interleaving or batch-dependent callback counts. -/
theorem partition_lossless (p : Leaf) (rows : List PackedRow) :
    List.Perm (decode (noEffectRows p rows) ++ decode (effectRows p rows)) (decode rows) := by
  rw [packed_noEffect_exact, packed_effect_exact]
  exact List.filter_append_perm _ _

theorem partition_union (p : Leaf) (rows : List PackedRow) (leaf : Leaf) :
    leaf ∈ decode (noEffectRows p rows) ∨ leaf ∈ decode (effectRows p rows) ↔
      leaf ∈ decode rows := by
  have same : leaf ∈ decode (noEffectRows p rows) ++ decode (effectRows p rows) ↔ leaf ∈ decode rows :=
    (partition_lossless p rows).mem_iff
  simpa only [List.mem_append] using same

theorem partition_disjoint (p : Leaf) (rows : List PackedRow) (leaf : Leaf)
    (left : leaf ∈ decode (noEffectRows p rows)) (right : leaf ∈ decode (effectRows p rows)) : False := by
  rw [packed_noEffect_exact] at left
  rw [packed_effect_exact] at right
  have yes := (List.mem_filter.mp left).2
  have no := (List.mem_filter.mp right).2
  simp [yes] at no

theorem partition_preserves_leaf (p : Leaf) (rows : List PackedRow) (leaf : Leaf)
    (present : leaf ∈ decode (noEffectRows p rows) ∨ leaf ∈ decode (effectRows p rows)) :
    ∃ original ∈ decode rows, original = leaf ∧
      original.header.layer = leaf.header.layer ∧
      original.header.fieldExclusions = leaf.header.fieldExclusions ∧
      original.header.markExclusions = leaf.header.markExclusions := by
  exact ⟨leaf, (partition_union p rows leaf).mp present, rfl, rfl, rfl, rfl⟩

/-- Direct scalar reference for the selected contract. -/
def scalarAliases (p : Leaf) (alias : Leaf → List Leaf) : List Leaf → List Leaf
  | [] => []
  | leaf :: rest => (if noMemoryEffect p leaf then [] else alias leaf) ++ scalarAliases p alias rest

theorem scalar_alias_filter (p : Leaf) (alias : Leaf → List Leaf) (leaves : List Leaf) :
    scalarAliases p alias leaves =
      (leaves.filter fun leaf => !noMemoryEffect p leaf).flatMap alias := by
  induction leaves with
  | nil => rfl
  | cons leaf rest ih =>
      cases selected : noMemoryEffect p leaf <;> simp [scalarAliases, selected, ih]

def packedAliases (p : Leaf) (alias : Leaf → List Leaf) (rows : List PackedRow) : List Leaf :=
  (decode (effectRows p rows)).flatMap alias

theorem scalar_packed_alias_equivalence (p : Leaf) (alias : Leaf → List Leaf) (rows : List PackedRow) :
    packedAliases p alias rows = scalarAliases p alias (decode rows) := by
  rw [packedAliases, packed_effect_exact, scalar_alias_filter]

structure Observations where
  summaries : List Leaf
  demands : List Leaf
  aliases : List Leaf
  deriving DecidableEq, BEq, Repr

def selectedScalarOutputs (p : Leaf) (demand alias : Leaf → List Leaf) (leaves : List Leaf) : Observations :=
  ⟨leaves, leaves.flatMap demand, scalarAliases p alias leaves⟩
def selectedPackedOutputs (p : Leaf) (demand alias : Leaf → List Leaf) (rows : List PackedRow) : Observations :=
  ⟨decode rows, (decode rows).flatMap demand, packedAliases p alias rows⟩

theorem selected_scalar_packed_outputs (p : Leaf) (demand alias : Leaf → List Leaf) (rows : List PackedRow) :
    selectedPackedOutputs p demand alias rows = selectedScalarOutputs p demand alias (decode rows) := by
  simp only [selectedPackedOutputs, selectedScalarOutputs, scalar_packed_alias_equivalence]
@[simp] theorem summary_retention_unchanged (p : Leaf) (demand alias : Leaf → List Leaf) (rows : List PackedRow) :
    (selectedPackedOutputs p demand alias rows).summaries = decode rows := rfl
@[simp] theorem demand_outputs_unchanged (p : Leaf) (demand alias : Leaf → List Leaf) (rows : List PackedRow) :
    (selectedPackedOutputs p demand alias rows).demands = (decode rows).flatMap demand := rfl

inductive Admission where
  | unclassified | noEffect | effect
  deriving DecidableEq, BEq, Repr

def classifySummary (p r : Leaf) : Admission :=
  if noMemoryEffect p r then .noEffect else .effect
/-- Unknown never skips. The run-1 layer cannot restore alias permission. -/
def run1Admits (flag : Admission) (_layer : Layer) : Bool :=
  match flag with
  | .noEffect => false
  | .unclassified | .effect => true

@[simp] theorem demand_does_not_bypass : run1Admits .noEffect .demand = false := rfl
@[simp] theorem normal_does_not_bypass : run1Admits .noEffect .normal = false := rfl
@[simp] theorem unclassified_never_skips (layer : Layer) : run1Admits .unclassified layer = true := rfl
theorem run1_admission_layer_independent (flag : Admission) (first second : Layer) :
    run1Admits flag first = run1Admits flag second := rfl

/-- Existing later/FULL guard. Its classifier and native/reversal adapters are
    outside this run-1 model and are not replaced by the selected predicate. -/
def existingLaterAdmits (identity : Bool) (layer : Layer) : Bool := decide (layer = .demand) || !identity
def scopedAdmits (run1 : Bool) (flag : Admission) (legacy : Bool) (layer : Layer) : Bool :=
  if run1 then run1Admits flag layer else existingLaterAdmits legacy layer
@[simp] theorem later_guard_unchanged (flag : Admission) (legacy : Bool) (layer : Layer) :
    scopedAdmits false flag legacy layer = existingLaterAdmits legacy layer := rfl

/-- This witness records the intentional semantic change in run-1. It makes
    no equivalence claim to the current DEMAND override. -/
theorem selected_differs_from_old_demand_bypass :
    scopedAdmits true .noEffect true .demand = false ∧
    existingLaterAdmits true .demand = true := by decide

inductive Producer where
  | calleeSummary (premise result : Leaf)
  | localSourceOrGen
  | unresolved (existingIsIdentity : Bool)
  | existingConstructorNoOp
  | unclassified
  deriving DecidableEq, Repr
/-- Existing producer tags are adapter inputs, not a new unresolved classifier. -/
def producerAdmission : Producer → Admission
  | .calleeSummary p r => classifySummary p r
  | .localSourceOrGen => .effect
  | .unresolved true | .existingConstructorNoOp => .noEffect
  | .unresolved false => .effect
  | .unclassified => .unclassified
@[simp] theorem local_generation_is_effect : producerAdmission .localSourceOrGen = .effect := rfl
@[simp] theorem unresolved_keeps_existing_tag (flag : Bool) :
    producerAdmission (.unresolved flag) = (if flag then .noEffect else .effect) := by cases flag <;> rfl
@[simp] theorem existing_noOp_tag : producerAdmission .existingConstructorNoOp = .noEffect := rfl
theorem zero_callee_producer_is_effect (p r : Leaf) (h : p.header.base = .zero) :
    producerAdmission (.calleeSummary p r) = .effect := by
  simp [producerAdmission, classifySummary, zero_summary_is_effect p r h]

structure CallItem where
  current : Leaf
  admission : Admission
  callerLayer : Layer
  pendingDemotion : Bool
  deriving DecidableEq, BEq, Repr
structure SavedCallState where
  admission : Admission
  callerLayer : Layer
  pendingDemotion : Bool
  deriving DecidableEq, BEq, Repr

def save (item : CallItem) : SavedCallState := ⟨item.admission, item.callerLayer, item.pendingDemotion⟩
def resume (state : SavedCallState) (current : Leaf) : CallItem :=
  ⟨current, state.admission, state.callerLayer, state.pendingDemotion⟩
def carryOutput (item : CallItem) (result : Leaf) : CallItem := {item with current := result}
/-- Equal current values cannot erase a distinct raw-summary admission tag.
    Runtime sets/maps must retain this coordinate in a mixed-arm batch. -/
theorem same_current_distinct_flags_remain_distinct (current : Leaf) (layer : Layer) (pending : Bool) :
    (⟨current, .noEffect, layer, pending⟩ : CallItem) ≠ ⟨current, .effect, layer, pending⟩ := by
  intro same
  have impossible := congrArg CallItem.admission same
  cases impossible

theorem saved_distinct_flags_remain_distinct (layer : Layer) (pending : Bool) :
    (⟨.noEffect, layer, pending⟩ : SavedCallState) ≠ ⟨.effect, layer, pending⟩ := by
  intro same
  have impossible := congrArg SavedCallState.admission same
  cases impossible

/-- Existing pending demotion stays deferred until call exit. It must not
    clear the selected flag or change field processing inside the call. -/
def markPendingDemotion (item : CallItem) : CallItem := {item with pendingDemotion := true}
def finish (item : CallItem) : CallItem :=
  if item.pendingDemotion then
    {item with current := withAnnotations item.current .demand item.current.header.fieldExclusions item.current.header.markExclusions}
  else item

@[simp] theorem finish_keeps_admission (item : CallItem) : (finish item).admission = item.admission := by
  simp [finish]; split <;> rfl

theorem finish_pending_demotes (item : CallItem) (pending : item.pendingDemotion = true) :
    (finish item).current.header.layer = .demand := by
  simp [finish, pending, withAnnotations]

theorem finish_keeps_both_exclusions (item : CallItem) :
    (finish item).current.header.fieldExclusions = item.current.header.fieldExclusions ∧
    (finish item).current.header.markExclusions = item.current.header.markExclusions := by
  unfold finish
  split <;> exact ⟨rfl, rfl⟩

/-- A genuinely new producer replaces the tag. Binding and rewriter outputs
    use carryOutput instead. Existing producer semantics are adapter obligations. -/
def newProducerOutput (producer : Producer) (item : CallItem) (result : Leaf) : CallItem :=
  {item with current := result, admission := producerAdmission producer}

@[simp] theorem local_source_does_not_inherit_noEffect (item : CallItem) (result : Leaf) :
    (newProducerOutput .localSourceOrGen item result).admission = .effect := rfl

inductive Step where
  | bind (result : Leaf) | cleaner (result : Leaf) | cut (result : Leaf)
  | pendingDemotion | pointReply (result : Leaf)
  deriving DecidableEq, Repr

def advance (item : CallItem) : Step → CallItem
  | .bind result | .cleaner result | .cut result => carryOutput item result
  | .pendingDemotion => markPendingDemotion item
  | .pointReply result => resume (save item) result

def runStages (item : CallItem) (steps : List Step) : CallItem := steps.foldl advance item

@[simp] theorem stage_keeps_admission (item : CallItem) (step : Step) :
    (advance item step).admission = item.admission := by cases step <;> rfl
@[simp] theorem stage_keeps_caller_layer (item : CallItem) (step : Step) :
    (advance item step).callerLayer = item.callerLayer := by cases step <;> rfl

theorem stages_keep_admission (item : CallItem) (steps : List Step) :
    (runStages item steps).admission = item.admission := by
  induction steps generalizing item with
  | nil => rfl
  | cons first rest ih =>
      simpa only [runStages, List.foldl_cons, stage_keeps_admission] using ih (advance item first)
theorem stages_keep_caller_layer (item : CallItem) (steps : List Step) :
    (runStages item steps).callerLayer = item.callerLayer := by
  induction steps generalizing item with
  | nil => rfl
  | cons first rest ih =>
      simpa only [runStages, List.foldl_cons, stage_keeps_caller_layer] using ih (advance item first)
theorem pending_demotion_never_resets (item : CallItem) (steps : List Step)
    (pending : item.pendingDemotion = true) : (runStages item steps).pendingDemotion = true := by
  induction steps generalizing item with
  | nil => exact pending
  | cons first rest ih =>
      apply ih (advance item first)
      cases first <;> simp_all [advance, carryOutput, markPendingDemotion, resume, save]
@[simp] theorem save_resume_keeps_full_state (item : CallItem) (result : Leaf) :
    save (resume (save item) result) = save item := rfl

theorem noEffect_stays_skipped_after_stages (item : CallItem) (steps : List Step)
    (known : item.admission = .noEffect) :
    run1Admits (runStages item steps).admission (runStages item steps).current.header.layer = false := by
  rw [stages_keep_admission, known]
  rfl
theorem raw_effect_survives_binding (p r bound : Leaf) (layer : Layer)
    (effect : noMemoryEffect p r = false) :
    (carryOutput ⟨r, classifySummary p r, layer, false⟩ bound).admission = .effect := by
  simp [carryOutput, classifySummary, effect]

/-! Finite controls use kernel-reduced decide, not native evaluation. All raw
    tail pairs are tested; this does not expand the legal runtime fact grammar. -/
def testLeaf (base : Base) (path : List Nat) (tail : Tail) (payload : Payload)
    (layer : Layer := .normal) : Leaf :=
  ⟨⟨base, path, tail, layer, [31], [.group 41]⟩, payload⟩
def tails : List Tail := [.star, .exact, .any, .anyTaint]
def tailControls : List Bool := tails.flatMap fun first => tails.map fun second =>
  noMemoryEffect (testLeaf (.arg 0) [7] first (.taint (.group 1)))
    (testLeaf (.arg 0) [7] second (.taint (.group 1)) .demand) &&
  (noMemoryEffect (testLeaf (.arg 0) [7] first .flow)
    (testLeaf (.arg 0) [7] second .flow .demand) == (first == .star && second == .star))
theorem all_tail_combinations_control : tailControls.all id = true := by decide

def mixedPremise : Leaf := testLeaf (.arg 0) [7] .exact (.taint (.group 1))
def mixedRows : List PackedRow := [
  ⟨(testLeaf (.arg 0) [7] .any .flow .demand).header, [.taint (.group 1), .taint (.group 2)]⟩,
  ⟨(testLeaf (.arg 0) [8] .exact .flow).header, [.taint (.group 1)]⟩]
theorem mixed_path_and_mark_partition_control :
    (decode (noEffectRows mixedPremise mixedRows)).map Leaf.payload = [.taint (.group 1)] ∧
    (decode (effectRows mixedPremise mixedRows)).map Leaf.payload =
      [.taint (.group 2), .taint (.group 1)] := by decide

theorem full_mark_and_group_controls :
    noMemoryEffect (testLeaf (.arg 0) [] .exact (.taint (.full 1 10)))
      (testLeaf (.arg 0) [] .any (.taint (.full 1 11)) .demand) = false ∧
    noMemoryEffect (testLeaf (.arg 0) [] .exact (.taint (.full 1 10)))
      (testLeaf (.arg 0) [] .any (.taint (.full 1 10)) .demand) = true ∧
    noMemoryEffect (testLeaf (.arg 0) [] .exact (.taint (.group 1)))
      (testLeaf (.arg 0) [] .any (.taint (.full 1 10)) .demand) = false := by decide

theorem independent_headers_control :
    noMemoryEffect (withAnnotations mixedPremise .demand [1,2] [.full 8 9])
      (withAnnotations (withTail mixedPremise .any) .normal [3] [.group 5]) = true := by decide

def rawArgument : Leaf := testLeaf (.arg 0) [] .exact (.taint (.group 1))
def rawReturn : Leaf := testLeaf .ret [] .any (.taint (.group 1))
def collapsedCaller : Leaf := testLeaf (.local 9) [] .any (.taint (.group 1)) .demand
theorem arg_return_caller_collapse_control :
    noMemoryEffect rawArgument rawReturn = false ∧
    noMemoryEffect collapsedCaller collapsedCaller = true ∧
    (runStages ⟨rawReturn, classifySummary rawArgument rawReturn, .normal, false⟩
      [.bind collapsedCaller, .cleaner collapsedCaller, .pendingDemotion,
       .pointReply collapsedCaller]).admission = .effect ∧
    (runStages ⟨rawReturn, classifySummary rawArgument rawReturn, .normal, false⟩
      [.bind collapsedCaller, .cleaner collapsedCaller, .pendingDemotion,
       .pointReply collapsedCaller]).pendingDemotion = true := by decide

/-- The final layer change also keeps admission and therefore cannot reopen
    the alias stage after any modeled branch, cleaner or late reply. -/
theorem final_demotion_does_not_reopen_aliases (item : CallItem) (steps : List Step)
    (known : item.admission = .noEffect) :
    run1Admits (finish (runStages item steps)).admission
      (finish (runStages item steps)).current.header.layer = false := by
  rw [finish_keeps_admission, stages_keep_admission, known]
  rfl

#print axioms scalar_classifier_exact
#print axioms partition_lossless
#print axioms partition_disjoint
#print axioms selected_scalar_packed_outputs
#print axioms stages_keep_admission
#print axioms pending_demotion_never_resets
#print axioms final_demotion_does_not_reopen_aliases

end ApSpec.Run1SummaryAliasAdmission
