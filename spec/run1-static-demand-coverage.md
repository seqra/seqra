# Static group-demand coverage from run-1 to run-2

> Superseded for implementation by [Mark groups and direct static handling](grouped-run1-statics.md),
> based on pinned analyzer `a80d5c580`. This file keeps historical design and
> local proof evidence. It does not add runtime requirements to that spec.

Date: 18 October 2026. Status: **proof review and proposed correction;
analyzer unchanged**.
Language: ASD-STE100 Simplified Technical English. Defined technical terms,
code names and mathematical notation keep their stated meanings.

This review follows [proof-guide.md](proof-guide.md). It covers the handoff
in [the static proposal](run1-group-static-pool.md). It separates an actual
missing-demand counterexample from the correction and its checked scope.
The old writer-search proof starts with a read query. It does not prove
that run-1 creates every group demand needed to obtain that query.

The [immediate-summary review](run1-immediate-static-summaries.md) makes one
scope correction: a sink-origin backward path stays under initial ZERO
while it returns through wrappers. The existing ZERO publication exception
needs no run-1 wrapper carry demand. Section 5's carry construction is a
sufficient solution for **nonzero** static-query admission; it is not a
required addition for that ZERO-origin wrapper case.

The selected [shared all-method static demand](run1-universal-static-carry.md)
also removes the need for a source-derived, per-field carry guide at a
nonzero static relay. A broad GROUP entry/exit permission retains the actual
full query. Productive reader/writer and source demands remain separate.

Measured runtime remains `63d5481e4e4b69ecf042c1149bb574ad40fd1624`.
Latest reached / completed engine / accepted forward report: OWASP
**run-7 / run-6 / run-5**; conductor **run-1 / none / none**;
ThingsBoard **run-1 / none / none**; Stirling-PDF **run-2 / run-1 / run-1**.
No project run or runtime change is part of this review.

## 1. The mark gate

Run-2 uses full marks at field limit 1. A run-1 demand with group G admits
an eligible full mark T when owner(T) = G. Run-1 does not need to emit T.
A FLOW demand alone does not authorize a full-mark initial. ZERO and a
writer-inventory match do not supply the missing group demand either.
This statement concerns a nonzero marked initial. It does not remove the
ordinary sink seed under ZERO or restrict its ZERO-premise balanced return.
The resulting requirement keeps T; it does not create a marked ZERO initial.

```kotlin
fun markDemandMatches(demand: GroupId, mark: ExactMark): Boolean =
    demand == mark.group

fun emitBackward(query: FullQuery, guides: FrozenGroupGuides) {
    val guide = guides.match(query.method, query.position, query.mark.group)
        ?: return
    emitFullQuery(query, guide) // preserve the actual full mark
}
```

The group selector must apply at the method/port and geometry where the
emission occurs. A G demand in one method is not a G demand in every method.
Publication must also retain the required paired exit group/geometry.
For a rewriter, the entry and exit groups can differ.

An unpruned spatial search is not an exception to this mark gate. Every
marked initial still needs an explicit group demand. Queries are search
obligations, not facts that establish actual taint or a source hit.

## 2. The missing demands in the previous proposal

```text
static_set:     t = src(); C.f = t
static_consume: t = static_get(); sink(t)
static_get:     return C.f
```

Run-1 can have all of these:

```text
source-derived C.f/G supply
getter conditional summary: C.f/FLOW -> return/FLOW
caller point refinement:    C.f/G -> t/G at the sink
sink group demand:          G
```

But the getter still has no G summary. Reversing its FLOW summary produces
only FLOW guidance. The backward `return/T` input therefore lacks a mark
demand in the getter. Geometry normalization does not change this selector.
The missing paired group demand is:

```text
method = static_get
entry  = return/ANY/Group(G)
exit   = C.f/STAR/Group(G)
```

There is a second gap if the getter uses an ordinary helper:

```text
static_get: return id(C.f)
id(p):      return p
```

Decorating only the getter is insufficient. The helper also needs:

```text
method = id
entry  = return/ANY/Group(G)
exit   = p/STAR/Group(G)
```

The [counterexample model](lean/ApSpec/Run1StaticDemandGateGap.lean)
proves these local admission failures under selector-preserving handoff.
It does not assert an end-to-end missed runtime report. It also proves
that the group demand accepts an unobserved full member with the same
owner. No exact full-mark demand in run-1 is required.

## 3. Required handoff artifacts

The guide snapshot must include the following typed information:

| Artifact | Purpose |
| --- | --- |
| Group-decorated conditional summary pair | Admit a marked backward callee input and publish its corresponding result |
| Actual reached call-input binding | Transfer an original-slot group to the correct callee parameter/receiver slot |
| Internal point group demand | Start a query at a check/source/cleaner/writer stage, including a method with no normal exit |
| Static carry group demand | Permit an actual field query across a transparent call without a marked S-root initial |
| Writer point/transfer group demand | Admit a writer query and its RHS/guard continuation after an inventory opening |
| Exact bound occurrence and support | Preserve source/sink conditions, actual method contexts and ND obligations |

An internal point demand is not an entry-to-exit summary. An inventory
opening is not its mark demand. The handoff must retain both when needed.
A normal summary needs a reached normal exit. An internal point demand does
not. Do not require a fictitious normal return to seed an internal query.

## 4. Forward generation of group guides

Point refinement does not replay the grouped prefix. Generate its missing
guide annotations from the conditional interfaces instead. This is a
forward group-set closure. It has no local predecessor walk and emits no
run-1 full-mark taint facts.

```kotlin
data class OriginalSlot(val method: MethodKey, val initial: CanonicalSlot)

data class CallInputUse(
    val original: OriginalSlot,
    val callee: OriginalSlot,
    val binding: ReachedArgumentBinding,
)

data class GuideRow(
    val original: OriginalSlot,
    val current: PointOrOutput,
    val relation: CertifiedFlowRelation,
    val support: SupportRef,
)

class GroupGuideBuilder {
    val masks: Map<OriginalSlot, GroupSet>
    val callInputs: Map<OriginalSlot, List<CallInputUse>>
    val rows: Map<OriginalSlot, List<GuideRow>>
    val work: FreshSlotGroupQueue
    val guides: GroupGuideStore
}

fun offer(slot: OriginalSlot, groups: GroupSet) {
    val fresh = masks[slot].unionAndGetGrowth(groups)
    if (!fresh.isEmpty()) work.enqueue(slot, fresh)
}

fun drain(slot: OriginalSlot, fresh: GroupSet) {
    for (row in rows[slot])
        guides.addGroupInstance(row, fresh) // guide only, always DEMAND
    for (use in callInputs[slot])
        offer(use.callee, use.binding.forwardGroups(fresh))
}
```

For the checked identity scope, `forwardGroups` preserves the group.
Static-original slots obtain groups from compatible source-derived field
supply. Ordinary slots obtain actual caller/input groups or groups carried
by a reached forward call-input binding. Source/boundary seeds retain their
normal root evidence. A conditional initial is not an independent seed.

Retain the call-input rows before dropping ordinary call/subscription state.
They include the actual reached method key, stage, input geometry and real
argument/formal projection. A callee exit summary alone does not contain
that mapping. Merge by canonical slot and group, not by full mark or remote
source. Annotate summary/point rows, not each preceding local statement.

Join old rows with new masks and old masks with new rows. Drain this work
before freezing the run-1 handoff. The final snapshot is immutable. Run-2
matches full marks against these group selectors; it does not need to
create a new full-mark permission from a FLOW demand.

The general concept uses a positive rule program at these interface cuts.
A rule records its full ordered premise list and one group conclusion.
Sources and actual supplied inputs are explicit seeds. Compute its least
forward solution, then freeze group demands for every supported interface:

```kotlin
val known = forwardGroupClosure(interfaceRules, rootedInputs)
for ((ruleId, rule) in interfaceRules.withIndex()) {
    if (rule.premises.isEmpty()) continue // real sources are rootedInputs
    if (!rule.hasAllPremisesIn(known)) continue
    for ((premiseId, premise) in rule.premises.withIndex())
        guides.addPair(ruleId, premiseId,
                       entry = rule.conclusion.groupPattern,
                       exit = premise.groupPattern,
                       wholeSupport = rule.premises)
}
for (target in observationTargets)
    for (group in known.groupsAt(target))
        guides.addPoint(target, group)
```

The point demand supplies the actual backward start. The paired demands
supply intermediate admission and publication. Both are required, including
a source-to-check path with no intermediate rule. Group guide annotations
are not new roots or NORMAL taint facts.

All mark-changing actions keep their declared group relation. A uniform
G-to-G copy cannot stand for a g-to-h rewrite. Multi-input guard/ND rules
keep all their antecedents; a group on one argument cannot satisfy another.
The existing positive group calculus can express those rules, but compiling
all real operations to this interface needs a separate adapter proof.

## 5. Static carries, writer openings and internal starts

### 5.1. The real read-to-write path

The shared field pool is not a control-flow connection from a read to a
write. The required run-2 path uses the ordinary CFG and real call ports:

```text
static_get:     read C.f -> method entry
call port:     static_get entry -> static_consume before static_get()
static_consume: before static_get() -> method entry
call port:     static_consume entry -> main before static_consume()
main:          move backward to the preceding static_set() call
call port:     main after static_set() -> static_set normal exit
static_set:    normal exit -> C.f = t -> t -> actual source
```

Retain the read origin and full mark T through this path. Returning to a
caller resumes at that call's **before** stage. Entering a preceding callee
starts at its actual exit. A method pair in the call graph does not identify
these stages, the target context, or the normal/exceptional continuation.

```kotlin
data class CallPort(
    val callerBefore: ProgramPoint,
    val callerAfter: ProgramPoint,
    val target: MethodKey, // includes the analysis context
    val calleeEntry: ProgramPoint,
    val calleeExit: ProgramPoint,
    val exitKind: ExitKind,
)

val ports = prescan.freezeActualCallPorts()
// Keep ports even when run-1 had no C.f input/subscription at the call.
// Ordinary control/exit eligibility still applies to a run-2 traversal.
```

At a callee entry, the internal query result uses eligible incoming ports.
It need not wait for a marked run-1 caller subscription that this optimization
removed. A writer-inventory opening may find more candidates, but it cannot
replace this path in the coverage proof.

### 5.2. Frozen carry demands for the omitted static relays

Run-1 does not propagate C.f through every method on this path. Thus their
guides cannot depend on observing a C.f FLOW initial in those methods.
Build a factored immutable carry relation instead:

```kotlin
data class FrozenCarryGuides(
    val methods: FrozenSet<MethodKey>,
    val supplied: FrozenFieldGroupRelation,
)

val carry = FrozenCarryGuides(
    methods = prescan.closedControlMethodUniverse(),
    supplied = staticPool.freezeGroundedFieldGroups(),
)

fun FrozenCarryGuides.match(q: FullStaticQuery): DemandPair? {
    if (q.method !in methods) return null
    if (!supplied.covers(q.fieldScope, q.mark.group)) return null
    return demandPair(
        entry = q.fieldScope.withTail(ANY).withGroup(q.mark.group),
        exit = q.fieldScope.withTail(STAR).withGroup(q.mark.group),
    )
}
```

`methods` contains the whole closed, control-eligible method universe, with
all real port endpoints and contexts. Do not build it only from
`result.analyzers`, nonzero initials, methods with reads, or methods with
normal exits. Method metadata has no mark group. Only the source-rooted
field/group relation supplies G. A FLOW write or synthetic S identity does
not supply G.

