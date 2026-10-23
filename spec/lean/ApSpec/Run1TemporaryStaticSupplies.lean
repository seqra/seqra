/-
Exact temporary admission for run-1 FULL static supplies. The canonical actor
profile keeps the COMPLETE bounded output region, including E, as its key.
Only empty-E named fields and EXACT class markers enter that profile. The raw
API, GROUP and later-run profiles keep the original ordered list.

The service is an executable pointwise projection of finite runtime maps.
Lookup functions never enumerate their infinite index type. `answers` and
`writerEffects` below are GHOST finite-trace observations, not new answered
runtime stores. Supplies run after the unchanged earlier commit lanes, with
no owner callbacks inside the locked supply lane.

The final finite derivation transport assumes the SAME deterministic ordinary,
query and demand adapter inventory. It does not prove the Kotlin AP adapters,
actor fairness, allocation counts or wall-clock performance.
-/
import Std
namespace ApSpec.Run1TemporaryStaticSupplies
set_option autoImplicit false

structure FullMark where
  group : Nat
  full : Nat
  deriving DecidableEq, Repr
inductive Slot where
  | field (classId fieldId : Nat)
  | exactClass (classId : Nat)
  deriving DecidableEq, Repr
inductive Tail where
  | exact | star | any | must
  deriving DecidableEq, Repr
structure Region where
  slot : Slot
  suffix : List Nat := []
  tail : Tail := .any
  exclusion : List Nat := []
  deriving DecidableEq, Repr

def Region.canonical (r : Region) : Bool :=
  r.exclusion.isEmpty && match r.slot with
  | .field _ _ => true
  | .exactClass _ => r.suffix.isEmpty && decide (r.tail = .exact)
structure Atom where
  region : Region
  mark : FullMark
  deriving DecidableEq, Repr
structure Packet where
  region : Region
  marks : List FullMark
  deriving DecidableEq, Repr

def expand (p : Packet) : List Atom := p.marks.map (⟨p.region, ·⟩)
def flatten (packets : List Packet) : List Atom := packets.flatMap expand
structure Row where
  region : Region
  marks : List FullMark
  deriving DecidableEq, Repr
abbrev SupplyMap := List Row

def insertMark (mark : FullMark) (marks : List FullMark) : List FullMark :=
  if mark ∈ marks then marks else mark :: marks

def add (atom : Atom) : SupplyMap → SupplyMap
  | [] => [⟨atom.region, [atom.mark]⟩]
  | row :: rest =>
      if row.region = atom.region then
        { row with marks := insertMark atom.mark row.marks } :: rest
      else row :: add atom rest

def merge (atoms : List Atom) : SupplyMap := atoms.foldl (fun rows atom => add atom rows) []
def decode (rows : SupplyMap) : List Atom :=
  rows.flatMap (fun row => row.marks.map (⟨row.region, ·⟩))

theorem mem_insertMark (x mark : FullMark) (marks : List FullMark) :
    x ∈ insertMark mark marks ↔ x = mark ∨ x ∈ marks := by
  by_cases h : mark ∈ marks
  · simp only [insertMark, if_pos h]
    constructor
    · exact Or.inr
    · intro hx; rcases hx with rfl | hx; exact h; exact hx
  · simp [insertMark, h]

theorem decode_cons (row : Row) (rows : SupplyMap) :
    decode (row :: rows) = row.marks.map (⟨row.region, ·⟩) ++ decode rows := rfl

theorem mem_row_atoms (x : Atom) (row : Row) :
    x ∈ row.marks.map (⟨row.region, ·⟩) ↔
      x.region = row.region ∧ x.mark ∈ row.marks := by
  constructor
  · intro h
    rcases List.mem_map.mp h with ⟨mark, hm, eq⟩
    rw [← eq]
    exact ⟨rfl, hm⟩
  · intro ⟨hr, hm⟩
    exact List.mem_map.mpr ⟨x.mark, hm, by cases x; simp_all⟩

theorem atom_eq_iff (x a : Atom) : x = a ↔ x.region = a.region ∧ x.mark = a.mark := by
  cases x; cases a; simp

theorem mem_decode_add (x atom : Atom) (rows : SupplyMap) :
    x ∈ decode (add atom rows) ↔ x = atom ∨ x ∈ decode rows := by
  induction rows with
  | nil => simp [add, decode, atom_eq_iff]
  | cons row rest ih =>
    by_cases h : row.region = atom.region
    · simp only [add, if_pos h, decode_cons, List.mem_append]
      rw [mem_row_atoms x { row with marks := insertMark atom.mark row.marks },
        mem_row_atoms x row]
      change (x.region = row.region ∧ x.mark ∈ insertMark atom.mark row.marks ∨
        x ∈ decode rest) ↔
        x = atom ∨ (x.region = row.region ∧ x.mark ∈ row.marks ∨ x ∈ decode rest)
      rw [mem_insertMark, atom_eq_iff]
      simp only [← h, and_or_left, or_assoc]
    · simp only [add, if_neg h, decode_cons, List.mem_append]
      rw [ih]
      simp only [or_left_comm]

theorem mem_decode_fold (x : Atom) (atoms : List Atom) (initial : SupplyMap) :
    x ∈ decode (atoms.foldl (fun rows atom => add atom rows) initial) ↔
      x ∈ atoms ∨ x ∈ decode initial := by
  induction atoms generalizing initial with
  | nil => simp
  | cons a rest ih =>
      simp only [List.foldl_cons, ih, mem_decode_add, List.mem_cons]
      simp [or_assoc, or_left_comm]

theorem decoded_exact (x : Atom) (atoms : List Atom) :
    x ∈ decode (merge atoms) ↔ x ∈ atoms := by
  unfold merge
  rw [mem_decode_fold]
  simp [decode]

theorem no_invented_FULL (atoms : List Atom) (atom : Atom)
    (member : atom ∈ decode (merge atoms)) : atom ∈ atoms :=
  (decoded_exact atom atoms).mp member

