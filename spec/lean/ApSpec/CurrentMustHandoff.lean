/-
  Local must-to-may copying and original generic-Cross handoff diagnostics.

  A normal ANY conclusion with a concrete mark is a must fact in the
  annotated forward model. A backward demand pattern has no must flag or
  must exclusion. The computed copy keeps the base, path, tail and concrete
  mark, and forgets the exclusion. It can add locations but loses none.

  The selection theorems use original Handoff.Cross. F77 instead omits every
  native normal singleton admitted by R1. These historical selection theorems
  do not specify that policy. Copy certificates apply to selected demand pieces.

  The historical generic demand selector reads the RAW leaf. Restriction then
  publishes a piece, and the handoff copies it. In that older selector every
  raw must conclusion is selected, also when its piece narrows to exact. F77's
  selector omits all native R1-eligible normal singleton summaries. These local copy
  results still apply to current selected demand pieces; the historical selector
  does not specify current demand ownership.
  The blocked-record vector below uses generic revRec as a diagnostic. F76's
  actual must-summary reader uses CurrentMustReverse's STAR/E view instead.
-/
import ApSpec.CurrentHandoff
import ApSpec.HandoffXRestrict

namespace ApSpec.CurrentMustHandoff
open ApSpec ApSpec.Reverse ApSpec.Handoff ApSpec.Abs
open ApSpec.AnyTaintEx ApSpec.AnyTaintExCov ApSpec.HandoffX

/-- Generic revEdge reverses an ANY conclusion to an ANY premise. F76's
    separate record view uses STAR/E instead. -/
theorem rev_premise_any {j f : PFact} (h : f.kind = .any) :
    (revEdge j f).1.kind = .any := by
  rw [revEdge_eq]
  dsimp only
  rw [h]
  cases j.kind <;> rfl

/-- This includes both normal must and demand may conclusions. -/
theorem noCross_any {j : PFact} {g : AFact} (h : g.fact.kind = .any) :
    ¬ Cross j g := by
  rintro ⟨_, _, _, hk⟩
  rw [rev_premise_any h] at hk
  exact hk

/-- On the normal ANY subtree, testing non-Cross is redundant. Selection
    must occur before restriction; this equality concerns raw leaves. -/
theorem normal_any_selection {j : PFact} {g : AFact} :
    (g.demand = false ∧ g.fact.kind = .any ∧ ¬ Cross j g) ↔
      (g.demand = false ∧ g.fact.kind = .any) :=
  ⟨fun h => ⟨h.1, h.2.1⟩, fun h => ⟨h.1, h.2, noCross_any h.2⟩⟩

def normalAnyB (x : PFact × AFact) : Bool := !x.2.demand && x.2.fact.kind.isAny

def selectAny (raw : List (PFact × AFact)) : List (PFact × AFact) :=
  raw.filter normalAnyB

def selectAnyChecked (crossTest : PFact × AFact → Bool)
    (raw : List (PFact × AFact)) : List (PFact × AFact) :=
  raw.filter (fun x => normalAnyB x && !crossTest x)

/-- The executable tail selector gives the same leaves as the Cross filter.
    Only soundness of true Cross tests is needed, not a completeness oracle. -/
theorem selectAny_equiv (crossTest : PFact × AFact → Bool)
    (sound : ∀ x, crossTest x = true → Cross x.1 x.2)
    (raw : List (PFact × AFact)) : selectAnyChecked crossTest raw = selectAny raw := by
  have hp : ∀ x, (normalAnyB x && !crossTest x) = normalAnyB x := by
    intro x
    cases hn : normalAnyB x with
    | false => rfl
    | true =>
      have hk : x.2.fact.kind = .any := by
        cases h : x.2.fact.kind with
        | any => rfl
        | exact => simp [normalAnyB, h, Kind.isAny] at hn
        | star _ => simp [normalAnyB, h, Kind.isAny] at hn
      have hc : crossTest x = false := by
        cases h : crossTest x with
        | false => rfl
        | true => exact False.elim (noCross_any hk (sound x h))
      rw [hc]
      rfl
  unfold selectAnyChecked selectAny
  induction raw with
  | nil => rfl
  | cons x xs ih => simp only [List.filter_cons, hp x, ih]

