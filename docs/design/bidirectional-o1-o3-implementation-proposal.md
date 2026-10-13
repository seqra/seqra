# Implementation proposal: O1, O2 and O3

Status: historical implementation design. Original date: 12 October 2026. O1 NNF source/sink predicates,
O2 managed soft method-form caches and O3 compact form containers were implemented. The old memory
priorities are superseded by the [latest proposal](bidirectional-core-optimization-20261013.md).
The user-requested rollback removes the manager lifecycle rewrite and the new weak identity cache;
both are optional future candidates, not part of the current implementation. The form cache uses the
original manager API. The [implementation plan](bidirectional-o1-o3-implementation-plan.md) records this scope correction.

Replace compiled condition cubes with a shared NNF evaluator. Retain logical support in the run, rather than in millions of compiled alternatives. Put rebuildable forward/backward forms behind managed soft references. Store small form fields and lookup indexes without a map and wrapper for each field.

The [optimization overview](bidirectional-core-optimization.md) ranks the expected memory impact. The [memory report](../reports/bidirectional-project-memory.md) measures the owners: method caches retain 2.41–6.46 GB; form collection classes use 1.36 GB in Conductor and 3.16 GB in ThingsBoard. Those retained unions overlap. This proposal does not predict an exact saving or claim that an optimized scan has run.

## Scope and decisions

| Area | Decision |
| --- | --- |
| Condition storage | Owned NNF DAG with AND, OR, leaf and constant nodes; no DNF or array Cartesian product in production rule lowering |
| Rule kinds | One predicate model for sources and sinks; separate action and report ownership |
| Contradictions | Fold signed NNF first. `P AND NOT P` is false. Approximate remaining negative source/sink literals only after this fold. |
| Forward state | Actor-owned leaf contributions and incremental proof joins, scoped by rule, statement and method context |
| Backward work | Walk the full applicable positive condition with unary/multi-literal policy; preserve witnessed method contexts and separate confirmation proofs |
| Persistent identity | Stable value keys for rule/action/proof/temporary occurrences; no dependence on a compiled form's address |
| Cache ownership | Small strong method/slot owners; managed soft forward/backward payloads; strong local pins during application |
| Form containers | Shared empty values, direct singletons and owned arrays; compact base and call-point indexes |
| Migration | A test-only legacy cube oracle, with the same corrected signed normalization policy |

IR and IR storage remain out of scope. O4–O12 are not selected. In particular, do not add global payload interning, rule-template caches, fact-group rewrites, cleaner action deduplication, graph caches, queue changes or trie changes. Local immutable node reuse required by O1 and ownership factories required by O3 are part of the selected work.

Keep the exclusion table, AP operations, NORMAL/DEMAND layers, premise union, type filters, mark policies and F74/F75/F76 behavior. Keep run 1 at field limit 1 and retain the existing positive-limit sequence, timeout and heap settings. Keep `allRelevant=true` and its required mark universe. Do not add an alternative or work cap.

## Current components and changes

Paths below refer to the existing source. New type names are proposed interfaces, not implemented APIs.

