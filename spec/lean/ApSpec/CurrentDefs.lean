/-
  F75 closures. RC answers requests in run 1. FC and BC omit all request
  rules. Each closure uses the same base-relevant selected-mark cleaner.
  Parameters expose the program, limits, records, demand policy and seeds.

  `.added` below denotes semantic/ghost supplier reachability, NOT membership
  in a required runtime callee input store. Removing AddedFactStore leaves these
  mathematical objects available for induction. AddedFactsRemoval proves a local
  finite replay refinement, conditional on exact retained caller-N2 effects; it
  does not by itself reprove this whole closure, ND confirmation or scheduling.
-/
import ApSpec.BaseCleaner
import ApSpec.AbsDefs

namespace ApSpec.Current
open ApSpec ApSpec.Reverse ApSpec.Handoff ApSpec.Abs

section Run1
variable (P : Program) (counted : Acc → Bool) (L : Nat)
  (α : MethodId → PFact → PFact) (sinks : List (MethodId × Node × PFact))
  (roots : List MethodId)
inductive RC : Obj → Prop where
  | root {M} : M ∈ roots → RC (.init M zeroFact)
  | start {M i} : RC (.init M i) → RC (.edge M i (P.entry M) (startFact i))
  | step {M i n f n' s f'} :
      RC (.edge M i n f) → (M, n, Instr.stmt s, n') ∈ P.edges →
      f' ∈ (transfer counted L s f).facts → RC (.edge M i n' f')
  | reqStmt {M i n f n' s t} :
      RC (.edge M i n f) → (M, n, Instr.stmt s, n') ∈ P.edges →
      t ∈ (transfer counted L s f).reqs → RC (.req M i t)
  | pass {M i n f n' c} :
      RC (.edge M i n f) → (M, n, Instr.call c, n') ∈ P.edges →
      memB f.fact.base c.touched = false → RC (.edge M i n' f)
  /-- Ghost reachability of an actual call input; no runtime retention obligation. -/
  | added {M i n f n' c e a} :
      RC (.edge M i n f) → (M, n, Instr.call c, n') ∈ P.edges →
      e ∈ c.toCallee → a ∈ (applyEdge f e.1 e.2).facts →
      RC (.added c.callee a.fact)
  | initA {m a} : RC (.added m a) → RC (.init m (α m a))
  -- The caller subscribes to EVERY summary edge whose premise its fact satisfies:
  -- the initial fact selected by the abstraction, the answers of requests, and any
  -- other initial fact of the callee that is applicable to the added fact.
  | ret {M i n f n' c e1 a j g r e2 r'} :
      RC (.edge M i n f) → (M, n, Instr.call c, n') ∈ P.edges →
      e1 ∈ c.toCallee → a ∈ (applyEdge f e1.1 e1.2).facts →
      RC (.init c.callee j) →
      applicable j a.fact = true →
      RC (.edge c.callee j (P.exit c.callee) g) →
      r ∈ (applySummary a j g).facts →
      e2 ∈ c.fromCallee → r' ∈ (applyEdge r e2.1 e2.2).facts →
      RC (.edge M i n' (limitF counted L r'))
  | reqSink {M i n f s t} :
      RC (.edge M i n f) → (M, n, s) ∈ sinks → check i f s = .request t →
      RC (.req M i t)
  | answer {M i t a} :
      RC (.req M i t) → RC (.added M a) → a.mark = .conc t →
      overlapB a i = true → RC (.init M (answerInit i a t))
  | reqUp {m j t M ic n f n' c e a} :
      RC (.req m j t) → RC (.edge M ic n f) → (M, n, Instr.call c, n') ∈ P.edges →
      c.callee = m → e ∈ c.toCallee → a ∈ (applyEdge f e.1 e.2).facts →
      climbsB a.fact.mark t = true → overlapB a.fact j = true → RC (.req M ic t)
  | vuln {M i n f s} :
      RC (.edge M i n f) → (M, n, s) ∈ sinks → check i f s = .triggered →
      RC (.vuln M n s f.demand)
  -- a cleaner (spec §4.7)
  | clean {M i n f n' cl f'} :
      RC (.edge M i n f) → (M, n, Instr.clean cl, n') ∈ P.edges →
      f' ∈ (BaseCleaner.cleanRes cl f).facts → RC (.edge M i n' f')
  | reqClean {M i n f n' cl t} :
      RC (.edge M i n f) → (M, n, Instr.clean cl, n') ∈ P.edges →
      t ∈ (BaseCleaner.cleanRes cl f).reqs → RC (.req M i t)
  -- a type filter (spec §4.8): a fact whose path may exist passes; another one is dropped
  | filt {M i n f n' b may} :
      RC (.edge M i n f) → (M, n, Instr.filt b may, n') ∈ P.edges →
      (f.fact.base = b → may f.fact.path = true) → RC (.edge M i n' f)

end Run1
section Forward
variable (P : Program) (counted : Acc → Bool) (L : Nat)
  (demand : MethodId → DemandEdge → Prop)
  (emit : PFact → PFact → Option PFact)
  (sat : PFact → PFact → Bool)
  (restrict : PFact → AFact → DemandEdge → Option AFact)
  (recs : MethodId → (PFact × AFact) → Prop)
  (sinks : List (MethodId × Node × PFact)) (roots : List MethodId)
inductive FC : Obj → Prop where
  | root {M} : M ∈ roots → FC (.init M zeroFact)
  | start {M i} : FC (.init M i) → FC (.edge M i (P.entry M) (startFact i))
  | step {M i n f n' s f'} :
      FC (.edge M i n f) → (M, n, Instr.stmt s, n') ∈ P.edges →
      f' ∈ (transfer counted L s f).facts → FC (.edge M i n' f')
  | pass {M i n f n' c} :
      FC (.edge M i n f) → (M, n, Instr.call c, n') ∈ P.edges →
      memB f.fact.base c.touched = false → FC (.edge M i n' f)
  /-- Ghost reachability of an actual call input; no runtime retention obligation. -/
  | added {M i n f n' c e a} :
      FC (.edge M i n f) → (M, n, Instr.call c, n') ∈ P.edges →
      e ∈ c.toCallee → a ∈ (applyEdge f e.1 e.2).facts →
      FC (.added c.callee a.fact)
  | initR {m a d j} :
      FC (.added m a) → demand m d → emit d.din a = some j → FC (.init m j)
  | ret {M i n f n' c e1 a j g d g' r e2 r'} :
      FC (.edge M i n f) → (M, n, Instr.call c, n') ∈ P.edges →
      e1 ∈ c.toCallee → a ∈ (applyEdge f e1.1 e1.2).facts →
      FC (.init c.callee j) →
      FC (.edge c.callee j (P.exit c.callee) g) →
      demand c.callee d → restrict j g d = some g' →
      sat j a.fact = true →
      r ∈ (applySummary a j g').facts →
      e2 ∈ c.fromCallee → r' ∈ (applyEdge r e2.1 e2.2).facts →
      FC (.edge M i n' (limitF counted L r'))
  | retRec {M i n f n' c e1 a j g r e2 r'} :
      FC (.edge M i n f) → (M, n, Instr.call c, n') ∈ P.edges →
      e1 ∈ c.toCallee → a ∈ (applyEdge f e1.1 e1.2).facts →
      recs c.callee (j, g) → (sat j a.fact = true ∨ applicable j a.fact = true) →
      r ∈ (applySummary a j g).facts →
      e2 ∈ c.fromCallee → r' ∈ (applyEdge r e2.1 e2.2).facts →
      FC (.edge M i n' (limitF counted L r'))
  | vuln {M i n f s} :
      FC (.edge M i n f) → (M, n, s) ∈ sinks → check i f s = .triggered →
      FC (.vuln M n s f.demand)
  | clean {M i n f n' cl f'} :
      FC (.edge M i n f) → (M, n, Instr.clean cl, n') ∈ P.edges →
      f' ∈ (BaseCleaner.cleanRes cl f).facts → FC (.edge M i n' f')
  | filt {M i n f n' b may} :
      FC (.edge M i n f) → (M, n, Instr.filt b may, n') ∈ P.edges →
      (f.fact.base = b → may f.fact.path = true) → FC (.edge M i n' f)

end Forward
section Backward
variable (Pb : Program) (counted : Acc → Bool) (L : Nat)
  (demand : MethodId → DemandEdge → Prop)
  (emit : PFact → PFact → Option PFact)
  (sat : PFact → PFact → Bool)
  (restrict : PFact → AFact → DemandEdge → Option AFact)
  (recs : MethodId → (PFact × AFact) → Prop)
  (sinks : List (MethodId × Node × PFact)) (roots : List MethodId)
  (seeds : List (MethodId × Node × PFact)) (zbind : Bool)
inductive BC : Obj → Prop where
  | root {M} : M ∈ roots → BC (.init M zeroFact)
  | start {M i} : BC (.init M i) → BC (.edge M i (Pb.entry M) (startFact i))
  | step {M i n f n' s f'} :
      BC (.edge M i n f) → (M, n, Instr.stmt s, n') ∈ Pb.edges →
      f' ∈ (transfer counted L s f).facts → BC (.edge M i n' f')
  | pass {M i n f n' c} :
      BC (.edge M i n f) → (M, n, Instr.call c, n') ∈ Pb.edges →
      memB f.fact.base c.touched = false → BC (.edge M i n' f)
  /-- Ghost reachability of an actual call input; no runtime retention obligation. -/
  | added {M i n f n' c e a} :
      BC (.edge M i n f) → (M, n, Instr.call c, n') ∈ Pb.edges →
      e ∈ c.toCallee → a ∈ (applyEdge f e.1 e.2).facts →
      BC (.added c.callee a.fact)
  | initR {m a d j} :
      BC (.added m a) → demand m d → emit d.din a = some j → BC (.init m j)
  | ret {M i n f n' c e1 a j g d g' r e2 r'} :
      BC (.edge M i n f) → (M, n, Instr.call c, n') ∈ Pb.edges →
      e1 ∈ c.toCallee → a ∈ (applyEdge f e1.1 e1.2).facts →
      BC (.init c.callee j) →
      BC (.edge c.callee j (Pb.exit c.callee) g) →
      demand c.callee d → restrict j g d = some g' →
      sat j a.fact = true →
      r ∈ (applySummary a j g').facts →
      e2 ∈ c.fromCallee → r' ∈ (applyEdge r e2.1 e2.2).facts →
      BC (.edge M i n' (limitF counted L r'))
  | retRec {M i n f n' c e1 a j g r e2 r'} :
      BC (.edge M i n f) → (M, n, Instr.call c, n') ∈ Pb.edges →
      e1 ∈ c.toCallee → a ∈ (applyEdge f e1.1 e1.2).facts →
      recs c.callee (j, g) → (sat j a.fact = true ∨ applicable j a.fact = true) →
      r ∈ (applySummary a j g).facts →
      e2 ∈ c.fromCallee → r' ∈ (applyEdge r e2.1 e2.2).facts →
      BC (.edge M i n' (limitF counted L r'))
  | vuln {M i n f s} :
      BC (.edge M i n f) → (M, n, s) ∈ sinks → check i f s = .triggered →
      BC (.vuln M n s f.demand)
  | clean {M i n f n' cl f'} :
      BC (.edge M i n f) → (M, n, Instr.clean cl, n') ∈ Pb.edges →
      f' ∈ (BaseCleaner.cleanRes cl f).facts → BC (.edge M i n' f')
  | filt {M i n f n' b may} :
      BC (.edge M i n f) → (M, n, Instr.filt b may, n') ∈ Pb.edges →
      (f.fact.base = b → may f.fact.path = true) → BC (.edge M i n' f)
  | zpass {M n n' c} :
      BC (.edge M zeroFact n Backward.zeroAF) → (M, n, Instr.call c, n') ∈ Pb.edges →
      BC (.edge M zeroFact n' Backward.zeroAF)
  | zin {M n n' c} : zbind = true →
      BC (.edge M zeroFact n Backward.zeroAF) → (M, n, Instr.call c, n') ∈ Pb.edges →
      BC (.init c.callee zeroFact)
  | seed {M n s} : (M, n, s) ∈ seeds → BC (.edge M zeroFact n Backward.zeroAF) →
      BC (.edge M zeroFact n (limitF counted L ⟨s, false⟩))
  | zret {M n n' c g r e2 r'} : zbind = true →
      BC (.edge M zeroFact n Backward.zeroAF) → (M, n, Instr.call c, n') ∈ Pb.edges →
      BC (.edge c.callee zeroFact (Pb.exit c.callee) g) →
      r ∈ (applySummary Backward.zeroAF zeroFact g).facts →
      e2 ∈ c.fromCallee → r' ∈ (applyEdge r e2.1 e2.2).facts →
      BC (.edge M zeroFact n' (limitF counted L r'))

end Backward

/-- Canonical request-free forward run with FLOW emission. -/
abbrev FW (P : Program) (counted : Acc → Bool) (L : Nat)
    (dem : MethodId → DemandEdge → Prop) (rc : Recs)
    (sinks : List (MethodId × Node × PFact)) (roots : List MethodId) :=
  FC P counted L dem emitW satW restrictI rc sinks roots

/-- Canonical request-free backward run with FLOW emission. -/
abbrev BW (Pb : Program) (counted : Acc → Bool) (L : Nat)
    (dem : MethodId → DemandEdge → Prop) (rc : Recs)
    (roots : List MethodId) (seeds : List (MethodId × Node × PFact)) :=
  BC Pb counted L dem emitW satW restrictI rc [] roots seeds true

end ApSpec.Current
