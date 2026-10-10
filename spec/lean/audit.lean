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
