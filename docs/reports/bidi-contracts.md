# Bidirectional contract and reuse review

This report supports S00 of `spec/plan.md`. The specifications define behavior.
The current proposals define the public interfaces and the implementation order.
Historical code and proof vectors supply data only within their stated conditions.

## Inspected state

The inspected branch was `saloed/bidirectional`, at commit
`601737d6be69f41fdf5bf05efd3141d288872947`. At the start of this review,
`docs/reports/`, `formal/`, `run-debug/`, and `run-debug-2/` were untracked.
This review did not change those existing files or the old analyzer.
Baseline test commands and results belong to the separate S00 baseline report.

The review used these sources:

- `spec/plan.md`: S00 and A01–A03, working method, and observation rules.
- `spec/ap.md`: construction rules, W1–W8, zero, reference types, and AP representation.
- `spec/ap-impl.md`: Part I §2–§6 and Part II §23.4.
- `spec/validation-plan.md`: proposal review and AP test inventory.
- `spec/proof-status.md`: current proof guards and historical vector limits.
- `spec/implementation-background.md`: reuse map and historical interface warning.

## A01 interfaces and invariants

The new AP package is `org.opentaint.dataflow.bidi.ap`. It is separate from
the old `ap.ifds.access` package. It does not implement the old fact interfaces.
`MethodKey` is a type alias for `MethodEntryPoint`, declared in `Premise.kt`.
It includes the context and the forward entry statement.

| Value or API | Required contract |
|---|---|
| `Direction` | `FORWARD`, `BACKWARD`. |
| `Layer` | `NORMAL`, `DEMAND`. Demand cannot return to normal through an operation. Layer is separate from premise identity. |
| `Tail` | `STAR`, `ANY`, `ANY_TAINT`, `EXACT`. The tails and marks are payloads, not accessors. |
| `ApMode(run1, direction, fieldLimit)` | A run-1 mode is forward and has a limit of at least one. AP utility cuts at zero are tested later. |
| `AccessorTable.index/accessor` | Reuse `AccessorIdx` encoding. Admit field, element, and class accessors only. Reject mark, final, any, type-info, group, and value accessors. |
| `AccessorIdx.isCounted` | Fields and elements count. Class accessors do not count. |
| `TaintMark`, `MarkTable` | Intern mark names. Reserve ID zero for the zero mark. A rule does not name this mark. |
| `MarkSet` | Sorted, duplicate-free mark IDs; content equality and stable hash. Union, difference, intersection, and subset tests use that canonical content. |
| `MarkSlot` | `Concrete(T)` or `Star(X)`. `Star(EMPTY)` is `*`; a conclusion or added fact can retain `*∖X`. |
| `ExclusionSet` | Empty or a nonempty canonical concrete set. There is no Universe value. The set constrains the first accessor of a nonempty continuation; it always admits an empty continuation. |
| `PathNode` | An interned immutable accessor chain. Cache total size and counted size. The only AP depth bound is the field limit. |
| `InitialAp` | Base, path, tail, field exclusion, and full mark slot identify the key. A premise has `*` or a concrete mark, never `*∖X`. EXACT and ANY_TAINT require concrete marks. Only STAR and ANY_TAINT can carry field exclusions. |
| `ApManager.initial` | No mode parameter. Intern a full initial key. Do not merge must and may tails or distinct must exclusions. |
| `PremiseKey` | Nonempty; exposes `size`, `member(k)`, `isZero`, and `nonZeroCount`. A singleton is the `InitialAp` itself. |
| `PremiseSet` | At least two members; canonical order by intern ID; no duplicate or zero member; every member has a concrete mark. |
| `premiseOf` and `union` | Remove zero if a nonzero member exists. All-zero input gives the canonical zero. Deduplicate and sort. Return the direct singleton or an interned multiple-member key. |
| Zero | Exactly `(Zero, [], EXACT, Empty, Concrete(ZERO))`. This is also the singleton premise key `{zero}`. |

`InitialAp` and `ApManager.initial` do not know the run mode. A01 must not add
a mode argument to enforce the forward restricted must-premise rule. A07 and
E02 check the run-1 and backward rejection when the mode is known.

There is one dependency-order issue in the original A01/A02 split:
`InitialAp.toPattern()` and `ApManager.initial(Pattern)` need raw reference
values, and A01 calls for a demand-STAR shape test. Move the declarations of
`PathFact`, `Pattern`, and `Conclusion` into the A01 compilable slice, at the
proposal's `Reference.kt` path. Keep direct operations, `PathEdge`, and the
primitive types in A02. Record this change in the plan before implementation.

