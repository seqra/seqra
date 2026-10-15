/-
  Finite grouped encoding of the caller-free pending-answer relation.
  A row key is (FULL question geometry, FULL emitted initial shape); marks are
  an executable deduplicated finite set. Removing one question mark consumes it
  only at that geometry, across its answer shapes. This file proves expansion
  and update/consumption laws, not a Kotlin layout, byte bound or scheduler.
-/
import ApSpec.AddedFactsRemoval

namespace ApSpec.AddedFactsRemoval

section Packed
variable {Geometry Shape : Type} [DecidableEq Geometry] [DecidableEq Shape]

structure AnswerRow (Geometry Shape : Type) where
  key : Geometry × Shape
  marks : List Mark
  deriving DecidableEq, Repr

def putMark (key : Geometry × Shape) (mark : Mark) :
    List (AnswerRow Geometry Shape) → List (AnswerRow Geometry Shape)
  | [] => [⟨key, [mark]⟩]
  | row :: rows =>
    if row.key = key then {row with marks := remember mark row.marks} :: rows
    else row :: putMark key mark rows

/-- A mark's answer is scoped to one question geometry; other geometry and
    mark cells remain. Empty rows are removed rather than retained as tombstones. -/
def consumeMark (geometry : Geometry) (mark : Mark) :
    List (AnswerRow Geometry Shape) → List (AnswerRow Geometry Shape)
  | [] => []
  | row :: rows =>
    if row.key.1 = geometry then
      let marks := row.marks.filter (fun m => decide (m ≠ mark))
      if marks = [] then consumeMark geometry mark rows
      else ⟨row.key, marks⟩ :: consumeMark geometry mark rows
    else row :: consumeMark geometry mark rows

/-- Executable scalar expansion; no origin or supplier payload occurs. -/
def expandRows (rows : List (AnswerRow Geometry Shape)) : List ((Geometry × Shape) × Mark) :=
  rows.flatMap fun row => row.marks.map (fun mark => (row.key, mark))

def RowHolds (key : Geometry × Shape) (mark : Mark) : List (AnswerRow Geometry Shape) → Prop
  | [] => False
  | row :: rows => (row.key = key ∧ mark ∈ row.marks) ∨ RowHolds key mark rows

omit [DecidableEq Geometry] [DecidableEq Shape] in
@[simp] theorem expandRows_cons (row : AnswerRow Geometry Shape)
    (rows : List (AnswerRow Geometry Shape)) :
    expandRows (row :: rows) = row.marks.map (fun m => (row.key, m)) ++ expandRows rows := rfl

omit [DecidableEq Geometry] [DecidableEq Shape] in
 theorem row_holds_expansion (key : Geometry × Shape) (mark : Mark)
    (rows : List (AnswerRow Geometry Shape)) :
    RowHolds key mark rows ↔ (key, mark) ∈ expandRows rows := by
  induction rows with
  | nil => simp [RowHolds, expandRows]
  | cons row rows ih =>
    simp only [RowHolds, expandRows_cons, List.mem_append, List.mem_map, Prod.mk.injEq]
    rw [← ih]
    constructor
    · rintro (⟨hk, hm⟩ | ht)
      · exact Or.inl ⟨mark, hm, hk, rfl⟩
      · exact Or.inr ht
    · rintro (⟨m, hm, hk, rfl⟩ | ht)
      · exact Or.inl ⟨hk, hm⟩
      · exact Or.inr ht

theorem putMark_exact (rows : List (AnswerRow Geometry Shape))
    (key query : Geometry × Shape) (mark wanted : Mark) :
    RowHolds query wanted (putMark key mark rows) ↔
      (query = key ∧ wanted = mark) ∨ RowHolds query wanted rows := by
  induction rows with
  | nil => simp [putMark, RowHolds, eq_comm]
  | cons row rows ih =>
    by_cases same : row.key = key
    · simp only [putMark, if_pos same, RowHolds, mem_remember]
      constructor
      · rintro (⟨hk, hm | hm⟩ | ht)
        · exact Or.inl ⟨hk.symm.trans same, hm⟩
        · exact Or.inr (Or.inl ⟨hk, hm⟩)
        · exact Or.inr (Or.inr ht)
      · rintro (⟨hk, hm⟩ | ⟨hr, hm⟩ | ht)
        · exact Or.inl ⟨same.trans hk.symm, Or.inl hm⟩
        · exact Or.inl ⟨hr, Or.inr hm⟩
        · exact Or.inr ht
    · simp only [putMark, if_neg same, RowHolds, ih]
      constructor
      · rintro (h | h | h)
        · exact Or.inr (Or.inl h)
        · exact Or.inl h
        · exact Or.inr (Or.inr h)
      · rintro (h | h | h)
        · exact Or.inr (Or.inl h)
        · exact Or.inl h
        · exact Or.inr (Or.inr h)

