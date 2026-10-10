/-
  Local equivalences for the accepted specification simplifications.

  LostCorr removes only the below-case loss bit: an admitted concrete gate
  cannot receive a legal STAR conclusion; may targets already force demand.
  Both comparisons retain the complete result and request list. Above-case
  demotion, eager normal forms, exclusions and transfer order are unchanged.

  Confirmation removes a duplicate outer admissibility check, retaining it
  within joint same-call support. MustViews factors the existing three native
  reader branches without accepting any additional shape. Analyzer compares
  finite local origin updates and abstract action-list execution/reversal.
  Flow proves canonical annotated inside-or-applicable equals annotated overlap,
  retaining added-fact exclusions and rejecting broader reader generalizations.

  These are local operation equalities. They do not prove the full annotated
  interpreter, standing stores, restricted ND closure, or iteration contract,
  and they make no runtime benchmark or record/demand disposition claim.
-/
import ApSpec.Exact
import ApSpec.CurrentSummarySelection
import ApSpec.CurrentAliasGuard

namespace ApSpec.CurrentSimplifications
open ApSpec ApSpec.Abs ApSpec.AnyTaintEx ApSpec.CurrentSummarySelection
open ApSpec.CurrentMustReverse

namespace LostCorr
/-- W2's mark clause: a STAR conclusion has an abstract mark. -/
def LegalStarMark (c : PFact) : Prop :=
  ∀ e, c.kind = .star e → Exact.absB c.mark = true

/-- A concrete-premise gate can never accept a legal STAR conclusion. -/
theorem concrete_gate_no_star {c fr : PFact} {t : Mark}
    (hlegal : LegalStarMark c) (hfr : fr.mark = .conc t)
    (hgate : markGate fr.mark c.mark = .ok) :
    ∀ e, c.kind ≠ .star e := by
  intro e he
  have hm := hlegal e he
  cases hc : c.mark with
  | conc m => simp [hc, Exact.absB] at hm
  | star => simp [hfr, hc, markGate] at hgate
  | starEx xs =>
      simp [hfr, hc, markGate] at hgate
      split at hgate <;> contradiction

/-- The below-case lostCorr bit is always false once such a gate accepts. -/
theorem concrete_gate_no_lostCorr {c fr : PFact} {t : Mark}
    (hlegal : LegalStarMark c) (hfr : fr.mark = .conc t)
    (hgate : markGate fr.mark c.mark = .ok) (r : List Acc) :
    lostCorr c.kind fr.kind r = false := by
  have hn := concrete_gate_no_star hlegal hfr hgate
  cases hk : c.kind with
  | star e => exact False.elim (hn e hk)
  | any => rfl
  | exact => rfl

/-- An exact target uses a concrete premise (S8), so its below demotion is dead. -/
theorem exact_target_no_lostCorr {c fr to : PFact}
    (hlegal : LegalStarMark c)
    (hs8 : to.kind = .exact → ∃ t, fr.mark = .conc t)
    (hto : to.kind = .exact) (hgate : markGate fr.mark c.mark = .ok)
    (r : List Acc) : lostCorr c.kind fr.kind r = false := by
  obtain ⟨t, hfr⟩ := hs8 hto
  exact concrete_gate_no_lostCorr hlegal hfr hgate r

/-- The same result covers must targets via their concrete-premise invariant. -/
theorem must_target_no_lostCorr {c fr : PFact} {t : Mark}
    (hlegal : LegalStarMark c) (hfr : fr.mark = .conc t)
    (hgate : markGate fr.mark c.mark = .ok)
    (r : List Acc) : lostCorr c.kind fr.kind r = false :=
  concrete_gate_no_lostCorr hlegal hfr hgate r


/-- Erase only below-case demand caused by the old lostCorr computation. -/
def belowSimple (ck fk : Kind) (r toPath : List Acc) (tk : Kind) :
    Option (List Acc × Kind × Bool) :=
  match tk with
  | .star _ => belowCase ck fk r toPath tk
  | .any => if admitsTailB fk r then some (toPath,.any,false) else none
  | .exact =>
    if admitsTailB fk r then
      match ck,fk,r with
      | .star _,.exact,[] => some (toPath,.star .univ,false)
      | _,_,_ => some (toPath,.exact,false)
    else none

