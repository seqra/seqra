/-
  Selected F1 strong preservation with layer-only exclusion fallback.

  A definite field overwrite still kills an old component wholly inside its
  destination. Only a surviving broad ancestor that would need a new spatial
  exclusion retains its original payload at DEMAND. Disjoint components and
  exact ancestor points are unchanged. No AFact.norm / STAR-to-ANY conversion
  occurs. Possible-alias writes use the separate existing weak keep rule;
  deciding/enumerating actual alias targets remains an adapter obligation.

  This finite scalar model uses the actual AP prefix/tail semantics. It checks
  survival coverage and independent pre-store value branches, not packed AP
  splitting, all rule lowerings, AA completeness, scheduling or whole-engine
  refinement. A normal concrete ANY payload denotes the existing must carrier;
  the edge layer here is independent of that payload. Selected F1 inputs and
  value branches are required to have Empty E; preservation never introduces E.
-/
import ApSpec.Core
import ApSpec.Run1DefaultSummaryApplication

namespace ApSpec.Run1StrongPreservation
open ApSpec
set_option autoImplicit false

/-- A real field destination is nonempty; static C.f uses parent=[C]. -/
structure FieldTarget where
  base : Base
  parent : List Acc
  field : Acc
  deriving DecidableEq, Repr

def FieldTarget.path (target : FieldTarget) : List Acc := target.parent ++ [target.field]

inductive Outcome where
  | identity
  | kill
  | exclusionNeeded
  deriving DecidableEq, Repr

/-- Full destination inclusion is checked BEFORE partial ancestor overlap.
    Exact ancestor points do not admit a nonempty tail and need no exclusion. -/
def classify (target : FieldTarget) (fact : PFact) : Outcome :=
  if Nat.beq fact.base target.base then
    match relate target.path fact.path with
    | .below _ => .kill
    | .above rest => if admitsTailB fact.kind rest then .exclusionNeeded else .identity
    | .apart => .identity
  else .identity

inductive Certainty where
  | definite
  | mayAlias
  deriving DecidableEq, Repr

/-- The original full premise is opaque and preserved, not used as current AP. -/
structure Edge (Origin : Type) where
  original : Origin
  current : _root_.ApSpec.Run1DefaultSummaryApplication.Layered
  deriving DecidableEq, Repr

def demote {Origin : Type} (input : Edge Origin) : Edge Origin :=
  {input with current := {input.current with demand := true}}

/-- The may-alias branch is the old weak preservation rule. It never adopts
    a definite kill, and this model does not add a new alias demotion policy. -/
def preserve {Origin : Type} (certainty : Certainty) (target : FieldTarget)
    (input : Edge Origin) : Option (Edge Origin) :=
  match certainty with
  | .mayAlias => some input
  | .definite => match classify target input.current.fact with
    | .identity => some input
    | .kill => none
    | .exclusionNeeded => some (demote input)

/-- Values have already been computed from pre-store inputs. Preservation
    neither re-evaluates nor changes their payload, layer or original premise. -/
def write {Origin : Type} (certainty : Certainty) (target : FieldTarget)
    (input : Edge Origin) (preStoreValues : List (Edge Origin)) : List (Edge Origin) :=
  (preserve certainty target input).toList ++ preStoreValues

def EmptyE (fact : PFact) : Prop := match fact.kind with
  | .star e => e = Excl.empty
  | _ => True

def Written (target : FieldTarget) (location : Loc) : Prop :=
  location.base = target.base ∧ ∃ suffix, location.path = target.path ++ suffix

def StrongOldSurvives (target : FieldTarget) (fact : PFact) (location : Loc) : Prop :=
  fact.covers location ∧ ¬ Written target location

theorem at_or_below_is_killed (target : FieldTarget) (suffix : List Acc)
    (kind : Kind) (mark : MarkA) :
    classify target ⟨target.base,target.path ++ suffix,kind,mark⟩ = .kill := by
  have below := CoreAux.relate_below (CoreAux.dropPrefix_some.mpr
    (show target.path ++ suffix = target.path ++ suffix from rfl))
  simp [classify,below]

