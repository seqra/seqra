/-
  Local scope of native must-record demotion timing.

  Demotion commutes with every empty-source STAR/STAR mapping of a concrete
  exact or any leaf. JVM binding back is the empty-target instance; call aliases
  can prepend an arbitrary target path. Path/mark filters and the field cut
  commute too. Primitive cleaners at an empty path commute on this domain.

  These results do not prove commutation with F74 named-field action chains,
  their intermediate filters/cuts, alias selection, trie folding or a complete
  call plan. The generic post-result field-read counterexample remains outside
  the JVM binding-back contract. No whole iteration result follows here.

  LateReturn models the selected contract directly: returned actions and
  bindings run on the original annotated results; a separate pending batch
  becomes DEMAND only at the end, before the call cut. The proofs compare
  separate batches with a per-member tagged reference and do not assume
  equivalence with early demotion. No complete call-plan theorem is claimed.
-/
import ApSpec.CurrentProposal

namespace ApSpec.CurrentRecordTiming
open ApSpec ApSpec.AnyTaintEx

def demote (x : XFact) : XFact := recLayerX true false x

def mappedDemotion (r : ResX) : ResX := ⟨r.facts.map demote, r.reqs⟩

/-- JVM return binding has empty source and target paths. Forward call alias
    mappings have the same source and may prepend a target path. -/
def rootMapping (srcBase dstBase : Base) (prefixPath : List Acc) : MicroEdge :=
  (⟨srcBase, [], .star Excl.empty, .star⟩,
   ⟨dstBase, prefixPath, .star Excl.empty, .star⟩)

theorem mapping_commutes (x : XFact) (srcBase dstBase : Base) (prefixPath : List Acc)
    (tail : x.af.fact.kind = .exact ∨ x.af.fact.kind = .any)
    (mark : ∃ t, x.af.fact.mark = .conc t) :
    bindX (demote x) (rootMapping srcBase dstBase prefixPath) =
      mappedDemotion (bindX x (rootMapping srcBase dstBase prefixPath)) := by
  obtain ⟨⟨⟨base, path, kind, slot⟩, dm⟩, ex⟩ := x
  dsimp only at tail mark ⊢
  obtain ⟨t, rfl⟩ := mark
  cases baseMatch : Nat.beq base srcBase <;>
    rcases tail with rfl | rfl <;>
    cases path <;> cases dm <;> cases ex <;>
    simp [bindX, rootMapping, demote, recLayerX, mappedDemotion,
      applyEdgeX, annX, relate, dropPrefix, applyEdge, baseMatch, belowCase,
      admitsTailB, Excl.admits, Excl.empty, tailExcl, Excl.union, Excl.isEmptyB,
      keepB, markGate, markComp, normX, carriesB, concB, Kind.isAny, layerX,
      AFact.norm, memB, Res.none]

/-- The concrete-path filter and the mark policy read no layer or annotation.
    This equality does not allow a filter to inspect the DEMAND bit. -/
def selected (keep : PFact → Bool) (x : XFact) : Option XFact :=
  if keep x.af.fact then some x else none

theorem filter_commutes (keep : PFact → Bool) (x : XFact) :
    selected keep (demote x) = (selected keep x).map demote := by
  cases accepts : keep x.af.fact <;>
    simp [selected, demote, recLayerX, accepts]

/-- Demotion commutes with a field cut, including its path truncation. -/
theorem cut_commutes (counted : Acc → Bool) (limit : Nat) (x : XFact) :
    limitFX counted limit (demote x) = demote (limitFX counted limit x) := by
  cases cut : cutPath counted limit x.af.fact.path with
  | none => simp [limitFX, demote, recLayerX, cut]
  | some path =>
      simp [limitFX, demote, recLayerX, cut, limitF]

