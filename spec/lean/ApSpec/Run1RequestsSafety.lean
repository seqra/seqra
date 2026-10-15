/-
  Constructive finite static-component partition and root-frame refinement.

  Unlike an assumption `root is correct outside H`, frame_identity below is
  DERIVED from executable read/copy, strong-write, cleaner and check primitives.
  Ownership is at the INPUT component; summaries may move it to another field.
  Persisted/native/opposite reads use the same proved relation, not raw root E.

  This is a location/component model. It does not prove that Kotlin's AP matcher,
  alias lowering, finite canonical field fieldUniverse or every JVM effective form
  implements these primitives. ANY/must projection below proves location-set
  partition, not all path-insensitive must/ND provenance or layer theorems.
-/
import ApSpec.Run1Requests

namespace ApSpec.Run1RequestsSafety
open ApSpec ApSpec.Run1Requests

abbrev Field := Nat × Nat

structure Component where
  field : Field
  suffix : List Acc
  mark : Mark
  demand : Bool
  deriving DecidableEq, Repr

def path (p : Component) : List Acc := [2*p.field.1, 2*p.field.2+1] ++ p.suffix

/-- Ordinary location membership keeps E. Only the request join erases E. -/
def tailAt (a : Leaf) (rest : List Acc) : Bool := match a.tail with
  | .exact => rest.isEmpty
  | .any => true
  | .must => a.fieldExclusion.admits rest

def contains (a : Leaf) (p : Component) : Bool :=
  a.mark == p.mark && match a.point with
  | .root => tailAt a (path p)
  | .cls c => c == p.field.1 && tailAt a ((2*p.field.2+1)::p.suffix)
  | .field c f => c == p.field.1 && f == p.field.2 && tailAt a p.suffix
  | .local _ _ => false

/-- A view borrows one immutable supplier and selects exactly one field cell.
    It does not enumerate marks, create a root request, or modify the supplier. -/
def fieldView (a : Leaf) (f : Field) (p : Component) : Bool :=
  decide (p.field = f) && contains a p

def ownedViews (fieldUniverse owned : List Field) (a : Leaf) (p : Component) : Bool :=
  (fieldUniverse.filter (fun f => decide (f ∈ owned))).any (fun f => fieldView a f p)

def residualView (fieldUniverse owned : List Field) (a : Leaf) (p : Component) : Bool :=
  decide (p.field ∈ fieldUniverse) && !decide (p.field ∈ owned) && contains a p

/-- Exhaustive finite partition for every relevant field; no ownership premise
    equivalent to result preservation is assumed. Universe completeness is the
    explicit interpreter/prescan boundary (p.field ∈ fieldUniverse). -/
theorem finite_partition_exact (fieldUniverse owned : List Field) (a : Leaf) (p : Component)
    (hp : p.field ∈ fieldUniverse) :
    (ownedViews fieldUniverse owned a p || residualView fieldUniverse owned a p) = contains a p := by
  have hv : ownedViews fieldUniverse owned a p =
      (decide (p.field ∈ owned) && contains a p) := by
    apply Bool.eq_iff_iff.mpr
    simp only [ownedViews, List.any_eq_true, List.mem_filter, decide_eq_true_eq,
      fieldView, Bool.and_eq_true]
    constructor
    · rintro ⟨f, ⟨_, ho⟩, he, ht⟩
      exact ⟨he ▸ ho, ht⟩
    · rintro ⟨ho, ht⟩
      exact ⟨p.field, ⟨hp,ho⟩, rfl, ht⟩
  rw [hv]
  simp only [residualView, hp, decide_true, Bool.true_and]
  cases decide (p.field ∈ owned) <;> cases contains a p <;> rfl

