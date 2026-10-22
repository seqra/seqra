/-
Finite run-2 FULL witness replay after run-1 static/static summary erasure.

The old and new executions share ONE ordinary kernel, seed inventory, complete
full atoms and call-port identities. Only static query admission differs. The
old side uses actual input plus intersections with old guides; the new side
uses complete actual input with same-field EXACT completion. The imported AP
query theorem constructs each equal query, rather than assuming final closure
equality or requiring the erased guides in the new execution.

The ordinary kernel is a finite list of unchanged allowed FULL transitions.
It can include AP transfer, call/summary application, complete same-port ND
joins and source-hit observations. Its adapter must encode actual matching,
layers, field E, FLOW mx, raw alias policy and source/action identities. This
module proves a replay theorem PARAMETRIC in that identical kernel; it does
not verify Kotlin compilation, the new producer grammar, resource-limited
execution, arbitrary runtime callbacks, or an infinite fixed point.

Actual arrivals are derived from an existing full atom at its exact method,
statement, stage and invocation. They are not assumed available globally.
The bounded Actual datatype is the selected producer-grammar boundary. No
additional field validation, runtime support label or solver is introduced.
-/
import ApSpec.Run1StaticQueryCompletion

namespace ApSpec.Run1StaticErasureBackwardReplay
open ApSpec
set_option autoImplicit false

structure Port where
  method : Nat
  statement : Nat
  stage : Nat
  invocation : Nat
  deriving DecidableEq, Repr

structure Original where
  owner : Nat
  initial : PFact
  deriving DecidableEq, Repr

/-- All ordinary payload coordinates are retained. Empty originals can encode
    ZERO; no set of original slots or caller-wide group mask replaces them. -/
structure FullAtom where
  port : Port
  originals : List Original
  current : PFact
  layer : Nat
  /-- Opaque ordinary headers/record provenance not present in scalar PFact. -/
  annotations : List Nat
  deriving DecidableEq, Repr

structure SinkSeed where
  site : Port
  fullRule : Nat
  occurrence : Nat
  atom : FullAtom
  deriving DecidableEq, Repr

structure SourceHit where
  site : Port
  fullRule : Nat
  action : Nat
  occurrence : Nat
  deriving DecidableEq, Repr

inductive Event where
  | sink (seed : SinkSeed)
  | full (atom : FullAtom)
  | arrival (site : Port) (actual : Run1StaticQueryCompletion.Actual)
  | query (site : Port) (pattern : PFact)
  | sourceHit (hit : SourceHit)
  deriving DecidableEq, Repr

inductive RuleKind where
  | transfer | callSummary | ndJoin | sourceObservation
  deriving DecidableEq, Repr

structure FullRule where
  identity : Nat
  kind : RuleKind
  port : Port
  premises : List Event
  conclusion : Event
  deriving DecidableEq, Repr

def atPort (port : Port) : Event → Bool
  | .full atom => decide (atom.port = port)
  | _ => false

/-- A complete ordinary ND binding belongs to one invocation/stage. This is
    an adapter condition of the shared kernel, not a new run-1 validator. -/
def FullRule.valid (rule : FullRule) : Bool :=
  match rule.kind with
  | .ndJoin => decide (2 ≤ rule.premises.length) && rule.premises.all (atPort rule.port)
  | _ => true

structure Kernel where
  owner : Run1StaticQueryCompletion.Owner
  oldGuides : Port → List Run1StaticQueryCompletion.Guide
  retainedSeeds : List SinkSeed
  rules : List FullRule

inductive Admission where
  | reference | completeActual | actualOnly
  deriving DecidableEq, Repr

/-- actualOnly exists only as a negative-control policy. -/
def queries (mode : Admission) (kernel : Kernel) (site : Port) (actual : Run1StaticQueryCompletion.Actual) : List PFact :=
  match mode with
  | .reference => Run1StaticQueryCompletion.referenceQueries kernel.owner (kernel.oldGuides site) actual
  | .completeActual => Run1StaticQueryCompletion.complete actual
  | .actualOnly => [actual.pattern]

