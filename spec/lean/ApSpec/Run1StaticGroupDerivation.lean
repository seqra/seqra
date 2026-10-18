/-
  Rooted run-1 group supply. A group is supplied by an explicit source or
  boundary seed, or by a declared positive operation with supplied premises.
  FLOW and a request do not create a group. A request only selects supply.
  The finite executable closure keeps groups exact in this abstract domain.
  AP geometry, actual control reachability and operation compilation are
  separate adapters. Static order and caller context remain approximated.
-/
import Std
import ApSpec.RuleGroupMarks

namespace ApSpec.Run1StaticGroupDerivation
set_option autoImplicit false

structure Fact (Node : Type) where
  node : Node
  group : Nat
  deriving DecidableEq, Repr

structure Rule (Node : Type) where
  premises : List (Fact Node)
  conclusion : Fact Node
  deriving DecidableEq, Repr

variable {Node : Type} [DecidableEq Node]

/-- Every productive operation needs at least one supplied premise. A real
    zero-input source is an explicit seed, with its source guard certified
    by the operation adapter. A need is never an input of this function. -/
def enabled (known : List (Fact Node)) (rule : Rule Node) : Prop :=
  rule.premises ≠ [] ∧ ∀ f, f ∈ rule.premises → f ∈ known

instance enabledDecidable (known : List (Fact Node)) (rule : Rule Node) :
    Decidable (enabled known rule) := by
  unfold enabled
  infer_instance

def advance (rules : List (Rule Node)) (known : List (Fact Node)) : List (Fact Node) :=
  (known ++ (rules.filter (fun rule => decide (enabled known rule))).map Rule.conclusion).eraseDups

def closeAt (rules : List (Rule Node)) (seeds : List (Fact Node)) : Nat → List (Fact Node)
  | 0 => seeds.eraseDups
  | n + 1 => advance rules (closeAt rules seeds n)

/-- A bounded proof is data. It can be returned by an executable extractor.
    `retain` accounts for earlier answers. `apply` retains every premise,
    including the guard premises of a rule that changes the group. -/
inductive Derivation (rules : List (Rule Node)) (seeds : List (Fact Node)) :
    Nat → Fact Node → Type where
  | seed (fuel : Nat) (f : Fact Node) (source : f ∈ seeds) : Derivation rules seeds fuel f
  | retain {fuel : Nat} {f : Fact Node} (prior : Derivation rules seeds fuel f) :
      Derivation rules seeds (fuel + 1) f
  | apply {fuel : Nat} (rule : Rule Node) (declared : rule ∈ rules)
      (productive : rule.premises ≠ [])
      (inputs : (f : Fact Node) → f ∈ rule.premises → Derivation rules seeds fuel f) :
      Derivation rules seeds (fuel + 1) rule.conclusion

theorem advance_spec (rules : List (Rule Node)) (known : List (Fact Node)) (f : Fact Node) :
    f ∈ advance rules known ↔ f ∈ known ∨
      ∃ rule, rule ∈ rules ∧ enabled known rule ∧ rule.conclusion = f := by
  simp only [advance, List.mem_eraseDups, List.mem_append, List.mem_map,
    List.mem_filter, decide_eq_true_eq]
  constructor
  · intro h
    rcases h with h | ⟨rule, ⟨hr, he⟩, hf⟩
    · exact Or.inl h
    · exact Or.inr ⟨rule, hr, he, hf⟩
  · intro h
    rcases h with h | ⟨rule, hr, he, hf⟩
    · exact Or.inl h
    · exact Or.inr ⟨rule, ⟨hr, he⟩, hf⟩

theorem advance_includes (rules : List (Rule Node)) (known : List (Fact Node))
    (f : Fact Node) (h : f ∈ known) : f ∈ advance rules known :=
  (advance_spec rules known f).mpr (Or.inl h)

theorem seed_in_closure (rules : List (Rule Node)) (seeds : List (Fact Node))
    (fuel : Nat) (f : Fact Node) (h : f ∈ seeds) : f ∈ closeAt rules seeds fuel := by
  induction fuel with
  | zero => simpa [closeAt] using h
  | succ fuel ih => exact advance_includes rules _ f ih

theorem derivation_in_closure (rules : List (Rule Node)) (seeds : List (Fact Node))
    {fuel : Nat} {f : Fact Node} (d : Derivation rules seeds fuel f) :
    f ∈ closeAt rules seeds fuel := by
  induction d with
  | seed fuel f h => exact seed_in_closure rules seeds fuel f h
  | retain d ih => exact advance_includes rules _ _ ih
  | apply rule declared productive inputs ih =>
    apply (advance_spec rules _ _).mpr
    exact Or.inr ⟨rule, declared, ⟨productive, ih⟩, rfl⟩

