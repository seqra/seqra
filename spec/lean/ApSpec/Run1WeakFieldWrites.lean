/-
  Selected F1 weak field writes, DEMAND preservation and E-free publication.

  This local model is separate from the older request/static modules. A raw
  ordinary operation may produce either E representation (STAR/E or normal
  ANY-taint/E). The single publication boundary converts lost spatial precision
  to DEMAND first and canonicalizes STAR to ANY before clearing E. It does not
  erase mark exclusions or change the original premise, base, path or marks.

  The tagged default S-to-S schema is F1 may/guidance, not a propagated root
  initial, an executed root summary, a normal-return proof or a strong record.
  The write flag covers modeled logical mutations and unknown effects. The
  actual JVM producer inventory, AP tree codec, limits, ND and driver bridge are
  not certified by this finite reference model. Kind.any/NORMAL denotes the
  concrete must/ANY-taint case; Kind.any/DEMAND is may. Raw comparison inputs
  can carry E, while closed producer outputs cannot. Alias-target coverage is
  an external frontend obligation, not a consequence of the Boolean alias flag.
-/
import ApSpec.RequestGatedStatics

namespace ApSpec.Run1WeakFieldWrites
open ApSpec ApSpec.Run1Requests

/-- The second E is the annotation of a normal ANY-taint fact. Kind.star keeps
    its own E, as in the existing AP models. MarkA.starEx is a MARK exclusion. -/
structure Fact where
  original : Nat
  ap : PFact
  anyEx : Excl
  demand : Bool
  deriving DecidableEq, Repr

def emptyKindE : Kind → Prop
  | .star e => e = .empty
  | _ => True

def EmptyE (f : Fact) : Prop := emptyKindE f.ap.kind ∧ f.anyEx = .empty

def kindHasE : Kind → Bool
  | .star e => !e.isEmptyB
  | _ => false

def hasSpatialE (f : Fact) : Bool := kindHasE f.ap.kind || !f.anyEx.isEmptyB

def stripKindE : Kind → Kind
  | .star _ => .star .empty
  | k => k

/-- W2/W8 canonical DEMAND representation. EXACT keeps its geometry; both
    correlated STAR and normal ANY-taint become ordinary DEMAND ANY. -/
def demandKind : Kind → Kind
  | .exact => .exact
  | _ => .any

def publish (f : Fact) : Fact :=
  let d := f.demand || hasSpatialE f
  ⟨f.original,{f.ap with kind := if d then demandKind f.ap.kind else stripKindE f.ap.kind},
    .empty,d⟩

theorem publication_has_empty_E (f : Fact) : EmptyE (publish f) := by
  unfold EmptyE publish
  dsimp
  cases f.ap.kind <;> cases f.demand || hasSpatialE f <;> simp [emptyKindE, demandKind, stripKindE]

theorem publication_preserves_full_geometry (f : Fact) :
    (publish f).original = f.original ∧ (publish f).ap.base = f.ap.base ∧
      (publish f).ap.path = f.ap.path ∧ (publish f).ap.mark = f.ap.mark := ⟨rfl,rfl,rfl,rfl⟩

theorem publication_layer_monotone (f : Fact) (demand : f.demand = true) :
    (publish f).demand = true := by simp [publish,demand]

theorem spatial_precision_loss_is_DEMAND (f : Fact) (loss : hasSpatialE f = true) :
    (publish f).demand = true := by simp [publish,loss]

theorem demand_STAR_is_ANY (f : Fact) (e : Excl)
    (star : f.ap.kind = .star e) (demand : (publish f).demand = true) :
    (publish f).ap.kind = .any := by
  simp only [publish] at demand ⊢
  rw [demand,star]
  rfl

theorem exact_geometry_stays_EXACT (f : Fact) (exact : f.ap.kind = .exact) :
    (publish f).ap.kind = .exact := by
  simp [publish,exact,demandKind,stripKindE]

theorem no_demand_STAR_publication (f : Fact) (e : Excl)
    (demand : (publish f).demand = true) : (publish f).ap.kind ≠ .star e := by
  simp only [publish] at demand ⊢
  rw [demand]
  cases f.ap.kind <;> simp [demandKind]

private theorem isEmptyB_exact (e : Excl) : e.isEmptyB = true ↔ e = .empty := by
  cases e with
  | univ => simp [Excl.isEmptyB,Excl.empty]
  | set xs => cases xs <;> simp [Excl.isEmptyB,Excl.empty]

