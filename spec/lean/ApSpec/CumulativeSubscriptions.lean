/-
  N2 cumulative subscriptions: a logical P5 encoding and the checked E6 quotient.

  A key includes the full caller reference and ADMITTED group header. A leaf is an
  atomic path/payload item in that group; an exact delta must not reconstruct its
  key from its normalized empty-E header. `history` below is proof-only ghost data.

  These proofs do not assume a permanent runtime delta list. They prove the exact
  accepted-leaf relation, persistence, candidate/effect lifting, and the ordered
  Cartesian binding quotient. The current AP tree matcher/transfer must satisfy
  the stated per-leaf effect encoding; its full current-mode proof is separate.
  No scheduler, Q1/Q2, or N3 change is modeled here.
-/
namespace ApSpec.CumulativeSubscriptions

variable {Key Leaf Position Effect Ref Root : Type}

abbrev Rows (Key Leaf : Type) := Key → Leaf → Bool

def Encodes (rows : Rows Key Leaf) (history : List (Key × Leaf)) : Prop :=
  ∀ k l, rows k l = true ↔ (k, l) ∈ history

def grow [DecidableEq Key] [DecidableEq Leaf] (rows : Rows Key Leaf)
    (key : Key) (incoming : List Leaf) : Rows Key Leaf :=
  fun k l => rows k l || decide (k = key ∧ l ∈ incoming)

def appendHistory (history : List (Key × Leaf)) (key : Key) (incoming : List Leaf) :=
  history ++ incoming.map (fun leaf => (key, leaf))

theorem empty_encodes : Encodes (fun _ _ => false) ([] : List (Key × Leaf)) := by
  intro k l
  simp

theorem grow_encodes [DecidableEq Key] [DecidableEq Leaf]
    {rows : Rows Key Leaf} {history : List (Key × Leaf)}
    (h : Encodes rows history) (key : Key) (incoming : List Leaf) :
    Encodes (grow rows key incoming) (appendHistory history key incoming) := by
  intro k l
  simp only [grow, Bool.or_eq_true, decide_eq_true_eq, appendHistory, List.mem_append]
  rw [h k l]
  constructor
  · intro hs
    rcases hs with old | ⟨hk, hl⟩
    · exact Or.inl old
    · exact Or.inr (List.mem_map.mpr ⟨l, hl, by subst k; rfl⟩)
  · intro hs
    rcases hs with old | fresh
    · exact Or.inl old
    · obtain ⟨x, hx, he⟩ := List.mem_map.mp fresh
      have hk : key = k := congrArg Prod.fst he
      have hl : x = l := congrArg Prod.snd he
      exact Or.inr ⟨hk.symm, hl ▸ hx⟩

def growAll [DecidableEq Key] [DecidableEq Leaf] (rows : Rows Key Leaf)
    (updates : List (Key × List Leaf)) : Rows Key Leaf :=
  updates.foldl (fun state input => grow state input.1 input.2) rows

def historyAll (history : List (Key × Leaf)) (updates : List (Key × List Leaf)) :=
  updates.foldl (fun state input => appendHistory state input.1 input.2) history

/-- Representation equality after every finite sequence, including repeated growth. -/
theorem growth_trace_encodes [DecidableEq Key] [DecidableEq Leaf]
    (updates : List (Key × List Leaf)) (rows : Rows Key Leaf) (history : List (Key × Leaf))
    (h : Encodes rows history) : Encodes (growAll rows updates) (historyAll history updates) := by
  induction updates generalizing rows history with
  | nil => exact h
  | cons input updates ih =>
    exact ih _ _ (grow_encodes h input.1 input.2)

/-- P5 is persistence of accepted logical leaves, not of historical delta objects. -/
theorem accepted_persists [DecidableEq Key] [DecidableEq Leaf]
    {rows : Rows Key Leaf} {k : Key} {l : Leaf} (h : rows k l = true)
    (key : Key) (incoming : List Leaf) : grow rows key incoming k l = true := by
  simp [grow, h]

/-- A replay captures precisely the newly accepted leaves at its admission epoch. -/
def fresh [DecidableEq Key] [DecidableEq Leaf] (rows : Rows Key Leaf)
    (key : Key) (incoming : List Leaf) : Rows Key Leaf :=
  fun k l => decide (k = key ∧ l ∈ incoming) && !rows k l

theorem fresh_delta_union [DecidableEq Key] [DecidableEq Leaf]
    (rows : Rows Key Leaf) (key : Key) (incoming : List Leaf) (k : Key) (l : Leaf) :
    grow rows key incoming k l = (rows k l || fresh rows key incoming k l) := by
  simp only [grow, fresh]
  cases rows k l <;> simp

def Indexed (rows : Rows Key Leaf) (position : Leaf → Position) (k : Key) (p : Position) : Prop :=
  ∃ leaf, rows k leaf = true ∧ position leaf = p

/-- One handle per position represents the union of all historical postings. -/
theorem index_refines {rows : Rows Key Leaf} {history : List (Key × Leaf)}
    (h : Encodes rows history) (position : Leaf → Position) (k : Key) (p : Position) :
    Indexed rows position k p ↔ ∃ leaf, (k, leaf) ∈ history ∧ position leaf = p := by
  unfold Indexed
  constructor <;> rintro ⟨leaf, hl, hp⟩
  · exact ⟨leaf, (h k leaf).mp hl, hp⟩
  · exact ⟨leaf, (h k leaf).mpr hl, hp⟩

