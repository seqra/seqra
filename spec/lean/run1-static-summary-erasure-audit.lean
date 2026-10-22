import ApSpec.Run1StaticSummaryErasure
import ApSpec.Run1StaticRelayProjection
import ApSpec.Run1StaticQueryCompletion
import ApSpec.Run1StaticErasureBackwardReplay
import Lean

open Lean Elab Command

run_elab do
  let modules := [
    `ApSpec.Run1StaticSummaryErasure,
    `ApSpec.Run1StaticRelayProjection,
    `ApSpec.Run1StaticQueryCompletion,
    `ApSpec.Run1StaticErasureBackwardReplay]
  let env ← getEnv
  let mut counts : List Nat := modules.map (fun _ => 0)
  for (name, _) in env.constants.toList do
    let module := match env.getModuleIdxFor? name with
      | some i => env.header.moduleNames[i.toNat]!
      | none => Name.anonymous
    for index in [:modules.length] do
      if module == modules[index]! then
        counts := counts.modify index (· + 1)
        let dependencies ← collectAxioms name
        let forbidden := dependencies.filter (fun n => n != `propext && n != `Quot.sound)
        if !forbidden.isEmpty then
          throwError "Unexpected axioms: {name}: {forbidden}"
  for index in [:modules.length] do
    if counts[index]! == 0 then
      throwError "No owned declarations audited for {modules[index]!}"
    logInfo m!"Audited {modules[index]!}: {counts[index]!} declarations."
  logInfo m!"Total: {counts.foldl (· + ·) 0}; no unexpected axioms."

namespace RelayAudit
open ApSpec.Run1StaticRelayProjection

def controls : List Bool := [
  decide (reapply Controls.exactTarget = Controls.exactTarget),
  decide (reapply Controls.anyTarget = Controls.anyTarget),
  decide (reapply Controls.mustTarget = Controls.mustTarget),
  decide ((reapply Controls.mustTarget).group = 99),
  decide ((reapply Controls.mustTarget).path = [4,7]),
  decide ((reapply Controls.mustTarget).base = 1),
  decide ((reapply Controls.mustTarget).tail = .must),
  decide ((reapply Controls.mustTarget).demand = true),
  decide (reapply (reapply Controls.mustTarget) = Controls.mustTarget),
  decide (Controls.anyTarget.erase = Controls.mustTarget.erase),
  decide (Controls.anyTarget.region ≠ Controls.mustTarget.region),
  decide ((reapply {Controls.mustTarget with exclusion := .set [11]}).exclusion = .empty),
  decide ((reapply {Controls.exactTarget with path := [4]}).path = [4])
]
end RelayAudit

def runControls (name : String) (checks : List Bool) : IO Nat := do
  if !(checks.all id) then throw (IO.userError s!"Failed controls: {name}")
  IO.println s!"Controls passed: {name}: {checks.length}"
  return checks.length

#eval do
  let controls : List (String × List Bool) := [
    ("producer/relay", ApSpec.Run1StaticSummaryErasure.Controls.controls),
    ("scalar projection", RelayAudit.controls),
    ("actual queries", ApSpec.Run1StaticQueryCompletion.controls),
    ("backward replay", ApSpec.Run1StaticErasureBackwardReplay.Controls.controls)]
  let counts ← controls.mapM (fun (packet : String × List Bool) => runControls packet.1 packet.2)
  IO.println s!"Executable controls: {counts.sum}; all passed."

#print axioms ApSpec.Run1StaticSummaryErasure.localOutputPreimage
#print axioms ApSpec.Run1StaticSummaryErasure.compress
#print axioms ApSpec.Run1StaticSummaryErasure.complete_operational_closure
#print axioms ApSpec.Run1StaticSummaryErasure.no_source_free_cycle
#print axioms ApSpec.Run1StaticRelayProjection.reapply_preserves_region
#print axioms ApSpec.Run1StaticRelayProjection.normalizer_idempotent
#print axioms ApSpec.Run1StaticRelayProjection.reapply_erases_to_actual_AP
#print axioms ApSpec.Run1StaticQueryCompletion.coveredQuery
#print axioms ApSpec.Run1StaticQueryCompletion.old_query_batch_covered
#print axioms ApSpec.Run1StaticQueryCompletion.completion_adds_no_locations
#print axioms ApSpec.Run1StaticErasureBackwardReplay.replay
#print axioms ApSpec.Run1StaticErasureBackwardReplay.source_hit_identity_preserved
#print axioms ApSpec.Run1StaticErasureBackwardReplay.complete_binding_preserved
