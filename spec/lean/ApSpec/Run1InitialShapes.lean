/-
  Closed F78 run-1 INITIAL vocabulary, independent of current-fact vocabulary.
  There are ten shapes, not just the two normal ABSTRACT request slots.

  This certifies the selected firstLimit=1, valid JVM class-prefix supplier
  grammar and the F78 constructors below. It does NOT prove that the current
  generic driver (which permits larger first limits), historical abstract-class
  factory, arbitrary InitialAp API calls, or every runtime route obeys F78.

  The current F1 starts with EMPTY handoff seeds. Sink/trigger seeds create
  statement facts, not InitialAp; restricted demand emission and native-record
  replay are not run-1 initial factories. The zero root/zero incoming link are
  explicitly included below. Concrete mark IDs include 0 on nonzero bases;
  only the zero BASE has the canonical mark/tail restriction.
-/
import ApSpec.Run1Requests

namespace ApSpec.Run1InitialShapes
open ApSpec ApSpec.Run1Requests

/-- Includes forbidden raw cases, so absence of must/deep initials is a theorem
    of classification, not an omission from the raw input type. -/
inductive InitialTail where
  | exact | star | any | must
  deriving DecidableEq, Repr

inductive Place where
  | zero
  | at (point : Point)
  | staticDeep (cls field next : Nat) (rest : List Acc)
  deriving DecidableEq, Repr

structure RawInitial where
  place : Place
  tail : InitialTail
  mark : MarkA
  exclusion : Excl
  deriving DecidableEq, Repr

inductive Shape where
  | zero
  | abstractBase (base : Nat)
  | abstractRoot
  | abstractField (cls field : Nat)
  | baseExact (base mark : Nat)
  | baseChain (base mark : Nat)
  | fieldExact (cls field mark : Nat)
  | fieldChain (cls field mark : Nat)
  | classExact (cls mark : Nat)
  | classChain (cls mark : Nat)
  deriving DecidableEq, Repr

def encode : Shape → RawInitial
  | .zero => ⟨.zero,.exact,.conc 0,.empty⟩
  | .abstractBase b => ⟨.at (.local b []),.star,.star,.empty⟩
  | .abstractRoot => ⟨.at .root,.star,.star,.empty⟩
  | .abstractField c f => ⟨.at (.field c f),.star,.star,.empty⟩
  | .baseExact b t => ⟨.at (.local b []),.exact,.conc t,.empty⟩
  | .baseChain b t => ⟨.at (.local b []),.star,.conc t,.empty⟩
  | .fieldExact c f t => ⟨.at (.field c f),.exact,.conc t,.empty⟩
  | .fieldChain c f t => ⟨.at (.field c f),.star,.conc t,.empty⟩
  | .classExact c t => ⟨.at (.cls c),.exact,.conc t,.empty⟩
  | .classChain c t => ⟨.at (.cls c),.star,.conc t,.empty⟩

/-- Constructive parser returns an actual shape witness and its encoding proof.
    Rejected raw cases include abstract class/ANY/deep, concrete empty S root,
    non-root ordinary paths, must tails, all E and excluded initial marks. -/
