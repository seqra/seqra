/-
  Constructive static-writer discovery at the F1/B2 boundary.
  Scope: closed finite inventories and finite primitive reversal traces.
  An opening is a DEMAND search obligation, not a source fact, summary or
  proof that the write reaches the read. Original full query/context remain
  immutable. Ordinary reversal may change current geometry or full mark.
  The local bridge covers inventory, overlap and one-step reversal only.
  No run-1 observed group, relay summary or exact static initial gates lookup.
-/
import ApSpec.RuleGroupSchedule
namespace ApSpec.Run1GroupStaticHandoff
set_option autoImplicit false
open ApSpec.RuleGroupSchedule
inductive WriterKind where
  | codeStore | staticCopy | ruleSource | modelEffect | aliasStore | broadScope | boundary
  deriving DecidableEq, Repr
structure Writer where
  identity : Nat
  kind : WriterKind
  point : Nat
  coordinate : Nat
  condition : List SignedLiteral
  deriving DecidableEq, Repr
/-- Full geometry, signed condition and support are in query.payload. -/
structure ReadDemand where
  point : Nat
  context : List Nat
  query : FullQuery
  deriving DecidableEq, Repr
structure Cursor where
  point : Nat
  coordinate : Nat
  context : List Nat
  query : FullQuery
  deriving DecidableEq, Repr
structure Need where
  origin : ReadDemand
  current : Cursor
  deriving DecidableEq, Repr
structure Opening where
  need : Need
  writer : Writer
  deriving DecidableEq, Repr
/-- There is no NORMAL, confirmed edge or actual-taint constructor. -/
inductive SearchLayer where
  | demand
  deriving DecidableEq, Repr
def Opening.layer (_ : Opening) : SearchLayer := .demand
def descend (need : Need) (cursor : Cursor) : Need := ⟨need.origin, cursor⟩
/-- Decode every eligible catalog member. The group value is guidance only;
    each returned value is a FULL backward query at its original read. -/
def staticSeed (point coordinate : Nat) (context : List Nat) (query : FullQuery) : Need :=
  ⟨⟨point, context, query⟩, ⟨point, coordinate, context, query⟩⟩

def decodeStaticNeeds (catalog : Catalog) (demand : GroupDemand)
    (point coordinate : Nat) (context : List Nat) : List Need :=
  (extract catalog demand).map (staticSeed point coordinate context)

theorem every_full_group_member_gets_seed (catalog : Catalog) (demand : GroupDemand)
    (point coordinate : Nat) (context : List Nat) (mark : FullMark)
    (hc : mark ∈ catalog) (hg : demand.contains mark) :
    staticSeed point coordinate context ⟨demand.payload, mark⟩ ∈
      decodeStaticNeeds catalog demand point coordinate context := by
  apply List.mem_map.mpr
  exact ⟨⟨demand.payload, mark⟩, extracted_complete demand mark hc hg, rfl⟩

theorem decoded_static_seed_has_catalog_member {catalog : Catalog} {demand : GroupDemand}
    {point coordinate : Nat} {context : List Nat} {need : Need}
    (h : need ∈ decodeStaticNeeds catalog demand point coordinate context) :
    need.origin.query.mark ∈ catalog ∧
      demand.contains need.origin.query.mark ∧
      need.origin.query.payload = demand.payload ∧
      need.current.query = need.origin.query := by
  obtain ⟨query, hq, heq⟩ := List.mem_map.mp h
  cases heq
  have hs := extracted_sound hq
  exact ⟨hs.1, hs.2.1, hs.2.2, rfl⟩

theorem first_static_handoff_keeps_limit_one :
    limit ⟨0⟩ = 1 ∧ limit ⟨1⟩ = 1 ∧ mode ⟨1⟩ = .full := by decide

structure Inventory where
  writers : List Writer
  mayOverlap : Writer → Need → Bool
  reverse : Opening → List Cursor

def openWriters (inventory : Inventory) (need : Need) : List Opening :=
  (inventory.writers.filter (fun writer => inventory.mayOverlap writer need)).map
    (fun writer => ⟨need, writer⟩)
def predecessors (inventory : Inventory) (opening : Opening) : List Need :=
  (inventory.reverse opening).map (descend opening.need)

