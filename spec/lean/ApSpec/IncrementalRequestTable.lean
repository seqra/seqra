/-
  Constructive finite model of ONE source owner's merged pattern-interest table.

  Pending and processed are transport states, not concrete truth. A Candidate is
  an already reachability-justified fact-pattern match; this model neither
  constructs ordinary summary edges nor proves their AP/port semantics. `delivered` is
  ghost event history used only to prove exact once-per-pair delivery.

  The selected design subscribes at METHOD ENTRY. A candidate represents an
  actual entry offer at M whose method reaches this request's owner B in the
  already resolved, fixed prescan call graph. A read-free M can therefore
  deliver interest directly to B. An internal write alone is not an entry
  candidate: its output must first arrive at a real method entry. The global
  directory holds typed request/entry references; complete ports, premises,
  payloads and supports stay in their ordinary fact/summary owners.

  There is no per-relay request-table policy in this model. The positive gate
  below is generic; its late-certificate control is an OPTIONAL generalisation,
  not a requirement to add dynamic graph invalidation to the selected fixed-CG
  design. Construction of valid actual entries, full-pattern matching and the
  fixed-CG reachability certificate remain outside the proved scope.

  Lists implement finite-set oracles, not a proposed packed runtime layout.
  In particular, normalisation cost is outside the pair-count comparison.
-/
import ApSpec.Run1RequestProtocol

namespace ApSpec.IncrementalRequestTable
open ApSpec.Run1RequestProtocol

variable {Mark Candidate : Type} [DecidableEq Mark] [DecidableEq Candidate]

def normalise (xs : List Mark) : List Mark := merge xs []

@[simp] theorem mem_normalise (xs : List Mark) (q : Mark) :
    q ∈ normalise xs ↔ q ∈ xs := by simp [normalise]

theorem normalise_nodup (xs : List Mark) : (normalise xs).Nodup :=
  merge_nodup xs [] (by simp)

/-- A transient finite pair frontier; not a retained endpoint closure. -/
def pairs (marks : List Mark) (candidates : List Candidate) : List (Mark × Candidate) :=
  normalise (marks.flatMap fun mark => candidates.map fun candidate => (mark,candidate))

@[simp] theorem mem_pairs (marks : List Mark) (candidates : List Candidate)
    (q : Mark) (r : Candidate) : (q,r) ∈ pairs marks candidates ↔ q ∈ marks ∧ r ∈ candidates := by
  simp [pairs,List.mem_flatMap,List.mem_map]

theorem pairs_nodup (marks : List Mark) (candidates : List Candidate) :
    (pairs marks candidates).Nodup := normalise_nodup _

structure State (Mark Candidate : Type) where
  pending : List Mark
  processed : List Mark
  candidates : List Candidate
  delivered : List (Mark × Candidate)
  deriving Repr, DecidableEq

def empty : State Mark Candidate := ⟨[],[],[],[]⟩

def known (s : State Mark Candidate) (q : Mark) : Prop :=
  q ∈ s.pending ∨ q ∈ s.processed

instance (s : State Mark Candidate) (q : Mark) : Decidable (known s q) :=
  inferInstanceAs (Decidable (q ∈ s.pending ∨ q ∈ s.processed))

def requestDelta (s : State Mark Candidate) (offered : List Mark) : List Mark :=
  difference (normalise offered) (merge s.pending s.processed)

omit [DecidableEq Candidate] in
@[simp] theorem mem_requestDelta (s : State Mark Candidate) (offered : List Mark) (q : Mark) :
    q ∈ requestDelta s offered ↔ q ∈ offered ∧ ¬known s q := by
  simp [requestDelta,known]

def offer (s : State Mark Candidate) (offered : List Mark) : State Mark Candidate :=
  {s with pending := merge (requestDelta s offered) s.pending}

def candidateDelta (s : State Mark Candidate) (offered : List Candidate) : List Candidate :=
  difference (normalise offered) s.candidates

omit [DecidableEq Mark] in
@[simp] theorem mem_candidateDelta (s : State Mark Candidate) (offered : List Candidate) (r : Candidate) :
    r ∈ candidateDelta s offered ↔ r ∈ offered ∧ r ∉ s.candidates := by
  simp [candidateDelta]

/-- Existing processed requests meet only FRESH candidates. Pending requests will
    meet every then-current candidate when drained. -/
def addCandidates (s : State Mark Candidate) (offered : List Candidate) : State Mark Candidate :=
  let fresh := candidateDelta s offered
  {s with candidates := merge fresh s.candidates,
          delivered := s.delivered ++ pairs s.processed fresh}

/-- Fresh requests meet EXISTING candidates exactly once. Install processed state
    before outward delivery in a runtime refinement of this atomic step. -/
def drain (s : State Mark Candidate) : State Mark Candidate :=
  ⟨[],merge s.pending s.processed,s.candidates,
    s.delivered ++ pairs s.pending s.candidates⟩

