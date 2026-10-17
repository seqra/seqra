/-
  Constructive scalar model of bottom-up concrete-static propagation.

  CONCEPT: an actual static concrete supplier admits its own mark without a
  static request. This is the actual-input transport fragment. Interest from
  static-origin FLOW is routed separately by BottomUpConcreteStaticNeeds to
  actual write/nonstatic origins; it is never fabricated actual T or an
  unconditional concrete hypothesis in this module.
  Static origin/current/history is DEMAND, with the entire AP
  payload unchanged. Local consumers execute ordinary AP operations. Only
  abstract nonstatic original slots can emit the retained BaseSlot requests.

  OPTIMIZATION: exact and chain field carriers have a lossless typed codec;
  transformed, class-point and whole-ND inputs remain bound. Ordinary forwarding
  and contracted forwarding execute the SAME concrete effect kernel. Generic
  constructive expansion/compression, finite recursive derivations, two-sided
  delivery and a literal chain-layout cost model are instantiated below.

  This is not the Kotlin/JVM compiler, packed-trie implementation, alias/domain
  completeness proof, balanced-call compiler, scheduler fairness/termination or
  an ND confirmation theorem. Relay edges must be compiled from genuinely
  identity, observation-free operations; an opaque operation is never silently
  replaced with a relay. Histories below are proof-only observations. The
  explicit check/write/clean primitives model selected scalar components; the
  frontend must enumerate actual spatial components and preserve all guards.
-/
import ApSpec.BottomUpStaticModel

namespace ApSpec.BottomUpConcreteStatic
open ApSpec
set_option autoImplicit false

abbrev Initial := BottomUpStaticDomain.Initial
abbrev Leaf := Run1Requests.Leaf
abbrev Owner := Run1Requests.Owner
abbrev Question := Run1Requests.Question
abbrev Port := BottomUpStaticProtocol.Port
abbrev Layered := Run1DefaultSummaryApplication.Layered

/-! ## Actual suppliers, without a static request argument. -/

def supplyField (c f : Nat) (actual : Leaf) : Option PFact :=
  BottomUpStaticDomain.projectConcrete (.field c f) [actual.mark] actual

theorem supply_field_is_actual_projection (c f : Nat) (actual : Leaf) :
    supplyField c f actual = Run1Requests.project (.field c f) actual := by
  simp [supplyField,BottomUpStaticDomain.projectConcrete]

theorem supply_field_legal_and_actual (c f : Nat) (actual : Leaf) (out : PFact)
    (h : supplyField c f actual = some out) :
    BottomUpStaticDomain.LegalAP out ∧ out.mark = .conc actual.mark := by
  rw [supply_field_is_actual_projection] at h
  exact ⟨BottomUpStaticDomain.concrete_project_legal _ actual out h,
    Run1Requests.projector_keeps_mark _ actual out h⟩

theorem supply_field_exists_exactly (c f : Nat) (actual : Leaf) :
    (supplyField c f actual).isSome = Run1Requests.slotMatches (.field c f) actual := by
  rw [supply_field_is_actual_projection]
  exact Run1Requests.project_exists_iff _ _

theorem supply_field_ignores_E (c f : Nat) (actual : Leaf) (e : Excl) :
    supplyField c f {actual with fieldExclusion := e} = supplyField c f actual := by
  simp only [supply_field_is_actual_projection,Run1Requests.projector_ignores_field_E]

def supplyClass (c : Nat) (actual : Leaf) : Option PFact :=
  Run1Requests.classAnswer c actual

theorem supply_class_legal (c : Nat) (actual : Leaf) (out : PFact)
    (h : supplyClass c actual = some out) : BottomUpStaticDomain.LegalAP out :=
  BottomUpStaticDomain.class_answer_legal c actual out h

theorem supply_class_actual_mark (c : Nat) (actual : Leaf) (out : PFact)
    (h : supplyClass c actual = some out) : out.mark = .conc actual.mark := by
  unfold supplyClass Run1Requests.classAnswer at h
  cases hp : actual.point <;> simp only [hp] at h
  all_goals first | contradiction |
    (split at h <;> first | contradiction | (cases h; rfl))

theorem exact_field_supply (c f t : Nat) :
    supplyField c f ⟨.field c f,.exact,t,.empty,false⟩ =
      some (BottomUpStaticDomain.Initial.fieldExact c f t).toAP := by
  rw [supply_field_is_actual_projection,Run1Requests.static_exact_field]
  rfl

theorem chain_field_supply (c f t : Nat) (tail : Run1Requests.Tail)
    (nonexact : tail ≠ .exact) (e : Excl) (d : Bool) :
    supplyField c f ⟨.field c f,tail,t,e,d⟩ =
      some (BottomUpStaticDomain.Initial.fieldStar c f t).toAP := by
  rw [supply_field_is_actual_projection,
    Run1Requests.nonexact_field_chain c f t tail nonexact e d]
  rfl