theorem partition_disjoint (fieldUniverse owned : List Field) (a : Leaf) (p : Component) :
    ¬ (ownedViews fieldUniverse owned a p = true ∧ residualView fieldUniverse owned a p = true) := by
  rintro ⟨ho, hr⟩
  obtain ⟨f, hf, he⟩ := List.any_eq_true.mp ho
  have own := of_decide_eq_true (List.mem_filter.mp hf).2
  have eq := of_decide_eq_true ((Bool.and_eq_true _ _).mp he).1
  have notOwn : decide (p.field ∈ owned) = false := by
    have h := ((Bool.and_eq_true _ _).mp ((Bool.and_eq_true _ _).mp hr).1).2
    simpa using h
  have yes : decide (p.field ∈ owned) = true := by simpa [eq] using (decide_eq_true own)
  rw [yes] at notOwn
  cases notOwn

/-- Broad root/class suppliers do not disappear under the impossible exact-root
    assumption. Their valid ordinary E is retained in this read-time partition. -/
theorem broad_partition (fieldUniverse owned : List Field) (a : Leaf) (p : Component)
    (hp : p.field ∈ fieldUniverse) (broadInput : broad a.tail = true) :
    (ownedViews fieldUniverse owned a p || residualView fieldUniverse owned a p) = contains a p :=
  finite_partition_exact fieldUniverse owned a p hp

/-! ## The untouched frame is derived from executable primitives. -/

inductive Primitive where
  | identity
  | clean (field : Field) (mark : Mark)
  | strongWrite (field : Field)
  | copy (source target : Field)
  | check (field : Field) (mark : Mark)
  | excludedTarget (field : Field) (marks : List Mark)
  deriving DecidableEq, Repr

def footprint : Primitive → List Field
  | .identity => []
  | .clean f _ | .strongWrite f | .check f _ | .excludedTarget f _ => [f]
  | .copy src dst => [src,dst]

/-- Per-input ordinary semantics. Constant zero-GEN outputs are deliberately
    separate; this oracle never invents them once per input origin. -/
def primitive (op : Primitive) (p : Component) : List Component := match op with
  | .identity | .check _ _ => [p]
  | .clean f t => if p.field = f ∧ p.mark = t then [] else [p]
  | .strongWrite f => if p.field = f then [] else [p]
  | .excludedTarget f marks => if p.field = f ∧ p.mark ∈ marks then [] else [p]
  | .copy src dst =>
      (if p.field = dst then [] else [p]) ++
      (if p.field = src then [{p with field := dst}] else [])

theorem primitive_frame (op : Primitive) (p : Component)
    (h : p.field ∉ footprint op) : primitive op p = [p] := by
  cases op with
  | identity => rfl
  | check => rfl
  | clean f t => simp only [footprint, List.mem_singleton] at h; simp [primitive, h]
  | strongWrite f => simp only [footprint, List.mem_singleton] at h; simp [primitive, h]
  | excludedTarget f marks => simp only [footprint, List.mem_singleton] at h; simp [primitive, h]
  | copy src dst =>
    have hs : p.field ≠ src := fun he => h (by simp [footprint, he])
    have ht : p.field ≠ dst := fun he => h (by simp [footprint, he])
    simp [primitive, hs, ht]

def programFootprint (program : List Primitive) : List Field := program.flatMap footprint

def execute : List Primitive → Component → List Component
  | [], p => [p]
  | op::ops, p => (primitive op p).flatMap (execute ops)

theorem frame_identity (program : List Primitive) (p : Component)
    (h : p.field ∉ programFootprint program) : execute program p = [p] := by
  induction program with
  | nil => rfl
  | cons op ops ih =>
    have first : p.field ∉ footprint op := fun hf => h (by simp [programFootprint, hf])
    have rest : p.field ∉ programFootprint ops := fun hf => h (by simp [programFootprint] at *; exact Or.inr hf)
    simp [execute, primitive_frame op p first, ih rest]

/-- Every primitive contributes its own concrete structural footprint. Selected
    static cleaners additionally contribute the conservative wide-mark need. -/
