/-
  Precomputed request lifecycle: a frozen shared base, per-owner residual
  additions, and the same storage class for write-only temporary requests.

  `baseKey` is a reference into an immutable pool, not a copied request list.
  `read` expands the logical union only as a finite oracle; it is not a retained
  runtime view/cache. Production can borrow the two disjoint packed row sets.
  No callback, caller input history or second cumulative base is stored here.
-/
import ApSpec.Run1RequestProtocol
import ApSpec.Run1Requests

namespace ApSpec.Run1PrecomputedRequests
open ApSpec.Run1RequestProtocol

variable {Cell : Type} [DecidableEq Cell]

abbrev BasePool (Cell : Type) := Nat → List Cell

/-- Normalise collected static memberships once when building the shared pool. -/
def buildBase (raw : List Cell) : List Cell := merge raw []

theorem build_base_exact (raw : List Cell) (q : Cell) :
    q ∈ buildBase raw ↔ q ∈ raw := by simp [buildBase]

theorem build_base_nodup (raw : List Cell) : (buildBase raw).Nodup :=
  merge_nodup raw [] (by simp)

/-- The same representation is used by authoritative and temporary storage.
    Temporary storage always has no shared base. -/
structure RequestStorage (Cell : Type) where
  baseKey : Option Nat
  additions : List Cell
  deriving DecidableEq, Repr

def baseRows (pool : BasePool Cell) (s : RequestStorage Cell) : List Cell :=
  match s.baseKey with
  | none => []
  | some key => pool key

def read (pool : BasePool Cell) (s : RequestStorage Cell) : List Cell :=
  merge (baseRows pool s) s.additions

@[simp] theorem read_members (pool : BasePool Cell) (s : RequestStorage Cell) (q : Cell) :
    q ∈ read pool s ↔ q ∈ baseRows pool s ∨ q ∈ s.additions := by
  simp [read]

def temporary : RequestStorage Cell := ⟨none,[]⟩

structure MethodState (Cell : Type) where
  authoritative : RequestStorage Cell
  pending : RequestStorage Cell
  deriving DecidableEq, Repr

/-- Bind before exposing the method storage to any subscriber or input. -/
def initialise (baseKey : Nat) : MethodState Cell :=
  ⟨⟨some baseKey,[]⟩,temporary⟩

omit [DecidableEq Cell] in
theorem initialise_shares_base (key : Nat) :
    (initialise key : MethodState Cell).authoritative.baseKey = some key ∧
    (initialise key : MethodState Cell).authoritative.additions = [] ∧
    (initialise key : MethodState Cell).pending = temporary := by
  exact ⟨rfl,rfl,rfl⟩

theorem initial_read_is_precomputed (pool : BasePool Cell) (key : Nat) (q : Cell) :
    q ∈ read pool (initialise key).authoritative ↔ q ∈ pool key := by
  simp [initialise,read,baseRows]

/-- All local and received proposals use the effective admitted relation.
    The pending store is never read as a membership oracle. -/
def prepare (pool : BasePool Cell) (s : MethodState Cell) (offered : List Cell) : List Cell :=
  difference offered (read pool s.authoritative)

def collect (pool : BasePool Cell) (s : MethodState Cell) (offered : List Cell) : MethodState Cell :=
  {s with pending := {s.pending with additions := merge (prepare pool s offered) s.pending.additions}}

def fresh (pool : BasePool Cell) (s : MethodState Cell) : List Cell :=
  difference s.pending.additions (read pool s.authoritative)

/-- Synchronous commit installs only the residual, followed by owner-local
    replacement of temporary storage. The returned list is transient delivery. -/
def commitAndReplace (pool : BasePool Cell) (s : MethodState Cell) : MethodState Cell × List Cell :=
  let delta := fresh pool s
  (⟨{s.authoritative with additions := merge delta s.authoritative.additions},temporary⟩,delta)