theorem direct_class_supply (c t : Nat) (e : Excl) (d : Bool) :
    supplyClass c ⟨.cls c,.exact,t,e,d⟩ = some (BottomUpStaticDomain.Initial.classExact c t).toAP := by
  simp [supplyClass,Run1Requests.classAnswer,Run1Requests.concrete,
    BottomUpStaticDomain.Initial.toAP,BottomUpStaticDomain.encode,BottomUpStaticDomain.RawInitial.toAP,
    BottomUpStaticDomain.Place.base,BottomUpStaticDomain.Place.path,
    Run1Requests.Point.base,Run1Requests.Point.path]

theorem class_exact_does_not_fabricate_field (c f t : Nat) :
    supplyField c f ⟨.cls c,.exact,t,.empty,false⟩ = none := by
  rw [supply_field_is_actual_projection,Run1Requests.exact_class_not_field]

/-! ## Full origin and sticky concrete/static DEMAND, independent of tails. -/

def staticInitial : Initial → Bool
  | .abstractField _ _ | .fieldExact _ _ _ | .fieldStar _ _ _
    | .classExact _ _ | .classStar _ _ => true
  | _ => false

/-- The list is the WHOLE original premise, not a pool of independently true
    singleton facts. Port retains caller/callee contexts, call, stage and layer. -/
structure Input where
  port : Port
  original : List Initial
  current : Layered
  /-- W/transformation boundary: never silently recode as an identity carrier. -/
  ordinaryOnly : Bool := false
  /-- Opaque immutable whole-premise support IDs, not pooled proof evidence. -/
  support : List Nat := []
  deriving DecidableEq, Repr

def normalize (input : Input) : Input :=
  {input with current := ⟨input.current.fact,
    input.current.demand || input.original.any staticInitial ||
      (input.current.fact.base == 1)⟩}

theorem normalization_full_payload (input : Input) :
    (normalize input).port = input.port ∧
    (normalize input).original = input.original ∧
    (normalize input).current.fact = input.current.fact ∧
    (normalize input).ordinaryOnly = input.ordinaryOnly ∧
    (normalize input).support = input.support := ⟨rfl,rfl,rfl,rfl,rfl⟩

theorem normalization_sticky (input : Input) (h : input.current.demand = true) :
    (normalize input).current.demand = true := by simp [normalize,h]

theorem static_original_demand (input : Input)
    (h : input.original.any staticInitial = true) :
    (normalize input).current.demand = true := by simp [normalize,h]

theorem static_current_demand (input : Input) (h : input.current.fact.base = 1) :
    (normalize input).current.demand = true := by
  change (input.current.demand || input.original.any staticInitial ||
    (input.current.fact.base == 1)) = true
  rw [h]
  cases input.current.demand <;> cases input.original.any staticInitial <;> rfl

theorem normalization_idempotent (input : Input) : normalize (normalize input) = normalize input := by
  cases input with
  | mk port original current ordinaryOnly support =>
    cases current with
    | mk fact demand =>
      cases demand <;> cases h1 : original.any staticInitial <;>
        cases h2 : fact.base == 1 <;> simp [normalize,h1,h2]

def advance (input : Input) (next : PFact) (operationDemand : Bool) : Input :=
  normalize {input with current := ⟨next,input.current.demand || operationDemand⟩}

theorem advance_keeps_origin (input : Input) (next : PFact) (d : Bool) :
    (advance input next d).port = input.port ∧
      (advance input next d).original = input.original := ⟨rfl,rfl⟩

theorem static_history_stays_demand (input : Input) (next : PFact) (d : Bool)
    (h : input.current.demand = true) : (advance input next d).current.demand = true := by
  simp [advance,normalize,h]

def start (port : Port) (initial : Initial) : Input :=
  normalize ⟨port,[initial],BottomUpStaticDomain.startInitial initial,false,[]⟩

theorem exact_static_start_D (port : Port) (c f t : Nat) :
    (start port (.fieldExact c f t)).current =
      ⟨(BottomUpStaticDomain.Initial.fieldExact c f t).toAP,true⟩ := rfl

theorem chain_static_start_ANY_D (port : Port) (c f t : Nat) :
    (start port (.fieldStar c f t)).current =
      ⟨{(BottomUpStaticDomain.Initial.fieldStar c f t).toAP with kind := .any},true⟩ := rfl

theorem exact_class_start_D (port : Port) (c t : Nat) :
    (start port (.classExact c t)).current =
      ⟨(BottomUpStaticDomain.Initial.classExact c t).toAP,true⟩ := rfl

theorem chain_class_start_ANY_D (port : Port) (c t : Nat) :
    (start port (.classStar c t)).current =
      ⟨{(BottomUpStaticDomain.Initial.classStar c t).toAP with kind := .any},true⟩ := rfl

theorem independent_nonstatic_exact_start_N (port : Port) (b t : Nat) :
    (start port (.baseExact b t)).current =
      ⟨(BottomUpStaticDomain.Initial.baseExact b t).toAP,false⟩ := rfl

/-! ## BaseSlot requests remain; static origins use the separate fast need router. -/

def requestAt (owner : Owner) (original : List Initial) (mark : Mark) : Option Question :=
  match original with
  | [.abstractBase b] => some ⟨owner,.base b,mark⟩
  | _ => none

