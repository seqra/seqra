import Std

/-
Original provider composition admission survives rule normalization.
Go OVERRIDE uses the original extra-list presence, even if normalization drops
all extra rows. JVM OVERRIDE filters base rows by ORIGINAL extra conditions,
not folded conditions, and likewise retains original presence. The shared
normalizer is applied once per original row; returned labels preserve its
complete original row. This is an adapter law, not a new override policy.
-/
namespace ApSpec.RuleProviderComposition
set_option autoImplicit false
set_option maxHeartbeats 1000000

inductive Mode where | extend | override | ignore deriving DecidableEq, Repr

structure OriginalRow where
  identity : Nat
  conditionKey : Nat
  actionOrdinals : List Nat
  deriving DecidableEq, Repr

structure NormalizedRow where
  original : OriginalRow
  foldedConditionKey : Nat
  retainedActionOrdinals : List Nat
  deriving DecidableEq, Repr

abbrev Normalizer := OriginalRow → Option NormalizedRow

def KeepsOrigin (normalize : Normalizer) : Prop :=
  ∀ old value, normalize old = some value → value.original = old

def normalizeRows (normalize : Normalizer) (rows : List OriginalRow) : List NormalizedRow := rows.filterMap normalize

def goOriginal (mode : Mode) (base extra : List OriginalRow) : List OriginalRow :=
  match mode with
  | .extend => base ++ extra
  | .ignore => base
  | .override => if extra.isEmpty then base else extra

/-- The attached presence belongs to THIS query. It is not a mutable global
    signature cache or a second call to a custom rule provider. -/
def goNormalized (mode : Mode) (originalExtraPresent : Bool)
    (base extra : List NormalizedRow) : List NormalizedRow :=
  match mode with
  | .extend => base ++ extra
  | .ignore => base
  | .override => if originalExtraPresent then extra else base

def jvmOriginal (mode : Mode) (base extra : List OriginalRow) : List OriginalRow :=
  match mode with
  | .extend => base ++ extra
  | .ignore => base
  | .override => if extra.isEmpty then base else
      extra ++ base.filter (fun row => decide (row.conditionKey ∉ extra.map OriginalRow.conditionKey))

def jvmNormalized (mode : Mode) (originalExtraPresent : Bool) (originalExtraConditions : List Nat)
    (base extra : List NormalizedRow) : List NormalizedRow :=
  match mode with
  | .extend => base ++ extra
  | .ignore => base
  | .override => if originalExtraPresent then
      extra ++ base.filter (fun row => decide (row.original.conditionKey ∉ originalExtraConditions))
    else base

theorem normalize_append (normalize : Normalizer) (base extra : List OriginalRow) :
    normalizeRows normalize (base ++ extra) = normalizeRows normalize base ++ normalizeRows normalize extra := by
  simp [normalizeRows,List.filterMap_append]

theorem normalize_filter_original (normalize : Normalizer) (origin : KeepsOrigin normalize)
    (predicate : Nat → Bool) (rows : List OriginalRow) :
    normalizeRows normalize (rows.filter (fun row => predicate row.conditionKey)) =
      (normalizeRows normalize rows).filter (fun row => predicate row.original.conditionKey) := by
  induction rows with
  | nil => rfl
  | cons row rest ih =>
      cases result : normalize row with
      | none =>
          by_cases selected : predicate row.conditionKey = true <;>
            simp [normalizeRows,result,selected] at ih ⊢ <;> exact ih
      | some value =>
          have kept := origin row value result
          by_cases selected : predicate row.conditionKey = true <;>
            simp [normalizeRows,result,selected,kept] at ih ⊢ <;> exact ih

theorem go_admission_preserved (normalize : Normalizer) (mode : Mode) (base extra : List OriginalRow) :
    normalizeRows normalize (goOriginal mode base extra) =
      goNormalized mode (!extra.isEmpty) (normalizeRows normalize base) (normalizeRows normalize extra) := by
  cases mode with
  | extend => exact normalize_append normalize base extra
  | ignore => rfl
  | override =>
      cases extra <;> simp [goOriginal,goNormalized,normalizeRows]

