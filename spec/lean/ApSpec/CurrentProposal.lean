/-
  Local evidence for the implementation proposal rewrite.

  A successful concrete literal check on a legal TAINT leaf needs no second
  traversal to determine its contribution layer. The original literal
  precision rule is equal to the leaf's layer. The proof retains annotated
  overlap, concrete mark gating, and both supported literal tails. It does
  not cover FLOW checks or an arbitrary general-purpose literal carrier.

  The combination proof uses actual annotated summary application. Admitted
  concrete TAINT members with uncorrelated targets need only a layer header OR.

  The record timing example compares actual annotated operations on one legal
  must record. A general post-result field read does not commute with record
  demotion. This is a local operation example, not a whole JIR call-plan proof.
-/
import ApSpec.CurrentConjunction
import ApSpec.CurrentSimplifications

namespace ApSpec.CurrentProposal
open ApSpec ApSpec.AnyTaintEx ApSpec.CurrentConjunction

namespace LiteralLayer

/-- On an exact input, successful spatial matching implies coverage. -/
theorem exact_match_covered (literal added : PFact) (lex aex : Excl)
    (tail : literal.kind = .exact ∨ literal.kind = .any)
    (exact : added.kind = .exact)
    (marks : markSubB literal.mark added.mark = true)
    (overlap : overlapX literal lex added aex = true) :
    coversB literal added = true := by
  have spatial := (Bool.and_eq_true _ _).mp overlap |>.1
  unfold overlapB at spatial
  have base := (Bool.and_eq_true _ _).mp spatial |>.1
  have shape := (Bool.and_eq_true _ _).mp spatial |>.2
  unfold coversB
  rw [base, marks]
  simp only [Bool.true_and]
  cases hp : dropPrefix literal.path added.path with
  | some rest =>
      cases rest with
      | nil =>
          rcases tail with hl | hl <;> simp [hl, exact, tailSubB]
      | cons field rest =>
          simpa [relate, hp] using shape
  | none =>
      cases other : dropPrefix added.path literal.path with
      | none => simp [relate, hp, other] at shape
      | some rest =>
          have admitted : admitsTailB added.kind rest = true := by
            simpa [relate, hp, other] using shape
          rw [exact] at admitted
          cases rest with
          | cons field rest => cases admitted
          | nil =>
              have same : literal.path = added.path := by
                simpa using CoreAux.dropPrefix_some.mp other
              have present : dropPrefix literal.path added.path = some [] :=
                CoreAux.dropPrefix_some.mpr (by simp [same])
              rw [hp] at present
              cases present

/-- The concrete gate used by checkMark supplies the coverage mark clause. -/
theorem gate_mark_sub (t u : Mark)
    (gate : markGate (.conc t) (.conc u) = .ok) :
    markSubB (.conc t) (.conc u) = true := by
  cases hb : Nat.beq t u <;> simp [markGate, markSubB, hb] at gate ⊢

/-- The layer-only implementation equals the full literal precision rule
    on every matched exact/must/may TAINT leaf, with arbitrary annotations. -/
theorem matched_taint_layer (literal : PFact) (fact : XFact) (lex : Excl)
    (literalTail : literal.kind = .exact ∨ literal.kind = .any)
    (factTail : fact.af.fact.kind = .exact ∨ fact.af.fact.kind = .any)
    (concrete : ∃ t u, literal.mark = .conc t ∧ fact.af.fact.mark = .conc u)
    (gate : markGate literal.mark fact.af.fact.mark = .ok)
    (overlap : overlapX literal lex fact.af.fact fact.ex = true) :
    partDemand literal fact.af = fact.af.demand := by
  cases demand : fact.af.demand with
  | true => simp [partDemand, demand]
  | false =>
      rcases factTail with exact | any
      · obtain ⟨t, u, ht, hu⟩ := concrete
        have markSub : markSubB literal.mark fact.af.fact.mark = true := by
          rw [ht, hu]
          exact gate_mark_sub _ _ (by simpa [ht, hu] using gate)
        have covered := exact_match_covered literal fact.af.fact lex fact.ex
          literalTail exact markSub overlap
        simp [partDemand, demand, covered]
      · simp [partDemand, demand, any, Kind.isAny]

namespace Certificate

def literal : PFact := ⟨3, [2], .exact, .conc 7⟩
def normalMust : XFact := ⟨⟨⟨3, [], .any, .conc 7⟩, false⟩, .set [4]⟩
def excludedMust : XFact := {normalMust with ex := .set [2]}
def normalExact : XFact := ⟨⟨literal, false⟩, Excl.empty⟩
def demandMay : XFact := ⟨⟨normalMust.af.fact, true⟩, Excl.empty⟩
def matching (x : XFact) : Bool := overlapX literal Excl.empty x.af.fact x.ex &&
  (match markGate literal.mark x.af.fact.mark with | .ok => true | _ => false)
def cases : List (Bool × Bool × Bool) :=
  [normalMust, excludedMust, normalExact, demandMay].map fun f =>
    (matching f, partDemand literal f.af, f.af.demand)
