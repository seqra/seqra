# Run-1 performance: static and alias controls

Measurement dates: 21–22 October 2026. Status: **measurements complete**.
The fixed 16-cell matrix is closed. All source, input and actual JVM
argument checks passed. Thirteen isolated control measurements are closed.
No experimental analyzer change was applied to the user branch.

Only the all-enabled Thingsboard cell fails run-1 in this matrix. Its
other three configurations complete run-1 in 88.66–94.26 seconds. This
points to an interaction between aliases and static fields. It does not
explain all remaining time: OWASP's fastest observed cell is 152.372 seconds.
The earlier latest-version conductor and Stirling-PDF OOMs occur in
**run-2**, after run-1 completes. They must not be counted as run-1 failures.

The severe historical regressions partly recovered. The 15 October report
has run-1 times of 331.657 seconds for OWASP, 60.210 for conductor and
36.102 for Stirling-PDF. The fresh all-enabled cells take 230.832, 67.134
and 36.884 seconds. Thingsboard fails run-1 in both campaigns. Thus the
latest measurements do not show a large loss on every project against
15 October. They still miss the target and the expected BaseOnly result.
These are different campaigns, not repeated measurements of one patch.

The strongest current causal result is C5. On the latest runtime, removing
only two static-alias publication hooks lets Thingsboard finish run-1 in
96.253 seconds with AA and static reads enabled. FLOW writers and point
subscriptions both become zero. Identity-only controls still OOM. This
finds a writer-publication interaction, not a mark-table size problem.
C5 can lose real flows and is not a production fix.

The first valid historical regression has two separate measured factors:
broad static roots/classes expand into too many field initials, and NORMAL
static complements create costly root/class FLOW alternatives. Disabling
both structural expansions restores completion. Separately keeping a
coarse NORMAL root instead of partitioning its complement cuts the
completed historical engine from 149.046 to 53.241 seconds, with nearly
the same initial count. Both controls can omit or add flows. These old
mechanisms explain the history; their exact subsystem is absent today.

The measured work has two main coordinates. Static fields create many
writer/reader/continuation relations. Ordinary processing does AP and
rule work before storage admission. Finite controls also show repeated
work that produces equal stored results. Mark packing alone does
not bound either coordinate. The source comparison also finds less
sharing of ordinary transfers across origins than BaseOnly; its actual
project reuse rate has not been measured.

| Question | Closed result |
| --- | --- |
| Is current Thingsboard failure tied to statics and aliases together? | Yes in this matrix. Only their enabled pair OOMs; C5 restores completion. |
| Does alias identity filtering alone fix it? | No. Both C6 and C7 still OOM. |
| Does selected GROUP survival explain most OWASP work? | No. The intentionally aggressive C2 kill still performs 83.1 million form accesses. |
| Does emitting all required origin arms explain most conductor work? | C3 does not establish that: form accesses and sampled allocation stay almost unchanged. |
| Can longer duplicate suppression be transplanted cheaply? | C4 cuts form work by 24.31%, but has more GC work and a longer observed engine time. |
| Is a removed static router responsible for today's cost? | No. Its historical replay defect is separate from the current service. |

## Source and measurement scope

The production runtime is `d7244c08cdaefbb157f42e4d89731d301b97e48f`.
The selected workspace HEAD is `969649ddab2736fecc6e4b6a5a8a1b74361f0dc0`.
The diagnostic checkout is `d0c77158c48f47926fa50bfc2390bc5f0df84b99`.
Its only extra source changes add two disabled-by-default controls:

- `opentaint.bidi.disableAliases`: disable AA in prescan and the core.
- `opentaint.bidi.run1Only`: stop at the normal completed run-1 barrier.

All cells use the latter control. The existing
`opentaint.bidi.disableRun1Statics` control selects the other axis.
Static OFF keeps exact synthetic global class markers. It does not remove
alias preparation from form construction. Alias OFF can change analysis
precision and the work selected during prescan; it is not a fixed-graph
microbenchmark. Neither disabled axis is a production correctness proposal.

All runs use Gradle `runProjectAnalyzer`, Java 17, the configured models
and rules, and the conductor extensions. Every cell has continuous JFR,
the same form/optimization counters, and the existing CPU/heap capture
policy. The 60-second target covers **run-1 engine time**. Prescan,
the report barrier and final SARIF export are separate.

| Project | Heap | IFDS limit | Outer limit |
| --- | ---: | ---: | ---: |
| OWASP | 8g | 1000 s | 1200 s |
| conductor | 8g | 1080 s | 1200 s |
| thingsboard | 12g | 1080 s | 1200 s |
| Stirling-PDF | 8g | 1080 s | 1200 s |

The host is shared. These first matrix cells are single measurements.
Do not interpret a small timing difference as an isolated speed effect.
An OOM prefix is not a completed-work speed result.

## Closed matrix and latest iteration

