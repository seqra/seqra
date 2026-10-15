/-
  Callee added-fact-store elimination: a finite local protocol and its boundary.

  Suppliers and their full origins occur only in Ghost and in the caller's N2
  relation. State contains persistent questions, eligible question-specific
  answers, and emitted initials; it has no supplier field. An offer is already
  checked against the ORIGINAL supplier geometry/E/mx/layer. It is not an
  unchecked projection of a supplier, or an initial reused as a supplier.

  This is a local, set-of-outputs refinement, not a proof of the engine scheduler,
  Kotlin data structures, whole-program coverage, ND confirmation or time bounds.
  Exact N2 replay, including both arrival orders, is an explicit obligation below.
-/
import ApSpec.CurrentDemandClosure
import ApSpec.RequestSets
import ApSpec.CumulativeSubscriptions
import ApSpec.Statics

namespace ApSpec.AddedFactsRemoval
open ApSpec ApSpec.Abs ApSpec.Handoff

section Protocol
variable {Q Answer : Type} [DecidableEq Q] [DecidableEq Answer]

/-- Executable finite set insertion; multiplicity of suppliers is not retained. -/
def remember (x : Answer) (xs : List Answer) : List Answer :=
  if x ∈ xs then xs else x :: xs

@[simp] theorem mem_remember (x y : Answer) (xs : List Answer) :
    y ∈ remember x xs ↔ y = x ∨ y ∈ xs := by
  by_cases h : x ∈ xs
  · simp only [remember, if_pos h]
    constructor
    · exact Or.inr
    · rintro (rfl | hy)
      · exact h
      · exact hy
  · simp [remember, h]

theorem remember_idempotent (x : Answer) (xs : List Answer) :
    remember x (remember x xs) = remember x xs := by
  unfold remember at *
  by_cases h : x ∈ xs <;> simp [h]

/-- Instrumented finite oracle. questions/pending are runtime state; emitted
    is a logical set of observed outputs, NOT a retained runtime dedup history.
    Q includes full method/entry/context, role, geometry and mark. Answer retains
    the emitted initial shape, not caller origin. Runtime/advance below erase
    this observation and return a transient output batch for InitialFactStore. -/
structure State (Q Answer : Type) where
  questions : List Q
  pending : List (Q × Answer)
  emitted : List (Q × Answer)
  deriving DecidableEq, Repr

def empty : State Q Answer := ⟨[], [], []⟩

inductive Event (Q Answer : Type) where
  | request (question : Q)
  | offer (question : Q) (answer : Answer)
  deriving DecidableEq, Repr

/-- Request consumption removes only this exact question's pending cells.
    Keeping the question is necessary for suppliers arriving afterwards. -/
def step (s : State Q Answer) : Event Q Answer → State Q Answer
  | .request q =>
    ⟨remember q s.questions,
      s.pending.filter (fun p => decide (p.1 ≠ q)),
      s.pending.filter (fun p => decide (p.1 = q)) ++ s.emitted⟩
  | .offer q a =>
    if q ∈ s.questions then {s with emitted := remember (q, a) s.emitted}
    else {s with pending := remember (q, a) s.pending}

/-- This proof-only history is deliberately NOT a field of State. -/
structure Ghost (Q Answer : Type) where
  asked : List Q
  offered : List (Q × Answer)

def ghostStep (g : Ghost Q Answer) : Event Q Answer → Ghost Q Answer
  | .request q => {g with asked := q :: g.asked}
  | .offer q a => {g with offered := (q, a) :: g.offered}

/-- Exact partition: requested answers emitted, unrequested answers pending. -/
def Encodes (s : State Q Answer) (g : Ghost Q Answer) : Prop :=
  (∀ q, q ∈ s.questions ↔ q ∈ g.asked) ∧
  (∀ p, p ∈ s.emitted ↔ p ∈ g.offered ∧ p.1 ∈ g.asked) ∧
  (∀ p, p ∈ s.pending ↔ p ∈ g.offered ∧ p.1 ∉ g.asked)

omit [DecidableEq Q] [DecidableEq Answer] in
theorem empty_encodes : Encodes (empty : State Q Answer) ⟨[], []⟩ := by
  simp [Encodes, empty]

