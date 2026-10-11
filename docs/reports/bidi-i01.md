# I01 common forms and execution modes

The common interpreter forms now retain forward edge identities and own
cached collection containers. They provide statement and rule summaries,
sink alternatives, ordered clean steps, call points, stages and guards.
Reversal follows the ordinary tail table, removes forward filters and
guards, splits conjunction literals into demand branches, reverses action
order and retains sink triggers. A5 adds identities for untouched targets,
including ZERO when a final `ZERO_KEEP` makes it a target.

`FormApplier` implements STATEMENT, STAGE and GEN. It applies result filters
to plain and conjunction outputs. Untouched statement inputs pass unchanged.
Source selection precedes transfer; the first generated result records a
source hit before result filtering. GEN uses neither source hooks nor filters.

`FormsCache` creates each forward and backward value once per slot.
`DirectedForms` uses the specified entry, normal exit and exceptional exit
rules. `ReferenceAlgebra` uses per-path AP operations and type policies.
`FormsReference` supplies the reference call-plan walk with the same
application modes. Pending native must results retain exclusions through
actions and binding, clear the candidate flag at resume, and demote before
the exit hook applies the positive call limit.

## Tests before implementation

Missing-API failures preceded the production slices in
`/tmp/bidi-i01-forms-red.log`, `/tmp/bidi-i01-call-red.log` and
`/tmp/bidi-i01-reference-red.log`. The plan was updated before normative
corrections and coverage additions. The proposal omits result filtering
of conjunction outputs; interpreter §2.1 step 5 requires it. The common
sink constructor follows the positive concrete EXACT/ANY literal contract.
No behavior question or new Lean proof was needed.

The first compiled focused run passed 57 of 59 tests. Two graph tests
omitted ZERO from the reversed target set. The plan records why A5 includes
it; the expected sets were corrected. The next focused run passed all 59.
One further forward sink-trigger test covers eight fired-layer, identity,
empty and disabled-guard rows in the final gate.

| Test class | Tests | Checks |
|---|---:|---|
| StatementSummaryReversalTest | 16 | All ten admitted ordinary reversal rows, must guards, conjunction splitting, A5, removed filters, owned collections and 32,768-accessor paths. |
| RuleStatementReversalTest | 9 | Positive sink guards and seeds, owned sink paths, separate statement/GEN reversal, A5, exit removals and shared field-action temporary. |
| CallPlanReversalTest | 12 | Stage endpoints and action order, retained triggers, alias pass-over, candidate and current-layer guards, cached ownership, zero entry/pass-over and both resume directions. |
| FormApplierTest | 8 | Three application modes, operand/result filters, conjunction result filtering, source hook order, no-output source and disabled source hooks. |
| FormsCacheTest | 6 | Build/reverse once, direction order, slot guards, failed-reversal retry, context identity and normal/exceptional boundaries. |
| FormsReferenceTest | 8 | More than 10,000 admitted transfer vectors against tree operations, full raw leaf identity, requests, path/mark filters, ordered field actions, sibling stages and late must demotion before limit one. |
| FormsSinkTriggerTest | 1 | Forward END_FACTS uses sink identity, fired layer and zero premise; ordinary siblings retain their own metadata and source hooks remain off. |

## Final gate

The clean common gate passed 707 tests in 68 classes, with no failures,
errors or skips, in 32 seconds (`/tmp/bidi-i01-final-gate.log`). JVM and Go
test compilation, JVM SAST compilation and application compilation pass.
The final formatting check passed the same compile/test tasks in one second
(`/tmp/bidi-i01-format-check.log`).

All 559 new common annotated tests have void JVM return types and occur in
their result XML (`/tmp/bidi-i01-discovery.txt`). Fresh XML copies are in
`/tmp/bidi-i01/final-common/xml`. `git diff --check` passes. I01 is complete.
