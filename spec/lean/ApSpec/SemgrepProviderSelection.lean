import Std

/-
Provider selection only. Normal execution keeps the existing reduction's S.
Relevant metadata keeps complete supported graphs. The unchanged reducer and
language condition resolver are adapter boundaries: this file neither changes
dependency reachability nor turns a relevant source condition into executable
GEN. A graph's rule ID is not its mark owner. Sharing is conservative over
owner keys, complete FULL keys and serialized IDs. Unknown/mixed provenance can
retain one graph or the entire metadata view.

Finite support growth is followed by an executable closure check. A failed
check falls back to all graphs. Correctness therefore needs no assumed bound
on the minimal support algorithm. Minimality and runtime complexity are not
claimed. Filtering preserves the original variant objects, order and copies.

The scalar rewrite fragment proves preservation of active/unknown mark
observations under an explicit raw-to-resolved reset-coverage boundary. It
does NOT assert equality of old and new complete FLOW exclusion headers, nor
prove Kotlin resolution, AP geometry, prescan completeness or whole-engine
equivalence. The runtime adapter must prove those correspondences separately.
-/
namespace ApSpec.SemgrepProviderSelection
set_option autoImplicit false

structure OwnerKey where
  name : Nat
  primitive : Bool
  deriving DecidableEq, Repr

structure FullMark where
  owner : OwnerKey
  name : Nat
  deriving DecidableEq, Repr

structure Graph where
  ruleId : Nat
  owners : List OwnerKey
  marks : List FullMark
  itemIds : List Nat
  applicable : Bool
  uncertain : Bool := false
  deriving DecidableEq, Repr

structure ViewKey where
  providerView : Nat
  generation : Nat
  deriving DecidableEq, Repr

structure Snapshot where
  key : ViewKey
  graphs : List Graph
  executionIds : List Nat
  opaqueMetadata : Bool := false
  deriving DecidableEq, Repr

inductive Phase where
  | prescan | analysis
  deriving DecidableEq, Repr

structure ProviderState where
  phase : Phase
  selection : Option Snapshot
  declarations : List FullMark
  deriving DecidableEq, Repr

inductive Role where
  | source | sink | cleaner | passThrough
  deriving DecidableEq, Repr

inductive Query where
  | execution | metadata
  deriving DecidableEq, Repr

def shares {α : Type} [DecidableEq α] (xs ys : List α) : Bool :=
  xs.any fun x => decide (x ∈ ys)

theorem shares_true {α : Type} [DecidableEq α] (xs ys : List α) :
    shares xs ys = true ↔ ∃ x, x ∈ xs ∧ x ∈ ys := by
  simp [shares, List.any_eq_true]

def related (first second : Graph) : Bool :=
  shares first.owners second.owners || shares first.marks second.marks ||
    shares first.itemIds second.itemIds

def seed (s : Snapshot) (g : Graph) : Bool :=
  g.applicable || g.uncertain || shares g.itemIds s.executionIds

def grow (s : Snapshot) (chosen : List Graph) : List Graph :=
  s.graphs.filter fun g => seed s g || decide (g ∈ chosen) ||
    chosen.any (fun old => related old g)

def growRounds (s : Snapshot) : Nat → List Graph
  | 0 => s.graphs.filter (seed s)
  | n + 1 => grow s (growRounds s n)

def supportCheck (s : Snapshot) (chosen : List Graph) : Bool :=
  s.graphs.all (fun g => !seed s g || decide (g ∈ chosen)) &&
    s.graphs.all (fun g => s.graphs.all fun h =>
      !(decide (g ∈ chosen) && related g h) || decide (h ∈ chosen))

theorem whole_catalogue_passes (s : Snapshot) : supportCheck s s.graphs = true := by
  simp only [supportCheck, Bool.and_eq_true]
  constructor
  · apply List.all_eq_true.mpr
    intro g hg
    simp [hg]
  · apply List.all_eq_true.mpr
    intro g _
    apply List.all_eq_true.mpr
    intro h hh
    simp [hh]

def support (s : Snapshot) : List Graph :=
  let candidate := growRounds s s.graphs.length
  if supportCheck s candidate then candidate else s.graphs

theorem support_is_checked (s : Snapshot) : supportCheck s (support s) = true := by
  by_cases h : supportCheck s (growRounds s s.graphs.length) = true
  · simp [support, h]
  · simp [support, h, whole_catalogue_passes]

