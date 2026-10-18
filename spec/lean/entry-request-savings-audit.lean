/- Focused audit of restricted endpoint/count models against an ordinary
   incremental request algorithm. No full replay comparator; no whole-analyzer
   elapsed time, retained-byte or actual CFG/AP refinement theorem. Savings
   require uncovered dynamic original-slot needs, not checks already suppressed
   by historical frozen/transitive request plans. -/
import ApSpec.EntryRequestSavings
import Lean
open Lean Elab Command

run_elab do
  let env ← getEnv
  let moduleName := `ApSpec.EntryRequestSavings
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
    throwError "No entry request savings declarations audited."
  logInfo m!"Audited {total} selected declarations; {failures} have unexpected axioms."
  if failures > 0 then
    throwError "Constructive entry request savings audit failed."

#eval do
  if !(ApSpec.EntryRequestSavings.Controls.checks.all id) then
    throw (IO.userError "Entry request savings controls failed.")
  IO.println ("Executable entry request savings controls passed: " ++
    toString ApSpec.EntryRequestSavings.Controls.checks.length)

#print axioms ApSpec.EntryRequestSavings.chain_endpoint_unions_equal
#print axioms ApSpec.EntryRequestSavings.chain_has_supplied_witness
#print axioms ApSpec.EntryRequestSavings.strict_chain_mark_saving
#print axioms ApSpec.EntryRequestSavings.strict_chain_membership_saving
#print axioms ApSpec.EntryRequestSavings.strict_chain_envelope_saving
#print axioms ApSpec.EntryRequestSavings.SharedBottleneck.endpoint_unions_equal
#print axioms ApSpec.EntryRequestSavings.SharedBottleneck.ordinary_endpoint_from_execution
#print axioms ApSpec.EntryRequestSavings.SharedBottleneck.admitted_mark_never_retransmits
#print axioms ApSpec.EntryRequestSavings.SharedBottleneck.endpoint_witness
#print axioms ApSpec.EntryRequestSavings.SharedBottleneck.direct_can_do_more_work
#print axioms ApSpec.EntryRequestSavings.Controls.checks_pass
