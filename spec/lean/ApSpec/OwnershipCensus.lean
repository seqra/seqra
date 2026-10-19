/-
  Finite ownership-census cache model; NOT a provider purity proof.

  Fin n denotes complete certified replay scopes: provider/view and selection,
  model/body generation, role, method/field subject, allRelevant and no-END view.
  Statement/fact/initialFacts may be omitted only under a full-chain certificate
  for the actual null-input census. Binding must preserve ordered candidates,
  specializations and generated rule/action identities before local counters.
  Stable means equal ordered results and no observable binding side effects;
  pointer equality is neither required nor assumed. This abstract stable oracle
  does not certify Spring/combined/StringConcat/custom providers. Unsupported
  scopes use ordinary cached access. No early selection or ownership timing
  change is authorized. A session is discarded before the engine starts.
  Trace/acquisition/consume factorization is an observational oracle. Preserve
  each role's existing materialization/observation boundary (including eager
  sink copying before any observation); neither a new streaming nor buffering
  order is authorized. Stable certificates include exact failure prefixes and
  existing wrapper clone/cache-state boundaries. The inherited late-mark and
  generation drain is an adapter, not a new replay mechanism proved here.
-/
import ApSpec.GroupPrimitiveMetadata

namespace ApSpec.OwnershipCensus
set_option autoImplicit false

/-- Claim also models the INITIAL ordered markOwners sidecar. Actual query
    replay normally contains reserve/name events only; preserve that boundary. -/
inductive Observation where
  | reserve (name : Nat) (primitive : Bool)
  | claim (name : Nat) (primitive : Bool) (owner : Nat)
  deriving DecidableEq, Repr
abbrev Trace := List Observation

structure Identity where
  rule : Nat
  actions : List Nat
  deriving DecidableEq, Repr

structure Meaning where
  identities : List Identity
  trace : Trace
  deriving DecidableEq, Repr

structure Bound where
  meaning : Meaning
  /-- Abstract bound-payload root labels, not actual JVM bytes/unique objects. -/
  payloadRoots : List Nat
  deriving DecidableEq, Repr

inductive Resolution where
  | success (bound : Bound)
  /-- A lazy/binding failure can follow an already observed observedPrefix. -/
  | failure (observedPrefix : Trace) (code : Nat)
  deriving DecidableEq, Repr

structure Neutral where
  trace : Trace
  failure : Option Nat
  deriving DecidableEq, Repr

def neutral : Resolution → Neutral
  | .success bound => ⟨bound.meaning.trace, none⟩
  | .failure observedPrefix code => ⟨observedPrefix, some code⟩

/-- Projection excludes object addresses but keeps every logical rule/action
    identity, ordered observation and provider-error prefix/code. -/
def logicalResult : Resolution → Except (Trace × Nat) Meaning
  | .success bound => .ok bound.meaning
  | .failure observedPrefix code => .error (observedPrefix,code)

abbrev Cache (n : Nat) := Fin n → Option Bound
abbrev Memo (n : Nat) := Fin n → Option Trace
abbrev Resolver (n : Nat) := Fin n → Resolution

/-- Explicit complete-chain capability assumption. σ is OBSERVABLE semantic
    provider state; harmless physical interning/cache warming may be quotiented
    out only by a separate source refinement argument. The certificate is not
    constructed for arbitrary existing wrappers by this model. -/
def StableProvider {n : Nat} {σ : Type} (reference : Resolver n)
    (bind : σ → Fin n → Resolution × σ) (authorized : Fin n → Bool) : Prop :=
  ∀ state q, authorized q = true →
    logicalResult (bind state q).1 = logicalResult (reference q) ∧ (bind state q).2 = state

theorem stable_rebuild_identities {n : Nat} {σ : Type} (reference : Resolver n)
    (bind : σ → Fin n → Resolution × σ) (authorized : Fin n → Bool)
    (certificate : StableProvider reference bind authorized) (state : σ) (q : Fin n)
    (allowed : authorized q = true) (old fresh : Bound)
    (referenceResult : reference q = .success old)
    (rebuilt : (bind state q).1 = .success fresh) :
    fresh.meaning.identities = old.meaning.identities ∧
      fresh.meaning.trace = old.meaning.trace ∧ (bind state q).2 = state := by
  have logical := (certificate state q allowed).1
  rw [rebuilt, referenceResult] at logical
  have same : fresh.meaning = old.meaning := Except.ok.inj logical
  exact ⟨congrArg Meaning.identities same, congrArg Meaning.trace same,
    (certificate state q allowed).2⟩

def put {n : Nat} {α : Type} (table : Fin n → Option α) (q : Fin n) (value : α) :=
  fun key => if key = q then some value else table key
@[simp] theorem put_same {n : Nat} {α : Type} (table : Fin n → Option α)
    (q : Fin n) (value : α) : put table q value q = some value := by simp [put]
@[simp] theorem put_other {n : Nat} {α : Type} (table : Fin n → Option α)
    (q key : Fin n) (value : α) (different : key ≠ q) :
    put table q value key = table key := by simp [put, different]

