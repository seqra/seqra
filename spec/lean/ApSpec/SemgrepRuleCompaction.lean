import ApSpec.SemgrepReducedMarksTermination

/-
Role-aware rule compaction is INSIDE the executable normalization loop.
False rows of every role are removed. Empty sources/cleaners are removed;
empty sinks and passes remain. Retained rows/actions keep their original
logical ordinals. Source-hit/provider-enumeration diagnostics are not events
in this model; their disappearance is an explicit adapter semantic boundary.

Metadata is a separate post-fixed-point view. Derived RemoveMark TARGETS
use final executable LIVE = assigned intersect CM-used. Raw GEN/RemoveMark
position carriers stay when any live target needs them; unmarked actions stay.
Metadata guards are not mark-specialized and never add producer/check keys.
Naive own-mark carrier filtering is explicitly rejected.
-/
namespace ApSpec.SemgrepRuleCompaction
set_option autoImplicit false
set_option maxHeartbeats 4000000
open ApSpec.SemgrepReducedMarks
open ApSpec.SemgrepReducedMarksTermination

inductive Role where
  | source | cleaner | sink | pass
  deriving DecidableEq, Repr

structure Row where
  role : Role
  originalOrdinal : Nat
  bound : Rule
  deriving DecidableEq, Repr

def keepRow (row : Row) : Bool :=
  decide (row.bound.guard ≠ .truth false) &&
    match row.role with
    | .source | .cleaner => !row.bound.actions.isEmpty
    | .sink | .pass => true

def compact (rows : List Row) : List Row := rows.filter keepRow

def underlying (rows : List Row) : List Rule := rows.map Row.bound

def producers (policy : Policy) (rows : List Row) : List FullMark := assigned policy (underlying rows)
def consumers (policy : Policy) (rows : List Row) : List FullMark := checked policy (underlying rows)

/-- Common closed-view LIVE keys; protectAll is represented separately as TOP. -/
def liveKeys (policy : Policy) (rows : List Row) : List FullMark :=
  (producers policy rows).filter (fun mark => decide (mark ∈ consumers policy rows))

theorem live_keys_member (policy : Policy) (rows : List Row) (mark : FullMark) :
    mark ∈ liveKeys policy rows ↔ mark ∈ producers policy rows ∧ mark ∈ consumers policy rows := by
  simp [liveKeys]

def rewriteRow (available needed : List FullMark) (row : Row) : Row :=
  { row with bound := rewriteRule available needed row.bound }

def rewritePass (policy : Policy) (rows : List Row) : List Row :=
  if policy.protectAll then rows else
    rows.map (rewriteRow (producers policy rows) (consumers policy rows))

/-- P and C are simultaneously read from CURRENT compacted executable rows.
    The next round recomputes them after deleted rows disappear. -/
def round (policy : Policy) (rows : List Row) : List Row := compact (rewritePass policy rows)

def rowWeight (row : Row) : Nat := 1 + ruleWeight row.bound

def rowsWeight : List Row → Nat
  | [] => 0
  | row :: rows => rowWeight row + rowsWeight rows

theorem rowWeight_positive (row : Row) : 0 < rowWeight row := by unfold rowWeight; omega

theorem compact_sublist (rows : List Row) : (compact rows).Sublist rows := List.filter_sublist

theorem compact_retains_complete_rows (rows : List Row) (row : Row) :
    row ∈ compact rows ↔ row ∈ rows ∧ keepRow row = true := List.mem_filter

theorem compact_weight_nonincrease (rows : List Row) : rowsWeight (compact rows) ≤ rowsWeight rows := by
  induction rows with
  | nil => simp [compact,rowsWeight]
  | cons r rs ih =>
      by_cases keep : keepRow r = true
      · simpa [compact,List.filter_cons,keep,rowsWeight] using Nat.add_le_add_left ih (rowWeight r)
      · simp only [compact] at ih
        simp only [compact,List.filter_cons,keep,Bool.false_eq_true,↓reduceIte,rowsWeight]
        omega

theorem compact_same_or_strict (rows : List Row) :
    compact rows = rows ∨ rowsWeight (compact rows) < rowsWeight rows := by
  induction rows with
  | nil => exact Or.inl rfl
  | cons r rs ih =>
      by_cases keep : keepRow r = true
      · rcases ih with same | smaller
        · left; simp [compact,keep] at same ⊢; exact same
        · right; simpa [compact,List.filter_cons,keep,rowsWeight] using Nat.add_lt_add_left smaller (rowWeight r)
      · right
        have tail := compact_weight_nonincrease rs
        have positive := rowWeight_positive r
        simp only [compact] at tail
        simp only [compact,List.filter_cons,keep,Bool.false_eq_true,↓reduceIte,rowsWeight]
        omega

theorem rewriteRow_same_or_strict (available needed : List FullMark) (row : Row) :
    rewriteRow available needed row = row ∨ rowWeight (rewriteRow available needed row) < rowWeight row := by
  rcases rewrite_rule_same_or_strict available needed row.bound with same | smaller
  · left
    cases row
    simp_all [rewriteRow]
  · right
    simp only [rowWeight,rewriteRow]
    omega

theorem rewriteList_same_or_strict (available needed : List FullMark) (rows : List Row) :
    rows.map (rewriteRow available needed) = rows ∨
      rowsWeight (rows.map (rewriteRow available needed)) < rowsWeight rows := by
  induction rows with
  | nil => exact Or.inl rfl
  | cons r rs ih =>
      rcases rewriteRow_same_or_strict available needed r with same | smaller
      · rcases ih with sameTail | smallerTail
        · left; simp [List.map_cons,same,sameTail]
        · right; simp only [List.map_cons,rowsWeight,same]; omega
      · right
        rcases ih with sameTail | smallerTail
        · simp only [List.map_cons,rowsWeight,sameTail]; omega
        · simp only [List.map_cons,rowsWeight]; omega

theorem rewritePass_same_or_strict (policy : Policy) (rows : List Row) :
    rewritePass policy rows = rows ∨ rowsWeight (rewritePass policy rows) < rowsWeight rows := by
  by_cases all : policy.protectAll = true
  · exact Or.inl (by simp [rewritePass,all])
  · simpa [rewritePass,all] using rewriteList_same_or_strict (producers policy rows) (consumers policy rows) rows

theorem round_same_or_strict (policy : Policy) (rows : List Row) :
    round policy rows = rows ∨ rowsWeight (round policy rows) < rowsWeight rows := by
  rcases rewritePass_same_or_strict policy rows with same | smaller
  · simpa only [round,same] using compact_same_or_strict rows
  · right
    have bound := compact_weight_nonincrease (rewritePass policy rows)
    simp only [round]
    omega