def primitiveForm : Primitive → Form
  | .identity => .zeroGen
  | .strongWrite (c,f) => .code c f
  | .copy (c,f) (d,g) => .both (.code c f) (.code d g)
  | .check (c,f) t => .both (.code c f) (.marked .sink (.field c f) t)
  | .clean (c,f) t => .both (.code c f) (.marked .cleaner .allStatic t)
  | .excludedTarget (c,f) marks => .both (.code c f) (.excludedTarget .allStatic marks)

theorem primitive_footprint_collected (op : Primitive) (f : Field)
    (h : f ∈ footprint op) : Need.structural f.1 f.2 ∈ collect (primitiveForm op) := by
  cases op with
  | identity => simp [footprint] at h
  | clean field mark => cases field; cases f; simpa [footprint, primitiveForm, collect, Prod.mk.injEq] using h
  | strongWrite field => cases field; cases f; simpa [footprint, primitiveForm, collect, Prod.mk.injEq] using h
  | check field mark => cases field; cases f; simpa [footprint, primitiveForm, collect, Prod.mk.injEq] using h
  | excludedTarget field marks =>
    have eq : f = field := by simpa [footprint] using h
    subst f
    cases field
    simp [primitiveForm, collect]
  | copy src dst =>
    rcases src with ⟨c,x⟩; rcases dst with ⟨d,y⟩; rcases f with ⟨e,z⟩
    simpa [footprint, primitiveForm, collect, Prod.mk.injEq] using h

/-- Effective bodies are finite data. A production metadata collector must
    establish this encoding for its own lowered instruction/model vocabulary. -/
def effectiveForms {n : Nat} (bodies : Fin n → List Primitive) (m : Fin n) : List Form :=
  (bodies m).map primitiveForm

theorem reachable_primitive_footprint {n : Nat} (graph : Graph n)
    (bodies : Fin n → List Primitive) (owner method : Fin n)
    (op : Primitive) (field : Field) (reached : Reaches graph method owner)
    (instruction : op ∈ bodies method) (used : field ∈ footprint op) :
    Need.structural field.1 field.2 ∈ plan graph (effectiveForms bodies) owner := by
  apply plan_contains_reachable graph (effectiveForms bodies) owner method _ reached
  apply List.mem_flatMap.mpr
  exact ⟨primitiveForm op, List.mem_map.mpr ⟨op,instruction,rfl⟩,
    primitive_footprint_collected op field used⟩

/-- A finite normal-return trace can unfold recursive calls arbitrarily many
    times. Every executed primitive is tied to an actual effective method body
    and its resolved call path. This is stronger than assuming frame correctness. -/
def RecordedTrace {n : Nat} (graph : Graph n) (bodies : Fin n → List Primitive)
    (owner : Fin n) (trace : List Primitive) : Prop :=
  ∀ op ∈ trace, ∃ method, Reaches graph method owner ∧ op ∈ bodies method

theorem transitive_plan_frame {n : Nat} (graph : Graph n)
    (bodies : Fin n → List Primitive) (owner : Fin n) (trace : List Primitive)
    (recorded : RecordedTrace graph bodies owner trace) (p : Component)
    (outside : Need.structural p.field.1 p.field.2 ∉
      plan graph (effectiveForms bodies) owner) : execute trace p = [p] := by
  apply frame_identity
  intro member
  obtain ⟨op, hin, hf⟩ := List.mem_flatMap.mp member
  obtain ⟨method, path, instruction⟩ := recorded op hin
  exact outside (reachable_primitive_footprint graph bodies owner method op p.field path instruction hf)

def planComposition {n : Nat} (graph : Graph n) (bodies : Fin n → List Primitive)
    (owner : Fin n) (trace : List Primitive) (p : Component) : List Component :=
  if Need.structural p.field.1 p.field.2 ∈ plan graph (effectiveForms bodies) owner
  then execute trace p else [p]

