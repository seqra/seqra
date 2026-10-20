/-
  Diagnostic ablation, 20 October 2026. This is intentionally not equivalent
  to the analyzer with static fields enabled. It proves a local effect filter,
  not whole-engine soundness, finding inclusion, runtime or termination.
  No run-0 constructor exists. Class markers are exact and have no field tail.
-/
import Std

namespace ApSpec.Run1StaticFieldAblation
set_option autoImplicit false

inductive Run where
  | first
  | later (offset : Nat)
  deriving DecidableEq, Repr

structure Config where
  disableRun1StaticFields : Bool
  run : Run
  deriving DecidableEq, Repr

structure Field where
  cls : Nat
  name : Nat
  deriving DecidableEq, Repr

/-- All unselected edge data are opaque and survive without projection. -/
structure Payload where
  premise : Nat
  image : Nat
  group : Nat
  demand : Bool
  deriving DecidableEq, Repr

inductive Effect where
  | ordinaryTransfer (data : Payload)
  | ordinaryOverwrite (data : Payload)
  /-- Existing independent marker path. A real-field dependency uses fieldDependent. -/
  | exactClassCarrier (cls : Nat) (data : Payload)
  | fieldReadInjection (field : Field) (data : Payload)
  | fieldWrite (field : Field) (data : Payload)
  | fieldService (field : Field) (data : Payload)
  /-- A static-field premise is required, whatever the conclusion shape. -/
  | fieldDependent (field : Field) (data : Payload)
  deriving DecidableEq, Repr

def active (config : Config) : Bool :=
  config.disableRun1StaticFields && match config.run with
    | .first => true
    | .later _ => false

def isFieldEffect : Effect → Bool
  | .fieldReadInjection _ _ => true
  | .fieldWrite _ _ => true
  | .fieldService _ _ => true
  | .fieldDependent _ _ => true
  | _ => false

def keep (config : Config) (effect : Effect) : Bool :=
  !(active config && isFieldEffect effect)

def select (config : Config) (effects : List Effect) : List Effect :=
  effects.filter (keep config)

theorem default_off (run : Run) : active ⟨false, run⟩ = false := by
  simp [active]

theorem later_inactive (flag : Bool) (offset : Nat) :
    active ⟨flag, .later offset⟩ = false := by simp [active]

theorem enabled_first_active : active ⟨true, .first⟩ = true := rfl

theorem inactive_identity (config : Config) (h : active config = false)
    (effects : List Effect) : select config effects = effects := by
  simp [select, keep, h]

theorem off_identity (run : Run) (effects : List Effect) :
    select ⟨false, run⟩ effects = effects := inactive_identity _ (default_off run) effects

theorem later_identity (flag : Bool) (offset : Nat) (effects : List Effect) :
    select ⟨flag, .later offset⟩ effects = effects :=
  inactive_identity _ (later_inactive flag offset) effects

theorem member_exact (config : Config) (effects : List Effect) (effect : Effect) :
    effect ∈ select config effects ↔ effect ∈ effects ∧ keep config effect = true := by
  simp [select]

/-- A subset result for one supplied candidate effect list, not for a fixed
    point, a report, a later demand or a run with different control state. -/
theorem local_output_subset (config : Config) (effects : List Effect) :
    ∀ effect, effect ∈ select config effects → effect ∈ effects := by
  intro effect h
  exact ((member_exact config effects effect).mp h).1

theorem local_count_nonincrease (config : Config) (effects : List Effect) :
    (select config effects).length ≤ effects.length := List.length_filter_le _ _

theorem idempotent (config : Config) (effects : List Effect) :
    select config (select config effects) = select config effects := by
  simp [select, List.filter_filter]

theorem append_exact (config : Config) (left right : List Effect) :
    select config (left ++ right) = select config left ++ select config right := by
  simp [select]

theorem preserved_nonfield (config : Config) (effect : Effect)
    (h : isFieldEffect effect = false) : keep config effect = true := by
  simp [keep, h]

theorem retained_nonfield_iff (config : Config) (effects : List Effect)
    (effect : Effect) (h : isFieldEffect effect = false) :
    effect ∈ select config effects ↔ effect ∈ effects := by
  simp [member_exact, preserved_nonfield config effect h]

theorem ordinary_transfer_unchanged (config : Config) (effects : List Effect)
    (data : Payload) :
    Effect.ordinaryTransfer data ∈ select config effects ↔
      Effect.ordinaryTransfer data ∈ effects :=
  retained_nonfield_iff _ _ _ rfl