inductive Witness (mode : Admission) (kernel : Kernel) : Event → Type where
  | seed (seed : SinkSeed) (retained : seed ∈ kernel.retainedSeeds) :
      Witness mode kernel (.sink seed)
  | seedInput (seed : SinkSeed) (prior : Witness mode kernel (.sink seed)) :
      Witness mode kernel (.full seed.atom)
  | actualArrival (atom : FullAtom) (actual : Run1StaticQueryCompletion.Actual)
      (prior : Witness mode kernel (.full atom)) (same : atom.current = actual.pattern) :
      Witness mode kernel (.arrival atom.port actual)
  | admit (site : Port) (actual : Run1StaticQueryCompletion.Actual)
      (arrived : Witness mode kernel (.arrival site actual))
      (query : PFact) (present : query ∈ queries mode kernel site actual) :
      Witness mode kernel (.query site query)
  | ordinary (rule : FullRule) (present : rule ∈ kernel.rules)
      (valid : rule.valid = true)
      (inputs : (index : Fin rule.premises.length) →
        Witness mode kernel rule.premises[index]) :
      Witness mode kernel rule.conclusion

/-- Construct the new finite witness. All ordinary transfers, all complete ND
    arms, source observations and identities are reused without modification. -/
def replay {kernel : Kernel} {event : Event}
    (old : Witness .reference kernel event) : Witness .completeActual kernel event :=
  match old with
  | .seed seed retained => .seed seed retained
  | .seedInput seed prior => .seedInput seed (replay prior)
  | .actualArrival atom actual prior same => .actualArrival atom actual (replay prior) same
  | .admit site actual arrived query present =>
      .admit site actual (replay arrived) query
        (Run1StaticQueryCompletion.old_query_batch_covered kernel.owner (kernel.oldGuides site) actual query present)
  | .ordinary rule present valid inputs =>
      .ordinary rule present valid (fun index => replay (inputs index))

theorem finite_full_witness_replayed {kernel : Kernel} {atom : FullAtom}
    (old : Witness .reference kernel (.full atom)) :
    Nonempty (Witness .completeActual kernel (.full atom)) := ⟨replay old⟩

theorem source_hit_identity_preserved {kernel : Kernel} {hit : SourceHit}
    (old : Witness .reference kernel (.sourceHit hit)) :
    Nonempty (Witness .completeActual kernel (.sourceHit hit)) := ⟨replay old⟩

theorem retained_sink_seed_preserved {kernel : Kernel} {seed : SinkSeed}
    (old : Witness .reference kernel (.sink seed)) :
    Nonempty (Witness .completeActual kernel (.sink seed)) := ⟨replay old⟩

theorem actual_arrival_keeps_exact_port {kernel : Kernel} {site : Port} {actual : Run1StaticQueryCompletion.Actual}
    (old : Witness .reference kernel (.arrival site actual)) :
    Nonempty (Witness .completeActual kernel (.arrival site actual)) := ⟨replay old⟩

theorem no_required_old_guide_in_new_queries (kernel : Kernel) (site : Port) (actual : Run1StaticQueryCompletion.Actual) :
    queries .completeActual kernel site actual = Run1StaticQueryCompletion.complete actual := rfl

theorem complete_binding_preserved {kernel : Kernel} (rule : FullRule)
    (present : rule ∈ kernel.rules) (valid : rule.valid = true)
    (oldInputs : (index : Fin rule.premises.length) →
      Witness .reference kernel rule.premises[index]) :
    Nonempty (Witness .completeActual kernel rule.conclusion) :=
  ⟨.ordinary rule present valid (fun index => replay (oldInputs index))⟩

/-- A computable witness size. No termination claim about a real worklist is
    inferred from the finiteness of this particular derivation tree. -/
def witnessSize {mode : Admission} {kernel : Kernel} {event : Event} :
    Witness mode kernel event → Nat
  | .seed .. => 1
  | .seedInput _ prior => witnessSize prior + 1
  | .actualArrival _ _ prior _ => witnessSize prior + 1
  | .admit _ _ prior _ _ => witnessSize prior + 1
  | .ordinary rule _ _ inputs =>
      ((List.finRange rule.premises.length).map (fun index => witnessSize (inputs index))).sum + 1

/-- The new witness never needs to invent another ordinary transition. -/
theorem replay_size_equal {kernel : Kernel} {event : Event}
    (old : Witness .reference kernel event) : witnessSize (replay old) = witnessSize old := by
  induction old with
  | seed => rfl
  | seedInput seed prior ih => simp only [replay,witnessSize,ih]
  | actualArrival atom actual prior same ih => simp only [replay,witnessSize,ih]
  | admit site actual prior query present ih => simp only [replay,witnessSize,ih]
  | ordinary rule present valid inputs ih =>
      simp only [replay,witnessSize]
      congr 1
      apply congrArg List.sum
      apply List.map_congr_left
      intro index _
      exact ih index

