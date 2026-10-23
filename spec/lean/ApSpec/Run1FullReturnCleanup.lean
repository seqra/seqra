/-
FULL run-1 selected-mark return-cleaner blocks.

This executable finite spatial kernel models nonstatic cleaners, empty E,
collector-fixed headers, and DEMAND TAINT input. It includes the PART
complements and the BELOW exact-point exception from Clean.kt. It does
not model arbitrary field cuts, STATIC cleaners, or header-changing cover.
The independent serial definition executes one mark at a time. The bulk
definition selects a mark set. Equality is finite fact membership, not
packet order or handler allocation IDs.
-/
import Std

namespace ApSpec.Run1FullReturnCleanup
set_option autoImplicit false
set_option maxHeartbeats 4000000

inductive Reach where | exact | below | atAndBelow
  deriving DecidableEq, Repr

/-- Relative to one fixed cleaner path. Near is its immediate parent. -/
inductive Cell where
  | apartExact | apartExactAny | apartAny | apartMust
  | farExact | farExactAny | farAny | farMust
  | nearExact | nearExactAny | nearAny | nearMust
  | atExact | atExactAny | atAny | atMust
  | belowExact | belowExactAny | belowAny | belowMust
  deriving DecidableEq, Repr

inductive Position where | disjoint | inside | part
  deriving DecidableEq, Repr

def position (reach : Reach) (cell : Cell) : Position :=
  match cell with
  | .apartExact | .apartExactAny | .apartAny | .apartMust
  | .farExact | .farExactAny | .nearExact | .nearExactAny => .disjoint
  | .farAny | .farMust | .nearAny | .nearMust => .part
  | .atExact | .atExactAny => if reach = .below then .disjoint else .inside
  | .atAny | .atMust => if reach = .atAndBelow then .inside else .part
  | .belowExact | .belowExactAny | .belowAny | .belowMust =>
      if reach = .exact then .disjoint else .inside

/-- Actual selected-mark output law in the restricted run-1 scope.
    Every returned cell is DEMAND, with empty E. A retained PART cell is
    its DEMAND complement, not a claim that cleaning had no uncertainty. -/
def image (reach : Reach) (cell : Cell) : List Cell :=
  match position reach cell with
  | .disjoint => [cell]
  | .inside => []
  | .part =>
      if cell = .nearMust ∧ reach ≠ .exact then
        if reach = .below then [cell, .atExact] else [cell]
      else if (cell = .atAny ∨ cell = .atMust) ∧ reach = .below then [.atExact]
      else [cell]

/-- These two exact-carrier cases are extra proof representation only.
    Actual EXACT scalar observations ignore the unused tree anyTail; see
    eraseExactCarrier. A newly emitted exact point uses `atExact`. -/
theorem proof_carrier_variant_below :
    image .below .atExactAny = [.atExactAny] := rfl

def eraseExactCarrier : Cell → Cell
  | .apartExactAny => .apartExact
  | .farExactAny => .farExact
  | .nearExactAny => .nearExact
  | .atExactAny => .atExact
  | .belowExactAny => .belowExact
  | cell => cell

theorem erase_exact_carrier_idempotent (cell : Cell) :
    eraseExactCarrier (eraseExactCarrier cell) = eraseExactCarrier cell := by cases cell <;> rfl

theorem image_erases_exact_carrier (reach : Reach) (cell : Cell) :
    (image reach cell).map eraseExactCarrier = image reach (eraseExactCarrier cell) := by
  cases reach <;> cases cell <;> simp [image,position,eraseExactCarrier]

inductive Layer where | normal | demand deriving DecidableEq, Repr
def demandImage (reach : Reach) (cell : Cell) : List (Cell × Layer × List Nat) :=
  (image reach cell).map (fun c => (c,.demand,[]))

theorem exact_root_partial_demand_header :
    demandImage .exact .atAny = [(.atAny,.demand,[])] := rfl

theorem exact_root_any_is_partial : position .exact .atAny = .part := rfl
theorem exact_root_any_keeps_demand : image .exact .atAny = [.atAny] := rfl
theorem exact_root_must_keeps_demand : image .exact .atMust = [.atMust] := rfl
theorem below_any_becomes_exact : image .below .atAny = [.atExact] := rfl
theorem below_exact_is_stable : image .below .atExact = [.atExact] := rfl
theorem below_parent_must_keeps_both :
    image .below .nearMust = [.nearMust, .atExact] := rfl

theorem image_transitive (reach : Reach) (a b c : Cell)
    (ab : b ∈ image reach a) (bc : c ∈ image reach b) : c ∈ image reach a := by
  cases reach <;> cases a <;> cases b <;>
    simp_all [image, position]

theorem image_output_self (reach : Reach) (a b : Cell)
    (ab : b ∈ image reach a) : b ∈ image reach b := by
  cases reach <;> cases a <;> cases b <;>
    simp_all [image, position]

structure Located where
  path : List Nat
  cell : Cell
  deriving DecidableEq, Repr

def locatedImage (cleanerPath : List Nat) (reach : Reach) (a : Located) : List Located :=
  (image reach a.cell).map (fun cell =>
    if cell = a.cell then a else ⟨cleanerPath,cell⟩)

theorem only_fresh_cell (reach : Reach) (a b : Cell)
    (member : b ∈ image reach a) (different : b ≠ a) :
    reach = .below ∧ b = .atExact := by
  cases reach <;> cases a <;> cases b <;> simp_all [image,position]

/-- Each path is preserved independently. The sole new path is exactly
    the fixed cleaner path, never an arbitrary representative of a region. -/
theorem located_old_or_fresh (cleanerPath : List Nat) (reach : Reach) (a b : Located)
    (member : b ∈ locatedImage cleanerPath reach a) :
    b = a ∨ (reach = .below ∧ b = ⟨cleanerPath,.atExact⟩) := by
  obtain ⟨cell,hcell,eq⟩ := List.mem_map.mp member
  by_cases same : cell = a.cell
  · exact Or.inl (by simpa [same] using eq.symm)
  · obtain ⟨hr,hc⟩ := only_fresh_cell reach a.cell cell hcell same
    have beq : b = (⟨cleanerPath,cell⟩ : Located) := by simpa [same] using eq.symm
    exact Or.inr ⟨hr,by simpa [hc] using beq⟩

theorem located_output_self (cleanerPath : List Nat) (reach : Reach) (a b : Located)
    (member : b ∈ locatedImage cleanerPath reach a) : b ∈ locatedImage cleanerPath reach b := by
  obtain ⟨cell,hcell,eq⟩ := List.mem_map.mp member
  have preserved : b.cell = cell := by
    by_cases same : cell = a.cell
    · have beq : b = a := by simpa [same] using eq.symm
      simpa only [beq] using same.symm
    · have beq : b = (⟨cleanerPath,cell⟩ : Located) := by simpa [same] using eq.symm
      simp [beq]
  apply List.mem_map.mpr
  refine ⟨b.cell,?_,by simp⟩
  rw [preserved]
  exact image_output_self reach a.cell cell hcell

