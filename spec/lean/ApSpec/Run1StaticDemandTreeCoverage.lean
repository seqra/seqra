/-
  Frozen group-demand coverage for a finite positive semantic program.
  A guide keeps a rule ordinal, its complete ordered premise list, and a
  premise ordinal. It is guidance, not an ND/full-mark confirmation.
  Program nodes and geometry are opaque. JVM compilation is outside scope.
-/
import Std
import ApSpec.Run1StaticGroupDerivation

namespace ApSpec.Run1StaticDemandTreeCoverage
set_option autoImplicit false
open ApSpec.Run1StaticGroupDerivation

structure Indexed (α : Type) where
  ordinal : Nat
  value : α
  deriving DecidableEq, Repr

def enumerateFrom {α : Type} (start : Nat) : List α → List (Indexed α)
  | [] => []
  | head :: tail => ⟨start, head⟩ :: enumerateFrom (start + 1) tail

def enumerate {α : Type} (items : List α) : List (Indexed α) := enumerateFrom 0 items

theorem indexed_value_member {α : Type} (start : Nat) (items : List α) (item : Indexed α)
    (present : item ∈ enumerateFrom start items) : item.value ∈ items := by
  induction items generalizing start with
  | nil => simp [enumerateFrom] at present
  | cons head tail ih =>
      rcases List.mem_cons.mp present with same | prior
      · subst item; exact List.mem_cons_self
      · exact List.mem_cons_of_mem head (ih (start + 1) prior)

