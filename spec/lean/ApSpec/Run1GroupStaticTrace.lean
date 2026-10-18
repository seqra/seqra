/-
  Finite ordered static traces and their monotone pooled abstraction.
  Concrete writes replace field contents. Selected cleaners remove exact
  marks; whole kills remove all current contents. The pool keeps history.
  A local RHS certificate covers one primitive write, not the whole solver.
  Every real supplied mark remains a known group or unknown FLOW supply.
  Reached read/need witnesses then compute a DEMAND delivery.
  AP overlap, local CFG compilation and calls remain adapter obligations.
-/
import ApSpec.Run1GroupStaticPool
namespace ApSpec.Run1GroupStaticTrace
set_option autoImplicit false
open RuleGroupMarks (OwnedMark)
open Run1GroupStaticPool (Coordinate Atom Reader State Delivery)

inductive Envelope where
  | empty
  | known (groups : List Nat)
  | flow
  deriving DecidableEq, Repr

def Covers : Envelope → List OwnedMark → Prop
  | .empty, members => members = []
  | .known groups, members => ∀ m, m ∈ members → m.group ∈ groups
  | .flow, _ => True

def project (members : List OwnedMark) : Envelope := .known (members.map OwnedMark.group)

theorem project_covers (members : List OwnedMark) : Covers (project members) members := by
  intro m hm
  exact List.mem_map.mpr ⟨m, hm, rfl⟩

def offer (coordinate : Coordinate) : Envelope → State
  | .empty => {}
  | .known groups => { known := groups.map (fun g => ⟨coordinate, g⟩) }
  | .flow => { unknownFlow := [coordinate] }

def Supplied (pool : State) (coordinate : Coordinate) (m : OwnedMark) : Prop :=
  (⟨coordinate, m.group⟩ : Atom) ∈ pool.known ∨ coordinate ∈ pool.unknownFlow

theorem covered_offer (coordinate : Coordinate) (members : List OwnedMark)
    (envelope : Envelope) (cover : Covers envelope members)
    (m : OwnedMark) (hm : m ∈ members) : Supplied (offer coordinate envelope) coordinate m := by
  cases envelope with
  | empty =>
    change members = [] at cover
    rw [cover] at hm
    exact False.elim (List.not_mem_nil hm)
  | known groups =>
    exact Or.inl (List.mem_map.mpr ⟨m.group, cover m hm, rfl⟩)
  | flow => exact Or.inr (by simp [offer])

abbrev Heap := Coordinate → List OwnedMark

def replace (heap : Heap) (coordinate : Coordinate) (members : List OwnedMark) : Heap :=
  fun c => if c = coordinate then members else heap c

structure TraceState where
  heap : Heap
  pool : State

def initial : TraceState := ⟨fun _ => [], {}⟩

def Invariant (state : TraceState) : Prop :=
  ∀ coordinate m, m ∈ state.heap coordinate → Supplied state.pool coordinate m

inductive Event where
  | write (coordinate : Coordinate) (members : List OwnedMark)
      (envelope : Envelope) (cover : Covers envelope members)
  | selectedClean (coordinate : Coordinate) (literal : OwnedMark)
  | wholeKill (coordinate : Coordinate)
  | reachRead (reader : Reader)
  | need (atom : Atom)
  | relay

def step (state : TraceState) : Event → TraceState
  | .write coordinate members envelope _ =>
      ⟨replace state.heap coordinate members,
        Run1GroupStaticPool.append state.pool (offer coordinate envelope)⟩
  | .selectedClean coordinate literal =>
      ⟨replace state.heap coordinate
        ((state.heap coordinate).filter (fun m => decide (m ≠ literal))), state.pool⟩
  | .wholeKill coordinate => ⟨replace state.heap coordinate [], state.pool⟩
  | .reachRead reader =>
      ⟨state.heap, Run1GroupStaticPool.append state.pool { readers := [reader] }⟩
  | .need atom =>
      ⟨state.heap, Run1GroupStaticPool.append state.pool { needs := [atom] }⟩
  | .relay => state

theorem supplied_append_left (a b : State) (coordinate : Coordinate) (m : OwnedMark)
    (h : Supplied a coordinate m) : Supplied (Run1GroupStaticPool.append a b) coordinate m := by
  rcases h with hk | hf
  · exact Or.inl (List.mem_append.mpr (Or.inl hk))
  · exact Or.inr (List.mem_append.mpr (Or.inl hf))

theorem supplied_append_right (a b : State) (coordinate : Coordinate) (m : OwnedMark)
    (h : Supplied b coordinate m) : Supplied (Run1GroupStaticPool.append a b) coordinate m := by
  rcases h with hk | hf
  · exact Or.inl (List.mem_append.mpr (Or.inr hk))
  · exact Or.inr (List.mem_append.mpr (Or.inr hf))

theorem initial_invariant : Invariant initial := by
  intro _ _ hm
  exact False.elim (List.not_mem_nil hm)

