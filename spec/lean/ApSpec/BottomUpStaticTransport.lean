/-
  Transport part of run1-bottom-up-static-propagation.md.

  The two procedures below have different path grammars. Ordinary forwarding
  visits each relay; bottom-up transport retains a relay segment and visits
  each effect boundary. Compression and expansion are executable functions on
  derivation certificates, not an assumed coverage/equivalence predicate.

  P is the COMPLETE immutable AP/premise/context/support payload. No operation
  projects it. Effects may kill, branch, change base/mx/layer, or emit request,
  source/sink/call/exit observations. Only the syntactic relay constructor is
  identity and observation-free. Establishing that constructor from actual
  JVM forms, packed AP decomposition, balanced-call execution, and a concurrent
  scheduler remains a separate implementation bridge. Finite graph cycles are
  allowed; this module proves finite-path least closure, not termination for
  an infinite reachable payload domain.
-/
import Std

namespace ApSpec.BottomUpStaticTransport

inductive Boundary where
  | statement | callInput | callReturn | normalExit | exceptionalExit
  | request | source | sink
  deriving DecidableEq, Repr

structure Effect (N O : Type) where
  source : N
  target : N
  boundary : Boundary
  operation : O
  deriving DecidableEq, Repr

inductive Edge (N O : Type) where
  | relay (source target : N)
  | effect (value : Effect N O)
  deriving DecidableEq, Repr

abbrev Kernel (O P V : Type) := O → P → List (P × List V)

structure Record (N O P V : Type) where
  effect : Effect N O
  before : P
  after : P
  observations : List V
  deriving DecidableEq, Repr

structure State (N O P V : Type) where
  position : N
  payload : P
  history : List (Record N O P V)
  deriving DecidableEq, Repr

variable {N O P V : Type}

def fire (e : Effect N O) (p q : P) (vs : List V)
    (history : List (Record N O P V)) : State N O P V :=
  ⟨e.target, q, history ++ [⟨e,p,q,vs⟩]⟩

/-- Independent ordinary one-edge forwarding, including every effect boundary. -/
inductive SmallStep (graph : List (Edge N O)) (run : Kernel O P V) :
    State N O P V → State N O P V → Type where
  | relay {a b p history} (member : Edge.relay a b ∈ graph) :
      SmallStep graph run ⟨a,p,history⟩ ⟨b,p,history⟩
  | effect {e p q vs history} (member : Edge.effect e ∈ graph)
      (produced : (q,vs) ∈ run e.operation p) :
      SmallStep graph run ⟨e.source,p,history⟩ (fire e p q vs history)

inductive Forward (graph : List (Edge N O)) (run : Kernel O P V) :
    State N O P V → State N O P V → Type where
  | done (state) : Forward graph run state state
  | next {a b c} (step : SmallStep graph run a b) (rest : Forward graph run b c) :
      Forward graph run a c

/-- A concrete finite relay certificate; it may traverse cycles. -/
inductive Relays (graph : List (Edge N O)) : N → N → Type where
  | done (node) : Relays graph node node
  | next {a b c} (member : Edge.relay a b ∈ graph) (rest : Relays graph b c) :
      Relays graph a c

def Relays.append {graph : List (Edge N O)} {a b c : N} :
    Relays graph a b → Relays graph b c → Relays graph a c
  | .done _, right => right
  | .next edge tail, right => .next edge (tail.append right)

def Relays.forward {graph : List (Edge N O)} {run : Kernel O P V}
    {a b : N} (segment : Relays graph a b) {p history target} :
    Forward graph run ⟨b,p,history⟩ target → Forward graph run ⟨a,p,history⟩ target :=
  match segment with
  | .done _ => fun rest => rest
  | .next edge tail => fun rest => .next (.relay edge) (tail.forward rest)

/-- Contracted transport: one relay segment before each REAL effect, and a
    final segment. It cannot skip, invent, or reorder an effect. -/
inductive BottomUp (graph : List (Edge N O)) (run : Kernel O P V) :
    State N O P V → State N O P V → Type where
  | done {a b p history} (segment : Relays graph a b) :
      BottomUp graph run ⟨a,p,history⟩ ⟨b,p,history⟩
  | effect {a e p q vs history target} (segment : Relays graph a e.source)
      (member : Edge.effect e ∈ graph) (produced : (q,vs) ∈ run e.operation p)
      (rest : BottomUp graph run (fire e p q vs history) target) :
      BottomUp graph run ⟨a,p,history⟩ target

def BottomUp.prefix {graph : List (Edge N O)} {run : Kernel O P V}
    {a b : N} (segment : Relays graph a b) {p history target} :
    BottomUp graph run ⟨b,p,history⟩ target → BottomUp graph run ⟨a,p,history⟩ target
  | .done tail => .done (segment.append tail)
  | .effect pre edge produced tail => .effect (segment.append pre) edge produced tail

def compress {graph : List (Edge N O)} {run : Kernel O P V} {a b} :
    Forward graph run a b → BottomUp graph run a b
  | .done state => .done (.done state.position)
  | .next (.relay edge) rest => BottomUp.prefix (.next edge (.done _)) (compress rest)
  | .next (.effect edge produced) rest => .effect (.done _) edge produced (compress rest)

def expand {graph : List (Edge N O)} {run : Kernel O P V} {a b} :
    BottomUp graph run a b → Forward graph run a b
  | .done segment => segment.forward (.done _)
  | .effect segment edge produced rest =>
      segment.forward (.next (.effect edge produced) (expand rest))

