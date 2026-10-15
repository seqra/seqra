/-
  Executable finite oracle for the selected run-1 request grammar and static plan.
  The two normal slots are distinct from the special static-root seed and from
  legal concrete class-point inputs. E and mx are not request-key dimensions.

  The graph oracle enumerates finite closed sets: intentionally exponential,
  suitable as an independent specification, NOT the proposed production SCC
  algorithm. Its closure/leastness/reachability proofs do not assume coverage.
  Runtime-to-effective-form coverage and the Kotlin lowering remain separate.
-/
import ApSpec.Core

namespace ApSpec.Run1Requests
open ApSpec

inductive Slot where
  | base (id : Nat)
  | field (cls field : Nat)
  deriving DecidableEq, Repr

/-- Full method ownership; static plans below may conservatively union contexts,
    but a runtime request does not identify owners by method alone. -/
structure Owner where
  method : Nat
  entry : Nat
  context : List Nat
  deriving DecidableEq, Repr

structure Question where
  owner : Owner
  slot : Slot
  mark : Mark
  deriving DecidableEq, Repr

inductive Point where
  | local (base : Nat) (path : List Acc)
  | root
  | cls (id : Nat)
  | field (cls field : Nat)
  deriving DecidableEq, Repr

inductive Tail where
  | exact | any | must
  deriving DecidableEq, Repr

structure Leaf where
  point : Point
  tail : Tail
  mark : Mark
  fieldExclusion : Excl := .empty
  demand : Bool := false
  deriving DecidableEq, Repr

/-- The abstract/excluded-mark FLOW header is separate from concrete Leaf. -/
structure Flow where
  point : Point
  excludedMarks : List Mark
  fieldExclusion : Excl := .empty
  deriving DecidableEq, Repr

/-- Disjoint executable AP vocabulary: zero=0, S=1; ordinary bases start at 2;
    even class-accessor IDs and odd field-accessor IDs cannot collide. -/
def Slot.initial : Slot → PFact
  | .base b => ⟨b + 2, [], .star .empty, .star⟩
  | .field c f => ⟨1, [2*c, 2*f+1], .star .empty, .star⟩

def Point.base : Point → Base
  | .local b _ => b + 2
  | _ => 1

def Point.path : Point → List Acc
  | .local _ p => p
  | .root => []
  | .cls c => [2*c]
  | .field c f => [2*c, 2*f+1]

def concrete (p : Point) (k : Kind) (t : Mark) : PFact :=
  ⟨p.base, p.path, k, .conc t⟩

def broad : Tail → Bool
  | .exact => false
  | _ => true

/-- Independent geometry test: exact ancestors never cover a descendant.
    E is intentionally absent, following the newly selected request policy. -/
def slotMatches : Slot → Leaf → Bool
  | .base b, a => match a.point with
    | .local b' _ => Nat.beq b b'
    | _ => false
  | .field c f, a => match a.point with
    | .root => broad a.tail
    | .cls c' => Nat.beq c c' && broad a.tail
    | .field c' f' => Nat.beq c c' && Nat.beq f f'
    | _ => false

/-- Precise selected concrete-answer projector; no concrete FLOW answer exists.
    A broad static ancestor yields a concrete STAR chain, not an ANY initial. -/
def project (s : Slot) (a : Leaf) : Option PFact :=
  if !slotMatches s a then none else
  match s with
  | .base b =>
    let exactRoot : Bool := match a.point with
      | .local _ [] => a.tail == .exact
      | _ => false
    some (concrete (.local b []) (if exactRoot then .exact else .star .empty) a.mark)
  | .field c f =>
    let kind : Kind := match a.point with
      | .field _ _ => if a.tail == .exact then .exact else .any
      | _ => .star .empty
    some (concrete (.field c f) kind a.mark)

theorem project_exists_iff (s : Slot) (a : Leaf) :
    (project s a).isSome = slotMatches s a := by
  cases h : slotMatches s a <;> cases s <;> simp [project, h]

theorem projector_ignores_field_E (s : Slot) (a : Leaf) (e : Excl) :
    project s {a with fieldExclusion := e} = project s a := by
  cases s <;> cases a with | mk p k t ex d => cases p <;> rfl

