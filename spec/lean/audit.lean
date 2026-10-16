/-
  Run: cd spec/lean && lake build && lake env lean audit.lean

  Check every declaration from an ApSpec module, including private declarations and generated
  helpers. Fail if a transitive axiom is not propext or Quot.sound. This checks compiled proof
  dependencies, rather than only the declarations that have a #print axioms command.
-/
import ApSpec
import Lean

open Lean Elab Command

run_elab do
  let env ← getEnv
  let mut audited : Nat := 0
  let mut failed : Nat := 0
  for (name, _) in env.constants.toList do
    let fromApSpec := match env.getModuleIdxFor? name with
      | some i => env.header.moduleNames[i.toNat]!.getRoot == `ApSpec
      | none => false
    if fromApSpec then
      audited := audited + 1
      let deps ← collectAxioms name
      let unexpected := deps.filter (fun n => n != `propext && n != `Quot.sound)
      if !unexpected.isEmpty then
        failed := failed + 1
        logInfo m!"Unexpected axioms: {name}: {unexpected}"
  logInfo m!"Audited {audited} ApSpec declarations; {failed} have unexpected axioms."
  if failed > 0 then
    throwError "Constructive axiom audit failed."

-- Run the certified L6 witness through Lean's compiler as ordinary data.
#eval do
  let f := ApSpec.Abs.witnessVector 1
  if f.base != 3 || !f.path.isEmpty || f.kind != ApSpec.Kind.star ApSpec.Excl.empty ||
      f.mark != ApSpec.MarkA.star || f != ApSpec.Abs.witnessVector 2 then
    throw (IO.userError "Executable emission witness failed.")
  IO.println "Executable emission witness passed."

-- The cleaner witness is computed from cleanRes, including mark exclusions.
#eval do
  let f := ApSpec.Abs.cleanerWitnessVector 1
  let expected : ApSpec.AFact := ⟨⟨3, [4], .star ApSpec.Excl.empty, .star⟩, false⟩
  if f != expected then
    throw (IO.userError "Executable disjoint cleaner witness failed.")
  let g := ApSpec.Abs.cleanerOtherMarkVector
  let expectedOther : ApSpec.AFact := ⟨⟨3, [], .star ApSpec.Excl.empty, .starEx [2]⟩, false⟩
  if g != expectedOther then
    throw (IO.userError "Executable cleaner exclusion witness failed.")
  IO.println "Executable cleaner witnesses passed."

-- The lowered action computes an old-base or temporary intermediate location.
#eval do
  if ApSpec.CleanerLowering.lowerKeepVector != (⟨4, [4], 1⟩ : ApSpec.Loc) ||
      ApSpec.CleanerLowering.lowerTempVector != (⟨6, [9], 2⟩ : ApSpec.Loc) then
    throw (IO.userError "Executable field-cleaner lowering witness failed.")
  let expected : ApSpec.AFact := ⟨⟨4, [], .star (.set [5, 5]), .star⟩, false⟩
  if ApSpec.CleanerLowering.backwardKeepVector != expected then
    throw (IO.userError "Executable backward keep witness failed.")
  IO.println "Executable field-cleaner lowering witnesses passed."

-- Check all reach/mark policies and the computed normal outside-field X witness.
#eval do
  let expected : List (List Bool) :=
    [[true, false, false, false, false], [false, true, false, false, false],
      [true, true, false, false, false], [true, false, true, false, false],
      [false, true, false, true, false], [true, true, true, true, false]]
  if ApSpec.CleanerLowering.cleanReachVectors != expected ||
      ApSpec.CleanerLowering.lowerReachVectors !=
        ([⟨6, [9], 1⟩, ⟨6, [], 1⟩, ⟨6, [9], 2⟩,
          ⟨6, [9], 2⟩, ⟨6, [], 2⟩, ⟨4, [4], 1⟩] : List ApSpec.Loc) ||
      ApSpec.CleanerLowering.backwardKeepReachVectors !=
        List.replicate 6 ApSpec.CleanerLowering.backwardKeepVector then
    throw (IO.userError "Executable field-cleaner reach policy failed.")
  for reach in [ApSpec.CleanReach.exact, .below, .atAndBelow] do
    let f := (ApSpec.FieldCleanerX.outsideWitness reach).val
    if f != ApSpec.FieldCleanerX.outside || f.af.demand then
      throw (IO.userError "Executable outside-field X witness failed.")
  IO.println "Executable field-cleaner reach and X witnesses passed."

-- Data certificates for the actual hit, normal report and exact run-3 closure.
#eval do
  let hit := ApSpec.ReviewFieldCleaner.sourceHitWitness.val
  let finding := ApSpec.ReviewFieldCleaner.findingWitness.val
  let trace := ApSpec.ReviewFieldCleaner.traceWitness.val
  if hit != ApSpec.ReviewFieldCleaner.sourceE ||
      finding != (⟨ApSpec.ReviewReversal.sink, false⟩ : ApSpec.AFact) ||
      trace.edges.length != 23 || !trace.reqs.isEmpty then
    throw (IO.userError "Executable lowered-program certificate failed.")
  IO.println "Executable lowered-program certificates passed."

-- The root EXACT regression supplies data for its real flow and exact loss trace.
#eval do
  let sink := ApSpec.ReviewRootCleaner.concreteSinkWitness.val
  let trace := ApSpec.ReviewRootCleaner.strictTraceWitness.val
  if sink != (⟨2, [6, 4], 1⟩ : ApSpec.Loc) ||
      (trace.1.edges.length, trace.2.1.edges.length, trace.2.2.edges.length) != (17, 13, 7) ||
      !trace.1.reqs.isEmpty || !trace.2.1.reqs.isEmpty || !trace.2.2.reqs.isEmpty ||
      !trace.2.2.vulns.isEmpty then
    throw (IO.userError "Executable root EXACT regression failed.")
  IO.println "Executable root EXACT regression passed."

-- The proposed backward cleaner produces a reached record and false normal finding.
#eval do
  let record := ApSpec.ReviewBackwardCleaner.badRecordWitness.val
  let finding := ApSpec.ReviewBackwardCleaner.normalFindingWitness.val
  let expectedRec : ApSpec.PFact × ApSpec.AFact :=
    (⟨4, [], .star ApSpec.Excl.empty, .star⟩,
      ⟨⟨5, [6], .star (.set [4, 4]), .star⟩, false⟩)
  let added : ApSpec.AFact := ⟨⟨4, [], .exact, .conc 1⟩, false⟩
  let expectedReturn : ApSpec.AFact := ⟨⟨5, [6], .exact, .conc 1⟩, false⟩
  if record != expectedRec ||
      (ApSpec.applySummary added record.1 record.2).facts != [expectedReturn] ||
      finding != (⟨⟨2, [6], .exact, .conc 1⟩, false⟩ : ApSpec.AFact) then
    throw (IO.userError "Executable backward cleaner experiment failed.")
  IO.println "Executable backward cleaner experiment passed."

-- F75 uses the same base-dependent cleaner in both directions.
#eval do
  let repaired := ApSpec.ReviewBaseCleaner.Repaired.repairedWitness.val
  let rejected := ApSpec.ReviewBaseCleaner.Blocked.rejectedWitness.val
  let expected : ApSpec.PFact × ApSpec.AFact :=
    (⟨3, [], .exact, .conc 1⟩, ⟨⟨5, [6, 4], .exact, .conc 1⟩, false⟩)
  let added : ApSpec.AFact := ⟨⟨3, [], .exact, .conc 1⟩, false⟩
  let rootT : ApSpec.AFact := ⟨⟨4, [], .exact, .conc 1⟩, false⟩
  if repaired != expected ||
      (ApSpec.applySummary added repaired.1 repaired.2).facts != [expected.2] ||
      !(ApSpec.applySummary rootT rejected.1 rejected.2).facts.isEmpty ||
      !(ApSpec.BaseCleaner.vectors.all ApSpec.BaseCleaner.vectorOK) ||
      (ApSpec.BaseCleaner.requestedPair 1).value != (none, some 1) ||
      (ApSpec.BaseCleaner.requestedPair 2).value !=
        (some (ApSpec.BaseCleaner.residual ApSpec.BaseCleaner.fieldStar 1), none) ||
      ApSpec.BaseCleaner.workWitness.val != (33, 1) then
    throw (IO.userError "Executable base-cleaner correction failed.")
  IO.println "Executable base-cleaner correction and batch witnesses passed."

-- Current local demand contracts compute their actual AP operation results.
#eval do
  let emitted := (ApSpec.CurrentDemand.sharedEmission 1).premise
  let reduced := ApSpec.CurrentDemand.reducedWitness.conclusion
  let expectedEmission : ApSpec.PFact := ⟨3, [], .star ApSpec.Excl.empty, .star⟩
  let expectedReduction : ApSpec.AFact := ⟨⟨5, [4], .exact, .conc 1⟩, true⟩
  if emitted != expectedEmission ||
      emitted != (ApSpec.CurrentDemand.sharedEmission 2).premise ||
      reduced != expectedReduction ||
      ApSpec.CurrentDemand.reductionWorkWitness.val != (33, 1) ||
      !(ApSpec.Handoff.restrictI ApSpec.CurrentDemand.j ApSpec.CurrentDemand.g
        ApSpec.CurrentDemand.markRejected).isNone ||
      (ApSpec.CurrentDemand.restrictIndexed
        (ApSpec.CurrentDemand.demandIndex ApSpec.CurrentDemand.storedDemands)
        ApSpec.CurrentDemand.j ApSpec.CurrentDemand.g) != [expectedReduction] then
    throw (IO.userError "Executable current demand certificates failed.")
  IO.println "Executable current demand certificates and prefix-index witness passed."

-- The current tree reference reads marks, converts any to exact and keeps its key.
#eval do
  let t := ApSpec.CurrentTreeReduction.treeWitness.val
  let actual := ApSpec.Tree.toAFacts 5 t
  let expected := ApSpec.CurrentTreeReduction.expected
  if t.mx != [3] || t.excl != ApSpec.Excl.set [8] || !t.demand ||
      !(actual.all (fun a => expected.contains a)) ||
      !(expected.all (fun a => actual.contains a)) || actual.length != 2 then
    throw (IO.userError "Executable current tree reduction certificate failed.")
  IO.println "Executable current tree reduction certificate passed."

-- The native record shape survives actual reversal, including its mark exclusion.
#eval do
  let record := ApSpec.Current.recordShapeWitness.val
  let expected : ApSpec.PFact × ApSpec.AFact :=
    (⟨4, [], .star ApSpec.Excl.empty, .star⟩,
      ⟨⟨3, [], .star (.set [4]), .starEx [1]⟩, false⟩)
  let added : ApSpec.AFact := ⟨⟨4, [], .exact, .conc 1⟩, false⟩
  if record != expected || !(ApSpec.applySummary added record.1 record.2).facts.isEmpty then
    throw (IO.userError "Executable current record-shape certificate failed.")
  IO.println "Executable current record-shape certificate passed."

-- F76 reads cached must conclusions through a transient concrete-star premise.
#eval do
  let v := ApSpec.CurrentMustReverse.Certificate.exact.view
  let applied := ApSpec.CurrentMustReverse.Certificate.applied
  let excluded := ApSpec.CurrentMustReverse.Certificate.excluded
  let expectedPremise : ApSpec.PFact := ⟨5, [2], .star (.set [4]), .conc 1⟩
  let expectedOutput : ApSpec.AFact := ⟨⟨3, [], .exact, .conc 1⟩, false⟩
  let expectedDemand : ApSpec.AFact := ⟨⟨3, [], .any, .conc 1⟩, true⟩
  let incoming := ApSpec.CurrentMustReverse.Certificate.incoming
  let mustAny := ApSpec.CurrentMustReverse.Certificate.anyMust.view
  let mustExact := ApSpec.CurrentMustReverse.Certificate.exactMust.view
  if v.1 != expectedPremise || v.2 != expectedOutput ||
      applied.facts != [expectedOutput] || !applied.reqs.isEmpty ||
      ApSpec.applicable v.1 incoming.fact != true ||
      ApSpec.Abs.satW v.1 incoming.fact != false ||
      !(ApSpec.applySummary excluded v.1 v.2).facts.isEmpty ||
      (ApSpec.applySummary incoming mustAny.1 mustAny.2).facts != [expectedDemand] ||
      (ApSpec.applySummary incoming mustExact.1 mustExact.2).facts != [expectedDemand] ||
      ApSpec.CurrentMustReverse.Certificate.differentMarkTransfer.result.facts !=
        ([⟨⟨3, [], .exact, .conc 9⟩, false⟩] : List ApSpec.AFact) ||
      ApSpec.CurrentMustReverse.Certificate.mustAnyEndpoints.val !=
        ((⟨3, [9], 1⟩, ⟨5, [2, 7], 1⟩) : ApSpec.Loc × ApSpec.Loc) ||
      ApSpec.CurrentMustReverse.Certificate.mustExactEndpoints.val !=
        ((⟨3, [9], 1⟩, ⟨5, [2, 7], 1⟩) : ApSpec.Loc × ApSpec.Loc) then
    throw (IO.userError "Executable must-summary read-view certificate failed.")
  IO.println "Executable must-summary read-view certificates passed."

-- The original generic-Cross diagnostic and selected must-to-may copying.
#eval do
  let copied := ApSpec.CurrentMustHandoff.Certificate.copy.copied
  let expected : ApSpec.AnyTaintEx.XFact :=
    ⟨⟨⟨5, [], .any, .conc 1⟩, true⟩, ApSpec.Excl.empty⟩
  let expectedHandoff : ApSpec.DemandEdge :=
    ⟨ApSpec.CurrentMustHandoff.Certificate.incoming,
      some ApSpec.CurrentMustHandoff.Certificate.j⟩
  if copied != expected ||
      ApSpec.CurrentMustHandoff.Certificate.exactHandoff.val != expectedHandoff ||
      ApSpec.CurrentMustHandoff.Certificate.xExactHandoff.val != expectedHandoff ||
      ApSpec.CurrentMustHandoff.selectAny ApSpec.CurrentMustHandoff.Certificate.rawLeaves !=
        [(ApSpec.CurrentMustHandoff.Certificate.j, ApSpec.CurrentMustHandoff.Certificate.must.af)] then
    throw (IO.userError "Executable historical-selector and must-copy certificate failed.")
  IO.println "Executable historical-selector and must-copy certificates passed."

-- F76 reader guards and source chain; callee selection is checked separately.
#eval do
  let raw := ApSpec.CurrentMustReverse.Certificate.exactReadNative
  let view := ApSpec.CurrentMustReverse.Certificate.normalRead.view
  let piece := ApSpec.CurrentMustReverse.Certificate.exactPiece
  let mustAny := ApSpec.CurrentMustReverse.mustAnyNative 3 5 [] [2] 1 1 (.set [8]) (.set [4])
  let mustExact := ApSpec.CurrentMustReverse.mustExactNative 3 5 [] [2, 7] 1 1 (.set [8])
  let malformed := { raw with premiseEx := ApSpec.Excl.set [4] }
  let demand := { raw with conclusion := { raw.conclusion with af := { raw.conclusion.af with demand := true } } }
  if !ApSpec.CurrentMustReverse.exactMustGuardsB raw || view.2.demand ||
      view.2.fact != raw.premise ||
      ApSpec.CurrentMustReverse.exactMustGuardsB mustAny ||
      ApSpec.CurrentMustReverse.exactMustGuardsB mustExact ||
      ApSpec.CurrentMustReverse.exactMustGuardsB malformed ||
      ApSpec.CurrentMustReverse.exactMustGuardsB demand ||
      ApSpec.HandoffX.restrictIX raw.premise raw.premiseEx raw.conclusion
        ApSpec.CurrentMustReverse.Certificate.previousDemand != some piece ||
      piece.af.demand ||
      ApSpec.CurrentMustReverse.Certificate.upstreamSourceWitness.val !=
        ([⟨ApSpec.zeroFact, false⟩] : List ApSpec.AFact) then
    throw (IO.userError "Executable must-reader and source-chain certificate failed.")
  IO.println "Executable must-reader and source-chain certificates passed."

-- Concrete may requirements match above/at the view by inside, below by applicable.
#eval do
  let p := ApSpec.CurrentMustReverse.Certificate.exact.view.1
  let above : ApSpec.PFact := ⟨5, [], .any, .conc 1⟩
  let atFact : ApSpec.PFact := ⟨5, [2], .any, .conc 1⟩
  let below : ApSpec.PFact := ⟨5, [2, 7], .any, .conc 1⟩
  let blocked : ApSpec.PFact := ⟨5, [2, 4], .any, .conc 1⟩
  if !ApSpec.Abs.satW p above || !ApSpec.Abs.satW p atFact ||
      ApSpec.applicable p atFact || !ApSpec.applicable p below ||
      (ApSpec.Abs.satW p blocked || ApSpec.applicable p blocked) then
    throw (IO.userError "Executable concrete must-view matching certificate failed.")
  IO.println "Executable concrete must-view matching certificates passed."

-- Accepted local simplifications preserve facts, requests, views and stage guards.
#eval do
  if !ApSpec.CurrentSimplifications.Certificate.check then
    throw (IO.userError "Executable local simplification certificates failed.")
  IO.println "Executable local simplification certificates passed."

-- The forward conjunction layer is separate from next-run demand selection.
#eval do
  let normal := ApSpec.CurrentConjunction.completeCombination ApSpec.CurrentConjunction.cj
    (some ApSpec.CurrentConjunction.fa) (some ApSpec.CurrentConjunction.fb)
  let mixed := ApSpec.CurrentConjunction.completeCombination ApSpec.CurrentConjunction.cj
    (some ApSpec.CurrentConjunction.fa) (some ApSpec.CurrentConjunction.demandB)
  let absent := ApSpec.CurrentConjunction.completeCombination ApSpec.CurrentConjunction.cj
    (some ApSpec.CurrentConjunction.fa) none
  let publication := ApSpec.CurrentSummarySelection.publicationPlan .forward
    ⟨[ApSpec.CurrentConjunction.ja, ApSpec.CurrentConjunction.jb], ApSpec.CurrentConjunction.normalOutput⟩
    ApSpec.CurrentConjunction.normalOutput
  if normal != some (⟨ApSpec.CurrentConjunction.c, false⟩ : ApSpec.AFact) ||
      mixed != some (⟨ApSpec.CurrentConjunction.c, true⟩ : ApSpec.AFact) || absent != none ||
      !publication.1 || publication.2.af.demand ||
      ApSpec.CurrentConjunction.memberPatterns [ApSpec.CurrentConjunction.ja, ApSpec.CurrentConjunction.jb]
        ApSpec.CurrentConjunction.normalOutput !=
        ([⟨ApSpec.CurrentConjunction.c, some ApSpec.CurrentConjunction.a⟩,
          ⟨ApSpec.CurrentConjunction.c, some ApSpec.CurrentConjunction.b⟩] : List ApSpec.DemandEdge) ||
      ApSpec.CurrentConjunction.backwardBranches !=
        ([⟨ApSpec.CurrentConjunction.a, true⟩, ⟨ApSpec.CurrentConjunction.b, true⟩] : List ApSpec.AFact) ||
      ApSpec.CurrentConjunction.ReverseWitness.enabled
        [ApSpec.CurrentConjunction.a, ApSpec.CurrentConjunction.b] [ApSpec.CurrentConjunction.a] ||
      !ApSpec.CurrentConjunction.ReverseWitness.enabled
        [ApSpec.CurrentConjunction.a, ApSpec.CurrentConjunction.b]
        [ApSpec.CurrentConjunction.a, ApSpec.CurrentConjunction.b] ||
      !ApSpec.CurrentConjunction.ReverseWitness.enabled
        [ApSpec.CurrentConjunction.a] [ApSpec.CurrentConjunction.a] then
    throw (IO.userError "Executable conjunction layer and split witness failed.")
  IO.println "Executable conjunction layer and split witnesses passed."

-- F77 selection: layer, cardinality and backward zero; reader layer is separate.
#eval do
  let expected := [false, false, false, false, true, true, true, false, false, false, true, true]
  let j := ApSpec.CurrentSummarySelection.Certificate.exactEntry
  let m := ApSpec.CurrentSummarySelection.Certificate.mustEntry
  let leaves := ApSpec.CurrentSummarySelection.Certificate.normalLeaves
  let piece := ApSpec.CurrentSummarySelection.Certificate.outputExact
  let workload := ApSpec.CurrentSummarySelection.Certificate.workload
  let work := ApSpec.CurrentSummarySelection.Certificate.workWitness
  let expectedWork : ApSpec.CurrentSummarySelection.SelectionWork ×
      ApSpec.CurrentSummarySelection.SelectionWork := (⟨33, 33⟩, ⟨1, 0⟩)
  if ApSpec.CurrentSummarySelection.Certificate.cases != expected ||
      work != expectedWork ||
      ApSpec.CurrentSummarySelection.selectPacked .forward [j] false leaves != [] ||
      ApSpec.CurrentSummarySelection.selectPacked .forward [m] false leaves != [] ||
      (ApSpec.CurrentSummarySelection.countedPacked .forward [m] false workload).1 !=
        (ApSpec.CurrentSummarySelection.countedLeaves .forward [m] workload).1 ||
      ApSpec.CurrentSummarySelection.publicationPlan .forward
        ApSpec.CurrentSummarySelection.Certificate.rawMust piece != (false, piece) ||
      ApSpec.HandoffX.restrictIX m.fact m.ex
        ApSpec.CurrentSummarySelection.Certificate.outputMust
        ApSpec.CurrentSummarySelection.Certificate.restrictedDemand != some piece ||
      ApSpec.HandoffX.restrictIX m.fact m.ex
        ApSpec.CurrentSummarySelection.Certificate.outputDemand
        ApSpec.CurrentSummarySelection.Certificate.restrictedDemand !=
          some ApSpec.CurrentSummarySelection.Certificate.restrictedDemandPiece ||
      (ApSpec.applySummary ApSpec.CurrentSummarySelection.Certificate.incoming
        ApSpec.CurrentSummarySelection.Certificate.mustRead.view.1
        ApSpec.CurrentSummarySelection.Certificate.mustRead.view.2).facts !=
        ([⟨⟨3, [], .any, .conc 9⟩, true⟩] : List ApSpec.AFact) then
    throw (IO.userError "Executable full raw summary selector failed.")
  IO.println "Executable structural summary selector and one-group gate passed."

-- A removed final must leaf changes E; lookup uses the actual remainder key.
#eval do
  let result := ApSpec.CurrentTaintGroupKeys.Certificate.witness.val
  let expected : List ApSpec.CurrentTaintGroupKeys.Row :=
    [⟨ApSpec.Excl.empty, [ApSpec.CurrentTaintGroupKeys.Certificate.mustRoot,
      ApSpec.CurrentTaintGroupKeys.Certificate.exactG]⟩]
  if result.1.length != 2 || result.2 != expected then
    throw (IO.userError "Executable normalized taint-group destination failed.")
  IO.println "Executable normalized taint-group destination passed."

-- The oracle joins sink slots only within one method key.
#eval do
  if !ApSpec.CurrentSinkJoin.Certificate.check then
    throw (IO.userError "Executable sink join method isolation failed.")
  IO.println "Executable sink join method isolation passed."

-- Frontier counts accepted stored patterns, excluding duplicates and implicit zero.
#eval do
  if !ApSpec.CurrentDemandFrontier.Certificate.check then
    throw (IO.userError "Executable stored demand frontier count failed.")
  IO.println "Executable stored demand frontier count passed."

-- Exact counted-depth metadata cannot skip the cut at the former Short boundary.
#eval do
  if !ApSpec.CurrentDepthCache.workWitness then
    throw (IO.userError "Executable exact depth cache guard failed.")
  IO.println "Executable exact depth cache guard passed."

-- Root EXACT cleaning demotes the coarse link and leaves the precise descendant.
#eval do
  if !ApSpec.CurrentDemandLink.Certificate.check then
    throw (IO.userError "Executable current demand-link fixture failed.")
  IO.println "Executable current demand-link fixture passed."

-- AC4: demand identities take aliases; only normal identities skip them.
#eval do
  if !ApSpec.CurrentAliasGuard.Certificate.check then
    throw (IO.userError "Executable alias origin/layer guard failed.")
  IO.println "Executable alias origin/layer guard passed."

-- Matched TAINT literals need no second coverage traversal to select their layer.
#eval do
  let expected : List (Bool × Bool × Bool) :=
    [(true, false, false), (false, false, false), (true, false, false), (true, true, true)]
  if ApSpec.CurrentProposal.LiteralLayer.Certificate.cases != expected then
    throw (IO.userError "Executable matched literal layer failed.")
  IO.println "Executable matched literal layer passed."

-- W7 targets are uncorrelated: a full combination only joins member layers.
#eval do
  if !ApSpec.CurrentProposal.Combination.Certificate.check then
    throw (IO.userError "Executable summary combination layer join failed.")
  IO.println "Executable summary combination layer join passed."

-- T2 preserves union denotation but defeats mutual single-leaf coverage.
#eval do
  if !ApSpec.CurrentProposal.T2Oracle.Certificate.check then
    throw (IO.userError "Executable summary union comparison boundary failed.")
  IO.println "Executable summary union comparison boundary passed."

-- Demotion and a general field read do not commute. This is a local AP boundary.
#eval do
  if ApSpec.CurrentProposal.RecordTiming.constructed != [ApSpec.CurrentProposal.RecordTiming.g] ||
      ApSpec.CurrentProposal.RecordTiming.late != [] ||
      ApSpec.CurrentProposal.RecordTiming.early !=
        ([⟨⟨⟨6, [], .any, .conc 7⟩, true⟩, ApSpec.Excl.empty⟩] : List ApSpec.AnyTaintEx.XFact) then
    throw (IO.userError "Executable record demotion timing boundary failed.")
  IO.println "Executable record demotion timing boundary passed."

-- Per-leaf summary selectors keep the complete tail/exclusion guards.
#eval do
  if !ApSpec.CurrentSummaryApplication.Certificate.check then
    throw (IO.userError "Executable summary application and selector rows failed.")
  IO.println "Executable summary application and selector rows passed."

-- Ordinary root mappings commute; the F74 action chain remains outside this check.
#eval do
  if !ApSpec.CurrentRecordTiming.Certificate.check then
    throw (IO.userError "Executable root-mapping record demotion commutation failed.")
  IO.println "Executable root-mapping record demotion commutation passed."

-- Pending native results keep E through returned operations and demote before the call cut.
#eval do
  if !ApSpec.CurrentRecordTiming.LateReturn.Certificate.check then
    throw (IO.userError "Executable late-return record demotion failed.")
  IO.println "Executable late-return record demotion passed."


-- Store removal: executable question-specific consumption, actual AP guards,
-- immediate emission and a mixed-caller distinguishing witness. This observes
-- output values; it does not retain the emitted oracle relation at runtime.
#eval do
  let s := ApSpec.AddedFactsRemoval.Certificate.memoTrace
  if s.pending.length != 2 || s.emitted.length != 2 || s.questions.length != 1 then
    throw (IO.userError "Executable store-removal state certificate failed.")
  let qf := ApSpec.AddedFactsRemoval.Certificate.q [4]
  let qg := ApSpec.AddedFactsRemoval.Certificate.q [5]
  let original := ApSpec.AddedFactsRemoval.Certificate.excludedRoot
  let projected := ApSpec.AddedFactsRemoval.Certificate.projectedRoot
  let exactG := ApSpec.AddedFactsRemoval.Certificate.exactG
  if !(ApSpec.AddedFactsRemoval.checkedAnswer 3 qf exactG).isNone ||
      (ApSpec.AddedFactsRemoval.checkedAnswer 3 qg exactG).isNone ||
      !(ApSpec.AddedFactsRemoval.checkedAnswer 3 qf original).isNone ||
      (ApSpec.AddedFactsRemoval.checkedAnswer 3 qf projected).isNone then
    throw (IO.userError "Executable store-removal geometry/exclusion certificate failed.")
  let packed := ApSpec.AddedFactsRemoval.Certificate.rows
  let consumed := ApSpec.AddedFactsRemoval.consumeMark qf.1 7 packed
  if packed.length != 2 || consumed.length != 2 ||
      (ApSpec.AddedFactsRemoval.expandRows consumed).length != 2 ||
      !ApSpec.AddedFactsRemoval.Certificate.pooledLiterals ||
      ApSpec.AddedFactsRemoval.Certificate.sameCallerLiterals then
    throw (IO.userError "Executable packed memo/candidate certificate failed.")
  let expected : List ApSpec.PFact :=
    [⟨3, [], .star ApSpec.Excl.empty, .star⟩, ⟨3, [5], .exact, .conc 7⟩]
  if ApSpec.AddedFactsRemoval.restrictedEmit
      ApSpec.AddedFactsRemoval.Certificate.fixedDemand exactG.af.fact != expected ||
      ApSpec.AddedFactsRemoval.run1Admission 10 exactG.af.fact []
        [(qg, ⟨3, [5], .exact⟩)] != expected then
    throw (IO.userError "Executable immediate/additive abstract certificate failed.")
  IO.println "Executable added-fact-store elimination certificates passed."

-- F78: commit-and-replace and the executable choice of the non-missing replay arm.
#eval do
  let s := ApSpec.Run1RequestProtocol.batchVector
  if s.admitted != [3, 1, 2] || !s.temporary.isEmpty ||
      ApSpec.Run1RequestProtocol.raceArm 5 3 != ApSpec.Run1RequestProtocol.Arm.inputRead ||
      ApSpec.Run1RequestProtocol.raceArm 3 5 != ApSpec.Run1RequestProtocol.Arm.requestReplay then
    throw (IO.userError "Executable F78 request protocol certificate failed.")
  IO.println "Executable F78 request protocol certificates passed."

-- F78 static coverage: recursive calls, broad answers, legal class points,
-- and the two root-summary counterexamples repaired by the derived frame.
#eval do
  let broad : ApSpec.Run1Requests.Leaf := ⟨.root,.any,7,.empty,true⟩
  if ApSpec.Run1Requests.project (.field 3 2) broad !=
      some (ApSpec.Run1Requests.concrete (.field 3 2) (.star .empty) 7) ||
      ApSpec.Run1Requests.classAnswer 3 broad !=
      some (ApSpec.Run1Requests.concrete (.cls 3) (.star .empty) 7) then
    throw (IO.userError "F78 broad field/class projection certificate failed.")
  let anyField : ApSpec.Run1Requests.Leaf := ⟨.field 3 2,.any,7,.empty,false⟩
  let mustField : ApSpec.Run1Requests.Leaf := ⟨.field 3 2,.must,7,.empty,false⟩
  let abstractField := (ApSpec.Run1Requests.Slot.field 3 2).initial
  let concreteField := ApSpec.Run1Requests.concrete (.field 3 2) (.star .empty) 7
  if ApSpec.Run1InitialShapes.supplierStarts (.field 3 2) anyField false != [abstractField] ||
      ApSpec.Run1InitialShapes.supplierStarts (.field 3 2) anyField true !=
        [abstractField,concreteField] ||
      ApSpec.Run1InitialShapes.supplierStarts (.field 3 2) mustField false != [abstractField] ||
      ApSpec.Run1InitialShapes.supplierStarts (.field 3 2) mustField true !=
        [abstractField,concreteField] ||
      ApSpec.Run1Requests.classAnswer 3 ⟨.cls 3,.any,7,.empty,false⟩ !=
        some (ApSpec.Run1Requests.concrete (.cls 3) (.star .empty) 7) then
    throw (IO.userError "F78 no-ANY/additive concrete STAR certificates failed.")
  let classPoint : ApSpec.Run1Requests.Leaf := ⟨.cls 3,.exact,7,.empty,false⟩
  if ApSpec.Run1Requests.directClass classPoint !=
      some (ApSpec.Run1Requests.concrete (.cls 3) .exact 7) then
    throw (IO.userError "F78 exact-class direct certificate failed.")
  let f := ApSpec.Run1RequestsSafety.Counterexamples.f
  let g := ApSpec.Run1RequestsSafety.Counterexamples.g
  let target := ApSpec.Run1RequestsSafety.Counterexamples.target
  let sibling := ApSpec.Run1RequestsSafety.Counterexamples.sibling
  if ApSpec.Run1RequestsSafety.composed [f,g] [.clean f 7] target != [] ||
      ApSpec.Run1RequestsSafety.composed [f,g] [.clean f 7] sibling != [sibling] ||
      ApSpec.Run1RequestsSafety.returnedComposition false [f,g] [.identity] sibling != [] then
    throw (IO.userError "F78 root-frame/normal-return certificate failed.")
  IO.println "Executable F78 static request and derived-frame certificates passed."

-- F78 extension: shared precomputed rows never enter residual/pending state,
-- and every allowed initial has a constructor witness and the stated start.
#eval do
  let result := ApSpec.Run1PrecomputedRequests.Examples.result
  if result.1.authoritative.baseKey != some 0 ||
      result.1.authoritative.additions != [3] ||
      result.1.pending.baseKey.isSome || !result.1.pending.additions.isEmpty ||
      result.2 != [3] then
    throw (IO.userError "F78 shared precomputed request certificate failed.")
  if ApSpec.Run1InitialShapes.Certificate.samples.length != 10 ||
      !ApSpec.Run1InitialShapes.Certificate.checks then
    throw (IO.userError "F78 closed initial grammar/start-fact certificates failed.")
  IO.println "Executable F78 shared-base and ten-initial certificates passed."

-- Selected static contract: F78 certificates above retain their historical
-- ten-family/root-frame scope. The new module has nine families and no root.
#eval do
  let families := ApSpec.RequestGatedStatics.InitialGrammar.samples
  if families.length != 9 ||
      !families.all (fun s => (ApSpec.RequestGatedStatics.InitialGrammar.classify
        (ApSpec.RequestGatedStatics.InitialGrammar.encode s)).isSome) then
    throw (IO.userError "Selected nine-family static initial certificate failed.")
  let f : ApSpec.RequestGatedStatics.Field := (0,1)
  let p : ApSpec.RequestGatedStatics.Component := ⟨f,[],7,false⟩
  let input : ApSpec.Run1Requests.Leaf := ⟨.field 0 1,.any,7,.empty,false⟩
  if ApSpec.RequestGatedStatics.fieldReads
      (ApSpec.RequestGatedStatics.inventory [.containsMark (.field 0 1) 7]) != [] ||
      ApSpec.RequestGatedStatics.f1Starts [] [(.field 0 1,7)] f input !=
        [ApSpec.Run1Requests.concrete (.field 0 1) (.star .empty) 7] ||
      ApSpec.RequestGatedStatics.f1Starts [f] [] f input !=
        [(ApSpec.Run1Requests.Slot.field 0 1).initial] then
    throw (IO.userError "Independent field/mark gate certificates failed.")
  let point : ApSpec.RequestGatedStatics.Point := .fact p
  let q : ApSpec.RequestGatedStatics.Query := ⟨⟨0,0,[]⟩,2,true,point,none⟩
  if !(ApSpec.RequestGatedStatics.admitActualQuery [] [] q).contains q ||
      ApSpec.RequestGatedStatics.queryAnswers [point] .ret q != [point] ||
      ApSpec.RequestGatedStatics.queryAnswers [point] (.step (.kill f) .ret) q != [] ||
      ApSpec.RequestGatedStatics.queryAnswers [point] .abort q != [] then
    throw (IO.userError "Strong backward helper/kill/nonreturn certificates failed.")
  if ApSpec.RequestGatedStatics.strongRecordReadable 1 2 .strongLater ||
      ApSpec.RequestGatedStatics.strongRecordReadable 1 3 .weakRun1 ||
      !ApSpec.RequestGatedStatics.strongRecordReadable 2 3 .strongLater then
    throw (IO.userError "F1 quarantine/strong record certificates failed.")
  let loop : Fin 1 → ApSpec.RequestGatedStatics.BalancedCalls.Code 1 Nat :=
    ApSpec.RequestGatedStatics.BalancedCalls.loop
  if ApSpec.RequestGatedStatics.BalancedCalls.eval 8 loop (loop ⟨0,by decide⟩) 0 != [] then
    throw (IO.userError "Unproductive recursive call created a return.")
  IO.println "Executable request-gated statics and strong run-2 certificates passed."

#print axioms ApSpec.RequestGatedStatics.InitialGrammar.classification_exact
#print axioms ApSpec.RequestGatedStatics.InitialGrammar.exactly_old_grammar_minus_root
#print axioms ApSpec.RequestGatedStatics.containsMark_does_not_add_field
#print axioms ApSpec.RequestGatedStatics.strong_step_in_weak
#print axioms ApSpec.RequestGatedStatics.weak_clean_is_strong
#print axioms ApSpec.RequestGatedStatics.output_iff_actual_return
#print axioms ApSpec.RequestGatedStatics.backward_answers_exact
#print axioms ApSpec.RequestGatedStatics.BalancedCalls.balanced_query_exact
#print axioms ApSpec.RequestGatedStatics.BalancedCalls.least_closed_relation
#print axioms ApSpec.RequestGatedStatics.BalancedCalls.unproductive_recursion_has_no_return
#print axioms ApSpec.RequestGatedStatics.BalancedCalls.mixed_static_local_nested_call
#print axioms ApSpec.RequestGatedStatics.F1_record_never_executable

-- All-field F1 writes: canonical DEMAND survivors, no spatial E, distinct
-- value computations, frozen default layers and actual backward queries.
#eval do
  let broad : ApSpec.Run1WeakFieldWrites.Fact :=
    ⟨9,⟨4,[],.star .empty,.star⟩,.empty,false⟩
  let old := ApSpec.Run1WeakFieldWrites.weakKeep false (.instance 2 5 []) broad
  let disjoint : ApSpec.Run1WeakFieldWrites.Fact :=
    ⟨9,⟨4,[6],.exact,.conc 7⟩,.empty,false⟩
  if !old.demand || old.ap.kind != ApSpec.Kind.any || old.ap.path != [] ||
      old.anyEx != ApSpec.Excl.empty ||
      ApSpec.Run1WeakFieldWrites.weakKeep false (.instance 2 5 []) disjoint != disjoint then
    throw (IO.userError "Affected/disjoint weak-write layer certificates failed.")
  let value : ApSpec.Run1WeakFieldWrites.Fact :=
    ⟨10,⟨4,[5],.exact,.conc 8⟩,.empty,false⟩
  if ApSpec.Run1WeakFieldWrites.weakWrite false (.instance 2 5 []) broad [value] !=
      [old,value] then
    throw (IO.userError "New field value incorrectly inherited old-preservation debt.")
  let excludedMust : ApSpec.Run1WeakFieldWrites.Fact :=
    ⟨0,⟨4,[],.any,.conc 7⟩,.set [5],false⟩
  let cover := ApSpec.Run1WeakFieldWrites.publish excludedMust
  if !cover.demand || cover.anyEx != ApSpec.Excl.empty || cover.ap.kind != ApSpec.Kind.any ||
      cover.ap.mark != ApSpec.MarkA.conc 7 then
    throw (IO.userError "Partial must residual did not become an E-free DEMAND may cover.")
  let field : ApSpec.Run1WeakFieldWrites.Fact :=
    ⟨9,⟨1,[2,3],.star .empty,.star⟩,.empty,false⟩
  let defaultCall := ApSpec.Run1WeakFieldWrites.defaultCall ⟨true⟩ field
  if !defaultCall.continuation.demand ||
      defaultCall.continuation.ap.path != field.ap.path ||
      defaultCall.supplied != field ||
      !(ApSpec.Run1WeakFieldWrites.instantiateDefault ⟨false⟩
        defaultCall.continuation).demand ||
      ApSpec.Run1WeakFieldWrites.templateInitials ⟨true⟩ != [] then
    throw (IO.userError "Static default changed geometry, data delivery or created a root initial.")
  if !(ApSpec.Run1WeakFieldWrites.defaultFor
        ApSpec.Run1WeakFieldWrites.Controls.cycle
        ApSpec.Run1WeakFieldWrites.Controls.writes 0).mayWrite ||
      (ApSpec.Run1WeakFieldWrites.defaultFor
        ApSpec.Run1WeakFieldWrites.Controls.cycle (fun _ => false) 0).mayWrite then
    throw (IO.userError "Prescan static-write cycle closure certificate failed.")
  match ApSpec.Run1WeakFieldWrites.authorizeBackward ⟨true⟩ field with
  | none => throw (IO.userError "Default failed to authorize actual static query.")
  | some query =>
    if query.actual != field then
      throw (IO.userError "Default guide substituted wildcard-root query geometry.")
  IO.println "Executable all-field weak-write, Empty-E and static-default certificates passed."

#print axioms ApSpec.Run1WeakFieldWrites.publication_is_may_cover
#print axioms ApSpec.Run1WeakFieldWrites.normal_publication_does_not_forget_E
#print axioms ApSpec.Run1WeakFieldWrites.weakWrite_covers_strong_output
#print axioms ApSpec.Run1WeakFieldWrites.affected_keep_is_DEMAND
#print axioms ApSpec.Run1WeakFieldWrites.unaffected_keep_keeps_layer
#print axioms ApSpec.Run1WeakFieldWrites.every_producer_EEmpty
#print axioms ApSpec.Run1WeakFieldWrites.closedWrite_least
#print axioms ApSpec.Run1WeakFieldWrites.default_NORMAL_iff_no_reachable_write
#print axioms ApSpec.Run1WeakFieldWrites.default_layer_is_join
#print axioms ApSpec.Run1WeakFieldWrites.callee_delivery_is_pre_default
#print axioms ApSpec.Run1WeakFieldWrites.no_propagated_root_initial
#print axioms ApSpec.Run1WeakFieldWrites.backward_authorization_preserves_full_AP
