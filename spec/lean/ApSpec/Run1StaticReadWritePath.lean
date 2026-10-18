/-
  B2 static read-to-write movement through actual local and call ports.
  Scope: one fixed field/region and one full mark along a finite successful
  static corridor. Rooted F1 field/group supply and control-eligible method
  defaults build frozen carry guides. No marked F1 carrier, inventory jump,
  backward provider graph or method-entry replay is used.
  Ordinary write reversal pivots to its actual RHS. An explicit writer guide
  permits its nonstatic paired output; a field identity guide does not.
-/
import ApSpec.Run1StaticDemandCoverage

namespace ApSpec.Run1StaticReadWritePath
set_option autoImplicit false
open Run1StaticDemandCoverage

structure Port where
  method : Nat
  point : Nat
  context : List Nat
  region : Nat
  deriving DecidableEq, Repr

inductive ExitKind where
  | normal | exceptional
  deriving DecidableEq, Repr

/-- The inventory is the real prescan/control call-port inventory. A port
    records the exact caller statement, target MethodKey/context and stage.
    Compatibility certifies unchanged static field/region and real binding. -/
structure CallPort where
  identity : Nat
  callerBefore : Port
  callerAfter : Port
  calleeEntry : Port
  calleeExit : Port
  kind : ExitKind
  compatible : Bool
  exitReached : Bool
  deriving DecidableEq, Repr

/-- This primitive inventory contains no hit-overwrite operation. Its adapter
    must certify that each local movement passes the queried static value.
    An actual strong overwrite is handled only by the writer pivot below. -/
structure LocalPass where
  before : Port
  after : Port
  compatible : Bool
  deriving DecidableEq, Repr

structure Snapshot where
  carry : CarryStore
  calls : List CallPort
  localPasses : List LocalPass
  deriving DecidableEq, Repr

/-- A method default is independent of marks. This closure condition is
    primitive inventory completeness, not an assumed mark-demand result.
    It must cover all prescan/control-eligible methods, including methods
    without a nonzero run-1 analyzer or normal exit. -/
def DefaultsClosed (snapshot : Snapshot) : Prop :=
  (∀ call, call ∈ snapshot.calls →
    call.callerBefore.method ∈ snapshot.carry.defaultMethods ∧
    call.callerAfter.method ∈ snapshot.carry.defaultMethods ∧
    call.calleeEntry.method ∈ snapshot.carry.defaultMethods ∧
    call.calleeExit.method ∈ snapshot.carry.defaultMethods) ∧
  (∀ pass, pass ∈ snapshot.localPasses →
    pass.before.method ∈ snapshot.carry.defaultMethods ∧
    pass.after.method ∈ snapshot.carry.defaultMethods)

def callMethods (call : CallPort) : List Nat :=
  [call.callerBefore.method, call.callerAfter.method, call.calleeEntry.method, call.calleeExit.method]

def passMethods (pass : LocalPass) : List Nat := [pass.before.method, pass.after.method]

/-- The builder uses the closed prescan/control inventory, including real
    ports and methods without nonzero analyzers. It does not enumerate a
    method x field product. Fields/groups remain one shared relation. -/
def buildSnapshot (methods : List Nat) (groups : List FieldGroup)
    (calls : List CallPort) (passes : List LocalPass) : Snapshot :=
  ⟨⟨(methods ++ calls.flatMap callMethods ++ passes.flatMap passMethods).eraseDups, groups⟩,
    calls, passes⟩

theorem builder_call_method (methods : List Nat) (groups : List FieldGroup)
    (calls : List CallPort) (passes : List LocalPass) (call : CallPort)
    (registered : call ∈ calls) (method : Nat) (atPort : method ∈ callMethods call) :
    method ∈ (buildSnapshot methods groups calls passes).carry.defaultMethods := by
  apply List.mem_eraseDups.mpr
  exact List.mem_append.mpr (Or.inl (List.mem_append.mpr (Or.inr
    (List.mem_flatMap.mpr ⟨call, registered, atPort⟩))))