/-- Constructive one-event refinement, including duplicate requests/offers. -/
theorem step_encodes (s : State Q Answer) (g : Ghost Q Answer)
    (h : Encodes s g) (e : Event Q Answer) :
    Encodes (step s e) (ghostStep g e) := by
  rcases h with ⟨hq, he, hp⟩
  cases e with
  | request q =>
    refine ⟨fun k => ?_, fun p => ?_, fun p => ?_⟩
    · simp [step, ghostStep, hq]
    · simp only [step, ghostStep, List.mem_append, List.mem_filter,
        decide_eq_true_eq, List.mem_cons, he, hp]
      by_cases hk : p.1 = q <;> by_cases ha : p.1 ∈ g.asked <;>
        simp_all
    · simp only [step, ghostStep, List.mem_filter, decide_eq_true_eq,
        List.mem_cons, hp]
      constructor
      · rintro ⟨⟨ho, hn⟩, hd⟩
        exact ⟨ho, fun hc => hc.elim hd hn⟩
      · rintro ⟨ho, hn⟩
        exact ⟨⟨ho, fun ha => hn (Or.inr ha)⟩, fun hd => hn (Or.inl hd)⟩
  | offer q a =>
    by_cases asked : q ∈ s.questions
    · have hg := (hq q).mp asked
      refine ⟨by simpa only [step, if_pos asked, ghostStep] using hq, fun p => ?_, fun p => ?_⟩
      · simp only [step, if_pos asked, ghostStep, mem_remember, List.mem_cons, he]
        constructor
        · rintro (rfl | ⟨ho, ha⟩)
          · exact ⟨Or.inl rfl, hg⟩
          · exact ⟨Or.inr ho, ha⟩
        · rintro ⟨(rfl | ho), ha⟩
          · exact Or.inl rfl
          · exact Or.inr ⟨ho, ha⟩
      · simp only [step, if_pos asked, ghostStep, List.mem_cons, hp]
        constructor
        · rintro ⟨ho, hn⟩; exact ⟨Or.inr ho, hn⟩
        · rintro ⟨(rfl | ho), hn⟩
          · exact False.elim (hn hg)
          · exact ⟨ho, hn⟩
    · have hg : q ∉ g.asked := fun h => asked ((hq q).mpr h)
      refine ⟨by simpa only [step, if_neg asked, ghostStep] using hq, fun p => ?_, fun p => ?_⟩
      · simp only [step, if_neg asked, ghostStep, List.mem_cons, he]
        constructor
        · rintro ⟨ho, ha⟩; exact ⟨Or.inr ho, ha⟩
        · rintro ⟨(rfl | ho), ha⟩
          · exact False.elim (hg ha)
          · exact ⟨ho, ha⟩
      · simp only [step, if_neg asked, ghostStep, mem_remember, List.mem_cons, hp]
        constructor
        · rintro (rfl | ⟨ho, hn⟩)
          · exact ⟨Or.inl rfl, hg⟩
          · exact ⟨Or.inr ho, hn⟩
        · rintro ⟨(rfl | ho), hn⟩
          · exact Or.inl rfl
          · exact Or.inr ⟨ho, hn⟩

def run (s : State Q Answer) (events : List (Event Q Answer)) : State Q Answer :=
  events.foldl step s

def ghostRun (g : Ghost Q Answer) (events : List (Event Q Answer)) : Ghost Q Answer :=
  events.foldl ghostStep g

/-- Exact output/pending characterization after EVERY finite interleaving. -/
theorem trace_refines (events : List (Event Q Answer)) (s : State Q Answer)
    (g : Ghost Q Answer) (h : Encodes s g) :
    Encodes (run s events) (ghostRun g events) := by
  induction events generalizing s g with
  | nil => exact h
  | cons e events ih => exact ih _ _ (step_encodes s g h e)

theorem output_iff (events : List (Event Q Answer)) (p : Q × Answer) :
    p ∈ (run empty events).emitted ↔
      p ∈ (ghostRun ⟨[], []⟩ events).offered ∧
      p.1 ∈ (ghostRun ⟨[], []⟩ events).asked :=
  (trace_refines events empty ⟨[], []⟩ empty_encodes).2.1 p

theorem pending_iff (events : List (Event Q Answer)) (p : Q × Answer) :
    p ∈ (run empty events).pending ↔
      p ∈ (ghostRun ⟨[], []⟩ events).offered ∧
      p.1 ∉ (ghostRun ⟨[], []⟩ events).asked :=
  (trace_refines events empty ⟨[], []⟩ empty_encodes).2.2 p

/-- No global-mark consumption: an unrelated question's answer remains pending. -/
theorem request_preserves_other_pending (s : State Q Answer) (q : Q)
    (p : Q × Answer) (different : p.1 ≠ q) :
    p ∈ (step s (.request q)).pending ↔ p ∈ s.pending := by
  simp [step, different]

