/-
Current clarification, 20 October 2026. S.C.$/Group(g) is an exact global
mark carrier. S.C.* and abstract class initials do not exist. This model
selects no field-transfer, source, cleaner, order or caller-context changes.
It certifies pass-through of an existing exact marker and its separation
from field supplies. Runtime form coverage and ordinary call routing remain
adapter gates; no global-mark shortcut is introduced by these proofs.
-/
import ApSpec.GroupedRun1Initials

namespace ApSpec.ExactClassMarkers
open ApSpec Run1Requests
set_option autoImplicit false

structure Marker where
  cls : Nat
  group : Nat
  deriving DecidableEq, Repr

def initial (m : Marker) : GroupedRun1Initials.RawInput :=
  GroupedRun1Initials.descriptor (.classExact m.cls m.group)

def current (m : Marker) : AFact :=
  GroupedRun1Initials.entry (.classExact m.cls m.group)

theorem initial_legal (m : Marker) : GroupedRun1Initials.Legal (initial m) :=
  ⟨.classExact m.cls m.group, rfl⟩

theorem initial_exact (m : Marker) :
    initial m = .ap ⟨.at (.cls m.cls), .exact, .conc m.group, .empty⟩ := rfl

theorem current_exact (m : Marker) :
    (current m).fact = concrete (.cls m.cls) .exact m.group := rfl

theorem current_demand (m : Marker) : (current m).demand = true := rfl

theorem no_abstract_class (c : Nat) (tail : Run1InitialShapes.InitialTail) (e : Excl) :
    ¬ GroupedRun1Initials.Legal (.ap ⟨.at (.cls c), tail, .star, e⟩) :=
  GroupedRun1Initials.no_abstract_class c tail e

theorem no_class_star (c g : Nat) (e : Excl) :
    ¬ GroupedRun1Initials.Legal (.ap ⟨.at (.cls c), .star, .conc g, e⟩) :=
  GroupedRun1Initials.no_class_chain c g e

/-- The existing direct class-input adapter already keeps exact geometry.
    A local mark request, field read or static consumer is not a prerequisite. -/
def admit (a : Leaf) : Option PFact := directClass a

theorem direct_preservation (m : Marker) (e : Excl) (layer : Bool) :
    admit ⟨.cls m.cls, .exact, m.group, e, layer⟩ = some (current m).fact := rfl

theorem rejects_class_any (m : Marker) (e : Excl) (layer : Bool) :
    admit ⟨.cls m.cls, .any, m.group, e, layer⟩ = none := rfl

theorem rejects_class_must (m : Marker) (e : Excl) (layer : Bool) :
    admit ⟨.cls m.cls, .must, m.group, e, layer⟩ = none := rfl

theorem field_never_supplies_class (c f g : Nat) (tail : Tail) (e : Excl) (layer : Bool) :
    admit ⟨.field c f, tail, g, e, layer⟩ = none := rfl

theorem root_never_supplies_class (g : Nat) (tail : Tail) (e : Excl) (layer : Bool) :
    admit ⟨.root, tail, g, e, layer⟩ = none := rfl

/-- Class ANY/AT_AND_BELOW is a query, not a class-valued fact or initial. This is the
    existing positional query over the legal exact-marker/field vocabulary. -/
def classAnyMetadata (cls group : Nat) (a : Leaf) : Bool :=
  decide (a.mark = group) && match a.point with
    | .cls c => decide (c = cls)
    | .field c _ => decide (c = cls)
    | _ => false

theorem class_any_keeps_exact_global (m : Marker) (e : Excl) (layer : Bool) :
    classAnyMetadata m.cls m.group ⟨.cls m.cls, .exact, m.group, e, layer⟩ = true := by
  simp [classAnyMetadata]

theorem class_any_keeps_field (c f g : Nat) (tail : Tail) (e : Excl) (layer : Bool) :
    classAnyMetadata c g ⟨.field c f, tail, g, e, layer⟩ = true := by
  simp [classAnyMetadata]