theorem no_lost_region_or_atom (atoms : List Atom) (atom : Atom)
    (member : atom ∈ atoms) : atom ∈ decode (merge atoms) :=
  (decoded_exact atom atoms).mpr member

/-- Every row has a unique region; every member within a row is unique. -/
def WellFormed (rows : SupplyMap) : Prop :=
  (rows.map Row.region).Nodup ∧ ∀ row ∈ rows, row.marks.Nodup

theorem insertMark_nodup (mark : FullMark) (marks : List FullMark)
    (h : marks.Nodup) : (insertMark mark marks).Nodup := by
  by_cases hm : mark ∈ marks <;> simp [insertMark, hm, h]

theorem add_row_origin (atom : Atom) (rows : SupplyMap) (row : Row)
    (member : row ∈ add atom rows) : row.region = atom.region ∨ row ∈ rows := by
  induction rows with
  | nil => simp_all [add]
  | cons first rest ih =>
    by_cases h : first.region = atom.region
    · simp only [add, if_pos h, List.mem_cons] at member
      rcases member with rfl | hm
      · exact Or.inl h
      · exact Or.inr (by simp [hm])
    · simp only [add, if_neg h, List.mem_cons] at member
      rcases member with rfl | hm
      · exact Or.inr (by simp)
      · rcases ih hm with he | he
        · exact Or.inl he
        · exact Or.inr (by simp [he])

theorem add_wellFormed (atom : Atom) (rows : SupplyMap) (h : WellFormed rows) :
    WellFormed (add atom rows) := by
  induction rows with
  | nil => simp [WellFormed, add]
  | cons row rest ih =>
    rcases h with ⟨hk, hm⟩
    have hrest : WellFormed rest := ⟨(List.nodup_cons.mp hk).2,
      fun r hr => hm r (by simp [hr])⟩
    by_cases heq : row.region = atom.region
    · constructor
      · simpa only [add, if_pos heq, List.map_cons] using hk
      · intro r hr
        simp only [add, if_pos heq, List.mem_cons] at hr
        rcases hr with rfl | hr
        · exact insertMark_nodup _ _ (hm row (by simp))
        · exact hm r (by simp [hr])
    · have hi := ih hrest
      constructor
      · simp only [add, if_neg heq, List.map_cons, List.nodup_cons]
        constructor
        · intro hmem
          rcases List.mem_map.mp hmem with ⟨r, hr, eq⟩
          rcases add_row_origin atom rest r hr with ha | hr
          · exact heq (eq.symm.trans ha)
          · exact (List.nodup_cons.mp hk).1 (List.mem_map.mpr ⟨r, hr, eq⟩)
        · exact hi.1
      · intro r hr
        simp only [add, if_neg heq, List.mem_cons] at hr
        rcases hr with he | hr
        · rw [he]; exact hm row (by simp)
        · exact hi.2 r hr

theorem merge_wellFormed (atoms : List Atom) : WellFormed (merge atoms) := by
  suffices ∀ rows, WellFormed rows →
      WellFormed (atoms.foldl (fun rs atom => add atom rs) rows) from
    this [] (by simp [WellFormed])
  induction atoms with
  | nil => simp
  | cons atom rest ih =>
    intro rows h; exact ih _ (add_wellFormed atom rows h)

theorem row_atoms_nodup (row : Row) (h : row.marks.Nodup) :
    (row.marks.map (⟨row.region, ·⟩ : FullMark → Atom)).Nodup := by
  exact List.Pairwise.map _ (fun _ _ distinct equal => distinct (congrArg Atom.mark equal)) h

theorem decode_nodup (rows : SupplyMap) (h : WellFormed rows) :
    (decode rows).Nodup := by
  induction rows with
  | nil => simp [decode]
  | cons row rest ih =>
    rcases h with ⟨hk, hm⟩
    have hr : WellFormed rest := ⟨(List.nodup_cons.mp hk).2,
      fun r hp => hm r (by simp [hp])⟩
    rw [decode_cons, List.nodup_append]
    refine ⟨row_atoms_nodup row (hm row (by simp)), ih hr, ?_⟩
    intro a ha b hb equal
    have ha' := (mem_row_atoms a row).mp ha
    rcases List.mem_flatMap.mp hb with ⟨r, hin, hmap⟩
    have hb' := (mem_row_atoms b r).mp hmap
    apply (List.nodup_cons.mp hk).1
    apply List.mem_map.mpr
    refine ⟨r, hin, ?_⟩
    exact hb'.1.symm.trans ((congrArg Atom.region equal).symm.trans ha'.1)

theorem decoded_no_duplicates (atoms : List Atom) :
    (decode (merge atoms)).Nodup := decode_nodup _ (merge_wellFormed atoms)

theorem insertMark_length_le (mark : FullMark) (marks : List FullMark) :
    (insertMark mark marks).length ≤ marks.length + 1 := by
  by_cases present : mark ∈ marks <;> simp [insertMark, present]

theorem add_membership_cost (atom : Atom) (rows : SupplyMap) :
    (decode (add atom rows)).length ≤ (decode rows).length + 1 := by
  induction rows with
  | nil => simp [add, decode]
  | cons row rest ih =>
    have markBound := insertMark_length_le atom.mark row.marks
    by_cases same : row.region = atom.region
    · simp only [add, if_pos same, decode_cons, List.length_append, List.length_map]
      omega
    · simp only [add, if_neg same, decode_cons, List.length_append, List.length_map]
      omega

theorem fold_membership_cost (atoms : List Atom) (initial : SupplyMap) :
    (decode (atoms.foldl (fun rows atom => add atom rows) initial)).length ≤
      (decode initial).length + atoms.length := by
  induction atoms generalizing initial with
  | nil => simp
  | cons a rest ih =>
    have tailBound := ih (add a initial)
    have headBound := add_membership_cost a initial
    simp only [List.foldl_cons, List.length_cons]
    omega

