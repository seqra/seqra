/-
  Executable mark-only domain for rule-owned pairs and run-1 group selection.
  ZERO/control is outside this ordinary-taint identifier universe. A registry
  gives each full mark one owner. AP geometry, Kotlin catalog provenance, actual
  source delivery, ND compatibility and backward validation remain adapters.

  Whole-group NORMAL exclusion is a restriction of exact scalar cleaning.
  The missing group-minus-selected-mark is a DEMAND obligation, materialized
  only from actual supplied members and re-executed under exact operations.
  No theorem assumes that a requested group or literal actually exists.
-/
import ApSpec.Basic
namespace ApSpec.RuleGroupMarks
set_option autoImplicit false

structure OwnedMark where
  group : Nat
  full : Nat
  deriving DecidableEq, BEq, Repr

/-- Written explicitly: the toolchain's derived LawfulBEq helper uses choice. -/
instance : ReflBEq OwnedMark where
  rfl := by
    intro mark
    cases mark with
    | mk group full =>
      have hg : (group == group) = true :=
        @ReflBEq.rfl Nat _ Nat.instLawfulBEq.toReflBEq group
      have hf : (full == full) = true :=
        @ReflBEq.rfl Nat _ Nat.instLawfulBEq.toReflBEq full
      rw [instBEqOwnedMark.beq_spec_1,hg,hf]
      rfl

instance : LawfulBEq OwnedMark where
  eq_of_beq := by
    intro a b hit
    cases a with
    | mk ag af =>
      cases b with
      | mk bg bf =>
        rw [instBEqOwnedMark.beq_spec_1] at hit
        have both := Bool.and_eq_true_iff.mp hit
        have hg : ag = bg := eq_of_beq both.1
        have hf : af = bf := eq_of_beq both.2
        cases hg; cases hf; rfl
abbrev OwnerRegistry := Nat → Nat

def OwnerValid (owner : OwnerRegistry) (m : OwnedMark) : Prop := owner m.full = m.group
instance (owner : OwnerRegistry) (m : OwnedMark) : Decidable (OwnerValid owner m) :=
  inferInstanceAs (Decidable (owner m.full = m.group))

def own (owner : OwnerRegistry) (full : Nat) : OwnedMark := ⟨owner full,full⟩
@[simp] theorem own_valid (owner : OwnerRegistry) (full : Nat) :
    OwnerValid owner (own owner full) := rfl
@[simp] theorem own_full (owner : OwnerRegistry) (full : Nat) :
    (own owner full).full = full := rfl

theorem valid_same_full_same_pair (owner : OwnerRegistry) (a b : OwnedMark)
    (ha : OwnerValid owner a) (hb : OwnerValid owner b) (same : a.full = b.full) : a = b := by
  have groups : a.group = b.group := ha.symm.trans ((congrArg owner same).trans hb)
  cases a; cases b; simp_all

theorem valid_pair_eq_iff_full (owner : OwnerRegistry) (a b : OwnedMark)
    (ha : OwnerValid owner a) (hb : OwnerValid owner b) : a = b ↔ a.full = b.full := by
  constructor
  · intro h; exact congrArg OwnedMark.full h
  · exact valid_same_full_same_pair owner a b ha hb

inductive Selector where
  | exact (mark : OwnedMark)
  | group (id : Nat)
  | any
  deriving DecidableEq, Repr

def accepts : Selector → OwnedMark → Bool
  | .exact literal, actual => decide (actual = literal)
  | .group id, actual => decide (actual.group = id)
  | .any, _ => true

def selectRun1 (literal actual : OwnedMark) : Bool := accepts (.group literal.group) actual

def selectLater (literal actual : OwnedMark) : Bool := accepts (.exact literal) actual

@[simp] theorem accepts_exact (literal actual : OwnedMark) :
    accepts (.exact literal) actual = true ↔ actual = literal := by simp [accepts]
@[simp] theorem accepts_group (id : Nat) (actual : OwnedMark) :
    accepts (.group id) actual = true ↔ actual.group = id := by simp [accepts]
