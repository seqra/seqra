# Bottom-up concrete static propagation implementation

Date: 17 October 2026. Status: implemented; measured, resource/acceptance gates failed.
The [selected specification](../../spec/run1-bottom-up-concrete-static-propagation.md)
defines behavior. The [proof dossier](../../spec/run1-bottom-up-concrete-static-proofs.md)
defines the checked finite models and the remaining runtime bridge. This document
maps that design to the runtime APIs. It adds no approximation or workload result.
The [runtime validation report](../reports/bidirectional-run1-bottom-up-concrete-static-validation-20261017.md)
records the passed core/consumer checks and the unchanged application precision
failures.

The [current project report](../reports/bidirectional-run1-bottom-up-concrete-static-20261017.md)
binds runtime `6b9291d7d`. Latest reached/completed/accepted forward iterations:
OWASP run-8/run-7/run-7; conductor run-1/none/none; ThingsBoard
run-1/none/none; Stirling-PDF run-2/run-1/run-1. No measured project meets
the 60-second run-1 engine target. Further changes require a new selection.

The layout reduction applies to certified transparent cohorts. A returning
method with a STAR concrete initial has a STAR-to-ANY exit effect. That exit
is an ordinary boundary and cannot be counted as a transparent relay. The
implementation keeps its scalar summary even when the method has no read.

## 1. Actual admission and early DEMAND

`Run1Abstraction` projects each actual static TAINT member onto the legal field
inventory. It does not consult a static mark request. EXACT at the actual field
point yields an EXACT initial. Nonexact or descendant supply yields a STAR
initial, whose current fact starts with an ANY tail. Keep the actual mark.
Direct class components use their separate projector and scalar path. Neither
path emits a concrete empty-static-root or ANY initial. FLOW is not a T supplier.

```kotlin
for (field in legalFieldsSuppliedBy(actual)) {
    admitAbstract(field)
    for (actualT in concreteMembers(actual, field))
        admitConcrete(field, actualT.mark, actualT.initialKind)
}
```

Apply the independent layer rule before guards, local evaluation, call binding,
summary application and publication:

```kotlin
resultLayer = inputLayer or operationLayer or
    originalPremise.any { it.base == STATIC } or (current.base == STATIC)
```

Preserve path, tail, marks, E, mx and full support. A dependent local result stays
DEMAND through ordinary layer joins. A callee can still compute an independent
NORMAL nonstatic summary relative to its own nonstatic initial; applying that
summary to a DEMAND caller input joins to DEMAND. This is not final-output
relabeling of an execution that ran NORMAL guards.

## 2. Static interests and ordinary Base requests

Remove inherited frozen static mark payloads and static rows in ordinary R/P.
Keep the legal field/class inventory and local effect metadata. `StaticField`
remains a coordinate type for projection and routing, not an ordinary R/P key.

`StaticNeedRouter` owns the authoritative source union for one run. A source key
contains the complete `MethodKey` and original static field. `commit` merges
marks at that source. A traversal shares one immutable mark batch and a finite
visited set; it does not install another source at each intermediate method.

```kotlin
StaticNeedRouter(ap,
    dispatch = { visit -> summaryStorage(visit.key.method).notifyStaticNeed(visit) })

// Existing method-empty work barrier; reserve delivery before releasing work.
router.commit(sourceMethod, temporaryStaticNeeds)
```

`RunEvent.StaticNeed` delivers work to the subscribed owner. The subscription
manager reads actual registered FLOW inputs and packed identity relations.
It maps each geometrically matching callee field to the caller's actual original.
A static original continues the same traversal. A nonstatic original offers an
ordinary Base request. E and mx do not subtract interest. Concrete inputs never
act as FLOW routes. The relation uses post-binding, post-guard inputs at the
actual call-plan stage, rather than the prescan call graph.

Register new input geometry before calling `graphChanged()`. The initial correct
implementation replays every committed source through a fresh traversal. Fresh
source marks also use a fresh traversal. This handles cycles and either arrival
order; permanent visited state would lose late edges or marks. An ordinary Base
join that maps to a static caller original creates a source at that caller and
reenters the router.

The bootstrap is:

```text
A's abstract p -> C.f reaches B's call as a real FLOW input.
B's reached check creates Need(B,f,T), with no concrete T evidence.
The reverse binding reaches A's original p, so Base(A,p)/T is requested.
An actual p/T replays the write. B then admits the resulting actual C.f/T.
```

Absent actual p/T, the interest cannot satisfy a check or create T. Do not add
conditional T initials to close this bootstrap.

## 3. Concrete carrier lane and ordinary effect boundaries

