# Static GROUP protocol removal: implementation validation

Date: 22 October 2026. Status: **module checks, fixed-project runs and
source-bound profile/heap reviews closed**.
Contract: [selected specification](../../spec/run1-static-group-protocol-removal.md).
Runtime base: `a05444739`; its runtime is `a4bf789866f912ebefc54bca42d809c89beea2d4`.
Implemented and measured runtime: `30c1b84606695070a79d7ae467703267b9f931c1`.

The selected changes remove two repeated waits for a static GROUP input
that the method already has. They keep the scalar AP reducer, raw alias
partition, caller stages and selected demand relation. A field STAR
original and an exact class marker use their existing canonical DEMAND
input. Other legal shapes retain the current request path.

The computed-supply path still runs after raw summary admission. It does
not replace ordinary publication or demand admission. The first request,
FLOW writers, pending read refinement and the three static sets remain.
There is no field, source, exit, Spring, FULL-run or IR policy change.

Optional numeric counters use the existing optimization-statistics flag.
They count scalar call offers, direct applications, admitted fallback
requests, scalar AP applications, nonempty suffix batches, direct supply
leaves and fallback joins. They retain no methods or facts. The old
eligible waiting-table admission count cannot be recovered from a new
run. These counters do not establish a whole-engine speedup.

## Checks

| Gate | Current evidence |
| --- | --- |
| Selected constructive Lean models and axiom audit | PASS: 66 controls; 1,441 owned declarations; no unexpected axioms |
| Comparison and profile-summary controls | PASS: 36 tests |
| New production correspondence controls | PASS: first gate 35 tests; correction gate 24 tests, with overlap |
| Common dataflow | PASS: 1,745 tests; no failures, errors or skips |
| JVM dataflow | PASS: 438 tests; no failures, errors or skips |
| Application | 181 tests: 171 passed, 9 failures, 1 skip, no errors |
| Rule and join controls | 41 tests: 39 passed, 2 failures, no errors or skips |
| Fresh exact-base precision controls | Same findings in all 9 cleaner failures and both exit-scope failures |
| Candidate analyzer classes | PASS |
| Fresh fixed-configuration project campaign | All eight commands closed; source and inputs unchanged. All four candidate run-1 engines complete. Later engines stop on OOM/timeout. |

Evidence root:
`/drive-testcomp/opentaint-go-rules/run1-static-protocol-removal-20261022`.
The selected contract and formal source hashes are in
`validation/selected-contract.json`. Subsequent receipts must pin the
tested runtime and keep existing failed precision gates explicit.

The first common gate creates a canonical static GROUP original before
any pool answer in `StaticConditionalRequestJoinTest`. It then expects
that input to remain pending. That setup is outside the proved producer
invariant. The corrected delayed-input check uses the legal exact-field
fallback. A new grounded canonical case checks complete guard admission,
both literal orders and every contributing original. A correction attempt
failed at test compilation; it executed no tests. The next focused gate
and the full common rerun pass. The runtime is unchanged by these fixture
corrections.

The precision controls use a clean detached `a05444739` checkout. The
nine cleaner checks have the same expected and actual finding-ID sets,
or the same single-sink count, on both versions. The two exit-scope
checks report the same unannotated `clean` callee exits with aliases
enabled and disabled. Their complete exported finding lines and
multiplicities agree. These gates still fail. They are not project-wide
finding-parity evidence. No exit restriction, Spring rewrite or cleaner
semantic fix is included in this patch.

The test receipts pin source before and after execution. The runtime
audit binds all four changed production files and checks actor work
accounting, service locks, cancellation and ordinary demand admission.
The formal source files are unchanged. Compact evidence includes
`validation/runtime-source-audit.{md,json}`,
`validation/protocol-test-review-v2.json`,
`validation/precision-source-review.{md,json}` and
`validation/precision-base-application-comparison.json` plus its query
counterpart. The failed first common gate and compile-only correction
attempt remain available.

## Latest iteration

The [current performance report](bidirectional-static-protocol-removal-performance-20261022.md)
uses fresh main `f945f92c5` and the committed implementation. It records
each engine and barrier, strict finding differences and capture coverage.
All three fresh OOM heap reviews are closed. Each capture belongs to run-2.

| Project | Run-1 engine s | Latest reached | Latest COMPLETE | Latest accepted forward report |
| --- | ---: | --- | --- | --- |
| Thingsboard | 220.578 | run-2 OOM | run-1 | run-1 |
| OWASP | 239.868 | run-5 TIMEOUT | run-4 | run-3 |
| Conductor | 18.005 | run-2 OOM | run-1 | run-1 |
| Stirling-PDF | 29.530 | run-2 OOM | run-1 | run-1 |

Only Conductor and PDF meet the 60-second run-1 engine target. The strict
reported-location gate fails on two PDF returns. Their export-coordinate
mechanism is documented; coverage of the original paths is unproven.
OWASP retains all 4,112 main strict keys but has 9,179 trace attempts, so
the separate CI count criterion fails. Added findings remain unvalidated.
The existing failed module gates remain failed. No new source, exit,
field or FULL-run fix is included to change those outcomes.

The owner review identifies active later-run actor/index storage, pending
publication lists and Thingsboard's 2.683 GB incoming guidance store.
Its 5,890,526 static/static physical pairs are 84.68% of all demand pairs;
that is not a proved removable fraction. The run-1 allocation review also
finds repeated GROUP payload normalization. The
[next proposal](../design/bidirectional-static-protocol-removal-next-iteration-20261022.md)
separates those scopes and marks static relay removal as a major new
contract. It selects no further runtime change.
