import ApSpec.SemgrepReducedMarks

/-
Metadata projection, AFTER the ORIGINAL metadata
relevant resolver and original role-specific guard admission. The executable
absent-CM rewrite must NOT be used to admit additional metadata rows. In
particular, a cleaner with residual NOT CM(U) is not unconditionally true.

Each admitted source/selected-cleaner action contributes its POSITION crossed
with the row's complete relevant-mark set. The operation's own mark need not
equal a derived reset mark. Generic deletion-only policies have subset laws;
their strict-loss controls below describe the REJECTED naive action filter.
The SELECTED canonical policy keeps every original position carrier and
filters only reset targets by the final executable actual-used keys
L = assigned intersection CM-used. It drops rows only when their projected
reset product is empty. Reset membership is equal for every key in L.

Scalar TAINT survivor inclusion and FLOW exclusion inclusion below keep the
same incoming fact and spatial selector interpretation. The positive guard
model starts AFTER residual negative mark literals have been projected true.
It does not prove Kotlin lowering, a whole-engine theorem, or exact-negative
observation equality. Unknown exact-negative clients need their own contract.
-/
namespace ApSpec.SemgrepMetadataProjection
set_option autoImplicit false
set_option maxHeartbeats 3000000
open ApSpec.SemgrepReducedMarks

inductive MetadataRole where
  | source | selectedCleaner
  deriving DecidableEq, Repr

/-- Only actions whose positions enter the existing reset lowering occur here:
source assignments or selected RemoveMark cleaner actions. Other action
kinds retain their separate runtime behavior. Original identities are copied. -/
structure MetadataAction where
  ordinal : Nat
  position : Position
  operationMark : FullMark
  deriving DecidableEq, Repr

structure MetadataRow where
  itemId : Nat
  bindingId : Nat
  role : MetadataRole
  actions : List MetadataAction
  relevantMarks : List FullMark
  deriving DecidableEq, Repr

structure Reset where
  position : Position
  mark : FullMark
  deriving DecidableEq, Repr

def rowResets (row : MetadataRow) : List Reset :=
  row.actions.flatMap fun action =>
    row.relevantMarks.map fun mark => ⟨action.position, mark⟩

def resets (rows : List MetadataRow) : List Reset := rows.flatMap rowResets

structure Projection where
  keepRow : MetadataRow → Bool
  keepAction : MetadataRow → MetadataAction → Bool
  keepMark : MetadataRow → FullMark → Bool

def projectRow (policy : Projection) (row : MetadataRow) : MetadataRow :=
  { row with
    actions := row.actions.filter (policy.keepAction row)
    relevantMarks := row.relevantMarks.filter (policy.keepMark row) }

/-- The input is the fixed ORIGINAL-admitted census. The policy can delete
rows, and empty projected source/cleaner rows are also deleted. It cannot
invent a row by evaluating a new executable guard. -/
def project (policy : Projection) : List MetadataRow → List MetadataRow
  | [] => []
  | row :: rest =>
      let selected := projectRow policy row
      if policy.keepRow row && !selected.actions.isEmpty then
        selected :: project policy rest
      else project policy rest

theorem project_row_original_identities (policy : Projection) (row : MetadataRow) :
    (projectRow policy row).itemId = row.itemId ∧
    (projectRow policy row).bindingId = row.bindingId ∧
    (projectRow policy row).role = row.role := ⟨rfl, rfl, rfl⟩

theorem project_action_is_original (policy : Projection) (row : MetadataRow)
    (action : MetadataAction) (member : action ∈ (projectRow policy row).actions) :
    action ∈ row.actions := (List.mem_filter.mp member).1

theorem project_mark_is_original (policy : Projection) (row : MetadataRow)
    (mark : FullMark) (member : mark ∈ (projectRow policy row).relevantMarks) :
    mark ∈ row.relevantMarks := (List.mem_filter.mp member).1

