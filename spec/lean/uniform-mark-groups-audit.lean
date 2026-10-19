/- Focused constructive audit of uniform groups, interner and selected guidance. -/
import ApSpec.UniformMarkGroups
import Lean
open Lean Elab Command

run_elab do
  let env ← getEnv
  let mut total : Nat := 0
  let mut failures : Nat := 0
  for (name, _) in env.constants.toList do
    let owned := match env.getModuleIdxFor? name with
      | some i => env.header.moduleNames[i.toNat]! == `ApSpec.UniformMarkGroups
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
    throwError "Constructive uniform groups audit failed."

#eval do
  let controls := ApSpec.UniformMarkGroups.Controls.controls
  if !(controls.all Prod.snd) then
    throw (IO.userError "Uniform groups controls failed.")
  IO.println s!"Executable uniform groups controls passed: {controls.length}"

#print axioms ApSpec.UniformMarkGroups.validated_property
#print axioms ApSpec.UniformMarkGroups.uniform_group
#print axioms ApSpec.UniformMarkGroups.nonempty_scalar_eq_group
#print axioms ApSpec.UniformMarkGroups.same_witness_and_scalar
#print axioms ApSpec.UniformMarkGroups.Serialized.binding_names_scanned
#print axioms ApSpec.UniformMarkGroups.Serialized.bound_property_from_validated_declarations
#print axioms ApSpec.UniformMarkGroups.Serialized.setup_scan_and_no_binding_queries
#print axioms ApSpec.UniformMarkGroups.Interner.existing_slot_stable
#print axioms ApSpec.UniformMarkGroups.Interner.intern_idempotent
#print axioms ApSpec.UniformMarkGroups.Interner.projected_key_exact
#print axioms ApSpec.UniformMarkGroups.Guidance.emission_equivalence
#print axioms ApSpec.UniformMarkGroups.Guidance.restriction_equivalence
#print axioms ApSpec.UniformMarkGroups.Guidance.premise_member_union
#print axioms ApSpec.UniformMarkGroups.Guidance.selected_candidates_strict

#print axioms ApSpec.UniformMarkGroups.Guidance.unchanged_bypass_equivalence