structure Session (n : Nat) where
  cache : Cache n
  memo : Memo n

structure OrdinaryResult (n : Nat) where
  value : Resolution
  cache : Cache n

/-- Exactly the ordinary cache: borrow hits INCLUDING empty lists; insert only
    successful misses. Failed materialization does not install a partial row. -/
def ordinary {n : Nat} (resolve : Resolver n) (cache : Cache n) (q : Fin n) : OrdinaryResult n :=
  match cache q with
  | some bound => ⟨.success bound, cache⟩
  | none => match resolve q with
    | .success bound => ⟨.success bound, put cache q bound⟩
    | .failure observedPrefix code => ⟨.failure observedPrefix code, cache⟩

structure Acquisition (n : Nat) where
  state : Session n
  output : Neutral
  /-- A proposal only; publish after SUCCESSFUL ownership observation. -/
  memoCandidate : Option (Fin n × Trace)

/-- The memo is neutral trace only, after full wrapper selection/observation
    order. No bound object/list is inserted on an authorized census miss. -/
def acquire {n : Nat} (resolve : Resolver n) (authorized : Fin n → Bool)
    (state : Session n) (q : Fin n) : Acquisition n :=
  match state.cache q with
  | some bound => ⟨state, neutral (.success bound), none⟩
  | none =>
    if authorized q then
      match state.memo q with
      | some trace => ⟨state, ⟨trace,none⟩, none⟩
      | none => match resolve q with
        | .success bound => ⟨state, neutral (.success bound), some (q,bound.meaning.trace)⟩
        | .failure observedPrefix code => ⟨state, neutral (.failure observedPrefix code), none⟩
    else
      let result := ordinary resolve state.cache q
      ⟨{state with cache := result.cache}, neutral result.value, none⟩

/-- Memo validity is a source adapter obligation on the COMPLETE key. Stable
    full identities must also be maintained by binding, not inferred from names. -/
def MemoValid {n : Nat} (resolve : Resolver n) (memo : Memo n) : Prop :=
  ∀ q trace, memo q = some trace → ∃ bound,
    resolve q = .success bound ∧ trace = bound.meaning.trace

theorem acquisition_exact {n : Nat} (resolve : Resolver n) (authorized : Fin n → Bool)
    (state : Session n) (q : Fin n) (valid : MemoValid resolve state.memo) :
    (acquire resolve authorized state q).output = neutral (ordinary resolve state.cache q).value := by
  cases cached : state.cache q with
  | some bound => simp [acquire, ordinary, cached]
  | none =>
    cases admitted : authorized q with
    | false => simp [acquire, cached, admitted]
    | true =>
      cases memo : state.memo q with
      | none => cases resolved : resolve q <;> simp [acquire, ordinary, cached, admitted, memo, resolved]
      | some trace =>
        obtain ⟨bound, resolved, same⟩ := valid q trace memo
        simp [acquire, ordinary, cached, admitted, memo, resolved, neutral, same]

theorem authorized_cache_unchanged {n : Nat} (resolve : Resolver n)
    (authorized : Fin n → Bool) (state : Session n) (q : Fin n)
    (admitted : authorized q = true) :
    (acquire resolve authorized state q).state.cache = state.cache := by
  cases cached : state.cache q with
  | some bound => simp [acquire, cached]
  | none =>
    cases memo : state.memo q with
    | some trace => simp [acquire, cached, admitted, memo]
    | none => cases resolved : resolve q <;> simp [acquire, cached, admitted, memo, resolved]

theorem preexisting_bound_preserved {n : Nat} (resolve : Resolver n)
    (authorized : Fin n → Bool) (state : Session n) (q key : Fin n) (bound : Bound)
    (present : state.cache key = some bound) :
    (acquire resolve authorized state q).state.cache key = some bound := by
  cases cached : state.cache q with
  | some hit => simp [acquire, cached, present]
  | none =>
    have different : key ≠ q := by intro same; subst key; simp [cached] at present
    cases admitted : authorized q with
    | true => rw [authorized_cache_unchanged resolve authorized state q admitted]; exact present
    | false =>
      cases resolved : resolve q <;>
        simp [acquire, ordinary, cached, admitted, resolved, different, present]

theorem unsupported_is_ordinary {n : Nat} (resolve : Resolver n)
    (authorized : Fin n → Bool) (state : Session n) (q : Fin n)
    (unsupported : authorized q = false) :
    (acquire resolve authorized state q).output = neutral (ordinary resolve state.cache q).value ∧
    (acquire resolve authorized state q).state.cache = (ordinary resolve state.cache q).cache := by
  cases cached : state.cache q <;> simp [acquire, ordinary, cached, unsupported]

/-- This same delegate law holds for an arbitrary external/stateful ordinary
    API. No purity certificate is asserted for the unsupported provider. -/
def fallback {σ : Type} {α : Type} (ordinaryAPI : σ → α) (state : σ) : α := ordinaryAPI state
theorem fallback_exact {σ : Type} {α : Type} (ordinaryAPI : σ → α) (state : σ) :
    fallback ordinaryAPI state = ordinaryAPI state := rfl

