/-
  Local application audit of the current implementation proposal.
  No whole store, JVM call-plan or iteration theorem is claimed.
-/
import ApSpec.CurrentProposal

namespace ApSpec.CurrentSummaryApplication
open ApSpec ApSpec.AnyTaintEx

/-- A matched concrete exact/any input and an uncorrelated concrete exact/any
    summary target require only a layer OR, for EVERY premise tail, including
    the concrete STAR/E initial premises produced by run-1 mark answers.
    Both field annotations and successful overlap remain explicit. -/
theorem uncorrelated_member_application (a : XFact) (j : PFact) (jex : Excl) (g : XFact)
    (inputTail : a.af.fact.kind = .exact ∨ a.af.fact.kind = .any)
    (targetTail : g.af.fact.kind = .exact ∨ g.af.fact.kind = .any)
    (inputMark : ∃ t, a.af.fact.mark = .conc t ∧ j.mark = .conc t)
    (targetMark : ∃ u, g.af.fact.mark = .conc u)
    (overlap : overlapX j jex a.af.fact a.ex = true) :
    applySummaryX a j jex g =
      ⟨[normX ⟨⟨g.af.fact, a.af.demand || g.af.demand⟩, g.ex⟩], []⟩ := by
  obtain ⟨⟨⟨ab, ap, ak, am⟩, ad⟩, aex⟩ := a
  obtain ⟨jb, jp, jk, jm⟩ := j
  obtain ⟨⟨⟨gb, gp, gk, gm⟩, gd⟩, gex⟩ := g
  dsimp only at inputTail targetTail inputMark targetMark overlap ⊢
  obtain ⟨t, rfl, rfl⟩ := inputMark
  obtain ⟨u, rfl⟩ := targetMark
  have spatial := (Bool.and_eq_true _ _).mp overlap |>.1
  have annotation := (Bool.and_eq_true _ _).mp overlap |>.2
  have base := (Bool.and_eq_true _ _).mp spatial |>.1
  have shape := (Bool.and_eq_true _ _).mp spatial |>.2
  have reverseBase : Nat.beq ab jb = true := by
    exact CoreAux.beq_iff.mpr (CoreAux.beq_iff.mp base).symm
  cases position : relate jp ap with
  | apart => simp [position] at shape
  | below rest =>
      have admitted : admitsTailB jk rest = true := by simpa [position] using shape
      have annotated : jex.admits rest = true := by simpa [position] using annotation
      rcases inputTail with rfl | rfl <;>
        cases jk <;>
        rcases targetTail with rfl | rfl <;>
        cases rest <;> cases ad <;> cases gd <;>
        simp [applySummaryX, applyEdgeX, annX, applyEdge, reverseBase, position,
          belowCase, admitted, annotated, lostCorr, markGate, markComp, AFact.norm,
          layerX, normX, carriesB, concB, Kind.isAny]
  | above rest =>
      have admitted : admitsTailB ak rest = true := by simpa [position] using shape
      have annotated : aex.admits rest = true := by simpa [position] using annotation
      have nonempty : rest ≠ [] := by
        intro nil
        have eq := Exact.relate_above position
        have absent := Exact.relate_above_none position
        rw [nil, List.append_nil] at eq
        have present : dropPrefix jp ap = some [] :=
          CoreAux.dropPrefix_some.mpr (by simp [eq])
        rw [absent] at present
        cases present
      rcases inputTail with rfl | rfl
      · cases rest with
        | nil => exact False.elim (nonempty rfl)
        | cons _ _ => cases admitted
      · cases jk <;>
          rcases targetTail with rfl | rfl <;>
          cases ad <;> cases gd <;>
          simp [applySummaryX, applyEdgeX, annX, applyEdge, reverseBase, position,
            aboveCase, admitted, annotated, markGate, markComp, AFact.norm,
            layerX, normX, carriesB, concB, Kind.isAny]


namespace Selectors

/-- Current surface tails. Must and may differ in location exclusion and layer. -/
inductive Tail where
  | star | exact | may | must
  deriving DecidableEq, Repr

def Tail.isAny : Tail → Bool
  | .may | .must => true
  | _ => false

def admits (tail : Tail) (e : Excl) (rest : List Acc) : Bool :=
  match tail with
  | .exact => rest.isEmpty
  | .may => true
  | .star | .must => e.admits rest

