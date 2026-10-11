# E02 method-local transfer and boundaries

The method actor now owns one run's links, initials, edges, summaries and
backward source hits. It uses the existing runner queue and count protocol.
Required factories bind local request/sink services and call services in later
steps. A required publication sink receives owned immutable batches. No default
service discards an operation. The finite E02 adapters implement their tested
operations and reject unsupported calls and conjunctions.

Initial emission uses run-1 policy or the current demand. Initials deduplicate
exactly. Run 1 and backward runs reject must initials before store mutation.
Forward starts use entry rules. Backward zero starts use both exits; nonzero
requirements use normal exits. Statement transfer precedes end rules. Exceptional
forward exits run rules and sinks but make no summary.

All three application modes use the actual AP algebra. Generated results take
their result filters before cuts. Unchanged propagation retains the processed
Facts object, bypasses storage and statement cuts, and uses no liveness check.
Its repeat set lasts across Work quanta and resets after a drained burst.

Normal exits remove evaluated zero-premise statics and all entry-mark leaves on
zero-premise this/argument values. Raw summaries are stored before restriction.
Publication reduction retains complete premise keys and layers. Raw demand,
normal multi-premise and backward-zero selection controls stored demand pieces.
Every accepted DEMAND link, method delta and summary delta contributes to the
stop-rule counter, including a link that makes no new initial work.

Work returns completion only after all work and pending publications drain.
Synchronous publication can add more work. Publication failures propagate and
retain the pending batch. Cancellation checks cover processing, skipped GEN
candidates, boundary matching and the final publication callback. Freeze retains
forward links, summaries, backward source hits and counters; it drops run ports,
contexts, services, queues, edges, initials and backward links.

## Test evidence

Tests were saved before the corresponding handlers. The first missing-API test
compile is `/tmp/bidi-e02-api-red.log`. Later staged missing-analyzer checks
(`/tmp/bidi-e02-core-api-red.log`, `/tmp/bidi-e02-handlers-api-red.log`) stop in
the main compiler because the required service contracts name the absent actor.
They are not claimed as test-compiler failures.

The first executed gate passed 53 of 56 tests
(`/tmp/bidi-e02-focused-3.log`, saved XML `/tmp/bidi-e02-focused-red/xml`). Two
failures reproduce the final-publication and skipped-GEN cancellation defects.
The third exposed an incorrect STAGE/GEN static expectation: those modes take
the ordinary admitted above-premise row and produce abstract ANY in DEMAND.
The statement-only position request assertion remains unchanged. The plan
records these corrections before edits. Earlier compile corrections supply
explicit demand flags, valid list types, mutable collector access and link layers.

All 57 focused tests then passed (`/tmp/bidi-e02-focused-4.log`). Four final
integration rows cover an unreachable forward end, a new caller of the same
DEMAND added fact, actual exit-rule overflow at limit one, and direct restricted
request rejection. No new Lean proof was needed.

## Validation gate

The clean common/JVM gate and Go test-source, JVM SAST and application compile
checks pass in 76 seconds (`/tmp/bidi-e02-full-gate.log`). It ran 881 common and
339 JVM tests with no failures, errors or skips. The final common gate includes
all 61 E02 tests and passes in 31 seconds
(`/tmp/bidi-e02-final-common-gate.log`).

| Suite | Classes | Tests | Failures / errors / skips |
|---|---:|---:|---|
| Common dataflow | 88 | 885 | 0 / 0 / 0 |
| JVM dataflow | 21 | 339 | 0 / 0 / 0 |

All 737 new common and 225 new JVM annotated tests have void JVM returns and
appear in fresh XML. Discovery logs are `/tmp/bidi-e02-{common,jvm}-discovery.txt`.
XML copies are under `/tmp/bidi-e02/{opentaint-dataflow,opentaint-jvm-dataflow}/xml`.
`git diff --check` passes. E02 is complete. E03–E05 supply the remaining real
local, summary and call services; E07 owns the full closure oracle.
