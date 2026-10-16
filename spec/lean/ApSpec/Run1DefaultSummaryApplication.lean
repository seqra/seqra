/-
  Ordinary application of the proposed F1 static default.

  The Historical namespace checks the EXISTING applyEdge/applySummary kernel,
  whose demand normalizer changes STAR to ANY. Those laws describe the older
  kernel, not the selected layer-only rule.

  The selected reference below reuses ordinary applySummary for geometry with
  NORMAL tags and attaches the input/operation/summary layers independently.
  It does not invoke the old normalizer after attaching a layer. This generic
  separation, not a special default-call operator, keeps the entire actual
  payload (including tail, spatial E and mark exclusions) unchanged by an
  identity. A DEMAND wrapper around STAR is intentionally admitted here.

  These are local scalar AP laws, not proofs of a packed store/reader adapter,
  default installation, full-key scheduling, handoff or strong returnability.
  Inputs in the selected F1 use Empty E; the general unannotated theorem also
  states precisely what the pre-existing normalizer does on other raw inputs.
-/
import ApSpec.Basic

namespace ApSpec.Run1DefaultSummaryApplication
open ApSpec

/-- Raw identity geometry; no root initial or current root fact is emitted. -/
def rootSTAR (base : Base) : PFact := ⟨base,[],.star Excl.empty,.star⟩

namespace Historical

/-- Existing normalization commutes with subsequently adding a demand tag.
    No assumption that a raw STAR/concrete input is already legal is needed. -/
theorem normalize_then_join (actual : AFact) (edgeDemand : Bool) :
    AFact.norm ⟨actual.norm.fact,actual.norm.demand || edgeDemand⟩ =
      AFact.norm ⟨actual.fact,actual.demand || edgeDemand⟩ := by
  obtain ⟨⟨b,p,k,m⟩,d⟩ := actual
  cases k with
  | exact => rfl
  | any => rfl
  | star e =>
      cases e <;> cases m <;> cases d <;> cases edgeDemand <;> rfl

/-- Applying the ordinary raw STAR identity keeps every actual suffix and
    mark, before applying exactly the existing fact normalizer. -/
theorem root_STAR_edge (actual : AFact) :
    applyEdge actual (rootSTAR actual.fact.base) (rootSTAR actual.fact.base) =
      ⟨[actual.norm],[]⟩ := by
  obtain ⟨⟨b,p,k,m⟩,d⟩ := actual
  cases p with
  | nil =>
      cases k with
      | exact =>
          simp [applyEdge,rootSTAR,relate,dropPrefix,belowCase,admitsTailB,
            Excl.admits,Excl.empty,markGate,markComp]
      | any =>
          simp [applyEdge,rootSTAR,relate,dropPrefix,belowCase,admitsTailB,
            tailExcl,Excl.admits,Excl.isEmptyB,Excl.union,Excl.empty,
            markGate,markComp]
      | star e =>
          cases e <;>
            simp [applyEdge,rootSTAR,relate,dropPrefix,belowCase,admitsTailB,
              tailExcl,Excl.admits,Excl.union,Excl.empty,
              markGate,markComp]
  | cons a rest =>
      simp [applyEdge,rootSTAR,relate,dropPrefix,belowCase,admitsTailB,
        Excl.admits,Excl.empty,markGate,markComp,memB]

/-- Ordinary summary application with raw identity geometry and an independent
    edge layer. Normalize only after specializing to the actual path. -/
theorem root_STAR_summary (actual : AFact) (edgeDemand : Bool) :
    applySummary actual (rootSTAR actual.fact.base)
      ⟨rootSTAR actual.fact.base,edgeDemand⟩ =
      ⟨[AFact.norm ⟨actual.fact,actual.demand || edgeDemand⟩],[]⟩ := by
  simp only [applySummary,root_STAR_edge,List.map_cons,List.map_nil]
  rw [normalize_then_join]

/-- A raw root STAR default preserves the actual AP base/path/mark even when
    the ordinary final normalizer changes its tail to demand ANY (or EXACT
    for the unselected Universe case). -/