theorem changed_round_decreases (policy : Policy) (rows : List Row) (different : round policy rows ≠ rows) :
    rowsWeight (round policy rows) < rowsWeight rows := by
  rcases round_same_or_strict policy rows with same | smaller
  · exact False.elim (different same)
  · exact smaller

def iterate (policy : Policy) : Nat → List Row → List Row
  | 0, rows => rows
  | n + 1, rows => iterate policy n (round policy rows)

theorem iterate_fixed (policy : Policy) (fuel : Nat) (rows : List Row) (fixed : round policy rows = rows) :
    iterate policy fuel rows = rows := by
  induction fuel with
  | zero => rfl
  | succ n ih => simpa only [iterate,fixed] using ih

theorem enough_rounds_fixed (policy : Policy) (fuel : Nat) (rows : List Row) (enough : rowsWeight rows < fuel) :
    round policy (iterate policy fuel rows) = iterate policy fuel rows := by
  induction fuel generalizing rows with
  | zero => omega
  | succ n ih =>
      by_cases fixed : round policy rows = rows
      · rw [iterate_fixed policy (n+1) rows fixed]; exact fixed
      · have smaller := changed_round_decreases policy rows fixed
        exact ih (round policy rows) (by omega)

def stopAtFixed (policy : Policy) : Nat → List Row → List Row
  | 0, rows => rows
  | n + 1, rows => if round policy rows = rows then rows else stopAtFixed policy n (round policy rows)

theorem stopAtFixed_eq_iterate (policy : Policy) (fuel : Nat) (rows : List Row) :
    stopAtFixed policy fuel rows = iterate policy fuel rows := by
  induction fuel generalizing rows with
  | zero => rfl
  | succ n ih =>
      by_cases fixed : round policy rows = rows
      · simp only [stopAtFixed,if_pos fixed]
        exact (iterate_fixed policy (n+1) rows fixed).symm
      · simp only [stopAtFixed,if_neg fixed,iterate]
        exact ih (round policy rows)

/-- Total, early-stopping executable loop; metadata is not an input. -/
def normalizeTotal (policy : Policy) (rows : List Row) : List Row :=
  stopAtFixed policy (rowsWeight rows + 1) rows

theorem normalizeTotal_fixed (policy : Policy) (rows : List Row) :
    round policy (normalizeTotal policy rows) = normalizeTotal policy rows := by
  simp only [normalizeTotal,stopAtFixed_eq_iterate]
  exact enough_rounds_fixed policy _ rows (by omega)

theorem normalizeTotal_idempotent (policy : Policy) (rows : List Row) :
    normalizeTotal policy (normalizeTotal policy rows) = normalizeTotal policy rows := by
  have fixed := normalizeTotal_fixed policy rows
  simp only [normalizeTotal,stopAtFixed_eq_iterate] at fixed ⊢
  exact iterate_fixed policy _ _ fixed

def changedRounds (policy : Policy) : Nat → List Row → Nat
  | 0, _ => 0
  | n + 1, rows => if round policy rows = rows then 0 else 1 + changedRounds policy n (round policy rows)

def equalityChecks (policy : Policy) : Nat → List Row → Nat
  | 0, _ => 0
  | n + 1, rows => if round policy rows = rows then 1 else 1 + equalityChecks policy n (round policy rows)

theorem changed_rounds_bounded (policy : Policy) (fuel : Nat) (rows : List Row) :
    changedRounds policy fuel rows ≤ rowsWeight rows := by
  induction fuel generalizing rows with
  | zero => simp [changedRounds]
  | succ n ih =>
      by_cases fixed : round policy rows = rows
      · simp [changedRounds,fixed]
      · have smaller := changed_round_decreases policy rows fixed
        have tail := ih (round policy rows)
        simp only [changedRounds,if_neg fixed]
        omega

theorem equality_checks_bounded (policy : Policy) (fuel : Nat) (rows : List Row) :
    equalityChecks policy fuel rows ≤ rowsWeight rows + 1 := by
  induction fuel generalizing rows with
  | zero => simp [equalityChecks]
  | succ n ih =>
      by_cases fixed : round policy rows = rows
      · simp [equalityChecks,fixed]
      · have smaller := changed_round_decreases policy rows fixed
        have tail := ih (round policy rows)
        simp only [equalityChecks,if_neg fixed]
        omega

structure RowKey where
  role : Role
  itemId : Nat
  originalOrdinal : Nat
  positionMetadata : List Position
  deriving DecidableEq, Repr

def rowKey (row : Row) : RowKey :=
  ⟨row.role,row.bound.itemId,row.originalOrdinal,row.bound.positionMetadata⟩

inductive Event where
  | action (key : RowKey) (action : Action)
  | sink (key : RowKey)
  deriving DecidableEq, Repr

/-- Sink reports are independent of action-list size. Source-hit counts and
    row-enumeration diagnostics are deliberately not in this observation. -/
def events (marks : Position → FullMark → Bool) (other : Nat → Bool) (row : Row) : List Event :=
  if eval marks other row.bound.guard then
    (if row.role = .sink then [.sink (rowKey row)] else []) ++
      row.bound.actions.map (Event.action (rowKey row))
  else []

def execute (marks : Position → FullMark → Bool) (other : Nat → Bool) (rows : List Row) : List Event :=
  rows.flatMap (events marks other)

theorem discarded_row_has_no_events (marks : Position → FullMark → Bool) (other : Nat → Bool)
    (row : Row) (discarded : keepRow row ≠ true) : events marks other row = [] := by
  by_cases dead : row.bound.guard = .truth false
  · simp [events,dead,eval]
  · by_cases empty : row.bound.actions = []
    · cases role : row.role <;> simp_all [keepRow,events]
    · cases role : row.role <;> simp_all [keepRow]

theorem compact_preserves_execution_effects (marks : Position → FullMark → Bool) (other : Nat → Bool)
    (rows : List Row) : execute marks other (compact rows) = execute marks other rows := by
  induction rows with
  | nil => rfl
  | cons row rest ih =>
      by_cases keep : keepRow row = true
      · simpa only [compact,List.filter_cons,if_pos keep,execute,List.flatMap_cons] using
          congrArg (fun tail => events marks other row ++ tail) ih
      · have silent := discarded_row_has_no_events marks other row keep
        simpa only [compact,List.filter_cons,if_neg keep,execute,List.flatMap_cons,silent,List.nil_append] using ih

/-- Retained rule identities/order and exact action identities/order. -/
theorem compact_keys_sublist (rows : List Row) :
    ((compact rows).map rowKey).Sublist (rows.map rowKey) := (compact_sublist rows).map rowKey

theorem rewriteRow_key (available needed : List FullMark) (row : Row) :
    rowKey (rewriteRow available needed row) = rowKey row := rfl

