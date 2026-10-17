/-
  Mark-only frozen static requests for bottom-up run-1 transport.

  Owners are indices into an explicitly supplied, fully resolved finite call
  graph (an index may encode a full entry/context key). The graph and local
  effective descriptors are inputs; frontend collection/resolution is not
  proved here. No field-read plan or selected-field set occurs in lookup.

  The finite oracle saturates backwards from each descriptor owner, retaining
  executable call-path certificates. Root/class descriptors stay symbolic;
  only a single legal actual slot is resolved by a lookup. This is a proof
  oracle, not the production SCC implementation or a field-by-mark table.
-/
import ApSpec.Run1Requests
import ApSpec.Run1PrecomputedRequests

namespace ApSpec.BottomUpStaticRequests

open ApSpec
abbrev Slot := Run1Requests.Slot
abbrev Graph := Run1Requests.Graph

/-! ## A finite closure whose members carry executable path witnesses. -/

/-- A caller-to-descendant path. Its constructors contain actual graph edges. -/
inductive CallPath {n : Nat} (g : Graph n) : Fin n → Fin n → Type where
  | here (owner : Fin n) : CallPath g owner owner
  | prepend {caller callee source : Fin n} (edge : (caller,callee) ∈ g)
      (rest : CallPath g callee source) : CallPath g caller source

def CallPath.nodes {n : Nat} {g : Graph n} {a b : Fin n} :
    CallPath g a b → List (Fin n)
  | .here owner => [owner]
  | .prepend (caller := caller) _ rest => caller :: rest.nodes

theorem CallPath.sound {n : Nat} {g : Graph n} {a b : Fin n}
    (p : CallPath g a b) : Run1Requests.Reaches g b a := by
  induction p with
  | here => exact .seed
  | prepend edge _ ih => exact .call edge ih

abbrev PathEntry {n : Nat} (g : Graph n) (source : Fin n) :=
  (caller : Fin n) × CallPath g caller source

def findPath {n : Nat} {g : Graph n} {source : Fin n}
    (entries : List (PathEntry g source)) (caller : Fin n) :
    Option (CallPath g caller source) :=
  match entries with
  | [] => none
  | entry :: rest =>
      if h : entry.1 = caller then some (h ▸ entry.2) else findPath rest caller

theorem findPath_cons_old {n : Nat} {g : Graph n} {source caller : Fin n}
    (entry : PathEntry g source) (rest : List (PathEntry g source))
    (h : (findPath rest caller).isSome = true) :
    (findPath (entry :: rest) caller).isSome = true := by
  simp only [findPath]
  split
  · rfl
  · exact h

theorem findPath_cons_here {n : Nat} {g : Graph n} {source : Fin n}
    (entry : PathEntry g source) (rest : List (PathEntry g source)) :
    (findPath (entry :: rest) entry.1).isSome = true := by
  simp [findPath]