/-- Constructive exact membership bound. A generic subset/cardinality theorem
    is intentionally avoided because its library proof uses classical choice. -/
theorem membership_cost_never_grows (atoms : List Atom) :
    (decode (merge atoms)).length ≤ atoms.length := by
  simpa only [merge, decode, List.flatMap_nil, List.length_nil, Nat.zero_add] using
    fold_membership_cost atoms []

theorem insertMark_idempotent (mark : FullMark) (marks : List FullMark) :
    insertMark mark (insertMark mark marks) = insertMark mark marks := by
  have h : mark ∈ insertMark mark marks := (mem_insertMark mark mark marks).mpr (Or.inl rfl)
  exact if_pos h

theorem add_idempotent (atom : Atom) (rows : SupplyMap) :
    add atom (add atom rows) = add atom rows := by
  induction rows with
  | nil => simp [add, insertMark]
  | cons row rest ih =>
    by_cases same : row.region = atom.region
    · simp [add, same, insertMark_idempotent]
    · simp [add, same, ih]

theorem repeated_fold (atom : Atom) (n : Nat) (initial : SupplyMap) :
    (List.replicate (n+1) atom).foldl (fun rows atom => add atom rows) initial =
      add atom initial := by
  induction n generalizing initial with
  | zero => simp
  | succ n ih =>
    rw [List.replicate_succ, List.foldl_cons, ih, add_idempotent]

theorem repeat_cost_one (atom : Atom) (n : Nat) :
    (decode (merge (List.replicate (n+1) atom))).length = 1 := by
  simp only [merge, repeated_fold]
  simp [add, decode]

theorem repeated_work_strictly_reduced (atom : Atom) (n : Nat) (positive : 0 < n) :
    (decode (merge (List.replicate (n+1) atom))).length <
      (List.replicate (n+1) atom).length := by
  rw [repeat_cost_one, List.length_replicate]
  omega

/-- The measured two-batch arithmetic is a bound on pending memberships only.
    It is not a theorem about total engine time or process retained heap. -/
theorem measured_duplicate_memberships : 1769192 - 689 = 1768503 := by decide

def CanonicalAtoms (atoms : List Atom) : Prop :=
  ∀ atom ∈ atoms, atom.region.canonical = true

theorem canonical_merge_rows (atoms : List Atom) (legal : CanonicalAtoms atoms)
    (row : Row) (member : row ∈ merge atoms) : row.region.canonical = true := by
  have invariant : ∀ (initial : SupplyMap),
      (∀ r ∈ initial, r.region.canonical = true) →
      (∀ r ∈ atoms.foldl (fun rows atom => add atom rows) initial,
        r.region.canonical = true) := by
    clear row member
    induction atoms with
    | nil => intro initial h; exact h
    | cons atom rest ih =>
      intro initial initialLegal
      apply ih (fun a ha => legal a (by simp [ha]))
      intro r hr
      rcases add_row_origin atom initial r hr with same | old
      · rw [same]; exact legal atom (by simp)
      · exact initialLegal r old
  exact invariant [] (by simp) row member

def NonemptyRows (rows : SupplyMap) : Prop := ∀ row ∈ rows, row.marks ≠ []

theorem insertMark_nonempty (mark : FullMark) (marks : List FullMark) :
    insertMark mark marks ≠ [] := by
  intro h
  have member := (mem_insertMark mark mark marks).mpr (Or.inl rfl)
  rw [h] at member; simp at member

theorem add_nonempty_rows (atom : Atom) (rows : SupplyMap)
    (nonempty : NonemptyRows rows) : NonemptyRows (add atom rows) := by
  induction rows with
  | nil => simp [NonemptyRows, add]
  | cons row rest ih =>
    have restNonempty : NonemptyRows rest := fun r hr => nonempty r (by simp [hr])
    by_cases same : row.region = atom.region
    · intro r hr
      simp only [add, if_pos same, List.mem_cons] at hr
      rcases hr with eq | hr
      · rw [eq]; exact insertMark_nonempty _ _
      · exact restNonempty r hr
    · intro r hr
      simp only [add, if_neg same, List.mem_cons] at hr
      rcases hr with eq | hr
      · rw [eq]; exact nonempty row (by simp)
      · exact ih restNonempty r hr

theorem merge_nonempty_rows (atoms : List Atom) : NonemptyRows (merge atoms) := by
  suffices ∀ initial, NonemptyRows initial →
      NonemptyRows (atoms.foldl (fun rows atom => add atom rows) initial) from
    this [] (by simp [NonemptyRows])
  induction atoms with
  | nil => intro initial h; exact h
  | cons atom rest ih =>
    intro initial h; exact ih _ (add_nonempty_rows atom initial h)

theorem rows_le_memberships (rows : SupplyMap) (nonempty : NonemptyRows rows) :
    rows.length ≤ (decode rows).length := by
  induction rows with
  | nil => simp [decode]
  | cons row rest ih =>
    have restNonempty : NonemptyRows rest := fun r hr => nonempty r (by simp [hr])
    have rNonempty := nonempty row (by simp)
    have positive : 0 < row.marks.length := List.length_pos_iff.mpr rNonempty
    have restBound := ih restNonempty
    simp only [List.length_cons, decode_cons, List.length_append, List.length_map]
    omega

theorem region_row_cost_never_grows (atoms : List Atom) :
    (merge atoms).length ≤ atoms.length :=
  Nat.le_trans (rows_le_memberships _ (merge_nonempty_rows atoms)) (membership_cost_never_grows atoms)

/-- Supply admission retains full geometry. Only the existing ANSWER uses
    canonical named-field STAR or the unchanged exact class marker. -/
def answerRegion (slot : Slot) : Region := match slot with
  | .field c f => ⟨.field c f, [], .star, []⟩
  | .exactClass c => ⟨.exactClass c, [], .exact, []⟩

theorem answer_same_slot (slot : Slot) : (answerRegion slot).slot = slot := by
  cases slot <;> rfl

