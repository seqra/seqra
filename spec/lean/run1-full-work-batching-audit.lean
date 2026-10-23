import ApSpec.Run1TemporaryStaticSupplies
import ApSpec.Run1FullReturnCleanup
import ApSpec.Run1WorkBatchingClosure
import Lean

open Lean Elab Command

run_elab do
  let owners := #[`ApSpec.Run1TemporaryStaticSupplies,
    `ApSpec.Run1FullReturnCleanup, `ApSpec.Run1WorkBatchingClosure]
  let env ← getEnv
  let mut total : Nat := 0
  for owner in owners do
    let mut count : Nat := 0
    for (name, _) in env.constants.toList do
      let module := match env.getModuleIdxFor? name with
        | some index => env.header.moduleNames[index.toNat]!
        | none => Name.anonymous
      if module == owner then
        count := count + 1
        let dependencies ← collectAxioms name
        let forbidden := dependencies.filter (fun ax => ax != `propext && ax != `Quot.sound)
        if !forbidden.isEmpty then
          throwError "Unexpected axioms: {name}: {forbidden}"
    if count == 0 then throwError "No declarations audited for {owner}"
    total := total + count
    logInfo m!"Audited {owner}: {count} declarations."
  logInfo m!"Total selected batching declarations: {total}; no unexpected axioms."

#eval do
  let supply := ApSpec.Run1TemporaryStaticSupplies.Controls.controls
  let cleanup := ApSpec.Run1FullReturnCleanup.Controls.controls
  let closure := ApSpec.Run1WorkBatchingClosure.Controls.controls
  let all := supply ++ cleanup ++ closure
  if !(all.all id) then throw (IO.userError "Selected run-1 batching control failed")
  IO.println s!"Combined executable controls: {all.length} passed (supply {supply.length}, cleanup {cleanup.length}, closure {closure.length})."

#print axioms ApSpec.Run1TemporaryStaticSupplies.canonical_supply_commit_equal
#print axioms ApSpec.Run1TemporaryStaticSupplies.whole_commit_equal
#print axioms ApSpec.Run1FullReturnCleanup.located_serial_bulk_scalar
#print axioms ApSpec.Run1FullReturnCleanup.located_serial_with_normalization
#print axioms ApSpec.Run1FullReturnCleanup.history_exit_equal
#print axioms ApSpec.Run1FullReturnCleanup.static_mismatch_promotes_once
#print axioms ApSpec.Run1FullReturnCleanup.counted_flow_value_equal
#print axioms ApSpec.Run1FullReturnCleanup.flow_header_operations_strictly_reduce
#print axioms ApSpec.Run1WorkBatchingClosure.transport
#print axioms ApSpec.Run1WorkBatchingClosure.transportBack
#print axioms ApSpec.Run1WorkBatchingClosure.finite_closures_equivalent
#print axioms ApSpec.Run1WorkBatchingClosure.handoff_membership
#print axioms ApSpec.Run1WorkBatchingClosure.transportObservation
