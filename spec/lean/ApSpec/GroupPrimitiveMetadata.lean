/-
  Two-bit GROUP metadata for one restricted full-member policy family.
  A Bool member records requiresPrimitive's primitive fact, not uniformity of
  a group. Eligibility/ownership changes are modeled as per-entry events.
  No AP, field, layer, source, scheduling or cache semantics are changed here.
  Runtime policy classification, exact eligible-member authority, atomic reads,
  catalog publication/invalidation and accounting are implementation adapters.
-/
import Std

namespace ApSpec.GroupPrimitiveMetadata
set_option autoImplicit false

structure Metadata where
  hasEligible : Bool
  hasPrimitive : Bool
  deriving DecidableEq, Repr

def empty : Metadata := ⟨false, false⟩
def join (a b : Metadata) : Metadata :=
  ⟨a.hasEligible || b.hasEligible, a.hasPrimitive || b.hasPrimitive⟩

@[simp] theorem join_empty (a : Metadata) : join a empty = a := by
  cases a; simp [join, empty]
@[simp] theorem empty_join (a : Metadata) : join empty a = a := by
  cases a; simp [join, empty]
theorem join_assoc (a b c : Metadata) : join (join a b) c = join a (join b c) := by
  cases a; cases b; cases c; simp [join, Bool.or_assoc]
theorem join_comm (a b : Metadata) : join a b = join b a := by
  cases a; cases b; simp [join, Bool.or_comm]
@[simp] theorem join_idem (a : Metadata) : join a a = a := by
  cases a; simp [join]

def summary : List Bool → Metadata
  | [] => empty
  | p :: ps => join ⟨true, p⟩ (summary ps)

@[simp] theorem summary_hasEligible (ps : List Bool) :
    (summary ps).hasEligible = !ps.isEmpty := by
  cases ps <;> simp [summary, empty, join]
@[simp] theorem summary_hasPrimitive (ps : List Bool) :
    (summary ps).hasPrimitive = ps.any id := by
  induction ps with
  | nil => rfl
  | cons p ps ih => simp [summary, join, ih]

theorem summary_append (xs ys : List Bool) :
    summary (xs ++ ys) = join (summary xs) (summary ys) := by
  induction xs with
  | nil => simp [summary]
  | cons p xs ih => simp only [List.cons_append, summary, ih, join_assoc]

/-- The only policy family certified here: unconstrained or primitive-only. -/
def fullKeep (requiresPrimitive primitive : Bool) : Bool := !requiresPrimitive || primitive
@[simp] theorem fullKeep_true : fullKeep true = id := rfl
@[simp] theorem fullKeep_false : fullKeep false = (fun _ => true) := rfl

def oldKeep (requiresPrimitive : Bool) (members : List Bool) : Bool :=
  members.isEmpty || members.any (fullKeep requiresPrimitive)
def newKeep (requiresPrimitive : Bool) (metadata : Metadata) : Bool :=
  !requiresPrimitive || !metadata.hasEligible || metadata.hasPrimitive

theorem old_keep_eq_metadata (requiresPrimitive : Bool) (members : List Bool) :
    oldKeep requiresPrimitive members = newKeep requiresPrimitive (summary members) := by
  cases requiresPrimitive <;> cases members <;>
    simp [oldKeep, newKeep, fullKeep_true, fullKeep_false, summary, empty, join, summary_hasPrimitive]

/-- Conjoin the FULL policies before taking a GROUP existential. -/
theorem full_policy_conjunction (a b primitive : Bool) :
    (fullKeep a primitive && fullKeep b primitive) = fullKeep (a || b) primitive := by
  cases a <;> cases b <;> cases primitive <;> rfl

theorem same_witness_any (a b : Bool) (members : List Bool) :
    members.any (fun p => fullKeep a p && fullKeep b p) =
      members.any (fullKeep (a || b)) := by
  induction members with
  | nil => rfl
  | cons p ps ih => simp only [List.any_cons, full_policy_conjunction]

theorem primitive_conjunction_closes (a b : Bool) (members : List Bool) :
    (members.isEmpty || members.any (fun p => fullKeep a p && fullKeep b p)) =
      newKeep (a || b) (summary members) := by
  rw [same_witness_any]
  exact old_keep_eq_metadata (a || b) members

