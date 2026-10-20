# Thingsboard: why the static writer store is large

Date: 20 October 2026. Runtime:
`029ee41fd6de17388c2f61d0f900cd00f6bd33f8`.
Latest reached iteration: **run-1, forward, limit 1, OOM**.
Run-1 engine time: 396.256 s. No iteration completed or was accepted.
This investigation reads the existing live heap capture. It does not run
the analyzer again or measure a new optimization.

## Finding

The main retained writer population is a **static-to-static FLOW relation
matrix, repeated in many method owners**. It is not a large population of
concrete groups or distinct static fields.

Of 3,660,632 actor writer keys, 3,057,187 describe FLOW relations between
`EntityType` enum fields. They contain all `36 × 36 = 1,296` field pairs,
across 7,475 method owners. This is **83.5% of all actor writer keys**.

The runtime registers these conditional summaries as global pool
dependencies. A new supplied group can evaluate them even when nobody has
requested that output field/group. Callers also re-export static summaries
and register their own copies. Removing static current facts from the CFG
did not remove this interprocedural summary work.

## Evidence and scope

The existing capture is:

```text
immutable-mark-groups-20261019/projects/thingsboard/bidi/guard-live.hprof
SHA256: 02f000dfdeb8d224efbc839093b14b4a69ce8a8eaa13c155d1188e1147129530
phase: run-1; live=true; before cancellation/release
```

The two new saved-index query jobs used Java 21, MAT, a 24 GiB heap, and the
three existing workload locks. Both readers closed. Capture and data-index
stats stayed unchanged. Only MAT's plain operational log was appended.

Evidence root:
`/drive-testcomp/opentaint-go-rules/immutable-mark-groups-20261019/validation/`.

| Evidence | Location below the evidence root |
| --- | --- |
| Owner counts, premise vocabulary, accessor dictionaries, delivery-map census | `writer-census-20261020/job-102004907206/` |
| Complete FLOW and GROUP writer-key CSVs, corrected method dictionary | `writer-details-20261020/job-102315807275/` |
| Key counts and joins | `writer-details-20261020/job-102315807275/aggregates.json` |
| Enum matrix counts | `writer-details-20261020/job-102315807275/enum-patterns.json` |
| Owner contexts and read-registration split | `enum-writer-origin-20261020/job-162334622243/owner-breakdown.json` |
| Enum constant modifiers and assignment bytecode | `enum-writer-origin-20261020/bytecode-receipt.json` |
| Argument-0 alias write for the ResourceExportService example | `enum-alias-details-20261020/job-162741478004/review/E05.csv` |
| Original operation/slot target fanout | `enum-alias-fanout-20261020/job-162937070320/alias-fanout.json` |

The follow-up metadata readers also closed with unchanged capture/data-index
stats. `enum-writer-origin-20261020/analysis-receipt.json` binds their outputs.
Bytecode inspection reads the saved project class and the inspected Java 21
`Enum` class. It does not execute class initialization or start the analyzer.

Each job has a terminal receipt and query-outcome inventory. The first job's
W01/W02 queries failed because MAT could not index Kotlin's primitive-array
list wrapper as a Java collection. The corrected queries read its backing
`int[]`. Those failed results are preserved and are not counted as evidence.
The first CSV inventory also needed a larger cell limit for the accessor
dictionary; it was read again from the closed archive without a heap reader.

The complete W01/W02 exports total exactly 3,660,632 rows. Their owner join
matches the sum of all actor `writerKeys.size` values. All writer owners and
premise IDs join successfully. The accessor table maps the integer field
keys to the names below. Method names are used for selected examples; this
report does not claim that every method object has the same IR class.

## What is retained

| Actor writer-key category | Count |
| --- | ---: |
| Static premise, FLOW output | 3,513,054 |
| Nonstatic premise, FLOW output | 11,660 |
| Static premise, GROUP output | 131,774 |
| Nonstatic premise, GROUP output | 4,144 |
| Total | 3,660,632 |

All FLOW writer premises have the abstract mark. All outputs are DEMAND.
FLOW accounts for 96.3% of the keys. GROUP accounts for 3.7%.

There are only 107 distinct output field keys in this population. The keys
occur in 12,065 method owners. They form 184,609 owner/premise pairs and
348,989 owner/output-field pairs. If only the owner is removed from the
exported descriptor, 6,685 distinct descriptors remain. This is a count of
repeated descriptor content, **not** a proof that method-specific demand
guidance can be deleted.

