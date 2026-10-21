# Reduce run-1 cleaner work and static continuation state

Date: 21 October 2026. Status: **numbered optimizations 1–5 selected for
the next iteration**. The eight profile readers and both OOM analyses are
closed. The analyzer implementation is unchanged by this design update.

Use the [detailed implementation specification](bidirectional-cleaner-intermediate-work-implementation-20261021.md)
for the selected patch boundaries, storage lifecycle, code and proof/test
gates. Its narrower implementation choices control: scalar static reply
resumption, suffix-aware schedules and run-1 completed-step deduplication.
The separate construction follow-on, later FULL publication candidate and
reporting fix remain unselected.

Source: `5d3a706cfaa8fa5620dab5c5b268d02b1a8432e2`. Use the
[current report](../reports/bidirectional-run1-single-premise-rules-performance-20261021.md)
for settings, strict finding failures and measurement authority. Earlier
candidate reports are stale for this proposal.

| Project | Latest reached | Latest COMPLETE | Accepted forward report |
| --- | --- | --- | --- |
| OWASP | run-2 TIMEOUT | run-1 | run-1 |
| conductor | run-1 TIMEOUT | none | none |
| thingsboard | run-1 OOM | none | none |
| Stirling-PDF | run-2 OOM | run-1 | run-1 |

## Scope and rank

These proposals seek equal semantic results. No new run-1 approximation
is proposed. Preserve the selected representative premise, complete local
condition checks, source/sink occurrences, all field transfers, tails,
field exclusions, ordinary FLOW mark exclusions, layers, primitive policy,
static writer support and later FULL behavior. Keep request barriers,
cache managers, IR storage and the iteration schedule. Keep
`trackFactsReachAnalysisEnd` ignored. Existing permission to vary batch
callback counts does not permit changes to semantic occurrences.

Follow [proof-guide.md](../../spec/proof-guide.md) before implementation.
Prove the reference relation and effect correspondence in a constructive
Lean model. Then use the model to guide production controls. If a proof
needs a new field, cleaner or source contract, stop and specify that major
change. A proof of a different contract does not authorize it.

The priority is run-1. The memory evidence is strongest for static
continuations. The source and CPU evidence also support avoiding upstream
cleaner work. These are expected-impact priorities, not measured speedups.

| Priority | Candidate | Evidence and expected effect |
| --- | --- | --- |
| 1 | Merge static continuation images per complete resume context | Static owner union 4.766 GB in thingsboard run-1. One actor has 63746 leaf rows but 3309 complete header/context keys. Avoid per-image service rows and metadata. Payload images still remain. |
| 2 | Execute each eligible GROUP return cleaner once per block | One measured stage reaches 506 cleaner steps. Source expands full marks before group projection. Avoid repeated eligible plain operators and their pending resume sites. Actual eligible duplicate count is not known. |
| 3 | Deduplicate complete intermediate cleaner results | Whole OWASP run-1 cleaner ancestry is 84.96% of Java samples and 82.83% of estimated allocation weight. Source controls prove equal-key branching can multiply work. Actual intermediate multiplicity is not measured. |
| 4 | Reuse immutable mark-exclusion headers | OWASP run-1 normalization/projection exact leaves total 36.08% of Java samples. Avoid repeated metadata scans and allocation after avoiding work above. |
| 5 | Avoid an empty static-summary copy | Source confirms an unnecessary constructor path. Constructor allocation ancestry is 5.01% in OWASP run-1, but the empty-copy share is unmeasured. Expected benefit is narrower. |

These are priorities, not predicted speedup factors. A retained union is
not a partition. The one-actor directory count must not become a global
saved-byte estimate. A small count of groups does not bound the number
of sites, paths, full-mark-derived operators or intermediate list entries.

The base-only source comparison explains the work boundary: the old
rewriter filters by base, caches a complete rewritten AP and removes equal
surviving alternatives inside its cleaner. The current plan enumerates
full marks before group projection and processes intermediate lists.
Reducing the mark domain did not remove those action/list dimensions.
Do not add a whole-result cache without a separate design for its stateful
requests and lifetime. This proposal avoids work within existing owners.

Thingsboard has a distinct CPU cost: call-form construction is present
in 57.84% of its full run-1 Java samples. Its cleaner execution ancestry
is 6.98%, although cleaner allocation ancestry is 37.00%. Candidate 2's
execution change does not remove all construction work. A separately
selectable construction follow-on is specified below. No first-build or
rebuild rate was measured; do not assume cache thrashing.

