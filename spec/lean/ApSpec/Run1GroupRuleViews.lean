/-
Provider-owned GROUP NNF. Literal identity includes bound position, check kind
and registered owner. FULL signed folding precedes residual-negative projection.
This finite pure compiler owns no FULL-alias proof directory. JVM metadata/AP
adapters and source transfer cases have separate explicit obligations.
-/
import ApSpec.RuleGroupConditions
namespace ApSpec.Run1GroupRuleViews
set_option autoImplicit false

inductive CheckKind where
  | exact | anyField
  deriving DecidableEq, Repr
structure Position where
  base : Nat
  path : List Nat
  kind : CheckKind
  deriving DecidableEq, Repr
structure FullLiteral where
  position : Position
  mark : RuleGroupMarks.OwnedMark
  deriving DecidableEq, Repr
structure GroupLiteral where
  position : Position
  owner : Nat
  deriving DecidableEq, Repr

def projectLiteral (literal : FullLiteral) : GroupLiteral :=
  ⟨literal.position, literal.mark.group⟩
@[simp] theorem projection_keeps_position (literal : FullLiteral) :
    (projectLiteral literal).position = literal.position := rfl
@[simp] theorem projection_uses_registered_owner (literal : FullLiteral) :
    (projectLiteral literal).owner = literal.mark.group := rfl

theorem different_positions_stay_different (a b : FullLiteral)
    (different : a.position ≠ b.position) : projectLiteral a ≠ projectLiteral b := by
  intro same
  exact different (congrArg GroupLiteral.position same)
theorem different_owners_stay_different (a b : FullLiteral)
    (different : a.mark.group ≠ b.mark.group) : projectLiteral a ≠ projectLiteral b := by
  intro same
  exact different (congrArg GroupLiteral.owner same)

inductive Positive (Atom : Type) where
  | falsity | truth
  | atom (literal : Atom)
  | conj (left right : Positive Atom)
  | disj (left right : Positive Atom)
  deriving DecidableEq, Repr
namespace Positive
variable {Atom : Type}
def eval (actual : Atom → Bool) : Positive Atom → Bool
  | .falsity => false
  | .truth => true
  | .atom literal => actual literal
  | .conj left right => eval actual left && eval actual right
  | .disj left right => eval actual left || eval actual right
def map {Other : Type} (project : Atom → Other) : Positive Atom → Positive Other
  | .falsity => .falsity
  | .truth => .truth
  | .atom literal => .atom (project literal)
  | .conj left right => .conj (map project left) (map project right)
  | .disj left right => .disj (map project left) (map project right)
theorem eval_map {Other : Type} (project : Atom → Other) (actual : Other → Bool)
    (condition : Positive Atom) :
    eval actual (map project condition) = eval (fun atom => actual (project atom)) condition := by
  induction condition with
  | falsity => rfl
  | truth => rfl
  | atom _ => rfl
  | conj _ _ left right => simp only [map, eval, left, right]
  | disj _ _ left right => simp only [map, eval, left, right]
variable [DecidableEq Atom]
/-- Same-connective containment, not an implication solver. -/
def containsOr (condition part : Positive Atom) : Bool :=
  if condition = part then true else match condition with
    | .disj left right => containsOr left part || containsOr right part
    | _ => false
def containsAnd (condition part : Positive Atom) : Bool :=
  if condition = part then true else match condition with
    | .conj left right => containsAnd left part || containsAnd right part
    | _ => false