omit [DecidableEq Shape] in
/-- Exact consumption, including all shapes at this geometry. Same mark at a
    different geometry and other marks at the same geometry are preserved. -/
theorem consumeMark_exact (rows : List (AnswerRow Geometry Shape))
    (geometry : Geometry) (mark : Mark) (query : Geometry × Shape) (wanted : Mark) :
    RowHolds query wanted (consumeMark geometry mark rows) ↔
      RowHolds query wanted rows ∧ ¬ (query.1 = geometry ∧ wanted = mark) := by
  induction rows with
  | nil => simp [consumeMark, RowHolds]
  | cons row rows ih =>
    by_cases hg : row.key.1 = geometry
    · simp only [consumeMark, if_pos hg]
      by_cases empty : row.marks.filter (fun m => decide (m ≠ mark)) = []
      · simp only [empty, ite_true, ih, RowHolds]
        constructor
        · rintro ⟨ht, hn⟩; exact ⟨Or.inr ht, hn⟩
        · rintro ⟨(⟨hk, hm⟩ | ht), hn⟩
          · have ne : wanted ≠ mark := fun he => hn ⟨hk ▸ hg, he⟩
            have mem : wanted ∈ row.marks.filter (fun m => decide (m ≠ mark)) := by
              simp only [List.mem_filter, decide_eq_true_eq]; exact ⟨hm, ne⟩
            rw [empty] at mem; cases mem
          · exact ⟨ht, hn⟩
      · simp only [if_neg empty, RowHolds, ih, List.mem_filter, decide_eq_true_eq]
        constructor
        · rintro (⟨hk, hm, ne⟩ | ⟨ht, hn⟩)
          · exact ⟨Or.inl ⟨hk, hm⟩, fun h => ne h.2⟩
          · exact ⟨Or.inr ht, hn⟩
        · rintro ⟨(⟨hk, hm⟩ | ht), hn⟩
          · exact Or.inl ⟨hk, hm, fun he => hn ⟨hk ▸ hg, he⟩⟩
          · exact Or.inr ⟨ht, hn⟩
    · simp only [consumeMark, if_neg hg, RowHolds, ih]
      constructor
      · rintro (⟨hk, hm⟩ | ⟨ht, hn⟩)
        · exact ⟨Or.inl ⟨hk, hm⟩, fun he => hg (hk.symm ▸ he.1)⟩
        · exact ⟨Or.inr ht, hn⟩
      · rintro ⟨(h | ht), hn⟩
        · exact Or.inl h
        · exact Or.inr ⟨ht, hn⟩

/-- Inserted marks are deduplicated within the selected row. -/
theorem putMark_idempotent (rows : List (AnswerRow Geometry Shape))
    (key : Geometry × Shape) (mark : Mark) :
    putMark key mark (putMark key mark rows) = putMark key mark rows := by
  induction rows with
  | nil => simp [putMark, remember]
  | cons row rows ih =>
    by_cases hk : row.key = key
    · simp [putMark, hk, remember_idempotent]
    · simp [putMark, hk, ih]

/-- Physical row count is independent of caller multiplicity for a fixed answer
    row. This is a logical cell theorem, not a concrete memory/cost estimate. -/
theorem one_row_many_marks (key : Geometry × Shape) (marks : List Mark) :
    (marks.foldl (fun rows mark => putMark key mark rows) [⟨key, []⟩]).length = 1 := by
  have invariant : ∀ (marks : List Mark) (stored : List Mark),
      (marks.foldl (fun rows mark => putMark key mark rows) [⟨key, stored⟩]).length = 1 := by
    intro ms
    induction ms with
    | nil => intro stored; rfl
    | cons m ms ih =>
      intro stored
      simpa only [List.foldl_cons, putMark, ite_true] using ih (remember m stored)
  exact invariant marks []

/-- Decoder into the exact question-mark/answer-shape cells used by State.
    This is a logical expansion for the proof, not a required runtime array. -/
