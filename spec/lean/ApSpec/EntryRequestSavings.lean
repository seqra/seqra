/-
  Restricted cost/endpoint model against ORDINARY INCREMENTAL requests.

  This is NOT a comparison against full-source replay. Transparent relays do
  no AP transformations, kills, mark conversion, observations, or ND joins.
  Entry offers, original mappings and fixed-CG reachability are assumed valid.
  A separate runtime/AP/CFG refinement must realise these cohorts.

  Both counts concern DYNAMIC ORIGINAL-SLOT needs not already covered by a
  frozen/transitive request plan or prior active membership. Historical ab6
  already suppresses unchanged statically covered field checks; this model
  does not claim h*k savings for those checks. A possible uncovered cohort is
  g FLOW through h relays followed by `f = g; check f/T`, with no frozen g/T,
  class-wide T or root-wide T contributor. The AP/frontend bridge is unproved.

  Units are explicit: retained static (owner,mark) memberships, forwarded mark
  cells, and one envelope per whole-batch hop. No theorem bounds total analyzer
  memory/time, global directory work, candidate/SCC checks, actual facts or
  summaries. Lists are executable finite oracles, not runtime data structures.
-/
import ApSpec.Run1RequestProtocol

namespace ApSpec.EntryRequestSavings
open ApSpec.Run1RequestProtocol

variable {A B : Type} [DecidableEq A]

def normalise (xs : List A) : List A := merge xs []

@[simp] theorem mem_normalise (xs : List A) (x : A) :
    x ∈ normalise xs ↔ x ∈ xs := by simp [normalise]

/-- The raw source/endpoint Cartesian product, for counting actual attempts.
    Unlike a set union this preserves attempts from different reader sources. -/
def rectangle : List A → List B → List (A × B)
  | [],_ => []
  | x::xs,ys => ys.map (fun y => (x,y)) ++ rectangle xs ys

omit [DecidableEq A] in
@[simp] theorem mem_rectangle (xs : List A) (ys : List B) (x : A) (y : B) :
    (x,y) ∈ rectangle xs ys ↔ x ∈ xs ∧ y ∈ ys := by
  induction xs with
  | nil => simp [rectangle]
  | cons a xs ih =>
    simp only [rectangle,List.mem_append,List.mem_map,Prod.mk.injEq,ih,List.mem_cons]
    constructor
    · rintro (⟨b,hb,ha,hy⟩ | ⟨hx,hy⟩)
      · cases hy
        exact ⟨Or.inl ha.symm,hb⟩
      · exact ⟨Or.inr hx,hy⟩
    · rintro ⟨hx | hx,hy⟩
      · exact Or.inl ⟨y,hy,hx.symm,rfl⟩
      · exact Or.inr ⟨hx,hy⟩

omit [DecidableEq A] in
theorem rectangle_length (xs : List A) (ys : List B) :
    (rectangle xs ys).length = xs.length * ys.length := by
  induction xs with
  | nil => simp [rectangle]
  | cons a xs ih => simp [rectangle,ih,Nat.add_mul,Nat.add_comm]

abbrev Mark := Nat
abbrev Original := Nat
abbrev Endpoint := Original × Mark

def endpointUnion (original : Original) (marks : List Mark) : List Endpoint :=
  normalise (marks.map fun mark => (original,mark))

@[simp] theorem endpoint_union_exact (original target : Original)
    (marks : List Mark) (mark : Mark) :
    (target,mark) ∈ endpointUnion original marks ↔ target = original ∧ mark ∈ marks := by
  simp only [endpointUnion,mem_normalise,List.mem_map,Prod.mk.injEq]
  constructor
  · rintro ⟨t,ht,horiginal,hmark⟩
    cases hmark
    exact ⟨horiginal.symm,ht⟩
  · rintro ⟨rfl,ht⟩
    exact ⟨mark,ht,rfl,rfl⟩

/-- An ordinary incremental UNCOVERED-NEED chain merges at each owner. Each
    relay has one predecessor and one successor, so each distinct mark advances
    once. Already covered/active needs are outside this measured cohort. -/
def ordinaryChainEndpoint (h : Nat) (original : Original) (marks : List Mark) : List Endpoint :=
  match h with
  | 0 => endpointUnion original (normalise marks)
  | n+1 => ordinaryChainEndpoint n original (normalise marks)

