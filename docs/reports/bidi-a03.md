# A03 immutable tries and conclusion values

`Trie.kt` adds immutable sparse nodes and FLOW/TAINT leaf algebras.
Nodes own their child arrays and expose snapshots. Construction checks path
accessors, sorted unique children, aligned arrays and nonempty subtrees.
Counted depth, node count and any-leaf presence are exact cached metadata.
Checked addition rejects an unrepresentable count or depth.

`Conclusions.kt` adds the two REACH values, FLOW and TAINT trees, and group keys.
`ApManager` builds normalized typed facts and canonical leaf values.
Normal TAINT any leaves retain must exclusions. DEMAND clears those exclusions;
exact-only trees also use Empty E. Only REACH can have the zero base.
`PathWalk.kt` visits each proper prefix before child lookup. `ApOut` defines
result and request callbacks; its collectors belong to A04.

`TrieInterner.kt` owns one managed soft table per store and kind. Interning and
structural equality use explicit stacks and identity memos. Cache clearing or
loss changes sharing only. The table-local hash mixes canonical child identities
to avoid repeated-accessor clusters in the structural hash. Value hashing remains
structural across stores.

## Tests before implementation

Commands use `./gradlew --console=plain` from `core`.
The first selected `TrieTest` and `PathWalkTest`. Compilation failed because
A03 APIs did not exist (`/tmp/bidi-a03-red.log`). The first production compile
passed, but test compilation found an inferred pair-list type mismatch
(`/tmp/bidi-a03-first.log`). The plan was updated before fixing those test types.
The focused bidirectional suite then passed (`/tmp/bidi-a03-focused.log`).

Tests decode sparse trees locally, without production trie operations or indexes.
They cover canonical factories in eight threads, exact/any columns, annotations,
array ownership, independent-store values, soft-cache epochs, policy and
cancellation. Deep tests use 32,768 edges; shared-DAG tests require only a small
number of live nodes. Review added mixed-tree decode/rebuild, repeated-accessor
interning, the Long node-count boundary, and independent shared-DAG equality.

## Gate

The full common suite and shared-consumer compile command was:

```bash
./gradlew --console=plain --continue \
  :opentaint-dataflow-core:opentaint-dataflow:cleanTest \
  :opentaint-dataflow-core:opentaint-dataflow:test \
  :opentaint-dataflow-core:opentaint-jvm-dataflow:compileTestKotlin \
  :opentaint-dataflow-core:opentaint-go-dataflow:compileTestKotlin \
  :opentaint-jvm-sast-dataflow:compileKotlin compileKotlin
```

The command passed in 28 seconds (`/tmp/bidi-a03-gate.log`): 324 common tests
in 27 classes, with no failures, errors or skips. JVM and Go production and test
sources, SAST and application compilation all passed. The final review tests are
checked by the subsequent common-suite run recorded below.

The final common run passed in 26 seconds (`/tmp/bidi-a03-final-common.log`):
328 tests in 27 classes, with no failures, errors or skips. A03 adds 20 `TrieTest`,
16 `KindFactoryTest`, 6 `PathWalkTest` and 11 `InternerTest` checks. XML is saved
in `/tmp/bidi-a03/final-common/xml`. Production did not change after the consumer
compile gate. `git diff --check` passes. No Lean change was needed; the rules and
implementation choices were clear. S00 had no baseline failures. A03 is complete.