theorem seeded_graph_supported (s : Snapshot) (g : Graph)
    (hg : g ∈ s.graphs) (hs : seed s g = true) : g ∈ support s := by
  have checked := (Bool.and_eq_true_iff.mp (support_is_checked s)).1
  have row := List.all_eq_true.mp checked g hg
  simpa [hs] using row

theorem support_closed (s : Snapshot) (g h : Graph)
    (hg : g ∈ s.graphs) (hh : h ∈ s.graphs)
    (chosen : g ∈ support s) (link : related g h = true) : h ∈ support s := by
  have checked := (Bool.and_eq_true_iff.mp (support_is_checked s)).2
  have row := List.all_eq_true.mp (List.all_eq_true.mp checked g hg) h hh
  simpa [chosen, link] using row

theorem active_graph_supported (s : Snapshot) (g : Graph)
    (hg : g ∈ s.graphs) (active : g.applicable = true) : g ∈ support s := by
  apply seeded_graph_supported s g hg
  simp [seed, active]

theorem uncertain_graph_supported (s : Snapshot) (g : Graph)
    (hg : g ∈ s.graphs) (unknown : g.uncertain = true) : g ∈ support s := by
  apply seeded_graph_supported s g hg
  simp [seed, unknown]

theorem selected_item_graph_supported (s : Snapshot) (g : Graph) (id : Nat)
    (hg : g ∈ s.graphs) (hi : id ∈ g.itemIds) (selected : id ∈ s.executionIds) :
    g ∈ support s := by
  apply seeded_graph_supported s g hg
  have shared : shares g.itemIds s.executionIds = true :=
    (shares_true _ _).mpr ⟨id, hi, selected⟩
  simp [seed, shared]

theorem shared_owner_supported (s : Snapshot) (g h : Graph) (owner : OwnerKey)
    (hg : g ∈ s.graphs) (hh : h ∈ s.graphs) (chosen : g ∈ support s)
    (first : owner ∈ g.owners) (second : owner ∈ h.owners) : h ∈ support s := by
  apply support_closed s g h hg hh chosen
  have shared := (shares_true _ _).mpr ⟨owner, first, second⟩
  simp [related, shared]

theorem shared_full_mark_supported (s : Snapshot) (g h : Graph) (mark : FullMark)
    (hg : g ∈ s.graphs) (hh : h ∈ s.graphs) (chosen : g ∈ support s)
    (first : mark ∈ g.marks) (second : mark ∈ h.marks) : h ∈ support s := by
  apply support_closed s g h hg hh chosen
  have shared := (shares_true _ _).mpr ⟨mark, first, second⟩
  simp [related, shared]

theorem shared_item_id_supported (s : Snapshot) (g h : Graph) (id : Nat)
    (hg : g ∈ s.graphs) (hh : h ∈ s.graphs) (chosen : g ∈ support s)
    (first : id ∈ g.itemIds) (second : id ∈ h.itemIds) : h ∈ support s := by
  apply support_closed s g h hg hh chosen
  have shared := (shares_true _ _).mpr ⟨id, first, second⟩
  simp [related, shared]

inductive SupportPath (s : Snapshot) : Graph → Prop where
  | start (g : Graph) : g ∈ s.graphs → seed s g = true → SupportPath s g
  | next (g h : Graph) : SupportPath s g → g ∈ s.graphs → h ∈ s.graphs →
      related g h = true → SupportPath s h

theorem support_path_complete (s : Snapshot) (g : Graph) (path : SupportPath s g) :
    g ∈ support s := by
  induction path with
  | start g hg hs => exact seeded_graph_supported s g hg hs
  | next g h _ hg hh linked ih => exact support_closed s g h hg hh ih linked

def knownId (s : Snapshot) (id : Nat) : Bool :=
  s.graphs.any fun g => decide (id ∈ g.itemIds)

def supportedId (s : Snapshot) (id : Nat) : Bool :=
  (support s).any fun g => decide (id ∈ g.itemIds)

def executionKeeps (s : Snapshot) : Option Nat → Bool
  | none => true
  | some id => decide (id ∈ s.executionIds)

def metadataKeeps (s : Snapshot) : Option Nat → Bool
  | none => true
  | some id => s.opaqueMetadata || !knownId s id || supportedId s id

