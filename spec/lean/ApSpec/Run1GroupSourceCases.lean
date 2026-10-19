/-
  Finite source-case compiler. It derives unary anchors and a MULTI admission
  circuit from the original positive FULL NNF before GROUP normalization.
  It never constructs an AND-product of full proof alternatives. The pair
  circuit has a finite literal-pair index, not a full proof/cube inventory.
  Proof trees below are constructive ghost witnesses, not runtime state.
-/
import ApSpec.Run1GroupRuleViews
namespace ApSpec.Run1GroupSourceCases
set_option autoImplicit false
open Run1GroupRuleViews
open Positive

abbrev Formula := Positive FullLiteral

def atoms : Formula → List FullLiteral
  | .falsity | .truth => []
  | .atom literal => [literal]
  | .conj a b | .disj a b => (atoms a ++ atoms b).eraseDups

inductive Witness (actual : FullLiteral → Bool) : Formula → Type where
  | truth : Witness actual .truth
  | atom (literal : FullLiteral) (holds : actual literal = true) : Witness actual (.atom literal)
  | conj {a b : Formula} (left : Witness actual a) (right : Witness actual b) : Witness actual (.conj a b)
  | left {a b : Formula} (left : Witness actual a) : Witness actual (.disj a b)
  | right {a b : Formula} (right : Witness actual b) : Witness actual (.disj a b)

def support {actual : FullLiteral → Bool} {formula : Formula} : Witness actual formula → List FullLiteral
  | .truth => []
  | .atom literal _ => [literal]
  | .conj left right => (support left ++ support right).eraseDups
  | .left left => support left
  | .right right => support right

theorem witness_holds {actual : FullLiteral → Bool} {formula : Formula} (w : Witness actual formula) :
    eval actual formula = true := by
  induction w with
  | truth => rfl
  | atom _ holds => exact holds
  | conj _ _ left right => exact Bool.and_eq_true_iff.mpr ⟨left, right⟩
  | left _ holds => exact Bool.or_eq_true_iff.mpr (Or.inl holds)
  | right _ holds => exact Bool.or_eq_true_iff.mpr (Or.inr holds)

theorem supported_literal_holds {actual : FullLiteral → Bool} {formula : Formula}
    (w : Witness actual formula) (literal : FullLiteral) (present : literal ∈ support w) : actual literal = true := by
  induction w with
  | truth => cases present
  | atom value holds =>
      have same : literal = value := by simpa only [support, List.mem_singleton] using present
      exact same ▸ holds
  | conj left right ihLeft ihRight =>
      have either : literal ∈ support left ∨ literal ∈ support right := by
        simpa only [support, List.mem_eraseDups, List.mem_append] using present
      exact either.elim ihLeft ihRight
  | left left ih => exact ih present
  | right right ih => exact ih present

theorem supported_literal_declared {actual : FullLiteral → Bool} {formula : Formula}
    (w : Witness actual formula) (literal : FullLiteral) (present : literal ∈ support w) : literal ∈ atoms formula := by
  induction w with
  | truth => cases present
  | atom value _ => exact present
  | conj left right ihLeft ihRight =>
      have either : literal ∈ support left ∨ literal ∈ support right := by
        simpa only [support, List.mem_eraseDups, List.mem_append] using present
      simpa only [atoms, List.mem_eraseDups, List.mem_append] using either.imp ihLeft ihRight
  | left left ih =>
      exact List.mem_eraseDups.mpr (List.mem_append.mpr (Or.inl (ih present)))
  | right right ih =>
      exact List.mem_eraseDups.mpr (List.mem_append.mpr (Or.inr (ih present)))

/-- An NNF circuit for a proof which contains the designated original leaf. -/
def withLeaf (literal : FullLiteral) : Formula → Formula
  | .falsity | .truth => .falsity
  | .atom value => if literal = value then .atom value else .falsity
  | .disj a b => .disj (withLeaf literal a) (withLeaf literal b)
  | .conj a b => .disj (.conj (withLeaf literal a) b) (.conj a (withLeaf literal b))

/-- An NNF circuit for a proof containing two designated distinct FULL leaves.
    This circuit shares the same formula/subformula operands in a DAG adapter. -/
def withPair (first second : FullLiteral) : Formula → Formula
  | .falsity | .truth | .atom _ => .falsity
  | .disj a b => .disj (withPair first second a) (withPair first second b)
  | .conj a b => .disj
      (.disj (.conj (withPair first second a) b) (.conj a (withPair first second b)))
      (.disj (.conj (withLeaf first a) (withLeaf second b))
             (.conj (withLeaf second a) (withLeaf first b)))

theorem withLeaf_covers {actual : FullLiteral → Bool} {formula : Formula}
    (w : Witness actual formula) (literal : FullLiteral) (present : literal ∈ support w) :
    eval actual (withLeaf literal formula) = true := by
  induction w with
  | truth => cases present
  | atom value holds =>
      have same : literal = value := by simpa only [support, List.mem_singleton] using present
      simp only [withLeaf, same, ↓reduceIte, eval, holds]
  | conj left right ihLeft ihRight =>
      have either : literal ∈ support left ∨ literal ∈ support right := by
        simpa only [support, List.mem_eraseDups, List.mem_append] using present
      rcases either with hl | hr
      · exact Bool.or_eq_true_iff.mpr (Or.inl (Bool.and_eq_true_iff.mpr ⟨ihLeft hl, witness_holds right⟩))
      · exact Bool.or_eq_true_iff.mpr (Or.inr (Bool.and_eq_true_iff.mpr ⟨witness_holds left, ihRight hr⟩))
  | left left ih => exact Bool.or_eq_true_iff.mpr (Or.inl (ih present))
  | right right ih => exact Bool.or_eq_true_iff.mpr (Or.inr (ih present))