theorem step_invariant (state : TraceState) (event : Event) (h : Invariant state) :
    Invariant (step state event) := by
  intro coordinate m hm
  cases event with
  | write writtenAt members envelope cover =>
    change m ∈ replace state.heap writtenAt members coordinate at hm
    by_cases same : coordinate = writtenAt
    · subst coordinate
      simp only [replace] at hm
      exact supplied_append_right _ _ _ _ (covered_offer writtenAt members envelope cover m hm)
    · simp only [replace, if_neg same] at hm
      exact supplied_append_left _ _ _ _ (h coordinate m hm)
  | selectedClean writtenAt literal =>
    change m ∈ replace state.heap writtenAt _ coordinate at hm
    by_cases same : coordinate = writtenAt
    · subst coordinate
      simp only [replace] at hm
      exact h writtenAt m (List.mem_filter.mp hm).1
    · simp only [replace, if_neg same] at hm
      exact h coordinate m hm
  | wholeKill writtenAt =>
    change m ∈ replace state.heap writtenAt [] coordinate at hm
    by_cases same : coordinate = writtenAt
    · subst coordinate
      simp only [replace] at hm
      exact False.elim (List.not_mem_nil hm)
    · simp only [replace, if_neg same] at hm
      exact h coordinate m hm
  | reachRead reader => exact supplied_append_left _ _ _ _ (h coordinate m hm)
  | need atom => exact supplied_append_left _ _ _ _ (h coordinate m hm)
  | relay => exact h coordinate m hm

def execute (state : TraceState) (events : List Event) : TraceState :=
  events.foldl step state

theorem execute_invariant (state : TraceState) (events : List Event) (h : Invariant state) :
    Invariant (execute state events) := by
  induction events generalizing state with
  | nil => exact h
  | cons event rest ih => exact ih (step state event) (step_invariant state event h)

theorem every_finite_trace_covered (events : List Event) :
    Invariant (execute initial events) := execute_invariant initial events initial_invariant

theorem execute_relays (state : TraceState) (count : Nat) :
    execute state (List.replicate count .relay) = state := by
  induction count with
  | zero => rfl
  | succ count ih => simpa [execute, List.replicate_succ, step] using ih

/-- This certificate computes a local group delivery. It does not assert
    exact mark truth or a compatible whole-program execution. -/
def readWitness (state : TraceState) (reader : Reader) (m : OwnedMark)
    (cover : Invariant state) (actual : m ∈ state.heap reader.coordinate)
    (reached : reader ∈ state.pool.readers)
    (needed : Atom.mk reader.coordinate m.group ∈ state.pool.needs) :
    { delivery : Delivery // Run1GroupStaticPool.observed state.pool delivery = true } :=
  ⟨Run1GroupStaticPool.localDelivery reader m.group, by
    rw [Run1GroupStaticPool.observe_local, Run1GroupStaticPool.enabled_spec]
    refine ⟨reached, ?_⟩
    rcases cover reader.coordinate m actual with known | flow
    · exact Or.inl known
    · exact Or.inr ⟨flow, needed⟩⟩

def registerAndNeed (state : TraceState) (reader : Reader) (group : Nat) : TraceState :=
  step (step state (.reachRead reader)) (.need ⟨reader.coordinate, group⟩)

/-- At an actual read prefix, registration plus the relevant condition need
    computes a covered group delivery. No method-return summary is used. -/
def atReadWitness (events : List Event) (reader : Reader) (m : OwnedMark)
    (actual : m ∈ (execute initial events).heap reader.coordinate) :
    { delivery : Delivery //
      Run1GroupStaticPool.observed
        (registerAndNeed (execute initial events) reader m.group).pool delivery = true } :=
  readWitness (registerAndNeed (execute initial events) reader m.group) reader m
    (step_invariant _ _ (step_invariant _ _ (every_finite_trace_covered events)))
    actual
    (by simp [registerAndNeed, step, Run1GroupStaticPool.append])
    (by simp [registerAndNeed, step, Run1GroupStaticPool.append])

namespace Controls
def t : OwnedMark := ⟨7, 10⟩
def u : OwnedMark := ⟨7, 11⟩
def v : OwnedMark := ⟨8, 20⟩
def reader : Reader := ⟨31, 1⟩
def writeKnown (coordinate : Coordinate) (members : List OwnedMark) : Event :=
  .write coordinate members (project members) (project_covers members)
def writeFlow (coordinate : Coordinate) (members : List OwnedMark) : Event :=
  .write coordinate members .flow True.intro
def reads : List Event := [.reachRead reader, .need ⟨1, 7⟩]
def hasGroup (events : List Event) (group : Nat) : Bool :=
  Run1GroupStaticPool.enabled (execute initial events).pool reader group
def membersAfter (events : List Event) : List OwnedMark := (execute initial events).heap 1
def controls : List (String × Bool) := [
  ("known write and reached read", hasGroup (writeKnown 1 [t] :: reads) 7),
  ("unknown write plus group need", hasGroup (writeFlow 1 [t] :: reads) 7),
  ("group need alone has no supply", !(hasGroup reads 7)),
  ("strong overwrite changes exact contents", membersAfter [writeKnown 1 [t], writeKnown 1 [v]] == [v]),
  ("pool keeps overwritten possible group", hasGroup ([writeKnown 1 [t], writeKnown 1 [v]] ++ reads) 7),
  ("exact cleaner keeps same-group U", membersAfter [writeKnown 1 [t,u], .selectedClean 1 t] == [u]),
  ("whole kill empties actual field", (membersAfter [writeKnown 1 [t], .wholeKill 1]).isEmpty),
  ("pool history can add killed demand candidate", hasGroup ([writeKnown 1 [t], .wholeKill 1] ++ reads) 7),
  ("disjoint field supply not used", !(hasGroup (writeKnown 2 [t] :: reads) 7)),
  ("late write adds conservative earlier-read candidate", hasGroup (reads ++ [writeKnown 1 [t]]) 7),
  ("relay has no pool effect", hasGroup ([writeKnown 1 [t], .relay, .relay] ++ reads) 7)
]
theorem controls_pass : controls.all Prod.snd = true := by decide
end Controls
end ApSpec.Run1GroupStaticTrace
