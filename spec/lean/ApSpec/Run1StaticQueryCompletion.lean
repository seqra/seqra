/-
Local run-2 query coverage after run-1 static/static summary removal.
The selected run-2 FULL grammar is named field EXACT/ANY or exact class marker.
The user pinned it on 22 October 2026. A positive field limit alone does not
imply this grammar. Source, alias, record, reversal, binding and input adapters
must establish it. Later runs and arbitrary raw AP values are outside this gate.

The model calls existing emitM/emitW AP operations. An old group selector is
instantiated with the actual FULL identifier after its owner matches. No mark
or field universe is enumerated. We compare StrongQueries' actual query plus
extra concrete-guide starts, not FLOW-guide execution or whole analyzer closure.
Run-1 guide entries are field EXACT/ANY, class EXACT, and ignored FLOW entries.
E is Empty; backward MUST is absent. Broad FULL raw root/class values below
are counterexamples, not silently equated to named fields.
-/
import ApSpec.AbsDefs
import ApSpec.Run1Requests
import ApSpec.RuleGroupMarks

namespace ApSpec.Run1StaticQueryCompletion
open ApSpec ApSpec.Abs
set_option autoImplicit false
abbrev Owner := RuleGroupMarks.OwnerRegistry

inductive Actual where
  | fieldExact (cls field full : Nat)
  | fieldAny (cls field full : Nat)
  | classExact (cls full : Nat)
  deriving DecidableEq, Repr

def Actual.pattern : Actual → PFact
  | .fieldExact c f t => Run1Requests.concrete (.field c f) .exact t
  | .fieldAny c f t => Run1Requests.concrete (.field c f) .any t
  | .classExact c t => Run1Requests.concrete (.cls c) .exact t

def LegalActual (raw : PFact) : Prop := ∃ actual : Actual, actual.pattern = raw

/-- Same-field EXACT is a real intersection, not a widened alias or descendant. -/
def complete : Actual → List PFact
  | a@(.fieldExact _ _ _) => [a.pattern]
  | a@(.fieldAny c f t) => [a.pattern, (.fieldExact c f t : Actual).pattern]
  | a@(.classExact _ _) => [a.pattern]

inductive Guide where
  | rootFlow
  | rootGroup (group : Nat)
  | fieldFlow (cls field : Nat)
  | fieldExact (cls field group : Nat)
  | fieldAny (cls field group : Nat)
  | classExact (cls group : Nat)
  deriving DecidableEq, Repr

/-- Group matching copies the actual FULL mark into the existing AP emitter.
    StrongQueries filters FLOW old guides out of its additional-query path. -/
def oldExtraRaw (owner : Owner) (guide : Guide) (actual : PFact) : Option PFact :=
  match actual.mark with
  | .conc t => match guide with
    | .rootFlow | .fieldFlow _ _ => none
    | .rootGroup g =>
        if g = owner t then emitM ⟨1,[],.any,.conc t⟩ actual else none
    | .fieldExact c f g =>
        if g = owner t then emitM (Run1Requests.concrete (.field c f) .exact t) actual else none
    | .fieldAny c f g =>
        if g = owner t then emitM (Run1Requests.concrete (.field c f) .any t) actual else none
    | .classExact c g =>
        if g = owner t then emitM (Run1Requests.concrete (.cls c) .exact t) actual else none
  | _ => none

def oldExtra (owner : Owner) (guide : Guide) (actual : Actual) : Option PFact :=
  oldExtraRaw owner guide actual.pattern

def referenceQueries (owner : Owner) (guides : List Guide) (actual : Actual) : List PFact :=
  actual.pattern :: guides.filterMap (fun guide => oldExtra owner guide actual)

theorem actual_query_preserved (actual : Actual) : actual.pattern ∈ complete actual := by
  cases actual <;> simp [complete]

theorem completion_at_most_two (actual : Actual) : (complete actual).length ≤ 2 := by
  cases actual <;> simp [complete]

/-- Bounded class-leading shape counter, not a theorem about arbitrary paths. -/
def countedFields : List Nat → Nat
  | [] => 0
  | [_] => 0
  | _ :: _ :: rest => rest.length + 1

theorem legal_limit_one (actual : Actual) : countedFields actual.pattern.path ≤ 1 := by
  cases actual <;> simp [Actual.pattern,Run1Requests.concrete,Run1Requests.Point.path,countedFields]