`ConcreteStaticEntries` uses a field-only TAINT trie as a diagonal descriptor.
Exact leaves encode EXACT originals. Any leaves encode STAR originals and their
ANY current values. Each field/mark/kind member has its own original. The tree
is not a common-premise fact or an input/output-mark Cartesian product.

```kotlin
for (initial in codec.originals(entries)) {
    val current = codec.current(initial) // exact or ANY, always DEMAND
    evaluateOrdinary(initial, current)
}
```

`Run1ConcreteStaticFlow` retains entry and per-location descriptors. It uses the
existing conservative static effect selection to decode only potentially
affected members. Unknown operations select ordinary evaluation. Concrete
cleaners, conversions, writes, source/sink predicates, ND and call suffixes are
effect boundaries. Lack of GETSTATIC alone does not prove transparency.

The first version keeps class inputs, W/transformed inputs and whole ND inputs
on their ordinary bound paths. A same-field T-to-U result retains T in its
original premise and U in its current payload. A cross-field result retains the
original field. Only a full canonical member can use the diagonal lane; field
name equality is insufficient.

`ConcreteStaticSubscription` shares the complete caller/callee/call/stage port
and descriptor. Its shared `identityCandidate: Boolean?` field is also part of
the relation key. Packed call frames preserve and update that field through the
same `StageKind.identityCandidateOf` rules as the ordinary walker. A SOURCES or
END_FACTS keep still sets false. Default DEMAND application resumes with false.
Selected effect stages use the ordinary remaining call path. Once the complete
call exits to CFG state, no call-stage flag is retained; the next call starts
with its ordinary unclassified flag. Decode a matching member before ordinary summary application.
New publications visit existing inputs. New input members replay existing
publications. Whole ND matching keeps every literal at the same full call port;
a conditional summary cannot borrow a member from a different caller or call.
Preserve ordinary post-callee stages and caller layer joins.

At a real exit, a read-free STAR concrete initial still produces its ordinary
STAR-to-ANY effect. For distinct suffixes a and b, this effect can relate f.a/T
to f.b/T. The root identity cannot supply that relation. Do not compact it or
invent it for a method with no such exit. The unconditional root DEMAND default
remains an ordinary stored summary and requires no S-root initial.

## 4. Publication, persistence and lifetime

Retain ordinary event outputs even when all value outputs die. A request,
source or sink event is not erased by a later kill. Local effects execute before
summary compaction.

Pure concrete EXACT same-field/same-T DEMAND identity can be compacted only with
the full identity/header/support test and an available root default. Class,
chain, ND, transformed and NORMAL contributions do not pass this test. Continue
ordinary NORMAL record admission and typed handoff. DEMAND does not become a
reusable NORMAL record. Later runs retain their ordinary current-query workflow.

Pending actor work includes concrete descriptors, source needs, Base commits and
remote deliveries. Register child work before releasing the parent reservation.
Release actor descriptor maps, subscription relations and run-scoped source
unions after the joined run. No decoded supplier history, answer memo or new
global cache is introduced.

## 5. Independent verification

The production code is under
`core/opentaint-dataflow-core/opentaint-dataflow/src/main/kotlin/org/opentaint/dataflow/bidi/`.

| File/API | Responsibility |
| --- | --- |
| `engine/Run1Abstraction.kt`, `Run1StaticComponents.kt` | Actual legal supply projection; no static R gate |
| `engine/Run1StaticProfiles.kt` | Legal coordinate inventory and local metadata; no inherited static mark payload |
| `engine/Run1StaticFlowPolicy.kt` | Early/static-history D and exact identity-leaf compaction |
| `engine/ConcreteStaticEntries.kt` | `accepts`, `single`, `current`, `originals`, immutable merge/delta, conservative effect selection |
| `engine/Run1ConcreteStaticFlow.kt` | Actor entry/node descriptors, ordinary effect evaluation, actual-exit publication and release |
| `engine/ProtocolContracts.kt` | `ConcreteStaticSubscription` full shared call-port header |
| `engine/ConcreteStaticSubscriptions.kt` | Register before replay, candidate selection, exact member decoding and ordinary application |
| `engine/StaticNeedRouter.kt` | Source-only `commit`, `graphChanged`, shared traversal batch and source snapshot |
| `engine/SummaryStorage.kt`, `SubscriptionManager.kt` | Source notifications and reverse routing over actual caller inputs |
| `engine/RunMethodCallServices.kt` | `flowConcreteStatic`, exact stage continuation, ordinary effect/ND fallback |
| `engine/RunEvent.kt`, `RunnerPort.kt`, `UnitRunner.kt` | Counted concrete-entry and need delivery; test-only process observation |