theorem request_is_persistent (s : State Q Answer) (q : Q) :
    q ∈ (step s (.request q)).questions := by simp [step]

theorem late_supplier_emits (s : State Q Answer) (q : Q) (a : Answer)
    (asked : q ∈ s.questions) :
    (q, a) ∈ (step s (.offer q a)).emitted := by simp [step, asked]

/-- The two races have identical final state, not merely one-way coverage. -/
theorem both_arrival_orders (q : Q) (a : Answer) :
    run empty [.offer q a, .request q] = run empty [.request q, .offer q a] := by
  simp [run, empty, step, remember]

/-- Repeated suppliers with the SAME eligible answer add no memo cell. -/
theorem duplicate_offer (s : State Q Answer) (q : Q) (a : Answer) :
    step (step s (.offer q a)) (.offer q a) = step s (.offer q a) := by
  by_cases h : q ∈ s.questions <;> simp [step, h, remember_idempotent]

/-- n counts extra suppliers, NOT machine time or bytes. Equal checked answers
    from arbitrarily many original caller references do not grow this state. -/
theorem repeated_origins_same_state (s : State Q Answer) (q : Q) (a : Answer) (n : Nat) :
    run (step s (.offer q a)) (List.replicate n (.offer q a)) = step s (.offer q a) := by
  induction n with
  | zero => rfl
  | succ n ih =>
    simpa only [List.replicate_succ, run, List.foldl_cons, duplicate_offer] using ih

/-- Exact logical cell count in the scalar oracle, independent of caller
    multiplicity. It is not a claim about JVM bytes or all possible answer shapes. -/
theorem origin_multiplicity_storage (q : Q) (a : Answer) (n : Nat) :
    (run (step empty (.offer q a)) (List.replicate n (.offer q a))).pending.length = 1 := by
  rw [repeated_origins_same_state]
  simp [step, empty, remember]

def askedBy : List (Event Q Answer) → List Q
  | [] => []
  | .request q :: es => q :: askedBy es
  | .offer _ _ :: es => askedBy es

def offeredBy : List (Event Q Answer) → List (Q × Answer)
  | [] => []
  | .request _ :: es => offeredBy es
  | .offer q a :: es => (q, a) :: offeredBy es

omit [DecidableEq Q] [DecidableEq Answer] in
 theorem ghostRun_members (events : List (Event Q Answer)) (g : Ghost Q Answer) :
    (∀ q, q ∈ (ghostRun g events).asked ↔ q ∈ g.asked ∨ q ∈ askedBy events) ∧
    (∀ p, p ∈ (ghostRun g events).offered ↔ p ∈ g.offered ∨ p ∈ offeredBy events) := by
  induction events generalizing g with
  | nil => simp [ghostRun, askedBy, offeredBy]
  | cons e es ih =>
    obtain ⟨hq, hp⟩ := ih (ghostStep g e)
    cases e <;>
      simpa only [ghostRun, List.foldl_cons, ghostStep, askedBy, offeredBy,
        List.mem_cons, or_assoc, or_left_comm, or_comm] using And.intro hq hp

/-- Exact checked answers after every finite interleaving, including duplicates. -/
theorem trace_outputs (events : List (Event Q Answer)) (p : Q × Answer) :
    p ∈ (run empty events).emitted ↔ p ∈ offeredBy events ∧ p.1 ∈ askedBy events := by
  rw [output_iff]
  simp only [(ghostRun_members events ⟨[], []⟩).1,
    (ghostRun_members events ⟨[], []⟩).2, List.not_mem_nil, false_or]

/-- Actual N2 replay may use an index. Exactness is an effect-set equality, not
    a claim that emitted initials reconstruct original inputs. -/
def ExactReplay {Input : Type} (history : List Input)
    (effect : Q → Input → List Answer) (replay : Q → List Answer) : Prop :=
  ∀ q a, a ∈ replay q ↔ ∃ input, input ∈ history ∧ a ∈ effect q input

def replayQuestion (q : Q) (answers : List Answer) : State Q Answer :=
  run empty (.request q :: answers.map (fun a => .offer q a))

omit [DecidableEq Q] [DecidableEq Answer] in
@[simp] theorem offeredBy_offers (q : Q) (answers : List Answer) :
    offeredBy (answers.map (fun a => Event.offer q a)) = answers.map (fun a => (q, a)) := by
  induction answers <;> simp_all [offeredBy]

omit [DecidableEq Q] [DecidableEq Answer] in
@[simp] theorem askedBy_offers (q : Q) (answers : List Answer) :
    askedBy (answers.map (fun a => Event.offer q a)) = [] := by
  induction answers <;> simp_all [askedBy]