theorem legal_static_base (actual : Actual) : actual.pattern.base = 1 := by cases actual <;> rfl

theorem legal_full_mark (actual : Actual) : ∃ full, actual.pattern.mark = .conc full := by
  cases actual with
  | fieldExact c f t => exact ⟨t,rfl⟩
  | fieldAny c f t => exact ⟨t,rfl⟩
  | classExact c t => exact ⟨t,rfl⟩

theorem legal_no_STAR (actual : Actual) (e : Excl) : actual.pattern.kind ≠ .star e := by
  cases actual <;> simp [Actual.pattern,Run1Requests.concrete]

theorem completion_keeps_full_mark (actual : Actual) (query : PFact)
    (present : query ∈ complete actual) : query.mark = actual.pattern.mark := by
  cases actual <;> simp_all [complete,Actual.pattern,Run1Requests.concrete]
  rcases present with rfl | rfl <;> rfl

theorem completion_keeps_named_path (actual : Actual) (query : PFact)
    (present : query ∈ complete actual) : query.path = actual.pattern.path := by
  cases actual <;> simp_all [complete,Actual.pattern,Run1Requests.concrete]
  rcases present with rfl | rfl <;> rfl

theorem exact_class_has_one_query (cls full : Nat) :
    complete (.classExact cls full) = [(.classExact cls full : Actual).pattern] := rfl

theorem exact_completion_is_AP_intersection (cls field full : Nat) :
    emitM (.fieldExact cls field full : Actual).pattern (.fieldAny cls field full : Actual).pattern =
      some (.fieldExact cls field full : Actual).pattern := by
  simp [Actual.pattern,Run1Requests.concrete,emitM,markMatchB,RCore.relate_self,meetK]

theorem exact_completion_is_emitW (cls field full : Nat) :
    emitW (.fieldExact cls field full : Actual).pattern (.fieldAny cls field full : Actual).pattern =
      some (.fieldExact cls field full : Actual).pattern := by
  rw [emitW_nonstar (by simp [Actual.pattern,Run1Requests.concrete])]
  exact exact_completion_is_AP_intersection cls field full

/-- Every old concrete intersection copies the actual full identifier. Two
    different FULL members of the same group do not become one full mark. -/
theorem old_intersection_keeps_actual_mark (owner : Owner) (guide : Guide)
    (actual query : PFact) (emitted : oldExtraRaw owner guide actual = some query) :
    query.mark = actual.mark := by
  cases hm : actual.mark <;> cases guide <;> simp_all [oldExtraRaw]
  all_goals exact (RCore.emitM_mark emitted.2).trans hm

theorem no_full_from_FLOW (owner : Owner) (guide : Guide) (base : Nat)
    (path : List Nat) (kind : Kind) :
    oldExtraRaw owner guide ⟨base,path,kind,.star⟩ = none := rfl

theorem no_full_from_excluded_FLOW (owner : Owner) (guide : Guide) (base : Nat)
    (path : List Nat) (kind : Kind) (excluded : List Nat) :
    oldExtraRaw owner guide ⟨base,path,kind,.starEx excluded⟩ = none := rfl

theorem same_path_intersection (base : Nat) (path : List Nat) (a d : Kind) (full : Nat) :
    emitM ⟨base,path,d,.conc full⟩ ⟨base,path,a,.conc full⟩ =
      some ⟨base,path,meetK a d,.conc full⟩ := by
  simp [emitM,markMatchB,RCore.relate_self]

private theorem field_intersection (ac af dc df t : Nat) (ak dk : Kind) :
    emitM ⟨1,[2*dc,2*df+1],dk,.conc t⟩ ⟨1,[2*ac,2*af+1],ak,.conc t⟩ =
      if 2*dc = 2*ac ∧ 2*df = 2*af then
        some ⟨1,[2*ac,2*af+1],meetK ak dk,.conc t⟩ else none := by
  have reverseClass : 2*ac = 2*dc ↔ 2*dc = 2*ac := eq_comm
  have reverseField : 2*af = 2*df ↔ 2*df = 2*af := eq_comm
  by_cases hc : 2*dc = 2*ac <;> by_cases hf : 2*df = 2*af <;>
    simp_all [emitM,markMatchB,relate,dropPrefix]