## Merge static continuation images per complete resume context

Current behavior: `Run1PointResume.request` creates one point row per
current FLOW leaf. Each row has a key, saved FLOW tree, continuation and
global interest. Field/group service membership is small, but each site
and image has its own waiting row.

New behavior: one actor row names a complete resume context and FLOW
header. Keep its pending images separately for each requested group.
Merge images in a trie or an exact set. Send one service interest per
row/group while that group is pending. A reply resumes only that group's
images. Do not apply a reply to images that requested other groups.

```java
void consume() {
    Box x = C.f;
    sanitize(x); // configured cleaner chain
    sink(x);
}
```

```text
At the same site, original I=C.f/*/FLOW, layer and call state:
  I -> this.a.[any]/FLOW, mx={G1}, request H
  I -> this.b.[any]/FLOW, mx={G2}, request H
  I -> this.c.[any]/FLOW, mx={G2}, request K

Before: three point rows and three service interests.
After:  one row, with pending[H]={a.[any],b.[any]}, pending[K]={c.[any]}.
        Supply H resumes a and b. It does not resume c with H.
```

The current reply already clears FLOW mark exclusions before it applies
the concrete supply. Thus, clear mx at this storage boundary. Keep field
exclusions and all other context. This does not clear mx on ordinary
propagated facts or on static writer selectors.

```kotlin
// Pseudocode: all objects belong to one method actor and AP manager.
data class ResumeKey(
    val site: ResumeSite,             // complete logical operation + phase
    val initial: InitialAp,
    val base: AccessPathBase,
    val tail: Tail,
    val fieldExclusion: ExclusionSet,
    val layer: Layer,
    val callState: CallResumeState?,
)

class PendingPoint(val id: Long, val key: ResumeKey) {
    // Exact association: group -> current images that requested that group.
    val images = mutableMapOf<GroupId, PendingFlowImages>()
}

fun request(at: OperationInput, groups: GroupSet) {
    val original = at.premise as? InitialAp ?: return
    val flow = at.facts as? FlowTree ?: return
    if (original.base != STATIC || original.mark !is MarkSlot.Star) return

    val relation = flow.withMarkExclusion(MarkExclusions.EMPTY) // proposed helper
    val key = ResumeKey(at.site, original, flow.base, flow.tail,
        flow.exclusion, flow.layer, at.callState)
    val row = getOrCreatePoint(key)
    val subscribe = mutableGroupSet()
    for (group in groups) {
        val old = row.images[group]
        if (old == null) {
            row.images[group] = PendingFlowImages.of(relation)
            subscribe.add(group)
        } else old.addAll(relation) // union paths, never discard a distinct image
    }
    if (!subscribe.isEmpty()) {
        staticBatch.interests += StaticInterest(methodKey, row.id,
            original.toPattern(), subscribe.freeze())
    }
}

fun answer(row: PendingPoint, supply: RegionGroups) {
    for (group in supply.groups) {
        val images = row.images.remove(group) ?: continue
        // Existing concrete-initial and summary-application rules.
        resumeWithSupply(row.key, images, supply.only(group))
    }
    if (row.images.isEmpty()) removePointAndKey(row)
}
```

`PendingFlowImages` has an exact reference implementation as a set of
flattened scalar current images without mx, as in the old per-leaf rows.
It is not a set of arbitrary whole-tree wrapper objects. The optimized implementation can use
the existing FLOW trie union only after proving that materialization and
resumption distribute over that union. Preserve base, tail and field E
partitions. Do not merge different originals, sites, layers or call states.
Do not create a class-wide static initial, a new tail or a new field cut.

At an existing row, add a late image to the correct group's payload even
if no new service subscription is needed. A reply already queued for that
group can process the new image: the group is globally available. After
that group drains, a later request for it creates a new pending payload
and asks the service again. The service can answer it immediately. Remove
the directory row only when every pending group has drained. Keep monotone
point IDs so an old reply cannot address a reused ID.

The actor owns current images and resume state. `Run1StaticService` keeps
only the existing owner/id/region/group interest. `MethodEdgeStore` keeps
its current responsibility. No callback or mutable owner crosses the
service boundary. No second cumulative group-reach store is added.
Keep the existing call, writer-input, conditional-join and direct-reply
dispatch before the packed `points` branch. This change does not reinterpret
those other continuation directories.

