/- Focused constructive audit for the selected group-mark domain and schedule.
   No Kotlin, JVM, complete AP adapter or whole-engine claim follows. -/
import ApSpec.RuleGroupMarks
import ApSpec.RuleGroupRequestRepair
import ApSpec.RuleGroupSchedule
import ApSpec.RuleGroupConditions
import Lean

open Lean Elab Command

run_elab do
  let env ← getEnv
  let modules := #[`ApSpec.RuleGroupMarks, `ApSpec.RuleGroupRequestRepair,
    `ApSpec.RuleGroupSchedule, `ApSpec.RuleGroupConditions]
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
  logInfo m!"Group marks: audited {total} declarations; {unexpected} unexpected axioms."
  if unexpected > 0 then throwError "Constructive axiom audit failed"

#eval do
  let controls := ApSpec.RuleGroupMarks.Controls.controls ++
    ApSpec.RuleGroupRequestRepair.Controls.controls ++
    ApSpec.RuleGroupSchedule.Controls.controls ++
    ApSpec.RuleGroupConditions.Controls.controls
  for (name, passed) in controls do
    if !passed then throw (IO.userError ("Control failed: " ++ name))
  IO.println s!"Group mark controls PASS: {controls.length}"

#print axioms ApSpec.RuleGroupMarks.exact_clean_partition
#print axioms ApSpec.RuleGroupMarks.homogeneous_strict_slot_saving
#print axioms ApSpec.RuleGroupRequestRepair.concrete_survivor_covered
#print axioms ApSpec.RuleGroupRequestRepair.routeWitness
#print axioms ApSpec.RuleGroupRequestRepair.Handoff.survivorWitness
#print axioms ApSpec.RuleGroupRequestRepair.Handoff.fullQueryWitness
#print axioms ApSpec.RuleGroupSchedule.extracted_complete
#print axioms ApSpec.RuleGroupSchedule.simulateFinitePath
#print axioms ApSpec.RuleGroupConditions.exact_truth_implies_possible_truth