theorem projector_keeps_mark (s : Slot) (a : Leaf) (j : PFact)
    (h : project s a = some j) : j.mark = .conc a.mark := by
  unfold project at h
  split at h
  · cases h
  · cases s <;> simp only [Option.some.injEq] at h <;> subst j <;> rfl

theorem nonstatic_exact_root (b t : Nat) (e : Excl) (d : Bool) :
    project (.base b) ⟨.local b [], .exact, t, e, d⟩ =
      some (concrete (.local b []) .exact t) := by simp [project, slotMatches]

theorem nonstatic_chain (b t x : Nat) (p : List Acc) (k : Tail) :
    project (.base b) ⟨.local b (x::p), k, t, .empty, false⟩ =
      some (concrete (.local b []) (.star .empty) t) := by simp [project, slotMatches]

theorem static_exact_field (c f t : Nat) :
    project (.field c f) ⟨.field c f, .exact, t, .empty, false⟩ =
      some (concrete (.field c f) .exact t) := by simp [project, slotMatches]

theorem static_any_field (c f t : Nat) :
    project (.field c f) ⟨.field c f, .any, t, .empty, false⟩ =
      some (concrete (.field c f) .any t) := by simp [project, slotMatches]

theorem static_must_field (c f t : Nat) :
    project (.field c f) ⟨.field c f, .must, t, .empty, false⟩ =
      some (concrete (.field c f) .any t) := by simp [project, slotMatches]

theorem broad_root_projects (c f t : Nat) (k : Tail) (hk : broad k = true) :
    project (.field c f) ⟨.root, k, t, .empty, false⟩ =
      some (concrete (.field c f) (.star .empty) t) := by simp [project, slotMatches, hk]

theorem broad_class_projects (c f t : Nat) (k : Tail) (hk : broad k = true) :
    project (.field c f) ⟨.cls c, k, t, .empty, false⟩ =
      some (concrete (.field c f) (.star .empty) t) := by simp [project, slotMatches, hk]

theorem exact_class_not_field (c f t : Nat) :
    project (.field c f) ⟨.cls c, .exact, t, .empty, false⟩ = none := by simp [project, slotMatches, broad]

/-- Legal concrete class-point taint bypasses normal slot requests. It is
    preserved even when no local condition currently mentions that class. -/
def directClass (a : Leaf) : Option PFact := match a.point, a.tail with
  | .cls c, .exact => some (concrete (.cls c) .exact a.mark)
  | _, _ => none

theorem direct_class_preserved (c t : Nat) (e : Excl) (d : Bool) :
    directClass ⟨.cls c, .exact, t, e, d⟩ = some (concrete (.cls c) .exact t) := rfl

def forbiddenExactRoot (a : Leaf) : Bool := a.point == .root && a.tail == .exact

/-- The root assumption does not exclude broad ANY or must suppliers. -/
theorem broad_root_not_forbidden (k : Tail) (t : Nat) (h : broad k = true) :
    forbiddenExactRoot ⟨.root, k, t, .empty, false⟩ = false := by cases k <;> simp_all [broad, forbiddenExactRoot]

theorem exact_class_not_forbidden (c t : Nat) :
    forbiddenExactRoot ⟨.cls c, .exact, t, .empty, false⟩ = false := by simp [forbiddenExactRoot]

def Tail.asKind : Tail → Kind
  | .exact => .exact
  | .any | .must => .any

/-- The ordinary-base projector is the EXISTING AP answerInit after erasing
    request-irrelevant E and normalizing must to may. Static handling is separate. -/
theorem existing_nonstatic_answer_adapter (b t : Nat) (p : List Acc) (k : Tail) :
    project (.base b) ⟨.local b p,k,t,.empty,false⟩ =
      some (answerInit (Slot.base b).initial (concrete (.local b p) k.asKind t) t) := by
  cases p <;> cases k <;>
    simp [project, slotMatches, Tail.asKind, answerInit, Slot.initial,
      concrete, Point.base, Point.path, dropPrefix]

/-- A class-level static condition can also see a broad supplier. This direct
    metadata handler has no abstract class initial or class request key. -/