theorem hasSpatialE_false_iff (f : Fact) : hasSpatialE f = false ↔ EmptyE f := by
  have notFalse (b : Bool) : (!b) = false ↔ b = true := by cases b <;> decide
  cases hk : f.ap.kind <;>
    simp [hasSpatialE,kindHasE,EmptyE,emptyKindE,hk,notFalse,isEmptyB_exact]

theorem normal_publication_does_not_forget_E (f : Fact)
    (normal : (publish f).demand = false) :
    f.demand = false ∧ EmptyE f ∧ (publish f).ap = f.ap := by
  have bits : f.demand = false ∧ hasSpatialE f = false := by
    simpa only [publish,Bool.or_eq_false_iff] using normal
  have empty := (hasSpatialE_false_iff f).mp bits.2
  refine ⟨bits.1,empty,?_⟩
  have strip : stripKindE f.ap.kind = f.ap.kind := by
    cases hk : f.ap.kind with
    | exact => rfl
    | any => rfl
    | star e =>
      have he : e = .empty := by simpa [emptyKindE,hk] using empty.1
      simp [stripKindE,he]
  change {f.ap with kind := if f.demand || hasSpatialE f then demandKind f.ap.kind else stripKindE f.ap.kind} = f.ap
  rw [bits.1,bits.2]
  simp only [Bool.false_or,Bool.false_eq_true,↓reduceIte,strip]

theorem EEmpty_publication_keeps_layer (f : Fact) (empty : EmptyE f) :
    (publish f).demand = f.demand := by
  have e := (hasSpatialE_false_iff f).mpr empty
  simp [publish,e]


/-- Ordinary location membership, independent of normal/DEMAND certainty. -/
def possible (f : Fact) (location : Loc) : Prop :=
  location.base = f.ap.base ∧ f.ap.mark.admits location.mark ∧
    ∃ rest, location.path = f.ap.path ++ rest ∧
      admitsTailB f.ap.kind rest = true ∧
      (f.ap.kind = .any → f.anyEx.admits rest = true)

theorem publication_is_may_cover (f : Fact) (location : Loc) (h : possible f location) :
    possible (publish f) location := by
  obtain ⟨hb,hm,rest,hp,ht,he⟩ := h
  refine ⟨hb,hm,rest,hp,?_,?_⟩
  · cases hk : f.ap.kind <;> cases hd : f.demand || hasSpatialE f <;>
      simp_all [publish,stripKindE,demandKind,admitsTailB,CoreAux.empty_admits]
  · intro _
    exact CoreAux.empty_admits rest

/-- A NORMAL carrier is stronger than a DEMAND may carrier. This predicate is
    used only to distinguish the unsound NORMAL-erasure/weak-keep examples. -/
def normalClaim (f : Fact) (location : Loc) : Prop := f.demand = false ∧ possible f location

def cut (counted : Acc → Bool) (limit : Nat) (input : Fact) : Fact :=
  let bounded := limitF counted limit ⟨input.ap,input.demand⟩
  publish ⟨input.original,bounded.fact,input.anyEx,bounded.demand⟩

theorem cut_output_EEmpty (counted : Acc → Bool) (limit : Nat) (input : Fact) :
    EmptyE (cut counted limit input) := publication_has_empty_E _

inductive Target where
  | instance (base field : Nat) (suffix : List Acc)
  | static (cls field : Nat) (suffix : List Acc)
  deriving DecidableEq, Repr

def Target.base : Target → Base
  | .instance b _ _ => b+2
  | .static _ _ _ => 1

def Target.path : Target → List Acc
  | .instance _ f rest => f::rest
  | .static c f rest => [2*c,2*f+1] ++ rest

def Target.region (target : Target) : PFact :=
  ⟨target.base,target.path,.any,.star⟩

/-- Syntactic equality is always considered, independently of alias metadata.
    An additional alias result is a conservative MAY-alias input to the model. -/
def overwriteRelevant (mayAlias : Bool) (target : Target) (fact : Fact) : Bool :=
  (mayAlias || Nat.beq target.base fact.ap.base) &&
    overlapB {fact.ap with base := target.base} target.region

def writtenLocation (target : Target) (location : Loc) : Bool :=
  Nat.beq target.base location.base && (dropPrefix target.path location.path).isSome

def strongOldSurvivors (target : Target) (locations : List Loc) : List Loc :=
  locations.filter (fun location => !writtenLocation target location)

