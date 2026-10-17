/- Run after `lake build`: `lake env lean -M2048 bottom-up-concrete-static-audit.lean`.
   Check every declaration owned by the two selected modules, including all
   generated/private helpers and their transitive proof dependencies. -/
import ApSpec.BottomUpConcreteStatic
import ApSpec.BottomUpConcreteStaticNeeds
import Lean

open Lean Elab Command

run_elab do
  let env ← getEnv
  let modules := #[`ApSpec.BottomUpConcreteStatic, `ApSpec.BottomUpConcreteStaticNeeds]
  let mut total : Nat := 0
  let mut failures : Nat := 0
  for moduleName in modules do
    let mut count : Nat := 0
    for (name, _) in env.constants.toList do
      let owned := match env.getModuleIdxFor? name with
        | some i => env.header.moduleNames[i.toNat]! == moduleName
        | none => false
      if owned then
        count := count + 1
        let deps ← collectAxioms name
        let bad := deps.filter (fun n => n != `propext && n != `Quot.sound)
        if !bad.isEmpty then
          failures := failures + 1
          logInfo m!"Unexpected axioms: {name}: {bad}"
    if count == 0 then
      throwError "Selected module has no audited declarations: {moduleName}"
    total := total + count
    logInfo m!"Audited {count} declarations from {moduleName}."
  logInfo m!"Audited {total} selected declarations; {failures} have unexpected axioms."
  if failures > 0 then
    throwError "Concrete static constructive audit failed."

#eval do
  if !(ApSpec.BottomUpConcreteStatic.Controls.checks.all id) then
    throw (IO.userError "Actual concrete transport controls failed.")
  IO.println ("Executable actual concrete transport controls passed: " ++
    toString ApSpec.BottomUpConcreteStatic.Controls.checks.length)
  if !(ApSpec.BottomUpConcreteStaticNeeds.Controls.checks.all id) then
    throw (IO.userError "Fast static need controls failed.")
  IO.println ("Executable fast static need controls passed: " ++
    toString ApSpec.BottomUpConcreteStaticNeeds.Controls.checks.length)
