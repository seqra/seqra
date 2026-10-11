# Implementation plan for phases 1–3

Implement the JVM bidirectional analyzer in the order below. Each step has tests to write first,
implementation work, and a validation gate. Use Lean only to clarify unclear specified behavior,
as described below. Complete the validation gate before the next dependent step. This document
tracks the work; the implementation steps are not complete.

The behavior is defined by [ap.md](ap.md), [interpreter.md](interpreter.md), and
[analyzer-core.md](analyzer-core.md). Follow the interfaces and algorithms in
[ap-impl.md](ap-impl.md) and [analyzer-impl.md](analyzer-impl.md).
[validation-plan.md](validation-plan.md) gives the full test inventory.
[proof-status.md](proof-status.md) gives the scope and conditions of the existing proofs.
[implementation-background.md](implementation-background.md) gives the code reuse map.

The phases are those of [bidirectional-task.md](../bidirectional-task.md):

| Phase | Steps | Result |
|---|---|---|
| Preparation | S00 | Checked contracts, baseline and test fixtures. |
| 1: AP and stores | A01–A09, V1 | Typed facts, AP operations, reference operations and all stores. |
| 2: Analyzer | I01–I06, V2, E01–E07, V3, D01–D04, V4 | JVM forms, fresh run engines, summary communication and iteration driver. |
| 3: Taint analyzer entry | J01–J03, FINAL | Prescan hand-off, JVM entry and report output. |

The interpreter is a phase-2 prerequisite. Phase 4 owns full project benchmarks and trace-aware
parity. Phase 5 owns full trace resolution. Phase 3 returns the specified simple sink traces.
Go analysis is outside the feature scope. Shared type changes must still compile for Go consumers.

## Step status

Complete each gate before marking its step complete. Commit the completed step and its evidence.
The starting commit is `601737d6be69f41fdf5bf05efd3141d288872947`.

| Steps | Status | Evidence |
|---|---|---|
| S00 | Complete | [Baseline](../docs/reports/bidi-baseline.md), [class results](../docs/reports/bidi-baseline-tests.tsv), [contracts](../docs/reports/bidi-contracts.md), [fixtures](../docs/reports/bidi-fixtures.md). All baseline suites pass; all 12 fixture checks pass. SAST has no existing tests. |
| A01 | Complete | [A01 evidence](../docs/reports/bidi-a01.md). Common 196, JVM 119 and Go 1,313 tests pass. SAST and application compile. |
| A02 | Complete | [A02 evidence](../docs/reports/bidi-a02.md). All 275 common tests pass; none skipped. JVM/Go test sources, SAST and application compile. Both user decisions are applied. |
| A03 | Complete | [A03 evidence](../docs/reports/bidi-a03.md). All 328 common tests pass; none skipped. Deep and shared-DAG checks, concurrent factories and cache epochs pass. JVM/Go test sources, SAST and application compile. |
| A04 | Complete | [A04 evidence](../docs/reports/bidi-a04.md). All 391 common tests pass; none skipped. Merge/delta, T2 unions, 13,325 subsumption cells, normalized keys, deep/DAG operations and cancellation pass. JVM/Go test sources, SAST and application compile. |
| A05 | Complete | [A05 evidence](../docs/reports/bidi-a05.md). All 444 common tests pass; none skipped. Transfer, selection, all valid summary kinds, native views, deep/cache/concurrency and cancellation checks pass. JVM/Go test sources, SAST and application compile. |
| A06 | Complete | [A06 evidence](../docs/reports/bidi-a06.md). All 491 common tests pass; none skipped. Cleaner/filter/literal tables, positive cuts, layers/E, sharing, deep walks, state memos and cancellation pass. JVM/Go test sources, SAST and application compile. |
| A07 | Complete | [A07 evidence](../docs/reports/bidi-a07.md). All 528 common tests pass; none skipped. Emission, reduction, structural selection, request actions, representation guards, deep paths and cancellation pass. JVM/Go test sources, SAST and application compile. |
| A08–A09, V1 | Pending | |
| I01–I06, V2 | Pending | |
| E01–E07, V3 | Pending | |
| D01–D04, V4 | Pending | |
| J01–J03, FINAL | Pending | |