Keep the boundary between generic reference values and native keys explicit.
The reference can represent abstract EXACT geometry, for example when `inside`
temporarily ignores a fact's mark. The proposal explicitly retains generic
reduction forms outside `Facts`. An abstract EXACT initial key is invalid;
native FLOW packing also rejects abstract EXACT leaves. A global rejection in
`PathFact` would reject the specified reference computation. A canonical
conclusion cannot have a demand STAR tail. Demand ANY_TAINT becomes ANY with
Empty exclusion through normalization, before native packing.

## Canonical data ownership

The specification requires canonical mark sets, exclusion sets, and premise
arrays. It requires interned equal premise keys to be the same object. These
requirements need stable content while a key is in an interner or store.

The proposal exposes array fields in `MarkSet`, `ExclusionSet.Concrete`,
`InitialAp.pathArray`, and `PremiseSet.members`. Copying the proposal literally
would let a caller change canonical content after the cached hash is computed.
That can corrupt an interner or change the meaning of a stored premise.

Use owned private array storage. Copy mutable input arrays at construction.
Keep the required public value access through snapshots or read-only views.
Use internal access methods for hot paths if necessary. Check both input-array
mutation and returned-array mutation in tests. This is an implementation
requirement derived from canonical key identity, not a new analysis rule.
Apply the same ownership rule to A03 trie accessor and child arrays, because
the proposal explicitly requires immutable nodes.

## Shared base consumers

Add `Zero` and `CleanerTemporary` to the existing sealed `AccessPathBase` in
`ap/ifds/Accessors.kt`. `CleanerTemporary` is an ordinary class with object
identity and string form `<cleaner-temporary>`. A form builder makes one per
cached field action. Its reversed form shares the same object. I04 checks its
field-value type and lifetime. It must not equal a program local or zero.

The following current consumers need explicit internal-base handling. Prefixes
are those in `spec/plan.md`.

| Current file | Boundary to update |
|---|---|
| `DF/ap/ifds/AccessPathBaseStorage.kt` | Exhaustive `getOrCreate` and `find`. The old store rejects both internal bases. The new core does not use this store. |
| `DF/ap/ifds/serialization/AccessPathBaseSerializer.kt` | Exhaustive writer. Reject both bases; add no wire-format enum values. |
| `JVM/ap/ifds/JIRCallPositionResolver.kt` | Both `JIRMethodPositionBaseTypeResolver` switches. Reject internal bases at the language position boundary. |
| `JVM/ap/ifds/JIRMethodCallFactMapper.kt` | Exhaustive exit-to-return mapping. The old mapper does not receive internal facts. |
| `APP/jvm/sast/sarif/TraceMessageBuilder.kt` | Exhaustive base rendering. Reject both internal bases. |
| `APP/jvm/sast/project/spring/SpringRuleProvider.kt` | Exhaustive `cleanupPositions` conversion. Internal bases do not become rule positions. |
| `opentaint-go-dataflow/.../GoMethodCallFactMapper.kt` | Its existing catch-all silently drops unknown bases. Check explicit rejection of internal bases even though it compiles without a new branch. |

Other current consumers use `is` tests, equality tests, or a catch-all rather
than an exhaustive base switch. Those uses include liveness, statement builders,
trace preconditions, aliases, and the old sequent flow functions. Compile every
shared-base consumer, including Go and the root application, after the change.
Compilation alone does not prove internal-base rejection at a boundary that
already has a catch-all.

The new method-edge, summary, added-link, and source-hit stores reject a
`CleanerTemporary` when those stores are implemented. The temporary can occur
only within `CleanStep.Field`; it cannot escape the read/clean/write chain.
The old engine stays available for the prescan and the old full analysis path.

## Reuse and available reference branches

Both requested reference branches are available as local Git objects. This
review did not fetch or check out either branch.

| Reference | Inspected object and use |
|---|---|
| `origin/saloed/backward-main` | `b25397260ca1c978732ca3e89679eb5ed1c1f177`. Port `core/opentaint-dataflow-core/opentaint-jvm-dataflow/src/main/kotlin/org/opentaint/dataflow/jvm/ap/ifds/backward/JIRBackwardExitWiringGraph.kt`. It finds statements with no path to an exit, then connects them to the exceptional exit. Add the proposal's per-method cache. |
| `saloed/any-field-limit` | `85b7c614b936988e284e31102a8d6089f4533178`. Adapt limiter walks, sharing, and cancellation from `access/tree/AccessTree.kt`. Use exact counted-depth metadata from the current proposal. |