| Component | Required change |
| --- | --- |
| [JIRRuleForms](../../core/opentaint-dataflow-core/opentaint-jvm-dataflow/src/main/kotlin/org/opentaint/dataflow/jvm/bidi/interp/JIRRuleForms.kt) | Replace `cubes`, cube/action lowering and sink products with signed normalization and predicate compilation. Retain whole-rule validation. |
| [JIRCallPlanBuilder](../../core/opentaint-dataflow-core/opentaint-jvm-dataflow/src/main/kotlin/org/opentaint/dataflow/jvm/bidi/interp/JIRCallPlanBuilder.kt) | Bind source predicates and ordered targets once. Bind sink predicates and end-fact actions once. Use the same false-condition result in rewriter eligibility checks. |
| [JIRBoundaryForms](../../core/opentaint-dataflow-core/opentaint-jvm-dataflow/src/main/kotlin/org/opentaint/dataflow/jvm/bidi/interp/JIRBoundaryForms.kt) | Compile conditional exit predicates; preserve entry eligibility, entry-mark extraction, normal/exceptional result mapping and exit removal metadata. |
| [FormApplier](../../core/opentaint-dataflow-core/opentaint-dataflow/src/main/kotlin/org/opentaint/dataflow/bidi/interp/FormApplier.kt), `FactAlgebra`, `FormsReference` | Add a common predicate application path for tree and reference algebras. Keep plain micro edges and existing application modes. |
| [RunMethodLocalServices](../../core/opentaint-dataflow-core/opentaint-dataflow/src/main/kotlin/org/opentaint/dataflow/bidi/engine/RunMethodLocalServices.kt), `ConjunctionStore` | Add predicate support state. Use stable keys for joins, triggers and proof scopes. Keep the separate E6 ND-summary joins. |
| `VulnerabilityStore`, `HandOff`, `Seeds`, `SourceHitStore`, call guards | Replace alternative/reference keys where needed; preserve support shapes, full matching sink-condition demand and complete source-edge values. |
| [FormsCache](../../core/opentaint-dataflow-core/opentaint-dataflow/src/main/kotlin/org/opentaint/dataflow/bidi/interp/FormsCache.kt), JVM method owners | Introduce managed payload slots and application-scoped access. |
| [SoftReferenceManager](../../core/opentaint-dataflow-core/opentaint-dataflow/src/main/kotlin/org/opentaint/dataflow/util/SoftReferenceManager.kt) | Reuse the original manager and its existing API. A lifecycle redesign requires separate selection and design. |
| [StatementSummary](../../core/opentaint-dataflow-core/opentaint-dataflow/src/main/kotlin/org/opentaint/dataflow/bidi/interp/StatementSummary.kt), [CallPlan](../../core/opentaint-dataflow-core/opentaint-dataflow/src/main/kotlin/org/opentaint/dataflow/bidi/interp/CallPlan.kt), builders | Replace collection storage and indexes; include conditional rule leaves/targets in summary metadata. |

## O1: condition compiler

### Normalization order

The compiler has one explicit pipeline:

```text
validate complete raw rule
  -> signed NNF and raw Boolean folding
  -> static non-mark atoms evaluated for this placement
  -> signed constant, duplicate and complement folding
  -> rule-kind policy for remaining mark literals
  -> constant folding and removal of unreachable nodes
  -> active positive read/seed footprint
  -> owned predicate DAG, indexes and transfer-policy metadata
```

Validation visits every raw action and mark position, including negative and discarded branches. A malformed raw rule remains rejected even if its condition would be false. Do not use simplification to hide an error.

Run the signed folder before static atom evaluation, then run it again on the rewritten expression. A polarity-sensitive approximate static evaluator can otherwise turn both an atom and its negation into true before the contradiction is visible. Use the existing static evaluator and alias context for surviving nodes. Raw keys include complete atom kind and operands; mark keys include base, path, predicate/tail semantics and mark. Root and any-field checks are different atoms.

The folder does not perform a SAT search or distribute operators. Iterate nodes in postorder, flatten adjacent operators and retain deterministic first-occurrence order.

| Signed expression | Fold |
| --- | --- |
| `AND(false, X)` | false |
| `OR(true, X)` | true |
| `AND(true, X)` / `OR(false, X)` | X |
| `AND(P, P)` / `OR(P, P)` | P |
| `AND(P, NOT P)` | false |
| `OR(P, NOT P)` | true |

Apply direct complement checks within the current flattened operator. Do not infer a contradiction from unrelated positions or marks. Also use non-distributing unit propagation to detect the real resolver's expanded contradictions: an Object/array argument can turn `P AND NOT P` into `(P0 OR P1) AND NOT P0 AND NOT P1` before the bidi compiler receives it.

For each conjunction context, collect mandatory signed leaf assignments in a scratch worklist. Evaluate its remaining child graph under those assignments. A mandatory OR with no possible child proves false; one remaining literal forces another assignment; a remaining AND exposes its mandatory children. Continue until no new assignment exists or a conflict proves false. Use the dual check to prove a tautology. There is no guessed assignment, distribution or backtracking. Discard scratch state after folding.

Use this propagation to collapse proved false/true scopes. If it proves neither, retain the original live requirement structure apart from the direct folds above; do not remove leaf read effects merely because an assumption could simplify a live child. For example, `P AND (NOT P OR Q)` is not a contradiction. General absorption and a complete Boolean satisfiability solver are outside this first implementation. This policy handles source/sink complements without adding a cube inventory or changing the relevant-rule resolver's existing applicability rules.

