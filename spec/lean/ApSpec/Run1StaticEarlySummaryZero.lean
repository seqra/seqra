/-
  Early static write summaries and the existing backward ZERO-only route.
  Scope: finite actually reached compatible wrapper calls, identity local
  static transfer, one explicit getter group guide and one source write.
  Backward ZERO-premised summaries publish without a nonzero demand. Only
  genuine ZERO->REACH creates ZeroIn, zero-only subscriptions and sink seeds.
  This is not coverage of arbitrary pass-only calls under a nonzero gate.
-/
import ApSpec.Run1StaticReadWritePath

namespace ApSpec.Run1StaticEarlySummaryZero
set_option autoImplicit false
open Run1StaticDemandCoverage
open Run1StaticReadWritePath (Port CallPort LocalPass)

inductive Original where
  | zero
  | nonzero (identity : Nat)
  deriving DecidableEq, Repr

inductive Value where
  | reach
  | staticFull (field : Nat) (mark : RuleGroupSchedule.FullMark)
  deriving DecidableEq, Repr

structure Edge where
  original : Original
  value : Value
  deriving DecidableEq, Repr

structure ZeroEffects where
  unchanged : Bool
  sinkSeeds : Bool
  zeroOnly : Bool
  zeroIn : Bool
  deriving DecidableEq, Repr

/-- `zeroAtCall` first keeps REACH. A nonzero original then returns. It
    cannot start the ZERO protocol merely because its current value is REACH. -/
def backwardZeroAtCall (edge : Edge) : ZeroEffects :=
  match edge.value with
  | .reach =>
      match edge.original with
      | .zero => ⟨true, true, true, true⟩
      | .nonzero _ => ⟨true, false, false, false⟩
  | .staticFull _ _ => ⟨false, false, false, false⟩

theorem nonzero_reach_no_zero_protocol (identity : Nat) :
    backwardZeroAtCall ⟨.nonzero identity, .reach⟩ = ⟨true, false, false, false⟩ := rfl

theorem genuine_zero_starts_protocol :
    backwardZeroAtCall ⟨.zero, .reach⟩ = ⟨true, true, true, true⟩ := rfl

structure SeedOrigin where
  sink : Port
  query : RuleGroupSchedule.FullQuery
  deriving DecidableEq, Repr

structure BackwardSummary where
  method : Nat
  original : Original
  field : Nat
  origin : SeedOrigin
  deriving DecidableEq, Repr

/-- This models the actual backward ZERO-original publication bypass.
    It depends on the original, not on current REACH or a wrapper F1 guide. -/
def unrestrictedBackward (summary : BackwardSummary) : Bool :=
  summary.original == .zero

theorem zero_summary_unrestricted (method field : Nat) (origin : SeedOrigin) :
    unrestrictedBackward ⟨method, .zero, field, origin⟩ = true := rfl

structure WrapperPass where
  call : Nat
  field : Nat
  transfer : LocalPass
  deriving DecidableEq, Repr

structure Frames where
  calls : List CallPort
  reached : List Nat
  passes : List WrapperPass := []
  deriving DecidableEq, Repr

/-- `reached` is actual ZERO/control call reachability in the finite witness.
    A prescan candidate alone is insufficient. No exit summary is required.
    The local control compiler must establish the between-call ZERO passes. -/
def legal (frames : Frames) (call : CallPort) : Prop :=
  call ∈ frames.calls ∧ call.identity ∈ frames.reached ∧ call.compatible = true ∧ call.consistent

instance legalDecidable (frames : Frames) (call : CallPort) : Decidable (legal frames call) := by
  unfold legal
  infer_instance

/-- A field-specific ordinary body certificate moves the call reply from
    caller-before to caller-entry. The compatible bit certifies the actual
    identity/no-hit primitive path for this field. A strong kill blocks it.
    ZERO control reachability alone does not establish this value path. -/
def bodyLegal (frames : Frames) (call : CallPort) (field : Nat) (pass : WrapperPass) : Prop :=
  pass ∈ frames.passes ∧ pass.call = call.identity ∧ pass.field = field ∧
    pass.transfer.after = call.callerBefore ∧ pass.transfer.compatible = true ∧
    pass.transfer.before.method = call.callerBefore.method ∧
    pass.transfer.before.context = call.callerBefore.context ∧
    pass.transfer.before.region = call.callerBefore.region

instance bodyLegalDecidable (frames : Frames) (call : CallPort) (field : Nat) (pass : WrapperPass) :
    Decidable (bodyLegal frames call field pass) := by unfold bodyLegal; infer_instance

