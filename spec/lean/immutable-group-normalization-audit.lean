import ApSpec.ImmutableGroupNormalization
import Lean
open Lean Elab Command

run_elab do
  let env ← getEnv
  let mut total : Nat := 0
  for (name, _) in env.constants.toList do
    let owned := match env.getModuleIdxFor? name with
      | some i => env.header.moduleNames[i.toNat]! == `ApSpec.ImmutableGroupNormalization
      | none => false
    if owned then
      total := total + 1
      let deps ← collectAxioms name
      let bad := deps.filter (fun n => n != `propext && n != `Quot.sound)
      if !bad.isEmpty then throwError "Unexpected axioms: {name}: {bad}"
  if total == 0 then throwError "Empty normalization audit"
  logInfo m!"Audited {total} declarations; no classical, choice, or sorry axioms."

#eval do
  let controls := ApSpec.ImmutableGroupNormalization.controls
  if !controls.all id then throw (IO.userError "Normalization controls failed")
  IO.println s!"Normalization controls passed: {controls.length}"
