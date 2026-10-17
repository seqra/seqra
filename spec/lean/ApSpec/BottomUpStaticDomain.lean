/-
  Constructive scalar domain for the proposed bottom-up run-1 statics.

  This file checks the NINE selected initial families (no special S-root
  initial), supplied-input projection WITHOUT F, original-slot mark requests,
  sticky early static FLOW demotion, and the existing strong preservation
  kernel. The default theorem uses the existing ordinary scalar AP application
  with independent layers. Identity compaction below has a full-payload guard.

  Scope: scalar members and canonical incoming field bases. This is NOT a
  proof of packed-trie splitting, frontend coordinate completeness, static
  transparency/AtCallee reachability, scheduling, source/ND protocol equality,
  persistence, or the whole bottom-up/reference fixed-point theorem. In
  particular, no activation-to-supplier implication is assumed here.
-/
import ApSpec.Run1Requests
import ApSpec.Run1StrongPreservation

namespace ApSpec.BottomUpStaticDomain
open ApSpec
set_option autoImplicit false


/-! ## Closed initial vocabulary, separate from current facts. -/

inductive InitialTail where
  | exact | star | any | must
  deriving DecidableEq, Repr

inductive Place where
  | zero
  | local (base : Nat) (path : List Acc)
  | root
  | cls (cls : Nat)
  | field (cls field : Nat)
  | deep (cls field next : Nat) (rest : List Acc)
  deriving DecidableEq, Repr

structure RawInitial where
  place : Place
  tail : InitialTail
  mark : MarkA
  exclusion : Excl
  deriving DecidableEq, Repr

inductive Initial where
  | zero
  | abstractBase (base : Nat)
  | abstractField (cls field : Nat)
  | baseExact (base mark : Nat)
  | baseStar (base mark : Nat)
  | fieldExact (cls field mark : Nat)
  | fieldStar (cls field mark : Nat)
  | classExact (cls mark : Nat)
  | classStar (cls mark : Nat)
  deriving DecidableEq, Repr

def encode : Initial → RawInitial
  | .zero => ⟨.zero,.exact,.conc 0,.empty⟩
  | .abstractBase b => ⟨.local b [],.star,.star,.empty⟩
  | .abstractField c f => ⟨.field c f,.star,.star,.empty⟩
  | .baseExact b t => ⟨.local b [],.exact,.conc t,.empty⟩
  | .baseStar b t => ⟨.local b [],.star,.conc t,.empty⟩
  | .fieldExact c f t => ⟨.field c f,.exact,.conc t,.empty⟩
  | .fieldStar c f t => ⟨.field c f,.star,.conc t,.empty⟩
  | .classExact c t => ⟨.cls c,.exact,.conc t,.empty⟩
  | .classStar c t => ⟨.cls c,.star,.conc t,.empty⟩