def EffectAt (rows : Rows Key Leaf) (matchLeaf : Leaf → Prop)
    (transfer : Leaf → Effect → Prop) (key : Key) (effect : Effect) : Prop :=
  ∃ leaf, rows key leaf = true ∧ matchLeaf leaf ∧ transfer leaf effect

/-- Selection followed by a leaf-encoded transfer has identical closure effects.
    This theorem exposes, rather than assumes silently, the AP encoding obligation. -/
theorem effects_refine {rows : Rows Key Leaf} {history : List (Key × Leaf)}
    (h : Encodes rows history) (matchLeaf : Leaf → Prop) (transfer : Leaf → Effect → Prop)
    (key : Key) (effect : Effect) :
    EffectAt rows matchLeaf transfer key effect ↔
      ∃ leaf, (key, leaf) ∈ history ∧ matchLeaf leaf ∧ transfer leaf effect := by
  unfold EffectAt
  constructor <;> rintro ⟨leaf, hl, hm, ht⟩
  · exact ⟨leaf, (h key leaf).mp hl, hm, ht⟩
  · exact ⟨leaf, (h key leaf).mpr hl, hm, ht⟩

/-! E6 projection is permitted ONLY after full matching, nonzero-member and
    TaintTree checks. The rows here contain those already-admitted inputs.
    Full Ref includes caller method/context, premise, caller layer and call. -/

structure Binding (Ref : Type) where
  ref : Ref
  linkDemand : Bool
  deriving DecidableEq

structure Matched (Ref Root : Type) where
  binding : Binding Ref
  root : Root

def projectRows (rows : List (Matched Ref Root → Prop)) : List (Binding Ref → Prop) :=
  rows.map (fun row binding => ∃ matched, row matched ∧ matched.binding = binding)

inductive Choices {α : Type} : List (α → Prop) → List α → Prop where
  | nil : Choices [] []
  | cons {row rows x xs} : row x → Choices rows xs → Choices (row :: rows) (x :: xs)

/-- Deduplicating roots by binding in EACH ordered slot preserves every full
    binding combination. Equal bindings in different slots still fill both slots. -/
theorem projected_choices (rows : List (Matched Ref Root → Prop)) (bindings : List (Binding Ref)) :
    Choices (projectRows rows) bindings ↔
      ∃ matched, Choices rows matched ∧ matched.map Matched.binding = bindings := by
  induction rows generalizing bindings with
  | nil =>
    constructor
    · intro h
      cases h
      exact ⟨[], Choices.nil, rfl⟩
    · rintro ⟨matched, h, he⟩
      cases h
      cases he
      exact Choices.nil
  | cons row rows ih =>
    constructor
    · intro h
      cases h with
      | cons hx hs =>
        obtain ⟨m, hm, hb⟩ := hx
        obtain ⟨ms, hms, hbs⟩ := (ih _).mp hs
        exact ⟨m :: ms, Choices.cons hm hms, by simp only [List.map_cons, hb, hbs]⟩
    · rintro ⟨matched, h, he⟩
      cases h with
      | cons hm hms =>
        subst bindings
        exact Choices.cons ⟨_, hm, rfl⟩ ((ih _).mpr ⟨_, hms, rfl⟩)

structure Result (Ref Target : Type) where
  orderedBindings : List (Binding Ref)
  demand : Bool
  target : Target

def combination {Target : Type} (publicationDemand : Bool) (target : Target)
    (bindings : List (Binding Ref)) : Result Ref Target :=
  ⟨bindings, publicationDemand || bindings.any (fun b => b.linkDemand), target⟩

/-- Roots are absent from the result and from ordered resume/support metadata. -/
theorem combination_root_insensitive {Target : Type}
    (publicationDemand : Bool) (target : Target)
    (xs ys : List (Matched Ref Root)) (h : xs.map Matched.binding = ys.map Matched.binding) :
    combination publicationDemand target (xs.map Matched.binding) =
      combination publicationDemand target (ys.map Matched.binding) := by
  rw [h]

/-- Every old full-combination result exists after binding normalization, and no
    new ordered header/result is introduced by that quotient. -/
theorem combination_effects_quotient {Target : Type}
    (rows : List (Matched Ref Root → Prop)) (publicationDemand : Bool)
    (target : Target) (output : Result Ref Target) :
    (∃ matched, Choices rows matched ∧
      combination publicationDemand target (matched.map Matched.binding) = output) ↔
    (∃ bindings, Choices (projectRows rows) bindings ∧
      combination publicationDemand target bindings = output) := by
  constructor
  · rintro ⟨matched, hc, he⟩
    exact ⟨matched.map Matched.binding,
      (projected_choices rows _).mpr ⟨matched, hc, rfl⟩, he⟩
  · rintro ⟨bindings, hc, he⟩
    obtain ⟨matched, hm, hb⟩ := (projected_choices rows bindings).mp hc
    exact ⟨matched, hm, by rw [hb]; exact he⟩

/-- Caller layer is retained INSIDE Ref; it is not a contribution to the AP result layer. -/
theorem normal_result {Target : Type} (target : Target) (bindings : List (Binding Ref))
    (h : bindings.any (fun b => b.linkDemand) = false) :
    (combination false target bindings).demand = false := by
  simp [combination, h]

#print axioms grow_encodes
#print axioms growth_trace_encodes
#print axioms accepted_persists
#print axioms fresh_delta_union
#print axioms index_refines
#print axioms effects_refine
#print axioms projected_choices
#print axioms combination_root_insensitive
#print axioms combination_effects_quotient
#print axioms normal_result

end ApSpec.CumulativeSubscriptions
