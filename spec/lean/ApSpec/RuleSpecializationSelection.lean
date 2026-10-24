/- Ordered item specialization and selection. Both loops use one explicit pure
binder, which constructs complete variants with the original serialized ID.
Selection is an arbitrary per-query Bool predicate on exact optional IDs.
A second executable model below covers the observable fallback sink-ID state.

Both interpreters operate within ONE FIXED CATALOG GENERATION and the SAME
ORIGINAL BINDING SKELETON. Normalization can be a deterministic post-bound
rewrite; it does not change this model's raw items/context enumerator.
This does not equate a pre-normalization binder with a normalized raw binder:
removing GEN actions before enumeration can change AnyArg domains/identities.
That transport requires a separate proof or preserved original binding domains.
The stateful fallback is also within this fixed generation. Any earlier
normalization that changes fallback-ID allocation needs a separate guard.
No elapsed-time or retained-byte conclusion is part of this model. -/
import Std
namespace ApSpec.RuleSpecializationSelection
set_option autoImplicit false
set_option maxRecDepth 10000
set_option maxHeartbeats 3000000
universe u v w x

inductive Role where
  | entrySource | callSource | exitSource | staticSource
  | entrySink | callSink | exitSink | cleaner | returnRewriter
  deriving DecidableEq, Repr
inductive Mode where | full | group deriving DecidableEq, Repr
structure Query where
  role : Role
  mode : Mode
  allRelevant : Bool
  catalogGeneration : Nat
  deriving DecidableEq, Repr
structure RawItem (Id : Type u) (Data : Type v) where
  serializedId : Option Id
  data : Data
  deriving DecidableEq, Repr
structure Bound (Id : Type u) (Ctx : Type w) (Payload : Type x) where
  serializedId : Option Id
  query : Query
  context : Ctx
  payload : Payload
  deriving DecidableEq, Repr
structure Specializer (Id : Type u) (Data : Type v) (Ctx : Type w) (Payload : Type x) where
  contexts : Query → RawItem Id Data → List Ctx
  expand : Query → RawItem Id Data → Ctx → List Payload

def bind {Id : Type u} {Data : Type v} {Ctx : Type w} {Payload : Type x}
    (sp : Specializer Id Data Ctx Payload) (q : Query) (raw : RawItem Id Data) : List (Bound Id Ctx Payload) :=
  (sp.contexts q raw).flatMap fun ctx =>
    (sp.expand q raw ctx).map fun body => ⟨raw.serializedId, q, ctx, body⟩

theorem bind_preserves_serialized_id
    {Id : Type u} {Data : Type v} {Ctx : Type w} {Payload : Type x}
    (sp : Specializer Id Data Ctx Payload) (q : Query) (raw : RawItem Id Data)
    (bound : Bound Id Ctx Payload) (h : bound ∈ bind sp q raw) : bound.serializedId = raw.serializedId := by
  obtain ⟨ctx, _, hb⟩ := List.mem_flatMap.mp h
  obtain ⟨body, _, rfl⟩ := List.mem_map.mp hb
  rfl

/-- Direct recursion keeps the filter proof constructive. In particular, this
does not use the library's classical `filter_eq_nil_iff` theorem. -/
theorem filter_of_all_false {A : Type u} (predicate : A → Bool) (items : List A)
    (h : ∀ item ∈ items, predicate item = false) : items.filter predicate = [] := by
  induction items with
  | nil => rfl
  | cons item rest ih =>
      have head := h item (by simp)
      have tail : ∀ item ∈ rest, predicate item = false := by
        intro a ha
        exact h a (by simp [ha])
      simp only [List.filter_cons, head, Bool.false_eq_true, ↓reduceIte]
      exact ih tail

theorem filter_of_all_true {A : Type u} (predicate : A → Bool) (items : List A)
    (h : ∀ item ∈ items, predicate item = true) : items.filter predicate = items := by
  induction items with
  | nil => rfl
  | cons item rest ih =>
      have head := h item (by simp)
      have tail : ∀ item ∈ rest, predicate item = true := by
        intro a ha
        exact h a (by simp [ha])
      simp only [List.filter_cons, head, ↓reduceIte]
      rw [ih tail]