private theorem belowSimple_as_map (ck fk : Kind) (r p : List Acc) (tk : Kind)
    (ht : tk.isStar = false) :
    belowSimple ck fk r p tk =
      (belowCase ck fk r p tk).map (fun (p,k,_) => (p,k,false)) := by
  cases tk with
  | star e => cases ht
  | any => simp [belowSimple,belowCase]
  | exact =>
    cases ck <;> cases fk <;> cases r <;>
      simp [belowSimple,belowCase,lostCorr]


def finish (c : AFact) (fr to : PFact)
    (geo : Option (List Acc × Kind × Bool)) : Res :=
  match geo with
  | none => Res.none
  | some (p,k,ap) =>
    match markGate fr.mark c.fact.mark with
    | .no => Res.none
    | .req t => ⟨[],[t]⟩
    | .ok =>
      match markComp to.mark c.fact.mark with
      | none => Res.none
      | some m => ⟨[AFact.norm ⟨⟨to.base,p,k,m⟩,c.demand || ap⟩],[]⟩

def applySimple (c : AFact) (fr to : PFact) : Res :=
  if Nat.beq c.fact.base fr.base then
    finish c fr to (match relate fr.path c.fact.path with
      | .below r => belowSimple c.fact.kind fr.kind r to.path to.kind
      | .above r => aboveCase c.fact.kind fr.kind r to.path to.kind
      | .apart => none)
  else Res.none

private theorem belowSimple_nonstar {ck : Kind}
    (hn : ∀ e, ck ≠ .star e) (fk : Kind) (r p : List Acc) (tk : Kind) :
    belowSimple ck fk r p tk = belowCase ck fk r p tk := by
  cases ck with
  | star e => exact False.elim (hn e rfl)
  | any =>
    cases tk <;> simp [belowSimple, belowCase, lostCorr]
  | exact =>
    cases tk <;> simp [belowSimple, belowCase, lostCorr]

private theorem finish_erased_blocked (c : AFact) (fr to : PFact)
    (hgate : markGate fr.mark c.fact.mark ≠ .ok)
    (g : Option (List Acc × Kind × Bool)) :
    finish c fr to (g.map fun (p,k,_) => (p,k,false)) = finish c fr to g := by
  cases g with
  | none => rfl
  | some x =>
      rcases x with ⟨p,k,ap⟩
      cases hg : markGate fr.mark c.fact.mark with
      | no => simp [finish, hg]
      | req t => simp [finish, hg]
      | ok => exact False.elim (hgate hg)

/-- Full gated equivalence. Geometry is still evaluated before mark requests;
    the above case is literally the old operation.  `hunc` is S8 for exact
    targets, and S7/W8 for must targets. May targets are handled by W6 below. -/
theorem applySimple_eq (c : AFact) (fr to : PFact)
    (hlegal : LegalStarMark c.fact)
    (hunc : to.kind.isStar = false → ∃ t, fr.mark = .conc t) :
    applySimple c fr to = applyEdge c fr to := by
  unfold applySimple applyEdge
  split
  · change finish c fr to _ = finish c fr to _
    cases hr : relate fr.path c.fact.path with
    | apart => rfl
    | above r => rfl
    | below r =>
      cases ht : to.kind.isStar with
      | true =>
        cases htk : to.kind with
        | star e => rfl
        | any => simp [htk,Kind.isStar] at ht
        | exact => simp [htk,Kind.isStar] at ht
      | false =>
        obtain ⟨t,hfr⟩ := hunc ht
        change finish c fr to (belowSimple c.fact.kind fr.kind r to.path to.kind) =
          finish c fr to (belowCase c.fact.kind fr.kind r to.path to.kind)
        cases hg : markGate fr.mark c.fact.mark with
        | ok => rw [belowSimple_nonstar (concrete_gate_no_star hlegal hfr hg)]
        | no =>
          rw [belowSimple_as_map _ _ _ _ _ ht]
          exact finish_erased_blocked c fr to (by rw [hg]; intro h; cases h) _
        | req t =>
          rw [belowSimple_as_map _ _ _ _ _ ht]
          exact finish_erased_blocked c fr to (by rw [hg]; intro h; cases h) _
  · rfl


