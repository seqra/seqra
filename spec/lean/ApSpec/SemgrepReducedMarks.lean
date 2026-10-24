import Std

/-
Execution normalization AFTER original binding. Full keys retain owner and
primitive property. CM retains typed positions. Declarations and an independent
metadata parameter are unchanged; metadata policy is not selected here.
No-spontaneous-mark/assigned-cover is an explicit local refinement premise.
Opaque source/any-mark clients protect ALL. Original binding domains, report-ID
counter increments and logical action indices remain runtime adapter duties.
Fuel plus an executable fixed-point check returns a fixed point or the EXACT
original rules; an unchecked partial normalization never escapes.
-/
namespace ApSpec.SemgrepReducedMarks
set_option autoImplicit false
set_option maxHeartbeats 4000000

structure OwnerKey where
  name : Nat
  primitive : Bool
  deriving DecidableEq, Repr
structure FullMark where
  owner : OwnerKey
  name : Nat
  deriving DecidableEq, Repr
inductive Base where
  | argument (index : Nat) | receiver | result | staticField (classId fieldId : Nat)
  | local (index : Nat)
  deriving DecidableEq, Repr
/-- Bound CM selectors include the JVM any-field and Go any-accessor
    predicates. The selector is part of the typed query, not a new mark. -/
inductive QueryKind where
  | exact | anyField | anyAccessor
  deriving DecidableEq, Repr
structure Position where
  base : Base
  fields : List Nat := []
  query : QueryKind := .exact
  deriving DecidableEq, Repr
inductive Expr where
  | truth (value : Bool)
  | cm (position : Position) (mark : FullMark)
  | other (identity : Nat)
  | neg (arg : Expr)
  | conj (left right : Expr)
  | disj (left right : Expr)
  deriving DecidableEq, Repr

def eval (marks : Position → FullMark → Bool) (other : Nat → Bool) : Expr → Bool
  | .truth b => b
  | .cm p m => marks p m
  | .other id => other id
  | .neg e => !(eval marks other e)
  | .conj a b => eval marks other a && eval marks other b
  | .disj a b => eval marks other a || eval marks other b

def uses : Expr → List FullMark
  | .truth _ | .other _ => []
  | .cm _ m => [m]
  | .neg e => uses e
  | .conj a b | .disj a b => uses a ++ uses b

def mkNot : Expr → Expr
  | .truth b => .truth (!b)
  | e => .neg e

def mkAnd (a b : Expr) : Expr :=
  match a, b with
  | .truth false, _ | _, .truth false => .truth false
  | .truth true, e | e, .truth true => e
  | x, y => .conj x y

def mkOr (a b : Expr) : Expr :=
  match a, b with
  | .truth true, _ | _, .truth true => .truth true
  | .truth false, e | e, .truth false => e
  | x, y => .disj x y

def fold (assigned : List FullMark) : Expr → Expr
  | .truth b => .truth b
  | .cm p m => if m ∈ assigned then .cm p m else .truth false
  | .other id => .other id
  | .neg e => mkNot (fold assigned e)
  | .conj a b => mkAnd (fold assigned a) (fold assigned b)
  | .disj a b => mkOr (fold assigned a) (fold assigned b)

def AssignedCover (assigned : List FullMark) (marks : Position → FullMark → Bool) : Prop :=
  ∀ p m, marks p m = true → m ∈ assigned

theorem eval_mkNot (marks : Position → FullMark → Bool) (other : Nat → Bool) (e : Expr) :
    eval marks other (mkNot e) = !(eval marks other e) := by
  cases e <;> rfl

theorem eval_mkAnd (marks : Position → FullMark → Bool) (other : Nat → Bool) (a b : Expr) :
    eval marks other (mkAnd a b) = (eval marks other a && eval marks other b) := by
  cases a <;> cases b <;> simp [mkAnd, eval] <;> split <;> simp_all [eval]