structure Mark where
  name : Nat
  primitive : Bool
  owner : Option Nat
  deriving DecidableEq, Repr
abbrev Catalog := List Mark

inductive Error where
  | conflictingOwner (name prior attempted : Nat)
  | provider (code : Nat)
  deriving DecidableEq, Repr

def findMark (name : Nat) : Catalog → Option Mark
  | [] => none
  | mark :: rest => if mark.name = name then some mark else findMark name rest

def reserve (catalog : Catalog) (name : Nat) (primitive : Bool) : Catalog :=
  match findMark name catalog with
  | some _ => catalog
  | none => catalog ++ [⟨name,primitive,none⟩]

def observe (catalog : Catalog) : Observation → Except Error Catalog
  | .reserve name primitive => .ok (reserve catalog name primitive)
  | .claim name primitive owner =>
    let reserved := reserve catalog name primitive
    match (findMark name reserved).bind Mark.owner with
    | none => .ok (reserved.map (fun mark => if mark.name = name then {mark with owner := some owner} else mark))
    | some prior => if prior = owner then .ok reserved else .error (.conflictingOwner name prior owner)

def replay (catalog : Catalog) : Trace → Except Error Catalog
  | [] => .ok catalog
  | event :: rest => match observe catalog event with
    | .error error => .error error
    | .ok next => replay next rest

def consume (catalog : Catalog) (result : Neutral) : Except Error Catalog :=
  match replay catalog result.trace with
  | .error error => .error error
  | .ok final => match result.failure with
    | none => .ok final
    | some code => .error (.provider code)

/-- Pending raw names stay unowned during observation. The existing completion
    boundary assigns model ownership; no eager owner at raw reservation. -/
def finish (sharedOwner : Nat) (catalog : Catalog) : List GroupPrimitiveMetadata.AllKnown.KnownFull :=
  catalog.map (fun mark => ⟨mark.name, mark.owner.getD sharedOwner, mark.primitive⟩)

theorem same_trace_same_catalog_or_error (catalog : Catalog) (left right : Neutral)
    (same : left = right) : consume catalog left = consume catalog right := by rw [same]

theorem acquisition_same_catalog_or_error {n : Nat} (resolve : Resolver n)
    (authorized : Fin n → Bool) (state : Session n) (q : Fin n)
    (valid : MemoValid resolve state.memo) (catalog : Catalog) :
    consume catalog (acquire resolve authorized state q).output =
      consume catalog (neutral (ordinary resolve state.cache q).value) := by
  rw [acquisition_exact resolve authorized state q valid]

/-- A proposed neutral memo is installed only after complete binding AND actual
    successful ordered observation. Ownership or provider errors never publish it. -/
def commit {n : Nat} (candidate : Acquisition n) (initial : Catalog) : Session n :=
  match consume initial candidate.output with
  | .error _ => candidate.state
  | .ok _ => match candidate.memoCandidate with
    | none => candidate.state
    | some (q,trace) => {candidate.state with memo := put candidate.state.memo q trace}

theorem failed_observation_no_memo {n : Nat} (candidate : Acquisition n)
    (initial : Catalog) (error : Error) (failed : consume initial candidate.output = .error error) :
    (commit candidate initial).memo = candidate.state.memo := by simp [commit, failed]

theorem commit_does_not_insert_bound {n : Nat} (candidate : Acquisition n) (initial : Catalog) :
    (commit candidate initial).cache = candidate.state.cache := by
  unfold commit
  cases consumed : consume initial candidate.output with
  | error error => rfl
  | ok final => cases proposal : candidate.memoCandidate with
    | none => rfl
    | some pair => cases pair; rfl

theorem candidate_trace_valid {n : Nat} (resolve : Resolver n) (authorized : Fin n → Bool)
    (state : Session n) (q key : Fin n) (trace : Trace)
    (proposed : (acquire resolve authorized state q).memoCandidate = some (key,trace)) :
    ∃ bound, resolve key = .success bound ∧ trace = bound.meaning.trace := by
  cases cached : state.cache q with
  | some bound => simp [acquire, cached] at proposed
  | none =>
    cases admitted : authorized q with
    | false => simp [acquire, cached, admitted] at proposed
    | true =>
      cases memo : state.memo q with
      | some old => simp [acquire, cached, admitted, memo] at proposed
      | none =>
        cases resolved : resolve q with
        | failure observedPrefix code => simp [acquire, cached, admitted, memo, resolved] at proposed
        | success bound =>
          simp only [acquire, cached, admitted, ↓reduceIte, memo, resolved,
            Option.some.injEq, Prod.mk.injEq] at proposed
          obtain ⟨rfl,rfl⟩ := proposed
          exact ⟨bound,resolved,rfl⟩