theorem builder_pass_method (methods : List Nat) (groups : List FieldGroup)
    (calls : List CallPort) (passes : List LocalPass) (pass : LocalPass)
    (registered : pass ∈ passes) (method : Nat) (atPort : method ∈ passMethods pass) :
    method ∈ (buildSnapshot methods groups calls passes).carry.defaultMethods := by
  apply List.mem_eraseDups.mpr
  exact List.mem_append.mpr (Or.inr (List.mem_flatMap.mpr ⟨pass, registered, atPort⟩))

theorem builder_defaults_closed (methods : List Nat) (groups : List FieldGroup)
    (calls : List CallPort) (passes : List LocalPass) :
    DefaultsClosed (buildSnapshot methods groups calls passes) := by
  constructor
  · intro call registered
    exact ⟨builder_call_method methods groups calls passes call registered _ (by simp [callMethods]),
      builder_call_method methods groups calls passes call registered _ (by simp [callMethods]),
      builder_call_method methods groups calls passes call registered _ (by simp [callMethods]),
      builder_call_method methods groups calls passes call registered _ (by simp [callMethods])⟩
  · intro pass registered
    exact ⟨builder_pass_method methods groups calls passes pass registered _ (by simp [passMethods]),
      builder_pass_method methods groups calls passes pass registered _ (by simp [passMethods])⟩

inductive Move where
  | localPass (pass : LocalPass)
  | enterCalleeAtExit (call : CallPort)
  | leaveCalleeAtEntry (call : CallPort)
  deriving DecidableEq, Repr

def Move.start : Move → Port
  | .localPass pass => pass.after
  | .enterCalleeAtExit call => call.callerAfter
  | .leaveCalleeAtEntry call => call.calleeEntry

def Move.finish : Move → Port
  | .localPass pass => pass.before
  | .enterCalleeAtExit call => call.calleeExit
  | .leaveCalleeAtEntry call => call.callerBefore

/-- These equalities are checked even for a registered call port. The
    remaining compatible bit certifies the actual alias/stage binding. -/
def CallPort.consistent (call : CallPort) : Prop :=
  call.callerBefore.method = call.callerAfter.method ∧
  call.callerBefore.context = call.callerAfter.context ∧
  call.calleeEntry.method = call.calleeExit.method ∧
  call.calleeEntry.context = call.calleeExit.context ∧
  call.callerBefore.region = call.callerAfter.region ∧
  call.callerBefore.region = call.calleeEntry.region ∧
  call.callerBefore.region = call.calleeExit.region

instance callConsistentDecidable (call : CallPort) : Decidable call.consistent := by
  unfold CallPort.consistent
  infer_instance

/-- The only cross-method moves are actual registered call entry/exit
    bindings. A global writer lookup is deliberately absent. -/
def permitted (snapshot : Snapshot) : Move → Prop
  | .localPass pass => pass ∈ snapshot.localPasses ∧ pass.compatible = true ∧
      pass.before.method = pass.after.method ∧ pass.before.context = pass.after.context ∧
      pass.before.region = pass.after.region
  | .enterCalleeAtExit call => call ∈ snapshot.calls ∧ call.compatible = true ∧ call.consistent ∧
      call.exitReached = true
  | .leaveCalleeAtEntry call => call ∈ snapshot.calls ∧ call.compatible = true ∧ call.consistent

instance permittedDecidable (snapshot : Snapshot) (move : Move) : Decidable (permitted snapshot move) := by
  cases move <;> unfold permitted <;> infer_instance

theorem move_defaults (snapshot : Snapshot) (closed : DefaultsClosed snapshot)
    (move : Move) (legal : permitted snapshot move) :
    move.start.method ∈ snapshot.carry.defaultMethods ∧
      move.finish.method ∈ snapshot.carry.defaultMethods := by
  cases move with
  | localPass pass =>
      have h := closed.2 pass legal.1
      exact ⟨h.2, h.1⟩
  | enterCalleeAtExit call =>
      have h := closed.1 call legal.1
      exact ⟨h.2.1, h.2.2.2⟩
  | leaveCalleeAtEntry call =>
      have h := closed.1 call legal.1
      exact ⟨h.2.2.1, h.1⟩

structure Origin where
  read : Port
  field : Nat
  query : RuleGroupSchedule.FullQuery
  deriving DecidableEq, Repr

structure Query where
  origin : Origin
  current : Port
  place : Place
  mark : RuleGroupSchedule.FullMark
  deriving DecidableEq, Repr