All entries below are run-1 FORWARD, field limit 1. Every cell reached
**run-1**. All COMPLETE cells completed and accepted **run-1**. The
Thingsboard all-enabled cell has no completed or accepted iteration.

| Project | Statics OFF / AA OFF | Statics OFF / AA ON | Statics ON / AA OFF | Statics ON / AA ON |
| --- | ---: | ---: | ---: | ---: |
| conductor | 24.807 COMPLETE | 37.731 COMPLETE | 34.036 COMPLETE | 67.134 COMPLETE |
| Stirling-PDF | 16.422 COMPLETE | 27.215 COMPLETE | 15.646 COMPLETE | 36.884 COMPLETE |
| OWASP | 152.372 COMPLETE | 261.867 COMPLETE | 152.767 COMPLETE | 230.832 COMPLETE |
| thingsboard | 88.657 COMPLETE | 94.262 COMPLETE | 88.766 COMPLETE | 132.727 OOM |

The Thingsboard OOM engine time includes a 28.350-second live-heap capture,
23:30:12.143–23:30:40.493 UTC. The dump has 17,943,129,657 file bytes.
File size is not retained heap. The closed log reports 4,439,702 in-flight
protocol events near cancellation. Events are not facts.

Thingsboard's both-disabled cell exports 337,225 DEMAND findings after
the engine. This export extends command time. It is not part of the
88.657-second engine result. The one-run stop changes when final export
occurs; it does not change the run-1 engine inputs or transfer rules.

## What the current static counters show

These are terminal service counters. They describe different populations;
do not add them as a fact count or convert them to retained bytes.

| Thingsboard population | Both OFF | AA only | Statics only | Both ON, OOM |
| --- | ---: | ---: | ---: | ---: |
| Write regions, including synthetic markers | 65 | 65 | 65 | 104 |
| Read descriptors | 0 | 0 | 41,933 | 41,911 |
| Point subscriptions | 0 | 0 | 0 | 2,732,842 |
| Static-origin FLOW writer rows | 0 | 0 | 0 | 1,255,875 |
| Other FLOW writer rows | 0 | 0 | 0 | 45,125 |
| Pending group memberships | 0 | 0 | 0 | 32,170,001 |
| Writer group admissions | 0 | 0 | 0 | 42,896,991 |

The alias-disabled, static-enabled cell has many reads but no FLOW writer
rows. Exact class markers cannot activate field reads. The enabled pair
creates a large writer/read/point relation. The size of that relation is
not bounded by the number of mark groups.

The source contains a specific amplification path. An activated static
read installs a DEMAND FLOW edge. A normal identity summary applied to
that input keeps DEMAND. The call ALIASES guard admits a DEMAND identity.
Its static alias output can publish a new FLOW writer and activate more
reads. This path needs no concrete group request. It needs a first writer;
a read alone does not bootstrap it. Statement field aliases and real
nonidentity effects can provide that writer.

The DEMAND exception and NORMAL-only identity classifier predate the
15 October reference. Later static/GROUP DEMAND policies change their
inputs; `b88` makes reached static alias outputs publish writers.

The closed controls identify alias-write publication as a cause of the
observed explosion. DEMAND identities are one source-supported route
into that family; restricting identities alone does not prevent OOM.
C5 completes with
zero FLOW writers and zero point subscriptions while AA and reads remain
enabled. It removes two metadata hooks together, so it does not isolate
statement publication from call publication. Real heap mutations also
need these hooks; the result does not make all alias writers redundant.

A source-level example shows the two parts. This is an explanatory
fixture, not a trace of a measured Thingsboard method:

```java
void seed(Object p) {
    Box b = C.f;             // AA learns an alias before taint activation.
    b.value = p;             // A tracked RHS can publish a FLOW writer.
}
Box relay(boolean flag) {
    Box b = flag ? C.f : C.g;
    pass(b);                 // A DEMAND input can enter the alias stage.
    return b;
}
```

After a writer activates the read, its original `C.f/FLOW` can accompany
nonstatic b through ordinary code. A compatible alias output can publish
`C.f/FLOW -> C.g/FLOW`. Static conclusions stop local CFG propagation,
but caller summary application can publish the relation again under a
different method owner. Global region deduplication does not merge these
method/premise/output rows. Thus no local static-fact relay does not mean
no static summary or refinement propagation. The real mutation in `seed`
also shows why simply deleting all alias publication is incomplete.

## Fresh Thingsboard heap

The all-enabled run-1 OOM snapshot has 11,234,045,720 reachable heap bytes
in the complete MAT histogram. The initial HPROF scan counts 346,654,131
objects before MAT reachability processing. File bytes and scanned objects
are not the reachable retained population.

| Measured owner selection | Retained bytes |
| --- | ---: |
| Static service and point continuation union | 2,984,636,392 |
| Static service, one individual dominator | 1,467,513,248 |
| Work-queue union | 717,404,440 |
| Ordinary caller/publication/request union | 636,610,232 |
| Run summary owner union | 478,835,424 |
| Method edge owner union | 428,022,800 |
| All exact witness and factored result owner union | 72,499,104 |
| Run-1 factored result owner union | 12,067,832 |
| Mark owner union | 459,472 |