theorem opened_iff {inventory : Inventory} {need : Need} {opening : Opening} :
    opening ∈ openWriters inventory need ↔
      opening.need = need ∧ opening.writer ∈ inventory.writers ∧
        inventory.mayOverlap opening.writer need = true := by
  constructor
  · intro h
    obtain ⟨writer, hw, heq⟩ := List.mem_map.mp h
    obtain ⟨hi, ho⟩ := List.mem_filter.mp hw
    cases heq
    exact ⟨rfl, hi, ho⟩
  · rintro ⟨hn, hi, ho⟩
    apply List.mem_map.mpr
    exact ⟨opening.writer, List.mem_filter.mpr ⟨hi, ho⟩, by cases opening; simp_all⟩

theorem open_writer_complete (inventory : Inventory) (need : Need) (writer : Writer)
    (hi : writer ∈ inventory.writers)
    (ho : inventory.mayOverlap writer need = true) :
    (⟨need, writer⟩ : Opening) ∈ openWriters inventory need :=
  opened_iff.mpr ⟨rfl, hi, ho⟩

theorem opening_keeps_full_root {inventory : Inventory} {need : Need} {opening : Opening}
    (h : opening ∈ openWriters inventory need) :
    opening.need.origin = need.origin := congrArg Need.origin (opened_iff.mp h).1

theorem opening_keeps_full_current {inventory : Inventory} {need : Need} {opening : Opening}
    (h : opening ∈ openWriters inventory need) :
    opening.need.current = need.current := congrArg Need.current (opened_iff.mp h).1

theorem opening_retains_writer_guard {inventory : Inventory} {need : Need} {opening : Opening}
    (h : opening ∈ openWriters inventory need) :
    opening.writer ∈ inventory.writers ∧
      (⟨need, opening.writer⟩ : Opening).writer.condition = opening.writer.condition :=
  ⟨(opened_iff.mp h).2.1, rfl⟩

theorem all_openings_are_demand (opening : Opening) : opening.layer = .demand := rfl

theorem predecessor_keeps_origin {inventory : Inventory} {opening : Opening} {need : Need}
    (h : need ∈ predecessors inventory opening) : need.origin = opening.need.origin := by
  obtain ⟨cursor, _, heq⟩ := List.mem_map.mp h
  cases heq
  rfl

theorem reverse_cursor_is_exact {inventory : Inventory} {opening : Opening} {need : Need}
    (h : need ∈ predecessors inventory opening) :
    need.current ∈ inventory.reverse opening := by
  obtain ⟨cursor, hc, heq⟩ := List.mem_map.mp h
  cases heq
  exact hc

/-- Fuel bounds search depth, not the AP limit (which is 1 in F1 and B2). -/
def discover (inventory : Inventory) (need : Need) : Nat → List Opening
  | 0 => []
  | fuel + 1 =>
      openWriters inventory need ++
        (openWriters inventory need).flatMap (fun opening =>
          (predecessors inventory opening).flatMap (fun child => discover inventory child fuel))

theorem discover_here (inventory : Inventory) (need : Need) (fuel : Nat)
    {opening : Opening} (h : opening ∈ openWriters inventory need) :
    opening ∈ discover inventory need (fuel + 1) :=
  List.mem_append.mpr (Or.inl h)

theorem discover_child (inventory : Inventory) (need : Need) (fuel : Nat)
    {opening target : Opening} {child : Need}
    (ho : opening ∈ openWriters inventory need)
    (hc : child ∈ predecessors inventory opening)
    (ht : target ∈ discover inventory child fuel) :
    target ∈ discover inventory need (fuel + 1) := by
  apply List.mem_append.mpr
  apply Or.inr
  apply List.mem_flatMap.mpr
  exact ⟨opening, ho, List.mem_flatMap.mpr ⟨child, hc, ht⟩⟩

theorem discovered_root_unchanged (inventory : Inventory) (need : Need) (fuel : Nat)
    {opening : Opening} (h : opening ∈ discover inventory need fuel) :
    opening.need.origin = need.origin := by
  induction fuel generalizing need with
  | zero => simp [discover] at h
  | succ fuel ih =>
    obtain hh | hc := List.mem_append.mp h
    · exact opening_keeps_full_root hh
    · obtain ⟨parent, hp, hchildren⟩ := List.mem_flatMap.mp hc
      obtain ⟨child, hchild, htarget⟩ := List.mem_flatMap.mp hchildren
      exact (ih child htarget).trans
        ((predecessor_keeps_origin hchild).trans (opening_keeps_full_root hp))