/-- Demand patterns have no forward must flag or must exclusion. The demand
    layer on this diagnostic view makes the conversion explicit. -/
def mayCopy (x : XFact) : XFact :=
  ⟨⟨normFact x.af.fact, true⟩, Excl.empty⟩

theorem mayCopy_shape (x : XFact) :
    (mayCopy x).af.fact.base = x.af.fact.base ∧
    (mayCopy x).af.fact.path = x.af.fact.path ∧
    (mayCopy x).af.fact.kind = x.af.fact.kind ∧
    (mayCopy x).af.demand = true ∧ (mayCopy x).ex = Excl.empty :=
  ⟨rfl, rfl, rfl, rfl, rfl⟩

theorem mayCopy_concrete {x : XFact} {t : Mark} (h : x.af.fact.mark = .conc t) :
    (mayCopy x).af.fact.mark = .conc t := by
  change markNorm x.af.fact.mark = .conc t
  rw [h]
  rfl

theorem mayCopy_covers {x : XFact} {l : Loc}
    (h : coversFX x.af.fact x.ex l) : (mayCopy x).af.fact.covers l := by
  obtain ⟨hb, ⟨σ, hp, hi, _⟩, t, ht, hlt⟩ := h
  apply normFact_covers
  refine ⟨hb, ⟨σ, hp, hi⟩, ?_⟩
  rw [ht, hlt]
  rfl

/-- Forgetting mark exclusions as well as field annotations preserves pairs. -/
theorem den_normalize {j f : PFact} {l0 l1 : Loc} (h : den j f l0 l1) :
    den (normFact j) (normFact f) l0 l1 := by
  obtain ⟨hb0, hb1, hm0, hm1, hpass, σ, τ, hp0, hp1, hi, hf⟩ := h
  refine ⟨hb0, hb1, markNorm_admits hm0, ?_, ?_, σ, τ, hp0, hp1, hi, hf⟩
  · change l1.mark = (markNorm f.mark).out l0.mark
    cases hfm : f.mark <;> rw [hfm] at hm1 <;> exact hm1
  · change (markNorm f.mark).passes l0.mark
    cases f.mark with
    | star => trivial
    | conc _ => trivial
    | starEx _ => trivial

theorem mayCopy_pairs {x : XFact} {j : PFact} {jex : Excl} {l0 l1 : Loc}
    (h : denX j jex x.af.fact x.ex l0 l1) :
    den (normFact j) (mayCopy x).af.fact l0 l1 :=
  den_normalize (denX_den h)

/-- The returned value is the actual conversion, with no existential choice. -/
structure CopyCertificate (x : XFact) where
  copied : XFact
  computed : copied = mayCopy x
  shape : copied.af.fact.base = x.af.fact.base ∧
    copied.af.fact.path = x.af.fact.path ∧ copied.af.fact.kind = x.af.fact.kind
  mark : ∀ t, x.af.fact.mark = .conc t → copied.af.fact.mark = .conc t
  may : copied.af.demand = true ∧ copied.ex = Excl.empty
  covers : ∀ l, coversFX x.af.fact x.ex l → copied.af.fact.covers l
  pairs : ∀ j jex l0 l1, denX j jex x.af.fact x.ex l0 l1 →
    den (normFact j) copied.af.fact l0 l1

def copyCertificate (x : XFact) : CopyCertificate x :=
  ⟨mayCopy x, rfl, ⟨rfl, rfl, rfl⟩, fun _ => mayCopy_concrete,
    ⟨rfl, rfl⟩, fun _ => mayCopy_covers, fun _ _ _ _ => mayCopy_pairs⟩

/-- The computed demand copies the published piece, not the raw leaf. -/
def handoffCopy (j : PFact) (published : AFact) : DemandEdge :=
  normDem ⟨published.fact, some j⟩

/-- Every original published pair has both endpoints in the computed demand,
    including when its premise or conclusion has a nonempty annotation. -/
