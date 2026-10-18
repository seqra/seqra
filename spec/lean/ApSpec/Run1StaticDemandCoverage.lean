/-
  Forward GROUP-demand decoration at the run-1 handoff.
  Scope: finite successful identity-preserving FLOW interfaces. Call-input
  bindings are already reached and certified by the ordinary binding adapter.
  Actual source/input groups and rooted static field supply are explicit seeds.
  This module does not infer a concrete mark from FLOW. It computes group
  guidance before run-2, without taint-body replay or a reverse provider graph.
  Nonidentity operations, conjunction support and JVM trace compilation need
  separate adapters. A group guide is a search permission, not a taint fact.
-/
import ApSpec.Run1StaticGroupDerivation
import ApSpec.RuleGroupSchedule

namespace ApSpec.Run1StaticDemandCoverage
set_option autoImplicit false

inductive SlotKind where
  | ordinary (canonical : Nat)
  | staticField (field : Nat)
  deriving DecidableEq, Repr

structure Slot where
  method : Nat
  kind : SlotKind
  deriving DecidableEq, Repr

abbrev Mask := Run1StaticGroupDerivation.Fact Slot

/-- A call-input cut retains caller ORIGINAL -> actual argument -> callee
    canonical initial. The point and binding IDs retain the real interface.
    It is not a static availability relay or a backward local operation. -/
structure CallInput where
  callerOriginal : Slot
  calleeInput : Slot
  point : Nat
  binding : Nat
  deriving DecidableEq, Repr

structure FieldGroup where
  field : Nat
  group : Nat
  deriving DecidableEq, Repr

structure Supplies where
  actualInputs : List Mask := []
  staticGroups : List FieldGroup := []
  publishedWrites : List Nat := []
  deriving DecidableEq, Repr

/-- This maintained local contract is the read-admission interface. Rooted
    group provenance comes from the source/writer model, not this predicate.
    `actualInputs` must contain actual source/caller inputs, not hypotheses. -/
def HasWriteForSupply (supply : Supplies) : Prop :=
  ∀ pair, pair ∈ supply.staticGroups → pair.field ∈ supply.publishedWrites

/-- Each registered static original obtains masks from the SAME global
    field supply. This does not emit an initial fact at method entry. -/
def staticSeeds (slots : List Slot) (supply : Supplies) : List Mask :=
  slots.flatMap (fun slot => match slot.kind with
    | .ordinary _ => []
    | .staticField field =>
        (supply.staticGroups.filter (fun pair => pair.field == field)).map
          (fun pair => ⟨slot, pair.group⟩))

def seedMasks (slots : List Slot) (supply : Supplies) : List Mask :=
  (supply.actualInputs ++ staticSeeds slots supply).eraseDups

/-- The finite scalar oracle expands only GROUPS. Production can execute
    the identical forward relation with merged GroupSets on each binding. -/
def interfaceRules (groups : List Nat) (calls : List CallInput) : List (Run1StaticGroupDerivation.Rule Slot) :=
  calls.flatMap (fun call => groups.map (fun group =>
    Run1StaticGroupDerivation.copyRule call.callerOriginal call.calleeInput group))

def maskClosure (groups : List Nat) (calls : List CallInput)
    (slots : List Slot) (supply : Supplies) : List Mask :=
  (Run1StaticGroupDerivation.solve (interfaceRules groups calls) (seedMasks slots supply)).facts

theorem actual_input_seed (slots : List Slot) (supply : Supplies) (mask : Mask)
    (actual : mask ∈ supply.actualInputs) : mask ∈ seedMasks slots supply := by
  simp only [seedMasks, List.mem_eraseDups, List.mem_append]
  exact Or.inl actual

theorem static_field_seed (slots : List Slot) (supply : Supplies)
    (slot : Slot) (field group : Nat) (registered : slot ∈ slots)
    (static : slot.kind = .staticField field)
    (grounded : (⟨field, group⟩ : FieldGroup) ∈ supply.staticGroups) :
    (⟨slot, group⟩ : Mask) ∈ seedMasks slots supply := by
  apply List.mem_eraseDups.mpr
  apply List.mem_append.mpr
  apply Or.inr
  apply List.mem_flatMap.mpr
  refine ⟨slot, registered, ?_⟩
  rw [static]
  exact List.mem_map.mpr ⟨⟨field, group⟩,
    List.mem_filter.mpr ⟨grounded, decide_eq_true rfl⟩, rfl⟩

