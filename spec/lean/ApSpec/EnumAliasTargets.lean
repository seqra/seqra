/-
  Selected DIRTY HACK only. This model proves the exact alias filter, not
  unrestricted no-false-negative analysis. A mutable enum is a counterexample
  to that wider claim. Direct transfers are outside the alias boundary.
  Metadata lookup is keyed by the field's declaring owner, name and type.
-/
import Std

namespace ApSpec.EnumAliasTargets
set_option autoImplicit false

inductive Base where
  | static
  | local (id : Nat)
  deriving DecidableEq, Repr

structure FieldKey where
  owner : String
  name : String
  type : String
  deriving DecidableEq, Repr

inductive Accessor where
  | static (owner : String)
  | field (key : FieldKey)
  | other (id : Nat)
  deriving DecidableEq, Repr

structure Flags where
  isStatic : Bool
  isEnum : Bool
  deriving DecidableEq, Repr

abbrev Lookup := FieldKey → Option Flags
abbrev Alias := Base × List Accessor

def reject (lookup : Lookup) : Base → List Accessor → Bool
  | .static, .static _ :: .field key :: _ =>
      match lookup key with
      | some flags => flags.isStatic && flags.isEnum
      | none => false
  | _, _ => false

def keep (lookup : Lookup) (alias : Alias) : Bool :=
  !(reject lookup alias.1 alias.2)

def selected (lookup : Lookup) (aliases : List Alias) : List Alias :=
  aliases.filter (keep lookup)

theorem root_exact (lookup : Lookup) (root : String) (key : FieldKey)
    (suffix : List Accessor) :
    reject lookup .static (.static root :: .field key :: suffix) =
      match lookup key with
      | some flags => flags.isStatic && flags.isEnum
      | none => false := by rfl

theorem descendants_equal (lookup : Lookup) (root : String) (key : FieldKey)
    (left right : List Accessor) :
    reject lookup .static (.static root :: .field key :: left) =
      reject lookup .static (.static root :: .field key :: right) := by rfl

theorem declaring_owner_not_static_root (lookup : Lookup) (a b : String)
    (key : FieldKey) (suffix : List Accessor) :
    reject lookup .static (.static a :: .field key :: suffix) =
      reject lookup .static (.static b :: .field key :: suffix) := by rfl

theorem unresolved_kept (lookup : Lookup) (root : String) (key : FieldKey)
    (suffix : List Accessor) (missing : lookup key = none) :
    keep lookup (.static, .static root :: .field key :: suffix) = true := by
  simp [keep, reject, missing]

theorem nonstatic_base_kept (lookup : Lookup) (id : Nat) (path : List Accessor) :
    keep lookup (.local id, path) = true := by rfl

theorem selected_exact (lookup : Lookup) (aliases : List Alias) (alias : Alias) :
    alias ∈ selected lookup aliases ↔ alias ∈ aliases ∧ keep lookup alias = true := by
  simp [selected]

theorem selected_idempotent (lookup : Lookup) (aliases : List Alias) :
    selected lookup (selected lookup aliases) = selected lookup aliases := by
  simp [selected, List.filter_filter]

theorem size_does_not_increase (lookup : Lookup) (aliases : List Alias) :
    (selected lookup aliases).length ≤ aliases.length := by
  exact List.length_filter_le _ _

/-- The feature only filters aliases. Direct reads, writes and rule operations
    remain in the result even when they mention a filtered enum constant. -/
def lowerBoundary (lookup : Lookup) (direct : List Nat) (aliases : List Alias) :=
  (direct, selected lookup aliases)

theorem direct_unchanged (lookup : Lookup) (direct : List Nat) (aliases : List Alias) :
    (lowerBoundary lookup direct aliases).1 = direct := by rfl

namespace Controls
def constant : FieldKey := ⟨"E", "A", "E"⟩
def ordinary : FieldKey := ⟨"E", "BY_PROTO", "java.util.Map"⟩
def typed : FieldKey := ⟨"Holder", "value", "E"⟩
def nonstatic : FieldKey := ⟨"E", "payload", "java.lang.Object"⟩
def lookup : Lookup := fun key =>
  if key = constant then some ⟨true, true⟩
  else if key = ordinary ∨ key = typed then some ⟨true, false⟩
  else if key = nonstatic then some ⟨false, true⟩
  else none
def alias (key : FieldKey) : Alias := (.static, [.static "E", .field key])
def controls : List (String × Bool) := [
  ("constant rejected", reject lookup (alias constant).1 (alias constant).2),
  ("constant descendant rejected", reject lookup .static
    [.static "E$1", .field constant, .other 0]),
  ("ordinary enum static retained", keep lookup (alias ordinary)),
  ("enum-typed field retained", keep lookup (alias typed)),
  ("nonstatic enum flag retained", keep lookup (alias nonstatic)),
  ("unknown owner retained", keep lookup (alias ⟨"Missing", "A", "E"⟩)),
  ("wrong type retained", keep lookup (alias ⟨"E", "A", "Missing"⟩)),
  ("nonstatic base retained", keep lookup (.local 1, (alias constant).2)),
  ("no static accessor retained", keep lookup (.static, [.field constant])),
  ("selected relation shrinks", selected lookup [alias constant, alias ordinary] == [alias ordinary])]

theorem controls_pass : controls.all Prod.snd = true := by decide

/-- Executable witness: mutable enum payload can carry a flow through an alias
    which the selected hack removes. There is no universal soundness theorem. -/
def mutableEnumFlowWitness : List Alias := [alias constant]
theorem mutable_enum_flow_removed :
    mutableEnumFlowWitness ≠ [] ∧ selected lookup mutableEnumFlowWitness = [] := by decide
end Controls
end ApSpec.EnumAliasTargets
