/-
  Executable counterexample for a strict B2 mark-demand gate. This is not a
  selected repair. Positions are finite opaque geometry keys. ANY and STAR
  name the required query/premise tags; this model proves no AP transfer law.
  A summary-only handoff preserves the summary's FLOW/group selector.
  Emissions are backward demand queries, not evidence of actual taint.
-/
import Std
import ApSpec.RuleGroupMarks

namespace ApSpec.Run1StaticDemandGateGap
set_option autoImplicit false

abbrev Method := Nat
abbrev Group := Nat
abbrev FullMark := RuleGroupMarks.OwnedMark

inductive Base where
  | returned
  | staticField (field : Nat)
  | parameter (index : Nat)
  | point (identity : Nat)
  deriving DecidableEq, Repr

inductive Tail where
  | any | star
  deriving DecidableEq, Repr

structure Position where
  base : Base
  tail : Tail
  deriving DecidableEq, Repr

inductive Selector where
  | flow
  | group (owner : Group)
  deriving DecidableEq, Repr

def accepts : Selector → FullMark → Bool
  | .flow, _ => false
  | .group group, mark => group == mark.group

theorem flow_never_matches (mark : FullMark) : accepts .flow mark = false := rfl

theorem group_matches_owner (group : Group) (mark : FullMark) :
    accepts (.group group) mark = true ↔ group = mark.group := by simp [accepts]

structure ForwardSummary where
  method : Method
  initial : Position
  result : Position
  selector : Selector
  deriving DecidableEq, Repr

structure Guide where
  method : Method
  input : Position
  into : Position
  selector : Selector
  deriving DecidableEq, Repr

/-- The existing selector is retained, rather than changed into every group. -/
def reverseSummary (summary : ForwardSummary) : Guide :=
  ⟨summary.method, summary.result, summary.initial, summary.selector⟩

def summaryHandoff (summaries : List ForwardSummary) : List Guide :=
  summaries.map reverseSummary

structure Query where
  method : Method
  position : Position
  mark : FullMark
  deriving DecidableEq, Repr

structure Emission where
  origin : Query
  into : Position
  mark : FullMark
  deriving DecidableEq, Repr

def compatible (guide : Guide) (query : Query) : Bool :=
  guide.method == query.method && guide.input == query.position &&
    accepts guide.selector query.mark

def instantiate (guide : Guide) (query : Query) : Emission :=
  ⟨query, guide.into, query.mark⟩

def emit (guides : List Guide) (query : Query) : List Emission :=
  (guides.filter (fun guide => compatible guide query)).map
    (fun guide => instantiate guide query)

def admitted (guides : List Guide) (query : Query) : Bool :=
  guides.any (fun guide => compatible guide query)

theorem compatible_iff (guide : Guide) (query : Query) :
    compatible guide query = true ↔
      guide.method = query.method ∧ guide.input = query.position ∧
        accepts guide.selector query.mark = true := by
  simp [compatible, Bool.and_eq_true, and_assoc]

theorem grouped_guide_accepts_every_owner (method : Method) (input into : Position)
    (group : Group) (mark : FullMark) (sameOwner : group = mark.group) :
    compatible ⟨method, input, into, .group group⟩ ⟨method, input, mark⟩ = true := by
  exact (compatible_iff _ _).mpr ⟨rfl, rfl, (group_matches_owner group mark).mpr sameOwner⟩

/-- An admitted exact full mark is retained on the reversed edge. -/
theorem emission_keeps_full_mark (guide : Guide) (query : Query) :
    (instantiate guide query).mark = query.mark ∧
      (instantiate guide query).origin = query := ⟨rfl, rfl⟩