theorem supported_item_kept (s : Snapshot) (g : Graph) (id : Nat)
    (chosen : g ∈ support s) (hi : id ∈ g.itemIds) : metadataKeeps s (some id) = true := by
  have h : supportedId s id = true := by
    apply List.any_eq_true.mpr
    exact ⟨g, chosen, by simpa using hi⟩
  simp [metadataKeeps, h]

theorem active_graph_metadata_complete (s : Snapshot) (g : Graph) (id : Nat)
    (hg : g ∈ s.graphs) (active : g.applicable = true) (hi : id ∈ g.itemIds) :
    metadataKeeps s (some id) = true :=
  supported_item_kept s g id (active_graph_supported s g hg active) hi

theorem execution_id_retained_in_metadata (s : Snapshot) (id : Nat)
    (selected : id ∈ s.executionIds) : metadataKeeps s (some id) = true := by
  by_cases known : knownId s id = true
  · obtain ⟨g, hg, hi⟩ := List.any_eq_true.mp known
    have belongs : id ∈ g.itemIds := by simpa using hi
    exact supported_item_kept s g id (selected_item_graph_supported s g id hg belongs selected) belongs
  · have absent : knownId s id = false := Bool.eq_false_iff.mpr known
    simp [metadataKeeps, absent]

@[simp] theorem null_metadata_kept (s : Snapshot) : metadataKeeps s none = true := rfl
@[simp] theorem null_execution_kept (s : Snapshot) : executionKeeps s none = true := rfl

theorem unknown_id_kept (s : Snapshot) (id : Nat) (unknown : knownId s id = false) :
    metadataKeeps s (some id) = true := by simp [metadataKeeps, unknown]

theorem opaque_metadata_kept (s : Snapshot) (id : Option Nat) (unknown : s.opaqueMetadata = true) :
    metadataKeeps s id = true := by cases id <;> simp [metadataKeeps, unknown]

/-- Rejection needs a known ID, no opaque fallback, and no supported owner,
    complete-mark or ID-sharing component. This is stronger than "not in S". -/
theorem rejected_metadata_has_no_support (s : Snapshot) (id : Nat)
    (rejected : metadataKeeps s (some id) = false) :
    s.opaqueMetadata = false ∧ knownId s id = true ∧ supportedId s id = false := by
  cases ho : s.opaqueMetadata <;> cases hk : knownId s id <;>
    cases hs : supportedId s id <;> simp_all [metadataKeeps]

theorem rejected_item_is_not_active (s : Snapshot) (g : Graph) (id : Nat)
    (hg : g ∈ s.graphs) (hi : id ∈ g.itemIds)
    (rejected : metadataKeeps s (some id) = false) :
    g.applicable = false ∧ g.uncertain = false ∧ ¬ SupportPath s g := by
  have unsupported : g ∉ support s := by
    intro chosen
    have kept := supported_item_kept s g id chosen hi
    simp [rejected] at kept
  constructor
  · cases ha : g.applicable
    · rfl
    · exact False.elim (unsupported (active_graph_supported s g hg ha))
  constructor
  · cases hu : g.uncertain
    · rfl
    · exact False.elim (unsupported (uncertain_graph_supported s g hg hu))
  · intro path
    exact unsupported (support_path_complete s g path)

def keep (state : ProviderState) (role : Role) (query : Query) (id : Option Nat) : Bool :=
  match state.selection with
  | none => true
  | some s => match query, role with
    | .execution, .source | .execution, .sink => executionKeeps s id
    | .metadata, .source | .metadata, .sink | .metadata, .cleaner => metadataKeeps s id
    | _, _ => true

/-- Existing normal execution policy; relevant metadata was broad before this change. -/
def oldKeep (state : ProviderState) (role : Role) (query : Query) (id : Option Nat) : Bool :=
  match state.selection with
  | none => true
  | some s => match query, role with
    | .execution, .source | .execution, .sink => executionKeeps s id
    | _, _ => true

theorem normal_execution_unchanged (state : ProviderState) (role : Role) (id : Option Nat) :
    keep state role .execution id = oldKeep state role .execution id := by
  cases hs : state.selection <;> cases role <;> simp [keep, oldKeep, hs]

@[simp] theorem pass_execution_unchanged (state : ProviderState) (query : Query) (id : Option Nat) :
    keep state .passThrough query id = true := by
  cases hs : state.selection <;> cases query <;> simp [keep, hs]