theorem ordinary_overwrite_unchanged (config : Config) (effects : List Effect)
    (data : Payload) :
    Effect.ordinaryOverwrite data ∈ select config effects ↔
      Effect.ordinaryOverwrite data ∈ effects :=
  retained_nonfield_iff _ _ _ rfl

theorem exact_class_carrier_unchanged (config : Config) (effects : List Effect)
    (cls : Nat) (data : Payload) :
    Effect.exactClassCarrier cls data ∈ select config effects ↔
      Effect.exactClassCarrier cls data ∈ effects :=
  retained_nonfield_iff _ _ _ rfl

theorem class_is_not_field (cls : Nat) (data : Payload) :
    isFieldEffect (.exactClassCarrier cls data) = false := rfl

theorem excluded_field (config : Config) (h : active config = true)
    (effects : List Effect) (effect : Effect) (field : isFieldEffect effect = true) :
    effect ∉ select config effects := by
  simp [member_exact, keep, h, field]

theorem no_read_injection (config : Config) (h : active config = true)
    (effects : List Effect) (field : Field) (data : Payload) :
    Effect.fieldReadInjection field data ∉ select config effects :=
  excluded_field _ h _ _ rfl

theorem no_field_write (config : Config) (h : active config = true)
    (effects : List Effect) (field : Field) (data : Payload) :
    Effect.fieldWrite field data ∉ select config effects :=
  excluded_field _ h _ _ rfl

theorem no_field_service_effect (config : Config) (h : active config = true)
    (effects : List Effect) (field : Field) (data : Payload) :
    Effect.fieldService field data ∉ select config effects :=
  excluded_field _ h _ _ rfl

theorem no_field_dependent_effect (config : Config) (h : active config = true)
    (effects : List Effect) (field : Field) (data : Payload) :
    Effect.fieldDependent field data ∉ select config effects :=
  excluded_field _ h _ _ rfl

theorem every_selected_effect_nonfield (config : Config) (h : active config = true)
    (effects : List Effect) :
    ∀ effect, effect ∈ select config effects → isFieldEffect effect = false := by
  intro effect member
  have allowed := ((member_exact config effects effect).mp member).2
  cases he : isFieldEffect effect with
  | false => rfl
  | true => simp [keep, h, he] at allowed

/-- Class preservation is byte-for-byte at this boundary: no change to
    premise, image, group or layer. A class STAR shape cannot be constructed. -/
theorem singleton_class_identity (config : Config) (cls : Nat) (data : Payload) :
    select config [.exactClassCarrier cls data] = [.exactClassCarrier cls data] := by
  simp [select, keep, isFieldEffect]

namespace Controls

def field : Field := ⟨1, 2⟩
def data : Payload := ⟨3, 4, 5, true⟩
def ordinary : List Effect := [.ordinaryTransfer data, .ordinaryOverwrite data]
def classMarker : Effect := .exactClassCarrier 1 data
def fieldEffects : List Effect :=
  [.fieldWrite field data, .fieldReadInjection field data, .fieldService field data,
   .fieldDependent field data]
def allEffects : List Effect := ordinary ++ [classMarker] ++ fieldEffects

def controls : List (String × Bool) := [
  ("off first identity", select ⟨false, .first⟩ allEffects == allEffects),
  ("off later identity", select ⟨false, .later 0⟩ allEffects == allEffects),
  ("enabled later identity", select ⟨true, .later 0⟩ allEffects == allEffects),
  ("enabled far later identity", select ⟨true, .later 99⟩ allEffects == allEffects),
  ("enabled first drops all field effects", select ⟨true, .first⟩ fieldEffects == []),
  ("ordinary transfer and overwrite preserved", select ⟨true, .first⟩ ordinary == ordinary),
  ("independent exact class preserved", select ⟨true, .first⟩ [classMarker] == [classMarker]),
  ("field-dependent conclusion removed", select ⟨true, .first⟩ [.fieldDependent field data] == []),
  ("mixed effects preserve only allowed data", select ⟨true, .first⟩ allEffects == ordinary ++ [classMarker]),
  ("idempotent filter", select ⟨true, .first⟩ (select ⟨true, .first⟩ allEffects) == select ⟨true, .first⟩ allEffects)
]

theorem controls_pass : controls.all Prod.snd = true := by decide

/-- Executable counterexample to an equivalence claim. A real static write
    and read are absent in the selected result. Later refinement is not a
    recovery proof when run-1 failed to produce their demand guidance. -/
theorem intentionally_not_equivalent :
    fieldEffects ≠ [] ∧ select ⟨true, .first⟩ fieldEffects = [] := by decide

end Controls
end ApSpec.Run1StaticFieldAblation