theorem project_row_reset_subset (policy : Projection) (row : MetadataRow)
    (reset : Reset) (member : reset ∈ rowResets (projectRow policy row)) :
    reset ∈ rowResets row := by
  rcases List.mem_flatMap.mp member with ⟨action, actionMember, resetMember⟩
  rcases List.mem_map.mp resetMember with ⟨mark, markMember, same⟩
  apply List.mem_flatMap.mpr
  refine ⟨action, project_action_is_original policy row action actionMember, ?_⟩
  exact List.mem_map.mpr
    ⟨mark, project_mark_is_original policy row mark markMember, same⟩

theorem projected_row_has_original (policy : Projection) (rows : List MetadataRow)
    (row : MetadataRow) (member : row ∈ project policy rows) :
    ∃ original ∈ rows, row = projectRow policy original := by
  induction rows with
  | nil => simp [project] at member
  | cons original rest ih =>
      simp only [project] at member
      split at member
      · rcases List.mem_cons.mp member with same | later
        · exact ⟨original, by simp, same⟩
        · rcases ih later with ⟨old, oldMember, same⟩
          exact ⟨old, by simp [oldMember], same⟩
      · rcases ih member with ⟨old, oldMember, same⟩
        exact ⟨old, by simp [oldMember], same⟩

/-- Reset inclusion requires the fixed-original-admission contract stated at
the module boundary. It is not an equality of reset masks. -/
theorem projected_reset_subset (policy : Projection) (rows : List MetadataRow)
    (reset : Reset) (member : reset ∈ resets (project policy rows)) :
    reset ∈ resets rows := by
  rcases List.mem_flatMap.mp member with ⟨row, rowMember, resetMember⟩
  rcases projected_row_has_original policy rows row rowMember with
    ⟨original, originalMember, same⟩
  subst row
  exact List.mem_flatMap.mpr
    ⟨original, originalMember, project_row_reset_subset policy original reset resetMember⟩

def resetHit (rows : List MetadataRow) (position : Position) (mark : FullMark) : Bool :=
  decide (Reset.mk position mark ∈ resets rows)

def taintSurvives (rows : List MetadataRow) (position : Position) (mark : FullMark) : Bool :=
  decide (Reset.mk position mark ∉ resets rows)

theorem old_taint_survivor_implies_new (policy : Projection) (rows : List MetadataRow)
    (position : Position) (mark : FullMark)
    (survived : taintSurvives rows position mark = true) :
    taintSurvives (project policy rows) position mark = true := by
  apply decide_eq_true
  intro member
  exact (of_decide_eq_true survived) (projected_reset_subset policy rows _ member)

/-- A FLOW exclusion contains either an incoming exclusion or a derived reset.
The common incoming mask is arbitrary; it is not recomputed by this policy. -/
def mxAfter (incoming : FullMark → Bool) (rows : List MetadataRow)
    (position : Position) (mark : FullMark) : Bool :=
  incoming mark || resetHit rows position mark

theorem new_mx_subset_old (policy : Projection) (rows : List MetadataRow)
    (incoming : FullMark → Bool) (position : Position) (mark : FullMark)
    (member : mxAfter incoming (project policy rows) position mark = true) :
    mxAfter incoming rows position mark = true := by
  rcases Bool.or_eq_true_iff.mp member with oldIncoming | reset
  · exact Bool.or_eq_true_iff.mpr (Or.inl oldIncoming)
  · apply Bool.or_eq_true_iff.mpr
    right
    apply decide_eq_true
    exact projected_reset_subset policy rows _ (of_decide_eq_true reset)

def taintAfter (incoming : Position → FullMark → Bool) (rows : List MetadataRow)
    (position : Position) (mark : FullMark) : Bool :=
  incoming position mark && taintSurvives rows position mark

def MarkSubset (oldMarks newMarks : Position → FullMark → Bool) : Prop :=
  ∀ position mark, oldMarks position mark = true → newMarks position mark = true

