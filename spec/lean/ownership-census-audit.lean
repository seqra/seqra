/- Focused constructive audit of the additive census model and generated helpers. -/
import ApSpec.OwnershipCensus
import Lean
open Lean Elab Command

run_elab do
  let env ← getEnv
  let mut total : Nat := 0
  let mut failures : Nat := 0
  for (name, _) in env.constants.toList do
    let owned := match env.getModuleIdxFor? name with
      | some i => env.header.moduleNames[i.toNat]! == `ApSpec.OwnershipCensus
      | none => false
    if owned then
      total := total + 1
      let deps ← collectAxioms name
      let bad := deps.filter (fun n => n != `propext && n != `Quot.sound)
      if !bad.isEmpty then
        failures := failures + 1
        logInfo m!"Unexpected axioms: {name}: {bad}"
  logInfo m!"Audited {total} owned declarations; {failures} unexpected-axiom declarations."
  if total = 0 ∨ failures > 0 then
    throwError "Constructive ownership census audit failed."

#eval do
  let controls := ApSpec.OwnershipCensus.Controls.controls
  if !(controls.all Prod.snd) then
    throw (IO.userError "Ownership census controls failed.")
  IO.println s!"Executable ownership census controls passed: {controls.length}"

#print axioms ApSpec.OwnershipCensus.stable_rebuild_identities
#print axioms ApSpec.OwnershipCensus.acquisition_exact
#print axioms ApSpec.OwnershipCensus.preexisting_bound_preserved
#print axioms ApSpec.OwnershipCensus.acquisition_same_catalog_or_error
#print axioms ApSpec.OwnershipCensus.commit_preserves_memo_valid
#print axioms ApSpec.OwnershipCensus.visit_exact
#print axioms ApSpec.OwnershipCensus.census_lookup_no_bound_insertion
#print axioms ApSpec.OwnershipCensus.memo_slots_bound
#print axioms ApSpec.OwnershipCensus.same_catalog_same_all_known

#print axioms ApSpec.OwnershipCensus.commit_visit_preserves_memo_valid
#print axioms ApSpec.OwnershipCensus.visit_same_all_known_catalog_and_primitive

#print axioms ApSpec.OwnershipCensus.query_list_exact
#print axioms ApSpec.OwnershipCensus.census_equals_cached_reference

#print axioms ApSpec.OwnershipCensus.successful_census_run_preserves_cache
#print axioms ApSpec.OwnershipCensus.query_list_same_all_known

#print axioms ApSpec.OwnershipCensus.exclusive_payload_retention_difference
#print axioms ApSpec.OwnershipCensus.fresh_bound_write_cost
