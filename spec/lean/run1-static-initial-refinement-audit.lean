/- Audit for forward static initials and direct current-point refinement.
   Reverse provider needs and request-slice closure are outside this scope. -/
import ApSpec.Run1StaticGroupDerivation
import ApSpec.Run1StaticInitialRefinement
import ApSpec.Run1GroupStaticHandoff
import Lean

open Lean Elab Command

run_elab do
  let env ← getEnv
  let modules := #[`ApSpec.Run1StaticGroupDerivation,
    `ApSpec.Run1StaticInitialRefinement, `ApSpec.Run1GroupStaticHandoff]
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
  logInfo m!"Static initial refinement: audited {total} declarations; {unexpected} unexpected axioms."
  if unexpected > 0 then throwError "Constructive axiom audit failed"

#eval do
  let controls := ApSpec.Run1StaticGroupDerivation.Controls.controls ++
    ApSpec.Run1StaticInitialRefinement.Controls.controls ++
    ApSpec.Run1GroupStaticHandoff.Controls.controls
  for (name, passed) in controls do
    if !passed then throw (IO.userError ("Control failed: " ++ name))
  IO.println s!"Static initial refinement controls PASS: {controls.length}"

#print axioms ApSpec.Run1StaticGroupDerivation.solution_exact
#print axioms ApSpec.Run1StaticGroupDerivation.solutionRoot
#print axioms ApSpec.Run1StaticGroupDerivation.compact_expanded_exact
#print axioms ApSpec.Run1StaticInitialRefinement.identity_refinement_commutes
#print axioms ApSpec.Run1StaticInitialRefinement.zero_keeps_static_premise
#print axioms ApSpec.Run1StaticInitialRefinement.materializationCertificate
#print axioms ApSpec.Run1StaticInitialRefinement.materializationRoot
#print axioms ApSpec.Run1StaticInitialRefinement.refinement_complete
#print axioms ApSpec.Run1StaticInitialRefinement.merged_arrival_commutes
#print axioms ApSpec.Run1StaticInitialRefinement.fresh_after_commit_empty
#print axioms ApSpec.Run1StaticInitialRefinement.request_independent_of_forward_depth
#print axioms ApSpec.Run1GroupStaticHandoff.compileGraphPath