theorem transitive_plan_composition {n : Nat} (graph : Graph n)
    (bodies : Fin n → List Primitive) (owner : Fin n) (trace : List Primitive)
    (recorded : RecordedTrace graph bodies owner trace) (p : Component) :
    planComposition graph bodies owner trace p = execute trace p := by
  unfold planComposition
  split
  · rfl
  · rename_i outside
    exact (transitive_plan_frame graph bodies owner trace recorded p outside).symm

def ownedFields (fieldUniverse : List Field) (program : List Primitive) : List Field :=
  fieldUniverse.filter (fun f => decide (f ∈ programFootprint program))

/-- The special root carries only a derived identity frame. Covered input
    components execute through their normal field analyses; no provisional raw
    root result is used while those analyses are pending. -/
def composed (fieldUniverse : List Field) (program : List Primitive) (p : Component) : List Component :=
  if p.field ∈ ownedFields fieldUniverse program then execute program p else [p]

theorem root_field_composition (fieldUniverse : List Field) (program : List Primitive)
    (p : Component) (complete : p.field ∈ fieldUniverse) :
    composed fieldUniverse program p = execute program p := by
  unfold composed
  split
  · rfl
  · rename_i hn
    have outside : p.field ∉ programFootprint program := by
      intro hf
      exact hn (List.mem_filter.mpr ⟨complete, by simp [hf]⟩)
    exact (frame_identity program p outside).symm

/-- Input ownership cannot be replaced by output ownership: a covered source
    may return at a different field. The complete relation is preserved. -/
theorem relation_composition (fieldUniverse : List Field) (program : List Primitive)
    (input output : Component) (complete : input.field ∈ fieldUniverse) :
    output ∈ composed fieldUniverse program input ↔ output ∈ execute program input := by
  rw [root_field_composition fieldUniverse program input complete]

/-- Native and opposite-direction read views share the already-proved relation.
    This models read-time selection, not mutation of stored summaries or IR. -/
def readRelation (opposite : Bool) (fieldUniverse : List Field) (program : List Primitive)
    (a b : Component) : Prop :=
  if opposite then a ∈ composed fieldUniverse program b else b ∈ composed fieldUniverse program a

def ordinaryRelation (opposite : Bool) (program : List Primitive) (a b : Component) : Prop :=
  if opposite then a ∈ execute program b else b ∈ execute program a

theorem native_and_opposite_read_preserved (opposite : Bool) (fieldUniverse : List Field)
    (program : List Primitive) (a b : Component)
    (ha : a.field ∈ fieldUniverse) (hb : b.field ∈ fieldUniverse) :
    readRelation opposite fieldUniverse program a b ↔ ordinaryRelation opposite program a b := by
  cases opposite <;> simp only [readRelation, ordinaryRelation, Bool.false_eq_true,
    if_false, if_true, root_field_composition fieldUniverse program a ha,
    root_field_composition fieldUniverse program b hb]

/-- The list oracle represents one ACTUALLY RETURNED finite path. A summary
    reader must carry that return fact; an untouched frame cannot fabricate a
    return for a diverging/nonreturning method. No CFG-return theorem is assumed. -/
def returnedComposition (returned : Bool) (fieldUniverse : List Field)
    (program : List Primitive) (p : Component) : List Component :=
  if returned then composed fieldUniverse program p else []

def returnedExecution (returned : Bool) (program : List Primitive) (p : Component) : List Component :=
  if returned then execute program p else []

theorem no_return_no_frame (fieldUniverse : List Field) (program : List Primitive)
    (p : Component) : returnedComposition false fieldUniverse program p = [] := rfl

theorem returned_composition_exact (returned : Bool) (fieldUniverse : List Field)
    (program : List Primitive) (p : Component) (complete : p.field ∈ fieldUniverse) :
    returnedComposition returned fieldUniverse program p = returnedExecution returned program p := by
  cases returned
  · rfl
  · exact root_field_composition fieldUniverse program p complete

