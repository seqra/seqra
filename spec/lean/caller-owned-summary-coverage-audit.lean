/- Focused audit of the caller-owned default-summary inclusion model.
   Runtime AP/JVM/kernel/scheduler conformance remains external. -/
import ApSpec.CallerOwnedSummaryCoverage
import Lean
open Lean Elab Command
run_elab do
  let env ← getEnv
  let mut audited : Nat := 0
  let mut failed : Nat := 0
  for (name, _) in env.constants.toList do
    let selected := match env.getModuleIdxFor? name with
      | some i => env.header.moduleNames[i.toNat]! == `ApSpec.CallerOwnedSummaryCoverage
      | none => false
    if selected then
      audited := audited + 1
      let deps ← collectAxioms name
      let unexpected := deps.filter (fun n => n != `propext && n != `Quot.sound)
      if !unexpected.isEmpty then
        failed := failed + 1
        logInfo m!"Unexpected axioms: {name}: {unexpected}"
  if audited == 0 then throwError "No summary coverage declarations audited"
  logInfo m!"Summary coverage: audited {audited} declarations; {failed} unexpected axioms."
  if failed > 0 then throwError "Constructive axiom audit failed"
#eval do
  let controls := ApSpec.CallerOwnedSummaryCoverage.Witness.controls ++
    ApSpec.CallerOwnedSummaryCoverage.Witness.routingControls
  for (name,passed) in controls do
    if !passed then throw (IO.userError ("Control failed: " ++ name))
  IO.println s!"Summary coverage controls PASS: {controls.length}"

#print axioms ApSpec.CallerOwnedSummaryCoverage.promote
#print axioms ApSpec.CallerOwnedSummaryCoverage.default_summary_coverage
#print axioms ApSpec.CallerOwnedSummaryCoverage.every_default_observation_covered
#print axioms ApSpec.CallerOwnedSummaryCoverage.same_compaction_visible_coverage
#print axioms ApSpec.CallerOwnedSummaryCoverage.batch_decode_encode
#print axioms ApSpec.CallerOwnedSummaryCoverage.complete_bundle_enumerated
#print axioms ApSpec.CallerOwnedSummaryCoverage.summary_answers_exact