theorem replayQuestion_output (q : Q) (answers : List Answer) (a : Answer) :
    (q, a) ∈ (replayQuestion q answers).emitted ↔ a ∈ answers := by
  simp only [replayQuestion, trace_outputs, offeredBy, askedBy,
    offeredBy_offers, askedBy_offers, List.mem_cons, List.not_mem_nil, or_false,
    and_true, List.mem_map]
  constructor
  · rintro ⟨b, hb, he⟩
    have equal : b = a := congrArg Prod.snd he
    rw [← equal]
    exact hb
  · intro h; exact ⟨a, h, rfl⟩

/-- Sound AND complete local outputs after a late question, under exact replay.
    replayQuestion has no callee supplier field. -/
theorem exact_replay_refinement {Input : Type} (history : List Input)
    (effect : Q → Input → List Answer) (replay : Q → List Answer)
    (exactReplay : ExactReplay history effect replay) (q : Q) (a : Answer) :
    (q, a) ∈ (replayQuestion q (replay q)).emitted ↔
      ∃ input, input ∈ history ∧ a ∈ effect q input := by
  rw [replayQuestion_output]
  exact exactReplay q a

omit [DecidableEq Q] [DecidableEq Answer] in
/-- Existing cumulative N2 encoding discharges the ghost-history side of replay.
    Key retains full original CallerRef and admitted header until effect checking. -/
theorem n2_history_replay {Key Leaf : Type}
    {rows : CumulativeSubscriptions.Rows Key Leaf} {history : List (Key × Leaf)}
    (encoded : CumulativeSubscriptions.Encodes rows history)
    (effect : Q → Key → Leaf → Answer → Prop) (q : Q) (a : Answer) :
    (∃ key leaf, rows key leaf = true ∧ effect q key leaf a) ↔
      ∃ key leaf, (key, leaf) ∈ history ∧ effect q key leaf a := by
  constructor <;> rintro ⟨key, leaf, accepted, applied⟩
  · exact ⟨key, leaf, (encoded key leaf).mp accepted, applied⟩
  · exact ⟨key, leaf, (encoded key leaf).mpr accepted, applied⟩

omit [DecidableEq Q] [DecidableEq Answer] in
/-- New questions see retained inputs; existing questions see fresh input or
    subscription deltas. Crossed arrivals belong to arm1, before projection. -/
theorem n2_two_sided_frontier {Input : Type}
    (oldQ freshQ : Q → Prop) (oldI freshI : Input → Prop)
    (effect : Q → Input → Answer → Prop) (q : Q) (i : Input) (a : Answer) :
    (((freshQ q ∧ (oldI i ∨ freshI i)) ∨ (oldQ q ∧ freshI i)) ∧ effect q i a) ↔
      (((oldQ q ∨ freshQ q) ∧ (oldI i ∨ freshI i)) ∧
        (freshQ q ∨ freshI i)) ∧ effect q i a := by
  rw [RequestSets.frontier_pairs]

/-- Actual retained local state has NO supplier or emitted-output-history field. -/
structure Runtime (Q Answer : Type) where
  questions : List Q
  pending : List (Q × Answer)
  deriving DecidableEq, Repr

def retained (s : State Q Answer) : Runtime Q Answer := ⟨s.questions, s.pending⟩

/-- The second component is a transient output batch. Repeated outputs may be
    handed to InitialFactStore's ordinary projected-initial deduplication. -/
def advance (s : Runtime Q Answer) : Event Q Answer → Runtime Q Answer × List (Q × Answer)
  | .request q =>
    (⟨remember q s.questions, s.pending.filter (fun p => decide (p.1 ≠ q))⟩,
      s.pending.filter (fun p => decide (p.1 = q)))
  | .offer q a =>
    if q ∈ s.questions then (s, [(q, a)])
    else (⟨s.questions, remember (q, a) s.pending⟩, [])

/-- Forgetting observation history commutes with each executable transition. -/
theorem erase_observation_step (s : State Q Answer) (e : Event Q Answer) :
    retained (step s e) = (advance (retained s) e).1 := by
  cases e with
  | request q => rfl
  | offer q a => by_cases h : q ∈ s.questions <;> simp [retained, step, advance, h]

/-- Exact emitted-set law needs no retained output history in the runtime. -/
theorem advance_output_set (s : State Q Answer) (e : Event Q Answer) (p : Q × Answer) :
    p ∈ (step s e).emitted ↔ p ∈ (advance (retained s) e).2 ∨ p ∈ s.emitted := by
  cases e with
  | request q => simp [step, advance, retained]
  | offer q a =>
    by_cases h : q ∈ s.questions <;> simp [step, advance, retained, h]