/-! Executable bounded controls. `step` enumerates actual arrivals and rules;
    it cannot derive a query from FLOW or ZERO by guessing a FULL atom. -/
def insert (event : Event) (state : List Event) : List Event :=
  if event ∈ state then state else state ++ [event]

def ruleReady (rule : FullRule) (state : List Event) : Bool :=
  rule.valid && rule.premises.all (fun event => decide (event ∈ state))

def step (mode : Admission) (kernel : Kernel) (actuals : List Run1StaticQueryCompletion.Actual)
    (state : List Event) : List Event := Id.run do
  let mut out := state
  for seed in kernel.retainedSeeds do
    out := insert (.sink seed) out
    out := insert (.full seed.atom) out
  for event in state do
    match event with
    | .full atom =>
        for actual in actuals do
          if atom.current = actual.pattern then out := insert (.arrival atom.port actual) out
    | .arrival site actual =>
        for query in queries mode kernel site actual do out := insert (.query site query) out
    | _ => pure ()
  for rule in kernel.rules do
    if ruleReady rule state then out := insert rule.conclusion out
  return out

def execute (fuel : Nat) (mode : Admission) (kernel : Kernel) (actuals : List Run1StaticQueryCompletion.Actual) : List Event :=
  match fuel with
  | 0 => []
  | n + 1 => step mode kernel actuals (execute n mode kernel actuals)

namespace Controls
def port : Port := ⟨8,20,3,44⟩
def otherPort : Port := {port with invocation := 45}
def actual : Run1StaticQueryCompletion.Actual := .fieldAny 2 3 101
def exact : PFact := (Run1StaticQueryCompletion.Actual.fieldExact 2 3 101).pattern
def original : Original := ⟨8,actual.pattern⟩
def input : FullAtom := ⟨port,[original],actual.pattern,1,[17,29]⟩
def exactInput : FullAtom := {input with current := exact, originals := [⟨8,exact⟩]}
def seed : SinkSeed := ⟨port,700,91,input⟩
def hit : SourceHit := ⟨port,700,800,92⟩
def openExact : FullRule := ⟨10,.transfer,port,[.query port exact],.full exactInput⟩
def observe : FullRule := ⟨11,.sourceObservation,port,[.full exactInput],.sourceHit hit⟩
def kernel : Kernel :=
  ⟨(fun _ => 7),(fun _ => [.fieldExact 2 3 7]),[seed],[openExact,observe]⟩

def oldSeed : Witness .reference kernel (.sink seed) := .seed seed (by simp [kernel])
def oldInput : Witness .reference kernel (.full input) := .seedInput seed oldSeed
def oldArrival : Witness .reference kernel (.arrival port actual) :=
  .actualArrival input actual oldInput rfl
def oldQuery : Witness .reference kernel (.query port exact) :=
  .admit port actual oldArrival exact (by decide)
def oldExact : Witness .reference kernel (.full exactInput) :=
  .ordinary openExact (by simp [kernel]) rfl (fun index =>
    match index with | ⟨0,_⟩ => oldQuery)
def oldHit : Witness .reference kernel (.sourceHit hit) :=
  .ordinary observe (by simp [kernel]) rfl (fun index =>
    match index with | ⟨0,_⟩ => oldExact)
def newHit : Witness .completeActual kernel (.sourceHit hit) := replay oldHit

def second : FullAtom := {exactInput with annotations := [31], current :=
  (Run1StaticQueryCompletion.Actual.fieldExact 2 4 102).pattern}
def nd : FullRule := ⟨12,.ndJoin,port,[.full exactInput,.full second],.sourceHit hit⟩
def wrongCaller : FullAtom := {second with port := otherPort}
def mixedND : FullRule := {nd with premises := [.full exactInput,.full wrongCaller]}
def zero : PFact := ⟨0,[],.exact,.conc 0⟩
def zeroAtom : FullAtom := ⟨port,[],zero,0,[]⟩
def zeroSeed : SinkSeed := ⟨port,701,93,zeroAtom⟩
def zeroKernel : Kernel := {kernel with retainedSeeds := [zeroSeed],rules := []}