theorem exact_class_is_not_field (c d f : Nat) :
    answerRegion (.exactClass c) ≠ answerRegion (.field d f) := by
  intro h; have := congrArg Region.slot h; simp [answerRegion] at this

theorem canonical_exact_class_answer_unchanged (c : Nat) (r : Region)
    (slot : r.slot = .exactClass c) (legal : r.canonical = true) :
    answerRegion r.slot = r := by
  rcases r with ⟨rslot,suffix,tail,exclusion⟩
  cases slot
  simp only [Region.canonical, Bool.and_eq_true, List.isEmpty_iff,
    decide_eq_true_eq] at legal
  rcases legal with ⟨emptyE,emptySuffix,exactTail⟩
  subst exclusion; subst suffix; subst tail; rfl

structure Membership where
  requested : Bool := false
  known : Bool := false
  deriving DecidableEq, Repr

def req (s : Membership) := s.requested && !s.known
def reach (s : Membership) := !s.requested && s.known
def processed (s : Membership) := s.requested && s.known

theorem three_sets_disjoint (s : Membership) :
    (req s && reach s) = false ∧ (req s && processed s) = false ∧
      (reach s && processed s) = false := by
  cases s with | mk a b => cases a <;> cases b <;> decide

theorem requested_partition (s : Membership) :
    (req s || processed s) = s.requested := by
  cases s with | mk a b => cases a <;> cases b <;> decide

theorem known_partition (s : Membership) :
    (reach s || processed s) = s.known := by
  cases s with | mk a b => cases a <;> cases b <;> decide

structure AtomLive where
  membership : Membership := {}
  pending : Nat → Bool := fun _ => false
  writers : Nat → Bool := fun _ => false
  admitted : Nat → Bool := fun _ => false
structure AtomFrame where
  live : AtomLive := {}
  /-- GHOST trace, not a retained answered-request store. -/
  answers : Nat → Bool := fun _ => false
  writerEffects : Nat → Bool := fun _ => false

def AtomFrame.supply (s : AtomFrame) : AtomFrame :=
  if s.live.membership.known then s else
  { s with
    live := { s.live with
      membership := { s.live.membership with known := true }
      pending := fun _ => false }
    answers := fun id => s.answers id || s.live.pending id }

def AtomFrame.request (s : AtomFrame) (point : Nat) : AtomFrame :=
  let fresh := !s.live.membership.requested
  { live := { s.live with
      membership := { s.live.membership with requested := true }
      pending := fun id => s.live.pending id ||
        (decide (id = point) && !s.live.membership.known)
      admitted := fun id => s.live.admitted id || (fresh && s.live.writers id) }
    answers := fun id => s.answers id ||
      (decide (id = point) && s.live.membership.known)
    writerEffects := fun id => s.writerEffects id ||
      (fresh && s.live.writers id && !s.live.admitted id) }

def AtomFrame.writer (s : AtomFrame) (writer : Nat) : AtomFrame :=
  if s.live.writers writer then s else
  { s with
    live := { s.live with
      writers := fun id => s.live.writers id || decide (id = writer)
      admitted := fun id => s.live.admitted id ||
        (decide (id = writer) && s.live.membership.requested) }
    writerEffects := fun id => s.writerEffects id ||
      (decide (id = writer) && s.live.membership.requested && !s.live.admitted id) }

theorem request_does_not_supply (s : AtomFrame) (point : Nat) :
    (s.request point).live.membership.known = s.live.membership.known := rfl

theorem writer_does_not_supply (s : AtomFrame) (writer : Nat) :
    (s.writer writer).live.membership.known = s.live.membership.known := by
  unfold AtomFrame.writer; split <;> rfl

theorem supply_idempotent (s : AtomFrame) : s.supply.supply = s.supply := by
  cases h : s.live.membership.known <;> simp [AtomFrame.supply, h]

theorem supply_does_not_request (s : AtomFrame) :
    s.supply.live.membership.requested = s.live.membership.requested := by
  cases h : s.live.membership.known <;> simp [AtomFrame.supply, h]

theorem supply_never_asks_writer (s : AtomFrame) :
    s.supply.live.admitted = s.live.admitted ∧
      s.supply.writerEffects = s.writerEffects := by
  cases h : s.live.membership.known <;> simp [AtomFrame.supply, h]

theorem fresh_supply_removes_pending (s : AtomFrame)
    (fresh : s.live.membership.known = false) (point : Nat) :
    s.supply.live.pending point = false ∧
      s.supply.answers point = (s.answers point || s.live.pending point) := by
  simp [AtomFrame.supply, fresh]

theorem reached_request_answers_without_pending (s : AtomFrame) (point : Nat)
    (known : s.live.membership.known = true)
    (absent : s.live.pending point = false) :
    (s.request point).answers point = true ∧
      (s.request point).live.pending point = false := by
  simp [AtomFrame.request, known, absent]

theorem late_writer_gets_processed_request (s : AtomFrame) (writer : Nat)
    (asked : s.live.membership.requested = true)
    (newWriter : s.live.writers writer = false) :
    (s.writer writer).live.admitted writer = true := by
  simp [AtomFrame.writer, asked, newWriter]

/-- One pointwise (reader,complete overlap) projection. `written` projects the
    old write index; `delivered` is the read row's old dedup membership. -/
structure ReadCell where
  registered : Bool := false
  written : Bool := false
  delivered : Bool := false
  deriving DecidableEq, Repr

def ReadCell.write (s : ReadCell) : ReadCell :=
  { s with written := true, delivered := s.delivered || s.registered }
def ReadCell.register (s : ReadCell) : ReadCell :=
  { s with registered := true, delivered := s.delivered || s.written }

theorem read_write_idempotent (s : ReadCell) : s.write.write = s.write := by
  cases s with | mk a b c => cases a <;> cases b <;> cases c <;> decide

theorem late_read_and_late_write (s : ReadCell) :
    s.write.register = s.register.write := by
  cases s with | mk a b c => cases a <;> cases b <;> cases c <;> decide