theorem path_equivalence (graph : List (Edge N O)) (run : Kernel O P V) (a b) :
    Nonempty (Forward graph run a b) ↔ Nonempty (BottomUp graph run a b) :=
  ⟨fun ⟨path⟩ => ⟨compress path⟩, fun ⟨path⟩ => ⟨expand path⟩⟩

abbrev ForwardWitness (graph : List (Edge N O)) (run : Kernel O P V) (start) :=
  (target : State N O P V) × Forward graph run start target

def compressWitness {graph : List (Edge N O)} {run : Kernel O P V} {start}
    (w : ForwardWitness graph run start) :
    (target : State N O P V) × BottomUp graph run start target := ⟨w.1,compress w.2⟩

@[simp] theorem complete_result_preserved {graph : List (Edge N O)}
    {run : Kernel O P V} {start} (w : ForwardWitness graph run start) :
    (compressWitness w).1 = w.1 := rfl

@[simp] theorem full_payload_preserved {graph : List (Edge N O)}
    {run : Kernel O P V} {start} (w : ForwardWitness graph run start) :
    (compressWitness w).1.payload = w.1.payload := rfl

@[simp] theorem boundary_and_observation_records_preserved {graph : List (Edge N O)}
    {run : Kernel O P V} {start} (w : ForwardWitness graph run start) :
    (compressWitness w).1.history = w.1.history := rfl

def Forward.append {graph : List (Edge N O)} {run : Kernel O P V} {a b c} :
    Forward graph run a b → Forward graph run b c → Forward graph run a c
  | .done _, right => right
  | .next step tail, right => .next step (tail.append right)

def Reach (graph : List (Edge N O)) (run : Kernel O P V)
    (seeds : List (State N O P V)) (target : State N O P V) : Prop :=
  ∃ seed ∈ seeds, Nonempty (Forward graph run seed target)

def BottomReach (graph : List (Edge N O)) (run : Kernel O P V)
    (seeds : List (State N O P V)) (target : State N O P V) : Prop :=
  ∃ seed ∈ seeds, Nonempty (BottomUp graph run seed target)

theorem recursive_finite_path_equality (graph : List (Edge N O)) (run : Kernel O P V)
    (seeds : List (State N O P V)) (target) :
    Reach graph run seeds target ↔ BottomReach graph run seeds target := by
  constructor
  · rintro ⟨seed,member,⟨path⟩⟩
    exact ⟨seed,member,⟨compress path⟩⟩
  · rintro ⟨seed,member,⟨path⟩⟩
    exact ⟨seed,member,⟨expand path⟩⟩

theorem reach_seed {graph : List (Edge N O)} {run : Kernel O P V}
    {seeds : List (State N O P V)} {seed} (member : seed ∈ seeds) :
    Reach graph run seeds seed := ⟨seed,member,⟨.done seed⟩⟩

theorem reach_step {graph : List (Edge N O)} {run : Kernel O P V}
    {seeds : List (State N O P V)} {a b} (reached : Reach graph run seeds a)
    (step : SmallStep graph run a b) : Reach graph run seeds b := by
  obtain ⟨seed,member,⟨path⟩⟩ := reached
  exact ⟨seed,member,⟨path.append (.next step (.done _))⟩⟩

theorem Forward.closed {graph : List (Edge N O)} {run : Kernel O P V}
    {relation : State N O P V → Prop}
    (closed : ∀ {a b}, relation a → SmallStep graph run a b → relation b)
    {a b} (path : Forward graph run a b) (initial : relation a) : relation b := by
  induction path with
  | done => exact initial
  | next step rest ih => exact ih (closed initial step)

theorem least_closure {graph : List (Edge N O)} {run : Kernel O P V}
    {seeds : List (State N O P V)} {relation : State N O P V → Prop}
    (initial : ∀ s ∈ seeds, relation s)
    (closed : ∀ {a b}, relation a → SmallStep graph run a b → relation b)
    {target} (reached : BottomReach graph run seeds target) : relation target := by
  obtain ⟨seed,member,⟨path⟩⟩ := reached
  exact (expand path).closed closed (initial seed member)

/- Finite n-ary derivations can represent mutually produced inputs, summaries,
   and observations by tagging ports inside N/P. Every rule retains its entire
   ordered premise list. In particular this is not an independent-member ND
   union or a same-mark approximation. The actual engine's clause compiler is
   a separate conformance obligation. -/
structure Clause (F : Type) where
  premises : List F
  conclusion : F
  deriving DecidableEq, Repr

inductive Derivation {F : Type} (transport : F → F → Type)
    (seeds : List F) (clauses : List (Clause F)) : F → Type where
  | seed {value} (member : value ∈ seeds) : Derivation transport seeds clauses value
  | transport {a b} (prior : Derivation transport seeds clauses a) (path : transport a b) :
      Derivation transport seeds clauses b
  | rule {clause : Clause F} (member : clause ∈ clauses)
      (all : (value : F) → value ∈ clause.premises → Derivation transport seeds clauses value) :
      Derivation transport seeds clauses clause.conclusion

def Derivation.map {F : Type} {left right : F → F → Type}
    {seeds : List F} {clauses : List (Clause F)}
    (convert : ∀ {a b}, left a b → right a b) {value}
    (proof : Derivation left seeds clauses value) : Derivation right seeds clauses value :=
  match proof with
  | .seed member => .seed member
  | .transport prior path => .transport (prior.map convert) (convert path)
  | .rule member all => .rule member (fun x hx => (all x hx).map convert)