The field-limit branch has a saturated `Short` depth cache. Do not copy it.
A path of 32,768 counted accessors is incorrectly treated as fitting a budget
of 32,767 by that cache. A03 requires exact `Int` metadata and checked addition;
A06 tests the cut. `CurrentDepthCache` supplies the current local witness.

Reuse field/element/class accessor values and index encoding from `Accessors.kt`
and `AccessorInterner.kt`. Replace the old accessor storage with the proposal's
safe publication table. Reuse `MethodEntryPoint`, cancellation, reference
management, and the statement index layout. Adapt old path cells, tree
merge/delta, bottom-up soft interning, path filtering, subtraction, and path
indexes to the new values. Keep structural tree equality across stores.

Do not port old any-accessor unrolling, deep exclusions, initial-fact
abstraction, side-effect requirements, delayed depth scheduling, lossy sink
merging, or crossability APIs. `implementation-background.md` labels the
crossability interfaces and `crossableLeaves` counter as removed by F77.

## Historical fixture adaptations

Each imported vector must keep its module and version label. Adapt an expected
result only by a current specified rule. Compare raw operation results by full
leaf identity and requests separately. After store reduction, compare the union
of decoded locations and marks with the premise and layer keys.

| Historical data | Current fixture expectation |
|---|---|
| `Cases`, `RestrictedCases` | Keep their input and output provenance. Apply current W2/W6/W8 packing to reachable values. A may ANY input or target produces DEMAND. A forward must source produces normal ANY_TAINT. |
| Historical `lostCorr` examples | These exercise generic algebra outside reachable W2/S8 conditions. They do not define a below-case demotion rule. Use `CurrentSimplifications.LostCorr` for legal gated transfer and equal requests. |
| `AnyTaintCases.W.keep_demand` | Use `AnyTaintEx.Vec.setter_keep`: a strong write retains normal ANY_TAINT with the added first-accessor exclusion. |
| Round-1 `AnyTaint` vectors | Keep them labelled as the earlier no-exclusion design. Prefer `AnyTaintEx.Vec` and `AnyTaintExCases` for current field exclusions, reads, cleaner rows, cuts, and must emission. |
| F70/F71 `HandoffCases` and `HandoffMain` | Earlier closure results are comparison data. Build current hand-offs from selected stored publications only: DEMAND, multiple premises, or backward zero. Selection preserves the raw layer. |
| Pre-F72 mark exclusions in demand patterns | Current hand-off replaces `*∖X` by `*`. Raw records and added facts retain the full mark exclusion. Abstract demand emission uses a shared FLOW premise; concrete emission retains the added mark and computes the specified meet. |
| Pre-F74 named field cleaners | Lower to read, root clean on a fresh temporary, and strong write. Retain identities, keep edges, cut points, and reverse order. A primitive root cleaner is a separate fixture. |
| `ReviewRootCleaner` | Historical spatial counterexample, superseded by F75. Current selected-mark cleaning first changes every same-base abstract mark to `*∖(X ∪ {T})`; run 1 requests T before any path test, even if T was already excluded. |
| `ReviewBackwardCleaner` | Rejected abstract pass-through experiment. Current restricted cleaners make no requests but retain the F75 mark exclusion. |
| Pre-F76 reversed must records | Use transient Pattern/Conclusion read views. Preserve side marks and conclusion E; discard suffixes at exact targets. A must-premise view can return caller DEMAND while the native singleton summary has no callee demand. |
| Generic per-leaf demand selection | Replace with the F77 structural selector. Tail, mark, exclusion, or view-result layer does not add a native selection condition. |
| Pairwise leaf coverage for T2 | Use union denotation. `CurrentProposal.T2Oracle` shows that two differently excluded must leaves can merge to one empty-exclusion leaf even when mutual single-leaf coverage fails. |

The current local proof vectors do not establish the complete Kotlin engine,
the current full iteration, or concurrency. Missing global proof coverage is
not an implementation block under the plan's conditional proof policy.
Use Kotlin reference equivalence and the specified validation gates.

## Review disposition

No missing A01 behavior decision was found. The raw-reference/native-key
boundary and the A01/A02 dependency-order adjustment are implementation
clarifications. Follow the specification for these points and record the
scheduling change in the plan. Later steps must check modes, temporary escape,
current hand-off selection, and native record timing at their named gates.
