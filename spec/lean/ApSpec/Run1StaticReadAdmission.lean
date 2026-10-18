/-
  A finite read/write-presence gate. Field keys denote adapter-certified overlap
  cells. Write presence records a reached published FLOW or group-valued write.
  Prescan candidates and identity/default summaries are not write events.
  Write presence is independent of group supply. A pending reached read emits
  its conditional template only after a compatible write-presence event.
-/
import Std
import ApSpec.Run1StaticInitialRefinement

namespace ApSpec.Run1StaticReadAdmission
set_option autoImplicit false

abbrev Field := Run1StaticInitialRefinement.Field
abbrev Template := Run1StaticInitialRefinement.Template
abbrev Subscription := Run1StaticInitialRefinement.Subscription
abbrev Emitted := Run1StaticInitialRefinement.Emitted

structure State where
  reachedReads : List Template := []
  writes : List Field := []
  deriving DecidableEq, Repr

def admitted (state : State) : List Template :=
  (state.reachedReads.filter (fun read => state.writes.contains read.originalField)).eraseDups

theorem admitted_iff (state : State) (read : Template) : read ∈ admitted state ↔
    read ∈ state.reachedReads ∧ read.originalField ∈ state.writes := by simp [admitted]

def reachRead (state : State) (read : Template) : State :=
  { state with reachedReads := (read :: state.reachedReads).eraseDups }

def publishWrite (state : State) (field : Field) : State :=
  { state with writes := (field :: state.writes).eraseDups }

theorem read_without_write_pending (state : State) (read : Template)
    (absent : read.originalField ∉ state.writes) :
    read ∈ (reachRead state read).reachedReads ∧
      read ∉ admitted (reachRead state read) := by
  simp [reachRead, admitted_iff, absent]

theorem no_write_no_edge (state : State) (read : Template)
    (absent : read.originalField ∉ state.writes) : read ∉ admitted state := by
  intro present
  exact absent ((admitted_iff state read).mp present).2

theorem no_reached_read_no_edge (state : State) (read : Template)
    (absent : read ∉ state.reachedReads) : read ∉ admitted state := by
  intro present
  exact absent ((admitted_iff state read).mp present).1

