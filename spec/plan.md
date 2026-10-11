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
| A08 | Complete | [A08 evidence](../docs/reports/bidi-a08.md). All 581 common tests pass; none skipped. Index/full-scan equality, exact links, deltas, raw/publication separation, immutable snapshots, positive limits, deep paths and cancellation pass. All 433 new annotated tests are discovered. JVM/Go test sources, SAST and application compile. |
| A09 | Complete | [A09 evidence](../docs/reports/bidi-a09.md). All 635 common tests pass; none skipped. Standing products, native records, concurrent lossless witnesses, immutable stored shapes, deep paths and cancellation pass. All 487 new annotated tests are discovered. JVM/Go test sources, SAST and application compile. |
| V1 | Complete | [Phase 1 audit and gate](../docs/reports/bidi-v1.md). All public AP/store operations have contract coverage. Clean common 647, JVM 119 and Go 1,313 tests pass; none skipped. All 499 new common and five new JVM annotated tests are discovered. SAST and application compile. |
| I01 | Complete | [Common forms and mode gate](../docs/reports/bidi-i01.md). Reversal, cached directions, all three application modes and the per-path call walk pass 60 new tests. Clean common 707 tests pass; all 559 new common tests are discovered. JVM/Go test, SAST and application compilation pass. |
| I02 | Complete | [Shared builder gate](../docs/reports/bidi-i02.md). Exact forms and independent per-path/tree vectors pass all 41 new tests, including field-clean actions. Clean common 748 tests pass; all 600 new common tests are discovered. JVM/Go test, SAST and application compilation pass. |
| I03 | Complete | [JVM statement gate](../docs/reports/bidi-i03.md). All 52 new construction, effect and filter tests pass. Clean common 748 and JVM 171 tests pass; all 600 common and 57 JVM new tests are discovered. Go test, SAST and application compilation pass. |
| I04 | Complete | Whole-rule validation, rule forms, lowered field actions and temporary lifetime guards. Clean common/JVM and shared-consumer compile gate passed; see `docs/reports/bidi-i04.md`. |
| I05 | Complete | [Call-plan and adapter gate](../docs/reports/bidi-i05.md). Clean common 755 and JVM 286 tests pass; all 607 common and 172 JVM new tests are discovered. Go test sources, SAST and application compile. |
| I06 | Complete | Method boundaries, complete JVM facade/cache, wired graphs and Spring forms. Clean common/JVM and application regression gate passed; see `docs/reports/bidi-i06.md`. |
| V2 | Complete | Actual static exit-source requests, matched source/pass effects and contextual construction scan pass. See `docs/reports/bidi-v2.md`. |
| E01 | Complete | Typed run shell, count protocol, immutable queue priorities and context adapters. Clean gate passed; see `docs/reports/bidi-e01.md`. |
| E02 | Complete | Method-local transfer, unchanged bursts, boundaries, raw/selected summaries and publication lifetime. Clean gate passed; see [E02 evidence](../docs/reports/bidi-e02.md). |
| E03 | Complete | Standing requests, sinks, conjunctions and backward end-fact triggers. Clean common and shared-consumer compile gate passed; see [E03 evidence](../docs/reports/bidi-e03.md). |
| E04 | Complete | Locked summary snapshots, complete replay/delivery indexes, native/view reads and actual method dispatch. Clean gate passed; see [E04 evidence](../docs/reports/bidi-e04.md). |
| E05 | Complete | Cached call walk, native/view returns, late demotion, zero/source rules and actual E6. Clean gate passed; see [E05 evidence](../docs/reports/bidi-e05.md). |
| E06 | Complete | Shared deadline, scheduling and failure-safe joined release; 50 focused and 1,054 clean common tests. See `docs/reports/bidi-e06.md`. |
| E07 | Complete | Direct finite closure, 408 seeded executions and fixed protocol controls; 1,083 clean common tests. See [E07 evidence](../docs/reports/bidi-e07.md). |
| V3 | Complete | One-run acceptance and protocol/store audit; 1,088 common and 339 JVM tests. See [V3 evidence](../docs/reports/bidi-v3.md). |
| D01 | Complete | Joint support, confirmation and reports. See `docs/reports/bidi-d01.md`: 40 new tests and 1,128 common regressions pass. |
| D02 | Complete | Both hand-offs, seeds and frontier counts. See `docs/reports/bidi-d02.md`: 29 new tests and 1,157 common regressions pass. |
| D03 | Complete | Iteration loop, joined barriers, one budget and DLINK 1–5. See `docs/reports/bidi-d03.md`: 23 new and 1,180 common tests pass. |
| D04, V4 | Pending | |
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
A08 demand snapshots own their index and stored path lists. A later builder
insertion or caller path-list change must not change a built store. Keep
single-writer indexes iterative at the tested depths. An optional cancellation
argument lets run stores check long index walks without changing the proposal's
no-argument `PathTrie` constructor. Method-edge stores require a positive limit.
The A08 review found that the publication snippet checks K6 but omits the
multi-premise TAINT guard from raw summary insertion. Apply that guard to both
collections, as required by `ap.md` §4.6 and §8.5.
`DemandStore.Builder` must also reject concrete STAR entry tails. The snippet
only rejects abstract STAR, but `ap.md` §6.1 and §8.6 require every stored
entry to have a non-STAR tail. Keep generic STAR emission/reduction cells in
the direct API; enforce the stronger guard at the typed demand store.
Validate the stored pattern's own field exclusion too: `ap.md` §3.4 permits
nonempty E only on STAR or forward must tails. Stored demands have no must
tails, so only a STAR exit can carry nonempty E. Raw generic patterns remain
separate from this validated boundary.
Demand query payloads must not expose a mutable stored path list. Keep owned
path lists unmodifiable, so a cast by a reader cannot change the snapshot.
The A08 review extends the initial-store matrix with abstract STAR/E and a
second nonzero base. It also adds a direct check for the request store's
optional cancellation argument, before the final gate.
The first combined A08 compile check found that the repository's
`fastutil-core` dependency omits `Reference2ObjectOpenHashMap`. Use the JDK
`IdentityHashMap` for canonical premise-key maps. Keep their identity contract
and the existing dependency set.
The A08 sequence review found that buffered path values and added leaf lists
can emit another item after cancellation during iteration. Add regressions
that consume one item, cancel, then request the next. Check cancellation on
each emitted value or link, as well as during the index walk.
The A08 XML audit found three annotated tests that JUnit did not discover:
their Kotlin expression bodies infer a non-Unit return type. Declare `Unit`
on expression-body test methods. Audit all new bidirectional annotated tests
against compiled method return types and XML names, then repeat the gate.
A09 requires arity at least two for k-ary joins, as stated in the join
contract. Use an iterative product and optional cancellation for large
literal sets; do not depend on the JVM call stack. Validate the native record
kind, normal layer and direction guards on direct insertion as well as
persistence. `ap.md` §8.7 requires these guards even though the record snippet
omits them. Read views remain transient. Keep witness input collections owned
so a later caller list change cannot alter stored support shapes or end facts.
Each conjunction rule/statement has one fixed arity. Check a literal index
before creating its join, and reject a different arity on a later call for
the same key. Invalid calls must not leave a stale join shape.
The A09 concurrent witness test must create its statement and vulnerability
key before starting worker threads. Its simple statement fixture uses a
single-writer map; concurrent fixture creation can produce different keys
and does not test atomic merging of one vulnerability.
The A09 witness review found that public copied lists can still be cast and
changed. Such a change makes a stored witness differ from its private shape
key, or changes its end-fact seed path. Test mutation through returned lists
and nested paths. Use owned unmodifiable lists for these published values.
The A09 record scan exposed a generic reversed read with an ANY result.
The proposal always assigns NORMAL in this fallback, which violates W6.
Test both native directions and apply the reversed may view in DEMAND;
retain NORMAL for generic STAR/E views. Align the reference snippet with
this layer correction. The F76 must-side conversions remain unchanged.
The A09 ND join review found that returned subscriber tuples share mutable
lists with retained full combinations. A caller can insert a subscriber
that was never accepted. Test this alias before the fix, then retain owned
unmodifiable tuple lists for later conclusion delivery.
The added reverse-view test must compare the `EdgeOutcome.Fact` value;
`EdgeOutcome` has no common result property. Correct this test typing before
the behavioral RED run.
The V1 store audit found a missing checkpoint between two REACH layers
returned by `MethodEdgeStore.edgesAt`. Test cancellation after NORMAL is
consumed and before DEMAND is returned. Check each REACH yield, as required
by the A08 per-value iteration contract.
The V1 tree review found that `leaves` and `leavesNear` return buffered
sequences with no checkpoint between emitted patterns. Add cancellation
regressions after sequence creation and after its first value; check each
emitted leaf. Complete the direct cancellation checks for satisfaction,
summary and record application, combinations and subtraction before V1.
Complete V1 collector coverage with equal-content values that have different
normal FLOW exclusions, FLOW mark exclusions or normal must exclusions.
`Results` keeps each complete group key separate; it must not apply store
T2 intersection to one operation's output.
The same V1 emission audit found no checkpoint between REACH callbacks in
`Results.flush`. Test cancellation after the first callback and before the
second. Apply the existing per-result check to REACH as well as trie facts.
I01 adds the common sink, exit-rule and clean-step declarations from
§23.4 because the reversal and action-chain interfaces use them. I04
retains JVM rule construction and field-cleaner lowering. Cached form
collections are owned and unmodifiable, so caller list changes cannot
change the forms used by later runs or their standing-join keys.
`SinkRule` constructors enforce the normative positive-literal contract:
at least one pattern, each with a concrete mark and EXACT or ANY tail.
The proposal's must-only rejection is weaker than interpreter I11(f) and
the AP sink shape. Keep unconditional zero represented by `ZERO_PATTERN`.
Own form collection containers and sink-pattern paths. Preserve existing
micro-edge, forward `PathEdge`, conjunction and sink object identities;
path-producing helpers own their paths rather than cloning retained forms.
Use regular value classes for `CallStage.Actions` and `Callees` when an
owned list cannot replace a data-class primary constructor value. Keep
the proposed public constructor names, getters and structural equality;
copy collection containers at construction.
Apply a summary's result filters to conjunction results as well as plain
micro-edge results. The proposal omits this in both touched and untouched
literal-input paths. Interpreter §2.1 step 5 and the STAGE result-filter
contract require it. An untouched input itself still passes unchanged.
The I01 test compile found inferred narrow map keys in the collection
ownership test. Use explicit `AccessPathBase` keys; the public filter maps
use that type. Complete source-hook coverage for an admitted source with
no output and for a source edge at a place where source hooks are off.
The I01 graph tests omitted ZERO from reversed final-stage targets.
`ZERO_KEEP` has ZERO as a target, so A5 adds its pass-over identity and
includes ZERO in reversed `touched` when it was untouched forward.
Correct the two expected sets. The separate zero pass-over-and-entry
rule still applies in either direction, even when ZERO is touched.
Complete I01 call-guard coverage with an executed forward END_FACTS
stage. A fired sink supplies its own layer and the zero premise; it
does not reuse the incoming premise or call source hooks. Check sink
object identity, sibling input independence and disabled guards.
Keep the I01 directed transfer matrix within W8: a backward input has
no normal must column, and a backward micro edge has no must target.
I02 retains the I01 producer ownership contract for injected `PathEdge`
and conjunction objects. The builder preserves those identities; its
move/read/write helpers own all paths that they create. Go tuple-target
reads remain with the old Go analyzer; the current new interpreter plan
is JVM-scoped and does not add a target-path read overload.
The proposed `JIRInterpreter` constructor depends on rule forms (I04),
call forms (I05) and method entries (I06), which in turn use the facade.
Keep I03 compilable by giving `JIRStatementForms` its actual inputs:
the AP manager, type filters, alias-path provider and read-source hook.
I04 supplies the real read-source hook. I06 adds and wires the complete
`JIRInterpreter` facade after all those forms exist. Keep the proposed
public class names and the common Interpreter behavior; add no runtime
placeholder for later steps.
The I02 field-action review found a test that used object identity for
`TaintMark`, an inline value class. Compare mark values by equality;
retain identity checks for the cleaner, filter and temporary objects.
The staged I03 alias provider is called only for a local write base.
This preserves the existing `forEachAliasPathAtStatement` guard and
interpreter A1/A4: reads and static or nonlocal writes have no aliases.
The I03 test compile found a generic assertion with a `JIRType` value
and a `JIRClassType` value. State their shared `JIRType?` assertion type.
Keep the checker's existing helper return types and filter behavior.
The full I03 test compile also requires a shared pair type for alias
query assertions. Use `Pair<JIRInst, AccessPathBase>` in expected lists;
the actual provider may receive a typed local assignment.
The I03 focused run found two fixture assumptions. JIR prints a nested
type with a dot, although its class name uses `$`. Check each API's own
name. JIR can lower an ignored non-void call as an assignment. Build a
direct `JIRCallInst` from that real call to check both admitted call forms.
The same run found that the reused `unboxIfNeeded` helper recognizes
`java.lang.Char`, but not Java's `java.lang.Character`. Interpreter §5.1
requires primitive or boxed types. Add explicit Character recognition
to the new mark policy, including element levels. Keep the shared helper
and old prescan behavior unchanged. Add Character-array coverage.
The I04 proposal review found that its validation visits only supported
action kinds. Interpreter §1.3 requires every action position to be
checked before static evaluation. Add a general action-position pass
before kind-specific restrictions. A malformed position rejects the whole
rule, even on an action ignored by that rule kind. Keep otherwise ignored
action kinds unchanged. Test valid sibling actions and negated literals.
Complete the deferred temporary lifetime guards in I04. Method-edge,
raw/publication summary, added-link and source-hit stores reject temporary
bases before mutation. Native records and demand hand-offs also reject
temporary conclusions or raw patterns. Requests retain the original
incoming premise; the field action projects its temporary before storage.
Use the existing `Severity.Error` enum value in the I04 test rule
fixture. Keep the shared fixture's `forms` factory as the entry point
for tests; use the accessor-index extension from its companion import.
Use a block body for I04 `cubes`: its true-literal shortcut returns from
the function, which Kotlin does not permit in an expression body.
The I04 effect review found that a shared FLOW premise was also used for
concrete TAINT inputs. Make each vector's default premise follow K6:
an abstract STAR initial for FLOW, and a concrete exact initial for TAINT.
Reject mixed input kinds in the helper and assert the premise relation.
Keep explicitly supplied F75 premise objects, expected facts and requests.
The I04 storage review found that the initial factories still permit a
temporary base. Reject it in `InitialAp` and before `ApManager.initial`
interning. This closes temporary premise endpoints in initial, request,
method, summary, link and native-record stores. Add a failing regression
for both factory forms and direct internal construction. Keep transient
`Facts` creation available for the field action.
The I04 final coverage review adds the reachable run-1 static kill cell:
an identity static FLOW premise at the root, a `RemoveAllMarks(C.s)`
action, its root exclusion and class-position request. Also check that
an excluded class raises no request. Keep the bare-static must vector
labelled as a direct AP cell outside JVM emission.
I05 uses the same dependency staging as I03. Give `JIRCallPlanBuilder`
its actual manager, rule forms, checker, type filters, caller, statement,
call, resolution function, entry-point resolver, frozen lambda map and
optional alias analysis/default getter. A persisted-alias provider defaults
to the real analysis helper. I06 wires these services from the complete
facade and method entry. Keep real condition rewriting, public class names
and the specified stages; add no runtime placeholder.
I05 adds the common unresolved-call observer contract and pass-read
metadata. I06 adds the JVM tracker hook with the facade. E05 calls it
for each new run-1 TAINT added-fact delta; it never selects transfers.
The I05 resolution review found that the proposal filters empty concrete
methods but not frozen lambda implementations. Apply interpreter I8 and
§3.6 to every expanded target. Drop empty implementations before entry-key
creation. If all expanded targets are empty, use the unresolved path.
A mixed empty/nonempty target set keeps the nonempty targets without an
extra failure; an independent resolver failure remains. Test these cases
before implementation. Adapt `MethodWithContext.method` to `JIRMethod`
before checking its instruction list.
The first I05 test compile found an invariant condition type in a fixture:
state `CommonCondition<JirCondition>` when building a mark atom. Keep the
same condition and expected forms.
The I05 effect review adds a reachable run-1 unresolved static pass:
the static root identity binds through the call, a field-to-Result pass
raises its position request on the original caller premise, and the
blocked class gives no request. Check this through both plan walks.
The complete I05 test compile requires explicit map/set types for empty
lambda rows. Build false and mark conditions with the existing condition
factories; a mark atom is not itself a `Condition`, and `False` is not an
enum member. Keep the same test inputs and expected results.
The first executable I05 run found fixture assumptions. `Cleaner` has
object identity, so compare its base, path, reach and mark fields
when separate lowering calls make separate cleaners; keep identity checks
for retained native cleaners. JIR can lower an ignored nonvoid call as an
assignment. Use a direct `JIRCallInst` from that expression to test no lhs.
An Object argument has both root and array sink alternatives. Its reversed
end facts must fire both owning alternatives and seed both paths.
Investigate actual alias snapshots before changing alias expectations:
the legacy analysis is reused and its heap/liveness inputs remain in force.
The alias fixture starts at a virtual entry. Wrap its raw application graph
in `JApplicationSingleExitGraph`, as production does, so that entry has a
successor. The raw graph leaves the appended entry disconnected and gives
empty alias state. Keep the declared alias cases and full-path assertions.
The remaining connected alias fixture failure needs instruction-level
diagnosis. Its helper selects the first field-read temporary, which can
differ from the call result local that the test intends to overwrite.
Compare the actual call lhs, operands and their alias snapshots before
changing this fixture. Keep nonvacuous before-only and after-only checks
with the exact intersection. Do not change the reused alias analysis.
The diagnostic confirms JIR splits `a = external(a, value)` into a fresh
call result `%2` and a later `a = %2` copy. At the call, the old `a` still
has its field alias. Keep that positive timing check. Use a held field
value with its owner passed to the call for a before-only heap alias;
the existing analysis invalidates that heap path. For the modeled
after-only alias, query the actual fresh call lhs. Keep both negative
checks nonvacuous and retain the same helper intersection contract.

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