def NoBaseCopies (pool : BasePool Cell) (s : MethodState Cell) : Prop :=
  s.pending.baseKey = none ∧
  (∀ q, q ∈ baseRows pool s.authoritative → q ∉ s.authoritative.additions) ∧
  (∀ q, q ∈ baseRows pool s.authoritative → q ∉ s.pending.additions)

omit [DecidableEq Cell] in
theorem initialise_no_base_copies (pool : BasePool Cell) (key : Nat) :
    NoBaseCopies pool (initialise key) := by
  simp [NoBaseCopies,initialise,temporary]

theorem precomputed_never_offered (pool : BasePool Cell) (s : MethodState Cell)
    (offered : List Cell) (q : Cell) (planned : q ∈ baseRows pool s.authoritative) :
    q ∉ prepare pool s offered := by
  intro h
  exact ((mem_difference _ _ _).mp h).2 ((read_members _ _ _).mpr (Or.inl planned))

theorem entirely_precomputed_sends_nothing (pool : BasePool Cell) (s : MethodState Cell)
    (offered : List Cell) (planned : ∀ q ∈ offered, q ∈ baseRows pool s.authoritative) :
    prepare pool s offered = [] := by
  revert planned
  induction offered with
  | nil => intro _; rfl
  | cons q rest ih =>
    intro allPlanned
    have admitted : q ∈ read pool s.authoritative :=
      (read_members _ _ _).mpr (Or.inl (allPlanned q (by simp)))
    have tailPlanned : ∀ x ∈ rest, x ∈ baseRows pool s.authoritative :=
      fun x hx => allPlanned x (List.mem_cons_of_mem q hx)
    simpa only [prepare,difference,List.filter_cons,admitted,not_true_eq_false,
      decide_false,Bool.false_eq_true,if_false] using ih tailPlanned

theorem collect_no_base_copies (pool : BasePool Cell) (s : MethodState Cell)
    (h : NoBaseCopies pool s) (offered : List Cell) :
    NoBaseCopies pool (collect pool s offered) := by
  rcases h with ⟨pendingEmpty,disjointR,disjointP⟩
  refine ⟨pendingEmpty,disjointR,?_⟩
  intro q hbase hnew
  rcases (mem_merge _ _ _).mp hnew with new | old
  · exact precomputed_never_offered pool s offered q hbase new
  · exact disjointP q hbase old

theorem fresh_never_precomputed (pool : BasePool Cell) (s : MethodState Cell)
    (q : Cell) (planned : q ∈ baseRows pool s.authoritative) :
    q ∉ fresh pool s := by
  intro h
  exact ((mem_difference _ _ _).mp h).2 ((read_members _ _ _).mpr (Or.inl planned))

theorem commit_preserves_base_reference (pool : BasePool Cell) (s : MethodState Cell) :
    (commitAndReplace pool s).1.authoritative.baseKey = s.authoritative.baseKey := rfl

theorem commit_no_base_copies (pool : BasePool Cell) (s : MethodState Cell)
    (h : NoBaseCopies pool s) : NoBaseCopies pool (commitAndReplace pool s).1 := by
  rcases h with ⟨_,disjointR,_⟩
  refine ⟨rfl,?_,?_⟩
  · intro q hbase hnew
    rcases (mem_merge _ _ _).mp hnew with new | old
    · exact fresh_never_precomputed pool s q hbase new
    · exact disjointR q hbase old
  · intro q _ hnew
    exact List.not_mem_nil hnew

theorem initial_commit_no_notification (pool : BasePool Cell) (key : Nat) :
    (commitAndReplace pool (initialise key)).2 = [] := by
  simp [commitAndReplace,fresh,initialise,temporary,difference]

theorem notification_never_precomputed (pool : BasePool Cell) (s : MethodState Cell)
    (q : Cell) (planned : q ∈ baseRows pool s.authoritative) :
    q ∉ (commitAndReplace pool s).2 := fresh_never_precomputed pool s q planned