@[simp] theorem select_run1_iff (literal actual : OwnedMark) :
    selectRun1 literal actual = true ↔ actual.group = literal.group := by simp [selectRun1]
@[simp] theorem select_later_iff (literal actual : OwnedMark) :
    selectLater literal actual = true ↔ actual = literal := by simp [selectLater]

theorem exact_selection_covered (literal actual : OwnedMark)
    (hit : selectLater literal actual = true) : selectRun1 literal actual = true := by
  have same : actual = literal := (select_later_iff literal actual).mp hit
  subst actual; simp

theorem later_does_not_merge_full_marks (literal actual : OwnedMark)
    (different : actual.full ≠ literal.full) : selectLater literal actual = false := by
  have ne : actual ≠ literal := fun same => different (congrArg OwnedMark.full same)
  simp [selectLater,accepts,ne]

/-- Lists are finite membership presentations, not physical deduplicated stores. -/
def markUnion (xs ys : List OwnedMark) : List OwnedMark := xs ++ ys

def markInter (xs ys : List OwnedMark) : List OwnedMark :=
  xs.filter (fun m => decide (m ∈ ys))
@[simp] theorem mem_markUnion (m : OwnedMark) (xs ys : List OwnedMark) :
    m ∈ markUnion xs ys ↔ m ∈ xs ∨ m ∈ ys := by simp [markUnion]
@[simp] theorem mem_markInter (m : OwnedMark) (xs ys : List OwnedMark) :
    m ∈ markInter xs ys ↔ m ∈ xs ∧ m ∈ ys := by simp [markInter]

/-- Admission filters an actual supply; it does not enumerate a group's universe. -/
def materialize (group : Nat) (actual : List OwnedMark) : List OwnedMark :=
  actual.filter (fun m => decide (m.group = group))
@[simp] theorem mem_materialize (group : Nat) (actual : List OwnedMark) (m : OwnedMark) :
    m ∈ materialize group actual ↔ m ∈ actual ∧ m.group = group := by simp [materialize]

theorem materialize_no_fabrication (group : Nat) (actual : List OwnedMark) (m : OwnedMark)
    (hit : m ∈ materialize group actual) : m ∈ actual := ((mem_materialize _ _ _).mp hit).1
@[simp] theorem materialize_empty (group : Nat) : materialize group [] = [] := rfl

theorem materialize_union (group : Nat) (xs ys : List OwnedMark) :
    materialize group (markUnion xs ys) =
      markUnion (materialize group xs) (materialize group ys) := by
  simp [materialize,markUnion]

theorem materialize_keeps_owners (owner : OwnerRegistry) (group : Nat)
    (actual : List OwnedMark) (valid : ∀ m, m ∈ actual → OwnerValid owner m) :
    ∀ m, m ∈ materialize group actual → OwnerValid owner m := by
  intro m hit; exact valid m (materialize_no_fabrication group actual m hit)

structure Flow where
  excludedGroups : List Nat
  demand : Bool := false
  deriving DecidableEq, Repr

def FlowAllows (flow : Flow) (mark : OwnedMark) : Prop := mark.group ∉ flow.excludedGroups
instance (flow : Flow) (mark : OwnedMark) : Decidable (FlowAllows flow mark) :=
  inferInstanceAs (Decidable (mark.group ∉ flow.excludedGroups))

def normalComplement (flow : Flow) (literal : OwnedMark) : Flow :=
  {flow with excludedGroups := literal.group :: flow.excludedGroups}

/-- Requests ignore mx. Keep the literal separately for the concrete handoff. -/
def groupInterest (_flow : Flow) (literal : OwnedMark) : Nat := literal.group

def concreteObligation (_flow : Flow) (literal : OwnedMark) : OwnedMark := literal
@[simp] theorem interest_ignores_exclusions (a b : Flow) (literal : OwnedMark) :
    groupInterest a literal = groupInterest b literal := rfl
@[simp] theorem obligation_keeps_full (flow : Flow) (literal : OwnedMark) :
    (concreteObligation flow literal).full = literal.full := rfl