Before closing a gate, check that each new annotated test has a JVM void
return type and appears in the test XML. Kotlin expression-body tests must
declare `Unit` when their last assertion returns a value.

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

**Implement.** Add `JIRStatementForms` and `JIRTypeFilters`. Add the complete `JIRInterpreter`
facade in I06, after its rule, call and method-entry dependencies. Reuse JIR values,
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

**Implementation staging and corrections.** I03 and I05 expose real builder
inputs. The facade now wires the statement alias provider, static-read
source hook, call resolver, entry resolver, per-method aliases and frozen
lambda targets. The `JIRMethodContextCache` adapter needs
`MethodContextSource`, which E01 declares. Add that adapter in E01; all
I06 forms and graphs remain usable without a placeholder interface.

Copy the prescan lambda values at all three collection levels when the
method entries are constructed. Use owned unmodifiable outer maps,
primitive maps and sets. J01 later supplies the production extraction
and subscription release. The proposal's last-instruction check does
not establish both exits and the entry. Check the final boundary triple
(`JMethodEnterInst`, normal exit, exceptional exit), as required by the
single-exit graph and I11. Add a separate test classpath with the linked
lambda features and boundary feature last; keep the existing test kit.

Own the generated method's cleaner list. The cleanup primitive has a
null path and mark, so it does not depend on an AP manager. Preserve
the existing fact-based Spring rule provider. Test the actual private
dispatcher generator through reflection with nonannotated samples;
this does not add automatic Spring entry points to unrelated tests.
Cache synchronization follows the method runner ownership rule:
one method's contexts use the same runner. The caches keep strong form
references for standing join identity.