def classAnswer (cls : Nat) (a : Leaf) : Option PFact := match a.point with
  | .cls c => if c = cls then
      some (concrete (.cls cls) (if a.tail == .exact then .exact else .any) a.mark)
    else none
  | .root => if broad a.tail then some (concrete (.cls cls) (.star .empty) a.mark) else none
  | _ => none

theorem broad_root_class_answer (c t : Nat) (k : Tail) (hk : broad k = true) :
    classAnswer c ⟨.root,k,t,.empty,false⟩ =
      some (concrete (.cls c) (.star .empty) t) := by simp [classAnswer, hk]

theorem same_class_any_answer (c t : Nat) :
    classAnswer c ⟨.cls c,.any,t,.empty,false⟩ = some (concrete (.cls c) .any t) := by
  simp [classAnswer]

theorem broad_class_answer_starts_demand (c t : Nat) :
    (startFact (concrete (.cls c) (.star .empty) t)).demand = true := rfl

theorem same_class_any_starts_demand (c t : Nat) :
    (startFact (concrete (.cls c) .any t)).demand = true := rfl

/-- The special root seed is emitted independently of ordinary request slots. -/
def specialRoot : PFact := ⟨1,[],.star .empty,.star⟩

def staticAbstractStarts (slots : List Slot) : List PFact :=
  specialRoot :: (slots.filter (fun s => match s with | .field _ _ => true | _ => false)).map Slot.initial

theorem static_root_unconditional (slots : List Slot) :
    specialRoot ∈ staticAbstractStarts slots := by simp [staticAbstractStarts]

/-! ## Finite resolved-callgraph closure, including recursive components. -/

def subsets {α : Type} : List α → List (List α)
  | [] => [[]]
  | x::xs => subsets xs ++ (subsets xs).map (x :: ·)

theorem filter_mem_subsets {α : Type} (p : α → Bool) (xs : List α) :
    xs.filter p ∈ subsets xs := by
  induction xs with
  | nil => simp [subsets]
  | cons x xs ih =>
    cases hx : p x
    · simp [List.filter_cons, hx, subsets, ih]
    · apply List.mem_append.mpr
      right
      exact List.mem_map.mpr ⟨xs.filter p, ih, by simp [List.filter_cons, hx]⟩

abbrev Graph (n : Nat) := List (Fin n × Fin n)

def closed {n : Nat} (g : Graph n) (seed : Fin n) (s : List (Fin n)) : Bool :=
  decide (seed ∈ s) && g.all (fun e => decide (e.2 ∈ s → e.1 ∈ s))

def closure {n : Nat} (g : Graph n) (seed : Fin n) : List (Fin n) :=
  let all := List.finRange n
  all.filter (fun m => (subsets all).all (fun s => !closed g seed s || decide (m ∈ s)))

theorem closure_mem_iff {n : Nat} (g : Graph n) (seed m : Fin n) :
    m ∈ closure g seed ↔
      ∀ s ∈ subsets (List.finRange n), closed g seed s = true → m ∈ s := by
  simp only [closure, List.mem_filter, List.mem_finRange, true_and, List.all_eq_true,
    Bool.or_eq_true, Bool.not_eq_true, decide_eq_true_eq]
  constructor
  · intro h s hs hc
    rcases h s hs with no | yes
    · rw [hc] at no; cases no
    · exact yes
  · intro h s hs
    cases hc : closed g seed s
    · exact Or.inl rfl
    · exact Or.inr (h s hs hc)

theorem closure_seed {n : Nat} (g : Graph n) (seed : Fin n) :
    seed ∈ closure g seed := by
  apply (closure_mem_iff g seed seed).mpr
  intro s _ hc
  exact of_decide_eq_true ((Bool.and_eq_true _ _).mp hc).1

theorem closure_call {n : Nat} (g : Graph n) (seed a b : Fin n)
    (edge : (a,b) ∈ g) (hb : b ∈ closure g seed) : a ∈ closure g seed := by
  apply (closure_mem_iff g seed a).mpr
  intro s hs hc
  have bh := (closure_mem_iff g seed b).mp hb s hs hc
  have all := (Bool.and_eq_true _ _).mp hc |>.2
  have implication := (List.all_eq_true.mp all) (a,b) edge
  exact (of_decide_eq_true implication) bh