structure ZeroSubscription where
  call : CallPort
  callerOriginal : Original
  zeroOnly : Bool
  deriving DecidableEq, Repr

def subscribe (frames : Frames) (call : CallPort) (edge : Edge) : Option ZeroSubscription :=
  if legal frames call ∧ (backwardZeroAtCall edge).zeroOnly = true then
    some ⟨call, .zero, true⟩
  else none

/-- ZERO-only application preserves caller ZERO. The original sink/query
    and its complete full mark are immutable. There is no added f initial. -/
def liftBackward (subscription : ZeroSubscription) (summary : BackwardSummary) :
    Option BackwardSummary :=
  if subscription.zeroOnly = true ∧ subscription.callerOriginal = .zero ∧
      summary.original = .zero ∧ summary.method = subscription.call.calleeEntry.method then
    some { summary with method := subscription.call.callerBefore.method, original := .zero }
  else none

/-- Both old and new publications join the same reached subscription. -/
def joinedReply (frames : Frames) (call : CallPort) (edge : Edge)
    (summary : BackwardSummary) : Option BackwardSummary :=
  (subscribe frames call edge).bind (fun subscription => liftBackward subscription summary)

theorem subscribe_genuine_zero (frames : Frames) (call : CallPort) (reached : legal frames call) :
    subscribe frames call ⟨.zero, .reach⟩ = some ⟨call, .zero, true⟩ := by
  simp only [subscribe, genuine_zero_starts_protocol, reached, and_self, reduceIte]

theorem subscribe_nonzero_reach (frames : Frames) (call : CallPort) (identity : Nat) :
    subscribe frames call ⟨.nonzero identity, .reach⟩ = none := by
  simp only [subscribe, backwardZeroAtCall, Bool.false_eq_true, and_false, reduceIte]

theorem lift_zero_reply (call : CallPort) (field : Nat) (origin : SeedOrigin) :
    liftBackward ⟨call, .zero, true⟩ ⟨call.calleeEntry.method, .zero, field, origin⟩ =
      some ⟨call.callerBefore.method, .zero, field, origin⟩ := by
  simp only [liftBackward, and_self, reduceIte]

def passToCallerEntry (frames : Frames) (call : CallPort) (field : Nat)
    (pass : WrapperPass) (summary : BackwardSummary) : Option BackwardSummary :=
  if bodyLegal frames call field pass ∧ summary.method = call.callerBefore.method ∧ summary.field = field then
    some summary
  else none

/-- The finite chain uses actual reached caller/callee ports. It contains
    no per-wrapper nonzero guide, static entry fact or writer-inventory jump. -/
inductive Chain (frames : Frames) (field : Nat) : Nat → Nat → Type where
  | done (method : Nat) : Chain frames field method method
  | next (call : CallPort) (reached : legal frames call)
      (pass : WrapperPass) (survives : bodyLegal frames call field pass) {deep : Nat}
      (rest : Chain frames field call.calleeEntry.method deep) :
      Chain frames field call.callerBefore.method deep

def Chain.calls {frames : Frames} {field root deep : Nat} : Chain frames field root deep → List CallPort
  | .done _ => []
  | .next call _ _ _ rest => call :: rest.calls

structure Lifted (frames : Frames) (origin : SeedOrigin) (field root deep : Nat)
    (path : Chain frames field root deep) where
  summary : BackwardSummary
  result : summary = ⟨root, .zero, field, origin⟩
  unrestricted : unrestrictedBackward summary = true
  subscriptions : List ZeroSubscription
  realPorts : subscriptions.map ZeroSubscription.call = path.calls

/-- A deep Z->f/T summary lifts through an arbitrary finite wrapper chain.
    The depth changes neither full T nor the original sink/context. -/
def liftChain (frames : Frames) (origin : SeedOrigin) (field : Nat) :
    {root deep : Nat} → (path : Chain frames field root deep) → Lifted frames origin field root deep path
  | root, _, .done _ =>
      ⟨⟨root, .zero, field, origin⟩, rfl, zero_summary_unrestricted root field origin, [], rfl⟩
  | _, _, .next call reached pass survives rest =>
      let child := liftChain frames origin field rest
      { summary := ⟨call.callerBefore.method, .zero, field, origin⟩
        result := rfl
        unrestricted := zero_summary_unrestricted _ _ _
        subscriptions := ⟨call, .zero, true⟩ :: child.subscriptions
        realPorts := by simp only [List.map_cons, Chain.calls, child.realPorts] }

