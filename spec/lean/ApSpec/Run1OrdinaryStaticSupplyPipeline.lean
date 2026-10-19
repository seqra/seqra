/-
Connected executable compiler for a restricted ordinary language, independent
of the core module's reference-rule inclusion lemma. Primitive bodies are a
scalar mark-preserving static setter, a two-position guarded GEN writer, a
static FLOW copy, a getter and a sink. Any finite number of ordinary wrappers
is compiled by actually applying the child summary to complete symbolic
caller bindings. Real source inputs then use applyCall; pool copies use
staticApply. No supplied list of already-emitted wrapper/static rules is an
argument of the execution functions or the connected coverage theorems.

The reference language has one certified scalar AP cell per slot. Wider
AP geometry is handled by the separate local cell/image laws in the core
module; Kotlin/JVM compilation and queue scheduling remain adapter duties.
-/
import ApSpec.Run1OrdinaryStaticSupply

namespace ApSpec.Run1OrdinaryStaticSupplyPipeline
set_option autoImplicit false
open Run1OrdinaryStaticSupply


-- These declarations use only the single represented cell of this language.
def parameter (base : Nat) (mark : Run1OrdinaryStaticSupply.Mark) : Initial := ⟨.base base, [0], mark⟩
def staticParameter (field : Nat) (mark : Run1OrdinaryStaticSupply.Mark) : Initial := ⟨.staticField field, [0], mark⟩

def setter (owner field : Nat) : Summary :=
  ⟨owner, owner, true, [parameter 0 .flow], field, .flow [(0,0)] []⟩

def guardedWriter (owner field first second output : Nat) : Summary :=
  ⟨owner, owner, true, [parameter 0 (.group first),parameter 1 (.group second)],
    field, .generate output [0]⟩

def wrapperPort (parent child : Nat) : Port := ⟨parent, child, 10, 3, 7, 1⟩

def symbolicRows (parent : Nat) (child : Summary) : List Row :=
  child.premises.map (fun initial =>
    ⟨wrapperPort parent child.owner, [⟨parent, initial⟩],
      ⟨initial.slot, 0, initial.mark, []⟩⟩)

/-- Only the computed static effect is republished. Its complete caller
    original list becomes the next ordinary summary premise list. -/
def republish (outcome : Outcome) : Option Summary :=
  match outcome.value.slot, outcome.value.mark with
  | .staticField field, .flow =>
      some ⟨outcome.owner, outcome.owner, true, outcome.originals.map Original.initial,
        field, .flow [(0,outcome.value.cell)] outcome.value.excluded⟩
  | .staticField field, .group group =>
      some ⟨outcome.owner, outcome.owner, true, outcome.originals.map Original.initial,
        field, .generate group [outcome.value.cell]⟩
  | _, _ => none

def publishWrapper (parent : Nat) (child : Summary) : List Summary :=
  (applyCall child (wrapperPort parent child.owner) (symbolicRows parent child)).filterMap republish

theorem wrapper_setter_compiles (parent child field : Nat) :
    publishWrapper parent (setter child field) = [setter parent field] := by
  simp [publishWrapper, symbolicRows, setter, parameter, applyCall, callReady, bodyLegal,
    rowsAligned, align, valueMatches, markMatches, evalBody, flowPermitted,
    originalsOf, wrapperPort, republish, List.eraseDups_cons]

theorem wrapper_guarded_compiles (parent child field first second output : Nat) :
    publishWrapper parent (guardedWriter child field first second output) =
      [guardedWriter parent field first second output] := by
  simp [publishWrapper, symbolicRows, guardedWriter, parameter, applyCall, callReady,
    bodyLegal, rowsAligned, align, valueMatches, markMatches, evalBody,
    originalsOf, wrapperPort, republish, List.eraseDups_cons]

def setterChain : Nat → Nat → Nat → List Summary
  | 0, owner, field => [setter owner field]
  | count + 1, owner, field =>
      (setterChain count (owner + 1) field).flatMap (publishWrapper owner)

def guardedChain : Nat → Nat → Nat → Nat → Nat → Nat → List Summary
  | 0, owner, field, first, second, output => [guardedWriter owner field first second output]
  | count + 1, owner, field, first, second, output =>
      (guardedChain count (owner + 1) field first second output).flatMap (publishWrapper owner)

theorem setter_chain_emits (count owner field : Nat) :
    setterChain count owner field = [setter owner field] := by
  induction count generalizing owner with
  | zero => rfl
  | succ count ih => simp [setterChain, ih, wrapper_setter_compiles]

theorem guarded_chain_emits (count owner field first second output : Nat) :
    guardedChain count owner field first second output =
      [guardedWriter owner field first second output] := by
  induction count generalizing owner with
  | zero => rfl
  | succ count ih => simp [guardedChain, ih, wrapper_guarded_compiles]

def rootPort : Port := ⟨0,1,20,3,9,1⟩

def actualRow (base group : Nat) : Row :=
  ⟨rootPort, [], ⟨.base base,0,.group group,[]⟩⟩

def runSetter (count field group : Nat) (sourceExecuted : Bool) : List Outcome :=
  if sourceExecuted then
    (setterChain count 1 field).flatMap (fun summary => applyCall summary rootPort [actualRow 0 group])
  else []

def runGuarded (count field first second output : Nat) (rows : List Row) : List Outcome :=
  (guardedChain count 1 field first second output).flatMap (fun summary => applyCall summary rootPort rows)