For accepted call/exit sources and sinks, project remaining negative mark literals to true after these signed folds. Fold constants again. Entry rules retain their placement eligibility: the signed/static-folded condition must already be true; do not make a remaining negative entry condition eligible merely by projection. For cleaners, keep the existing action-specific normalization from `saloed/cleaner-action-normalisation` and its broad-reach guard. A remaining mark-dependent cleaner does not clean or request. Pass rules retain their current logged fallback for remaining mark conditions. Do not broaden the source/sink policy to those rules.

FALSE stops rule compilation after validation: no action, footprint, request, witness, trigger or seed. Remove false subtrees before collecting the active footprint. Thus `A OR (P AND NOT P)` has only the active `A` branch. The changed contradiction behavior is intentional. Compare implementations using an oracle that applies this same policy, rather than accepting the old projection of `P AND NOT P` to `P`.

For a non-false exit predicate, collect its positive read/drop footprint before any future absorption. `A OR (A AND B)` still checks and can drop a zero-premise static `B` even if the root never fires. Keeping that footprint does not require any cubes.

### Owned predicate layout

Use one compiled predicate per bound rule, not one per target or disjunct:

```kotlin
class RulePredicate internal constructor(
    val root: Int,
    // Owned node tags, child ranges, child ordinals and leaf descriptors.
    // Public methods expose read-only traversal; raw arrays stay private.
)

class SourceRuleOccurrence(
    val id: RuleOccurrenceId,
    val predicate: RulePredicate,
    val actions: List<SourceAction>, // Backed by a private owned vector.
)

class SinkRuleOccurrence(
    val id: RuleOccurrenceId,
    val predicate: RulePredicate,
    val reportOwner: RuleId,
    val endActions: List<EndFactAction>, // Backed by a private owned vector.
)
```

Use primitive node tags and child offsets/counts with one child-ordinal array. Leaf descriptors own their base/path/tail/mark values. Keep an index from canonical leaf requirement to a leaf ordinal and inverse parent edges for incremental updates. Share equal node payloads inside this predicate, while retaining rule/action scope. No analysis-wide structural pool is needed.

Use a compact base-to-leaf index and a mark-to-leaf index for marked input. FLOW has no mark, so visit all applicable leaves on its base to raise requests. Constants and zero facts use the current placement path. Keep checked leaf effects separate from root firing so exit removals can read them.

For an eligible call-sink argument leaf, add `OR(rootPath, elementPrefixPath)` locally. The size grows by a small number of nodes per eligible leaf, not by the product across leaves. Keep actual static array eligibility and the current one-prefix rule, including already-element paths. Do not add this choice to source, receiver, result, entry or exit leaves.

### Unary and multi-literal transfer classes without cubes

The same predicate can contain both classes. This affects AP transfer and request behavior, not only proof recording.

Compute `unaryLeaves` on the positive, constant-folded DAG. For each canonical leaf key `k`, a unary proof exists when the root is true with only `k` true. Implement this by postorder set propagation: a leaf gives its singleton key; OR unions unary-key sets; AND intersects them after true children are removed. FALSE has no proof; a true root uses the unconditional path. Compiler scratch sets are discarded after retaining the root's unary-leaf ordinals.

Compute `multiLeaves` from co-occurrence. For each leaf key, walk inverse parent edges toward the root. An AND ancestor permits a multi-literal use if a sibling can mention a different leaf key. OR does not add sibling requirements. Use each subtree's atom summary and memoized traversal to avoid enumerating paths or cubes. Remove unreachable/false branches first. Count distinct canonical requirements: `A AND A` has one requirement, while `(A OR B) AND A` permits unary `A` and multi-literal `A/B`.

Keep only compact unary/multi flags or ordinal vectors in the compiled predicate. A leaf can be in both. Do not choose its policy based on which facts happen to arrive first.

For unary source uses, call the existing generic statement transfer immediately with the original incoming `Facts`, leaf and action target, before releasing that input. Preserve path suffixes, exclusions, static POSITION requests and result filtering. Do not reconstruct this result later from a stored premise/layer pair or replace it with a fixed target tree. For multi-literal uses, use the existing MARK checks and conjunctive target construction. A pure leaf check can be reused only within the same input, AP mode and check policy.

