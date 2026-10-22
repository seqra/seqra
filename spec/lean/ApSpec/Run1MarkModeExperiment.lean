/-
Run-1 GROUP/FULL experiment contract. This model checks flag selection,
runtime mark identity, selected-mark cleaner precision, unchanged run-1
structural policy, and FULL-guide query coverage. It does not prove Kotlin
adapters, whole rule compilation, engine soundness, or a performance bound.
The earlier GROUP erasure models keep their original scope and receipts.
-/
import ApSpec.Run1StaticQueryCompletion

namespace ApSpec.Run1MarkModeExperiment
set_option autoImplicit false

inductive Mode where
  | group | full
  deriving DecidableEq, Repr

structure FullMark where
  group : Nat
  full : Nat
  deriving DecidableEq, Repr

inductive Atom where
  | zero
  | group (id : Nat)
  | full (mark : FullMark)
  deriving DecidableEq, Repr

inductive Checked (α : Type) where
  | ok (value : α)
  | error (reason : String)
  deriving DecidableEq, Repr

def parseFlag : Option String → Checked Mode
  | none | some "GROUP" => .ok .group
  | some "FULL" => .ok .full
  | some _ => .error "Expected GROUP or FULL"

def selectedMode (index : Nat) (run1Mode : Mode) : Mode :=
  if index = 1 then run1Mode else .full

def project (mode : Mode) (mark : FullMark) : Atom :=
  match mode with
  | .group => .group mark.group
  | .full => .full mark

def runtimeAtom (mode : Mode) : Atom → Checked Atom
  | .zero => .ok .zero
  | .full mark => .ok (project mode mark)
  | .group id => match mode with
    | .group => .ok (.group id)
    | .full => .error "A GROUP selector is not a FULL executable mark"

theorem default_is_group : parseFlag none = .ok .group := rfl

theorem run1_uses_selected_mode (mode : Mode) : selectedMode 1 mode = mode := by
  simp [selectedMode]

theorem later_runs_full (index : Nat) (notRun1 : index ≠ 1) (mode : Mode) :
    selectedMode index mode = .full := by simp [selectedMode,notRun1]

theorem full_identity (mark : FullMark) : project .full mark = .full mark := rfl

theorem full_keys_injective (a b : FullMark) :
    project .full a = project .full b ↔ a = b := by simp [project]

theorem group_keys_merge_exactly_owners (a b : FullMark) :
    project .group a = project .group b ↔ a.group = b.group := by simp [project]

theorem zero_unchanged (mode : Mode) : runtimeAtom mode .zero = .ok .zero := rfl

theorem group_not_decoded_to_full (id : Nat) :
    runtimeAtom .full (.group id) = .error "A GROUP selector is not a FULL executable mark" := rfl

theorem runtime_atom_idempotent (mode : Mode) (atom : Atom) (out : Atom)
    (accepted : runtimeAtom mode atom = .ok out) : runtimeAtom mode out = .ok out := by
  cases mode <;> cases atom <;> simp_all [runtimeAtom,project]
  all_goals cases accepted <;> rfl

/-- Mark precision changes; structural run-1 policy does not. These fields
    describe the selected contract, not a proof of the existing Kotlin code. -/
structure StructuralPolicy where
  fieldLimit : Nat
  concreteIsDemand : Bool
  fieldExclusionsEmpty : Bool
  eraseStaticStatic : Bool
  localWritersOnly : Bool
  historicalStaticSuppression : Bool
  exitEntryMarkRemoval : Bool
  deriving DecidableEq, Repr

def structuralPolicy (_mode : Mode) : StructuralPolicy := ⟨1,true,true,true,true,false,false⟩

theorem same_structural_policy (a b : Mode) : structuralPolicy a = structuralPolicy b := rfl

/-- Identity component only. Spatial admission and partial-cleaner rules
    are the same external AP kernel in both modes. -/
def selectedCleanerKills (mode : Mode) (literal actual : FullMark) : Bool :=
  match mode with
  | .group => false
  | .full => decide (literal = actual)

theorem group_cleaner_keeps_wildcard (literal actual : FullMark) :
    selectedCleanerKills .group literal actual = false := rfl

theorem full_cleaner_kills_only_selected (literal actual : FullMark) :
    selectedCleanerKills .full literal actual = true ↔ literal = actual := by
  simp [selectedCleanerKills]

def conditionHolds (mode : Mode) (literal : FullMark) (actuals : List FullMark) : Bool :=
  decide (project mode literal ∈ actuals.map (project mode))