/-- The same-base root cleaner has no F74 read/write actions. -/
theorem root_cleaner_commutes (base : Base) (reach : CleanReach) (mark : Option Mark)
    (x : XFact)
    (tail : x.af.fact.kind = .exact ∨ x.af.fact.kind = .any)
    (concrete : ∃ t, x.af.fact.mark = .conc t) :
    cleanResX ⟨base, [], reach, mark⟩ (demote x) =
      mappedDemotion (cleanResX ⟨base, [], reach, mark⟩ x) := by
  obtain ⟨⟨⟨xb, path, kind, slot⟩, dm⟩, ex⟩ := x
  dsimp only at tail concrete ⊢
  obtain ⟨t, rfl⟩ := concrete
  cases baseMatch : Nat.beq xb base <;>
    rcases tail with rfl | rfl <;>
    cases path <;> cases reach <;> cases dm <;> cases mark with
    | none =>
        simp [cleanResX, cleanPosX, cleanPos, baseMatch, relate, dropPrefix,
          demote, recLayerX, mappedDemotion, Cleaner.markB, partX, concPart,
          carriesB, concB, Kind.isAny, ResX.none]
    | some u =>
        cases selectedMark : Nat.beq t u <;>
          simp [cleanResX, cleanPosX, cleanPos, baseMatch, relate, dropPrefix,
            demote, recLayerX, mappedDemotion, Cleaner.markB, selectedMark,
            partX, concPart, carriesB, concB, Kind.isAny, ResX.none]

namespace Certificate

def normalMust : XFact := ⟨⟨⟨3, [], .any, .conc 7⟩, false⟩, .set [2]⟩
def normalExact : XFact := ⟨⟨⟨3, [4], .exact, .conc 7⟩, false⟩, Excl.empty⟩
def input : List XFact := [normalMust, normalExact]
def root : MicroEdge := rootMapping 3 5 []
def alias : MicroEdge := rootMapping 3 6 [8]
def filter (f : PFact) : Bool := !f.path.isEmpty

def check : Bool :=
  (input.all fun x => bindX (demote x) root == mappedDemotion (bindX x root)) &&
  (input.all fun x => bindX (demote x) alias == mappedDemotion (bindX x alias)) &&
  (selected filter (demote normalMust) == none) &&
  (selected filter (demote normalExact) == some (demote normalExact)) &&
  (limitFX (fun _ => true) 0 (demote normalExact) ==
    demote (limitFX (fun _ => true) 0 normalExact)) &&
  (cleanResX ⟨3, [], .below, some 7⟩ (demote normalMust) ==
    ⟨[⟨⟨⟨3, [], .exact, .conc 7⟩, true⟩, Excl.empty⟩], []⟩)
theorem checked : check = true := by decide

end Certificate

namespace LateReturn

/-- Candidate metadata belongs to the call adapter, not to the AP fact. -/
structure Leaf where
  facts : XFact
  candidate : Bool
deriving DecidableEq, Repr

/-- The reference tags each member. The proposal instead resumes ordinary
    and pending batches separately. -/
structure Tagged where
  leaf : Leaf
  pending : Bool
deriving DecidableEq, Repr

abbrev Step := XFact → List XFact

/-- A returned action or binding changes AP data only through its supplied
    operation. Candidate metadata is carried through this local stage. -/
def advance (step : Step) (xs : List Leaf) : List Leaf :=
  xs.flatMap fun x => (step x.facts).map fun y => ⟨y, x.candidate⟩

def pipeline : List Step → List Leaf → List Leaf
  | [], xs => xs
  | step :: steps, xs => pipeline steps (advance step xs)

def rawPipeline : List Step → List XFact → List XFact
  | [], xs => xs
  | step :: steps, xs => rawPipeline steps (xs.flatMap step)

def tag (pending : Bool) (xs : List Leaf) : List Tagged :=
  xs.map fun x => ⟨x, pending⟩

def advanceTagged (step : Step) (xs : List Tagged) : List Tagged :=
  xs.flatMap fun x => (step x.leaf.facts).map fun y =>
    ⟨⟨y, x.leaf.candidate⟩, x.pending⟩