Do not prebuild the leaf-by-action transfer product. Keep leaves and targets separately, and apply a small transfer view when that pair is used. Any compiled side cache belongs to the reclaimable form payload and is populated only for actual use. This is the minimal AP adapter needed by O1, not a general edge-storage rewrite.

### Incremental forward evaluator

Add an actor-owned `RulePredicateStore`. Key a state by `(MethodKey, statement, RuleOccurrenceId)`. It contains leaf contributions, node deltas and shared proof records. It must not retain the compiled `CallPlan`, `StatementSummary`, `RulePredicate` or its node/index arrays. Use the predicate only under the current form pin. Persist scalar node/leaf IDs and owned support values; resolve topology and action descriptors from the rebuilt payload on the next application.

```text
accept(predicate, ruleScope, premise, facts, mode)
  -> check indexed leaves and raise required requests
  -> insert new admitted leaf contributions
  -> iterative delta worklist through parent nodes
  -> emit complete root support to source or sink handling
```

A source multi-literal contribution needs premise and effective layer. It need not retain the full checked fact after the mark check if that value is not used by the source result. A sink contribution also retains the checked fact needed for witness support and exit removal. Avoid retaining source facts merely to make both contribution types identical.

OR forwards each child support with its branch scope. AND uses a delta join: the new child support joins standing supports from the other children. Use the existing last-new-member discipline so a complete combination is emitted once. Stream combinations to the parent worklist rather than building a temporary list of every product.

Each proof carries a binding from canonical leaf requirement to one contribution. When child proofs share a requirement, those bindings must agree; incompatible bindings cannot be joined. This prevents `A AND A` from inventing a result with two different `A` premises. Premise union drops zero unless every input is zero. The combined layer is DEMAND if any input is DEMAND. Exclusion admission remains in the AP checks.

Store proofs as immutable leaf/OR-choice/AND-child records shared within the actor. Separate three identities: the structural `ProofKey` describes selected leaf/OR choices; `SupportShape` adds run/context and ordered premise/layer/checked-fact group headers; contribution versions carry the actual growing matched facts. A new fact version must produce the required delta even when its structural proof is already known. Merge witness facts only within an equal support shape, using the existing AP exact union. At the root, deduplicate exact repeated outcomes while retaining relevant OR provenance. Do not merge different premises, layers, checked-fact groups or proof scopes. Root proofs with one distinct leaf use the unary path; proofs with at least two use the multi-literal path. Do not emit the unary result again through the multi-literal evaluator.

Multi-literal source roots emit their ordered actions for each required support outcome, with a separate action handle. Unary source outcomes already emitted through the immediate generic transfer are recorded/deduplicated without a second fixed-target emission. Sink roots create witnesses with ordered support headers and apply the owning end-fact actions in the current mode. Sink witness union still requires equal support shape, context and run. Same-call confirmation retains each required support member; Boolean true does not replace that evidence.

Leaf reads and mark requests occur even before the root is complete. Keep the G2/D30 positive exit footprint and the distinction between zero-premise and caller-set static state. A request does not count as a satisfied mark. Clear all actor-owned predicate state when the run releases the actor; witnesses and handoff seeds retain only owned support descriptors they need.

Actual inputs can still create many support combinations. The design removes static cube storage, not the inherent result count. Use cancellation checkpoints in traversal and joining. Do not add a support cap or collapse premise sets as a memory shortcut.

### Placement and backward integration

| Placement | Behavior retained |
| --- | --- |
| Call source/sink | Current stage order and callee-coordinate binding; call sink array choices; operand/result filters |
| Normal exit | Conditional sources/sinks, normal result base, zero-premise state removal and entry-mark removal |
| Exceptional exit | Exception result base and no normal-exit drop metadata |
| Entry | Current unconditional eligibility and context filtering; no new mark-dependent entry behavior |
| Static-field read source | Current raw validation and unconditional read-source behavior |
| End facts | GEN at boundaries and END_FACTS at calls; not subject to unconditional source admission |