/-- Leastness is checked against EVERY finite closed set, not postulated. -/
theorem closure_least {n : Nat} (g : Graph n) (seed : Fin n) (s : List (Fin n))
    (hs : closed g seed s = true) (m : Fin n) (hm : m ∈ closure g seed) : m ∈ s := by
  let canonical := (List.finRange n).filter (fun x => decide (x ∈ s))
  have mem (x : Fin n) : x ∈ canonical ↔ x ∈ s := by simp [canonical]
  have hc : closed g seed canonical = true := by
    apply (Bool.and_eq_true _ _).mpr
    refine ⟨by simpa [mem] using (Bool.and_eq_true _ _).mp hs |>.1, ?_⟩
    apply List.all_eq_true.mpr
    intro e he
    apply decide_eq_true
    intro hb
    exact (mem e.1).mpr ((of_decide_eq_true
      (List.all_eq_true.mp ((Bool.and_eq_true _ _).mp hs).2 e he)) ((mem e.2).mp hb))
  exact (mem m).mp ((closure_mem_iff g seed m).mp hm canonical
    (filter_mem_subsets _ _) hc)

/-- Semantic backward call reachability; cycles may be arbitrarily repeated. -/
inductive Reaches {n : Nat} (g : Graph n) (seed : Fin n) : Fin n → Prop
  | seed : Reaches g seed seed
  | call {a b} : (a,b) ∈ g → Reaches g seed b → Reaches g seed a

theorem recursive_call_coverage {n : Nat} (g : Graph n) (seed m : Fin n)
    (h : Reaches g seed m) : m ∈ closure g seed := by
  induction h with
  | seed => exact closure_seed g seed
  | call edge _ ih => exact closure_call g seed _ _ edge ih

/-! ## Finite effective producer plan; no NNF cube expansion. -/

inductive Scope where
  | field (cls field : Nat)
  | cls (id : Nat)
  | allStatic
  deriving DecidableEq, Repr

def Scope.covers : Scope → Slot → Bool
  | .field c f, .field c' f' => Nat.beq c c' && Nat.beq f f'
  | .cls c, .field c' _ => Nat.beq c c'
  | .allStatic, .field _ _ => true
  | _, _ => false

inductive Need where
  | structural (cls field : Nat)
  | marked (scope : Scope) (mark : Mark)
  | classPoint (cls : Nat) (mark : Mark)
  deriving DecidableEq, Repr

inductive Producer where
  | source | sink | conjunction | inputGate | cleaner | rewriter | excludedTarget
  deriving DecidableEq, Repr

/-- A finite effective-form leaf: roles label the collection input, not R keys. -/
inductive Form where
  | code (cls field : Nat)
  | marked (producer : Producer) (scope : Scope) (mark : Mark)
  | classPoint (producer : Producer) (cls : Nat) (mark : Mark)
  | excludedTarget (scope : Scope) (marks : List Mark)
  | zeroGen | removeAll
  | both (left right : Form)
  deriving DecidableEq, Repr

def collect : Form → List Need
  | .code c f => [.structural c f]
  | .marked _ s t => [.marked s t]
  | .classPoint _ c t => [.classPoint c t]
  | .excludedTarget scope marks => marks.map (Need.marked scope)
  | .zeroGen | .removeAll => []
  | .both a b => collect a ++ collect b

def direct (forms : List Form) : List Need := forms.flatMap collect

def plan {n : Nat} (g : Graph n) (forms : Fin n → List Form) (m : Fin n) : List Need :=
  (List.finRange n).flatMap (fun k => if m ∈ closure g k then direct (forms k) else [])

theorem producer_in_direct (forms : List Form) (p : Producer) (s : Scope) (t : Mark)
    (h : Form.marked p s t ∈ forms) : .marked s t ∈ direct forms := by
  exact List.mem_flatMap.mpr ⟨_, h, by simp [collect]⟩

/-- A generic abstract target *\\X is an explicit creator, not an assumption
    that a cleaner somewhere else has already offered its excluded marks. -/
theorem excluded_target_member_collected (scope : Scope) (marks : List Mark) (t : Mark)
    (h : t ∈ marks) : Need.marked scope t ∈ collect (.excludedTarget scope marks) := by
  exact List.mem_map.mpr ⟨t,h,rfl⟩

