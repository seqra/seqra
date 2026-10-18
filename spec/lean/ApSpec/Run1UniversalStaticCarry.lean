/-
  Shared all-method static GROUP demand at the F1/B2 handoff.
  The frozen implicit view applies to every ordinary valid method key,
  including lazy B2 actors. It stores no method x field x group product.
  It is query permission, not an executable static identity record.
  Scope: canonical actual full static queries and finite real identity/copy
  method bodies. Actual kills and productive nonstatic/source/group-changing
  results stay distinct. This is not a whole-JVM no-loss theorem.
-/
import ApSpec.Run1StaticEarlySummaryGap

namespace ApSpec.Run1UniversalStaticCarry
set_option autoImplicit false

structure StaticAP where
  field : Nat
  suffix : List Nat
  shape : Nat
  deriving DecidableEq, Repr

/-- AP is the actual already-canonical query geometry. There is no S-root
    taint initial and no group-only value that can manufacture a full mark. -/
structure StaticQuery where
  ap : StaticAP
  mark : RuleGroupSchedule.FullMark
  deriving DecidableEq, Repr

inductive Value where
  | staticFull (query : StaticQuery)
  | staticFlow (ap : StaticAP)
  | ordinaryFull (geometry : Nat) (mark : RuleGroupSchedule.FullMark)
  | reach
  deriving DecidableEq, Repr

structure FrozenDefaults where
  enabled : Bool
  groups : List Nat
  deriving DecidableEq, Repr

/-- Group membership is frozen from the complete rule catalog. Method keys
    are admitted by the ordinary program boundary, not enumerated here. -/
def permitted (defaults : FrozenDefaults) (_method group : Nat) : Bool :=
  defaults.enabled && defaults.groups.contains group

theorem all_methods_same_view (defaults : FrozenDefaults) (a b group : Nat) :
    permitted defaults a group = permitted defaults b group := rfl

/-- Shared S.*->S.* GROUP permission intersects the ACTUAL full input.
    FLOW cannot provide a concrete full mark, even when a group is catalogued. -/
def emit (defaults : FrozenDefaults) (method : Nat) : Option Value → Option Value
  | some (.staticFull query) =>
      if permitted defaults method query.mark.group then some (.staticFull query) else none
  | _ => none

/-- Ordinary mark-FLOW demand is different: it cannot supply a concrete
    marked initial. It drops T on this scalar comparison boundary. -/
def emitFlow : Option Value → Option Value
  | some (.staticFull query) => some (.staticFlow query.ap)
  | some (.staticFlow ap) => some (.staticFlow ap)
  | _ => none

theorem emits_actual_full (defaults : FrozenDefaults) (method : Nat) (query : StaticQuery)
    (active : defaults.enabled = true) (catalogued : query.mark.group ∈ defaults.groups) :
    emit defaults method (some (.staticFull query)) = some (.staticFull query) := by
  have enabled : permitted defaults method query.mark.group = true := by
    simp only [permitted, active, Bool.true_and, List.contains_iff_mem]
    exact catalogued
  simp only [emit, enabled, reduceIte]

theorem no_input_no_mark (defaults : FrozenDefaults) (method : Nat) : emit defaults method none = none := rfl

theorem flow_cannot_create_mark (defaults : FrozenDefaults) (method : Nat) (ap : StaticAP) :
    emit defaults method (some (.staticFlow ap)) = none := rfl

/-- The shared pair has static roots on both sides and SAME group on both
    sides. Actual outputs may have another static field/AP. Only the body
    can produce that change. Other shapes require productive writer pairs. -/
def publication (defaults : FrozenDefaults) (method : Nat) (input : StaticQuery) : Value → Bool
  | .staticFull output => permitted defaults method input.mark.group &&
      decide (output.mark.group = input.mark.group)
  | _ => false

inductive Body where
  | identity
  | copyStatic (target : StaticAP)
  | clear (field : Nat)
  | toOrdinary (target : Nat)
  | sourceZero
  | rewrite (mark : RuleGroupSchedule.FullMark)
  deriving DecidableEq, Repr

/-- Ordinary full-mark body semantics is evaluated AFTER demand admission.
    A shared demand does not cause an identity return around a strong kill. -/
def body (operation : Body) (query : StaticQuery) : Option Value :=
  match operation with
  | .identity => some (.staticFull query)
  | .copyStatic target => some (.staticFull { query with ap := target })
  | .clear field => if query.ap.field = field then none else some (.staticFull query)
  | .toOrdinary target => some (.ordinaryFull target query.mark)
  | .sourceZero => some .reach
  | .rewrite mark => some (.staticFull { query with mark := mark })

def run (defaults : FrozenDefaults) (method : Nat) (operation : Body) (query : StaticQuery) : Option Value :=
  (emit defaults method (some (.staticFull query))).bind (fun admitted =>
    match admitted with
    | .staticFull actual => (body operation actual).bind (fun result =>
        if publication defaults method actual result then some result else none)
    | _ => none)