@[simp] theorem excluded_group_still_requested (flow : Flow) (literal : OwnedMark) :
    groupInterest (normalComplement flow literal) literal = literal.group := rfl
@[simp] theorem complement_preserves_layer (flow : Flow) (literal : OwnedMark) :
    (normalComplement flow literal).demand = flow.demand := rfl
@[simp] theorem complement_allows_iff (flow : Flow) (literal actual : OwnedMark) :
    FlowAllows (normalComplement flow literal) actual ↔
      FlowAllows flow actual ∧ actual.group ≠ literal.group := by
  simp [FlowAllows,normalComplement,not_or,and_comm]

/-- Exact ordinary scalar cleaning of the concretization of this input FLOW. -/
def exactSurvivors (flow : Flow) (literal : OwnedMark) (actual : List OwnedMark) : List OwnedMark :=
  actual.filter (fun m => decide (FlowAllows flow m ∧ m ≠ literal))

def normalSurvivors (flow : Flow) (literal : OwnedMark) (actual : List OwnedMark) : List OwnedMark :=
  actual.filter (fun m => decide (FlowAllows (normalComplement flow literal) m))

/-- Restored actual inputs undergo the exact cleaner. Delivery/re-execution is
    a protocol obligation; a group request alone creates no member here. -/
def demandRepair (flow : Flow) (literal : OwnedMark) (actual : List OwnedMark) : List OwnedMark :=
  (materialize literal.group actual).filter
    (fun m => decide (FlowAllows flow m ∧ m ≠ literal))

def repairLayer : Bool := true
@[simp] theorem repair_is_demand : repairLayer = true := rfl
@[simp] theorem mem_exactSurvivors (flow : Flow) (literal m : OwnedMark) (actual : List OwnedMark) :
    m ∈ exactSurvivors flow literal actual ↔ m ∈ actual ∧ FlowAllows flow m ∧ m ≠ literal := by
  simp [exactSurvivors]
@[simp] theorem mem_normalSurvivors (flow : Flow) (literal m : OwnedMark) (actual : List OwnedMark) :
    m ∈ normalSurvivors flow literal actual ↔
      m ∈ actual ∧ FlowAllows flow m ∧ m.group ≠ literal.group := by
  simp [normalSurvivors]
@[simp] theorem mem_demandRepair (flow : Flow) (literal m : OwnedMark) (actual : List OwnedMark) :
    m ∈ demandRepair flow literal actual ↔
      m ∈ actual ∧ FlowAllows flow m ∧ m.group = literal.group ∧ m ≠ literal := by
  simp [demandRepair,materialize,and_assoc,and_left_comm,and_comm]

/-- Excluding the whole group is a sound NORMAL restriction, not scalar equality. -/
theorem normal_complement_sound (flow : Flow) (literal m : OwnedMark) (actual : List OwnedMark)
    (kept : m ∈ normalSurvivors flow literal actual) : m ∈ exactSurvivors flow literal actual := by
  obtain ⟨present,allowed,differentGroup⟩ := (mem_normalSurvivors _ _ _ _).mp kept
  refine (mem_exactSurvivors _ _ _ _).mpr ⟨present,allowed,?_⟩
  intro same; exact differentGroup (congrArg OwnedMark.group same)

/-- Every exact survivor is NORMAL or in the actual demanded g minus {T} repair. -/
theorem exact_clean_partition (flow : Flow) (literal m : OwnedMark) (actual : List OwnedMark) :
    m ∈ exactSurvivors flow literal actual ↔
      m ∈ normalSurvivors flow literal actual ∨ m ∈ demandRepair flow literal actual := by
  constructor
  · intro kept
    obtain ⟨present,allowed,different⟩ := (mem_exactSurvivors _ _ _ _).mp kept
    by_cases sameGroup : m.group = literal.group
    · exact Or.inr ((mem_demandRepair _ _ _ _).mpr ⟨present,allowed,sameGroup,different⟩)
    · exact Or.inl ((mem_normalSurvivors _ _ _ _).mpr ⟨present,allowed,sameGroup⟩)
  · intro kept
    rcases kept with normal | demand
    · exact normal_complement_sound _ _ _ _ normal
    · obtain ⟨present,allowed,_,different⟩ := (mem_demandRepair _ _ _ _).mp demand
      exact (mem_exactSurvivors _ _ _ _).mpr ⟨present,allowed,different⟩