theorem eval_mkOr (marks : Position → FullMark → Bool) (other : Nat → Bool) (a b : Expr) :
    eval marks other (mkOr a b) = (eval marks other a || eval marks other b) := by
  cases a <;> cases b <;> simp [mkOr, eval] <;> split <;> simp_all [eval]

theorem fold_truth_equivalent (assigned : List FullMark)
    (marks : Position → FullMark → Bool) (other : Nat → Bool)
    (cover : AssignedCover assigned marks) (e : Expr) :
    eval marks other (fold assigned e) = eval marks other e := by
  induction e with
  | truth b => rfl
  | other id => rfl
  | cm p m =>
      by_cases present : m ∈ assigned
      · simp [fold, present, eval]
      · have absent : marks p m = false := by
          cases h : marks p m with
          | false => rfl
          | true => exact False.elim (present (cover p m h))
        simp [fold, present, eval, absent]
  | neg e ih => simp only [fold, eval_mkNot, ih, eval]
  | conj a b ia ib => simp only [fold, eval_mkAnd, ia, ib, eval]
  | disj a b ia ib => simp only [fold, eval_mkOr, ia, ib, eval]

theorem uses_mkNot (e : Expr) (m : FullMark) : m ∈ uses (mkNot e) → m ∈ uses e := by
  cases e <;> simp [mkNot, uses]

theorem uses_mkAnd (a b : Expr) (m : FullMark) :
    m ∈ uses (mkAnd a b) → m ∈ uses a ∨ m ∈ uses b := by
  cases a <;> cases b <;> simp [mkAnd, uses, or_assoc] <;> split <;> simp_all [uses]

theorem uses_mkOr (a b : Expr) (m : FullMark) :
    m ∈ uses (mkOr a b) → m ∈ uses a ∨ m ∈ uses b := by
  cases a <;> cases b <;> simp [mkOr, uses, or_assoc] <;> split <;> simp_all [uses]

theorem fold_uses_subset (assigned : List FullMark) (e : Expr) (m : FullMark) :
    m ∈ uses (fold assigned e) → m ∈ uses e := by
  induction e with
  | truth b => simp [fold, uses]
  | other id => simp [fold, uses]
  | cm p k => by_cases h : k ∈ assigned <;> simp [fold, h, uses]
  | neg e ih => exact fun h => ih (uses_mkNot _ _ h)
  | conj a b ia ib =>
      intro h
      rcases uses_mkAnd _ _ _ h with ha | hb
      · exact List.mem_append_left _ (ia ha)
      · exact List.mem_append_right _ (ib hb)
  | disj a b ia ib =>
      intro h
      rcases uses_mkOr _ _ _ h with ha | hb
      · exact List.mem_append_left _ (ia ha)
      · exact List.mem_append_right _ (ib hb)

theorem fold_uses_assigned (available : List FullMark) (e : Expr) (m : FullMark) :
    m ∈ uses (fold available e) → m ∈ available := by
  induction e with
  | truth b => simp [fold, uses]
  | other id => simp [fold, uses]
  | cm p k =>
      by_cases h : k ∈ available
      · simpa [fold, h, uses] using fun eq : m = k => eq ▸ h
      · simp [fold, h, uses]
  | neg e ih => exact fun h => ih (uses_mkNot _ _ h)
  | conj a b ia ib =>
      intro h
      rcases uses_mkAnd _ _ _ h with ha | hb
      · exact ia ha
      · exact ib hb
  | disj a b ia ib =>
      intro h
      rcases uses_mkOr _ _ _ h with ha | hb
      · exact ia ha
      · exact ib hb

def weight : Expr → Nat
  | .truth _ | .other _ => 1
  | .cm _ _ => 2
  | .neg e => weight e + 1
  | .conj a b | .disj a b => weight a + weight b + 1

theorem weight_mkNot (e : Expr) : weight (mkNot e) ≤ weight e + 1 := by
  cases e <;> simp [mkNot, weight]

