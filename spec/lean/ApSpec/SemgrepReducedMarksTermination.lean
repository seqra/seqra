import ApSpec.SemgrepReducedMarks

/-
A total fixed-point computation for the SAME execution-only normalization step.
Every changed round strictly decreases a finite structural natural measure:
CM costs two; constants/other atoms cost one; connectors add one; GEN/opaque
ordered action slots each cost one. Unchanged metadata/IDs contribute no
varying measure. This does not claim truth-set monotonicity: NOT CM can become
true. The executable bound is structural, not a time/heap/binding-product bound.
-/
namespace ApSpec.SemgrepReducedMarksTermination
set_option autoImplicit false
set_option maxHeartbeats 4000000
open ApSpec.SemgrepReducedMarks

theorem mkNot_same_or_strict (e : Expr) :
    mkNot e = .neg e ∨ weight (mkNot e) < weight (.neg e) := by
  cases e <;> simp [mkNot,weight]

theorem mkAnd_same_or_strict (a b : Expr) :
    mkAnd a b = .conj a b ∨ weight (mkAnd a b) < weight (.conj a b) := by
  cases a <;> cases b <;> simp [mkAnd,weight] <;> split <;> simp_all [weight] <;> omega

theorem mkOr_same_or_strict (a b : Expr) :
    mkOr a b = .disj a b ∨ weight (mkOr a b) < weight (.disj a b) := by
  cases a <;> cases b <;> simp [mkOr,weight] <;> split <;> simp_all [weight] <;> omega

theorem fold_same_or_strict (available : List FullMark) (e : Expr) :
    fold available e = e ∨ weight (fold available e) < weight e := by
  induction e with
  | truth b => exact Or.inl rfl
  | other id => exact Or.inl rfl
  | cm p m =>
      by_cases h : m ∈ available
      · exact Or.inl (by simp [fold,h])
      · exact Or.inr (by simp [fold,h,weight])
  | neg e ih =>
      rcases ih with same | smaller
      · simpa only [fold,same] using mkNot_same_or_strict e
      · right
        have bound := weight_mkNot (fold available e)
        simp only [fold,weight]
        omega
  | conj a b ia ib =>
      rcases ia with sameA | smallerA
      · rcases ib with sameB | smallerB
        · simpa only [fold,sameA,sameB] using mkAnd_same_or_strict a b
        · right
          have bound := weight_mkAnd (fold available a) (fold available b)
          have equalA : weight (fold available a) = weight a := congrArg weight sameA
          simp only [fold,weight]
          omega
      · right
        have bound := weight_mkAnd (fold available a) (fold available b)
        have boundB := fold_weight_nonincrease available b
        simp only [fold,weight]
        omega
  | disj a b ia ib =>
      rcases ia with sameA | smallerA
      · rcases ib with sameB | smallerB
        · simpa only [fold,sameA,sameB] using mkOr_same_or_strict a b
        · right
          have bound := weight_mkOr (fold available a) (fold available b)
          have equalA : weight (fold available a) = weight a := congrArg weight sameA
          simp only [fold,weight]
          omega
      · right
        have bound := weight_mkOr (fold available a) (fold available b)
        have boundB := fold_weight_nonincrease available b
        simp only [fold,weight]
        omega

theorem prune_same_or_strict (needed : List FullMark) (actions : List Action) :
    pruneActions needed actions = actions ∨
      (pruneActions needed actions).length < actions.length := by
  by_cases same : pruneActions needed actions = actions
  · exact Or.inl same
  · right
    have sub := pruneActions_sublist needed actions
    have bound := sub.length_le
    have different : (pruneActions needed actions).length ≠ actions.length :=
      fun equal => same (sub.eq_of_length equal)
    omega

def ruleWeight (r : Rule) : Nat := weight r.guard + r.actions.length

def programWeight : List Rule → Nat
  | [] => 0
  | r :: rs => ruleWeight r + programWeight rs

