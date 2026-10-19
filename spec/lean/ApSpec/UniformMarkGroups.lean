/-
  Selected uniform encoded groups, serialized discovery, lazy immutable keys,
  and query-dependent STATIC guidance. Historical census/member models are not
  imported. Bool suffixes here are the PARSED exact primitive suffix, not a
  substring test. Owner precedence is resolved by the existing import/sidecar
  rules BEFORE these declarations; this model selects no new precedence rule.

  GroupName is an immutable canonical owner+flag encoding. Validation rejects
  conflicting full owners and flags for one authoritative owner, including a
  mixed SharedModel; it must not silently split an owner by per-mark property.
  The interner stores keys only, never a catalog/group-member inventory.

  Guidance theorems compare contribution unions, not candidate lists or event
  counts. An abstract ordinary AP transfer retains all geometry/tail/E/mx/layer
  behavior. Its existing mark guard is a required refinement bridge. No actual
  input, source, static supply or executable root is created by a guide.
  This replaces ONLY the implicit guide branch. Existing actual-current-premise
  admission/publication and ZERO/other publication bypasses remain unchanged;
  an empty implicit contribution is not a global veto on a cross-group result.
-/
import Std

namespace ApSpec.UniformMarkGroups
set_option autoImplicit false

inductive Owner where
  | rule (id : Nat)
  | model (id : Nat)
  | shared
  deriving DecidableEq, Repr

structure FullName where
  stem : Nat
  primitiveSuffix : Bool
  deriving DecidableEq, Repr

structure GroupName where
  owner : Owner
  primitiveFlag : Bool
  deriving DecidableEq, Repr

/-- Typed canonical representation of the encoded name; actual string parsing
    and exact suffix validation must implement this injective representation. -/
def encode (group : GroupName) : Owner × Bool := (group.owner,group.primitiveFlag)
def decode (code : Owner × Bool) : GroupName := ⟨code.1,code.2⟩
theorem decode_encode (group : GroupName) : decode (encode group) = group := by cases group; rfl
theorem encode_injective (a b : GroupName) (same : encode a = encode b) : a = b := by
  rw [← decode_encode a, ← decode_encode b, same]

structure Declaration where
  full : FullName
  group : GroupName
  deriving DecidableEq, Repr

def validDeclaration (d : Declaration) : Bool := decide (d.full.primitiveSuffix = d.group.primitiveFlag)
def compatible (a b : Declaration) : Bool := decide (
  (a.full = b.full → a.group = b.group) ∧ (a.group.owner = b.group.owner → a.group = b.group))
def validate (declarations : List Declaration) : Bool :=
  declarations.all validDeclaration && declarations.all (fun a => declarations.all (compatible a))

theorem validated_property (declarations : List Declaration) (valid : validate declarations = true)
    (d : Declaration) (present : d ∈ declarations) : d.full.primitiveSuffix = d.group.primitiveFlag := by
  have checked := List.all_eq_true.mp (Bool.and_eq_true_iff.mp valid).1 d present
  simpa [validDeclaration] using checked

theorem validated_owners (declarations : List Declaration) (valid : validate declarations = true)
    (a b : Declaration) (ha : a ∈ declarations) (hb : b ∈ declarations) :
    (a.full = b.full → a.group = b.group) ∧ (a.group.owner = b.group.owner → a.group = b.group) := by
  have row := List.all_eq_true.mp (Bool.and_eq_true_iff.mp valid).2 a ha
  have pair := List.all_eq_true.mp row b hb
  exact of_decide_eq_true pair

theorem uniform_group (declarations : List Declaration) (valid : validate declarations = true)
    (a b : Declaration) (ha : a ∈ declarations) (hb : b ∈ declarations)
    (sameGroup : a.group = b.group) : a.full.primitiveSuffix = b.full.primitiveSuffix := by
  rw [validated_property declarations valid a ha, validated_property declarations valid b hb, sameGroup]

def fullKeep (requiresPrimitive : Bool) (mark : FullName) : Bool :=
  !requiresPrimitive || mark.primitiveSuffix
def groupKeep (requiresPrimitive : Bool) (group : GroupName) : Bool :=
  !requiresPrimitive || group.primitiveFlag