theorem located_transitive (cleanerPath : List Nat) (reach : Reach) (a b c : Located)
    (ab : b ∈ locatedImage cleanerPath reach a) (bc : c ∈ locatedImage cleanerPath reach b) :
    c ∈ locatedImage cleanerPath reach a := by
  rcases located_old_or_fresh cleanerPath reach a b ab with same | ⟨rfl,rfl⟩
  · simpa only [same] using bc
  · have eq : c = (⟨cleanerPath,.atExact⟩ : Located) := by
      simpa [locatedImage,image,position] using bc
    simpa only [eq] using ab

theorem located_image_idempotent (cleanerPath : List Nat) (reach : Reach) (a : Located) :
    (∀ out, out ∈ (locatedImage cleanerPath reach a).flatMap (locatedImage cleanerPath reach) ↔
      out ∈ locatedImage cleanerPath reach a) := by
  intro out
  simp only [List.mem_flatMap]
  constructor
  · rintro ⟨middle,hm,ho⟩
    exact located_transitive cleanerPath reach a middle out hm ho
  · intro ho
    exact ⟨out,ho,located_output_self cleanerPath reach a out ho⟩

def selectedCells (reach : Reach) (active : Bool) (cell : Cell) : List Cell :=
  if active then image reach cell else [cell]

def Rel (reach : Reach) (active : Bool) (a b : Cell) : Prop :=
  b ∈ selectedCells reach active a

theorem rel_compose (reach : Reach) (x y : Bool) (a c : Cell) :
    (∃ b, Rel reach x a b ∧ Rel reach y b c) ↔ Rel reach (x || y) a c := by
  cases x <;> cases y
  · simp [Rel, selectedCells]
  · simp [Rel, selectedCells]
  · simp [Rel, selectedCells]
  · simp only [Bool.or_self, Rel, selectedCells, ↓reduceIte]
    constructor
    · rintro ⟨b, ab, bc⟩
      exact image_transitive reach a b c ab bc
    · intro ac
      exact ⟨c, ac, image_output_self reach a c ac⟩

abbrev Atom := Cell × Nat
abbrev Facts := List Atom
def Obs {α : Type} (a b : List α) : Prop := ∀ x, x ∈ a ↔ x ∈ b

def eraseAtom (atom : Atom) : Atom := (eraseExactCarrier atom.1,atom.2)
def scalarFacts (facts : Facts) : Facts := facts.map eraseAtom
def ScalarObs (a b : Facts) : Prop := Obs (scalarFacts a) (scalarFacts b)

theorem obs_refl {α : Type} (a : List α) : Obs a a := fun _ => Iff.rfl
theorem obs_trans {α : Type} {a b c : List α} (ab : Obs a b) (bc : Obs b c) :
    Obs a c := fun x => (ab x).trans (bc x)

namespace Packed
/- A reusable executable mark-selection algebra. Its only spatial gate is
one-step image idempotence, proved above for the path-preserving kernel. -/
def cells {β : Type} (kernel : β → List β) (active : Bool) (a : β) : List β :=
  if active then kernel a else [a]
def outputs {β : Type} (kernel : β → List β) (selected : Nat → Bool) (a : β × Nat) : List (β × Nat) :=
  (cells kernel (selected a.2) a.1).map (fun b => (b,a.2))
def execute {β : Type} (kernel : β → List β) (selected : Nat → Bool)
    (input : List (β × Nat)) : List (β × Nat) := input.flatMap (outputs kernel selected)
def bulk {β : Type} (kernel : β → List β) (marks : List Nat)
    (input : List (β × Nat)) : List (β × Nat) := execute kernel (fun t => decide (t ∈ marks)) input
def serial {β : Type} (kernel : β → List β) : List Nat → List (β × Nat) → List (β × Nat)
  | [], input => input
  | mark :: rest, input => serial kernel rest (bulk kernel [mark] input)

theorem mem_outputs {β : Type} (kernel : β → List β) (selected : Nat → Bool)
    (a b : β) (s t : Nat) :
    (b,t) ∈ outputs kernel selected (a,s) ↔ s = t ∧ b ∈ cells kernel (selected t) a := by
  simp only [outputs,List.mem_map]
  constructor
  · rintro ⟨b,hb,eq⟩; cases eq; exact ⟨rfl,hb⟩
  · rintro ⟨rfl,hb⟩; exact ⟨b,hb,rfl⟩

theorem mem_execute {β : Type} (kernel : β → List β) (selected : Nat → Bool)
    (input : List (β × Nat)) (b : β) (t : Nat) :
    (b,t) ∈ execute kernel selected input ↔
      ∃ a, (a,t) ∈ input ∧ b ∈ cells kernel (selected t) a := by
  simp only [execute,List.mem_flatMap]
  constructor
  · rintro ⟨⟨a,s⟩,ha,hb⟩
    obtain ⟨rfl,hrel⟩ := (mem_outputs kernel selected a b s t).mp hb
    exact ⟨a,ha,hrel⟩
  · rintro ⟨a,ha,hb⟩
    exact ⟨(a,t),ha,(mem_outputs kernel selected a b t t).mpr ⟨rfl,hb⟩⟩

theorem execute_obs {β : Type} (kernel : β → List β) (selected : Nat → Bool)
    {a b : List (β × Nat)} (same : Obs a b) :
    Obs (execute kernel selected a) (execute kernel selected b) := by
  rintro ⟨cell,mark⟩
  simp only [mem_execute]
  constructor <;> rintro ⟨src,hs,hr⟩
  · exact ⟨src,(same _).mp hs,hr⟩
  · exact ⟨src,(same _).mpr hs,hr⟩

theorem cells_compose {β : Type} (kernel : β → List β)
    (idempotent : ∀ a, Obs ((kernel a).flatMap kernel) (kernel a))
    (x y : Bool) (a c : β) :
    (∃ b, b ∈ cells kernel x a ∧ c ∈ cells kernel y b) ↔ c ∈ cells kernel (x || y) a := by
  cases x <;> cases y
  · simp [cells]
  · simp [cells]
  · simp [cells]
  · simpa [cells,List.mem_flatMap] using idempotent a c

theorem composition {β : Type} (kernel : β → List β)
    (idempotent : ∀ a, Obs ((kernel a).flatMap kernel) (kernel a))
    (first second : Nat → Bool) (input : List (β × Nat)) :
    Obs (execute kernel second (execute kernel first input))
      (execute kernel (fun t => first t || second t) input) := by
  rintro ⟨cell,mark⟩
  simp only [mem_execute]
  constructor
  · rintro ⟨middle,⟨src,hs,hf⟩,hsec⟩
    exact ⟨src,hs,(cells_compose kernel idempotent (first mark) (second mark) src cell).mp
      ⟨middle,hf,hsec⟩⟩
  · rintro ⟨src,hs,hboth⟩
    obtain ⟨middle,hf,hsec⟩ :=
      (cells_compose kernel idempotent (first mark) (second mark) src cell).mpr hboth
    exact ⟨middle,⟨src,hs,hf⟩,hsec⟩