In [JIRCallPlanBuilder](../../core/opentaint-dataflow-core/opentaint-jvm-dataflow/src/main/kotlin/org/opentaint/dataflow/jvm/bidi/interp/JIRCallPlanBuilder.kt), use the signed-fold result for source rewriter eligibility too. A known-false source must not generate rewriter cleaning steps merely because the older rewrite did not detect its contradiction. Retain `allRelevant=true`, the full required mark set and cleaner ordering.

Backward source application starts with the required action target. Iterate the predicate's leaf requirements. Unary leaves use ordinary target-to-leaf reversal. Multi-literal leaves use the same spatial reversal with forced DEMAND. A leaf present in both classes can emit both effects. Preserve forward `may` classification, tail reversal and the no-must backward invariant. Conditional sources do not become unconditional source hits.

Backward sink seeds carry `PredicateSeed(ruleOccurrenceId, MethodKey, statement)` or owned leaf requirements derived from that full condition. Walk all active positive leaves; do not materialize a cube list. Emitting OR requirements does not mean that a forward AND is satisfied: the forward store still checks complete support. Preserve existing exact/may seed layer behavior.

[HandOff.toBackward](../../core/opentaint-dataflow-core/opentaint-dataflow/src/main/kotlin/org/opentaint/dataflow/bidi/driver/HandOff.kt) takes the method contexts from DEMAND report witnesses, then seeds every matching sink alternative at that place, including unwitnessed OR branches. Preserve that behavior with one full-condition seed handle for each matching bound rule at each witnessed method context and statement. Do not seed unrelated contexts. Proof scope stays in the witness and confirmation logic; it does not restrict the main backward handoff's positive leaves. TRUE contributes no mark requirements and FALSE contributes no seed.

Reversed end facts need the full owning predicate's positive leaf scope. Forward GEN end facts use canonical zero premise and do not carry an OR-proof correlation. For `A OR B` with the same end target, a backward requirement on that target must demand both `A` and `B`, even if only `A` has a recorded witness. Fire this full trigger scope once per `(MethodKey, statement, RuleOccurrenceId)` across that occurrence's end actions, including CONFIRMED results. Proof-restricted triggering would require explicit backward proof provenance that the current fact representation does not have. Preserve source-hit admission separately through the complete immutable forward-edge value.

## Stable identity required by O1 and O2

IDs are values, not references to compiled payloads. They stay equal across cache misses and direction changes within one analysis session.

| Key | Contents |
| --- | --- |
| `FormKey` | Analysis/configuration/body generation; physical method; full `MethodKey` for context-dependent forms; instruction or boundary kind |
| `RuleOccurrenceId` | Form key; provider rule occurrence token; rule kind and specialization identity |
| `ActionId` | Rule occurrence; application role/stage; original action ordinal; generated relevant-mark and step ordinals where applicable |
| `LeafKey` | Canonical full base/path/tail/mark requirement within its rule scope |
| `ProofKey` | Rule scope and canonical ordered support/OR-choice descriptor for an observed proof |
| `TemporaryKey` | Full generated action scope, including application role/stage, relevant mark and field/string-byte substep ordinal |

A displayed rule ID or serialized rule ID alone is insufficient. Use the original provider occurrence and binding. Where the provider has no stable token, bind the token to its existing frozen candidate list and ordinal; record that list's generation. Rebuilds must reuse the same list identity/order or fail the frozen-input invariant. Keep this metadata compact and derive leaf/action IDs from it; do not allocate a registry entry for every potential proof.

Make node and action numbering deterministic from source order, not hash iteration. Cache epoch, run index, field limit and direction never enter logical occurrence keys. Those values can identify execution or allocation in diagnostics and support-store scope. The method body and installed boundaries are fixed while these keys are in use. Configuration/body replacement creates a new session and owners; O1–O3 do not introduce live-body invalidation.

Change standing rule joins, trigger sets and `Guard.SinkTriggered` comparisons to these keys. Do not change the separate callee/premise/call key of E6 joins. Persistent witnesses use proof/support keys rather than compiled `SinkRule` references. They keep report metadata and owned support/end-fact values. Sink seed descriptors store a form/rule lookup key and full-condition scope; they reacquire the predicate rather than strongly retaining its graph. Source seeds/hits compare the complete edge value as today; a retained edge descriptor has no compiled-state or form-owner backreference.