theorem full_group_policy (r : Bool) (mark : FullName) (group : GroupName)
    (uniform : mark.primitiveSuffix = group.primitiveFlag) :
    fullKeep r mark = groupKeep r group := by simp [fullKeep, groupKeep, uniform]

/-- Finite members are a proof-only old scalar reference, never a runtime API. -/
def scalarReference (policy : FullName → Bool) (members : List FullName) : Bool :=
  members.isEmpty || members.any policy

theorem uniform_any (members : List FullName) (policy : FullName → Bool) (value : Bool)
    (uniform : ∀ member, member ∈ members → policy member = value) :
    members.any policy = (!members.isEmpty && value) := by
  induction members with
  | nil => rfl
  | cons mark rest ih =>
    have head := uniform mark (by simp)
    have tail : ∀ member, member ∈ rest → policy member = value := by
      intro member present; exact uniform member (by simp [present])
    rw [List.any_cons, head, ih tail]
    cases value <;> simp

theorem nonempty_scalar_eq_group (members : List FullName) (group : GroupName) (r : Bool)
    (nonempty : members.isEmpty = false)
    (uniform : ∀ member, member ∈ members → member.primitiveSuffix = group.primitiveFlag) :
    scalarReference (fullKeep r) members = groupKeep r group := by
  rw [scalarReference, uniform_any members (fullKeep r) (groupKeep r group)
    (fun member present => full_group_policy r member group (uniform member present)), nonempty]
  rfl

theorem primitive_policy_and (a b primitive : Bool) :
    ((!a || primitive) && (!b || primitive)) = (!(a || b) || primitive) := by
  cases a <;> cases b <;> cases primitive <;> rfl

theorem group_policy_and (a b : Bool) (group : GroupName) :
    (groupKeep a group && groupKeep b group) = groupKeep (a || b) group :=
  primitive_policy_and a b group.primitiveFlag

theorem same_witness_and_scalar (members : List FullName) (group : GroupName) (a b : Bool)
    (nonempty : members.isEmpty = false)
    (uniform : ∀ member, member ∈ members → member.primitiveSuffix = group.primitiveFlag) :
    scalarReference (fun mark => fullKeep a mark && fullKeep b mark) members =
      groupKeep (a || b) group := by
  rw [scalarReference, uniform_any members _ (groupKeep (a || b) group) (by
    intro mark present
    rw [full_group_policy a mark group (uniform mark present),
      full_group_policy b mark group (uniform mark present), group_policy_and]), nonempty]
  rfl

/-- Exact native policy contracts compose. One-way FULL coverage alone would
    not prove equality to arbitrary old existential member lifting. -/
theorem native_policy_and (fullA fullB : FullName → Bool) (groupA groupB : GroupName → Bool)
    (mark : FullName) (group : GroupName)
    (a : fullA mark = groupA group) (b : fullB mark = groupB group) :
    (fullA mark && fullB mark) = (groupA group && groupB group) := by rw [a,b]

namespace Serialized

/-- Covers mark-bearing conditions/actions/metadata structurally. Generated
    names must be supplied by the existing generated markOwners sidecars. -/
inductive Term where
  | empty
  | mark (name : FullName)
  | combine (left right : Term)
  deriving DecidableEq, Repr

def scan : Term → List FullName
  | .empty => []
  | .mark name => [name]
  | .combine left right => scan left ++ scan right

/-- Binding chooses positions, drops candidates and copies unchanged kinds.
    A wrapper that invents an undeclared name does NOT satisfy this relation. -/
inductive Binds : Term → List (Nat × FullName) → Prop where
  | empty : Binds .empty []
  | mark (name : FullName) (positions : List Nat) :
      Binds (.mark name) (positions.map (fun position => (position,name)))
  | discard (term : Term) : Binds term []
  | combine {left right : Term} {a b : List (Nat × FullName)} :
      Binds left a → Binds right b → Binds (.combine left right) (a ++ b)
  | reposition {term : Term} {values : List (Nat × FullName)} (position : Nat → Nat) :
      Binds term values → Binds term (values.map (fun value => (position value.1,value.2)))