theorem partition_disjoint (flow : Flow) (literal m : OwnedMark) (actual : List OwnedMark) :
    ¬ (m ∈ normalSurvivors flow literal actual ∧ m ∈ demandRepair flow literal actual) := by
  intro both
  have different := ((mem_normalSurvivors _ _ _ _).mp both.1).2.2
  have same := ((mem_demandRepair _ _ _ _).mp both.2).2.2.1
  exact different same

theorem repair_is_actual (flow : Flow) (literal m : OwnedMark) (actual : List OwnedMark)
    (kept : m ∈ demandRepair flow literal actual) : m ∈ actual :=
  ((mem_demandRepair _ _ _ _).mp kept).1
@[simp] theorem repair_empty (flow : Flow) (literal : OwnedMark) :
    demandRepair flow literal [] = [] := by simp [demandRepair]

theorem cleaned_literal_never_repaired (flow : Flow) (literal : OwnedMark) (actual : List OwnedMark) :
    literal ∉ demandRepair flow literal actual := by simp

/-- Union is sequential exclusion; intersection is the union of alternatives'
    admitted marks. This does not erase full caller/ND support correlation. -/
def exclusionUnion (xs ys : List Nat) : List Nat := xs ++ ys

def exclusionInter (xs ys : List Nat) : List Nat := xs.filter (fun g => decide (g ∈ ys))
@[simp] theorem mem_exclusionUnion (g : Nat) (xs ys : List Nat) :
    g ∈ exclusionUnion xs ys ↔ g ∈ xs ∨ g ∈ ys := by simp [exclusionUnion]
@[simp] theorem mem_exclusionInter (g : Nat) (xs ys : List Nat) :
    g ∈ exclusionInter xs ys ↔ g ∈ xs ∧ g ∈ ys := by simp [exclusionInter]

theorem exclusion_union_denotation (xs ys : List Nat) (m : OwnedMark) :
    m.group ∉ exclusionUnion xs ys ↔ m.group ∉ xs ∧ m.group ∉ ys := by simp

theorem exclusion_inter_denotation (xs ys : List Nat) (m : OwnedMark) :
    m.group ∉ exclusionInter xs ys ↔ m.group ∉ xs ∨ m.group ∉ ys := by
  by_cases hx : m.group ∈ xs <;> by_cases hy : m.group ∈ ys <;> simp [hx,hy]

/-- Later precise cleaners may add a full selector to an inherited group
    exclusion. Their meet is semantic intersection, not equality of atom tags. -/
inductive ExclusionSelector where
  | full (mark : OwnedMark)
  | group (id : Nat)
  deriving DecidableEq, Repr

def selectorExcludes : ExclusionSelector → OwnedMark → Bool
  | .full literal, actual => decide (actual = literal)
  | .group id, actual => decide (actual.group = id)

def optionalExcludes : Option ExclusionSelector → OwnedMark → Bool
  | none, _ => false
  | some selector, actual => selectorExcludes selector actual

def selectorInter : ExclusionSelector → ExclusionSelector → Option ExclusionSelector
  | .full a, .full b => if a = b then some (.full a) else none
  | .group a, .group b => if a = b then some (.group a) else none
  | .full mark, .group group => if mark.group = group then some (.full mark) else none
  | .group group, .full mark => if mark.group = group then some (.full mark) else none

@[simp] theorem excludes_full (literal actual : OwnedMark) :
    selectorExcludes (.full literal) actual = true ↔ actual = literal := by
  simp [selectorExcludes]
@[simp] theorem excludes_group (group : Nat) (actual : OwnedMark) :
    selectorExcludes (.group group) actual = true ↔ actual.group = group := by
  simp [selectorExcludes]

