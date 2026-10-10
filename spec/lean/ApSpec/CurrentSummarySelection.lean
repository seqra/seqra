/-
  Current structural summary demand selection.

  A persisted normal singleton summary needs no separate callee demand.
  Its backward reader may still produce a caller-side DEMAND fact. Keep a
  DEMAND-layer leaf, a leaf with several premises, and a backward zero-premise
  leaf. Keep an empty-premise carrier conservatively; native leaves are nonempty.

  The selector does not inspect tails, marks, exclusions or read-view layers.
  Native R1/S14 eligibility and matching remain reader/store obligations.
  The finite list model proves the local group shortcut and actual annotated
  restriction behavior. It does not prove global no-loss or iteration coverage.
-/
import ApSpec.CurrentMustReverse

namespace ApSpec.CurrentSummarySelection
open ApSpec ApSpec.Reverse ApSpec.Handoff ApSpec.AnyTaintEx
open ApSpec.CurrentMustReverse

inductive Direction where
  | forward | backward
deriving DecidableEq, Repr

structure Entry where
  fact : PFact
  must : Bool
  ex : Excl
deriving DecidableEq, Repr

structure RawLeaf where
  premises : List Entry
  conclusion : XFact
deriving DecidableEq, Repr

def native (j : Entry) (g : XFact) : NativeRecord :=
  ⟨j.fact, j.must, j.ex, g⟩

/-- One gate per premise/layer group. The normal singleton zero case is kept
    only when the producer run is backward. No field or mark test is used. -/
def groupGate (dir : Direction) (js : List Entry) (demand : Bool) : Bool :=
  demand || match js with
    | [j] => dir == .backward && j.fact == zeroFact
    | _ => true

def selectedB (dir : Direction) (raw : RawLeaf) : Bool :=
  groupGate dir raw.premises raw.conclusion.af.demand

def DemandRequired (dir : Direction) (raw : RawLeaf) : Prop :=
  raw.conclusion.af.demand = true ∨ match raw.premises with
    | [j] => dir = .backward ∧ j.fact = zeroFact
    | _ => True

theorem demand_iff (dir : Direction) (raw : RawLeaf) :
    selectedB dir raw = true ↔ DemandRequired dir raw := by
  obtain ⟨js, g⟩ := raw
  cases js with
  | nil => simp [selectedB, groupGate, DemandRequired]
  | cons j js =>
    cases js with
    | nil => simp [selectedB, groupGate, DemandRequired, Bool.or_eq_true, Bool.and_eq_true]
    | cons k ks => simp [selectedB, groupGate, DemandRequired]

theorem omitted_iff (dir : Direction) (raw : RawLeaf) :
    selectedB dir raw = false ↔ ¬ DemandRequired dir raw := by
  rw [← demand_iff]
  cases selectedB dir raw <;> simp

theorem normal_forward_singleton_omitted (j : Entry) (g : XFact)
    (hn : g.af.demand = false) : selectedB .forward ⟨[j], g⟩ = false := by
  simp [selectedB, groupGate, hn]

theorem normal_backward_nonzero_omitted (j : Entry) (g : XFact)
    (hn : g.af.demand = false) (hz : j.fact ≠ zeroFact) :
    selectedB .backward ⟨[j], g⟩ = false := by
  simp [selectedB, groupGate, hn, hz]

theorem empty_selected (dir : Direction) (g : XFact) :
    selectedB dir ⟨[], g⟩ = true := by simp [selectedB, groupGate]

theorem multi_selected (dir : Direction) (j k : Entry) (rest : List Entry) (g : XFact) :
    selectedB dir ⟨j :: k :: rest, g⟩ = true := by simp [selectedB, groupGate]

theorem backward_zero_selected (j : Entry) (g : XFact) (hz : j.fact = zeroFact) :
    selectedB .backward ⟨[j], g⟩ = true := by simp [selectedB, groupGate, hz]

theorem demand_layer_selected (dir : Direction) (js : List Entry) (g : XFact)
    (hd : g.af.demand = true) : selectedB dir ⟨js, g⟩ = true := by
  simp [selectedB, groupGate, hd]

/-- A caller-side demand result of a backward view does not add callee demand. -/
theorem must_view_omitted (j : Entry) (g : XFact) (v : ReadView)
    (hn : g.af.demand = false) (hm : j.must = true)
    (hv : readView (native j g) = some v) :
    selectedB .forward ⟨[j], g⟩ = false ∧ v.2.demand = true :=
  ⟨normal_forward_singleton_omitted j g hn, must_read_demand hm hv⟩

/-- Publication carries the selected raw bit. Actual restriction preserves
    the layer, so the structural selector gives the same bit on the piece. -/
def publicationPlan (dir : Direction) (raw : RawLeaf) (piece : XFact) : Bool × XFact :=
  (selectedB dir raw, piece)

theorem publication_keeps_selection (dir : Direction) (raw : RawLeaf) (piece : XFact) :
    (publicationPlan dir raw piece).1 = selectedB dir raw ∧
    (publicationPlan dir raw piece).2 = piece := ⟨rfl, rfl⟩