def weakKeep (mayAlias : Bool) (target : Target) (input : Fact) : Fact :=
  let admitted := publish input
  publish {admitted with demand := admitted.demand || overwriteRelevant mayAlias target admitted}

theorem weak_keep_no_new_E (mayAlias : Bool) (target : Target) (input : Fact) :
    EmptyE (weakKeep mayAlias target input) := publication_has_empty_E _

theorem weak_keep_preserves_origin_base_path_mark (mayAlias : Bool) (target : Target) (input : Fact) :
    (weakKeep mayAlias target input).original = input.original ∧
      (weakKeep mayAlias target input).ap.base = input.ap.base ∧
      (weakKeep mayAlias target input).ap.path = input.ap.path ∧
      (weakKeep mayAlias target input).ap.mark = input.ap.mark := ⟨rfl,rfl,rfl,rfl⟩

theorem weak_keep_is_may_cover (alias : Bool) (target : Target) (input : Fact)
    (location : Loc) (h : possible input location) :
    possible (weakKeep alias target input) location :=
  publication_is_may_cover _ location (publication_is_may_cover input location h)

theorem affected_keep_is_DEMAND (mayAlias : Bool) (target : Target) (input : Fact)
    (affected : overwriteRelevant mayAlias target (publish input) = true) :
    (weakKeep mayAlias target input).demand = true := by
  apply publication_layer_monotone
  change ((publish input).demand || overwriteRelevant mayAlias target (publish input)) = true
  rw [affected]
  exact Bool.or_true _

theorem weak_keep_layer_monotone (mayAlias : Bool) (target : Target) (input : Fact)
    (demand : input.demand = true) : (weakKeep mayAlias target input).demand = true := by
  simp [weakKeep,publish,demand]

theorem unaffected_keep_keeps_layer (mayAlias : Bool) (target : Target) (input : Fact)
    (unaffected : overwriteRelevant mayAlias target (publish input) = false) :
    (weakKeep mayAlias target input).demand = (publish input).demand := by
  have empty := publication_has_empty_E input
  cases hk : input.ap.kind <;> cases hd : input.demand || hasSpatialE input <;>
    simp_all [weakKeep,publish,hasSpatialE,kindHasE,demandKind,stripKindE,Excl.isEmptyB,Excl.empty]

def weakWrite (mayAlias : Bool) (target : Target) (input : Fact) (valueOutputs : List Fact) : List Fact :=
  weakKeep mayAlias target input :: valueOutputs.map publish

theorem weak_write_preservation_exists (alias : Bool) (target : Target) (input : Fact)
    (values : List Fact) : weakKeep alias target input ∈ weakWrite alias target input values := by
  simp [weakWrite]

theorem fresh_value_layer_not_demoted_by_store (alias : Bool) (target : Target) (input value : Fact)
    (values : List Fact) (hv : value ∈ values) : publish value ∈ weakWrite alias target input values := by
  exact List.mem_cons_of_mem _ (List.mem_map.mpr ⟨value,hv,rfl⟩)

theorem fresh_value_keeps_its_own_layer (value : Fact) (empty : EmptyE value) :
    (publish value).demand = value.demand := EEmpty_publication_keeps_layer value empty

theorem weak_write_every_output_EEmpty (alias : Bool) (target : Target) (input output : Fact)
    (values : List Fact) (h : output ∈ weakWrite alias target input values) : EmptyE output := by
  rcases List.mem_cons.mp h with rfl | h
  · exact weak_keep_no_new_E alias target input
  · obtain ⟨raw,_,rfl⟩ := List.mem_map.mp h
    exact publication_has_empty_E raw

/-- The ordinary strong field store removes old destination locations, then
    unions the independently computed pre-store value outputs. -/
def strongWriteContains (target : Target) (input : Fact) (values : List Fact) (location : Loc) : Prop :=
  (possible input location ∧ writtenLocation target location = false) ∨
    ∃ value, value ∈ values ∧ possible value location

theorem weakWrite_covers_strong_output (alias : Bool) (target : Target) (input : Fact)
    (values : List Fact) (location : Loc) (h : strongWriteContains target input values location) :
    ∃ output, output ∈ weakWrite alias target input values ∧ possible output location := by
  rcases h with ⟨old,_⟩ | ⟨value,present,holds⟩
  · exact ⟨weakKeep alias target input,weak_write_preservation_exists alias target input values,
      weak_keep_is_may_cover alias target input location old⟩
  · exact ⟨publish value,fresh_value_layer_not_demoted_by_store alias target input value values present,
      publication_is_may_cover value location holds⟩