theorem rewrite_rule_same_or_strict (available needed : List FullMark) (r : Rule) :
    rewriteRule available needed r = r ∨
      ruleWeight (rewriteRule available needed r) < ruleWeight r := by
  rcases fold_same_or_strict available r.guard with sameGuard | smallerGuard
  · rcases prune_same_or_strict needed r.actions with sameActions | smallerActions
    · left
      cases r
      simp_all [rewriteRule]
    · right
      have sameWeight := congrArg weight sameGuard
      simp only [ruleWeight,rewriteRule]
      omega
  · right
    have actionBound := prune_action_slots_nonincrease needed r.actions
    simp only [ruleWeight,rewriteRule]
    omega

theorem rewrite_rows_same_or_strict (available needed : List FullMark) (rules : List Rule) :
    rules.map (rewriteRule available needed) = rules ∨
      programWeight (rules.map (rewriteRule available needed)) < programWeight rules := by
  induction rules with
  | nil => exact Or.inl rfl
  | cons r rs ih =>
      rcases rewrite_rule_same_or_strict available needed r with sameRow | smallerRow
      · rcases ih with sameTail | smallerTail
        · left
          simp only [List.map_cons,sameRow,sameTail]
        · right
          simp only [List.map_cons,programWeight,sameRow]
          omega
      · right
        rcases ih with sameTail | smallerTail
        · simp only [List.map_cons,programWeight,sameTail]
          omega
        · simp only [List.map_cons,programWeight]
          omega

theorem step_same_or_strict (policy : Policy) (rules : List Rule) :
    step policy rules = rules ∨ programWeight (step policy rules) < programWeight rules := by
  by_cases all : policy.protectAll = true
  · exact Or.inl (protectAll_step policy rules all)
  · simpa [step,all] using
      rewrite_rows_same_or_strict (assigned policy rules) (checked policy rules) rules

theorem step_weight_nonincrease (policy : Policy) (rules : List Rule) :
    programWeight (step policy rules) ≤ programWeight rules := by
  rcases step_same_or_strict policy rules with same | smaller
  · simp [same]
  · exact Nat.le_of_lt smaller

theorem changed_step_decreases (policy : Policy) (rules : List Rule)
    (changed : step policy rules ≠ rules) :
    programWeight (step policy rules) < programWeight rules := by
  rcases step_same_or_strict policy rules with same | smaller
  · exact False.elim (changed same)
  · exact smaller

theorem iterate_fixed (policy : Policy) (fuel : Nat) (rules : List Rule)
    (fixed : step policy rules = rules) : iterate policy fuel rules = rules := by
  induction fuel with
  | zero => rfl
  | succ n ih => simpa only [iterate,fixed] using ih

/-- A concrete structural bound: by weight+1 rounds the exact step is fixed.
    There is no arbitrary external fuel and no old-program fallback here. -/
theorem sufficient_rounds_fixed (policy : Policy) (fuel : Nat) (rules : List Rule)
    (enough : programWeight rules < fuel) :
    step policy (iterate policy fuel rules) = iterate policy fuel rules := by
  induction fuel generalizing rules with
  | zero => omega
  | succ n ih =>
      by_cases fixed : step policy rules = rules
      · rw [iterate_fixed policy (n+1) rules fixed]
        exact fixed
      · have smaller := changed_step_decreases policy rules fixed
        exact ih (step policy rules) (by omega)

/-- Stop when executable equality detects stability. The numeric bound is
    derived from the catalog, not an arbitrary configuration/fallback knob. -/
def stopAtFixed (policy : Policy) : Nat → List Rule → List Rule
  | 0, rules => rules
  | n + 1, rules =>
      if step policy rules = rules then rules else stopAtFixed policy n (step policy rules)

theorem stopAtFixed_eq_iterate (policy : Policy) (fuel : Nat) (rules : List Rule) :
    stopAtFixed policy fuel rules = iterate policy fuel rules := by
  induction fuel generalizing rules with
  | zero => rfl
  | succ n ih =>
      by_cases fixed : step policy rules = rules
      · simp only [stopAtFixed,if_pos fixed]
        exact (iterate_fixed policy (n+1) rules fixed).symm
      · simp only [stopAtFixed,if_neg fixed,iterate]
        exact ih (step policy rules)

/-- Total automatic normalization, with early stop. The sole step is the
    original simultaneous CM-fold/ordered-GEN-prune operation. -/
def normalizeTotal (policy : Policy) (rules : List Rule) : List Rule :=
  stopAtFixed policy (programWeight rules + 1) rules