abbrev AtomKey := Slot × FullMark
abbrev ReadKey := Nat × Region
structure Service where
  atoms : AtomKey → AtomFrame := fun _ => {}
  reads : ReadKey → ReadCell := fun _ => {}

/-- Existing deterministic COMPLETE AP intersection. Do not replace it by a
    field-only match or a guessed descendant field. -/
theorem Service.ext {s t : Service} (atoms : s.atoms = t.atoms)
    (reads : s.reads = t.reads) : s = t := by
  cases s; cases t; cases atoms; cases reads; rfl

structure SpatialAdapter where
  overlap : Nat → Region → Option Region

def deliver (spatial : SpatialAdapter) (atom : Atom) (s : Service) : Service :=
  { atoms := fun key => if key = (atom.region.slot, atom.mark)
      then (s.atoms key).supply else s.atoms key
    reads := fun key => if spatial.overlap key.1 atom.region = some key.2
      then (s.reads key).write else s.reads key }

/-- Direct current packet transition. `groupWrite` unions fresh FULL members,
    answers/removes each matching pending membership, and `admitWrite` activates
    a read once when the packet is nonempty. This is separate from flattening. -/
def deliverPacket (spatial : SpatialAdapter) (packet : Packet) (s : Service) : Service :=
  { atoms := fun key =>
      if key.1 = packet.region.slot ∧ key.2 ∈ packet.marks
      then (s.atoms key).supply else s.atoms key
    reads := fun key =>
      if packet.marks ≠ [] ∧ spatial.overlap key.1 packet.region = some key.2
      then (s.reads key).write else s.reads key }

theorem empty_packet_no_effect (spatial : SpatialAdapter) (region : Region)
    (s : Service) : deliverPacket spatial ⟨region,[]⟩ s = s := by
  apply Service.ext <;> funext key <;> simp [deliverPacket]

theorem packet_cons_local_correspondence (spatial : SpatialAdapter)
    (region : Region) (mark : FullMark) (marks : List FullMark) (s : Service) :
    deliver spatial ⟨region,mark⟩ (deliverPacket spatial ⟨region,marks⟩ s) =
      deliverPacket spatial ⟨region,mark :: marks⟩ s := by
  apply Service.ext
  · funext key
    rcases key with ⟨slot,atom⟩
    by_cases hs : slot = region.slot
    · subst slot
      by_cases ha : atom = mark
      · subst atom
        by_cases hm : mark ∈ marks <;>
          simp [deliver, deliverPacket, hm, supply_idempotent]
      · simp [deliver, deliverPacket, ha]
    · simp [deliver, deliverPacket, hs]
  · funext key
    by_cases overlap : spatial.overlap key.1 region = some key.2
    · cases marks <;> simp [deliver, deliverPacket, overlap, read_write_idempotent]
    · simp [deliver, deliverPacket, overlap]

theorem delivery_cannot_supply_other_slot_or_FULL (spatial : SpatialAdapter)
    (atom : Atom) (s : Service) (key : AtomKey)
    (different : key ≠ (atom.region.slot,atom.mark)) :
    (deliver spatial atom s).atoms key = s.atoms key := by
  simp [deliver, different]

theorem delivery_idempotent (spatial : SpatialAdapter) (atom : Atom) (s : Service) :
    deliver spatial atom (deliver spatial atom s) = deliver spatial atom s := by
  apply Service.ext
  · funext key; simp only [deliver]
    split <;> simp_all [supply_idempotent]
  · funext key; simp only [deliver]
    split <;> simp_all [read_write_idempotent]

theorem deliveries_commute (spatial : SpatialAdapter) (a b : Atom) (s : Service) :
    deliver spatial a (deliver spatial b s) = deliver spatial b (deliver spatial a s) := by
  apply Service.ext
  · funext key; simp only [deliver]
    split <;> split <;> simp_all [supply_idempotent]
  · funext key; simp only [deliver]
    split <;> split <;> simp_all [read_write_idempotent]

section FiniteAction
variable {A S : Type} (step : A → S → S)
def execute (atoms : List A) (s : S) : S := atoms.foldr step s

theorem execute_cons (a : A) (xs : List A) (s : S) :
    execute step (a :: xs) s = step a (execute step xs s) := rfl

theorem execute_append (xs ys : List A) (s : S) :
    execute step (xs ++ ys) s = execute step xs (execute step ys s) := by
  simp [execute, List.foldr_append]

theorem step_execute (commute : ∀ a b s, step a (step b s) = step b (step a s))
    (a : A) (xs : List A) (s : S) :
    step a (execute step xs s) = execute step xs (step a s) := by
  induction xs with
  | nil => rfl
  | cons x xs ih => simpa only [execute, List.foldr_cons] using
      (commute a x (execute step xs s)).trans (congrArg (step x) ih)

theorem execute_commute (commute : ∀ a b s, step a (step b s) = step b (step a s))
    (xs ys : List A) (s : S) :
    execute step xs (execute step ys s) = execute step ys (execute step xs s) := by
  induction xs with
  | nil => rfl
  | cons a xs ih =>
    simp only [execute_cons]
    rw [ih, step_execute step commute]

theorem execute_absorbs_member
    (idem : ∀ a s, step a (step a s) = step a s)
    (commute : ∀ a b s, step a (step b s) = step b (step a s))
    (a : A) (xs : List A) (s : S) (member : a ∈ xs) :
    step a (execute step xs s) = execute step xs s := by
  induction xs with
  | nil => simp at member
  | cons x xs ih =>
    rcases List.mem_cons.mp member with rfl | member
    · exact idem a (execute step xs s)
    · simp only [execute_cons]
      rw [commute, ih member]