/-- Per FULL identifier; owner=None has no GROUP contribution. Multiple entries
    may belong to one shared group and may have different primitive facts. -/
structure Entry where
  eligible : Bool
  owner : Option Nat
  primitive : Bool
  deriving DecidableEq, Repr

def active (group : Nat) (entry : Entry) : Bool :=
  entry.eligible && decide (entry.owner = some group)
def contribution (group : Nat) (entry : Entry) : Metadata :=
  let enabled := active group entry
  ⟨enabled, enabled && entry.primitive⟩
def membersFor (group : Nat) (entries : List Entry) : List Bool :=
  entries.filterMap (fun e => if active group e then some e.primitive else none)
def catalogSummary (group : Nat) : List Entry → Metadata
  | [] => empty
  | e :: es => join (contribution group e) (catalogSummary group es)

theorem contribution_inactive (g : Nat) (e : Entry) (h : active g e = false) :
    contribution g e = empty := by simp [contribution, h, empty]
theorem ineligible_no_contribution (g : Nat) (e : Entry) (h : e.eligible = false) :
    contribution g e = empty := by simp [contribution, active, h, empty]
theorem unowned_no_contribution (g : Nat) (eligible primitive : Bool) :
    contribution g ⟨eligible, none, primitive⟩ = empty := by
  simp [contribution, active, empty]

theorem catalogSummary_eq_members (g : Nat) (entries : List Entry) :
    catalogSummary g entries = summary (membersFor g entries) := by
  induction entries with
  | nil => rfl
  | cons e es ih =>
    cases h : active g e with
    | false =>
      have hm : membersFor g (e :: es) = membersFor g es := by simp [membersFor, h]
      rw [hm]
      change join (contribution g e) (catalogSummary g es) = _
      rw [contribution_inactive g e h, empty_join, ih]
    | true =>
      have hm : membersFor g (e :: es) = e.primitive :: membersFor g es := by
        simp [membersFor, h]
      have hc : contribution g e = ⟨true, e.primitive⟩ := by simp [contribution, h]
      rw [hm]
      change join (contribution g e) (catalogSummary g es) = join _ _
      rw [hc, ih]

theorem catalog_keep_equivalence (g : Nat) (entries : List Entry) (r : Bool) :
    oldKeep r (membersFor g entries) = newKeep r (catalogSummary g entries) := by
  rw [catalogSummary_eq_members]
  exact old_keep_eq_metadata r _

theorem catalog_append (g : Nat) (xs ys : List Entry) :
    catalogSummary g (xs ++ ys) = join (catalogSummary g xs) (catalogSummary g ys) := by
  induction xs with
  | nil => simp [catalogSummary]
  | cons e es ih => simp only [List.cons_append, catalogSummary, ih, join_assoc]

private theorem join_reorder (a b c : Metadata) :
    join a (join b c) = join (join a c) b := by
  rw [join_comm b c, ← join_assoc]

/-- Publish the initial census, then OR an entry's first contribution. This
    covers late eligibility and late ownership; it does not certify arbitrary
    removals, reassignment, primitive changes or stale reader snapshots. -/
theorem late_contribution_update (g : Nat) (before after : List Entry)
    (old fresh : Entry) (previous : contribution g old = empty) :
    catalogSummary g (before ++ fresh :: after) =
      join (catalogSummary g (before ++ old :: after)) (contribution g fresh) := by
  simp only [catalog_append, catalogSummary, previous, empty_join]
  exact join_reorder _ _ _

theorem equal_contribution_noop (g : Nat) (before after : List Entry)
    (old fresh : Entry) (same : contribution g old = contribution g fresh) :
    catalogSummary g (before ++ old :: after) =
      catalogSummary g (before ++ fresh :: after) := by
  simp only [catalog_append, catalogSummary, same]

theorem duplicate_contribution_noop (g : Nat) (before : List Entry) (e : Entry) :
    catalogSummary g (before ++ [e,e]) = catalogSummary g (before ++ [e]) := by
  simp [catalog_append, catalogSummary]