theorem containsOr_sound (condition part : Positive Atom) (actual : Atom → Bool) :
    containsOr condition part = true → eval actual part = true → eval actual condition = true := by
  induction condition with
  | falsity =>
      intro contained holds
      unfold containsOr at contained
      split at contained
      next same => rw [← same] at holds; cases holds
      next different => cases contained
  | truth => intro _ _; rfl
  | atom literal => intro contained holds; simp only [containsOr] at contained; split at contained <;> simp_all
  | conj left right => intro contained holds; simp only [containsOr] at contained; split at contained <;> simp_all
  | disj left right ihLeft ihRight =>
      intro contained holds
      unfold containsOr at contained
      split at contained
      next same => simpa only [same] using holds
      next different =>
        rcases Bool.or_eq_true_iff.mp contained with hit | hit
        · exact Bool.or_eq_true_iff.mpr (Or.inl (ihLeft hit holds))
        · exact Bool.or_eq_true_iff.mpr (Or.inr (ihRight hit holds))
theorem containsAnd_sound (condition part : Positive Atom) (actual : Atom → Bool) :
    containsAnd condition part = true → eval actual condition = true → eval actual part = true := by
  induction condition with
  | falsity => intro _ impossible; cases impossible
  | truth => intro contained holds; simp only [containsAnd] at contained; split at contained <;> simp_all
  | atom literal => intro contained holds; simp only [containsAnd] at contained; split at contained <;> simp_all
  | disj left right => intro contained holds; simp only [containsAnd] at contained; split at contained <;> simp_all
  | conj left right ihLeft ihRight =>
      intro contained holds
      unfold containsAnd at contained
      split at contained
      next same => simpa only [same] using holds
      next different =>
        rcases Bool.or_eq_true_iff.mp contained with hit | hit
        · exact ihLeft hit (Bool.and_eq_true_iff.mp holds).1
        · exact ihRight hit (Bool.and_eq_true_iff.mp holds).2

def and (a b : Positive Atom) : Positive Atom :=
  if a = .falsity ∨ b = .falsity then .falsity
  else if a = .truth then b
  else if b = .truth then a
  else if containsOr a b then b
  else if containsOr b a then a
  else .conj a b
def or (a b : Positive Atom) : Positive Atom :=
  if a = .truth ∨ b = .truth then .truth
  else if a = .falsity then b
  else if b = .falsity then a
  else if containsAnd a b then b
  else if containsAnd b a then a
  else .disj a b

theorem eval_and (a b : Positive Atom) (actual : Atom → Bool) :
    eval actual (and a b) = (eval actual a && eval actual b) := by
  unfold and
  split
  next zero => rcases zero with rfl | rfl <;> simp [eval]
  split
  next one => subst a; simp [eval]
  split
  next one => subst b; simp [eval]
  split
  next contained =>
    have sound := containsOr_sound a b actual contained
    cases ha : eval actual a <;> cases hb : eval actual b <;> simp_all
  split
  next contained =>
    have sound := containsOr_sound b a actual contained
    cases ha : eval actual a <;> cases hb : eval actual b <;> simp_all
  rfl
theorem eval_or (a b : Positive Atom) (actual : Atom → Bool) :
    eval actual (or a b) = (eval actual a || eval actual b) := by
  unfold or
  split
  next one => rcases one with rfl | rfl <;> simp [eval]
  split
  next zero => subst a; simp [eval]
  split
  next zero => subst b; simp [eval]
  split
  next contained =>
    have sound := containsAnd_sound a b actual contained
    cases ha : eval actual a <;> cases hb : eval actual b <;> simp_all
  split
  next contained =>
    have sound := containsAnd_sound b a actual contained
    cases ha : eval actual a <;> cases hb : eval actual b <;> simp_all
  rfl

/-- Flatten equal connectives without distributing AND over OR. -/
def andTerms : Positive Atom → List (Positive Atom)
  | .conj left right => andTerms left ++ andTerms right
  | other => [other]
def orTerms : Positive Atom → List (Positive Atom)
  | .disj left right => orTerms left ++ orTerms right
  | other => [other]
def all (actual : Atom → Bool) : List (Positive Atom) → Bool
  | [] => true
  | x :: xs => eval actual x && all actual xs
def any (actual : Atom → Bool) : List (Positive Atom) → Bool
  | [] => false
  | x :: xs => eval actual x || any actual xs
