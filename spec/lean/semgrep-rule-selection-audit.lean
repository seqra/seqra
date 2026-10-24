import ApSpec.SemgrepProviderSelection
import ApSpec.RuleSpecializationSelection
import ApSpec.SemgrepReducedMarks
import ApSpec.RuleSelectionEpoch
import ApSpec.RuleMarkCoverage
import ApSpec.RuleProviderReplay
import ApSpec.SemgrepReducedMarksTermination
import ApSpec.SemgrepRuleCompaction
import ApSpec.SemgrepMetadataProjection
import ApSpec.RuleProviderComposition
import Lean

open Lean Elab Command

run_elab do
  let owners := #[`ApSpec.SemgrepProviderSelection, `ApSpec.RuleSpecializationSelection,
    `ApSpec.SemgrepReducedMarks, `ApSpec.RuleSelectionEpoch,
    `ApSpec.RuleMarkCoverage, `ApSpec.RuleProviderReplay,
    `ApSpec.SemgrepReducedMarksTermination,
    `ApSpec.SemgrepRuleCompaction, `ApSpec.SemgrepMetadataProjection,
    `ApSpec.RuleProviderComposition]
  let env ← getEnv
  let mut total : Nat := 0
  for owner in owners do
    let mut count : Nat := 0
    for (name, _) in env.constants.toList do
      let module := match env.getModuleIdxFor? name with
        | some index => env.header.moduleNames[index.toNat]!
        | none => Name.anonymous
      if module == owner then
        count := count + 1
        let axioms ← collectAxioms name
        let forbidden := axioms.filter (fun ax => ax != `propext && ax != `Quot.sound)
        if !forbidden.isEmpty then throwError "Unexpected axioms: {name}: {forbidden}"
    if count == 0 then throwError "No declarations audited for {owner}"
    total := total + count
    logInfo m!"Audited {owner}: {count} declarations."
  logInfo m!"Total selected provider/reduction declarations: {total}; no unexpected axioms."

namespace BindingControls
open ApSpec.RuleSpecializationSelection
def checks : List Bool := [
  decide (product20.length = 400),
  decide ((bind productSpecializer qFull rejectedProduct).length = 400),
  decide ((late productSpecializer qFull exactPolicy [rejectedProduct]).binderCalls = 1 ∧
    (early productSpecializer qFull exactPolicy [rejectedProduct]).binderCalls = 0 ∧
    (late productSpecializer qFull exactPolicy [rejectedProduct]).contextVisits = 400 ∧
    (early productSpecializer qFull exactPolicy [rejectedProduct]).contextVisits = 0),
  decide ((early productSpecializer qFull exactPolicy [⟨none,3⟩]).variants =
      (late productSpecializer qFull exactPolicy [⟨none,3⟩]).variants),
  decide ((early productSpecializer qFull (fun _ _ => true) [rejectedProduct]).contextVisits =
      (late productSpecializer qFull (fun _ _ => true) [rejectedProduct]).contextVisits),
  decide ((early productSpecializer qAllRelevant exactPolicy [rejectedProduct]).contextVisits = 400),
  decide ((lateSink oneSpecializer qFull exactPolicy fallbackItems 0).variants.map (·.reportId) = [.generated 2] ∧
    (earlySink oneSpecializer qFull exactPolicy fallbackItems 0).variants.map (·.reportId) = [.generated 1]),
  decide ((lateSink oneSpecializer qFull exactPolicy fallbackItems 0).variants ≠
      (earlySink oneSpecializer qFull exactPolicy fallbackItems 0).variants),
  decide ((guardedSink oneSpecializer qFull exactPolicy fallbackItems 0).variants.map (·.reportId) = [.generated 2] ∧
    (guardedSink oneSpecializer qFull exactPolicy fallbackItems 0).binderCalls = 2),
  decide ((guardedSink oneSpecializer qFull exactPolicy explicitItems 42).variants.map (·.reportId) = [.explicit 200] ∧
    (guardedSink oneSpecializer qFull exactPolicy explicitItems 42).counter = 42 ∧
    (guardedSink oneSpecializer qFull exactPolicy explicitItems 42).binderCalls = 1 ∧
    (lateSink oneSpecializer qFull exactPolicy explicitItems 42).binderCalls = 2),
  decide ((early labelledSpecializer qFull exactPolicy [⟨some 7,10⟩,⟨some 8,99⟩,⟨some 7,20⟩]).variants =
      [⟨some 7,qFull,1,10⟩,⟨some 7,qFull,1,10⟩,
       ⟨some 7,qFull,1,20⟩,⟨some 7,qFull,1,20⟩]),
  decide ((early labelledSpecializer qFull exactPolicy [⟨some 7,10⟩]).variants.map (·.payload) = [10,10] ∧
    (early labelledSpecializer qGroup exactPolicy [⟨some 7,10⟩]).variants.map (·.payload) = [11,11]),
  decide ((guardedSink productSpecializer qFull exactPolicy [⟨rejectedProduct,.explicit 100⟩] 0).contextVisits = 0 ∧
    (lateSink productSpecializer qFull exactPolicy [⟨rejectedProduct,.explicit 100⟩] 0).contextVisits = 400),
  decide ((guardedSink oneSpecializer qFull exactPolicy
      [⟨⟨some 8,10⟩,.cwe 100⟩,⟨⟨some 7,20⟩,.cwe 200⟩] 42).variants.map (·.reportId) = [.cwe 200] ∧
    (guardedSink oneSpecializer qFull exactPolicy
      [⟨⟨some 8,10⟩,.cwe 100⟩,⟨⟨some 7,20⟩,.cwe 200⟩] 42).counter = 42),
  decide ((guardedTrace oneSpecializer exactPolicy mixedCounterTrace 0).variants.map (·.reportId) =
      [.generated 2,.explicit 200,.explicit 300,.generated 3] ∧
    (guardedTrace oneSpecializer exactPolicy mixedCounterTrace 0).counter = 3 ∧
    (guardedTrace oneSpecializer exactPolicy mixedCounterTrace 0).binderCalls = 4 ∧
    (lateTrace oneSpecializer exactPolicy mixedCounterTrace 0).binderCalls = 5)
]
end BindingControls

