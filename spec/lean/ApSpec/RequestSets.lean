/-
  Request-set batching: logical set expansion and the two-sided frontier law.
  A Request includes the full method/context and requesting InitialAp. A Link
  includes the full raw CallerRef, independent layer and admitted group header.
  These are encoding laws, not a proof of the Kotlin cursor or whole engine.
-/
namespace ApSpec.RequestSets

variable {Request Link Mark Flag Target : Type}

/-- The new phase joins fresh requests against all captured links, then old
    requests against fresh links. Crossed arrivals occur in the first arm. -/
theorem frontier_pairs (oldR freshR : Request → Prop) (oldL freshL : Link → Prop)
    (r : Request) (l : Link) :
    (freshR r ∧ (oldL l ∨ freshL l)) ∨ (oldR r ∧ freshL l) ↔
      ((oldR r ∨ freshR r) ∧ (oldL l ∨ freshL l)) ∧ (freshR r ∨ freshL l) := by
  constructor
  · intro h
    rcases h with ⟨hr, hl⟩ | ⟨hr, hl⟩
    · exact ⟨⟨Or.inr hr, hl⟩, Or.inl hr⟩
    · exact ⟨⟨Or.inl hr, Or.inr hl⟩, Or.inr hl⟩
  · rintro ⟨⟨hr, hl⟩, hf⟩
    rcases hf with fr | fl
    · exact Or.inl ⟨fr, hl⟩
    · rcases hr with old | fresh
      · exact Or.inr ⟨old, fl⟩
      · exact Or.inl ⟨fresh, hl⟩

/-- If fresh memberships are exact deltas, the two arms do not repeat a pair. -/
theorem frontier_arms_disjoint (oldR freshR : Request → Prop)
    (h : ∀ r, ¬ (oldR r ∧ freshR r)) (oldL freshL : Link → Prop) (r : Request) (l : Link) :
    ¬ ((freshR r ∧ (oldL l ∨ freshL l)) ∧ (oldR r ∧ freshL l)) := by
  rintro ⟨⟨hf, _⟩, ⟨ho, _⟩⟩
  exact h r ⟨ho, hf⟩

/-- Every overlapping flag of one raw FLOW group has the same target and mx.
    Thus existence of a geometric match plus set subtraction is sufficient. -/
theorem flow_group_effects (requested excluded : Mark → Prop) (overlaps : Flag → Prop)
    (target : Target) (mark : Mark) (output : Target) :
    (∃ flag, overlaps flag ∧ requested mark ∧ ¬ excluded mark ∧ output = target) ↔
      (∃ flag, overlaps flag) ∧ requested mark ∧ ¬ excluded mark ∧ output = target := by
  constructor
  · rintro ⟨flag, hg, hr, hx, ht⟩
    exact ⟨⟨flag, hg⟩, hr, hx, ht⟩
  · rintro ⟨⟨flag, hg⟩, hr, hx, ht⟩
    exact ⟨flag, hg, hr, hx, ht⟩

/-- Outgoing union is permitted after each full raw binding has been checked.
    Targets can be projected, but this does not remove the raw links. -/
theorem target_batch_expansion (effect : Link → Target → Mark → Prop)
    (links : Link → Prop) (target : Target) (mark : Mark) :
    (∃ link, links link ∧ effect link target mark) ↔
      (fun t m => ∃ link, links link ∧ effect link t m) target mark := by
  rfl

/-- The existing Work reservation may end only after every covered owner drains.
    In particular, facts added by a request answer prevent completion. -/
def workComplete (facts publications requests continuation outgoing : Bool) : Bool :=
  !(facts || publications || requests || continuation || outgoing)

theorem work_complete_iff (facts publications requests continuation outgoing : Bool) :
    workComplete facts publications requests continuation outgoing = true ↔
      facts = false ∧ publications = false ∧ requests = false ∧
      continuation = false ∧ outgoing = false := by
  cases facts <;> cases publications <;> cases requests <;>
    cases continuation <;> cases outgoing <;> decide

#print axioms frontier_pairs
#print axioms frontier_arms_disjoint
#print axioms flow_group_effects
#print axioms target_batch_expansion
#print axioms work_complete_iff

end ApSpec.RequestSets