Measured scope: one thingsboard actor, run-1 OOM, has 63746 point rows.
Removing mx alone gives 55406 keys, a 13.08% reduction. Removing path from
the directory key, while retaining it in the payload, gives 3309 header
keys, or 19.26 old rows per header. The row set has 5 originals and 134
image geometries; 99.10% of rows name CLEAN-phase cleaner sites. These
counts do not include per-group payload costs or establish a global ratio.
The full static retained union also contains other state that stays.

Required proofs and controls:

- Canonicalization removes only mx that the existing reply already clears.
- For every group, saved image membership is equal to the old pending
  rows' union. Never take a cross-product of groups and images.
- Concrete supply application over merged images has the same result and
  semantic effect set as application to each old image.
- Partial replies, duplicate offers, supply-before-request, late images,
  queued replies, full row removal and later registration preserve that
  membership. Include cancellation and multiple pending groups.
- Preserve complete operation/action/cleaner-phase addresses and
  null/true/false call identity state. Internal IDs and callback counts
  can differ. Semantic source/sink identities must remain equal.

Complexity: service and actor directory rows become proportional to
complete headers/contexts, rather than leaf images. The payload still
stores every required group/image membership. This is not a proof that
all static state becomes small or that the entire retained union disappears.
Evidence: `validation/thingsboard-heap-analysis.*`,
`validation/thingsboard-point-probe-analysis.*` and
`validation/static-point-key-equivalence-review.md` in the current study.
The first probe's unavailable class columns remain preserved; the second
uses correct nullable MAT class metadata and matches all 63746 rows.

## Execute each eligible GROUP return cleaner once per block

Current behavior: `JIRCallPlanBuilder.rewriterSteps` loops over each
position and every full name in `UserDefinedRuleInfo.relevantTaintMarks`.
`JIRRuleForms.mark` then maps each full mark to its run-1 group. This can
create many selected cleaners with the same `(base,group)` effect. The
condition simplifier does not reduce this action sequence.

New behavior: in `RUN1_GROUP`, execute the first eligible operation for
each `(base,group)` in each contiguous eligible return-rewriter block.
Keep its original descriptor and logical step ordinal. A barrier resets
the selection. FULL retains its complete original sequence.

This is a compiler/interpreter optimization of the existing cleaner law.
It does not introduce a field-cleaner law. The earlier narrow draft used
only adjacent equal root cleaners. The source review permits a larger
block for plain nonstatic selected cleaners because their FLOW branch
ignores path/reach and their GROUP TAINT branch cannot remove a mark.
This broader scope requires the continuation proof below.

```java
Object y = configured(x);
// Return-rewriter metadata names full marks T1,T2,... in group G,
// then U in H, then another full mark in G.
```

```text
Raw eligible block: Clean(return,G), Clean(return,G), Clean(return,H), Clean(return,G)
Executed block:     first G, first H
FLOW:              same geometry, mx union {G,H}; requests at first G/H
GROUP TAINT:       unchanged by every selected cleaner
Late static G:     first G point resumes TAINT/G; later eligible steps preserve it
FULL:              execute every original full-mark cleaner
```

Only the generated `Actions(RETURNED, REWRITTEN, ...)` stage qualifies.
Do not apply this pass to configured bound cleaners or other stages.
An eligible step is `CleanStep.Clean`, with a nonstatic legal base and a
nonnull genuine GROUP selector. Element paths and different reaches are
allowed by the current nonstatic primitive. Keep the first descriptor's
path/reach exactly: the special STATIC-result wrapper can still read them.
`Field`, `Kill`, RemoveAll/null mark, STATIC, unsupported/control marks,
and every other operation are barriers. A String-bytes field companion
is a barrier even if it follows an eligible root cleaner. Never move a
step across that companion.

```kotlin
// Pseudocode: retain raw steps and original addresses. Do not compact or
// renumber CleanerInput.step, OperationId.substep or Field TemporaryKeys.
fun executeIndices(raw: List<CleanStep>, firstStep: Int): List<Int> {
    val result = ArrayList<Int>()
    val seen = HashSet<Pair<AccessPathBase, GroupId>>()
    for (i in firstStep until raw.size) {
        val key = eligibleReturnGroupKey(raw[i]) // only in RUN1_GROUP stage
        if (key == null) {
            seen.clear()
            result += i
        } else if (seen.add(key)) result += i
    }
    return result
}

// Existing phase/state handling uses each ORIGINAL index.
for (stepIndex in executeIndices(steps, firstStep)) {
    executeExistingStep(steps[stepIndex], stepIndex,
        if (stepIndex == firstStep) firstPhase else CleanerPhase.INPUT)
}
```