theorem setter_execution (count field group : Nat) :
    runSetter count field group true = [⟨0,[],⟨.staticField field,0,.group group,[]⟩⟩] := by
  simp [runSetter, setter_chain_emits, setter, rootPort, actualRow, applyCall, callReady,
    bodyLegal, parameter, rowsAligned, align, valueMatches, markMatches, evalBody,
    flowPermitted, originalsOf]

theorem guarded_execution (count field first second output : Nat) :
    runGuarded count field first second output [actualRow 0 first, actualRow 1 second] =
      [⟨0,[],⟨.staticField field,0,.group output,[]⟩⟩] := by
  simp [runGuarded, guarded_chain_emits, guardedWriter, actualRow, rootPort,
    applyCall, callReady, bodyLegal, parameter, rowsAligned, align, valueMatches,
    markMatches, evalBody, originalsOf]

theorem guarded_missing_member (count field first second output : Nat) :
    runGuarded count field first second output [actualRow 0 first] = [] := by
  simp [runGuarded, guarded_chain_emits, guardedWriter, actualRow, rootPort,
    applyCall, callReady, bodyLegal, parameter, rowsAligned, align, valueMatches,
    markMatches]

def zeroValues (outcomes : List Outcome) : List Value :=
  (outcomes.filter (fun outcome => outcome.originals.isEmpty)).map Outcome.value

def copySummary (origin target : Nat) : Summary :=
  ⟨2,2,true,[staticParameter origin .flow],target,.flow [(0,0)] []⟩

def copyPool (origin target : Nat) (pool : List Value) : List Value :=
  pool.flatMap (fun input => staticApply (copySummary origin target) [input])

def getter (field : Nat) (readReached : Bool) (writes : List Nat) (pool : List Value) : List Value :=
  if readReached && writes.contains field then
    (pool.filter (fun value => decide (value.slot = .staticField field) && staticGroup value)).map
      (fun value => ⟨.base 3,value.cell,value.mark,[]⟩)
  else []

def sink (group : Nat) (inputs : List Value) : List Nat :=
  (inputs.filter (fun value => valueMatches (parameter 3 (.group group)) value)).map
    (fun _ => group)

structure Execution where
  written : List Value
  copied : List Value
  getterValues : List Value
  observed : List Nat
  requested : List BaseRequest
  deriving DecidableEq, Repr

def execute (count field copiedField group : Nat) (sourceExecuted : Bool) : Execution :=
  let written := zeroValues (runSetter count field group sourceExecuted)
  let copied := copyPool field copiedField written
  let read := getter copiedField true [field,copiedField] copied
  let need : Interest := ⟨field,[0],group⟩
  let requests := baseRequests ⟨setterChain count 1 field,[need]⟩
  ⟨written,copied,read,sink group read,requests⟩

theorem copy_execution (origin target group : Nat) :
    copyPool origin target [⟨.staticField origin,0,.group group,[]⟩] =
      [⟨.staticField target,0,.group group,[]⟩] := by
  simp [copyPool, staticApply, copySummary, staticParameter, bodyLegal, allStatic,
    staticGroup, align, valueMatches, markMatches, evalBody, flowPermitted]

theorem execute_connected (count field copiedField group : Nat) :
    execute count field copiedField group true =
      ⟨[⟨.staticField field,0,.group group,[]⟩],
       [⟨.staticField copiedField,0,.group group,[]⟩],
       [⟨.base 3,0,.group group,[]⟩],[group],[⟨1,0,group⟩]⟩ := by
  simp [execute, setter_execution, zeroValues, copy_execution, getter, staticGroup,
    sink, parameter, valueMatches, markMatches, setter_chain_emits, baseRequests,
    writerRequest, setter, List.eraseDups_cons]

theorem execute_no_source (count field copiedField group : Nat) :
    (execute count field copiedField group false).written = [] ∧
      (execute count field copiedField group false).copied = [] ∧
      (execute count field copiedField group false).observed = [] := by
  simp [execute, runSetter, zeroValues, copyPool, getter, sink]

/-- The reference program is fixed by language syntax, not by successful
    optimized outputs. A method body store/copy/read is present even if a
    compiler were to forget to emit its required summary. -/
def source (group full : Nat) (sourceExecuted : Bool) : GroupedRun1StaticComposition.Source :=
  ⟨0,⟨0,1000,0,⟨group,full⟩,true,false⟩,sourceExecuted⟩

def fullFact (site : GroupedRun1StaticComposition.Site) (group full : Nat) :
    Run1StaticDemandTreeCoverage.FullFact GroupedRun1StaticComposition.Site :=
  Run1StaticDemandTreeCoverage.fullFact site ⟨group,full⟩

def reference (field copiedField group full : Nat) (sourceExecuted : Bool) :
    GroupedRun1StaticComposition.Program :=
  { sources := [source group full sourceExecuted]
    rules :=
      [⟨[fullFact (.ordinary 0) group full], fullFact (.staticField field) group full⟩,
       ⟨[fullFact (.staticField field) group full], fullFact (.staticField copiedField) group full⟩,
       ⟨[fullFact (.staticField copiedField) group full], fullFact (.ordinary 2) group full⟩,
       ⟨[fullFact (.ordinary 2) group full], fullFact (.ordinary 3) group full⟩]
    targets := [.ordinary 3]
    groups := [group] }

def referenceTarget (group full : Nat) := fullFact (.ordinary 3) group full

