/-
  F75 base handoff shape and reversal guards. These are closure invariants,
  not assumptions about a finite example. Concrete non-star seeds are explicit.
  This module proves normal-record reversal and the demand emission tail guard;
  it does not prove source retention or full iteration coverage. Alternating
  shape sequences here use the historical generic Cross selector, not F77.
  Local shape and reversal lemmas keep their stated assumptions.
-/
import ApSpec.CurrentExact
import ApSpec.AbsHandoff

namespace ApSpec.Current
open ApSpec ApSpec.Abs ApSpec.Handoff ApSpec.HandoffNoStar ApSpec.Reverse
open ApSpec.AbsHandoff (normFact_normMark emitW_cross_or_conc emitW_premEmpty entryShape_PatTail)

abbrev NormMark := ApSpec.AbsHandoff.NormMark
abbrev EntryShape := ApSpec.AbsHandoff.EntryShape

section
open ApSpec.Invariant
variable {P : Program} {counted : Acc → Bool} {L : Nat}
  {α : MethodId → PFact → PFact} {sinks : List (MethodId × Node × PFact)}
  {roots : List MethodId}
theorem RC_inv {o : Obj} (h : RC P counted L α sinks roots o) : Invariant.Inv o := by
  induction h with
  | root => trivial
  | @start M i _ =>
    exact ⟨fun e hk => startFact_legal hk, fun ei hi => startFact_Q hi⟩
  | @step M i n f n' s f' _ _ hf' ih =>
    obtain ⟨ih1, ih2⟩ := ih
    rcases transfer_mem hf' with rfl | ⟨x, e, _, hx, rfl⟩
    · exact ⟨ih1, ih2⟩
    · exact ⟨limitF_Legal (applyEdge_Legal hx),
        fun ei hi => limitF_Q (applyEdge_Q (ih2 ei hi) ih1 hx)⟩
  | reqStmt => trivial
  | pass _ _ _ ih => exact ih
  | added => trivial
  | initA => trivial
  | @ret M i n f n' c e1 a j g r e2 r' _ _ _ ha _ _ _ hr _ hr' ihF _ _ =>
    obtain ⟨ihL, ihQ⟩ := ihF
    refine ⟨limitF_Legal (applyEdge_Legal hr'), fun ei hi => ?_⟩
    have qa := applyEdge_Q (ihQ ei hi) ihL ha
    have qr := applySummary_Q qa (applyEdge_Legal ha) hr
    exact limitF_Q (applyEdge_Q qr (applySummary_Legal hr) hr')
  | reqSink => trivial
  | answer => trivial
  | reqUp => trivial
  | vuln => trivial
  | @clean M i n f n' cl f' _ _ hf' ih =>
    obtain ⟨ih1, ih2⟩ := ih
    exact ⟨Current.cleanRes_Legal ih1 hf', fun ei hi => Current.cleanRes_Q (ih2 ei hi) hf'⟩
  | reqClean => trivial
  | filt _ _ _ ih => exact ih

theorem RC_premK (hα : ∀ m a, Run1K (α m a).kind) {o : Obj}
    (h : RC P counted L α sinks roots o) : PremK o := by
  induction h with
  | root => exact Or.inl rfl
  | start _ ih => exact ih
  | step _ _ _ ih => exact ih
  | reqStmt _ _ _ ih => exact ih
  | pass _ _ _ ih => exact ih
  | added => trivial
  | initA => exact hα _ _
  | ret _ _ _ _ _ _ _ _ _ _ ih _ _ => exact ih
  | reqSink _ _ _ ih => exact ih
  | @answer M i t a _ _ _ _ ihR _ =>
    rcases HandoffNoStar.answerInit_kind i a t with h | h
    · exact Or.inl h
    · show Run1K (answerInit i a t).kind
      rw [h]
      exact ihR
  | reqUp _ _ _ _ _ _ _ _ _ ihE => exact ihE
  | vuln => trivial
  | clean _ _ _ ih => exact ih
  | reqClean _ _ _ ih => exact ih
  | filt _ _ _ ih => exact ih

end

section
variable {P : Program} {counted : Acc → Bool} {L : Nat}
  {demand : MethodId → DemandEdge → Prop}
  {emit : PFact → PFact → Option PFact} {sat : PFact → PFact → Bool}
  {restrict : PFact → AFact → DemandEdge → Option AFact} {recs : Recs}
  {sinks : List (MethodId × Node × PFact)} {roots : List MethodId}
theorem FC_inv {o : Obj} (h : FC P counted L demand emit sat restrict recs sinks roots o) :
    Invariant.Inv o := by
  induction h with
  | root => trivial
  | @start M i _ =>
    exact ⟨fun e hk => Invariant.startFact_legal hk, fun ei hi => Invariant.startFact_Q hi⟩
  | @step M i n f n' s f' _ _ hf' ih =>
    obtain ⟨ih1, ih2⟩ := ih
    rcases Invariant.transfer_mem hf' with rfl | ⟨x, e, _, hx, rfl⟩
    · exact ⟨ih1, ih2⟩
    · exact ⟨Invariant.limitF_Legal (Invariant.applyEdge_Legal hx),
        fun ei hi => Invariant.limitF_Q (Invariant.applyEdge_Q (ih2 ei hi) ih1 hx)⟩
  | pass _ _ _ ih => exact ih
  | added => trivial
  | initR => trivial
  | @ret M i n f n' c e1 a j g d g' r e2 r' _ _ _ ha _ _ _ _ _ hr _ hr' ihF _ _ =>
    obtain ⟨ihL, ihQ⟩ := ihF
    refine ⟨Invariant.limitF_Legal (Invariant.applyEdge_Legal hr'), fun ei hi => ?_⟩
    have qa := Invariant.applyEdge_Q (ihQ ei hi) ihL ha
    have qr := Invariant.applySummary_Q qa (Invariant.applyEdge_Legal ha) hr
    exact Invariant.limitF_Q (Invariant.applyEdge_Q qr (Invariant.applySummary_Legal hr) hr')
  | @retRec M i n f n' c e1 a j g r e2 r' _ _ _ ha _ _ hr _ hr' ihF =>
    obtain ⟨ihL, ihQ⟩ := ihF
    refine ⟨Invariant.limitF_Legal (Invariant.applyEdge_Legal hr'), fun ei hi => ?_⟩
    have qa := Invariant.applyEdge_Q (ihQ ei hi) ihL ha
    have qr := Invariant.applySummary_Q qa (Invariant.applyEdge_Legal ha) hr
    exact Invariant.limitF_Q (Invariant.applyEdge_Q qr (Invariant.applySummary_Legal hr) hr')
  | vuln => trivial
  | @clean M i n f n' cl f' _ _ hf' ih =>
    obtain ⟨ih1, ih2⟩ := ih
    exact ⟨Current.cleanRes_Legal ih1 hf', fun ei hi => Current.cleanRes_Q (ih2 ei hi) hf'⟩
  | filt _ _ _ ih => exact ih

end

theorem run1_exit_star_cross {P : Program} {counted : Acc → Bool} {L : Nat}
    {sinks : List (MethodId × Node × PFact)} {roots : List MethodId} {M : MethodId}
    {j : PFact} {n : Node} {g : AFact} {E : Excl}
    (hj : RC P counted L policy1 sinks roots (.init M j))
    (hg : RC P counted L policy1 sinks roots (.edge M j n g)) (hk : g.fact.kind = .star E) :
    Cross j g := by
  have hpj : Run1K j.kind := RC_premK policy1_run1K hj
  obtain ⟨habs, hdem⟩ := (RC_inv hg).1 E hk
  exact ⟨hdem, run1K_crossK hpj, Or.inl habs, crossK_rev_star hpj hk⟩

#print axioms run1_exit_star_cross

/-- THE HAND-OFF OF RUN 1 HAS NO `*` ENTRY PATTERN. Every demand edge `handF` gives for run 1 (the
    publication `pubD`) has an entry pattern `D-c` with no `*` tail; its exit pattern `D-p` (a
    premise of run 1) has the tail `$` or `*/{}`. -/
theorem handF_run1_nonstar {P : Program} {counted : Acc → Bool} {L : Nat}
    {sinks : List (MethodId × Node × PFact)} {roots : List MethodId} {m : MethodId}
    {d : DemandEdge} (h : handF P (RC P counted L policy1 sinks roots) pubD m d) :
    NoStarK d.din.kind ∧ ∀ p, d.dout = some p → Run1K p.kind := by
  obtain ⟨j, g, g', hj, hg, hnc, hpub, rfl⟩ := h
  have hg' : g' = g := hpub
  subst hg'
  refine ⟨fun E hE => hnc (run1_exit_star_cross hj hg hE), fun p hp => ?_⟩
  have hjp : j = p := Option.some.inj hp
  subst hjp
  exact RC_premK policy1_run1K hj


theorem backward_concrete_premise {Pb : Program} {counted : Acc → Bool} {L : Nat}
    {demand : MethodId → DemandEdge → Prop} {emit : PFact → PFact → Option PFact}
    {sat : PFact → PFact → Bool} {restrict : PFact → AFact → DemandEdge → Option AFact}
    {recs : Recs} {sinks : List (MethodId × Node × PFact)} {roots : List MethodId}
    {seeds : List (MethodId × Node × PFact)} {zbind : Bool} {o : Obj}
    (hsd : ∀ M n s, (M, n, s) ∈ seeds → ∃ t, s.mark = .conc t)
    (h : BC Pb counted L demand emit sat restrict recs sinks roots seeds zbind o) :
    ReviewDemand.ConcEdge o := by
  induction h with
  | root => trivial
  | @start M i _ _ =>
    intro hc
    obtain ⟨t, ht⟩ := hc
    exact ⟨t, by rw [startFact_mark, ht]⟩
  | @step M i n f n' s f' _ _ hf ih =>
    intro hc
    obtain ⟨t, ht⟩ := ih hc
    exact transfer_mark_conc ht hf
  | pass _ _ _ ih => exact ih
  | added => trivial
  | initR => trivial
  | @ret M i n f n' c e1 a j g d g' r e2 r' _ _ _ ha _ _ _ _ _ hr _ hr' ihF _ _ =>
    intro hc
    obtain ⟨t, ht⟩ := ihF hc
    obtain ⟨t1, h1⟩ := applyEdge_mark_conc ht ha
    obtain ⟨t2, h2⟩ := applySummary_mark_conc h1 hr
    obtain ⟨t3, h3⟩ := applyEdge_mark_conc h2 hr'
    exact ⟨t3, by rw [limitF_mark, h3]⟩
  | @retRec M i n f n' c e1 a j g r e2 r' _ _ _ ha _ _ hr _ hr' ihF =>
    intro hc
    obtain ⟨t, ht⟩ := ihF hc
    obtain ⟨t1, h1⟩ := applyEdge_mark_conc ht ha
    obtain ⟨t2, h2⟩ := applySummary_mark_conc h1 hr
    obtain ⟨t3, h3⟩ := applyEdge_mark_conc h2 hr'
    exact ⟨t3, by rw [limitF_mark, h3]⟩
  | vuln => trivial
  | @clean M i n f n' cl f' _ _ hf ih =>
    intro hc
    obtain ⟨t, ht⟩ := ih hc
    exact Current.cleanRes_mark_conc ht hf
  | filt _ _ _ ih => exact ih
  | zpass => exact fun _ => ⟨zeroMark, rfl⟩
  | zin => trivial
  | @seed M n s hs _ _ =>
    obtain ⟨t, ht⟩ := hsd M n s hs
    exact fun _ => ⟨t, by rw [limitF_mark]; exact ht⟩
  | @zret M n n' c g r e2 r' _ _ _ _ hr _ hr' _ _ =>
    obtain ⟨t2, h2⟩ := applySummary_mark_conc (a := Backward.zeroAF) (t := zeroMark) rfl hr
    obtain ⟨t3, h3⟩ := applyEdge_mark_conc h2 hr'
    exact fun _ => ⟨t3, by rw [limitF_mark, h3]⟩

theorem backward_zero_premise_concrete {Pb : Program} {counted : Acc → Bool} {L : Nat}
    {demand : MethodId → DemandEdge → Prop} {emit : PFact → PFact → Option PFact}
    {sat : PFact → PFact → Bool} {restrict : PFact → AFact → DemandEdge → Option AFact}
    {recs : Recs} {sinks : List (MethodId × Node × PFact)} {roots : List MethodId}
    {seeds : List (MethodId × Node × PFact)} {zbind : Bool} {M n} {f : AFact}
    (hsd : ∀ M n s, (M, n, s) ∈ seeds → ∃ t, s.mark = .conc t)
    (h : BC Pb counted L demand emit sat restrict recs sinks roots seeds zbind (.edge M zeroFact n f)) :
    ∃ t, f.fact.mark = .conc t := backward_concrete_premise hsd h ⟨zeroMark, rfl⟩


section Forward
variable {P : Program} {counted : Acc → Bool} {L : Nat}
  {dem : MethodId → DemandEdge → Prop} {sat : PFact → PFact → Bool}
  {restrict : PFact → AFact → DemandEdge → Option AFact} {rc : Recs}
  {sinks : List (MethodId × Node × PFact)} {roots : List MethodId}

local notation "FR" => FC P counted L dem emitW sat restrict rc sinks roots

theorem forward_concrete_premise {o : Obj} (h : FR o) : ReviewDemand.ConcEdge o := by
  induction h with
  | root => trivial
  | @start M i _ _ =>
    intro hc
    obtain ⟨t, ht⟩ := hc
    exact ⟨t, by rw [startFact_mark, ht]⟩
  | @step M i n f n' s f' _ _ hf ih =>
    intro hc
    obtain ⟨t, ht⟩ := ih hc
    exact transfer_mark_conc ht hf
  | pass _ _ _ ih => exact ih
  | added => trivial
  | initR => trivial
  | @ret M i n f n' c e1 a j g d g' r e2 r' _ _ _ ha _ _ _ _ _ hr _ hr' ihF _ _ =>
    intro hc
    obtain ⟨t, ht⟩ := ihF hc
    obtain ⟨t1, h1⟩ := applyEdge_mark_conc ht ha
    obtain ⟨t2, h2⟩ := applySummary_mark_conc h1 hr
    obtain ⟨t3, h3⟩ := applyEdge_mark_conc h2 hr'
    exact ⟨t3, by rw [limitF_mark, h3]⟩
  | @retRec M i n f n' c e1 a j g r e2 r' _ _ _ ha _ _ hr _ hr' ihF =>
    intro hc
    obtain ⟨t, ht⟩ := ihF hc
    obtain ⟨t1, h1⟩ := applyEdge_mark_conc ht ha
    obtain ⟨t2, h2⟩ := applySummary_mark_conc h1 hr
    obtain ⟨t3, h3⟩ := applyEdge_mark_conc h2 hr'
    exact ⟨t3, by rw [limitF_mark, h3]⟩
  | vuln => trivial
  | @clean M i n f n' cl f' _ _ hf ih =>
    intro hc
    obtain ⟨t, ht⟩ := ih hc
    exact Current.cleanRes_mark_conc ht hf
  | filt _ _ _ ih => exact ih

theorem forward_init_shape (hd : EntryShape dem) {M : MethodId} {j : PFact}
    (h : FR (.init M j)) : CrossK j.kind ∨ ∃ t, j.mark = .conc t := by
  cases h with
  | root => exact Or.inl trivial
  | initR _ hdem he =>
    have hs := hd _ _ hdem
    exact emitW_cross_or_conc hs.1 hs.2 he

theorem forward_init_premEmpty (hd : EntryShape dem) {M : MethodId} {j : PFact}
    (h : FR (.init M j)) : Reverse.PremEmpty j.kind := by
  cases h with
  | root => trivial
  | initR ha hdem he =>
    have hs := hd _ _ hdem
    apply emitW_premEmpty hs.1 hs.2 _ he
    cases ha with
    | added _ _ _ hx =>
      intro t ht e hek
      exact Invariant.AbsMark.not_conc (Invariant.applyEdge_Legal hx e hek).1 t ht

/-- A raw star conclusion is crossable before summary restriction. -/
theorem forward_star_cross (hd : EntryShape dem) {M : MethodId} {j : PFact} {n : Node}
    {g : AFact} {e : Excl} (hj : FR (.init M j)) (hg : FR (.edge M j n g))
    (hk : g.fact.kind = .star e) : Cross j g := by
  have hl := (FC_inv hg).1 e hk
  rcases forward_init_shape hd hj with hcross | hconc
  · exact cross_of_star_concl hl.2 hcross hk hl.1
  · obtain ⟨t, ht⟩ := forward_concrete_premise hg hconc
    exact (Invariant.AbsMark.not_conc hl.1 t ht).elim

theorem handFA_forward_entry_shape (hd : EntryShape dem) :
    EntryShape (handFA P FR (pubR dem)) := by
  intro M d h
  obtain ⟨d0, ⟨j, g, g', hj, hg, hnc, ⟨dp, _, hp⟩, rfl⟩, rfl⟩ := h
  obtain ⟨p, _, _, hc⟩ := restrictI_some hp
  refine ⟨?_, normFact_normMark _⟩
  change NoStarK g'.fact.kind
  apply restrictConcI_nonstar hc
  intro e hk
  exact hnc (forward_star_cross hd hj hg hk)

end Forward

section Backward
variable {Pb : Program} {counted : Acc → Bool} {L : Nat}
  {dem : MethodId → DemandEdge → Prop} {emit : PFact → PFact → Option PFact}
  {sat : PFact → PFact → Bool} {restrict : PFact → AFact → DemandEdge → Option AFact}
  {rc : Recs} {sinks : List (MethodId × Node × PFact)} {roots : List MethodId}
  {seeds : List (MethodId × Node × PFact)} {zbind : Bool}

local notation "BR" => BC Pb counted L dem emit sat restrict rc sinks roots seeds zbind

theorem backward_legal (hsk : ∀ M n s, (M, n, s) ∈ seeds → NoStarK s.kind)
    {o : Obj} (h : BR o) : LegalE o := by
  induction h with
  | root => trivial
  | start _ _ => exact fun _ hk => Invariant.startFact_legal hk
  | step _ _ hf ih =>
    rcases Invariant.transfer_mem hf with rfl | ⟨x, e, _, hx, rfl⟩
    · exact ih
    · exact Invariant.limitF_Legal (Invariant.applyEdge_Legal hx)
  | pass _ _ _ ih => exact ih
  | added => trivial
  | initR => trivial
  | ret _ _ _ _ _ _ _ _ _ _ _ hr' _ _ _ =>
    exact Invariant.limitF_Legal (Invariant.applyEdge_Legal hr')
  | retRec _ _ _ _ _ _ _ _ hr' _ =>
    exact Invariant.limitF_Legal (Invariant.applyEdge_Legal hr')
  | vuln => trivial
  | clean _ _ hf ih => exact Current.cleanRes_Legal ih hf
  | filt _ _ _ ih => exact ih
  | zpass => intro e hk; cases hk
  | zin => trivial
  | seed hs _ _ =>
    exact Invariant.limitF_Legal (fun e hk => absurd hk (hsk _ _ _ hs e))
  | zret _ _ _ _ _ _ hr' _ _ =>
    exact Invariant.limitF_Legal (Invariant.applyEdge_Legal hr')

/-- The backward hand-off has no star entry even if some backward premises are FLOW. -/
theorem demOfNA_backward_entry_shape
    (hsd : ∀ M n s, (M, n, s) ∈ seeds → ∃ t, s.mark = .conc t)
    (hsk : ∀ M n s, (M, n, s) ∈ seeds → NoStarK s.kind) :
    EntryShape (demOfNA Pb BR (pubR dem)) := by
  intro M d h
  obtain ⟨d0, h0, rfl⟩ := h
  refine ⟨?_, normFact_normMark _⟩
  change NoStarK d0.din.kind
  rcases h0 with rfl | ⟨g, hg, rfl⟩ | ⟨jb, gb, gb', _, _, hgb, hnc, ⟨dp, _, hp⟩, rfl⟩
  · intro e he; cases he
  · intro e he
    have hl := backward_legal hsk hg e he
    obtain ⟨t, ht⟩ := backward_zero_premise_concrete hsd hg
    exact Invariant.AbsMark.not_conc hl.1 t ht
  · obtain ⟨p, _, _, hc⟩ := restrictI_some hp
    apply restrictConcI_nonstar hc
    intro e he
    have hl := backward_legal hsk hgb e he
    exact hnc (crossB_of_star_concl hl.2 he hl.1)

end Backward

section BackwardPremises
variable {Pb : Program} {counted : Acc → Bool} {L : Nat}
  {dem : MethodId → DemandEdge → Prop} {sat : PFact → PFact → Bool}
  {restrict : PFact → AFact → DemandEdge → Option AFact} {rc : Recs}
  {sinks : List (MethodId × Node × PFact)} {roots : List MethodId}
  {seeds : List (MethodId × Node × PFact)} {zbind : Bool}

theorem backward_init_premEmpty (hd : EntryShape dem) {M : MethodId} {j : PFact}
    (h : BC Pb counted L dem emitW sat restrict rc sinks roots seeds zbind (.init M j)) :
    Reverse.PremEmpty j.kind := by
  cases h with
  | root => trivial
  | zin => trivial
  | initR ha hdem he =>
    have hs := hd _ _ hdem
    apply emitW_premEmpty hs.1 hs.2 _ he
    cases ha with
    | added _ _ _ hx =>
      intro t ht e hek
      exact Invariant.AbsMark.not_conc (Invariant.applyEdge_Legal hx e hek).1 t ht

/-- The same premise is retained along each edge, including the special zero paths. -/
def EmptyPrem : Obj → Prop
  | .init _ j => Reverse.PremEmpty j.kind
  | .edge _ j _ _ => Reverse.PremEmpty j.kind
  | _ => True

theorem backward_premEmpty (hd : EntryShape dem) {o : Obj}
    (h : BC Pb counted L dem emitW sat restrict rc sinks roots seeds zbind o) :
    EmptyPrem o := by
  induction h with
  | root => trivial
  | start _ ih => exact ih
  | step _ _ _ ih => exact ih
  | pass _ _ _ ih => exact ih
  | added => trivial
  | initR ha hd0 he _ => exact backward_init_premEmpty hd (BC.initR ha hd0 he)
  | ret _ _ _ _ _ _ _ _ _ _ _ _ ih _ _ => exact ih
  | retRec _ _ _ _ _ _ _ _ _ ih => exact ih
  | vuln => trivial
  | clean _ _ _ ih => exact ih
  | filt _ _ _ ih => exact ih
  | zpass => trivial
  | zin => trivial
  | seed => trivial
  | zret => trivial

end BackwardPremises

/-- Reversal exactness with the premise condition derived from the normalized entry shape. -/
theorem BW_rev_record_exact_shape {P : Program} {counted : Acc → Bool} {L : Nat}
    {dem : MethodId → DemandEdge → Prop} {rc : Recs} {roots : List MethodId}
    {seeds : List (MethodId × Node × PFact)} (hshape : EntryShape dem)
    (hmw : Exact.MarkWF P) (hup : Exact.FiltUp P) (hnz : Backward.NoZeroBack P)
    (hR : Reverse.RevStmts P) (hC : Reverse.RevCalls P)
    (hrecs : BExact.RecsExactNZ (Program.rev P) rc) {M : MethodId}
    {jb : PFact} {gb : AFact} {l l0 : Loc}
    (h : BW (Program.rev P) counted L dem rc roots seeds
      (.edge M jb ((Program.rev P).exit M) gb))
    (hi : jb ≠ zeroFact) (hn : gb.demand = false)
    (hd : den (revEdge jb gb.fact).1 (revEdge jb gb.fact).2 l l0) :
    Flow P M l (P.exit M) l0 :=
  BW_rev_record_exact hmw hup hnz hR hC hrecs h hi hn (backward_premEmpty hshape h) hd

theorem entryShape_PatTail {dem : MethodId → DemandEdge → Prop} (hd : EntryShape dem)
    {M : MethodId} {d : DemandEdge} (h : dem M d) : PatTailB d.din.kind = true := by
  have hk := (hd _ _ h).1
  cases he : d.din.kind with
  | exact => rfl
  | any => rfl
  | star e => exact (hk e he).elim

/-- The shape state records the actual forward closure and its publication rule. -/
structure ShapeState where
  run : Obj → Prop
  pub : Pub

def shapeNext (P Pb : Program) (counted : Acc → Bool) (lf lb : Nat) (rcf rcb : Recs)
    (sinks : List (MethodId × Node × PFact)) (roots : List MethodId)
    (seeds : List (MethodId × Node × PFact)) (prev : ShapeState) : ShapeState :=
  let db := handFA P prev.run prev.pub
  let br := BW Pb counted lb db rcb roots seeds
  let df := demOfNA Pb br (pubR db)
  ⟨FW P counted lf df rcf sinks roots, pubR df⟩

def shapeSeq (P Pb : Program) (counted : Acc → Bool) (lf lb : Nat → Nat)
    (rcf rcb : Nat → Recs) (sinks : List (MethodId × Node × PFact)) (roots : List MethodId)
    (seeds : Nat → List (MethodId × Node × PFact)) : Nat → ShapeState
  | 0 => ⟨RC P counted (lf 0) policy1 sinks roots, pubD⟩
  | k + 1 => shapeNext P Pb counted (lf (k + 1)) (lb k) (rcf (k + 1)) (rcb k)
      sinks roots (seeds k) (shapeSeq P Pb counted lf lb rcf rcb sinks roots seeds k)

/-- Every backward entry demand of the explicit alternating base sequence has a non-star tail.
    Records can be arbitrary here: their exactness is a separate obligation. -/
theorem shapeSeq_entry_shape (P Pb : Program) (counted : Acc → Bool) (lf lb : Nat → Nat)
    (rcf rcb : Nat → Recs) (sinks : List (MethodId × Node × PFact)) (roots : List MethodId)
    (seeds : Nat → List (MethodId × Node × PFact))
    (hsd : ∀ k M n s, (M, n, s) ∈ seeds k → ∃ t, s.mark = .conc t)
    (hsk : ∀ k M n s, (M, n, s) ∈ seeds k → NoStarK s.kind) (k : Nat) :
    EntryShape (handFA P (shapeSeq P Pb counted lf lb rcf rcb sinks roots seeds k).run
      (shapeSeq P Pb counted lf lb rcf rcb sinks roots seeds k).pub) := by
  cases k with
  | zero =>
    intro M d h
    obtain ⟨d0, h0, rfl⟩ := h
    exact ⟨(handF_run1_nonstar h0).1, normFact_normMark _⟩
  | succ k =>
    dsimp only [shapeSeq, shapeNext]
    apply handFA_forward_entry_shape
    exact demOfNA_backward_entry_shape (hsd k) (hsk k)

/-- L6's tail guard holds in every backward demand of the base sequence. -/
theorem shapeSeq_PatTail (P Pb : Program) (counted : Acc → Bool) (lf lb : Nat → Nat)
    (rcf rcb : Nat → Recs) (sinks : List (MethodId × Node × PFact)) (roots : List MethodId)
    (seeds : Nat → List (MethodId × Node × PFact))
    (hsd : ∀ k M n s, (M, n, s) ∈ seeds k → ∃ t, s.mark = .conc t)
    (hsk : ∀ k M n s, (M, n, s) ∈ seeds k → NoStarK s.kind) {k M d}
    (h : handFA P (shapeSeq P Pb counted lf lb rcf rcb sinks roots seeds k).run
      (shapeSeq P Pb counted lf lb rcf rcb sinks roots seeds k).pub M d) :
    PatTailB d.din.kind = true :=
  entryShape_PatTail (shapeSeq_entry_shape P Pb counted lf lb rcf rcb sinks roots seeds hsd hsk k) h

/-- Run 1 initializes backward demands with non-star normalized entry patterns. -/
theorem handFA_run1_entry_shape {P : Program} {counted : Acc → Bool} {L : Nat}
    {sinks : List (MethodId × Node × PFact)} {roots : List MethodId} :
    EntryShape (handFA P (RC P counted L policy1 sinks roots) pubD) := by
  intro M d h
  obtain ⟨d0, h0, rfl⟩ := h
  exact ⟨(handF_run1_nonstar h0).1, normFact_normMark _⟩

/-- There is no request object in a restricted forward closure. -/
theorem FC_no_req {P : Program} {counted : Acc → Bool} {L : Nat}
    {dem : MethodId → DemandEdge → Prop} {emit : PFact → PFact → Option PFact}
    {sat : PFact → PFact → Bool} {restrict : PFact → AFact → DemandEdge → Option AFact}
    {rc : Recs} {sinks : List (MethodId × Node × PFact)} {roots : List MethodId}
    {M : MethodId} {i : PFact} {t : Mark} :
    ¬FC P counted L dem emit sat restrict rc sinks roots (.req M i t) := by
  intro h
  cases h

/-- There is no request object in a restricted backward closure. -/
theorem BC_no_req {Pb : Program} {counted : Acc → Bool} {L : Nat}
    {dem : MethodId → DemandEdge → Prop} {emit : PFact → PFact → Option PFact}
    {sat : PFact → PFact → Bool} {restrict : PFact → AFact → DemandEdge → Option AFact}
    {rc : Recs} {sinks : List (MethodId × Node × PFact)} {roots : List MethodId}
    {seeds : List (MethodId × Node × PFact)} {zbind : Bool}
    {M : MethodId} {i : PFact} {t : Mark} :
    ¬BC Pb counted L dem emit sat restrict rc sinks roots seeds zbind (.req M i t) := by
  intro h
  cases h

/-- All current raw normal nonzero backward exit records are exact after
    reversal; eligibility filters and completeness can select any subset. -/
theorem BW_rev_records_exact_shape {P : Program} {counted : Acc → Bool} {L : Nat}
    {dem : MethodId → DemandEdge → Prop} {rc : Recs} {roots : List MethodId}
    {seeds : List (MethodId × Node × PFact)} (hshape : EntryShape dem)
    (hmw : Exact.MarkWF P) (hup : Exact.FiltUp P) (hnz : Backward.NoZeroBack P)
    (hR : RevStmts P) (hC : RevCalls P)
    (hrecs : BExact.RecsExactNZ (Program.rev P) rc) :
    RExact.RecsExact P (BExact.revRecs P
      (BW (Program.rev P) counted L dem rc roots seeds)) := by
  intro M j g hr _ l0 l hd
  obtain ⟨jb,gb,hg,hj,hn,he⟩ := hr
  cases he
  exact BW_rev_record_exact_shape hshape hmw hup hnz hR hC hrecs hg hj hn hd

#print axioms handFA_forward_entry_shape
#print axioms demOfNA_backward_entry_shape
#print axioms BW_rev_record_exact_shape
#print axioms shapeSeq_entry_shape
#print axioms shapeSeq_PatTail
end ApSpec.Current