private theorem class_guide_field_input_none (ac af dc t : Nat) (ak : Kind) :
    emitM ⟨1,[2*dc],.exact,.conc t⟩ ⟨1,[2*ac,2*af+1],ak,.conc t⟩ = none := by
  have reverseClass : 2*ac = 2*dc ↔ 2*dc = 2*ac := eq_comm
  by_cases hc : 2*dc = 2*ac <;>
    simp_all [emitM,markMatchB,relate,dropPrefix,admitsTailB]

private theorem field_guide_class_input_none (ac dc df t : Nat) (dk : Kind) :
    emitM ⟨1,[2*dc,2*df+1],dk,.conc t⟩ ⟨1,[2*ac],.exact,.conc t⟩ = none := by
  have reverseClass : 2*ac = 2*dc ↔ 2*dc = 2*ac := eq_comm
  by_cases hc : 2*dc = 2*ac <;>
    simp_all [emitM,markMatchB,relate,dropPrefix,admitsTailB]

private theorem class_intersection (ac dc t : Nat) :
    emitM ⟨1,[2*dc],.exact,.conc t⟩ ⟨1,[2*ac],.exact,.conc t⟩ =
      if 2*dc = 2*ac then some ⟨1,[2*ac],.exact,.conc t⟩ else none := by
  have reverseClass : 2*ac = 2*dc ↔ 2*dc = 2*ac := eq_comm
  by_cases hc : 2*dc = 2*ac <;>
    simp_all [emitM,markMatchB,relate,dropPrefix,meetK]

private theorem emit_raw_coverage (actual : Actual) (guide : Guide) (owner : Owner)
    (query : PFact) (emitted : oldExtra owner guide actual = some query) :
    query ∈ complete actual := by
  cases actual <;> cases guide <;>
    simp_all [oldExtra,oldExtraRaw,Actual.pattern,Run1Requests.concrete,
      Run1Requests.Point.base,Run1Requests.Point.path,complete,
      field_intersection,class_guide_field_input_none,field_guide_class_input_none,
      class_intersection,meetK]
  all_goals try simp_all [emitM,markMatchB,relate,dropPrefix,admitsTailB]
  all_goals split at emitted <;> simp_all

/-- Exact query identity is preserved, not just denoted location inclusion. -/
theorem old_extra_query_in_completion (owner : Owner) (actual : Actual) (guide : Guide)
    (query : PFact) (emitted : oldExtra owner guide actual = some query) :
    query ∈ complete actual := emit_raw_coverage actual guide owner query emitted

theorem old_query_batch_covered (owner : Owner) (guides : List Guide) (actual : Actual)
    (query : PFact) (present : query ∈ referenceQueries owner guides actual) :
    query ∈ complete actual := by
  simp only [referenceQueries,List.mem_cons] at present
  rcases present with same | extra
  · subst query; exact actual_query_preserved actual
  · obtain ⟨guide,_,emitted⟩ := List.mem_filterMap.mp extra
    exact old_extra_query_in_completion owner actual guide query emitted

