# Caller-owned run-1 subscriptions: implementation

Date: 18 October 2026. Status: core validation passed; application precision gate failed; project resource and output acceptance gates failed.
The [selected specification](../../spec/run1-caller-owned-subscriptions.md)
defines the required behavior. The
[proof dossier](../../spec/run1-caller-owned-subscriptions-proofs.md) defines
the formal scope and the runtime adapter obligations.

Measured runtime: `63d5481e4e4b69ecf042c1149bb574ad40fd1624`.
Latest reached / completed engine / accepted forward report: OWASP
**run-7 / run-6 / run-5**, conductor **run-1 / none / none**, ThingsBoard
**run-1 / none / none**, and Stirling-PDF **run-2 / run-1 / run-1**.
OWASP and ThingsBoard timed out; conductor and PDF stopped with OOM.
All four miss the 60-second run-1 engine target. The fixed matrix and
80-check/41-dataset input audit are closed. The
[current performance report](../reports/bidirectional-caller-owned-subscriptions-performance-20261018.md)
records strict finding differences, CPU evidence and memory analysis.
The full core gate passes, but application precision and project no-loss
acceptance fail. These measurements do not prove a semantic transfer loss
or a universal saving. IR and cache/reference-manager rewrites remain outside
implementation scope.

The mixed Base/static batch correction is committed and passes the full core
gate. No implementation exception recurred in the corrected project matrix.
Main uses the legacy engine and has no numbered bidirectional iterations.

## Input admission and entry descriptors

`SubscriptionManager` admits an actual input into the existing caller input
relation. A structural directory key includes both full method keys, the
statement and the call stage. Original premises and individual layers remain
inside that relation. Compatible NORMAL and DEMAND ND members share the same
structural directory.

```kotlin
val entry = directory(sub.callSiteKey)
val admitted = entry.admit(sub) ?: return
val oldAnswers = entry.answeredRows(admitted.fresh.added)
val prepared = prepareRun1Initials(ops, profile, sub.ref, admitted.fresh.added)
ensureRequestStreams(admitted.fresh, entry, prepared)
send(PreparedInitialsIn(sub.callee, prepared))
replayOrdinarySummaries(admitted.fresh)
joinRequests(oldAnswers, admitted.fresh)
```

The caller runs the existing coordinate normalizer. It sends canonical entry
descriptors to the callee. The callee does not repeat input projection and
does not retain the caller's raw input. A repeated admitted input sends no
entry batch. A fresh mark or header delta retains its ordinary joins.

Static packed abstract and concrete diagonal descriptors retain their
existing transport paths. Actual static marks remain eager. Nonstatic
concrete starts require the caller's actual mark and a matching request.
An ANY supplier still gives a STAR initial tag. The initial grammar has no
ANY initial and no static-root initial.

`PreparedInitialsIn` also carries input presence. Presence initializes the
callee and installs its ordinary stored `S.* -> S.* [DEMAND]` default even
when the input gives no legal initial. It retains the DEMAND-input flag.
The callee applies the ordinary start rules and initial-store deduplication.

## Method requests and callsite requests

The same `RequestStorage` class stores Base and static-field mark sets.
Each method has one temporary outgoing table and one committed table in its
`SummaryStorage`.

```kotlin
temporary.addAll(slot, offeredMarks)
// At the method fact-queue-empty barrier:
summaryStorage.commit(temporary)
temporary = RequestStorage()
```

The summary store registers a static source reference before it publishes
its first delta. The directory holds a getter for the committed row. It does
not hold another cumulative source mark set. Exact request streams have
their own subscriber references. Summary subscribers alone do not receive
request notifications.

Each structural callsite has pending and answered request tables:

```kotlin
pending.addAll(slot, offeredMarks - answered[slot])
// At the caller's method-empty barrier:
val delta = answered.commit(pending)
pending = RequestStorage()
joinCurrentInputs(delta)
```

Answered means processed against current inputs with late-input service
installed. It does not mean that an actual mark or a summary was found.
The two frontiers are fresh inputs against old answered marks, and fresh
request marks against current inputs. Capture the old answered snapshot
at input admission, before a deferred replay can see later request commits.

