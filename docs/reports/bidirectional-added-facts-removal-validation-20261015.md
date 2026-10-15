# Added-fact removal: implementation validation

Date: 15 October 2026. Status: implemented as selected; the core protocol
checks pass, but the strict broader application gate has eight precision regressions.
The fixed-project scans are complete. The [project measurement report](bidirectional-added-facts-removal-performance-20261015.md)
records their failures and the heap/profile review status. Main uses the legacy engine
and has no run-N iteration.

## Change

The implementation follows the [accepted proposal](../design/bidirectional-added-facts-removal-implementation-20261015.md).
It removes the callee input store and its indexed input views. After run-1, each input
is matched against fixed demand and then released. Run-1 always emits the abstract
initial, including the static root. It stores only checked answer shapes with packed
concrete marks until a request consumes those memberships. Requests stay active for
future suppliers. A mark request uses an initial with empty field exclusion; supplier
exclusions are checked before an answer is projected.

Late questions use the existing caller N2 relation. They keep the full query path,
tail, exclusion, operation role and original initial slot. The caller visits both
prefix and extension candidates, reads current admitted values, and applies the full
AP predicate. Answers and FLOW climbs use queued, counted delivery. A queued replay
keeps a stable input handle rather than an old cumulative input root. Existing
method-queue-empty summary batching and caller-side ND premise matching remain.

The driver no longer reads raw links through Support. A witness without a confirmation
certificate remains a DEMAND candidate, even on a NORMAL layer. Full-driver reference
tests use this same candidate policy. Fixed-configuration transfer tests still compare
exact facts, initials, requests, premises, records and source identities. A candidate
cannot trigger STOP_RULE while demand entries remain.

The old store, index and support algorithm exist only in test sources as historical
oracles. Tests capture transport inputs in test-owned histories. The runtime jar has
none of the old AddedFactStore, AddedFactIndex, Run1Abstraction.Phase, Support or Link
classes. The retained caller N2 accumulator is a separate runtime owner.

## Observer contract

The proposal permits separate observer cleanup when callback multiplicity is observable.
That condition holds: `ExternalMethodTracker.trackExternalMethod` increments the exported
`callSites` count on each callback. The existing call-plan controls expect distinct
callbacks for fresh payloads and caller origins. A once-per-call identity set would
change that result.

This iteration therefore keeps the observer's existing exact duplicate contract through
`RunMethodCallServices.observed`, using the existing AddedFactAccumulator. It needs no
positional index or captured input view. This is an isolated observer owner; emission
and request replay do not read it. Reports must include it in heap ownership rather
than claiming that all input payload storage has disappeared. A new observation
contract and compact gate remain possible work for a later iteration.

## Evidence

The complete common module passed 1,424 tests, with zero failures, errors or skips.
This includes an independent scalar-history answer oracle, packed mark consumption,
both arrival orders, full catalog geometry, original supplier exclusions, FLOW mark
exclusions and caller routing, queued delivery and work accounting, cancellation,
failed enqueue, ND summaries and mixed-caller sink candidates with zero-GEN results.
The actual F1/B2/F3 and five-run cases pass under the new candidate policy.

The full JVM module passed 395 tests, with zero failures, errors or skips. The JVM
and Go consumers compiled during implementation. Root application tests compile;
the 13 actual JVM sequence tests pass. The broader existing application run has
158 tests: 149 pass, eight strict finding-set cases fail, and one stream-flatMap case
has its existing disabled annotation. There are no errors. The eight failures remain
unchanged assertions. They add 26 case/rule memberships and miss no expected rule ID
in these fixtures. This does not establish a no-missed-location result for projects.

An independent, clean worktree at `acea668b08b7c0659821a277cafb19a527508232`
passes all 28 tests in the two affected cleaner classes. A second worktree at the
same revision removes only the driver call to `Support.confirm`; it retains the
old emitter, N2 and raw stores. That control reproduces all eight exact extra-finding
sets, plus the expected old CONFIRMED-to-DEMAND assertion difference. Thus the new
candidate/seed policy alone is sufficient to cause these fixture regressions.
It is not a failure inherited unchanged from the old core.

