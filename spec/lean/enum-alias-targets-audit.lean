import ApSpec.EnumAliasTargets
import Lean
open Lean Elab Command

run_elab do
  let env ← getEnv
  let mut total : Nat := 0
  for (name, _) in env.constants.toList do
    let owned := match env.getModuleIdxFor? name with
      | some i => env.header.moduleNames[i.toNat]! == `ApSpec.EnumAliasTargets
      | none => false
    if owned then
      total := total + 1
      let deps ← collectAxioms name
      let bad := deps.filter (fun n => n != `propext && n != `Quot.sound)
      if !bad.isEmpty then throwError "Unexpected axioms: {name}: {bad}"
  if total = 0 then throwError "No enum alias declarations audited"
  logInfo m!"Audited {total} enum alias declarations; no unexpected axioms."

#eval do
  let controls := ApSpec.EnumAliasTargets.Controls.controls
  if !(controls.all Prod.snd) then throw (IO.userError "Enum alias controls failed")
  IO.println s!"Executable enum alias controls passed: {controls.length}"

#print axioms ApSpec.EnumAliasTargets.selected_exact
#print axioms ApSpec.EnumAliasTargets.size_does_not_increase
#print axioms ApSpec.EnumAliasTargets.Controls.mutable_enum_flow_removed