theorem filter_bind
    {Id : Type u} {Data : Type v} {Ctx : Type w} {Payload : Type x}
    (sp : Specializer Id Data Ctx Payload) (q : Query) (raw : RawItem Id Data)
    (keep : Query → Option Id → Bool) :
    (bind sp q raw).filter (fun b => keep q b.serializedId) =
      if keep q raw.serializedId then bind sp q raw else [] := by
  cases h : keep q raw.serializedId
  · simp only [Bool.false_eq_true, ↓reduceIte]
    apply filter_of_all_false
    intro b hb
    simp [bind_preserves_serialized_id sp q raw b hb, h]
  · simp only [↓reduceIte]
    apply filter_of_all_true
    intro b hb
    simp [bind_preserves_serialized_id sp q raw b hb, h]

structure Result (Id : Type u) (Ctx : Type w) (Payload : Type x) where
  variants : List (Bound Id Ctx Payload)
  binderCalls : Nat
  contextVisits : Nat
  deriving DecidableEq, Repr

/-- One binder invocation and its actual finite context enumeration are counted
at each entered bind branch. This is an operational count, not VM wall time. -/
def late {Id : Type u} {Data : Type v} {Ctx : Type w} {Payload : Type x}
    (sp : Specializer Id Data Ctx Payload) (q : Query) (keep : Query → Option Id → Bool) :
    List (RawItem Id Data) → Result Id Ctx Payload
  | [] => ⟨[], 0, 0⟩
  | raw :: rest =>
      let bs := bind sp q raw
      let tail := late sp q keep rest
      ⟨bs.filter (fun b => keep q b.serializedId) ++ tail.variants,
        1 + tail.binderCalls, (sp.contexts q raw).length + tail.contextVisits⟩

def early {Id : Type u} {Data : Type v} {Ctx : Type w} {Payload : Type x}
    (sp : Specializer Id Data Ctx Payload) (q : Query) (keep : Query → Option Id → Bool) :
    List (RawItem Id Data) → Result Id Ctx Payload
  | [] => ⟨[], 0, 0⟩
  | raw :: rest =>
      if keep q raw.serializedId then
        let bs := bind sp q raw
        let tail := early sp q keep rest
        ⟨bs ++ tail.variants, 1 + tail.binderCalls, (sp.contexts q raw).length + tail.contextVisits⟩
      else early sp q keep rest

theorem exact_ordered_variants
    {Id : Type u} {Data : Type v} {Ctx : Type w} {Payload : Type x}
    (sp : Specializer Id Data Ctx Payload) (q : Query) (keep : Query → Option Id → Bool)
    (items : List (RawItem Id Data)) :
    (late sp q keep items).variants = (early sp q keep items).variants := by
  induction items with
  | nil => rfl
  | cons raw rest ih =>
      cases h : keep q raw.serializedId <;> simp [late, early, filter_bind, h, ih]

def allWeight {A : Type u} (weight : A → Nat) : List A → Nat
  | [] => 0
  | a :: rest => weight a + allWeight weight rest

def selectedWeight {A : Type u} (keep : A → Bool) (weight : A → Nat) : List A → Nat
  | [] => 0
  | a :: rest => if keep a then weight a + selectedWeight keep weight rest else selectedWeight keep weight rest

def rejectedWeight {A : Type u} (keep : A → Bool) (weight : A → Nat) : List A → Nat
  | [] => 0
  | a :: rest => if keep a then rejectedWeight keep weight rest else weight a + rejectedWeight keep weight rest

theorem work_partition {A : Type u} (keep : A → Bool) (weight : A → Nat) (items : List A) :
    allWeight weight items = selectedWeight keep weight items + rejectedWeight keep weight items := by
  induction items with
  | nil => rfl
  | cons a rest ih => cases h : keep a <;> simp [allWeight, selectedWeight, rejectedWeight, h] <;> omega

theorem selected_work_nonincrease {A : Type u} (keep : A → Bool) (weight : A → Nat) (items : List A) :
    selectedWeight keep weight items ≤ allWeight weight items := by
  have := work_partition keep weight items
  omega

theorem rejected_positive_work_strict {A : Type u} (keep : A → Bool) (weight : A → Nat) (items : List A)
    (positive : 0 < rejectedWeight keep weight items) : selectedWeight keep weight items < allWeight weight items := by
  have := work_partition keep weight items
  omega

theorem pure_actual_costs
    {Id : Type u} {Data : Type v} {Ctx : Type w} {Payload : Type x}
    (sp : Specializer Id Data Ctx Payload) (q : Query) (keep : Query → Option Id → Bool)
    (items : List (RawItem Id Data)) :
    (late sp q keep items).binderCalls = allWeight (fun _ => 1) items ∧
    (early sp q keep items).binderCalls = selectedWeight (fun raw => keep q raw.serializedId) (fun _ => 1) items ∧
    (late sp q keep items).contextVisits = allWeight (fun raw => (sp.contexts q raw).length) items ∧
    (early sp q keep items).contextVisits = selectedWeight (fun raw => keep q raw.serializedId)
      (fun raw => (sp.contexts q raw).length) items := by
  induction items with
  | nil => simp [late, early, allWeight, selectedWeight]
  | cons raw rest ih => cases h : keep q raw.serializedId <;>
      simp [late, early, allWeight, selectedWeight, h, ih.1, ih.2.1, ih.2.2.1, ih.2.2.2]