Implementation corrections: the earlier FINAL text described this file as a planning-only
deliverable. `task.md` now requires implementation. FINAL closes that implementation.
The A01 key factories already use `Pattern` and `InitialAp.toPattern`. Thus A01 adds the
`PathFact`, `Pattern` and `Conclusion` value declarations in `Reference.kt`; A02 adds their
operations. Raw reference values can represent abstract exact results of generic reduction
outside typed `Facts`, as stated in `ap-impl.md` §4.1 and §6. Initial and typed-fact factories
reject abstract exact shapes. Canonical conclusions reject demand STAR tails.
Key arrays use owned storage and public snapshots so callers cannot change interned content.
An A01 review found that foreign path suffixes can defeat canonical identity, and that the
union identity shortcut can bypass manager ownership. Path cells and premise inputs must
belong to their manager. Test ownership before these shortcuts.
The same review found the explicit-base `Position.resolveAp` overload. Add its internal-base
rejection with the other JVM position boundaries and retain valid program-base mappings.
A02 adds the `RequestKind` values at `store/RequestStore.kt`, because the public
`RequestAction` primitive needs them. A08 adds the request store. Add the `PathEdge`
compiled cache in A05, with its `CompiledEdge` type; the A02 reference has no tree dependency.
The A02 table review found a generic-cell conflict: the STAR-target row in `ap.md`
§4.1 tests the edge exclusion on a nonempty continuation, but both reference code
copies omit that test for a must premise with its own exclusion. The case is outside
native JVM micro edges and canonical summary kinds, but the generic AP API admits it.
The user resolved this case: follow the specification table. Test shared E for a
nonempty continuation to a STAR target, after the premise's own exclusion test and
before the mark gate. Enable the concrete regression and add admitted and rejected
suffixes, including abstract inputs that would otherwise raise requests.
Use the same combined premise/target admission in A05's compiled child filter;
the reference and tree forms must agree on this generic cell.
The A02 compile check found Kotlin syntax constraints in the executable snippets and tests.
Use plain tail names in diagnostic strings and use integer IDs or lists in test helpers;
Kotlin does not permit a value-class type such as `TaintMark` in a vararg parameter.
The A02 reference review also adds focused nested-prefix selector checks, all exit-mark
combinations, and the remaining cleaner and restriction cells. Keep the bounded oracle
independent; use extra path depth only for these focused cases.
The same review found a second generic utility conflict in `ap.md` §4.4. The cut rule
retains uncounted prefixes, and the reference code retains a class prefix at limit zero,
but the next sentence says that limit zero keeps the empty path. No analysis run uses
limit zero. The user resolved this case: run 1 starts at limit one, and limit zero
is impossible. Require a positive limit in `ApMode` and public cut operations. Replace
zero-limit utility expectations with rejection tests and positive-limit cut vectors.
Keep historical zero-limit proof data labelled as outside the current API domain.
The native reversal review found that generic fallback could accept an exact native
premise with a field exclusion, or a backward native must conclusion, when the target
exclusion is empty. Those inputs violate the native premise and W8(d) rules. Add explicit
native-boundary rejection before fallback, with failing regressions for empty target E.
Keep generic `revEdge` unchanged: it has a different contract.
One added cleaner test applied the concrete-mark partial-cleaning row to an abstract
ANY fact. `ap.md` §4.7 gives abstract all-marks partial cleaning its own row: preserve
the fact in DEMAND. Correct that expected value; do not change the reference operation.
Run the two retained pending witnesses once with their skip annotations removed.
Save the observed results, then restore the annotations. This check confirms the
code behavior; it did not resolve the contradictory rules or close the A02 gate.
The user has now supplied both decisions. Align the AP reference snippets and the
validation inventory with these decisions; no new Lean proof is required.
A03 uses owned accessor/child arrays with public snapshots and internal read access,
so external array mutation cannot change immutable trie content or metadata. Validate
sorted path accessors, aligned arrays and nonempty children at node construction.
Use iterative structural equality and bottom-up interning: a positive Int field limit
does not bound valid paths to the JVM recursion stack. Test the 32,768-depth case.
The A03 fact factories need a one-leaf path builder before the A04 trie operations
exist. Add a private iterative factory helper in A03; A04 adds generic prepend.
Typed FLOW and TAINT constructors reject the zero base; only REACH represents zero.
Raw zero `factsOf` inputs must have the canonical zero geometry and mark.
The proposal's shifted structural hash repeats on long repeated-accessor paths.
Keep structural value hashing, but mix canonical child identities in the table-local
interner hash. Its equality already compares child identity after bottom-up interning.
This prevents a long chain from occupying one linear-probe cluster. Cache count and
depth additions fail when their declared numeric type cannot represent the result.
The first A03 compile check found that Kotlin's generic `assertEquals` cannot infer
one type for a trie-node pair list and an `Any` pair list. Use the concrete leaf type
in the walk test's callback list; this is a test typing correction.
The A03 review adds a compact shared-DAG check at the Long count boundary and
compares two independent 32-level shared DAGs. These checks exercise checked count
failure and equality's pair memo without allocating their expanded path count.
Use a repeated accessor in the deep interner test to exercise the table-local hash
correction; distinct accessors do not expose that structural-hash cluster.
Complete A03's decode/rebuild gate with a mixed sparse FLOW and TAINT round trip.
The test rebuilds from its local decoded leaf list, without A04 merge operations.
A04 replaces the recursive proposal helpers with explicit postorder stacks.
Preserve identity sharing and use identity memos for whole-subtree maps and pair
memos for merge and subtraction. Leaf enumeration keeps one mutable path buffer,
then copies it only for a leaf callback, so a deep one-leaf path has linear cost.
Generic long-walk helpers that have no manager accept an optional cancellation
argument. Store and result callers pass their analysis cancellation; prepend and
chain already receive their manager. This preserves cancellation without hidden
mutable state in the shared FLOW algebra. Keep optional T5 folding disabled.
The generic-helper contract returns null for an empty result. Apply that rule
also when `withLeaf` receives an already empty node with an unchanged empty leaf;
the proposal's identity shortcut must not return an empty subtree there.
The first A04 compile check requires an explicit nullable-node pair result type
for the merge/delta loop; otherwise Kotlin infers `Nothing?` from the same-node
case. A source review also requires `minusNode` to distinguish an absent matching
child from a present child whose computed remainder is null. The latter removes
the child; a null-coalescing shortcut must not restore it.
A04 keeps the proposal's positional callback arguments through forwarding
overloads. The added cancellation forms must not break calls that pass a named
predicate or function value as the original positional argument.
The subtraction review adds a shared-child witness with different inherited
cover on its two paths. Its memo key must include the cover value as well as both
node identities; a node-pair-only memo could remove the excluded child too.
The complete A04 test compile needs explicit `Facts` types for expected REACH
lists and integer-ID varargs for a mark-set helper. Kotlin does not infer one
assertion type across those collection element types or allow value-class varargs.
The A04 deep-path gate exposed a path-cache hash cluster: accessor zero repeated
32,768 times gives the same public path hash for every suffix. The prepend/chain
test took 5.488 seconds, while other deep operations took at most 0.062 seconds.
Keep the proposal's public path hash. Key the manager's private path table by
accessor value and canonical suffix identity, with a matching local hash. Test
repeated deep path lookup by both array and list. This changes sharing cost only.
Add a direct positional function-value check for the generic callback overloads.
A05 adds the `without` facade from §5.8 together with satisfaction. A04 already
provides its generic subtraction. It is needed to expose the native record's
inside and applicable-only parts at the A05 gate; A06 adds the remaining mark
check and removal helpers. Both parts retain their ordinary layer and E. The
caller owns the later pending demotion. Keep per-call cancellation explicit in
all tree maps, folds, grafts and path walks.
A05's compiled edge owns its from-path array and exposes a snapshot, with internal
read access for walks. Reuse a cached to-path only when it belongs to the current
manager; otherwise compile it again. This preserves A01's path ownership and the
immutable compiled-cache contract when a raw edge is reused by another manager.
The static root/class check uses the existing accessor-index `isClass` extension;
the accessor table supplies values but has no class-test method.
In `leavesNear`, copy a prefix only when an admitted any leaf is present. Empty
TAINT prefixes produce no pattern. This avoids quadratic path-array allocation
on a deep path and preserves the proposal's selected leaves.
The A05 review found that the static-depth shortcut omits the premise mark test.
`ap.md` §4.1 requires an abstract `*` premise for the identity static exception.
K6 gives this in stored FLOW edges, but the public operation must test the stated
condition too. Add a concrete-STAR static-premise regression, observe ordinary
mark requests, then add the missing `i.mark == MarkSlot.STAR` guard.
A06 replaces recursive cleaner spines, path filters, element-policy walks and
field cuts with explicit stacks or loops. A valid positive limit can be 32,768
or more. Preserve unchanged sharing and cancellation. Path-filter memos key
both node identity and filter identity; field-cut memos key node identity and
remaining budget. Their results must not depend on the path to a shared node.
The first A06 test compile found two `ExclusionSet` types through wildcard
imports of the old and new AP packages. Add an explicit bidirectional
`ExclusionSet` import in the filter test. Keep the old API unchanged.
Extend the deep counted-path cut check to `L = depth - 1`, in addition to the
all-fit and `L = 1` cases. This exercises a long cut walk with many remaining
budgets. Check its one known output by a linear path walk, without a quadratic
test decoder. Retain the deep uncounted-class walk at remaining budget zero.
A07 adds the `CallerRef` value declaration in `store/AddedFacts.kt`, because the
specified `requestAction` signature uses it. A08 adds its store and link types.
`startFact` has no mode parameter, as specified. It constructs the initial's
normal form; E03's initial-start boundary enforces the restricted-forward
must-premise condition before this operation.
The first A07 compile check found that `CommonInst` is in the common `cfg`
package. Use that existing type for `CallerRef.call`.
One A07 test passed an unmatched FLOW value to `applySummary` for a concrete
premise. That operation takes a satisfying part. Check that satisfaction is
null, then keep the standing-request climb assertion; do not call transfer
with an input outside its contract.
The A07 review adds admitted abstract ANY and concrete STAR initials to the
reduction matrix. Repeat the gate with all 7,722 cells before the commit.

AP means access path. A fact's denotation is the set of marked locations that it describes;
a summary describes pairs of input and output locations. Field exclusions and mark exclusions
are separate annotations. NORMAL and DEMAND name layers; a selected demand edge can be NORMAL.

## Working method

For phases 1–3, including preparation and validation, the conditional proof policy below replaces
proof-before-implementation requirements in [task.md](../task.md) and the proposals, including
`analyzer-impl.md` §9.3. The specified behavior, interfaces, algorithms and tests still apply.

Use the following cycle in every step:

1. Read the relevant specification and proposal sections. Follow behavior that they clearly define.
   Resolve an unclear case through the clarification rule below.
2. Write Kotlin tests from the specified table rows, examples and executable reference operations.
   Compare indexes, tries and scheduling with their direct reference behavior.
3. Run the tests and observe the intended failure. A missing API can be the first failure;
   rerun after it compiles to check behavior.
4. Implement a complete, compilable slice from the proposal. Run its focused tests, then the checks
   affected by the slice. Refactor after these checks pass.
5. Record the test commands, results and any unresolved behavior question. Advance when the
   implementation gate passes and its required behavior is clear.

For a counterexample, retain the smallest program, full fact annotations, requests, links,
summaries and hand-offs. Compare them with the specified behavior and fix an implementation
error through a regression test. Use the clarification rule when the expected behavior is unclear.
A routine implementation choice can follow the proposal and repository code.

### When behavior needs clarification

Use a focused Lean proof or counterexample only when the specification and proposal do not
clearly cover a particular situation, but the specification supplies the definitions needed
to decide it. For example, derive the expected facts for an unclear summary-application edge
case from the specified matching and transfer rules. State the question and relevant rules,
model that case, then add a regression test and clarify the applicable document.

If a required definition is missing or the documents contradict each other, retain the concrete
example and get a behavior decision. A proof must not invent a missing rule. Pause only the
dependent work while the decision is pending.

Ordinary implementation, a new optimization, integration work or an open general proof does
not require new Lean work. Existing checked vectors can supply test data within their stated
conditions. Historical vectors retain their version labels. Missing proof coverage alone does
not block implementation of covered behavior.

When this rule requires Lean changes, use constructive definitions and computed witnesses.
Import new modules from `lean/ApSpec.lean`, add executable certificates to `lean/audit.lean`,
and run the build and audit. The audit permits only `propext` and `Quot.sound`; `sorryAx`,
classical choice and native proof oracles fail it. Update `proof-status.md` for changed results.
Run these checks when Lean changes, not as an automatic gate for every implementation step.

### Paths and test observations

Use the current modules. These prefixes name Kotlin source roots:

| Prefix | Path |
|---|---|
| DF | `core/opentaint-dataflow-core/opentaint-dataflow/src/main/kotlin/org/opentaint/dataflow` |
| JVM | `core/opentaint-dataflow-core/opentaint-jvm-dataflow/src/main/kotlin/org/opentaint/dataflow/jvm` |
| SAST | `core/opentaint-jvm-sast-dataflow/src/main/kotlin/org/opentaint` |
| APP | `core/src/main/kotlin/org/opentaint` |