/-- Every chain link is an actual subscribed ZERO response, not just a
    fabricated result with the right endpoint. -/
theorem one_chain_link_is_reply (frames : Frames) (call : CallPort)
    (reached : legal frames call) (origin : SeedOrigin) (field : Nat) :
    joinedReply frames call ⟨.zero, .reach⟩ ⟨call.calleeEntry.method, .zero, field, origin⟩ =
      some ⟨call.callerBefore.method, .zero, field, origin⟩ := by
  rw [joinedReply, subscribe_genuine_zero frames call reached]
  exact lift_zero_reply call field origin

/-- Executable ordinary response composition. Each wrapper consumes the
    actual child publication through its actual ZERO-only subscription. -/
def runChain (frames : Frames) (origin : SeedOrigin) (field : Nat) :
    {root deep : Nat} → Chain frames field root deep → Option BackwardSummary
  | method, _, .done _ => some ⟨method, .zero, field, origin⟩
  | _, _, .next call _ pass _ rest =>
      (runChain frames origin field rest).bind (fun child =>
        (joinedReply frames call ⟨.zero, .reach⟩ child).bind
          (passToCallerEntry frames call field pass))

theorem runChain_result (frames : Frames) (origin : SeedOrigin) (field : Nat)
    {root deep : Nat} (path : Chain frames field root deep) :
    runChain frames origin field path = some ⟨root, .zero, field, origin⟩ := by
  induction path with
  | done => rfl
  | next call reached pass survives rest ih =>
    rw [runChain, ih]
    change (joinedReply frames call ⟨.zero, .reach⟩
      ⟨call.calleeEntry.method, .zero, field, origin⟩).bind
        (passToCallerEntry frames call field pass) = _
    rw [one_chain_link_is_reply frames call reached origin field]
    simp only [Option.bind_some, passToCallerEntry, survives, and_self, reduceIte]

theorem runChain_matches_certificate (frames : Frames) (origin : SeedOrigin) (field : Nat)
    {root deep : Nat} (path : Chain frames field root deep) :
    runChain frames origin field path = some (liftChain frames origin field path).summary := by
  rw [(liftChain frames origin field path).result]
  exact runChain_result frames origin field path

/-- Strict getter admission stays necessary. The compatible bit certifies
    the actual getter return/input binding, AP geometry, operation stage and
    identity transfer ret/T -> f/T. The explicit field check is separate.
    A local sink seed and ZERO cannot replace the ret/G -> f/G demand. -/
def startDeep (method field : Nat) (origin : SeedOrigin) (seedPresent compatible : Bool)
    (getter : Option GroupGuide) (control : Edge) : Option BackwardSummary :=
  if seedPresent = true ∧ compatible = true ∧ (backwardZeroAtCall control).sinkSeeds = true then
    getter.bind (fun guide =>
      if guide.row.original.kind = .staticField field then
        (emitFromGroupGuide guide origin.query.mark).map (fun _ => ⟨method, .zero, field, origin⟩)
      else none)
  else none

/-- A real source write is observed at the reached store, before any method
    normal exit. Full source metadata is projected to owner G only. The two
    booleans are primitive source/action and write-stage certificates. -/
structure SourceWrite where
  method : Nat
  source : Nat
  field : Nat
  mark : RuleGroupSchedule.FullMark
  sourceExecuted : Bool
  storeReached : Bool
  deriving DecidableEq, Repr

structure EarlySummary where
  method : Nat
  source : Nat
  field : Nat
  group : Nat
  deriving DecidableEq, Repr

/-- This carrier is a ZERO-premised source effect. ZERO is never a group. -/
def EarlySummary.original (_summary : EarlySummary) : Original := .zero

def earlySourceWrite (write : SourceWrite) : Option EarlySummary :=
  if write.sourceExecuted = true ∧ write.storeReached = true then
    some ⟨write.method, write.source, write.field, write.mark.group⟩
  else none

/-- F1 caller application retains ZERO-source provenance and immediately
    republishes the static result. It creates no normal-return control. -/
def liftEarly (frames : Frames) (call : CallPort) (summary : EarlySummary) : Option EarlySummary :=
  if legal frames call ∧ summary.method = call.calleeEntry.method then
    some { summary with method := call.callerBefore.method }
  else none