theorem kill_has_destination_prefix (target : FieldTarget) (fact : PFact)
    (killed : classify target fact = .kill) :
    fact.base = target.base ∧ ∃ suffix, fact.path = target.path ++ suffix := by
  unfold classify at killed
  split at killed
  · rename_i base
    refine ⟨Nat.eq_of_beq_eq_true base,?_⟩
    cases relation : relate target.path fact.path with
    | below suffix => exact ⟨suffix,CoreAux.relate_below_inv relation⟩
    | above suffix =>
        cases admitted : admitsTailB fact.kind suffix <;>
          simp [relation,admitted] at killed
    | apart => rw [relation] at killed; cases killed
  · cases killed

/-- Exclusion fallback is only for a proper ancestor admitting the destination
    continuation; equality/subtree cases have already been classified as kill. -/
theorem exclusion_has_proper_ancestor (target : FieldTarget) (fact : PFact)
    (needsExclusion : classify target fact = .exclusionNeeded) :
    fact.base = target.base ∧ ∃ rest, target.path = fact.path ++ rest ∧
      rest ≠ [] ∧ admitsTailB fact.kind rest = true := by
  unfold classify at needsExclusion
  split at needsExclusion
  · rename_i base
    refine ⟨Nat.eq_of_beq_eq_true base,?_⟩
    cases relation : relate target.path fact.path with
    | below rest => simp [relation] at needsExclusion
    | apart => simp [relation] at needsExclusion
    | above rest =>
        obtain ⟨path,nonempty⟩ := CoreAux.relate_above_inv relation
        refine ⟨rest,path,nonempty,?_⟩
        cases admitted : admitsTailB fact.kind rest with
        | false => simp [relation,admitted] at needsExclusion
        | true => rfl
  · cases needsExclusion

/-- A killed component contains no old location outside the destination. -/
theorem killed_region_is_written (target : FieldTarget) (fact : PFact) (location : Loc)
    (killed : classify target fact = .kill) (covered : fact.covers location) :
    Written target location := by
  obtain ⟨base,rest,path⟩ := kill_has_destination_prefix target fact killed
  obtain ⟨actualBase,⟨suffix,actualPath,_⟩,_⟩ := covered
  refine ⟨actualBase.trans base,rest ++ suffix,?_⟩
  rw [actualPath,path,List.append_assoc]

/-- The identity outcome is genuinely disjoint from the write, not merely a
    Boolean flag supplied by a caller. -/
theorem identity_has_no_written_location (target : FieldTarget) (fact : PFact) (location : Loc)
    (identity : classify target fact = .identity) (covered : fact.covers location) :
    ¬ Written target location := by
  intro written
  obtain ⟨actualBase,⟨suffix,actualPath,tail⟩,_⟩ := covered
  obtain ⟨writtenBase,rest,writtenPath⟩ := written
  have base : fact.base = target.base := actualBase.symm.trans writtenBase
  have common : target.path ++ rest = fact.path ++ suffix := writtenPath.symm.trans actualPath
  rcases CoreAux.relate_common common with ⟨r,relation,_,_⟩ | ⟨r,relation,_,_,suffixEq⟩
  · simp [classify,base,relation] at identity
  · rw [suffixEq] at tail
    have admitted := CoreAux.tailI_append_admits tail
    simp [classify,base,relation,admitted] at identity

theorem root_STAR_needs_exclusion (target : FieldTarget) (mark : MarkA) :
    classify target ⟨target.base,[],.star Excl.empty,mark⟩ = .exclusionNeeded := by
  obtain ⟨b,parent,field⟩ := target
  cases parent <;>
    simp [classify,FieldTarget.path,relate,dropPrefix,admitsTailB,
      Excl.empty,Excl.admits,memB]