theorem base_request_retained (owner : Owner) (b t : Nat) :
    requestAt owner [.abstractBase b] t = some ⟨owner,.base b,t⟩ := rfl

theorem request_has_base_origin (owner : Owner) (original : List Initial) (t : Mark)
    (question : Question) (h : requestAt owner original t = some question) :
    ∃ b, original = [.abstractBase b] ∧ question = ⟨owner,.base b,t⟩ := by
  unfold requestAt at h
  split at h
  · rename_i b
    cases h
    exact ⟨b,rfl,rfl⟩
  · contradiction

theorem no_static_origin_request (owner : Owner) (initial : Initial) (t : Mark)
    (h : staticInitial initial = true) : requestAt owner [initial] t = none := by
  cases initial <;> simp_all [staticInitial,requestAt]

theorem base_request_even_current_static (owner : Owner) (b t : Nat) (input : Input)
    (h : input.original = [.abstractBase b]) :
    requestAt owner input.original t = some ⟨owner,.base b,t⟩ := by rw [h]; rfl

/-! ## Lossless concrete carrier coding; no mark Cartesian product. -/

inductive InitialKind where
  | exact | chain
  deriving DecidableEq, Repr

def fieldInitial (c f t : Nat) : InitialKind → Initial
  | .exact => .fieldExact c f t
  | .chain => .fieldStar c f t

structure CarrierToken where
  port : Port
  cls : Nat
  field : Nat
  mark : Mark
  initialKind : InitialKind
  deriving DecidableEq, Repr

def CarrierToken.decode (token : CarrierToken) : Input :=
  start token.port (fieldInitial token.cls token.field token.mark token.initialKind)

inductive Encoded where
  | carrier (token : CarrierToken)
  | bound (input : Input)
  deriving DecidableEq, Repr

def encode (input : Input) : Encoded :=
  match input.original with
  | [.fieldExact c f t] =>
      let token : CarrierToken := ⟨input.port,c,f,t,.exact⟩
      if input = token.decode then .carrier token else .bound input
  | [.fieldStar c f t] =>
      let token : CarrierToken := ⟨input.port,c,f,t,.chain⟩
      if input = token.decode then .carrier token else .bound input
  | _ => .bound input

def decode : Encoded → Input
  | .carrier token => token.decode
  | .bound input => input

theorem decode_encode (input : Input) : decode (encode input) = input := by
  unfold encode
  split
  all_goals try dsimp only
  all_goals try split
  all_goals first | rfl | (rename_i same; exact same.symm)

theorem interpreter_preserved {Result : Type} (run : Input → Result) (input : Input) :
    run (decode (encode input)) = run input := by rw [decode_encode]

theorem encoded_inputs_exact (inputs : List Input) :
    (inputs.map encode).map decode = inputs := by
  induction inputs with
  | nil => rfl
  | cons head rest ih => simp [decode_encode,ih]

theorem exact_carrier_compresses (port : Port) (c f t : Nat) :
    encode (start port (.fieldExact c f t)) = .carrier ⟨port,c,f,t,.exact⟩ := by
  simp [encode,start,normalize,staticInitial,CarrierToken.decode,fieldInitial]

theorem chain_carrier_compresses (port : Port) (c f t : Nat) :
    encode (start port (.fieldStar c f t)) = .carrier ⟨port,c,f,t,.chain⟩ := by
  simp [encode,start,normalize,staticInitial,CarrierToken.decode,fieldInitial]

theorem nary_stays_bound (input : Input) (a b : Initial) (rest : List Initial)
    (h : input.original = a :: b :: rest) : encode input = .bound input := by
  simp [encode,h]

theorem effect_stays_bound (input : Input) (effect : input.ordinaryOnly = true) :
    encode input = .bound input := by
  unfold encode
  split
  all_goals try dsimp only
  all_goals try split
  all_goals
    first
    | rfl
    | rename_i same
      have impossible := congrArg Input.ordinaryOnly same
      simp [CarrierToken.decode,start,normalize,effect] at impossible

theorem nonidentity_exact_stays_bound (port : Port) (c f t : Nat) (input : Input)
    (origin : input.original = [.fieldExact c f t]) (samePort : input.port = port)
    (different : input ≠ start port (.fieldExact c f t)) : encode input = .bound input := by
  simp [encode,origin,CarrierToken.decode,fieldInitial,samePort,different]

/-! ## Transport compression is not chain-summary identity compaction. -/

theorem exact_summary_contained_in_root (c f t : Nat) (before after : Loc)
    (pair : den (BottomUpStaticDomain.Initial.fieldExact c f t).toAP
      (BottomUpStaticDomain.Initial.fieldExact c f t).toAP before after) :
    den BottomUpStaticApplication.staticRoot BottomUpStaticApplication.staticRoot before after := by
  obtain ⟨base0,base1,mark0,mark1,_,suffix,rest,path0,path1,tail0,tail1⟩ := pair
  have beforeMark : before.mark = t := mark0
  have afterMark : after.mark = t := mark1
  have mark : after.mark = before.mark := afterMark.trans beforeMark.symm
  have empty0 : suffix = [] := tail0
  have empty1 : rest = [] := tail1
  have samePath : after.path = before.path := by rw [path1,path0,empty0,empty1]
  refine ⟨base0,base1,trivial,mark,trivial,before.path,before.path,?_,?_,?_,?_,?_⟩
  · simp [BottomUpStaticApplication.staticRoot,Run1DefaultSummaryApplication.rootSTAR]
  · simpa [BottomUpStaticApplication.staticRoot,Run1DefaultSummaryApplication.rootSTAR] using samePath
  · exact CoreAux.empty_admits _
  · rfl
  · exact CoreAux.empty_admits _