theorem pure_calls_nonincrease
    {Id : Type u} {Data : Type v} {Ctx : Type w} {Payload : Type x}
    (sp : Specializer Id Data Ctx Payload) (q : Query) (keep : Query → Option Id → Bool)
    (items : List (RawItem Id Data)) :
    (early sp q keep items).binderCalls ≤ (late sp q keep items).binderCalls := by
  obtain ⟨hl, he, _, _⟩ := pure_actual_costs sp q keep items
  rw [hl, he]
  exact selected_work_nonincrease _ _ _

theorem pure_contexts_nonincrease
    {Id : Type u} {Data : Type v} {Ctx : Type w} {Payload : Type x}
    (sp : Specializer Id Data Ctx Payload) (q : Query) (keep : Query → Option Id → Bool)
    (items : List (RawItem Id Data)) :
    (early sp q keep items).contextVisits ≤ (late sp q keep items).contextVisits := by
  obtain ⟨_, _, hl, he⟩ := pure_actual_costs sp q keep items
  rw [hl, he]
  exact selected_work_nonincrease _ _ _

theorem pure_contexts_strict_when_rejected_work_positive
    {Id : Type u} {Data : Type v} {Ctx : Type w} {Payload : Type x}
    (sp : Specializer Id Data Ctx Payload) (q : Query) (keep : Query → Option Id → Bool)
    (items : List (RawItem Id Data))
    (positive : 0 < rejectedWeight (fun raw => keep q raw.serializedId) (fun raw => (sp.contexts q raw).length) items) :
    (early sp q keep items).contextVisits < (late sp q keep items).contextVisits := by
  obtain ⟨_, _, hl, he⟩ := pure_actual_costs sp q keep items
  rw [hl, he]
  exact rejected_positive_work_strict _ _ _ positive

/- Observable state in MethodTaintConfigurationResolver.ruleId: explicit ID,
then CWE, otherwise incrementAndGet. This ID is distinct from serializedId. -/
inductive ReportKey where | explicit (id : Nat) | cwe (id : Nat) | fallback
  deriving DecidableEq, Repr
inductive ReportId where | explicit (id : Nat) | cwe (id : Nat) | generated (id : Nat)
  deriving DecidableEq, Repr

def stableKey : ReportKey → Bool
  | .explicit _ | .cwe _ => true
  | .fallback => false

def allocate : ReportKey → Nat → ReportId × Nat
  | .explicit id, n => (.explicit id, n)
  | .cwe id, n => (.cwe id, n)
  | .fallback, n => (.generated (n+1), n+1)

structure SinkRaw (Id : Type u) (Data : Type v) where
  item : RawItem Id Data
  reportKey : ReportKey
  deriving DecidableEq, Repr
structure Reported (Id : Type u) (Ctx : Type w) (Payload : Type x) where
  bound : Bound Id Ctx Payload
  reportId : ReportId
  deriving DecidableEq, Repr
structure ReportRows (Id : Type u) (Ctx : Type w) (Payload : Type x) where
  variants : List (Reported Id Ctx Payload)
  counter : Nat
  deriving DecidableEq, Repr

def assignReports {Id : Type u} {Ctx : Type w} {Payload : Type x}
    (key : ReportKey) (counter : Nat) : List (Bound Id Ctx Payload) → ReportRows Id Ctx Payload
  | [] => ⟨[], counter⟩
  | b :: rest =>
      let (id, next) := allocate key counter
      let tail := assignReports key next rest
      ⟨⟨b,id⟩ :: tail.variants, tail.counter⟩

theorem assign_stable_counter
    {Id : Type u} {Ctx : Type w} {Payload : Type x}
    (key : ReportKey) (counter : Nat) (bs : List (Bound Id Ctx Payload))
    (h : stableKey key = true) : (assignReports key counter bs).counter = counter := by
  cases key <;> simp [stableKey] at h
  · induction bs with
    | nil => rfl
    | cons b rest ih => simpa [assignReports, allocate] using ih
  · induction bs with
    | nil => rfl
    | cons b rest ih => simpa [assignReports, allocate] using ih