theorem rewritePass_keys (policy : Policy) (rows : List Row) :
    (rewritePass policy rows).map rowKey = rows.map rowKey := by
  simp only [rewritePass]
  split
  · rfl
  · simp [List.map_map,rewriteRow_key]

theorem round_keys_sublist (policy : Policy) (rows : List Row) :
    ((round policy rows).map rowKey).Sublist (rows.map rowKey) := by
  have h := compact_keys_sublist (rewritePass policy rows)
  rw [rewritePass_keys] at h
  exact h

theorem iterate_keys_sublist (policy : Policy) (fuel : Nat) (rows : List Row) :
    ((iterate policy fuel rows).map rowKey).Sublist (rows.map rowKey) := by
  induction fuel generalizing rows with
  | zero => exact .refl _
  | succ n ih => exact (ih (round policy rows)).trans (round_keys_sublist policy rows)

theorem normalizeTotal_keys_sublist (policy : Policy) (rows : List Row) :
    ((normalizeTotal policy rows).map rowKey).Sublist (rows.map rowKey) := by
  simp only [normalizeTotal,stopAtFixed_eq_iterate]
  exact iterate_keys_sublist policy _ rows

def RefinesRow (new old : Row) : Prop :=
  rowKey new = rowKey old ∧ new.bound.actions.Sublist old.bound.actions

theorem rewriteRow_refines (available needed : List FullMark) (row : Row) :
    RefinesRow (rewriteRow available needed row) row :=
  ⟨rfl,pruneActions_sublist needed row.bound.actions⟩

theorem RefinesRow.trans {first second third : Row}
    (left : RefinesRow first second) (right : RefinesRow second third) : RefinesRow first third :=
  ⟨left.1.trans right.1,left.2.trans right.2⟩

theorem round_refines_rows (policy : Policy) (rows : List Row) (row : Row) (present : row ∈ round policy rows) :
    ∃ old ∈ rows, RefinesRow row old := by
  have inRewrite := (List.mem_filter.mp present).1
  by_cases all : policy.protectAll = true
  · simp only [rewritePass,all,↓reduceIte] at inRewrite
    exact ⟨row,inRewrite,rfl,.refl _⟩
  · simp only [rewritePass,all,Bool.false_eq_true,↓reduceIte] at inRewrite
    rcases List.mem_map.mp inRewrite with ⟨old,member,eq⟩
    subst row
    exact ⟨old,member,rewriteRow_refines _ _ old⟩

theorem iterate_refines_rows (policy : Policy) (fuel : Nat) (rows : List Row) (row : Row)
    (present : row ∈ iterate policy fuel rows) : ∃ old ∈ rows, RefinesRow row old := by
  induction fuel generalizing rows with
  | zero => exact ⟨row,present,rfl,.refl _⟩
  | succ n ih =>
      rcases ih (round policy rows) present with ⟨middle,middlePresent,first⟩
      rcases round_refines_rows policy rows middle middlePresent with ⟨old,oldPresent,second⟩
      exact ⟨old,oldPresent,first.trans second⟩

theorem normalizeTotal_retains_action_ordinals (policy : Policy) (rows : List Row) (row : Row)
    (present : row ∈ normalizeTotal policy rows) : ∃ old ∈ rows, RefinesRow row old := by
  simp only [normalizeTotal,stopAtFixed_eq_iterate] at present
  exact iterate_refines_rows policy _ rows row present

theorem underlying_rewritePass (policy : Policy) (rows : List Row) :
    underlying (rewritePass policy rows) = step policy (underlying rows) := by
  simp only [rewritePass,step]
  split
  · rfl
  · simp [underlying,List.map_map,rewriteRow,producers,consumers]

theorem checked_sublist (policy : Policy) {small large : List Rule}
    (sub : small.Sublist large) (m : FullMark) : m ∈ checked policy small → m ∈ checked policy large := by
  intro h
  simp only [checked,List.mem_append] at h ⊢
  rcases h with external | used
  · exact Or.inl external
  · right
    rcases List.mem_flatMap.mp used with ⟨r,present,use⟩
    exact List.mem_flatMap.mpr ⟨r,sub.subset present,use⟩

theorem assigned_sublist (policy : Policy) {small large : List Rule}
    (sub : small.Sublist large) (m : FullMark) : m ∈ assigned policy small → m ∈ assigned policy large := by
  intro h
  simp only [assigned,List.mem_append] at h ⊢
  rcases h with external | produced
  · exact Or.inl external
  · right
    rcases List.mem_flatMap.mp produced with ⟨r,present,mark⟩
    exact List.mem_flatMap.mpr ⟨r,sub.subset present,mark⟩

theorem round_consumers_subset (policy : Policy) (rows : List Row) (m : FullMark) :
    m ∈ consumers policy (round policy rows) → m ∈ consumers policy rows := by
  intro h
  have sub := (compact_sublist (rewritePass policy rows)).map Row.bound
  have h' := checked_sublist policy sub m h
  change m ∈ checked policy (underlying (rewritePass policy rows)) at h'
  rw [underlying_rewritePass] at h'
  exact step_checked_subset policy (underlying rows) m h'

theorem round_producers_subset (policy : Policy) (rows : List Row) (m : FullMark) :
    m ∈ producers policy (round policy rows) → m ∈ producers policy rows := by
  intro h
  have sub := (compact_sublist (rewritePass policy rows)).map Row.bound
  have h' := assigned_sublist policy sub m h
  change m ∈ assigned policy (underlying (rewritePass policy rows)) at h'
  rw [underlying_rewritePass] at h'
  exact step_assigned_subset policy (underlying rows) m h'

theorem normalizeTotal_consumers_subset (policy : Policy) (rows : List Row) (m : FullMark) :
    m ∈ consumers policy (normalizeTotal policy rows) → m ∈ consumers policy rows := by
  suffices allFuel : ∀ n rs, m ∈ consumers policy (iterate policy n rs) → m ∈ consumers policy rs by
    simpa only [normalizeTotal,stopAtFixed_eq_iterate] using allFuel (rowsWeight rows + 1) rows
  intro n
  induction n with
  | zero => exact fun rs h => h
  | succ n ih => exact fun rs h => round_consumers_subset policy rs m (ih (round policy rs) h)

theorem normalizeTotal_producers_subset (policy : Policy) (rows : List Row) (m : FullMark) :
    m ∈ producers policy (normalizeTotal policy rows) → m ∈ producers policy rows := by
  suffices allFuel : ∀ n rs, m ∈ producers policy (iterate policy n rs) → m ∈ producers policy rs by
    simpa only [normalizeTotal,stopAtFixed_eq_iterate] using allFuel (rowsWeight rows + 1) rows
  intro n
  induction n with
  | zero => exact fun rs h => h
  | succ n ih => exact fun rs h => round_producers_subset policy rs m (ih (round policy rs) h)