These selections overlap. Do not add their retained bytes. The queue
selection contains 4,413,052 `PointReady` events and their `RegionGroups`
payloads. The service has 1,301,000 FLOW writer rows and 2,731,183 pending
field-interest rows across 81 field states. Actor directories include
2,509,920 writer inputs, 648,293 points and 220,532 calls. These are
different coordinates, not unique fact counts.

Four static FLOW initial IDs account for 634,592 of the 648,295 physical
point rows, or 97.9%. A seven-query indexed pass resolves their names:

| Static premise | Physical point rows |
| --- | ---: |
| `TenantId.tenants` | 171,896 |
| `ModelConstants.MOBILE_APP_STORE_INFO_EMPTY_OBJECT` | 160,459 |
| `ModelConstants.MOBILE_APP_VERSION_INFO_EMPTY_OBJECT` | 153,913 |
| `TenantId.SYS_TENANT_ID` | 148,324 |

These counts name initial premises. They are not read-site counts or
writer-target counts. The source has one actual read site for each mobile
default, in `MobileAppEntity.toData`:

```java
mobile.setStoreInfo(storeInfo != null
    ? fromJson(storeInfo, StoreInfo.class)
    : MOBILE_APP_STORE_INFO_EMPTY_OBJECT);
```

The conditional static premise can then accompany nonstatic current facts
and return/caller summaries. Later checks retain distinct refinement
points for that premise. A single read is not a bound on this population.
Alias analysis can also join the default and parsed-value alternatives;
the exact writer derivations still need their controlled test.

The closed follow-up joins every physical point row to its actual site.
It matches all 648,295 rows and every initial/pending-group total from the
first query. Exactly 593,314 points, or 91.52%, use `CleanerInput`;
51,429 use `CallStageInput`, and 3,552 use `BoundaryInput`. Separately,
526,979 current bases, or 81.29%, are `Return`; 33,944 are cleaner
temporaries. These are overlapping dimensions. The dominant retained
points are later call/cleaner refinement state, not the original static
read descriptors. All 593,314 cleaner points have phase CLEAN. All 33,944
named temporaries have role `RETURN_REWRITER`; this does not identify the
role of every ordinary-base point. The maximum cleaner-step ordinal is
123; 431,823 cleaner points have step at least 10, and 81,025 have step at
least 100. Step numbers are not mark counts or proof of duplicate work.

The first optional-metadata review rejects native `null` rendering in
nonmatching subtypes. A separate Python review normalizes only those
structurally absent phase/role strings. Its core and typed checks pass;
the original review remains. No MAT/query rerun occurred.

The source declares `tenants` as a map filled by `computeIfAbsent`.
The mobile defaults are shared `@Data` objects with mutable fields.
`static final` fixes their references; it does not prove their contents
immutable. The source declarations are ordinary static fields and do not
match the enum-constant hack. Source bindings are in
`validation/current-static-field-pattern`; the seven-query review is in
`validation/matrix-static-field-names-review-v2`.

The 1,479,557 standing actor writer rows reconcile exactly with 1,301,000
committed service rows plus 175,710 static-origin and 2,847 other pending
batch rows. This difference is a batching frontier, not evidence of a leak.

The mark interner has 42 group keys, 934 full keys and 977 atoms. Thus a
small group domain coexists with a large writer/continuation relation.
The histogram has only 353 canonical `InitialAp` objects. One such value
can be used by many methods, current facts and operation points. Writer
keys include premise, output and layer; point keys also include the site,
current base/tail and caller state. The field-exclusion key coordinate
stays empty in run-1; FLOW mark exclusions are not in the point key.
A small fact-value domain
does not bound the number of these relations. Existing equal-key
deduplication does not merge different relation keys.
The heap also retains IR RAM storage and resolved rule configuration;
IR storage changes remain out of scope.

MAT completed with exit code zero. The first wrapper rejected the generated
archive filename. A separate review binds the actual ZIP to the same
capture, actual VM, process closure, report and all 41 query commands.
No MAT rerun occurred; the original failed wrapper receipt remains.
The complete admissions table exceeds the one-million-row review cap
and is unavailable for an exact aggregate. A blank phase status is also
unavailable. Neither outcome means zero. The compact query review is in
`validation/matrix-heap-archive-review-v2`.

The complete shallow histogram also has 12,474,771 `ContainsMark` objects,
299,394,504 shallow bytes. Small mark-key storage does not mean small
resolved-rule storage. This is a separate population from propagated marks.

## Profiles and the remaining nonstatic work

All 16 CPU/allocation readers and all 16 blocking readers are closed.
Four reader controls passed before the recordings were read. Each reader
uses the explicit run-1 interval. Inclusive stack percentages overlap.
Allocation weights are sampled weights, not exact allocations or retained
heap. Failed engines have different work frontiers.