theorem root_STAR_summary_geometry (actual : AFact) (edgeDemand : Bool) (output : AFact)
    (present : output ∈ (applySummary actual (rootSTAR actual.fact.base)
      ⟨rootSTAR actual.fact.base,edgeDemand⟩).facts) :
    output.fact.base = actual.fact.base ∧ output.fact.path = actual.fact.path ∧
      output.fact.mark = actual.fact.mark := by
  rw [root_STAR_summary] at present
  simp only [List.mem_singleton] at present
  subst output
  obtain ⟨⟨b,p,k,m⟩,d⟩ := actual
  cases k with
  | exact => exact ⟨rfl,rfl,rfl⟩
  | any => exact ⟨rfl,rfl,rfl⟩
  | star e =>
      cases e <;> cases m <;> cases d <;> cases edgeDemand <;>
        exact ⟨rfl,rfl,rfl⟩

/-- On an already canonical actual fact the result layer is exactly the OR
    of the input layer and the independent summary-edge demand bit. -/
theorem root_STAR_summary_layer (actual : AFact) (edgeDemand : Bool) (output : AFact)
    (canonical : actual.norm = actual)
    (present : output ∈ (applySummary actual (rootSTAR actual.fact.base)
      ⟨rootSTAR actual.fact.base,edgeDemand⟩).facts) :
    output.demand = (actual.demand || edgeDemand) := by
  rw [root_STAR_summary] at present
  simp only [List.mem_singleton] at present
  subst output
  cases edgeDemand with
  | false =>
      simp only [Bool.or_false]
      exact congrArg AFact.demand canonical
  | true =>
      simp only [Bool.or_true]
      obtain ⟨⟨b,p,k,m⟩,d⟩ := actual
      cases k with
      | exact => rfl
      | any => rfl
      | star e => cases e <;> cases m <;> rfl

theorem root_STAR_summary_no_requests (actual : AFact) (edgeDemand : Bool) :
    (applySummary actual (rootSTAR actual.fact.base)
      ⟨rootSTAR actual.fact.base,edgeDemand⟩).reqs = [] := by
  rw [root_STAR_summary]

/-- Exact actual facts remain exact at their actual suffix for either layer. -/
theorem exact_actual (b : Base) (path : List Acc) (mark : MarkA) (inputD edgeD : Bool) :
    applySummary ⟨⟨b,path,.exact,mark⟩,inputD⟩ (rootSTAR b) ⟨rootSTAR b,edgeD⟩ =
      ⟨[⟨⟨b,path,.exact,mark⟩,inputD || edgeD⟩],[]⟩ := by
  rw [root_STAR_summary]
  rfl

/-- A legal NORMAL abstract STAR input specializes first, then becomes demand
    ANY at that SAME actual path when the summary edge carries demand. -/
theorem abstract_STAR_actual (b : Base) (path : List Acc) (edgeD : Bool) :
    applySummary ⟨⟨b,path,.star Excl.empty,.star⟩,false⟩
      (rootSTAR b) ⟨rootSTAR b,edgeD⟩ =
      ⟨[if edgeD then ⟨⟨b,path,.any,.star⟩,true⟩
         else ⟨⟨b,path,.star Excl.empty,.star⟩,false⟩],[]⟩ := by
  rw [root_STAR_summary]
  cases edgeD <;> rfl

namespace Controls

def fieldInput : AFact := ⟨⟨1,[2,3],.exact,.conc 7⟩,false⟩
def canonicalRootANY : AFact := AFact.norm ⟨rootSTAR 1,true⟩

/-- Normalizing the symbolic conclusion BEFORE ordinary application is not
    equivalent: the canonical root ANY relation loses the actual C.f suffix. -/
theorem canonical_root_ANY_loses_field :
    applySummary fieldInput (rootSTAR 1) canonicalRootANY =
      ⟨[⟨⟨1,[],.any,.conc 7⟩,true⟩],[]⟩ := rfl