omit [DecidableEq Atom] in
@[simp] theorem all_append (actual : Atom → Bool) (xs ys : List (Positive Atom)) :
    all actual (xs ++ ys) = (all actual xs && all actual ys) := by
  induction xs with
  | nil => simp [all]
  | cons x xs ih => simp only [List.cons_append, all, ih, Bool.and_assoc]
omit [DecidableEq Atom] in
@[simp] theorem any_append (actual : Atom → Bool) (xs ys : List (Positive Atom)) :
    any actual (xs ++ ys) = (any actual xs || any actual ys) := by
  induction xs with
  | nil => simp [any]
  | cons x xs ih => simp only [List.cons_append, any, ih, Bool.or_assoc]
omit [DecidableEq Atom] in
theorem andTerms_eval (condition : Positive Atom) (actual : Atom → Bool) :
    all actual (andTerms condition) = eval actual condition := by
  induction condition <;> simp_all [andTerms, all, eval]
omit [DecidableEq Atom] in
theorem orTerms_eval (condition : Positive Atom) (actual : Atom → Bool) :
    any actual (orTerms condition) = eval actual condition := by
  induction condition <;> simp_all [orTerms, any, eval]
def foldAnd : List (Positive Atom) → Positive Atom
  | [] => .truth
  | x :: xs => and x (foldAnd xs)
def foldOr : List (Positive Atom) → Positive Atom
  | [] => .falsity
  | x :: xs => or x (foldOr xs)
theorem foldAnd_eval (xs : List (Positive Atom)) (actual : Atom → Bool) :
    eval actual (foldAnd xs) = all actual xs := by
  induction xs with
  | nil => rfl
  | cons x xs ih => simp only [foldAnd, eval_and, all, ih]
theorem foldOr_eval (xs : List (Positive Atom)) (actual : Atom → Bool) :
    eval actual (foldOr xs) = any actual xs := by
  induction xs with
  | nil => rfl
  | cons x xs ih => simp only [foldOr, eval_or, any, ih]
def normalize : Positive Atom → Positive Atom
  | .conj left right => foldAnd (andTerms (normalize left) ++ andTerms (normalize right))
  | .disj left right => foldOr (orTerms (normalize left) ++ orTerms (normalize right))
  | other => other
theorem normalize_eval (condition : Positive Atom) (actual : Atom → Bool) :
    eval actual (normalize condition) = eval actual condition := by
  induction condition with
  | falsity => rfl
  | truth => rfl
  | atom _ => rfl
  | conj left right ihLeft ihRight =>
      simp only [normalize, foldAnd_eval, all_append, andTerms_eval, ihLeft, ihRight, eval]
  | disj left right ihLeft ihRight =>
      simp only [normalize, foldOr_eval, any_append, orTerms_eval, ihLeft, ihRight, eval]
def isAlwaysTrue (condition : Positive Atom) : Bool := eval (fun _ => false) condition
def isAlwaysFalse (condition : Positive Atom) : Bool := !(eval (fun _ => true) condition)
theorem normalization_keeps_true_class (condition : Positive Atom) :
    isAlwaysTrue (normalize condition) = isAlwaysTrue condition := normalize_eval _ _
theorem normalization_keeps_false_class (condition : Positive Atom) :
    isAlwaysFalse (normalize condition) = isAlwaysFalse condition := by
  simp only [isAlwaysFalse, normalize_eval]
end Positive

inductive Signed where
  | falsity | truth
  | positive (literal : FullLiteral)
  | negative (literal : FullLiteral)
  | conj (left right : Signed)
  | disj (left right : Signed)
  deriving DecidableEq, Repr
namespace Signed
def eval (actual : FullLiteral → Bool) : Signed → Bool
  | .falsity => false
  | .truth => true
  | .positive literal => actual literal
  | .negative literal => !(actual literal)
  | .conj left right => eval actual left && eval actual right
  | .disj left right => eval actual left || eval actual right