theorem static_seed_has_write (supply : Supplies) (safe : HasWriteForSupply supply)
    (field group : Nat) (grounded : (⟨field, group⟩ : FieldGroup) ∈ supply.staticGroups) :
    field ∈ supply.publishedWrites := safe ⟨field, group⟩ grounded

theorem call_rule_present (groups : List Nat) (calls : List CallInput)
    (call : CallInput) (group : Nat) (reached : call ∈ calls) (eligible : group ∈ groups) :
    Run1StaticGroupDerivation.copyRule call.callerOriginal call.calleeInput group ∈ interfaceRules groups calls := by
  exact List.mem_flatMap.mpr ⟨call, reached, List.mem_map.mpr ⟨group, eligible, rfl⟩⟩

/-- The reference eagerly passes the actual group through each reached
    identity call interface. It does not assume any endpoint demand exists. -/
inductive InterfaceTrace (calls : List CallInput) (roots : List Mask) (group : Nat) : Slot → Type where
  | root (slot : Slot) (actual : (⟨slot, group⟩ : Mask) ∈ roots) :
      InterfaceTrace calls roots group slot
  | call (cut : CallInput) (reached : cut ∈ calls)
      (prior : InterfaceTrace calls roots group cut.callerOriginal) :
      InterfaceTrace calls roots group cut.calleeInput

/-- Each eager interface trace constructs a proof in the forward mask
    closure. There is no desired-summary-coverage assumption. -/
def compileTrace (groups : List Nat) (calls : List CallInput) (roots : List Mask)
    (group : Nat) (eligible : group ∈ groups) :
    {slot : Slot} → InterfaceTrace calls roots group slot →
      Run1StaticGroupDerivation.Tree (interfaceRules groups calls) roots ⟨slot, group⟩
  | _, .root slot actual => .source _ actual
  | _, .call cut reached prior =>
      .operation (Run1StaticGroupDerivation.copyRule cut.callerOriginal cut.calleeInput group)
        (call_rule_present groups calls cut group reached eligible) (by simp [Run1StaticGroupDerivation.copyRule])
        (fun input present => by
          have same : input = ⟨cut.callerOriginal, group⟩ := by
            simpa only [Run1StaticGroupDerivation.copyRule, List.mem_singleton] using present
          exact same.symm ▸ compileTrace groups calls roots group eligible prior)

def visited {calls : List CallInput} {roots : List Mask} {group : Nat} :
    {slot : Slot} → InterfaceTrace calls roots group slot → List Slot
  | _, .root slot _ => [slot]
  | _, .call cut _ prior => cut.calleeInput :: visited prior

theorem trace_mask_present (groups : List Nat) (calls : List CallInput)
    (slots : List Slot) (supply : Supplies) (group : Nat) (eligible : group ∈ groups)
    {slot : Slot} (trace : InterfaceTrace calls (seedMasks slots supply) group slot) :
    (⟨slot, group⟩ : Mask) ∈ maskClosure groups calls slots supply :=
  Run1StaticGroupDerivation.solution_contains_tree _ _ (compileTrace groups calls _ group eligible trace)

/-- Coverage includes every helper along the trace, not only its endpoint. -/
theorem every_visited_mask (groups : List Nat) (calls : List CallInput)
    (slots : List Slot) (supply : Supplies) (group : Nat) (eligible : group ∈ groups)
    {slot : Slot} (trace : InterfaceTrace calls (seedMasks slots supply) group slot) :
    ∀ key, key ∈ visited trace →
      (⟨key, group⟩ : Mask) ∈ maskClosure groups calls slots supply := by
  induction trace with
  | root slot actual =>
    intro key present
    have same : key = slot := by simpa [visited] using present
    subst key
    exact trace_mask_present groups calls slots supply group eligible (.root slot actual)
  | call cut reached prior ih =>
    intro key present
    rcases List.mem_cons.mp present with same | prior
    · subst key
      exact trace_mask_present groups calls slots supply group eligible (.call cut reached prior)
    · exact ih key prior

/-- Every compiled rule has one registered same-group call-input witness.
    The statement is primitive syntax coverage, not endpoint reachability. -/