theorem acquire_memo_unchanged {n : Nat} (resolve : Resolver n)
    (authorized : Fin n → Bool) (state : Session n) (q : Fin n) :
    (acquire resolve authorized state q).state.memo = state.memo := by
  cases cached : state.cache q with
  | some bound => simp [acquire, cached]
  | none =>
    cases admitted : authorized q with
    | false => simp [acquire, cached, admitted]
    | true =>
      cases memo : state.memo q with
      | some trace => simp [acquire, cached, admitted, memo]
      | none => cases resolved : resolve q <;> simp [acquire, cached, admitted, memo, resolved]

theorem memo_valid_put {n : Nat} (resolve : Resolver n) (memo : Memo n)
    (key : Fin n) (trace : Trace) (valid : MemoValid resolve memo)
    (fresh : ∃ bound, resolve key = .success bound ∧ trace = bound.meaning.trace) :
    MemoValid resolve (put memo key trace) := by
  intro actual observed hit
  by_cases same : actual = key
  · subst actual
    have equal : trace = observed := by simpa only [put_same, Option.some.injEq] using hit
    rw [← equal]
    exact fresh
  · exact valid actual observed (by simpa only [put_other memo key actual trace same] using hit)

theorem commit_preserves_memo_valid {n : Nat} (resolve : Resolver n)
    (authorized : Fin n → Bool) (state : Session n) (q : Fin n) (initial : Catalog)
    (valid : MemoValid resolve state.memo) :
    MemoValid resolve (commit (acquire resolve authorized state q) initial).memo := by
  have unchanged := acquire_memo_unchanged resolve authorized state q
  have current : MemoValid resolve (acquire resolve authorized state q).state.memo := by
    rw [unchanged]; exact valid
  unfold commit
  cases consumed : consume initial (acquire resolve authorized state q).output with
  | error error => exact current
  | ok final =>
    cases proposed : (acquire resolve authorized state q).memoCandidate with
    | none => exact current
    | some pair =>
      rcases pair with ⟨key,trace⟩
      exact memo_valid_put resolve _ key trace current
        (candidate_trace_valid resolve authorized state q key trace proposed)

/-- Known leaf lookup can avoid insertion WITHOUT any replay certificate. -/
def censusOnly {n : Nat} (resolve : Resolver n) (state : Session n) (q : Fin n) : Acquisition n :=
  let result := match state.cache q with
    | some bound => Resolution.success bound
    | none => resolve q
  ⟨state,neutral result,none⟩

/-- Separate leaf lookup capability from optional complete-chain memo key.
    Unknown/custom view has censusLookup=false and takes ordinary API fallback. -/
def visit {n : Nat} (resolve : Resolver n) (censusLookup replayKey : Fin n → Bool)
    (state : Session n) (q : Fin n) : Acquisition n :=
  if censusLookup q then
    if replayKey q then acquire resolve (fun _ => true) state q else censusOnly resolve state q
  else acquire resolve (fun _ => false) state q

theorem census_only_exact {n : Nat} (resolve : Resolver n) (state : Session n) (q : Fin n) :
    (censusOnly resolve state q).output = neutral (ordinary resolve state.cache q).value := by
  cases cached : state.cache q with
  | some bound => simp [censusOnly, ordinary, cached]
  | none => cases resolved : resolve q <;> simp [censusOnly, ordinary, cached, resolved]

theorem visit_exact {n : Nat} (resolve : Resolver n) (censusLookup replayKey : Fin n → Bool)
    (state : Session n) (q : Fin n) (valid : MemoValid resolve state.memo) :
    (visit resolve censusLookup replayKey state q).output =
      neutral (ordinary resolve state.cache q).value := by
  unfold visit
  split
  · split
    · exact acquisition_exact resolve _ state q valid
    · exact census_only_exact resolve state q
  · exact acquisition_exact resolve _ state q valid

theorem census_lookup_no_bound_insertion {n : Nat} (resolve : Resolver n)
    (censusLookup replayKey : Fin n → Bool) (state : Session n) (q : Fin n)
    (capable : censusLookup q = true) :
    (visit resolve censusLookup replayKey state q).state.cache = state.cache := by
  simp only [visit, capable, ↓reduceIte]
  split
  · exact authorized_cache_unchanged resolve _ state q rfl
  · rfl

theorem no_key_no_memo_candidate {n : Nat} (resolve : Resolver n)
    (censusLookup replayKey : Fin n → Bool) (state : Session n) (q : Fin n)
    (capable : censusLookup q = true) (noKey : replayKey q = false) :
    (visit resolve censusLookup replayKey state q).memoCandidate = none := by
  simp [visit, capable, noKey, censusOnly]

theorem no_proposal_commit_memo {n : Nat} (candidate : Acquisition n) (catalog : Catalog)
    (noneProposed : candidate.memoCandidate = none) :
    (commit candidate catalog).memo = candidate.state.memo := by
  unfold commit
  cases observed : consume catalog candidate.output <;> simp [noneProposed]

theorem commit_visit_preserves_memo_valid {n : Nat} (resolve : Resolver n)
    (censusLookup replayKey : Fin n → Bool) (state : Session n) (q : Fin n)
    (catalog : Catalog) (valid : MemoValid resolve state.memo) :
    MemoValid resolve (commit (visit resolve censusLookup replayKey state q) catalog).memo := by
  unfold visit
  split
  · split
    · exact commit_preserves_memo_valid resolve _ state q catalog valid
    · rw [no_proposal_commit_memo _ catalog rfl]
      exact valid
  · exact commit_preserves_memo_valid resolve _ state q catalog valid

