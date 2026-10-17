/- Constructive finite replay model. Graph nodes encode full owner/original keys.
   Fixed graphs are semantic/cost oracles, not runtime graph snapshots. Sources
   stay authoritative; grouping and visited state are rebuilt for every flush.
   JVM geometry, owner queues, fairness and concurrent IO are adapter duties.
   Source-event lists are a union oracle for per-key authoritative/pending maps;
   same_source_union and fresh_delta_exact justify that representation bridge. -/
import ApSpec.BottomUpConcreteStaticNeeds
namespace ApSpec.StaticNeedReplayBatch
open ApSpec
set_option autoImplicit false
abbrev Graph := Run1Requests.Graph
abbrev Reach {n : Nat} (g : Graph n) (s v : Fin n) : Prop := Run1Requests.Reaches g s v
abbrev Node := BottomUpConcreteStaticNeeds.Node
abbrev Endpoint := BottomUpConcreteStaticNeeds.BaseRequest
abbrev Needs := BottomUpConcreteStaticNeeds.SourceNeeds

/-- One virtual seed permits one existing bounded visited/path search. -/
def lifted {n : Nat} (g : Graph n) (starts : List (Fin n)) : Graph (n+1) :=
  g.map (fun e => (e.1.castSucc,e.2.castSucc)) ++
    starts.map (fun s => (s.castSucc,Fin.last n))

theorem cast_ne_last {n : Nat} (v : Fin n) : v.castSucc ≠ Fin.last n := by
  intro h
  have equal : v.val = n := congrArg Fin.val h
  exact (Nat.ne_of_lt v.isLt) equal

theorem reach_lift {n : Nat} (g : Graph n) (starts : List (Fin n))
    (s v : Fin n) (hs : s ∈ starts) (path : Reach g s v) :
    Reach (lifted g starts) (Fin.last n) v.castSucc := by
  induction path with
  | seed =>
    apply Run1Requests.Reaches.call (b := Fin.last n)
    · exact List.mem_append_right _ (List.mem_map.mpr ⟨s,hs,rfl⟩)
    · exact .seed
  | call edge _ ih =>
    apply Run1Requests.Reaches.call (b := _ ) _ ih
    exact List.mem_append_left _ (List.mem_map.mpr ⟨_,edge,rfl⟩)

theorem reach_unlift {n : Nat} (g : Graph n) (starts : List (Fin n))
    (v : Fin (n+1)) (path : Reach (lifted g starts) (Fin.last n) v) :
    v = Fin.last n ∨ ∃ s ∈ starts, ∃ x : Fin n, v = x.castSucc ∧ Reach g s x := by
  induction path with
  | seed => exact Or.inl rfl
  | @call a b edge _ ih =>
    rcases List.mem_append.mp edge with old | seed
    · obtain ⟨⟨x,y⟩,present,equal⟩ := List.mem_map.mp old
      have ha : x.castSucc = a := congrArg Prod.fst equal
      have hb : y.castSucc = b := congrArg Prod.snd equal
      subst a; subst b
      rcases ih with impossible | ⟨s,hs,z,hz,rest⟩
      · exact False.elim (cast_ne_last y impossible)
      · have same : y = z := Fin.castSucc_inj.mp hz
        subst z
        exact Or.inr ⟨s,hs,x,rfl,.call present rest⟩
    · obtain ⟨s,hs,equal⟩ := List.mem_map.mp seed
      have ha : s.castSucc = a := congrArg Prod.fst equal
      subst a
      exact Or.inr ⟨s,hs,s,rfl,.seed⟩

/-- One shared visited search; omit its virtual seed from real work. -/
def multiNodes {n : Nat} (g : Graph n) (starts : List (Fin n)) : List (Fin n) :=
  let entries := (BottomUpStaticRequests.callClosure (lifted g starts) (Fin.last n)).entries
  (List.finRange n).filter fun v => (BottomUpStaticRequests.findPath entries v.castSucc).isSome

def multiPath {n : Nat} (g : Graph n) (starts : List (Fin n)) (v : Fin n) :
    Option (BottomUpStaticRequests.CallPath (lifted g starts) v.castSucc (Fin.last n)) :=
  BottomUpStaticRequests.inheritedPath (lifted g starts) (Fin.last n) v.castSucc