Tests use the corresponding `src/test/kotlin` roots. Common AP, forms, engine and driver tests use
`org.opentaint.dataflow.bidi`. JVM form and entry tests use `org.opentaint.dataflow.jvm.bidi`.
Keep `NaiveClosure` and schedule controls in test sources. Keep `Reference.kt` and
`FormsReference.kt` at their proposal source paths. Keep the public names from the proposal;
combine declarations that depend on each other in one compilable patch.

Compare raw operation outputs by full leaf identity: base, path, tail, field exclusion, mark,
mark exclusion and layer. Compare requests separately. After a subsuming store merges leaves,
compare the union of decoded locations and marks in both directions, with premise and layer keys.
Pairwise leaf coverage is insufficient for the T2 merge witness in `CurrentProposal.T2Oracle`.
Use a bounded test universe with fresh accessors and marks to expose omitted exclusion cells.

For whole runs, also compare raw summaries, reduced publications, selected demand edges, added
links, source hits, sink witnesses, support, seeds and both hand-offs. Finding counts alone do
not establish equivalence. Preserve method keys, call sites and rule alternatives in observations.

## S00. Establish the baseline and test fixtures

**Requires:** the finalized specifications and proposals.

**Tests first.** Make a test fixture for per-path facts, requests, method keys and stable forms.
Record which historical vectors need the current tail/layer or hand-off expectation. Read the
relevant spec/proposal sections and use the clarification rule only for a concrete unclear case.

**Implementation preparation.** Record the starting commit and repository state. Run the
existing tests in the affected Gradle builds. Preserve unrelated work. Inspect the reuse files
and the locally available backward/field-limit reference
branches; use the proposal contract when a reference branch is unavailable. Keep the old engine
available for the prescan and old analysis path.

**Gate.** Record baseline failures by test and message, with fresh per-module results. Establish
the commands in the command section below. Baseline failures remain visible throughout the plan.

## Phase 1: AP and stores

### A01. Add fact values, paths and premise keys

**Requires:** S00. **Proposal:** `ap-impl.md` §3, §5.1; reuse §2.

**Tests first.** Write `FactShapeTest`, `PremiseTest` and mark/accessor value tests for the
specified factory and union rules. Reject invalid abstract exact facts and demand STAR
conclusions at shape construction. `InitialAp` and `ApManager.initial` have no mode argument;
schedule backward/run-1 must-initial rejection in A07/E02, where mode is known.

**Implement.** Add `bidi/ap/Facts.kt`, `AccessorTable.kt`, `PathNode.kt` and `Premise.kt`.
Add the per-path value declarations in `Reference.kt` needed by these factories; A02 adds
the direct operations. Use private owned arrays and public snapshots for canonical key values.
Intern accessor and mark values; reserve the zero mark. Count field and element accessors,
but not class accessors. Store a singleton premise directly; sort and deduplicate multiple
members. Premise union drops zero unless all members are zero. Full tail, mark and exclusion
data remain part of premise identity. Add the manager's key factories with these checks.

Generalize `AccessPathBase` with `Zero` and `CleanerTemporary`. Update exhaustive consumers;
position resolution, serialization and trace code reject these internal bases. Define the
temporary type here; its lifetime and field-value type are checked in I04.

**Gate.** Factory and union properties pass. Distinct must/may and distinct must exclusions
remain distinct keys. Compile every consumer of the shared base type, including Go.

### A02. Implement the direct per-path AP model in Kotlin

**Requires:** A01. **Proposal:** `ap-impl.md` §6 and the primitive types in §5.

**Tests first.** Write denotation, selector and transfer vectors from the current AP tables
and examples before tree algorithms. Existing checked vectors can supply cases within their
stated conditions. Label historical vectors and adapt expectations only by a current rule.

**Implement.** Add `PathFact`, `Pattern`, `Conclusion`, `PathEdge`, result/request values and
the direct operations in `Reference.kt`. Add the public primitive data types in `Primitives.kt`.
Include matching, transfer, cleaner position, cut, mark checks, emission, reduction, generic
reversal and native record views. Keep generic reversal and transient native read views separate.
Implement R1 eligibility and structural demand selection as separate functions.

**Gate.** `DenotationTest`, `ApplyEdgeVectorsTest` and direct operation tests pass for every
admitted table cell. Outputs and requests match the specified vectors. The oracle uses no
production trie or candidate index. This reference is the test basis for A03–A09.

### A03. Add immutable tries, conclusion kinds and interning

**Requires:** A01–A02. **Proposal:** `ap-impl.md` §4.1, §4.5–§4.6, §5.1–§5.2.

**Tests first.** Write `TrieTest`, `KindFactoryTest`, `PathWalkTest` and `InternerTest`.
Check typed-trie decoding against reference leaves, factory shapes and exact counted-depth
metadata at node construction.

**Implement.** Add `Trie.kt`, `Conclusions.kt`, `TrieInterner.kt`, `PathWalk.kt` and `ApOut.kt`.
Implement `TrieNode<P>`, leaf algebras, Reach values, FLOW and TAINT conclusions. The method-edge
REACH bit sets belong to A08. A normal TAINT any leaf denotes must/E; a demand TAINT any leaf
denotes may with Empty E. Complete the Facts
factories in `ApManager`. Use immutable nodes, per-store soft interning and structural equality
across interner tables. Share `walkPath` across trie consumers. Add the `ApOut` interface here;
complete its group-based Results collectors and StoreInterners factories in A04, after their
dependencies.

**Gate.** Decode/rebuild and factory checks pass. Clearing an interner cache changes sharing
only. Equal trees from different stores compare equal. Run concurrent manager-factory checks;
single-writer store interners keep their specified ownership.

### A04. Implement trie merge, delta, groups and subsumption

**Requires:** A02–A03. **Proposal:** `ap-impl.md` §4.2–§4.4.

**Tests first.** Write `TrieMergeDeltaTest`, `GroupMergeTest`, `TaintGroupKeysTest` and
`SubsumptionTest`. Compare T1–T4, subtraction, group placement, stored union and delta behavior
with the direct list reference. Include the T2 union witness and test both insertion orders.

**Implement.** Add `TrieOps.kt` and `Groups.kt`. Adapt merge, delta, leaf maps, prepend, chain,
minus, graft, subtraction and filtering from the old tree algorithms. Preserve sharing and
cancellation checks. Apply T2 only to the allowed equal-content groups, by intersecting
exclusions. Normalize the destination group key after subtraction. Implement required incoming
subsumption and exact duplicate handling. Normal must leaves never absorb exact leaves.
Complete `Results`, `CollectingOut`, `ConclusionGroup` and the group/interner factories needed
by later AP operations in this same slice. Use traversal methods that handle the tested depths
without relying on a small JVM call stack.

**Gate.** Stored union and delta properties pass in both insertion orders, including changing
must exclusions. Optional T5 demand absorption can remain disabled. If enabled, test equivalence
with the reference and measure its cost; it is not a prerequisite for later steps.

### A05. Implement transfer, satisfaction and summary application

**Requires:** A02–A04. **Proposal:** `ap-impl.md` §5.3–§5.4, §5.11.

**Tests first.** Write `EdgeTreeEquivalenceTest`, `SummaryKindsTest`, `ReversalTest` and
summary selector tests. Compare tree-selected leaves, matches, annotated outputs and requests
with the per-path reference. Cover canonical and noncanonical FLOW premises and guarded
must-tail read views.

**Implement.** Add `MarkGate.kt`, `EdgeApplication.kt`, `Summary.kt` and the `ApOps` facade.
Implement `applyCompiledEdge`, `satisfying`, `applySummary`, `applyRecordLeaf`,
`applyCombination`, `reverse`, `leaves` and `leavesNear`. Geometry precedes mark requests.
Canonical FLOW satisfaction is annotated overlap; noncanonical FLOW keeps the full guards.
For already matched concrete combination members, join their result layers and the summary
layer. Preserve distinct input/output marks, field exclusions and exact-target suffix dropping.

Native read views remain transient Pattern/Conclusion pairs: never emit, start or persist them.
Expose the native record inside/applicable-only result split. The caller applies the pending
demotion at E05, after returned actions and mappings; do not clear its E here.

**Gate.** Every kind pair and full-combination cell agrees with the reference, including concrete
STAR/E premises and must exceptions. Malformed view inputs are rejected. A view's caller
DEMAND output does not change the native summary's callee-demand classification.

### A06. Implement cleaners, filters, mark checks and field cuts

**Requires:** A02–A05. **Proposal:** `ap-impl.md` §5.5–§5.8.