/-! A direct selected-mark cleaner stage is distinct from a logical field
    overwrite. Field RemoveAll and cleaner/rewriter writeback use weakWrite;
    this direct stage can drop its selected mark exactly. -/

def withoutMark (mark : MarkA) (selected : Mark) : Option MarkA := match mark with
  | .conc t => if t = selected then none else some mark
  | .star => some (.starEx [selected])
  | .starEx excluded => some (.starEx (selected::excluded))

def cleanWholeMark (selected : Mark) (input : Fact) : List Fact :=
  match withoutMark input.ap.mark selected with
  | none => []
  | some mark => [publish {input with ap := {input.ap with mark := mark}}]

theorem matching_concrete_cleaner_drops (selected : Mark) (input : Fact)
    (mark : input.ap.mark = .conc selected) : cleanWholeMark selected input = [] := by
  simp [cleanWholeMark,withoutMark,mark]

theorem cleaner_never_keeps_selected_mark (selected : Mark) (input output : Fact)
    (h : output ∈ cleanWholeMark selected input) : ¬ output.ap.mark.admits selected := by
  unfold cleanWholeMark at h
  cases hm : withoutMark input.ap.mark selected with
  | none => simp [hm] at h
  | some mark =>
    simp only [hm,List.mem_singleton] at h
    subst output
    cases hk : input.ap.mark with
    | conc t =>
      simp only [withoutMark,hk] at hm
      split at hm
      · cases hm
      · simp only [Option.some.injEq] at hm
        subst mark
        simpa [publish,MarkA.admits] using Ne.symm (by assumption : t ≠ selected)
    | star =>
      have eq : mark = .starEx [selected] := by simpa [withoutMark,hk] using hm.symm
      subst mark
      simp [publish,MarkA.admits,memB]
    | starEx excluded =>
      have eq : mark = .starEx (selected::excluded) := by simpa [withoutMark,hk] using hm.symm
      subst mark
      simp [publish,MarkA.admits,memB]

/-! Every modeled producer goes through the E-free publication boundary. Raw
    partial-clean results may contain E; they get a DEMAND may envelope. -/

inductive Producer where
  | generated (raw : Fact)
  | valueTransfer (raw : Fact)
  | partialResidual (raw : Fact)
  | weakStore (alias : Bool) (target : Target) (input : Fact) (values : List Fact)
  | markClean (mark : Mark) (input : Fact)
  deriving DecidableEq, Repr

def emitted : Producer → List Fact
  | .generated raw | .valueTransfer raw | .partialResidual raw => [publish raw]
  | .weakStore alias target input values => weakWrite alias target input values
  | .markClean mark input => cleanWholeMark mark input

theorem every_producer_EEmpty (producer : Producer) (output : Fact)
    (h : output ∈ emitted producer) : EmptyE output := by
  cases producer with
  | generated raw | valueTransfer raw | partialResidual raw =>
    simp only [emitted,List.mem_singleton] at h
    subst output
    exact publication_has_empty_E raw
  | weakStore alias target input values => exact weak_write_every_output_EEmpty alias target input output values h
  | markClean mark input =>
    change output ∈ cleanWholeMark mark input at h
    cases hm : withoutMark input.ap.mark mark with
    | none => simp [cleanWholeMark,hm] at h
    | some remaining =>
      simp only [cleanWholeMark,hm,List.mem_singleton] at h
      subst output
      exact publication_has_empty_E _

theorem all_queued_producer_results_EEmpty (producers : List Producer) (output : Fact)
    (h : output ∈ producers.flatMap emitted) : EmptyE output := by
  obtain ⟨producer,_,emitted⟩ := List.mem_flatMap.mp h
  exact every_producer_EEmpty producer output emitted

def started (shape : RequestGatedStatics.InitialGrammar.Shape) (original : Nat) : Fact :=
  let initial := startFact (RequestGatedStatics.InitialGrammar.toAP shape)
  publish ⟨original,initial.fact,.empty,initial.demand⟩

theorem every_started_fact_EEmpty (shape : RequestGatedStatics.InitialGrammar.Shape) (original : Nat) :
    EmptyE (started shape original) := publication_has_empty_E _

/-! Shared transitive logical-static-write flag. The graph convention is the
    already-checked caller-to-callee graph used by Run1Requests. -/