The first Spring test compile exposed fixture API errors beside the
expected missing I06 APIs. Construct `MethodKey` from context and entry
statement, use the existing string `typeName()` helper, and build old
AP facts with `createFinalAp(...).prependAccessor(...)`. Check the old
provider's registry and outside cases separately; `FinalFactAp` has no
public merge method.

The Spring call vectors use legal premises: a forward FLOW input uses
the static STAR initial; forward TAINT inputs use zero. Backward nonzero
requirements retain their own initial pattern. These are form-level
pre-cut vectors; they do not claim a completed driver sequence.

Use JVM binary names (`$AComponent`, `$ZComponent`) for the Spring
sample's nested class lookup, as in the existing resolver fixtures.

The first integrated JVM compile found the facade's accessor-index
import in the wrong package. Reuse `ap.ifds.access.util.AccessorIdx`,
as the existing builders do; no new index type is needed.

The integrated test compile also found two `JIRInstList.isEmpty()`
calls. Use its public `size` property; the list is not a Kotlin collection.

The lambda snapshot review found that fastutil's unmodifiable hash-map
wrapper exposes entries whose `setValue` mutates the underlying map.
Add a regression through both primitive and generic entry views before
the fix. Keep hash lookup and expose detached immutable `BasicEntry`
views through an owned primitive-map adapter inside the unmodifiable
wrapper, after local API verification. This avoids a linear call-site lookup.
The outer map and target sets remain owned and unmodifiable.

