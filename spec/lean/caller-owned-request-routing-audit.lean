/- Focused audit of ordinary finite request-path coverage and caller-view
   union/delta composition. Actual input/coverage construction, full AP/CFG
   validity, scheduler progress and summary soundness remain external. -/
import ApSpec.CallerOwnedRequestRouting
import Lean
open Lean Elab Command

run_elab do
  let env ← getEnv
  let moduleName := `ApSpec.CallerOwnedRequestRouting
  let mut total : Nat := 0
  let mut failures : Nat := 0
  for (name, _) in env.constants.toList do
    let owned := match env.getModuleIdxFor? name with
      | some i => env.header.moduleNames[i.toNat]! == moduleName
      | none => false
    if owned then
      total := total + 1
      let deps ← collectAxioms name
      let bad := deps.filter (fun n => n != `propext && n != `Quot.sound)
      if !bad.isEmpty then
        failures := failures + 1
        logInfo m!"Unexpected axioms: {name}: {bad}"
  if total == 0 then
    throwError "No caller-owned routing declarations audited."
  logInfo m!"Audited {total} selected declarations; {failures} have unexpected axioms."
  if failures > 0 then
    throwError "Constructive caller-owned routing audit failed."

#print axioms ApSpec.CallerOwnedRequestRouting.compress
#print axioms ApSpec.CallerOwnedRequestRouting.every_base_endpoint_covered
#print axioms ApSpec.CallerOwnedRequestRouting.compression_preserves_exact_productive_bindings
#print axioms ApSpec.CallerOwnedRequestRouting.no_base_endpoint_from_coverage_alone
#print axioms ApSpec.CallerOwnedRequestRouting.static_base_entry_ancestry
#print axioms ApSpec.CallerOwnedRequestRouting.canonical_descriptors_pass_unchanged
#print axioms ApSpec.CallerOwnedRequestRouting.caller_view_both_arrival_orders
#print axioms ApSpec.CallerOwnedRequestRouting.caller_view_no_pair_twice
#print axioms ApSpec.CallerOwnedRequestRouting.Pipeline.source_view_callsite_input_coverage
#print axioms ApSpec.CallerOwnedRequestRouting.Pipeline.certified_pipeline_hop

#eval do
  if !(ApSpec.CallerOwnedRequestRouting.Controls.checks.all id) then
    throw (IO.userError "Caller-owned request routing controls failed.")
  IO.println ("Executable caller-owned routing controls passed: " ++
    toString ApSpec.CallerOwnedRequestRouting.Controls.checks.length)
