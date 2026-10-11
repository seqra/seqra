# V3 one-run analyzer validation

The actual one-run analyzer has fixed and seeded closure coverage in both
directions, run 1 and restricted modes, and both unit layouts. E07 adds
full-context finite programs and compares all stored observations under
408 seeded executions. This gate adds five independent acceptance rows:

1. A dead local passes an unrelated statement and reaches an exit sink.
2. A synthetic two-field strong write retains two normal must prefixes,
   rejects the overwritten sink and admits both sibling sinks.
3. A setter exclusion blocks its reader and sink; a sibling reader retains
   a normal must result and reaches its sink.
4. All three primitive cleaner reaches run at a call. Their three sinks
   retain the specified normal or demand layers.
5. A normal exact singleton remains raw. Restricted publication requires
   the matching exit mark; run 1 publishes it independently of that demand.

All five pass (`/tmp/bidi-v3-acceptance-green.log`). The first gate has one
malformed read fixture (`/tmp/bidi-v3-acceptance-first.log`, RED XML in
`/tmp/bidi-v3-acceptance-red/xml`). It omitted the read operand from the
touched set. Interpreter I1/AP S10 require every micro edge to read a
touched base. The plan records the correction first: include the operand
and written bases, its keep edge and both readers. The independent expected
facts and sink layers stay unchanged. No production correction is needed.

## One-run inventory

The numbers below refer to the Analyzer section of `validation-plan.md`.
Driver-dependent assertions remain assigned to D01–D04 and V4.

| Items | Enabled one-run coverage |
|---|---|
| 1 | `ScheduleFuzzerTest`, `NaiveClosureTest` and `ClosureUnionObservationTest`: complete unions, reproducible schedules and full observation headers. |
| 2 | `SummaryStorageProtocolTest`: broken P1–P4 controls, real arrival orders and P5–P6 lifetime. |
| 3 | `IndexCompletenessTest`, `NaiveClosureFanoutTest`: precise/may/must/E/mark candidates, full scans and actual delivery to 17 contexts. |
| 4–6 | `NdJoinAdapterTest`, `AnyDeliveryTest`, `RecordReplayTest`, `RunShellTest`, `RunLifetimeTest`: replay, delivery, F76 views and fresh runs. |
| 8 | `ModesTest`, `MethodLocalServicesTest`, `CallPlanTest`, `CallZeroTest`, `JIRCallTransferTest`: concrete FLOW, unresolved and zero call paths, source/pass order and reversal. |
| 12 | `RunLifetimeTest`, `RunFailureTest`, `RunCleanupTest`, `RunJoinOverrideTest`: status priority, joined owners and failure-safe release. Barrier/policy/stop checks follow in D03/V4. |
| 14 | `VulnerabilityStoreTest`, `SinkEndFactTest`: context/alternative/run shapes and concurrent lossless merges. Confirmation follows in D01. |
| 15 | `DeltaWorklistTest`, `MethodWorkTest`, `OneRunAcceptanceTest`: priorities, repeated/cyclic unchanged work, partial quanta and dead-local sink reach. |
| 16–18 | JVM call-plan/transfer tests, `CallPlanTest`, `SinkEndFactTest`, `ExitRulesTest`, `RecordReplayTest`: empty/unresolved/mixed callees, effective ends, backward record triggers and stored static conjunction parts. |
| 21 | `LocalConjunctionTest`, `NdJoinAdapterTest`, `NaiveClosureTest`: full premise unions, restricted E, context and same-call isolation, mixed layers and split replay/delivery. |
| 25 | `FactKindsTest`, `RecordDemotionTest`, `AnyDeliveryTest`, `NaiveClosureTest`: full premise keys, late pending demotion, aliases, native zero and current record views. |
| 26 | `VulnerabilityStoreTest` covers local supplier tables. Joint support and confirmation follow in D01/V4. |
| 27 | `EmissionTest`, `ApplyEdgeVectorsTest`, `CleanerTest`, `MarkCheckTest`, `LocalConjunctionTest`, `JIRCallTransferTest`: normal/must/may and marked transfer/reversal contracts. |
| 28–31 | Builder/cleaner/field-limit/cut tests and `OneRunAcceptanceTest`: synthetic strong-write outputs and sinks, excluded/admitted reads, all cleaner reaches and positive public limits. Run 1 starts at limit 1. |
| 35–36 | `DemandSelectionTest`, `RunSummaryStoreTest`, `RecordStoreTest`, `AnyDeliveryTest`, `ExitRulesTest`, `RestrictionTest`, `ReferenceOperationsTest`: selected/raw separation, record persistence, native/view kinds and full mark-aware restriction. Hand-off construction follows in D02. |
| 41 | `FactKindsTest`, `OneRunAcceptanceTest`: raw DEMAND and normal singleton storage, admitted/rejected publication marks. |
| 42 | Generic raw emission vector only. Canonical `DemandStore.Builder` rejects excluded abstract marks. This input is not a reachable stored demand. |
| 43–45 | Local mode/emission/restriction and cleaner controls pass. Actual two-mark getter, normalized hand-off and repeated cleaner/mark-changing sequences follow in D02–D04/V4. |

## Protocol and representation review

Publication updates its locked whole map before notification. Subscription
registration precedes replay. Replay reads a locked whole snapshot, and
delivery reads current subscriptions. Subscribers and publications remain
standing until joined release. P1–P6 tests and the fixed/fuzzed full programs
exercise these boundaries.

Raw summaries and selected demand groups stay separate. Stored keys retain
complete premises, E, marks and layers. NORMAL must cannot absorb exact;
correlated FLOW cannot absorb a descendant. Stored comparisons use whole
unions, including valid T2 partitions at downstream links and sinks. Exact
raw DTOs and store annotations remain available. Pending native records
retain E through RETURNED actions and bindings, then demote immediately
before the call cut. Views remain nonpending. Source hits retain actual
reversed source identity. Joined freeze exposes only the specified retained
summaries, forward links, backward hits and counters.

No unresolved one-run integration defect remains in this tested scope.
Global support, constructed hand-offs and iteration are the next steps.

## Validation gate

The clean common/JVM gate and Go test-source, JVM SAST and application
compile checks pass in 78 seconds (`/tmp/bidi-v3-full-gate.log`). All 1,088
common tests in 113 classes and 339 JVM tests in 21 classes pass. No test
fails, errors or skips. All 940 new common and 225 new JVM annotated tests
have void returns and appear in fresh XML. Discovery logs are
`/tmp/bidi-v3-{common,jvm}-discovery.txt`; XML is saved under
`/tmp/bidi-v3/{opentaint-dataflow,opentaint-jvm-dataflow}/xml`.
`git diff --check` passes. No new Lean clarification is needed. V3 is complete.