theorem root_EXACT_unchanged (target : FieldTarget) (mark : MarkA) :
    classify target ⟨target.base,[],.exact,mark⟩ = .identity := by
  obtain ⟨b,parent,field⟩ := target
  cases parent <;>
    simp [classify,FieldTarget.path,relate,dropPrefix,admitsTailB]

theorem disjoint_bases_unchanged (target : FieldTarget) (fact : PFact)
    (different : fact.base ≠ target.base) : classify target fact = .identity := by
  simp [classify,different]

theorem disjoint_paths_unchanged (target : FieldTarget) (fact : PFact)
    (apart : relate target.path fact.path = .apart) : classify target fact = .identity := by
  simp [classify,apart]

theorem definite_kill {Origin : Type} (target : FieldTarget) (input : Edge Origin)
    (killed : classify target input.current.fact = .kill) :
    preserve .definite target input = none := by simp [preserve,killed]

theorem exclusion_fallback {Origin : Type} (target : FieldTarget) (input : Edge Origin)
    (needsExclusion : classify target input.current.fact = .exclusionNeeded) :
    preserve .definite target input = some (demote input) := by simp [preserve,needsExclusion]

theorem identity_unchanged {Origin : Type} (target : FieldTarget) (input : Edge Origin)
    (identity : classify target input.current.fact = .identity) :
    preserve .definite target input = some input := by simp [preserve,identity]

theorem possible_alias_is_not_strong_kill {Origin : Type} (target : FieldTarget) (input : Edge Origin) :
    preserve .mayAlias target input = some input := rfl

/-- Every surviving old result has the IDENTICAL original premise and current
    payload: path, tail, mark/mx and existing E are not rewritten. -/
theorem preservation_full_payload {Origin : Type} (certainty : Certainty) (target : FieldTarget)
    (input output : Edge Origin) (kept : preserve certainty target input = some output) :
    output.original = input.original ∧ output.current.fact = input.current.fact := by
  cases certainty with
  | mayAlias => cases kept; exact ⟨rfl,rfl⟩
  | definite =>
      cases outcome : classify target input.current.fact <;>
        simp [preserve,outcome] at kept <;> subst output <;> exact ⟨rfl,rfl⟩

theorem preservation_layer_monotone {Origin : Type} (certainty : Certainty) (target : FieldTarget)
    (input output : Edge Origin) (kept : preserve certainty target input = some output)
    (demand : input.current.demand = true) : output.current.demand = true := by
  cases certainty with
  | mayAlias => cases kept; exact demand
  | definite =>
      cases outcome : classify target input.current.fact <;>
        simp [preserve,outcome] at kept <;> subst output
      · exact demand
      · rfl

theorem preservation_no_new_E {Origin : Type} (certainty : Certainty) (target : FieldTarget)
    (input output : Edge Origin) (kept : preserve certainty target input = some output)
    (empty : EmptyE input.current.fact) : EmptyE output.current.fact := by
  rw [(preservation_full_payload certainty target input output kept).2]
  exact empty

theorem write_outputs_EEmpty {Origin : Type} (certainty : Certainty) (target : FieldTarget)
    (input output : Edge Origin) (values : List (Edge Origin))
    (inputEmpty : EmptyE input.current.fact)
    (valuesEmpty : ∀ value, value ∈ values → EmptyE value.current.fact)
    (present : output ∈ write certainty target input values) : EmptyE output.current.fact := by
  rcases List.mem_append.mp present with old | fresh
  · cases kept : preserve certainty target input with
    | none => simp [kept] at old
    | some actual =>
        simp only [kept,Option.toList_some,List.mem_singleton] at old
        subst output
        exact preservation_no_new_E certainty target input actual kept inputEmpty
  · exact valuesEmpty output fresh

/-- Every location that survives ordinary STRONG destination subtraction is
    covered by an actual selected preservation result. Killed regions are not
    retained to obtain this theorem. -/
