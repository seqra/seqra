import ApSpec.Run1StaticFieldAblation
import Lean
open Lean Elab Command

run_elab do
  let env ← getEnv
  let mut total : Nat := 0
  for (name, _) in env.constants.toList do
    let owned := match env.getModuleIdxFor? name with
      | some i => env.header.moduleNames[i.toNat]! == `ApSpec.Run1StaticFieldAblation
      | none => false
    if owned then
      total := total + 1
      let deps ← collectAxioms name
      let bad := deps.filter (fun n => n != `propext && n != `Quot.sound)
      if !bad.isEmpty then throwError "Unexpected axioms: {name}: {bad}"
  if total = 0 then throwError "No diagnostic static-field ablation declarations audited"
  logInfo m!"Audited {total} diagnostic static-field ablation declarations; no unexpected axioms."

#eval do
  let controls := ApSpec.Run1StaticFieldAblation.Controls.controls
  if !(controls.all Prod.snd) then throw (IO.userError "Static-field ablation controls failed")
  IO.println s!"Executable static-field ablation controls passed: {controls.length}"

#print axioms ApSpec.Run1StaticFieldAblation.off_identity
#print axioms ApSpec.Run1StaticFieldAblation.later_identity
#print axioms ApSpec.Run1StaticFieldAblation.every_selected_effect_nonfield
#print axioms ApSpec.Run1StaticFieldAblation.exact_class_carrier_unchanged
#print axioms ApSpec.Run1StaticFieldAblation.Controls.intentionally_not_equivalent