/-- Projection to emitted initials removes question multiplicity AFTER eligibility.
    No conjunction of question/premise alternatives is introduced. -/
theorem projected_output_iff {Initial : Type} (project : Q × Answer → Initial)
    (events : List (Event Q Answer)) (initial : Initial) :
    initial ∈ (run empty events).emitted.map project ↔
      ∃ p, p ∈ offeredBy events ∧ p.1 ∈ askedBy events ∧ project p = initial := by
  simp only [List.mem_map, trace_outputs]
  constructor
  · rintro ⟨p, ⟨ho, hq⟩, he⟩; exact ⟨p, ho, hq, he⟩
  · rintro ⟨p, ho, hq, he⟩; exact ⟨p, ⟨ho, hq⟩, he⟩

end Protocol

/-! Immediate emission after run1: suppliers below are ghost history, not state. -/
section Immediate
variable {Input Output : Type}

def immediate (emit : Input → List Output) (outputs : List Output) (input : Input) :=
  outputs ++ emit input

/-- Direct delivery has exactly the same outputs as a ghost history scan. -/
theorem immediate_stream (emit : Input → List Output) (history : List Input)
    (outputs : List Output) :
    history.foldl (immediate emit) outputs = outputs ++ history.flatMap emit := by
  induction history generalizing outputs with
  | nil => simp
  | cons i is ih =>
    simp only [List.foldl_cons, List.flatMap_cons, immediate, ih, List.append_assoc]

def restrictedEmit (demand : List DemandEdge) (a : PFact) : List PFact :=
  demand.filterMap (fun d => emitW d.din a)

/-- Both request-free directions use the actual existing emitW operation. -/
theorem restricted_immediate_exact (demand : List DemandEdge) (history : List PFact)
    (j : PFact) :
    j ∈ history.foldl (immediate (restrictedEmit demand)) [] ↔
      ∃ a, a ∈ history ∧ ∃ d, d ∈ demand ∧ emitW d.din a = some j := by
  rw [immediate_stream]
  simp only [List.nil_append, List.mem_flatMap, restrictedEmit, List.mem_filterMap]

end Immediate

/-! AP instantiation. Method context/entry and operation role are explicit keys.
    Full patterns preserve tail/exclusions. No full-engine identification of this
    descriptor with the JVM forms/role enums is asserted by these local proofs. -/
structure FullMethod where
  method : MethodId
  context : Nat
  entry : Node
  deriving DecidableEq, Repr

structure QuestionGeometry where
  method : FullMethod
  role : Nat
  initial : PFact
  pattern : PFact
  patternEx : Excl
  deriving DecidableEq, Repr

abbrev Question := QuestionGeometry × Mark

/-- Pending row payload: full emitted initial shape WITHOUT its concrete mark.
    The Kind retains its tail exclusion; no caller identity is a field here. -/
structure AnswerShape where
  base : Base
  path : List Acc
  kind : Kind
  deriving DecidableEq, Repr

def shapeOf (j : PFact) : AnswerShape := ⟨j.base, j.path, j.kind⟩
def withMark (s : AnswerShape) (t : Mark) : PFact := ⟨s.base, s.path, s.kind, .conc t⟩

theorem shape_copies_mark (j : PFact) (t : Mark) (concrete : j.mark = .conc t) :
    withMark (shapeOf j) t = j := by cases j; simp_all [withMark, shapeOf]

/-- Existing deep-static answer policy, otherwise the existing answerInit.
    ANY-taint annotation eligibility is checked before this projection. In run1
    an emitted ANY premise has the ordinary demand start (no extra E). -/
def requestInitial (staticBase : Base) (q a : PFact) (t : Mark) : PFact :=
  if Nat.beq q.base staticBase && (dropPrefix q.path a.path).isSome then
    ⟨a.base, a.path, a.kind, .conc t⟩
  else answerInit q a t

theorem requestInitial_matches_existing (x : Statics.SCtx) (deep : x.deepAns = true)
    (q a : PFact) (t : Mark) :
    requestInitial x.sB q a t = x.ansInit q a t := by
  simp only [requestInitial, Statics.SCtx.ansInit, deep, Bool.true_and]

theorem requestInitial_mark (s : Base) (q a : PFact) (t : Mark) :
    (requestInitial s q a t).mark = .conc t := by
  unfold requestInitial
  split
  · rfl
  · exact answerInit_mark