theorem fixed_rows_keep (policy : Policy) (rows : List Row) (fixed : round policy rows = rows)
    (row : Row) (present : row ∈ rows) : keepRow row = true := by
  rw [← fixed] at present
  exact (List.mem_filter.mp present).2

theorem total_rows_have_nonfalse_guard (policy : Policy) (rows : List Row) (row : Row)
    (present : row ∈ normalizeTotal policy rows) : row.bound.guard ≠ .truth false := by
  have keep := fixed_rows_keep policy _ (normalizeTotal_fixed policy rows) row present
  intro dead
  simp [keepRow,dead] at keep

theorem total_source_cleaner_have_actions (policy : Policy) (rows : List Row) (row : Row)
    (present : row ∈ normalizeTotal policy rows) (producer : row.role = .source ∨ row.role = .cleaner) :
    row.bound.actions ≠ [] := by
  have keep := fixed_rows_keep policy _ (normalizeTotal_fixed policy rows) row present
  intro empty
  rcases producer with role | role <;> simp [keepRow,role,empty] at keep

/-- Only checked/protected action events are observed after mark projection;
    every sink event remains independent of its action list. -/
def observedEvents (needed : List FullMark) (marks : Position → FullMark → Bool)
    (other : Nat → Bool) (row : Row) : List Event :=
  if eval marks other row.bound.guard then
    (if row.role = .sink then [.sink (rowKey row)] else []) ++
      (observeActions needed row.bound.actions).map (Event.action (rowKey row))
  else []

def observedExecute (needed : List FullMark) (marks : Position → FullMark → Bool)
    (other : Nat → Bool) (rows : List Row) : List Event := rows.flatMap (observedEvents needed marks other)

theorem discarded_row_has_no_observed_events (needed : List FullMark)
    (marks : Position → FullMark → Bool) (other : Nat → Bool) (row : Row) (drop : keepRow row ≠ true) :
    observedEvents needed marks other row = [] := by
  by_cases dead : row.bound.guard = .truth false
  · simp [observedEvents,dead,eval]
  · by_cases empty : row.bound.actions = []
    · cases role : row.role <;> simp_all [keepRow,observedEvents,observeActions,pruneActions]
    · cases role : row.role <;> simp_all [keepRow]

theorem compact_preserves_observed_events (needed : List FullMark)
    (marks : Position → FullMark → Bool) (other : Nat → Bool) (rows : List Row) :
    observedExecute needed marks other (compact rows) = observedExecute needed marks other rows := by
  induction rows with
  | nil => rfl
  | cons row rest ih =>
      by_cases keep : keepRow row = true
      · simpa only [compact,List.filter_cons,if_pos keep,observedExecute,List.flatMap_cons] using
          congrArg (fun tail => observedEvents needed marks other row ++ tail) ih
      · have silent := discarded_row_has_no_observed_events needed marks other row keep
        simpa only [compact,List.filter_cons,if_neg keep,observedExecute,List.flatMap_cons,silent,List.nil_append] using ih

theorem rewriteRow_preserves_checked_events (available needed : List FullMark)
    (marks : Position → FullMark → Bool) (other : Nat → Bool)
    (cover : AssignedCover available marks) (row : Row) :
    observedEvents needed marks other (rewriteRow available needed row) = observedEvents needed marks other row := by
  simp only [observedEvents,rewriteRow,rewriteRule,fold_truth_equivalent available marks other cover]
  rw [checked_action_outputs_order_ids]
  rfl

theorem round_preserves_current_checked_events (policy : Policy)
    (marks : Position → FullMark → Bool) (other : Nat → Bool) (rows : List Row)
    (cover : AssignedCover (producers policy rows) marks) :
    observedExecute (consumers policy rows) marks other (round policy rows) =
      observedExecute (consumers policy rows) marks other rows := by
  rw [round,compact_preserves_observed_events]
  simp only [rewritePass]
  split
  · rfl
  · simp only [observedExecute,List.flatMap_map]
    apply congrArg (fun f => rows.flatMap f)
    funext r
    exact rewriteRow_preserves_checked_events _ _ marks other cover r

theorem fixed_rewritePass (policy : Policy) (rows : List Row) (fixed : round policy rows = rows) :
    rewritePass policy rows = rows := by
  rcases rewritePass_same_or_strict policy rows with same | smaller
  · exact same
  · have bound := compact_weight_nonincrease (rewritePass policy rows)
    change rowsWeight (round policy rows) ≤ rowsWeight (rewritePass policy rows) at bound
    rw [fixed] at bound
    omega

theorem fixed_underlying_step (policy : Policy) (rows : List Row) (fixed : round policy rows = rows) :
    step policy (underlying rows) = underlying rows := by
  rw [← underlying_rewritePass, fixed_rewritePass policy rows fixed]

theorem total_cm_has_producer (policy : Policy) (rows : List Row) (closed : policy.protectAll = false)
    (row : Row) (present : row ∈ normalizeTotal policy rows) (m : FullMark) (used : m ∈ uses row.bound.guard) :
    m ∈ producers policy (normalizeTotal policy rows) := by
  exact fixed_cm_has_producer policy _ closed
    (fixed_underlying_step policy _ (normalizeTotal_fixed policy rows)) row.bound
    (List.mem_map.mpr ⟨row,present,rfl⟩) m used

theorem total_GEN_is_checked (policy : Policy) (rows : List Row) (closed : policy.protectAll = false)
    (row : Row) (present : row ∈ normalizeTotal policy rows) (id : Nat) (target : Position)
    (m : FullMark) (payload : Nat) (action : Action.gen id target m payload ∈ row.bound.actions) :
    m ∈ consumers policy (normalizeTotal policy rows) := by
  exact fixed_gen_is_checked policy _ closed
    (fixed_underlying_step policy _ (normalizeTotal_fixed policy rows)) row.bound
    (List.mem_map.mpr ⟨row,present,rfl⟩) id target m payload action

/-- Equality of executable folding when inventories agree on every CM key. -/
theorem fold_congr_on_uses (first second : List FullMark) (e : Expr)
    (same : ∀ mark, mark ∈ uses e → (mark ∈ first ↔ mark ∈ second)) :
    fold first e = fold second e := by
  induction e with
  | truth value => rfl
  | other id => rfl
  | cm position mark =>
      have agree := same mark (by simp [uses])
      by_cases present : mark ∈ first
      · simp [fold,present,agree.mp present]
      · have absent : mark ∉ second := fun h => present (agree.mpr h)
        simp [fold,present,absent]
  | neg e ih => simp only [fold,ih same]
  | conj a b ia ib =>
      simp only [fold,ia (fun mark h => same mark (List.mem_append_left _ h)),
        ib (fun mark h => same mark (List.mem_append_right _ h))]
  | disj a b ia ib =>
      simp only [fold,ia (fun mark h => same mark (List.mem_append_left _ h)),
        ib (fun mark h => same mark (List.mem_append_right _ h))]