theorem assign_preserves_serialized_id
    {Id : Type u} {Ctx : Type w} {Payload : Type x}
    (key : ReportKey) (counter : Nat) (bs : List (Bound Id Ctx Payload))
    (id : Option Id) (ids : ∀ b ∈ bs, b.serializedId = id) :
    ∀ b ∈ (assignReports key counter bs).variants, b.bound.serializedId = id := by
  induction bs generalizing counter with
  | nil => simp [assignReports]
  | cons b rest ih =>
      intro out member
      have hb := ids b (by simp)
      have ht : ∀ b ∈ rest, b.serializedId = id := by
        intro x hx; exact ids x (by simp [hx])
      cases ha : allocate key counter with
      | mk report next =>
          simp only [assignReports, ha] at member
          rcases List.mem_cons.mp member with same | later
          · cases same; exact hb
          · exact ih next ht out later

theorem filter_assigned_bind
    {Id : Type u} {Data : Type v} {Ctx : Type w} {Payload : Type x}
    (sp : Specializer Id Data Ctx Payload) (q : Query) (raw : SinkRaw Id Data)
    (keep : Query → Option Id → Bool) (counter : Nat) :
    (assignReports raw.reportKey counter (bind sp q raw.item)).variants.filter
      (fun b => keep q b.bound.serializedId) =
      if keep q raw.item.serializedId then
        (assignReports raw.reportKey counter (bind sp q raw.item)).variants else [] := by
  have ids := assign_preserves_serialized_id raw.reportKey counter (bind sp q raw.item)
    raw.item.serializedId (fun b h => bind_preserves_serialized_id sp q raw.item b h)
  cases h : keep q raw.item.serializedId
  · simp only [Bool.false_eq_true, ↓reduceIte]
    apply filter_of_all_false
    intro out ho
    simp [ids out ho, h]
  · simp only [↓reduceIte]
    apply filter_of_all_true
    intro out ho
    simp [ids out ho, h]

structure StatefulResult (Id : Type u) (Ctx : Type w) (Payload : Type x) where
  variants : List (Reported Id Ctx Payload)
  counter : Nat
  binderCalls : Nat
  contextVisits : Nat
  deriving DecidableEq, Repr

def lateSink {Id : Type u} {Data : Type v} {Ctx : Type w} {Payload : Type x}
    (sp : Specializer Id Data Ctx Payload) (q : Query) (keep : Query → Option Id → Bool) :
    List (SinkRaw Id Data) → Nat → StatefulResult Id Ctx Payload
  | [], counter => ⟨[],counter,0,0⟩
  | raw :: rest, counter =>
      let bound := assignReports raw.reportKey counter (bind sp q raw.item)
      let tail := lateSink sp q keep rest bound.counter
      ⟨bound.variants.filter (fun b => keep q b.bound.serializedId) ++ tail.variants,
        tail.counter, 1+tail.binderCalls, (sp.contexts q raw.item).length+tail.contextVisits⟩

def earlySink {Id : Type u} {Data : Type v} {Ctx : Type w} {Payload : Type x}
    (sp : Specializer Id Data Ctx Payload) (q : Query) (keep : Query → Option Id → Bool) :
    List (SinkRaw Id Data) → Nat → StatefulResult Id Ctx Payload
  | [], counter => ⟨[],counter,0,0⟩
  | raw :: rest, counter =>
      if keep q raw.item.serializedId then
        let bound := assignReports raw.reportKey counter (bind sp q raw.item)
        let tail := earlySink sp q keep rest bound.counter
        ⟨bound.variants ++ tail.variants, tail.counter, 1+tail.binderCalls,
          (sp.contexts q raw.item).length+tail.contextVisits⟩
      else earlySink sp q keep rest counter

def stableQuery {Id : Type u} {Data : Type v} : List (SinkRaw Id Data) → Bool
  | [] => true
  | raw :: rest => stableKey raw.reportKey && stableQuery rest

theorem stable_sink_observation_equal
    {Id : Type u} {Data : Type v} {Ctx : Type w} {Payload : Type x}
    (sp : Specializer Id Data Ctx Payload) (q : Query) (keep : Query → Option Id → Bool)
    (items : List (SinkRaw Id Data)) (counter : Nat) (stable : stableQuery items = true) :
    (lateSink sp q keep items counter).variants = (earlySink sp q keep items counter).variants ∧
    (lateSink sp q keep items counter).counter = (earlySink sp q keep items counter).counter := by
  induction items generalizing counter with
  | nil => exact ⟨rfl,rfl⟩
  | cons raw rest ih =>
      have hs : stableKey raw.reportKey = true ∧ stableQuery rest = true := by
        simpa [stableQuery] using stable
      have hc := assign_stable_counter raw.reportKey counter (bind sp q raw.item) hs.1
      cases h : keep q raw.item.serializedId
      · have tail := ih counter hs.2
        simp [lateSink, earlySink, filter_assigned_bind, h, hc, tail.1, tail.2]
      · have tail := ih (assignReports raw.reportKey counter (bind sp q raw.item)).counter hs.2
        simp [lateSink, earlySink, filter_assigned_bind, h, tail.1, tail.2]