theorem multi_nodes_exact {n : Nat} (g : Graph n) (starts : List (Fin n)) (v : Fin n) :
    v ∈ multiNodes g starts ↔ ∃ s ∈ starts, Reach g s v := by
  simp only [multiNodes,List.mem_filter,List.mem_finRange,true_and]
  change (BottomUpStaticRequests.inheritedPath (lifted g starts) (Fin.last n) v.castSucc).isSome = true ↔ _
  rw [BottomUpStaticRequests.inheritedPath_exact]
  constructor
  · intro h
    rcases reach_unlift g starts v.castSucc h with impossible | ⟨s,hs,x,hx,path⟩
    · exact False.elim (cast_ne_last v impossible)
    · have same : v = x := Fin.castSucc_inj.mp hx
      subst x; exact ⟨s,hs,path⟩
  · rintro ⟨s,hs,path⟩; exact reach_lift g starts s v hs path

theorem multi_path_complete {n : Nat} (g : Graph n) (starts : List (Fin n)) (v : Fin n) :
    (multiPath g starts v).isSome = true ↔ v ∈ multiNodes g starts := by
  simp only [multiPath,BottomUpStaticRequests.inheritedPath,multiNodes,List.mem_filter,List.mem_finRange,true_and]

structure Row (n : Nat) where
  origin : Fin n
  marks : List Mark
  deriving DecidableEq, Repr
structure Batch (n : Nat) where
  starts : List (Fin n)
  marks : List Mark
  deriving DecidableEq, Repr

/-- Exact content equality permits reordered/duplicate immutable encodings. -/
def equalMarks (a b : List Mark) : Bool :=
  a.all (fun t => decide (t ∈ b)) && b.all (fun t => decide (t ∈ a))

theorem equal_marks_exact (a b : List Mark) :
    equalMarks a b = true ↔ ∀ t, t ∈ a ↔ t ∈ b := by
  simp only [equalMarks,Bool.and_eq_true,List.all_eq_true,decide_eq_true_eq]
  constructor
  · rintro ⟨ab,ba⟩ t; exact ⟨ab t,ba t⟩
  · intro h; exact ⟨fun t ht => (h t).mp ht,fun t ht => (h t).mpr ht⟩

def insert {n : Nat} (row : Row n) : List (Batch n) → List (Batch n)
  | [] => if row.marks.isEmpty then [] else [⟨[row.origin],row.marks⟩]
  | batch::rest =>
    if row.marks.isEmpty then batch::rest
    else if equalMarks row.marks batch.marks then
      {batch with starts := row.origin::batch.starts}::rest
    else batch::insert row rest

def group {n : Nat} : List (Row n) → List (Batch n)
  | [] => []
  | row::rest => insert row (group rest)

def Supports {n : Nat} (batches : List (Batch n)) (s : Fin n) (t : Mark) : Prop :=
  ∃ b ∈ batches, s ∈ b.starts ∧ t ∈ b.marks

@[simp] theorem supports_cons {n : Nat} (b : Batch n) (bs : List (Batch n))
    (s : Fin n) (t : Mark) :
    Supports (b::bs) s t ↔ (s ∈ b.starts ∧ t ∈ b.marks) ∨ Supports bs s t := by
  simp [Supports]

theorem insert_support {n : Nat} (row : Row n) (batches : List (Batch n))
    (s : Fin n) (t : Mark) :
    Supports (insert row batches) s t ↔
      (s = row.origin ∧ t ∈ row.marks) ∨ Supports batches s t := by
  induction batches with
  | nil =>
    cases hm : row.marks with
    | nil => simp [insert,Supports,hm]
    | cons h tail => simp [insert,Supports,hm,and_comm]
  | cons b bs ih =>
    by_cases empty : row.marks.isEmpty = true
    · have hm : row.marks = [] := List.isEmpty_iff.mp empty
      simp [insert,hm]
    · by_cases same : equalMarks row.marks b.marks = true
      · have marks := (equal_marks_exact _ _).mp same t
        simp only [insert,empty,Bool.false_eq_true,if_false,same,if_true,supports_cons,
          List.mem_cons]
        rw [← marks]
        constructor
        · rintro (⟨hs,ht⟩ | rest)
          · rcases hs with eq | hs
            · exact Or.inl ⟨eq,ht⟩
            · exact Or.inr (Or.inl ⟨hs,ht⟩)
          · exact Or.inr (Or.inr rest)
        · rintro (⟨eq,ht⟩ | (⟨hs,ht⟩ | rest))
          · exact Or.inl ⟨Or.inl eq,ht⟩
          · exact Or.inl ⟨Or.inr hs,ht⟩
          · exact Or.inr rest
      · simp only [insert,empty,Bool.false_eq_true,if_false,same,supports_cons,ih]
        exact or_left_comm