theorem planned_membership_persists (pool : BasePool Cell) (s : MethodState Cell)
    (q : Cell) (planned : q ∈ baseRows pool s.authoritative) :
    q ∈ read pool (commitAndReplace pool s).1.authoritative := by
  exact (read_members _ _ _).mpr (Or.inl planned)

theorem commit_read_exact (pool : BasePool Cell) (s : MethodState Cell) (q : Cell) :
    q ∈ read pool (commitAndReplace pool s).1.authoritative ↔
      q ∈ read pool s.authoritative ∨ q ∈ s.pending.additions := by
  simp only [read_members,commitAndReplace,mem_merge]
  change (q ∈ baseRows pool s.authoritative ∨ q ∈ fresh pool s ∨ q ∈ s.authoritative.additions) ↔
    (q ∈ baseRows pool s.authoritative ∨ q ∈ s.authoritative.additions) ∨ q ∈ s.pending.additions
  simp only [fresh,mem_difference,read_members]
  by_cases b : q ∈ baseRows pool s.authoritative <;>
    by_cases d : q ∈ s.authoritative.additions <;> simp [b,d]

/-- Binding the frozen base precedes every input. Existing base requests therefore
    need only the normal new-input reader, not a synthetic initial notification. -/
def inspectInput (pool : BasePool Cell) (s : MethodState Cell)
    (eligible : Cell → Bool) : List Cell := (read pool s.authoritative).filter eligible

theorem new_input_sees_precomputed (pool : BasePool Cell) (s : MethodState Cell)
    (eligible : Cell → Bool) (q : Cell) (planned : q ∈ baseRows pool s.authoritative)
    (matchInput : eligible q = true) : q ∈ inspectInput pool s eligible := by
  exact List.mem_filter.mpr ⟨(read_members _ _ _).mpr (Or.inl planned),matchInput⟩

theorem changed_origin_not_suppressed (pool : BasePool Cell) (s : MethodState Cell)
    (offered : List Cell) (q : Cell) (provided : q ∈ offered)
    (notAdmitted : q ∉ read pool s.authoritative) : q ∈ prepare pool s offered := by
  exact (mem_difference _ _ _).mpr ⟨provided,notAdmitted⟩

inductive Event (Cell : Type) where
  | contribution (offered : List Cell)
  | barrier

def step (pool : BasePool Cell) (s : MethodState Cell) : Event Cell → MethodState Cell
  | .contribution offered => collect pool s offered
  | .barrier => (commitAndReplace pool s).1

def run (pool : BasePool Cell) (s : MethodState Cell) (events : List (Event Cell)) : MethodState Cell :=
  events.foldl (step pool) s

theorem trace_no_base_copies (pool : BasePool Cell) (s : MethodState Cell)
    (h : NoBaseCopies pool s) (events : List (Event Cell)) :
    NoBaseCopies pool (run pool s events) := by
  induction events generalizing s with
  | nil => exact h
  | cons event events ih =>
    cases event with
    | contribution offered => exact ih _ (collect_no_base_copies pool s h offered)
    | barrier => exact ih _ (commit_no_base_copies pool s h)

theorem trace_preserves_base_reference (pool : BasePool Cell) (s : MethodState Cell)
    (events : List (Event Cell)) :
    (run pool s events).authoritative.baseKey = s.authoritative.baseKey := by
  induction events generalizing s with
  | nil => rfl
  | cons event events ih =>
    cases event with
    | contribution offered => exact ih _
    | barrier => exact ih _

theorem initialised_trace_no_base_copies (pool : BasePool Cell) (key : Nat)
    (events : List (Event Cell)) : NoBaseCopies pool (run pool (initialise key) events) :=
  trace_no_base_copies pool _ (initialise_no_base_copies pool key) events