theorem execute_absorbs_subset
    (idem : ∀ a s, step a (step a s) = step a s)
    (commute : ∀ a b s, step a (step b s) = step b (step a s))
    (xs ys : List A) (s : S) (subset : ∀ a ∈ xs, a ∈ ys) :
    execute step xs (execute step ys s) = execute step ys s := by
  induction xs with
  | nil => rfl
  | cons a xs ih =>
    simp only [execute_cons]
    rw [ih (fun x hx => subset x (by simp [hx]))]
    exact execute_absorbs_member step idem commute a ys s (subset a (by simp))

theorem execute_same_members
    (idem : ∀ a s, step a (step a s) = step a s)
    (commute : ∀ a b s, step a (step b s) = step b (step a s))
    (xs ys : List A) (s : S) (same : ∀ a, a ∈ xs ↔ a ∈ ys) :
    execute step xs s = execute step ys s := by
  have left := execute_absorbs_subset step idem commute xs ys s
    (fun a ha => (same a).mp ha)
  have right := execute_absorbs_subset step idem commute ys xs s
    (fun a ha => (same a).mpr ha)
  exact right.symm.trans ((execute_commute step commute ys xs s).trans left)
end FiniteAction

/-- The actual service for-loop uses chronological foldl. Commutation connects
    that order to the foldr used by the generic finite-action proof above. -/
def executeForward {A S : Type} (step : A → S → S) (atoms : List A) (s : S) : S :=
  atoms.foldl (fun current atom => step atom current) s

theorem forward_cons {A S : Type} (step : A → S → S) (a : A)
    (atoms : List A) (s : S) :
    executeForward step (a :: atoms) s = executeForward step atoms (step a s) := rfl

theorem forward_append {A S : Type} (step : A → S → S)
    (xs ys : List A) (s : S) :
    executeForward step (xs ++ ys) s = executeForward step ys (executeForward step xs s) := by
  simp [executeForward, List.foldl_append]

theorem forward_eq_execute {A S : Type} (step : A → S → S)
    (commute : ∀ a b s, step a (step b s) = step b (step a s))
    (atoms : List A) (s : S) : executeForward step atoms s = execute step atoms s := by
  induction atoms generalizing s with
  | nil => rfl
  | cons atom rest ih =>
    change executeForward step rest (step atom s) = step atom (execute step rest s)
    rw [ih]
    exact (step_execute step commute atom rest s).symm

theorem projected_supply_commit_equal (spatial : SpatialAdapter)
    (atoms : List Atom) (s : Service) :
    execute (deliver spatial) (decode (merge atoms)) s =
      execute (deliver spatial) atoms s := by
  exact execute_same_members _ (delivery_idempotent spatial)
    (deliveries_commute spatial) _ _ s (fun a => decoded_exact a atoms)

theorem packet_atomic_correspondence (spatial : SpatialAdapter)
    (packet : Packet) (s : Service) :
    deliverPacket spatial packet s = executeForward (deliver spatial) (expand packet) s := by
  rw [forward_eq_execute _ (deliveries_commute spatial)]
  rcases packet with ⟨region,marks⟩
  induction marks with
  | nil => exact empty_packet_no_effect spatial region s
  | cons mark marks ih =>
    change deliverPacket spatial ⟨region,mark :: marks⟩ s =
      deliver spatial ⟨region,mark⟩ (execute (deliver spatial)
        (expand ⟨region,marks⟩) s)
    rw [← ih]
    exact (packet_cons_local_correspondence spatial region mark marks s).symm

theorem packet_list_atomic_correspondence (spatial : SpatialAdapter)
    (packets : List Packet) (s : Service) :
    executeForward (deliverPacket spatial) packets s =
      executeForward (deliver spatial) (flatten packets) s := by
  induction packets generalizing s with
  | nil => rfl
  | cons packet rest ih =>
    rw [forward_cons, ih, packet_atomic_correspondence]
    change executeForward (deliver spatial) (flatten rest)
        (executeForward (deliver spatial) (expand packet) s) =
      executeForward (deliver spatial) (expand packet ++ flatten rest) s
    exact (forward_append _ _ _ _).symm

def rowPacket (row : Row) : Packet := ⟨row.region,row.marks⟩
def mergedPackets (packets : List Packet) : List Packet :=
  (merge (flatten packets)).map rowPacket

theorem flatten_row_packets (rows : SupplyMap) :
    flatten (rows.map rowPacket) = decode rows := by
  induction rows with
  | nil => rfl
  | cons row rest ih =>
    change expand (rowPacket row) ++ flatten (rest.map rowPacket) =
      row.marks.map (⟨row.region, ·⟩) ++ decode rest
    rw [ih]; rfl

/-- Runtime-facing gate: the actor selects this profile once for run-1 FULL
    and proves every bounded supply is in the selected canonical grammar. -/
theorem canonical_supply_commit_equal (spatial : SpatialAdapter)
    (atoms : List Atom) (_legal : CanonicalAtoms atoms) (s : Service) :
    executeForward (deliver spatial) (decode (merge atoms)) s =
      executeForward (deliver spatial) atoms s := by
  rw [forward_eq_execute _ (deliveries_commute spatial),
    forward_eq_execute _ (deliveries_commute spatial)]
  exact projected_supply_commit_equal spatial atoms s

/-- Complete old packet-list commit versus complete-region/mark-set commit.
    Direct bulk packet transitions, not equal final closures by definition. -/
theorem merged_packet_commit_equal (spatial : SpatialAdapter)
    (packets : List Packet) (legal : CanonicalAtoms (flatten packets)) (s : Service) :
    executeForward (deliverPacket spatial) (mergedPackets packets) s =
      executeForward (deliverPacket spatial) packets s := by
  rw [packet_list_atomic_correspondence, packet_list_atomic_correspondence]
  change executeForward (deliver spatial)
      (flatten ((merge (flatten packets)).map rowPacket)) s = _
  rw [flatten_row_packets]
  exact canonical_supply_commit_equal spatial (flatten packets) legal s