The test-only `core.concreteStaticEntryFacts()` and
`RunnerPort.onConcreteStaticProcess` expose descriptor views for logical
observation. They do not install a production decoded-history store.

`NaiveClosure.Semantics.SELECTED` is the ordinary concept reference. It executes
per-path scalar statements, retains an expanded static-interest walk through
actual caller inputs, admits actual static T without static R, and has no frozen
static mark base. Its static source observations and ordinary Base endpoints are
compared with the optimized router. Intermediate expanded static request rows
are reference state, not a required production store.

`ProductionSchedule` uses real actors, subscription/storage handlers and queued
replay/notification. Its source callback records committed source needs. Test-only
decoders expand FLOW and concrete descriptors at observation boundaries. These
decoded rows are never retained by production.

`Run1BottomUpConcreteStaticTest` compares all nine semantic observation channels:
processed facts, logical initials, raw summaries, publications, selected demand,
full caller links, source/Base requests, backward source hits and witnesses.
The bounded union comparison retains distinct tails, layers, marks, E, mx and
complete premises. It does not replace expected results with runtime output.

Required controls include:

1. Write bootstrap through a read-free relay, both with actual T and without T.
2. Changed field routing back to the original nonstatic write input.
3. Actual exact T admission without any local mark need, with diagonal fields.
4. Returning STAR-to-ANY effects and direct class exact supply.
5. Strong kill before call; T-to-U conversion with the old original retained.
6. Split-call ND inputs versus all inputs at one actual call port.
7. Source-before-input, input-before-source, late graph growth, same/cross unit,
   duplicate delivery, recursion and bounded quantum schedules.
8. NORMAL independent nonstatic results, sticky static dependence, release and
   counted completion, plus ordinary run-2-and-later regression gates.

The scalar/transport Lean checks do not verify the Kotlin compiler or concurrent
scheduler. Run focused controls first, then full common/JVM/consumer gates and
the unchanged strict application finding assertions. Report actual failures;
do not relax finding sets to meet the gate.

## 6. Fixed project measurement plan

Do not run workloads until the implementation is committed, gates are bound,
both runtimes are prebuilt, old owned raw captures are cleaned with receipts,
and the fresh input audit passes 80 checks over 41 content datasets. Refresh and
pin remote main before launch. Previous main was
`f945f92c5ae554ff4f5fc7696ee5a269210478bb`; this is a historical known revision,
not a claim that remote main is still unchanged at the next launch.

Use JDK17 and direct Gradle `runProjectAnalyzer` in each checkout's `core/`:

```sh
./gradlew :classes --max-workers=2 --console=plain -x :opentaint-ir:go:buildGoServer
./gradlew :runProjectAnalyzer --console=plain -I <new-bundle>/benchmark.init.gradle -x :opentaint-ir:go:buildGoServer
```

The reviewed harness configures only the selected JavaExec. It preserves CI
batch size 2000 and multi-release=false. Run only these configurations:

| Project | Existing model | Analyzer heap | IFDS seconds | Outer seconds |
| --- | --- | --- | --- | --- |
| OWASP | `/drive-testcomp/opentaint-go-rules/owasp-ci-investigation/project/project.yaml` | 8g | 1000 | 1200 |
| conductor | `../opentaint-test/opentaint-test-conductor/opentaint-project/project.yaml` | 8g | 1080 | 1200 |
| thingsboard | `../opentaint-test/opentaint-test-thingsboard/opentaint-project/project.yaml` | 12g | 1080 | 1200 |
| Stirling-PDF | `/drive-testcomp/opentaint-go-rules/e2e-root/model-Stirling-PDF/project.yaml` | 8g | 1080 | 1200 |

Use the conductor extension rules, passthrough and compiled dataflow models from
the fixed harness. Use identical models/rules on both sides; add
`--ifds-bidirectional` only for the candidate. The benchmark manifest must retain
the complete generated JavaExec arguments and actual revisions.

Run fresh main four, then committed candidate four, sequentially. Bind continuous
JFR, the requested 30-second normal/near-limit CPU windows and actual OOM guard
snapshots to their true phases. Profile windows can cross iterations. Repeat the
input audit after all eight invocations close. Only then run exact findings
comparisons and serialized profile/heap analysis.

Preserve full rule/sink identity, exact spans and occurrence counts. Record latest
reached, completed and accepted iteration separately. A wrapper exit0 is not
analyzer success. Older `ab6c3cd00` measurements are historical controls and do not
establish this implementation's performance, precision or completion.
