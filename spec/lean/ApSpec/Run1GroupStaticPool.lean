/-
  A finite transport model for the run-1 shared static pool.
  A coordinate is an opaque, adapter-certified geometry key.
  A reader is already reached. A need is interest, not taint evidence.
  All local deliveries are DEMAND. No key has a remote original.
  This model does not prove AP geometry or JVM reachability.
-/
import Std
import ApSpec.RuleGroupMarks

namespace ApSpec.Run1GroupStaticPool
set_option autoImplicit false

abbrev Coordinate := Nat
abbrev Group := Nat

structure Atom where
  coordinate : Coordinate
  group : Group
  deriving DecidableEq, Repr

structure Reader where
  site : Nat
  coordinate : Coordinate
  deriving DecidableEq, Repr

inductive Layer where
  | normal | demand
  deriving DecidableEq, Repr

structure Delivery where
  reader : Reader
  group : Group
  layer : Layer
  deriving DecidableEq, Repr

/-- There is no remote original in this local delivery key. -/
def localDelivery (reader : Reader) (group : Group) : Delivery :=
  ⟨reader, group, .demand⟩

structure State where
  known : List Atom := []
  unknownFlow : List Coordinate := []
  needs : List Atom := []
  readers : List Reader := []
  deriving DecidableEq, Repr

/-- Scalar offers are a reference oracle. They can contain duplicates. -/
def append (a b : State) : State :=
  ⟨a.known ++ b.known, a.unknownFlow ++ b.unknownFlow,
   a.needs ++ b.needs, a.readers ++ b.readers⟩

/-- Each pool row keeps a union, not one row per offer or full mark. -/
def pack (s : State) : State :=
  ⟨s.known.eraseDups, s.unknownFlow.eraseDups,
   s.needs.eraseDups, s.readers.eraseDups⟩

def merge (a b : State) : State := pack (append a b)

/-- Unknown FLOW is supply metadata. A need selects a possible group. -/
def enabled (s : State) (reader : Reader) (group : Group) : Bool :=
  s.readers.contains reader &&
    (s.known.contains ⟨reader.coordinate, group⟩ ||
      (s.unknownFlow.contains reader.coordinate &&
       s.needs.contains ⟨reader.coordinate, group⟩))

/-- FLOW also reaches readers before a group is selected. This lets a
    local copy publish unknown supply at another static coordinate. -/
def flowEnabled (s : State) (reader : Reader) : Bool :=
  s.readers.contains reader && s.unknownFlow.contains reader.coordinate

def observed (s : State) (delivery : Delivery) : Bool :=
  delivery.layer == .demand && enabled s delivery.reader delivery.group

def SameAtoms (a b : State) : Prop :=
  (∀ atom, atom ∈ a.known ↔ atom ∈ b.known) ∧
  (∀ coordinate, coordinate ∈ a.unknownFlow ↔ coordinate ∈ b.unknownFlow) ∧
  (∀ atom, atom ∈ a.needs ↔ atom ∈ b.needs) ∧
  (∀ reader, reader ∈ a.readers ↔ reader ∈ b.readers)

def Equivalent (a b : State) : Prop :=
  (∀ reader group, enabled a reader group = enabled b reader group) ∧
  (∀ reader, flowEnabled a reader = flowEnabled b reader)

@[simp] theorem enabled_spec (s : State) (reader : Reader) (group : Group) :
    enabled s reader group = true ↔
      reader ∈ s.readers ∧
        (⟨reader.coordinate, group⟩ ∈ s.known ∨
          reader.coordinate ∈ s.unknownFlow ∧
            ⟨reader.coordinate, group⟩ ∈ s.needs) := by
  simp [enabled, Bool.and_eq_true, Bool.or_eq_true]

@[simp] theorem flow_enabled_spec (s : State) (reader : Reader) :
    flowEnabled s reader = true ↔
      reader ∈ s.readers ∧ reader.coordinate ∈ s.unknownFlow := by
  simp [flowEnabled, Bool.and_eq_true]

theorem same_atoms_refl (s : State) : SameAtoms s s := by
  exact ⟨fun _ => Iff.rfl, fun _ => Iff.rfl, fun _ => Iff.rfl, fun _ => Iff.rfl⟩