/-- Search real incoming edges, rather than outbound-object references. -/
def extension {n : Nat} {g : Graph n} {source : Fin n}
    (entries : List (PathEntry g source)) (caller : Fin n)
    (edges : List {e : Fin n × Fin n // e ∈ g}) :
    Option (CallPath g caller source) :=
  match edges with
  | [] => none
  | edge :: rest =>
      if h : edge.1.1 = caller then
        match findPath entries edge.1.2 with
        | some path => some (.prepend (h ▸ edge.2) path)
        | none => extension entries caller rest
      else extension entries caller rest

theorem extension_none {n : Nat} {g : Graph n} {source : Fin n}
    (entries : List (PathEntry g source)) (caller : Fin n)
    (edges : List {e : Fin n × Fin n // e ∈ g})
    (noneFound : extension entries caller edges = none) :
    ∀ edge ∈ edges, edge.1.1 = caller → findPath entries edge.1.2 = none := by
  induction edges with
  | nil => simp
  | cons head tail ih =>
      intro edge member same
      by_cases h : head.1.1 = caller
      · cases found : findPath entries head.1.2 with
        | none =>
            have tailNone : extension entries caller tail = none := by
              simpa [extension,h,found] using noneFound
            rcases List.mem_cons.mp member with eq | later
            · subst edge; exact found
            · exact ih tailNone edge later same
        | some path => simp [extension,h,found] at noneFound
      · have tailNone : extension entries caller tail = none := by
          simpa [extension,h] using noneFound
        rcases List.mem_cons.mp member with eq | later
        · subst edge; exact False.elim (h same)
        · exact ih tailNone edge later same

/-- A candidate contains both its remaining-owner membership and its path. -/
def nextEntry {n : Nat} {g : Graph n} {source : Fin n}
    (entries : List (PathEntry g source)) (remaining : List (Fin n)) :
    Option {entry : PathEntry g source // entry.1 ∈ remaining} :=
  match remaining with
  | [] => none
  | caller :: rest =>
      match extension entries caller g.attach with
      | some path => some ⟨⟨caller,path⟩,by simp⟩
      | none => (nextEntry entries rest).map fun candidate =>
          ⟨candidate.1,by simp [candidate.2]⟩

theorem nextEntry_none {n : Nat} {g : Graph n} {source : Fin n}
    (entries : List (PathEntry g source)) (remaining : List (Fin n))
    (h : nextEntry entries remaining = none) :
    ∀ caller ∈ remaining, extension entries caller g.attach = none := by
  induction remaining with
  | nil => simp
  | cons head tail ih =>
      cases found : extension entries head g.attach with
      | some path => simp [nextEntry,found] at h
      | none =>
          have tailNone : nextEntry entries tail = none := by
            simpa [nextEntry,found] using h
          intro caller member
          rcases List.mem_cons.mp member with eq | later
          · subst caller; exact found
          · exact ih tailNone caller later

def Covers {n : Nat} {g : Graph n} {source : Fin n}
    (remaining : List (Fin n)) (entries : List (PathEntry g source)) : Prop :=
  ∀ owner, owner ∈ remaining ∨ (findPath entries owner).isSome = true

/-- Direct inductions avoid the installed library's classical erase lemmas. -/
theorem mem_erase_neq {α : Type} [DecidableEq α] (xs : List α) (a b : α)
    (different : a ≠ b) : a ∈ xs.erase b ↔ a ∈ xs := by
  induction xs with
  | nil => rfl
  | cons head tail ih =>
      by_cases same : head = b
      · subst head
        rw [List.erase_cons_head]
        simp only [List.mem_cons]
        constructor
        · exact Or.inr
        · rintro (equal | later)
          · exact False.elim (different equal)
          · exact later
      · rw [List.erase_cons_tail (by simpa only [beq_iff_eq] using same)]
        simp only [List.mem_cons,ih]

theorem erase_length_lt {α : Type} [DecidableEq α] (xs : List α) (a : α)
    (member : a ∈ xs) : (xs.erase a).length < xs.length := by
  induction xs with
  | nil => cases member
  | cons head tail ih =>
      by_cases same : head = a
      · subst head
        rw [List.erase_cons_head]
        exact Nat.lt_succ_self _
      · have later : a ∈ tail := (List.mem_cons.mp member).resolve_left
          (fun eq => same eq.symm)
        rw [List.erase_cons_tail (by simpa only [beq_iff_eq] using same)]
        exact Nat.succ_lt_succ (ih later)

theorem covers_insert {n : Nat} {g : Graph n} {source : Fin n}
    (remaining : List (Fin n)) (entries : List (PathEntry g source))
    (cover : Covers remaining entries) (candidate : PathEntry g source) :
    Covers (remaining.erase candidate.1) (candidate :: entries) := by
  intro owner
  by_cases same : owner = candidate.1
  · subst owner; exact Or.inr (findPath_cons_here candidate entries)
  · rcases cover owner with pending | present
    · exact Or.inl ((mem_erase_neq remaining owner candidate.1 same).mpr pending)
    · exact Or.inr (findPath_cons_old candidate entries present)

structure Saturated {n : Nat} (g : Graph n) (source : Fin n)
    (initial : List (PathEntry g source)) where
  entries : List (PathEntry g source)
  includes : ∀ owner, (findPath initial owner).isSome = true →
    (findPath entries owner).isSome = true
  edgeClosed : ∀ caller callee, (caller,callee) ∈ g →
    (findPath entries callee).isSome = true →
    (findPath entries caller).isSome = true

/-- Each recursive call removes one remaining owner. No fuel truncation,
    excluded-middle search or choice is used; failure to add means closure. -/
def saturate {n : Nat} {g : Graph n} {source : Fin n}
    (remaining : List (Fin n)) (entries : List (PathEntry g source))
    (cover : Covers remaining entries) : Saturated g source entries :=
  match found : nextEntry entries remaining with
  | none => {
      entries := entries
      includes := fun _ h => h
      edgeClosed := by
        intro caller callee edge hasCallee
        rcases cover caller with pending | present
        · have noExtension := nextEntry_none entries remaining found caller pending
          have noPath := extension_none entries caller g.attach noExtension
            ⟨(caller,callee),edge⟩ (List.mem_attach _ _) rfl
          rw [noPath] at hasCallee
          cases hasCallee
        · exact present
    }
  | some candidate =>
      let later := saturate (remaining.erase candidate.1.1) (candidate.1 :: entries)
        (covers_insert remaining entries cover candidate.1)
      { entries := later.entries
        includes := fun owner h => later.includes owner (findPath_cons_old _ _ h)
        edgeClosed := later.edgeClosed }
termination_by remaining.length
decreasing_by
  exact erase_length_lt remaining candidate.1.1 candidate.2

def callClosure {n : Nat} (g : Graph n) (source : Fin n) :
    Saturated g source [⟨source,.here source⟩] :=
  saturate (g := g) (List.finRange n) [⟨source,.here source⟩]
    (fun owner => Or.inl (List.mem_finRange owner))

def inheritedPath {n : Nat} (g : Graph n) (source caller : Fin n) :
    Option (CallPath g caller source) :=
  findPath (callClosure g source).entries caller

theorem inheritedPath_seed {n : Nat} (g : Graph n) (source : Fin n) :
    (inheritedPath g source source).isSome = true := by
  apply (callClosure g source).includes
  simp [findPath]

theorem inheritedPath_call {n : Nat} (g : Graph n) (source caller callee : Fin n)
    (edge : (caller,callee) ∈ g)
    (present : (inheritedPath g source callee).isSome = true) :
    (inheritedPath g source caller).isSome = true :=
  (callClosure g source).edgeClosed caller callee edge present

theorem inheritedPath_complete {n : Nat} (g : Graph n) (source caller : Fin n)
    (reachable : Run1Requests.Reaches g source caller) :
    (inheritedPath g source caller).isSome = true := by
  induction reachable with
  | seed => exact inheritedPath_seed g source
  | call edge _ ih => exact inheritedPath_call g source _ _ edge ih

theorem inheritedPath_exact {n : Nat} (g : Graph n) (source caller : Fin n) :
    (inheritedPath g source caller).isSome = true ↔
      Run1Requests.Reaches g source caller := by
  constructor
  · intro found
    obtain ⟨path,_⟩ := Option.isSome_iff_exists.mp found
    exact path.sound
  · exact inheritedPath_complete g source caller

/-! ## Symbolic static scopes; resolve one legal actual slot on demand. -/

inductive Scope where
  | root
  | cls (id : Nat)
  | field (cls field : Nat)
  deriving DecidableEq, Repr

def Scope.matches : Scope → Slot → Bool
  | .root, .field _ _ => true
  | .cls c, .field c' _ => decide (c = c')
  | .field c f, .field c' f' => decide (c = c' ∧ f = f')
  | _, .base _ => false

structure Descriptor where
  scope : Scope
  mark : Mark
  deriving DecidableEq, Repr

/-- A descriptor plus its actual declaration and executable inheritance path.
    No selected-field/read-plan membership or per-field mark row is stored. -/
structure Inherited {n : Nat} (g : Graph n)
    (localNeeds : Fin n → List Descriptor) (caller : Fin n) where
  source : Fin n
  descriptor : Descriptor
  declared : descriptor ∈ localNeeds source
  path : CallPath g caller source

def compileDescriptors {n : Nat} (g : Graph n)
    (localNeeds : Fin n → List Descriptor) (caller : Fin n) :
    List (Inherited g localNeeds caller) :=
  (List.finRange n).flatMap fun source =>
    match inheritedPath g source caller with
    | none => []
    | some path => (localNeeds source).attach.map fun need =>
        ⟨source,need.1,need.2,path⟩

theorem compileDescriptors_complete {n : Nat} (g : Graph n)
    (localNeeds : Fin n → List Descriptor) (caller source : Fin n)
    (need : Descriptor) (declared : need ∈ localNeeds source)
    (reachable : Run1Requests.Reaches g source caller) :
    ∃ entry ∈ compileDescriptors g localNeeds caller,
      entry.source = source ∧ entry.descriptor = need := by
  have found := inheritedPath_complete g source caller reachable
  cases pathFound : inheritedPath g source caller with
  | none => simp [pathFound] at found
  | some path =>
      refine ⟨⟨source,need,declared,path⟩,?_,rfl,rfl⟩
      apply List.mem_flatMap.mpr
      refine ⟨source,List.mem_finRange source,?_⟩
      simp only [pathFound]
      exact List.mem_map.mpr ⟨⟨need,declared⟩,List.mem_attach _ _,rfl⟩

/-- A lookup witness contains the originating effective descriptor and the
    full caller-to-declaration route. It is computed, not chosen from Prop. -/
structure NeedWitness {n : Nat} (g : Graph n)
    (localNeeds : Fin n → List Descriptor) (caller : Fin n)
    (legalSlots : List Slot) (slot : Slot) (mark : Mark) where
  inherited : Inherited g localNeeds caller
  legal : slot ∈ legalSlots
  matching : inherited.descriptor.scope.matches slot = true
  sameMark : inherited.descriptor.mark = mark

def seekNeed {n : Nat} {g : Graph n} {localNeeds : Fin n → List Descriptor}
    {caller : Fin n} (entries : List (Inherited g localNeeds caller))
    (legalSlots : List Slot) (slot : Slot) (mark : Mark) :
    Option (NeedWitness g localNeeds caller legalSlots slot mark) :=
  match entries with
  | [] => none
  | entry :: rest =>
      if h : slot ∈ legalSlots ∧ entry.descriptor.scope.matches slot = true ∧
          entry.descriptor.mark = mark then
        some ⟨entry,h.1,h.2.1,h.2.2⟩
      else seekNeed rest legalSlots slot mark

theorem seekNeed_exact {n : Nat} {g : Graph n}
    {localNeeds : Fin n → List Descriptor} {caller : Fin n}
    (entries : List (Inherited g localNeeds caller))
    (legalSlots : List Slot) (slot : Slot) (mark : Mark) :
    (seekNeed entries legalSlots slot mark).isSome = true ↔
      slot ∈ legalSlots ∧ ∃ entry ∈ entries,
        entry.descriptor.scope.matches slot = true ∧ entry.descriptor.mark = mark := by
  induction entries with
  | nil => simp [seekNeed]
  | cons head tail ih =>
      by_cases h : slot ∈ legalSlots ∧ head.descriptor.scope.matches slot = true ∧
          head.descriptor.mark = mark
      · simp only [seekNeed,dif_pos h,Option.isSome_some,true_iff]
        exact ⟨h.1,head,by simp,h.2⟩
      · simp only [seekNeed,dif_neg h,ih]
        constructor
        · rintro ⟨legal,entry,member,matching,sameMark⟩
          exact ⟨legal,entry,List.mem_cons_of_mem _ member,matching,sameMark⟩
        · rintro ⟨legal,entry,member,matching,sameMark⟩
          rcases List.mem_cons.mp member with eq | later
          · subst entry; exact False.elim (h ⟨legal,matching,sameMark⟩)
          · exact ⟨legal,entry,later,matching,sameMark⟩

def lookupWitness {n : Nat} (g : Graph n)
    (localNeeds : Fin n → List Descriptor) (caller : Fin n)
    (legalSlots : List Slot) (slot : Slot) (mark : Mark) :=
  seekNeed (compileDescriptors g localNeeds caller) legalSlots slot mark

/-- A per-actual-slot logical read. legalSlots is the complete validated
    accepted coordinate domain, never a local-read set or old frozen S/F.
    `compileDescriptors` stays independent of legalSlots and the queried field.
    `merge` removes overlap/root/class
    duplicate marks; there is no stored field-by-mark Cartesian product. -/
def borrowMarks {n : Nat} (g : Graph n)
    (localNeeds : Fin n → List Descriptor) (caller : Fin n)
    (legalSlots : List Slot) (slot : Slot) : List Mark :=
  if slot ∈ legalSlots then
    Run1RequestProtocol.merge
      (((compileDescriptors g localNeeds caller).filter fun entry =>
        entry.descriptor.scope.matches slot).map fun entry => entry.descriptor.mark) []
  else []

theorem borrowMarks_nodup {n : Nat} (g : Graph n)
    (localNeeds : Fin n → List Descriptor) (caller : Fin n)
    (legalSlots : List Slot) (slot : Slot) :
    (borrowMarks g localNeeds caller legalSlots slot).Nodup := by
  unfold borrowMarks
  split
  · exact Run1RequestProtocol.merge_nodup _ _ (by simp)
  · simp

theorem borrowMarks_witness {n : Nat} (g : Graph n)
    (localNeeds : Fin n → List Descriptor) (caller : Fin n)
    (legalSlots : List Slot) (slot : Slot) (mark : Mark) :
    mark ∈ borrowMarks g localNeeds caller legalSlots slot ↔
      (lookupWitness g localNeeds caller legalSlots slot mark).isSome = true := by
  rw [lookupWitness,seekNeed_exact]
  by_cases legal : slot ∈ legalSlots
  · rw [borrowMarks,if_pos legal]
    simp only [Run1RequestProtocol.mem_merge,List.not_mem_nil,
      or_false,legal,true_and,List.mem_map,List.mem_filter]
    constructor
    · rintro ⟨entry,⟨member,matching⟩,sameMark⟩
      exact ⟨entry,member,matching,sameMark⟩
    · rintro ⟨entry,member,matching,sameMark⟩
      exact ⟨entry,⟨member,matching⟩,sameMark⟩
  · simp [borrowMarks,legal]

theorem borrowMarks_exact {n : Nat} (g : Graph n)
    (localNeeds : Fin n → List Descriptor) (caller : Fin n)
    (legalSlots : List Slot) (slot : Slot) (mark : Mark) :
    mark ∈ borrowMarks g localNeeds caller legalSlots slot ↔
      slot ∈ legalSlots ∧ ∃ source need,
        need ∈ localNeeds source ∧ Run1Requests.Reaches g source caller ∧
        need.scope.matches slot = true ∧ need.mark = mark := by
  rw [borrowMarks_witness,lookupWitness,seekNeed_exact]
  constructor
  · rintro ⟨legal,entry,_,matching,sameMark⟩
    exact ⟨legal,entry.source,entry.descriptor,entry.declared,entry.path.sound,
      matching,sameMark⟩
  · rintro ⟨legal,source,need,declared,reachable,matching,sameMark⟩
    obtain ⟨entry,member,_,eq⟩ :=
      compileDescriptors_complete g localNeeds caller source need declared reachable
    exact ⟨legal,entry,member,by simpa [eq] using matching,by simpa [eq] using sameMark⟩

/-- Closure/leastness is established from local descriptors and graph edges,
    not from an assumed coverage property equivalent to the target. -/
theorem borrowMarks_least {n : Nat} (g : Graph n)
    (localNeeds : Fin n → List Descriptor) (legalSlots : List Slot)
    (other : Fin n → Slot → Mark → Prop)
    (localIncluded : ∀ owner need, need ∈ localNeeds owner → ∀ slot,
      slot ∈ legalSlots → need.scope.matches slot = true → other owner slot need.mark)
    (callClosed : ∀ caller callee, (caller,callee) ∈ g → ∀ slot mark,
      other callee slot mark → other caller slot mark)
    (caller : Fin n) (slot : Slot) (mark : Mark)
    (present : mark ∈ borrowMarks g localNeeds caller legalSlots slot) :
    other caller slot mark := by
  obtain ⟨legal,source,need,declared,reachable,matching,sameMark⟩ :=
    (borrowMarks_exact g localNeeds caller legalSlots slot mark).mp present
  clear present
  subst mark
  have initial := localIncluded source need declared slot legal matching
  induction reachable with
  | seed => exact initial
  | call edge _ ih => exact callClosed _ _ edge slot _ ih

theorem local_mark_in_lookup {n : Nat} (g : Graph n)
    (localNeeds : Fin n → List Descriptor) (owner : Fin n) (need : Descriptor)
    (declared : need ∈ localNeeds owner) (legalSlots : List Slot) (slot : Slot)
    (legal : slot ∈ legalSlots) (matching : need.scope.matches slot = true) :
    need.mark ∈ borrowMarks g localNeeds owner legalSlots slot :=
  (borrowMarks_exact g localNeeds owner legalSlots slot need.mark).mpr
    ⟨legal,owner,need,declared,.seed,matching,rfl⟩

/-- Prepending the actual caller edge is an explicit executable witness for
    same-slot frozen inheritance. No F or selected-row premise is needed. -/
def NeedWitness.prepend {n : Nat} {g : Graph n}
    {localNeeds : Fin n → List Descriptor} {caller callee : Fin n}
    {legalSlots : List Slot} {slot : Slot} {mark : Mark}
    (edge : (caller,callee) ∈ g)
    (witness : NeedWitness g localNeeds callee legalSlots slot mark) :
    NeedWitness g localNeeds caller legalSlots slot mark :=
  { witness with inherited :=
      { witness.inherited with path := .prepend edge witness.inherited.path } }

theorem same_slot_frozen_inherits {n : Nat} (g : Graph n)
    (localNeeds : Fin n → List Descriptor) (caller callee : Fin n)
    (edge : (caller,callee) ∈ g) (legalSlots : List Slot) (slot : Slot) (mark : Mark)
    (present : mark ∈ borrowMarks g localNeeds callee legalSlots slot) :
    mark ∈ borrowMarks g localNeeds caller legalSlots slot := by
  obtain ⟨legal,source,need,declared,reachable,matching,sameMark⟩ :=
    (borrowMarks_exact g localNeeds callee legalSlots slot mark).mp present
  exact (borrowMarks_exact g localNeeds caller legalSlots slot mark).mpr
    ⟨legal,source,need,declared,.call edge reachable,matching,sameMark⟩

theorem root_need_resolves_legal_field {n : Nat} (g : Graph n)
    (localNeeds : Fin n → List Descriptor) (owner : Fin n) (mark cls field : Nat)
    (declared : ⟨.root,mark⟩ ∈ localNeeds owner) (legalSlots : List Slot)
    (legal : (Run1Requests.Slot.field cls field) ∈ legalSlots) :
    mark ∈ borrowMarks g localNeeds owner legalSlots (.field cls field) :=
  local_mark_in_lookup g localNeeds owner ⟨.root,mark⟩ declared legalSlots
    (.field cls field) legal rfl

theorem class_need_resolves_legal_field {n : Nat} (g : Graph n)
    (localNeeds : Fin n → List Descriptor) (owner : Fin n) (mark cls field : Nat)
    (declared : ⟨.cls cls,mark⟩ ∈ localNeeds owner) (legalSlots : List Slot)
    (legal : (Run1Requests.Slot.field cls field) ∈ legalSlots) :
    mark ∈ borrowMarks g localNeeds owner legalSlots (.field cls field) := by
  apply local_mark_in_lookup g localNeeds owner ⟨.cls cls,mark⟩ declared
    legalSlots (.field cls field) legal
  simp [Scope.matches]

@[simp] theorem Scope.matches_base (scope : Scope) (base : Nat) :
    scope.matches (.base base) = false := by cases scope <;> rfl

theorem static_plan_has_no_base_row {n : Nat} (g : Graph n)
    (localNeeds : Fin n → List Descriptor) (owner : Fin n)
    (legalSlots : List Slot) (base : Nat) :
    borrowMarks g localNeeds owner legalSlots (.base base) = [] := by
  have emptyFilter : (compileDescriptors g localNeeds owner).filter
      (fun entry => entry.descriptor.scope.matches (.base base)) = [] := by
    induction compileDescriptors g localNeeds owner with
    | nil => rfl
    | cons head tail ih =>
        simp only [List.filter_cons,Scope.matches_base,Bool.false_eq_true,if_false]
        simpa only [Scope.matches_base] using ih
  unfold borrowMarks
  split
  · rw [emptyFilter]; rfl
  · rfl

/-! ## Reuse the existing shared-B / residual / temporary lifecycle.

  This is a logical single-slot instantiation of the checked lifecycle. The
  pool key identifies the owner; its value is the virtual borrowed row for
  that one actual slot. It is NOT a global field-by-mark row materialisation.
  Existing lifecycle theorems provide the packed-storage refinement boundary.
-/

def rowPool {n : Nat} (g : Graph n) (localNeeds : Fin n → List Descriptor)
    (legalSlots : List Slot) (slot : Slot) : Run1PrecomputedRequests.BasePool Mark :=
  fun key => if h : key < n then borrowMarks g localNeeds ⟨key,h⟩ legalSlots slot else []

@[simp] theorem rowPool_owner {n : Nat} (g : Graph n)
    (localNeeds : Fin n → List Descriptor) (legalSlots : List Slot)
    (slot : Slot) (owner : Fin n) :
    rowPool g localNeeds legalSlots slot owner.val =
      borrowMarks g localNeeds owner legalSlots slot := by
  simp [rowPool,owner.isLt]

theorem shared_row_initialise_no_copies {n : Nat} (g : Graph n)
    (localNeeds : Fin n → List Descriptor) (legalSlots : List Slot)
    (slot : Slot) (owner : Fin n) :
    Run1PrecomputedRequests.NoBaseCopies (rowPool g localNeeds legalSlots slot)
      (Run1PrecomputedRequests.initialise owner.val) ∧
    (Run1PrecomputedRequests.initialise owner.val :
      Run1PrecomputedRequests.MethodState Mark).authoritative.baseKey = some owner.val :=
  ⟨Run1PrecomputedRequests.initialise_no_base_copies _ _,rfl⟩

theorem same_slot_no_precomputed_delivery {n : Nat} (g : Graph n)
    (localNeeds : Fin n → List Descriptor) (caller callee : Fin n)
    (edge : (caller,callee) ∈ g) (legalSlots : List Slot) (slot : Slot) (mark : Mark)
    (events : List (Run1PrecomputedRequests.Event Mark))
    (present : mark ∈ borrowMarks g localNeeds callee legalSlots slot) :
    mark ∉ (Run1PrecomputedRequests.commitAndReplace
      (rowPool g localNeeds legalSlots slot)
      (Run1PrecomputedRequests.run (rowPool g localNeeds legalSlots slot)
        (Run1PrecomputedRequests.initialise caller.val) events)).2 := by
  apply Run1PrecomputedRequests.no_precomputed_delivery_after_any_trace
  simpa using same_slot_frozen_inherits g localNeeds caller callee edge
    legalSlots slot mark present

/-- The request routes to the actual original slot, never to a checked/current
    field merely because that field has inherited frozen membership. -/
def routeNeed (original : Slot) (mark : Mark) : Slot × Mark := (original,mark)

theorem changed_origin_not_checked (original checked : Slot) (mark : Mark)
    (different : original ≠ checked) : routeNeed original mark ≠ (checked,mark) := by
  intro eq
  exact different (congrArg Prod.fst eq)

/-- Even when the checked field is precomputed, a different original slot
    without that membership contributes a genuine fresh P→D notification.
    A real existing dynamic R membership may suppress it via the same
    lifecycle; this theorem starts from the freshly bound frozen row. -/
theorem changed_origin_collects_fresh {n : Nat} (g : Graph n)
    (localNeeds : Fin n → List Descriptor) (owner : Fin n)
    (legalSlots : List Slot) (original checked : Slot) (mark : Mark)
    (checkedHas : mark ∈ borrowMarks g localNeeds owner legalSlots checked)
    (originalMissing : mark ∉ borrowMarks g localNeeds owner legalSlots original) :
    routeNeed original mark ≠ (checked,mark) ∧
    mark ∈ (Run1PrecomputedRequests.commitAndReplace
      (rowPool g localNeeds legalSlots original)
      (Run1PrecomputedRequests.collect (rowPool g localNeeds legalSlots original)
        (Run1PrecomputedRequests.initialise owner.val) [mark])).2 := by
  constructor
  · apply changed_origin_not_checked
    intro same
    subst checked
    exact originalMissing checkedHas
  · simp [Run1PrecomputedRequests.commitAndReplace,Run1PrecomputedRequests.fresh,
      Run1PrecomputedRequests.collect,Run1PrecomputedRequests.prepare,
      Run1PrecomputedRequests.read,Run1PrecomputedRequests.initialise,
      Run1PrecomputedRequests.baseRows,Run1PrecomputedRequests.temporary,
      Run1RequestProtocol.difference,originalMissing]

/-! ## Executable distinguishing controls.
  Mark zero is admitted; recursive inheritance terminates with a real path;
  root/class overlap deduplicates; unrelated classes/base slots stay distinct.
  There is no read-plan argument to any of these lookups.
-/

def controlGraph : Graph 3 := [(0,1),(1,2),(2,1)]
def controlNeeds : Fin 3 → List Descriptor := fun owner =>
  if owner = 2 then [⟨.root,0⟩,⟨.cls 4,0⟩,⟨.field 4 1,8⟩] else []
def controlLegal : List Slot := [.field 4 1,.field 4 2,.field 9 1,.base 2]

def controls : List Bool := [
  (inheritedPath controlGraph 2 0).map CallPath.nodes == some [0,1,2],
  borrowMarks controlGraph controlNeeds 0 controlLegal (.field 4 1) == [0,8],
  borrowMarks controlGraph controlNeeds 0 controlLegal (.field 4 2) == [0],
  borrowMarks controlGraph controlNeeds 0 controlLegal (.field 9 1) == [0],
  borrowMarks controlGraph controlNeeds 0 controlLegal (.base 2) == [],
  borrowMarks controlGraph controlNeeds 0 controlLegal (.field 4 99) == [],
  (lookupWitness controlGraph controlNeeds 0 controlLegal (.field 4 1) 8).isSome,
  !(lookupWitness controlGraph controlNeeds 0 controlLegal (.field 4 2) 8).isSome,
  (compileDescriptors controlGraph controlNeeds 0).length == 3,
  routeNeed (.field 4 2) 8 == (.field 4 2,8),
  (Run1PrecomputedRequests.commitAndReplace
    (rowPool controlGraph controlNeeds controlLegal (.field 4 2))
    (Run1PrecomputedRequests.collect
      (rowPool controlGraph controlNeeds controlLegal (.field 4 2))
      (Run1PrecomputedRequests.initialise 0) [8])).2 == [8],
  (Run1PrecomputedRequests.commitAndReplace
    (rowPool controlGraph controlNeeds controlLegal (.field 4 1))
    (Run1PrecomputedRequests.collect
      (rowPool controlGraph controlNeeds controlLegal (.field 4 1))
      (Run1PrecomputedRequests.initialise 0) [0,8,8])).2 == []
]

#eval controls

end ApSpec.BottomUpStaticRequests