theorem row_CM_is_consumer (policy : Policy) (rows : List Row) (row : Row) (present : row ∈ rows)
    (mark : FullMark) (used : mark ∈ uses row.bound.guard) : mark ∈ consumers policy rows := by
  apply List.mem_append_right
  exact List.mem_flatMap.mpr ⟨row.bound,List.mem_map.mpr ⟨row,present,rfl⟩,used⟩

theorem row_GEN_is_producer (policy : Policy) (rows : List Row) (row : Row) (present : row ∈ rows)
    (liveGuard : row.bound.guard ≠ .truth false) (id : Nat) (target : Position) (mark : FullMark) (payload : Nat)
    (action : Action.gen id target mark payload ∈ row.bound.actions) : mark ∈ producers policy rows := by
  apply List.mem_append_right
  apply List.mem_flatMap.mpr
  refine ⟨row.bound,List.mem_map.mpr ⟨row,present,rfl⟩,?_⟩
  simp only [assignedBy,if_neg liveGuard]
  exact List.mem_filterMap.mpr ⟨.gen id target mark payload,action,rfl⟩

theorem fold_live_eq_available (policy : Policy) (rows : List Row) (row : Row) (present : row ∈ rows) :
    fold (liveKeys policy rows) row.bound.guard = fold (producers policy rows) row.bound.guard := by
  apply fold_congr_on_uses
  intro mark used
  rw [live_keys_member]
  exact ⟨And.left,fun produced => ⟨produced,row_CM_is_consumer policy rows row present mark used⟩⟩

theorem prune_live_eq_checked (policy : Policy) (rows : List Row) (row : Row) (present : row ∈ rows)
    (liveGuard : row.bound.guard ≠ .truth false) :
    pruneActions (liveKeys policy rows) row.bound.actions = pruneActions (consumers policy rows) row.bound.actions := by
  apply List.filter_congr
  intro action member
  cases action with
  | other id payload => rfl
  | gen id target mark payload =>
      have produced := row_GEN_is_producer policy rows row present liveGuard id target mark payload member
      simp only [keepAction]
      have agree : mark ∈ liveKeys policy rows ↔ mark ∈ consumers policy rows := by
        rw [live_keys_member]
        exact ⟨And.right,fun used => ⟨produced,used⟩⟩
      simp [agree]

/-- The dead payload is kept on its compatibility path before certified
    compaction. Every executable nonfalse row uses the common L for both. -/
def liveRewriteRow (policy : Policy) (rows : List Row) (row : Row) : Row :=
  if row.bound.guard = .truth false then
    rewriteRow (producers policy rows) (consumers policy rows) row
  else rewriteRow (liveKeys policy rows) (liveKeys policy rows) row

theorem liveRewriteRow_eq_original (policy : Policy) (rows : List Row) (row : Row) (present : row ∈ rows) :
    liveRewriteRow policy rows row = rewriteRow (producers policy rows) (consumers policy rows) row := by
  by_cases dead : row.bound.guard = .truth false
  · simp [liveRewriteRow,dead]
  · have guardEq := fold_live_eq_available policy rows row present
    have actionEq := prune_live_eq_checked policy rows row present dead
    simp only [liveRewriteRow,if_neg dead,rewriteRow,rewriteRule,guardEq,actionEq]

/-- Same total pass, with L used explicitly at every nonfalse row. -/
def liveRound (policy : Policy) (rows : List Row) : List Row :=
  compact (if policy.protectAll then rows else rows.map (liveRewriteRow policy rows))

theorem liveRound_eq_round (policy : Policy) (rows : List Row) : liveRound policy rows = round policy rows := by
  by_cases all : policy.protectAll = true
  · simp [liveRound,round,rewritePass,all]
  · simp only [liveRound,round,rewritePass,all,Bool.false_eq_true,↓reduceIte]
    apply congrArg compact
    exact List.map_congr_left (fun row member => liveRewriteRow_eq_original policy rows row member)

inductive MetadataAction where
  | gen (originalOrdinal : Nat) (target : Position) (mark : FullMark) (payload : Nat)
  | removeMark (originalOrdinal : Nat) (target : Position) (mark : FullMark) (payload : Nat)
  | removeAllMarks (originalOrdinal : Nat) (target : Position)
  | structural (originalOrdinal payload : Nat)
  | other (originalOrdinal payload : Nat)
  deriving DecidableEq, Repr

/-- The raw marked action supplies a POSITION for derived resets; its own
    mark is not the derived RemoveMark target. Unknown roles/actions are kept. -/
def carrierPosition (role : Role) : MetadataAction → Option Position
  | .gen _ position _ _ => if role = .source then some position else none
  | .removeMark _ position _ _ => if role = .cleaner then some position else none
  | _ => none

def keepMetadataAction (role : Role) (liveTargets : List FullMark) (action : MetadataAction) : Bool :=
  match carrierPosition role action with
  | some _ => decide (liveTargets ≠ [])
  | none => true

structure MetadataRow where
  role : Role
  itemId : Nat
  originalOrdinal : Nat
  guard : Expr
  actions : List MetadataAction
  positionMetadata : List Position
  controlledMarks : List FullMark
  deriving DecidableEq, Repr

def filterMetadataActions (live : List FullMark) (row : MetadataRow) : MetadataRow :=
  let targets := row.controlledMarks.filter (fun mark => decide (mark ∈ live))
  { row with
    controlledMarks := targets
    actions := row.actions.filter (keepMetadataAction row.role targets) }

def keepMetadataRow (row : MetadataRow) : Bool :=
  decide (row.guard ≠ .truth false) && match row.role with
    | .source | .cleaner => !row.actions.isEmpty
    | .sink | .pass => true

/-- Final executable LIVE keys are supplied; this function does not feed them. -/
def metadataView (finalExecutableLive : List FullMark) (rows : List MetadataRow) : List MetadataRow :=
  (rows.map (filterMetadataActions finalExecutableLive)).filter keepMetadataRow

def compactMetadata (rows : List MetadataRow) : List MetadataRow := rows.filter keepMetadataRow

/-- Unknown executable live scope means TOP, not an empty finite set.
    Only certified false/actionless role compaction remains on that path. -/
def metadataForPolicy (policy : Policy) (finalExecutableLive : List FullMark)
    (rows : List MetadataRow) : List MetadataRow :=
  if policy.protectAll then compactMetadata rows else metadataView finalExecutableLive rows

