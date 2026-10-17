/- Focused audit of the additive replay model, including generated/private
   declarations and transitive proof dependencies. Does not audit Kotlin. -/
import ApSpec.StaticNeedReplayBatch
import Lean
open Lean Elab Command

run_elab do
  let env ← getEnv
  let moduleName := `ApSpec.StaticNeedReplayBatch
  let mut total : Nat := 0
  let mut failures : Nat := 0
  for (name, _) in env.constants.toList do
    let owned := match env.getModuleIdxFor? name with
      | some i => env.header.moduleNames[i.toNat]! == moduleName
      | none => false
    if owned then
      total := total + 1
      let deps ← collectAxioms name
      let bad := deps.filter (fun n => n != `propext && n != `Quot.sound)
      if !bad.isEmpty then
        failures := failures + 1
        logInfo m!"Unexpected axioms: {name}: {bad}"
  if total == 0 then
    throwError "No replay model declarations audited."
  logInfo m!"Audited {total} selected declarations; {failures} have unexpected axioms."
  if failures > 0 then
    throwError "Constructive replay audit failed."

#eval do
  if !(ApSpec.StaticNeedReplayBatch.Controls.checks.all id) then
    throw (IO.userError "Static replay batch controls failed.")
  IO.println ("Executable replay batch controls passed: " ++
    toString ApSpec.StaticNeedReplayBatch.Controls.checks.length)

#print axioms ApSpec.StaticNeedReplayBatch.multi_nodes_exact
#print axioms ApSpec.StaticNeedReplayBatch.multi_path_complete
#print axioms ApSpec.StaticNeedReplayBatch.grouped_exact
#print axioms ApSpec.StaticNeedReplayBatch.every_trace_valid
#print axioms ApSpec.StaticNeedReplayBatch.trace_flush_exact
#print axioms ApSpec.StaticNeedReplayBatch.same_source_union
#print axioms ApSpec.StaticNeedReplayBatch.fresh_delta_exact
#print axioms ApSpec.StaticNeedReplayBatch.committed_filter_stutter
#print axioms ApSpec.StaticNeedReplayBatch.fixed_graph_visit_cost_nonincrease
#print axioms ApSpec.StaticNeedReplayBatch.runtime_static_scan_nonincrease
#print axioms ApSpec.StaticNeedReplayBatch.Controls.distinct_mark_cross_product_unsound

#print axioms ApSpec.StaticNeedReplayBatch.stable_route_repeat_strict
#print axioms ApSpec.StaticNeedReplayBatch.Controls.strict_static_saving_with_equality
#print axioms ApSpec.StaticNeedReplayBatch.Controls.grouping_overhead_can_exceed_saving
