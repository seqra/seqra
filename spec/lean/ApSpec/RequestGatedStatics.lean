/-
  Local executable model for request-gated run-1 statics.

  This module deliberately does not change the historical F78 modules. Its
  demand inputs are effective positive mark leaves after frontend signed
  condition normalization, not an interpretation of mark absence. Read
  demands and mark-only demands occupy separate inventory columns. Run 1 has
  no computational S-root initial. Its weak/bypass transfers overapproximate
  the independently defined strong transfers. Later summaries below are exact
  relations of finite returning bodies, including a separate zero-GEN origin.

  Not proved here: JVM effective-form coverage, packed AP encoding, actual
  the recursive query solver, global paired closure, ND confirmation, or concurrency.
  In particular, a strong identity summary is never invented from a weak F1
  bypass or from absence of a prior summary.
-/
import ApSpec.Run1InitialShapes
import ApSpec.Run1RequestsSafety

namespace ApSpec.RequestGatedStatics
open ApSpec ApSpec.Run1Requests

abbrev Field := Nat × Nat

namespace InitialGrammar
open Run1InitialShapes

/-- Selected nine families; the historical abstract-root constructor is absent. -/
inductive Shape where
  | zero
  | abstractBase (base : Nat)
  | abstractField (cls field : Nat)
  | baseExact (base mark : Nat)
  | baseChain (base mark : Nat)
  | fieldExact (cls field mark : Nat)
  | fieldChain (cls field mark : Nat)
  | classExact (cls mark : Nat)
  | classChain (cls mark : Nat)
  deriving DecidableEq, Repr

def oldShape : Shape → Run1InitialShapes.Shape
  | .zero => .zero
  | .abstractBase b => .abstractBase b
  | .abstractField c f => .abstractField c f
  | .baseExact b t => .baseExact b t
  | .baseChain b t => .baseChain b t
  | .fieldExact c f t => .fieldExact c f t
  | .fieldChain c f t => .fieldChain c f t
  | .classExact c t => .classExact c t
  | .classChain c t => .classChain c t

def encode (s : Shape) : RawInitial := Run1InitialShapes.encode (oldShape s)
def toAP (s : Shape) : PFact := (oldShape s).toAP