theorem group_support {n : Nat} (rows : List (Row n)) (s : Fin n) (t : Mark) :
    Supports (group rows) s t ↔ ∃ row ∈ rows, s = row.origin ∧ t ∈ row.marks := by
  induction rows with
  | nil => simp [group,Supports]
  | cons row rest ih =>
    rw [group,insert_support,ih]
    simp [List.mem_cons,or_and_right,exists_or]

/-- The independent reference runs the existing single-source path search. -/
def reference {n : Nat} (labels : Fin n → Node) (g : Graph n)
    (rows : List (Row n)) : List Endpoint :=
  rows.flatMap fun row => (BottomUpConcreteStaticNeeds.fastNodes g row.origin).flatMap fun v => BottomUpConcreteStaticNeeds.emitAt (labels v) row.marks

def batched {n : Nat} (labels : Fin n → Node) (g : Graph n)
    (batches : List (Batch n)) : List Endpoint :=
  batches.flatMap fun b => (multiNodes g b.starts).flatMap fun v => BottomUpConcreteStaticNeeds.emitAt (labels v) b.marks

theorem fast_nodes_reach {n : Nat} (g : Graph n) (s v : Fin n) :
    v ∈ BottomUpConcreteStaticNeeds.fastNodes g s ↔ Reach g s v := by
  simp only [BottomUpConcreteStaticNeeds.fastNodes,List.mem_filter,List.mem_finRange,true_and]
  exact BottomUpStaticRequests.inheritedPath_exact g s v

theorem mem_reference {n : Nat} (labels : Fin n → Node) (g : Graph n)
    (rows : List (Row n)) (q : Endpoint) :
    q ∈ reference labels g rows ↔ ∃ row ∈ rows, ∃ v,
      Reach g row.origin v ∧ labels v = .base q.owner q.base ∧ q.mark ∈ row.marks := by
  simp only [reference,List.mem_flatMap,BottomUpConcreteStaticNeeds.mem_emitAt,fast_nodes_reach]

theorem mem_batched {n : Nat} (labels : Fin n → Node) (g : Graph n)
    (batches : List (Batch n)) (q : Endpoint) :
    q ∈ batched labels g batches ↔ ∃ s v,
      Supports batches s q.mark ∧ Reach g s v ∧ labels v = .base q.owner q.base := by
  simp only [batched,List.mem_flatMap,BottomUpConcreteStaticNeeds.mem_emitAt,multi_nodes_exact]
  constructor
  · rintro ⟨b,hb,v,⟨s,hs,path⟩,label,mark⟩
    exact ⟨s,v,⟨b,hb,hs,mark⟩,path,label⟩
  · rintro ⟨s,v,⟨b,hb,hs,mark⟩,path,label⟩
    exact ⟨b,hb,v,⟨s,hs,path⟩,label,mark⟩

theorem grouped_exact {n : Nat} (labels : Fin n → Node) (g : Graph n)
    (rows : List (Row n)) (q : Endpoint) :
    q ∈ batched labels g (group rows) ↔ q ∈ reference labels g rows := by
  rw [mem_batched,mem_reference]
  simp only [group_support]
  constructor
  · rintro ⟨s,v,⟨row,member,same,mark⟩,path,label⟩
    subst s; exact ⟨row,member,v,path,label,mark⟩
  · rintro ⟨row,member,v,path,label,mark⟩
    exact ⟨row.origin,v,⟨row,member,rfl,mark⟩,path,label⟩

/-- Grouping skips empty mark sets; this is not a count of delivered messages. -/
theorem insert_length_le {n : Nat} (row : Row n) (bs : List (Batch n)) :
    (insert row bs).length ≤ bs.length + 1 := by
  induction bs with
  | nil => simp only [insert]; split <;> simp
  | cons b bs ih =>
    simp only [insert]
    split
    · simp
    · split
      · simp
      · simp only [List.length_cons]; omega

theorem group_count_le_rows {n : Nat} (rows : List (Row n)) :
    (group rows).length ≤ rows.length := by
  induction rows with
  | nil => simp [group]
  | cons r rs ih =>
    have h := insert_length_le r (group rs)
    simp only [group,List.length_cons]; omega

theorem group_marks_nonempty {n : Nat} (rows : List (Row n)) :
    ∀ b ∈ group rows, b.marks ≠ [] := by
  have inserted : ∀ (r : Row n) bs, (∀ b ∈ bs, b.marks ≠ []) →
      ∀ b ∈ insert r bs, b.marks ≠ [] := by
    intro r bs
    induction bs with
    | nil =>
      intro _ b hb
      simp only [insert] at hb
      split at hb
      · simp at hb
      · simp only [List.mem_singleton] at hb
        subst b
        intro eq
        change r.marks = [] at eq
        have hempty : r.marks.isEmpty = true := by simp [eq]
        contradiction
    | cons b bs ih =>
      intro all x hx
      simp only [insert] at hx
      split at hx
      · exact all x hx
      · split at hx
        · rcases List.mem_cons.mp hx with eq | hx
          · subst x; exact all b (by simp)
          · exact all x (by simp [hx])
        · rcases List.mem_cons.mp hx with eq | hx
          · subst x; exact all b (by simp)
          · exact ih (fun z hz => all z (by simp [hz])) x hx
  induction rows with
  | nil => simp [group]
  | cons r rs ih => exact inserted r (group rs) ih