/-- Primitive finite semantics, not whole-execution reachability. -/
structure Reference where
  writers : List Writer
  maySupply : Writer → Need → Bool
  reverse : Opening → List Cursor
/-- Local bridge obligations. A true primitive supply cannot be rejected as
    spatially disjoint; ordinary reversed queries must all be discoverable. -/
structure LocalBridge (reference : Reference) (inventory : Inventory) where
  closed : ∀ {writer : Writer}, writer ∈ reference.writers → writer ∈ inventory.writers
  overlap : ∀ {writer : Writer} {need : Need},
    reference.maySupply writer need = true → inventory.mayOverlap writer need = true
  reversal : ∀ {opening : Opening} {cursor : Cursor},
    cursor ∈ reference.reverse opening → cursor ∈ inventory.reverse opening

/-- Recursive copy cycles are permitted; each finite witness remains finite. -/
inductive ReferencePath (reference : Reference) : Need → Type
  | here (need : Need) (writer : Writer)
      (inProgram : writer ∈ reference.writers)
      (supply : reference.maySupply writer need = true) : ReferencePath reference need
  | next (need : Need) (writer : Writer)
      (inProgram : writer ∈ reference.writers)
      (supply : reference.maySupply writer need = true)
      (cursor : Cursor) (reverse : cursor ∈ reference.reverse ⟨need, writer⟩)
      (rest : ReferencePath reference (descend need cursor)) : ReferencePath reference need

def ReferencePath.target {reference : Reference} {need : Need} :
    ReferencePath reference need → Opening
  | .here need writer _ _ => ⟨need, writer⟩
  | .next _ _ _ _ _ _ rest => rest.target

def ReferencePath.depth {reference : Reference} {need : Need} :
    ReferencePath reference need → Nat
  | .here _ _ _ _ => 1
  | .next _ _ _ _ _ _ rest => rest.depth + 1

structure DiscoveryWitness (inventory : Inventory) (need : Need) where
  target : Opening
  fuel : Nat
  found : target ∈ discover inventory need fuel

def compilePath {reference : Reference} {inventory : Inventory}
    (bridge : LocalBridge reference inventory) {need : Need}
    (path : ReferencePath reference need) : DiscoveryWitness inventory need :=
  match path with
  | .here need writer hi hs =>
      ⟨⟨need, writer⟩, 1, discover_here inventory need 0
        (open_writer_complete inventory need writer (bridge.closed hi) (bridge.overlap hs))⟩
  | .next need writer hi hs cursor hr rest =>
      let child := compilePath bridge rest
      ⟨child.target, child.fuel + 1,
        discover_child inventory need child.fuel
          (open_writer_complete inventory need writer (bridge.closed hi) (bridge.overlap hs))
          (List.mem_map.mpr ⟨cursor, bridge.reversal hr, rfl⟩) child.found⟩

theorem compile_target {reference : Reference} {inventory : Inventory}
    (bridge : LocalBridge reference inventory) {need : Need}
    (path : ReferencePath reference need) : (compilePath bridge path).target = path.target := by
  induction path with
  | here => rfl
  | next _ _ _ _ _ _ _ ih => exact ih

theorem compile_depth {reference : Reference} {inventory : Inventory}
    (bridge : LocalBridge reference inventory) {need : Need}
    (path : ReferencePath reference need) : (compilePath bridge path).fuel = path.depth := by
  induction path with
  | here => rfl
  | next _ _ _ _ _ _ _ ih => exact congrArg (fun n => n + 1) ih

theorem every_finite_reference_trace_discovered {reference : Reference} {inventory : Inventory}
    (bridge : LocalBridge reference inventory) {need : Need}
    (path : ReferencePath reference need) :
    path.target ∈ discover inventory need path.depth := by
  have h := (compilePath bridge path).found
  rw [compile_target bridge path, compile_depth bridge path] at h
  exact h

/-- A path in the retained dependency graph keeps every intermediate writer.
    Query deduplication must not discard these parent/reversal edges. -/