theorem withPair_covers {actual : FullLiteral → Bool} {formula : Formula}
    (w : Witness actual formula) (first second : FullLiteral) (different : first ≠ second)
    (one : first ∈ support w) (two : second ∈ support w) :
    eval actual (withPair first second formula) = true := by
  induction w with
  | truth => cases one
  | atom value _ =>
      have ha : first = value := by simpa only [support, List.mem_singleton] using one
      have hb : second = value := by simpa only [support, List.mem_singleton] using two
      exact False.elim (different (ha.trans hb.symm))
  | left left ih => exact Bool.or_eq_true_iff.mpr (Or.inl (ih one two))
  | right right ih => exact Bool.or_eq_true_iff.mpr (Or.inr (ih one two))
  | conj left right ihLeft ihRight =>
      have ha : first ∈ support left ∨ first ∈ support right := by
        simpa only [support, List.mem_eraseDups, List.mem_append] using one
      have hb : second ∈ support left ∨ second ∈ support right := by
        simpa only [support, List.mem_eraseDups, List.mem_append] using two
      rcases ha with al | ar <;> rcases hb with bl | br
      · exact Bool.or_eq_true_iff.mpr (Or.inl (Bool.or_eq_true_iff.mpr (Or.inl
          (Bool.and_eq_true_iff.mpr ⟨ihLeft al bl, witness_holds right⟩))))
      · exact Bool.or_eq_true_iff.mpr (Or.inr (Bool.or_eq_true_iff.mpr (Or.inl
          (Bool.and_eq_true_iff.mpr ⟨withLeaf_covers left first al, withLeaf_covers right second br⟩))))
      · exact Bool.or_eq_true_iff.mpr (Or.inr (Bool.or_eq_true_iff.mpr (Or.inr
          (Bool.and_eq_true_iff.mpr ⟨withLeaf_covers left second bl, withLeaf_covers right first ar⟩))))
      · exact Bool.or_eq_true_iff.mpr (Or.inl (Bool.or_eq_true_iff.mpr (Or.inr
          (Bool.and_eq_true_iff.mpr ⟨witness_holds left, ihRight ar br⟩))))

def witnessOfEval (actual : FullLiteral → Bool) : (formula : Formula) →
    eval actual formula = true → Witness actual formula
  | .falsity, impossible => False.elim (Bool.noConfusion impossible)
  | .truth, _ => .truth
  | .atom literal, holds => .atom literal holds
  | .conj a b, holds => .conj (witnessOfEval actual a (Bool.and_eq_true_iff.mp holds).1)
      (witnessOfEval actual b (Bool.and_eq_true_iff.mp holds).2)
  | .disj a b, holds =>
      if left : eval actual a = true then .left (witnessOfEval actual a left)
      else .right (witnessOfEval actual b ((Bool.or_eq_true_iff.mp holds).resolve_left left))

