/- Executable scalar AP instantiation of the transport model.
   Fact outputs and request/observation outputs are distinct channels: a kill
   cannot erase an already emitted request. Unknown operations stay opaque
   ordinary boundaries. This does not instantiate a future JVM/packed compiler. -/
import ApSpec.BottomUpStaticApplication
import ApSpec.BottomUpStaticProtocol
import ApSpec.BottomUpStaticTransport
namespace ApSpec.BottomUpStaticModel
open ApSpec

inductive Event where
  | request (question : Run1Requests.Question)
  | observation (owner : Run1Requests.Owner) (value : BottomUpStaticApplication.Observation)
  deriving DecidableEq, Repr
inductive Payload where
  | fact (state : BottomUpStaticApplication.State)
  | notice (owner : Run1Requests.Owner)
  deriving DecidableEq, Repr
structure Operation where
  owner : Run1Requests.Owner
  instruction : BottomUpStaticApplication.Primitive
  deriving DecidableEq, Repr

/-- The event branch exists even if there are NO surviving fact outputs. -/
def events (owner : Run1Requests.Owner) (result : BottomUpStaticApplication.Result) : List Event :=
  result.requests.map Event.request ++ result.observations.map (Event.observation owner)
def outcomes (owner : Run1Requests.Owner) (result : BottomUpStaticApplication.Result) : List (Payload × List Event) :=
  result.states.map (fun state => (.fact state,[])) ++
    if (events owner result).isEmpty then [] else [(.notice owner,events owner result)]

def facts : List (Payload × List Event) → List BottomUpStaticApplication.State
  | [] => []
  | (.fact state,_) :: rest => state :: facts rest
  | (.notice _,_) :: rest => facts rest

def notices (values : List (Payload × List Event)) : List Event :=
  values.flatMap fun value => value.2

theorem facts_append (left right : List (Payload × List Event)) :
    facts (left ++ right) = facts left ++ facts right := by
  induction left with
  | nil => rfl
  | cons head rest ih => cases head with | mk p vs => cases p <;> simp [facts,ih]
theorem facts_map (states : List BottomUpStaticApplication.State) :
    facts (states.map (fun state => (Payload.fact state,([] : List Event)))) = states := by
  induction states with
  | nil => rfl
  | cons state states ih => simp [facts,ih]
theorem facts_outcomes (owner : Run1Requests.Owner) (result : BottomUpStaticApplication.Result) :
    facts (outcomes owner result) = result.states := by
  unfold outcomes
  rw [facts_append,facts_map]
  split <;> simp [facts]
theorem notices_outcomes (owner : Run1Requests.Owner) (result : BottomUpStaticApplication.Result) :
    notices (outcomes owner result) = events owner result := by
  unfold notices outcomes
  rw [List.flatMap_append]
  have silent : (result.states.map (fun state => (Payload.fact state,([] : List Event)))).flatMap
      (fun value => value.2) = [] := by simp
  rw [silent]
  split
  · rename_i h
    have empty : events owner result = [] := List.isEmpty_iff.mp h
    simp [empty]
  · simp

/-- Imported ordinary behavior is identical on both sides and never certified
    as transparent merely because it is unknown to the selected scalar model. -/
def kernel (fallback : Operation → BottomUpStaticApplication.State → BottomUpStaticApplication.Result) : BottomUpStaticTransport.Kernel Operation Payload Event
  | operation,.fact state =>
    let result := match BottomUpStaticApplication.eval operation.owner operation.instruction state with
      | some result => result
      | none => fallback operation state
    outcomes operation.owner result
  | _,.notice _ => []

theorem known_fact_outputs (fallback : Operation → BottomUpStaticApplication.State → BottomUpStaticApplication.Result)
    (operation : Operation) (input : BottomUpStaticApplication.State) (result : BottomUpStaticApplication.Result)
    (known : BottomUpStaticApplication.eval operation.owner operation.instruction input = some result) :
    facts (kernel fallback operation (.fact input)) = result.states := by
  simp only [kernel,known]
  exact facts_outcomes _ _
