/-
  Finite signed NNF at one fixed AP/condition observation point. Every literal
  below has that same position. For different positions, the adapter must
  retain each position: Contains(x,T) and Contains(y,T) are not one literal.
  This module does not model multi-position ND or the bound-rule compiler.

  The caller supplies
  the original signed-folded NNF: in particular, a proved full-literal
  contradiction is already `falsity`. This module does not implement a
  normalizer. It retains full literals rather than replacing them by groups.

  The possible evaluator is the selected coarse residual projection:
  positive literals test a possible owner group; residual negative literals
  are true. It proves coverage, not exact condition truth. Its source and
  observation outputs are DEMAND candidates with the original condition.

  Lists present finite membership sets. groupProjection comes only from the
  actual full-mark presence list; the more general theorem permits a covering
  group list. Registry provenance, eligible-negative compilation, AP/ND
  support, Kotlin adapters and later backward validation remain obligations.
-/
import ApSpec.RuleGroupMarks

namespace ApSpec.RuleGroupConditions
set_option autoImplicit false

open RuleGroupMarks (OwnedMark)

inductive SignedNNF where
  | truth
  | falsity
  | positive (literal : OwnedMark)
  | negative (literal : OwnedMark)
  | conj (left right : SignedNNF)
  | disj (left right : SignedNNF)
  deriving DecidableEq, Repr

/-- Membership uses the complete OwnedMark, not its group alone. -/
def exactEval (actual : List OwnedMark) : SignedNNF → Bool
  | .truth => true
  | .falsity => false
  | .positive literal => decide (literal ∈ actual)
  | .negative literal => !(decide (literal ∈ actual))
  | .conj left right => exactEval actual left && exactEval actual right
  | .disj left right => exactEval actual left || exactEval actual right

def groupProjection (actual : List OwnedMark) : List Nat :=
  actual.map OwnedMark.group

def dedupGroupProjection (actual : List OwnedMark) : List Nat :=
  (groupProjection actual).eraseDups

/-- Exact equality for a group query, not equality of full-mark identities. -/
theorem scalar_group_query_eq_projection (actual : List OwnedMark) (group : Nat) :
    actual.any (fun mark => mark.group == group) = (groupProjection actual).contains group := by
  apply Bool.eq_iff_iff.mpr
  simp only [List.any_eq_true,List.contains_iff_mem,groupProjection,List.mem_map,beq_iff_eq]

theorem projection_query_eq_dedup (actual : List OwnedMark) (group : Nat) :
    (groupProjection actual).contains group = (dedupGroupProjection actual).contains group := by
  apply Bool.eq_iff_iff.mpr
  simp [dedupGroupProjection]

theorem scalar_group_query_eq_dedup (actual : List OwnedMark) (group : Nat) :
    actual.any (fun mark => mark.group == group) = (dedupGroupProjection actual).contains group :=
  (scalar_group_query_eq_projection actual group).trans
    (projection_query_eq_dedup actual group)

/-- Negatives here are eligible residuals of the unchanged full-literal NNF.
    They are projected to possible, not proved absent. A folded false stays
    false. This definition must not be used as a full-mark truth test. -/
def possibleEval (groups : List Nat) : SignedNNF → Bool
  | .truth => true
  | .falsity => false
  | .positive literal => decide (literal.group ∈ groups)
  | .negative _ => true
  | .conj left right => possibleEval groups left && possibleEval groups right
  | .disj left right => possibleEval groups left || possibleEval groups right

theorem possible_eval_dedup_projection (actual : List OwnedMark) (condition : SignedNNF) :
    possibleEval (groupProjection actual) condition =
      possibleEval (dedupGroupProjection actual) condition := by
  induction condition with
  | truth => rfl
  | falsity => rfl
  | positive literal => simp [possibleEval,dedupGroupProjection]
  | negative _ => rfl
  | conj _ _ ihLeft ihRight => simp [possibleEval,ihLeft,ihRight]
  | disj _ _ ihLeft ihRight => simp [possibleEval,ihLeft,ihRight]