def and : Signed → Signed → Signed
  | .falsity, _ | _, .falsity => .falsity
  | .truth, b => b
  | a, .truth => a
  | .positive a, .negative b => if a = b then .falsity else .conj (.positive a) (.negative b)
  | .negative a, .positive b => if a = b then .falsity else .conj (.negative a) (.positive b)
  | a, b => .conj a b
def or : Signed → Signed → Signed
  | .truth, _ | _, .truth => .truth
  | .falsity, b => b
  | a, .falsity => a
  | .positive a, .negative b => if a = b then .truth else .disj (.positive a) (.negative b)
  | .negative a, .positive b => if a = b then .truth else .disj (.negative a) (.positive b)
  | a, b => .disj a b
theorem eval_and (a b : Signed) (actual : FullLiteral → Bool) :
    eval actual (and a b) = (eval actual a && eval actual b) := by
  cases a <;> cases b <;> simp [and, eval]
  all_goals split <;> simp_all [eval]
theorem eval_or (a b : Signed) (actual : FullLiteral → Bool) :
    eval actual (or a b) = (eval actual a || eval actual b) := by
  cases a <;> cases b <;> simp [or, eval]
  all_goals split <;> simp_all [eval]
def fold : Signed → Signed
  | .conj a b => and (fold a) (fold b)
  | .disj a b => or (fold a) (fold b)
  | other => other
theorem fold_eval (condition : Signed) (actual : FullLiteral → Bool) :
    eval actual (fold condition) = eval actual condition := by
  induction condition <;> simp_all only [fold, eval_and, eval_or, eval]
/-- Only eligible source/sink residual negatives use this projection. -/
def possible : Signed → Positive GroupLiteral
  | .falsity => .falsity
  | .truth => .truth
  | .positive literal => .atom (projectLiteral literal)
  | .negative _ => .truth
  | .conj a b => .conj (possible a) (possible b)
  | .disj a b => .disj (possible a) (possible b)
def GroupsCover (actual : FullLiteral → Bool) (groups : GroupLiteral → Bool) : Prop :=
  ∀ literal, actual literal = true → groups (projectLiteral literal) = true
theorem exact_implies_possible (condition : Signed) (actual : FullLiteral → Bool)
    (groups : GroupLiteral → Bool) (cover : GroupsCover actual groups) :
    eval actual condition = true → Positive.eval groups (possible condition) = true := by
  induction condition with
  | falsity => intro impossible; cases impossible
  | truth => intro _; rfl
  | positive literal => exact cover literal
  | negative _ => intro _; rfl
  | conj a b ihA ihB =>
      intro holds
      have both := Bool.and_eq_true_iff.mp holds
      exact Bool.and_eq_true_iff.mpr ⟨ihA both.1, ihB both.2⟩
  | disj a b ihA ihB =>
      intro holds
      rcases Bool.or_eq_true_iff.mp holds with left | right
      · exact Bool.or_eq_true_iff.mpr (Or.inl (ihA left))
      · exact Bool.or_eq_true_iff.mpr (Or.inr (ihB right))
end Signed

def compileGroup (condition : Signed) : Positive GroupLiteral :=
  Positive.normalize (Signed.possible (Signed.fold condition))
theorem group_normalization_truth (condition : Signed) (groups : GroupLiteral → Bool) :
    Positive.eval groups (compileGroup condition) =
      Positive.eval groups (Signed.possible (Signed.fold condition)) := Positive.normalize_eval _ _
theorem full_witness_covered (condition : Signed) (actual : FullLiteral → Bool)
    (groups : GroupLiteral → Bool) (cover : Signed.GroupsCover actual groups)
    (holds : Signed.eval actual condition = true) :
    Positive.eval groups (compileGroup condition) = true := by
  rw [group_normalization_truth]
  apply Signed.exact_implies_possible _ _ _ cover
  rwa [Signed.fold_eval]