/-- Concrete STAR→ANY is uncorrelated. Even with the SAME field and mark it
    can relate different suffixes, which the root identity cannot supply. -/
theorem chain_summary_not_root_identity (c f t a b : Nat) (different : a ≠ b) :
    den (BottomUpStaticDomain.Initial.fieldStar c f t).toAP
      {(BottomUpStaticDomain.Initial.fieldStar c f t).toAP with kind := .any}
      ⟨1,[2*c,2*f+1,a],t⟩ ⟨1,[2*c,2*f+1,b],t⟩ ∧
    ¬ den BottomUpStaticApplication.staticRoot BottomUpStaticApplication.staticRoot
      ⟨1,[2*c,2*f+1,a],t⟩ ⟨1,[2*c,2*f+1,b],t⟩ := by
  constructor
  · exact ⟨rfl,rfl,rfl,rfl,trivial,[a],[b],rfl,rfl,CoreAux.empty_admits _,trivial⟩
  · intro root
    obtain ⟨_,_,_,_,_,suffix,rest,path0,path1,_,tail⟩ := root
    have tails : rest = suffix := tail.1
    change [2*c,2*f+1,a] = [] ++ suffix at path0
    change [2*c,2*f+1,b] = [] ++ rest at path1
    simp only [List.nil_append] at path0 path1
    have paths : [2*c,2*f+1,b] = [2*c,2*f+1,a] := path1.trans (tails.trans path0.symm)
    have same : b = a := by simpa only [List.cons.injEq,true_and,and_true] using paths
    exact different same.symm

def compactableExact (input : Input) : Bool :=
  if input.ordinaryOnly || !input.support.isEmpty then false else match input.original with
  | [.fieldExact c f t] =>
      input.current == (start input.port (.fieldExact c f t)).current
  | _ => false

theorem exact_compaction_full_guard (input : Input) (h : compactableExact input = true) :
    ∃ c f t, input.original = [.fieldExact c f t] ∧
      input.current = ⟨(BottomUpStaticDomain.Initial.fieldExact c f t).toAP,true⟩ := by
  unfold compactableExact at h
  split at h
  · contradiction
  · split at h
    · rename_i c f t origin
      exact ⟨c,f,t,origin,by simpa only [beq_iff_eq,exact_static_start_D] using h⟩
    · contradiction

theorem chain_not_compactable (port : Port) (c f t : Nat) :
    compactableExact (start port (.fieldStar c f t)) = false := rfl

theorem nary_not_compactable (input : Input) (a b : Initial) (rest : List Initial)
    (h : input.original = a :: b :: rest) : compactableExact input = false := by
  simp [compactableExact,h]

theorem compacted_exact_is_D (input : Input) (h : compactableExact input = true) :
    input.current.demand = true := by
  obtain ⟨_,_,_,_,current⟩ := exact_compaction_full_guard input h
  rw [current]

/-- A root D default never covers a NORMAL contribution at the layer level. -/
theorem normal_not_compactable (input : Input) (normal : input.current.demand = false) :
    compactableExact input = false := by
  cases h : compactableExact input with
  | false => rfl
  | true => have demand := compacted_exact_is_D input h; rw [normal] at demand; cases demand

def persistInputs (eligible : Input → Bool) (rows : List Input) : List Input :=
  rows.filter fun input => !input.current.demand && eligible input

def compactInputs (rows : List Input) : List Input :=
  rows.filter fun input => !compactableExact input

theorem concrete_compaction_preserves_normal_persistence
    (eligible : Input → Bool) (rows : List Input) :
    persistInputs eligible (compactInputs rows) = persistInputs eligible rows := by
  unfold persistInputs compactInputs
  rw [List.filter_filter]
  apply List.filter_congr
  intro input _
  cases compactable : compactableExact input with
  | false => simp
  | true =>
    have demand := compacted_exact_is_D input compactable
    simp [demand]

def summaryPair (input : Input) (before after : Loc) : Prop :=
  ∃ initial, input.original = [initial] ∧ den initial.toAP input.current.fact before after

theorem compactable_pair_covered (input : Input) (before after : Loc)
    (compactable : compactableExact input = true) (pair : summaryPair input before after) :
    input.current.demand = true ∧
      den BottomUpStaticApplication.staticRoot BottomUpStaticApplication.staticRoot before after := by
  obtain ⟨c,f,t,origin,current⟩ := exact_compaction_full_guard input compactable
  obtain ⟨initial,singleton,relation⟩ := pair
  have initials : initial = .fieldExact c f t := List.singleton_inj.mp (singleton.symm.trans origin)
  subst initial
  rw [current] at relation
  exact ⟨compacted_exact_is_D input compactable,exact_summary_contained_in_root c f t before after relation⟩

