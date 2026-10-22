/-
Actual local AP projection for scalar run-1 GROUP static relay results.

Current Kotlin keeps EXACT, ANY and ANY_TAINT as separate tail tags even in
DEMAND. The historical AnyTaintEx model erases ANY and ANY_TAINT to Kind.any
and distinguishes must by layer. Therefore applySummaryX alone cannot prove
preservation of the current ANY_TAINT tag. This module exposes the narrow
same-path concrete row of Reference.below/normalize, proves its complete region
preservation, and proves that its erased result equals existing applySummaryX.

Scope: matching base/path/input group; Empty premise/target field exclusions;
canonical concrete ANY field input or exact class input, in DEMAND; concrete
EXACT/ANY/MUST target. The target group can differ from the input group. No
class-wide mark, FLOW target, packed additivity, field cut, pool schedule or
whole-analyzer simulation is assumed. The scalar extraction/graft, receiving
AP manager ownership, field limit and run1Cover adapters remain obligations.
-/
import ApSpec.CurrentSummaryApplication
import ApSpec.Run1Requests

namespace ApSpec.Run1StaticRelayProjection
open ApSpec ApSpec.AnyTaintEx
set_option autoImplicit false

inductive Tail where
  | exact | any | must
  deriving DecidableEq, Repr

inductive InputTail where
  | exact | any
  deriving DecidableEq, Repr

inductive InitialTail where
  | exact | star
  deriving DecidableEq, Repr

structure Scalar where
  base : Nat
  path : List Nat
  tail : Tail
  group : Nat
  exclusion : Excl := .empty
  demand : Bool := true
  deriving DecidableEq, Repr

/-- Exactly the fields used for static region/group admission. Layer is not
    geometry. E remains explicit even though the selected run-1 E is Empty. -/
structure Region where
  base : Nat
  path : List Nat
  tail : Tail
  group : Nat
  exclusion : Excl
  deriving DecidableEq, Repr

def Scalar.region (target : Scalar) : Region :=
  ⟨target.base,target.path,target.tail,target.group,target.exclusion⟩

/-- Erasure is explicit; no theorem recovers the MUST tag from this value. -/
def Tail.erase : Tail → Kind
  | .exact => .exact
  | .any | .must => .any

def InputTail.erase : InputTail → Kind
  | .exact => .exact
  | .any => .any

def InitialTail.erase : InitialTail → Kind
  | .exact => .exact
  | .star => .star .empty

def Scalar.erase (target : Scalar) : XFact :=
  ⟨⟨⟨target.base,target.path,target.tail.erase,.conc target.group⟩,target.demand⟩,target.exclusion⟩

def actualInput (base : Nat) (path : List Nat) (group : Nat) (tail : InputTail) : XFact :=
  ⟨⟨⟨base,path,tail.erase,.conc group⟩,true⟩,.empty⟩

def initial (base : Nat) (path : List Nat) (group : Nat) (tail : InitialTail) : PFact :=
  ⟨base,path,tail.erase,.conc group⟩

/-- Reference.below at the empty relative path, for concrete uncorrelated
    targets only. EXACT uses Empty E; ANY is demand and uses Empty E; MUST
    copies target E and retains its explicit MUST tag. Matching concrete
    markComp picks the target group, including a different group. -/
def belowAtZero (inputDemand : Bool) (target : Scalar) : Scalar :=
  match target.tail with
  | .exact => ⟨target.base,target.path,.exact,target.group,.empty,inputDemand⟩
  | .any => ⟨target.base,target.path,.any,target.group,.empty,true⟩
  | .must => ⟨target.base,target.path,.must,target.group,target.exclusion,inputDemand⟩

/-- Surface normalize does not erase a MUST tag in the DEMAND layer. -/
def surfaceNormalize (target : Scalar) : Scalar :=
  match target.tail with
  | .any => {target with exclusion := .empty, demand := true}
  | .exact | .must => target

/-- Current run-1 cover strips field E without changing its tail or group. -/
def run1Cover (target : Scalar) : Scalar :=
  if target.exclusion.isEmptyB then target
  else {target with exclusion := .empty, demand := true}

/-- Apply target edge, summary layer, surface normal form and GROUP policy in
    that order. The canonical actual input is already in DEMAND. -/
def reapply (target : Scalar) : Scalar :=
  let result := belowAtZero true target
  let result := surfaceNormalize {result with demand := result.demand || target.demand}
  let result := run1Cover result
  {result with demand := true}

/-- Derived closed form, not the definition of reapply. -/
theorem reapply_closed_form (target : Scalar) :
    reapply target = {target with exclusion := .empty, demand := true} := by
  rcases target with ⟨base,path,tail,group,e,demand⟩
  cases tail <;> cases e with
  | univ => simp [reapply,belowAtZero,surfaceNormalize,run1Cover,Excl.empty,Excl.isEmptyB]
  | set xs => cases xs <;> simp [reapply,belowAtZero,surfaceNormalize,run1Cover,Excl.empty,Excl.isEmptyB]

/-- All target admission fields, including the MAY/MUST distinction, survive
    the selected scalar relay. No target-group equality hypothesis is needed. -/
theorem reapply_preserves_region (target : Scalar) (emptyE : target.exclusion = .empty) :
    (reapply target).region = target.region := by
  rw [reapply_closed_form]
  cases target
  simp_all [Scalar.region]

theorem reapply_preserves_target (target : Scalar) (emptyE : target.exclusion = .empty)
    (demand : target.demand = true) : reapply target = target := by
  rw [reapply_closed_form]
  cases target
  simp_all