def Valid (s : State Mark Candidate) : Prop :=
  s.pending.Nodup ∧ s.processed.Nodup ∧ s.candidates.Nodup ∧
  (∀ q, q ∈ s.pending → q ∉ s.processed) ∧ s.delivered.Nodup ∧
  (∀ q r, (q,r) ∈ s.delivered ↔ q ∈ s.processed ∧ r ∈ s.candidates)

omit [DecidableEq Mark] [DecidableEq Candidate] in
theorem empty_valid : Valid (empty : State Mark Candidate) := by
  simp [Valid,empty]

omit [DecidableEq Candidate] in
@[simp] theorem offer_known (s : State Mark Candidate) (offered : List Mark) (q : Mark) :
    known (offer s offered) q ↔ q ∈ offered ∨ known s q := by
  simp only [known,offer,mem_merge,mem_requestDelta]
  by_cases hp : q ∈ s.pending <;> by_cases ha : q ∈ s.processed <;>
    simp [hp,ha]

omit [DecidableEq Candidate] in
/-- An already-known mark cannot create a second pending admission. -/
theorem duplicate_request_suppressed (s : State Mark Candidate) (q : Mark)
    (h : known s q) : requestDelta s [q] = [] := by
  have hmem : q ∈ merge s.pending s.processed := by
    simpa only [mem_merge,known] using h
  change difference [q] (merge s.pending s.processed) = []
  simp only [difference,List.filter_cons,List.filter_nil,hmem,
    not_true_eq_false,decide_false,Bool.false_eq_true,if_false]

omit [DecidableEq Candidate] in
theorem offer_valid (s : State Mark Candidate) (h : Valid s) (offered : List Mark) :
    Valid (offer s offered) := by
  rcases h with ⟨hp,ha,hr,hpa,hd,hcover⟩
  refine ⟨merge_nodup _ _ hp,ha,hr,?_,hd,hcover⟩
  intro q hq
  rcases (mem_merge _ _ _).mp hq with hf | old
  · exact fun admitted => (mem_requestDelta _ _ _).mp hf |>.2 (Or.inr admitted)
  · exact hpa q old

theorem drain_frontier_exact (s : State Mark Candidate) (q : Mark) (r : Candidate) :
    (q,r) ∈ pairs s.pending s.candidates ↔ q ∈ s.pending ∧ r ∈ s.candidates :=
  mem_pairs _ _ _ _

theorem drain_frontier_is_fresh (s : State Mark Candidate) (h : Valid s)
    (q : Mark) (r : Candidate) (fresh : (q,r) ∈ pairs s.pending s.candidates) :
    (q,r) ∉ s.delivered := by
  intro old
  exact h.2.2.2.1 q ((mem_pairs _ _ _ _).mp fresh).1
    ((h.2.2.2.2.2 q r).mp old).1

theorem candidate_frontier_exact (s : State Mark Candidate) (offered : List Candidate)
    (q : Mark) (r : Candidate) :
    (q,r) ∈ pairs s.processed (candidateDelta s offered) ↔
      q ∈ s.processed ∧ r ∈ offered ∧ r ∉ s.candidates := by simp

theorem candidate_frontier_is_fresh (s : State Mark Candidate) (h : Valid s)
    (offered : List Candidate) (q : Mark) (r : Candidate)
    (fresh : (q,r) ∈ pairs s.processed (candidateDelta s offered)) :
    (q,r) ∉ s.delivered := by
  intro old
  exact ((candidate_frontier_exact _ _ _ _).mp fresh).2.2
    ((h.2.2.2.2.2 q r).mp old).2

theorem drain_valid (s : State Mark Candidate) (h : Valid s) : Valid (drain s) := by
  rcases h with ⟨hp,ha,hr,hpa,hd,hcover⟩
  refine ⟨by simp [drain],merge_nodup _ _ ha,hr,?_,?_,?_⟩
  · simp [drain]
  · apply List.nodup_append.mpr
    refine ⟨hd,pairs_nodup _ _,?_⟩
    intro a hold b hfresh eq
    subst b
    rcases a with ⟨q,r⟩
    exact hpa q ((mem_pairs _ _ _ _).mp hfresh).1 ((hcover q r).mp hold).1
  · intro q r
    simp only [drain,List.mem_append,mem_pairs,mem_merge]
    rw [hcover]
    constructor
    · rintro (⟨ha,hr⟩ | ⟨hp,hr⟩)
      · exact ⟨Or.inr ha,hr⟩
      · exact ⟨Or.inl hp,hr⟩
    · rintro ⟨hp | ha,hr⟩
      · exact Or.inr ⟨hp,hr⟩
      · exact Or.inl ⟨ha,hr⟩