theorem early_reply_keeps_source (frames : Frames) (call : CallPort)
    (reached : legal frames call) (source field group : Nat) :
    liftEarly frames call ⟨call.calleeEntry.method, source, field, group⟩ =
      some ⟨call.callerBefore.method, source, field, group⟩ := by
  simp only [liftEarly, reached, and_self, reduceIte]

structure WriterDemand where
  method : Nat
  source : Nat
  field : Nat
  group : Nat
  exit : Option Original
  deriving DecidableEq, Repr

/-- Reverse Z->f/G into f/G->Z. Some ZERO is an explicit exit pattern;
    it differs from an absent exit, which cannot publish a paired result. -/
def writerDemand (summary : EarlySummary) : WriterDemand :=
  ⟨summary.method, summary.source, summary.field, summary.group, some .zero⟩

def writerAdmits (demand : WriterDemand) (field : Nat) (mark : RuleGroupSchedule.FullMark) : Bool :=
  decide (demand.field = field ∧ demand.group = mark.group)

def publishesSourceZero (demand : WriterDemand) : Bool := demand.exit == some .zero

theorem writer_has_explicit_zero (summary : EarlySummary) :
    (writerDemand summary).exit = some .zero ∧ publishesSourceZero (writerDemand summary) = true := by
  exact ⟨rfl, rfl⟩

theorem absent_exit_cannot_publish_zero (demand : WriterDemand) :
    publishesSourceZero { demand with exit := none } = false := rfl

/-- Actual source reversal requires the full source mark, not only G.
    Its output is ordinary unmarked REACH/ZERO. A nonzero query original
    remains nonzero, so this result cannot trigger the ZERO protocol. -/
def reverseActualSource (original : Original) (incoming : RuleGroupSchedule.FullMark)
    (write : SourceWrite) : Option Edge :=
  if write.sourceExecuted = true ∧ incoming = write.mark then some ⟨original, .reach⟩ else none

theorem reversed_source_nonzero_no_zero_protocol (identity : Nat)
    (incoming : RuleGroupSchedule.FullMark) (write : SourceWrite)
    (executed : write.sourceExecuted = true) (same : incoming = write.mark) :
    reverseActualSource (.nonzero identity) incoming write = some ⟨.nonzero identity, .reach⟩ ∧
      backwardZeroAtCall ⟨.nonzero identity, .reach⟩ = ⟨true, false, false, false⟩ := by
  exact ⟨by simp only [reverseActualSource, executed, same, and_self, reduceIte], rfl⟩

namespace Controls

def port (method point : Nat) : Port := ⟨method, point, [method], 5⟩
def frame (caller callee : Nat) : CallPort :=
  ⟨caller, port caller 1, port caller 2, port callee 0, port callee 9, .normal, true, true⟩
def body (caller callee : Nat) : WrapperPass :=
  ⟨caller, 8, ⟨port caller 0, (frame caller callee).callerBefore, true⟩⟩
def frames : Frames := ⟨[frame 1 2, frame 2 3, frame 3 4], [1, 2, 3],
  [body 1 2, body 2 3, body 3 4]⟩
def origin : SeedOrigin := ⟨port 4 6, ⟨⟨90, 5, [], [7]⟩, ⟨7, 100⟩⟩⟩
def payload : RuleGroupSchedule.QueryPayload := origin.query.payload
def getterRow : Row := ⟨⟨5, .staticField 8⟩, .ordinary 1, 2, .pairedSummary, payload⟩
def getterGuide : GroupGuide := ⟨getterRow, 7⟩
def chain : Chain frames 8 1 4 :=
  .next (frame 1 2) (by decide) (body 1 2) (by decide)
    (.next (frame 2 3) (by decide) (body 2 3) (by decide)
      (.next (frame 3 4) (by decide) (body 3 4) (by decide) (.done 4)))
def lifted : Lifted frames origin 8 1 4 chain := liftChain frames origin 8 chain
def write : SourceWrite := ⟨6, 1000, 8, ⟨7, 100⟩, true, true⟩
def early : EarlySummary := ⟨6, 1000, 8, 7⟩