def atStatic (origin : Origin) (port : Port) : Query :=
  ⟨origin, port, .staticField origin.field, origin.query.mark⟩

def advance (query : Query) (port : Port) : Query := { query with current := port }

theorem advance_keeps_original (query : Query) (port : Port) :
    (advance query port).origin = query.origin ∧
      (advance query port).mark = query.mark := ⟨rfl, rfl⟩

/-- This is a query-only check of BOTH ends of a paired demand piece.
    It does not apply a summary or claim a path reaches the method exit. -/
def pairedPublication (guide : GroupGuide) (entry output : Place)
    (entryMark outputMark : RuleGroupSchedule.FullMark) : Prop :=
  guide.backwardPatterns.1.place = entry ∧ guide.backwardPatterns.2.place = output ∧
    guide.backwardPatterns.1.group = entryMark.group ∧
    guide.backwardPatterns.2.group = outputMark.group

instance pairedPublicationDecidable (guide : GroupGuide) (entry output : Place)
    (entryMark outputMark : RuleGroupSchedule.FullMark) :
    Decidable (pairedPublication guide entry output entryMark outputMark) := by
  unfold pairedPublication
  infer_instance

structure CarryAdmission (snapshot : Snapshot) (origin : Origin) (port : Port) where
  guide : GroupGuide
  existing : carryPermitted snapshot.carry port.method origin.field guide.group
  suppliedGroup : guide.group = origin.query.mark.group
  fieldInput : guide.backwardPatterns.1.place = .staticField origin.field
  fieldOutput : guide.backwardPatterns.2.place = .staticField origin.field
  emits : emitFromGroupGuide guide origin.query.mark = some origin.query.mark
  publishes : pairedPublication guide (.staticField origin.field) (.staticField origin.field)
    origin.query.mark origin.query.mark

/-- This function derives the demanded GROUP from actual frozen builder
    input. The caller does not supply a preexisting per-method/field guide. -/
def carryAdmission (snapshot : Snapshot) (origin : Origin) (port : Port)
    (methodRecorded : port.method ∈ snapshot.carry.defaultMethods)
    (fieldSupplied : (⟨origin.field, origin.query.mark.group⟩ : FieldGroup) ∈ snapshot.carry.staticGroups) :
    CarryAdmission snapshot origin port :=
  let permission : carryPermitted snapshot.carry port.method origin.field origin.query.mark.group :=
    ⟨methodRecorded, fieldSupplied⟩
  let guide := carryGuide snapshot.carry port.method origin.field origin.query.mark.group port.point
    origin.query.payload permission
  { guide := guide
    existing := permission
    suppliedGroup := rfl
    fieldInput := rfl
    fieldOutput := rfl
    emits := explicit_group_emits_full guide origin.query.mark rfl
    publishes := ⟨rfl, rfl, rfl, rfl⟩ }

/-- A finite real backward corridor. It has no writer-jump constructor and
    no move across a hit overwrite. A source/store endpoint comes next. -/
inductive Corridor (snapshot : Snapshot) : Port → Port → Type where
  | done (port : Port) : Corridor snapshot port port
  | next (move : Move) (legal : permitted snapshot move) {last : Port}
      (rest : Corridor snapshot move.finish last) : Corridor snapshot move.start last

def Corridor.moves {snapshot : Snapshot} {start last : Port} :
    Corridor snapshot start last → List Move
  | .done _ => []
  | .next move _ rest => move :: rest.moves

inductive AdmittedCorridor (snapshot : Snapshot) (origin : Origin) : Port → Port → Type where
  | done (port : Port) (admission : CarryAdmission snapshot origin port) :
      AdmittedCorridor snapshot origin port port
  | next (move : Move) (legal : permitted snapshot move)
      (admission : CarryAdmission snapshot origin move.start) {last : Port}
      (rest : AdmittedCorridor snapshot origin move.finish last) :
      AdmittedCorridor snapshot origin move.start last

def AdmittedCorridor.moves {snapshot : Snapshot} {origin : Origin} {start last : Port} :
    AdmittedCorridor snapshot origin start last → List Move
  | .done _ _ => []
  | .next move _ _ rest => move :: rest.moves