/-! ## Executable local consumers, with separate nonerasable event outputs. -/

inductive Event where
  | observed (occurrence : Nat) (input : Input)
  | requested (question : Question)
  deriving DecidableEq, Repr

structure Result where
  states : List Input
  events : List Event
  deriving DecidableEq, Repr

inductive Instruction where
  | pass
  | ap (source target : PFact) (demand : Bool)
  | preserveWrite (target : Run1StrongPreservation.FieldTarget)
  /-- Spatial reach/predicate was matched by the ordinary component selector.
      This small evaluator certifies only an exact point. Partial broad/chain
      cleaners stay ordinary fallback; a mere overlap never licenses full kill. -/
  | matchedClean (mark : Mark)
  | check (mark : Mark) (occurrence : Nat)
  | observe (occurrence : Nat)
  | unknown
  deriving DecidableEq, Repr

def apRequests (owner : Owner) (input : Input) (marks : List Mark) : List Event :=
  marks.filterMap fun mark => (requestAt owner input.original mark).map Event.requested

def preserveWrite (target : Run1StrongPreservation.FieldTarget) (input : Input) : List Input :=
  let edge : Run1StrongPreservation.Edge (Port × List Initial) :=
    ⟨(input.port,input.original),input.current⟩
  (Run1StrongPreservation.preserve .definite target edge).toList.map fun kept =>
    normalize ⟨kept.original.1,kept.original.2,kept.current,true,input.support⟩

/-- No static R exists in this evaluator. TAINT check/clean use the actual mark;
    opaque/nonconcrete cases explicitly require ordinary fallback. -/
def eval (owner : Owner) (instruction : Instruction) (raw : Input) : Option Result :=
  let input := normalize raw
  match instruction with
  | .pass => some ⟨[input],[]⟩
  | .ap source target demand =>
      let ordinary := applySummary ⟨input.current.fact,false⟩ source ⟨target,false⟩
      let applied := Run1DefaultSummaryApplication.withEdgeLayers
        input.current.demand demand ordinary
      some ⟨applied.facts.map (fun out =>
          {(advance input out.fact out.demand) with ordinaryOnly := true}),
        apRequests owner input applied.reqs⟩
  | .preserveWrite target => some ⟨preserveWrite target input,[]⟩
  | .matchedClean selected =>
      if input.current.fact.kind == .exact then match input.current.fact.mark with
        | .conc actual => some ⟨if actual == selected then [] else [input],[]⟩
        | _ => none
      else none
  | .check selected occurrence => match input.current.fact.mark with
      | .conc actual => some
          (if actual == selected then ⟨[input],[.observed occurrence input]⟩ else ⟨[],[]⟩)
      | _ => none
  | .observe occurrence => some ⟨[input],[.observed occurrence input]⟩
  | .unknown => none

/-- Use the concrete decidable Nat equality, rather than a generic ordering
    law instance whose transitive proof may use classical choice. -/
theorem mark_beq_refl (mark : Mark) : (mark == mark) = true := by
  change decide (mark = mark) = true
  exact decide_eq_true rfl

theorem actual_check_no_request (owner : Owner) (input : Input) (t occurrence : Nat)
    (actual : input.current.fact.mark = .conc t) :
    eval owner (.check t occurrence) input =
      some ⟨[normalize input],[.observed occurrence (normalize input)]⟩ := by
  change (match input.current.fact.mark with
    | .conc value => some (if value == t then
        (⟨[normalize input],[.observed occurrence (normalize input)]⟩ : Result) else ⟨[],[]⟩)
    | _ => none) = _
  rw [actual]
  change some (if t == t then
    (⟨[normalize input],[.observed occurrence (normalize input)]⟩ : Result) else ⟨[],[]⟩) = _
  exact congrArg some (if_pos (mark_beq_refl t))

theorem concrete_clean_kills (owner : Owner) (input : Input) (t : Nat)
    (actual : input.current.fact.mark = .conc t) (point : input.current.fact.kind = .exact) :
    eval owner (.matchedClean t) input = some ⟨[],[]⟩ := by
  change (if input.current.fact.kind == .exact then
    match input.current.fact.mark with
    | .conc value => some (⟨if value == t then [] else [normalize input],[]⟩ : Result)
    | _ => none
    else none) = _
  rw [point,actual]
  change some (⟨if t == t then [] else [normalize input],[]⟩ : Result) = _
  exact congrArg (fun states => some (⟨states,[]⟩ : Result)) (if_pos (mark_beq_refl t))

theorem broad_clean_requires_fallback (owner : Owner) (input : Input) (t : Nat)
    (broad : input.current.fact.kind ≠ .exact) :
    eval owner (.matchedClean t) input = none := by simp [eval,normalize,broad]

theorem unknown_requires_fallback (owner : Owner) (input : Input) :
    eval owner .unknown input = none := rfl