theorem selection_layer_only (dir : Direction) (js : List Entry) (a b : XFact)
    (hd : a.af.demand = b.af.demand) :
    selectedB dir ⟨js, a⟩ = selectedB dir ⟨js, b⟩ := by
  simp only [selectedB]
  rw [hd]

theorem restriction_preserves_selection (dir : Direction) (js : List Entry)
    (j : PFact) (jex : Excl) (g piece : XFact) (d : DemandEdge)
    (h : ApSpec.HandoffX.restrictIX j jex g d = some piece) :
    selectedB dir ⟨js, g⟩ = selectedB dir ⟨js, piece⟩ :=
  selection_layer_only dir js g piece (ApSpec.HandoffX.restrictIX_sub_base h).1.symm

/-- Per-leaf reference selection retains order and duplicate leaves. -/
def selectLeaves (dir : Direction) (js : List Entry) (leaves : List XFact) : List XFact :=
  leaves.filter (fun g => selectedB dir ⟨js, g⟩)

/-- A store group has one premise and layer key. Keep or drop the entire group
    after one gate; no tail partition or leaf walk is required. -/
def selectPacked (dir : Direction) (js : List Entry) (dm : Bool)
    (leaves : List XFact) : List XFact :=
  if groupGate dir js dm then leaves else []

private theorem filter_none {α : Type} (p : α → Bool) (xs : List α)
    (h : ∀ x, x ∈ xs → p x = false) : xs.filter p = [] := by
  induction xs with
  | nil => rfl
  | cons x xs ih =>
    rw [List.filter_cons, h x (List.Mem.head xs)]
    simp only [Bool.false_eq_true, ite_false]
    exact ih (fun y hy => h y (List.Mem.tail x hy))

private theorem filter_all {α : Type} (p : α → Bool) (xs : List α)
    (h : ∀ x, x ∈ xs → p x = true) : xs.filter p = xs := by
  induction xs with
  | nil => rfl
  | cons x xs ih =>
    rw [List.filter_cons, h x (List.Mem.head xs)]
    simp only [ite_true]
    exact congrArg (x :: ·) (ih (fun y hy => h y (List.Mem.tail x hy)))

theorem group_selection_equiv (dir : Direction) (js : List Entry) (dm : Bool)
    (leaves : List XFact) (layer : ∀ g, g ∈ leaves → g.af.demand = dm) :
    selectPacked dir js dm leaves = selectLeaves dir js leaves := by
  have same : ∀ g, g ∈ leaves → selectedB dir ⟨js, g⟩ = groupGate dir js dm := by
    intro g hg
    simp only [selectedB]
    rw [layer g hg]
  unfold selectPacked selectLeaves
  cases h : groupGate dir js dm with
  | false =>
    simp only [Bool.false_eq_true, ite_false]
    exact (filter_none _ leaves (fun g hg => (same g hg).trans h)).symm
  | true =>
    simp only [ite_true]
    exact (filter_all _ leaves (fun g hg => (same g hg).trans h)).symm

/-- Local counts cover gate calls and selection leaf visits. They exclude
    equality costs, group construction, trie packing and later consumers. -/
structure SelectionWork where
  calls : Nat
  visits : Nat
deriving DecidableEq, BEq, Repr

def countedLeaves (dir : Direction) (js : List Entry) :
    List XFact → List XFact × SelectionWork
  | [] => ([], ⟨0, 0⟩)
  | g :: gs =>
    let next := countedLeaves dir js gs
    let selected := selectedB dir ⟨js, g⟩
    (if selected then g :: next.1 else next.1,
      ⟨next.2.calls + 1, next.2.visits + 1⟩)

theorem countedLeaves_result (dir : Direction) (js : List Entry) (gs : List XFact) :
    (countedLeaves dir js gs).1 = selectLeaves dir js gs := by
  induction gs with
  | nil => rfl
  | cons g gs ih => simp [countedLeaves, selectLeaves, List.filter_cons, ih]

theorem countedLeaves_work (dir : Direction) (js : List Entry) (gs : List XFact) :
    (countedLeaves dir js gs).2 = ⟨gs.length, gs.length⟩ := by
  induction gs with
  | nil => rfl
  | cons g gs ih => simp [countedLeaves, ih]

def countedPacked (dir : Direction) (js : List Entry) (dm : Bool)
    (gs : List XFact) : List XFact × SelectionWork :=
  (selectPacked dir js dm gs, ⟨1, 0⟩)

theorem countedPacked_result (dir : Direction) (js : List Entry) (dm : Bool)
    (gs : List XFact) : (countedPacked dir js dm gs).1 = selectPacked dir js dm gs := rfl

theorem countedPacked_work (dir : Direction) (js : List Entry) (dm : Bool)
    (gs : List XFact) : (countedPacked dir js dm gs).2 = ⟨1, 0⟩ := rfl