inductive Mutation where
  | none
  | ordinaryWrite (target : Target)
  | staticAliasWrite
  | staticCleaner (scope : Scope)
  | staticRemoveAll (scope : Scope)
  | staticRewriter (scope : Scope)
  | unknownEffect
  deriving DecidableEq, Repr

def mayWriteStatic : Mutation → Bool
  | .none => false
  | .ordinaryWrite (.instance _ _ _) => false
  | .ordinaryWrite (.static _ _ _) => true
  | _ => true

def localWrite (body : List Mutation) : Bool := body.any mayWriteStatic

def closedWrite {n : Nat} (graph : Graph n) (locals : Fin n → Bool) (owner : Fin n) : Bool :=
  (List.finRange n).any fun producer => decide (owner ∈ closure graph producer) && locals producer

theorem closedWrite_has_contributor {n : Nat} (graph : Graph n) (locals : Fin n → Bool)
    (owner : Fin n) : closedWrite graph locals owner = true ↔
      ∃ producer, owner ∈ closure graph producer ∧ locals producer = true := by
  simp [closedWrite,List.any_eq_true,Bool.and_eq_true]

theorem local_static_write_in_closed {n : Nat} (graph : Graph n) (locals : Fin n → Bool)
    (owner : Fin n) (write : locals owner = true) : closedWrite graph locals owner = true := by
  exact (closedWrite_has_contributor graph locals owner).mpr ⟨owner,closure_seed graph owner,write⟩

theorem recursive_write_inherited {n : Nat} (graph : Graph n) (locals : Fin n → Bool)
    (owner producer : Fin n) (path : Reaches graph producer owner) (write : locals producer = true) :
    closedWrite graph locals owner = true := by
  exact (closedWrite_has_contributor graph locals owner).mpr
    ⟨producer,recursive_call_coverage graph producer owner path,write⟩

theorem no_local_writes_no_cycle_invention {n : Nat} (graph : Graph n) (owner : Fin n) :
    closedWrite graph (fun _ => false) owner = false := by simp [closedWrite]

theorem closedWrite_call {n : Nat} (graph : Graph n) (locals : Fin n → Bool)
    (caller callee : Fin n) (edge : (caller,callee) ∈ graph)
    (write : closedWrite graph locals callee = true) : closedWrite graph locals caller = true := by
  obtain ⟨producer,hp,hl⟩ := (closedWrite_has_contributor graph locals callee).mp write
  exact (closedWrite_has_contributor graph locals caller).mpr
    ⟨producer,closure_call graph producer caller callee edge hp,hl⟩

theorem closedWrite_false_exact {n : Nat} (graph : Graph n) (locals : Fin n → Bool)
    (owner : Fin n) : closedWrite graph locals owner = false ↔
      ∀ producer, owner ∈ closure graph producer → locals producer = false := by
  constructor
  · intro noWrite producer reachable
    cases hw : locals producer
    · rfl
    · have yes := (closedWrite_has_contributor graph locals owner).mpr ⟨producer,reachable,hw⟩
      rw [noWrite] at yes
      cases yes
  · intro none
    cases flag : closedWrite graph locals owner
    · rfl
    · obtain ⟨producer,reachable,write⟩ := (closedWrite_has_contributor graph locals owner).mp flag
      rw [none producer reachable] at write
      cases write

/-- A competitor need only satisfy the direct seed and call-union rules. The
    leastness proof does not assume that the desired closed result is present. -/
theorem closedWrite_least {n : Nat} (graph : Graph n) (locals flags : Fin n → Bool)
    (seeds : ∀ method, locals method = true → flags method = true)
    (calls : ∀ caller callee, (caller,callee) ∈ graph → flags callee = true → flags caller = true)
    (owner : Fin n) (write : closedWrite graph locals owner = true) : flags owner = true := by
  obtain ⟨producer,reachable,localWrite⟩ := (closedWrite_has_contributor graph locals owner).mp write
  let selected := (List.finRange n).filter flags
  have membership (method : Fin n) : method ∈ selected ↔ flags method = true := by simp [selected]
  have closed : Run1Requests.closed graph producer selected = true := by
    apply (Bool.and_eq_true _ _).mpr
    refine ⟨by simpa [membership] using seeds producer localWrite,?_⟩
    apply List.all_eq_true.mpr
    intro edge present
    apply decide_eq_true
    intro callee
    exact (membership edge.1).mpr (calls edge.1 edge.2 present ((membership edge.2).mp callee))
  exact (membership owner).mp (closure_least graph producer selected closed owner reachable)