def coveredQuery (owner : Owner) (guides : List Guide) (actual : Actual)
    (query : PFact) (present : query ∈ referenceQueries owner guides actual) :
    { witness : PFact // witness = query ∧ witness ∈ complete actual } :=
  ⟨query,rfl,old_query_batch_covered owner guides actual query present⟩

theorem completion_keeps_static_base (actual : Actual) (query : PFact)
    (present : query ∈ complete actual) : query.base = 1 := by
  cases actual <;> simp_all [complete,Actual.pattern,Run1Requests.concrete,Run1Requests.Point.base]
  rcases present with rfl | rfl <;> rfl

/-- The optional exact start is inside the actual fact. It does not create a
    new mark or a descendant field location from a broad descriptor. -/
theorem completion_adds_no_locations (actual : Actual) (query : PFact) (location : Loc)
    (present : query ∈ complete actual) (covered : query.covers location) :
    actual.pattern.covers location := by
  cases actual with
  | fieldExact c f t =>
      simp only [complete,List.mem_singleton] at present
      subst query
      exact covered
  | fieldAny c f t =>
      simp only [complete,List.mem_cons,List.not_mem_nil,or_false] at present
      rcases present with rfl | rfl
      · exact covered
      · exact ((RCore.emitM_inter (exact_completion_is_AP_intersection c f t)) location).mp covered |>.1
  | classExact c t =>
      simp only [complete,List.mem_singleton] at present
      subst query
      exact covered

namespace Counterexamples

def owner : Owner := fun _ => 7

theorem actual_only_misses_exact_field :
    oldExtra owner (.fieldExact 2 3 7) (.fieldAny 2 3 101) =
      some (.fieldExact 2 3 101 : Actual).pattern ∧
    (.fieldExact 2 3 101 : Actual).pattern ∉ [(.fieldAny 2 3 101 : Actual).pattern] := by decide

def rootAny : PFact := ⟨1,[],.any,.conc 101⟩
def classAny : PFact := ⟨1,[4],.any,.conc 101⟩
def samePathOnly (actual : PFact) : List PFact := [actual,{actual with kind := .exact}]

theorem broad_root_needs_a_descendant_query :
    oldExtraRaw owner (.fieldExact 2 3 7) rootAny =
      some (.fieldExact 2 3 101 : Actual).pattern ∧
    (.fieldExact 2 3 101 : Actual).pattern ∉ samePathOnly rootAny := by decide

theorem broad_class_needs_a_descendant_query :
    oldExtraRaw owner (.fieldExact 2 3 7) classAny =
      some (.fieldExact 2 3 101 : Actual).pattern ∧
    (.fieldExact 2 3 101 : Actual).pattern ∉ samePathOnly classAny := by decide

theorem raw_limit_one_does_not_exclude_broad_values :
    countedFields rootAny.path ≤ 1 ∧ countedFields classAny.path ≤ 1 := by decide

theorem broad_root_rejected : ¬ LegalActual rootAny := by
  rintro ⟨actual,same⟩
  cases actual <;> simp [Actual.pattern,Run1Requests.concrete,Run1Requests.Point.path,rootAny] at same

theorem broad_class_rejected : ¬ LegalActual classAny := by
  rintro ⟨actual,same⟩
  cases actual <;> simp [Actual.pattern,Run1Requests.concrete,Run1Requests.Point.path,classAny] at same

/-- Both same-owner FULL identifiers are copied independently. -/
theorem same_owner_different_fulls_stay_distinct :
    oldExtra owner (.fieldExact 2 3 7) (.fieldAny 2 3 101) =
      some (.fieldExact 2 3 101 : Actual).pattern ∧
    oldExtra owner (.fieldExact 2 3 7) (.fieldAny 2 3 102) =
      some (.fieldExact 2 3 102 : Actual).pattern ∧
    (.fieldExact 2 3 101 : Actual).pattern ≠ (.fieldExact 2 3 102 : Actual).pattern := by decide

theorem wrong_owner_has_no_extra_query :
    oldExtra owner (.fieldExact 2 3 8) (.fieldAny 2 3 101) = none := by decide

theorem exact_class_does_not_supply_a_field :
    oldExtra owner (.fieldAny 2 3 7) (.classExact 2 101) = none := by decide

theorem exact_class_guide_does_not_capture_field :
    oldExtra owner (.classExact 2 7) (.fieldAny 2 3 101) = none := by decide

end Counterexamples

theorem no_old_guide_required (owner : Owner) (actual : Actual) :
    actual.pattern ∈ complete actual ∧ referenceQueries owner [] actual = [actual.pattern] :=
  ⟨actual_query_preserved actual,rfl⟩

/-- Closed independent finite controls supplement the universally quantified
    coverage proofs; they are not a substitute for the production adapters. -/
def controls : List Bool := [
  decide (complete (.fieldExact 2 3 101) = [(.fieldExact 2 3 101 : Actual).pattern]),
  decide (complete (.fieldAny 2 3 101) =
    [(.fieldAny 2 3 101 : Actual).pattern,(.fieldExact 2 3 101 : Actual).pattern]),
  decide (complete (.classExact 2 101) = [(.classExact 2 101 : Actual).pattern]),
  decide (oldExtra Counterexamples.owner (.rootGroup 7) (.fieldAny 2 3 101) =
    some (.fieldAny 2 3 101 : Actual).pattern),
  decide (oldExtra Counterexamples.owner (.fieldAny 2 4 7) (.fieldAny 2 3 101) = none),
  decide (oldExtra Counterexamples.owner (.fieldAny 4 3 7) (.fieldAny 2 3 101) = none),
  decide (oldExtra Counterexamples.owner (.rootFlow) (.fieldAny 2 3 101) = none),
  decide (oldExtra Counterexamples.owner (.fieldFlow 2 3) (.fieldAny 2 3 101) = none)
]

theorem controls_pass : controls.all id = true := by decide

end ApSpec.Run1StaticQueryCompletion