The eligibility function is a proposed view-aware compiler helper. A
precomputed default-entry mask is possible, but a suffix FLOW invocation
must select relative to its actual `firstStep`. A whole-block mask could
skip the only G request after a mid-block entry. The code above handles
that case by computing from the invocation start. Do not put mutable
seen state on a shared compiled form. Retained GROUP indices must be stable
across rebuilds of that view and direction. FULL and reversed plans keep
their existing raw indexing; they do not inherit this forward GROUP mask.
Keep original action/source/sink
admission and occurrence IDs; this does not change rule metadata.

For one eligible matching FLOW primitive, the only change is
`mx := mx union {G}`, plus a G request. All geometry, layer, original and
call state stay equal. These unions are idempotent and commute within
the eligible block. A GROUP TAINT value is preserved by every selected
cleaner in the block. A disjoint base is preserved by the primitive.
No eligible operation can change the base or AP geometry.

Ordinary duplicate requests address the same initial/group and already
merge. Static-origin requests instead create distinct point sites. The
optimization intentionally removes later sites. Therefore, prove equal
semantic outputs at the block exit, rather than equality of internal
site-registration sets. The first G site receives existing or late supply;
its TAINT/G reply passes every remaining eligible selected cleaner. It
covers the same exit value as a reply at a removed later G site. Keep the
exact path, tail, field E, layer, original and call state. Do not conflate
images or groups. `PointReady` already clears mx at this reply boundary.

A disjoint STATIC input needs a separate case. The current wrapper can
publish or drop it at the first eligible step, instead of appending it
to the next list. That first step is retained unchanged. No eligible step
creates STATIC later in the block. Do not replace its descriptor with a
canonical root or skip its publication behavior.

Required constructive proofs and controls:

- Prove the primitive FLOW union and GROUP TAINT identity laws for all
  legal headers, Element paths/reaches, disjoint bases and primitive policy.
  Prove block induction with every barrier retained.
- Prove first-site late-answer coverage at the block exit, both arrival
  orders, partial groups, static/ordinary originals and original call
  identity state. Semantic source/sink occurrences must remain equal.
- Prove the special STATIC first-step publish/drop case. Keep its original
  path/reach, site, filters and result handling.
- Prove original-ordinal resume addressing. Current plain-cleaner interior
  group replies are TAINT; retain suffix-relative selection for any exposed
  FLOW entry. Never assume arbitrary mid-block FLOW away.
- Keep FULL/reversed plans complete. Negative controls must cover field
  read/write temporaries, static cleaners on different fields, RemoveAll,
  Kill, source/sink stages and barriers between repeated groups.

K eligible operations execute at most U distinct `(base,group)` operators
per block and invocation start. The initial raw scan is still O(K).
The measured 506-step stage did not export each selected target/group.
It gives no actual collapse factor. This pass can reduce executed work
and pending point sites without reducing all form-build work.
Evidence: `validation/plain-group-cleaner-block-source-review.md`, the
narrow `validation/group-root-rewriter-source-audit.*` and its cross-rule
appendix. The broader block proof supersedes literal request-site equality
from the narrow draft; it does not authorize changes to field processing.

### Separately selectable construction follow-on

Even after the execution pass, a builder can still enumerate and lower
every full member. Each member repeats position/path conversion,
String-bytes companion checks and field lowering. Thingsboard's profile
justifies addressing construction as well as execution, but does not
assign all builder samples to this loop.

Prepare one immutable lowering template per existing admitted action
position. Keep every original full-mark ordinal and every field companion.
Use the same group selection and eligibility law as above. This is an
additional candidate, not an implied part of a post-lowering skip mask.

```kotlin
for (existingAdmittedAction in originalActionOrder) {
    if (info.relevantTaintMarks.isEmpty()) continue // preserve prior laziness
    val template = preparePositionReachAndCompanions(existingAdmittedAction)
    for ((ordinal, fullName) in info.relevantTaintMarks.withIndex()) {
        lowerUsingSameGeometry(template, runtimeGroup(fullName),
            originalActionId(ordinal))
    }
}
```

Base-type resolution is already memoized per builder, and type filters
are already memoized by type. Target repeated geometry/lowering work,
not an invented uncached base-type lookup. Prove identical null/type
behavior, filters, companions, original TemporaryKeys and action order.
Do not change FULL metadata or enumerate group members through a new
census. No group-count construction bound is claimed. More aggressive
pre-lowering elimination needs its own proof of identical admitted
operations and diagnostics.

