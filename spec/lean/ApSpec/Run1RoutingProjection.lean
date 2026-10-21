import Std
import ApSpec.ImmutableGroupNormalization

/- Local routing and projection laws for the selected implementation spec.
   List paths have arbitrary finite length. Receiving-manager interning and
   effectful Kotlin callbacks remain explicit adapter obligations. -/
namespace ApSpec.Run1RoutingProjection

def overlaps : List Nat → Bool → List Nat → List Nat → Bool → List Nat → Bool
  | [], _, _, [], _, _ => true
  | [], exact, excluded, y :: _, _, _ => !exact && !excluded.contains y
  | x :: _, _, _, [], exact, excluded => !exact && !excluded.contains x
  | x :: xs, a, ae, y :: ys, b, be => x == y && overlaps xs a ae ys b be

theorem exact_class_field_disjoint (c d f : Nat) (rest ae be : List Nat) (tail : Bool) :
    overlaps [c] true ae (d :: f :: rest) tail be = false := by
  simp [overlaps]

theorem field_exact_class_disjoint (c d f : Nat) (rest ae be : List Nat) (tail : Bool) :
    overlaps (d :: f :: rest) tail be [c] true ae = false := by
  simp [overlaps]

def pathPrefix : List Nat → List Nat → Bool
  | [], _ => true
  | _ :: _, [] => false
  | x :: xs, y :: ys => x == y && pathPrefix xs ys

theorem exact_match_has_prefix (writer query ae be : List Nat) (tail : Bool)
    (h : overlaps writer tail ae query true be = true) : pathPrefix writer query = true := by
  induction writer generalizing query with
  | nil => rfl
  | cons x xs ih =>
    cases query with
    | nil => simp [overlaps] at h
    | cons y ys =>
      have hs : (x == y) = true ∧ overlaps xs tail ae ys true be = true := by
        simpa [overlaps] using h
      simp [pathPrefix, hs.1, ih ys hs.2]

/-- A removed row has no effects, so deleting it preserves the effect list. -/
theorem remove_empty_effects {α β : Type} (rows : List α) (keep : α → Bool)
    (effects : α → List β) (empty : ∀ x ∈ rows, keep x = false → effects x = []) :
    (rows.filter keep).flatMap effects = rows.flatMap effects := by
  induction rows with
  | nil => rfl
  | cons x xs ih =>
    have tail : ∀ y ∈ xs, keep y = false → effects y = [] :=
      fun y hy => empty y (List.mem_cons_of_mem x hy)
    cases hk : keep x with
    | false => simp [hk, empty x (by simp) hk, ih tail]
    | true => simp [hk, ih tail]

open ApSpec.ImmutableGroupNormalization (Atom project referenceAtoms projected_list_idempotent)

structure Leaf where
  path : List Nat
  exact : List Atom
  any : List Atom
  deriving DecidableEq, Repr

def projectLeaf (leaf : Leaf) : Leaf :=
  {leaf with exact := referenceAtoms leaf.exact, any := referenceAtoms leaf.any}

theorem leaf_projection_idempotent (leaf : Leaf) :
    projectLeaf (projectLeaf leaf) = projectLeaf leaf := by
  cases leaf
  simp [projectLeaf, projected_list_idempotent]

def projectTree (tree : List Leaf) : List Leaf := tree.map projectLeaf

theorem tree_projection_idempotent (tree : List Leaf) :
    projectTree (projectTree tree) = projectTree tree := by
  simp [projectTree, List.map_map, Function.comp_def, leaf_projection_idempotent]

theorem identity_reuse_exact {α : Type} [DecidableEq α] (old normalized : α) :
    (if normalized = old then old else normalized) = normalized := by
  split
  · rename_i h; exact h.symm
  · rfl

/-- The producer has already projected the exact value delivered to the consumer. -/
theorem immediate_consumer_skip {α : Type} (normalize : α → α)
    (idempotent : ∀ x, normalize (normalize x) = normalize x)
    (after : α → List α) (raw : α) :
    after (normalize (normalize raw)) = after (normalize raw) := by
  rw [idempotent]

def controls : List Bool := [
  !overlaps [1] true [] [1, 2, 3] false [],
  !overlaps [1, 2] false [] [1] true [],
  overlaps [] false [] [1] true [],
  overlaps [1] false [] [1, 2] false [],
  !overlaps [1] false [2] [1, 2] false [],
  pathPrefix [] [1], pathPrefix [1] [1], !pathPrefix [1, 2] [1],
  projectTree [⟨[1, 2], [.full 7 8], [.group 9]⟩] ==
    [⟨[1, 2], [.group 7], [.group 9]⟩]
]

theorem controls_pass : controls.all id = true := by decide

end ApSpec.Run1RoutingProjection
