/- Run after `lake build`: `lake env lean -M2048 bottom-up-static-audit.lean`.
   Audit every owned declaration, including generated/private helpers, then
   execute the six selected control groups. No analyzer implementation runs. -/
import ApSpec.BottomUpStaticDomain
import ApSpec.BottomUpStaticApplication
import ApSpec.BottomUpStaticProtocol
import ApSpec.BottomUpStaticTransport
import ApSpec.BottomUpStaticRequests
import ApSpec.BottomUpStaticModel
import Lean

open Lean Elab Command

run_elab do
  let env ← getEnv
  let modules := #[`ApSpec.BottomUpStaticDomain, `ApSpec.BottomUpStaticApplication,
    `ApSpec.BottomUpStaticProtocol, `ApSpec.BottomUpStaticTransport,
    `ApSpec.BottomUpStaticRequests, `ApSpec.BottomUpStaticModel]
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
    throwError "Bottom-up static constructive audit failed."

#eval do
  let groups : List (String × Bool) := [
    ("domain", ApSpec.BottomUpStaticDomain.Controls.checks),
    ("scalar application", ApSpec.BottomUpStaticApplication.Controls.checks),
    ("call protocol", ApSpec.BottomUpStaticProtocol.Controls.checks.all id),
    ("transport", ApSpec.BottomUpStaticTransport.Certificate.checks.all id),
    ("frozen requests", ApSpec.BottomUpStaticRequests.controls.all id),
    ("scalar transport model", ApSpec.BottomUpStaticModel.Controls.checks)]
  for (name, passed) in groups do
    if !passed then
      throw (IO.userError ("Bottom-up static control group failed: " ++ name))
    IO.println ("Executable bottom-up static controls passed: " ++ name)