def completedCatalog (sharedOwner : Nat) (outcome : Except Error Catalog) :
    Except Error (List GroupPrimitiveMetadata.AllKnown.KnownFull) :=
  match outcome with
  | .error error => .error error
  | .ok catalog => .ok (finish sharedOwner catalog)

def primitiveOutcome (sharedOwner group : Nat) (outcome : Except Error Catalog) : Except Error Bool :=
  match outcome with
  | .error error => .error error
  | .ok catalog => .ok (GroupPrimitiveMetadata.AllKnown.bit group (finish sharedOwner catalog))

theorem visit_same_all_known_catalog_and_primitive {n : Nat} (resolve : Resolver n)
    (censusLookup replayKey : Fin n → Bool) (state : Session n) (q : Fin n)
    (catalog : Catalog) (sharedOwner group : Nat) (valid : MemoValid resolve state.memo) :
    completedCatalog sharedOwner (consume catalog (visit resolve censusLookup replayKey state q).output) =
      completedCatalog sharedOwner (consume catalog (neutral (ordinary resolve state.cache q).value)) ∧
    primitiveOutcome sharedOwner group (consume catalog (visit resolve censusLookup replayKey state q).output) =
      primitiveOutcome sharedOwner group (consume catalog (neutral (ordinary resolve state.cache q).value)) := by
  rw [visit_exact resolve censusLookup replayKey state q valid]
  exact ⟨rfl,rfl⟩

/-- Consistency of existing cache entries in one fixed resolver generation.
    Physical payload identities may differ; full logical identities may not. -/
def CacheValid {n : Nat} (resolve : Resolver n) (cache : Cache n) : Prop :=
  ∀ q bound, cache q = some bound → ∃ canonical,
    resolve q = .success canonical ∧ bound.meaning = canonical.meaning

theorem ordinary_canonical {n : Nat} (resolve : Resolver n) (cache : Cache n)
    (q : Fin n) (valid : CacheValid resolve cache) :
    neutral (ordinary resolve cache q).value = neutral (resolve q) := by
  cases cached : cache q with
  | none => cases resolved : resolve q <;> simp [ordinary, cached, resolved]
  | some bound =>
    obtain ⟨canonical,resolved,same⟩ := valid q bound cached
    simp [ordinary, cached, resolved, neutral, same]

theorem ordinary_preserves_cache_valid {n : Nat} (resolve : Resolver n) (cache : Cache n)
    (q : Fin n) (valid : CacheValid resolve cache) :
    CacheValid resolve (ordinary resolve cache q).cache := by
  cases cached : cache q with
  | some bound => simpa [ordinary, cached] using valid
  | none =>
    cases resolved : resolve q with
    | failure observedPrefix code => simpa [ordinary, cached, resolved] using valid
    | success bound =>
      intro key value hit
      by_cases same : key = q
      · subst key
        have equal : bound = value := by
          simpa only [ordinary, cached, resolved, put_same, Option.some.injEq] using hit
        subst value
        exact ⟨bound,resolved,rfl⟩
      · exact valid key value (by simpa [ordinary, cached, resolved, put, same] using hit)

theorem visit_preserves_cache_valid {n : Nat} (resolve : Resolver n)
    (censusLookup replayKey : Fin n → Bool) (state : Session n) (q : Fin n)
    (valid : CacheValid resolve state.cache) :
    CacheValid resolve (visit resolve censusLookup replayKey state q).state.cache := by
  cases capable : censusLookup q with
  | true => rw [census_lookup_no_bound_insertion resolve censusLookup replayKey state q capable]; exact valid
  | false =>
    have fallbackCache := (unsupported_is_ordinary resolve (fun _ => false) state q rfl).2
    simp only [visit, capable, Bool.false_eq_true, ↓reduceIte]
    rw [fallbackCache]
    exact ordinary_preserves_cache_valid resolve state.cache q valid

/-- Executable query-list composition. It stops on the exact first role/provider
    or ownership error and commits neutral memo only after successful observation. -/
def run {n : Nat} (resolve : Resolver n) (censusLookup replayKey : Fin n → Bool) :
    Session n → Catalog → List (Fin n) → Except Error (Session n × Catalog)
  | state, catalog, [] => .ok (state,catalog)
  | state, catalog, q :: qs =>
    let candidate := visit resolve censusLookup replayKey state q
    match consume catalog candidate.output with
    | .error error => .error error
    | .ok next => run resolve censusLookup replayKey (commit candidate catalog) next qs

def canonicalRun {n : Nat} (resolve : Resolver n) : Catalog → List (Fin n) → Except Error Catalog
  | catalog, [] => .ok catalog
  | catalog, q :: qs => match consume catalog (neutral (resolve q)) with
    | .error error => .error error
    | .ok next => canonicalRun resolve next qs