/-- Fallback is the entire late reference query IN THE SAME CATALOG GENERATION,
including rejected bind work and ID state.
It is not an interleaving of early selection with fallback-only items. -/
def guardedSink {Id : Type u} {Data : Type v} {Ctx : Type w} {Payload : Type x}
    (sp : Specializer Id Data Ctx Payload) (q : Query) (keep : Query → Option Id → Bool)
    (items : List (SinkRaw Id Data)) (counter : Nat) : StatefulResult Id Ctx Payload :=
  if stableQuery items then earlySink sp q keep items counter else lateSink sp q keep items counter

theorem guarded_sink_observation_equal
    {Id : Type u} {Data : Type v} {Ctx : Type w} {Payload : Type x}
    (sp : Specializer Id Data Ctx Payload) (q : Query) (keep : Query → Option Id → Bool)
    (items : List (SinkRaw Id Data)) (counter : Nat) :
    (guardedSink sp q keep items counter).variants = (lateSink sp q keep items counter).variants ∧
    (guardedSink sp q keep items counter).counter = (lateSink sp q keep items counter).counter := by
  cases h : stableQuery items
  · simp [guardedSink,h]
  · obtain ⟨hv,hc⟩ := stable_sink_observation_equal sp q keep items counter h
    simpa [guardedSink,h] using And.intro hv.symm hc.symm

theorem unstable_query_uses_entire_original_pipeline
    {Id : Type u} {Data : Type v} {Ctx : Type w} {Payload : Type x}
    (sp : Specializer Id Data Ctx Payload) (q : Query) (keep : Query → Option Id → Bool)
    (items : List (SinkRaw Id Data)) (counter : Nat) (h : stableQuery items = false) :
    guardedSink sp q keep items counter = lateSink sp q keep items counter := by
  simp [guardedSink,h]

theorem stateful_actual_costs
    {Id : Type u} {Data : Type v} {Ctx : Type w} {Payload : Type x}
    (sp : Specializer Id Data Ctx Payload) (q : Query) (keep : Query → Option Id → Bool)
    (items : List (SinkRaw Id Data)) (counter : Nat) :
    (lateSink sp q keep items counter).binderCalls = allWeight (fun _ => 1) items ∧
    (earlySink sp q keep items counter).binderCalls = selectedWeight (fun raw => keep q raw.item.serializedId) (fun _ => 1) items ∧
    (lateSink sp q keep items counter).contextVisits = allWeight (fun raw => (sp.contexts q raw.item).length) items ∧
    (earlySink sp q keep items counter).contextVisits = selectedWeight (fun raw => keep q raw.item.serializedId)
      (fun raw => (sp.contexts q raw.item).length) items := by
  induction items generalizing counter with
  | nil => simp [lateSink, earlySink, allWeight, selectedWeight]
  | cons raw rest ih =>
      cases h : keep q raw.item.serializedId <;>
        simp [lateSink, earlySink, allWeight, selectedWeight, h, (ih _).1,
          (ih _).2.1, (ih _).2.2.1, (ih _).2.2.2]

theorem guarded_actual_work_nonincrease
    {Id : Type u} {Data : Type v} {Ctx : Type w} {Payload : Type x}
    (sp : Specializer Id Data Ctx Payload) (q : Query) (keep : Query → Option Id → Bool)
    (items : List (SinkRaw Id Data)) (counter : Nat) :
    (guardedSink sp q keep items counter).binderCalls ≤ (lateSink sp q keep items counter).binderCalls ∧
    (guardedSink sp q keep items counter).contextVisits ≤ (lateSink sp q keep items counter).contextVisits := by
  cases h : stableQuery items
  · simp [guardedSink,h]
  · obtain ⟨hl,he,hcl,hce⟩ := stateful_actual_costs sp q keep items counter
    simp only [guardedSink,h,↓reduceIte,hl,he,hcl,hce]
    exact ⟨selected_work_nonincrease _ _ _,selected_work_nonincrease _ _ _⟩