/-- Every intermediate method/port gets explicit G guidance and admits the
    actual full T. Defaults and the single supplied field/G row suffice.
    No taint-body replay or dynamically invented B2 demand is involved. -/
def compileCorridor (snapshot : Snapshot) (closed : DefaultsClosed snapshot) (origin : Origin)
    (fieldSupplied : (⟨origin.field, origin.query.mark.group⟩ : FieldGroup) ∈ snapshot.carry.staticGroups) :
    {start last : Port} → Corridor snapshot start last →
    start.method ∈ snapshot.carry.defaultMethods → AdmittedCorridor snapshot origin start last
  | _, _, .done port, recorded => .done port (carryAdmission snapshot origin port recorded fieldSupplied)
  | _, _, .next move legal rest, _ =>
      let methods := move_defaults snapshot closed move legal
      .next move legal (carryAdmission snapshot origin move.start methods.1 fieldSupplied)
        (compileCorridor snapshot closed origin fieldSupplied rest methods.2)

theorem compile_uses_real_moves (snapshot : Snapshot) (closed : DefaultsClosed snapshot)
    (origin : Origin)
    (fieldSupplied : (⟨origin.field, origin.query.mark.group⟩ : FieldGroup) ∈ snapshot.carry.staticGroups)
    {start last : Port} (path : Corridor snapshot start last)
    (recorded : start.method ∈ snapshot.carry.defaultMethods) :
    (compileCorridor snapshot closed origin fieldSupplied path recorded).moves = path.moves := by
  induction path with
  | done => rfl
  | next move legal rest ih =>
      simpa only [compileCorridor, AdmittedCorridor.moves, Corridor.moves] using
        congrArg (List.cons move) (ih (move_defaults snapshot closed move legal).2)

def finalQuery (origin : Origin) {snapshot : Snapshot} {start last : Port}
    (_path : Corridor snapshot start last) : Query := atStatic origin last

theorem corridor_keeps_full_read (origin : Origin) {snapshot : Snapshot} {start last : Port}
    (path : Corridor snapshot start last) :
    (finalQuery origin path).origin = origin ∧
      (finalQuery origin path).mark = origin.query.mark ∧
      (finalQuery origin path).current = last := ⟨rfl, rfl, rfl⟩

/-- The closed builder provides every route method. Only the initial point
    is an explicit prescan/control root when the corridor has zero moves. -/
def compileBuiltCorridor (methods : List Nat) (groups : List FieldGroup)
    (calls : List CallPort) (passes : List LocalPass) (origin : Origin)
    (supplied : (⟨origin.field, origin.query.mark.group⟩ : FieldGroup) ∈ groups)
    {start last : Port} (path : Corridor (buildSnapshot methods groups calls passes) start last)
    (initialMethod : start.method ∈ methods) :
    AdmittedCorridor (buildSnapshot methods groups calls passes) origin start last :=
  compileCorridor _ (builder_defaults_closed methods groups calls passes) origin supplied path
    (List.mem_eraseDups.mpr (List.mem_append.mpr (Or.inl
      (List.mem_append.mpr (Or.inl initialMethod)))))

/-- An actual ordinary store primitive; no field-identity result crosses
    this hit. The compatible bit includes AP/alias and operation-stage checks.
    The local compiler must retain these real write semantics. -/
structure Store where
  point : Port
  field : Nat
  rhs : Place
  compatible : Bool
  deriving DecidableEq, Repr

def reverseStore (query : Query) (store : Store) : Option Query :=
  if query.current = store.point ∧ query.place = .staticField store.field ∧ store.compatible = true then
    some { query with place := store.rhs }
  else none

theorem reverse_store_pivots (query : Query) (store : Store)
    (atPoint : query.current = store.point) (sameField : query.place = .staticField store.field)
    (compatible : store.compatible = true) :
    reverseStore query store = some { query with place := store.rhs } := by
  simp only [reverseStore, atPoint, sameField, compatible, and_self, reduceIte]

theorem interface_endpoint_visited {calls : List CallInput} {roots : List Mask}
    {group : Nat} {slot : Slot} (trace : InterfaceTrace calls roots group slot) :
    slot ∈ visited trace := by
  cases trace <;> simp [visited]