def ordinaryRun {n : Nat} (resolve : Resolver n) : Cache n → Catalog → List (Fin n) → Except Error Catalog
  | _, catalog, [] => .ok catalog
  | cache, catalog, q :: qs =>
    let result := ordinary resolve cache q
    match consume catalog (neutral result.value) with
    | .error error => .error error
    | .ok next => ordinaryRun resolve result.cache next qs

def resultCatalog {n : Nat} : Except Error (Session n × Catalog) → Except Error Catalog
  | .error error => .error error
  | .ok (_,catalog) => .ok catalog

theorem query_list_exact {n : Nat} (resolve : Resolver n) (censusLookup replayKey : Fin n → Bool)
    (state : Session n) (catalog : Catalog) (queries : List (Fin n))
    (cacheValid : CacheValid resolve state.cache) (memoValid : MemoValid resolve state.memo) :
    resultCatalog (run resolve censusLookup replayKey state catalog queries) =
      canonicalRun resolve catalog queries := by
  induction queries generalizing state catalog with
  | nil => rfl
  | cons q qs ih =>
    have output := (visit_exact resolve censusLookup replayKey state q memoValid).trans
      (ordinary_canonical resolve state.cache q cacheValid)
    have nextCache : CacheValid resolve
        (commit (visit resolve censusLookup replayKey state q) catalog).cache := by
      rw [commit_does_not_insert_bound]
      exact visit_preserves_cache_valid resolve censusLookup replayKey state q cacheValid
    have nextMemo := commit_visit_preserves_memo_valid resolve censusLookup replayKey state q catalog memoValid
    simp only [run, canonicalRun, output]
    cases observed : consume catalog (neutral (resolve q)) with
    | error error => rfl
    | ok next => exact ih _ _ nextCache nextMemo

theorem ordinary_query_list_exact {n : Nat} (resolve : Resolver n) (cache : Cache n)
    (catalog : Catalog) (queries : List (Fin n)) (valid : CacheValid resolve cache) :
    ordinaryRun resolve cache catalog queries = canonicalRun resolve catalog queries := by
  induction queries generalizing cache catalog with
  | nil => rfl
  | cons q qs ih =>
    have output := ordinary_canonical resolve cache q valid
    simp only [ordinaryRun, canonicalRun, output]
    cases observed : consume catalog (neutral (resolve q)) with
    | error error => rfl
    | ok next => exact ih _ _ (ordinary_preserves_cache_valid resolve cache q valid)

theorem census_equals_cached_reference {n : Nat} (resolve : Resolver n)
    (censusLookup replayKey : Fin n → Bool) (state : Session n) (catalog : Catalog)
    (queries : List (Fin n)) (cacheValid : CacheValid resolve state.cache)
    (memoValid : MemoValid resolve state.memo) :
    resultCatalog (run resolve censusLookup replayKey state catalog queries) =
      ordinaryRun resolve state.cache catalog queries := by
  rw [query_list_exact resolve censusLookup replayKey state catalog queries cacheValid memoValid,
    ordinary_query_list_exact resolve state.cache catalog queries cacheValid]

theorem successful_census_run_preserves_cache {n : Nat} (resolve : Resolver n)
    (censusLookup replayKey : Fin n → Bool) (state final : Session n)
    (catalog finalCatalog : Catalog) (queries : List (Fin n))
    (capable : ∀ q, q ∈ queries → censusLookup q = true)
    (closed : run resolve censusLookup replayKey state catalog queries = .ok (final,finalCatalog)) :
    final.cache = state.cache := by
  induction queries generalizing state catalog with
  | nil =>
    simp only [run, Except.ok.injEq, Prod.mk.injEq] at closed
    rw [← closed.1]
  | cons q qs ih =>
    have one : censusLookup q = true := capable q (by simp)
    have rest : ∀ key, key ∈ qs → censusLookup key = true := by
      intro key present
      exact capable key (by simp [present])
    simp only [run] at closed
    cases observed : consume catalog (visit resolve censusLookup replayKey state q).output with
    | error error => simp [observed] at closed
    | ok next =>
      have tailClosed : run resolve censusLookup replayKey
          (commit (visit resolve censusLookup replayKey state q) catalog) next qs =
            .ok (final,finalCatalog) := by simpa only [observed] using closed
      have unchanged := ih _ _ rest tailClosed
      exact unchanged.trans ((commit_does_not_insert_bound _ catalog).trans
        (census_lookup_no_bound_insertion resolve censusLookup replayKey state q one))

theorem query_list_same_all_known {n : Nat} (resolve : Resolver n)
    (censusLookup replayKey : Fin n → Bool) (state : Session n) (catalog : Catalog)
    (queries : List (Fin n)) (cacheValid : CacheValid resolve state.cache)
    (memoValid : MemoValid resolve state.memo) (sharedOwner group : Nat) :
    completedCatalog sharedOwner (resultCatalog (run resolve censusLookup replayKey state catalog queries)) =
      completedCatalog sharedOwner (ordinaryRun resolve state.cache catalog queries) ∧
    primitiveOutcome sharedOwner group (resultCatalog (run resolve censusLookup replayKey state catalog queries)) =
      primitiveOutcome sharedOwner group (ordinaryRun resolve state.cache catalog queries) := by
  rw [census_equals_cached_reference resolve censusLookup replayKey state catalog queries cacheValid memoValid]
  exact ⟨rfl,rfl⟩