inductive CandidatePath (inventory : Inventory) : Need → Type
  | here (need : Need) (writer : Writer)
      (opened : (⟨need, writer⟩ : Opening) ∈ openWriters inventory need) :
      CandidatePath inventory need
  | next (need : Need) (writer : Writer)
      (opened : (⟨need, writer⟩ : Opening) ∈ openWriters inventory need)
      (cursor : Cursor) (reversed : cursor ∈ inventory.reverse ⟨need, writer⟩)
      (rest : CandidatePath inventory (descend need cursor)) : CandidatePath inventory need

def ReferencePath.writerTrace {reference : Reference} {need : Need} :
    ReferencePath reference need → List Writer
  | .here _ writer _ _ => [writer]
  | .next _ writer _ _ _ _ rest => writer :: rest.writerTrace

def CandidatePath.writerTrace {inventory : Inventory} {need : Need} :
    CandidatePath inventory need → List Writer
  | .here _ writer _ => [writer]
  | .next _ writer _ _ _ rest => writer :: rest.writerTrace

def compileGraphPath {reference : Reference} {inventory : Inventory}
    (bridge : LocalBridge reference inventory) {need : Need}
    (path : ReferencePath reference need) : CandidatePath inventory need :=
  match path with
  | .here need writer hi hs =>
      .here need writer
        (open_writer_complete inventory need writer (bridge.closed hi) (bridge.overlap hs))
  | .next need writer hi hs cursor hr rest =>
      .next need writer
        (open_writer_complete inventory need writer (bridge.closed hi) (bridge.overlap hs))
        cursor (bridge.reversal hr) (compileGraphPath bridge rest)

theorem compiled_graph_keeps_all_writers {reference : Reference} {inventory : Inventory}
    (bridge : LocalBridge reference inventory) {need : Need}
    (path : ReferencePath reference need) :
    (compileGraphPath bridge path).writerTrace = path.writerTrace := by
  induction path with
  | here => rfl
  | next _ _ _ _ _ _ _ ih => exact congrArg (List.cons _) ih

theorem compiled_graph_keeps_all_guards {reference : Reference} {inventory : Inventory}
    (bridge : LocalBridge reference inventory) {need : Need}
    (path : ReferencePath reference need) :
    (compileGraphPath bridge path).writerTrace.map Writer.condition =
      path.writerTrace.map Writer.condition :=
  congrArg (List.map Writer.condition) (compiled_graph_keeps_all_writers bridge path)

/-- External ordinary strong-path checks, not the implementation of a JVM
    validator. A speculative writer lookup supplies none of these facts. -/
structure Validation where
  orderedPath : Bool
  compatibleContext : Bool
  noStrongKill : Bool
  originalCondition : Bool
  writerConditions : Bool
  jointSupport : Bool
  realSource : Bool
  deriving DecidableEq, Repr

def Validation.accepts (validation : Validation) : Bool :=
  validation.orderedPath && validation.compatibleContext && validation.noStrongKill &&
    validation.originalCondition && validation.writerConditions &&
    validation.jointSupport && validation.realSource

theorem validation_requires_all (validation : Validation)
    (h : validation.accepts = true) :
    validation.orderedPath = true ∧ validation.compatibleContext = true ∧
    validation.noStrongKill = true ∧ validation.originalCondition = true ∧
    validation.writerConditions = true ∧ validation.jointSupport = true ∧ validation.realSource = true := by
  simpa [Validation.accepts, Bool.and_eq_true, and_assoc] using h

theorem no_source_no_confirmation (validation : Validation)
    (h : validation.realSource = false) : validation.accepts = false := by
  simp [Validation.accepts, h]

def discoverWithObserved (inventory : Inventory) (_observed : List Nat)
    (need : Need) (fuel : Nat) : List Opening := discover inventory need fuel

theorem observed_groups_do_not_gate_discovery (inventory : Inventory)
    (left right : List Nat) (need : Need) (fuel : Nat) :
    discoverWithObserved inventory left need fuel =
      discoverWithObserved inventory right need fuel := rfl

namespace Controls