/-- One precision example, not a proof of equivalence of arbitrary conditions. -/
theorem full_condition_has_actual_witness (literal : FullMark) (actuals : List FullMark) :
    conditionHolds .full literal actuals = true ↔ literal ∈ actuals := by
  simp [conditionHolds,project]

namespace Query
open ApSpec

def actualFull : Run1StaticQueryCompletion.Actual → Nat
  | .fieldExact _ _ full | .fieldAny _ _ full | .classExact _ full => full

/-- The mapped GROUP selector is an upper bound on a FULL guide's candidate
    queries. A FULL guide additionally requires its exact full identifier. -/
def oldFullExtra (owner : Run1StaticQueryCompletion.Owner) (shape : Run1StaticQueryCompletion.Guide) (literal : Nat)
    (actual : Run1StaticQueryCompletion.Actual) : Option PFact :=
  if literal = actualFull actual then Run1StaticQueryCompletion.oldExtra owner shape actual else none

theorem full_guide_query_covered (owner : Run1StaticQueryCompletion.Owner) (shape : Run1StaticQueryCompletion.Guide) (literal : Nat)
    (actual : Run1StaticQueryCompletion.Actual) (query : PFact)
    (emitted : oldFullExtra owner shape literal actual = some query) :
    query ∈ Run1StaticQueryCompletion.complete actual := by
  unfold oldFullExtra at emitted
  split at emitted
  · exact Run1StaticQueryCompletion.old_extra_query_in_completion owner actual shape query emitted
  · cases emitted

def fullGuideQueryWitness (owner : Run1StaticQueryCompletion.Owner) (shape : Run1StaticQueryCompletion.Guide) (literal : Nat)
    (actual : Run1StaticQueryCompletion.Actual) (query : PFact)
    (emitted : oldFullExtra owner shape literal actual = some query) :
    {witness : PFact // witness = query ∧ witness ∈ Run1StaticQueryCompletion.complete actual} :=
  ⟨query,rfl,full_guide_query_covered owner shape literal actual query emitted⟩

end Query

namespace Controls
def t : FullMark := ⟨7,101⟩
def u : FullMark := ⟨7,102⟩
def v : FullMark := ⟨8,103⟩
def owner : Run1StaticQueryCompletion.Owner := fun _ => 7
def actual : Run1StaticQueryCompletion.Actual := .fieldAny 2 3 101
def exact := (Run1StaticQueryCompletion.Actual.fieldExact 2 3 101).pattern

def controls : List Bool := [
  decide (parseFlag none = .ok .group),
  decide (parseFlag (some "GROUP") = .ok .group),
  decide (parseFlag (some "FULL") = .ok .full),
  decide (parseFlag (some "bad") = .error "Expected GROUP or FULL"),
  decide (selectedMode 1 .group = .group),
  decide (selectedMode 1 .full = .full),
  decide (selectedMode 2 .group = .full),
  decide (selectedMode 2 .full = .full),
  decide (selectedMode 3 .group = .full),
  decide (selectedMode 3 .full = .full),
  decide (runtimeAtom .full (.full t) = .ok (.full t)),
  decide (runtimeAtom .group (.full t) = .ok (.group 7)),
  decide (runtimeAtom .full (.group 7) = .error "A GROUP selector is not a FULL executable mark"),
  decide (runtimeAtom .group .zero = .ok .zero),
  decide (runtimeAtom .full .zero = .ok .zero),
  decide (project .group t = project .group u),
  decide (project .full t ≠ project .full u),
  decide (project .group t ≠ project .group v),
  decide (structuralPolicy .group = structuralPolicy .full),
  decide ((structuralPolicy .full).historicalStaticSuppression = false),
  decide ((structuralPolicy .full).exitEntryMarkRemoval = false),
  decide (selectedCleanerKills .group t t = false),
  decide (selectedCleanerKills .full t t = true),
  decide (selectedCleanerKills .full t u = false),
  decide (conditionHolds .group u [t] = true),
  decide (conditionHolds .full u [t] = false),
  decide (conditionHolds .full u [t,u] = true),
  decide (Query.oldFullExtra owner (.fieldExact 2 3 7) 101 actual = some exact),
  decide (Query.oldFullExtra owner (.fieldExact 2 3 7) 102 actual = none),
  decide (exact ∈ Run1StaticQueryCompletion.complete actual)
]

theorem controls_pass : controls.all id = true := by decide
end Controls
end ApSpec.Run1MarkModeExperiment