The current runtime has two writer paths. Their names can hide the scale:

```kotlin
when {
    allPremiseMembersAreStatic -> register(StaticWriterRegistration(...))
    ordinaryFlowPremise && flowOutput -> register(StaticFlowWriter(...))
}
```

The reported `flowWriterRows=10,158` counts only the second path. It does
not count the millions of FLOW outputs in the first path.

At capture, the global service contains 3,010,244 `WriterRow` objects.
The whole heap contains 3,644,828 `StaticWriterRegistration` objects,
which equals the actor's all-static key population. The remaining 634,584
registrations have not become service rows at this snapshot. Do not label
that difference as unregistered nonstatic group writes.

The earlier heap report measured 4,213,559,264 retained bytes for the union
of the static service and point-resume directories. This includes reads,
requests, call continuations, and writer state. It is not writer-only memory.
The service-only retained size overlaps that union; do not add them.

## The dominant fact pattern

Let `E` mean `org.thingsboard.server.common.data.EntityType`.

```text
initial I_i:  S.E.e_i.* / FLOW
result:       S.E.e_j.[any] / FLOW, DEMAND
stored edge:  I_i -> result
```

`[any]` is the current/output tail. The initial remains `E.e_i.*`; the census
does not show an ANY initial.

There are 3,057,187 FLOW keys with both ends in `EntityType`. All have the
ANY output tail. Only 86,222 have the same input and output field. Thus,
deleting same-field identities alone cannot remove the matrix.

Examples of output-field key counts, including all output mark kinds:

| Field | Writer keys |
| --- | ---: |
| `EntityType.TENANT` | 88,627 |
| `EntityType.DEVICE` | 88,611 |
| `EntityType.DEVICE_PROFILE` | 88,610 |
| `EntityType.ASSET` | 88,589 |
| `EntityType.CUSTOMER` | 88,584 |

Some individual method owners retain the complete 1,296-pair enum matrix.
Examples include `AccessValidator.validateEntityView`,
`TbKafkaProducerTemplate.send`, and
`TbGetCustomerDetailsNode.getContactBasedFuture`. The last owner has exactly
1,296 writer keys: its whole writer directory is this enum matrix.

## Why there are 7,475 owners

The number is an analysis-owner count. An owner has a `MethodEntryPoint`
key, which includes the method context and entry statement. It is not a
count of Java methods that assign enum constants.

The follow-up E01/E02/E06 census splits these owners as follows:

| Method kind | Physical method objects | Analysis owners |
| --- | ---: | ---: |
| Bytecode methods | 4,408 | 6,509 |
| Generated lambda methods | 228 | 228 |
| Modeled library methods | 28 | 738 |
| Total | 4,664 | 7,475 |

There is one logical entry index per method in this population. Different
physical entry-statement objects can have that same index after a graph
rebuild. They do not establish extra logical entry points. The additional
owners have different method contexts: 4,185 owners use the empty context,
2,106 use argument-type contexts, 254 use receiver-type contexts, and 930
use combined contexts.

For example, one modeled `Stream.map` method has 266 owners and one modeled
`Stream.filter` has 190. An `EntityActionService.pushEntityActionToRuleEngine`
method has 87. These are repeated analyses of common methods under type
contexts, rather than hundreds of different source bodies.

The call path spreads the relations into these owners. A reached ZERO call
can admit a callee static FLOW premise on run-1. If the result is static,
the caller immediately re-exports it as its own static summary and registers
another writer. There is no requirement that the caller itself read or
assign that enum field.

This is visible in the complete read-registration census: **4,525 of the
7,475 owners have no reached local static-read/field-MarkInput registration
for any static field**. Only 2,950 have such a registration. The absence is
about reached runtime registrations, not a claim that their source text has
no static access. Caller re-exports and alias effects on callee results can
still produce their static relations.

Thus, there are two different multipliers: the relations reach many caller
bodies, and the same bodies can have several type contexts. Reducing context
count alone would leave thousands of affected methods. Filtering only local
static readers would also discard valid effects of real callees. The storage
design must remove repeated global writer work while it retains the ordinary
method summaries needed for call semantics and run-2 guidance.

## Is EntityType writable?

The compiled application class declares all 36 enum constants as
`public static final EntityType`. Its assignments to those references are in
`<clinit>`. The application instance fields `protoNumber`, `tableName`, and
`normalName` are final and are assigned in constructors.

These are different questions:

```java
EntityType.TENANT = anotherValue;  // reference assignment: not a normal legal write

// Separate example: a final reference can still point to mutable contents.
static final Box BOX = new Box();
BOX.value = v;
```