theorem protectAll_metadata_keeps_mark_actions (policy : Policy) (used : List FullMark)
    (rows : List MetadataRow) (all : policy.protectAll = true) :
    metadataForPolicy policy used rows = compactMetadata rows := by simp [metadataForPolicy,all]

theorem metadata_action_filter_sublist (used : List FullMark) (row : MetadataRow) :
    (filterMetadataActions used row).actions.Sublist row.actions := List.filter_sublist

theorem metadata_filter_identity (used : List FullMark) (row : MetadataRow) :
    (filterMetadataActions used row).itemId = row.itemId ∧
    (filterMetadataActions used row).originalOrdinal = row.originalOrdinal ∧
    (filterMetadataActions used row).guard = row.guard ∧
    (filterMetadataActions used row).positionMetadata = row.positionMetadata := ⟨rfl,rfl,rfl,rfl⟩

def metadataKey (row : MetadataRow) : Role × Nat × Nat × Expr × List Position :=
  (row.role,row.itemId,row.originalOrdinal,row.guard,row.positionMetadata)

theorem metadata_keys_sublist (used : List FullMark) (rows : List MetadataRow) :
    ((metadataView used rows).map metadataKey).Sublist (rows.map metadataKey) := by
  have sub : ((rows.map (filterMetadataActions used)).filter keepMetadataRow).Sublist
      (rows.map (filterMetadataActions used)) := List.filter_sublist
  have keys := sub.map metadataKey
  have sameKey : metadataKey ∘ filterMetadataActions used = metadataKey := by
    funext row
    rfl
  simpa only [metadataView,List.map_map,sameKey] using keys

theorem unmarked_RemoveAll_retained (used : List FullMark) (ordinal : Nat) (target : Position) :
    keepMetadataAction .cleaner used (.removeAllMarks ordinal target) = true := rfl

theorem unmarked_structural_retained (used : List FullMark) (ordinal payload : Nat) :
    keepMetadataAction .cleaner used (.structural ordinal payload) = true := rfl

theorem projected_controlled_marks_sublist (live : List FullMark) (row : MetadataRow) :
    (filterMetadataActions live row).controlledMarks.Sublist row.controlledMarks := List.filter_sublist

theorem projected_controlled_mark_is_live (live : List FullMark) (row : MetadataRow) (mark : FullMark)
    (member : mark ∈ (filterMetadataActions live row).controlledMarks) : mark ∈ live := by
  exact of_decide_eq_true (List.mem_filter.mp member).2

theorem supported_positions_keep_raw_actions (live : List FullMark) (row : MetadataRow)
    (supported : (filterMetadataActions live row).controlledMarks ≠ []) :
    (filterMetadataActions live row).actions = row.actions := by
  dsimp only [filterMetadataActions] at supported ⊢
  apply List.filter_eq_self.mpr
  intro action member
  cases h : carrierPosition row.role action with
  | none => simp [keepMetadataAction,h]
  | some position => simpa only [keepMetadataAction,h] using decide_eq_true supported

/-- Derived removals retain BOTH raw carrier ordinal and original controlled
    mark ordinal. The helper filters actual RemoveMark targets, not GEN keys. -/
structure DerivedReset where
  carrierOrdinal : Nat
  target : Position
  originalMarkOrdinal : Nat
  mark : FullMark
  deriving DecidableEq, Repr

def carrierOrdinal : MetadataAction → Nat
  | .gen ordinal _ _ _ | .removeMark ordinal _ _ _ | .removeAllMarks ordinal _ => ordinal
  | .structural ordinal _ | .other ordinal _ => ordinal

def originalRemovals (row : MetadataRow) : List DerivedReset :=
  row.actions.flatMap fun action => match carrierPosition row.role action with
    | none => []
    | some position => row.controlledMarks.zipIdx.map fun (mark,ordinal) =>
        ⟨carrierOrdinal action,position,ordinal,mark⟩

def effectiveRemovals (live : List FullMark) (row : MetadataRow) : List DerivedReset :=
  (originalRemovals row).filter (fun reset => decide (reset.mark ∈ live))

theorem effective_removals_keep_order_and_ordinals (live : List FullMark) (row : MetadataRow) :
    (effectiveRemovals live row).Sublist (originalRemovals row) := List.filter_sublist

theorem effective_removal_target_is_live (live : List FullMark) (row : MetadataRow) (reset : DerivedReset)
    (member : reset ∈ effectiveRemovals live row) : reset.mark ∈ live :=
  of_decide_eq_true (List.mem_filter.mp member).2

structure Snapshot where
  execution : List Row
  metadata : List MetadataRow
  declarations : List FullMark
  deriving DecidableEq, Repr

/-- Metadata is filtered ONLY after the compacted executable loop is fixed. -/
def compileSnapshot (policy : Policy) (snapshot : Snapshot) : Snapshot :=
  let executable := normalizeTotal policy snapshot.execution
  { execution := executable,
    metadata := metadataForPolicy policy (liveKeys policy executable) snapshot.metadata,
    declarations := snapshot.declarations }

theorem compileSnapshot_execution_fixed (policy : Policy) (snapshot : Snapshot) :
    round policy (compileSnapshot policy snapshot).execution = (compileSnapshot policy snapshot).execution :=
  normalizeTotal_fixed policy snapshot.execution

theorem compileSnapshot_declarations_unchanged (policy : Policy) (snapshot : Snapshot) :
    (compileSnapshot policy snapshot).declarations = snapshot.declarations := rfl

theorem compileSnapshot_final_executable_live_keys (policy : Policy) (snapshot : Snapshot) :
    (compileSnapshot policy snapshot).metadata =
      metadataForPolicy policy (liveKeys policy (compileSnapshot policy snapshot).execution) snapshot.metadata := rfl

theorem compileSnapshot_execution_independent_of_metadata (policy : Policy) (snapshot : Snapshot)
    (different : List MetadataRow) :
    (compileSnapshot policy {snapshot with metadata := different}).execution =
      (compileSnapshot policy snapshot).execution := rfl

/-- Catalog roots/checks are executable-only, for every metadata payload. -/
def snapshotSets (policy : Policy) (rows : List Row) (_metadata : List MetadataRow) : List FullMark × List FullMark :=
  (producers policy rows,consumers policy rows)

theorem metadata_cannot_add_producers_or_checks (policy : Policy) (rows : List Row)
    (old new : List MetadataRow) : snapshotSets policy rows old = snapshotSets policy rows new := rfl

/-- Position-derived reset geometry. Projection must preserve live targets
    even when the carrier's OWN assignment/cleaner mark is not live. -/
def resetPositions (row : MetadataRow) : List Position := row.actions.filterMap (carrierPosition row.role)