theorem counted_selection_equiv (dir : Direction) (js : List Entry) (dm : Bool)
    (gs : List XFact) (layer : ∀ g, g ∈ gs → g.af.demand = dm) :
    (countedPacked dir js dm gs).1 = (countedLeaves dir js gs).1 := by
  rw [countedPacked_result, countedLeaves_result]
  exact group_selection_equiv dir js dm gs layer

namespace Certificate

def exactEntry : Entry := ⟨⟨3, [], .exact, .conc 9⟩, false, Excl.empty⟩
def mustEntry : Entry := ⟨⟨3, [], .any, .conc 9⟩, true, .set [8]⟩
def outputMust : XFact := ⟨⟨⟨5, [2], .any, .conc 1⟩, false⟩, .set [4]⟩
def outputExact : XFact := ⟨⟨⟨5, [2, 7], .exact, .conc 1⟩, false⟩, Excl.empty⟩
def outputDemand : XFact := ⟨⟨outputMust.af.fact, true⟩, Excl.empty⟩
def zeroEntry : Entry := ⟨zeroFact, false, Excl.empty⟩
def zeroOutput : XFact := ⟨⟨zeroFact, false⟩, Excl.empty⟩

def cases : List Bool := [
  selectedB .forward ⟨[exactEntry], outputMust⟩,
  selectedB .forward ⟨[exactEntry], outputExact⟩,
  selectedB .forward ⟨[mustEntry], outputMust⟩,
  selectedB .forward ⟨[mustEntry], outputExact⟩,
  selectedB .forward ⟨[exactEntry], outputDemand⟩,
  selectedB .forward ⟨[exactEntry, mustEntry], outputExact⟩,
  selectedB .backward ⟨[zeroEntry], zeroOutput⟩,
  selectedB .backward ⟨[exactEntry], outputExact⟩,
  selectedB .backward ⟨[mustEntry], outputExact⟩,
  selectedB .forward ⟨[zeroEntry], zeroOutput⟩,
  selectedB .forward ⟨[], outputExact⟩,
  selectedB .backward ⟨[exactEntry, mustEntry], outputExact⟩]

theorem case_values :
    cases = [false, false, false, false, true, true, true, false, false, false, true, true] := by
  decide

def rawMust : RawLeaf := ⟨[mustEntry], outputMust⟩

theorem published_raw_bit :
    publicationPlan .forward rawMust outputExact = (false, outputExact) ∧
    selectedB .forward ⟨[mustEntry], outputExact⟩ = false := by decide

def restrictedDemand : DemandEdge := ⟨mustEntry.fact, some outputExact.af.fact⟩

theorem actual_restricted_piece :
    ApSpec.HandoffX.restrictIX mustEntry.fact mustEntry.ex outputMust restrictedDemand =
      some outputExact ∧ outputExact.af.demand = outputMust.af.demand := by decide

/-- An actual demand-layer restriction stays demand and remains selected. -/
def restrictedDemandPiece : XFact := ⟨⟨outputExact.af.fact, true⟩, Excl.empty⟩

theorem actual_demand_restriction :
    ApSpec.HandoffX.restrictIX mustEntry.fact mustEntry.ex outputDemand restrictedDemand =
      some restrictedDemandPiece ∧
    selectedB .forward ⟨[mustEntry], restrictedDemandPiece⟩ = true := by decide

/-- The read-view result is demand, while no demand enters this callee. -/
def mustRead : ViewCertificate (native mustEntry outputMust) :=
  mustAnyCertificate 3 5 [] [2] 9 1 (.set [8]) (.set [4])
def incoming : AFact := outputExact.af

theorem caller_demand_without_callee_demand :
    readView (native mustEntry outputMust) = some mustRead.view ∧
    (applySummary incoming mustRead.view.1 mustRead.view.2).facts =
      [⟨⟨3, [], .any, .conc 9⟩, true⟩] ∧
    selectedB .forward rawMust = false := by decide

def normalLeaves : List XFact := [outputMust, outputExact]

theorem packed_values :
    selectPacked .forward [exactEntry] false normalLeaves = [] ∧
    selectPacked .forward [mustEntry] false normalLeaves = [] ∧
    selectPacked .backward [exactEntry] false [outputExact] = [] ∧
    selectPacked .backward [zeroEntry] false normalLeaves = normalLeaves ∧
    selectPacked .forward [exactEntry] true [outputDemand] = [outputDemand] ∧
    selectPacked .forward [exactEntry, mustEntry] false normalLeaves = normalLeaves := by decide

/-- Thirty-three sibling must leaves have distinct paths and the same gate. -/
def workload : List XFact := (List.range 33).map fun n =>
  { outputMust with af := { outputMust.af with
    fact := { outputMust.af.fact with path := [n] } } }

def workWitness : SelectionWork × SelectionWork :=
  ((countedLeaves .forward [mustEntry] workload).2,
    (countedPacked .forward [mustEntry] false workload).2)

theorem work_values : workWitness = (⟨33, 33⟩, ⟨1, 0⟩) := by decide

theorem workload_same_result :
    (countedPacked .forward [mustEntry] false workload).1 =
      (countedLeaves .forward [mustEntry] workload).1 := by decide

end Certificate

end ApSpec.CurrentSummarySelection