/-- ap.md §3.4 tail inclusion, independent of the tree selector. -/
def includes (outer inner : Tail) (oe ie : Excl) : Bool :=
  match outer with
  | .may => true
  | .exact => (match inner with | .exact => true | _ => false)
  | .star | .must => match inner with
    | .exact => true
    | .may => oe.isEmptyB
    | .star | .must => oe.subB ie

/-- The equal-path leaf row of applicablePart, before the concrete mark filter. -/
def applicableAt (j a : Tail) (je ae : Excl) : Bool :=
  match j with
  | .star => match a with
    | .exact => true
    | .star | .must => je.subB ae
    | .may => je.isEmptyB
  | .exact => (match a with | .exact => true | _ => false)
  | .may => a.isAny
  | .must => a.isAny && (match a with | .must => je.subB ae | _ => je.isEmptyB)

/-- The strict-descendant row of applicablePart. -/
def applicableBelow (j a : Tail) (je : Excl) (first : Acc) (rest : List Acc) : Bool :=
  match j with
  | .star => je.admits (first :: rest)
  | .exact => false
  | .may => a.isAny
  | .must => je.admits (first :: rest) && a.isAny

theorem applicable_at_exact (j a : Tail) (je ae : Excl) :
    applicableAt j a je ae = (includes j a je ae && (!j.isAny || a.isAny)) := by
  cases j <;> cases a <;> simp [applicableAt, includes, Tail.isAny]

theorem applicable_below_exact (j a : Tail) (je : Excl) (first : Acc) (rest : List Acc) :
    applicableBelow j a je first rest =
      (admits j je (first :: rest) && (!j.isAny || a.isAny)) := by
  cases j <;> cases a <;> simp [applicableBelow, admits, Tail.isAny]

/-- The equal-path insidePart row. A STAR input is a FLOW leaf, so a must
    premise with a concrete mark cannot match it even if these spatial tests differ. -/
def insideAt (j a : Tail) (je ae : Excl) : Bool :=
  match a with
  | .star => match j with
    | .exact => true
    | .star => ae.subB je
    | .may | .must => ae.isEmptyB
  | .exact => (match j with | .exact => true | _ => false)
  | .may | .must => (match j with | .exact => true | _ => false) || ae.subB je

@[simp] theorem empty_isEmpty : Excl.empty.isEmptyB = true := by rfl

@[simp] theorem empty_sub (e : Excl) : Excl.empty.subB e = true := by
  cases e <;> rfl

@[simp] theorem sub_empty (e : Excl) : e.subB Excl.empty = e.isEmptyB := by
  cases e with
  | univ => rfl
  | set xs => cases xs <;> rfl

/-- Every allowed non-must/STAR equal-path pair has the same spatial row.
    The may entry guard supplies empty E; exact E is irrelevant. -/
theorem inside_at_spatial (j a : Tail) (je ae : Excl)
    (jmay : j = .may → je = Excl.empty)
    (amay : a = .may → ae = Excl.empty)
    (guard : ¬(j = .must ∧ a = .star)) :
    insideAt j a je ae = includes a j ae je := by
  cases j <;> cases a <;>
    simp [insideAt, includes, jmay, amay] at *

/-- The exceptional spatial pair still agrees after the concrete mark gate:
    a must premise is concrete; a STAR input has an abstract mark. -/
theorem inside_must_flow_rejected (je ae : Excl) (t : Mark) :
    (insideAt .must .star je ae && markSubB (.conc t) .star) =
      (includes .star .must ae je && markSubB (.conc t) .star) := by
  simp [markSubB]

theorem inside_must_excluded_flow_rejected (je ae : Excl) (t : Mark) (xs : List Mark) :
    (insideAt .must .star je ae && markSubB (.conc t) (.starEx xs)) =
      (includes .star .must ae je && markSubB (.conc t) (.starEx xs)) := by
  simp [markSubB]

/-- Both insidePart and location inclusion select only a STAR/any ancestor,
    and read its E on the first accessor. Exact ancestors cannot admit a step. -/
def insideAbove (a : Tail) (ae : Excl) (first : Acc) (rest : List Acc) : Bool :=
  match a with
  | .exact => false
  | .may => true
  | .star | .must => ae.admits (first :: rest)