@[simp] theorem actual_cleaner_unchanged (state : ProviderState) (id : Option Nat) :
    keep state .cleaner .execution id = true := by
  cases hs : state.selection <;> simp [keep, hs]

theorem initial_selection_unfiltered (phase : Phase) (decls : List FullMark)
    (role : Role) (query : Query) (id : Option Nat) :
    keep ⟨phase, none, decls⟩ role query id = true := rfl

def enterPhase (state : ProviderState) (phase : Phase) : ProviderState := {state with phase := phase}
def install (state : ProviderState) (snapshot : Snapshot) : ProviderState :=
  {state with selection := some snapshot}

@[simp] theorem phase_change_does_not_reset (state : ProviderState) (phase : Phase) :
    (enterPhase state phase).selection = state.selection := rfl

theorem phase_change_preserves_selection (state : ProviderState) (phase : Phase)
    (role : Role) (query : Query) (id : Option Nat) :
    keep (enterPhase state phase) role query id = keep state role query id := rfl

@[simp] theorem phase_keeps_declarations (state : ProviderState) (phase : Phase) :
    (enterPhase state phase).declarations = state.declarations := rfl

@[simp] theorem install_keeps_declarations (state : ProviderState) (snapshot : Snapshot) :
    (install state snapshot).declarations = state.declarations := rfl

structure Row where
  serializedId : Option Nat
  variant : Nat
  position : Nat
  resets : List FullMark
  payload : List Nat
  deriving DecidableEq, Repr

def selectedRows (state : ProviderState) (role : Role) (query : Query) (rows : List Row) : List Row :=
  rows.filter fun row => keep state role query row.serializedId

theorem selected_rows_are_original_subsequence (state : ProviderState) (role : Role)
    (query : Query) (rows : List Row) : List.Sublist (selectedRows state role query rows) rows :=
  List.filter_sublist

theorem selected_row_exact (state : ProviderState) (role : Role) (query : Query)
    (rows : List Row) (row : Row) :
    row ∈ selectedRows state role query rows ↔
      row ∈ rows ∧ keep state role query row.serializedId = true := by
  simp [selectedRows]

def copies (row : Row) : List Row → Nat
  | [] => 0
  | first :: rest => (if first = row then 1 else 0) + copies row rest

theorem copies_preserved_exactly (state : ProviderState) (role : Role) (query : Query)
    (rows : List Row) (row : Row) :
    copies row (selectedRows state role query rows) =
      if keep state role query row.serializedId then copies row rows else 0 := by
  induction rows with
  | nil => simp [selectedRows, copies]
  | cons first rest ih =>
    by_cases same : first = row
    · subst first
      cases hk : keep state role query row.serializedId <;>
        simp_all [selectedRows, copies]
    · cases hf : keep state role query first.serializedId <;>
        cases hr : keep state role query row.serializedId <;>
        simp_all [selectedRows, copies]

theorem execution_rows_unchanged (state : ProviderState) (role : Role) (rows : List Row) :
    selectedRows state role .execution rows =
      rows.filter (fun row => oldKeep state role .execution row.serializedId) := by
  simp only [selectedRows, normal_execution_unchanged]

theorem phase_preserves_rows (state : ProviderState) (phase : Phase)
    (role : Role) (query : Query) (rows : List Row) :
    selectedRows (enterPhase state phase) role query rows = selectedRows state role query rows := rfl

structure SavedQuery where
  snapshot : Snapshot
  role : Role
  query : Query
  deriving DecidableEq, Repr

def resume (current : Snapshot) (saved : SavedQuery) (rows : List Row) : Option (List Row) :=
  if current.key = saved.snapshot.key then
    some (selectedRows ⟨.analysis, some current, []⟩ saved.role saved.query rows)
  else none

/-- Runtime boundary: equal view-qualified generation keys name one snapshot. -/
def CoherentGeneration (first second : Snapshot) : Prop := first.key = second.key → first = second

theorem stable_snapshot_resume (saved : SavedQuery) (rows : List Row) :
    resume saved.snapshot saved rows =
      some (selectedRows ⟨.analysis, some saved.snapshot, []⟩ saved.role saved.query rows) := by
  simp [resume]

theorem coherent_generation_resume (current : Snapshot) (saved : SavedQuery) (rows : List Row)
    (coherent : CoherentGeneration current saved.snapshot) (same : current.key = saved.snapshot.key) :
    resume current saved rows =
      some (selectedRows ⟨.analysis, some saved.snapshot, []⟩ saved.role saved.query rows) := by
  have equal := coherent same
  subst current
  exact stable_snapshot_resume saved rows

