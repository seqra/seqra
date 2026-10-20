import ApSpec.ExactClassMarkers
import Lean
open Lean Elab Command

run_elab do
  let env ← getEnv
  let mut total : Nat := 0
  for (name, _) in env.constants.toList do
    let owned := match env.getModuleIdxFor? name with
      | some i => env.header.moduleNames[i.toNat]! == `ApSpec.ExactClassMarkers ||
          env.header.moduleNames[i.toNat]! == `ApSpec.GroupedRun1Initials
      | none => false
    if owned then
      total := total + 1
      let bad := (← collectAxioms name).filter (fun n => n != `propext && n != `Quot.sound)
      if !bad.isEmpty then throwError "Unexpected axioms: {name}: {bad}"
  if total == 0 then throwError "Empty exact class marker audit"
  logInfo m!"Audited {total} current grammar and exact class marker declarations; no classical/choice/sorry axioms."

#eval do
  let oldControls := ApSpec.GroupedRun1Initials.Controls.controls.map Prod.snd
  let newControls := ApSpec.ExactClassMarkers.controls
  if !(oldControls ++ newControls).all id then throw (IO.userError "Exact class marker controls failed")
  IO.println s!"Current grammar and exact class marker controls passed: {oldControls.length + newControls.length}"