theorem taint_after_membership_monotone (policy : Projection) (rows : List MetadataRow)
    (incoming : Position → FullMark → Bool) :
    MarkSubset (taintAfter incoming rows) (taintAfter incoming (project policy rows)) := by
  intro position mark member
  rcases Bool.and_eq_true_iff.mp member with ⟨initial, survived⟩
  exact Bool.and_eq_true_iff.mpr
    ⟨initial, old_taint_survivor_implies_new policy rows position mark survived⟩

/-- The final executable FULL-key inventories are inputs. This construction
does not bind methods, fold metadata conditions or infer mark availability. -/
def actualUsedKeys (assigned checked : List FullMark) : List FullMark :=
  assigned.filter fun mark => decide (mark ∈ checked)

theorem actual_used_membership (assigned checked : List FullMark) (mark : FullMark) :
    mark ∈ actualUsedKeys assigned checked ↔ mark ∈ assigned ∧ mark ∈ checked := by
  simp [actualUsedKeys]

/-- Retain ORIGINAL action-position carriers even when the action's own GEN
mark is not a target. Only the derived reset target mask is specialized.
Rows with no retained reset marks, or no original actions, have no effect. -/
def canonicalProjection (targetKeys : List FullMark) : Projection :=
  { keepRow := fun row =>
      !(row.relevantMarks.filter fun mark => decide (mark ∈ targetKeys)).isEmpty
    keepAction := fun _ _ => true
    keepMark := fun _ mark => decide (mark ∈ targetKeys) }

def actualUsedProjection (assigned checked : List FullMark) : Projection :=
  canonicalProjection (actualUsedKeys assigned checked)

theorem canonical_original_position_carriers (targetKeys : List FullMark) (row : MetadataRow) :
    (projectRow (canonicalProjection targetKeys) row).actions = row.actions := by
  simp [projectRow, canonicalProjection]

theorem canonical_relevant_marks (targetKeys : List FullMark) (row : MetadataRow) :
    (projectRow (canonicalProjection targetKeys) row).relevantMarks =
      row.relevantMarks.filter (fun mark => decide (mark ∈ targetKeys)) := rfl

theorem canonical_row_reset_membership (targetKeys : List FullMark) (row : MetadataRow)
    (reset : Reset) (target : reset.mark ∈ targetKeys) :
    reset ∈ rowResets (projectRow (canonicalProjection targetKeys) row) ↔
      reset ∈ rowResets row := by
  constructor
  · exact project_row_reset_subset _ row reset
  · intro member
    rcases List.mem_flatMap.mp member with ⟨action, actionMember, resetMember⟩
    rcases List.mem_map.mp resetMember with ⟨mark, markMember, same⟩
    subst reset
    apply List.mem_flatMap.mpr
    refine ⟨action, ?_, ?_⟩
    · simpa only [canonical_original_position_carriers] using actionMember
    · apply List.mem_map.mpr
      refine ⟨mark, ?_, rfl⟩
      simp only [canonical_relevant_marks, List.mem_filter, decide_eq_true_eq]
      exact ⟨markMember, target⟩

theorem list_nonempty_of_member {A : Type} (items : List A) (item : A)
    (member : item ∈ items) : items.isEmpty = false := by
  cases items with
  | nil => simp at member
  | cons _ _ => rfl

theorem retained_row_reset_is_projected (policy : Projection) (rows : List MetadataRow)
    (row : MetadataRow) (original : row ∈ rows) (kept : policy.keepRow row = true)
    (reset : Reset) (member : reset ∈ rowResets (projectRow policy row)) :
    reset ∈ resets (project policy rows) := by
  have nonempty : (projectRow policy row).actions.isEmpty = false := by
    rcases List.mem_flatMap.mp member with ⟨action, actionMember, _⟩
    exact list_nonempty_of_member _ action actionMember
  induction rows with
  | nil => simp at original
  | cons head rest ih =>
      rcases List.mem_cons.mp original with same | later
      · subst head
        simp only [project, kept, nonempty, Bool.not_false, Bool.true_and, ↓reduceIte,
          resets, List.flatMap_cons]
        exact List.mem_append_left _ member
      · have tail := ih later
        simp only [project]
        split
        · exact List.mem_append_right _ tail
        · exact tail