theorem weight_mkAnd (a b : Expr) : weight (mkAnd a b) ≤ weight a + weight b + 1 := by
  cases a <;> cases b <;> simp [mkAnd, weight] <;> split <;> simp_all [weight] <;> omega

theorem weight_mkOr (a b : Expr) : weight (mkOr a b) ≤ weight a + weight b + 1 := by
  cases a <;> cases b <;> simp [mkOr, weight] <;> split <;> simp_all [weight] <;> omega

theorem fold_weight_nonincrease (available : List FullMark) (e : Expr) :
    weight (fold available e) ≤ weight e := by
  induction e with
  | truth b => exact Nat.le_refl _
  | other id => exact Nat.le_refl _
  | cm p m => by_cases h : m ∈ available <;> simp [fold, h, weight]
  | neg e ih =>
      have h := weight_mkNot (fold available e)
      simp only [fold, weight]
      omega
  | conj a b ia ib =>
      have h := weight_mkAnd (fold available a) (fold available b)
      simp only [fold, weight]
      omega
  | disj a b ia ib =>
      have h := weight_mkOr (fold available a) (fold available b)
      simp only [fold, weight]
      omega

/-- Agreement remains position-sensitive; equal mark names alone do not suffice. -/
theorem eval_eq_on_used_marks (left right : Position → FullMark → Bool)
    (other : Nat → Bool) (e : Expr)
    (agree : ∀ p m, m ∈ uses e → left p m = right p m) :
    eval left other e = eval right other e := by
  induction e with
  | truth b => rfl
  | other id => rfl
  | cm p m => exact agree p m (by simp [uses])
  | neg e ih => exact congrArg Bool.not (ih agree)
  | conj a b ia ib =>
      simp only [eval, ia (fun p m h => agree p m (List.mem_append_left _ h)),
        ib (fun p m h => agree p m (List.mem_append_right _ h))]
  | disj a b ia ib =>
      simp only [eval, ia (fun p m h => agree p m (List.mem_append_left _ h)),
        ib (fun p m h => agree p m (List.mem_append_right _ h))]

inductive Action where
  | gen (actionId : Nat) (target : Position) (mark : FullMark) (payload : Nat)
  | other (actionId payload : Nat)
  deriving DecidableEq, Repr

def actionMark : Action → Option FullMark
  | .gen _ _ m _ => some m
  | .other _ _ => none

def keepAction (checked : List FullMark) : Action → Bool
  | .gen _ _ m _ => decide (m ∈ checked)
  | .other _ _ => true

def pruneActions (checked : List FullMark) (actions : List Action) : List Action :=
  actions.filter (keepAction checked)

/-- Exact ordered values, including IDs, target positions and payloads. -/
def observeActions := pruneActions

theorem pruneActions_sublist (checked : List FullMark) (actions : List Action) :
    (pruneActions checked actions).Sublist actions := List.filter_sublist

theorem checked_action_outputs_order_ids (checked : List FullMark) (actions : List Action) :
    observeActions checked (pruneActions checked actions) = observeActions checked actions := by
  simp [observeActions, pruneActions, List.filter_filter]

theorem prune_preserves_smaller_observation (needed observed : List FullMark)
    (included : ∀ m, m ∈ observed → m ∈ needed) (actions : List Action) :
    observeActions observed (pruneActions needed actions) = observeActions observed actions := by
  simp only [observeActions, pruneActions, List.filter_filter]
  apply List.filter_congr
  intro a member
  cases a with
  | other id payload => rfl
  | gen id target m payload =>
      by_cases seen : m ∈ observed
      · simp [keepAction, seen, included m seen]
      · simp [keepAction, seen]

structure Rule where
  itemId : Nat
  guard : Expr
  actions : List Action
  /-- Original binding/position metadata is not rebuilt from surviving GEN. -/
  positionMetadata : List Position
  deriving DecidableEq, Repr