In OWASP with both features OFF, `cleanChain` occurs in 34.63% of execution
samples; `cleanFacts` occurs in 21.41%, and the field-step `statement`
helper occurs in 17.45%. These are nested costs. They must not be added.
The primitive `TaintClean.run` occurs in only 3.35%, and `FlowClean.run`
in 0.96%. A synchronous cleaner callback can execute read/write, filtering
and trie-cut work. A hot inclusive cleaner frame does not mean the
primitive mark test consumes that whole share.
In Thingsboard with both ON, `JIRCallPlanBuilder.build` occurs in 45.63%
and intra-procedural alias computation in 27.99% of execution samples.
Those percentages also overlap. These are different workload patterns.

The OWASP both-disabled cell offers 2,003,560 ordinary request memberships:
1,931,253 are temporary duplicates, 19,007 are authoritative duplicates,
and 53,300 are fresh. Temporary request storage already merges them before
commit. This saves storage and propagation. It does not undo cleaner/AP
work or request construction before admission. Do not treat these offers
as two million retained requests.

With statics OFF and aliases ON, Stirling-PDF records
`JIRCallPlanBuilder.build` in 59.88% and its `aliases` helper in 44.73%
of execution samples. Call plans prepare aliases before the later
memory-effect execution guard. Method-level alias results are lazy;
this does not mean DSU is recomputed for every call. Form rebuilding can
repeat per-call alias-list selection and lowering. This preparation cost
exists even when the static propagation switch is OFF.

The default scheduling unit is a package. OWASP has 2,740 of 2,766 input
Java files in its testcode package. One coroutine processes a unit's
method actors sequentially. This limits useful parallel work. The same
package policy exists in BaseOnly and the 15 October reference, so it is
an inherited limit, not the introducing regression.

The closed observer-only run confirms this distribution in the actual
statics-OFF/AA-OFF workload. It completes and accepts run-1 in 151.092
seconds. There are 72,392,389 actor steps across 12,066 method actors in
nine units. The testcode package owns 72,368,707 steps, or 99.967%, and
11,847 actors. The largest actor has 202,790 steps. These are
ordinary/unchanged worklist steps; they exclude
direct resume queues and owner callbacks. The 3,047,784 method edge-delta
admissions count a different population. Their ratio is not a measured
duplicate-work ratio. This observer leaves hot transfer processing intact;
it reads existing counters after runners join.

The BaseOnly core also grouped equal current facts across origins and
cached supported ordinary transfers without the origin in the key. The
new core repeats the AP kernel per original premise. This difference
also predates the 15 October reference. Its current frequency needs a
separate measurement; hot AP stacks alone do not prove repeated equal work.

There is no supported zero-versus-one-field explanation. The old
`BaseOnlyField` mode and current limit 1 both retain one structural field
or element. Their tail, matching, projection and layer rules differ.
For example, a second field projects into the old compact outer-field
form, while current run-1 can create an `[any]` DEMAND suffix. The deleted
experiment's actual mode flags are unknown; the source default does not
establish those flags. Both engines also batch summaries until local
method work drains. Missing summary batching is not the source difference.

## Verified cleaner mechanism

The actual JVM builder tests pass for String, primitive-group String,
Object and AnyField cases. For two full names T/U in one group G, a String
return rewriter has four active steps in **both** GROUP and FULL views:

```text
GROUP: Clean(root,G), Field(bytes,G), Clean(root,G), Field(bytes,G)
FULL:  Clean(root,T), Field(bytes,T), Clean(root,U), Field(bytes,U)
```

The field step resets group scheduling history. Object and AnyField
controls have two plain GROUP cleaners and one active step. Thus String
does not create more raw steps in GROUP than FULL. GROUP can execute
more of them because the selected clean does not end the contribution.

These return rewriters also implement user-rule overrides at source action
positions. They are not limited to explicit configured cleaners. FULL
returned marks can end before configured source outputs are applied;
GROUP keeps its candidate. Relevant per-method full names are not proof
that a cleaner sequence covers every member of the top-level group.
Killing that whole group is a semantic change requiring a coverage proof.

Projection to G also does not remove all full-name coordinates. Field
cleaners retain their raw step indices and temporary action IDs. Two full
names in one group can therefore request the same G at two different
continuation keys. Storage merges an equal key; it does not merge these
different keys. This explains why the number of groups alone cannot bound
the request workload. Their exact retained frequency remains under review.

Passing finite actor/scalar cases show the work before ordinary storage:
two String-shaped field wrappers on an exact root perform four statement
cuts and preserve the same result. An ANY_TAINT root case performs fourteen
statement cuts and produces two call-exit callbacks; ordinary storage keeps
one fact. A nested selected-group wrapper can also add a broader MAY demand.
These cases prove that work occurs before store deduplication. They do not
measure its workload frequency or justify bypassing every field wrapper.