/-- Explicit writer guidance is generated from its captured ordinary FLOW
    writer row and forward input-mask trace, rather than assumed available.
    The guide permits f/T -> p/T publication; the carry identity cannot. -/
def writerGuide (groups : List Nat) (calls : List CallInput) (slots : List Slot)
    (supply : Supplies) (rows : List Row) (row : Row) (group : Nat)
    (eligible : group ∈ groups) (registered : row ∈ rows)
    (trace : InterfaceTrace calls (seedMasks slots supply) group row.original) :
    { guide : GroupGuide // guide ∈ freezeGuides rows (maskClosure groups calls slots supply) ∧
      guide.row = row ∧ guide.group = group } :=
  requiredGuide groups calls slots supply rows group eligible trace row registered
    (interface_endpoint_visited trace)

theorem writer_paired_output_allowed (row : Row) (mark : RuleGroupSchedule.FullMark)
    (field : Nat) (output : row.output = .staticField field) :
    pairedPublication ⟨row, mark.group⟩ (.staticField field) (originalPlace row.original) mark mark :=
  ⟨output, rfl, rfl, rfl⟩

theorem static_identity_rejects_ordinary_output (method field point group geometry : Nat)
    (payload : RuleGroupSchedule.QueryPayload) (mark : RuleGroupSchedule.FullMark) :
    ¬ pairedPublication ⟨carryRow method field point payload, group⟩
      (.staticField field) (.ordinary geometry) mark mark := by
  intro impossible
  cases impossible.2.1

/-- Captured primitive equalities identify the real writer row and stage.
    The row's original p and actual RHS t can differ. Reversing t to p needs
    the ordinary local/call reversal adapter, not the paired demand guide.
    These equalities do not assume a marked guide or a return summary. -/
def WriterBindingMatches (store : Store) (row : Row) : Prop :=
  row.kind = .writerPoint ∧ row.original.method = store.point.method ∧
  row.point = store.point.point ∧ row.output = .staticField store.field

instance writerBindingMatchesDecidable (store : Store) (row : Row) :
    Decidable (WriterBindingMatches store row) := by unfold WriterBindingMatches; infer_instance

structure WriterEndpointCoverage (groups : List Nat) (calls : List CallInput)
    (slots : List Slot) (supply : Supplies) (rows : List Row)
    (origin : Origin) (store : Store) (row : Row) where
  guide : GroupGuide
  frozen : guide ∈ freezeGuides rows (maskClosure groups calls slots supply)
  sameRow : guide.row = row
  matchesWriter : WriterBindingMatches store row
  admitsFull : emitFromGroupGuide guide origin.query.mark = some origin.query.mark
  pivots : reverseStore (atStatic origin store.point) store =
    some { (atStatic origin store.point) with place := store.rhs }
  publishes : pairedPublication guide (.staticField store.field) (originalPlace row.original)
    origin.query.mark origin.query.mark

/-- A real store and its captured FLOW writer row compute the extra marked
    paired demand. A field identity alone would filter the original p.
    The real store pivots to its current RHS t; the guide permits an eventual
    method result p. This function does not prove the ordinary t-to-p prefix.
    The row need not have reached a forward normal exit. -/
def writerEndpointCoverage (groups : List Nat) (calls : List CallInput)
    (slots : List Slot) (supply : Supplies) (rows : List Row)
    (origin : Origin) (store : Store) (row : Row)
    (eligible : origin.query.mark.group ∈ groups) (registered : row ∈ rows)
    (trace : InterfaceTrace calls (seedMasks slots supply) origin.query.mark.group row.original)
    (sameField : origin.field = store.field) (compatible : store.compatible = true)
    (matchesWriter : WriterBindingMatches store row) :
    WriterEndpointCoverage groups calls slots supply rows origin store row :=
  let captured := writerGuide groups calls slots supply rows row origin.query.mark.group
    eligible registered trace
  { guide := captured.val
    frozen := captured.property.1
    sameRow := captured.property.2.1
    matchesWriter := matchesWriter
    admitsFull := explicit_group_emits_full captured.val origin.query.mark captured.property.2.2
    pivots := reverse_store_pivots _ store rfl (by change Place.staticField origin.field = _; rw [sameField]) compatible
    publishes := by
      have same : captured.val = ⟨row, origin.query.mark.group⟩ :=
        calc
          captured.val = ⟨captured.val.row, captured.val.group⟩ := by cases captured.val; rfl
          _ = ⟨row, origin.query.mark.group⟩ :=
            by rw [captured.property.2.1, captured.property.2.2]
      rw [same]
      exact writer_paired_output_allowed row origin.query.mark store.field matchesWriter.2.2.2 }