def forceDemand (r : Res) : Res := ⟨r.facts.map (fun f => ⟨f.fact,true⟩),r.reqs⟩

private theorem force_finish_any (c : AFact) (fr to : PFact)
    (p : List Acc) (ap : Bool) :
    forceDemand (finish c fr to (some (p,.any,false))) =
    forceDemand (finish c fr to (some (p,.any,ap))) := by
  cases hg : markGate fr.mark c.fact.mark with
  | no => simp [finish,hg]
  | req t => simp [finish,hg]
  | ok =>
    cases hm : markComp to.mark c.fact.mark with
    | none => simp [finish,hg,hm]
    | some m => simp [finish,hg,hm,forceDemand,AFact.norm]

/-- May ANY targets have forced DEMAND, so their lostCorr bit is irrelevant
    even when the premise mark is abstract. -/
theorem applySimple_may_eq (c : AFact) (fr to : PFact) (hany : to.kind = .any) :
    forceDemand (applySimple c fr to) = forceDemand (applyEdge c fr to) := by
  unfold applySimple applyEdge
  split
  · change forceDemand (finish c fr to _) = forceDemand (finish c fr to _)
    cases hr : relate fr.path c.fact.path with
    | apart => rfl
    | above r => rfl
    | below r =>
      change forceDemand (finish c fr to (belowSimple c.fact.kind fr.kind r to.path to.kind)) =
        forceDemand (finish c fr to (belowCase c.fact.kind fr.kind r to.path to.kind))
      rw [belowSimple_as_map _ _ _ _ _ (by rw [hany]; rfl)]
      rw [hany]
      cases hadm : admitsTailB fr.kind r with
      | false => simp [belowCase,hadm]
      | true =>
        simp only [belowCase,hadm,↓reduceIte,Option.map_some]
        exact force_finish_any c fr to to.path _
  · rfl

namespace Certificate

def inputs : List AFact := [
  ⟨⟨4, [2], .star (.set [7]), .star⟩, false⟩,
  ⟨⟨4, [2], .star (.set [7]), .starEx []⟩, false⟩,
  ⟨⟨4, [2], .star (.set [7]), .starEx [1]⟩, false⟩,
  ⟨⟨4, [2], .exact, .conc 1⟩, false⟩,
  ⟨⟨4, [2], .any, .conc 1⟩, false⟩]
def premise : PFact := ⟨4, [], .star Excl.empty, .conc 1⟩
def target : PFact := ⟨6, [], .exact, .conc 1⟩
def oldResults := inputs.map fun f =>
  let r := applyEdge f premise target
  (r.facts, r.reqs)
def newResults := inputs.map fun f =>
  let r := applySimple f premise target
  (r.facts, r.reqs)

def check : Bool := oldResults == newResults &&
  oldResults.map Prod.snd == [[1], [1], [], [], []] &&
  oldResults.map Prod.fst == [[], [], [],
    [⟨⟨6, [], .exact, .conc 1⟩, false⟩],
    [⟨⟨6, [], .exact, .conc 1⟩, false⟩]]
theorem checked : check = true := by decide

end Certificate
end LostCorr

namespace Confirmation
/-- Local encoding of AP condition 2. `Entry.must` distinguishes a native must
    premise from the base model's may-any shape. Canonical zero is reserved. -/
def admissible (restricted : Bool) (j : Entry) : Bool :=
  (j.fact == zeroFact) ||
    (concB j.fact.mark &&
      ((j.fact.kind == .exact) ||
        (restricted && j.must && (j.fact.kind == .any))))

/-- Each supplier predicate stands for the already computed normal-link and
    supported-caller tests at ONE call site. Their internals are unchanged. -/