theorem reference_witness_requires_source (field copiedField group full : Nat)
    (sourceExecuted : Bool)
    (witness : Run1StaticGroupDerivation.Tree (reference field copiedField group full sourceExecuted).rules
      (GroupedRun1StaticComposition.seeds (reference field copiedField group full sourceExecuted).sources)
      (referenceTarget group full)) : sourceExecuted = true := by
  cases sourceExecuted with
  | true => rfl
  | false =>
      have root := Run1StaticGroupDerivation.rootWitness _ _
        (Run1StaticGroupDerivation.boundTree _ _ witness).snd
      have empty : GroupedRun1StaticComposition.seeds
          (reference field copiedField group full false).sources = [] := by
        simp [reference, source, GroupedRun1StaticComposition.seeds,
          GroupedRun1StaticComposition.Source.eligible]
      rw [empty] at root
      exact False.elim (List.not_mem_nil root.property)

structure ConnectedCoverage (count field copiedField group full : Nat) (sourceExecuted : Bool) where
  setterPublished : setterChain count 1 field = [setter 1 field]
  actualWriter : (execute count field copiedField group sourceExecuted).written =
    [⟨.staticField field,0,.group group,[]⟩]
  actualCopy : (execute count field copiedField group sourceExecuted).copied =
    [⟨.staticField copiedField,0,.group group,[]⟩]
  actualDemand : group ∈ (execute count field copiedField group sourceExecuted).observed
  bootstrapRequest : BaseRequest.mk 1 0 group ∈
    (execute count field copiedField group sourceExecuted).requested
  backward : GroupedRun1StaticComposition.Replay (reference field copiedField group full sourceExecuted)
    (.nonzero full) (referenceTarget group full)

/-- Connected no-loss theorem: an independent full-language witness yields
    computed F1 writer/copy/demand and exact B2 replay. No summary-inventory,
    already-generated demand, or target-guide premise appears. -/
def compileConnected (count field copiedField group full : Nat) (sourceExecuted : Bool)
    (witness : Run1StaticGroupDerivation.Tree (reference field copiedField group full sourceExecuted).rules
      (GroupedRun1StaticComposition.seeds (reference field copiedField group full sourceExecuted).sources)
      (referenceTarget group full)) : ConnectedCoverage count field copiedField group full sourceExecuted := by
  have active := reference_witness_requires_source field copiedField group full sourceExecuted witness
  refine ⟨setter_chain_emits count 1 field, ?_, ?_, ?_, ?_,
    GroupedRun1StaticComposition.compileReplay _ (.nonzero full) witness⟩
  all_goals rw [active, execute_connected]
  all_goals simp

/-- Constructor for a real declared singleton operation, with executable child. -/
def unaryStep {Node : Type} [DecidableEq Node]
    (rules : List (Run1StaticGroupDerivation.Rule Node))
    (seeds : List (Run1StaticGroupDerivation.Fact Node))
    (input output : Run1StaticGroupDerivation.Fact Node)
    (declared : (⟨[input],output⟩ : Run1StaticGroupDerivation.Rule Node) ∈ rules)
    (child : Run1StaticGroupDerivation.Tree rules seeds input) :
    Run1StaticGroupDerivation.Tree rules seeds output :=
  .operation ⟨[input],output⟩ declared (by simp)
    (fun actual present => (List.mem_singleton.mp present).symm ▸ child)

/-- The successful full-language witness is itself an executable constructor. -/
def fullReferenceWitness (field copiedField group full : Nat) :
    Run1StaticGroupDerivation.Tree (reference field copiedField group full true).rules
      (GroupedRun1StaticComposition.seeds (reference field copiedField group full true).sources)
      (referenceTarget group full) := by
  let rules := (reference field copiedField group full true).rules
  let roots := GroupedRun1StaticComposition.seeds (reference field copiedField group full true).sources
  let input := fullFact (.ordinary 0) group full
  let written := fullFact (.staticField field) group full
  let copied := fullFact (.staticField copiedField) group full
  let read := fullFact (.ordinary 2) group full
  let target := referenceTarget group full
  have present : input ∈ roots := by
    simp [roots, input, reference, source, fullFact, GroupedRun1StaticComposition.seeds,
      GroupedRun1StaticComposition.Source.eligible, GroupedRun1StaticComposition.Source.fact,
      Run1StaticDemandTreeCoverage.fullFact]
  let first : Run1StaticGroupDerivation.Tree rules roots input := .source input present
  let second := unaryStep rules roots input written (by simp [rules, reference, input, written]) first
  let third := unaryStep rules roots written copied (by simp [rules, reference, written, copied]) second
  let fourth := unaryStep rules roots copied read (by simp [rules, reference, copied, read]) third
  exact unaryStep rules roots read target (by simp [rules, reference, read, target, referenceTarget]) fourth

def sourceAt (point group full : Nat) (active : Bool) : GroupedRun1StaticComposition.Source :=
  ⟨point,⟨0,1000+point,0,⟨group,full⟩,true,false⟩,active⟩

def ndReference (field copiedField first firstFull second secondFull output outputFull : Nat)
    (firstActive secondActive : Bool) : GroupedRun1StaticComposition.Program :=
  { sources := [sourceAt 0 first firstFull firstActive,sourceAt 1 second secondFull secondActive]
    rules :=
      [⟨[fullFact (.ordinary 0) first firstFull,fullFact (.ordinary 1) second secondFull],
          fullFact (.staticField field) output outputFull⟩,
       ⟨[fullFact (.staticField field) output outputFull],fullFact (.staticField copiedField) output outputFull⟩,
       ⟨[fullFact (.staticField copiedField) output outputFull],fullFact (.ordinary 2) output outputFull⟩,
       ⟨[fullFact (.ordinary 2) output outputFull],fullFact (.ordinary 3) output outputFull⟩]
    targets := [.ordinary 3]
    groups := [first,second,output] }