theorem normalizer_idempotent (target : Scalar) : reapply (reapply target) = reapply target := by
  simp only [reapply_closed_form]

theorem repeated_relay_region_stable (target : Scalar) (emptyE : target.exclusion = .empty) :
    (reapply (reapply target)).region = target.region := by
  rw [normalizer_idempotent]
  exact reapply_preserves_region target emptyE

/-- Equal-path/mark supplies overlap both STAR and EXACT original premises.
    This is spatial+mark admission; it does not manufacture a group supply. -/
theorem canonical_overlap (base : Nat) (path : List Nat) (group : Nat)
    (inputTail : InputTail) (initialTail : InitialTail) :
    overlapX (initial base path group initialTail) .empty
      (actualInput base path group inputTail).af.fact .empty = true := by
  cases inputTail <;> cases initialTail <;>
    simp [overlapX,overlapB,initial,actualInput,InputTail.erase,InitialTail.erase,
      RCore.relate_self,admitsTailB,Excl.admits,Excl.empty]

/-- Existing annotated AP application returns exactly the erased surface
    result with no request, for every target/input group pair and target tail.
    MUST-tag preservation is proved above, not inferred from this erasure. -/
theorem reapply_erases_to_actual_AP (base : Nat) (path : List Nat) (inputGroup : Nat)
    (inputTail : InputTail) (initialTail : InitialTail) (target : Scalar)
    (emptyE : target.exclusion = .empty) :
    applySummaryX (actualInput base path inputGroup inputTail)
      (initial base path inputGroup initialTail) .empty target.erase =
      ⟨[(reapply target).erase],[]⟩ := by
  have inputShape : (actualInput base path inputGroup inputTail).af.fact.kind = .exact ∨
      (actualInput base path inputGroup inputTail).af.fact.kind = .any := by
    cases inputTail <;> simp [actualInput,InputTail.erase]
  have targetShape : target.erase.af.fact.kind = .exact ∨ target.erase.af.fact.kind = .any := by
    cases h : target.tail <;> simp [Scalar.erase,Tail.erase,h]
  have inputMark : ∃ t, (actualInput base path inputGroup inputTail).af.fact.mark = .conc t ∧
      (initial base path inputGroup initialTail).mark = .conc t := ⟨inputGroup,rfl,rfl⟩
  have targetMark : ∃ t, target.erase.af.fact.mark = .conc t := ⟨target.group,rfl⟩
  rw [CurrentSummaryApplication.uncorrelated_member_application _ _ _ _ inputShape targetShape
    inputMark targetMark (canonical_overlap base path inputGroup inputTail initialTail)]
  rw [reapply_closed_form]
  cases target with
  | mk tb tp tt tg te td =>
      cases tt <;> cases td <;>
        simp_all [Scalar.erase,Tail.erase,actualInput,normX,carriesB,concB,Kind.isAny]

/-- Source-specific canonical inputs. These are inputs, not new writer sites. -/
def fieldInput (cls field group : Nat) : XFact :=
  actualInput 1 (Run1Requests.Point.field cls field).path group .any

def classInput (cls group : Nat) : XFact :=
  actualInput 1 (Run1Requests.Point.cls cls).path group .exact

theorem field_STAR_reapplication (cls field inputGroup : Nat) (target : Scalar)
    (emptyE : target.exclusion = .empty) :
    applySummaryX (fieldInput cls field inputGroup)
      (initial 1 (Run1Requests.Point.field cls field).path inputGroup .star) .empty target.erase =
      ⟨[(reapply target).erase],[]⟩ :=
  reapply_erases_to_actual_AP _ _ _ .any .star target emptyE

theorem field_EXACT_reapplication (cls field inputGroup : Nat) (target : Scalar)
    (emptyE : target.exclusion = .empty) :
    applySummaryX (fieldInput cls field inputGroup)
      (initial 1 (Run1Requests.Point.field cls field).path inputGroup .exact) .empty target.erase =
      ⟨[(reapply target).erase],[]⟩ :=
  reapply_erases_to_actual_AP _ _ _ .any .exact target emptyE

theorem class_EXACT_reapplication (cls inputGroup : Nat) (target : Scalar)
    (emptyE : target.exclusion = .empty) :
    applySummaryX (classInput cls inputGroup)
      (initial 1 (Run1Requests.Point.cls cls).path inputGroup .exact) .empty target.erase =
      ⟨[(reapply target).erase],[]⟩ :=
  reapply_erases_to_actual_AP _ _ _ .exact .exact target emptyE

namespace Controls

def exactTarget : Scalar := ⟨1,[4,7],.exact,99,.empty,true⟩
def anyTarget : Scalar := ⟨1,[4,7],.any,99,.empty,true⟩
def mustTarget : Scalar := ⟨1,[4,7],.must,99,.empty,true⟩

theorem all_three_tags_preserved :
    reapply exactTarget = exactTarget ∧ reapply anyTarget = anyTarget ∧
    reapply mustTarget = mustTarget := by decide

theorem erasure_does_not_prove_tag_equality :
    anyTarget.erase = mustTarget.erase ∧ anyTarget.region ≠ mustTarget.region := by decide

theorem nonempty_E_is_outside_region_identity :
    (reapply {mustTarget with exclusion := .set [11]}).exclusion = .empty ∧
    (reapply {mustTarget with exclusion := .set [11]}).region ≠
      ({mustTarget with exclusion := .set [11]} : Scalar).region := by decide

end Controls
end ApSpec.Run1StaticRelayProjection