/-- Constructive first-match search, including the list-membership witness. -/
def select {α : Type} (xs : List α) (predicate : α → Prop) [DecidablePred predicate] :
    Option { x : α // x ∈ xs ∧ predicate x } :=
  match xs with
  | [] => none
  | x :: tail =>
    if hx : predicate x then
      some ⟨x, ⟨by simp, hx⟩⟩
    else
      (select tail predicate).map (fun found =>
        ⟨found.val, ⟨List.mem_cons_of_mem x found.property.1, found.property.2⟩⟩)

theorem select_none {α : Type} (xs : List α) (predicate : α → Prop)
    [DecidablePred predicate] (h : select xs predicate = none) :
    ∀ x, x ∈ xs → ¬ predicate x := by
  induction xs with
  | nil => simp
  | cons head tail ih =>
    intro x hx hp
    simp only [select] at h
    by_cases hh : predicate head
    · simp only [dif_pos hh] at h
      contradiction
    · simp only [dif_neg hh] at h
      rcases List.mem_cons.mp hx with eq | ht
      · subst x
        exact hh hp
      · have hn : select tail predicate = none := by
          cases hs : select tail predicate with
          | none => rfl
          | some found => simp only [hs, Option.map_some] at h; contradiction
        exact ih hn x ht hp

/-- Every closure answer carries a finite executable proof. This function
    uses decidable finite search. It uses no classical witness extraction. -/
def deriveAt (rules : List (Rule Node)) (seeds : List (Fact Node)) :
    (fuel : Nat) → (f : Fact Node) → f ∈ closeAt rules seeds fuel →
      Derivation rules seeds fuel f
  | 0, f, h => .seed 0 f (by simpa [closeAt] using h)
  | fuel + 1, f, h =>
    if prior : f ∈ closeAt rules seeds fuel then
      .retain (deriveAt rules seeds fuel f prior)
    else
      match found : select rules
        (fun rule => enabled (closeAt rules seeds fuel) rule ∧ rule.conclusion = f) with
      | some witness =>
        have output : witness.val.conclusion = f := witness.property.2.2
        output ▸ Derivation.apply witness.val witness.property.1 witness.property.2.1.1
          (fun input hin => deriveAt rules seeds fuel input (witness.property.2.1.2 input hin))
      | none => False.elim (by
        have existsRule := (advance_spec rules (closeAt rules seeds fuel) f).mp h
        rcases existsRule with earlier | ⟨rule, hr, he, hf⟩
        · exact prior earlier
        · exact select_none rules _ found rule hr ⟨he, hf⟩)

/-- The finite closure is exact for the positive reference calculus at the
    same proof depth, rather than for a pre-assumed whole-engine result. -/
theorem bounded_exact (rules : List (Rule Node)) (seeds : List (Fact Node))
    (fuel : Nat) (f : Fact Node) :
    f ∈ closeAt rules seeds fuel ↔ Nonempty (Derivation rules seeds fuel f) := by
  constructor
  · intro h; exact ⟨deriveAt rules seeds fuel f h⟩
  · rintro ⟨d⟩; exact derivation_in_closure rules seeds d

/-- No sources or boundary supply means no group, including in copy cycles. -/
theorem empty_seed_closure (rules : List (Rule Node)) (fuel : Nat) :
    closeAt rules [] fuel = [] := by
  induction fuel with
  | zero => rfl
  | succ fuel ih =>
    simp only [closeAt, ih, advance, List.nil_append]
    have notEnabled : ∀ rule : Rule Node, ¬ enabled [] rule := by
      intro rule h
      apply h.1
      cases hp : rule.premises with
      | nil => rfl
      | cons p ps =>
        have impossible := h.2 p (by simp [hp])
        exact False.elim (List.not_mem_nil impossible)
    have noneEnabled : ∀ items : List (Rule Node),
        items.filter (fun rule => decide (enabled [] rule)) = [] := by
      intro items
      induction items with
      | nil => rfl
      | cons rule rest ih => simp only [List.filter_cons, decide_eq_false (notEnabled rule),
          Bool.false_eq_true, reduceIte, ih]
    simp [noneEnabled]

/-- A root witness identifies a real seed. Its source group need not equal
    the result group: a declared operation can change that group. -/
def rootWitness (rules : List (Rule Node)) (seeds : List (Fact Node)) :
    {fuel : Nat} → {f : Fact Node} → Derivation rules seeds fuel f →
      { root : Fact Node // root ∈ seeds }
  | _, _, .seed _ f source => ⟨f, source⟩
  | _, _, .retain prior => rootWitness rules seeds prior
  | _, _, .apply rule _ productive inputs =>
      match hp : rule.premises with
      | [] => False.elim (productive hp)
      | p :: ps => rootWitness rules seeds (inputs p (by simp [hp]))

def Stable (rules : List (Rule Node)) (set : List (Fact Node)) : Prop :=
  ∀ rule, rule ∈ rules → enabled set rule → rule.conclusion ∈ set

/-- Leastness: every positive closed set containing the explicit seeds
    contains every finite answer. No global reachability premise is used. -/
theorem closure_least (rules : List (Rule Node)) (seeds target : List (Fact Node))
    (includeSeeds : ∀ f, f ∈ seeds → f ∈ target) (stable : Stable rules target)
    (fuel : Nat) : ∀ f, f ∈ closeAt rules seeds fuel → f ∈ target := by
  induction fuel with
  | zero => intro f h; exact includeSeeds f (by simpa [closeAt] using h)
  | succ fuel ih =>
    intro f h
    rcases (advance_spec rules _ f).mp h with old | ⟨rule, hr, he, hf⟩
    · exact ih f old
    · rw [← hf]
      exact stable rule hr ⟨he.1, fun p hp => ih p (he.2 p hp)⟩

/-- Need storage is independent of supply. Adding a need selects already
    derived supply and never changes the computed closure. -/
def answers (derived : List (Fact Node)) (needs : List (Fact Node)) : List (Fact Node) :=
  derived.filter (fun f => decide (f ∈ needs))

theorem answer_has_derivation (rules : List (Rule Node)) (seeds needs : List (Fact Node))
    (fuel : Nat) (f : Fact Node) (h : f ∈ answers (closeAt rules seeds fuel) needs) :
    Nonempty (Derivation rules seeds fuel f) :=
  (bounded_exact rules seeds fuel f).mp (List.mem_filter.mp h).1

def answerWitness (rules : List (Rule Node)) (seeds needs : List (Fact Node))
    (fuel : Nat) (f : Fact Node) (h : f ∈ answers (closeAt rules seeds fuel) needs) :
    Derivation rules seeds fuel f := deriveAt rules seeds fuel f (List.mem_filter.mp h).1

theorem requests_do_not_invent (rules : List (Rule Node)) (needs : List (Fact Node))
    (fuel : Nat) : answers (closeAt rules [] fuel) needs = [] := by
  simp [empty_seed_closure, answers]

theorem wrong_group_not_answered (known needs : List (Fact Node)) (f : Fact Node)
    (absent : f ∉ known) : f ∉ answers known needs := by
  intro h
  exact absent (List.mem_filter.mp h).1

theorem merge_requests_same_answers (known needs : List (Fact Node)) :
    answers known needs.eraseDups = answers known needs := by
  simp [answers]

/-- Copy preserves the group. A rule that changes it is a different declared
    operation, with an explicit productive input and any guard inputs. -/
def copyRule (origin target : Node) (group : Nat) : Rule Node :=
  ⟨[⟨origin, group⟩], ⟨target, group⟩⟩

def transformRule (origin target : Node) (inputGroup outputGroup : Nat) : Rule Node :=
  ⟨[⟨origin, inputGroup⟩], ⟨target, outputGroup⟩⟩

/-- Group projection merges full marks of one owner, but not two owners. -/
def fullSupply (node : Node) (marks : List RuleGroupMarks.OwnedMark) : List (Fact Node) :=
  (marks.map (fun mark => ⟨node, mark.group⟩)).eraseDups

theorem full_supply_exact (node : Node) (marks : List RuleGroupMarks.OwnedMark)
    (group : Nat) :
    (⟨node, group⟩ : Fact Node) ∈ fullSupply node marks ↔
      ∃ mark, mark ∈ marks ∧ mark.group = group := by
  simp only [fullSupply, List.mem_eraseDups, List.mem_map]
  constructor
  · rintro ⟨m, hm, heq⟩
    exact ⟨m, hm, congrArg Fact.group heq⟩
  · rintro ⟨m, hm, hgroup⟩
    exact ⟨m, hm, by cases m; simp_all⟩

/-- Executable positive proof trees. The premise function has finite domain:
    the declared list of operation inputs. It retains conjunction support. -/
inductive Tree (rules : List (Rule Node)) (seeds : List (Fact Node)) : Fact Node → Type where
  | source (f : Fact Node) (root : f ∈ seeds) : Tree rules seeds f
  | operation (rule : Rule Node) (declared : rule ∈ rules)
      (productive : rule.premises ≠ [])
      (inputs : (f : Fact Node) → f ∈ rule.premises → Tree rules seeds f) :
      Tree rules seeds rule.conclusion

def toTree (rules : List (Rule Node)) (seeds : List (Fact Node)) :
    {fuel : Nat} → {f : Fact Node} → Derivation rules seeds fuel f → Tree rules seeds f
  | _, _, .seed _ f source => .source f source
  | _, _, .retain d => toTree rules seeds d
  | _, _, .apply rule declared productive inputs =>
      .operation rule declared productive (fun f h => toTree rules seeds (inputs f h))

def maxDepth : List Nat → Nat
  | [] => 0
  | head :: tail => max head (maxDepth tail)

theorem member_le_maxDepth (depths : List Nat) (n : Nat) (h : n ∈ depths) :
    n ≤ maxDepth depths := by
  induction depths with
  | nil => exact False.elim (List.not_mem_nil h)
  | cons head tail ih =>
    rcases List.mem_cons.mp h with hh | ht
    · subst n; exact Nat.le_max_left _ _
    · exact Nat.le_trans (ih ht) (Nat.le_max_right _ _)

def treeHeight (rules : List (Rule Node)) (seeds : List (Fact Node)) :
    {f : Fact Node} → Tree rules seeds f → Nat
  | _, .source _ _ => 0
  | _, .operation rule _ _ inputs =>
      maxDepth (rule.premises.attach.map (fun p => treeHeight rules seeds (inputs p.val p.property))) + 1

theorem closeAt_mono (rules : List (Rule Node)) (seeds : List (Fact Node))
    (n m : Nat) (h : n ≤ m) (f : Fact Node) (hf : f ∈ closeAt rules seeds n) :
    f ∈ closeAt rules seeds m := by
  induction h with
  | refl => exact hf
  | step _ ih => exact advance_includes rules _ f ih

theorem tree_in_closure (rules : List (Rule Node)) (seeds : List (Fact Node))
    {f : Fact Node} (tree : Tree rules seeds f) :
    f ∈ closeAt rules seeds (treeHeight rules seeds tree) := by
  induction tree with
  | source f root => simpa [treeHeight, closeAt] using root
  | operation rule declared productive inputs ih =>
    apply (advance_spec rules _ _).mpr
    refine Or.inr ⟨rule, declared, ⟨productive, ?_⟩, rfl⟩
    intro f hf
    have hdepth : treeHeight rules seeds (inputs f hf) ≤
        maxDepth (rule.premises.attach.map (fun p => treeHeight rules seeds (inputs p.val p.property))) :=
      member_le_maxDepth _ _ (List.mem_map.mpr ⟨⟨f, hf⟩, by simp, rfl⟩)
    exact closeAt_mono rules seeds _ _ hdepth f (ih f hf)

/-- A finite proof tree computes a concrete closure bound and witness. -/
def boundTree (rules : List (Rule Node)) (seeds : List (Fact Node))
    {f : Fact Node} (tree : Tree rules seeds f) :
    (fuel : Nat) × Derivation rules seeds fuel f :=
  ⟨treeHeight rules seeds tree,
    deriveAt rules seeds _ f (tree_in_closure rules seeds tree)⟩

/-- A constructive remove operation. Some library erase lemmas use classical
    choice. These small list proofs keep the executable solver constructive. -/
def removeFirst {α : Type} [DecidableEq α] (value : α) : List α → List α
  | [] => []
  | head :: tail => if head = value then tail else head :: removeFirst value tail

theorem removeFirst_subset {α : Type} [DecidableEq α] (value : α) (items : List α) :
    ∀ x, x ∈ removeFirst value items → x ∈ items := by
  induction items with
  | nil => simp [removeFirst]
  | cons head tail ih =>
    intro x hx
    by_cases equal : head = value
    · simp only [removeFirst, if_pos equal] at hx
      exact List.mem_cons_of_mem head hx
    · simp only [removeFirst, if_neg equal] at hx
      rcases List.mem_cons.mp hx with same | prior
      · exact List.mem_cons.mpr (Or.inl same)
      · exact List.mem_cons_of_mem head (ih x prior)

theorem removeFirst_keeps_other {α : Type} [DecidableEq α] (value : α) (items : List α)
    (x : α) (different : x ≠ value) (present : x ∈ items) :
    x ∈ removeFirst value items := by
  induction items with
  | nil => exact False.elim (List.not_mem_nil present)
  | cons head tail ih =>
    by_cases equal : head = value
    · simp only [removeFirst, if_pos equal]
      rcases List.mem_cons.mp present with same | prior
      · exact False.elim (different (same.trans equal))
      · exact prior
    · simp only [removeFirst, if_neg equal]
      rcases List.mem_cons.mp present with same | prior
      · exact List.mem_cons.mpr (Or.inl same)
      · exact List.mem_cons_of_mem head (ih prior)

theorem removeFirst_shorter {α : Type} [DecidableEq α] (value : α) (items : List α)
    (present : value ∈ items) : (removeFirst value items).length < items.length := by
  induction items with
  | nil => exact False.elim (List.not_mem_nil present)
  | cons head tail ih =>
    by_cases equal : head = value
    · simp only [removeFirst, if_pos equal, List.length_cons]
      exact Nat.lt_succ_self _
    · simp only [removeFirst, if_neg equal, List.length_cons]
      have ht : value ∈ tail := by
        rcases List.mem_cons.mp present with same | prior
        · exact False.elim (equal same.symm)
        · exact prior
      exact Nat.succ_lt_succ (ih ht)

/-- A partial result is stable for the remaining rules and includes the
    current known values. It uses the original rule and root catalog for
    every proof. The type is local to the executable saturation algorithm. -/
structure Saturation (rules : List (Rule Node)) (seeds : List (Fact Node))
    (pendingRules : List (Rule Node)) (known : List (Fact Node)) where
  facts : List (Fact Node)
  firings : Nat
  firingBound : firings ≤ pendingRules.length
  includes : ∀ f, f ∈ known → f ∈ facts
  sound : (f : Fact Node) → f ∈ facts → Tree rules seeds f
  stable : Stable pendingRules facts

/-- Each successful step removes one rule. Positive premises stay true.
    The first applicable firing is enough; its conclusion is retained.
    The algorithm thus terminates after at most the initial rule count.
    A blocked rule cannot generate an unsupported group to unblock itself. -/
def saturate (rules : List (Rule Node)) (seeds : List (Fact Node))
    (pendingRules : List (Rule Node)) (known : List (Fact Node))
    (declared : ∀ rule, rule ∈ pendingRules → rule ∈ rules)
    (grounded : (f : Fact Node) → f ∈ known → Tree rules seeds f) :
    Saturation rules seeds pendingRules known :=
  match found : select pendingRules (enabled known) with
  | none =>
      { facts := known
        firings := 0
        firingBound := Nat.zero_le _
        includes := fun _ h => h
        sound := grounded
        stable := fun rule hr he => False.elim (select_none pendingRules _ found rule hr he) }
  | some selected =>
      let rule := selected.val
      let fresh := rule.conclusion :: known
      let newGrounded : (f : Fact Node) → f ∈ fresh → Tree rules seeds f :=
        fun f hf =>
          if same : f = rule.conclusion then
            same.symm ▸ Tree.operation rule (declared rule selected.property.1)
              selected.property.2.1 (fun p hp => grounded p (selected.property.2.2 p hp))
          else grounded f (by
            rcases List.mem_cons.mp hf with head | prior
            · exact False.elim (same head)
            · exact prior)
      let completed := saturate rules seeds (removeFirst rule pendingRules) fresh
        (fun r hr => declared r (removeFirst_subset rule pendingRules r hr)) newGrounded
      { facts := completed.facts
        firings := completed.firings + 1
        firingBound := Nat.succ_le_of_lt (Nat.lt_of_le_of_lt completed.firingBound
          (removeFirst_shorter rule pendingRules selected.property.1))
        includes := fun f hf => completed.includes f (List.mem_cons_of_mem _ hf)
        sound := completed.sound
        stable := fun r hr he => by
          by_cases equal : r = rule
          · subst r
            exact completed.includes rule.conclusion (by simp [fresh])
          · exact completed.stable r (removeFirst_keeps_other rule pendingRules r equal hr) he }
termination_by pendingRules.length
decreasing_by exact removeFirst_shorter _ _ selected.property.1

/-- Full finite least closure, with executable derivation certificates.
    External adapters must certify each root as a reached source or modeled
    boundary. A conditional input descriptor is a rule premise, not a root. -/
def solve (rules : List (Rule Node)) (seeds : List (Fact Node)) :
    Saturation rules seeds rules seeds :=
  saturate rules seeds rules seeds (fun _ h => h) (fun f h => .source f h)

theorem solution_contains_tree (rules : List (Rule Node)) (seeds : List (Fact Node))
    {f : Fact Node} (tree : Tree rules seeds f) : f ∈ (solve rules seeds).facts := by
  induction tree with
  | source f root => exact (solve rules seeds).includes f root
  | operation rule declared productive inputs ih =>
    exact (solve rules seeds).stable rule declared ⟨productive, ih⟩

theorem solution_exact (rules : List (Rule Node)) (seeds : List (Fact Node)) (f : Fact Node) :
    f ∈ (solve rules seeds).facts ↔ Nonempty (Tree rules seeds f) := by
  constructor
  · intro present; exact ⟨(solve rules seeds).sound f present⟩
  · rintro ⟨tree⟩; exact solution_contains_tree rules seeds tree

/-- The full solver returns a source witness for each group. It does not
    retain that witness as a required implementation storage dimension. -/
def solutionRoot (rules : List (Rule Node)) (seeds : List (Fact Node))
    (f : Fact Node) (present : f ∈ (solve rules seeds).facts) :
    { root : Fact Node // root ∈ seeds } :=
  rootWitness rules seeds (boundTree rules seeds ((solve rules seeds).sound f present)).snd


theorem solve_firing_bound (rules : List (Rule Node)) (seeds : List (Fact Node)) :
    (solve rules seeds).firings ≤ rules.length := (solve rules seeds).firingBound

theorem solution_empty_without_seed (rules : List (Rule Node)) :
    (solve rules []).facts = [] := by
  cases actual : (solve rules []).facts with
  | nil => rfl
  | cons first tail =>
    have present : first ∈ (solve rules []).facts := by rw [actual]; simp
    exact False.elim (List.not_mem_nil (solutionRoot rules [] first present).property)

/- The comparison reference has explicit identity static relay vertices.
   It uses the same accepted order/context approximation as the pool. Any
   reached relay site of a field can receive that field's established group.
   Productive local operations keep their complete premise lists. -/
inductive RelayNode where
  | point (point : Nat)
  | static (field : Nat) (relay : Nat)
  deriving DecidableEq, Repr

inductive PoolNode where
  | point (point : Nat)
  | field (field : Nat)
  deriving DecidableEq, Repr

def compactNode : RelayNode → PoolNode
  | .point point => .point point
  | .static field _ => .field field

def canonicalNode : PoolNode → RelayNode
  | .point point => .point point
  | .field field => .static field 0

def compactFact (f : Fact RelayNode) : Fact PoolNode := ⟨compactNode f.node, f.group⟩
def canonicalFact (f : Fact PoolNode) : Fact RelayNode := ⟨canonicalNode f.node, f.group⟩
def compactRule (rule : Rule RelayNode) : Rule PoolNode :=
  ⟨rule.premises.map compactFact, compactFact rule.conclusion⟩

@[simp] theorem compact_canonical (f : Fact PoolNode) :
    compactFact (canonicalFact f) = f := by cases f with | mk n g => cases n <;> rfl

/-- Ordinary expanded availability. A relay cannot change a field or group.
    Such relays add no source permission and keep no full-mark identity. -/
inductive Expanded (rules : List (Rule RelayNode)) (seeds : List (Fact RelayNode)) :
    Fact RelayNode → Type where
  | source (f : Fact RelayNode) (root : f ∈ seeds) : Expanded rules seeds f
  | operation (rule : Rule RelayNode) (declared : rule ∈ rules)
      (productive : rule.premises ≠ [])
      (inputs : (f : Fact RelayNode) → f ∈ rule.premises → Expanded rules seeds f) :
      Expanded rules seeds rule.conclusion
  | relay (field group origin target : Nat)
      (prior : Expanded rules seeds ⟨.static field origin, group⟩) :
      Expanded rules seeds ⟨.static field target, group⟩

/-- Every expanded finite proof has a direct compact proof. Only the
    identity relay step is erased. No productive operation is erased. -/
def contract (rules : List (Rule RelayNode)) (seeds : List (Fact RelayNode)) :
    {f : Fact RelayNode} → Expanded rules seeds f →
      Tree (rules.map compactRule) (seeds.map compactFact) (compactFact f)
  | _, .source f root => .source _ (List.mem_map.mpr ⟨f, root, rfl⟩)
  | _, .operation rule declared productive inputs =>
      .operation (compactRule rule) (List.mem_map.mpr ⟨rule, declared, rfl⟩)
        (by simpa [compactRule] using productive)
        (fun input hin => by
          have mapped := List.mem_map.mp hin
          let selected := select rule.premises (fun p => compactFact p = input)
          match found : select rule.premises (fun p => compactFact p = input) with
          | some original =>
              exact original.property.2 ▸ contract rules seeds (inputs original.val original.property.1)
          | none =>
              exact False.elim (by
                rcases mapped with ⟨p, hp, heq⟩
                exact select_none rule.premises _ found p hp heq))
  | _, .relay field group origin _ prior =>
      contract rules seeds (f := ⟨.static field origin, group⟩) prior

/-- Routing within one collapsed static field is an identity-only step.
    Local operation points cannot be interchanged by this function. -/
def route (rules : List (Rule RelayNode)) (seeds : List (Fact RelayNode))
    (origin target : Fact RelayNode) (same : compactFact origin = compactFact target)
    (proof : Expanded rules seeds origin) : Expanded rules seeds target := by
  rcases origin with ⟨on, og⟩
  rcases target with ⟨tn, tg⟩
  cases on with
  | point op =>
    cases tn with
    | point tp =>
      simp only [compactFact, compactNode, Fact.mk.injEq, PoolNode.point.injEq] at same
      rcases same with ⟨rfl, rfl⟩
      exact proof
    | static tf ti => simp [compactFact, compactNode] at same
  | static ofield oi =>
    cases tn with
    | point tp => simp [compactFact, compactNode] at same
    | static tfield ti =>
      simp only [compactFact, compactNode, Fact.mk.injEq, PoolNode.field.injEq] at same
      rcases same with ⟨rfl, rfl⟩
      exact .relay ofield og oi ti proof

/-- The reverse constructive witness rules out invented groups in the
    compact calculus. It reconstructs all productive local dependencies.
    The route step uses precisely the chosen reference's static availability
    approximation, not a claim about concrete CFG order. -/
def expand (rules : List (Rule RelayNode)) (seeds : List (Fact RelayNode)) :
    {f : Fact PoolNode} → Tree (rules.map compactRule) (seeds.map compactFact) f →
      Expanded rules seeds (canonicalFact f)
  | _, .source f root =>
      match found : select seeds (fun original => compactFact original = f) with
      | some original =>
          route rules seeds original.val (canonicalFact f)
            (original.property.2.trans (compact_canonical f).symm)
            (.source original.val original.property.1)
      | none => False.elim (by
          rcases List.mem_map.mp root with ⟨original, hin, heq⟩
          exact select_none seeds _ found original hin heq)
  | _, .operation rule declared productive inputs =>
      match found : select rules (fun original => compactRule original = rule) with
      | some original =>
          let operation : Expanded rules seeds original.val.conclusion :=
            .operation original.val original.property.1
              (by
                intro empty
                apply productive
                rw [← original.property.2]
                simp [compactRule, empty])
              (fun p hp =>
                let mapped : Fact PoolNode := compactFact p
                have hin : mapped ∈ rule.premises := by
                  rw [← original.property.2]
                  exact List.mem_map.mpr ⟨p, hp, rfl⟩
                route rules seeds (canonicalFact mapped) p (compact_canonical mapped)
                  (expand rules seeds (inputs mapped hin)))
          route rules seeds original.val.conclusion (canonicalFact rule.conclusion)
            (by
              rw [compact_canonical]
              exact congrArg Rule.conclusion original.property.2)
            operation
      | none => False.elim (by
          rcases List.mem_map.mp declared with ⟨original, hin, heq⟩
          exact select_none rules _ found original hin heq)

/-- Equality of reachable group facts in the abstract expanded and compact
    domains. Both directions have executable proof transformations above. -/
theorem compact_expanded_exact (rules : List (Rule RelayNode))
    (seeds : List (Fact RelayNode)) (f : Fact RelayNode) :
    Nonempty (Expanded rules seeds f) ↔
      Nonempty (Tree (rules.map compactRule) (seeds.map compactFact) (compactFact f)) := by
  constructor
  · rintro ⟨proof⟩; exact ⟨contract rules seeds proof⟩
  · rintro ⟨proof⟩
    exact ⟨route rules seeds _ f (compact_canonical (compactFact f)) (expand rules seeds proof)⟩

/-- Every stored atom belongs to the finite seed/conclusion universe.
    Per-rule/full-mark or per-writer-consumer keys are not required. -/
theorem finite_candidate_scope (rules : List (Rule Node)) (seeds : List (Fact Node))
    (fuel : Nat) (f : Fact Node) (h : f ∈ closeAt rules seeds fuel) :
    f ∈ seeds ++ rules.map Rule.conclusion := by
  induction fuel with
  | zero => exact List.mem_append.mpr (Or.inl (by simpa [closeAt] using h))
  | succ fuel ih =>
    rcases (advance_spec rules _ f).mp h with earlier | ⟨rule, hr, _, hf⟩
    · exact ih earlier
    · exact List.mem_append.mpr (Or.inr (List.mem_map.mpr ⟨rule, hr, hf⟩))

def relayChain (field group : Nat) :
    (hops : Nat) → Expanded [] [⟨.static field 0, group⟩] ⟨.static field hops, group⟩
  | 0 => .source _ (by simp)
  | n + 1 => .relay field group n (n + 1) (relayChain field group n)

/-- Count only static availability transfers. Productive local work is not
    included in this savings claim. -/
def relayWork (rules : List (Rule RelayNode)) (seeds : List (Fact RelayNode)) :
    {f : Fact RelayNode} → Expanded rules seeds f → Nat
  | _, .source _ _ => 0
  | _, .operation rule _ _ inputs =>
      (rule.premises.attach.map (fun p => relayWork rules seeds (inputs p.val p.property))).foldl Nat.add 0
  | _, .relay _ _ _ _ prior => relayWork rules seeds prior + 1

theorem relay_chain_work (field group hops : Nat) :
    relayWork [] [⟨.static field 0, group⟩] (relayChain field group hops) = hops := by
  induction hops with
  | zero => rfl
  | succ n ih => simpa [relayChain, relayWork] using congrArg Nat.succ ih

theorem contracted_chain_is_source (field group hops : Nat) :
    contract [] [⟨.static field 0, group⟩] (relayChain field group hops) =
      Tree.source (rules := []) (seeds := [⟨PoolNode.field field, group⟩])
        ⟨PoolNode.field field, group⟩ (by simp) := by
  induction hops with
  | zero => rfl
  | succ n ih => simpa only [relayChain, contract, compactFact, compactNode] using ih

theorem strict_chain_saving (field group hops : Nat) (positive : 0 < hops) :
    0 < relayWork [] [⟨.static field 0, group⟩] (relayChain field group hops) := by
  simpa [relay_chain_work] using positive

namespace Controls

def t : RuleGroupMarks.OwnedMark := ⟨7, 10⟩
def u : RuleGroupMarks.OwnedMark := ⟨7, 11⟩
def v : RuleGroupMarks.OwnedMark := ⟨8, 20⟩

def atom (point group : Nat) : Fact Nat := ⟨point, group⟩
def chain : List (Rule Nat) := [copyRule 0 1 7, copyRule 1 2 7]
def cycle : List (Rule Nat) := [copyRule 0 1 7, copyRule 1 0 7]
def transform : List (Rule Nat) := [transformRule 0 2 7 8]
def guardRule : Rule Nat := ⟨[atom 0 7, atom 1 7], atom 2 8⟩
def inputHypothesis : List (Rule Nat) := [copyRule 4 2 7]
def has (rules : List (Rule Nat)) (seeds : List (Fact Nat)) (fuel point group : Nat) : Bool :=
  (closeAt rules seeds fuel).contains (atom point group)

def controls : List (String × Bool) := [
  ("T and U with one owner merge", decide (fullSupply 0 [t, u] = [atom 0 7])),
  ("different owners stay different", decide (fullSupply 0 [t, v] = [atom 0 7, atom 0 8])),
  ("duplicate full mark merges", decide (fullSupply 0 [t, t, u] = [atom 0 7])),
  ("owner does not become another group", !(has [] (fullSupply 0 [t, u]) 0 0 8)),
  ("source is supply without a request", has [] [atom 0 7] 0 0 7),
  ("copy step preserves group", has chain [atom 0 7] 1 1 7),
  ("copy chain reaches second store", has chain [atom 0 7] 2 2 7),
  ("second store needs its input first", !(has chain [atom 0 7] 1 2 7)),
  ("copy does not invent owner eight", !(has chain [atom 0 7] 8 2 8)),
  ("unseeded copy cycle is empty", decide (closeAt cycle [] 8 = [])),
  ("unseeded request remains pending", decide (answers (closeAt cycle [] 8) [atom 1 7] = [])),
  ("FLOW metadata and interest have no seed API", decide (answers (closeAt [] [] 1) [atom 0 7] = [])),
  ("seed reaches a copy cycle", has cycle [atom 0 7] 3 1 7),
  ("a cycle does not make other owners", !(has cycle [atom 0 7] 3 1 8)),
  ("late real seed can answer old interest", decide (answers (closeAt cycle [atom 0 7] 2) [atom 1 7] = [atom 1 7])),
  ("wrong group interest has no answer", decide (answers (closeAt cycle [atom 0 7] 2) [atom 1 8] = [])),
  ("declared transform emits its output group", has transform [atom 0 7] 1 2 8),
  ("transform does not emit input group by default", !(has transform [atom 0 7] 1 2 7)),
  ("transform needs a rooted input", !(has transform [] 4 2 8)),
  ("wrong transform input group is insufficient", !(has transform [atom 0 8] 4 2 8)),
  ("one of two guard premises is insufficient", !(has [guardRule] [atom 0 7] 2 2 8)),
  ("two different points are retained", !(has [guardRule] [atom 0 7, atom 3 7] 2 2 8)),
  ("all declared guard premises enable output", has [guardRule] [atom 0 7, atom 1 7] 1 2 8),
  ("empty operation is not an implicit source", !(has [⟨[], atom 2 8⟩] [atom 0 7] 4 2 8)),
  ("canonical input hypothesis is not a source", !(has inputHypothesis [] 4 2 7)),
  ("actual rooted input enables that writer", has inputHypothesis [atom 4 7] 1 2 7),
  ("duplicate needs do not duplicate supply", decide (answers [atom 2 7] [atom 2 7, atom 2 7] = [atom 2 7])),
  ("empty seed cannot answer selected output group", decide (answers (closeAt transform [] 3) [atom 2 8] = [])),
  ("eight identity relays have eight carrier steps", decide (relayWork [] [⟨.static 1 0, 7⟩] (relayChain 1 7 8) = 8)),
  ("contracted relay proof is one source", decide (treeHeight [] [⟨PoolNode.field 1, 7⟩] (contract [] [⟨.static 1 0, 7⟩] (relayChain 1 7 8)) = 0)),
  ("complete solver needs no chosen depth", decide (atom 2 7 ∈ (solve chain [atom 0 7]).facts)),
  ("complete solver consumes two copies once", decide ((solve chain [atom 0 7]).firings = 2)),
  ("complete unseeded solver stays empty", decide ((solve cycle []).facts = [])),
  ("complete solver keeps conjunctive guard", decide (atom 2 8 ∉ (solve [guardRule] [atom 0 7]).facts)),
  ("complete guarded result has both premises", decide (atom 2 8 ∈ (solve [guardRule] [atom 0 7, atom 1 7]).facts))
]

/-- The first thirty controls reduce in the kernel. The full solver tests
    below use executable evaluation; its general invariants are proved above. -/
theorem finite_controls_pass : (controls.take 30).all Prod.snd = true := by decide

end Controls
end ApSpec.Run1StaticGroupDerivation