def suppliedAt (restricted : Bool) (js : List Entry) (supplier : Entry → Bool) : Bool :=
  js.all fun j => admissible restricted j && supplier j

def isSupported (restricted root : Bool) (js : List Entry)
    (sites : List (Entry → Bool)) : Bool :=
  (root && js.all (fun j => j.fact == zeroFact)) ||
    sites.any (suppliedAt restricted js)

theorem supplied_admissible (restricted : Bool) (js : List Entry)
    (supplier : Entry → Bool) (h : suppliedAt restricted js supplier = true) :
    js.all (admissible restricted) = true := by
  apply List.all_eq_true.mpr
  intro j hj
  have hs := List.all_eq_true.mp h j hj
  simp only [Bool.and_eq_true] at hs
  exact hs.1

theorem supported_admissible (restricted root : Bool) (js : List Entry)
    (sites : List (Entry → Bool)) (h : isSupported restricted root js sites = true) :
    js.all (admissible restricted) = true := by
  simp only [isSupported, Bool.or_eq_true] at h
  rcases h with hz | hs
  · simp only [Bool.and_eq_true] at hz
    have hall := hz.2
    apply List.all_eq_true.mpr
    intro j hj
    have hjz := List.all_eq_true.mp hall j hj
    simp [admissible, hjz]
  · obtain ⟨supplier, _, hsupplier⟩ := List.any_eq_true.mp hs
    exact supplied_admissible restricted js supplier hsupplier

def oldConfirm (normal restricted root : Bool) (js : List Entry)
    (sites : List (Entry → Bool)) : Bool :=
  normal && js.all (admissible restricted) && isSupported restricted root js sites

def simpleConfirm (normal restricted root : Bool) (js : List Entry)
    (sites : List (Entry → Bool)) : Bool :=
  normal && isSupported restricted root js sites

/-- Exact local equivalence for every witness, including rejected inputs.
    Keep same-call support and the admissibility guard inside suppliedAt. -/
theorem confirm_equiv (normal restricted root : Bool) (js : List Entry)
    (sites : List (Entry → Bool)) :
    oldConfirm normal restricted root js sites =
      simpleConfirm normal restricted root js sites := by
  cases hs : isSupported restricted root js sites with
  | false => simp [oldConfirm, simpleConfirm, hs]
  | true =>
    have ha := supported_admissible restricted root js sites hs
    simp [oldConfirm, simpleConfirm, hs, ha]

namespace Certificate

def exactT : Entry := ⟨⟨3, [], .exact, .conc 1⟩, false, Excl.empty⟩
def exactU : Entry := ⟨⟨4, [], .exact, .conc 2⟩, false, Excl.empty⟩
def mayT : Entry := ⟨⟨3, [], .any, .conc 1⟩, false, Excl.empty⟩
def mustT : Entry := {mayT with must := true}
def firstOnly (j : Entry) : Bool := j == exactT
def secondOnly (j : Entry) : Bool := j == exactU
def both (j : Entry) : Bool := firstOnly j || secondOnly j

def results : List Bool := [
  simpleConfirm true true false [exactT, exactU] [firstOnly, secondOnly],
  simpleConfirm true true false [exactT, exactU] [both],
  simpleConfirm true true false [mayT] [fun _ => true],
  simpleConfirm true true false [mustT] [fun _ => true],
  simpleConfirm true false false [mustT] [fun _ => true]]
def check : Bool := results == [false, true, false, true, false]
theorem checked : check = true := by decide

end Certificate
end Confirmation

namespace MustViews
/-- The three current must branches share two side conversions; acceptance
    guards are retained verbatim. This rewrite claims no new eligible shapes. -/
