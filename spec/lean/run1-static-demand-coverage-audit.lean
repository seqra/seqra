/- Group-demand sufficiency at the F1/B2 handoff. All emitted guides are
   frozen GROUP permissions; FLOW alone cannot authorize a full mark. -/
import ApSpec.Run1StaticGroupDerivation
import ApSpec.Run1StaticInitialRefinement
import ApSpec.Run1StaticReadAdmission
import ApSpec.Run1GroupStaticHandoff
import ApSpec.Run1StaticDemandGateGap
import ApSpec.Run1StaticDemandCoverage
import ApSpec.Run1StaticDemandTreeCoverage
import ApSpec.Run1StaticReadWritePath
import ApSpec.Run1StaticEarlySummaryZero
import Lean

open Lean Elab Command

run_elab do
  let env ← getEnv
  let modules := #[`ApSpec.Run1StaticGroupDerivation,
    `ApSpec.Run1StaticInitialRefinement, `ApSpec.Run1StaticReadAdmission,
    `ApSpec.Run1GroupStaticHandoff, `ApSpec.Run1StaticDemandGateGap,
    `ApSpec.Run1StaticDemandCoverage, `ApSpec.Run1StaticDemandTreeCoverage,
    `ApSpec.Run1StaticReadWritePath, `ApSpec.Run1StaticEarlySummaryZero]
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
  logInfo m!"Static demand coverage: audited {total} declarations; {unexpected} unexpected axioms."
  if unexpected > 0 then throwError "Constructive axiom audit failed"

#eval do
  let controls := ApSpec.Run1StaticGroupDerivation.Controls.controls ++
    ApSpec.Run1StaticInitialRefinement.Controls.controls ++
    ApSpec.Run1StaticReadAdmission.Controls.controls ++
    ApSpec.Run1GroupStaticHandoff.Controls.controls ++
    ApSpec.Run1StaticDemandGateGap.Counterexample.controls ++
    ApSpec.Run1StaticDemandCoverage.Controls.controls ++
    ApSpec.Run1StaticDemandTreeCoverage.Controls.controls ++
    ApSpec.Run1StaticReadWritePath.Controls.controls ++
    ApSpec.Run1StaticEarlySummaryZero.Controls.controls
  for (name, passed) in controls do
    if !passed then throw (IO.userError ("Control failed: " ++ name))
  IO.println s!"Static demand coverage controls PASS: {controls.length}"

#print axioms ApSpec.Run1StaticDemandGateGap.flow_only_no_emission
#print axioms ApSpec.Run1StaticDemandGateGap.grouped_guide_accepts_every_owner
#print axioms ApSpec.Run1StaticDemandCoverage.compileTrace
#print axioms ApSpec.Run1StaticDemandCoverage.every_visited_mask
#print axioms ApSpec.Run1StaticDemandCoverage.requiredGuide
#print axioms ApSpec.Run1StaticDemandCoverage.eager_and_frozen_guides_equal
#print axioms ApSpec.Run1StaticDemandCoverage.explicit_group_emits_full
#print axioms ApSpec.Run1StaticDemandTreeCoverage.projectTree
#print axioms ApSpec.Run1StaticDemandTreeCoverage.buildFullSkeleton
#print axioms ApSpec.Run1StaticDemandTreeCoverage.collected_guides_frozen
#print axioms ApSpec.Run1StaticDemandTreeCoverage.fullTreePointWitness
#print axioms ApSpec.Run1StaticDemandTreeCoverage.fullLocalReversePublication
#print axioms ApSpec.Run1StaticReadWritePath.builder_defaults_closed
#print axioms ApSpec.Run1StaticReadWritePath.compileCorridor
#print axioms ApSpec.Run1StaticReadWritePath.compile_uses_real_moves
#print axioms ApSpec.Run1StaticReadWritePath.writerEndpointCoverage
#print axioms ApSpec.Run1StaticReadWritePath.coverReadToWriter
#print axioms ApSpec.Run1StaticReadWritePath.static_identity_rejects_ordinary_output
#print axioms ApSpec.Run1StaticReadWritePath.zero_hit_keeps_original
#print axioms ApSpec.Run1StaticEarlySummaryZero.nonzero_reach_no_zero_protocol
#print axioms ApSpec.Run1StaticEarlySummaryZero.liftChain
#print axioms ApSpec.Run1StaticEarlySummaryZero.runChain_result
#print axioms ApSpec.Run1StaticEarlySummaryZero.runChain_matches_certificate
#print axioms ApSpec.Run1StaticEarlySummaryZero.writer_has_explicit_zero
#print axioms ApSpec.Run1StaticEarlySummaryZero.reversed_source_nonzero_no_zero_protocol
#print axioms ApSpec.Run1GroupStaticHandoff.compileGraphPath
