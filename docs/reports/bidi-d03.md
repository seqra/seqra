# D03 iteration driver and complete-run barrier

The driver uses fresh engines for alternating forward and backward runs. Each
complete run joins before the barrier reads its stores. The forward barrier
confirms witnesses and updates the report, persists raw R1 records, builds
demand and seeds, and logs the frontier before testing the three stop rules.
The final complete run has the same barrier. Incomplete runs add and refute
nothing. A backward barrier proceeds to the next forward run.

Run 1 has a positive field limit, read once. Later limits cannot decrease.
The driver uses one time budget. It caps the policy timeout to the remaining
time after the policy callback. Barrier time consumes the next run's budget.
A barrier guard hit ends with OOM, including a cancellation after the last
checkpoint. Other throwables, including Errors, end with FAILED and retain
the report so far. The driver keeps each result in its one-run frame only.
Policy inputs and public frontier lists are owned immutable snapshots.

## Tests and observations

All 23 new tests pass in four classes: DriverTest (13), BarrierFailureTest (6),
DriverIntegrationTest (3), and DemandLinkTest (1). The failure table has 21
cases. Managers, method actors, services, stores and routing are real. Internal
seams select faults and guard timing; the public constructor runs the native
manager and memory guard.

The 1/2/3 sequence checks selected publication, sink seed, backward source
identity, precise demand, forward source seed, final native record and
confirmation. Equal 1/1/1 limits retain a may finding until POLICY. A normal
conjunction supplied at separate sites stops with NO_DEMAND_EDGE and keeps
its demand finding. Stops occur after their complete frontier.

The DLINK 1/2/3/4/5 fixture uses two separate native field reads and the
primitive root EXACT cleaner from the validation inventory. Run 3 has normal
processed edges and raw summaries, but a demand link in the throwing sink
method. Its normal witness remains unconfirmed. The driver continues and
run 5 confirms through the precise normal link. This is an observed finite
program sequence, not a full iteration theorem.

Timeout, memory guard, resolver, handler and construction faults cover first
and later runs. Barrier construction, body and cleanup faults preserve the
completed report state. Late cancellation overrides both a stop and a next-run
decision. Expiry during policy and barrier callbacks cannot renew the budget.
The deadline remains cooperative; these tests do not claim a wall-clock bound.

## Validation gate

The final API RED log is `/tmp/bidi-d03-final-api-red.log`; all tests were
installed before the driver APIs. The first implementation gate passes in
four seconds (`/tmp/bidi-d03-focused-first.log`). No semantic correction is
needed. An independent source review finds no driver contract defect.

The clean common gate passes 1,180 tests in 126 classes, with no failures,
errors or skips. JVM and Go test sources, JVM SAST and application test sources
compile. The gate takes 36 seconds (`/tmp/bidi-d03-full-gate.log`). All 1,032
new common annotated tests have void returns and appear in fresh XML
(`/tmp/bidi-d03-common-discovery.txt`). XML is saved under
`/tmp/bidi-d03/opentaint-dataflow/xml`. `git diff --check` passes. D03 is complete.