theorem binding_names_scanned (term : Term) (bound : List (Nat × FullName))
    (binding : Binds term bound) (value : Nat × FullName) (present : value ∈ bound) :
    value.2 ∈ scan term := by
  induction binding generalizing value with
  | empty => simp at present
  | mark name positions =>
    obtain ⟨position,_,same⟩ := List.mem_map.mp present
    rw [← same]; simp [scan]
  | discard term => simp at present
  | combine left right ihLeft ihRight =>
    rcases List.mem_append.mp present with fromLeft | fromRight
    · exact List.mem_append_left _ (ihLeft value fromLeft)
    · exact List.mem_append_right _ (ihRight value fromRight)
  | reposition position original ih =>
    obtain ⟨source,sourcePresent,same⟩ := List.mem_map.mp present
    have nameSame := congrArg (fun item : Nat × FullName => item.2) same
    rw [← nameSame]
    exact ih source sourcePresent

/-- Completeness is supplied by serialized declarations PLUS every existing
    generated-owner sidecar. Binding itself performs no owner/name discovery. -/
theorem bound_property_from_validated_declarations (term : Term) (bound : List (Nat × FullName))
    (binding : Binds term bound) (declarations : List Declaration)
    (valid : validate declarations = true) (owner : FullName → GroupName)
    (complete : ∀ name, name ∈ scan term → ∃ declaration,
      declaration ∈ declarations ∧ declaration.full = name ∧ declaration.group = owner name)
    (value : Nat × FullName) (present : value ∈ bound) :
    value.2.primitiveSuffix = (owner value.2).primitiveFlag := by
  obtain ⟨declaration,inDeclarations,sameName,sameOwner⟩ :=
    complete value.2 (binding_names_scanned term bound binding value present)
  have property := validated_property declarations valid declaration inDeclarations
  simpa only [sameName,sameOwner] using property

structure ScanResult where
  names : List FullName
  specializedLookups : Nat
  deriving DecidableEq, Repr

def setup : Term → ScanResult
  | .empty => ⟨[],0⟩
  | .mark name => ⟨[name],0⟩
  | .combine left right =>
    let a := setup left
    let b := setup right
    ⟨a.names ++ b.names,a.specializedLookups + b.specializedLookups⟩

theorem setup_scan_and_no_binding_queries (term : Term) :
    (setup term).names = scan term ∧ (setup term).specializedLookups = 0 := by
  induction term with
  | empty => exact ⟨rfl,rfl⟩
  | mark name => exact ⟨rfl,rfl⟩
  | combine left right ihLeft ihRight =>
    simp [setup, scan, ihLeft.1, ihRight.1, ihLeft.2, ihRight.2]

end Serialized

namespace Interner

inductive Key where
  | zero
  | full (name : FullName)
  | group (name : GroupName)
  deriving DecidableEq, Repr
abbrev State := List Key

def index (state : State) (key : Key) : Nat := state.findIdx (fun candidate => decide (candidate = key))
def intern (state : State) (key : Key) : State × Nat :=
  if index state key < state.length then (state,index state key) else (state ++ [key],state.length)

theorem existing_slot_stable (state : State) (key : Key) (id : Nat) (old : id < state.length) :
    (intern state key).1[id]? = state[id]? := by
  unfold intern
  split
  · rfl
  · exact List.getElem?_append_left old

theorem intern_lookup (state : State) (key : Key) :
    (intern state key).1[(intern state key).2]? = some key := by
  unfold intern
  split
  · rename_i present
    have hit := @List.findIdx_getElem Key (fun candidate => decide (candidate = key)) state present
    have same : state[index state key] = key := of_decide_eq_true hit
    rw [List.getElem?_eq_getElem present, same]
  · simp

theorem intern_idempotent (state : State) (key : Key) :
    intern (intern state key).1 key = intern state key := by
  by_cases present : index state key < state.length
  · simp [intern, present]
  · have appended : index (state ++ [key]) key = state.length := by
      unfold index
      rw [List.findIdx_append]
      change (if index state key < state.length then index state key else _ + state.length) = state.length
      simp [present]
    simp [intern, present, appended]

theorem ids_identify_keys (state : State) (a b : Key) (sameId : (intern state a).2 = (intern state b).2)
    (bothExisting : (intern state a).1 = state ∧ (intern state b).1 = state) : a = b := by
  have ha := intern_lookup state a
  have hb := intern_lookup state b
  rw [bothExisting.1] at ha
  rw [bothExisting.2, ← sameId] at hb
  exact Option.some.inj (ha.symm.trans hb)