theorem case_values : cases = [(true,false,false), (false,false,false),
    (true,false,false), (true,true,true)] := by decide
def check : Bool := cases == [(true,false,false), (false,false,false),
    (true,false,false), (true,true,true)]
theorem checked : check = true := by decide

end Certificate
end LiteralLayer

namespace Combination

/-- One admitted concrete TAINT member produces the uncorrelated summary
    target, with the OR of the input and summary layers. There are no requests.
    Annotation overlap includes both premise and added-fact exclusions. -/
theorem member_application (a : XFact) (j : PFact) (jex : Excl) (g : XFact)
    (inputTail : a.af.fact.kind = .exact ∨ a.af.fact.kind = .any)
    (premiseTail : j.kind = .exact ∨ j.kind = .any)
    (targetTail : g.af.fact.kind = .exact ∨ g.af.fact.kind = .any)
    (inputMark : ∃ t, a.af.fact.mark = .conc t ∧ j.mark = .conc t)
    (targetMark : ∃ u, g.af.fact.mark = .conc u)
    (overlap : overlapX j jex a.af.fact a.ex = true) :
    applySummaryX a j jex g =
      ⟨[normX ⟨⟨g.af.fact, a.af.demand || g.af.demand⟩, g.ex⟩], []⟩ := by
  obtain ⟨⟨⟨ab, ap, ak, am⟩, ad⟩, aex⟩ := a
  obtain ⟨jb, jp, jk, jm⟩ := j
  obtain ⟨⟨⟨gb, gp, gk, gm⟩, gd⟩, gex⟩ := g
  dsimp only at inputTail premiseTail targetTail inputMark targetMark overlap ⊢
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
        rcases premiseTail with rfl | rfl <;>
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
      · rcases premiseTail with rfl | rfl <;>
          rcases targetTail with rfl | rfl <;>
          cases ad <;> cases gd <;>
          simp [applySummaryX, applyEdgeX, annX, applyEdge, reverseBase, position,
            aboveCase, admitted, annotated, markGate, markComp, AFact.norm,
            layerX, normX, carriesB, concB, Kind.isAny]

/-- Old normal/demand accumulator after all member applications. The proven
    member result has demand equal to inputDemand OR summaryDemand. -/
def oldLayers (summary : Bool) (parts : List Bool) : Bool × Bool :=
  parts.foldl (fun state dm =>
    (state.1 && !(dm || summary), state.2 || (dm || summary))) (!summary, summary)

def joinedLayer (summary : Bool) (parts : List Bool) : Bool :=
  summary || parts.any id

/-- The old two output flags are complementary. One header OR gives the same
    layer; an admitted combination cannot produce both layers. -/
theorem joined_layers (summary : Bool) (parts : List Bool) :
    oldLayers summary parts = (!(joinedLayer summary parts), joinedLayer summary parts) := by
  have fold : ∀ (bits : List Bool) (acc : Bool), (summary || acc) = acc →
      bits.foldl (fun state dm =>
        (state.1 && !(dm || summary), state.2 || (dm || summary))) (!acc, acc) =
      (!(acc || bits.any id), acc || bits.any id) := by
    intro bits
    induction bits with
    | nil => intro acc _; simp
    | cons dm bits ih =>
        intro acc holds
        have step : (!acc && !(dm || summary), acc || (dm || summary)) =
            (!(acc || dm), acc || dm) := by
          cases summary <;> cases acc <;> cases dm <;> simp at holds ⊢
        have next : (summary || (acc || dm)) = (acc || dm) := by
          rw [← Bool.or_assoc, holds]
        rw [List.foldl_cons, step, ih (acc || dm) next]
        simp [List.any_cons, Bool.or_assoc]
  exact fold parts summary (Bool.or_self _)

namespace Certificate
def premise : PFact := ⟨3, [2], .exact, .conc 7⟩
def target : XFact := ⟨⟨⟨5, [8], .any, .conc 9⟩, false⟩, .set [4]⟩
def exactInput : XFact := ⟨⟨premise, false⟩, Excl.empty⟩
def mustInput : XFact := ⟨⟨⟨3, [], .any, .conc 7⟩, false⟩, .set [4]⟩
def demandInput : XFact := ⟨⟨mustInput.af.fact, true⟩, Excl.empty⟩
def check : Bool :=
  (applySummaryX exactInput premise Excl.empty target == ⟨[target], []⟩) &&
  (applySummaryX mustInput premise Excl.empty target == ⟨[target], []⟩) &&
  (applySummaryX demandInput premise Excl.empty target ==
    ⟨[⟨⟨target.af.fact, true⟩, Excl.empty⟩], []⟩) &&
  (oldLayers false [false, false] == (true, false)) &&
  (oldLayers false [false, true] == (false, true)) &&
  (oldLayers true [false, false] == (false, true))
theorem checked : check = true := by decide
end Certificate

end Combination

