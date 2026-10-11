# E03 standing requests, sinks and conjunctions

`RunMethodLocalServices` supplies the actual method-local E02 services. Only
run 1 allocates its request store and standing request/link join. Only forward
runs allocate a conjunction store. E6 will share this store in E05.

Both join arrival orders retain complete caller references and independent
caller/link layers. Every accepted link delta joins all standing requests.
Answers start concrete initials; climbs send the original caller premise and
request. Restricted runs reject request entry and allocate no request state.

Conjunctions retain each literal contribution and combine full premise sets
with their effective layers. Zero contributors drop from mixed premise unions.
Backward forms use independent DEMAND branches. Forward sink witnesses retain
literal order, facts, premise keys, layers, alternative, method context and run.
Every evaluated part remains available for the global-state exit rule, including
an incomplete conjunctive sink. Caller-set statics stay in raw summaries.

Backward zero reachability fires hand-off sink seeds. A reversed end fact fires
its alternative's literal seeds once per statement, also for a confirmed
vulnerability. End facts create no source hit. Real reversed sources record
their original cached forward form before output cuts or duplicate storage.
Every seed takes `Cut.SEED`. Cancellation precedes mutation and candidate,
literal and seed work. Freeze releases requests, joins, triggers and the owner.

## Test evidence

The first tests stop in the test compiler at the missing service API
(`/tmp/bidi-e03-api-red.log`, `/tmp/bidi-e03-all-api-red.log`). The initial
executed snapshot passes 35 of 37 tests (`/tmp/bidi-e03-focused.log`). Its two
failures are draft fixture errors: a DEMAND must input has already lost E, and
an exact-only fact cannot retain E. The plan records these corrections before
edits. The mixed static fixture now builds one shared exact/must header and
retains E only where normalization permits it.

All 45 tests of the next focused gate pass
(`/tmp/bidi-e03-focused-2.log`). The clean common gate passes 930 tests in 93
classes, with no failure, error or skip (`/tmp/bidi-e03-full-gate.log`, 33
seconds). JVM and Go test sources, JVM SAST and the application compile in the
same gate. All 782 new common annotated tests have void JVM returns and appear
in fresh XML (`/tmp/bidi-e03-common-discovery.txt`). XML is saved under
`/tmp/bidi-e03/opentaint-dataflow/xml`.

The final coverage review adds actual FLOW sink/conjunction requests through
the standing join, restricted abstract controls and caller references that
differ only in caller layer. All 48 E03 tests pass in the final clean common
gate (`/tmp/bidi-e03-final-common-gate.log`, 31 seconds). It passes 933 tests in
93 classes, with no failures, errors or skips. All 785 new common annotated
tests are discovered with void returns and fresh XML
(`/tmp/bidi-e03-final-common-discovery.txt`). E05 owns actual record replay and
its source-hit check. No new Lean clarification is needed. `git diff --check`
passes. E03 is complete.