theorem selector_inter_denotation (a b : ExclusionSelector) (actual : OwnedMark) :
    optionalExcludes (selectorInter a b) actual = true ↔
      selectorExcludes a actual = true ∧ selectorExcludes b actual = true := by
  cases a with
  | full left =>
    cases b with
    | full right =>
      by_cases same : left = right
      · subst right; simp [selectorInter,optionalExcludes,selectorExcludes]
      · simp [selectorInter,optionalExcludes,selectorExcludes,same]
        intro eqLeft eqRight
        exact same (eqLeft.symm.trans eqRight)
    | group group =>
      by_cases owner : left.group = group
      · simp [selectorInter,optionalExcludes,selectorExcludes,owner]
        intro eqLeft; exact eqLeft ▸ owner
      · simp [selectorInter,optionalExcludes,selectorExcludes,owner]
        intro eqLeft groupHit
        exact owner (eqLeft ▸ groupHit)
  | group left =>
    cases b with
    | full right =>
      by_cases owner : right.group = left
      · simp [selectorInter,optionalExcludes,selectorExcludes,owner]
        intro eqRight; exact eqRight ▸ owner
      · simp [selectorInter,optionalExcludes,selectorExcludes,owner]
        intro groupHit eqRight
        exact owner (eqRight ▸ groupHit)
    | group right =>
      by_cases same : left = right
      · subst right; simp [selectorInter,optionalExcludes,selectorExcludes]
      · simp [selectorInter,optionalExcludes,selectorExcludes,same]
        intro eqLeft eqRight
        exact same (eqLeft.symm.trans eqRight)

@[simp] theorem mixed_inter_same_group (literal : OwnedMark) :
    selectorInter (.group literal.group) (.full literal) = some (.full literal) := by
  simp [selectorInter]

theorem mixed_inter_different_group (group : Nat) (literal : OwnedMark)
    (different : literal.group ≠ group) :
    selectorInter (.group group) (.full literal) = none := by
  simp [selectorInter,different]

def selectorUnion (xs ys : List ExclusionSelector) : List ExclusionSelector := xs ++ ys

def selectorsExclude (selectors : List ExclusionSelector) (actual : OwnedMark) : Bool :=
  selectors.any (fun selector => selectorExcludes selector actual)

theorem selector_union_denotation (xs ys : List ExclusionSelector) (actual : OwnedMark) :
    selectorsExclude (selectorUnion xs ys) actual =
      (selectorsExclude xs actual || selectorsExclude ys actual) := by
  simp [selectorsExclude,selectorUnion]

theorem full_exclusion_subsumed_by_own_group (literal actual : OwnedMark)
    (fullHit : selectorExcludes (.full literal) actual = true) :
    selectorExcludes (.group literal.group) actual = true := by
  have same := (excludes_full _ _).mp fullHit
  subst actual
  simp

theorem mixed_union_absorption (literal actual : OwnedMark) :
    selectorsExclude [.group literal.group,.full literal] actual = true ↔
      selectorExcludes (.group literal.group) actual = true := by
  simp only [selectorsExclude,List.any_cons,List.any_nil,Bool.or_false,Bool.or_eq_true]
  constructor
  · intro either
    exact either.elim id (full_exclusion_subsumed_by_own_group literal actual)
  · exact Or.inl

theorem inherited_group_is_not_a_single_full_exclusion (literal actual : OwnedMark)
    (sameGroup : actual.group = literal.group) (differentFull : actual.full ≠ literal.full) :
    selectorExcludes (.group literal.group) actual = true ∧
      selectorExcludes (.full literal) actual = false := by
  refine ⟨(excludes_group _ _).mpr sameGroup,?_⟩
  have different : actual ≠ literal := fun same => differentFull (congrArg OwnedMark.full same)
  simp [selectorExcludes,different]

/-- A selected scalar cleaner cannot kill the grouped TAINT possibility. -/
def cleanGrouped (_literal : OwnedMark) (group : Nat) : Selector := .group group