def resets (row : MetadataRow) : List (Position × FullMark) :=
  (resetPositions row).flatMap fun position => row.controlledMarks.map (fun mark => (position,mark))

theorem no_targets_make_no_resets (positions : List Position) :
    positions.flatMap (fun _ => ([] : List (Position × FullMark))) = [] := by
  induction positions with
  | nil => rfl
  | cons position rest ih => simp [ih]

/-- No reset geometry is lost for a retained target. Filtering own GEN names
    would not satisfy this exact ordered equality. -/
theorem projected_reset_product (live : List FullMark) (row : MetadataRow) :
    resets (filterMetadataActions live row) =
      (resetPositions row).flatMap (fun position =>
        (row.controlledMarks.filter (fun mark => decide (mark ∈ live))).map (fun mark => (position,mark))) := by
  by_cases empty : (filterMetadataActions live row).controlledMarks = []
  · have projectedEmpty : row.controlledMarks.filter (fun mark => decide (mark ∈ live)) = [] := empty
    simp only [resets,empty,projectedEmpty,List.map_nil,no_targets_make_no_resets]
  · have sameActions := supported_positions_keep_raw_actions live row empty
    change ((filterMetadataActions live row).actions.filterMap (carrierPosition row.role)).flatMap
      (fun position => (filterMetadataActions live row).controlledMarks.map (fun mark => (position,mark))) = _
    rw [sameActions]
    rfl

theorem filter_marks_at_position (live : List FullMark) (position : Position) (marks : List FullMark) :
    (marks.filter (fun mark => decide (mark ∈ live))).map (fun mark => (position,mark)) =
      (marks.map (fun mark => (position,mark))).filter (fun pair => decide (pair.2 ∈ live)) := by
  induction marks with
  | nil => rfl
  | cons mark rest ih =>
      by_cases present : mark ∈ live <;> simp [present,ih]

theorem projected_resets_exact_live_targets (live : List FullMark) (row : MetadataRow) :
    resets (filterMetadataActions live row) =
      (resets row).filter (fun pair => decide (pair.2 ∈ live)) := by
  rw [projected_reset_product]
  simp only [resets,List.filter_flatMap]
  apply congrArg (fun f => (resetPositions row).flatMap f)
  funext position
  exact filter_marks_at_position live position row.controlledMarks


def naiveOwnMarkFilter (live : List FullMark) (row : MetadataRow) : MetadataRow :=
  {row with actions := row.actions.filter fun action => match action with
    | .gen _ _ mark _ | .removeMark _ _ mark _ => decide (mark ∈ live)
    | _ => true}

namespace Controls
open ApSpec.SemgrepReducedMarks.Controls

def emptySource : Row := ⟨.source,7,⟨10,.cm arg0 u,[],[arg0]⟩⟩
def writerU : Row := ⟨.source,8,⟨11,.truth true,[.gen 4 result u 0],[result]⟩⟩
def emptySink : Row := ⟨.sink,9,⟨12,.truth true,[],[]⟩⟩
def emptyCleaner : Row := ⟨.cleaner,10,⟨13,.truth true,[],[]⟩⟩
def emptyPass : Row := ⟨.pass,11,⟨14,.truth true,[],[]⟩⟩
def compactionCascade : List Row := [emptySource,writerU,emptySink]
def sinkT : Row := ⟨.sink,12,⟨15,.cm arg0 t,[],[arg0]⟩⟩
def metadataSource : MetadataRow :=
  ⟨.source,90,20,.truth true,[.gen 7 result u 0],[result],[t,u]⟩
def metadataCleaner : MetadataRow :=
  ⟨.cleaner,91,21,.truth true,
    [.removeMark 1 arg0 u 0,.removeMark 3 arg1 t 0,.removeAllMarks 6 result,.structural 8 9],
    [arg0,arg1,result],[t]⟩

theorem empty_producers_drop_but_sink_and_pass_remain :
    compact [emptySource,emptyCleaner,emptySink,emptyPass] = [emptySink,emptyPass] := by decide

theorem false_sink_dropped_despite_empty_actions :
    compact [{emptySink with bound := {emptySink.bound with guard := .truth false}}] = [] := by decide

theorem actionless_sink_still_reports :
    events (fun _ _ => false) (fun _ => false) emptySink = [.sink (rowKey emptySink)] := by decide

theorem removing_actionless_source_removes_only_use_then_GEN :
    consumers {} compactionCascade = [u] ∧
    round {} compactionCascade = [writerU,emptySink] ∧
    round {} (round {} compactionCascade) = [emptySink] ∧
    producers {} (normalizeTotal {} compactionCascade) = [] ∧
    normalizeTotal {} compactionCascade = [emptySink] := by decide

theorem sink_action_ids_and_original_rule_order_preserved :
    compact [emptySource,{emptySink with originalOrdinal := 40},emptyCleaner,emptySink] =
      [{emptySink with originalOrdinal := 40},emptySink] := by decide

theorem live_T_reset_keeps_unused_U_position_carrier :
    (filterMetadataActions [t] metadataSource).actions = [.gen 7 result u 0] ∧
    (filterMetadataActions [t] metadataSource).controlledMarks = [t] ∧
    resets (filterMetadataActions [t] metadataSource) = [(result,t)] ∧
    effectiveRemovals [t] metadataSource = [⟨7,result,0,t⟩] := by decide

theorem naive_own_mark_filter_is_rejected :
    resets (naiveOwnMarkFilter [t] metadataSource) = [] ∧
    resets (filterMetadataActions [t] metadataSource) = [(result,t)] := by decide

theorem no_live_target_removes_only_carriers_not_RemoveAll_structural :
    (filterMetadataActions [] metadataCleaner).actions = [.removeAllMarks 6 result,.structural 8 9] := by decide

theorem live_target_keeps_cleaner_carriers_even_when_own_mark_unused :
    (filterMetadataActions [t] metadataCleaner).actions = metadataCleaner.actions ∧
    resets (filterMetadataActions [t] metadataCleaner) = [(arg0,t),(arg1,t)] := by decide

theorem actionless_metadata_sink_kept :
    metadataView [] [{metadataSource with role := .sink,actions := []}] =
      [{metadataSource with role := .sink,actions := [],controlledMarks := []}] := by decide

theorem false_rows_drop_for_all_four_roles :
    compact ([emptySource,emptyCleaner,emptySink,emptyPass].map
      (fun row => {row with bound := {row.bound with guard := .truth false}})) = [] := by decide

theorem unmarked_executable_action_keeps_source :
    compact [{emptySource with bound := {emptySource.bound with actions := [.other 17 0]}}] =
      [{emptySource with bound := {emptySource.bound with actions := [.other 17 0]}}] := by decide