theorem stale_or_foreign_view_rejected (current : Snapshot) (saved : SavedQuery) (rows : List Row)
    (different : current.key ≠ saved.snapshot.key) : resume current saved rows = none := by
  simp [resume, different]

theorem growth_is_catalogue_only (s : Snapshot) (n : Nat) (g : Graph)
    (member : g ∈ growRounds s n) : g ∈ s.graphs := by
  cases n with
  | zero => exact (List.mem_filter.mp member).1
  | succ n => exact (List.mem_filter.mp member).1

theorem supported_graph_is_raw (s : Snapshot) (g : Graph)
    (member : g ∈ support s) : g ∈ s.graphs := by
  by_cases checked : supportCheck s (growRounds s s.graphs.length) = true
  · simp only [support, checked, if_pos] at member
    exact growth_is_catalogue_only s _ g member
  · simpa [support, checked] using member

def knownMark (s : Snapshot) (mark : FullMark) : Bool :=
  s.graphs.any fun g => decide (mark ∈ g.marks)

def supportedMark (s : Snapshot) (mark : FullMark) : Bool :=
  (support s).any fun g => decide (mark ∈ g.marks)

/-- Unknown marks are observable, not silently erased. Known inactive marks
    alone can disappear from this observation projection. -/
def observableMark (s : Snapshot) (mark : FullMark) : Bool :=
  !knownMark s mark || supportedMark s mark

def projectMarks (s : Snapshot) (marks : List FullMark) : List FullMark :=
  marks.filter (observableMark s)

theorem supported_mark_observable (s : Snapshot) (g : Graph) (mark : FullMark)
    (member : g ∈ support s) (contains : mark ∈ g.marks) : observableMark s mark = true := by
  have supported : supportedMark s mark = true :=
    List.any_eq_true.mpr ⟨g, member, by simpa using contains⟩
  simp [observableMark, supported]

theorem active_graph_marks_observable (s : Snapshot) (g : Graph) (mark : FullMark)
    (raw : g ∈ s.graphs) (active : g.applicable = true) (contains : mark ∈ g.marks) :
    observableMark s mark = true :=
  supported_mark_observable s g mark (active_graph_supported s g raw active) contains

theorem unknown_mark_remains_observable (s : Snapshot) (mark : FullMark)
    (unknown : knownMark s mark = false) : observableMark s mark = true := by
  simp [observableMark, unknown]

@[simp] theorem project_marks_append (s : Snapshot) (first second : List FullMark) :
    projectMarks s (first ++ second) = projectMarks s first ++ projectMarks s second := by
  simp [projectMarks]

theorem observable_project_membership (s : Snapshot) (mark : FullMark)
    (marks : List FullMark) (visible : observableMark s mark = true) :
    mark ∈ projectMarks s marks ↔ mark ∈ marks := by
  simp [projectMarks, visible]

/-- Adapter obligation: a known serialized ID's complete resolved reset marks
    occur in its raw graph support. Unknown/null rows are retained without this
    claim. This also covers multiple variants and colliding raw item IDs. -/
def ResetCoverage (s : Snapshot) (row : Row) : Prop :=
  ∀ id, row.serializedId = some id → knownId s id = true →
    ∀ mark, mark ∈ row.resets →
      ∃ g, g ∈ s.graphs ∧ id ∈ g.itemIds ∧ mark ∈ g.marks

theorem observable_reset_row_is_kept (s : Snapshot) (row : Row) (mark : FullMark)
    (coverage : ResetCoverage s row) (mentioned : mark ∈ row.resets)
    (visible : observableMark s mark = true) : metadataKeeps s row.serializedId = true := by
  cases idCase : row.serializedId with
  | none => simp [metadataKeeps]
  | some id =>
    by_cases hk : knownId s id = true
    · obtain ⟨g, hg, hi, hm⟩ := coverage id idCase hk mark mentioned
      have markKnown : knownMark s mark = true :=
        List.any_eq_true.mpr ⟨g, hg, by simpa using hm⟩
      have markSupported : supportedMark s mark = true := by
        simpa [observableMark, markKnown] using visible
      obtain ⟨other, chosen, contains⟩ := List.any_eq_true.mp markSupported
      have cm : mark ∈ other.marks := by simpa using contains
      have oldRaw := supported_graph_is_raw s other chosen
      have gSupported := shared_full_mark_supported s other g mark oldRaw hg chosen cm hm
      simpa [idCase] using supported_item_kept s g id gSupported hi
    · have absent : knownId s id = false := Bool.eq_false_iff.mpr hk
      simp [metadataKeeps, absent]