def withLeafWitness (actual : FullLiteral → Bool) (literal : FullLiteral) :
    (formula : Formula) → eval actual (withLeaf literal formula) = true →
      { w : Witness actual formula // literal ∈ support w }
  | .falsity, impossible => False.elim (Bool.noConfusion impossible)
  | .truth, impossible => False.elim (Bool.noConfusion impossible)
  | .atom value, holds => by
      simp only [withLeaf] at holds
      split at holds
      next same => exact ⟨.atom value holds, by simp only [support, List.mem_singleton]; exact same⟩
      next different => cases holds
  | .disj a b, holds => by
      if left : eval actual (withLeaf literal a) = true then
        let found := withLeafWitness actual literal a left
        exact ⟨.left found.val, found.property⟩
      else
        have right := (Bool.or_eq_true_iff.mp holds).resolve_left left
        let found := withLeafWitness actual literal b right
        exact ⟨.right found.val, found.property⟩
  | .conj a b, holds => by
      if left : (eval actual (withLeaf literal a) && eval actual b) = true then
        let found := withLeafWitness actual literal a (Bool.and_eq_true_iff.mp left).1
        let other := witnessOfEval actual b (Bool.and_eq_true_iff.mp left).2
        exact ⟨.conj found.val other, List.mem_eraseDups.mpr (List.mem_append.mpr (Or.inl found.property))⟩
      else
        have right := (Bool.or_eq_true_iff.mp holds).resolve_left left
        let other := witnessOfEval actual a (Bool.and_eq_true_iff.mp right).1
        let found := withLeafWitness actual literal b (Bool.and_eq_true_iff.mp right).2
        exact ⟨.conj other found.val, List.mem_eraseDups.mpr (List.mem_append.mpr (Or.inr found.property))⟩

def withPairWitness (actual : FullLiteral → Bool) (first second : FullLiteral) :
    (formula : Formula) → eval actual (withPair first second formula) = true →
      { w : Witness actual formula // first ∈ support w ∧ second ∈ support w }
  | .falsity, impossible => False.elim (Bool.noConfusion impossible)
  | .truth, impossible => False.elim (Bool.noConfusion impossible)
  | .atom _, impossible => False.elim (Bool.noConfusion impossible)
  | .disj a b, holds => by
      if left : eval actual (withPair first second a) = true then
        let found := withPairWitness actual first second a left
        exact ⟨.left found.val, found.property⟩
      else
        have right := (Bool.or_eq_true_iff.mp holds).resolve_left left
        let found := withPairWitness actual first second b right
        exact ⟨.right found.val, found.property⟩
  | .conj a b, holds => by
      if left : (eval actual (withPair first second a) && eval actual b) = true then
        let found := withPairWitness actual first second a (Bool.and_eq_true_iff.mp left).1
        let other := witnessOfEval actual b (Bool.and_eq_true_iff.mp left).2
        exact ⟨.conj found.val other,
          List.mem_eraseDups.mpr (List.mem_append.mpr (Or.inl found.property.1)),
          List.mem_eraseDups.mpr (List.mem_append.mpr (Or.inl found.property.2))⟩
      else if right : (eval actual a && eval actual (withPair first second b)) = true then
        let other := witnessOfEval actual a (Bool.and_eq_true_iff.mp right).1
        let found := withPairWitness actual first second b (Bool.and_eq_true_iff.mp right).2
        exact ⟨.conj other found.val,
          List.mem_eraseDups.mpr (List.mem_append.mpr (Or.inr found.property.1)),
          List.mem_eraseDups.mpr (List.mem_append.mpr (Or.inr found.property.2))⟩
      else if cross : (eval actual (withLeaf first a) && eval actual (withLeaf second b)) = true then
        let l := withLeafWitness actual first a (Bool.and_eq_true_iff.mp cross).1
        let r := withLeafWitness actual second b (Bool.and_eq_true_iff.mp cross).2
        exact ⟨.conj l.val r.val,
          List.mem_eraseDups.mpr (List.mem_append.mpr (Or.inl l.property)),
          List.mem_eraseDups.mpr (List.mem_append.mpr (Or.inr r.property))⟩
      else
        have last : (eval actual (withLeaf second a) && eval actual (withLeaf first b)) = true := by
          have all := Bool.or_eq_true_iff.mp holds
          rcases all with old | fresh
          · exact False.elim ((Bool.or_eq_true_iff.mp old).elim left right)
          · exact (Bool.or_eq_true_iff.mp fresh).resolve_left cross
        let l := withLeafWitness actual second a (Bool.and_eq_true_iff.mp last).1
        let r := withLeafWitness actual first b (Bool.and_eq_true_iff.mp last).2
        exact ⟨.conj l.val r.val,
          List.mem_eraseDups.mpr (List.mem_append.mpr (Or.inr r.property)),
          List.mem_eraseDups.mpr (List.mem_append.mpr (Or.inl l.property))⟩

/-- Pure monotonicity; it does not identify distinct bound positions. -/
theorem eval_mono (formula : Formula) (a b : FullLiteral → Bool)
    (cover : ∀ literal, a literal = true → b literal = true) :
    eval a formula = true → eval b formula = true := by
  induction formula with
  | falsity => intro impossible; cases impossible
  | truth => intro _; rfl
  | atom literal => exact cover literal
  | conj _ _ left right =>
      intro holds
      exact Bool.and_eq_true_iff.mpr ⟨left (Bool.and_eq_true_iff.mp holds).1, right (Bool.and_eq_true_iff.mp holds).2⟩
  | disj _ _ left right =>
      intro holds
      exact Bool.or_eq_true_iff.mpr ((Bool.or_eq_true_iff.mp holds).elim
        (fun h => Or.inl (left h)) (fun h => Or.inr (right h)))

theorem support_suffices {actual : FullLiteral → Bool} {formula : Formula}
    (w : Witness actual formula) :
    eval (fun literal => decide (literal ∈ support w)) formula = true := by
  induction w with
  | truth => rfl
  | atom literal _ => simp only [support, eval, List.mem_singleton, decide_true]
  | left left ih => exact Bool.or_eq_true_iff.mpr (Or.inl ih)
  | right right ih => exact Bool.or_eq_true_iff.mpr (Or.inr ih)
  | @conj a b left right ihLeft ihRight =>
      apply Bool.and_eq_true_iff.mpr
      constructor
      · apply eval_mono a _ _ _ ihLeft
        intro literal present
        apply decide_eq_true
        exact List.mem_eraseDups.mpr (List.mem_append.mpr (Or.inl (of_decide_eq_true present)))
      · apply eval_mono b _ _ _ ihRight
        intro literal present
        apply decide_eq_true
        exact List.mem_eraseDups.mpr (List.mem_append.mpr (Or.inr (of_decide_eq_true present)))

def singletonAdmission (formula : Formula) (anchor : FullLiteral) : Bool :=
  eval (fun literal => decide (literal = anchor)) (withLeaf anchor formula)

def unaryAnchors (formula : Formula) : List FullLiteral :=
  (atoms formula).filter (singletonAdmission formula)

def pairs (formula : Formula) : List (FullLiteral × FullLiteral) :=
  (atoms formula).flatMap (fun first => ((atoms formula).filter (fun second => decide (first ≠ second))).map (first, ·))

def multiAdmission (formula : Formula) : Formula :=
  Positive.foldOr ((pairs formula).map (fun pair => withPair pair.1 pair.2 formula))

theorem pair_declared (formula : Formula) (first second : FullLiteral)
    (one : first ∈ atoms formula) (two : second ∈ atoms formula) (different : first ≠ second) :
    (first, second) ∈ pairs formula := by
  apply List.mem_flatMap.mpr
  refine ⟨first, one, ?_⟩
  apply List.mem_map.mpr
  exact ⟨second, List.mem_filter.mpr ⟨two, by simpa only [decide_eq_true_eq] using different⟩, rfl⟩

theorem any_member_true (xs : List Formula) (actual : FullLiteral → Bool) (formula : Formula)
    (present : formula ∈ xs) (holds : eval actual formula = true) : Positive.any actual xs = true := by
  induction xs with
  | nil => cases present
  | cons head rest ih =>
      rcases List.mem_cons.mp present with same | prior
      · exact Bool.or_eq_true_iff.mpr (Or.inl (same ▸ holds))
      · exact Bool.or_eq_true_iff.mpr (Or.inr (ih prior))

theorem multi_admission_covers {actual : FullLiteral → Bool} {formula : Formula}
    (w : Witness actual formula) (first second : FullLiteral) (different : first ≠ second)
    (one : first ∈ support w) (two : second ∈ support w) : eval actual (multiAdmission formula) = true := by
  rw [multiAdmission, Positive.foldOr_eval]
  exact any_member_true _ _ _ (List.mem_map.mpr ⟨(first, second),
    pair_declared formula first second (supported_literal_declared w first one)
      (supported_literal_declared w second two) different, rfl⟩)
    (withPair_covers w first second different one two)

/-- The input family retains the original unary anchor before GROUP projection. -/
inductive Transfer where
  | unconditional
  | unary (anchor : GroupLiteral)
  | multi
  deriving DecidableEq, Repr
structure GroupCase where
  transfer : Transfer
  condition : Positive GroupLiteral
  deriving DecidableEq, Repr

def sourceCases (formula : Formula) : List GroupCase :=
  (if eval (fun _ => false) formula then [⟨.unconditional, .truth⟩] else []) ++
  ((unaryAnchors formula).map (fun anchor => ⟨.unary (projectLiteral anchor), .atom (projectLiteral anchor)⟩)).eraseDups ++
  [⟨.multi, Positive.normalize (Positive.map projectLiteral (multiAdmission formula))⟩]

/-- Distinct action occurrences are never deduplicated with the conditions. -/
structure ActionId where
  occurrence : Occurrence
  ordinal : Nat
  deriving DecidableEq, Repr
inductive TargetTail where
  | exact | anyTaint
  deriving DecidableEq, Repr
structure SourceAction where
  id : ActionId
  target : Position
  tail : TargetTail
  owner : Nat
  deriving DecidableEq, Repr
structure ExecutableCase where
  transfer : Transfer
  condition : Positive GroupLiteral
  action : SourceAction
  deriving DecidableEq, Repr

def attachActions (cases : List GroupCase) (actions : List SourceAction) : List ExecutableCase :=
  cases.flatMap (fun selected => actions.map (fun action => ⟨selected.transfer, selected.condition, action⟩))

theorem each_original_action (cases : List GroupCase) (actions : List SourceAction)
    (selected : GroupCase) (active : selected ∈ cases) (action : SourceAction) (present : action ∈ actions) :
    ExecutableCase.mk selected.transfer selected.condition action ∈ attachActions cases actions := by
  apply List.mem_flatMap.mpr
  exact ⟨selected, active, List.mem_map.mpr ⟨action, present, rfl⟩⟩

theorem attached_action_is_original (cases : List GroupCase) (actions : List SourceAction)
    (selected : ExecutableCase) (present : selected ∈ attachActions cases actions) : selected.action ∈ actions := by
  obtain ⟨condition, _, member⟩ := List.mem_flatMap.mp present
  obtain ⟨action, original, same⟩ := List.mem_map.mp member
  cases same
  exact original

theorem multi_mode_not_reclassified (formula : Formula) :
    GroupCase.mk .multi (Positive.normalize (Positive.map projectLiteral (multiAdmission formula))) ∈ sourceCases formula := by
  simp only [sourceCases, List.mem_append, List.mem_singleton]
  exact Or.inr True.intro

theorem unary_keeps_original_anchor (formula : Formula) (anchor : FullLiteral)
    (present : anchor ∈ unaryAnchors formula) :
    GroupCase.mk (.unary (projectLiteral anchor)) (.atom (projectLiteral anchor)) ∈ sourceCases formula := by
  apply List.mem_append.mpr
  apply Or.inl
  apply List.mem_append.mpr
  apply Or.inr
  apply List.mem_eraseDups.mpr
  exact List.mem_map.mpr ⟨anchor, present, rfl⟩

theorem packed_literals_nodup (literals : List FullLiteral) : literals.eraseDups.Nodup :=
  match literals with
  | [] => by simp
  | head :: tail => by
      rw [List.eraseDups_cons, List.nodup_cons]
      constructor
      · simp
      · exact packed_literals_nodup (tail.filter (fun next => !next == head))
termination_by literals.length
decreasing_by
  have bound := List.length_filter_le (fun next => !next == head) tail
  simp only [List.length_cons]
  omega

theorem support_nodup {actual : FullLiteral → Bool} {formula : Formula}
    (w : Witness actual formula) : (support w).Nodup := by
  induction w with
  | truth => exact List.nodup_nil
  | atom literal _ => exact List.nodup_cons.mpr ⟨List.not_mem_nil, List.nodup_nil⟩
  | conj _ _ _ _ => exact packed_literals_nodup _
  | left _ ih => exact ih
  | right _ ih => exact ih

/-- Classification observes distinct original FULL leaves, never normalized GROUP leaves. -/
def originalTransfer {actual : FullLiteral → Bool} {formula : Formula}
    (w : Witness actual formula) : Transfer :=
  match support w with
  | [] => .unconditional
  | [anchor] => .unary (projectLiteral anchor)
  | _ :: _ :: _ => .multi

/-- Re-evaluate the same proof shape using only its actual selected leaves. -/
def revalueWitness {actual : FullLiteral → Bool} {formula : Formula}
    (w : Witness actual formula) (next : FullLiteral → Bool)
    (available : ∀ literal, literal ∈ support w → next literal = true) : Witness next formula :=
  match w with
  | .truth => .truth
  | .atom literal _ => .atom literal (available literal List.mem_cons_self)
  | .left left => .left (revalueWitness left next available)
  | .right right => .right (revalueWitness right next available)
  | .conj left right => .conj
      (revalueWitness left next (fun literal present => available literal
        (List.mem_eraseDups.mpr (List.mem_append.mpr (Or.inl present)))))
      (revalueWitness right next (fun literal present => available literal
        (List.mem_eraseDups.mpr (List.mem_append.mpr (Or.inr present)))))

theorem revalue_keeps_support {actual : FullLiteral → Bool} {formula : Formula}
    (w : Witness actual formula) (next : FullLiteral → Bool)
    (available : ∀ literal, literal ∈ support w → next literal = true) :
    support (revalueWitness w next available) = support w := by
  induction w with
  | truth => rfl
  | atom _ _ => rfl
  | left left ih => exact ih available
  | right right ih => exact ih available
  | conj left right ihLeft ihRight =>
      simp only [revalueWitness, support, ihLeft, ihRight]

theorem unary_witness_admitted {actual : FullLiteral → Bool} {formula : Formula}
    (w : Witness actual formula) (anchor : FullLiteral) (same : support w = [anchor]) :
    anchor ∈ unaryAnchors formula := by
  apply List.mem_filter.mpr
  constructor
  · apply supported_literal_declared w anchor
    rw [same]
    exact List.mem_cons_self
  · let next := fun literal : FullLiteral => decide (literal = anchor)
    have available : ∀ literal, literal ∈ support w → next literal = true := by
      intro literal present
      have equal : literal = anchor := by simpa only [same, List.mem_singleton] using present
      simp only [next, equal, decide_true]
    let revised := revalueWitness w next available
    have member : anchor ∈ support revised := by
      rw [revalue_keeps_support, same]
      exact List.mem_cons_self
    exact withLeaf_covers revised anchor member

theorem empty_witness_admitted {actual : FullLiteral → Bool} {formula : Formula}
    (w : Witness actual formula) (same : support w = []) :
    eval (fun _ => false) formula = true := by
  have enough :=  support_suffices w
  simpa only [same, List.not_mem_nil, decide_false] using enough

theorem group_formula_coverage (formula : Formula) (actual : FullLiteral → Bool)
    (groups : GroupLiteral → Bool) (cover : Signed.GroupsCover actual groups)
    (holds : eval actual formula = true) :
    eval groups (Positive.normalize (Positive.map projectLiteral formula)) = true := by
  rw [Positive.normalize_eval, Positive.eval_map]
  exact eval_mono formula actual (fun literal => groups (projectLiteral literal)) cover holds

/-- Every original source proof has an executable same-mode GROUP case.
    The witness is constructed from the finite original FULL proof; no
    GROUP compiler success or helper-guide coverage is assumed. -/
def compileSourceWitness {actual : FullLiteral → Bool} {formula : Formula}
    (w : Witness actual formula) (groups : GroupLiteral → Bool)
    (cover : Signed.GroupsCover actual groups) :
    { selected : GroupCase // selected ∈ sourceCases formula ∧
      selected.transfer = originalTransfer w ∧ eval groups selected.condition = true } := by
  cases shape : support w with
  | nil =>
      refine ⟨⟨.unconditional, .truth⟩, ?_, ?_, rfl⟩
      · simp only [sourceCases, empty_witness_admitted w shape, ↓reduceIte, List.mem_append,
          List.mem_singleton]
        exact Or.inl (Or.inl True.intro)
      · simp only [originalTransfer, shape]
  | cons first rest =>
      cases rest with
      | nil =>
          refine ⟨⟨.unary (projectLiteral first), .atom (projectLiteral first)⟩, ?_, ?_, ?_⟩
          · exact unary_keeps_original_anchor formula first (unary_witness_admitted w first shape)
          · simp only [originalTransfer, shape]
          · exact cover first (supported_literal_holds w first (by rw [shape]; exact List.mem_cons_self))
      | cons second tail =>
          have distinct : first ≠ second := by
            have nodup := support_nodup w
            rw [shape] at nodup
            intro same
            exact (List.nodup_cons.mp nodup).1 (List.mem_cons.mpr (Or.inl same))
          have one : first ∈ support w := by rw [shape]; exact List.mem_cons_self
          have two : second ∈ support w := by rw [shape]; exact List.mem_cons_of_mem _ List.mem_cons_self
          refine ⟨⟨.multi, Positive.normalize (Positive.map projectLiteral (multiAdmission formula))⟩,
            multi_mode_not_reclassified formula, ?_, ?_⟩
          · simp only [originalTransfer, shape]
          · exact group_formula_coverage (multiAdmission formula) actual groups cover
              (multi_admission_covers w first second distinct one two)

def firstTrue {Item : Type} (items : List Item) (test : Item → Bool)
    (hit : items.any test = true) : { item : Item // item ∈ items ∧ test item = true } :=
  match items with
  | [] => False.elim (Bool.noConfusion hit)
  | head :: tail =>
      if active : test head = true then ⟨head, List.mem_cons_self, active⟩
      else
        let found := firstTrue tail test ((Bool.or_eq_true_iff.mp hit).resolve_left active)
        ⟨found.val, List.mem_cons_of_mem head found.property.1, found.property.2⟩

theorem positive_any_eq_list_any (items : List Formula) (actual : FullLiteral → Bool) :
    Positive.any actual items = items.any (fun formula => eval actual formula) := by
  induction items with
  | nil => rfl
  | cons head rest ih => simp only [Positive.any, List.any_cons, ih]

theorem pair_member_distinct (formula : Formula) (pair : FullLiteral × FullLiteral)
    (present : pair ∈ pairs formula) : pair.1 ≠ pair.2 := by
  obtain ⟨first, _, selected⟩ := List.mem_flatMap.mp present
  obtain ⟨second, allowed, same⟩ := List.mem_map.mp selected
  cases same
  exact of_decide_eq_true (List.mem_filter.mp allowed).2

/-- Reverse MULTI extraction. A successful projected circuit supplies a genuine
    original proof with at least two distinct FULL leaf identities. -/
def multiFamilyWitness (formula : Formula) (groups : GroupLiteral → Bool)
    (active : eval groups (Positive.normalize (Positive.map projectLiteral (multiAdmission formula))) = true) :
    { w : Witness (fun literal => groups (projectLiteral literal)) formula // originalTransfer w = .multi } := by
  have fullActive : eval (fun literal => groups (projectLiteral literal)) (multiAdmission formula) = true := by
    simpa only [Positive.normalize_eval, Positive.eval_map] using active
  have foundAny : (pairs formula).any (fun pair =>
      eval (fun literal => groups (projectLiteral literal)) (withPair pair.1 pair.2 formula)) = true := by
    simpa only [multiAdmission, Positive.foldOr_eval, positive_any_eq_list_any, List.any_map, Function.comp_def] using fullActive
  let found := firstTrue (pairs formula) _ foundAny
  let result := withPairWitness (fun literal => groups (projectLiteral literal)) found.val.1 found.val.2 formula found.property.2
  have different := pair_member_distinct formula found.val found.property.1
  refine ⟨result.val, ?_⟩
  cases shape : support result.val with
  | nil => have empty := result.property.1; rw [shape] at empty; cases empty
  | cons head tail =>
      cases tail with
      | nil =>
          have ha : found.val.1 = head := by simpa only [shape, List.mem_singleton] using result.property.1
          have hb : found.val.2 = head := by simpa only [shape, List.mem_singleton] using result.property.2
          exact False.elim (different (ha.trans hb.symm))
      | cons _ _ => simp only [originalTransfer, shape]

theorem nodup_singleton_of_only (items : List FullLiteral) (anchor : FullLiteral)
    (nodup : items.Nodup) (present : anchor ∈ items)
    (only : ∀ item, item ∈ items → item = anchor) : items = [anchor] := by
  cases items with
  | nil => cases present
  | cons first rest =>
      have same := only first List.mem_cons_self
      subst first
      cases rest with
      | nil => rfl
      | cons second tail =>
          have equal := only second (List.mem_cons_of_mem _ List.mem_cons_self)
          exact False.elim ((List.nodup_cons.mp nodup).1 (List.mem_cons.mpr (Or.inl equal.symm)))

/-- Reverse anchored UNARY extraction. A true unconditional branch alone is
    insufficient: singletonAdmission explicitly requires the original anchor. -/
def unaryFamilyWitness (formula : Formula) (anchor : FullLiteral)
    (registered : anchor ∈ unaryAnchors formula) (groups : GroupLiteral → Bool)
    (active : groups (projectLiteral anchor) = true) :
    { w : Witness (fun literal => groups (projectLiteral literal)) formula //
      support w = [anchor] ∧ originalTransfer w = .unary (projectLiteral anchor) } := by
  have singleton := (List.mem_filter.mp registered).2
  let original := withLeafWitness (fun literal => decide (literal = anchor)) anchor formula singleton
  have only : ∀ literal, literal ∈ support original.val → literal = anchor := by
    intro literal present
    exact of_decide_eq_true (supported_literal_holds original.val literal present)
  have shape := nodup_singleton_of_only (support original.val) anchor (support_nodup original.val) original.property only
  have available : ∀ literal, literal ∈ support original.val → groups (projectLiteral literal) = true := by
    intro literal present
    exact (only literal present) ▸ active
  let result := revalueWitness original.val (fun literal => groups (projectLiteral literal)) available
  have kept : support result = [anchor] := (revalue_keeps_support _ _ _).trans shape
  exact ⟨result, kept, by simp only [originalTransfer, kept]⟩

/-- The same proof extracts a real empty source proof, never a taint source
    from a merely possible marked input. -/
def unconditionalFamilyWitness (formula : Formula)
    (registered : eval (fun _ => false) formula = true) (groups : GroupLiteral → Bool) :
    { w : Witness (fun literal => groups (projectLiteral literal)) formula // originalTransfer w = .unconditional } := by
  let original := witnessOfEval (fun _ => false) formula registered
  have empty : support original = [] := by
    cases shape : support original with
    | nil => rfl
    | cons first rest =>
        have bad := supported_literal_holds original first (by rw [shape]; exact List.mem_cons_self)
        cases bad
  have available : ∀ literal, literal ∈ support original → groups (projectLiteral literal) = true := by
    intro literal present
    rw [empty] at present
    cases present
  let result := revalueWitness original (fun literal => groups (projectLiteral literal)) available
  have kept : support result = [] := (revalue_keeps_support _ _ _).trans empty
  exact ⟨result, by simp only [originalTransfer, kept]⟩

/-- Decode every admitted selected case to its original semantic family.
    This is the reverse direction within the GROUP concept. -/
def decodeSourceCase (formula : Formula) (selected : GroupCase)
    (registered : selected ∈ sourceCases formula) (groups : GroupLiteral → Bool)
    (active : eval groups selected.condition = true) :
    { w : Witness (fun literal => groups (projectLiteral literal)) formula //
      originalTransfer w = selected.transfer } := by
  let zeroCases : List GroupCase := if eval (fun _ => false) formula then [⟨.unconditional, .truth⟩] else []
  let unaries := ((unaryAnchors formula).map (fun anchor =>
    GroupCase.mk (.unary (projectLiteral anchor)) (.atom (projectLiteral anchor)))).eraseDups
  if first : selected ∈ zeroCases then
    if zero : eval (fun _ => false) formula = true then
      have same : selected = ⟨.unconditional, .truth⟩ := by simpa only [zeroCases, zero, ↓reduceIte, List.mem_singleton] using first
      let found := unconditionalFamilyWitness formula zero groups
      exact ⟨found.val, found.property.trans (congrArg GroupCase.transfer same).symm⟩
    else
      have absent : zeroCases = [] := by simp [zeroCases, zero]
      have impossible : False := by
        rw [absent] at first
        exact List.not_mem_nil first
      exact False.elim impossible
  else if middle : selected ∈ unaries then
    have someAnchor : (unaryAnchors formula).any (fun anchor => decide
        (GroupCase.mk (.unary (projectLiteral anchor)) (.atom (projectLiteral anchor)) = selected)) = true := by
      have mapped := List.mem_eraseDups.mp middle
      obtain ⟨anchor, present, same⟩ := List.mem_map.mp mapped
      exact List.any_eq_true.mpr ⟨anchor, present, by simp only [same, decide_true]⟩
    let found := firstTrue (unaryAnchors formula) _ someAnchor
    have same : GroupCase.mk (.unary (projectLiteral found.val)) (.atom (projectLiteral found.val)) = selected :=
      of_decide_eq_true found.property.2
    have holds : groups (projectLiteral found.val) = true := by
      have relation := congrArg (fun value => eval groups value.condition) same
      exact relation.trans active
    let result := unaryFamilyWitness formula found.val found.property.1 groups holds
    exact ⟨result.val, result.property.2.trans (congrArg GroupCase.transfer same)⟩
  else
    have either : (selected ∈ zeroCases ∨ selected ∈ unaries) ∨
        selected = ⟨.multi, Positive.normalize (Positive.map projectLiteral (multiAdmission formula))⟩ := by
      simpa only [sourceCases, List.mem_append, List.mem_singleton] using registered
    have same := either.resolve_left (fun prior => prior.elim first middle)
    have holds : eval groups (Positive.normalize (Positive.map projectLiteral (multiAdmission formula))) = true := by
      rw [same] at active
      exact active
    let result := multiFamilyWitness formula groups holds
    exact ⟨result.val, result.property.trans (congrArg GroupCase.transfer same).symm⟩

theorem source_family_equivalence (formula : Formula) (groups : GroupLiteral → Bool)
    (transfer : Transfer) :
    (∃ selected, selected ∈ sourceCases formula ∧ selected.transfer = transfer ∧
      eval groups selected.condition = true) ↔
    Nonempty { w : Witness (fun literal => groups (projectLiteral literal)) formula // originalTransfer w = transfer } := by
  constructor
  · rintro ⟨selected, registered, same, active⟩
    let found := decodeSourceCase formula selected registered groups active
    exact ⟨⟨found.val, found.property.trans same⟩⟩
  · rintro ⟨⟨w, same⟩⟩
    let found := compileSourceWitness w groups (fun _ holds => holds)
    exact ⟨found.val, found.property.1, found.property.2.1.trans same, found.property.2.2⟩

/-- A generic dispatch law covers arbitrary action/target/output types, including
    STAR and ANY. These primitives are parameters, not invented AP semantics.
    The theorem says that the compiler calls the same original primitive with
    the same original anchor/action/input. It is separate from fixed-tail tests. -/
def dispatch {Action Input Output : Type} (generate : Action → Input → Output)
    (unary : GroupLiteral → Action → Input → Output) (multi : Action → Input → Output)
    (transfer : Transfer) (action : Action) (input : Input) : Output :=
  match transfer with
  | .unconditional => generate action input
  | .unary anchor => unary anchor action input
  | .multi => multi action input

theorem generic_dispatch_preserved {Action Input Output : Type}
    (generate : Action → Input → Output) (unary : GroupLiteral → Action → Input → Output)
    (multi : Action → Input → Output) {actual : FullLiteral → Bool} {formula : Formula}
    (w : Witness actual formula) (groups : GroupLiteral → Bool) (cover : Signed.GroupsCover actual groups)
    (action : Action) (input : Input) :
    dispatch generate unary multi (compileSourceWitness w groups cover).val.transfer action input =
      dispatch generate unary multi (originalTransfer w) action input := by
  rw [(compileSourceWitness w groups cover).property.2.1]

/-- Fixed source generation is independent of an inherited MAY ancestor.
    The naive unary path has a different tail on that ancestor, as in Kotlin. -/
inductive ResultTail where
  | exact | any | anyTaint
  deriving DecidableEq, Repr
inductive InputHit where
  | atTarget | mayAncestor | mustAncestor
  deriving DecidableEq, Repr

def fixedTail : TargetTail → ResultTail
  | .exact => .exact
  | .anyTaint => .anyTaint

def outputTail (kind : Transfer) (target : TargetTail) (hit : InputHit) : ResultTail :=
  match kind, target, hit with
  | .unary _, .anyTaint, .mayAncestor => .any
  | _, target, _ => fixedTail target

structure Output where
  action : ActionId
  target : Position
  owner : Nat
  tail : ResultTail
  premise : List Nat
  demand : Bool
  deriving DecidableEq, Repr

def execute (selected : ExecutableCase) (hit : InputHit) (originals : List Nat) : Output :=
  ⟨selected.action.id, selected.action.target, selected.action.owner,
   outputTail selected.transfer selected.action.tail hit, originals.eraseDups, true⟩

theorem source_output_action_and_premise (selected : ExecutableCase) (hit : InputHit) (originals : List Nat) :
    (execute selected hit originals).action = selected.action.id ∧
    (execute selected hit originals).premise = originals.eraseDups ∧
    (execute selected hit originals).demand = true := ⟨rfl, rfl, rfl⟩

theorem mode_preserved_output (a b : ExecutableCase) (sameMode : a.transfer = b.transfer)
    (sameAction : a.action = b.action) (hit : InputHit) (originals : List Nat) :
    execute a hit originals = execute b hit originals := by
  cases a; cases b
  simp_all only [execute]

theorem multi_always_fixed (action : SourceAction) (condition : Positive GroupLiteral)
    (hit : InputHit) (originals : List Nat) :
    (execute ⟨.multi, condition, action⟩ hit originals).tail = fixedTail action.tail := by
  cases action with
  | mk id target tail owner => cases tail <;> cases hit <;> rfl

namespace Controls
open Run1GroupRuleViews.Controls (t u atY)
def aliases : Formula := .conj (.atom t) (.atom u)
def mixed : Formula := .disj (.atom t) (.conj (.atom t) (.atom u))
def g := projectLiteral t
def occurrence : Occurrence := ⟨1, 2, 3, 4, 5, 0⟩
def action : SourceAction := ⟨⟨occurrence, 0⟩, ⟨9, [], .exact⟩, .anyTaint, 7⟩
def secondAction : SourceAction := { action with id := ⟨occurrence, 1⟩ }
def controls : List (String × Bool) := [
  ("reverse MULTI witness retains distinct full leaves",
    decide (2 ≤ (support (multiFamilyWitness aliases (fun _ => true) (by decide)).val).length)),
  ("reverse UNARY witness keeps the original full anchor",
    decide (support (unaryFamilyWitness (.atom t) t (by decide) (fun _ => true) (by decide)).val = [t])),
  ("reverse empty proof is unconditional",
    decide (originalTransfer (unconditionalFamilyWitness .truth (by decide) (fun _ => false)).val = .unconditional)),
  ("AND aliases has no original unary anchor", (unaryAnchors aliases).isEmpty),
  ("AND aliases retain MULTI despite one group literal", decide (⟨.multi, .atom g⟩ ∈ sourceCases aliases)),
  ("old MULTI MUST output survives", decide ((execute ⟨.multi, .atom g, action⟩ .mayAncestor [1]).tail = .anyTaint)),
  ("naive unary would lose MUST", decide ((execute ⟨.unary g, .atom g, action⟩ .mayAncestor [1]).tail = .any)),
  ("mixed source retains both transfer meanings", decide (⟨.unary g, .atom g⟩ ∈ sourceCases mixed ∧ ⟨.multi, .atom g⟩ ∈ sourceCases mixed)),
  ("equal output actions keep distinct action IDs", decide (action.id ≠ secondAction.id) && decide ((attachActions [⟨.multi, .atom g⟩] [action, secondAction]).length = 2)),
  ("whole original tuple remains", decide ((execute ⟨.multi, .atom g, action⟩ .atTarget [10, 20, 10]).premise = [10, 20])),
  ("unconditional family remains", decide (⟨.unconditional, .truth⟩ ∈ sourceCases .truth)),
  ("unary anchor retains geometry", decide (projectLiteral t ≠ projectLiteral atY))
]
theorem controls_pass : controls.all Prod.snd = true := by decide
end Controls
end ApSpec.Run1GroupSourceCases