def controls : List (String × Bool) := [
  ("genuine ZERO starts sink and call protocol", decide (backwardZeroAtCall ⟨.zero, .reach⟩ = ⟨true, true, true, true⟩)),
  ("nonzero REACH creates no ZeroIn", !(backwardZeroAtCall ⟨.nonzero 1, .reach⟩).zeroIn),
  ("nonzero REACH fires no sink seed", !(backwardZeroAtCall ⟨.nonzero 1, .reach⟩).sinkSeeds),
  ("ZERO with current taint is not ZERO control", !(backwardZeroAtCall ⟨.zero, .staticFull 8 ⟨7, 100⟩⟩).zeroOnly),
  ("three wrappers retain actual call ports", decide (lifted.subscriptions.map ZeroSubscription.call = chain.calls)),
  ("three wrappers preserve ZERO original", decide (lifted.summary.original = .zero)),
  ("three wrappers preserve full mark and sink context", decide (lifted.summary.origin = origin)),
  ("wrapper publication needs no nonzero F1 demand", unrestrictedBackward lifted.summary),
  ("sink seed and getter guide start deep summary", decide (startDeep 4 8 origin true true (some getterGuide) ⟨.zero, .reach⟩ = some ⟨4, .zero, 8, origin⟩)),
  ("absent getter guide blocks strict mark query", decide (startDeep 4 8 origin true true none ⟨.zero, .reach⟩ = none)),
  ("wrong getter group blocks strict mark query", decide (startDeep 4 8 origin true true (some ⟨getterRow, 9⟩) ⟨.zero, .reach⟩ = none)),
  ("absent sink seed produces no local summary", decide (startDeep 4 8 origin false true (some getterGuide) ⟨.zero, .reach⟩ = none)),
  ("nonzero REACH cannot start local seed", decide (startDeep 4 8 origin true true (some getterGuide) ⟨.nonzero 1, .reach⟩ = none)),
  ("actual early write projects only group", decide (earlySourceWrite write = some early)),
  ("unexecuted source supplies no early writer", decide (earlySourceWrite { write with sourceExecuted := false } = none)),
  ("unreached store supplies no early writer", decide (earlySourceWrite { write with storeReached := false } = none)),
  ("writer demand has explicit ZERO exit", decide ((writerDemand early).exit = some .zero)),
  ("writer group admits full T", writerAdmits (writerDemand early) 8 ⟨7, 100⟩),
  ("writer group admits another full member", writerAdmits (writerDemand early) 8 ⟨7, 101⟩),
  ("writer rejects wrong group", !(writerAdmits (writerDemand early) 8 ⟨9, 100⟩)),
  ("writer rejects wrong field", !(writerAdmits (writerDemand early) 9 ⟨7, 100⟩)),
  ("null exit cannot publish source ZERO", !(publishesSourceZero { writerDemand early with exit := none })),
  ("actual source emits unmarked ZERO", decide (reverseActualSource (.nonzero 8) ⟨7, 100⟩ write = some ⟨.nonzero 8, .reach⟩)),
  ("wrong full mark in same group is not this source", decide (reverseActualSource (.nonzero 8) ⟨7, 101⟩ write = none)),
  ("computed three-wrapper reply preserves sink/full T", decide (runChain frames origin 8 chain = some ⟨1, .zero, 8, origin⟩)),
  ("depth zero needs no wrapper guide", decide (runChain frames origin 8 (.done 4) = some ⟨4, .zero, 8, origin⟩)),
  ("depth one uses actual ZERO response", decide (runChain frames origin 8
    (.next (frame 3 4) (by decide) (body 3 4) (by decide) (.done 4)) = some ⟨3, .zero, 8, origin⟩)),
  ("wrong getter field is rejected", decide (startDeep 4 9 origin true true (some getterGuide) ⟨.zero, .reach⟩ = none)),
  ("incompatible getter binding is rejected", decide (startDeep 4 8 origin true false (some getterGuide) ⟨.zero, .reach⟩ = none)),
  ("early writer lifts with same source/field/group", decide (liftEarly frames (frame 3 4) ⟨4, 1000, 8, 7⟩ = some ⟨3, 1000, 8, 7⟩)),
  ("early writer cannot lift at unrelated call", decide (liftEarly frames (frame 3 4) early = none)),
  ("early write has ZERO original", decide (early.original = .zero)),
  ("registered killing body blocks publication", decide (passToCallerEntry
    { frames with passes := [{ body 3 4 with transfer := { (body 3 4).transfer with compatible := false } }] }
    (frame 3 4) 8 { body 3 4 with transfer := { (body 3 4).transfer with compatible := false } }
    ⟨3, .zero, 8, origin⟩ = none)),
  ("body certificate for another field is insufficient", decide (passToCallerEntry frames (frame 3 4) 9
    (body 3 4) ⟨3, .zero, 9, origin⟩ = none))
]
end Controls
end ApSpec.Run1StaticEarlySummaryZero
