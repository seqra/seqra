/-
  Finite composition of grouped F1 and exact B2. The full positive program
  is the reference semantics. Its source guards, local operations, AP/call
  bindings and reached points must be compiled by the ordinary front end.
  This module does not prove that JVM compiler adapter.

  Productive pair rows below are GHOST certificates, not a new runtime
  barrier store or an old-guide veto. B2 registers an actual incoming full
  query and publishes only results of its ordinary reverse primitive.
  Static GROUP defaults add permission; they do not execute a body.
-/
import ApSpec.Run1StaticDemandTreeCoverage
import ApSpec.Run1UniversalStaticCarry

namespace ApSpec.GroupedRun1StaticComposition
set_option autoImplicit false
open Run1StaticGroupDerivation
open Run1StaticDemandTreeCoverage

inductive Site where
  | ordinary (point : Nat)
  | staticField (field : Nat)
  deriving DecidableEq, Repr

/-- The local full source adapter certifies its actual guard and action.
    A request or a conditional method initial is not a configured source. -/
structure Source where
  point : Nat
  write : Run1StaticEarlySummaryZero.SourceWrite
  guardAccepted : Bool
  deriving DecidableEq, Repr

def Source.eligible (source : Source) : Bool :=
  source.guardAccepted && source.write.sourceExecuted

def Source.fact (source : Source) : FullFact Site :=
  fullFact (.ordinary source.point) ⟨source.write.mark.group, source.write.mark.identity⟩

def seeds (sources : List Source) : List (FullFact Site) :=
  (sources.filter Source.eligible).map Source.fact

structure Program where
  sources : List Source
  rules : List (FullRule Site)
  targets : List Site
  groups : List Nat
  deriving Repr

def Program.groupRules (program : Program) := program.rules.map projectRule

def Program.groupSeeds (program : Program) := (seeds program.sources).map projectFact

def Program.solution (program : Program) :=
  solve program.groupRules program.groupSeeds

def Program.points (program : Program) :=
  freezePointDemands program.groupRules program.groupSeeds program.targets

def Program.ghostPairs (program : Program) :=
  freeze program.groupRules program.groupSeeds

def Program.defaults (program : Program) : Run1UniversalStaticCarry.FrozenDefaults := ⟨true, program.groups⟩

/-- Executable source selection. The proof cannot choose an arbitrary
    same-group source: both node and full identity must match. -/