structure Policy where
  externalAssigned : List FullMark := []
  protectedChecks : List FullMark := []
  /-- Unknown source/any-mark/opaque consumers conservatively protect ALL. -/
  protectAll : Bool := false
  deriving DecidableEq, Repr

def assignedBy (r : Rule) : List FullMark :=
  if r.guard = .truth false then [] else r.actions.filterMap actionMark

def assigned (policy : Policy) (rules : List Rule) : List FullMark :=
  policy.externalAssigned ++ rules.flatMap assignedBy

def checked (policy : Policy) (rules : List Rule) : List FullMark :=
  policy.protectedChecks ++ rules.flatMap (fun r => uses r.guard)

def rewriteRule (available needed : List FullMark) (r : Rule) : Rule :=
  { r with guard := fold available r.guard, actions := pruneActions needed r.actions }

def step (policy : Policy) (rules : List Rule) : List Rule :=
  if policy.protectAll then rules else
    rules.map (rewriteRule (assigned policy rules) (checked policy rules))

def executeRule (marks : Position → FullMark → Bool) (other : Nat → Bool) (r : Rule) : List Action :=
  if eval marks other r.guard then r.actions else []

def execute (marks : Position → FullMark → Bool) (other : Nat → Bool) (rules : List Rule) : List Action :=
  rules.flatMap (executeRule marks other)

theorem rewrite_rule_identity_metadata (available needed : List FullMark) (r : Rule) :
    (rewriteRule available needed r).itemId = r.itemId ∧
    (rewriteRule available needed r).positionMetadata = r.positionMetadata := ⟨rfl,rfl⟩

theorem rewrite_rule_checked_outputs (available needed : List FullMark)
    (marks : Position → FullMark → Bool) (other : Nat → Bool)
    (cover : AssignedCover available marks) (r : Rule) :
    observeActions needed (executeRule marks other (rewriteRule available needed r)) =
    observeActions needed (executeRule marks other r) := by
  simp only [executeRule, rewriteRule, fold_truth_equivalent available marks other cover]
  cases h : eval marks other r.guard <;>
    simp [observeActions, pruneActions, List.filter_filter]

/-- A round preserves checked action observations. Whole analyzer traces must
    establish producer cover and observation closure; they are not axioms here. -/
theorem step_checked_outputs (policy : Policy) (rules : List Rule)
    (marks : Position → FullMark → Bool) (other : Nat → Bool)
    (cover : AssignedCover (assigned policy rules) marks) :
    observeActions (checked policy rules) (execute marks other (step policy rules)) =
    observeActions (checked policy rules) (execute marks other rules) := by
  simp only [step]
  split
  · rfl
  · simp only [execute, List.flatMap_map, observeActions, pruneActions, List.filter_flatMap]
    apply congrArg (fun f => rules.flatMap f)
    funext r
    exact rewrite_rule_checked_outputs _ _ marks other cover r

theorem step_row_identities (policy : Policy) (rules : List Rule) :
    (step policy rules).map (fun r => (r.itemId,r.positionMetadata)) =
      rules.map (fun r => (r.itemId,r.positionMetadata)) := by
  simp only [step]
  split
  · rfl
  · simp [List.map_map, rewriteRule]

theorem step_checked_subset (policy : Policy) (rules : List Rule) (m : FullMark) :
    m ∈ checked policy (step policy rules) → m ∈ checked policy rules := by
  by_cases all : policy.protectAll = true
  · simp [step, all]
  · intro h
    simp only [checked, List.mem_append] at h ⊢
    rcases h with retainedCheck | used
    · exact Or.inl retainedCheck
    · right
      rcases List.mem_flatMap.mp used with ⟨new, row, use⟩
      simp only [step, all, Bool.false_eq_true, ↓reduceIte] at row
      rcases List.mem_map.mp row with ⟨old, member, eq⟩
      subst new
      exact List.mem_flatMap.mpr ⟨old, member, fold_uses_subset _ _ _ use⟩