/-- Compose actual read-to-store motion with the independently constructed
    ordinary writer demand. The store endpoint and read origin are indexed,
    so no jump or unrelated writer row can be substituted. -/
def coverReadToWriter (snapshot : Snapshot) (closed : DefaultsClosed snapshot)
    (origin : Origin) (store : Store)
    (supplied : (⟨origin.field, origin.query.mark.group⟩ : FieldGroup) ∈ snapshot.carry.staticGroups)
    (path : Corridor snapshot origin.read store.point)
    (readMethod : origin.read.method ∈ snapshot.carry.defaultMethods)
    (groups : List Nat) (calls : List CallInput) (slots : List Slot) (supply : Supplies)
    (rows : List Row) (row : Row)
    (eligible : origin.query.mark.group ∈ groups) (registered : row ∈ rows)
    (trace : InterfaceTrace calls (seedMasks slots supply) origin.query.mark.group row.original)
    (sameField : origin.field = store.field) (compatible : store.compatible = true)
    (matchesWriter : WriterBindingMatches store row) :
    AdmittedCorridor snapshot origin origin.read store.point ×
      WriterEndpointCoverage groups calls slots supply rows origin store row :=
  ⟨compileCorridor snapshot closed origin supplied path readMethod,
    writerEndpointCoverage groups calls slots supply rows origin store row eligible registered trace
      sameField compatible matchesWriter⟩

/-- An actual reversed source action yields unmarked control ZERO. A group
    guide permits searching for this action; it cannot construct this hit.
    Full source identity and its ordinary permission remain primitive input. -/
structure SourceAction where
  identity : Nat
  point : Port
  rhs : Place
  fullMark : RuleGroupSchedule.FullMark
  enabled : Bool
  deriving DecidableEq, Repr

structure ZeroSourceHit where
  origin : Origin
  source : SourceAction
  deriving DecidableEq, Repr

def reverseSource (query : Query) (source : SourceAction) : Option ZeroSourceHit :=
  if query.current = source.point ∧ query.place = source.rhs ∧
      query.mark = source.fullMark ∧ source.enabled = true then
    some ⟨query.origin, source⟩
  else none

theorem zero_hit_keeps_original (query : Query) (source : SourceAction) (hit : ZeroSourceHit)
    (actual : reverseSource query source = some hit) : hit.origin = query.origin := by
  unfold reverseSource at actual
  split at actual
  · cases Option.some.inj actual; rfl
  · contradiction

namespace Controls

def port (method point : Nat) : Port := ⟨method, point, [method], 5⟩
def consume : CallPort := ⟨1, port 1 10, port 1 11, port 2 20, port 2 29, .normal, true, true⟩
def getter : CallPort := ⟨2, port 2 21, port 2 22, port 3 30, port 3 39, .normal, true, true⟩
def setter : CallPort := ⟨3, port 1 8, port 1 10, port 4 40, port 4 49, .normal, true, true⟩
def getPass : LocalPass := ⟨port 3 30, port 3 32, true⟩
def consumePass : LocalPass := ⟨port 2 20, port 2 21, true⟩
def setPass : LocalPass := ⟨port 4 45, port 4 49, true⟩
def snapshot : Snapshot := ⟨⟨[1, 2, 3, 4, 5], [⟨5, 7⟩]⟩,
  [consume, getter, setter], [getPass, consumePass, setPass]⟩
def payload : RuleGroupSchedule.QueryPayload := ⟨90, 5, [], [1]⟩
def origin : Origin := ⟨port 3 32, 5, ⟨payload, ⟨7, 100⟩⟩⟩
def store : Store := ⟨port 4 45, 5, .ordinary 6, true⟩
def writer : Row := ⟨⟨4, .ordinary 6⟩, .staticField 5, 45, .writerPoint, payload⟩
def source : SourceAction := ⟨1000, port 4 45, .ordinary 6, ⟨7, 100⟩, true⟩