theorem static_requests_empty (owner : Owner) (initial : Initial) (marks : List Mark)
    (input : Input) (origin : input.original = [initial])
    (static : staticInitial initial = true) : apRequests owner input marks = [] := by
  simp only [apRequests,origin]
  induction marks with
  | nil => rfl
  | cons mark rest ih =>
    simp only [List.filterMap_cons,no_static_origin_request owner initial mark static,
      Option.map_none,ih]

/-- Actual executable equality check, rather than an assumed transparency
    predicate. Every successful result is one unchanged FULL normalized input,
    and the empty event channel excludes requests and source/sink observations. -/
def checkPrimitive (owner : Owner) (instruction : Instruction) (input : Input) : Bool :=
  match eval owner instruction input with
  | some result => result == (⟨[input],[]⟩ : Result)
  | none => false

theorem checked_primitive_exact (owner : Owner) (instruction : Instruction) (input : Input)
    (checked : checkPrimitive owner instruction input = true) :
    eval owner instruction input = some ⟨[input],[]⟩ := by
  cases evaluated : eval owner instruction input with
  | none => simp [checkPrimitive,evaluated] at checked
  | some result =>
    have same : result = ⟨[input],[]⟩ := by simpa [checkPrimitive,evaluated] using checked
    exact congrArg some same

theorem check_never_transparent (owner : Owner) (input : Input) (mark occurrence : Nat) :
    checkPrimitive owner (.check mark occurrence) input = false := by
  cases h : input.current.fact.mark <;>
    simp only [checkPrimitive,eval,normalize,h]
  all_goals first | rfl | (split <;> simp_all)

theorem unknown_never_transparent (owner : Owner) (input : Input) :
    checkPrimitive owner .unknown input = false := rfl

inductive Payload where
  | fact (input : Input)
  | notice (input : Input)
  deriving DecidableEq, Repr

structure Operation where
  owner : Owner
  instruction : Instruction
  deriving DecidableEq, Repr

def outcomes (input : Input) (result : Result) : List (Payload × List Event) :=
  result.states.map (fun state => (.fact state,[])) ++
    (if result.events.isEmpty then [] else [(.notice input,result.events)])

def kernel (fallback : Operation → Input → Result) :
    BottomUpStaticTransport.Kernel Operation Payload Event
  | operation,.fact input => outcomes input ((eval operation.owner operation.instruction input).getD
      (fallback operation input))
  | _,.notice _ => []

theorem events_survive_without_facts (input : Input) (head : Event) (tail : List Event) :
    outcomes input ⟨[],head :: tail⟩ = [(.notice input,head :: tail)] := rfl

theorem concrete_transport_equivalence {Node : Type}
    (graph : List (BottomUpStaticTransport.Edge Node Operation))
    (fallback : Operation → Input → Result) (a b) :
    Nonempty (BottomUpStaticTransport.Forward graph (kernel fallback) a b) ↔
      Nonempty (BottomUpStaticTransport.BottomUp graph (kernel fallback) a b) :=
  BottomUpStaticTransport.path_equivalence graph (kernel fallback) a b

theorem concrete_nary_equivalence {Node : Type}
    (graph : List (BottomUpStaticTransport.Edge Node Operation))
    (fallback : Operation → Input → Result) (seeds clauses value) :
    Nonempty (BottomUpStaticTransport.Derivation
      (BottomUpStaticTransport.Forward graph (kernel fallback)) seeds clauses value) ↔
    Nonempty (BottomUpStaticTransport.Derivation
      (BottomUpStaticTransport.BottomUp graph (kernel fallback)) seeds clauses value) :=
  BottomUpStaticTransport.mutual_finite_derivation_equivalence graph (kernel fallback) seeds clauses value

/-- Snapshot-dependent call/summary discovery can use the generic positive
    round constructor, with this concrete kernel at every snapshot. The shared
    constructor is explicit; arbitrary differing JVM inventories are not
    assumed equal, and finite-round equality is not termination. -/
def concreteProgram {Node : Type}
    (graph : BottomUpStaticTransport.Snapshot
      (BottomUpStaticTransport.State Node Operation Payload Event) →
        List (BottomUpStaticTransport.Edge Node Operation))
    (fallback : BottomUpStaticTransport.Snapshot
      (BottomUpStaticTransport.State Node Operation Payload Event) → Operation → Input → Result)
    (seeds : BottomUpStaticTransport.Snapshot
      (BottomUpStaticTransport.State Node Operation Payload Event) →
        List (BottomUpStaticTransport.State Node Operation Payload Event))
    (clauses : BottomUpStaticTransport.Snapshot
      (BottomUpStaticTransport.State Node Operation Payload Event) →
        List (BottomUpStaticTransport.Clause
          (BottomUpStaticTransport.State Node Operation Payload Event))) :
    BottomUpStaticTransport.RoundProgram Node Operation Payload Event :=
  ⟨graph,(fun snapshot => kernel (fallback snapshot)),seeds,clauses⟩