def markT : FullMark := ⟨7, 101⟩
def markU : FullMark := ⟨7, 102⟩
def payload : QueryPayload := ⟨21, 33, [.positive markU, .negative markT], [61, 62]⟩
def query : FullQuery := ⟨payload, markU⟩
def origin : ReadDemand := ⟨91, [8, 9], query⟩
def cursor : Cursor := ⟨91, 1, [8, 9], query⟩
def need : Need := ⟨origin, cursor⟩
def writer : Writer := ⟨10, .codeStore, 75, 1, []⟩
def guarded : Writer := ⟨11, .ruleSource, 76, 1, [.positive markT]⟩
def missing : Writer := ⟨12, .boundary, 0, 1, []⟩
def disjoint : Writer := ⟨13, .aliasStore, 77, 2, []⟩
def priorCursor : Cursor := ⟨75, 2, [8, 9], ⟨{payload with geometry := 34}, markU⟩⟩
def inventory : Inventory :=
  ⟨[writer, guarded, missing, disjoint],
    (fun w n => w.coordinate == n.current.coordinate),
    (fun opening => if opening.writer.identity = writer.identity then [priorCursor] else [])⟩
def emptyInventory : Inventory := {inventory with writers := []}
def cyclicInventory : Inventory := {inventory with reverse := fun opening =>
  if opening.writer.identity = writer.identity then [priorCursor]
  else if opening.writer.identity = disjoint.identity then [cursor] else []}
def rejected : Validation := ⟨true, true, true, true, true, true, false⟩
def wrongOrder : Validation := ⟨false, true, true, true, true, true, true⟩
def strongKill : Validation := ⟨true, true, false, true, true, true, true⟩
def badGuard : Validation := ⟨true, true, true, true, false, true, true⟩
def accepted : Validation := ⟨true, true, true, true, true, true, true⟩

def controls : List (String × Bool) := [
  ("unobserved U opens writer", (openWriters inventory need).contains ⟨need, writer⟩),
  ("full U stays U", ((openWriters inventory need).headD ⟨need, writer⟩).need.current.query.mark == markU),
  ("same-group T not substituted", ((openWriters inventory need).headD ⟨need, writer⟩).need.current.query.mark != markT),
  ("unobserved boundary opens", (openWriters inventory need).contains ⟨need, missing⟩),
  ("spatial disjoint writer excluded", !((openWriters inventory need).contains ⟨need, disjoint⟩)),
  ("missing inventory loses candidates", (openWriters emptyInventory need).isEmpty),
  ("conditional guard retained", ((openWriters inventory need).getD 1 ⟨need, writer⟩).writer.condition == [.positive markT]),
  ("no source no confirmation", !rejected.accepts),
  ("wrong order no confirmation", !wrongOrder.accepts),
  ("strong kill no confirmation", !strongKill.accepts),
  ("unmet guard no confirmation", !badGuard.accepts),
  ("external validator controls acceptance", accepted.accepts),
  ("no observed group gate", discoverWithObserved inventory [] need 2 == discoverWithObserved inventory [7] need 2),
  ("reversal changes geometry", ((predecessors inventory ⟨need, writer⟩).headD need).current.query.payload.geometry == 34),
  ("reversal preserves root", ((predecessors inventory ⟨need, writer⟩).headD need).origin == origin),
  ("full condition support retained", ((openWriters inventory need).headD ⟨need, writer⟩).need.origin.query.payload == payload),
  ("all openings demand", ((openWriters inventory need).headD ⟨need, writer⟩).layer == .demand),
  ("decoder keeps all same-group members", (decodeStaticNeeds [markT, markU] (abstractQuery query) 91 1 [8, 9]).length == 2),
  ("decoder needs no F1 observation", (decodeStaticNeeds [markT, markU] (abstractQuery query) 91 1 [8, 9]).contains need),
  ("zero fuel no openings", (discover inventory need 0).isEmpty),
  ("transitive copy writer discovered", (discover inventory need 2).contains ⟨descend need priorCursor, disjoint⟩),
  ("finite cycle witness discovered", (discover cyclicInventory need 3).contains ⟨need, writer⟩),
  ("recursive search is fuel bounded", (discover cyclicInventory need 3).length > (discover cyclicInventory need 1).length)
]
theorem controls_pass : controls.all Prod.snd = true := by decide
end Controls
end ApSpec.Run1GroupStaticHandoff