/-- Ordinary setter(p) corridor. Its actual store is the endpoint. The
    separately tested source primitive below is not an end-to-end source
    compiler theorem for a ZERO-origin setter body. -/
def built : Snapshot := buildSnapshot [1, 2, 3, 4, 5] [⟨5, 7⟩]
  [consume, getter, setter] [getPass, consumePass, setPass]

def fullPath : Corridor built origin.read store.point :=
  .next (.localPass getPass) (by decide)
    (.next (.leaveCalleeAtEntry getter) (by decide)
      (.next (.localPass consumePass) (by decide)
        (.next (.leaveCalleeAtEntry consume) (by decide)
          (.next (.enterCalleeAtExit setter) (by decide)
            (.next (.localPass setPass) (by decide) (.done store.point))))))

def fullAdmitted : AdmittedCorridor built origin origin.read store.point :=
  compileBuiltCorridor [1, 2, 3, 4, 5] [⟨5, 7⟩]
    [consume, getter, setter] [getPass, consumePass, setPass] origin (by decide) fullPath (by decide)

def wrapperGet : CallPort := ⟨4, port 5 52, port 5 53, port 3 30, port 3 39, .normal, true, true⟩
def consumeWrapper : CallPort := ⟨5, port 2 21, port 2 22, port 5 50, port 5 59, .normal, true, true⟩
def wrapperPass : LocalPass := ⟨port 5 50, port 5 52, true⟩
def wrapped : Snapshot := buildSnapshot [1, 2, 3, 4, 5] [⟨5, 7⟩]
  [consume, wrapperGet, consumeWrapper, setter] [getPass, wrapperPass, consumePass, setPass]

def wrappedPath : Corridor wrapped origin.read store.point :=
  .next (.localPass getPass) (by decide)
    (.next (.leaveCalleeAtEntry wrapperGet) (by decide)
      (.next (.localPass wrapperPass) (by decide)
        (.next (.leaveCalleeAtEntry consumeWrapper) (by decide)
          (.next (.localPass consumePass) (by decide)
            (.next (.leaveCalleeAtEntry consume) (by decide)
              (.next (.enterCalleeAtExit setter) (by decide)
                (.next (.localPass setPass) (by decide) (.done store.point))))))))

def wrappedAdmitted : AdmittedCorridor wrapped origin origin.read store.point :=
  compileBuiltCorridor [1, 2, 3, 4, 5] [⟨5, 7⟩]
    [consume, wrapperGet, consumeWrapper, setter] [getPass, wrapperPass, consumePass, setPass]
    origin (by decide) wrappedPath (by decide)

def writerSupply : Supplies := ⟨[⟨writer.original, 7⟩], [⟨5, 7⟩], [5]⟩
def writerTrace : InterfaceTrace [] (seedMasks [writer.original] writerSupply) 7 writer.original :=
  .root writer.original (actual_input_seed _ _ _ (by decide))

def endpoint : WriterEndpointCoverage [7] [] [writer.original] writerSupply [writer] origin store writer :=
  writerEndpointCoverage [7] [] [writer.original] writerSupply [writer] origin store writer
    (by decide) (by decide) writerTrace rfl rfl (by decide)

