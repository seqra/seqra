/-
  Constructive schedule and rule-group handoff fragment.

  Run numbers are positive. F1 uses grouped concrete-mark candidates at limit
  1; B2 uses full concrete marks at the SAME limit; F3/B4/... have limits
  2/3/... . An ordinary FLOW form is separate from a group wildcard.

  The finite catalog is a CLOSED universe, not the set of marks observed so
  far. A FullMark's group is its catalog-recorded TOP-LEVEL generated-rule
  owner: join child namespaces do not allocate another group. Plain model
  marks need a catalog owner/fallback before this interface.

  The executable group expansion below preserves every catalog member and
  the complete opaque query payload. It is discovery, not evidence of an
  actual mark, satisfiable signed condition, or compatible ND tuple.

  Separate generic finite-path theorems lift LOCAL primitive inclusion or
  refinement simulation through a nondecreasing limit schedule. They do not
  assume whole-closure coverage, and do not prove the real AP/F72/JVM bridge or the new
  pooled candidate-to-seed compiler. Strict growth at the F1/B2 boundary is
  FALSE; any earlier iteration theorem requiring it needs a new bridge.
-/
import Std

namespace ApSpec.RuleGroupSchedule
set_option autoImplicit false

/-! ## Positive, one-based schedule. -/

structure RunId where
  predecessor : Nat
  deriving DecidableEq, Repr

def RunId.number (run : RunId) : Nat := run.predecessor + 1
def RunId.next (run : RunId) : RunId := ⟨run.predecessor + 1⟩

def RunId.ofNat : Nat → Option RunId
  | 0 => none
  | n + 1 => some ⟨n⟩

inductive Direction where
  | forward | backward
  deriving DecidableEq, Repr

inductive Mode where
  | group | full
  deriving DecidableEq, Repr

def direction (run : RunId) : Direction :=
  if run.predecessor % 2 = 0 then .forward else .backward

def mode (run : RunId) : Mode :=
  if run.predecessor = 0 then .group else .full

def limit (run : RunId) : Nat := max 1 run.predecessor

def Direction.opposite : Direction → Direction
  | .forward => .backward
  | .backward => .forward

theorem next_direction_alternates (run : RunId) :
    direction run.next = (direction run).opposite := by
  rcases Nat.mod_two_eq_zero_or_one run.predecessor with hz | ho
  · simp [direction, RunId.next, Direction.opposite, Nat.add_mod, hz]
  · simp [direction, RunId.next, Direction.opposite, Nat.add_mod, ho]

theorem number_positive (run : RunId) : 0 < run.number :=
  Nat.zero_lt_succ _

theorem decode_number (run : RunId) : RunId.ofNat run.number = some run := by
  cases run
  rfl

theorem zero_not_a_run : RunId.ofNat 0 = none := rfl

theorem limit_one_based_formula (run : RunId) :
    limit run = max 1 (run.number - 1) := by
  simp [limit, RunId.number]

theorem limit_positive (run : RunId) : 0 < limit run :=
  Nat.lt_of_lt_of_le (by decide : 0 < 1) (Nat.le_max_left _ _)

theorem limit_mono {earlier later : RunId}
    (h : earlier.number ≤ later.number) : limit earlier ≤ limit later := by
  apply Nat.max_le.mpr
  exact ⟨Nat.le_max_left _ _,
    Nat.le_trans (Nat.le_of_succ_le_succ h) (Nat.le_max_right _ _)⟩

theorem next_limit_nondecreasing (run : RunId) : limit run ≤ limit run.next := by
  apply Nat.max_le.mpr
  exact ⟨Nat.le_max_left _ _, Nat.le_trans (Nat.le_succ _) (Nat.le_max_right _ _)⟩

theorem full_from_run_two (run : RunId) (h : 2 ≤ run.number) : mode run = .full := by
  have hn : run.predecessor ≠ 0 := by
    intro hz
    simp [RunId.number, hz] at h
  simp [mode, hn]

theorem later_limit_formula (run : RunId) (h : 2 ≤ run.number) :
    limit run = run.predecessor := by
  apply Nat.max_eq_right
  exact Nat.le_of_succ_le_succ h

theorem strict_growth_after_run_two (run : RunId) (h : 2 ≤ run.number) :
    limit run < limit run.next := by
  rw [later_limit_formula run h]
  exact Nat.lt_of_lt_of_le (Nat.lt_succ_self _) (Nat.le_max_right _ _)

theorem no_strict_growth_first_boundary : ¬ (limit ⟨0⟩ < limit ⟨1⟩) := by decide