The first executed construction scan found two fixture assumptions.
The IR classpath appends a lookup feature after instruction features;
check that the boundary feature is the last instruction feature.
A field action's write also keeps its temporary, so select the write
edge by both the temporary source and the original target base.
Keep the existing full-path and strong-keep assertions.

The rich construction scan's first target resolves, so it does not
exercise its pass rules. Also scan a real unresolved external call
with rich rules. Require its nonidentity pass edges to include a may
target and ordinary STAR or exact premise in both cached directions.

The real call-alias fixture compared a caller input base with an edge
source in callee coordinates. Use callee `arg(0)` on the source side
and retain the actual caller `arg(0).f` target check. Add diagnostic
paths to the statement-alias assertion before changing its expectation.

The Spring pre-cut transfer test drops its saved deep fact. Add a
temporary stage diagnostic for the real save/cleanup/restore forms
and the declared component field types before changing a path or filter.

That first diagnostic did not distinguish path matching from filtering.
The actual field path uses declaration coordinates: the enclosing
class name and the raw field type name, as `mkFieldAccess` does.
Typed field values can have dotted nested type names, while the raw
declaration uses binary `$` names. Build the Spring expected static path
from `ref.field.field`, and its suffix owner from the actual component
declaration. Keep the filters and full restored-fact assertion.
The diagnostic log preserves the earlier evidence.

A read-only alias probe confirms the same raw-type mismatch in the
statement-alias vector: `box` has raw type `$Box`, not `.Box`.
Correct that expected accessor string; the emitted full heap path
and real alias provider are correct.

The detached primitive-entry iterator must explicitly implement
`remove()` in this fastutil API. Make it throw
`UnsupportedOperationException`; an immutable snapshot has no remove.

The remaining concat scan fails because the JVM samples compile as
Java 8, which emits StringBuilder calls instead of StringConcatFactory.
Keep that existing sample setting. Add a separate Java 17 sample source
set to the sample jar and use its concat method to test the installed
transformer. Require real String.concat calls and cached form checks.
This tests the transformer input required by the proposal.

**Tests first.** Write `JIRBoundaryFormsTest`, `JIRFormsContractTest`, method-cache tests
and `SpringDispatcherFormsTest`. Check entry/end-rule order, normal/exceptional boundary
wiring, seed placement and zero-premise static/entry-mark removal independently.

**Implement.** Add the complete `JIRInterpreter` facade, `JIRBoundaryForms`, `JIRExitWiredGraph`, `JIRMethodEntry`, `JIRMethodForms`,
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

The read-only inventory review identified three form-level checks to
complete in this gate. Apply an actual conditional static exit-source
form to a run-1 static STAR identity and compare raw/tree facts and
the original-premise position request; add restricted/backward controls.
Compare actual matched source-must and pass-may call plans on one exact
normal input, including the exact backward result's forced DEMAND for
the may pass. Extend the all-form construction scan with touched-read,
mark, binding and identity-exclusion clauses, with GEN and binding
contexts kept distinct. These tests precede any resulting refinement.

The first focused compile cannot smart-cast the public mark property
across module boundaries. Bind the mark to a local value before the
abstract-premise exclusion assertion. Keep the assertion unchanged.

Run common-form and JVM-form tests and type/effect equivalence tests.
Complete all JVM construction cases in the Interpreter inventory; reserve whole-analysis cases
for J03. Compare named field actions with F74, their primitive cleaner with F75, and the pending
return path with late demotion. Every cached form used by a standing join has stable identity.

### E01. Add the run shell

**Requires:** V1–V2. **Proposal:** `analyzer-impl.md` §3, §6.

**Dependency staging and corrections.** The proposal's shell names the
method analyzer, summary services and seeds before their implementation
steps. Advance the complete pure contracts for `EdgeDelta`, `Cut`,
`Publication`, `Subscription`, `ProtocolSteps`, `SummaryApplier` and
`Seed`/`SeedIndex` into E01. Keep hand-off construction in D02 and the
summary algorithms in E04. Add `MethodContextSource`, `RunMethodContext`
and the real `JIRMethodContextCache` adapter here, as recorded in I06.

Use a required typed handler factory while the later handlers are absent.
`RunActor` supplies the method key, step/zero-work state, work operation,
freeze operation and result counters. `RunnerHandler<A>` creates actors
and handles non-Work events. `RunnerHandlerFactory<A>` receives the
runner port. `UnitRunner<A>` owns the actual analyzer map, channel,
queue and queued-actor set. Its loop runs Work directly on its owned
actor. `RunManager<A>` and `RunResult<A>` retain that concrete analyzer
type. E02 supplies `RunMethodAnalyzer`; E04 supplies the summary services
and production event handler. There is no default handler, no empty
summary service and no cast to an unimplemented analyzer. The E01 gate
uses finite test actors and complete empty/unknown-unit runs. It does
not claim program-analysis closure before E02–E05.