Change `CleanerTemporary` to support a deterministic generated `TemporaryKey`; preserve a unique fresh token for public/manual construction. Equal rebuilt generated temporaries refer to the same logical action. Distinct actions remain distinct even if their cleaner tuples are equal. Forward and backward forms in one payload use the same temporary. No temporary may escape through a record, vulnerability or handoff seed.

## O2: managed soft form cache

### Slot and payload API

Register `refManager.softRefManager("bidi-method-forms")` in [JIRBidiAnalysis](../../core/opentaint-dataflow-core/opentaint-jvm-dataflow/src/main/kotlin/org/opentaint/dataflow/jvm/bidi/JIRBidiAnalysis.kt), using the same `RefManager` passed to `SharedObjects` and `MemoryManager`. Pass the category explicitly into `JIRMethodEntries` and then its method form caches before the analysis starts. Avoid a second unobserved manager and avoid plain soft references outside it.

Use one slot array per existing cache owner. Allocate a `FormSlot` only on first use, with atomic publication so concurrent lookups share its single-flight lock. Use an atomic reference array or equivalent owner lock; do not allocate a lock/counter object for every untouched instruction. Each used slot holds a managed reference to `ForwardReversePayload<F>`:

```kotlin
class ForwardReversePayload<F : Any>(val forward: F) {
    // Backward is initialized once from this exact forward form.
}

class FormSlot<F : Any> {
    // Small lock/state plus Reference<ForwardReversePayload<F>>?, no F.
}

fun <F : Any, R> withForm(
    slot: Int,
    direction: Direction,
    forwardBuilder: () -> F,
    use: (F) -> R,
): R
```

The builder is passed to access and is not stored as a closure in the slot. Existing `MethodForms` getters can remain compatibility adapters. Add `withStatement`, `withCall`, `withEntryRules` and `withExitRules` accessors; production application uses them. A JVM override pins the forward/backward payload for the full operation. Keep the payload reachable through the callback, using `Reference.reachabilityFence(payload)` in a `finally` block if the selected form is the only value passed to the callback. This needs no lease object per access. A reference/test implementation can use a default accessor over its current getter.

Use one managed reference per used forward/backward pair, not two references per direction and not one payload for every plan in a method. Statement/exit slots remain physical-method scoped; call and entry slots remain full-`MethodKey` scoped.

### Access and publication

1. Read the managed reference once into a strong local payload.
2. If absent, take the slot's single-flight build lock and recheck.
3. Build the forward form completely. Keep failed construction unpublished.
4. Register its payload with the managed manager. If registration returns null because caching is disabled, keep no strong fallback in the slot.
5. Obtain the selected direction. Build reversal once under the payload's lock from its own forward form. A failed reversal leaves that forward form usable while its managed payload survives.
6. Apply the form with the payload held strongly by the local accessor. Release it when the accessor returns, including on exception/cancellation.

The cache has no mutable form arrays visible to other contexts. A cleanup can clear the managed reference during a call, but it cannot invalidate the local payload. Concurrent users of the same surviving slot share one published payload. If cleanup causes separate transient builds while caching is disabled, their logical IDs remain equal. Reject recursive construction of the same slot rather than returning a half-built form. Build dependencies stay acyclic: exit construction may read entry rules, but entry construction must not read that exit slot.

In `RunMethodCallServices`, hold the lease through the active frame-stack walk. A suspended subscription stores the call site, keys and support metadata, then reacquires the plan on resume. It does not retain a plan while waiting for a callee. Boundary worklists hold a lease only while they use the form. Audit `RunMethodAnalyzer.reversedEnds`, guards, closures and deferred callbacks so none pins a whole method cache by accident.

### Managed-reference lifecycle

Reuse the original manager's `createRef`, `cleanup` and `enable` API. The requested rollback removes
the lifecycle lock, identity registry, reference queue and tracking diagnostic APIs. O2 still registers
rebuildable form payloads through the original API and pins a payload locally while it is applied.
Keep the memory guard threshold and GC/cancellation sequence unchanged.