theorem bulk_empty {β : Type} (kernel : β → List β) (input : List (β × Nat)) :
    bulk kernel [] input = input := by
  induction input with
  | nil => rfl
  | cons a rest ih => simp [bulk,execute,outputs,cells] at ih ⊢; exact ih

theorem serial_bulk {β : Type} (kernel : β → List β)
    (idempotent : ∀ a, Obs ((kernel a).flatMap kernel) (kernel a))
    (marks : List Nat) (input : List (β × Nat)) :
    Obs (serial kernel marks input) (bulk kernel marks input) := by
  induction marks generalizing input with
  | nil => rw [serial,bulk_empty]; exact obs_refl _
  | cons mark rest ih =>
      have comp := composition kernel idempotent (fun t => decide (t ∈ [mark]))
        (fun t => decide (t ∈ rest)) input
      have same : (fun t => decide (t ∈ [mark]) || decide (t ∈ rest)) =
          (fun t => decide (t ∈ mark :: rest)) := by
        funext t
        by_cases h : t = mark <;> by_cases k : t ∈ rest <;> simp [h,k]
      exact obs_trans (ih (bulk kernel [mark] input)) (by simpa only [bulk,same] using comp)
end Packed

/-- Full concrete paths and marks survive the serial-to-bulk proof.
    Distinct `farAny` or `belowAny` paths are not identified. -/
theorem located_serial_bulk (cleanerPath : List Nat) (reach : Reach)
    (marks : List Nat) (input : List (Located × Nat)) :
    Obs (Packed.serial (locatedImage cleanerPath reach) marks input)
      (Packed.bulk (locatedImage cleanerPath reach) marks input) :=
  Packed.serial_bulk _ (located_image_idempotent cleanerPath reach) marks input

def atomOut (reach : Reach) (selected : Nat → Bool) (a : Atom) : Facts :=
  (selectedCells reach (selected a.2) a.1).map (fun c => (c, a.2))

def apply (reach : Reach) (selected : Nat → Bool) (facts : Facts) : Facts :=
  facts.flatMap (atomOut reach selected)

theorem mem_atomOut (reach : Reach) (selected : Nat → Bool) (a b : Cell) (s t : Nat) :
    (b,t) ∈ atomOut reach selected (a,s) ↔ s = t ∧ Rel reach (selected t) a b := by
  simp only [atomOut, List.mem_map]
  constructor
  · rintro ⟨c, hc, eq⟩
    cases eq
    exact ⟨rfl, hc⟩
  · rintro ⟨rfl, hc⟩
    exact ⟨b, hc, rfl⟩

theorem mem_apply (reach : Reach) (selected : Nat → Bool) (facts : Facts) (b : Cell) (t : Nat) :
    (b,t) ∈ apply reach selected facts ↔
      ∃ a, (a,t) ∈ facts ∧ Rel reach (selected t) a b := by
  simp only [apply, List.mem_flatMap]
  constructor
  · rintro ⟨⟨a,s⟩, hin, hout⟩
    obtain ⟨rfl, hrel⟩ := (mem_atomOut reach selected a b s t).mp hout
    exact ⟨a, hin, hrel⟩
  · rintro ⟨a, hin, hrel⟩
    exact ⟨(a,t), hin, (mem_atomOut reach selected a b t t).mpr ⟨rfl,hrel⟩⟩

theorem apply_obs (reach : Reach) (selected : Nat → Bool) {a b : Facts}
    (h : Obs a b) : Obs (apply reach selected a) (apply reach selected b) := by
  rintro ⟨cell, mark⟩
  simp only [mem_apply]
  constructor <;> rintro ⟨src, hs, hr⟩
  · exact ⟨src, (h _).mp hs, hr⟩
  · exact ⟨src, (h _).mpr hs, hr⟩

theorem atomOut_erases_carrier (reach : Reach) (selected : Nat → Bool) (a : Atom) :
    (atomOut reach selected a).map eraseAtom = atomOut reach selected (eraseAtom a) := by
  rcases a with ⟨cell,mark⟩
  cases flag : selected mark <;> cases reach <;> cases cell <;>
    simp [atomOut,selectedCells,flag,eraseAtom,image,position,eraseExactCarrier]

theorem apply_erases_carrier (reach : Reach) (selected : Nat → Bool) (facts : Facts) :
    scalarFacts (apply reach selected facts) = apply reach selected (scalarFacts facts) := by
  induction facts with
  | nil => rfl
  | cons atom rest ih =>
      simpa [scalarFacts,apply,List.map_append,atomOut_erases_carrier] using
        congrArg (List.append (atomOut reach selected (eraseAtom atom))) ih

theorem apply_scalar_obs (reach : Reach) (selected : Nat → Bool) {a b : Facts}
    (same : ScalarObs a b) : ScalarObs (apply reach selected a) (apply reach selected b) := by
  unfold ScalarObs
  rw [apply_erases_carrier,apply_erases_carrier]
  exact apply_obs reach selected same

theorem apply_composition (reach : Reach) (first second : Nat → Bool) (facts : Facts) :
    Obs (apply reach second (apply reach first facts))
      (apply reach (fun t => first t || second t) facts) := by
  rintro ⟨cell, mark⟩
  simp only [mem_apply]
  constructor
  · rintro ⟨middle, ⟨src, hs, hfirst⟩, hsecond⟩
    exact ⟨src, hs, (rel_compose reach (first mark) (second mark) src cell).mp
      ⟨middle,hfirst,hsecond⟩⟩
  · rintro ⟨src, hs, hboth⟩
    obtain ⟨middle, hfirst, hsecond⟩ :=
      (rel_compose reach (first mark) (second mark) src cell).mpr hboth
    exact ⟨middle, ⟨src,hs,hfirst⟩, hsecond⟩

def flags (marks : List Nat) (mark : Nat) : Bool := decide (mark ∈ marks)
def bulk (reach : Reach) (marks : List Nat) (facts : Facts) : Facts :=
  apply reach (flags marks) facts

def serial (reach : Reach) : List Nat → Facts → Facts
  | [], facts => facts
  | mark :: rest, facts => serial reach rest (bulk reach [mark] facts)

theorem bulk_nil (reach : Reach) (facts : Facts) : bulk reach [] facts = facts := by
  induction facts with
  | nil => rfl
  | cons a rest ih => simp [bulk, apply, atomOut, flags, selectedCells] at ih ⊢; exact ih

theorem bulk_cons (reach : Reach) (mark : Nat) (rest : List Nat) (facts : Facts) :
    Obs (bulk reach rest (bulk reach [mark] facts)) (bulk reach (mark :: rest) facts) := by
  have h := apply_composition reach (flags [mark]) (flags rest) facts
  have hf : (fun t => flags [mark] t || flags rest t) = flags (mark :: rest) := by
    funext t
    by_cases h : t = mark <;> by_cases k : t ∈ rest <;> simp [flags,h,k]
  simpa only [bulk, hf] using h