The first common-control attempt had two FULL fixture failures: it requested
invalid run-3/limit-1. The independent JVM tests and 23 completed GROUP
traces remain preserved. The test-only correction uses legal run-3/limit-2
actor controls plus separate scalar FULL/limit-1 controls. All nine corrected
common tests pass; all three independent JVM lowering tests pass.

## Historical regressions

The history has multiple regressions and recoveries. It does not show
one continuous loss after added-fact removal.

- The [mark-request report](bidirectional-run1-mark-request-performance-20261015.md),
  runtime `4c64c579f`, first loses run-1 completion through a static-domain exception.
  Its short failing prefixes are not performance wins.
- The corrected materialized control in the
  [static-domain report](bidirectional-static-domain-frozen-requests-performance-20261016.md),
  runtime `d330a335a`, is the first later valid resource regression:
  conductor changes from 60.210-second COMPLETE run-1 to run-1 OOM.
  Stirling-PDF changes from 36.102 to 168.350 seconds. The campaigns reach
  run-2/run-4 at the reference and run-1/run-4 at the corrected revision.
- `4c` also expands broad static roots/classes from explicit children to
  the owned field universe. `d330` retains this expansion while correcting
  owner/metadata coverage. The later `4ac` factors request representation
  without reducing the logical field-initial population. These are
  separate atoms; the corrected campaign does not isolate their costs.
- `28a123f0e` restores conductor and Stirling-PDF run-1 completion below
  60 seconds. Both campaigns then reach run-2. It combines static gating
  with field/tail/layer changes, so its timing does not isolate one atom.
- `6b9291d7d` introduces the old need router and packed concrete-static
  carriers together, followed by another regression. A finite production
  probe proves quadratic binding rescans in that router. It does not
  allocate the whole project loss to replay. The router was later removed.
- `ff2966455` combines GROUP marks, cleaner retention, static handling,
  DEMAND changes and unintended grounding. `dac301f2e` removes grounding.
  `51dbc4d66` later restores three completed run-1 engines. `d7244c08c`
  adds required origin arms as well as routing/projection reductions.

The updated atomic ledger separates 72 units in 19 production patches. It records
what survives today. Old root expansion and old graph replay must not be
presented as current mechanisms. The successful deleted BaseOnly reports
are accepted as user evidence; their unavailable timings are not invented.

| Performance-critical atom | Present now? | Causal check and limit |
| --- | --- | --- |
| Broad root/class expansion to owned field universe, `4c`/`d330` | Replaced | ROOT-only OFF still OOMs; ROOT+CLASS OFF completes but remains slow; keep root emission and mark requests |
| Extra static-summary ancestor candidates, `4c-E` | General matching survives; old static contract replaced | Hselect changes both transpose lookups; almost all expanded caller groups and FLOW cost remain |
| NORMAL root complement split into root/class residual pieces, `4c-F` | Old subsystem removed | Hresidual cuts caller groups from 2.61 million to 522,141 with nearly equal initial count |
| Repeated whole-graph static replay, `6b`/`f18` | Removed | Historical source and finite replay probe; no current router rollback |
| Selected GROUP survives cleaners, `ff` | Yes | C2 lower bound kills too much; it cannot be a production fix |
| Static alias outputs publish write metadata, `b88` | Yes | C5 removes only two metadata seams; alias value propagation remains |
| DEMAND bypasses the alias identity guard; GROUP identities fail the NORMAL-only classifier | Yes | C6 still OOMed; C7 separates the wider identity path; both can lose demands |
| Emit all contributing result origins, `d724` | Yes | C3 restores old tuple representatives and loses required arms |
| Unchanged repeat keys expire before all method fact queues drain | Yes; predates reference | C4 extends the guard to the whole fact-queue epoch |

BaseOnly also shared supported ordinary AP transfers across equal current
facts with different origins. The new engine caches forms, but applies
them per origin. That difference predates the reference. It is a candidate
for a new, separately specified optimization; it is not a narrow rollback
of the static refactor. Static conclusions stop local CFG propagation,
but their static premises can still accompany nonstatic current facts and
ordinary caller results. Thus “no local static propagation” does not bound
work by the number of static fields or groups.

## Closed isolated controls

