# V4: Phase 2 acceptance and API audit

The clean gate passes 1,226 common tests in 138 classes and 339 JVM tests in
21 classes. There are no failures, errors or skipped tests. All 1,078 new
common and 225 new JVM annotated tests compile to void and occur in fresh XML.
Go, JVM SAST and application test sources compile. Production code is unchanged
in this step.

## New acceptance evidence

Nine new tests close the remaining Phase 2 checks.

| Test owner | Checks |
|---|---|
| CallerStateAcceptanceTest (1) | Real acquire/release/after calls, caller static STATE and independent Result literals, native normal return, joint same-site confirmation, run 1 and independently seeded restricted run 3, empty records. The native run-1 sequence stops after both confirmations. |
| EarlierConfirmationStopTest (1) | Real joined F1/B2/F3 engines, then an explicit read-only final witness-store view carrying only DEMAND for an earlier confirmed key. Report retains the original confirmation; no sink seed is sent; STOP_RULE precedes demand-edge and policy checks. The witness-boundary injection is explicit. |
| HistoricalFixedResidualTest (3) | Matching END fixture, raw pre-F76 R3/Cross vectors and the native factory with normal must/E records. Compare independent historical and direct-current reports, source hits, seeds and raw records. |
| HistoricalMustProgramTest (4) | Old emitTX, cleaner and must/E restriction cells, plus actual G/C loops with independent historical SupX and both hand-off pattern sides. |
| AnyTaintIterationTest (existing 7) | The same G/C/I/S/SD/factory programs now run the historical backend and literal labelled golden report checks before comparison with the actual current driver. |

The default historical domain remains the earlier exact-source F71 domain.
Separate opt-ins admit END rules or refined F69 normal-must sources. END uses
F71 AP equations with the F67/F68 interpreter rules. Factory checks are labelled
F69/pre-F76. The G/C/I/S/SD golden findings cite the earlier D6X/DRXs results.
These finite tests do not establish a general theorem or a Lean END/factory
finding theorem. Historical support, emission, cleaner, restriction, hand-off
and record reversal compute their own expected values. They do not read current
selectors, demand indexes, support or report builders.

The first historical gate passed 32 of 33 tests. Its remaining assertion compared
regular CleanOut objects by identity; facts and request fields now have separate
assertions. Independent review then found three omitted old must/E restriction
cells. Their new test failed before the test helper correction. The normative
current implementation needed no change.

## Public API and ownership review