def sourceWitness (sources : List Source) (fact : FullFact Site)
    (present : fact ∈ seeds sources) :
    { source : Source // source ∈ sources ∧ source.eligible = true ∧ source.fact = fact } :=
  match found : select sources (fun source => source.eligible = true ∧ source.fact = fact) with
  | some chosen => ⟨chosen.val, chosen.property.1, chosen.property.2⟩
  | none => False.elim (by
      obtain ⟨source, active, same⟩ := List.mem_map.mp present
      obtain ⟨registered, eligible⟩ := List.mem_filter.mp active
      exact select_none sources _ found source registered ⟨eligible, same⟩)

def scheduleMark (fact : FullFact Site) : RuleGroupSchedule.FullMark :=
  ⟨fact.group, fact.node.2⟩

/-- Source confirmation is exact. Its result is unmarked CONTROL_ZERO
    (REACH here). The query original is retained and is never Group ZERO. -/
def confirmSource (original : Run1StaticEarlySummaryZero.Original) (fact : FullFact Site)
    (source : Source) : Option Run1StaticEarlySummaryZero.Edge :=
  if source.fact = fact ∧ source.eligible = true then
    Run1StaticEarlySummaryZero.reverseActualSource original (scheduleMark fact) source.write
  else none

theorem selected_source_confirms (sources : List Source) (fact : FullFact Site)
    (present : fact ∈ seeds sources) (original : Run1StaticEarlySummaryZero.Original) :
    confirmSource original fact (sourceWitness sources fact present).val =
      some ⟨original, .reach⟩ := by
  let selected := sourceWitness sources fact present
  have executed : selected.val.write.sourceExecuted = true :=
    (Bool.and_eq_true_iff.mp selected.property.2.1).2
  have same : scheduleMark fact = selected.val.write.mark :=
    (congrArg scheduleMark selected.property.2.2).symm
  change confirmSource original fact selected.val = _
  rw [confirmSource, if_pos ⟨selected.property.2.2, selected.property.2.1⟩]
  simp only [Run1StaticEarlySummaryZero.reverseActualSource, executed, same,
    and_self, reduceIte]


/-- An actual incoming query is registered without changing its full mark.
    A session permits computed results for this same original. It does not
    compute those results and cannot create an input when no input arrived. -/
structure Session where
  original : FullFact Site
  deriving DecidableEq, Repr

def admitActual : Option (FullFact Site) → Option Session
  | none => none
  | some actual => some ⟨actual⟩

def publishActual (session : Session) (original : FullFact Site)
    (computed : Option (FullFact Site)) : Option (FullFact Site) :=
  if session.original = original then computed else none

theorem actual_admission_and_publication (query result : FullFact Site) :
    (admitActual (some query)).bind (fun session =>
      publishActual session query (some result)) = some result := by
  simp only [admitActual, Option.bind_some, publishActual, reduceIte]

theorem no_incoming_no_session : admitActual none = none := rfl

theorem no_computed_result_no_publication (session : Session) (original : FullFact Site) :
    publishActual session original none = none := by
  by_cases same : session.original = original <;> simp [publishActual, same]

/-- The ordinary reverse primitive for this finite language. A declared
    positive rule supplies its exact preimages, not all full marks in G.
    All other guards are still required by Replay.operation below. -/
def reversePremise (rule : FullRule Site) (query input : FullFact Site) :
    Option (FullFact Site) :=
  if query = rule.conclusion ∧ input ∈ rule.premises then some input else none

def executePair (rule : FullRule Site) (query input : FullFact Site) :
    Option (FullFact Site) :=
  (admitActual (some query)).bind (fun session =>
    publishActual session query (reversePremise rule query input))

theorem primitive_pair_executes (rule : FullRule Site) (input : FullFact Site)
    (present : input ∈ rule.premises) :
    executePair rule rule.conclusion input = some input := by
  simp only [executePair, admitActual, Option.bind_some, publishActual,
    reversePremise, present, and_self, reduceIte]

/-- This is an executable exact B2 proof tree. Every operation keeps all
    full guard children. A single admitted pair is not ND confirmation. -/
inductive Replay (program : Program) (original : Run1StaticEarlySummaryZero.Original) : FullFact Site → Type where
  | source (fact : FullFact Site) (root : fact ∈ seeds program.sources)
      (confirmed : confirmSource original fact
        (sourceWitness program.sources fact root).val =
          some ⟨original, .reach⟩) : Replay program original fact
  | operation (rule : FullRule Site) (declared : rule ∈ program.rules)
      (productive : rule.premises ≠ [])
      (pairs : (input : FullFact Site) → input ∈ rule.premises →
        executePair rule rule.conclusion input = some input)
      (children : (input : FullFact Site) → input ∈ rule.premises → Replay program original input) :
      Replay program original rule.conclusion

def compileReplay (program : Program) (original : Run1StaticEarlySummaryZero.Original) :
    {fact : FullFact Site} → Tree program.rules (seeds program.sources) fact → Replay program original fact
  | _, .source fact root => .source fact root
      (selected_source_confirms program.sources fact root original)
  | _, .operation rule declared productive inputs =>
      .operation rule declared productive (fun input present =>
        primitive_pair_executes rule input present)
        (fun input present => compileReplay program original (inputs input present))

/-- Frozen target permission, all productive ghost rows, and actual B2
    replay are built from one full witness. No required-guide premise. -/
structure Coverage (program : Program) (target : FullFact Site) where
  targetPermission : { permission : Fact Site // permission ∈ program.points ∧
    endpointMatches permission (endpointOf target) }
  ghost : FullSkeleton program.rules (seeds program.sources) target
  backward : Replay program (.nonzero target.node.2) target

def compileCoverage (program : Program) {target : FullFact Site}
    (tree : Tree program.rules (seeds program.sources) target)
    (observed : target.node.1 ∈ program.targets) : Coverage program target :=
  ⟨fullTreePointWitness program.rules (seeds program.sources) program.targets tree observed,
    buildFullSkeleton program.rules (seeds program.sources) tree, compileReplay program (.nonzero target.node.2) tree⟩

theorem coverage_ghost_rows_frozen (program : Program) {target : FullFact Site}
    (coverage : Coverage program target) (guide : Guide Site)
    (present : guide ∈ collectFullGuides program.rules (seeds program.sources) coverage.ghost) :
    guide ∈ program.ghostPairs :=
  collected_guides_frozen program.rules (seeds program.sources) coverage.ghost guide present

/-- A static supply row is rooted in the grouped least closure. The row
    itself also commits write presence, so it cannot bypass the read gate. -/
structure StaticPublication where
  field : Nat
  group : Nat
  deriving DecidableEq, Repr

def publications (program : Program) : List StaticPublication :=
  program.solution.facts.filterMap (fun fact => match fact.node with
    | .ordinary _ => none
    | .staticField field => some ⟨field, fact.group⟩)

def readAdmission (program : Program) (field group : Nat) (readReached : Bool) : Bool :=
  readReached && (publications program).contains ⟨field, group⟩

def publishWitness (program : Program) (field : Nat) (mark : RuleGroupMarks.OwnedMark)
    (tree : Tree program.rules (seeds program.sources) (fullFact (.staticField field) mark)) :
    { row : StaticPublication // row ∈ publications program ∧ row = ⟨field, mark.group⟩ } := by
  refine ⟨⟨field, mark.group⟩, ?_, rfl⟩
  apply List.mem_filterMap.mpr
  exact ⟨⟨.staticField field, mark.group⟩,
    solution_contains_tree program.groupRules program.groupSeeds
      (projectTree program.rules (seeds program.sources) tree), rfl⟩

theorem rooted_write_admits_read (program : Program) (field : Nat)
    (mark : RuleGroupMarks.OwnedMark)
    (tree : Tree program.rules (seeds program.sources) (fullFact (.staticField field) mark)) :
    readAdmission program field mark.group true = true := by
  simp only [readAdmission, Bool.true_and, List.contains_iff_mem]
  exact (publishWitness program field mark tree).property.1

/-- No static group can arise from read interest, a conditional initial,
    or an empty positive cycle without an actual source root. -/
theorem empty_sources_no_publication (program : Program) (absent : program.sources = []) :
    publications program = [] := by
  unfold publications Program.solution Program.groupSeeds
  rw [absent]
  change List.filterMap _ (solve program.groupRules []).facts = []
  rw [solution_empty_without_seed]
  rfl


/-- Existing finite static transport is composed with the generated handoff
    default. There is no marked S-root and no per-method field product. -/
theorem generated_static_relays (program : Program) (relays : List Run1UniversalStaticCarry.Relay)
    (query : Run1UniversalStaticCarry.StaticQuery) (catalogued : query.mark.group ∈ program.groups) :
    Run1UniversalStaticCarry.runPath program.defaults relays query = some (.staticFull (Run1UniversalStaticCarry.reference relays query)) :=
  Run1UniversalStaticCarry.every_finite_transport_path program.defaults relays query rfl catalogued

/-- Early source writes retain explicit ZERO provenance and exact output
    group. A wrong/unexecuted source guard cannot publish the writer. -/
def earlyWriter (source : Source) : Option Run1StaticEarlySummaryZero.EarlySummary :=
  if source.guardAccepted then Run1StaticEarlySummaryZero.earlySourceWrite source.write else none

def frozenWriter (source : Source) : Option Run1StaticEarlySummaryZero.WriterDemand :=
  (earlyWriter source).map Run1StaticEarlySummaryZero.writerDemand

theorem reached_source_writer (source : Source) (eligible : source.eligible = true)
    (storeReached : source.write.storeReached = true) :
    earlyWriter source = some ⟨source.write.method, source.write.source,
      source.write.field, source.write.mark.group⟩ ∧
    frozenWriter source = some ⟨source.write.method, source.write.source,
      source.write.field, source.write.mark.group, some .zero⟩ := by
  have guards := Bool.and_eq_true_iff.mp eligible
  simp only [earlyWriter, guards.1, reduceIte, Run1StaticEarlySummaryZero.earlySourceWrite, guards.2,
    storeReached, and_self, frozenWriter, Option.map_some, Run1StaticEarlySummaryZero.writerDemand]

/-- ZERO wrapper composition is actual joinedReply execution plus local
    field survival at each wrapper. It needs no nonzero wrapper F1 guide. -/
theorem real_zero_wrapper_chain (frames : Run1StaticEarlySummaryZero.Frames) (origin : Run1StaticEarlySummaryZero.SeedOrigin) (field : Nat)
    {root deep : Nat} (path : Run1StaticEarlySummaryZero.Chain frames field root deep) :
    Run1StaticEarlySummaryZero.runChain frames origin field path = some ⟨root, .zero, field, origin⟩ :=
  Run1StaticEarlySummaryZero.runChain_result frames origin field path

/-- An exact source leaf can be selected without classical choice. All
    other positive branches remain in Replay and its full guard payload. -/
structure SourceAnswer (program : Program) (original : Run1StaticEarlySummaryZero.Original) where
  source : Source
  fact : FullFact Site
  registered : source ∈ program.sources
  eligible : source.eligible = true
  same : source.fact = fact
  confirms : confirmSource original fact source = some ⟨original, .reach⟩

theorem matching_source_confirms (source : Source) (fact : FullFact Site)
    (eligible : source.eligible = true) (same : source.fact = fact)
    (original : Run1StaticEarlySummaryZero.Original) :
    confirmSource original fact source = some ⟨original, .reach⟩ := by
  have executed := (Bool.and_eq_true_iff.mp eligible).2
  have mark : scheduleMark fact = source.write.mark :=
    (congrArg scheduleMark same).symm
  rw [confirmSource, if_pos ⟨same, eligible⟩]
  simp only [Run1StaticEarlySummaryZero.reverseActualSource, executed, mark, and_self, reduceIte]

def matchingAnswer (program : Program) (original : Run1StaticEarlySummaryZero.Original)
    (fact : FullFact Site) (source : Source) (registered : source ∈ program.sources)
    (eligible : source.eligible = true) (same : source.fact = fact) : SourceAnswer program original :=
  ⟨source, fact, registered, eligible, same, matching_source_confirms source fact eligible same original⟩

/-- One taint fact can have multiple configured source/action observations.
    Enumerate every eligible exact match without duplicating that fact. -/
def matchingSources (program : Program) (original : Run1StaticEarlySummaryZero.Original)
    (fact : FullFact Site) : List (SourceAnswer program original) :=
  program.sources.attach.filterMap (fun source =>
    if active : source.val.eligible = true ∧ source.val.fact = fact then
      some (matchingAnswer program original fact source.val source.property active.1 active.2)
    else none)

theorem matching_source_is_enumerated (program : Program)
    (original : Run1StaticEarlySummaryZero.Original) (fact : FullFact Site) (source : Source)
    (registered : source ∈ program.sources) (eligible : source.eligible = true)
    (same : source.fact = fact) :
    matchingAnswer program original fact source registered eligible same ∈ matchingSources program original fact := by
  apply List.mem_filterMap.mpr
  refine ⟨⟨source, registered⟩, List.mem_attach _ _, ?_⟩
  rw [dif_pos ⟨eligible, same⟩]

def firstPremise (facts : List (FullFact Site)) (productive : facts ≠ []) :
    { fact : FullFact Site // fact ∈ facts } :=
  match facts with
  | [] => False.elim (productive rfl)
  | head :: _ => ⟨head, List.mem_cons_self⟩

def firstSource (program : Program) (original : Run1StaticEarlySummaryZero.Original) :
    {fact : FullFact Site} → Replay program original fact → SourceAnswer program original
  | _, .source fact root confirmed =>
      let chosen := sourceWitness program.sources fact root
      ⟨chosen.val, fact, chosen.property.1, chosen.property.2.1,
        chosen.property.2.2, confirmed⟩
  | _, .operation rule _ productive _ children =>
      let input := firstPremise rule.premises productive
      firstSource program original (children input.val input.property)

def compileExactSource (program : Program) {target : FullFact Site}
    (tree : Tree program.rules (seeds program.sources) target) :
    SourceAnswer program (.nonzero target.node.2) :=
  firstSource program (.nonzero target.node.2)
    (compileReplay program (.nonzero target.node.2) tree)

/-- Visit every positive premise, including guard branches. The first-source
    projection is only a convenience; it is not the complete traversal. -/
def collectAllSources (program : Program) (original : Run1StaticEarlySummaryZero.Original) :
    {fact : FullFact Site} → Replay program original fact → List (SourceAnswer program original)
  | _, .source fact _ _ => matchingSources program original fact
  | _, .operation rule _ _ _ children =>
      rule.premises.attach.flatMap (fun input =>
        collectAllSources program original (children input.val input.property))

theorem source_leaf_keeps_every_occurrence (program : Program)
    (original : Run1StaticEarlySummaryZero.Original) (fact : FullFact Site)
    (root : fact ∈ seeds program.sources)
    (confirmed : confirmSource original fact (sourceWitness program.sources fact root).val =
      some ⟨original, .reach⟩) (source : Source) (registered : source ∈ program.sources)
    (eligible : source.eligible = true) (same : source.fact = fact) :
    matchingAnswer program original fact source registered eligible same ∈
      collectAllSources program original (.source fact root confirmed) :=
  matching_source_is_enumerated program original fact source registered eligible same

theorem collected_sources_confirm (program : Program) (original : Run1StaticEarlySummaryZero.Original)
    {target : FullFact Site} (replay : Replay program original target)
    (hit : SourceAnswer program original) (_present : hit ∈ collectAllSources program original replay) :
    hit.source ∈ program.sources ∧
      confirmSource original hit.fact hit.source = some ⟨original, .reach⟩ :=
  ⟨hit.registered, hit.confirms⟩

theorem collect_visits_each_branch (program : Program) (original : Run1StaticEarlySummaryZero.Original)
    (rule : FullRule Site) (declared : rule ∈ program.rules) (productive : rule.premises ≠ [])
    (pairs : (input : FullFact Site) → input ∈ rule.premises → executePair rule rule.conclusion input = some input)
    (children : (input : FullFact Site) → input ∈ rule.premises → Replay program original input)
    (input : FullFact Site) (present : input ∈ rule.premises) (hit : SourceAnswer program original)
    (found : hit ∈ collectAllSources program original (children input present)) :
    hit ∈ collectAllSources program original (.operation rule declared productive pairs children) := by
  apply List.mem_flatMap.mpr
  exact ⟨⟨input, present⟩, List.mem_attach _ _, found⟩

/-- This path selects an actual subtree, not an unrelated same-program
    supply witness. In particular, an ND guard keeps its own full fact. -/
inductive Subtree (program : Program) :
    {target : FullFact Site} → Tree program.rules (seeds program.sources) target → FullFact Site → Type where
  | here {target : FullFact Site} (tree : Tree program.rules (seeds program.sources) target) :
      Subtree program tree target
  | child (rule : FullRule Site) (declared : rule ∈ program.rules)
      (productive : rule.premises ≠ [])
      (inputs : (input : FullFact Site) → input ∈ rule.premises → Tree program.rules (seeds program.sources) input)
      (input : FullFact Site) (present : input ∈ rule.premises) {cut : FullFact Site}
      (path : Subtree program (inputs input present) cut) :
      Subtree program (.operation rule declared productive inputs) cut

def extractSubtree (program : Program) :
    {target cut : FullFact Site} → {tree : Tree program.rules (seeds program.sources) target} →
      Subtree program tree cut → Tree program.rules (seeds program.sources) cut
  | _, _, _, .here tree => tree
  | _, _, _, .child _ _ _ _ _ _ path => extractSubtree program path

structure StaticCoverage (program : Program) (target : FullFact Site)
    (field : Nat) (mark : RuleGroupMarks.OwnedMark) where
  target : Coverage program target
  publication : { row : StaticPublication // row ∈ publications program ∧ row = ⟨field, mark.group⟩ }
  readAdmitted : readAdmission program field mark.group true = true

def compileStaticCoverage (program : Program) {target : FullFact Site}
    (tree : Tree program.rules (seeds program.sources) target)
    (observed : target.node.1 ∈ program.targets) (field : Nat) (mark : RuleGroupMarks.OwnedMark)
    (cut : Subtree program tree (fullFact (.staticField field) mark)) :
    StaticCoverage program target field mark :=
  let staticTree := extractSubtree program cut
  ⟨compileCoverage program tree observed, publishWitness program field mark staticTree,
    rooted_write_admits_read program field mark staticTree⟩

/-- The target group is sufficient to select its full query. Selection does
    not claim that every full member is a real positive program witness. -/
def targetInput (program : Program) (target : FullFact Site)
    (seedPresent : Bool) (control : Run1StaticEarlySummaryZero.Edge) : Option (FullFact Site) :=
  if seedPresent && (Run1StaticEarlySummaryZero.backwardZeroAtCall control).sinkSeeds &&
      program.points.contains (projectFact target) then some target else none

theorem fact_eq (a b : Fact Site) (node : a.node = b.node) (group : a.group = b.group) :
    a = b := by
  cases a; cases b
  cases node; cases group
  rfl

theorem target_tree_is_admitted (program : Program) {target : FullFact Site}
    (tree : Tree program.rules (seeds program.sources) target)
    (observed : target.node.1 ∈ program.targets) :
    targetInput program target true ⟨.zero, .reach⟩ = some target := by
  have permission := fullTreePointWitness program.rules (seeds program.sources)
    program.targets tree observed
  have same : permission.val = projectFact target :=
    fact_eq _ _ permission.property.2.1 permission.property.2.2
  have included : projectFact target ∈ program.points := same ▸ permission.property.1
  simp only [targetInput, Run1StaticEarlySummaryZero.backwardZeroAtCall,
    Bool.true_and, List.contains_iff_mem, included, reduceIte]

def continuePair (rule : FullRule Site) (input : FullFact Site) :
    Option (FullFact Site) → Option (FullFact Site)
  | none => none
  | some actual => executePair rule actual input

/-- The final productive source exit is explicit ZERO, outside the shared
    static-only permission. An ordinary body must first reach this source. -/
def finishSource (program : Program) (source : Source)
    (original : Run1StaticEarlySummaryZero.Original)
    (input : Option Run1UniversalStaticCarry.Value) : Option Run1StaticEarlySummaryZero.Edge :=
  if source ∈ program.sources then
    match input with
    | some (.staticFull query) =>
        (frozenWriter source).bind (fun demand =>
          if Run1StaticEarlySummaryZero.writerAdmits demand query.ap.field query.mark &&
            Run1StaticEarlySummaryZero.publishesSourceZero demand then
            confirmSource original (fullFact (.ordinary source.point)
              ⟨query.mark.group, query.mark.identity⟩) source
          else none)
    | _ => none
  else none

/-- The source writer is a real resolved call in the current root method.
    This is an actual-input route, not an inventory jump to an arbitrary
    same-group source elsewhere in the program. -/
def finishWriterCall (program : Program) (frames : Run1StaticEarlySummaryZero.Frames)
    (call : Run1StaticReadWritePath.CallPort) (root : Nat) (source : Source)
    (original : Run1StaticEarlySummaryZero.Original)
    (input : Option Run1UniversalStaticCarry.Value) : Option Run1StaticEarlySummaryZero.Edge :=
  if Run1StaticEarlySummaryZero.legal frames call ∧ call.callerBefore.method = root ∧
      call.calleeEntry.method = source.write.method then
    finishSource program source original input
  else none

/-- A registered ordinary primitive certifies actual CFG reachability and
    no hit overwrite for one field. Endpoint equality alone is insufficient. -/
structure FieldPass where
  field : Nat
  transfer : Run1StaticReadWritePath.LocalPass
  deriving DecidableEq, Repr

def fieldPassLegal (passes : List FieldPass) (field : Nat) (pass : FieldPass) : Prop :=
  pass ∈ passes ∧ pass.field = field ∧ pass.transfer.compatible = true ∧
    pass.transfer.before.method = pass.transfer.after.method ∧
    pass.transfer.before.context = pass.transfer.after.context ∧
    pass.transfer.before.region = pass.transfer.after.region

instance fieldPassLegalDecidable (passes : List FieldPass) (field : Nat) (pass : FieldPass) :
    Decidable (fieldPassLegal passes field pass) := by unfold fieldPassLegal; infer_instance

/-- This connected runner uses identity relays only. A relay step names
    its actual call and its registered callee-body pass for the same field.
    General static copies remain covered by generated_static_relays. -/
inductive RootMove where
  | localPass (pass : FieldPass)
  | relay (call : Run1StaticReadWritePath.CallPort) (body : FieldPass)
  deriving DecidableEq, Repr

def RootMove.start : RootMove → Run1StaticReadWritePath.Port
  | .localPass pass => pass.transfer.after
  | .relay call _ => call.callerAfter

def RootMove.finish : RootMove → Run1StaticReadWritePath.Port
  | .localPass pass => pass.transfer.before
  | .relay call _ => call.callerBefore

def rootMoveLegal (frames : Run1StaticEarlySummaryZero.Frames) (passes : List FieldPass)
    (field : Nat) : RootMove → Prop
  | .localPass pass => fieldPassLegal passes field pass
  | .relay call body => Run1StaticEarlySummaryZero.legal frames call ∧ call.exitReached = true ∧
      fieldPassLegal passes field body ∧ body.transfer.before = call.calleeEntry ∧
      body.transfer.after = call.calleeExit

instance rootMoveLegalDecidable (frames : Run1StaticEarlySummaryZero.Frames)
    (passes : List FieldPass) (field : Nat) (move : RootMove) :
    Decidable (rootMoveLegal frames passes field move) := by
  cases move <;> unfold rootMoveLegal <;> infer_instance

def rootRelays (moves : List RootMove) : List Run1UniversalStaticCarry.Relay :=
  moves.filterMap (fun move => match move with
    | .localPass _ => none
    | .relay call _ => some ⟨call.calleeEntry.method, .identity⟩)

def runRootCorridor (frames : Run1StaticEarlySummaryZero.Frames) (passes : List FieldPass)
    (field : Nat) (current : Run1StaticReadWritePath.Port) :
    List RootMove → Option Run1StaticReadWritePath.Port
  | [] => some current
  | move :: rest =>
      if move.start = current ∧ rootMoveLegal frames passes field move then
        runRootCorridor frames passes field move.finish rest
      else none

/-- Connected finite example runner: supported local target, ordinary
    backwards getter, ZERO wrapper replies, shared static relay execution,
    and explicit source-writer reversal. Every stage consumes its predecessor.
    Getter/body/port certificates remain ordinary semantic compiler inputs. -/
def runMixed (program : Program) (target : FullFact Site) (getter : FullRule Site)
    (staticInput : FullFact Site) (source : Source)
    (relays : List Run1UniversalStaticCarry.Relay)
    (frames : Run1StaticEarlySummaryZero.Frames)
    (origin : Run1StaticEarlySummaryZero.SeedOrigin)
    (field root deep : Nat) (path : Run1StaticEarlySummaryZero.Chain frames field root deep)
    (outerCall writerCall : Run1StaticReadWritePath.CallPort)
    (rootPasses : List FieldPass) (rootMoves : List RootMove)
    (seedPresent : Bool) : Option Run1StaticEarlySummaryZero.Edge :=
  if getter ∈ program.rules ∧ getter.premises = [staticInput] then
  (continuePair getter staticInput (targetInput program target seedPresent ⟨.zero, .reach⟩)).bind
    (fun actual => match actual.node.1 with
      | .ordinary _ => none
      | .staticField actualField =>
        if actualField = field ∧ scheduleMark actual = origin.query.mark ∧
            readAdmission program actualField actual.group true = true then
          (Run1StaticEarlySummaryZero.runChain frames origin field path).bind (fun child =>
            (Run1StaticEarlySummaryZero.joinedReply frames outerCall ⟨.zero, .reach⟩ child).bind
              (fun response =>
                let query : Run1UniversalStaticCarry.StaticQuery :=
                  ⟨⟨response.field, [], 0⟩, response.origin.query.mark⟩
                if rootRelays rootMoves = relays ∧
                    runRootCorridor frames rootPasses actualField outerCall.callerBefore rootMoves =
                      some writerCall.callerAfter then
                  finishWriterCall program frames writerCall response.method source (.nonzero target.node.2)
                    (Run1UniversalStaticCarry.runPath program.defaults relays query)
                else none))
        else none)
  else none

/-- This is a local compiler contract for group ownership. It states only
    how configured source marks and declared operation outputs are owned;
    it does not assume target coverage or the existence of demand rows. -/
structure OwnerContract (program : Program) (owner : RuleGroupMarks.OwnerRegistry) : Prop where
  sources : ∀ fact, fact ∈ seeds program.sources → owner fact.node.2 = fact.group
  outputs : ∀ rule, rule ∈ program.rules → owner rule.conclusion.node.2 = rule.conclusion.group

theorem tree_preserves_owner (program : Program) (owner : RuleGroupMarks.OwnerRegistry)
    (contract : OwnerContract program owner) {fact : FullFact Site}
    (tree : Tree program.rules (seeds program.sources) fact) :
    RuleGroupMarks.OwnerValid owner (endpointOf fact).mark := by
  cases tree with
  | source fact root => exact contract.sources fact root
  | operation rule declared _ _ => exact contract.outputs rule declared

/-- The finite semantic no-loss statement returns executable witnesses:
    generated target permission, every productive branch, an exact source,
    and the unchanged ownership of its full mark. It permits cross-group
    operations only through their declared exact full semantic rule. -/
def completeFiniteWitness (program : Program) (owner : RuleGroupMarks.OwnerRegistry)
    (contract : OwnerContract program owner) {target : FullFact Site}
    (tree : Tree program.rules (seeds program.sources) target)
    (observed : target.node.1 ∈ program.targets) :
    Coverage program target ×
      { hit : SourceAnswer program (.nonzero target.node.2) //
        RuleGroupMarks.OwnerValid owner (endpointOf hit.fact).mark } := by
  let hit := compileExactSource program tree
  have rooted : hit.fact ∈ seeds program.sources := by
    apply List.mem_map.mpr
    exact ⟨hit.source, List.mem_filter.mpr ⟨hit.registered, hit.eligible⟩, hit.same⟩
  exact ⟨compileCoverage program tree observed,
    ⟨hit, contract.sources hit.fact rooted⟩⟩

/-- The F1 loop is the executable least positive closure. It terminates
    after at most the finite number of declared grouped rules has fired.
    This bounds the model's closure phase, not the whole JVM engine. -/
theorem group_closure_firing_bound (program : Program) :
    program.solution.firings ≤ program.groupRules.length :=
  solve_firing_bound program.groupRules program.groupSeeds

namespace Controls

open Run1StaticEarlySummaryZero (Original Edge)

def source : Source := ⟨1, ⟨6, 1000, 8, ⟨7, 100⟩, true, true⟩, true⟩
def guard : Source := ⟨3, ⟨7, 1001, 9, ⟨9, 900⟩, true, false⟩, true⟩
def sourceFact : FullFact Site := source.fact
def fieldFact : FullFact Site := fullFact (.staticField 8) ⟨7, 100⟩
def target : FullFact Site := fullFact (.ordinary 2) ⟨7, 100⟩
def transformed : FullFact Site := fullFact (.ordinary 4) ⟨8, 800⟩
def write : FullRule Site := ⟨[sourceFact], fieldFact⟩
def getter : FullRule Site := ⟨[fieldFact], target⟩
def rewrite : FullRule Site := ⟨[target, guard.fact], transformed⟩
def program : Program := ⟨[source], [write, getter], [.ordinary 2], [7, 8, 9]⟩
def guarded : Program := ⟨[source, guard], [write, getter, rewrite], [.ordinary 4], [7, 8, 9]⟩

def sourceTree : Tree program.rules (seeds program.sources) sourceFact := .source _ (by decide)
def writeInputs (fact : FullFact Site) (present : fact ∈ write.premises) :
    Tree program.rules (seeds program.sources) fact := by
  have same : fact = sourceFact := by simpa [write] using present
  exact same ▸ sourceTree

def fieldTree : Tree program.rules (seeds program.sources) fieldFact :=
  .operation write (by decide) (by decide) writeInputs

def getterInputs (fact : FullFact Site) (present : fact ∈ getter.premises) :
    Tree program.rules (seeds program.sources) fact := by
  have same : fact = fieldFact := by simpa [getter] using present
  exact same ▸ fieldTree

def fullTree : Tree program.rules (seeds program.sources) target :=
  .operation getter (by decide) (by decide) getterInputs

def staticCoverage : StaticCoverage program target 8 ⟨7, 100⟩ :=
  compileStaticCoverage program fullTree (by decide) 8 ⟨7, 100⟩
    (.child getter (by decide) (by decide) getterInputs fieldFact (by decide) (.here fieldTree))

def guardedLeaf (fact : FullFact Site) (present : fact ∈ [sourceFact, guard.fact]) :
    Tree guarded.rules (seeds guarded.sources) fact := by
  if same : fact = sourceFact then exact same ▸ Tree.source sourceFact (by decide)
  else
    have rest := (List.mem_cons.mp present).resolve_left same
    have same : fact = guard.fact := by simpa using rest
    exact same ▸ Tree.source guard.fact (by decide)

def guardedFieldInputs (fact : FullFact Site) (present : fact ∈ write.premises) :
    Tree guarded.rules (seeds guarded.sources) fact :=
  guardedLeaf fact (by
    have same : fact = sourceFact := by simpa [write] using present
    exact List.mem_cons.mpr (Or.inl same))

def guardedField : Tree guarded.rules (seeds guarded.sources) fieldFact :=
  .operation write (by decide) (by decide) guardedFieldInputs

def guardedTargetInputs (fact : FullFact Site) (present : fact ∈ getter.premises) :
    Tree guarded.rules (seeds guarded.sources) fact := by
  have same : fact = fieldFact := by simpa [getter] using present
  exact same ▸ guardedField

def guardedTarget : Tree guarded.rules (seeds guarded.sources) target :=
  .operation getter (by decide) (by decide) guardedTargetInputs

def guardedRewriteInputs (fact : FullFact Site) (present : fact ∈ rewrite.premises) :
    Tree guarded.rules (seeds guarded.sources) fact := by
  if same : fact = target then exact same ▸ guardedTarget
  else
    have rest := (List.mem_cons.mp present).resolve_left same
    have same : fact = guard.fact := by simpa [rewrite] using rest
    exact same ▸ Tree.source guard.fact (by decide)

def guardedTree : Tree guarded.rules (seeds guarded.sources) transformed :=
  .operation rewrite (by decide) (by decide) guardedRewriteInputs

def secondSource : Source := { source with write := { source.write with source := 2000 } }
def duplicateSources : Program := { program with sources := [source, secondSource] }
def duplicateSourceReplay : Replay duplicateSources (.nonzero 100) sourceFact :=
  compileReplay duplicateSources (.nonzero 100) (.source sourceFact (by decide))
def duplicateSourceAnswers := collectAllSources duplicateSources (.nonzero 100) duplicateSourceReplay

def allGuardedSources := collectAllSources guarded (.nonzero 800)
  (compileReplay guarded (.nonzero 800) guardedTree)

def coverage : Coverage program target := compileCoverage program fullTree (by decide)
def exactSource := compileExactSource program fullTree

def port (method point : Nat) : Run1StaticReadWritePath.Port := ⟨method, point, [method], 5⟩
def call (caller callee : Nat) : Run1StaticReadWritePath.CallPort :=
  ⟨caller, port caller 1, port caller 2, port callee 0, port callee 9, .normal, true, true⟩
def pass (caller callee : Nat) : Run1StaticEarlySummaryZero.WrapperPass :=
  ⟨caller, 8, ⟨port caller 0, (call caller callee).callerBefore, true⟩⟩
def outerCall : Run1StaticReadWritePath.CallPort :=
  ⟨90, port 0 8, port 0 9, port 1 0, port 1 9, .normal, true, true⟩
def writerCall : Run1StaticReadWritePath.CallPort :=
  ⟨91, port 0 1, port 0 2, port 6 0, port 6 9, .normal, true, true⟩
def relayCall (identity method before after : Nat) : Run1StaticReadWritePath.CallPort :=
  ⟨identity, port 0 before, port 0 after, port method 0, port method 9, .normal, true, true⟩
def relayBody (method : Nat) : FieldPass := ⟨8, ⟨port method 0, port method 9, true⟩⟩
def rootPasses : List FieldPass := [⟨8, ⟨port 0 7, port 0 8, true⟩⟩,
  relayBody 20, ⟨8, ⟨port 0 5, port 0 6, true⟩⟩,
  relayBody 21, ⟨8, ⟨port 0 2, port 0 4, true⟩⟩]
def rootMoves : List RootMove := [.localPass ⟨8, ⟨port 0 7, port 0 8, true⟩⟩,
  .relay (relayCall 92 20 6 7) (relayBody 20), .localPass ⟨8, ⟨port 0 5, port 0 6, true⟩⟩,
  .relay (relayCall 93 21 4 5) (relayBody 21), .localPass ⟨8, ⟨port 0 2, port 0 4, true⟩⟩]
def frames : Run1StaticEarlySummaryZero.Frames :=
  ⟨[call 1 2, call 2 3, outerCall, writerCall, relayCall 92 20 6 7, relayCall 93 21 4 5], [1, 2, 90, 91, 92, 93], [pass 1 2, pass 2 3]⟩
def path : Run1StaticEarlySummaryZero.Chain frames 8 1 3 :=
  .next (call 1 2) (by decide) (pass 1 2) (by decide)
    (.next (call 2 3) (by decide) (pass 2 3) (by decide) (.done 3))
def origin : Run1StaticEarlySummaryZero.SeedOrigin :=
  ⟨port 3 6, ⟨⟨90, 5, [], [7]⟩, ⟨7, 100⟩⟩⟩
def relays : List Run1UniversalStaticCarry.Relay := [⟨20, .identity⟩, ⟨21, .identity⟩]
def lateWriterCall : Run1StaticReadWritePath.CallPort :=
  ⟨94, port 0 10, port 0 11, port 6 0, port 6 9, .normal, true, true⟩
def lateFrames : Run1StaticEarlySummaryZero.Frames :=
  { frames with calls := lateWriterCall :: frames.calls, reached := 94 :: frames.reached }
def latePath : Run1StaticEarlySummaryZero.Chain lateFrames 8 1 3 :=
  .next (call 1 2) (by decide) (pass 1 2) (by decide)
    (.next (call 2 3) (by decide) (pass 2 3) (by decide) (.done 3))
def absentPublication : Program :=
  { program with rules := [getter, ⟨[sourceFact], target⟩] }
def killedPasses : List FieldPass := rootPasses.map (fun pass =>
  if pass.transfer.before.method = 20 then
    { pass with transfer := { pass.transfer with compatible := false } }
  else pass)
def mixed := runMixed program target getter fieldFact source relays frames origin 8 1 3 path outerCall writerCall rootPasses rootMoves true

theorem mixed_example : mixed = some ⟨.nonzero 100, .reach⟩ := by
  unfold mixed runMixed
  rw [if_pos (by decide)]
  rw [target_tree_is_admitted program fullTree (by decide)]
  change (executePair getter getter.conclusion fieldFact).bind _ = _
  rw [primitive_pair_executes getter fieldFact (by decide)]
  simp only [Option.bind_some, fieldFact, fullFact]
  rw [if_pos (by exact ⟨trivial, rfl, rooted_write_admits_read program 8 ⟨7, 100⟩ fieldTree⟩)]
  change (Run1StaticEarlySummaryZero.runChain frames origin 8 path).bind _ = _
  rw [Run1StaticEarlySummaryZero.runChain_result frames origin 8 path]
  simp only [Option.bind_some]
  change (Run1StaticEarlySummaryZero.joinedReply frames outerCall ⟨.zero, .reach⟩
    ⟨outerCall.calleeEntry.method, .zero, 8, origin⟩).bind _ = _
  rw [Run1StaticEarlySummaryZero.one_chain_link_is_reply frames outerCall (by decide) origin 8]
  simp only [Option.bind_some]
  rw [if_pos (by decide)]
  change finishWriterCall program frames writerCall 0 source (.nonzero 100)
    (Run1UniversalStaticCarry.runPath program.defaults relays ⟨⟨8, [], 0⟩, ⟨7, 100⟩⟩) = _
  rw [generated_static_relays program relays ⟨⟨8, [], 0⟩, ⟨7, 100⟩⟩ (by decide)]
  rfl

def hasPoint (program : Program) (node : Site) (group : Nat) : Bool :=
  program.points.contains ⟨node, group⟩
def missingGuard := { guarded with sources := [source] }
def wrongGuard := { guarded with sources := [source, { guard with point := 99 }] }
def lateClosure := closeAt guarded.groupRules guarded.groupSeeds

def controls : List (String × Bool) := [
  ("source eligibility makes a real full seed", (seeds [source]).contains source.fact),
  ("false local source guard creates no seed", (seeds [{ source with guardAccepted := false }]).isEmpty),
  ("unexecuted source creates no seed", (seeds [{ source with write := { source.write with sourceExecuted := false } }]).isEmpty),
  ("F1 source reaches published static group", (publications program).contains ⟨8, 7⟩),
  ("published writer admits reached read", readAdmission program 8 7 true),
  ("unreached read is not admitted", !readAdmission program 8 7 false),
  ("wrong group gets no read supply", !readAdmission program 8 8 true),
  ("source and getter produce target candidate", hasPoint program (.ordinary 2) 7),
  ("target seed is admitted from generated group", decide (targetInput program target true ⟨.zero, .reach⟩ = some target)),
  ("absent supported seed does not start B2", decide (targetInput program target false ⟨.zero, .reach⟩ = none)),
  ("nonzero REACH does not start sink seed", decide (targetInput program target true ⟨.nonzero 1, .reach⟩ = none)),
  ("ghost rows are generated for getter and writer", program.ghostPairs.length == 2),
  ("all actual full queries bypass old-guide veto", decide (executePair getter target fieldFact = some fieldFact)),
  ("actual query registration preserves exact full mark", decide (admitActual (some target) = some ⟨target⟩)),
  ("no incoming query creates no session", decide (admitActual none = none)),
  ("wrong original cannot publish result", decide (publishActual ⟨target⟩ sourceFact (some fieldFact) = none)),
  ("body with no computed result publishes nothing", decide (publishActual ⟨target⟩ target none = none)),
  ("actual getter rejects wrong full member in same group", decide (executePair getter (fullFact (.ordinary 2) ⟨7, 101⟩) fieldFact = none)),
  ("exact source witness is registered source", exactSource.source == source),
  ("exact source confirmation returns unmarked control", decide (confirmSource (.nonzero 100) sourceFact exactSource.source = some ⟨.nonzero 100, .reach⟩)),
  ("same group wrong full source cannot hit", decide (confirmSource (.nonzero 100) (fullFact (.ordinary 1) ⟨7, 101⟩) source = none)),
  ("source result with nonzero original cannot create ZeroIn", !(Run1StaticEarlySummaryZero.backwardZeroAtCall ⟨.nonzero 100, .reach⟩).zeroIn),
  ("early write generates explicit ZERO exit", decide (frozenWriter source = some ⟨6, 1000, 8, 7, some .zero⟩)),
  ("unreached store generates no early writer", decide (frozenWriter { source with write := { source.write with storeReached := false } } = none)),
  ("guarded rewrite reaches new owner only with all inputs", hasPoint guarded (.ordinary 4) 8),
  ("missing ND guard produces no target", !hasPoint missingGuard (.ordinary 4) 8),
  ("same-group guard at wrong point produces no target", !hasPoint wrongGuard (.ordinary 4) 8),
  ("ordered guard slots survive projection", (projectRule rewrite).premises.length == 2),
  ("cross-owner productive pair is admitted", decide (executePair rewrite transformed target = some target)),
  ("group default cannot publish nonstatic RHS", !Run1UniversalStaticCarry.publication program.defaults 20
    ⟨⟨8, [], 0⟩, ⟨7, 100⟩⟩ (.ordinaryFull 2 ⟨7, 100⟩)),
  ("group default cannot publish CONTROL_ZERO", !Run1UniversalStaticCarry.publication program.defaults 20
    ⟨⟨8, [], 0⟩, ⟨7, 100⟩⟩ .reach),
  ("ordinary computed cross-owner result publishes", decide (publishActual ⟨transformed⟩ transformed (some target) = some target)),
  ("shared static permission does not bypass kill", decide (Run1UniversalStaticCarry.run program.defaults 20 (.clear 8) ⟨⟨8, [], 0⟩, ⟨7, 100⟩⟩ = none)),
  ("late forward join eventually enables all guards", (lateClosure 3).contains ⟨.ordinary 4, 8⟩),
  ("earlier barrier is not falsely considered final", !(lateClosure 1).contains ⟨.ordinary 4, 8⟩),
  ("connected sink getter wrappers relays setter reaches exact source", decide (mixed = some ⟨.nonzero 100, .reach⟩)),
  ("connected pipeline has no source with missing target seed", decide (runMixed program target getter fieldFact source relays frames origin 8 1 3 path outerCall writerCall rootPasses rootMoves false = none)),
  ("connected pipeline rejects an unregistered getter", decide (runMixed { program with rules := [write] } target getter fieldFact source relays frames origin 8 1 3 path outerCall writerCall rootPasses rootMoves true = none)),
  ("connected pipeline rejects an unchecked extra getter guard", decide (runMixed { program with rules := [write, ⟨[fieldFact, guard.fact], target⟩] } target ⟨[fieldFact, guard.fact], target⟩ fieldFact source relays frames origin 8 1 3 path outerCall writerCall rootPasses rootMoves true = none)),
  ("unregistered source cannot publish a hit", decide (finishSource { program with sources := [] } source (.nonzero 100) (some (.staticFull ⟨⟨8, [], 0⟩, ⟨7, 100⟩⟩)) = none)),
  ("unregistered writer call cannot jump to source", decide (finishWriterCall program frames (call 99 6) 1 source (.nonzero 100) (some (.staticFull ⟨⟨8, [], 0⟩, ⟨7, 100⟩⟩)) = none)),
  ("wrong writer target cannot reach source", decide (finishWriterCall program frames (call 1 2) 1 source (.nonzero 100) (some (.staticFull ⟨⟨8, [], 0⟩, ⟨7, 100⟩⟩)) = none)),
  ("no roots cannot create static publication", (publications { program with sources := [] }).isEmpty),
  ("no roots cannot create supported point", ({ program with sources := [] }.points).isEmpty),
  ("target permission alone cannot bypass absent static publication", hasPoint absentPublication (.ordinary 2) 7 &&
    decide (runMixed absentPublication target getter fieldFact source relays frames origin 8 1 3 path outerCall writerCall rootPasses rootMoves true = none)),
  ("registered later writer fails exact root corridor", decide (runMixed program target getter fieldFact source relays lateFrames origin 8 1 3 latePath outerCall lateWriterCall rootPasses rootMoves true = none)),
  ("killing relay body blocks root corridor", decide (runMixed program target getter fieldFact source relays frames origin 8 1 3 path outerCall writerCall killedPasses rootMoves true = none)),
  ("relay order must match actual root call corridor", decide (runMixed program target getter fieldFact source relays.reverse frames origin 8 1 3 path outerCall writerCall rootPasses rootMoves true = none)),
  ("all-source traversal retains principal and ND guard sources", allGuardedSources.map (fun hit => hit.source.write.source) == [1000, 1001]),
  ("all-source traversal keeps each exact full identity", allGuardedSources.map (fun hit => hit.fact.node.2) == [100, 900]),
  ("same exact fact retains distinct source action identities", duplicateSourceAnswers.map
    (fun hit => hit.source.write.source) == [1000, 2000]),
  ("duplicate source identities share one exact full fact", duplicateSourceAnswers.all
    (fun hit => hit.fact == sourceFact)),
  ("closed catalog blocks unrelated group relay", decide (Run1UniversalStaticCarry.runPath { program.defaults with groups := [] } relays ⟨⟨8, [], 0⟩, ⟨7, 100⟩⟩ = none))
]

end Controls
end ApSpec.GroupedRun1StaticComposition