def directChainEndpoint (original : Original) (marks : List Mark) : List Endpoint :=
  endpointUnion original (normalise marks)

theorem ordinary_chain_endpoint_exact (h : Nat) (original target : Original)
    (marks : List Mark) (mark : Mark) :
    (target,mark) ∈ ordinaryChainEndpoint h original marks ↔
      target = original ∧ mark ∈ marks := by
  induction h generalizing marks with
  | zero => simp [ordinaryChainEndpoint]
  | succ h ih => simp [ordinaryChainEndpoint,ih]

theorem chain_endpoint_unions_equal (h : Nat) (original target : Original)
    (marks : List Mark) (mark : Mark) :
    (target,mark) ∈ ordinaryChainEndpoint h original marks ↔
      (target,mark) ∈ directChainEndpoint original marks := by
  simp [ordinary_chain_endpoint_exact,directChainEndpoint]

/-- Constructive endpoint witness: no missing mark or different original is
    introduced by direct delivery. -/
theorem chain_has_supplied_witness (h : Nat) (original : Original)
    (marks : List Mark) (mark : Mark) (supplied : mark ∈ marks) :
    (original,mark) ∈ ordinaryChainEndpoint h original marks ∧
    (original,mark) ∈ directChainEndpoint original marks := by
  constructor
  · exact (ordinary_chain_endpoint_exact _ _ _ _ _).mpr ⟨rfl,supplied⟩
  · simpa [directChainEndpoint] using supplied

def membershipCells : List (List Mark) → Nat
  | [] => 0
  | row::rows => row.length + membershipCells rows

theorem replicate_cells (owners : Nat) (marks : List Mark) :
    membershipCells (List.replicate owners marks) = owners * marks.length := by
  induction owners with
  | zero => simp [membershipCells]
  | succ owners ih => simp [List.replicate_succ,membershipCells,ih,Nat.add_mul,Nat.add_comm]

/-- h counts intermediate static relay owners, excluding source and endpoint.
    Endpoint Base storage/admission is common and excluded from both counts. -/
def ordinaryStaticRows (h : Nat) (marks : List Mark) : List (List Mark) :=
  List.replicate (h+1) (normalise marks)

def directStaticRows (marks : List Mark) : List (List Mark) := [normalise marks]

theorem ordinary_static_memberships (h : Nat) (marks : List Mark) :
    membershipCells (ordinaryStaticRows h marks) = (h+1) * (normalise marks).length :=
  replicate_cells _ _

theorem direct_static_memberships (marks : List Mark) :
    membershipCells (directStaticRows marks) = (normalise marks).length := by
  simp [directStaticRows,membershipCells]

/-- Per-edge whole-batch transfers, source->relay(s)->endpoint. Both procedures
    submit every distinct mark once at the endpoint; neither repeats old work. -/
def ordinaryChainMarkCells (h k : Nat) : Nat := (h+1)*k
def directChainMarkCells (k : Nat) : Nat := k
def ordinaryChainEnvelopes (h : Nat) : Nat := h+1
def directChainEnvelopes : Nat := 1

theorem chain_mark_cell_difference (h k : Nat) :
    ordinaryChainMarkCells h k = directChainMarkCells k + h*k := by
  simp [ordinaryChainMarkCells,directChainMarkCells,Nat.add_mul,Nat.add_comm]

theorem strict_chain_mark_saving (h k : Nat) (hasRelay : 0 < h) (hasMark : 0 < k) :
    directChainMarkCells k < ordinaryChainMarkCells h k := by
  rw [chain_mark_cell_difference]
  have positive : 0 < h*k := Nat.mul_pos hasRelay hasMark
  omega

theorem strict_chain_membership_saving (h : Nat) (marks : List Mark)
    (hasRelay : 0 < h) (hasMark : 0 < (normalise marks).length) :
    membershipCells (directStaticRows marks) < membershipCells (ordinaryStaticRows h marks) := by
  rw [direct_static_memberships,ordinary_static_memberships]
  exact strict_chain_mark_saving h _ hasRelay hasMark

theorem strict_chain_envelope_saving (h : Nat) (hasRelay : 0 < h) :
    directChainEnvelopes < ordinaryChainEnvelopes h := by
  simp [directChainEnvelopes,ordinaryChainEnvelopes]
  omega