Require run index and field limit to be positive. Copy roots and seed
containers. Own sink requirement paths and delivery publication lists;
retain forward source edge identities and Facts identities. The record
input is a read-only live view. The driver must not change the underlying
record store during a run (A4); do not copy all records per run.
Use an ordinary `Seed.Sink` class with an owned requirement and explicit
structural equality/hash. A data-class input property cannot own a mutable
path in place. Preserve source edge objects and seed-list multiplicity.

The proposal increments the count but ignores a rejected `trySend`.
Reserve before visibility, then roll back a rejected send without adding
to the handled count. Work requeue keeps its reservation and gets a new
fixed priority snapshot. One queued-actor set prevents duplicate Work.
Unknown units are discarded before any runner or count is created.
Hold the start reservation until the full root batch is sent.
If root routing throws, select FAILED before releasing that reservation;
otherwise the release could select COMPLETE before the exception handler.
Use the same order if event enqueue throws after reservation: select
FAILED, roll back the reservation, then propagate the throwable.
Resolve units before the admission lock, then recheck run admission
before runner creation and send. Serialize reservation, acceptance and
completed-handler release with that lock, so a zero transition cannot
race an accepted send. A slow resolver does not block handler completion
or selection of an abnormal status.

Select the first terminal status before cancellation. Activate the
analysis cancellation only in manager construction. Close admission
and join all created runners on every exit, including caller-thread
throws. Freeze only after a successful join and COMPLETE status. A
failed join overrides the status with FAILED and exposes no analyzers.
Keep counters, queues, actors and run scopes fresh for each run.

The first configuration test review found that `ApMode` uses object
identity. Compare its four mode fields instead of a separately created
mode object; this preserves the configuration expectation.
Construct the shell fixture's AP manager with the same reference manager
that its memory guard receives. This tests the actual shared lifetime.
The shell API compile also found two fixture types: compare the optional
observer through its interface and pass the actual JIR instruction to the
JVM language manager. Keep the identity and call-location assertions.
The observer's expected list also needs the declared `AccessPathBase`
element type; its concrete Argument-only inference does not match the
recorded base list. Keep the exact expected value.
The protocol fixture must use valid links: the link layer equals the
added fact's layer, while the caller layer is independent. Use REACH for
zero-only subscriptions. Correct the fixture and its description.
The first executed shell gate found that the finite test actor returns
true while work remains. The actor contract and proposal use true for
completion. Return true only when its remaining count is zero; keep
the partial-quantum, count and freeze expectations unchanged.

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

**Dependency staging and corrections.** Require a publication sink and per-actor
local-service and call-service factories. E03 supplies standing requests, sink and
conjunction state; E05 supplies call processing and summary/record application.
Do not add default implementations that discard those operations. E02 tests use
explicit finite adapters for the tested operations and reject unsupported calls.
Advance the complete immutable `Triggers` result contract here. The method actor
owns one set of links, initials, edges, summaries and backward source hits. Expose
its real AP algebra to mode tests through an internal API. E04 binds the actual
publication sink and production event handler.

Detach an owned publication batch before publication, then check for new local
work or publications before reporting completion. Retain Facts identities. A
publication error must propagate; do not discard an unaccepted batch. Freeze only
after join, retain forward links, summaries, backward source hits and counters,
and release run ports, contexts, queues and injected services (§7.6).

The proposal clears the unchanged repeat set on a later removal. That misses a
terminating unchanged burst when no later removal occurs. Reset this set after
the final item's processing and successors, when the unchanged queue is empty
(§4.3). Do not reset it at pop or at a Work quantum boundary while unchanged work
remains. Test a later identical burst and a cycle across several quanta.
Use the complete K6 check `(facts is FlowTree) == premise.isFlow`, with
multi-premise conclusions restricted to TAINT. Add cancellation checks to each
processing, boundary-drain and candidate loop, including unchanged paths. Allocate
no backward conjunction state. Keep the unit runner's sole queued-actor set.
The local fixture must give its AP manager and memory guard the same reference
manager. Add an optional reference-manager input to the existing store fixture;
preserve its default and all existing callers.
For a synchronous publisher that adds fresh actor work, require full drain and
flush before a true return. The actor may continue within its current quantum or
return false. Do not require one particular return before checking this invariant.
The first focused test compile found three fixture typing errors: raw
`Conclusion` values need the explicit demand flag; a position-request expected
list needs the declared `RequestKind` element type; clear the fixture's mutable
publication collector instead of its read-only view. Keep the expected values.
Use DEMAND as the link layer of a DEMAND added fact. The caller layer can remain
NORMAL; the link and added-fact layers must agree (§8.3).
For FLOW added-fact fixtures, use a FLOW caller premise. Preserve the same
initial and counter checks. The native normal-must publication fixture needs
a matching exit demand: an entry-only demand has no publication (§6.4).
Keep the intended publication and raw-selection assertions with that matching
exit demand.
The core audit found missing cancellation checks inside the nested reversed
end-fact matching loops. Check each candidate and each sink edge, not only the
outer sink loop. Add actual-actor work tests for unchanged cycles across quanta,
repeat-set reset after drain, cancellation and publication failure propagation.
The cancellation fixture must reactivate cancellation before reading retained
store content. First assert that the cancelled store read also stops.
The final publication callback can cancel the analysis. Check cancellation after
flush and before returning completion. Check AP-algebra base reads too: GEN must
check cancellation even for a later candidate whose base does not match and which
therefore invokes no AP transfer. Add failing regressions before these changes.
Import the interpreter's zero fact and keep-edge helpers in the actor-work tests;
these helpers are not in the AP package.
The executed static-position test incorrectly expected STAGE and GEN to give no
result. These modes omit the statement-only exception and use the ordinary
above-premise row: an admitted root FLOW fact produces an abstract ANY result in
DEMAND without a position request. Correct these expected results; retain the
statement request and original-premise assertions. The same run reproduces both
new cancellation failures before their fixes (53 of 56 tests passed).
Capture the actual publication batch before the fixture copies it. Verify that
the actor's batch rejects mutation, retains the supplied Facts objects and stays
unchanged when a later batch is published.
Complete the final E02 integration rows with an unreachable forward normal exit,
a second caller reference for the same DEMAND added fact, and an actual exit-rule
overflow at limit one. Verify no new start work for the second link and verify
the filtered EXIT_RULES result before its cut. Also check that direct restricted
request handlers reject before invoking local services.

**Tests first.** Write `EngineAlgebraTest`, `ModesTest`, `FactKindsTest`, `ExitRulesTest`,
`CutPointTest`, `DeltaWorklistTest`, `MethodWorkTest` and `MethodServicesTest` before each handler.
Cover initial emission/start, statement, unchanged and
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