theorem strong_survivor_covered {Origin : Type} (certainty : Certainty) (target : FieldTarget)
    (input : Edge Origin) (location : Loc) (survives : StrongOldSurvives target input.current.fact location) :
    ∃ output, preserve certainty target input = some output ∧ output.current.fact.covers location := by
  cases certainty with
  | mayAlias => exact ⟨input,rfl,survives.1⟩
  | definite =>
      cases outcome : classify target input.current.fact with
      | identity => exact ⟨input,by simp [preserve,outcome],survives.1⟩
      | exclusionNeeded => exact ⟨demote input,by simp [preserve,outcome],survives.1⟩
      | kill => exact False.elim (survives.2 (killed_region_is_written target _ location outcome survives.1))

theorem pre_store_value_unchanged {Origin : Type} (certainty : Certainty) (target : FieldTarget)
    (input value : Edge Origin) (values : List (Edge Origin)) (present : value ∈ values) :
    value ∈ write certainty target input values :=
  List.mem_append_right _ present

def StrongWriteContains {Origin : Type} (target : FieldTarget) (input : Edge Origin)
    (values : List (Edge Origin)) (location : Loc) : Prop :=
  StrongOldSurvives target input.current.fact location ∨
    ∃ value, value ∈ values ∧ value.current.fact.covers location

theorem strong_write_survival_coverage {Origin : Type} (certainty : Certainty) (target : FieldTarget)
    (input : Edge Origin) (values : List (Edge Origin)) (location : Loc)
    (strong : StrongWriteContains target input values location) :
    ∃ output, output ∈ write certainty target input values ∧ output.current.fact.covers location := by
  rcases strong with old | ⟨value,present,covered⟩
  · obtain ⟨output,kept,covered⟩ := strong_survivor_covered certainty target input location old
    refine ⟨output,List.mem_append_left _ ?_,covered⟩
    simp [kept]
  · exact ⟨value,pre_store_value_unchanged certainty target input value values present,covered⟩

namespace Controls

def target : FieldTarget := ⟨4,[],5⟩
def root : Edge Nat := ⟨11,⟨⟨4,[],.star Excl.empty,.star⟩,false⟩⟩
def destination : Edge Nat := ⟨11,⟨⟨4,[5],.star Excl.empty,.star⟩,false⟩⟩
def descendant : Edge Nat := ⟨11,⟨⟨4,[5,7],.exact,.conc 3⟩,false⟩⟩
def sibling : Edge Nat := ⟨11,⟨⟨4,[6],.star Excl.empty,.star⟩,false⟩⟩
def exactAncestor : Edge Nat := ⟨11,⟨⟨4,[],.exact,.conc 3⟩,false⟩⟩
def exactDestination : Edge Nat := ⟨11,⟨⟨4,[5],.exact,.conc 3⟩,false⟩⟩
def demandDestination : Edge Nat := demote destination
def demandDescendant : Edge Nat := demote descendant
def demandExactDestination : Edge Nat := demote exactDestination
def fresh : Edge Nat := ⟨19,⟨⟨4,[5],.exact,.conc 3⟩,false⟩⟩
def staticTarget : FieldTarget := ⟨1,[2],3⟩
def staticRoot : Edge Nat := ⟨21,⟨⟨1,[],.star Excl.empty,.star⟩,false⟩⟩
def staticField : Edge Nat := ⟨21,⟨⟨1,[2,3],.exact,.conc 7⟩,false⟩⟩
def staticSibling : Edge Nat := ⟨21,⟨⟨1,[2,5],.star Excl.empty,.star⟩,false⟩⟩

theorem root_demotes_without_ANY : preserve .definite target root = some (demote root) := rfl
theorem destination_is_killed : preserve .definite target destination = none := rfl
theorem descendant_is_killed : preserve .definite target descendant = none := rfl
theorem sibling_is_unchanged : preserve .definite target sibling = some sibling := rfl
theorem exact_ancestor_is_unchanged : preserve .definite target exactAncestor = some exactAncestor := rfl
theorem possible_alias_destination_survives : preserve .mayAlias target destination = some destination := rfl