/-- Serial order is the actual left-to-right raw step order. -/
theorem serial_bulk (reach : Reach) (marks : List Nat) (facts : Facts) :
    Obs (serial reach marks facts) (bulk reach marks facts) := by
  induction marks generalizing facts with
  | nil => rw [serial,bulk_nil]; exact obs_refl facts
  | cons mark rest ih =>
      exact obs_trans (ih (bulk reach [mark] facts)) (bulk_cons reach mark rest facts)

theorem obs_map {α β : Type} (map : α → β) {a b : List α} (same : Obs a b) :
    Obs (a.map map) (b.map map) := by
  intro out
  simp only [List.mem_map]
  constructor <;> rintro ⟨src,hs,eq⟩
  · exact ⟨src,(same src).mp hs,eq⟩
  · exact ⟨src,(same src).mpr hs,eq⟩

def eraseLocated (a : Located) : Located := ⟨a.path,eraseExactCarrier a.cell⟩
def eraseLocatedAtom (a : Located × Nat) : Located × Nat := (eraseLocated a.1,a.2)
def LocatedScalarObs (a b : List (Located × Nat)) : Prop :=
  Obs (a.map eraseLocatedAtom) (b.map eraseLocatedAtom)

theorem located_image_erases_carrier (cleanerPath : List Nat) (reach : Reach) (a : Located) :
    (locatedImage cleanerPath reach a).map eraseLocated =
      locatedImage cleanerPath reach (eraseLocated a) := by
  rcases a with ⟨path,cell⟩
  cases reach <;> cases cell <;>
    simp [locatedImage,image,position,eraseLocated,eraseExactCarrier]

theorem located_outputs_erasure (cleanerPath : List Nat) (reach : Reach)
    (selected : Nat → Bool) (a : Located × Nat) :
    (Packed.outputs (locatedImage cleanerPath reach) selected a).map eraseLocatedAtom =
      Packed.outputs (locatedImage cleanerPath reach) selected (eraseLocatedAtom a) := by
  rcases a with ⟨⟨path,cell⟩,mark⟩
  cases flag : selected mark <;> cases reach <;> cases cell <;>
    simp [Packed.outputs,Packed.cells,flag,locatedImage,image,position,
      eraseLocatedAtom,eraseLocated,eraseExactCarrier]

theorem located_execute_erasure (cleanerPath : List Nat) (reach : Reach)
    (selected : Nat → Bool) (input : List (Located × Nat)) :
    (Packed.execute (locatedImage cleanerPath reach) selected input).map eraseLocatedAtom =
      Packed.execute (locatedImage cleanerPath reach) selected (input.map eraseLocatedAtom) := by
  induction input with
  | nil => rfl
  | cons atom rest ih =>
      simpa [Packed.execute,List.map_append,located_outputs_erasure] using
        congrArg (List.append
          (Packed.outputs (locatedImage cleanerPath reach) selected (eraseLocatedAtom atom))) ih

theorem located_execute_scalar_obs (cleanerPath : List Nat) (reach : Reach)
    (selected : Nat → Bool) {a b : List (Located × Nat)} (same : LocatedScalarObs a b) :
    LocatedScalarObs (Packed.execute (locatedImage cleanerPath reach) selected a)
      (Packed.execute (locatedImage cleanerPath reach) selected b) := by
  unfold LocatedScalarObs
  rw [located_execute_erasure,located_execute_erasure]
  exact Packed.execute_obs _ selected same

theorem located_serial_bulk_scalar (cleanerPath : List Nat) (reach : Reach)
    (marks : List Nat) (input : List (Located × Nat)) :
    LocatedScalarObs (Packed.serial (locatedImage cleanerPath reach) marks input)
      (Packed.bulk (locatedImage cleanerPath reach) marks input) :=
  obs_map eraseLocatedAtom (located_serial_bulk cleanerPath reach marks input)

def locatedSerialNormalized (normalize : List (Located × Nat) → List (Located × Nat))
    (cleanerPath : List Nat) (reach : Reach) : List Nat → List (Located × Nat) → List (Located × Nat)
  | [], input => input
  | mark :: rest, input => locatedSerialNormalized normalize cleanerPath reach rest
      (normalize (Packed.bulk (locatedImage cleanerPath reach) [mark] input))

theorem located_serial_scalar_obs (cleanerPath : List Nat) (reach : Reach) (marks : List Nat)
    {a b : List (Located × Nat)} (same : LocatedScalarObs a b) :
    LocatedScalarObs (Packed.serial (locatedImage cleanerPath reach) marks a)
      (Packed.serial (locatedImage cleanerPath reach) marks b) := by
  induction marks generalizing a b with
  | nil => exact same
  | cons mark rest ih => exact ih (located_execute_scalar_obs cleanerPath reach _ same)

theorem located_serial_with_normalization
    (normalize : List (Located × Nat) → List (Located × Nat))
    (normalizationLaw : ∀ input, LocatedScalarObs (normalize input) input)
    (cleanerPath : List Nat) (reach : Reach) (marks : List Nat) (input : List (Located × Nat)) :
    LocatedScalarObs (locatedSerialNormalized normalize cleanerPath reach marks input)
      (Packed.serial (locatedImage cleanerPath reach) marks input) := by
  induction marks generalizing input with
  | nil => exact obs_refl _
  | cons mark rest ih =>
      exact obs_trans (ih (normalize (Packed.bulk (locatedImage cleanerPath reach) [mark] input)))
        (located_serial_scalar_obs cleanerPath reach rest (normalizationLaw _))

/-- Production adapter observes EXACT independently of unused anyTail.
    ANY and MUST stay distinct. No raw packed-tree header equality is claimed. -/
theorem serial_bulk_scalar (reach : Reach) (marks : List Nat) (facts : Facts) :
    ScalarObs (serial reach marks facts) (bulk reach marks facts) :=
  obs_map eraseAtom (serial_bulk reach marks facts)

def serialNormalized (normalize : Facts → Facts) (reach : Reach) : List Nat → Facts → Facts
  | [], facts => facts
  | mark :: rest, facts =>
      serialNormalized normalize reach rest (normalize (bulk reach [mark] facts))

theorem scalar_obs_trans {a b c : Facts} (ab : ScalarObs a b) (bc : ScalarObs b c) :
    ScalarObs a c := obs_trans ab bc

/-- Legal intermediate packing/collector normalization is an explicit
    scalar preservation gate, not assumed equality of raw headers. -/
theorem serial_scalar_obs (reach : Reach) (marks : List Nat) {a b : Facts}
    (same : ScalarObs a b) : ScalarObs (serial reach marks a) (serial reach marks b) := by
  induction marks generalizing a b with
  | nil => exact same
  | cons mark rest ih => exact ih (apply_scalar_obs reach (flags [mark]) same)