theorem complete_owner_keys_filter_derived_targets :
    effectiveRemovals [sameNameOtherOwner] metadataSource = [] := by decide

theorem actionless_metadata_cleaner_drops_without_live_targets :
    metadataView [] [{metadataCleaner with actions := [.removeMark 7 result u 0]}] = [] := by decide

theorem no_metadata_producer_or_CM_feedback :
    snapshotSets {} [emptySink] [metadataSource] = ([],[]) ∧
    (compileSnapshot {} ⟨compactionCascade,[metadataSource],[t,u]⟩).execution = [emptySink] ∧
    (compileSnapshot {} ⟨compactionCascade,[metadataSource],[t,u]⟩).declarations = [t,u] := by decide

theorem cascade_two_changes_and_final_check :
    changedRounds {} (rowsWeight compactionCascade+1) compactionCascade = 2 ∧
    equalityChecks {} (rowsWeight compactionCascade+1) compactionCascade = 3 := by decide

theorem protectAll_metadata_is_TOP_despite_finite_L_empty :
    liveKeys {protectAll := true} [emptySink] = [] ∧
    (compileSnapshot {protectAll := true} ⟨[emptySink],[metadataSource],[t,u]⟩).metadata = [metadataSource] := by decide

theorem assigned_but_unchecked_not_live :
    producers {externalAssigned := [t]} [emptySink] = [t] ∧
    liveKeys {externalAssigned := [t]} [emptySink] = [] ∧
    (compileSnapshot {externalAssigned := [t]} ⟨[emptySink],[metadataSource],[t,u]⟩).metadata = [] := by decide

theorem checked_but_unassigned_not_live :
    consumers {} [sinkT] = [t] ∧ liveKeys {} [sinkT] = [] := by decide

theorem final_live_intersection_drives_metadata :
    liveKeys {externalAssigned := [t]} [sinkT] = [t] ∧
    (compileSnapshot {externalAssigned := [t]} ⟨[sinkT],[metadataSource],[t,u]⟩).metadata =
      [{metadataSource with controlledMarks := [t]}] := by decide

theorem metadata_CM_condition_remains_verbatim :
    (filterMetadataActions [t] {metadataSource with guard := .neg (.cm arg0 u)}).guard =
      .neg (.cm arg0 u) := by decide

theorem live_round_matches_current_round_on_cascade :
    liveRound {} compactionCascade = round {} compactionCascade := by decide

/-- One executable Bool per named closed control above, in source order. -/
def checks : List Bool := [
  decide (
    compact [emptySource,emptyCleaner,emptySink,emptyPass] = [emptySink,emptyPass]),
  decide (
    compact [{emptySink with bound := {emptySink.bound with guard := .truth false}}] = []),
  decide (
    events (fun _ _ => false) (fun _ => false) emptySink = [.sink (rowKey emptySink)]),
  decide (
    consumers {} compactionCascade = [u] ∧
        round {} compactionCascade = [writerU,emptySink] ∧
        round {} (round {} compactionCascade) = [emptySink] ∧
        producers {} (normalizeTotal {} compactionCascade) = [] ∧
        normalizeTotal {} compactionCascade = [emptySink]),
  decide (
    compact [emptySource,{emptySink with originalOrdinal := 40},emptyCleaner,emptySink] =
          [{emptySink with originalOrdinal := 40},emptySink]),
  decide (
    (filterMetadataActions [t] metadataSource).actions = [.gen 7 result u 0] ∧
        (filterMetadataActions [t] metadataSource).controlledMarks = [t] ∧
        resets (filterMetadataActions [t] metadataSource) = [(result,t)] ∧
        effectiveRemovals [t] metadataSource = [⟨7,result,0,t⟩]),
  decide (
    resets (naiveOwnMarkFilter [t] metadataSource) = [] ∧
        resets (filterMetadataActions [t] metadataSource) = [(result,t)]),
  decide (
    (filterMetadataActions [] metadataCleaner).actions = [.removeAllMarks 6 result,.structural 8 9]),
  decide (
    (filterMetadataActions [t] metadataCleaner).actions = metadataCleaner.actions ∧
        resets (filterMetadataActions [t] metadataCleaner) = [(arg0,t),(arg1,t)]),
  decide (
    metadataView [] [{metadataSource with role := .sink,actions := []}] =
          [{metadataSource with role := .sink,actions := [],controlledMarks := []}]),
  decide (
    compact ([emptySource,emptyCleaner,emptySink,emptyPass].map
          (fun row => {row with bound := {row.bound with guard := .truth false}})) = []),
  decide (
    compact [{emptySource with bound := {emptySource.bound with actions := [.other 17 0]}}] =
          [{emptySource with bound := {emptySource.bound with actions := [.other 17 0]}}]),
  decide (
    effectiveRemovals [sameNameOtherOwner] metadataSource = []),
  decide (
    metadataView [] [{metadataCleaner with actions := [.removeMark 7 result u 0]}] = []),
  decide (
    snapshotSets {} [emptySink] [metadataSource] = ([],[]) ∧
        (compileSnapshot {} ⟨compactionCascade,[metadataSource],[t,u]⟩).execution = [emptySink] ∧
        (compileSnapshot {} ⟨compactionCascade,[metadataSource],[t,u]⟩).declarations = [t,u]),
  decide (
    changedRounds {} (rowsWeight compactionCascade+1) compactionCascade = 2 ∧
        equalityChecks {} (rowsWeight compactionCascade+1) compactionCascade = 3),
  decide (
    liveKeys {protectAll := true} [emptySink] = [] ∧
        (compileSnapshot {protectAll := true} ⟨[emptySink],[metadataSource],[t,u]⟩).metadata = [metadataSource]),
  decide (
    producers {externalAssigned := [t]} [emptySink] = [t] ∧
        liveKeys {externalAssigned := [t]} [emptySink] = [] ∧
        (compileSnapshot {externalAssigned := [t]} ⟨[emptySink],[metadataSource],[t,u]⟩).metadata = []),
  decide (
    consumers {} [sinkT] = [t] ∧ liveKeys {} [sinkT] = []),
  decide (
    liveKeys {externalAssigned := [t]} [sinkT] = [t] ∧
        (compileSnapshot {externalAssigned := [t]} ⟨[sinkT],[metadataSource],[t,u]⟩).metadata =
          [{metadataSource with controlledMarks := [t]}]),
  decide (
    (filterMetadataActions [t] {metadataSource with guard := .neg (.cm arg0 u)}).guard =
          .neg (.cm arg0 u)),
  decide (
    liveRound {} compactionCascade = round {} compactionCascade)
]

theorem checks_pass : checks.all id = true := by decide

end Controls
end ApSpec.SemgrepRuleCompaction