def emissionWitness (guides : List Guide) (query : Query) (guide : Guide)
    (registered : guide ∈ guides) (matching : compatible guide query = true) :
    { edge : Emission // edge ∈ emit guides query ∧ edge.into = guide.into } :=
  ⟨instantiate guide query, List.mem_map.mpr
    ⟨guide, List.mem_filter.mpr ⟨registered, matching⟩, rfl⟩, rfl⟩

def FlowOnlyFor (guides : List Guide) (method : Method) : Prop :=
  ∀ guide, guide ∈ guides → guide.method = method → guide.selector = .flow

theorem flow_only_no_admission (guides : List Guide) (query : Query)
    (only : FlowOnlyFor guides query.method) : admitted guides query = false := by
  cases equation : admitted guides query with
  | false => rfl
  | true =>
      obtain ⟨guide, present, matching⟩ := List.any_eq_true.mp equation
      have facts := (compatible_iff guide query).mp matching
      have selector := only guide present facts.1
      simp [selector, accepts] at facts

theorem flow_only_no_emission (guides : List Guide) (query : Query)
    (only : FlowOnlyFor guides query.method) : emit guides query = [] := by
  cases equation : emit guides query with
  | nil => rfl
  | cons edge tail =>
      have present : edge ∈ emit guides query := by simp [equation]
      obtain ⟨guide, filtered, _⟩ := List.mem_map.mp present
      have valid := List.mem_filter.mp filtered
      have facts := (compatible_iff guide query).mp valid.2
      have selector := only guide valid.1 facts.1
      simp [selector, accepts] at facts

/-- Ordinary geometry normalization can change AP keys, but cannot create a
    mark-demand selector or move a row into another method's demand store. -/
def normalizeGuide (geometry : Position → Position) (guide : Guide) : Guide :=
  { guide with input := geometry guide.input, into := geometry guide.into }

def normalize (geometry : Position → Position) (guides : List Guide) : List Guide :=
  (guides.map (normalizeGuide geometry)).eraseDups

theorem normalization_keeps_selector (geometry : Position → Position) (guide : Guide) :
    (normalizeGuide geometry guide).selector = guide.selector := rfl

theorem normalization_keeps_method (geometry : Position → Position) (guide : Guide) :
    (normalizeGuide geometry guide).method = guide.method := rfl

theorem flow_only_after_normalization (geometry : Position → Position) (guides : List Guide)
    (method : Method) (only : FlowOnlyFor guides method) :
    FlowOnlyFor (normalize geometry guides) method := by
  intro guide present sameMethod
  obtain ⟨before, registered, eq⟩ := List.mem_map.mp (List.mem_eraseDups.mp present)
  subst guide
  exact only before registered sameMethod

theorem normalization_does_not_fill_gap (geometry : Position → Position)
    (guides : List Guide) (query : Query) (only : FlowOnlyFor guides query.method) :
    emit (normalize geometry guides) { query with position := geometry query.position } = [] :=
  flow_only_no_emission _ _ (flow_only_after_normalization geometry guides query.method only)

namespace Counterexample

def caller : Method := 0
def getter : Method := 1
def helper : Method := 2
def nonReturning : Method := 3
def owner : Group := 5
def fullMark : FullMark := ⟨owner, 501⟩
def otherFullMark : FullMark := ⟨owner, 502⟩
def wrongOwnerMark : FullMark := ⟨9, 901⟩

def ret : Position := ⟨.returned, .any⟩
def field : Position := ⟨.staticField 7, .star⟩
def parameter : Position := ⟨.parameter 0, .star⟩
def sink : Position := ⟨.point 99, .any⟩

def getterFlow : ForwardSummary := ⟨getter, field, ret, .flow⟩
def helperFlow : ForwardSummary := ⟨helper, parameter, ret, .flow⟩
def summaries : List ForwardSummary := [getterFlow, helperFlow]

/-- The caller has a point-refined grouped candidate at its sink. It is not
    an additional grouped summary in the getter or the ordinary helper. -/
def sinkGuide : Guide := ⟨caller, sink, field, .group owner⟩
def baseline : List Guide := sinkGuide :: summaryHandoff summaries

def getterQuery : Query := ⟨getter, ret, fullMark⟩
def helperQuery : Query := ⟨helper, ret, fullMark⟩
def sinkQuery : Query := ⟨caller, sink, fullMark⟩

/-- These are obligations that the baseline lacks. Their constructors do not
    decide whether frozen handoff or query-time admission must provide them. -/
def requiredGetter : Guide := ⟨getter, ret, field, .group owner⟩
def requiredHelper : Guide := ⟨helper, ret, parameter, .group owner⟩
def getterAdded : List Guide := requiredGetter :: baseline
def bothAdded : List Guide := requiredHelper :: getterAdded

def sourceSupplied : List (Nat × Group) := [(7, owner)]

theorem getter_required_pair : requiredGetter =
    ⟨getter, ⟨.returned, .any⟩, ⟨.staticField 7, .star⟩, .group owner⟩ := rfl

theorem helper_required_pair : requiredHelper =
    ⟨helper, ⟨.returned, .any⟩, ⟨.parameter 0, .star⟩, .group owner⟩ := rfl

theorem getter_baseline_flow_only : FlowOnlyFor baseline getter := by
  intro guide present same
  simp [baseline, summaryHandoff, summaries, reverseSummary] at present
  rcases present with eq | eq | eq
  · subst guide; simp [sinkGuide, caller, getter] at same
  · subst guide; rfl
  · subst guide; rfl

theorem helper_missing_after_getter_added : FlowOnlyFor getterAdded helper := by
  intro guide present same
  simp [getterAdded, baseline, summaryHandoff, summaries, reverseSummary] at present
  rcases present with eq | eq | eq | eq
  · subst guide; simp [requiredGetter, getter, helper] at same
  · subst guide; simp [sinkGuide, caller, helper] at same
  · subst guide; rfl
  · subst guide; rfl

theorem getter_cannot_emit : emit baseline getterQuery = [] :=
  flow_only_no_emission baseline getterQuery getter_baseline_flow_only

theorem helper_still_cannot_emit : emit getterAdded helperQuery = [] :=
  flow_only_no_emission getterAdded helperQuery helper_missing_after_getter_added

theorem getter_gap_survives_normalization (geometry : Position → Position) :
    emit (normalize geometry baseline) { getterQuery with position := geometry ret } = [] :=
  normalization_does_not_fill_gap geometry baseline getterQuery getter_baseline_flow_only

theorem source_sink_and_flow_do_not_fill_getter_gap :
    (7, owner) ∈ sourceSupplied ∧ getterFlow ∈ summaries ∧
      admitted baseline sinkQuery = true ∧ admitted baseline getterQuery = false := by decide

def controls : List (String × Bool) :=
  [ ("actual source group is present", sourceSupplied.contains (7, owner))
  , ("getter structural summary is present", summaries.contains getterFlow)
  , ("helper structural summary is present", summaries.contains helperFlow)
  , ("caller sink group demand is present", admitted baseline sinkQuery)
  , ("getter group demand is absent", !(admitted baseline getterQuery))
  , ("getter full-mark emission is blocked", (emit baseline getterQuery).isEmpty)
  , ("FLOW survives summary reversal", (reverseSummary getterFlow).selector == .flow)
  , ("getter reversal has return ANY input", (reverseSummary getterFlow).input == ret)
  , ("getter reversal has static STAR output", (reverseSummary getterFlow).into == field)
  , ("caller demand does not become getter demand", !(compatible sinkGuide getterQuery))
  , ("normalization cannot invent getter group", !(admitted (normalize id baseline) getterQuery))
  , ("adding getter group demand admits getter", admitted getterAdded getterQuery)
  , ("getter group demand emits static predecessor", (emit getterAdded getterQuery).contains
      ⟨getterQuery, field, fullMark⟩)
  , ("getter group demand alone leaves helper blocked", !(admitted getterAdded helperQuery))
  , ("helper group demand has return ANY input", requiredHelper.input == ret)
  , ("helper group demand has parameter STAR output", requiredHelper.into == parameter)
  , ("adding helper demand admits ordinary helper", admitted bothAdded helperQuery)
  , ("helper demand emits parameter predecessor", (emit bothAdded helperQuery).contains
      ⟨helperQuery, parameter, fullMark⟩)
  , ("group selector accepts all full members", accepts (.group owner) otherFullMark)
  , ("unseen full member uses the same getter group demand", admitted getterAdded
      { getterQuery with mark := otherFullMark })
  , ("unseen full member is emitted in full", (emit getterAdded
      { getterQuery with mark := otherFullMark }).contains
        ⟨{ getterQuery with mark := otherFullMark }, field, otherFullMark⟩)
  , ("group selector rejects another owner", !(accepts (.group owner) wrongOwnerMark))
  , ("FLOW selector rejects full query", !(accepts .flow fullMark))
  , ("full mark is kept after admission", (emit bothAdded helperQuery).all
      (fun edge => edge.mark == fullMark))
  , ("source group alone has no method guide", (emit [] getterQuery).isEmpty)
  , ("nonreturning point has no method-return guide", !(admitted (summaryHandoff [])
      ⟨nonReturning, sink, fullMark⟩))
  , ("nonreturning point needs its own point demand", admitted
      [⟨nonReturning, sink, field, .group owner⟩] ⟨nonReturning, sink, fullMark⟩)
  ]

theorem controls_pass : controls.all Prod.snd = true := by decide

end Counterexample
end ApSpec.Run1StaticDemandGateGap