/-- The executable witness is the immutable read template itself. -/
def admissionWitness (state : State) (read : Template)
    (reached : read ∈ state.reachedReads) (written : read.originalField ∈ state.writes) :
    { edge : Template // edge ∈ admitted state ∧ edge = read } :=
  ⟨read, (admitted_iff state read).mpr ⟨reached, written⟩, rfl⟩

theorem late_write_admits (state : State) (read : Template)
    (reached : read ∈ state.reachedReads) :
    read ∈ admitted (publishWrite state read.originalField) := by
  simp [admitted_iff, publishWrite, reached]

theorem late_read_admits (state : State) (read : Template)
    (written : read.originalField ∈ state.writes) :
    read ∈ admitted (reachRead state read) := by
  simp [admitted_iff, reachRead, written]

theorem read_then_write_same_state (state : State) (read : Template) (field : Field) :
    publishWrite (reachRead state read) field =
      reachRead (publishWrite state field) read := rfl

def append (left right : State) : State :=
  ⟨left.reachedReads ++ right.reachedReads, left.writes ++ right.writes⟩

def pack (state : State) : State :=
  ⟨state.reachedReads.eraseDups, state.writes.eraseDups⟩

def merge (left right : State) : State := pack (append left right)

def SameAtoms (left right : State) : Prop :=
  (∀ read, read ∈ left.reachedReads ↔ read ∈ right.reachedReads) ∧
  (∀ field, field ∈ left.writes ↔ field ∈ right.writes)

def Equivalent (left right : State) : Prop :=
  ∀ read, read ∈ admitted left ↔ read ∈ admitted right

theorem same_atoms_admit {left right : State} (same : SameAtoms left right) :
    Equivalent left right := by
  intro read
  simp only [admitted_iff]
  rw [same.1, same.2]

theorem pack_preserves_admission (state : State) : Equivalent (pack state) state := by
  apply same_atoms_admit
  simp [SameAtoms, pack]

theorem merge_arrival_commutes (left right : State) :
    Equivalent (merge left right) (merge right left) := by
  apply same_atoms_admit
  simp [SameAtoms, merge, pack, append, or_comm]

theorem merge_associative (a b c : State) :
    Equivalent (merge (merge a b) c) (merge a (merge b c)) := by
  apply same_atoms_admit
  simp [SameAtoms, merge, pack, append, or_assoc]

theorem duplicate_batch_same (state : State) : Equivalent (merge state state) state := by
  apply same_atoms_admit
  simp [SameAtoms, merge, pack, append]

theorem admission_monotone_left (left right : State) (read : Template)
    (present : read ∈ admitted left) : read ∈ admitted (merge left right) := by
  have facts := (admitted_iff left read).mp present
  simp [admitted_iff, merge, pack, append, facts.1, facts.2]

def fresh (state : State) (emitted : List Template) : List Template :=
  (admitted state).filter (fun read => !emitted.contains read)

def commit (state : State) (emitted : List Template) : List Template :=
  (emitted ++ admitted state).eraseDups

theorem fresh_iff (state : State) (emitted : List Template) (read : Template) :
    read ∈ fresh state emitted ↔ read ∈ admitted state ∧ read ∉ emitted := by simp [fresh]

theorem packed_templates_nodup (templates : List Template) : templates.eraseDups.Nodup :=
  match templates with
  | [] => by simp
  | head :: tail => by
      rw [List.eraseDups_cons, List.nodup_cons]
      constructor
      · simp
      · exact packed_templates_nodup (tail.filter (fun next => !next == head))
termination_by templates.length
decreasing_by
  have bound := List.length_filter_le (fun next => !next == head) tail
  simp only [List.length_cons]
  omega

theorem no_duplicate_admissions (state : State) : (admitted state).Nodup :=
  packed_templates_nodup _

theorem fresh_after_commit_empty (state : State) (emitted : List Template) :
    fresh state (commit state emitted) = [] := by
  cases equation : fresh state (commit state emitted) with
  | nil => rfl
  | cons read tail =>
      have present : read ∈ fresh state (commit state emitted) := by simp [equation]
      have facts := (fresh_iff state _ read).mp present
      exact False.elim (facts.2 (by simp [commit, facts.1]))

/-- Gate the conditional templates, without changing either group store. -/
def refinementState (gate : State) (supplied : List Run1StaticGroupProtocol.Atom)
    (subscriptions : List Subscription) : Run1StaticInitialRefinement.State :=
  ⟨supplied, admitted gate, subscriptions⟩

theorem gate_does_not_fabricate_supply (gate : State)
    (supplied : List Run1StaticGroupProtocol.Atom) (subscriptions : List Subscription) :
    (refinementState gate supplied subscriptions).supplied = supplied := rfl

theorem gated_output_iff (gate : State) (supplied : List Run1StaticGroupProtocol.Atom)
    (subscriptions : List Subscription) (edge : Emitted) :
    edge ∈ Run1StaticInitialRefinement.outputs (refinementState gate supplied subscriptions) ↔
      edge.layer = .demand ∧
      Run1StaticInitialRefinement.subscriptionOf edge ∈ subscriptions ∧
      (Run1StaticInitialRefinement.subscriptionOf edge).template ∈ gate.reachedReads ∧
      edge.originalField ∈ gate.writes ∧
      Run1StaticGroupProtocol.Atom.mk edge.originalField edge.group ∈ supplied := by
  simp only [Run1StaticInitialRefinement.output_iff, refinementState, admitted_iff,
    Run1StaticInitialRefinement.subscriptionOf, Run1StaticInitialRefinement.needKey]
  simp only [and_assoc]

theorem gated_no_write_no_group_edge (gate : State)
    (supplied : List Run1StaticGroupProtocol.Atom) (subscriptions : List Subscription)
    (edge : Emitted) (absent : edge.originalField ∉ gate.writes) :
    edge ∉ Run1StaticInitialRefinement.outputs (refinementState gate supplied subscriptions) := by
  intro present
  exact absent ((gated_output_iff gate supplied subscriptions edge).mp present).2.2.2.1

theorem matching_write_retains_refinement (gate : State)
    (supplied : List Run1StaticGroupProtocol.Atom) (subscriptions : List Subscription)
    (subscription : Subscription) (reached : subscription.template ∈ gate.reachedReads)
    (requested : subscription ∈ subscriptions)
    (available : Run1StaticInitialRefinement.needKey subscription ∈ supplied) :
    Run1StaticInitialRefinement.materialize subscription ∈
      Run1StaticInitialRefinement.outputs
        (refinementState (publishWrite gate subscription.template.originalField)
          supplied subscriptions) := by
  apply Run1StaticInitialRefinement.refinement_complete
  · exact late_write_admits gate subscription.template reached
  · exact requested
  · exact available

def gatedCertificate (network : Run1StaticGroupProtocol.Network) (gate : State)
    (supplied : List Run1StaticGroupProtocol.Atom) (subscriptions : List Subscription)
    (safe : Run1StaticInitialRefinement.Safe network
      (refinementState gate supplied subscriptions))
    (edge : Emitted)
    (present : edge ∈ Run1StaticInitialRefinement.outputs
      (refinementState gate supplied subscriptions)) :
    Run1StaticGroupProtocol.Derivation network ⟨edge.originalField, edge.group⟩ :=
  Run1StaticInitialRefinement.materializationCertificate network _ safe edge present

/-- If the production supply adapter also publishes matching write presence,
    the gate cannot suppress a reached, requested and supplied read. The event
    premise is explicit; the model does not assume it for every runtime source. -/
theorem supply_with_write_event_complete (gate : State)
    (supplied : List Run1StaticGroupProtocol.Atom) (subscriptions : List Subscription)
    (subscription : Subscription) (reached : subscription.template ∈ gate.reachedReads)
    (requested : subscription ∈ subscriptions)
    (available : Run1StaticInitialRefinement.needKey subscription ∈ supplied)
    (eventPublished : Run1StaticInitialRefinement.needKey subscription ∈ supplied →
      subscription.template.originalField ∈ gate.writes) :
    Run1StaticInitialRefinement.materialize subscription ∈
      Run1StaticInitialRefinement.outputs (refinementState gate supplied subscriptions) := by
  apply Run1StaticInitialRefinement.refinement_complete
  · exact (admitted_iff gate subscription.template).mpr ⟨reached, eventPublished available⟩
  · exact requested
  · exact available

structure Store where
  gate : State := {}
  supplied : List Run1StaticGroupProtocol.Atom := []
  subscriptions : List Subscription := []
  deriving DecidableEq, Repr

def HasWriteForSupply (store : Store) : Prop :=
  ∀ atom, atom ∈ store.supplied → atom.node ∈ store.gate.writes

theorem empty_store_has_write_for_supply : HasWriteForSupply {} := by
  intro atom impossible
  simp at impossible

/-- The write event is committed first. Publishing a proven group can never
    leave a group row without the corresponding write-presence row. -/
def publishGroup {network : Run1StaticGroupProtocol.Network} (store : Store) (atom : Run1StaticGroupProtocol.Atom)
    (_proof : Run1StaticGroupProtocol.Derivation network atom) : Store :=
  let writeCommitted := { store with gate := publishWrite store.gate atom.node }
  { writeCommitted with supplied := (atom :: writeCommitted.supplied).eraseDups }

theorem publish_group_preserves_write_invariant {network : Run1StaticGroupProtocol.Network} (store : Store)
    (invariant : HasWriteForSupply store) (atom : Run1StaticGroupProtocol.Atom)
    (proof : Run1StaticGroupProtocol.Derivation network atom) :
    HasWriteForSupply (publishGroup store atom proof) := by
  intro candidate present
  have present : candidate = atom ∨ candidate ∈ store.supplied := by
    simpa [publishGroup] using present
  rcases present with same | prior
  · subst candidate
    simp [publishGroup, publishWrite]
  · have written := invariant candidate prior
    simp [publishGroup, publishWrite, written]

def storeRefinement (store : Store) : Run1StaticInitialRefinement.State :=
  refinementState store.gate store.supplied store.subscriptions

def ungatedRefinement (store : Store) : Run1StaticInitialRefinement.State :=
  ⟨store.supplied, store.gate.reachedReads, store.subscriptions⟩

def publishGroupSafe (network : Run1StaticGroupProtocol.Network) (store : Store)
    (safe : Run1StaticInitialRefinement.Safe network (storeRefinement store))
    (atom : Run1StaticGroupProtocol.Atom)
    (proof : Run1StaticGroupProtocol.Derivation network atom) :
    Run1StaticInitialRefinement.Safe network (storeRefinement (publishGroup store atom proof)) := by
  intro candidate present
  have present : candidate = atom ∨ candidate ∈ store.supplied := by
    simpa [storeRefinement, refinementState, publishGroup] using present
  if same : candidate = atom then exact same ▸ proof
  else exact safe candidate (present.resolve_left same)

/-- The gate removes no existing supplied requested output, when the store
    invariant is maintained. No global engine-completeness premise is used. -/
theorem gate_preserves_supplied_outputs (store : Store) (invariant : HasWriteForSupply store)
    (edge : Emitted) :
    edge ∈ Run1StaticInitialRefinement.outputs (ungatedRefinement store) ↔
      edge ∈ Run1StaticInitialRefinement.outputs (storeRefinement store) := by
  constructor
  · intro present
    have facts := (Run1StaticInitialRefinement.output_iff _ edge).mp present
    have written : edge.originalField ∈ store.gate.writes := invariant _ facts.2.2.2
    exact (gated_output_iff _ _ _ edge).mpr
      ⟨facts.1, facts.2.1, facts.2.2.1, written, facts.2.2.2⟩
  · intro present
    have facts := (gated_output_iff _ _ _ edge).mp present
    exact (Run1StaticInitialRefinement.output_iff _ edge).mpr
      ⟨facts.1, facts.2.1, facts.2.2.1, facts.2.2.2.2⟩

namespace Controls

def read : Template := ⟨7, 30, 200⟩
def otherRead : Template := ⟨8, 31, 201⟩
def consumer : Subscription := ⟨read, 5⟩
def pending : State := reachRead {} read
def writeFirst : State := publishWrite {} 7
def readFirst : State := publishWrite pending 7
def readLate : State := reachRead writeFirst read
def combined : State := merge pending writeFirst
def groupSupply : List Run1StaticGroupProtocol.Atom := [⟨7, 5⟩]
def groupRequests : List Subscription := [consumer]

def reachedStore : Store := ⟨pending, [], groupRequests⟩

def publishedStore : Store := publishGroup reachedStore ⟨7, 5⟩
  Run1StaticInitialRefinement.Controls.fieldSource

def controls : List (String × Bool) :=
  [ ("read without write stays pending", pending.reachedReads.contains read)
  , ("read without write emits no conditional edge", (admitted pending).isEmpty)
  , ("pending read has no queued emission", (fresh pending []).isEmpty)
  , ("write without reached read emits no edge", (admitted writeFirst).isEmpty)
  , ("old read and new write join", (admitted readFirst).contains read)
  , ("old write and new read join", (admitted readLate).contains read)
  , ("arrival order has identical state", readFirst == readLate)
  , ("unrelated write does not admit read", (admitted (publishWrite pending 8)).isEmpty)
  , ("write presence needs no group", (admitted readFirst).length == 1)
  , ("duplicate reached read admits once", (admitted (reachRead readFirst read)).length == 1)
  , ("duplicate write admits once", (admitted (publishWrite readFirst 7)).length == 1)
  , ("separate batches join", (admitted combined).contains read)
  , ("duplicate batch emits one template", (admitted (merge combined combined)).length == 1)
  , ("frontier does not emit twice", (fresh readFirst (commit readFirst [])).isEmpty)
  , ("gate alone supplies no group", (refinementState readFirst [] groupRequests).supplied.isEmpty)
  , ("gate plus request does not fabricate group", (Run1StaticInitialRefinement.outputs
      (refinementState readFirst [] groupRequests)).isEmpty)
  , ("FLOW-only write admits conditional read without taint", (admitted readFirst).contains read &&
      (Run1StaticInitialRefinement.outputs (refinementState readFirst [] groupRequests)).isEmpty)
  , ("actual group cannot bypass missing write", (Run1StaticInitialRefinement.outputs
      (refinementState pending groupSupply groupRequests)).isEmpty)
  , ("late matching write enables direct refinement", (Run1StaticInitialRefinement.outputs
      (refinementState readFirst groupSupply groupRequests)).contains
        (Run1StaticInitialRefinement.materialize consumer))
  , ("unrelated reached read remains unadmitted", !(admitted (reachRead readFirst otherRead)).contains otherRead)
  , ("current request point remains unchanged", (Run1StaticInitialRefinement.outputs
      (refinementState readFirst groupSupply groupRequests)).all (fun edge => edge.point == 30))
  , ("direct refinement remains DEMAND", (Run1StaticInitialRefinement.outputs
      (refinementState readFirst groupSupply groupRequests)).all (fun edge => edge.layer == .demand))
  , ("group publication commits write presence", publishedStore.gate.writes.contains 7)
  , ("group publication retains rooted supply", publishedStore.supplied.contains ⟨7, 5⟩)
  , ("group publication wakes pending refinement", (Run1StaticInitialRefinement.outputs
      (storeRefinement publishedStore)).contains (Run1StaticInitialRefinement.materialize consumer))
  , ("all published supplies have write presence", publishedStore.supplied.all
      (fun atom => publishedStore.gate.writes.contains atom.node))
  , ("repeated group publication stays one row", (publishGroup publishedStore ⟨7, 5⟩
      Run1StaticInitialRefinement.Controls.fieldSource).supplied.length == 1)
  ]

theorem controls_pass : controls.all Prod.snd = true := by decide

end Controls
end ApSpec.Run1StaticReadAdmission