theorem serial_with_normalization (normalize : Facts → Facts)
    (normalizationLaw : ∀ facts, ScalarObs (normalize facts) facts)
    (reach : Reach) (marks : List Nat) (facts : Facts) :
    ScalarObs (serialNormalized normalize reach marks facts) (serial reach marks facts) := by
  induction marks generalizing facts with
  | nil => exact obs_refl _
  | cons mark rest ih =>
      exact scalar_obs_trans (ih (normalize (bulk reach [mark] facts)))
        (serial_scalar_obs reach rest (normalizationLaw (bulk reach [mark] facts)))

theorem bulk_idempotent (reach : Reach) (marks : List Nat) (facts : Facts) :
    Obs (bulk reach marks (bulk reach marks facts)) (bulk reach marks facts) := by
  simpa only [bulk, Bool.or_self] using apply_composition reach (flags marks) (flags marks) facts

theorem duplicate_marks (reach : Reach) (mark : Nat) (facts : Facts) :
    Obs (serial reach [mark,mark] facts) (serial reach [mark] facts) := by
  simpa [serial] using bulk_idempotent reach [mark] facts

theorem missing_mark_no_effect (reach : Reach) (marks : List Nat) (facts : Facts)
    (absent : ∀ cell mark, (cell,mark) ∈ facts → mark ∉ marks) :
    Obs (bulk reach marks facts) facts := by
  rintro ⟨cell,mark⟩
  simp only [bulk,mem_apply]
  constructor
  · rintro ⟨src,hs,hr⟩
    have no := absent src mark hs
    have eq : cell = src := by simpa [flags,no,Rel,selectedCells] using hr
    simpa only [eq] using hs
  · intro hs
    exact ⟨cell,hs,by simp [flags,absent cell mark hs,Rel,selectedCells]⟩

theorem apply_singleton_selected (reach : Reach) (marks : List Nat) (cell : Cell) (mark : Nat)
    (requested : mark ∈ marks) :
    bulk reach marks [(cell,mark)] = (image reach cell).map (fun c => (c,mark)) := by
  simp [bulk,apply,atomOut,flags,requested,selectedCells]

theorem scalar_resume (reach : Reach) (marks : List Nat) (cell : Cell) (mark : Nat)
    (requested : mark ∈ marks) :
    Obs (serial reach marks [(cell,mark)])
      ((image reach cell).map (fun c => (c,mark))) := by
  simpa only [apply_singleton_selected reach marks cell mark requested] using
    serial_bulk reach marks [(cell,mark)]

/-- One complete ordinary context is opaque. Allocated handler IDs are not
    part of this semantic metadata. The same context is passed unchanged. -/
structure Tagged (α : Type) where
  context : α
  facts : Facts

def taggedBulk {α : Type} (r : Reach) (marks : List Nat) (value : Tagged α) : Tagged α :=
  ⟨value.context, bulk r marks value.facts⟩

theorem complete_context_unchanged {α : Type} (r : Reach) (marks : List Nat) (value : Tagged α) :
    (taggedBulk r marks value).context = value.context := rfl

theorem prefix_suffix {α : Type} (r : Reach) (marks : List Nat)
    (beforeBlock : α → Facts) (suffix : Facts → α)
    (respects : ∀ a b, Obs a b → suffix a = suffix b) (input : α) :
    suffix (serial r marks (beforeBlock input)) = suffix (bulk r marks (beforeBlock input)) :=
  respects _ _ (serial_bulk r marks (beforeBlock input))

/-- mx and requests are finite set observations; root images are separate.
    Nonstatic selected FLOW cleaning does not inspect spatial overlap. -/
structure Flow (α : Type) where
  context : α
  image : List Nat
  mx : List Nat
  deriving Repr

def flowSerial {α : Type} : List Nat → Flow α → Flow α
  | [], f => f
  | mark :: rest, f => flowSerial rest { f with mx := f.mx ++ [mark] }

def flowBulk {α : Type} (marks : List Nat) (f : Flow α) : Flow α :=
  { f with mx := f.mx ++ marks }

theorem flow_serial_bulk {α : Type} (marks : List Nat) (f : Flow α) :
    flowSerial marks f = flowBulk marks f := by
  induction marks generalizing f with
  | nil => simp [flowSerial,flowBulk]
  | cons mark rest ih => simp [flowSerial,ih,flowBulk,List.append_assoc]

/-- Count logical header operations for a persistent same-base FLOW input.
    This does not count allocated objects, mark membership work or CPU time.
    FLOW survives each eligible step; shrinking TAINT packets are out of scope. -/
structure CountedFlow (α : Type) where
  value : Flow α
  headerOperations : Nat

def countedFlowSerial {α : Type} : List Nat → Flow α → CountedFlow α
  | [], f => ⟨f,0⟩
  | mark :: rest, f =>
      let tail := countedFlowSerial rest { f with mx := f.mx ++ [mark] }
      ⟨tail.value,tail.headerOperations + 1⟩

def countedFlowBulk {α : Type} (marks : List Nat) (f : Flow α) : CountedFlow α :=
  match marks with
  | [] => ⟨f,0⟩
  | _ :: _ => ⟨flowBulk marks f,1⟩

theorem counted_flow_serial_value {α : Type} (marks : List Nat) (f : Flow α) :
    (countedFlowSerial marks f).value = flowSerial marks f := by
  induction marks generalizing f with
  | nil => rfl
  | cons mark rest ih => simp only [countedFlowSerial,flowSerial,ih]

theorem counted_flow_bulk_value {α : Type} (marks : List Nat) (f : Flow α) :
    (countedFlowBulk marks f).value = flowBulk marks f := by
  cases marks <;> simp [countedFlowBulk,flowBulk]

theorem counted_flow_value_equal {α : Type} (marks : List Nat) (f : Flow α) :
    (countedFlowSerial marks f).value = (countedFlowBulk marks f).value := by
  rw [counted_flow_serial_value,counted_flow_bulk_value,flow_serial_bulk]

theorem counted_flow_serial_work {α : Type} (marks : List Nat) (f : Flow α) :
    (countedFlowSerial marks f).headerOperations = marks.length := by
  induction marks generalizing f with
  | nil => rfl
  | cons mark rest ih => simp only [countedFlowSerial,ih,List.length_cons]

theorem flow_header_operations_never_increase {α : Type} (marks : List Nat) (f : Flow α) :
    (countedFlowBulk marks f).headerOperations ≤
      (countedFlowSerial marks f).headerOperations := by
  rw [counted_flow_serial_work]
  cases marks <;> simp [countedFlowBulk]

theorem flow_header_operations_strictly_reduce {α : Type} (marks : List Nat) (f : Flow α)
    (multiple : 1 < marks.length) :
    (countedFlowBulk marks f).headerOperations <
      (countedFlowSerial marks f).headerOperations := by
  rw [counted_flow_serial_work]
  cases marks with
  | nil => simp at multiple
  | cons mark rest => simpa only [countedFlowBulk] using multiple

def flowRequests (marks : List Nat) : List Nat := marks