def returnedRead (returned opposite : Bool) (fieldUniverse : List Field)
    (program : List Primitive) (a b : Component) : Prop :=
  if opposite then a ∈ returnedComposition returned fieldUniverse program b
  else b ∈ returnedComposition returned fieldUniverse program a

theorem persisted_return_guard_preserved (returned opposite : Bool) (fieldUniverse : List Field)
    (program : List Primitive) (a b : Component)
    (ha : a.field ∈ fieldUniverse) (hb : b.field ∈ fieldUniverse) :
    returnedRead returned opposite fieldUniverse program a b ↔
      (returned = true ∧ ordinaryRelation opposite program a b) := by
  cases returned <;> cases opposite <;>
    simp [returnedRead, returnedComposition, ordinaryRelation,
      root_field_composition fieldUniverse program a ha,
      root_field_composition fieldUniverse program b hb]

theorem cleaner_removes_target (f : Field) (t : Mark) (suffix : List Acc) (d : Bool) :
    execute [.clean f t] ⟨f,suffix,t,d⟩ = [] := by simp [execute, primitive]

theorem cleaner_preserves_sibling (f g : Field) (t : Mark) (suffix : List Acc) (d : Bool)
    (different : g ≠ f) : execute [.clean f t] ⟨g,suffix,t,d⟩ = [⟨g,suffix,t,d⟩] := by
  simp [execute, primitive, different]

/-- The normal rule operation is absent on the special root. Its complement
    frame was proved above from primitive footprint, not from running that rule. -/
def evaluatesRules : Origin → Bool
  | .staticRoot _ => false
  | _ => true

theorem root_does_not_evaluate_rules (owner : Owner) :
    evaluatesRules (.staticRoot owner) = false := rfl

namespace Counterexamples
def f : Field := (3,2)
def g : Field := (3,9)
def target : Component := ⟨f,[],7,false⟩
def sibling : Component := ⟨g,[],7,false⟩

/-- Root skip-rules without composition is NOT enough: the root identity adds
    back the very concrete fact removed by the normal cleaner analysis. -/
def unmasked (program : List Primitive) (p : Component) : List Component := p :: execute program p

theorem raw_root_cleaner_leak :
    target ∈ unmasked [.clean f 7] target ∧ target ∉ execute [.clean f 7] target := by
  decide +kernel

/-- The coarse root strong-write E={class C} rejects ALL fields of C,
    although its normal field f analysis should not remove sibling g. -/
def coarseRootWrite (cls : Nat) (p : Component) : List Component :=
  if p.field.1 = cls then [] else [p]

theorem raw_root_write_loses_sibling :
    sibling ∈ execute [.strongWrite f] sibling ∧
      sibling ∉ coarseRootWrite 3 sibling := by decide +kernel

theorem constructive_frame_repairs_both :
    composed [f,g] [.clean f 7] target = [] ∧
    composed [f,g] [.clean f 7] sibling = [sibling] ∧
    composed [f,g] [.strongWrite f] sibling = [sibling] := by decide +kernel

theorem changed_g_to_f_keeps_original_input_owner :
    ⟨f,[],7,false⟩ ∈ execute [.copy g f] sibling := by decide +kernel
end Counterexamples

#print axioms finite_partition_exact
#print axioms partition_disjoint
#print axioms primitive_frame
#print axioms frame_identity
#print axioms primitive_footprint_collected
#print axioms transitive_plan_frame
#print axioms transitive_plan_composition
#print axioms root_field_composition
#print axioms native_and_opposite_read_preserved
#print axioms persisted_return_guard_preserved
#print axioms no_return_no_frame
#print axioms Counterexamples.raw_root_cleaner_leak
#print axioms Counterexamples.raw_root_write_loses_sibling

end ApSpec.Run1RequestsSafety