/-- Annotated HISTORICAL same-slot request kernel, not the complete current
    catalog/tree emitter. The descriptor retains original initial separately
    from full query pattern/E. Eligibility uses ACTUAL supplier and query E
    before projection; answer construction uses the original initial. The RC
    bridge below requires equal slot/pattern and empty query E. Distinct-slot
    catalog emission and current layer/ANY normalization remain ExactReplay
    obligations; no production completeness follows from this adapter. FLOW
    climbs keep full origins in N2 and are outside this answer codec. -/
def checkedAnswer (staticBase : Base) (q : Question) (a : AnyTaintEx.XFact) : Option AnswerShape :=
  if a.af.fact.mark = .conc q.2 ∧
      AnyTaintEx.overlapX a.af.fact a.ex q.1.pattern q.1.patternEx = true then
    some (shapeOf (requestInitial staticBase q.1.initial a.af.fact q.2))
  else none

/-- The proof supplies executable witnesses (the actual guarded answer), rather
    than selecting a supplier from an existential proposition. -/
theorem checkedAnswer_iff (s : Base) (q : Question) (a : AnyTaintEx.XFact)
    (shape : AnswerShape) :
    checkedAnswer s q a = some shape ↔
      a.af.fact.mark = .conc q.2 ∧
      AnyTaintEx.overlapX a.af.fact a.ex q.1.pattern q.1.patternEx = true ∧
      shape = shapeOf (requestInitial s q.1.initial a.af.fact q.2) := by
  unfold checkedAnswer
  split
  · rename_i h
    constructor
    · intro e; exact ⟨h.1, h.2, (Option.some.inj e).symm⟩
    · rintro ⟨_, _, rfl⟩; rfl
  · rename_i h
    constructor
    · intro impossible; cases impossible
    · rintro ⟨hm, ho, _⟩; exact False.elim (h ⟨hm, ho⟩)

/-- The emitted concrete initial is reconstructed exactly from the compact row. -/
theorem checkedAnswer_initial (s : Base) (q : Question) (a : AnyTaintEx.XFact)
    (shape : AnswerShape) (h : checkedAnswer s q a = some shape) :
    withMark shape q.2 = requestInitial s q.1.initial a.af.fact q.2 := by
  rw [(checkedAnswer_iff s q a shape).mp h |>.2.2]
  exact shape_copies_mark _ _ (requestInitial_mark _ _ _ _)

/-- For a non-static answer the existing RC request rule derives the very same
    local initial. It does not prove whole-engine store elimination/coverage. -/
theorem rc_answer_from_checked {P : Program} {counted : Acc → Bool} {L : Nat}
    {α : MethodId → PFact → PFact} {sinks : List (MethodId × Node × PFact)}
    {roots : List MethodId} {s : Base} {q : Question} {a : AnyTaintEx.XFact}
    {shape : AnswerShape}
    (sameSlot : q.1.initial = q.1.pattern)
    (_emptyQueryEx : q.1.patternEx = Excl.empty)
    (nonStatic : Nat.beq q.1.initial.base s = false)
    (checked : checkedAnswer s q a = some shape)
    (request : Current.RC P counted L α sinks roots
      (.req q.1.method.method q.1.initial q.2))
    (supplier : Current.RC P counted L α sinks roots (.added q.1.method.method a.af.fact)) :
    Current.RC P counted L α sinks roots
      (.init q.1.method.method (withMark shape q.2)) := by
  obtain ⟨mark, overlap, _⟩ := (checkedAnswer_iff s q a shape).mp checked
  rw [checkedAnswer_initial s q a shape checked]
  simp only [requestInitial, nonStatic, Bool.false_and, Bool.false_eq_true, if_false]
  have overlapping := AnyTaintEx.overlapX_overlapB overlap
  rw [← sameSlot] at overlapping
  exact Current.RC.answer request supplier mark overlapping

/-- A successful immediate emitW directly satisfies the current FC init rule. -/
theorem forward_immediate_init {P : Program} {counted : Acc → Bool} {L : Nat}
    {dem : MethodId → DemandEdge → Prop} {rc : Recs}
    {sinks : List (MethodId × Node × PFact)} {roots : List MethodId}
    {M : MethodId} {d : DemandEdge} {a j : PFact}
    (supplier : Current.FW P counted L dem rc sinks roots (.added M a))
    (demand : dem M d) (emitted : emitW d.din a = some j) :
    Current.FW P counted L dem rc sinks roots (.init M j) :=
  Current.FC.initR supplier demand emitted

