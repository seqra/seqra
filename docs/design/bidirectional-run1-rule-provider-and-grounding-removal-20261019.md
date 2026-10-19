# Run-1 rule views and removal of GroundingSupport

Date: 2026-10-19. Status: refactoring proposal. No runtime change is made.
Language: ASD-STE100 Simplified Technical English. Defined technical terms,
code names and mathematical notation keep their stated meanings.

The [spec's semantic inventory](../../spec/grouped-run1-statics.md#0-selected-semantic-scope)
compares the complete feature with the pre-feature a80 base. Its revision
2.1 clarifies source handling: project condition/action marks and reuse the
existing source AP transfer. No new `provider.sourceCases` API or runtime
source-case analysis is selected. The detailed source-family construction
is proof/regression evidence only.

The runtime under review is `ff2966455da2e5ffd0db655a1ff06c5954ed3c8a`.
The [termination investigation](../reports/bidirectional-grouped-statics-termination-investigation-20261019.md)
confirms a support-only work cycle with one group and one AP leaf.
This proposal removes that state dimension instead of making its formulas
faster to compare.

The unchanged AP/field baseline remains
`a80d5c580b3b4715ab39679fbf66f0f190125fa0`, as pinned in the
[feature spec](../../spec/grouped-run1-statics.md#1-pinned-implementation-baseline).
This proposal supersedes the guard/certificate retention and full-alias
designs in the [earlier next-iteration proposal](bidirectional-grouped-statics-next-iteration-20261019.md).
Its other measured optimization candidates remain separate work.

The proposal has two changes:

1. The new-core rule provider supplies a simplified GROUP condition for
   run-1 and the original FULL condition for later runs.
2. Delete `GroundingSupport` completely. Ordinary facts and summaries use
   ordinary premises and AP deltas. Static group supply uses ordinary
   completed summary application.

The user clarified the field contract: the previous iteration changed
marks and statics only. It did not authorize a change to field processing.
Keep the earlier run-1 DEMAND abstraction. Remove the extra exact-field
writer checks together with `GroundingSupport`. Do not add a replacement
validator or change the initial grammar. Section 6 states this boundary.

## 1. Current behavior and proposed behavior

| Area | Current behavior | Proposed behavior | Reason |
| --- | --- | --- | --- |
| Conditions in run-1 | GROUP input matches each original full-mark leaf and its proof alternatives | Provider projects and normalizes the condition once; runtime sees canonical group leaves | Remove repeated aliases and their branch products |
| Method facts | AP value plus a separate logical/fixed support expression | Premise, position, AP value and layer only | Restore the AP store's ownership and finite delta rule |
| Work on an old fact | A different support expression can enqueue it again | Enqueue only a semantic AP delta | Remove the confirmed recurrence |
| Source output | Carries original premise plus a composed support expression | Keeps the original singleton or complete ND premise | Preserve dependency through ordinary summary composition |
| Static writer | Formula evaluation plus forwarded input-grounding state | Early conditional summary; ordinary caller/static application discharges its complete premise | Use the analyzer's existing dependency mechanism |
| Later rules | Shared FULL forms with runtime mark projection | Explicit FULL form view and caches | Keep exact backward checks and later source permissions |

All examples below are pseudocode. Names identify responsibilities, not
already implemented APIs.

## 2. What GroundingSupport currently does

A method can analyze a conditional initial such as `p/Group(g)`.
This initial describes a query. Its presence does not prove that a root
actually supplied that mark to this call.

`GroundingSupport` tries to retain extra conditions under which an output
can count as an actual static writer. A row contains an original premise,
an accepted AP value, a checked literal, and sometimes a call relation.
AND and OR expressions combine these rows along execution paths.
`WithFixed` also carries evidence for a fixed generated output.

For example:

```text
conditional premise: p/Group(g)
condition:           ContainsMark(p.f, g)
fixed output:        C.out/Group(h)
```

The ordinary initial/output relation can lose the intermediate `.f`
check after widening. The current implementation carries that check as
support through all later facts. Static grounding later re-evaluates it
against an actual caller input. This extra exact-field validation exceeds
the selected marks-and-statics scope. The earlier run-1 DEMAND contract
already allowed bounded AP approximation.

This concern has spread into unrelated owners. `MethodEdgeStore` owns
support rows and merges formulas. Work items, call frames, N2 subscriptions,
publications and rule stores carry them. An AP duplicate can therefore
become new method work because a formula has a different syntax.
This is how `S OR (S AND B)` keeps the method alive.

The proposed deletion is complete. Do not replace the class with a renamed
guard formula, a support token on each AP row, a path-history key or a
second formula solver.

## 3. Provider-owned run-1 condition rewrite

### 3.1 Keep two explicit views

Keep the legacy provider and its shared rule objects unchanged.
The new-core adapter owns the selected condition view.
Use typed group literals. Do not invent full-mark strings to represent
group selectors.

```kotlin
enum class RuleView { RUN1_GROUP, FULL }

data class RuleCandidate<R>(
    val ordinal: Int,             // original order, before filtering
    val original: R,
)

data class GroupLiteral(
    val position: BoundPosition, // complete base/path/tail/check kind
    val group: GroupId,
)

sealed interface SelectedCondition {
    data class Full(val nnf: FullConditionNnf) : SelectedCondition
    data class Group(val nnf: PositiveNnf<GroupLiteral>) : SelectedCondition
}

interface BidiRuleProvider {
    fun candidates(query: RuleQuery): List<RuleCandidate<*>>
    fun condition(candidate: RuleCandidate<*>, binding: RuleBinding,
                  view: RuleView): SelectedCondition
}
```

The wrapper covers method sources/sinks, entry and exit rules, static
field sources, cleaners, pass-through rules and rewriters. Keep original
candidate order, rule/report metadata and configured action order.
The full ownership census still reads original effective marks.
`GeneratedMark` and every branch of a join retain the top-level owner.
Complete ownership before compiling GROUP conditions.

Resolve each condition mark through the existing ownership catalog.
When all marks share one group, their equal-position aliases collapse.
Imported marks retain their registered owners. Legal mixed-owner
conditions remain legal; this rewrite adds no single-owner input check.
An action target still uses its own registered owner.

### 3.2 Normalize in the correct order

```kotlin
fun run1Condition(rule: RuleCandidate<*>, binding: RuleBinding): GroupNnf {
    val signedFull = normalizeExactSigned(rule.original.condition)
    val boundFull = binding.rewriteMetadataAndPositions(signedFull)
    val foldedFull = normalizeExactSigned(boundFull)
    val positive = existingPositivePossibilityPolicy(foldedFull)
    return normalizePositiveNnf(positive.mapLeaves { leaf ->
        GroupLiteral(leaf.boundPosition, catalog.ownerOf(leaf.fullMark))
    })
}
```

The provider owns this sequence. The compiler consumes the selected
result; it must not rebuild the full alias list during run-1 execution.
Keep one NNF DAG. Do not expand cubes or distribute AND over OR.
Apply constant folds, equal-child elimination, same-connective flattening,
idempotence and positive absorption.

```text
P(x,T) OR  P(x,U)          -> P(x,g)
P(x,T) AND P(x,U)          -> P(x,g)
(P(x,T) OR P(x,U))
  AND (P(y,T) OR P(y,U))   -> P(x,g) AND P(y,g)
```

These folds require equal bound positions and equal check kinds.
`P(x.f,g)` and `P(x.h,g)` stay different.
Two argument slots stay different. Exact and any-field checks stay different.
Real spatial alternatives can still have more than one proof.
The proposal removes products caused only by full-mark aliases.

Fold signed full literals before group projection:

```text
P(x,T) AND NOT P(x,T) -> false
P(x,T) AND NOT P(x,U), T != U -> not a contradiction
```

For the second expression, the existing residual-negative possibility
policy can leave `P(x,T)`, then GROUP projection gives `P(x,g)`.
Projecting the signed expression first would create a false contradiction.
Group presence must not become a proof of full-mark absence.

The positive GROUP rewrite must preserve TRUE/FALSE classification after
the existing signed and role-specific policy. In particular, do not add a
signed GROUP tautology fold. Predicate-sink handoff omits unconditional
conditions; a new unconditional classification could remove a later FULL
sink seed. A remaining positive atom cannot become TRUE only because its
full aliases have one group.

### 3.3 Preserve role admission and actions

Cleaner, entry, pass-through and rewriter admission keep their current
role-specific metadata policy. A possible group condition is not proof
that a cleaner can execute unconditionally.
Perform that admission before selecting its executable action list.
The ordinary source/sink possibility rewrite does not override it.

Keep the original `ActionId`, including its action ordinal. Two equal
configured actions remain two actions. Do not run an action once per full
alias removed from the group condition.

For a source condition, only the mark selector changes:

```text
CM(p1, (g,T)) AND CM(p2, (g,U))
  -> CM(p1, g) AND CM(p2, g)
```

Keep distinct bound positions, use the ordinary complete binding and run the existing
source action with its group mark. Its AP transfer, original input Facts,
tails, bound positions and ActionId stay unchanged. There is no new source
transfer algorithm. The schematic execution is:

```text
guard = simplify(projectConditionMarks(original.boundCondition))
when guard has an ordinary accepted binding:
    execute existing source actions with group selectors and DEMAND layer
```

The baseline has unary and multi-input implementation paths. If equal
positions collapse to one GROUP leaf, do not select another AP primitive
from that new leaf count. Section 15.3 records this compiler regression
check. It does not require a new provider API, source-case store or phase.

Selected-mark cleaners request the removed position's group and never kill
group TAINT in run-1. Actual group input survives as DEMAND; a request alone
does not invent that input. The FLOW complement/DEMAND repair remains.
`RemoveAllMarks` and structural field kills retain their current rules.
`trackFactsReachAnalysisEnd` stays empty in every run.

### 3.4 Split GROUP and FULL form caches

Direction alone is insufficient: run-1 and run-3 are both forward.
Select rule view explicitly through context/form lookup.

```kotlin
fun RunConfig.ruleView() = if (run1) RUN1_GROUP else FULL

class MethodRuleForms {
    private val groupForms = ExistingFormCaches(provider.view(RUN1_GROUP))
    private val fullForms = ExistingFormCaches(provider.view(FULL))

    fun selected(run: RunConfig) = when (run.ruleView()) {
        RUN1_GROUP -> groupForms
        FULL -> fullForms
    }
}
```

Run-2 reverses a FULL forward form. Run-3 uses FULL forward forms.
Include the view in call, entry, exit and rule-bearing statement selection.
Keep stable `FormOwner`, occurrence and action IDs across rebuilding.
Reuse the current managed soft-reference mechanism. Do not rewrite
`SoftReferenceManager` or add a weak cache.

Remove `RulePredicate.byGroup` full-alias dispatch after GROUP forms contain
group atoms directly. The ordinary mark index can match those atoms once.

## 4. Restore plain AP storage and work

`MethodEdgeStore` owns AP admission only.
It must not import rule proof or grounding classes.

```kotlin
class MethodEdgeStore {
    fun add(node: Inst, premise: PremiseKey, value: Facts): Facts?
}

data class EdgeDelta(val premise: PremiseKey, val node: Inst, val facts: Facts)

fun emitTo(successor: Inst, premise: PremiseKey, value: Facts) {
    edges.add(successor, premise, value)?.let { delta ->
        worklist.add(EdgeDelta(premise, successor, delta))
    }
}
```

Retain the baseline temporary unchanged-work path and its finite repeat
check. It has no support field. Rule execution can still emit real requests
and observations while processing an admitted input. Their owners deduplicate
their own semantic memberships.

```kotlin
data class Publication(val premise: PremiseKey, val conclusion: Facts)
data class OperationInput(val site: ResumeSite, val premise: PremiseKey,
                          val facts: Facts, val callState: CallResumeState?)
data class PlanFact(val premise: PremiseKey, val facts: Facts,
                    val identityCandidate: Boolean?)
```

N2 owns exact current caller inputs. Summary storage owns current summary
relations. Predicate storage owns the selected condition's accepted inputs.
These owners change only on their ordinary fact/membership deltas.
Remove support-only revisions, cofactor directories and formula rebinding.
Do not replace them with another revision that can change without a
semantic value or dependency membership change.

## 5. Ownership after deletion

| Owner | Stored state | Authorized work |
| --- | --- | --- |
| Rule provider/form cache | Original rule metadata; selected GROUP or FULL condition; stable IDs | Compile/rebuild the selected view |
| Method actor and `MethodEdgeStore` | Current AP edges and ordinary work | Transfer fresh AP deltas; publish early static effects |
| Summary storage | Current ordinary singleton/ND summaries and ordinary requests | Join new input with old summary and new summary with old input |
| Caller subscription owner | Current qualified inputs at each call stage | Match and apply a whole compatible singleton/ND binding |
| Static service | Write regions, supplied region/group sets, read and point interests, references to ordinary early summaries | Join supply with all-static premises; route a writer interest to its known ordinary original |
| Point-resume owner | Actual operation/stage address, current AP image and delivered group memberships | Resume a fresh reply at that exact suffix |

The static service does not interpret source conditions or inspect a method
actor's stores under its lock. It sends immutable IDs and matched candidates.
The addressed owner performs AP matching and transfer.
No owner retains a derivation formula or old fact versions for grounding.

## 6. Keep the earlier run-1 DEMAND field contract

Use the same conservative AP interpretation for an ordinary early static
effect as for an ordinary DEMAND summary. A canonical marked initial can
over-approximate its incoming geometry. Keep ordinary AP matching, field
transfers, definite kills, field limits and tail rules unchanged.

```text
actual input:          p.left/g
canonical initial:    p.*/g
run-1 entry value:    p.[any]/g
condition:            ContainsMark(p.right,g)
possible run-1 output: C.out/g, DEMAND
```

The final line is a possible candidate under the earlier DEMAND contract.
There is an actual matched ordinary input and a reached writer operation,
but the bounded AP analysis has widened the checked location.
Do not describe this as an exact full-mark writer witness.
Later FULL runs reevaluate the original condition through ordinary DEMAND
refinement. A bounded FULL run need not reject each spatial candidate
immediately. This proposal adds no exact field validator.

Remove the current extra requirement to reject this candidate during
run-1. This restores the intended field contract. It does not permit
inventing arbitrary groups or treating a merely analyzed initial as a
rooted input. Sections 7 and 8 define that distinction.
All affected group TAINT and static FLOW summaries stay DEMAND.
Keep the existing rules for NORMAL records and group exclusions.

## 7. Keep writer dependency on the ordinary early summary

At a reached static write, publish the current ordinary edge immediately.
Do not propagate its static conclusion through the remainder of the CFG.
Keep its full original premise. A conditional source keeps its input
premise. An ND source keeps the union of all contributing originals.

```kotlin
fun staticWrite(input: OperationInput, output: Facts) {
    val effect = Publication(input.premise, output.toDemand())
    actor.summaryDelta(effect) // existing actor-owned publication barrier
}

fun applyCalleeSummary(binding: MatchedCallBinding, summary: Publication) {
    ordinaryApply(binding, summary) { callerPremise, output ->
        if (run1 && output.base == STATIC) {
            actor.summaryDelta(Publication(callerPremise, output.toDemand()))
        } else {
            continueAtCallSuffix(callerPremise, output)
        }
    }
}
```

This composes a nonstatic writer dependency toward its caller without
relaying a static fact through intermediate statements.
Use the existing summary delta and publication barrier. The pseudocode
does not authorize a second publication route or a direct service write
to another actor's summary store.
Capture a static effect at its logical operation output, as today.
Keep ordinary call binding, projection and the applicable action/cleaner
phase before that output. An early write is not a normal-return value:
it does not execute the remaining CFG or assert a caller continuation.
Keep the existing same-call/stage checks for a complete ND application.
A caller input from one call must not complete another call's missing slot.

Do not reset a conditional source result to ZERO.
The retained source paths already preserve their premise. The ignored
sink end-fact paths do not authorize a ZERO reset.

## 8. Pool admission and direct consumers

The pool is a set of possible static region/group values under the
run-1 abstraction. A pending initial alone cannot add a group to it.

```kotlin
fun onFreshStaticEffect(ref: SummaryRef, effect: Publication) {
    staticService.registerWriteRegion(effect.conclusion.geometry())
    when {
        effect.premise.isZero && effect.conclusion is TaintTree ->
            staticService.offer(effect.conclusion)
        effect.premise.members.all { it.base == STATIC } ->
            staticService.subscribeSummary(ref, effect.premise)
        else -> Unit // ordinary caller application must still discharge the premise
    }
}
```

Nonstatic or mixed premises remain ordinary summaries. Do not turn them
into method-level grounded bits. Do not infer supply from the set of
initials a method has analyzed.

For an all-static premise, match actual pool values against every member.
Use the ordinary singleton/ND AP operation at the owner.
This includes FLOW summaries, not only group-TAINT summaries.
For example, `C.f/FLOW -> D.h/FLOW` must transfer every actually supplied
group on C.f to its matching D.h image. FLOW alone supplies no group.
Do not wait for a marked summary on that path; it might never be requested.

```kotlin
fun onStaticSummaryCandidates(ref: SummaryRef, candidates: List<Facts>) {
    val effect = summaryStorage.borrow(ref) ?: return
    require(candidates.size == effect.premise.members.size)
    val matched = effect.premise.members.zip(candidates).map { (initial, value) ->
        ops.satisfying(value, initial, mode) ?: return
    }
    ordinaryApplyMatched(effect.premise, matched, effect.conclusion) { result ->
        if (result is TaintTree) staticService.offer(result.toDemand())
    }
}
```

The candidate list is one ordered, compatible, complete binding.
It is not a flat batch of alternatives. Check arity before `zip`, which
would otherwise truncate a missing ND slot.
The service index supplies candidates, not proof of AP matching.
In particular, `applyCombination` expects already matched inputs.
Recheck every ND member before calling it. Group equality alone is
insufficient. Preserve paths, tails and ordinary exclusions in matching.

An all-static effect is a positive ordinary summary dependency.
With no source/pool seed, a cycle such as `C.f/g -> C.h/g -> C.f/g`
cannot create its first group value.
After a real seed arrives, each fresh region/group membership is added once.
Late input and late summary arrivals both use register-before-snapshot joins.

### 8.1 Keep ordinary requests for nonstatic writer inputs

Keep the existing nonstatic request path. A reached mark-preserving writer
can have only a FLOW summary so far:

```text
set(p): p/FLOW -> C.f/FLOW
consumer interest: C.f/g
```

Index the early summary by its static output region. Join new interests
with old writer references and new writer references with old interests.
For a mark-preserving FLOW effect with an ordinary original, deliver the
interest to the method owner. That owner offers the group on the known
ordinary original through its existing temporary request store.

```kotlin
fun onWriterInterest(ref: SummaryRef, group: GroupId) {
    val effect = summaryStorage.borrow(ref) ?: return
    val original = effect.premise.singleOrNull() ?: return
    if (original.isOrdinaryBaseFlow && effect.conclusion is FlowTree) {
        temporaryRequests.offer(ordinaryRun1Slot(original), GroupSet.of(group))
        requestWork()
    }
}

// Keep the existing method-fact-queue-empty barrier.
summaryStorage.commit(temporaryRequests)
temporaryRequests = RequestStorage()
```

The existing nonstatic P/R request protocol then exposes matching concrete
group inputs. Ordinary summary application still decides whether a
ZERO-rooted or all-static writer effect follows. The request itself
supplies no group. Mere analysis of `p/g` supplies no group either.
The owner checks the legal singleton FLOW relation before routing.
As in the baseline request protocol, field and FLOW mark exclusions do
not suppress this request interest. They must not prevent a requested
complement from being analyzed.

This path does not walk a static call chain or construct a backward CFG
graph. It uses a retained summary reference and its original base.
The static service sends a counted owner event for each fresh
`(writer reference, wanted group)` delivery membership. It does not store
nonstatic actual input masks, cofactors or a copy of the summary's facts.
An unanswered ordinary request creates no supply and does not block
completion. The barrier waits for queued work, not a future answer.

An all-static FLOW copy instead transfers actual pool groups as shown
above. A group-changing or ND TAINT effect keeps its declared input
groups and complete ordinary binding. Do not request its output group
on every input as if that effect were mark-preserving.

### 8.2 Deliver point replies at their saved phase

Static reads remain write-gated. FLOW write admission enables their
conditional FLOW edges. A mark reply materializes the current marked
input at the saved operation and cleaner phase. Rerun that operation,
then continue its suffix. Only an already-applied read output starts at
the post-operation continuation. Neither case resumes at method entry.
Keep operation, call-stage and cleaner-phase identity in the continuation.
Do not retain a support expression in it.

ZERO can discover a static-premise summary only in run-1. Discovery must
retain that static premise; it does not discharge its group dependency.
Only a real pool match can supply the corresponding group.
Later runs require the real static premise even for ordinary application.

## 9. Traces that define the owner protocol

### Nonstatic writer

```text
set(p) { C.f = p; }
main() { x = source(g); set(x); }

set summary:            p/g -> C.f/g
mere analyzed p/g:     no pool supply
main call input:        ZERO -> p/g
ordinary composition:  ZERO -> C.f/g
pool:                   add (C.f,g)
```

With one extra wrapper, the wrapper first publishes `arg/g -> C.f/g`.
The complete dependency remains until a root or static binding discharges it.
No support formula or nonstatic grounded-input directory is needed.
If only `p/FLOW -> C.f/FLOW` exists when the consumer asks for g, section
8.1 first offers the ordinary p/g request. The request does not bypass
the complete caller join shown here.

### Static read to another write

```text
copy() { x = C.f; D.h = x; }
conditional effect: C.f/FLOW -> D.h/FLOW
empty group pool:   no D.h/g
later C.f/g:        apply the FLOW relation to actual g, then add D.h/g
```

### ND inputs stay correlated

```text
combine(p,q) { if P(p,g) AND P(q,g) then C.f = source(g); }
wrap(a,b) { combine(a,b); }
main() { wrap(marked,clean); wrap(clean,marked); }
```

`combine` publishes a complete two-input effect. `wrap` preserves both
originals. Neither call in `main` supplies the complete tuple.
No ZERO effect and no pool group follow from combining their separate slots.
Do not replace this test with a method-wide set saying both marks occurred.

### Marked-copy loop

The loop from the termination investigation reaches its AP fixed point.
Re-evaluating an equal guard creates no separate metadata value.
`MethodEdgeStore.add` returns no AP delta, so the loop generates no further
fact work. Real new requests and input/summary memberships still have their
own finite notification paths.

## 10. Later runs and observations

Keep the run schedule: GROUP forward/limit 1; FULL backward/limit 1;
FULL forward/limit 2; FULL backward/limit 3; then the current schedule.
Keep universal `S.* -> S.*` DEMAND guidance after run-1 and the current
selective field-write rules. No executable S-root is introduced in run-1.

Run-1 stores the original sink occurrence and report identity.
It does not preserve a product of full alias proofs.

```kotlin
// run-1 -> run-2
seed = PredicateSink(originalOccurrence, method, statement)

// run-2: decode with FULL forms, irrespective of the last cache access
fullForms.withSink(seed.occurrence) { sink ->
    sink.fullSeedPatterns().forEach(::emitBackwardSeed)
}
```

Current predicate sink handoff already uses the occurrence handle.
Make FULL selection explicit. Group leaf ordinals must never be decoded
as full leaf ordinals.
Forward runs do not own `SourceHitStore`; the run-2 to run-3 source handoff
stays FULL and keeps the original full edge and `ActionId`.
Batch-dependent callback and duplicate-work counts can change. Preserve
semantic report/source/sink identities and rule/action occurrences.

Preserve full conditions, rule metadata, generated mark ownership and
configured actions for later confirmation. Extra run-1 candidates caused
by the selected abstraction do not become exact full witnesses.

## 11. Deletion and replacement map

| Current component | Refactoring |
| --- | --- |
| `Run1Grounding.kt`: support AST, requirements, arguments, FLOW-shadow maps, rebinding and permits | Delete the file and all corresponding owner state |
| `MethodEdgeStore.supports`, support row counters, `addSupported`, support-aware unchanged path | Delete; retain ordinary AP admission and unchanged traversal |
| `EdgeDelta.grounding`, support-aware work repeat keys | Delete; ordinary semantic work keys only |
| Actor `currentGroundingSupport`, `withGroundingSupport`, guard/fixed/observe/revision helpers | Delete; use ordinary operation input and AP transfer |
| Actor `PointWork.support`, `RuleItem.support`, `guardedSummaryPremises` | Delete; keep ordinary point/rule work and complete premises |
| `PublicationIndex` support directories and support-only publication changes | Delete; current summary AP values own publication deltas |
| N2 support/cofactor maps and support revisions | Delete; retain exact current qualified added inputs and their AP growth |
| Grounding-only input handles and `borrowN2Cofactors` port plumbing | Delete; retain an ordinary input handle only where the baseline caller join still consumes it |
| `Subscription`, `Publication`, `PlanFact`, `OperationInput` support fields | Delete |
| Rule predicate/conjunction input support and formula merges | Delete; retain current facts, complete premises and selected NNF evaluation |
| Call guard replays and support rebinding | Delete; ordinary singleton/ND summary application remains |
| Static method grounded-input masks/forwarding, `InputReady`, `WriterReady` support probing | Delete; ordinary early summaries carry nonstatic dependencies to callers |
| Point writer support rows and cached writer-binding evaluations | Delete; early summary storage owns the relation |
| Run manager `BIDI_GROUNDING_STATISTICS` aggregation | Delete counters for the removed state; retain ordinary AP/request/static measurements |
| Static service | Retain write/read/group/point joins and ordinary writer-interest routing; apply all-static early summaries through ordinary owner AP matching |
| Full-alias GROUP dispatch | Delete; compile normalized group literals in provider-selected forms |
| FULL rules, source permissions, occurrence/action IDs, later AP semantics | Retain |

Adapt the old support tests. Preserve their semantic intent where it is
part of the new contract. Remove assertions about deleted metadata.
The strict disjoint-field writer rejection checked the extra validator.
Keep this case in the test inventory. Replace its run-1 rejection assertion
with an explicit DEMAND candidate and a later FULL refinement control.
Retain the independent controls for definite kills and disjoint transfer.

## 12. Scope boundary

Do not replace `GroundingSupport` with a second actual-input validator,
a guard graph, a certificate solver or a new initial/relation grammar.
Ordinary complete summary premises carry dependencies. The baseline
DEMAND field abstraction remains responsible for bounded geometry.

Any future exact-field writer validation needs a separate proposal and
its own finite progress proof. It is outside this refactoring.

The implementation must revise these documents together. Sections 15 and
16 give the complete section map and proof obligations:

| Document | Required update |
| --- | --- |
| [Feature spec](../../spec/grouped-run1-statics.md), sections 2–3, 5 and 7–17 | Update condition views, ordinary dependency/pool ownership and progress; remove extra exact-field writer checks and support transport. Section 15 below gives the full map. |
| Same spec, section 4 | Retain the existing initial grammar, entry conversion and field-transfer rules exactly |
| [Proof dossier](../../spec/grouped-run1-statics-proofs.md) | Prove ordinary summary dependency coverage under bounded AP abstraction; retire claims that require the deleted validator |
| [Earlier implementation proposal](bidirectional-grouped-statics-implementation-20261019.md), sections 8–11 and 13–15 | Replace support/cofactor/forwarded-input plumbing with the owners and joins in this proposal; retain logical operation and resume addresses |

## 13. Proof and implementation plan

Follow [proof-guide.md](../../spec/proof-guide.md). Use constructive Lean
models and executable finite witnesses before runtime implementation.
This document states proof obligations; it does not claim they are proved.

1. Update the normative spec to the clarified earlier field contract.
   Remove its extra exact-field writer validation and its requirement to
   propagate guard/certificate formulas. Keep field transfer unchanged.
2. Model provider projection separately from runtime execution. Prove that
   exact signed folding precedes GROUP projection, positive GROUP
   normalization preserves its projected meaning, and positions stay distinct.
   Reuse the original source AP transfer and bound positions. Prove
   output AP/tail and complete accepted premise coverage, not only truth.
   Prove inclusion of every full positive witness in the group condition.
3. Prove stable occurrence/action identity, exact FULL view selection,
   role admission and full backward seed coverage after group alias removal.
4. Model ordinary early-effect composition. Prove that original singleton
   and all ND premises survive each caller step. Prove no pool group comes
   from an unrooted initial or unseeded dependency cycle.
5. Prove source/pool witness coverage under the selected bounded AP
   abstraction, including static read-to-write paths and mixed premises.
   State the restored DEMAND approximation explicitly; do not reuse a
   strict-spatial theorem with a changed conclusion.
6. Prove owner isolation, register-before-snapshot arrival symmetry and
   finite notification identities. Under a finite program/catalog and the
   baseline finite AP domain, bound fresh value/dependency memberships and
   unchanged-work bursts. Each burst has finite repeat keys; a new burst
   requires a fresh bounded event. Formula syntax is absent from this bound.
7. Implement the provider view and cache split with failing GROUP/FULL
   identity and condition-normalization tests first.
8. Restore ordinary AP storage/work and remove support plumbing. Convert
   the reproduced loop cases into bounded-quiescence regressions.
9. Implement early-effect pool admission, ordinary all-static joins and
   nonstatic writer request routing. Test late interest/write/supply/summary
   arrivals, nested ND calls, rootless cycles, sibling fields, fixed targets,
   real kills and exact resume stages.
10. Run the required core/JVM/sequence gates. Then repeat only the four
    authorized fixed project configurations against fresh main.
    Record reached/completed/accepted iterations and run-1 engine time.

The latest measured reached/completed/accepted iterations are
**run-1 / none / none**, TIMEOUT, for OWASP, conductor, ThingsBoard and
Stirling-PDF. The refactoring has no new project measurements. Finite
convergence alone does not establish the 60-second engine target or
finding parity.

IR storage, reference-manager changes and other mark-normalization/cache
optimizations are outside this refactoring.

## 14. Code locations and review checks

| Code | Planned change |
| --- | --- |
| [OwnedRulesProvider.kt](../../core/opentaint-dataflow-core/opentaint-jvm-dataflow/src/main/kotlin/org/opentaint/dataflow/jvm/bidi/interp/OwnedRulesProvider.kt) | Own GROUP/FULL rule selection and the run-1 rewrite |
| [JIRRulePredicates.kt](../../core/opentaint-dataflow-core/opentaint-jvm-dataflow/src/main/kotlin/org/opentaint/dataflow/jvm/bidi/interp/JIRRulePredicates.kt) and [JIRMethodForms.kt](../../core/opentaint-dataflow-core/opentaint-jvm-dataflow/src/main/kotlin/org/opentaint/dataflow/jvm/bidi/interp/JIRMethodForms.kt) | Compile selected literals and keep mode-specific payloads |
| [FormsCache.kt](../../core/opentaint-dataflow-core/opentaint-dataflow/src/main/kotlin/org/opentaint/dataflow/bidi/interp/FormsCache.kt) | Make view selection explicit without changing reference management |
| [MethodEdges.kt](../../core/opentaint-dataflow-core/opentaint-dataflow/src/main/kotlin/org/opentaint/dataflow/bidi/store/MethodEdges.kt) and [RunMethodAnalyzer.kt](../../core/opentaint-dataflow-core/opentaint-dataflow/src/main/kotlin/org/opentaint/dataflow/bidi/engine/RunMethodAnalyzer.kt) | Restore AP-only admission/work and retain the ordinary publication barrier |
| [RunMethodCallServices.kt](../../core/opentaint-dataflow-core/opentaint-dataflow/src/main/kotlin/org/opentaint/dataflow/bidi/engine/RunMethodCallServices.kt) and [CalleeSubscriptions.kt](../../core/opentaint-dataflow-core/opentaint-dataflow/src/main/kotlin/org/opentaint/dataflow/bidi/engine/CalleeSubscriptions.kt) | Retain complete caller/stage input matching; remove formula replay and cofactor state |
| [Run1StaticService.kt](../../core/opentaint-dataflow-core/opentaint-dataflow/src/main/kotlin/org/opentaint/dataflow/bidi/engine/Run1StaticService.kt) and [Run1PointResume.kt](../../core/opentaint-dataflow-core/opentaint-dataflow/src/main/kotlin/org/opentaint/dataflow/bidi/engine/Run1PointResume.kt) | Join actual pool values with ordinary static summaries; resume at the saved operation phase |

Independent source reviews checked the provider/FULL handoff, the complete
static join protocol and the deletion/ownership map. They found no need
for a replacement validator under the clarified DEMAND contract.
Runtime code and tests were not changed for this proposal. No fresh
runtime, Lean or project validation is claimed.

## 15. Exact change map for the selected documents

This section specifies the required edits. It does not yet rewrite the
selected spec or its earlier implementation proposal.

There is a spec change. The written writer-eligibility contract requires
extra validation of a positive guard against actual input geometry.
The selected refactoring removes that extra validation and restores the
earlier bounded DEMAND contract. Section 4's field operations do not change.
The initial grammar, field limit, tails and definite kills do not change.

There is also a change to the required condition representation. Run-1
will use normalized GROUP conditions and existing source AP transfers. It will
not retain each full alias as a runtime leaf/proof alternative. Original
FULL rules and occurrence/action identities remain available. This
preserves projected truth; it does not preserve every run-1 proof record.

Most other edits remove implementation state or clarify its owner.
Complete ordinary premises still carry real dependencies. Deleting
`GroundingSupport` does not delete ordinary ND/call matching, the full
condition used by later runs, or proof witnesses in Lean.

### 15.1 Normative spec

The document is [grouped-run1-statics.md](../../spec/grouped-run1-statics.md).
Section numbers below refer to that document.

| Section | Required edit | Contract that stays |
| --- | --- | --- |
| Header and 1. Pinned baseline | Update the stale implementation status. Keep a80 as the primitive/field baseline; identify ff2966455 as the delivered runtime to refactor. Do not request another checkout reset. | Pinned ordinary contracts, exception scope and historical evidence |
| 2. Domain and proof scope | Remove runtime support from the edge definition. Define writer derivation in the bounded ordinary DEMAND relation, rather than through a separate exact-field validator. | Full reference-witness inclusion, rooted groups, accepted static order/context approximation, later FULL confirmation |
| 3. Ownership, forms and schedule | Specify provider-selected GROUP and FULL condition views and separate compiled cache selection. Preserve each imported leaf's registered owner; add no single-owner rejection. | Catalog, top-level join ownership, typed atoms, run schedule and layer rules |
| 4. Initials and field transfer | Retain the section. Clarify that no extra writer validator follows these operations. | All legal initial families, STAR-to-ANY entry, exact entry, point materialization, no static entry lane, selective writes and empty E |
| 5. Conditions | Replace run-1 full-alias leaf retention with exact signed folding, existing role/negative policy, then positive GROUP projection and normalization. Reuse the existing source AP transfer. | Original FULL conditions and IDs, distinct bound positions/check kinds, no cubes, unchanged action AP semantics |
| 5. END, cleaners and exclusions | Retain these rules. | Empty END in every run, selected cleaner requests/repair, RemoveAll, actual kills, typed exclusions and NORMAL complements |
| 6. Ordinary requests | Keep Base P/R. Make the ordinary-origin writer bootstrap explicit: C.f/g interest plus p/FLOW-to-C.f/FLOW offers Base(p)/g. | Commit-then-replace, both arrival orders, no static request rows, request matching ignores E/mx only for interest |
| 7. Storage | Remove grounded-input/support bits and support from continuation/writer keys. Keep references to ordinary early summaries for static joins and writer interests. | Existing owners, merged region/group sets, no duplicate Facts history or static relay graph |
| 8. Read admission | Remove `read.support` and use the selected GROUP mark-input view. | Write gating, both arrival orders, actual AP read images, class/field distinction, dormant reads and independent sources/control |
| 9. Early summaries and supply | Replace guard-formula/mask grounding with section 7–8's complete ordinary composition. Admit legitimate ZERO TAINT results and complete all-static pool applications; keep ordinary/mixed premises conditional. | Raw/live publication, queue-empty notifications, all-static FLOW copies, reached writes, source permission, no rootless group cycle or fake return |
| 10. Point/caller refinement | Remove support from subscription keys. Keep complete premises and phase-specific replies. | Actual FLOW composition, K6, legal group descriptor, operation-input rerun, ZERO retains C.f in run-1; actual C.f required later |
| 11. Handoff and run-2 | Select FULL forms by original sink occurrence. Do not decode GROUP ordinals as FULL ordinals. Replace any formula wording with complete premises and original FULL conditions. | Explicit control-ZERO exit, complete full catalog, implicit static demand, actual-query admission and real backward execution |
| 12. ZERO and sources | Retain behavior; make FULL view lookup explicit. | ZERO discovery, balanced replies, nonzero REACH distinction, exact all-source/action hits and finding confirmation |
| 13. Records | Remove support-state lifetime wording. Keep ordinary pending joins, point state and catalog alive through handoff. | NORMAL-only persistence, all speculative group/static effects DEMAND, typed readers, repair drain, no incomplete export |
| 14. Commits and completion | Replace grounding/support events with ordinary AP, request, summary, static join, writer-interest and point deltas. State finite semantic membership progress. | Ownership, counted sends, commit ordering, cancellation, dormant unanswered interests and allowed diagnostic count changes |
| 15. Removals | Add complete support-subsystem and full-alias dispatcher removal. | Ordinary N2, predicate and ND owners; prior obsolete static machinery stays removed |
| 16. Validation | Add the revised proof/adapter gates in section 16 below. Replace strict writer rejection with an explicit DEMAND candidate and later FULL refinement control. | Real-kill, ND, source identity, class/broad data, empty END and fixed project gates |
| 17. Baseline map | Record ordinary field behavior separately from the removed extra writer check; state the GROUP/FULL condition split. | Field primitives and later full operations retain the pinned contract; IR and reference manager stay unchanged |
| 18. Measurements | Keep the ff2966455 measurements as old runtime evidence. State that this proposed refactoring has no fresh project result. | Latest measured run-1 TIMEOUTs, finding-comparison caveats and the 60-second engine target |

The material precision difference is:

```text
actual supplier:        p.left/g
ordinary initial:      p.*/g
ordinary entry image:  p.[any]/g
positive check:        ContainsMark(p.right,g)

current extra validator: reject this writer candidate in run-1
selected contract:       ordinary analysis can emit C.out/g, DEMAND
later FULL analysis:     reevaluate the original condition and real path
```

This is not a new weak-write rule. Ordinary disjoint matching and definite
kills still apply where the AP representation distinguishes those cases.
A DEMAND candidate is not an exact field witness or a NORMAL record.
No request or merely analyzed initial becomes pool supply.

### 15.2 Implementation proposal

The document is [bidirectional-grouped-statics-implementation-20261019.md](bidirectional-grouped-statics-implementation-20261019.md).
Section numbers below refer to that document.

| Section | Required edit |
| --- | --- |
| 1. Base and conventions | Keep a80 as the inherited AP/field contract; use the delivered ff2966455 runtime as refactoring input. Replace the full-alias index overview with provider-selected conditions. |
| 2. Files | Delete `Run1Grounding.kt` and its store/protocol additions. Add provider view selection, transfer-aware GROUP compilation and view-specific cache payloads. |
| 3. Ownership | Keep provenance/census logic. Select GROUP/FULL condition views and reuse source AP transfers. Preserve imported owners and original candidate/action order. |
| 4. Atoms and forms | Keep typed atoms and action mark gates. Select GROUP/FULL condition payloads explicitly; both run-1 and run-3 are forward. Do not mutate shared FULL forms. |
| 5. Conditions and cleaners | Replace group-to-full-leaf dispatch and its instruction not to coalesce equal groups. Normalize GROUP NNF without changing source AP transfers. Keep exclusion algebra and cleaner semantics. |
| 6. Config and initials | Retain schedule, layer rules, initial grammar and entry/point distinction. |
| 7. Operations and resumes | Remove support fields from `OperationInput` and continuations. Retain exact operation/stage/cleaner addresses, current AP image, complete premise and ordinary call state. |
| 8. Static service | Delete support-growth batches, input-grounding directories, support probes and support-keyed images. Index ordinary early-summary references for all-static matching and ordinary FLOW writer interests. |
| 9. Reads | Remove support arguments. Keep write/read joins, actual AP images, direct class matching and source/control exceptions. Use selected GROUP condition descriptors. |
| 10. Writes/publications | Remove support-only updates. Keep complete premises, logical write classification and the ordinary raw/live AP-delta publication barrier. |
| 11. Grounded inputs/writers | Replace the section with ordinary caller composition, actual all-static matching, FLOW copies and Base request bootstrap. Keep the complete-call ND counterexample. |
| 11.1. Support representation | Delete the runtime formulas, fixed certificates, FLOW-shadow validator, cofactors, revisions, rebinding/replay and grounding counters. Retain old measurements/controls as historical evidence where needed. |
| 12. Empty END | Retain the entire policy. Source actions and sink discovery remain enabled. Correct its stale measurement wording: ff2966455 already used empty END; the new refactoring is unmeasured. |
| 13. Point composition | Delete support-key components. Retain AP composition, request-only mx treatment, K6, legal initials and rerun at the saved operation input. |
| 14. Base requests | Keep P/R and the static-origin boundary. Add the explicit ordinary-origin writer-interest join with both arrival orders. |
| 15. ZERO/static bridge | Keep run-1-only activation, static premises, actual supplied groups and complete same-call ND matching. Remove formula/mask dependencies. |
| 16. Handoff/records | Keep static demand, exact full source identity and NORMAL export. Lookup original FULL forms explicitly for backward seeds. |
| 17. Queues/completion | Remove support observers/events. Use counted writer-interest and static-candidate replies; drain ordinary work and semantic memberships at the same barriers. |
| 18. Removals | Add the deletion list in section 11 of this refactoring proposal. Keep ordinary caller inputs, predicate proofs and ND state. |
| 19. Sequence/tests | Replace grounding implementation stages with GROUP/FULL view, transfer-preservation, complete-premise pool and finite-progress stages. |
| 20. Evidence | Keep old measured acceptance failures bound to ff2966455. Add new results only after a new validation run. |

### 15.3 Compiler regression check for unchanged source transfer

Boolean condition equivalence alone does not preserve source transfer.
The baseline implementation has different unary and multi-input paths.
This source-derived example shows the risk; it is not a new executed test:

```text
entry current: p.[any]/g
condition:     P(p.f,T) AND P(p.f,U), owner(T) = owner(U) = g
action:        AssignMark at q, target tail ANY_TAINT

old multi-input path: targetTree -> q.[any-taint]/g, DEMAND
naive GROUP rewrite:  one leaf -> unary applyEdge -> q.[any]/g, DEMAND
required rewrite:     one GROUP leaf, original MULTI transfer retained
```

The current unary ancestor case can change the output tail. An EXACT
target does not demonstrate this difference; ANY_TAINT needs its own control.
Keep the original transfer kind and each unary transfer's bound source
Pattern before GROUP projection. Do not infer transfer kind from the new
leaf count. A mixed unary/multi source can retain both enabled semantic
transfer families under one original ActionId. Coalesce aliases within
equal transfer relations; do not retain their proof product.

This constraint prevents an unintended AP/action change. It adds no
runtime edge support. Its implementation needs the proof below.

## 16. Proof reuse, changed claims and new obligations

Yes, update [the proof dossier](../../spec/grouped-run1-statics-proofs.md)
and the relevant adapters before runtime implementation. Do not discard
the unchanged pure laws. Do not relabel the old audit as proof of the
new provider, publication protocol or work queue.

### 16.1 Reuse the pure results

| Existing model/results | Reuse and limit |
| --- | --- |
| `RuleGroupMarks`: `own_valid`, `valid_pair_eq_iff_full`, `exact_selection_covered` | Ownership and exact later selection stay unchanged. Recheck their provider binding. |
| `RuleGroupMarks`: cleaner partition, NORMAL complement and typed selector laws; `RuleGroupRequestRepair` | Keep group exclusion/repair and request meaning. Retain actual producer/consumer wiring and genuine kills. |
| `RuleGroupSchedule`: `limit_positive`, `full_from_run_two`, `no_strict_growth_first_boundary` | Schedule remains `[1,1,2,3,...]`. |
| `GroupedRun1Initials`, selected `Run1InitialShapes` laws | Legal grammar stays unchanged; old executable root families remain excluded. |
| `Run1StrongPreservation`: `definite_kill`, `exclusion_fallback`, `preservation_no_new_E`, `strong_write_survival_coverage` | Reuse field-transfer laws without adding writer validation to them. |
| `Run1StaticReadAdmission` and `Run1StaticInitialRefinement` arrival/packing/original/point laws | Reuse these local set and transport laws. Recheck ordinary AP matching and the new owner messages. |
| `Run1StaticGroupDerivation`: `solution_exact`, `solutionRoot`, `solution_empty_without_seed` | Keep finite positive closure and constructive root witnesses. Exactness is relative to the declared abstract program, not exact concrete field eligibility. |
| `Run1StaticEarlySummaryZero`, `Run1UniversalStaticCarry`, ordinary actual-query/source/record laws | Keep ZERO wrappers, shared static guidance, exact source enumeration and restricted NORMAL records. Recheck the changed form/premise adapters. |

`RuleGroupConditions.exact_truth_implies_possible_truth` also remains a
useful lemma. It evaluates the original formula under the group view.
It does not implement or prove the new provider normalizer, bind multiple
positions, or preserve source transfer modes by itself.

### 16.2 Add or revise these proofs

| Obligation | Required statement and control |
| --- | --- |
| Bound condition rewrite | Exact signed folds occur before positive projection. Projection uses each registered owner. GROUP normalization preserves projected truth and unconditional classification, with distinct positions/check kinds intact. Include same-group signed literals and legal imported/mixed owners. |
| Unchanged source actions | Prove that condition mark projection preserves each existing action's AP transfer, bound positions, original input Facts, output tail and complete GROUP premises. The source-family model is compatibility evidence, not a required runtime phase or API. |
| Occurrence/action and FULL views | Group alias removal retains original candidate/action IDs and every configured action. B2 selects the original FULL sink occurrence after eviction/rebuild, including F1/F3 forward cache separation. Group ordinals are never FULL seed ordinals. |
| Ordinary early-summary composition | Construct complete singleton/ND applications through real caller ports. Every unresolved original survives. Mixed premises cannot use an all-static shortcut or combine incompatible calls. An old input/new summary and new input/old summary both fire. |
| Pool supply and bootstrap | Every supplied group has a source-backed derivation in the bounded AP program. ZERO roots require legitimate source/control permission. All-static FLOW copies apply actual pool groups. Ordinary FLOW writers route interests through Base P/R. Requests, hypothetical initials and unseeded cycles supply nothing. |
| Complete F1-to-B2 coverage | Reconnect normalized GROUP source/sink cases and ordinary early effects to `GroupedRun1StaticComposition.compileCoverage`/`compileStaticCoverage` and exact FULL replay. Construct each required demand from a full reference witness; do not assume all guides exist. |
| Finite engine progress | Bound fresh AP admissions, request/summary memberships, static supplies and per-image deliveries. An unchanged-work burst has finite repeat keys; a new burst needs a fresh bounded event. Stable IDs cannot hide an unbounded revision/history. Count sends before the producer can complete; unanswered interests are dormant. |
| Scoped savings | Compare alias-sensitive evaluation with normalized cases under the same GROUP concept. With one accepted input per position, remove alias-choice products while retaining necessary spatial/ND choices. Prove only this scoped work reduction; measure whole-project time/memory. |

Do not claim equality between the old strict run-1 candidate set and the
restored DEMAND candidate set. Keep full reference-witness inclusion and
unchanged later finding confirmation as the semantic targets. Within the
selected GROUP concept, condition normalization and merged joins must
preserve their decoded semantic answer relation.

### 16.3 Retire the extra validator claim, not field soundness

Remove the requirement that a separate run-1 writer validator reject
`p.left/g -> check p.right/g` after ordinary entry widening. Retire its
FLOW-shadow/fixed-certificate and support-revision adapter assertions.
Keep the case as an explicit DEMAND candidate test plus later FULL
refinement. Retain definite-kill, disjoint-transfer and exact AP-composition
controls. A bounded FULL run need not reject every approximate candidate
immediately.

The referenced finite models do not define `GroundingSupport`, `WithFixed`
or its cofactor/work protocol. `Run1StaticGroupDerivation.solve_firing_bound`
and composition's `group_closure_firing_bound` bound the finite rule solver.
They do not prove termination of the delivered method actor. The new engine
progress proof must cover ordinary queue admission and unchanged bursts.
Keep the support-growth countermodel as a historical regression witness;
convert the runtime loop diagnostics to bounded-quiescence regressions.

### 16.4 Update the dossier and its evidence

| Dossier section | Required edit |
| --- | --- |
| 1–2. Claim/concept | Define rooted derivation in the bounded ordinary AP program. State projected condition equivalence, reference inclusion and the allowed run-1 precision change separately. |
| 3. Requirement map | Add transfer-aware provider normalization, explicit FULL decoding, ordinary pool adapters and engine progress. Keep the reusable pure results and their limited scopes. |
| 4–5. Coverage/ZERO argument | Reconnect full witness projection to normalized GROUP conditions and existing source actions, complete early-summary joins and the real backward pipeline. Preserve source/control and static-premise distinctions. |
| 6. NORMAL | Keep its claim. Recheck that every newly admitted group/static candidate is DEMAND and cannot enter normal persistence. |
| 7. Savings | Remove support identity from the runtime size model. Add a semantic-membership bound and the scoped alias-work comparison. |
| 8. Evidence map | Distinguish reusable laws, revised adapters and retired strict-validator controls. A Lean ghost witness is not a required runtime support object. |
| 9. Validation | Build/audit the changed models and retain a new source-bound receipt with constructive executable witnesses and no classical choice or `sorry`. Old totals stay bound to old sources. |
| 10. Measurements | Correct the stale checkpoint using the current report. All four latest measured projects reached run-1, completed none and accepted none; all timed out. No result measures this proposed refactoring. |

This analysis changes documentation only. It does not execute the new
proof obligations, run tests, or change the analyzer. Spec/proof alignment
is required work before the runtime refactoring.
