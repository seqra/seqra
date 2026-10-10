/-
  F75 FLOW representation invariants. The program's S7/S8 edge conditions,
  abstract emission shape, restriction shape and stored-record shape are
  explicit. Arbitrary AP programs and arbitrary record stores do not imply
  these properties. The later handoff proof derives demand shape for the
  historical generic selector. It does not derive F77 sequence preservation.
-/
import ApSpec.CurrentDemandClosure

namespace ApSpec.Current
open ApSpec ApSpec.Abs ApSpec.Handoff ApSpec.Reverse ApSpec.Kinds

/-- The F75 residual changes only marks, so it preserves the FLOW tail form. -/
theorem cleanRes_flow {cl : Cleaner} {c r : AFact} (hc : FlowF c)
    (hr : r ∈ (BaseCleaner.cleanRes cl c).facts) : FlowF r := by
  rcases BaseCleaner.result_cases hr with ⟨t, _, _, _, rfl⟩ | hold
  · exact ⟨Exact.addEx_abs t hc.1, hc.2⟩
  · exact Kinds.cleanRes_flow hc hold

section Run1
variable {P : Program} {counted : Acc → Bool} {L : Nat}
  {α : MethodId → PFact → PFact} {sinks : List (MethodId × Node × PFact)}
  {roots : List MethodId}
theorem RC_kinds (hmw : Exact.MarkWF P) (het : ExactTargetConc P)
    (hA : Invariant.AllEdges P Invariant.PremConc) (hB : Invariant.AllEdges P Invariant.NoUnivE)
    (hα : ∀ m a, InitK (α m a)) {o : Obj} (h : RC P counted L α sinks roots o) : KInv o := by
  induction h with
  | root => exact initK_conc (t := zeroMark) rfl
  | start _ ih => exact ⟨ih, fun ha => startFact_flow ih ha⟩
  | @step M i n f n' s f' _ hE hf ih =>
    refine ⟨ih.1, fun ha => transfer_flow (fun e he => ?_) (ih.2 ha) hf⟩
    exact ⟨hmw.stmt _ _ _ _ hE e he, hA.1 _ _ _ _ hE e he, hB.1 _ _ _ _ hE e he,
      het.1 _ _ _ _ hE e he⟩
  | reqStmt => trivial
  | pass _ _ _ ih => exact ih
  | added => trivial
  | initA => exact hα _ _
  | @ret M i n f n' c e1 a j g r e2 r' _ hE he1 ha1 _ _ _ hr he2 hr' ihF ihJ ihG =>
    refine ⟨ihF.1, fun ha => limitF_flow ?_⟩
    have hA1 := micro_flow (hmw.toC _ _ _ _ hE e1 he1) (hA.2.1 _ _ _ _ hE e1 he1)
      (hB.2.1 _ _ _ _ hE e1 he1) (het.2.1 _ _ _ _ hE e1 he1) (ihF.2 ha) ha1
    have hR := applySummary_flow hA1 ihJ.2 ihG.2 hr
    exact micro_flow (hmw.fromC _ _ _ _ hE e2 he2) (hA.2.2 _ _ _ _ hE e2 he2)
      (hB.2.2 _ _ _ _ hE e2 he2) (het.2.2 _ _ _ _ hE e2 he2) hR hr'
  | reqSink => trivial
  | answer => exact initK_conc answerInit_mark
  | reqUp => trivial
  | vuln => trivial
  | clean _ _ hf ih => exact ⟨ih.1, fun ha => Current.cleanRes_flow (ih.2 ha) hf⟩
  | reqClean => trivial
  | filt _ _ _ ih => exact ih

end Run1


/-- Stored summaries retain the representation shape of their native run. -/
def RecordKinds (rc : Recs) : Prop :=
  ∀ m j g, rc m (j,g) → InitK j ∧ (Exact.absB j.mark = true → FlowF g)

/-- Restriction must preserve a FLOW conclusion of an abstract premise. -/
def RestrictFlow (dem : MethodId → DemandEdge → Prop)
    (restrict : PFact → AFact → DemandEdge → Option AFact) : Prop :=
  ∀ m j g d g', dem m d → Exact.absB j.mark = true → FlowF g →
    restrict j g d = some g' → FlowF g'

section Forward
variable {P : Program} {counted : Acc → Bool} {L : Nat}
  {dem : MethodId → DemandEdge → Prop}
  {emit : PFact → PFact → Option PFact} {sat : PFact → PFact → Bool}
  {restrict : PFact → AFact → DemandEdge → Option AFact} {rc : Recs}
  {sinks : List (MethodId × Node × PFact)} {roots : List MethodId}

