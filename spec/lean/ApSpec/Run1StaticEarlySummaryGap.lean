/-
  Executable counterexample to universal B2 demand coverage under a strict
  old per-method GROUP gate. Program: set(); pass_only(); consume().
  The relay is a resolved static empty method. Its S base is touched and
  its plan has CALLEES only, with no static PASS_OVER bypass. It has no sink.
  F1 early source summaries do not create an f/G-premised relay summary.
  Current actual-input admission is a separate policy, already in Kotlin.
  This finite model neither changes it nor grants all-group F1 guidance.
-/
import ApSpec.Run1StaticEarlySummaryZero

namespace ApSpec.Run1StaticEarlySummaryGap
set_option autoImplicit false
open Run1StaticEarlySummaryZero
open Run1StaticDemandCoverage

inductive Policy where
  | strictOldGroup | actualIncoming
  deriving DecidableEq, Repr

inductive Guide where
  | flowStaticDefault
  | fieldGroup (field group : Nat)
  deriving DecidableEq, Repr

/-- The small language contains only actual resolved empty static relays.
    Empty-body identity is ordinary semantics, not a summary assumption. -/
structure Relay where
  method : Nat
  guides : List Guide := [.flowStaticDefault]
  deriving DecidableEq, Repr

inductive Route where
  | calleesOnly
  deriving DecidableEq, Repr

def staticTouched (_relay : Relay) : Bool := true
def staticPassOver (_relay : Relay) : Bool := false
def route (_relay : Relay) : Route := .calleesOnly

/-- ZERO reaches the child, but the empty child has no internal sink seed.
    Its ZERO summary has REACH only. It cannot answer a marked f/T input. -/
def zeroChildOutputs (_relay : Relay) : List Value := [.reach]

def hasGroup (relay : Relay) (field group : Nat) : Bool :=
  relay.guides.contains (.fieldGroup field group)

def admits (policy : Policy) (relay : Relay) : Value → Bool
  | .reach => true
  | .staticFull field mark =>
      match policy with
      | .strictOldGroup => hasGroup relay field mark.group
      | .actualIncoming => true

/-- The input is actual query data. No input means no admission. In current
    mode its canonical full initial also authorizes its ordinary publication.
    The identity body preserves the exact field and full mark. -/
def runRelay (policy : Policy) (relay : Relay) (input : Option Value) : Option Value :=
  input.bind (fun actual =>
    if admits policy relay actual then
      let currentQueries := [actual]
      if currentQueries.contains actual then some actual else none
    else none)

def crossRelays (policy : Policy) : List Relay → Option Value → Option Value
  | [], input => input
  | relay :: rest, input => crossRelays policy rest (runRelay policy relay input)

theorem actual_query_relay_identity (relay : Relay) (value : Value) :
    runRelay .actualIncoming relay (some value) = some value := by
  cases value <;> simp [runRelay, admits]

theorem actual_query_chain_identity (relays : List Relay) (value : Value) :
    crossRelays .actualIncoming relays (some value) = some value := by
  induction relays with
  | nil => rfl
  | cons relay rest ih => simpa only [crossRelays, actual_query_relay_identity] using ih

theorem absent_query_cannot_create_mark (policy : Policy) (relay : Relay) :
    runRelay policy relay none = none := rfl

theorem zero_child_cannot_answer_static (relay : Relay) (field : Nat)
    (mark : RuleGroupSchedule.FullMark) :
    Value.staticFull field mark ∉ zeroChildOutputs relay := by
  simp [zeroChildOutputs]

structure Program where
  source : SourceWrite
  relays : List Relay
  sink : SeedOrigin
  sinkSeedPresent : Bool := true
  getterRegistered : Bool := true
  deriving DecidableEq, Repr

/-- F1 emits early setter/main Z->f/G and a getter f/G->ret/G guide.
    Relay guidance is unchanged: default FLOW is not a demanded group. -/
def getterGuide (program : Program) : Option GroupGuide :=
  if program.getterRegistered = true then
    (earlySourceWrite program.source).map (fun summary =>
      ⟨⟨⟨99, .staticField summary.field⟩, .ordinary 1, 2, .pairedSummary,
        program.sink.query.payload⟩, summary.group⟩)
  else none

/-- The reference executes the actual source write and then actual empty
    bodies. Exact full source/sink identity is required, not just owner G. -/
def referenceAccepts (program : Program) : Bool :=
  program.source.sourceExecuted && program.source.storeReached &&
    program.sinkSeedPresent && decide (program.source.mark = program.sink.query.mark)

inductive ActualEvent where
  | sourceWrite (source field : Nat) (mark : RuleGroupSchedule.FullMark)
  | emptyStaticCall (method field : Nat) (mark : RuleGroupSchedule.FullMark)
  | sinkRead (field : Nat) (mark : RuleGroupSchedule.FullMark)
  deriving DecidableEq, Repr

/-- This executable full-mark trace supplies the concrete counterexample
    witness. Each relay step has identical incoming/outgoing field state. -/
def actualTrace (program : Program) : List ActualEvent :=
  .sourceWrite program.source.source program.source.field program.source.mark ::
    (program.relays.map (fun relay => .emptyStaticCall relay.method program.source.field program.source.mark)) ++
      [.sinkRead program.source.field program.sink.query.mark]

/-- The dead/local sink seed and its getter guide form Z->f/T in consume.
    At the earlier relay call, static f is touched, so this actual marked
    value must enter the resolved callee through a NONZERO f/T initial. -/