def memoCells (rows : List (AnswerRow Geometry Shape)) : List ((Geometry × Mark) × Shape) :=
  (expandRows rows).map (fun p => ((p.1.1, p.2), p.1.2))

omit [DecidableEq Geometry] [DecidableEq Shape] in
 theorem memoCells_iff (rows : List (AnswerRow Geometry Shape))
    (geometry : Geometry) (shape : Shape) (mark : Mark) :
    ((geometry, mark), shape) ∈ memoCells rows ↔ RowHolds (geometry, shape) mark rows := by
  rw [row_holds_expansion]
  simp only [memoCells, List.mem_map]
  constructor
  · rintro ⟨⟨⟨g, s⟩, m⟩, hm, he⟩
    have eg : g = geometry := congrArg (fun p => p.1.1) he
    have es : s = shape := congrArg Prod.snd he
    have em : m = mark := congrArg (fun p => p.1.2) he
    subst g; subst s; subst m
    exact hm
  · intro hm; exact ⟨((geometry, shape), mark), hm, rfl⟩

/-- Packed insertion exactly refines remember on scalar logical memberships. -/
theorem packed_insert_refines (rows : List (AnswerRow Geometry Shape))
    (g queryG : Geometry) (s queryS : Shape) (mark wanted : Mark) :
    ((queryG, wanted), queryS) ∈ memoCells (putMark (g, s) mark rows) ↔
      ((queryG, wanted), queryS) ∈ remember ((g, mark), s) (memoCells rows) := by
  rw [memoCells_iff, putMark_exact, mem_remember, memoCells_iff]
  simp only [Prod.mk.injEq]
  constructor
  · rintro (⟨⟨hg, hs⟩, hm⟩ | ht)
    · exact Or.inl ⟨⟨hg, hm⟩, hs⟩
    · exact Or.inr ht
  · rintro (⟨⟨hg, hm⟩, hs⟩ | ht)
    · exact Or.inl ⟨⟨hg, hs⟩, hm⟩
    · exact Or.inr ht

omit [DecidableEq Shape] in
/-- Packed consumption is precisely scalar request removal for (geometry,mark).
    It cannot consume another role/slot/pattern/E key or a different mark. -/
theorem packed_consume_refines (rows : List (AnswerRow Geometry Shape))
    (g queryG : Geometry) (s : Shape) (mark wanted : Mark) :
    ((queryG, wanted), s) ∈ memoCells (consumeMark g mark rows) ↔
      ((queryG, wanted), s) ∈
        (memoCells rows).filter (fun p => decide (p.1 ≠ (g, mark))) := by
  rw [memoCells_iff, consumeMark_exact]
  simp only [List.mem_filter, decide_eq_true_eq, memoCells_iff]
  constructor
  · rintro ⟨h, hn⟩
    exact ⟨h, fun e => hn ⟨congrArg Prod.fst e, congrArg Prod.snd e⟩⟩
  · rintro ⟨h, hn⟩
    exact ⟨h, fun e => hn (Prod.ext e.1 e.2)⟩

end Packed

namespace Certificate

def rows : List (AnswerRow QuestionGeometry AnswerShape) :=
  putMark ((q [4]).1, ⟨3, [4], .exact⟩) 7
    (putMark ((q [4]).1, ⟨3, [4], .exact⟩) 8
      (putMark ((q [5]).1, ⟨3, [5], .exact⟩) 7 []))

theorem packed_consumption :
    rows.length = 2 ∧
    (consumeMark (q [4]).1 7 rows).length = 2 ∧
    (expandRows (consumeMark (q [4]).1 7 rows)).length = 2 ∧
    RowHolds ((q [4]).1, ⟨3, [4], .exact⟩) 8 (consumeMark (q [4]).1 7 rows) ∧
    RowHolds ((q [5]).1, ⟨3, [5], .exact⟩) 7 (consumeMark (q [4]).1 7 rows) := by
  simp only [row_holds_expansion]
  decide

end Certificate

#print axioms memoCells_iff
#print axioms packed_insert_refines
#print axioms packed_consume_refines
#print axioms row_holds_expansion
#print axioms putMark_exact
#print axioms consumeMark_exact
#print axioms putMark_idempotent
#print axioms one_row_many_marks
#print axioms Certificate.packed_consumption

end ApSpec.AddedFactsRemoval