namespace MarkControls
open ApSpec.SemgrepReducedMarks ApSpec.SemgrepReducedMarks.Controls
def checks : List Bool := [
  decide (fold [] (.cm arg0 t) = .truth false),
  decide (fold [] (.neg (.cm arg0 t)) = .truth true),
  decide (fold [] (.cm anyFieldArg0 t) = .truth false),
  decide (fold [] (.neg (.cm anyFieldArg0 t)) = .truth true),
  decide (fold [] (.cm anyAccessorArg0 t) = .truth false),
  decide (fold [] (.neg (.cm anyAccessorArg0 t)) = .truth true),
  decide (fold [u] (.conj (.cm arg0 t) (.cm arg1 u)) = .truth false),
  decide (fold [u] (.disj (.cm arg0 t) (.cm arg1 u)) = .cm arg1 u),
  decide (fold [sameNameOtherOwner] (.cm arg0 t) = .truth false),
  decide (fold [⟨⟨1,true⟩,10⟩] (.cm arg0 t) = .truth false),
  decide (fold [t] (.cm arg1 t) = .cm arg1 t),
  decide (pruneActions [t] [.gen 7 arg0 t 1,.gen 9 arg1 u 2,.other 10 3,.gen 12 result t 4] =
      [.gen 7 arg0 t 1,.other 10 3,.gen 12 result t 4]),
  decide ((rewriteRule [t,u] [t] source).actions = [.gen 17 result t 5] ∧
    (rewriteRule [t,u] [t] source).positionMetadata = [result,arg1] ∧
    (rewriteRule [t,u] [t] source).itemId = 100),
  decide (assignedBy ⟨1,.truth false,[.gen 1 result t 0],[result]⟩ = []),
  decide ((normalize {} 4 cascade).map (fun r => (r.guard,r.actions)) =
      [(.truth false,[]),(.truth false,[]),(.truth false,[])] ∧
    step {} (normalize {} 4 cascade) = normalize {} 4 cascade),
  decide (normalize {} 1 cascade = cascade),
  decide (normalize {} 4 guardedSelf = guardedSelf),
  decide (normalize {protectAll := true} 20 cascade = cascade),
  decide ((step {externalAssigned := [t]} [sink]).map Rule.guard = [.cm arg0 t]),
  decide (fold [] (.other 21) = .other 21),
  decide (pruneActions [] [.gen 7 result t 1,.other 8 9] = [.other 8 9]),
  decide ((normalize {} 5 [source,sink]).map (fun r => (r.itemId,r.guard,r.actions,r.positionMetadata)) =
    [(100,.truth true,[.gen 17 result t 5],[result,arg1]),
     (200,.cm arg0 t,[],[arg0])])
]
end MarkControls