/-! ## Group ownership and complete backward discovery queries. -/

structure FullMark where
  group : Nat
  identity : Nat
  deriving DecidableEq, Repr

abbrev Catalog := List FullMark

inductive SignedLiteral where
  | positive (mark : FullMark)
  | negative (mark : FullMark)
  deriving DecidableEq, Repr

/-- IDs encode full immutable occurrences/geometry; supports are WHOLE support
    identities. This model neither pools ND evidence nor interprets NNF. -/
structure QueryPayload where
  occurrence : Nat
  geometry : Nat
  signedCondition : List SignedLiteral
  support : List Nat
  deriving DecidableEq, Repr

structure GroupDemand where
  payload : QueryPayload
  group : Nat
  deriving DecidableEq, Repr

structure FullQuery where
  payload : QueryPayload
  mark : FullMark
  deriving DecidableEq, Repr

def GroupDemand.matches (demand : GroupDemand) (mark : FullMark) : Bool :=
  demand.group == mark.group

/-- Symbolic membership has no observed-mark parameter. -/
def GroupDemand.contains (demand : GroupDemand) (mark : FullMark) : Prop :=
  demand.group = mark.group

theorem symbolic_matches_exactly (demand : GroupDemand) (mark : FullMark) :
    demand.matches mark = true ↔ demand.contains mark := by
  simp [GroupDemand.matches, GroupDemand.contains]

def abstractQuery (query : FullQuery) : GroupDemand :=
  ⟨query.payload, query.mark.group⟩

/-- A full member can be used symbolically without materializing its siblings. -/
theorem abstraction_covers_member (query : FullQuery) :
    (abstractQuery query).contains query.mark := rfl

/-- Executable finite extraction against the closed catalog, never just the
    full marks observed during F1. Duplicate catalog rows are harmless here. -/
def extract (catalog : Catalog) (demand : GroupDemand) : List FullQuery :=
  (catalog.filter demand.matches).map (fun mark => ⟨demand.payload, mark⟩)

theorem extracted_sound {catalog : Catalog} {demand : GroupDemand} {query : FullQuery}
    (h : query ∈ extract catalog demand) :
    query.mark ∈ catalog ∧ demand.contains query.mark ∧ query.payload = demand.payload := by
  obtain ⟨mark, hmark, heq⟩ := List.mem_map.mp h
  obtain ⟨hcat, hgroup⟩ := List.mem_filter.mp hmark
  cases heq
  exact ⟨hcat, (symbolic_matches_exactly _ _).mp hgroup, rfl⟩

theorem extracted_complete {catalog : Catalog} (demand : GroupDemand) (mark : FullMark)
    (hcat : mark ∈ catalog) (hgroup : demand.contains mark) :
    (⟨demand.payload, mark⟩ : FullQuery) ∈ extract catalog demand := by
  apply List.mem_map.mpr
  exact ⟨mark, List.mem_filter.mpr ⟨hcat, (symbolic_matches_exactly _ _).mpr hgroup⟩, rfl⟩

theorem first_boundary_keeps_full_query (catalog : Catalog) (query : FullQuery)
    (hcat : query.mark ∈ catalog) : query ∈ extract catalog (abstractQuery query) := by
  exact extracted_complete (abstractQuery query) query.mark hcat rfl

/-- A delivered full seed exposes its original catalog membership witness;
    group interest by itself is not a concrete observation. -/
theorem no_fabricated_catalog_member {catalog : Catalog} {demand : GroupDemand}
    {query : FullQuery} (h : query ∈ extract catalog demand) : query.mark ∈ catalog :=
  (extracted_sound h).1

theorem extraction_payload_unchanged {catalog : Catalog} {demand : GroupDemand}
    {query : FullQuery} (h : query ∈ extract catalog demand) :
    query.payload = demand.payload := (extracted_sound h).2.2

/-- Catalog extension can reveal an unobserved full member of the same group. -/
theorem later_catalog_member (catalog : Catalog) (demand : GroupDemand) (mark : FullMark)
    (hgroup : demand.contains mark) :
    (⟨demand.payload, mark⟩ : FullQuery) ∈ extract (catalog ++ [mark]) demand := by
  apply extracted_complete demand mark
  · exact List.mem_append.mpr (Or.inr (by simp))
  · exact hgroup

/-! ## FULL mode preserves ordinary FLOW as a distinct form. -/

inductive CarrierLabel where
  | groupWildcard (group : Nat)
  | fullMark (mark : FullMark)
  | ordinaryFlow
  deriving DecidableEq, Repr