theorem kill_still_kills (defaults : FrozenDefaults) (method : Nat) (query : StaticQuery) :
    run defaults method (.clear query.ap.field) query = none := by
  cases allowed : permitted defaults method query.mark.group with
  | false => simp [run, emit, allowed]
  | true => simp [run, emit, allowed, body]

inductive Transport where
  | identity
  | copy (target : StaticAP)
  deriving DecidableEq, Repr

def Transport.operation : Transport → Body
  | .identity => .identity
  | .copy target => .copyStatic target

def Transport.output (transfer : Transport) (query : StaticQuery) : StaticQuery :=
  match transfer with
  | .identity => query
  | .copy target => { query with ap := target }

theorem transport_keeps_full (transfer : Transport) (query : StaticQuery) :
    (transfer.output query).mark = query.mark := by cases transfer <;> rfl

theorem transport_admitted_and_published (defaults : FrozenDefaults) (method : Nat)
    (query : StaticQuery) (transfer : Transport) (active : defaults.enabled = true)
    (catalogued : query.mark.group ∈ defaults.groups) :
    run defaults method transfer.operation query = some (.staticFull (transfer.output query)) := by
  have allowed : permitted defaults method query.mark.group = true := by
    simp only [permitted, active, Bool.true_and, List.contains_iff_mem]
    exact catalogued
  cases transfer <;>
    simp only [run, emit, allowed, reduceIte, Option.bind_some, Transport.operation,
      body, publication, Bool.true_and, Transport.output] <;> rfl

structure Relay where
  method : Nat
  transport : Transport
  deriving DecidableEq, Repr

def reference : List Relay → StaticQuery → StaticQuery
  | [], query => query
  | relay :: rest, query => reference rest (relay.transport.output query)

def runPath (defaults : FrozenDefaults) : List Relay → StaticQuery → Option Value
  | [], query => some (.staticFull query)
  | relay :: rest, query =>
      (run defaults relay.method relay.transport.operation query).bind (fun value =>
        match value with
        | .staticFull next => runPath defaults rest next
        | _ => none)

/-- Every real identity/copy relay is admitted AND its static result can
    publish. Guidance comes from the shared all-method catalog predicate;
    no individual field or method guide is assumed. -/
theorem every_finite_transport_path (defaults : FrozenDefaults) (relays : List Relay)
    (query : StaticQuery) (active : defaults.enabled = true)
    (catalogued : query.mark.group ∈ defaults.groups) :
    runPath defaults relays query = some (.staticFull (reference relays query)) := by
  induction relays generalizing query with
  | nil => rfl
  | cons relay rest ih =>
    rw [runPath, transport_admitted_and_published defaults relay.method query relay.transport active catalogued]
    exact ih (relay.transport.output query) (by simpa [transport_keeps_full] using catalogued)

/-- Bridge to the immutable prior counterexample. This is an additive view
    of a frozen group demand when an actual field query matches it; no table
    of method/field/group entries is built. -/
def gapRelay (defaults : FrozenDefaults) (relay : Run1StaticEarlySummaryGap.Relay) :
    Option Run1StaticEarlySummaryZero.Value → Option Run1StaticEarlySummaryZero.Value
  | some (.staticFull field mark) =>
      if permitted defaults relay.method mark.group then
        Run1StaticEarlySummaryGap.runRelay .strictOldGroup
          { relay with guides := .fieldGroup field mark.group :: relay.guides } (some (.staticFull field mark))
      else none
  | other => Run1StaticEarlySummaryGap.runRelay .strictOldGroup relay other

def crossGap (defaults : FrozenDefaults) : List Run1StaticEarlySummaryGap.Relay →
    Option Run1StaticEarlySummaryZero.Value → Option Run1StaticEarlySummaryZero.Value
  | [], input => input
  | relay :: rest, input => crossGap defaults rest (gapRelay defaults relay input)

theorem gapRelay_matches_current (defaults : FrozenDefaults)
    (relay : Run1StaticEarlySummaryGap.Relay) (field : Nat) (mark : RuleGroupSchedule.FullMark)
    (allowed : permitted defaults relay.method mark.group = true) :
    gapRelay defaults relay (some (.staticFull field mark)) =
      Run1StaticEarlySummaryGap.runRelay .actualIncoming relay (some (.staticFull field mark)) := by
  rw [Run1StaticEarlySummaryGap.actual_query_relay_identity]
  simp [gapRelay, allowed, Run1StaticEarlySummaryGap.runRelay,
    Run1StaticEarlySummaryGap.admits, Run1StaticEarlySummaryGap.hasGroup]