def groupKey (owner : FullName → GroupName) : Key → Key
  | .zero => .zero
  | .full name => .group (owner name)
  | .group name => .group name

theorem projection_idempotent (owner : FullName → GroupName) (key : Key) :
    groupKey owner (groupKey owner key) = groupKey owner key := by cases key <;> rfl

def project (owner : FullName → GroupName) (state : State) (key : Key) : State × Nat :=
  intern state (groupKey owner key)

theorem projected_key_exact (owner : FullName → GroupName) (state : State) (key : Key) :
    (project owner state key).1[(project owner state key).2]? = some (groupKey owner key) :=
  intern_lookup state _

theorem projected_group_reintern_noop (owner : FullName → GroupName) (state : State) (key : Key) :
    intern (project owner state key).1 (groupKey owner key) = project owner state key :=
  intern_idempotent state _

theorem allocation_bound (state : State) (key : Key) : (intern state key).1.length ≤ state.length + 1 := by
  unfold intern
  split <;> simp

end Interner

namespace Guidance

inductive Mark where
  | zero | flow | full (name : FullName)
  deriving DecidableEq, Repr
inductive Base where
  | zero | ordinary (id : Nat) | static
  deriving DecidableEq, Repr
inductive Tail where
  | exact | star | any | must
  deriving DecidableEq, Repr

/-- Opaque AP data retained by the same ordinary transfer on both sides. -/
structure Actual where
  base : Base
  path : List Nat
  tail : Tail
  fields : List (List Nat)
  exclusions : List GroupName
  demand : Bool
  mark : Mark
  deriving DecidableEq, Repr

structure Query where
  entry : Actual
  initial : Actual
  conclusion : Actual
  deriving DecidableEq, Repr

structure Guide where
  entryGroup : GroupName
  exitGroup : GroupName
  entryTail : Tail
  exitTail : Tail
  entryPath : List Nat
  exitPath : List Nat
  entryFields : List (List Nat)
  exitFields : List (List Nat)
  deriving DecidableEq, Repr

def shared (group : GroupName) : Guide := ⟨group,group,.any,.star,[],[],[],[]⟩

def select (owner : FullName → GroupName) (actual : Actual) : Option GroupName :=
  match actual.base, actual.mark with
  | .static, .full name => some (owner name)
  | _, _ => none

def emissionOwner (owner : FullName → GroupName) (query : Query) := select owner query.entry
def restrictionOwner (owner : FullName → GroupName) (query : Query) := select owner query.initial

/-- Abstract form of the EXISTING ordinary entry mark/base guard. `body` is the
    same full AP operation (including its paired exit test), not a new policy.
    Kotlin emit/restrict must establish this guard factorization. -/
def contribution {α : Type} (selector : Query → Option GroupName)
    (body : Guide → Query → List α) (group : GroupName) (query : Query) : List α :=
  if selector query = some group then body (shared group) query else []

def enumerated {α : Type} (groups : List GroupName) (selector : Query → Option GroupName)
    (body : Guide → Query → List α) (query : Query) : List α :=
  groups.flatMap (fun group => contribution selector body group query)

def selected {α : Type} (selector : Query → Option GroupName)
    (body : Guide → Query → List α) (query : Query) : List α :=
  match selector query with
  | none => []
  | some group => body (shared group) query

theorem wrong_group_zero {α : Type} (selector : Query → Option GroupName)
    (body : Guide → Query → List α) (group : GroupName) (query : Query)
    (wrong : selector query ≠ some group) : contribution selector body group query = [] := by
  simp [contribution, wrong]

/-- Exact union equivalence. No global inventory is used by `selected`. The
    list of groups is a proof-only complete enumeration reference. -/
theorem enumeration_equivalence {α : Type} (groups : List GroupName)
    (selector : Query → Option GroupName) (body : Guide → Query → List α) (query : Query)
    (complete : ∀ group, selector query = some group → group ∈ groups) (result : α) :
    result ∈ enumerated groups selector body query ↔ result ∈ selected selector body query := by
  cases chosen : selector query with
  | none => simp [enumerated, contribution, selected, chosen]
  | some group =>
    constructor
    · intro present
      obtain ⟨candidate,_,output⟩ := List.mem_flatMap.mp present
      by_cases same : candidate = group
      · subst candidate; simpa [contribution, selected, chosen] using output
      · have wrong : selector query ≠ some candidate := by simp [chosen, Ne.symm same]
        simp [contribution, wrong] at output
    · intro present
      apply List.mem_flatMap.mpr
      exact ⟨group,complete group chosen,by simpa [contribution, selected, chosen] using present⟩