| Owners/APIs | Contract checked | Existing evidence |
|---|---|---|
| `RunConfig`, `RunStatus`/`RunResult`, `RunActor`, `RunEvent`, `RunnerPort`, `MethodServices`, `SharedObjects` | Positive field limits and valid run direction; owned roots/event/result lists; complete full method and caller keys; caller layer distinct from link/added layer; mandatory actual services; independent run status and counter. | `RunConfigTest`, `RunMethodHandlersTest`, `IndexCompletenessTest`, `RunShellTest`, lifetime/failure tests. |
| `MethodContextCache`, `RunMethodContext` | Directed cached forms and CFG, forward normal ends versus exceptional rules, backward zero starts at both exits, no backward context filter. Context-local graph/forms are retained for one actor. | I06 real JIR cache/graph/boundary tests, `ExitRulesTest`, `CutPointTest`. |
| `DeltaWorklist`, `EventQueue`, `InFlight` | Unchanged before ordinary work; cycle termination across quanta; queue rank/steps/FIFO snapshots; requeue refresh without another reservation; reserve before admission, release after handler, rejected-send rollback, underflow rejection before mutation. | `DeltaWorklistTest`, `EventQueueTest`, `InFlightTest`, `RunSchedulingTest`. |
| `RunMethodAnalyzer` handlers/work/freeze | Required service callbacks; exact accepted delta processing; raw storage before reduction; selection on raw headers; full-premise restricted publication; pending publication flush before counted Work ends; synchronous publication may add further work; joined freeze retains summaries/forward links/backward hits/counters and releases initials/edges/services. | `MethodWorkTest`, `ExitRulesTest`, `FactKindsTest`, `ModesTest`, `RunSchedulingTest`, `RunCleanupTest`. |
| `RunMethodLocalServices` | Run-1-only standing requests and full caller refs; concrete answer versus policy/static climb; all existing/new links participate; forward literal joins and ordered sink parts; end facts use zero premise and fired layer; backward independent seeds/hits; cancellation and released-owner rejection. | `RequestJoinTest`, `MethodLocalServicesTest`, `LocalConjunctionTest`, `SinkEndFactTest`, `EndFactTriggerTest`. |
| `RunMethodCallServices` | Actual staged call ordering; zero/balanced returns; exact subscription keys; ND full-member joins; native inside/applicable-only split; pending E retained through return actions/binding/aliases, demotion directly before CALL cut; record crossing counted before later removal; F76 views never pending/persisted; source hooks distinguish actual reverse execution from record reads. | `CallPlanTest`, `CallZeroTest`, `RecordReplayTest`, `RecordDemotionTest`, `NdJoinAdapterTest`, `CallSourceHitTest`, E07 fixed and seeded programs. |
| `PublicationIndex`, `CalleeSubscriptions`, global `matches` | Full member keys retained after path candidate lookup; K6/temporary/zero-only guards before admission; exact link/subscription delta rather than edge subsumption; same AP matcher for replay and delivery, no layer reclassification; lazy query cancellation. | `IndexCompletenessTest` full scans in all four modes, `AnyDeliveryTest`, actual 17-context `NaiveClosureFanoutTest`. |
| `SummaryStorage`, `SubscriptionManager`, protocol contracts/ports | Locked whole-batch publication/read, register before replay, insert before notification, current subscriptions at delivery, standing objects until joined release, immutable publication batches. Same-direction records and opposite-direction views have separate replay paths. | `SummaryStorageProtocolTest`, `AnyDeliveryTest`, E07 event-pool scheduler. |
| `RunMethodHandlers`, `UnitRunner`, `RunManager` | One owner per actor/subscription map; one queued-actor identity set; exact actor identity; counted remote sends and same-unit inline work scheduling; root phantom; one monotonic setup/routing/handling/wait deadline; first-terminal preservation, failed join overrides, complete-only freeze, joined-only owner release. | `RunMethodHandlersTest`, `RunShellTest`, `RunSchedulingTest`, `RunLifetimeTest`, `RunFailureTest`, `RunCleanupTest`, `RunJoinOverrideTest`. |
| `Support` | COMPLETE forward only; nonempty queries; least fixed point from root zero; all members supplied jointly at one full call site; both normal layers; exact or restricted same-mark must/E suppliers; FLOW/may exclusion; cancellation in constructor/query/confirmation. | D01: 18 primitive support tests, 20 independent must/E/mark cells in both modes, five actual joined-run tests. |
| `Report`/`ReportBuilder` | Latest complete forward DEMAND set; first CONFIRMED entry final; incomplete/backward runs neither add nor refute; complete witnesses retained, one staged map update, immutable report snapshots. | D01 `ReportTest` (12), D03/D04 incomplete-run and barrier-failure tests. |
| `HandOff`, `SeedIndex`, `Frontier` | Stored selected publications only; full member-to-pattern expansion; must-to-ANY/Empty E and abstract exclusion-to-STAR normalization; backward singleton/zero guards; actual cached sink alternatives and source identities; implicit zero excluded from insertion counts; immutable keys/counts without actor/result retention. | D02: 29 tests, including eight independent normalization rows and bounded frontier/count properties. |
| `IterationPolicy`, `FixedLimits`, `Next`, `IterationDriver` | Public initial limit positive/read once; later limits nondecreasing; fresh actual managers; one budget with freshly recomputed cap after policy; complete joined barriers; report→persist→hand-off→frontier→STOP_RULE/NO_DEMAND_EDGE/POLICY order; backward no stop callback; guard cancellation even after last checkpoint; local-only RunResult lifetime; immutable history. | D03: 23 tests, real 1–3 refinement and DLINK 1–5; D04 actual complete sequences and incomplete third-run preservation. |