theorem rule_schema (groups : List Nat) (calls : List CallInput)
    (rule : Run1StaticGroupDerivation.Rule Slot)
    (present : rule ∈ interfaceRules groups calls) :
    ∃ call, call ∈ calls ∧
      Run1StaticGroupDerivation.copyRule call.callerOriginal call.calleeInput rule.conclusion.group = rule := by
  rcases List.mem_flatMap.mp present with ⟨call, hc, hm⟩
  rcases List.mem_map.mp hm with ⟨group, _, equal⟩
  refine ⟨call, hc, ?_⟩
  have resultGroup : group = rule.conclusion.group :=
    congrArg (fun r : Run1StaticGroupDerivation.Rule Slot => r.conclusion.group) equal
  rw [← resultGroup]
  exact equal

/-- A solver proof reconstructs the eager reference trace. Finite search
    returns a declared binding witness instead of using classical choice. -/
def reconstructTrace (groups : List Nat) (calls : List CallInput) (roots : List Mask) :
    {mask : Mask} → Run1StaticGroupDerivation.Tree (interfaceRules groups calls) roots mask →
      InterfaceTrace calls roots mask.group mask.node
  | _, .source mask actual => .root mask.node actual
  | _, .operation rule declared _ inputs =>
      match found : Run1StaticGroupDerivation.select calls (fun call =>
        Run1StaticGroupDerivation.copyRule call.callerOriginal call.calleeInput rule.conclusion.group = rule) with
      | some original =>
          have inputPresent :
              (⟨original.val.callerOriginal, rule.conclusion.group⟩ : Mask) ∈ rule.premises := by
            have shape : [({ node := original.val.callerOriginal, group := rule.conclusion.group } : Mask)] = rule.premises :=
              congrArg Run1StaticGroupDerivation.Rule.premises original.property.2
            rw [← shape]
            simp
          let prior := reconstructTrace groups calls roots
            (inputs ⟨original.val.callerOriginal, rule.conclusion.group⟩ inputPresent)
          have outputSame : original.val.calleeInput = rule.conclusion.node :=
            congrArg (fun r : Run1StaticGroupDerivation.Rule Slot => r.conclusion.node) original.property.2
          outputSame ▸ InterfaceTrace.call original.val original.property.1 prior
      | none => False.elim (by
          rcases rule_schema groups calls rule declared with ⟨call, hc, equal⟩
          exact Run1StaticGroupDerivation.select_none calls _ found call hc equal)

def referenceTrace (groups : List Nat) (calls : List CallInput)
    (slots : List Slot) (supply : Supplies) (mask : Mask)
    (present : mask ∈ maskClosure groups calls slots supply) :
    InterfaceTrace calls (seedMasks slots supply) mask.group mask.node :=
  reconstructTrace groups calls _ ((Run1StaticGroupDerivation.solve _ _).sound mask present)

theorem eager_and_mask_closure_equal (groups : List Nat) (calls : List CallInput)
    (slots : List Slot) (supply : Supplies) (group : Nat) (eligible : group ∈ groups)
    (slot : Slot) :
    (⟨slot, group⟩ : Mask) ∈ maskClosure groups calls slots supply ↔
      Nonempty (InterfaceTrace calls (seedMasks slots supply) group slot) := by
  constructor
  · intro present; exact ⟨referenceTrace groups calls slots supply ⟨slot, group⟩ present⟩
  · rintro ⟨trace⟩; exact trace_mask_present groups calls slots supply group eligible trace

/-- The identity call trace retains its actual root and group. A root still
    needs the caller/source provenance adapter stated for Supplies. -/