theorem rejected_reset_has_no_observable_mark (s : Snapshot) (row : Row)
    (coverage : ResetCoverage s row) (rejected : metadataKeeps s row.serializedId = false) :
    projectMarks s row.resets = [] := by
  have absent : ∀ mark, mark ∈ row.resets → observableMark s mark = false := by
    intro mark mentioned
    cases visible : observableMark s mark with
    | false => rfl
    | true =>
      have kept := observable_reset_row_is_kept s row mark coverage mentioned visible
      simp [rejected] at kept
  have empty : ∀ marks : List FullMark,
      (∀ mark, mark ∈ marks → observableMark s mark = false) →
      marks.filter (observableMark s) = [] := by
    intro marks
    induction marks with
    | nil => intro _; rfl
    | cons first rest ih =>
      intro allAbsent
      have headAbsent := allAbsent first (by simp)
      have tailAbsent : ∀ mark, mark ∈ rest → observableMark s mark = false := by
        intro mark hm
        exact allAbsent mark (by simp [hm])
      simpa only [List.filter_cons, headAbsent, Bool.false_eq_true, ↓reduceIte]
        using ih tailAbsent
  exact empty row.resets absent

def metadataRows (s : Snapshot) (rows : List Row) : List Row :=
  rows.filter fun row => metadataKeeps s row.serializedId

theorem opaque_rows_are_old_rows (s : Snapshot) (rows : List Row)
    (unknown : s.opaqueMetadata = true) : metadataRows s rows = rows := by
  apply List.filter_eq_self.mpr
  intro row _
  exact opaque_metadata_kept s row.serializedId unknown

/-- Position is an already chosen scalar geometry. This is not a replacement
    for the AP spatial kernel or its EXACT/ANY/MUST laws. -/
def resetMaskAt (position : Nat) : List Row → List FullMark
  | [] => []
  | row :: rest => (if row.position = position then row.resets else []) ++ resetMaskAt position rest

theorem projected_reset_mask_equal (s : Snapshot) (position : Nat) (rows : List Row)
    (coverage : ∀ row, row ∈ rows → ResetCoverage s row) :
    projectMarks s (resetMaskAt position (metadataRows s rows)) =
      projectMarks s (resetMaskAt position rows) := by
  induction rows with
  | nil => rfl
  | cons row rest ih =>
    have headCoverage := coverage row (by simp)
    have tailCoverage : ∀ r, r ∈ rest → ResetCoverage s r := by
      intro r hr
      exact coverage r (by simp [hr])
    have tail := ih tailCoverage
    simp only [metadataRows] at tail ⊢
    cases kept : metadataKeeps s row.serializedId with
    | false =>
      have empty := rejected_reset_has_no_observable_mark s row headCoverage kept
      by_cases place : row.position = position <;>
        simp [kept, resetMaskAt, place, project_marks_append, empty, tail]
    | true =>
      by_cases place : row.position = position <;>
        simp [kept, resetMaskAt, place, project_marks_append, tail]

def rewriteMarks (resets marks : List FullMark) : List FullMark :=
  marks.filter fun mark => !decide (mark ∈ resets)

theorem projected_rewrite_depends_only_on_projected_resets (s : Snapshot)
    (old new marks : List FullMark) (equal : projectMarks s old = projectMarks s new) :
    projectMarks s (rewriteMarks old marks) = projectMarks s (rewriteMarks new marks) := by
  simp only [projectMarks, rewriteMarks, List.filter_filter]
  apply List.filter_congr
  intro mark _
  cases visible : observableMark s mark with
  | false => simp
  | true =>
    have membership : mark ∈ old ↔ mark ∈ new := by
      have first := observable_project_membership s mark old visible
      have second := observable_project_membership s mark new visible
      rw [equal] at first
      exact first.symm.trans second
    simp [membership]

structure Header where
  origins : List Nat
  base : Nat
  path : List Nat
  tail : Nat
  layer : Nat
  fieldExclusions : List Nat
  deriving DecidableEq, Repr

structure TaintValue where
  header : Header
  marks : List FullMark
  deriving DecidableEq, Repr