**Tests first.** Write `CleanerTest`, `TypeFilterTest`, `MarkCheckTest`, `FieldLimitTest`
and `LayerRulesTest` from the primitive AP tables. Compare full facts and requests, including
counted-depth and layer behavior. Keep primitive cleaners separate from named field actions.

**Implement.** Add `Clean.kt`, `Filter.kt`, `Limit.kt` and `MarkCheck.kt`. A selected-mark cleaner first
updates every same-base abstract mark `*∖X` to `*∖(X ∪ {T})`, before any spatial test. Run 1 also requests
T, even when already excluded. Different bases pass; restricted runs create no requests.
Concrete and all-mark cleaners keep their spatial table. Apply path filters, then mark policy.
For an accepted TAINT literal, retain its input layer; conjunction joins occur later.

Implement exact counted-depth metadata and cuts. Retain class accessors without charging depth.
Use the specified Empty-E demand conversion for cut or other demoted any results. Preserve W3
at store insertion. All limits are positive. Reject `L = 0` at the public AP boundary.

**Gate.** Full fact and request equality passes, including off-spine leaves, disjoint same-base
cleaners, root EXACT regressions and the 32,768-depth cache case. Filters preserve unchanged
sharing. All required demotion cells match the reference.

### A07. Implement demand emission, reduction and request actions

**Requires:** A02–A06. **Proposal:** `ap-impl.md` §5.9–§5.10.

**Tests first.** Write `EmissionTest`, `RestrictionTest`, `DemandSelectionTest` and
`RequestActionTest`. Compare emission and reduction with per-leaf reference results, including
demand-entry shapes, modes, marks and layers, before using tree shortcuts.

**Implement.** Add `Demand.kt` and the remaining `ApOps` operations. Run 1 emits the policy
FLOW premise. Abstract demands share a FLOW premise across added concrete marks; concrete
demands reject abstract added marks and emit the current meet. A must initial starts in NORMAL
with its E, only in a restricted forward run. Restriction checks the whole premise in locations
and marks, then the demanded exit location and mark, and keeps the raw layer.

Implement request answer/climb operations for run 1, including static position requests.
Implement `demandPart` from raw layer, premise count and backward zero status. Selected pieces
keep their layer. Preserve AP §6.4's representability exceptions; do not narrow an abstract mark
or correlated tail beyond the specified meet.

**Gate.** Emission and publication outputs match the specified vectors. The per-leaf reference
and tree implementation agree on required examples and generated cases. Raw record
eligibility remains independent of reduction and demand selection.

### A08. Implement path indexes and the run fact stores

**Requires:** A03–A07. **Proposal:** `ap-impl.md` §7.1–§7.7, §7.9.

**Tests first.** Write `PathTrieTest` and tests for method edges, initials, links, raw
summaries, demands and requests. For each index, compare candidates followed by full matching
with a complete list scan. Check new-key and delta behavior against the reference.

**Implement.** Add `PathTrie`, `MethodEdgeStore`, `InitialFactStore`, `AddedFactStore`,
`RunSummaryStore`, `DemandStore` and `RequestStore`. Added facts group by exact caller reference,
link layer and conclusion group key. Keep every caller reference even when an added fact already
exists. Summary storage retains raw leaves and a separate selected-publication collection.
`DemandStore.Builder` deduplicates exact normalized patterns; `near` and `covering` return
candidates with the specified implicit-zero behavior.

**Gate.** All query results equal complete scans after matching. Use enough entries to exercise
every index branch. Stored unions and insertion deltas match the reference contract, including
the T2/T4 case that returns the whole merged tree. A delta can include old denotation; it must
retain all required new propagation. Snapshot demands are immutable. Run-local stores have one
owning writer.

### A09. Implement standing joins and persistent records and witnesses

**Requires:** A05–A08. **Proposal:** `ap-impl.md` §7.8, §7.10–§7.12.

**Tests first.** Write `KaryJoinTest`, `RecordStoreTest`, `ConjunctionStoreTest`,
`SourceHitStoreTest` and `VulnerabilityStoreTest`. Compare joins with complete finite scans;
check arrival orders, duplicate inputs, literal slots, contexts, alternatives and support
shapes. Check that witness merges retain every distinct witness.

**Implement.** Add `KaryJoin`, `StandingJoin`, `ConjunctionStore`, `NdSummaryJoin`, `RecordStore`,
`SourceHitStore` and `VulnerabilityStore`. Keep rule alternatives and method keys separate in
literal joins. A full forward conjunction is NORMAL exactly when all effective contributions
are NORMAL. Multiple premises remain a joint requirement at one call.

Persist only raw R1 normal singletons, excluding backward zero-premise summaries. Retain native
direction and annotations; record views are read-only. Key a vulnerability by rule, context-free
method and statement. Keep context, alternative, run and support shape in its witnesses. Use
atomic lossless witness merges and the specified source-hit identity.

**Gate.** All arrival orders and duplicate inputs retain every required combination. Repeated
literals occupy their distinct slots. Different calls or contexts cannot supply a joint witness.
Record persistence and demand selection pass separate truth tables. RecordStore writes occur
only at the joined run barrier. VulnerabilityStore accepts concurrent runner writes; the driver
reads its completed-run snapshot at the barrier.

### V1. Validate Phase 1

Run all AP/store tests and the shared-consumer compile checks.
Complete the AP/store-local portions of validation items 1–12, 14–15 and 17–18. Keep named form,
call and whole-run checks assigned to V2/V3/V4/J03. Review every AP public operation and store in `ap-impl.md` §0.2
against its specified contract and tests. Phase 2 can now use the AP without stub transfer semantics.

## Phase 2: Interpreter, engine and driver

### I01. Implement common forms and all three application modes

**Requires:** V1. **Proposal:** `ap-impl.md` §23.1–§23.3, §23.5–§23.8.

**Tests first.** Write `StatementSummaryReversalTest`, `RuleStatementReversalTest`,
`CallPlanReversalTest` and `FormApplierTest` before production mode execution. Check action
order, filters, source hooks, pass-over behavior, mode guards and identity candidates.

**Implement.** Add the `bidi/interp` interfaces, micro edges, statement/rule forms, call points,
stages, guards, plan reversal, `FactAlgebra`, `FormApplier`, forms cache and `DirectedForms`.
STATEMENT, STAGE and GEN retain their distinct identity/pass-over rules. Use one ordered Actions
stage and nullable `identityCandidate`; keep `StageKind` independent. Backward reversal reverses
action order and stage endpoints, removes forward-only filters/guards, and retains end-fact triggers.
Split a reversed conjunction into independent DEMAND edges; backward forms contain no conjunctions
or must tails. Generic source reversal remains separate from the native summary-view reader.

Add `ReferenceAlgebra` and `FormsReference` in `DF/bidi/interp/FormsReference.kt`. Preserve the proposal's
`pendingDemotion` run argument and finalization order; no extra Facts or PlanItem flag is needed.

**Gate.** Directed modes agree with the per-path operations, including untouched paths and seed
hook order. Reversed alias targets have pass-over identities. Reversal tests cover both graph
directions and supported resume points, rather than assuming every lossy reversal is an involution.

### I02. Implement the shared micro-edge builder

**Requires:** I01. **Proposal:** `ap-impl.md` §24.

**Tests first.** Write `MicroEdgeBuilderTest` and construction-invariant assertions from
the read/write/alias rows of `interpreter.md` §2. Check each generated edge's effect with the
per-path AP reference and independent expected vectors.

**Implement.** Adapt `StatementSummaryBuilder` into `MicroEdgeBuilder`. Build explicit targets,
touched bases, strong and weak keep edges, read identities and alias edges. Combine filters
without changing their order. A field read has no refine-only edge or exclusion update.
A strong write adds the specified first-accessor exclusion; a weak write keeps the old part.

**Gate.** Builder rows and decoded effects match the specified vectors and per-path reference.
Test self reads/writes, two-accessor static writes, root copies and alias targets. Synthetic
two-level AP writes remain separate from JVM lowering through local temporaries.

### I03. Implement JVM statement forms and type filters

**Requires:** I02. **Proposal:** `ap-impl.md` §25–§26.

**Tests first.** Write `JIRStatementFormsTest`, `JIRTypeFiltersTest` and
`JIRStatementEffectTest`. Use explicit construction and effect vectors for each admitted JVM
instruction, with the per-path AP reference where applicable.

**Implement.** Add `JIRInterpreter`, `JIRStatementForms` and `JIRTypeFilters`. Reuse JIR values,
local aliases and the fact type checker. Cover copy, field/static/element read and write, cast,
operators, allocation, return, throw and the unchanged path. Apply forward path filters and
mark policy at root and every element level; backward forms omit them. Keep method statement
forms stable in the cache and use the new builder rather than the old fact-local engine.
Expose the existing checker's `localFilter`, `fieldAccessorType` and `fieldClassType` helpers
with the proposal signatures; keep the old filter entry points for the prescan.