def taggedPipeline : List Step → List Tagged → List Tagged
  | [], xs => xs
  | step :: steps, xs => taggedPipeline steps (advanceTagged step xs)

/-- False admits the pending branch to memory aliases even while AP data
    still has its original layer. It does not clear the AP exclusion. -/
def pendingBatch (xs : List Leaf) : List Leaf :=
  xs.map fun x => ⟨x.facts, false⟩

/-- The final hand-off occurs after all returned actions and bindings, before
    the final call cut. Ordinary and inside members receive only that cut. -/
def finishLeaf (counted : Acc → Bool) (limit : Nat) (pending : Bool) (x : Leaf) : Leaf :=
  ⟨limitFX counted limit (if pending then demote x.facts else x.facts),
   if pending then false else x.candidate⟩

def finishTagged (counted : Acc → Bool) (limit : Nat) (xs : List Tagged) : List Leaf :=
  xs.map fun x => finishLeaf counted limit x.pending x.leaf

def splitReturn (steps : List Step) (counted : Acc → Bool) (limit : Nat)
    (ordinary pending : List Leaf) : List Leaf :=
  (pipeline steps ordinary).map (finishLeaf counted limit false) ++
  (pipeline steps (pendingBatch pending)).map (finishLeaf counted limit true)

def taggedReturn (steps : List Step) (counted : Acc → Bool) (limit : Nat)
    (ordinary pending : List Leaf) : List Leaf :=
  finishTagged counted limit (taggedPipeline steps
    (tag false ordinary ++ tag true (pendingBatch pending)))

theorem advance_data (step : Step) (xs : List Leaf) :
    (advance step xs).map Leaf.facts = (xs.map Leaf.facts).flatMap step := by
  simp [advance, List.map_flatMap, List.flatMap_map, List.map_map,
    Function.comp_def]

/-- The wrapper retains the exact output facts and annotations of every
    supplied operation. This permits arbitrary operations, including ones
    for which early demotion would not commute. -/
theorem pipeline_data (steps : List Step) (xs : List Leaf) :
    (pipeline steps xs).map Leaf.facts = rawPipeline steps (xs.map Leaf.facts) := by
  induction steps generalizing xs with
  | nil => rfl
  | cons step steps ih =>
      simp only [pipeline, rawPipeline, ih, advance_data]

theorem pending_data (xs : List Leaf) :
    (pendingBatch xs).map Leaf.facts = xs.map Leaf.facts := by
  simp [pendingBatch, List.map_map, Function.comp_def]

theorem pending_pipeline_data (steps : List Step) (xs : List Leaf) :
    (pipeline steps (pendingBatch xs)).map Leaf.facts =
      rawPipeline steps (xs.map Leaf.facts) := by
  rw [pipeline_data, pending_data]

theorem advance_tag (step : Step) (pending : Bool) (xs : List Leaf) :
    advanceTagged step (tag pending xs) = tag pending (advance step xs) := by
  simp [advanceTagged, advance, tag, List.map_flatMap, List.flatMap_map,
    List.map_map, Function.comp_def]

theorem advance_pending (step : Step) (xs : List Leaf) :
    advance step (pendingBatch xs) = pendingBatch (advance step xs) := by
  simp [advance, pendingBatch, List.map_flatMap, List.flatMap_map,
    List.map_map, Function.comp_def]

theorem pipeline_pending (steps : List Step) (xs : List Leaf) :
    pipeline steps (pendingBatch xs) = pendingBatch (pipeline steps xs) := by
  induction steps generalizing xs with
  | nil => rfl
  | cons step steps ih => simp only [pipeline, advance_pending, ih]

theorem pending_pipeline_candidate (steps : List Step) (xs : List Leaf)
    (y : Leaf) (member : y ∈ pipeline steps (pendingBatch xs)) :
    y.candidate = false := by
  rw [pipeline_pending] at member
  obtain ⟨x, _, rfl⟩ := List.mem_map.mp member
  rfl

