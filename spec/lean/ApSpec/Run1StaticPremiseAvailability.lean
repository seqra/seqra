/-
Run-1 static GROUP premise availability. This constructive finite model proves
an origin invariant. It does not model the AP kernel, scheduler, JVM provider,
field grammar, or every output of the Kotlin engine.

Only a supplied and already requested atom can create a static GROUP initial. Ordinary
transfer, source transfer, an all-origin conjunction arm, call application,
and static-summary promotion preserve an existing original. Static FLOW reads
have no such availability assertion. A retained summary keeps its derivation.

The old static call round trip can recover its processed-input witness from
the summary derivation. Equivalence of direct resumption additionally needs
the explicit canonical AP self-application adapter below. This adapter is
not proved by the origin invariant. It includes every selected observation,
including layer and alias classification, not only the output mark.

Seed membership follows the current engine's admitted computed-supply
contract. This model does not add an exit-rule ZERO filter or require a new
actual-input validation for source GEN. Any existing computed seed is kept.
-/
import Std

namespace ApSpec.Run1StaticPremiseAvailability

set_option autoImplicit false

inductive StaticSlot where
  | field (name : Nat)
  | exactClass (name : Nat)
  deriving DecidableEq, Repr

structure Atom where
  slot : StaticSlot
  group : Nat
  deriving DecidableEq, Repr

structure SupplyRule where
  input : Atom
  output : Atom
  deriving DecidableEq, Repr

structure Network where
  seeds : List Atom
  rules : List SupplyRule
  deriving DecidableEq, Repr

/-- Requests, static FLOW hypotheses, and promoted FLOW summaries cannot
    start this derivation. The existing computed seed contract is abstracted
    by n.seeds, without strengthening current source/exit eligibility. -/
inductive Supplied (n : Network) : Atom → Type where
  | source (a : Atom) (present : a ∈ n.seeds) (time : Nat) : Supplied n a
  | transfer (r : SupplyRule) (present : r ∈ n.rules)
      (input : Supplied n r.input) : Supplied n r.output