theorem jvm_admission_preserved (normalize : Normalizer) (origin : KeepsOrigin normalize)
    (mode : Mode) (base extra : List OriginalRow) :
    normalizeRows normalize (jvmOriginal mode base extra) =
      jvmNormalized mode (!extra.isEmpty) (extra.map OriginalRow.conditionKey)
        (normalizeRows normalize base) (normalizeRows normalize extra) := by
  cases mode with
  | extend => exact normalize_append normalize base extra
  | ignore => rfl
  | override =>
      cases empty : extra.isEmpty
      · simp only [jvmOriginal,jvmNormalized,empty,Bool.false_eq_true,↓reduceIte,Bool.not_false]
        rw [normalize_append]
        exact congrArg (fun rows => normalizeRows normalize extra ++ rows)
          (normalize_filter_original normalize origin
            (fun key => decide (key ∉ extra.map OriginalRow.conditionKey)) base)
      · have absent : extra = [] := List.isEmpty_iff.mp empty
        simp [jvmOriginal,jvmNormalized,absent]

/-- A whole nested query carries the original presence, not normalized size. -/
structure QueryResult where
  rows : List NormalizedRow
  originalPresence : Bool
  deriving DecidableEq, Repr

def originalPresence (mode : Mode) (base extra : Bool) : Bool :=
  match mode with
  | .ignore => base
  | .extend | .override => base || extra

def combineGoQuery (mode : Mode) (base extra : QueryResult) : QueryResult :=
  ⟨goNormalized mode extra.originalPresence base.rows extra.rows,
    originalPresence mode base.originalPresence extra.originalPresence⟩

theorem original_presence_combines (mode : Mode) (base extra : List OriginalRow) :
    (!(goOriginal mode base extra).isEmpty) = originalPresence mode (!base.isEmpty) (!extra.isEmpty) := by
  cases mode <;> cases base <;> cases extra <;> rfl

theorem query_envelope_preserves_original_presence (normalize : Normalizer) (mode : Mode)
    (base extra : List OriginalRow) :
    combineGoQuery mode ⟨normalizeRows normalize base,!base.isEmpty⟩ ⟨normalizeRows normalize extra,!extra.isEmpty⟩ =
      ⟨normalizeRows normalize (goOriginal mode base extra),!(goOriginal mode base extra).isEmpty⟩ := by
  simp only [combineGoQuery]
  rw [← go_admission_preserved normalize mode base extra]
  cases mode <;> cases base <;> cases extra <;> rfl

/- Configuration metadata is not a new semantic equality key. The runtime
   adapter must implement key equality on the original configuration fields;
   ordered list positions still carry distinct occurrence labels. This is a
   local key-projection model, not a proof of Kotlin generated bytecode. -/
namespace MetadataIdentity

structure SemanticKey where
  role : Nat
  target : Nat
  condition : Nat
  actions : List Nat
  info : Option Nat
  serializedId : Option Nat
  sinkId : Option Nat
  sinkMeta : Option Nat
  deriving DecidableEq, Repr

structure Labels where
  ordinal : Nat
  actionOrdinals : List Nat
  original : SemanticKey
  deriving DecidableEq, Repr

structure Row where
  key : SemanticKey
  labels : Option Labels
  deriving DecidableEq, Repr

def semanticEq (left right : Row) : Bool := decide (left.key = right.key)
def semanticHash (hash : SemanticKey → Nat) (row : Row) : Nat := hash row.key

def labelFrom (ordinal : Nat) : List SemanticKey → List Row
  | [] => []
  | key :: rest => ⟨key,some ⟨ordinal,[],key⟩⟩ :: labelFrom (ordinal + 1) rest

theorem equality_ignores_labels (key : SemanticKey) (left right : Option Labels) :
    semanticEq ⟨key,left⟩ ⟨key,right⟩ = true := by simp [semanticEq]

theorem hash_ignores_labels (hash : SemanticKey → Nat) (key : SemanticKey)
    (left right : Option Labels) :
    semanticHash hash ⟨key,left⟩ = semanticHash hash ⟨key,right⟩ := rfl

theorem semantic_fields_stay_distinct (left right : Row) (different : left.key ≠ right.key) :
    semanticEq left right = false := by simp [semanticEq,different]

theorem labeled_key_stream (ordinal : Nat) (keys : List SemanticKey) :
    (labelFrom ordinal keys).map Row.key = keys := by
  induction keys generalizing ordinal with
  | nil => rfl
  | cons key rest ih => simp [labelFrom,ih]

theorem occurrence_count_preserved (ordinal : Nat) (keys : List SemanticKey) :
    (labelFrom ordinal keys).length = keys.length := by
  induction keys generalizing ordinal with
  | nil => rfl
  | cons key rest ih => simp [labelFrom,ih]