**Dependency staging and corrections.** Add `RunMethodLocalServices` as the real
E02 local-service implementation. It owns the run-1 request store and standing
join, the forward-only conjunction store and the trigger cache. Give
`MethodLocalServices` a required nullable conjunction-store property. The later
E6 call adapter must use this same store; it must not allocate a second owner.
The E02 finite adapter rejects unsupported conjunction-store access explicitly.
Pass the analysis cancellation to `RequestStore` and check every literal,
candidate, combination and seed before mutation or emission.

Extend the finite fixture to accept a full actor method key while sharing one AP
manager, method graph and forms. Use separate actors with the same canonical
premise in different contexts to check isolation. Keep publisher/call factories
required. Requests retain the complete caller reference; caller and link layers
remain independent. Zero links never answer or climb requests.
Use a zero or concrete caller premise for TAINT request-answer fixtures and a
policy FLOW premise for abstract climb fixtures. Preserve all expected answers
and layers. The raw link store does not validate the unseen caller edge's kind.
The E03 test review found raw `Conclusion` values without their required demand
flag. Supply explicit false for normal expectations and true for demand ones;
preserve the expected facts and layers.
Use `LocalConjunctionTest` for the local literal union and layer cases,
`SinkEndFactTest` for real witnesses and evaluated parts, `EndFactTriggerTest`
for backward trigger seeds, and `MethodLocalServicesTest` for allocation,
cancellation and lifetime. E05 adds the interprocedural `NdJoinAdapterTest`.
The first focused gate found two draft sink-fixture errors. A DEMAND must input
normalizes to may with empty E before sink admission. It therefore admits the
formerly excluded suffix. A mixed static exact/must input needs one common
header built directly; an exact-only input cannot retain E. Use an empty-E
exact caller premise and expect an empty-E selected exact witness. Retain E on
the mixed input and its remaining must summary. These corrections follow AP
normalization and do not change the local-service implementation.
The final coverage review adds real run-1 FLOW sink and conjunction requests
through the standing join and concrete answer, with a restricted abstract
control. Add two otherwise equal caller references with different caller layers
on one DEMAND link. Both references must climb independently and retain the
original premise. E05 checks that actual record replay creates no source hit;
the E03 finite call service explicitly rejects replay.

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

**Dependency staging and corrections.** Keep the E01 generic run shell. Add
run-owned summary storages to `RunManager` and required storage, subscribe and
link operations to `RunnerPort`. Add `SubscriptionPort` and real
`RunMethodHandlers`, with required local and call factories. The actual method
handler owns one runner-local subscription manager. A `SubscriptionHandler`
contract binds subscribe and freeze; a generic handler rejects unsupported
subscription use. Same-unit links call the actual handler directly; other
links use events. Runner release freezes subscriptions only after join and
then clears run-owned storages. Finite test ports reject unsupported operations
explicitly. E05 supplies the required real call service.
After run release, storage acquisition must reject instead of creating a new
map entry. The actual method handler drops its port and subscription-manager
references on freeze; later dispatch or subscribe rejects.

Expose the proposal's storage/index/subscription APIs. One `matches` function
serves replay and delivery. Own all list snapshots and deltas while retaining
the immutable Facts objects. Storage insertion and snapshot reads share one
lock. Match and invoke callbacks outside it. Use analysis cancellation in every
member, candidate, record/view and subscriber loop. Records replay only for
restricted non-zero subscriptions, with ordinary record matching and dedup.

Validate K6, multi-premise TAINT and temporary rejection at publication storage
admission before inserting index members or groups. Validate zero-only
subscriptions before inserting their separate zero table: their caller premise
is zero, added facts are REACH and the link layer equals the added layer, as in
the specified zret producer. Keep the caller layer independent. These are the same
producer boundaries as A08 and I04. Keep full tails, exclusions, layers and
caller references in keys; positional indexes return candidates only.
The index-test review found four pointer-identity assertions on nonzero AP
matching results. AP satisfying can create a new Facts wrapper with the same
value and shared root. Use value equality there, as DD5 requires. Keep the
zero-only bypass identity assertion and all full-scan comparisons.
The handler review found that a final subscription-manager field would keep
the released manager object. Use a nullable field and clear it with the port
on freeze, as required by the lifetime contract above.
The first focused compile requires an explicit `RequestKind` element type
for the handler's expected request list. Preserve the expected request.
The record-fixture review found exact child inputs used as positive native
ANY/must matches. The AP `applicable` tail gate rejects those exact children;
they also cannot contain the root premise. Use admitted any-tail added facts
for positive ANY/must rows, with explicit expected tails, layers and E. Keep
an exact root leaf for the exact-record row. Make the excluded-must negative
use an any-tail blocked location so it tests E after the tail gate.
The second focused compile found that the test timing helper `notifyAll`
collides with the final JVM monitor method. Name the helper
`runNotifications` and update its calls; retain the same queued schedule.
The first executed gate found the same ANY-tail fixture error in the deep
index row: an ANY premise does not match the exact added fact at that path.
Use an exact premise for this exact 32,768-accessor vector. Retain the full
path, both indexes, all modes and cancellation checks.
Use policy FLOW caller premises by default for FLOW subscription fixtures, as
the real call producer requires under K6. Keep zero as the default for other
facts. Change the independent full-reference variant to a distinct policy
path so it stays separate. This changes no matching expectation and adds no
caller-kind guard to the raw subscription or link stores.

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

**Dependency staging.** Add `RunMethodCallServices(core)` as the actual required
call factory product. It uses the actor's existing AP, forms, context, cut and
local-service boundaries. E6 uses the same forward conjunction store added in
E03. The service releases its owner and call-local caches on freeze.

Extend the finite method fixture with cached forward/reversed call plans and
actual call expressions. Keep the existing no-call adapter explicit. Required
subscribe/link callbacks can bind a protocol fixture or capture complete
events; absent callbacks reject call protocol use. Tests for returned stages
can call the real summary/record adapter directly on a known call plan. E07
adds the whole-program closure fixture and independent oracle.

The observer contract set in I05 requires new run-1 TAINT added-fact deltas.
The proposal's sample loop reports all non-REACH batches and does not remove
duplicates. Keep a call-local TAINT delta store at ADDED, with the full caller
reference and fact group. Report only its new TAINT deltas at an unresolved
stage. FLOW, REACH, repeated deltas and restricted runs raise no observation.
The observer must not select transfer results. Add a capture hook to the
finite interpreter and test these cases before implementation.
Declare the fixture's public interpreter as `Interpreter`. Kotlin cannot use
an anonymous object's two interfaces as its public inferred type. The object
still implements the observer for the runtime interface check.
The first focused gate passes 53 of 55 tests. Two call tests inspect the
method-edge store for an unchanged pass. E02 keeps unchanged items in the
worklist without inserting them in that store. Drain the actual actor and
compare its raw summary for untouched and REACH pass controls. Retain the
empty edge-store and no-call-cut assertions; this changes no transfer rule.
The native demotion test also contains a forward must-premise-to-REACH
record. AP §7.2 permits a nonzero REACH premise only backward, so that input
is outside the native record contract. Remove that loop case. Keep pending
exact/ANY results and ordinary backward zero returns in their own tests.

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