theorem concrete_finite_round_closure {Node : Type}
    (program : BottomUpStaticTransport.RoundProgram Node Operation Payload Event) (initial value) :
    (∃ n, BottomUpStaticTransport.iterate
      (BottomUpStaticTransport.ordinaryRound program) initial n value) ↔
    ∃ n, BottomUpStaticTransport.iterate
      (BottomUpStaticTransport.bottomUpRound program) initial n value :=
  BottomUpStaticTransport.finite_round_closure_equal program initial value

theorem concrete_both_arrival_orders {Node Key : Type} [DecidableEq Node]
    (fallback : Operation → Input → Result)
    (state : BottomUpStaticTransport.JoinState Node Operation Payload Event Key)
    (complete : BottomUpStaticTransport.JoinComplete (kernel fallback) state)
    (inputs effects value) :
    value ∈ (BottomUpStaticTransport.effectArrives (kernel fallback)
      (BottomUpStaticTransport.inputArrives (kernel fallback) state inputs) effects).observed ↔
    value ∈ (BottomUpStaticTransport.inputArrives (kernel fallback)
      (BottomUpStaticTransport.effectArrives (kernel fallback) state effects) inputs).observed :=
  BottomUpStaticTransport.both_arrival_orders (kernel fallback) state complete inputs effects value

/-- Whole-premise matching reuses ordinary literal tests after exact decoding;
    neither mark pooling nor per-member implication is introduced. -/
def protocolInput (input : Input) : BottomUpStaticProtocol.Input :=
  ⟨input.port,.concrete (input.original.map BottomUpStaticDomain.Initial.toAP),
    ⟨input.current.fact,input.current.demand⟩⟩

theorem same_port_nary_codec (port : Port) (literals : List PFact) (inputs : List Input)
    (admit : PFact → BottomUpStaticProtocol.Input → Bool) :
    BottomUpStaticProtocol.joinedInputs port literals
      (((inputs.map encode).map decode).map protocolInput) admit =
      BottomUpStaticProtocol.joinedInputs port literals (inputs.map protocolInput) admit := by
  rw [encoded_inputs_exact]

/-- Literal layouts, not measured byte or runtime savings. Only this transparent
    chain cohort has the demonstrated strict reduction. -/
theorem concrete_chain_storage_reduction (extraRelays extraBindings : Nat) :
    (BottomUpStaticTransport.contractedLayout (extraRelays + 3)
      (List.range (extraBindings + 2))).slots <
    (BottomUpStaticTransport.ordinaryLayout (extraRelays + 3)
      (List.range (extraBindings + 2))).length :=
  BottomUpStaticTransport.concrete_chain_layout_reduction extraRelays extraBindings

def concreteBindings (port : Port) (c f count : Nat) : List Input :=
  (List.range count).map fun mark => start port (.fieldExact c f mark)

/-- The layout theorem also holds with actual full concrete inputs as endpoint
    labels. It counts slots only; structural sharing and byte sizes are not
    asserted by this cost model. -/
theorem actual_concrete_chain_reduction (port : Port) (c f extraRelays extraBindings : Nat) :
    (BottomUpStaticTransport.contractedLayout (extraRelays + 3)
      (concreteBindings port c f (extraBindings + 2))).slots <
    (BottomUpStaticTransport.ordinaryLayout (extraRelays + 3)
      (concreteBindings port c f (extraBindings + 2))).length := by
  rw [BottomUpStaticTransport.contracted_layout_slots,
    BottomUpStaticTransport.ordinary_layout_length]
  simp only [concreteBindings,List.length_map,List.length_range]
  exact BottomUpStaticTransport.chain_space_strict extraRelays extraBindings

/-! ## Independent executable concrete consumer and negative controls. -/
namespace Controls

def owner : Owner := ⟨7,2,[3,5]⟩
def otherOwner : Owner := ⟨7,3,[3,5]⟩
def port : Port := ⟨⟨1,0,[2]⟩,13,owner,17,false⟩
def otherPort : Port := {port with callee := otherOwner}
def input : Input := start port (.fieldExact 2 3 11)
def chainInput : Input := start port (.fieldStar 2 3 11)
def readSource : PFact := (Run1Requests.Slot.field 2 3).initial
def readTarget : PFact := (Run1Requests.Slot.base 0).initial
def read : Operation := ⟨owner,.ap readSource readTarget false⟩
def localInput : Input :=
  {(advance input (BottomUpStaticDomain.Initial.baseExact 0 11).toAP true) with ordinaryOnly := true}
def checked : Operation := ⟨owner,.check 11 23⟩
def target : Run1StrongPreservation.FieldTarget := ⟨1,[4],7⟩
def killed : Operation := ⟨owner,.preserveWrite target⟩
def fallback (_ : Operation) (value : Input) : Result := ⟨[value],[.observed 999 value]⟩
def changedMark : Input := advance input
  {(BottomUpStaticDomain.Initial.fieldExact 2 3 11).toAP with mark := .conc 19} false
def changedField : Input := advance input (BottomUpStaticDomain.Initial.fieldExact 2 4 11).toAP false
def wholeND : Input := {input with original := [.fieldExact 2 3 11,.baseExact 0 19]}

