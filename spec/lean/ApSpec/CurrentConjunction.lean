/-
  Local conjunction contract. Forward literal contributions join their layers;
  one missing contribution gives no result. A normal multi-premise publication
  stays normal when selected for demand hand-off. Backward reversal splits a
  conjunction into independent DEMAND branches, not sufficient forward flows.

  part_factor is an exact rewrite of the current binary AnyTaintND layer rule.
  The examples use exact facts or empty-exclusion must facts. The coordination
  example models one accepted combination. ReverseWitness checks a local
  support counterexample. None is a full annotated store, restricted ND closure,
  or iteration proof. Summary selection and persistence policies are unchanged.
-/
import ApSpec.AnyTaintND
import ApSpec.CurrentSummarySelection

namespace ApSpec.CurrentConjunction
open ApSpec ApSpec.ND ApSpec.AnyTaintND ApSpec.AnyTaintEx
open ApSpec.CurrentSummarySelection

/-- Layer of one accepted literal contribution. Overlap and the concrete mark
    gate are preconditions of storing this contribution, not a demand bit. -/
def partDemand (lit : PFact) (f : AFact) : Bool :=
  f.demand || (!coversB lit f.fact && !f.fact.kind.isAny)

/-- Local equivalence only: factor the current binary conjunction layer into
    one layer per literal and join those layers. -/
theorem part_factor (cj : Conj) (f1 f2 : AFact) :
    conjLayerT cj f1 f2 = (partDemand cj.lit1 f1 || partDemand cj.lit2 f2) := by
  unfold conjLayerT partDemand
  cases f1.demand <;> cases f2.demand <;>
    cases coversB cj.lit1 f1.fact <;> cases coversB cj.lit2 f2.fact <;>
    cases f1.fact.kind.isAny <;> cases f2.fact.kind.isAny <;> rfl

theorem joined_normal_iff (cj : Conj) (f1 f2 : AFact) :
    conjLayerT cj f1 f2 = false ↔
      partDemand cj.lit1 f1 = false ∧ partDemand cj.lit2 f2 = false := by
  rw [part_factor]
  cases partDemand cj.lit1 f1 <;> cases partDemand cj.lit2 f2 <;> decide

theorem joined_demand_iff (cj : Conj) (f1 f2 : AFact) :
    conjLayerT cj f1 f2 = true ↔
      partDemand cj.lit1 f1 = true ∨ partDemand cj.lit2 f2 = true := by
  rw [part_factor]
  cases partDemand cj.lit1 f1 <;> cases partDemand cj.lit2 f2 <;> decide

/-- Local coordination model after accepted literal matches have been stored.
    It models one combination, not the whole standing store or analysis closure. -/
def completeCombination (cj : Conj) : Option AFact → Option AFact → Option AFact
  | some f1, some f2 => some (conjFactT cj f1 f2)
  | _, _ => none

theorem missing_first (cj : Conj) (second : Option AFact) :
    completeCombination cj none second = none := by cases second <;> rfl

theorem missing_second (cj : Conj) (first : Option AFact) :
    completeCombination cj first none = none := by cases first <;> rfl

def a : PFact := ⟨1, [], .exact, .conc 7⟩
def b : PFact := ⟨2, [], .exact, .conc 8⟩
def c : PFact := ⟨3, [], .exact, .conc 9⟩
def cj : Conj := ⟨a, b, c⟩
def fa : AFact := ⟨a, false⟩
def fb : AFact := ⟨b, false⟩
def demandB : AFact := ⟨b, true⟩

theorem normal_example : conjLayerT cj fa fb = false := by decide
theorem mixed_example : conjLayerT cj fa demandB = true := by decide

/-- Normal `[any-taint]` is an accepted, normal literal contribution even when
    the literal is one exact location inside that must input. -/
theorem must_example :
    overlapB AnyTaintND.Example.cj.lit1 AnyTaintND.Example.fx.fact = true ∧
    markGate AnyTaintND.Example.cj.lit1.mark AnyTaintND.Example.fx.fact.mark = .ok ∧
    coversB AnyTaintND.Example.cj.lit1 AnyTaintND.Example.fx.fact = false ∧
    partDemand AnyTaintND.Example.cj.lit1 AnyTaintND.Example.fx = false ∧
    conjLayerT AnyTaintND.Example.cj AnyTaintND.Example.fx AnyTaintND.Example.fy = false := by
  exact ⟨by decide, rfl, by decide, by decide, by decide⟩

def ja : Entry := ⟨a, false, Excl.empty⟩
def jb : Entry := ⟨b, false, Excl.empty⟩
def normalOutput : XFact := ⟨⟨c, false⟩, Excl.empty⟩