theorem plan_contains_reachable {n : Nat} (g : Graph n) (forms : Fin n → List Form)
    (m k : Fin n) (tok : Need) (path : Reaches g k m) (localNeed : tok ∈ direct (forms k)) :
    tok ∈ plan g forms m := by
  apply List.mem_flatMap.mpr
  refine ⟨k, by simp, ?_⟩
  simpa [recursive_call_coverage g k m path] using localNeed

/-- An actually collected static producer is covered through arbitrary recursive
    call propagation. Context/form completeness is NOT inferred from call edges. -/
theorem unchanged_static_plan {n : Nat} (g : Graph n) (forms : Fin n → List Form)
    (m k : Fin n) (p : Producer) (c f t : Nat) (path : Reaches g k m)
    (effective : Form.marked p (.field c f) t ∈ forms k) :
    .marked (.field c f) t ∈ plan g forms m :=
  plan_contains_reachable g forms m k _ path (producer_in_direct _ _ _ _ effective)

/-- `both` represents either NNF connective for collecting potential needs.
    Keeping a union of leaves requires no DNF/cube expansion. -/
inductive Occurs (p : Producer) (scope : Scope) (mark : Mark) : Form → Prop
  | here : Occurs p scope mark (.marked p scope mark)
  | left {a b} : Occurs p scope mark a → Occurs p scope mark (.both a b)
  | right {a b} : Occurs p scope mark b → Occurs p scope mark (.both a b)

theorem effective_leaf_collected {p : Producer} {scope : Scope} {mark : Mark} {form : Form}
    (h : Occurs p scope mark form) : Need.marked scope mark ∈ collect form := by
  induction h with
  | here => simp [collect]
  | left _ ih => exact List.mem_append_left _ ih
  | right _ ih => exact List.mem_append_right _ ih

theorem zero_and_removeAll_not_mark_producers :
    collect Form.zeroGen = [] ∧ collect Form.removeAll = [] := ⟨rfl,rfl⟩

/-- A zero-GEN or remove-all action can still have a structural target. The
    no-mark-producer theorem does not erase that target's footprint. -/
def zeroAt (c f : Nat) : Form := .both (.code c f) .zeroGen
def removeAllAt (c f : Nat) : Form := .both (.code c f) .removeAll

theorem generated_targets_keep_structure (c f : Nat) :
    collect (zeroAt c f) = [.structural c f] ∧
      collect (removeAllAt c f) = [.structural c f] := by
  simp [zeroAt, removeAllAt, collect]

/-- Only actual finite incoming/structural slots are activated. Wide declarations
    stay immutable; no project-field universe or root request is manufactured. -/
def activate (needs : List Need) (actualSlots : List Slot) : List (Slot × Mark) :=
  needs.flatMap fun need => match need with
    | .marked scope t => (actualSlots.filter scope.covers).map (fun slot => (slot,t))
    | _ => []

theorem wide_activation_complete (needs : List Need) (slots : List Slot)
    (s : Scope) (t : Mark) (slot : Slot) (hn : Need.marked s t ∈ needs)
    (hs : slot ∈ slots) (hc : s.covers slot = true) :
    (slot,t) ∈ activate needs slots := by
  apply List.mem_flatMap.mpr
  exact ⟨_, hn, List.mem_map.mpr ⟨slot, List.mem_filter.mpr ⟨hs,hc⟩, rfl⟩⟩

theorem activate_does_not_invent_slots (needs : List Need) (slots : List Slot)
    (s : Slot) (t : Mark) (h : (s,t) ∈ activate needs slots) : s ∈ slots := by
  obtain ⟨need, hn, hx⟩ := List.mem_flatMap.mp h
  cases need with
  | structural => simp [activate] at hx
  | classPoint => simp [activate] at hx
  | marked scope mark =>
    obtain ⟨slot, hs, he⟩ := List.mem_map.mp hx
    have eq : slot = s := congrArg Prod.fst he
    exact eq ▸ (List.mem_filter.mp hs).1

def Slot.isStatic : Slot → Bool
  | .field _ _ => true
  | _ => false

/-- Only same-slot static operations may omit their dynamic proposal because
    of ALREADY ADMITTED membership. The plan declaration alone is never read here. -/