theorem mutual_finite_derivation_equivalence (graph : List (Edge N O)) (run : Kernel O P V)
    (seeds : List (State N O P V)) (clauses : List (Clause (State N O P V))) (value) :
    Nonempty (Derivation (Forward graph run) seeds clauses value) ↔
      Nonempty (Derivation (BottomUp graph run) seeds clauses value) :=
  ⟨fun ⟨proof⟩ => ⟨proof.map (fun path => compress path)⟩,
    fun ⟨proof⟩ => ⟨proof.map (fun path => expand path)⟩⟩

theorem Derivation.least {F : Type} {transport : F → F → Type}
    {seeds : List F} {clauses : List (Clause F)} {relation : F → Prop}
    (initial : ∀ x ∈ seeds, relation x)
    (step : ∀ {a b}, relation a → transport a b → relation b)
    (ruleClosed : ∀ clause ∈ clauses,
      (∀ x ∈ clause.premises, relation x) → relation clause.conclusion)
    {value} (proof : Derivation transport seeds clauses value) : relation value := by
  induction proof with
  | seed member => exact initial _ member
  | transport prior path ih => exact step ih path
  | rule member all ih => exact ruleClosed _ member ih

def Forward.extend {small large : List (Edge N O)} {first next : Kernel O P V}
    (edges : ∀ e ∈ small, e ∈ large)
    (outputs : ∀ op p result, result ∈ first op p → result ∈ next op p) {a b} :
    Forward small first a b → Forward large next a b
  | .done state => .done state
  | .next (.relay member) rest => .next (.relay (edges _ member)) (rest.extend edges outputs)
  | .next (.effect member produced) rest =>
      .next (.effect (edges _ member) (outputs _ _ _ produced)) (rest.extend edges outputs)