theorem addCandidates_valid (s : State Mark Candidate) (h : Valid s) (offered : List Candidate) :
    Valid (addCandidates s offered) := by
  rcases h with ⟨hp,ha,hr,hpa,hd,hcover⟩
  refine ⟨hp,ha,merge_nodup _ _ hr,hpa,?_,?_⟩
  · apply List.nodup_append.mpr
    refine ⟨hd,pairs_nodup _ _,?_⟩
    intro a hold b hfresh eq
    subst b
    rcases a with ⟨q,r⟩
    exact ((mem_candidateDelta _ _ _).mp ((mem_pairs _ _ _ _).mp hfresh).2).2
      ((hcover q r).mp hold).2
  · intro q r
    simp only [addCandidates,List.mem_append,mem_pairs,mem_merge]
    rw [hcover]
    constructor
    · rintro (⟨ha,hr⟩ | ⟨ha,hfresh⟩)
      · exact ⟨ha,Or.inr hr⟩
      · exact ⟨ha,Or.inl hfresh⟩
    · rintro ⟨ha,hfresh | hr⟩
      · exact Or.inr ⟨ha,hfresh⟩
      · exact Or.inl ⟨ha,hr⟩

/-- A global fact-pattern directory can retain candidates while positive
    reachability certificates grow. Reconsidering its retained candidates must
    not treat an earlier missing certificate as a permanent negative cache.
    Membership in `provenReachable` is an EXTERNAL certificate assumption:
    no call-graph/CFG/AP reachability algorithm is modelled here. This generic
    late-certificate facility is optional: selected method-entry subscriptions
    use reachability in the fixed prescan CG and require no graph invalidation. -/
def discoverReachable (s : State Mark Candidate) (patternCandidates provenReachable : List Candidate) :
    State Mark Candidate :=
  addCandidates s (patternCandidates.filter fun candidate => decide (candidate ∈ provenReachable))

theorem discovery_frontier_exact (s : State Mark Candidate)
    (patternCandidates provenReachable : List Candidate) (q : Mark) (r : Candidate) :
    (q,r) ∈ pairs s.processed
      (candidateDelta s (patternCandidates.filter fun candidate => decide (candidate ∈ provenReachable))) ↔
    q ∈ s.processed ∧ r ∈ patternCandidates ∧ r ∈ provenReachable ∧ r ∉ s.candidates := by
  simp [and_assoc]

theorem discoverReachable_valid (s : State Mark Candidate) (h : Valid s)
    (patternCandidates provenReachable : List Candidate) :
    Valid (discoverReachable s patternCandidates provenReachable) :=
  addCandidates_valid s h _

/-- A repeated drain has no new pair frontier. -/
theorem second_drain_no_frontier (s : State Mark Candidate) :
    pairs (drain s).pending (drain s).candidates = [] := by
  simp [drain,pairs,normalise,merge]

omit [DecidableEq Mark] in
/-- A candidate already installed is not joined again against processed marks. -/
theorem duplicate_candidate_suppressed (s : State Mark Candidate) (r : Candidate)
    (h : r ∈ s.candidates) : candidateDelta s [r] = [] := by
  change difference [r] s.candidates = []
  simp only [difference,List.filter_cons,List.filter_nil,h,
    not_true_eq_false,decide_false,Bool.false_eq_true,if_false]

inductive Event (Mark Candidate : Type) where
  | requests (marks : List Mark)
  | candidates (candidates : List Candidate)
  | barrier
  deriving Repr

def step (s : State Mark Candidate) : Event Mark Candidate → State Mark Candidate
  | .requests marks => offer s marks
  | .candidates candidates => addCandidates s candidates
  | .barrier => drain s

def run (s : State Mark Candidate) : List (Event Mark Candidate) → State Mark Candidate
  | [] => s
  | event::rest => run (step s event) rest

def offeredRequests : List (Event Mark Candidate) → List Mark
  | [] => []
  | .requests marks :: rest => marks ++ offeredRequests rest
  | _ :: rest => offeredRequests rest

def offeredCandidates : List (Event Mark Candidate) → List Candidate
  | [] => []
  | .candidates candidates :: rest => candidates ++ offeredCandidates rest
  | _ :: rest => offeredCandidates rest

theorem run_requests_exact (s : State Mark Candidate)
    (events : List (Event Mark Candidate)) (q : Mark) :
    known (run s events) q ↔ known s q ∨ q ∈ offeredRequests events := by
  induction events generalizing s with
  | nil => simp [run,offeredRequests]
  | cons event rest ih =>
    rw [run,ih]
    cases event with
    | requests marks =>
      simp only [step,offer_known,offeredRequests,List.mem_append]
      constructor
      · rintro ((hm | hs) | hr)
        · exact Or.inr (Or.inl hm)
        · exact Or.inl hs
        · exact Or.inr (Or.inr hr)
      · rintro (hs | hm | hr)
        · exact Or.inl (Or.inr hs)
        · exact Or.inl (Or.inl hm)
        · exact Or.inr hr
    | candidates candidates => rfl
    | barrier =>
      simp only [step,known,drain,List.not_mem_nil,mem_merge,false_or,
        offeredRequests]