theorem same_atoms_trans {a b c : State}
    (h : SameAtoms a b) (k : SameAtoms b c) : SameAtoms a c := by
  exact ⟨fun x => (h.1 x).trans (k.1 x),
    fun x => (h.2.1 x).trans (k.2.1 x),
    fun x => (h.2.2.1 x).trans (k.2.2.1 x),
    fun x => (h.2.2.2 x).trans (k.2.2.2 x)⟩

theorem pack_preserves_atoms (s : State) : SameAtoms (pack s) s := by
  simp [SameAtoms, pack]

theorem same_atoms_observe {a b : State} (h : SameAtoms a b) : Equivalent a b := by
  constructor
  · intro reader group
    apply Bool.eq_iff_iff.mpr
    simp only [enabled_spec]
    rw [h.2.2.2, h.1, h.2.1, h.2.2.1]
  · intro reader
    apply Bool.eq_iff_iff.mpr
    simp only [flow_enabled_spec]
    rw [h.2.2.2, h.2.1]

theorem scalar_merged_equal (s : State) : Equivalent (pack s) s :=
  same_atoms_observe (pack_preserves_atoms s)

theorem merge_membership (a b : State) : SameAtoms (merge a b) (append a b) :=
  pack_preserves_atoms (append a b)

theorem append_congr {a b c d : State}
    (h : SameAtoms a b) (k : SameAtoms c d) :
    SameAtoms (append a c) (append b d) := by
  rcases h with ⟨hk, hf, hn, hr⟩
  rcases k with ⟨kk, kf, kn, kr⟩
  simp only [SameAtoms, append, List.mem_append]
  exact ⟨fun x => or_congr (hk x) (kk x),
    fun x => or_congr (hf x) (kf x),
    fun x => or_congr (hn x) (kn x),
    fun x => or_congr (hr x) (kr x)⟩

theorem merge_commutes (a b : State) : Equivalent (merge a b) (merge b a) := by
  apply same_atoms_observe
  simp [SameAtoms, merge, pack, append, or_comm]

theorem merge_batch_associative (a b c : State) :
    Equivalent (merge (merge a b) c) (merge a (merge b c)) := by
  apply same_atoms_observe
  simp [SameAtoms, merge, pack, append, or_assoc]

theorem duplicate_batch_same (s : State) : Equivalent (merge s s) s := by
  apply same_atoms_observe
  simp [SameAtoms, merge, pack, append]

theorem observe_never_normal (s : State) (reader : Reader) (group : Group) :
    observed s ⟨reader, group, .normal⟩ = false := by
  simp [observed]

theorem observe_local (s : State) (reader : Reader) (group : Group) :
    observed s (localDelivery reader group) = enabled s reader group := by
  simp [observed, localDelivery]