theorem assignedBy_rewrite_subset (available needed : List FullMark) (r : Rule) (m : FullMark) :
    m ∈ assignedBy (rewriteRule available needed r) → m ∈ assignedBy r := by
  by_cases dead : r.guard = .truth false
  · simp [assignedBy, rewriteRule, dead, fold]
  · intro h
    simp only [assignedBy, rewriteRule] at h
    by_cases foldedDead : fold available r.guard = .truth false
    · simp [foldedDead] at h
    · simp only [foldedDead, ↓reduceIte] at h
      simp only [assignedBy, dead, ↓reduceIte]
      rcases List.mem_filterMap.mp h with ⟨a, member, mark⟩
      exact List.mem_filterMap.mpr ⟨a, (List.mem_filter.mp member).1, mark⟩

theorem step_assigned_subset (policy : Policy) (rules : List Rule) (m : FullMark) :
    m ∈ assigned policy (step policy rules) → m ∈ assigned policy rules := by
  by_cases all : policy.protectAll = true
  · simp [step, all]
  · intro h
    simp only [assigned, List.mem_append] at h ⊢
    rcases h with external | used
    · exact Or.inl external
    · right
      rcases List.mem_flatMap.mp used with ⟨new, row, member⟩
      simp only [step, all, Bool.false_eq_true, ↓reduceIte] at row
      rcases List.mem_map.mp row with ⟨old, present, eq⟩
      subst new
      exact List.mem_flatMap.mpr ⟨old,present,assignedBy_rewrite_subset _ _ old m member⟩

theorem step_preserves_smaller_observation (policy : Policy) (rules : List Rule)
    (observed : List FullMark)
    (included : ∀ m, m ∈ observed → m ∈ checked policy rules)
    (marks : Position → FullMark → Bool) (other : Nat → Bool)
    (cover : AssignedCover (assigned policy rules) marks) :
    observeActions observed (execute marks other (step policy rules)) =
      observeActions observed (execute marks other rules) := by
  simp only [step]
  split
  · rfl
  · simp only [execute, List.flatMap_map, observeActions, pruneActions, List.filter_flatMap]
    apply congrArg (fun f => rules.flatMap f)
    funext r
    simp only [executeRule, rewriteRule,
      fold_truth_equivalent (assigned policy rules) marks other cover]
    cases value : eval marks other r.guard
    · rfl
    · exact prune_preserves_smaller_observation _ observed included r.actions

def iterate (policy : Policy) : Nat → List Rule → List Rule
  | 0, rules => rules
  | n + 1, rules => iterate policy n (step policy rules)

/-- Fuel exhaustion safely retains original execution, not a partial result. -/
def normalize (policy : Policy) (fuel : Nat) (rules : List Rule) : List Rule :=
  let candidate := iterate policy fuel rules
  if step policy candidate = candidate then candidate else rules

theorem normalize_fixed_or_original (policy : Policy) (fuel : Nat) (rules : List Rule) :
    step policy (normalize policy fuel rules) = normalize policy fuel rules ∨
      normalize policy fuel rules = rules := by
  simp only [normalize]
  split
  · exact Or.inl (by assumption)
  · exact Or.inr rfl

theorem protectAll_step (policy : Policy) (rules : List Rule) (h : policy.protectAll = true) :
    step policy rules = rules := by simp [step, h]

theorem protectAll_iterate (policy : Policy) (fuel : Nat) (rules : List Rule)
    (h : policy.protectAll = true) : iterate policy fuel rules = rules := by
  induction fuel generalizing rules with
  | zero => rfl
  | succ n ih => simp [iterate, protectAll_step policy rules h, ih]