theorem field_query_does_not_become_class (c f g : Nat) (tail : Tail) (e : Excl) (layer : Bool) :
    classAnyMetadata c g ⟨.field c f, tail, g, e, layer⟩ = true ∧
      admit ⟨.field c f, tail, g, e, layer⟩ = none :=
  ⟨class_any_keeps_field c f g tail e layer, rfl⟩

/-- Strict BELOW excludes the exact class marker. It selects field positions
    only; this is distinct from inclusive ANY/AT_AND_BELOW metadata. -/
def strictBelowMetadata (cls group : Nat) (a : Leaf) : Bool :=
  decide (a.mark = group) && match a.point with
    | .field c _ => decide (c = cls)
    | _ => false

theorem strict_below_excludes_global (m : Marker) (e : Excl) (layer : Bool) :
    strictBelowMetadata m.cls m.group ⟨.cls m.cls, .exact, m.group, e, layer⟩ = false := by
  simp [strictBelowMetadata]

theorem strict_below_keeps_field (c f g : Nat) (tail : Tail) (e : Excl) (layer : Bool) :
    strictBelowMetadata c g ⟨.field c f, tail, g, e, layer⟩ = true := by
  simp [strictBelowMetadata]

theorem class_never_supplies_field (m : Marker) (c f : Nat) (e : Excl) (layer : Bool) :
    project (.field c f) ⟨.cls m.cls, .exact, m.group, e, layer⟩ = none := by
  simp [project, slotMatches, broad]

theorem successful_adapter_is_exact (a : Leaf) (j : PFact) (h : admit a = some j) :
    ∃ m : Marker, j = (current m).fact := by
  cases a with
  | mk point tail mark e d =>
    cases point <;> cases tail <;> simp [admit, directClass] at h
    rename_i cls
    exact ⟨⟨cls, mark⟩, h.symm⟩

/-- Ordinary pass-through does not project a class marker to a field. Its
    full class/group payload remains available at the next reached stage. -/
def pass (m : Marker) : Marker := m

def passPath : Nat → Marker → Marker
  | 0, m => m
  | n + 1, m => passPath n (pass m)

theorem pass_path_preserved (n : Nat) (m : Marker) : passPath n m = m := by
  induction n with
  | zero => rfl
  | succ n ih => exact ih

theorem pass_path_fact_preserved (n : Nat) (m : Marker) :
    (current (passPath n m)).fact = (current m).fact := by rw [pass_path_preserved]

/-- Field writes preserve the disjoint marker position. Explicit rule actions
    on the exact class point keep their existing independent semantics. -/
def fieldWrite (_cls _field : Nat) (m : Marker) : Marker := m

theorem field_write_preserves_marker (c f : Nat) (m : Marker) :
    fieldWrite c f m = m := rfl

def controls : List Bool := [
  (GroupedRun1Initials.parse (initial ⟨1, 7⟩)).isSome,
  (GroupedRun1Initials.parse (.ap ⟨.at (.cls 1), .star, .conc 7, .empty⟩)).isNone,
  (GroupedRun1Initials.parse (.ap ⟨.at (.cls 1), .star, .star, .empty⟩)).isNone,
  decide ((current ⟨1, 7⟩).fact.kind = .exact),
  (current ⟨1, 7⟩).demand,
  (admit ⟨.field 1 2, .exact, 7, .empty, false⟩).isNone,
  (project (.field 1 2) ⟨.cls 1, .exact, 7, .empty, false⟩).isNone,
  classAnyMetadata 1 7 ⟨.cls 1, .exact, 7, .empty, false⟩,
  classAnyMetadata 1 7 ⟨.field 1 2, .any, 7, .empty, false⟩,
  !strictBelowMetadata 1 7 ⟨.cls 1, .exact, 7, .empty, false⟩,
  strictBelowMetadata 1 7 ⟨.field 1 2, .any, 7, .empty, false⟩,
  decide (passPath 17 ⟨1, 7⟩ = ⟨1, 7⟩),
  decide (fieldWrite 1 2 ⟨1, 7⟩ = ⟨1, 7⟩)
]

theorem controls_pass : controls.all id = true := by decide

end ApSpec.ExactClassMarkers