The runner keeps one counted drain wake per caller. If the caller already
has Work, its existing Work reservation owns the pending requests until that
method empties. An idle caller handles the wake at message priority. It does
not wait for the whole unit queue to empty. The drain visits only pending
callsites and groups their transient effects by destination before sending.
It retains no second input history or output memo.

The input index can visit a concrete Base input with a merged batch that
also contains static-field rows. The concrete helper selects rows in place:

```kotlin
if (input !is TaintTree || input.base == STATIC) return
for (row in rows) {
    val slot = row.slot as? Run1Slot.Base ?: continue
    if (slot.base != input.base) continue
    // Intersect this row with actual branch marks and emit its legal tag.
}
```

This corrects the former assertion that every batch row must be Base.
For example, actual `b/{T,U}`, Base request `b/{T,V}` and static request
`C.f/{U}` can start only `b/T`. They cannot start `b/U` or absent `b/V`.
The static row still joins a productive static FLOW input in the same
batch. Static concrete admission remains eager. The correction makes no
new request copy, changes no initial shape and adds no propagation policy.

## Fixed graph and shared static views

`Run1StaticProfiles` exposes the SCCs and fixed DAG already built after
prescan. `FixedPrescanReachability` reuses them. Reusable pair answers depend
on method SCCs. They do not depend on fields or marks.

```text
authoritative source: (full source method, field) -> committed request row
shared view:          (full callee method, field) -> merged marks + subscribers
directory:            field -> SCC -> source/view references
```

Source registration and view registration both install the reference before
reading the other side's snapshot. Deltas union into the shared view before
subscriber fanout. Source getters and receiver callbacks run outside the
directory lock. Callbacks acquire counted caller-owner delivery before they
return. A snapshot/delta race can repeat an offer; the receiving union rejects
the duplicate.

Only a certified same-field static request relay can omit its backward join.
The certificate requires actual entry ancestry and a known direct prescan
edge. Packed entry geometry supplies the ancestry check. Merged packed rows
retain a certificate only if all merged parts are certified. A changed-field
or Base-original input keeps its productive join. Unknown calls keep their
ordinary request work. A shared view can still serve their actual incoming
binding; graph reachability does not prove actual fact reachability.

The production run no longer constructs a `StaticNeedRouter`, wave, visited
set or `PendingNeedReplay`. Historical protocol types remain available to
tests. No runtime `graphChanged` or full-source replay is scheduled.

## Summary matching and later runs

Ordinary summary storage, full AP matching and ND support remain in use.
Call-stage coordinates are retained in subscription headers and ND joins.
Summary delivery captures all current candidate values before any callback
can grow the caller directories. New inputs replay stored answers and later
answers replay all matching current inputs.

Static-related facts remain DEMAND. Strong field overwrites retain the
selected Empty-E run-1 rule. The ordinary default summary, eager static-mark
admission, identity compaction and persistence of NORMAL summaries remain in
use. Restricted runs retain their existing abstraction and record-resume
behavior. Backward ZERO subscriptions retain the reversed call stage.

## Validation and measurement gates

The full common suite passes **1,607 tests** and the full JVM suite passes
**411 tests**, with no failures, errors or skips. The analyzer jar builds.
Evidence is in
`/drive-testcomp/opentaint-go-rules/caller-owned-subscriptions-20261018/validation/full-core-8/`.
The fresh application selection has **148 passes, nine precision failures
and one skip**. It is not a green acceptance gate. The failure packet is
`validation/application-2/` under the same evidence root. Assertions and
expected findings were not weakened. Fixed-configuration candidate runs and input equality checks are closed;
the linked performance report records the failed project gates.

Validation covers source/view registration order, late subscribers,
cycles, field changes, Base/static reentry, actual-mark evidence, both request
frontiers, full call stages, mixed ND layers, presence-only input, cancellation
and counted completion. Existing multi-run differential and application
checks remain in the gate.

Project runs must use the committed implementation, direct
`runProjectAnalyzer`, the fixed task configurations, fresh main controls,
profiles and MemoryManager OOM snapshots. Each report must state the latest
reached, completed and accepted iteration. Source stability and identical
input manifests are separate gates from semantic location comparison.

The Lean results prove finite-calculus routing and summary coverage. Runtime
tests and measurements must check the Kotlin adapters. Neither the formal
models nor source review prove a universal time or memory gain.
