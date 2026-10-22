import ApSpec.Run1OperationStaticWriters
import ApSpec.Run1SummaryAliasAdmission
import Lean

open Lean Elab Command

run_elab do
  let env ← getEnv
  let mut writerTotal : Nat := 0
  let mut aliasTotal : Nat := 0
  for (name, _) in env.constants.toList do
    let module := match env.getModuleIdxFor? name with
      | some i => env.header.moduleNames[i.toNat]!
      | none => Name.anonymous
    if module == `ApSpec.Run1OperationStaticWriters || module == `ApSpec.Run1SummaryAliasAdmission then
      if module == `ApSpec.Run1OperationStaticWriters then
        writerTotal := writerTotal + 1
      else
        aliasTotal := aliasTotal + 1
      let dependencies ← collectAxioms name
      let forbidden := dependencies.filter (fun n => n != `propext && n != `Quot.sound)
      if !forbidden.isEmpty then
        throwError "Unexpected axioms: {name}: {forbidden}"
  if writerTotal == 0 || aliasTotal == 0 then
    throwError "Both selected modules must have audited declarations"
  logInfo m!"Audited {writerTotal} writer declarations and {aliasTotal} alias declarations; no unexpected axioms."

namespace Run1WriterAliasAudit

open ApSpec

def writerControls : List Bool :=
  let w := Run1OperationStaticWriters.Controls.setter
  let row := Run1OperationStaticWriters.Controls.row
  let empty : Run1OperationStaticWriters.Stores := {}
  let need : Run1OperationStaticWriters.PairState := {}
  [decide ((Run1OperationStaticWriters.promote empty row).writers = []),
   decide ((Run1OperationStaticWriters.localResult
      (Run1OperationStaticWriters.promote empty row) row).writers =
      [Run1OperationStaticWriters.writerKey w]),
   decide ((Run1OperationStaticWriters.localResult
      (Run1OperationStaticWriters.localResult empty row)
      ⟨1, { w with operation := 99 }⟩).writers = [Run1OperationStaticWriters.writerKey w]),
   decide (Run1OperationStaticWriters.Controls.wrapperStores.summaries.length = 3),
   decide (Run1OperationStaticWriters.Controls.wrapperStores.writers.length = 1),
   decide (Run1OperationStaticWriters.Controls.sourceReplay.requests = [.base 1 0, .base 0 0]),
   decide (Run1OperationStaticWriters.Controls.sourceReplay.inputs = [.base 0 0, .base 1 0]),
   decide (Run1OperationStaticWriters.Controls.staticReplay.requests = [.base 1 0, .field 20]),
   decide (Run1OperationStaticWriters.Controls.staticReplay.inputs = [.field 20, .base 1 0]),
   decide (Run1OperationStaticWriters.Controls.copyReplay.requests = [.field 20]),
   Run1OperationStaticWriters.poolEligible .zero,
   Run1OperationStaticWriters.poolEligible (.staticField 20),
   !Run1OperationStaticWriters.poolEligible (.base 1 0),
   decide (Run1OperationStaticWriters.request (Run1OperationStaticWriters.arrive need) =
      Run1OperationStaticWriters.arrive (Run1OperationStaticWriters.request need)),
   !Run1OperationStaticWriters.ready (Run1OperationStaticWriters.request need),
   !Run1OperationStaticWriters.ready (Run1OperationStaticWriters.arrive need),
   Run1OperationStaticWriters.ready
      (Run1OperationStaticWriters.request (Run1OperationStaticWriters.arrive need)),
   decide (¬ Run1OperationStaticWriters.enabled
      { w with selectors := { markExclusion := [3] } } 3),
   decide (Run1OperationStaticWriters.writerKey w ≠ Run1OperationStaticWriters.writerKey
      { w with selectors := { markExclusion := [3] } })]

def aliasControls : List Bool :=
  let p := Run1SummaryAliasAdmission.mixedPremise
  let rows := Run1SummaryAliasAdmission.mixedRows
  Run1SummaryAliasAdmission.tailControls ++
  [decide ((Run1SummaryAliasAdmission.decode
      (Run1SummaryAliasAdmission.noEffectRows p rows)).length = 1),
   decide ((Run1SummaryAliasAdmission.decode
      (Run1SummaryAliasAdmission.effectRows p rows)).length = 2),
   !Run1SummaryAliasAdmission.noMemoryEffect
      Run1SummaryAliasAdmission.rawArgument Run1SummaryAliasAdmission.rawReturn,
   Run1SummaryAliasAdmission.noMemoryEffect
      Run1SummaryAliasAdmission.collapsedCaller Run1SummaryAliasAdmission.collapsedCaller,
   !Run1SummaryAliasAdmission.run1Admits .noEffect .normal,
   !Run1SummaryAliasAdmission.run1Admits .noEffect .demand,
   Run1SummaryAliasAdmission.run1Admits .unclassified .demand,
   Run1SummaryAliasAdmission.run1Admits .effect .normal,
   Run1SummaryAliasAdmission.existingLaterAdmits true .demand,
   !Run1SummaryAliasAdmission.scopedAdmits true .noEffect true .demand,
   Run1SummaryAliasAdmission.scopedAdmits false .noEffect true .demand]

theorem writer_controls_pass : writerControls.all id = true := by decide
theorem alias_controls_pass : aliasControls.all id = true := by decide

#eval do
  if !(writerControls.all id) then throw (IO.userError "Writer controls failed")
  if !(aliasControls.all id) then throw (IO.userError "Alias controls failed")
  IO.println s!"Executable controls passed: {writerControls.length} writer, {aliasControls.length} alias."

#print axioms ApSpec.Run1OperationStaticWriters.finite_closure_equivalence
#print axioms ApSpec.Run1OperationStaticWriters.replay_has_finite_requests
#print axioms ApSpec.Run1OperationStaticWriters.local_writer_even_after_promotion
#print axioms ApSpec.Run1OperationStaticWriters.covered_after_semantic_replacement
#print axioms ApSpec.Run1SummaryAliasAdmission.scalar_classifier_exact
#print axioms ApSpec.Run1SummaryAliasAdmission.partition_lossless
#print axioms ApSpec.Run1SummaryAliasAdmission.selected_scalar_packed_outputs
#print axioms ApSpec.Run1SummaryAliasAdmission.stages_keep_admission
#print axioms ApSpec.Run1SummaryAliasAdmission.selected_differs_from_old_demand_bypass
#print axioms writer_controls_pass
#print axioms alias_controls_pass

end Run1WriterAliasAudit