theorem emission_equivalence {α : Type} (groups : List GroupName) (owner : FullName → GroupName)
    (body : Guide → Query → List α) (query : Query)
    (complete : ∀ group, emissionOwner owner query = some group → group ∈ groups) (result : α) :
    result ∈ enumerated groups (emissionOwner owner) body query ↔
      result ∈ selected (emissionOwner owner) body query :=
  enumeration_equivalence groups _ body query complete result

theorem restriction_equivalence {α : Type} (groups : List GroupName) (owner : FullName → GroupName)
    (body : Guide → Query → List α) (query : Query)
    (complete : ∀ group, restrictionOwner owner query = some group → group ∈ groups) (result : α) :
    result ∈ enumerated groups (restrictionOwner owner) body query ↔
      result ∈ selected (restrictionOwner owner) body query :=
  enumeration_equivalence groups _ body query complete result

/-- Actual-current-premise publication, explicit guides and other unchanged
    paths are composed outside this implicit family. Empty family contribution
    never vetoes an already authorized cross-group ordinary result. -/
theorem unchanged_bypass_equivalence {α : Type} (groups : List GroupName)
    (selector : Query → Option GroupName) (body : Guide → Query → List α) (query : Query)
    (complete : ∀ group, selector query = some group → group ∈ groups)
    (bypass : List α) (result : α) :
    result ∈ bypass ++ enumerated groups selector body query ↔
      result ∈ bypass ++ selected selector body query := by
  simp only [List.mem_append]
  rw [enumeration_equivalence groups selector body query complete result]

theorem selected_uses_same_pair {α : Type} (selector : Query → Option GroupName)
    (body : Guide → Query → List α) (query : Query) (group : GroupName)
    (chosen : selector query = some group) :
    selected selector body query = body (shared group) query ∧
      (shared group).entryGroup = group ∧ (shared group).exitGroup = group := by
  simp [selected, chosen, shared]

theorem no_output_without_ordinary_result {α : Type} (selector : Query → Option GroupName)
    (body : Guide → Query → List α) (query : Query)
    (none : ∀ group, body (shared group) query = []) : selected selector body query = [] := by
  cases chosen : selector query <;> simp [selected, chosen, none]

theorem flow_zero_no_selection (owner : FullName → GroupName) (actual : Actual)
    (notFull : actual.mark = .flow ∨ actual.mark = .zero) : select owner actual = none := by
  rcases notFull with flow | zero
  · cases actual.base <;> simp [select, flow]
  · cases actual.base <;> simp [select, zero]

theorem premise_member_union {α : Type} (groups : List GroupName) (selector : Query → Option GroupName)
    (body : Guide → Query → List α) (queries : List Query)
    (complete : ∀ query, query ∈ queries → ∀ group, selector query = some group → group ∈ groups)
    (result : α) :
    result ∈ queries.flatMap (enumerated groups selector body) ↔
      result ∈ queries.flatMap (selected selector body) := by
  constructor
  · intro present
    obtain ⟨query,inQueries,output⟩ := List.mem_flatMap.mp present
    exact List.mem_flatMap.mpr ⟨query,inQueries,
      (enumeration_equivalence groups selector body query (complete query inQueries) result).mp output⟩
  · intro present
    obtain ⟨query,inQueries,output⟩ := List.mem_flatMap.mp present
    exact List.mem_flatMap.mpr ⟨query,inQueries,
      (enumeration_equivalence groups selector body query (complete query inQueries) result).mpr output⟩

/-- Candidate-count scope only: one existing ordinary transfer can still do
    arbitrary AP work. No CPU, allocation or full-fact work ratio is claimed. -/
def selectedCandidateCount (selector : Query → Option GroupName) (query : Query) : Nat :=
  match selector query with | none => 0 | some _ => 1

