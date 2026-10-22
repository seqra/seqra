# Thingsboard run-1: avoidable static GROUP work

Date: 22 October 2026. Status: source investigation and local model closed;
runtime implementation and new project measurements not performed.
Runtime: `a4bf789866f912ebefc54bca42d809c89beea2d4`.
Documentation commit `0a55060ec` does not change that runtime.

**Latest reached: run-1 OOM. Latest completed: none. Latest accepted
report: none.** Engine time: 260.838122588 seconds, including its
28.863-second diagnostic heap capture. Use the existing 12g / 1080-second
configuration. This report analyzes the closed capture; it does not
claim a new run or extrapolate a successful completion time.

The [performance report](bidirectional-local-static-writers-aliases-performance-20261022.md)
contains the full campaign. The
[revised proposal](../design/bidirectional-local-static-writers-aliases-next-iteration-20261022.md)
now focuses on this run-1 workload. Conductor's 18.134-second and PDF's
25.089-second completed/accepted run-1 times are acceptable for the
current investigation. Their later run-2 OOMs are outside this scope.

## 1. Main finding

The current implementation rechecks whether a static GROUP original is
available **after that availability was required to create the original**.
It does this at static-premise call application and at computed static
group output. These checks do not discover a new group. They create
continuations, service interests, actor replies and AP self-compositions.

The call path also converts a packed summary into one scalar publication
per output leaf. It then executes the caller suffix separately for those
leaves. Keeping the packed relation can remove a large multiplicative
factor. This is work elimination, not a faster map or trie operation.

It does not require an exit-rule change. The proposed ZERO restriction
is **withdrawn**. Controller exit checks must retain controller annotation
selection. Current source, exit and Spring behavior remains the reference.

## 2. What the measurements establish

Evidence root:
`/drive-testcomp/opentaint-go-rules/run1-local-writers-aliases-20261022`.
The live-heap source and all 19 query bindings are recorded in
`validation/combined-thingsboard-heap-review.{md,json}`.

| Run-1 evidence | Observed value |
| --- | ---: |
| Actor EdgeDelta steps | 48,073,520 |
| Cumulative static owner replies | 19,894,416 |
| Lower bound on PointReady replies | 19,657,611, or 98.81% |
| Pending call continuations | 304,292 |
| Call continuation keys | 304,292 |
| Scalar Publications in static/point retained selection | 304,292 |
| TaintTrees in that retained selection | 304,292 |
| Pending fixed-group conditional joins | 10,276 |
| Other pending operation-point rows | 226,498 |
| Saved FLOW images | 618,263 |
| Physical FLOW writer rows | 5,446 |

The reply lower bound subtracts at most 58,875 ReadReady candidate visits
and 177,930 WriterInterest admissions. It does **not** establish that all
PointReady replies belong to calls. Real operation-point refinements,
writer inputs and class replies share that event type.

The 30-second normal window is in run-1. Static owner handling has
12.82% native sample ancestry; static call supply has 9.38%;
`publishMatched` has 2.76%. These shares overlap and include required
work. They are not wall-time savings. The retained JFR estimates about
1,065.692 decimal GB of allocation, with final-endpoint coverage uncertain
at metadata rounding precision.

Reachable heap is 11,441,129,264 bytes. Actor/edge work retains
3,910,286,992 bytes; publication/subscription retains 1,388,957,272;
static service and point engines retain 651,113,304. These are overlapping
unions. Do not add them or call all static bytes removable. Ordinary
PointRow and service interests still perform real discovery.

The source allocates a scalar TaintTree and Publication for each call
continuation. Thus the 304,292 cohort is a concrete standing cost of the
protocol being considered for removal. It is not an estimate of repeated
equal stage inputs or a count of unique current scalar facts.

## 3. The causal invariant

Consider:

```java
Object get() { return C.f; }
Object wrap() { return get(); }
```

Before a group is known, a reached read can produce
`C.f/*/FLOW -> return/*/FLOW, DEMAND`. A check can request G.
Only an actual pool reply creates the concrete original:

```text
pool request C.f/G answered
    -> C.f/*/G original
    -> C.f/*/G -> return/./G summary
    -> summary notification at wrap
```

The constructors are explicit:

- `Run1Abstraction.emit` rejects STATIC entry inputs.
- `Run1PointResume.ReadReady` creates a FLOW original.
- `concreteInitial` in direct replies and FLOW-image materialization
  uses the supplied RegionGroups.
- `RunMethodCallServices.staticSupply` also constructs its selected
  static original from the supplied input.
- Ordinary transfer, wrapper promotion and all-origin callbacks preserve
  an existing original, or use ZERO. They do not manufacture another
  static GROUP original. Executable run-1 ND premises are forbidden.

For a field, PointReady follows the transition to `processed`. Therefore
a summary's concrete static original proves both an existing supply and
an earlier request. This matters: availability in `reach` alone would
not justify deletion, because its first request must wake FLOW writers.
The earlier processed transition already admitted and secured the required
writer-interest deliveries. They can still be queued and must retain
their counted work. Later writers also read processed groups during
registration. The skipped request adds no fresh writer/group admission.