def readViewFactored (r : NativeRecord) : Option ReadView :=
  let accepted := !r.conclusion.af.demand && concB r.premise.mark &&
    concB r.conclusion.af.fact.mark &&
    match r.premise.kind, r.premiseMust with
    | .exact, false => r.premiseEx == Excl.empty && r.conclusion.af.fact.kind == .any
    | .any, true => r.conclusion.af.fact.kind == .any ||
        (r.conclusion.af.fact.kind == .exact && r.conclusion.ex == Excl.empty)
    | _, _ => false
  if accepted then
    let p := if r.conclusion.af.fact.kind == .any then
      { r.conclusion.af.fact with kind := .star r.conclusion.ex }
      else r.conclusion.af.fact
    some (p, ⟨r.premise, r.premiseMust⟩)
  else none

theorem readView_factored_equiv (r : NativeRecord) :
    readViewFactored r = readView r := by
  obtain ⟨⟨_, _, pk, pm⟩, must, jex, ⟨⟨⟨_, _, xk, xm⟩, dm⟩, ex⟩⟩ := r
  cases dm <;> cases must <;> cases pk <;> cases pm <;> cases xk <;> cases xm <;>
    simp [readViewFactored, readView, concB, revEdge, revKinds]
  all_goals by_cases hj : jex = Excl.empty <;> by_cases hx : ex = Excl.empty <;>
    simp [hj, hx]

namespace Certificate

def exactMust := exactNative 3 5 [] [2] 9 1 (.set [4])
def mustMust := mustAnyNative 3 5 [] [2] 9 1 (.set [8]) (.set [4])
def mustExact := mustExactNative 3 5 [] [2] 9 1 (.set [8])
def views := [readViewFactored exactMust, readViewFactored mustMust,
  readViewFactored mustExact]
def expected : List (Option ReadView) := [
  some (⟨5, [2], .star (.set [4]), .conc 1⟩,
    ⟨⟨3, [], .exact, .conc 9⟩, false⟩),
  some (⟨5, [2], .star (.set [4]), .conc 1⟩,
    ⟨⟨3, [], .any, .conc 9⟩, true⟩),
  some (⟨5, [2], .exact, .conc 1⟩,
    ⟨⟨3, [], .any, .conc 9⟩, true⟩)]
def check : Bool := views == expected
theorem checked : check = true := by decide

end Certificate
end MustViews

namespace Analyzer
open ApSpec.CurrentAliasGuard

/-- Only this quotient is observed by the forward alias guard. Retain the
    optional unset state, stage endpoints and stage identity separately. -/
def identityCandidate (o : Origin) : Bool := o == .identity

def compactAdmits (identity : Bool) (layer : Layer) : Bool :=
  layer == .demand || !identity

theorem origin_quotient (o : Origin) (layer : Layer) :
    admits o layer = compactAdmits (identityCandidate o) layer := by
  cases o <;> cases layer <;> rfl

inductive StageKind where
  | sources | endFacts | unresolved | constructor | bindIn | bindBack | aliases | passOver
  deriving DecidableEq, BEq, Repr

def oldUpdate (k : StageKind) (microIdentity : Bool) (prev : Option Origin) : Option Origin :=
  match k with
  | .sources => some .source
  | .endFacts => some .endFact
  | .unresolved => some (match microIdentity with | true => .identity | false => .pass)
  | .constructor => some .identity
  | .bindIn => prev
  | .bindBack => prev
  | .aliases => prev
  | .passOver => prev

def newUpdate (k : StageKind) (microIdentity : Bool) (prev : Option Bool) : Option Bool :=
  match k with
  | .sources => some false
  | .endFacts => some false
  | .unresolved => some microIdentity
  | .constructor => some true
  | .bindIn => prev
  | .bindBack => prev
  | .aliases => prev
  | .passOver => prev

theorem update_commutes (k : StageKind) (m : Bool) (prev : Option Origin) :
    (oldUpdate k m prev).map identityCandidate = newUpdate k m (prev.map identityCandidate) := by
  cases k <;> cases m <;> rfl

inductive OldActions (A : Type) where
  | clean (src dst : Nat) (steps : List A)
  | rewrite (src dst : Nat) (steps : List A)

structure Actions (A : Type) where
  src : Nat
  dst : Nat
  steps : List A

def merge {A : Type} (s : OldActions A) : Actions A :=
  match s with
  | .clean f t xs | .rewrite f t xs => ⟨f, t, xs⟩

