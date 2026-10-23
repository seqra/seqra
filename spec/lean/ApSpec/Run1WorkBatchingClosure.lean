/-
Finite constructive transport for the selected run-1 batching changes.
Atoms are complete normalized boundary observations, not mutable pending
rows or callback counts. The two local models must supply the output
membership equivalence. This module does not assume whole-engine closure
equality and does not prove Kotlin adapters.
-/
import Std

namespace ApSpec.Run1WorkBatchingClosure
set_option autoImplicit false
universe u v w

/-- A finite proof has one executable child for every required input.
    Empty inputs remain empty; no representative origin replaces a join. -/
inductive Derivation {Atom : Type u} {Op : Type v}
    (seeds : List Atom) (inputs outputs : Op → List Atom) : Atom → Type (max u v) where
  | seed (atom : Atom) (present : atom ∈ seeds) : Derivation seeds inputs outputs atom
  | fire (op : Op) (atom : Atom) (present : atom ∈ outputs op)
      (children : (input : Atom) → input ∈ inputs op → Derivation seeds inputs outputs input) :
      Derivation seeds inputs outputs atom

/-- Local output equivalence transports a finite derivation. Its inputs,
    including every origin and every join member, are unchanged. -/
def transport {Atom : Type u} {Op : Type v}
    {seeds : List Atom} {inputs oldOutput newOutput : Op → List Atom}
    (same : (op : Op) → (atom : Atom) → atom ∈ oldOutput op ↔ atom ∈ newOutput op)
    {atom : Atom} (proof : Derivation seeds inputs oldOutput atom) :
    Derivation seeds inputs newOutput atom :=
  match proof with
  | .seed atom present => .seed atom present
  | .fire op atom present children =>
      .fire op atom ((same op atom).mp present)
        (fun input required => transport same (children input required))

def transportBack {Atom : Type u} {Op : Type v}
    {seeds : List Atom} {inputs oldOutput newOutput : Op → List Atom}
    (same : (op : Op) → (atom : Atom) → atom ∈ oldOutput op ↔ atom ∈ newOutput op)
    {atom : Atom} (proof : Derivation seeds inputs newOutput atom) :
    Derivation seeds inputs oldOutput atom :=
  transport (fun op atom => (same op atom).symm) proof

theorem finite_closures_equivalent {Atom : Type u} {Op : Type v}
    {seeds : List Atom} {inputs oldOutput newOutput : Op → List Atom}
    (same : (op : Op) → (atom : Atom) → atom ∈ oldOutput op ↔ atom ∈ newOutput op)
    (atom : Atom) :
    Nonempty (Derivation seeds inputs oldOutput atom) ↔
      Nonempty (Derivation seeds inputs newOutput atom) := by
  constructor
  · rintro ⟨proof⟩
    exact ⟨transport same proof⟩
  · rintro ⟨proof⟩
    exact ⟨transportBack same proof⟩

/-- A handoff is a fixed per-row normalizer plus finite union. -/
def handoff {Atom : Type u} {Guide : Type w}
    (rows : List Atom) (emit : Atom → List Guide) : List Guide := rows.flatMap emit

theorem handoff_membership {Atom : Type u} {Guide : Type w}
    (oldRows newRows : List Atom) (emit : Atom → List Guide)
    (same : (atom : Atom) → atom ∈ oldRows ↔ atom ∈ newRows) (guide : Guide) :
    guide ∈ handoff oldRows emit ↔ guide ∈ handoff newRows emit := by
  simp only [handoff, List.mem_flatMap]
  constructor
  · rintro ⟨atom, present, emitted⟩
    exact ⟨atom, (same atom).mp present, emitted⟩
  · rintro ⟨atom, present, emitted⟩
    exact ⟨atom, (same atom).mpr present, emitted⟩

/-- The observer keeps its full occurrence key as part of the output.
    Packet/callback multiplicities are intentionally not represented. -/
structure Observation {Atom : Type u} {Op : Type v} {Seen : Type w}
    (seeds : List Atom) (inputs outputs : Op → List Atom)
    (render : Atom → List Seen) (seen : Seen) where
  atom : Atom
  proof : Derivation seeds inputs outputs atom
  occurrence : seen ∈ render atom

def transportObservation {Atom : Type u} {Op : Type v} {Seen : Type w}
    {seeds : List Atom} {inputs oldOutput newOutput : Op → List Atom}
    {render : Atom → List Seen} {seen : Seen}
    (same : (op : Op) → (atom : Atom) → atom ∈ oldOutput op ↔ atom ∈ newOutput op)
    (old : Observation seeds inputs oldOutput render seen) :
    Observation seeds inputs newOutput render seen :=
  ⟨old.atom, transport same old.proof, old.occurrence⟩

namespace Controls

def inputs : Nat → List Nat
  | 0 => []
  | _ => [1, 2]

def oldOutput : Nat → List Nat
  | 0 => [3, 3]
  | _ => [4, 4]

def newOutput : Nat → List Nat
  | 0 => [3]
  | _ => [4]

theorem same (op atom : Nat) : atom ∈ oldOutput op ↔ atom ∈ newOutput op := by
  cases op <;> simp [oldOutput, newOutput]

def joined : Derivation [1, 2] inputs oldOutput 4 :=
  .fire 1 4 (by decide) (fun input required => .seed input (by simpa [inputs] using required))

def zeroPremise : Derivation [1, 2] inputs oldOutput 3 :=
  .fire 0 3 (by decide) (fun _ required => by simp [inputs] at required)

def root {seeds : List Nat} {ins outs : Nat → List Nat} {atom : Nat}
    (proof : Derivation seeds ins outs atom) : Nat :=
  match proof with
  | .seed value _ => value
  | .fire _ value _ _ => value

def controls : List Bool := [
  decide (inputs 0 = []),
  decide (inputs 1 = [1, 2]),
  decide (3 ∈ newOutput 0),
  decide (4 ∈ newOutput 1),
  decide (9 ∉ newOutput 1),
  decide (handoff [1, 1, 2] (fun n => [n + 1]) = [2, 2, 3]),
  root (transport same joined) == 4,
  root (transport same zeroPremise) == 3,
  root (transportBack same (transport same joined)) == 4
]

end Controls
end ApSpec.Run1WorkBatchingClosure
