# Thingsboard FULL run-1: work and retained state

Date: 23 October 2026. Scope: Thingsboard, run-1, FULL marks.
Measured runtime: `b68b3a1c9389c8d1b8ab6ad513af0c586a546895`.
Read the [paired report](bidirectional-static-summary-erasure-performance-20261022.md)
for the fixed commands, resources and comparison limits.

FULL is now the selected default for run-1. GROUP remains an explicit
experiment. Default change: `8ab1445b8167a4b2db73f97788653aa4e21a3933`.
This decision changes the absent-setting policy. It does not
change either mode's transfer rules. The measurements below used explicit
FULL on the measured runtime. They are not new measurements of the default
change.

All three FULL commands reach **run-1 OOM**. None has a COMPLETE iteration
or an accepted forward report. Their failed engine prefixes are 355.533 s,
303.285 s and 208.315 s. These times include diagnostic heap capture.
They are not completed-run timing results.

The main finding is excessive work and retained state before summary
publication. Removing S-to-S summaries did not remove their producer
effects, request processing or the ordinary nonstatic analysis. FULL also
keeps each full mark distinct. A smaller summary table therefore does not
imply a small run-1 workload.

## The FULL workload

These are executed prefixes. Do not use them as completed FULL/GROUP
performance ratios.

| Run-1 scope | FULL 1 | FULL 2 | FULL 3 |
| --- | ---: | ---: | ---: |
| Input arrivals | 3,892,895 | 4,027,379 | 4,059,792 |
| Fresh method initials | 488,219 | 497,852 | 489,522 |
| Committed fresh request memberships | 7,695,900 | 7,625,464 | 6,989,211 |
| Request member scans | 38,087,521 | 38,977,678 | 35,493,181 |
| Outgoing request memberships | 13,821,101 | 13,707,539 | 13,054,581 |
| Canonical static producer-supply leaves | 4,223,791 | 4,032,461 | 3,574,194 |
| Static distinct requested / supplied FULL atoms | 742 / 94 | 742 / 94 | 742 / 84 |
| Pending static memberships at END | 5,803,770 | 5,015,532 | 5,112,358 |

A request membership is scoped to a method slot or a saved static point.
It is not one globally unique mark. Equal mark names at different points
can produce millions of such memberships. The ordinary authoritative and
temporary request stores already reject duplicate memberships. Duplicate
offers must not be counted as fresh propagation.

The supplied-atom count is a failed-prefix observation. An unanswered atom
is not proved impossible: a later producer could supply it. Dropping all
currently unanswered requests would change the analysis.

For context, the first completed GROUP run-1 commits 666,043 fresh
request memberships and admits 321,765 fresh initials. The unfinished
FULL 1 prefix already commits 7,695,900 and admits 488,219. Request
membership work grows much more than fresh initial admission. This is
a comparison of scoped work counts, not a completed-run time ratio.

Method facts remain separated by their original premise and statement.
Output marks are already packed, but different FULL input premises still
have different relations. The FULL heap has 4,437,032 ConclusionGroup
objects and 7,905,482 TaintTree objects across all owning stores. Those
class totals are not unique semantic fact counts. Merging conclusions
from different origins would lose the input/output relation.

## Why S-to-S erasure leaves expensive work

The measured producer path is:

```text
executed local micro-edge or first retained caller binding
  -> bounded static result
  -> retainSummary(premise, result)
       -> computedGroups(premise, result)
            -> match the known static input
            -> apply the scalar summary
            -> field limit and result policy
            -> split into region/atom supplies
            -> append every supply to StaticBatch.supplies
       -> erase the S-to-S summary row
```

`computedGroups` runs before the erasure test and before raw-summary
admission. For erased rows, raw-summary deduplication never runs. The
global service removes equal memberships only when the actor commits its
batch. A quantum yield keeps the same batch. The actor must first reach
its existing queue-empty barrier.

This order preserves required effects. It also lets equal computed
supplies accumulate in an unmerged temporary list. The current summary
erasure rule is not an admission rule for those producer effects.

The [next-iteration proposal](../design/bidirectional-thingsboard-full-next-iteration-20261023.md)
separates removal of duplicate temporary payloads from removal of earlier
composition work. The second change needs a stronger proof.

## Exact duplicate supplies

New indexed queries retain the complete region key and the FULL atom ID.
Representation controls prove that the lists contain singleton atom
packets and that the path-content query covers every entry. All eleven
discovery queries validate successfully.

| Batch | Supply records | Complete regions | Distinct region/FULL-atom pairs | Duplicate records |
| --- | ---: | ---: | ---: | ---: |
| A | 974,206 | 13 | 351 | 973,855 |
| B | 794,986 | 13 | 338 | 794,648 |

Batch A has 27 distinct atoms at each of its 13 regions. Batch B has 26.
More than 99.95% of each list is duplicate region/atom membership. These
are exact counts, not the older bound from the total atom interner.

