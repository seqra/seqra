/- Constructive no-spontaneous-mark witnesses for the reduced catalog.
Every concrete atom comes from a declared live GEN, a protected imported
root, or a mark-preserving transfer. Runtime constructor completeness is
an explicit adapter boundary, not a new runtime provenance label. -/
import ApSpec.SemgrepReducedMarks
namespace ApSpec.RuleMarkCoverage
set_option autoImplicit false
open ApSpec.SemgrepReducedMarks

inductive Witness (policy : Policy) (rules : List Rule) : FullMark → Type where
  | imported (mark : FullMark) (present : mark ∈ policy.externalAssigned) :
      Witness policy rules mark
  | gen (row : Rule) (present : row ∈ rules) (live : row.guard ≠ .truth false)
      (id : Nat) (position : Position) (mark : FullMark) (payload : Nat)
      (action : Action.gen id position mark payload ∈ row.actions) :
      Witness policy rules mark
  | transfer (mark : FullMark) (parent : Witness policy rules mark) :
      Witness policy rules mark

theorem witness_assigned {policy : Policy} {rules : List Rule} {mark : FullMark}
    (witness : Witness policy rules mark) : mark ∈ assigned policy rules :=
  match witness with
  | .imported mark present => List.mem_append_left _ present
  | .gen row present live id position mark payload action => by
      apply List.mem_append_right
      apply List.mem_flatMap.mpr
      refine ⟨row,present,?_⟩
      simp only [assignedBy, if_neg live]
      exact List.mem_filterMap.mpr ⟨.gen id position mark payload,action,rfl⟩
  | .transfer _ parent => witness_assigned parent

theorem enabled_guard_is_live (row : Rule) (marks : Position → FullMark → Bool)
    (other : Nat → Bool) (enabled : eval marks other row.guard = true) :
    row.guard ≠ .truth false := by
  intro dead
  simp [dead,eval] at enabled

theorem cover_from_witnesses (policy : Policy) (rules : List Rule)
    (marks : Position → FullMark → Bool)
    (history : (position : Position) → (mark : FullMark) →
      marks position mark = true → Witness policy rules mark) :
    AssignedCover (assigned policy rules) marks :=
  fun position mark present => witness_assigned (history position mark present)

theorem absent_mark_cannot_be_true (policy : Policy) (rules : List Rule)
    (marks : Position → FullMark → Bool)
    (history : (position : Position) → (mark : FullMark) →
      marks position mark = true → Witness policy rules mark)
    (position : Position) (mark : FullMark) (absent : mark ∉ assigned policy rules) :
    marks position mark = false := by
  cases value : marks position mark with
  | false => rfl
  | true => exact False.elim (absent (witness_assigned (history position mark value)))

theorem folded_guard_from_witnesses (policy : Policy) (rules : List Rule)
    (marks : Position → FullMark → Bool) (other : Nat → Bool)
    (history : (position : Position) → (mark : FullMark) →
      marks position mark = true → Witness policy rules mark) (guard : Expr) :
    eval marks other (fold (assigned policy rules) guard) = eval marks other guard :=
  fold_truth_equivalent (assigned policy rules) marks other
    (cover_from_witnesses policy rules marks history) guard

namespace Controls
open ApSpec.SemgrepReducedMarks.Controls

def sourceT : Rule := ⟨1,.truth true,[.gen 9 result t 0],[result]⟩
def root : Witness {} [sourceT] t := .gen sourceT (by simp) (by decide) 9 result t 0 (by simp [sourceT])
def copied : Witness {} [sourceT] t := .transfer t (.transfer t root)
def imported : Witness {externalAssigned := [u]} [] u := .imported u (by simp)
def controls : List Bool := [
  decide (t ∈ assigned {} [sourceT]),
  decide (u ∉ assigned {} [sourceT]),
  decide (u ∈ assigned {externalAssigned := [u]} []),
  decide (fold (assigned {} [sourceT]) (.neg (.cm arg0 u)) = .truth true),
  decide (fold (assigned {} [sourceT]) (.cm arg1 t) = .cm arg1 t),
  decide (sameNameOtherOwner ∉ assigned {} [sourceT])
]
theorem controls_pass : controls.all id = true := by decide
end Controls
end ApSpec.RuleMarkCoverage