Form owners remain strong and payloads remain managed soft references.
Measure first builds, existing-slot rebuilds and uncached builds before
claiming a cache-lifetime cause. This candidate adds no new cache manager
or weak identity cache. Source: `validation/thingsboard-form-build-source-audit.*`.

## Deduplicate complete intermediate cleaner results

Current behavior: `cleanChain` uses lists between actions. Equal complete
results enter the next action separately. Edge-store deduplication happens
after this call suffix and cannot remove this earlier work.

New behavior: use an insertion-ordered set at each completed step output,
within one invocation. Keep all complete key fields.

```java
void helper(Object p) {
    Inner i = new Inner(); i.value = p;
    Box x = new Box(); x.inner = i;
    sanitize(x); // selected cleaners on x.inner.value
}
```

```text
Run-1 GROUP input F = I -> x.inner.[any]/G / DEMAND.
Nested read/selected-clean/write can return [F,F].
Before: both copies enter every remaining cleaner.
After:  one complete key enters the next cleaner.
```

```kotlin
data class PlanFact(
    val premise: PremiseKey,
    val facts: Facts,
    val identityCandidate: Boolean?,
)

// One cleanChain invocation, one step output.
val next = LinkedHashSet<PlanFact>()
fun emitNext(result: PlanFact) { next.add(result) }
// Existing branches call emitNext. Preserve first-arrival order.
current = next.toList()
```

`Facts` equality must retain kind, base, layer, full tree, tail, field E
and FLOW mx. Keep the original premise and three-way identity value.
The invocation fixes method/node/plan/stage/resume context; do not share
this set across invocations or late static resumes.

The source-bound 12-action TAINT control has 4096 entries with one key,
including a control with 12 different selected groups. Deduplication gives
one entry and 24 clean calls instead of 8190. For FLOW, different exclusion
subsets can be different keys. Exact deduplication must retain them. These
are model counts, not actual project multiplicities.

Prove equality of result-key sets AND semantic effect sets for the stateful
interpreter. Keep request original/group/site, static point registrations,
writer/supply effects, complete summary publications and source/sink
occurrences. Do not assume callbacks are pure. Account for late writer
answers and repeated registration. Deduplicating this step's outputs keeps
its callbacks, but can skip repeated callbacks at later steps. Preserve
non-idempotent semantic effects separately if any exist.

The thingsboard snapshot has only 489 PlanFacts and 28 call frames.
Thus, this candidate addresses transient work and allocation. It must not
be described as removing that snapshot's 4.766 GB static owner union.

## Reuse immutable mark-exclusion results

Current behavior: an immutable set addition can return its old set, but
`MarkExclusions.plus/union` still normalizes it. GROUP projection scans
atoms before returning an already projected header. These paths repeatedly
call synchronized `MarkTable` methods.

New behavior: return an unchanged normalized header; retain immutable
metadata that states whether it has FULL atoms. Keep catalog validation
and interner synchronization.

```java
void check(Object x) {
    sanitize(x); // selected group G
    sanitize(x); // selects G again
}
```

```text
Input: I -> x/FLOW with mx={G}; add G again, then project to GROUP.
Before: repeat metadata scans, normalization and temporary allocations.
After:  return the existing header. Keep the request and fact unchanged.
```

```kotlin
operator fun plus(mark: TaintMark): MarkExclusions {
    if (mark in atoms) return this
    return normalized(atoms + mark, catalog)
}
fun union(other: MarkExclusions): MarkExclusions {
    val table = tableWith(other) // validate FIRST
    val combined = atoms + other.atoms
    if (combined === atoms && catalog === table) return this
    return normalized(combined, table)
}
fun projected(table: MarkTable, mode: MarkMode): MarkExclusions {
    if (mode == MarkMode.FULL) return withCatalog(table)
    if (isEmpty) return EMPTY
    if (catalog === table && !hasFullAtoms) return this
    return existingProjection(table, mode)
}
```

Calculate metadata when immutable atom keys and catalog ownership are
fixed. A catalog-free nonempty header must acquire and validate a catalog
before using that metadata. EMPTY remains catalog-independent. Do not
read a mutable interner without its lock or add a full-member census.

Prove exact normalization/projection equality for GROUP/FULL/mixed headers,
ZERO and catalog acquisition. A group excludes all its FULL members; a
finite member set does not exclude its group. Keep cross-catalog rejection,
immutable public arrays, negative conditions and run-2 group demand matching.
This candidate reduces cost per operation, not the number of semantic keys.