theorem flow_request_membership (marks : List Nat) (mark : Nat) :
    mark ∈ flowRequests marks ↔ mark ∈ marks := Iff.rfl

theorem flow_image_unchanged {α : Type} (marks : List Nat) (f : Flow α) :
    (flowSerial marks f).image = f.image := by rw [flow_serial_bulk]; rfl

theorem flow_context_unchanged {α : Type} (marks : List Nat) (f : Flow α) :
    (flowSerial marks f).context = f.context := by rw [flow_serial_bulk]; rfl

theorem flow_duplicate_mx_idempotent {α : Type} (marks : List Nat) (f : Flow α) :
    Obs (flowBulk marks (flowBulk marks f)).mx (flowBulk marks f).mx := by
  intro mark
  simp [flowBulk,List.mem_append]

theorem flow_request_set_preserved (a b : List Nat) (same : Obs a b) :
    Obs (flowRequests a) (flowRequests b) := same

/-- Explicit base mismatch does not create a request or alter mx. The
    result still goes through the independent existing dispatcher below. -/
def flowAtBase {α : Type} (baseMatch : Bool) (marks : List Nat) (f : Flow α) : Flow α :=
  if baseMatch then flowBulk marks f else f
def requestsAtBase (baseMatch : Bool) (marks : List Nat) : List Nat :=
  if baseMatch then flowRequests marks else []

theorem mismatched_flow_no_request (marks : List Nat) : requestsAtBase false marks = [] := rfl
theorem mismatched_flow_header_kept {α : Type} (marks : List Nat) (f : Flow α) :
    flowAtBase false marks f = f := rfl

/-- Ghost attempt witness, not a proposed runtime archive. Raw suffix entry
    j = before.length and old request k = j + between.length remain distinct.
    Both are derived from immutable raw steps, not allocated PointRow IDs. -/
structure Attempt (α : Type) where
  before : List Nat
  between : List Nat
  mark : Nat
  after : List Nat
  context : α
  image : List Cell

def Attempt.body {α : Type} (a : Attempt α) : List Nat :=
  (a.before ++ a.between) ++ a.mark :: a.after
def Attempt.entry {α : Type} (a : Attempt α) : Nat := a.before.length
def Attempt.oldStep {α : Type} (a : Attempt α) : Nat := a.before.length + a.between.length
def Attempt.active {α : Type} (a : Attempt α) : List Nat := a.between ++ a.mark :: a.after
def Attempt.oldSuffix {α : Type} (a : Attempt α) : List Nat := a.mark :: a.after

theorem attempt_order {α : Type} (a : Attempt α) : a.entry ≤ a.oldStep := by
  exact Nat.le_add_right _ _

theorem attempt_active_requested {α : Type} (a : Attempt α) : a.mark ∈ a.active := by
  simp [Attempt.active]

theorem attempt_raw_requested {α : Type} (a : Attempt α) : a.mark ∈ a.oldSuffix := by
  simp [Attempt.oldSuffix]

theorem entry_is_raw_suffix {α : Type} (a : Attempt α) :
    a.body.drop a.entry = a.active := by
  simp [Attempt.body,Attempt.entry,Attempt.active,List.append_assoc]

theorem old_step_is_raw_suffix {α : Type} (a : Attempt α) :
    a.body.drop a.oldStep = a.oldSuffix := by
  change ((a.before ++ a.between) ++ a.mark :: a.after).drop
    (a.before.length + a.between.length) = a.mark :: a.after
  rw [← List.length_append]
  simp

theorem old_step_has_selected_mark {α : Type} (a : Attempt α) :
    (a.body.drop a.oldStep).head? = some a.mark := by
  rw [old_step_is_raw_suffix]
  rfl

theorem rebuilt_raw_suffix_stable (old rebuilt : List Nat) (entry : Nat)
    (same : old = rebuilt) : old.drop entry = rebuilt.drop entry := by rw [same]

def attemptExit {α : Type} (r : Reach) (atEntry : Bool) (a : Attempt α) : Facts :=
  serial r (if atEntry then a.active else a.oldSuffix) (a.image.map (fun c => (c,a.mark)))

theorem scalar_packet_resume (r : Reach) (marks : List Nat) (cells : List Cell) (mark : Nat)
    (requested : mark ∈ marks) :
    Obs (serial r marks (cells.map (fun c => (c,mark))))
      (bulk r [mark] (cells.map (fun c => (c,mark)))) := by
  have h := serial_bulk r marks (cells.map (fun c => (c,mark)))
  apply obs_trans h
  intro out
  rcases out with ⟨c,t⟩
  simp only [bulk,mem_apply]
  simp only [List.mem_map, Prod.mk.injEq] 
  constructor <;> rintro ⟨src, ⟨old, hold, heq, hmark⟩, hrel⟩
  · subst t
    exact ⟨src,⟨old,hold,heq,rfl⟩,by simpa [flags,requested] using hrel⟩
  · subst t
    exact ⟨src,⟨old,hold,heq,rfl⟩,by simpa [flags,requested] using hrel⟩

/-- Each old atomic request has an executable active-entry witness; each
    new attempt contains an explicit old raw occurrence (between,mark,after).
    Theorem deliberately does not identify whole pending PointRows. -/
theorem attempt_exit_equal {α : Type} (r : Reach) (a : Attempt α) :
    Obs (attemptExit r false a) (attemptExit r true a) := by
  have old := scalar_packet_resume r a.oldSuffix a.image a.mark (attempt_raw_requested a)
  have new := scalar_packet_resume r a.active a.image a.mark (attempt_active_requested a)
  intro out
  exact (old out).trans (new out).symm

structure Request (α : Type) where
  step : Nat
  mark : Nat
  context : α
  image : List Cell

def oldRequest {α : Type} (a : Attempt α) : Request α :=
  ⟨a.oldStep,a.mark,a.context,a.image⟩
def newRequest {α : Type} (a : Attempt α) : Request α :=
  ⟨a.entry,a.mark,a.context,a.image⟩

theorem request_image_exact {α : Type} (a : Attempt α) :
    (oldRequest a).image = (newRequest a).image ∧
    (oldRequest a).mark = (newRequest a).mark ∧
    (oldRequest a).context = (newRequest a).context := ⟨rfl,rfl,rfl⟩

/-- Decode one image leaf from a complete semantic point key. `context`
    includes the immutable origin/header/call state, not an allocation ID. -/
def requestTest {α : Type} [DecidableEq α] (atEntry : Bool)
    (context : α) (step mark : Nat) (leaf : Cell) (a : Attempt α) : Bool :=
  decide (a.context = context ∧ (if atEntry then a.entry else a.oldStep) = step ∧
    a.mark = mark ∧ leaf ∈ a.image)

def decodedLeaf {α : Type} [DecidableEq α] (atEntry : Bool)
    (context : α) (step mark : Nat) (leaf : Cell) (history : List (Attempt α)) : Bool :=
  history.any (requestTest atEntry context step mark leaf)