**Gate.** Every JVM row in `interpreter.md` §2.2 and §2.4 has a construction and effect test.
Primitive-tracking marks survive the specified byte/element cases. Existing old-path statement
tests still pass; the new forms carry the changed expectations in their own tests.

### I04. Implement rule forms and lowered field cleaners

**Requires:** I01–I03. **Proposal:** `ap-impl.md` §23.4, §27.

**Tests first.** Write `JIRRuleFormsTest`, cleaner mapping tests and lowered-action
vectors with limits 1, 2 and 3. Check action order, temporary projection, result filters,
touched-output cuts, source tails and the original premise used for a cleaner request.

**Implement.** Validate a complete rule before producing forms. Resolve positions, non-mark
conditions, DNF cubes and array sink alternatives. Enforce position/mark/source/pass constraints
and retain rule-error reporting. An AnyField source target has the must tail; an AnyField pass
target has the may tail. Keep the exact/STAR target shapes of other source/pass forms. Validate
pass-rule mark-condition restrictions and reject an invalid rule without partial rewriter effects.
Cleaners act only when static evaluation leaves true with no positive or negative mark literal;
rewriter selection uses its separate rule. Build call, read, entry and exit rule forms through
the common operations.

Lower a named nonstatic terminal-field cleaner `clean(x.f, T)` to a fresh field-value temporary:
read, root clean, strong write-back, then project the temporary. Cut each touched read/write output
after its result filter; untouched pass-over is uncut. Give the temporary the field value's type.
Reverse the whole action order with the same cached temporary for backward runs. Root, element
and static cases keep the specified primitive/killing forms.

A temporary FLOW cleaner can raise a run-1 F75 request. Store that request on the incoming
original premise; do not drop it because its transient input base is temporary. Project temporary
facts before run storage/publication. Temporary bases cannot enter published or request premises,
serialization or traces.

**Gate.** Primitive and lowered tests have separate expected vectors. Check no-op cleaners,
already-excluded marks, static positions, string-byte positions, conditional-source DNF,
conjunctive sources and array sink seeds. Run construction assertions for every admitted form.

### I05. Implement JVM call plans, bindings and aliases

**Requires:** I03–I04. **Proposal:** `ap-impl.md` §28.

**Tests first.** Write `JIRCallPlanTest`, binding tests and forward/backward order
vectors for resolved, unresolved and constructor paths. Compare with the per-path forms
reference, including returned actions, sibling mappings, cuts and native pending candidates.

**Implement.** Add `JIRCallPlanBuilder`. Construct BEFORE/BOUND/ADDED/RETURNED/REWRITTEN/AFTER
stages with the proposal's bindings, sinks, end facts, Actions, callees and alias mappings.
Preserve S at every call. Keep zero pass/entry/return paths separate. Exclude empty methods from
resolved callees; use unresolved pass/default behavior when no resolved method remains.
Use copied prescan lambda targets to select the resolved path. Notify the external-method
observer without using it as a transfer guard.

Build aliases from the rewritten result as sibling mappings beside bind-back. Their guard uses
the current layer and identity candidate. Native pending results retain E and enter these
mappings with `identityCandidate=false`; the engine finalizes them in E05.

**Gate.** Call order, zero/static paths, rewriter selection, aliases and reversed pass-over match
the reference plan. Include killed results, multiple resolved targets, unresolved identity,
constructor and lambda calls. No extra compatibility or liveness filter is introduced.

### I06. Implement method boundaries, cache and Spring forms

**Requires:** I03–I05. **Proposal:** `ap-impl.md` §29–§31.

**Tests first.** Write `JIRBoundaryFormsTest`, `JIRFormsContractTest`, method-cache tests
and `SpringDispatcherFormsTest`. Check entry/end-rule order, normal/exceptional boundary
wiring, seed placement and zero-premise static/entry-mark removal independently.

**Implement.** Add `JIRBoundaryForms`, `JIRExitWiredGraph`, `JIRMethodEntry`, `JIRMethodForms`,
`JIRMethodEntries` and `PrescanLambdas`. Reuse backward graph utilities and port exit wiring.
Cache statements/exit rules per method and calls/entry rules per method key. Run exit rules
at both exits with exceptional Result=exc; only the normal forward exit emits a summary.
Remove all entry-mark leaves only from eligible zero-premise items. Keep evaluated caller-set
static state and the independent conjunctive exit-source slots.

Adapt the Spring dispatcher in APP: save registry fields, run the fact-free whole-static cleanup,
then restore them. Attach the existing `Cleaner(ClassStatic, null, AT_AND_BELOW, null)` to the
generated cleanup method. Read it through `CallCleaners` when building the call's Actions stage.
Keep the current fact-based `SpringRuleProvider` path for the prescan and old full scan.
Preserve the current alias policy. Use frozen lambda values without retaining
the prescan subscriptions; J01 supplies the production extraction and release path.

**Gate.** Check virtual-exit wiring and preserve the specified catch timing and class-initializer
omission. Check context cache identity, both-exit seeds, all-depth entry-mark removal and cleanup
form effects. Full two-controller propagation follows in J03. Construction scans enforce interpreter
invariants I1–I14 and the AP producer duties on every forward/backward form.

### V2. Validate interpreter integration

Run common-form and JVM-form tests and type/effect equivalence tests.
Complete all JVM construction cases in the Interpreter inventory; reserve whole-analysis cases
for J03. Compare named field actions with F74, their primitive cleaner with F75, and the pending
return path with late demotion. Every cached form used by a standing join has stable identity.

### E01. Add the run shell

**Requires:** V1–V2. **Proposal:** `analyzer-impl.md` §3, §6.

**Tests first.** Write `InFlightTest`, `EventQueueTest`, context-cache and run-lifetime
tests for the Q1–Q4 ownership/count transitions before adding the asynchronous run shell.

**Implement.** Add `SharedObjects`, `RunConfig`, `RunStatus`, `InFlight`, `RunEvent`,
`RunnerPort`, `UnitRunner`, `RunManager` and `MethodContextCache` in `bidi/engine`.
Adapt unit routing, channels, runner quantum, memory guards and the analyzer table.
Allocate fresh managers, scopes, counters, queues and analyzers per run. Share only the
specified analysis objects. Treat records and demands as immutable run inputs.

**Gate.** Event send/consume, local work and start batches keep the completion count live.
Unknown-unit events are discarded before counting; rejected sends cannot leave a phantom count.
A second run has no queued/analyzer state from the first. The shell can start and join a
zero-work run. Handlers implemented later must use these same count transitions.

### E02. Implement method-local transfer and boundaries

**Requires:** E01. **Proposal:** `analyzer-impl.md` §4.1–§4.4, §4.7; `analyzer-core.md` §4.

**Tests first.** Write `EngineAlgebraTest`, `ModesTest`, `FactKindsTest`, `ExitRulesTest`
and `CutPointTest` before each handler. Cover initial emission/start, statement, unchanged and
boundary transitions, kind preservation and the absence of restricted-run requests.

**Implement.** Add `RunMethodAnalyzer`, `DeltaWorklist`, `EdgeDelta`, `EngineAlgebra` and handlers
for roots, links and initials. Use `FormApplier` for all modes. Emit from links by run-1 policy
or the current demand and exact-deduplicate initials. Apply entry/end rules in the specified
order. Cut after result filters at STATEMENT, ENTRY_RULES, EXIT_RULES and SEED points.
Keep the processed fact on the unchanged path; it has no postprocessor or liveness gate.

Store raw summary deltas before reduction. Restricted nonzero publication uses the current
reduction; run 1 and backward zero-return publications use their specified bypass. Keep the
original layer and raw selection status on stored publication pieces. Count accepted demand-layer edge, summary
and link deltas. A demand link alone can prevent the no-demand stop.

**Gate.** Single-method closure agrees with the direct per-path fixture for both directions.
Check zero-rule placement, both exits, static position requests, unreachable method ends,
off-spine facts and raw summaries rejected for publication.

### E03. Implement standing requests, sinks and conjunctions

**Requires:** E02. **Proposal:** `analyzer-impl.md` §4.8–§4.9, §4.11.

**Tests first.** Write `RequestJoinTest`, sink/end-fact tests and the local parts of
`NdJoinAdapterTest`. Check request/link arrival orders, context-isolated joins, literal layers
and source-hit timing against the specified cases and per-path reference.