theorem normalizeTotal_eq_iterate (policy : Policy) (rules : List Rule) :
    normalizeTotal policy rules = iterate policy (programWeight rules + 1) rules :=
  stopAtFixed_eq_iterate policy _ rules

theorem normalizeTotal_fixed (policy : Policy) (rules : List Rule) :
    step policy (normalizeTotal policy rules) = normalizeTotal policy rules := by
  rw [normalizeTotal_eq_iterate]
  exact sufficient_rounds_fixed policy (programWeight rules + 1) rules (by omega)

theorem normalizeTotal_eq_verified_bound (policy : Policy) (rules : List Rule) :
    normalize policy (programWeight rules + 1) rules = normalizeTotal policy rules := by
  have fixed := normalizeTotal_fixed policy rules
  rw [normalizeTotal_eq_iterate] at fixed ⊢
  simp [normalize,fixed]

theorem normalizeTotal_idempotent (policy : Policy) (rules : List Rule) :
    normalizeTotal policy (normalizeTotal policy rules) = normalizeTotal policy rules := by
  rw [normalizeTotal_eq_iterate]
  exact iterate_fixed policy _ _ (normalizeTotal_fixed policy rules)

/-- Strictly changing rounds; the final equality test is counted separately. -/
def changedRounds (policy : Policy) : Nat → List Rule → Nat
  | 0, _ => 0
  | n + 1, rules =>
      if step policy rules = rules then 0 else 1 + changedRounds policy n (step policy rules)

def equalityChecks (policy : Policy) : Nat → List Rule → Nat
  | 0, _ => 0
  | n + 1, rules =>
      if step policy rules = rules then 1 else 1 + equalityChecks policy n (step policy rules)

theorem changed_rounds_bounded (policy : Policy) (fuel : Nat) (rules : List Rule) :
    changedRounds policy fuel rules ≤ programWeight rules := by
  induction fuel generalizing rules with
  | zero => simp [changedRounds]
  | succ n ih =>
      by_cases fixed : step policy rules = rules
      · simp [changedRounds,fixed]
      · have smaller := changed_step_decreases policy rules fixed
        have tail := ih (step policy rules)
        simp only [changedRounds,if_neg fixed]
        omega

theorem equality_checks_bounded (policy : Policy) (fuel : Nat) (rules : List Rule) :
    equalityChecks policy fuel rules ≤ programWeight rules + 1 := by
  induction fuel generalizing rules with
  | zero => simp [equalityChecks]
  | succ n ih =>
      by_cases fixed : step policy rules = rules
      · simp [equalityChecks,fixed]
      · have smaller := changed_step_decreases policy rules fixed
        have tail := ih (step policy rules)
        simp only [equalityChecks,if_neg fixed]
        omega

theorem normalizeTotal_row_identities (policy : Policy) (rules : List Rule) :
    (normalizeTotal policy rules).map (fun r => (r.itemId,r.positionMetadata)) =
      rules.map (fun r => (r.itemId,r.positionMetadata)) := by
  rw [normalizeTotal_eq_iterate]
  exact iterate_row_identities policy _ rules

theorem normalizeTotal_checked_subset (policy : Policy) (rules : List Rule) (m : FullMark) :
    m ∈ checked policy (normalizeTotal policy rules) → m ∈ checked policy rules := by
  rw [normalizeTotal_eq_iterate]
  exact iterate_checked_subset policy _ rules m

theorem normalizeTotal_assigned_subset (policy : Policy) (rules : List Rule) (m : FullMark) :
    m ∈ assigned policy (normalizeTotal policy rules) → m ∈ assigned policy rules := by
  rw [normalizeTotal_eq_iterate]
  exact iterate_assigned_subset policy _ rules m

theorem normalizeTotal_protectAll (policy : Policy) (rules : List Rule)
    (all : policy.protectAll = true) : normalizeTotal policy rules = rules := by
  rw [normalizeTotal_eq_iterate]
  exact protectAll_iterate policy _ rules all

theorem normalizeTotal_cm_has_producer (policy : Policy) (rules : List Rule)
    (closed : policy.protectAll = false) (r : Rule) (member : r ∈ normalizeTotal policy rules)
    (m : FullMark) (used : m ∈ uses r.guard) :
    m ∈ assigned policy (normalizeTotal policy rules) :=
  fixed_cm_has_producer policy _ closed (normalizeTotal_fixed policy rules) r member m used