theorem packetization_independent (spatial : SpatialAdapter)
    (left right : List Packet) (_legal : CanonicalAtoms (flatten left)) (s : Service)
    (same : ∀ a, a ∈ flatten left ↔ a ∈ flatten right) :
    execute (deliver spatial) (decode (merge (flatten left))) s =
      execute (deliver spatial) (flatten right) s := by
  rw [projected_supply_commit_equal]
  exact execute_same_members _ (delivery_idempotent spatial)
    (deliveries_commute spatial) _ _ s same

/-- A late read, writer or request runs AFTER the locked lane. Congruence does
    not permit callback interleaving inside the reordered supply lane. -/
theorem later_event_equal (spatial : SpatialAdapter) (atoms : List Atom)
    (s : Service) (next : Service → Service) :
    next (execute (deliver spatial) (decode (merge atoms)) s) =
      next (execute (deliver spatial) atoms s) := by
  rw [projected_supply_commit_equal]

structure EarlierLanes where
  reads : Service → Service
  interests : Service → Service
  staticWriters : Service → Service
  flowWriters : Service → Service
  writePresence : Service → Service

def beforeSupply (lanes : EarlierLanes) (s : Service) : Service :=
  lanes.writePresence (lanes.flowWriters (lanes.staticWriters
    (lanes.interests (lanes.reads s))))

theorem whole_commit_equal (spatial : SpatialAdapter) (lanes : EarlierLanes)
    (atoms : List Atom) (legal : CanonicalAtoms atoms) (s : Service) :
    executeForward (deliver spatial) (decode (merge atoms)) (beforeSupply lanes s) =
      executeForward (deliver spatial) atoms (beforeSupply lanes s) :=
  canonical_supply_commit_equal spatial atoms legal (beforeSupply lanes s)

/-- Arbitrarily many later requests/writers/reads and batches are admitted at
    commit boundaries. The unchanged event functions may carry the old queues,
    ordinary edges and secured effects; they cannot run within the locked lane. -/
inductive BoundaryEvent where
  | ordinary (step : Service → Service)
  | batch (lanes : EarlierLanes) (atoms : List Atom) (legal : CanonicalAtoms atoms)

def oldEvent (spatial : SpatialAdapter) : BoundaryEvent → Service → Service
  | .ordinary step, s => step s
  | .batch lanes atoms _, s =>
      executeForward (deliver spatial) atoms (beforeSupply lanes s)

def newEvent (spatial : SpatialAdapter) : BoundaryEvent → Service → Service
  | .ordinary step, s => step s
  | .batch lanes atoms _, s =>
      executeForward (deliver spatial) (decode (merge atoms)) (beforeSupply lanes s)

theorem boundary_event_equal (spatial : SpatialAdapter) (event : BoundaryEvent)
    (s : Service) : newEvent spatial event s = oldEvent spatial event s := by
  cases event with
  | ordinary => rfl
  | batch lanes atoms legal => exact whole_commit_equal spatial lanes atoms legal s

theorem finite_boundary_trace_equal (spatial : SpatialAdapter)
    (events : List BoundaryEvent) (s : Service) :
    events.foldl (fun current event => newEvent spatial event current) s =
      events.foldl (fun current event => oldEvent spatial event current) s := by
  simp only [boundary_event_equal]

/- RAW_API keeps LIST order. At the same class path, an EXACT interest
   intersects either supply at EXACT and UNIONs E. Empty E is essential. -/
structure RawRegion where
  tail : Tail
  exclusion : List Nat
  deriving DecidableEq, Repr
structure RawSupply where
  region : RawRegion
  mark : FullMark
  deriving DecidableEq, Repr

/-- Existing equal-path intersect with an empty-E EXACT class interest. -/
def rawOverlap (supply : RawRegion) : RawRegion :=
  ⟨.exact, supply.exclusion⟩

def rawFirstAnswer (wanted : FullMark) (supplies : List RawSupply) : Option RawRegion :=
  (supplies.find? (fun supply => decide (supply.mark = wanted))).map
    (fun supply => rawOverlap supply.region)

def rawP : RawRegion := ⟨.star, [4]⟩
def rawQ : RawRegion := ⟨.exact, []⟩

theorem raw_first_match_counterexample (t u : FullMark) (different : t ≠ u) :
    rawFirstAnswer u [⟨rawP,t⟩,⟨rawQ,u⟩,⟨rawP,u⟩] ≠
      rawFirstAnswer u [⟨rawP,t⟩,⟨rawP,u⟩,⟨rawQ,u⟩] := by
  simp [rawFirstAnswer, different, rawOverlap, rawP, rawQ]

inductive Profile where
  | canonicalRun1Full | orderedList
  deriving DecidableEq, Repr
inductive Temporary where
  | canonical (rows : SupplyMap)
  | ordered (packets : List RawSupply)
  deriving DecidableEq, Repr

def emptyTemporary : Profile → Temporary
  | .canonicalRun1Full => .canonical []
  | .orderedList => .ordered []
def profile : Temporary → Profile
  | .canonical _ => .canonicalRun1Full
  | .ordered _ => .orderedList

def replace (temporary : Temporary) : Temporary := emptyTemporary (profile temporary)

theorem replacement_same_profile (temporary : Temporary) :
    profile (replace temporary) = profile temporary := by cases temporary <;> rfl

def rawCommit (packets : List RawSupply) : List RawSupply := packets

theorem raw_compatibility (packets : List RawSupply) : rawCommit packets = packets := rfl

/- One counted reservation owns queue-empty commit, synchronous service
   update, replacement and secured sends. Replacement may precede securing, as
   in the current actor. Release needs BOTH. A quantum yield retains the batch. -/
structure Barrier where
  profile : Profile
  committed : Bool := false
  replaced : Bool := false
  secured : Bool := false
  counted : Bool := true
  deriving DecidableEq, Repr

def commitBarrier (b : Barrier) (queueEmpty : Bool) : Barrier :=
  if queueEmpty then { b with committed := true } else b

def secureBarrier (b : Barrier) : Barrier :=
  if b.committed then { b with secured := true } else b