theorem FC_kinds (hmw : Exact.MarkWF P) (het : ExactTargetConc P)
    (hA : Invariant.AllEdges P Invariant.PremConc) (hB : Invariant.AllEdges P Invariant.NoUnivE)
    (hemit : ∀ m d a j, dem m d → emit d.din a = some j → InitK j)
    (hres : RestrictFlow dem restrict) (hrec : RecordKinds rc)
    {o : Obj} (h : FC P counted L dem emit sat restrict rc sinks roots o) : KInv o := by
  induction h with
  | root => exact initK_conc (t := zeroMark) rfl
  | start _ ih => exact ⟨ih, fun ha => startFact_flow ih ha⟩
  | @step M i n f n' s f' _ hE hf ih =>
    refine ⟨ih.1, fun ha => transfer_flow (fun e he => ?_) (ih.2 ha) hf⟩
    exact ⟨hmw.stmt _ _ _ _ hE e he, hA.1 _ _ _ _ hE e he, hB.1 _ _ _ _ hE e he,
      het.1 _ _ _ _ hE e he⟩
  | pass _ _ _ ih => exact ih
  | added => trivial
  | initR _ hd he => exact hemit _ _ _ _ hd he
  | @ret M i n f n' c e1 a j g d g' r e2 r' _ hE he1 ha1 _ _ hd hred _ hr he2 hr' ihF ihJ ihG =>
    refine ⟨ihF.1, fun ha => limitF_flow ?_⟩
    have hA1 := micro_flow (hmw.toC _ _ _ _ hE e1 he1) (hA.2.1 _ _ _ _ hE e1 he1)
      (hB.2.1 _ _ _ _ hE e1 he1) (het.2.1 _ _ _ _ hE e1 he1) (ihF.2 ha) ha1
    have hR := applySummary_flow hA1 ihJ.2
      (fun hj => hres _ _ _ _ _ hd hj (ihG.2 hj) hred) hr
    exact micro_flow (hmw.fromC _ _ _ _ hE e2 he2) (hA.2.2 _ _ _ _ hE e2 he2)
      (hB.2.2 _ _ _ _ hE e2 he2) (het.2.2 _ _ _ _ hE e2 he2) hR hr'
  | @retRec M i n f n' c e1 a j g r e2 r' _ hE he1 ha1 hrc _ hr he2 hr' ihF =>
    refine ⟨ihF.1, fun ha => limitF_flow ?_⟩
    have hA1 := micro_flow (hmw.toC _ _ _ _ hE e1 he1) (hA.2.1 _ _ _ _ hE e1 he1)
      (hB.2.1 _ _ _ _ hE e1 he1) (het.2.1 _ _ _ _ hE e1 he1) (ihF.2 ha) ha1
    have hs := hrec _ _ _ hrc
    have hR := applySummary_flow hA1 hs.1.2 hs.2 hr
    exact micro_flow (hmw.fromC _ _ _ _ hE e2 he2) (hA.2.2 _ _ _ _ hE e2 he2)
      (hB.2.2 _ _ _ _ hE e2 he2) (het.2.2 _ _ _ _ hE e2 he2) hR hr'
  | vuln => trivial
  | clean _ _ hf ih => exact ⟨ih.1, fun ha => Current.cleanRes_flow (ih.2 ha) hf⟩
  | filt _ _ _ ih => exact ih

end Forward

section Backward
variable {P : Program} {counted : Acc → Bool} {L : Nat}
  {dem : MethodId → DemandEdge → Prop}
  {emit : PFact → PFact → Option PFact} {sat : PFact → PFact → Bool}
  {restrict : PFact → AFact → DemandEdge → Option AFact} {rc : Recs}
  {sinks : List (MethodId × Node × PFact)} {roots : List MethodId}
  {seeds : List (MethodId × Node × PFact)} {zbind : Bool}