The matrix output has an ANY tail below the static field. It describes a
conditional relation to possible contents of the referenced object. It is
not evidence that 7,475 methods execute a `putstatic EntityType.TENANT`.

Also, `final` alone is not a deep-immutability test. A final static reference
can point to a mutable object or array. The inspected Java 21 `Enum` base
class even has a mutable integer hash cache written by `hashCode`. That fact
does not establish that the observed alias sites mutate the enum, or justify
all-to-all transfer between distinct constants. It prevents a blanket claim
that every enum instance has no mutable storage. Any read-only value policy
must state the supported methods and model actions explicitly.

The actual problem here is that **static writer registrations include alias
copy-back effects, including preservation effects**. The presence of a
`StaticOperation.Write` descriptor does not prove a changed Java field value.
The following section shows why this matters for read-only calls.

## How the pipeline creates and repeats these rows

The key is not a source-code assignment ID:

```kotlin
// Run1PointResume.summary
WriterKey(completePremise, outputLeafPattern, layer)
```

There is one directory in each method owner. It retains a key for every
fresh nonzero static summary leaf. The ordinary summary store deduplicates
equal inserts first, but a different premise, output field, tail, or owner
is a different key.

Static alias effects are also write producers. The call-plan builder emits
an edge to each persisted alias of a bind-back local and attaches a
`StaticOperation.Write` for a static alias:

```kotlin
for (alias in persistedAliases(local, call)) {
    val transfer = starEdge(boundPosition, alias.base, alias.path)
    emit(transfer)
    if (alias.base == STATIC) emit(StaticOperation.Write(transfer))
}
```

The alias stage has this existing guard:

```kotlin
MemoryEffect.admits(identityCandidate, layer) =
    layer == DEMAND || !identityCandidate
```

A static-origin run-1 result is DEMAND. Therefore this guard admits it even
when its call result is an identity candidate. A static read can return to
a static alias as a computed effect. It need not come from a Java static
assignment. The field limit can then give that effect an ANY tail.

An actual small example in the source is:

```java
// ResourceExportService
public Set<EntityType> getSupportedEntityTypes() {
    return Set.of(EntityType.TB_RESOURCE);
}
```

Its actor has this retained writer selector:

```text
EntityType.TB_RESOURCE.* / FLOW
    -> EntityType.TB_RESOURCE.[any] / FLOW, DEMAND
```

The source has no assignment to `TB_RESOURCE`. This is a concrete example
of a writer directory that includes analysis effects beyond Java static
assignments. The heap does not retain the complete event history; it does
not establish the first producing call for every cross-field pair. The
code above identifies the alias route, not a recovered historical trace of
all 3,057,187 rows.

The follow-up E05 query identifies the actual descriptor for this example:
call stage 7 copies argument 0 to static class accessor 61, field accessor
1388, which the accessor dictionary resolves to `EntityType.TB_RESOURCE`.
Both transfer tails are STAR. There is no configured rule action on this
descriptor. The retained summary result later has the ANY tail shown above.
This pins the alias copy-back independently of a source-code assignment.

The E07 census finds 64,404 original static-write transfer descriptors whose
targets are these enum constants, in 483 physical method objects and 1,328
method keys. It finds **1,703 operation/slot groups that each target all 36
constants**. Each group holds method key, statement index, call stage, source
base/slot, and action fixed. Thus, the 36-way target expansion already exists
at individual operations, before caller re-export multiplies the summaries.

The relevant code is
[writer admission](../../core/opentaint-dataflow-core/opentaint-dataflow/src/main/kotlin/org/opentaint/dataflow/bidi/engine/Run1PointResume.kt),
[alias lowering](../../core/opentaint-dataflow-core/opentaint-jvm-dataflow/src/main/kotlin/org/opentaint/dataflow/jvm/bidi/interp/JIRCallPlanBuilder.kt),
the [memory-effect guard](../../core/opentaint-dataflow-core/opentaint-dataflow/src/main/kotlin/org/opentaint/dataflow/bidi/interp/CallPlan.kt),
[caller re-export](../../core/opentaint-dataflow-core/opentaint-dataflow/src/main/kotlin/org/opentaint/dataflow/bidi/engine/RunMethodCallServices.kt),
and the [global writer join](../../core/opentaint-dataflow-core/opentaint-dataflow/src/main/kotlin/org/opentaint/dataflow/bidi/engine/Run1StaticService.kt).