**Implement.** Add the run-1 request/link standing join, answer and climb handlers. Handle
request-first, link-first and a later new caller reference to an existing added fact. No
restricted run allocates request machinery. Add positive literal joins, sink witnesses,
end-fact propagation and trigger seeds. Apply source-seed filters/hit hooks only at real
source-seed places. Record a backward source hit on its first AP output, before result filters
or store insertion can remove the result. Keep alternatives and method keys separate.

Implement forward conjunction completion with the effective input-layer join and zero-dropping
premise union. Backward uses the independent DEMAND branches built by reversal. Check cut and
boundary handling on conjunction outputs. E05 completes the interprocedural E6 adapter.

**Gate.** Missing literals never produce a result. Normal contributions can produce a normal
conjunction. Different contexts/calls never combine. Backward has no sink firing or conjunction
store input. A real reversed source can record a hit; record replay cannot synthesize one.

### E04. Implement summary publication, subscriptions and replay

**Requires:** E02–E03. **Proposal:** `analyzer-impl.md` §5.

**Tests first.** Write broken P1–P4 mocks first, then `SummaryStorageProtocolTest`,
`IndexCompletenessTest` and `AnyDeliveryTest`. Under reliable delivery and completed processing,
compare applicable publication/subscription pairs with a complete list scan. Cover P1–P6,
replay, delayed notification and duplicate delivery.

**Implement.** Add `SummaryStorage`, `PublicationIndex`, `SubscriptionManager`,
`CalleeSubscriptions` and dispatch for `LinkIn`/`Delivery`. Register before replay; insert before
notify. Read callee storage under its lock. Replay and delivery use the same full `matches`
operation on current subscriptions. Indexes return all candidates for inside/applicable
matching, including must/may facts and distinct exclusions at one path.

**Gate.** Replay-first, publication-first, delayed notify and duplicate delivery produce the same
applications. Exercise index fan-out above the old failure threshold. Add synchronized concurrent
read/publication schedules; a one-thread fuzzer does not check JVM snapshot visibility. Locks protect publication
state without violating runner ownership. P1–P6 each have an explicit test observation.

### E05. Implement calls, record resumption and E6

**Requires:** E03–E04. **Proposal:** `analyzer-impl.md` §4.5–§4.6, §4.10–§4.11.

**Tests first.** Write `CallPlanTest`, `RecordReplayTest`, `RecordDemotionTest` and
interprocedural `NdJoinAdapterTest`. Compare annotated link/return effects, zero paths,
complete E6 combinations and native record resumption with `FormsReference.run`, including
late demotion and record-crossing counts.

**Implement.** Run the cached call stage graph and suspend/resume at the callee stage. Register
subscriptions and replay before sending link deltas. Apply E6 only forward, at one caller method
key and call statement, keyed by callee, full callee premise set and publication layer. Different
caller premise sets can supply its members; union those sets and join the effective layers.
Support is checked later at the barrier. Treat REACH zero pass/entry/return paths
separately; only `{zero} → zero` enters the zero-rule path.

For a native same-direction must record, compute separate inside and applicable-only batches.
Count one record crossing if either initial batch is nonempty, before the rewriter can remove
results. Set pending candidates false while preserving their facts and E. Resume the inside batch
normally and the pending batch with the invocation-level pending Boolean carried through recursive
flow. Run returned Actions, filters, statement cuts, bind-back and sibling aliases. Immediately
before `Cut.CALL`, demote surviving pending results to DEMAND and Empty E. Ordinary summaries,
views, E6, zero and backward paths use the ordinary resumption. Add no per-fact pending field.

**Gate.** The production walk agrees with `FormsReference.run` for each batch and final exit.
Test identity aliases, E-sensitive actions, already-demand results, killed results and mixed
batches. Replay/delivery can complete each other's E6 slots. Missing a slot or mixing sites
produces no summary application. No intermediate return result enters successor closure.

### E06. Complete scheduling, failure and freeze behavior

**Requires:** E01–E05. **Proposal:** `analyzer-impl.md` §6, §7.7.

**Tests first.** Write `DeltaWorklistTest`, delayed-send tests, `RunFailureTest` and
`LifetimeTest`. Cover Q1–Q4 across partial Work steps, publication flush, cancellation and
join. Check unchanged-loop termination and repeat keys across quantum boundaries.

**Implement.** Finish the runner event loop, immutable queue-priority keys, local work quantum,
`InFlight` transitions and status-first cancellation. Drain unchanged work first; retain its
repeat set across Work events until the queue drains. Handle timeout, memory guard, runner
failure and join failures as specified. Checkpoint `Cancellation.Cancelled` stops under the
selected end status; other runner/caller-thread throwables give FAILED, and the explicit guard
gives OOM. Preserve the first end status, except that a failed join overrides even COMPLETE with
FAILED and exposes no run stores. Join runners before any driver barrier read.

Freeze completed analyzers by the proposal's lifetime table. Retain forward links through
confirmation, raw summaries through record persistence, and source hits through hand-off;
release run-only state after its last consumer.

**Gate.** Delayed handlers cannot cause early completion. Abnormal runs terminate and join without
publishing partial driver updates. The unchanged loop terminates across quantum boundaries.
No run-local object is reused by the next engine.

### E07. Compare the engine with a direct closure and varied schedules

**Requires:** E02–E06. **Proposal:** `analyzer-impl.md` §9.1–§9.3.

**Tests first.** Write `NaiveClosureTest` and fixed failing schedules before adding
randomized schedules. Use the Kotlin per-path closure with the current selector, cleaner,
views and late return order. Keep independent expected vectors for shared helper behavior.

**Implement test support.** Add the per-path `NaiveClosure` with complete candidate list scans.
Use stable `ReferenceAlgebra` forms and explicit call, action, boundary, trigger and seed hooks.
Resume native pending batches from RETURNED with `pendingDemotion=true`; admit only finalized
plan-exit results to caller closure. Keep sink/conjunction joins local to each method key.

Add the one-thread seeded `ScheduleFuzzerTest`. Control event selection, partial Work steps,
replay and notification. Cover separate units and direct same-unit calls. Save the seed,
program and processed event sequence for every mismatch and minimize it before changing code.

**Gate.** Compare full run observations under replay-first, notify-first, duplicate and split-E6
schedules. Shared helper agreement checks integration; independent specified vectors check
helper behavior. Run all engine test rows through their enabled scope.

### V3. Validate the complete one-run analyzer

Run all AP, forms, engine and protocol tests. Check both directions
and run-1/restricted modes. Complete the one-run portions of Analyzer validation items 1–6,
8, 12, 14–18, 21, 25–31, 35–36 and 41–45. Driver-dependent expectations follow in V4.
Review the current publication protocol and store subsumption assumptions; record any unresolved
integration concern. The engine can now supply a joined `RunResult` to the driver.

### D01. Implement joint support, confirmation and reports

**Requires:** V3. **Proposal:** `analyzer-impl.md` §7.5–§7.6; `ap-impl.md` §7.12.

**Tests first.** Write `SupportTest`, `AnyTaintSupportTest`, witness-key tests and
`ReportTest`. Cover same-call suppliers, allowed zero roots, unsupported cycles, link and
caller-edge layers, and tail/mark/E admission against the specified support rules.

**Implement.** Add `Support` and `ReportBuilder` using `confirmableMember` and `supplies`.
Both the link layer and caller-edge layer must be NORMAL. Preserve the zero/equal-fact supplier
case and the guarded same-concrete-mark, must/E containment case. Demand links supply no confirmation.
Require all members jointly at one call under a supported caller premise. Keep member
admissibility in supplier checks. Confirm a sink witness only from NORMAL and joint support.

Keep confirmed entries final. Replace unconfirmed demand entries from the latest complete
forward run. An incomplete forward run changes neither set; incomplete run 1 leaves an empty
report. Preserve witnesses without merging contexts or alternatives. Store `AnalysisEnd`.

**Gate.** Two separate calls cannot confirm an ND witness. Cycles without a root remain
unsupported. Tails, marks and E affect supplier admission. Mixed confirmed/demand reports and
all incomplete-run sequences match the specified state transitions.

### D02. Implement both hand-offs, seeds and frontier counts

**Requires:** D01. **Proposal:** `analyzer-impl.md` §7.2–§7.4, §7.8.

**Tests first.** Write `HandOffTest`, `NormalizedHandOffTest`, `FrontierPropertyTest`,
END and CONJ regression tests. Compare selected publications, normalization, must forgetting
and seed construction with the direct reference. Check demand-entry shapes and stored counts.

**Implement.** Add `HandOff`, `Seeds`, `SeedIndex` and `Frontier`. Consume the stored
`RunSummaryStore.demandEdges()` publications. E02 has selected raw DEMAND, multi-premise and
backward-zero summaries and preserved their layers. Do not select or reduce the pieces again
at the barrier. Every other normal singleton gives no callee demand. Persist eligible raw
records by the separate R1 operation. Normalize abstract mark exclusions only in patterns;
keep raw summaries unchanged. Forward must sides become may patterns with Empty E.