def Derivation.extend {F : Type} {left right : F → F → Type}
    {seeds seeds' : List F} {clauses clauses' : List (Clause F)}
    (transport : ∀ {a b}, left a b → right a b)
    (seedInclusion : ∀ x ∈ seeds, x ∈ seeds')
    (clauseInclusion : ∀ c ∈ clauses, c ∈ clauses') {value}
    (proof : Derivation left seeds clauses value) : Derivation right seeds' clauses' value :=
  match proof with
  | .seed member => .seed (seedInclusion _ member)
  | .transport prior path =>
      .transport (prior.extend transport seedInclusion clauseInclusion) (transport path)
  | .rule member all => .rule (clauseInclusion _ member)
      (fun x hx => (all x hx).extend transport seedInclusion clauseInclusion)

/- Snapshot-dependent mutual rounds. Unlike fixed-graph path equivalence,
   graph/kernel/seeds/n-ary clauses here are recomputed from EVERY current
   snapshot. Local round equality is DERIVED by compress/expand, then lifted
   by induction. There is no assumed global coverage/equality predicate.

   All finite proof trees are admitted, including recursively discovered call
   and summary clauses. A production bridge must show its positive, complete
   snapshot constructor produces these SAME clauses on both sides. Arbitrary
   negative guards or unproved JVM relay classification are not covered. -/
abbrev Snapshot (F : Type) := F → Prop

structure RoundProgram (N O P V : Type) where
  graph : Snapshot (State N O P V) → List (Edge N O)
  kernel : Snapshot (State N O P V) → Kernel O P V
  seeds : Snapshot (State N O P V) → List (State N O P V)
  clauses : Snapshot (State N O P V) → List (Clause (State N O P V))

def ordinaryRound (program : RoundProgram N O P V) (snapshot : Snapshot (State N O P V)) :
    Snapshot (State N O P V) := fun value => snapshot value ∨
      Nonempty (Derivation (Forward (program.graph snapshot) (program.kernel snapshot))
        (program.seeds snapshot) (program.clauses snapshot) value)

def bottomUpRound (program : RoundProgram N O P V) (snapshot : Snapshot (State N O P V)) :
    Snapshot (State N O P V) := fun value => snapshot value ∨
      Nonempty (Derivation (BottomUp (program.graph snapshot) (program.kernel snapshot))
        (program.seeds snapshot) (program.clauses snapshot) value)

theorem snapshot_round_equivalence (program : RoundProgram N O P V)
    (snapshot : Snapshot (State N O P V)) (value) :
    ordinaryRound program snapshot value ↔ bottomUpRound program snapshot value := by
  exact or_congr Iff.rfl
    (mutual_finite_derivation_equivalence _ _ _ _ value)

theorem snapshot_round_equal (program : RoundProgram N O P V) (snapshot) :
    ordinaryRound program snapshot = bottomUpRound program snapshot := by
  funext value
  exact propext (snapshot_round_equivalence program snapshot value)

def iterate {F : Type} (operation : Snapshot F → Snapshot F) (initial : Snapshot F) :
    Nat → Snapshot F
  | 0 => initial
  | n + 1 => operation (iterate operation initial n)

theorem snapshot_iterates_equal (program : RoundProgram N O P V) (initial) (n : Nat) :
    iterate (ordinaryRound program) initial n = iterate (bottomUpRound program) initial n := by
  induction n with
  | zero => rfl
  | succ n ih =>
    change ordinaryRound program _ = bottomUpRound program _
    rw [ih, snapshot_round_equal]

theorem finite_round_closure_equal (program : RoundProgram N O P V) (initial) (value) :
    (∃ n, iterate (ordinaryRound program) initial n value) ↔
      ∃ n, iterate (bottomUpRound program) initial n value := by
  constructor <;> rintro ⟨n,member⟩
  · exact ⟨n, (snapshot_iterates_equal program initial n) ▸ member⟩
  · exact ⟨n, (snapshot_iterates_equal program initial n).symm ▸ member⟩

def Included {F : Type} (left right : Snapshot F) : Prop := ∀ x, left x → right x
def MonotoneRound {F : Type} (operation : Snapshot F → Snapshot F) : Prop :=
  ∀ left right, Included left right → Included (operation left) (operation right)

/-- Local positive constructors suffice for monotonicity: no global closure
    equality is a hypothesis. Real producer eligibility must justify these
    pointwise extensions, or provide a separate exact positive formulation. -/
structure PositiveProgram (program : RoundProgram N O P V) : Prop where
  edges : ∀ left right, Included left right →
    ∀ e ∈ program.graph left, e ∈ program.graph right
  outputs : ∀ left right, Included left right →
    ∀ op p result, result ∈ program.kernel left op p → result ∈ program.kernel right op p
  seeds : ∀ left right, Included left right →
    ∀ x ∈ program.seeds left, x ∈ program.seeds right
  clauses : ∀ left right, Included left right →
    ∀ c ∈ program.clauses left, c ∈ program.clauses right

theorem positive_program_monotone (program : RoundProgram N O P V)
    (positive : PositiveProgram program) : MonotoneRound (ordinaryRound program) := by
  intro left right inclusion value member
  rcases member with old | derived
  · exact Or.inl (inclusion value old)
  · obtain ⟨proof⟩ := derived
    exact Or.inr ⟨proof.extend
      (fun path => path.extend (positive.edges left right inclusion)
        (positive.outputs left right inclusion))
      (positive.seeds left right inclusion) (positive.clauses left right inclusion)⟩

theorem snapshot_monotonicity_transfers (program : RoundProgram N O P V)
    (monotone : MonotoneRound (ordinaryRound program)) : MonotoneRound (bottomUpRound program) := by
  intro left right inclusion
  rw [← snapshot_round_equal, ← snapshot_round_equal]
  exact monotone left right inclusion

theorem finite_round_least {F : Type} (operation : Snapshot F → Snapshot F)
    (monotone : MonotoneRound operation) (initial candidate : Snapshot F)
    (seeded : Included initial candidate) (closed : Included (operation candidate) candidate)
    (n : Nat) : Included (iterate operation initial n) candidate := by
  induction n with
  | zero => exact seeded
  | succ n ih => exact fun value member => closed value (monotone _ _ ih value member)

theorem bottom_up_round_least (program : RoundProgram N O P V)
    (monotone : MonotoneRound (ordinaryRound program)) (initial candidate)
    (seeded : Included initial candidate)
    (closed : Included (ordinaryRound program candidate) candidate) (n : Nat) :
    Included (iterate (bottomUpRound program) initial n) candidate := by
  rw [← snapshot_iterates_equal]
  exact finite_round_least _ monotone initial candidate seeded closed n

theorem positive_bottom_up_least (program : RoundProgram N O P V)
    (positive : PositiveProgram program) (initial candidate)
    (seeded : Included initial candidate)
    (closed : Included (ordinaryRound program candidate) candidate) (n : Nat) :
    Included (iterate (bottomUpRound program) initial n) candidate :=
  bottom_up_round_least program (positive_program_monotone program positive)
    initial candidate seeded closed n

theorem observable_projection_equality (graph : List (Edge N O)) (run : Kernel O P V)
    (seeds : List (State N O P V)) {R : Type} (observe : State N O P V → List R) (row : R) :
    (∃ state, Reach graph run seeds state ∧ row ∈ observe state) ↔
      ∃ state, BottomReach graph run seeds state ∧ row ∈ observe state := by
  constructor <;> rintro ⟨state,reached,member⟩
  · exact ⟨state,(recursive_finite_path_equality _ _ _ _).mp reached,member⟩
  · exact ⟨state,(recursive_finite_path_equality _ _ _ _).mpr reached,member⟩

/- A finite graph alone does not make State finite: history is a ghost trace,
   and even finite P permits unbounded traces around effect cycles. The least
   relations above range over FINITE witnesses, not a finite-state scheduler
   termination proof. Termination of a deduplicating implementation additionally
   needs a finite normalized payload/observation quotient and fair worklists.
   No stored path history, pair history, or per-origin relay cache is proposed. -/

/- Two-sided delta join of actual input bindings with available effects.
   `observed` is a proof observer, not a proposed retained output/pair cache.
   K is retained provenance, not a substitute for an eligibility check: the
   actual adapter's N must contain the full method/context/call/stage port.
   Matching anonymous CFG numbers alone does not prove same-call eligibility. -/
structure Binding (N P K : Type) where
  owner : K
  position : N
  payload : P
  deriving DecidableEq, Repr

structure Delivery (N O P V K : Type) where
  binding : Binding N P K
  effect : Effect N O
  value : P
  observations : List V
  deriving DecidableEq, Repr

section Incremental
variable {K : Type} [DecidableEq N]

def deliver (run : Kernel O P V) (binding : Binding N P K) (effect : Effect N O) :
    List (Delivery N O P V K) :=
  if binding.position = effect.source then
    (run effect.operation binding.payload).map fun (value,vs) => ⟨binding,effect,value,vs⟩
  else []

def join (run : Kernel O P V) (bindings : List (Binding N P K))
    (effects : List (Effect N O)) : List (Delivery N O P V K) :=
  bindings.flatMap fun binding => effects.flatMap (deliver run binding)

theorem mem_join_left (run : Kernel O P V) (a b : List (Binding N P K))
    (effects : List (Effect N O)) (value) :
    value ∈ join run (a ++ b) effects ↔
      value ∈ join run a effects ∨ value ∈ join run b effects := by
  simp [join,List.flatMap_append]

theorem mem_join_right (run : Kernel O P V) (bindings : List (Binding N P K))
    (a b : List (Effect N O)) (value) :
    value ∈ join run bindings (a ++ b) ↔
      value ∈ join run bindings a ∨ value ∈ join run bindings b := by
  simp only [join,List.mem_flatMap,List.mem_append]
  constructor
  · rintro ⟨binding,hb,effect,(he | he),hv⟩
    · exact Or.inl ⟨binding,hb,effect,he,hv⟩
    · exact Or.inr ⟨binding,hb,effect,he,hv⟩
  · rintro (⟨binding,hb,effect,he,hv⟩ | ⟨binding,hb,effect,he,hv⟩)
    · exact ⟨binding,hb,effect,Or.inl he,hv⟩
    · exact ⟨binding,hb,effect,Or.inr he,hv⟩

structure JoinState (N O P V K : Type) where
  bindings : List (Binding N P K)
  effects : List (Effect N O)
  observed : List (Delivery N O P V K)

def JoinState.empty : JoinState N O P V K := ⟨[],[],[]⟩

def inputArrives (run : Kernel O P V) (s : JoinState N O P V K)
    (newInputs : List (Binding N P K)) : JoinState N O P V K :=
  ⟨s.bindings ++ newInputs,s.effects,s.observed ++ join run newInputs s.effects⟩

def effectArrives (run : Kernel O P V) (s : JoinState N O P V K)
    (newEffects : List (Effect N O)) : JoinState N O P V K :=
  ⟨s.bindings,s.effects ++ newEffects,s.observed ++ join run s.bindings newEffects⟩

def JoinComplete (run : Kernel O P V) (s : JoinState N O P V K) : Prop :=
  ∀ value, value ∈ s.observed ↔ value ∈ join run s.bindings s.effects

theorem empty_join_complete (run : Kernel O P V) :
    JoinComplete run (JoinState.empty : JoinState N O P V K) := by
  simp [JoinComplete,JoinState.empty,join]

theorem input_arrival_complete (run : Kernel O P V) (s : JoinState N O P V K)
    (complete : JoinComplete run s) (incoming) : JoinComplete run (inputArrives run s incoming) := by
  intro value
  change value ∈ s.observed ++ join run incoming s.effects ↔
    value ∈ join run (s.bindings ++ incoming) s.effects
  rw [List.mem_append,complete value,mem_join_left]

theorem effect_arrival_complete (run : Kernel O P V) (s : JoinState N O P V K)
    (complete : JoinComplete run s) (incoming) : JoinComplete run (effectArrives run s incoming) := by
  intro value
  change value ∈ s.observed ++ join run s.bindings incoming ↔
    value ∈ join run s.bindings (s.effects ++ incoming)
  rw [List.mem_append,complete value,mem_join_right]

inductive Arrival (N O P K : Type) where
  | inputs (values : List (Binding N P K))
  | effects (values : List (Effect N O))

def advance (run : Kernel O P V) (s : JoinState N O P V K) : Arrival N O P K → JoinState N O P V K
  | .inputs values => inputArrives run s values
  | .effects values => effectArrives run s values

def drive (run : Kernel O P V) (s : JoinState N O P V K) : List (Arrival N O P K) → JoinState N O P V K
  | [] => s
  | event :: rest => drive run (advance run s event) rest

theorem incremental_closure (run : Kernel O P V) (events : List (Arrival N O P K))
    (s : JoinState N O P V K) (complete : JoinComplete run s) : JoinComplete run (drive run s events) := by
  induction events generalizing s with
  | nil => exact complete
  | cons event rest ih =>
    apply ih
    cases event with
    | inputs values => exact input_arrival_complete run s complete values
    | effects values => exact effect_arrival_complete run s complete values

theorem both_arrival_orders (run : Kernel O P V) (s : JoinState N O P V K)
    (complete : JoinComplete run s) (inputs) (effects) (value) :
    value ∈ (effectArrives run (inputArrives run s inputs) effects).observed ↔
      value ∈ (inputArrives run (effectArrives run s effects) inputs).observed := by
  rw [effect_arrival_complete run _ (input_arrival_complete run s complete inputs) effects value,
    input_arrival_complete run _ (effect_arrival_complete run s complete effects) inputs value]
  rfl

theorem late_input_emits (run : Kernel O P V) (s : JoinState N O P V K) (incoming) (value)
    (found : value ∈ join run incoming s.effects) :
    value ∈ (inputArrives run s incoming).observed := List.mem_append.mpr (Or.inr found)

theorem late_effect_emits (run : Kernel O P V) (s : JoinState N O P V K) (incoming) (value)
    (found : value ∈ join run s.bindings incoming) :
    value ∈ (effectArrives run s incoming).observed := List.mem_append.mpr (Or.inr found)

theorem delivery_retains_actual_binding (run : Kernel O P V) (binding : Binding N P K)
    (effect) (value) (found : value ∈ deliver run binding effect) : value.binding = binding := by
  unfold deliver at found
  split at found
  · obtain ⟨⟨out,vs⟩,_,rfl⟩ := List.mem_map.mp found
    rfl
  · simp at found

theorem delivered_has_ordinary_effect (graph : List (Edge N O)) (run : Kernel O P V)
    (binding : Binding N P K) (effect : Effect N O) (member : Edge.effect effect ∈ graph)
    (value : Delivery N O P V K) (found : value ∈ deliver run binding effect)
    (history : List (Record N O P V)) :
    Nonempty (SmallStep graph run ⟨binding.position,binding.payload,history⟩
      (fire effect binding.payload value.value value.observations history)) := by
  unfold deliver at found
  split at found
  · rename_i position
    obtain ⟨⟨out,vs⟩,produced,rfl⟩ := List.mem_map.mp found
    exact ⟨by simpa only [position] using
      (SmallStep.effect (history := history) member produced)⟩
  · simp at found

theorem emitted_has_registered_binding (run : Kernel O P V) (s : JoinState N O P V K)
    (complete : JoinComplete run s) (value) (found : value ∈ s.observed) :
    ∃ binding ∈ s.bindings, value.binding = binding := by
  have joined := (complete value).mp found
  obtain ⟨binding,registered,rest⟩ := List.mem_flatMap.mp joined
  obtain ⟨effect,_,delivered⟩ := List.mem_flatMap.mp rest
  exact ⟨binding,registered,delivery_retains_actual_binding run binding effect value delivered⟩

end Incremental

/- Local counted-work proof. Enqueue children BEFORE releasing the active
   owner. This does not prove a Kotlin queue implementation or fairness. -/
structure Accounting where
  queued : Nat
  active : Nat
  reserved : Nat
  deriving DecidableEq, Repr

def Accounted (s : Accounting) : Prop := s.reserved = s.queued + s.active
def enqueue (s : Accounting) (n : Nat) : Accounting :=
  ⟨s.queued + n,s.active,s.reserved + n⟩
def takeWork (s : Accounting) : Accounting :=
  ⟨s.queued - 1,s.active + 1,s.reserved⟩
def finishWork (s : Accounting) : Accounting :=
  ⟨s.queued,s.active - 1,s.reserved - 1⟩

theorem enqueue_accounted (s : Accounting) (n) (h : Accounted s) : Accounted (enqueue s n) := by
  dsimp [Accounted,enqueue] at *
  omega

theorem take_accounted (s : Accounting) (h : Accounted s) (pending : 0 < s.queued) :
    Accounted (takeWork s) := by
  dsimp [Accounted,takeWork] at *
  omega

theorem finish_accounted (s : Accounting) (h : Accounted s) (owner : 0 < s.active) :
    Accounted (finishWork s) := by
  dsimp [Accounted,finishWork] at *
  omega

theorem no_early_completion (s : Accounting) (h : Accounted s) :
    s.reserved = 0 ↔ s.queued = 0 ∧ s.active = 0 := by
  unfold Accounted at h
  rw [h]
  cases s.queued <;> cases s.active <;> simp

theorem enqueue_before_release (s : Accounting) (children : Nat) (h : Accounted s)
    (owner : 0 < s.active) :
    Accounted (finishWork (enqueue s children)) ∧
      (0 < children → 0 < (finishWork (enqueue s children)).reserved) := by
  constructor
  · exact finish_accounted _ (enqueue_accounted s children h) owner
  · intro nonempty
    unfold Accounted at h
    simp only [finishWork,enqueue]
    omega

/- A cost MODEL, not byte/time prediction: m distinct full origin bindings on
   an n-relay chain. Ordinary stores m at each node; contracted transport keeps
   two endpoint bindings per origin and one shared n-cell segment. -/
def ordinaryCells (relays bindings : Nat) := (relays + 1) * bindings
def contractedCells (relays bindings : Nat) := relays + 2 * bindings
def ordinaryTraversals (relays bindings : Nat) := relays * bindings
def contractedTraversals (relays bindings : Nat) := relays + 2 * bindings

theorem chain_space_strict (extraRelays extraBindings : Nat) :
    contractedCells (extraRelays + 3) (extraBindings + 2) <
      ordinaryCells (extraRelays + 3) (extraBindings + 2) := by
  simp only [contractedCells,ordinaryCells,Nat.add_mul,Nat.mul_add]
  omega

theorem chain_traversal_strict (extraRelays extraBindings : Nat) :
    contractedTraversals (extraRelays + 5) (extraBindings + 2) <
      ordinaryTraversals (extraRelays + 5) (extraBindings + 2) := by
  simp only [contractedTraversals,ordinaryTraversals,Nat.add_mul,Nat.mul_add]
  omega

def Relays.length {graph : List (Edge N O)} {a b} : Relays graph a b → Nat
  | .done _ => 0
  | .next _ rest => rest.length + 1

def Forward.length {graph : List (Edge N O)} {run : Kernel O P V} {a b} :
    Forward graph run a b → Nat
  | .done _ => 0
  | .next _ rest => rest.length + 1

def BottomUp.effectCount {graph : List (Edge N O)} {run : Kernel O P V} {a b} :
    BottomUp graph run a b → Nat
  | .done _ => 0
  | .effect _ _ _ rest => rest.effectCount + 1

def Relays.weaken {small large : List (Edge N O)}
    (embedding : ∀ edge ∈ small, edge ∈ large) {a b} : Relays small a b → Relays large a b
  | .done node => .done node
  | .next edge rest => .next (embedding _ edge) (rest.weaken embedding)

def chain : Nat → List (Edge Nat Nat)
  | 0 => []
  | n + 1 => .relay (n + 1) n :: chain n

def chainWitness : (n : Nat) → Relays (chain n) n 0
  | 0 => .done 0
  | n + 1 => .next (b := n) (by simp [chain])
      ((chainWitness n).weaken (by intro edge member; exact List.mem_cons_of_mem _ member))

theorem Relays.weaken_length {small large : List (Edge N O)}
    (embedding : ∀ edge ∈ small, edge ∈ large) {a b} (path : Relays small a b) :
    (path.weaken embedding).length = path.length := by
  induction path with
  | done => rfl
  | next edge rest ih => simp only [weaken,length,ih]

theorem chain_witness_length (n : Nat) : (chainWitness n).length = n := by
  induction n with
  | zero => rfl
  | succ n ih =>
    exact congrArg (· + 1)
      ((Relays.weaken_length (large := .relay (n + 1) n :: chain n)
        (fun _ member => List.mem_cons_of_mem _ member) (chainWitness n)).trans ih)

theorem Relays.forward_length {graph : List (Edge N O)} {run : Kernel O P V}
    {a b} (path : Relays graph a b) (p : P) (history : List (Record N O P V)) :
    (path.forward (run := run) (.done ⟨b,p,history⟩)).length = path.length := by
  induction path with
  | done => rfl
  | next edge rest ih => simp only [forward,Forward.length,length,ih]

theorem chain_ordinary_steps (n : Nat) (run : Kernel Nat P V) (p : P)
    (history : List (Record Nat Nat P V)) :
    ((chainWitness n).forward (run := run) (.done ⟨0,p,history⟩)).length = n := by
  rw [Relays.forward_length,chain_witness_length]

def chainContracted (n : Nat) (run : Kernel Nat P V) (p : P)
    (history : List (Record Nat Nat P V)) :
    BottomUp (chain n) run ⟨n,p,history⟩ ⟨0,p,history⟩ := .done (chainWitness n)

theorem chain_has_no_skipped_effect (n : Nat) (run : Kernel Nat P V) (p : P)
    (history : List (Record Nat Nat P V)) :
    (chainContracted n run p history).effectCount = 0 := rfl

/- Executable storage layouts for the chain cost model. A derivation's Relays
   value is a GHOST certificate, not the implementation's retained route. The
   proposed layout below contains ONE shared route list, plus literal complete
   origin bindings at its endpoints; ordinaryLayout repeats each binding at
   every node. Labels may be arbitrary immutable full-context values. -/
def ordinaryLayout {K : Type} : Nat → List K → List (Nat × K)
  | 0, bindings => bindings.map fun binding => (0,binding)
  | n + 1, bindings => (bindings.map fun binding => (n + 1,binding)) ++ ordinaryLayout n bindings

structure ContractedLayout (K : Type) where
  sharedRoute : List (Edge Nat Nat)
  entryBindings : List K
  exitBindings : List K
  deriving DecidableEq, Repr

def contractedLayout {K : Type} (relays : Nat) (bindings : List K) : ContractedLayout K :=
  ⟨chain relays,bindings,bindings⟩

def ContractedLayout.slots {K : Type} (layout : ContractedLayout K) : Nat :=
  layout.sharedRoute.length + layout.entryBindings.length + layout.exitBindings.length

theorem chain_length (n : Nat) : (chain n).length = n := by
  induction n with
  | zero => rfl
  | succ n ih => simp only [chain,List.length_cons,ih]

theorem ordinary_layout_length {K : Type} (n : Nat) (bindings : List K) :
    (ordinaryLayout n bindings).length = ordinaryCells n bindings.length := by
  induction n with
  | zero => simp [ordinaryLayout,ordinaryCells]
  | succ n ih =>
    simp only [ordinaryLayout,List.length_append,List.length_map,ih,ordinaryCells,Nat.add_mul]
    omega

theorem contracted_layout_slots {K : Type} (n : Nat) (bindings : List K) :
    (contractedLayout n bindings).slots = contractedCells n bindings.length := by
  simp only [contractedLayout,ContractedLayout.slots,chain_length,contractedCells]
  omega

theorem concrete_chain_layout_reduction (extraRelays extraBindings : Nat) :
    (contractedLayout (extraRelays + 3) (List.range (extraBindings + 2))).slots <
      (ordinaryLayout (extraRelays + 3) (List.range (extraBindings + 2))).length := by
  rw [contracted_layout_slots,ordinary_layout_length,List.length_range]
  exact chain_space_strict extraRelays extraBindings

/- Executable full-payload, boundary, kill, late-arrival and chain controls. -/
namespace Certificate

structure Payload where
  original : Nat
  wholePremise : List Nat
  methodContext : List Nat
  callStack : List Nat
  base : Nat
  path : List Nat
  tail : Nat
  fieldE : List Nat
  marks : List Nat
  mx : List Nat
  demand : Bool
  deriving DecidableEq, Repr

def input : Payload := ⟨7,[3,5],[11,13],[],1,[17,19],2,[23],[29,31],[37],true⟩
def called : Payload := {input with callStack := [41]}
def requested : Payload := {called with mx := [43,37]}
def returned : Payload := {requested with callStack := []}

def kernel : Kernel Nat Payload Nat
  | 0,p => [(p,[])]
  | 1,p => [({p with callStack := 41 :: p.callStack},[101])]
  | 2,p => [({p with mx := 43 :: p.mx},[103])]
  | 3,p => match p.callStack with
    | 41 :: rest => [({p with callStack := rest},[107])]
    | _ => []
  | _,_ => []

def call : Effect Nat Nat := ⟨1,2,.callInput,1⟩
def request : Effect Nat Nat := ⟨3,4,.request,2⟩
def exit : Effect Nat Nat := ⟨4,5,.normalExit,3⟩
def graph : List (Edge Nat Nat) := [.relay 0 1,.effect call,.relay 2 3,.effect request,.effect exit]
def start : State Nat Nat Payload Nat := ⟨0,input,[]⟩
def finish : State Nat Nat Payload Nat :=
  ⟨5,returned,[⟨call,input,called,[101]⟩,⟨request,called,requested,[103]⟩,
    ⟨exit,requested,returned,[107]⟩]⟩

def ordinary : Forward graph kernel start finish :=
  .next (.relay (by decide))
    (.next (.effect (e := call) (q := called) (vs := [101]) (by decide) (by decide))
      (.next (.relay (by decide))
        (.next (.effect (e := request) (q := requested) (vs := [103]) (by decide) (by decide))
          (.next (.effect (e := exit) (q := returned) (vs := [107]) (by decide) (by decide))
            (.done finish)))))

def contracted : BottomUp graph kernel start finish := compress ordinary
def expanded : Forward graph kernel start finish := expand contracted

def cycleEffect : Effect Nat Nat := ⟨0,2,.request,2⟩
def cycleGraph : List (Edge Nat Nat) := [.relay 0 1,.relay 1 0,.effect cycleEffect]
def cycleOutput : Payload := {input with mx := [43,37]}
def cycleFinish : State Nat Nat Payload Nat :=
  ⟨2,cycleOutput,[⟨cycleEffect,input,cycleOutput,[103]⟩]⟩
def cyclicOrdinary : Forward cycleGraph kernel start cycleFinish :=
  .next (.relay (b := 1) (by decide)) (.next (.relay (b := 0) (by decide))
    (.next (.effect (e := cycleEffect) (q := cycleOutput) (vs := [103])
      (by decide) (by decide)) (.done cycleFinish)))
def cyclicContracted : BottomUp cycleGraph kernel start cycleFinish := compress cyclicOrdinary

def naryClause : Clause (State Nat Nat Payload Nat) := ⟨[finish,start],cycleFinish⟩
def naryOrdinary : Derivation (Forward graph kernel) [start] [naryClause] cycleFinish := by
  apply Derivation.rule (clause := naryClause) (by decide)
  intro value member
  have alternatives : value = finish ∨ value = start := by simpa [naryClause] using member
  exact if equal : value = finish then
      equal.symm ▸ Derivation.transport (.seed (by decide)) ordinary
    else (alternatives.resolve_left equal).symm ▸ Derivation.seed (by decide)

def naryContracted : Derivation (BottomUp graph kernel) [start] [naryClause] cycleFinish :=
  naryOrdinary.map (fun path => compress path)
def naryExpanded : Derivation (Forward graph kernel) [start] [naryClause] cycleFinish :=
  naryContracted.map (fun path => expand path)

/-- Forces the executable mapper's seed/rule/transport constructors; the root
    rule still has BOTH literal premises. This is not a scope/ND compiler. -/
def derivationKind {F : Type} {transport : F → F → Type} {seeds clauses value} :
    Derivation transport seeds clauses value → Nat
  | .seed _ => 0
  | .transport _ _ => 1
  | .rule _ _ => 2

def binding : Binding Nat Payload (List Nat) := ⟨[109,113],3,called⟩
def empty : JoinState Nat Nat Payload Nat (List Nat) := .empty
def inputFirst := effectArrives kernel (inputArrives kernel empty [binding]) [request]
def effectFirst := inputArrives kernel (effectArrives kernel empty [request]) [binding]

def checks : List Bool := [
  decide (finish.payload = returned),
  decide (finish.history.map (fun row => row.effect.boundary) = [.callInput,.request,.normalExit]),
  decide (finish.history.flatMap (fun row => row.observations) = [101,103,107]),
  decide (finish.payload.wholePremise = input.wholePremise),
  decide (finish.payload.methodContext = input.methodContext),
  decide (finish.payload.demand = true),
  decide (kernel 99 input = []),
  decide (kernel 3 input = []),
  decide (inputFirst.observed = effectFirst.observed),
  decide ((effectArrives kernel empty [request]).observed = []),
  decide ((deliver kernel {binding with position := 0} request) = []),
  decide (ordinary.length = 5),
  decide (contracted.effectCount = 3),
  decide (cyclicOrdinary.length = 3),
  decide (cyclicContracted.effectCount = 1),
  decide ((chainWitness 10).length = 10),
  decide ((ordinaryLayout 10 (List.range 5)).length = 55),
  decide ((contractedLayout 10 (List.range 5)).slots = 20),
  decide (derivationKind naryContracted = 2),
  decide (derivationKind naryExpanded = 2),
  decide (naryClause.premises.length = 2),
  decide ((effectArrives kernel inputFirst [request]).observed.length = 2),
  decide (ordinaryCells 10 100 = 1100),
  decide (contractedCells 10 100 = 210),
  decide (ordinaryTraversals 10 100 = 1000)]

set_option maxRecDepth 16384 in
theorem controls_pass : checks.all id = true := by decide

#eval checks

end Certificate

#print axioms path_equivalence
#print axioms recursive_finite_path_equality
#print axioms mutual_finite_derivation_equivalence
#print axioms snapshot_round_equal
#print axioms snapshot_iterates_equal
#print axioms finite_round_closure_equal
#print axioms positive_program_monotone
#print axioms positive_bottom_up_least
#print axioms observable_projection_equality
#print axioms both_arrival_orders
#print axioms incremental_closure
#print axioms enqueue_before_release
#print axioms concrete_chain_layout_reduction
#print axioms Certificate.controls_pass

end ApSpec.BottomUpStaticTransport