def scalarClean (literal : OwnedMark) (actual : List OwnedMark) : List OwnedMark :=
  actual.filter (fun m => decide (m ≠ literal))

theorem grouped_clean_covers_scalar (literal actual : OwnedMark) (group : Nat)
    (covered : accepts (.group group) actual = true)
    (_survives : actual ∈ scalarClean literal [actual]) :
    accepts (cleanGrouped literal group) actual = true := covered
@[simp] theorem grouped_clean_keeps_selected_group (literal : OwnedMark) :
    cleanGrouped literal literal.group = .group literal.group := rfl

/-- The selected run-1 wildcard is DEMAND even when a caller supplies a
    noncanonical NORMAL header. It never asserts that every group member exists. -/
structure GroupFact where
  group : Nat
  demand : Bool
  deriving DecidableEq, BEq, Repr

instance : ReflBEq GroupFact where
  rfl := by
    intro fact
    cases fact with
    | mk group demand =>
      have hg : (group == group) = true :=
        @ReflBEq.rfl Nat _ Nat.instLawfulBEq.toReflBEq group
      have hd : (demand == demand) = true := by cases demand <;> rfl
      rw [instBEqGroupFact.beq_spec_1,hg,hd]
      rfl

instance : LawfulBEq GroupFact where
  eq_of_beq := by
    intro a b hit
    cases a with
    | mk ag ad =>
      cases b with
      | mk bg bd =>
        rw [instBEqGroupFact.beq_spec_1] at hit
        have both := Bool.and_eq_true_iff.mp hit
        have hg : ag = bg := eq_of_beq both.1
        have hd : ad = bd := eq_of_beq both.2
        cases hg; cases hd; rfl

def abstractActual (mark : OwnedMark) : GroupFact := ⟨mark.group,true⟩

def groupDenotes (fact : GroupFact) (mark : OwnedMark) : Prop := mark.group = fact.group

def cleanGroupFact (_literal : OwnedMark) (fact : GroupFact) : GroupFact :=
  {fact with demand := true}

@[simp] theorem abstract_actual_demand (mark : OwnedMark) :
    (abstractActual mark).demand = true := rfl
@[simp] theorem clean_group_demand (literal : OwnedMark) (fact : GroupFact) :
    (cleanGroupFact literal fact).demand = true := rfl
@[simp] theorem clean_group_keeps_group (literal : OwnedMark) (fact : GroupFact) :
    (cleanGroupFact literal fact).group = fact.group := rfl

/-- All actual exact survivors remain represented; this selected-cleaner
    abstraction may additionally retain the cleaned literal as a D possibility. -/
theorem clean_group_covers_exact (literal mark : OwnedMark) (fact : GroupFact)
    (before : groupDenotes fact mark) :
    groupDenotes (cleanGroupFact literal fact) mark := before

/-- A true spatial hit means the RemoveAll operation covers this whole scalar
    geometry. Partial/disjoint geometry must be split by the AP adapter first. -/
def removeAllActual (wholeHit : Bool) (actual : List OwnedMark) : List OwnedMark :=
  if wholeHit then [] else actual

def removeAllGrouped (wholeHit : Bool) (facts : List GroupFact) : List GroupFact :=
  if wholeHit then [] else facts

def removeAllFlow (wholeHit : Bool) (flow : Option Flow) : Option Flow :=
  if wholeHit then none else flow

@[simp] theorem remove_all_actual_hit (actual : List OwnedMark) :
    removeAllActual true actual = [] := rfl
@[simp] theorem remove_all_grouped_hit (facts : List GroupFact) :
    removeAllGrouped true facts = [] := rfl
@[simp] theorem remove_all_flow_hit (flow : Option Flow) :
    removeAllFlow true flow = none := rfl
@[simp] theorem remove_all_actual_miss (actual : List OwnedMark) :
    removeAllActual false actual = actual := rfl
@[simp] theorem remove_all_grouped_miss (facts : List GroupFact) :
    removeAllGrouped false facts = facts := rfl