/-! Tagged default schema, never an InitialAp or an ordinary executed edge. -/

structure DefaultSchema where
  mayWrite : Bool
  deriving DecidableEq, Repr

def defaultFor {n : Nat} (graph : Graph n) (locals : Fin n → Bool) (owner : Fin n) : DefaultSchema :=
  ⟨closedWrite graph locals owner⟩

def instantiateDefault (schema : DefaultSchema) (actual : Fact) : Fact :=
  publish {actual with demand := actual.demand || schema.mayWrite}

def applyStaticDefault (schema : DefaultSchema) (actual : Fact) : Option Fact :=
  if actual.ap.base = 1 then some (instantiateDefault schema actual) else none

theorem static_default_applies_only_to_static (schema : DefaultSchema) (actual output : Fact)
    (h : applyStaticDefault schema actual = some output) : actual.ap.base = 1 := by
  unfold applyStaticDefault at h
  split at h
  · assumption
  · cases h

theorem static_actual_receives_default (schema : DefaultSchema) (actual : Fact)
    (static : actual.ap.base = 1) :
    applyStaticDefault schema actual = some (instantiateDefault schema actual) := by
  simp [applyStaticDefault,static]

theorem default_instantiates_actual_geometry (schema : DefaultSchema) (actual : Fact) :
    (instantiateDefault schema actual).original = actual.original ∧
      (instantiateDefault schema actual).ap.base = actual.ap.base ∧
      (instantiateDefault schema actual).ap.path = actual.ap.path ∧
      (instantiateDefault schema actual).ap.mark = actual.ap.mark := ⟨rfl,rfl,rfl,rfl⟩

theorem writing_default_demotes_actual (actual : Fact) :
    (instantiateDefault ⟨true⟩ actual).demand = true := by simp [instantiateDefault,publish]

theorem default_never_creates_empty_root (schema : DefaultSchema) (actual : Fact)
    (field : actual.ap.path ≠ []) : (instantiateDefault schema actual).ap.path ≠ [] := field

theorem default_is_EEmpty (schema : DefaultSchema) (actual : Fact) :
    EmptyE (instantiateDefault schema actual) := publication_has_empty_E _

structure DefaultCall where
  continuation : Fact
  supplied : Fact
  deriving DecidableEq, Repr

/-- Only the local may continuation joins the default layer. Callee delivery
    retains the actual pre-default fact, its premise and its own layer. -/
def defaultCall (schema : DefaultSchema) (actual : Fact) : DefaultCall :=
  ⟨instantiateDefault schema actual,actual⟩

theorem callee_delivery_is_pre_default (schema : DefaultSchema) (actual : Fact) :
    (defaultCall schema actual).supplied = actual := rfl

theorem default_call_EEmpty (schema : DefaultSchema) (actual : Fact) (inputEmpty : EmptyE actual) :
    EmptyE (defaultCall schema actual).continuation ∧ EmptyE (defaultCall schema actual).supplied :=
  ⟨default_is_EEmpty schema actual,inputEmpty⟩

theorem default_layer_is_join (schema : DefaultSchema) (actual : Fact) (empty : EmptyE actual) :
    (instantiateDefault schema actual).demand = (actual.demand || schema.mayWrite) := by
  have unchanged : hasSpatialE {actual with demand := actual.demand || schema.mayWrite} = false :=
    (hasSpatialE_false_iff _).mpr empty
  simp [instantiateDefault,publish,unchanged]

theorem default_NORMAL_iff_no_reachable_write {n : Nat} (graph : Graph n)
    (locals : Fin n → Bool) (owner : Fin n) : (defaultFor graph locals owner).mayWrite = false ↔
      ∀ producer, owner ∈ closure graph producer → locals producer = false :=
  closedWrite_false_exact graph locals owner

def joinDefaults (a b : DefaultSchema) : DefaultSchema := ⟨a.mayWrite || b.mayWrite⟩

theorem layer_join_associative (a b c : Bool) : (a || b) || c = a || (b || c) := by
  cases a <;> cases b <;> cases c <;> rfl

theorem layer_join_idempotent (a : Bool) : a || a = a := by cases a <;> rfl

theorem writing_alternative_cannot_become_NORMAL (a : DefaultSchema) :
    (joinDefaults a ⟨true⟩).mayWrite = true := by simp [joinDefaults]

/-- A template is a guide only. It contributes no root initial at any time. -/
def templateInitials (_ : DefaultSchema) : List PFact := []