def controls : List (String × Bool) := [
  ("getter entry returns to consume BEFORE call", decide ((Move.leaveCalleeAtEntry getter).finish = port 2 21)),
  ("consume entry returns to main BEFORE consume", decide ((Move.leaveCalleeAtEntry consume).finish = port 1 10)),
  ("main enters actual setter at normal exit", decide ((Move.enterCalleeAtExit setter).finish = port 4 49)),
  ("local backward pass is registered", decide (permitted snapshot (.localPass setPass))),
  ("real call port is registered", decide (permitted snapshot (.enterCalleeAtExit setter))),
  ("wrong call context is not registered", decide (¬ permitted snapshot (.enterCalleeAtExit { setter with calleeExit := port 44 49 }))),
  ("incompatible alias region is rejected", decide (¬ permitted { snapshot with localPasses := [{ setPass with compatible := false }] }
    (.localPass { setPass with compatible := false }))),
  ("source group supplies explicit carry demand", decide (carryPermitted snapshot.carry 2 5 7)),
  ("wrong group has no carry demand", decide (¬ carryPermitted snapshot.carry 2 5 8)),
  ("unrecorded method has no carry demand", decide (¬ carryPermitted snapshot.carry 99 5 7)),
  ("actual store pivots to RHS", decide (reverseStore (atStatic origin store.point) store =
    some { (atStatic origin store.point) with place := .ordinary 6 })),
  ("store does not pass static field as identity", decide (reverseStore (atStatic origin store.point) store ≠ some (atStatic origin store.point))),
  ("wrong static field cannot use store pivot", decide (reverseStore { (atStatic origin store.point) with place := .staticField 6 } store = none)),
  ("writer guide admits ordinary RHS publication", decide (pairedPublication ⟨writer, 7⟩ (.staticField 5) (.ordinary 6) ⟨7, 100⟩ ⟨7, 100⟩)),
  ("carry guide rejects ordinary RHS publication", decide (¬ pairedPublication ⟨carryRow 4 5 45 payload, 7⟩
    (.staticField 5) (.ordinary 6) ⟨7, 100⟩ ⟨7, 100⟩)),
  ("actual source hit is unmarked ZERO", decide (reverseSource { (atStatic origin store.point) with place := .ordinary 6 } source =
    some ⟨origin, source⟩)),
  ("different full mark in same group is not this source", decide (reverseSource { (atStatic origin store.point) with place := .ordinary 6, mark := ⟨7, 101⟩ } source = none)),
  ("nonreturning check can retain an internal origin", decide (origin.read.point = 32)),
  ("nonreturning callee can leave at entry", decide (permitted { snapshot with calls := [{ getter with exitReached := false }] }
    (.leaveCalleeAtEntry { getter with exitReached := false }))),
  ("nonreturning callee cannot enter at normal exit", decide (¬ permitted { snapshot with calls := [{ getter with exitReached := false }] }
    (.enterCalleeAtExit { getter with exitReached := false }))),
  ("local context mismatch is rejected", decide (¬ permitted { snapshot with localPasses := [{ setPass with before := { setPass.before with context := [99] } }] }
    (.localPass { setPass with before := { setPass.before with context := [99] } }))),
  ("local AP-region mismatch is rejected", decide (¬ permitted { snapshot with localPasses := [{ setPass with before := { setPass.before with region := 99 } }] }
    (.localPass { setPass with before := { setPass.before with region := 99 } }))),
  ("registered inconsistent call context is rejected", decide (¬ permitted { snapshot with calls := [{ setter with callerAfter := { setter.callerAfter with context := [99] } }] }
    (.enterCalleeAtExit { setter with callerAfter := { setter.callerAfter with context := [99] } }))),
  ("registered inconsistent callee method is rejected", decide (¬ permitted { snapshot with calls := [{ setter with calleeExit := { setter.calleeExit with method := 99 } }] }
    (.enterCalleeAtExit { setter with calleeExit := { setter.calleeExit with method := 99 } }))),
  ("real getter-consume-main-setter route has six moves", decide (fullPath.moves.length = 6)),
  ("every original-route port is admitted", decide (fullAdmitted.moves.length = 6)),
  ("extra transparent helper route has eight moves", decide (wrappedPath.moves.length = 8)),
  ("extra helper ports are all admitted", decide (wrappedAdmitted.moves.length = 8)),
  ("corridor retains exact original read context", decide ((finalQuery origin wrappedPath).origin = origin)),
  ("endpoint guide is generated from writer mask", decide (endpoint.guide ∈
    freezeGuides [writer] (maskClosure [7] [] [writer.original] writerSupply))),
  ("endpoint guide permits original-slot publication", decide (pairedPublication endpoint.guide
    (.staticField 5) (.ordinary 6) origin.query.mark origin.query.mark)),
  ("store current RHS may differ from original p", decide (reverseStore (atStatic origin store.point)
    { store with rhs := .ordinary 9 } = some { (atStatic origin store.point) with place := .ordinary 9 })),
  ("p guide does not publish temporary t as method result", decide (¬ pairedPublication endpoint.guide
    (.staticField 5) (.ordinary 9) origin.query.mark origin.query.mark))
]
end Controls
end ApSpec.Run1StaticReadWritePath
