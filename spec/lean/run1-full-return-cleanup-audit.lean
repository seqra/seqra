import ApSpec.Run1FullReturnCleanup
import Lean

open Lean Elab Command

run_elab do
  let env ← getEnv
  let mut count : Nat := 0
  for (name, _) in env.constants.toList do
    let owner := match env.getModuleIdxFor? name with
      | some index => env.header.moduleNames[index.toNat]!
      | none => Name.anonymous
    if owner == `ApSpec.Run1FullReturnCleanup then
      count := count + 1
      let axioms ← collectAxioms name
      let forbidden := axioms.filter (fun a => a != `propext && a != `Quot.sound)
      if !forbidden.isEmpty then
        throwError "Unexpected axioms: {name}: {forbidden}"
  if count == 0 then throwError "No owned cleaner declarations found"
  logInfo m!"Audited {count} cleaner declarations; no unexpected axioms."

#eval do
  let controls := ApSpec.Run1FullReturnCleanup.Controls.controls
  if !(controls.all id) then throw (IO.userError "FULL cleaner controls failed")
  IO.println s!"Executable FULL cleaner controls passed: {controls.length}."

#print axioms ApSpec.Run1FullReturnCleanup.serial_bulk
#print axioms ApSpec.Run1FullReturnCleanup.bulk_idempotent
#print axioms ApSpec.Run1FullReturnCleanup.exact_root_partial_demand_header
#print axioms ApSpec.Run1FullReturnCleanup.located_serial_bulk_scalar
#print axioms ApSpec.Run1FullReturnCleanup.located_serial_with_normalization
#print axioms ApSpec.Run1FullReturnCleanup.flow_serial_bulk
#print axioms ApSpec.Run1FullReturnCleanup.counted_flow_value_equal
#print axioms ApSpec.Run1FullReturnCleanup.flow_header_operations_never_increase
#print axioms ApSpec.Run1FullReturnCleanup.flow_header_operations_strictly_reduce
#print axioms ApSpec.Run1FullReturnCleanup.new_attempt_has_old_occurrence
#print axioms ApSpec.Run1FullReturnCleanup.decoded_has_executable_preimage
#print axioms ApSpec.Run1FullReturnCleanup.history_exit_equal
#print axioms ApSpec.Run1FullReturnCleanup.static_mismatch_promotes_once