theorem no_propagated_root_initial (schema : DefaultSchema) :
    specialRoot ∉ templateInitials schema := by simp [templateInitials]

structure GuidedQuery where
  actual : Fact
  default : DefaultSchema
  deriving DecidableEq, Repr

/-- Backward authorization borrows the actual requirement including its E.
    It does not run F1 publication normalization on later strong query data. -/
def authorizeBackward (schema : DefaultSchema) (requirement : Fact) : Option GuidedQuery :=
  if requirement.ap.base = 1 then some ⟨requirement,schema⟩ else none

theorem backward_authorization_preserves_full_AP (schema : DefaultSchema) (requirement : Fact)
    (query : GuidedQuery) (h : authorizeBackward schema requirement = some query) :
    query.actual = requirement := by
  unfold authorizeBackward at h
  split at h
  · simp only [Option.some.injEq] at h
    subst query
    rfl
  · cases h

theorem backward_authorization_no_field_enumeration (schema : DefaultSchema) (requirement : Fact)
    (query : GuidedQuery) (h : authorizeBackward schema requirement = some query) :
    query.actual.ap.path = requirement.ap.path := by
  rw [backward_authorization_preserves_full_AP schema requirement query h]

theorem backward_static_requirement_authorized (schema : DefaultSchema) (requirement : Fact)
    (static : requirement.ap.base = 1) : authorizeBackward schema requirement = some ⟨requirement,schema⟩ := by
  simp [authorizeBackward,static]

theorem later_query_not_vetoed_by_default (_schema : DefaultSchema)
    (pending : List RequestGatedStatics.Query) (query : RequestGatedStatics.Query) :
    query ∈ RequestGatedStatics.admitActualQuery [] pending query :=
  RequestGatedStatics.actual_query_admitted [] pending query

/-! Executable distinguishing controls. -/

namespace Controls
def broadX : Fact := ⟨9,⟨4,[],.star .empty,.star⟩,.empty,false⟩
def exactX : Fact := ⟨9,⟨4,[],.exact,.conc 7⟩,.empty,false⟩
def fieldF : Fact := ⟨9,⟨1,[2,3],.star .empty,.star⟩,.empty,false⟩
def concreteField : Fact := ⟨9,⟨1,[2,3],.exact,.conc 7⟩,.empty,false⟩
def excludedFlow : Fact := ⟨9,⟨4,[],.star (.set [5]),.star⟩,.empty,false⟩
def excludedMust : Fact := ⟨9,⟨4,[],.any,.conc 7⟩,.set [5],false⟩
def excludedAnyTaint : Fact := ⟨9,⟨4,[],.any,.conc 7⟩,.set [5],false⟩
def generatedAny : Fact := ⟨0,⟨4,[],.any,.conc 7⟩,.empty,false⟩
def partialAfterGEN : Fact := {generatedAny with anyEx := .set [5]}
def newValue : Fact := ⟨10,⟨4,[5],.exact,.conc 8⟩,.empty,false⟩
def unsafeErase (f : Fact) : Fact := {f with ap := {f.ap with kind := stripKindE f.ap.kind},anyEx := .empty}

theorem broad_instance_preservation_DEMAND_ANY :
    weakKeep false (.instance 2 5 []) broadX = {broadX with ap := {broadX.ap with kind := .any},demand := true} := by decide

theorem exact_ancestor_not_affected : weakKeep false (.instance 2 5 []) exactX = exactX := by decide

theorem exact_field_target_preserved_DEMAND :
    let old := {exactX with ap := {exactX.ap with path := [5]}}
    weakKeep false (.instance 2 5 []) old = {old with demand := true} := by decide

theorem broad_partial_overwrite_NORMAL_would_be_unsound :
    normalClaim broadX ⟨4,[5],7⟩ ∧
      strongOldSurvivors (.instance 2 5 []) [⟨4,[5],7⟩] = [] ∧
      ¬ normalClaim (weakKeep false (.instance 2 5 []) broadX) ⟨4,[5],7⟩ := by
  simp [normalClaim,possible,broadX,weakKeep,publish,overwriteRelevant,
    Target.base,Target.region,Target.path,overlapB,relate,dropPrefix,admitsTailB,
    hasSpatialE,kindHasE,Excl.isEmptyB,Excl.empty,Excl.admits,MarkA.admits,
    strongOldSurvivors,writtenLocation,demandKind,stripKindE,memB]