/-- Candidate membership, before committed-R subtraction. -/
def Candidate {n : Nat} (labels : Fin n → Node) (g : Graph n)
    (rows : List (Row n)) (q : Endpoint) : Prop := q ∈ reference labels g rows

@[simp] theorem candidate_cons {n : Nat} (labels : Fin n → Node) (g : Graph n)
    (r : Row n) (rs : List (Row n)) (q : Endpoint) :
    Candidate labels g (r::rs) q ↔
      Candidate labels g [r] q ∨ Candidate labels g rs q := by
  simp [Candidate,reference]

theorem candidate_mono {n : Nat} (labels : Fin n → Node) (g h : Graph n)
    (rows : List (Row n)) (edges : ∀ e ∈ g, e ∈ h) (q : Endpoint) :
    Candidate labels g rows q → Candidate labels h rows q := by
  rw [Candidate,mem_reference,Candidate,mem_reference]
  rintro ⟨r,hr,v,path,label,mark⟩
  exact ⟨r,hr,v,BottomUpConcreteStaticNeeds.reaches_mono g h edges _ _ path,label,mark⟩

/-- A registration-prefix model. Runtime traversal reads a live graph; it does
not atomically snapshot it. This serialized-prefix model uses a fixed graph
oracle per abstract flush. A simulation of overlapping live traversal is an
additional adapter; every later insertion must dirty another counted turn. -/
structure State (n : Nat) where
  graph : Graph n
  sources : List (Row n)
  pending : List (Row n)
  graphDirty : Bool
  candidates : List Endpoint
  deriving Repr

def replayRows {n : Nat} (s : State n) : List (Row n) :=
  if s.graphDirty then s.sources else s.pending

def initial {n : Nat} (g : Graph n) : State n := ⟨g,[],[],false,[]⟩
def addSource {n : Nat} (s : State n) (row : Row n) : State n :=
  {s with sources := row::s.sources, pending := row::s.pending}
def addEdges {n : Nat} (s : State n) (edges : Graph n) : State n :=
  {s with graph := edges ++ s.graph, graphDirty := true}
def flush {n : Nat} (labels : Fin n → Node) (s : State n) : State n :=
  let outputs := batched labels s.graph (group (replayRows s))
  { s with
    candidates := Run1RequestProtocol.merge outputs s.candidates
    pending := []
    graphDirty := false }

/-- An inductive work invariant, proved below for every generated registration
trace; it is not a summary-completeness assumption. -/
def Valid {n : Nat} (labels : Fin n → Node) (s : State n) : Prop :=
  (∀ q ∈ s.candidates, Candidate labels s.graph s.sources q) ∧
  (∀ q, Candidate labels s.graph (replayRows s) q → Candidate labels s.graph s.sources q) ∧
  (∀ q, Candidate labels s.graph s.sources q →
    q ∈ s.candidates ∨ Candidate labels s.graph (replayRows s) q)

theorem initial_valid {n : Nat} (labels : Fin n → Node) (g : Graph n) :
    Valid labels (initial g) := by simp [Valid,initial,replayRows,Candidate,reference]

theorem source_preserves_valid {n : Nat} (labels : Fin n → Node) (s : State n)
    (row : Row n) (valid : Valid labels s) : Valid labels (addSource s row) := by
  rcases valid with ⟨sound,pending,cover⟩
  have replay : replayRows (addSource s row) = row :: replayRows s := by
    cases h : s.graphDirty <;> simp [replayRows,addSource,h]
  refine ⟨?_,?_,?_⟩
  · intro q hq; exact (candidate_cons _ _ _ _ _).mpr (Or.inr (sound q hq))
  · intro q hq
    change Candidate labels s.graph (row::s.sources) q
    rw [replay] at hq
    rcases (candidate_cons _ _ _ _ _).mp hq with new | old
    · exact (candidate_cons _ _ _ _ _).mpr (Or.inl new)
    · exact (candidate_cons _ _ _ _ _).mpr (Or.inr (pending q old))
  · intro q hq
    change Candidate labels s.graph (row::s.sources) q at hq
    rcases (candidate_cons _ _ _ _ _).mp hq with new | old
    · right; rw [replay]; exact (candidate_cons _ _ _ _ _).mpr (Or.inl new)
    · rcases cover q old with done | more
      · exact Or.inl done
      · right; rw [replay]; exact (candidate_cons _ _ _ _ _).mpr (Or.inr more)