theorem pipeline_tag (steps : List Step) (pending : Bool) (xs : List Leaf) :
    taggedPipeline steps (tag pending xs) = tag pending (pipeline steps xs) := by
  induction steps generalizing xs with
  | nil => rfl
  | cons step steps ih => simp only [taggedPipeline, pipeline, advance_tag, ih]

theorem tagged_pipeline_append (steps : List Step) (xs ys : List Tagged) :
    taggedPipeline steps (xs ++ ys) =
      taggedPipeline steps xs ++ taggedPipeline steps ys := by
  induction steps generalizing xs ys with
  | nil => rfl
  | cons step steps ih =>
      simp only [taggedPipeline, advanceTagged, List.flatMap_append, ih]

/-- Split resumptions implement the same local hand-off as member tags,
    preserving branch membership through any sequence of fact operations. -/
theorem split_eq_tagged (steps : List Step) (counted : Acc → Bool) (limit : Nat)
    (ordinary pending : List Leaf) :
    splitReturn steps counted limit ordinary pending =
      taggedReturn steps counted limit ordinary pending := by
  unfold splitReturn taggedReturn
  rw [tagged_pipeline_append, pipeline_tag, pipeline_tag]
  simp [finishTagged, tag, List.map_map, Function.comp_def]

theorem ordinary_finish (counted : Acc → Bool) (limit : Nat) (x : Leaf) :
    finishLeaf counted limit false x =
      ⟨limitFX counted limit x.facts, x.candidate⟩ := rfl

theorem pending_finish (counted : Acc → Bool) (limit : Nat) (x : Leaf) :
    (finishLeaf counted limit true x).facts.af.demand = true ∧
    (finishLeaf counted limit true x).facts.ex = Excl.empty ∧
    (finishLeaf counted limit true x).candidate = false := by
  cases cut : cutPath counted limit x.facts.af.fact.path <;>
    simp [finishLeaf, limitFX, demote, recLayerX, cut, limitF]

/-- A legal already-DEMAND result is unchanged by the pending demotion;
    only the normal final call cut remains. -/
theorem already_demand_finish (counted : Acc → Bool) (limit : Nat) (x : Leaf)
    (demand : x.facts.af.demand = true) (wf : WFX x.facts) :
    (finishLeaf counted limit true x).facts = limitFX counted limit x.facts := by
  have noEx : x.facts.ex = Excl.empty :=
    wf (by simp [carriesB, demand])
  obtain ⟨⟨af, ex⟩, candidate⟩ := x
  obtain ⟨fact, dm⟩ := af
  dsimp only at demand noEx ⊢
  subst dm
  subst ex
  rfl

/-- The normative memory-effect guard admits DEMAND or a false candidate. -/
def aliasAdmits (x : Leaf) : Bool := x.facts.af.demand || !x.candidate

theorem pending_alias_admitted (counted : Acc → Bool) (limit : Nat) (x : Leaf) :
    aliasAdmits (finishLeaf counted limit true x) = true := by
  simp [aliasAdmits, (pending_finish counted limit x).1]

theorem pending_candidate_admitted (x : XFact) :
    aliasAdmits ⟨x, false⟩ = true := by simp [aliasAdmits]

theorem pending_pipeline_admitted (steps : List Step) (xs : List Leaf)
    (y : Leaf) (member : y ∈ pipeline steps (pendingBatch xs)) :
    aliasAdmits y = true := by
  simp [aliasAdmits, pending_pipeline_candidate steps xs y member]

namespace Certificate
open ApSpec.CurrentProposal

def recordInput : List Leaf := RecordTiming.result.map fun x => ⟨x, false⟩
def fieldRead : Step := fun x => (bindX x RecordTiming.read).facts
def bindBack : Step := fun x => (bindX x (rootMapping 5 6 [])).facts
def alias : MicroEdge := rootMapping 5 9 [8]
/-- In the native plan the alias and bind-back mappings are sibling branches.
    The pending batch has a false candidate before either mapping. -/