theorem raw_STAR_retains_field :
    applySummary fieldInput (rootSTAR 1) ⟨rootSTAR 1,true⟩ =
      ⟨[⟨⟨1,[2,3],.exact,.conc 7⟩,true⟩],[]⟩ := rfl

theorem early_root_normalization_changes_application :
    (applySummary fieldInput (rootSTAR 1) canonicalRootANY).facts ≠
      (applySummary fieldInput (rootSTAR 1) ⟨rootSTAR 1,true⟩).facts := by decide

/-- Field suffix, concrete/mx marks, exact/STAR/ANY inputs and both edge layers.
    These executable controls use ordinary summary application throughout. -/
def checks : Bool :=
  let actuals : List AFact :=
    [fieldInput, ⟨⟨1,[2,3],.star Excl.empty,.star⟩,false⟩,
      ⟨⟨1,[2,3],.star Excl.empty,.starEx [9]⟩,false⟩,
      ⟨⟨1,[2,3],.any,.conc 7⟩,false⟩,
      ⟨⟨1,[2,3],.any,.conc 7⟩,true⟩]
  actuals.all fun a => [false,true].all fun d =>
    let result := applySummary a (rootSTAR a.fact.base) ⟨rootSTAR a.fact.base,d⟩
    result.facts == [AFact.norm ⟨a.fact,a.demand || d⟩] && result.reqs.isEmpty

theorem checks_pass : checks = true := by decide

end Controls

#eval Controls.checks
#print axioms normalize_then_join
#print axioms root_STAR_edge
#print axioms root_STAR_summary
#print axioms root_STAR_summary_geometry
#print axioms root_STAR_summary_layer
#print axioms root_STAR_summary_no_requests
#print axioms exact_actual
#print axioms abstract_STAR_actual
#print axioms Controls.canonical_root_ANY_loses_field
#print axioms Controls.early_root_normalization_changes_application
#print axioms Controls.checks_pass

end Historical

/-! Selected layer-only composition of the existing ordinary geometry. -/

/-- Fact payload and edge layer are independent; this is not AFact.norm. -/
structure Layered where
  fact : PFact
  demand : Bool
  deriving DecidableEq, Repr

structure LayeredResults where
  facts : List Layered
  reqs : List Mark
  deriving DecidableEq, Repr

/-- Generic attachment of input, operation and summary layers after ordinary
    geometry application. No fact/tail/E/mark normalization occurs here. -/
def withEdgeLayers (inputD edgeD : Bool) (ordinary : Res) : LayeredResults :=
  ⟨ordinary.facts.map (fun output =>
    ⟨output.fact,(inputD || output.demand) || edgeD⟩),ordinary.reqs⟩

/-- Payload validity is checked independently of the edge layer. This is an
    executable local normal-form test, not an assumed summary correctness law. -/
def CanonicalPayload (fact : PFact) : Prop :=
  AFact.norm ⟨fact,false⟩ = ⟨fact,false⟩

theorem exact_payload (b : Base) (path : List Acc) (mark : MarkA) :
    CanonicalPayload ⟨b,path,.exact,mark⟩ := rfl

theorem ANY_payload (b : Base) (path : List Acc) (mark : MarkA) :
    CanonicalPayload ⟨b,path,.any,mark⟩ := rfl

theorem abstract_STAR_payload (b : Base) (path : List Acc) (e : Excl) :
    CanonicalPayload ⟨b,path,.star e,.star⟩ := rfl

theorem excluded_mark_STAR_payload (b : Base) (path : List Acc) (e : Excl) (mx : List Mark) :
    CanonicalPayload ⟨b,path,.star e,.starEx mx⟩ := rfl

/-- Apply the ordinary STAR identity with NORMAL tags, then attach only the
    independent edge layers. Every actual suffix and the FULL payload survive;
    neither demand input nor demand summary changes STAR into ANY. -/