theorem every_input_after_binding_sees_base (pool : BasePool Cell) (key : Nat)
    (events : List (Event Cell)) (eligible : Cell → Bool) (q : Cell)
    (planned : q ∈ pool key) (inputMatch : eligible q = true) :
    q ∈ inspectInput pool (run pool (initialise key) events) eligible := by
  have ref : (run pool (initialise key) events).authoritative.baseKey = some key :=
    trace_preserves_base_reference pool (initialise key) events
  apply new_input_sees_precomputed pool _ eligible q _ inputMatch
  simpa [baseRows,ref] using planned

theorem no_precomputed_delivery_after_any_trace (pool : BasePool Cell) (key : Nat)
    (events : List (Event Cell)) (q : Cell) (planned : q ∈ pool key) :
    q ∉ (commitAndReplace pool (run pool (initialise key) events)).2 := by
  have ref : (run pool (initialise key) events).authoritative.baseKey = some key :=
    trace_preserves_base_reference pool (initialise key) events
  apply notification_never_precomputed pool _ q
  simpa [baseRows,ref] using planned

section StaticClosure
open ApSpec.Run1Requests

/-- A callee's closed plan member is also in its caller's closed plan. This
    derives same-slot inheritance from graph closure, not request propagation. -/
theorem caller_plan_inherits {n : Nat} (g : Graph n) (forms : Fin n → List Form)
    (caller callee : Fin n) (call : (caller,callee) ∈ g) (need : Need)
    (calleeHas : need ∈ plan g forms callee) : need ∈ plan g forms caller := by
  unfold plan at *
  obtain ⟨origin,horigin,hneed⟩ := List.mem_flatMap.mp calleeHas
  by_cases hc : callee ∈ closure g origin
  · have localNeed : need ∈ direct (forms origin) := by simpa [hc] using hneed
    have callerHas := closure_call g origin caller callee call hc
    exact List.mem_flatMap.mpr ⟨origin,horigin,by simpa [callerHas] using localNeed⟩
  · simp [hc] at hneed

theorem caller_activated_inherits {n : Nat} (g : Graph n) (forms : Fin n → List Form)
    (caller callee : Fin n) (call : (caller,callee) ∈ g)
    (callerSlots calleeSlots : List Slot) (slot : Slot) (mark : ApSpec.Mark)
    (calleeHas : (slot,mark) ∈ activate (plan g forms callee) calleeSlots)
    (callerActive : slot ∈ callerSlots) :
    (slot,mark) ∈ activate (plan g forms caller) callerSlots := by
  obtain ⟨need,hn,hx⟩ := List.mem_flatMap.mp calleeHas
  cases need with
  | structural => simp at hx
  | classPoint => simp at hx
  | marked scope t =>
    obtain ⟨s,hs,eq⟩ := List.mem_map.mp hx
    have slotEq : s = slot := congrArg Prod.fst eq
    have markEq : t = mark := congrArg Prod.snd eq
    subst s
    subst t
    exact wide_activation_complete _ _ scope mark slot
      (caller_plan_inherits g forms caller callee call _ hn) callerActive
      (List.mem_filter.mp hs).2

/-- Slots resolved BEFORE run-1 from explicit structural or field-mark needs.
    Wide descriptors alone are not runtime slot activation. -/
def slotsOfNeed : Need → List Slot
  | .structural c f => [.field c f]
  | .marked (.field c f) _ => [.field c f]
  | _ => []

def frozenSlots (needs : List Need) : List Slot := needs.flatMap slotsOfNeed

def frozenBase (needs : List Need) : List (Slot × ApSpec.Mark) :=
  buildBase (activate needs (frozenSlots needs))

theorem frozen_slots_monotone (fromNeeds toNeeds : List Need)
    (includes : ∀ need ∈ fromNeeds, need ∈ toNeeds) (slot : Slot)
    (present : slot ∈ frozenSlots fromNeeds) : slot ∈ frozenSlots toNeeds := by
  obtain ⟨need,hn,hs⟩ := List.mem_flatMap.mp present
  exact List.mem_flatMap.mpr ⟨need,includes need hn,hs⟩

