/- Audit for source-backed run-1 groups and the full-mark handoff.
   The old FLOW-plus-interest pool model is outside this active scope. -/
import ApSpec.Run1StaticGroupDerivation
import ApSpec.Run1StaticGroupProtocol
import ApSpec.Run1StaticGroupRequestSlice
import ApSpec.Run1GroupStaticHandoff
import Lean

open Lean Elab Command

run_elab do
  let env ← getEnv
  let modules := #[`ApSpec.Run1StaticGroupDerivation,
    `ApSpec.Run1StaticGroupProtocol, `ApSpec.Run1StaticGroupRequestSlice,
    `ApSpec.Run1GroupStaticHandoff]
  let mut total : Nat := 0
  let mut unexpected : Nat := 0
  for mod in modules do
    let mut count : Nat := 0
    for (name, _) in env.constants.toList do
      let selected := match env.getModuleIdxFor? name with
        | some i => env.header.moduleNames[i.toNat]! == mod
        | none => false
      if selected then
        count := count + 1
        let axioms ← collectAxioms name
        let bad := axioms.filter (fun n => n != `propext && n != `Quot.sound)
        if !bad.isEmpty then
          unexpected := unexpected + 1
          logInfo m!"Unexpected axioms in {name}: {bad}"
    if count == 0 then throwError "No declarations audited for {mod}"
    total := total + count
    logInfo m!"{mod}: audited {count} declarations."
  logInfo m!"Static group communication: audited {total} declarations; {unexpected} unexpected axioms."
  if unexpected > 0 then throwError "Constructive axiom audit failed"

#eval do
  let controls := ApSpec.Run1StaticGroupDerivation.Controls.controls ++
    ApSpec.Run1StaticGroupProtocol.Controls.controls ++
    ApSpec.Run1StaticGroupRequestSlice.Controls.controls ++
    ApSpec.Run1GroupStaticHandoff.Controls.controls
  for (name, passed) in controls do
    if !passed then throw (IO.userError ("Control failed: " ++ name))
  IO.println s!"Static group communication controls PASS: {controls.length}"

#print axioms ApSpec.Run1StaticGroupDerivation.solution_exact
#print axioms ApSpec.Run1StaticGroupDerivation.solutionRoot
#print axioms ApSpec.Run1StaticGroupDerivation.compact_expanded_exact
#print axioms ApSpec.Run1StaticGroupProtocol.closeCertificate
#print axioms ApSpec.Run1StaticGroupProtocol.close_complete
#print axioms ApSpec.Run1StaticGroupProtocol.late_registration_same_answers
#print axioms ApSpec.Run1StaticGroupRequestSlice.requested_solution_exact
#print axioms ApSpec.Run1StaticGroupRequestSlice.restrictTree
#print axioms ApSpec.Run1GroupStaticHandoff.compileGraphPath