Removing just these 1,768,503 duplicates changes the snapshot-wide
2,841,377 supply records to at most 1,072,874 records: a **2.648x reduction
for this pending-record population**. A region-to-MarkSet map needs only
13 region rows for each large batch. These bounds do not establish a
whole-engine speedup or prove that the rest of run-1 fits in memory.

The two batches retain 168,545,936 and 136,931,120 bytes. Their full
retained contents are not all removable. Global static membership already
deduplicates these offers after commit; the problem is admission too late
to prevent the temporary payload backlog.

The actors are `AccessValidator.validateOtaPackage` and
`AccessValidator.validateDeviceProfile`. Both use the same 13 output
fields. Nine are generated `__spring_registry__` slots for these services:

```text
EdgeService                      EventService
TbCoreDeviceRpcService           TbApiUsageStateService
DeviceSessionCacheService        TbCoreToTransportService
CassandraBufferedRateWriteExecutor
TbClusterService                 TimeseriesService
```

The other four are `TbCallback.EMPTY`, `TbMsgCallback.EMPTY`,
`TenantId.tenants` and `TbMathNode.locks`. These are not the old enum-field
matrix. The map field types are `ConcurrentReferenceHashMap` and
`ConcurrentMap`. A static reference can be final while the referenced
object remains mutable.

The 27 FULL atoms include 18 XSS artificial/intermediate marks and nine
UNTRUSTED source marks from different rules. Runtime atom 243 names the
Spring path-traversal UNTRUSTED source mark. These are actual decoded atom
keys; they are not 27 owner groups. The complete region/atom census proves
the matrix. The output-list order alone does not prove which call or
alias micro-edge produced each individual record.

The failed IR-modifier query supplies no modifier counts. Do not label
all 13 fields writable or unwritable from that failed query. Likewise,
the presence of registry slots does not authorize removal of their alias
effects: the generated registry supplies globally available service
objects, and alias writeback can affect those referenced objects.

The frozen Spring model provides separate positive evidence: generated
registry fields are PUBLIC and STATIC, are not final, and their
initialization and dependency setup emit writes. The local alias stage
also lowers static output edges to `StaticOperation.Write`. This stage
can feed the producer pipeline above. First ordinary callee bindings can
promote a computed static output without creating a new writer. These
routes explain why registry output is possible; the batch does not retain
per-record provenance, so they do not classify every record as an alias
write.

## Retained state in the FULL 1 run-1 heap

The representative heap has 11,356,759,304 bytes of MAT live objects.
Its HPROF file is larger; file size is not live heap. The following
retained unions overlap. Do not add their bytes.

| Selected owner roots | Retained bytes |
| --- | ---: |
| Method execution stores | 1,770,136,208 |
| Summary and subscription stores | 1,772,220,536 |
| Global static service plus local point directories | 1,301,242,496 |
| Static point directories alone | 965,508,752 |
| Caller-input accumulators | 501,003,016 |
| Temporary StaticBatch objects | 521,812,144 |
| Ordinary RequestStorage objects | 48,609,312 |

The additive root partition puts 4,221,196,832 bytes under unit runners.
That includes method actors and durable stores, not only queued events.
There are only 8,806 LinkIn event objects in this heap. A pending-input
queue alone does not explain the FULL run-1 OOM.

There are 808,519 saved PointRows and 4,840,208 PendingFlowImages holders.
Those holders reference 1,113,722 distinct FLOW roots. The implementation
already shares immutable roots. Do not treat each holder as a separate
copy of a complete tree. The exact relation between requested marks and
FLOW images must remain intact.

The new numeric-class-ID queries give the actual point-site partition.
These counts classify PointRows, rather than unrelated instances of the
site classes. Each column sums to the complete same-heap total.

| Saved site | PointRows | Pending mark/image memberships |
| --- | ---: | ---: |
| Cleaner input | 782,080 | 782,080 |
| Call-stage input | 24,499 | 3,966,089 |
| Boundary input | 1,940 | 92,039 |
| Total | 808,519 | 4,840,208 |

Cleaners own 96.73% of the rows. Call-stage inputs own 81.94% of the
memberships, about 162 per row. These are different expansion patterns.
Optimizing cleaner rows alone does not remove the larger call-stage
membership population. Global root sharing does not prove that marks
within a particular point have equal images; that needs a per-point
cohort census.

The same snapshot proves at least 965,456 repeated successful form-slot
builds out of 1,885,879 successful builds. Soft payload reclamation can
therefore add work under memory pressure. This is a count bound, not a
CPU estimate or a justification for retaining all forms strongly.

IR storage remains outside this optimization scope. The Spring provider
subtree is also not a large ignored-action saving: the completed end-list
census measures 102,679,536 bytes across all allocated end lists, about
0.9% of this live heap; nonempty lists retain only 480 bytes.