/-- Executable preimage finder for a leaf of a merged old or new row. -/
def selectAttempt {α : Type} [DecidableEq α] (atEntry : Bool)
    (context : α) (step mark : Nat) (leaf : Cell) (history : List (Attempt α)) : Option (Attempt α) :=
  history.find? (requestTest atEntry context step mark leaf)

theorem decoded_has_executable_preimage {α : Type} [DecidableEq α] (atEntry : Bool)
    (context : α) (step mark : Nat) (leaf : Cell) (history : List (Attempt α)) :
    decodedLeaf atEntry context step mark leaf history = true ↔
      (selectAttempt atEntry context step mark leaf history).isSome = true := by
  simp only [decodedLeaf,selectAttempt,List.any_eq_true,List.find?_isSome]

theorem selected_preimage_sound {α : Type} [DecidableEq α] (atEntry : Bool)
    (context : α) (step mark : Nat) (leaf : Cell) (history : List (Attempt α)) (a : Attempt α)
    (selected : selectAttempt atEntry context step mark leaf history = some a) :
    a ∈ history ∧ a.context = context ∧
      (if atEntry then a.entry else a.oldStep) = step ∧ a.mark = mark ∧ leaf ∈ a.image := by
  have hp := List.find?_some selected
  exact ⟨List.mem_of_find?_eq_some selected, by simpa [requestTest] using hp⟩

theorem atomic_attempt_in_both_decodes {α : Type} [DecidableEq α]
    (history : List (Attempt α)) (a : Attempt α) (leaf : Cell)
    (present : a ∈ history) (inImage : leaf ∈ a.image) :
    decodedLeaf false a.context a.oldStep a.mark leaf history = true ∧
    decodedLeaf true a.context a.entry a.mark leaf history = true := by
  constructor <;> apply List.any_eq_true.mpr <;>
    exact ⟨a,present,by simp [requestTest,inImage]⟩

/-- Constructively locate one raw mark occurrence. No choice or complete
    answer set is assumed to manufacture the old request witness. -/