def admitted (run : RunId) : CarrierLabel → Bool
  | .groupWildcard _ => mode run == .group
  | .fullMark _ => mode run == .full
  | .ordinaryFlow => true

theorem full_phase_rejects_group_wildcard (run : RunId) (h : 2 ≤ run.number) (g : Nat) :
    admitted run (.groupWildcard g) = false := by
  simp [admitted, full_from_run_two run h]

theorem full_phase_keeps_full_mark (run : RunId) (h : 2 ≤ run.number) (mark : FullMark) :
    admitted run (.fullMark mark) = true := by
  simp [admitted, full_from_run_two run h]

theorem ordinary_flow_is_not_a_group (run : RunId) :
    admitted run .ordinaryFlow = true := rfl

/-! ## Generalized finite-path limit law, from primitive local obligations. -/

/-- An executable local kernel. Its pointwise inclusion is an explicit bridge
    obligation, NOT an assumption that an entire analysis is already sound. -/
structure Kernel (Atom : Type) where
  step : Nat → Atom → List Atom
  monotone : ∀ {small large : Nat}, small ≤ large → ∀ {a b : Atom},
    b ∈ step small a → b ∈ step large a

/-- A finite derivation with concrete seed and primitive-step witnesses. -/
inductive Derives {Atom : Type} (kernel : Kernel Atom) (bound : Nat)
    (seeds : List Atom) : Atom → Prop
  | seed {atom : Atom} : atom ∈ seeds → Derives kernel bound seeds atom
  | step {before after : Atom} : Derives kernel bound seeds before →
      after ∈ kernel.step bound before → Derives kernel bound seeds after

theorem derivation_limit_lift {Atom : Type} (kernel : Kernel Atom)
    {small large : Nat} (hlimit : small ≤ large) {seeds : List Atom} {atom : Atom}
    (derivation : Derives kernel small seeds atom) : Derives kernel large seeds atom := by
  induction derivation with
  | seed member => exact .seed member
  | step _ primitive ih => exact .step ih (kernel.monotone hlimit primitive)

theorem derivation_seed_lift {Atom : Type} (kernel : Kernel Atom) (bound : Nat)
    {earlier later : List Atom} (hseeds : ∀ a, a ∈ earlier → a ∈ later) {atom : Atom}
    (derivation : Derives kernel bound earlier atom) : Derives kernel bound later atom := by
  induction derivation with
  | seed member => exact .seed (hseeds _ member)
  | step _ primitive ih => exact .step ih primitive

/-- The equal first limit does not invalidate finite local-kernel coverage. -/
theorem equal_first_limit_lift {Atom : Type} (kernel : Kernel Atom)
    {seeds : List Atom} {atom : Atom}
    (derivation : Derives kernel (limit ⟨0⟩) seeds atom) :
    Derives kernel (limit ⟨1⟩) seeds atom := derivation

/-- This is same-direction/same-kernel limit inclusion. It does NOT equate a
    forward kernel to a reversed backward kernel. Their bridge is separate. -/
theorem scheduled_finite_derivation_lift {Atom : Type} (kernel : Kernel Atom)
    {earlier later : RunId} (horder : earlier.number ≤ later.number)
    {seeds : List Atom} {atom : Atom}
    (derivation : Derives kernel (limit earlier) seeds atom) :
    Derives kernel (limit later) seeds atom :=
  derivation_limit_lift kernel (limit_mono horder) derivation

/-- The first group-to-full handoff covers every derivation seeded by a
    particular actual catalog member, including a member never observed in F1.
    The SAME full-query kernel is used on each side of this fragment. -/
theorem group_seed_derivation_coverage (kernel : Kernel FullQuery)
    (catalog : Catalog) (query : FullQuery) (hcat : query.mark ∈ catalog)
    {result : FullQuery}
    (derivation : Derives kernel 1 [query] result) :
    Derives kernel 1 (extract catalog (abstractQuery query)) result := by
  apply derivation_seed_lift kernel 1 (fun a ha => ?_) derivation
  have heq : a = query := List.mem_singleton.mp ha
  subst a
  exact first_boundary_keeps_full_query catalog query hcat

/-! ## Representation-changing refinement from executable local witnesses.

  Actual AP outputs can change geometry as the limit grows. Literal output
  inclusion above is therefore NOT claimed for AP. This relation-based lift
  also works with different state representations and yields a finite target
  path. Seed and single-step correspondences are the explicit local duties.