theorem canonical_reset_membership (targetKeys : List FullMark) (rows : List MetadataRow)
    (reset : Reset) (target : reset.mark ∈ targetKeys) :
    reset ∈ resets (project (canonicalProjection targetKeys) rows) ↔ reset ∈ resets rows := by
  constructor
  · exact projected_reset_subset _ rows reset
  · intro member
    rcases List.mem_flatMap.mp member with ⟨row, rowMember, oldReset⟩
    have newReset := (canonical_row_reset_membership targetKeys row reset target).mpr oldReset
    have admitted : (canonicalProjection targetKeys).keepRow row = true := by
      rcases List.mem_flatMap.mp newReset with ⟨_, _, markMember⟩
      rcases List.mem_map.mp markMember with ⟨mark, member, _⟩
      have nonempty := list_nonempty_of_member _ mark member
      change Bool.not ((projectRow (canonicalProjection targetKeys) row).relevantMarks.isEmpty) = true
      rw [nonempty]
      rfl
    exact retained_row_reset_is_projected _ rows row rowMember admitted reset newReset

theorem canonical_reset_hit_equal (targetKeys : List FullMark) (rows : List MetadataRow)
    (position : Position) (mark : FullMark) (target : mark ∈ targetKeys) :
    resetHit (project (canonicalProjection targetKeys) rows) position mark =
      resetHit rows position mark := by
  simp only [resetHit, canonical_reset_membership targetKeys rows (Reset.mk position mark) target]

theorem canonical_taint_survivor_equal (targetKeys : List FullMark) (rows : List MetadataRow)
    (position : Position) (mark : FullMark) (target : mark ∈ targetKeys) :
    taintSurvives (project (canonicalProjection targetKeys) rows) position mark =
      taintSurvives rows position mark := by
  simp only [taintSurvives, canonical_reset_membership targetKeys rows (Reset.mk position mark) target]

theorem canonical_mx_equal (targetKeys : List FullMark) (rows : List MetadataRow)
    (incoming : FullMark → Bool) (position : Position) (mark : FullMark)
    (target : mark ∈ targetKeys) :
    mxAfter incoming (project (canonicalProjection targetKeys) rows) position mark =
      mxAfter incoming rows position mark := by
  simp only [mxAfter, canonical_reset_hit_equal targetKeys rows position mark target]

theorem actual_used_reset_membership (assigned checked : List FullMark) (rows : List MetadataRow)
    (reset : Reset) (produced : reset.mark ∈ assigned) (used : reset.mark ∈ checked) :
    reset ∈ resets (project (actualUsedProjection assigned checked) rows) ↔ reset ∈ resets rows :=
  canonical_reset_membership _ rows reset ((actual_used_membership _ _ _).mpr ⟨produced, used⟩)

theorem row_reset_product_empty (row : MetadataRow) :
    rowResets row = [] ↔ row.actions = [] ∨ row.relevantMarks = [] := by
  cases actions : row.actions with
  | nil => simp [rowResets, actions]
  | cons action rest =>
      cases marks : row.relevantMarks with
      | nil => simp [rowResets, actions, marks]
      | cons mark more => simp [rowResets, actions, marks]