namespace SharedBottleneck

/- These events count logical single-mark offer attempts BEFORE optional
   destination-mailbox combining. They are not physical-message counts. -/

/-- r readers each offer the SAME mark to the SAME ordinary relay slot.
    Ordinary incremental request union at H admits that mark only once, then
    forwards it to p distinct caller originals. Reader-to-H offers still cost r.
    The same uncovered-original-slot condition applies: e.g. distinct reader
    checks on f_i after f_i=g, with g/T absent from frozen/root/class coverage. -/
inductive OrdinaryDelivery where
  | toRelay (reader : Nat)
  | toOriginal (original : Original)
  deriving Repr, DecidableEq

/-- The single-mark ordinary incremental algorithm. `seen` is precisely H's
    admitted membership for that mark. An old membership never retransmits. -/
def runOrdinaryReaders : Nat → Nat → Bool → List OrdinaryDelivery
  | 0,_,_ => []
  | r+1,p,seen =>
      .toRelay r ::
        ((if seen then [] else (List.range p).map OrdinaryDelivery.toOriginal) ++
          runOrdinaryReaders r p true)

def ordinaryDeliveries (r p : Nat) : List OrdinaryDelivery := runOrdinaryReaders r p false

theorem admitted_mark_never_retransmits (r p : Nat) :
    (runOrdinaryReaders r p true).length = r := by
  induction r with
  | zero => rfl
  | succ r ih => simp [runOrdinaryReaders,ih]

/-- The proposed table has distinct reader-owned requests and p entry offers
    at H. Its exact-once rule is PER READER: it sends the same mark r*p times.
    Endpoint union removes these duplicates but cannot remove attempt cost. -/
def directDeliveries (r p : Nat) : List (Nat × Original) :=
  rectangle (List.range r) (List.range p)

theorem ordinary_delivery_count (r p : Nat) (hasReader : 0 < r) :
    (ordinaryDeliveries r p).length = r+p := by
  cases r with
  | zero => omega
  | succ r =>
    simp [ordinaryDeliveries,runOrdinaryReaders,admitted_mark_never_retransmits]
    omega

theorem direct_delivery_count (r p : Nat) :
    (directDeliveries r p).length = r*p := by
  simp [directDeliveries,rectangle_length]

def ordinaryEndpoints : Nat → Nat → Mark → List Endpoint
  | 0,_,_ => []
  | _+1,p,mark => normalise ((List.range p).map fun original => (original,mark))

def directEndpoints (r p : Nat) (mark : Mark) : List Endpoint :=
  normalise ((directDeliveries r p).map fun (_,original) => (original,mark))

def projectOrdinary (mark : Mark) : List OrdinaryDelivery → List Endpoint
  | [] => []
  | .toRelay _ :: rest => projectOrdinary mark rest
  | .toOriginal original :: rest => (original,mark) :: projectOrdinary mark rest

@[simp] theorem project_append (mark : Mark) (xs ys : List OrdinaryDelivery) :
    projectOrdinary mark (xs ++ ys) = projectOrdinary mark xs ++ projectOrdinary mark ys := by
  induction xs with
  | nil => rfl
  | cons event xs ih => cases event <;> simp [projectOrdinary,ih]

@[simp] theorem project_originals (mark : Mark) (originals : List Original) :
    projectOrdinary mark (originals.map OrdinaryDelivery.toOriginal) =
      originals.map (fun original => (original,mark)) := by
  induction originals with
  | nil => rfl
  | cons original rest ih => simp [projectOrdinary,ih]

theorem admitted_mark_no_new_endpoints (r p : Nat) (mark : Mark) :
    projectOrdinary mark (runOrdinaryReaders r p true) = [] := by
  induction r with
  | zero => rfl
  | succ r ih => simp [runOrdinaryReaders,projectOrdinary,ih]

/-- The endpoint oracle is EXACTLY the union of the executable incremental
    algorithm's actual endpoint deliveries, not a separate desired output. -/
