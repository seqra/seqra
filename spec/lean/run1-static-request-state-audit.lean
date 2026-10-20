import ApSpec.EnumAliasTargets
import ApSpec.ImmutableGroupNormalization
import ApSpec.Run1ThreeSetRequests
import ApSpec.ExactClassMarkers
import Lean

open Lean Elab Command

run_elab do
  let env ← getEnv
  let modules := [`ApSpec.EnumAliasTargets, `ApSpec.ImmutableGroupNormalization,
    `ApSpec.Run1ThreeSetRequests, `ApSpec.GroupedRun1Initials, `ApSpec.ExactClassMarkers]
  let mut total : Nat := 0
  for target in modules do
    let mut count : Nat := 0
    for (name, _) in env.constants.toList do
      let owned := match env.getModuleIdxFor? name with
        | some i => env.header.moduleNames[i.toNat]! == target
        | none => false
      if owned then
        count := count + 1
        let axioms ← collectAxioms name
        let unexpected := axioms.filter (fun a => a != `propext && a != `Quot.sound)
        if !unexpected.isEmpty then
          throwError "Unexpected axioms in {name}: {unexpected}"
    if count == 0 then throwError "No declarations audited in {target}"
    total := total + count
    logInfo m!"Audited {count} declarations in {target}; no unexpected axioms."
  logInfo m!"Selected audit total: {modules.length} modules, {total} declarations."

#eval do
  let enumControls := ApSpec.EnumAliasTargets.Controls.controls
  let markControls := ApSpec.ImmutableGroupNormalization.controls
  let initialControls := ApSpec.GroupedRun1Initials.Controls.controls
  let exactClassControls := ApSpec.ExactClassMarkers.controls
  if !(enumControls.all Prod.snd) || !(markControls.all id) ||
      !(initialControls.all Prod.snd) || !(exactClassControls.all id) then
    throw (IO.userError "Selected optimization controls failed")
  IO.println s!"Enum controls: {enumControls.length}; normalization controls: {markControls.length}"
  IO.println s!"Current initial controls: {initialControls.length}; exact class controls: {exactClassControls.length}"

open ApSpec.Run1ThreeSetRequests in
def protocolControls : List Bool :=
  [ req (request {})
  , reach (write {})
  , processed (write (request {}))
  , processed (request (write {}))
  , !(ask (write {}) ⟨true, false⟩).2
  , (ask (request {}) ⟨true, false⟩).2
  , !(ask (request {}) (ask (request {}) ⟨true, false⟩).1).2
  , (consume (admit (admit {} 1) 2) 1).waiting.contains 2
  , (canonicalWitness [1, 2]).val == [1, 2]
  , !(runnable [1, 2] [])
  ]

example : protocolControls.all id = true := by decide

#eval do
  if !protocolControls.all id then throw (IO.userError "Protocol controls failed")
  IO.println s!"Protocol controls: {protocolControls.length}"

#print axioms ApSpec.Run1ThreeSetRequests.canonical_covers
#print axioms ApSpec.Run1ThreeSetRequests.requested_transitive_closure
#print axioms ApSpec.Run1ThreeSetRequests.full_mark_guidance
#print axioms ApSpec.Run1ThreeSetRequests.empty_cycle_cannot_complete_nd
#print axioms ApSpec.Run1ThreeSetRequests.queued_reply_preserves_new_membership