C6 changes only classified run-1 alias identity admission. Its build and
eight focused controls pass. Its Thingsboard measurement is closed:
**run-1 OOM, 195.034 seconds**. Latest reached: run-1; completed and
accepted: none. The engine includes a 30.032-second heap capture.
The endpoint, actual argument and compiled-input checks pass.
Its terminal counters have 713,164 static-origin FLOW writer rows,
1,197,562 point subscriptions and 18,225,138 pending group memberships.
The nearby log has 1,868 events in flight. These are different failed
frontiers; their time and population ratios are not speedup measurements.
This guard change is insufficient to prevent the OOM. It does not exclude
GROUP identities that the current summary classifier does not recognize,
statement aliases, or real nonidentity effects.
C2 kills selected GROUP values as a lower-bound diagnostic; its build and
four focused controls pass. Its OWASP measurement is closed:
**run-1 COMPLETE, 209.189 seconds**, latest reached/completed/accepted
run-1. The baseline all-enabled cell takes 230.832 seconds. This single
timing pair is not a precise effect estimate. The control still makes
83,118,705 form accesses, versus 90,192,231 in the baseline. Static writer,
point, pending-membership and admission counts are identical; ordinary
requests have 54,233 fresh memberships versus 54,239, and about 3.335 million
offers in both. Thus selected GROUP survival is not sufficient to explain
most OWASP work. The control can lose required demands and findings.
Neither C2 nor C6 is an acceptable production rollback.
The control tests expose their intended omissions; they are not a claim
that the original correctness suite passes.

C5 removes only the static alias-write metadata added at two JVM lowering
sites: instance/array-field assignment aliases and persisted call aliases.
It keeps AA, alias value edges, static read descriptors, direct IR writes,
rule writes and callee static results. Its seven focused controls pass.
The closed Thingsboard run is **run-1 COMPLETE, 96.253 seconds**, latest
reached/completed/accepted run-1. It has 43,505 read descriptors, but no
FLOW writer rows, point subscriptions, pending group memberships or
writer group admissions. The all-enabled baseline OOMs; disabling static
propagation with AA ON completes in 94.262 seconds. The latter timing
pair is one observation, not a precise overhead estimate.

This isolates the two-hook publication family much better than AA OFF:
the alias calculation still runs. C5 can also remove metadata allocation
and reached-operation rows. It does not prove that deleting publication
is sound or that every alias-derived writer is a false effect.

C7 adds GROUP identity classification to C6's identity guard. Its eight
focused controls pass. It still ends with **run-1 OOM, 225.005 seconds**;
latest reached run-1, none completed or accepted. This includes a
28.662-second heap capture. It has 751,185 static-origin FLOW writer
rows, 1,167,938 point subscriptions and 13,528,574 pending memberships.
Neither identity control is sufficient. Their different failed frontiers
do not give a time speed ratio or exact share of redundant writers.

All C2/C5/C6/C7 batches pass their endpoint, actual JVM argument and
compiled-input checks. C5 and C7 are intentionally incomplete controls,
not production fixes.

Thirteen isolated control measurements are closed: seven on the latest
runtime and six matched historical measurements. They do not cover every
possible candidate. No measured rollback is ready for production use.

C3 restores old tuple storage and representative selection together.
C7 includes the C6 guard and adds GROUP identity classification. These
are diagnostic combinations, not independent single-atom estimates.
The closed C3 conductor run completes and accepts run-1 in **44.936
seconds**, versus 67.134 in D0. It still has 34,631,801 form accesses,
versus 34,625,762, and 72,866 new initials, versus 72,870. Fresh ordinary
request memberships are identical at 149,179. Point subscriptions fall
from 142,937 to 123,665. Thus the control does not remove most of the
measured form/input workload. Approximate engine process CPU is 459 versus
539 CPU-seconds; mean process CPU is 10.19 versus 8.02 CPUs. Polling,
GC/JIT and different host scheduling limit this comparison. The wall-time
change alone is not an isolated origin-arm cost. Both exports have 12,732
rows; equal row counts do not establish equal findings or demand guidance.
The closed recordings also have almost equal sampled allocation weight:
206.426 GB in C3 and 206.214 GB in D0. Cleaner ancestry is about 21% in
both. These observations do not support assigning most conductor work
to the required extra origin arms.

C4 keeps unchanged-repeat keys until the whole method fact queue drains.
Its focused controls pass. The closed OWASP run completes and accepts
run-1 in **252.691 seconds**, versus 230.832 in D0. Form accesses fall
from 90,192,231 to 68,264,525, or 24.31%. Request membership offers fall
from 3,335,956 to 2,903,805. New initials remain 42,327; fresh ordinary
request memberships remain 54,239; form builds and static-service counts
are identical. This is a measured work reduction, not a measured speedup.
Approximate process CPU rises from 364.98 to 1,008.31 CPU-seconds. The
closed recording shows a large GC change: G1 old-generation cycles rise
from 1 to 38. The union of GC event intervals rises from 7.760 to 129.290
seconds; pause union rises from 6.803 to 18.126 seconds. GC event intervals
include concurrent work. They are not GC CPU-seconds or wholly blocked
engine time. Sampled allocation weight falls from 549.175 to 443.822 GB.
Thus fewer form operations and fewer sampled allocations coexist with
more GC work.

The changed cache strongly retains complete EdgeDelta/Facts keys until a
later barrier. Its backing hash table also keeps its high-water capacity.
This supports a live-state/GC cost explanation, but no C4 heap-owner
measurement assigns the extra live bytes to that cache. Separate
after-GC heap values are unavailable in the sealed compact reader output.
Do not treat the old compact BaseOnly guard policy as a free transplant
to trie-backed keys. C4 also needs the notification-contract integration
proof before production use.