theorem late_eligibility (g : Nat) (before after : List Entry)
    (owner : Option Nat) (primitive : Bool) :
    catalogSummary g (before ++ ⟨true,owner,primitive⟩ :: after) =
      join (catalogSummary g (before ++ ⟨false,owner,primitive⟩ :: after))
        (contribution g ⟨true,owner,primitive⟩) :=
  late_contribution_update g before after _ _ (ineligible_no_contribution _ _ rfl)

theorem late_ownership (g : Nat) (before after : List Entry)
    (eligible primitive : Bool) :
    catalogSummary g (before ++ ⟨eligible,some g,primitive⟩ :: after) =
      join (catalogSummary g (before ++ ⟨eligible,none,primitive⟩ :: after))
        (contribution g ⟨eligible,some g,primitive⟩) :=
  late_contribution_update g before after _ _ (unowned_no_contribution _ _ _)

/-- Narrow operation-count certificate, not a JVM/CPU/memory theorem. Charge E
    visits for enumerating E eligible members and one stored-metadata read per
    query; metadata construction/update is charged separately by the adapter.
    Empty-group old enumeration is zero, so no universal strict saving claim. -/
def enumerationVisits (members : List Bool) (queries : Nat) : Nat := queries * members.length
def metadataReads (queries : Nat) : Nat := queries

theorem fixed_members_nonincrease (members : List Bool) (queries : Nat)
    (nonempty : 1 ≤ members.length) :
    metadataReads queries ≤ enumerationVisits members queries := by
  simpa [metadataReads, enumerationVisits] using Nat.mul_le_mul_left queries nonempty

theorem fixed_members_strict (members : List Bool) (queries : Nat)
    (wide : 1 < members.length) (queried : 0 < queries) :
    metadataReads queries < enumerationVisits members queries := by
  simpa [metadataReads, enumerationVisits] using Nat.mul_lt_mul_of_pos_left wide queried

namespace Controls

def controls : List (String × Bool) := [
  ("empty group kept even for primitive policy", newKeep true (summary []) == true),
  ("nonprimitive-only group rejected", oldKeep true [false,false] == false),
  ("mixed shared group has primitive witness", newKeep true (summary [false,true]) == true),
  ("unconstrained policy keeps nonprimitive", newKeep false (summary [false]) == true),
  ("ineligible primitive excluded", catalogSummary 0 [⟨false,some 0,true⟩] == empty),
  ("unowned primitive excluded", catalogSummary 0 [⟨true,none,true⟩] == empty),
  ("other group excluded", catalogSummary 0 [⟨true,some 1,true⟩] == empty),
  ("late primitive eligibility joins", catalogSummary 0 [⟨true,some 0,true⟩] == ⟨true,true⟩),
  ("late owner assignment joins", catalogSummary 7 [⟨true,some 7,false⟩] == ⟨true,false⟩),
  ("duplicate contribution idempotent", catalogSummary 0 [⟨true,some 0,true⟩,⟨true,some 0,true⟩]
      == catalogSummary 0 [⟨true,some 0,true⟩]),
  ("empty to nonprimitive keep may decrease", newKeep true empty &&
      !newKeep true (catalogSummary 0 [⟨true,some 0,false⟩])),
  ("arbitrary split witnesses cannot be intersected",
    let pairs := [(true,false),(false,true)]
    pairs.any Prod.fst && pairs.any Prod.snd && !pairs.any (fun p => p.1 && p.2)),
  ("strict fixed-facts enumeration saving", metadataReads 3 < enumerationVisits [false,true] 3),
  ("empty enumeration not a saving", enumerationVisits [] 3 < metadataReads 3)]

theorem controls_pass : controls.all Prod.snd = true := by decide

end Controls
/- Selected policy revision: all known owned full marks participate. The
   namespace above remains the OLD eligibility comparison, not the new policy.
   Empty groups require an external boundary rule; the one-bit equation below
   is deliberately conditional on a valid nonempty group. -/
namespace AllKnown