## Executed work and the GC failure pattern

FULL has no GROUP projection path. The full recorded FULL 1 run-1 stacks
give the following inclusive scopes. Each row overlaps other rows.
Allocation weights estimate allocation work; they are not retained bytes.

| Ancestor context | Java CPU samples | Allocation weight |
| --- | ---: | ---: |
| Actual call-plan construction | 36.02% | 20.04% |
| Subscription replay | 25.04% | 40.28% |
| Cleaner chains | 12.55% | 24.10% |
| Complete summary applicability predicate | 6.099% | 120.691 GB |
| Static computed-group effects | 0.955% | 36.557 GB |
| Static actual-input composition | 0.691% | 32.509 GB |
| Static supply slicing | 0.0089% | 4.008 GB |

The explicit ancestor pass checks 32 old totals and call-plan/alias
controls. It reads the saved recording with a 192 MiB Java heap. It does
not attach to a running analyzer. In particular, merging temporary
supplies cannot be credited with removing all call, cleaner or replay
work. Earlier composition work is also a small measured CPU scope.

Each FULL attempt handles about 4.4–4.5 million events and admits about
0.49 million fresh initials before failure. The last 30 seconds before
heap capture contain 27.286, 27.549 and 27.472 seconds of GC pauses.
Soft-reference clear cycles become more frequent, and event throughput
falls to about 1.1–1.2 thousand events per second. There are about
15–17 thousand sampled unfinished reservations at this stage, not an
unbounded queue of millions of input events.

The three attempts allocate about 937–976 GB of sampled allocation weight
before failure, despite different elapsed times. Together with the
FULL 1 owner evidence and repeated form builds, this supports a failure
sequence of growing retained state, frequent reclamation, rebuild work
and low useful throughput. The heap dump is a later diagnostic pause;
it does not cause the preceding GC stalls.

Handled-event counts continue to advance before capture. The END
counters establish fresh admissions over the full prefix, not at each
late interval. These results do not identify an infinite unchanged-fact
queue loop. They are not a termination proof, and the saved counters
cannot classify every actor step as useful or duplicate. Only FULL 1 has
the detailed owner analysis used here; FULL 2 and 3 provide independent
pressure/profile observations.

## Why some cleaner requests cannot be removed

Ordinary generated return rewriters use EXACT selected-mark cleaners.
An empty cleaner path does not mean that the cleaner covers an abstract
subtree. For example:

```java
Object t = C.f;
cleanObjectMarkOnly(t); // rule: RemoveMark(T, t, EXACT)
use(t.child);
```

```text
Incoming relation: C.f/*/FLOW -> t/*/FLOW
Cleaner: remove T from the object t; do not clean its fields.

FLOW result: exclude T from the abstract relation and request T.
If C.f/T arrives: materialize t/[any]/T, DEMAND.
EXACT cleaner: keep a DEMAND complement for possible marked descendants.
```

Dropping that request can remove guidance needed for the marked descendant.
The current coarse field grammar makes this case possible even when the
initial value looked simple. Do not treat all selected-cleaner requests
as useless. Do not add grounding support or an actual-input field filter
to suppress them.

A FULL-specific bulk cleaner can instead preserve the complete mark
request set and the same spatial result while avoiding separate action
walks and resume rows for equal-geometry cleanup steps. That is a new
proposal with an explicit equivalence obligation. It is not implemented.

## Evidence and limits

Evidence root:
`/drive-testcomp/opentaint-go-rules/run1-static-summary-erasure-20261022`.
The follow-up reads saved recordings and the saved FULL 1 heap. It does
not change the measured runtime or run a new analyzer configuration.
New queries and analysis receipts are under
`validation/thingsboard-full-followup-20261023/`.

The final `findings.json` and `.md` bind the source, report and ZIP hashes.
New MAT sections comprise 24 validated results and two preserved failures.
The field-modifier query failed; separate frozen model source proves the
registry flags. The per-point root-cohort query also failed. It gives no
factorable count and is not used to predict image-store savings.

`full-run1-profile-source-analysis.md`, `profile-source-analysis-receipt.json`
and `interval-trends.json` bind the saved profile and interval analysis.
The full-depth cleaner scope is 12.5512% CPU and 24.1047% allocation
weight. Its first-five-frame control reproduces the old shallower
4.8235% and 12.6806% values with identical event denominators. It is not a
return-rewriter-only count. The equal-geometry pure-block share remains
unmeasured.

All three FULL attempts end in run-1. A GROUP run-2 heap cannot establish
the earlier GROUP run-1 peak or a causal mode difference. Allocation
weights are not retained bytes. Inclusive CPU contexts overlap.
An OOM prefix does not prove that the remaining closure is small or that
one selected change will make run-1 complete within 60 seconds.