theorem BC_kinds (hmw : Exact.MarkWF P) (het : ExactTargetConc P)
    (hA : Invariant.AllEdges P Invariant.PremConc) (hB : Invariant.AllEdges P Invariant.NoUnivE)
    (hemit : ∀ m d a j, dem m d → emit d.din a = some j → InitK j)
    (hres : RestrictFlow dem restrict) (hrec : RecordKinds rc)
    {o : Obj} (h : BC P counted L dem emit sat restrict rc sinks roots seeds zbind o) : KInv o := by
  induction h with
  | root => exact initK_conc (t := zeroMark) rfl
  | start _ ih => exact ⟨ih, fun ha => startFact_flow ih ha⟩
  | @step M i n f n' s f' _ hE hf ih =>
    refine ⟨ih.1, fun ha => transfer_flow (fun e he => ?_) (ih.2 ha) hf⟩
    exact ⟨hmw.stmt _ _ _ _ hE e he, hA.1 _ _ _ _ hE e he, hB.1 _ _ _ _ hE e he,
      het.1 _ _ _ _ hE e he⟩
  | pass _ _ _ ih => exact ih
  | added => trivial
  | initR _ hd he => exact hemit _ _ _ _ hd he
  | @ret M i n f n' c e1 a j g d g' r e2 r' _ hE he1 ha1 _ _ hd hred _ hr he2 hr' ihF ihJ ihG =>
    refine ⟨ihF.1, fun ha => limitF_flow ?_⟩
    have hA1 := micro_flow (hmw.toC _ _ _ _ hE e1 he1) (hA.2.1 _ _ _ _ hE e1 he1)
      (hB.2.1 _ _ _ _ hE e1 he1) (het.2.1 _ _ _ _ hE e1 he1) (ihF.2 ha) ha1
    have hR := applySummary_flow hA1 ihJ.2
      (fun hj => hres _ _ _ _ _ hd hj (ihG.2 hj) hred) hr
    exact micro_flow (hmw.fromC _ _ _ _ hE e2 he2) (hA.2.2 _ _ _ _ hE e2 he2)
      (hB.2.2 _ _ _ _ hE e2 he2) (het.2.2 _ _ _ _ hE e2 he2) hR hr'
  | @retRec M i n f n' c e1 a j g r e2 r' _ hE he1 ha1 hrc _ hr he2 hr' ihF =>
    refine ⟨ihF.1, fun ha => limitF_flow ?_⟩
    have hA1 := micro_flow (hmw.toC _ _ _ _ hE e1 he1) (hA.2.1 _ _ _ _ hE e1 he1)
      (hB.2.1 _ _ _ _ hE e1 he1) (het.2.1 _ _ _ _ hE e1 he1) (ihF.2 ha) ha1
    have hs := hrec _ _ _ hrc
    have hR := applySummary_flow hA1 hs.1.2 hs.2 hr
    exact micro_flow (hmw.fromC _ _ _ _ hE e2 he2) (hA.2.2 _ _ _ _ hE e2 he2)
      (hB.2.2 _ _ _ _ hE e2 he2) (het.2.2 _ _ _ _ hE e2 he2) hR hr'
  | vuln => trivial
  | clean _ _ hf ih => exact ⟨ih.1, fun ha => Current.cleanRes_flow (ih.2 ha) hf⟩
  | filt _ _ _ ih => exact ih
  | zpass => exact ⟨initK_conc (t := zeroMark) rfl, fun ha => by cases ha⟩
  | zin => exact initK_conc (t := zeroMark) rfl
  | seed => exact ⟨initK_conc (t := zeroMark) rfl, fun ha => by cases ha⟩
  | zret => exact ⟨initK_conc (t := zeroMark) rfl, fun ha => by cases ha⟩

end Backward

/-- The entry and the paired exit conditions needed by the tree representation. -/
def TreeDemandShape (dem : MethodId → DemandEdge → Prop) : Prop :=
  EntryShape dem ∧
  (∀ m d, dem m d → Exact.absB d.din.mark = true → d.din.kind = .any) ∧
  (∀ m d, dem m d → CurrentDemand.PairedExactExit d)

/-- Under the tree entry shape an abstract premise has the empty star tail. -/
theorem emitW_tree_init {dem : MethodId → DemandEdge → Prop}
    (hs : TreeDemandShape dem) {m : MethodId} {d : DemandEdge} {a j : PFact}
    (hd : dem m d) (he : emitW d.din a = some j) : InitK j := by
  cases hm : d.din.mark with
  | star =>
    have hj := (emitW_star hm he).1
    subst j
    have hk := hs.2.1 m d hd (by rw [hm]; rfl)
    exact ⟨Or.inl rfl, fun _ => by rw [flowForm, hk]; exact Or.inr ⟨[],rfl⟩⟩
  | starEx xs =>
    have hn := (hs.1 m d hd).2
    rw [hm] at hn
    exact hn.elim
  | conc t =>
    rcases AbsHandoff.emitW_cross_or_conc (hs.1 m d hd).1 (hs.1 m d hd).2 he with hcross | hc
    · rw [emitW_nonstar (by rw [hm]; intro h; cases h)] at he
      have hmatch := (RCore.emitM_cases he).2.1
      have hj := RCore.emitM_mark he
      cases ham : a.mark with
      | star => rw [hm, ham] at hmatch; cases hmatch
      | starEx xs => rw [hm, ham] at hmatch; cases hmatch
      | conc u => exact initK_conc (hj.trans ham)
    · obtain ⟨u, hu⟩ := hc
      exact initK_conc hu