def eraseEligibility (entry : Entry) : Entry := { entry with eligible := true }
def entries (catalog : List Entry) : List Entry := catalog.map eraseEligibility
def members (g : Nat) (catalog : List Entry) : List Bool := membersFor g (entries catalog)
def metadata (g : Nat) (catalog : List Entry) : Metadata := catalogSummary g (entries catalog)
def hasPrimitive (g : Nat) (catalog : List Entry) : Bool := (metadata g catalog).hasPrimitive
/-- All-known scalar reference, including its explicit empty-group fallback. -/
def referenceKeep (requiresPrimitive : Bool) (g : Nat) (catalog : List Entry) : Bool :=
  oldKeep requiresPrimitive (members g catalog)
def guardedKeep (requiresPrimitive : Bool) (g : Nat) (catalog : List Entry) : Bool :=
  newKeep requiresPrimitive (metadata g catalog)
/-- This fast path is certified ONLY for valid nonempty groups. -/
def oneBitKeep (requiresPrimitive : Bool) (g : Nat) (catalog : List Entry) : Bool :=
  !requiresPrimitive || hasPrimitive g catalog

theorem ignores_eligibility (g : Nat) (before after : List Entry)
    (oldFlag newFlag : Bool) (owner : Option Nat) (primitive : Bool) :
    metadata g (before ++ ⟨oldFlag,owner,primitive⟩ :: after) =
      metadata g (before ++ ⟨newFlag,owner,primitive⟩ :: after) := by
  simp [metadata, entries, eraseEligibility]

theorem reference_eq_guarded (r : Bool) (g : Nat) (catalog : List Entry) :
    referenceKeep r g catalog = guardedKeep r g catalog :=
  catalog_keep_equivalence g (entries catalog) r

theorem one_bit_nonempty (r : Bool) (g : Nat) (catalog : List Entry)
    (nonempty : (members g catalog).isEmpty = false) :
    referenceKeep r g catalog = oneBitKeep r g catalog := by
  rw [reference_eq_guarded]
  unfold guardedKeep newKeep oneBitKeep hasPrimitive metadata
  rw [catalogSummary_eq_members, summary_hasEligible]
  change (!r || !(!(members g catalog).isEmpty) ||
    (summary (members g catalog)).hasPrimitive) = _
  rw [nonempty]
  simp [members]

theorem empty_boundary (g : Nat) (catalog : List Entry)
    (emptyGroup : members g catalog = []) :
    referenceKeep true g catalog = true ∧ oneBitKeep true g catalog = false := by
  constructor
  · simp [referenceKeep, emptyGroup, oldKeep]
  · unfold oneBitKeep hasPrimitive metadata
    rw [catalogSummary_eq_members]
    change (!true || (summary (members g catalog)).hasPrimitive) = false
    rw [emptyGroup]
    rfl

/-- A concrete member's FULL keep result is covered by the selected GROUP.
    This is a positive witness theorem, not eligibility-policy equivalence. -/
theorem member_keep_coverage (r : Bool) (g : Nat) (catalog : List Entry)
    (primitive : Bool) (present : primitive ∈ members g catalog)
    (kept : fullKeep r primitive = true) : referenceKeep r g catalog = true := by
  have witness : (members g catalog).any (fullKeep r) = true :=
    List.any_eq_true.mpr ⟨primitive, present, kept⟩
  simp [referenceKeep, oldKeep, witness]

theorem member_nonempty (g : Nat) (catalog : List Entry) (primitive : Bool)
    (present : primitive ∈ members g catalog) : (members g catalog).isEmpty = false := by
  cases h : members g catalog with
  | nil => simp [h] at present
  | cons p ps => rfl

theorem member_one_bit_coverage (r : Bool) (g : Nat) (catalog : List Entry)
    (primitive : Bool) (present : primitive ∈ members g catalog)
    (kept : fullKeep r primitive = true) : oneBitKeep r g catalog = true := by
  rw [← one_bit_nonempty r g catalog (member_nonempty g catalog primitive present)]
  exact member_keep_coverage r g catalog primitive present kept

/-- Membership and actual owner publication provide the executable member
    witness; its old eligibility flag is deliberately irrelevant. -/