def readEffect : BottomUpStaticTransport.Effect Nat Operation := ⟨1,2,.statement,read⟩
def checkEffect : BottomUpStaticTransport.Effect Nat Operation := ⟨3,4,.sink,checked⟩
def graph : List (BottomUpStaticTransport.Edge Nat Operation) :=
  [.relay 0 1,.effect readEffect,.relay 2 3,.effect checkEffect]
def first : BottomUpStaticTransport.State Nat Operation Payload Event := ⟨0,.fact input,[]⟩
def afterRead : BottomUpStaticTransport.State Nat Operation Payload Event :=
  BottomUpStaticTransport.fire readEffect (.fact input) (.fact localInput) [] []
def last : BottomUpStaticTransport.State Nat Operation Payload Event :=
  BottomUpStaticTransport.fire checkEffect (.fact localInput) (.notice localInput)
    [.observed 23 localInput] afterRead.history

def ordinary : BottomUpStaticTransport.Forward graph (kernel fallback) first last :=
  .next (.relay (b := 1) (by decide))
    (.next (.effect (e := readEffect) (q := .fact localInput) (vs := []) (by decide) (by decide))
      (.next (.relay (b := 3) (by decide))
        (.next (.effect (e := checkEffect) (q := .notice localInput)
          (vs := [.observed 23 localInput]) (by decide) (by decide)) (.done last))))

def contracted : BottomUpStaticTransport.BottomUp graph (kernel fallback) first last :=
  BottomUpStaticTransport.compress ordinary

def expanded : BottomUpStaticTransport.Forward graph (kernel fallback) first last :=
  BottomUpStaticTransport.expand contracted

def binding : BottomUpStaticTransport.Binding Nat Payload Port := ⟨port,3,.fact localInput⟩
def emptyJoin : BottomUpStaticTransport.JoinState Nat Operation Payload Event Port := .empty
def inputFirst := BottomUpStaticTransport.effectArrives (kernel fallback)
  (BottomUpStaticTransport.inputArrives (kernel fallback) emptyJoin [binding]) [checkEffect]
def effectFirst := BottomUpStaticTransport.inputArrives (kernel fallback)
  (BottomUpStaticTransport.effectArrives (kernel fallback) emptyJoin [checkEffect]) [binding]

def protocolInputs : List BottomUpStaticProtocol.Input :=
  [protocolInput input,protocolInput {input with port := otherPort}]
def twoLiterals : List PFact := [input.current.fact,input.current.fact]
def admit (literal : PFact) (value : BottomUpStaticProtocol.Input) : Bool :=
  literal == value.current.fact

def checks : List Bool := [
  decide (supplyField 2 3 ⟨.field 2 3,.exact,11,.empty,false⟩ =
    some (BottomUpStaticDomain.Initial.fieldExact 2 3 11).toAP),
  decide (supplyField 2 3 ⟨.field 2 3,.must,0,.set [9],false⟩ =
    some (BottomUpStaticDomain.Initial.fieldStar 2 3 0).toAP),
  decide (supplyField 2 3 ⟨.field 2 4,.exact,11,.empty,false⟩ = none),
  decide (supplyClass 2 ⟨.cls 2,.exact,11,.empty,false⟩ =
    some (BottomUpStaticDomain.Initial.classExact 2 11).toAP),
  decide (eval owner read.instruction input = some ⟨[localInput],[]⟩),
  decide (eval owner checked.instruction localInput = some ⟨[localInput],[.observed 23 localInput]⟩),
  decide (eval owner (.check 19 23) localInput = some ⟨[],[]⟩),
  decide (eval owner (.matchedClean 11) input = some ⟨[],[]⟩),
  decide (eval owner (.matchedClean 19) input = some ⟨[input],[]⟩),
  decide (eval owner (.matchedClean 11) chainInput = none),
  decide (kernel fallback killed (.fact input) = []),
  decide (encode changedMark = .bound changedMark),
  decide (encode changedField = .bound changedField),
  decide (encode wholeND = .bound wholeND),
  decide (encode {input with ordinaryOnly := true} = .bound {input with ordinaryOnly := true}),
  decide (encode {input with support := [31]} = .bound {input with support := [31]}),
  decide (compactableExact input = true),
  decide (compactableExact chainInput = false),
  decide ((normalize {input with current := ⟨input.current.fact,false⟩}).current.fact = input.current.fact),
  decide (requestAt owner [.abstractField 2 3] 11 = none),
  decide (requestAt owner [.abstractBase 0] 11 = some ⟨owner,.base 0,11⟩),
  decide (inputFirst.observed = effectFirst.observed),
  decide ((BottomUpStaticProtocol.joinedInputs port twoLiterals protocolInputs admit).length = 1),
  decide ((BottomUpStaticProtocol.joinedInputs port twoLiterals protocolInputs admit).all
    (fun combination => combination.length == 2 && combination.all (fun value => value.port == port))),
  decide (last.history.length = 2),
  decide (contracted.effectCount = 2)
]

theorem checks_pass : checks.all id = true := by decide

end Controls

end ApSpec.BottomUpConcreteStatic