theorem inside_above_exact (a : Tail) (ae : Excl) (first : Acc) (rest : List Acc) :
    insideAbove a ae first rest = admits a ae (first :: rest) := by
  cases a <;> rfl

namespace Certificate

def f : Acc := 2
def g : Acc := 4
def ef : Excl := .set [f]
def eg : Excl := .set [g]
def efg : Excl := .set [f,g]

def check : Bool :=
  -- Canonical FLOW overlaps equal-path must, despite a nonempty added E.
  applicableAt .star .must Excl.empty ef &&
  -- Incomparable FLOW exclusions overlap, but neither satisfaction branch holds.
  !(applicableAt .star .star ef eg) && !(insideAt .star .star ef eg) &&
  -- Concrete STAR/E native reader admits an exact root and rejects an excluded suffix.
  applicableAt .star .exact ef Excl.empty &&
  !(applicableBelow .star .exact ef f []) &&
  applicableBelow .star .exact ef g [] &&
  -- A restricted must summary uses inside only; a must record adds applicable.
  applicableAt .must .must ef efg && !(insideAt .must .must ef efg) &&
  -- A same-path may requirement lies outside the exclusion but covers STAR/E.
  !(applicableAt .star .may ef Excl.empty) && insideAt .star .may ef Excl.empty

theorem checked : check = true := by decide

end Certificate
end Selectors


namespace Certificate

def j : PFact := ⟨3, [], .star (.set [2]), .conc 7⟩
def input : XFact := ⟨⟨⟨3, [4], .exact, .conc 7⟩, false⟩, Excl.empty⟩
def target : XFact := ⟨⟨⟨5, [2], .any, .conc 11⟩, false⟩, .set [4]⟩
def demandInput : XFact := ⟨⟨⟨3, [], .any, .conc 7⟩, true⟩, Excl.empty⟩
def demandTarget : XFact := normX ⟨⟨target.af.fact, true⟩, target.ex⟩
def excludedInput : XFact := ⟨⟨⟨3, [2], .exact, .conc 7⟩, false⟩, Excl.empty⟩

def run1Policy : PFact := ⟨3, [], .star Excl.empty, .star⟩
def normalMustInput : XFact := ⟨⟨⟨3, [], .any, .conc 7⟩, false⟩, .set [2,4]⟩
def run1Answer : PFact := ApSpec.answerInit run1Policy normalMustInput.af.fact 7
def nativeDemandTarget : XFact := normX ⟨⟨target.af.fact, true⟩, target.ex⟩

/-- Native run-1 chain answer. Its concrete STAR premise starts demand;
    the W7 target is demand, has a different mark/path, and its E is normalized away. -/
def nativeAnswerCheck : Bool :=
  run1Answer.kind == .star Excl.empty &&
  (startFact run1Answer).demand &&
  applicable run1Answer normalMustInput.af.fact &&
  applySummaryX normalMustInput run1Answer Excl.empty nativeDemandTarget ==
    ⟨[nativeDemandTarget], []⟩ &&
  nativeDemandTarget.ex == Excl.empty

/-- Concrete STAR/E members preserve a normal target's mark/path/E and clear E
    on a demand contribution. An excluded suffix does not apply. -/
def check : Bool :=
  applySummaryX input j Excl.empty target == ⟨[target], []⟩ &&
  applySummaryX demandInput j Excl.empty target == ⟨[demandTarget], []⟩ &&
  applySummaryX excludedInput j Excl.empty target == ResX.none &&
  Selectors.Certificate.check && nativeAnswerCheck

theorem checked : check = true := by decide
theorem native_answer_checked : nativeAnswerCheck = true := by decide

end Certificate

#eval Certificate.check
#print axioms Certificate.native_answer_checked
#print axioms Certificate.checked

#print axioms Selectors.applicable_at_exact
#print axioms Selectors.applicable_below_exact
#print axioms Selectors.inside_at_spatial
#print axioms Selectors.inside_must_flow_rejected
#print axioms Selectors.inside_must_excluded_flow_rejected
#print axioms Selectors.inside_above_exact
#print axioms Selectors.Certificate.checked
#print axioms uncorrelated_member_application
end ApSpec.CurrentSummaryApplication