def fullNdWitness (field copiedField first firstFull second secondFull output outputFull : Nat) :
    Run1StaticGroupDerivation.Tree
      (ndReference field copiedField first firstFull second secondFull output outputFull true true).rules
      (GroupedRun1StaticComposition.seeds
        (ndReference field copiedField first firstFull second secondFull output outputFull true true).sources)
      (referenceTarget output outputFull) := by
  let program := ndReference field copiedField first firstFull second secondFull output outputFull true true
  let roots := GroupedRun1StaticComposition.seeds program.sources
  let a := fullFact (.ordinary 0) first firstFull
  let b := fullFact (.ordinary 1) second secondFull
  let written := fullFact (.staticField field) output outputFull
  let copied := fullFact (.staticField copiedField) output outputFull
  let read := fullFact (.ordinary 2) output outputFull
  have ra : a ∈ roots := by
    simp [roots, program, ndReference, sourceAt, a, GroupedRun1StaticComposition.seeds,
      GroupedRun1StaticComposition.Source.eligible, GroupedRun1StaticComposition.Source.fact,
      fullFact, Run1StaticDemandTreeCoverage.fullFact]
  have rb : b ∈ roots := by
    simp [roots, program, ndReference, sourceAt, b, GroupedRun1StaticComposition.seeds,
      GroupedRun1StaticComposition.Source.eligible, GroupedRun1StaticComposition.Source.fact,
      fullFact, Run1StaticDemandTreeCoverage.fullFact]
  let writerTree : Run1StaticGroupDerivation.Tree program.rules roots written :=
    .operation ⟨[a,b],written⟩ (by simp [program, ndReference, a, b, written]) (by simp)
      (fun actual present => by
        if same : actual = a then exact same.symm ▸ .source a ra
        else
          have rest : actual = b := by simpa [same] using present
          exact rest.symm ▸ .source b rb)
  let copyTree := unaryStep program.rules roots written copied
    (by simp [program, ndReference, written, copied]) writerTree
  let getterTree := unaryStep program.rules roots copied read
    (by simp [program, ndReference, copied, read]) copyTree
  exact unaryStep program.rules roots read (referenceTarget output outputFull)
    (by simp [program, ndReference, read, referenceTarget]) getterTree

/-- A one-position mask is not a complete ND binding. The invariant is over
    exact source sites; it is not a method-wide set of mark groups. -/
theorem one_input_cannot_enable_nd
    (field copiedField first firstFull second secondFull output outputFull : Nat)
    (firstActive secondActive : Bool) (point knownGroup knownFull : Nat)
    (sourcePosition : point = 0 ∨ point = 1)
    (onlyOne : ∀ fact, fact ∈ GroupedRun1StaticComposition.seeds
      (ndReference field copiedField first firstFull second secondFull output outputFull firstActive secondActive).sources →
      fact ∈ [fullFact (.ordinary point) knownGroup knownFull]) :
    ¬ Nonempty (Run1StaticGroupDerivation.Tree
      (ndReference field copiedField first firstFull second secondFull output outputFull firstActive secondActive).rules
      (GroupedRun1StaticComposition.seeds
        (ndReference field copiedField first firstFull second secondFull output outputFull firstActive secondActive).sources)
      (referenceTarget output outputFull)) := by
  let program := ndReference field copiedField first firstFull second secondFull output outputFull firstActive secondActive
  let known := [fullFact (.ordinary point) knownGroup knownFull]
  have stable : Run1StaticGroupDerivation.Stable program.rules known := by
    intro rule registered active
    have casesRule : rule = ⟨[fullFact (.ordinary 0) first firstFull,fullFact (.ordinary 1) second secondFull],
          fullFact (.staticField field) output outputFull⟩ ∨
        rule = ⟨[fullFact (.staticField field) output outputFull],fullFact (.staticField copiedField) output outputFull⟩ ∨
        rule = ⟨[fullFact (.staticField copiedField) output outputFull],fullFact (.ordinary 2) output outputFull⟩ ∨
        rule = ⟨[fullFact (.ordinary 2) output outputFull],fullFact (.ordinary 3) output outputFull⟩ := by
      simpa [program, ndReference] using registered
    rcases casesRule with rfl | rfl | rfl | rfl
    · rcases sourcePosition with rfl | rfl
      · have impossible := active.2 (fullFact (.ordinary 1) second secondFull) (by simp)
        simp [known, fullFact, Run1StaticDemandTreeCoverage.fullFact] at impossible
      · have impossible := active.2 (fullFact (.ordinary 0) first firstFull) (by simp)
        simp [known, fullFact, Run1StaticDemandTreeCoverage.fullFact] at impossible
    · have impossible := active.2 (fullFact (.staticField field) output outputFull) (by simp)
      simp [known, fullFact, Run1StaticDemandTreeCoverage.fullFact] at impossible
    · have impossible := active.2 (fullFact (.staticField copiedField) output outputFull) (by simp)
      simp [known, fullFact, Run1StaticDemandTreeCoverage.fullFact] at impossible
    · have impossible := active.2 (fullFact (.ordinary 2) output outputFull) (by simp)
      rcases sourcePosition with rfl | rfl <;>
        simp [known, fullFact, Run1StaticDemandTreeCoverage.fullFact] at impossible
  rintro ⟨tree⟩
  have bounded := Run1StaticGroupDerivation.boundTree _ _ tree
  have member := Run1StaticGroupDerivation.derivation_in_closure _ _ bounded.snd
  have forbidden := Run1StaticGroupDerivation.closure_least program.rules
    (GroupedRun1StaticComposition.seeds program.sources) known onlyOne stable bounded.fst _ member
  rcases sourcePosition with rfl | rfl <;>
    simp [known, referenceTarget, fullFact, Run1StaticDemandTreeCoverage.fullFact] at forbidden