theorem guarded_context_work_strict
    {Id : Type u} {Data : Type v} {Ctx : Type w} {Payload : Type x}
    (sp : Specializer Id Data Ctx Payload) (q : Query) (keep : Query → Option Id → Bool)
    (items : List (SinkRaw Id Data)) (counter : Nat) (stable : stableQuery items = true)
    (positive : 0 < rejectedWeight (fun raw => keep q raw.item.serializedId)
      (fun raw => (sp.contexts q raw.item).length) items) :
    (guardedSink sp q keep items counter).contextVisits < (lateSink sp q keep items counter).contextVisits := by
  obtain ⟨_,_,hl,he⟩ := stateful_actual_costs sp q keep items counter
  simp only [guardedSink,stable,↓reduceIte,hl,he]
  exact rejected_positive_work_strict _ _ _ positive

/- Executable controls. Product generation is explicit; counts are not assumed. -/
def qFull : Query := ⟨.callSink,.full,false,0⟩
def qGroup : Query := ⟨.callSink,.group,false,0⟩
def qAllRelevant : Query := ⟨.returnRewriter,.full,true,0⟩

def exactPolicy (q : Query) (id : Option Nat) : Bool :=
  q.allRelevant || match id with | none => true | some n => n == 7

def product20 : List (Nat × Nat) :=
  (List.range 20).flatMap fun a => (List.range 20).map fun b => (a,b)
def productSpecializer : Specializer Nat Nat (Nat × Nat) Nat :=
  ⟨(fun _ _ => product20),(fun _ raw ctx => [raw.data + ctx.1*20 + ctx.2])⟩
def rejectedProduct : RawItem Nat Nat := ⟨some 8,3⟩

theorem product_has_400_contexts : product20.length = 400 := by decide
theorem rejected_product_late_builds_400_variants :
    (bind productSpecializer qFull rejectedProduct).length = 400 := by decide
theorem rejected_product_work_saved :
    (late productSpecializer qFull exactPolicy [rejectedProduct]).binderCalls = 1 ∧
    (early productSpecializer qFull exactPolicy [rejectedProduct]).binderCalls = 0 ∧
    (late productSpecializer qFull exactPolicy [rejectedProduct]).contextVisits = 400 ∧
    (early productSpecializer qFull exactPolicy [rejectedProduct]).contextVisits = 0 := by decide

theorem null_id_policy_is_preserved :
    (early productSpecializer qFull exactPolicy [⟨none,3⟩]).variants =
      (late productSpecializer qFull exactPolicy [⟨none,3⟩]).variants := by
  exact (exact_ordered_variants _ _ _ _).symm

theorem unknown_policy_all_keeps_work :
    (early productSpecializer qFull (fun _ _ => true) [rejectedProduct]).contextVisits =
      (late productSpecializer qFull (fun _ _ => true) [rejectedProduct]).contextVisits := by decide

theorem all_relevant_query_is_not_replaced_by_selected_policy :
    (early productSpecializer qAllRelevant exactPolicy [rejectedProduct]).contextVisits = 400 := by decide

def oneSpecializer : Specializer Nat Nat Nat Nat :=
  ⟨(fun _ _ => [0]),(fun _ raw _ => [raw.data])⟩
def fallbackItems : List (SinkRaw Nat Nat) :=
  [⟨⟨some 8,10⟩,.fallback⟩,⟨⟨some 7,20⟩,.fallback⟩]

theorem fallback_counter_changes_accepted_report_id :
    (lateSink oneSpecializer qFull exactPolicy fallbackItems 0).variants.map (·.reportId) = [.generated 2] ∧
    (earlySink oneSpecializer qFull exactPolicy fallbackItems 0).variants.map (·.reportId) = [.generated 1] := by decide

theorem unguarded_stateful_early_is_not_equivalent :
    (lateSink oneSpecializer qFull exactPolicy fallbackItems 0).variants ≠
      (earlySink oneSpecializer qFull exactPolicy fallbackItems 0).variants := by decide

theorem fallback_guard_preserves_id_and_work :
    (guardedSink oneSpecializer qFull exactPolicy fallbackItems 0).variants.map (·.reportId) = [.generated 2] ∧
    (guardedSink oneSpecializer qFull exactPolicy fallbackItems 0).binderCalls = 2 := by decide

def explicitItems : List (SinkRaw Nat Nat) :=
  [⟨⟨some 8,10⟩,.explicit 100⟩,⟨⟨some 7,20⟩,.explicit 200⟩]

