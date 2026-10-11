# A06 cleaners, filters, mark checks and field cuts

`Clean.kt` implements the primitive cleaner tables. A selected mark on a
same-base abstract tree changes its mark exclusion before spatial tests and
shares the whole root. Run 1 raises its request even if that mark was already
excluded. Concrete and all-mark cleaning rebuild one spine, preserve off-spine
subtrees, and map only the subtree below the cleaner. Normal must partial rows
retain or extend E where the table requires it. Other demoted any results have
Empty E.

`Filter.kt` applies the path filter, then the concrete mark policy. The path
filter memo uses node and filter identity, including cached null results.
Accept shares a whole subtree. The policy visits the root and its uninterrupted
element chain, at levels zero and above; it preserves off-chain children.
Unchanged results retain identity. Whole tails, input layers and their allowed
exclusions are preserved.

`MarkCheck.kt` selects full literal input leaves. Geometry and exclusions are
tested before requests. Accepted concrete facts retain their input layer.
Restricted runs raise no requests. Mark removal handles both payload columns
and clears E when the last must leaf is removed. Concrete target construction
normalizes exact, must, may and zero results through the typed factory.

`Limit.kt` uses exact counted-depth metadata for its all-fit shortcut. Field
and element accessors consume the budget. Class accessors remain in the prefix
without consuming it. Cut leaves retain their marks, become demand any leaves,
and have Empty E. The cut memo uses node identity and remaining budget.
Public limits must be positive, including for REACH. An internal remaining
budget of zero can still retain class edges before the next counted cut.

Cleaner, filter, element-policy and cut walks use explicit stacks or loops.
Long walks and callback boundaries check cancellation.

## Tests before implementation

The first RED failed on missing A06 APIs (`/tmp/bidi-a06-red.log`). Production
then compiled, but wildcard imports made the old and new `ExclusionSet` types
ambiguous in the filter test (`/tmp/bidi-a06-first.log`). The plan was updated
before adding an explicit import. The focused run passed all 47 tests in five
classes, with no failures, errors or skips, in nine seconds
(`/tmp/bidi-a06-focused.log`).

| Test class | Tests | Checks |
|---|---:|---|
| CleanerTest | 11 | 1,458 direct cleaner matrix cells, F75 header update before spatial tests, must partial rows, first-step E, mixed marks, off-spine sharing, static/zero/base cases, huge shared DAG, positional callbacks, depth 32,768 and cancellation. |
| TypeFilterTest | 10 | Full annotated path/policy outputs, operation order, root/element policy, two FilterNext conjunction states, node/filter identity and cached-null DAG cases, sharing, depth 32,768 and cancellation. |
| MarkCheckTest | 7 | Direct literal checks across kinds/layers/directions, exclusions before requests, retained must and exact annotations, both-column mark removal, target normalization and guards, zero, deep selection and cancellation. |
| FieldLimitTest | 13 | Positive-limit reference equality, invalid limits, mixed kept/cut trees, repeated fields/elements, class edges at budget zero, exact depth boundary, sharing, distinct DAG budgets, depth 32,768 and cancellation. |
| LayerRulesTest | 6 | Cleaner results through cuts, filters, literal checks and mark removal, with explicit paths/marks/layers/E. |

Inputs and expected facts use local raw construction and decoding or direct
per-path AP operations. Deep checks use linear path walks. The selected-mark
abstract check shares a 62-level DAG without enumerating its leaves.
The deep cut check covers all-fit, limit one and limit 32,767, plus a deep
uncounted class path after budget zero. The plan was updated before adding
the long changing-budget case. No new Lean proof was needed; the specified
tables and reference cover these operations.

## Final gate

```bash
./gradlew --console=plain --continue \
  :opentaint-dataflow-core:opentaint-dataflow:cleanTest \
  :opentaint-dataflow-core:opentaint-dataflow:test \
  :opentaint-dataflow-core:opentaint-jvm-dataflow:compileTestKotlin \
  :opentaint-dataflow-core:opentaint-go-dataflow:compileTestKotlin \
  :opentaint-jvm-sast-dataflow:compileKotlin compileKotlin
```

The gate passed in 30 seconds (`/tmp/bidi-a06-gate.log`): all 491 common tests
in 43 classes pass, with no failures, errors or skips. JVM and Go production/test
sources, SAST and application compilation pass. XML is saved in
`/tmp/bidi-a06/final-common/xml`. The extended counted-path cut test took
0.098 seconds; the uncounted-class test took 0.039 seconds. These are local gate
timings. `git diff --check` passes. A06 is complete.