theorem nd_witness_requires_both_sources
    (field copiedField first firstFull second secondFull output outputFull : Nat)
    (firstActive secondActive : Bool)
    (tree : Run1StaticGroupDerivation.Tree
      (ndReference field copiedField first firstFull second secondFull output outputFull firstActive secondActive).rules
      (GroupedRun1StaticComposition.seeds
        (ndReference field copiedField first firstFull second secondFull output outputFull firstActive secondActive).sources)
      (referenceTarget output outputFull)) : firstActive = true ∧ secondActive = true := by
  constructor
  · cases firstActive with
    | true => rfl
    | false =>
        apply False.elim
        apply one_input_cannot_enable_nd field copiedField first firstFull second secondFull output outputFull
          false secondActive 1 second secondFull (Or.inr rfl) _ ⟨tree⟩
        intro fact present
        cases secondActive <;>
          simpa [ndReference, sourceAt, GroupedRun1StaticComposition.seeds,
            GroupedRun1StaticComposition.Source.eligible, GroupedRun1StaticComposition.Source.fact,
            fullFact, Run1StaticDemandTreeCoverage.fullFact] using present
  · cases secondActive with
    | true => rfl
    | false =>
        apply False.elim
        apply one_input_cannot_enable_nd field copiedField first firstFull second secondFull output outputFull
          firstActive false 0 first firstFull (Or.inl rfl) _ ⟨tree⟩
        intro fact present
        cases firstActive <;>
          simpa [ndReference, sourceAt, GroupedRun1StaticComposition.seeds,
            GroupedRun1StaticComposition.Source.eligible, GroupedRun1StaticComposition.Source.fact,
            fullFact, Run1StaticDemandTreeCoverage.fullFact] using present

def executeNd (count field copiedField first second output : Nat)
    (firstActive secondActive : Bool) : Execution :=
  let written := if firstActive && secondActive then
    zeroValues (runGuarded count field first second output [actualRow 0 first,actualRow 1 second]) else []
  let copied := copyPool field copiedField written
  let read := getter copiedField true [field,copiedField] copied
  ⟨written,copied,read,sink output read,[]⟩

theorem execute_nd_connected (count field copiedField first second output : Nat) :
    executeNd count field copiedField first second output true true =
      ⟨[⟨.staticField field,0,.group output,[]⟩],
       [⟨.staticField copiedField,0,.group output,[]⟩],
       [⟨.base 3,0,.group output,[]⟩],[output],[]⟩ := by
  simp [executeNd, guarded_execution, zeroValues, copy_execution, getter,
    staticGroup, sink, parameter, valueMatches, markMatches]

/-- All source children of a declared ND GEN stay required in the independent
    full reference. The computed grouped pipeline keeps both call slots and
    emits the declared output group, not either input group. -/
def compileNdConnected (count field copiedField first firstFull second secondFull output outputFull : Nat)
    (firstActive secondActive : Bool)
    (tree : Run1StaticGroupDerivation.Tree
      (ndReference field copiedField first firstFull second secondFull output outputFull firstActive secondActive).rules
      (GroupedRun1StaticComposition.seeds
        (ndReference field copiedField first firstFull second secondFull output outputFull firstActive secondActive).sources)
      (referenceTarget output outputFull)) :
    { seen : Nat // seen = output ∧ seen ∈
      (executeNd count field copiedField first second output firstActive secondActive).observed } ×
    GroupedRun1StaticComposition.Replay
      (ndReference field copiedField first firstFull second secondFull output outputFull firstActive secondActive)
      (.nonzero outputFull) (referenceTarget output outputFull) := by
  have active := nd_witness_requires_both_sources field copiedField first firstFull second secondFull
    output outputFull firstActive secondActive tree
  refine ⟨⟨output, rfl, ?_⟩, GroupedRun1StaticComposition.compileReplay _ (.nonzero outputFull) tree⟩
  rw [active.1, active.2, execute_nd_connected]
  simp

/-- A complete ordinary binding is kept as one tuple. This reference protocol
    never flattens children from unrelated tuples or ports. Individual N2 row
    assembly is governed by the core's complete arity/full-port laws. -/
structure CallJoin where
  summaries : List Summary := []
  bindings : List (List Row) := []
  deriving DecidableEq, Repr

def publicationArrives (state : CallJoin) (summary : Summary) : CallJoin :=
  if summary ∈ state.summaries then state
  else { state with summaries := summary :: state.summaries }

def bindingArrives (state : CallJoin) (binding : List Row) : CallJoin :=
  if binding ∈ state.bindings then state
  else { state with bindings := binding :: state.bindings }

def joined (port : Port) (state : CallJoin) : List Outcome :=
  (state.summaries.flatMap (fun summary =>
    state.bindings.flatMap (applyCall summary port))).eraseDups