The returned pair is a view of an already frozen DEMAND relation. Run-2 does
not add a new demand. Store methods and field/group supply separately;
do not materialize their product. There is no marked S-root initial.
ANY in the demand entry is a match pattern, not a run-1 initial.

This carry demand admits full C.f/T and permits a C.f/T paired result.
It **does not apply an identity summary** or skip a method body. Run-2 must
still execute its real reverse transfers. A definite overwrite can pivot
to its RHS or kill the old value. An actual RemoveAll kill cannot be crossed
using the carry permission. A nonreturning callee has no normal-exit entry
step, even though its method metadata and internal point guides exist.

### 5.3. A writer needs a different paired demand

The static identity pair is insufficient at a write:

```text
set(p): C.f = p

carry guide:  C.f/ANY/G -> C.f/STAR/G
needed pair:  C.f/ANY/G -> p/STAR/G
```

The first pair can admit C.f/T. Its output pattern cannot publish p/T.
Capture the reached forward writer row before intercepting its static
output. For an identity writer, instantiate its guide from the forward
original-slot mask:

```kotlin
for (writer in capturedWriterRows) {
    for (group in masks[writer.original]) {
        guides.addWriterPair(
            method = writer.method,
            entry = backwardInputPattern(writer.written, group),
            exit = backwardOutputPattern(writer.original, group),
            writerStage = writer.inputStage,
            support = writer.support,
        )
        guides.addWriterInputPoint(writer, group)
    }
}
```

This query-only boundary pair is required even when run-1 intercepts the
write before an ordinary method-exit summary exists. It does not assert that
the write reaches that exit. The real run-2 CFG checks that fact. Local RHS
transfer and helpers use the captured conditional row and the forward
input-mask closure in section 4. A real finite writer witness supplies its
rooted original-slot trace; a hypothetical p/G request does not.
The pattern builders keep the canonical EXACT/STAR original tag and the
ordinary paired-demand AP encoding. They do not make an ANY initial.

For a source-generated write, retain the actual source point, full source
identity and ordinary source permission. Reversing that source produces
**unmarked control ZERO**. Do not construct `ZERO/Group(G)` or use group
permission as proof of an actual source hit. A group-changing writer keeps
its actual input/output group relation and guard slots.

### 5.4. Internal starts and candidate discovery

Record internal point demands at their actual operation inputs. They can
start backward queries independently of method-exit reachability. Preserve
the full catalog expansion, condition and support of each bound occurrence.
A seed wrapper must start at that point; an exit-only ZERO traversal is not
sufficient for a nonreturning method.
Register observation targets when ordinary control reaches the bound rule
stage. Do not collect targets only from existing report hits or normal
return summaries. At freeze, a supported group at such a target supplies
its point query permission. Writer-point permissions are used by actual
read openings; they do not seed every writer automatically. Query permission
alone is not a positive taint observation or a confirmed source action.

Keep the complete writer inventory and original-read dependency on every
opening. A compatible writer opening must have an explicit group-demand
path for its writer output, reverse RHS/guards and any called helpers.
Opening the inventory entry alone does not discharge this obligation.
Do not declare the whole writer discovery search complete if its mark gate
can reject a required intermediate query.

Complete inventory enumeration and marked witness coverage are different
claims. A false or unsupported inventory candidate need not have a rooted
forward guide. The required theorem covers every real finite witness whose
primitives compile into the guide program. It does not assert that every
speculative inventory candidate executes in run-2.

The identity-interface model and the actual-port corridor model cover
different parts of this result. The first covers productive conditional
interfaces; the second covers static relays omitted from run-1. The positive
full-to-group model covers group changes and guards. Complete production
collection of these interfaces and real reverse primitives remains required.
Leaving a scope unpruned is not a substitute for explicit group demands.