Seed backward from current DEMAND vulnerabilities only. Reversed end-fact triggers can seed
an already confirmed sink alternative. Build next-forward source seeds from actual backward
hits; cached source records produce no synthetic hit. Handle backward zero summaries and
implicit zero patterns by their separate rules. Count accepted stored patterns after exact
normalization/deduplication, excluding implicit zero from explicit demand counts.

**Gate.** Both hand-offs match direct construction, including excluded abstract marks, distinct
concrete marks, zero and ND branches. Reader-result DEMAND creates caller propagation only.
END retains the later finding; CONJ cannot create a one-literal forward record. Frontier logs
describe accepted builder contents, not attempted insertions.

### D03. Implement the iteration loop and complete-run barrier

**Requires:** D01–D02. **Proposal:** `analyzer-impl.md` §7.1, §7.7–§7.8.

**Tests first.** Write `DriverTest`, `DemandLinkTest`, end-reason tests and barrier
failure tests. Check sequential forward/backward runs, barrier order, status/report persistence
and stop conditions against the specified driver transitions.

**Implement.** Add `IterationDriver` and `IterationPolicy`. Start forward with limit at least 1;
alternate directions with nondecreasing limits. Use one analysis budget and fresh engine
objects per run. After a complete joined forward run: confirm/update report, persist raw R1
records, build demand/seeds, log the frontier, then check stop conditions. Run the barrier for
the final complete run too. Backward completion proceeds to forward without a forward stop test.

Keep the barrier memory guard and AP cancellation checks, including a check after the barrier
returns for a guard hit after its last internal checkpoint. The barrier has no separate deadline;
its elapsed time consumes the next run's remaining budget. Apply STOP_RULE, NO_DEMAND_EDGE and
policy in the specified order.
NO_DEMAND_EDGE checks accepted demand-layer edges, summaries and links. Incomplete runs add
and refute nothing. Map failure/timeout/guard results without restarting cancelled work.

**Gate.** Check 1/2/3 and 1/2/3/4/5 schedules, equal/nondecreasing limits, exhausted budget,
complete/incomplete barriers and all statuses. The DLINK root EXACT fixture continues when its
only demand object is a link. Optional diagnostic counters cannot change a stop decision.

### D04. Validate current iteration behavior

**Requires:** D03. **Proposal:** `analyzer-impl.md` §9–§10; `analyzer-core.md` §7, §11.

**Tests first.** Write `AnyTaintIterationTest` and annotated sequence fixtures for the
specified F72/F75/F76/F77 programs, using real seeds, support and cleaner lowering. Check all
facts, requests, links, raw/reduced summaries, records and demands after each run. Use current
selection rules; retain historical fixtures with their original version labels.

**Implement tests and refinements.** Add `AnyTaintIterationTest`, sharing, mode, frontier,
conjunction and hand-off regressions. Cover getter with two marks, callee sink, identity, setter,
factory, WRAP, END, CONJ, root EXACT cleaners, mark-changing pass and partial field cleaner.
Use limits 1/2/3 and the DLINK 1–5 sequence. Keep invalid-limit checks and synthetic multi-field AP
statements outside JVM sequence claims. Preserve documented alias/exception/filter gaps.

Generate small programs with concrete and abstract marks, cleaner bases, depths, calls, static
paths and conjunctions. Compare the current engine with its direct per-path closure, and perform
the validation inventory's labeled historical/concrete-design differential check. A discrepancy
needs a minimized annotated trace; historical output alone is not a semantic oracle.

**Gate.** All specified sequence fixtures pass and engine/reference differences are resolved.
Record the tested program scope and filter, shape, seed and record conditions. Do not claim
exact round-to-round narrowing in the abstract/representability exception cells.

### V4. Validate Phase 2

Run the full common AP/forms/engine/driver tests. Complete AP whole-run
items 13, 17–18 and all Analyzer inventory items except the phase-3 wiring/release/output
checks and the full project benchmark work reserved for phase 4. Compare records and raw
selection independently from current callee demand. Review all public engine/driver APIs and
every P/Q ownership rule. Phase 3 can now run the new driver through a JVM entry.

## Phase 3: Prescan and JVM analyzer entry

### J01. Copy prescan values and release prescan state

**Requires:** V4. **Proposal:** `analyzer-impl.md` §8.1; `analyzer-core.md` §9.

**Tests first.** Write prescan-copy, release and `LifetimeTest` cases before changing
existing managers. Check the prescan value contract, ownership transfer, failed joins and JVM
reachability after release.

**Implement.** Add the proposed `prescanRuleIds`, `prescanLambdas` and `releasePrescan` APIs
to their existing owners. Verify that all prescan jobs have stopped and joined before copying/releasing state and starting
the new engine. The current prescan join uses `withTimeoutOrNull`; inspect and handle its failed
join result rather than assuming cleared maps mean joined jobs. If the jobs did not stop, end
with the specified failure path and expose no live prescan state to the new engine.

Copy selected rule IDs, context-sensitive lambda targets, root method entry points, fact type
checker and external-method tracker. Obtain lambda values from the existing context resolution
trackers without retaining trackers, subscribers or contexts.
Release contexts, unit/runners, dependency maps and the old active AP-manager reference after
the copy. A new analysis Cancellation is independent of the prescan cancellation state.

**Gate.** Copied data still resolves lambda calls after release. Weak-reference checks show
prescan AP manager, runner and context release, with bounded GC polling as in the inventory.
Retained checker/tracker values do not retain released run, edge, summary or alias state.
Keep the intentionally shared application graph and classpath available.
The old full-scan path, which does not call release, retains its behavior.

### J02. Wire the phase-3 JVM entry and report conversion

**Requires:** J01. **Proposal:** `analyzer-impl.md` §8.1.

**Tests first.** Write `JIRBidiAnalysisTest` and output/status tests before the entry hook.
Check each specified report-to-output mapping, status mapping and witness choice.

**Implement.** Add `BidiEntry` in DF and `JIRBidiAnalysis` plus `JIRMethodContextCache` in JVM.
Use copied prescan values to build the reduced rule provider, AP manager, interpreter, method
cache, record/witness stores and driver. Add the optional entry hook to `TaintAnalyzer` and
`JIRTaintAnalyzer`. Expose the currently private analysis graph through a narrow accessor or
pass it to the entry; the proposal snippet needs this integration change to compile.

Return every report entry, both CONFIRMED and DEMAND, with a simple trace. Choose a confirmed
witness's method key when available, else the first witness. Keep the existing CWE filter.
Map COMPLETE/TIMEOUT/OOM/FAILED to OK/TIMEOUT/OOM/EXCEPTION. A setup failure returns empty
output and EXCEPTION. Log entry counts per report state and set trace-resolution status to OK.
Use the remaining shared analysis budget after prescan. The null entry
continues through the current full-scan path.

**Gate.** Each state/status mapping and witness choice has an exact test. DEMAND entries remain
in output. Setup failure and incomplete run 1 produce no entries. Both entry modes compile and
run, and phase-3 output does not depend on full trace resolution.

### J03. Run JVM analysis acceptance checks through the new entry

**Requires:** J02. **Proposal:** `analyzer-impl.md` §9; `ap-impl.md` §33.

**Tests first.** Use the D04 program fixtures with JVM builders and prescan data.
Keep independent form vectors for lowering and mark-policy cases; a shared builder/oracle alone
cannot validate their semantics. Pin finding identity and report state.

**Implement tests and fixes.** Adapt the existing analysis test fixture to select the bidi entry
and retain the old-entry control. An unchanged harness invokes the old analyzer. Run
cleaner sensitivity/DSL/deep-summary, primitive AnyField, ND, lambda/collection reachability,
multiple-return, Spring dispatcher and entry-mark-removal cases. Name
`JavaDataFlowReachabilityTest`, `KotlinDataFlowReachabilityTest` and `MultiReturnDataFlowTest`
explicitly when filtering; `*AnalysisTest` does not select them. Run query-language ND
`ExampleTest` in its separate module. Run the current getter,
setter, factory, pass, field-cleaner and record-return programs through this same path.
Check complete per-run observations as well as the final output.

Keep the existing old-engine suites intact for the prescan/full-scan path. Separate assertions
that require full phase-5 traces from phase-3 finding/state assertions; list that scope in the
results. Retain the specified disabled exception cases and known alias false positives.
Fix regressions found by the enabled gate before closing the phase.

**Gate.** Fresh test results show which suites exercised the new entry. Full trace assertions
are explicitly assigned to phase 5. The new path passes every enabled phase-3 acceptance case;
shared changes introduce no new baseline regression in the old path.