The E01 shell follows the proposal's wait-timeout fragment. Complete the
budget audit here: root routing and run setup must consume the same run
timeout. Do not start a fresh full wait budget after a slow root batch.
Test the routing/deadline boundary before refining it, with the first
terminal status and join rules retained (`analyzer-core.md` §0, §7.1).
Audit throwing actor and handler freeze here too. A required service can throw
during release. Test that failure before refining cleanup; complete release of
joined runner and storage owners even if one callback fails. The E04 concrete
subscription freeze itself has no throwing callback.

The E06 audit confirms that a slow unknown-unit resolver can release the
start phantom after the timeout and select COMPLETE. A known-unit resolver
or runner factory can also admit Start after the budget expires. Use one
monotonic run deadline from entry to `run`. Check it before and after root
routing and setup, and before releasing the phantom. Wait only for the
remaining budget. A previously selected terminal status keeps priority.

After a successful join, a throwing actor or handler freeze must not stop
release of other joined owners. Attempt all complete actor freezes and all
runner releases, then clear runner, job and storage maps. Collect failures
and propagate the first one after cleanup for the guarded driver. Preserve
the first run status; only a failed join overrides it. Each runner clears its
queue and actor collections in `finally`, even if its handler freeze throws.
The failed-join path still exposes no run stores and releases no live owners.

Q1–Q4 and unchanged-loop tests already exist from E01–E02. Add the remaining
actual-path checks here: zero-phase yield with pending publications, priority
resampling when a Work event is partial, and a counted publication callback
that creates more local work. Keep one Work reservation until both local work
and publication batches are complete. Write deadline and cleanup tests first.
The cleanup fixture review found nested `it` names in failure selection.
Use explicit method-key and unit names so the callback throws at the intended
owner. This test typing correction precedes its first executed check.
The actual method freeze has the same callback-failure gap: a throwing local
service prevents call-service freeze and reference release. Keep its empty
work/publication precondition before any release. After that check succeeds,
attempt both required-service freezes, detach run-only references, and then
propagate the first failure with later failures suppressed. Test the actual
actor before this refinement; keep its barrier-retained stores and counters.
The combined test compile needs `UnitResolver<CommonMethod>` in the actual
runner-priority fixture. State the method type explicitly; this changes no
routing or scheduling expectation.

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

**Completed evidence.** The deadline tests expose four expired-routing cases.
The cleanup tests expose failures in manager, runner and actual method release.
All 50 focused tests then pass. The clean common gate passes 1,054 tests in
108 classes, with no failures, errors or skips. JVM/Go test-source, JVM SAST
and application compile checks pass. All 906 new common tests return void
and occur in fresh XML. See `docs/reports/bidi-e06.md`.

### E07. Compare the engine with a direct closure and varied schedules

**Requires:** E02–E06. **Proposal:** `analyzer-impl.md` §9.1–§9.3.

The E07 audit needs a multi-method fixture. Existing local fixtures use one
method and cannot test whole-program context isolation. Add stable forms and
graphs per full method key. Keep separate raw demand, record and seed input
lists for reference scans. The production side builds its usual stores.
Use one test event pool with actual method handlers, actors, summary storage
and subscriptions. Select events, partial Work, replay and notification on
one thread. Test one unit per key and direct same-unit calls.

Compare processed closure, including unchanged work, rather than only the
edge store. Compare stored unions per full key and layer, not schedule-dependent
delta counts or pairwise leaf coverage. Retain complete raw summary, selected
publication, link, request, source-hit and witness observations. Add explicit
finite vectors before the support code and randomized schedules. Concrete
support confirmation and both hand-off outputs depend on D01/D02; compare
their raw inputs here and their completed outputs in V4. This stages §9.2's
driver observations at their implementation dependencies.
The production handler owns its publication sink. At quiescence, the test
scheduler may read the private storage publication map with a read-only test
adapter. This records actual publication unions without replacing the handler
or recomputing publication selection. The direct reference uses no such map.
If a scheduled callback fails, retain its seed and trace. Freeze method actors
only after the whole event pool has closed; removing the failing last event
does not establish closure.
Validate the test scheduler's positive quantum before constructing its shared
owners. This retains the production scheduler's admitted input domain.
Use actual `ExitRules` members for test diagnostics; read its drop flag from
the exit object. The first support compile also requires explicit
`ConcurrentHashMap.containsKey` for the immutable-form check. Use an
assertion failure in the semantic comparator so the test attaches its
seed, program and event trace to a mismatch.
The next fixed compile needs the shared AP `ClassStaticAccessor` import and the
manager-owned `PathNode` for a non-root cleaner. Correct those test types
before changing expectations. Build each bounded comparison universe from
all observed and expected bases, including link and witness facts, with fresh
accessor and mark values.
Decode Link, Request and Delivery trace payloads into raw patterns and
conclusions. Default AP object strings contain identity hashes and omit
annotations; repeated seeded runs need the same complete value trace.
The first nine fixed closure tests pass. The comparison audit then finds
that normal FLOW T2/T2' folds can change stored E and abstract mark exclusions
while retaining the complete pair union. Add explicit equivalent folds and
unequal single-group controls before correcting the adapter. For stored
edge unions, retain method, node, full premise, kind, layer, base and semantic
component as headers. Decode conclusion E and marks into the cells; do not
also require an equal number of annotation groups. Keep raw DTOs for exact
operation checks and full initial/request headers. For links and witnesses,
retain caller and literal support headers; the downstream T2 control below
checks their conclusion annotation unions.
The synthetic assigned-result call helpers must include their written result
base in `touched`. Existing forward inputs do not use that base, but reverse
pass-over identities depend on it. Correct the generic and static helpers
before adding backward call vectors; keep additional alias outputs separate.
The V3 preparation audit finds that the large subscription fan-out has index
coverage but lacks an actual complete-program delivery check. Add an E07
finite program with at least 17 subscribed callers before a callee publication.
Compare may, must and excluded must added facts above precise premises through
the actual storage and subscription manager. Retain an admitted sibling and an
excluded-field negative, and test direct and cross-unit dispatch.
Record real subscription attempts and their decoded payloads in the schedule
trace before invoking the actual handler. The fan-out test checks distinct
nonzero consumer headers before the callee result delivery; use a longer
finite callee graph to make that order visible, then assert it explicitly.
Add a fixed E6 producer/consumer loop with the consumer's second literal
source delayed by a finite chain. Assert that the first literal subscription
precedes the callee result delivery and the second one follows it, then replay
completes the same call's combination. Test normal and mixed layers before
using that shape in the seeded schedule matrix.
The comparison review also needs a whole-program T2 control. Two equal-root
must facts with different field exclusions can merge before a call or sink.
Add a fixed program that exposes the downstream links and root sink witnesses.
Check their stored unions under §9.2, while retaining the full caller premise,
method, call, layer and literal support headers. Save the result before changing
the comparison adapter; exact raw observations remain available for operation
and support checks.
The first five-family seeded gate finds a test assertion at native-record
seed 1. The excluded returned read produces only the raw sibling alias;
the caller demand selects the result base, not that alias. Require selected
demand only for an admitted pending read. Retain the raw alias expectation
and the complete closure comparison.
The first fixed split-E6 gate agrees on full closure but fails its arrival
order assertion. The consumer's zero path traverses the whole delay before
its first nonzero literal. Stop zero at node 2. Emit a separate nonzero delay
signal before the first literal, then map the signal to the second literal
at the delayed node. Seed the real signal source at entry; the later map is
conditional and gives no source seed. Include the signal base and mark in
the bounded universe. Retain both strict delivery/replay order assertions.
Both downstream T2 controls fail in all six fixed schedules. Independent
location unions and all caller/literal support headers agree; every other
stored observation agrees. Only the comparison header still requires equal
conclusion E groups. Apply §9.2's stored-union comparison to added link and
sink conclusion cells too. Keep callee, full caller premise, caller layer,
call, link layer and component in link headers. Keep full witness method,
statement, alternative, run, ordered literal premises/layers/kinds/bases and
confirmation in witness headers. Decode conclusion E and mark exclusions
into cells, while raw DTOs and actual stores keep their full exact shapes.
Add equivalent partition controls and unequal annotation/header controls;
do not change production link or witness merging.

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