theorem known_events (fallback : Operation → BottomUpStaticApplication.State → BottomUpStaticApplication.Result)
    (operation : Operation) (input : BottomUpStaticApplication.State) (result : BottomUpStaticApplication.Result)
    (known : BottomUpStaticApplication.eval operation.owner operation.instruction input = some result) :
    notices (kernel fallback operation (.fact input)) = events operation.owner result := by
  simp only [kernel,known]
  exact notices_outcomes _ _

/-- The syntactic identity edge has a local, executable justification. The
    complete ordinary evaluator, rather than a field-name test, certifies it. -/
theorem checked_kernel_identity
    (fallback : Operation → BottomUpStaticApplication.State → BottomUpStaticApplication.Result)
    (operation : Operation) (input : BottomUpStaticApplication.State)
    (checked : BottomUpStaticApplication.checkPrimitive
      operation.owner operation.instruction input = true) :
    kernel fallback operation (.fact input) = [(.fact input,[])] := by
  have evaluated := BottomUpStaticApplication.checked_primitive_exact
    operation.owner operation.instruction input checked
  simp [kernel,evaluated,outcomes,events,BottomUpStaticApplication.Result.identity]

/-- Install the selected static FLOW policy before an operation can inspect
    layers, and again before its outputs are admitted. Concrete origins stay
    on the ordinary path. Geometry, original support and marks are unchanged. -/
def normalizeState (state : BottomUpStaticApplication.State) : BottomUpStaticApplication.State :=
  match BottomUpStaticDomain.abstractOrigin state.original with
  | none => state
  | some slot =>
      {state with current := (BottomUpStaticDomain.demote ⟨slot,state.current⟩).current}

theorem normalization_keeps_payload (state : BottomUpStaticApplication.State) :
    (normalizeState state).current.fact = state.current.fact := by
  unfold normalizeState
  split <;> rfl

theorem normalization_keeps_origin (state : BottomUpStaticApplication.State) :
    (normalizeState state).original = state.original := by
  unfold normalizeState
  split <;> rfl

theorem normalization_static_D (state : BottomUpStaticApplication.State)
    (slot : Run1Requests.Slot)
    (abstract : BottomUpStaticDomain.abstractOrigin state.original = some slot)
    (static : state.current.fact.base = 1) :
    (normalizeState state).current.demand = true := by
  simp only [normalizeState,abstract]
  exact BottomUpStaticDomain.static_current_demand ⟨slot,state.current⟩ static

theorem normalization_sticky (state : BottomUpStaticApplication.State)
    (demand : state.current.demand = true) :
    (normalizeState state).current.demand = true := by
  unfold normalizeState
  split
  · exact demand
  · exact BottomUpStaticDomain.demotion_sticky _ demand

def normalizePayload : Payload → Payload
  | .fact state => .fact (normalizeState state)
  | .notice owner => .notice owner

def selectedKernel
    (fallback : Operation → BottomUpStaticApplication.State → BottomUpStaticApplication.Result)
    (operation : Operation) (input : Payload) : List (Payload × List Event) :=
  (kernel fallback operation (normalizePayload input)).map
    fun (output,notices) => (normalizePayload output,notices)

theorem checked_selected_kernel_identity
    (fallback : Operation → BottomUpStaticApplication.State → BottomUpStaticApplication.Result)
    (operation : Operation) (input : BottomUpStaticApplication.State)
    (normalized : normalizeState input = input)
    (checked : BottomUpStaticApplication.checkPrimitive
      operation.owner operation.instruction input = true) :
    selectedKernel fallback operation (.fact input) = [(.fact input,[])] := by
  simp only [selectedKernel,normalizePayload,normalized,
    checked_kernel_identity fallback operation input checked,List.map_cons,List.map_nil]

theorem scalar_path_equivalence {N : Type} (graph : List (BottomUpStaticTransport.Edge N Operation))
    (fallback : Operation → BottomUpStaticApplication.State → BottomUpStaticApplication.Result) (start finish) :
    Nonempty (BottomUpStaticTransport.Forward graph (selectedKernel fallback) start finish) ↔
      Nonempty (BottomUpStaticTransport.BottomUp graph (selectedKernel fallback) start finish) :=
  BottomUpStaticTransport.path_equivalence _ _ _ _