theorem owned_entry_member (g : Nat) (catalog : List Entry) (entry : Entry)
    (present : entry ∈ catalog) (owned : entry.owner = some g) :
    entry.primitive ∈ members g catalog := by
  unfold members membersFor entries
  apply List.mem_filterMap.mpr
  exact ⟨eraseEligibility entry, List.mem_map.mpr ⟨entry, present, rfl⟩,
    by simp [active, eraseEligibility, owned]⟩

theorem owned_entry_coverage (r : Bool) (g : Nat) (catalog : List Entry) (entry : Entry)
    (present : entry ∈ catalog) (owned : entry.owner = some g)
    (kept : fullKeep r entry.primitive = true) :
    referenceKeep r g catalog = true ∧ oneBitKeep r g catalog = true := by
  have member := owned_entry_member g catalog entry present owned
  exact ⟨member_keep_coverage r g catalog entry.primitive member kept,
    member_one_bit_coverage r g catalog entry.primitive member kept⟩

theorem metadata_append (g : Nat) (xs ys : List Entry) :
    metadata g (xs ++ ys) = join (metadata g xs) (metadata g ys) := by
  simp only [metadata, entries, List.map_append, catalog_append]

theorem primitive_append (g : Nat) (xs ys : List Entry) :
    hasPrimitive g (xs ++ ys) = (hasPrimitive g xs || hasPrimitive g ys) := by
  unfold hasPrimitive
  rw [metadata_append]
  rfl

/-- Executable one-bit summary: no eligibility test and no hasEligible bit. -/
def primitiveContribution (g : Nat) (entry : Entry) : Bool :=
  decide (entry.owner = some g) && entry.primitive
def primitiveSummary (g : Nat) : List Entry → Bool
  | [] => false
  | e :: es => primitiveContribution g e || primitiveSummary g es

theorem hasPrimitive_eq_one_bit_summary (g : Nat) (catalog : List Entry) :
    hasPrimitive g catalog = primitiveSummary g catalog := by
  induction catalog with
  | nil => rfl
  | cons e es ih =>
    change ((contribution g (eraseEligibility e)).hasPrimitive || hasPrimitive g es) =
      (primitiveContribution g e || primitiveSummary g es)
    rw [ih]
    rfl

theorem one_bit_summary_append (g : Nat) (xs ys : List Entry) :
    primitiveSummary g (xs ++ ys) = (primitiveSummary g xs || primitiveSummary g ys) := by
  rw [← hasPrimitive_eq_one_bit_summary, primitive_append,
    hasPrimitive_eq_one_bit_summary, hasPrimitive_eq_one_bit_summary]

theorem all_known_conjunction (a b : Bool) (g : Nat) (catalog : List Entry) :
    ((members g catalog).isEmpty || (members g catalog).any
      (fun p => fullKeep a p && fullKeep b p)) = guardedKeep (a || b) g catalog := by
  rw [primitive_conjunction_closes]
  unfold guardedKeep metadata
  rw [catalogSummary_eq_members]
  rfl

/-- Successful owner publication carries owner and immutable primitive fact
    together. A raw reserveFull may remain pending/unowned under the runtime's
    existing protocol; that earlier state is not a KnownFull. This model does
    not move ownership to raw reservation time. Published full IDs are stable; registering an already-known ID is a no-op even if a caller
    attempts different fields. This is state-only: reporting a conflicting
    registration as an error is outside the model. There is no removal or
    reassignment operation. -/
structure KnownFull where
  full : Nat
  owner : Nat
  primitive : Bool
  deriving DecidableEq, Repr

def asEntry (mark : KnownFull) : Entry := ⟨true,some mark.owner,mark.primitive⟩
def known (catalog : List KnownFull) (id : Nat) : Bool :=
  catalog.any (fun mark => decide (mark.full = id))
def register (catalog : List KnownFull) (mark : KnownFull) : List KnownFull :=
  if known catalog mark.full then catalog else catalog ++ [mark]
def bit (g : Nat) (catalog : List KnownFull) : Bool :=
  hasPrimitive g (catalog.map asEntry)

theorem register_existing_noop (catalog : List KnownFull) (mark : KnownFull)
    (present : known catalog mark.full = true) : register catalog mark = catalog := by
  simp [register, present]