/-- The old 'overlap always retains D' result is NOT the selected rule. -/
theorem broad_destination_is_not_retained_DEMAND :
    preserve .definite target destination ≠ some (demote destination) := by decide

theorem fresh_value_survives_old_destination_kill :
    write .definite target destination [fresh] = [fresh] := rfl

theorem static_root_demotes_same_payload :
    preserve .definite staticTarget staticRoot = some (demote staticRoot) := rfl
theorem exact_static_destination_killed : preserve .definite staticTarget staticField = none := rfl
theorem static_sibling_unchanged :
    preserve .definite staticTarget staticSibling = some staticSibling := rfl

theorem demand_destination_is_killed : preserve .definite target demandDestination = none := rfl
theorem demand_descendant_is_killed : preserve .definite target demandDescendant = none := rfl
theorem demand_exact_destination_is_killed : preserve .definite target demandExactDestination = none := rfl

/-- Independent scalar members: ancestor demotes, overwritten exact disappears,
    sibling stays NORMAL. This does not claim a packed-trie splitting proof. -/
theorem mixed_members_preserved_separately :
    [root,exactDestination,sibling].flatMap (fun input => write .definite target input []) =
      [demote root,sibling] := rfl

/-- A logical field writeback with no value branch does not resurrect the
    destination; direct selected-mark cleaner behavior is a separate operation. -/
theorem empty_writeback_kills_destination : write .definite target exactDestination [] = [] := rfl

/-- A self-assignment has a genuine pre-store value branch. Killing its old
    destination does not kill or demote that independently computed value. -/
theorem self_assignment_keeps_pre_store_value :
    write .definite target exactDestination [exactDestination] = [exactDestination] := rfl

/-- The ancestor case has BOTH an unwritten surviving point and an overwritten
    point. Retaining it NORMAL would certify a point the strong write removed. -/
theorem ancestor_has_partial_overwrite :
    StrongOldSurvives target root.current.fact ⟨4,[],3⟩ ∧
    root.current.fact.covers ⟨4,[5],3⟩ ∧ Written target ⟨4,[5],3⟩ := by
  refine ⟨⟨⟨rfl,⟨[],rfl,rfl⟩,trivial⟩,?_⟩,
    ⟨rfl,⟨[5],rfl,rfl⟩,trivial⟩,rfl,[],rfl⟩
  intro written
  obtain ⟨_,suffix,path⟩ := written
  cases path

def checks : Bool :=
  preserve .definite target root == some (demote root) &&
  preserve .definite target destination == none &&
  preserve .definite target descendant == none &&
  preserve .definite target sibling == some sibling &&
  preserve .definite target exactAncestor == some exactAncestor &&
  preserve .mayAlias target destination == some destination &&
  write .definite target destination [fresh] == [fresh] &&
  preserve .definite staticTarget staticRoot == some (demote staticRoot) &&
  preserve .definite staticTarget staticField == none &&
  preserve .definite staticTarget staticSibling == some staticSibling &&
  preserve .definite target demandDestination == none &&
  preserve .definite target demandDescendant == none &&
  preserve .definite target demandExactDestination == none &&
  [root,exactDestination,sibling].flatMap (fun input => write .definite target input []) ==
    [demote root,sibling] &&
  write .definite target exactDestination [] == [] &&
  write .definite target exactDestination [exactDestination] == [exactDestination]

theorem checks_pass : checks = true := by decide

end Controls

#eval Controls.checks
#print axioms kill_has_destination_prefix
#print axioms exclusion_has_proper_ancestor
#print axioms killed_region_is_written
#print axioms identity_has_no_written_location
#print axioms root_STAR_needs_exclusion
#print axioms root_EXACT_unchanged
#print axioms preservation_full_payload
#print axioms preservation_layer_monotone
#print axioms preservation_no_new_E
#print axioms write_outputs_EEmpty
#print axioms strong_survivor_covered
#print axioms pre_store_value_unchanged
#print axioms strong_write_survival_coverage
#print axioms Controls.checks_pass

end ApSpec.Run1StrongPreservation