def runChain {A F : Type} (step : A → F → List F) (xs : List A) (inputs : List F) : List F :=
  xs.foldl (fun cur a => cur.flatMap (step a)) inputs

def oldRun {A F : Type} (step : A → F → List F) (s : OldActions A) (inputs : List F) : List F :=
  match s with
  | .clean _ _ xs | .rewrite _ _ xs => runChain step xs inputs

def newRun {A F : Type} (step : A → F → List F) (s : Actions A) (inputs : List F) : List F :=
  runChain step s.steps inputs

theorem action_stage_merge {A F : Type} (step : A → F → List F) (s : OldActions A) (inputs : List F) :
    oldRun step s inputs = newRun step (merge s) inputs := by
  cases s <;> rfl

def oldReverse {A : Type} (rev : A → A) : OldActions A → OldActions A
  | .clean f t xs => .clean t f (xs.reverse.map rev)
  | .rewrite f t xs => .rewrite t f (xs.reverse.map rev)

def newReverse {A : Type} (rev : A → A) (s : Actions A) : Actions A :=
  ⟨s.dst, s.src, s.steps.reverse.map rev⟩

theorem merge_reverse {A : Type} (rev : A → A) (s : OldActions A) :
    merge (oldReverse rev s) = newReverse rev (merge s) := by
  cases s <;> rfl

namespace Certificate

def normalIdentity : Origin × Layer := (.identity, .normal)
def demandIdentity : Origin × Layer := (.identity, .demand)
def normalSource : Origin × Layer := (.source, .normal)
def oldKept := [normalIdentity, demandIdentity, normalSource].filter fun x => admits x.1 x.2
def newKept := [normalIdentity, demandIdentity, normalSource].filter fun x =>
  compactAdmits (identityCandidate x.1) x.2

def step (action fact : Nat) : List Nat := [10 * fact + action]
def clean : OldActions Nat := .clean 1 2 [3, 4]
def rewrite : OldActions Nat := .rewrite 2 3 [4, 3]
def check : Bool := oldKept == newKept && newKept == [demandIdentity, normalSource] &&
  oldRun step clean [0] == newRun step (merge clean) [0] &&
  oldRun step rewrite [0] == newRun step (merge rewrite) [0] &&
  newRun step (merge clean) [0] == [34] &&
  newRun step (merge rewrite) [0] == [43]
theorem checked : check = true := by decide

end Certificate
end Analyzer

namespace Flow

/-- The canonical restricted FLOW branch of the annotated per-leaf contract:
    annotated inside OR applicable. The premise exclusion is empty, so the
    applicable test requires no separate annotation filter. -/