/-- Selection for next iteration is not a conversion to the demand layer. -/
theorem normal_selected_example :
    selectedB .forward ⟨[ja, jb], normalOutput⟩ = true ∧
    normalOutput.af.demand = false := by decide

theorem normal_publication_example :
    publicationPlan .forward ⟨[ja, jb], normalOutput⟩ normalOutput =
      (true, normalOutput) ∧
    (publicationPlan .forward ⟨[ja, jb], normalOutput⟩ normalOutput).2.af.demand = false :=
  by decide

/-- The forward-to-backward orientation of two per-member exact demand patterns.
    This constructor example does not prove a full hand-off closure theorem. -/
def memberPatterns (js : List Entry) (g : XFact) : List ApSpec.DemandEdge :=
  js.map (fun j => ⟨g.af.fact, some j.fact⟩)

theorem member_patterns_example :
    memberPatterns [ja, jb] normalOutput = [⟨c, some a⟩, ⟨c, some b⟩] := by decide

/-- The generic per-literal reverse pairs are singleton micro edges. The
    spec's conjunction reverse wrapper adds demand; generic revEdge does not. -/
def split : List MicroEdge := [revEdge a c, revEdge b c]
theorem reverse_pairs : split = [(c, a), (c, b)] := by decide

def backwardBranches : List AFact := split.flatMap fun e =>
    (applyEdge ⟨c, false⟩ e.1 e.2).facts.map (fun f => ⟨f.fact, true⟩)
theorem backward_split_example :
    backwardBranches = [⟨a, true⟩, ⟨b, true⟩] := by decide

namespace ReverseWitness

def la : Loc := ⟨1, [], 7⟩
def lb : Loc := ⟨2, [], 8⟩
def lc : Loc := ⟨3, [], 9⟩
def Q : NProg := ⟨⟨fun _ => 0, fun _ => 1, []⟩, [(0, 0, cj, 1)]⟩

theorem entry_single {M l L} (h : TaintN Q M 0 l L) : L = [l] := by
  cases h with
  | start => rfl
  | step _ he _ => simp [Q] at he
  | pass _ he _ => simp [Q] at he
  | clean _ he _ => simp [Q] at he
  | filt _ he _ => simp [Q] at he
  | call he _ _ _ _ => simp [Q] at he
  | conj _ _ he _ _ _ => simp [Q] at he

theorem both_support : TaintN Q 0 1 lc [la, lb] :=
  TaintN.conj (.start 0 la) (.start 0 lb) List.mem_cons_self
    (by exact ⟨rfl, ⟨[], rfl, rfl⟩, rfl⟩)
    (by exact ⟨rfl, ⟨[], rfl, rfl⟩, rfl⟩)
    (by exact ⟨rfl, ⟨[], rfl, rfl⟩, rfl⟩)

theorem exit_support_length {M l L} (h : TaintN Q M 1 l L) : L.length = 2 := by
  cases h with
  | step _ he _ => simp [Q] at he
  | pass _ he _ => simp [Q] at he
  | clean _ he _ => simp [Q] at he
  | filt _ he _ => simp [Q] at he
  | call he _ _ _ _ => simp [Q] at he
  | conj h1 h2 he _ _ _ =>
    simp only [Q, List.mem_singleton] at he
    cases he
    rw [entry_single h1, entry_single h2]
    rfl

/-- A normal exact forward summary cannot claim that a alone suffices for c. -/
theorem a_alone_not_support : ¬ TaintN Q 0 1 lc [la] := by
  intro h
  have hl := exit_support_length h
  simp at hl

example : (conjFact cj ⟨a, false⟩ ⟨b, false⟩).demand = false := by decide
example : (conjFact cj ⟨a, true⟩ ⟨b, false⟩).demand = true := by decide

/-- A summary is enabled only when the same call supplies every prerequisite. -/
def enabled (prerequisites supplied : List PFact) : Bool :=
  prerequisites.all (fun p => supplied.contains p)

/-- Exact concrete generic reversal swaps the endpoints. -/
theorem reverse_a : revEdge a c = (c, a) := by decide
theorem reverse_b : revEdge b c = (c, b) := by decide

/-- The complete NORMAL forward conjunction needs both facts. -/
theorem conjunction_both : enabled [a, b] [a, b] = true := by decide
/-- Supplying only a does not establish the conjunction. -/
theorem conjunction_a_only : enabled [a, b] [a] = false := by decide
/-- If the reversed c -> a piece were persisted as NORMAL, reading it forward
    would instead accept a -> c, losing the b prerequisite. -/
theorem reused_singleton_a_only : enabled [a] [a] = true := by decide

end ReverseWitness

#print axioms part_factor
#print axioms joined_normal_iff
#print axioms joined_demand_iff
#print axioms ReverseWitness.both_support
#print axioms ReverseWitness.a_alone_not_support
end ApSpec.CurrentConjunction
