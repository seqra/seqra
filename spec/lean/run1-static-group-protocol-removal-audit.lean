import ApSpec.Run1StaticPremiseAvailability
import ApSpec.Run1StaticGroupProtocolRemoval
import Lean

open Lean Elab Command

run_elab do
  let env ← getEnv
  let mut availabilityTotal : Nat := 0
  let mut protocolTotal : Nat := 0
  for (name, _) in env.constants.toList do
    let module := match env.getModuleIdxFor? name with
      | some i => env.header.moduleNames[i.toNat]!
      | none => Name.anonymous
    if module == `ApSpec.Run1StaticPremiseAvailability ||
        module == `ApSpec.Run1StaticGroupProtocolRemoval then
      if module == `ApSpec.Run1StaticPremiseAvailability then
        availabilityTotal := availabilityTotal + 1
      else
        protocolTotal := protocolTotal + 1
      let dependencies ← collectAxioms name
      let forbidden := dependencies.filter (fun n => n != `propext && n != `Quot.sound)
      if !forbidden.isEmpty then
        throwError "Unexpected axioms: {name}: {forbidden}"
  if availabilityTotal == 0 || protocolTotal == 0 then
    throwError "Both selected modules must have audited owned declarations"
  logInfo m!"Audited {availabilityTotal} availability and {protocolTotal} protocol-removal declarations; no unexpected axioms."

#eval do
  let availability := ApSpec.Run1StaticPremiseAvailability.controls
  let protocol := ApSpec.Run1StaticGroupProtocolRemoval.Controls.controls
  if !(availability.all id) then throw (IO.userError "Availability controls failed")
  if !(protocol.all id) then throw (IO.userError "Protocol controls failed")
  IO.println s!"Executable controls passed: {availability.length} availability, {protocol.length} protocol removal."

#print axioms ApSpec.Run1StaticPremiseAvailability.origin_available_no_later
#print axioms ApSpec.Run1StaticPremiseAvailability.published_static_premise_is_processed
#print axioms ApSpec.Run1StaticPremiseAvailability.unseeded_static_summary_cannot_publish
#print axioms ApSpec.Run1StaticPremiseAvailability.processed_request_wakes_no_writer
#print axioms ApSpec.Run1StaticGroupProtocolRemoval.same_canonical_input
#print axioms ApSpec.Run1StaticGroupProtocolRemoval.call_boundary_effect_simulation
#print axioms ApSpec.Run1StaticGroupProtocolRemoval.finite_handoff_method_entry_exit_group_seed_equality
#print axioms ApSpec.Run1StaticGroupProtocolRemoval.repeated_equal_effects_do_not_change_handoff_membership
#print axioms ApSpec.Run1StaticGroupProtocolRemoval.duplicate_boundary_in_context_preserves_handoff_membership
#print axioms ApSpec.Run1StaticGroupProtocolRemoval.preserveRun2Guidance
#print axioms ApSpec.Run1StaticGroupProtocolRemoval.computed_original_raw_and_demand_kept
#print axioms ApSpec.Run1StaticGroupProtocolRemoval.empty_exclusions_cannot_replace_existing
#print axioms ApSpec.Run1StaticGroupProtocolRemoval.admitted_selector_persists_in_merge
#print axioms ApSpec.Run1StaticGroupProtocolRemoval.exact_leaf_survives_finite_later_admissions
#print axioms ApSpec.Run1StaticGroupProtocolRemoval.complete_positive_supply_closure_equal
#print axioms ApSpec.Run1StaticGroupProtocolRemoval.complete_semantic_effect_closure_equal
#print axioms ApSpec.Run1StaticGroupProtocolRemoval.protocol_work_zero
#print axioms ApSpec.Run1StaticGroupProtocolRemoval.aligned_reference_jobs_same_scalar_work
#print axioms ApSpec.Run1StaticGroupProtocolRemoval.Controls.controls_pass