theorem selected_candidates_nonincrease (groups : List GroupName)
    (selector : Query → Option GroupName) (query : Query)
    (complete : ∀ group, selector query = some group → group ∈ groups) :
    selectedCandidateCount selector query ≤ groups.length := by
  cases chosen : selector query with
  | none => simp [selectedCandidateCount, chosen]
  | some group =>
    have present := complete group chosen
    have positive := List.length_pos_of_mem present
    simpa [selectedCandidateCount, chosen] using (Nat.succ_le_of_lt positive)

theorem selected_candidates_strict (groups : List GroupName)
    (selector : Query → Option GroupName) (query : Query) (group : GroupName)
    (chosen : selector query = some group) (many : 1 < groups.length) :
    selectedCandidateCount selector query < groups.length := by
  simpa [selectedCandidateCount, chosen] using many

end Guidance

namespace Controls

def ordinaryGroup : GroupName := ⟨.rule 4,false⟩
def primitiveGroup : GroupName := ⟨.rule 5,true⟩
def ordinaryMark : FullName := ⟨10,false⟩
def primitiveMark : FullName := ⟨11,true⟩
def owner : FullName → GroupName := fun mark => if mark.primitiveSuffix then primitiveGroup else ordinaryGroup

def actual (mark : Guidance.Mark) : Guidance.Actual :=
  ⟨.static,[2,7],.must,[[9]],[primitiveGroup],true,mark⟩
def query : Guidance.Query := ⟨actual (.full ordinaryMark),actual (.full ordinaryMark),actual .flow⟩

def controls : List (String × Bool) := [
  ("uniform ordinary declaration valid", validate [⟨ordinaryMark,ordinaryGroup⟩]),
  ("uniform primitive declaration valid", validate [⟨primitiveMark,primitiveGroup⟩]),
  ("exact suffix mismatch rejected", !validate [⟨primitiveMark,ordinaryGroup⟩]),
  ("mixed SharedModel rejected", !validate [⟨ordinaryMark,⟨.shared,false⟩⟩,⟨primitiveMark,⟨.shared,false⟩⟩]),
  ("same owner cannot silently split flag", !validate [⟨ordinaryMark,⟨.shared,false⟩⟩,⟨primitiveMark,⟨.shared,true⟩⟩]),
  ("conflicting full owner rejected", !validate [⟨ordinaryMark,ordinaryGroup⟩,⟨ordinaryMark,⟨.rule 6,false⟩⟩]),
  ("group property is encoded", (decode (encode primitiveGroup)).primitiveFlag),
  ("no runtime member enumeration", groupKeep true primitiveGroup),
  ("empty scalar exception needs nonempty guard", scalarReference (fullKeep true) [] && !groupKeep true ordinaryGroup),
  ("interner idempotent", Interner.intern (Interner.intern [] (.group primitiveGroup)).1
    (.group primitiveGroup) == Interner.intern [] (.group primitiveGroup)),
  ("projection does not allocate FULL sibling inventory", (Interner.project owner [] (.full ordinaryMark)).1.length == 1),
  ("FLOW gives no guide", (Guidance.select owner (actual .flow)).isNone),
  ("ZERO gives no guide", (Guidance.select owner (actual .zero)).isNone),
  ("restriction uses initial owner even with FLOW output", Guidance.selected (Guidance.restrictionOwner owner)
    (fun _ _ => [37]) query == [37]),
  ("conclusion selection would lose ordinary result", (Guidance.select owner query.conclusion).isNone),
  ("ordinary bypass survives absent implicit guide", ([91] ++ Guidance.selected
    (fun _ => none) (fun _ _ => []) query) == [91]),
  ("wrong group contributes nothing", Guidance.contribution (Guidance.restrictionOwner owner)
    (fun _ _ => [37]) primitiveGroup query == []),
  ("paired exit remains entry group", (Guidance.shared ordinaryGroup).exitGroup == ordinaryGroup),
  ("selected transfer receives unchanged geometry", Guidance.selected (Guidance.restrictionOwner owner)
    (fun _ q => [q.conclusion]) query == [query.conclusion]),
  ("serialized scanner does not bind specialized methods", (Serialized.setup
    (.combine (.mark ordinaryMark) (.mark primitiveMark))).specializedLookups == 0)]

theorem controls_pass : controls.all Prod.snd = true := by decide

end Controls
end ApSpec.UniformMarkGroups