theorem join_both_arrival_orders (state : CallJoin) (summary : Summary)
    (port : Port) (binding : List Row) :
    joined port (bindingArrives (publicationArrives state summary) binding) =
      joined port (publicationArrives (bindingArrives state binding) summary) := by
  by_cases hs : summary ∈ state.summaries <;> by_cases hb : binding ∈ state.bindings <;>
    simp [bindingArrives, publicationArrives, hs, hb]

theorem repeated_publication_merges (state : CallJoin) (summary : Summary) :
    publicationArrives (publicationArrives state summary) summary = publicationArrives state summary := by
  by_cases h : summary ∈ state.summaries <;> simp [publicationArrives, h]

theorem repeated_binding_merges (state : CallJoin) (binding : List Row) :
    bindingArrives (bindingArrives state binding) binding = bindingArrives state binding := by
  by_cases h : binding ∈ state.bindings <;> simp [bindingArrives, h]

theorem joined_application_complete (port : Port) (state : CallJoin) (summary : Summary)
    (binding : List Row) (outcome : Outcome)
    (produced : outcome ∈ applyCall summary port binding) :
    outcome ∈ joined port (bindingArrives (publicationArrives state summary) binding) := by
  simp only [joined, List.mem_eraseDups]
  apply List.mem_flatMap.mpr
  refine ⟨summary, ?_, List.mem_flatMap.mpr ⟨binding, ?_, produced⟩⟩ <;>
    by_cases hs : summary ∈ state.summaries <;> by_cases hb : binding ∈ state.bindings <;>
    simp [bindingArrives, publicationArrives, hs, hb]

/-- Unlike fixed GEN, a marked TAINT copy uses the actual input AP image. -/
def markedCopySummary (origin target group fromCell toCell : Nat) : Summary :=
  ⟨2,2,true,[⟨.staticField origin,[fromCell],.group group⟩],target,
    .marked group [([fromCell],toCell)]⟩

theorem marked_copy_execution (origin target group fromCell toCell : Nat) :
    staticApply (markedCopySummary origin target group fromCell toCell)
      [⟨.staticField origin,fromCell,.group group,[]⟩] =
      [⟨.staticField target,toCell,.group group,[]⟩] := by
  simp [staticApply, markedCopySummary, bodyLegal, allStatic, staticGroup,
    align, valueMatches, markMatches, evalBody]

def markedCopyPool (origin target group : Nat) (pool : List Value) : List Value :=
  pool.flatMap (fun input => staticApply (markedCopySummary origin target group 0 0) [input])

def executeMarked (count field copiedField group : Nat) (active : Bool) : Execution :=
  let written := zeroValues (runSetter count field group active)
  let copied := markedCopyPool field copiedField group written
  let read := getter copiedField true [field,copiedField] copied
  ⟨written,copied,read,sink group read,
    baseRequests ⟨setterChain count 1 field,[⟨field,[0],group⟩]⟩⟩

theorem execute_marked_connected (count field copiedField group : Nat) :
    executeMarked count field copiedField group true =
      ⟨[⟨.staticField field,0,.group group,[]⟩],
       [⟨.staticField copiedField,0,.group group,[]⟩],
       [⟨.base 3,0,.group group,[]⟩],[group],[⟨1,0,group⟩]⟩ := by
  simp [executeMarked, setter_execution, zeroValues, markedCopyPool, marked_copy_execution,
    getter, staticGroup, sink, parameter, valueMatches, markMatches,
    setter_chain_emits, baseRequests, writerRequest, setter, List.eraseDups_cons]

/-- The same independent source/full witness is covered when static copy uses
    the marked relation path instead of polymorphic FLOW transport. -/