@[simp] theorem remove_all_flow_miss (flow : Option Flow) :
    removeAllFlow false flow = flow := rfl

theorem request_after_remove_all_is_empty (group : Nat) (actual : List OwnedMark) :
    materialize group (removeAllActual true actual) = [] := rfl

theorem requested_input_reexecuted_through_remove_all_is_empty
    (group : Nat) (actual : List OwnedMark) :
    removeAllActual true (materialize group actual) = [] := rfl

/-- An explicit homogeneous family: `count` distinct full IDs, one owner.
    These laws concern logical mark-fact slots, not JVM bytes, total events,
    predicate cost, or other AP/provenance coordinates. -/
def homogeneousMarks (group count : Nat) : List OwnedMark :=
  (List.range count).map (fun full => ⟨group,full⟩)

def homogeneousGroups (group count : Nat) : List Nat :=
  if count = 0 then [] else [group]

@[simp] theorem homogeneous_full_ids (group count : Nat) :
    (homogeneousMarks group count).map OwnedMark.full = List.range count := by
  simp [homogeneousMarks,List.map_map,Function.comp_def]
@[simp] theorem homogeneous_count (group count : Nat) :
    (homogeneousMarks group count).length = count := by simp [homogeneousMarks]

theorem homogeneous_owned (group count : Nat) (mark : OwnedMark)
    (present : mark ∈ homogeneousMarks group count) :
    OwnerValid (fun _ => group) mark := by
  obtain ⟨full,_,same⟩ := List.mem_map.mp present
  cases same
  rfl

theorem homogeneous_covered (group count : Nat) (mark : OwnedMark)
    (present : mark ∈ homogeneousMarks group count) :
    accepts (.group group) mark = true := by
  have owner := homogeneous_owned group count mark present
  exact (accepts_group group mark).mpr owner.symm

theorem homogeneous_one_group (group count : Nat) (positive : 0 < count) :
    (homogeneousGroups group count).length = 1 := by
  simp [homogeneousGroups,Nat.ne_of_gt positive]

theorem homogeneous_strict_slot_saving (group count : Nat) (many : 1 < count) :
    (homogeneousGroups group count).length < (homogeneousMarks group count).length := by
  rw [homogeneous_one_group group count (Nat.lt_trans (by decide) many),homogeneous_count]
  exact many

/-- Signed folding uses exact literal identity BEFORE group abstraction. -/
def signedCondition (present : List OwnedMark) (positive negative : OwnedMark) : Bool :=
  decide (positive ∈ present) && !decide (negative ∈ present)

def groupPresent (present : List OwnedMark) (group : Nat) : Bool :=
  present.any (fun m => decide (m.group = group))

def quotientSignedCondition (present : List OwnedMark) (positive negative : OwnedMark) : Bool :=
  groupPresent present positive.group && !groupPresent present negative.group
@[simp] theorem signed_same_literal_false (present : List OwnedMark) (mark : OwnedMark) :
    signedCondition present mark mark = false := by simp [signedCondition]

namespace Controls

def a : OwnedMark := ⟨7,10⟩
def b : OwnedMark := ⟨7,11⟩
def c : OwnedMark := ⟨8,12⟩
def emptyFlow : Flow := ⟨[],false⟩
def oldDemand : Flow := ⟨[9],true⟩