def GroupsCover (actual : List OwnedMark) (groups : List Nat) : Prop :=
  ∀ literal, literal ∈ actual → literal.group ∈ groups

theorem actual_projection_covers (actual : List OwnedMark) :
    GroupsCover actual (groupProjection actual) := by
  intro literal present
  exact List.mem_map.mpr ⟨literal,present,rfl⟩

/-- Structural coverage for the same original formula on both sides. No
    literal, conjunction or disjunction is merged by group identity. -/
theorem exact_truth_implies_possible_truth
    (actual : List OwnedMark) (groups : List Nat)
    (cover : GroupsCover actual groups) (condition : SignedNNF) :
    exactEval actual condition = true → possibleEval groups condition = true := by
  induction condition with
  | truth => intro _; rfl
  | falsity => intro impossible; cases impossible
  | positive literal =>
      intro present
      have member : literal ∈ actual := of_decide_eq_true present
      simp [possibleEval,cover literal member]
  | negative _ => intro _; rfl
  | conj left right ihLeft ihRight =>
      intro hit
      have both := Bool.and_eq_true_iff.mp hit
      exact Bool.and_eq_true_iff.mpr ⟨ihLeft both.1,ihRight both.2⟩
  | disj left right ihLeft ihRight =>
      intro hit
      rcases Bool.or_eq_true_iff.mp hit with leftHit | rightHit
      · exact Bool.or_eq_true_iff.mpr (Or.inl (ihLeft leftHit))
      · exact Bool.or_eq_true_iff.mpr (Or.inr (ihRight rightHit))

theorem projected_truth_coverage (actual : List OwnedMark) (condition : SignedNNF)
    (hit : exactEval actual condition = true) :
    possibleEval (groupProjection actual) condition = true :=
  exact_truth_implies_possible_truth actual (groupProjection actual)
    (actual_projection_covers actual) condition hit

theorem dedup_projected_truth_coverage (actual : List OwnedMark) (condition : SignedNNF)
    (hit : exactEval actual condition = true) :
    possibleEval (dedupGroupProjection actual) condition = true := by
  rw [← possible_eval_dedup_projection actual condition]
  exact projected_truth_coverage actual condition hit

@[simp] theorem folded_false_stays_false (groups : List Nat) :
    possibleEval groups .falsity = false := rfl

theorem exact_full_literal_contradiction (actual : List OwnedMark) (literal : OwnedMark) :
    exactEval actual (.conj (.positive literal) (.negative literal)) = false := by
  simp [exactEval]

/-- This witness is why the caller must fold full contradictions before the
    residual projection. The raw contradictory formula is not a normal form. -/
theorem raw_contradiction_can_be_possible (literal : OwnedMark) :
    possibleEval [literal.group]
      (.conj (.positive literal) (.negative literal)) = true := by
  simp [possibleEval]

/-- Two distinct full IDs remain distinct even when their owner is equal. -/
theorem distinct_full_literals_can_hold (positive negative : OwnedMark)
    (different : positive.full ≠ negative.full) :
    exactEval [positive] (.conj (.positive positive) (.negative negative)) = true := by
  have ne : negative ≠ positive := by
    intro same
    exact different (congrArg OwnedMark.full same).symm
  simp [exactEval,ne]

theorem same_group_distinct_pair_not_false (positive negative : OwnedMark)
    (_sameGroup : positive.group = negative.group)
    (different : positive.full ≠ negative.full) :
    exactEval [positive] (.conj (.positive positive) (.negative negative)) = true ∧
      exactEval [positive] .falsity = false :=
  ⟨distinct_full_literals_can_hold positive negative different,rfl⟩

inductive CandidateRole where
  | source
  | observation
  deriving DecidableEq, Repr