/-- Binding is explicit and precedes the second exact fold. -/
def compileBound (bind : Signed → Signed) (condition : Signed) : Positive GroupLiteral :=
  compileGroup (bind (Signed.fold condition))

/-- The metadata/position adapter discharges this local semantics contract.
    It is not an assumed whole-analysis coverage proposition. -/
def BindingPreserves (bind : Signed → Signed)
    (before after : FullLiteral → Bool) : Prop :=
  ∀ condition, Signed.eval after (bind condition) = Signed.eval before condition

theorem bound_full_witness_covered (bind : Signed → Signed) (condition : Signed)
    (before after : FullLiteral → Bool) (groups : GroupLiteral → Bool)
    (binding : BindingPreserves bind before after) (cover : Signed.GroupsCover after groups)
    (holds : Signed.eval before condition = true) :
    Positive.eval groups (compileBound bind condition) = true := by
  apply full_witness_covered _ after groups cover
  rw [binding, Signed.fold_eval]
  exact holds

inductive View where
  | run1Group | full
  deriving DecidableEq, Repr
/-- The public run number is predecessor + 1; zero denotes no public run. -/
def runNumber (predecessor : Nat) : Nat := predecessor + 1
theorem run_number_positive (predecessor : Nat) : 0 < runNumber predecessor := Nat.zero_lt_succ _
/-- Predecessor 0 selects run-1, not a run-0. -/
def viewForPredecessor (predecessor : Nat) : View := if predecessor = 0 then .run1Group else .full
structure Occurrence where
  generation : Nat
  method : Nat
  context : Nat
  statement : Nat
  role : Nat
  candidateOrdinal : Nat
  deriving DecidableEq, Repr
structure Action where
  ordinal : Nat
  fullTarget : FullLiteral
  deriving DecidableEq, Repr
structure Candidate where
  occurrence : Occurrence
  originalCondition : Signed
  actions : List Action
  deriving DecidableEq, Repr
structure Selected where
  original : Candidate
  view : View
  deriving DecidableEq, Repr
def select (view : View) (candidate : Candidate) : Selected := ⟨candidate, view⟩
def Selected.condition (selected : Selected) : Sum (Positive GroupLiteral) Signed :=
  match selected.view with
  | .run1Group => .inl (compileGroup selected.original.originalCondition)
  | .full => .inr selected.original.originalCondition
/-- Any existing role/metadata decision is made on the original candidate.
    Selection cannot convert a possible GROUP condition into cleaner permission. -/
def selectAdmitted (legacyAdmission : Candidate → Bool) (view : View)
    (candidate : Candidate) : Option Selected :=
  if legacyAdmission candidate then some (select view candidate) else none

theorem original_role_admission_preserved (legacyAdmission : Candidate → Bool)
    (view : View) (candidate : Candidate) :
    (selectAdmitted legacyAdmission view candidate).isSome = legacyAdmission candidate := by
  cases result : legacyAdmission candidate <;> simp [selectAdmitted, result]

theorem original_role_rejection_preserved (legacyAdmission : Candidate → Bool)
    (view : View) (candidate : Candidate) (rejected : legacyAdmission candidate = false) :
    selectAdmitted legacyAdmission view candidate = none := by
  simp only [selectAdmitted, rejected, Bool.false_eq_true, ↓reduceIte]

theorem selected_occurrence_stable (view : View) (candidate : Candidate) :
    (select view candidate).original.occurrence = candidate.occurrence := rfl
theorem all_original_actions_preserved (view : View) (candidate : Candidate) :
    (select view candidate).original.actions = candidate.actions := rfl
theorem full_decode_exact (candidate : Candidate) :
    (select .full candidate).condition = .inr candidate.originalCondition := rfl
structure CacheKey where
  occurrence : Occurrence
  view : View
  deriving DecidableEq, Repr
