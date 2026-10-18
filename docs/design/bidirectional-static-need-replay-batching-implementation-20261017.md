# Static need replay batching: implementation map

Date: 17 October 2026. Implemented. Core checks pass; the application selection
has nine strict precision failures. Fresh fixed-configuration project runs
are closed; their resource and location gates are not passed. The
[project report](../reports/bidirectional-static-need-replay-batching-20261017.md)
records exact reached/completed/accepted iterations and current measurements.
See the [runtime validation](../reports/bidirectional-static-need-replay-batching-validation-20261017.md).
Contract: [replay batching specification](../../spec/run1-static-need-replay-batching.md).
The [proof dossier](../../spec/run1-static-need-replay-batching-proofs.md) gives
the checked finite model and the runtime refinement obligations.

## Owner turn

Each run-1 `UnitRunner` owns one `PendingNeedReplay`. Both runner ports expose
that exact object. A method key or a worker thread does not select its owner.

```kotlin
// Fresh actual FLOW admission, after directory registration:
port.pendingNeedReplay!!.graphChanged()

// Existing method-empty barrier:
val fresh = router.commitSourceUnions(method, rows)
port.pendingNeedReplay!!.sourcesCommitted(fresh)

// Common UnitRunner event / Work path:
val complete = handleEventOrWorkQuantum(event)
pendingNeedReplay?.flush(router)
if (complete) releaseParentReservation()
else requeueWithExistingReservation(event)
```

The source map remains cumulative under the router lock. Its commit returns
only fresh immutable marks. The pending map unions those deltas by source.
Ordinary Base requests keep their existing method-empty commit.

## Flush and exact sharing

```kotlin
val full = graphGrew
val deltas = sourceDeltas
graphGrew = false
sourceDeltas = LinkedHashMap()

val selected = if (full) router.sourcesSnapshot() else deltas
router.replayEqualMarkGroups(selected)
check(isEmpty()) // dispatch posts child visits; it does not run owner work
```

A dirty turn selects cumulative sources once. A source-only turn selects its
unioned deltas. The router groups nonempty mark sets by exact content. Its
temporary equality key checks cancellation during long content comparisons
and counts actual comparisons when diagnostics are enabled.

```kotlin
for ((marks, roots) in exactGroups) {
    val wave = StaticNeedWave(router, marks)
    for (root in roots) forward(wave, root)
}

fun forward(wave: StaticNeedWave, key: StaticNeedKey) {
    if (wave.enter(key)) dispatchToAllSubscribers(key, wave)
}
```

Each group gets fresh visited state. The shared atomic entry check precedes
the complete subscriber snapshot. Each recipient reads its existing actual
FLOW directories. Static targets continue the wave. Nonstatic targets use
the existing committed-R subtraction and counted Base delivery.

## Lifecycle and measurements

Pending replay keeps the current event or Work reservation until flush ends.
Flush also runs for incomplete Work, before requeue. Cancellation does not
flush from a `finally` block. Joined release clears the pending accumulator
and the existing run owners.

Diagnostics use numeric totals in the existing abstraction statistics. They
record binding changes, full/delta selections, selected rows, groups, waves,
node entries, subscriber deliveries, binding scans, Base offers and deliveries,
ordinary/source-reentry envelopes, and grouping and flush latency. They retain
no source keys, completed waves or per-wave history.

The active-work wave gauge counts root dispatch, deferred notifications and
accepted visit owners.
It does not measure JVM reachability after those owners finish. A heap dump
can give a JVM-live census at the capture point. Neither is an exact JVM-live
maximum over the entire run. A canceled run can abandon queued work; joined
release reports that scope before it resets the numeric gauge.

## Runtime refinement checks

The test adapter assigns an explicit owner turn to each event, Work quantum
and deferred protocol callback. It flushes that owner before finishing or
requeuing the turn. The independent scalar graph oracle expands each source
and mark separately; it does not call the grouping or batch-selection code.

Required validation includes incomplete Work, final-parent accounting, exact
content equality and hash collisions, source/graph arrival orders, late routes,
cycles, subscriber fan-out, owner independence, rejected children, cancellation,
and the existing request/bootstrap and semantic integration suites.

The finite Lean model does not prove the concurrent Kotlin scheduler. These
adapter checks and the fresh project runs provide separate evidence. Project
reports must identify the measured revision and latest reached, completed and
accepted forward iteration, including incomplete runs.