def fromOld : (old : Run1InitialShapes.Shape) → Option {s : Shape // oldShape s = old}
  | .zero => some ⟨.zero,rfl⟩
  | .abstractBase b => some ⟨.abstractBase b,rfl⟩
  | .abstractRoot => none
  | .abstractField c f => some ⟨.abstractField c f,rfl⟩
  | .baseExact b t => some ⟨.baseExact b t,rfl⟩
  | .baseChain b t => some ⟨.baseChain b t,rfl⟩
  | .fieldExact c f t => some ⟨.fieldExact c f t,rfl⟩
  | .fieldChain c f t => some ⟨.fieldChain c f t,rfl⟩
  | .classExact c t => some ⟨.classExact c t,rfl⟩
  | .classChain c t => some ⟨.classChain c t,rfl⟩

def classify (raw : RawInitial) : Option {s : Shape // encode s = raw} :=
  match Run1InitialShapes.classify raw with
  | none => none
  | some old => match fromOld old.val with
    | none => none
    | some s => some ⟨s.val,by rw [encode,s.property]; exact old.property⟩

def Legal (raw : RawInitial) : Prop := ∃ s : Shape, encode s = raw
def LegalAP (fact : PFact) : Prop := ∃ s : Shape, toAP s = fact

theorem classify_encode (s : Shape) : (classify (encode s)).isSome = true := by
  cases s <;> rfl

theorem classification_exact (raw : RawInitial) : (classify raw).isSome = true ↔ Legal raw := by
  constructor
  · intro h
    cases hc : classify raw with
    | none => simp [hc] at h
    | some witness => exact ⟨witness.val,witness.property⟩
  · rintro ⟨s,rfl⟩
    exact classify_encode s

theorem legal_empty_E (raw : RawInitial) (h : Legal raw) : raw.exclusion = .empty := by
  obtain ⟨s,rfl⟩ := h
  exact Run1InitialShapes.legal_empty_E _ ⟨oldShape s,rfl⟩

theorem legal_no_ANY (raw : RawInitial) (h : Legal raw) : raw.tail ≠ .any := by
  obtain ⟨s,rfl⟩ := h
  exact Run1InitialShapes.legal_no_any _ ⟨oldShape s,rfl⟩

theorem legal_no_must (raw : RawInitial) (h : Legal raw) : raw.tail ≠ .must := by
  obtain ⟨s,rfl⟩ := h
  exact Run1InitialShapes.legal_no_must _ ⟨oldShape s,rfl⟩

theorem legalAP_no_ANY (fact : PFact) (h : LegalAP fact) : fact.kind ≠ .any := by
  obtain ⟨s,rfl⟩ := h
  exact Run1InitialShapes.legalAP_no_any _ ⟨oldShape s,rfl⟩

theorem old_root_encoding_exact (s : Run1InitialShapes.Shape) :
    s.toAP = specialRoot ↔ s = .abstractRoot := by
  cases s <;> simp [Run1InitialShapes.Shape.toAP,RawInitial.toAP,
    Run1InitialShapes.encode,Place.base,Place.path,Point.base,Point.path,specialRoot]

theorem oldShape_not_root (s : Shape) : oldShape s ≠ .abstractRoot := by
  cases s <;> simp [oldShape]

theorem legalAP_no_root (fact : PFact) (h : LegalAP fact) : fact ≠ specialRoot := by
  obtain ⟨s,rfl⟩ := h
  intro root
  exact oldShape_not_root s ((old_root_encoding_exact _).mp root)

theorem old_nonroot_has_new (s : Run1InitialShapes.Shape) (h : s ≠ .abstractRoot) :
    ∃ next : Shape, oldShape next = s := by
  cases s with
  | zero => exact ⟨.zero,rfl⟩
  | abstractBase b => exact ⟨.abstractBase b,rfl⟩
  | abstractRoot => exact (h rfl).elim
  | abstractField c f => exact ⟨.abstractField c f,rfl⟩
  | baseExact b t => exact ⟨.baseExact b t,rfl⟩
  | baseChain b t => exact ⟨.baseChain b t,rfl⟩
  | fieldExact c f t => exact ⟨.fieldExact c f t,rfl⟩
  | fieldChain c f t => exact ⟨.fieldChain c f t,rfl⟩
  | classExact c t => exact ⟨.classExact c t,rfl⟩
  | classChain c t => exact ⟨.classChain c t,rfl⟩

theorem exactly_old_grammar_minus_root (fact : PFact) :
    LegalAP fact ↔ Run1InitialShapes.LegalAP fact ∧ fact ≠ specialRoot := by
  constructor
  · intro h
    refine ⟨?_,legalAP_no_root fact h⟩
    obtain ⟨s,hs⟩ := h
    exact ⟨oldShape s,hs⟩
  · rintro ⟨⟨s,hs⟩,notRoot⟩
    have notShape : s ≠ .abstractRoot := by
      intro eq
      apply notRoot
      rw [← hs,eq]
      rfl
    obtain ⟨next,hn⟩ := old_nonroot_has_new s notShape
    refine ⟨next,?_⟩
    change (oldShape next).toAP = fact
    rw [hn]
    exact hs

theorem started_layer (shape : Shape) :
    (startFact (toAP shape)).demand = Run1InitialShapes.demandStart (oldShape shape) :=
  Run1InitialShapes.started_layer (oldShape shape)

theorem started_geometry (shape : Shape) :
    (startFact (toAP shape)).fact.base = (toAP shape).base ∧
      (startFact (toAP shape)).fact.path = (toAP shape).path ∧
      (startFact (toAP shape)).fact.mark = (toAP shape).mark :=
  Run1InitialShapes.started_geometry (oldShape shape)

def samples : List Shape := [.zero,.abstractBase 2,.abstractField 3 2,
  .baseExact 2 8,.baseChain 2 8,.fieldExact 3 2 8,.fieldChain 3 2 8,
  .classExact 3 8,.classChain 3 0]

theorem nine_sample_families : samples.length = 9 := rfl
theorem all_sample_factories_classify : samples.all (fun s => (classify (encode s)).isSome) = true := by decide

end InitialGrammar

inductive Demand where
  | read (field : Field)
  | containsMark (scope : Scope) (mark : Mark)
  | excludedTarget (scope : Scope) (marks : List Mark)
  | both (left right : Demand)
  deriving DecidableEq, Repr

def demandForm : Demand → Form
  | .read (c,f) => .code c f
  | .containsMark scope mark => .marked .inputGate scope mark
  | .excludedTarget scope marks => .excludedTarget scope marks
  | .both a b => .both (demandForm a) (demandForm b)

def inventory (demands : List Demand) : List Need :=
  direct (demands.map demandForm)

def fieldReads (needs : List Need) : List Field := needs.filterMap fun need =>
  match need with
  | .structural c f => some (c,f)
  | _ => none

def markRows (needs : List Need) (actual : List Field) : List (Slot × Mark) :=
  activate needs (actual.map fun f => .field f.1 f.2)

theorem read_adds_field_only (f : Field) :
    fieldReads (inventory [.read f]) = [f] ∧
      markRows (inventory [.read f]) [f] = [] := by
  rcases f with ⟨c,f⟩
  simp [fieldReads, inventory, direct, demandForm, collect, markRows, activate]

theorem containsMark_does_not_add_field (scope : Scope) (t : Mark) :
    fieldReads (inventory [.containsMark scope t]) = [] := by
  simp [fieldReads, inventory, direct, demandForm, collect]

theorem containsMark_adds_covered_mark (scope : Scope) (f : Field) (t : Mark)
    (covered : scope.covers (.field f.1 f.2) = true) :
    (.field f.1 f.2,t) ∈ markRows (inventory [.containsMark scope t]) [f] := by
  apply wide_activation_complete _ _ scope t _
  · simp [inventory, direct, demandForm, collect]
  · simp
  · exact covered

theorem excludedTarget_does_not_add_field (scope : Scope) (marks : List Mark) :
    fieldReads (inventory [.excludedTarget scope marks]) = [] := by
  simp [fieldReads, inventory, direct, demandForm, collect, List.filterMap_map]

theorem fieldReads_append (a b : List Need) :
    fieldReads (a ++ b) = fieldReads a ++ fieldReads b := by
  simp [fieldReads]

theorem extra_mark_leaf_preserves_reads (demands : List Demand) (scope : Scope) (t : Mark) :
    fieldReads (inventory (demands ++ [.containsMark scope t])) =
      fieldReads (inventory demands) := by
  simp [inventory, direct, List.flatMap_append,
    demandForm, collect, fieldReads]

def inherited {n : Nat} (graph : Graph n) (demands : Fin n → List Demand)
    (owner : Fin n) : List Need := plan graph (fun m => (demands m).map demandForm) owner

/-- Even under recursive call propagation, adding mark-only leaves anywhere
    cannot create field-read activations. The graph itself is unchanged. -/
theorem transitive_mark_only_preserves_reads {n : Nat} (graph : Graph n)
    (demands : Fin n → List Demand) (scopes : Fin n → Scope) (marks : Fin n → Mark)
    (owner : Fin n) :
    fieldReads (inherited graph
      (fun m => demands m ++ [.containsMark (scopes m) (marks m)]) owner) =
      fieldReads (inherited graph demands owner) := by
  unfold inherited plan
  simp only [fieldReads, List.filterMap_flatMap]
  apply congrArg (List.flatMap · (List.finRange n))
  funext method
  split
  · exact extra_mark_leaf_preserves_reads (demands method) (scopes method) (marks method)
  · rfl

theorem recursive_read_inherited {n : Nat} (graph : Graph n)
    (demands : Fin n → List Demand) (owner producer : Fin n) (f : Field)
    (reached : Reaches graph producer owner) (present : Demand.read f ∈ demands producer) :
    Need.structural f.1 f.2 ∈ inherited graph demands owner := by
  apply plan_contains_reachable graph _ owner producer _ reached
  apply List.mem_flatMap.mpr
  refine ⟨demandForm (.read f), List.mem_map.mpr ⟨.read f,present,rfl⟩, ?_⟩
  rcases f with ⟨c,f⟩
  simp [demandForm, collect]

theorem recursive_mark_inherited {n : Nat} (graph : Graph n)
    (demands : Fin n → List Demand) (owner producer : Fin n) (scope : Scope) (t : Mark)
    (reached : Reaches graph producer owner)
    (present : Demand.containsMark scope t ∈ demands producer) :
    Need.marked scope t ∈ inherited graph demands owner := by
  exact plan_contains_reachable graph _ owner producer _ reached
    (producer_in_direct _ .inputGate scope t (List.mem_map.mpr ⟨_,present,rfl⟩))

/-! The two F1 gates are independent: an R-only need never starts abstract F. -/

def f1Starts (reads : List Field) (requests : List (Slot × Mark))
    (field : Field) (supplier : Leaf) : List PFact :=
  let slot := Slot.field field.1 field.2
  (if field ∈ reads ∧ slotMatches slot supplier = true then [slot.initial] else []) ++
    (if (slot,supplier.mark) ∈ requests then (project slot supplier).toList else [])

theorem abstract_start_iff (reads : List Field) (requests : List (Slot × Mark))
    (field : Field) (supplier : Leaf) :
    (Slot.field field.1 field.2).initial ∈ f1Starts reads requests field supplier ↔
      field ∈ reads ∧ slotMatches (.field field.1 field.2) supplier = true := by
  have notConcrete : (Slot.field field.1 field.2).initial ∉
      (project (.field field.1 field.2) supplier).toList := by
    intro h
    have hp := Option.mem_toList.mp h
    have hm := projector_keeps_mark _ _ _ hp
    simp [Slot.initial] at hm
  simp only [f1Starts, List.mem_append]
  split <;> split <;> simp_all

theorem concrete_start_iff (reads : List Field) (requests : List (Slot × Mark))
    (field : Field) (supplier : Leaf) (answer : PFact)
    (concreteMark : answer.mark = .conc supplier.mark) :
    answer ∈ f1Starts reads requests field supplier ↔
      (.field field.1 field.2,supplier.mark) ∈ requests ∧
        project (.field field.1 field.2) supplier = some answer := by
  have notAbstract : answer ≠ (Slot.field field.1 field.2).initial := by
    intro h
    have hm := congrArg PFact.mark h
    simp [concreteMark, Slot.initial] at hm
  simp only [f1Starts, List.mem_append]
  split <;> split <;> simp_all

theorem project_field_path (f : Field) (supplier : Leaf) (answer : PFact)
    (h : project (.field f.1 f.2) supplier = some answer) :
    answer.base = 1 ∧ answer.path = [2*f.1,2*f.2+1] := by
  unfold project at h
  split at h
  · cases h
  · simp only [Option.some.injEq] at h
    subst answer
    exact ⟨rfl,rfl⟩

theorem f1_no_root_initial (reads : List Field) (requests : List (Slot × Mark))
    (f : Field) (supplier : Leaf) (answer : PFact)
    (h : answer ∈ f1Starts reads requests f supplier) :
    answer.path = [2*f.1,2*f.2+1] := by
  simp only [f1Starts, List.mem_append] at h
  rcases h with h | h
  · split at h
    · simp only [List.mem_singleton] at h
      subst answer
      rfl
    · simp at h
  · split at h
    · exact (project_field_path f supplier answer (Option.mem_toList.mp h)).2
    · simp at h

theorem f1_never_emits_special_root (reads : List Field) (requests : List (Slot × Mark))
    (f : Field) (supplier : Leaf) : specialRoot ∉ f1Starts reads requests f supplier := by
  intro h
  have hp := f1_no_root_initial reads requests f supplier specialRoot h
  simp [specialRoot] at hp

theorem f1_no_ANY_initial (reads : List Field) (requests : List (Slot × Mark))
    (f : Field) (supplier : Leaf) (answer : PFact)
    (h : answer ∈ f1Starts reads requests f supplier) : answer.kind ≠ .any := by
  simp only [f1Starts, List.mem_append] at h
  rcases h with h | h
  · split at h
    · simp only [List.mem_singleton] at h
      subst answer
      simp [Slot.initial]
    · simp at h
  · split at h
    · exact Run1InitialShapes.request_projector_no_any _ _ _ (Option.mem_toList.mp h)
    · simp at h

theorem f1_initials_in_nine_family_grammar (reads : List Field)
    (requests : List (Slot × Mark)) (f : Field) (supplier : Leaf) (answer : PFact)
    (h : answer ∈ f1Starts reads requests f supplier) : InitialGrammar.LegalAP answer := by
  apply (InitialGrammar.exactly_old_grammar_minus_root answer).mpr
  refine ⟨?_,?_⟩
  · simp only [f1Starts, List.mem_append] at h
    rcases h with h | h
    · split at h
      · simp only [List.mem_singleton] at h
        subst answer
        exact Run1InitialShapes.slot_initial_legal _
      · simp at h
    · split at h
      · exact Run1InitialShapes.request_projector_legal _ _ _ (Option.mem_toList.mp h)
      · simp at h
  · intro root
    subst answer
    exact f1_never_emits_special_root reads requests f supplier h

theorem concrete_output_uses_actual_mark (reads : List Field)
    (requests : List (Slot × Mark)) (f : Field) (supplier : Leaf) (answer : PFact)
    (t : Mark) (h : answer ∈ f1Starts reads requests f supplier)
    (concreteOutput : answer.mark = .conc t) : t = supplier.mark := by
  simp only [f1Starts, List.mem_append] at h
  rcases h with h | h
  · split at h
    · simp only [List.mem_singleton] at h
      subst answer
      simp [Slot.initial] at concreteOutput
    · simp at h
  · split at h
    · have actual := projector_keeps_mark _ _ _ (Option.mem_toList.mp h)
      rw [concreteOutput] at actual
      exact MarkA.conc.inj actual
    · simp at h

/-! Independently executable strong and weak transfer semantics. -/

abbrev Component := Run1RequestsSafety.Component

inductive Point where
  | zero
  | fact (component : Component)
  deriving DecidableEq, Repr

inductive Op where
  | keep
  | copy (source target : Field)
  | kill (target : Field)
  | clean (target : Field) (mark : Mark)
  | generate (target : Field) (mark : Mark) (demand : Bool)
  | containsMark (target : Field) (mark : Mark)
  deriving DecidableEq, Repr

def generated (f : Field) (t : Mark) (d : Bool) : Point := .fact ⟨f,[],t,d⟩

def strong (op : Op) : Point → List Point
  | .zero => .zero :: (match op with | .generate f t d => [generated f t d] | _ => [])
  | .fact p => match op with
    | .keep | .containsMark _ _ => [.fact p]
    | .kill f | .generate f _ _ => if p.field = f then [] else [.fact p]
    | .clean f t => if p.field = f ∧ p.mark = t then [] else [.fact p]
    | .copy src dst =>
      (if p.field = dst then [] else [.fact p]) ++
      (if p.field = src then [.fact {p with field := dst}] else [])

def weak (op : Op) : Point → List Point
  | .zero => .zero :: (match op with | .generate f t d => [generated f t d] | _ => [])
  | .fact p => match op with
    | .clean f t => if p.field = f ∧ p.mark = t then [] else [.fact p]
    | .copy src dst => .fact p ::
      (if p.field = src then [.fact {p with field := dst}] else [])
    | _ => [.fact p]

theorem strong_step_in_weak (op : Op) (input output : Point)
    (h : output ∈ strong op input) : output ∈ weak op input := by
  cases input with
  | zero => exact h
  | fact p =>
    cases op <;> simp only [strong, weak] at *
    · exact h
    · split at h <;> simp_all
    · split at h <;> simp_all
    · exact h
    · split at h <;> simp_all
    · exact h

def execute (step : Op → Point → List Point) : List Op → Point → List Point
  | [], input => [input]
  | op::ops, input => (step op input).flatMap (execute step ops)

theorem strong_trace_in_weak (ops : List Op) (input output : Point)
    (h : output ∈ execute strong ops input) : output ∈ execute weak ops input := by
  induction ops generalizing input with
  | nil => exact h
  | cons op ops ih =>
    obtain ⟨middle, hm, ho⟩ := List.mem_flatMap.mp h
    exact List.mem_flatMap.mpr ⟨middle,strong_step_in_weak op input middle hm,ih middle ho⟩

theorem strong_preserves_layer (op : Op) (input output : Component)
    (h : .fact output ∈ strong op (.fact input)) : output.demand = input.demand := by
  cases op with
  | keep =>
    exact congrArg Run1RequestsSafety.Component.demand (Point.fact.inj (List.mem_singleton.mp h))
  | containsMark =>
    exact congrArg Run1RequestsSafety.Component.demand (Point.fact.inj (List.mem_singleton.mp h))
  | kill f => simp only [strong] at h; split at h <;> simp_all
  | clean f t => simp only [strong] at h; split at h <;> simp_all
  | generate f t d => simp only [strong] at h; split at h <;> simp_all
  | copy src dst =>
    simp only [strong, List.mem_append] at h
    rcases h with h | h
    · split at h <;> simp_all
    · split at h <;> simp_all

def callBypass (input : Point) : List Point := [input]

theorem weak_bypass (input : Point) : input ∈ callBypass input := by simp [callBypass]

theorem weak_clean_is_strong (f : Field) (t : Mark) (input : Point) :
    weak (.clean f t) input = strong (.clean f t) input := by cases input <;> rfl

theorem copy_exact (src dst : Field) (p : Component) (output : Point) :
    output ∈ strong (.copy src dst) (.fact p) ↔
      (p.field ≠ dst ∧ output = .fact p) ∨
      (p.field = src ∧ output = .fact {p with field := dst}) := by
  simp only [strong, List.mem_append]
  split <;> split <;> simp_all

theorem kill_exact (f : Field) (p : Component) :
    strong (.kill f) (.fact p) = if p.field = f then [] else [.fact p] := rfl

theorem zero_generation (f : Field) (t : Mark) (d : Bool) :
    strong (.generate f t d) .zero = [.zero,generated f t d] := rfl

theorem no_generation_per_input (f : Field) (t : Mark) (d : Bool) (p : Component)
    (output : Point) (h : output ∈ strong (.generate f t d) (.fact p)) :
    output = .fact p ∧ p.field ≠ f := by
  simp only [strong] at h
  split at h <;> simp_all

theorem selected_cleaner_removes_T (f : Field) (t : Mark) (suffix : List Acc) (d : Bool) :
    strong (.clean f t) (.fact ⟨f,suffix,t,d⟩) = [] := by simp [strong]

theorem selected_cleaner_preserves_U (f : Field) (t u : Mark) (suffix : List Acc)
    (d : Bool) (different : u ≠ t) :
    strong (.clean f t) (.fact ⟨f,suffix,u,d⟩) = [.fact ⟨f,suffix,u,d⟩] := by
  simp [strong, different]

theorem strong_write_preserves_sibling (f g : Field) (p : Component)
    (atSibling : p.field = g) (different : g ≠ f) :
    strong (.kill f) (.fact p) = [.fact p] := by simp [strong, atSibling, different]

/-! Later S→S is the actual strong returning relation, not an identity axiom. -/

inductive Body where
  | ret
  | abort
  | step (op : Op) (next : Body)
  | branch (left right : Body)
  deriving DecidableEq, Repr

def run (step : Op → Point → List Point) : Body → Point → List Point
  | .ret, p => [p]
  | .abort, _ => []
  | .step op next, p => (step op p).flatMap (run step next)
  | .branch left right, p => run step left p ++ run step right p

inductive ActualReturn : Body → Point → Point → Prop
  | ret (p) : ActualReturn .ret p p
  | step {op next input middle output} : middle ∈ strong op input →
      ActualReturn next middle output → ActualReturn (.step op next) input output
  | left {l r input output} : ActualReturn l input output → ActualReturn (.branch l r) input output
  | right {l r input output} : ActualReturn r input output → ActualReturn (.branch l r) input output

theorem output_iff_actual_return (body : Body) (input output : Point) :
    output ∈ run strong body input ↔ ActualReturn body input output := by
  constructor
  · induction body generalizing input with
    | ret =>
      intro h
      have eq : output = input := by simpa [run] using h
      subst output
      exact ActualReturn.ret input
    | abort => simp [run]
    | step op next ih =>
      intro h
      obtain ⟨middle, hm, ho⟩ := List.mem_flatMap.mp h
      exact .step hm (ih middle ho)
    | branch l r il ir =>
      intro h
      rcases List.mem_append.mp h with h | h
      · exact .left (il input h)
      · exact .right (ir input h)
  · intro h
    induction h with
    | ret => simp [run]
    | step hm _ ih => exact List.mem_flatMap.mpr ⟨_,hm,ih⟩
    | left _ ih => exact List.mem_append_left _ ih
    | right _ ih => exact List.mem_append_right _ ih

theorem abort_no_summary (input output : Point) : output ∉ run strong .abort input := by
  simp [run]

theorem identity_only_on_return (input output : Point) :
    output ∈ run strong .ret input ↔ output = input := by simp [run]

theorem strong_write_then_read_has_no_input_edge (f g : Field) (p : Component)
    (atWritten : p.field = f) :
    run strong (.step (.kill f) (.step (.copy f g) .ret)) (.fact p) = [] := by
  simp [run, strong, atWritten]

theorem zero_generation_then_read (f g : Field) (t : Mark) (d : Bool) :
    generated g t d ∈ run strong (.step (.generate f t d) (.step (.copy f g) .ret)) .zero := by
  simp [run, strong, generated]

theorem strong_body_in_weak (body : Body) (input output : Point)
    (h : output ∈ run strong body input) : output ∈ run weak body input := by
  induction body generalizing input with
  | ret => exact h
  | abort => exact h
  | step op next ih =>
    obtain ⟨middle, hm, ho⟩ := List.mem_flatMap.mp h
    exact List.mem_flatMap.mpr ⟨middle,strong_step_in_weak op input middle hm,ih middle ho⟩
  | branch l r il ir =>
    rcases List.mem_append.mp h with h | h
    · exact List.mem_append_left _ (il input h)
    · exact List.mem_append_right _ (ir input h)

def spatialRelation (body : Body) (pattern : Leaf) (input : Component) (output : Point) : Prop :=
  Run1RequestsSafety.contains pattern input = true ∧
    output ∈ run strong body (.fact input)

def oppositeRelation (body : Body) (pattern : Leaf) (output : Point) (input : Component) : Prop :=
  spatialRelation body pattern input output

theorem ordinary_spatial_E_retained (body : Body) (pattern : Leaf)
    (input : Component) (output : Point) (h : spatialRelation body pattern input output) :
    Run1RequestsSafety.contains pattern input = true := h.1

theorem converse_exact (body : Body) (pattern : Leaf) (input : Component) (output : Point) :
    oppositeRelation body pattern output input ↔ spatialRelation body pattern input output := Iff.rfl

theorem opposite_has_actual_return (body : Body) (pattern : Leaf)
    (input : Component) (output : Point) (h : oppositeRelation body pattern output input) :
    ActualReturn body (.fact input) output := (output_iff_actual_return _ _ _).mp h.2

/-! A class point is a concrete atom, not a new abstract request slot. The
    ordinary E/tail predicate is reused; exact empty S is not an admitted atom. -/

inductive StaticAtom where
  | field (component : Component)
  | classPoint (cls : Nat) (mark : Mark) (demand : Bool)
  deriving DecidableEq, Repr

def atomContains (pattern : Leaf) : StaticAtom → Bool
  | .field p => Run1RequestsSafety.contains pattern p
  | .classPoint c t _ => pattern.mark == t && match pattern.point with
    | .root => Run1RequestsSafety.tailAt pattern [2*c]
    | .cls cls => cls == c && Run1RequestsSafety.tailAt pattern []
    | _ => false

theorem exact_empty_root_not_an_atom (t : Mark) (e : Excl) (d : Bool) (a : StaticAtom) :
    atomContains ⟨.root,.exact,t,e,d⟩ a = false := by
  cases a with
  | field p => simp [atomContains, Run1RequestsSafety.contains,
      Run1RequestsSafety.tailAt, Run1RequestsSafety.path]
  | classPoint => simp [atomContains, Run1RequestsSafety.tailAt]

theorem exact_class_point_preserved (c : Nat) (t : Mark) (e : Excl) (d d' : Bool) :
    atomContains ⟨.cls c,.exact,t,e,d⟩ (.classPoint c t d') = true := by
  change (decide (t = t) && (decide (c = c) && true)) = true
  exact (Bool.and_eq_true _ _).mpr ⟨decide_eq_true rfl,
    (Bool.and_eq_true _ _).mpr ⟨decide_eq_true rfl,rfl⟩⟩

theorem broad_root_preserves_class_point (c : Nat) (t : Mark) (d d' : Bool) :
    atomContains ⟨.root,.any,t,.empty,d⟩ (.classPoint c t d') = true := by
  change (decide (t = t) && true) = true
  exact (Bool.and_eq_true _ _).mpr ⟨decide_eq_true rfl,rfl⟩

/-! Later admission is query-led, independent of prior F1 availability. The
    finite candidate candidates is explicit; it is not a blanket S-root initial. -/

structure Query where
  owner : Owner
  run : Nat
  backward : Bool
  anchor : Point
  ordinaryPattern : Option Leaf
  deriving DecidableEq, Repr

def admits (pattern : Option Leaf) : Point → Bool
  | .zero => pattern.isNone
  | .fact input => match pattern with
    | none => true
    | some leaf => Run1RequestsSafety.contains leaf input

def queryMatches (body : Body) (query : Query) (candidate : Point) : Bool :=
  if query.backward then
    admits query.ordinaryPattern candidate && decide (query.anchor ∈ run strong body candidate)
  else
    admits query.ordinaryPattern query.anchor && decide (candidate ∈ run strong body query.anchor)

def queryAnswers (candidates : List Point) (body : Body) (query : Query) : List Point :=
  candidates.filter (queryMatches body query)

theorem forward_answers_exact (candidates : List Point) (body : Body) (query : Query)
    (forward : query.backward = false) (candidate : Point) :
    candidate ∈ queryAnswers candidates body query ↔
      candidate ∈ candidates ∧ admits query.ordinaryPattern query.anchor = true ∧
        ActualReturn body query.anchor candidate := by
  simp only [queryAnswers, List.mem_filter, queryMatches, forward, Bool.false_eq_true,
    ↓reduceIte, Bool.and_eq_true, decide_eq_true_eq, output_iff_actual_return]

theorem backward_answers_exact (candidates : List Point) (body : Body) (query : Query)
    (backward : query.backward = true) (candidate : Point) :
    candidate ∈ queryAnswers candidates body query ↔
      candidate ∈ candidates ∧ admits query.ordinaryPattern candidate = true ∧
        ActualReturn body candidate query.anchor := by
  simp only [queryAnswers, List.mem_filter, queryMatches, backward, ↓reduceIte,
    Bool.and_eq_true, decide_eq_true_eq, output_iff_actual_return]

def registerQuery (pending : List Query) (query : Query) : List Query :=
  if query ∈ pending then pending else pending ++ [query]

/-- Prior guidance is deliberately not consulted for admission. It may affect
    ordering, but cannot license an executable edge or make a real query vanish. -/
def admitActualQuery (_priorGuidance : List (Point × Point))
    (pending : List Query) (query : Query) : List Query := registerQuery pending query

theorem actual_query_admitted (guidance : List (Point × Point)) (pending : List Query)
    (query : Query) : query ∈ admitActualQuery guidance pending query := by
  simp only [admitActualQuery, registerQuery]
  split <;> simp_all

theorem query_admission_independent_of_F1 (first second : List (Point × Point))
    (pending : List Query) (query : Query) :
    admitActualQuery first pending query = admitActualQuery second pending query := rfl

theorem query_registration_idempotent (pending : List Query) (query : Query) :
    registerQuery (registerQuery pending query) query = registerQuery pending query := by
  have h := actual_query_admitted [] pending query
  simp only [admitActualQuery] at h
  change (if query ∈ registerQuery pending query then registerQuery pending query
    else registerQuery pending query ++ [query]) = registerQuery pending query
  exact if_pos h

inductive Knowledge where
  | pending
  | complete (answers : List Point)
  deriving DecidableEq, Repr

theorem unknown_not_empty : Knowledge.pending ≠ .complete [] := by decide
theorem unknown_not_identity (p : Point) : Knowledge.pending ≠ .complete [p] := by
  intro h; cases h

theorem completed_query_has_no_fake_edges (candidates : List Point) (body : Body)
    (query : Query) (candidate : Point) (h : candidate ∈ queryAnswers candidates body query) :
    if query.backward then ActualReturn body candidate query.anchor
    else ActualReturn body query.anchor candidate := by
  cases direction : query.backward
  · exact ((forward_answers_exact candidates body query direction candidate).mp h).2.2
  · exact ((backward_answers_exact candidates body query direction candidate).mp h).2.2

/-! Finite balanced calls, including recursion. The state type is generic so
    a static route can pass through local/argument/return states. Procedures
    are indexed full owners; concrete bind-in/out effects are ordinary steps.
    No inference is enabled merely by a pending query or an F1 record. -/

namespace BalancedCalls

inductive Code (n : Nat) (State : Type) where
  | ret
  | abort
  | step (effect : State → List State) (next : Code n State)
  | call (callee : Fin n) (next : Code n State)
  | branch (left right : Code n State)

def eval {n : Nat} {State : Type} (fuel : Nat) (program : Fin n → Code n State)
    (code : Code n State) (input : State) : List State := match fuel with
  | 0 => []
  | fuel+1 => match code with
    | .ret => [input]
    | .abort => []
    | .step effect next => (effect input).flatMap (eval fuel program next)
    | .call callee next =>
      (eval fuel program (program callee) input).flatMap (eval fuel program next)
    | .branch left right => eval fuel program left input ++ eval fuel program right input

inductive Derives {n : Nat} {State : Type} (program : Fin n → Code n State) :
    Code n State → State → State → Prop
  | ret (p) : Derives program .ret p p
  | step {effect next input middle output} : middle ∈ effect input →
      Derives program next middle output → Derives program (.step effect next) input output
  | call {callee next input middle output} :
      Derives program (program callee) input middle →
      Derives program next middle output → Derives program (.call callee next) input output
  | left {left right input output} : Derives program left input output →
      Derives program (.branch left right) input output
  | right {left right input output} : Derives program right input output →
      Derives program (.branch left right) input output

theorem eval_sound {n : Nat} {State : Type} (fuel : Nat)
    (program : Fin n → Code n State) (code : Code n State) (input output : State)
    (h : output ∈ eval fuel program code input) : Derives program code input output := by
  induction fuel generalizing code input output with
  | zero => simp [eval] at h
  | succ fuel ih =>
    cases code with
    | ret =>
      have eq : output = input := by simpa [eval] using h
      subst output
      exact .ret input
    | abort => simp [eval] at h
    | step effect next =>
      obtain ⟨middle, hm, ho⟩ := List.mem_flatMap.mp h
      exact .step hm (ih next middle output ho)
    | call callee next =>
      obtain ⟨middle, hm, ho⟩ := List.mem_flatMap.mp h
      exact .call (ih (program callee) input middle hm) (ih next middle output ho)
    | branch l r =>
      rcases List.mem_append.mp h with h | h
      · exact .left (ih l input output h)
      · exact .right (ih r input output h)

theorem eval_step_mono {n : Nat} {State : Type} (fuel : Nat)
    (program : Fin n → Code n State) (code : Code n State) (input output : State)
    (h : output ∈ eval fuel program code input) :
    output ∈ eval (fuel+1) program code input := by
  induction fuel generalizing code input output with
  | zero => simp [eval] at h
  | succ fuel ih =>
    cases code with
    | ret => exact h
    | abort => exact h
    | step effect next =>
      obtain ⟨middle, hm, ho⟩ := List.mem_flatMap.mp h
      exact List.mem_flatMap.mpr ⟨middle,hm,ih next middle output ho⟩
    | call callee next =>
      obtain ⟨middle, hm, ho⟩ := List.mem_flatMap.mp h
      exact List.mem_flatMap.mpr ⟨middle,ih (program callee) input middle hm,ih next middle output ho⟩
    | branch l r =>
      rcases List.mem_append.mp h with h | h
      · exact List.mem_append_left _ (ih l input output h)
      · exact List.mem_append_right _ (ih r input output h)

theorem eval_mono {n : Nat} {State : Type} (low high : Nat)
    (program : Fin n → Code n State) (code : Code n State) (input output : State)
    (bound : low ≤ high) (h : output ∈ eval low program code input) :
    output ∈ eval high program code input := by
  obtain ⟨extra,rfl⟩ := Nat.exists_eq_add_of_le bound
  induction extra with
  | zero => simpa using h
  | succ extra ih => exact eval_step_mono (low+extra) program code input output (ih (by omega))

/-- The existence witness is built from the finite balanced derivation. No
    summary-completeness or acyclic-callgraph hypothesis is assumed. -/
theorem eval_complete {n : Nat} {State : Type} (program : Fin n → Code n State)
    (code : Code n State) (input output : State) (h : Derives program code input output) :
    ∃ fuel, output ∈ eval fuel program code input := by
  induction h with
  | ret => exact ⟨1,by simp [eval]⟩
  | step hm _ ih =>
    obtain ⟨fuel,hf⟩ := ih
    exact ⟨fuel+1,List.mem_flatMap.mpr ⟨_,hm,hf⟩⟩
  | @call callee next input middle output _ _ first second =>
    obtain ⟨f,hf⟩ := first
    obtain ⟨g,hg⟩ := second
    refine ⟨max f g+1,List.mem_flatMap.mpr ⟨middle,?_,?_⟩⟩
    · exact eval_mono f (max f g) program (program callee) input middle (Nat.le_max_left _ _) hf
    · exact eval_mono g (max f g) program next middle output (Nat.le_max_right _ _) hg
  | left _ ih =>
    obtain ⟨fuel,hf⟩ := ih
    exact ⟨fuel+1,List.mem_append_left _ hf⟩
  | right _ ih =>
    obtain ⟨fuel,hf⟩ := ih
    exact ⟨fuel+1,List.mem_append_right _ hf⟩

theorem balanced_query_exact {n : Nat} {State : Type} (program : Fin n → Code n State)
    (code : Code n State) (input output : State) :
    (∃ fuel, output ∈ eval fuel program code input) ↔ Derives program code input output :=
  ⟨fun ⟨fuel,h⟩ => eval_sound fuel program code input output h,
    eval_complete program code input output⟩

structure Closed {n : Nat} {State : Type} (program : Fin n → Code n State)
    (relation : Code n State → State → State → Prop) : Prop where
  ret : ∀ p, relation .ret p p
  step : ∀ effect next input middle output, middle ∈ effect input →
    relation next middle output → relation (.step effect next) input output
  call : ∀ callee next input middle output, relation (program callee) input middle →
    relation next middle output → relation (.call callee next) input output
  left : ∀ left right input output, relation left input output →
    relation (.branch left right) input output
  right : ∀ left right input output, relation right input output →
    relation (.branch left right) input output

theorem least_closed_relation {n : Nat} {State : Type} (program : Fin n → Code n State)
    (relation : Code n State → State → State → Prop) (closed : Closed program relation)
    (code : Code n State) (input output : State) (h : Derives program code input output) :
    relation code input output := by
  induction h with
  | ret p => exact closed.ret p
  | step hm _ ih => exact closed.step _ _ _ _ _ hm ih
  | call _ _ first second => exact closed.call _ _ _ _ _ first second
  | left _ ih => exact closed.left _ _ _ _ ih
  | right _ ih => exact closed.right _ _ _ _ ih

def loop {State : Type} : Fin 1 → Code 1 State := fun _ => .call ⟨0,by decide⟩ .ret

theorem unproductive_recursion_empty {State : Type} (fuel : Nat) (input : State) :
    eval fuel loop (loop ⟨0,by decide⟩) input = [] := by
  induction fuel generalizing input with
  | zero => rfl
  | succ fuel ih =>
    change (eval fuel loop (loop ⟨0,by decide⟩) input).flatMap (eval fuel loop .ret) = []
    rw [ih]
    rfl

theorem unproductive_recursion_has_no_return {State : Type} (input output : State) :
    ¬ Derives loop (loop ⟨0,by decide⟩) input output := by
  intro h
  obtain ⟨fuel,hf⟩ := eval_complete _ _ _ _ h
  rw [unproductive_recursion_empty] at hf
  cases hf

private def mixedProgram : Fin 2 → Code 2 Nat := fun method =>
  if method.val = 0 then
    .step (fun p => [p+1]) (.call ⟨1,by decide⟩ (.step (fun p => [p+1]) .ret))
  else .ret

/-- 0=static f, 1=local intermediate, 2=static g. The callee query receives
    state 1, so a STATIC-current-base-only admission policy would be incomplete. -/
theorem mixed_static_local_nested_call :
    eval 4 mixedProgram (mixedProgram ⟨0,by decide⟩) 0 = [2] := by decide

end BalancedCalls

/-! Cache flavors are disjoint; a weak bypass cannot serve as a strong edge.
    The run-keyed query/compiled cache below is one sufficient discipline,
    not a requirement that compatible strong records cannot cross runs. -/

inductive Flavor where
  | weakRun1 | strongLater
  deriving DecidableEq, Repr

structure CacheKey where
  owner : Owner
  run : Nat
  flavor : Flavor
  deriving DecidableEq, Repr

def strongCacheReadable (key : CacheKey) (owner : Owner) (run : Nat) : Bool :=
  decide (2 ≤ run ∧ key = ⟨owner,run,.strongLater⟩)

theorem weak_cache_not_strong (owner : Owner) (oldRun run : Nat) :
    strongCacheReadable ⟨owner,oldRun,.weakRun1⟩ owner run = false := by
  simp [strongCacheReadable]

theorem different_run_not_readable (owner : Owner) (oldRun run : Nat) (h : oldRun ≠ run) :
    strongCacheReadable ⟨owner,oldRun,.strongLater⟩ owner run = false := by
  simp [strongCacheReadable, h]

theorem whole_F1_quarantine (owner : Owner) (flavor : Flavor) (run : Nat) :
    strongCacheReadable ⟨owner,1,flavor⟩ owner run = false := by
  cases flavor <;> simp [strongCacheReadable]
  omega

def strongRecordReadable (producerRun readerRun : Nat) (flavor : Flavor) : Bool :=
  decide (2 ≤ producerRun ∧ 2 ≤ readerRun ∧ flavor = .strongLater)

theorem weak_record_never_executable (producerRun readerRun : Nat) :
    strongRecordReadable producerRun readerRun .weakRun1 = false := by
  simp [strongRecordReadable]

theorem F1_record_never_executable (readerRun : Nat) (flavor : Flavor) :
    strongRecordReadable 1 readerRun flavor = false := by simp [strongRecordReadable]

theorem compatible_strong_records_cross_runs (producerRun readerRun : Nat)
    (producerLater : 2 ≤ producerRun) (readerLater : 2 ≤ readerRun) :
    strongRecordReadable producerRun readerRun .strongLater = true := by
  simp [strongRecordReadable, producerLater, readerLater]

/-- Symbolic relation exports consist of admitted field components. This is
    the finite model's NoRoot export law, not a theorem about arbitrary packed
    AP/IR records or nonstatic outputs of the whole runtime. -/
def exportPoint : Point → Option PFact
  | .zero => none
  | .fact p => some ⟨1,[2*p.field.1,2*p.field.2+1] ++ p.suffix,.exact,.conc p.mark⟩

theorem export_no_empty_static_root (point : Point) (fact : PFact)
    (h : exportPoint point = some fact) : fact.base = 1 ∧ fact.path ≠ [] := by
  cases point with
  | zero => cases h
  | fact p =>
    simp only [exportPoint, Option.some.injEq] at h
    subst fact
    exact ⟨rfl,by simp⟩

/-! Distinguishing executable controls. No native_decide or external evaluator. -/

private def f : Field := (0,1)
private def g : Field := (0,2)
private def p : Component := ⟨f,[],7,false⟩

example : fieldReads (inventory [.containsMark (.field 0 1) 7]) = [] := rfl
example : f1Starts [] [(.field 0 1,7)] f ⟨.field 0 1,.any,7,.empty,false⟩ =
    [concrete (.field 0 1) (.star .empty) 7] := by decide
example : f1Starts [f] [] f ⟨.field 0 1,.any,7,.empty,false⟩ = [(.field 0 1 : Slot).initial] := by decide
example : strong (.kill f) (.fact p) = [] ∧ .fact p ∈ weak (.kill f) (.fact p) := by decide
example : .fact {p with field := g} ∈ strong (.copy f g) (.fact p) := by decide
example : generated f 7 false ∈ run strong (.step (.generate f 7 false) .ret) .zero := by decide
example : .fact p ∉ run strong (.step (.kill f) .ret) (.fact p) := by decide
example : .fact p ∉ run strong .abort (.fact p) := by decide

private def helperQuery : Query := ⟨⟨0,0,[]⟩,2,false,.fact p,none⟩

/-- A helper with no F1 edges still answers a real later identity query. -/
theorem missing_F1_helper_still_answers :
    helperQuery ∈ admitActualQuery [] [] helperQuery ∧
      .fact p ∈ queryAnswers [.fact p] .ret helperQuery := by decide

/-- Reusing an unconditional bypass instead would be wrong for either case. -/
theorem killing_helper_has_no_identity :
    queryAnswers [.fact p] (.step (.kill f) .ret) helperQuery = [] := by decide

theorem nonreturning_helper_has_no_identity :
    queryAnswers [.fact p] .abort helperQuery = [] := by decide

example : Run1RequestsSafety.contains
    ⟨.field 0 1,.must,7,.set [9],false⟩ ⟨f,[9],7,false⟩ = false := by decide

example : ¬ spatialRelation .ret ⟨.field 0 1,.must,7,.set [9],false⟩
    ⟨f,[9],7,false⟩ (.fact ⟨f,[9],7,false⟩) := by
  intro h
  have denied : Run1RequestsSafety.contains
      ⟨.field 0 1,.must,7,.set [9],false⟩ ⟨f,[9],7,false⟩ = false := by decide
  unfold spatialRelation at h
  rw [denied] at h
  cases h.1

end ApSpec.RequestGatedStatics