theorem canonical_drops_empty_effects (targetKeys : List FullMark) (row : MetadataRow)
    (empty : rowResets (projectRow (canonicalProjection targetKeys) row) = []) :
    project (canonicalProjection targetKeys) [row] = [] := by
  rcases (row_reset_product_empty _).mp empty with noActions | noMarks
  · simp only [project, noActions, List.isEmpty_nil, Bool.not_true, Bool.and_false,
      Bool.false_eq_true, ↓reduceIte]
  · have noMarks' : row.relevantMarks.filter (fun mark => decide (mark ∈ targetKeys)) = [] :=
      noMarks
    simp [project, canonicalProjection, noMarks']

theorem canonical_empty_effect_drop_preserves_resets (targetKeys : List FullMark) (row : MetadataRow)
    (empty : rowResets (projectRow (canonicalProjection targetKeys) row) = []) :
    resets (project (canonicalProjection targetKeys) [row]) =
      rowResets (projectRow (canonicalProjection targetKeys) row) := by
  rw [canonical_drops_empty_effects targetKeys row empty, empty]
  rfl

/-- This is the runtime positive guard domain AFTER the supported residual
negative-mark approximation. Opaque values have the SAME interpretation. -/
inductive PositiveGuard where
  | constant (value : Bool)
  | cm (position : Position) (mark : FullMark)
  | opaque (identity : Nat)
  | conj (left right : PositiveGuard)
  | disj (left right : PositiveGuard)
  deriving DecidableEq, Repr

def evalPositive (marks : Position → FullMark → Bool) (other : Nat → Bool) :
    PositiveGuard → Bool
  | .constant value => value
  | .cm position mark => marks position mark
  | .opaque identity => other identity
  | .conj left right => evalPositive marks other left && evalPositive marks other right
  | .disj left right => evalPositive marks other left || evalPositive marks other right

theorem positive_guard_monotone (oldMarks newMarks : Position → FullMark → Bool)
    (other : Nat → Bool) (included : MarkSubset oldMarks newMarks) (guard : PositiveGuard) :
    evalPositive oldMarks other guard = true → evalPositive newMarks other guard = true := by
  induction guard with
  | constant value => exact id
  | cm position mark => exact included position mark
  | «opaque» identity => exact id
  | conj left right ihLeft ihRight =>
      intro satisfied
      rcases Bool.and_eq_true_iff.mp satisfied with ⟨leftTrue, rightTrue⟩
      exact Bool.and_eq_true_iff.mpr ⟨ihLeft leftTrue, ihRight rightTrue⟩
  | disj left right ihLeft ihRight =>
      intro satisfied
      rcases Bool.or_eq_true_iff.mp satisfied with leftTrue | rightTrue
      · exact Bool.or_eq_true_iff.mpr (Or.inl (ihLeft leftTrue))
      · exact Bool.or_eq_true_iff.mpr (Or.inr (ihRight rightTrue))

theorem surviving_positive_guard_monotone (policy : Projection) (rows : List MetadataRow)
    (incoming : Position → FullMark → Bool) (other : Nat → Bool) (guard : PositiveGuard)
    (satisfied : evalPositive (taintAfter incoming rows) other guard = true) :
    evalPositive (taintAfter incoming (project policy rows)) other guard = true :=
  positive_guard_monotone _ _ other (taint_after_membership_monotone policy rows incoming) guard satisfied

/-- The supported residual-negative policy is a constant possibility test,
not a Boolean test that the current mark set lacks this mark. -/
def projectResidualNegative (_position : Position) (_mark : FullMark) : PositiveGuard :=
  .constant true

theorem residual_negative_runtime_projection_true
    (marks : Position → FullMark → Bool) (other : Nat → Bool)
    (position : Position) (mark : FullMark) :
    evalPositive marks other (projectResidualNegative position mark) = true := rfl

namespace Controls
def owner : OwnerKey := ⟨1, false⟩
def t : FullMark := ⟨owner, 10⟩
def u : FullMark := ⟨owner, 11⟩
def result : Position := ⟨.result, [], .exact⟩
def arg0 : Position := ⟨.argument 0, [], .exact⟩
def unusedAssignment : MetadataAction := ⟨7, result, u⟩
def liveAssignment : MetadataAction := ⟨19, arg0, t⟩
def row : MetadataRow := ⟨100, 9, .source, [unusedAssignment], [t, u]⟩
/-- REJECTED naive policy: an unused GEN mark does not justify deleting its
original metadata position carrier for other, actually used reset targets. -/
def pruneU : Projection :=
  ⟨(fun _ => true), (fun _ action => decide (action.operationMark ≠ u)), (fun _ _ => true)⟩
def selectedProjection : Projection := actualUsedProjection [t, u] [t]
def incomingT : Position → FullMark → Bool :=
  fun position mark => decide (position = result ∧ mark = t)

theorem unused_action_resets_live_mark_before_projection :
    rowResets row = [⟨result, t⟩, ⟨result, u⟩] ∧
    unusedAssignment.operationMark ≠ t := by decide

theorem deleting_unused_action_removes_live_reset :
    resetHit [row] result t = true ∧
    resetHit (project pruneU [row]) result t = false ∧
    project pruneU [row] = [] := by decide

theorem old_new_reset_masks_are_not_equal :
    resets (project pruneU [row]) ≠ resets [row] := by decide

theorem extra_live_taint_survives :
    taintAfter incomingT [row] result t = false ∧
    taintAfter incomingT (project pruneU [row]) result t = true := by decide

theorem exclusion_can_strictly_shrink :
    mxAfter (fun _ => false) [row] result t = true ∧
    mxAfter (fun _ => false) (project pruneU [row]) result t = false := by decide

theorem original_binding_and_action_ordinal_are_retained :
    let retainedRow := { row with actions := [unusedAssignment, liveAssignment] }
    project pruneU [retainedRow] =
      [⟨100, 9, .source, [⟨19, arg0, t⟩], [t, u]⟩] ∧
    resetHit (project pruneU [retainedRow]) arg0 t = true ∧
    resetHit (project pruneU [retainedRow]) result t = false := by decide

theorem dropping_a_row_only_deletes_its_resets :
    project { pruneU with keepRow := fun _ => false } [row] = [] := by decide

theorem exact_negative_scalar_can_flip :
    !(taintAfter incomingT [row] result t) = true ∧
    !(taintAfter incomingT (project pruneU [row]) result t) = false := by decide

theorem supported_negative_projection_does_not_flip :
    evalPositive (taintAfter incomingT [row]) (fun _ => false)
      (projectResidualNegative result t) = true ∧
    evalPositive (taintAfter incomingT (project pruneU [row])) (fun _ => false)
      (projectResidualNegative result t) = true := by decide

theorem positive_compound_guard_keeps_its_old_witness :
    let incoming := fun (_ : Position) (mark : FullMark) => decide (mark = t)
    let guard := PositiveGuard.conj
      (.disj (.cm result t) (.cm arg0 t)) (.opaque 3)
    evalPositive (taintAfter incoming [row]) (fun id => id == 3) guard = true ∧
    evalPositive (taintAfter incoming (project pruneU [row])) (fun id => id == 3) guard = true := by decide

/-- This is the existing bidi metadata-cleaner admission shape. It is not the
source/sink residual-negative possibility projection. -/
def metadataCleanerAdmitted : Expr → Bool
  | .truth true => true
  | _ => false

def admitMetadataCleaner (guard : Expr) (candidate : MetadataRow) : List MetadataRow :=
  if metadataCleanerAdmitted guard then [candidate] else []

def guardedCleaner : MetadataRow :=
  ⟨101, 10, .selectedCleaner, [⟨3, result, t⟩], [t, u]⟩

/-- Using the executable absent-CM rewrite to admit metadata would create a
new live-T reset. It violates the deletion-only model's named precondition. -/
theorem folding_metadata_negative_would_change_admission :
    metadataCleanerAdmitted (.neg (.cm arg0 u)) = false ∧
    metadataCleanerAdmitted (fold [] (.neg (.cm arg0 u))) = true ∧
    resetHit (admitMetadataCleaner (.neg (.cm arg0 u)) guardedCleaner) result t = false ∧
    resetHit (admitMetadataCleaner (fold [] (.neg (.cm arg0 u))) guardedCleaner) result t = true := by decide

theorem actual_used_is_assigned_intersection_checked :
    actualUsedKeys [t, u] [t] = [t] ∧
    actualUsedKeys [t] [t, u] = [t] := by decide

theorem assigned_but_unchecked_reset_target_is_skipped :
    u ∈ ([t, u] : List FullMark) ∧
    u ∉ ([t] : List FullMark) ∧
    resetHit (project selectedProjection [row]) result u = false := by decide

theorem checked_but_unassigned_reset_target_is_skipped :
    u ∈ ([t, u] : List FullMark) ∧
    u ∉ ([t] : List FullMark) ∧
    resetHit (project (actualUsedProjection [t] [t, u]) [row]) result u = false := by decide

theorem unused_GEN_carrier_still_resets_live_target :
    project selectedProjection [row] =
      [⟨100, 9, .source, [⟨7, result, u⟩], [t]⟩] ∧
    resetHit (project selectedProjection [row]) result t = resetHit [row] result t := by decide

theorem naive_action_deletion_is_a_rejected_regression :
    resetHit [row] result t = true ∧
    resetHit (project pruneU [row]) result t = false ∧
    resetHit (project selectedProjection [row]) result t = true := by decide

theorem selected_live_taint_and_exclusion_are_equal :
    taintAfter incomingT (project selectedProjection [row]) result t =
      taintAfter incomingT [row] result t ∧
    mxAfter (fun _ => false) (project selectedProjection [row]) result t =
      mxAfter (fun _ => false) [row] result t := by decide

theorem all_original_action_positions_are_carriers :
    let twoPositions := { row with actions := [unusedAssignment, liveAssignment] }
    resets (project selectedProjection [twoPositions]) = [⟨result, t⟩, ⟨arg0, t⟩] := by decide

theorem actionless_or_effectless_metadata_rows_are_dropped :
    project selectedProjection [{ row with actions := [] }] = [] ∧
    project (actualUsedProjection [t, u] []) [row] = [] ∧
    project (actualUsedProjection [] [t, u]) [row] = [] := by decide

theorem selected_live_exact_negative_is_equal :
    !(taintAfter incomingT (project selectedProjection [row]) result t) =
      !(taintAfter incomingT [row] result t) := by decide

/-- Each entry executes the closed proposition's decision procedure; the
named witness supplies its readable correspondence to the control above. -/
private def checkWitness {p : Prop} [Decidable p] (_witness : p) : Bool := decide p

def checks : List Bool := [
  checkWitness unused_action_resets_live_mark_before_projection,
  checkWitness deleting_unused_action_removes_live_reset,
  checkWitness old_new_reset_masks_are_not_equal,
  checkWitness extra_live_taint_survives,
  checkWitness exclusion_can_strictly_shrink,
  checkWitness original_binding_and_action_ordinal_are_retained,
  checkWitness dropping_a_row_only_deletes_its_resets,
  checkWitness exact_negative_scalar_can_flip,
  checkWitness supported_negative_projection_does_not_flip,
  checkWitness positive_compound_guard_keeps_its_old_witness,
  checkWitness folding_metadata_negative_would_change_admission,
  checkWitness actual_used_is_assigned_intersection_checked,
  checkWitness assigned_but_unchecked_reset_target_is_skipped,
  checkWitness checked_but_unassigned_reset_target_is_skipped,
  checkWitness unused_GEN_carrier_still_resets_live_target,
  checkWitness naive_action_deletion_is_a_rejected_regression,
  checkWitness selected_live_taint_and_exclusion_are_equal,
  checkWitness all_original_action_positions_are_carriers,
  checkWitness actionless_or_effectless_metadata_rows_are_dropped,
  checkWitness selected_live_exact_negative_is_equal
]

theorem checks_pass : checks.all id = true := by decide

end Controls
end ApSpec.SemgrepMetadataProjection
