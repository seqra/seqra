# E04 summary publication and replay

The run manager now owns one summary storage per complete method key. The
actual method handler owns one subscription manager per runner. It dispatches
Start, LinkIn, ZeroIn, RequestIn and Delivery to the actual method actor and
its required services. Same-unit links call the owning handler directly;
remote links use counted events. Publications go to the run's callee storage.
Joined release drops subscription maps, handler ports and run-owned storages.
A released run or handler rejects new work.

Storage insertion and replay snapshots use one lock. Matching and callbacks
run outside it. Registration precedes replay; insertion precedes notification.
Delivery reads current subscriptions. Replay and delivery use one complete AP
match after their transposed positional scans. Keys retain full premise sets,
member indexes, tails, exclusions, marks, layers and caller references.
Publication admission checks K6 and rejects temporary conclusions. Zero-only
subscriptions check the specified zret shape before storage. All candidate,
member, record, view and subscriber loops use analysis cancellation.

Restricted nonzero subscriptions replay native same-direction records and
transient opposite-direction views. Both use ordinary record matching on the
full accepted added group and deduplicate candidates. Views retain their
Pattern premise. Record reads create no current publication or callee demand.
E05 owns their actual call-plan resumption and source-hit integration.

## Test evidence

Tests first save broken P1–P4 finite schedules and the actual pipeline, index
and method-handler API cases. Missing APIs stop in the test compiler
(`/tmp/bidi-e04-api-red.log`, `/tmp/bidi-e04-all-api-red.log`). Record replay
tests are saved before its implementation. Main compilation passes in the
first focused attempt. Two test compile corrections give an explicit request
list type and rename a timing helper that collides with JVM `notifyAll`.

The plan records each correction before edits. Native ANY/must positives use
any-tail added facts, as the specified applicable tail gate requires. Exact
descendant rejection remains explicit; exclusion and mark negatives now pass
the tail gate. The first executed focused gate passes 44 of 45 tests
(`/tmp/bidi-e04-focused-3.log`, XML under `/tmp/bidi-e04-focused-red/xml`). The
remaining deep-path fixture also needs an exact premise for its exact input.
Nonzero Facts comparisons use value equality; zero-only bypass tests keep
identity checks. FLOW fixture caller premises follow the real K6 producer.

All 45 E04 tests pass in the next focused gate
(`/tmp/bidi-e04-focused-4.log`). They cover actual P1–P6 observations,
publication/replay orders, delayed notifications, duplicates, current
subscriptions, owned snapshots and concurrent whole-batch visibility. The
index matrices make 217,248 finite full-scan AP checks. Other rows cover
33-way fan-out, depth 32,768, native keys, all three F76 view shapes, layers,
zero and run-1 record guards, cancellation and joined lifetime.

No new Lean clarification is needed. These finite tests do not prove general
pipeline closure or whole-project performance. E07 adds the complete-run
closure oracle and schedule fuzzer.

## Validation gate

The clean common gate and JVM/Go test-source, JVM SAST and application compile
checks pass in 33 seconds (`/tmp/bidi-e04-full-gate.log`). All 978 common tests
in 97 classes pass, with no failures, errors or skips. All 830 new common
annotated tests have void returns and appear in fresh XML. Discovery is
recorded in `/tmp/bidi-e04-common-discovery.txt`; XML is saved under
`/tmp/bidi-e04/opentaint-dataflow/xml`. `git diff --check` passes. E04 is
complete. E06 also audits cleanup when a required freeze callback throws.