theorem explicit_guard_keeps_full_label_and_skips_one_binder :
    (guardedSink oneSpecializer qFull exactPolicy explicitItems 42).variants.map (·.reportId) = [.explicit 200] ∧
    (guardedSink oneSpecializer qFull exactPolicy explicitItems 42).counter = 42 ∧
    (guardedSink oneSpecializer qFull exactPolicy explicitItems 42).binderCalls = 1 ∧
    (lateSink oneSpecializer qFull exactPolicy explicitItems 42).binderCalls = 2 := by decide


/-- A concrete context with no variants and duplicate surviving variants remains
ordered; selection neither deduplicates nor erases the context/query labels. -/
def labelledSpecializer : Specializer Nat Nat Nat Nat :=
  ⟨(fun _ _ => [0,1]), (fun q raw ctx =>
    if q.mode = .group then [raw.data+1]
    else if ctx = 0 then [] else [raw.data,raw.data])⟩

theorem ordered_duplicate_complete_labels_control :
    (early labelledSpecializer qFull exactPolicy [⟨some 7,10⟩,⟨some 8,99⟩,⟨some 7,20⟩]).variants =
      [⟨some 7,qFull,1,10⟩,⟨some 7,qFull,1,10⟩,
       ⟨some 7,qFull,1,20⟩,⟨some 7,qFull,1,20⟩] := by decide

theorem mode_specific_binding_is_not_collapsed :
    (early labelledSpecializer qFull exactPolicy [⟨some 7,10⟩]).variants.map (·.payload) = [10,10] ∧
    (early labelledSpecializer qGroup exactPolicy [⟨some 7,10⟩]).variants.map (·.payload) = [11,11] := by decide

theorem stable_guard_product_work_saved :
    (guardedSink productSpecializer qFull exactPolicy [⟨rejectedProduct,.explicit 100⟩] 0).contextVisits = 0 ∧
    (lateSink productSpecializer qFull exactPolicy [⟨rejectedProduct,.explicit 100⟩] 0).contextVisits = 400 := by decide

theorem cwe_identity_is_stable_control :
    (guardedSink oneSpecializer qFull exactPolicy
      [⟨⟨some 8,10⟩,.cwe 100⟩,⟨⟨some 7,20⟩,.cwe 200⟩] 42).variants.map (·.reportId) = [.cwe 200] ∧
    (guardedSink oneSpecializer qFull exactPolicy
      [⟨⟨some 8,10⟩,.cwe 100⟩,⟨⟨some 7,20⟩,.cwe 200⟩] 42).counter = 42 := by decide


/-- The supported integration order is selection around the original binder,
then the SAME deterministic rewrite of retained complete bound rows. -/
theorem post_bound_rewrite_equal
    {Id : Type u} {Data : Type v} {Ctx : Type w} {Payload : Type x} {Out : Type}
    (sp : Specializer Id Data Ctx Payload) (q : Query) (keep : Query → Option Id → Bool)
    (items : List (RawItem Id Data)) (finish : Bound Id Ctx Payload → List Out) :
    (late sp q keep items).variants.flatMap finish =
      (early sp q keep items).variants.flatMap finish := by
  rw [exact_ordered_variants]

theorem guarded_post_bound_rewrite_equal
    {Id : Type u} {Data : Type v} {Ctx : Type w} {Payload : Type x} {Out : Type}
    (sp : Specializer Id Data Ctx Payload) (q : Query) (keep : Query → Option Id → Bool)
    (items : List (SinkRaw Id Data)) (counter : Nat)
    (finish : Reported Id Ctx Payload → List Out) :
    (guardedSink sp q keep items counter).variants.flatMap finish =
      (lateSink sp q keep items counter).variants.flatMap finish := by
  rw [(guarded_sink_observation_equal sp q keep items counter).1]

theorem stable_query_zero_counter_effect
    {Id : Type u} {Data : Type v} {Ctx : Type w} {Payload : Type x}
    (sp : Specializer Id Data Ctx Payload) (q : Query) (keep : Query → Option Id → Bool)
    (items : List (SinkRaw Id Data)) (counter : Nat) (h : stableQuery items = true) :
    (lateSink sp q keep items counter).counter = counter ∧
    (guardedSink sp q keep items counter).counter = counter := by
  have original : (lateSink sp q keep items counter).counter = counter := by
    induction items generalizing counter with
    | nil => rfl
    | cons raw rest ih =>
        have hs : stableKey raw.reportKey = true ∧ stableQuery rest = true := by
          simpa [stableQuery] using h
        have hc := assign_stable_counter raw.reportKey counter (bind sp q raw.item) hs.1
        simpa [lateSink, hc] using ih counter hs.2
  exact ⟨original, (guarded_sink_observation_equal sp q keep items counter).2.trans original⟩