theorem protectAll_normalize (policy : Policy) (fuel : Nat) (rules : List Rule)
    (h : policy.protectAll = true) : normalize policy fuel rules = rules := by
  simp [normalize, protectAll_iterate policy fuel rules h, protectAll_step policy rules h]

theorem iterate_checked_subset (policy : Policy) (fuel : Nat) (rules : List Rule) (m : FullMark) :
    m ∈ checked policy (iterate policy fuel rules) → m ∈ checked policy rules := by
  induction fuel generalizing rules with
  | zero => exact id
  | succ n ih =>
      intro h
      exact step_checked_subset policy rules m (ih (step policy rules) h)

theorem iterate_assigned_subset (policy : Policy) (fuel : Nat) (rules : List Rule) (m : FullMark) :
    m ∈ assigned policy (iterate policy fuel rules) → m ∈ assigned policy rules := by
  induction fuel generalizing rules with
  | zero => exact id
  | succ n ih =>
      intro h
      exact step_assigned_subset policy rules m (ih (step policy rules) h)

theorem iterate_row_identities (policy : Policy) (fuel : Nat) (rules : List Rule) :
    (iterate policy fuel rules).map (fun r => (r.itemId,r.positionMetadata)) =
      rules.map (fun r => (r.itemId,r.positionMetadata)) := by
  induction fuel generalizing rules with
  | zero => rfl
  | succ n ih =>
      exact (ih (step policy rules)).trans (step_row_identities policy rules)

theorem normalize_row_identities (policy : Policy) (fuel : Nat) (rules : List Rule) :
    (normalize policy fuel rules).map (fun r => (r.itemId,r.positionMetadata)) =
      rules.map (fun r => (r.itemId,r.positionMetadata)) := by
  simp only [normalize]
  split
  · exact iterate_row_identities policy fuel rules
  · rfl

theorem normalize_checked_subset (policy : Policy) (fuel : Nat) (rules : List Rule) (m : FullMark) :
    m ∈ checked policy (normalize policy fuel rules) → m ∈ checked policy rules := by
  simp only [normalize]
  split
  · exact iterate_checked_subset policy fuel rules m
  · exact id

theorem normalize_assigned_subset (policy : Policy) (fuel : Nat) (rules : List Rule) (m : FullMark) :
    m ∈ assigned policy (normalize policy fuel rules) → m ∈ assigned policy rules := by
  simp only [normalize]
  split
  · exact iterate_assigned_subset policy fuel rules m
  · exact id

theorem fixed_cm_has_producer (policy : Policy) (rules : List Rule)
    (closed : policy.protectAll = false) (fixed : step policy rules = rules)
    (r : Rule) (member : r ∈ rules) (m : FullMark) (used : m ∈ uses r.guard) :
    m ∈ assigned policy rules := by
  have row : r ∈ step policy rules := by rw [fixed]; exact member
  simp only [step, closed, Bool.false_eq_true, ↓reduceIte] at row
  rcases List.mem_map.mp row with ⟨old, present, eq⟩
  subst r
  exact fold_uses_assigned _ _ m used

theorem fixed_gen_is_checked (policy : Policy) (rules : List Rule)
    (closed : policy.protectAll = false) (fixed : step policy rules = rules)
    (r : Rule) (member : r ∈ rules) (id : Nat) (target : Position)
    (m : FullMark) (payload : Nat) (output : Action.gen id target m payload ∈ r.actions) :
    m ∈ checked policy rules := by
  have row : r ∈ step policy rules := by rw [fixed]; exact member
  simp only [step, closed, Bool.false_eq_true, ↓reduceIte] at row
  rcases List.mem_map.mp row with ⟨old, present, eq⟩
  subst r
  have retained := (List.mem_filter.mp output).2
  simpa [keepAction] using retained

/-- Logical GEN action execution slots, not allocations or runtime speedup. -/
theorem prune_action_slots_nonincrease (needed : List FullMark) (actions : List Action) :
    (pruneActions needed actions).length ≤ actions.length :=
  (pruneActions_sublist needed actions).length_le