structure FlowValue where
  header : Header
  mx : List FullMark
  deriving DecidableEq, Repr

def rewriteTaint (rows : List Row) (value : TaintValue) : TaintValue :=
  {value with marks := rewriteMarks (resetMaskAt value.header.base rows) value.marks}

def rewriteFlow (rows : List Row) (value : FlowValue) : FlowValue :=
  {value with mx := value.mx ++ resetMaskAt value.header.base rows}

def taintObservations (s : Snapshot) (value : TaintValue) : List (Header × FullMark) :=
  (projectMarks s value.marks).map fun mark => (value.header, mark)

def flowObservation (s : Snapshot) (value : FlowValue) : FlowValue :=
  {value with mx := projectMarks s value.mx}

@[simp] theorem taint_header_unchanged (rows : List Row) (value : TaintValue) :
    (rewriteTaint rows value).header = value.header := rfl

@[simp] theorem flow_header_unchanged (rows : List Row) (value : FlowValue) :
    (rewriteFlow rows value).header = value.header := rfl

theorem active_taint_observations_preserved (s : Snapshot) (rows : List Row) (value : TaintValue)
    (coverage : ∀ row, row ∈ rows → ResetCoverage s row) :
    taintObservations s (rewriteTaint (metadataRows s rows) value) =
      taintObservations s (rewriteTaint rows value) := by
  have masks := projected_reset_mask_equal s value.header.base rows coverage
  have equal := projected_rewrite_depends_only_on_projected_resets s _ _ value.marks masks
  exact congrArg (fun marks => marks.map fun mark => (value.header, mark)) equal

theorem active_flow_observation_preserved (s : Snapshot) (rows : List Row) (value : FlowValue)
    (coverage : ∀ row, row ∈ rows → ResetCoverage s row) :
    flowObservation s (rewriteFlow (metadataRows s rows) value) =
      flowObservation s (rewriteFlow rows value) := by
  have masks := projected_reset_mask_equal s value.header.base rows coverage
  simp only [flowObservation, rewriteFlow, project_marks_append]
  rw [masks]

theorem active_mark_check_preserved (s : Snapshot) (rows : List Row) (value : TaintValue)
    (coverage : ∀ row, row ∈ rows → ResetCoverage s row)
    (mark : FullMark) (visible : observableMark s mark = true) :
    mark ∈ (rewriteTaint (metadataRows s rows) value).marks ↔
      mark ∈ (rewriteTaint rows value).marks := by
  have masks := projected_reset_mask_equal s value.header.base rows coverage
  have equal := projected_rewrite_depends_only_on_projected_resets s _ _ value.marks masks
  have first := observable_project_membership s mark
    (rewriteTaint (metadataRows s rows) value).marks visible
  have second := observable_project_membership s mark (rewriteTaint rows value).marks visible
  change mark ∈ projectMarks s (rewriteMarks _ _) ↔ _ at first
  rw [equal] at first
  exact first.symm.trans second

theorem active_negative_mark_check_preserved (s : Snapshot) (rows : List Row) (value : TaintValue)
    (coverage : ∀ row, row ∈ rows → ResetCoverage s row)
    (mark : FullMark) (visible : observableMark s mark = true) :
    (!decide (mark ∈ (rewriteTaint (metadataRows s rows) value).marks)) =
      (!decide (mark ∈ (rewriteTaint rows value).marks)) := by
  simp only [active_mark_check_preserved s rows value coverage mark visible]

/- Executable witnesses. All rows use complete identities; equal serialized
   IDs do not collapse variants, and equal owner names with different primitive
   properties remain distinct. These controls are not runtime AP tests. -/
namespace Controls

def owner : OwnerKey := ⟨1, true⟩
def otherOwner : OwnerKey := ⟨2, false⟩
def thirdOwner : OwnerKey := ⟨3, false⟩
def t : FullMark := ⟨owner, 10⟩
def u : FullMark := ⟨otherOwner, 20⟩
def v : FullMark := ⟨thirdOwner, 30⟩
def header : Header := ⟨[41, 42], 7, [8, 9], 0, 1, []⟩
def active : Graph := ⟨900, [owner], [t], [1, 2, 3], true, false⟩
def inactive : Graph := ⟨901, [otherOwner], [u], [4], false, false⟩
def snapshot : Snapshot := ⟨⟨11, 1⟩, [active, inactive], [1, 3], false⟩
def state : ProviderState := ⟨.analysis, some snapshot, [t, u]⟩
def resetT : Row := ⟨some 2, 80, 7, [t], [501, 502]⟩
def resetU : Row := ⟨some 4, 81, 7, [u], [503]⟩
def secondVariant : Row := ⟨some 2, 82, 7, [t], [504]⟩
def rows : List Row := [resetT, resetU, secondVariant, resetT]
def taint : TaintValue := ⟨header, [t]⟩
def flow : FlowValue := ⟨header, []⟩