/-- Fixed finite scope has at most n neutral trace slots, never bound payloads. -/
def memoSize {n : Nat} (memo : Memo n) : Nat := ((List.ofFn memo).filterMap id).length

theorem memo_slots_bound {n : Nat} (memo : Memo n) : memoSize memo ≤ n := by
  simpa [memoSize] using List.length_filterMap_le id (List.ofFn memo)

/-- Bound roots are a cache-entry/payload-label scope, not unique reachable
    objects or net JVM memory. Neutral trace names may share other owners. -/
def boundRoots {n : Nat} (cache : Cache n) : List Nat :=
  (List.ofFn cache).flatMap (fun value => match value with
    | none => []
    | some bound => bound.payloadRoots)

theorem authorized_bound_roots_unchanged {n : Nat} (resolve : Resolver n)
    (authorized : Fin n → Bool) (state : Session n) (q : Fin n)
    (admitted : authorized q = true) :
    boundRoots (acquire resolve authorized state q).state.cache = boundRoots state.cache := by
  rw [authorized_cache_unchanged resolve authorized state q admitted]

theorem fresh_miss_root_edge_difference {n : Nat} (resolve : Resolver n)
    (authorized : Fin n → Bool) (state : Session n) (q : Fin n) (bound : Bound)
    (admitted : authorized q = true) (missing : state.cache q = none)
    (resolved : resolve q = .success bound) :
    (ordinary resolve state.cache q).cache q = some bound ∧
      (acquire resolve authorized state q).state.cache q = none := by
  constructor
  · simp [ordinary, missing, resolved]
  · rw [authorized_cache_unchanged resolve authorized state q admitted]
    exact missing

/-- Abstract object-graph scope. This is NOT a JVM heap-size theorem. Other
    roots must include every remaining provider/form/session/stack owner. -/
inductive Reachable (edges : Nat → List Nat) (roots : List Nat) : Nat → Prop where
  | root {node : Nat} : node ∈ roots → Reachable edges roots node
  | next {origin node : Nat} : Reachable edges roots origin → node ∈ edges origin → Reachable edges roots node

theorem reachable_monotone (edges : Nat → List Nat) (small large : List Nat)
    (included : ∀ node, node ∈ small → node ∈ large) (node : Nat)
    (reachable : Reachable edges small node) : Reachable edges large node := by
  induction reachable with
  | root present => exact .root (included _ present)
  | next prior adjacent ih => exact .next ih adjacent

theorem payload_root_member {n : Nat} (cache : Cache n) (q : Fin n) (bound : Bound)
    (present : cache q = some bound) (node : Nat) (payload : node ∈ bound.payloadRoots) :
    node ∈ boundRoots cache := by
  apply List.mem_flatMap.mpr
  exact ⟨some bound, List.mem_ofFn.mpr ⟨q,present⟩, payload⟩

theorem exclusive_payload_retention_difference {n : Nat} (resolve : Resolver n)
    (censusLookup replayKey : Fin n → Bool) (state : Session n) (q : Fin n) (bound : Bound)
    (capable : censusLookup q = true) (missing : state.cache q = none)
    (resolved : resolve q = .success bound) (edges : Nat → List Nat) (otherRoots : List Nat)
    (node : Nat) (payload : node ∈ bound.payloadRoots)
    (exclusive : ¬ Reachable edges (otherRoots ++ boundRoots state.cache) node) :
    Reachable edges (otherRoots ++ boundRoots (ordinary resolve state.cache q).cache) node ∧
      ¬ Reachable edges (otherRoots ++ boundRoots (visit resolve censusLookup replayKey state q).state.cache) node := by
  constructor
  · have cached : (ordinary resolve state.cache q).cache q = some bound := by
      simp [ordinary, missing, resolved]
    exact .root (List.mem_append_right _ (payload_root_member _ q bound cached node payload))
  · rw [census_lookup_no_bound_insertion resolve censusLookup replayKey state q capable]
    exact exclusive

/-- Charge one cache-owner edge write for a successful cold ordinary lookup.
    This excludes binding, neutral trace storage, wrappers and later runtime
    rebuilding; no total CPU or peak/allocation saving is asserted. -/
def boundInsertionCost {n : Nat} (before after : Cache n) (q : Fin n) : Nat :=
  if (before q).isNone && (after q).isSome then 1 else 0