Exact class markers retain their own supplied/classRequested witness.
They do not supply a static field. No class-wide STAR or S-root initial
is introduced. The optimization must preserve the exact marker route.

These are derivation invariants of the current selected approximation.
They do not add GroundingSupport, validate a rule's concrete-program
guard or change the current GEN policy. An unseeded copy cycle still
cannot create its first GROUP original. A configured source accepted by
the current engine remains a seed under that same engine contract.

## 4. Current work versus removable work

At the ZERO control activation of the published summary, current code
does this:

```text
Run1PointResume.callInterest:
    for every output leaf:
        copy scalar publication
        create CallContinuation
        append interest for the SAME original C.f/G
method barrier -> service.request -> immediate processed-group answer
PointReady -> staticSupply -> staticFacts -> satisfying
           -> applyParts -> applySummary -> caller suffix
```

No new semantic fact is needed between the publication and that reply.
The pending continuation can still wait for a method barrier, so many
logically answerable rows can accumulate.

New work can be:

```text
published static GROUP summary
    -> existing packed alias partition
    -> same caller suffix, under the SAME static original
```

Never turn the static original into ZERO. Static facts still do not
travel through ordinary intra-procedural CFG edges. A relation such as
`C.f/G -> local/G` has a static **original** and a nonstatic current fact;
the current call protocol handles those relations despite the absence
of ordinary static-current propagation.

There is an additional AP obligation. For the actual production shapes,
the old supplied input equals `startFact(original)` at DEMAND. Applying
the summary to that same canonical input must reproduce the raw result,
including tails, mark/path membership, layer and alias classification.
The source audit supports this identity. It needs direct AP controls
before runtime implementation. Other legal shapes retain the old path
until proved; the optimization must not narrow the fact grammar.

The second unnecessary protocol is `computedGroups` for an all-static
GROUP original. It creates one unary conditional join per output leaf,
requests that already-processed original again and self-composes before
publishing the static output. The same invariant can remove that join.
Keep the actual output supply and real first-request/writer behavior.

## 5. Large work bounds, with their scope

For a compatible packed publication with L output leaves:

| Operation | Current | Proposed |
| --- | ---: | ---: |
| Call continuations / scalar publication copies | L | 0 |
| Premise interests / PointReady replies | L | 0 |
| Canonical input materializations / self-compositions | L | 0 |
| Caller suffix inputs | L | At most two packed alias arms |
| Actual output memberships / demand coordinates | All | All |

For a 32-leaf nonstatic output with one alias arm, suffix inputs change
from 32 to 1. With N wrappers, the protocol currently repeats that work
at every applicable call. The direct path retains those method summaries
and their demand pairs; it removes re-proving their premise and avoids
scalarizing their output. It does not remove all call-boundary work.

For L fresh static output leaves, the unary computed-supply protocol
likewise changes from L joins/interests/replies/self-compositions to zero,
with all output memberships retained.

This provides a source-backed mechanism for a large reduction. It does
not prove Thingsboard's leaf-width distribution or a twofold reduction in
total engine time. No measured project speedup is available without an
implementation run. In particular, do not count every one of the 19.7
million PointReady replies as removable call work.

Packed suffix execution must preserve complete rule joins, negative
conditions, sources and cleaner order. If that adapter is not additive,
retain scalar suffix execution and remove only the proven redundant
handshake. No rule-semantic change is authorized to obtain the bound.

## 6. Checked model and remaining obligations

[Run1StaticPremiseAvailability.lean](../../spec/lean/ApSpec/Run1StaticPremiseAvailability.lean)
passes the focused Lean 4.33.1 check. All **24 executable controls** pass;
the owned-module audit checks **533 declarations** with no unexpected
axioms. The audit permits only the stated `propext`/`Quot.sound`
dependencies, with no classical choice, placeholder or native decision
axiom. Exact commands, hashes and producer mapping are in
`validation/run1-static-premise-availability-source.{md,json}`.

Checked scope includes causal supplied/processed provenance, preserved
originals, wrapper promotion, both arrival orders, unseeded cycles,
first-request effects, repeated field/class request effects and decoded
packed output observations. Canonical AP self-application and call-stage
distributivity remain explicit adapter hypotheses. This is not a proof
of whole-engine equivalence, complete run-2 recovery, or termination.

Before code changes, close those adapters and test raw alias partition,
late writers, all original shapes, actual sources/sinks and every selected
method demand pair. Preserve current exit/Spring behavior. Keep canceled
runs from reporting COMPLETE with unfinished direct effects.

The caller-origin source audit in
`validation/run1-thingsboard-caller-origin-audit.md` identifies two further
possibilities: admit equal internal stage inputs before execution, and
avoid LinkIn when caller subscription admission has no fresh delta.
Neither has a measured workload factor yet. They should not distract
from eliminating the already-answered GROUP protocols first.

No new heap parse, profiler decode, analyzer run or runtime edit was
needed for this investigation. Existing compact evidence was reused.
The next measurement must count call-handshake and conditional-join
removal, packed suffix inputs, actual EdgeDelta work and unchanged demand
closure. The output must include latest reached/completed/accepted run.