theorem register_preserves_existing (catalog : List KnownFull) (mark prior : KnownFull)
    (present : prior ∈ catalog) : prior ∈ register catalog mark := by
  unfold register
  split
  · exact present
  · exact List.mem_append_left _ present

theorem register_duplicate_noop (catalog : List KnownFull) (mark : KnownFull) :
    register (register catalog mark) mark = register catalog mark := by
  cases h : known catalog mark.full with
  | true => simp [register, h]
  | false =>
    have present : known (catalog ++ [mark]) mark.full = true := by
      simp [known]
    simp [register, h, present]

theorem creation_bit_update (g : Nat) (catalog : List KnownFull) (mark : KnownFull)
    (fresh : known catalog mark.full = false) :
    bit g (register catalog mark) =
      (bit g catalog || (decide (mark.owner = g) && mark.primitive)) := by
  simp only [register, fresh, Bool.false_eq_true, ↓reduceIte, bit, List.map_append]
  rw [primitive_append]
  congr 1
  simp [hasPrimitive, metadata, entries, asEntry, eraseEligibility,
    catalogSummary, contribution, active, empty, join]

theorem owner_cannot_be_reassigned (catalog : List KnownFull) (attempt : KnownFull)
    (alreadyKnown : known catalog attempt.full = true) :
    register catalog attempt = catalog := register_existing_noop catalog attempt alreadyKnown

/-- The selected policy change is NOT always widening: previously no eligible
    members caused keep=true; a known nonprimitive member now causes false. -/
theorem eligibility_change_can_reject :
    oldKeep true (membersFor 0 [⟨false,some 0,false⟩]) = true ∧
      referenceKeep true 0 [⟨false,some 0,false⟩] = false := by decide

theorem eligibility_change_can_keep :
    oldKeep true (membersFor 0 [⟨true,some 0,false⟩,⟨false,some 0,true⟩]) = false ∧
      referenceKeep true 0 [⟨true,some 0,false⟩,⟨false,some 0,true⟩] = true := by decide

namespace Controls

def controls : List (String × Bool) := [
  ("known ineligible primitive contributes", metadata 0 [⟨false,some 0,true⟩] == ⟨true,true⟩),
  ("eligibility is ignored", metadata 0 [⟨false,some 0,false⟩] ==
    metadata 0 [⟨true,some 0,false⟩]),
  ("unknown owner contributes nothing", metadata 0 [⟨false,none,true⟩] == empty),
  ("other owner contributes nothing", metadata 0 [⟨false,some 1,true⟩] == empty),
  ("one bit keeps mixed shared group", oneBitKeep true 0
    [⟨false,some 0,false⟩,⟨true,some 0,true⟩]),
  ("old empty eligible new rejection", oldKeep true (membersFor 0 [⟨false,some 0,false⟩]) &&
    !referenceKeep true 0 [⟨false,some 0,false⟩]),
  ("adding known primitive may also keep", !oldKeep true
    (membersFor 0 [⟨true,some 0,false⟩,⟨false,some 0,true⟩]) &&
    referenceKeep true 0 [⟨true,some 0,false⟩,⟨false,some 0,true⟩]),
  ("empty group requires boundary contract", referenceKeep true 0 [] && !oneBitKeep true 0 []),
  ("known creation updates bit", bit 0 (register [] ⟨7,0,true⟩)),
  ("duplicate full registration idempotent", register [⟨7,0,false⟩] ⟨7,0,false⟩ ==
    [⟨7,0,false⟩]),
  ("attempted owner replacement ignored", register [⟨7,0,false⟩] ⟨7,1,true⟩ ==
    [⟨7,0,false⟩]),
  ("owned primitive full witness covered", fullKeep true true &&
    oneBitKeep true 0 [⟨false,some 0,true⟩]),
  ("unconstrained nonprimitive full witness covered", fullKeep false false &&
    oneBitKeep false 0 [⟨false,some 0,false⟩])]

theorem controls_pass : controls.all Prod.snd = true := by decide

end Controls
end AllKnown
end ApSpec.GroupPrimitiveMetadata