theorem scalar_nary_equivalence {N : Type} (graph : List (BottomUpStaticTransport.Edge N Operation))
    (fallback : Operation → BottomUpStaticApplication.State → BottomUpStaticApplication.Result)
    (seeds : List (BottomUpStaticTransport.State N Operation Payload Event))
    (clauses : List (BottomUpStaticTransport.Clause (BottomUpStaticTransport.State N Operation Payload Event))) (value) :
    Nonempty (BottomUpStaticTransport.Derivation (BottomUpStaticTransport.Forward graph (selectedKernel fallback)) seeds clauses value) ↔
      Nonempty (BottomUpStaticTransport.Derivation (BottomUpStaticTransport.BottomUp graph (selectedKernel fallback)) seeds clauses value) :=
  BottomUpStaticTransport.mutual_finite_derivation_equivalence _ _ _ _ _

/-- Exact same interpreter, including event-only results, after call-input coding. -/
theorem call_input_client_equal {Result : Type}
    (client : BottomUpStaticProtocol.Input → Result) (input : BottomUpStaticProtocol.Input) :
    client (BottomUpStaticProtocol.decode (BottomUpStaticProtocol.encode input)) = client input :=
  BottomUpStaticProtocol.interpreter_preserved _ _

namespace Controls
 def owner : Run1Requests.Owner := ⟨1,2,[3]⟩
 def field : PFact := (Run1Requests.Slot.field 2 3).initial
 def input : BottomUpStaticApplication.State := ⟨.abstractField 2 3,⟨field,true⟩⟩
 def question : Run1Requests.Question := ⟨owner,.field 2 3,11⟩
 def target : Run1StrongPreservation.FieldTarget := ⟨1,[4],7⟩
 def killedWithRequest : Operation := ⟨owner,.definiteWrite target ⟨[],[question],[]⟩⟩
 def ordinaryFallback (_ : Operation) (state : BottomUpStaticApplication.State) : BottomUpStaticApplication.Result := .identity state
 def source : PFact := (Run1Requests.Slot.base 0).initial
 def localInput : BottomUpStaticApplication.State := ⟨.abstractBase 0,⟨source,false⟩⟩
 def staticInput : BottomUpStaticApplication.State := ⟨.abstractBase 0,⟨field,false⟩⟩
 def staticDemand : BottomUpStaticApplication.State := ⟨.abstractBase 0,⟨field,true⟩⟩
 def checks : Bool :=
   (facts (kernel ordinaryFallback killedWithRequest (.fact input))).isEmpty &&
   (notices (kernel ordinaryFallback killedWithRequest (.fact input)) == [.request question]) &&
   (kernel ordinaryFallback ⟨owner,.rootCalleeDefault⟩ (.fact input) == [(.fact input,[])]) &&
   (kernel ordinaryFallback ⟨owner,.pass⟩ (.notice owner)).isEmpty &&
   (BottomUpStaticApplication.checkPrimitive owner .unknownFallback input == false) &&
   (facts (kernel ordinaryFallback ⟨owner,.unknownFallback⟩ (.fact input)) == [input]) &&
   (normalizeState staticInput == staticDemand) &&
   (facts (selectedKernel ordinaryFallback ⟨owner,.pass⟩ (.fact staticInput)) == [staticDemand]) &&
   (facts (selectedKernel ordinaryFallback ⟨owner,.apEffect source field false⟩ (.fact localInput)) == [staticDemand]) &&
   (notices (selectedKernel ordinaryFallback killedWithRequest (.fact input)) == [.request question])
 theorem controls_pass : checks = true := by decide
 #guard checks
 #eval checks
end Controls
#print axioms scalar_nary_equivalence
#print axioms notices_outcomes
#print axioms Controls.controls_pass
end ApSpec.BottomUpStaticModel