theorem fresh_bound_write_cost {n : Nat} (resolve : Resolver n)
    (censusLookup replayKey : Fin n → Bool) (state : Session n) (q : Fin n) (bound : Bound)
    (capable : censusLookup q = true) (missing : state.cache q = none)
    (resolved : resolve q = .success bound) :
    boundInsertionCost state.cache (ordinary resolve state.cache q).cache q = 1 ∧
      boundInsertionCost state.cache (visit resolve censusLookup replayKey state q).state.cache q = 0 := by
  constructor
  · simp [boundInsertionCost, ordinary, missing, resolved]
  · rw [census_lookup_no_bound_insertion resolve censusLookup replayKey state q capable]
    simp [boundInsertionCost, missing]

/-- Same successful ordered observation gives exactly the same completed
    all-known catalog and primitive bit for each group. -/
theorem same_catalog_same_all_known (sharedOwner group : Nat) (left right : Catalog)
    (same : left = right) :
    finish sharedOwner left = finish sharedOwner right ∧
    GroupPrimitiveMetadata.AllKnown.bit group (finish sharedOwner left) =
      GroupPrimitiveMetadata.AllKnown.bit group (finish sharedOwner right) := by
  subst right
  exact ⟨rfl,rfl⟩

namespace Controls

def trace : Trace := [.claim 7 true 2, .reserve 9 false, .reserve 7 true]
def value : Bound := ⟨⟨[⟨40,[0,1]⟩],trace⟩,[100,101]⟩
def key : Fin 1 := ⟨0,by decide⟩
def emptySession : Session 1 := ⟨fun _ => none, fun _ => none⟩
def resolver : Resolver 1 := fun _ => .success value

def hasError (actual : Except Error Catalog) (wanted : Error) : Bool :=
  match actual with
  | .error error => decide (error = wanted)
  | .ok _ => false
def hasCatalog (actual : Except Error Catalog) (wanted : Catalog) : Bool :=
  match actual with
  | .error _ => false
  | .ok catalog => decide (catalog = wanted)

def controls : List (String × Bool) := [
  ("ordinary miss inserts bound", ((ordinary resolver emptySession.cache key).cache key).isSome),
  ("certified miss does not insert bound", ((acquire resolver (fun _ => true) emptySession key).state.cache key).isNone),
  ("successful observation installs neutral memo", ((commit (acquire resolver (fun _ => true) emptySession key) []).memo key).isSome),
  ("capable lookup without key does not insert", ((visit resolver (fun _ => true)
    (fun _ => false) emptySession key).state.cache key).isNone),
  ("capable lookup without key has no memo proposal", ((visit resolver (fun _ => true)
    (fun _ => false) emptySession key).memoCandidate).isNone),
  ("unsupported scope inserts by ordinary API", ((acquire resolver (fun _ => false) emptySession key).state.cache key).isSome),
  ("empty cached value still borrowed", (acquire (fun _ => .failure [] 99) (fun _ => true)
    ⟨fun _ => some ⟨⟨[],[]⟩,[]⟩,fun _ => none⟩ key).output == ⟨[],none⟩),
  ("generated identities stay ordered", value.meaning.identities == [⟨40,[0,1]⟩]),
  ("equal names alone do not certify identities", decide
    (Bound.meaning value ≠ {value.meaning with identities := [⟨41,[0,1]⟩]}) &&
    (value.meaning.trace == ({value.meaning with identities := [⟨41,[0,1]⟩]}).trace)),
  ("ownership conflict preserved", hasError (replay [] [.claim 7 false 1,.claim 7 false 2])
    (.conflictingOwner 7 1 2)),
  ("trace order changes conflict diagnostic", hasError (replay [] [.claim 7 false 2,.claim 7 false 1])
    (.conflictingOwner 7 2 1)),
  ("reserve does not assign owner early", hasCatalog (replay [] [.reserve 7 true]) [⟨7,true,none⟩]),
  ("completion assigns existing shared owner", finish 0 [⟨7,true,none⟩] == [⟨7,0,true⟩]),
  ("provider failure observedPrefix observed then fails", hasError (consume [] ⟨[.reserve 7 true],some 3⟩) (.provider 3)),
  ("failed observation does not install memo", ((commit
    (acquire (fun _ => .success ⟨⟨[],[.claim 7 false 1,.claim 7 false 2]⟩,[]⟩)
      (fun _ => true) emptySession key) []).memo key).isNone),
  ("override before projection excludes base mark", hasCatalog
    (replay [] [.reserve 9 false]) [⟨9,false,none⟩] && !hasCatalog
    (replay [] [.reserve 7 true,.reserve 9 false]) [⟨9,false,none⟩]),
  ("provider failure cannot publish a partial memo", ((commit
    (acquire (fun _ => .failure [.reserve 7 true] 5) (fun _ => true) emptySession key) []).memo key).isNone),
  ("empty neutral trace is a valid memo hit", (acquire (fun _ => .success ⟨⟨[],[]⟩,[]⟩) (fun _ => true)
    ⟨fun _ => none,fun _ => some []⟩ key).output == ⟨[],none⟩),
  ("same mark set need not preserve ordered trace", decide
    ([Observation.reserve 7 true,.reserve 9 false] ≠ [.reserve 9 false,.reserve 7 true]))]

theorem controls_pass : controls.all Prod.snd = true := by decide

end Controls
end ApSpec.OwnershipCensus
