/- Focused audit of this additive metadata model, including generated helpers. -/
import ApSpec.GroupPrimitiveMetadata
import Lean
open Lean Elab Command

run_elab do
  let env ← getEnv
  let mut total : Nat := 0
  let mut failures : Nat := 0
  for (name, _) in env.constants.toList do
    let owned := match env.getModuleIdxFor? name with
      | some i => env.header.moduleNames[i.toNat]! == `ApSpec.GroupPrimitiveMetadata
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
    throwError "Constructive group metadata audit failed."

#eval do
  let controls := ApSpec.GroupPrimitiveMetadata.Controls.controls ++
    ApSpec.GroupPrimitiveMetadata.AllKnown.Controls.controls
  if !(controls.all Prod.snd) then
    throw (IO.userError "Group metadata controls failed.")
  IO.println s!"Executable group metadata controls passed: {controls.length}"

#print axioms ApSpec.GroupPrimitiveMetadata.old_keep_eq_metadata
#print axioms ApSpec.GroupPrimitiveMetadata.primitive_conjunction_closes
#print axioms ApSpec.GroupPrimitiveMetadata.late_contribution_update
#print axioms ApSpec.GroupPrimitiveMetadata.fixed_members_strict

#print axioms ApSpec.GroupPrimitiveMetadata.AllKnown.one_bit_nonempty
#print axioms ApSpec.GroupPrimitiveMetadata.AllKnown.creation_bit_update
#print axioms ApSpec.GroupPrimitiveMetadata.AllKnown.eligibility_change_can_reject

#print axioms ApSpec.GroupPrimitiveMetadata.AllKnown.member_keep_coverage
#print axioms ApSpec.GroupPrimitiveMetadata.AllKnown.owned_entry_coverage