/-- Constructive first occurrence. Equal rules still have catalog ordinals. -/
def locate {α : Type} [DecidableEq α] (start : Nat) (items : List α)
    (target : α) (present : target ∈ items) :
    { item : Indexed α // item ∈ enumerateFrom start items ∧ item.value = target } :=
  match items with
  | [] => False.elim (List.not_mem_nil present)
  | head :: tail =>
      if same : head = target then
        ⟨⟨start, head⟩, List.mem_cons_self, same⟩
      else
        let found := locate (start + 1) tail target
          ((List.mem_cons.mp present).resolve_left (fun equal => same equal.symm))
        ⟨found.val, List.mem_cons_of_mem _ found.property.1, found.property.2⟩

def mapIndex {α β : Type} (project : α → β) (item : Indexed α) : Indexed β :=
  ⟨item.ordinal, project item.value⟩

theorem enumerate_map {α β : Type} (project : α → β) (start : Nat) (items : List α) :
    enumerateFrom start (items.map project) =
      (enumerateFrom start items).map (mapIndex project) := by
  induction items generalizing start with
  | nil => rfl
  | cons head tail ih =>
      simp only [List.map_cons, enumerateFrom, mapIndex, ih]

variable {Node : Type} [DecidableEq Node]

structure Guide (Node : Type) where
  owner : Indexed (Rule Node)
  premise : Indexed (Fact Node)
  deriving DecidableEq, Repr

def Guide.entry (guide : Guide Node) : Fact Node := guide.owner.value.conclusion
def Guide.exit (guide : Guide Node) : Fact Node := guide.premise.value
def Guide.support (guide : Guide Node) : List (Fact Node) := guide.owner.value.premises

/-- A frozen row exists only for a declared productive operation whose entire
    positive premise list is supplied by the least solution. -/
def freeze (rules : List (Rule Node)) (seeds : List (Fact Node)) : List (Guide Node) :=
  (enumerate rules).flatMap (fun owner =>
    if enabled (solve rules seeds).facts owner.value then
      (enumerate owner.value.premises).map (Guide.mk owner)
    else [])

def GuideCertificate (rules : List (Rule Node)) (seeds : List (Fact Node))
    (owner : Indexed (Rule Node)) (premise : Indexed (Fact Node)) : Type :=
  { guide : Guide Node // guide ∈ freeze rules seeds ∧
      guide.owner = owner ∧ guide.premise = premise }

def freezeWitness (rules : List (Rule Node)) (seeds : List (Fact Node))
    (owner : Indexed (Rule Node)) (declared : owner ∈ enumerate rules)
    (active : enabled (solve rules seeds).facts owner.value)
    (premise : Indexed (Fact Node)) (input : premise ∈ enumerate owner.value.premises) :
    GuideCertificate rules seeds owner premise :=
  ⟨⟨owner, premise⟩, by
    apply List.mem_flatMap.mpr
    refine ⟨owner, declared, ?_⟩
    rw [if_pos active]
    exact List.mem_map.mpr ⟨premise, input, rfl⟩, rfl, rfl⟩

theorem frozen_guide_spec (rules : List (Rule Node)) (seeds : List (Fact Node))
    (guide : Guide Node) : guide ∈ freeze rules seeds ↔
      guide.owner ∈ enumerate rules ∧
      enabled (solve rules seeds).facts guide.owner.value ∧
      guide.premise ∈ enumerate guide.owner.value.premises := by
  constructor
  · intro present
    obtain ⟨owner, declared, inner⟩ := List.mem_flatMap.mp present
    by_cases active : enabled (solve rules seeds).facts owner.value
    · rw [if_pos active] at inner
      obtain ⟨premise, input, same⟩ := List.mem_map.mp inner
      subst guide
      exact ⟨declared, active, input⟩
    · rw [if_neg active] at inner
      exact False.elim (List.not_mem_nil inner)
  · intro valid
    exact (freezeWitness rules seeds guide.owner valid.1 valid.2.1
      guide.premise valid.2.2).property.1

/-- Every retained guard has an executable positive proof, independently of
    which premise row the backward consumer visits first. -/
def frozenGuardTree (rules : List (Rule Node)) (seeds : List (Fact Node))
    (guide : Guide Node) (present : guide ∈ freeze rules seeds)
    (fact : Fact Node) (guard : fact ∈ guide.support) : Tree rules seeds fact :=
  (solve rules seeds).sound fact
    (((frozen_guide_spec rules seeds guide).mp present).2.1.2 fact guard)

theorem no_seed_no_frozen_guide (rules : List (Rule Node)) : freeze rules [] = [] := by
  cases equation : freeze rules [] with
  | nil => rfl
  | cons guide tail =>
      have present : guide ∈ freeze rules [] := by simp [equation]
      have active := ((frozen_guide_spec rules [] guide).mp present).2.1
      cases premises : guide.owner.value.premises with
      | nil => exact False.elim (active.1 premises)
      | cons first rest =>
          have supplied := active.2 first (by simp [premises])
          have empty := solution_empty_without_seed rules
          rw [empty] at supplied
          exact False.elim (List.not_mem_nil supplied)

theorem rule_enabled_from_trees (rules : List (Rule Node)) (seeds : List (Fact Node))
    (rule : Rule Node) (productive : rule.premises ≠ [])
    (inputs : (fact : Fact Node) → fact ∈ rule.premises → Tree rules seeds fact) :
    enabled (solve rules seeds).facts rule :=
  ⟨productive, fun fact present => solution_contains_tree rules seeds (inputs fact present)⟩

/-- Every node keeps all guards and all child witnesses. A row for one premise
    is not a proof that the whole rule's full-mark ND guard holds. -/
inductive Skeleton (rules : List (Rule Node)) (seeds : List (Fact Node)) : Fact Node → Type where
  | source (fact : Fact Node) (root : fact ∈ seeds) : Skeleton rules seeds fact
  | operation (rule : Rule Node) (owner : Indexed (Rule Node))
      (same : owner.value = rule) (declared : owner ∈ enumerate rules)
      (productive : rule.premises ≠ [])
      (guides : (premise : Indexed (Fact Node)) → premise ∈ enumerate rule.premises →
        GuideCertificate rules seeds owner premise)
      (children : (fact : Fact Node) → fact ∈ rule.premises → Skeleton rules seeds fact) :
      Skeleton rules seeds rule.conclusion

/-- This executable function derives all demand rows from the actual finite
    positive tree. It does not assume the desired trace already has guidance. -/
def buildSkeleton (rules : List (Rule Node)) (seeds : List (Fact Node)) :
    {target : Fact Node} → Tree rules seeds target → Skeleton rules seeds target
  | _, .source fact root => .source fact root
  | _, .operation rule declared productive inputs =>
      let found := locate 0 rules rule declared
      let active : enabled (solve rules seeds).facts found.val.value :=
        found.property.2.symm ▸ rule_enabled_from_trees rules seeds rule productive inputs
      .operation rule found.val found.property.2 found.property.1 productive
        (fun premise present => freezeWitness rules seeds found.val found.property.1 active
          premise (by simpa only [found.property.2] using present))
        (fun fact present => buildSkeleton rules seeds (inputs fact present))

structure FullEndpoint (Node : Type) where
  node : Node
  mark : RuleGroupMarks.OwnedMark
  deriving DecidableEq, Repr

def endpointMatches (fact : Fact Node) (endpoint : FullEndpoint Node) : Prop :=
  fact.node = endpoint.node ∧ fact.group = endpoint.mark.group

instance endpointMatchesDecidable (fact : Fact Node) (endpoint : FullEndpoint Node) :
    Decidable (endpointMatches fact endpoint) := by unfold endpointMatches; infer_instance

def admitsPair (guide : Guide Node) (output input : FullEndpoint Node) : Prop :=
  endpointMatches guide.entry output ∧ endpointMatches guide.exit input

/-- A real reverse primitive must supply both exact endpoints and permission.
    A grouped guide alone does not invent a full-mark preimage. -/
structure LocalReverseWitness (guide : Guide Node)
    (primitive : FullEndpoint Node → FullEndpoint Node → Prop) where
  output : FullEndpoint Node
  input : FullEndpoint Node
  outputOwner : endpointMatches guide.entry output
  inputOwner : endpointMatches guide.exit input
  permitted : primitive output input

def admitLocalReverse (guide : Guide Node)
    (primitive : FullEndpoint Node → FullEndpoint Node → Prop)
    (witness : LocalReverseWitness guide primitive) :
    { pair : FullEndpoint Node × FullEndpoint Node //
      admitsPair guide pair.1 pair.2 ∧ primitive pair.1 pair.2 } :=
  ⟨⟨witness.output, witness.input⟩,
    ⟨witness.outputOwner, witness.inputOwner⟩, witness.permitted⟩

/-- Full identity is part of a full semantic node. Group projection removes
    only that identity. Node and ordered guard positions are retained. -/
abbrev FullFact (Node : Type) := Fact (Node × Nat)
abbrev FullRule (Node : Type) := Rule (Node × Nat)

def fullFact (node : Node) (mark : RuleGroupMarks.OwnedMark) : FullFact Node :=
  ⟨⟨node, mark.full⟩, mark.group⟩

def projectFact (fact : FullFact Node) : Fact Node := ⟨fact.node.1, fact.group⟩

def projectRule (rule : FullRule Node) : Rule Node :=
  ⟨rule.premises.map projectFact, projectFact rule.conclusion⟩

omit [DecidableEq Node] in
theorem projection_preserves_guard_slots (rule : FullRule Node) :
    (projectRule rule).premises.length = rule.premises.length := by simp [projectRule]

def projectionPreimage (premises : List (FullFact Node)) (fact : Fact Node)
    (present : fact ∈ premises.map projectFact) :
    { full : FullFact Node // full ∈ premises ∧ projectFact full = fact } :=
  match found : select premises (fun full => projectFact full = fact) with
  | some witness => witness
  | none => False.elim (by
      obtain ⟨full, registered, same⟩ := List.mem_map.mp present
      exact select_none premises _ found full registered same)

omit [DecidableEq Node] in
theorem projected_productive (rule : FullRule Node) (productive : rule.premises ≠ []) :
    (projectRule rule).premises ≠ [] := by
  cases equation : rule.premises with
  | nil => exact False.elim (productive equation)
  | cons first rest => simp [projectRule, equation]

/-- A full finite witness constructively yields a positive grouped witness.
    Each guard subtree is projected; no empty-premise productive rule is added. -/
def projectTree (rules : List (FullRule Node)) (seeds : List (FullFact Node)) :
    {target : FullFact Node} → Tree rules seeds target →
      Tree (rules.map projectRule) (seeds.map projectFact) (projectFact target)
  | _, .source fact root => .source _ (List.mem_map.mpr ⟨fact, root, rfl⟩)
  | _, .operation rule declared productive inputs =>
      .operation (projectRule rule) (List.mem_map.mpr ⟨rule, declared, rfl⟩)
        (projected_productive rule productive)
        (fun fact present =>
          let full := projectionPreimage rule.premises fact present
          full.property.2 ▸ projectTree rules seeds (inputs full.val full.property.1))

/-- This quotient-level skeleton may select an equal grouped rule. Use the
    indexed full-rule API below to retain an original full catalog occurrence. -/
def fullWitnessSkeleton (rules : List (FullRule Node)) (seeds : List (FullFact Node))
    {target : FullFact Node} (tree : Tree rules seeds target) :
    Skeleton (rules.map projectRule) (seeds.map projectFact) (projectFact target) :=
  buildSkeleton _ _ (projectTree rules seeds tree)

/-- Keep the original full program beside its grouped guidance. Its rule and
    premise ordinals resolve full guards without changing their identities. -/
structure FullSnapshot (Node : Type) where
  rules : List (FullRule Node)
  seeds : List (FullFact Node)

def FullSnapshot.guides (snapshot : FullSnapshot Node) : List (Guide Node) :=
  freeze (snapshot.rules.map projectRule) (snapshot.seeds.map projectFact)

def fullRuleGuideWitness (rules : List (FullRule Node)) (seeds : List (FullFact Node))
    (owner : Indexed (FullRule Node)) (declared : owner ∈ enumerate rules)
    (productive : owner.value.premises ≠ [])
    (inputs : (fact : FullFact Node) → fact ∈ owner.value.premises → Tree rules seeds fact)
    (premise : Indexed (FullFact Node)) (input : premise ∈ enumerate owner.value.premises) :
    GuideCertificate (rules.map projectRule) (seeds.map projectFact)
      (mapIndex projectRule owner) (mapIndex projectFact premise) := by
  have ownerDeclared : mapIndex projectRule owner ∈ enumerate (rules.map projectRule) := by
    rw [enumerate, enumerate_map]
    exact List.mem_map.mpr ⟨owner, declared, rfl⟩
  have inputDeclared : mapIndex projectFact premise ∈
      enumerate (projectRule owner.value).premises := by
    rw [projectRule, enumerate, enumerate_map]
    exact List.mem_map.mpr ⟨premise, input, rfl⟩
  have active : enabled (solve (rules.map projectRule) (seeds.map projectFact)).facts
      (projectRule owner.value) := by
    apply rule_enabled_from_trees
    · exact projected_productive owner.value productive
    · intro fact present
      let full := projectionPreimage owner.value.premises fact present
      exact full.property.2 ▸ projectTree rules seeds (inputs full.val full.property.1)
  exact freezeWitness _ _ _ ownerDeclared active _ inputDeclared

/-- Unlike a quotient-only witness, this skeleton keeps the original FULL
    operation/guard payload and uses the matching catalog/premise ordinals. -/
inductive FullSkeleton (rules : List (FullRule Node)) (seeds : List (FullFact Node)) :
    FullFact Node → Type where
  | source (fact : FullFact Node) (root : fact ∈ seeds) : FullSkeleton rules seeds fact
  | operation (rule : FullRule Node) (owner : Indexed (FullRule Node))
      (same : owner.value = rule) (declared : owner ∈ enumerate rules)
      (productive : rule.premises ≠ [])
      (guides : (premise : Indexed (FullFact Node)) → premise ∈ enumerate rule.premises →
        GuideCertificate (rules.map projectRule) (seeds.map projectFact)
          (mapIndex projectRule owner) (mapIndex projectFact premise))
      (children : (fact : FullFact Node) → fact ∈ rule.premises → FullSkeleton rules seeds fact) :
      FullSkeleton rules seeds rule.conclusion

def buildFullSkeleton (rules : List (FullRule Node)) (seeds : List (FullFact Node)) :
    {target : FullFact Node} → Tree rules seeds target → FullSkeleton rules seeds target
  | _, .source fact root => .source fact root
  | _, .operation rule declared productive inputs =>
      let found := locate 0 rules rule declared
      let correspondingInputs : (fact : FullFact Node) →
          fact ∈ found.val.value.premises → Tree rules seeds fact :=
        fun fact present => inputs fact (by simpa only [found.property.2] using present)
      .operation rule found.val found.property.2 found.property.1 productive
        (fun premise present => fullRuleGuideWitness rules seeds found.val found.property.1
          (by simpa only [found.property.2] using productive) correspondingInputs
          premise (by simpa only [found.property.2] using present))
        (fun fact present => buildFullSkeleton rules seeds (inputs fact present))

def collectFullGuides (rules : List (FullRule Node)) (seeds : List (FullFact Node)) :
    {target : FullFact Node} → FullSkeleton rules seeds target → List (Guide Node)
  | _, .source _ _ => []
  | _, .operation rule _ _ _ _ guides children =>
      (enumerate rule.premises).attach.map (fun p => (guides p.val p.property).val) ++
        rule.premises.attach.flatMap (fun p => collectFullGuides rules seeds (children p.val p.property))

theorem collected_guides_frozen (rules : List (FullRule Node)) (seeds : List (FullFact Node))
    {target : FullFact Node} (skeleton : FullSkeleton rules seeds target)
    (guide : Guide Node) (present : guide ∈ collectFullGuides rules seeds skeleton) :
    guide ∈ freeze (rules.map projectRule) (seeds.map projectFact) := by
  induction skeleton with
  | source fact root => simp [collectFullGuides] at present
  | operation rule owner same declared productive guides children ih =>
      rcases List.mem_append.mp present with own | child
      · obtain ⟨premise, _, eq⟩ := List.mem_map.mp own
        subst guide
        exact (guides premise.val premise.property).property.1
      · obtain ⟨premise, _, appears⟩ := List.mem_flatMap.mp child
        exact ih premise.val premise.property appears

def endpointOf (fact : FullFact Node) : FullEndpoint Node :=
  ⟨fact.node.1, ⟨fact.group, fact.node.2⟩⟩

omit [DecidableEq Node] in
theorem projected_endpoint_matches (fact : FullFact Node) :
    endpointMatches (projectFact fact) (endpointOf fact) := ⟨rfl, rfl⟩

/-- This witness uses the original full rule and original full premise slot.
    It admits both exact endpoints without replacing either full identity. -/
def fullPairWitness (rules : List (FullRule Node)) (seeds : List (FullFact Node))
    (owner : Indexed (FullRule Node)) (declared : owner ∈ enumerate rules)
    (productive : owner.value.premises ≠ [])
    (inputs : (fact : FullFact Node) → fact ∈ owner.value.premises → Tree rules seeds fact)
    (premise : Indexed (FullFact Node)) (input : premise ∈ enumerate owner.value.premises) :
    { guide : Guide Node //
      guide ∈ freeze (rules.map projectRule) (seeds.map projectFact) ∧
      guide.owner = mapIndex projectRule owner ∧
      guide.premise = mapIndex projectFact premise ∧
      admitsPair guide (endpointOf owner.value.conclusion) (endpointOf premise.value) } := by
  let proof := fullRuleGuideWitness rules seeds owner declared productive inputs premise input
  refine ⟨proof.val, proof.property.1, proof.property.2.1, proof.property.2.2, ?_⟩
  constructor
  · simp only [Guide.entry, proof.property.2.1, mapIndex]
    exact projected_endpoint_matches _

  · simp only [Guide.exit, proof.property.2.2, mapIndex]
    exact projected_endpoint_matches _

/-- Publishing the admitted full pair still requires the actual local reverse
    primitive's permission. The group ledger supplies no such permission. -/
def fullLocalReversePublication (rules : List (FullRule Node)) (seeds : List (FullFact Node))
    (owner : Indexed (FullRule Node)) (declared : owner ∈ enumerate rules)
    (productive : owner.value.premises ≠ [])
    (inputs : (fact : FullFact Node) → fact ∈ owner.value.premises → Tree rules seeds fact)
    (premise : Indexed (FullFact Node)) (input : premise ∈ enumerate owner.value.premises)
    (primitive : FullEndpoint Node → FullEndpoint Node → Prop)
    (permitted : primitive (endpointOf owner.value.conclusion) (endpointOf premise.value)) :
    (guide : { guide : Guide Node //
      guide ∈ freeze (rules.map projectRule) (seeds.map projectFact) }) ×
      LocalReverseWitness guide.val primitive := by
  let pair := fullPairWitness rules seeds owner declared productive inputs premise input
  exact ⟨⟨pair.val, pair.property.1⟩,
    ⟨endpointOf owner.value.conclusion, endpointOf premise.value,
      pair.property.2.2.2.1, pair.property.2.2.2.2, permitted⟩⟩

/-- Point permissions are distinct from paired transfer guides. Only designated
    target points receive them. They do not automatically create B2 roots. -/
def freezePointDemands (rules : List (Rule Node)) (seeds : List (Fact Node))
    (targets : List Node) : List (Fact Node) :=
  ((solve rules seeds).facts.filter (fun fact => decide (fact.node ∈ targets))).eraseDups

def treePointWitness (rules : List (Rule Node)) (seeds : List (Fact Node))
    (targets : List Node) {target : Fact Node} (tree : Tree rules seeds target)
    (observed : target.node ∈ targets) :
    { permission : Fact Node // permission ∈ freezePointDemands rules seeds targets ∧
      permission = target } :=
  ⟨target, by
    simp only [freezePointDemands, List.mem_eraseDups, List.mem_filter, decide_eq_true_eq]
    exact ⟨solution_contains_tree rules seeds tree, observed⟩, rfl⟩

def fullTreePointWitness (rules : List (FullRule Node)) (seeds : List (FullFact Node))
    (targets : List Node) {target : FullFact Node} (tree : Tree rules seeds target)
    (observed : target.node.1 ∈ targets) :
    { permission : Fact Node //
      permission ∈ freezePointDemands (rules.map projectRule) (seeds.map projectFact) targets ∧
      endpointMatches permission (endpointOf target) } := by
  let point := treePointWitness (rules.map projectRule) (seeds.map projectFact)
    targets (projectTree rules seeds tree) observed
  exact ⟨point.val, point.property.1,
    point.property.2.symm ▸ projected_endpoint_matches target⟩

theorem no_seed_no_point_demand (rules : List (Rule Node)) (targets : List Node) :
    freezePointDemands rules [] targets = [] := by
  simp [freezePointDemands, solution_empty_without_seed]

omit [DecidableEq Node] in
theorem point_permission_accepts_owner (permission : Fact Node)
    (mark : RuleGroupMarks.OwnedMark) (same : permission.group = mark.group) :
    endpointMatches permission ⟨permission.node, mark⟩ := ⟨rfl, same⟩

namespace Controls

def input : FullFact Nat := fullFact 0 ⟨5, 5001⟩
def guard : FullFact Nat := fullFact 1 ⟨5, 5002⟩
def rewritten : FullFact Nat := fullFact 2 ⟨8, 8001⟩
def consumed : FullFact Nat := fullFact 3 ⟨8, 8001⟩
def rewrite : FullRule Nat := ⟨[input, guard], rewritten⟩
def consume : FullRule Nat := ⟨[rewritten], consumed⟩
def rules : List (FullRule Nat) := [rewrite, consume]
def seeds : List (FullFact Nat) := [input, guard]
def groupedRules : List (Rule Nat) := rules.map projectRule
def groupedSeeds : List (Fact Nat) := seeds.map projectFact
def frozen : List (Guide Nat) := freeze groupedRules groupedSeeds

def inputTree : Tree rules seeds input := .source _ (by decide)
def guardTree : Tree rules seeds guard := .source _ (by decide)

def rewriteInputs (fact : FullFact Nat) (present : fact ∈ rewrite.premises) :
    Tree rules seeds fact := by
  if same : fact = input then exact same ▸ inputTree
  else
    have alternatives : fact = input ∨ fact = guard := by simpa [rewrite] using present
    exact (alternatives.resolve_left same) ▸ guardTree

def rewriteTree : Tree rules seeds rewritten :=
  .operation rewrite (by decide) (by decide) rewriteInputs

def consumeInputs (fact : FullFact Nat) (present : fact ∈ consume.premises) :
    Tree rules seeds fact := by
  have same : fact = rewritten := by simpa [consume] using present
  exact same ▸ rewriteTree

def completeTree : Tree rules seeds consumed :=
  .operation consume (by decide) (by decide) consumeInputs

def allGuides : List (Guide Nat) := collectFullGuides rules seeds
  (buildFullSkeleton rules seeds completeTree)

def hasPair (guides : List (Guide Nat)) (ruleSlot premiseSlot outNode outGroup inNode inGroup : Nat) : Bool :=
  guides.any (fun guide => guide.owner.ordinal == ruleSlot && guide.premise.ordinal == premiseSlot &&
    guide.entry == Fact.mk outNode outGroup && guide.exit == Fact.mk inNode inGroup)

def sameGroupOtherInput : FullFact Nat := fullFact 0 ⟨5, 5009⟩
def sameGroupOtherOutput : FullFact Nat := fullFact 2 ⟨8, 8009⟩
def duplicateGroupGuard : FullRule Nat := ⟨[input, sameGroupOtherInput], rewritten⟩
def duplicateGuardSnapshot : FullSnapshot Nat :=
  ⟨[duplicateGroupGuard], [input, sameGroupOtherInput]⟩
def firstEquivalent : FullRule Nat := ⟨[input], rewritten⟩
def secondEquivalent : FullRule Nat := ⟨[sameGroupOtherInput], sameGroupOtherOutput⟩
def equivalentRules : List (FullRule Nat) := [firstEquivalent, secondEquivalent]
def equivalentSeeds : List (FullFact Nat) := [input, sameGroupOtherInput]

def equivalentInput (fact : FullFact Nat) (present : fact ∈ secondEquivalent.premises) :
    Tree equivalentRules equivalentSeeds fact := by
  have same : fact = sameGroupOtherInput := by simpa [secondEquivalent] using present
  exact same ▸ Tree.source sameGroupOtherInput (by decide)

def equivalentTree : Tree equivalentRules equivalentSeeds sameGroupOtherOutput :=
  .operation secondEquivalent (by decide) (by decide) equivalentInput

def equivalentGuides : List (Guide Nat) := collectFullGuides equivalentRules equivalentSeeds
  (buildFullSkeleton equivalentRules equivalentSeeds equivalentTree)

def localPrimitive (output input : FullEndpoint Nat) : Prop :=
  output = endpointOf rewritten ∧ input = endpointOf guard

def publication := fullLocalReversePublication rules seeds ⟨0, rewrite⟩ (by decide)
  (by decide) rewriteInputs ⟨1, guard⟩ (by decide) localPrimitive ⟨rfl, rfl⟩

def cycle : List (Rule Nat) := [copyRule 0 1 5, copyRule 1 0 5]

def controls : List (String × Bool) :=
  [ ("all projected full-tree rules have frozen rows", allGuides.all (fun guide => frozen.contains guide))
  , ("rewrite output group differs from input", hasPair frozen 0 0 2 8 0 5)
  , ("second same-group guard keeps its own point", hasPair frozen 0 1 2 8 1 5)
  , ("nested consumer keeps rewritten group", hasPair frozen 1 0 3 8 2 8)
  , ("whole guarded premise list is retained", frozen.any (fun guide => guide.owner.ordinal == 0 && guide.support.length == 2))
  , ("both distinct guard slots remain", (frozen.filter (fun guide => guide.owner.ordinal == 0)).length == 2)
  , ("missing guard produces no operation guidance", (freeze groupedRules [projectFact input]).isEmpty)
  , ("unrelated same-group point is insufficient", (freeze groupedRules
      [projectFact input, ⟨9, 5⟩]).isEmpty)
  , ("unseeded positive cycle emits no guide", (freeze cycle []).isEmpty)
  , ("empty productive rule is not a source", (freeze [Rule.mk [] ⟨2, 8⟩] [Fact.mk 0 5]).isEmpty)
  , ("full rule projection retains guard count", (projectRule rewrite).premises.length == 2)
  , ("projection keeps node and owner", projectFact input == Fact.mk 0 5)
  , ("projected equivalent rules are equal", projectRule firstEquivalent == projectRule secondEquivalent)
  , ("full witness keeps second original ordinal", equivalentGuides.any (fun guide => guide.owner.ordinal == 1))
  , ("equal grouped rule does not replace full ordinal", !(equivalentGuides.any (fun guide => guide.owner.ordinal == 0)))
  , ("same-node same-group full guards keep two slots", duplicateGuardSnapshot.guides.length == 2)
  , ("full snapshot keeps both full guard identities", duplicateGroupGuard.premises.map
      (fun fact => fact.node.2) == [5001, 5009])
  , ("same-group duplicate guard slots stay distinct", duplicateGuardSnapshot.guides.any
      (fun guide => guide.premise.ordinal == 0) && duplicateGuardSnapshot.guides.any
      (fun guide => guide.premise.ordinal == 1))
  , ("paired full publication keeps output identity", publication.snd.output.mark.full == 8001)
  , ("paired full publication keeps input identity", publication.snd.input.mark.full == 5002)
  , ("paired publication has different endpoint owners", publication.snd.output.mark.group == 8 && publication.snd.input.mark.group == 5)
  , ("frozen target point is supplied by full tree", (freezePointDemands groupedRules groupedSeeds [3]).contains ⟨3, 8⟩)
  , ("undesignated point gets no point permission", !(freezePointDemands groupedRules groupedSeeds [3]).contains ⟨2, 8⟩)
  , ("unseen full member matches point owner", decide (endpointMatches (Fact.mk 3 8)
      (FullEndpoint.mk 3 ⟨8, 8099⟩)))
  , ("another owner cannot use point permission", !decide (endpointMatches (Fact.mk 3 8)
      (FullEndpoint.mk 3 ⟨5, 5001⟩)))
  , ("same-stage source target has point permission", (freezePointDemands ([] : List (Rule Nat)) [⟨7, 5⟩] [7]).contains ⟨7, 5⟩)
  , ("same-stage source needs no operation guide", (freeze ([] : List (Rule Nat)) [⟨7, 5⟩]).isEmpty)
  , ("unseeded point has no permission", (freezePointDemands cycle [] [0, 1]).isEmpty)
  ]

theorem controls_pass : controls.all Prod.snd = true := by decide +kernel

end Controls

end ApSpec.Run1StaticDemandTreeCoverage