def compileMarkedConnected (count field copiedField group full : Nat) (active : Bool)
    (tree : Run1StaticGroupDerivation.Tree (reference field copiedField group full active).rules
      (GroupedRun1StaticComposition.seeds (reference field copiedField group full active).sources)
      (referenceTarget group full)) :
    { seen : Nat // seen = group ∧ seen ∈ (executeMarked count field copiedField group active).observed } ×
    GroupedRun1StaticComposition.Replay (reference field copiedField group full active)
      (.nonzero full) (referenceTarget group full) := by
  have executed := reference_witness_requires_source field copiedField group full active tree
  refine ⟨⟨group,rfl,?_⟩,GroupedRun1StaticComposition.compileReplay _ (.nonzero full) tree⟩
  rw [executed, execute_marked_connected]
  simp

/-- Only an actual matching Base answer becomes a marked call input. The
    request itself is not an input value, source event or static pool member. -/
def requestedRows (requests : List BaseRequest) (actual : List Row) : List Row :=
  (actual.filter (fun row => requests.any (fun request => (answer request row).isSome))).eraseDups

theorem requested_rows_are_actual (requests : List BaseRequest) (actual : List Row)
    (row : Row) (selected : row ∈ requestedRows requests actual) : row ∈ actual := by
  exact (List.mem_filter.mp (List.mem_eraseDups.mp selected)).1

def fieldsWritten (values : List Value) : List Nat :=
  (values.filterMap (fun value => match value.slot with
    | .staticField field => some field | .base _ => none)).eraseDups

def executeRequested (count field copiedField group : Nat) (active : Bool) : Execution :=
  let summaries := setterChain count 1 field
  let requests := baseRequests ⟨summaries,[⟨field,[0],group⟩]⟩
  let actual := if active then [actualRow 0 group] else []
  let rows := requestedRows requests actual
  let written := zeroValues (summaries.flatMap (fun summary => applyCall summary rootPort rows))
  let copied := markedCopyPool field copiedField group written
  let read := getter copiedField true (fieldsWritten (written ++ copied)) copied
  ⟨written,copied,read,sink group read,requests⟩

theorem request_dependent_execution (count field copiedField group : Nat) :
    executeRequested count field copiedField group true =
      ⟨[⟨.staticField field,0,.group group,[]⟩],
       [⟨.staticField copiedField,0,.group group,[]⟩],
       [⟨.base 3,0,.group group,[]⟩],[group],[⟨1,0,group⟩]⟩ := by
  simp [executeRequested, setter_chain_emits, baseRequests, writerRequest, setter,
    requestedRows, answer, actualRow, rootPort, parameter, applyCall, callReady,
    bodyLegal, rowsAligned, align, valueMatches, markMatches, evalBody, flowPermitted,
    originalsOf, zeroValues, markedCopyPool, marked_copy_execution, fieldsWritten,
    getter, staticGroup, sink, List.eraseDups_cons]

theorem requests_cannot_answer_without_actual (requests : List BaseRequest) :
    requestedRows requests [] = [] := rfl

theorem actual_without_request_not_admitted (actual : List Row) :
    requestedRows [] actual = [] := by
  induction actual with
  | nil => rfl
  | cons first rest ih => simpa [requestedRows] using ih

/-- Full source witness -> emitted Base interest -> matched actual Base answer
    -> ordinary summary application -> actual static marked image -> getter
    demand. This supplies the bootstrap missing from a parallel request count.
    Source eligibility is derived from the independent reference tree.
    Scope: the writer-field interest is declared by executeRequested. This
    theorem does not derive that interest backwards through a static copy. -/
def compileRequestedConnected (count field copiedField group full : Nat) (active : Bool)
    (tree : Run1StaticGroupDerivation.Tree (reference field copiedField group full active).rules
      (GroupedRun1StaticComposition.seeds (reference field copiedField group full active).sources)
      (referenceTarget group full)) :
    { seen : Nat // seen = group ∧ seen ∈ (executeRequested count field copiedField group active).observed } ×
    GroupedRun1StaticComposition.Replay (reference field copiedField group full active)
      (.nonzero full) (referenceTarget group full) := by
  have executed := reference_witness_requires_source field copiedField group full active tree
  refine ⟨⟨group,rfl,?_⟩,GroupedRun1StaticComposition.compileReplay _ (.nonzero full) tree⟩
  rw [executed, request_dependent_execution]
  simp

namespace Controls

#guard decide (setterChain 3 1 8 = [setter 1 8])
#guard decide (guardedChain 3 1 8 5 9 7 = [guardedWriter 1 8 5 9 7])
#guard decide ((execute 2 8 9 5 true).observed = [5])
#guard decide ((execute 2 8 9 5 true).requested = [⟨1,0,5⟩])
#guard decide ((execute 2 8 9 5 false).observed = [])
#guard decide ((runGuarded 2 8 5 9 7 [actualRow 0 5,actualRow 1 9]).map Outcome.originals = [[]])
#guard decide (runGuarded 2 8 5 9 7 [actualRow 0 5] = [])
#guard decide (runGuarded 2 8 5 9 7 [actualRow 0 5,
  { actualRow 1 9 with port := { rootPort with statement := 21 } }] = [])
#guard decide (runGuarded 2 8 5 9 7
  [{ actualRow 0 5 with originals := [⟨0,parameter 0 (.group 5)⟩] },actualRow 1 9] ≠ [])
#guard decide (zeroValues (runGuarded 2 8 5 9 7
  [{ actualRow 0 5 with originals := [⟨0,parameter 0 (.group 5)⟩] },actualRow 1 9]) = [])
#guard decide (getter 9 true [] [⟨.staticField 9,0,.group 5,[]⟩] = [])
#guard decide (getter 9 false [9] [⟨.staticField 9,0,.group 5,[]⟩] = [])
#guard decide (copyPool 8 9 [] = [])
#guard decide (sink 6 [⟨.base 3,0,.group 5,[]⟩] = [])
#guard decide ((executeNd 2 8 9 5 6 7 true true).observed = [7])
#guard decide ((executeNd 2 8 9 5 6 7 true false).observed = [])
#guard decide ((executeNd 2 8 9 5 6 7 false true).observed = [])
#guard decide ((executeNd 2 8 9 5 5 7 true true).observed = [7])
#guard decide ((joined rootPort (bindingArrives (publicationArrives {} (setter 1 8))
  [actualRow 0 5])).map Outcome.value = [⟨.staticField 8,0,.group 5,[]⟩])
#guard decide (joined rootPort (publicationArrives {} (setter 1 8)) = [])
#guard decide (joined rootPort (bindingArrives {} [actualRow 0 5]) = [])
#guard decide (joined rootPort (bindingArrives (publicationArrives {} (guardedWriter 1 8 5 9 7))
  [actualRow 0 5]) = [])
#guard decide (staticApply (markedCopySummary 8 9 5 1 2)
  [⟨.staticField 8,1,.group 5,[]⟩] = [⟨.staticField 9,2,.group 5,[]⟩])
#guard decide (staticApply (markedCopySummary 8 9 5 1 2)
  [⟨.staticField 8,0,.group 5,[]⟩] = [])
#guard decide ((executeMarked 2 8 9 5 true).observed = [5])
#guard decide ((executeMarked 2 8 9 5 false).observed = [])
#guard decide ((executeRequested 3 8 9 5 true).observed = [5])
#guard decide ((executeRequested 3 8 9 5 false).observed = [])
#guard decide (requestedRows [] [actualRow 0 5] = [])
#guard decide (requestedRows [⟨1,0,5⟩] [actualRow 0 6] = [])
#guard decide (requestedRows [⟨1,0,5⟩] [actualRow 0 5] = [actualRow 0 5])