/-- A connected call/summary and complete two-arm binding. The second seed is
    a separate retained actual input; the first arm cannot stand in for it. -/
def returned : FullAtom := {exactInput with current :=
  (Run1StaticQueryCompletion.Actual.fieldAny 2 5 101).pattern, annotations := [41,42]}
def secondSeed : SinkSeed := ⟨port,702,94,second⟩
def callSummary : FullRule :=
  ⟨20,.callSummary,port,[.full exactInput,.query port exact],.full returned⟩
def completeND : FullRule :=
  ⟨21,.ndJoin,port,[.full returned,.full second],.sourceHit hit⟩
def ndKernel : Kernel :=
  {kernel with retainedSeeds := [seed,secondSeed],rules := [openExact,callSummary,completeND]}
def missingArmKernel : Kernel := {ndKernel with retainedSeeds := [seed]}

def ndSeed : Witness .reference ndKernel (.full input) :=
  .seedInput seed (.seed seed (by simp [ndKernel]))
def ndArrival : Witness .reference ndKernel (.arrival port actual) :=
  .actualArrival input actual ndSeed rfl
def ndQuery : Witness .reference ndKernel (.query port exact) :=
  .admit port actual ndArrival exact (by decide)
def ndStart : Witness .reference ndKernel (.full exactInput) :=
  .ordinary openExact (by simp [ndKernel]) rfl (fun index =>
    match index with | ⟨0,_⟩ => ndQuery)
def ndReturned : Witness .reference ndKernel (.full returned) :=
  .ordinary callSummary (by simp [ndKernel]) rfl (fun index =>
    match index with | ⟨0,_⟩ => ndStart | ⟨1,_⟩ => ndQuery)
def ndSecond : Witness .reference ndKernel (.full second) :=
  .seedInput secondSeed (.seed secondSeed (by simp [ndKernel]))
def oldNDHit : Witness .reference ndKernel (.sourceHit hit) :=
  .ordinary completeND (by simp [ndKernel]) (by decide) (fun index =>
    match index with | ⟨0,_⟩ => ndReturned | ⟨1,_⟩ => ndSecond)
def newNDHit : Witness .completeActual ndKernel (.sourceHit hit) := replay oldNDHit

def controls : List Bool := [
  decide (.sourceHit hit ∈ execute 6 .reference kernel [actual]),
  decide (.sourceHit hit ∈ execute 6 .completeActual kernel [actual]),
  decide (.sourceHit hit ∉ execute 6 .actualOnly kernel [actual]),
  decide (.query port exact ∈ execute 4 .completeActual kernel [actual]),
  decide (.query otherPort exact ∉ execute 6 .completeActual kernel [actual]),
  decide (Run1StaticQueryCompletion.complete (.classExact 2 101) = [(Run1StaticQueryCompletion.Actual.classExact 2 101).pattern]),
  decide (exact ∉ Run1StaticQueryCompletion.complete (.classExact 2 101)),
  decide (nd.valid = true),
  decide (ruleReady nd [.full exactInput,.full second] = true),
  decide (ruleReady nd [.full exactInput] = false),
  decide (ruleReady nd [.full second] = false),
  decide (mixedND.valid = false),
  decide (.sink zeroSeed ∈ execute 1 .completeActual zeroKernel []),
  decide (.full zeroAtom ∈ execute 1 .completeActual zeroKernel []),
  decide (Run1StaticQueryCompletion.complete (.fieldAny 2 3 102) ≠ Run1StaticQueryCompletion.complete actual),
  decide (witnessSize newHit = witnessSize oldHit),
  decide (.sourceHit hit ∈ execute 8 .reference ndKernel [actual]),
  decide (.sourceHit hit ∈ execute 8 .completeActual ndKernel [actual]),
  decide (.sourceHit hit ∉ execute 8 .completeActual missingArmKernel [actual]),
  decide (.sourceHit hit ∉ execute 8 .actualOnly ndKernel [actual]),
  decide (witnessSize newNDHit = witnessSize oldNDHit),
  decide (returned.originals = exactInput.originals)
]

theorem controls_pass : controls.all id = true := by decide

end Controls
end ApSpec.Run1StaticErasureBackwardReplay