The preparation audit also needs explicit actual-path controls for a dead
local reaching a sink, the synthetic two-level write's admitted and excluded
sinks, all three cleaner reaches at a call, and a normal singleton raw summary
whose publication is accepted or rejected by its exit mark. Add those fixed
rows before the V3 gate. Include the setter followed by excluded and admitted
reads, with actual sink outcomes for Analyzer item 29.
Analyzer item 42's excluded-mark demand remains a
generic raw emission vector: canonical `DemandStore.Builder` requires
normalized marks, so do not describe that input as a reachable stored demand.
The first acceptance gate finds a malformed read fixture. Its operand base
is absent from `StatementSummary.touched`, so both readers take the unchanged
path. Include the operand and both written result bases, with its keep edge
and the two read edges. Retain the independent excluded/admitted sink and
normal must expectations. This is a fixture correction, not a transfer change.

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
The D01 preparation review finds omitted API guards in the proposal snippets.
Support reads only a COMPLETE forward result. Report updates from incomplete
or backward runs leave both report sets unchanged. Guard empty member
collections at the public support query, because stored premise keys are
nonempty. Check cancellation during site, member, link and witness scans,
including direct support queries and confirmation. Test these guards before
implementation.

Keep confirmed entries final. Replace unconfirmed demand entries from the latest complete
forward run. An incomplete forward run changes neither set; incomplete run 1 leaves an empty
report. Preserve witnesses without merging contexts or alternatives. Store `AnalysisEnd`.

**Gate.** Two separate calls cannot confirm an ND witness. Cycles without a root remain
unsupported. Tails, marks and E affect supplier admission. Mixed confirmed/demand reports and
all incomplete-run sequences match the specified state transitions.

**Result.** All 40 new support, joined-run confirmation and report tests pass.
The fresh common gate passes 1,128 tests in 117 classes. JVM and Go test sources,
JVM SAST and application test sources compile. The report records the failing
API gates, fixed-point checks, exclusion vectors and discovery evidence.

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
The D02 preparation review requires COMPLETE results before hand-off or
frontier scans. Use `RunResult<RunMethodAnalyzer>` for the retained summaries,
hits and counters. Guard each leaf, member, sink, hit and frontier walk with
cancellation checks. Own the frontier sets/maps and hand-off count map;
retain only keys and counts. The current engine has no optional cause counters,
so keep that optional field null rather than inventing counts. Historical
`crossableLeaves` does not belong to the current frontier contract.
Derived forward-to-backward sink seeds are exactly deduplicated, as in the
proposal, while `SeedIndex` itself retains input multiplicity. Test that
distinction and the implicit zero builder rejection explicitly.

**Gate.** Both hand-offs match direct construction, including excluded abstract marks, distinct
concrete marks, zero and ND branches. Reader-result DEMAND creates caller propagation only.
END retains the later finding; CONJ cannot create a one-literal forward record. Frontier logs
describe accepted builder contents, not attempted insertions.

**Result.** All 29 new tests pass. Actual END reversal recovers a source seed
from a confirmed trigger and retains the later demand finding. Actual CONJ
reversal keeps each literal branch in DEMAND and excludes singleton records.
The fresh common gate passes 1,157 tests in 122 classes. All affected consumer
test sources compile. Independent expected patterns and accepted-count checks,
ownership reviews and discovery evidence are in the report.

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

The D03 preparation review requires the actual generic manager factory:
`RunMethodHandlers` with the local and call services. Keep the public driver
constructor from the proposal. Add internal run-invocation and barrier-wrapper
seams for deterministic status and late-guard tests; their defaults create
fresh real managers and memory guards. Keep each run result in the one-run
frame only. Own unmodifiable frontier snapshots for the getter and policy
input, and copy the FixedLimits list. Read and check the first limit once.
Enforce §7.1's one analysis budget: a policy timeout cannot renew an expired
budget or exceed the remaining time. Recompute remaining time after the policy
callback and cap its selected timeout to that remaining time. Test these
proposal guard and ownership corrections before implementation.

**Gate.** Check 1/2/3 and 1/2/3/4/5 schedules, equal/nondecreasing limits, exhausted budget,
complete/incomplete barriers and all statuses. The DLINK root EXACT fixture continues when its
only demand object is a link. Optional diagnostic counters cannot change a stop decision.

**Result.** All 23 new driver tests pass. Actual 1/2/3 and 1–5 schedules
check hand-off, records, source identity, frontier order and final confirmation.
Status, report, guard and budget cases pass with real joined managers. The
fresh common gate passes 1,180 tests. All affected consumer test sources
compile. The independent driver review finds no contract defect. See
[D03 evidence](../docs/reports/bidi-d03.md).

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