private theorem restrictConcI_flow_nonexact {g g' : AFact} {p : PFact}
    (hf : FlowF g) (hp : p.kind ≠ .exact) (hr : restrictConcI g p = some g') : FlowF g' := by
  obtain ⟨_, ⟨_, he⟩ | ⟨_, _, _, _, he⟩ | ⟨_, _, _, _, he⟩ | ⟨_, _, _, _, _, _, he⟩⟩ :=
    restrictConcI_cases hr
  · rw [he]
    refine ⟨hf.1, ?_⟩
    rcases hf.2 with hg | ⟨xs, hg⟩
    · cases hk : p.kind with
      | exact => exact (hp hk).elim
      | any => exact Or.inl (by rw [hg]; rfl)
      | star e => exact Or.inl (by rw [hg]; rfl)
    · exact Or.inr ⟨xs, by rw [hg]; cases p.kind <;> rfl⟩
  · rw [he]; exact hf
  · rw [he]
    refine ⟨hf.1, Or.inl ?_⟩
    cases hk : p.kind with
    | exact => exact (hp hk).elim
    | any => rfl
    | star e => rfl
  · rw [he]; exact hf

/-- The paired exact-exit guard makes the actual base restriction FLOW-safe. -/
theorem restrictI_tree_flow {dem : MethodId → DemandEdge → Prop}
    (hs : TreeDemandShape dem) : RestrictFlow dem restrictI := by
  intro m j g d g' hd hj hg hr
  obtain ⟨p, hp, hin, _, hc⟩ := restrictI_someM hr
  apply restrictConcI_flow_nonexact hg _ hc
  intro hex
  obtain ⟨t, ht⟩ := hs.2.2 m d hd p hp hex
  have hcj := CurrentDemand.inside_concrete_mark hin ht
  rw [hcj] at hj
  cases hj

/-- A concrete premise has a concrete conclusion in run 1. -/
theorem RC_concrete_premise {P : Program} {counted : Acc → Bool} {L : Nat}
    {α : MethodId → PFact → PFact} {sinks : List (MethodId × Node × PFact)}
    {roots : List MethodId} {o : Obj} (h : RC P counted L α sinks roots o) :
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
  | initA => trivial
  | @ret M i n f n' c e1 a j g r e2 r' _ _ _ ha _ _ _ hr _ hr' ihF _ _ =>
    intro hc
    obtain ⟨t, ht⟩ := ihF hc
    obtain ⟨t1, h1⟩ := applyEdge_mark_conc ht ha
    obtain ⟨t2, h2⟩ := applySummary_mark_conc h1 hr
    obtain ⟨t3, h3⟩ := applyEdge_mark_conc h2 hr'
    exact ⟨t3, by rw [limitF_mark, h3]⟩
  | @clean M i n f n' cl f' _ _ hf ih =>
    intro hc
    obtain ⟨t, ht⟩ := ih hc
    exact Current.cleanRes_mark_conc ht hf
  | filt _ _ _ ih => exact ih
  | reqStmt => trivial
  | reqSink => trivial
  | answer => trivial
  | reqUp => trivial
  | vuln => trivial
  | reqClean => trivial

private theorem abstract_output_flow {j : PFact} {g : AFact}
    (hk : InitK j ∧ (Exact.absB j.mark = true → FlowF g))
    (hc : (∃ t, j.mark = .conc t) → ∃ t, g.fact.mark = .conc t)
    (hg : Exact.absB g.fact.mark = true) : FlowF g := by
  rcases hk.1.1 with hs | hconc
  · apply hk.2
    rw [hs]; rfl
  · obtain ⟨t, ht⟩ := hc hconc
    rw [ht] at hg
    cases hg

private theorem init_exact_concrete {j : PFact} (hk : InitK j) (hj : j.kind = .exact) :
    ∃ t, j.mark = .conc t := by
  rcases hk.1 with hm | hc
  · have hf := hk.2 (by rw [hm]; rfl)
    exact ((flowKind_ne hf).1 hj).elim
  · exact hc

private theorem normFact_abs {f : PFact} :
    Exact.absB (normFact f).mark = Exact.absB f.mark := by
  cases h : f.mark <;> simp [normFact, markNorm, Exact.absB, h]

private theorem normFact_conc {f : PFact} {t : Mark} (h : f.mark = .conc t) :
    (normFact f).mark = .conc t := by rw [normFact, h]; rfl

/-- Shape of an actual published edge; no restriction result is assumed raw. -/
def PublishedKinds (P : Program) (R : Obj → Prop) (pub : Pub) : Prop :=
  ∀ M j g g', R (.edge M j (P.exit M) g) → pub M j g g' →
    InitK j ∧ (Exact.absB g'.fact.mark = true → FlowF g') ∧
    ((∃ t, j.mark = .conc t) → ∃ t, g'.fact.mark = .conc t)

private theorem premise_abstract_of_output {j : PFact} {g : AFact} (hk : InitK j)
    (hc : (∃ t, j.mark = .conc t) → ∃ t, g.fact.mark = .conc t)
    (hg : Exact.absB g.fact.mark = true) : Exact.absB j.mark = true := by
  rcases hk.1 with hs | hconc
  · rw [hs]; rfl
  · obtain ⟨t, ht⟩ := hc hconc
    rw [ht] at hg
    cases hg

/-- Shape of run-1 publication follows from the native closure shape. -/
theorem RC_published_kinds {P : Program} {counted : Acc → Bool} {L : Nat}
    {sinks : List (MethodId × Node × PFact)} {roots : List MethodId}
    (hmw : Exact.MarkWF P) (het : ExactTargetConc P)
    (hA : Invariant.AllEdges P Invariant.PremConc) (hB : Invariant.AllEdges P Invariant.NoUnivE) :
    PublishedKinds P (RC P counted L policy1 sinks roots) pubD := by
  intro M j g g' hg hp
  have he : g' = g := hp
  subst g'
  have hk := RC_kinds hmw het hA hB policy1_initK hg
  have hc := RC_concrete_premise hg
  exact ⟨hk.1, abstract_output_flow hk hc, hc⟩

/-- Native closure and actual paired reduction imply restricted publication shape. -/
theorem pubR_kinds {P : Program} {R : Obj → Prop} {dem : MethodId → DemandEdge → Prop}
    (hk : ∀ M j n g, R (.edge M j n g) → KInv (.edge M j n g))
    (hc : ∀ M j n g, R (.edge M j n g) → ReviewDemand.ConcEdge (.edge M j n g))
    (hr : RestrictFlow dem restrictI) : PublishedKinds P R (pubR dem) := by
  intro M j g g' hg hp
  obtain ⟨d, hd, hred⟩ := hp
  obtain ⟨p, _, _, hredC⟩ := restrictI_some hred
  have hm := restrictConcI_mark hredC
  have hkg := hk M j (P.exit M) g hg
  have hcg := hc M j (P.exit M) g hg
  refine ⟨hkg.1, ?_, ?_⟩
  · intro hab
    have habg : Exact.absB g.fact.mark = true := by rw [← hm]; exact hab
    have hj := premise_abstract_of_output hkg.1 hcg habg
    exact hr M j g d g' hd hj (hkg.2 hj) hred
  · intro hj
    obtain ⟨t, ht⟩ := hcg hj
    exact ⟨t, hm.trans ht⟩

/-- A handoff from actual published edges has the paired tree demand shape. -/
theorem handFA_tree_shape {P : Program} {R : Obj → Prop} {pub : Pub}
    (hs : EntryShape (handFA P R pub)) (hk : PublishedKinds P R pub) :
    TreeDemandShape (handFA P R pub) := by
  refine ⟨hs, ?_, ?_⟩
  · intro M d h hab
    obtain ⟨d0, ⟨j,g,g',_,hg,_,hp,rfl⟩,rfl⟩ := h
    have hkg := hk M j g g' hg hp
    have ha : Exact.absB g'.fact.mark = true := by
      rw [← normFact_abs (f := g'.fact)]
      exact hab
    have hf := hkg.2.1 ha
    have hn := (hs M (normDem ⟨g'.fact,some j⟩)
      ⟨⟨g'.fact,some j⟩,⟨j,g,g',by assumption,hg,by assumption,hp,rfl⟩,rfl⟩).1
    change g'.fact.kind = .any
    rcases hf.2 with he | ⟨xs,he⟩
    · exact he
    · exact (hn (.set xs) he).elim
  · intro M d h p hp hex
    obtain ⟨d0,⟨j,g,g',_,hg,_,hpub,rfl⟩,rfl⟩ := h
    have hjp : normFact j = p := Option.some.inj hp
    have hj : j.kind = .exact := by
      have he : (normFact j).kind = .exact := hjp.symm ▸ hex
      exact he
    obtain ⟨t,ht⟩ := init_exact_concrete (hk M j g g' hg hpub).1 hj
    obtain ⟨u,hu⟩ := (hk M j g g' hg hpub).2.2 ⟨t,ht⟩
    exact ⟨u,normFact_conc hu⟩

/-- Run 1's demand shape also excludes abstract exact entry/exit pairing. -/
theorem handFA_run1_tree_shape {P : Program} {counted : Acc → Bool} {L : Nat}
    {sinks : List (MethodId × Node × PFact)} {roots : List MethodId}
    (hmw : Exact.MarkWF P) (het : ExactTargetConc P)
    (hA : Invariant.AllEdges P Invariant.PremConc) (hB : Invariant.AllEdges P Invariant.NoUnivE) :
    TreeDemandShape (handFA P (RC P counted L policy1 sinks roots) pubD) :=
  handFA_tree_shape handFA_run1_entry_shape (RC_published_kinds hmw het hA hB)

/-- Actual restricted forward publication keeps the paired demand shape. -/
theorem handFA_forward_tree_shape {P : Program} {counted : Acc → Bool} {L : Nat}
    {dem : MethodId → DemandEdge → Prop} {rc : Recs}
    {sinks : List (MethodId × Node × PFact)} {roots : List MethodId}
    (hs : TreeDemandShape dem) (hmw : Exact.MarkWF P) (het : ExactTargetConc P)
    (hA : Invariant.AllEdges P Invariant.PremConc) (hB : Invariant.AllEdges P Invariant.NoUnivE)
    (hrec : RecordKinds rc) :
    TreeDemandShape (handFA P (FW P counted L dem rc sinks roots) (pubR dem)) := by
  apply handFA_tree_shape (handFA_forward_entry_shape hs.1)
  exact pubR_kinds
    (fun _ _ _ _ hg => FC_kinds hmw het hA hB
      (fun _ _ _ _ hd he => emitW_tree_init hs hd he) (restrictI_tree_flow hs) hrec hg)
    (fun _ _ _ _ hg => forward_concrete_premise hg) (restrictI_tree_flow hs)

/-- Zero-premise seed paths and native publication give the backward paired shape. -/
theorem demOfNA_tree_shape {Pb : Program} {R : Obj → Prop} {pub : Pub}
    (hs : EntryShape (demOfNA Pb R pub)) (hk : PublishedKinds Pb R pub)
    (hz : ∀ M n g, R (.edge M zeroFact n g) → ∃ t, g.fact.mark = .conc t) :
    TreeDemandShape (demOfNA Pb R pub) := by
  refine ⟨hs, ?_, ?_⟩
  · intro M d h hab
    obtain ⟨d0,h0,rfl⟩ := h
    rcases h0 with rfl | ⟨g,hg,rfl⟩ | ⟨j,g,g',hj,hjz,hg,hn,hp,rfl⟩
    · cases hab
    · obtain ⟨t,ht⟩ := hz M (Pb.exit M) g hg
      change Exact.absB (normFact g.fact).mark = true at hab
      rw [normFact_conc ht] at hab
      cases hab
    · have hkg := hk M j g g' hg hp
      have ha : Exact.absB g'.fact.mark = true := by
        rw [← normFact_abs (f := g'.fact)]
        exact hab
      have hf := hkg.2.1 ha
      have hns := (hs M (normDem ⟨g'.fact,some j⟩)
        ⟨⟨g'.fact,some j⟩,Or.inr (Or.inr ⟨j,g,g',hj,hjz,hg,hn,hp,rfl⟩),rfl⟩).1
      change g'.fact.kind = .any
      rcases hf.2 with he | ⟨xs,he⟩
      · exact he
      · exact (hns (.set xs) he).elim
  · intro M d h p hp hex
    obtain ⟨d0,h0,rfl⟩ := h
    rcases h0 with rfl | ⟨g,hg,rfl⟩ | ⟨j,g,g',_,_,hg,_,hpub,rfl⟩
    · cases hp
    · cases hp
    · have hjp : normFact j = p := Option.some.inj hp
      have hj : j.kind = .exact := by
        have he : (normFact j).kind = .exact := hjp.symm ▸ hex
        exact he
      obtain ⟨t,ht⟩ := init_exact_concrete (hk M j g g' hg hpub).1 hj
      obtain ⟨u,hu⟩ := (hk M j g g' hg hpub).2.2 ⟨t,ht⟩
      exact ⟨u,normFact_conc hu⟩

/-- Actual restricted backward publication keeps the paired demand shape. -/
theorem demOfNA_backward_tree_shape {Pb : Program} {counted : Acc → Bool} {L : Nat}
    {dem : MethodId → DemandEdge → Prop} {rc : Recs} {roots : List MethodId}
    {seeds : List (MethodId × Node × PFact)}
    (hs : TreeDemandShape dem) (hmw : Exact.MarkWF Pb) (het : ExactTargetConc Pb)
    (hA : Invariant.AllEdges Pb Invariant.PremConc) (hB : Invariant.AllEdges Pb Invariant.NoUnivE)
    (hrec : RecordKinds rc)
    (hsd : ∀ M n s, (M,n,s) ∈ seeds → ∃ t,s.mark = .conc t)
    (hsk : ∀ M n s, (M,n,s) ∈ seeds → NoStarK s.kind) :
    TreeDemandShape (demOfNA Pb (BW Pb counted L dem rc roots seeds) (pubR dem)) := by
  apply demOfNA_tree_shape (demOfNA_backward_entry_shape hsd hsk)
  · exact pubR_kinds
      (fun _ _ _ _ hg => BC_kinds hmw het hA hB
        (fun _ _ _ _ hd he => emitW_tree_init hs hd he) (restrictI_tree_flow hs) hrec hg)
      (fun _ _ _ _ hg => backward_concrete_premise hsd hg) (restrictI_tree_flow hs)
  · exact fun _ _ _ hg => backward_zero_premise_concrete hsd hg

/-- The complete paired tree-demand guard is preserved by the explicit base
    sequence, under S7/S8 for both programs and native record shape. This is a
    representation theorem; full witness coverage is a separate obligation. -/
theorem shapeSeq_tree_shape (P Pb : Program) (counted : Acc → Bool) (lf lb : Nat → Nat)
    (rcf rcb : Nat → Recs) (sinks : List (MethodId × Node × PFact)) (roots : List MethodId)
    (seeds : Nat → List (MethodId × Node × PFact))
    (hmw : Exact.MarkWF P) (het : ExactTargetConc P)
    (hA : Invariant.AllEdges P Invariant.PremConc) (hB : Invariant.AllEdges P Invariant.NoUnivE)
    (hmwB : Exact.MarkWF Pb) (hetB : ExactTargetConc Pb)
    (hAB : Invariant.AllEdges Pb Invariant.PremConc) (hBB : Invariant.AllEdges Pb Invariant.NoUnivE)
    (hrf : ∀ k, RecordKinds (rcf k)) (hrb : ∀ k, RecordKinds (rcb k))
    (hsd : ∀ k M n s, (M,n,s) ∈ seeds k → ∃ t,s.mark = .conc t)
    (hsk : ∀ k M n s, (M,n,s) ∈ seeds k → NoStarK s.kind) (k : Nat) :
    TreeDemandShape (handFA P (shapeSeq P Pb counted lf lb rcf rcb sinks roots seeds k).run
      (shapeSeq P Pb counted lf lb rcf rcb sinks roots seeds k).pub) := by
  induction k with
  | zero => exact handFA_run1_tree_shape hmw het hA hB
  | succ k ih =>
    dsimp only [shapeSeq,shapeNext]
    apply handFA_forward_tree_shape _ hmw het hA hB (hrf (k+1))
    exact demOfNA_backward_tree_shape ih hmwB hetB hAB hBB (hrb k) (hsd k) (hsk k)

#print axioms RC_kinds
#print axioms FC_kinds
#print axioms BC_kinds
#print axioms handFA_run1_tree_shape
#print axioms handFA_forward_tree_shape
#print axioms demOfNA_backward_tree_shape
#print axioms shapeSeq_tree_shape
end ApSpec.Current

namespace ApSpec.Current
open ApSpec ApSpec.Abs ApSpec.Handoff ApSpec.Reverse ApSpec.Kinds

/-- Reversal preserves the record representation shape. A crossable reversed
    premise is required; concrete native premises must keep concrete outputs. -/
theorem revRec_kinds {j : PFact} {g : AFact}
    (hk : InitK j ∧ (Exact.absB j.mark = true → FlowF g))
    (hc : (∃ t, j.mark = .conc t) → ∃ t, g.fact.mark = .conc t)
    (hcross : CrossK (revRec (j,g)).1.kind) :
    InitK (revRec (j,g)).1 ∧
      (Exact.absB (revRec (j,g)).1.mark = true → FlowF (revRec (j,g)).2) := by
  rcases hk.1.1 with hj | ⟨t,hj⟩
  · have ha : Exact.absB j.mark = true := by rw [hj]; rfl
    have hjk := hk.1.2 ha
    have hgk := hk.2 ha
    cases hgm : g.fact.mark with
    | conc t =>
      have hga := hgk.1
      rw [hgm] at hga
      cases hga
    | star =>
      rcases hjk with hja | ⟨js,hjs⟩ <;>
        rcases hgk.2 with hga | ⟨gs,hgs⟩
      · simp [revRec,revEdge,revKinds,hj,hgm,hja,hga,CrossK] at hcross
      · simp [revRec,revEdge,revKinds,hj,hgm,hja,hgs,InitK,FlowF,FlowKind,Exact.absB,Excl.empty]
      · simp [revRec,revEdge,revKinds,hj,hgm,hjs,hga,CrossK] at hcross
      · simp [revRec,revEdge,revKinds,hj,hgm,hjs,hgs,InitK,FlowF,FlowKind,Exact.absB,
          Excl.union,Excl.empty]
    | starEx xs =>
      rcases hjk with hja | ⟨js,hjs⟩ <;>
        rcases hgk.2 with hga | ⟨gs,hgs⟩
      · simp [revRec,revEdge,revKinds,hj,hgm,hja,hga,CrossK] at hcross
      · simp [revRec,revEdge,revKinds,hj,hgm,hja,hgs,InitK,FlowF,FlowKind,Exact.absB,Excl.empty]
      · simp [revRec,revEdge,revKinds,hj,hgm,hjs,hga,CrossK] at hcross
      · simp [revRec,revEdge,revKinds,hj,hgm,hjs,hgs,InitK,FlowF,FlowKind,Exact.absB,
          Excl.union,Excl.empty]
  · obtain ⟨u,hu⟩ := hc ⟨t,hj⟩
    have hn : (revRec (j,g)).1.mark = .conc u := by simp [revRec,revEdge,hu]
    exact ⟨initK_conc hn, fun hab => (not_abs_of_conc hn hab).elim⟩

/-- Native raw exit edges inherit the same shape before summary reduction. -/
theorem native_record_kinds {P : Program} {R : Obj → Prop}
    (h : ∀ M j n g, R (.edge M j n g) → KInv (.edge M j n g)) :
    RecordKinds (RExact.exitRecs P R) := fun M j g hg => h M j (P.exit M) g hg

/-- Stored forward records and their actual reversed reads keep the tree shape. -/
theorem reversed_record_kinds {P : Program} {R : Obj → Prop}
    (hk : ∀ M j n g, R (.edge M j n g) → KInv (.edge M j n g))
    (hc : ∀ M j n g, R (.edge M j n g) → ReviewDemand.ConcEdge (.edge M j n g)) :
    RecordKinds (fun M rec => ∃ j g, R (.edge M j (P.exit M) g) ∧
      CrossK (revRec (j,g)).1.kind ∧ rec = revRec (j,g)) := by
  intro M j g hr
  obtain ⟨j0,g0,hg,hcross,he⟩ := hr
  have hh := revRec_kinds (hk M j0 (P.exit M) g0 hg) (hc M j0 (P.exit M) g0 hg) hcross
  cases he
  exact hh

/-- Taking subsets, retaining old records, and inserting native records do
    not change the record shape. The proof holds for any eligibility filter. -/
theorem record_kinds_union {old fresh : Recs} (ho : RecordKinds old) (hf : RecordKinds fresh) :
    RecordKinds (fun M rec => old M rec ∨ fresh M rec) := by
  intro M j g hr
  rcases hr with hr | hr
  · exact ho M j g hr
  · exact hf M j g hr

theorem record_kinds_subset {rc sub : Recs} (hr : RecordKinds rc)
    (hs : ∀ M rec,sub M rec → rc M rec) : RecordKinds sub :=
  fun M j g h => hr M j g (hs M (j,g) h)

#print axioms revRec_kinds
#print axioms reversed_record_kinds
end ApSpec.Current

namespace ApSpec.Current
open ApSpec ApSpec.Abs ApSpec.Handoff ApSpec.Reverse ApSpec.Kinds

private def shapePremise : PFact := ⟨3,[],.star .empty,.star⟩
private def shapeConclusion : AFact := ⟨⟨4,[],.star (.set [4]),.starEx [1]⟩,false⟩
private def shapeAdded : AFact := ⟨⟨4,[],.exact,.conc 1⟩,false⟩

/-- Executable native-to-reversed record certificate. Its abstract mark
    exclusion survives reversal and rejects the selected concrete mark. -/
def recordShapeWitness :
    {rec : PFact × AFact // rec = revRec (shapePremise,shapeConclusion) ∧
      InitK rec.1 ∧ (Exact.absB rec.1.mark = true → FlowF rec.2) ∧
      (applySummary shapeAdded rec.1 rec.2).facts = []} := by
  have hk : InitK shapePremise ∧
      (Exact.absB shapePremise.mark = true → FlowF shapeConclusion) :=
    ⟨⟨Or.inl rfl, fun _ => Or.inr ⟨[],rfl⟩⟩,fun _ => ⟨rfl,Or.inr ⟨[4],rfl⟩⟩⟩
  have hc : (∃ t,shapePremise.mark = .conc t) → ∃ t,shapeConclusion.fact.mark = .conc t := by
    intro ⟨t,ht⟩
    cases ht
  have hs := revRec_kinds hk hc rfl
  exact ⟨revRec (shapePremise,shapeConclusion),rfl,hs.1,hs.2,by decide⟩

#eval recordShapeWitness.val
#print axioms recordShapeWitness
end ApSpec.Current

namespace ApSpec.Current
open ApSpec ApSpec.Handoff ApSpec.Kinds

/-- General executable record transformer with its preserved shape proof. -/
def reverseRecordKindWitness (j : PFact) (g : AFact)
    (hk : InitK j ∧ (Exact.absB j.mark = true → FlowF g))
    (hc : (∃ t,j.mark = .conc t) → ∃ t,g.fact.mark = .conc t)
    (hcross : CrossK (revRec (j,g)).1.kind) :
    {rec : PFact × AFact // rec = revRec (j,g) ∧ InitK rec.1 ∧
      (Exact.absB rec.1.mark = true → FlowF rec.2)} :=
  let hs := revRec_kinds hk hc hcross
  ⟨revRec (j,g),rfl,hs.1,hs.2⟩

#print axioms reverseRecordKindWitness
end ApSpec.Current
