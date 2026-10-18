/-
  Requested-scope completeness for positive group dependencies.
  A need selects every productive input and guard premise of every candidate
  producer. This selector closure is separate from actual group supply.
  Restricting rules and real seeds to that scope preserves every requested
  finite derivation, including group changes and multi-point guards.
  No need or canonical hypothesis becomes a supply seed.
-/
import ApSpec.Run1StaticGroupDerivation
namespace ApSpec.Run1StaticGroupRequestSlice
set_option autoImplicit false
open Run1StaticGroupDerivation
variable {Node : Type} [DecidableEq Node]

def reverseNeeds (rules : List (Rule Node)) : List (Rule Node) :=
  rules.flatMap (fun rule => rule.premises.map (fun input => ⟨[rule.conclusion], input⟩))

/-- Its result is a request set, not a supplied-group set. -/
def scope (rules : List (Rule Node)) (requests : List (Fact Node)) : List (Fact Node) :=
  (solve (reverseNeeds rules) requests).facts

def PremiseClosed (rules : List (Rule Node)) (wanted : List (Fact Node)) : Prop :=
  ∀ rule, rule ∈ rules → rule.conclusion ∈ wanted →
    ∀ input, input ∈ rule.premises → input ∈ wanted

theorem scope_contains_requests (rules : List (Rule Node)) (requests : List (Fact Node))
    (f : Fact Node) (requested : f ∈ requests) : f ∈ scope rules requests :=
  (solve (reverseNeeds rules) requests).includes f requested

theorem scope_premise_closed (rules : List (Rule Node)) (requests : List (Fact Node)) :
    PremiseClosed rules (scope rules requests) := by
  intro rule declared output input premise
  apply (solve (reverseNeeds rules) requests).stable
    (⟨[rule.conclusion], input⟩ : Rule Node)
  · exact List.mem_flatMap.mpr ⟨rule, declared, List.mem_map.mpr ⟨input, premise, rfl⟩⟩
  · refine ⟨by simp, ?_⟩
    intro f member
    have same : f = rule.conclusion := List.mem_singleton.mp member
    exact same ▸ output

def activeRules (rules : List (Rule Node)) (wanted : List (Fact Node)) : List (Rule Node) :=
  rules.filter (fun rule => decide (rule.conclusion ∈ wanted))

def activeSeeds (seeds wanted : List (Fact Node)) : List (Fact Node) :=
  seeds.filter (fun f => decide (f ∈ wanted))

def restrictTree (rules : List (Rule Node)) (seeds wanted : List (Fact Node))
    (closed : PremiseClosed rules wanted) :
    {f : Fact Node} → Tree rules seeds f → f ∈ wanted →
      Tree (activeRules rules wanted) (activeSeeds seeds wanted) f
  | _, .source f root, selected =>
      .source f (List.mem_filter.mpr ⟨root, of_decide_eq_true (by simpa using selected)⟩)
  | _, .operation rule declared productive inputs, selected =>
      .operation rule (List.mem_filter.mpr ⟨declared, by simpa using selected⟩) productive
        (fun input member => restrictTree rules seeds wanted closed
          (inputs input member) (closed rule declared selected input member))

def restoreTree (rules : List (Rule Node)) (seeds wanted : List (Fact Node)) :
    {f : Fact Node} → Tree (activeRules rules wanted) (activeSeeds seeds wanted) f →
      Tree rules seeds f
  | _, .source f root => .source f (List.mem_filter.mp root).1
  | _, .operation rule declared productive inputs =>
      .operation rule (List.mem_filter.mp declared).1 productive
        (fun input member => restoreTree rules seeds wanted (inputs input member))

theorem selected_tree_exact (rules : List (Rule Node)) (seeds wanted : List (Fact Node))
    (closed : PremiseClosed rules wanted) (f : Fact Node) (selected : f ∈ wanted) :
    Nonempty (Tree rules seeds f) ↔
      Nonempty (Tree (activeRules rules wanted) (activeSeeds seeds wanted) f) := by
  constructor
  · rintro ⟨tree⟩
    exact ⟨restrictTree rules seeds wanted closed tree selected⟩
  · rintro ⟨tree⟩
    exact ⟨restoreTree rules seeds wanted tree⟩

theorem requested_solution_exact (rules : List (Rule Node))
    (seeds requests : List (Fact Node)) (f : Fact Node) (requested : f ∈ requests) :
    f ∈ (solve rules seeds).facts ↔
      f ∈ (solve (activeRules rules (scope rules requests))
        (activeSeeds seeds (scope rules requests))).facts := by
  rw [solution_exact, solution_exact]
  exact selected_tree_exact rules seeds _ (scope_premise_closed rules requests) f
    (scope_contains_requests rules requests f requested)

namespace Controls
def fact (node group : Nat) : Fact Nat := ⟨node, group⟩
def guarded : List (Rule Nat) := [⟨[fact 0 7, fact 1 8], fact 2 9⟩]
def roots : List (Fact Nat) := [fact 0 7, fact 1 8, fact 3 10]
def wanted : List (Fact Nat) := scope guarded [fact 2 9]
def controls : List (String × Bool) := [
  ("wanted output activates RHS", decide (fact 0 7 ∈ wanted)),
  ("wanted output activates different-group guard", decide (fact 1 8 ∈ wanted)),
  ("unrelated source is outside slice", decide (fact 3 10 ∉ wanted)),
  ("all requested premises yield output", decide (fact 2 9 ∈
    (solve (activeRules guarded wanted) (activeSeeds roots wanted)).facts)),
  ("missing guard cannot yield output", decide (fact 2 9 ∉
    (solve (activeRules guarded wanted) (activeSeeds [fact 0 7] wanted)).facts)),
  ("request roots are not supply roots", decide (fact 2 9 ∉
    (solve (activeRules guarded wanted) (activeSeeds [] wanted)).facts))
]
theorem controls_pass : controls.all Prod.snd = true := by decide +kernel
end Controls
end ApSpec.Run1StaticGroupRequestSlice