/-- The same direct rule applies to the current BC (backward) closure. -/
theorem backward_immediate_init {P : Program} {counted : Acc → Bool} {L : Nat}
    {dem : MethodId → DemandEdge → Prop} {rc : Recs} {roots : List MethodId}
    {seeds : List (MethodId × Node × PFact)} {M : MethodId} {d : DemandEdge} {a j : PFact}
    (supplier : Current.BW P counted L dem rc roots seeds (.added M a))
    (demand : dem M d) (emitted : emitW d.din a = some j) :
    Current.BW P counted L dem rc roots seeds (.init M j) :=
  Current.BC.initR supplier demand emitted

/-- Run1 admission keeps its unconditional abstract policy answer, independent
    of concrete question answers. structural abstracts are supplied by the
    separately specified catalog; catalog completeness is NOT proved here. -/
def run1Admission (M : MethodId) (input : PFact) (structural : List PFact)
    (ready : List (Question × AnswerShape)) : List PFact :=
  policy1 M input :: (structural ++ ready.map (fun p => withMark p.2 p.1.2))

theorem run1_abstract_always (M : MethodId) (input : PFact)
    (structural : List PFact) (ready : List (Question × AnswerShape)) :
    policy1 M input ∈ run1Admission M input structural ready := by
  simp only [run1Admission, List.mem_cons, true_or]

theorem run1_abstract_shape (M : MethodId) (input : PFact) (nonzero : input ≠ zeroFact) :
    policy1 M input = ⟨input.base, [], .star Excl.empty, .star⟩ := by
  simp [policy1, policy, nonzero]

theorem run1_concrete_additive (M : MethodId) (input : PFact)
    (structural : List PFact) (ready : List (Question × AnswerShape))
    (p : Question × AnswerShape) (present : p ∈ ready) :
    withMark p.2 p.1.2 ∈ run1Admission M input structural ready := by
  apply List.mem_cons_of_mem
  exact List.mem_append_right _ (List.mem_map.mpr ⟨p, present, rfl⟩)

/-- RC's added object is ghost reachability; this constructor does not require
    retaining the supplier as a runtime store member. -/
theorem rc_unconditional_abstract {P : Program} {counted : Acc → Bool} {L : Nat}
    {sinks : List (MethodId × Node × PFact)} {roots : List MethodId}
    {M : MethodId} {a : PFact}
    (supplier : Current.RC P counted L policy1 sinks roots (.added M a)) :
    Current.RC P counted L policy1 sinks roots (.init M (policy1 M a)) :=
  Current.RC.initA supplier

namespace Certificate

def method : FullMethod := ⟨10, 20, 30⟩
def q (path : List Acc) (mark : Mark := 7) : Question :=
  (⟨method, 1, ⟨3, path, .star Excl.empty, .star⟩,
    ⟨3, path, .star Excl.empty, .star⟩, Excl.empty⟩, mark)
def exactG : AnyTaintEx.XFact := ⟨⟨⟨3, [5], .exact, .conc 7⟩, false⟩, Excl.empty⟩
def excludedRoot : AnyTaintEx.XFact :=
  ⟨⟨⟨3, [], .any, .conc 7⟩, false⟩, .set [4]⟩
def staticBase : Base := 3

/-- Exact g:T cannot answer f:T, even though the same concrete mark occurs. -/
theorem geometry_distinguishes :
    checkedAnswer staticBase (q [4]) exactG = none ∧
    checkedAnswer staticBase (q [5]) exactG = some ⟨3, [5], .exact⟩ := by decide

/-- A generic initial emitted for root question is NOT a valid supplier for a
    fresh f question: it has forgotten the actual ancestor exclusion. -/
def rootShape : AnswerShape := ⟨3, [], .any⟩
def projectedRoot : AnyTaintEx.XFact :=
  ⟨⟨withMark rootShape 7, true⟩, Excl.empty⟩

theorem projected_initial_is_not_supplier :
    checkedAnswer staticBase (q []) excludedRoot = some rootShape ∧
    checkedAnswer staticBase (q [4]) excludedRoot = none ∧
    checkedAnswer staticBase (q [4]) projectedRoot = some ⟨3, [4], .star Excl.empty⟩ := by
  decide

/-- Persistent questions and exact mark consumption are computed by the actual
    executable protocol, not asserted only by a relational encoding. -/
def memoTrace : State Question AnswerShape :=
  run empty [Event.offer (q [4]) ⟨3, [4], .exact⟩,
    .offer (q [5]) ⟨3, [5], .exact⟩,
    .offer (q [4] 8) ⟨3, [4], .exact⟩,
    .request (q [4]), .offer (q [4]) ⟨3, [4], .any⟩]