With several possible enum inputs and persisted enum aliases, the pipeline
can form this product:

```text
for each static input e_i:
    I_i -> bound local/FLOW, DEMAND
    call result -> bind-back local/FLOW, DEMAND
    for each admitted static alias e_j:
        I_i -> E.e_j.[any]/FLOW, DEMAND
```

The complete product is observed in the heap. The caller path repeats it:

```text
callee static summary
    -> applyStaticSummary / applyParts
    -> retainStaticCallEffect
    -> emitOperationResult(StaticOperation.Write)
    -> summaryDelta
    -> RunSummaryStore.add
    -> Run1PointResume.summary
    -> another owner-specific writer registration
```

`Zero` can admit the static FLOW summary on run-1. A static conclusion is
retained as a caller summary immediately. It does not traverse the caller
CFG, but it can travel up the call graph through repeated summary publication.

Finally, the service evaluates all-static writers eagerly:

```text
register writer -> dirty writer
new pool group  -> dirty staticDependents
dirty writer    -> candidateBindings(completePremise)
                -> WriterReady
                -> owner composes summary and publishes a supplied group
```

There is no test that the output field/group has a request. Many owner
copies can therefore compute the same global supply, which the pool only
deduplicates after the owner performs the work.

## Explanations that do not fit the census

- **Concrete mark count:** the dominant output is FLOW, not GROUP. The GROUP
  writer CSV contains only three different runtime atom IDs.
- **Many mark-exclusion variants:** 3,331,204 FLOW keys have the same mx set,
  raw atom IDs `{2,4}`. Removing mx from FLOW keys reduces 3,524,714 keys to
  3,496,922, a reduction of only 27,792, or 0.79%. That is not the main factor.
- **A large ND Cartesian history per writer:** no live `PremiseSet` occurs
  in the complete class census. Of the 3,010,244 service writer rows,
  2,622,788 have one delivery-map entry, 379,483 have two, and 7,973 have none.
  The cost is millions of separate rows and small maps.
- **A missing equal-insert check:** `RunSummaryStore.add` returns a fresh
  delta before the writer path runs. The product members differ in premise,
  field, or method owner. Existing local deduplication cannot merge them.

Obsolete selectors are retained by the current code. This census does not
classify every selector as current or obsolete. The small mx multiplicity
already rules out historical mx variants as the main explanation.

The terminal service counters report only 116 write regions, 91 supplied
regions, and 91 region/group memberships, against 3,010,244 writer rows and
441,329,622 candidate visits. The visit counter combines several join paths;
this report does not attribute every visit to writer replay or infer an
infinite loop. The evidence shows a large finite relation product and repeated
processing. It does not prove termination of the complete run.

## Effect on the selected redesign

The [selected three-set design](../design/bidirectional-run1-next-iteration-20261020.md#3-static-requests-three-sets-and-pending-continuations)
must apply to **all FLOW writers**, including the current all-static
`StaticWriterRegistration` path. Applying it only to the small
`StaticFlowWriter` directory would miss the main population.

The selected policy stops a newly written, never-requested group from waking
existing FLOW writers. It also releases answered read requests and uses one
canonical `C.f.*/G, DEMAND` answer. These changes remove eager unrequested
group work and region-specific answered-request state.

They do not by themselves prove that the FLOW relation matrix will disappear.
Keeping one persistent FLOW writer per owner/premise/output leaf would still
retain most of this population. Removing stored group-valued writers targets
only 3.7% of actor keys in this capture.

The next storage design must distinguish an original computed write from a
caller re-export of the same global effect. It should avoid a new permanent
pool dependency for each re-export, while it keeps the ordinary per-method
demand guidance that run-2 needs. The 6,685 owner-free descriptors show the
scale of repeated content, not an authorized owner-free replacement.

Also audit the identity-candidate/DEMAND alias interaction above. Changing
that guard can change field and memory-effect semantics. Such a change needs
an explicit contract and proofs; it must not be hidden in this request-store
optimization. No runtime or field-transfer change is made by this report.

The selected interim measure is now the
[DIRTY HACK: exclude enum constants from alias targets](../design/bidirectional-run1-next-iteration-20261020.md#2-dirty-hack-exclude-enum-constants-from-alias-targets).
It filters enum-constant static alias targets before edge creation. It keeps
direct reads, assignments, and rule positions. It is an explicit semantic
restriction, rather than a proof that enum instances cannot change. It is
not implemented or measured yet. The latest reached iteration remains
run-1 OOM.