## FINAL. Review, validate and close the implementation

Run one final review after all enabled gates pass:

1. Audit every proposal package, public API, handler, AP operation, store and call stage against
   its implementation and test owner. Confirm that the validation inventory below has no gap.
2. Recheck the current behavior rules: F74 lowered actions, F75 same-base split/request, F76
   transient views, structural demand selection, separate R1 persistence, normal forward
   conjunctions, independent backward branches and late native demotion. Remove obsolete
   crossability, callee-demand-from-view, liveness and depth-delay code from the new path.
3. Recheck replay/delivery completeness, same-call support, current-layer alias admission,
   field cuts, source hooks, immutable inputs, joined barriers, report status and object lifetimes.
4. Run fresh affected-module test suites and the existing JVM analysis acceptance suites with
   explicit composite-build tasks. Run shared-consumer checks for changed common types.
   Run `git diff --check` on the final content. If Lean artifacts changed during clarification,
   run the Lean build and audit under the clarification rule.
5. Check every local document link, numbered section reference and code fence. Read the three
   normative specs and two proposals together for contradictions and missing definitions.
   Update references and validation results to match the final implementation. Update proof
   status only for results changed by clarification work.
6. Record the implemented scope, exact commands/results, clarified behavior, known specified
   gaps and work reserved for phases 4–5. All enabled implementation and test requirements must
   be complete. Test results do not establish general theorems.

Commit each reviewed implementation/validation slice with its tests when commits are requested.
Include clarification evidence when applicable. The deliverable for this task is the complete
phase-1–3 implementation, its tests, and the gate evidence in this plan. Full project benchmarks
remain in phase 4.

## Validation inventory ownership

Item numbers below belong to the named part of `validation-plan.md`. Every item is assigned;
its full text still applies. Earlier steps establish local tests; later gates establish integration.

| Inventory | Items | First implementation/test owner | Final gate |
|---|---|---|---|
| AP | 1–4 | A02, A05–A06 | V1 |
| AP | 5–6 | A06; lowered actions I04 | V2, J03 |
| AP | 7–8 | A04, A06, A09, E03, E05 | V3, D04 |
| AP | 9–10 | A05, A08, E03–E05 | V3 |
| AP | 11–12 | A07–A08 | V1, V3 |
| AP | 13 | D02–D04 | V4 |
| AP | 14–15 | A05, A08–A09, D01–D02 | V1, V4, J02 |
| AP | 16–18 | D01, D04, J03 | FINAL; project/trace parity continues in phases 4–5 |
| Analyzer | 1–6 | E01, E04–E07 | V3 |
| Analyzer | 7–10 | E02–E03, D02–D04 | V4 |
| Analyzer | 11 | J03 | FINAL for phase-3 assertions; phases 4–5 for full parity |
| Analyzer | 12–15 | E06, A09, D01 | V4 |
| Analyzer | 16–18 | I05–I06, E03–E05 | V3, J03 |
| Analyzer | 19–20 | J01–J02 | J03 |
| Analyzer | 21–27 | A05–A09, I04, I06, E05, D01 | V4, J03 |
| Analyzer | 28–31 | A05–A06, I03–I04, D04 | V4; JVM lowering J03 |
| Analyzer | 32–40 | D02–D04 | V4 |
| Analyzer | 41–45 | A07, E02–E03, D02–D04 | V4 |
| Analyzer | 46–47 | D04 | V4 |
| Interpreter new | 1–4 | I02–I05 | V2 |
| Interpreter new | 5 | Go-only cleaner mapping | Outside JVM feature scope |
| Interpreter new | 6–12 | I01–I04, I06, E03 | V2, V3 |
| Interpreter new | 13–17 | I04–I06, E03, D02 | V4, J03 |
| Interpreter new | 18–23 | I04–I06, E02, E05 | V3, J03 |
| Interpreter new | 24–28 | I03–I06, E03, D02 | V3, J03 |
| Interpreter new | 29–35 | A05–A07, I03–I06, D04 | V4, J03 |
| Interpreter existing | All JVM rows in §7.1 | I03–I06, J03 | FINAL |
| Interpreter existing/new | Go-only rows or clauses within shared items | Shared-consumer compile/regression checks only | FINAL for changed common code |

The short proposal test tables remain useful implementation checklists: `ap-impl.md` §8 has
24 rows, §33.1 has 15 rows plus 4a, and `analyzer-impl.md` §9.1 has 34 rows. Match each row
to the corresponding step above. The full validation inventory takes precedence over a short table.

## Optimization checks

Separate semantics from cost. Run these checks when the corresponding optimization is added,
after tests show that its results match the direct reference:

| Optimization | Concept/equality check | Cost check |
|---|---|---|
| Demand prefix index | Full demand scan vs prefix candidates followed by full reduction (A08). | Count full-reduction calls; measure lookup and build costs separately. The existing `CurrentDemand` finite witness is optional comparison data, excluding construction. |
| One demand selector per group | Per-leaf selector vs homogeneous premise/layer group (A07–A08). | Count selector work separately from later packing/consumer work. The existing `CurrentSummarySelection.countedPacked_work` witness is optional comparison data. |
| Exact depth shortcut | Explicit counted path maximum vs cached depth guard (A06). | Count visited nodes for all-fit and cut-needed trees, including metadata construction. |
| Same-base cleaner batching | Per-leaf F75 updates/requests vs tree batch (A06). | Count base guards separately from residual leaf visits and action cuts. |
| Trie sharing and deltas | Decoded union/delta vs list concept (A03–A04). | Count visits, allocations and retained nodes on chains, forks and shared DAGs; use cold and cleared caches. |
| Standing joins/indexes | Complete finite scan vs every admitted combination (A09, E04–E05). | Count candidate tests and emissions for both arrival orders and duplicate delivery; preserve output cardinality. |
| Literal/combination simplifications | Matched TAINT literal and full concrete combination equal the annotated reference (A05–A06). | Count avoided coverage traversals and result-flag work separately from matching and output work. |

For a cost claim, record operation counts or JVM measurements and the tested scope.
Measure JVM effects on representative chain, fork, exclusion and multi-mark fixtures.
Do not claim whole-project performance from a local count. Full project benchmarks belong to phase 4.

## Commands and evidence

Run Lean commands from `spec/lean` only when clarification work changes Lean artifacts:

```bash
lake build
lake env lean audit.lean
```

Run Gradle commands from `core`. It is a composite build. Name included-build tasks explicitly;
the dataflow build has no separate wrapper. Commands below are for the implementation gates,
after the named tests exist. Confirm task/test discovery before using their result as evidence.

Focused examples:

```bash
./gradlew :opentaint-dataflow-core:opentaint-dataflow:test --tests '*CleanerTest'
./gradlew :opentaint-dataflow-core:opentaint-jvm-dataflow:test --tests '*JIRCallPlanTest'
./gradlew :opentaint-dataflow-core:opentaint-dataflow:test --tests '*ScheduleFuzzerTest'
```

At V1–V4 and FINAL, clean the selected test results before running the required suites:

```bash
./gradlew --continue \
  :opentaint-dataflow-core:opentaint-dataflow:cleanTest \
  :opentaint-dataflow-core:opentaint-jvm-dataflow:cleanTest \
  :opentaint-dataflow-core:opentaint-dataflow:test \
  :opentaint-dataflow-core:opentaint-jvm-dataflow:test
```

For phase-3 JVM integration, also run the SAST included build and application tests:

```bash
./gradlew --continue \
  :opentaint-jvm-sast-dataflow:cleanTest :opentaint-jvm-sast-dataflow:test \
  cleanTest test
./gradlew :opentaint-java-querylang:cleanTest :opentaint-java-querylang:test \
  --tests 'org.opentaint.semgrep.ExampleTest'
```

When common base/types change, check the Go consumer and run its existing regressions as required:

```bash
./gradlew :opentaint-dataflow-core:opentaint-go-dataflow:compileKotlin
./gradlew :opentaint-dataflow-core:opentaint-go-dataflow:cleanTest \
  :opentaint-dataflow-core:opentaint-go-dataflow:test
```

Record the task paths, exercised test classes, fresh XML/results directories and failed messages
per gate. Record the question, assumptions and result for any Lean clarification separately.
New test system properties must be forwarded to workers and declared as test-task inputs through
the existing build configuration. A cached result with a different property is not evidence for
the new setting. Do not use a recursive stale-results count.
The current composite build can execute included tasks even with `--dry-run`; use task discovery
for command inspection. Root JVM test filters still initialize the build's Go test environment
and sample jars. Record environment/setup failures separately from feature failures.
Run only the modules and checks affected by a step; the phase/final gates provide the wider check.