## Avoid an empty static-summary copy

Current behavior: run-1 cleaner READ/WRITE fallback can derive no static
operations, then call `withStaticOperations(emptyList())`. The constructor
shares owned payloads but rebuilds `byBase` and `targets` per input.

New behavior: retain the original summary in this empty-to-empty case.

```java
void sanitize(Box x) { /* selected cleaner on x.value, no static operation */ }
```

```text
Before: unchanged summary -> new wrapper and derived directories.
After:  unchanged summary -> same summary object.
Fact transfer, cleaner field read/write and requests remain equal.
```

```kotlin
// Existing fallback already requires summary.staticOperations.isEmpty().
val operations = existingStaticOperationDerivation(/* unchanged */)
val compiled = if (operations.isEmpty()) summary
               else summary.withStaticOperations(operations)
```

Prove equality of interpreter-visible fields with the same payload/filter
objects and deterministic derived directories. Retain nonempty descriptor
identities, action IDs and substeps. Do not replace nonempty lists through
structural equality. Wrapper identity must not be a semantic key. This is
a narrow technical candidate; constructor ancestry is not proof that its
arguments were empty. Source audit:
`validation/source-audits/cleaner-empty-static-summary-5d3/`.

## Separate later-run candidate: publish only newly authorized summaries

This candidate has **no run-1 effect**. Schedule it separately after the
run-1 priority. PDF's run-2 OOM has 22601733 pending Publication wrappers,
89.95% of the physical Publication count. The ordinary method-edge union
is also large and remains a separate owner; wrapper removal will not
remove that whole store.

Current behavior: when a batch admits any fresh actual query, `addLink`
replays every currently permitted summary. Already authorized summaries
are queued again. Publication-index deduplication happens later.

```java
Object identity(Object x) { return x; }
// Many callers progressively add different actual FULL queries.
```

```text
Existing summaries: P1->Y1, P2->Y2, ..., Pk->Yk.
New query Pk+1 arrives.
Before: queue P1..Pk again, plus any newly authorized summary.
After:  queue only summaries whose complete premise became authorized now.
```

```kotlin
// Proposed admission result. The whole batch is installed first, as now.
data class QueryAdmission(val fresh: Set<InitialAp>, val firstAdmission: Boolean)

fun newlyAuthorized(p: PremiseKey, batch: QueryAdmission): Boolean {
    if (!currentQueries.permits(p)) return false
    if (p.isZero) return batch.firstAdmission
    return p.members.any { it in batch.fresh }
}

val batch = currentQueries.admitBatch(added, guides)
if (batch.fresh.isNotEmpty()) {
    for ((premise, facts) in summaries.all()) {
        if (newlyAuthorized(premise, batch)) queuePublications(premise, facts)
    }
    batch.fresh.forEach(::addInitial)
}
// retainSummary still queues every genuinely fresh summary delta.
```

The first version can retain the scan. It avoids wrapper creation and
repeated delivery work without a new index or cumulative answered cache.
Keep ZERO authorization on the first real admission. For later forward ND,
require every member to be admitted and at least one member to be fresh.
Do not authorize an ND premise through one independently matching member.

Prove monotone admission and an exact false-to-true authorization test.
Every old-authorized summary must already be queued or present in the
ordinary publication index. Every newly authorized summary must be queued
once. Include an existing summary that was previously only partly guided,
ZERO/GEN, late callers, ND's final missing member and fresh summary deltas
after authorization. Preserve demand-record additions and occurrence sets.
Do not change barriers to hide the pending growth.

The scalar census counts physical wrappers. It does not establish their
exact semantic duplicate ratio or promise a particular saved-byte factor.
Source replay semantics establish the avoidable-work mechanism; controls
must check it before selection.

## Reporting correctness prerequisite and next measurement

The strict PDF comparison misses two physical return locations because
boundary reporting loses the actual return anchor and exports a forced
Simple trace. Another 525 results fail location export. Keep these as
independent correctness failures. Design an anchor-preserving reporting
fix separately. Do not move sink evaluation from the common exit to each
return without a selected semantic design: that can change path/cube joins.

After selection and proofs, implement only selected candidates. Keep the
fixed configured project runs and a fresh main comparison. Measure run-1
engine time, latest reached/COMPLETE/accepted runs, strict locations, unique
summary/request state, complete intermediate multiplicity, pending rows,
CPU and allocation. Do not add a hidden field approximation, a new cache
manager or an IR storage change to obtain a better result.