-/

inductive FinitePath {Atom : Type} (step : Atom → List Atom)
    (seeds : List Atom) : Atom → Type
  | seed {atom : Atom} : atom ∈ seeds → FinitePath step seeds atom
  | next {before after : Atom} : FinitePath step seeds before →
      after ∈ step before → FinitePath step seeds after

structure LocalSimulation {Small Large : Type}
    (smallStep : Small → List Small) (largeStep : Large → List Large)
    (smallSeeds : List Small) (largeSeeds : List Large)
    (related : Small → Large → Prop) where
  seed : ∀ (a : Small), a ∈ smallSeeds →
    { b : Large // b ∈ largeSeeds ∧ related a b }
  primitive : ∀ (a : Small) (b : Large) (next : Small), related a b →
    next ∈ smallStep a → { out : Large // out ∈ largeStep b ∧ related next out }

structure LiftedPath {Small Large : Type} (largeStep : Large → List Large)
    (largeSeeds : List Large) (related : Small → Large → Prop) (source : Small) where
  result : Large
  path : FinitePath largeStep largeSeeds result
  relation : related source result

/-- This computes the target path by induction; it does not choose from a
    proposition asserting that some complete target result must exist. -/
def simulateFinitePath {Small Large : Type}
    {smallStep : Small → List Small} {largeStep : Large → List Large}
    {smallSeeds : List Small} {largeSeeds : List Large}
    {related : Small → Large → Prop}
    (bridge : LocalSimulation smallStep largeStep smallSeeds largeSeeds related)
    {source : Small} (path : FinitePath smallStep smallSeeds source) :
    LiftedPath largeStep largeSeeds related source :=
  match path with
  | .seed member =>
    let target := bridge.seed _ member
    ⟨target.val, .seed target.property.1, target.property.2⟩
  | .next before primitive =>
    let previous := simulateFinitePath bridge before
    let target := bridge.primitive _ previous.result _ previous.relation primitive
    ⟨target.val, .next previous.path target.property.1, target.property.2⟩

/-- Schedule positivity and nondecrease supply the local bridge's numerical
    side conditions, including EQUAL limits. Direction reversal and group to
    full kernel construction must still provide their actual local bridges. -/
def scheduledRefinement {Small Large : Type}
    (smallStep : Nat → Small → List Small) (largeStep : Nat → Large → List Large)
    (smallSeeds : List Small) (largeSeeds : List Large)
    (related : Small → Large → Prop)
    (primitiveBridge : ∀ (small large : Nat), 0 < small → small ≤ large →
      LocalSimulation (smallStep small) (largeStep large) smallSeeds largeSeeds related)
    (earlier later : RunId) (horder : earlier.number ≤ later.number)
    {source : Small} (path : FinitePath (smallStep (limit earlier)) smallSeeds source) :
    LiftedPath (largeStep (limit later)) largeSeeds related source :=
  simulateFinitePath
    (primitiveBridge (limit earlier) (limit later) (limit_positive earlier) (limit_mono horder)) path

/-! An executable local primitive whose bound is meaningful, not an always-
    true coverage predicate. At bound L it may advance n to n+1 only if n<L. -/
def boundedStep (bound atom : Nat) : List Nat :=
  if atom < bound then [atom + 1] else []

def boundedKernel : Kernel Nat where
  step := boundedStep
  monotone := by
    intro small large hlimit a b hstep
    unfold boundedStep at hstep ⊢
    split at hstep
    next hsmall =>
      rw [if_pos (Nat.lt_of_lt_of_le hsmall hlimit)]
      exact hstep
    next => exact False.elim (List.not_mem_nil hstep)

theorem bounded_derivation_result_le {bound atom : Nat}
    (derivation : Derives boundedKernel bound [0] atom) : atom ≤ bound := by
  induction derivation with
  | seed member =>
    have heq : _ = 0 := List.mem_singleton.mp member
    subst heq
    exact Nat.zero_le _
  | @step before after _ primitive _ =>
    change after ∈ boundedStep bound before at primitive
    unfold boundedStep at primitive
    split at primitive
    next hlt =>
      have heq : after = before + 1 := List.mem_singleton.mp primitive
      rw [heq]
      exact Nat.succ_le_of_lt hlt
    next => exact False.elim (List.not_mem_nil primitive)

theorem run_three_two_step_witness : Derives boundedKernel (limit ⟨2⟩) [0] 2 :=
  .step (before := 1) (.step (before := 0) (.seed (by decide)) (by decide)) (by decide)

theorem run_two_cannot_take_second_step :
    ¬ Derives boundedKernel (limit ⟨1⟩) [0] 2 := by
  intro derivation
  have hbound := bounded_derivation_result_le derivation
  exact (by decide : ¬ (2 ≤ limit ⟨1⟩)) hbound

/-! ## Executable positive and negative controls. -/
namespace Controls

def t : FullMark := ⟨7, 10⟩
def u : FullMark := ⟨7, 11⟩
def other : FullMark := ⟨8, 12⟩
def catalog : Catalog := [t,u,other]
def payload : QueryPayload := ⟨31,45,[.positive t,.negative u],[101,102]⟩
def demand : GroupDemand := ⟨payload,7⟩
def qt : FullQuery := ⟨payload,t⟩
def qu : FullQuery := ⟨payload,u⟩

def toySmallStep : Bool → List Bool
  | false => [true]
  | true => []

def toyLargeStep (state : Nat) : List Nat := if state == 10 then [11] else []
def toyRelated (small : Bool) (large : Nat) : Prop := large = if small then 11 else 10

def toyBridge : LocalSimulation toySmallStep toyLargeStep [false] [10] toyRelated where
  seed := by
    intro a member
    have heq : a = false := List.mem_singleton.mp member
    subst a
    exact ⟨10, by decide, rfl⟩
  primitive := by
    intro a b next related primitive
    cases a with
    | false =>
      have hb : b = 10 := related
      subst b
      have hn : next = true := List.mem_singleton.mp primitive
      subst next
      exact ⟨11, by decide, rfl⟩
    | true => exact False.elim (List.not_mem_nil primitive)

def toyPath : FinitePath toySmallStep [false] true :=
  .next (before := false) (.seed (by decide)) (by decide)

def toyFirstBoundary : LiftedPath toyLargeStep [10] toyRelated true :=
  scheduledRefinement (fun _ => toySmallStep) (fun _ => toyLargeStep)
    [false] [10] toyRelated (fun _ _ _ _ => toyBridge) ⟨0⟩ ⟨1⟩ (by decide) toyPath

theorem toy_target_witness : toyFirstBoundary.result = 11 := rfl

def schedule : List (Nat × Direction × Nat × Mode) :=
  (List.range 6).map fun n =>
    let r : RunId := ⟨n⟩
    (r.number,direction r,limit r,mode r)

def controls : List (String × Bool) := [
  ("one-based schedule, repeated limit only at first boundary", schedule == [
    (1,.forward,1,.group),(2,.backward,1,.full),(3,.forward,2,.full),
    (4,.backward,3,.full),(5,.forward,4,.full),(6,.backward,5,.full)]),
  ("run zero is rejected", RunId.ofNat 0 == none),
  ("B2 retains limit one", limit ⟨1⟩ == 1),
  ("all catalog group members are extracted", extract catalog demand == [qt,qu]),
  ("other group is excluded", !(extract catalog demand).contains ⟨payload,other⟩),
  ("unobserved same-group full member is still seeded", (extract catalog demand).contains qu),
  ("observed-only subset loses a full member", !(extract [t] demand).contains qu),
  ("same-group concrete literals remain distinct", t != u),
  ("signed condition and whole support survive", (extract catalog demand).all
    fun q => q.payload == payload),
  ("F1 accepts group carrier", admitted ⟨0⟩ (.groupWildcard 7)),
  ("B2 rejects group carrier", !(admitted ⟨1⟩ (.groupWildcard 7))),
  ("B2 accepts concrete full mark", admitted ⟨1⟩ (.fullMark t)),
  ("B2 ordinary FLOW remains legal", admitted ⟨1⟩ .ordinaryFlow),
  ("F3 ordinary FLOW remains legal", admitted ⟨2⟩ .ordinaryFlow),
  ("group interest alone supplies no catalog member", (extract [] demand).isEmpty),
  ("L1 permits first primitive step", boundedStep (limit ⟨1⟩) 0 == [1]),
  ("B2 L1 rejects second primitive step", (boundedStep (limit ⟨1⟩) 1).isEmpty),
  ("F3 L2 permits second primitive step", boundedStep (limit ⟨2⟩) 1 == [2]),
  ("equal-limit relational bridge computes changed representation", toyFirstBoundary.result == 11),
  ("relational bridge preserves target local step", toyLargeStep 10 == [toyFirstBoundary.result])
]

theorem controls_pass : controls.all Prod.snd = true := by decide

end Controls
end ApSpec.RuleGroupSchedule