No concrete production contract defect was identified in this source review.
Raw protocol DTOs are consumed through their specified canonical producer/owner
contracts; this review does not add guards or policies absent from the normative rules.

## P/Q protocol evidence

| Rule | Implementation evidence | Test evidence |
|---|---|---|
| P1 register before read | `SubscriptionManager.subscribe` registers in the callee store before scheduling replay. | Broken-P1 loss control and real deferred registration/read test. |
| P2 insert before notify | `SummaryStorage.publish` inserts its whole batch under the lock before the notification step reads handlers. | Broken-P2 loss control and inspection at notification boundary. |
| P3 linearizable replay read | Publication index updates and replay snapshots use the same storage lock. | Broken stale-read control and concurrent whole-batch snapshots. |
| P4 one matcher/complete candidates | Replay and delivery invoke global `matches`; indexes include full prefix/extension candidates and preserve premise/member annotations. | Broken predicate control; all-mode independent full-list scans; 17 subscriptions; seeded schedules. |
| P5 no removal during run | Publications, subscriptions and handlers remain standing until successful joined release. | Earlier links/publications reach later input; freeze/lifetime tests. |
| P6 match at delivery | `onDelivery` queries current runner-owned subscriptions. Notification touches no caller-local subscription map. | Delayed delivery sees a subscription added after notification; fixed E6 split replay/delivery. |
| Q1 count before send | Admission lock serializes reserve+channel/local admission and rollback for rejection. | InFlight and shell accepted/rejected/concurrent sends. |
| Q2 release after handler and sends | Handler completion releases under the same admission lock; partial Work keeps its reservation until worklist and publication flush are done. | Delayed-child sends, repeated partial quanta, real blocked publication with reentrant work. |
| Q3 guarded root batch | Phantom reservation spans root routing/admission and checks expiry before release. | Slow/unknown/known root batches and expiration after earlier root completion. |
| Q4 counter per run | Each manager owns its counter, scope, routing maps and status; the driver creates another manager for every run. | Old-counter isolation and fresh manager after aborted run. |

## Acceptance mapping

The V3/D01–D03 reports contain executed gate counts and saved RED/green logs.
D04 results are recorded in its report. The V4 additions below close the remaining Phase 2 rows.