theorem edges_preserve_valid {n : Nat} (labels : Fin n → Node) (s : State n)
    (edges : Graph n) (valid : Valid labels s) : Valid labels (addEdges s edges) := by
  refine ⟨?_,?_,?_⟩
  · intro q hq
    exact candidate_mono labels s.graph (edges ++ s.graph) s.sources
      (fun e he => List.mem_append_right _ he) q (valid.1 q hq)
  · intro q hq; exact hq
  · intro q hq; exact Or.inr hq

theorem flush_exact {n : Nat} (labels : Fin n → Node) (s : State n)
    (valid : Valid labels s) (q : Endpoint) :
    q ∈ (flush labels s).candidates ↔ Candidate labels s.graph s.sources q := by
  simp only [flush,Run1RequestProtocol.mem_merge,grouped_exact]
  exact ⟨fun h => h.elim (valid.2.1 q) (valid.1 q),fun h =>
    (valid.2.2 q h).elim Or.inr Or.inl⟩

theorem flush_preserves_valid {n : Nat} (labels : Fin n → Node) (s : State n)
    (valid : Valid labels s) : Valid labels (flush labels s) := by
  refine ⟨?_,?_,?_⟩
  · intro q h; exact (flush_exact labels s valid q).mp h
  · simp [flush,replayRows,Candidate,reference]
  · intro q h; exact Or.inl ((flush_exact labels s valid q).mpr h)

inductive Event (n : Nat) where
  | source (row : Row n)
  | edges (graph : Graph n)
  | flush
  deriving Repr

def step {n : Nat} (labels : Fin n → Node) (s : State n) : Event n → State n
  | .source r => addSource s r
  | .edges g => addEdges s g
  | .flush => flush labels s

def run {n : Nat} (labels : Fin n → Node) (g : Graph n) (events : List (Event n)) : State n :=
  events.foldl (step labels) (initial g)

theorem step_preserves_valid {n : Nat} (labels : Fin n → Node) (s : State n)
    (e : Event n) (h : Valid labels s) : Valid labels (step labels s e) := by
  cases e with
  | source r => exact source_preserves_valid labels s r h
  | edges g => exact edges_preserve_valid labels s g h
  | flush => exact flush_preserves_valid labels s h

theorem every_trace_valid {n : Nat} (labels : Fin n → Node) (g : Graph n)
    (events : List (Event n)) : Valid labels (run labels g events) := by
  have fold : ∀ (es : List (Event n)) s, Valid labels s →
      Valid labels (es.foldl (step labels) s) := by
    intro es
    induction es with
    | nil => intro s hs; exact hs
    | cons e es ih => intro s hs; exact ih _ (step_preserves_valid labels s e hs)
  exact fold events (initial g) (initial_valid labels g)

/-- Late graph/source arrivals are covered at the next completed turn. This
allows any serialized finite interleaving. Overlapping JVM waves and latest-edge
insertion coverage are a separate live-traversal simulation obligation. -/
theorem trace_flush_exact {n : Nat} (labels : Fin n → Node) (g : Graph n)
    (events : List (Event n)) (q : Endpoint) :
    q ∈ (flush labels (run labels g events)).candidates ↔
      Candidate labels (run labels g events).graph (run labels g events).sources q :=
  flush_exact labels _ (every_trace_valid labels g events) q

/-- R subtraction is stutter for already committed members and preserves
unrelated requests. Raw delivery counts and final R=Need are not claimed. -/
def uncommitted (r candidates : List Endpoint) : List Endpoint :=
  candidates.filter fun q => !(decide (q ∈ r))

theorem committed_filter_stutter (r candidates : List Endpoint) (q : Endpoint) :
    q ∈ Run1RequestProtocol.merge (uncommitted r candidates) r ↔
    q ∈ candidates ∨ q ∈ r := by
  simp only [Run1RequestProtocol.mem_merge,uncommitted,List.mem_filter]
  by_cases h : q ∈ r <;> simp [h]