def incomingStaticQuery (program : Program) : Option Value :=
  (startDeep 10 program.source.field program.sink program.sinkSeedPresent true
    (getterGuide program) ⟨.zero, .reach⟩).map (fun summary =>
      .staticFull summary.field summary.origin.query.mark)

def finishAtSource (program : Program) : Option Value → Bool
  | some (.staticFull field mark) =>
      match earlySourceWrite program.source with
      | none => false
      | some summary =>
          let demand := writerDemand summary
          writerAdmits demand field mark && publishesSourceZero demand &&
            decide (reverseActualSource (.nonzero field) mark program.source =
              some ⟨.nonzero field, .reach⟩)
  | _ => false

/-- Only actual resolved-callee crossing is available at the relay. The
    separate ZERO child has no marked publication and offers no fallback. -/
def analyze (policy : Policy) (program : Program) : Bool :=
  finishAtSource program (crossRelays policy program.relays.reverse (incomingStaticQuery program))

/-- A universal no-loss claim over this small executable language. A
    failure here disproves the wider unconditional strict-guide claim. -/
def NoLoss (policy : Policy) : Prop :=
  ∀ program, referenceAccepts program = true → analyze policy program = true

namespace Counterexample

def payload : RuleGroupSchedule.QueryPayload := ⟨100, 8, [], [7]⟩
def sink : SeedOrigin := ⟨⟨10, 20, [10], 8⟩, ⟨payload, ⟨7, 100⟩⟩⟩
def setter : SourceWrite := ⟨6, 1000, 8, ⟨7, 100⟩, true, true⟩
def passOnly : Relay := ⟨2, [.flowStaticDefault]⟩
def program : Program := ⟨setter, [passOnly], sink, true, true⟩

theorem actual_full_trace_exists : referenceAccepts program = true := by decide

theorem strict_misses : analyze .strictOldGroup program = false := by decide

theorem current_actual_query_succeeds : analyze .actualIncoming program = true := by decide

/-- Concrete executable counterexample data, rather than classical
    selection of a witness from an existential proposition. -/
def witness : { candidate : Program // referenceAccepts candidate = true ∧
    analyze .strictOldGroup candidate = false ∧ analyze .actualIncoming candidate = true } :=
  ⟨program, actual_full_trace_exists, strict_misses, current_actual_query_succeeds⟩

end Counterexample

theorem strict_universal_no_loss_false : ¬ NoLoss .strictOldGroup := by
  intro allPrograms
  have accepted := allPrograms Counterexample.program Counterexample.actual_full_trace_exists
  rw [Counterexample.strict_misses] at accepted
  contradiction

namespace Controls
open Counterexample

def controls : List (String × Bool) := [
  ("actual source passes empty relay to exact sink T", referenceAccepts program),
  ("finite actual trace has source relay sink", decide ((actualTrace program).length = 3)),
  ("resolved static relay touches S", staticTouched passOnly),
  ("resolved static relay has no PASS_OVER", !(staticPassOver passOnly)),
  ("marked query must use CALLEES", decide (route passOnly = .calleesOnly)),
  ("F1 relay has only FLOW default", decide (passOnly.guides = [.flowStaticDefault])),
  ("early source summary does not add relay G guide", !(hasGroup passOnly 8 7)),
  ("actual sink/getter produces marked f/T query", decide (incomingStaticQuery program = some (.staticFull 8 ⟨7, 100⟩))),
  ("strict gate rejects actual f/T at relay", decide (runRelay .strictOldGroup passOnly (incomingStaticQuery program) = none)),
  ("current fallback admits actual f/T unchanged", decide (runRelay .actualIncoming passOnly (incomingStaticQuery program) = incomingStaticQuery program)),
  ("ZERO child has no static T answer", decide (Value.staticFull 8 ⟨7, 100⟩ ∉ zeroChildOutputs passOnly)),
  ("strict policy loses real trace", !(analyze .strictOldGroup program)),
  ("current actual-query policy keeps real trace", analyze .actualIncoming program),
  ("no relay means strict example succeeds", analyze .strictOldGroup { program with relays := [] }),
  ("two empty relays still defeat strict gate", !(analyze .strictOldGroup { program with relays := [passOnly, ⟨3, [.flowStaticDefault]⟩] })),
  ("two empty relays pass current actual queries", analyze .actualIncoming { program with relays := [passOnly, ⟨3, [.flowStaticDefault]⟩] }),
  ("absent actual input cannot create a mark", decide (runRelay .actualIncoming passOnly none = none)),
  ("current ZERO child remains unmarked REACH", decide (runRelay .actualIncoming passOnly (some .reach) = some .reach)),
  ("absent getter guide blocks local requirement", !(analyze .actualIncoming { program with getterRegistered := false })),
  ("absent sink seed cannot start B2 marked query", !(analyze .actualIncoming { program with sinkSeedPresent := false })),
  ("wrong source group cannot satisfy sink", !(analyze .actualIncoming { program with source := { setter with mark := ⟨9, 100⟩ } })),
  ("same group different full mark is not actual source", !(analyze .actualIncoming { program with sink := { sink with query := ⟨payload, ⟨7, 101⟩⟩ } })),
  ("same group different full mark is not a reference flow", !(referenceAccepts { program with sink := { sink with query := ⟨payload, ⟨7, 101⟩⟩ } })),
  ("unexecuted source has no accepted reference flow", !(referenceAccepts { program with source := { setter with sourceExecuted := false } })),
  ("unexecuted source has no backward source answer", !(analyze .actualIncoming { program with source := { setter with sourceExecuted := false } }))
]
end Controls
end ApSpec.Run1StaticEarlySummaryGap