The failing current fixture roots all reach and complete **run-9, forward, limit 9**,
with policy termination and an accepted run-9 report. The old matrix roots stop at
**run-5**, and the three branch/alias roots stop at **run-3**, through STOP_RULE.
Leaving witnesses unproved changes sink seeds and later source/refinement work.
The observed `anyPlain` extra already exists as a DEMAND witness in run-1 and remains
in runs 3, 5, 7 and 9. Its actual run-2 source hits are the original entry's exact
and must source edges; those same edges are incoming run-3 source seeds. In run-3,
the same full method/context/premise carries a NORMAL root must fact into the plain
cleaner and a DEMAND root ANY fact into the following sink. Thus this residual
candidate survives later accepted reports under the new source/seed frontiers.
Local NORMAL support remains separate from a concrete confirmation certificate.

The implementation does not add a confirmation shortcut, omit required seeds, change
stop rules or relax the finding tests to remove those failures. The accepted minimal
candidate policy has a precision cost. A correction needs a separate refinement or
validation design. The strict broader application gate is **failed**. The passing
core checks provide readiness for measurement, not full finding acceptance. No
project performance or memory improvement is claimed here.

| Contract | Executed controls | Remaining measurement scope |
| --- | --- | --- |
| AF1–AF3: abstract emission, packed answers, exact matching | AddedFactRemovalTest, Run1AbstractionTest, RequestGroupCursorTest, RequestBarrierJoinTest | Project mark/shape widths and retained memo ownership |
| AF4–AF5: both replay orders, full headers, queued effects and cancellation | MarkQuestionKeyTest, MarkQuestionProtocolTest, SummaryStorageProtocolTest, RunShellTest, RequestBatchCycleTest | Largest live replay visit, service delay and delivery queues |
| AF6: direct restricted emission at a fixed configuration | GeneratedCurrentProgramTest, ScheduleFuzzerTest, closure and AP emission controls | Repeated arrival/intersection cost under real workload |
| AF7–AF10: ND, candidate reports, zero-GEN, records and complete hand-offs | SupportIntegrationTest, ReportTest, CurrentSequenceTest, CurrentModeIterationTest, CurrentModeActionsTest, PredicateClosureTest, AnyTaintIterationTest | Strict main location comparison and candidate growth |
| AF11: optional exact support | No exact-support mode is selected; incomplete-barrier/report tests pass | A future N2 confirmation window needs its own implementation |
| AF12: ownership and observation | Built jar absence check, release tests, independent source hashes and preserved observer callback controls | Heap ownership when an OOM capture is available; observer exception above |

The constructive Lean model and its recorded build and axiom audit remain unchanged.
They prove the local protocol under the exact-replay premise. They do not prove the
whole Kotlin engine, concurrent scheduling, ND trace validity, memory bytes or a
60-second run-1 bound. The real-project finding, timing, profile and heap gates are
separate required evidence.

Artifacts are under
`/drive-testcomp/opentaint-go-rules/bidi-o1-o3-20261012/added-facts-removal/20261015T090757.961708Z`.
The fresh main baseline has 4,112 OWASP, 7 Conductor, 14 ThingsBoard and 17 Stirling-PDF
unique sink locations. The measured current runtime is
`27e22bb1ed871651143538783473cb0d4fcbcf3e`.

| Project | Latest reached | Last completed engine | Accepted forward report |
| --- | --- | --- | --- |
| OWASP | run-5, forward; TIMEOUT | run-4, backward | run-3 |
| Conductor | run-2, backward; OOM | run-1, forward | run-1 |
| ThingsBoard | run-1, forward; OOM | None | None |
| Stirling-PDF | run-4, backward; OOM | run-3, forward | run-3 |

Previous optimization reports describe a different runtime and are historical evidence only.