## 6. Proof target and checked boundary

The end-to-end target is:

```text
for every finite real full-mark static witness in the selected AP scope,
  run-1 produces a group-demand seed and all required intermediate guides;
  each full query on its reversed witness matches an explicit group demand;
  its paired publications and writer/source queries remain available.
```

A group seed at the sink is one part of this target, not its whole proof.
The proof must compose source/write/read admission, conditional transport,
forward group-guide generation, full-member decoding, ordinary backward
steps and complete writer discovery. It must retain point starts and paired
publication, not just admit the first method input.

The constructive models separate seven claims:

| Model | Claim |
| --- | --- |
| [Demand-gate counterexample](lean/ApSpec/Run1StaticDemandGateGap.lean) | The previous sink-only refinement can leave getter/helper inputs without group guidance; group G admits any full member owned by G |
| [Forward interface coverage](lean/ApSpec/Run1StaticDemandCoverage.lean) | Forward mask closure equals eager same-group interface propagation; each visited nested helper has a frozen paired/point/writer guide |
| [Full positive-witness coverage](lean/ApSpec/Run1StaticDemandTreeCoverage.lean) | A full finite positive witness projects to group derivations and frozen guide witnesses for all its rule premises, with different groups and full guard lists |
| [Real read-to-write corridor](lean/ApSpec/Run1StaticReadWritePath.lean) | Frozen field/group supply and closed method/port metadata produce carry guides at every actual backward port; a captured writer row supplies its distinct paired RHS permission |
| [Existing ZERO wrapper path](lean/ApSpec/Run1StaticEarlySummaryZero.lean) | A real sink-origin ZERO summary returns through reached zero-only subscriptions and surviving wrapper-body transfers without run-1 wrapper carry demands; early writes reverse to explicit ZERO exits |
| [Strict-gate counterexample](lean/ApSpec/Run1StaticEarlySummaryGap.lean) | A resolved static relay without a group guide disproves universal strict-gate coverage; actual-query admission avoids this loss |
| [Shared static GROUP default](lean/ApSpec/Run1UniversalStaticCarry.lean) | One implicit all-method catalog relation admits and publishes finite actual static identity/copy paths without per-field guide rows; the prior counterexample succeeds |

The production compiler must preserve each real primitive, AP endpoint,
reached call/return port, context, rule occurrence and guard slot. The full
rule catalog and its indices must remain available after group projection;
matching group identities do not identify full source/sink occurrences.
This is a local semantic compilation obligation, not an assumption that
all desired demand edges already exist.

The semantic proof distinguishes source evidence, positive group closure
and query permission. It does not implement run-2 or prove that the current
Kotlin snapshot builder satisfies the new rule. Actual AP demand encoding,
point starts, actor completeness, reverse primitive soundness and paired
publication need their production adapter checks. Report a finite semantic
coverage theorem, not a complete checked JVM implementation.


## 7. Constructive proof and validation

`projectTree` maps a finite full-mark positive witness to the group program.
The map retains nodes and every ordered premise slot. `buildFullSkeleton`
uses the original full-rule catalog indices, so two different full rules
with equal group projections do not acquire each other's occurrence ID.
`collected_guides_frozen` proves membership of every collected rule/premise
pair in the frozen group ledger. This includes all guard branches, not just
one path to a root.

`fullTreePointWitness` constructs the designated target group permission.
It also handles a target that is already a source seed, with no operation
between source and check. `fullPairWitness` matches the exact full endpoint
identities against the two group selectors. `fullLocalReversePublication`
retains the paired permission and requires the actual reverse primitive's
permission separately. A group guide never invents a full-mark preimage.

Thus the revised concept has this checked finite-semantic result:

```text
finite FULL witness + designated target
  -> target GROUP demand in the frozen ledger
  -> frozen GROUP pair for every rule/premise of that witness
  -> matching admission/publication for its actual full endpoint marks
     whenever the ordinary reverse primitive permits that step
```