/-- The relevant resolver relaxes the negative arity only in metadata.
    The positive T literal remains a real check in both functions. -/
def normalSelfLoop (arity : Nat) (input : List FullMark) : Bool :=
  decide (t ∈ input) && !decide (arity = 1)
def relevantSelfLoop (input : List FullMark) : Bool := decide (t ∈ input)
def sourceB (arity : Nat) (input : List FullMark) : List FullMark :=
  if executionKeeps snapshot (some 2) && normalSelfLoop arity input then [t] else []
def oldReturn : TaintValue := rewriteTaint [resetT] taint
def metadataReturn : TaintValue := rewriteTaint (metadataRows snapshot [resetT]) taint
def blindReturn : TaintValue :=
  rewriteTaint (selectedRows state .source .execution [resetT]) taint
def sinkF (value : TaintValue) : Bool := decide (t ∈ value.marks)

def sameOwnerGraph : Graph := ⟨1000, [owner], [u], [5], false, false⟩
def sameIdGraph : Graph := ⟨1001, [thirdOwner], [v], [5], false, false⟩
def sameMarkGraph : Graph := ⟨1002, [], [v], [6], false, false⟩
def sharing : Snapshot := {snapshot with
  graphs := [active, sameOwnerGraph, sameIdGraph, sameMarkGraph]}
def uncertainGraph : Graph := {inactive with uncertain := true}
def uncertainSnapshot : Snapshot := {snapshot with graphs := [active, uncertainGraph]}
def noSelection : ProviderState := ⟨.prescan, none, [t, u]⟩
def foreign : Snapshot := {snapshot with key := ⟨12, 1⟩}
def newer : Snapshot := {snapshot with key := ⟨11, 2⟩}
def ticket : SavedQuery := ⟨snapshot, .source, .metadata⟩

def checks : List Bool := [
  decide (selectedRows noSelection .source .execution rows = rows),
  decide (selectedRows noSelection .cleaner .metadata rows = rows),
  decide (selectedRows (enterPhase state .prescan) .source .execution rows = []),
  decide (selectedRows state .source .metadata rows = [resetT, secondVariant, resetT]),
  decide (copies resetT (selectedRows state .source .metadata rows) = 2),
  metadataKeeps snapshot none,
  metadataKeeps snapshot (some 999),
  !executionKeeps snapshot (some 999),
  decide (selectedRows state .cleaner .execution rows = rows),
  decide (selectedRows state .passThrough .metadata rows = rows),
  !normalSelfLoop 1 [t],
  relevantSelfLoop [t],
  decide (sourceB 1 [t] = []),
  executionKeeps snapshot (some 1),
  executionKeeps snapshot (some 3),
  metadataKeeps snapshot (some 2),
  !sinkF oldReturn,
  !sinkF metadataReturn,
  sinkF blindReturn,
  decide (oldReturn = metadataReturn),
  decide ((rewriteFlow [resetU] flow).mx ≠ (rewriteFlow (metadataRows snapshot [resetU]) flow).mx),
  decide (flowObservation snapshot (rewriteFlow [resetU] flow) =
    flowObservation snapshot (rewriteFlow (metadataRows snapshot [resetU]) flow)),
  metadataKeeps sharing (some 5),
  metadataKeeps sharing (some 6),
  metadataKeeps uncertainSnapshot (some 4),
  decide (metadataRows {snapshot with opaqueMetadata := true} rows = rows),
  decide (resume foreign ticket rows = none),
  decide (resume newer ticket rows = none),
  decide (resume snapshot ticket rows = some [resetT, secondVariant, resetT]),
  decide ((install noSelection snapshot).declarations = noSelection.declarations)
]

theorem checks_pass : checks.all id = true := by decide
theorem checks_count : checks.length = 30 := by decide

#eval checks.length
#eval checks.all id

end Controls

end ApSpec.SemgrepProviderSelection