theorem old_and_fresh_value_keep_separate_layers :
    weakWrite false (.instance 2 5 []) broadX [newValue] =
      [{broadX with ap := {broadX.ap with kind := .any},demand := true},newValue] := by decide

theorem defaultD_keeps_field_geometry_and_demotes :
    instantiateDefault ⟨true⟩ fieldF = {fieldF with ap := {fieldF.ap with kind := .any},demand := true} := by decide

theorem unsafe_E_erase_invents_normal_location :
    ¬ possible excludedMust ⟨4,[5],7⟩ ∧
      possible (unsafeErase excludedMust) ⟨4,[5],7⟩ ∧
      (unsafeErase excludedMust).demand = false := by
  simp [possible,excludedMust,unsafeErase,stripKindE,admitsTailB,Excl.admits,Excl.empty,memB,MarkA.admits]

theorem safe_E_fallback_is_DEMAND_ANY :
    (publish excludedFlow).demand = true ∧ (publish excludedFlow).ap.kind = .any ∧
      (publish excludedAnyTaint).demand = true ∧ EmptyE (publish excludedAnyTaint) := by
  simp [excludedFlow,excludedAnyTaint,publish,hasSpatialE,kindHasE,Excl.isEmptyB,demandKind,EmptyE,emptyKindE,Excl.empty]

/-- GEN creates a current ANY-taint fact, not an ANY initial. A reference
    partial cleaner can subsequently require ANY-taint/E, so weak stores alone
    cannot establish the global EEmpty invariant. -/
theorem generated_ANY_then_partial_residual :
    emitted (.generated generatedAny) = [generatedAny] ∧
      emitted (.partialResidual partialAfterGEN) = [{generatedAny with demand := true}] := by decide

theorem generated_ANY_is_not_an_initial :
    ¬ RequestGatedStatics.InitialGrammar.LegalAP generatedAny.ap := by
  intro initial
  exact RequestGatedStatics.InitialGrammar.legalAP_no_ANY _ initial rfl

theorem cut_can_change_path_but_is_DEMAND_EEmpty :
    let input : Fact := ⟨9,⟨4,[5,6],.exact,.conc 7⟩,.empty,false⟩
    (cut (fun _ => true) 1 input).ap.path = [5] ∧
      (cut (fun _ => true) 1 input).demand = true ∧ EmptyE (cut (fun _ => true) 1 input) := by
  dsimp only
  exact ⟨by decide,by decide,cut_output_EEmpty _ _ _⟩

theorem cleaner_not_weak_keep : cleanWholeMark 7 concreteField = [] := by decide

theorem cleaner_must_be_in_effect_inventory :
    localWrite [.staticCleaner (.field 1 2)] = true ∧
      cleanWholeMark 7 concreteField = [] ∧ instantiateDefault ⟨false⟩ concreteField = concreteField := by decide

/-- Executable W2 guard for these controls, including valid excluded reference
    residuals. It is not a proof of every arbitrary Raw Fact's well-formedness. -/
def referenceW2 (f : Fact) : Bool :=
  if f.demand then match f.ap.kind with | .star _ => false | _ => true
  else match f.ap.kind,f.ap.mark with
    | .star _,.star | .star _,.starEx _ => true
    | .exact,.conc _ | .any,.conc _ => true
    | _,_ => false

theorem all_control_inputs_obey_W2 :
    [broadX,exactX,fieldF,concreteField,excludedFlow,excludedMust,
      excludedAnyTaint,generatedAny,partialAfterGEN,newValue].all referenceW2 = true := by decide

def cycle : Graph 3 := [(0,1),(1,0),(1,2)]
def writes (method : Fin 3) : Bool := method.val == 2

theorem recursive_multiple_callee_write :
    (defaultFor cycle writes 0).mayWrite = true ∧
      (defaultFor cycle writes 1).mayWrite = true ∧
      (defaultFor cycle (fun _ => false) 0).mayWrite = false := by decide

theorem cleaner_and_unknown_are_static_mutations :
    localWrite [.staticCleaner (.field 1 2)] = true ∧
      localWrite [.unknownEffect] = true ∧
      localWrite [.ordinaryWrite (.instance 2 5 [])] = false := by decide

theorem template_is_not_return_evidence :
    instantiateDefault ⟨false⟩ fieldF = fieldF ∧
      RequestGatedStatics.run RequestGatedStatics.strong .abort
        (.fact ⟨(1,2),[],7,false⟩) = [] := by decide

end Controls
end ApSpec.Run1WeakFieldWrites