/-- Same-slot inheritance has no arbitrary runtime callerActive premise:
    both resolved slots and marks come from the actual frozen plans. -/
theorem caller_frozen_base_inherits {n : Nat} (g : Graph n) (forms : Fin n → List Form)
    (caller callee : Fin n) (call : (caller,callee) ∈ g) (q : Slot × ApSpec.Mark)
    (calleeHas : q ∈ frozenBase (plan g forms callee)) :
    q ∈ frozenBase (plan g forms caller) := by
  apply (build_base_exact _ _).mpr
  have activated := (build_base_exact _ _).mp calleeHas
  have calleeSlot := activate_does_not_invent_slots _ _ _ _ activated
  have callerSlot := frozen_slots_monotone _ _
    (fun need hn => caller_plan_inherits g forms caller callee call need hn) q.1 calleeSlot
  exact caller_activated_inherits g forms caller callee call _ _ q.1 q.2 activated callerSlot

theorem frozen_batch_no_send {n : Nat} (g : Graph n) (forms : Fin n → List Form)
    (caller callee : Fin n) (call : (caller,callee) ∈ g)
    (offered : List (Slot × ApSpec.Mark))
    (precomputed : ∀ q ∈ offered, q ∈ frozenBase (plan g forms callee)) :
    prepare (fun _ => frozenBase (plan g forms caller)) (initialise 0) offered = [] := by
  apply entirely_precomputed_sends_nothing
  intro q hq
  exact caller_frozen_base_inherits g forms caller callee call q (precomputed q hq)

theorem identical_static_route_already_admitted {n : Nat} (g : Graph n)
    (forms : Fin n → List Form) (caller callee : Fin n) (call : (caller,callee) ∈ g)
    (callerSlots calleeSlots : List Slot) (slot : Slot) (mark : ApSpec.Mark)
    (calleeHas : (slot,mark) ∈ activate (plan g forms callee) calleeSlots)
    (callerActive : slot ∈ callerSlots) :
    prepare (fun _ => activate (plan g forms caller) callerSlots)
      (initialise 0) [(slot,mark)] = [] := by
  apply entirely_precomputed_sends_nothing
  intro q hq
  have eq : q = (slot,mark) := by simpa using hq
  subst q
  exact caller_activated_inherits g forms caller callee call callerSlots calleeSlots slot mark
    calleeHas callerActive

/-- The entire inherited precomputed contribution can be skipped as a batch;
    no per-mark callback or initial publication wave is required. -/
theorem unchanged_precomputed_batch_no_send {n : Nat} (g : Graph n)
    (forms : Fin n → List Form) (caller callee : Fin n) (call : (caller,callee) ∈ g)
    (callerSlots calleeSlots : List Slot) (offered : List (Slot × ApSpec.Mark))
    (precomputed : ∀ q ∈ offered, q ∈ activate (plan g forms callee) calleeSlots)
    (active : ∀ q ∈ offered, q.1 ∈ callerSlots) :
    prepare (fun _ => activate (plan g forms caller) callerSlots)
      (initialise 0) offered = [] := by
  apply entirely_precomputed_sends_nothing
  intro q hq
  exact caller_activated_inherits g forms caller callee call callerSlots calleeSlots q.1 q.2
    (precomputed q hq) (active q hq)

end StaticClosure

namespace Examples

def pool : BasePool Nat := fun key => if key == 0 then [1,2] else []
def created : MethodState Nat := initialise 0
def result : MethodState Nat × List Nat :=
  commitAndReplace pool (collect pool (collect pool created [2,3]) [2,3])

theorem shared_base_and_fresh_only :
    result.1.authoritative.baseKey = some 0 ∧
    result.1.authoritative.additions = [3] ∧
    result.1.pending = temporary ∧ result.2 = [3] := by decide +kernel

end Examples

end ApSpec.Run1PrecomputedRequests