def classify : (i : RawInitial) → Option {s : Shape // encode s = i}
  | ⟨.zero,.exact,.conc 0,.set []⟩ => some ⟨.zero,rfl⟩
  | ⟨.at (.local b []),.star,.star,.set []⟩ => some ⟨.abstractBase b,rfl⟩
  | ⟨.at .root,.star,.star,.set []⟩ => some ⟨.abstractRoot,rfl⟩
  | ⟨.at (.field c f),.star,.star,.set []⟩ => some ⟨.abstractField c f,rfl⟩
  | ⟨.at (.local b []),.exact,.conc t,.set []⟩ => some ⟨.baseExact b t,rfl⟩
  | ⟨.at (.local b []),.star,.conc t,.set []⟩ => some ⟨.baseChain b t,rfl⟩
  | ⟨.at (.field c f),.exact,.conc t,.set []⟩ => some ⟨.fieldExact c f t,rfl⟩
  | ⟨.at (.field c f),.star,.conc t,.set []⟩ => some ⟨.fieldChain c f t,rfl⟩
  | ⟨.at (.cls c),.exact,.conc t,.set []⟩ => some ⟨.classExact c t,rfl⟩
  | ⟨.at (.cls c),.star,.conc t,.set []⟩ => some ⟨.classChain c t,rfl⟩
  | _ => none

def check (i : RawInitial) : Bool := (classify i).isSome
def Legal (i : RawInitial) : Prop := ∃ s, encode s = i

theorem classify_encode (s : Shape) : (classify (encode s)).isSome = true := by
  cases s <;> rfl

/-- Exact closed grammar with an executable witness extractor, not just a
    predicate chosen to hold of the constructors being checked. -/
theorem classification_exact (i : RawInitial) : check i = true ↔ Legal i := by
  constructor
  · intro h
    cases hc : classify i with
    | none => simp [check,hc] at h
    | some witness => exact ⟨witness.val,witness.property⟩
  · rintro ⟨s,rfl⟩
    exact classify_encode s

theorem legal_empty_E (i : RawInitial) (h : Legal i) : i.exclusion = .empty := by
  obtain ⟨s,rfl⟩ := h
  cases s <;> rfl

theorem legal_no_any (i : RawInitial) (h : Legal i) : i.tail ≠ .any := by
  obtain ⟨s,rfl⟩ := h
  cases s <;> simp [encode]

theorem legal_no_must (i : RawInitial) (h : Legal i) : i.tail ≠ .must := by
  obtain ⟨s,rfl⟩ := h
  cases s <;> simp [encode]

theorem legal_no_excluded_initial_mark (i : RawInitial) (h : Legal i) (xs : List Mark) :
    i.mark ≠ .starEx xs := by
  obtain ⟨s,rfl⟩ := h
  cases s <;> simp [encode]

theorem zero_base_canonical (i : RawInitial) (h : Legal i) (hz : i.place = .zero) :
    i = encode .zero := by
  obtain ⟨s,rfl⟩ := h
  cases s <;> simp_all [encode]

theorem no_concrete_empty_static_root (tail : InitialTail) (t : Mark) (e : Excl) :
    ¬ Legal ⟨.at .root,tail,.conc t,e⟩ := by
  rintro ⟨s,hs⟩
  cases s <;> simp [encode] at hs

theorem no_abstract_class (c : Nat) (tail : InitialTail) (e : Excl) :
    ¬ Legal ⟨.at (.cls c),tail,.star,e⟩ := by
  rintro ⟨s,hs⟩
  cases s <;> simp [encode] at hs

theorem no_nonstatic_field_initial (b next : Nat) (rest : List Acc)
    (tail : InitialTail) (mark : MarkA) (e : Excl) :
    ¬ Legal ⟨.at (.local b (next::rest)),tail,mark,e⟩ := by
  rintro ⟨s,hs⟩
  cases s <;> simp [encode] at hs

theorem no_static_deep_initial (c f next : Nat) (rest : List Acc)
    (tail : InitialTail) (mark : MarkA) (e : Excl) :
    ¬ Legal ⟨.staticDeep c f next rest,tail,mark,e⟩ := by
  rintro ⟨s,hs⟩
  cases s <;> simp [encode] at hs

theorem no_abstract_any (place : Place) (e : Excl) :
    ¬ Legal ⟨place,.any,.star,e⟩ := by
  rintro ⟨s,hs⟩
  cases s <;> simp [encode] at hs

def Place.base : Place → Base
  | .zero => zeroBase
  | .at p => p.base
  | .staticDeep _ _ _ _ => 1

def Place.path : Place → List Acc
  | .zero => []
  | .at p => p.path
  | .staticDeep c f x rest => [2*c,2*f+1,x] ++ rest

/-- Must is present in the RAW vocabulary, but no Legal input reaches it.
    The old PFact AP model stores must/may in one Kind and keeps the layer separate. -/
def RawInitial.toAP (i : RawInitial) : PFact :=
  ⟨i.place.base,i.place.path,
    match i.tail with
    | .exact => .exact
    | .star => .star i.exclusion
    | .any | .must => .any,
    i.mark⟩

def Shape.toAP (s : Shape) : PFact := (encode s).toAP
def LegalAP (i : PFact) : Prop := ∃ s : Shape, s.toAP = i

/-- Universal no-ANY statement for the encoded AP initial, not its
    start/current fact. Concrete STAR still starts as ANY in the DEMAND layer. -/
theorem legalAP_no_any (i : PFact) (h : LegalAP i) : i.kind ≠ .any := by
  obtain ⟨s,rfl⟩ := h
  cases s <;> simp [Shape.toAP,RawInitial.toAP,encode]

theorem root_zero_legal : LegalAP zeroFact := ⟨.zero,rfl⟩

theorem slot_initial_legal (slot : Slot) : LegalAP slot.initial := by
  cases slot with
  | base b => exact ⟨.abstractBase b,rfl⟩
  | field c f => exact ⟨.abstractField c f,rfl⟩

theorem special_root_legal : LegalAP specialRoot := ⟨.abstractRoot,rfl⟩

theorem static_abstract_starts_legal (slots : List Slot) (j : PFact)
    (h : j ∈ staticAbstractStarts slots) : LegalAP j := by
  simp only [staticAbstractStarts, List.mem_cons, List.mem_map] at h
  rcases h with rfl | ⟨slot,_,rfl⟩
  · exact special_root_legal
  · exact slot_initial_legal slot

/-- Every successful selected request projector output is in the exact grammar.
    The supplier may be broad or precise; E does not create another initial kind. -/
theorem request_projector_legal (slot : Slot) (a : Leaf) (j : PFact)
    (h : project slot a = some j) : LegalAP j := by
  unfold project at h
  split at h
  · cases h
  · cases slot with
    | base b =>
      dsimp only at h
      split at h
      · split at h
        · simp only [Option.some.injEq] at h
          exact ⟨.baseExact b a.mark,h⟩
        · simp only [Option.some.injEq] at h
          exact ⟨.baseChain b a.mark,h⟩
      · simp only [Option.some.injEq] at h
        exact ⟨.baseChain b a.mark,h⟩
    | field c f =>
      cases hp : a.point <;> simp only [hp] at h
      · simp only [Option.some.injEq] at h
        exact ⟨.fieldChain c f a.mark,h⟩
      · simp only [Option.some.injEq] at h
        exact ⟨.fieldChain c f a.mark,h⟩
      · simp only [Option.some.injEq] at h
        exact ⟨.fieldChain c f a.mark,h⟩
      · split at h
        · simp only [Option.some.injEq] at h
          exact ⟨.fieldExact c f a.mark,h⟩
        · simp only [Option.some.injEq] at h
          exact ⟨.fieldChain c f a.mark,h⟩

theorem request_projector_no_any (slot : Slot) (a : Leaf) (j : PFact)
    (h : project slot a = some j) : j.kind ≠ .any :=
  legalAP_no_any j (request_projector_legal slot a j h)

theorem direct_class_legal (a : Leaf) (j : PFact) (h : directClass a = some j) : LegalAP j := by
  cases a with
  | mk point tail mark e d =>
    cases point <;> cases tail <;> simp [directClass] at h
    rename_i c
    exact ⟨.classExact c mark,h⟩

theorem class_projector_legal (c : Nat) (a : Leaf) (j : PFact)
    (h : classAnswer c a = some j) : LegalAP j := by
  cases hp : a.point <;> simp only [classAnswer,hp] at h
  · cases h
  · split at h
    · simp only [Option.some.injEq] at h
      exact ⟨.classChain c a.mark,h⟩
    · cases h
  · split at h
    · split at h
      · simp only [Option.some.injEq] at h
        exact ⟨.classExact c a.mark,h⟩
      · simp only [Option.some.injEq] at h
        exact ⟨.classChain c a.mark,h⟩
    · cases h
  · cases h

theorem class_projector_no_any (c : Nat) (a : Leaf) (j : PFact)
    (h : classAnswer c a = some j) : j.kind ≠ .any :=
  legalAP_no_any j (class_projector_legal c a j h)

/-! ## Supply conditions and exhaustive run-1 factory inventory. -/

inductive Cause where
  | zeroRoot
  | zeroInput
  | normalSlot (slot : Slot) (selectedAndSupplied : Bool)
  | staticSummarySeed
  | request (slot : Slot) (supplier : Leaf) (admitted : Bool)
  | classMetadata (cls : Nat) (supplier : Leaf) (needed : Bool)
  | exactClassInput (supplier : Leaf)
  deriving DecidableEq, Repr

def optional : Option PFact → List PFact
  | none => []
  | some i => [i]

/-- Legal shape is only a NECESSARY condition. An admitted request and a matching
    concrete supplier are required for a normal answer. Direct class handling
    remains independent of a new class/root request key. -/
def emit : Cause → List PFact
  | .zeroRoot | .zeroInput => [zeroFact]
  | .normalSlot slot supplied => if supplied then [slot.initial] else []
  | .staticSummarySeed => [specialRoot]
  | .request slot supplier admitted => if admitted then optional (project slot supplier) else []
  | .classMetadata cls supplier needed => if needed then optional (classAnswer cls supplier) else []
  | .exactClassInput supplier => optional (directClass supplier)

theorem optional_legal (f : Option PFact) (h : ∀ j, f = some j → LegalAP j)
    (j : PFact) (hj : j ∈ optional f) : LegalAP j := by
  cases f with
  | none => cases hj
  | some i =>
    have eq : j = i := by simpa [optional] using hj
    exact eq ▸ h i rfl

theorem every_emitted_initial_legal (cause : Cause) (j : PFact) (h : j ∈ emit cause) : LegalAP j := by
  cases cause with
  | zeroRoot | zeroInput =>
    have eq : j = zeroFact := by simpa [emit] using h
    exact eq ▸ root_zero_legal
  | normalSlot slot supplied =>
    cases supplied
    · simp [emit] at h
    · have eq : j = slot.initial := by simpa [emit] using h
      exact eq ▸ slot_initial_legal slot
  | staticSummarySeed =>
    have eq : j = specialRoot := by simpa [emit] using h
    exact eq ▸ special_root_legal
  | request slot supplier admitted =>
    cases admitted
    · simp [emit] at h
    · exact optional_legal _ (request_projector_legal slot supplier) j h
  | classMetadata cls supplier needed =>
    cases needed
    · simp [emit] at h
    · exact optional_legal _ (class_projector_legal cls supplier) j h
  | exactClassInput supplier => exact optional_legal _ (direct_class_legal supplier) j h

theorem every_emitted_no_any (cause : Cause) (j : PFact) (h : j ∈ emit cause) :
    j.kind ≠ .any := legalAP_no_any j (every_emitted_initial_legal cause j h)

/-- Additive admission view: every matching concrete supplier starts the normal
    abstract slot even when its concrete mark is not requested. The requested
    concrete start is an addition, never a replacement for that abstract slot. -/
def supplierStarts (slot : Slot) (a : Leaf) (admitted : Bool) : List PFact :=
  if slotMatches slot a then slot.initial :: emit (.request slot a admitted) else []

theorem supplier_starts_legal (slot : Slot) (a : Leaf) (admitted : Bool)
    (j : PFact) (h : j ∈ supplierStarts slot a admitted) : LegalAP j := by
  unfold supplierStarts at h
  split at h
  · simp only [List.mem_cons] at h
    rcases h with rfl | h
    · exact slot_initial_legal slot
    · exact every_emitted_initial_legal _ j h
  · cases h

theorem supplier_starts_no_any (slot : Slot) (a : Leaf) (admitted : Bool)
    (j : PFact) (h : j ∈ supplierStarts slot a admitted) : j.kind ≠ .any :=
  legalAP_no_any j (supplier_starts_legal slot a admitted j h)

theorem abstract_start_additive (slot : Slot) (a : Leaf) (admitted : Bool)
    (matching : slotMatches slot a = true) :
    slot.initial ∈ supplierStarts slot a admitted := by
  simp [supplierStarts,matching]

theorem no_request_abstract_only (slot : Slot) (a : Leaf)
    (matching : slotMatches slot a = true) :
    supplierStarts slot a false = [slot.initial] := by
  simp [supplierStarts,matching,emit]

/-- ANY and must incoming C.f(T) share this exact selected emission law.
    No request: only C.f*(*) starts. Request: that start plus C.f*(T).
    This theorem does not create a class request key or bypass admission. -/
theorem nonexact_field_additive (c f t : Nat) (k : Tail) (hk : k ≠ .exact)
    (e : Excl) (d : Bool) :
    supplierStarts (.field c f) ⟨.field c f,k,t,e,d⟩ false =
      [(Slot.field c f).initial] ∧
    supplierStarts (.field c f) ⟨.field c f,k,t,e,d⟩ true =
      [(Slot.field c f).initial,concrete (.field c f) (.star .empty) t] := by
  cases k <;> simp_all [supplierStarts,slotMatches,emit,project,optional]

/-- Admission reads the exact full owner/original-slot/actual supplier mark
    membership. There is no field/class-wide or caller-erased request key. -/
def requestedStarts (requests : List Question) (owner : Owner) (slot : Slot)
    (a : Leaf) : List PFact :=
  supplierStarts slot a (decide (⟨owner,slot,a.mark⟩ ∈ requests))

theorem nonexact_field_without_request (requests : List Question) (owner : Owner)
    (c f t : Nat) (k : Tail) (hk : k ≠ .exact) (e : Excl) (d : Bool)
    (absent : ⟨owner,Slot.field c f,t⟩ ∉ requests) :
    requestedStarts requests owner (.field c f) ⟨.field c f,k,t,e,d⟩ =
      [(Slot.field c f).initial] := by
  simp only [requestedStarts,absent,decide_false]
  exact (nonexact_field_additive c f t k hk e d).1

theorem nonexact_field_with_request (requests : List Question) (owner : Owner)
    (c f t : Nat) (k : Tail) (hk : k ≠ .exact) (e : Excl) (d : Bool)
    (admitted : ⟨owner,Slot.field c f,t⟩ ∈ requests) :
    requestedStarts requests owner (.field c f) ⟨.field c f,k,t,e,d⟩ =
      [(Slot.field c f).initial,concrete (.field c f) (.star .empty) t] := by
  simp only [requestedStarts,admitted,decide_true]
  exact (nonexact_field_additive c f t k hk e d).2

theorem unadmitted_request_emits_nothing (slot : Slot) (a : Leaf) :
    emit (.request slot a false) = [] := rfl

theorem unmatched_supplier_emits_nothing (slot : Slot) (a : Leaf)
    (h : slotMatches slot a = false) : emit (.request slot a true) = [] := by
  simp [emit, project, h, optional]

/-- An allowed shape is not sufficient to start: both admission and an actual
    successful concrete-supplier projection are necessary and sufficient here. -/
theorem request_emission_exact (slot : Slot) (a : Leaf) (admitted : Bool) (j : PFact) :
    j ∈ emit (.request slot a admitted) ↔ admitted = true ∧ project slot a = some j := by
  cases admitted <;> cases hp : project slot a <;> simp [emit,optional,hp,eq_comm]

theorem request_requires_matching_concrete_supplier (slot : Slot) (a : Leaf)
    (admitted : Bool) (j : PFact) (h : j ∈ emit (.request slot a admitted)) :
    admitted = true ∧ slotMatches slot a = true ∧ j.mark = .conc a.mark := by
  have actual := (request_emission_exact slot a admitted j).mp h
  have matchProof := project_exists_iff slot a
  rw [actual.2] at matchProof
  exact ⟨actual.1, matchProof.symm, projector_keeps_mark slot a j actual.2⟩

inductive Incoming where
  | zero
  | flow (facts : ApSpec.Run1Requests.Flow)
  | taint (facts : Leaf)
  deriving DecidableEq, Repr

def incomingAnswers (slot : Slot) (admitted : Bool) : Incoming → List PFact
  | .taint a => emit (.request slot a admitted)
  | _ => []

theorem FLOW_never_answers (slot : Slot) (admitted : Bool) (facts : ApSpec.Run1Requests.Flow) :
    incomingAnswers slot admitted (.flow facts) = [] := rfl

theorem incoming_answers_legal (slot : Slot) (admitted : Bool) (input : Incoming)
    (j : PFact) (h : j ∈ incomingAnswers slot admitted input) : LegalAP j := by
  cases input with
  | zero | flow => cases h
  | taint a => exact every_emitted_initial_legal (.request slot a admitted) j h

/-- The impossible exact empty-static-root case is never silently converted
    into a new legal initial. Its impossibility is needed for COVERAGE, not for
    manufacturing legality by dropping or widening that input. -/
theorem exact_empty_root_has_no_answer_factory (slot : Slot) (c t : Nat)
    (e : Excl) (d : Bool) :
    project slot ⟨.root,.exact,t,e,d⟩ = none ∧
    classAnswer c ⟨.root,.exact,t,e,d⟩ = none ∧
    directClass ⟨.root,.exact,t,e,d⟩ = none := by
  cases slot <;> simp [project,slotMatches,broad,classAnswer,directClass]

/-- Every grammar case has an executable distinguishing supply witness. This
    does not claim every legal shape is emitted for every method/input. -/
def witness : Shape → Cause
  | .zero => .zeroRoot
  | .abstractBase b => .normalSlot (.base b) true
  | .abstractRoot => .staticSummarySeed
  | .abstractField c f => .normalSlot (.field c f) true
  | .baseExact b t => .request (.base b) ⟨.local b [],.exact,t,.empty,false⟩ true
  | .baseChain b t => .request (.base b) ⟨.local b [],.any,t,.empty,true⟩ true
  | .fieldExact c f t => .request (.field c f) ⟨.field c f,.exact,t,.empty,false⟩ true
  | .fieldChain c f t => .request (.field c f) ⟨.root,.any,t,.empty,true⟩ true
  | .classExact c t => .exactClassInput ⟨.cls c,.exact,t,.empty,false⟩
  | .classChain c t => .classMetadata c ⟨.root,.any,t,.empty,true⟩ true

theorem every_shape_has_supply_witness (shape : Shape) : shape.toAP ∈ emit (witness shape) := by
  cases shape <;> simp [witness, emit, optional, project, classAnswer, directClass,
    slotMatches, broad, Shape.toAP, RawInitial.toAP, encode, concrete,
    Point.base, Point.path, Place.base, Place.path, Slot.initial, specialRoot, zeroFact, zeroMark]

/-! ## Paired start-fact shape and layer in the existing AP model. -/

def abstractMark : Shape → Bool
  | .abstractBase _ | .abstractRoot | .abstractField _ _ => true
  | _ => false

def demandStart : Shape → Bool
  | .baseChain _ _ | .fieldChain _ _ _ | .classChain _ _ => true
  | _ => false

def startKind (s : Shape) : Kind := match s with
  | .abstractBase _ | .abstractRoot | .abstractField _ _ => .star .empty
  | .baseChain _ _ | .fieldChain _ _ _ | .classChain _ _ => .any
  | _ => .exact

theorem started_layer (shape : Shape) :
    (startFact shape.toAP).demand = demandStart shape := by cases shape <;> rfl

theorem started_zero_is_reach : startFact Shape.zero.toAP = ⟨zeroFact,false⟩ := rfl

theorem started_tail (shape : Shape) :
    (startFact shape.toAP).fact.kind = startKind shape := by cases shape <;> rfl

theorem started_geometry (shape : Shape) :
    (startFact shape.toAP).fact.base = shape.toAP.base ∧
      (startFact shape.toAP).fact.path = shape.toAP.path ∧
      (startFact shape.toAP).fact.mark = shape.toAP.mark := by cases shape <;> exact ⟨rfl,rfl,rfl⟩

theorem started_FLOW_exactly_abstract (shape : Shape) :
    (startFact shape.toAP).fact.mark = .star ↔ abstractMark shape = true := by
  cases shape <;> simp [startFact, Shape.toAP, RawInitial.toAP, encode, abstractMark]

/-- Current ANY facts remain legal even though every run-1 initial is EXACT
    or STAR. No run-1 initial constructor returns ANY or ANY_TAINT. -/
theorem concrete_any_is_not_an_ordinary_base_initial (b t : Nat) :
    ¬ Legal ⟨.at (.local b []),.any,.conc t,.empty⟩ := by
  rintro ⟨s,hs⟩
  cases s <;> simp [encode] at hs

namespace Certificate
def samples : List Shape := [.zero,.abstractBase 2,.abstractRoot,.abstractField 3 2,
  .baseExact 2 8,.baseChain 2 8,.fieldExact 3 2 8,.fieldChain 3 2 8,
  .classExact 3 8,.classChain 3 0]

def checks : Bool :=
  !(check ⟨.at .root,.exact,.conc 8,.empty⟩) &&
  !(check ⟨.at (.cls 3),.star,.star,.empty⟩) &&
  !(check ⟨.at (.local 2 []),.any,.conc 8,.empty⟩) &&
  !(check ⟨.at (.field 3 2),.must,.conc 8,.empty⟩) &&
  check (encode (.classChain 3 0)) && check (encode (.fieldChain 3 2 8)) &&
  !(check ⟨.at (.field 3 2),.any,.conc 8,.empty⟩) &&
  !(check ⟨.at (.cls 3),.any,.conc 8,.empty⟩) &&
  decide (supplierStarts (.field 3 2) ⟨.field 3 2,.any,8,.empty,false⟩ false =
    [(Slot.field 3 2).initial]) &&
  decide (supplierStarts (.field 3 2) ⟨.field 3 2,.must,8,.empty,false⟩ true =
    [(Slot.field 3 2).initial,concrete (.field 3 2) (.star .empty) 8]) &&
  decide (classAnswer 3 ⟨.cls 3,.must,8,.empty,false⟩ =
    some (concrete (.cls 3) (.star .empty) 8)) &&
  decide ((startFact (Shape.baseChain 2 8).toAP).demand = true) &&
  samples.all (fun s => check (encode s) && decide (s.toAP ∈ emit (witness s)) &&
    decide ((startFact s.toAP).demand = demandStart s))
theorem checks_pass : checks = true := by decide +kernel
end Certificate

#print axioms classification_exact
#print axioms legal_empty_E
#print axioms legal_no_any
#print axioms legalAP_no_any
#print axioms every_emitted_no_any
#print axioms nonexact_field_additive
#print axioms legal_no_must
#print axioms request_projector_legal
#print axioms class_projector_legal
#print axioms every_emitted_initial_legal
#print axioms every_shape_has_supply_witness
#print axioms request_emission_exact
#print axioms request_requires_matching_concrete_supplier
#print axioms started_layer
#print axioms started_FLOW_exactly_abstract

end ApSpec.Run1InitialShapes
