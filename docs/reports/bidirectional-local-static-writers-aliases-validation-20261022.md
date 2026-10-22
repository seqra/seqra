# Local static writers and summary aliases: implementation validation

Date: 22 October 2026. Status: implementation validated with existing
precision failures. Combined project measurements are not yet complete.
Base: `24ed1ca51c65321a1661ea8f39cfdf4c2c75a385`; its runtime is
`d7244c08cdaefbb157f42e4d89731d301b97e48f`.
Contract: [selected specification](../../spec/run1-static-writers-and-summary-aliases.md).

## Implemented scope

Only executed local transfers admit static write points. Caller results
and direct cleaner results retain ordinary summaries, selected demands
and computed ZERO/all-static group supplies. They create no FLOW writer.
Local admission does not depend on a fresh summary delta. A local writer
owns its immutable bounded relation, including mark exclusions.

Completed source actions carry their existing action identity through the
result filter. A completed conjunction can use only its own unattributed
write descriptor. This invocation metadata is not a fact or storage key.

Run-1 alias admission uses the raw scalar callee premise. FLOW requires
equal bases/chains and two STAR field tails. TAINT requires equal
bases/chains and actual marks; tail kind does not affect this test.
The packed partition preserves actual outputs and headers. Call stages
and saved replies preserve each arm's classification. Run-1 DEMAND does
not bypass the guard. Later FULL/native/reversed behavior is preserved.

The selected alias contract changes which candidates receive aliases.
This is not equality with the old DEMAND alias closure. Field transfer,
rule admission, mark policy, run limits, reference managers and IR are
outside this change. Duplicate write-presence batch suppression is also
deferred.

## Executed checks

| Gate | Result |
| --- | --- |
| Common dataflow | 1,737 passed; no failures, errors or skips |
| JVM dataflow | 438 passed; no failures, errors or skips |
| New actual JVM static controls | All 5 passed, within the application gate |
| Broader application | 181 tests: 171 passed, 9 existing precision failures, 1 skip |
| Rule/join and enabled bidi controls | 41 tests: 39 passed, 2 existing exit-scope precision failures, no skips |
| Focused Lean models and audit | Passed; 1,490 declarations and 46 controls; no unexpected axioms |

The common controls cover wrapper summaries without writer copies,
promoted-before-local order, immutable relations after summary merge,
mark-exclusion composition, complete source/conjunction admission,
unseeded cycles and actual caller request replay. The replay controls use
nested callers, static and nonstatic actual inputs, late groups, duplicate
delivery and three queue policies. The alias controls cover mixed packed
outputs, raw arg/return distinction, both layers and late cleaner/binding
replies. Existing FULL/native and reversal controls pass.

The new actual JVM cases cover `set(source())`, `set(C.input)`, nested
setter/getter/sink wrappers, two callers, a conditional modeled static
action and absent-source/condition negatives. They check run-1 group
demands, run-2 full-mark static queries and source action hits, and the
exact source-seed handoff into run-3. These are finite controls, not a
whole-engine proof.

The nine cleaner failures have the same finding sets as the base runtime:
no lost or added IDs and no missing expected IDs in these comparisons.
They still fail. The two exit-scope tests were skipped in the old gate
because its environment flag was absent. With the flag enabled, both
current and exact base `24ed1ca51` report the same unannotated `clean`
callee exits, with aliases both enabled and disabled. Positive controls
pass. The current rule gate must not be reported as green. Their source
cause and correction are outside the selected static/alias patch.

The first combined compile failed on test fixture references and executed
no tests. The next focused run found two old explicit DEMAND-bypass
expectations; these were updated to the selected rule. The new JVM static
argument assertion was corrected to require its static conditional
original, rather than an invalid ZERO original. Final broad gates above
include these corrections. Stale XML from the compile failure is excluded.

## Evidence and latest iteration

Evidence root:
`/drive-testcomp/opentaint-go-rules/run1-local-writers-aliases-20261022`.
`validation/frozen-implementation-source.json` pins all 23 changed code
and test files. Gate directories retain commands, full JUnit XML and
receipts. `validation/application-regressions.json` and
`validation/exit-scope-base-comparison.json` retain the failed-gate
comparisons. Lean logs are `validation/lean-build.log` and
`validation/lean-audit.log`.

All four fresh main `f945f92c5` measurements are OK. Main has no numbered
bidirectional iteration. The fixed writer-only control is `8157a6055`:
conductor completes run-1 in **50.198 seconds**, accepts run-1, then ends
with **run-2 OOM**. Latest reached/completed/accepted are therefore
**run-2/run-1/run-1**. Its run-2 engine time includes the live-heap capture.
The earlier writer-only control is superseded by the producer-identity
correction and is not the final control authority.

Combined project results are pending. These module and finite controls
do not establish project no-FN acceptance or the 60-second run-1 target
for all four projects. The performance report and next proposal must use
the committed implementation and the approved project settings.
