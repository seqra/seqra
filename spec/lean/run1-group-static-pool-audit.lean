/- Constructive audit for the proposed run-1 group static pool.
   It does not prove the JVM, AP index, runner or point-query adapters. -/
import ApSpec.Run1GroupStaticPool
import ApSpec.Run1GroupStaticTrace
import ApSpec.Run1GroupStaticHandoff
import Lean

open Lean Elab Command

run_elab do
  let env ← getEnv
  let modules := #[`ApSpec.Run1GroupStaticPool, `ApSpec.Run1GroupStaticTrace,
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
  logInfo m!"Group static pool: audited {total} declarations; {unexpected} unexpected axioms."
  if unexpected > 0 then throwError "Constructive axiom audit failed"

#eval do
  let controls := ApSpec.Run1GroupStaticPool.Controls.controls ++
    ApSpec.Run1GroupStaticTrace.Controls.controls ++
    ApSpec.Run1GroupStaticHandoff.Controls.controls
  for (name, passed) in controls do
    if !passed then throw (IO.userError ("Control failed: " ++ name))
  IO.println s!"Group static pool controls PASS: {controls.length}"

#print axioms ApSpec.Run1GroupStaticPool.process_event_coverage
#print axioms ApSpec.Run1GroupStaticPool.indexed_scalar_equal
#print axioms ApSpec.Run1GroupStaticPool.fresh_after_commit_empty
#print axioms ApSpec.Run1GroupStaticTrace.every_finite_trace_covered
#print axioms ApSpec.Run1GroupStaticTrace.atReadWitness
#print axioms ApSpec.Run1GroupStaticHandoff.compilePath
#print axioms ApSpec.Run1GroupStaticHandoff.compileGraphPath
#print axioms ApSpec.Run1GroupStaticHandoff.every_full_group_member_gets_seed
