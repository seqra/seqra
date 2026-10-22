# Static summary erasure: production adapters

Date: 22 October 2026. This note records source correspondence for
[the selected spec](../../spec/run1-static-summary-erasure.md), sections
4 through 7 and 12. The query-completion adapter has a
[separate note](bidirectional-static-query-completion-adapters-20261022.md).
The erasure contract applies to GROUP and FULL run-1 modes.

## Summary and effect boundary

`RunMethodAnalyzer.retainSummary` is the only production call to
`RunSummaryStore.add`. It first checks the existing summary-base and
fact-kind contracts. For a static result, it secures computed effects.
It then erases a nonzero all-static original with a static result. The
test includes FLOW, concrete values, exact fields and exact class points.
ZERO is not a static original.

An erased result cannot reach the raw store, `addDemand`, the pending
publication batch or `SummaryStorage`'s publication index. Handoff reads
the retained selected store. Native persistence reads the retained raw
store and keeps only NORMAL results. Thus these downstream owners have
no erased row to copy. Later-run summary and native-record paths remain.

`emitOperationResult` admits the reached local write region and local
FLOW writer before this boundary. A call result uses
`promoteStaticSummary` and cannot create a local FLOW writer. Both paths
keep the existing field cut, run-1 cover and static diagnostic gate.
Static FLOW and concrete results stay DEMAND in either selected mode.

The first ordinary input binding still runs. For
`set(p) { C.out = p; }`, the `arg0/G -> C.out/G` callee row remains.
The caller's current input is already mapped to `arg0`; its original
can still be `C.in/G`. Applying the callee row can supply `C.out/G`.
Secure this supply before erasing the caller's `C.in/G -> C.out/G` row.
Do not change its original to ZERO.

`RunMethodCallServices` also rejects a run-1 static/static publication
at its summary-application and delayed-static-reply entries. It does not
perform an S-to-S caller relay. Retained ZERO/nonstatic-to-static and
static-to-nonstatic callee relations still use ordinary subscriptions.

## Unanswered scalar ownership

`Run1PointResume.WriterKey` holds the complete original, scalar output
pattern and layer. The pattern keeps the AP, tail, field exclusion and
FLOW mark exclusion. A local write is the admission witness; its statement
is not a new writer-key dimension.

The exact-field fixed-mark fallback uses this same scalar shape in an
unanswered `ConditionalJoin`. `conditionalKeys` deduplicates its complete
identity while it waits. Each reply supplies the unchanged AP reducer.
After the complete input tuple arrives, both the join and its key are
removed before publication of the computed supply. There is no completed
join archive. A repeated offer can create a new temporary join.

`publishMatched` reconstructs the scalar from the saved pattern and
layer. It no longer reads `summaries.borrow` or `currentValues`. Thus a
delayed reply works when the method has no corresponding raw summary.
The canonical static input path still uses its existing direct reducer.
Exact class points keep their separate point-interest path.

## Work and lifetime

The existing actor work contract does not depend on a summary count.
`hasPendingAbstractionWork` includes the static effect batch and the
resume queue. `work` commits the batch and submits all static replies
before it can return complete. A method with no retained rows can still
schedule this work.

`RunnerPort.sendStatic` sends an ordinary `RunEvent.StaticOwner`.
`UnitRunner` reserves accepted events through `RunManager.accept`.
It keeps a Work reservation across a quantum yield and releases it only
after the handler's sends and enqueues finish. The run joins its runners
before freezing actors. `freeze` checks that the local work, publication
batch and admitted effect batch have drained, then clears point-owned
state. Dormant unanswered interests do not by themselves require work.
No new completion or cancellation protocol was added.

## Focused controls and limits

`Run1StaticSummaryErasureTest` exercises these production seams:

| Test | Scope |
| --- | --- |
| `field and exact class scalar effects survive without any raw selected or published row` | Both modes, canonical field/class originals, cross-mark outputs, EXACT/ANY/MUST output tags, effects without method rows. |
| `fixed exact-field joins own deduplicated unanswered scalars after erasure` | Both modes and input orders, pending-join deduplication, actual-input requirement, answer and key removal, repeated offer without a completed archive. |
| `first ordinary binding supplies its static output then erases the caller relation` | Both modes, actual callee-projected current input, retained formal row, first computed caller supply, no caller writer or static relay. |
| `run1 call application never relays an already static summary` | Both modes, ordinary and ZERO/static application entry points. |
| `complete mixed guard retains its nonstatic origin and secures the erased static arm` | Both modes, complete local conjunction, every contributing origin, retained nonstatic arm, static-arm effect, no partial-cube, ZERO or ND result. |
| `later full analysis retains and persists a real static summary` | Backward FULL native persistence of a NORMAL static/static row. |
| `a method with no retained summaries still delivers static work to a dead local sink` | Real run manager, both modes, one and two worker threads, looping copy with no normal return or retained summaries, local sink with no exported static premise. |

`Run1OperationWriterPipelineTest` uses the production protocol with three
schedule policies and duplicate deliveries. One local writer supplies
the sink across two callers without raw, selected or published S-to-S
relay rows. `StaticConditionalRequestJoinTest` keeps the complete-guard
and actual-input checks when its result arms are erased.

Existing controls remain relevant:

- `Run1FieldPreservationTest` checks broad preservation demotion, direct
  destination kills, sibling preservation and later strong exclusions.
- `Run1OriginConjunctionStoreTest.ZERO requires same complete partition
  and late all zero binding` and
  `Run1OriginPredicateStoreTest.zero requires one complete cube and never
  pools unrelated OR leaves` check exact ZERO eligibility.
- `GroupedDirectStaticsEngineTest.early setter and getter cross two ZERO
  wrappers and refine through backward run two` checks a non-exported
  local sink and actual run-2 source hit.
- `Run1StaticDriverSafetyTest.ZERO discovery keeps only a provisional F1
  candidate after a nonreturning call` checks later rejection of that
  accepted run-1 control approximation.
- `Run1StaticFieldAblationTest` checks that the diagnostic gate precedes
  field reads, writes, aliases, supplies and caller effects, while exact
  markers and later FULL handling remain enabled.

These finite controls do not prove correctness for every scheduler,
provider or program. The implementation receipt must record their final
compiled results. Project performance and latest reached/COMPLETE run
iterations belong in the project report. This note makes no whole-engine
soundness, termination or speed claim.