theorem handoff_retains_pair {j : PFact} {jex : Excl} {published : XFact} {l0 l1 : Loc}
    (h : denX j jex published.af.fact published.ex l0 l1) :
    (handoffCopy j published.af).din.covers l1 ∧
    (handoffCopy j published.af).dout = some (normFact j) ∧
    (normFact j).covers l0 := by
  have hb := denX_den h
  refine ⟨normFact_covers (den_covers_final hb), rfl, normFact_covers ?_⟩
  obtain ⟨hbase, _, hmark, _, _, σ, _, hpath, _, htail, _⟩ := hb
  exact ⟨hbase, ⟨σ, hpath, htail⟩, hmark⟩

/-- Every reached raw ANY leaf generates demand for each published piece,
    even if restriction changes its tail to exact. -/
theorem any_published_demand {P : Program} {R : Obj → Prop} {pub : Pub}
    {m : MethodId} {j : PFact} {raw published : AFact}
    (hi : R (.init m j)) (he : R (.edge m j (P.exit m) raw))
    (hk : raw.fact.kind = .any) (hp : pub m j raw published) :
    handFA P R pub m (handoffCopy j published) :=
  ⟨⟨published.fact, some j⟩, ⟨j, raw, published, hi, he, noCross_any hk, hp, rfl⟩, rfl⟩