This proves group-gate coverage without assuming that the required guides
already exist. It does not prove the production interface compiler or the
JVM reverse interpreter. The selected full witness must compile locally to
the same positive program, with true source eligibility, AP coverage,
actual ports and bound condition identities. Point-demand activation and
paired demand-store encoding remain explicit implementation checks.

The forward interface model separately proves equivalence with eager
same-group propagation and coverage of every visited nested helper.
No full-mark taint-body replay or backward provider graph is part of that
model. The general model adds group changes and multi-input guards.

### 7.1. The omitted read-to-write corridor

The corridor model fills a different gap. The positive witness alone did
not show how an omitted static carrier obtains a guide at each actual
caller/callee boundary. The construction is:

1. Build the default-method set from the closed primitive method/call-port
   inventory. This step uses no taint initials or group demands.
2. Take the frozen C.f/G supply from the rooted forward group computation.
   Positive full-to-group projection covers that supply for a compiled
   finite full witness. A request alone cannot provide it.
3. For each local pass or real call-port step of the ordinary reverse path,
   derive method membership from the inventory. Join that membership with
   the same C.f/G supply to construct the matching carry pair. It admits
   full T and its static paired output without changing T or the read origin.
4. At a hit store, stop the carry path. Execute the actual reverse store,
   then use the distinct captured writer guide to retain its RHS result.
   An identity carry pair cannot publish an ordinary RHS.

Induction on the finite reverse path constructs one guide witness per
visited port. It retains the exact list of local/call moves. It introduces
no read-to-writer jump, entry replay or marked S-root. Any finite number
of intermediate pass-only methods is covered without a marked run-1 input
in those methods.

The path starts at the original read and ends at the actual store point.
For an ordinary-origin writer, the writer pair is derived from its captured
FLOW row and actual rooted forward input-interface trace. Its method,
point and written field must match the store. Reversing `C.f = t` produces
the current RHS t/T. A writer pair `C.f/G -> p/G` permits the eventual
original-slot p/T result; it does not turn t into p. The ordinary reverse
RHS path still needs its own real transfers and helper guides. The proof
does not assume that this writer group pair is already present.
A source-generated writer additionally
uses the source/RHS point and helper-call guides from the positive witness
model. The local `reverseSource` law only checks an actual exact source hit;
it does not itself prove the whole RHS-to-source CFG/call path.

The corridor is a sequence of registered primitive moves. Real AP overlap,
operation-stage eligibility and well-bracketed call matching still come
from the ordinary CFG/call adapter. Coverage of all such real paths does
not assert that every syntactically composable inventory path is feasible.
The carry relation authorizes analysis only; it cannot bypass a kill or
establish a normal-return/source result.

Validation: Lean 4.33.1 builds **157 jobs**. The combined audit checks
**4,201 declarations**, admits no unexpected axioms, and passes **299
controls**. Of these, the seven new demand modules contribute **2,817
declarations and 186 controls**. The audit accepts only standard `propext`
and `Quot.sound`; it rejects classical choice and unproved placeholders.

```sh
cd spec/lean
lake build
lake env lean run1-static-demand-coverage-audit.lean
```

The [receipt](/drive-testcomp/opentaint-go-rules/run1-universal-static-carry-20261018/validation/20261018T235053.989525Z/receipt.json) has SHA-256
`04545b213180c2c9556a42567923ba24775c6582d61c3a3e0548cd6a567e5403`. It binds stable proof-source hashes to the
[build log](/drive-testcomp/opentaint-go-rules/run1-universal-static-carry-20261018/validation/20261018T235053.989525Z/build.log) and
[audit log](/drive-testcomp/opentaint-go-rules/run1-universal-static-carry-20261018/validation/20261018T235053.989525Z/audit.log).
No runtime implementation or project benchmark changed.
