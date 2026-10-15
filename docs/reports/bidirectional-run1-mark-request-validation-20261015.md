# Merged run-1 mark requests: validation

Date: 15 October 2026. Status: implemented; project measurements are pending.

The implementation follows the [request proposal](../design/bidirectional-run1-mark-request-implementation-20261015.md)
and its [static component clarification](../design/bidirectional-run1-static-component-codec-20261015.md).
Each summary storage borrows the frozen prescan request base and owns only dynamic
additions. The method actor merges pending mark sets in the same storage class.
It commits them when the method fact queue is empty, then replaces the temporary
storage. A quantum yield does not commit requests.

Caller subscriptions deliver late requests to the original caller slot. Concrete
inputs answer requests from their actual marks; they do not propagate requests.
The emitter has no question mirror, input history or deferred-answer cache.
Request matching ignores field and mark exclusions. Ordinary AP matching retains
its exclusion checks.

Run-1 admission enforces limit 1 and the specified initial shapes. Nonexact concrete
initials use STAR; their starting facts can still use ANY in the DEMAND layer.
Static root summaries serve as return evidence. The reader reconstructs only the
untouched component from the original input. Owned fields and class points use
their own analyses. Different exclusion headers remain separate during projection.
The structural footprint remains available to later record readers after the
run-1 request plan is released.

## Core checks

| Check | Tests | Failures / errors / skips |
| --- | --- | --- |
| Complete common dataflow module | 1,464 | 0 / 0 / 0 |
| Complete JVM dataflow module | 397 | 0 / 0 / 0 |
| Root and JVM SAST test-source compilation | Pass | Not applicable |

The seven static driver controls include exact class and field cleaners, preservation
of an unrelated mark, a strong write with an untouched sibling, a nonreturning callee,
and native/opposite record reads after request-plan release. The record control
reaches and completes run-3 and accepts its run-3 report. Separate controls cover
request arrival orders, recursive batches, method-empty barriers, request-only work,
cancellation, failed delivery and numeric counter units.

The broad class cleaner test initially expected the exact-point precision of an
exact supplier. Ordinary AP PART semantics retain a DEMAND residual for a broad
supplier. The final tests preserve that residual and separately require exact
class cleaning to remove T while preserving U. No AP cleaner rule was changed.
Historical scalar oracles remain available. The selected run-1 oracle constructs
STAR initials before execution; it does not relabel observations after execution.

The runtime jar contains none of the removed `MarkQuestion`, `StaticSupplierCursor`,
`RequestGroupCursor`, `AddedFactStore` or `AddedFactIndex` classes. The existing
reference manager and IR representation are unchanged.

The source-level selected JVM boundary argument is in the codec clarification.
Existing Lean receipts retain their original scalar-model scope. They do not
certify the Kotlin codec, JVM producer bridge, concurrent scheduler or a time bound.

## Measurement status

The existing application acceptance selection ran 158 tests: 149 passed, eight
failed their strict finding-set assertions, and one retained its existing skip.
There were no XML errors. All eight failed case names and complete expected/actual
rule-ID sets equal the previous candidate-policy control: 26 extra memberships
and no missing expected rule ID. No assertion was relaxed. The affected roots
reach and complete run-9 at limit 9, finish its barrier and return an accepted
run-9 report with COMPLETE/POLICY. This gate remains failed; core protocol success
does not establish full application precision or project recall.

No new fixed-project scan has started. There is no measured speedup or completed
run-1 time for this implementation. The 60-second run-1 engine target remains an
acceptance target. Prescan and report-barrier time must be reported separately.
Fresh OWASP, Conductor, ThingsBoard and Stirling-PDF scans must state their latest
reached, completed and accepted-report iterations and compare locations with main.

Evidence is under
`/drive-testcomp/opentaint-go-rules/bidi-o1-o3-20261012/run1-mark-requests-preparation/verification-20261015T205444.938773Z`:
`common-final/receipt.json` records the unchanged source checkpoint and complete
common gate; `jvm-consumers/receipt.json` records the JVM and compilation gate.
The application gate and comparison with the earlier exact finding sets are
preserved in `application-acceptance/`.