/-- The parser returns the actual constructor and its encoding proof. -/
def classify : (i : RawInitial) → Option {s : Initial // encode s = i}
  | ⟨.zero,.exact,.conc 0,.set []⟩ => some ⟨.zero,rfl⟩
  | ⟨.local b [],.star,.star,.set []⟩ => some ⟨.abstractBase b,rfl⟩
  | ⟨.field c f,.star,.star,.set []⟩ => some ⟨.abstractField c f,rfl⟩
  | ⟨.local b [],.exact,.conc t,.set []⟩ => some ⟨.baseExact b t,rfl⟩
  | ⟨.local b [],.star,.conc t,.set []⟩ => some ⟨.baseStar b t,rfl⟩
  | ⟨.field c f,.exact,.conc t,.set []⟩ => some ⟨.fieldExact c f t,rfl⟩
  | ⟨.field c f,.star,.conc t,.set []⟩ => some ⟨.fieldStar c f t,rfl⟩
  | ⟨.cls c,.exact,.conc t,.set []⟩ => some ⟨.classExact c t,rfl⟩
  | ⟨.cls c,.star,.conc t,.set []⟩ => some ⟨.classStar c t,rfl⟩
  | _ => none

def Legal (i : RawInitial) : Prop := ∃ s, encode s = i

theorem classify_encode (s : Initial) : (classify (encode s)).isSome = true := by
  cases s <;> rfl

theorem classifier_exact (i : RawInitial) : (classify i).isSome = true ↔ Legal i := by
  constructor
  · intro h
    cases hc : classify i with
    | none => simp [hc] at h
    | some witness => exact ⟨witness.val,witness.property⟩
  · rintro ⟨s,rfl⟩
    exact classify_encode s

theorem no_any_initial (i : RawInitial) (h : Legal i) : i.tail ≠ .any := by
  obtain ⟨s,rfl⟩ := h
  cases s <;> simp [encode]

theorem no_must_initial (i : RawInitial) (h : Legal i) : i.tail ≠ .must := by
  obtain ⟨s,rfl⟩ := h
  cases s <;> simp [encode]

theorem initial_E_empty (i : RawInitial) (h : Legal i) : i.exclusion = .empty := by
  obtain ⟨s,rfl⟩ := h
  cases s <;> rfl

theorem initial_mx_empty (i : RawInitial) (h : Legal i) (mx : List Mark) :
    i.mark ≠ .starEx mx := by
  obtain ⟨s,rfl⟩ := h
  cases s <;> simp [encode]

theorem no_static_root_initial (tail : InitialTail) (mark : MarkA) (e : Excl) :
    ¬ Legal ⟨.root,tail,mark,e⟩ := by
  rintro ⟨s,h⟩
  cases s <;> simp [encode] at h

theorem no_abstract_class (c : Nat) (tail : InitialTail) (e : Excl) :
    ¬ Legal ⟨.cls c,tail,.star,e⟩ := by
  rintro ⟨s,h⟩
  cases s <;> simp [encode] at h

theorem no_nonstatic_field_initial (b x : Nat) (rest : List Acc)
    (tail : InitialTail) (mark : MarkA) (e : Excl) :
    ¬ Legal ⟨.local b (x::rest),tail,mark,e⟩ := by
  rintro ⟨s,h⟩
  cases s <;> simp [encode] at h

theorem no_deep_initial (c f x : Nat) (rest : List Acc)
    (tail : InitialTail) (mark : MarkA) (e : Excl) :
    ¬ Legal ⟨.deep c f x rest,tail,mark,e⟩ := by
  rintro ⟨s,h⟩
  cases s <;> simp [encode] at h

theorem zero_canonical (i : RawInitial) (h : Legal i) (hz : i.place = .zero) :
    i = encode .zero := by
  obtain ⟨s,rfl⟩ := h
  cases s <;> simp_all [encode]

def Place.base : Place → Base
  | .zero => 0
  | .local b _ => b + 2
  | _ => 1

def Place.path : Place → List Acc
  | .zero | .root => []
  | .local _ p => p
  | .cls c => [2*c]
  | .field c f => [2*c,2*f+1]
  | .deep c f x rest => [2*c,2*f+1,x] ++ rest

def RawInitial.toAP (i : RawInitial) : PFact :=
  ⟨i.place.base,i.place.path,
    match i.tail with
    | .exact => .exact
    | .star => .star i.exclusion
    | .any | .must => .any,
    i.mark⟩

def Initial.toAP (i : Initial) : PFact := (encode i).toAP
def LegalAP (p : PFact) : Prop := ∃ i : Initial, i.toAP = p

theorem legal_AP_no_any (p : PFact) (h : LegalAP p) : p.kind ≠ .any := by
  obtain ⟨i,rfl⟩ := h
  cases i <;> simp [Initial.toAP,RawInitial.toAP,encode]

def abstractInitial : Run1Requests.Slot → Initial
  | .base b => .abstractBase b
  | .field c f => .abstractField c f

theorem abstract_initial_AP (slot : Run1Requests.Slot) :
    (abstractInitial slot).toAP = slot.initial := by cases slot <;> rfl

/-- The existing startFact defines concrete STAR starts. Only abstract static
    starts get the new blanket FLOW demotion; concrete exact starts stay N. -/
def startInitial (initial : Initial) : Run1DefaultSummaryApplication.Layered :=
  match initial with
  | .abstractField _ _ => ⟨initial.toAP,true⟩
  | _ => let started := startFact initial.toAP; ⟨started.fact,started.demand⟩

theorem abstract_static_start_D (c f : Nat) :
    startInitial (.abstractField c f) = ⟨(Run1Requests.Slot.field c f).initial,true⟩ := rfl

theorem abstract_nonstatic_start_N (b : Nat) :
    startInitial (.abstractBase b) = ⟨(Run1Requests.Slot.base b).initial,false⟩ := rfl

theorem concrete_exact_not_blanket_demoted (c f t : Nat) :
    startInitial (.fieldExact c f t) = ⟨(Initial.fieldExact c f t).toAP,false⟩ := rfl

theorem concrete_STAR_start_ANY_D (c f t : Nat) :
    startInitial (.fieldStar c f t) =
      ⟨{(Initial.fieldStar c f t).toAP with kind := .any},true⟩ := rfl

/-! ## Supplied geometry and concrete answers, without a field-request gate. -/

/-- No F/LocalRead argument: every matching supplied slot gets its abstract
    basis. Optimized local-reader activation is a separate protocol obligation. -/
def projectAbstract (slot : Run1Requests.Slot) (a : Run1Requests.Leaf) : Option PFact :=
  if Run1Requests.slotMatches slot a then some slot.initial else none

def projectConcrete (slot : Run1Requests.Slot) (requests : List Mark) (a : Run1Requests.Leaf) : Option PFact :=
  if a.mark ∈ requests then Run1Requests.project slot a else none

theorem abstract_project_exists (slot : Run1Requests.Slot) (a : Run1Requests.Leaf) :
    (projectAbstract slot a).isSome = Run1Requests.slotMatches slot a := by
  cases h : Run1Requests.slotMatches slot a <;> simp [projectAbstract,h]

theorem abstract_project_legal (slot : Run1Requests.Slot) (a : Run1Requests.Leaf) (out : PFact)
    (h : projectAbstract slot a = some out) : LegalAP out := by
  unfold projectAbstract at h
  split at h
  · cases h
    exact ⟨abstractInitial slot,abstract_initial_AP slot⟩
  · cases h

theorem concrete_project_legal (slot : Run1Requests.Slot) (a : Run1Requests.Leaf) (out : PFact)
    (h : Run1Requests.project slot a = some out) : LegalAP out := by
  unfold Run1Requests.project at h
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
            exact ⟨.baseStar b a.mark,h⟩
        · simp only [Option.some.injEq] at h
          exact ⟨.baseStar b a.mark,h⟩
    | field c f =>
        cases hp : a.point <;> simp only [hp] at h
        · simp only [Option.some.injEq] at h
          exact ⟨.fieldStar c f a.mark,h⟩
        · simp only [Option.some.injEq] at h
          exact ⟨.fieldStar c f a.mark,h⟩
        · simp only [Option.some.injEq] at h
          exact ⟨.fieldStar c f a.mark,h⟩
        · split at h
          · simp only [Option.some.injEq] at h
            exact ⟨.fieldExact c f a.mark,h⟩
          · simp only [Option.some.injEq] at h
            exact ⟨.fieldStar c f a.mark,h⟩

theorem concrete_answer_requires_actual_and_request (slot : Run1Requests.Slot) (requests : List Mark)
    (a : Run1Requests.Leaf) (out : PFact) (h : projectConcrete slot requests a = some out) :
    a.mark ∈ requests ∧ out.mark = .conc a.mark ∧ LegalAP out := by
  unfold projectConcrete at h
  split at h
  · rename_i requested
    exact ⟨requested,Run1Requests.projector_keeps_mark slot a out h,concrete_project_legal slot a out h⟩
  · cases h

theorem no_request_no_concrete (slot : Run1Requests.Slot) (a : Run1Requests.Leaf) :
    projectConcrete slot [] a = none := by simp [projectConcrete]

theorem projection_ignores_E (slot : Run1Requests.Slot) (requests : List Mark) (a : Run1Requests.Leaf) (e : Excl) :
    projectConcrete slot requests {a with fieldExclusion := e} = projectConcrete slot requests a := by
  simp only [projectConcrete,Run1Requests.projector_ignores_field_E]

theorem exact_class_not_field (c f t : Nat) :
    projectAbstract (.field c f) ⟨.cls c,.exact,t,.empty,false⟩ = none := by
  simp [projectAbstract,Run1Requests.slotMatches,Run1Requests.broad]

theorem class_answer_legal (c : Nat) (a : Run1Requests.Leaf) (out : PFact)
    (h : Run1Requests.classAnswer c a = some out) : LegalAP out := by
  cases hp : a.point <;> simp only [Run1Requests.classAnswer,hp] at h
  · cases h
  · split at h
    · simp only [Option.some.injEq] at h
      exact ⟨.classStar c a.mark,h⟩
    · cases h
  · split at h
    · split at h
      · simp only [Option.some.injEq] at h
        exact ⟨.classExact c a.mark,h⟩
      · simp only [Option.some.injEq] at h
        exact ⟨.classStar c a.mark,h⟩
    · cases h
  · cases h

/-! ## Sticky independent FLOW layer and actual original request origin. -/

def staticSlot : Run1Requests.Slot → Bool
  | .base _ => false
  | .field _ _ => true

structure FlowState where
  original : Run1Requests.Slot
  current : Run1DefaultSummaryApplication.Layered
  deriving DecidableEq, Repr

/-- Only FLOW calls this function; concrete TAINT is a separate typed path. -/
def demote (input : FlowState) : FlowState :=
  ⟨input.original,⟨input.current.fact,
    input.current.demand || staticSlot input.original || (input.current.fact.base == 1)⟩⟩

def advance (input : FlowState) (next : PFact) (operationDemand : Bool) : FlowState :=
  demote ⟨input.original,⟨next,input.current.demand || operationDemand⟩⟩

theorem demotion_keeps_full_payload (input : FlowState) :
    (demote input).original = input.original ∧ (demote input).current.fact = input.current.fact :=
  ⟨rfl,rfl⟩

theorem demotion_sticky (input : FlowState) (h : input.current.demand = true) :
    (demote input).current.demand = true := by simp [demote,h]

theorem static_origin_demand (c f : Nat) (payload : Run1DefaultSummaryApplication.Layered) :
    (demote ⟨.field c f,payload⟩).current.demand = true := by simp [demote,staticSlot]

theorem static_current_demand (input : FlowState) (h : input.current.fact.base = 1) :
    (demote input).current.demand = true := by
  change (input.current.demand || staticSlot input.original || (input.current.fact.base == 1)) = true
  rw [h]
  cases input.current.demand <;> cases staticSlot input.original <;> rfl

theorem advance_sticky (input : FlowState) (next : PFact) (operationDemand : Bool)
    (h : input.current.demand = true) :
    (advance input next operationDemand).current.demand = true := by simp [advance,demote,h]

theorem static_to_local_stays_demand (input : FlowState) (localPayload : PFact)
    (h : input.current.fact.base = 1) :
    (advance (demote input) localPayload false).current.demand = true :=
  advance_sticky _ _ _ (static_current_demand input h)

def abstractOrigin : Initial → Option Run1Requests.Slot
  | .abstractBase b => some (.base b)
  | .abstractField c f => some (.field c f)
  | _ => none

/-- The current payload is deliberately not a request-key dimension. Actual
    operation eligibility is checked before calling the collector. -/
def requestAt (owner : Run1Requests.Owner) (origin : Initial) (_current : PFact) (mark : Mark) :
    Option Run1Requests.Question := (abstractOrigin origin).map (fun slot => ⟨owner,slot,mark⟩)

theorem request_original_field (owner : Run1Requests.Owner) (c f : Nat) (current : PFact) (mark : Mark) :
    requestAt owner (.abstractField c f) current mark = some ⟨owner,.field c f,mark⟩ := rfl

theorem request_ignores_E_mx (owner : Run1Requests.Owner) (origin : Initial)
    (current other : PFact) (mark : Mark) :
    requestAt owner origin current mark = requestAt owner origin other mark := rfl

theorem concrete_origins_do_not_request (owner : Run1Requests.Owner) (origin : Initial)
    (current : PFact) (mark : Mark) (h : abstractOrigin origin = none) :
    requestAt owner origin current mark = none := by simp [requestAt,h]

/-! ## Strong keep/kill and pre-store value branches, not weak preservation. -/

theorem overwritten_demand_is_killed (target : Run1StrongPreservation.FieldTarget) (suffix : List Acc)
    (kind : Kind) (mark : MarkA) (origin : Initial) :
    Run1StrongPreservation.preserve .definite target
      ⟨origin,⟨⟨target.base,target.path ++ suffix,kind,mark⟩,true⟩⟩ = none :=
  Run1StrongPreservation.definite_kill target _ (Run1StrongPreservation.at_or_below_is_killed target suffix kind mark)

theorem disjoint_write_unchanged (target : Run1StrongPreservation.FieldTarget) (input : Run1StrongPreservation.Edge Initial)
    (apart : relate target.path input.current.fact.path = .apart) :
    Run1StrongPreservation.preserve .definite target input = some input :=
  Run1StrongPreservation.identity_unchanged target input (Run1StrongPreservation.disjoint_paths_unchanged target _ apart)

theorem self_assignment_uses_pre_store_value (target : Run1StrongPreservation.FieldTarget) (input : Run1StrongPreservation.Edge Initial)
    (killed : Run1StrongPreservation.classify target input.current.fact = .kill) :
    Run1StrongPreservation.write .definite target input [input] = [input] := by
  simp [Run1StrongPreservation.write,Run1StrongPreservation.definite_kill target input killed]

theorem survivor_full_payload (target : Run1StrongPreservation.FieldTarget) (input output : Run1StrongPreservation.Edge Initial)
    (kept : Run1StrongPreservation.preserve .definite target input = some output) :
    output.original = input.original ∧ output.current.fact = input.current.fact :=
  Run1StrongPreservation.preservation_full_payload .definite target input output kept

/-- Selected scalar mark cleaner: an actual matched field changes mx and
    emits interest even when the incoming mx already contains that mark. -/
def excludeMark (mark : MarkA) (t : Mark) : MarkA := match mark with
  | .star => .starEx [t]
  | .starEx xs => .starEx (if t ∈ xs then xs else xs ++ [t])
  | .conc v => .conc v

def cleanMatchedFlow (owner : Run1Requests.Owner) (origin : Initial) (current : Run1DefaultSummaryApplication.Layered) (t : Mark) :
    Run1DefaultSummaryApplication.Layered × Option Run1Requests.Question :=
  (⟨{current.fact with mark := excludeMark current.fact.mark t},current.demand⟩,
    requestAt owner origin current.fact t)

theorem clean_preserves_geometry_layer (owner : Run1Requests.Owner) (origin : Initial)
    (current : Run1DefaultSummaryApplication.Layered) (t : Mark) :
    (cleanMatchedFlow owner origin current t).1.fact.base = current.fact.base ∧
    (cleanMatchedFlow owner origin current t).1.fact.path = current.fact.path ∧
    (cleanMatchedFlow owner origin current t).1.fact.kind = current.fact.kind ∧
    (cleanMatchedFlow owner origin current t).1.demand = current.demand :=
  ⟨rfl,rfl,rfl,rfl⟩

theorem already_excluded_still_requests (owner : Run1Requests.Owner) (c f : Nat)
    (payload : PFact) (demand : Bool) (t : Mark) :
    (cleanMatchedFlow owner (.abstractField c f)
      ⟨{payload with mark := .starEx [t]},demand⟩ t).2 =
      some ⟨owner,.field c f,t⟩ := rfl

theorem cleaner_excludes_selected_mark (t : Mark) : ¬ (excludeMark .star t).admits t := by
  simp [excludeMark,MarkA.admits,memB]

theorem cleaner_keeps_other_mark (t u : Mark) (different : u ≠ t) :
    (excludeMark .star t).admits u := by
  cases eq : Nat.beq u t with
  | false => simp [excludeMark,MarkA.admits,memB,eq]
  | true => exact False.elim (different (Nat.eq_of_beq_eq_true eq))

/-! ## Unconditional ordinary DEMAND root default and safe compaction. -/

/-- No normal-exit predicate/bit is accepted. This is a summary, not an initial. -/
def defaultSummary (_owner : Run1Requests.Owner) : Run1DefaultSummaryApplication.Layered := ⟨Run1DefaultSummaryApplication.rootSTAR 1,true⟩

def applyDefault (actual : Run1DefaultSummaryApplication.Layered) : Run1DefaultSummaryApplication.LayeredResults :=
  Run1DefaultSummaryApplication.withEdgeLayers actual.demand true
    (applySummary ⟨actual.fact,false⟩ (Run1DefaultSummaryApplication.rootSTAR 1) ⟨Run1DefaultSummaryApplication.rootSTAR 1,false⟩)

theorem default_full_payload (actual : Run1DefaultSummaryApplication.Layered) (static : actual.fact.base = 1)
    (canonical : Run1DefaultSummaryApplication.CanonicalPayload actual.fact) :
    applyDefault actual = ⟨[⟨actual.fact,true⟩],[]⟩ := by
  unfold applyDefault
  rw [← static]
  simpa using Run1DefaultSummaryApplication.ordinary_root_STAR_layer_only actual true canonical

structure FieldSummary where
  premise : Initial
  conclusion : Run1DefaultSummaryApplication.Layered
  deriving DecidableEq, Repr

/-- A sufficient full semantic guard on a canonical supplied field basis.
    No path-only test can replace the complete payload/layer equality. -/
def compactable (edge : FieldSummary) : Bool := match edge.premise with
  | .abstractField c f =>
      edge.conclusion == Run1DefaultSummaryApplication.Layered.mk (Run1Requests.Slot.field c f).initial true
  | _ => false

def compact (edges : List FieldSummary) : List FieldSummary :=
  edges.filter (fun edge => !compactable edge)

theorem compaction_preserves_nonidentity (edge : FieldSummary) (edges : List FieldSummary)
    (h : compactable edge = false) : edge ∈ compact edges ↔ edge ∈ edges := by
  simp [compact,h]

theorem compaction_idempotent (edges : List FieldSummary) : compact (compact edges) = compact edges := by
  simp [compact,List.filter_filter]

theorem compactable_exact (edge : FieldSummary) (h : compactable edge = true) :
    ∃ c f, edge.premise = .abstractField c f ∧
      edge.conclusion = ⟨(Run1Requests.Slot.field c f).initial,true⟩ := by
  cases hp : edge.premise <;> simp [compactable,hp] at h
  rename_i c f
  exact ⟨c,f,rfl,h⟩

/-- A discarded identity is actually produced by the existing ordinary root
    application on that canonical field basis. No general bottom-up theorem
    or claimed equality for arbitrary broad suppliers is assumed. -/
theorem discarded_identity_supplied_by_default (edge : FieldSummary)
    (h : compactable edge = true) :
    applyDefault ⟨edge.premise.toAP,true⟩ = ⟨[edge.conclusion],[]⟩ := by
  obtain ⟨c,f,hp,hc⟩ := compactable_exact edge h
  rw [hp,hc]
  exact default_full_payload _ rfl (Run1DefaultSummaryApplication.abstract_STAR_payload _ _ _)

namespace Controls
def owner : Run1Requests.Owner := ⟨7,3,[11]⟩
def fieldInitial : Initial := .abstractField 2 3
def field : PFact := (Run1Requests.Slot.field 2 3).initial
def plain : FieldSummary := ⟨fieldInitial,⟨field,true⟩⟩
def cleaned : FieldSummary :=
  ⟨fieldInitial,⟨{field with mark := .starEx [8]},true⟩⟩
def movedTail : FieldSummary :=
  ⟨fieldInitial,⟨{field with kind := .any},true⟩⟩
def normal : FieldSummary := ⟨fieldInitial,⟨field,false⟩⟩
def target : Run1StrongPreservation.FieldTarget := ⟨1,[4],7⟩
def oldField : Run1StrongPreservation.Edge Initial := ⟨fieldInitial,⟨field,true⟩⟩
def oldSibling : Run1StrongPreservation.Edge Initial :=
  ⟨fieldInitial,⟨(Run1Requests.Slot.field 2 4).initial,true⟩⟩

def sameFieldOnly (edge : FieldSummary) : Bool :=
  edge.conclusion.fact.base == edge.premise.toAP.base &&
  edge.conclusion.fact.path == edge.premise.toAP.path

theorem field_only_test_unsound : sameFieldOnly cleaned = true ∧ compactable cleaned = false := by decide
theorem transformed_mark_really_differs : cleaned.conclusion.fact ≠ plain.conclusion.fact := by decide
theorem tail_and_layer_are_not_identities :
    compactable movedTail = false ∧ compactable normal = false := by decide
theorem initial_mark_zero_is_legal : Legal (encode (.fieldExact 2 3 0)) := ⟨_,rfl⟩

def checks : Bool :=
  let a : Run1Requests.Leaf := ⟨.field 2 3,.any,8,.set [19],true⟩
  let slot : Run1Requests.Slot := .field 2 3
  (classify ⟨.root,.star,.star,.empty⟩).isNone &&
  (classify ⟨.field 2 3,.any,.conc 8,.empty⟩).isNone &&
  (classify ⟨.field 2 3,.must,.conc 8,.empty⟩).isNone &&
  (projectAbstract slot a == some slot.initial) &&
  (projectConcrete slot [] a == none) &&
  (projectConcrete slot [8] a == some (Initial.fieldStar 2 3 8).toAP) &&
  (projectAbstract slot ⟨.cls 2,.exact,8,.empty,false⟩ == none) &&
  (requestAt owner (.abstractField 9 7) field 8 == some ⟨owner,.field 9 7,8⟩) &&
  (requestAt owner (.fieldExact 2 3 8) field 8 == none) &&
  (compact [plain,cleaned,movedTail,normal] == [cleaned,movedTail,normal]) &&
  ((cleanMatchedFlow owner fieldInitial ⟨field,true⟩ 8).2 == some ⟨owner,slot,8⟩) &&
  (Run1StrongPreservation.preserve .definite target oldField == none) &&
  (Run1StrongPreservation.preserve .definite target oldSibling == some oldSibling) &&
  (Run1StrongPreservation.write .definite target oldField [oldField] == [oldField]) &&
  ((advance (demote ⟨.base 0,⟨field,false⟩⟩)
    (Run1Requests.Slot.base 0).initial false).current.demand)

theorem controls_pass : checks = true := by decide

/-- Existing guard observes the intermediate layer; final relabeling is not
    an equivalent way to install the early-demotion policy. -/
def memoryEffectAdmits (identityCandidate demand : Bool) : Bool := demand || !identityCandidate
theorem early_demotion_changes_guard :
    memoryEffectAdmits true false = false ∧ memoryEffectAdmits true true = true := by decide

#eval checks
#guard checks
end Controls

#print axioms classifier_exact
#print axioms legal_AP_no_any
#print axioms abstract_static_start_D
#print axioms concrete_exact_not_blanket_demoted
#print axioms concrete_STAR_start_ANY_D
#print axioms abstract_project_legal
#print axioms concrete_answer_requires_actual_and_request
#print axioms projection_ignores_E
#print axioms class_answer_legal
#print axioms demotion_sticky
#print axioms static_origin_demand
#print axioms static_current_demand
#print axioms advance_sticky
#print axioms static_to_local_stays_demand
#print axioms request_ignores_E_mx
#print axioms overwritten_demand_is_killed
#print axioms self_assignment_uses_pre_store_value
#print axioms survivor_full_payload
#print axioms cleaner_excludes_selected_mark
#print axioms already_excluded_still_requests
#print axioms default_full_payload
#print axioms compaction_idempotent
#print axioms discarded_identity_supplied_by_default
#print axioms Controls.controls_pass
#print axioms Controls.field_only_test_unsound

end ApSpec.BottomUpStaticDomain
