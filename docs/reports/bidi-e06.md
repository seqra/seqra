# E06 deadline, scheduling and joined release

The run uses one monotonic deadline from entry to `run`. Root resolution,
runner creation and the completion wait consume that budget. Admission
checks the deadline after each external routing or factory callback. The
start phantom checks it before release. A prior terminal status keeps its
priority. The completion wait uses only the remaining time.

After a successful join, release attempts all complete actor freezes and
all runner releases. It clears runner, job and storage maps before it
propagates the first callback failure. Later distinct failures are
suppressed. Each runner clears its queue, queued identities and actors in
`finally`. Actual method freeze keeps the unfinished-work precondition
first. It then attempts both required services and detaches run references
before it propagates a failure. Forward links, summaries, backward source
hits and counters remain available to the barrier. Failed join still
overrides COMPLETE with FAILED and exposes no live run stores.

The scheduling tests exercise the actual actor, handler, runner and summary
storage. A zero-phase yield retains pending publications. Partial Work
resamples event priority while keeping one reservation. A counted publication
callback can add work before quiescence. These controls pass with the existing
Q1–Q4 and unchanged-loop tests.

## Test evidence

The first deadline slice fails its two expired resolver tests
(`/tmp/bidi-e06-deadline-red.log`). The combined slice first exposes an
explicit `UnitResolver<CommonMethod>` test typing requirement
(`/tmp/bidi-e06-cleanup-red.log`). The plan records the correction before
the edit. The next slice has 12 tests and six expected failures: four
deadline gaps and two release gaps
(`/tmp/bidi-e06-cleanup-red-2.log`). RED XML is saved under
`/tmp/bidi-e06-cleanup-red/xml`.

After the manager and runner corrections, 47 of 50 focused tests pass.
Only the three actual method service-release cases fail
(`/tmp/bidi-e06-core-freeze-red.log`). RED XML is saved under
`/tmp/bidi-e06-core-freeze-red/xml`. The plan records the actor release
correction before its edit. All 50 then pass
(`/tmp/bidi-e06-focused-green.log`). The four new classes contain 21 tests:
RunCleanupTest 11, RunFailureTest 6, RunJoinOverrideTest 1 and
RunSchedulingTest 3.

## Validation gate

The clean common gate and JVM/Go test-source, JVM SAST and application
compile checks pass in 34 seconds (`/tmp/bidi-e06-full-gate.log`). All 1,054
common tests in 108 classes pass, with no failures, errors or skips. All
906 new common annotated tests have void returns and occur in fresh XML.
Discovery is recorded in `/tmp/bidi-e06-common-discovery.txt`; XML is saved
under `/tmp/bidi-e06/opentaint-dataflow/xml`. `git diff --check` passes.

No new Lean clarification is needed. E06 is complete.