/-- A cache-hit command contains the already-pinned raw bound rows and exact
query policy. The source bridge must establish identical cache keys/generation,
hit/miss sequence and cached rows. This is not a cache-redesign theorem. -/
inductive TraceCommand (Id : Type u) (Data : Type v) (Ctx : Type w) (Payload : Type x) where
  | bindQuery (query : Query) (items : List (SinkRaw Id Data))
  | cached (query : Query) (rows : List (Reported Id Ctx Payload))

def lateTrace {Id : Type u} {Data : Type v} {Ctx : Type w} {Payload : Type x}
    (sp : Specializer Id Data Ctx Payload) (keep : Query → Option Id → Bool) :
    List (TraceCommand Id Data Ctx Payload) → Nat → StatefulResult Id Ctx Payload
  | [], counter => ⟨[],counter,0,0⟩
  | .bindQuery q items :: rest, counter =>
      let first := lateSink sp q keep items counter
      let tail := lateTrace sp keep rest first.counter
      ⟨first.variants ++ tail.variants, tail.counter,
        first.binderCalls+tail.binderCalls, first.contextVisits+tail.contextVisits⟩
  | .cached q rows :: rest, counter =>
      let tail := lateTrace sp keep rest counter
      ⟨rows.filter (fun b => keep q b.bound.serializedId) ++ tail.variants,
        tail.counter,tail.binderCalls,tail.contextVisits⟩

def guardedTrace {Id : Type u} {Data : Type v} {Ctx : Type w} {Payload : Type x}
    (sp : Specializer Id Data Ctx Payload) (keep : Query → Option Id → Bool) :
    List (TraceCommand Id Data Ctx Payload) → Nat → StatefulResult Id Ctx Payload
  | [], counter => ⟨[],counter,0,0⟩
  | .bindQuery q items :: rest, counter =>
      let first := guardedSink sp q keep items counter
      let tail := guardedTrace sp keep rest first.counter
      ⟨first.variants ++ tail.variants, tail.counter,
        first.binderCalls+tail.binderCalls, first.contextVisits+tail.contextVisits⟩
  | .cached q rows :: rest, counter =>
      let tail := guardedTrace sp keep rest counter
      ⟨rows.filter (fun b => keep q b.bound.serializedId) ++ tail.variants,
        tail.counter,tail.binderCalls,tail.contextVisits⟩

theorem fixed_cache_query_trace_equal
    {Id : Type u} {Data : Type v} {Ctx : Type w} {Payload : Type x}
    (sp : Specializer Id Data Ctx Payload) (keep : Query → Option Id → Bool)
    (commands : List (TraceCommand Id Data Ctx Payload)) (counter : Nat) :
    (guardedTrace sp keep commands counter).variants = (lateTrace sp keep commands counter).variants ∧
    (guardedTrace sp keep commands counter).counter = (lateTrace sp keep commands counter).counter := by
  induction commands generalizing counter with
  | nil => exact ⟨rfl,rfl⟩
  | cons command rest ih =>
      cases command with
      | bindQuery q items =>
          have first := guarded_sink_observation_equal sp q keep items counter
          have tail := ih (lateSink sp q keep items counter).counter
          simp [guardedTrace,lateTrace,first.1,first.2,tail.1,tail.2]
      | cached q rows =>
          have tail := ih counter
          simp [guardedTrace,lateTrace,tail.1,tail.2]

def mixedCounterTrace : List (TraceCommand Nat Nat Nat Nat) :=
  [.bindQuery qFull fallbackItems, .bindQuery qGroup explicitItems,
   .cached qFull [⟨⟨some 7,qFull,0,30⟩,.explicit 300⟩],
   .bindQuery qFull [⟨⟨some 7,40⟩,.fallback⟩]]

theorem mixed_query_counter_and_cache_control :
    (guardedTrace oneSpecializer exactPolicy mixedCounterTrace 0).variants.map (·.reportId) =
      [.generated 2,.explicit 200,.explicit 300,.generated 3] ∧
    (guardedTrace oneSpecializer exactPolicy mixedCounterTrace 0).counter = 3 ∧
    (guardedTrace oneSpecializer exactPolicy mixedCounterTrace 0).binderCalls = 4 ∧
    (lateTrace oneSpecializer exactPolicy mixedCounterTrace 0).binderCalls = 5 := by decide

end ApSpec.RuleSpecializationSelection