/-- Explicit round-by-round no-spontaneous-mark boundary. It is not a claim
    that arbitrary external inputs satisfy a closed-world catalog. -/
def RoundCover (policy : Policy) (marks : Position → FullMark → Bool) : Nat → List Rule → Prop
  | 0, _ => True
  | n + 1, rules => AssignedCover (assigned policy rules) marks ∧
      RoundCover policy marks n (step policy rules)

theorem iterate_protected_observations (policy : Policy) (fuel : Nat) (rules : List Rule)
    (observed : List FullMark)
    (protectedKeys : ∀ m, m ∈ observed → m ∈ policy.protectedChecks)
    (marks : Position → FullMark → Bool) (other : Nat → Bool)
    (covers : RoundCover policy marks fuel rules) :
    observeActions observed (execute marks other (iterate policy fuel rules)) =
      observeActions observed (execute marks other rules) := by
  induction fuel generalizing rules with
  | zero => rfl
  | succ n ih =>
      have headCover := covers.1
      have tailCover := covers.2
      exact (ih (step policy rules) tailCover).trans
        (step_preserves_smaller_observation policy rules observed
          (fun m hm => List.mem_append_left _ (protectedKeys m hm)) marks other headCover)

theorem normalize_protected_observations (policy : Policy) (fuel : Nat) (rules : List Rule)
    (observed : List FullMark)
    (protectedKeys : ∀ m, m ∈ observed → m ∈ policy.protectedChecks)
    (marks : Position → FullMark → Bool) (other : Nat → Bool)
    (covers : RoundCover policy marks fuel rules) :
    observeActions observed (execute marks other (normalize policy fuel rules)) =
      observeActions observed (execute marks other rules) := by
  simp only [normalize]
  split
  · exact iterate_protected_observations policy fuel rules observed protectedKeys marks other covers
  · rfl

structure Bundle (Metadata : Type) where
  execution : List Rule
  metadata : Metadata
  declarations : List FullMark

def normalizeExecution {Metadata : Type} (policy : Policy) (fuel : Nat)
    (b : Bundle Metadata) : Bundle Metadata :=
  { b with execution := normalize policy fuel b.execution }

theorem metadata_parameter_untouched {Metadata : Type} (policy : Policy) (fuel : Nat)
    (b : Bundle Metadata) : (normalizeExecution policy fuel b).metadata = b.metadata := rfl

theorem no_new_mark_eligibility {Metadata : Type} (policy : Policy) (fuel : Nat)
    (b : Bundle Metadata) : (normalizeExecution policy fuel b).declarations = b.declarations := rfl

namespace Controls

def ownerA : OwnerKey := ⟨1,false⟩
def ownerB : OwnerKey := ⟨2,false⟩
def t : FullMark := ⟨ownerA,10⟩
def u : FullMark := ⟨ownerA,11⟩
def v : FullMark := ⟨ownerA,12⟩
def sameNameOtherOwner : FullMark := ⟨ownerB,10⟩
def arg0 : Position := ⟨.argument 0,[],.exact⟩
def arg1 : Position := ⟨.argument 1,[],.exact⟩
def result : Position := ⟨.result,[],.exact⟩
def source : Rule := ⟨100,.truth true,[.gen 17 result t 5,.gen 23 arg1 u 6],[result,arg1]⟩
def sink : Rule := ⟨200,.cm arg0 t,[],[arg0]⟩

theorem missing_cm_false : fold [] (.cm arg0 t) = .truth false := by decide
theorem negative_missing_cm_true : fold [] (.neg (.cm arg0 t)) = .truth true := by decide

def anyFieldArg0 : Position := { arg0 with query := .anyField }
def anyAccessorArg0 : Position := { arg0 with query := .anyAccessor }

theorem missing_any_field_cm_false :
    fold [] (.cm anyFieldArg0 t) = .truth false := by decide