namespace TerminationControls
open ApSpec.SemgrepReducedMarks ApSpec.SemgrepReducedMarks.Controls
open ApSpec.SemgrepReducedMarksTermination ApSpec.SemgrepReducedMarksTermination.Controls
def checks : List Bool := [
  decide (step {} (normalizeTotal {} cascade) = normalizeTotal {} cascade),
  decide ((normalizeTotal {} cascade).map (fun r => (r.guard,r.actions)) =
      [(.truth false,[]),(.truth false,[]),(.truth false,[])]),
  decide (programWeight cascade = 8 ∧ changedRounds {} 9 cascade = 4 ∧ equalityChecks {} 9 cascade = 5),
  decide (equalityChecks {} 100 guardedSelf = 1),
  decide (normalizeTotal {} guardedSelf = guardedSelf),
  decide (normalizeTotal {protectAll := true} cascade = cascade),
  decide ((normalizeTotal {} [negSource,sinkU]).map (fun r => (r.itemId,r.guard,r.actions)) =
      [(300,.truth true,[.gen 41 result u 0]),(301,.cm arg0 u,[])]),
  decide ((normalizeTotal {} [source,sink]).map (fun r => (r.itemId,r.guard,r.actions,r.positionMetadata)) =
      [(100,.truth true,[.gen 17 result t 5],[result,arg1]),(200,.cm arg0 t,[],[arg0])]),
  decide (normalize {} 1 cascade = cascade ∧ normalizeTotal {} cascade ≠ cascade),
  decide (weight (fold [sameNameOtherOwner] (.cm anyAccessorArg0 t)) < weight (.cm anyAccessorArg0 t))
]
end TerminationControls

#eval do
  let selection := ApSpec.SemgrepProviderSelection.Controls.checks
  let binding := BindingControls.checks
  let reduction := MarkControls.checks
  let epoch := ApSpec.RuleSelectionEpoch.Controls.controls
  let coverage := ApSpec.RuleMarkCoverage.Controls.controls
  let replay := ApSpec.RuleProviderReplay.Controls.controls
  let termination := TerminationControls.checks
  let compaction := ApSpec.SemgrepRuleCompaction.Controls.checks
  let metadata := ApSpec.SemgrepMetadataProjection.Controls.checks
  let composition := ApSpec.RuleProviderComposition.Controls.checks
  let all := selection ++ binding ++ reduction ++ epoch ++ coverage ++ replay ++ termination ++ compaction ++ metadata ++ composition
  if !(all.all id) then throw (IO.userError "Provider/reduction control failed")
  IO.println s!"Provider/reduction executable controls: {all.length} passed (selection {selection.length}, binding {binding.length}, reduction {reduction.length}, epoch {epoch.length}, coverage {coverage.length}, replay {replay.length}, termination {termination.length}, compaction {compaction.length}, metadata {metadata.length}, composition {composition.length})."

#print axioms ApSpec.SemgrepProviderSelection.active_graph_metadata_complete
#print axioms ApSpec.SemgrepProviderSelection.active_flow_observation_preserved
#print axioms ApSpec.RuleSpecializationSelection.exact_ordered_variants
#print axioms ApSpec.RuleSpecializationSelection.guarded_sink_observation_equal
#print axioms ApSpec.RuleSpecializationSelection.fixed_cache_query_trace_equal
#print axioms ApSpec.SemgrepReducedMarks.fold_truth_equivalent
#print axioms ApSpec.SemgrepReducedMarks.normalize_protected_observations
#print axioms ApSpec.SemgrepReducedMarks.fixed_cm_has_producer
#print axioms ApSpec.SemgrepReducedMarks.fixed_gen_is_checked
#print axioms ApSpec.RuleSelectionEpoch.unsafe_query_handoff_commutes
#print axioms ApSpec.RuleMarkCoverage.witness_assigned
#print axioms ApSpec.RuleProviderReplay.interactive_replay_equal

#print axioms ApSpec.SemgrepReducedMarksTermination.normalizeTotal_fixed
#print axioms ApSpec.SemgrepRuleCompaction.normalizeTotal_fixed
#print axioms ApSpec.SemgrepRuleCompaction.compact_preserves_execution_effects
#print axioms ApSpec.SemgrepRuleCompaction.compileSnapshot_execution_independent_of_metadata
#print axioms ApSpec.SemgrepRuleCompaction.liveRound_eq_round
#print axioms ApSpec.SemgrepMetadataProjection.projected_reset_subset
#print axioms ApSpec.SemgrepMetadataProjection.old_taint_survivor_implies_new
#print axioms ApSpec.SemgrepMetadataProjection.positive_guard_monotone
#print axioms ApSpec.SemgrepMetadataProjection.actual_used_reset_membership
#print axioms ApSpec.SemgrepMetadataProjection.canonical_taint_survivor_equal
#print axioms ApSpec.SemgrepMetadataProjection.canonical_mx_equal

#print axioms ApSpec.RuleProviderComposition.go_admission_preserved
#print axioms ApSpec.RuleProviderComposition.jvm_admission_preserved
#print axioms ApSpec.RuleProviderComposition.query_envelope_preserves_original_presence
