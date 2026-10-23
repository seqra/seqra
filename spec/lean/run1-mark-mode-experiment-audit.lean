import ApSpec.Run1MarkModeExperiment
import Lean

open Lean Elab Command

run_elab do
  let env ← getEnv
  let mut total : Nat := 0
  for (name, _) in env.constants.toList do
    let module := match env.getModuleIdxFor? name with
      | some index => env.header.moduleNames[index.toNat]!
      | none => Name.anonymous
    if module == `ApSpec.Run1MarkModeExperiment then
      total := total + 1
      let dependencies ← collectAxioms name
      let forbidden := dependencies.filter (fun n => n != `propext && n != `Quot.sound)
      if !forbidden.isEmpty then
        throwError "Unexpected axioms: {name}: {forbidden}"
  if total == 0 then throwError "No experiment declarations audited"
  logInfo m!"Audited {total} experiment declarations; no unexpected axioms."

#eval do
  let controls : List Bool := ApSpec.Run1MarkModeExperiment.Controls.controls
  if !(controls.all id) then throw (IO.userError "Mode experiment controls failed")
  IO.println s!"Executable mode controls passed: {controls.length}."

#print axioms ApSpec.Run1MarkModeExperiment.later_runs_full
#print axioms ApSpec.Run1MarkModeExperiment.default_is_full
#print axioms ApSpec.Run1MarkModeExperiment.default_matches_explicit_full
#print axioms ApSpec.Run1MarkModeExperiment.explicit_group_is_group
#print axioms ApSpec.Run1MarkModeExperiment.full_keys_injective
#print axioms ApSpec.Run1MarkModeExperiment.group_keys_merge_exactly_owners
#print axioms ApSpec.Run1MarkModeExperiment.runtime_atom_idempotent
#print axioms ApSpec.Run1MarkModeExperiment.same_structural_policy
#print axioms ApSpec.Run1MarkModeExperiment.full_cleaner_kills_only_selected
#print axioms ApSpec.Run1MarkModeExperiment.Query.fullGuideQueryWitness