theorem cache_views_distinct (occurrence : Occurrence) :
    CacheKey.mk occurrence .run1Group ≠ CacheKey.mk occurrence .full := by
  intro same
  cases same
structure SinkSeed where
  occurrence : Occurrence
  deriving DecidableEq, Repr
def resolveFull : List Candidate → SinkSeed → Option Selected
  | [], _ => none
  | candidate :: rest, seed =>
      if candidate.occurrence = seed.occurrence then some (select .full candidate)
      else resolveFull rest seed

def UniqueOccurrences (candidates : List Candidate) : Prop :=
  ∀ a, a ∈ candidates → ∀ b, b ∈ candidates → a.occurrence = b.occurrence → a = b

theorem resolveFull_present (candidates : List Candidate) (candidate : Candidate)
    (present : candidate ∈ candidates) (unique : UniqueOccurrences candidates) :
    resolveFull candidates ⟨candidate.occurrence⟩ = some (select .full candidate) := by
  induction candidates with
  | nil => cases present
  | cons head rest ih =>
      unfold resolveFull
      split
      next same =>
        have equal := unique head (List.mem_cons_self) candidate present same
        exact congrArg (fun value => some (select .full value)) equal
      next different =>
        apply ih
        · exact (List.mem_cons.mp present).resolve_left (fun same => different (congrArg Candidate.occurrence same).symm)
        · intro a ha b hb same
          exact unique a (List.mem_cons_of_mem head ha) b (List.mem_cons_of_mem head hb) same

theorem resolveFull_keeps_original (candidates : List Candidate) (candidate : Candidate)
    (present : candidate ∈ candidates) (unique : UniqueOccurrences candidates) :
    (resolveFull candidates ⟨candidate.occurrence⟩).map Selected.condition =
      some (.inr candidate.originalCondition) := by
  rw [resolveFull_present candidates candidate present unique]
  rfl

/-- Filtering never renumbers a surviving original provider candidate. -/
def keepCandidates (keep : Candidate → Bool) (candidates : List Candidate) : List Candidate :=
  candidates.filter keep

theorem filtered_seed_still_resolves (keep : Candidate → Bool) (candidates : List Candidate)
    (candidate : Candidate) (present : candidate ∈ candidates) (accepted : keep candidate = true)
    (unique : UniqueOccurrences candidates) :
    resolveFull (keepCandidates keep candidates) ⟨candidate.occurrence⟩ = some (select .full candidate) := by
  apply resolveFull_present
  · exact List.mem_filter.mpr ⟨present, accepted⟩
  · intro a ha b hb same
    exact unique a (List.mem_filter.mp ha).1 b (List.mem_filter.mp hb).1 same
theorem backward_uses_full (candidate : Candidate) : viewForPredecessor 1 = .full ∧
    (select (viewForPredecessor 1) candidate).condition = .inr candidate.originalCondition := ⟨rfl, rfl⟩
theorem forward_three_uses_full : viewForPredecessor 2 = .full := rfl

theorem projection_keeps_true_class (condition : Positive FullLiteral) :
    Positive.isAlwaysTrue (Positive.normalize (Positive.map projectLiteral condition)) =
      Positive.isAlwaysTrue condition := by
  simp only [Positive.isAlwaysTrue, Positive.normalize_eval, Positive.eval_map]
theorem projection_keeps_false_class (condition : Positive FullLiteral) :
    Positive.isAlwaysFalse (Positive.normalize (Positive.map projectLiteral condition)) =
      Positive.isAlwaysFalse condition := by
  simp only [Positive.isAlwaysFalse, Positive.normalize_eval, Positive.eval_map]

/-- A scoped work comparison counts literal nodes, not project time or bytes. -/
def literalCount {Atom : Type} : Positive Atom → Nat
  | .falsity | .truth => 0
  | .atom _ => 1
  | .conj a b | .disj a b => literalCount a + literalCount b