theorem canonical_flow_satisfaction_X (b : Base) (p : List Acc)
    (a : PFact) (aex : Excl) :
    (satX ⟨b, p, .star Excl.empty, .star⟩ Excl.empty a aex ||
      applicable ⟨b, p, .star Excl.empty, .star⟩ a) =
    overlapX ⟨b, p, .star Excl.empty, .star⟩ Excl.empty a aex := by
  by_cases hb : b = a.base
  · subst b
    cases hdp : dropPrefix p a.path with
    | some r =>
      cases r with
      | nil =>
        cases hk : a.kind with
        | star e => cases he : e <;>
          simp [satX, applicable, coversB, hdp, overlapX, overlapB, relate,
            hk, markSubB, Kind.isAny, tailSubB, he, Excl.empty, Excl.subB,
            admitsTailB, Excl.admits]
        | any => simp [satX, applicable, coversB, hdp, overlapX, overlapB, relate,
            hk, markSubB, Kind.isAny, tailSubB, Excl.empty, Excl.isEmptyB,
            admitsTailB, Excl.admits]
        | exact => simp [satX, applicable, coversB, hdp, overlapX, overlapB, relate,
            hk, markSubB, Kind.isAny, tailSubB, Excl.empty, admitsTailB, Excl.admits]
      | cons x xs =>
        simp [satX, applicable, coversB, hdp, overlapX, overlapB, relate,
          markSubB, Kind.isAny, admitsTailB, Excl.empty, Excl.admits, memB]
    | none =>
      cases hrev : dropPrefix a.path p with
      | none => simp [satX, satI, applicable, coversB, hdp, hrev, overlapX,
          overlapB, relate, markSubB]
      | some r =>
        cases r with
        | nil =>
          have hp : p = a.path := by simpa using CoreAux.dropPrefix_some.mp hrev
          have hx : dropPrefix p a.path = some [] :=
            CoreAux.dropPrefix_some.mpr (by simp [hp])
          rw [hdp] at hx
          cases hx
        | cons x xs =>
          simp [satX, insideExB, satI, applicable, coversB, hdp, hrev,
            overlapX, overlapB, relate, markSubB, Nat.beq_refl]
  · have hbeq : Nat.beq b a.base = false := Bool.eq_false_iff.mpr
      (fun h => hb (CoreAux.beq_iff.mp h))
    have hbeq' : Nat.beq a.base b = false := Bool.eq_false_iff.mpr
      (fun h => hb (CoreAux.beq_iff.mp h).symm)
    simp [satX, satI, applicable, coversB, overlapX, overlapB, hbeq, hbeq', markSubB]

namespace Certificate

def shared : PFact := ⟨3, [2], .star Excl.empty, .star⟩
def below : PFact := ⟨3, [2, 7], .exact, .conc 9⟩
def above : PFact := ⟨3, [], .any, .conc 9⟩
def same : PFact := ⟨3, [], .star Excl.empty, .star⟩
def atMust : PFact := ⟨3, [], .any, .conc 9⟩
def satisfied (j a : PFact) (ae : Excl) : Bool := satX j Excl.empty a ae || applicable j a

/-- Added must exclusions are read at their own tail boundary. At the same
    path applicable can succeed even when inside rejects the unrestricted j. -/
def cases : List (Bool × Bool × Bool) := [
  (satX shared Excl.empty below Excl.empty, applicable shared below,
    overlapX shared Excl.empty below Excl.empty),
  (satX shared Excl.empty above (.set [4]), applicable shared above,
    overlapX shared Excl.empty above (.set [4])),
  (satX shared Excl.empty above (.set [2]), applicable shared above,
    overlapX shared Excl.empty above (.set [2])),
  (satX same Excl.empty atMust (.set [2]), applicable same atMust,
    overlapX same Excl.empty atMust (.set [2]))]

def excludedJ : PFact := ⟨3, [], .star (.set [2]), .star⟩
def excludedA : PFact := ⟨3, [], .star (.set [4]), .star⟩
def concreteJ : PFact := ⟨3, [2], .star (.set [4]), .conc 9⟩
def wrongMark : PFact := ⟨3, [2, 7], .exact, .conc 10⟩

def check : Bool := cases == [(false, true, true), (true, false, true),
  (false, false, false), (false, true, true)] &&
  !satisfied excludedJ excludedA Excl.empty &&
  overlapX excludedJ Excl.empty excludedA Excl.empty &&
  !satisfied concreteJ wrongMark Excl.empty &&
  overlapX concreteJ Excl.empty wrongMark Excl.empty
theorem checked : check = true := by decide

end Certificate
end Flow

namespace Certificate

def check : Bool := LostCorr.Certificate.check && Confirmation.Certificate.check &&
  MustViews.Certificate.check && Analyzer.Certificate.check && Flow.Certificate.check
theorem checked : check = true := by decide

end Certificate

#print axioms LostCorr.applySimple_eq
#print axioms LostCorr.applySimple_may_eq
#print axioms Confirmation.confirm_equiv
#print axioms MustViews.readView_factored_equiv
#print axioms Analyzer.origin_quotient
#print axioms Analyzer.update_commutes
#print axioms Analyzer.action_stage_merge
#print axioms Analyzer.merge_reverse
#print axioms Flow.canonical_flow_satisfaction_X
#print axioms Certificate.checked
end ApSpec.CurrentSimplifications