/-- This function returns the local DEMAND delivery and its enabled proof. -/
def knownWitness (s : State) (reader : Reader) (group : Group)
    (hr : reader ∈ s.readers)
    (hs : Atom.mk reader.coordinate group ∈ s.known) :
    { delivery : Delivery // observed s delivery = true } :=
  ⟨localDelivery reader group, by
    rw [observe_local, enabled_spec]
    exact ⟨hr, Or.inl hs⟩⟩

/-- A selected group can be possible only after FLOW supply exists. -/
def flowWitness (s : State) (reader : Reader) (group : Group)
    (hr : reader ∈ s.readers) (hf : reader.coordinate ∈ s.unknownFlow)
    (hn : Atom.mk reader.coordinate group ∈ s.needs) :
    { delivery : Delivery // observed s delivery = true } :=
  ⟨localDelivery reader group, by
    rw [observe_local, enabled_spec]
    exact ⟨hr, Or.inr ⟨hf, hn⟩⟩⟩

/-- The full identity is not retained in the pool atom. -/
def projectSupply (coordinate : Coordinate)
    (mark : RuleGroupMarks.OwnedMark) : Atom := ⟨coordinate, mark.group⟩

def scalarKnownWitness (s : State) (reader : Reader)
    (mark : RuleGroupMarks.OwnedMark)
    (hr : reader ∈ s.readers) (hs : projectSupply reader.coordinate mark ∈ s.known) :
    { delivery : Delivery // observed s delivery = true } :=
  knownWitness s reader mark.group hr hs

/-- Enumerate only groups already known or explicitly needed. -/
def deliverableGroups (s : State) (reader : Reader) : List Group :=
  ((s.known ++ s.needs).filterMap (fun atom =>
    if atom.coordinate = reader.coordinate then some atom.group else none)).eraseDups.filter
      (enabled s reader)

@[simp] theorem deliverable_group_iff (s : State) (reader : Reader) (group : Group) :
    group ∈ deliverableGroups s reader ↔ enabled s reader group = true := by
  constructor
  · intro hg
    exact (List.mem_filter.mp hg).2
  · intro hg
    apply List.mem_filter.mpr
    refine ⟨?_, hg⟩
    simp only [List.mem_eraseDups, List.mem_filterMap, List.mem_append]
    have witness : Atom.mk reader.coordinate group ∈ s.known ++ s.needs := by
      rcases (enabled_spec s reader group).mp hg with ⟨_, hk | ⟨_, hn⟩⟩
      · exact List.mem_append_left _ hk
      · exact List.mem_append_right _ hn
    exact ⟨⟨reader.coordinate, group⟩, List.mem_append.mp witness, by simp⟩

/-- A consumer keeps its delivered group set. Fresh work excludes it. -/
def freshGroups (s : State) (reader : Reader) (delivered : List Group) : List Group :=
  (deliverableGroups s reader).filter (fun group => !delivered.contains group)

def commitDelivered (s : State) (reader : Reader) (delivered : List Group) : List Group :=
  (delivered ++ deliverableGroups s reader).eraseDups

theorem fresh_group_iff (s : State) (reader : Reader) (delivered : List Group)
    (group : Group) : group ∈ freshGroups s reader delivered ↔
      enabled s reader group = true ∧ group ∉ delivered := by
  simp [freshGroups]

theorem fresh_after_commit_empty (s : State) (reader : Reader) (delivered : List Group) :
    freshGroups s reader (commitDelivered s reader delivered) = [] := by
  cases heq : freshGroups s reader (commitDelivered s reader delivered) with
  | nil => rfl
  | cons group tail =>
    have hg : group ∈ freshGroups s reader (commitDelivered s reader delivered) := by
      rw [heq]
      exact List.mem_cons_self
    have h := (fresh_group_iff s reader _ group).mp hg
    exfalso
    apply h.2
    simp only [commitDelivered, List.mem_eraseDups, List.mem_append]
    exact Or.inr ((deliverable_group_iff s reader group).mpr h.1)

theorem full_marks_same_group_same_atom (coordinate : Coordinate)
    (a b : RuleGroupMarks.OwnedMark) (h : a.group = b.group) :
    projectSupply coordinate a = projectSupply coordinate b := by
  simp [projectSupply, h]

/-- A nonempty repeated offer uses one stored known-supply atom. -/
theorem repeated_offer_one_atom (atom : Atom) (extra : Nat) :
    (pack { known := List.replicate (extra + 1) atom }).known = [atom] := by
  have hf : (List.replicate extra atom).filter (fun b => !b == atom) = [] := by
    induction extra with
    | zero => rfl
    | succ n ih =>
      simp only [List.replicate_succ, List.filter_cons, BEq.rfl, Bool.not_true,
        Bool.false_eq_true, ↓reduceIte, ih]
  simp only [pack, List.replicate_succ, List.eraseDups_cons, hf, List.eraseDups_nil]

/-- Each coordinate has one merged row in the indexed implementation. -/
structure Row where
  knownGroups : List Group
  unknownFlow : Bool
  neededGroups : List Group
  readers : List Reader
  deriving DecidableEq, Repr

def groupsAt (atoms : List Atom) (coordinate : Coordinate) : List Group :=
  (atoms.filter (fun atom => atom.coordinate == coordinate)).map Atom.group

@[simp] theorem groups_at_member (atoms : List Atom) (coordinate : Coordinate)
    (group : Group) : group ∈ groupsAt atoms coordinate ↔
      Atom.mk coordinate group ∈ atoms := by
  simp only [groupsAt, List.mem_map, List.mem_filter, beq_iff_eq]
  constructor
  · rintro ⟨atom, ⟨ha, hc⟩, hg⟩
    cases atom with
    | mk ac ag => simp_all
  · intro ha
    exact ⟨⟨coordinate, group⟩, ⟨ha, rfl⟩, rfl⟩

def indexRow (s : State) (coordinate : Coordinate) : Row :=
  ⟨(groupsAt s.known coordinate).eraseDups,
    s.unknownFlow.contains coordinate,
    (groupsAt s.needs coordinate).eraseDups,
    (s.readers.filter (fun reader => reader.coordinate == coordinate)).eraseDups⟩

def indexedEnabled (row : Row) (reader : Reader) (group : Group) : Bool :=
  row.readers.contains reader &&
    (row.knownGroups.contains group ||
      (row.unknownFlow && row.neededGroups.contains group))

theorem indexed_scalar_equal (s : State) (reader : Reader) (group : Group) :
    indexedEnabled (indexRow s reader.coordinate) reader group =
      enabled s reader group := by
  apply Bool.eq_iff_iff.mpr
  simp only [indexedEnabled, indexRow, Bool.and_eq_true, Bool.or_eq_true,
    List.contains_iff_mem, List.mem_eraseDups, List.mem_filter, beq_iff_eq,
    groups_at_member, enabled_spec, and_true]

/-- A supplied full member has an explicit projected atom witness. -/
def scalarSupplyWitness (coordinate : Coordinate)
    (members : List RuleGroupMarks.OwnedMark) (mark : RuleGroupMarks.OwnedMark)
    (hm : mark ∈ members) :
    { atom : Atom // atom ∈ members.map (projectSupply coordinate) ∧
      atom.coordinate = coordinate ∧ atom.group = mark.group } :=
  ⟨projectSupply coordinate mark, List.mem_map.mpr ⟨mark, hm, rfl⟩, rfl, rfl⟩

/-- Ambient FLOW has no remote original. A reached local operation can
    select a group from that supply and keeps its validation obligation. -/
structure Ambient where
  reached : Bool
  flowSupply : Bool
  groups : List Group
  needs : List Group
  deriving DecidableEq, Repr

def ambientEnabled (ambient : Ambient) (group : Group) : Bool :=
  ambient.reached &&
    (ambient.groups.contains group || (ambient.flowSupply && ambient.needs.contains group))

def ambientWitness (ambient : Ambient) (reader : Reader) (group : Group)
    (hr : ambient.reached = true) (hf : ambient.flowSupply = true)
    (hn : group ∈ ambient.needs) :
    { delivery : Delivery // delivery.layer = .demand ∧
      ambientEnabled ambient delivery.group = true } :=
  ⟨localDelivery reader group, rfl, by simp [ambientEnabled, hr, hf, hn, localDelivery]⟩

theorem ambient_need_alone_not_supply (group : Group) :
    ambientEnabled ⟨true, false, [], [group]⟩ group = false := by
  simp [ambientEnabled]

theorem no_supply_no_group (s : State) (reader : Reader) (group : Group)
    (hk : Atom.mk reader.coordinate group ∉ s.known)
    (hf : reader.coordinate ∉ s.unknownFlow) :
    enabled s reader group = false := by
  simp [enabled, hk, hf]

theorem no_reader_no_delivery (s : State) (reader : Reader) (group : Group)
    (hr : reader ∉ s.readers) : enabled s reader group = false := by
  simp [enabled, hr]

inductive Event where
  | known (atom : Atom)
  | flow (coordinate : Coordinate)
  | need (atom : Atom)
  | reach (reader : Reader)
  deriving DecidableEq, Repr

def eventState : Event → State
  | .known atom => { known := [atom] }
  | .flow coordinate => { unknownFlow := [coordinate] }
  | .need atom => { needs := [atom] }
  | .reach reader => { readers := [reader] }

/-- The independent scalar oracle retains every offer. -/
def scalarOffers : List Event → State
  | [] => {}
  | event :: tail => append (eventState event) (scalarOffers tail)

/-- The optimized state joins each batch before observation. -/
def process : List Event → State
  | [] => {}
  | event :: tail => merge (eventState event) (process tail)

theorem process_scalar_atoms (events : List Event) :
    SameAtoms (process events) (scalarOffers events) := by
  induction events with
  | nil => exact same_atoms_refl _
  | cons event tail ih =>
    exact same_atoms_trans (merge_membership _ _)
      (append_congr (same_atoms_refl _) ih)

theorem process_scalar_equal (events : List Event) :
    Equivalent (process events) (scalarOffers events) :=
  same_atoms_observe (process_scalar_atoms events)

@[simp] theorem scalar_known_iff (events : List Event) (atom : Atom) :
    atom ∈ (scalarOffers events).known ↔ Event.known atom ∈ events := by
  induction events with
  | nil => simp [scalarOffers]
  | cons event tail ih =>
    cases event <;> simp [scalarOffers, append, eventState, ih]

@[simp] theorem scalar_flow_iff (events : List Event) (coordinate : Coordinate) :
    coordinate ∈ (scalarOffers events).unknownFlow ↔ Event.flow coordinate ∈ events := by
  induction events with
  | nil => simp [scalarOffers]
  | cons event tail ih =>
    cases event <;> simp [scalarOffers, append, eventState, ih]

@[simp] theorem scalar_need_iff (events : List Event) (atom : Atom) :
    atom ∈ (scalarOffers events).needs ↔ Event.need atom ∈ events := by
  induction events with
  | nil => simp [scalarOffers]
  | cons event tail ih =>
    cases event <;> simp [scalarOffers, append, eventState, ih]

@[simp] theorem scalar_reader_iff (events : List Event) (reader : Reader) :
    reader ∈ (scalarOffers events).readers ↔ Event.reach reader ∈ events := by
  induction events with
  | nil => simp [scalarOffers]
  | cons event tail ih =>
    cases event <;> simp [scalarOffers, append, eventState, ih]

@[simp] theorem process_known_iff (events : List Event) (atom : Atom) :
    atom ∈ (process events).known ↔ Event.known atom ∈ events :=
  ((process_scalar_atoms events).1 atom).trans (scalar_known_iff events atom)

@[simp] theorem process_flow_iff (events : List Event) (coordinate : Coordinate) :
    coordinate ∈ (process events).unknownFlow ↔ Event.flow coordinate ∈ events :=
  ((process_scalar_atoms events).2.1 coordinate).trans (scalar_flow_iff events coordinate)

@[simp] theorem process_need_iff (events : List Event) (atom : Atom) :
    atom ∈ (process events).needs ↔ Event.need atom ∈ events :=
  ((process_scalar_atoms events).2.2.1 atom).trans (scalar_need_iff events atom)

@[simp] theorem process_reader_iff (events : List Event) (reader : Reader) :
    reader ∈ (process events).readers ↔ Event.reach reader ∈ events :=
  ((process_scalar_atoms events).2.2.2 reader).trans (scalar_reader_iff events reader)

/-- Separate event batches give the same observations as one scalar batch. -/
theorem process_append_equal (first second : List Event) :
    Equivalent (process (first ++ second)) (merge (process first) (process second)) := by
  apply same_atoms_observe
  simp [SameAtoms, merge, pack, append]

theorem event_membership_equal (first second : List Event)
    (h : ∀ event, event ∈ first ↔ event ∈ second) :
    Equivalent (process first) (process second) := by
  apply same_atoms_observe
  simp only [SameAtoms, process_known_iff, process_flow_iff,
    process_need_iff, process_reader_iff]
  exact ⟨fun atom => h (.known atom), fun coordinate => h (.flow coordinate),
    fun atom => h (.need atom), fun reader => h (.reach reader)⟩

/-- This finite iff is the full transport contract. No request can create
    supply, and no event order appears in the result. -/
theorem process_event_coverage (events : List Event) (reader : Reader) (group : Group) :
    enabled (process events) reader group = true ↔
      Event.reach reader ∈ events ∧
        (Event.known ⟨reader.coordinate, group⟩ ∈ events ∨
          Event.flow reader.coordinate ∈ events ∧
            Event.need ⟨reader.coordinate, group⟩ ∈ events) := by
  rw [(process_scalar_equal events).1 reader group]
  simp

/-- The executable witness covers a full scalar mark offer. It is DEMAND,
    and it contains the local consumer instead of the supplier's origin. -/
def scalarEventWitness (events : List Event) (reader : Reader)
    (mark : RuleGroupMarks.OwnedMark)
    (hr : Event.reach reader ∈ events)
    (hk : Event.known (projectSupply reader.coordinate mark) ∈ events) :
    { delivery : Delivery // observed (process events) delivery = true } :=
  ⟨localDelivery reader mark.group, by
    rw [observe_local, process_event_coverage]
    exact ⟨hr, Or.inl hk⟩⟩

def unknownEventWitness (events : List Event) (reader : Reader) (group : Group)
    (hr : Event.reach reader ∈ events)
    (hf : Event.flow reader.coordinate ∈ events)
    (hn : Event.need ⟨reader.coordinate, group⟩ ∈ events) :
    { delivery : Delivery // observed (process events) delivery = true } :=
  ⟨localDelivery reader group, by
    rw [observe_local, process_event_coverage]
    exact ⟨hr, Or.inr ⟨hf, hn⟩⟩⟩

/-- Registration, supply and need can arrive in every order. -/
theorem swap_arrivals (a b : Event) (tail : List Event) :
    Equivalent (process (a :: b :: tail)) (process (b :: a :: tail)) := by
  apply same_atoms_observe
  simp [SameAtoms, process, merge, pack, append, or_left_comm]

/-- Batch grouping cannot change a possible group or a FLOW delivery. -/
theorem batch_union_equal (a b c : List Event) :
    Equivalent (merge (process a) (merge (process b) (process c)))
      (merge (merge (process a) (process b)) (process c)) := by
  exact ⟨fun r g => (merge_batch_associative (process a) (process b) (process c)).1 r g |>.symm,
    fun r => (merge_batch_associative (process a) (process b) (process c)).2 r |>.symm⟩

/-- A transparent relay chain needs one old carrier per group per relay.
    The shared pool needs no carrier at a relay. Count this lane only. -/
def relayCarriers (height groups : Nat) : Nat := height * groups

def sharedRelayCarriers (_height _groups : Nat) : Nat := 0

theorem transparent_chain_saving (height groups : Nat) (hh : 0 < height) (hg : 0 < groups) :
    sharedRelayCarriers height groups < relayCarriers height groups := by
  exact Nat.mul_pos hh hg

namespace Controls

def reader : Reader := ⟨10, 1⟩
def otherReader : Reader := ⟨11, 2⟩
def readEvent : Event := .reach reader
def knownEvent : Event := .known ⟨1, 7⟩
def flowEvent : Event := .flow 1
def needEvent : Event := .need ⟨1, 7⟩

def yes (events : List Event) (group : Group := 7) : Bool :=
  enabled (process events) reader group

def controls : List (String × Bool) := [
  ("known before registration", yes [knownEvent, readEvent]),
  ("registration before known", yes [readEvent, knownEvent]),
  ("need cannot create supply", !(yes [needEvent, readEvent])),
  ("FLOW without need has no named group", !(yes [flowEvent, readEvent])),
  ("need FLOW registration", yes [needEvent, flowEvent, readEvent]),
  ("FLOW registration need", yes [flowEvent, readEvent, needEvent]),
  ("registration need FLOW", yes [readEvent, needEvent, flowEvent]),
  ("unreached reader has no delivery", !(yes [knownEvent])),
  ("different group is not known", !(yes [knownEvent, readEvent] 8)),
  ("different coordinate known", !(yes [.known ⟨2, 7⟩, readEvent])),
  ("different coordinate FLOW", !(yes [.flow 2, needEvent, readEvent])),
  ("duplicate known offer", yes [knownEvent, knownEvent, readEvent]),
  ("FLOW read before mark interest", flowEnabled (process [readEvent, flowEvent]) reader),
  ("no NORMAL output", !(observed (process [readEvent, knownEvent]) ⟨reader, 7, .normal⟩)),
  ("local DEMAND output", observed (process [readEvent, knownEvent]) (localDelivery reader 7)),
  ("same-group full identities merge", decide (projectSupply 1 ⟨7, 10⟩ = projectSupply 1 ⟨7, 20⟩)),
  ("positive relay saving", decide (sharedRelayCarriers 10 7 < relayCarriers 10 7)),
  ("empty relay chain has no strict saving", decide (sharedRelayCarriers 0 7 = relayCarriers 0 7)),
  ("finite group enumeration", decide (deliverableGroups (process [knownEvent, readEvent]) reader = [7])),
  ("answered group is not delivered twice", decide (freshGroups (process [knownEvent, readEvent]) reader [7] = [])),
  ("fresh group is scheduled", decide (freshGroups (process [knownEvent, readEvent]) reader [] = [7])),
  ("ambient FLOW and local need", ambientEnabled ⟨true, true, [], [7]⟩ 7),
  ("ambient need cannot create supply", !(ambientEnabled ⟨true, false, [], [7]⟩ 7)),
  ("ambient operation must be reached", !(ambientEnabled ⟨false, true, [], [7]⟩ 7)),
  ("ambient need does not select another group", !(ambientEnabled ⟨true, true, [], [7]⟩ 8))
]

theorem controls_pass : controls.all Prod.snd = true := by decide

end Controls
end ApSpec.Run1GroupStaticPool