def replaceBarrier (b : Barrier) : Barrier :=
  if b.committed then { b with replaced := true } else b

def releaseBarrier (b : Barrier) : Barrier :=
  if b.committed && b.replaced && b.secured then { b with counted := false } else b

theorem quantum_yield_retains_batch (b : Barrier) : commitBarrier b false = b := by
  simp [commitBarrier]

theorem commit_stays_counted (b : Barrier) (empty : Bool) :
    (commitBarrier b empty).counted = b.counted := by
  unfold commitBarrier; split <;> rfl

theorem no_release_before_secured (b : Barrier) (notSecured : b.secured = false) :
    releaseBarrier b = b := by simp [releaseBarrier, notSecured]

theorem no_release_before_replaced (b : Barrier) (notReplaced : b.replaced = false) :
    releaseBarrier b = b := by simp [releaseBarrier, notReplaced]

theorem counted_commit_sequence (p : Profile) :
    let b : Barrier := ⟨p, false, false, false, true⟩
    let committed := commitBarrier b true
    let replaced := replaceBarrier committed
    let secured := secureBarrier replaced
    committed.counted = true ∧ replaced.counted = true ∧ secured.counted = true ∧
      (releaseBarrier secured).counted = false ∧ secured.profile = p := by
  simp [commitBarrier, secureBarrier, replaceBarrier, releaseBarrier]

/- Existing deterministic finite adapter inventory, not an assumed final pool.
   Every ND premise member has a derivation; no representative-only shortcut. -/
inductive Observation where
  | answer (slot : Slot) (mark : FullMark) (point : Nat)
  | read (reader : Nat) (overlap : Region)
  | writer (slot : Slot) (mark : FullMark) (writer : Nat)
  | query (payload : Nat)
  | demand (payload : Nat)
  | ordinary (payload : Nat)
  deriving DecidableEq, Repr

def observes (s : Service) : Observation → Bool
  | .answer slot mark point => (s.atoms (slot, mark)).answers point
  | .read reader overlap => (s.reads (reader, overlap)).delivered
  | .writer slot mark writer => (s.atoms (slot, mark)).writerEffects writer
  | .query _ | .demand _ | .ordinary _ => false
structure AdapterRule where
  premises : List Observation
  conclusion : Observation
  deriving DecidableEq, Repr
inductive Derivation (base : Observation → Bool) (rules : List AdapterRule) :
    Observation → Type where
  | base (observation : Observation) (admitted : base observation = true) :
      Derivation base rules observation
  | transfer (rule : AdapterRule) (registered : rule ∈ rules)
      (allMembers : (premise : Observation) → premise ∈ rule.premises →
        Derivation base rules premise) : Derivation base rules rule.conclusion

def transport (left right : Observation → Bool) (rules : List AdapterRule)
    (same : ∀ observation, left observation = right observation)
    {observation : Observation} (proof : Derivation left rules observation) :
    Derivation right rules observation :=
  match proof with
  | .base observation admitted => .base observation (by rw [← same]; exact admitted)
  | .transfer rule registered members => .transfer rule registered
      (fun premise member => transport left right rules same (members premise member))

def query_demand_witness (spatial : SpatialAdapter) (atoms : List Atom)
    (legal : CanonicalAtoms atoms) (s : Service) (rules : List AdapterRule)
    {observation : Observation}
    (proof : Derivation (observes (executeForward (deliver spatial) atoms s)) rules observation) :
    Derivation (observes (executeForward (deliver spatial) (decode (merge atoms)) s)) rules observation :=
  transport _ _ rules (fun observation => by rw [canonical_supply_commit_equal spatial atoms legal]) proof

namespace Controls

def t : FullMark := ⟨7, 10⟩
def u : FullMark := ⟨7, 11⟩
def fieldAny : Region := ⟨.field 1 2, [], .any, []⟩
def fieldExact : Region := ⟨.field 1 2, [], .exact, []⟩
def otherField : Region := ⟨.field 1 3, [], .any, []⟩
def marker : Region := ⟨.exactClass 1, [], .exact, []⟩
def a : Atom := ⟨fieldAny, t⟩
def b : Atom := ⟨fieldAny, u⟩
def c : Atom := ⟨fieldExact, t⟩

def controls : List Bool := [
  decide ((merge [a, b, a]).length = 1),
  decide ((decode (merge [a, b, a])).length = 2),
  decide ((merge (List.replicate 100 a)).length = 1),
  decide ((decode (merge (List.replicate 100 a))).length = 1),
  decide (a ∈ decode (merge [a, b, a])),
  decide (b ∈ decode (merge [a, b, a])),
  decide ((merge [a, c]).length = 2),
  decide (t ≠ u),
  fieldAny.canonical, marker.canonical,
  !({ marker with tail := .any }).canonical,
  !({ fieldAny with exclusion := [4] }).canonical,
  decide (answerRegion fieldAny.slot = {fieldAny with tail := .star}),
  decide (answerRegion marker.slot = marker),
  decide (rawFirstAnswer u [⟨rawP,t⟩,⟨rawQ,u⟩,⟨rawP,u⟩] = some rawQ),
  decide (rawFirstAnswer u [⟨rawP,t⟩,⟨rawP,u⟩,⟨rawQ,u⟩] = some ⟨.exact,[4]⟩),
  let state : AtomFrame := {}; !(state.supply.live.membership.requested),
  let state := ({} : AtomFrame).request 42; state.supply.answers 42,
  let state := ({} : AtomFrame).supply; (state.request 42).answers 42,
  let state := ({} : AtomFrame).request 42; !(state.supply.live.pending 42),
  let state := ({} : AtomFrame).writer 9; !state.supply.live.admitted 9,
  let state := (({} : AtomFrame).supply).request 42; (state.writer 9).live.admitted 9,
  decide ((({} : ReadCell).write).register.delivered = true),
  decide ((({} : ReadCell).register).write.delivered = true)
]
end Controls
end ApSpec.Run1TemporaryStaticSupplies