theorem universal_gap_chain_identity (defaults : FrozenDefaults)
    (relays : List Run1StaticEarlySummaryGap.Relay) (field : Nat)
    (mark : RuleGroupSchedule.FullMark) (active : defaults.enabled = true)
    (catalogued : mark.group ∈ defaults.groups) :
    crossGap defaults relays (some (.staticFull field mark)) = some (.staticFull field mark) := by
  induction relays with
  | nil => rfl
  | cons relay rest ih =>
    have allowed : permitted defaults relay.method mark.group = true := by
      simp only [permitted, active, Bool.true_and, List.contains_iff_mem]
      exact catalogued
    rw [crossGap, gapRelay_matches_current defaults relay field mark allowed,
      Run1StaticEarlySummaryGap.actual_query_relay_identity]
    exact ih

theorem universal_matches_current_identity_chain (defaults : FrozenDefaults)
    (relays : List Run1StaticEarlySummaryGap.Relay) (field : Nat)
    (mark : RuleGroupSchedule.FullMark) (active : defaults.enabled = true)
    (catalogued : mark.group ∈ defaults.groups) :
    crossGap defaults relays (some (.staticFull field mark)) =
      Run1StaticEarlySummaryGap.crossRelays .actualIncoming relays (some (.staticFull field mark)) :=
  (universal_gap_chain_identity defaults relays field mark active catalogued).trans
    (Run1StaticEarlySummaryGap.actual_query_chain_identity relays (.staticFull field mark)).symm

def analyzeGap (defaults : FrozenDefaults) (program : Run1StaticEarlySummaryGap.Program) : Bool :=
  Run1StaticEarlySummaryGap.finishAtSource program
    (crossGap defaults program.relays.reverse (Run1StaticEarlySummaryGap.incomingStaticQuery program))

theorem previous_gap_is_closed :
    analyzeGap ⟨true, [7]⟩ Run1StaticEarlySummaryGap.Counterexample.program = true := by decide

namespace Controls

def defaults : FrozenDefaults := ⟨true, [7, 8]⟩
def ap : StaticAP := ⟨5, [10], 1⟩
def query : StaticQuery := ⟨ap, ⟨7, 100⟩⟩
def copyTarget : StaticAP := ⟨6, [20, 30], 2⟩
def relays : List Relay := [⟨1, .identity⟩, ⟨999, .copy copyTarget⟩, ⟨2, .identity⟩]

def controls : List (String × Bool) := [
  ("all method keys share the frozen default", decide (permitted defaults 1 7 = permitted defaults 999 7)),
  ("actual static full query remains precise", decide (emit defaults 1 (some (.staticFull query)) = some (.staticFull query))),
  ("FLOW root default drops the concrete mark", decide (emitFlow (some (.staticFull query)) = some (.staticFlow ap))),
  ("GROUP root default preserves full T", decide (emit defaults 1 (some (.staticFull query)) ≠ emitFlow (some (.staticFull query)))),
  ("absent input cannot emit T", decide (emit defaults 1 none = none)),
  ("actual FLOW cannot emit T", decide (emit defaults 1 (some (.staticFlow ap)) = none)),
  ("unlisted group is rejected", decide (emit ⟨true, [8]⟩ 1 (some (.staticFull query)) = none)),
  ("disabled default is rejected", decide (emit ⟨false, [7]⟩ 1 (some (.staticFull query)) = none)),
  ("actual body kill still removes field", decide (run defaults 1 (.clear 5) query = none)),
  ("disjoint clear preserves actual query", decide (run defaults 1 (.clear 6) query = some (.staticFull query))),
  ("static copy changes only actual AP", decide (run defaults 1 (.copyStatic copyTarget) query = some (.staticFull ⟨copyTarget, query.mark⟩))),
  ("root pair rejects nonstatic RHS", !(publication defaults 1 query (.ordinaryFull 9 query.mark))),
  ("root pair rejects source ZERO", !(publication defaults 1 query .reach)),
  ("root pair rejects cross-group result", !(publication defaults 1 query (.staticFull ⟨ap, ⟨8, 200⟩⟩))),
  ("cross-group rewrite needs productive pair", decide (run defaults 1 (.rewrite ⟨8, 200⟩) query = none)),
  ("finite identity/copy path admits and publishes", decide (runPath defaults relays query = some (.staticFull ⟨copyTarget, query.mark⟩))),
  ("previous strict-guide relay gap is closed", analyzeGap defaults Run1StaticEarlySummaryGap.Counterexample.program),
  ("previous writer explicit ZERO pair still publishes", Run1StaticEarlySummaryZero.publishesSourceZero
    (Run1StaticEarlySummaryZero.writerDemand Run1StaticEarlySummaryZero.Controls.early)),
  ("default does not bypass wrong exact source", !(analyzeGap defaults
    { Run1StaticEarlySummaryGap.Counterexample.program with sink :=
      { Run1StaticEarlySummaryGap.Counterexample.sink with query := ⟨Run1StaticEarlySummaryGap.Counterexample.payload, ⟨7, 101⟩⟩ } }))
]
end Controls
end ApSpec.Run1UniversalStaticCarry