theorem ordinary_root_STAR_layer_only (actual : Layered) (edgeD : Bool)
    (canonical : CanonicalPayload actual.fact) :
    withEdgeLayers actual.demand edgeD
      (applySummary ⟨actual.fact,false⟩ (rootSTAR actual.fact.base)
        ⟨rootSTAR actual.fact.base,false⟩) =
      ⟨[⟨actual.fact,actual.demand || edgeD⟩],[]⟩ := by
  rw [Historical.root_STAR_summary]
  simp only [Bool.false_or,CanonicalPayload] at canonical ⊢
  rw [canonical]
  simp [withEdgeLayers]

/-- No output field is replaced: equality of the full PFact includes base,
    actual path, tail, spatial E (inside STAR) and complete mark/mx payload. -/
theorem ordinary_identity_preserves_payload (actual : Layered) (edgeD : Bool)
    (canonical : CanonicalPayload actual.fact) (output : Layered)
    (present : output ∈ (withEdgeLayers actual.demand edgeD
      (applySummary ⟨actual.fact,false⟩ (rootSTAR actual.fact.base)
        ⟨rootSTAR actual.fact.base,false⟩)).facts) :
    output.fact = actual.fact ∧ output.demand = (actual.demand || edgeD) := by
  rw [ordinary_root_STAR_layer_only actual edgeD canonical] at present
  simp only [List.mem_singleton] at present
  subst output
  exact ⟨rfl,rfl⟩

/-- Generic operation-level layer monotonicity, including operations whose
    own geometry already requires demand. No post-join normalizer is used. -/
theorem joined_layer_monotone (inputD edgeD : Bool) (ordinary : Res) (output : Layered)
    (present : output ∈ (withEdgeLayers inputD edgeD ordinary).facts)
    (demand : inputD = true ∨ edgeD = true) : output.demand = true := by
  obtain ⟨raw,_,rfl⟩ := List.mem_map.mp present
  rcases demand with h | h <;> simp [h]

theorem attachment_keeps_requests (inputD edgeD : Bool) (ordinary : Res) :
    (withEdgeLayers inputD edgeD ordinary).reqs = ordinary.reqs := rfl

namespace SelectedControls

def checks : Bool :=
  let payloads : List PFact :=
    [⟨1,[2,3],.exact,.conc 7⟩, ⟨1,[2,3],.star Excl.empty,.star⟩,
      ⟨1,[2,3],.star (.set [5]),.starEx [9]⟩, ⟨1,[2,3],.any,.conc 7⟩]
  payloads.all fun payload => [false,true].all fun inputD =>
    [false,true].all fun edgeD =>
      let result := withEdgeLayers inputD edgeD
        (applySummary ⟨payload,false⟩ (rootSTAR payload.base) ⟨rootSTAR payload.base,false⟩)
      result.facts == [⟨payload,inputD || edgeD⟩] && result.reqs.isEmpty

theorem checks_pass : checks = true := by decide

theorem STAR_stays_STAR_at_DEMAND :
    (withEdgeLayers false true
      (applySummary ⟨⟨1,[2,3],.star Excl.empty,.star⟩,false⟩
        (rootSTAR 1) ⟨rootSTAR 1,false⟩)).facts =
      [⟨⟨1,[2,3],.star Excl.empty,.star⟩,true⟩] := rfl

theorem historical_demand_normalization_changes_tail :
    (applySummary ⟨⟨1,[2,3],.star Excl.empty,.star⟩,false⟩
      (rootSTAR 1) ⟨rootSTAR 1,true⟩).facts =
      [⟨⟨1,[2,3],.any,.star⟩,true⟩] := rfl

end SelectedControls

#eval SelectedControls.checks
#print axioms ordinary_root_STAR_layer_only
#print axioms ordinary_identity_preserves_payload
#print axioms joined_layer_monotone
#print axioms attachment_keeps_requests
#print axioms SelectedControls.checks_pass
#print axioms SelectedControls.STAR_stays_STAR_at_DEMAND

end ApSpec.Run1DefaultSummaryApplication