def traceRoot {calls : List CallInput} {roots : List Mask} {group : Nat} :
    {slot : Slot} → InterfaceTrace calls roots group slot →
      { root : Slot // (⟨root, group⟩ : Mask) ∈ roots }
  | _, .root slot actual => ⟨slot, actual⟩
  | _, .call _ _ prior => traceRoot prior

inductive Place where
  | ordinary (geometry : Nat)
  | staticField (field : Nat)
  deriving DecidableEq, Repr

inductive RowKind where
  | pairedSummary | observationPoint | writerPoint | callInputPoint | staticCarry
  deriving DecidableEq, Repr

/-- A row is an existing successful forward FLOW interface/cut. The payload
    retains original full NNF, occurrence, geometry and whole support IDs.
    Point rows cover internal checks/writers even without a normal return. -/
structure Row where
  original : Slot
  output : Place
  point : Nat
  kind : RowKind
  payload : RuleGroupSchedule.QueryPayload
  deriving DecidableEq, Repr

/-- Only group metadata is added. There is no exact-mark or actual-taint
    constructor in this handoff guide type. All guides are DEMAND. -/
structure GroupGuide where
  row : Row
  group : Nat
  deriving DecidableEq, Repr

inductive GuideLayer where
  | demand
  deriving DecidableEq, Repr

def GroupGuide.layer (_ : GroupGuide) : GuideLayer := .demand

def GroupGuide.pattern (guide : GroupGuide) : RuleGroupSchedule.GroupDemand :=
  ⟨guide.row.payload, guide.group⟩

structure GroupPattern where
  place : Place
  group : Nat
  deriving DecidableEq, Repr

def originalPlace (slot : Slot) : Place :=
  match slot.kind with
  | .ordinary geometry => .ordinary geometry
  | .staticField field => .staticField field

/-- Paired summaries reverse their two geometric ends and retain GROUP G
    on both ends. Point rows retain the same pair plus their actual point. -/
def GroupGuide.backwardPatterns (guide : GroupGuide) : GroupPattern × GroupPattern :=
  (⟨guide.row.output, guide.group⟩, ⟨originalPlace guide.row.original, guide.group⟩)

theorem backward_patterns_have_group (guide : GroupGuide) :
    guide.backwardPatterns.1.group = guide.group ∧
      guide.backwardPatterns.2.group = guide.group := ⟨rfl, rfl⟩

/-- B2 emits the actual full incoming member when its owner is demanded.
    The F1 guide stores only G. It cannot generate a full mark on its own. -/
def emitFromGroupGuide (guide : GroupGuide) (incoming : RuleGroupSchedule.FullMark) :
    Option RuleGroupSchedule.FullMark :=
  if guide.pattern.matches incoming then some incoming else none

theorem explicit_group_emits_full (guide : GroupGuide) (incoming : RuleGroupSchedule.FullMark)
    (owner : guide.group = incoming.group) : emitFromGroupGuide guide incoming = some incoming := by
  have reflexive : (incoming.group == incoming.group) = true := decide_eq_true rfl
  simp only [emitFromGroupGuide, GroupGuide.pattern, RuleGroupSchedule.GroupDemand.matches, owner,
    reflexive, reduceIte]

theorem emission_preserves_full_identity (guide : GroupGuide)
    (incoming emitted : RuleGroupSchedule.FullMark)
    (result : emitFromGroupGuide guide incoming = some emitted) : emitted = incoming := by
  unfold emitFromGroupGuide at result
  split at result
  · exact (Option.some.inj result).symm
  · contradiction

/-- This complete set is frozen before run-2. A B2 query reads a group
    permission; it does not add a new group demand. -/
def freezeGuides (rows : List Row) (masks : List Mask) : List GroupGuide :=
  (rows.flatMap (fun row =>
    (masks.filter (fun mask => mask.node == row.original)).map
      (fun mask => ⟨row, mask.group⟩))).eraseDups

theorem frozen_guide_iff (rows : List Row) (masks : List Mask) (guide : GroupGuide) :
    guide ∈ freezeGuides rows masks ↔ guide.row ∈ rows ∧
      (⟨guide.row.original, guide.group⟩ : Mask) ∈ masks := by
  simp only [freezeGuides, List.mem_eraseDups, List.mem_flatMap,
    List.mem_map, List.mem_filter, beq_iff_eq]
  constructor
  · rintro ⟨row, hr, mask, ⟨hm, hn⟩, eq⟩
    cases guide with
    | mk grow group =>
      simp only [GroupGuide.mk.injEq] at eq
      rcases eq with ⟨rfl, rfl⟩
      exact ⟨hr, by cases mask; simp_all⟩
  · rintro ⟨hr, hm⟩
    exact ⟨guide.row, hr, ⟨guide.row.original, guide.group⟩, ⟨hm, rfl⟩, by cases guide; rfl⟩

/-- Functional equality to eager group instantiation, including the explicit
    GROUP guide for each registered row. No full mark is emitted in run-1. -/
theorem eager_and_frozen_guides_equal (groups : List Nat) (calls : List CallInput)
    (slots : List Slot) (supply : Supplies) (rows : List Row)
    (row : Row) (group : Nat) (eligible : group ∈ groups) :
    (⟨row, group⟩ : GroupGuide) ∈ freezeGuides rows (maskClosure groups calls slots supply) ↔
      row ∈ rows ∧ Nonempty (InterfaceTrace calls (seedMasks slots supply) group row.original) := by
  rw [frozen_guide_iff, eager_and_mask_closure_equal groups calls slots supply group eligible]

/-- Executable demand witness for any required row at any visited helper.
    The only row premise is actual registration of that interface/cut. -/
def requiredGuide (groups : List Nat) (calls : List CallInput)
    (slots : List Slot) (supply : Supplies) (rows : List Row)
    (group : Nat) (eligible : group ∈ groups) {slot : Slot}
    (trace : InterfaceTrace calls (seedMasks slots supply) group slot)
    (row : Row) (registered : row ∈ rows) (visitedRow : row.original ∈ visited trace) :
    { guide : GroupGuide //
      guide ∈ freezeGuides rows (maskClosure groups calls slots supply) ∧
      guide.row = row ∧ guide.group = group } :=
  ⟨⟨row, group⟩, (frozen_guide_iff _ _ _).mpr
    ⟨registered, every_visited_mask groups calls slots supply group eligible trace row.original visitedRow⟩,
    rfl, rfl⟩

/-- Any catalog full member of G is admitted by the explicit G guide.
    F1 emits no exact full mark. B2 still uses the closed full catalog. -/
def fullMemberQuery (catalog : RuleGroupSchedule.Catalog) (guide : GroupGuide) (mark : RuleGroupSchedule.FullMark)
    (catalogued : mark ∈ catalog) (owner : guide.group = mark.group) :
    { query : RuleGroupSchedule.FullQuery // query ∈ RuleGroupSchedule.extract catalog guide.pattern ∧
      query.mark = mark ∧ query.payload = guide.row.payload } :=
  ⟨⟨guide.row.payload, mark⟩, RuleGroupSchedule.extracted_complete guide.pattern mark catalogued owner, rfl, rfl⟩

theorem wrong_group_rejected (catalog : RuleGroupSchedule.Catalog) (guide : GroupGuide)
    (mark : RuleGroupSchedule.FullMark) (wrong : guide.group ≠ mark.group) :
    (⟨guide.row.payload, mark⟩ : RuleGroupSchedule.FullQuery) ∉ RuleGroupSchedule.extract catalog guide.pattern := by
  intro present
  exact wrong (RuleGroupSchedule.extracted_sound present).2.1

/-- Omitted transparent static carriers have a factored immutable guide
    relation: default method x ACTUAL supplied field/group. The query reads
    this already frozen permission. No marked S-root is representable here. -/
structure CarryStore where
  defaultMethods : List Nat
  staticGroups : List FieldGroup
  deriving DecidableEq, Repr

def carryPermitted (store : CarryStore) (method field group : Nat) : Prop :=
  method ∈ store.defaultMethods ∧ (⟨field, group⟩ : FieldGroup) ∈ store.staticGroups

instance carryPermittedDecidable (store : CarryStore) (method field group : Nat) :
    Decidable (carryPermitted store method field group) := by unfold carryPermitted; infer_instance

def carryRow (method field point : Nat) (payload : RuleGroupSchedule.QueryPayload) : Row :=
  ⟨⟨method, .staticField field⟩, .staticField field, point, .staticCarry, payload⟩

def carryGuide (store : CarryStore) (method field group point : Nat)
    (payload : RuleGroupSchedule.QueryPayload) (_permission : carryPermitted store method field group) : GroupGuide :=
  ⟨carryRow method field point payload, group⟩

theorem carry_is_field_specific (store : CarryStore) (method field group point : Nat)
    (payload : RuleGroupSchedule.QueryPayload) (permission : carryPermitted store method field group) :
    (carryGuide store method field group point payload permission).row.original.kind = .staticField field ∧
      (carryGuide store method field group point payload permission).row.output = .staticField field := ⟨rfl, rfl⟩

theorem guide_is_demand (guide : GroupGuide) : guide.layer = .demand := rfl

theorem empty_supply_no_masks (groups : List Nat) (calls : List CallInput) (slots : List Slot) :
    maskClosure groups calls slots {} = [] := by
  have noStatic : staticSeeds slots ({} : Supplies) = [] := by
    induction slots with
    | nil => rfl
    | cons slot rest ih => cases hk : slot.kind <;> simp [staticSeeds, hk] at ih ⊢ <;> exact ih
  have empty : seedMasks slots ({} : Supplies) = [] := by simp [seedMasks, noStatic]
  rw [maskClosure, empty]
  exact Run1StaticGroupDerivation.solution_empty_without_seed _

theorem no_supply_no_group_guides (groups : List Nat) (calls : List CallInput)
    (slots : List Slot) (rows : List Row) :
    freezeGuides rows (maskClosure groups calls slots {}) = [] := by
  rw [empty_supply_no_masks]
  have emptyRows : rows.flatMap (fun _ => ([] : List GroupGuide)) = [] := by
    induction rows with
    | nil => rfl
    | cons row rest ih => simpa only [List.flatMap_cons, List.nil_append] using ih
  simp only [freezeGuides, List.filter_nil, List.map_nil, emptyRows, List.eraseDups_nil]

namespace Controls

def getter : Slot := ⟨10, .staticField 5⟩
def consumer : Slot := ⟨20, .staticField 5⟩
def helper : Slot := ⟨30, .ordinary 0⟩
def nested : Slot := ⟨40, .ordinary 0⟩
def calls : List CallInput := [⟨consumer, helper, 21, 1⟩, ⟨helper, nested, 31, 2⟩]
def slots : List Slot := [getter, consumer, helper, nested]
def supply : Supplies := ⟨[], [⟨5, 7⟩], [5]⟩
def payload : RuleGroupSchedule.QueryPayload := ⟨90, 8, [], [1, 2]⟩
def getterRow : Row := ⟨getter, .ordinary 1, 11, .pairedSummary, payload⟩
def helperRow : Row := ⟨helper, .ordinary 1, 31, .pairedSummary, payload⟩
def nestedRow : Row := ⟨nested, .ordinary 1, 41, .pairedSummary, payload⟩
def sinkRow : Row := ⟨nested, .ordinary 1, 42, .observationPoint, payload⟩
def writerRow : Row := ⟨helper, .staticField 6, 32, .writerPoint, payload⟩
def rows : List Row := [getterRow, helperRow, nestedRow, sinkRow, writerRow]
def masks : List Mask := maskClosure [7, 8] calls slots supply
def guides : List GroupGuide := freezeGuides rows masks

def controls : List (String × Bool) := [
  ("getter has explicit group guide", decide (⟨getterRow, 7⟩ ∈ guides)),
  ("ordinary helper has explicit group guide", decide (⟨helperRow, 7⟩ ∈ guides)),
  ("nested helper has explicit group guide", decide (⟨nestedRow, 7⟩ ∈ guides)),
  ("internal nonreturning sink has point guide", decide (⟨sinkRow, 7⟩ ∈ guides)),
  ("internal writer has point guide", decide (⟨writerRow, 7⟩ ∈ guides)),
  ("wrong group has no helper guide", decide (⟨helperRow, 8⟩ ∉ guides)),
  ("T in group seven is admitted", (GroupGuide.pattern ⟨helperRow, 7⟩).matches ⟨7, 100⟩),
  ("different full U in group seven is admitted", (GroupGuide.pattern ⟨helperRow, 7⟩).matches ⟨7, 200⟩),
  ("different owner is rejected", !((GroupGuide.pattern ⟨helperRow, 7⟩).matches ⟨8, 100⟩)),
  ("without call-input link helper has no mask", decide ((⟨helper, 7⟩ : Mask) ∉ maskClosure [7] [] slots supply)),
  ("FLOW-only write does not supply a group", decide (maskClosure [7] calls slots ⟨[], [], [5]⟩ = [])),
  ("unseeded call cycle has no group", decide (maskClosure [7] [⟨helper, nested, 31, 1⟩, ⟨nested, helper, 41, 2⟩] slots {} = [])),
  ("field carry is permitted only for supplied group", decide (carryPermitted ⟨[50], [⟨5, 7⟩]⟩ 50 5 7)),
  ("field carry does not grant another field", decide (¬ carryPermitted ⟨[50], [⟨5, 7⟩]⟩ 50 6 7)),
  ("field carry does not grant another group", decide (¬ carryPermitted ⟨[50], [⟨5, 7⟩]⟩ 50 5 8)),
  ("guide layer is DEMAND", decide ((GroupGuide.mk helperRow 7).layer = .demand)),
  ("group guide emits actual full T", decide (emitFromGroupGuide ⟨helperRow, 7⟩ ⟨7, 100⟩ = some ⟨7, 100⟩)),
  ("same group guide emits distinct full U", decide (emitFromGroupGuide ⟨helperRow, 7⟩ ⟨7, 200⟩ = some ⟨7, 200⟩)),
  ("wrong owner emits no mark", decide (emitFromGroupGuide ⟨helperRow, 7⟩ ⟨8, 100⟩ = none)),
  ("backward paired guide has G at both ends", decide ((GroupGuide.mk helperRow 7).backwardPatterns =
    (⟨.ordinary 1, 7⟩, ⟨.ordinary 0, 7⟩)))
]
end Controls
end ApSpec.Run1StaticDemandCoverage