def constructed : ConnectedCoverage 2 8 9 5 100 true :=
  compileConnected 2 8 9 5 100 true (fullReferenceWitness 8 9 5 100)

def ndConstructed := compileNdConnected 2 8 9 5 100 6 200 7 300 true true
  (fullNdWitness 8 9 5 100 6 200 7 300)

def requestConstructed := compileRequestedConnected 2 8 9 5 100 true
  (fullReferenceWitness 8 9 5 100)

#guard decide (ndConstructed.1.val = 7)
#guard decide (requestConstructed.1.val = 5)

/-- Named executable controls; exactly the cases checked above. -/
def controls : List (String × Bool) :=
  [ ("arbitrary-wrapper-setter-publication", decide (setterChain 3 1 8 = [setter 1 8])),
    ("arbitrary-wrapper-nd-publication", decide (guardedChain 3 1 8 5 9 7 = [guardedWriter 1 8 5 9 7])),
    ("source-writer-copy-getter-sink", decide ((execute 2 8 9 5 true).observed = [5])),
    ("emitted-base-bootstrap", decide ((execute 2 8 9 5 true).requested = [⟨1,0,5⟩])),
    ("no-source-no-observation", decide ((execute 2 8 9 5 false).observed = [])),
    ("nd-two-actual-inputs", decide ((runGuarded 2 8 5 9 7 [actualRow 0 5,actualRow 1 9]).map Outcome.originals = [[]])),
    ("nd-missing-input", decide (runGuarded 2 8 5 9 7 [actualRow 0 5] = [])),
    ("nd-wrong-callsite", decide (runGuarded 2 8 5 9 7 [actualRow 0 5,
        { actualRow 1 9 with port := { rootPort with statement := 21 } }] = [])),
    ("unresolved-original-retained", decide (runGuarded 2 8 5 9 7
        [{ actualRow 0 5 with originals := [⟨0,parameter 0 (.group 5)⟩] },actualRow 1 9] ≠ [])),
    ("unresolved-original-not-zero", decide (zeroValues (runGuarded 2 8 5 9 7
        [{ actualRow 0 5 with originals := [⟨0,parameter 0 (.group 5)⟩] },actualRow 1 9]) = [])),
    ("read-without-presence-disabled", decide (getter 9 true [] [⟨.staticField 9,0,.group 5,[]⟩] = [])),
    ("unreached-getter-disabled", decide (getter 9 false [9] [⟨.staticField 9,0,.group 5,[]⟩] = [])),
    ("empty-pool-no-copy", decide (copyPool 8 9 [] = [])),
    ("sink-wrong-group", decide (sink 6 [⟨.base 3,0,.group 5,[]⟩] = [])),
    ("nd-declared-group-change", decide ((executeNd 2 8 9 5 6 7 true true).observed = [7])),
    ("nd-missing-second-source", decide ((executeNd 2 8 9 5 6 7 true false).observed = [])),
    ("nd-missing-first-source", decide ((executeNd 2 8 9 5 6 7 false true).observed = [])),
    ("same-group-two-positions-still-complete", decide ((executeNd 2 8 9 5 5 7 true true).observed = [7])),
    ("publication-and-binding-join", decide ((joined rootPort (bindingArrives (publicationArrives {} (setter 1 8))
        [actualRow 0 5])).map Outcome.value = [⟨.staticField 8,0,.group 5,[]⟩])),
    ("publication-alone-not-input", decide (joined rootPort (publicationArrives {} (setter 1 8)) = [])),
    ("binding-alone-not-summary", decide (joined rootPort (bindingArrives {} [actualRow 0 5]) = [])),
    ("partial-nd-binding-not-output", decide (joined rootPort (bindingArrives (publicationArrives {} (guardedWriter 1 8 5 9 7))
        [actualRow 0 5]) = [])),
    ("marked-copy-actual-cell", decide (staticApply (markedCopySummary 8 9 5 1 2)
        [⟨.staticField 8,1,.group 5,[]⟩] = [⟨.staticField 9,2,.group 5,[]⟩])),
    ("marked-copy-disjoint-cell", decide (staticApply (markedCopySummary 8 9 5 1 2)
        [⟨.staticField 8,0,.group 5,[]⟩] = [])),
    ("marked-connected-output", decide ((executeMarked 2 8 9 5 true).observed = [5])),
    ("marked-no-source-no-output", decide ((executeMarked 2 8 9 5 false).observed = [])),
    ("causal-request-answer-writer-getter", decide ((executeRequested 3 8 9 5 true).observed = [5])),
    ("causal-request-no-actual-source", decide ((executeRequested 3 8 9 5 false).observed = [])),
    ("actual-without-request-not-admitted", decide (requestedRows [] [actualRow 0 5] = [])),
    ("request-wrong-answer-mark", decide (requestedRows [⟨1,0,5⟩] [actualRow 0 6] = [])),
    ("request-retains-actual-row", decide (requestedRows [⟨1,0,5⟩] [actualRow 0 5] = [actualRow 0 5])),
    ("executable-full-nd-correspondence", decide (ndConstructed.1.val = 7)),
    ("executable-request-driven-correspondence", decide (requestConstructed.1.val = 5)) ]

end Controls
end ApSpec.Run1OrdinaryStaticSupplyPipeline