The source trie hash has finite collision families, but the C4 recording
does not show a dominant sampled trie-equality/tree-bin hotspot. That
hypothesis is not established as the cause of the CPU increase. No hash
change was made. The trie hash and D0 unchanged-burst reset policy predate
15 October; C4 extends the repeat-key lifetime.

The matched historical root pair keeps AA OFF. It can isolate structural
root expansion under that setting, but cannot reproduce or explain the
old AA-ON OOM by itself.

The closed historical conductor controls use the same fixed model and
heap. All reach run-1. The four COMPLETE rows complete and accept run-1:

| Source/control, statics ON and AA OFF | Run-1 engine | New initials | Static structural candidate attempts |
| --- | ---: | ---: | ---: |
| `27e22` plus runner controls, Hgood | 32.692 COMPLETE | 99,240 | 351,360 |
| `d330`, root/class expansion ON | 79.085 OOM | 751,492 | 2,814,687 |
| Same `d330` build, root expansion OFF, class expansion ON | 99.203 OOM | 616,332 | 1,534,018 |
| Same `d330` build, root and class expansion OFF | 149.046 COMPLETE | 97,900 | 138,955 |
| Same gates, old summary candidate eligibility | 146.955 COMPLETE | 97,900 | 131,400 |
| Same gates, coarse NORMAL root instead of residual partition | 53.241 COMPLETE | 97,848 | 138,501 |

The failed runs have no completed or accepted iteration. They include
17.342- and 17.535-second heap captures, respectively. Candidate attempts
are not unique initials. These are different failed work frontiers.
Disabling root expansion alone is insufficient. The remaining class-level
selection uses the same field-universe traversal. Turning both expansions
OFF restores completion. It does not restore the earlier speed, despite
almost the same number of new initials. No declaring-owner correctness
fix was reverted. These diagnostic gates can omit required structural
flows; they are not a production fix.

The completed both-OFF control receives 3,231,539 inputs, versus 994,189
in Hgood. Caller input groups rise from 641,471 to 2,606,400. Form builds,
call-graph counts and the method population match. Thus initial fact count
alone misses a large downstream relation. The frozen static request plan
has only 2,790 logical method rows and 5,580 memberships in this control.
Old request scan/output counters are larger, not smaller, in Hgood; some
counter positions also changed. A raw request-count explosion does not
explain this remaining loss.

The closed historical profiles locate the cost in ordinary packed FLOW
admission. `FlowGroup.add` occurs in 48.38% of execution samples and
67.29% of sampled allocation weight in the both-OFF control, versus 0.26%
and 0.24% in Hgood. `TrieNode.equals` alone takes 10.41% of execution
samples at the leaf. Total sampled allocation weight rises from 155.502
to 1,379.396 GB. The FLOW/trie source is unchanged between these revisions.
`MethodEdgeStore.add` occurs in 38.85% of execution samples, compared with
0.81% for the caller `AddedFactAccumulator.add`. These inclusive figures
overlap other stacks; they do not define exclusive cost shares.
GC interval union rises from 21.921 to 131.268 seconds; pause union rises
from 2.195 to 9.277 seconds. Concurrent GC intervals are not blocked engine
time. This is evidence of a changed submitted workload, not a new trie
merge implementation.

The remaining `4c` changes include ancestor root/class matching for static
field summaries and reconstruction of residual static complements with
different exclusions. They can add caller applications and different
packed FLOW alternatives while leaving initial count similar. Historical
field-exclusion alternatives must not be presented as current run-1
behavior, where field exclusions stay empty. The store already merges
same-root alternatives with different exclusions. Exclusion variation
alone does not prove the amplification; mixed roots, annotations and
repeated admissions need the isolated control below.

Hselect restores the old run-1 candidate relation in both transpose
indexes. It keeps the new projectors, residual complement and requests.
All 41 focused controls pass. The closed conductor measurement completes
and accepts run-1 in **146.955 seconds**, versus 149.046 seconds. New
initials stay at 97,900. Input arrivals are 3,204,606 versus 3,231,539;
caller groups are 2,594,515 versus 2,606,400. Thus almost all of the
expanded relation remains. This rules out candidate eligibility as its
dominant cause in this configuration; the small wall-time difference is
not a measured speed effect. The control can omit valid matches. A
separate control tests residual partitioning. The closed Hselect
profile confirms the negative result: `FlowGroup.add` remains at 47.02%
of execution samples and 68.13% of allocation weight. Total sampled
allocation weight is 1,363.803 GB, versus 1,379.396 GB in its reference.