def sourceWitness (n : Network) {a : Atom} (d : Supplied n a) :
    {s : Atom // s ∈ n.seeds} :=
  match d with
  | .source atom present _ => ⟨atom, present⟩
  | .transfer _ _ input => sourceWitness n input

theorem no_source_free_group_cycle (rules : List SupplyRule) {a : Atom}
    (d : Supplied ⟨[], rules⟩ a) : False := by
  have h := (sourceWitness _ d).property
  simp at h

def supplyTime {n : Network} {a : Atom} (d : Supplied n a) : Nat :=
  match d with
  | .source _ _ time => time
  | .transfer _ _ input => supplyTime input + 1

/-- A PointReady is sent only after request() or groupWrite() has installed
    the field/group in processed. For an exact class point the analogous
    witness is classRequested plus an actual matching broadGroups supply. -/
structure Processed (n : Network) (a : Atom) where
  supply : Supplied n a
  requestTime : Nat

def processedTime {n : Network} {a : Atom} (p : Processed n a) : Nat :=
  max (supplyTime p.supply) p.requestTime + 1

inductive Original where
  | zero
  | baseFlow (base : Nat)
  | baseGroup (base group : Nat)
  | staticFlow (field : Nat)
  | staticGroup (atom : Atom)
  deriving DecidableEq, Repr

/-- The fields stand for a complete result descriptor. They are proof data,
    not a proposed runtime record. In particular, no origin-group stamp or
    grounding label is proposed. -/
structure Output where
  fact : Nat
  layer : Nat
  aliasClassification : Bool
  deriving DecidableEq, Repr

/-- Model the actual origin-producing seams, independently of publication.
    A rule-result arm selects an already contributing original. It does not
    assume a GROUP is supplied merely because its condition is supported. -/
inductive Edge (n : Network) : Nat → Original → Output → Type where
  | zeroStart (owner : Nat) (output : Output) : Edge n owner .zero output
  | baseFlow (owner base : Nat) (output : Output) : Edge n owner (.baseFlow base) output
  | baseGroup (owner base group : Nat) (output : Output) :
      Edge n owner (.baseGroup base group) output
  | staticRead (owner field : Nat) (output : Output) :
      Edge n owner (.staticFlow field) output
  | staticReply (owner : Nat) (atom : Atom) (reply : Processed n atom) (output : Output) :
      Edge n owner (.staticGroup atom) output
  | transfer {owner : Nat} {origin : Original} {before : Output}
      (input : Edge n owner origin before) (after : Output) : Edge n owner origin after
  | sourceTransfer {owner : Nat} {origin : Original} {before : Output}
      (input : Edge n owner origin before) (after : Output) : Edge n owner origin after
  | originArm {owner : Nat} {origin : Original} {before : Output}
      (contributing : Edge n owner origin before) (after : Output) : Edge n owner origin after
  | callApply {owner callee : Nat} {origin nativeOrigin : Original} {input result : Output}
      (caller : Edge n owner origin input) (summary : Edge n callee nativeOrigin result)
      (after : Output) : Edge n owner origin after
  | staticPromote {callee : Nat} {atom : Atom} {result : Output}
      (summary : Edge n callee (.staticGroup atom) result) (caller : Nat) :
      Edge n caller (.staticGroup atom) result
  | flowPromote {callee field : Nat} {result : Output}
      (summary : Edge n callee (.staticFlow field) result) (caller : Nat) :
      Edge n caller (.staticFlow field) result
  | zeroPromote {callee : Nat} {result : Output}
      (summary : Edge n callee .zero result) (caller : Nat) : Edge n caller .zero result

def OriginAvailability (n : Network) (origin : Original) : Type :=
  match origin with
  | .staticGroup atom => Processed n atom
  | _ => Unit

/-- The conclusion is derived from the producer grammar, not placed on a
    summary constructor as an assumed invariant. -/
def originAvailability {n : Network} {owner : Nat} {origin : Original} {output : Output}
    (edge : Edge n owner origin output) : OriginAvailability n origin :=
  match edge with
  | .zeroStart .. => ()
  | .baseFlow .. => ()
  | .baseGroup .. => ()
  | .staticRead .. => ()
  | .staticReply _ _ reply _ => reply
  | .transfer input _ => originAvailability input
  | .sourceTransfer input _ => originAvailability input
  | .originArm input _ => originAvailability input
  | .callApply input _ _ => originAvailability input
  | .staticPromote summary _ => originAvailability summary
  | .flowPromote .. => ()
  | .zeroPromote .. => ()

/-- A causal witness clock, not a CPU/work estimate. All derived operations
    occur after their premises. The availability theorem must not appeal to a
    source which only arrives after the GROUP edge has been created. -/
def edgeTime {n : Network} {owner : Nat} {origin : Original} {output : Output}
    (edge : Edge n owner origin output) : Nat :=
  match edge with
  | .zeroStart .. => 0
  | .baseFlow .. => 0
  | .baseGroup .. => 0
  | .staticRead .. => 0
  | .staticReply _ _ reply _ => processedTime reply + 1
  | .transfer input _ => edgeTime input + 1
  | .sourceTransfer input _ => edgeTime input + 1
  | .originArm input _ => edgeTime input + 1
  | .callApply input summary _ => max (edgeTime input) (edgeTime summary) + 1
  | .staticPromote summary _ => edgeTime summary + 1
  | .flowPromote summary _ => edgeTime summary + 1
  | .zeroPromote summary _ => edgeTime summary + 1

def availabilityTime {n : Network} {origin : Original} : OriginAvailability n origin → Nat :=
  match origin with
  | .staticGroup _ => processedTime
  | _ => fun _ => 0

theorem origin_available_no_later {n : Network} {owner : Nat} {origin : Original}
    {output : Output} (edge : Edge n owner origin output) :
    availabilityTime (originAvailability edge) ≤ edgeTime edge := by
  induction edge with
  | zeroStart | baseFlow | baseGroup | staticRead => exact Nat.le_refl 0
  | staticReply => exact Nat.le_succ _
  | transfer input after ih | sourceTransfer input after ih | originArm input after ih =>
    exact Nat.le_trans ih (Nat.le_succ _)
  | callApply input summary after ih _ =>
    exact Nat.le_trans ih (Nat.le_trans (Nat.le_max_left _ _) (Nat.le_succ _))
  | staticPromote summary caller ih => exact Nat.le_trans ih (Nat.le_succ _)
  | flowPromote summary caller _ | zeroPromote summary caller _ => exact Nat.zero_le _

structure Summary (n : Network) where
  owner : Nat
  origin : Original
  output : Output
  derivation : Edge n owner origin output

def summaryAvailability {n : Network} (summary : Summary n) :
    OriginAvailability n summary.origin := originAvailability summary.derivation

theorem published_static_premise_is_available {n : Network} {owner : Nat}
    {atom : Atom} {output : Output} (edge : Edge n owner (.staticGroup atom) output) :
    Nonempty (Supplied n atom) := ⟨(originAvailability edge).supply⟩

theorem published_static_premise_is_processed {n : Network} {owner : Nat}
    {atom : Atom} {output : Output} (edge : Edge n owner (.staticGroup atom) output) :
    Nonempty (Processed n atom) := ⟨originAvailability edge⟩

theorem unseeded_static_summary_cannot_publish (rules : List SupplyRule)
    {owner : Nat} {atom : Atom} {output : Output}
    (edge : Edge ⟨[], rules⟩ owner (.staticGroup atom) output) : False :=
  no_source_free_group_cycle rules (originAvailability edge).supply

/-- A run-scoped supply only grows. This executable list extension models the
    lifetime obligation; removing a completed consumer does not remove its
    supplied atom. -/
def extendSupply (known arrived : List Atom) : List Atom := known ++ arrived

theorem availability_survives_late_arrival {known arrived : List Atom} {a : Atom}
    (present : a ∈ known) : a ∈ extendSupply known arrived := by
  simp [extendSupply, present]

theorem availability_survives_consumer_removal {known : List Atom} {a : Atom}
    (present : a ∈ known) (_removedConsumer : Nat) :
    a ∈ extendSupply known ([] : List Atom) := by
  exact availability_survives_late_arrival present

/-- This adapter must include exact AP images, canonical initial tails, layer,
    alias partition, and receiving-manager ownership. The availability theorem
    alone does not establish this equation. -/
structure CanonicalApplication where
  apply : Atom → Output → List Output
  exact : ∀ atom output, apply atom output = [output]

def oldCall (kernel : CanonicalApplication) {n : Network} {callee : Nat}
    {atom : Atom} {output : Output}
    (_summary : Edge n callee (.staticGroup atom) output)
    (_reply : Processed n atom) : List Output := kernel.apply atom output

def directCall {n : Network} {callee : Nat} {atom : Atom} {output : Output}
    (_summary : Edge n callee (.staticGroup atom) output) : List Output := [output]

def recoverOldReply {n : Network} {callee : Nat} {atom : Atom} {output : Output}
    (summary : Edge n callee (.staticGroup atom) output) : Processed n atom :=
  originAvailability summary

theorem direct_call_observations_equal (kernel : CanonicalApplication)
    {n : Network} {callee : Nat} {atom : Atom} {output : Output}
    (summary : Edge n callee (.staticGroup atom) output) :
    oldCall kernel summary (recoverOldReply summary) = directCall summary :=
  kernel.exact atom output

/-- The same witness removes the all-static unary computed-supply wait. This
    theorem does not authorize a nonstatic GROUP original to supply the pool. -/
def directComputedSupply {n : Network} {owner : Nat} {input : Atom} {output : Output}
    (edge : Edge n owner (.staticGroup input) output) (r : SupplyRule)
    (present : r ∈ n.rules) (sameInput : r.input = input) : Supplied n r.output :=
  .transfer r present (sameInput ▸ (originAvailability edge).supply)

/-- The request handler's three-set transitions for one field/group. No
    supply is overwritten during the run. Each constructor encodes a disjoint
    case, so an already processed request cannot become newly requested. -/
inductive PairState where
  | absent | requested | reached | processed
  deriving DecidableEq, Repr

structure RequestEffect where
  state : PairState
  writerWake : Bool
  reply : Bool
  deriving DecidableEq, Repr

def request (state : PairState) : RequestEffect :=
  match state with
  | .absent => ⟨.requested, true, false⟩
  | .requested => ⟨.requested, false, false⟩
  | .reached => ⟨.processed, true, true⟩
  | .processed => ⟨.processed, false, true⟩

def arrive (state : PairState) : PairState :=
  match state with
  | .absent => .reached
  | .requested => .processed
  | .reached => .reached
  | .processed => .processed

theorem processed_request_keeps_state : (request .processed).state = .processed := rfl
theorem processed_request_wakes_no_writer : (request .processed).writerWake = false := rfl
theorem processed_request_already_has_answer : (request .processed).reply = true := rfl
theorem unrequested_reach_does_not_suffice : (request .reached).writerWake = true := rfl
theorem both_arrival_orders_processed :
    arrive (request .absent).state = (request (arrive .absent)).state := rfl

/-- Exact-class requests revisit positional writers, but askOnce has already
    admitted the group. A later writer gets it from classRequested during its
    own registration. This finite model abstracts that matching relation to
    one Boolean. No new lookup/stamp is proposed. -/
def classAskOnce (admitted : Bool) : Bool := !admitted
def classRegister (classRequested : Bool) : Bool := classRequested

theorem repeated_exact_class_request_has_no_fresh_writer_job : classAskOnce true = false := rfl
theorem late_exact_class_writer_is_covered : classRegister true = true := rfl

structure Work where
  interests : Nat
  replies : Nat
  applications : Nat
  resumes : Nat
  deriving DecidableEq, Repr

def oldWork (jobs : Nat) : Work := ⟨jobs, jobs, jobs, jobs⟩
def directWork (jobs : Nat) : Work := ⟨0, 0, 0, jobs⟩

theorem resume_count_unchanged (jobs : Nat) :
    (oldWork jobs).resumes = (directWork jobs).resumes := rfl

theorem all_pool_roundtrips_removed (jobs : Nat) :
    (directWork jobs).interests + (directWork jobs).replies +
      (directWork jobs).applications = 0 := rfl

/-- callInterest currently splits a packed publication into scalar output
    leaves. This limited model compares their decoded observations. A real
    packed call suffix must additionally be shown union-distributive, with
    the existing alias/no-effect partition preserved. -/
def oldPackedObservations (outputs : List Output) : List Output :=
  outputs.flatMap fun output => [output]

def directPackedObservations (outputs : List Output) : List Output := outputs

theorem packed_decoded_outputs_equal (outputs : List Output) :
    oldPackedObservations outputs = directPackedObservations outputs := by
  induction outputs with
  | nil => rfl
  | cons output rest ih => simp_all [oldPackedObservations, directPackedObservations]

def oldPackedResumes (outputs : List Output) : Nat := outputs.length
def homogeneousPackedResumes (outputs : List Output) : Nat := if outputs.isEmpty then 0 else 1

theorem nonempty_homogeneous_pack_resumes_once (output : Output) (rest : List Output) :
    homogeneousPackedResumes (output :: rest) = 1 := rfl

def f : Atom := ⟨.field 1, 3⟩
def g : Atom := ⟨.field 2, 3⟩
def marker : Atom := ⟨.exactClass 7, 3⟩
def world : Network := ⟨[f, marker], [⟨f, g⟩]⟩
def value : Output := ⟨17, 1, true⟩
def suppliedF : Supplied world f := .source f (by decide) 5
def processedF : Processed world f := ⟨suppliedF, 3⟩
def leaf : Edge world 0 (.staticGroup f) value := .staticReply 0 f processedF value
def wrappers : Edge world 3 (.staticGroup f) value :=
  .staticPromote (.staticPromote (.staticPromote leaf 1) 2) 3
def identityKernel : CanonicalApplication := ⟨fun _ output => [output], by intros; rfl⟩

example : Nonempty (Supplied world f) := published_static_premise_is_available wrappers
example : oldCall identityKernel wrappers (recoverOldReply wrappers) = [value] := rfl
example : directCall wrappers = [value] := rfl
example : directCall wrappers = oldCall identityKernel wrappers processedF := rfl
example : (directWork 100).replies = 0 := rfl
example : (oldWork 100).replies = 100 := rfl
example : (directWork 100).resumes = (oldWork 100).resumes := rfl
example : f ∈ extendSupply [f] [g] := by decide
example : f ∈ extendSupply [g] [f] := by decide
example : f ∈ extendSupply [f] [f] := by decide
example : Nonempty (Supplied world g) :=
  ⟨directComputedSupply wrappers ⟨f, g⟩ (by decide) rfl⟩
example : Nonempty (Supplied world marker) := ⟨.source marker (by decide) 2⟩
example : processedTime (originAvailability wrappers) = 6 := rfl
example : edgeTime wrappers = 10 := rfl
example : processedTime (recoverOldReply wrappers) ≤ edgeTime wrappers :=
  origin_available_no_later wrappers

def controls : List Bool :=
  [decide (directCall wrappers = [value]),
   decide (oldCall identityKernel wrappers processedF = [value]),
   decide (processedTime (recoverOldReply wrappers) = 6),
   decide (edgeTime wrappers = 10),
   decide (processedTime (recoverOldReply wrappers) ≤ edgeTime wrappers),
   decide ((directWork 1).replies = 0),
   decide ((oldWork 1).replies = 1),
   decide ((directWork 100).applications = 0),
   decide ((oldWork 100).applications = 100),
   decide ((directWork 100).resumes = (oldWork 100).resumes),
   decide (f ∈ extendSupply [f] [g]),
   decide (f ∈ extendSupply [g] [f]),
   decide (f ∈ extendSupply [f] [f]),
   decide (marker ∈ world.seeds),
   decide ((request .processed).state = .processed),
   !(request .processed).writerWake,
   (request .processed).reply,
   (request .reached).writerWake,
   decide (arrive (request .absent).state = (request (arrive .absent)).state),
   !classAskOnce true,
   classRegister true,
   decide (oldPackedObservations (List.replicate 8 value) =
      directPackedObservations (List.replicate 8 value)),
   decide (oldPackedResumes (List.replicate 8 value) = 8),
   decide (homogeneousPackedResumes (List.replicate 8 value) = 1)]

theorem executable_controls_pass : controls.all id = true := by decide

#eval do
  if !(controls.all id) then throw (IO.userError "Static premise availability controls failed")
  IO.println s!"Static premise availability controls passed: {controls.length}."

end ApSpec.Run1StaticPremiseAvailability
