# E05 call execution and record returns

The actual call service walks the cached directed call graph. It suspends at
each callee stage and resumes at that stage's end point. It checks sinks at
BOUND, applies ordered actions and filters, removes field-action temporaries,
and applies the final call cut before successor work. It registers and replays
a subscription before sending its link. Untouched and REACH passes use the
existing unchanged worklist. Only the zero-premise REACH path runs extra zero
rules. Backward zero subscriptions and source hits keep their separate rules.

Normal singleton returns split exact annotated identity leaves from effects.
Full group keys include exclusions and abstract mark exclusions. Forward
aliases admit DEMAND and effect results. Same-direction must records split
inside and applicable-only inputs. Both initial batches count as one crossing
before later actions can remove results. The pending batch keeps its facts
and E through returned actions, statement cuts, bind-back and aliases. It
becomes DEMAND with Empty E immediately before the call cut. Pending state
belongs to one invocation. Transient opposite-direction views keep their
Pattern premise and use ordinary resumption; no reader creates a source hit.

E6 uses the actor's actual local conjunction store. Its keys keep the callee
context, complete callee premise set, publication layer and call statement.
Each complete input combination unions caller premises and joins effective
added-fact layers with the publication layer. Replay and delivery can fill
each other's slots. Partial combinations produce no caller result. Support
is reserved for the later barrier.

The unresolved observer now receives only new run-1 TAINT deltas at ADDED.
Its cache retains the full caller reference and fact group. It reports no
FLOW, REACH, duplicate or restricted-run delta and does not select transfer.
Freeze releases the call-service owner and observation cache. Each public
operation and each walk loop checks analysis cancellation.

## Test evidence

The first slices and the full test snapshot fail in the test compiler at the
missing call service (`/tmp/bidi-e05-api-red.log` and
`/tmp/bidi-e05-all-api-red.log`). The first implemented focused run passes
53 of 55 tests (`/tmp/bidi-e05-focused.log`). Two tests inspect the edge store
for unchanged work. The plan records the correction before edits: drain the
actual actor and compare raw summaries, while retaining empty edge-store and
no-call-cut controls. The review also removes a forward nonzero-to-REACH
native record input outside AP §7.2. Ordinary backward zero coverage remains.
RED XML is saved under `/tmp/bidi-e05-focused-red/xml`.

The next focused gate passes all 55 tests
(`/tmp/bidi-e05-focused-2.log`): CallPlanTest 19, RecordReplayTest 9,
RecordDemotionTest 8, NdJoinAdapterTest 7, CallZeroTest 4,
CallSourceHitTest 4 and CallServiceLifetimeTest 4. The return comparisons use
raw concat and the per-path FormsReference walk, including RETURNED pending
resumption. Fixed vectors check layers, E, identity aliases, returned field
actions, positive cuts, killed crossings and views independently. Other rows
check E6 key isolation and Cartesian combinations, real replay/delivery,
contextual source seeds, original source-hit timing, zero rules, paths of
depth 32,768 and cancellation during subscription replay.

## Validation gate

The clean common gate and JVM/Go test-source, JVM SAST and application compile
checks pass in 33 seconds (`/tmp/bidi-e05-full-gate.log`). All 1,033 common
tests in 104 classes pass, with no failures, errors or skips. All 885 new
common annotated tests have void returns and appear in fresh XML. Discovery
is recorded in `/tmp/bidi-e05-common-discovery.txt`; XML is saved under
`/tmp/bidi-e05/opentaint-dataflow/xml`. `git diff --check` passes.

No new Lean clarification is needed. These finite checks establish the
tested call and return behavior, not general program closure. E07 supplies
the complete-run reference and varied schedules. E05 is complete.