Hresidual changes one branch: for a NORMAL special-root input and a
non-allStatic footprint, use the whole input instead of its complement
pieces. It keeps allStatic suppression, DEMAND handling, field/class
projectors, requests, declaring-owner repair, root-return evidence guards
and expanded candidate lookup. All 43 focused controls pass. The closed
run completes and accepts run-1 in **53.241 seconds**. Initials are 97,848
versus 97,900, but input arrivals fall to 932,916 from 3,231,539 and caller
groups to 522,141 from 2,606,400. This isolates a large relation multiplier
at the residual partition boundary. It does not prove that the coarse
replacement preserves semantics, or attribute an exact wall-time share
under the shared host.

For example, a NORMAL `S.* E{}` input with a footprint that owns `C.f`
and does not own the class point is partitioned into:

```text
before: S.* E{C}  +  S.C.* E{f}
control: S.* E{}
```

The roots differ, so same-root exclusion merging cannot collapse the
whole partition. The control also retains the owned field; it is an
intentionally coarse diagnostic, not exact old-27 summary semantics.

The closed profile confirms the changed work. `FlowGroup.add` falls from
48.38% to 0.10% of execution samples and from 67.29% to 0.44% of sampled
allocation weight. Total sampled allocation weight falls from 1,379.396
to 307.276 GB. GC interval union falls from 131.268 to 38.645 seconds,
and pause union from 9.277 to 3.056 seconds. `prepareStaticSummaryInputs`
now has a larger relative profile share because the FLOW merge work
collapsed; its own allocation weight falls from 222.855 to 165.849 GB.
Relative share alone would give the wrong conclusion.

The blocking profile does not show a long-monitor bottleneck in OWASP:
with both features OFF, recorded monitor-entry wait totals 0.244 blocked
thread-seconds. All-enabled OWASP has no recorded monitor-entry event.
The observed JFR threshold is 10 ms, so this does not exclude shorter
lock costs. Idle worker parking is not useful analyzer work. Thingsboard's
all-enabled failed prefix records 58.238 blocked thread-seconds at the
static service commit lock. Thread-time can overlap and is not wall time.

## What to target next

The highest measured memory target is current static-alias writer
publication and its downstream point relation. A fix must retain real
alias writes. C5 identifies two producer seams together; it does not
separate their costs. C6/C7 show that identity filtering alone is too
narrow.

The static-disabled target is work before storage admission: ordinary
AP transfers, cleaner field wrappers and call-form alias preparation.
The package-unit observer also shows limited parallel work in OWASP.
These costs cannot be removed by a static-field fix. Before a transfer
cache design, measure repeated complete kernel inputs across origins;
the source difference from BaseOnly does not give a project hit rate.
C4 demonstrates why less duplicate work alone does not guarantee less
engine time when a cache retains more state.

The historical structural expansion, old replay router and NORMAL
residual partition explain earlier regressions. Their removed versions
are not current rollback targets. The tests above do not justify
dropping required origin arms, changing cleaner semantics or applying
an unproved coarse static pass in production.

## Evidence

Artifacts are under
`/drive-testcomp/opentaint-go-rules/run1-causal-investigation-20261021`.
The key receipts are `execution/controller-terminal.json`,
`validation/matrix-control-review/closed16-argv-review.json`,
`validation/matrix-summary.json`, and `validation/matrix-profiles/terminal.json`.
The updated historical ledger is `validation/history/atomic-change-ledger-v2.md`.
It adds separate summary-eligibility and residual-complement units;
the original 70-unit ledger remains available.
Current source maps are in `validation/alias-static-writer-map` and
`validation/pure-transfer-origin-memo`. Rollback plans are in
`causal-controls/critical-a`, `critical-b`, `critical-c`, `critical-d` and
`critical-e`. Closed counter extraction is in `validation/causal-results-v8.json`.
The paired profile comparison is
`validation/causal-pair-jfr-v1/compact-closed-comparison.json`.
The three matched historical profiles are in
`validation/historical-structural-jfr-v1/compact-historical-comparison.json`.
The eligibility control comparison is in
`validation/historical-summary-eligibility-jfr-v1/comparison/compact-comparison.json`.
The residual control comparison is in
`validation/historical-static-residual-jfr-v1/comparison/compact-comparison.json`.
The exact point-site join and typed review are bound by
`validation/matrix-static-point-sites-review-v2/review/closed.json`.

Compact entry points:

- [All 16 matrix results](/drive-testcomp/opentaint-go-rules/run1-causal-investigation-20261021/validation/matrix-summary.json)
- [All 13 isolated controls](/drive-testcomp/opentaint-go-rules/run1-causal-investigation-20261021/validation/causal-results-v8.json)
- [Atomic source audit](/drive-testcomp/opentaint-go-rules/run1-causal-investigation-20261021/validation/history/atomic-change-ledger-v2.md)
- [Historical residual comparison](/drive-testcomp/opentaint-go-rules/run1-causal-investigation-20261021/validation/historical-static-residual-jfr-v1/comparison/compact-comparison.md)

Before this matrix, 56 closed raw captures and indexes from the previous
campaign were removed. Their hashes and compact analyses remain. The
cleanup freed 98,420,057,656 bytes. No active capture or user file was removed.