A registration/cleanup race and retained cleared wrappers are questions for a separate design. The
[new proposal's N11](bidirectional-core-optimization-20261013.md#n11-optional-managed-reference-manager-redesign)
lists a possible manager redesign and its measurement and validation gates. It is not selected here.

### Strong-owner audit

| Owner | May remain strong | Must not retain |
| --- | --- | --- |
| Method/form owners | Method keys, stable rule/action headers, existing graph/alias inputs | Every compiled statement/call/exit payload |
| Predicate state | Contributions, logical node/proof keys and support required for current joins | A compiled `RulePredicate`/index graph, parent `CallPlan`, form payload, or slot lease |
| Witnesses/frontier/seeds | Report owner and owned support/requirement/end-fact descriptors | A compiled rule wrapper or its owner bundle |
| Call subscriptions | Caller/callee keys, statement and saved support | A suspended plan or frame closure containing a lease |
| Manager registry | Soft reference and tracking entry | A referent or builder closure |
| Existing validation/resolution caches | Their current rule configuration data | New compiled form payloads |

Rule-template retention and graph/alias cache redesign are outside this selection. They can limit the total saving. A large active form is also not reclaimable while it is being used. Record active-pin bytes, stable metadata bytes and remaining strong paths instead of promising to recover the full method-cache union.

## O3: compact form collections and indexes

### Owned collections

Add a small internal collection family used only by form construction. Preserve the existing read-only `List`, `Set` and `Map` interfaces where practical.

| Representation | Storage |
| --- | --- |
| Empty vector/set/map | Shared immutable instance, no per-field backing array/map |
| Singleton vector | Direct element, no one-element array or list wrapper |
| Many vector | One private owned reference array; no additional unmodifiable wrapper |
| Singleton set/map | Direct key and optional value |
| Small ordered set/map | Owned key/value arrays with linear lookup |
| Larger ordered set/map | Same ordered arrays plus one private lookup index |

Start with linear lookup through eight keys and an index above eight. This is a storage threshold, not an analysis limit. Validate it with a lookup/allocation benchmark before release; change the named constant if measured access makes another threshold better. Keep original first-occurrence order, set equality and map equality.

Public constructors snapshot caller collections once. Internal builders can transfer a newly owned final-sized array to a private frozen constructor; then they cannot mutate or reuse it. Repeated public builder use remains safe: each public build is a snapshot, and later additions cannot change an earlier form. Keep owned-vector implementations internal and expose existing public collection types across the common/JVM module boundary. Do not expose raw arrays or a public `alreadyOwned` flag. Java collection mutation methods, mutable entries and iterator removal remain unsupported. Subviews retain only the owning immutable vector/index needed for their lifetime.

### StatementSummary

Store touched bases, targets, filters, plain edges and conditional source-rule descriptors in compact owned collections. Keep sink rule descriptors in the owning rule statement/call plan. Targets include conditional action targets; leaf indexes include conditional read bases. Preserve builder semantics instead of deriving touched bases from an arbitrary union in every constructor.

Keep the original ordered edge vector once. For a nonempty base index, store ordered base keys, offsets and edge ordinals. Edges for one base need not be contiguous in the original vector; ordinals preserve their original relative order. Empty and singleton cases have direct representations. Cache each compatibility `edgesOf(base)` view once; direct application iterates its ordinals without constructing a view.

Use compact maps for operand/result filters, retaining the same `TypeFilter` objects and `and` combination semantics. Do not replace a filter by a type name or merge its mark policy into a weaker predicate. Build targets once, with no `groupBy`/`mapValues` container chain.

Share fields individually. `StatementSummary.EMPTY` is valid only when touched bases, edges, conditional rules, filters and other semantic fields are all empty. An edge-free touched base still kills. STMT, STAGE and GEN remain distinct. `buildStage` accounts for its complete read/target set; `buildGen` does not invent touched bases. End facts remain GEN behavior, not source-gated actions.

Reversal owns only the required reversed plain transfers and metadata. O1's conditional source reversal is a predicate leaf view, so it does not rebuild a branch object graph for every potential conjunction. Preserve A5 identities and dropped forward filters. Do not perform a separate O8 general reverse-compaction rewrite.

### CallPlan and rule statements

Keep one ordered stage vector. `CallPoint` has six fixed values. Replace `groupBy`-generated stage lists/maps with a compact point directory and stage ordinals. Use a shared empty directory when no stages exist, a direct singleton for one stage, and primitive offset/ordinal arrays for larger plans. Cache read-only compatibility views; the frame walker uses a direct `stagesAt(point)` accessor.

Keep point/stage order, guards, identity-candidate classification and the rule that stages from one point read the same input. `CallStage.Actions` retains its full ordered step chain. `CallStage.Callees` retains distinct method keys and their order. This work does not remove repeated cleaner actions or alter callee resolution.

Use compact owned vectors for source/sink rule descriptors, end actions, steps and callees. Under O1 these store bound rule/action descriptors rather than DNF alternatives. Keep entry/exit fields and observer `passReads`. `withIdentities` can adopt existing owned vectors and append newly owned identities, without copying nested payloads again.

## Specifications, tests and acceptance

Update [interpreter.md](../../spec/interpreter.md) §4.2 with signed folding before negative approximation, NNF evaluation, proof-based support and array OR nodes. Update its condition/reversal/seed sections and G2 footprint wording without changing the exclusion table. Update [ap.md](../../spec/ap.md) conjunction/witness/seed implementation descriptions where they assume numbered expanded alternatives. Update [ap-impl.md](../../spec/ap-impl.md) §23.7, strong-cache identity requirements and F74 temporary identity. Update [analyzer-core.md](../../spec/analyzer-core.md), implementation contracts and [validation-plan.md](../../spec/validation-plan.md) for the new handles and checks. Keep existing case IDs; add new cases rather than renumbering prior controls.

| Area | Required evidence |
| --- | --- |
| Signed folding | Source and sink `P AND NOT P` are false; `P OR NOT P` is true; real serialized Object/array expansion folds false; raw non-mark complements fold before approximate static evaluation; nested false branch removed; invalid raw discarded action still rejected; remaining cleaner/pass policies unchanged |
| NNF equivalence | Corrected-policy cube oracle versus NNF complete closures for source and sink rules, with input-order permutations, layers, exclusions, repeated leaves, arrays, unary/multi classes, source target filters and exit footprints |
| Proofs and seeds | Same-call support and confirmation; distinct rule/context/proof scopes; no premature AND firing; backward exact/may layers; full matching condition handoff within witnessed contexts; full-predicate end-trigger requirements including unwitnessed OR branches; trigger-once; CONFIRMED end facts; complete source admission values |
| Reclamation | Evict between join inputs and across directions; rebuild guards and temporaries; cleanup during a leased call; suspended call resume; disabled caching; failed build/reversal; concurrent access and cleanup; bounded registry tracking |
| Ownership and compact forms | Caller mutation has no effect; empty/singleton/many behavior; edge/stage order; cached view reuse; kill versus EMPTY; STMT/STAGE/GEN; full filter/mark policy; reversal and identities |

Use existing common and JVM bidi suites, plus new focused compiler/store/cache/container suites described in the plan. Do not require the old object address to survive an explicit eviction. Require the same logical keys and analysis effects. GC collection timing is not a correctness oracle: use controlled managed cleanup and reachability checks.

Measure compiled expression nodes, leaves, actions, predicate support/proof counts, emitted combinations, container sizes, cache hits/misses, rebuild time, cleared references and active leases. Compare retained owners at comparable progress. A compiler stress fixture such as an AND of many two-way ORs must compile proportional to its expression, without building 2^n forms; this is not a new real-project configuration.

Finally repeat the four fixed workloads through the direct `runProjectAnalyzer` task with the existing 8/8/12/8 GiB heaps and 1000/1080/1080/1080 second limits. Use the [input inventory](../reports/bidirectional-project-inputs.md), compare strict sink locations, and keep the known PDF exit-location defect visible. Separate any finding change caused by a proved unsatisfiable raw condition from an unintended propagation change; do not silently relax comparison keys. Record known control-suite failures rather than adding ignores.

Success requires no compiled cube/action/array products, reclaimable unused form payloads, preserved live support across rebuilds, smaller form container owners and no unexplained finding regression. Full workload completion within existing budgets is the final resource gate. If the scans still fail, report the remaining owners; O1–O3 do not include IR or the other deferred optimizations.