def requestIfUnplanned (admitted : List (Slot × Mark)) (owner : Owner)
    (original checked : Slot) (t : Mark) : Option Question :=
  if original = checked ∧ original.isStatic = true ∧ (original,t) ∈ admitted then none
  else some ⟨owner,original,t⟩

theorem static_dynamic_cover (admitted : List (Slot × Mark)) (owner : Owner)
    (original checked : Slot) (t : Mark) :
    (original,t) ∈ admitted ∨
      requestIfUnplanned admitted owner original checked t = some ⟨owner,original,t⟩ := by
  unfold requestIfUnplanned
  split
  · rename_i h; exact Or.inl h.2.2
  · exact Or.inr rfl

theorem changed_slot_retains_dynamic (admitted : List (Slot × Mark)) (owner : Owner)
    (original checked : Slot) (t : Mark) (changed : original ≠ checked) :
    requestIfUnplanned admitted owner original checked t = some ⟨owner,original,t⟩ := by
  simp [requestIfUnplanned, changed]

/-- Constructed static coverage, rather than an assumed target predicate:
    an effective producer is collected, recursive CG closure carries its need,
    activation creates membership, and only then may dispatch omit the request. -/
theorem effective_static_dispatch {n : Nat} (g : Graph n) (forms : Fin n → List Form)
    (m k : Fin n) (p : Producer) (c f t : Nat) (owner : Owner)
    (activeSlots : List Slot) (active : Slot.field c f ∈ activeSlots)
    (path : Reaches g k m) (effective : Form.marked p (.field c f) t ∈ forms k) :
    let admitted := activate (plan g forms m) activeSlots
    (.field c f,t) ∈ admitted ∧
      requestIfUnplanned admitted owner (.field c f) (.field c f) t = none := by
  dsimp only
  have member := wide_activation_complete (plan g forms m) activeSlots
    (.field c f) t (.field c f)
    (unchanged_static_plan g forms m k p c f t path effective) active (by simp [Scope.covers])
  exact ⟨member, by simp [requestIfUnplanned, Slot.isStatic, member]⟩

inductive Origin where
  | abstract (owner : Owner) (slot : Slot)
  | concrete (owner : Owner) (initial : PFact)
  | staticRoot (owner : Owner)
  | zero (owner : Owner)
  deriving DecidableEq, Repr

/-- Concrete/zero and special root origins never become request keys.
    Normal abstract origins route to the ORIGINAL slot, not the checked field. -/
def dynamic (origin : Origin) (t : Mark) : Option Question := match origin with
  | .abstract owner slot => some ⟨owner,slot,t⟩
  | _ => none

theorem changed_static_origin (owner : Owner) (c g f t : Nat) :
    dynamic (.abstract owner (.field c g)) t = some ⟨owner,.field c g,t⟩ := rfl

theorem concrete_never_requests (owner : Owner) (initial : PFact) (t : Mark) :
    dynamic (.concrete owner initial) t = none := rfl

theorem special_root_never_requests (owner : Owner) (t : Mark) :
    dynamic (.staticRoot owner) t = none := rfl

namespace Examples
def cycle : Graph 3 := [(0,1),(1,0),(1,2)]
theorem recursive_seed_reaches_caller : (0 : Fin 3) ∈ closure cycle 2 := by decide +kernel
theorem class_exact_is_not_a_field_answer :
    project (.field 4 7) ⟨.cls 4,.exact,11,.empty,false⟩ = none ∧
      directClass ⟨.cls 4,.exact,11,.empty,false⟩ = some (concrete (.cls 4) .exact 11) := by decide +kernel
theorem wide_cleaner_covers_unrelated_incoming_field :
    (.field 3 9, 7) ∈ activate [.marked .allStatic 7] [.field 3 2, .field 3 9] := by decide +kernel
end Examples

#print axioms project_exists_iff
#print axioms projector_ignores_field_E
#print axioms closure_least
#print axioms recursive_call_coverage
#print axioms unchanged_static_plan
#print axioms wide_activation_complete
#print axioms existing_nonstatic_answer_adapter
#print axioms effective_leaf_collected
#print axioms effective_static_dispatch
#print axioms static_dynamic_cover
#print axioms excluded_target_member_collected

end ApSpec.Run1Requests