def aliasOr (position : Position) (owner : Nat) : Nat → Positive FullLiteral
  | 0 => .atom ⟨position, ⟨owner, 0⟩⟩
  | n + 1 => .disj (.atom ⟨position, ⟨owner, n + 1⟩⟩) (aliasOr position owner n)

theorem alias_literal_count (position : Position) (owner count : Nat) :
    literalCount (aliasOr position owner count) = count + 1 := by
  induction count with
  | zero => rfl
  | succ n ih => simp only [aliasOr, literalCount, ih]; omega

theorem alias_projection_one_literal (position : Position) (owner count : Nat) :
    Positive.normalize (Positive.map projectLiteral (aliasOr position owner count)) =
      .atom ⟨position, owner⟩ := by
  induction count with
  | zero => rfl
  | succ n ih =>
      simp only [aliasOr, Positive.map, Positive.normalize, ih, projectLiteral,
        Positive.orTerms]
      simp [Positive.foldOr, Positive.or, Positive.containsAnd]

theorem aliases_strictly_reduce_literal_nodes (position : Position) (owner count : Nat)
    (many : 0 < count) :
    literalCount (Positive.normalize (Positive.map projectLiteral (aliasOr position owner count))) <
      literalCount (aliasOr position owner count) := by
  rw [alias_projection_one_literal, alias_literal_count]
  change 1 < count + 1
  omega

namespace Controls
def x : Position := ⟨0, [4], .exact⟩
def y : Position := ⟨1, [4], .exact⟩
def anyX : Position := ⟨0, [4], .anyField⟩
def t : FullLiteral := ⟨x, ⟨7, 10⟩⟩
def u : FullLiteral := ⟨x, ⟨7, 11⟩⟩
def otherOwner : FullLiteral := ⟨x, ⟨8, 12⟩⟩
def atY : FullLiteral := ⟨y, ⟨7, 10⟩⟩
def rejectedRoleCandidate : Candidate := ⟨⟨1, 2, 3, 4, 5, 0⟩, .negative t, []⟩
def metadataOnlyAdmission (candidate : Candidate) : Bool :=
  decide (candidate.originalCondition = Signed.truth)
def controls : List (String × Bool) := [
  ("GROUP possibility never overrides original role rejection",
    decide (compileGroup rejectedRoleCandidate.originalCondition = .truth) &&
    decide (selectAdmitted metadataOnlyAdmission .run1Group rejectedRoleCandidate = none)),
  ("OR aliases collapse", decide (compileGroup (.disj (.positive t) (.positive u)) = .atom (projectLiteral t))),
  ("AND aliases collapse", decide (compileGroup (.conj (.positive t) (.positive u)) = .atom (projectLiteral t))),
  ("positions stay distinct", decide (compileGroup (.conj (.positive t) (.positive atY)) = .conj (.atom (projectLiteral t)) (.atom (projectLiteral atY)))),
  ("imported owner remains legal", decide (compileGroup (.conj (.positive t) (.positive otherOwner)) = .conj (.atom (projectLiteral t)) (.atom (projectLiteral otherOwner)))),
  ("full contradiction folds first", decide (compileGroup (.conj (.positive t) (.negative t)) = .falsity)),
  ("same-group distinct signed pair", decide (compileGroup (.conj (.positive t) (.negative u)) = .atom (projectLiteral t))),
  ("check kinds remain distinct", decide (projectLiteral t ≠ projectLiteral ⟨anyX, t.mark⟩)),
  ("positive absorption", decide (Positive.normalize (.disj (.atom (projectLiteral t)) (.conj (.atom (projectLiteral t)) (.atom (projectLiteral atY)))) = .atom (projectLiteral t))),
  ("view schedule", decide (viewForPredecessor 0 = .run1Group ∧ viewForPredecessor 1 = .full ∧ viewForPredecessor 2 = .full))
]
theorem controls_pass : controls.all Prod.snd = true := by decide
end Controls
end ApSpec.Run1GroupRuleViews
