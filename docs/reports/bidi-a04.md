# A04 trie operations and conclusion groups

`TrieOps.kt` adds merge, new-only delta, leaf maps, folds, child edits, prepend,
chain, subtraction, leaf enumeration and graft. Traversals use explicit stacks.
Whole-subtree maps use node identity memos. Merge uses node-pair identity memos;
subsumption also keys its memo by inherited cover. Unchanged subtrees stay shared.
Enumeration keeps one path buffer and copies it for each leaf callback.
Optional cancellation forms retain the proposal's positional callback interfaces.

`Groups.kt` adds FLOW, TAINT and combined conclusion groups and store interners.
It applies T1/T2/T2'/T3/T4 and required incoming subsumption. T2 reinsertion uses
a loop. Normal must leaves never remove exact leaves, and no subsumption crosses
layers or kinds. The destination TAINT key is read after subtraction canonicalizes
the remainder. Optional T5 folding stays disabled.

`ApOut.kt` completes Results and CollectingOut. Result groups normalize E and
merge exact keys. `ApManager` supplies store interners and uses generic prepend
for typed fact construction.

## Tests before implementation

Commands use `./gradlew --console=plain` from `core`.
The first RED selected ResultsTest; missing A04 APIs prevented test compilation
(`/tmp/bidi-a04-red.log`). GroupMergeTest and SubsumptionTest also failed on
missing group APIs (`/tmp/bidi-a04-groups-red.log`). After production compiled,
the complete test compile found expected-list type inference and value-class
vararg errors (`/tmp/bidi-a04-first.log`). The plan was updated before each fix.
The focused bidirectional run then passed: 242 tests in 21 classes, with no
failures, errors or skips (`/tmp/bidi-a04-focused.log`).

| Test class | Tests | Checks |
|---|---:|---|
| TrieMergeDeltaTest | 26 | Raw path/mark-column union and delta in both orders; all generic helpers, identity/sharing, positional callbacks, repeated accessors, nested graft, deep operations, DAG memos and cancellation. |
| GroupMergeTest | 12 | T1–T4, equal-content restrictions, field and mark intersections, full relation unions, key separation, interner ownership and collectors. |
| TaintGroupKeysTest | 7 | Last-any removal, normalized Empty-E destination, existing-destination delta and unique group keys in both preload orders. |
| SubsumptionTest | 14 | 13,325 ordered single-leaf pairs against the direct reference, partial deltas, layer/kind/base separation, first-step E, retained exact facts, T5 disabled, relation unions and state-sensitive shared-child subtraction. |
| ResultsTest | 4 | Exact-key result union, canonical exclusions, reach bits, flush reset and retained request callbacks. |

Expected unions use test-local raw decoding and the independent bounded denotation.
T2 checks full relation-pair unions in both subset directions, with a fresh accessor
and mark. It does not use pairwise leaf coverage. Deep tests use 32,768 edges;
shared-DAG checks use a small number of live nodes and do not expand all paths.

Review corrected empty-result normalization and a nullable-child subtraction
shortcut. It added a shared input reached with different inherited cover, and
positional callback checks. No new Lean proof was needed; the reference rules
covered these cases.

## Path cache correction

The first full gate passed: 390 tests in 32 classes, with no failures, errors or
skips. All consumer compilation passed in 34 seconds (`/tmp/bidi-a04-gate.log`).
XML is saved in `/tmp/bidi-a04/first-gate/xml`.
The deep prepend/chain case took 5.488 seconds. Every repeated zero-accessor
suffix had public hash zero, so the manager's path table formed one hash cluster.
Other deep operations took at most 0.062 seconds in that run.

The plan was updated before changing the manager's private path key to accessor
value plus canonical suffix identity. Public path hash, equality and values stay
the same. The extended deep test checks canonical repeated lookup from an array,
a copied array and a list before prepend.

## Final gate

```bash
./gradlew --console=plain --continue \
  :opentaint-dataflow-core:opentaint-dataflow:cleanTest \
  :opentaint-dataflow-core:opentaint-dataflow:test \
  :opentaint-dataflow-core:opentaint-jvm-dataflow:compileTestKotlin \
  :opentaint-dataflow-core:opentaint-go-dataflow:compileTestKotlin \
  :opentaint-jvm-sast-dataflow:compileKotlin compileKotlin
```

The final gate passed in 29 seconds (`/tmp/bidi-a04-final-gate.log`): 391 common
tests in 32 classes, with no failures, errors or skips. JVM and Go production/test
sources, SAST and application compilation passed. Final common XML is saved in
`/tmp/bidi-a04/final-common/xml`. The extended deep prepend/chain test took
0.029 seconds in this run; this is local gate timing, not a project benchmark.
`git diff --check` passes. S00 had no baseline failures. A04 is complete.