| Inventory | Evidence/mapping |
|---|---|
| AP13; Analyzer 7, 32–34, 37–40, 43–45, 47 | D02 constructed hand-offs; D03 real refinement and DLINK; D04 `CurrentSequenceTest` WRAP five runs, worker loop, incomplete F3, END, CONJ; `CurrentModeIterationTest` shared getter and normalized cleaner; `CurrentModeActionsTest` concrete mark-changing pass and lowered named-field cleaner. Native record/raw selection checks are independent from callee demand. |
| AP17; Analyzer 22–23, 26 | D01 exact and must/E support tables, actual same-site conjunction confirmation; D04 `AnyTaintIterationTest` G/C/I, S/SD, broad-demand controls, factory F76, source-versus-pass and root EXACT recovery. Admitted/blocked fields and same concrete marks remain explicit. |
| AP18; Analyzer 42–47 | Shared two-mark getter has one backward FLOW premise; raw excluded cleaner marks survive while hand-off becomes STAR; restricted request sets stay empty; concrete pattern rejects abstract added marks; mark-changing pass and F74 outside-field results are pinned. `ConcreteDesignDifferentialTest` compares 24 generated families with labelled F71 plus one explicit excluded-mark barrier. V4 fixed comparisons close the named-program gap. |
| Analyzer 1–6 | E07 NaiveClosure/fixed schedules/408 seeded executions compare complete stored unions and full headers; P1–P6 negative controls, indexed full scans, fanout17, ND split, native/opposite replay, counter/fresh-run tests. |
| Analyzer 8 | Modes, real local/call services, cached reverse forms, JIR call transfer/plan tests, zero callees and aliases. No restricted request store/counter. |
| Analyzer 9, 12–13 | D03 stop/budget/failure/barrier tests; actual DLINK 1–5; D04 incomplete F3 preserves earlier CONFIRMED and DEMAND entries. V4 EarlierConfirmationStopTest isolates the final witness boundary and actual stop rule. |
| Analyzer 10 | Actual call/entry/exit/statement reversed source hooks, two sources of one statement both hit before duplicate zero storage, record replay creates no synthetic hit; D04 factory stays unseeded but its native record supplies the result. |
| Analyzer 14–15 | Vulnerability store full context/alternative headers, ordered sink parts and D01 confirmation; unchanged priority/cycles/dead-local sink and D04 nonreturning worker. |
| Analyzer 16–17 | Actual JIR native/mixed call resolution and default identity; demand end-fact layer/zero premise and result identity aliases versus normal identity suppression. |
| Analyzer 18 | Zero-premise static G2 removal and stored incomplete conjunction are covered by `ExitRulesTest`/`SinkEndFactTest`. V4 CallerStateAcceptanceTest covers actual acquire/release/after calls in runs 1 and 3. |
| Analyzer 21, 24–25 | Full local exit conjunction/E6, source versus may pass (including reversed DEMAND), full must/may/E group keys, pending/native/view records and aliases. |
| Analyzer 27–31 | AP reference/tree vectors, emission/restriction, cuts reject nonpositive limits, positive deep cuts, synthetic two-level strong-write acceptance, admitted/blocked readers, all primitive reaches and lowered field actions. The synthetic strong-write test does not claim JVM nested alias-write precision. |
| Analyzer 35–36, 41–42 | All native normal singleton shapes remain raw records without separate callee demand; full F76 views and mark-aware restriction; raw excluded entry-mark generic emission stays outside canonical DemandStore and is rejected at construction. |
| Analyzer 11, 19–20 | Reserved phase-3 regression, prescan release and output/status wiring. Not a V4 omission under its stated phase boundary. |


## Closed inventory and phase boundary

The caller-state, final-stop and fixed historical comparison gaps from the
read-only audit are closed by the tests above. D04 contains the matching
getter, mark-changing pass, primitive partial cleaner, normal cleaner, WRAP
and CONJ historical programs. V4 adds END, G/C/I/S/SD and factory.

The partial-field-cleaner validation wording is handled as recorded in D04:
F74 retains the outside STAR/E{f} part and the original mark. Tests require the
specified final g/k findings and absence of f. They do not claim that only one
concrete backward pattern can carry g. JVM entry, prescan release and output
remain J01–J03. Full project benchmarks and full trace resolution remain
phases 4 and 5.

## Commands and saved evidence

Run from core:

```sh
./gradlew --continue :opentaint-dataflow-core:opentaint-dataflow:cleanTest :opentaint-dataflow-core:opentaint-dataflow:test :opentaint-dataflow-core:opentaint-jvm-dataflow:cleanTest :opentaint-dataflow-core:opentaint-jvm-dataflow:test :opentaint-dataflow-core:opentaint-go-dataflow:compileTestKotlin :opentaint-jvm-sast-dataflow:compileTestKotlin :compileTestKotlin --console=plain
```

Build succeeds in 1 minute 21 seconds. Log: /tmp/bidi-v4-full-gate.log.
Fresh XML copies: /tmp/bidi-v4/opentaint-dataflow/xml and
/tmp/bidi-v4/opentaint-jvm-dataflow/xml. Discovery audits:
/tmp/bidi-v4-common-discovery.txt and /tmp/bidi-v4-jvm-discovery.txt.
Focused RED/green logs and XML are retained under /tmp/bidi-v4, including
historical-first-xml and must-restriction-red-xml. git diff --check passes.
