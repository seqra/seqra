/- Focused constructive audit. Selected candidates are actual method-entry
   offers admitted by reachability in the fixed prescan call graph. The generic
   late-certificate control is optional, not a selected graph-invalidation rule.
   No claim about actual-entry construction, full-pattern/port semantics,
   call-graph reachability, scheduling, or Kotlin/JVM refinement. -/
import ApSpec.IncrementalRequestTable
import Lean
open Lean Elab Command

run_elab do
  let env ← getEnv
  let moduleName := `ApSpec.IncrementalRequestTable
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
    throwError "No incremental request declarations audited."
  logInfo m!"Audited {total} selected declarations; {failures} have unexpected axioms."
  if failures > 0 then
    throwError "Constructive incremental request audit failed."

#eval do
  if !(ApSpec.IncrementalRequestTable.Controls.checks.all id) then
    throw (IO.userError "Incremental request controls failed.")
  IO.println ("Executable incremental request controls passed: " ++
    toString ApSpec.IncrementalRequestTable.Controls.checks.length)

#print axioms ApSpec.IncrementalRequestTable.every_trace_valid
#print axioms ApSpec.IncrementalRequestTable.final_barrier_exact
#print axioms ApSpec.IncrementalRequestTable.all_arrival_orders_exact
#print axioms ApSpec.IncrementalRequestTable.no_pair_delivered_twice
#print axioms ApSpec.IncrementalRequestTable.discovery_frontier_exact
#print axioms ApSpec.IncrementalRequestTable.Controls.control_checks
