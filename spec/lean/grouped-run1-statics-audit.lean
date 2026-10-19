/- Unified constructive evidence audit. Some imports are historical/ghost
   comparison models. Only the scopes in grouped-run1-statics-proofs.md are
   selected; this audit does not certify the JVM implementation. -/
import ApSpec.RuleGroupMarks
import ApSpec.RuleGroupRequestRepair
import ApSpec.RuleGroupSchedule
import ApSpec.RuleGroupConditions
import ApSpec.Run1StaticGroupDerivation
import ApSpec.Run1StaticInitialRefinement
import ApSpec.Run1StaticReadAdmission
import ApSpec.Run1GroupStaticHandoff
import ApSpec.Run1StaticDemandGateGap
import ApSpec.Run1StaticDemandCoverage
import ApSpec.Run1StaticDemandTreeCoverage
import ApSpec.Run1StaticReadWritePath
import ApSpec.Run1StaticEarlySummaryZero
import ApSpec.Run1StaticEarlySummaryGap
import ApSpec.Run1UniversalStaticCarry
import ApSpec.Run1InitialShapes
import ApSpec.Run1StrongPreservation
import ApSpec.Run1DefaultSummaryApplication
import ApSpec.GroupedRun1Initials
import ApSpec.GroupedRun1StaticComposition
import Lean

open Lean Elab Command

run_elab do
  let env ← getEnv
  let modules := #[`ApSpec.RuleGroupMarks,
    `ApSpec.RuleGroupRequestRepair,
    `ApSpec.RuleGroupSchedule,
    `ApSpec.RuleGroupConditions,
    `ApSpec.Run1StaticGroupDerivation,
    `ApSpec.Run1StaticInitialRefinement,
    `ApSpec.Run1StaticReadAdmission,
    `ApSpec.Run1GroupStaticHandoff,
    `ApSpec.Run1StaticDemandGateGap,
    `ApSpec.Run1StaticDemandCoverage,
    `ApSpec.Run1StaticDemandTreeCoverage,
    `ApSpec.Run1StaticReadWritePath,
    `ApSpec.Run1StaticEarlySummaryZero,
    `ApSpec.Run1StaticEarlySummaryGap,
    `ApSpec.Run1UniversalStaticCarry,
    `ApSpec.Run1InitialShapes,
    `ApSpec.Run1StrongPreservation,
    `ApSpec.Run1DefaultSummaryApplication,
    `ApSpec.GroupedRun1Initials,
    `ApSpec.GroupedRun1StaticComposition]
  let mut total : Nat := 0
  let mut unexpected : Nat := 0
  for mod in modules do
    let mut count : Nat := 0
    for (name, _) in env.constants.toList do
      let selected := match env.getModuleIdxFor? name with
        | some i => env.header.moduleNames[i.toNat]! == mod
        | none => false
      if selected then
        count := count + 1
        let axioms ← collectAxioms name
        let bad := axioms.filter (fun n => n != `propext && n != `Quot.sound)
        if !bad.isEmpty then
          unexpected := unexpected + 1
          logInfo m!"Unexpected axioms in {name}: {bad}"
    if count == 0 then throwError "No declarations audited for {mod}"
    total := total + count
    logInfo m!"{mod}: audited {count} declarations."
  logInfo m!"Grouped run-1 statics: audited {total} declarations; {unexpected} unexpected axioms."
  if unexpected > 0 then throwError "Constructive axiom audit failed"

#eval do
  let controls := ApSpec.RuleGroupMarks.Controls.controls ++
    ApSpec.RuleGroupRequestRepair.Controls.controls ++
    ApSpec.RuleGroupSchedule.Controls.controls ++
    ApSpec.RuleGroupConditions.Controls.controls ++
    ApSpec.Run1StaticGroupDerivation.Controls.controls ++
    ApSpec.Run1StaticInitialRefinement.Controls.controls ++
    ApSpec.Run1StaticReadAdmission.Controls.controls ++
    ApSpec.Run1GroupStaticHandoff.Controls.controls ++
    ApSpec.Run1StaticDemandGateGap.Counterexample.controls ++
    ApSpec.Run1StaticDemandCoverage.Controls.controls ++
    ApSpec.Run1StaticDemandTreeCoverage.Controls.controls ++
    ApSpec.Run1StaticReadWritePath.Controls.controls ++
    ApSpec.Run1StaticEarlySummaryZero.Controls.controls ++
    ApSpec.Run1StaticEarlySummaryGap.Controls.controls ++
    ApSpec.Run1UniversalStaticCarry.Controls.controls ++
    [("baseline initial grammar composite", ApSpec.Run1InitialShapes.Certificate.checks),
    ("baseline selective field writes composite", ApSpec.Run1StrongPreservation.Controls.checks),
    ("baseline layer-only application composite", ApSpec.Run1DefaultSummaryApplication.SelectedControls.checks)] ++
    ApSpec.GroupedRun1Initials.Controls.controls ++
    ApSpec.GroupedRun1StaticComposition.Controls.controls
  for (name, passed) in controls do
    if !passed then throw (IO.userError ("Control failed: " ++ name))
  IO.println s!"Grouped run-1 statics controls PASS: {controls.length}"

#print axioms ApSpec.GroupedRun1StaticComposition.completeFiniteWitness
#print axioms ApSpec.GroupedRun1StaticComposition.compileCoverage
#print axioms ApSpec.GroupedRun1StaticComposition.rooted_write_admits_read
#print axioms ApSpec.GroupedRun1StaticComposition.compileStaticCoverage
#print axioms ApSpec.GroupedRun1StaticComposition.collect_visits_each_branch
#print axioms ApSpec.GroupedRun1StaticComposition.collected_sources_confirm
#print axioms ApSpec.GroupedRun1StaticComposition.matching_source_is_enumerated
#print axioms ApSpec.GroupedRun1StaticComposition.source_leaf_keeps_every_occurrence
#print axioms ApSpec.GroupedRun1StaticComposition.Controls.mixed_example
#print axioms ApSpec.Run1StaticGroupDerivation.compact_expanded_exact
#print axioms ApSpec.Run1StaticGroupDerivation.strict_chain_saving
#print axioms ApSpec.RuleGroupMarks.exact_clean_partition
#print axioms ApSpec.RuleGroupConditions.exact_truth_implies_possible_truth
#print axioms ApSpec.Run1StrongPreservation.strong_write_survival_coverage