structure DemandCandidate where
  role : CandidateRole
  originalCondition : SignedNNF
  demand : Bool
  deriving DecidableEq, Repr

def emitCandidate (role : CandidateRole) (groups : List Nat)
    (condition : SignedNNF) : Option DemandCandidate :=
  if possibleEval groups condition then
    some ⟨role,condition,true⟩
  else none

theorem possible_emits_demand (role : CandidateRole) (groups : List Nat)
    (condition : SignedNNF) (hit : possibleEval groups condition = true) :
    emitCandidate role groups condition = some ⟨role,condition,true⟩ := by
  simp [emitCandidate,hit]

theorem exact_reached_emits_demand (role : CandidateRole) (actual : List OwnedMark)
    (condition : SignedNNF) (hit : exactEval actual condition = true) :
    emitCandidate role (groupProjection actual) condition = some ⟨role,condition,true⟩ :=
  possible_emits_demand role (groupProjection actual) condition
    (projected_truth_coverage actual condition hit)

theorem emitted_candidate_keeps_role_condition_and_demand
    (role : CandidateRole) (groups : List Nat) (condition : SignedNNF)
    (candidate : DemandCandidate) (emitted : emitCandidate role groups condition = some candidate) :
    candidate.role = role ∧ candidate.originalCondition = condition ∧ candidate.demand = true := by
  unfold emitCandidate at emitted
  split at emitted
  · cases emitted
    exact ⟨rfl,rfl,rfl⟩
  · cases emitted

theorem no_normal_candidate (role : CandidateRole) (groups : List Nat)
    (condition : SignedNNF) (candidate : DemandCandidate)
    (emitted : emitCandidate role groups condition = some candidate) :
    candidate.demand ≠ false := by
  have demand := (emitted_candidate_keeps_role_condition_and_demand
    role groups condition candidate emitted).2.2
  simp [demand]

namespace Controls

def t : OwnedMark := ⟨7,10⟩
def u : OwnedMark := ⟨7,11⟩
def v : OwnedMark := ⟨8,20⟩

def isDemand : Option DemandCandidate → Bool
  | some candidate => candidate.demand
  | none => false

def controls : List (String × Bool) := [
  ("positive-present-and-deduplicated-queries", exactEval [t] (.positive t) &&
    possibleEval (groupProjection [t]) (.positive t) &&
    decide (dedupGroupProjection [t,u,t,v] = [7,8]) &&
    (dedupGroupProjection [t,u,t,v]).contains 7 &&
    !((dedupGroupProjection [t,u,t,v]).contains 9)),
  ("negative-absent-covered", exactEval [t] (.negative u) &&
    possibleEval (groupProjection [t]) (.negative u)),
  ("different-owner-positive-rejected", !(possibleEval (groupProjection [t]) (.positive v))),
  ("raw-contradiction-requires-full-fold", !(exactEval [t] (.conj (.positive t) (.negative t))) &&
    possibleEval (groupProjection [t]) (.conj (.positive t) (.negative t))),
  ("already-folded-false-stays-false", !(possibleEval [7] .falsity)),
  ("same-group-distinct-full-literals", exactEval [t] (.conj (.positive t) (.negative u)) &&
    possibleEval (groupProjection [t]) (.conj (.positive t) (.negative u))),
  ("nested-and-or-covered", exactEval [t,v]
    (.conj (.positive t) (.disj (.positive v) (.negative u))) &&
    possibleEval (groupProjection [t,v])
      (.conj (.positive t) (.disj (.positive v) (.negative u)))),
  ("negative-residual-is-not-truth", !(exactEval [t] (.negative t)) &&
    possibleEval (groupProjection [t]) (.negative t)),
  ("source-possible-output-is-demand", isDemand (emitCandidate .source [7] (.positive u))),
  ("observation-residual-output-is-demand", isDemand (emitCandidate .observation [7] (.negative t)))
]

end Controls
end ApSpec.RuleGroupConditions