theorem exact_consumption_and_late_supplier :
    memoTrace.pending.length = 2 ∧ memoTrace.emitted.length = 2 ∧
    memoTrace.questions = [q [4]] ∧
    ((q [5]), ⟨3, [5], .exact⟩) ∈ memoTrace.pending ∧
    ((q [4] 8), ⟨3, [4], .exact⟩) ∈ memoTrace.pending := by decide

/-- Full context, entry and operation role are NOT projected from the key. -/
theorem question_key_distinctions :
    q [] ≠ ({(q []).1 with method := {method with context := 21}}, 7) ∧
    q [] ≠ ({(q []).1 with method := {method with entry := 31}}, 7) ∧
    q [] ≠ ({(q []).1 with role := 2}, 7) := by decide

/-- Actual emitW immediate results include the fixed abstract FLOW form and a
    separately requested concrete shape, preserving independent alternatives. -/
def fixedDemand : List DemandEdge :=
  [⟨⟨3, [], .any, .star⟩, none⟩, ⟨⟨3, [5], .exact, .conc 7⟩, none⟩]

theorem immediate_emitW_values :
    restrictedEmit fixedDemand exactG.af.fact =
      [⟨3, [], .star Excl.empty, .star⟩, ⟨3, [5], .exact, .conc 7⟩] := by decide

/-- Static root is included even when only concrete answers are deferred. -/
theorem run1_static_root_with_concrete :
    run1Admission 10 exactG.af.fact [] [((q [5]), ⟨3, [5], .exact⟩)] =
      [⟨3, [], .star Excl.empty, .star⟩, ⟨3, [5], .exact, .conc 7⟩] := by decide

/-- Additional key dimensions remain distinguishable even if the requested
    mark and current pattern path are identical. -/
theorem original_slot_and_query_E_distinguish :
    q [] ≠ ({(q []).1 with initial := ⟨3, [4], .star Excl.empty, .star⟩}, 7) ∧
    q [] ≠ ({(q []).1 with patternEx := .set [4]}, 7) := by decide

/-- Executable limit of caller-free local joins: the pooled literals hold,
    whereas no single full caller-origin supplies both. This certifies a
    distinguishing candidate, NOT exact confirmation or an engine counterexample. -/
def splitCallInputs : List (Nat × Mark) := [(11, 7), (22, 8)]
def atCaller (caller : Nat) (mark : Mark) : Bool :=
  splitCallInputs.any (fun p => p.1 == caller && p.2 == mark)
def pooledLiterals : Bool :=
  ([7, 8] : List Mark).all (fun mark => splitCallInputs.any (fun p => p.2 == mark))
def sameCallerLiterals : Bool :=
  ([11, 22] : List Nat).any (fun caller => ([7, 8] : List Mark).all (atCaller caller))

theorem mixed_caller_is_not_confirmation :
    pooledLiterals = true ∧ sameCallerLiterals = false := by decide

end Certificate

#print axioms step_encodes
#print axioms trace_refines
#print axioms output_iff
#print axioms pending_iff
#print axioms request_preserves_other_pending
#print axioms request_is_persistent
#print axioms late_supplier_emits
#print axioms both_arrival_orders
#print axioms duplicate_offer
#print axioms origin_multiplicity_storage
#print axioms trace_outputs
#print axioms exact_replay_refinement
#print axioms n2_history_replay
#print axioms n2_two_sided_frontier
#print axioms erase_observation_step
#print axioms advance_output_set
#print axioms projected_output_iff
#print axioms immediate_stream
#print axioms restricted_immediate_exact
#print axioms checkedAnswer_iff
#print axioms checkedAnswer_initial
#print axioms rc_answer_from_checked
#print axioms forward_immediate_init
#print axioms backward_immediate_init
#print axioms run1_abstract_always
#print axioms run1_abstract_shape
#print axioms run1_concrete_additive
#print axioms rc_unconditional_abstract
#print axioms Certificate.geometry_distinguishes
#print axioms Certificate.projected_initial_is_not_supplier
#print axioms Certificate.exact_consumption_and_late_supplier
#print axioms Certificate.question_key_distinctions
#print axioms Certificate.immediate_emitW_values
#print axioms Certificate.run1_static_root_with_concrete
#print axioms Certificate.original_slot_and_query_E_distinguish
#print axioms Certificate.mixed_caller_is_not_confirmation

end ApSpec.AddedFactsRemoval