theorem normalizeTotal_gen_is_checked (policy : Policy) (rules : List Rule)
    (closed : policy.protectAll = false) (r : Rule) (member : r ∈ normalizeTotal policy rules)
    (id : Nat) (target : Position) (m : FullMark) (payload : Nat)
    (action : Action.gen id target m payload ∈ r.actions) :
    m ∈ checked policy (normalizeTotal policy rules) :=
  fixed_gen_is_checked policy _ closed (normalizeTotal_fixed policy rules) r member id target m payload action

theorem normalizeTotal_protected_observations (policy : Policy) (rules : List Rule)
    (observed : List FullMark)
    (protectedKeys : ∀ m, m ∈ observed → m ∈ policy.protectedChecks)
    (marks : Position → FullMark → Bool) (other : Nat → Bool)
    (covers : RoundCover policy marks (programWeight rules + 1) rules) :
    observeActions observed (execute marks other (normalizeTotal policy rules)) =
      observeActions observed (execute marks other rules) := by
  rw [normalizeTotal_eq_iterate]
  exact iterate_protected_observations policy _ rules observed protectedKeys marks other covers

def normalizeExecutionTotal {Metadata : Type} (policy : Policy)
    (b : Bundle Metadata) : Bundle Metadata :=
  { b with execution := normalizeTotal policy b.execution }

theorem normalizeExecutionTotal_metadata {Metadata : Type} (policy : Policy) (b : Bundle Metadata) :
    (normalizeExecutionTotal policy b).metadata = b.metadata := rfl

theorem normalizeExecutionTotal_declarations {Metadata : Type} (policy : Policy) (b : Bundle Metadata) :
    (normalizeExecutionTotal policy b).declarations = b.declarations := rfl

namespace Controls
open ApSpec.SemgrepReducedMarks.Controls

theorem cascade_total_fixed :
    step {} (normalizeTotal {} cascade) = normalizeTotal {} cascade := by decide

theorem cascade_total_closes_all_conditions_and_GEN :
    (normalizeTotal {} cascade).map (fun r => (r.guard,r.actions)) =
      [(.truth false,[]),(.truth false,[]),(.truth false,[])] := by decide

theorem cascade_four_changes_and_one_final_check :
    programWeight cascade = 8 ∧ changedRounds {} 9 cascade = 4 ∧ equalityChecks {} 9 cascade = 5 := by decide

theorem fixed_catalog_stops_immediately : equalityChecks {} 100 guardedSelf = 1 := by decide

theorem supported_bootstrap_cycle_retains_original : normalizeTotal {} guardedSelf = guardedSelf := by decide

theorem opaque_clients_keep_original : normalizeTotal {protectAll := true} cascade = cascade := by decide

def negSource : Rule := ⟨300,.neg (.cm anyFieldArg0 t),[.gen 41 result u 0],[anyFieldArg0,result]⟩
def sinkU : Rule := ⟨301,.cm arg0 u,[],[arg0]⟩

theorem absent_negative_any_field_can_enable_GEN :
    (normalizeTotal {} [negSource,sinkU]).map (fun r => (r.itemId,r.guard,r.actions)) =
      [(300,.truth true,[.gen 41 result u 0]),(301,.cm arg0 u,[])] := by decide

theorem unused_target_metadata_preserved_by_total :
    (normalizeTotal {} [source,sink]).map (fun r => (r.itemId,r.guard,r.actions,r.positionMetadata)) =
      [(100,.truth true,[.gen 17 result t 5],[result,arg1]),(200,.cm arg0 t,[],[arg0])] := by decide

theorem total_does_not_use_the_short_fuel_fallback :
    normalize {} 1 cascade = cascade ∧ normalizeTotal {} cascade ≠ cascade := by decide

theorem same_displayed_name_different_owner_still_absent :
    weight (fold [sameNameOtherOwner] (.cm anyAccessorArg0 t)) < weight (.cm anyAccessorArg0 t) := by decide

end Controls

end ApSpec.SemgrepReducedMarksTermination