def handoffCertificate (P : Program) (R : Obj → Prop) (pub : Pub) (m : MethodId)
    (j : PFact) (raw published : AFact)
    (hi : R (.init m j)) (he : R (.edge m j (P.exit m) raw))
    (hk : raw.fact.kind = .any) (hp : pub m j raw published) :
    {d : DemandEdge // d = handoffCopy j published ∧ handFA P R pub m d} :=
  ⟨handoffCopy j published, rfl, any_published_demand hi he hk hp⟩

/-- The annotated publication interface already forgets the must exclusion.
    No annotated cleaner or whole-run theorem is assumed here. -/
theorem x_any_restricted_demand {P : Program} {R : XObj → Prop}
    {dem : MethodId → DemandEdge → Prop} {m : MethodId} {j : PFact}
    {must : Bool} {jex : Excl} {raw published : XFact} {d : DemandEdge}
    (hi : R (.init m j must jex)) (he : R (.edge m j must jex (P.exit m) raw))
    (hk : raw.af.fact.kind = .any) (hd : dem m d)
    (hr : restrictIX j jex raw d = some published) :
    handFA P (forgetX R) (pubRX P R dem) m (handoffCopy j published.af) := by
  apply any_published_demand
  · exact ⟨.init m j must jex, hi, rfl⟩
  · exact ⟨.edge m j must jex (P.exit m) raw, he, rfl⟩
  · exact hk
  · exact ⟨must, jex, raw.ex, published.ex, d, he, hd, hr⟩

namespace Certificate

def j : PFact := ⟨3, [], .exact, .conc 1⟩
def must : XFact := ⟨⟨⟨5, [], .any, .conc 1⟩, false⟩, Excl.empty⟩
def excludedMust : XFact := ⟨must.af, .set [4]⟩
def incoming : PFact := ⟨5, [7], .exact, .conc 1⟩
def excludedIncoming : PFact := ⟨5, [4], .exact, .conc 1⟩
def backwardRecord : PFact × AFact := revRec (j, must.af)
def reversedMicro : MicroEdge := revEdge j must.af.fact
def copy : CopyCertificate excludedMust := copyCertificate excludedMust
def emitted : Option PFact := emitW copy.copied.af.fact incoming
def recovered : Res := applyEdge ⟨incoming, false⟩ reversedMicro.1 reversedMicro.2

/-- The old generic revRec diagnostic cannot read an exact requirement.
    The F76 STAR/E read view removes this cached-record reuse limit. -/
theorem record_blocked :
    satW backwardRecord.1 incoming = false ∧
    applicable backwardRecord.1 incoming = false := by decide

/-- The may demand admits the requirement; direct reversed statement transfer
    recovers the concrete nonzero premise that record satisfaction rejected. -/
theorem micro_recovery :
    emitted = some incoming ∧ recovered.facts = [⟨j, false⟩] ∧ recovered.reqs = [] := by decide

/-- Dropping the exclusion permits extra demands at excluded fields. -/
theorem excluded_field_demand :
    emitW copy.copied.af.fact excludedIncoming = some excludedIncoming ∧
    copy.copied.ex = Excl.empty := by decide

theorem exclusion_loss_adds_location :
    ¬ coversFX excludedMust.af.fact excludedMust.ex ⟨5, [4], 1⟩ ∧
    copy.copied.af.fact.covers ⟨5, [4], 1⟩ := by
  constructor
  · rintro ⟨_, ⟨τ, hp, _, ha⟩, _⟩
    have hτ : τ = [4] := hp.symm
    rw [hτ] at ha
    cases ha
  · exact ⟨rfl, ⟨[4], rfl, trivial⟩, rfl⟩

/-- The original admitted pair survives the computed conversion. -/
def admittedPair : {l : Loc //
    denX j Excl.empty excludedMust.af.fact excludedMust.ex ⟨3, [], 1⟩ l ∧
    den (normFact j) copy.copied.af.fact ⟨3, [], 1⟩ l} :=
  let h : denX j Excl.empty excludedMust.af.fact excludedMust.ex
      ⟨3, [], 1⟩ ⟨5, [7], 1⟩ :=
    ⟨rfl, rfl, rfl, rfl, trivial, [], [7], rfl, rfl, rfl, trivial, rfl, rfl⟩
  ⟨⟨5, [7], 1⟩, h, copy.pairs j Excl.empty _ _ h⟩

def exitDemand : DemandEdge := ⟨j, some incoming⟩
def publishedExact : XFact := ⟨⟨incoming, false⟩, Excl.empty⟩
def pubProgram : Program := ⟨fun _ => 0, fun _ => 1, []⟩
def reached : Obj → Prop := fun o => o = .init 2 j ∨ o = .edge 2 j 1 must.af
def demanded : MethodId → DemandEdge → Prop := fun m d => m = 2 ∧ d = exitDemand

/-- A concrete exact D-p narrows an admitted field and drops the annotation;
    an excluded field still has no published result. -/
theorem exact_reduction :
    restrictIX j Excl.empty excludedMust exitDemand = some publishedExact ∧
    restrictIX j Excl.empty excludedMust ⟨j, some excludedIncoming⟩ = none ∧
    restrictI j must.af exitDemand = some publishedExact.af := by decide

def exactHandoff : {d : DemandEdge // d = ⟨incoming, some j⟩ ∧
    handFA pubProgram reached (pubR demanded) 2 d} :=
  let w := handoffCertificate pubProgram reached (pubR demanded) 2 j must.af publishedExact.af
    (Or.inl rfl) (Or.inr rfl) rfl ⟨exitDemand, ⟨rfl, rfl⟩, exact_reduction.2.2⟩
  ⟨w.val, rfl, w.property.2⟩

def xReached : XObj → Prop := fun o =>
  o = .init 2 j false Excl.empty ∨ o = .edge 2 j false Excl.empty 1 excludedMust

/-- Same exact publication, through the annotated restriction interface. -/
def xExactHandoff : {d : DemandEdge // d = ⟨incoming, some j⟩ ∧
    handFA pubProgram (forgetX xReached) (pubRX pubProgram xReached demanded) 2 d} :=
  ⟨handoffCopy j publishedExact.af, rfl,
    x_any_restricted_demand (Or.inl rfl) (Or.inr rfl) rfl ⟨rfl, rfl⟩ exact_reduction.1⟩

def rawLeaves : List (PFact × AFact) := [(j, must.af), (j, publishedExact.af)]

theorem raw_selection_before_publication :
    selectAny rawLeaves = [(j, must.af)] ∧
    selectAny [(j, publishedExact.af)] = [] := by decide

#eval copy.copied
#eval emitted
#eval recovered
#eval exactHandoff.val
#eval xExactHandoff.val
#eval selectAny rawLeaves

end Certificate

#print axioms noCross_any
#print axioms normal_any_selection
#print axioms selectAny_equiv
#print axioms copyCertificate
#print axioms handoffCertificate
#print axioms handoff_retains_pair
#print axioms x_any_restricted_demand
#print axioms Certificate.exactHandoff
#print axioms Certificate.admittedPair
#print axioms Certificate.xExactHandoff

end ApSpec.CurrentMustHandoff