def controls : List (String × Bool) := [
  ("run1 matches another full mark in the same group", selectRun1 a b),
  ("later does not match another full mark", !(selectLater a b)),
  ("later matches the identical pair", selectLater a a),
  ("run1 rejects another group", !(selectRun1 a c)),
  ("owner constructor retains full ID", (own (fun n => n / 10) 21) == (⟨2,21⟩ : OwnedMark)),
  ("materialize selects only actual same-group marks", materialize 7 [a,b,c] == [a,b]),
  ("empty actual supply cannot answer a group", materialize 7 ([] : List OwnedMark) == []),
  ("FLOW cleaner excludes the whole group", (normalComplement emptyFlow a).excludedGroups == [7]),
  ("FLOW cleaner preserves an old demand layer", (normalComplement oldDemand a).demand),
  ("an excluded group is still requested", groupInterest (normalComplement emptyFlow a) a == 7),
  ("concrete obligation retains full literal", concreteObligation emptyFlow a == a),
  ("normal complement keeps the other group", normalSurvivors emptyFlow a [a,b,c] == [c]),
  ("exact demand replay keeps group minus cleaned literal", demandRepair emptyFlow a [a,b,c] == [b]),
  ("exact scalar cleaner keeps all other marks", exactSurvivors emptyFlow a [a,b,c] == [b,c]),
  ("selected cleaner retains the grouped selector", cleanGrouped a 7 == Selector.group 7),
  ("exact cleaner keeps a distinct same-group mark", scalarClean a [a,b] == [b]),
  ("signed exact literals distinguish T from U", signedCondition [a] a b),
  ("quotient before signed folding loses a valid case", !(quotientSignedCondition [a] a b)),
  ("the same full literal is a signed contradiction", !(signedCondition [a] a a)),
  ("sequential exclusions union", exclusionUnion [7] [8] == [7,8]),
  ("alternative disjoint exclusions intersect to empty", exclusionInter [7] [8] == []),
  ("alternative exclusions retain only common groups", exclusionInter [7,8] [8,9] == [8]),
  ("wrong ownership is rejected", decide (¬ OwnerValid (fun _ => 7) c)),
  ("selected cleaner retains grouped DEMAND", cleanGroupFact a (abstractActual a) == abstractActual a),
  ("group cleaning does not produce NORMAL wildcard", cleanGroupFact a (⟨7,false⟩ : GroupFact) == (⟨7,true⟩ : GroupFact)),
  ("RemoveAll kills full marks at a whole hit", removeAllActual true [a,b,c] == []),
  ("RemoveAll leaves disjoint geometry unchanged", removeAllActual false [a,b,c] == [a,b,c]),
  ("RemoveAll kills grouped facts at a whole hit", removeAllGrouped true [abstractActual a] == []),
  ("RemoveAll kills FLOW at a whole hit", removeAllFlow true (some emptyFlow) == none),
  ("request after RemoveAll has no supplied member", materialize 7 (removeAllActual true [a,b]) == []),
  ("requested input reexecution still obeys RemoveAll", removeAllActual true (materialize 7 [a,b]) == []),
  ("homogeneous exact family has 64 slots", (homogeneousMarks 7 64).length == 64),
  ("homogeneous grouped family has one slot", (homogeneousGroups 7 64).length == 1),
  ("empty homogeneous family has no group slot", homogeneousGroups 7 0 == []),
  ("group-full meet retains the covered full selector", decide (selectorInter (.group 7) (.full b) = some (.full b))),
  ("full-group meet retains the covered full selector", decide (selectorInter (.full b) (.group 7) = some (.full b))),
  ("group-full meet across owners is empty", decide (selectorInter (.group 7) (.full c) = none)),
  ("full-full meet keeps exact equality", decide (selectorInter (.full b) (.full b) = some (.full b))),
  ("full-full meet does not merge same-group literals", decide (selectorInter (.full a) (.full b) = none)),
  ("different group meet is empty", decide (selectorInter (.group 7) (.group 8) = none)),
  ("group exclusion absorbs a covered full exclusion", selectorsExclude [.group 7,.full b] a),
  ("inherited group exclusion stays broader than one full", selectorExcludes (.group 7) b && !(selectorExcludes (.full a) b))
]

theorem controls_pass : controls.all Prod.snd = true := by decide
#guard controls.length == 42
#guard controls.all Prod.snd

theorem group_quotient_loses_signed_literal_identity :
    signedCondition [a] a b = true ∧ quotientSignedCondition [a] a b = false := by decide

theorem deleting_selected_group_loses_other_mark :
    b ∈ scalarClean a [a,b] ∧ accepts (.group a.group) b = true := by decide

end Controls
end ApSpec.RuleGroupMarks