def locate (mark : Nat) : (marks : List Nat) → mark ∈ marks →
    {parts : List Nat × List Nat // marks = parts.1 ++ mark :: parts.2}
  | [], impossible => False.elim (List.not_mem_nil impossible)
  | head :: rest, member =>
      if equal : head = mark then ⟨([],rest),by simp [equal]⟩
      else
        let child := locate mark rest ((List.mem_cons.mp member).resolve_left (Ne.symm equal))
        ⟨(head :: child.val.1,child.val.2),by simpa using congrArg (List.cons head) child.property⟩

def attemptFromNew {α : Type} (before : List Nat) (marks : List Nat) (mark : Nat)
    (member : mark ∈ marks) (context : α) (image : List Cell) : Attempt α :=
  let found := locate mark marks member
  ⟨before,found.val.1,mark,found.val.2,context,image⟩

theorem new_attempt_has_old_occurrence {α : Type} (before marks : List Nat) (mark : Nat)
    (member : mark ∈ marks) (context : α) (image : List Cell) :
    (attemptFromNew before marks mark member context image).body = before ++ marks ∧
    (attemptFromNew before marks mark member context image).active = marks := by
  have h := (locate mark marks member).property
  simp only [attemptFromNew,Attempt.body,Attempt.active]
  exact ⟨by simpa [List.append_assoc] using congrArg (fun xs => before ++ xs) h.symm,h.symm⟩

def historyExit {α : Type} (r : Reach) (atEntry : Bool) (history : List (Attempt α)) :
    List (α × Atom) :=
  history.flatMap (fun a => (attemptExit r atEntry a).map (fun atom => (a.context,atom)))

theorem history_exit_equal {α : Type} (r : Reach) (history : List (Attempt α)) :
    Obs (historyExit r false history) (historyExit r true history) := by
  intro out
  simp only [historyExit,List.mem_flatMap]
  constructor <;> rintro ⟨a,ha,hout⟩ <;>
    obtain ⟨atom,hatom,eq⟩ := List.mem_map.mp hout
  · exact ⟨a,ha,List.mem_map.mpr ⟨atom,(attempt_exit_equal r a atom).mp hatom,eq⟩⟩
  · exact ⟨a,ha,List.mem_map.mpr ⟨atom,(attempt_exit_equal r a atom).mpr hatom,eq⟩⟩

/-- Finite pending-attempt ghost model. Detach removes selected attempts
    before any callback; exact images/context are not multiplied. -/
def detach {α : Type} (answered : List Nat) (history : List (Attempt α)) :
    List (Attempt α) × List (Attempt α) :=
  (history.filter (fun a => decide (a.mark ∈ answered)),
   history.filter (fun a => decide (a.mark ∉ answered)))

theorem detached_has_answer {α : Type} (answered : List Nat) (history : List (Attempt α))
    (a : Attempt α) (h : a ∈ (detach answered history).1) : a.mark ∈ answered := by
  simpa [detach] using (List.mem_filter.mp h).2

theorem pending_has_no_answer {α : Type} (answered : List Nat) (history : List (Attempt α))
    (a : Attempt α) (h : a ∈ (detach answered history).2) : a.mark ∉ answered := by
  simpa [detach] using (List.mem_filter.mp h).2

theorem late_images_preserved {α : Type} (answered : List Nat)
    (early late : List (Attempt α)) :
    (detach answered (early ++ late)).1 =
      (detach answered early).1 ++ (detach answered late).1 := by
  simp [detach,List.filter_append]

theorem detach_no_second_answer {α : Type} (answered : List Nat) (history : List (Attempt α)) :
    (detach answered (detach answered history).2).1 = [] := by
  induction history with
  | nil => rfl
  | cons a rest ih =>
    cases (inferInstance : Decidable (a.mark ∈ answered)) with
    | isTrue h =>
      simpa only [detach,List.filter_cons,h,not_true_eq_false,not_false_eq_true,
        decide_true,decide_false,Bool.false_eq_true,if_true,if_false] using ih
    | isFalse h =>
      simpa only [detach,List.filter_cons,h,not_true_eq_false,not_false_eq_true,
        decide_true,decide_false,Bool.false_eq_true,if_true,if_false] using ih

/-- A callback may add a fresh request after detachment. The old answered
    attempts do not run again, but the fresh matching images remain visible. -/
theorem new_requests_after_detach {α : Type} (answered : List Nat)
    (history fresh : List (Attempt α)) :
    (detach answered ((detach answered history).2 ++ fresh)).1 =
      (detach answered fresh).1 := by
  rw [late_images_preserved,detach_no_second_answer,List.nil_append]

theorem taint_union_distribution (r : Reach) (selected : Nat → Bool) (a b : Facts) :
    apply r selected (a ++ b) = apply r selected a ++ apply r selected b := by
  simp [apply,List.flatMap_append]

/-- Eligibility is independent of the cleanup proof. Unsupported headers
    select the existing scalar implementation, not an identity bypass. -/
structure Eligibility where
  run1 : Bool
  full : Bool
  plainNonstaticSameGeometry : Bool
  emptyFieldExclusion : Bool
  collectorFixed : Bool
  taintLayer : Option Layer
  deriving DecidableEq, Repr

def eligible (e : Eligibility) : Bool :=
  e.run1 && e.full && e.plainNonstaticSameGeometry && e.emptyFieldExclusion &&
    e.collectorFixed && (match e.taintLayer with | none => true | some l => l == .demand)

def selectImplementation {α : Type} (e : Eligibility) (scalar candidate : α) : α :=
  if eligible e then candidate else scalar

theorem normal_taint_falls_back {α : Type} (e : Eligibility) (scalar candidate : α) :
    selectImplementation { e with taintLayer := some .normal } scalar candidate = scalar := by
  simp [selectImplementation,eligible]

theorem nonempty_exclusion_falls_back {α : Type} (e : Eligibility) (scalar candidate : α) :
    selectImplementation { e with emptyFieldExclusion := false } scalar candidate = scalar := by
  simp [selectImplementation,eligible]

theorem changing_cover_falls_back {α : Type} (e : Eligibility) (scalar candidate : α) :
    selectImplementation { e with collectorFixed := false } scalar candidate = scalar := by
  simp [selectImplementation,eligible]

/-- A dispatcher can terminate a base-mismatched STATIC value by promotion.
    This is not unconditional next-list passthrough. Nonstatic collector-
    fixed data uses the continued branch. Empty suffix has no dispatch. -/
inductive Dispatch (α : Type) where
  | continued (value : α)
  | promoted (value : α)
  deriving DecidableEq, Repr

def mismatchSerial {α : Type} (staticResult : Bool) : List Nat → α → Dispatch α
  | [], value => .continued value
  | _ :: rest, value => if staticResult then .promoted value
      else mismatchSerial staticResult rest value

def mismatchBulk {α : Type} (staticResult : Bool) (marks : List Nat) (value : α) : Dispatch α :=
  if marks.isEmpty then .continued value
  else if staticResult then .promoted value else .continued value

theorem mismatch_dispatch_equal {α : Type} (staticResult : Bool) (marks : List Nat) (value : α) :
    mismatchSerial staticResult marks value = mismatchBulk staticResult marks value := by
  cases staticResult
  · induction marks with
    | nil => rfl
    | cons m rest ih => simpa [mismatchSerial,mismatchBulk] using ih
  · cases marks <;> rfl

theorem reach_continues_unchanged {α : Type} (marks : List Nat) (value : α) :
    mismatchSerial false marks value = .continued value := by
  rw [mismatch_dispatch_equal]
  simp [mismatchBulk]

theorem static_mismatch_promotes_once {α : Type} (mark : Nat) (rest : List Nat) (value : α) :
    mismatchSerial true (mark :: rest) value = .promoted value ∧
    mismatchBulk true (mark :: rest) value = .promoted value := ⟨rfl,rfl⟩

namespace Controls
def t : Nat := 7
def u : Nat := 8
def atom : Facts := [(.atAny,t)]
def sample : Attempt Nat := ⟨[99],[u],t,[t,u],123,[.atAny,.nearMust]⟩
def eligibleInput : Eligibility := ⟨true,true,true,true,true,some .demand⟩
def controls : List Bool := [
  decide (position .exact .atAny = .part),
  decide (image .exact .atAny = [.atAny]),
  decide (image .exact .atMust = [.atMust]),
  decide (image .below .atAny = [.atExact]),
  decide (image .below .atExact = [.atExact]),
  decide (image .below .nearMust = [.nearMust,.atExact]),
  decide (image .atAndBelow .nearMust = [.nearMust]),
  decide (image .exact .atExact = []),
  decide (image .atAndBelow .atAny = []),
  decide (bulk .exact [t,u] atom = atom),
  decide (bulk .below [t] atom = [(.atExact,t)]),
  decide (bulk .atAndBelow [u] atom = atom),
  decide ((flowBulk [t,u,t] (Flow.mk 3 [10,11] [9])).mx = [9,t,u,t]),
  decide ((flowBulk [t,u] (Flow.mk 3 [10,11] [9])).image = [10,11]),
  decide (sample.entry = 1),
  decide (sample.oldStep = 2),
  decide (sample.active = [u,t,t,u]),
  decide (mismatchBulk true [t,u] 41 = Dispatch.promoted 41),
  decide (mismatchBulk true [] 41 = Dispatch.continued 41),
  decide (mismatchBulk false [t,u] 41 = Dispatch.continued 41),
  decide (image .below .atExactAny = [.atExactAny]),
  decide (demandImage .exact .atAny = [(.atAny,.demand,[])]),
  decide ((serial .below [t,u,t] [(.atAny,t),(.atAny,u)]).eraseDups =
    [(.atExact,t),(.atExact,u)]),
  decide ((attemptFromNew [99] [u,t,t] t (by decide) 41 [.atAny]).oldStep = 2),
  decide (sample.body.drop sample.entry = sample.active),
  decide (sample.body.drop sample.oldStep = sample.oldSuffix),
  decide (decodedLeaf false 123 2 t .atAny [sample] = true),
  decide (decodedLeaf true 123 1 t .atAny [sample] = true),
  decide ((selectAttempt false 123 2 t .atAny [sample]).isSome = true),
  decide (requestsAtBase false [t,u] = []),
  decide (eligible eligibleInput = true),
  decide (eligible { eligibleInput with taintLayer := some .normal } = false),
  decide (eligible { eligibleInput with emptyFieldExclusion := false } = false),
  decide (eligible { eligibleInput with collectorFixed := false } = false),
  decide (locatedImage [1,2] .below ⟨[1],.nearMust⟩ =
    [⟨[1],.nearMust⟩,⟨[1,2],.atExact⟩]),
  decide (Packed.bulk (locatedImage [9] .exact) [t]
    [(⟨[1,2],.farAny⟩,t),(⟨[1,3],.farAny⟩,t)] =
    [(⟨[1,2],.farAny⟩,t),(⟨[1,3],.farAny⟩,t)]),
  decide (eraseLocated ⟨[1,2],.atExactAny⟩ = (⟨[1,2],.atExact⟩ : Located)),
  decide (eraseExactCarrier .atAny ≠ eraseExactCarrier .atMust),
  decide ((countedFlowSerial [] (Flow.mk 3 [10,11] [9])).headerOperations = 0),
  decide ((countedFlowBulk [] (Flow.mk 3 [10,11] [9])).headerOperations = 0),
  decide ((countedFlowSerial [t,u,t] (Flow.mk 3 [10,11] [9])).headerOperations = 3),
  decide ((countedFlowBulk [t,u,t] (Flow.mk 3 [10,11] [9])).headerOperations = 1),
  decide ((countedFlowBulk [t,u,t] (Flow.mk 3 [10,11] [9])).value.mx = [9,t,u,t])
]
theorem controls_pass : controls.all id = true := by decide
end Controls

end ApSpec.Run1FullReturnCleanup