theorem run_candidates_exact (s : State Mark Candidate)
    (events : List (Event Mark Candidate)) (r : Candidate) :
    r ∈ (run s events).candidates ↔ r ∈ s.candidates ∨ r ∈ offeredCandidates events := by
  induction events generalizing s with
  | nil => simp [run,offeredCandidates]
  | cons event rest ih =>
    rw [run,ih]
    cases event with
    | requests marks => rfl
    | barrier => rfl
    | candidates candidates =>
      simp only [step,addCandidates,mem_merge,mem_candidateDelta,
        offeredCandidates,List.mem_append]
      by_cases old : r ∈ s.candidates <;> simp [old]

theorem step_valid (s : State Mark Candidate) (h : Valid s) (event : Event Mark Candidate) :
    Valid (step s event) := by
  cases event with
  | requests marks => exact offer_valid s h marks
  | candidates candidates => exact addCandidates_valid s h candidates
  | barrier => exact drain_valid s h

theorem every_trace_valid (s : State Mark Candidate) (h : Valid s)
    (events : List (Event Mark Candidate)) : Valid (run s events) := by
  induction events generalizing s with
  | nil => exact h
  | cons event rest ih => exact ih _ (step_valid s h event)

/-- For ANY interleaving, a final drain delivers exactly known requests ×
    admitted candidates. Nodup of the ghost history proves at most one delivery. -/
theorem final_barrier_exact (events : List (Event Mark Candidate)) (q : Mark) (r : Candidate) :
    let s := run empty events
    (q,r) ∈ (drain s).delivered ↔ known s q ∧ r ∈ s.candidates := by
  dsimp
  have h := drain_valid (run empty events) (every_trace_valid empty empty_valid events)
  simpa only [drain,mem_merge,known] using h.2.2.2.2.2 q r

theorem no_pair_delivered_twice (events : List (Event Mark Candidate)) :
    (run empty events).delivered.Nodup :=
  (every_trace_valid empty empty_valid events).2.2.2.2.1

/-- Requests and reachable candidates can arrive in ANY order. After a final
    barrier the exact cross-product of offered cells was delivered once. -/
theorem all_arrival_orders_exact (events : List (Event Mark Candidate))
    (q : Mark) (r : Candidate) :
    (q,r) ∈ (drain (run empty events)).delivered ↔
      q ∈ offeredRequests events ∧ r ∈ offeredCandidates events := by
  have exactPairs := final_barrier_exact events q r
  dsimp only at exactPairs
  rw [exactPairs,run_requests_exact,run_candidates_exact]
  simp [known,empty]

namespace Controls

def requestsFirst : List (Event Nat Nat) :=
  [.requests [7,7],.barrier,.candidates [3,3],.candidates [4],.barrier]

def candidatesFirst : List (Event Nat Nat) :=
  [.candidates [4,3,3],.requests [7,7],.barrier]

def growingCandidates (n : Nat) : List (Event Nat Nat) :=
  [.requests [7],.barrier] ++ (List.range n).map (fun candidate => .candidates [candidate])

def incrementalPairs (n : Nat) : Nat := (run empty (growingCandidates n)).delivered.length

/-- Full replay of one old request against ALL candidates after each insertion. -/
def replayPairs : Nat → Nat
  | 0 => 0
  | n+1 => replayPairs n + (n+1)

def awaitingReachability : State Nat Nat := drain (offer empty [7])

def beforeCertificate : State Nat Nat :=
  discoverReachable awaitingReachability [3] []

def afterCertificate : State Nat Nat :=
  discoverReachable beforeCertificate [3] [3]

def checks : List Bool :=
  [ (run empty requestsFirst).delivered == [(7,3),(7,4)],
    (run empty candidatesFirst).delivered.length == 2,
    decide ((7,3) ∈ (run empty candidatesFirst).delivered),
    decide ((7,4) ∈ (run empty candidatesFirst).delivered),
    incrementalPairs 64 == 64,
    replayPairs 64 == 2080,
    incrementalPairs 64 < replayPairs 64,
    beforeCertificate.delivered.isEmpty,
    afterCertificate.delivered == [(7,3)],
    (discoverReachable afterCertificate [3] [3]).delivered == [(7,3)],
    (run empty [.requests [7],.requests [7],.barrier,.candidates [3],.candidates [3],
      .barrier,.requests [7],.barrier] : State Nat Nat).delivered == [(7,3)] ]

theorem control_checks : checks.all id = true := by decide

end Controls
end ApSpec.IncrementalRequestTable
