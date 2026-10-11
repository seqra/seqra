# E01 fresh run shell

The shell now owns a fresh scope, routing table, counter, event queues
and actor maps for each run. A required typed handler factory supplies
actors and non-Work handlers. Later steps supply the method analyzer and
summary services. Results keep the concrete actor type and real counters.
The shell gate uses finite actors and empty or unknown-unit runs.

Accepted sends reserve before visibility. Rejected sends release their
reservation without adding to the handled count. Handler completion is
serialized with admission. A root-batch reservation stays live until all
roots are sent. Routing failures select FAILED before guard release.
Unknown units create no runner and no event reservation.

Each runner owns one queued-actor set. It rejects foreign actors, keeps
one reservation across partial Work and samples a new fixed priority on
requeue. Queue order uses zero work, event kind, completed steps and FIFO
ties. The first terminal status precedes cancellation. Every exit closes
admission and joins all runners. A failed join gives FAILED and no actor
stores. Only a successfully joined COMPLETE result freezes actors.

RunConfig rejects nonpositive indexes and limits. It owns roots and uses
a read-only live record view. Seeds own containers and sink paths while
retaining forward source edges. Delivery owns its publication list.
The context cache selects cached forms and graphs in both directions.
The JVM source delegates to the actual I06 cache.

## Tests and corrections

Tests were saved before their implementation. Missing-API logs are
`/tmp/bidi-e01-contract-api-red.log` and
`/tmp/bidi-e01-shell-api-red-2.log`. The latter reaches both module test
compilers and reports missing shell APIs plus the recorded fixture types.

The 74 focused tests cover configuration, seeds, protocol identities,
context delegation, concurrent counter releases, priority snapshots,
root/child routing, 200 concurrent accepted deliveries, delayed handlers,
quantum requeue, closed channels, throwable paths, status order, join
failure, freeze order and a fresh second run. The finite test actor had
an inverted completion flag in the first executed shell gate. The plan
records its correction before the edit; count and freeze assertions remain.
Other corrections use actual interface/list types and valid link layers.
No new Lean proof was required.

The focused gate passes (`/tmp/bidi-e01-focused-3.log`). E06 retains the
full scheduling and budget audit. In particular, root routing and setup
must consume the run timeout instead of receiving a fresh wait budget.
Program-analysis closure remains assigned to E02–E07 and V3.

## Final gate

The clean common/JVM gate and Go test-source, JVM SAST and application
compile checks pass in 77 seconds (`/tmp/bidi-e01-full-gate.log`).

| Suite | Classes | Tests | Failures / errors / skips |
|---|---:|---:|---|
| Common dataflow | 80 | 824 | 0 / 0 / 0 |
| JVM dataflow | 21 | 339 | 0 / 0 / 0 |

All 676 new common and 225 new JVM annotated tests have void JVM returns
and appear in fresh XML. Discovery logs are
`/tmp/bidi-e01-{common,jvm}-discovery.txt`; XML copies are under
`/tmp/bidi-e01/{opentaint-dataflow,opentaint-jvm-dataflow}/xml`.
`git diff --check` passes. E01 is complete.