/-- Union at one source is exact, without mixing another origin's marks. This
refines per-key pending/source set union from the registration-list oracle. -/
theorem same_source_union {n : Nat} (labels : Fin n → Node) (g : Graph n)
    (source : Fin n) (a b : List Mark) (rest : List (Row n)) (q : Endpoint) :
    Candidate labels g (⟨source,a⟩::⟨source,b⟩::rest) q ↔
    Candidate labels g (⟨source,Run1RequestProtocol.merge a b⟩::rest) q := by
  simp only [Candidate,mem_reference,List.mem_cons]
  constructor
  · rintro ⟨r,(eq | eq | hr),v,path,label,mark⟩
    · subst r
      exact ⟨_,Or.inl rfl,v,path,label,(Run1RequestProtocol.mem_merge _ _ _).mpr (Or.inl mark)⟩
    · subst r
      exact ⟨_,Or.inl rfl,v,path,label,(Run1RequestProtocol.mem_merge _ _ _).mpr (Or.inr mark)⟩
    · exact ⟨r,Or.inr hr,v,path,label,mark⟩
  · rintro ⟨r,(eq | hr),v,path,label,mark⟩
    · subst r
      rcases (Run1RequestProtocol.mem_merge _ _ _).mp mark with ha | hb
      · exact ⟨_,Or.inl rfl,v,path,label,ha⟩
      · exact ⟨_,Or.inr (Or.inl rfl),v,path,label,hb⟩
    · exact ⟨r,Or.inr (Or.inr hr),v,path,label,mark⟩

def freshMarks (old offered : List Mark) : List Mark :=
  (Run1RequestProtocol.merge offered []).filter fun t => !(decide (t ∈ old))

theorem fresh_source_decomposition (old offered : List Mark) (t : Mark) :
    t ∈ Run1RequestProtocol.merge offered old ↔
    t ∈ old ∨ t ∈ freshMarks old offered := by
  simp only [Run1RequestProtocol.mem_merge,freshMarks,List.mem_filter,List.not_mem_nil,or_false]
  by_cases h : t ∈ old <;> simp [h]

/-- Fresh-delta replay plus old committed source has exactly cumulative support. -/
theorem fresh_delta_exact {n : Nat} (labels : Fin n → Node) (g : Graph n)
    (source : Fin n) (old offered : List Mark) (q : Endpoint) :
    Candidate labels g [⟨source,Run1RequestProtocol.merge offered old⟩] q ↔
    Candidate labels g [⟨source,old⟩,⟨source,freshMarks old offered⟩] q := by
  simp only [Candidate,reference,List.flatMap_cons,List.flatMap_nil,List.mem_append,List.not_mem_nil,or_false]
  simp only [List.mem_flatMap,BottomUpConcreteStaticNeeds.mem_emitAt,fresh_source_decomposition]
  constructor
  · rintro ⟨v,hv,label,(old | fresh)⟩
    · exact Or.inl ⟨v,hv,label,old⟩
    · exact Or.inr ⟨v,hv,label,fresh⟩
  · rintro (⟨v,hv,label,old⟩ | ⟨v,hv,label,fresh⟩)
    · exact ⟨v,hv,label,Or.inl old⟩
    · exact ⟨v,hv,label,Or.inr fresh⟩

/-- Fixed-graph cost: multiplicity of independent single-start visits. -/
def multiplicity {n : Nat} (g : Graph n) (v : Fin n) : List (Fin n) → Nat
  | [] => 0
  | s::ss => (if v ∈ BottomUpConcreteStaticNeeds.fastNodes g s then 1 else 0) + multiplicity g v ss

theorem multiplicity_positive {n : Nat} (g : Graph n) (v : Fin n) (starts : List (Fin n)) :
    0 < multiplicity g v starts ↔ v ∈ multiNodes g starts := by
  rw [multi_nodes_exact]
  induction starts with
  | nil => simp [multiplicity]
  | cons s ss ih =>
    by_cases h : v ∈ BottomUpConcreteStaticNeeds.fastNodes g s
    · have path := (fast_nodes_reach g s v).mp h
      simp only [multiplicity,h,if_true]
      constructor
      · intro _; exact ⟨s,by simp,path⟩
      · intro _; omega
    · have no : ¬ Reach g s v := fun path => h ((fast_nodes_reach g s v).mpr path)
      simp [multiplicity,h,List.mem_cons,no,← ih]

/-- Node weights can charge each node's complete edge fanout. For the runtime
static visited-set claim, Base-node weights MUST be zero: distinct static
parents may repeatedly offer the same Base terminal. Search/grouping
construction overhead is excluded here and charged separately below. -/
def sharedVisitCost {n : Nat} (g : Graph n) (starts : List (Fin n)) (weight : Fin n → Nat) :
    List (Fin n) → Nat
  | [] => 0
  | v::vs => (if v ∈ multiNodes g starts then weight v else 0) + sharedVisitCost g starts weight vs