def guardedAlias : Step := fun x =>
  if aliasAdmits ⟨x, false⟩ then (bindX x alias).facts else []
def returnBranches : Step := fun x => bindBack x ++ guardedAlias x
def countFields : Acc → Bool := fun _ => true

def beforeRead : List XFact :=
  (pipeline [] (pendingBatch recordInput)).map Leaf.facts
def lateRead : List Leaf := splitReturn [fieldRead] countFields 3 [] recordInput
def earlyRead : List XFact := RecordTiming.early
def beforeFinish : List XFact :=
  (pipeline [bindBack] (pendingBatch recordInput)).map Leaf.facts
def afterFinish : List Leaf := splitReturn [bindBack] countFields 0 [] recordInput
def beforeAliasFinish : List XFact :=
  (pipeline [guardedAlias] (pendingBatch recordInput)).map Leaf.facts
def afterAliases : List XFact :=
  (splitReturn [guardedAlias] countFields 0 [] recordInput).map Leaf.facts
def afterAliasesWithinLimit : List XFact :=
  (splitReturn [guardedAlias] countFields 3 [] recordInput).map Leaf.facts
def bothBranches : List Leaf := splitReturn [returnBranches] countFields 0 [] recordInput
def mixed : List Leaf :=
  splitReturn [bindBack] countFields 3 recordInput recordInput
def cutOutput : Leaf := finishLeaf countFields 0 true
  ⟨ApSpec.CurrentRecordTiming.Certificate.normalExact, true⟩

def check : Bool :=
  (beforeRead == [RecordTiming.g]) &&
  (beforeRead.map XFact.ex == [.set [2]]) &&
  (lateRead == []) &&
  (earlyRead == [⟨⟨⟨6, [], .any, .conc 7⟩, true⟩, Excl.empty⟩]) &&
  (beforeFinish == [⟨⟨⟨6, [], .any, .conc 7⟩, false⟩, .set [2]⟩]) &&
  (afterFinish == [⟨⟨⟨⟨6, [], .any, .conc 7⟩, true⟩, Excl.empty⟩, false⟩]) &&
  ((pendingBatch recordInput).all fun x => !x.facts.af.demand &&
    x.candidate == false && aliasAdmits x) &&
  (beforeAliasFinish == [⟨⟨⟨9, [8], .any, .conc 7⟩, false⟩, .set [2]⟩]) &&
  (afterAliases == [⟨⟨⟨9, [], .any, .conc 7⟩, true⟩, Excl.empty⟩]) &&
  (afterAliasesWithinLimit == [⟨⟨⟨9, [8], .any, .conc 7⟩, true⟩, Excl.empty⟩]) &&
  ((afterAliases.filter fun x => x.af.demand).length == 1) &&
  ((bothBranches.filter fun x => x.facts.af.demand).length == 2) &&
  (mixed.map fun x => x.facts.af.demand == false) == [true, false] &&
  (mixed.map fun x => x.facts.ex) == [.set [2], Excl.empty] &&
  (cutOutput == ⟨⟨⟨⟨3, [], .any, .conc 7⟩, true⟩, Excl.empty⟩, false⟩)

theorem checked : check = true := by decide

end Certificate
end LateReturn

#print axioms mapping_commutes
#print axioms filter_commutes
#print axioms cut_commutes
#print axioms root_cleaner_commutes
#print axioms Certificate.checked
#print axioms LateReturn.pipeline_data
#print axioms LateReturn.pending_pipeline_data
#print axioms LateReturn.split_eq_tagged
#print axioms LateReturn.pending_finish
#print axioms LateReturn.already_demand_finish
#print axioms LateReturn.pending_alias_admitted
#print axioms LateReturn.pending_candidate_admitted
#print axioms LateReturn.pending_pipeline_candidate
#print axioms LateReturn.pending_pipeline_admitted
#print axioms LateReturn.Certificate.checked

end ApSpec.CurrentRecordTiming