theorem negative_missing_any_field_cm_true :
    fold [] (.neg (.cm anyFieldArg0 t)) = .truth true := by decide

theorem missing_any_accessor_cm_false :
    fold [] (.cm anyAccessorArg0 t) = .truth false := by decide

theorem negative_missing_any_accessor_cm_true :
    fold [] (.neg (.cm anyAccessorArg0 t)) = .truth true := by decide
theorem false_and_collapses :
    fold [u] (.conj (.cm arg0 t) (.cm arg1 u)) = .truth false := by decide
theorem false_or_retains_typed_live_check :
    fold [u] (.disj (.cm arg0 t) (.cm arg1 u)) = .cm arg1 u := by decide

theorem equal_name_other_owner_not_a_producer :
    fold [sameNameOtherOwner] (.cm arg0 t) = .truth false := by decide

theorem primitive_property_is_part_of_owner_key :
    fold [⟨⟨1,true⟩,10⟩] (.cm arg0 t) = .truth false := by decide

theorem typed_position_survives_fold : fold [t] (.cm arg1 t) = .cm arg1 t := by decide

theorem prune_keeps_checked_order_ids_and_opaque :
    pruneActions [t] [.gen 7 arg0 t 1,.gen 9 arg1 u 2,.other 10 3,.gen 12 result t 4] =
      [.gen 7 arg0 t 1,.other 10 3,.gen 12 result t 4] := by decide

theorem unused_target_metadata_is_not_erased :
    (rewriteRule [t,u] [t] source).actions = [.gen 17 result t 5] ∧
    (rewriteRule [t,u] [t] source).positionMetadata = [result,arg1] ∧
    (rewriteRule [t,u] [t] source).itemId = 100 := by decide

def cascade : List Rule :=
  [⟨1,.cm arg0 t,[.gen 1 result u 0],[arg0,result]⟩,
   ⟨2,.cm arg0 u,[.gen 2 result v 0],[arg0,result]⟩,
   ⟨3,.cm arg0 v,[],[arg0]⟩]

theorem dead_guarded_source_not_a_producer :
    assignedBy ⟨1,.truth false,[.gen 1 result t 0],[result]⟩ = [] := by decide

theorem cascade_reaches_verified_fixed_point :
    (normalize {} 4 cascade).map (fun r => (r.guard,r.actions)) =
      [(.truth false,[]),(.truth false,[]),(.truth false,[])] ∧
    step {} (normalize {} 4 cascade) = normalize {} 4 cascade := by decide

theorem insufficient_fuel_returns_exact_old_rules : normalize {} 1 cascade = cascade := by decide

def guardedSelf : List Rule := [⟨1,.cm arg0 t,[.gen 1 result t 0],[arg0,result]⟩]

theorem unsupported_bootstrap_cycle_is_conservatively_retained :
    normalize {} 4 guardedSelf = guardedSelf := by decide

theorem opaque_source_or_any_mark_client_protects_all :
    normalize {protectAll := true} 20 cascade = cascade := by decide

theorem external_assigned_mark_is_not_folded_false :
    (step {externalAssigned := [t]} [sink]).map Rule.guard = [.cm arg0 t] := by decide

theorem opaque_nonmark_condition_is_preserved :
    fold [] (.other 21) = .other 21 := by decide

theorem no_CM_no_protected_client_drops_GEN_only :
    pruneActions [] [.gen 7 result t 1,.other 8 9] = [.other 8 9] := by decide

theorem source_action_and_check_remain_with_exact_binding_ids :
    (normalize {} 5 [source,sink]).map (fun r => (r.itemId,r.guard,r.actions,r.positionMetadata)) =
    [(100,.truth true,[.gen 17 result t 5],[result,arg1]),
     (200,.cm arg0 t,[],[arg0])] := by decide

end Controls

end ApSpec.SemgrepReducedMarks