def referenceVisitCost {n : Nat} (g : Graph n) (starts : List (Fin n)) (weight : Fin n → Nat) :
    List (Fin n) → Nat
  | [] => 0
  | v::vs => weight v * multiplicity g v starts + referenceVisitCost g starts weight vs

theorem fixed_graph_visit_cost_nonincrease {n : Nat} (g : Graph n) (starts : List (Fin n))
    (weight : Fin n → Nat) (nodes : List (Fin n)) :
    sharedVisitCost g starts weight nodes ≤ referenceVisitCost g starts weight nodes := by
  induction nodes with
  | nil => simp [sharedVisitCost,referenceVisitCost]
  | cons v vs ih =>
    have localBound : (if v ∈ multiNodes g starts then weight v else 0) ≤
        weight v * multiplicity g v starts := by
      split
      · exact Nat.le_mul_of_pos_right _ ((multiplicity_positive g v starts).mpr ‹_›)
      · exact Nat.zero_le _
    simp only [sharedVisitCost,referenceVisitCost]
    exact Nat.add_le_add localBound ih

/-- A conservative charge for this list-based equality routine: membership
comparisons can inspect both lists' product, including collision-heavy groups.
A JVM sorted-array comparator has a separate linear adapter cost. -/
def equalityCellCharge (a b : List Mark) : Nat :=
  1 + a.length + b.length + 2 * a.length * b.length

def insertEqualityCharge {n : Nat} (r : Row n) : List (Batch n) → Nat
  | [] => 0
  | b::bs => if r.marks.isEmpty then 0 else
    equalityCellCharge r.marks b.marks +
      if equalMarks r.marks b.marks then 0 else insertEqualityCharge r bs

def groupingEqualityCharge {n : Nat} : List (Row n) → Nat
  | [] => 0
  | r::rs => groupingEqualityCharge rs + insertEqualityCharge r (group rs)

/-- Honest total-cost condition: visit saving must pay equality work. No
universal live-schedule or wall-clock nonincrease follows from visit sharing. -/
theorem cost_with_grouping {n : Nat} (g : Graph n) (starts : List (Fin n))
    (weight : Fin n → Nat) (nodes : List (Fin n)) (charge saving : Nat)
    (gap : sharedVisitCost g starts weight nodes + saving ≤ referenceVisitCost g starts weight nodes)
    (pays : charge ≤ saving) :
    sharedVisitCost g starts weight nodes + charge ≤ referenceVisitCost g starts weight nodes := by
  omega

/-- Runtime static scans use weight zero at Base terminals; this does not assert
one callback/message per Base endpoint. -/
def staticWeight {n : Nat} (labels : Fin n → Node) (weight : Fin n → Nat) (v : Fin n) : Nat :=
  match labels v with
  | .base .. => 0
  | .staticField .. => weight v

theorem runtime_static_scan_nonincrease {n : Nat} (labels : Fin n → Node) (g : Graph n)
    (starts : List (Fin n)) (weight : Fin n → Nat) (nodes : List (Fin n)) :
    sharedVisitCost g starts (staticWeight labels weight) nodes ≤
      referenceVisitCost g starts (staticWeight labels weight) nodes :=
  fixed_graph_visit_cost_nonincrease g starts _ nodes

/-- Stable-route arithmetic only: fewer replay traversals cost no more at a
fixed per-traversal charge. Equality/group setup costs must be added separately. -/
theorem stable_route_repeat_nonincrease (batches notifications perReplay : Nat)
    (h : batches ≤ notifications) : batches * perReplay ≤ notifications * perReplay :=
  Nat.mul_le_mul_right perReplay h

theorem stable_route_repeat_strict (batches notifications perReplay : Nat)
    (h : batches < notifications) (positive : 0 < perReplay) :
    batches * perReplay < notifications * perReplay :=
  Nat.mul_lt_mul_of_pos_right h positive

namespace Controls
def owner : Run1Requests.Owner := ⟨1,0,[1]⟩
def labels : Fin 3 → Node := fun i => match i.val with
  | 0 => .base owner 0
  | 1 => .staticField owner 1 4
  | _ => .staticField owner 1 5

def graph : Graph 3 := [(0,1),(1,2)]
def cycle : Graph 3 := graph ++ [(2,1)]
def rows : List (Row 3) := [⟨1,[7,8]⟩,⟨2,[8,7,8]⟩]
def q7 : Endpoint := ⟨owner,0,7⟩
def q8 : Endpoint := ⟨owner,0,8⟩
def after (es : List (Event 3)) : State 3 := flush labels (run labels [] es)
def graphFirst : List (Event 3) := [.edges graph,.source ⟨2,[7]⟩]
def sourceFirst : List (Event 3) := [.source ⟨2,[7]⟩,.flush,.edges graph]
def lateBehindVisited : List (Event 3) :=
  [.edges [(1,2)],.source ⟨2,[7]⟩,.flush,.edges [(0,1)]]