/-- Any legacy equality-key observer, including a key-set or multiset
    consumer, sees the same ordered keys. It must not inspect new labels. -/
theorem legacy_key_observer_preserved {Output : Type} (observe : List SemanticKey → Output)
    (ordinal : Nat) (keys : List SemanticKey) :
    observe ((labelFrom ordinal keys).map Row.key) = observe keys := by
  rw [labeled_key_stream]

def sample : SemanticKey := ⟨1,2,3,[4],none,some 5,some 6,some 7⟩
def first : Row := ⟨sample,some ⟨0,[4],sample⟩⟩
def second : Row := ⟨sample,some ⟨1,[4],sample⟩⟩
def controls : List Bool := [
  semanticEq ⟨sample,none⟩ first,
  semanticEq first second,
  decide (first ≠ second),
  decide ((labelFrom 0 [sample,sample]).length = 2),
  decide ((labelFrom 0 [sample,sample]).map Row.key = [sample,sample]),
  decide (semanticHash SemanticKey.target first = semanticHash SemanticKey.target second),
  !semanticEq first ⟨{sample with actions := [99]},first.labels⟩
]
theorem controls_pass : controls.all id = true := by decide
end MetadataIdentity

namespace Controls

def baseA : OriginalRow := ⟨1,10,[0,2]⟩
def baseB : OriginalRow := ⟨2,20,[1]⟩
def extraA : OriginalRow := ⟨3,10,[4]⟩
def extraFolded : OriginalRow := ⟨4,99,[7,9]⟩
def normalize (row : OriginalRow) : Option NormalizedRow :=
  if row.identity = 3 then none else
    some ⟨row,if row.identity = 4 then 10 else row.conditionKey,row.actionOrdinals⟩

theorem go_deleted_override_does_not_enable_base :
    goNormalized .override true (normalizeRows normalize [baseA]) (normalizeRows normalize [extraA]) = [] := by decide

theorem jvm_deleted_override_still_suppresses_matching_base :
    jvmNormalized .override true [10] (normalizeRows normalize [baseA,baseB]) [] =
      normalizeRows normalize [baseB] := by decide

theorem jvm_folded_condition_does_not_override_unrelated_original :
    jvmNormalized .override true [99] (normalizeRows normalize [baseA]) (normalizeRows normalize [extraFolded]) =
      normalizeRows normalize [extraFolded,baseA] := by decide

theorem original_false_empty_extra_allows_unchanged_base :
    goNormalized .override false (normalizeRows normalize [baseA]) [] = normalizeRows normalize [baseA] := by decide

theorem extend_preserves_both_order_and_labels :
    goNormalized .extend true (normalizeRows normalize [baseA,baseB]) (normalizeRows normalize [extraFolded]) =
      normalizeRows normalize [baseA,baseB,extraFolded] := by decide

theorem ignore_does_not_add_extra :
    goNormalized .ignore true (normalizeRows normalize [baseA]) (normalizeRows normalize [extraFolded]) =
      normalizeRows normalize [baseA] := by decide

theorem nested_deleted_override_preserves_presence :
    combineGoQuery .override ⟨normalizeRows normalize [baseA],true⟩ ⟨[],true⟩ = ⟨[],true⟩ := by decide

def checks : List Bool := [
  decide (goNormalized .override true (normalizeRows normalize [baseA]) (normalizeRows normalize [extraA]) = []),
  decide (jvmNormalized .override true [10] (normalizeRows normalize [baseA,baseB]) [] = normalizeRows normalize [baseB]),
  decide (jvmNormalized .override true [99] (normalizeRows normalize [baseA]) (normalizeRows normalize [extraFolded]) = normalizeRows normalize [extraFolded,baseA]),
  decide (goNormalized .override false (normalizeRows normalize [baseA]) [] = normalizeRows normalize [baseA]),
  decide (goNormalized .extend true (normalizeRows normalize [baseA,baseB]) (normalizeRows normalize [extraFolded]) = normalizeRows normalize [baseA,baseB,extraFolded]),
  decide (goNormalized .ignore true (normalizeRows normalize [baseA]) (normalizeRows normalize [extraFolded]) = normalizeRows normalize [baseA]),
  decide (combineGoQuery .override ⟨normalizeRows normalize [baseA],true⟩ ⟨[],true⟩ = ⟨[],true⟩)
] ++ MetadataIdentity.controls
theorem checks_pass : checks.all id = true := by decide
end Controls
end ApSpec.RuleProviderComposition
