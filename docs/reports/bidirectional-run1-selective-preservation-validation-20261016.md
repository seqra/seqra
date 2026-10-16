# Run-1 request gates and selective preservation: validation

Date: 16 October 2026. Status: implementation complete; core checks pass;
the strict application precision gate fails. Project measurements are pending.

The implementation follows the single
[run-1 specification](../../spec/run1-field-writes.md) and its
[implementation design](../design/bidirectional-request-gated-static-handling-20261016.md).
That contract includes static requests as well as field writes.

Latest fixed-project measurement at this checkpoint: historical ThingsBoard
**run-1, FORWARD, limit 1; OOM**. No iteration completed and no forward report
was accepted. This result predates the implementation below. There is no fresh
project performance result for it. The run-1 target is **60 seconds of engine time**.

## Implemented behavior

| Area | Previous behavior | Selected behavior |
| --- | --- | --- |
| Static initial emission | Root propagation and broad structural ownership could start unrelated fields | Actual value reads gate abstract field initials; mark interests independently gate actual concrete suppliers |
| Static requests | Frozen mark interests could select fields through broad ownership | F and B have separate selected slots; B stays shared, wide scopes stay symbolic, and changed origins use merged dynamic requests |
| Definite field overwrite | Strong preservation could add field E | Kill overwritten components, retain disjoint components, and move only an E-requiring residual to DEMAND with Empty E |
| Demotion | The layer could force STAR or must to ANY | Preserve the actual tail and payload; genuine cuts retain their own widening |
| Static identity | Executed root/frame handling supplied preservation | Publish one ordinary N/D static identity per reached method; emit no root initial |
| Later phases | F1 normal records and old guide rows could control callee execution | F1 supplies guidance and candidates; later actual queries and concrete guide intersections start strong work and authorize complete-premise responses |

Prescan uses the existing resolved call graph. It closes field reads, mark
interests and static write effects separately. Source and sink conditions use
the same request workflow. Alias endpoints and write targets add no field-read
request. `ContainsMark(C.f,T)` adds only mark interest. SummaryStorage borrows
the frozen base; it does not copy or publish a startup request wave. Temporary
requests commit when the method queue is empty. Concrete facts answer requests
and do not propagate them upstream.

Field E is Empty throughout run-1. Complete overwrite kills apply in either
layer; pre-store value transfers remain separate. The ordinary default
summary keeps actual AP geometry and joins only the edge layer. Its premise
pattern can be a static root without becoming a computational initial.

All F1 sink matches remain unconfirmed candidates. No F1 publication becomes
an executable native or opposite record. Later current-query work covers all
bases, including nested static/local/argument/return paths. IR storage,
SoftReferenceManager and identity-cache policy are unchanged.

## Validation

| Check | Result |
| --- | --- |
| Common bidi tests | 1,360 passed; zero failures, errors or skips |
| JVM bidi tests | 297 passed; zero failures, errors or skips |
| Analyzer application build | `:projectAnalyzerJar` passed |
| JVM sequence acceptance | All 13 cases passed; all 17 finding assertions retain their original expected sets |
| Complete bidi application selection | 158 tests: 148 passed, 9 failed, 1 existing skip; zero errors |

The application failures retain their strict assertions. Eight cases have
exactly the previous expected/actual rule-ID sets: 26 extra memberships and
no missing expected IDs. One additional case reports a sanitized deep field.
That case reaches, completes and accepts **run-9 at limit 9**, ending with
COMPLETE/POLICY and one DEMAND finding. Thus application precision remains
an open failure, rather than a green acceptance claim.

The new false positive has an observed source-to-sink route. Both an actual
root-must query and a guide-refined `f.k` must query reach the named field
cleaner. Its temporary cleaner and String-bytes companion execute. Partial
EXACT cleaning leaves DEMAND remnants; writeback retains their geometry.
Exact and must remnants reach the sink through the returned object. The
backward source hit and forward source permission are present. Broad query
admission alone is not a sufficient cause: the refined must query also exposes
the residual. No extra precision change is included in this iteration.

Tests also cover actual N/D default storage and replay, the nine initial
families, independent field/mark gates, changed-origin requests, full ND
premises, cancellation and strong overwrite/cleaner/nonreturn behavior.
The nonreturning finite fixture now supplies the existing I11(e) exceptional
discovery graph. Its normal exit stays disconnected. The strict test proves
backward source discovery and the forward local finding without a fabricated
normal-return summary. Production seed handling is unchanged.

The independent scalar closure compares all static observations, not only
selected fields. Historical reference behavior stays separate. Existing Lean
receipts retain their local model scope; these runtime checks do not turn
them into a proof of the complete Kotlin runner or frontend.

## Evidence and next measurement

Evidence is under
`/drive-testcomp/opentaint-go-rules/bidi-o1-o3-20261012/run1-selective-preservation-preparation/verification-20261016T202647.993220Z`.
The main receipts are `validation/integrated-3-composite.json`,
`validation/consumer-build-1/receipt.json`,
`validation/application-sequence-final-1/receipt.json` and
`validation/application-final-1/receipt.json`. The new precision route and
old finding-set equality are in `validation/application-1/strict-finding-review/`.
Temporary diagnostics were archived and removed. Production sources remained
the same across the final core, application-build and application-test gates.

After the implementation commit, run the fixed OWASP, conductor, ThingsBoard
and Stirling-PDF configurations through `runProjectAnalyzer`. Bind the runtime
revision and full inputs, compare against current main, and report engine,
barrier, profile and heap evidence with reached/completed/accepted iterations.
Those measurements must precede the next optimization ranking.