namespace T2Oracle

def fact : PFact := ⟨3, [], .any, .conc 7⟩
def left : XFact := ⟨⟨fact, false⟩, .set [2]⟩
def right : XFact := ⟨⟨fact, false⟩, .set [4]⟩
def merged : XFact := ⟨⟨fact, false⟩, left.ex.inter right.ex⟩

/-- The single-leaf coverage test used by the old oracle, on the normal must
    rows in this example. Location inclusion reads both exclusions. -/
def leafCovers (a b : XFact) : Bool :=
  coversB a.af.fact b.af.fact && a.ex.subB b.ex

def singleCoverAgrees (reference actual : List XFact) : Bool :=
  reference.all (fun r => actual.any (fun g =>
    (g.af.demand == r.af.demand) && leafCovers g r)) &&
  actual.all (fun g => reference.any (fun r =>
    (r.af.demand == g.af.demand) && leafCovers r g))

/-- T2 exclusion intersection represents exactly the union here, at every
    concrete marked location, rather than only on a bounded test universe. -/
theorem union_locations (loc : Loc) :
    coversFX merged.af.fact merged.ex loc ↔
      coversFX left.af.fact left.ex loc ∨ coversFX right.af.fact right.ex loc := by
  obtain ⟨base, path, mark⟩ := loc
  cases path with
  | nil => simp [coversFX, merged, left, right, fact, tailI, Excl.inter,
      Excl.admits, memB]
  | cons field rest =>
      by_cases same : field = 2
      · subst field
        simp [coversFX, merged, left, right, fact, tailI, Excl.inter,
          Excl.admits, memB, show Nat.beq 2 4 = false from rfl]
      · have denied : Nat.beq field 2 = false := Bool.eq_false_iff.mpr
          (fun yes => same (CoreAux.beq_iff.mp yes))
        simp [coversFX, merged, left, right, fact, tailI, Excl.inter,
          Excl.admits, memB, denied, ← and_or_left]

namespace Certificate
def check : Bool := (merged.ex == Excl.empty) &&
  !singleCoverAgrees [left, right] [merged]
theorem checked : check = true := by decide
end Certificate

end T2Oracle

namespace RecordTiming

def j : PFact := ⟨3, [], .any, .conc 7⟩
def added : XFact := ⟨⟨⟨3, [4], .any, .conc 7⟩, false⟩, Excl.empty⟩
def g : XFact := ⟨⟨⟨5, [], .any, .conc 7⟩, false⟩, .set [2]⟩
def copy : MicroEdge := (⟨3, [], .star Excl.empty, .star⟩,
  ⟨5, [], .star Excl.empty, .star⟩)
def keep : MicroEdge := (⟨5, [], .star Excl.empty, .star⟩,
  ⟨5, [], .star (.set [2]), .star⟩)
def read : MicroEdge := (⟨5, [2], .star Excl.empty, .star⟩,
  ⟨6, [], .star Excl.empty, .star⟩)
def mustStart : XFact := startX j true Excl.empty
def constructed : List XFact :=
  (bindX mustStart copy).facts.flatMap fun x => (bindX x keep).facts
def result : List XFact := (applySummaryX added j Excl.empty g).facts
def late : List XFact := result.flatMap fun x =>
  (bindX x read).facts.map (recLayerX true (satX j Excl.empty added.af.fact added.ex))
def early : List XFact := result.flatMap fun x =>
  (bindX (recLayerX true (satX j Excl.empty added.af.fact added.ex) x) read).facts

/-- Copying a must input and strongly overwriting field 2 constructs this
    normal exclusion-bearing summary conclusion from existing AP operations. -/
theorem constructed_summary : constructed = [g] := by decide
theorem applicable_only : applicable j added.af.fact = true ∧
    satX j Excl.empty added.af.fact added.ex = false := by decide
theorem summary_result : result = [g] := by decide
theorem timing_difference : late = [] ∧
    early = [⟨⟨⟨6, [], .any, .conc 7⟩, true⟩, Excl.empty⟩] := by decide

end RecordTiming

namespace Certificate
def check : Bool := LiteralLayer.Certificate.check && Combination.Certificate.check &&
  T2Oracle.Certificate.check &&
  (RecordTiming.constructed == [RecordTiming.g]) &&
  (RecordTiming.result == [RecordTiming.g]) &&
  (RecordTiming.late == []) &&
  (RecordTiming.early ==
    [⟨⟨⟨6, [], .any, .conc 7⟩, true⟩, Excl.empty⟩])
theorem checked : check = true := by decide
end Certificate

#print axioms LiteralLayer.matched_taint_layer
#print axioms LiteralLayer.Certificate.checked
#print axioms Combination.member_application
#print axioms Combination.joined_layers
#print axioms T2Oracle.union_locations
#print axioms T2Oracle.Certificate.checked
#print axioms RecordTiming.constructed_summary
#print axioms RecordTiming.timing_difference
#print axioms Certificate.checked

end ApSpec.CurrentProposal