def postSnapshotSource : List (Event 3) :=
  [.edges graph,.source ⟨2,[7]⟩,.flush,.source ⟨2,[8]⟩]

def disconnectedLabels : Fin 4 → Node := fun i => match i.val with
  | 0 => .base owner 0
  | 1 => .staticField owner 1 4
  | 2 => .base owner 1
  | _ => .staticField owner 1 5

def disconnected : Graph 4 := [(0,1),(2,3)]
def separate : List (Row 4) := [⟨1,[7]⟩,⟨3,[8]⟩]
def wrongCrossProduct : List (Batch 4) := [⟨[1,3],[7,8]⟩]

/-- Equal-mark grouping is necessary; merging unrelated marks at disconnected
starts introduces a Base/mark pair absent from the reference. -/
theorem distinct_mark_cross_product_unsound :
    q8 ∈ batched disconnectedLabels disconnected wrongCrossProduct ∧
    q8 ∉ reference disconnectedLabels disconnected separate := by decide +kernel

/-- Actual static overlap pays this explicit equality charge in the chosen
finite cohort. It is a witness, not a universal total-cost theorem. -/
theorem strict_static_saving_with_equality :
    sharedVisitCost graph [1,2] (staticWeight labels (fun _ => 10)) [0,1,2] +
      groupingEqualityCharge ([⟨1,[7]⟩,⟨2,[7]⟩] : List (Row 3)) <
    referenceVisitCost graph [1,2] (staticWeight labels (fun _ => 10)) [0,1,2] := by
  decide +kernel

/-- Small overlap can fail to amortize equality work. -/
theorem grouping_overhead_can_exceed_saving :
    sharedVisitCost graph [1,2] (fun _ => 1) [0,1,2] +
      groupingEqualityCharge ([⟨1,[7]⟩,⟨2,[7]⟩] : List (Row 3)) >
    referenceVisitCost graph [1,2] (fun _ => 1) [0,1,2] := by
  decide +kernel

def checks : List Bool := [
  equalMarks [7,8] [8,7,8],
  !equalMarks [7] [8],
  (group rows).length == 1,
  (group ([⟨1,[]⟩] : List (Row 3))).length == 0,
  (group separate).length == 2,
  decide (q7 ∈ batched labels graph (group rows)),
  decide (q8 ∈ reference labels graph rows),
  multiNodes graph [1,2] == [0,1,2],
  multiNodes cycle [1,2] == [0,1,2],
  multiNodes graph [] == [],
  (multiPath graph [1,2] 0).isSome,
  (multiPath [] ([2] : List (Fin 3)) 0).isNone,
  (after graphFirst).candidates == (after sourceFirst).candidates,
  decide (q7 ∈ (after lateBehindVisited).candidates),
  decide (q7 ∈ (after postSnapshotSource).candidates),
  decide (q8 ∈ (after postSnapshotSource).candidates),
  freshMarks [7] [7,8,8] == [8],
  decide (q7 ∈ reference labels graph [⟨2,[7]⟩,⟨2,[8]⟩]),
  decide (q8 ∈ reference labels graph [⟨2,Run1RequestProtocol.merge [7] [8]⟩]),
  uncommitted [q7] [q7,q8] == [q8],
  Run1RequestProtocol.merge (uncommitted [q7] [q7,q8]) [q7] == [q8,q7],
  referenceVisitCost graph [1,2] (fun _ => 1) [0,1,2] == 5,
  sharedVisitCost graph [1,2] (fun _ => 1) [0,1,2] == 3,
  groupingEqualityCharge ([⟨1,[7]⟩,⟨2,[7]⟩] : List (Row 3)) == 5,
  decide (sharedVisitCost graph [1,2] (fun _ => 1) [0,1,2] + 5 >
    referenceVisitCost graph [1,2] (fun _ => 1) [0,1,2]),
  decide (sharedVisitCost graph [1,2] (staticWeight labels (fun _ => 10)) [0,1,2] + 5 <
    referenceVisitCost graph [1,2] (staticWeight labels (fun _ => 10)) [0,1,2]),
  decide (q8 ∈ batched disconnectedLabels disconnected wrongCrossProduct),
  decide (q8 ∉ reference disconnectedLabels disconnected separate)]

theorem controls_pass : checks.all id = true := by decide +kernel
#guard checks.all id
end Controls
end ApSpec.StaticNeedReplayBatch