theorem ordinary_endpoint_from_execution (r p : Nat) (mark : Mark) :
    normalise (projectOrdinary mark (ordinaryDeliveries r p)) = ordinaryEndpoints r p mark := by
  cases r with
  | zero => rfl
  | succ r =>
    change normalise (projectOrdinary mark
      ((OrdinaryDelivery.toRelay r) ::
        ((List.range p).map OrdinaryDelivery.toOriginal ++ runOrdinaryReaders r p true))) =
      normalise ((List.range p).map fun original => (original,mark))
    rw [projectOrdinary,project_append,project_originals,admitted_mark_no_new_endpoints,
      List.append_nil]

theorem direct_endpoint_exact (r p : Nat) (mark : Mark) (original : Original) (t : Mark) :
    (original,t) ∈ directEndpoints r p mark ↔ 0 < r ∧ original < p ∧ t = mark := by
  simp only [directEndpoints,mem_normalise,List.mem_map]
  constructor
  · rintro ⟨⟨reader,parent⟩,hpair,heq⟩
    have members := (mem_rectangle _ _ _ _).mp hpair
    have readerBound : reader < r := List.mem_range.mp members.1
    have parentBound : parent < p := List.mem_range.mp members.2
    cases heq
    exact ⟨by omega,parentBound,rfl⟩
  · rintro ⟨readerExists,parentBound,rfl⟩
    refine ⟨(0,original),?_,rfl⟩
    exact (mem_rectangle _ _ _ _).mpr
      ⟨List.mem_range.mpr readerExists,List.mem_range.mpr parentBound⟩

theorem endpoint_unions_equal (r p : Nat) (mark : Mark) (original : Original) (t : Mark) :
    (original,t) ∈ ordinaryEndpoints r p mark ↔
      (original,t) ∈ directEndpoints r p mark := by
  rw [direct_endpoint_exact]
  cases r with
  | zero => simp only [ordinaryEndpoints,List.not_mem_nil,Nat.lt_irrefl,false_and]
  | succ r =>
    simp only [ordinaryEndpoints,
      mem_normalise,List.mem_map,Nat.zero_lt_succ,true_and]
    constructor
    · rintro ⟨parent,hparent,heq⟩
      have bound := List.mem_range.mp hparent
      cases heq
      exact ⟨bound,rfl⟩
    · rintro ⟨bound,rfl⟩
      exact ⟨original,List.mem_range.mpr bound,rfl⟩

/-- Constructive common endpoint witness; reader 0 justifies direct delivery. -/
theorem endpoint_witness (r p : Nat) (mark : Mark) (hasReader : 0 < r)
    (original : Original) (hasOriginal : original < p) :
    (original,mark) ∈ ordinaryEndpoints r p mark ∧
    (original,mark) ∈ directEndpoints r p mark := by
  have hd := (direct_endpoint_exact r p mark original mark).mpr
    ⟨hasReader,hasOriginal,rfl⟩
  exact ⟨(endpoint_unions_equal _ _ _ _ _).mpr hd,hd⟩

theorem direct_can_do_more_work :
    (ordinaryDeliveries 4 4).length = 8 ∧
    (directDeliveries 4 4).length = 16 ∧
    (ordinaryDeliveries 4 4).length < (directDeliveries 4 4).length := by decide

end SharedBottleneck

namespace Controls

def checks : List Bool :=
  [ membershipCells (ordinaryStaticRows 4 [7,8,9]) == 15,
    membershipCells (directStaticRows [7,8,9]) == 3,
    ordinaryChainMarkCells 4 3 == 15,
    directChainMarkCells 3 == 3,
    ordinaryChainEnvelopes 4 == 5,
    directChainEnvelopes == 1,
    ordinaryChainEndpoint 4 11 [7,7,8] == directChainEndpoint 11 [7,7,8],
    (SharedBottleneck.ordinaryDeliveries 4 4).length == 8,
    (SharedBottleneck.directDeliveries 4 4).length == 16,
    SharedBottleneck.ordinaryEndpoints 4 4 7 == SharedBottleneck.directEndpoints 4 4 7,
    SharedBottleneck.ordinaryEndpoints 0 4 7 == [],
    SharedBottleneck.directEndpoints 0 4 7 == [],
    decide ((3,7) ∈ SharedBottleneck.directEndpoints 4 4 7),
    decide ((4,7) ∉ SharedBottleneck.directEndpoints 4 4 7) ]

theorem checks_pass : checks.all id = true := by decide

end Controls
end ApSpec.EntryRequestSavings
