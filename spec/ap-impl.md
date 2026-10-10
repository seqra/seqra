# AP, stores and IR interpreter — implementation proposal

Status: implementation proposal for phase 1 of [bidirectional-task.md](../bidirectional-task.md), with F72, the F75 cleaner correction, and the F76 must-summary read views. It implements the
two parts of the AP spec: [`ap.md`](ap.md) (Part I: the access path and its stores) and
[`interpreter.md`](interpreter.md) (Part II: the IR interpreter for the JVM). The specs are normative. This document
does not change them. [`analyzer-impl.md`](analyzer-impl.md) (phase 2) calls the facade `ApOps` (§5), the stores
(§7), the `Interpreter` and its forms (§23, §25). JVM only.

The demand, satisfaction, reduction and hand-off rules below use the current per-leaf reference operations.
`CurrentDemand` checks emission and reduction witnesses and the prefix restriction index.
`CurrentTreeReduction` checks reduction against those operations, including marks and layers.
`CurrentSummarySelection` checks structural demand selection and its constant-time group shortcut.
Use these operations before a tree optimization. General F72/F75 coverage, X integration and the concurrent pipeline remain open
(`ap.md` §11.2). The local F74 lowering and F75 cleaner checks do not close those obligations.

Language: ASD-STE100 Simplified Technical English. Code first.

Numbering: Part I uses §0 to §8, Part II uses §20 to §34. In the text and in the package maps, a qualified name
(`ap.md` §4.1) names a spec section, and a bare number names a section of this document. In a Kotlin comment, a bare
`§` number names a section of the spec that the part implements (`ap.md` in Part I, `interpreter.md` in Part II), and
`Part I §n` or `Part II §n` names a section of this document. A rule id (`S7`, `W2`, `T1`, `I3`, `AC3`, `O1`, `P4`)
names the rule of the spec that defines it. Two specs have `A` ids: a bare `A1` to `A6` is a rule of
`interpreter.md` §2.5, and an assumption of `analyzer-core.md` §0.1 is always qualified (`analyzer-core.md` A4). The
`D` ids of Part II are the preserved deviations in `implementation-background.md` → Interpreter migration differences → §6. The ids `DD`, `K` and `SI` are ids of this document.

Paths of today's code: the design decisions and Part I give them relative to
`core/opentaint-dataflow-core/opentaint-dataflow/src/main/kotlin/org/opentaint/dataflow/`, unless they start with `core/`.
Part II uses the prefixes `DF/`, `JVM/` and `BWD/` (see the scope of Part II). `path:n` is line `n` of that file; a bare
`:n` is line `n` of the file that the row or the comment names.

Each part ends with its test plan (§8, §33). §34 lists the spec issues and the deviations from today's code.

## Design decisions

The sections below cite these decisions by their id.

| Id | Decision | Reason | Code |
|---|---|---|---|
| DD1 | Put `bidi.ap`, `bidi.store` and `bidi.interp` in today's dataflow module; put `jvm.bidi.interp` in the JVM module. | Reuse internal utilities. The prescan keeps the old core. | §1, §20 |
| DD2 | Keep per-path reference forms. Store conclusions as one typed `Facts` group: REACH, FLOW or TAINT. | Compare tree operations with their per-path reference. | §6, §8 |
| DD3 | Propagate `(premise, node, facts)`. `facts.layer` is the layer; its kind replaces an Edge class. | The premise and conclusion determine the kind. | §3.4, §4.1 |
| DD4 | Keep added facts as merged `Facts` values per caller/link/group key. Insertions return new leaves. | Replay and delivery share `ApOps.satisfying`. | §5.4, §7.5 |
| DD5 | Intern AP keys. Give each store a soft trie-node cache within its guarded run or barrier. Trie and Facts equality is structural, with an identity fast path. | Clearing a cache loses sharing only. Cross-store comparisons must remain valid. | §4.5, §5.1 |
| DD6 | Use `AccessorIdx` for fields, elements and class accessors. Count fields and elements for L. | `ap.md` W5; the prescan owns type-info/value accessors. | §3.1, §6 |
| DD7 | Use `TaintMark(id)` and `MarkTable` in `bidi.ap`; reserve `TaintMark.ZERO`. Map JVM rule marks by name. | The common dataflow module cannot use the JVM rule-mark type. | §3.1 |
| DD8 | Add `AccessPathBase.Zero`. | The old core never produces it. | §2, §3.4 |
| DD9 | Apply the path type filter, then mark policy at the root and each element-chain level. Backward runs apply neither. | Match `interpreter.md` §5.1, including element marks. | §5.5, §26.2 |
| DD10 | Key vulnerabilities by `(rule, method without context, statement)`. Keep witnesses per alternative, method key, run and shape. Merge triggering facts only within that entry. | Preserve confirmation inputs and sink alternatives. | §7.12 |
| DD11 | Cache calls and entry rules per method key; cache statements and exit rules per method. | Calls and start filters read the context. | §31.2 |
| DD12 | REACH holds zero; FLOW holds abstract marks; TAINT holds concrete marks. All can occur in restricted runs. Normal FLOW is star/E, demand FLOW is any; normal TAINT any is must/E, demand TAINT any is may. | Enforce `ap.md` §7.2 and canonical packing guards. | §4.1, §5.9 |
| DD13 | Use InitialAp for one premise and a sorted PremiseSet for two or more. Union drops zero unless every input is zero. ND premises are concrete and have TAINT conclusions. | `ap.md` §7.1; `NDZero.dnz_to_dn`, `dn_to_dnz`. | §3.4, §5.1 |
| DD14 | Use one generic trie and leaf algebra. Share merge/delta, maps, paths, graft, subtraction, fold, cut, filter and interning. Dispatch kind-specific gates and transfers through ApOps. | Keep one implementation per operation. Use `KaryJoin` and `StandingJoin` for their distinct joins. | §4, §5, §7.10 |
| DD15 | `applyCompiledEdge` is tree delta-concat. `MarkCheck` checks literals; `MarkGate` is the shared mark gate for transfer, checks and satisfaction. | Give the same rule one implementation. | §5.3, §5.8 |
| DD16 | Forward normal TAINT any leaves mean `[any-taint]` and share E; exact leaves ignore E. Backward runs have no must tail. Demotion drops E. Must leaves never absorb exact leaves. `MicroEdge.may` reads the forward ANY target or reversed-conjunction flag. | `ap.md` W6/W8: retain normal exact facts through later demotion. No rule-kind field or new trie payload is needed. | §3.2, §3.4, §4.3, §5.3, §5.6, §5.7, §7.8, §23.1 |
| DD17 | Persist raw normal R1 summaries. Omit them from callee demand. Keep demand-layer, multi-premise and backward zero-premise summaries, and restrict their publications. Keep reversed conjunction results demand and retain end-fact triggers. | Replay supplies normal singleton summaries; a demand result from a read view propagates in the caller. | §5.9, §7.6, §7.8; `analyzer-impl.md` §4.7, §7.2 |
| DD18 | Abstract entry marks share one emitted FLOW premise. Concrete entries emit marked intersections. Restricted canonical FLOW uses overlap; other FLOW shapes retain applicable or inside; concrete satisfaction uses inside. Reduction tests premise containment, exit marks and locations. Normalize abstract mark exclusions at hand-off. | Current `ap.md` §6.3, §6.4, §9.2. | §5.4, §5.9, §6, §7.7 |

---

## Part I — The access path and its stores

Scope: the AP with all its stores (`ap.md` §2 to §9) and the test plan of `validation-plan.md` → AP tests → §13. The code is in the module
`core/opentaint-dataflow-core/opentaint-dataflow`, in the new packages `org.opentaint.dataflow.bidi.ap` and
`org.opentaint.dataflow.bidi.store` (DD1). The behaviour of the old core does not change (the prescan runs it).

## 0. Conventions and additions to the AP interface

### 0.1 Conventions

| # | Convention | Reason |
|---|---|---|
| K1 | A path element is `AccessorIdx` (DD6): a field, an element or a class accessor. `isClass` is `AccessorIdx.isStaticAccessor()`. A counted accessor is a field or an element accessor (`isCounted`, §3.1). | `ap.md` §1, §3.4, W5. |
| K2 | A mark is the value class `TaintMark` of `bidi.ap` (DD7). | The JVM `TaintMark` is not visible in `opentaint-dataflow`. |
| K3 | The zero base is `AccessPathBase.Zero` (DD8). Each exhaustive `when` over `AccessPathBase` gets a `Zero` branch (§2). F74 also adds `CleanerTemporary` (§23.4); language position resolvers, serialization and trace code reject it. | `ap.md` §1, §2.4; the temporary stays inside a field action. |
| K4 | `MethodKey` is `typealias MethodKey = MethodEntryPoint` (REUSE, `ap/ifds/MethodWithContext.kt:24`), declared in `bidi/ap/Premise.kt` (§1). `bidi.ap`, `bidi.store`, `bidi.interp` and the `bidi.engine` of `analyzer-impl.md` import it from there. | `ap.md` §1 and `analyzer-core.md` §1 define the method key as the `MethodEntryPoint` of the method (context and forward entry statement). |
| K5 | A RUN store has one writer (the runner of its method, `analyzer-core.md` O1). It has no lock. `ApManager`, `ApOps` and `VulnerabilityStore` are thread-safe. §7.1 gives the table. | `analyzer-core.md` §2. |
| K6 | A FLOW premise is one `InitialAp` with the mark `*` (`PremiseKey.isFlow`). Every other `InitialAp` gives REACH (a conclusion on the zero base) or TAINT conclusions; a `PremiseSet` gives TAINT only. | `ap.md` §7.2, §4.6. |
| K7 | AN ANY TAIL is `Tail.ANY` (`[any]`, a may) or `Tail.ANY_TAINT` (`[any-taint]`, a must; `Tail.isAny`, §3.2). A test that reads only locations (`covers`, `overlap`, `tailAdmits`, `cleanPos`, the filter) reads `ANY_TAINT` as `ANY` with its exclusion `E` as a part of the location set: an accessor in `E` is not the first accessor after the path (an `[any]` has the Empty exclusion). In a trie the LAYER names an any leaf (DD16): `[any-taint]` in a normal TAINT tree, `[any]` in a demand tree; the per-path view (`ApOps.leaves`, §5.11) gives that name and the exclusion of the tree. An `[any-taint]` premise is a MUST-PREMISE: it occurs only in a FORWARD restricted run (W8 (c)); the backward run has no `[any-taint]`. | `ap.md` §1 (any tail), §3.1, §3.4, W8; Lean `AnyTaintEx.coversX`, `coversFX`. |

### 0.2 Additions to the AP interface

`ap.md` gives the types of `ap.md` §3.4, §7.1, §7.2 and §8.7, the operations of `ap.md` §4, §6.2 to §6.5 and §9.1,
and the stores of `ap.md` §8. Part I adds these members. `analyzer-impl.md` and Part II call them.

| What | Why | Code |
|---|---|---|
| `PremiseKey` (`size`, `member(k)`, `isZero`, `nonZeroCount`), `PremiseSet`, `ApManager.premiseOf(members)`, `ApManager.union(a, b)`, `PremiseKey.members`, `PremiseKey.forEachMember`, `PremiseKey.isFlow` | The premise set of `ap.md` §7.1 without a list (DD13); the union of `ap.md` §4.6, without the zero fact; the kind of `ap.md` §7.2 (K6). | §3.4, §5.1 |
| `Facts`, `Reach`, `FlowTree`, `TaintTree` (with `TaintTree.exclusion`, DD16), `Facts.groupKey` (`GroupKey`, `FactsKind`); `Layer.or(demand)` | The three conclusion kinds of `ap.md` §7.2 (DD12); the exclusion of the `[any-taint]` leaves of a normal TAINT tree. | §4.1 |
| `TrieNode<P>`, `TrieLeaf`, `FlowLeaf`, `TaintLeaves`, `LeafAlgebra<P>`, `FlowAlgebra`, `TaintAlgebra`, `TrieInterner<P>`, `walkPath` | The one trie and its generic algorithms (DD14). | §4 |
| `FlowGroup`, `TaintGroup`, `ConclusionGroup`, `StoreInterners` | The merge rules T1 to T5 and the subsumption of `ap.md` §8.1, per kind and for all kinds of one premise key. | §4.3 |
| `ApMode`, `ApOut` (`result(f: Facts)`), `CollectingOut` | The constants of a run that the operations read; the receiver of the results and of the requests. | §3.2, §5.2 |
| `ApOps.applyEdge` (with `may`), and inside it `applyCompiledEdge`, `EdgeApplication`, `MarkGate` | The micro edge on all paths of one `Facts` (`ap.md` §7.3; DD15). `may = true`: every result in the demand layer (a micro edge whose forward target is `[any]`, and since F70 the reversed literal of a conjunctive edge: `MicroEdge.may`, Part II §23.1). | §5.3 |
| `ApOps.satisfying(a, j: InitialAp, mode, record = false)`, `satisfying(a, j: Pattern, mode, record = false)` | The part of an added fact whose facts satisfy the premise `j` (P4 of `analyzer-core.md` §5.3, DD4). | §5.4 |
| `Tail.ANY_TAINT`, `Tail.isAny` | The tail `[any-taint]` of `ap.md` W8 and the any-tail test of `ap.md` §3.4 (K7, DD16). | §3.2 |
| `ApOps.startFact(i)`; the reference `startFact(i)` | `ap.md` §6.5: a must-premise `(x, p, [any-taint], E, T)` starts as itself, in the normal layer, with its exclusion (Lean `AnyTaintEx.startX`). It occurs only in a forward restricted run, so no row reads the direction: no direction parameter (A1). | §5.9, §6 |
| the reference `recordDemand` (`ap.md` §6.3) | THE RECORD DEMOTION of a must record (`ap.md` §4.3; forward only); its tree form is the split of §5.4, which the analyzer does (`analyzer-impl.md` `applyRecord`). | §5.4, §6 |
| `confirmableMember(j, mode)`, `supplies(jm, link, mode)` (reference forms in `VulnerabilityStore.kt`) | `ap.md` §4.9 condition 2 and condition 3.2.3 per (member, link), with the must-premise and the `[any-taint]` support link with the exclusions (`AnyTaintEx.SupLinkX`): the oracle of the driver's `Support` (`analyzer-impl.md` §7.5), which is the code. | §7.12 |
| `revEdge(e)`, `ApOps.reverse(e)` | Generic micro-edge reversal (`ap.md` §9.1), separate from `reverseRecordLeaf`: null for an `[any-taint]` premise and for an `[any-taint]` conclusion with a non-empty exclusion; the target `[any-taint]` of a source reverses to the premise `[any]` (the backward run has no `[any-taint]`, `interpreter.md` I14, §4.9). | §5.11, §6 |
| `ApOps.applySummary(a, j, g, mode, out)`, `applyRecordLeaf(a, view, mode, out)`, `applyCombination(parts, g, mode, out)` | A summary on the satisfying part, by kind; a summary with several premises on one full combination (`ap.md` §4.6, event E6). | §5.4 |
| `ApOps.filter`, `TypeFilter`, `MarkPolicy` (`keeps(mark, elements)`) | `ap.md` §4.8 and the mark policy of `interpreter.md` §5.1 at the root and below each `[e]` (DD9). | §5.5 |
| `ApOps.clean`, `Cleaner`, `CleanReach` | `ap.md` §4.7, per kind. | §5.6, §6 |
| `ApOps.limit` | `ap.md` §4.4, with the tables of the cut points and of the facts that can exceed `L`. | §5.7 |
| `ApOps.checkMark(c, p, mode): MarkCheck` (`None`, `Request`, `Holds(facts, covered)`) | The one check of a mark literal: a sink, a conjunction literal, a literal of a conjunctive sink (`ap.md` §4.6, §4.9; DD15). | §5.8 |
| `ApOps.without(c, part)`, `ApOps.withoutMarks(c, marks)`, `ApOps.targetTree(target, layer)`, `ConjunctiveEdge` | The exact removal of a part (the global-state rule of `interpreter.md` §4.7 step 3); the removal of every leaf with a mark of a set, at every depth and with both tails (the entry marks, `interpreter.md` §4.7 step 4); a one-leaf TAINT tree for a conjunction target or an end fact; the conjunctive micro edge. | §5.8 |
| `ApOps.zero(layer): Reach`, `ApOps.policy(added)` | The end facts take no input fact: on a trigger they apply to the zero fact in the layer of the sink edge or of the combination (`interpreter.md` §4.1); `ap.md` §6.2 on one `Facts`. | §5.9 |
| `ApOps.restrict(j, g, d): List<Facts>` | `ap.md` §6.4 on one value, AS AN INTERSECTION (F70 D4; DD17), MARK-AWARE (F71; DD18): the premise must lie inside `D-c` in its locations and its marks, the leaves whose mark does not meet the mark of `D-p` go, and the tails meet at the path of `D-p`. One tree has one exclusion, so one value gives up to three values: the leaves at or below `D-p` keep the exclusion `E` of `g`, the `[any-taint]` leaves at a `*/E2` `D-p` get `E ∪ E2`, the chain of the leaves above `D-p` gets `E2` or none (Lean `Handoff.restrictI`; with the exclusion `HandoffX.restrictIX`, `meetExX`, `chainExX`). | §5.9 |
| `ApOps.demandPart(premise, g, direction)` | Keep the whole raw group if it is demand-layer, has several premises, or is backward with the zero premise. Omit every other normal singleton group. The hand-off receives the selected group's published restrictions. Selection does not inspect leaves, marks, tails, exclusions or read-view result layers. | §5.9 |
| `RunSummaryStore.addDemand(premise, piece)`, `demandEdges()` | Store the published pieces of selected raw groups during each summary delta (`analyzer-impl.md` `summaryDelta`). The hand-off reads only these pieces; `all()` stays for R1 persistence. | §7.6 |
| `ApOps.requestAction(i, kind, a, caller): RequestAction` | The AP rule of one (request, link) pair (`ap.md` §4.5, §4.10 items 2 to 4). | §5.10 |
| `ApOps.leaves(f)`, `ApOps.leavesNear(f, p)` | The per-path view of one `Facts`, and its leaves that overlap `(f.base, p, *, {}, *)` (one walk of `p`). The links and the tests read it. | §5.11 |
| `ApManager(cancellation, refManager)`, `ApManager.softRefs`, `ExclusionSet.of(a: AccessorIdx)`, `InitialAp.manager` (internal), `ApManager.newInterners()`, `ApManager.flowTree`, `taintTree`, `factsOf` | The `RefManager` of the memory guard holds the soft trie tables (DD5); the exclusion `{a}` of one keep edge (`interpreter.md` §2.1 `strongKeep`); `Record.reversedAt` builds transient Pattern/Conclusion leaf views; the trie interners of one store (DD5); the canonical factories of the three kinds. | §3.2, §3.4, §4.5, §5.1 |
| `MethodEdgeStore(m, method, lm, fieldLimit)`, `edgesAt` | The edges per kind (`ap.md` §8.1), with the W3 assert; two queries of the edges at a statement for the tests (trace resolution is out of scope, `ap-history.md` F67). | §7.3 |
| `AddedFactStore` (the key per kind, `add` returns the delta, `overlapping(base, path)`, `links()`) | DD4. The request join of `ap.md` §8.8 and the support at the barrier read the links. | §7.5 |
| `DemandStore.Builder`; the implicit zero demand of `near`; `DemandStore.covering` | The driver builds the store before the run (`analyzer-core.md` A4); every method key has the zero demand (`analyzer-core.md` §4.4; F70 D5: no change). `covering`: the restriction query of the intersection, the patterns whose `D-c` is at or above the premise (F70 D4). The index is keyed by the chain only: `emit` and `restrict` test the marks on the returned entries (F71, DD18). | §7.7 |
| `RecordStore` (an interface), `PersistentRecordStore`, `view()`, `persist(direction, summaries)`, `Record.reversedAt(a)` | A run reads the store through a read-only view (`analyzer-core.md` A4). The driver persists the records at a barrier (R1: every normal one-premise summary, in a forward run also the must records and the `[any-taint]` leaves; a backward run has no `[any-taint]`). A reader in the other direction reads transient `RecordLeafView` values. New forward must shapes use `reverseRecordLeaf`; other permitted leaves use generic `revEdge`. Every omitted normal singleton summary is persisted; replay replaces its callee analysis (§7.8). The layer of a reversed view result does not create a demand for the summarized method. | §7.8 |
| `KaryJoin<T>`, `StandingJoin<A, B>(nearB, nearA, meet)` (`newA`, `newB`); `ConjunctionStore.add(rule, statement, arity, literal, input)`, `Input`, `Combination`, `ndJoin<S>(NdKey)`, `NdSummaryJoin` | The two standing joins: k slots of one type (the literals of a conjunction or of a conjunctive sink, the members of a summary with several premises; `ap.md` §8.9, `analyzer-core.md` §5.4), and two sides with index lookups (the request × link join of `ap.md` §8.8). | §7.10 |
| `RequestKind.Position(path: PathNode)` (the type of `analyzer-core.md` §10) | The path of a position is an interned `PathNode` (§3.3), not a list: the key of `RequestStore`, the argument of `ApOut.positionRequest` and of `ApManager.position`. | §5.2, §7.9 |
| `SourceHitStore.entries()` | The driver reads the source hits at the barrier. | §7.11 |
| `VulnerabilityKey(rule: CommonTaintConfigurationSink, method: CommonMethod, statement)`, `SinkEdge(premise, layer, facts: Facts)`, `SinkWitness(alternative, methodKey, edges, run, endFacts)`, `ConcurrentVulnerabilityStore` | The key reuses the rule object of every run and has the method without the context. Several witnesses per key, one per alternative and method key; the witness merge (DD10). | §7.12 |

---

## 1. Package map

```
core/opentaint-dataflow-core/opentaint-dataflow/src/main/kotlin/org/opentaint/dataflow/
├ ap/ifds/Accessors.kt              GENERALIZE: + AccessPathBase.Zero (DD8)
├ ap/ifds/AccessPathBaseStorage.kt  GENERALIZE: `Zero -> error(...)` in `getOrCreate`/`find` (the old core never makes Zero)
└ bidi/
  ├ ap/
  │ ├ Facts.kt             Direction, Layer, Tail, ApMode, TaintMark, MarkSet, MarkSlot, ExclusionSet (§3.2)
  │ ├ AccessorTable.kt     AccessorTable, MarkTable: id <-> value, no ConcurrentReadSafe map (§3.1)
  │ ├ PathNode.kt          PathNode: the interned accessor chain of a premise (§3.3)
  │ ├ Premise.kt           PremiseKey, InitialAp, PremiseSet, the zero fact (§3.4); typealias MethodKey (K4)
  │ ├ Trie.kt              TrieLeaf, FlowLeaf, TaintLeaves, TrieNode<P>, LeafAlgebra<P>, FlowAlgebra, TaintAlgebra (§4.1)
  │ ├ TrieOps.kt           the generic trie algorithms: merge, delta, maps, prepend, chain, minus, graft, fold, subtract, cut, filter (§4.2–§4.4)
  │ ├ TrieInterner.kt      T6 hash-consing, one per store and kind, its table behind a soft reference (§4.5)
  │ ├ PathWalk.kt          walkPath: the prefix nodes and the node of a path, for every trie (§4.6)
  │ ├ Conclusions.kt       Facts, Reach, FlowTree, TaintTree (§4.1)
  │ ├ Groups.kt            FlowGroup, TaintGroup, ConclusionGroup, StoreInterners: T1–T5 and the subsumption (§4.3)
  │ ├ ApManager.kt         the interners and the factories (§5.1)
  │ ├ ApOut.kt             ApOut, Results (one collector per kind), CollectingOut (§5.2)
  │ ├ ApOps.kt             the facade of the AP operations (§5)
  │ ├ MarkGate.kt          MarkGate: ap.md §4.1 steps 4 and 5 per kind (§5.3)
  │ ├ EdgeApplication.kt   CompiledEdge, applyCompiledEdge, EdgeApplication: the tree form of delta-concat (§5.3)
  │ ├ Summary.kt           satisfying, applySummary, applyCombination (§5.4)
  │ ├ Clean.kt             the cleaner per kind (§5.6)
  │ ├ Limit.kt             the field limit, the cut (§5.7)
  │ ├ MarkCheck.kt         MarkCheck, checkMark, without, targetTree (§5.8)
  │ ├ Demand.kt            zero, startFact, policy, emit, restrict, demandPart (§5.9)
  │ ├ Primitives.kt        Cleaner, CleanReach, TypeFilter, MarkPolicy, ConjunctiveEdge, RequestAction
  │ └ Reference.kt         the per-path forms of ap.md §3.4, §4.1, §6.3, §6.4 and of §6 (DD2)
  └ store/
    ├ PathTrie.kt          the path index of ap.md §8 (§7.2)
    ├ MethodEdgeStore.kt   ap.md §8.1, per kind (§7.3)
    ├ InitialFactStore.kt  ap.md §8.2 (§7.4)
    ├ AddedFactStore.kt    ap.md §8.3; CallerRef, Link (§7.5)
    ├ RunSummaryStore.kt   ap.md §8.5 (§7.6)
    ├ DemandStore.kt       ap.md §8.6; DemandStore.Builder (§7.7)
    ├ RecordStore.kt       ap.md §8.7; Record, RecordStore, PersistentRecordStore (§7.8)
    ├ RequestStore.kt      ap.md §8.8; RequestKind (§7.9)
    ├ KaryJoin.kt          the standing joins: KaryJoin, StandingJoin (§7.10)
    ├ ConjunctionStore.kt  ap.md §8.9; NdSummaryJoin (§7.10)
    ├ SourceHitStore.kt    ap.md §8.11 (§7.11)
    └ VulnerabilityStore.kt ap.md §8.10; VulnerabilityKey, SinkEdge, SinkWitness (§7.12)
```

---

## 2. Reuse map

The kinds of reuse (both parts):

* REUSE: import today's class and call it as it is.
* GENERALIZE: make today's class generic (a type parameter, an extracted interface), so that the old core and the new
  core both use it. The behaviour of the old core does not change (the prescan runs it).
* ADAPT: copy the algorithm into a new class and change it. The row says what changes.
* PORT: copy a class of the branch `origin/saloed/backward-main` into a new class.
* SPLIT: divide today's class into new classes.
* REPLACE: a new class does the work; the new core does not use today's class.
* REMOVE, NOT USED: the new core does not use today's class, and no new class does its work.

| Today (path:line) | Kind | New |
|---|---|---|
| `AccessPathBase` (`ap/ifds/Accessors.kt:5`) | GENERALIZE | `+ data object Zero`. Each exhaustive `when` over `AccessPathBase` gets `Zero -> error("zero base")`: `ap/ifds/AccessPathBaseStorage.kt`, `ap/ifds/serialization/AccessPathBaseSerializer.kt`, `core/opentaint-dataflow-core/opentaint-jvm-dataflow/.../JIRCallPositionResolver.kt`, `.../JIRMethodCallFactMapper.kt`, `core/src/main/kotlin/.../sarif/TraceMessageBuilder.kt`, `.../spring/SpringRuleProvider.kt` (a `when` with `else` does not change). The old core never makes `Zero`, so its behaviour does not change. |
| `FieldAccessor`, `ElementAccessor`, `ClassStaticAccessor` (`ap/ifds/Accessors.kt:96`, `:125`, `:148`) | REUSE | the values of `AccessorTable`; `TypeFilter` reads them |
| `TypeInfoAccessor`, `TypeInfoGroupAccessor`, `ValueAccessor`, `TaintMarkAccessor`, `FinalAccessor`, `AnyAccessor` (`ap/ifds/Accessors.kt`) | REMOVE, NOT USED | the prescan only (type info), the tail and the mark of a fact (`ap.md` §1, W5) |
| `AccessorIdx`, the bit layout and its tests (`ap/ifds/access/util/AccessorInterner.kt:17`, `:94-137`) | REUSE | path elements; `isFieldAccessor`, `ELEMENT_ACCESSOR_IDX` (counted), `isStaticAccessor` (class) |
| `AccessorInterner.AccessorStorage` (`ap/ifds/access/util/AccessorInterner.kt:20-38`) on `ConcurrentReadSafeObject2IntMap` | ADAPT | `AccessorTable`, `MarkTable`: `ConcurrentHashMap` and a volatile copy-on-write array (§3.1); only the field and the class storages |
| `ConcurrentReadSafeObject2IntMap`, `ConcurrentReadSafeInt2ObjectMap` (`src/main/java/.../util/`) | REPLACE | not used by the new core (§3.1) |
| `ExclusionSet` (`ap/ifds/ExclusionSet.kt:6`) | REPLACE | `bidi.ap.ExclusionSet`: no `Universe`, `IntArray` of `AccessorIdx` |
| `AccessPath.AccessNode` (`ap/ifds/access/tree/AccessPath.kt:261`) | ADAPT | `PathNode`: interned, no manager field, no `addParent` collapse (`:316`, `limitFieldAccess` `:375`, `limitElementAccess` `:354`) |
| `AccessPath` (`ap/ifds/access/tree/AccessPath.kt:27`) | REPLACE | `InitialAp` (also the premise key of one member, DD13) |
| `AccessTree.AccessNode` (`ap/ifds/access/tree/AccessTree.kt:261`) | ADAPT | `TrieNode<P>` (DD14): one generic node; the leaf payload `P` replaces `isAbstract`/`isFinal`/`deepAccessorExclusion`; no `[any]` edge, no `$` child. DROPPED: the other depth bounds of today's node, `limitFieldAccess` (the repeated-field fold, `:1468`, through `addParentFieldAccess`, `:625`), `limitElementAccess` with `SUBSEQUENT_ARRAY_ELEMENTS_LIMIT = 2` (`:599`, `:1635`) and the `containsStatic` guard (`:273`, `:436`). The new AP has only the field limit `L` (`ap.md` §4.4; §4.2, §5.7), as for `PathNode` |
| `mergeAdd`, `mergeAddDelta`, `mergeAddStep`, `mergeAddDeltaStep`, `AccessNodeMergePair`, `mergeNodeLoop`, `pushSharedChildPairs`, `mergeAccessorsRaw`, `transformAccessors`, `removeSingleAccessor`, `trimModifiedAccessors` (`AccessTree.kt:815`, `:854`, `:826`, `:859`, `:803`, `:912`, `:1005`, `:1388`, `:1671`, `:1638`, `:1712`) | ADAPT | `TrieOps.kt` (§4.2), generic over `LeafAlgebra<P>`: the leaf union and the leaf delta replace the flags; the parameter `foldToAny` (`:815`, `:854`) and `trimAnyCoveredAndPushChildren` (`:965`) go |
| `TreeApManager.create`, `createElementAndField` (`AccessTree.kt:1784`, `:1806`) | ADAPT | `LeafAlgebra.node`: one shared leaf node per payload |
| `annotateAbstractNodes(cache)` (`AccessTree.kt:735`) | ADAPT | the identity memo of every node map (`mapLeaves`) |
| `filterAccessNode(FactApFilter)` (`AccessTree.kt:1031`) | ADAPT | `filterPath` (§5.5), generic: no `[any]` edge, no `FinalAccessor` check. A mark is a leaf payload, not an accessor, so the mark check of the filter below `[e]` (`:1039-1045` with `FilterNext`) becomes the `[e]` chain walk of the mark policy (`markPolicyOnElements`, §5.5), as today |
| `concatToLeafAbstractNodes` (`AccessTree.kt:1114`, `:1281-1326`) | ADAPT | `graft` (§4.2) in `applySummary` (§5.4), generic over the kinds of the summary and of the result; it MERGES at nested occurrences as today (`:1321-1324`). DROPPED: `filterTypes` (`:1290`; no summary-side filter, `ap.md` §4.8), `limitElementAccess` (`:1291`), `filterDeepExclusion` (`:1292`), `limitFieldAccess` (`:1299`) and the `isFinal` leaf of the summary (`:1321`): the field limit `L` is the only depth bound |
| `filterStartsWith` (`AccessTree.kt:1329`) | ADAPT | the run-1 part of `satisfying` (§5.4) |
| `internNodes` (`AccessTree.kt:1125-1172`), `markInterned` (`:1174`), `AccessTreeInterner` (`ap/ifds/access/tree/AccessTreeInterner.kt:8`) | ADAPT | `TrieInterner<P>.internBottomUp` and its `InternStrategy` (§4.5): the copy with `interned = true`, the children by identity |
| `AccessTreeSoftInterner` (`ap/ifds/access/tree/AccessTreeSoftInterner.kt:7`) | ADAPT | the soft table of `TrieInterner` (§4.5): `refs.createRef(table)` (`:18-23`), re-created on demand |
| `TreeSetWithCompression.internIfRequired`, `intern(idx)` (`ap/ifds/access/tree/TreeSetWithCompression.kt:14`, `:19`) | ADAPT | `TrieInterner.internIfRequired` (§4.5): the force size and the rate stay; the rate tick interns the trie of the add, not every trie of the store (a deliberate difference, §4.5) |
| `RefManager`, `SoftReferenceManager` (`util/RefManager.kt:6`, `util/SoftReferenceManager.kt:8`) | REUSE | `ApManager.softRefs = refManager.softRefManager("bidi")`: the memory guard clears every trie table (DD5) |
| `boundedDepthRaw`, `FieldLimiter.limit` (branch `saloed/any-field-limit`, `ap/ifds/access/tree/AccessTree.kt:298`, `:722`) | ADAPT | `TrieNode.boundedDepth`, `FieldLimitCut` (§5.7): generic; no `[any]` edge to keep |
| `TreeApManager.isCounted` (branch, `ap/ifds/access/tree/TreeApManager.kt:79`) | ADAPT | `AccessorIdx.isCounted()`: field or element, no unroll strategy (`ap.md` §1) |
| `TreeFieldLimitCheck` (branch) | ADAPT | the W3 assert of `MethodEdgeStore.add` (§7.3) and the test `FieldLimitTest` (§8) |
| `TreeApManager` (`ap/ifds/access/tree/TreeApManager.kt:34`), `ap/ifds/access/ApManager.kt` | REPLACE | `bidi.ap.ApManager` (§5.1) |
| `AnyAccessorUnrollStrategy`, `AccessTreeAnySuffixMatcher`, `DeepAccessorExclusion` (`ap/ifds/access/DeepAccessorExclusion.kt:6`), `TreeInitialFactAbstraction`, mark/`$`/`[any]` accessors in paths | REPLACE | the any tails (`[any]`, and `[any-taint]` with its exclusion for the target of a source, DD16), the mark exclusion `*∖X` of a FLOW tree, the policy and the emission (§5.9; `ap.md` §6.2, §6.3, §7.6) |
| `AccessBasedStorage` (`ap/ifds/access/tree/AccessBasedStorage.kt:12`) | ADAPT | `PathTrie` (§7.2): keyed by `IntArray`, plain fastutil maps, `walkPath`, `lookupPrefixes`/`lookupExtensions`/`around` |
| `MethodAnalyzerEdges` (`ap/ifds/MethodAnalyzerEdges.kt:13`): `SameInitialZeroFactEdges` (`:150`), `instructionStorageIdx` (`:272`) | REUSE the structure | `MethodEdgeStore` (§7.3): the REACH bit sets per statement (now per premise key and layer), the statement index. The premise maps are `Reference2ObjectOpenHashMap`s, and the bases are keys inside `ConclusionGroup` (§4.3): `AccessPathBaseStorage` is NOT USED by the new core, because it rejects `Zero` (the row above) |
| `MethodAnalyzerEdges.EdgeStorage`, `CommonF2FSet` (`ap/ifds/MethodAnalyzerEdges.kt:240`, `ap/ifds/access/common/CommonF2FSet.kt:12`) | REPLACE | `ConclusionGroup` (§4.3): one premise key, every base and kind |
| `EdgeNonUniverseExclusionMergingStorage` (`ap/ifds/access/tree/MethodEdgesInitialToFinalTreeApSet.kt:75`) | REPLACE | `FlowGroup`, `TaintGroup` (§4.3): the union of `:95` is forbidden by T3 |
| `MethodInitialToFinalApSummaries` (`ap/ifds/access/tree/MethodInitialToFinalApSummaries.kt:13`) | REPLACE | `RunSummaryStore` (§7.6): the union of `:271` is forbidden by T3 |
| `MethodTreeAccessPathSubscription`, `AccessTreeIndex` (`ap/ifds/access/tree/MethodTreeAccessPathSubscription.kt:22`, `:199`) | REPLACE | `PathTrie` (the delivery-side miss of `analyzer-core.md` Appendix A P4 goes) |
| `FactTypeChecker.FactApFilter`, `FilterResult` (`ap/ifds/FactTypeChecker.kt:27`, `:12`) | REUSE | `TypeFilter.may` (§5.5) |
| `TaintSinkTracker` rule assumptions (`ap/ifds/taint/TaintSinkTracker.kt:173-240`) and vulnerability nodes (`:15`) | REPLACE | `KaryJoin`, `ConjunctionStore` (§7.10), `VulnerabilityStore` (§7.12) |
| `Edge` (`ZeroToZero`, `ZeroToFact`, `FactToFact`, `NDFactToFact`) | REPLACE | `PremiseKey` + `Facts` (DD3, DD12) |
| `Cancellation` (`util/Cancellation.kt:5`) | REUSE | `ApManager.cancellation.checkpoint()` in long walks |
| `LanguageManager.getInstIndex`, `getMaxInstIndex` (`ap/ifds/LanguageManager.kt:9`) | REUSE | the statement index of `MethodEdgeStore` |
| `CommonTaintConfigurationSink` (`configuration-rules-common`) | REUSE | `VulnerabilityKey.rule` |

---

## 3. The fact model (`ap.md` §2, §3.4, §7.1)

### 3.1 Accessors and marks

```kotlin
package org.opentaint.dataflow.bidi.ap

/** AccessorIdx <-> Accessor. ADAPT of AccessorInterner (ap/ifds/access/util/AccessorInterner.kt:19): the same index layout
 *  (REUSE of its companion, :94-137), a new storage. Today AccessorStorage.index reads a ConcurrentReadSafeObject2IntMap
 *  without a lock (:24-26). Its getInt re-reads non-volatile fields in a `while (true)` loop
 *  (ConcurrentReadSafeObject2IntMap.java:27-33). The JIT can hoist the reads, and the loop then never ends
 *  (analyzer-core.md Appendix A). AccessorTable uses a ConcurrentHashMap for value -> id and a volatile copy-on-write array
 *  for id -> value. A read has no retry loop. */
class AccessorTable {
    internal class Storage<T : Any> {
        private val ids = ConcurrentHashMap<T, Int>()
        @Volatile private var values: Array<Any?> = arrayOfNulls(64)
        private var size = 0                                                      // guarded by `this`

        fun index(v: T): Int = ids[v] ?: synchronized(this) {
            ids[v] ?: run {
                val i = size++
                val arr = if (i < values.size) values else values.copyOf(2 * values.size)
                arr[i] = v
                values = arr                                                      // the volatile write publishes arr[i]
                ids[v] = i                                                        // a reader gets i only after the publication
                i
            }
        }

        @Suppress("UNCHECKED_CAST")
        fun value(i: Int): T = values[i] as T                                     // one volatile read
    }

    private val fields = Storage<Accessor>()
    private val statics = Storage<Accessor>()

    /** ap.md §1, W5: a path holds fields, elements and class accessors only. The type-info and value accessors serve the
     *  lambda analysis of the prescan (the old core); no statement and no rule of the new core makes them. */
    fun index(a: Accessor): AccessorIdx = when (a) {
        is FieldAccessor -> setAccessorKind(fields.index(a), FIELD_KIND, BASIC_KIND_BITS)
        is ClassStaticAccessor -> setAccessorKind(statics.index(a), STATIC_KIND, BASIC_KIND_BITS)
        ElementAccessor -> ELEMENT_ACCESSOR_IDX
        else -> error("W5: $a is not an accessor of a path")          // a mark, `$`, an any tail, type info, value
    }

    fun accessor(i: AccessorIdx): Accessor = when {                  // the decode of AccessorInterner.accessor (:70-92)
        i.isFieldAccessor() -> fields.value(i.getAccessorIdx(BASIC_KIND_BITS))
        i.isStaticAccessor() -> statics.value(i.getAccessorIdx(BASIC_KIND_BITS))
        i == ELEMENT_ACCESSOR_IDX -> ElementAccessor
        else -> error("W5: not a path accessor: $i")
    }
}

/** ap.md §1: a counted accessor is a field or an element accessor. The class accessor `<C>` is not counted. */
fun AccessorIdx.isCounted(): Boolean = isFieldAccessor() || this == ELEMENT_ACCESSOR_IDX

/** ap.md §4.1 `rootOrClass`: the class accessor `<C>` (`ClassStaticAccessor`). */
fun AccessorIdx.isClass(): Boolean = isStaticAccessor()

/** DD7: a concrete mark. The id is the index of its name in MarkTable. */
@JvmInline
value class TaintMark(val id: Int) {
    companion object { val ZERO = TaintMark(0) }                                 // ap.md §2.4: the zero mark; no rule names it
}

class MarkTable {
    private val names = AccessorTable.Storage<String>().also { check(it.index("<zero>") == 0) }
    fun mark(name: String): TaintMark = TaintMark(names.index(name))            // the JVM glue: marks.mark(jvmMark.name)
    fun name(m: TaintMark): String = names.value(m.id)
}
```

### 3.2 Tails, layers, marks, exclusions

```kotlin
enum class Direction { FORWARD, BACKWARD }                                        // ap.md §8.7
enum class Layer { NORMAL, DEMAND }                                               // ap.md §2.2
/** ap.md §2.1, §3.4: `*`, `[any]` (a may), `[any-taint]` (a must, forward only; W8: a concrete mark, and an exclusion E of
 *  first accessors), `$`. */
enum class Tail { STAR, ANY, ANY_TAINT, EXACT }

/** ap.md §3.4: an any tail. `[any]` and `[any-taint]` have one location set (§3.1), less the excluded part of an
 *  `[any-taint]/E`; the layer gives the meaning (K7). */
val Tail.isAny: Boolean get() = this == Tail.ANY || this == Tail.ANY_TAINT

/** ap.md §2.2: a demand input or a demand step gives a demand result; the layer never goes back to normal. */
infix fun Layer.or(demand: Boolean): Layer = if (demand) Layer.DEMAND else this

/** The constants of one run that the AP operations read (ap.md §4.1, §4.4, §6.1). */
class ApMode(val run1: Boolean, val direction: Direction, val fieldLimit: Int) {
    val restricted: Boolean get() = !run1
    init { require(!run1 || (direction == Direction.FORWARD && fieldLimit >= 1)) }   // run 1 is forward; S12 (d): L >= 1
}

/** ap.md §3.4: a canonical set of concrete marks (sorted ids, no duplicates): the X of `*∖X`, the marks of a TAINT leaf.
 *  ApManager interns it (T6), so equal sets are mostly the same object; equals also compares the content, so a reference
 *  form can make a set without the manager. */
class MarkSet(@JvmField val ids: IntArray) {
    private val hash = ids.contentHashCode()
    val isEmpty: Boolean get() = ids.isEmpty()
    operator fun contains(m: TaintMark): Boolean = ids.binarySearch(m.id) >= 0
    fun isSubsetOf(o: MarkSet): Boolean = this === o || sortedSubset(ids, o.ids)
    operator fun plus(o: MarkSet): MarkSet = if (o.isSubsetOf(this)) this else MarkSet(sortedUnion(ids, o.ids))
    operator fun plus(m: TaintMark): MarkSet = if (m in this) this else MarkSet(sortedUnion(ids, intArrayOf(m.id)))
    operator fun minus(o: MarkSet): MarkSet = MarkSet(ids.filter { it !in o.ids }.toIntArray())   // canonical stays canonical
    fun intersect(o: MarkSet): MarkSet = MarkSet(ids.filter { o.ids.binarySearch(it) >= 0 }.toIntArray())
    override fun equals(other: Any?) = this === other || (other is MarkSet && hash == other.hash && ids.contentEquals(other.ids))
    override fun hashCode() = hash
    companion object { val EMPTY = MarkSet(IntArray(0)) }
}

/** ap.md §3.4. `*` is Star(MarkSet.EMPTY). A premise has `*` or a concrete mark (§2.2). A demand entry pattern has
 *  no mark exclusions after either hand-off. Added facts and conclusions can retain `*∖X`;
 *  generic mark tests read its exclusion exactly (Part I §5.9). */
sealed interface MarkSlot {
    data class Star(val excluded: MarkSet) : MarkSlot                            // `*` or `*∖X`
    data class Concrete(val mark: TaintMark) : MarkSlot                           // `T`; the zero mark is concrete
    companion object { val STAR = Star(MarkSet.EMPTY) }
}

/** ap.md §3.4: the exclusion E. No Universe (§1, S8). Concrete is never empty. REPLACE of ap/ifds/ExclusionSet.kt. */
sealed interface ExclusionSet {
    fun admits(first: AccessorIdx): Boolean                                       // r = [first, ...]: the first accessor is not in E
    fun admits(r: List<AccessorIdx>): Boolean = r.isEmpty() || admits(r[0])
    fun union(o: ExclusionSet): ExclusionSet                                      // along one derivation only (§3.3)
    fun intersect(o: ExclusionSet): ExclusionSet                                  // merge rule 2 (§3.3)
    fun isSubsetOf(o: ExclusionSet): Boolean

    data object Empty : ExclusionSet {
        override fun admits(first: AccessorIdx) = true
        override fun union(o: ExclusionSet) = o
        override fun intersect(o: ExclusionSet) = Empty
        override fun isSubsetOf(o: ExclusionSet) = true
    }

    class Concrete internal constructor(@JvmField val ids: IntArray) : ExclusionSet {   // sorted AccessorIdx
        private val hash = ids.contentHashCode()
        override fun admits(first: AccessorIdx) = ids.binarySearch(first) < 0
        override fun union(o: ExclusionSet) = if (o is Concrete) of(sortedUnion(ids, o.ids)) else this
        override fun intersect(o: ExclusionSet) = if (o is Concrete) of(ids.filter { o.ids.binarySearch(it) >= 0 }.toIntArray()) else Empty
        override fun isSubsetOf(o: ExclusionSet) = o is Concrete && sortedSubset(ids, o.ids)
        override fun equals(other: Any?) = this === other || (other is Concrete && hash == other.hash && ids.contentEquals(other.ids))
        override fun hashCode() = hash
    }

    companion object {
        /** The only factories: an empty set is Empty (ap.md §3.4). ApManager.intern makes it the shared object. */
        fun of(ids: IntArray): ExclusionSet = if (ids.isEmpty()) Empty else Concrete(ids.sortedDistinct())
        /** `{a}`: the exclusion of one keep edge (interpreter.md §2.1 `strongKeep`). */
        fun of(a: AccessorIdx): ExclusionSet = Concrete(intArrayOf(a))
    }
}

// Facts.kt helpers: `fun IntArray.sortedDistinct(): IntArray`; `fun sortedUnion(a: IntArray, b: IntArray): IntArray` and
// `fun sortedSubset(a: IntArray, b: IntArray): Boolean` (a merge walk of two sorted arrays); `fun IntArray.startsWith(p:
// IntArray): Boolean` (the `startsWith` of ap.md §3.4 on arrays); `val EMPTY_PATH = IntArray(0)`.
```

### 3.3 `PathNode`

```kotlin
/** ap.md §7.1: the concrete path of a premise, a position or a demand chain, linked from the root. No `[any]`,
 *  `[any-taint]`, `$` or mark accessor (W4, W5). ADAPT of AccessPath.AccessNode (ap/ifds/access/tree/AccessPath.kt:261): the same (accessor, next)
 *  cell with a cached hash and size. Changes: no `manager` field; ApManager.path interns every node, so equality is
 *  identity; no `addParent`. The old `addParent` (:316) collapses a repeated field (`limitFieldAccess`, :375) and long
 *  element chains (`limitElementAccess`, :354). These are depth bounds; the field limit must be the only one (ap.md §4.4). */
class PathNode internal constructor(@JvmField val accessor: AccessorIdx, @JvmField val next: PathNode?) {
    @JvmField val size: Int = 1 + (next?.size ?: 0)
    @JvmField val counted: Int = (if (accessor.isCounted()) 1 else 0) + (next?.counted ?: 0)  // W3 checks
    private val hash: Int = accessor + 17 * (next?.hashCode() ?: 0)

    /** Shallow: `next` is interned. The interner of ApManager uses it; after interning it is identity. */
    override fun equals(other: Any?) = this === other || (other is PathNode && accessor == other.accessor && next === other.next)
    override fun hashCode() = hash

    fun toIntArray(): IntArray = IntArray(size).also { var n: PathNode? = this; var i = 0; while (n != null) { it[i++] = n.accessor; n = n.next } }
    fun toList(): List<AccessorIdx> = toIntArray().asList()
}
```

### 3.4 `PremiseKey`, `InitialAp`, `PremiseSet`, the zero fact

```kotlin
/** ap.md §7.1: the premise SET of an edge, never empty, interned (equal keys are one object; DD13). One member is the
 *  common case: the InitialAp itself is the key, with no wrapper and no list. Two or more members: a PremiseSet, an ND
 *  edge (no zero member, ap.md §4.6). The layer is not part of the key. The tail and the exclusion of each member are
 *  part of the key: the must-premise `(x, p, [any-taint], E, T)` (one key per E) and the `[any]` premise
 *  `(x, p, [any], T)` are different keys, with their own edges, summaries and publications (ap.md §7.1; Lean the must
 *  flag and the premise exclusion of AnyTaintEx.XObj). */
sealed interface PremiseKey {
    val size: Int
    fun member(k: Int): InitialAp
    val isZero: Boolean                        // the one member is the zero fact
    val nonZeroCount: Int                      // ap.md §4.6: 0 = zero-to-fact ({zero}), 1 = fact-to-fact, >= 2 = ND edge
}

val PremiseKey.members: List<InitialAp> get() = List(size, ::member)          // on a PremiseSet receiver: its array
inline fun PremiseKey.forEachMember(body: (InitialAp) -> Unit) { for (k in 0 until size) body(member(k)) }

/** K6, ap.md §7.2: the conclusions of a premise with the mark `*` (a policy fact, a position answer) are FLOW. Every other
 *  InitialAp gives REACH (a conclusion on the zero base) or TAINT; a PremiseSet gives TAINT only (ap.md §4.6). */
val PremiseKey.isFlow: Boolean get() = this is InitialAp && mark is MarkSlot.Star

/** ap.md §7.1: the premise; also the premise key of one member. ApManager.initial interns it: equality is identity (DD5). */
class InitialAp internal constructor(
    val base: AccessPathBase,
    val path: PathNode?,
    val tail: Tail,                               // ANY_TAINT: a must-premise, only in a forward restricted run (W8 (c), §6.5)
    val exclusion: ExclusionSet,                  // Empty unless STAR or ANY_TAINT: an emission can give a `*/E` demand exclusion
                                                  // and a must-premise `[any-taint]/E` (§6.3; Lean AnyTaintEx.emitX)
    val mark: MarkSlot,                           // `*` or `T`, never `*∖X` (§2.2)
    @JvmField val id: Int,                        // the intern order: the sort key of a PremiseSet
    @JvmField internal val manager: ApManager,    // native premise factories; reversed record readers use Pattern (§7.8)
) : PremiseKey {
    init {
        check(mark !is MarkSlot.Star || mark.excluded.isEmpty)
        check(tail == Tail.STAR || tail == Tail.ANY_TAINT || exclusion == ExclusionSet.Empty)
        check(tail != Tail.EXACT || mark is MarkSlot.Concrete) { "S8: an exact premise has a concrete mark" }
        check(tail != Tail.ANY_TAINT || mark is MarkSlot.Concrete) { "W8 (a): `[any-taint]` has a concrete mark" }
    }
    @JvmField val pathArray: IntArray = path?.toIntArray() ?: EMPTY_PATH
    override val size: Int get() = 1
    override fun member(k: Int): InitialAp = also { check(k == 0) }
    override val isZero: Boolean get() = base == AccessPathBase.Zero
    override val nonZeroCount: Int get() = if (isZero) 0 else 1
    fun toPattern(): Pattern = Pattern(PathFact(base, pathArray.asList(), tail, mark), exclusion)
}

/** ap.md §7.1, §4.6: an ND edge. Two or more members, sorted by the intern id, no duplicates; no member is the zero fact
 *  (the union drops it) and every member has a concrete mark (an input of a conjunction passes the mark gate of its
 *  literal; a member of an E6 summary is satisfied only by a concrete added fact). ApManager.premiseOf interns it. */
class PremiseSet internal constructor(@JvmField val members: Array<InitialAp>) : PremiseKey {
    init {
        check(members.size >= 2)
        check(members.none { it.isZero }) { "ap.md §4.6: a premise set with two or more members has no zero member" }
        check(members.all { it.mark is MarkSlot.Concrete }) { "ap.md §4.6: every member of an ND edge is concrete" }
    }
    override val size: Int get() = members.size
    override fun member(k: Int): InitialAp = members[k]
    override val isZero: Boolean get() = false
    override val nonZeroCount: Int get() = members.size
}

// In ApManager (Part I §5.1):
//   fun premiseOf(members: Collection<InitialAp>): PremiseKey   drops the zero fact (only zero: {zero}); one member: the
//                                                                InitialAp itself; else the interned PremiseSet
//   fun union(a: PremiseKey, b: PremiseKey): PremiseKey          ap.md §4.6: the union of two premise sets, without zero
//   val zero: InitialAp = initial(AccessPathBase.Zero, null, Tail.EXACT, ExclusionSet.Empty, MarkSlot.Concrete(TaintMark.ZERO))
//                                                                the zero fact, also the premise key {zero} (ap.md §2.4)
```

---

## 4. Tries and conclusions (`ap.md` §7.2, §7.5, §8.1)

### 4.1 `TrieNode<P>`, the leaf algebras, the three kinds

```kotlin
/** The leaf payload of a trie node (DD14). `hasAny`: the payload has an any leaf with a concrete mark (TAINT): an
 *  `[any-taint]` leaf in a normal tree, an `[any]` leaf in a demand tree (DD16). */
interface TrieLeaf { val isEmpty: Boolean; val hasAny: Boolean }

/** FLOW: one flag per node. Normal means `p.*/E`; demand means `p.[any]`.
 *  The tree mark is `*∖X`. Canonical construction guards (§5.9) exclude abstract exact
 *  and must tails; generic reference reduction keeps those forms outside Facts. */
enum class FlowLeaf : TrieLeaf {
    NONE, LEAF;
    override val isEmpty: Boolean get() = this == NONE
    override val hasAny: Boolean get() = false                // the layer of the tree says `[any]`, not the leaf
}

/** TAINT: the concrete marks of the `$` leaves and of the any leaves at one node. `any` holds the `[any-taint]` marks in a
 *  NORMAL tree (W8) and the `[any]` marks in a DEMAND tree (W6): the layer of the tree names them (ap.md §7.2 TaintTree,
 *  DD16), so a node has no field per any tail. The exclusion of the `[any-taint]` leaves is a field of the tree
 *  (`TaintTree.exclusion`), not of a node. Interned by TaintAlgebra (T6): TaintLeaves.EMPTY is the only empty object. */
class TaintLeaves internal constructor(@JvmField val exact: MarkSet, @JvmField val any: MarkSet) : TrieLeaf {
    private val hash = exact.hashCode() * 31 + any.hashCode()
    override val isEmpty: Boolean get() = exact.isEmpty && any.isEmpty
    override val hasAny: Boolean get() = !any.isEmpty
    override fun equals(other: Any?) = this === other || (other is TaintLeaves && hash == other.hash && exact == other.exact && any == other.any)
    override fun hashCode() = hash
    companion object { @JvmField val EMPTY = TaintLeaves(MarkSet.EMPTY, MarkSet.EMPTY) }
}

/** The ONE trie node (DD14). ADAPT of AccessTree.AccessNode (ap/ifds/access/tree/AccessTree.kt:261): the leaf payload
 *  replaces isAbstract, isFinal and deepAccessorExclusion; no `[any]` edge, no `$` child. Immutable. Every child subtree
 *  is non-empty. */
class TrieNode<P : TrieLeaf> internal constructor(
    @JvmField val leaf: P,
    @JvmField val accessors: IntArray?,          // sorted AccessorIdx, as today
    @JvmField val children: Array<TrieNode<P>>?,
    @JvmField val interned: Boolean,
) {
    @JvmField val hash: Long                     // structural (AccessTree.kt:281), with the child accessor
    @JvmField val boundedDepth: Int              // exact max counted accessors on a path below (Part I §4.5); not in the hash
    @JvmField val hasAny: Boolean                // an any mark at or below (TAINT): the T5 trigger (Part I §4.3), the normal form of
                                                 // TaintTree.exclusion (below), the backward W6 assert of persist (§7.8)
    @JvmField val size: Long                     // nodes, shared subtrees counted again (AccessTree.kt:316-322): the intern policy
    @JvmField internal var allLeaves: P? = null  // the lazy foldAll: the leaf of this node and every leaf below it; a benign race

    init {
        var h = leaf.hashCode().toLong()
        var depth = 0
        var any = leaf.hasAny
        var count = 1L
        if (children != null) for (i in children.indices) {
            val c = children[i]
            count += c.size
            h += (c.hash * 31 + accessors!![i]) shl 5
            val d = Math.addExact(c.boundedDepth, if (accessors[i].isCounted()) 1 else 0)
            if (d > depth) depth = d
            any = any || c.hasAny
        }
        hash = h
        boundedDepth = depth                      // exact Int; checked addition rejects an unrepresentable depth
        hasAny = any; size = count
    }

    val isEmpty: Boolean get() = leaf.isEmpty && accessors == null
    fun child(a: AccessorIdx): TrieNode<P>? =                          // getNodeByAccessor (AccessTree.kt:385)
        accessors?.binarySearch(a)?.let { if (it >= 0) children!![it] else null }
    override fun hashCode() = hash.toInt()
    override fun equals(other: Any?): Boolean =                        // AccessNode.equals (:326); see Part I §4.5
        this === other || (other is TrieNode<*> && hash == other.hash && leaf == other.leaf &&
            accessors.contentEquals(other.accessors) && children.contentEquals(other.children))
}

typealias FlowNode = TrieNode<FlowLeaf>
typealias TaintNode = TrieNode<TaintLeaves>

/** The algebra of one leaf payload (DD14). The generic algorithms of TrieOps.kt read only these members. */
abstract class LeafAlgebra<P : TrieLeaf> {
    abstract val empty: P
    abstract fun union(a: P, b: P): P
    abstract fun minus(a: P, b: P): P                     // the leaves of a that b does not have
    /** ap.md §8.1, T5: what a leaf `p` covers strictly below its node inside one DEMAND tree: an `[any]` leaf covers
     *  every leaf with its mark. A FLOW flag absorbs only in a demand tree. In a NORMAL TAINT tree the groups use
     *  `TaintAlgebra.belowNormal` (only the `[any-taint]` leaves, through a child accessor that the tree exclusion
     *  admits: the callers pass `admits`, §4.3, §4.4; DD16). */
    abstract fun below(p: P): P
    /** ap.md §4.4: the leaves `p` of a cut subtree as one `[any]` leaf at the cut point (the mark stays). The cut trie is a
     *  demand trie, so an `[any-taint]` leaf that the cut reaches becomes `[any]` and loses its exclusion (W8 (b); Lean
     *  AnyTaintEx.limitFX, the vector AnyTaintEx.Vec.cut_drops; AnyTaintCases.Cut.cut_transfer). */
    abstract fun asAny(p: P): P
    abstract fun intern(p: P): P
    private val leafNodes = ConcurrentHashMap<P, TrieNode<P>>()
    /** One shared leaf node per payload (ADAPT of TreeApManager.create, AccessTree.kt:1784). */
    fun leafNode(p: P): TrieNode<P> = leafNodes.computeIfAbsent(intern(p)) { TrieNode(it, null, null, interned = true) }
    fun node(p: P, accessors: IntArray?, children: Array<TrieNode<P>>?): TrieNode<P> =
        if (accessors == null || accessors.isEmpty()) leafNode(p) else TrieNode(intern(p), accessors, children, interned = false)
}

object FlowAlgebra : LeafAlgebra<FlowLeaf>() {
    override val empty = FlowLeaf.NONE
    override fun union(a: FlowLeaf, b: FlowLeaf) = if (a == FlowLeaf.LEAF || b == FlowLeaf.LEAF) FlowLeaf.LEAF else FlowLeaf.NONE
    override fun minus(a: FlowLeaf, b: FlowLeaf) = if (a == FlowLeaf.LEAF && b == FlowLeaf.NONE) FlowLeaf.LEAF else FlowLeaf.NONE
    override fun below(p: FlowLeaf) = p                    // an `[any]` flag covers every leaf below it (the mark is the same)
    override fun asAny(p: FlowLeaf) = p                    // in the demand tree of the cut, the flag is `[any]`
    override fun intern(p: FlowLeaf) = p
    val LEAF_NODE: FlowNode get() = leafNode(FlowLeaf.LEAF)
}

class TaintAlgebra(private val m: ApManager) : LeafAlgebra<TaintLeaves>() {
    private val table = ConcurrentHashMap<TaintLeaves, TaintLeaves>()
    override val empty = TaintLeaves.EMPTY
    fun leaves(exact: MarkSet, any: MarkSet): TaintLeaves = intern(TaintLeaves(m.intern(exact), m.intern(any)))
    override fun intern(p: TaintLeaves) = if (p.isEmpty) TaintLeaves.EMPTY else table.putIfAbsent(p, p) ?: p
    override fun union(a: TaintLeaves, b: TaintLeaves) = if (a === b) a else leaves(a.exact + b.exact, a.any + b.any)
    override fun minus(a: TaintLeaves, b: TaintLeaves) = leaves(a.exact - b.exact, a.any - b.any)
    override fun below(p: TaintLeaves) = leaves(p.any, p.any)          // DEMAND: an `[any]` leaf with T covers the `$` and `[any]` leaves with T below
    /** NORMAL TAINT tree (DD16; ap-history.md F69): an `[any-taint]` leaf with T covers only the `[any-taint]` leaves
     *  with T below it, NEVER a `$` leaf. A later demoting cleaner row (`exact` at `x.f`, or a cleaner two accessors
     *  below) moves the `[any-taint]` leaf to the demand tree, and a `$` leaf that it does not touch must stay normal:
     *  `{x: [any-taint] (T), x.g: $ (T)}`, then `clean(x.f, exact, T)`: `x.g.$ (T)` stays NORMAL (`sink(x.g)` CONFIRMED). */
    fun belowNormal(p: TaintLeaves) = leaves(MarkSet.EMPTY, p.any)
    /** The cover below a leaf in a tree of `layer`: `below` (demand), `belowNormal` (normal). Users: TaintGroup (§4.3). */
    fun belowIn(layer: Layer): (TaintLeaves) -> TaintLeaves = if (layer == Layer.NORMAL) this::belowNormal else this::below
    override fun asAny(p: TaintLeaves) = leaves(MarkSet.EMPTY, p.exact + p.any)
}
```

The three kinds of `ap.md` §7.2:

```kotlin
/** ap.md §7.2: the conclusions of one edge group at a node. ONE kind (DD12). */
sealed interface Facts { val base: AccessPathBase; val layer: Layer }

/** REACH: the zero fact is at the node (today ZeroToZero). Two objects. */
class Reach private constructor(override val layer: Layer) : Facts {
    override val base: AccessPathBase get() = AccessPathBase.Zero
    companion object {
        @JvmField val NORMAL = Reach(Layer.NORMAL)
        @JvmField val DEMAND = Reach(Layer.DEMAND)
        fun of(layer: Layer): Reach = if (layer == Layer.NORMAL) NORMAL else DEMAND
    }
}

/** FLOW in every run, including a reused record with a `*` premise: the flag of a node is `p.*/E` (normal) or `p.[any]` (demand), with the
 *  mark `*∖X`. W2, W6 and W8 (a) hold by the type (no concrete mark, so no `$` and no `[any-taint]` leaf; the flag of a
 *  demand tree IS `[any]`); `init` checks W1 for the demand layer. ApManager.flowTree is the normalising factory (it maps a demand exclusion to Empty). */
class FlowTree internal constructor(
    override val base: AccessPathBase,
    override val layer: Layer,
    val exclusion: ExclusionSet,
    val markExclusion: MarkSet,
    val root: FlowNode,
) : Facts {
    init { check(layer == Layer.NORMAL || exclusion == ExclusionSet.Empty) { "W1: a demand flag is `[any]`" } }   // O(1)
    internal fun withRoot(r: FlowNode) = FlowTree(base, layer, exclusion, markExclusion, r)
    override fun equals(other: Any?) = this === other || (other is FlowTree && layer == other.layer && base == other.base &&
        exclusion == other.exclusion && markExclusion == other.markExclusion && root == other.root)
    override fun hashCode() = ((root.hashCode() * 31 + base.hashCode()) * 31 + exclusion.hashCode()) * 31 + markExclusion.hashCode() + layer.ordinal
}

/** TAINT: concrete marks only. No `*` leaf and no mark exclusion (by the type). THE LAYER NAMES THE ANY LEAVES
 *  (ap.md §7.2, W8 (b); DD16): in a NORMAL tree an any leaf is `[any-taint]` (a must: every location at or below it, less
 *  the excluded part, carries its mark), in a DEMAND tree it is `[any]`. So a normal tree has no `[any]` leaf and a demand
 *  tree no `[any-taint]` leaf by construction, and `init` has no W6 check: W6 is the rule of the operation that applies a
 *  may `[any]` target (Part I §5.3 `EdgeApplication.put`).
 *  THE EXCLUSION (ap.md W8, §4.1; DD16): ONE exclusion `E` of first accessors for every `[any-taint]` leaf of the tree:
 *  an any leaf at `p` with the mark T says that every location `(base, p ++ τ, T)` with `E admits τ` carries T (Lean
 *  AnyTaintEx.coversFX). The `$` leaves do not read it. Its normal form (as `normX`): Empty in the demand layer (an `[any]`
 *  has no exclusion) and in a tree with no any leaf. It is a part of the group key (as the exclusion of a FlowTree), so the
 *  results of one operation with two exclusions are two trees (Part I §5.2, §4.3). `withRoot` keeps the layer and the
 *  exclusion, so it never renames a leaf; a demotion is a new tree in the demand layer with the same root and no
 *  exclusion (the same fact, ap.md §3.1; Lean AnyTaintEx.recLayerX, w6tX, limitFX). */
class TaintTree internal constructor(
    override val base: AccessPathBase,
    override val layer: Layer,
    val root: TaintNode,
    val exclusion: ExclusionSet = ExclusionSet.Empty,
) : Facts {
    init { check(exclusion == ExclusionSet.Empty || (layer == Layer.NORMAL && root.hasAny)) { "W8: only `[any-taint]` has an exclusion" } }   // O(1)
    internal fun withRoot(r: TaintNode) = TaintTree(base, layer, r, if (r.hasAny) exclusion else ExclusionSet.Empty)
    override fun equals(other: Any?) = this === other || (other is TaintTree && layer == other.layer && base == other.base &&
        exclusion == other.exclusion && root == other.root)
    override fun hashCode() = ((root.hashCode() * 31 + base.hashCode()) * 31 + exclusion.hashCode()) * 2 + layer.ordinal
}
```

```kotlin
/** ap.md §8.1: the store key of a value without the statement and the premise. Two values with the same group key merge
 *  (T1, T4); two values with different group keys never merge (T3). REACH: the layer. FLOW: the base, the layer, the
 *  exclusion and the mark exclusion. TAINT: the base, the layer and the exclusion of the `[any-taint]` leaves (DD16).
 *  Users: AddedFactStore (Part I §7.5), the identity test of a summary (analyzer-impl.md `summaryParts`). */
enum class FactsKind { REACH, FLOW, TAINT }
data class GroupKey(val kind: FactsKind, val layer: Layer, val base: AccessPathBase,
                    val exclusion: ExclusionSet = ExclusionSet.Empty, val markExclusion: MarkSet = MarkSet.EMPTY)

val Facts.groupKey: GroupKey get() = when (this) {
    is Reach -> GroupKey(FactsKind.REACH, layer, base)
    is FlowTree -> GroupKey(FactsKind.FLOW, layer, base, exclusion, markExclusion)
    is TaintTree -> GroupKey(FactsKind.TAINT, layer, base, exclusion)
}
```

Value equality is structural (DD5). The analyzer compares `Facts` by value: the set of the unchanged queue
(`DeltaWorklist`), the inputs of `ConjunctionStore`, the AC4 identity test and the split of a summary delta
(`analyzer-impl.md` §4).

### 4.2 T1 merge, T4 delta and the other generic trie operations

`mergeAdd` and `mergeAddDelta` keep the iterative pair loop of today, without the `[any]` fold. The loop and the two
steps:

```kotlin
// TrieOps.kt. ADAPT of AccessNode.mergeAdd, mergeAddDelta and mergeNodeLoop (AccessTree.kt:815, :854, :912-963). The
// parameter `foldToAny` (:815, :854) and `trimAnyCoveredAndPushChildren` (:965) go (no `[any]` edge): the loop always
// calls `pushSharedChildPairs` (:1005-1029). `mergeAccessors` (:1368) and `mergeAccessorsRaw` (:1388) are copied as they
// are, with the type parameter P: the sorted merge of the children of `a` with (`accessors`, `children`) of `b`; it
// calls `onOtherNode` for a child that only `b` has and `merge` for a shared accessor, and it returns null if `a`
// keeps its children. `getComputedResult` is `get(key) ?: error(...)` (:1851).

/** The key of the pair memo: the identity of both nodes (AccessNodeMergePair, AccessTree.kt:803-813). */
internal class NodePair<P : TrieLeaf>(@JvmField val a: TrieNode<P>, @JvmField val b: TrieNode<P>) {
    private val hash = System.identityHashCode(a) * 31 + System.identityHashCode(b)
    override fun hashCode() = hash
    override fun equals(other: Any?) = other is NodePair<*> && a === other.a && b === other.b
}

internal object Expanding                                                  // NodeExpansionRequested of today

/** The pair loop: the first visit of a pair pushes its shared child pairs; the second visit merges it with `step`, which
 *  reads the results of the child pairs. No recursion, so a wide or deep trie needs no deep call stack. */
@Suppress("UNCHECKED_CAST")
internal inline fun <P : TrieLeaf, T : Any> mergeNodeLoop(a0: TrieNode<P>, b0: TrieNode<P>, same: (TrieNode<P>) -> T,
                                                          step: (TrieNode<P>, TrieNode<P>, Object2ObjectOpenHashMap<NodePair<P>, T>) -> T): T {
    if (a0 === b0) return same(a0)
    val results = Object2ObjectOpenHashMap<NodePair<P>, Any>()
    val stack = ArrayList<NodePair<P>>()
    val initial = NodePair(a0, b0).also { stack += it }
    while (stack.isNotEmpty()) {
        val pair = stack.last()
        if (pair.a === pair.b) { results[pair] = same(pair.a); stack.removeLast(); continue }
        when (results.putIfAbsent(pair, Expanding)) {
            null -> pushSharedChildPairs(pair.a, pair.b, stack)                       // first visit: the child pairs first
            Expanding -> { results[pair] = step(pair.a, pair.b, results as Object2ObjectOpenHashMap<NodePair<P>, T>); stack.removeLast() }
            else -> stack.removeLast()                                                // a shared pair: merged already
        }
    }
    return results[initial] as T
}

internal fun <P : TrieLeaf> pushSharedChildPairs(a: TrieNode<P>, b: TrieNode<P>, stack: MutableList<NodePair<P>>) {
    val x = a.accessors ?: return
    val y = b.accessors ?: return
    var i = 0
    var j = 0
    while (i < x.size && j < y.size) when {
        x[i] < y[j] -> i++
        x[i] > y[j] -> j++
        else -> { stack += NodePair(a.children!![i], b.children!![j]); i++; j++ }
    }
}

fun <P : TrieLeaf> LeafAlgebra<P>.mergeAdd(a: TrieNode<P>, b: TrieNode<P>): TrieNode<P> =
    mergeNodeLoop(a, b, { it }) { x, y, results -> mergeAddStep(x, y, results) }

fun <P : TrieLeaf> LeafAlgebra<P>.mergeAddDelta(a: TrieNode<P>, b: TrieNode<P>): Pair<TrieNode<P>, TrieNode<P>?> =
    mergeNodeLoop(a, b, { it to null }) { x, y, results -> mergeAddDeltaStep(x, y, results) }

/** ADAPT of AccessNode.mergeAddStep (AccessTree.kt:826-852): the leaf union replaces the flags. */
internal fun <P : TrieLeaf> LeafAlgebra<P>.mergeAddStep(
    a: TrieNode<P>, b: TrieNode<P>, results: Object2ObjectOpenHashMap<NodePair<P>, TrieNode<P>>,
): TrieNode<P> {
    val leaf = union(a.leaf, b.leaf)
    val merged = mergeAccessors(a, b.accessors, b.children, onOtherNode = { _, _ -> }) { _, x, y ->
        results.getComputedResult(NodePair(x, y)) }
    if (leaf == a.leaf && merged == null) return a
    return node(leaf, merged?.first ?: a.accessors, merged?.second ?: a.children)
}

/** ADAPT of AccessNode.mergeAddDeltaStep (AccessTree.kt:859-910). */
internal fun <P : TrieLeaf> LeafAlgebra<P>.mergeAddDeltaStep(
    a: TrieNode<P>, b: TrieNode<P>, results: Object2ObjectOpenHashMap<NodePair<P>, Pair<TrieNode<P>, TrieNode<P>?>>,
): Pair<TrieNode<P>, TrieNode<P>?> {
    val leaf = union(a.leaf, b.leaf)                       // was: isAbstract ||, isFinal ||, intersectDeepExclusion
    val leafDelta = minus(b.leaf, a.leaf)                  // was: isAbstractDelta, isFinalDelta, deltaDeepExclusion
    val deltaAccessors = IntArrayList()
    val deltaNodes = ArrayList<TrieNode<P>>()
    val merged = mergeAccessors(a, b.accessors, b.children,
        onOtherNode = { acc, n -> deltaAccessors.add(acc); deltaNodes.add(n) }) { acc, x, y ->
        val (node, delta) = results.getComputedResult(NodePair(x, y))
        if (delta != null) { deltaAccessors.add(acc); deltaNodes.add(delta) }
        node
    }
    if (leaf == a.leaf && merged == null) return a to null
    val delta = node(leafDelta, deltaAccessors.toIntArray(), deltaNodes.toTypedArray()).takeIf { !it.isEmpty }
    return node(leaf, merged?.first ?: a.accessors, merged?.second ?: a.children) to delta
}
```

T1 (`Tree.rule1_mem`): two values with the same key merge by `mergeAdd`. T4: `mergeAddDelta` returns the new part only.
The same rule on two `Facts` of one group key (§4.1), for the stores that merge exactly (no subsumption):

```kotlin
/** T1, T4 on two values of one group key: (the merged value, the delta or null). Users: AddedFactStore.add (Part I §7.5),
 *  ConcurrentVulnerabilityStore.union (Part I §7.12). */
internal fun ApManager.mergeAddDelta(old: Facts, new: Facts): Pair<Facts, Facts?> {
    check(old.groupKey == new.groupKey)                                     // T3: never across keys
    return when (old) {
        is Reach -> old to null                                             // one bit
        is FlowTree -> FlowAlgebra.mergeAddDelta(old.root, (new as FlowTree).root).let { (n, d) -> old.withRoot(n) to d?.let(old::withRoot) }
        is TaintTree -> taintAlg.mergeAddDelta(old.root, (new as TaintTree).root).let { (n, d) -> old.withRoot(n) to d?.let(old::withRoot) }
    }
}
```

The other generic functions of `TrieOps.kt` (extension functions on `LeafAlgebra<P>`; each returns `null` for an empty
node and shares every child that it does not change; a map over a whole subtree has an identity memo for one call, as
`annotateAbstractNodes`, `AccessTree.kt:735`):

| Function | Result | Users | Code |
|---|---|---|---|
| `mergeAdd(a, b)`, `mergeAddDelta(a, b)` | T1, T4 | the groups (§4.3), `Results` (§5.2), `AddedFactStore` (§7.5), `VulnerabilityStore` (§7.12) | above |
| `mapChildren(n, leaf, f)` | `n` with a new leaf and each child `c` at `a` replaced by `f(a, c)` (ADAPT of `transformAccessors`, `AccessTree.kt:1461`) | every map below | below |
| `replaceChild(n, leaf, a, c)`, `withLeaf(n, leaf)` | one child replaced (a null `c` removes it); the leaf of `n` replaced (`n` may be null: a leaf node) | `clean`, `satisfying`, `restrict`, the mark policy | below |
| `mapLeaves(n, f)` | every leaf of the subtree replaced by `f(leaf)` | the gate on a subtree, the mark filter of `satisfying`, `clean`, the mark test of `restrict` below `D-p` (F71) | below |
| `retainChildren(n, pred)` | a node with the empty leaf and only the children whose accessor `pred` accepts | `applyCompiledEdge`, `satisfying`, `restrict`, `clean` | below |
| `foldAll(n)`, `foldLeaves(n, pred)` | `foldAll(n)`: the union of the leaf of `n` and of every leaf in the subtree of `n` (AT OR BELOW `n`; cached in `TrieNode.allLeaves`). `foldLeaves(n, pred)`: the leaf of `n` and `foldAll(c)` of each child `c` whose accessor `pred` accepts | the fold to one any leaf (`[any]`, `[any-taint]`) or `$` leaf (`applyCompiledEdge`), the cut (`foldAll` of the cut child: its own leaf is beyond `L` too), the leaf kinds of a summary (`foldAll(g.root)`: a root leaf `ret.$ (T)` is a kind) | below |
| `prepend(m, path, n)`, `chain(m, path, leaves, tip = null)` | `path ++ n` (`Tree.prependPath`); one leaf per depth on one path and the node `tip` at its end, no other child | every operation that makes a result at a path | below |
| `minusNode(n, k)` | the leaves of `n` not in `k`, walked along `k` (a subtree of `n` off `k` is shared) | `without` (§5.8), `checkMark` | below |
| `subtract(n, s, above, at, below, admits)`, `foldUnder(n, above, below, admits)`, `foldUnderDelta(n, d, below, admits)` | the subsumption of `ap.md` §8.1 and the fold T5; `admits`: the child accessors through which a leaf covers the leaves below it (the exclusion of a normal TAINT tree; every accessor by default) | the groups | §4.3, §4.4 |
| `forEachLeaf(n, prefix, f)`, `forEachLeafPosition(n, f)` | `f(path, leaf)` per node with a leaf; `f(path)` | `leaves`, `emit`, the indexes of the stores | below |
| `graft(g, occurs, r)` | `r` at every node of `g` whose leaf `occurs` holds, MERGED with the grafts below it | `applySummary` (§5.4) | below |
| `filterPath(m, n, may)` | the paths that the type filter accepts | `filter` | §5.5 |
| `cleanSpine(n, p, reach, d, atNode, inside)` | the spine walk of a cleaner | `clean`, both kinds | §5.6 |
| `FieldLimitCut(alg, m).keep(n, L)` | (the paths within `L`, the `[any]` leaves at the cut points) | `limit`, both kinds | §5.7 |
| `TrieInterner.intern` (`internBottomUp`), `TrieNode.boundedDepth` | T6; the counted depth below a node | the stores; `limit`, the W3 assert | §4.5, §4.1 |

NO OTHER DEPTH BOUND. Today's node operations also collapse paths: `addParent` folds a repeated field
(`addParentFieldAccess` → `limitFieldAccess`, `AccessTree.kt:625-639`, `:1468-1496`), folds a chain of more than
`SUBSEQUENT_ARRAY_ELEMENTS_LIMIT = 2` elements (`limitElementAccess`, `:599-615`, `:1635`), refuses a parent above a class
accessor (`containsStatic`, `:273`, `:436`), and `concatToLeafAbstractNodes` applies the first two to the caller delta
(`:1291`, `:1299`). None of them is ported: `prepend`, `chain`, `graft` and every map build exactly the paths of the
spec. The field limit `L` (`ap.md` §4.4, Part I §5.7) is the only depth bound, as for `PathNode` (§3.3).

```kotlin
// TrieOps.kt: the code of the rows above.

/** ADAPT of transformAccessors (AccessTree.kt:1461-1466, :1671-1710): `f` maps each child (null drops it); an unchanged
 *  child list and leaf give `n` itself. */
internal inline fun <P : TrieLeaf> LeafAlgebra<P>.mapChildren(n: TrieNode<P>, leaf: P, f: (AccessorIdx, TrieNode<P>) -> TrieNode<P>?): TrieNode<P>? {
    val acc = n.accessors ?: return withLeaf(n, leaf)
    val kids = n.children!!
    var changed = false
    val outAcc = IntArrayList(acc.size)
    val outKids = ArrayList<TrieNode<P>>(acc.size)
    for (i in acc.indices) {
        val c = f(acc[i], kids[i])
        if (c !== kids[i]) changed = true
        if (c != null) { outAcc.add(acc[i]); outKids.add(c) }
    }
    if (!changed) return withLeaf(n, leaf)
    return node(leaf, outAcc.toIntArray(), outKids.toTypedArray()).takeIf { !it.isEmpty }
}

internal fun <P : TrieLeaf> LeafAlgebra<P>.withLeaf(n: TrieNode<P>?, leaf: P): TrieNode<P>? = when {
    n == null -> if (leaf.isEmpty) null else leafNode(leaf)
    leaf == n.leaf -> n
    else -> node(leaf, n.accessors, n.children).takeIf { !it.isEmpty }
}

/** The child at `a` replaced by `c` (inserted at its sorted place if `n` has no child `a`; removed if `c` is null), and the
 *  leaf replaced by `leaf`. */
internal fun <P : TrieLeaf> LeafAlgebra<P>.replaceChild(n: TrieNode<P>?, leaf: P, a: AccessorIdx, c: TrieNode<P>?): TrieNode<P>? {
    val old = n?.child(a)
    if (old === c) return withLeaf(n, leaf)                                       // the child does not change
    val outAcc = IntArrayList()
    val outKids = ArrayList<TrieNode<P>>()
    var put = c == null
    n?.accessors?.forEachIndexed { i, x ->
        if (!put && x > a) { outAcc.add(a); outKids.add(c!!); put = true }       // insert before the first larger accessor
        if (x == a) { if (c != null) { outAcc.add(a); outKids.add(c) }; put = true }   // replace or remove
        else { outAcc.add(x); outKids.add(n.children!![i]) }
    }
    if (!put) { outAcc.add(a); outKids.add(c!!) }
    return node(leaf, outAcc.toIntArray(), outKids.toTypedArray()).takeIf { !it.isEmpty }
}

internal inline fun <P : TrieLeaf> LeafAlgebra<P>.retainChildren(n: TrieNode<P>, pred: (AccessorIdx) -> Boolean): TrieNode<P>? =
    mapChildren(n, empty) { a, c -> if (pred(a)) c else null }

fun <P : TrieLeaf> LeafAlgebra<P>.mapLeaves(n: TrieNode<P>, f: (P) -> P): TrieNode<P>? = mapLeavesMemo(n, f, IdentityHashMap())

private fun <P : TrieLeaf> LeafAlgebra<P>.mapLeavesMemo(n: TrieNode<P>, f: (P) -> P,
                                                        memo: IdentityHashMap<TrieNode<P>, Optional<TrieNode<P>>>): TrieNode<P>? {
    memo[n]?.let { return it.orElse(null) }
    val r = mapChildren(n, intern(f(n.leaf))) { _, c -> mapLeavesMemo(c, f, memo) }
    memo[n] = Optional.ofNullable(r)
    return r
}

/** Cached per node: the result is a function of the node (immutable), so the race of two writers is benign. */
fun <P : TrieLeaf> LeafAlgebra<P>.foldAll(n: TrieNode<P>): P {
    n.allLeaves?.let { return it }
    var acc = n.leaf
    n.children?.forEach { acc = union(acc, foldAll(it)) }
    return acc.also { n.allLeaves = it }
}

internal inline fun <P : TrieLeaf> LeafAlgebra<P>.foldLeaves(n: TrieNode<P>, pred: (AccessorIdx) -> Boolean): P {
    var acc = n.leaf
    n.accessors?.forEachIndexed { i, a -> if (pred(a)) acc = union(acc, foldAll(n.children!![i])) }
    return acc
}

/** `path ++ n`: one node with the empty leaf per accessor of `path` (Tree.prependPath). `m` is only passed on: one
 *  signature for every helper that makes a result at a path (`chain` does not read it either). */
fun <P : TrieLeaf> LeafAlgebra<P>.prepend(m: ApManager, path: PathNode?, n: TrieNode<P>): TrieNode<P> =
    if (path == null) n else node(empty, intArrayOf(path.accessor), arrayOf(prepend(m, path.next, n)))

/** One leaf per depth on `path` (`spine[d]` at depth d; a missing entry is empty) and the node `tip` at the end of the
 *  path (its leaf joins `spine[path.size]`). `spine` is shorter than `path.size + 1` when the walk that made it stopped
 *  early; then `tip` is null. */
internal fun <P : TrieLeaf> LeafAlgebra<P>.chain(@Suppress("UNUSED_PARAMETER") m: ApManager, path: IntArray, spine: List<P>,
                                                 tip: TrieNode<P>? = null): TrieNode<P>? {
    check(spine.size <= path.size + 1)
    var r = if (spine.size == path.size + 1) withLeaf(tip, union(tip?.leaf ?: empty, spine[path.size])) else tip
    for (d in path.indices.reversed()) r = replaceChild(null, spine.getOrNull(d) ?: empty, path[d], r)
    return r
}

/** The leaves of `n` that `k` does not have, at the same path; `k` comes from `n` (a part), so the walk follows `k` and
 *  shares every child of `n` off `k`. */
fun <P : TrieLeaf> LeafAlgebra<P>.minusNode(n: TrieNode<P>, k: TrieNode<P>): TrieNode<P>? {
    if (n === k) return null
    return mapChildren(n, minus(n.leaf, k.leaf)) { a, c -> val kc = k.child(a); if (kc == null) c else minusNode(c, kc) }
}

/** `f(path, leaf)` for each node with a non-empty leaf; a shared subtree is visited once per path (each path is a leaf). */
fun <P : TrieLeaf> LeafAlgebra<P>.forEachLeaf(n: TrieNode<P>, prefix: IntArray, f: (IntArray, P) -> Unit) {
    if (!n.leaf.isEmpty) f(prefix, n.leaf)
    n.accessors?.forEachIndexed { i, a -> forEachLeaf(n.children!![i], prefix + a, f) }
}

fun <P : TrieLeaf> LeafAlgebra<P>.forEachLeafPosition(n: TrieNode<P>, f: (IntArray) -> Unit) = forEachLeaf(n, EMPTY_PATH) { p, _ -> f(p) }

/** The leaf positions of one Facts: `(Zero, [])` for REACH. Users: the indexes of AddedFactStore and PersistentRecordStore. */
fun ApManager.forEachLeafPosition(f: Facts, action: (AccessPathBase, IntArray) -> Unit) = when (f) {
    is Reach -> action(AccessPathBase.Zero, EMPTY_PATH)
    is FlowTree -> FlowAlgebra.forEachLeafPosition(f.root) { action(f.base, it) }
    is TaintTree -> taintAlg.forEachLeafPosition(f.root) { action(f.base, it) }
}

/** `r` (the result of one summary leaf kind, computed at the empty path, Part I §5.4) at every node of `g` whose leaf
 *  `occurs` holds, and no other leaf of `g`. ADAPT of concatToLeafAbstractNodes (AccessTree.kt:1281-1326). At an
 *  occurring node the result is the MERGE of `r` and the grafts below it, as today (:1321-1324: `bulkMergeAddAccessors` of
 *  the nested results, then `mergeAdd(concatNode)`). So an occurrence below an occurring node keeps both: the child of `r`
 *  from the graft above and `r` grafted again (a "replace" loses the leaves below: a false negative). A node of `g` with
 *  no occurrence at or below it gives null. The identity memo over `g` keeps the shared subtrees of `g` shared: the
 *  result depends only on the node of `g`. The ADAPT drops `filterTypes` (:1290; the AP has no summary-side filter,
 *  ap.md §4.8), `limitElementAccess` (:1291), `filterDeepExclusion` (:1292), `limitFieldAccess` (:1299; `L` is the only
 *  depth bound) and the `isFinal` leaf of `g` (:1321; one LeafKind per leaf kind, Part I §5.4). */
fun <G : TrieLeaf, R : TrieLeaf> LeafAlgebra<R>.graft(g: TrieNode<G>, occurs: (G) -> Boolean, r: TrieNode<R>): TrieNode<R>? =
    graftMemo(g, occurs, r, IdentityHashMap())

private fun <G : TrieLeaf, R : TrieLeaf> LeafAlgebra<R>.graftMemo(g: TrieNode<G>, occurs: (G) -> Boolean, r: TrieNode<R>,
                                                                  memo: IdentityHashMap<TrieNode<G>, Optional<TrieNode<R>>>): TrieNode<R>? {
    memo[g]?.let { return it.orElse(null) }
    val acc = IntArrayList()
    val kids = ArrayList<TrieNode<R>>()
    g.accessors?.forEachIndexed { i, a -> graftMemo(g.children!![i], occurs, r, memo)?.let { acc.add(a); kids.add(it) } }
    val below = node(empty, acc.toIntArray(), kids.toTypedArray()).takeIf { !it.isEmpty }   // the grafts below; no leaf of g
    val res = when {
        !occurs(g.leaf) -> below
        below == null -> r
        else -> mergeAdd(r, below)                                                // :1324
    }
    memo[g] = Optional.ofNullable(res)
    return res
}
```

Example (the merge): the summary `g = FlowTree(ret, {[]: LEAF, [f]: LEAF})` (`ret.*` and `ret.f.*`) and the result
`r = {[]: $ (T), [f]: $ (T)}` of the caller part `{j.$ (T), j.f.$ (T)}`. The graft at `[f]` gives `r` there
(`ret.f.$`, `ret.f.f.$`); the occurrence at `[]` gives `r` (`ret.$`, `ret.f.$`). The merge at `[]` keeps all three
leaves `ret.$ (T)`, `ret.f.$ (T)`, `ret.f.f.$ (T)`, the per-leaf union of `ap.md` §4.3. A replace at `[]` keeps only
`r` and loses `ret.f.f.$ (T)`.

### 4.3 The groups: T1, T2, T2', T3, T5 and the subsumption of `ap.md` §8.1

```kotlin
/** ap.md §8.1 subsumption, generic. `at(s)`: what a stored leaf `s` covers at its own node; `below(s)`: what it covers
 *  strictly below (an any leaf: `[any]` in a demand tree covers the `$` and `[any]` leaves, `[any-taint]` in a normal
 *  TAINT tree only the `[any-taint]` leaves, `TaintAlgebra.belowNormal`), through a child accessor that `admits` accepts
 *  (the exclusion of a stored normal TAINT tree, A2: an `[any-taint]/E` leaf covers nothing below an accessor in E;
 *  deeper steps are not filtered). The result is the part of `n` that no stored leaf covers. One layer only. */
internal fun <P : TrieLeaf> LeafAlgebra<P>.subtract(
    n: TrieNode<P>, s: TrieNode<P>?, above: P, at: (P) -> P, below: (P) -> P, admits: (AccessorIdx) -> Boolean = { true },
): TrieNode<P>? {
    if (s == null && above.isEmpty) return n                                        // nothing covers below: share
    val cover = if (s == null) above else union(above, at(s.leaf))
    val next = if (s == null) above else union(above, below(s.leaf))
    return mapChildren(n, minus(n.leaf, cover)) { a, c -> subtract(c, s?.child(a), if (admits(a)) next else above, at, below, admits) }
}

/** FLOW trees of one (statement, premise, base): per layer a few trees with distinct (exclusion, mark exclusion). T1,
 *  T2 (normal), T2', T3, T4, T5 (demand), the subsumption of ap.md §8.1. Single writer. REPLACE of
 *  EdgeNonUniverseExclusionMergingStorage (ap/ifds/access/tree/MethodEdgesInitialToFinalTreeApSet.kt:75), which unions
 *  exclusions across edges (:95): T3 forbids it (Subsume.union_loses_pairs). */
class FlowGroup(private val m: ApManager, private val interner: TrieInterner<FlowLeaf>) {
    private val trees = arrayOf(ArrayList<FlowTree>(1), ArrayList<FlowTree>(1))           // NORMAL, DEMAND: T3
    fun all(): Sequence<FlowTree> = trees[0].asSequence() + trees[1].asSequence()

    /** Returns the delta (T4) or null if `t` adds nothing. */
    fun add(t: FlowTree): FlowTree? {
        val list = trees[t.layer.ordinal]
        val i = list.indexOfFirst { it.exclusion == t.exclusion && it.markExclusion == t.markExclusion }
        if (i >= 0) {                                                                     // T1: the same key
            // §8.1 FIRST, against every tree of the layer, the same-key tree included: a leaf that the stored tree has, or
            // that one of its `[any]` leaves absorbed (T5), is not new. Without it an absorbed leaf is a delta on every
            // arrival, and a loop never empties the worklist.
            val rest = subtractSubsumed(t, list) ?: return null
            val (merged, d) = FlowAlgebra.mergeAddDelta(list[i].root, rest.root)
            if (d == null) return null
            val kept = if (t.layer == Layer.DEMAND) FlowAlgebra.foldUnderDelta(merged, d, FlowAlgebra::below) ?: merged else merged
            list[i] = list[i].withRoot(interner.internIfRequired(kept))                   // T5 on the stored tree (Part I §4.4)
            return m.flowTree(t.base, t.layer, t.exclusion, t.markExclusion, d)
        }
        val k = list.indexOfFirst { it.root == t.root &&                                  // EQUAL content (Tree.rule2_den)
            (it.markExclusion == t.markExclusion || it.exclusion == t.exclusion) }
        if (k >= 0) {                                                                     // T2 / T2': intersect, never union
            val s = list[k]
            val merged = m.flowTree(t.base, t.layer, s.exclusion.intersect(t.exclusion), s.markExclusion.intersect(t.markExclusion), s.root)
            if (merged.exclusion == s.exclusion && merged.markExclusion == s.markExclusion) return null   // s covers t
            list.removeAt(k)
            return add(merged)                         // T4 exception: the WHOLE merged tree (or its delta to a tree with that key)
        }
        val fresh = subtractSubsumed(t, list) ?: return null                               // §8.1
        val folded = if (t.layer == Layer.DEMAND) FlowAlgebra.foldUnder(fresh.root, FlowLeaf.NONE, FlowAlgebra::below) ?: fresh.root else fresh.root
        list += fresh.withRoot(interner.internIfRequired(folded))                          // T5 on the first tree of the key
        return fresh
    }

    /** §8.1 for FLOW (Subsume.subsumesB, markSubsB): a stored flag covers a flag of `t` at its node if its mark exclusion
     *  is a subset (`*∖Xs` over `*∖Xn`) and, for `*` leaves, its exclusion is a subset (`*/Es` over `*/En`); a demand flag
     *  (`[any]`) also covers every flag below it. */
    private fun subtractSubsumed(t: FlowTree, others: List<FlowTree>): FlowTree? {
        var root: FlowNode = t.root
        for (s in others) {
            val marks = s.markExclusion.isSubsetOf(t.markExclusion)
            val atOk = marks && (t.layer == Layer.DEMAND || s.exclusion.isSubsetOf(t.exclusion))
            val belowOk = marks && t.layer == Layer.DEMAND
            root = FlowAlgebra.subtract(root, s.root, FlowLeaf.NONE,
                at = { if (atOk) it else FlowLeaf.NONE }, below = { if (belowOk) it else FlowLeaf.NONE }) ?: return null
        }
        return if (root === t.root) t else t.withRoot(root)
    }
}

/** TAINT trees of one (statement, premise key, base): ONE demand tree, and in the normal layer one tree per EXCLUSION of
 *  the `[any-taint]` leaves (ap.md §8.1; DD16: the exclusion is in the key, as for FLOW). T1 (the same key), T2 (equal
 *  content, another exclusion: the INTERSECTION, never the union; ap.md §3.3 merge rule 2: the union of the two location
 *  sets of an `[any-taint]` leaf is the leaf with `Es ∩ Et`, because an exclusion reads only the first accessor), T3, T4,
 *  T5, and the subsumption of ap.md §8.1: a stored leaf with the mark T covers leaves with T of `t`. A `$` leaf covers
 *  a `$` leaf at its node. In the DEMAND tree an `[any]` leaf covers a `$` and an `[any]` leaf at its node and every leaf
 *  strictly below it. In the NORMAL trees an `[any-taint]/Es` leaf covers only `[any-taint]` leaves: at its node if
 *  `Es ⊆ Et`, strictly below its node through a child accessor that `Es` admits; it NEVER covers or absorbs a `$` leaf
 *  (`TaintAlgebra.belowNormal`; DD16, ap-history.md F69: a later demoting cleaner row moves the `[any-taint]` leaf to the
 *  demand tree, and the `$` leaf stays exact and normal). A demand leaf never subsumes a normal one, and the trees of one
 *  group are of one premise key, so a must-premise and the `[any]` premise of one path never subsume each other (two
 *  keys, Part I §3.4). The merge rule 2 and the subsumption with the exclusion are argued (ap.md §11.2). Single writer. */
class TaintGroup(private val m: ApManager, private val interner: TrieInterner<TaintLeaves>) {
    private val trees = arrayOf(ArrayList<TaintTree>(1), ArrayList<TaintTree>(1))           // NORMAL (per exclusion), DEMAND (one)
    fun all(): Sequence<TaintTree> = trees[0].asSequence() + trees[1].asSequence()

    fun add(t: TaintTree): TaintTree? {
        val alg = m.taintAlg
        val list = trees[t.layer.ordinal]
        if (list.none { it.exclusion == t.exclusion }) {
            val k = list.indexOfFirst { it.root == t.root }                              // T2: equal content, another exclusion
            if (k >= 0) {
                val s = list[k]
                val e = s.exclusion.intersect(t.exclusion)
                if (e == s.exclusion) return null                                         // s covers t
                list.removeAt(k)
                return add(m.taintTree(t.base, t.layer, s.root, e))                      // T4 exception: the WHOLE merged tree
            }
        }
        val rest = subtractSubsumed(t, list) ?: return null                              // §8.1 first, as in FlowGroup
        val i = list.indexOfFirst { it.exclusion == rest.exclusion }                     // last any removed: rest now has Empty E
        val below = alg.belowIn(t.layer)                                                 // normal: `[any-taint]` covers no `$` leaf
        if (i < 0) {                                                                     // T5 on the first tree of the key
            val folded = if (rest.root.hasAny) alg.foldUnder(rest.root, TaintLeaves.EMPTY, below, rest.exclusion::admits) ?: rest.root else rest.root
            list += rest.withRoot(interner.internIfRequired(folded))
            return rest
        }
        val (merged, d) = alg.mergeAddDelta(list[i].root, rest.root)
        if (d == null) return null
        val kept = if (d.hasAny) alg.foldUnderDelta(merged, d, below, rest.exclusion::admits) ?: merged else merged   // T5, both layers
        list[i] = list[i].withRoot(interner.internIfRequired(kept))
        return m.taintTree(t.base, t.layer, d, rest.exclusion)
    }

    private fun subtractSubsumed(t: TaintTree, others: List<TaintTree>): TaintTree? {
        val alg = m.taintAlg
        val normal = t.layer == Layer.NORMAL                                             // `others` are of the layer of t
        var root: TaintNode = t.root
        for (s in others) {
            val anyAt = s.exclusion.isSubsetOf(t.exclusion)                              // `[any-taint]/Es` over `[any-taint]/Et`
            root = alg.subtract(root, s.root, TaintLeaves.EMPTY,
                at = { alg.leaves(if (normal) it.exact else it.exact + it.any,           // demand: an `[any]` leaf T covers `$` T
                                  if (anyAt) it.any else MarkSet.EMPTY) },               // at its node; normal: no `$` leaf (DD16)
                below = alg.belowIn(t.layer), admits = s.exclusion::admits) ?: return null
        }
        return if (root === t.root) t else t.withRoot(root)
    }
}

/** The conclusions of ONE premise key, every base and kind, with the rules above. REACH: one bit per layer. Users:
 *  MethodEdgeStore (one per statement and premise key), RunSummaryStore, PersistentRecordStore. NdSummaryJoin keeps
 *  TaintGroups only (an ND conclusion is TAINT). */
class ConclusionGroup(private val m: ApManager, private val interners: StoreInterners) {
    private val reach = BooleanArray(2)
    private val flow = HashMap<AccessPathBase, FlowGroup>(2)
    private val taint = HashMap<AccessPathBase, TaintGroup>(2)

    fun add(f: Facts): Facts? = when (f) {
        is Reach -> if (reach[f.layer.ordinal]) null else f.also { reach[f.layer.ordinal] = true }
        is FlowTree -> flow.getOrPut(f.base) { FlowGroup(m, interners.flow) }.add(f)
        is TaintTree -> taint.getOrPut(f.base) { TaintGroup(m, interners.taint) }.add(f)
    }

    fun all(): Sequence<Facts> = Layer.entries.asSequence().filter { reach[it.ordinal] }.map(Reach::of) +
        flow.values.asSequence().flatMap { it.all() } + taint.values.asSequence().flatMap { it.all() }
}

/** The trie interners of one store, one per kind (DD5); each table is soft (Part I §4.5). ApManager.newInterners makes it. */
class StoreInterners(refs: SoftReferenceManager, cancellation: Cancellation) {
    val flow = TrieInterner<FlowLeaf>(refs, cancellation)
    val taint = TrieInterner<TaintLeaves>(refs, cancellation)

    /** The policy of Part I §4.5 on the trie of one stored value; REACH has no trie. User: AddedFactStore (Part I §7.5). */
    fun internIfRequired(f: Facts): Facts = when (f) {
        is Reach -> f
        is FlowTree -> flow.internIfRequired(f.root).let { if (it === f.root) f else f.withRoot(it) }
        is TaintTree -> taint.internIfRequired(f.root).let { if (it === f.root) f else f.withRoot(it) }
    }
}
```

Subtraction can remove the last any leaf. The TAINT factory then gives the remainder Empty E, so destination lookup
uses `rest.exclusion`. Looking up the input E can append a second Empty-E tree. For example, a stored
`x.[any-taint](T1)` subsumes the any leaf of incoming `{x.[any-taint]/{f}(T1), x.g.$(T2)}`. Merge the remaining
`x.g.$(T2)` into the existing Empty-E tree. `CurrentTaintGroupKeys.insert_unique` checks key uniqueness;
`place_reads` checks that this destination merge preserves every annotated leaf. The computed regression is
`Certificate.witness`. These results cover destination lookup and merge, not the full trie store.

The subscriptions and the links do not use subsumption (`analyzer-core.md` §5.3): `AddedFactStore` and `RequestStore`
deduplicate exactly (§7.5, §7.9).

### 4.4 T5: the fold under `[any]` (generic)

```kotlin
/** T5 (ap.md §7.2): inside ONE demand tree, an `[any]` leaf at p absorbs the leaves strictly below p that `below` covers
 *  (FLOW: every flag; TAINT: the `$` and `[any]` leaves with its marks); inside ONE normal TAINT tree, an `[any-taint]`
 *  leaf at p absorbs only the `[any-taint]` leaves below p with its marks (`TaintAlgebra.belowNormal`: never a `$`
 *  leaf, DD16), through a child accessor that the tree exclusion admits (`admits`: a leaf below an excluded accessor is
 *  not a location of the any leaf). The denotation does not change (argued, ap.md §11.2). The groups apply it to the
 *  STORED tree after the delta is computed, so a fold never hides a new leaf from the delta. */
internal fun <P : TrieLeaf> LeafAlgebra<P>.foldUnder(n: TrieNode<P>, above: P, below: (P) -> P,
                                                     admits: (AccessorIdx) -> Boolean = { true }): TrieNode<P>? {
    if (above.isEmpty && n.accessors == null) return n                              // a leaf node with nothing above it
    val next = union(above, below(n.leaf))
    return mapChildren(n, minus(n.leaf, above)) { a, c -> foldUnder(c, if (admits(a)) next else above, below, admits) }
}

/** T5 after an add: `n` is the merged stored trie, `d` the delta of the add (`n` contains `d`). The walk follows `d` only:
 *  at a node where a leaf of `d` absorbs (`below` is not empty) it folds the stored subtree there; a child that `d` does
 *  not have is shared as it is. The same result as `foldUnder(n, empty, below)`: the stored trie was folded before the add
 *  (the groups fold the first tree of a key too), and the subsumption of `ap.md` §8.1, with the same `below`, dropped
 *  every new leaf that an old absorbing leaf covers (a `$` leaf under an `[any-taint]` leaf is neither dropped nor
 *  folded), so a new absorption is only below a leaf of `d`. */
internal fun <P : TrieLeaf> LeafAlgebra<P>.foldUnderDelta(n: TrieNode<P>, d: TrieNode<P>, below: (P) -> P,
                                                          admits: (AccessorIdx) -> Boolean = { true }): TrieNode<P>? {
    if (!below(d.leaf).isEmpty) return foldUnder(n, empty, below, admits)          // a leaf of `d` absorbs: fold here
    if (d.accessors == null) return n
    return mapChildren(n, n.leaf) { a, c -> d.child(a)?.let { foldUnderDelta(c, it, below, admits) } ?: c }
}
```

T5 is optional ("may", `ap.md` §7.2). A group applies it only when the delta has an absorbing leaf: every FLOW demand
delta (each flag is an `[any]` leaf), a TAINT delta with an any mark (`hasAny`): an `[any]` mark in the demand layer, an
`[any-taint]` mark in the normal layer (with the exclusion of its tree as `admits`, and `belowNormal`: it folds only the
`[any-taint]` leaves below it, a `$` leaf stays). The cost of one add is the
nodes of the delta and the stored subtrees below its absorbing leaves (`foldUnderDelta`), not a walk of the whole stored
trie. The first tree of a key takes `foldUnder` once (its own size).

### 4.5 T6 interning, `boundedDepth`, equality across interners

```kotlin
/** T6, ap.md §7.5: bottom-up hash-consing. ADAPT of AccessTreeInterner (ap/ifds/access/tree/AccessTreeInterner.kt:8), of
 *  AccessTreeSoftInterner (AccessTreeSoftInterner.kt:7) and of AccessNode.internNodes (AccessTree.kt:1125-1172).
 *  THE TABLE IS A CACHE behind a managed soft reference, as today (AccessTreeSoftInterner.kt:18-23, TreeApManager.kt:39):
 *  `refs` is the SoftReferenceManager of the analysis (ApManager.softRefs, Part I §5.1). The first stage of the memory
 *  guard clears it (util/MemoryManager.kt:62-76), and the next intern makes a new table. This is exact: an interned node
 *  stays valid and equality is structural (DD5); only the sharing with later equal nodes is lost. While the guard keeps
 *  the manager disabled (`createRef` gives null), the table lives for one intern call, as today. The lifetime of a table:
 *  below the code. Not thread-safe: one per store and kind (owner-local, Part I §7.1; DD5). */
class TrieInterner<P : TrieLeaf>(private val refs: SoftReferenceManager, private val cancellation: Cancellation) {
    private class Table<P : TrieLeaf> {
        private val buckets = Long2ObjectOpenHashMap<Object2ObjectOpenCustomHashMap<TrieNode<P>, TrieNode<P>>>()
        fun intern(n: TrieNode<P>): TrieNode<P> =                                     // AccessTreeInterner.intern (:46-52)
            buckets.computeIfAbsent(n.hash) { Object2ObjectOpenCustomHashMap<TrieNode<P>, TrieNode<P>>(InternStrategy) }
                .putIfAbsent(n, n) ?: n
    }

    private var cache: Reference<Table<P>>? = null
    private var operationsBeforeIntern = INTERN_RATE

    private fun table(): Table<P> = cache?.get() ?: Table<P>().also { cache = refs.createRef(it) }   // AccessTreeSoftInterner.kt:18-23

    /** A node with `interned = true` (this store or another one made it) stays as it is (AccessTree.kt:1134, :1157-1161). */
    fun intern(n: TrieNode<P>): TrieNode<P> = if (n.interned) n else internBottomUp(n, table(), IdentityHashMap())

    /** Bottom-up: the children first, then a COPY of the node with the interned children and `interned = true` (the flag is
     *  a constructor val: markInterned, AccessTree.kt:1174-1182). `memo` is the identity memo of one call, so a shared
     *  subtree is walked once. The recursion depth is the trie depth: W3 bounds it (at most L counted accessors and the
     *  uncounted class accessor), so the explicit stack of :1136-1169 is not needed. */
    private fun internBottomUp(n: TrieNode<P>, t: Table<P>, memo: IdentityHashMap<TrieNode<P>, TrieNode<P>>): TrieNode<P> {
        if (n.interned) return n
        memo[n]?.let { return it }
        cancellation.checkpoint()
        val kids = n.children
        var out = kids                                                     // copied only when a child changes
        if (kids != null) for (i in kids.indices) {
            val c = internBottomUp(kids[i], t, memo)
            if (c !== kids[i]) { if (out === kids) out = kids.copyOf(); out!![i] = c }
        }
        return t.intern(TrieNode(n.leaf, n.accessors, out, interned = true)).also { memo[n] = it }
    }

    /** THE POLICY, a deliberate difference to today: a large trie at once (as TreeSetWithCompression.internIfRequired,
     *  ap/ifds/access/tree/TreeSetWithCompression.kt:14-17), else the trie of one add in INTERN_RATE when it is not small.
     *  Today the same tick re-interns EVERY trie of the store (`intern(idx)`, `internImpl`, :19-29, :32-55). Reason: each
     *  group stores its trie at the place of its add (a FLOW list, a TAINT array, a map value of NdSummaryJoin or
     *  AddedFactStore), so the policy acts on the trie that the add stores, with no enumeration of the store. A later
     *  intern of the same trie walks only its new nodes (an interned subtree keeps its flag). The cost: a trie of a store
     *  with few adds is shared only by the operations (they share every unchanged subtree, Part I §4.2), not by the table. The
     *  groups call it on every stored trie. */
    fun internIfRequired(n: TrieNode<P>): TrieNode<P> = when {
        n.size >= SIZE_TO_FORCE_INTERN -> intern(n)
        --operationsBeforeIntern > 0 || n.size < MIN_SIZE_TO_INTERN -> n
        else -> { operationsBeforeIntern = INTERN_RATE; intern(n) }
    }

    companion object { const val MIN_SIZE_TO_INTERN = 100L; const val SIZE_TO_FORCE_INTERN = 100_000L; const val INTERN_RATE = 100 }
}

/** AccessTreeInterner.InternStrategy (AccessTreeInterner.kt:9-42): the hash, the leaf by `==` (a FlowLeaf is an enum, a
 *  TaintLeaves is interned by the one ApManager), the accessors by content, the children by IDENTITY (bottom-up: equal
 *  children are one object in one table). */
private object InternStrategy : Hash.Strategy<TrieNode<*>> {
    override fun hashCode(o: TrieNode<*>?): Int = o?.hashCode() ?: 0
    override fun equals(a: TrieNode<*>?, b: TrieNode<*>?): Boolean {
        if (a === b) return true
        if (a == null || b == null || a.hash != b.hash || a.leaf != b.leaf) return false
        if (!a.accessors.contentEquals(b.accessors)) return false
        val x = a.children ?: return b.children == null
        val y = b.children ?: return false
        return x.size == y.size && x.indices.all { x[it] === y[it] }
    }
}
```

THE LIFETIME OF A TABLE. A table lives inside one guarded region: a run (the memory guard of `RunManager.run`) or a
barrier (the memory guard of the barrier, `analyzer-core.md` §7.2 B4, `analyzer-impl.md` §7.1; `ap-history.md`
F68 (5)). Today's `MemoryManager.runWithMemoryManager` (`util/MemoryManager.kt:107-117`) enables every soft-reference
manager at the start of the region and calls `cleanup()` in its `finally`: every table of the region is cleared, and the
manager stays disabled (`util/SoftReferenceManager.kt:12-14`) until the next region enables it. So:

| Where | The table |
|---|---|
| inside a run | lives until the first stage of the guard clears it (the manager is disabled until a GC below the threshold enables it again, `MemoryManager.kt:50-51`); the next intern makes a new one |
| at a barrier | the managers are enabled during the whole barrier, `persist` too: `PersistentRecordStore` interns with a live table, so the records of one barrier share nodes (§7.8). The first stage of the guard clears the table (sharing only), as in a run; a hit of the guard cancels the `Cancellation` and ends the iteration with `OOM` (`analyzer-core.md` §7.2 B4) |
| between two regions (the driver code outside `RunManager.run` and outside a barrier; the join of a run is inside `RunManager.run`, `analyzer-impl.md` §3.2) | the manager is disabled: `createRef` gives null, and a table lives for one intern call |

A table never outlives its region. A node that it interned stays valid after the table goes (DD5). A later table does
not unify a new node with an equal node of an earlier table; only the operations share them (they share every unchanged
subtree, §4.2). No store keeps a strong table: the guard could not clear it.

`TrieNode.size` (§4.1) is the node count with shared subtrees counted again, as `AccessNode.size`
(`AccessTree.kt:316-322`). `boundedDepth` is the exact number of counted accessors on the longest path below a node.
It is an `Int`, with checked child-depth addition, and is not part of the hash (`ap.md` §7.5). JVM paths and budgets
must fit their `Int` representation; overflow fails construction instead of returning a shallow cache value.
`CurrentDepthCache.depthWitness` computes a path attaining the maximum; `skip_iff_fits` proves that
`root.boundedDepth <= L` permits `limit` to share the whole subtree exactly when all paths fit (§5.7).
The former saturated `Short` cache gave 32,767 for a 32,768-accessor path and incorrectly skipped its cut at
`L = 32,767` (`short_cache_skips_over_limit`, `exact_cache_requires_cut`). `MethodEdgeStore.add` asserts W3 with
the exact cache (§7.3). These are metadata checks; they do not prove the full Kotlin cut or an unbounded heap model.

EQUALITY ACROSS INTERNERS (DD5). Each store has its own interners, so two equal nodes of two stores can be two objects.
A callee publication (from the callee `RunSummaryStore`) meets a caller subscription (from the caller `AddedFactStore`)
in `satisfying`, `applySummary` and the groups of the caller. So no operation reads identity as equality:

| Place | Why it is correct with two interners |
|---|---|
| `TrieNode.hash`, `equals` | the leaf by its value hash and `==` (an enum, or a `TaintLeaves` with a content hash), the children by their structural hash and `equals`: the same for equal content in every store; `this === other` is only a fast path |
| `TrieInterner` | it compares children by identity, which is exact inside one table (bottom-up: equal children are one object there). A node that another store interned, or that a cleared table of this store interned, stays as it is: it is immutable and equality is structural; it only is not shared with the equal nodes of the current table |
| `mergeNodeLoop` | identity pairs (`NodePair`) are only a memo and `a === b` only a shortcut: two equal nodes of two stores take the full merge, with the same result |
| T2 (§4.3), `minusNode` (§5.8), `Facts.equals` (§4.1) | they use `==`, so they also work for a trie that another store made |

### 4.6 `walkPath`: the one path walk

```kotlin
/** The walk of `path` from `root` (ap.md §7.3 step 1): `onPrefix(d, node)` for each proper prefix node (depth d; the rest of
 *  the path starts at path[d]); the result is the node of the path, or null if the trie has no such node. ONE walk for
 *  every trie of Part I. */
inline fun <N : Any> walkPath(root: N, path: IntArray, child: (N, AccessorIdx) -> N?, onPrefix: (Int, N) -> Unit = { _, _ -> }): N? {
    var n = root
    for (d in path.indices) {
        onPrefix(d, n)
        n = child(n, path[d]) ?: return null
    }
    return n
}

inline fun <P : TrieLeaf> TrieNode<P>.walk(path: IntArray, onPrefix: (Int, TrieNode<P>) -> Unit = { _, _ -> }): TrieNode<P>? =
    walkPath(this, path, { n, a -> n.child(a) }, onPrefix)
```

| User | The prefix nodes | The node of the path |
|---|---|---|
| `applyCompiledEdge` (§5.3) | the case `above` | the subtree U of the case `below` |
| `satisfying` (§5.4) | `inside`: the leaves above `j` | `applicable`: the subtree at `j`; `inside`: the leaves at `j` |
| `checkMark` (§5.8) | the leaves above the pattern | the leaves at and below the pattern |
| `restrict`, `emit` (§5.9) | the moved any marks that meet the mark of `D-p` (F71); the emissions above `D-c` | the node of `D-p`: the meet of the tails and the leaves below `D-p` (F70), with the mark test (F71); the meet and the facts below `D-c` |
| `PathTrie` (§7.2) | `lookupPrefixes` | `find` (`lookupExtensions`, `around`) |

`clean` (§5.6) does not use `walkPath`: it rebuilds the spine of `x.p` with its own recursion (`cleanSpine`).

---

## 5. `ApManager` and `ApOps` (`ap.md` §4, §6.3–§6.5, §7.3, §7.4, §9.1)

### 5.1 `ApManager`

```kotlin
/** The interners and the factories of the AP. One per analysis (analyzer-core.md §2). Thread-safe (O4): every shared table is
 *  a ConcurrentHashMap (lock-free reads that are correct under the JMM) or the copy-on-write arrays of Part I §3.1. No
 *  ConcurrentReadSafe map. Trie hash-consing is owner-local (Part I §4.5, DD5), so no node table is shared, and no run leaks
 *  its tries into the next run. The trie tables of the stores are soft: `softRefs` is a SoftReferenceManager of the
 *  RefManager that the memory guard of every run and of every barrier reads (REUSE, util/RefManager.kt:10-11), so the
 *  guard clears them, as today's "Tree" manager (TreeApManager.kt:39); the lifetime of a table: Part I §4.5. The
 *  analysis passes the RefManager of its memory guard
 *  (`SharedObjects.refManager`, analyzer-impl.md §3.1: the RefManager of `TaintAnalyzer`, which `JIRBidiAnalysis` takes,
 *  analyzer-impl.md §8.1); the default serves the tests.
 *  The tables of ApManager itself stay strong: a store key compares their objects by identity (DD5). */
class ApManager(val cancellation: Cancellation, refManager: RefManager = RefManager()) {
    val softRefs: SoftReferenceManager = refManager.softRefManager("bidi")
    val accessors = AccessorTable()
    val marks = MarkTable()
    val flowAlg: FlowAlgebra get() = FlowAlgebra
    val taintAlg = TaintAlgebra(this)                                              // interns TaintLeaves (T6)

    private val markSets = ConcurrentHashMap<MarkSet, MarkSet>()
    private val exclusions = ConcurrentHashMap<ExclusionSet, ExclusionSet>()
    private val paths = ConcurrentHashMap<PathNode, PathNode>()
    private val initials = ConcurrentHashMap<InitialKey, InitialAp>()
    private val premiseSets = ConcurrentHashMap<List<InitialAp>, PremiseSet>()
    private val nextId = AtomicInteger()
    private data class InitialKey(val base: AccessPathBase, val path: PathNode?, val tail: Tail, val exclusion: ExclusionSet, val mark: MarkSlot)

    fun intern(s: MarkSet): MarkSet = if (s.isEmpty) MarkSet.EMPTY else markSets.putIfAbsent(s, s) ?: s
    fun intern(e: ExclusionSet): ExclusionSet = if (e == ExclusionSet.Empty) e else exclusions.putIfAbsent(e, e) ?: e
    fun markSetOf(t: TaintMark): MarkSet = intern(MarkSet(intArrayOf(t.id)))
    fun path(a: AccessorIdx, next: PathNode?): PathNode = PathNode(a, next).let { paths.putIfAbsent(it, it) ?: it }
    fun path(p: List<AccessorIdx>): PathNode? = p.foldRight(null as PathNode?) { a, n -> path(a, n) }
    fun path(p: IntArray): PathNode? = p.foldRight(null as PathNode?) { a, n -> path(a, n) }

    /** §4.10: a position is the path cut to at most two accessors (`[<C>, f]` or `[<C>]`). */
    fun position(p: IntArray): PathNode = checkNotNull(path(p.copyOf(minOf(2, p.size))))

    fun initial(base: AccessPathBase, path: PathNode?, tail: Tail, exclusion: ExclusionSet, mark: MarkSlot): InitialAp =
        initials.computeIfAbsent(InitialKey(base, path, tail, intern(exclusion), mark)) { k ->
            InitialAp(k.base, k.path, k.tail, k.exclusion, k.mark, nextId.getAndIncrement(), this)
        }
    fun initial(p: Pattern): InitialAp = initial(p.fact.base, path(p.fact.path), p.fact.tail, p.exclusion, p.fact.mark)

    /** ap.md §7.1, §4.6, DD13. The zero fact adds no condition (it is at every node that an edge reaches), so it is
     *  dropped; the result is {zero} only if every member is the zero fact. One member: the InitialAp itself; two or more:
     *  the interned PremiseSet (an ND edge). */
    fun premiseOf(members: Collection<InitialAp>): PremiseKey {
        check(members.isNotEmpty()) { "a premise set is never empty" }
        val sorted = members.filter { !it.isZero }.distinct().sortedBy { it.id }
        return when (sorted.size) {
            0 -> zero                                                                // every member is the zero fact
            1 -> sorted[0]                                                           // {zero, i} is {i}
            else -> premiseSets.computeIfAbsent(sorted) { PremiseSet(it.toTypedArray()) }
        }
    }

    /** ap.md §4.6: the union of two premise sets without the zero fact (a conjunction result, a conjunctive sink, the caller
     *  edges of E6). {zero} ∪ {zero} = {zero}; {zero} ∪ {i} = {i}; {i} ∪ {k} = {i, k}. */
    fun union(a: PremiseKey, b: PremiseKey): PremiseKey = if (a === b) a else premiseOf(a.members + b.members)

    val zero: InitialAp = initial(AccessPathBase.Zero, null, Tail.EXACT, ExclusionSet.Empty, MarkSlot.Concrete(TaintMark.ZERO))

    /** The canonical FLOW tree: a demand flag is `[any]`, so its exclusion is Empty (W1, W2). */
    fun flowTree(base: AccessPathBase, layer: Layer, exclusion: ExclusionSet, mx: MarkSet, root: FlowNode): FlowTree {
        check(!root.isEmpty)
        return FlowTree(base, layer, if (layer == Layer.DEMAND) ExclusionSet.Empty else intern(exclusion), intern(mx), root)
    }

    /** The canonical TAINT tree: the layer names the any leaves (W8 (b), DD16); `exclusion` is the exclusion of the
     *  `[any-taint]` leaves, Empty in the demand layer and with no any leaf (a demoted leaf loses it, AnyTaintEx.normX). */
    fun taintTree(base: AccessPathBase, layer: Layer, root: TaintNode, exclusion: ExclusionSet = ExclusionSet.Empty): TaintTree {
        check(!root.isEmpty)
        return TaintTree(base, layer, root, if (layer == Layer.DEMAND || !root.hasAny) ExclusionSet.Empty else intern(exclusion))
    }

    fun taintLeaves(exact: MarkSet, any: MarkSet): TaintLeaves = taintAlg.leaves(exact, any)
    fun newInterners(): StoreInterners = StoreInterners(softRefs, cancellation)
}
```

The one-leaf `Facts` of a `PathFact`, through the normal form of `ap.md` §4.1 step 6. Users: `targetTree` (§5.8) and
`applyRecordLeaf` results (§5.4), after direct edge application:

```kotlin
/** TrieOps.kt. REACH on the zero base, FLOW for an abstract mark, TAINT for a concrete mark; W2, W6 and W8 by `normalize`
 *  (an `[any]` leaf goes to the demand tree; an `[any-taint]/E` leaf stays in the normal tree with E as the tree
 *  exclusion, and in the demand layer it is `[any]` with no exclusion: the same any leaf, DD16). The user with an
 *  `[any-taint]` input: a conjunction target of a source (ap.md §4.6; its exclusion is Empty, interpreter.md I14). */
fun ApManager.factsOf(f: PathFact, exclusion: ExclusionSet, layer: Layer): Facts {
    val n = normalize(f, exclusion, layer == Layer.DEMAND)                         // ap.md §4.1 step 6 (Part I §6)
    val l = if (n.demand) Layer.DEMAND else Layer.NORMAL
    val p = path(n.fact.path)
    return when (val mk = n.fact.mark) {
        is MarkSlot.Star -> {
            check(n.fact.tail != Tail.EXACT) { "S8: a `$` leaf has a concrete mark" }
            flowTree(f.base, l, n.exclusion, mk.excluded, FlowAlgebra.prepend(this, p, FlowAlgebra.LEAF_NODE))
        }
        is MarkSlot.Concrete -> if (f.base == AccessPathBase.Zero) Reach.of(l) else taintTree(f.base, l,
            taintAlg.prepend(this, p, taintAlg.leafNode(
                if (n.fact.tail == Tail.EXACT) taintLeaves(markSetOf(mk.mark), MarkSet.EMPTY) else taintLeaves(MarkSet.EMPTY, markSetOf(mk.mark)))),
            if (n.fact.tail == Tail.ANY_TAINT) n.exclusion else ExclusionSet.Empty)          // `[any-taint]/E`: the tree exclusion
    }
}
```
 The trie functions are the generic functions
of `TrieOps.kt` (§4.2); `ApManager` has no node function of its own.

### 5.2 `ApOut` and the result collectors

```kotlin
/** Receives the results of one operation on one input. Requests exist only in run 1, and only for FLOW input (DD12). */
interface ApOut {
    fun result(f: Facts)                          // one result value: its own kind and canonical key
    fun markRequest(mark: TaintMark)              // ap.md §4.5, on the premise of the input edge
    fun positionRequest(position: PathNode)       // ap.md §4.10 item 1, the path cut to <= 2 accessors
}

/** ap.md §7.3 steps 5 to 7: the results of one operation on one input, one value per kind and canonical key. REACH: the
 *  layer. FLOW: (layer, exclusion, mark exclusion), the exclusion Empty in the demand layer. TAINT: (layer, exclusion of
 *  the `[any-taint]` leaves), the exclusion Empty in the demand layer and for a node with no any leaf (the normal form of
 *  TaintTree, DD16): one application can give `[any-taint]` leaves with several exclusions (ap.md §4.1 A2 rows; Lean
 *  AnyTaintExCases.X.two_results), so several trees. NO W6 SPLIT: an any leaf of a normal node is `[any-taint]` (a must,
 *  W8), so the caller decides the layer and the exclusion where it makes the leaf (W6 for a may `[any]` target:
 *  `EdgeApplication.put`; the `part` rows: `TaintClean.put`; the cut: `limit`). Users: applyCompiledEdge, clean (and
 *  through them applySummary, applyCombination). */
internal class Results(private val m: ApManager, private val base: AccessPathBase) {
    private val reach = BooleanArray(2)
    private data class FlowKey(val layer: Layer, val exclusion: ExclusionSet, val mx: MarkSet)
    private val flow = LinkedHashMap<FlowKey, FlowNode>(2)
    private data class TaintKey(val layer: Layer, val exclusion: ExclusionSet)
    private val taint = LinkedHashMap<TaintKey, TaintNode>(2)

    fun reach(layer: Layer) { reach[layer.ordinal] = true }

    fun flow(layer: Layer, exclusion: ExclusionSet, mx: MarkSet, node: FlowNode) {
        val k = FlowKey(layer, if (layer == Layer.DEMAND) ExclusionSet.Empty else m.intern(exclusion), m.intern(mx))
        flow[k] = flow[k]?.let { FlowAlgebra.mergeAdd(it, node) } ?: node
    }

    /** `layer`: NORMAL keeps the any leaves of `node` as `[any-taint]` with `exclusion`; DEMAND reads them as `[any]` with
     *  no exclusion (W8 (b)). */
    fun taint(layer: Layer, node: TaintNode, exclusion: ExclusionSet = ExclusionSet.Empty) {
        val k = TaintKey(layer, if (layer == Layer.DEMAND || !node.hasAny) ExclusionSet.Empty else m.intern(exclusion))
        taint[k] = taint[k]?.let { m.taintAlg.mergeAdd(it, node) } ?: node
    }

    fun flush(out: ApOut) {
        for (l in Layer.entries) if (reach[l.ordinal]) out.result(Reach.of(l))
        for ((k, n) in flow) out.result(m.flowTree(base, k.layer, k.exclusion, k.mx, n))
        for ((k, n) in taint) out.result(m.taintTree(base, k.layer, n, k.exclusion))
        reach.fill(false); flow.clear(); taint.clear()
    }
}

/** An ApOut that keeps everything: tests, applySummary (Part I §5.4), the barrier. */
class CollectingOut : ApOut {
    val results = ArrayList<Facts>(); val markRequests = ArrayList<TaintMark>(); val positionRequests = ArrayList<PathNode>()
    override fun result(f: Facts) { results += f }
    override fun markRequest(mark: TaintMark) { markRequests += mark }
    override fun positionRequest(position: PathNode) { positionRequests += position }
}
```

### 5.3 The facade, `MarkGate`, `applyCompiledEdge` (`ap.md` §4.1, §4.2 step 4, §7.3; DD15)

```kotlin
/** The facade of the AP operations. Stateless: every memo is local to one call, so one instance serves every thread. */
class ApOps(val manager: ApManager) {
    /** `may`: the micro edge is a MAY (its FORWARD target is `[any]`: a pass rule with an `AnyField` target, Part II §23.1
     *  `MicroEdge.may`), so every result is in the demand layer. Forward W6 gives it anyway (every result has the `[any]`
     *  tail); backward it is the rule of the reversed may edge, also for a `$` result (interpreter.md I14, §4.9 the
     *  pass-rule row; argued, ap.md §11.2), and of the reversed literal of a conjunctive edge (F70, THE REVERSAL OF A
     *  CONJUNCTION, Part II §23.1 `MicroEdge.conjunctive`). The engine passes `me.may` (analyzer-impl.md `EngineAlgebra`). */
    fun applyEdge(c: Facts, premise: PremiseKey, e: PathEdge, statementEdge: Boolean, mode: ApMode, out: ApOut, may: Boolean = false) {
        val ce = e.compiled ?: manager.compile(e).also { e.compiled = it }        // the compile cache of the edge; `ap.md` §7.5 has no walk memo (Part II §34 SI18)
        applyCompiledEdge(c, ce, edgeDemand = may, staticAt = staticIdentityDepth(c, premise, ce, statementEdge, mode), mode, out)
    }
    // Part I §5.4: satisfying, applySummary, applyCombination.   Part I §5.5: filter.   Part I §5.6: clean.
    // Part I §5.7: limit.   Part I §5.8: checkMark, without, targetTree.   Part I §5.9: zero, startFact, policy, emit, restrict, demandPart.
    // Part I §5.10: requestAction.   Part I §5.11: reverse, leaves.
}
```

§5.4 to §5.11 write each operation as `fun ApOps.x(...)` to show its file. In the code each one is a member of `ApOps`
with the same signature. The member calls the internal helper of its file (`EdgeApplication`, `FlowClean`, `TaintClean`,
`FieldLimitCut`).

```kotlin
/** A PathEdge in the form that the tree walk reads. PathEdge caches it (`@JvmField internal var compiled`, Part I §6): a
 *  benign race, because CompiledEdge is immutable with final fields (JLS §17.5). Part II makes the PathEdges once per
 *  statement and keeps them in the method context cache, so the cache hits. */
internal fun ApManager.compile(e: PathEdge) = CompiledEdge(
    e.from.base, e.from.path.toIntArray(), e.from.tail, e.from.mark,
    e.to.base, path(e.to.path), e.to.tail, e.to.mark, intern(e.exclusion), intern(e.fromExclusion), intern(e.toExclusion))

internal class CompiledEdge(
    @JvmField val fromBase: AccessPathBase, @JvmField val fromPath: IntArray, @JvmField val fromTail: Tail, @JvmField val fromMark: MarkSlot,
    @JvmField val toBase: AccessPathBase, @JvmField val toPath: PathNode?, @JvmField val toTail: Tail, @JvmField val toMark: MarkSlot,
    @JvmField val exclusion: ExclusionSet,              // the ONE exclusion of the `*` sides of the edge (§2.2)
    @JvmField val fromExclusion: ExclusionSet = ExclusionSet.Empty,   // A2, ap.md §4.1 `PathEdge`: the own exclusion Ej of a
    @JvmField val toExclusion: ExclusionSet = ExclusionSet.Empty,     // must-premise, and Et of an `[any-taint]` summary
) {                                                     // conclusion: only a summary or record edge (§5.4); a micro edge has none (I14)
    init {                                              // §4.1 preconditions; a bug of the interpreter or the analyzer
        check(fromMark !is MarkSlot.Star || fromMark.excluded.isEmpty)                        // S7
        check(toMark !is MarkSlot.Concrete || fromMark is MarkSlot.Concrete)                  // S7
        check(fromTail != Tail.EXACT || fromMark is MarkSlot.Concrete)                        // S8
        check(fromTail != Tail.EXACT || toTail != Tail.STAR)                                  // S8
        check(toTail != Tail.EXACT || fromMark is MarkSlot.Concrete)                          // S8 (ExactTargetConc)
        check(toTail != Tail.ANY_TAINT || toMark is MarkSlot.Concrete)                        // W8, S15: a must has a concrete mark
        check(fromTail != Tail.ANY_TAINT || fromMark is MarkSlot.Concrete)                    // W8 (a); with S7, an `[any-taint]`
                                                                                              // target has a concrete premise mark
        check(fromTail == Tail.STAR || toTail == Tail.STAR || exclusion == ExclusionSet.Empty)  // W1: else `admitsRest`
                                                        // would silently drop children of U (also the edges of leafKinds)
        check(fromTail == Tail.ANY_TAINT || fromExclusion == ExclusionSet.Empty)              // A2: only an `[any-taint]`
        check(toTail == Tail.ANY_TAINT || toExclusion == ExclusionSet.Empty)                  // side has its own exclusion
    }
}

/** ap.md §4.1 static exception, §4.10 item 1. The depth of the `*` leaf of an identity static `*` edge `(S, q, */E0, *) -> c`
 *  at `q = []` or `q = [<C>]`, when a statement micro edge reads strictly below q in run 1; else -1. Only a normal FLOW tree
 *  has `*` leaves. The walk tests condition 4 (`Ec admits r`). */
private fun staticIdentityDepth(c: Facts, premise: PremiseKey, ce: CompiledEdge, statementEdge: Boolean, mode: ApMode): Int {
    if (c !is FlowTree || c.layer != Layer.NORMAL || !mode.run1 || !statementEdge) return -1
    if (c.base != STATIC || ce.fromBase != STATIC) return -1
    val i = premise as? InitialAp ?: return -1                                                  // a FLOW premise is one InitialAp (K6)
    if (i.base != STATIC || i.tail != Tail.STAR) return -1
    val q = i.pathArray
    if (!(q.isEmpty() || (q.size == 1 && q[0].isClass()))) return -1                            // rootOrClass(q)
    if (ce.fromPath.size <= q.size || !ce.fromPath.startsWith(q)) return -1                      // case `above`
    return q.size
}
```

THE MARK GATE. One class for `applyCompiledEdge` (micro edges, summaries, records), `checkMark` (sinks, literals) and
`satisfying` (`markSub`):

```kotlin
/** ap.md §4.1 steps 4 and 5, per kind (§7.3; Lean markGate, markComp). `premise` is never `*∖X` (S7). */
internal class MarkGate(private val m: ApManager, val premise: MarkSlot, val target: MarkSlot) {
    init {
        check(premise !is MarkSlot.Star || premise.excluded.isEmpty)                               // S7
        check(premise is MarkSlot.Concrete || target is MarkSlot.Star)                             // S7: a concrete target needs a concrete premise
    }

    /** FLOW input with the mark `*∖x`: the mark exclusion of the result (a `*` premise passes every mark; a `*∖Y` target
     *  adds Y), or null: a concrete premise mark gives no fact (§7.3). */
    fun flowMarks(x: MarkSet): MarkSet? =
        if (premise is MarkSlot.Concrete) null else m.intern(x + (target as MarkSlot.Star).excluded)

    /** FLOW input under a concrete premise mark T: the request T (run 1), or null if `*∖x` excludes T (cleaned). */
    fun flowRequest(x: MarkSet): TaintMark? = (premise as? MarkSlot.Concrete)?.mark?.takeIf { it !in x }

    /** TAINT or REACH input: the result marks of the concrete marks `ms`, or null (no fact). */
    fun taintMarks(ms: MarkSet): MarkSet? {
        if (ms.isEmpty) return null
        val passed = when (premise) {
            is MarkSlot.Concrete -> if (premise.mark in ms) m.markSetOf(premise.mark) else return null   // T' ≠ T
            is MarkSlot.Star -> ms                                                                     // `*` passes every mark
        }
        val result = when (target) {
            is MarkSlot.Concrete -> m.markSetOf(target.mark)
            is MarkSlot.Star -> m.intern(passed - target.excluded)                                    // a mark in Y: no fact
        }
        return result.takeIf { !it.isEmpty }
    }
}
```

THE EDGE APPLICATION. `applyCompiledEdge` is the tree form of `concat` (`ap.md` §4.1, "delta-concat"; the reference
`concat` of `Reference.kt`): it computes, for every fact of `c`, the part that the premise selects (the case `below` or
`above`) and concatenates it with the target. One walk (`walk`, §4.6), one gate (`MarkGate`), one collector
(`Results`), per kind.

THE ANY TAILS (`ap.md` §4.1, W6, W8; DD16). A micro edge with an `[any]` target is a MAY (a pass rule): every result of
it is in the demand layer (W6). A micro edge with an `[any-taint]` target is a MUST (a taint edge, `ap.md` S15): its
result keeps the layer of its input, with the Empty exclusion. An any leaf of a NORMAL TAINT tree `c` is `[any-taint]`
with the tree exclusion `E`: every location below it, less the excluded part, carries its mark, so its case `above`
loses nothing. THE EXCLUSION ROWS (`ap.md` §4.1, A2; Lean `AnyTaintEx.annX`) give its results, and none of them demotes:
* the case `above` (the leaf at `q`, the premise at `q ++ r`): only if `E` admits `r`, else nothing (no common
  location; a read `y = c.P.g` with `g ∈ E`). A `*` target gives `[any-taint]` with the exclusion of the EDGE, in the
  layer of `c` (a read: `{}`; the keep edge `x.f.* →_{g} x.f.*`: `{g}`); a `$` target gives `$`; an `[any-taint]` target
  gives `[any-taint]` with the target exclusion (a source: `{}`); an `[any]` target gives `[any]` in the demand layer (W6);
* the case `below`, `r = []` (the leaf at the premise path): a `*` target gives `[any-taint]` with `E ∪` the exclusion of
  the edge, in the layer of `c`: the keep edge of a strong write (`dto.setName(c)`: `{name}`), a `*/E'` summary or record;
* the case `below`, `r ≠ []`: a `*` target keeps `E` at the new path end `to.path ++ r` (a copy of the object);
* a target that is not `*` folds the subtree as before: `$`, `[any-taint]` with the target exclusion, or `[any]` (W6).
A demand result (a demand input, a demand summary, a may edge) has no exclusion: the normal form of the tree (§4.1).
Lean: `AnyTaintEx.applyEdgeX` (the base `applyEdge`, then `annX` and `layerX`), then `w6tX`; the vectors
`AnyTaintEx.Vec.setter_keep` (the base demotes: `setter_keep_base`), `read_excluded`, `read_admitted`, `above_star_excl`,
`below_keeps`; soundness `AnyTaintExCov.applyEdgeX_sound`; exactness of the rows `AnyTaintExExact.keep_row_exact`,
`above_row_exact`, `below_row_exact`. The test of `E` in the case `above` is necessary (`AnyTaintExExact.CexAbove.cex_above`:
a setter, then a read of the written field).

```kotlin
/** ap.md §7.3 steps 1 to 5 and 7 on all paths of `c` at once. Step 6 (the field limit) is not here: the caller cuts at the
 *  cut points of §4.4 (Part I §5.7). Cost: |from.path| + 1 nodes for the walk, the subtree U for the transform
 *  (Tree.walkSteps_le). */
internal fun ApOps.applyCompiledEdge(c: Facts, ce: CompiledEdge, edgeDemand: Boolean, staticAt: Int, mode: ApMode, out: ApOut) {
    if (c.base != ce.fromBase) return                                                       // §4.1 step 1
    val app = EdgeApplication(manager, ce, edgeDemand, mode, out)
    when (c) {
        is Reach -> app.reach(c)
        is FlowTree -> app.flow(c, staticAt)
        is TaintTree -> app.taint(c)
    }
    app.results.flush(out)
}

internal class EdgeApplication(private val m: ApManager, private val ce: CompiledEdge, private val edgeDemand: Boolean,
                               private val mode: ApMode, private val out: ApOut) {
    val results = Results(m, ce.toBase)
    private val gate = MarkGate(m, ce.fromMark, ce.toMark)
    private val admitsRest = { a: AccessorIdx -> when (ce.fromTail) {                                    // §4.1 step 3, row 1
        Tail.EXACT -> false; Tail.ANY_TAINT -> ce.fromExclusion.admits(a); else -> ce.exclusion.admits(a) } }   // Ej of a must-premise
    private val aboveTail = if (ce.toTail == Tail.EXACT) Tail.EXACT else Tail.ANY                        // case `above`
    private val mustAboveTail = when (ce.toTail) {                                                      // ... of `[any-taint]`:
        Tail.EXACT -> Tail.EXACT; Tail.ANY -> Tail.ANY                                                   // `$`; a may `[any]` (W6)
        Tail.STAR, Tail.ANY_TAINT -> Tail.ANY_TAINT                                                      // a `*` or a must target
    }
    private val aboveExclusion = if (ce.toTail == Tail.ANY_TAINT) ce.toExclusion else ce.exclusion       // A2: the edge's
    private var requested = false

    /** REACH (§7.3): only an edge from the zero fact applies. The zero keep edge and the zero binding `zero.* -> zero.*`
     *  (ap.md §3.5: a binding has the premise mark `*`) give REACH, a source gives TAINT: `zero.$ (zeroMark) -> P.[any-taint] (T)`
     *  (an `[any]`-target source, S15; the Spring DTO source, Part II §29) gives a normal `[any-taint]` leaf with the Empty
     *  exclusion. A ZERO-PREMISE SUMMARY OR RECORD with an `[any-taint]/Et` leaf (its kind edge, §5.4 `leafKinds`, has
     *  `toExclusion = Et`) gives `[any-taint]/Et` (ap.md §4.1 case `below`: the target exclusion `edge.toExclusion`; Lean
     *  AnyTaintEx.applySummaryX, the vector AnyTaintEx.Vec.summary_ann): `mk() { d = srcAny(); d.setName(c); return d; }`
     *  gives the caller `(r, ., [any-taint], {name}, T)`, so `sink(r.name)` is not reported. `put` keeps the exclusion
     *  only for an `[any-taint]` result, and `Results` drops it in the demand layer. */
    fun reach(c: Reach) {
        check(ce.fromPath.isEmpty() && !ce.fromTail.isAny)           // `zero.$ (zeroMark)`, or the zero binding `zero.* (*)`
        gate.taintMarks(m.markSetOf(TaintMark.ZERO))?.let { put(c.layer or edgeDemand, ce.toTail, it, ce.toExclusion) }
    }

    /** FLOW: the flag of a node is `p.*/Ec` (normal) or `p.[any]` (demand), with the mark `*∖Xc`. */
    fun flow(c: FlowTree, staticAt: Int) {
        val mx = gate.flowMarks(c.markExclusion)                     // null: a concrete premise mark; a hit is a request
        check(mx == null || (ce.toTail != Tail.EXACT && ce.toTail != Tail.ANY_TAINT))   // S8, W8: a `$` or `[any-taint]` target
                                                                     // has a concrete premise mark, so FLOW gives no `[any-taint]`
        val normal = c.layer == Layer.NORMAL
        val layer = c.layer or edgeDemand
        val u = c.root.walk(ce.fromPath) { d, n ->                   // §7.3 step 1: the case `above`
            if (n.leaf.isEmpty || (normal && !c.exclusion.admits(ce.fromPath[d]))) return@walk    // Tree.cS: `*/Ec` admits r
            if (normal && d == staticAt) out.positionRequest(m.position(ce.fromPath))               // §4.10 item 1: no fact, no request
            else flowHit(mx, c) { results.flow(Layer.DEMAND, ExclusionSet.Empty, it, flagAtTarget()) }  // `[any]` at to.path
        } ?: return
        val kids = FlowAlgebra.retainChildren(u, admitsRest)          // the case `below`, r ≠ []
        when (ce.toTail) {
            Tail.STAR -> {
                if (kids != null) flowHit(mx, c) {                   // r ≠ []: `to.path ++ r`, the tail of c with Ec
                    results.flow(layer, c.exclusion, it, FlowAlgebra.prepend(m, ce.toPath, kids)) }
                if (!u.leaf.isEmpty) flowHit(mx, c) {                // r = []: `*/(Ec ∪ E)` (normal); `[any]` (demand, W6)
                    results.flow(layer, c.exclusion.union(ce.exclusion), it, flagAtTarget()) }
            }
            else -> if (kids != null || !u.leaf.isEmpty)             // fold U: `[any]` (W6); `$`, `[any-taint]`: only the request
                flowHit(mx, c) { results.flow(Layer.DEMAND, ExclusionSet.Empty, it, flagAtTarget()) }
        }
    }

    /** One FLOW result (the premise mark `*`), or the request of a concrete premise mark: the gate comes after the
     *  position test, so an apart fact gives no request (§4.1 step 4). */
    private inline fun flowHit(mx: MarkSet?, c: FlowTree, add: (MarkSet) -> Unit) {
        if (mx != null) { add(mx); return }
        val t = gate.flowRequest(c.markExclusion) ?: return                     // `*∖X` with T ∈ X: cleaned, no request
        if (mode.run1 && !requested) { out.markRequest(t); requested = true }
    }

    /** TAINT: at a node the marks of the `$` leaves and of the any leaves. An
     *  `[any-taint]` target is a taint edge with a concrete premise mark (S15). `must`: the any leaves of c are
     *  `[any-taint]` with the tree exclusion `e` (a normal tree, DD16); in a demand tree they are `[any]` and `e` is Empty.
     *  THE EXCLUSION ROWS (A2, above): no row demotes; `E` filters the case `above`, and goes to the results. */
    fun taint(c: TaintTree) {
        val layer = c.layer or edgeDemand
        val must = c.layer == Layer.NORMAL
        val e = c.exclusion
        val u = c.root.walk(ce.fromPath) { d, n ->                    // the case `above`: an any leaf admits r, a `$` leaf does not
            if (!e.admits(ce.fromPath[d])) return@walk                 // A2: E excludes the step down to the premise: no location
            gate.taintMarks(n.leaf.any)?.let {
                if (must) put(layer, mustAboveTail, it, aboveExclusion) // §4.1, the `[any-taint]` rows of the case `above`
                else put(Layer.DEMAND, aboveTail, it)                  // an `[any]` fact is demand already (W6)
            }
        } ?: return
        when (ce.toTail) {
            Tail.STAR -> {
                m.taintAlg.retainChildren(u, admitsRest)?.let { kids ->          // r ≠ []: `to.path ++ r`, the tails of c, E kept
                    gateTree(kids)?.let { results.taint(layer, m.taintAlg.prepend(m, ce.toPath, it), e) } }
                gate.taintMarks(u.leaf.exact)?.let { put(layer, Tail.EXACT, it) }            // r = [], `$`
                gate.taintMarks(u.leaf.any)?.let {                                         // r = [], an any leaf of c:
                    if (must) put(layer, Tail.ANY_TAINT, it, e.union(ce.exclusion))        // `[any-taint]/(E ∪ E')`, normal (A2)
                    else put(Layer.DEMAND, Tail.ANY, it)                                   // `[any]` (W6)
                }
            }
            else -> {                                                            // fold U into one any leaf or one `$` leaf:
                val all = m.taintAlg.foldLeaves(u, admitsRest)                   // `[any]` (a may: demand, W6, in `put`),
                gate.taintMarks(all.exact + all.any)?.let { put(layer, ce.toTail, it, ce.toExclusion) }   // `[any-taint]`, `$`
            }
        }
    }

    /** One TAINT leaf at to.path. A target on the zero base is the zero fact: REACH (a reversed source, ap.md §9.2).
     *  W6 HERE: a result with the tail `[any]` (a may) is in the demand layer. `[any]` and `[any-taint]` are the same any
     *  leaf: the layer of the result names it (W8 (b): a demand `[any-taint]` result is `[any]`, with no exclusion).
     *  `exclusion`: the exclusion of an `[any-taint]` result (A2); `Results` drops it in the demand layer. */
    private fun put(layer: Layer, tail: Tail, ms: MarkSet, exclusion: ExclusionSet = ExclusionSet.Empty) {
        val l = if (tail == Tail.ANY) Layer.DEMAND else layer                                // W6
        if (ce.toBase == AccessPathBase.Zero) { results.reach(l); return }
        val leaf = when (tail) {
            Tail.EXACT -> m.taintLeaves(ms, MarkSet.EMPTY)
            Tail.ANY, Tail.ANY_TAINT -> m.taintLeaves(MarkSet.EMPTY, ms)
            Tail.STAR -> error("S8: a concrete mark has no `*` leaf (W2)")
        }
        results.taint(l, m.taintAlg.prepend(m, ce.toPath, m.taintAlg.leafNode(leaf)),
            if (tail == Tail.ANY_TAINT) exclusion else ExclusionSet.Empty)
    }

    /** The gate on every leaf of a TAINT subtree. No copy for `*` -> `*` (the common case). */
    private fun gateTree(n: TaintNode): TaintNode? =
        if (ce.fromMark == MarkSlot.STAR && ce.toMark == MarkSlot.STAR) n
        else m.taintAlg.mapLeaves(n) { p -> m.taintLeaves(gate.taintMarks(p.exact) ?: MarkSet.EMPTY, gate.taintMarks(p.any) ?: MarkSet.EMPTY) }

    private fun flagAtTarget(): FlowNode = FlowAlgebra.prepend(m, ce.toPath, FlowAlgebra.LEAF_NODE)
}
```

`Tree.applyTreeE_mem`, `applyTreeE_den` prove this walk for a `*`-to-`*` edge with the mark `*` on both sides (a FLOW
tree). The gate, the static exception, the TAINT kind and the other target tails (also the `[any-taint]` rows of the
cases `below` and `above` with the exclusion, and step 6 with W6 and W8) are the per-path rows of `ap.md` §4.1 applied
per leaf. The test `FactsEquivalenceTest` (§8) compares every case with the reference `concat`.

The rows of `ap.md` §4.2 and `interpreter.md` §2.4 with an `[any-taint]` fact, on the tree form (Lean
`AnyTaintEx.Vec.setter_keep`, `read_admitted`, `read_excluded`, `below_keeps`; `AnyTaintExCases.X.two_results`):

```kotlin
// a = b.f on the normal tree {b: [any-taint] (T)}, exclusion E: the walk of [f] meets the any leaf above the premise:
//   b.f.* -> a.*   : the case `above`; f ∉ E, a `*` target       -> a.[any-taint]/{} (T), normal (the edge has no exclusion)
//                    f ∈ E                                         -> nothing: no location of b is at or below b.f
//   b.* -> b.*     : the case `below`, r = []                      -> b.[any-taint]/E (T), normal
// a.f = b on the normal tree {a: [any-taint] (T)}, exclusion E (the keep edge a.* ->_{f} a.*, I3):
//   a.* ->_{f} a.* : the case `below`, r = [], a `*` target       -> a.[any-taint]/(E ∪ {f}) (T), NORMAL: no demotion (A2)
// y.h = c on the normal tree {c: [any-taint] (T)}, exclusion E (the copy c.* -> y.h.*):
//   c.* -> y.h.*   : the case `below`, r = []                      -> y.h.[any-taint]/E (T), normal: E stays at the new end
// x.f.g = v on the normal tree {x: [any-taint] (T)}, exclusion {} (the keep edges x.* ->_{f} x.*, x.f.* ->_{g} x.f.*):
//   x.* ->_{f} x.*     : the case `below`, r = []                  -> x.[any-taint]/{f} (T), normal
//   x.f.* ->_{g} x.f.* : the case `above`, a `*` target           -> x.f.[any-taint]/{g} (T), normal: two result trees
// a pass rule P.$ (T) -> Q.[any] (T) on {P: $ (T)}                -> put(.., ANY): Q.[any] (T), demand (W6, a may)
// a source    P.$ (T) -> Q.[any-taint] (T) on {P: $ (T)}          -> put(NORMAL, ANY_TAINT): Q.[any-taint]/{} (T), normal
```

### 5.4 `satisfying`, `applySummary`, `applyCombination` (`ap.md` §4.3, §4.6; DD4)

`satisfying` reads the leaves of a trie as tails (`ap.md` §3.4 `tailSub`, `tailAdmits`). For a restricted canonical
FLOW premise `(base, path, STAR, Empty, *)`, satisfaction is location overlap, including the added fact's exclusion
(`CurrentSimplifications.Flow.canonical_flow_satisfaction_X`). The tree implementation below retains the union of
`applicablePart` and `insidePart`, which computes that predicate per leaf. The shortcut does not apply to a concrete
record reader, a nonempty premise exclusion or a restricted concrete mark. No trie lookup or tree walk changes.
The kind gives the tails:

```kotlin
/** The tails of the leaves of one trie: a FLOW flag is `*/Ea` (normal) or `[any]` (demand); a TAINT node has `$` and
 *  any leaves (`[any-taint]` in a normal tree, `[any]` in a demand tree: `any` selects both, K7). `exclusion`: of a `*`
 *  flag; `anyExclusion`: of an any leaf (the exclusion of a normal TAINT tree, A2; Empty otherwise). `select` keeps the
 *  leaves of the accepted tails. */
private class Tails<P : TrieLeaf>(val exclusion: ExclusionSet, val anyExclusion: ExclusionSet = ExclusionSet.Empty,
                                  val select: (P, star: Boolean, any: Boolean, exact: Boolean) -> P)

private fun flowTails(a: FlowTree) = Tails<FlowLeaf>(a.exclusion) { p, star, any, _ ->
    if (if (a.layer == Layer.NORMAL) star else any) p else FlowLeaf.NONE }
private fun ApOps.taintTails(a: TaintTree) = Tails<TaintLeaves>(ExclusionSet.Empty, a.exclusion) { p, _, any, exact ->
    manager.taintLeaves(if (exact) p.exact else MarkSet.EMPTY, if (any) p.any else MarkSet.EMPTY) }

/** The part of the added fact `a` whose facts satisfy `j`: `applicable` (run 1), `inside` (restricted concrete),
 *  overlap (restricted canonical FLOW, computed by the same applicable/inside union), both (a record,
 *  §8.7 R4). ONE function for the replay and the delivery (analyzer-core.md P4).
 *  A MUST-PREMISE `j` (`[any-taint]` with its exclusion `Ej`; forward restricted runs only, W8 (c)) is satisfied only by
 *  `inside` (§4.3; Lean AnyTaint.SatInside, satI_inside): `a` covers every location of `j`, so it is an any leaf at or
 *  above `j`. A normal result also needs a normal link: an `[any]` added fact is in the demand tree, so it gives demand
 *  results (the layer of `a` is the link layer, Part I §7.5).
 *  THE EXCLUSION (A2; Lean AnyTaintEx.satX = satI and insideExB, satX_inside): `inside` reads the exclusion `E` of an
 *  `[any-taint]` leaf of `a`: a leaf strictly above `j` at `j.path = q ++ r` only if `E` admits `r`; a leaf at `j.path`
 *  only if `E ⊆ Ej` (a `$` `j`: always). `applicable` (run 1, the records) is the base rule (AnyTaintEx.DRX: `retRec`);
 *  the edge application then reads both exclusions (§5.3). The part keeps the exclusion of `a`.
 *  A MUST RECORD (`record = true` with an `[any-taint]` `j`) gives the whole part, `applicable` or `inside` (R4). THE
 *  RECORD DEMOTION (§4.3; reference `recordDemand`, Part I §6) is the caller's, in a forward run (the backward run has no
 *  must record): the part that `satisfying(a, j, mode)` (`inside`) gives has the result of the application, and the rest
 *  of the part (`ApOps.without`, `applicable` only) gives its facts in the demand layer, with no exclusion (Lean
 *  AnyTaint.recLayer, recLayer_fact, AnyTaintEx.recLayerX, or a superset of them: THE EARLY RAISE, below; necessary:
 *  AnyTaintExact.CexApp.cex_app; analyzer-impl.md `applyRecord`). */
fun ApOps.satisfying(a: Facts, j: InitialAp, mode: ApMode, record: Boolean = false): Facts? =
    satisfying(a, j.toPattern(), mode, record)

/** Transient concrete STAR/E record readers are Patterns, never InitialAp values. */
fun ApOps.satisfying(a: Facts, j: Pattern, mode: ApMode, record: Boolean = false): Facts? {
    if (a.base != j.fact.base) return null
    check(j.fact.tail != Tail.ANY_TAINT || (mode.restricted && mode.direction == Direction.FORWARD)) { "W8 (c): a must-premise is forward restricted" }
    val below = record || mode.run1 || (mode.restricted && j.fact.mark is MarkSlot.Star)   // applicable: FLOW also in restricted runs
    val inside = record || mode.restricted                                   // inside (satI): j at or below a
    val m = manager
    return when (a) {
        is Reach -> a.takeIf { j.fact.base == AccessPathBase.Zero }                                    // applicable(zero, zero), inside(zero, zero)
        is FlowTree -> if (j.fact.mark !is MarkSlot.Star) null                    // markSub(T, *∖X) is false: see "Run 1" below
            else part(a.root, FlowAlgebra, flowTails(a), j, below, inside)?.let { m.flowTree(a.base, a.layer, a.exclusion, a.markExclusion, it) }
        is TaintTree -> part(a.root, m.taintAlg, taintTails(a), j, below, inside)
            ?.let { markSubPart(it, j) }?.let { m.taintTree(a.base, a.layer, it, a.exclusion) }
    }
}

/** markSub(j.fact.mark, T) on every leaf of a TAINT part. */
private fun ApOps.markSubPart(n: TaintNode, j: Pattern): TaintNode? = MarkGate(manager, j.fact.mark, MarkSlot.STAR).let { g ->
    manager.taintAlg.mapLeaves(n) { p -> manager.taintLeaves(g.taintMarks(p.exact) ?: MarkSet.EMPTY, g.taintMarks(p.any) ?: MarkSet.EMPTY) } }

private fun <P : TrieLeaf> ApOps.part(root: TrieNode<P>, alg: LeafAlgebra<P>, t: Tails<P>, j: Pattern, below: Boolean, inside: Boolean): TrieNode<P>? {
    val b = if (below) applicablePart(root, alg, t, j) else null
    val i = if (inside) insidePart(root, alg, t, j) else null
    return if (b == null) i else if (i == null) b else alg.mergeAdd(b, i)
}

/** applicable(j, leaf) (ap.md §3.4): j covers the leaf, and a premise with an any tail needs a fact with an any tail
 *  (K7: `[any-taint]` as `[any]`). ADAPT of AccessNode.filterStartsWith (AccessTree.kt:1329): walk j.path, take the
 *  subtree, rebuild the chain. THE PREMISE EXCLUSION `Ej` (a `*/Ej` premise; a must record `[any-taint]/Ej`, R4) is read
 *  as the reference reads it (`tailSub`, `tailAdmits`): at j.path an any leaf of `a` only if `Ej ⊆ Ea` (`t.anyExclusion`:
 *  Ea of a normal TAINT tree; Empty for an `[any]` leaf, so there `Ej = {}`, as `tailSub` says), below j.path only
 *  through a first accessor that `Ej` admits. An `[any]` premise has no exclusion. */
private fun <P : TrieLeaf> ApOps.applicablePart(root: TrieNode<P>, alg: LeafAlgebra<P>, t: Tails<P>, j: Pattern): TrieNode<P>? {
    val u = root.walk(j.fact.path.toIntArray()) ?: return null
    val ej = j.exclusion
    val anyAt = ej.isSubsetOf(t.anyExclusion)                                 // tailSub(j, any leaf): Ej ⊆ Ea
    val at = when (j.fact.tail) {                                                  // r = []: tailSub(j, leaf)
        Tail.STAR -> t.select(u.leaf, ej.isSubsetOf(t.exclusion), anyAt, true)
        Tail.EXACT -> t.select(u.leaf, false, false, true)
        Tail.ANY_TAINT -> t.select(u.leaf, false, anyAt, false)              // an any leaf only (applicable), Ej ⊆ Ea
        Tail.ANY -> t.select(u.leaf, false, true, false)
    }
    val kids = when (j.fact.tail) {                                                // r ≠ []: j.fact.tail admits r
        Tail.STAR -> alg.retainChildren(u) { ej.admits(it) }
        Tail.EXACT -> null
        Tail.ANY_TAINT -> alg.retainChildren(u) { ej.admits(it) }?.let { k -> alg.mapLeaves(k) { t.select(it, false, true, false) } }
        Tail.ANY -> alg.retainChildren(u) { true }?.let { k -> alg.mapLeaves(k) { t.select(it, false, true, false) } }
    }
    return alg.withLeaf(kids, at)?.let { alg.prepend(manager, manager.path(j.fact.path), it) }
}

/** inside(j, leaf) (ap.md §3.4, Lean satI; with the exclusions AnyTaintEx.satX): j lies inside the leaf as locations.
 *  Such a leaf is at or above j.path. The only test of a must-premise (an `[any-taint]` j: an any leaf at or above it,
 *  ap.md §4.3). An `[any-taint]/E` leaf: above j only if E admits the step down, at j only if E ⊆ Ej (A2). */
private fun <P : TrieLeaf> ApOps.insidePart(root: TrieNode<P>, alg: LeafAlgebra<P>, t: Tails<P>, j: Pattern): TrieNode<P>? {
    val p = j.fact.path.toIntArray()
    val spine = ArrayList<P>(p.size + 1)
    val u = root.walk(p) { d, n -> spine += t.select(n.leaf, t.exclusion.admits(p[d]), t.anyExclusion.admits(p[d]), false) }   // above j
    if (u != null) spine += t.select(u.leaf,                                                              // at j: tailSub(leaf, j)
        star = when (j.fact.tail) {
            Tail.EXACT -> true; Tail.STAR -> t.exclusion.isSubsetOf(j.exclusion)
            Tail.ANY, Tail.ANY_TAINT -> t.exclusion == ExclusionSet.Empty
        },
        any = j.fact.tail == Tail.EXACT || t.anyExclusion.isSubsetOf(j.exclusion), exact = j.fact.tail == Tail.EXACT)
    return alg.chain(manager, p, spine)
}
```

THE RECORD DEMOTION ON THE TREE (`ap.md` §4.3; Lean `AnyTaint.recLayer`, `AnyTaintEx.recLayerX`). For a must record
`j -> g` (`j` is `[any-taint]`, a forward record, §8.7) and its satisfying part `a = satisfying(a0, j, mode, record =
true)`, the per-path reference `recordDemand` (`ap.md` §6.3, Part I §6) is true on a leaf exactly when the leaf is in
`only`:

```kotlin
// forward:  inside = satisfying(a, j, mode)           (the any leaves of `a` at or above j.path that j lies inside, with
//                                                       the exclusions: the result as applied)
//           only   = without(a, inside)               (the other leaves: `applicable` only -> DEMAND)
// The backward run has no must record (A1), so no backward row. The results of `only` move to the demand tree with
// the same roots and no exclusion (the fact does not change, Lean recLayer_fact; an any leaf of a demand tree is
// `[any]`, W8 (b)). Necessary (AnyTaintExact.CexApp.cex_app): `get(p) { ret = p.g }`,
// the record `(p, ., [any-taint], T) -> (ret, ., [any-taint], T)`, applied to `(p, .f, [any-taint], T)` (the source
// `dto.f.[any-taint] (T)`), would give a normal `(x, ., [any-taint], T)` at `x = get(dto)`, but `p.g` carries no taint.
```

The analyzer does the split and the raise at the end point of its callees stage (`analyzer-impl.md` `applyRecord`). A
summary (not a record) of a must-premise needs no demotion: a restricted run reads it by `inside` only. THE EARLY RAISE
IS A SUPERSET: the model raises after the binding back and before the field limit (Lean `AnyTaintEx.DRX` `retRec`:
`limitFX counted L (recLayerX mj (sat …) r')`; the two commute). The analyzer raises before the rewriter, the binding
back and the cut, so these steps see a demand tree with no exclusion: `EdgeApplication.taint` reads the exclusion and
the must flag of a normal tree only, and `TaintClean` names the any leaf by the layer (for example a `below` cleaner one
accessor below gives `[any]` alone, which covers the `(x, p.f, $, T)` that the model adds). So the result is the facts
of `recLayerX` or a SUPERSET of them, all in the demand layer: sound (an exclusion only removes locations), not equal.

RUN 1: A SUMMARY WITH A CONCRETE PREMISE MARK AND A `*` FACT IN THE CALLER. Let `j = (x, [], *, {}, T)` or `(x, [], $, {}, T)` (a chain answer of the policy fact) and
let the added fact be a FLOW tree `a` (mark `*∖X`, `T ∉ X`). The summaries of `j` are TAINT (a concrete premise has
concrete conclusions, `Coverage.edge_conc`):

```kotlin
satisfying(a, j, run1)        // null: `applicable` needs markSub(T, *∖X), which is false. So applySummary never runs:
                              // a FLOW added fact never meets a TAINT summary, and the application raises NO request
                              // (Coverage.summary_step: a summary application never requests).
// The callee raised the standing request (m, i, T) when its rule met its policy fact i (§4.5, a FLOW fact). For the
// link of this caller (the added fact a: mark `*`, T not excluded) requestAction gives Climb(callerPremise, Mark(T))
// (Part I §5.10): the caller premise gets the request, and its own callers answer it. The FLOW fact a itself satisfies
// the policy fact i (`*`), and gets the FLOW summary of i.
```

`applySummary` is `concat(a, j -> g)` with `edgeDemand` = the layer of `g`, on every leaf of `g`. The leaves of one
(tail, mark) kind of `g` differ only by their path, so ONE `applyCompiledEdge` with `to.path = []` gives the result of
every such leaf, and `graft` puts it at each path (ADAPT of `concatToLeafAbstractNodes`, `AccessTree.kt:1281`, which puts
a caller delta under every abstract node of a summary):

```kotlin
/** ap.md §4.3, by kind (§7.2): FLOW a × FLOW g -> FLOW; TAINT a × FLOW g -> TAINT (a run-1 record with a `*` premise is a
 *  transfer function); TAINT a × TAINT g -> TAINT; TAINT a × REACH g -> REACH (backward: a requirement reached a source);
 *  REACH a × (REACH | TAINT) g -> the kind of g. FLOW a × TAINT g never occurs (above). Precondition: a = satisfying(...). */
fun ApOps.applySummary(a: Facts, j: InitialAp, g: Facts, mode: ApMode, out: ApOut) {
    check(a !is FlowTree || g is FlowTree) { "a FLOW fact satisfies only a `*` premise, whose summaries are FLOW" }
    for (kind in leafKinds(g, j)) {
        val one = CollectingOut()
        applyCompiledEdge(a, kind.edge, edgeDemand = g.layer == Layer.DEMAND, staticAt = -1, mode, one)
        check(one.markRequests.isEmpty())                                  // Coverage.summary_step
        for (r in one.results) out.result(kind.graft(r))
    }
}

/** Apply the satisfying record=true part directly, without InitialAp or startFact.
 *  STAR/E -> exact folds every admitted input suffix to the old exact premise path. */
fun ApOps.applyRecordLeaf(a: Facts, view: RecordLeafView, mode: ApMode, out: ApOut) {
    val e = summaryEdge(view.premise, view.result)
    val ce = e.compiled ?: manager.compile(e).also { e.compiled = it }
    val one = CollectingOut()
    applyCompiledEdge(a, ce, edgeDemand = view.result.demand, staticAt = -1, mode, one)
    check(one.markRequests.isEmpty() && one.positionRequests.isEmpty())
    for (r in one.results) out.result(r)
}

/** One (tail, mark) kind of the leaves of g: the edge `j -> (tail, mark)` at the empty path, and the graft of a result into
 *  the paths of g with that kind. */
private class LeafKind(val edge: CompiledEdge, val graft: (Facts) -> Facts)

private fun ApOps.leafKinds(g: Facts, j: InitialAp): List<LeafKind> {
    val m = manager
    val jOwn = j.tail == Tail.ANY_TAINT                                           // a must-premise: Ej is its own exclusion
    fun edge(tail: Tail, mark: MarkSlot, excl: ExclusionSet, toExcl: ExclusionSet = ExclusionSet.Empty) =   // ap.md §4.1 PathEdge
        CompiledEdge(j.base, j.pathArray, j.tail, j.mark, g.base, null, tail, mark,
            m.intern(if (jOwn) ExclusionSet.Empty else excl), m.intern(if (jOwn) j.exclusion else ExclusionSet.Empty), m.intern(toExcl))
    return when (g) {
        is Reach -> listOf(LeafKind(edge(Tail.EXACT, MarkSlot.Concrete(TaintMark.ZERO), ExclusionSet.Empty)) { it })   // the zero fact
        is FlowTree -> listOf(                                                     // one kind: the flag, the mark `*∖Xg`
            if (g.layer == Layer.NORMAL) LeafKind(edge(Tail.STAR, MarkSlot.Star(g.markExclusion), j.exclusion.union(g.exclusion))) { graft(g.root, { it == FlowLeaf.LEAF }, it) }
            else LeafKind(edge(Tail.ANY, MarkSlot.Star(g.markExclusion), j.exclusion)) { graft(g.root, { it == FlowLeaf.LEAF }, it) })
        is TaintTree -> {                                                          // one kind per mark of the `$` and the any leaves
            val all = m.taintAlg.foldAll(g.root)
            // An any leaf of a NORMAL summary is `[any-taint]/Eg`: its kind edge has the must target ANY_TAINT with the
            // target exclusion Eg (`toExclusion`; Lean the `tex` of AnyTaintEx.applySummaryX) and keeps the layer of the
            // added fact (§4.1); of a DEMAND summary it is `[any]` (edgeDemand gives the demand layer anyway).
            val anyTail = if (g.layer == Layer.NORMAL) Tail.ANY_TAINT else Tail.ANY
            all.exact.ids.map { t -> LeafKind(edge(Tail.EXACT, MarkSlot.Concrete(TaintMark(t)), j.exclusion)) { graft(g.root, { TaintMark(t) in it.exact }, it) } } +
            all.any.ids.map { t -> LeafKind(edge(anyTail, MarkSlot.Concrete(TaintMark(t)), j.exclusion, g.exclusion)) { graft(g.root, { TaintMark(t) in it.any }, it) } }
        }
    }
}

/** A result `r` (computed at the empty path) at every node of g whose leaf `occurs` holds; no other leaf of g. REACH has no
 *  path. An identity memo over g keeps shared subtrees of g shared. */
private fun <G : TrieLeaf> ApOps.graft(g: TrieNode<G>, occurs: (G) -> Boolean, r: Facts): Facts = when (r) {
    is Reach -> r
    is FlowTree -> r.withRoot(FlowAlgebra.graft(g, occurs, r.root)!!)
    is TaintTree -> r.withRoot(manager.taintAlg.graft(g, occurs, r.root)!!)
}
```

`LeafAlgebra<R>.graft(g: TrieNode<G>, occurs, r: TrieNode<R>)` (`TrieOps.kt`, code in §4.2) is generic over the kind
`G` of the summary and the kind `R` of the result (TAINT a × FLOW g grafts a TAINT result into a FLOW skeleton). It
keeps an identity memo over `g`. At an occurring node with occurrences below it, it MERGES `r` with the grafts below
(`mergeAdd`), as today: `ap.md` §4.3 is the union over every leaf of `g`.

Event E6 applies a summary with several premises to one full combination (`ap.md` §4.6 "at a call", `ap.md` §4.3; Lean
`ND.DN.ndBind`). The caller keeps the combination (`analyzer-core.md` §5.4). For each member `jm`, it gives the
satisfying part of the added fact of its link, `satisfying(a, jm, mode)`. The layer of that `Facts` IS the layer of the
added fact on the link (§7.5: `add` asserts `linkLayer == added.layer`). So the link layer enters through `part.layer`,
and the same `applySummary` of E2 and E4 gives the layer of each member:

```kotlin
/** One full combination: parts[m] = (the satisfying part for member m, the member jm). The summary has a PremiseSet, so
 *  its conclusion g is TAINT with no `*` tail (W7, ap.md §4.6) and every part is TAINT (a concrete member is satisfied
 *  only by a concrete added fact). Every member application gives the leaves of g (an uncorrelated target puts the result at
 *  to.path with the tail and the mark of the target, §4.1 step 3); only its LAYER differs. The result is in the demand
 *  layer if g is, if one part is demand on its link, or if one application moves it there (case `above`). The analyzer
 *  gives the result the union of the premise sets of the caller edges (ApManager.union), binds it back and cuts it. */
fun ApOps.applyCombination(parts: List<Pair<Facts, InitialAp>>, g: TaintTree, mode: ApMode, out: ApOut) {
    check(parts.size >= 2 && parts.all { it.first is TaintTree })               // ap.md §4.6: an ND edge is TAINT
    var normal = g.layer == Layer.NORMAL                                         // a choice of leaves with every member normal
    var demand = g.layer == Layer.DEMAND                                         // a choice with one member in the demand layer
    for ((part, j) in parts) {
        val one = CollectingOut()
        applySummary(part, j, g, mode, one)
        check(one.markRequests.isEmpty())                                        // satisfying: the gate passes (C4)
        if (one.results.isEmpty()) return                                        // not a full combination
        normal = normal && one.results.any { it.layer == Layer.NORMAL }
        demand = demand || one.results.any { it.layer == Layer.DEMAND }
    }
    if (normal) out.result(manager.taintTree(g.base, Layer.NORMAL, g.root, g.exclusion))   // every result is a TaintTree; an any
    if (demand) out.result(manager.taintTree(g.base, Layer.DEMAND, g.root))     // leaf of g: `[any-taint]/Eg` normal, `[any]` demand
}
```

### 5.5 `filter` and the mark policy (`ap.md` §4.8, `interpreter.md` §5.1)

```kotlin
/** The mark policy of interpreter.md §5.1 (`markPolicyKeeps`): it reads a concrete mark on the value of the base itself
 *  (`elements = 0`) or on the value at `[e]^k` below the base (`elements = k`: the k-th element type), as today. It is NOT
 *  a may-predicate: it reads the mark, not the path, and it can drop a real flow (outside ap.md S5; gap G6). Part II
 *  builds it from the static type t: `MarkPolicy { mark, k -> the type at level k is not primitive or boxed, or
 *  isPrimitiveTracking(mark) }`; null if no level of t is primitive or boxed. */
fun interface MarkPolicy { fun keeps(mark: TaintMark, elements: Int): Boolean }

/** ap.md §4.8: the primitive `filter(b, may)`; `may` is prefix-closed (S5). The base is the key of
 *  StatementSummary.typeFilters (and resultFilters, Part II §23.2). Part II builds `may` with JIRFactTypeChecker (REUSE of
 *  its FactApFilter). `markPolicy` is the policy of the same static type and of its element types, or null (DD9). */
class TypeFilter(val may: FactTypeChecker.FactApFilter, val markPolicy: MarkPolicy? = null) {
    /** interpreter.md §5.1: two filters on one base are a conjunction. */
    fun and(o: TypeFilter): TypeFilter = TypeFilter(AndFilter(may, o.may), when {
        markPolicy == null -> o.markPolicy
        o.markPolicy == null -> markPolicy
        else -> MarkPolicy { t, k -> markPolicy.keeps(t, k) && o.markPolicy.keeps(t, k) }
    })

    private class AndFilter(val a: FactTypeChecker.FactApFilter, val b: FactTypeChecker.FactApFilter) : FactTypeChecker.FactApFilter {
        override fun check(accessor: Accessor): FactTypeChecker.FilterResult {
            val x = a.check(accessor)
            val y = b.check(accessor)
            return when {
                x === FactTypeChecker.FilterResult.Reject || y === FactTypeChecker.FilterResult.Reject -> FactTypeChecker.FilterResult.Reject
                x === FactTypeChecker.FilterResult.Accept -> y                                   // Accept and r: r
                y === FactTypeChecker.FilterResult.Accept -> x
                else -> FactTypeChecker.FilterResult.FilterNext(                               // both FilterNext: the conjunction below
                    AndFilter((x as FactTypeChecker.FilterResult.FilterNext).filter, (y as FactTypeChecker.FilterResult.FilterNext).filter))
            }
        }
    }
}

/** ap.md §4.8: `filter(b, may)`, then the mark policy (interpreter.md §5.1: "after the filter", on the same facts). The
 *  path filter checks the concrete path only and keeps every tail whole (`*`, `[any]`, `[any-taint]` with its exclusion;
 *  ap.md §11.1: the locations below the path that the type cannot have are not valid, S13). The zero fact passes (S11 (d)). The backward run
 *  calls neither (analyzer-core.md §3). */
fun ApOps.filter(c: Facts, f: TypeFilter): Facts? = when (c) {
    is Reach -> c
    is FlowTree -> FlowAlgebra.filterPath(manager, c.root, f.may)                 // no concrete mark: no policy
        ?.let { if (it === c.root) c else c.withRoot(it) }
    is TaintTree -> {
        var r = manager.taintAlg.filterPath(manager, c.root, f.may)
        val p = f.markPolicy
        if (r != null && p != null) r = markPolicyOnElements(r, p, 0)
        r?.let { if (it === c.root) c else manager.taintTree(c.base, c.layer, it, c.exclusion) }   // the exclusion stays (A2)
    }
}

/** TrieOps.kt, generic. ADAPT of AccessNode.filterAccessNode(FactApFilter) (AccessTree.kt:1031): no `[any]` edge, no
 *  FinalAccessor check. A filter never rejects the empty path, so the leaf of the root stays. */
internal fun <P : TrieLeaf> LeafAlgebra<P>.filterPath(m: ApManager, n: TrieNode<P>, may: FactTypeChecker.FactApFilter): TrieNode<P>? =
    mapChildren(n, n.leaf) { a, child ->
        when (val r = may.check(m.accessors.accessor(a))) {
            FactTypeChecker.FilterResult.Accept -> child                         // the whole subtree (prefix-closed, S5)
            FactTypeChecker.FilterResult.Reject -> null
            is FactTypeChecker.FilterResult.FilterNext -> filterPath(m, child, r.filter)
        }
    }

/** interpreter.md §5.1 `markPolicyKeeps(t, f)`: true unless `f.path` is `[e]^k` (k >= 0: the root path or a chain of
 *  element accessors from the root) and `f.mark` is a concrete mark that the policy of level k rejects. So the marks of
 *  the TAINT leaves at the root and at each node of the `[e]` chain can go, for both tails; a leaf off the chain stays
 *  (below a field or a class accessor). As today: AccessorFilter gives FilterNext(element type) at `[e]`
 *  (core/opentaint-dataflow-core/opentaint-jvm-dataflow/.../jvm/ap/ifds/JIRFactTypeChecker.kt:114-123), and
 *  filterAccessNode (AccessTree.kt:1039-1045) shows the mark accessors of that subtree to it, so its TaintMarkAccessor
 *  case (JIRFactTypeChecker.kt:96-104) acts below each `[e]` with the element type. Here a mark is a leaf payload, so
 *  the policy walks the `[e]` chain itself. `n` is the result of filterPath. */
private fun ApOps.markPolicyOnElements(n: TaintNode, p: MarkPolicy, k: Int): TaintNode? {
    fun keep(ms: MarkSet) = if (ms.isEmpty) ms else manager.intern(MarkSet(ms.ids.filter { p.keeps(TaintMark(it), k) }.toIntArray()))
    val kept = manager.taintLeaves(keep(n.leaf.exact), keep(n.leaf.any))
    val e = n.child(ELEMENT_ACCESSOR_IDX)
    val below = e?.let { markPolicyOnElements(it, p, k + 1) }                      // the next level of the `[e]` chain
    if (kept === n.leaf && below === e) return n                                   // nothing dropped: share
    return if (e == null) manager.taintAlg.withLeaf(n, kept)
           else manager.taintAlg.replaceChild(n, kept, ELEMENT_ACCESSOR_IDX, below)   // null `below`: the `[e]` child goes
}
```

Part II gives the policy of each level from the static type: level 0 is the type itself, level `k + 1` is the element
type of level `k` (`ifArrayGetElementType`, the type that `FilterNext` of `[e]` carries today). A level with an unknown
type keeps every mark (today `[e]` gives `Accept` there, so no mark check runs below it).

### 5.6 `clean` (`ap.md` §4.7)

The position of a leaf against the cleaned locations is one table for every kind (`ap.md` §4.7 first table, Lean
`cleanPos`). For a selected mark and an abstract fact on the same base, F75 changes the mark exclusion of the
whole tree before the position test, including its off-spine leaves. It shares the root and raises one request in
run 1, even if T is already excluded. Concrete marks and all-marks cleaners still use a spine walk (`cleanSpine`):
it rebuilds only the spine of `x.p`; a node off the spine is `disjoint` and stays shared.
`BaseCleaner.uniform_batch_equiv` proves the same per-leaf facts and request set for a uniform abstract batch.
Its executable guard-count witness is 33 distinct paths against one batch guard. The code below shares the trie
root; the Lean list proof does not prove the trie representation. Mark-set insertion, interning and output storage
have separate costs. The primitive selected-mark branch does not walk leaves.

```kotlin
/** ap.md §4.7 first table, for a leaf with the tail `tail` at depth d of the spine of x.p (d < |p|: strictly above;
 *  d = |p|: at). `admitsRest`: the exclusion of a `*` leaf, or of an `[any-taint]` leaf (A2; Lean AnyTaintEx.cleanPosX),
 *  admits the rest of x.p; an `[any]` leaf has the Empty exclusion. Strictly below x.p: `inside`, or `disjoint` for the
 *  reach `exact` (`cleanSpine`). */
internal fun cleanPosAt(cl: Cleaner, tail: Tail, d: Int, admitsRest: Boolean): Pos =
    if (d < cl.pathArray.size) when (tail) {
        Tail.EXACT -> Pos.DISJOINT
        Tail.STAR, Tail.ANY, Tail.ANY_TAINT -> if (admitsRest) Pos.PART else Pos.DISJOINT   // `*/E`, an any tail (K7)
    } else when (tail) {
        Tail.EXACT -> if (cl.reach == CleanReach.BELOW) Pos.DISJOINT else Pos.INSIDE
        else -> if (cl.reach == CleanReach.AT_AND_BELOW) Pos.INSIDE else Pos.PART
    }

/** TrieOps.kt, generic: `atNode(d, leaf)` returns the leaf that stays on the spine node of depth d (and moves the rest);
 *  `inside(kids)` returns what stays of the children strictly below x.p (reach below or atAndBelow). */
internal fun <P : TrieLeaf> LeafAlgebra<P>.cleanSpine(n: TrieNode<P>, p: IntArray, reach: CleanReach, d: Int,
                                                      atNode: (Int, P) -> P, inside: (TrieNode<P>) -> TrieNode<P>?): TrieNode<P>? {
    val kept = atNode(d, n.leaf)
    if (d < p.size) return replaceChild(n, kept, p[d], n.child(p[d])?.let { cleanSpine(it, p, reach, d + 1, atNode, inside) })
    if (reach == CleanReach.EXACT) return withLeaf(n, kept)                       // strictly below x.p: disjoint
    return withLeaf(retainChildren(n) { true }?.let(inside), kept)
}

fun ApOps.clean(c: Facts, premise: PremiseKey, cl: Cleaner, mode: ApMode, out: ApOut) {
    if (c.base != cl.base) { out.result(c); return }                           // another base: disjoint
    when (c) {
        is Reach -> out.result(c)                                               // S11 (d): no cleaner on the zero base
        is FlowTree -> FlowClean(manager, c, cl, mode, out).run()
        is TaintTree -> TaintClean(manager, c, cl, out).run()                   // concrete cleaner rows
    }
}

/** ap.md §4.7, F75: selected-mark abstract facts split by base; all-marks cleaners keep the spatial rows. */
private class FlowClean(val m: ApManager, val c: FlowTree, val cl: Cleaner, val mode: ApMode, val out: ApOut) {
    private val results = Results(m, c.base)
    private val tail = if (c.layer == Layer.NORMAL) Tail.STAR else Tail.ANY
    private val t = cl.mark
    private val cleanedMx = t?.let { m.intern(c.markExclusion + it) }          // `*∖(X ∪ {T})`
    fun run() {
        if (t != null) {
            results.flow(c.layer, c.exclusion, cleanedMx!!, c.root)           // every leaf, unchanged path/tail/layer
            if (mode.run1) out.markRequest(t)                               // also T already excluded; standing set key
            results.flush(out)
            return
        }
        FlowAlgebra.cleanSpine(c.root, cl.pathArray, cl.reach, 0, ::row) { _ ->          // strictly below x.p: inside
            null                                                                         // all marks: dropped
        }?.let { results.flow(c.layer, c.exclusion, c.markExclusion, it) }               // the kept part keeps the key of c
        results.flush(out)
    }

    private fun row(d: Int, leaf: FlowLeaf): FlowLeaf {
        if (leaf.isEmpty) return leaf
        val pos = cleanPosAt(cl, tail, d, admitsRest = d < cl.pathArray.size && c.exclusion.admits(cl.pathArray[d]))
        if (pos == Pos.DISJOINT) return leaf
        val at = FlowAlgebra.prepend(m, m.path(cl.pathArray.copyOf(d)), FlowAlgebra.LEAF_NODE)
        if (pos == Pos.PART) results.flow(Layer.DEMAND, ExclusionSet.Empty, c.markExclusion, at)  // all marks, part: demand (W2)
        return FlowLeaf.NONE
    }
}

/** ap.md §4.7, the rows of a concrete mark: inside -> dropped; part -> concPart; a mark that the cleaner does not clean stays.
 *  An any leaf is `[any-taint]/E` in a normal tree (`anyTail`, DD16; E = c.exclusion). THE EXCLUSION ROWS (`ap.md` §4.7,
 *  A2; Lean AnyTaintEx.cleanPosX, partX, cleanResX): a cleaner strictly below the leaf through an accessor in E cleans
 *  nothing of it (`disjoint`). A cleaner ONE accessor below the leaf, at `x.p.f`: `atAndBelow` keeps the leaf with
 *  `E ∪ {f}`; `below` keeps the leaf with `E ∪ {f}` and gives `(x, p.f, $, T)` (it cleans only strictly below `x.p.f`);
 *  both in the layer of c. `exact` there, and every other `part` row (a cleaner deeper below the leaf; `exact` at its
 *  path) give the demand tree: `[any]` with no exclusion (no shape for "every location but one"; necessary:
 *  AnyTaintExExact.CexExactCleaner.cex_exact_cleaner: `[any-taint]/{f}` would miss the real `x.f.g`, and a normal
 *  `[any-taint]/{}` would claim the cleaned `x.f`). Under a `below` cleaner AT its path an any leaf keeps
 *  `(x, p, $, T)` in its layer, as before. The kept part keeps its `[any-taint]` leaves and E in the layer of c
 *  (interpreter.md §5.2 ON AN `[any-taint]` FACT; the vectors AnyTaintEx.Vec.clean_atAndBelow, clean_below, clean_exact,
 *  clean_excluded, below_new_fact; the program AnyTaintExCases.CL). */
private class TaintClean(val m: ApManager, val c: TaintTree, val cl: Cleaner, val out: ApOut) {
    private val results = Results(m, c.base)
    private val anyTail = if (c.layer == Layer.NORMAL) Tail.ANY_TAINT else Tail.ANY
    private fun gone(ms: MarkSet) = if (cl.mark == null) ms else ms.intersect(m.markSetOf(cl.mark))

    fun run() {
        m.taintAlg.cleanSpine(c.root, cl.pathArray, cl.reach, 0, ::row) { kids ->        // strictly below x.p: inside, cleaned marks go
            m.taintAlg.mapLeaves(kids) { p -> m.taintLeaves(p.exact - gone(p.exact), p.any - gone(p.any)) }
        }?.let { results.taint(c.layer, it, c.exclusion) }
        results.flush(out)
    }

    private fun row(d: Int, leaf: TaintLeaves): TaintLeaves = m.taintLeaves(cut(d, Tail.EXACT, leaf.exact), cut(d, anyTail, leaf.any))

    private fun cut(d: Int, tail: Tail, ms: MarkSet): MarkSet {
        val g = gone(ms)
        if (g.isEmpty) return ms
        val p = cl.pathArray
        when (cleanPosAt(cl, tail, d, admitsRest = d >= p.size || c.exclusion.admits(p[d]))) {   // E of an any leaf (A2)
            Pos.DISJOINT -> return ms
            Pos.INSIDE -> Unit                                                        // dropped
            Pos.PART -> {                                                             // concPart, partX (Lean)
                val at = m.path(p.copyOf(d))
                when {
                    tail == Tail.ANY_TAINT && d == p.size - 1 && cl.reach != CleanReach.EXACT -> {   // A2: one accessor below
                        put(c.layer, at, Tail.ANY_TAINT, g, c.exclusion.union(ExclusionSet.of(p[d])))   // `[any-taint]/(E ∪ {f})`
                        if (cl.reach == CleanReach.BELOW) put(c.layer, cl.path, Tail.EXACT, g)        // `(x, p.f, $, T)`
                    }
                    tail.isAny && cl.reach == CleanReach.BELOW && d == p.size -> put(c.layer, at, Tail.EXACT, g)   // `(x, p, $, T)`
                    else -> put(Layer.DEMAND, at, tail, g)                            // any other part: the demand layer (`[any]`, W8)
                }
            }
        }
        return ms - g
    }

    private fun put(layer: Layer, at: PathNode?, tail: Tail, ms: MarkSet, exclusion: ExclusionSet = ExclusionSet.Empty) =
        results.taint(layer, m.taintAlg.prepend(m, at,
            m.taintAlg.leafNode(if (tail == Tail.EXACT) m.taintLeaves(ms, MarkSet.EMPTY) else m.taintLeaves(MarkSet.EMPTY, ms))), exclusion)
}
```

### 5.7 `limit` and the field-limit tables (`ap.md` §4.4, W3)

```kotlin
/** ap.md §4.4. A path with more than L counted accessors is cut before the (L+1)-th one; the tail becomes `[any]`, the
 *  exclusion Empty, the mark stays (also `*∖X`), the layer becomes demand. `L = 0` keeps the empty path. An `[any-taint]`
 *  leaf over the limit becomes `[any]` too and LOSES its exclusion: the cut trie is a demand trie (W8, A2: the cut point is
 *  above the leaf, so not every location below it carries the mark; Lean AnyTaintEx.limitFX, the vector
 *  AnyTaintEx.Vec.cut_drops, the program AnyTaintExCases.CUT; AnyTaintCases.Cut.limitF_cut_demand); the kept part keeps
 *  its `[any-taint]` leaves and the exclusion in the layer of c (`withRoot`). */
fun ApOps.limit(c: Facts, fieldLimit: Int, out: ApOut) {
    when (c) {
        is Reach -> out.result(c)                                                          // the empty path
        is FlowTree -> limitTrie(FlowAlgebra, c.root, fieldLimit, { out.result(if (it === c.root) c else c.withRoot(it)) }) {
            out.result(manager.flowTree(c.base, Layer.DEMAND, ExclusionSet.Empty, c.markExclusion, it)) }   // `[any]`, `*∖X`
        is TaintTree -> limitTrie(manager.taintAlg, c.root, fieldLimit, { out.result(if (it === c.root) c else c.withRoot(it)) }) {
            out.result(manager.taintTree(c.base, Layer.DEMAND, it)) }                      // `[any]` with the marks of the cut leaves
                                                                                           // (`$`, `[any-taint]` or `[any]`)
    }
}

/** One trie, both kinds: the O(1) check (Part I §4.5), else the cut: the kept trie and the cut trie (each may be absent). */
private inline fun <P : TrieLeaf> ApOps.limitTrie(alg: LeafAlgebra<P>, root: TrieNode<P>, fieldLimit: Int,
                                                  kept: (TrieNode<P>) -> Unit, cut: (TrieNode<P>) -> Unit) {
    if (root.boundedDepth <= fieldLimit) { kept(root); return }
    val (k, c) = FieldLimitCut(alg, manager).keep(root, fieldLimit)
    k?.let(kept); c?.let(cut)
}

/** The cut on a trie, generic. ADAPT of FieldLimiter.limit (branch saloed/any-field-limit, AccessTree.kt:722): the same
 *  budget walk over the nodes with boundedDepth > budget, with a memo per budget; no `[any]` edge to keep, so the cut part
 *  is a separate demand trie. */
internal class FieldLimitCut<P : TrieLeaf>(private val alg: LeafAlgebra<P>, private val m: ApManager) {
    private val memo = Int2ObjectOpenHashMap<IdentityHashMap<TrieNode<P>, Pair<TrieNode<P>?, TrieNode<P>?>>>()

    /** (the paths within the budget, the `[any]` leaves at the cut points); both rooted at n, so the memo is path-free. */
    fun keep(n: TrieNode<P>, budget: Int): Pair<TrieNode<P>?, TrieNode<P>?> {
        if (n.boundedDepth <= budget) return n to null
        memo.getOrPut(budget, ::IdentityHashMap)[n]?.let { return it }
        m.cancellation.checkpoint()
        var cutHere = alg.empty                                                    // the leaves of the cut subtrees, as `[any]`
        val keptKids = IntArrayList(); val keptNodes = ArrayList<TrieNode<P>>()
        val cutKids = IntArrayList(); val cutNodes = ArrayList<TrieNode<P>>()
        n.accessors?.forEachIndexed { i, a ->
            val child = n.children!![i]
            if (a.isCounted() && budget == 0) { cutHere = alg.union(cutHere, alg.asAny(alg.foldAll(child))); return@forEachIndexed }
            val (k, ct) = keep(child, if (a.isCounted()) budget - 1 else budget)   // an uncounted accessor stays in the prefix
            k?.let { keptKids.add(a); keptNodes.add(it) }
            ct?.let { cutKids.add(a); cutNodes.add(it) }
        }
        val kept = alg.node(n.leaf, keptKids.toIntArray(), keptNodes.toTypedArray()).takeIf { !it.isEmpty }
        val cut = alg.node(cutHere, cutKids.toIntArray(), cutNodes.toTypedArray()).takeIf { !it.isEmpty }
        return (kept to cut).also { memo[budget]!![n] = it }
    }
}
```

THE CUT POINTS (`ap.md` §4.4). `analyzer-impl.md` makes each call through its one `cut(...)` helper; the helper calls
`ApOps.limit` with `RunConfig.fieldLimit`:

| Cut point (`ap.md` §4.4) | `Cut` of `analyzer-impl.md` §4.3 | Call site | What is cut |
|---|---|---|---|
| the statement transfer, after the micro edges and the lhs filter (`ap.md` §4.2 step 6); the read sources; the read and write of a field cleaner (F74) | `STATEMENT` | `runStatement` (`analyzer-impl.md` §4.3), after the result filters; `cleanChain` (§4.5) for the two lowered statements | the results of the statement on the touched bases |
| the call return: after the rewriter, the binding back and the aliases (`ap.md` §5.3 step 5) | `CALL` | `flow` at the exit point of the plan (`analyzer-impl.md` §4.5): one site for every result of a call (§4.6: `applySummary`, `applyRecord`, `zret`) | the summary results `g.path ++ r`, the record results, the unresolved and pass results, the alias results |
| the source results and the end facts of a sink at a call, after the binding back (`ap.md` §5.3 step 3) | `CALL` | the same site | the source targets and the end facts in caller coordinates |
| the entry rules (`interpreter.md` §4.3) | `ENTRY_RULES` | `RuleWorklist` of `startAt` (forward, `analyzer-impl.md` §4.4); of `endAt` (backward: the reversed entry rules, §4.7) | the entry-point sources and the end facts of an entry sink |
| the exit rules, before the summary edge (`interpreter.md` §4.7) | `EXIT_RULES` | `RuleWorklist` of `endAt` (forward, both exits, `analyzer-impl.md` §4.7); of `startAt` (backward: the reversed exit rules, §4.4) | the exit sources and the end facts of an exit sink |
| a conjunction result; a summary with several premises (`ap.md` §4.6, E6) | `STATEMENT`, `CALL` | the conjunction of a statement: `runStatement` (§4.3); the conjunction of a stage (an ND source; a pass rule never makes one, `interpreter.md` §4.2) and E6 (`applyCombination`, §4.11): the plan exit (§4.5) | the target `z.π.t(T)`; the E6 result |
| the backward seed (`ap.md` §9.2) | `SEED` | `fireSinkSeeds` (`analyzer-impl.md` §4.9); a seed at a call is cut again at the plan exit (no change) | the sink pattern as a requirement (an `[any]` pattern gives an `[any]` requirement in the demand layer, W6; the backward run has no `[any-taint]`) |

WHERE A FACT CAN EXCEED `L` (`ap.md` W3: only a micro edge has no bound; every result of an operation is in the bound
after its cut):

| Object | Longer than `L`? | Reason |
|---|---|---|
| a micro edge: a statement edge, a binding, an alias edge, a rule edge | yes: never cut | I10, `ap.md` §4.2, W3: the limit applies to the RESULT of an operation, never to an edge |
| a rule pattern: a sink pattern, a literal pattern, the end-fact patterns of a witness | yes: never cut | the check compares; it makes no fact (`checkMark`) |
| the intermediate result of one operation before its cut point: a statement transfer before step 6 | yes | row 1 of the cut points |
| the points `BOUND` to `REWRITTEN` of a call plan: a summary result `g.path ++ r`, a pass result, a source result, an end fact, an alias path | yes | the cut comes at the plan exit (rows 2, 3) |
| the results of the entry rules or of the exit rules before their cut | yes | rows 4, 5 |
| a conjunction result or an E6 result before its cut | yes | row 6 |
| a seed before its cut | yes | row 7 |
| a summary edge, a record (applied as an edge) | NO, and not cut again before the application (I10) | each one is a cut result of an earlier operation or run (the next two rows and the last row) |
| a stored conclusion (`MethodEdgeStore`) | NO (W3) | every result is cut before `edges.add`; the store asserts it (§7.3) |
| an initial fact | NO | the policy fact has the path `[]`; a chain answer is never deeper than its request premise (`ap.md` §4.5); a static mark answer is the added fact (below); a position answer has at most two accessors, and the class accessor is not counted, so at most one counted (`L >= 1`); an emission is `a` itself, its meet (the path of `a`) or the demand chain of the run before (`L' <= L`, non-decreasing limits, `ap.md` §6.6) |
| an added fact, a link, a subscription | NO | a binding does not make a path longer (`ai.* -> argi.*`, `S.* -> S.*`); a cleaner does not; the caller fact was cut |
| a publication, a run summary | NO | an exit fact after the exit rules and their cut (row 5); in a restricted run the restriction moves a conclusion only to `D-p.path`, a path of a run with `L' <= L` (`ap.md` §6.4) |
| a record, a demand pattern | NO | a summary of a run with `L' <= L` (non-decreasing limits); a demand pattern is only read, as a filter (`emit`, `restrict`) |

The NO rows are W3. It is argued, not proved (`ap.md` §11.2). `MethodEdgeStore.add` asserts it (§7.3).

### 5.8 `MarkCheck`, `without`, `targetTree`, `ConjunctiveEdge` (`ap.md` §4.6, §4.9; DD15)

```kotlin
/** The one check of a mark literal (ap.md §4.9 sinks, §4.6 conjunction literals; Lean check, conj, markGate). */
sealed interface MarkCheck {
    data object None : MarkCheck
    /** A FLOW fact that may carry T: the request T on the premise (run 1). */
    class Request(val mark: TaintMark) : MarkCheck
    /** REACH (an unconditional sink: the zero pattern), or the overlapping TAINT leaves with T. `covered`: the part inside
     *  the pattern (`coversB`), for the layer of a conjunction input (ap.md §4.6, ND.conjLayer). */
    class Holds(val facts: Facts, val covered: Facts?) : MarkCheck {
        /** ap.md §4.6 with the `[any-taint]` rule (Lean AnyTaintND.conjLayerT, lit_loc): a hit leaf gives a NORMAL input
         *  if it is normal and the literal covers it, or if it is `[any-taint]` (every location of it carries the mark,
         *  so the literal holds at a common location). In a normal tree every hit leaf that `covered` lacks is an any leaf
         *  above or at the pattern, so `[any-taint]`: a normal tree gives a normal input only, a demand tree a demand
         *  input only (`conjDemand` per leaf, Part I §6, is the reference; AnyTaintND.Example.layer_new). The hit reads the
         *  exclusion (A2): an `[any-taint]/E` leaf above the pattern whose E excludes the step down is no hit, so it is no
         *  input (the conjunction with the exclusion is argued: AnyTaintND.DNzT has none, ap.md §11.2). */
        val normalPart: Boolean get() = facts.layer == Layer.NORMAL                         // a normal conjunction input
        val demandPart: Boolean get() = facts.layer == Layer.DEMAND                         // a demand conjunction input
    }
}

fun ApOps.checkMark(c: Facts, p: Pattern, mode: ApMode): MarkCheck {
    val t = (p.fact.mark as MarkSlot.Concrete).mark                                     // a literal names its mark (S9)
    if (c.base != p.fact.base) return MarkCheck.None
    val gate = MarkGate(manager, p.fact.mark, p.fact.mark)                              // the gate of the premise mark T (§4.1 step 4)
    return when (c) {
        is Reach -> if (t == TaintMark.ZERO) MarkCheck.Holds(c, c) else MarkCheck.None  // the zero pattern
        is FlowTree -> {
            if (!flowOverlaps(c, p)) return MarkCheck.None                              // overlap first, marks ignored (§3.2)
            val r = gate.flowRequest(c.markExclusion) ?: return MarkCheck.None          // `*∖X` with T ∈ X: no request
            if (mode.run1) MarkCheck.Request(r) else MarkCheck.None
        }
        is TaintTree -> {
            val hit = taintOverlap(c.root, p, c.exclusion) ?: return MarkCheck.None    // the leaves that overlap p (with E)
            val withT = manager.taintAlg.mapLeaves(hit) { l ->
                manager.taintLeaves(gate.taintMarks(l.exact) ?: MarkSet.EMPTY, gate.taintMarks(l.any) ?: MarkSet.EMPTY) } ?: return MarkCheck.None
            MarkCheck.Holds(manager.taintTree(c.base, c.layer, withT, c.exclusion),
                coveredPart(withT, p)?.let { manager.taintTree(c.base, c.layer, it, c.exclusion) })
        }
    }
}

// ap.md §4.9: on a request, "i.mark is abstract too ... the implementation asserts it". checkMark has no premise
// parameter; the request comes only from a FLOW fact, whose premise has the mark `*` (K6), and RequestStore.add
// (Part I §7.9) checks that the premise has the mark `*`.

/** Lean overlapB per leaf, on the walk of p.path: above p, a flag whose tail admits the rest (`*/Ec`: Ec admits it;
 *  `[any]`: always); at p, the flag; below p, a flag if p has an any tail. */
private fun flowOverlaps(c: FlowTree, p: Pattern): Boolean {
    val path = p.fact.path.toIntArray()
    var hit = false
    val u = c.root.walk(path) { d, n -> if (!n.leaf.isEmpty && (c.layer == Layer.DEMAND || c.exclusion.admits(path[d]))) hit = true }
    return hit || (u != null && (!u.leaf.isEmpty || (p.fact.tail.isAny && u.accessors != null)))
}

/** The TAINT leaves that overlap p (marks ignored): above p, the any leaves whose exclusion `e` admits the step down
 *  (A2; Lean AnyTaintEx.overlapX, checkX); at p, every leaf; below p, every leaf if p has an any tail. Rebuilt on the
 *  path (TrieOps.chain). An `[any-taint]` leaf triggers as an `[any]` leaf with no location in its excluded part
 *  (ap.md §4.9); its sink edge is normal, so it can be confirmed (AnyTaintExact.sink_den; AnyTaintEx.Vec.check_vectors;
 *  AnyTaintExCases.S.run1_name_no_trigger: `sink(dto.name)` on `(dto, ., [any-taint], {name}, T)` does not trigger). */
private fun ApOps.taintOverlap(c: TaintNode, p: Pattern, e: ExclusionSet): TaintNode? {
    val path = p.fact.path.toIntArray()
    val spine = ArrayList<TaintLeaves>(path.size + 1)
    val u = c.walk(path) { d, n -> spine += if (e.admits(path[d])) manager.taintLeaves(MarkSet.EMPTY, n.leaf.any) else TaintLeaves.EMPTY }
    val tip = u?.let { if (p.fact.tail.isAny) it else manager.taintAlg.leafNode(it.leaf) }
    return manager.taintAlg.chain(manager, path, spine, tip)
}

/** coversB(p, leaf): a `$` pattern covers the `$` leaves at p; an `[any]` pattern covers every leaf at or below p. */
private fun ApOps.coveredPart(hit: TaintNode, p: Pattern): TaintNode? {
    val u = hit.walk(p.fact.path.toIntArray()) ?: return null
    val at = if (p.fact.tail == Tail.EXACT) manager.taintAlg.leafNode(manager.taintLeaves(u.leaf.exact, MarkSet.EMPTY)).takeIf { !it.isEmpty } else u
    return at?.let { manager.taintAlg.prepend(manager, manager.path(p.fact.path), it) }
}

/** The leaves of `c` that are not in `part` (same path, tail and mark); the same kind. A `part` of another group key (another
 *  base, layer, kind, exclusion or mark exclusion: another tail) removes nothing, so no leaf of another tail goes. The
 *  global-state rule (interpreter.md §4.7 step 3) drops from the summary edge the part of a zero-premise item on S that
 *  satisfies a mark literal of an exit sink, plain or conjunctive (`MarkCheck.Holds.facts`). For a conjunctive sink the
 *  same part is the stored input of that literal (ConjunctionStore.Input.facts, Part I §7.10), so a later item can complete
 *  the combination with it. */
fun ApOps.without(c: Facts, part: Facts): Facts? = when {
    c.base != part.base -> c
    c is TaintTree && part is TaintTree ->                                 // a part with no any leaf has the Empty exclusion
        if (c.layer != part.layer || (part.root.hasAny && part.exclusion != c.exclusion)) c      // (its normal form, §4.1)
        else manager.taintAlg.minusNode(c.root, part.root)?.let { if (it === c.root) c else c.withRoot(it) }
    c.groupKey != part.groupKey -> c                                       // the contract: only the same tail and mark
    c is Reach -> if (part is Reach && part.layer == c.layer) null else c
    c is FlowTree && part is FlowTree -> FlowAlgebra.minusNode(c.root, part.root)?.let { if (it === c.root) c else c.withRoot(it) }
    else -> c
}

/** THE ENTRY-MARK REMOVAL (interpreter.md §4.7 step 4, gap G2, deviation D35; Part II §23.4
 *  `ExitRules.entryMarkRemoval`): `c` without every leaf whose mark is in `marks`, AT EVERY DEPTH AND WITH BOTH TAILS: a
 *  `$` leaf and an any leaf (an `[any-taint]/E` leaf goes with its exclusion: the tree exclusion stays for the other any
 *  leaves and goes with the last one, `withRoot`). The other leaves stay in their layer. This is NOT today's
 *  `TaintMarkRemover` (core/opentaint-dataflow-core/opentaint-jvm-dataflow/.../jvm/ap/ifds/analysis/
 *  JIRMethodSequentFlowFunction.kt:301-314): its filter reads only the children of the root, and `Accept` keeps a
 *  non-mark child with its whole subtree (AccessTree.kt:1031-1056), so today only `b.$ (m)` goes, and `b.f.$ (m)` and
 *  the any child `b.[any] (m)` leak (D35). A mark filter on the leaves, one walk, exact: equal to
 *  `clean(c, Cleaner(c.base, null, AT_AND_BELOW, T))` for each `T` of `marks` (every leaf at or below the root is
 *  `inside`, ap.md §4.7), the reference of the tests. REACH and a FLOW tree have no concrete mark: unchanged (a
 *  zero-premise item is REACH or TAINT, ap.md §7.2). Null: nothing stays. */
fun ApOps.withoutMarks(c: Facts, marks: MarkSet): Facts? = when {
    marks.isEmpty || c !is TaintTree -> c
    else -> manager.taintAlg.mapLeaves(c.root) { manager.taintLeaves(it.exact - marks, it.any - marks) }
        ?.let { if (it === c.root) c else c.withRoot(it) }
}

/** A conjunction result `z.π.t(T)` (ap.md §4.6), an end fact `P.$ (T)` or `P.[any-taint] (T)` (interpreter.md §4.1,
 *  I14) or a backward seed (the requirement of a sink pattern, Part II §23.4 `seedPatterns`): a one-leaf TAINT tree.
 *  REACH only for the zero pattern (a seed of an unconditional sink); a conjunction target is never on the zero base
 *  (ConjunctiveEdge), so its result is a TaintTree. AN `[any-taint]` TARGET in the normal layer stays NORMAL, with the
 *  Empty exclusion (W8; `normalize` of `factsOf`): the target of a conjunctive source or of an end fact on an `AnyField`
 *  position; in the demand layer it reads `[any]`. An `[any]` target is demand
 *  (W6): also the seed of an `[any]` sink pattern (the backward run has no `[any-taint]`, interpreter.md I11 (f)). */
fun ApOps.targetTree(target: PathFact, layer: Layer): Facts {
    check(target.mark is MarkSlot.Concrete && target.tail != Tail.STAR)                 // W7
    return manager.factsOf(target, ExclusionSet.Empty, layer)
}

/** ap.md §4.6: `x1.ρ1.t1(T1) ∧ … ∧ xk.ρk.tk(Tk) → z.π.t(T)`. Each literal has a concrete mark (S9) and the tail `$` or
 *  `[any]` (a forward pattern: never `[any-taint]`, interpreter.md I14); the target has a concrete mark and the tail `$` or
 *  `[any-taint]` (W7, S10): a conjunctive edge is a SOURCE (a pass rule has no mark literal), so an AnyField target is a
 *  taint edge (S15), never a may `[any]`. Part II reverses it into one PathEdge per literal,
 *  `revEdge(PathEdge(lit.fact, target, Empty))` (ap.md §9.2: an `[any]` literal gives an `[any]` requirement in the
 *  demand layer, W6; a target `[any-taint]` is the reversed premise `[any]`). The analyzer checks each literal with
 *  checkMark and gives the inputs to ConjunctionStore.add (Part I §7.10); the target is `targetTree(target, comb.layer)`:
 *  `[any-taint]` in a normal combination, `[any]` in a demand one. */
class ConjunctiveEdge(val literals: List<Pattern>, val target: PathFact) {
    init {
        check(literals.size >= 2)
        check(literals.all { it.fact.mark is MarkSlot.Concrete && (it.fact.tail == Tail.EXACT || it.fact.tail == Tail.ANY) })
        check(target.mark is MarkSlot.Concrete && (target.tail == Tail.EXACT || target.tail == Tail.ANY_TAINT))   // W7, I14
        check(target.base != AccessPathBase.Zero)                       // so targetTree(target, layer) is a TaintTree
    }
}
```

`TrieOps.chain(m, path, spine, tip)` builds one leaf per depth on `path` and the node `tip` at its end (`null` if
everything is empty); `minusNode` walks `part` inside `c`. Both are in §4.2.

### 5.9 `zero`, `startFact`, `policy`, `emit`, `restrict`, `demandPart` (`ap.md` §2.4, §6.2–§6.5, §7.4, §9.2)

```kotlin
/** The zero fact in a layer (ap.md §2.4). The END FACTS of a sink take no input fact: on a trigger they apply
 *  `zero.$ (zeroMark) -> P.$ (T)`, or `-> P.[any-taint] (T)` for an `AnyField` position (interpreter.md I14), to the zero
 *  fact in the layer of the sink edge or of the combination (interpreter.md §4.1), so a demand sink edge needs the demand
 *  REACH. */
fun ApOps.zero(layer: Layer): Reach = Reach.of(layer)

/** ap.md §6.5 (Lean startFact), by kind. A MUST-PREMISE `(x, p, [any-taint], E, T)` (an emission of an `[any-taint]/E`
 *  added fact, §6.3; forward restricted runs only, W8 (c)) starts AS ITSELF, `(x, p, [any-taint], E, T)` in the NORMAL
 *  layer (Lean AnyTaint.startT; with the exclusion AnyTaintEx.startX, the vector AnyTaintEx.Vec.start_must): as if a
 *  source fired at the method entry, so its edges are end-exact on the admitted locations (§1), and a vulnerability under
 *  it can be confirmed (§4.9 condition 2). The backward run and run 1 have no must-premise (`satisfying` asserts it, and
 *  analyzer-impl.md `addInitial`), so no row reads the run: no direction parameter (A1). */
fun ApOps.startFact(i: InitialAp): Facts {
    if (i.isZero) return Reach.NORMAL                                                    // the zero fact starts as itself
    val m = manager
    return when (val mk = i.mark) {
        is MarkSlot.Star -> when (i.tail) {                                              // FLOW: a policy fact, a position answer or a shared emission
            Tail.STAR -> m.flowTree(i.base, Layer.NORMAL, i.exclusion, MarkSet.EMPTY, FlowAlgebra.prepend(m, i.path, FlowAlgebra.LEAF_NODE))   // identity
            Tail.ANY -> m.flowTree(i.base, Layer.DEMAND, ExclusionSet.Empty, MarkSet.EMPTY, FlowAlgebra.prepend(m, i.path, FlowAlgebra.LEAF_NODE))
            Tail.EXACT -> error("S8: a `$` premise has a concrete mark")
            Tail.ANY_TAINT -> error("W8 (a): `[any-taint]` has a concrete mark")       // InitialAp.init rejects it
        }
        is MarkSlot.Concrete -> {                                                        // TAINT: an answer, an emission
            val ms = m.markSetOf(mk.mark)
            val anyLeaf = m.taintAlg.prepend(m, i.path, m.taintAlg.leafNode(m.taintLeaves(MarkSet.EMPTY, ms)))
            when (i.tail) {
                Tail.EXACT -> m.taintTree(i.base, Layer.NORMAL, m.taintAlg.prepend(m, i.path, m.taintAlg.leafNode(m.taintLeaves(ms, MarkSet.EMPTY))))
                Tail.ANY_TAINT -> m.taintTree(i.base, Layer.NORMAL, anyLeaf, i.exclusion)   // a must-premise: itself, with its E
                Tail.ANY, Tail.STAR -> m.taintTree(i.base, Layer.DEMAND, anyLeaf)        // `[any]`; `*` with T: `[any]` (W2)
            }
        }
    }
}

/** ap.md §6.2 (Lean policy1): the zero fact for the zero fact, else `(x, [], *, {}, *)`. One per added value (one base). */
fun ApOps.policy(added: Facts): InitialAp =
    if (added is Reach) manager.zero else manager.initial(added.base, null, Tail.STAR, ExclusionSet.Empty, MarkSlot.STAR)

// Canonical tree conversion guard (CurrentKinds.TreeDemandShape; F77 sequence preservation is open):
// demand marks are normalized; abstract D-c has tail ANY; exact D-p has a concrete D-c.
// Also require MarkWF, PremConc, NoUnivE and ExactTargetConc for both program directions,
// concrete seeds with no STAR tail, and RecordKinds for both stores (InitK premises;
// abstract premise => FLOW conclusion). These are construction conditions, not a claim
// about arbitrary demands or record lists. R1 persistence and structural hand-off selection use
// the same normal-singleton eligibility; backward zero-premise summaries retain their seed paths.

/** Current per-leaf emission (ap.md §6.3). A star entry produces its FLOW form, independent of the
 *  matching leaf's concrete mark. The set shares that premise across leaves and demand exits.
 *  No emission branch raises a request. The reference reads locations, exclusions and marks. */
fun ApOps.emit(d: DemandPattern, added: Facts): List<InitialAp> {
    check(d.entry.fact.tail != Tail.ANY_TAINT) { "W8 (d): no pattern has [any-taint]" }
    val res = LinkedHashSet<InitialAp>()
    for (a in leaves(added)) {
        val j = emit(d.entry, a) ?: continue                 // per-path Reference.emit, not this tree overload
        res += manager.initial(j.fact.base, manager.path(j.fact.path), j.fact.tail, j.exclusion, j.fact.mark)
    }
    return res.toList()
}

/** Generic mark-aware reduction (ap.md §6.4), including FLOW. This is the checked reference route.
 *  Select demand from the raw group headers before calling it. Do not normalize result marks here.
 *  Results have the same layer as the input and keep its mark; an abstract mark can keep extra
 *  marks where the representation cannot express the mark intersection (the stated exception). */
fun ApOps.restrictReference(j: InitialAp, g: Facts, d: DemandPattern): List<Conclusion> =
    leaves(g).mapNotNull { a ->
        restrict(j.toPattern(), Conclusion(a.fact, a.exclusion, g.layer == Layer.DEMAND), d)
    }.distinct().toList()

/** Pack reference results without changing their denotation. Group by each Facts key, including
 *  field and mark exclusions. The existing single-flag FlowTree cannot encode abstract `$`.
 *  The canonical paired guard above excludes abstract `$`; W2 checks the layer too.
 *  Generic callers keep the Conclusion list from restrictReference instead. Never widen or discard it to fit a tree. */
fun ApOps.restrict(j: InitialAp, g: Facts, d: DemandPattern): List<Facts> {
    val values = LinkedHashMap<GroupKey, Facts>()
    for (c in restrictReference(j, g, d)) {
        check(c.fact.mark !is MarkSlot.Star ||
            (c.fact.tail == Tail.STAR && !c.demand) || (c.fact.tail == Tail.ANY && c.demand)) {
            "reference result needs a FLOW tail that Facts cannot encode; validate the canonical paired guard"
        }
        val f = manager.factsOf(c.fact, c.exclusion, if (c.demand) Layer.DEMAND else Layer.NORMAL)
        val old = values[f.groupKey]
        values[f.groupKey] = if (old == null) f else manager.mergeAddDelta(old, f).first
    }
    return values.values.toList()
}

/** The demand part of one RAW summary group (ap.md §8.5, §8.7, §9.2).
 *  Return the whole group or null using headers only. A reversed record view can return DEMAND
 *  in the caller without creating a demand for the summarized method. */
fun ApOps.demandPart(premise: PremiseKey, g: Facts, direction: Direction): Facts? {
    if (g.layer == Layer.DEMAND) return g                            // no record (R1)
    val j = premise as? InitialAp ?: return g                        // several premises: no record (R1)
    if (direction == Direction.BACKWARD && j.isZero) return g         // seed paths; no record (R1)
    return null                                                     // every remaining normal singleton is a record
}
```

For a raw summary delta `g`, `summaryDelta` computes `demandPart(premise, g, direction)` before restriction.
It publishes `g`. If selected, it stores the same publications in `RunSummaryStore.addDemand`; otherwise it stores none.
Run 1 and backward zero-premise summaries publish unchanged. Other summaries use each member and each
candidate demand. The reduction tests marks and locations per leaf. Keep the full premise set of an ND summary;
its members are emitted concrete premises with the required demand containment guard.
Persistence at the barrier reads raw summaries under R1. Normal singleton summaries with must premises or
must conclusions are records too. Omission does not change their tails, exclusions, matching or result layers.
`CurrentSummarySelection.demand_iff` checks the structural selector; `group_selection_equiv` checks the group
shortcut against per-leaf selection. The shortcut checks one header per group and performs no leaf walk.
The executable `CurrentSummarySelection.Certificate.workWitness` compares selector calls and leaf visits. These counts exclude construction,
reduction, packing and record application.

### 5.10 `requestAction` (`ap.md` §4.5, §4.10 items 2 to 4)

The analyzer joins a request with a link (`analyzer-core.md` §4.6, through `StandingJoin`, §7.10). The AP rule of the pair
is here:

```kotlin
sealed interface RequestAction {
    class Answer(val initial: InitialAp) : RequestAction                             // a new initial fact of this method
    class Climb(val premise: InitialAp, val request: RequestKind) : RequestAction    // RequestIn to the caller, on its premise
    data object None : RequestAction
}

/** One (request, link) pair. `a` is one leaf of the added fact of the link; it overlaps the request (RequestStore and
 *  AddedFactStore give only such pairs). */
fun ApOps.requestAction(i: InitialAp, kind: RequestKind, a: Pattern, caller: CallerRef): RequestAction {
    check(i.tail == Tail.STAR && i.exclusion == ExclusionSet.Empty && i.mark == MarkSlot.STAR)   // §4.5: a policy fact or a position answer
    val m = manager
    return when (kind) {
        is RequestKind.Mark -> when (val am = a.fact.mark) {
            is MarkSlot.Concrete ->
                if (am.mark == kind.mark) RequestAction.Answer(m.initial(answer(i.toPattern(), a, kind.mark)))   // `answer`, Part I §6
                                                                                       // (an `[any-taint]` `a`: no must-premise, W8 (c))
                else RequestAction.None                                                // T' ≠ T
            is MarkSlot.Star ->                                                        // a FLOW link: its caller edge is FLOW (K6)
                if (kind.mark in am.excluded) RequestAction.None                       // `*∖X` with T ∈ X: does not climb
                else RequestAction.Climb(caller.premise as InitialAp, kind)            // Lean climbsB
        }
        is RequestKind.Position -> when {
            a.fact.path.startsWith(kind.path.toList()) ->                               // item 2: at or below p
                RequestAction.Answer(m.initial(STATIC, kind.path, Tail.STAR, ExclusionSet.Empty, MarkSlot.STAR))
            (caller.premise as? InitialAp)?.base == STATIC -> RequestAction.Climb(caller.premise as InitialAp, kind)   // item 3: above p
            else -> RequestAction.None
        }
    }
}
```

A `Climb` reads the caller premise as an `InitialAp`: an added fact with an abstract mark comes from a FLOW caller edge,
whose premise is one `InitialAp` with the mark `*` (K6). An ND caller edge is TAINT (W7), so its added facts are concrete
and never climb.

RUN 1, A CONCRETE SUMMARY PREMISE AND A `*` CALLER FACT (§5.4): the summary is not applicable, and its application
raises no request. The request that reaches that caller is the standing request of the callee: the rule of the callee
raised it on its own policy fact, and the link of the caller (the `*` added fact) gives `Climb` here.

### 5.11 `reverse`, `leaves`, `leavesNear` (`ap.md` §9.1)

```kotlin
/** Generic micro-edge reversal: null for non-mark-reversible or unsupported must/exclusion cells.
 *  Part II uses it for micro edges; reverseRecordLeaf handles new native forward must shapes separately (§7.8). The reversal reads no rule kind: the backward run has no
 *  `[any-taint]`, and the may rule of a reversed pass rule reads the forward target (`MicroEdge.may`, Part II §23.1). */
fun ApOps.reverse(e: PathEdge): PathEdge? = revEdge(e)                              // Part I §6

/** The per-path view: one Pattern per leaf and mark. REACH: the zero pattern. FLOW: `*/E` (normal) or `[any]` (demand),
 *  with the mark `*∖X`. TAINT: `$` and an any tail with each concrete mark: `[any-taint]` (`Tail.ANY_TAINT`) with the
 *  exclusion of the tree in a normal tree, `[any]` in a demand tree (DD16, K7). The tests, the links (Part I §7.5) and the
 *  support at the barrier read every result through it, so they see the tail and the exclusion of each leaf. */
fun ApOps.leaves(f: Facts): Sequence<Pattern> = when (f) {
    is Reach -> sequenceOf(manager.zero.toPattern())
    is FlowTree -> buildList { FlowAlgebra.forEachLeaf(f.root, EMPTY_PATH) { path, _ -> add(flowPattern(f, path)) } }.asSequence()
    is TaintTree -> buildList {
        manager.taintAlg.forEachLeaf(f.root, EMPTY_PATH) { path, leaf -> taintPatterns(f, path, leaf) { add(it) } }
    }.asSequence()
}

/** The leaves of `f` that overlap `(f.base, p, *, {}, *)` (ap.md §3.2): strictly above p, a leaf whose tail admits the rest
 *  of p (`*/E` or `[any-taint]/E` with `p[d]` not in E, `[any]`; a `$` leaf does not); at or below p, every leaf. One walk of p (walkPath,
 *  §4.6), then the leaves of the subtree at p. Equal to `leaves(f).filter { overlap(it, q) }`. User: AddedFactStore.overlapping. */
fun ApOps.leavesNear(f: Facts, p: IntArray): Sequence<Pattern> = when (f) {
    is Reach -> if (p.isEmpty()) leaves(f) else emptySequence()
    is FlowTree -> buildList {
        val u = f.root.walk(p) { d, n ->
            if (!n.leaf.isEmpty && (f.layer == Layer.DEMAND || f.exclusion.admits(p[d]))) add(flowPattern(f, p.copyOf(d))) }
        u?.let { FlowAlgebra.forEachLeaf(it, p) { path, _ -> add(flowPattern(f, path)) } }
    }.asSequence()
    is TaintTree -> buildList {
        val u = f.root.walk(p) { d, n ->                                              // above p: the any leaves that E lets through
            if (f.exclusion.admits(p[d])) taintPatterns(f, p.copyOf(d), manager.taintLeaves(MarkSet.EMPTY, n.leaf.any)) { add(it) } }
        u?.let { manager.taintAlg.forEachLeaf(it, p) { path, leaf -> taintPatterns(f, path, leaf) { add(it) } } }
    }.asSequence()
}

/** The leaf of a FLOW node at `path`: `*/E` (normal) or `[any]` (demand), with the mark `*∖X`. */
private fun flowPattern(f: FlowTree, path: IntArray): Pattern {
    val tail = if (f.layer == Layer.NORMAL) Tail.STAR else Tail.ANY
    return Pattern(PathFact(f.base, path.asList(), tail, MarkSlot.Star(f.markExclusion)), if (tail == Tail.STAR) f.exclusion else ExclusionSet.Empty)
}

/** The leaves of a TAINT node at `path`: `$` and the any tail of the layer (`[any-taint]` normal, `[any]` demand; W8 (b))
 *  with each concrete mark; an `[any-taint]` leaf has the exclusion of the tree (A2; Empty in a demand tree). */
private inline fun taintPatterns(f: TaintTree, path: IntArray, leaf: TaintLeaves, add: (Pattern) -> Unit) {
    val anyTail = if (f.layer == Layer.NORMAL) Tail.ANY_TAINT else Tail.ANY
    for (id in leaf.exact.ids) add(Pattern(PathFact(f.base, path.asList(), Tail.EXACT, MarkSlot.Concrete(TaintMark(id))), ExclusionSet.Empty))
    for (id in leaf.any.ids) add(Pattern(PathFact(f.base, path.asList(), anyTail, MarkSlot.Concrete(TaintMark(id))), f.exclusion))
}
```
---

## 6. The reference forms (`Reference.kt`)

`Reference.kt` uses the current rules of `ap.md` §3.4, §4.1, §6.3 and §6.4. Its per-path `emit`,
`satisfies` and `restrict` are the reference for their tree operations. A `*` demand entry emits its FLOW form;
restricted canonical FLOW satisfaction uses overlap; other FLOW shapes retain `applicable` and `inside`. All
restricted request branches return no request.
Both hand-offs remove abstract mark exclusions. Keep field exclusions and concrete marks except the explicit
forward `[any-taint]/E` to backward `[any]` view of `ap.md` §9.2.

`CurrentDemand.emitCertificate`, `restrictCertificate`, `reduceEmission`, `normalizeCertificate` and
`restrict_index_equiv` check the per-leaf contracts. `CurrentTreeReduction.tree_reference_equiv` checks mark-aware
reduction, including the whole fact and layer. Its generic Lean tree also represents abstract `$`.
`CurrentKinds.TreeDemandShape` excludes abstract `$` under the guards stated at §5.9.
`FlowTree` also checks the W2 tail/layer pairing; the paired-pattern theorem alone does not prove it. General closure coverage, X integration, optimized
Kotlin tree equivalence and the concurrent pipeline remain open. Earlier F71 results are historical.
`CurrentMustReverse` checks the three native forward reader shapes: exact-premise must reversal is pair-exact;
must-premise reads always produce demand. `normalMustWitness` computes the normal reader and its annotated
converse. `concrete_read_matches` checks record satisfaction for a concrete exact/any requirement with a common
covered location; `normal_view_transfer` checks admitted exact transfer. The earlier omission-guard lemmas
are historical. Current selection uses only the structural headers (§5.9), checked by `CurrentSummarySelection`.
Native singleton R1/S14 provenance remains the caller's contract. Full annotated-closure and hand-off coverage
are separate obligations.

An `[any-taint]/E` path carries `E` in `Pattern.exclusion` or `Conclusion.exclusion`. The accessor type is
`AccessorIdx` (DD6). `PathEdge` adds the compiled cache below; it is excluded from equality.

The following per-path code defines all demand operation dependencies. `Tail`, `MarkSlot` and the set types are
those of §3.2. `emit` recognizes the unexcluded `*` mark exactly. Generic `*∖X` inputs retain their mark test;
canonical demand stores never contain them. `meet` states the unsupported backward must-tail cell explicitly.

```kotlin
fun <T> List<T>.startsWith(p: List<T>): Boolean = size >= p.size && subList(0, p.size) == p

val STATIC: AccessPathBase = AccessPathBase.ClassStatic

data class PathFact(val base: AccessPathBase, val path: List<AccessorIdx>, val tail: Tail, val mark: MarkSlot)

data class Pattern(val fact: PathFact, val exclusion: ExclusionSet)

data class DemandPattern(val entry: Pattern, val exit: Pattern?)

fun tailAdmits(tail: Tail, exclusion: ExclusionSet, r: List<AccessorIdx>): Boolean = when (tail) {
    Tail.STAR, Tail.ANY_TAINT -> exclusion.admits(r)   // r = [] or the first accessor of r is not excluded
    Tail.ANY -> true
    Tail.EXACT -> r.isEmpty()
}
fun Pattern.tailAdmits(r: List<AccessorIdx>) = tailAdmits(fact.tail, exclusion, r)

fun markSub(i: MarkSlot, c: MarkSlot): Boolean = when (i) {
    is MarkSlot.Star -> when (c) {
        is MarkSlot.Star -> i.excluded.isSubsetOf(c.excluded)   // *∖X ⊇ *∖Y if and only if X ⊆ Y; * ⊇ every abstract mark
        is MarkSlot.Concrete -> c.mark !in i.excluded
    }
    is MarkSlot.Concrete -> c is MarkSlot.Concrete && c.mark == i.mark
}

private fun tailSub(i: Pattern, c: Pattern): Boolean = when (i.fact.tail) {
    Tail.ANY -> true
    Tail.EXACT -> c.fact.tail == Tail.EXACT
    Tail.STAR, Tail.ANY_TAINT -> when (c.fact.tail) {       // the exclusion of i must exclude only what c excludes
        Tail.EXACT -> true
        Tail.ANY -> i.exclusion == ExclusionSet.Empty
        Tail.STAR, Tail.ANY_TAINT -> i.exclusion.isSubsetOf(c.exclusion)
    }
}

fun covers(i: Pattern, c: Pattern): Boolean {
    if (i.fact.base != c.fact.base || !markSub(i.fact.mark, c.fact.mark)) return false
    val p = i.fact.path
    val q = c.fact.path
    if (!q.startsWith(p)) return false
    return if (q.size == p.size) tailSub(i, c) else i.tailAdmits(q.drop(p.size))
}

fun overlap(a: Pattern, b: Pattern): Boolean {
    if (a.fact.base != b.fact.base) return false
    val p = a.fact.path
    val q = b.fact.path
    return when {
        q.startsWith(p) -> a.tailAdmits(q.drop(p.size))
        p.startsWith(q) -> b.tailAdmits(p.drop(q.size))
        else -> false
    }
}

fun applicable(j: Pattern, a: Pattern): Boolean =
    covers(j, a) && (!j.fact.tail.isAny || a.fact.tail.isAny)

fun inside(j: Pattern, a: Pattern): Boolean =
    covers(a.copy(fact = a.fact.copy(mark = MarkSlot.Star(MarkSet.EMPTY))), j) && markSub(j.fact.mark, a.fact.mark)
data class Conclusion(val fact: PathFact, val exclusion: ExclusionSet, val demand: Boolean)

/** Transient native-record read. Never emitted, started, interned as an InitialAp, or persisted. */
data class RecordLeafView(val premise: Pattern, val result: Conclusion)

fun summaryEdge(j: Pattern, g: Conclusion): PathEdge {
    val jOwn = j.fact.tail == Tail.ANY_TAINT
    val gOwn = g.fact.tail == Tail.ANY_TAINT
    return PathEdge(j.fact, g.fact,
        (if (jOwn) ExclusionSet.Empty else j.exclusion).union(if (gOwn) ExclusionSet.Empty else g.exclusion),
        fromExclusion = if (jOwn) j.exclusion else ExclusionSet.Empty,
        toExclusion = if (gOwn) g.exclusion else ExclusionSet.Empty)
}

/** The native forward must shapes accepted by CurrentMustReverse. Keep the guards separate from the two
 *  side conversions: a must conclusion becomes a STAR/E read premise; a must input becomes an ANY demand result. */
private fun mustRecordReadView(p: Pattern, g: Conclusion): RecordLeafView? {
    val concrete = p.fact.mark is MarkSlot.Concrete && g.fact.mark is MarkSlot.Concrete
    val accepted = !g.demand && concrete && when (p.fact.tail) {
        Tail.EXACT -> p.exclusion == ExclusionSet.Empty && g.fact.tail == Tail.ANY_TAINT
        Tail.ANY_TAINT -> g.fact.tail == Tail.ANY_TAINT ||
            (g.fact.tail == Tail.EXACT && g.exclusion == ExclusionSet.Empty)
        else -> false
    }
    if (!accepted) return null
    val readPremise = if (g.fact.tail == Tail.ANY_TAINT)
        Pattern(g.fact.copy(tail = Tail.STAR), g.exclusion)          // retain the old conclusion E and concrete mark
    else Pattern(g.fact, ExclusionSet.Empty)                        // exact conclusion stays exact
    val demand = p.fact.tail == Tail.ANY_TAINT
    val resultFact = if (demand) p.fact.copy(tail = Tail.ANY) else p.fact
    return RecordLeafView(readPremise, Conclusion(resultFact, ExclusionSet.Empty, demand))   // forget old must-input E
}

/** Raw normal singleton record only. CurrentSimplifications.MustViews.readView_factored_equiv checks the accepted
 *  must constructor. Retain generic micro-edge revEdge and its fallback guards for every other native shape. */
fun reverseRecordLeaf(direction: Direction, p: Pattern, g: Conclusion): RecordLeafView? {
    if (g.demand) return null
    if (direction == Direction.FORWARD) mustRecordReadView(p, g)?.let { return it }
    if (p.fact.tail == Tail.ANY_TAINT) return null                   // no unsupported native must-input fallback
    if (p.fact.tail == Tail.STAR && p.fact.mark is MarkSlot.Concrete) return null   // invalid native normal shape
    val rev = revEdge(summaryEdge(p, g)) ?: return null
    return RecordLeafView(Pattern(rev.from, ExclusionSet.Empty), Conclusion(rev.to, rev.exclusion, demand = false))
}

/** Identity classification without starting a transient premise. Concrete STAR readers are effects. */
fun recordLeafIdentity(v: RecordLeafView): Boolean = !v.result.demand && v.premise.fact.base != AccessPathBase.Zero &&
    (v.premise.fact.tail == Tail.EXACT || (v.premise.fact.tail == Tail.STAR && v.premise.fact.mark is MarkSlot.Star)) &&
    v.result.fact == v.premise.fact && v.result.exclusion == v.premise.exclusion


fun meet(a: Pattern, d: Pattern): Pair<Tail, ExclusionSet> {
    // a */E pattern is backward only; the backward run has no [any-taint] (W8 (d)): the cell does not occur
    check(!(a.fact.tail == Tail.ANY_TAINT && d.fact.tail == Tail.STAR))
    return when {
        a.fact.tail.isAny && d.fact.tail.isAny -> a.fact.tail to a.exclusion
        a.fact.tail.isAny -> d.fact.tail to d.exclusion
        d.fact.tail.isAny -> a.fact.tail to a.exclusion
        a.fact.tail == Tail.EXACT || d.fact.tail == Tail.EXACT -> Tail.EXACT to ExclusionSet.Empty
        else -> Tail.STAR to a.exclusion.union(d.exclusion)
    }
}

fun emit(d: Pattern, a: Pattern): Pattern? {
    if (d.fact.mark == MarkSlot.STAR)
        return if (overlap(d, a)) flowForm(d) else null
    return commonPart(d, a)
}

fun flowForm(d: Pattern): Pattern {
    val star = MarkSlot.Star(MarkSet.EMPTY)
    return if (d.fact.tail == Tail.ANY) Pattern(d.fact.copy(tail = Tail.STAR, mark = star), ExclusionSet.Empty)
    else Pattern(d.fact.copy(mark = star), d.exclusion)                // `*/E` keeps E
}

fun commonPart(d: Pattern, a: Pattern): Pattern? {
    if (d.fact.base != a.fact.base) return null
    // a T demand needs the mark T: an abstract `a` gives nothing ("the added fact can't satisfy the demand", F72);
    // `*` admits every mark (Lean: markMatchB; with no `*∖X` pattern it is markSub)
    if (!markSub(d.fact.mark, a.fact.mark)) return null
    val p = d.fact.path
    val q = a.fact.path
    return when {
        q.size > p.size && q.startsWith(p) ->                                   // below: the fact itself
            if (d.tailAdmits(q.drop(p.size))) a else null
        q == p -> {                                                              // at: the meet of the tails
            val (tail, excl) = meet(a, d)
            Pattern(a.fact.copy(tail = tail), excl)
        }
        p.startsWith(q) ->                                    // above: the demand chain, the meet of the tails
            if (a.tailAdmits(p.drop(q.size))) {      // a concrete `a` has an any tail here (W2); reads E of `[any-taint]/E`
                val (tail, excl) = meet(a, d)
                Pattern(d.fact.copy(tail = tail, mark = a.fact.mark),
                    if (tail.isAny) ExclusionSet.Empty else excl)    // every location below the chain is admitted
            } else null
        else -> null
    }
}

fun satisfies(j: Pattern, a: Pattern, restricted: Boolean): Boolean =
    if (!restricted) applicable(j, a)       // run 1: a inside j (run 1 has no `[any-taint]` premise)
    else if (j.fact.tail == Tail.STAR && j.exclusion == ExclusionSet.Empty && j.fact.mark == MarkSlot.STAR)
        overlap(j, a)                      // canonical FLOW only; reads the exclusion of the added fact
    else inside(j, a) ||                   // concrete/must premise: inside; noncanonical FLOW retains both tests
        (j.fact.mark == MarkSlot.STAR && applicable(j, a))

fun recordDemand(j: Pattern, a: Pattern, resultDemand: Boolean): Boolean =
    resultDemand || (j.fact.tail == Tail.ANY_TAINT && !inside(j, a))
fun insideLoc(j: Pattern, d: Pattern): Boolean {
    val star = MarkSlot.Star(MarkSet.EMPTY)
    return covers(d.copy(fact = d.fact.copy(mark = star)), j.copy(fact = j.fact.copy(mark = star)))
}

fun insideDemand(j: Pattern, d: Pattern): Boolean = insideLoc(j, d) && markSub(d.fact.mark, j.fact.mark)

fun marksMeet(dp: MarkSlot, c: MarkSlot): Boolean = when {
    dp is MarkSlot.Concrete && c is MarkSlot.Concrete -> dp.mark == c.mark
    dp is MarkSlot.Concrete -> dp.mark !in (c as MarkSlot.Star).excluded      // an abstract conclusion stays as it is
    c is MarkSlot.Concrete -> c.mark !in (dp as MarkSlot.Star).excluded
    else -> true
}

fun meetConclusion(sc: Conclusion, dp: Pattern): Conclusion = when {
    !sc.fact.tail.isAny -> sc                                     // `$`; `*` stays whole (a FLOW conclusion, F72)
    dp.fact.tail == Tail.EXACT -> sc.copy(fact = sc.fact.copy(tail = Tail.EXACT), exclusion = ExclusionSet.Empty)
    sc.fact.tail == Tail.ANY_TAINT && dp.fact.tail == Tail.STAR -> sc.copy(exclusion = sc.exclusion.union(dp.exclusion))
    else -> sc                                                    // `[any] ∩ */E2` (the exception), `∩ [any]`
}

fun restrict(sp: Pattern, sc: Conclusion, d: DemandPattern): Conclusion? {
    val dp = d.exit ?: return null                           // the demand does not reach the exit
    if (!insideDemand(sp, d.entry)) return null              // the premise lies inside D-c: locations (reads Ej, §3.4)
                                                             // and marks
    if (!marksMeet(dp.fact.mark, sc.fact.mark)) return null  // the mark of the conclusion meets the mark of D-p
    if (sc.fact.base != dp.fact.base) return null
    val p = dp.fact.path
    val q = sc.fact.path
    return when {
        q == p -> meetConclusion(sc, dp)                                         // at D-p: the meet
        q.startsWith(p) -> if (dp.tailAdmits(q.drop(p.size))) sc else null     // below D-p: keeps E
        p.startsWith(q) -> when (sc.fact.tail) {                                // above D-p
            Tail.ANY, Tail.ANY_TAINT ->
                if (!sc.exclusion.admits(p.drop(q.size))) null                   // E of `[any-taint]/E` (W8)
                else if (dp.fact.tail == Tail.EXACT)                             // the chain of D-p, the layer stays
                    sc.copy(fact = sc.fact.copy(path = p, tail = Tail.EXACT), exclusion = ExclusionSet.Empty)
                else sc.copy(fact = sc.fact.copy(path = p),
                    exclusion = if (sc.fact.tail == Tail.ANY_TAINT && dp.fact.tail == Tail.STAR) dp.exclusion
                                else ExclusionSet.Empty)                         // [any-taint]/E2 under */E2 (W8)
            Tail.STAR -> if (sc.exclusion.admits(p.drop(q.size))) sc else null   // a FLOW conclusion (F72)
            else -> null                                                         // `$`: no common location
        }
        else -> null
    }
}
```

```kotlin
data class PathEdge(val from: PathFact, val to: PathFact, val exclusion: ExclusionSet,
                    val fromExclusion: ExclusionSet = ExclusionSet.Empty,     // Ej of a must-premise (a summary, a record)
                    val toExclusion: ExclusionSet = ExclusionSet.Empty) {     // Et of an `[any-taint]/Et` summary conclusion
    @JvmField internal var compiled: CompiledEdge? = null                   // not in equals/hashCode (not a constructor property)
}
```

The reference edge application below supplies `concat` and `normalize` to the forms oracle. It applies spatial
tests before the mark gate. Static and mark requests are suppressed in restricted runs. A below-case exact or must
target needs a concrete premise mark (S7, S8, W8). Legal STAR input has an abstract mark (W2), so that gate gives no
fact. The below shape therefore has no separate correlation-loss bit; the above-case demand rule stays.
`CurrentSimplifications.LostCorr.applySimple_eq` checks the gated local equality; `applySimple_may_eq` checks a may
target, whose normalization already forces DEMAND.

```kotlin
private fun PathEdge.premiseAdmits(r: List<AccessorIdx>): Boolean = when (from.tail) {
    Tail.EXACT -> r.isEmpty()
    Tail.ANY_TAINT -> fromExclusion.admits(r)   // a must-premise: Ej
    else -> exclusion.admits(r)        // `*`: E; `[any]`: E is Empty unless the target is `*` (row 1)
}

private class Shape(val path: List<AccessorIdx>, val tail: Tail, val exclusion: ExclusionSet, val demand: Boolean)

private fun below(c: Conclusion, edge: PathEdge, r: List<AccessorIdx>): Shape? {
    if (!edge.premiseAdmits(r)) return null
    val to = edge.to
    return when (to.tail) {
        Tail.STAR -> when {
            r.isNotEmpty() -> Shape(to.path + r, c.fact.tail, c.exclusion, demand = false)   // keeps Ec (W8)
            c.fact.tail == Tail.EXACT -> Shape(to.path, Tail.EXACT, ExclusionSet.Empty, false)
            c.fact.tail == Tail.ANY -> Shape(to.path, Tail.ANY, ExclusionSet.Empty, demand = true)   // W6
            else -> Shape(to.path, c.fact.tail, c.exclusion.union(edge.exclusion), false)  // `*/Ec`, or the exclusion
        }                                                                                 // edge on `[any-taint]/Ec` (W8)
        Tail.ANY -> Shape(to.path, Tail.ANY, ExclusionSet.Empty, demand = true)               // W6: a may
        Tail.ANY_TAINT, Tail.EXACT -> Shape(to.path, to.tail,                                  // a must, or `$`
            if (to.tail == Tail.ANY_TAINT) edge.toExclusion else ExclusionSet.Empty,           // Et (W8)
            demand = false)        // W2 and the concrete premise gate reject STAR input; geometry still precedes that gate
    }
}

private fun above(c: Conclusion, edge: PathEdge, r: List<AccessorIdx>): Shape? {
    val admitted = when (c.fact.tail) {
        Tail.STAR, Tail.ANY_TAINT -> c.exclusion.admits(r); Tail.ANY -> true; Tail.EXACT -> false
    }
    if (!admitted) return null                     // no common location: no result and no request
    val to = edge.to
    if (c.fact.tail != Tail.ANY_TAINT) {           // a `*` fact loses its correlation; an `[any]` fact is demand (W6)
        val tail = if (to.tail == Tail.EXACT) Tail.EXACT else Tail.ANY
        return Shape(to.path, tail, ExclusionSet.Empty, demand = true)
    }
    return when (to.tail) {                        // every admitted location of c carries its mark: nothing is lost
        Tail.EXACT -> Shape(to.path, Tail.EXACT, ExclusionSet.Empty, demand = false)
        Tail.ANY_TAINT -> Shape(to.path, Tail.ANY_TAINT, edge.toExclusion, demand = false)    // Et
        Tail.ANY -> Shape(to.path, Tail.ANY, ExclusionSet.Empty, demand = true)               // W6: a may
        Tail.STAR -> Shape(to.path, Tail.ANY_TAINT, edge.exclusion, demand = false)          // the edge exclusion (W8)
    }
}

sealed interface EdgeOutcome {
    data object None : EdgeOutcome
    data class Fact(val conclusion: Conclusion) : EdgeOutcome
    data class Request(val mark: TaintMark) : EdgeOutcome                  // run 1 only (§4.5)
    data class PositionRequest(val path: List<AccessorIdx>) : EdgeOutcome     // run 1 only (§4.10 item 1)
}

fun rootOrClass(q: List<AccessorIdx>): Boolean = q.isEmpty() || (q.size == 1 && q[0].isClass())

fun concat(c: Conclusion, edge: PathEdge, edgeDemand: Boolean = false,
           staticIdentity: Boolean = false, restricted: Boolean = false): EdgeOutcome {
    val from = edge.from
    check(from.mark !is MarkSlot.Star || from.mark.excluded == MarkSet.EMPTY)          // S7
    check(edge.to.mark !is MarkSlot.Concrete || from.mark is MarkSlot.Concrete)          // S7
    check(from.tail != Tail.EXACT || from.mark is MarkSlot.Concrete)                    // S8
    check(from.tail != Tail.EXACT || edge.to.tail != Tail.STAR)                         // S8
    check(edge.to.tail != Tail.EXACT || from.mark is MarkSlot.Concrete)                 // S8 (ExactTargetConc)
    check(edge.to.tail != Tail.ANY_TAINT || edge.to.mark is MarkSlot.Concrete)          // W8, S15
    check(from.tail != Tail.ANY_TAINT || from.mark is MarkSlot.Concrete)                // W8
    if (c.fact.base != from.base) return EdgeOutcome.None
    val p = from.path
    val q = c.fact.path
    val isAbove = p.size > q.size && p.startsWith(q)
    val shape = when {
        q.startsWith(p) -> below(c, edge, q.drop(p.size))
        isAbove -> above(c, edge, p.drop(q.size))
        else -> null
    } ?: return EdgeOutcome.None
    if (staticIdentity && from.base == STATIC && isAbove && rootOrClass(q)) {   // the static exception (§4.10)
        check(!restricted)
        return EdgeOutcome.PositionRequest(p.take(2))                          // cut to the static field or class
    }
    val premiseMark = from.mark
    if (premiseMark is MarkSlot.Concrete) {                                   // step 4: the mark gate
        when (val cm = c.fact.mark) {
            is MarkSlot.Concrete -> if (cm.mark != premiseMark.mark) return EdgeOutcome.None
            is MarkSlot.Star -> {
                if (premiseMark.mark in cm.excluded) return EdgeOutcome.None
                if (restricted) return EdgeOutcome.None                       // F72: no request after run 1 (§4.5)
                return EdgeOutcome.Request(premiseMark.mark)
            }
        }
    }
    val mark = compose(edge.to.mark, c.fact.mark) ?: return EdgeOutcome.None  // step 5
    return EdgeOutcome.Fact(normalize(PathFact(edge.to.base, shape.path, shape.tail, mark),
        shape.exclusion, c.demand || edgeDemand || shape.demand))
}

fun compose(to: MarkSlot, c: MarkSlot): MarkSlot? = when (to) {
    is MarkSlot.Concrete -> to
    is MarkSlot.Star -> when (c) {
        is MarkSlot.Star -> MarkSlot.Star(to.excluded + c.excluded)
        is MarkSlot.Concrete -> if (c.mark in to.excluded) null else c
    }
}

fun normalize(f: PathFact, exclusion: ExclusionSet, demand: Boolean): Conclusion = when {
    f.tail == Tail.ANY -> Conclusion(f, ExclusionSet.Empty, demand = true)                                  // W6
    f.tail == Tail.ANY_TAINT -> {
        check(f.mark is MarkSlot.Concrete)                                                                  // W8 (a)
        if (demand) Conclusion(f.copy(tail = Tail.ANY), ExclusionSet.Empty, demand = true)                // W8 (b)
        else Conclusion(f, exclusion, demand = false)                                    // keeps E (W8 (a))
    }
    f.tail != Tail.STAR || (f.mark is MarkSlot.Star && !demand) -> Conclusion(f, exclusion, demand)
    else -> Conclusion(f.copy(tail = Tail.ANY), ExclusionSet.Empty, demand = true)                         // W2
}
```

The forms that `ap.md` does not give in Kotlin (from the Lean definitions in `Basic.lean`, `ND.lean`,
`Subsume.lean`, and for F70 `HandoffDefs.lean` and `HandoffXRestrict.lean`):

```kotlin
/** ap.md §6.5 (Lean startFact; a must-premise: AnyTaint.startT, with the exclusion AnyTaintEx.startX). No direction
 *  parameter: a must-premise occurs only in a forward restricted run (A1), and no other row reads the run. */
fun startFact(i: Pattern): Conclusion = when (i.fact.tail) {
    Tail.STAR -> if (i.fact.mark is MarkSlot.Star) Conclusion(i.fact, i.exclusion, demand = false)
                 else Conclusion(i.fact.copy(tail = Tail.ANY), ExclusionSet.Empty, demand = true)          // W2
    Tail.ANY -> Conclusion(i.fact, ExclusionSet.Empty, demand = true)
    Tail.ANY_TAINT -> Conclusion(i.fact, i.exclusion, demand = false)                // a must-premise: itself, normal
    Tail.EXACT -> Conclusion(i.fact, ExclusionSet.Empty, demand = false)
}

/** ap.md §4.4 (Lean cutPath, limitF; with the exclusion AnyTaintEx.limitFX). An `[any-taint]/E` fact over the limit
 *  becomes `[any]` in the demand layer and loses E (W8, A2). */
fun limit(c: Conclusion, fieldLimit: Int): Conclusion {
    var n = 0
    for ((k, a) in c.fact.path.withIndex())
        if (a.isCounted() && ++n > fieldLimit)
            return Conclusion(c.fact.copy(path = c.fact.path.take(k), tail = Tail.ANY), ExclusionSet.Empty, demand = true)
    return c
}

// Primitives.kt (not a reference form; listed here because cleanPos reads it)
enum class CleanReach { EXACT, BELOW, AT_AND_BELOW }

/** ap.md §4.7 (Lean Cleaner). `mark == null`: every mark. Part II builds it (interpreter.md §5.2). */
class Cleaner(val base: AccessPathBase, val path: PathNode?, val reach: CleanReach, val mark: TaintMark?) {
    init { check(base != AccessPathBase.Zero) }                             // S11 (d): no cleaner on the zero base
    @JvmField internal val pathArray: IntArray = path?.toIntArray() ?: EMPTY_PATH
}

enum class Pos { INSIDE, DISJOINT, PART }

/** ap.md §4.7 (Lean cleanPos; with the exclusion AnyTaintEx.cleanPosX: a cleaner strictly below an `[any-taint]/E` fact
 *  through an accessor in E is `disjoint`). The tree form is cleanPosAt (Part I §5.6). */
fun cleanPos(cl: Cleaner, c: PathFact, exclusion: ExclusionSet): Pos {
    if (c.base != cl.base) return Pos.DISJOINT
    val p = cl.path?.toList().orEmpty()
    val q = c.path
    return when {
        q.size > p.size && q.startsWith(p) -> if (cl.reach == CleanReach.EXACT) Pos.DISJOINT else Pos.INSIDE
        q == p -> when {
            cl.reach == CleanReach.AT_AND_BELOW -> Pos.INSIDE
            c.tail == Tail.EXACT -> if (cl.reach == CleanReach.EXACT) Pos.INSIDE else Pos.DISJOINT
            else -> Pos.PART
        }
        p.startsWith(q) -> when (c.tail) {
            Tail.EXACT -> Pos.DISJOINT
            Tail.STAR, Tail.ANY, Tail.ANY_TAINT ->                                  // `[any]`: the Empty exclusion admits
                if (exclusion.admits(p.drop(q.size))) Pos.PART else Pos.DISJOINT
        }
        else -> Pos.DISJOINT
    }
}

class CleanOut(val facts: List<Conclusion>, val request: TaintMark?)

/** ap.md §4.7, F75 selected-mark split (BaseCleaner.cleanRes); concrete/all-marks rows keep concPart. */
fun cleanRes(cl: Cleaner, c: Conclusion): CleanOut {
    if (c.fact.base != cl.base) return CleanOut(listOf(c), null)
    val abstractMark = c.fact.mark as? MarkSlot.Star
    val selected = cl.mark
    if (abstractMark != null && selected != null)
        return CleanOut(listOf(c.copy(fact = c.fact.copy(mark = MarkSlot.Star(abstractMark.excluded + selected)))), selected)
        // The caller records this request only in run 1. Do not test the path or T ∈ X first.
    val pos = cleanPos(cl, c.fact, c.exclusion)
    if (pos == Pos.DISJOINT) return CleanOut(listOf(c), null)
    return when (val mk = c.fact.mark) {
        is MarkSlot.Concrete ->
            if (cl.mark != null && cl.mark != mk.mark) CleanOut(listOf(c), null)                  // T' not cleaned
            else if (pos == Pos.INSIDE) CleanOut(emptyList(), null)
            else CleanOut(concPart(cl, c), null)                                                  // one or two facts (A2)
        is MarkSlot.Star -> {
            check(cl.mark == null)                                               // selected mark handled before cleanPos
            CleanOut(if (pos == Pos.INSIDE) emptyList() else listOf(normalize(c.fact, c.exclusion, demand = true)), null)
        }
    }
}

/** ap.md §4.7 (Lean concPart; with the exclusion AnyTaintEx.partX): a normal `[any-taint]/E` fact at x.p and a cleaner ONE
 *  accessor below it, at x.p.f: `atAndBelow` gives the fact with `E ∪ {f}`, `below` gives it and `(x, p.f, $, T)`, in
 *  the layer of c (A2). An any-tail fact at x.p under a `below` cleaner AT x.p keeps `(x, p, $, T)` in its layer. Any
 *  other `part` result (the `exact` cleaner one accessor below too) goes to the demand layer: an `[any-taint]` fact as
 *  `[any]`, with no exclusion (W8). */
fun concPart(cl: Cleaner, c: Conclusion): List<Conclusion> {
    val p = cl.path?.toList().orEmpty()
    val q = c.fact.path
    if (c.fact.tail == Tail.ANY_TAINT && !c.demand && p.size == q.size + 1 && p.startsWith(q) && cl.reach != CleanReach.EXACT) {
        val kept = c.copy(exclusion = c.exclusion.union(ExclusionSet.of(p.last())))          // `[any-taint]/(E ∪ {f})`
        return if (cl.reach == CleanReach.AT_AND_BELOW) listOf(kept)
               else listOf(kept, c.copy(fact = c.fact.copy(path = p, tail = Tail.EXACT), exclusion = ExclusionSet.Empty))   // + x.p.f.$
    }
    return listOf(
        if (c.fact.tail.isAny && cl.reach == CleanReach.BELOW && q == p)
            c.copy(fact = c.fact.copy(tail = Tail.EXACT), exclusion = ExclusionSet.Empty)   // in the layer of c
        else c.copy(fact = if (c.fact.tail == Tail.ANY_TAINT) c.fact.copy(tail = Tail.ANY) else c.fact,
                    exclusion = ExclusionSet.Empty, demand = true))
}

sealed interface CheckResult { data object None : CheckResult; data object Holds : CheckResult; data class Request(val mark: TaintMark) : CheckResult }

/** ap.md §4.9 and §4.6 (Lean `check`, `ND.DN.conj`; renamed: `check` is the Kotlin assert): the check of one mark literal `s`
 *  on one fact. The tree form is ApOps.checkMark (Part I §5.8). The §11.2 difference: the effective-mark case is asserted away. */
fun markCheck(i: Pattern, f: Conclusion, s: Pattern, restricted: Boolean = false): CheckResult {
    val t = (s.fact.mark as MarkSlot.Concrete).mark
    if (!overlap(Pattern(f.fact, f.exclusion), s)) return CheckResult.None
    return when (val mk = f.fact.mark) {
        is MarkSlot.Concrete -> if (mk.mark == t) CheckResult.Holds else CheckResult.None
        is MarkSlot.Star -> if (restricted || t in mk.excluded) CheckResult.None
                            else { check(i.fact.mark is MarkSlot.Star); CheckResult.Request(t) }
    }
}

/** ap.md §4.6 (Lean ND.conjLayer; with the `[any-taint]` rule AnyTaintND.conjLayerT): the layer of one input of a literal
 *  that holds on it. Demand if the input is in the demand layer, or if the literal does not cover it and it is not
 *  `[any-taint]` (a normal `[any-taint]` input that overlaps its literal and passes its mark gate keeps the result
 *  normal: every location of it carries the mark, AnyTaintND.lit_loc). The overlap of `markCheck` reads the exclusion of
 *  an `[any-taint]/E` input (A2), so an input in the excluded part never reaches here. MarkCheck.Holds.normalPart and
 *  demandPart are its tree form. */
fun conjDemand(f: Conclusion, lit: Pattern): Boolean =
    f.demand || (!covers(lit, Pattern(f.fact, f.exclusion)) && f.fact.tail != Tail.ANY_TAINT)

/** ap.md §4.5 chain answer (Lean answerInit) and §4.10 item 4 (Lean Statics.SCtx.ansInit). Precondition: a overlaps i, a.mark = t.
 *  Run 1 has no must-premise (W8 (c)): an `[any-taint]/E` `a` gives the static mark answer with the tail `[any]` and no
 *  exclusion (a larger premise, demand: sound). */
fun answer(i: Pattern, a: Pattern, t: TaintMark): Pattern = when {
    i.fact.base == STATIC && a.fact.path.startsWith(i.fact.path) ->          // a at or below a static premise: a itself
        if (a.fact.tail == Tail.ANY_TAINT) Pattern(a.fact.copy(mark = MarkSlot.Concrete(t), tail = Tail.ANY), ExclusionSet.Empty)   // `[any]`: no E
        else Pattern(a.fact.copy(mark = MarkSlot.Concrete(t)), a.exclusion)
    a.fact.tail == Tail.EXACT && a.fact.path == i.fact.path ->
        Pattern(PathFact(i.fact.base, i.fact.path, Tail.EXACT, MarkSlot.Concrete(t)), ExclusionSet.Empty)
    else -> Pattern(i.fact.copy(mark = MarkSlot.Concrete(t)), i.exclusion)   // the request chain and kind, mark t
}

/** ap.md §6.2 (Lean policy1). */
fun policy(a: Pattern): Pattern =
    if (a.fact.base == AccessPathBase.Zero) a
    else Pattern(PathFact(a.fact.base, emptyList(), Tail.STAR, MarkSlot.STAR), ExclusionSet.Empty)

/** Generic micro-edge reversal (Lean revEdge, revKinds, MarkRev), unchanged by record read views.
 *  It rejects a must premise and a must conclusion with nonempty exclusion; reverseRecordLeaf handles the supported
 *  native forward must shapes separately (§7.8). A forward must micro target still reverses to ordinary ANY.
 *  MicroEdge.may reads the forward target for pass-rule demand handling. */
fun revEdge(e: PathEdge): PathEdge? {
    if (e.from.tail == Tail.ANY_TAINT) return null                          // generic reversal only
    if (e.to.tail == Tail.ANY_TAINT && (e.toExclusion != ExclusionSet.Empty || e.exclusion != ExclusionSet.Empty))
        return null                                                         // generic reversal only
    val fAbstract = e.to.mark is MarkSlot.Star
    if (!fAbstract && e.from.mark !is MarkSlot.Concrete) return null       // no_rev_of_star_conc
    val (pt, ct) = revTails(e.from.tail, e.to.tail)
    val pm = if (fAbstract) e.from.mark else e.to.mark                     // the new premise never has `*∖X`
    val cm = if (fAbstract) e.to.mark else e.from.mark
    val excl = if (pt == Tail.STAR || ct == Tail.STAR) e.exclusion else ExclusionSet.Empty   // the exclusion goes to the new conclusion (W1)
    return PathEdge(PathFact(e.to.base, e.to.path, pt, pm), PathFact(e.from.base, e.from.path, ct, cm), excl)
}

fun revTails(i: Tail, f: Tail): Pair<Tail, Tail> = when (i) {             // the table of §9.1
    Tail.STAR -> when (f) {
        Tail.STAR -> Tail.STAR to Tail.STAR; Tail.EXACT -> Tail.EXACT to Tail.ANY; Tail.ANY -> Tail.ANY to Tail.ANY
        Tail.ANY_TAINT -> error("S15: a taint edge has the premise `$` or `[any]`; a `*` premise has no `[any-taint]` conclusion")
    }
    Tail.ANY -> when (f) {
        Tail.STAR -> Tail.STAR to Tail.STAR; Tail.EXACT -> Tail.EXACT to Tail.ANY; Tail.ANY -> Tail.ANY to Tail.ANY
        Tail.ANY_TAINT -> Tail.ANY to Tail.ANY                               // a conditional source with an `[any]` literal (A1)
    }
    Tail.EXACT -> when (f) {
        Tail.STAR -> Tail.EXACT to Tail.EXACT; Tail.EXACT -> Tail.EXACT to Tail.EXACT; Tail.ANY -> Tail.ANY to Tail.EXACT
        Tail.ANY_TAINT -> Tail.ANY to Tail.EXACT                              // a source result: the premise `[any]` (A1)
    }
    Tail.ANY_TAINT -> error("generic revTails has no must-premise row")   // reverseRecordLeaf handles native must views
}

/** ap.md §8.1 (Subsume.subsumesB, markSubsB): s subsumes n inside one layer. FlowGroup and TaintGroup are its tree form.
 *  An `[any]` s (demand) subsumes every fact at or below its path with its mark. An `[any-taint]/Es` s (normal)
 *  subsumes, with its mark, in its layer, ONLY `[any-taint]` facts: an `[any-taint]/En` fact at its path if `Es ⊆ En`,
 *  and one strictly below its path through an accessor that `Es` admits (A2; argued, ap.md §11.2); NEVER a `$` fact
 *  (DD16, ap-history.md F69: a later demoting cleaner row moves s to the demand layer, and the `$` fact stays normal). */
fun subsumes(s: Conclusion, n: Conclusion): Boolean {
    if (s.fact.base != n.fact.base || s.demand != n.demand || !markSubsumes(s.fact.mark, n.fact.mark)) return false
    return when (s.fact.tail) {
        Tail.ANY -> n.fact.path.startsWith(s.fact.path)
        Tail.ANY_TAINT -> n.fact.tail == Tail.ANY_TAINT && n.fact.path.startsWith(s.fact.path) &&
            if (n.fact.path.size == s.fact.path.size) s.exclusion.isSubsetOf(n.exclusion)
            else s.exclusion.admits(n.fact.path[s.fact.path.size])
        Tail.STAR -> n.fact.tail == Tail.STAR && s.fact.path == n.fact.path && s.exclusion.isSubsetOf(n.exclusion)
        Tail.EXACT -> n.fact.tail == Tail.EXACT && s.fact.path == n.fact.path
    }
}
fun markSubsumes(s: MarkSlot, n: MarkSlot): Boolean = when (s) {
    is MarkSlot.Concrete -> n == s
    is MarkSlot.Star -> n is MarkSlot.Star && s.excluded.isSubsetOf(n.excluded)
}
```

The test sources add the denotation `den(i, f)(l0, l1)` of `ap.md` §3.2, a statement transfer (`transfer` of
`Basic.lean`: the union of `concat` over the micro edges, an untouched base unchanged, then `limit`) for the vector
tests, and `asConclusions(f: Facts)`: the leaves of `f` (§5.11) with the layer of `f` (§8).

---

## 7. The stores (`ap.md` §8)

### 7.1 Ownership and concurrency (`analyzer-core.md` §2, O1–O5)

| Store | Lifetime | Writer | Readers | Concurrency |
|---|---|---|---|---|
| `MethodEdgeStore`, `InitialFactStore`, `RequestStore`, `ConjunctionStore` (with its `KaryJoin`s), the `StandingJoin` of the requests and links | RUN: garbage at the end of the run (`analyzer-core.md` §7.6; `freeze`, `analyzer-impl.md` §7.7) | the runner of the method (O1) | the same runner | none: single writer |
| `AddedFactStore` (the links) | RUN; the links of a FORWARD run stay until the confirmation at its barrier (`analyzer-core.md` §7.6) | the runner of the method (O1) | the same runner; the driver at the barrier of a forward run (the support, O5) | none: single writer; the join of the runners (`analyzer-core.md` §6.3) orders the barrier reads |
| `RunSummaryStore`, `SourceHitStore` | HAND-OFF | the runner of the method (O1) | the driver at the barrier (`RunSummaryStore.all()`: the records, R1; `demandEdges()`: the hand-off, F70 D3) | as above |
| `DemandStore` | RUN, read-only | the driver, before the run (`Builder.build`) | any runner (`analyzer-core.md` A4) | immutable after `build`; the start of the run publishes it |
| `RecordStore` | PERSISTENT | the driver, at a barrier (`persist`, inside the memory guard of the barrier, §7.8) | any runner, through `view()` (`analyzer-core.md` A4) | written only when no runner is alive; `view()` rejects writes |
| `VulnerabilityStore` | PERSISTENT | any runner (O4) | the driver at the barrier | nested `ConcurrentHashMap`s; `merge` is atomic per (key, shape) (§7.12) |
| `PathTrie`, `TrieInterner`, the groups of §4.3 | inside a store | the owner of the store | the owner | none; `SummaryStorage` (`analyzer-impl.md` §5.2) guards its trie with its lock (P3). The memory guard (of a run or of the barrier) clears the soft table of a `TrieInterner` from its own thread; `Reference.clear` and `get` are thread-safe, and the owner then makes a new table (§4.5 THE LIFETIME OF A TABLE) |
| `ApManager`, `ApOps`, `FlowAlgebra`, `TaintAlgebra` | analysis | any | any | `ConcurrentHashMap`; `ApOps` is stateless (§5.3); a `TrieNode` is immutable |

### 7.2 `PathTrie` (`ap.md` §8 PATH TRIES)

```kotlin
package org.opentaint.dataflow.bidi.store

/** Entries keyed by `base :: path`. ADAPT of AccessBasedStorage (ap/ifds/access/tree/AccessBasedStorage.kt:12): the same child walk
 *  (`getOrCreateNode` :19, `find` :34, `allNodes` :76), keyed by IntArray instead of the old AccessPath.AccessNode, and plain
 *  fastutil maps instead of ConcurrentReadSafeInt2ObjectMap. The lookups use walkPath (Part I §4.6). Single writer. Store.lean
 *  proves each lookup equals its list filter (`Store.PathMap.lookupPrefixes_equiv`, `Store.PathMap.lookupExtensions_equiv`,
 *  `Store.mem_around_indexBy`).
 *  A node holds a SET of values: a value is at most once per position, and `add` costs O(1). A hot callee has one
 *  AddedFactStore key per (call site, caller premise) at `arg0 :: []`, so a list with a linear test is O(n²) there. The
 *  linked set keeps the insertion order, so every lookup is deterministic. */
class PathTrie<V : Any> {
    private class Node<V : Any> {
        val values = ObjectLinkedOpenHashSet<V>(2)
        var children: Int2ObjectOpenHashMap<Node<V>>? = null
        fun child(a: Int): Node<V>? = children?.get(a)
        fun getOrCreate(a: Int): Node<V> = (children ?: Int2ObjectOpenHashMap<Node<V>>().also { children = it }).getOrPut(a) { Node() }
    }
    private val roots = HashMap<AccessPathBase, Node<V>>(4)

    /** False if the value is at this position already (the equality of V; the users' values are interned or data classes). */
    fun add(base: AccessPathBase, path: IntArray, value: V): Boolean = node(base, path).values.add(value)

    private fun node(base: AccessPathBase, path: IntArray): Node<V> {
        var n = roots.getOrPut(base) { Node() }
        for (a in path) n = n.getOrCreate(a)
        return n
    }

    /** The entries at or above `path` (also at `path`). Cost: |path| + 1 nodes (Store.prefHits_le). */
    fun lookupPrefixes(base: AccessPathBase, path: IntArray): MutableList<V> {
        val out = ArrayList<V>()
        val root = roots[base] ?: return out
        walkPath(root, path, Node<V>::child) { _, n -> out += n.values }?.let { out += it.values }
        return out
    }

    /** The entries at or below `path` (also at `path`). */
    fun lookupExtensions(base: AccessPathBase, path: IntArray): List<V> =
        ArrayList<V>().also { out -> find(base, path)?.let { collect(it, out, self = true) } }

    /** lookupPrefixes ++ the entries strictly below: each entry once per position (Store.around; a value at two positions
     *  comes twice). It is also the `near` of ap.md §8.6 (RStore.nearBy_iff_around). Cost: walk + Σ|rel|
     *  (RStore.near_query_cost); a radix trie would give walk + count. */
    fun around(base: AccessPathBase, path: IntArray): List<V> =
        lookupPrefixes(base, path).also { out -> find(base, path)?.let { collect(it, out, self = false) } }

    fun all(): Sequence<V> = roots.values.asSequence().flatMap { r -> ArrayList<V>().also { collect(r, it, self = true) } }

    private fun find(base: AccessPathBase, path: IntArray): Node<V>? = roots[base]?.let { walkPath(it, path, Node<V>::child) }

    private fun collect(n: Node<V>, out: MutableList<V>, self: Boolean) {   // iterative, as allNodes (:76)
        val stack = ArrayDeque<Node<V>>()
        if (self) out += n.values
        n.children?.values?.let(stack::addAll)
        while (stack.isNotEmpty()) { val x = stack.removeLast(); out += x.values; x.children?.values?.let(stack::addAll) }
    }
}
```

### 7.3 `MethodEdgeStore` (`ap.md` §8.1)

```kotlin
/** ap.md §8.1, per kind (§7.2): REACH bits per (statement, premise key, layer); FLOW trees per (statement, premise, layer,
 *  base, exclusion, mark exclusion); TAINT trees per (statement, premise key, layer, base). REUSE of the structure of
 *  MethodAnalyzerEdges (ap/ifds/MethodAnalyzerEdges.kt:13): the zero edges in a BitSet per statement (SameInitialZeroFactEdges,
 *  :150), here one per premise key and layer; the statement as its instruction index (instructionStorageIdx, :272). The
 *  FLOW and TAINT keys below the premise are inside ConclusionGroup (Part I §4.3). */
class MethodEdgeStore(private val m: ApManager, private val method: MethodKey, private val lm: LanguageManager,
                      private val fieldLimit: Int) {
    private val size = lm.getMaxInstIndex(method.method) + 1
    private val reach = Reference2ObjectOpenHashMap<PremiseKey, Array<BitSet>>()                 // premise -> [NORMAL, DEMAND]
    private val groups = Reference2ObjectOpenHashMap<PremiseKey, Array<ConclusionGroup?>>()      // premise keys are interned
    private val interners = m.newInterners()

    /** Returns the delta (T4) or null (`analyzer-core.md` §4.3). */
    fun add(node: CommonInst, premise: PremiseKey, f: Facts): Facts? {
        check((f is FlowTree) == premise.isFlow)                     // K6: the kind follows from the premise (ap.md §7.2)
        check(premise !is PremiseSet || f is TaintTree)              // ap.md §4.6: an ND edge is TAINT
        val idx = lm.getInstIndex(node)
        when (f) {
            is Reach -> {                                            // premise {zero}, or {jb} in a backward run: the reversed
                                                                     // source `x.p.$ (T) -> zero.$ (zeroMark)` (ap.md §9.2 SOURCE HITS)
                val bits = reach.getOrPut(premise) { arrayOf(BitSet(size), BitSet(size)) }[f.layer.ordinal]
                if (bits[idx]) return null
                bits.set(idx)
                return f
            }
            is FlowTree -> assert(f.root.boundedDepth <= fieldLimit) { "W3: a stored conclusion is within L (Part I §5.7)" }
            is TaintTree -> assert(f.root.boundedDepth <= fieldLimit) { "W3: a stored conclusion is within L (Part I §5.7)" }
        }
        val byStatement = groups.getOrPut(premise) { arrayOfNulls(size) }
        val g = byStatement[idx] ?: ConclusionGroup(m, interners).also { byStatement[idx] = it }
        return g.add(f)
    }

    /** A query for the tests (not a query of ap.md §8.1): the edges at `node`, of one premise key or of every premise key.
     *  A set REACH bit is the Reach of that premise key and layer; the FLOW and TAINT values are the stored trees of the
     *  groups (Part I §4.3). */
    fun edgesAt(node: CommonInst, premise: PremiseKey? = null): Sequence<Pair<PremiseKey, Facts>> {
        val idx = lm.getInstIndex(node)
        val keys = premise?.let(::sequenceOf) ?: (reach.keys.asSequence() + groups.keys.asSequence()).distinct()
        return keys.flatMap { p ->
            val bits = reach[p]
            val r = Layer.entries.asSequence().filter { bits != null && bits[it.ordinal][idx] }.map { p to (Reach.of(it) as Facts) }
            r + groups[p]?.get(idx)?.all().orEmpty().map { p to it }
        }
    }

    /** A query for the tests: the stored values at `node` with a leaf that overlaps `pattern` (ap.md §3.2, the reference
     *  `overlap`). The result is the WHOLE stored value, not its overlapping part. */
    fun edgesAt(node: CommonInst, pattern: Pattern): Sequence<Pair<PremiseKey, Facts>> {
        val ops = ApOps(m)
        val p = pattern.fact.path.toIntArray()
        return edgesAt(node).filter { (_, f) -> f.base == pattern.fact.base && ops.leavesNear(f, p).any { overlap(it, pattern) } }
    }
}
```

The two queries read the store during its run. They are for the tests (`MethodEdgeStoreTest`, §8 test 22); `ap.md`
§8.1 has no query of the edges. The store lives for its run (`ap.md` §8.1). Trace resolution is out of scope
(`ap-history.md` F67), so no run keeps its store for a trace resolver.

The W3 assert is a JVM `assert`: the Gradle test task enables it (`-ea`), a production run does not pay for it. It
replaces the check of `TreeFieldLimitCheck` (branch `saloed/any-field-limit`). The items of the unchanged path
(`ap.md` §8.1) skip the store, as today: they go to the `unchanged` queue of the method analyzer and not through `add`
(`analyzer-impl.md` `DeltaWorklist.addUnchanged`).

### 7.4 `InitialFactStore` (`ap.md` §8.2)

```kotlin
class InitialFactStore {
    private val initials = ReferenceOpenHashSet<InitialAp>()            // interned: identity
    fun add(i: InitialAp): Boolean = initials.add(i)                    // also deduplicates the answers of §8.8
    fun all(): Collection<InitialAp> = initials
}
```

The store keeps no supported premise sets. The confirmation (`ap.md` §4.9 condition 3) reads `Support`, which the
driver computes at the barrier from the links (`analyzer-impl.md` §7.5).

### 7.5 `AddedFactStore`, `CallerRef`, `Link` (`ap.md` §8.3; DD4)

```kotlin
/** E-2 of analyzer-core.md §5.1: the caller side of a link. */
data class CallerRef(val caller: MethodKey, val premise: PremiseKey, val callerLayer: Layer, val call: CommonInst)

/** ap.md §8.3, the spec form: one leaf of an added value with its caller reference and its layer on the link. An added
 *  fact with an any tail is `[any-taint]` on a NORMAL link and `[any]` on a DEMAND link (W8; Lean the flag `am` and the
 *  exclusion `aex` of AnyTaintEx.XObj.added): `addedFact.fact.tail` is `Tail.ANY_TAINT` or `Tail.ANY` by `linkLayer`,
 *  and `addedFact.exclusion` is the exclusion E of an `[any-taint]` leaf (ApOps.leaves, Part I §5.11). The emission
 *  (§6.3) and the support of §4.9 condition 3.2.3 (Part I §7.12 `supplies`) read both. */
data class Link(val addedFact: Pattern, val linkLayer: Layer, val caller: CallerRef)

/** ap.md §8.3. The added facts of one caller reference are ONE value per (link layer, kind key): REACH (no more), FLOW
 *  (base, exclusion, mark exclusion), TAINT (base, exclusion of the `[any-taint]` leaves). The leaves of the value are the added facts (DD4). Two values with
 *  different keys never merge (T3).
 *  EXACT DEDUPLICATION (analyzer-core.md §5.1 E-3, §5.3): the only merge is the T1 union of leaves (mergeAddDelta, Part I §4.2,
 *  with no fold). A leaf that the value of its key has (same path, tail, mark) is dropped, nothing else: no T5, no
 *  subsumption (no FlowGroup, TaintGroup, foldUnder, subtract). Reason: `applicable` is not monotone in the added fact
 *  (a fact above j does not satisfy j), so a subsumed leaf can satisfy a premise that the subsuming leaf does not; dropping
 *  it loses a link, an answer or a summary application. A leaf has ONE key, with one exception: a FLOW leaf has no `$`
 *  tail, and an any leaf is in the key of its tree (an `[any-taint]/E` leaf in the key with E; DD12, DD16); but a `$`
 *  leaf does not read the tree exclusion, so the same `$` added fact can be a leaf of the normal TAINT value with the key
 *  E (beside `[any-taint]/E` leaves) and of the value with the key `{}` of the same caller reference: two links for one
 *  added fact. The duplicate is harmless: every consumer deduplicates (`InitialFactStore.add`, `MethodEdgeStore.add`,
 *  the request answers, Part I §7.4, §7.3, §7.9). Every other leaf is a new link once. The analyzer reuses this store to
 *  merge its subscription values.
 *  THE `[any-taint]` FLAG OF A LINK is the tail of its added fact, and the link layer gives it (DD16): the key holds the
 *  layer and the exclusion (the group key), so an `[any-taint]/E` added fact (normal link) and an `[any]` added fact
 *  (demand link) of one path and mark are two keys, and `links()` reports each with its tail and its exclusion. No other
 *  field. */
class AddedFactStore(private val m: ApManager) {
    private data class Key(val ref: CallerRef, val group: GroupKey)                     // the layer of the group is the link layer
    private val values = Object2ObjectOpenHashMap<Key, Facts>()
    private val index = PathTrie<Key>()                                   // one entry per new leaf position (§8.8 overlap queries)
    private val interners = m.newInterners()                              // DD5: the stored values, as every store (Part I §4.5)

    /** Event E1/E2: returns the delta (the new links) or null. */
    fun add(ref: CallerRef, linkLayer: Layer, added: Facts): Facts? {
        check(linkLayer == added.layer)                                    // the layer of the added fact on the link (§8.3)
        val key = Key(ref, added.groupKey)
        val old = values[key]
        val delta: Facts = if (old == null) added.also { values[key] = interners.internIfRequired(it) } else {
            val (merged, d) = m.mergeAddDelta(old, added)                   // T1, T4 (Part I §4.2); no subsumption
            values[key] = interners.internIfRequired(merged)                // equal content: exact (DD5)
            d ?: return null
        }
        m.forEachLeafPosition(delta) { base, path -> index.add(base, path, key) }   // a key once per position (a set, §7.2)
        return delta
    }

    /** §8.3, §8.8: the links whose added fact overlaps `(base, path, *, {}, *)` (a request premise or a position, §4.5).
     *  The index gives the candidate keys near the path; each candidate value is walked along the path (walkPath,
     *  ApOps.leavesNear, Part I §5.11), so only its leaves that overlap the query become links: no list of every leaf. */
    fun overlapping(base: AccessPathBase, path: PathNode?): Sequence<Link> {
        val p = path?.toIntArray() ?: EMPTY_PATH
        val ops = ApOps(m)
        return index.around(base, p).distinct().asSequence()                 // a key at several positions comes once
            .flatMap { key -> ops.leavesNear(values.getValue(key), p).map { Link(it, key.group.layer, key.ref) } }
    }

    fun links(): Sequence<Link> = values.keys.asSequence().flatMap { links(it) }      // the support at the barrier (analyzer-core.md §7.5)
    private fun links(k: Key) = ApOps(m).leaves(values.getValue(k)).map { Link(it, k.group.layer, k.ref) }
}
```

`ApManager.forEachLeafPosition(f: Facts, action: (AccessPathBase, IntArray) -> Unit)` (`TrieOps.kt`, §4.2) is
`forEachLeafPosition` of the algebra of the kind, and `(Zero, [])` for REACH. The analyzer computes the satisfying part
of a stored value with `ApOps.satisfying` (P4); `satisfying` reads the value, not the per-leaf links.

COST. A hot callee (for example a library method that 50 000 call sites reach) has one key per (call site, caller
premise) at the same position. `PathTrie.add` is O(1) per key (a hash set per node, §7.2), and a request reads, per
candidate key, the walk of its path and the leaves at or below it (`leavesNear`), not every leaf of the value.

### 7.6 `RunSummaryStore` (`ap.md` §8.5)

```kotlin
/** Raw summaries and hand-off pieces are separate stores, with the same canonical keys.
 *  add/all keeps raw exit conclusions; R1 persistence reads only these at the barrier.
 *  addDemand/demandEdges keeps publications of groups selected by layer, premise count and backward zero.
 *  summaryDelta selects raw group headers before restriction. Every other normal singleton stays out of demand.
 *  Exact merge and subsumption never cross layer, field-exclusion or mark-exclusion keys. */
class RunSummaryStore(private val m: ApManager) {
    private val groups = Reference2ObjectOpenHashMap<PremiseKey, ConclusionGroup>()
    private val demand = Reference2ObjectOpenHashMap<PremiseKey, ConclusionGroup>()     // F70 D3: the demand edges
    private val interners = m.newInterners()

    fun add(premise: PremiseKey, g: Facts): Facts? {
        check((g is FlowTree) == premise.isFlow)                         // K6
        check(premise !is PremiseSet || g is TaintTree)                  // ap.md §4.6: an ND summary is TAINT
        return groups.getOrPut(premise) { ConclusionGroup(m, interners) }.add(g)
    }

    /** One publication of a group selected by `ApOps.demandPart` (Part I §5.9). The delta or null, as `add`. */
    fun addDemand(premise: PremiseKey, piece: Facts): Facts? {
        check((piece is FlowTree) == premise.isFlow)                     // K6, as `add`
        return demand.getOrPut(premise) { ConclusionGroup(m, interners) }.add(piece)
    }

    /** R1: the summaries before the restriction; the records read them (Part I §7.8). */
    fun all(): Sequence<Pair<PremiseKey, Facts>> = groups.asSequence().flatMap { (p, g) -> g.all().map { p to it } }

    /** F70 D3: the demand edges of the run; the hand-off reads only these. */
    fun demandEdges(): Sequence<Pair<PremiseKey, Facts>> = demand.asSequence().flatMap { (p, g) -> g.all().map { p to it } }
}
```

THE WRAP EXAMPLE (current behavior; historical finite witness `HandoffCases.Wrap`, `ap-history.md` F70). Run 1 gives `wrap` the one summary `(arg, ., *) → (ret, .f, *)`,
a normal FLOW value under the policy fact: `demandPart` is null, so `demandEdges()` of `wrap` is empty
(`HandoffCases.Wrap.handF_w1_exact`: the only demand edge of run 1 is in the root), and the summary is a record (R1).
Backward run 2 crosses `wrap` by its reversal (`HandoffCases.Wrap.bn_cross`) and does not enter it, except by the zero
fact (`bn_wrap_zero_only`). Forward run 3 crosses `wrap` by the record (`fn_record_applicable`: by `applicable`), cuts
the path in the root (`fn_cut_in_root`), analyses `wrap` only from the zero fact (`fn_wrap_zero_only`) and reports the
vulnerability (`fn_found`). With the hand-off before F70, runs 2 and 3 enter `wrap` again and run 3 cuts inside it
(`old_b2_wrap_init`, `old_f3_wrap_init`, `old_f3_wrap_cut`; the two together: `wrap_old_vs_new`).

### 7.7 `DemandStore` (`ap.md` §8.6)

```kotlin
/** Demand patterns per method key. The hand-off normalizes mark exclusions and applies
 *  the paired shape guard (§5.9); it alone converts forward must tails to the backward view.
 *  The zero demand is implicit for every key. Keys are atomic base :: D-c.path; marks,
 *  tails and exclusions remain payloads for the exact operation test. */
class DemandStore private constructor(
    private val byMethod: Map<MethodKey, PathTrie<DemandPattern>>,      // keyed by `base :: D-c.path`
    private val zeroDemand: DemandPattern,                              // (zero, none) of EVERY method key (§9.2, analyzer-core.md §4.4)
) {
    /** Emission scans prefixes and extensions. Compare each candidate with Reference.emit.
     *  Include the implicit zero demand on the zero base. */
    fun near(method: MethodKey, base: AccessPathBase, path: PathNode?): List<DemandPattern> {
        val stored = byMethod[method]?.around(base, path?.toIntArray() ?: EMPTY_PATH).orEmpty()
        return if (base == AccessPathBase.Zero) stored + zeroDemand else stored
    }

    /** Restriction scans prefixes only. CurrentDemand.restrict_index_equiv proves equivalence
     *  to the full list scan for every mark/tail cell. Test full containment, output marks and
     *  exit locations on every payload. The zero demand has no exit and produces no result. */
    fun covering(method: MethodKey, base: AccessPathBase, path: PathNode?): List<DemandPattern> =
        byMethod[method]?.lookupPrefixes(base, path?.toIntArray() ?: EMPTY_PATH).orEmpty()

    class Builder(private val m: ApManager) {
        private val byMethod = HashMap<MethodKey, PathTrie<DemandPattern>>()
        private val seen = HashSet<Pair<MethodKey, DemandPattern>>()
        private val zeroDemand = DemandPattern(m.zero.toPattern(), null)
        /** True only for a new stored pattern. The frontier counts these insertions. */
        fun add(method: MethodKey, d: DemandPattern): Boolean {
            check(d.entry.fact.tail != Tail.ANY_TAINT && d.exit?.fact?.tail != Tail.ANY_TAINT) { "W8 (d): a pattern has no [any-taint]" }
            fun normalized(p: Pattern) = p.fact.mark !is MarkSlot.Star || p.fact.mark.excluded.isEmpty
            check(normalized(d.entry) && (d.exit == null || normalized(d.exit))) { "normalize mark exclusions at hand-off" }
            check(d.entry.fact.mark !is MarkSlot.Star || d.entry.fact.tail == Tail.ANY) { "canonical abstract D-c has tail [any]" }
            check(d.exit?.fact?.tail != Tail.EXACT || d.entry.fact.mark is MarkSlot.Concrete) { "canonical exact D-p has concrete D-c" }
            if (d == zeroDemand || !seen.add(method to d)) return false     // the zero demand is implicit
            byMethod.getOrPut(method, ::PathTrie).add(d.entry.fact.base, d.entry.fact.path.toIntArray(), d)
            return true
        }
        fun build(): DemandStore = DemandStore(byMethod, zeroDemand)
    }
}
```

### 7.8 `RecordStore`, `Record` (`ap.md` §8.7)

```kotlin
/** Native normal singleton raw summary. Same-direction must reuse keeps R4 demotion.
 *  Opposite-direction read views never enter either persistent index. */
class Record(val method: MethodKey, val direction: Direction, val premise: InitialAp, val conclusion: Facts) {
    /** R3, leaf by leaf. Native records and exclusions stay unchanged. New forward reads:
     *  exact U -> must/E T: STAR/E T -> exact U NORMAL;
     *  must/Ej U -> must/E T: STAR/E T -> ANY U DEMAND;
     *  must/Ej U -> exact T: exact T -> ANY U DEMAND.
     *  The exact output discards input suffixes. May outputs forget Ej. Other leaves use generic revEdge.
     *  demandPart omits every native normal singleton record, including both must-premise cases. */
    fun reversedAt(a: Pattern): Sequence<RecordLeafView> {
        check(conclusion.layer == Layer.NORMAL)
        val p = premise.toPattern()
        return ApOps(premise.manager).leaves(conclusion)
            .filter { it.fact.base == a.fact.base &&
                (it.fact.path.startsWith(a.fact.path) || a.fact.path.startsWith(it.fact.path)) }
            .mapNotNull { leaf -> reverseRecordLeaf(direction, p, Conclusion(leaf.fact, leaf.exclusion, demand = false)) }
    }
}

/** ap.md §8.7. PERSISTENT, direction-neutral. */
interface RecordStore {
    fun add(record: Record)
    /** R2: candidates for an added fact `a` of a link, in the direction of the record: `around(base :: a.path)`. The reader
     *  tests `applicable || inside` (analyzer-core.md `recordApplies`). */
    fun byEntry(method: MethodKey, addedFact: Pattern): Sequence<Record>
    /** R2, R3: candidates for a reader in the other direction (PipelineStore.record_lookup). */
    fun byExit(method: MethodKey, fact: Pattern): Sequence<Record>
    /** A read-only view for a run (analyzer-core.md A4): add and persist throw. */
    fun view(): RecordStore
    /** R1, at a barrier. */
    fun persist(direction: Direction, summaries: Sequence<Pair<MethodKey, RunSummaryStore>>)
}

/** Written only at a barrier (no runner is alive); read by every runner of the next run through view(). */
class PersistentRecordStore(private val m: ApManager) : RecordStore {
    private val entry = HashMap<MethodKey, PathTrie<Record>>()          // R2: base :: premise path
    private val exit = HashMap<MethodKey, PathTrie<Record>>()           // R2: base :: leaf path, one entry per leaf
    private val merged = HashMap<Triple<MethodKey, Direction, InitialAp>, ConclusionGroup>()
    private val interners = m.newInterners()

    override fun add(record: Record) {
        entry.getOrPut(record.method, ::PathTrie).add(record.premise.base, record.premise.pathArray, record)
        m.forEachLeafPosition(record.conclusion) { base, p -> exit.getOrPut(record.method, ::PathTrie).add(base, p, record) }
    }

    override fun byEntry(method: MethodKey, addedFact: Pattern): Sequence<Record> =
        entry[method]?.around(addedFact.fact.base, addedFact.fact.path.toIntArray())?.asSequence().orEmpty()

    override fun byExit(method: MethodKey, fact: Pattern): Sequence<Record> =
        exit[method]?.around(fact.fact.base, fact.fact.path.toIntArray())?.asSequence()?.distinct().orEmpty()

    override fun view(): RecordStore = object : RecordStore by this {
        override fun add(record: Record) = error("read-only during a run (analyzer-core.md A4)")
        override fun persist(direction: Direction, summaries: Sequence<Pair<MethodKey, RunSummaryStore>>) = error("read-only")
        override fun view(): RecordStore = this
    }

    /** A delta per premise: ConclusionGroup (Part I §4.3) merges the same record of several runs, so a record never repeats.
     *  The driver calls it at the barrier, inside the memory guard of the barrier (analyzer-impl.md §7.1; ap-history.md
     *  F68 (5)). So the soft-reference managers are enabled, the interners of this store intern with a live table, and the
     *  records of one barrier share nodes (Part I §4.5 THE LIFETIME OF A TABLE). The guard can clear the table during the
     *  call: only sharing is lost (DD5). A guard hit cancels the `Cancellation`, so the checkpoint below throws and the
     *  driver ends the iteration with OOM and the report so far (analyzer-core.md §7.2 B4). */
    override fun persist(direction: Direction, summaries: Sequence<Pair<MethodKey, RunSummaryStore>>) {
        for ((method, store) in summaries) {
            m.cancellation.checkpoint()                                  // once per analyzer: the barrier guard (analyzer-core.md B4)
            for ((premise, g) in store.all()) {
                if (g.layer == Layer.DEMAND) continue                    // normal only
                val j = premise as? InitialAp ?: continue                // ONE member; a PremiseSet is never a record (R1)
                if (direction == Direction.BACKWARD && j.isZero) continue   // a zero-premise backward edge is never persisted
                // R1, ONE NOTION OF COMPLETE: a normal edge. FORWARD: also with `[any-taint]/E` leaves and of a must-premise
                // (a must record; AnyTaintExExact.recs_of_DRX_valid). BACKWARD: no `[any-taint]` (A1), and W6 keeps every
                // backward any leaf in the demand layer, so a normal backward summary has no any leaf.
                check(direction == Direction.FORWARD || !(g is TaintTree && g.root.hasAny)) { "W6: a backward any leaf is demand" }
                val delta = merged.getOrPut(Triple(method, direction, j)) { ConclusionGroup(m, interners) }.add(g) ?: continue
                add(Record(method, direction, j, delta))
            }
        }
    }
}
```

NORMAL SINGLETON SUMMARIES ARE RECORDS (R1; DD17). `persist` reads raw `RunSummaryStore.all()`.
Except for backward zero-premise summaries, every normal singleton group is omitted from callee demand.
This includes native exact→must/E, must→must/E and must→exact records. Their readers retain the same
matching, mark gates and layers: exact→must/E can return a normal exact requirement; a must-premise view
returns ordinary ANY in DEMAND and forgets the old premise E. That result propagates in the caller.
It does not require analysis inside the summarized method. Restriction does not determine omission.

R5 stays: replay creates no initial fact and does not suppress another demand or seed. The zero fact still
enters every callee. A forward zero-premise source record may apply without a source seed; the reader itself
creates no SourceHit. Reversed conjunction results remain demand, so R1 does not persist them.

`CurrentMustReverse` checks the local readers. `CurrentSummarySelection` checks current structural selection.
Full annotated-closure and iteration coverage remain open. Earlier selectors and their
loss diagnostics in Lean are historical; they do not define current demand selection.

### 7.9 `RequestStore`, `RequestKind` (`ap.md` §8.8)

```kotlin
/** A request of run 1 (ap.md §4.5, §4.10). */
sealed interface RequestKind {
    data class Mark(val mark: TaintMark) : RequestKind
    data class Position(val path: PathNode) : RequestKind               // a static position, cut to <= 2 accessors
}

/** ap.md §8.8. RUN 1 only. A mark request is keyed by `base :: i.path`, a position request by `S :: p`. Every request
 *  comes from a FLOW fact (DD12), so its premise is a FLOW premise: one InitialAp with the mark `*` (K6). */
class RequestStore {
    private val seen = HashSet<Pair<InitialAp, RequestKind>>()
    private val index = PathTrie<Pair<InitialAp, RequestKind>>()

    fun add(premise: InitialAp, kind: RequestKind): Boolean {
        check(premise.tail == Tail.STAR && premise.exclusion == ExclusionSet.Empty && premise.mark == MarkSlot.STAR)   // §4.5
        if (!seen.add(premise to kind)) return false                     // exact deduplication (E-3)
        when (kind) {
            is RequestKind.Mark -> index.add(premise.base, premise.pathArray, premise to kind)
            is RequestKind.Position -> index.add(STATIC, kind.path.toIntArray(), premise to kind)
        }
        return true
    }

    /** Event E2: every standing request that overlaps the added fact `a` (`Store.standing_complete`). */
    fun overlapping(a: Pattern): Sequence<Pair<InitialAp, RequestKind>> =
        index.around(a.fact.base, a.fact.path.toIntArray()).asSequence().filter { (i, k) ->
            overlap(a, when (k) {
                is RequestKind.Mark -> i.toPattern()
                is RequestKind.Position -> Pattern(PathFact(STATIC, k.path.toList(), Tail.STAR, MarkSlot.STAR), ExclusionSet.Empty)
            })
        }
}
```

Event E5/E7 (a new request) reads `AddedFactStore.overlapping(base, path)`; event E2 (a new link) reads
`RequestStore.overlapping(leaf)` for each new leaf. `ApOps.requestAction` (§5.10) gives the action of each pair. The
analyzer joins the pairs with `StandingJoin` (§7.10), with these two stores as its two sides (`analyzer-impl.md`
§4.8).

### 7.10 `KaryJoin`, `StandingJoin`, `ConjunctionStore`, `NdSummaryJoin` (`ap.md` §8.8, §8.9, `analyzer-core.md` §5.4)

THE STANDING JOINS. Two utilities for the rule "the input that arrives last completes the combination". `KaryJoin` has
`k` slots of one input type and owns them; `StandingJoin` has two sides of two types that the Part I stores own:

```kotlin
/** The standing k-ary join (DD14). `add(s, x)` stores x in slot s and returns the NEW full combinations: x at s, a stored
 *  input at every other slot. The input that arrives last completes each combination, so each combination comes out
 *  exactly once, in every arrival order; an input that is in two slots joins with itself. Single writer. REPLACE of the
 *  rule assumptions of TaintSinkTracker (ap/ifds/taint/TaintSinkTracker.kt:173-240): the same "the last input sees every
 *  earlier input" join, with premise sets and layers. */
class KaryJoin<T : Any>(val arity: Int) {
    private val slots = Array(arity) { LinkedHashSet<T>() }

    fun add(slot: Int, x: T): List<List<T>> {
        if (!slots[slot].add(x)) return emptyList()                     // stored already (exact deduplication, E-3)
        val out = ArrayList<List<T>>()
        val acc = ArrayList<T>(arity)
        fun product(k: Int) {
            if (k == arity) { out += ArrayList(acc); return }
            for (y in if (k == slot) listOf(x) else slots[k]) { acc += y; product(k + 1); acc.removeAt(acc.lastIndex) }
        }
        product(0)
        return out
    }
}
```

| User | Slots | Input |
|---|---|---|
| `ConjunctionStore.add` (below) | one per literal of a conjunction (`ap.md` §4.6) or of a conjunctive sink (`ap.md` §4.9) | `Input(premise, layer, facts)` from `MarkCheck.Holds` |
| `NdSummaryJoin` (below) | one per member of a summary with several premises (`ap.md` §4.6, E6); the conclusion is a `TaintTree` | the subscription of the analyzer that satisfies the member |


```kotlin
/** The two-sided standing join. The caller stores and deduplicates each side (E-3) BEFORE it calls newA or newB; a new
 *  element meets every stored element of the other side that `near` gives, so each pair meets once (one thread, O1).
 *  `near` must be symmetric: b is in nearB(a) if and only if a is in nearA(b). Users: the requests and the links of
 *  RunMethodAnalyzer (analyzer-impl.md §4.8): nearB = AddedFactStore.overlapping, nearA = RequestStore.overlapping
 *  (both path-index lookups, Part I §7.2), meet = ApOps.requestAction (Part I §5.10). */
class StandingJoin<A, B>(private val nearB: (A) -> Sequence<B>, private val nearA: (B) -> Sequence<A>,
                         private val meet: (A, B) -> Unit) {
    fun newA(a: A) { for (b in nearB(a)) meet(a, b) }
    fun newB(b: B) { for (a in nearA(b)) meet(a, b) }
}
```

| User | Join | Input |
|---|---|---|
| the request × link join (`analyzer-impl.md` §4.8, events E2, E5, E7) | `StandingJoin`: the requests (`RequestStore`) and the links (`AddedFactStore`) | a request, a link |

WHY TWO FORMS. Both keep the invariant "the input that arrives last completes the combination, so each combination
comes out once" (`Store.standing_complete`). They differ in what they own and in what a combination is:

| | `KaryJoin<T>` | `StandingJoin<A, B>` |
|---|---|---|
| sides | `k >= 2` slots, one type | 2 sides, two types (a request, a link) |
| storage and deduplication | its own slots (`LinkedHashSet`) | none: the Part I stores (`RequestStore`, `AddedFactStore`) store and deduplicate, and the analyzer reads them at the barrier |
| partners of a new input | every stored input of every other slot: the full product (a literal input carries no position to index) | only the overlapping elements: the path-index lookups of the stores (`around`, Part I §7.2) |
| result | the list of new combinations (the caller makes one conjunction result or one E6 application per combination) | a callback per pair (`requestAction`: answer, climb or nothing) |

A `KaryJoin` with arity 2 is a `StandingJoin` whose `near` is "all stored inputs of the other slot". The converse does
not hold: a `KaryJoin` slot cannot use the stores' index without the index of every slot, and for `k >= 3` a "near"
of one new input does not bound the product. So the product code stays in `KaryJoin`, and `StandingJoin` is the two
loops above; no line of logic is in both.

```kotlin
/** ap.md §8.9. RUN. The literals of a source conjunction (an ND source) and of a conjunctive sink; a pass rule never
 *  makes a conjunction (interpreter.md §4.2). A combination has the union of the input premise sets WITHOUT the zero
 *  fact (ApManager.union): `{zero}` only if every input has `{zero}`. Its layer is NORMAL if all literal contributions
 *  are NORMAL, otherwise DEMAND. Premise count and next-run hand-off selection do not decide that layer. */
class ConjunctionStore(private val m: ApManager) {
    /** One input of a literal: its premise set and the layer of its contribution (§4.6: MarkCheck.Holds.normalPart gives
     *  a NORMAL input, demandPart a DEMAND input; one layer per Holds value), and for a conjunctive sink the facts on which the literal
     *  holds (§4.9: MarkCheck.Holds.facts). */
    data class Input(val premise: PremiseKey, val layer: Layer, val facts: Facts? = null)
    class Combination(val premise: PremiseKey, val layer: Layer, val inputs: List<Input>)

    private val joins = HashMap<Pair<Any, CommonInst>, KaryJoin<Input>>()

    /** `rule`: the conjunctive micro edge (a ConjunctiveEdge) or the sink alternative (a conjunctive SinkRule: one object
     *  per alternative, interpreter.md §5.3). So the join is per (conjunctive micro edge or sink alternative, statement),
     *  and its slot is the literal index. Returns the NEW full combinations. */
    fun add(rule: Any, statement: CommonInst, arity: Int, literal: Int, input: Input): List<Combination> =
        joins.getOrPut(rule to statement) { KaryJoin(arity) }.add(literal, input).map(::combine)

    private fun combine(inputs: List<Input>) = Combination(
        inputs.map { it.premise }.reduce(m::union),                     // the union without the zero fact (ap.md §4.6)
        if (inputs.any { it.layer == Layer.DEMAND }) Layer.DEMAND else Layer.NORMAL, inputs)

    /** analyzer-core.md §5.4 (E6): one join per (callee, premise key, layer of the publication, call statement). `S` is the
     *  link type of the analyzer (its Subscription), so this package does not depend on bidi.engine. */
    data class NdKey(val callee: MethodKey, val premise: PremiseKey, val layer: Layer, val call: CommonInst)
    private val ndJoins = HashMap<NdKey, NdSummaryJoin<*>>()
    @Suppress("UNCHECKED_CAST")
    fun <S : Any> ndJoin(key: NdKey): NdSummaryJoin<S> =
        ndJoins.getOrPut(key) { NdSummaryJoin<S>(m, key.premise.size) } as NdSummaryJoin<S>
}

/** E6: the members of a summary with several premises (a KaryJoin) and its conclusion, which grows by deltas. The
 *  summary has a PremiseSet, so its conclusion is TAINT (ap.md §4.6): one TaintGroup per base (T1, T4, T5). */
class NdSummaryJoin<S : Any>(private val m: ApManager, arity: Int) {
    private val members = KaryJoin<S>(arity)
    private val full = ArrayList<List<S>>()                              // the full combinations so far
    private val interners = m.newInterners()
    private val conclusion = HashMap<AccessPathBase, TaintGroup>(2)

    /** A subscription that satisfies member `index` (it goes under EVERY index that it satisfies): the new full
     *  combinations, each with every stored conclusion value. */
    fun addSubscription(index: Int, s: S): List<Pair<List<S>, TaintTree>> {
        val fresh = members.add(index, s)
        full += fresh
        return fresh.flatMap { c -> conclusion.values.flatMap { it.all() }.map { c to it } }
    }

    /** A new conclusion: its delta with every full combination (the key holds no conclusion, so every order works). */
    fun addConclusion(g: TaintTree): List<Pair<List<S>, TaintTree>> {
        val delta = conclusion.getOrPut(g.base) { TaintGroup(m, interners.taint) }.add(g) ?: return emptyList()
        return full.map { it to delta }
    }
}
```

### 7.11 `SourceHitStore` (`ap.md` §8.11)

```kotlin
/** ap.md §8.11. HAND-OFF of a backward run; owner: the backward RunMethodAnalyzer (O1). The forward form of a micro edge is a
 *  data class (PathEdge), equal in every run (interpreter.md I5). */
class SourceHitStore {
    private val hits = HashSet<Triple<MethodKey, CommonInst, PathEdge>>()
    fun add(method: MethodKey, statement: CommonInst, forward: PathEdge): Boolean = hits.add(Triple(method, statement, forward))
    /** The driver reads the hits at the barrier (O5) and makes the source seeds of the next forward run (§9.2). */
    fun entries(): Sequence<Triple<MethodKey, CommonInst, PathEdge>> = hits.asSequence()
}
```

### 7.12 `VulnerabilityStore` (`ap.md` §8.10)

```kotlin
typealias RuleId = CommonTaintConfigurationSink                        // REUSE: the same rule object in every run

/** ap.md §8.10: the key; the same key in two runs is the same vulnerability. `method` is the METHOD of the method key,
 *  WITHOUT the context: one sink statement that the analysis reaches in several contexts is ONE vulnerability
 *  (ap-history.md F67). Each witness keeps its own method key. */
data class VulnerabilityKey(val rule: RuleId, val method: CommonMethod, val statement: CommonInst)

/** ap.md §4.9: a sink edge. `facts` is the part of the conclusion on which the sink holds (MarkCheck.Holds.facts): REACH
 *  for an unconditional sink, else TAINT. Not one leaf. A normal TAINT `facts` can have `[any-taint]/E` leaves: the sink
 *  edge is normal, so it can be confirmed (condition 1; AnyTaintExact.sink_den); `checkMark` triggered it only on an
 *  admitted location (AnyTaintEx.checkX; run 1: AnyTaintExExact.confirmed_real6X). */
class SinkEdge(val premise: PremiseKey, val layer: Layer, val facts: Facts) {
    init { check(facts !is FlowTree) }                                 // MarkCheck: a FLOW fact gives only a request
}

/** One sink edge, or the sink edge set of a conjunctive sink, of ONE alternative in ONE method key; confirmed as a whole
 *  (analyzer-core.md §7.5). `alternative`: the index of the sink alternative of the rule at the statement (its cube and
 *  its array choice, as Part II numbers them: `SinkRule.alternative`); the same index in every run and every context
 *  (interpreter.md I5). `methodKey`: the method key of the sink edges; the confirmation reads the support of the premise
 *  set in this method key.
 *  THE PATTERN of a witness (ap.md §8.10) is DERIVED, not stored: the patterns of `alternative` of the rule at the
 *  statement in the method key, `SinkRule.patterns` of that `SinkRule` in the forms of `methodKey` (the same in every run,
 *  interpreter.md I5). The hand-off reads them there (analyzer-impl.md §7.2). It seeds the backward run only with the
 *  sinks of the DEMAND entries of the report after the forward run: a key that the latest complete forward run reports
 *  and that no complete forward run so far confirmed (F70 D6, ap.md §8.10: a CONFIRMED vulnerability is final;
 *  historical HandoffMain.iteration_generalN proves this for its version; full F77 no-loss integration remains open). The backward run also fires the sink seeds of an alternative
 *  whose reversed end-fact edge applies to a requirement, also of a CONFIRMED key (THE TRIGGER OF AN END FACT, ap.md
 *  §9.2; Part II §23.4). */
class SinkWitness(val alternative: Int, val methodKey: MethodKey, val edges: List<SinkEdge>, val run: Int,
                  val endFacts: List<PathFact> = emptyList()) {
    @Volatile var confirmed: Boolean = false                           // set only at the barrier of a complete forward run

    /** ap.md §4.9 condition 3: the premise set whose joint support confirms the witness. A conjunctive sink: the union of
     *  the premise sets of its edges, without the zero fact (`{zero}` if every edge has `{zero}`). */
    fun supportPremise(m: ApManager): PremiseKey = edges.map { it.premise }.reduce(m::union)
}

/** THE REFERENCE FORMS of the confirmation per member and per link (per path, as the forms of DD2; here in
 *  `VulnerabilityStore.kt`, because `Link` is in `bidi.store`). The code is the `Support` of the driver (analyzer-impl.md
 *  §7.5); `AnyTaintSupportTest` (analyzer-impl.md §9.1) and the
 *  `VulnerabilityStoreTest` (Part I §8) compare it with these forms. No other code calls them (DD14).
 *  ap.md §4.9 condition 1 reads the layer only: a NORMAL sink edge, also with `[any-taint]/E` facts (a normal TAINT tree,
 *  DD16; Lean AnyTaintEx.Confirmed6X, ConfirmedX; the conjunction AnyTaintND.ConfirmedNzT). Condition 2: each member of
 *  the premise set is the zero fact, an exact concrete fact `(x, p, $, T)`, or, in a FORWARD RESTRICTED run, a
 *  must-premise `(x, p, [any-taint], E, T)` (an emission of a normal `[any-taint]` added fact, ap.md §6.3). These are
 *  the admissible members for confirmation. A normal restricted edge can also have a FLOW premise,
 *  but that premise cannot trigger a sink and is not confirmable. The older concrete-only normal-premise
 *  theorem is historical; current confirmation-support integration remains open (proof-status.md). */
fun confirmableMember(j: InitialAp, mode: ApMode): Boolean =
    j.isZero || (j.mark is MarkSlot.Concrete && (j.tail == Tail.EXACT ||
        (j.tail == Tail.ANY_TAINT && mode.restricted && mode.direction == Direction.FORWARD)))

/** ap.md §4.9 condition 3.2.3 on one (member, link) pair: the link supplies the member `jm` EXACTLY. Either `jm = a` (the
 *  same base, path, tail and mark: the zero fact, an exact concrete answer of run 1, or the emission `a ∩ D-c = a`), or,
 *  in a restricted run, `a` is `[any-taint]/E` (an any leaf on a NORMAL link, Part I §7.5) and `jm` (`$` or
 *  `[any-taint]/Ej`) lies inside `a` with the exclusions (`inside`, ap.md §3.4: a leaf strictly below `a` through an
 *  accessor that E admits, or at `a` with `E ⊆ Ej`) with the SAME concrete mark: every admitted location of `a` carries
 *  its mark, so every location of `jm` is supplied (Lean AnyTaintEx.SupLinkX, SupX; round 1 AnyTaint.SupLink, SupT). The
 *  same mark makes the condition explicit: for a concrete premise `inside` already implies it
 *  (AnyTaintExact.markSub_conc); without it a `*`-mark premise inside `a` would pass (AnyTaintExact.CexSupMark.cex_sup_mark,
 *  a model premise: the code cannot build a `*`-mark `[any-taint]` member, `InitialAp.init`, so the tests check the
 *  condition with a `$` member of another concrete mark).
 *  A demand link (an `[any]` added fact) supplies nothing (condition 3.2). The caller edge of the link must be normal and
 *  its premise set supported (conditions 3.1, 3.2): the driver checks them (analyzer-impl.md §7.5 `Support`). */
fun supplies(jm: InitialAp, link: Link, mode: ApMode): Boolean {
    if (link.linkLayer != Layer.NORMAL) return false
    val a = link.addedFact
    val j = jm.toPattern()
    if (a == j) return true                                                       // jm = a (also an `[any-taint]` a = a ∩ D-c)
    return mode.restricted && a.fact.tail == Tail.ANY_TAINT && j.fact.mark == a.fact.mark &&
        (j.fact.tail == Tail.EXACT || j.fact.tail == Tail.ANY_TAINT) && inside(j, a)   // `inside` reads E and Ej (A2)
}

interface VulnerabilityStore {
    fun add(key: VulnerabilityKey, witness: SinkWitness)
    fun witnessesOf(run: Int): Sequence<Pair<VulnerabilityKey, SinkWitness>>
}

/** PERSISTENT; any runner adds (O4). SEVERAL WITNESSES PER KEY (DD10): one entry per (key, alternative, method key, run,
 *  shape); its facts are the union of the parts of every witness of that entry on which the sink holds. */
class ConcurrentVulnerabilityStore(private val m: ApManager) : VulnerabilityStore {
    /** The SHAPE of a witness: its alternative, its method key, its run and, per literal, the premise set and the layer of
     *  its sink edge and the group key of its facts. One alternative has one pattern per literal, so the base and the
     *  kind of edge k are fixed; the group key adds the layer of the facts (a conjunction input can be demand on normal
     *  facts, §7.10). So two entries with one shape never have two group keys at one literal. */
    private data class Shape(val alternative: Int, val methodKey: MethodKey, val run: Int,
                             val edges: List<Triple<PremiseKey, Layer, GroupKey>>)

    private val byKey = ConcurrentHashMap<VulnerabilityKey, ConcurrentHashMap<Shape, SinkWitness>>()

    override fun add(key: VulnerabilityKey, witness: SinkWitness) {
        check(witness.methodKey.method == key.method) { "the witness is in a method key of the method of its key" }
        val shape = Shape(witness.alternative, witness.methodKey, witness.run,
            witness.edges.map { Triple(it.premise, it.layer, it.facts.groupKey) })
        byKey.computeIfAbsent(key) { ConcurrentHashMap() }.merge(shape, witness, ::union)     // atomic per (key, shape)
    }

    /** Edge k of the result: the same premise set and layer; the facts are the union (T1) of the two parts. Both parts have
     *  the same group key: the base of literal k of the alternative, the kind (REACH or TAINT), the layer of the facts. */
    private fun union(a: SinkWitness, b: SinkWitness): SinkWitness {
        check(a.alternative == b.alternative && a.methodKey == b.methodKey && a.edges.size == b.edges.size)
        return SinkWitness(a.alternative, a.methodKey,
            a.edges.zip(b.edges) { x, y ->
                check(x.facts.groupKey == y.facts.groupKey) { "T3: the union of one literal never crosses group keys" }
                SinkEdge(x.premise, x.layer, m.mergeAddDelta(x.facts, y.facts).first)                // Part I §4.2
            },
            a.run, (a.endFacts + b.endFacts).distinct())
    }

    override fun witnessesOf(run: Int) = byKey.entries.asSequence()
        .flatMap { (k, byShape) -> byShape.values.asSequence().filter { it.run == run }.map { k to it } }
}
```

`analyzer-core.md` §4.7 allows this merge and no other: the witnesses of one (key, alternative, method key, run, shape)
merge (DD10). It is not today's merge:

* Today's merge is LOSSY. Today `TaintVulnerability.mergeAdd` (`ap/ifds/taint/TaintSinkTracker.kt:27`) can drop a
  witness: an `Unconditional` node replaces a `Fact` node (`:50`), a `Fact` node with another trigger position is ignored
  (`:62`), and a `Fact` node replaces a `WithRequirement` node (`:79`). The premise sets of the dropped witness are lost.
* This merge joins only witnesses of the SAME shape: the same alternative, the same method key, the same run and, per
  literal, the same premise set, the same layer and the same group key of the facts. The confirmation of `ap.md` §4.9
  reads only the method key, condition 1 (the layer of each sink edge), and condition 3 (joint support in that method
  key). Joint support includes condition 2: each member is zero, exact concrete or a forward must-premise
  (`confirmableMember`); each nonzero member has a supplier (`supplies`). So the merged entry is confirmed
  exactly when each of its witnesses is confirmed. `analyzer-core.md` §7.5 step 2 gives the same answer.
* The alternative is part of the shape. So the union never joins the sink edges of two alternatives: two alternatives
  on `arg0` and on `arg1` (an `Argument(*)` sink) with the same premise set stay two entries, and each literal of a union
  has one pattern, one base and one group key (the `check` in `union`).
* The method key is part of the shape. So one statement reached in two contexts is one vulnerability key with a witness
  per method key, and each witness is confirmed in its own method key.
* Each sink fact stays a leaf of the union value (T1 keeps every leaf), so the merge loses no sink fact.
* A conjunctive sink: the union of `(a1, b1)` and `(a2, b2)` of one alternative also denotes `(a1, b2)`. That
  combination is a witness too: `a1` and `b2` are stored inputs of the literals of that alternative at that statement,
  and `ConjunctionStore.add` (§7.10, one join per alternative) gives every combination of the stored inputs.
* The run is part of the shape. So a witness of one run never merges with a witness of another run (the report reads
  the witnesses of each run, `witnessesOf(run)`; DEMAND is a state of the report, not of one run:
  `ReportBuilder.hasDemandVulnerability`, `analyzer-core.md` §10).
* The merge only makes the number of entries per key smaller: one entry per shape, not one per delta that triggers the
  sink.

The driver builds the report at the barrier from `witnessesOf(run)` (`analyzer-impl.md` §7.6, `Report`): the
vulnerabilities that a complete forward run confirmed, and the DEMAND entries: the keys that the latest complete
forward run reports and that no complete forward run so far confirmed (`ap.md` §8.10; F70 D6: only these seed the next
backward run); an incomplete run adds nothing and refutes nothing (`ap-history.md` F67). If no forward run is complete (an
incomplete run 1), the report has no entry, and the status gives the cause (`ap-history.md` F68 (2)). The fields of an
entry are those of `Report.Entry`; the witnesses of its key (one or more per alternative and method key) give, with the
forms (`SinkRule.patterns`), the patterns, the sink edges and the end facts of `ap.md` §8.10. Trace resolution is out of
scope: no run keeps its stores for it. THE `[any-taint]` TAIL (`ap.md` §8.10, F69): a vulnerability whose taint comes
from an `[any]`-target source (for example the whole-object DTO source of a Spring entry point, Part II §29) can be a
CONFIRMED entry: its sink edge is normal with `[any-taint]/E` facts (condition 1), and a forward restricted run can
supply its must-premise (conditions 2, 3.2.3). A strong write into the object keeps it normal with a larger exclusion,
so a sink on the written field is not reported and a sink on another field is CONFIRMED in run 1
(`AnyTaintExCases.S.run1_name_not_reported`, `S.run1_email_confirmed`; `X.fh_confirmed`; `R.z_confirmed`); an
identity callee and a source confirm in run 1, a getter and a sink in the callee in run 3 (run 1 in the spec closure
`AnyTaintEx.D6X`: `AnyTaintExCases2.I.run1_confirmed`, `PassRule.source_confirmed`; run 3 in `AnyTaintEx.DRXs`, with
the earlier restriction and hand-off, the record of the earlier design: `G.run3_confirmed`, `C.run3_confirmed`; with
the intersection `HandoffX.restrictIX` and the hand-off of the demand edges the same programs are argued, `ap.md`
§11.2). Only a vulnerability that rests on a demotion of `ap.md` §2.2 stays a DEMAND entry: the field-limit
cut, a cleaner `part` row other than `atAndBelow`/`below` one accessor below the fact (the `exact` cleaner at or below
it, any cleaner two or more accessors below it), a may target (a pass rule with an `AnyField` target), a demand input (a
demand fact, summary or record), the must-record demotion (`AnyTaintExCases2.PassRule.pass_not_confirmed`,
`G.run1_not_confirmed`; `AnyTaintExCases.CUT.cut_reports`, `CL.exact_result`). A weak update (an alias write, the
constructor pass-over, the default identity of an unresolved callee, a call-result alias) keeps an `[any-taint]` object
whole, so its false positive is now a CONFIRMED entry (before F69 a DEMAND entry), as for a `$` fact today (`ap.md`
§11.1; `interpreter.md` §0.1). Phase 3 outputs EVERY entry of the report, CONFIRMED and DEMAND,
each with a trace with only the sink statement: REUSE `VulnerabilityWithTrace` (`ap/ifds/trace/VulnerabilityWithTrace.kt:11`) and
`TracePathGenerationResult.Simple` (`ap/ifds/trace/path/TracePath.kt:23`); the trace resolver of `trace/` is not used
(`ap-history.md` F68 (1); `analyzer-impl.md` §8.1).

---

## 8. Test plan (`validation-plan.md` → AP tests → §13)

Tests are in `core/opentaint-dataflow-core/opentaint-dataflow/src/test/kotlin/org/opentaint/dataflow/bidi/ap/` and
`.../bidi/store/`, with `kotlin.test` as today (`ap/ifds/access/tree/AnyFieldMarkExclusionTest.kt`). Write each test
class before its code, in this order. Each row names the Lean theorem or `example` that it mirrors. A generic test
runs once per leaf algebra (`FlowAlgebra`, `TaintAlgebra`): the trie code is shared (DD14), so is its test.

| # | Test class | Checks (`validation-plan.md` → AP tests → §13 item) | Lean |
|---|---|---|---|
| 1 | `FactModelTest` | `MarkSet`/`ExclusionSet` canonical form, no empty `Concrete`; `PathNode`, `InitialAp`, `PremiseSet`, `TaintLeaves` interning gives identity; `premiseOf` of one member is the `InitialAp` itself, of two or more a `PremiseSet` sorted by id; `union` is the set union WITHOUT the zero fact: `{zero} ∪ {i} = {i}`, `{zero} ∪ {zero} = {zero}`, `{i} ∪ {k} = {i, k}`, `{zero} ∪ {i, k} = {i, k}`; a `PremiseSet` with a zero member or with a `*` member is rejected; `nonZeroCount` names the edge (§4.6); `AccessorTable` decodes field, element and class accessors as `AccessorInternerTest` and REJECTS a type-info or a value accessor (W5); `TrieNode` and `Facts` equality across two interners (DD5) | — |
| 2 | `ApManagerConcurrencyTest` | 8 threads intern the same accessors, marks, paths, premises and leaves: one object each, no hang (a timeout fails the test) | — |
| 3 | `ReferenceDenotationTest` | `covers`, `overlap`, `applicable`, `inside`, `cleanPos` against `den` (`ap.md` §3.2) on a bounded universe with a fresh accessor and a fresh mark (item 2); F71 (DD18): `insideDemand(j, d)` holds exactly when every location of `j`, with its mark, is a location of `d` (it is `covers(d, j)`), and `insideLoc(j, d)` exactly when the same holds with the marks ignored; `marksMeet` on every cell of the mark table of `ap.md` §6.4 (the marks `T`, `U`, `*`, `*∖{T}`, `*∖{U}` on both sides), against `Handoff.concMarkB`, and on a concrete conclusion mark it equals `markSub(D-p.mark, c)`; the mark test of the reference `emit` equals `markSub(D-c.mark, a.mark)` on every concrete mark, with `*∖{T}` against `T` false | `coversB_sound`, `overlapB_of_common`, `cleanPos_inside_sound`, `cleanPos_disjoint_sound`; F71: `Handoff.insideB_covers`, `insideB_loc`, `insideB_mark`, `RAux.concMarkB_conc`, `RAux.markMatchB_conc` |
| 4 | `TrieOpsTest`, `TrieInternerTest` | each generic function of §4.2 and §4.6 on both algebras against a list-of-leaves model: `mergeAdd`/`mergeAddDelta` (the delta unions to the merge; a shared subtree pair is merged once; a deep trie needs no deep stack), `mapChildren` (an unchanged child list returns the node itself), `replaceChild` (insert, replace, and remove with a null child), `withLeaf`, `mapLeaves`, `retainChildren`, `prepend`/`chain` (also a spine shorter than the path, and a tip with its own leaf), `minusNode`, `forEachLeaf`, `foldUnder`, `foldUnderDelta` (after a group add to a folded trie, it equals `foldUnder` of the whole merged trie; also with an `admits` that rejects a child accessor below an absorbing leaf), `subtract` (also with `admits`), `filterPath`, `FieldLimitCut`, `walk`; `graft` MERGES at nested occurrences (`g = {ret.*, ret.f.*}` with `r` at `[]` and `[f]` keeps both `ret.f` leaves), drops a node of `g` with no occurrence, keeps a shared subtree of `g` shared; no operation collapses a repeated field, a chain of three `[e]` or a path below a class accessor (only `L` bounds a path); `foldAll` and `foldLeaves` include the leaf of the node itself (a trie with only a root leaf; a cut child with a leaf); `boundedDepth` and `hasAny` equal a recount; construct 32,767- and 32,768-accessor chains iteratively and check the exact Int cache and skip guard; checked depth addition rejects overflow. `TrieInterner`: two equal tries give one object, with `interned = true`; after `SoftReferenceManager.cleanup()` the next intern makes a new table, and the earlier interned nodes stay valid and equal (only sharing is lost); with a disabled manager (`createRef` gives null) interning still works within one call; `internIfRequired` interns a trie of `SIZE_TO_FORCE_INTERN` nodes at once and one add in `INTERN_RATE` otherwise | `Tree.rule1_mem`, `Tree.prependPath`; `CurrentDepthCache.depthWitness`, `skip_iff_fits`, `stored_skip_iff_fits`, `short_cache_skips_over_limit`, `exact_cache_requires_cut` |
| 5 | `KindTest` | the types enforce W1, W2 and W8 (a): `flowTree` gives the Empty exclusion in the demand layer; a FLOW tree has no `[any-taint]` leaf (no concrete mark); `Results` routes each result to its kind and canonical key (a source on REACH gives TAINT, the zero keep edge and the zero binding `zero.* -> zero.*` on `Reach.NORMAL` and `Reach.DEMAND` give the same `Reach`, a `*` edge on FLOW gives FLOW); THE LAYER NAMES THE ANY LEAVES (DD16): a normal TAINT tree with an any leaf reads `[any-taint]` through `leaves`, the same root in the demand layer reads `[any]`; `Results` has no W6 split (a normal any leaf stays normal); W6 is the rule of `put`: a may `[any]` target gives a demand result; the `init` check of `FlowTree` (W1 demand) fails on a bad `withRoot`; `InitialAp` rejects an `[any-taint]` premise with the mark `*` (W8 (a)) and accepts its exclusion; THE EXCLUSION OF A TAINT TREE (A2): `taintTree` gives the Empty exclusion in the demand layer and for a root with no any leaf, `TaintTree.init` rejects any other exclusion, `withRoot` keeps it (and drops it with the last any leaf), `leaves` gives `[any-taint]/E`, `Results` keys TAINT by (layer, exclusion), the group key holds it (item 1) | `Invariant.final_star_legal`, `Coverage.edge_conc`, `AnyTaintExKinds.D6X_any_conc`, `D6X_flow_no_any_taint`, `D6X_flow_no_excl`, `kinds_D6X`, `DRX_must_premise`, `AnyTaintSim.kinds_DRT`, `AnyTaintEx.carriesB`, `normX` |
| 6 | `ApplyEdgeVectorsTest` | every `example` of `Cases.lean` and `RestrictedCases.lean`, on the reference forms AND on `ApOps`; the model vectors with a normal `.any` result (`validation-plan.md` → AP tests → §13 item 1) assert the layer of `ap.md` §4.1: demand for a may `[any]` target or an `[any]` input, a normal `[any-taint]` fact for a taint edge or a normal `.any` input with a CONCRETE mark (a `.any` input with the mark `*` is `[any]` in the demand layer); the `decide` vectors of `AnyTaintEx.Vec` with their exclusions (`setter_keep`, `read_excluded`, `read_admitted`, `above_star_excl`, `source_any_target`, `below_keeps`, `summary_ann`) and, round 1, `AnyTaint.EmitVec`, `AnyTaint.Sanity`, `AnyTaintCases.PassRule.source_vs_pass`, `Cut.cut_transfer`; the `[any-taint]` rows of `ap.md` §4.2 on the tree form (§5.3); the zero binding on the zero fact (item 1) | `Cases.lean`, `RestrictedCases.lean`, `AnyTaintEx.Vec`, `AnyTaint.EmitVec`, `AnyTaint.Sanity`, `AnyTaintCases` |
| 7 | `FactsEquivalenceTest` | random `Facts` of each kind and random edges (every tail and mark row, the static exception, `*∖X`, the bindings of Part II §28.2 with the zero binding, and summary-like edges with a `toExclusion` on REACH inputs too: a zero-premise summary `zero.$ -> P.[any-taint]/Et`): the results of `applyCompiledEdge`, read by `leaves`, equal the per-leaf `concat`, layer, exclusion, mark exclusion and requests included; the same for `clean`, `filter`, `limit`, `restrict`, `checkMark`, `withoutMarks` (against `clean` with `atAndBelow` at the root per mark) and `satisfying` against their reference forms, also `satisfying(..., record = true)` with a must-premise `[any-taint]/Ej` and a `*/Ej` premise (the exclusion `Ej` against an any leaf, `applicable`); `restrict` (the intersection of F70: the union of the results of `restrict` per leaf, with random `D-c` and `D-p` of every tail, exclusion and mark: `*`, `*∖X` and `T`, F71; TAINT values with two or more marks, so that the mark test removes some leaves and keeps others) and `demandPart` (against per-leaf structural selection, test 24) (item 2) | `Tree.applyTreeE_mem`, `applyTreeE_den`, `applyTreeE_grouped_key`; the restriction: `RStore.restrictTreeE_mem_U` is the tree theorem of the restriction before F70, the tree form of the intersection is argued against its per-path forms `Handoff.restrictI`, `HandoffX.restrictIX` (since F71 with the mark tests `Handoff.insideB`, `concMarkB`, `HandoffX.insideXB`; the mark test leaf by leaf of `ap.md` §7.4 is argued too) |
| 8 | `MarkGateTest` | every row of `ap.md` §4.1 steps 4 and 5 per kind: FLOW + `T` gives no fact and the request `T` if `T ∉ X`, nothing if `T ∈ X`; TAINT + `T` keeps the leaves with `T`; `*` passes every mark; a `*∖Y` target drops the marks in `Y`; the `check` preconditions of `CompiledEdge` fail: S7 (a `*∖X` premise; a concrete target under a `*` premise), S8 (a `$` premise with `*`; a `$` premise with a `*` target; a `$` target under a `*` premise) and W1 (`x.[any] (T) →_{f} y.$ (T)`: a non-empty exclusion with no `*` side); the request comes after the position test (an apart fact gives none) (items 3, 4) | `CoreAux.markComp_sound`, `Core.applyEdge_sound` |
| 9 | `MergeRulesTest` | per kind: T1; FLOW: T2 and T2' only for equal content, the delta of T2 is the whole merged tree minus what the §8.1 subsumption drops (one case with a subsuming tree, one without), no union across trees; TAINT: one demand tree and one normal tree per exclusion; T4 deltas union to the value; a leaf below a stored `[any]` of the same mark gives null, also on its second arrival (the termination guard of T5); the same in a NORMAL TAINT tree for an `[any-taint]` leaf below a stored `[any-taint]/E` leaf through an accessor that E admits, and NOT through an accessor in E (T5 and `ap.md` §8.1 in its layer); a `$` leaf at or below a stored normal `[any-taint]` leaf of its mark is NOT dropped and NOT folded (DD16: `{x: [any-taint] (T)}` then `x.g.$ (T)` keeps both leaves, and after `clean(x.f, exact, T)` the leaf `x.g.$ (T)` is still NORMAL); a normal leaf below a stored demand `[any]` leaf is NOT dropped (a demand leaf never subsumes a normal one); TAINT with the exclusion (A2): one normal tree per exclusion, T1 merges the same exclusion, two normal trees with equal content and the exclusions `{f}`, `{g}` merge to one tree with `{}` (T2: the intersection, the whole merged tree as the delta), never a union of exclusions (T3), an `[any-taint]/{f}` leaf subsumes `[any-taint]/{f, g}` at its node and not `[any-taint]/{}` (item 8) | `Tree.rule1_mem`, `rule2_den`, `rule2_mark`, `Subsume.merge_inter`, `union_loses_pairs` |
| 10 | `SubsumptionTest` | `FlowGroup.add` and `TaintGroup.add` drop exactly what `subsumes` (§6) drops; never across layers or kinds | `Subsume.subsumes_sound`, `recordSubsumesLB_layer` |
| 11 | `LayerRulesTest` | items 3 and 4: the cut, W6, W2, demand in -> demand out; THE `[any-taint]` ROWS (W8, `validation-plan.md` → AP tests → §13 item 3): a source with an `[any]` target gives a normal `[any-taint]` result with the Empty exclusion and the same edge as a pass rule gives `[any]` in the demand layer; `applyEdge(..., may = true)` gives every result in the demand layer, also a `$` result of the reversed pass rule `Q.[any] (T) -> P.$ (T)`; every row of the two tables of `ap.md` §4.1 with an `[any-taint]/E` fact or target: THE EXCLUSION ROWS of §5.3 (no row demotes: the keep edge of a strong write gives `E ∪ {f}`, a `*/E'` summary gives `E ∪ E'`, the case `above` gives the edge exclusion and nothing when E excludes the step, the case below `r ≠ []` keeps E); the demotions that stay (`ap.md` §2.2), each with no exclusion: the field-limit cut, a cleaner `part` row other than `atAndBelow`/`below` one accessor below the fact (the `exact` cleaner at or below it, a cleaner two or more accessors below it), a may target, a demand input (a demand fact, summary or record), the must-record demotion (test 16); a demand-layer `[any-taint]` result reads `[any]`; the `CompiledEdge` checks fail on an `[any-taint]` target or premise with the mark `*` and on a `fromExclusion` or `toExclusion` of a side that is not `[any-taint]` | `Invariant.applyEdge_demand_monotone`, `AnyTaintEx.Vec.setter_keep`, `above_star_excl`, `cut_drops`, `AnyTaintExExact.keep_row_exact`, `above_row_exact`, `below_row_exact`, `CexAbove.cex_above`, `AnyTaintCases.Cut.limitF_cut_demand`, `AnyTaintExCases2.PassRule.source_vs_pass` |
| 12 | `FieldLimitTest` | `limit` equals the per-leaf `limit` (§6) for both kinds; `L = 0`; an uncounted class accessor stays; the `MethodEdgeStore.add` assert fires on a value deeper than `L` (W3, §5.7) | `limitF_sound` |
| 13 | `CleanerTest` | every row of the two tables of `ap.md` §4.7, per kind; F75 same-base abstract split before the path test, for all reaches and disjoint/off-spine leaves; run-1 request also for T ∈ X; preserve path/tail/field exclusion/layer; different-base pass; no cleaner request in either restricted direction; uniform-tree result and request set equal the per-leaf reference; the all-marks cleaner; a cleaned fact `*∖{T}` through a field write past the field limit keeps its mark exclusion in the cut demand tree (`limit` of the FLOW tree keeps `c.markExclusion`, §5.7); on an `[any-taint]/E` fact `(x, p, [any-taint], E, T)` (`interpreter.md` §5.2 ON AN `[any-taint]` FACT; program `AnyTaintExCases.CL`): the `below` cleaner at `x.p` keeps `(x, p, $, T)` NORMAL, `atAndBelow` at `x.p` drops it; one accessor below, at `x.p.f`, `atAndBelow` gives `(x, p, [any-taint], E ∪ {f}, T)` and `below` gives it and `(x, p.f, $, T)`, all NORMAL; `exact` at `x.p.f` and a cleaner two accessors below give `[any]` in the demand layer with no exclusion; a cleaner below an accessor in E changes nothing (item 5) | `BaseCleaner.vectors_pass`, `cleanRes_sound_witness`, `cleanRes_exact`, `uniform_batch_equiv`; unchanged concrete/all-marks rows: `CleanCases` in `Cases.lean`, `Core.cleanRes_sound`, `Exact.cleanRes_exact`, `AnyTaintEx.Vec.clean_atAndBelow`, `clean_below`, `clean_exact`, `clean_excluded`, `below_new_fact`, `AnyTaintExExact.cleanResX_exact`, `CexExactCleaner.cex_exact_cleaner`, `AnyTaintExCases.CL.clean_vectors` |
| 14 | `TypeFilterTest` | accepted path passes with its tail; rejected path drops; `FilterNext`; `and` is the conjunction (of the paths and of the policies); the mark policy drops a rejected concrete mark of a TAINT leaf, both tails, at the root path (level 0) and at each node of the `[e]` chain (`b.[e].$ (T)` with level 1, `b.[e].[e].[any] (T)` with level 2, as today on `byte[]`, `int[][]`, `Integer[]`), after the path filter; a leaf below a field or off the `[e]` chain stays; an `[e]` child whose marks all go is removed; an unchanged tree is returned as it is; a FLOW tree has no policy (item 6; `interpreter.md` §5.1) | `Core.filt_keeps` (path part; the policy is gap G6, no theorem) |
| 15 | `MarkCheckTest` | the `check` vectors of `ap.md` §4.9 on `checkMark` and `markCheck`; `*∖X` with T ∈ X; the static premise; REACH holds for the zero pattern; `Holds.normalPart`/`demandPart` equal `conjDemand` per leaf (a fully covered input is normal only; a normal `[any-taint]` input that overlaps a `$` literal and is not covered by it is normal only, `ap.md` §4.6; an `[any]` input is demand); `(x, ., [any-taint], T)` triggers a `$` pattern at `x.f` and an `[any]` pattern at `x` (as `[any]`, `ap.md` §4.9), in the normal layer; `(x, ., [any-taint], {f}, T)` does not trigger a `$` pattern at `x.f` or `x.f.g` and is no input of a literal there, and it triggers `x.g` and `x` (the exclusion, A2); `without(c, Holds.facts)` has no leaf of the part and keeps every other leaf, also when the part has only `$` leaves of a tree with an exclusion | `check_sound`, `check_request_star`, `ND.conjLayer`, `ND.Example.c3_normal`, `AnyTaintND.conjLayerT`, `lit_loc`, `Example.layer_new`, `AnyTaintExact.sink_den`, `AnyTaintEx.Vec.check_vectors`, `AnyTaintExCases.S.run1_name_no_trigger` |
| 16 | `SummaryKindsTest` | `applySummary` for every kind pair of §5.4 equals the per-leaf `concat` with the summary edge, also for a summary with a root leaf (`ret.$ (T)`, `arg0.[any] (T)`, a normal `arg0.[any-taint] (T)`: its kind edge has the target `[any-taint]` and keeps the layer); `satisfying(FLOW a, concrete j)` is null and no request comes (item 9); `applyCombination` gives the layer of `ND.DN.ndBind`; THE MUST-PREMISE (`ap.md` §4.3, §13 item 10): a summary of `(p, .f, [any-taint], T)` applies only to an added fact that it lies inside (`(p, ., [any-taint], T)`: normal results; `(p, ., [any], T)` on a demand link: demand results), never to `(p, .f.g, [any-taint], T)`; WITH THE EXCLUSION (A2): `(p, ., [any-taint], {f}, T)` does not satisfy `(p, .f, [any-taint], T)` and satisfies `(p, .g, [any-taint], T)`; at one path `[any-taint]/{f}` satisfies the premise `[any-taint]/{f, g}` and not `[any-taint]/{}`; the kind edge of an `[any-taint]/Eg` summary leaf gives `Eg` (`toExclusion`), on a TAINT added fact AND on the zero fact: the zero-premise summary `{zero} -> (ret, ., [any-taint], {name}, T)` on `Reach.NORMAL` gives `(ret, ., [any-taint], {name}, T)` NORMAL (the REACH branch of `EdgeApplication` passes `toExclusion`; program `mk`, test 23), and a must-premise `[any-taint]/Ej` filters the case below by `Ej` (`fromExclusion`); `applicable` reads `Ej` against an any leaf (§5.4 `applicablePart`): a must record `[any-taint]/{f}` does not apply to the added fact `[any-taint]/{g}` at its path and applies to `[any-taint]/{f, g}`, a `*/{f}` premise applies to `[any-taint]/{f}` at its path and not to a demand `[any]`; a must record by `satisfying(..., record = true)` gives the whole part, and the split of §5.4 (THE RECORD DEMOTION) gives the result of `inside` and the same facts in the demand layer with no exclusion for the `applicable`-only part (the program `AnyTaintExact.CexApp`); `recordDemand` (§6) agrees per leaf | `Coverage.summary_step`, `applicable_mark`, `ND.DN.ndBind`, `AnyTaint.SatInside`, `satI_inside`, `recLayer_fact`, `AnyTaintEx.satX`, `satX_inside`, `recLayerX`, `applySummaryX`, `Vec.sat_vectors`, `Vec.summary_ann`, `AnyTaintExact.CexApp.cex_app` |
| 17 | `EmissionTest`, `RestrictionTest` | Current per-leaf emission and restriction against §6; sharing across T/U leaves under a normalized `[any](*)` entry; no emission or request for abstract added facts under concrete demands; concrete patterns preserve must tails/exclusions; reduction checks full premise containment, conclusion mark overlap, exit tails and unchanged layer. Compare generic reference results as Conclusions, including abstract `$`; convert only canonical representable cases to Facts. Reject unsupported canonical store shapes. | `CurrentDemand.emitCertificate`, `restrictCertificate`, `reduceEmission`, `normalizeCertificate`, `restrict_index_equiv`; `CurrentTreeReduction.tree_reference_equiv`; `CurrentKinds.emitW_tree_init`, `restrictI_tree_flow` with their paired guards; historical `shapeSeq_tree_shape` does not prove F77 preservation |
| 18 | `RequestActionTest` | answer, climb, nothing; the chain answer; `ap.md` §4.10 items 2–4; the run-1 case of §5.10: a FLOW link under a concrete callee request climbs; `requestAction` rejects a premise that is not a policy fact `(x, [], *, {}, *)` or a static position answer `(S, p, *, {}, *)`; `EdgeApplication.flowHit` and `checkMark` return no request in a restricted run; F75 `FlowClean` also keeps the same-base mark exclusion there without a request; the restricted analyzer has no request store and `raise` rejects an accidental request (`analyzer-impl.md` §4.3); THE POSITION-REQUEST VECTORS of item 9 (`ap.md` §4.10), on `ApOps.applyEdge` with `statementEdge = true` in run 1: an identity static `*` edge at the root `[]` and at a class `[<C>]`, a static read `x = C.s`, the class keep edge of a write `C.s = x` and a pass rule between static fields each raise the position request cut to the static field and give no fact; a sink on `S.<C>.f` raises the ordinary mark request; a deep read below a static field is the ordinary case `above`; the root keep edge adds `<C>` to the exclusion; an added fact at or below the position answers it, an added fact above it does not; the climb through a caller edge on `S`; the mark answer on a static premise (the added fact itself at or below, the chain answer above) (item 9) | `answerInit_covers`, `Statics.CexClean.shallow_misses` |
| 19 | `ReversalTest` | Generic `revEdge` keeps its micro-edge table and rejection of must premises/nonempty must targets. `reverseRecordLeaf` separately checks normal singleton forward records: exact U→must/E T gives STAR/E T→exact U NORMAL; must/Ej U→must/E T gives STAR/E T→ANY U DEMAND; must/Ej U→exact T gives exact T→ANY U DEMAND. Preserve E admission and marks; reject excluded first accessors and invalid native/demand shapes; discard suffixes for exact outputs; never emit, start or persist the view. Other leaves keep generic reading. | `CurrentMustReverse.exact_converse`, `exact_shape`, `must_read_demand`, `Certificate.admitted_transfer`, `excluded_rejected`, `must_transfer`, `normal_must_bad_pair`; generic `Reverse.revEdge_exact` |
| 20 | `PathTrieTest` | `lookupPrefixes`, `lookupExtensions`, `around` equal their list filters on random keys; `add` of a value that is at the position already returns false and stores nothing; the lookups give the values in insertion order (item 14) | `Store.lookupPrefixes_equiv`, `lookupExtensions_equiv`, `mem_around_indexBy` |
| 21 | `KaryJoinTest` | `KaryJoin`: every combination comes out exactly once, in every arrival order (all permutations of a few inputs, arity 2 to 4); an input in two slots; a repeated input gives nothing. `StandingJoin`: with two `PathTrie`-backed sides and an overlap `near`, every overlapping pair meets exactly once in every arrival order, and no other pair meets | `standing_complete` |
| 22 | `AddedFactStoreTest`, `RequestStoreTest`, `DemandStoreTest`, `RecordStoreTest`, `ConjunctionStoreTest`, `VulnerabilityStoreTest`, `MethodEdgeStoreTest`, `RunSummaryStoreTest` | each index against its list filter; a new caller edge of an existing added fact is a new link (the example of `ap.md` §4.5); each leaf has one key, except a `$` leaf of a normal TAINT value, which can be in the key `E` and in the key `{}` of one caller reference (two links, and the consumers deduplicate: one initial fact, one edge; §7.5); `AddedFactStore.overlapping` equals `links().filter { overlap(it.addedFact, q) }` for queries above, at and below the leaves, and for a `*/E` leaf above the query whose `E` excludes the next accessor (no link); `ApOps.leavesNear` equals `leaves(f).filter { overlap }` per kind; 100 000 caller keys at one position are added and queried in linear time (a timeout fails the test); `RequestStore.add` rejects a premise that is not `(x, [], *, {}, *)` (item 9); a backward edge `{jb} → zero` is stored as REACH and its repeat gives null; the kind assert of `add` (K6), and a `PremiseSet` key with a REACH or FLOW value is rejected (`MethodEdgeStore`, `RunSummaryStore`); `MethodEdgeStore.edgesAt` (both overloads) against a list of every added edge: the REACH bits as `Reach` per layer, one premise or all, the pattern overload returns the whole stored value; a combination of a `{zero}` input and an `{i}` input has the premise `{i}`, and of two `{zero}` inputs `{zero}` (`ConjunctionStore`); `ConjunctionStoreTest`: two alternatives of one rule (two `SinkRule`s of one `rule`, each with the literal `(arg0, [], $, T)`) stay apart: an input of the first never completes a combination of the second (`ap.md` §8.9; item 14); `RecordStoreTest`: `persist` (R1) skips a DEMAND summary, a `PremiseSet` summary and a backward zero-premise summary, keeps a forward `{zero}` summary and a normal one-premise summary, and gives a repeat of a record as no new record; R1 WITH `[any-taint]` (one notion of complete: a normal edge): a forward normal summary with `[any-taint]/E` leaves and a forward must record are kept, with their exclusions; a normal backward summary with an any leaf fails the W6 assert (the backward run has no `[any-taint]`); `AddedFactStoreTest`: an `[any-taint]` added fact on a normal link and an `[any]` one on a demand link of one path are two keys, two `[any-taint]` added facts with the exclusions `{}` and `{f}` are two keys, and `links()` reports `Tail.ANY_TAINT` with its exclusion and `Tail.ANY`; `DemandStoreTest`: a pattern keeps its tail as given (`$`, `*/E` or `[any]`); `covering` equals the list filter "the path of `D-c` is a prefix of the query" on random keys, `near` contains every pattern of `covering`, and the zero demand stays implicit (F70 D5); two patterns that differ only in their marks (`T`, `U`, `*`, `*∖{T}`) are two entries, and `covering` and `near` return both: the index is keyed by the chain only, and `emit` and `restrict` test the marks (F71, `ap.md` §8.6); `RunSummaryStoreTest`: `all()` keeps every summary before the restriction and `demandEdges()` exactly the pieces given to `addDemand`, each with the K6 check; `RecordStoreTest`: every raw group that `demandPart` keeps out of the hand-off is a record that `persist` adds (a forward normal one-premise summary; a backward normal summary of a non-zero premise), and a backward demand summary is no record and is all in `demandPart`; inside a `runWithMemoryManager` region (the barrier guard, B4) two equal conclusions of `SIZE_TO_FORCE_INTERN` nodes of two method keys persist to one interned node (§4.5 THE LIFETIME OF A TABLE), and a cancelled `Cancellation` stops `persist` at its next method key; `SinkWitness.supportPremise` drops the zero fact; E6 in two orders. `VulnerabilityStoreTest`: two alternatives of one `Argument(*)` sink on `arg0` and on `arg1`, both with `{zero}` and normal, give two entries of one key and no exception; two method keys (two contexts) of one method at one statement give ONE `VulnerabilityKey` and two entries, each with its own method key; a witness whose method key is not of the key's method is rejected; the merge of one entry keeps every sink leaf and its end facts, never crosses group keys (also a demand input on normal facts), and the confirmation of a merged entry equals that of its witnesses (DD10); THE REFERENCE FORMS OF THE CONFIRMATION (`validation-plan.md` → AP tests → §13 item 17): `confirmableMember` accepts a must-premise only in a forward restricted run; `supplies` accepts a `$` member and a must-premise inside a normal `[any-taint]` link with the same mark, rejects a `$` member with another concrete mark (the mark condition; its reason is the model premise of `AnyTaintExact.CexSupMark.cex_sup_mark`, which `InitialAp.init` cannot build: test 5) and every member of a demand `[any]` link, and in run 1 only `jm = a`; with the exclusion (A2): a normal link `(o, ., [any-taint], {name}, T)` supplies `(o, .email, $, T)` and not `(o, .name, $, T)` (items 7, 14; the report and output parts of item 14 are `analyzer-impl.md` §9.1 rows 21 and 22) | `standing_complete`, `RStore.near_equiv`, `PipelineStore.record_lookup`, `NDConfirmed.CexSites.cex_sites`, `AnyTaintEx.SupLinkX`, `SupX`, `AnyTaint.SupLink`, `SupT`, `AnyTaintExact.CexSupMark.cex_sup_mark`, `markSub_conc` |
| 23 | `AnyTaintProgramsTest` (unit level, on `ApOps` and the stores, each program as a hand-made run with the forms of the Lean program; the analysis tests of the same programs are `analyzer-impl.md` §9.1) | THE WORKED PROGRAMS OF THE SPEC CLOSURE `AnyTaintEx.D6X` (run 1) and of `AnyTaintEx.DRXs` (the restricted runs with the earlier restriction and hand-off, the record of the earlier design; with the intersection `HandoffX.restrictIX` the same results are argued, `ap.md` §11.2) (`validation-plan.md` → AP tests → §13 items 16, 17; the round-1 programs re-derived in `AnyTaintExCases2`): G (`root() { dto = srcAny(); x = get(dto); sinkAny(x); }`, `get(p) { return p.f; }`): run 1 gives the FLOW summary of `get` as the case `above` (demand) and a demand sink edge; the backward hand-off demand `((p, .f, [any], T), (ret, ., [any], T))`; in run 3 the added fact `(p, ., [any-taint], T)` emits the must-premise `(p, .f, [any-taint], T)`, its start is normal, the summary `(p, .f, [any-taint], T) -> (ret, ., [any-taint], T)` is normal, and the sink edge in `root` is normal with a supported must-premise; C (`use(o) { sinkAny(o.f); }`): the must-premise `(o, .f, [any-taint], T)` and a normal sink edge in `use`, supported through the normal `[any-taint]` link; I (`x = id(dto)`, `id(p) { return p; }`): the run-1 FLOW summary `(p, ., *, *) -> (ret, ., *, *)` on the added fact `(p, ., [any-taint], {}, T)` gives `(x, ., [any-taint], {}, T)` NORMAL, and `sinkAny(x)` a normal sink edge under `{zero}` (run 1 confirms; no DEMAND entry); P: `P.$ (T) -> Q.[any-taint] (T)` normal, `P.$ (T) -> Q.[any] (T)` demand; `mk` (`mk() { d = srcAny(); d.setName(c); return d; }`, `root() { r = mk(); sink(r.name); sink(r.email); }`): the zero-premise summary `{zero} -> (ret, ., [any-taint], {name}, T)` applied on `Reach.NORMAL` gives `(r, ., [any-taint], {name}, T)` NORMAL (the REACH branch keeps `toExclusion`, §5.3; Example 4), so `sink(r.name)` has no witness and `sink(r.email)` a normal sink edge; THE PROGRAMS WITH THE EXCLUSION (`AnyTaintExCases`, A2; `validation-plan.md` → AP tests → §13 item 16, `validation-plan.md` → Interpreter tests → §7.2 item 30): S (`dto.setName(c)`, `setName(n) { this.name = n; }`): the run-1 record `(this, ., *, *) -> (this, ., */{name}, *)` on the added fact `(this, ., [any-taint], {}, T)` gives `(dto, ., [any-taint], {name}, T)` NORMAL, `sink(dto.name)` has no witness and `sink(dto.email)` a normal sink edge; SD (the setter one call deeper): the same in `root`; B (the broad demand `(D-c = (this, ., [any], T), D-p = (this, ., [any], T))`, by hand, with the run-1 records): run 3 emits the must-premise `(this, ., [any-taint], {}, T)` and its summary is `(this, ., [any-taint], {name}, T)`, normal; in the caller `sink(d.name)` has no witness and `sinkAny(e)` a normal sink edge (run 3; through `IterationDriver` B stops after run 1, which already confirms `sinkAny(e)`); X (`x.f.g = c` as ONE statement: a synthetic statement summary on the AP, the keep edges of `strongKeep(x, [f, g])` and the gen edge `c.* -> x.f.g.*`, applied with `ops.applyEdge`; not a JIR form: the JVM makes `t = x.f; t.g = c`, Part II test 15): the two results `(x, ., [any-taint], {f}, T)` and `(x, .f, [any-taint], {g}, T)`, two trees; R (`y = dto.name; z = dto.email` after S): no result on `y`, `(z, ., [any-taint], {}, T)` on `z`; CL: the three primitive AP cleaners at `x.f` (Part I §5.6; these hand-made forms are not F74 field actions); CUT (the results of X cut with `ops.limit(…, 0, …)`: the AP limit with `L = 0`, which no run has, `ApMode`): `(x, ., [any], T)` in the demand layer with no exclusion; THE CONJUNCTION (`AnyTaintND.Example`): `x = srcAny(); y = src();` and `(x, .f, $, T) ∧ (y, ., $, U) -> (z, ., $, V)` gives a normal result | `AnyTaintExCases2.G.run1_flow_above`, `run1_not_confirmed`, `HX_exact`, `handoffX_get`, `run3_must`, `run3_sink_normal`, `run3_must_supported`, `run3_confirmed`; `C.run3_must`, `run3_sink_normal`, `run3_supported`; `I.run1_flow`, `app_normal`, `run1_sink_normal`, `run1_confirmed`, `run1_no_demand`; `PassRule.source_vs_pass`, `source_normal`, `pass_demand`; `AnyTaintEx.Vec.summary_ann` (`mk`); `AnyTaintExCases.S.record_app`, `S.run1_dto_ann`, `S.run1_name_not_reported`, `S.run1_email_confirmed`, `SD.same_result`, `B.run3_must`, `B.run3_summary`, `B.run3_name_not_reported`, `B.run3_anyE_confirmed`, `X.two_results`, `X.locations_exact`, `R.reads`, `CL.atAndBelow_result`, `CL.below_result`, `CL.exact_result`, `CUT.cut_ops`, `CUT.run1_cut`; `AnyTaintND.Example.layer_new`, `confirmed` |
| 24 | `DemandSelectionTest` (DD17) | Select by layer, premise count and backward zero only. Keep every demand-layer group, normal multi-premise group and backward zero-premise group. Omit every other normal singleton, including must→must/E and must→exact records whose reversed views return DEMAND. Compare whole-group selection with per-leaf selection before restriction; reduction preserves the raw decision. Read-view guards and admission stay unchanged. | `CurrentSummarySelection.demand_iff`, `normal_forward_singleton_omitted`, `normal_backward_nonzero_omitted`, `must_view_omitted`, `group_selection_equiv`, `Certificate.workWitness`; local reader checks in `CurrentMustReverse` |

Example 1 — `ApplyEdgeVectorsTest`, the vector `a = b.f` on `(b, ., */{h}, *)` (`Cases.lean:64`, `ap.md` §4.2 table row 2):

```kotlin
package org.opentaint.dataflow.bidi.ap

class ApplyEdgeVectorsTest {
    private val m = ApManager(Cancellation())
    private val ops = ApOps(m)
    private val f = m.accessors.index(FieldAccessor("C", "f", "C"))
    private val h = m.accessors.index(FieldAccessor("C", "h", "C"))
    private val a = AccessPathBase.LocalVar(11)
    private val b = AccessPathBase.LocalVar(12)
    private val run1 = ApMode(run1 = true, Direction.FORWARD, fieldLimit = 8)

    private fun pf(base: AccessPathBase, path: List<Int>, tail: Tail, mark: MarkSlot = MarkSlot.STAR) = PathFact(base, path, tail, mark)

    /** `a = b.f` with the keep edge of the read base (Lean `Cases.loadF'`). */
    private val loadF = listOf(
        PathEdge(pf(b, emptyList(), Tail.STAR), pf(b, emptyList(), Tail.STAR), ExclusionSet.Empty),
        PathEdge(pf(b, listOf(f), Tail.STAR), pf(a, emptyList(), Tail.STAR), ExclusionSet.Empty))

    @Test
    fun `read of an abstract fact gives an any fact in the demand layer`() {
        val e1 = ExclusionSet.of(h)
        val c = Conclusion(pf(b, emptyList(), Tail.STAR), e1, demand = false)
        val expected = Conclusion(pf(a, emptyList(), Tail.ANY), ExclusionSet.Empty, demand = true)

        // the reference form (ap.md §4.1)
        val ref = loadF.map { concat(c, it) }.filterIsInstance<EdgeOutcome.Fact>().map { it.conclusion }.filter { it.fact.base == a }
        assertEquals(listOf(expected), ref)

        // the tree form (ap.md §7.3): the same fact, the same layer, the kind FLOW, no request (Cases.lean:74)
        val premise = m.initial(b, null, Tail.STAR, ExclusionSet.Empty, MarkSlot.STAR)     // the key IS the InitialAp (DD13)
        val tree = m.flowTree(b, Layer.NORMAL, e1, MarkSet.EMPTY, FlowAlgebra.LEAF_NODE)
        val out = CollectingOut()
        for (e in loadF) ops.applyEdge(tree, premise, e, statementEdge = true, run1, out)
        val onA = out.results.filter { it.base == a }
        assertEquals(listOf(Layer.DEMAND), onA.map { it.layer })
        assertTrue(onA.single() is FlowTree)                                                   // the mark `*` keeps the kind
        assertEquals(listOf(Pattern(expected.fact, ExclusionSet.Empty)), onA.flatMap { ops.leaves(it).toList() })
        assertTrue(out.markRequests.isEmpty() && out.positionRequests.isEmpty())
    }
}
```

Example 2 — `FactsEquivalenceTest`, the property that makes the tree form safe (`Tree.applyTreeE_mem` and its
extension to the mark gate and the kinds):

```kotlin
class FactsEquivalenceTest {
    private val gen = RandomFacts(seed = 42, accessors = 3, marks = 2, maxDepth = 3)   // a fresh accessor and mark are outside

    @Test
    fun `tree delta-concat equals the per-leaf reference concat`() = repeat(10_000) {
        val (facts, premise) = gen.facts()                        // a canonical Facts of a random kind and its premise (K6)
        val edge = gen.pathEdge()                                 // S7, S8 hold; every tail and mark
        val mode = gen.mode()                                     // run 1 or restricted; REACH, FLOW and TAINT
        val treeOut = CollectingOut()
        gen.ops.applyEdge(facts, premise, edge, statementEdge = true, mode, treeOut)

        val refOut = gen.asConclusions(facts).map { concat(it, edge, staticIdentity = gen.isStaticIdentity(facts, premise, it, mode), restricted = mode.restricted) }
        assertEquals(refOut.facts().toSet(), treeOut.results.flatMap { gen.asConclusions(it) }.toSet())     // fact, layer, exclusion
        assertEquals(refOut.markRequests().toSet(), treeOut.markRequests.toSet())
        assertEquals(refOut.positionRequests().toSet(), treeOut.positionRequests.map { it.toList() }.toSet())
        treeOut.results.forEach { gen.assertKind(it) }            // FLOW <=> abstract mark; REACH <=> zero base; W1, W2, W8 (a)
    }
}
```

The oracle helpers (test sources). The generator keeps one invariant per line; a generator that breaks one makes the
test compare two wrong answers, and an `isStaticIdentity` that is false too often makes the static rows vacuous.

```kotlin
/** The generator of FactsEquivalenceTest, over its own ApManager. */
class RandomFacts(seed: Long, accessors: Int, marks: Int, maxDepth: Int) {
    val ops: ApOps
    /** A canonical Facts of a random kind and its premise.
     *  K6: a FlowTree only under a `*` premise (one InitialAp); REACH or TAINT under the zero or a concrete premise; TAINT
     *      only under a PremiseSet (no zero member, every member concrete).
     *  W1: a demand FlowTree has the Empty exclusion; a `*/E` leaf only in the normal layer.
     *  W2, W6, W8: a FLOW leaf has the tail `*` (normal) or `[any]` (demand), never `$` or `[any-taint]`; an any leaf of a
     *      normal TaintTree is `[any-taint]` (the generator makes them too: a normal `[any-taint]` input of every row,
     *      with a random tree exclusion, also one that excludes the next accessor of the edge premise), of a demand
     *      TaintTree `[any]` (the Empty exclusion).
     *  The static base is drawn as often as a local, with the premise paths `[]` and `[<C>]` (the static rows). */
    fun facts(): Pair<Facts, PremiseKey>
    /** A micro edge of every tail and mark row of ap.md §4.1, also the taint edges (an `[any-taint]` target, S15) and the
     *  may `[any]` targets (a pass rule).
     *  S7: no `*∖X` premise mark; no concrete target mark under the premise mark `*`.
     *  S8: a `$` premise has a concrete mark and no `*` target; a `$` target has a concrete premise mark.
     *  W8: an `[any-taint]` target or premise has a concrete mark; a summary-like edge with an `[any-taint]` side gets a
     *      random `fromExclusion` or `toExclusion` (A2), also an edge from the zero fact on a REACH input (the leaf kind
     *      of a zero-premise summary `zero.$ -> P.[any-taint]/Et`: the REACH branch keeps `Et`, Part I §5.3); a micro
     *      edge has none.
     *  W1: a non-empty exclusion only with a `*` side. */
    fun pathEdge(): PathEdge
    /** Either run mode; all three kinds can occur (ap.md §6.1, §7.2). */
    fun mode(): ApMode
    /** The per-path view: one Conclusion per leaf and mark, with its layer (FormsReference.conclusions, Part II §23.8). */
    fun asConclusions(f: Facts): List<Conclusion>
    /** THE REFERENCE FORM OF staticIdentityDepth (Part I §5.3), for one leaf `c` of `facts`: ap.md §4.1 static exception,
     *  conditions 1 and 2, as ReferenceAlgebra.isStaticIdentity (Part II §23.8). The test passes `statementEdge = true`;
     *  the reference `concat` itself tests the case `above` (a read strictly below `q`) and condition 4 (`Ec admits r`). */
    fun isStaticIdentity(facts: Facts, premise: PremiseKey, c: Conclusion, mode: ApMode): Boolean {
        val i = (premise as? InitialAp)?.toPattern()?.fact ?: return false      // a FLOW premise is one InitialAp (K6)
        return mode.run1 && facts is FlowTree && !c.demand &&                     // run 1, a normal FLOW leaf
            i.base == AccessPathBase.ClassStatic && c.fact.base == AccessPathBase.ClassStatic &&
            i.tail == Tail.STAR && i.mark == MarkSlot.STAR && c.fact.tail == Tail.STAR && c.fact.mark is MarkSlot.Star &&
            c.fact.path == i.path && rootOrClass(i.path)                          // the leaf is at the premise path q
    }
    /** K6, W1, W2, W8 (a) on one result: FLOW <=> an abstract mark; REACH <=> the zero base; the layer rules of DD12,
     *  DD16 (W6 is a rule of the operation, so the reference `concat` checks it: an `[any]` result is demand). */
    fun assertKind(f: Facts)
}

/** The outcomes of the reference concat, in the groups of CollectingOut. */
fun List<EdgeOutcome>.facts(): List<Conclusion> = filterIsInstance<EdgeOutcome.Fact>().map { it.conclusion }
fun List<EdgeOutcome>.markRequests(): List<TaintMark> = filterIsInstance<EdgeOutcome.Request>().map { it.mark }
fun List<EdgeOutcome>.positionRequests(): List<List<AccessorIdx>> =
    filterIsInstance<EdgeOutcome.PositionRequest>().map { it.path }
```

Example 3 — `SummaryKindsTest`, run 1 with a concrete summary premise and a `*` caller fact (§5.4, §5.10; item 9):

```kotlin
@Test
fun `a FLOW added fact does not satisfy a concrete premise and climbs`() {
    val t = m.marks.mark("T")
    val j = m.initial(x, null, Tail.STAR, ExclusionSet.Empty, MarkSlot.Concrete(t))   // the chain answer (x, [], *, {}, T)
    val a = m.flowTree(x, Layer.NORMAL, ExclusionSet.Empty, MarkSet.EMPTY, FlowAlgebra.LEAF_NODE)    // the added fact x.* (*)
    assertNull(ops.satisfying(a, j, run1))     // path and tail match; only markSub(T, *) is false: no application, no request

    val policyOfCallee = m.initial(x, null, Tail.STAR, ExclusionSet.Empty, MarkSlot.STAR)          // its rule raised T on it
    val caller = CallerRef(callerKey, m.initial(y, null, Tail.STAR, ExclusionSet.Empty, MarkSlot.STAR), Layer.NORMAL, call)
    val action = ops.requestAction(policyOfCallee, RequestKind.Mark(t), ops.leaves(a).single(), caller)
    assertTrue(action is RequestAction.Climb && action.premise === caller.premise)      // the caller premise gets the request
}
```

Example 4 — `AnyTaintProgramsTest` (test 23), the rows of the tail `[any-taint]` and its exclusion on the tree form
(`ap.md` §4.1, §4.2, §6.3, §6.5; Lean `AnyTaintEx.Vec.setter_keep`, `read_admitted`, `read_excluded`,
`emit_above_excluded`, `start_must`, `summary_ann`; `AnyTaintExCases2.PassRule.source_vs_pass`, `AnyTaint.EmitVec`):

```kotlin
class AnyTaintProgramsTest {
    private val m = ApManager(Cancellation())
    private val ops = ApOps(m)
    private val f = m.accessors.index(FieldAccessor("C", "f", "C"))
    private val g = m.accessors.index(FieldAccessor("C", "g", "C"))
    private val dto = AccessPathBase.LocalVar(1); private val q = AccessPathBase.LocalVar(2); private val x = AccessPathBase.LocalVar(3)
    private val T = m.marks.mark("T")
    private val run1 = ApMode(run1 = true, Direction.FORWARD, fieldLimit = 4)
    private fun conc(b: AccessPathBase, p: List<Int>, tail: Tail) = PathFact(b, p, tail, MarkSlot.Concrete(T))
    private fun starSide(b: AccessPathBase, p: List<Int> = emptyList()) = PathFact(b, p, Tail.STAR, MarkSlot.STAR)
    /** `{zero} -> dto.[any-taint] (T)`: the result of an `[any]`-target source (the Spring DTO source), normal. */
    private val must: Facts = m.factsOf(conc(dto, emptyList(), Tail.ANY_TAINT), ExclusionSet.Empty, Layer.NORMAL)
    private fun apply(c: Facts, e: PathEdge) = CollectingOut().also { ops.applyEdge(c, m.zero, e, statementEdge = true, run1, it) }.results

    @Test // ap.md §4.2, interpreter.md §2.4: the exclusion rows (A2): no demotion at a strong write
    fun `a strong write adds the field to the exclusion, and a read through it gives nothing`() {
        val read = PathEdge(starSide(dto, listOf(f)), starSide(x), ExclusionSet.Empty)          // x = dto.f
        assertEquals(listOf(Pattern(conc(x, emptyList(), Tail.ANY_TAINT), ExclusionSet.Empty)),
            apply(must, read).flatMap { ops.leaves(it).toList() })                             // normal `[any-taint]/{}`
        val kept = apply(must, PathEdge(starSide(dto), starSide(dto), ExclusionSet.of(f))).single()   // dto.* ->_{f} dto.*
        assertEquals(Layer.NORMAL, kept.layer)                                                  // Vec.setter_keep: normal
        assertEquals(Pattern(conc(dto, emptyList(), Tail.ANY_TAINT), ExclusionSet.of(f)), ops.leaves(kept).single())   // E = {f}
        assertTrue(apply(kept, read).isEmpty())                                                 // Vec.read_excluded: f ∈ E
    }

    @Test // ap.md §2.3, S15: the same micro edge as a source (a must) and as a pass rule (a may)
    fun `a source target keeps the layer, a pass-rule target is demand`() {
        val onX = m.factsOf(conc(x, emptyList(), Tail.EXACT), ExclusionSet.Empty, Layer.NORMAL)
        val source = apply(onX, PathEdge(conc(x, emptyList(), Tail.EXACT), conc(q, emptyList(), Tail.ANY_TAINT), ExclusionSet.Empty)).single()
        val pass = apply(onX, PathEdge(conc(x, emptyList(), Tail.EXACT), conc(q, emptyList(), Tail.ANY), ExclusionSet.Empty)).single()
        assertEquals(Layer.NORMAL to Layer.DEMAND, source.layer to pass.layer)                  // W6 only for the may
        assertEquals((source as TaintTree).root, (pass as TaintTree).root)                      // the same fact (ap.md §3.1)
    }

    @Test // ap.md §6.3 THE MEET OF THE TAILS and the exclusion of the added fact, §6.5
    fun `an any-taint added fact emits a must-premise, which starts as itself`() {
        val mustPremise = m.initial(dto, m.path(listOf(f)), Tail.ANY_TAINT, ExclusionSet.Empty, MarkSlot.Concrete(T))
        val noG = m.taintTree(dto, Layer.NORMAL, (must as TaintTree).root, ExclusionSet.of(g))  // `(dto, ., [any-taint], {g}, T)`
        val d = DemandPattern(Pattern(conc(dto, listOf(f), Tail.ANY), ExclusionSet.Empty), exit = null)   // a pattern is `[any]`
        assertEquals(listOf(mustPremise), ops.emit(d, must))                                    // the tail of the ADDED fact
        assertEquals(listOf(mustPremise), ops.emit(d, noG))                                     // f ∉ E: the chain, no exclusion
        val may = m.taintTree(dto, Layer.DEMAND, (must as TaintTree).root)                      // the same fact, a demand link
        assertEquals(Tail.ANY, ops.emit(d, may).single().tail)                                  // a second premise key
        assertFailsWith<IllegalStateException> {                                                // W8 (d): no `[any-taint]` pattern
            ops.emit(DemandPattern(Pattern(conc(dto, listOf(f), Tail.ANY_TAINT), ExclusionSet.Empty), exit = null), must) }
        val atG = DemandPattern(Pattern(conc(dto, listOf(g), Tail.ANY), ExclusionSet.Empty), exit = null)
        assertTrue(ops.emit(atG, noG).isEmpty())                                                // Vec.emit_above_excluded: g ∈ E
        val start = ops.startFact(mustPremise) as TaintTree                                     // Vec.start_must: itself, normal
        assertEquals(Layer.NORMAL, start.layer)
    }

    @Test // ap.md §4.1 case `below`, the target exclusion: a zero-premise summary keeps Et on the zero fact (Vec.summary_ann)
    fun `a zero-premise summary with an any-taint leaf keeps its exclusion`() {
        val ret = AccessPathBase.Return
        val anyT = (m.factsOf(conc(ret, emptyList(), Tail.ANY_TAINT), ExclusionSet.Empty, Layer.NORMAL) as TaintTree).root
        val mk = m.taintTree(ret, Layer.NORMAL, anyT, ExclusionSet.of(g))        // mk: {zero} -> (ret, ., [any-taint], {g}, T)
        val r = CollectingOut().also { ops.applySummary(Reach.NORMAL, m.zero, mk, run1, it) }.results.single() as TaintTree
        assertEquals(Layer.NORMAL to ExclusionSet.of(g), r.layer to r.exclusion)                // not `[any-taint]/{}` (the REACH branch)
    }
}
```

Example 5 — current emission sharing, generic restriction and structural demand selection. The generic excluded-mark and exact-abstract
pattern vectors retained from F71 call the operation directly; canonical DemandStore rejects those pattern shapes.
They do not describe current hand-off output. The base `x` is `arg`, and accessor `f` is `f4`.

```kotlin
class EmissionTest {
    private val m = ApManager(Cancellation())
    private val ops = ApOps(m)
    private val x = AccessPathBase.Argument(0)
    private val T = m.marks.mark("T"); private val U = m.marks.mark("U")
    private fun fact(mk: MarkSlot) = PathFact(x, emptyList(), Tail.EXACT, mk)                           // `(x, ., $, mk)`

    @Test // F71, DD18: RVec.vEmit_starEx_T, vEmit_starEx_T_pre70, vEmit_starEx_U, vEmit_abstract; XVec.vM_emit
    fun `a star-minus-X entry pattern emits no premise for a mark of X`() {
        val dc = Pattern(fact(MarkSlot.Star(m.markSetOf(T))), ExclusionSet.Empty)                       // `(x, ., $, *∖{T})` (RVec.eD)
        val d = DemandPattern(dc, null)
        fun added(t: TaintMark) = m.factsOf(fact(MarkSlot.Concrete(t)), ExclusionSet.Empty, Layer.NORMAL)
        assertTrue(ops.emit(d, added(T)).isEmpty())                                                     // before F71: `(x, ., $, T)`
        assertNull(emit(dc, Pattern(fact(MarkSlot.Concrete(T)), ExclusionSet.Empty)))                   // the reference form
        val jU = ops.emit(d, added(U)).single().toPattern()
        assertEquals(fact(MarkSlot.Concrete(U)), jU.fact)                                               // `(x, ., $, U)`
        assertTrue(insideDemand(jU, dc))                                                                // C2: inside D-c, with its mark
        assertNull(emit(dc, Pattern(fact(MarkSlot.STAR), ExclusionSet.Empty)))   // generic excluded pattern rejects an unexcluded abstract fact
    }
    @Test
    fun `matching concrete leaves share one FLOW premise under a normalized star entry`() {
        val dc = Pattern(PathFact(x, emptyList(), Tail.ANY, MarkSlot.STAR), ExclusionSet.Empty)
        val d = DemandPattern(dc, null)
        fun added(t: TaintMark) = m.factsOf(fact(MarkSlot.Concrete(t)), ExclusionSet.Empty, Layer.NORMAL)
        val mixed = m.mergeAddDelta(added(T), added(U)).first
        val j = ops.emit(d, mixed).single()
        assertEquals(Tail.STAR to MarkSlot.STAR, j.tail to j.mark)
        assertEquals(Layer.NORMAL, ops.startFact(j).layer)
        val abstractAdded = m.flowTree(x, Layer.NORMAL, ExclusionSet.Empty, MarkSet.EMPTY, FlowAlgebra.LEAF_NODE)
        assertTrue(ops.emit(DemandPattern(dc.copy(fact = dc.fact.copy(mark = MarkSlot.Concrete(T))), null), abstractAdded).isEmpty())
        assertEquals(MarkCheck.None, ops.checkMark(abstractAdded,
            Pattern(fact(MarkSlot.Concrete(T)), ExclusionSet.Empty), ApMode(false, Direction.FORWARD, 3)))
    }

}

class RestrictionTest {
    private val m = ApManager(Cancellation())
    private val ops = ApOps(m)
    private val f4 = m.accessors.index(FieldAccessor("C", "f4", "C"))
    private val f5 = m.accessors.index(FieldAccessor("C", "f5", "C"))
    private val f7 = m.accessors.index(FieldAccessor("C", "f7", "C"))
    private val arg = AccessPathBase.Argument(0); private val ret = AccessPathBase.Return
    private val T = m.marks.mark("T")
    private fun conc(b: AccessPathBase, p: List<Int>, tail: Tail) = PathFact(b, p, tail, MarkSlot.Concrete(T))
    private fun pat(b: AccessPathBase, p: List<Int>, tail: Tail, e: ExclusionSet = ExclusionSet.Empty) =
        Pattern(PathFact(b, p, tail, MarkSlot.STAR), e)
    private val j = m.initial(arg, null, Tail.EXACT, ExclusionSet.Empty, MarkSlot.Concrete(T))     // `arg.$ (T)` (XVec.j1)
    private val dc = pat(arg, emptyList(), Tail.EXACT)                                               // XVec.dc1

    @Test // XVec.v64_demand, v64_taint; ap.md §6.4 the example (before F70 the any leaf stayed: RVec.v64_restrictU)
    fun `at a dollar exit pattern an any leaf becomes dollar in its layer`() {
        for (layer in Layer.entries) {                                      // `[any-taint]/{f4}` (normal), `[any]` (demand)
            val g = m.factsOf(conc(ret, emptyList(), Tail.ANY_TAINT), ExclusionSet.of(f4), layer)
            val r = ops.restrict(j, g, DemandPattern(dc, pat(ret, emptyList(), Tail.EXACT))).single()
            assertEquals(layer, r.layer)
            assertEquals(listOf(Pattern(conc(ret, emptyList(), Tail.EXACT), ExclusionSet.Empty)), ops.leaves(r).toList())
        }
    }

    @Test // XVec.v_taint_star, v_above_rows, v_below_rows: one exclusion per tree, so three values (an operation
          // generic operation vector; old non-star demand theorems do not prove current F77 pattern shape)
    fun `against a star exit pattern the any-taint leaves above, at and below get the exclusions of the intersection`() {
        val e = ExclusionSet.of(f4)
        val g = listOf(emptyList(), listOf(f7), listOf(f7, f7))              // above, at and below D-p = `ret.f7.*/{f5}`
            .map { m.factsOf(conc(ret, it, Tail.ANY_TAINT), e, Layer.NORMAL) }.reduce { a, b -> m.mergeAddDelta(a, b).first }
        val out = ops.restrict(j, g, DemandPattern(dc, pat(ret, listOf(f7), Tail.STAR, ExclusionSet.of(f5))))
        assertEquals(3, out.size)
        assertEquals(setOf(
            Pattern(conc(ret, listOf(f7), Tail.ANY_TAINT), ExclusionSet.of(f5)),               // the chain: `[any-taint]/E2`
            Pattern(conc(ret, listOf(f7), Tail.ANY_TAINT), e.union(ExclusionSet.of(f5))),      // the meet: `[any-taint]/(E ∪ E2)`
            Pattern(conc(ret, listOf(f7, f7), Tail.ANY_TAINT), e)),                            // below: the leaf with E
            out.flatMap { ops.leaves(it).toList() }.toSet())
    }

    @Test // RVec.vOverlap_inside, vOverlap_restrictI (before F70: vOverlap_restrictU gave g)
    fun `a premise that only overlaps D-c gives nothing`() {
        val any = m.initial(arg, null, Tail.ANY, ExclusionSet.Empty, MarkSlot.Concrete(T))      // `arg.[any] (T)` (RVec.vJo)
        val d = DemandPattern(pat(arg, listOf(f5), Tail.EXACT), pat(ret, emptyList(), Tail.ANY))  // RVec.vDo
        assertTrue(overlap(any.toPattern(), d.entry) && !insideLoc(any.toPattern(), d.entry))
        assertTrue(ops.restrict(any, m.factsOf(conc(ret, emptyList(), Tail.ANY_TAINT), ExclusionSet.Empty, Layer.NORMAL), d).isEmpty())
    }

    // F71 (DD18): the mark tests. The marks T, U of the vectors; `*∖{T}` is notT.
    private val U = m.marks.mark("U")
    private val cT = MarkSlot.Concrete(T); private val cU = MarkSlot.Concrete(U)
    private val notT = MarkSlot.Star(m.markSetOf(T))
    private fun mp(b: AccessPathBase, p: List<Int>, mk: MarkSlot) = Pattern(PathFact(b, p, Tail.EXACT, mk), ExclusionSet.Empty)
    private fun tf(p: List<Int>, mk: MarkSlot) = m.factsOf(PathFact(ret, p, Tail.EXACT, mk), ExclusionSet.Empty, Layer.NORMAL)

    @Test // THE EXAMPLE OF F71 (the user): RVec.vMark_user_inside, vMark_user_concMark, vMark_user_restrictI,
          // vMark_user_loc, vMark_user_same; XVec.vM_user
    fun `a conclusion mark that does not meet the mark of D-p gives nothing`() {
        val g = tf(listOf(f4), cT)                                                                      // `(ret, .f, $, T)` (RVec.mG)
        val sc = Conclusion(PathFact(ret, listOf(f4), Tail.EXACT, cT), ExclusionSet.Empty, demand = false)
        val dcT = mp(arg, emptyList(), cT)                                                              // D-c = `(x, ., $, T)`
        val dU = DemandPattern(dcT, mp(ret, listOf(f4), cU))                                            // D-p = `(ret, .f, $, U)` (RVec.mD)
        assertTrue(insideDemand(j.toPattern(), dcT) && !marksMeet(cU, cT))
        assertTrue(ops.restrict(j, g, dU).isEmpty())                                                    // before F71: g (the locations match)
        assertNull(restrict(j.toPattern(), sc, dU))
        val dT = DemandPattern(dcT, mp(ret, listOf(f4), cT))                                            // D-p = `(ret, .f, $, T)`: kept
        assertEquals(ops.leaves(g).toList(), ops.restrict(j, g, dT).flatMap { ops.leaves(it).toList() })
        assertEquals(sc, restrict(j.toPattern(), sc, dT))
    }

    @Test // RVec.vMark_prem_loc, vMark_prem_inside, vMark_prem_restrictI; XVec.vM_prem
    fun `a premise mark that D-c does not admit gives nothing`() {
        val dcU = mp(arg, emptyList(), cU)                                                              // D-c = `(x, ., $, U)` (RVec.mDc)
        assertTrue(insideLoc(j.toPattern(), dcU) && !insideDemand(j.toPattern(), dcU))
        assertTrue(ops.restrict(j, tf(listOf(f4), cT), DemandPattern(dcU, mp(ret, listOf(f4), cT))).isEmpty())
    }

    @Test // RVec.vMark_inStarEx_T, vMark_inStarEx_U (RVec.mDx), vMark_outStarEx_T, vMark_outStarEx_U (RVec.mDpx);
          // XVec.vM_inStarEx, vM_outStarEx
    fun `a star-minus-X pattern does not admit a mark of X`() {
        val jU = m.initial(arg, null, Tail.EXACT, ExclusionSet.Empty, cU)                               // `(x, ., $, U)` (RVec.mJU)
        val inEx = DemandPattern(mp(arg, emptyList(), notT), mp(ret, listOf(f4), MarkSlot.STAR))         // D-c = `(x, ., $, *∖{T})`
        assertTrue(ops.restrict(j, tf(listOf(f4), cT), inEx).isEmpty())
        assertEquals(1, ops.restrict(jU, tf(listOf(f4), cU), inEx).size)
        val outEx = DemandPattern(mp(arg, emptyList(), MarkSlot.STAR), mp(ret, listOf(f4), notT))        // D-p = `(ret, .f, $, *∖{T})`:
        assertTrue(ops.restrict(j, tf(listOf(f4), cT), outEx).isEmpty())                               // an operation vector, no
        assertEquals(1, ops.restrict(jU, tf(listOf(f4), cU), outEx).size)                              // spec run has such a D-p
    }

    @Test // ap.md §7.4: the mark test of the conclusion acts leaf by leaf, above, at and below D-p (F71)
    fun `only the leaves whose mark meets the mark of D-p stay`() {
        val g = listOf(tf(listOf(f4), cT), tf(listOf(f4), cU), tf(listOf(f4, f7), cT), tf(listOf(f4, f7), cU),
                       m.factsOf(PathFact(ret, emptyList(), Tail.ANY_TAINT, cU), ExclusionSet.Empty, Layer.NORMAL))   // above
            .reduce { a, b -> m.mergeAddDelta(a, b).first }
        fun marks(mk: MarkSlot) = ops.restrict(j, g, DemandPattern(dc, Pattern(PathFact(ret, listOf(f4), Tail.ANY, mk), ExclusionSet.Empty)))
            .flatMap { ops.leaves(it).toList() }.map { it.fact.mark }.toSet()
        assertEquals(setOf<MarkSlot>(cT), marks(cT))                                                    // D-p = `(ret, .f, [any], T)`
        assertEquals(setOf<MarkSlot>(cU), marks(notT))                                                  // the `U` leaves and the chain
        assertEquals(setOf<MarkSlot>(cT, cU), marks(MarkSlot.STAR))                                     // `*`: every leaf (backward run 2)
    }
}

class DemandSelectionTest {
    private val m = ApManager(Cancellation())
    private val ops = ApOps(m)
    private val f = m.accessors.index(FieldAccessor("C", "f", "C"))
    private val arg = AccessPathBase.Argument(0); private val ret = AccessPathBase.Return
    private val T = MarkSlot.Concrete(m.marks.mark("T"))
    private fun conc(b: AccessPathBase, tail: Tail) = PathFact(b, emptyList(), tail, T)

    @Test
    fun `normal singleton selection ignores tails and exclusions`() {
        val e = ExclusionSet.of(f)
        val exact = m.initial(arg, null, Tail.EXACT, ExclusionSet.Empty, T)
        val must = m.initial(arg, null, Tail.ANY_TAINT, e, T)
        for (j in listOf(exact, must)) for (tail in listOf(Tail.EXACT, Tail.ANY_TAINT)) {
            val leaf = Conclusion(conc(ret, tail), if (tail == Tail.ANY_TAINT) e else ExclusionSet.Empty, demand = false)
            val g = m.factsOf(leaf.fact, leaf.exclusion, Layer.NORMAL)
            assertNull(ops.demandPart(j, g, Direction.FORWARD))
            val view = checkNotNull(reverseRecordLeaf(Direction.FORWARD, j.toPattern(), leaf))
            assertEquals(j == must, view.result.demand)                  // read-view layer is not callee demand
            if (tail == Tail.ANY_TAINT) assertEquals(e, view.premise.exclusion)
        }
    }

    @Test
    fun `selection keeps only demand layer multi-premise and backward zero`() {
        val j = m.initial(arg, null, Tail.EXACT, ExclusionSet.Empty, T)
        val second = m.initial(AccessPathBase.Argument(1), null, Tail.EXACT, ExclusionSet.Empty, T)
        val nd = m.premiseOf(listOf(j, second))
        val normal = m.factsOf(conc(ret, Tail.EXACT), ExclusionSet.Empty, Layer.NORMAL)
        val demand = m.factsOf(conc(ret, Tail.ANY), ExclusionSet.Empty, Layer.DEMAND)
        for (direction in Direction.entries) {
            assertSame(demand, ops.demandPart(j, demand, direction))
            assertSame(normal, ops.demandPart(nd, normal, direction))
            assertNull(ops.demandPart(j, normal, direction))
        }
        assertSame(normal, ops.demandPart(m.zero, normal, Direction.BACKWARD))
        assertNull(ops.demandPart(m.zero, normal, Direction.FORWARD))
    }
}

```

Example 6 — F76 record leaf views. These tests exercise the Pattern matching overload and direct compiled edge,
including distinct native input/output marks. Engine replay and NaiveClosure use the same reader (§7.8).

```kotlin
class RecordReadViewTest {
    private val m = ApManager(Cancellation())
    private val ops = ApOps(m)
    private val mode = ApMode(false, Direction.BACKWARD, 2)
    private val p = AccessPathBase.Argument(0); private val x = AccessPathBase.Return
    private val f = m.accessors.index(FieldAccessor("C", "f", "C"))
    private val blocked = m.accessors.index(FieldAccessor("C", "blocked", "C"))
    private val ok = m.accessors.index(FieldAccessor("C", "ok", "C"))
    private val T = MarkSlot.Concrete(m.marks.mark("T")); private val U = MarkSlot.Concrete(m.marks.mark("U"))
    private val native = Pattern(PathFact(p, emptyList(), Tail.EXACT, U), ExclusionSet.Empty)
    private val mustOut = Conclusion(PathFact(x, listOf(f), Tail.ANY_TAINT, T), ExclusionSet.of(blocked), demand = false)
    private fun requirement(a: Int) = m.factsOf(PathFact(x, listOf(f, a), Tail.EXACT, T), ExclusionSet.Empty, Layer.NORMAL)

    @Test
    fun `normal exact output preserves exclusion and drops suffix`() {
        val v = checkNotNull(reverseRecordLeaf(Direction.FORWARD, native, mustOut))
        val a = requirement(ok)
        assertNull(ops.satisfying(a, v.premise, mode))                   // restricted satisfaction alone is insufficient
        val matched = checkNotNull(ops.satisfying(a, v.premise, mode, record = true))
        val out = CollectingOut().also { ops.applyRecordLeaf(matched, v, mode, it) }
        assertEquals(listOf(native), out.results.flatMap { ops.leaves(it).toList() })
        assertEquals(listOf(Layer.NORMAL), out.results.map { it.layer })
        assertTrue(out.markRequests.isEmpty() && out.positionRequests.isEmpty())
        assertNull(ops.satisfying(requirement(blocked), v.premise, mode, record = true))
        assertNull(reverseRecordLeaf(Direction.FORWARD, native.copy(exclusion = ExclusionSet.of(blocked)), mustOut))
        assertNull(reverseRecordLeaf(Direction.FORWARD, native, mustOut.copy(demand = true)))
    }

    @Test
    fun `old must premise becomes ordinary any and always demand`() {
        val old = native.copy(fact = native.fact.copy(tail = Tail.ANY_TAINT), exclusion = ExclusionSet.of(ok))
        val preciseOut = mustOut.copy(fact = mustOut.fact.copy(path = listOf(f, ok), tail = Tail.EXACT), exclusion = ExclusionSet.Empty)
        for (g in listOf(mustOut, preciseOut)) {
            val v = checkNotNull(reverseRecordLeaf(Direction.FORWARD, old, g))
            assertEquals(Conclusion(old.fact.copy(tail = Tail.ANY), ExclusionSet.Empty, demand = true), v.result)
            val matched = checkNotNull(ops.satisfying(requirement(ok), v.premise, mode, record = true))
            val out = CollectingOut().also { ops.applyRecordLeaf(matched, v, mode, it) }
            assertTrue(out.results.isNotEmpty() && out.results.all { it.layer == Layer.DEMAND })
            assertEquals(listOf(Pattern(old.fact.copy(tail = Tail.ANY), ExclusionSet.Empty)),
                out.results.flatMap { ops.leaves(it).toList() })
        }
    }
}
```

After the unit tests pass, the analysis tests of `validation-plan.md` → AP tests → §13 item 16 run through phase 3 (`analyzer-impl.md` §8.1).
The six static programs of item 9 are analysis tests too, as Java samples of the Lean programs: `Statics.CexAbove`,
`CexWide`, `CexClean`, `CopyF2F` and `DeepSink` give their vulnerability through a normal sink edge, and
`DeepSinkParam` through a demand sink edge. Phase 3 outputs the CONFIRMED and the DEMAND entries (`ap-history.md`
F68 (1)), so the test reads the layer of the sink edges of the witnesses in the `Report`, not only the output. The
programs of the tail `[any-taint]` (`validation-plan.md` → AP tests → §13 items 16, 17; F69) are analysis tests too: G and C (a Spring DTO through
a getter, and a sink in the callee) are CONFIRMED entries after run 3, I (an identity callee) and P as a source are
CONFIRMED in run 1; with the exclusion, S, SD and R confirm the sinks on the fields that the write does not touch in
run 1 and have no entry for the written field, B confirms `sinkAny(e)` in run 1, `mk` reports `sink(r.email)` and not
`sink(r.name)`; P as a pass rule and CL with the `exact` cleaner stay DEMAND entries (`analyzer-impl.md` §9.1). X and
CUT are AP-level tests (test 23: a one-statement two-level write and the limit `L = 0` exist only as synthetic AP
inputs). On the JVM a two-level write is `t = x.f; t.g = c` (the weak alias write, `interpreter.md` gap G7), so `x`
keeps `(x, ., [any-taint], {}, T)` and the JVM test of X expects `sink(x.f.g)` as the documented CONFIRMED false
positive of the alias gap (`ap.md` §11.1), beside `sink(x.f.h)` and `sink(x.k)` CONFIRMED; the JVM test of the cut uses
`L = 1` and a deeper write (the deep source of Part II test 14) and expects a DEMAND entry (Part II test 15).

---

## Part II — The IR interpreter (JVM)

Scope: `interpreter.md` behind the `Interpreter` interface of `analyzer-core.md` §4.9: the common forms and their
reversal (`org.opentaint.dataflow.bidi.interp`), and the JIR interpreter (`org.opentaint.dataflow.jvm.bidi.interp`)
(DD1). §2 gives the kinds of reuse.

Path prefixes in Part II:

* `DF/` = `core/opentaint-dataflow-core/opentaint-dataflow/src/main/kotlin/org/opentaint/dataflow/`
* `JVM/` = `core/opentaint-dataflow-core/opentaint-jvm-dataflow/src/main/kotlin/org/opentaint/dataflow/jvm/ap/ifds/`
* `BWD/` = `JVM/backward/` on `origin/saloed/backward-main`

## 20. Package map

```
opentaint-dataflow  (language-neutral)
  org.opentaint.dataflow.bidi.interp
    Interpreter.kt        Interpreter, ExitNode, UnresolvedCallObserver          (analyzer-core.md §4.9)
    MicroEdges.kt         MicroEdge, keepEdge, strongKeep, starEdge, ZERO_FACT, ZERO_KEEP, ZERO_PATTERN
    StatementSummary.kt   StatementSummary + reversed()
    Rules.kt              SinkRule, RuleStatement + reversed(), ExitRules + reversed(), CleanStep
    CallPlan.kt           CallPoint, StageKind, Guard, CallStage + reversed(), CallPlan + reversed()
    MicroEdgeBuilder.kt   MicroEdgeBuilder (ADAPT of StatementSummaryBuilder)
    Forms.kt              FormsCache, MethodForms, DirectedForms        (analyzer-core.md §4.8, §4.9 direction table)
    FormApplier.kt        FormApplier, FactAlgebra, Place: the three application modes, once (§23.3)
    FormsReference.kt     ReferenceAlgebra (the per-path FactAlgebra), FormsReference (the oracle of the forms) (§23.8)
opentaint-jvm-dataflow
  org.opentaint.dataflow.jvm.bidi.interp
    JIRInterpreter.kt         JIRInterpreter : Interpreter
    JIRStatementForms.kt      statement summaries of non-call statements (ADAPT of JIRStatementSummary)
    JIRTypeFilters.kt         Part I's TypeFilter made from JIRFactTypeChecker (interpreter.md §5.1)
    JIRRuleForms.kt           rules -> micro edges, conjunctions, sink rules, cleaners; RulePos; valid (a rule error rejects the rule); RuleErrors
    JIRCallPlanBuilder.kt     the forward call plan (interpreter.md §3, §4.5, §4.6)
    JIRBoundaryForms.kt       entry rules, exit rules, exit nodes (interpreter.md §4.3, §4.7)
    JIRExitWiredGraph.kt      the exit wiring (PORT of BWD/JIRBackwardExitWiringGraph.kt)
    JIRMethodEntry.kt         JIRMethodEntry (per method), JIRMethodForms : MethodForms (per method key), JIRMethodEntries,
                              PrescanLambdas (analyzer-core.md §4.8, §9)
```

`JIRMethodContextCache` (`analyzer-impl.md` §3.4) holds one `JIRMethodEntries` and gives, per method key, its
`JIRMethodForms` (§31.2).

## 21. Reuse map

| Today | Kind | New |
|---|---|---|
| `DF/ap/ifds/summary/StatementSummaryBuilder.kt:12-143` | ADAPT | `MicroEdgeBuilder`: `PathEdge`s; a read keeps the whole base (`interpreter.md` D1, D2); no `to == null` edge; no `keepAliasPropagationEdges`; operand and result filters apart; `buildReversed` (:30-46) becomes `StatementSummary.reversed` |
| `DF/ap/ifds/summary/StatementSummary.kt` | REPLACE | `bidi.interp.StatementSummary` (the old class stays for the prescan) |
| `DF/ap/ifds/analysis/MethodSequentFlowFunction.kt:41-78` (`transfer`) | REPLACE | `ApOps.applyEdge` in the core; `FormsReference.apply` (STATEMENT mode, §23.8) is its reference |
| `DF/graph/BackwardGraphs.kt:40-45` (`reversed`) | REUSE | the backward method graph |
| `DF/graph/MethodInstGraph.kt:25-46` (`build`) | REUSE | the compact graphs of `JIRMethodEntry` |
| `BWD/JIRBackwardExitWiringGraph.kt:10-63` | PORT | `JIRExitWiredGraph`; the wired node list is made once (today: on each `predecessors` call, :46-50) |
| `JVM/analysis/JIRStatementSummary.kt:24-117` | ADAPT | `JIRStatementForms`: the lhs filter is a result filter; a static field ref adds no filter on `S`; the read sources (`interpreter.md` §4.4) join the summary; no `buildReversed` |
| `JVM/analysis/JIRMethodSequentFlowFunction.kt` | ADAPT parts, REPLACE rest | exit sources and sinks (:136-226) -> `JIRBoundaryForms.exitRules`; the global-state drop (:186-188) -> `ExitRules.globalStateDrop`; the entry-mark drop (:300-314, `TaintMarkRemover`) -> `ExitRules.entryMarks`, `entryMarkRemoval` and `ApOps.withoutMarks` (REPLACE: every depth and both tails, D35); read sources (:228-269) -> `JIRRuleForms.readSources`; type-info facts (:45-52) not used (prescan only) |
| `JVM/analysis/JIRMethodCallFlowFunction.kt` | ADAPT the order, REPLACE the evaluation | `JIRCallPlanBuilder`: sinks (:221-233), sources (:235-251), cleaners (:146-196), constructor (:213-216), unresolved (:253-339), aliases (:350-363) become stages |
| `JVM/analysis/JIRMethodStartFlowFunction.kt` | ADAPT | `JIRBoundaryForms.entryRules`: `locationClass` (:64-84) copied; entry sinks (:86-127); entry sources (:35-48) |
| `JVM/analysis/JIRMethodCallResolver.kt:159-211` | ADAPT | `JIRCallPlanBuilder.resolve`: no tracker subscription, no lambda event; the lambdas are the prescan values |
| `JVM/JIRCallResolver.kt:90-246` | GENERALIZE | `resolve(call, location, context: JIRCallResolutionContext)`; `JIRMethodAnalysisContext` implements the new interface (§28.4) |
| `JVM/analysis/JIRMethodCallRuleBasedSummaryRewriter.kt:54-88` | ADAPT | `JIRCallPlanBuilder.rewriterSteps` |
| `JVM/analysis/JIRMethodCallSummaryHandler.kt` | REPLACE | aliases (:40-69, :98-101) -> stage `ALIASES` + `Guard.MemoryEffect`; rewriter (:71-79) -> stage `Rewrite`; exit mapping -> stage `BIND_BACK` |
| `JVM/analysis/JIRMethodSummaryEdgeProcessor.kt:15-25` | REMOVE | `interpreter.md` D14 |
| `JVM/analysis/JIRMethodAnalysisContext.kt` | SPLIT | per method -> `JIRMethodEntry`; caches (:41-76) -> `FormsCache`; `lambdaCallResolution` (:39) -> `PrescanLambdas`; `taintMarksAssignedOnMethodEnter` (:37) -> `ExitRules.entryMarks` |
| `JVM/analysis/JIRAnalysisManager.kt` | GENERALIZE | add `prescanLambdas()` over `contexts` (:76); `factTypeChecker`, `params` REUSE; ADAPT: `JIRMethodEntry.aliasAnalysis` copies the constructor call (:129-135), the old one stays (the prescan runs it) |
| `JVM/analysis/JIRAliasUtil.kt` | REUSE + GENERALIZE | REUSE `forEachAliasPathAtStatement` (:61-72), `apAccessor` (:77-81); add `aliasesPersistedThroughCall` (PORT of `BWD/../analysis/JIRAliasUtil.kt:31-35`); `forEachAliasAfterCallStatement` (:25-35) calls it |
| `JVM/JIRMethodCallFactMapper.kt` | ADAPT | `bindIn` (:210-242), `bindBack` (:141-205); `factIsRelevantToMethodCall` (:247-278) -> `CallPlan.touched` |
| `JVM/JIRFactTypeChecker.kt` | REUSE + GENERALIZE | REUSE `AccessorFilter` (:75-155) as `TypeFilter.may` (only its Field, Element and `<C>` cases run: `ap.md` W5); GENERALIZE: a new public member `localFilter(type)` builds it (today the class `AccessorFilter`, :75, is private, and only `filterFactByLocalType`, :177-183, and `accessPathFilter`, :185-188, build it); the mark policy moves to `TypeFilter.markPolicy`, one level per node of the `[e]` chain (§26.2) |
| `JVM/JIRLocalAliasAnalysis.kt` | REUSE | one per method in `JIRMethodEntry` |
| `JVM/JIRLocalVariableReachability.kt:18-22` | REUSE | only as an input of the alias analysis, as today (`JIRMethodEntry.aliasAnalysis`, §31.2). The forms have no liveness step (§32 D25) |
| `JVM/JIRLambdaTracker.kt` | REUSE | the source of the prescan values |
| `JVM/MethodFlowFunctionUtils.kt:37-73` | REUSE | `mkAccess`, `accessPathBase` |
| `JVM/JIRMarkAwareConditionRewriter.kt:18-52` | REUSE | the primary constructor (statement, alias analysis) |
| `JVM/taint/JIRBasicAtomEvaluator.kt` | REUSE | through the rewriter |
| `JVM/JIRCallPositionResolver.kt:35,51,77` | REUSE | `CallPositionToJIRValueResolver`, `CalleePositionToJIRValueResolver`, `JIRMethodPositionBaseTypeResolver` |
| `JVM/taint/TaintEvaluator.kt` | REUSE + ADAPT | REUSE `resolveBaseAp`, `resolveAp`, `toApAccessor` (:73-107); ADAPT the `<string-bytes>` rule (:43-61) |
| `JVM/taint/PrimitiveTaintExt.kt` | REUSE | `PRIMITIVE_TRACKING_ENABLED_MODE` in `isPrimitiveTracking` |
| `JVM/taint/TaintRulesProvider.kt` | REUSE | the REDUCED rule set (after `selectRules(relevantRuleIds)`) |
| `JVM/taint/JIRTaintAnalysisContext.kt` | REPLACE | direct `TaintRulesProvider` queries with the same rewriter (:107-122, :170-186); `handlePhase` (:188-194) not used |
| `JVM/taint/JIRSequentTaintUtil.kt`, `JVM/taint/JIRMethodCallTaintUtil.kt`, `DF/taint/TaintUtil.kt` | REPLACE | static forms + `ConjunctionStore` (Part I); the assumptions (`TaintUtil.kt:167-256`) go |
| `JVM/TaintConfigUtils.kt` | REPLACE | `applyPassThrough`, `applyCleaner` evaluate on a fact; the forms are static |
| `DF/taint/RuleConditionRewriter.kt:8-14`, `DF/taint/TaintMarkAwareConditionExpr.kt:48-50,110-123` | REUSE | `rewrite`, `removeTrueLiterals`, `explodeToDNF` |
| `DF/taint/PositionAccess.kt` | REUSE | `accessors()`, `base()` |
| `JVM/analysis/JIRMethodGetDefault.kt:37-51` | REUSE | the default getter rules |
| `JVM/analysis/JIRMethodEntrypointResolver.kt:11-16` | REUSE | the callee method keys |
| `DF/ap/ifds/taint/ExternalMethodTracker` | REUSE | through `UnresolvedCallObserver` (§28.5): the core calls it when an added fact reaches an unresolved callee, as today per fact (`JIRMethodCallFlowFunction.kt:285-295`) |
| `DF/util/SoftReferenceManager.kt` | NOT USED by Part II | the forms stay strongly held: their objects are keys of the run stores (§31.2). Part I holds the trie tables of the stores through it (DD5, Part I §4.5) |
| `core/src/main/kotlin/org/opentaint/jvm/sast/project/spring/SpringWebProject.kt:244-334` (`ndMethodDispatch`) | GENERALIZE (changed in place, shared with the old core) | the dispatcher saves and restores the registry fields around `__cleanup__()`; the results of the old core do not change (§31.3 item 1) |
| `core/src/main/kotlin/org/opentaint/jvm/sast/project/spring/SpringRuleProvider.kt:104-128` (`cleanerRulesForMethod`) | GENERALIZE (changed in place) | with no fact: the whole-base cleaner `RemoveAllMarks(AnyClassStatic)`; with a fact: today's code (§31.3) |
| `core/opentaint-configuration-rules/configuration-rules-jvm/src/main/kotlin/org/opentaint/dataflow/configuration/jvm/Position.kt:7-19` | GENERALIZE | the rule position `AnyClassStatic` (§31.3) |

## 22. Names from Part I and additions to `analyzer-core.md` §4.9

### 22.1 Names from Part I

Part II uses these names of Part I with the signatures of the Part I section.

| Name | Part I | Use in Part II |
|---|---|---|
| `AccessorIdx` (`Int`), `ApManager.accessors.index(a: Accessor)`, `accessors.accessor(i)` | §3.1 (DD6) | every path element; the JIR builders convert a JIR `Accessor` with `manager.accessors.index(a)` |
| `TaintMark(id)` (value class), `TaintMark.ZERO`, `ApManager.marks.mark(name)`, `marks.name(m)` | §3.1 (DD7) | the rule marks; the zero mark; `isPrimitiveTracking` |
| `AccessPathBase.Zero` | §2, §3.4 (DD8) | the zero base |
| `Tail` (with `ANY_TAINT` and `isAny`, DD16), `MarkSlot` (`MarkSlot.STAR`, `Star`, `Concrete`), `MarkSet`, `ExclusionSet` (`of(ids)`, `of(a: AccessorIdx)`), `Direction` | §3.2 | the forms; the target `[any-taint]` of a source (§27.2) |
| `PathNode`, `ApManager.path(p: List<AccessorIdx>): PathNode?` | §3.3, §5.1 | the path of a `Cleaner` |
| `ApManager`, `MethodKey` | §5.1, §0.1 (K4) | the builders; the forms |
| `TypeFilter(may: FactTypeChecker.FactApFilter, markPolicy: MarkPolicy? = null)`, `fun interface MarkPolicy { fun keeps(mark: TaintMark, elements: Int): Boolean }`, `TypeFilter.and` | §5.5 (DD9) | `StatementSummary.typeFilters`, `resultFilters`; the policy of each level of the `[e]` chain (§26.2) |
| `ConjunctiveEdge(literals: List<Pattern>, target: PathFact)` | §5.8 | the ND sources (`interpreter.md` §5.3); a pass rule makes none (`interpreter.md` §4.2, D24) |
| `PathFact(base, path: List<AccessorIdx>, tail, mark)`, `Pattern(fact, exclusion)`, `PathEdge(from, to, exclusion)`, `Conclusion`, `EdgeOutcome`, `concat` | §6 (DD2) | the micro edges; `FormsReference` |
| `revEdge(e: PathEdge): PathEdge?` | §6 | every `reversed()` (no rule kind: a forward `[any-taint]` target reverses to the premise `[any]`) |
| `Cleaner(base, path: PathNode?, reach: CleanReach, mark: TaintMark?)`, `enum class CleanReach { EXACT, BELOW, AT_AND_BELOW }` | §6 | `CleanStep.Clean`, the summary rewriter |
| `Facts` (`base`, `layer`), `Reach`, `FlowTree`, `TaintTree`, `Layer` | §7.2 of `ap.md`; Part I §3, §4 | the conclusions that the core applies the forms to (§23.3); `FormsReference.conclusions` |
| `PremiseKey` (`size`, `member(k)`, `isZero`, `nonZeroCount`), `InitialAp : PremiseKey` | §3.4 | summary identity classification (`j.isZero`), the zero premise of the exit rules (§29 step 4) |
| `MarkCheck` (`None`, `Request(mark)`, `Holds(facts, covered)`), `ApOps.checkMark(c: Facts, p: Pattern, mode)` | §5.8 | the patterns of a `SinkRule` and the literals of a `ConjunctiveEdge` (§23.3, §23.4, §29) |
| `ApOps.applyEdge(c, premise, e, statementEdge, mode, out, may)` (its internal tree step: `applyCompiledEdge`; `may` from `MicroEdge.may`, §23.1), `ApOut`, `ApMode`, `RequestKind`, `ApOps.leaves(f: Facts): Sequence<Pattern>`, `ApOps.targetTree(target, layer)`, `ApOps.without(c, part)`, `ApOps.withoutMarks(c, marks)`, `ApOps.startFact(j)`, `ConjunctionStore` (its k-ary join), `rootOrClass` | §3.2, §5, §6, §7.9, §7.10 | the application of a micro edge (§23.3, `EngineAlgebra` of `analyzer-impl.md`); `ReferenceAlgebra`, `FormsReference`; the end facts, the global-state rule and the entry marks (§29); `identityCandidate` |

### 22.2 Additions to the interface of `analyzer-core.md` §4.9

`analyzer-core.md` §4.9 gives the interpreter interface. Part II adds these members. `analyzer-impl.md` calls them.
Three rows are now in the spec (RESOLVED (F68)); they stay here as a record.

| What | Why | Code |
|---|---|---|
| `StatementSummary.resultFilters` (default empty) | `interpreter.md` §2.1 step 5 and the binding-back filters of `interpreter.md` §3.1 act on the results. One map per base cannot hold the operand filter and the result filter of one base (`x = x.f`; `o.m(this)`). RESOLVED (F68): `analyzer-core.md` §4.9 `StatementSummary` has the two maps (SI8). | §23.2 |
| `CallStage.Edges.kind: StageKind`, with the member `StageKind.statementEdges` | The core must find the sources stage (the source seeds and the source hits), the statement micro edges (the static exception of `ap.md` §4.10 item 1) and the `identityCandidate` of a fact. RESOLVED (F68): `analyzer-core.md` §4.9 has `StageKind` and `CallStage.Edges.kind`; `statementEdges` stays an addition (the spec names the two kinds, SOURCES and UNRESOLVED, in its comment). | §23.5 |
| `identityCandidate: Boolean?`; the `Guard` members `SinkTriggered(sink)` and `MemoryEffect` (`admits(identityCandidate, layer)`) | `analyzer-core.md` §4.9 defines the guards; §4.5 THE ALIAS GUARD gives the rule. Part II supplies the nullable candidate-flag update of each stage kind (`StageKind.identityCandidateOf`, below). The guard reads the current layer too: every demand result reaches aliases (`interpreter.md` §3.8 AC3, AC4). | §23.5 |
| `SinkRule.unconditional`, `conjunctive`, `seedPatterns()` | `analyzer-core.md` §4.9 defines `SinkRule(rule, alternative, patterns, endFacts)`; Part II adds these three derived members. `alternative` names the sink alternative of a witness (`ap.md` §8.10: the witnesses of two alternatives never merge). | §23.4 |
| `ExitRules.entryMarkRemoval(base): MarkSet?` | The marks that the core removes from a zero-premise item on `base` (`interpreter.md` §4.7 step 4, D35): the entry marks on `this` and `arg(i)`; null (nothing to remove) on another base or with no entry mark. The core removes every leaf with such a mark, at any depth and with both tails, with `ApOps.withoutMarks` (Part I §5.8). | §23.4 |
| `MicroEdge.isSource`, `MicroEdge.isIdentity`, `StatementSummary.edgesOf`, `StatementSummary.targets`, `CallPlan.stagesFrom`, `identityEdge(b)` | The source-seed places (`analyzer-core.md` §4.7), the alias guard (AC4), the edges of one base, the target bases (A5), the stages from one point, the identity edge of `interpreter.md` A5, §3.5 and §3.7. | §23.1, §23.2, §23.6 |
| `MicroEdge.conjunctive`, `CallStage.Edges.trigger` (F70) | `analyzer-core.md` §4.9 has `MicroEdge(edge, forward, conjunctive)`: a reversed literal of a conjunctive edge, whose every result is in the demand layer (`analyzer-core.md` §4.3 THE REVERSAL OF A CONJUNCTION; `MicroEdge.may` reads it). `trigger` is an addition: the sink alternative of a reversed `END_FACTS` stage, whose sink seeds the core fires when the stage gives a result (`analyzer-core.md` §4.5 THE TRIGGER OF AN END FACT: "the reversed `END_FACTS` stage keeps `sink`"). At a method boundary the core finds the sinks of a reversed end-fact edge by its forward form (`RuleStatement.reversed`). | §23.1, §23.2, §23.4, §23.5 |
| `MicroEdge.may` (derived: `forward.to.tail == Tail.ANY`, or `conjunctive`) | THE MAY EDGE (`interpreter.md` I14, §4.9): a micro edge whose FORWARD target is `[any]` (a pass rule with an `AnyField` target) gives every result in the demand layer, also backward, where its reversal can give a `$` result. The forward target tail of the edge tells it, so the forms carry no rule-kind flag (the backward run has no `[any-taint]`: the reversal of a source and of a pass rule differ only here). `analyzer-core.md` §4.9 `MicroEdge(edge, forward, conjunctive)` has the fields that it reads; the engine passes it to `ApOps.applyEdge(..., may)` (`analyzer-impl.md` `EngineAlgebra`). Since F70 a `conjunctive` edge (the reversed literal of a conjunctive edge) is a may too (THE REVERSAL OF A CONJUNCTION, §23.1). | §23.1, §23.8 |
| the stage `AFTER → BEFORE` of kind `PASS_OVER` in `CallPlan.reversed()` | The identity edge `b.* → b.*` of an alias base (`analyzer-core.md` §4.5, `interpreter.md` A5). RESOLVED (F68): `analyzer-core.md` §4.5 THE REVERSAL and the PASS_OVER row (step 1) of its step table now have this stage, and §4.9 the kind `PASS_OVER` (SI9). | §23.6 |
| `FormsCache`, `MethodForms`, `DirectedForms` | The forms cache of `analyzer-core.md` §4.8 and the direction table of `analyzer-core.md` §4.9. `analyzer-impl.md` §3.4 uses them. | §23.7 |
| `UnresolvedCallObserver` (`reached(call, position, plan)`); `JIRInterpreter` implements it; `CallPlan.passReads` | The external method tracker records the taint that reaches an unresolved callee, as today. `passReads`: the positions that a pass rule of the rule set reads (not a default model), for its `ruleApplied`. | §28.5, §23.6 |
| `FormApplier<P, F>` (`statement`, `stage`, `gen`), `FactAlgebra<P, F>`, `Place(node, statementEdge, sources)` | The three application modes belong to the forms; one implementation for the engine (`EngineAlgebra`, `analyzer-impl.md` §4.3) and both oracles. | §23.3 |
| `ReferenceAlgebra(mode, request, allowsSource, sourceHit, manager, conjunction)`, `FormsReference(ops, algebra)` (`applier`, `conclusions`, `apply`, `agrees`, `run(plan, inputs, at, hooks, from)`), `PlanItem(premise, c, identityCandidate)`, `PlanHooks(guards, atBound, callees, clean, exit, trigger)` | The per-path algebra on `Reference.kt`, with the static exception and the filter test, and the one per-path walk of a call plan; `NaiveClosure` (`analyzer-impl.md` §9.2) passes its hooks. | §23.8 |
| `StageKind.identityCandidateOf(me, prev)`; `FormApplier.statement(..., untouched)` | The `identityCandidate` rule of `interpreter.md` §3.8 AC3, AC4, once for the engine and the per-path walk; the unchanged path of the engine through the STATEMENT mode. | §23.3, §23.5 |

---

## 23. The common forms (`bidi.interp`)

Implements `analyzer-core.md` §4.9, `ap.md` §9.1, §9.2, `interpreter.md` §4.9.

### 23.1 Interpreter and micro edges

```kotlin
package org.opentaint.dataflow.bidi.interp

/** analyzer-core.md §4.9. The interpreter gives FORWARD forms only. It has no liveness member: the statement step keeps
 *  a fact on a dead local (`interpreter.md` §2.1 step 1, D25; Part II §32). */
interface Interpreter {
    fun entryNode(method: MethodKey): CommonInst
    fun exitNodes(method: MethodKey): List<ExitNode>
    fun entryRules(method: MethodKey): RuleStatement
    fun exitRules(method: MethodKey, exit: CommonInst): ExitRules
    fun statementSummary(method: MethodKey, statement: CommonInst): StatementSummary
    fun callPlan(caller: MethodKey, statement: CommonInst, call: CommonCallExpr): CallPlan
    fun isSummaryBase(base: AccessPathBase): Boolean
}

class ExitNode(val node: CommonInst, val exceptional: Boolean)

/** THE SOURCE-SEED PLACES (analyzer-core.md §4.7). The core applies the source-seed filter (forward restricted run) and
 *  records the source hits (backward run) ONLY on an `isSource` edge of: a statement summary, `RuleStatement.summary`
 *  (entry and exit rules), and a `StageKind.SOURCES` stage. NEVER on `RuleStatement.endFacts`, `SinkRule.endFacts` or a
 *  `StageKind.END_FACTS` stage: an end fact has the shape `zero -> P.$ (T)` or `zero -> P.[any-taint] (T)` of a source
 *  (interpreter.md I14), but it applies as usual. Backward, a reversed end-fact edge that applies to a requirement
 *  also fires the sink seeds of its sink (THE TRIGGER OF AN END FACT, §23.4).
 *  `conjunctive` (analyzer-core.md §4.9): this is the reversal of ONE literal of a conjunctive edge (§23.2); only
 *  `StatementSummary.reversed` sets it. */
class MicroEdge(val edge: PathEdge, val forward: PathEdge, val conjunctive: Boolean = false) {
    init {                                                       // interpreter.md I14: no forward form has the premise
        check(forward.from.tail != Tail.ANY_TAINT)                                 // `[any-taint]` (a pass rule with an
        check(edge == forward || (edge.from.tail != Tail.ANY_TAINT && edge.to.tail != Tail.ANY_TAINT))    // AnyField premise
    }                                                            // is a rule error, D33); a reversed form has no `[any-taint]` (A1)
    /** analyzer-core.md §4.7: a SOURCE goes from the zero fact to another base (forward form). */
    val isSource: Boolean get() = forward.from.base == AccessPathBase.Zero && forward.to.base != AccessPathBase.Zero
    /** interpreter.md §3.8 AC4: `x.p.* -> x.p.*` with no exclusion. */
    val isIdentity: Boolean get() = edge.from == edge.to && edge.exclusion == ExclusionSet.Empty
    /** interpreter.md I14, §4.9 THE MAY EDGE: the FORWARD target is `[any]` (a pass rule with an `AnyField` target, D18), so
     *  every result of the edge is in the demand layer, forward (W6: every result has the `[any]` tail) and backward (its
     *  reversal `Q.[any] (T) -> P.$ (T)` can give a `$` result; review RS-1; argued, ap.md §11.2). A source has the
     *  forward target `[any-taint]` (a must; its reversal follows the ordinary rows), so the forward target tail tells the
     *  two apart and no rule-kind flag is needed. The core passes it to `ApOps.applyEdge(..., may)` (Part I §5.3).
     *  THE REVERSAL OF A CONJUNCTION (F70; ap.md §9.2; analyzer-core.md §4.3; DD17): a `conjunctive` edge gives every
     *  result in the demand layer too, as the reversal of a may. A requirement that reaches one literal is not a converse
     *  flow of the conjunction (the other literals must hold too), so no backward summary through it is a record (R1),
     *  and the hand-off gives it to the next forward run, which analyses the callee with
     *  every member. Before F70 these results followed the ordinary rows: a NORMAL backward edge to one literal, a record
     *  by R1, and by R3 a forward record that drops the other literals (a false CONFIRMED; the program of §33.3, the third
     *  test). Argued: the model has no restricted run with ND edges (ap.md §11.2). */
    val may: Boolean get() = forward.to.tail == Tail.ANY || conjunctive
    companion object { fun of(e: PathEdge) = MicroEdge(e, e) }
}

val ZERO_FACT: PathFact = PathFact(AccessPathBase.Zero, emptyList(), Tail.EXACT, MarkSlot.Concrete(TaintMark.ZERO))   // DD8, DD7
val ZERO_PATTERN: Pattern = Pattern(ZERO_FACT, ExclusionSet.Empty)
/** interpreter.md I11 (d): the keep edge `zero.$ (zeroMark) -> zero.$ (zeroMark)`. */
val ZERO_KEEP: PathEdge = PathEdge(ZERO_FACT, ZERO_FACT, ExclusionSet.Empty)

// DD6: a path element is an AccessorIdx.
fun starSide(b: AccessPathBase, p: List<AccessorIdx> = emptyList()) = PathFact(b, p, Tail.STAR, MarkSlot.STAR)
/** interpreter.md §2.1 `keepEdge`. */
fun keepEdge(b: AccessPathBase, p: List<AccessorIdx> = emptyList(), e: ExclusionSet = ExclusionSet.Empty) =
    PathEdge(starSide(b, p), starSide(b, p), e)
/** interpreter.md §2.1 `strongKeep`: one keep edge per proper prefix, the next accessor excluded (I3). */
fun strongKeep(b: AccessPathBase, path: List<AccessorIdx>): List<PathEdge> =
    path.indices.map { k -> keepEdge(b, path.take(k), ExclusionSet.of(path[k])) }                        // ExclusionSet.of(a): Part I §3.2
/** A move, a binding or an alias edge `x.p.* -> y.q.*` (I4: `*` tail, mark `*`). */
fun starEdge(from: AccessPathBase, fp: List<AccessorIdx>, to: AccessPathBase, tp: List<AccessorIdx>) =
    PathEdge(starSide(from, fp), starSide(to, tp), ExclusionSet.Empty)
```

### 23.2 Statement summary and its reversal

`typeFilters` are the OPERAND filters (on the input, `interpreter.md` §2.1 step 3). `resultFilters` are the LHS and
binding-back filters (on the results, step 5). One map cannot hold both (§22.2): in `x = x.f`, and in `o.m(this)`, where
the callee base `this` and the caller base `this` differ.

```kotlin
class StatementSummary(
    val touched: Set<AccessPathBase>,
    val edges: List<MicroEdge>,
    val conjunctions: List<ConjunctiveEdge>,                                 // the ND sources only (interpreter.md §5.3)
    val typeFilters: Map<AccessPathBase, TypeFilter>,
    val resultFilters: Map<AccessPathBase, TypeFilter> = emptyMap(),          // analyzer-core.md §4.9 (Part II §22.2, SI8)
) {
    private val byBase: Map<AccessPathBase, List<MicroEdge>> = edges.groupBy { it.edge.from.base }
    fun edgesOf(b: AccessPathBase): List<MicroEdge> = byBase[b].orEmpty()

    /** The target bases of the forward edges: the micro edges and the conjunctive edges. */
    val targets: Set<AccessPathBase>
        get() = LinkedHashSet<AccessPathBase>().also { t ->
            edges.mapTo(t) { it.edge.to.base }; conjunctions.mapTo(t) { it.target.base }
        }

    /** ap.md §9.1, §9.2; interpreter.md §4.9 STATEMENTS; Lean `Reverse.Stmt.rev`. THE ANY TAILS (I14; A1): the backward
     *  run has no `[any-taint]`: the reversed `[any]` literal gives the requirement `[any]` (W6: demand), and a forward
     *  `[any-taint]` target is the reversed premise `[any]`. Each reversed micro edge keeps its forward form, so
     *  `MicroEdge.may` (§23.1) tells the core that the reversal of a pass rule with an `AnyField` target gives demand
     *  results only. THE REVERSAL OF A CONJUNCTION (F70; ap.md §9.2; interpreter.md §4.9 STATEMENTS, §5.3): a
     *  `ConjunctiveEdge` (two or more positive literals, Part I §5.8: a conjunctive source at a call or at an exit) gives
     *  one reversed edge per literal, an OR of the requirements, each one `conjunctive`, so `MicroEdge.may` puts every
     *  result of it in the demand layer (§23.1). */
    fun reversed(): StatementSummary {
        val rev = ArrayList<MicroEdge>()
        for (e in edges) rev += MicroEdge(revOrFail(e.edge), e.forward)
        for (c in conjunctions) for (lit in c.literals) {                                  // an OR of the requirements,
            val fwd = PathEdge(lit.fact, c.target, ExclusionSet.Empty)                    // every result demand (F70);
            rev += MicroEdge(revOrFail(fwd), fwd, conjunctive = true)                     // a literal is a Pattern (Part I §5.8)
        }
        val back = StatementSummary(touched + targets, rev, emptyList(), emptyMap())      // no filter (§5.1 last rows)
        return back.withIdentities(passOverBases(targets, touched))                     // A5
    }

    /** The identity edges of `bases`, added to this summary (touched too). */
    fun withIdentities(bases: Collection<AccessPathBase>): StatementSummary =
        if (bases.isEmpty()) this
        else StatementSummary(touched + bases, edges + bases.map(::identityEdge), conjunctions, typeFilters, resultFilters)

    companion object {
        val EMPTY = StatementSummary(emptySet(), emptyList(), emptyList(), emptyMap())
        /** A summary of identity edges only (STAGE mode: every base of an edge is touched). */
        fun identities(bases: Collection<AccessPathBase>) = EMPTY.withIdentities(bases)
    }
}

/** I11 (b): every statement micro edge is mark-reversible; a binding has `*` marks (S10). A micro edge has no
 *  `[any-taint]` premise and no exclusion on an `[any-taint]` target (I14), so R3 never gives null here. */
internal fun revOrFail(e: PathEdge): PathEdge = revEdge(e) ?: error("not mark-reversible (interpreter.md I11 (b)): $e")   // revEdge: Part I §6

/** THE IDENTITY OF A REVERSAL (interpreter.md A5, §4.9; ap.md §9.2; Lean `Reverse.idEdge`, `revNoId_breaks`). A forward
 *  target base that the forward form does not touch keeps its old value (a weak, gen-only target), so the reversed form
 *  keeps the requirement on it by `b.* -> b.*`. The one helper of the three reversals: `StatementSummary.reversed` (the
 *  alias targets of a write), `RuleStatement.reversed` (the end-fact targets), `CallPlan.reversed` (the alias bases of a
 *  call: the stage PASS_OVER). */
internal fun passOverBases(targets: Iterable<AccessPathBase>, touched: Set<AccessPathBase>): Set<AccessPathBase> =
    targets.filterTo(LinkedHashSet()) { it !in touched }

/** `b.* -> b.*`: the identity edge of A5, of the default identity of an unresolved callee (interpreter.md §3.7) and of the
 *  constructor pass-over (§3.5). */
fun identityEdge(b: AccessPathBase): MicroEdge = MicroEdge.of(keepEdge(b))
```

### 23.3 The three application modes: `FormApplier`

The core applies a `StatementSummary` in one of three modes. The mode comes from the place of the form, not from a
field.

| Mode | Forms | Rule |
|---|---|---|
| STATEMENT | `statementSummary`, `RuleStatement.summary`, `CleanStep.Kill.keepEdges`, the read and write of `CleanStep.Field` (F74) | `interpreter.md` §2.1 steps 2–5: an untouched base passes; a touched base keeps only what an edge gives |
| STAGE | `CallStage.Edges.summary` | only the edges give results; the plan relevance (`CallPlan.touched`) does the pass-over (`analyzer-core.md` §4.5). A stage summary has every base of its edges in `touched`, so `reversed()` adds no identity edge |
| GEN | `RuleStatement.endFacts`, `SinkRule.endFacts` at the method boundaries | the input stays where it is; the edges add results (`interpreter.md` §4.1 END FACTS, §4.7 step 2). At a call the end facts are the `END_FACTS` `Edges` stage (STAGE mode, on the zero fact, on a trigger; §28.1), whose reversal adds no identity. Reversed, an end-fact edge applies to every requirement, and when it gives a result the core fires the sink seeds of its sink (THE TRIGGER OF AN END FACT, §23.4; F70) |

ONE FORM APPLIER. `FormApplier` writes the three modes once, generic over the fact algebra `FactAlgebra<P, F>` (`P`:
the premise set; `F`: the facts of one input). Users, each with its own algebra:

| User | Algebra | Facts |
|---|---|---|
| the engine (`RunMethodAnalyzer`, `analyzer-impl.md` §4.3) | `EngineAlgebra` (`analyzer-impl.md` §4.3): `ApOps` on `Facts`, premise `PremiseKey` | the trees of `ap.md` §7.2 |
| the closure oracle (`NaiveClosure`, `analyzer-impl.md` §9.2) | `ReferenceAlgebra` (§23.8), one per method, with the hooks of the closure: its requests, seeds, hits and joins | per-path `Conclusion`s |
| the oracle of the forms (`FormsReference`, §23.8) | `ReferenceAlgebra` | per-path `Conclusion`s |

So the engine and both oracles share the mode logic and differ only in the algebra.

```kotlin
package org.opentaint.dataflow.bidi.interp          // FormApplier.kt

/** Where a form applies. `statementEdge`: the static exception of ap.md §4.10 item 1 (run 1, a statement micro edge:
 *  StageKind.statementEdges, a CleanStep.Kill, a statement summary). `sources`: a source-seed place (§23.1). */
data class Place(val node: CommonInst, val statementEdge: Boolean, val sources: Boolean)

/** The operations that the three modes read. P: the premise set; F: the facts of one input. */
interface FactAlgebra<P, F> {
    fun base(c: F): AccessPathBase
    fun filter(c: F, filter: TypeFilter): F?                                   // null: the filter rejects every fact
    fun applyEdge(me: MicroEdge, c: F, premise: P, statementEdge: Boolean, out: (P, F) -> Unit)
    fun conjunction(cj: ConjunctiveEdge, premise: P, c: F, node: CommonInst, out: (P, F) -> Unit)
    fun allowsSource(node: CommonInst, me: MicroEdge): Boolean                 // the source-seed filter (analyzer-core.md §4.7)
    fun sourceHit(node: CommonInst, me: MicroEdge)                             // backward: ap.md §8.11
}

/** The three application modes, once. */
class FormApplier<P, F>(private val alg: FactAlgebra<P, F>) {
    /** STATEMENT (interpreter.md §2.1 steps 2-5): an untouched base passes; a touched base gets the operand filters, the
     *  edges of its base (the kill) and the result filters. A statement summary, RuleStatement.summary, CleanStep.Kill.
     *  `untouched`: where an untouched input goes (the engine: the unchanged path with no `edges.add`, analyzer-core.md
     *  §4.3; default: with the results). A conjunction literal READS its base and does not kill it, so the conjunctions
     *  also see an untouched input (an untouched base has no operand filter); a touched input meets them in `stage`. */
    fun statement(s: StatementSummary, premise: P, c: F, at: Place, sink: (P, F) -> Unit,
                  untouched: (P, F) -> Unit = sink) {
        if (alg.base(c) !in s.touched) {
            for (cj in s.conjunctions) alg.conjunction(cj, premise, c, at.node, sink)
            untouched(premise, c); return
        }
        stage(s, premise, c, at) { pr, x, _ -> sink(pr, x) }
    }

    /** STAGE: only the edges, with the operand filters on the input and the result filters on the results.
     *  CallStage.Edges.summary; the plan relevance (CallPlan.touched) does the pass-over. Each result comes with the
     *  micro edge that made it (null: a conjunction), so the plan runner reads its identity-candidate flag (§23.5). */
    fun stage(s: StatementSummary, premise: P, c: F, at: Place, emit: (P, F, MicroEdge?) -> Unit) {
        val input = filterBy(s.typeFilters, c) ?: return                                       // operand filters
        for (me in s.edgesOf(alg.base(input)))
            micro(me, input, premise, at) { pr, x -> filterBy(s.resultFilters, x)?.let { emit(pr, it, me) } }   // result filters
        for (cj in s.conjunctions) alg.conjunction(cj, premise, input, at.node) { pr, x -> emit(pr, x, null) }
    }

    /** GEN: the edges add results; the input stays where it is; no filter; never a source-seed place (§23.1).
     *  RuleStatement.endFacts, SinkRule.endFacts. */
    fun gen(edges: List<MicroEdge>, premise: P, c: F, sink: (P, F) -> Unit) {
        for (me in edges) if (me.edge.from.base == alg.base(c)) alg.applyEdge(me, c, premise, statementEdge = false, sink)
    }

    private fun filterBy(filters: Map<AccessPathBase, TypeFilter>, c: F): F? {
        val f = filters[alg.base(c)] ?: return c
        return alg.filter(c, f)                                                // Part I §5.5: may, then the mark policy
    }

    /** One micro edge. At a source-seed place, the source-seed filter acts before it (a forward restricted run), and the
     *  source hit is recorded when the first result exists (a backward run), BEFORE that result goes to `sink` (where the
     *  engine calls edges.add; analyzer-core.md §4.7). So the hit does not depend on what edges.add gives. */
    private fun micro(me: MicroEdge, c: F, premise: P, at: Place, sink: (P, F) -> Unit) {
        val source = at.sources && me.isSource
        if (source && !alg.allowsSource(at.node, me)) return
        var hit = !source
        alg.applyEdge(me, c, premise, at.statementEdge) { pr, x ->
            if (!hit) { hit = true; alg.sourceHit(at.node, me) }                 // once, before the first result goes on
            sink(pr, x)
        }
    }
}
```

The engine applies one micro edge with the public `ApOps.applyEdge(c, premise, me.edge, statementEdge, mode, out, may =
me.may)` (Part I §5.3; it is `EngineAlgebra.applyEdge`; `may`: the reversal of a pass rule with an `AnyField` target
and the reversed literal of a conjunctive edge give demand results, §23.1). Its internal tree step is `applyCompiledEdge`, the tree form of the delta-concat
of `ap.md` §4.1. The kind of the result follows from the edge and the input (`ap.md` §7.2): the zero keep edge keeps
`Reach`; an edge from the zero fact (a source, an end fact) gives a TAINT tree; a `*`-to-`*` edge keeps the kind of its
input (FLOW or TAINT); an edge with a concrete premise mark gives TAINT from TAINT, and only the request from FLOW
(run 1). A conjunctive edge does not go through `applyEdge` forward (`FactAlgebra.conjunction`; backward its reversal
is one `conjunctive` micro edge per literal, §23.2, and goes through `applyEdge` with `may`): the engine checks each literal
with `ApOps.checkMark` (Part I), and each `MarkCheck.Holds` (with its `covered` part, for the layer of `ap.md` §4.6) is one
input of the k-ary join (`ConjunctionStore`, Part I §7.10); a full combination gives the target as a TAINT tree
(`ApOps.targetTree`). Its premise set is the union of the premise sets of the inputs without the zero fact, or `{zero}`
if every input has `{zero}` (`interpreter.md` §5.3, `ap.md` §4.6), so a premise set with two or more members is an ND
edge, and an ND edge is always TAINT. Only an ND source makes a conjunctive edge: a pass rule makes none
(`interpreter.md` §4.2, D24).

### 23.4 Sinks, rule statements, exit rules, clean steps

```kotlin
/** One alternative (one DNF cube) of one sink rule at one place (interpreter.md §4.1, §4.2; ap.md §4.9). The core checks
 *  each pattern with `ApOps.checkMark(c, pattern, mode)` (Part I): `MarkCheck.Holds` triggers the pattern,
 *  `MarkCheck.Request` raises the request (run 1: a FLOW fact that may carry the mark), `MarkCheck.None` does nothing.
 *  A plain sink triggers on `Holds`; a conjunctive sink gives each `Holds` to the k-ary join of its literals
 *  (`ConjunctionStore`, Part I §7.10), and a full combination triggers it. An unconditional sink has `ZERO_PATTERN`,
 *  which holds on the `Reach` conclusion (the zero fact). The same check serves the literals of a conjunctive edge
 *  (§23.3).
 *  `alternative`: the index of this alternative among the alternatives of `rule` at its place (the cube and the array
 *  choice, in the order of `JIRRuleForms.sinks`, Part II §27.3). The forms are the same in every run and every context
 *  (`interpreter.md` I5), so the index is stable across runs and contexts. The vulnerability store keeps the witnesses
 *  of two alternatives apart (`ap.md` §8.10; `SinkWitness.alternative`); it never reads the identity of the object. */
class SinkRule(
    val rule: CommonTaintConfigurationSink,
    val alternative: Int,
    val patterns: List<Pattern>,          // one per positive literal; [ZERO_PATTERN] for an unconditional sink
    val endFacts: List<MicroEdge>,        // GEN: zero.$ (zeroMark) -> P.$ (T), or -> P.[any-taint] (T) for an AnyField position
                                          // (interpreter.md I14), per `trackFactsReachAnalysisEnd` action
) {
    init { check(patterns.none { it.fact.tail == Tail.ANY_TAINT }) }          // I11 (f): a pattern is `$` or `[any]`
    val unconditional: Boolean get() = patterns.size == 1 && patterns[0].fact.base == AccessPathBase.Zero
    val conjunctive: Boolean get() = patterns.size >= 2                         // ap.md §4.9, §8.9
    /** ap.md §9.2 SINK SEEDS: one requirement per positive literal; none for an unconditional sink. The requirement of an
     *  `[any]` pattern (`ContainsMarkOnAnyField`) has the tail `[any]` and is in the demand layer (W6; the backward run has
     *  no `[any-taint]`: interpreter.md I11 (f), I14, §4.9). The backward run gets these seeds in two ways: the hand-off
     *  seeds the alternatives of the DEMAND entries of the report (F70 D6, Part I §7.12), and THE TRIGGER OF AN END FACT
     *  (F70; ap.md §9.2; analyzer-core.md §4.5) fires them in the run: when a reversed end-fact edge of this alternative
     *  at the statement `s` of the method key `M` applies to a requirement, the core fires these seeds at `(M, s)`, once
     *  per `(M, s, alternative)`, also when the vulnerability of the alternative is CONFIRMED. An end fact exists only
     *  after its sink triggers, so its reversal demands the trigger. An unconditional sink has no seed: its trigger needs
     *  only the zero fact, which enters every method key (F70 D5). Argued: the model has no end facts. */
    fun seedPatterns(): List<Pattern> = if (unconditional) emptyList() else patterns
}

/** analyzer-core.md §4.9. `summary` is STATEMENT mode; `endFacts` is GEN mode (the union of `sinks[*].endFacts`). */
class RuleStatement(val summary: StatementSummary, val endFacts: StatementSummary, val sinks: List<SinkRule>) {
    /** interpreter.md §4.9 RULE ROLES: the sources and the end facts reverse; the sinks stay (the place of the seeds);
     *  every filter goes (the context filter of the entry rules too). THE TRIGGER OF AN END FACT (F70; `SinkRule.
     *  seedPatterns`): each reversed end-fact edge keeps its forward form, which is an end-fact edge of one or more sinks of
     *  `sinks` (`SinkRule.endFacts`). So the core finds the sink alternatives of a reversed end-fact edge by its forward
     *  form (`analyzer-impl.md` `RuleWorklist`), and it fires their sink seeds when the edge applies to a requirement. Two
     *  alternatives with the same end-fact edge both fire: the end fact can come from either trigger. */
    fun reversed(): RuleStatement {
        val s = summary.reversed()
        // Forward, an end-fact target is a GEN target: it passes the place. So the requirement on it passes (A5).
        val revEnd = StatementSummary(emptySet(), endFacts.edges.map { MicroEdge(revOrFail(it.edge), it.forward) },
            emptyList(), emptyMap())
        return RuleStatement(s.withIdentities(passOverBases(endFacts.targets, s.touched)), revEnd, sinks)
    }
    companion object { val EMPTY = RuleStatement(StatementSummary.EMPTY, StatementSummary.EMPTY, emptyList()) }
}

/** interpreter.md §4.7, at an exit (normal or exceptional). `globalStateDrop`: step 3 applies (the exit has an exit sink):
 *  the part of a zero-premise `S` item on which a mark literal of an exit sink holds is dropped; a caller-set `S` item is
 *  not (D30, Part II §29). `entryMarks`: step 4 (D35, `entryMarkRemoval`).
 *  Steps 3 to 5 apply only at the normal exit, so at the exceptional exit `globalStateDrop` is false and `entryMarks` is
 *  empty. */
class ExitRules(val rules: RuleStatement, val globalStateDrop: Boolean, val entryMarks: Set<TaintMark>) {
    /** interpreter.md §4.9: the reversal drops G2 (steps 3 and 4). */
    fun reversed(): RuleStatement = rules.reversed()

    /** interpreter.md §4.7 step 4 (gap G2), DEVIATION D35: the marks that the core removes from a zero-premise item on
     *  `base`: the entry marks on `this` and `arg(i)`; null (nothing to remove) on another base, and when the exit has no
     *  entry mark (the exceptional exit, Part II §29). The removal reads the MARK: every leaf with such a mark goes, AT
     *  ANY DEPTH AND WITH BOTH TAILS: `(b, p, $, m)` for every path `p`, and `(b, p, [any-taint], E, m)` with its
     *  exclusion (the `AnyField` part of the Spring DTO source, Part II §29); the other leaves stay in their layer. The
     *  core applies `er.entryMarkRemoval(item.base)?.let { ops.withoutMarks(item, it) }` (Part I §5.8) to the item, a
     *  TAINT tree (a zero premise has concrete marks, ap.md §7.2). NOT today's `TaintMarkRemover`
     *  (`JVM/analysis/JIRMethodSequentFlowFunction.kt:301-314`): its filter reads only the children of the root, and
     *  `Accept` keeps a non-mark child with its whole subtree, so today only `b.$ (m)` goes, and `b.f.$ (m)` and the any
     *  child `b.[any] (m)` leak; since F69 that part is a normal `[any-taint]` fact (D32), so its leak would give
     *  CONFIRMED false positives in the callers. Normal TAINT trees never absorb a `$` leaf under an `[any-taint]` leaf
     *  (Part I §4.3), so every `$` leaf with `m` is still there to remove. */
    fun entryMarkRemoval(base: AccessPathBase): MarkSet? =
        if (entryMarkSet.isEmpty || (base != AccessPathBase.This && base !is AccessPathBase.Argument)) null else entryMarkSet
    private val entryMarkSet = MarkSet(entryMarks.map { it.id }.toIntArray().sortedDistinct())    // canonical (Part I §3.2)

    companion object { val EMPTY = ExitRules(RuleStatement.EMPTY, false, emptySet()) }
}

/** analyzer-core.md §4.9. `Kill.keepEdges` is STATEMENT mode with `touched = {S}`. F74: a field action keeps its
 *  read and strong write as ordinary statements. The temporary is fresh per cached action and does not escape. */
sealed interface CleanStep {
    fun reversed(): CleanStep
    class Clean(val cleaner: Cleaner) : CleanStep {
        override fun reversed() = this
    }
    class Kill(val keepEdges: StatementSummary) : CleanStep {
        override fun reversed() = Kill(keepEdges.reversed())
    }
    class Field(val temporary: AccessPathBase, val read: StatementSummary,
                val cleaner: Cleaner, val write: StatementSummary) : CleanStep {
        init { check(cleaner.base == temporary && cleaner.path == null) }
        override fun reversed() = Field(temporary, write.reversed(), cleaner, read.reversed())
    }
}
```

F74 adds `class CleanerTemporary : AccessPathBase` inside `AccessPathBase` in `DF/ap/ifds/Accessors.kt`.
It uses object identity, with `toString()` equal to `"<cleaner-temporary>"`. A form builder creates one per field
action; the cached reversal shares that object. It cannot equal a program local, argument, static base or zero.
It is used only inside `CleanStep.Field`. The method-edge, summary, link and source-hit stores reject this base.
The serializer, language position resolvers and trace code reject it too. The old core does not create it.

### 23.5 Call points, stages, guards

`StageKind` is in `analyzer-core.md` §4.9 (it was an addition of §22.2; RESOLVED (F68)); `statementEdges` is the one
addition. The core needs it for three things: the static
exception (the statement micro edges at a call, `ap.md` §4.10 item 1), the source seeds and the source hits of the
sources stage (`analyzer-core.md` §4.7), and the `identityCandidate` of a fact for the alias guard.

```kotlin
enum class CallPoint { BEFORE, BOUND, ADDED, RETURNED, REWRITTEN, AFTER }

enum class StageKind {
    BIND_IN, END_FACTS, SOURCES, UNRESOLVED, CONSTRUCTOR, BIND_BACK, ALIASES,
    PASS_OVER;                                   // reversed plans only: the identity of the alias bases (A5, Part II §23.6)
    /** ap.md §4.10 item 1: these are statement micro edges (with `CleanStep.Kill`). */
    val statementEdges: Boolean get() = this == SOURCES || this == UNRESOLVED
}

/** interpreter.md §3.8 AC3, AC4: update the nullable identity-candidate flag for an Edges result.
 *  Null means no producing stage has classified it yet. Sources, end facts and nonidentity pass rules are effects;
 *  default identity and constructor pass-over are candidates. Bindings, aliases, cleaners and the rewriter keep
 *  the flag. The callees stage classifies its native summary or record separately.
 *  Users: the engine plan runner (analyzer-impl.md §4.5) and FormsReference.run (§23.8). */
fun StageKind.identityCandidateOf(me: MicroEdge?, prev: Boolean?): Boolean? = when (this) {
    StageKind.SOURCES, StageKind.END_FACTS -> false
    StageKind.UNRESOLVED -> me?.isIdentity == true
    StageKind.CONSTRUCTOR -> true
    else -> prev
}

/** A forward-only selection of the inputs of a stage (analyzer-core.md §4.9). The reversal drops it. The reversed
 *  `END_FACTS` stage keeps the `sink` of its `SinkTriggered` guard as `CallStage.Edges.trigger`: that is not a
 *  selection of the inputs (the reversed stage applies to every requirement), it names the sink alternative whose seeds
 *  fire (THE TRIGGER OF AN END FACT, §23.4 `SinkRule.seedPatterns`). */
sealed interface Guard {
    /** interpreter.md §4.1 END FACTS. The sink TRIGGERS at BOUND: for a plain sink, `checkMark` gives `MarkCheck.Holds` on
     *  a bound fact of the caller edge `(i, layer)` (the layer of that sink edge); for a conjunctive sink, the k-ary join
     *  gives a new full combination (the layer of the combination; ap.md §4.9, §8.9). Then the core applies the stage to
     *  the ZERO fact (the `Reach` conclusion), with that layer: `Zero -> (s, P.$ (T))`, or `Zero -> (s, P.[any-taint]
     *  (T))` for an `AnyField` position (interpreter.md I14), a TAINT conclusion (the end fact has a concrete mark; ap.md
     *  §7.2). Each result goes to REWRITTEN with `identityCandidate = false`. */
    class SinkTriggered(val sink: SinkRule) : Guard
    /** interpreter.md §3.8 AC3, AC4. Today: `JIRMethodCallSummaryHandler.kt:59-63, 98-101`, `JIRMethodCallFlowFunction.kt:353-363`. */
    data object MemoryEffect : Guard {
        fun admits(identityCandidate: Boolean, layer: Layer): Boolean = layer == Layer.DEMAND || !identityCandidate
    }
}

sealed interface CallStage {
    val from: CallPoint
    val to: CallPoint
    fun reversed(): CallStage

    /** `kind`: analyzer-core.md §4.9 (Part II §22.2). The summary is STAGE mode. `trigger` (F70; a reversed plan only):
     *  the sink alternative of a reversed `END_FACTS` stage, the `sink` of the forward guard. When the stage gives a
     *  result on a requirement, the core fires the sink seeds of `trigger` at `BOUND` (`SinkRule.seedPatterns`, §23.4;
     *  ap.md §9.2 THE TRIGGER OF AN END FACT; analyzer-core.md §4.5). */
    data class Edges(override val from: CallPoint, override val to: CallPoint, val kind: StageKind,
                     val summary: StatementSummary, val guard: Guard? = null, val trigger: SinkRule? = null) : CallStage {
        init { check(trigger == null || (kind == StageKind.END_FACTS && guard == null)) }
        override fun reversed() = Edges(to, from, kind, summary.reversed(), guard = null,       // no guard, no filter;
            trigger = (guard as? Guard.SinkTriggered)?.sink)                                  // the sink of the trigger
    }
    /** The cleaners and rewriter use the same action chain at their own endpoints. */
    data class Actions(override val from: CallPoint, override val to: CallPoint, val steps: List<CleanStep>) : CallStage {
        override fun reversed() = Actions(to, from, steps.asReversed().map { it.reversed() })
    }
    data class Callees(override val from: CallPoint, override val to: CallPoint, val callees: List<MethodKey>) : CallStage {
        override fun reversed() = Callees(to, from, callees)                                  // backward summaries
    }
}
```

Each `Facts` group has one layer. The engine passes that layer to `MemoryEffect.admits`; the per-path walk passes
the layer of its `Conclusion`. `CurrentAliasGuard.selection_iff` checks this guard: every demand result passes,
and a normal candidate identity is skipped. `CurrentSimplifications.Analyzer.origin_quotient` and `update_commutes`
check the Boolean flag against the former categories and stage updates. `action_stage_merge` checks the shared
`Actions` chain; `merge_reverse` checks its reversal. Preserve both action endpoints, action order, statement cuts
and temporary projection. These are local equivalences; alias application and the full call walk remain open.

### 23.6 The call plan and its reversal

```kotlin
/** `passReads`: the callee positions that a pass rule of the rule set reads at an unresolved callee (not the default
 *  identity, not a default model such as the JVM default getter rules). Only the `UnresolvedCallObserver` reads it
 *  (Part II §28.5); it has no effect on facts. */
class CallPlan(val touched: Set<AccessPathBase>, val stages: List<CallStage>, val sinks: List<SinkRule>,
               val entry: CallPoint, val exit: CallPoint, val passReads: Set<AccessPathBase> = emptySet()) {
    /** analyzer-core.md §4.5: the stages from one point read the same facts; their order does not matter. */
    val stagesFrom: Map<CallPoint, List<CallStage>> = stages.groupBy { it.from }

    /** analyzer-core.md §4.5 THE REVERSAL; Lean `Reverse.Call.rev` (toCallee and fromCallee swap and reverse). */
    fun reversed(): CallPlan {
        check(entry == CallPoint.BEFORE && exit == CallPoint.AFTER) { "the core reverses only a forward plan" }
        val exitTargets = stages.filter { it.to == exit }.filterIsInstance<CallStage.Edges>().flatMap { it.summary.targets }
        val aliasBases = passOverBases(exitTargets, touched)                            // the A5 helper (Part II §23.2)
        val rev = stages.mapTo(ArrayList()) { it.reversed() }
        // Forward, an alias base is untouched: its fact passes over the call. Backward, it is touched (for the reversed
        // alias edges), so its requirement passes over by an explicit identity stage from the entry to the exit (A5).
        if (aliasBases.isNotEmpty())
            rev += CallStage.Edges(CallPoint.AFTER, CallPoint.BEFORE, StageKind.PASS_OVER, StatementSummary.identities(aliasBases))
        return CallPlan(touched + aliasBases, rev, sinks, entry = exit, exit = entry, passReads = passReads)
    }
}
```

The reversed plan of a JVM call against the backward call order (`interpreter.md` §4.9; `analyzer-core.md` §4.5 table).
`JIRCallPlanBuilder` (§28) makes the forward stages; `reversed()` makes the right column:

| Backward step | Forward stage (§28) | Reversed stage |
|---|---|---|
| 1 relevance | `touched` = `{S, o, ai, r}` | `touched + aliasBases` |
| 1 the pass-over of the alias bases (alias identity) | none: forward, an alias base is untouched and passes over the call | `AFTER→BEFORE PASS_OVER` |
| 2 reversed binding back, alias edges | `REWRITTEN→AFTER BIND_BACK`, `REWRITTEN→AFTER ALIASES (MemoryEffect)` | `AFTER→REWRITTEN BIND_BACK`, `AFTER→REWRITTEN ALIASES` (no guard) |
| 3 reversed sources, end facts | `BOUND→REWRITTEN SOURCES`, `BOUND→REWRITTEN END_FACTS (SinkTriggered)` | `REWRITTEN→BOUND SOURCES` (a reversed `[any]` literal gives an `[any]` requirement, demand; a forward `[any-taint]` target is the reversed premise `[any]`: I14, §4.9; a conjunctive source reverses into one edge per literal, every result demand: `MicroEdge.conjunctive`, F70), `REWRITTEN→BOUND END_FACTS` (no guard; `trigger` = the sink of the forward guard: when the stage gives a result, the sink seeds of that alternative fire at `BOUND`, THE TRIGGER OF AN END FACT, F70) |
| 4 reversed rewriter | `RETURNED→REWRITTEN Actions` | `REWRITTEN→RETURNED Actions` |
| 5.1 resolved callees | `ADDED→RETURNED Callees` | `RETURNED→ADDED Callees` |
| 5.2 unresolved callee | `ADDED→RETURNED UNRESOLVED` (identity; pass rules) | `RETURNED→ADDED UNRESOLVED` (both; every result of a reversed pass rule with an `AnyField` target is demand, also a `$` result: `MicroEdge.may`, `interpreter.md` §4.9; argued, `ap.md` §11.2) |
| 5.3 constructor | `ADDED→REWRITTEN CONSTRUCTOR` | `REWRITTEN→ADDED CONSTRUCTOR` |
| 6 reversed cleaners, kill | `BOUND→ADDED Actions` | `ADDED→BOUND Actions` (reverse the action order; a field action is reversed write, clean temporary, reversed read) |
| 7 seeds, read positions | `sinks` at `BOUND` | `sinks` at `BOUND` (the core seeds there: the seeds of the hand-off, and the trigger seeds of step 3) |
| 8 reversed binding in | `BEFORE→BOUND BIND_IN` | `BOUND→BEFORE BIND_IN` |
| 9 field limit | exit `AFTER` | exit `BEFORE` |

### 23.7 Forms cache and the direction view

Implements `analyzer-core.md` §4.8 (the forward forms and their reversals, made once) and the table "What the core uses in
each direction" of `analyzer-core.md` §4.9. `MethodContextCache.directed()` makes the `DirectedForms` of a run, and
`RunManager.forms` keeps it (`analyzer-impl.md` §3.2, §3.4).

```kotlin
/** A forward form and its reversal per slot (a statement index), each made once. No race: one runner uses a method
 *  (analyzer-core.md O1), and the join of a run orders the runs (analyzer-core.md B1), so a plain array is enough. A second build would
 *  break the identity keys of the run state (a `ConjunctiveEdge` or a conjunctive `SinkRule` is the key of
 *  `ConjunctionStore.add`, `Guard.SinkTriggered` names its `SinkRule`, a reversed `END_FACTS` stage names it as `trigger`
 *  and the backward core keys the trigger seeds by it: Part II §31.2 MEMORY), so this cache must never rebuild a form. */
class FormsCache<F : Any>(size: Int, private val reverse: (F) -> F) {
    private val slots = arrayOfNulls<Any>(2 * size)
    @Suppress("UNCHECKED_CAST")
    fun get(slot: Int, direction: Direction, forward: () -> F): F {
        val f = (slots[2 * slot] ?: forward().also { slots[2 * slot] = it }) as F
        if (direction == Direction.FORWARD) return f
        return (slots[2 * slot + 1] ?: reverse(f).also { slots[2 * slot + 1] = it }) as F
    }
}

/** The cached forms of one method key in both directions. The language part implements it (Part II §31.2). */
interface MethodForms {
    fun statement(direction: Direction, s: CommonInst): StatementSummary
    fun call(direction: Direction, s: CommonInst, call: CommonCallExpr): CallPlan
    fun entryRules(direction: Direction): RuleStatement                    // BACKWARD: `entryRules.reversed()`
    fun exitRules(direction: Direction, exit: CommonInst): ExitRules       // BACKWARD: `ExitRules(exitRules.reversed(), false, {})`
}

/** analyzer-core.md §4.9 table "What the core uses in each direction". */
class DirectedForms(val interp: Interpreter, val direction: Direction, private val forms: (MethodKey) -> MethodForms) {
    private val fwd get() = direction == Direction.FORWARD
    /** analyzer-core.md §4.4: forward the entry; backward every exit for the zero fact, the normal exits for another fact. */
    fun startNodes(m: MethodKey, zero: Boolean): List<CommonInst> =
        if (fwd) listOf(interp.entryNode(m)) else interp.exitNodes(m).filter { zero || !it.exceptional }.map { it.node }
    fun startRules(m: MethodKey, node: CommonInst): RuleStatement =
        if (fwd) forms(m).entryRules(Direction.FORWARD) else forms(m).exitRules(Direction.BACKWARD, node).rules
    fun statement(m: MethodKey, s: CommonInst) = forms(m).statement(direction, s)
    fun call(m: MethodKey, s: CommonInst, c: CommonCallExpr) = forms(m).call(direction, s, c)
    fun endNodes(m: MethodKey): List<CommonInst> =
        if (fwd) interp.exitNodes(m).filter { !it.exceptional }.map { it.node } else listOf(interp.entryNode(m))
    fun endRules(m: MethodKey, node: CommonInst): ExitRules =
        if (fwd) forms(m).exitRules(Direction.FORWARD, node)
        else ExitRules(forms(m).entryRules(Direction.BACKWARD), globalStateDrop = false, entryMarks = emptySet())
    // No liveness member: no run drops a fact on a dead local (interpreter.md §2.1 step 1, D25; Part II §32).
}
```

### 23.8 The per-path algebra and the oracle of the forms

`ReferenceAlgebra` is the `FactAlgebra` of the per-path reference forms (Part I `Reference.kt`, DD2): one `Conclusion`
per fact and a premise SET of patterns. It applies a micro edge with `concat` of `ap.md` §4.1 (`ApOps.applyEdge` is its
tree form), with the static exception of run 1 inside it, and it reads a type filter as `ApOps.filter` reads it
(Part I §5.5, DD9): `may` on the path, then the mark policy on a concrete mark at the root path or at `[e]^k` (the
policy of level `k`). The constructor takes the
mode of the run and the hooks of the user: `request` (run 1: a mark request, and the position request of the static
exception), `allowsSource` (the source-seed filter), `sourceHit` (the source hits), `conjunction` (the standing join of a
conjunctive edge, that is of an ND source; the premise set of its result drops the zero fact, `interpreter.md` §5.3).
`manager` decodes the accessors for the filter and makes the path of a position request. The defaults
do nothing; `NaiveClosure` (`analyzer-impl.md` §9.2) passes its own.

`FormsReference` is the oracle of the forms: `FormApplier` over a `ReferenceAlgebra`, plus `conclusions` (the per-path
view of a `Facts`), `agrees` (the core result and the reference result denote the same locations in each layer) and
`run`, THE ONE PER-PATH WALK OF A CALL PLAN. It has no mode logic of its own. `run` carries the `identityCandidate` of each item
(`StageKind.identityCandidateOf`, §23.5) and takes `PlanHooks`: the forward guards (the sink trigger of `END_FACTS`, the alias
selection by the candidate flag and current layer), the sink check at `BOUND`, the callees stage, the cleaners, the field limit at the exit point and,
backward, the sink seeds that a reversed `END_FACTS` stage fires (`trigger`, THE TRIGGER OF AN END FACT, §23.4).
With the default hooks it is the walk of the forms tests (§33.3). The closure oracle (`analyzer-impl.md` §9.2) calls it
with its own hooks and one `FormsReference` per method, so it has no walk of its own. The engine keeps its own plan
runner (`analyzer-impl.md` §4.5): it is the code under test; it shares `FormApplier` and `identityCandidateOf`.

```kotlin
package org.opentaint.dataflow.bidi.interp          // FormsReference.kt

typealias ReferenceSink = (Set<Pattern>, Conclusion) -> Unit

/** The per-path fact algebra. Users: FormsReference (below); NaiveClosure (analyzer-impl.md §9.2), one per method. */
class ReferenceAlgebra(
    private val mode: ApMode,                                                     // run1, restricted (Part I §3.2)
    request: (Set<Pattern>, RequestKind) -> Unit = { _, _ -> },                   // reqStmt, sreqStmt (run 1)
    allowsSource: (CommonInst, MicroEdge) -> Boolean = { _, _ -> true },          // the source seeds (analyzer-core.md §4.7)
    sourceHit: (CommonInst, MicroEdge) -> Unit = { _, _ -> },                     // srcHit (ap.md §8.11)
    private val manager: ApManager,                                               // the accessors of the filter; a position path
    conjunction: (ConjunctiveEdge, Set<Pattern>, Conclusion, CommonInst, ReferenceSink) -> Unit = { _, _, _, _, _ -> },  // conj: ND sources
) : FactAlgebra<Set<Pattern>, Conclusion> {
    private val onRequest = request
    private val seedAllows = allowsSource
    private val onHit = sourceHit
    private val join = conjunction

    override fun base(c: Conclusion) = c.fact.base

    override fun filter(c: Conclusion, filter: TypeFilter): Conclusion? = c.takeIf { passes(filter, it) }

    /** `me.may` (§23.1): every result in the demand layer, as `ApOps.applyEdge(..., may)` (Part I §5.3). */
    override fun applyEdge(me: MicroEdge, c: Conclusion, premise: Set<Pattern>, statementEdge: Boolean, out: ReferenceSink) {
        val identity = mode.run1 && statementEdge && isStaticIdentity(premise, c)     // ap.md §4.1, the static exception
        when (val o = concat(c, me.edge, edgeDemand = me.may, staticIdentity = identity, restricted = mode.restricted)) {   // Reference.kt
            is EdgeOutcome.Fact -> out(premise, o.conclusion)
            is EdgeOutcome.Request -> if (mode.run1) onRequest(premise, RequestKind.Mark(o.mark))                   // reqStmt
            is EdgeOutcome.PositionRequest -> if (mode.run1) onRequest(premise, RequestKind.Position(manager.path(o.path)!!))   // sreqStmt
            EdgeOutcome.None -> Unit
        }
    }

    override fun conjunction(cj: ConjunctiveEdge, premise: Set<Pattern>, c: Conclusion, node: CommonInst, out: ReferenceSink) =
        join(cj, premise, c, node, out)
    override fun allowsSource(node: CommonInst, me: MicroEdge) = seedAllows(node, me)
    override fun sourceHit(node: CommonInst, me: MicroEdge) = onHit(node, me)

    /** As ApOps.filter (Part I §5.5): `may` on the path, then the mark policy on a concrete mark at the root path or at
     *  `[e]^k` below the base (`markPolicyKeeps` of interpreter.md §5.1: level k). A leaf off the `[e]` chain stays. */
    fun passes(filter: TypeFilter, c: Conclusion): Boolean {
        var may: FactTypeChecker.FactApFilter = filter.may
        for (a in c.fact.path) when (val r = may.check(manager.accessors.accessor(a))) {
            FactTypeChecker.FilterResult.Accept -> break
            FactTypeChecker.FilterResult.Reject -> return false
            is FactTypeChecker.FilterResult.FilterNext -> may = r.filter
        }
        val mark = (c.fact.mark as? MarkSlot.Concrete)?.mark ?: return true
        val policy = filter.markPolicy ?: return true
        if (c.fact.path.any { it != ELEMENT_ACCESSOR_IDX }) return true                // off the `[e]` chain
        return policy.keeps(mark, elements = c.fact.path.size)
    }

    /** ap.md §4.1 static exception, conditions 1 and 2: the edge `premise -> c` is an identity static `*` edge, premise
     *  `{(S, q, */E0, *)}` and `c = (S, q, */Ec, *)` or `*∖X` at the same `q`, normal layer, `rootOrClass(q)`. */
    private fun isStaticIdentity(premise: Set<Pattern>, c: Conclusion): Boolean {
        val i = premise.singleOrNull()?.fact ?: return false
        val f = c.fact
        return i.base == AccessPathBase.ClassStatic && f.base == AccessPathBase.ClassStatic &&
            i.tail == Tail.STAR && f.tail == Tail.STAR && i.mark == MarkSlot.STAR && f.mark is MarkSlot.Star &&
            f.path == i.path && !c.demand && rootOrClass(i.path)                    // rootOrClass: Reference.kt
    }
}

/** One fact of a per-path plan walk: its premise set, its conclusion and its nullable identity-candidate flag (interpreter.md §3.8 AC3, AC4). */
data class PlanItem(val premise: Set<Pattern>, val c: Conclusion, val identityCandidate: Boolean?)

/** The hooks of a per-path plan walk. The defaults give the walk of the forms tests: no guard, no sink, no limit, the
 *  callees give nothing, a cleaner keeps the fact. The closure oracle (analyzer-impl.md §9.2) passes its own. */
class PlanHooks(
    /** The forward guards (analyzer-core.md §4.5): `Guard.SinkTriggered` and `Guard.MemoryEffect`. Off in a backward walk
     *  (a reversed plan has no guard) and in the forms tests. */
    val guards: Boolean = false,
    /** The rule point BOUND of a forward walk: the hook checks the sinks of the plan on the items (it records the witness
     *  or the request) and gives each sink that triggered with the layer of its sink edge (a conjunctive sink: of the new
     *  combination). The END_FACTS stage of a fired sink applies to the zero fact with that layer. */
    val atBound: (List<PlanItem>) -> List<Pair<SinkRule, Layer>> = { emptyList() },
    /** The callees stage: the closure adds a link per callee and gives nothing now (a summary resumes the walk at
     *  `RETURNED`, `run(from = RETURNED)`); a forms test gives the callee results. */
    val callees: (CallStage.Callees, PlanItem) -> List<PlanItem> = { _, _ -> emptyList() },
    /** One cleaner on one item (`cleanRes` of Reference.kt; the closure records its F75 request only in run 1, on the incoming premise). */
    val clean: (Cleaner, PlanItem) -> List<PlanItem> = { _, it -> listOf(it) },
    /** The field limit after a lowered ordinary statement. Untouched inputs pass without this cut. */
    val statementCut: (PlanItem) -> PlanItem = { it },
    /** The exit point: the field limit of the run (`limit` of Reference.kt; ap.md §4.4 rows 2, 3, 6). */
    val exit: (PlanItem) -> PlanItem = { it },
    /** BACKWARD, THE TRIGGER OF AN END FACT (F70; §23.4): a reversed `END_FACTS` stage with the `trigger` `sink` gave a
     *  result. The hook fires the sink seeds of `sink` (the closure: one zero-premise item per `seedPatterns()`, once per
     *  method key, statement and alternative) and gives them; they go on from `BOUND`, the end point of the stage. */
    val trigger: (SinkRule) -> List<PlanItem> = { emptyList() },
)

/** The oracle of the forms: FormApplier over a ReferenceAlgebra (the same mode logic as the engine, §23.3), and the one
 *  per-path walk of a call plan. Users: the forms tests (§33), and the closure oracle (analyzer-impl.md §9.2), with one
 *  FormsReference per method over the ReferenceAlgebra with its hooks. */
class FormsReference(
    private val ops: ApOps,
    algebra: ReferenceAlgebra = ReferenceAlgebra(ApMode(run1 = true, Direction.FORWARD, fieldLimit = Int.MAX_VALUE),
        manager = ops.manager),
) {
    val applier = FormApplier(algebra)
    private val zeroPremise = setOf(ZERO_PATTERN)

    /** The per-path view of a conclusion of the core: one Conclusion per leaf and mark (`ApOps.leaves`, Part I). */
    fun conclusions(f: Facts): List<Conclusion> {
        val demand = f.layer == Layer.DEMAND
        return when (f) {
            is Reach -> listOf(Conclusion(ZERO_FACT, ExclusionSet.Empty, demand))
            else -> ops.leaves(f).map { Conclusion(it.fact, it.exclusion, demand) }.toList()
        }
    }

    enum class Mode { STATEMENT, STAGE, GEN }
    /** `premise`: the premise set of the input edge as patterns (the members of its `PremiseKey`). The static exception
     *  of run 1 reads it (`ReferenceAlgebra.isStaticIdentity`, ap.md §4.1), as the core reads the premise of its edge. */
    fun apply(mode: Mode, s: StatementSummary, premise: Set<Pattern>, c: Conclusion, at: Place): List<Conclusion> = buildList {
        when (mode) {
            Mode.STATEMENT -> applier.statement(s, premise, c, at, { _, x -> add(x) })
            Mode.STAGE -> applier.stage(s, premise, c, at) { _, x, _ -> add(x) }
            Mode.GEN -> applier.gen(s.edges, premise, c) { _, x -> add(x) }
        }
    }

    /** The core applied `s` in `mode` to `input` (on an edge with the premise set `premise`) and gave `core`. Each
     *  reference result is covered by a core leaf of its layer, and each core leaf by a reference result (`covers`,
     *  ap.md §3.4): the same locations, also when the core merged leaves (T1, T2) or folded them under an any leaf (T5: an
     *  `[any]` leaf of a demand tree folds the leaves below it; an `[any-taint]/E` leaf of a normal tree folds only the
     *  `[any-taint]` leaves below it, through an accessor that E admits). `covers`
     *  reads `[any-taint]` as `[any]` with the exclusion of each leaf (K7); the layer of each side gives the name.
     *  A request that the reference raises is checked by the caller with the `request` hook of the algebra. */
    fun agrees(mode: Mode, s: StatementSummary, at: Place, premise: Set<Pattern>, input: Facts, core: List<Facts>): Boolean {
        val ref = conclusions(input).flatMap { apply(mode, s, premise, it, at) }
        val got = core.flatMap(::conclusions)
        fun Conclusion.p() = Pattern(fact, exclusion)
        return ref.all { r -> got.any { g -> g.demand == r.demand && covers(g.p(), r.p()) } } &&
            got.all { g -> ref.any { r -> r.demand == g.demand && covers(r.p(), g.p()) } }
    }

    /** THE PER-PATH WALK OF A CALL PLAN (analyzer-core.md §4.5; interpreter.md §4.5, §4.9). From `from` (the entry point:
     *  the relevance test of step 1, and the zero fact passes over AND enters), each item goes through every stage from its
     *  point; each `Edges` stage applies its summary in STAGE mode at `at` (`statementEdge` and `sources` by its kind), and
     *  the result gets `kind.identityCandidateOf`; `Actions` folds its steps; `Callees` calls the hook. An item at the
     *  exit point goes through `hooks.exit`. Gives every (point, item) that the walk reaches. */
    fun run(plan: CallPlan, inputs: List<PlanItem>, at: Place, hooks: PlanHooks = PlanHooks(),
            from: CallPoint = plan.entry): List<Pair<CallPoint, PlanItem>> {
        fun statement(s: StatementSummary, x: PlanItem): List<PlanItem> = buildList {
            applier.statement(s, x.premise, x.c, at.copy(statementEdge = true, sources = false),
                sink = { pr, y -> add(hooks.statementCut(PlanItem(pr, y, x.identityCandidate))) },
                untouched = { pr, y -> add(PlanItem(pr, y, x.identityCandidate)) })
        }
        fun cleanSteps(steps: List<CleanStep>, inputs: List<PlanItem>): List<PlanItem> =
            steps.fold(inputs) { fs, step -> fs.flatMap { x -> when (step) {
                is CleanStep.Clean -> hooks.clean(step.cleaner, x)
                is CleanStep.Kill -> buildList { applier.statement(step.keepEdges, x.premise, x.c,
                    at.copy(statementEdge = true, sources = false), { pr, y -> add(PlanItem(pr, y, x.identityCandidate)) }) }
                is CleanStep.Field -> statement(step.read, x).flatMap { hooks.clean(step.cleaner, it) }
                    .flatMap { statement(step.write, it) }.filter { it.c.fact.base != step.temporary }
            } } }
        val seen = LinkedHashSet<Pair<CallPoint, PlanItem>>()
        val work = ArrayDeque<Pair<CallPoint, List<PlanItem>>>()
        val start = ArrayList<PlanItem>()
        for (i in inputs) when {
            from != plan.entry -> start += i
            i.c.fact.base == AccessPathBase.Zero -> { seen += plan.exit to hooks.exit(i); start += i }   // passes over AND enters
            i.c.fact.base !in plan.touched -> seen += plan.exit to hooks.exit(i)                         // relevance (step 1)
            else -> start += i
        }
        if (start.isNotEmpty()) work.addLast(from to start)
        while (work.isNotEmpty()) {
            val (p, batch) = work.removeFirst()
            val items = batch.filter { seen.add(p to it) }
            if (items.isEmpty() || p == plan.exit) continue
            val fired = if (hooks.guards && p == CallPoint.BOUND) hooks.atBound(items) else emptyList()
            for (st in plan.stagesFrom[p].orEmpty()) {
                val out = ArrayList<PlanItem>()
                when (st) {
                    is CallStage.Edges -> {
                        val place = at.copy(statementEdge = st.kind.statementEdges, sources = st.kind == StageKind.SOURCES)
                        val guard = if (hooks.guards) st.guard else null
                        when {
                            guard is Guard.SinkTriggered ->                                     // END_FACTS: the zero fact, the fired layer
                                for ((sink, layer) in fired) if (sink === guard.sink) {
                                    val zero = Conclusion(ZERO_FACT, ExclusionSet.Empty, demand = layer == Layer.DEMAND)
                                    applier.stage(st.summary, zeroPremise, zero, place) { pr, x, me ->
                                        out += PlanItem(pr, x, st.kind.identityCandidateOf(me, null)) }
                                }
                            else -> {
                                for (i in items) {
                                    if (guard == Guard.MemoryEffect && !Guard.MemoryEffect.admits(checkNotNull(i.identityCandidate),
                                        if (i.c.demand) Layer.DEMAND else Layer.NORMAL)) continue   // AC3, AC4
                                    applier.stage(st.summary, i.premise, i.c, place) { pr, x, me ->
                                        out += PlanItem(pr, x, st.kind.identityCandidateOf(me, i.identityCandidate)) }
                                }
                                val sink = st.trigger                                           // backward: THE TRIGGER OF AN END FACT
                                if (sink != null && out.isNotEmpty()) out += hooks.trigger(sink)   // st.to == BOUND
                            }
                        }
                    }
                    is CallStage.Actions -> out += cleanSteps(st.steps, items)
                    is CallStage.Callees -> for (i in items) out += hooks.callees(st, i)
                }
                val next = if (st.to == plan.exit) out.map(hooks.exit) else out
                if (next.isNotEmpty()) work.addLast(st.to to next)
            }
        }
        return seen.toList()
    }
}
```

---

## 24. `MicroEdgeBuilder` (common)

ADAPT of `StatementSummaryBuilder` (`DF/ap/ifds/summary/StatementSummaryBuilder.kt:12-143`). Implements
`interpreter.md` §2.1 (write rule I3), §2.2 rows, §2.5 A1–A4.

```kotlin
class MicroEdgeBuilder {
    private val touched = LinkedHashSet<AccessPathBase>()
    private val edges = LinkedHashSet<PathEdge>()
    private val conjunctions = LinkedHashSet<ConjunctiveEdge>()
    private val operand = LinkedHashMap<AccessPathBase, TypeFilter>()
    private val result = LinkedHashMap<AccessPathBase, TypeFilter>()

    fun touch(b: AccessPathBase) { touched += b }
    /** One micro edge. A source and a pass rule with the same premise differ in the target tail (`[any-taint]` and
     *  `[any]`, interpreter.md I14), so they are two forms; `MicroEdge.may` reads the target (Part II §23.1). */
    fun edge(e: PathEdge) { edges += e }
    fun conjunction(c: ConjunctiveEdge) { conjunctions += c }
    /** `b` keeps its value: touched, with `b.* -> b.*`. Users: move, read, write (below); the entry rules (a base with a
     *  context filter) and the exit rules (a read base), Part II §29. */
    fun keep(b: AccessPathBase) { touch(b); edge(keepEdge(b)) }
    /** interpreter.md I11 (d): a form that touches the zero base keeps the zero fact. Users: the read sources (§27.2), the
     *  sources stage of a call (§28.3), the entry rules and the exit rules (§29). */
    fun keepZero() { touch(AccessPathBase.Zero); edge(ZERO_KEEP) }
    /** interpreter.md §5.1: two filters on one base are a conjunction (`TypeFilter.and`, DD9). */
    fun operandFilter(b: AccessPathBase, f: TypeFilter?) { if (f != null) operand.merge(b, f) { x, y -> x.and(y) } }
    fun resultFilter(b: AccessPathBase, f: TypeFilter?) { if (f != null) result.merge(b, f) { x, y -> x.and(y) } }

    /** `x = y`, `x = c`, `return v`, `throw t`; `from == null`: the kill (`x = new T`, ...). Today :67-72. */
    fun move(to: AccessPathBase, from: AccessPathBase?, toPath: List<AccessorIdx> = emptyList()) {
        touch(to)
        if (from == null) return
        keep(from)                                                                // y.* -> y.*
        if (from != to || toPath.isNotEmpty()) edge(starEdge(from, emptyList(), to, toPath))   // y.* -> x.*
    }

    /** `x = y.p` (I3: a read adds no exclusion). Today :74-92 kept every prefix except `p` and refined (D1, D2, D3). */
    fun read(to: AccessPathBase, base: AccessPathBase, path: List<AccessorIdx>) {
        touch(to); touch(base)
        if (base != to) keep(base)                                                  // y.* -> y.*        (D1)
        edge(starEdge(base, path, to, emptyList()))                                // y.p.* -> x.*      (D2 when base == to)
    }

    /** `b.p = v`: a strong write keeps every prefix except the written accessor (I3); a weak write keeps `b`.
     *  A2, A3: `v.* -> c.q.p.*` for each alias `(c, q)` of `b`; `c` is not touched. Today :94-121. */
    fun write(base: AccessPathBase, path: List<AccessorIdx>, weak: Boolean, values: List<AccessPathBase>,
              aliasPaths: List<Pair<AccessPathBase, List<AccessorIdx>>>) {
        touch(base)
        if (weak) keep(base) else strongKeep(base, path).forEach { edge(it) }
        for (v in values) {
            if (v != base) keep(v) else touch(v)
            edge(starEdge(v, emptyList(), base, path))
        }
        for ((c, q) in aliasPaths) {
            if (c == base) continue
            for (v in values) edge(starEdge(v, emptyList(), c, q + path))           // A6: not cut (I10)
        }
    }

    /** STATEMENT mode. */
    fun build(): StatementSummary = StatementSummary(LinkedHashSet(touched), edges.map { MicroEdge.of(it) },
        conjunctions.toList(), LinkedHashMap(operand), LinkedHashMap(result))

    /** STAGE mode (Part II §23.3): every base of an edge is touched. */
    fun buildStage(): StatementSummary {
        edges.forEach { touch(it.from.base); touch(it.to.base) }
        conjunctions.forEach { c -> c.literals.forEach { touch(it.fact.base) }; touch(c.target.base) }
        return build()
    }

    /** GEN mode: nothing is touched. */
    fun buildGen(): StatementSummary = StatementSummary(emptySet(), edges.map { MicroEdge.of(it) }, emptyList(), emptyMap())
}

/** F74: a named field action. Filters and aliases are those of the ordinary read and write at this place.
 *  The rule forms use callee coordinates; their existing call alias stage stays at §28.6. */
fun fieldCleanStep(base: AccessPathBase, path: List<AccessorIdx>, reach: CleanReach, mark: TaintMark?,
                   baseFilter: TypeFilter? = null, valueFilter: TypeFilter? = null,
                   aliasPaths: List<Pair<AccessPathBase, List<AccessorIdx>>> = emptyList()): CleanStep.Field {
    check(base != AccessPathBase.ClassStatic && path.isNotEmpty() && path.last().isFieldAccessor())
    val tmp = AccessPathBase.CleanerTemporary()
    val read = MicroEdgeBuilder().apply {
        read(tmp, base, path)
        operandFilter(base, baseFilter); resultFilter(tmp, valueFilter)
    }.build()
    val write = MicroEdgeBuilder().apply {
        write(base, path, weak = false, values = listOf(tmp), aliasPaths = aliasPaths)
        operandFilter(base, baseFilter); operandFilter(tmp, valueFilter)
    }.build()
    return CleanStep.Field(tmp, read, Cleaner(tmp, path = null, reach, mark), write)
}
```

A strong write with an empty path gives no keep edge (`strongKeep(b, [])` is empty). The JVM has no such write.

---

## 25. `JIRInterpreter`

```kotlin
package org.opentaint.dataflow.jvm.bidi.interp

class JIRInterpreter(
    val manager: ApManager,                        // Part I §5.1: the accessor and mark tables (DD6, DD7), the paths
    val rules: TaintRulesProvider,                 // the REDUCED rule set: the prescan called `selectRules(relevantRuleIds)`
    val checker: JIRFactTypeChecker,               // see below
    val callResolver: JIRCallResolver,
    val epResolver: JIRMethodEntrypointResolver,
    val entries: JIRMethodEntries,                 // Part II §31.2
    val defaultGetModel: JIRMethodGetDefault?,     // JIRAnalysisManager.Params.defaultGetModel
    val externalMethodTracker: ExternalMethodTracker?,
) : Interpreter, UnresolvedCallObserver {
    internal val errors = RuleErrors()
    internal val typeFilters = JIRTypeFilters(checker, manager)                                 // Part II §26.2
    internal val ruleForms = JIRRuleForms(rules, manager, errors, typeFilters)
    /** interpreter.md §4.7, §3.4: at the exceptional exit the rule position `Result` reads `exc`. */
    internal val ruleFormsAtThrow = JIRRuleForms(rules, manager, errors, typeFilters, resultBase = AccessPathBase.Exception)

    /** DD6: a JIR accessor as a path element. The JIR builders make only `FieldAccessor`, `ElementAccessor` and
     *  `ClassStaticAccessor` (ap.md W5; interpreter.md §1.2): from `MethodFlowFunctionUtils.mkAccess` (JVM/MethodFlowFunctionUtils.kt:37-63),
     *  `PositionAccessor.toApAccessor` (JVM/taint/TaintEvaluator.kt:103-107; `AnyField` is a tail, §27.1) and
     *  `AliasAccessor.apAccessor` (JVM/analysis/JIRAliasUtil.kt:77-81). The type-info accessors of the prescan
     *  (`TypeInfoSequentFlowFunction`, JVM/analysis/JIRMethodCallResolver.kt:145-157) and `ValueAccessor` never reach
     *  here; `AccessorTable.index` rejects them (Part I §3.1). */
    fun idx(a: Accessor): AccessorIdx = manager.accessors.index(a)

    /** The cached forms of a method key (Part II §31.2): the forward forms below and their reversals. */
    fun forms(key: MethodKey): MethodForms = entries.forms(this, key)

    override fun entryNode(method: MethodKey): CommonInst = method.statement                   // analyzer-core.md §4.4
    override fun exitNodes(method: MethodKey): List<ExitNode> = entries[method].exitNodes(method)  // Part II §30
    override fun entryRules(method: MethodKey) = forms(method).entryRules(Direction.FORWARD)
    override fun exitRules(method: MethodKey, exit: CommonInst) = forms(method).exitRules(Direction.FORWARD, exit)
    override fun statementSummary(method: MethodKey, statement: CommonInst) =
        forms(method).statement(Direction.FORWARD, statement)
    override fun callPlan(caller: MethodKey, statement: CommonInst, call: CommonCallExpr) =
        forms(caller).call(Direction.FORWARD, statement, call)

    // No liveness member (interpreter.md §2.1 step 1, D25): today's isReachable (JIRLocalVariableReachability.kt:27-31)
    // is not called. The alias analysis keeps its own reachability input (Part II §31.2).

    /** Part II §30: every base except a local or a cleaner temporary; the zero base too. */
    override fun isSummaryBase(base: AccessPathBase): Boolean =
        base !is AccessPathBase.LocalVar && base !is AccessPathBase.CleanerTemporary

    // override fun reached(...): Part II §28.5
}
```

`checker`: the checker of the prescan manager (`JIRAnalysisManager.factTypeChecker`, `JIRAnalysisManager.kt:68`) or a new
`JIRFactTypeChecker(cp)`. Both give the same filters (the checker has no state that changes the result). The reused one
keeps one set of statistics (`reportLanguageSpecificRunnerProgress`, `JIRAnalysisManager.kt:321-338`).
`JIRBidiAnalysis` uses the checker of the prescan (`analyzer-impl.md` §8.1). Part II does not depend on the choice.

---

## 26. Statement summaries of non-call statements

Implements `interpreter.md` §2.1, §2.2, §2.4, §2.5, §4.4, §5.1. ADAPT of `JIRStatementSummary`
(`JVM/analysis/JIRStatementSummary.kt:24-117`).

### 26.1 The builder

```kotlin
internal class JIRStatementForms(private val interp: JIRInterpreter, private val entry: JIRMethodEntry) {
    private fun filter(t: JIRType?) = interp.typeFilters.of(t)

    fun build(s: JIRInst): StatementSummary {
        check(s.callExpr == null) { "a call has a call plan (interpreter.md §2.1)" }
        val b = MicroEdgeBuilder()
        when (s) {
            is JIRAssignInst -> { b.assign(s, s.lhv, s.rhv); interp.ruleForms.readSources(b, s) }    // §4.4
            is JIRReturnInst -> b.move(AccessPathBase.Return, s.returnValue?.let(::accessPathBase))  // no filter
            is JIRThrowInst -> b.move(AccessPathBase.Exception, accessPathBase(s.throwable))
            else -> return StatementSummary.EMPTY      // branch, goto, monitor, catch, JMethod*Inst: {} (§2.2 last row)
        }
        return b.build()
    }

    /** Today :47-99. The lhs filter is a RESULT filter (§2.1 step 5). A static field ref adds no filter on `S`. */
    private fun MicroEdgeBuilder.assign(s: JIRInst, lhv: JIRValue, rhv: JIRExpr) {
        if (rhv is JIRBinaryExpr) { assign(s, lhv, rhv.lhv); assign(s, lhv, rhv.rhv); return }
        val from = when (rhv) {
            is JIRCastExpr -> mkAccess(rhv.operand)?.also { operandFilter(it.base, filter(rhv.type)) } ?: return
            is JIRImmediate -> mkAccess(rhv)?.also { operandFilter(it.base, filter(rhv.type)) } ?: return
            is JIRArrayAccess -> mkAccess(rhv)?.also { operandFilter(it.base, filter(rhv.array.type)) } ?: return
            is JIRFieldRef -> mkAccess(rhv)?.also { fieldFilters(it.base, rhv) } ?: return
            else -> null                                  // new, newarray, instanceof, neg, length, phi: kill
        }
        val to = when (lhv) {
            is JIRImmediate -> mkAccess(lhv)?.also { resultFilter(it.base, filter(lhv.type)) } ?: return
            is JIRArrayAccess -> mkAccess(lhv)?.also { operandFilter(it.base, filter(lhv.array.type)) } ?: return
            is JIRFieldRef -> mkAccess(lhv)?.also { fieldFilters(it.base, lhv) } ?: return
            else -> error("Assign to complex value: $lhv")
        }
        when {
            from is MemoryAccess -> {
                check(to !is MemoryAccess) { "Complex assignment: $lhv = $rhv" }       // as today, JIRStatementSummary.kt:82
                read(to.base, from.base, path(from))
            }
            to is MemoryAccess -> write(to.base, path(to), weak = (to as? RefAccess)?.accessor == ElementAccessor,
                values = listOfNotNull(from?.base), aliasPaths = aliasPaths(s, to.base))
            else -> move(to.base, from?.base)
        }
    }

    /** §5.1 "field read or write `y.f`": the static type of y AND the declaring class of f; none for a static field. */
    private fun MicroEdgeBuilder.fieldFilters(base: AccessPathBase, ref: JIRFieldRef) {
        val instance = ref.instance ?: return
        operandFilter(base, filter(instance.type)); operandFilter(base, filter(ref.field.enclosingType))
    }

    private fun path(a: MemoryAccess): List<AccessorIdx> = when (a) {                       // today :101-104; DD6
        is RefAccess -> listOf(interp.idx(a.accessor))
        is StaticRefAccess -> listOf(interp.idx(a.classStaticAccessor), interp.idx(a.accessor))
    }

    /** A1: the alias paths before the statement. REUSE JIRAliasUtil.forEachAliasPathAtStatement (:61-72); A4: only a
     *  local has aliases, so a static write has none. */
    private fun aliasPaths(s: JIRInst, base: AccessPathBase): List<Pair<AccessPathBase, List<AccessorIdx>>> {
        val result = ArrayList<Pair<AccessPathBase, List<AccessorIdx>>>()
        entry.aliasAnalysis?.forEachAliasPathAtStatement(s, base) { c, q -> result += c to q.map { interp.idx(it) } }
        return result
    }
}
```

The result for the rows of `interpreter.md` §2.2 (each is a test of §33):

| Statement | Builder calls | Edges |
|---|---|---|
| `x = y` | `move(x, y)` | `y.* → y.*`, `y.* → x.*` |
| `x = y.f` | `read(x, y, [f])` | `y.* → y.*`, `y.f.* → x.*` |
| `x = x.f` | `read(x, x, [f])` | `x.f.* → x.*` |
| `x = C.s` | `read(x, S, [<C>, s])` | `S.* → S.*`, `S.<C>.s.* → x.*` |
| `y.f = x` | `write(y, [f], weak = false, [x], aliases)` | `y.* →_{f} y.*`, `x.* → x.*`, `x.* → y.f.*` |
| `y[i] = x` | `write(y, [e], weak = true, [x], aliases)` | `y.* → y.*`, `x.* → x.*`, `x.* → y.[e].*` |
| `C.s = x` | `write(S, [<C>, s], weak = false, [x], [])` | `S.* →_{<C>} S.*`, `S.<C>.* →_{s} S.<C>.*`, `x.* → x.*`, `x.* → S.<C>.s.*` |
| `x = a op b` | `move(x, a)`, `move(x, b)` | `a.* → a.*`, `a.* → x.*`, `b.* → b.*`, `b.* → x.*` |

The static exception of run 1 (`interpreter.md` §2.1 step 4) is an AP rule:
`ApOps.applyEdge(..., statementEdge = true, ...)`. The interpreter gives no extra form for it.

### 26.2 Type filters (`interpreter.md` §5.1)

Part I's `TypeFilter(may, markPolicy)` (DD9). `may` is today's `AccessorFilter` of `JIRFactTypeChecker`
(`JVM/JIRFactTypeChecker.kt:75-155`). A path of the new analysis has only `FieldAccessor`, `ElementAccessor` and
`ClassStaticAccessor` (`ap.md` W5; `interpreter.md` §1.2). On them the cases of `checkAccessor` (:84-136) are the
`may(t, p)` of `interpreter.md` §5.1: Field: the declaring class, deeper fields not checked; Element: the array type,
then `FilterNext(element type)`, `Object`: accept; `<C>`: accept. The other cases of `checkAccessor` (`ValueAccessor`,
`TypeInfoAccessor`, `TypeInfoGroupAccessor`, `TaintMarkAccessor`, `AnyAccessor`, `FinalAccessor`) never run: no path has
such an accessor (`AccessorTable` rejects them, Part I §3.1), and a tail is not an accessor. They stay for the prescan,
which runs the old core. The mark policy of today's `TaintMarkAccessor` case (:96-104) moves to `TypeFilter.markPolicy`:
today the filter of the element type (`FilterNext` at `[e]`, :114-123) also reads the marks below `[e]`, so the policy
has one level per node of the `[e]` chain (Part I §5.5). GENERALIZE: one public member gives the private filter.

```kotlin
// JVM/JIRFactTypeChecker.kt — new member; `filterFactByLocalType` (:177-183) stays for the prescan.
fun localFilter(type: JIRType): FactTypeChecker.FactApFilter = AccessorFilter(type, isLocalCheck = true)
// F74: expose the existing private helpers (:224-229); their bodies stay the same.
fun fieldAccessorType(accessor: FieldAccessor): JIRType? = cp.findTypeOrNull(accessor.fieldType)
fun fieldClassType(accessor: FieldAccessor): JIRClassType? = cp.findTypeOrNull(accessor.className) as? JIRClassType
```

```kotlin
/** interpreter.md §5.1: `may` from the static type, then the mark policy (G6) on each level of the `[e]` chain whose
 *  type is primitive or boxed. One filter per type (the forms of every method share it). */
class JIRTypeFilters(private val checker: JIRFactTypeChecker, private val manager: ApManager) {
    private val byType = ConcurrentHashMap<JIRType, TypeFilter>()

    fun of(t: JIRType?): TypeFilter? = t?.let { byType.computeIfAbsent(it, ::make) }

    /** F74: ordinary root operand filter and the field-value filter for the temporary. The call's
     *  JIRMethodPositionBaseTypeResolver accepts only Simple positions, so decode the path here. */
    fun forField(baseType: JIRType?, path: List<AccessorIdx>): Pair<TypeFilter?, TypeFilter?> {
        val accessors = path.map(manager.accessors::accessor)
        val root = of(baseType)
        val declaring = (accessors.firstOrNull() as? FieldAccessor)?.let { of(checker.fieldClassType(it)) }
        val base = when { root == null -> declaring; declaring == null -> root; else -> root.and(declaring) }
        return base to of(valueType(baseType, path))
    }

    /** Resolve a decoded position path, including a static field with an unknown root type. */
    fun valueType(baseType: JIRType?, path: List<AccessorIdx>): JIRType? {
        var type = baseType
        for (i in path) type = when (val a = manager.accessors.accessor(i)) {
            is FieldAccessor -> checker.fieldAccessorType(a)
            ElementAccessor -> type?.ifArrayGetElementType
            else -> null
        }
        return type
    }

    /** Level 0 is `t`; level k + 1 is the element type of level k (`ifArrayGetElementType`, the type that `FilterNext` of
     *  `[e]` carries today, JIRFactTypeChecker.kt:114-123). A level with no known type keeps every mark (today `[e]`
     *  gives `Accept` there, so no mark check runs below it). No primitive or boxed level: no policy. */
    private fun make(t: JIRType): TypeFilter {
        val primitive = generateSequence(t) { it.ifArrayGetElementType }.map { it.unboxIfNeeded() is JIRPrimitiveType }.toList()
        return TypeFilter(
            may = checker.localFilter(t),
            markPolicy = if (true !in primitive) null
                         else MarkPolicy { m, k -> !primitive.getOrElse(k) { false } || isPrimitiveTracking(m) })
    }

    /** interpreter.md §5.1 `isPrimitiveTracking` (DD7). Today the TaintMarkAccessor case (:96-104). */
    fun isPrimitiveTracking(m: TaintMark): Boolean =
        manager.marks.name(m).endsWith(PrimitiveTaintExt.PRIMITIVE_TRACKING_ENABLED_MODE)
}
```

`ApOps.filter` applies `may` to the path and then the policy to the concrete marks at the root path and at each node of
the `[e]` chain, with the level of that node (Part I §5.5). The filter never reads the tail: a `*`, an `[any]` and an
`[any-taint]` fact keep their tail, and an `[any-taint]` fact its exclusion (`interpreter.md` D11, D12, §5.1; the
locations below a primitive value are not valid, I13). Two filters on one base are `TypeFilter.and`
(`MicroEdgeBuilder.operandFilter`, §24).

`forField` resolves the root as a Simple position and then walks the decoded accessors for the temporary's value
type. A nested field's declaring class is not a type of the root: do not combine all declaring-class filters on
that root. The ordinary `may` filter checks the root and its first field, as above. The temporary's result filter
and write operand filter use the final value type, including its primitive mark policy.

---

## 27. Rules to forms

Implements `interpreter.md` §1.3, §1.4, §4.1, §4.2, §5.2, §5.3. The rule queries pass `fact = null`: every rule of the
method in the REDUCED set (`TaintRulesProvider`, the prescan selected it). So no rule of the new analysis needs the
fact: a provider gives the same rules with no fact (the Spring provider: §31.3). The conditions use the rewriter of today:
`JIRMarkAwareConditionRewriter(positionResolver, checker, aliasAnalysis, statement)`
(`JVM/JIRMarkAwareConditionRewriter.kt:18-25`). It evaluates the non-mark atoms per statement (`JIRBasicAtomEvaluator`)
and gives the mark literals.

### 27.1 Positions, marks, literals, cubes

```kotlin
import org.opentaint.dataflow.configuration.jvm.TaintMark as RuleMark      // the rule mark; `TaintMark` is Part I's

internal class JIRRuleForms(
    val rules: TaintRulesProvider,
    private val manager: ApManager,
    val errors: RuleErrors,
    private val typeFilters: JIRTypeFilters,
    private val resultBase: AccessPathBase = AccessPathBase.Return,   // `exc` at the exceptional exit (interpreter.md §4.7)
) {

    /** interpreter.md §1.3: a rule position in callee coordinates. `any`: the position ends with AnyField. DD6: the path
     *  elements are AccessorIdx. `fact`: a literal, a sink pattern, a pass-rule TARGET (a pass-rule premise has no
     *  AnyField: D33): `[any]` for an AnyField position (a may, a pattern). `sourceFact`: the TARGET OF A SOURCE AND OF
     *  AN END FACT (`markTarget`, §27.2): `[any-taint]` (a must, I14, ap.md S15; D32; SI19) for an AnyField position
     *  (`AssignMarkOnAnyAccessor`, `AssignMark` on `PositionWithAccess(P, AnyField)`), with the Empty exclusion (I14).
     *  `star`: a `CopyAllMarks` side (`*`, or `[any]` for an AnyField target). */
    data class RulePos(val base: AccessPathBase, val path: List<AccessorIdx>, val any: Boolean) {
        val isClass: Boolean get() = base == AccessPathBase.ClassStatic && path.size == 1      // `S.<C>` (§1.4)
        fun fact(t: TaintMark) = PathFact(base, path, if (any) Tail.ANY else Tail.EXACT, MarkSlot.Concrete(t))
        fun sourceFact(t: TaintMark) = PathFact(base, path, if (any) Tail.ANY_TAINT else Tail.EXACT, MarkSlot.Concrete(t))
        fun star() = PathFact(base, path, if (any) Tail.ANY else Tail.STAR, MarkSlot.STAR)
    }

    /** §1.3: the position of a rule element, or null after a rule error. It NEVER throws.
     *  THE TWO INVARIANTS OF A RULE POSITION: (1) at most one `AnyField` accessor; (2) an `AnyField` is the LAST accessor
     *  (no concrete accessor after it). A position that breaks one (`[arg0, ".*", ".f"]`, `.*.*`) is a rule error: the
     *  WHOLE RULE is rejected and logged once (`RuleErrors`, `valid` below), as every other rule error (§1.3, §1.4, D29).
     *  `AnyClassStatic` (Part II §31.3) is the whole static base `S` with the empty path. It is allowed only as the whole
     *  position of a `RemoveAllMarks` action (`kill = true`); in every other rule element (a mark literal of a condition
     *  too: `valid` reads the `Position` of each literal with this overload), and with an accessor after it, it is a rule
     *  error. */
    fun pos(rule: Any, p: Position, kill: Boolean = false): RulePos? {
        if (p.root() == AnyClassStatic) {
            if (kill && p == AnyClassStatic) return RulePos(AccessPathBase.ClassStatic, emptyList(), any = false)
            errors.reject(rule, "AnyClassStatic outside RemoveAllMarks (§1.3)"); return null
        }
        return pos(rule, p.resolveAp())                                    // REUSE TaintEvaluator.kt:81-101
    }
    private tailrec fun Position.root(): Position = if (this is PositionWithAccess) base.root() else this
    fun pos(rule: Any, p: PositionAccess): RulePos? {
        val acc = p.accessors()
        val anyCount = acc.count { it == AnyAccessor }
        if (anyCount > 1) { errors.reject(rule, "two AnyField accessors in a position (§1.3)"); return null }        // (1)
        if (anyCount == 1 && acc.last() != AnyAccessor) {
            errors.reject(rule, "an accessor after AnyField in a position (§1.3)"); return null                     // (2)
        }
        val any = anyCount == 1
        val path = if (any) acc.dropLast(1) else acc
        val base = p.base().let { if (it == AccessPathBase.Return) resultBase else it }   // `Result` (§1.3; §4.7 at a throw)
        return RulePos(base, path.map { manager.accessors.index(it) }, any)           // DD6
    }

    /** The path of a Cleaner (Part I §6). */
    fun node(path: List<AccessorIdx>): PathNode? = manager.path(path)

    /** DD7: a rule mark (by its name) and a literal mark (`TaintMarkAccessor.mark` is the name). */
    fun mark(m: RuleMark): TaintMark = manager.marks.mark(m.name)
    fun mark(name: String): TaintMark = manager.marks.mark(name)

    /** §4.2: `ContainsMark(P, T)` -> `(P, $, T)`; `ContainsMarkOnAnyField(P, T)` or `ContainsMark(P.AnyField, T)` -> `(P, [any], T)`.
     *  Null after a rule error of the position. */
    fun literal(rule: Any, l: TaintMarkAwareConditionExpr.Literal): PathFact? = when (l) {
        is ContainsMarkLiteral -> pos(rule, l.position)?.fact(mark(l.mark.mark))
        is ContainsMarkOnAnyAccessorLiteral -> pos(rule, l.position)?.copy(any = true)?.fact(mark(l.mark.mark))
    }

    /** §4.2 for a source and a sink: a negated literal counts as true; `Or` gives one cube per alternative. The caller
     *  passes only a `valid` rule (below), so every literal has a position: a rule error rejects the whole rule, never one
     *  cube (§1.3, D29). A pass rule takes no cube: it has no mark literal (§4.2, D24; Part II §28.5). REUSE
     *  removeTrueLiterals (TaintMarkAwareConditionExpr.kt:48-50) and explodeToDNF (:110-123). */
    fun cubes(rule: Any, cond: ExprOrConstant): List<List<PathFact>> = when {
        cond.isFalse -> emptyList()
        cond.isTrue -> listOf(emptyList())
        else -> {
            val positive = cond.expr.removeTrueLiterals { it.negated } ?: return listOf(emptyList())
            positive.explodeToDNF().map { cube ->
                cube.literals.map { checkNotNull(literal(rule, it)) { "a rejected rule reaches the forms (§1.3): $rule" } }.distinct()
            }.distinct()
        }
    }

    /** §1.3, D29: A RULE ERROR REJECTS THE WHOLE RULE. The check reads the RAW rule once, before the static evaluation:
     *  every action position (`pos`; `kill` for a `RemoveAllMarks`), every mark literal of the condition, a negated one too
     *  (the `Position` overload of `pos`, so `AnyClassStatic` in a literal is a rule error), and the kind checks of §1.4 and
     *  §4.4, and A PASS RULE WITH AN `AnyField` PREMISE (`CopyAllMarks(P.AnyField -> Q)`, `CopyMark(T, P.AnyField -> Q)`,
     *  also with an `AnyField` target; D33): it reads one field that it does not know, so its result is a may, but a `$`
     *  result of an `[any]` premise keeps its layer (ap.md §4.1), so W6 cannot keep the may out of the normal layer.
     *  Today's rule base has none (the only `AnyField` of a pass rule is a target, Go `json.Unmarshal`). A rejected rule
     *  has no form at any place: every rule query of Part II keeps only the valid rules (`sinks` of a
     *  call, `sources`, `cleanSteps`, both loops of `rewriterSteps`, `passRules`, `readSources`, the entry and exit
     *  rules: Part II §27.2, §28.1, §28.3, §28.5, §29), so the summary rewriter never selects a rejected rule (§5.2). After the
     *  filter, `cubes`, `markTarget`, `pass` and `unconditional` never meet a bad position (their checks are asserts).
     *  One rule error does not reject: a pass rule with a mark literal (D24: `RuleErrors.applied`, Part II §28.5).
     *  Memoised per rule: a rule object is the same at every statement and in every run. */
    fun valid(rule: TaintConfigurationItem): Boolean = validity.computeIfAbsent(rule, ::validate)
    private val validity = ConcurrentHashMap<TaintConfigurationItem, Boolean>()

    private fun validate(rule: TaintConfigurationItem): Boolean {
        var ok = true
        fun reject(reason: String) { errors.reject(rule, reason); ok = false }
        fun at(p: Position, kill: Boolean = false): RulePos? = pos(rule, p, kill).also { if (it == null) ok = false }
        val literals = markLiterals(rule.rawCondition)                                // (position, ContainsMarkOnAnyField)
        val anyLiteral = literals.map { (p, onAny) -> at(p)?.any == true || onAny }.any { it }   // every literal is read
        /** markTarget (Part II §27.2): a class position takes no any target (a source or an end fact: `[any-taint]`,
         *  I14, SI19), and with a class target no `[any]` literal. */
        fun target(a: AssignMark, premises: Boolean) {
            val to = at(a.position) ?: return
            if (to.isClass && to.any) reject("[any-taint] target on a class position (§1.4)")
            if (to.isClass && premises && anyLiteral) reject("ContainsMarkOnAnyField with a class target (§1.4, I12 (b))")
        }
        when (rule) {
            is TaintConfigurationSource -> rule.actionsAfter.forEach { target(it, premises = true) }
            is TaintConfigurationSink -> rule.trackFactsReachAnalysisEnd.forEach { target(it, premises = false) }  // end facts: from zero
            is TaintPassThrough -> for (a in rule.actionsAfter) {
                val (from, to) = when (a) { is CopyAllMarks -> a.from to a.to; is CopyMark -> a.from to a.to; else -> continue }
                val f = at(from); val t = at(to)
                if (f != null && t != null && (f.isClass || t.isClass)) reject("pass rule from or to a class position (§1.4)")
                if (f != null && f.any) reject("pass rule with an AnyField premise (§1.3, D33)")
            }
            is TaintCleaner -> for (a in rule.actionsAfter) when (a) {
                is RemoveMark -> at(a.position)
                is RemoveAllMarks -> at(a.position, kill = true)?.let {
                    if (it.base == AccessPathBase.ClassStatic && it.any) reject("RemoveAllMarks(P.AnyField) on S (§1.4)")
                }
                else -> Unit
            }
        }
        if (rule is TaintStaticFieldSource) {                                          // §4.4: the read source at `x = C.s`
            if (!rule.condition.isTrue()) reject("read source with a condition (§4.1)")
            if (rule.actionsAfter.any { a -> pos(rule, a.position)?.let { it.base != resultBase } == true })
                reject("read source target is not the read value (§4.4)")
        }
        return ok
    }

    /** The mark literals of a raw condition, negated ones too: (the position, `ContainsMarkOnAnyField`). */
    private fun markLiterals(c: Condition): List<Pair<Position, Boolean>> = buildList {
        fun walk(x: Condition) {
            when (x) {
                is CommonCondition.Atom -> when (val a = x.atom) {
                    is ContainsMark -> add(a.position to false)
                    is ContainsMarkOnAnyField -> add(a.position to true)               // JVM/taint/ContainsMarkOnAnyField.kt
                    else -> Unit                                                       // a non-mark atom
                }
                is CommonCondition.Not -> walk(x.arg)
                is CommonCondition.And -> x.args.forEach(::walk)
                is CommonCondition.Or -> x.args.forEach(::walk)
                else -> Unit                                                           // True
            }
        }
        walk(c)
    }
    private val TaintConfigurationItem.rawCondition: Condition get() = when (this) {
        is TaintConfigurationSource -> condition
        is TaintConfigurationSink -> condition
        is TaintPassThrough -> condition
        is TaintCleaner -> condition
    }
```

### 27.2 The rule-to-edge path and the sources (call, entry, exit, read)

Every rule element that makes a mark is `premises -> target`: a source and an end fact (`AssignMark`, from the zero fact
or from the literals of the condition) and a `CopyMark` pass rule (from its own premise, the copied mark). One
function makes the edge for all of them (`ruleEdge`), and one function makes the target of an `AssignMark`
(`markTarget`; `valid`, §27.1, checked it by `interpreter.md` §1.4). Users: `source` (the sources stage of a call, §28.3; the entry rules and the exit rules, §29),
`endFacts` (§27.3), `pass` (§27.4), `readSources` (below).

THE TAINT ANNOTATION (`interpreter.md` I14, D32; `ap.md` S15). The target tail is the one carrier of the rule kind
(§23.1 `MicroEdge.may`): the caller of `markTarget` knows the kind. A SOURCE (`source`, `readSources`) with an `AnyField`
target gets the target tail `[any-taint]` with the Empty exclusion (`RulePos.sourceFact`): `zero.$ (zeroMark) ->
P.[any-taint] (T)`, `Q.t' (T') -> P.[any-taint] (T)`, or a conjunctive edge with that target. Its marks are concrete
(the rule names `T`; the premise mark is `zeroMark` or the literal mark `T'`, I6), so the `CompiledEdge` checks of W8
hold (Part I §5.3). A PASS rule (`pass`) keeps `[any]` for an `AnyField` target (a may, D18; an `AnyField` premise is a
rule error, D33). An END FACT (`endFacts`) takes the target of a source (`markTarget` → `sourceFact`:
`P.[any-taint] (T)` for an `AnyField` position; I14, SI19), but it is not a source-seed place (§23.1). A
`ContainsMarkOnAnyField` literal is the premise `Q.[any] (T')` (`literal`), never `[any-taint]`: no forward form has the
premise `[any-taint]` (I14; `MicroEdge.init`).

```kotlin
    /** THE RULE-TO-EDGE PATH (§4.1, §5.3). The premises have the tail `$` or `[any]` and a concrete mark (S9); the
     *  target has a concrete mark and no `*` tail (W7). No premise: from the zero fact (an unconditional source, an end
     *  fact). One: a plain micro edge (a conditional source, a `CopyMark`). Two or more: a conjunctive edge (an ND source;
     *  a pass rule has one premise, §4.2, D24). The target tail `[any-taint]` comes only from `source` and `readSources`
     *  (I14). */
    fun ruleEdge(b: MicroEdgeBuilder, premises: List<PathFact>, target: PathFact) = when (premises.size) {
        0 -> b.edge(PathEdge(ZERO_FACT, target, ExclusionSet.Empty))
        1 -> b.edge(PathEdge(premises[0], target, ExclusionSet.Empty))
        else -> b.conjunction(ConjunctiveEdge(premises.map { Pattern(it, ExclusionSet.Empty) }, target))
    }

    /** §1.4: the target `P.t (T)` of an `AssignMark`. `valid` (Part II §27.1) checked the class rules: a class position
     *  takes no any target, and with a class target no `[any]` literal (I12 (b)). A SOURCE target with an AnyField position
     *  is `[any-taint]` (I14: a must, every location at or below `P` gets the mark; D32); an END-FACT target is a source
     *  target too (interpreter.md I14, §4.1 END FACTS): `P.$ (T)`, or `P.[any-taint] (T)` for an AnyField position (§34 SI19). */
    fun markTarget(rule: Any, a: AssignMark): PathFact =
        checkNotNull(pos(rule, a.position)) { "a rejected rule reaches the forms (§1.3): $rule" }
            .let { it.sourceFact(mark(a.mark)) }               // a source and an end fact: the same target (I14, SI19)

    /** §4.1: `zero.$ (zeroMark) -> P.t (T)`, `Q.t' (T') -> P.t (T)`, or a conjunction (§5.3), with `t` = `$` or
     *  `[any-taint]` (I14). The sources at a call (Part II §28.3), at the method start (the entry-point sources, also the
     *  Spring DTO source, §29) and at an exit (§29). A `valid` rule only. */
    fun source(b: MicroEdgeBuilder, rule: TaintConfigurationItem, cube: List<PathFact>, a: AssignMark) {
        ruleEdge(b, cube, markTarget(rule, a))
    }

    /** §4.4: the read source at `x = C.s` (`TaintStaticFieldSource`). Today JIRMethodSequentFlowFunction.kt:235-268 (a
     *  non-true condition was a TODO). A condition, or a target that is not the read value, is a rule error: `valid`
     *  rejects the whole rule (Part II §27.1). */
    fun readSources(b: MicroEdgeBuilder, s: JIRAssignInst) {
        val field = (s.rhv as? JIRFieldRef)?.field?.field?.takeIf { it.isStatic } ?: return
        val x = accessPathBase(s.lhv) ?: return
        val found = rules.sourceRulesForStaticField(field, s, fact = null).filter(::valid)
        if (found.isEmpty()) return
        b.keepZero()                                                               // I11 (d)
        for (rule in found) for (a in rule.actionsAfter) {
            val p = checkNotNull(pos(rule, a.position))                            // valid: the base is `Result`
            ruleEdge(b, emptyList(), p.copy(base = x).sourceFact(mark(a.mark)))   // `x` is a local: no class
        }                                                                          // target; AnyField: `x.[any-taint] (T)` (I14)
    }
```

### 27.3 Sinks and end facts

A sink is not an edge: it is a set of patterns that the core checks with `ApOps.checkMark` (Part I; `ap.md` §4.9). On
each pattern of a `SinkRule`, `MarkCheck.Holds` triggers the pattern (a plain sink: the sink; a conjunctive sink: one
input of the k-ary join of its literals), `MarkCheck.Request` raises the request (run 1, a FLOW fact), and
`MarkCheck.None` does nothing.

```kotlin
    /** §4.1, §4.2, ap.md §4.9: one SinkRule per alternative. I11 (f): every pattern has the tail `$` or `[any]`.
     *  `mayBeArray(i)`: only for a sink at a call (Part II §28.1): ARRAY ELEMENTS OF A CALL SINK (§4.2). A literal on
     *  `arg(i)·ρ` whose argument may be an array is the `Or` of `(arg(i), ρ, t, T)` and `(arg(i), [e]·ρ, t, T)`, so a cube
     *  gives one alternative per choice (a conjunctive sink too). The entry and exit sinks pass no `mayBeArray`. All the
     *  alternatives have the same `rule`, so they have one vulnerability key; each one seeds its own patterns (§4.9).
     *  `SinkRule.alternative` is the index in this list: the cube order of `explodeToDNF`, then the order of the
     *  cartesian product of the array choices. Both orders are fixed for a rule at a statement, so the index is the same
     *  in every run and every context (I5). */
    fun sinks(rule: TaintConfigurationSink, cond: ExprOrConstant, mayBeArray: (Int) -> Boolean = { false }): List<SinkRule> {
        val ends = endFacts(rule)
        val alternatives: List<List<Pattern>> = cubes(rule, cond).flatMap { cube ->
            if (cube.isEmpty()) return@flatMap listOf(listOf(ZERO_PATTERN))
            cube.map { elementChoices(it, mayBeArray) }                      // REUSE DF/util/ListUtils.kt:127
                .cartesianProductMapTo { choice -> choice.map { Pattern(it, ExclusionSet.Empty) }.distinct() }
        }.distinct()
        return alternatives.mapIndexed { i, patterns -> SinkRule(rule, alternative = i, patterns, ends) }
    }

    /** Today `arrayElementConditionReaders` (`JVM/taint/JIRMethodCallTaintUtil.kt:191-203`): a reader with the prefix `[e]`
     *  on an `arg(i)` fact. Not for `this`, `Result` or `S`. */
    private fun elementChoices(lit: PathFact, mayBeArray: (Int) -> Boolean): List<PathFact> {
        val b = lit.base as? AccessPathBase.Argument ?: return listOf(lit)
        if (!mayBeArray(b.idx)) return listOf(lit)
        return listOf(lit, lit.copy(path = listOf(elementIdx) + lit.path))
    }
    private val elementIdx: AccessorIdx = manager.accessors.index(ElementAccessor)

    /** §4.1 END FACTS: `trackFactsReachAnalysisEnd` as the targets of a source from the zero fact (the rule-to-edge path,
     *  GEN mode). An end-fact target is a source target (I14): `P.$ (T)`, or `P.[any-taint] (T)` for an AnyField position (SI19). */
    fun endFacts(rule: TaintConfigurationSink): List<MicroEdge> = MicroEdgeBuilder().apply {
        for (a in rule.trackFactsReachAnalysisEnd) ruleEdge(this, emptyList(), markTarget(rule, a))
    }.buildGen().edges
```

### 27.4 Pass rules (unresolved callee)

`CopyMark(T, P → Q)` copies one mark. The copied mark is not a condition: it is the premise `P.$ (T)` of the pass edge
(`P.AnyField` is a rule error, D33; `interpreter.md` §1.3, §4.1, §4.2), so a fact at `P` with `T` gives `Q.t (T)`. A
pass rule has no mark-dependent condition (`interpreter.md` §4.2, D24): a pass rule with a mark literal is the one rule
error that does not reject (`interpreter.md` §1.3): it is logged once and applies without its mark literals. One check
in the caller does this for `CopyAllMarks` and `CopyMark`
(`passRules`, §28.5). So `pass` takes no cube, and a pass rule never makes a conjunctive edge. An `AnyField` target of a
pass rule is `[any]`, a MAY (`interpreter.md` D18, I14): the rule does not know the field (`Map.put` writes one
element), so W6 puts every result of it in the demand layer, and in the backward run every result of its reversal is
in the demand layer too, also a `$` result (`MicroEdge.may`, §23.1; `interpreter.md` §4.9). A vulnerability that rests
on it stays a DEMAND entry (`AnyTaintExCases2.PassRule.pass_not_confirmed`). An `AnyField` position on the PREMISE side is
a rule error (`valid`, §27.1; `interpreter.md` §1.3, D33), so `pass` never meets it.

```kotlin
    /** §4.1 pass-rule rows and the AnyField table. The caller removed the mark literals (§4.2, D24; Part II §28.5), so a
     *  pass rule gives plain micro edges only. A `valid` rule only (Part II §27.1: the positions and the class rule of §1.4 were
     *  checked). Gives the from base and the to base of the edges that it made, or null (not a pass action). */
    fun pass(b: MicroEdgeBuilder, rule: TaintConfigurationItem, action: Action): Pair<AccessPathBase, AccessPathBase>? {
        val (fromPos, toPos) = when (action) {
            is CopyAllMarks -> action.from to action.to
            is CopyMark -> action.from to action.to
            else -> return null
        }
        val from = checkNotNull(pos(rule, fromPos)) { "a rejected rule reaches the forms (§1.3): $rule" }
        val to = checkNotNull(pos(rule, toPos)) { "a rejected rule reaches the forms (§1.3): $rule" }
        check(!from.isClass && !to.isClass)                                                // valid: §1.4
        check(!from.any)                                                                   // valid: D33, an AnyField premise
        b.edge(keepEdge(from.base))                                                        // b.* -> b.*
        when (action) {
            // P.* -> Q.* ; P.* -> Q.[any]  (a may: `[any]`, never `[any-taint]`; I14, D18)
            is CopyAllMarks -> b.edge(PathEdge(from.star(), to.star(), ExclusionSet.Empty))
            // P.$ (T) -> Q.t (T): the copied mark is the one premise (§4.2), not a condition; `t` = `$` or `[any]`
            is CopyMark -> mark(action.mark).let { t -> ruleEdge(b, listOf(from.fact(t)), to.fact(t)) }
            else -> Unit
        }
        return from.base to to.base
    }
```

### 27.5 Cleaners (`interpreter.md` §4.2 cleaner rule, D20, §5.2, §1.4)

```kotlin
    /** The steps of one cleaner rule, actions in order (§4.8). §4.2 (D20): only an UNCONDITIONAL cleaner acts. The
     *  rewriter decides the non-mark atoms statically: false removes the rule, true drops out. If a mark literal is left
     *  (positive or negated, on any position, `ContainsMark` or `ContainsMarkOnAnyField`), the cleaner does not act and
     *  raises no request. So every acting cleaner is unconditional, and its `<string-bytes>` row (§5.2) applies. */
    fun cleanSteps(rule: TaintCleaner, cond: ExprOrConstant, types: PositionTypeResolver): List<CleanStep> =
        if (cond.isTrue) rule.actionsAfter.flatMap { unconditional(rule, it, types) } else emptyList()

    /** §5.2 mapping; `RemoveAllMarks` on S is the kill of §1.4; `RemoveAllMarks(AnyClassStatic)` is the whole-base
     *  cleaner (Part II §31.3). A `valid` rule only: a rule error rejects the whole rule (§1.3, Part II §27.1), so every position
     *  here has a form. The rewriter passes the action positions of a valid user rule (Part II §28.3). */
    fun unconditional(rule: TaintConfigurationItem, a: Action, types: PositionTypeResolver): List<CleanStep> = when (a) {
        is RemoveMark -> {
            val p = checkNotNull(pos(rule, a.position)) { "a rejected rule reaches the forms (§1.3): $rule" }
            val t = mark(a.mark)
            val reach = when {                                             // CleanReach: Part I §6
                p.any -> CleanReach.BELOW                                   // P.AnyField, Exact or ExactAndAnyField
                a.reach == TaintCleanReach.Exact -> CleanReach.EXACT
                else -> CleanReach.AT_AND_BELOW                             // ExactAndAnyField (D9)
            }
            val bytes = stringBytes(p, types)                             // ADAPT TaintEvaluator.kt:52-60
            listOfNotNull(Cleaner(p.base, node(p.path), reach, t), bytes?.let { Cleaner(p.base, node(p.path + it), reach, t) })
                .map { lowerCleaner(it, types) }
        }
        is RemoveAllMarks -> {
            val p = checkNotNull(pos(rule, a.position, kill = true)) { "a rejected rule reaches the forms (§1.3): $rule" }
            when {
                // AnyClassStatic: the WHOLE-BASE CLEANER (S, atAndBelow, all), the empty path (interpreter.md §1.4, I12 (e)).
                // Every fact on S lies inside it, so it drops the fact whole and raises no request.
                p.base == AccessPathBase.ClassStatic && p.path.isEmpty() ->
                    listOf(CleanStep.Clean(Cleaner(AccessPathBase.ClassStatic, path = null, CleanReach.AT_AND_BELOW, mark = null)))
                p.base == AccessPathBase.ClassStatic && p.any -> error("a rejected rule reaches the forms (§1.4): $rule")   // valid
                p.base == AccessPathBase.ClassStatic -> listOf(CleanStep.Kill(MicroEdgeBuilder().apply {
                    touch(AccessPathBase.ClassStatic); strongKeep(AccessPathBase.ClassStatic, p.path).forEach { edge(it) }
                }.build()))
                else -> listOf(lowerCleaner(Cleaner(p.base, node(p.path),
                    if (p.any) CleanReach.BELOW else CleanReach.AT_AND_BELOW, mark = null), types))   // D10
            }
        }
        else -> emptyList()
    }

    /** F74: lower a named field position after the reach mapping above. Static positions keep §1.4's forms.
     *  A trailing AnyField selected BELOW; it is not an accessor of `path`. Root and element positions retain
     *  their primitive forms. The call's alias stage still uses §3.8; no new alias query acts in callee coordinates. */
    fun lowerCleaner(cl: Cleaner, types: PositionTypeResolver): CleanStep {
        val path = cl.path?.toList().orEmpty()
        if (cl.base == AccessPathBase.ClassStatic || path.isEmpty() || !path.last().isFieldAccessor())
            return CleanStep.Clean(cl)
        val baseType = types.resolve(PositionAccess.Simple(cl.base)) as? JIRType
        val (baseFilter, valueFilter) = typeFilters.forField(baseType, path)
        return fieldCleanStep(cl.base, path, cl.reach, cl.mark,
            baseFilter = baseFilter, valueFilter = valueFilter)
    }

    private fun stringBytes(p: RulePos, types: PositionTypeResolver): AccessorIdx? {
        if (p.any) return null                                             // AnyField has no declared value type
        val rootType = types.resolve(PositionAccess.Simple(p.base)) as? JIRType
        if (typeFilters.valueType(rootType, p.path)?.typeName != "java.lang.String") return null
        return manager.accessors.index(FieldAccessor("java.lang.String", "<string-bytes>", "byte[]"))
    }
}

/** §1.3, D29: a RULE ERROR rejects the WHOLE rule (`JIRRuleForms.valid`) and is logged once per (rule, reason). The one
 *  rule error that does not reject (D24: a pass rule with a mark literal applies without its mark literals) is logged
 *  once by `applied`. So "rule error" has one meaning: `reject`. */
class RuleErrors {
    private val seen = ConcurrentHashMap.newKeySet<Pair<Any, String>>()
    fun reject(rule: Any, reason: String): Boolean {
        if (seen.add(rule to reason)) logger.warn { "Rule rejected ($reason): $rule" }
        return false
    }
    fun applied(rule: Any, reason: String) {
        if (seen.add(rule to reason)) logger.warn { "Rule applied in part ($reason): $rule" }
    }
    private companion object { val logger = KotlinLogging.logger {} }
}
```

---

## 28. The call plan

Implements `interpreter.md` §3, §4.5, §4.6, §5.1–§5.3 and the stage table of `analyzer-core.md` §4.5. One plan per
(method key, call statement): the callees depend on the caller context (`JIRCallResolver` reads it,
`JVM/JIRCallResolver.kt:193-246`).

### 28.1 The builder

```kotlin
internal class JIRCallPlanBuilder(
    private val interp: JIRInterpreter,
    private val entry: JIRMethodEntry,
    private val caller: MethodKey,
    private val s: JIRInst,
    private val call: JIRCallExpr,
) {
    private val forms = interp.ruleForms
    private val callee: JIRMethod = call.method.method                    // the method that the call names (G4)
    private val returnValue: JIRImmediate? = (s as? JIRAssignInst)?.lhv?.let {                // as today, JIRMethodCallFactMapper.kt:151-153
        it as? JIRImmediate ?: error("Non simple return value: $s")
    }
    private val r: AccessPathBase? = returnValue?.let(::accessPathBase)
    private val o: AccessPathBase? = (call as? JIRInstanceCallExpr)?.instance?.let(::accessPathBase)
    private val args: List<AccessPathBase?> = call.args.map(::accessPathBase)
    private val rewriter = JIRMarkAwareConditionRewriter(
        CallPositionToJIRValueResolver(call, returnValue), interp.checker, entry.aliasAnalysis, s)
    private val types = JIRMethodPositionBaseTypeResolver(callee)
    private fun filter(t: JIRType?) = interp.typeFilters.of(t)

    fun build(): CallPlan {
        // §4.2 ARRAY ELEMENTS OF A CALL SINK: REUSE JIRFactTypeChecker.callArgumentMayBeArray (JIRFactTypeChecker.kt:195-199),
        // the static type of the argument value at this call.
        val mayBeArray = { i: Int -> interp.checker.callArgumentMayBeArray(call, AccessPathBase.Argument(i)) }
        val sinks = interp.rules.sinkRulesForMethod(callee, s, fact = null).filter(forms::valid)   // §1.3: a rule error rejects the rule
            .flatMap { forms.sinks(it, rewriter.rewrite(it.condition), mayBeArray) }               // SinkRule.alternative: per rule (§27.3)
        val (callees, unresolved) = resolve()
        val back = bindBack()
        val stages = buildList {
            add(Edges(BEFORE, BOUND, BIND_IN, bindIn()))                                              // step 2
            for (sk in sinks) if (sk.endFacts.isNotEmpty())                                             // step 3
                add(Edges(BOUND, REWRITTEN, END_FACTS, MicroEdgeBuilder().apply { sk.endFacts.forEach { edge(it.edge) } }.buildStage(),
                    Guard.SinkTriggered(sk)))
            add(Edges(BOUND, REWRITTEN, SOURCES, sources()))                                          // step 4
            add(Actions(BOUND, ADDED, cleanSteps()))                                                    // step 5.1
            if (callees.isNotEmpty()) add(Callees(ADDED, RETURNED, callees))                          // step 5.2
            if (unresolved) {                                                                         // step 5.3 (Part II §28.5)
                add(Edges(ADDED, RETURNED, UNRESOLVED, StatementSummary.identities(boundPositions())))   // §3.7 item 1: no filter
                add(Edges(ADDED, RETURNED, UNRESOLVED, passRules()))                                 // §3.7 items 2, 3: declared types
            }
            if (callee.isConstructor) add(Edges(ADDED, REWRITTEN, CONSTRUCTOR, StatementSummary.identities(boundPositions())))   // §3.5: every added fact, S too (SI5); NORMAL identity takes no alias (SI15)
            add(Actions(RETURNED, REWRITTEN, rewriterSteps()))                                       // step 6
            add(Edges(REWRITTEN, AFTER, BIND_BACK, back))
            aliases(back)?.let { add(Edges(REWRITTEN, AFTER, ALIASES, it, Guard.MemoryEffect)) }
        }
        return CallPlan(touched(), stages, sinks, entry = BEFORE, exit = AFTER, passReads = passReads)   // passReads: §28.5
    }

    /** §3.1, §3.3, §4.5 step 1; today JIRMethodCallFactMapper.factIsRelevantToMethodCall (:247-278). Not the zero base. */
    private fun touched(): Set<AccessPathBase> =
        buildSet { add(AccessPathBase.ClassStatic); o?.let(::add); args.filterNotNullTo(this); r?.let(::add) }

    /** The callee positions that a binding fills (an added fact can be on them). */
    private fun boundPositions(): List<AccessPathBase> = buildList {
        add(AccessPathBase.ClassStatic)
        if (o != null) add(AccessPathBase.This)
        args.forEachIndexed { i, a -> if (a != null) add(AccessPathBase.Argument(i)) }
    }
```

### 28.2 Bindings (`interpreter.md` §3.1–§3.3)

ADAPT of `JIRMethodCallFactMapper.mapMethodCallToStartFlowFact` (:210-242) and `mapMethodExitToReturnSingleFlowFact`
(:141-205). Each binding is a `*`-to-`*` micro edge with the mark `*` (I4, I11 (a)).

```kotlin
    /** BEFORE -> BOUND. Caller-side OPERAND filters: o by the declaring class of the method that the call names;
     *  ai by its static type; none on S and zero. */
    private fun bindIn(): StatementSummary = MicroEdgeBuilder().apply {
        edge(keepEdge(AccessPathBase.ClassStatic))                                     // S.* -> S.*
        edge(starEdge(AccessPathBase.Zero, emptyList(), AccessPathBase.Zero, emptyList()))   // zero.* -> zero.* (§3.3)
        o?.let { edge(starEdge(it, emptyList(), AccessPathBase.This, emptyList())); operandFilter(it, filter(callee.enclosingClass.toType())) }
        args.forEachIndexed { i, a ->
            if (a == null) return@forEachIndexed
            edge(starEdge(a, emptyList(), AccessPathBase.Argument(i), emptyList())); operandFilter(a, filter(call.args[i].type))
        }
    }.buildStage()

    /** REWRITTEN -> AFTER. RESULT filters at the caller. No `exc`, no local, no zero (I11 (c)). No constant edge: no form
     *  makes a fact on a constant base (§3.1; SI6). */
    private fun bindBack(): StatementSummary = MicroEdgeBuilder().apply {
        edge(keepEdge(AccessPathBase.ClassStatic))
        if (o != null && o !is AccessPathBase.Constant) {
            edge(starEdge(AccessPathBase.This, emptyList(), o, emptyList()))
            resultFilter(o, filter((call as JIRInstanceCallExpr).instance.type))
        }
        args.forEachIndexed { i, a ->
            if (a == null || a is AccessPathBase.Constant) return@forEachIndexed
            edge(starEdge(AccessPathBase.Argument(i), emptyList(), a, emptyList())); resultFilter(a, filter(call.args[i].type))
        }
        if (r != null) { edge(starEdge(AccessPathBase.Return, emptyList(), r, emptyList())); resultFilter(r, filter(returnValue!!.type)) }
    }.buildStage()
```

### 28.3 Sources, cleaners, rewriter

```kotlin
    /** §4.1 THE RULE STATEMENT OF A CALL: the zero keep edge and the sources (no other keep edge). Only the `valid` rules
     *  (§1.3, Part II §27.1), here and in every rule query below. */
    private fun sources(): StatementSummary = MicroEdgeBuilder().apply {
        keepZero()
        for (rule in interp.rules.sourceRulesForMethod(callee, s, fact = null).filter(forms::valid)) {
            val cubes = forms.cubes(rule, rewriter.rewrite(rule.condition))
            for (a in rule.actionsAfter) for (cube in cubes) forms.source(this, rule, cube, a)
        }
    }.buildStage()

    /** §4.5 step 5.1, §4.8: the cleaner rules of the named method, in rule order. */
    private fun cleanSteps(): List<CleanStep> = interp.rules.cleanerRulesForMethod(callee, s, fact = null)
        .filter(forms::valid).flatMap { forms.cleanSteps(it, rewriter.rewrite(it.condition), types) }

    /** §5.2 THE SUMMARY REWRITER: RULE-GUIDED FLOW. It acts for the methods that user-defined rules cover. Its purpose:
     *  the rule overrides the real data flow of the callee. At the action positions of a selected rule, the rewriter
     *  cleans the relevant marks of the rule from every result at RETURNED, so for these marks the rule, not the callee
     *  body, decides what comes back (a source then adds its own target in the SOURCES stage). This is a by-design
     *  override, not a cleaner placement: D20 does not govern it.
     *  RULE SELECTION (the non-mark atoms are decided statically by the rewriter of the call):
     *  * every user-defined SOURCE rule whose condition is not statically false (`!isFalse`, as today);
     *  * every user-defined CLEANER rule only if it is UNCONDITIONAL (`isTrue`). A conditional user cleaner does not act
     *    through the rewriter, as it does not act in step 5.1 (D20). Today it acts here when its condition is not
     *    statically false (:77-85; `interpreter.md` D23).
     *  * NEVER a rule that a rule error rejected (`valid`, §1.3, §5.2): a rejected source rule gives no source edge, so it
     *    must not clean its marks from the callee results either.
     *  ADAPT of JIRMethodCallRuleBasedSummaryRewriter.userRuleDefinedActions (:54-88): the same rule queries
     *  (`allRelevant = true`), `clean(P, exact, T)` for each relevant mark and action position. AN `AnyField` ACTION
     *  POSITION OF A SOURCE (`AssignMark` on `PositionWithAccess(P, AnyField)`; interpreter.md §5.2, D34) gives
     *  `clean(P, atAndBelow, T)`: the source marks every location at or below `P` (a must, I14), so these are its rule
     *  positions (today `RemoveMark(T, position, Exact)`, :105, is the `below` row on that position). An `AnyField`
     *  action position of a CLEANER keeps the row of `unconditional` (`(P, below, T)`, as today). A rule with no
     *  `UserDefinedRuleInfo` is not selected (the Spring `__cleanup__` cleaner, §31.3, too). */
    private fun rewriterSteps(): List<CleanStep> {
        val out = ArrayList<CleanStep>()
        fun add(rule: TaintConfigurationItem, positions: List<Position>, source: Boolean) {
            val info = rule.info as? UserDefinedRuleInfo ?: return
            for (p in positions) for (m in info.relevantTaintMarks) {
                val anyTarget = source && forms.pos(rule, p)?.any == true                  // valid: `p` has a form (§27.1)
                val action = if (anyTarget) RemoveMark(RuleMark(m), (p as PositionWithAccess).base, TaintCleanReach.ExactAndAnyField)
                             else RemoveMark(RuleMark(m), p, TaintCleanReach.Exact)          // `(P, atAndBelow, T)`, D34; else `exact`,
                                                                                             // and `(P, below, T)` for a cleaner's `P.AnyField`
                val steps = forms.unconditional(rule, action, types)                       // RuleMark: Part II §27.1
                out += steps                                                              // keep every read/clean/write action
            }
        }
        for (rule in interp.rules.sourceRulesForMethod(callee, s, fact = null, allRelevant = true).filter(forms::valid))
            if (!rewriter.rewrite(rule.condition).isFalse) add(rule, rule.actionsAfter.map { it.position }, source = true)   // sources: !isFalse
        for (rule in interp.rules.cleanerRulesForMethod(callee, s, fact = null, allRelevant = true).filter(forms::valid))
            if (rewriter.rewrite(rule.condition).isTrue)
                add(rule, rule.actionsAfter.filterIsInstance<RemoveMark>().map { it.position }, source = false)       // cleaners: isTrue only
        return out
    }
```

### 28.4 Callees (`interpreter.md` §3.6, §3.9)

GENERALIZE `JIRCallResolver`: it reads two members of the caller context only (`JIRCallResolver.kt:198, 209, 216`). Four
declarations change their parameter type; the old core keeps its behaviour, because each one reads only these two members.

```kotlin
// JVM/JIRCallResolver.kt
interface JIRCallResolutionContext {
    val methodEntryPoint: MethodEntryPoint
    val aliasAnalysis: JIRLocalAliasAnalysis?
}
fun resolve(call: JIRCallExpr, location: JIRInst, context: JIRCallResolutionContext): List<MethodResolutionResult>   // :90
private fun resolveVirtualMethod(baseMethod: JIRMethod, call: JIRVirtualCallExpr, location: JIRInst,
                                 context: JIRCallResolutionContext): List<MethodResolutionResult>                // :116-121
private fun resolveValueTypeConstraints(value: JIRValue, location: JIRInst,
                                        context: JIRCallResolutionContext): Set<TypeConstraintInfo>?           // :193-197
private inner class MethodContextCreator(val context: JIRCallResolutionContext, /* other parameters unchanged */)  // :288-292

// JVM/analysis/JIRMethodAnalysisContext.kt
class JIRMethodAnalysisContext(
    /* ... */
    override val methodEntryPoint: MethodEntryPoint,           // overrides MethodAnalysisContext and JIRCallResolutionContext
    /* ... */
    override val aliasAnalysis: JIRLocalAliasAnalysis?,        // was `val` (:25)
    /* ... */
) : MethodAnalysisContext, JIRCallResolutionContext
```

```kotlin
    /** ADAPT of JIRMethodCallResolver.resolvedJirMethodCalls (:159-211): no subscription, no lambda event. §3.9 (D19): a
     *  `Lambda` result is the lambda methods that the prescan knows for this call, and nothing else (no unresolved path);
     *  if the prescan knows none, it is a resolution failure: the unresolved path (§3.7).
     *  §3.6, I8 (D28): an EMPTY METHOD (no instruction: a native or an abstract method, a method with no body) is not
     *  analysable, so it is never a callee: the resolver drops it. If every resolution result is an empty method, the
     *  call is an UNRESOLVED call (§3.7: the default identity and the pass rules). An abstract method never comes here
     *  (`isValidConcreteMethod`, JIRCallResolver.kt:183-184); a native method of a project class does. A call with an
     *  empty and a non-empty result enters only the non-empty callee (gap G12).
     *  §3.7: AN EMPTY RESOLUTION RESULT (no callee and no failure) is a resolution failure: the unresolved path, as in Go.
     *  On the JVM only a raw `JIRLambdaExpr` gives it (JIRCallResolver.kt:98-101); the required lambda features remove
     *  every raw lambda, and `JIRMethodEntries` asserts them (Part II §31.2). */
    private fun resolve(): Pair<List<MethodKey>, Boolean> {
        val ctx = object : JIRCallResolutionContext {
            override val methodEntryPoint = caller
            override val aliasAnalysis = entry.aliasAnalysis
        }
        val callees = LinkedHashSet<MethodKey>()
        var unresolved = false
        val results = interp.callResolver.resolve(call, s, ctx)
        if (results.isEmpty()) return emptyList<MethodKey>() to true                                // §3.7: as a failure
        for (res in results) when (res) {
            JIRCallResolver.MethodResolutionResult.MethodResolutionFailed -> unresolved = true
            is JIRCallResolver.MethodResolutionResult.ConcreteMethod ->
                if (!isEmptyMethod(res.method.method)) callees += keys(res.method)                   // an empty method: dropped
            is JIRCallResolver.MethodResolutionResult.Lambda -> {
                val impls = entry.lambdas[s.location.index]
                if (impls.isNullOrEmpty()) unresolved = true
                else impls.forEach { callees += keys(MethodWithContext(it, EmptyMethodContext)) }
            }
        }
        if (results.all { it is JIRCallResolver.MethodResolutionResult.ConcreteMethod && isEmptyMethod(it.method.method) })
            unresolved = true                                                                        // every result is an empty method: §3.7
        return callees.toList() to unresolved
    }

    private fun isEmptyMethod(m: JIRMethod): Boolean = m.instList.size == 0                            // JIRLanguageManager.isEmpty

    /** As MethodAnalyzer.methodEntryPoints (DF/ap/ifds/MethodAnalyzer.kt:815-821). Never an empty method (above), so the
     *  method has its entry statement (`JMethodEnterInst`, §31.2). */
    private fun keys(m: MethodWithContext): List<MethodKey> =
        interp.epResolver.resolveEntryPoints(m.method, m.ctx).map { MethodEntryPoint(m.ctx, it) }
```

### 28.5 Unresolved callee (`interpreter.md` §3.7)

ADAPT of `JIRMethodCallFlowFunction.propagateUnresolvedCallFact` (:253-339). Two `UNRESOLVED` stages start at `ADDED`:

* the default identity with no filter (today `unresolvedCallDefaultFactPropagation`, :330-339, has no type check);
* the pass rules with the declared-type filters (`interpreter.md` §5.1 row "pass rule"; today
  `DF/taint/Propagator.kt:55-87`, `:89-105`).

With one stage, the operand filter of a base would also filter its identity edge (STAGE mode filters the input before
every edge). The two stages start at one point, so the core gives each added fact to both and takes the union
(`analyzer-core.md` §4.5). Each stage reverses on its own.

```kotlin
    /** The from bases of the pass rules of the rule set (not of the default getter rules): `CallPlan.passReads`. */
    private val passReads = LinkedHashSet<AccessPathBase>()

    /** §3.7 items 2 and 3: the pass rules of the named method and the default getter rules (JIRMethodGetDefault.kt:37-51). */
    private fun passRules(): StatementSummary = MicroEdgeBuilder().apply {
        val rules = interp.rules.passTroughRulesForMethod(callee, s, fact = null).filter(forms::valid)   // §1.3: rejected rules go
            .map { Triple(it, rewriter.rewrite(it.condition), true) } +
            interp.defaultGetModel?.defaultPropagationRules(callee).orEmpty().filter { forms.valid(it.rule) }
                .map { Triple(it.rule, it.condition, false) }
        val read = HashSet<AccessPathBase>()
        val written = HashSet<AccessPathBase>()
        for ((rule, cond, ofRuleSet) in rules) {
            if (cond.isFalse) continue                                                  // §4.2: a false static atom removes the rule
            // §4.2, D24: a pass rule has no mark-dependent condition. A mark literal is left after the static atoms when
            // the rewritten condition is an expression: ONE check for CopyAllMarks and CopyMark, the one rule error that
            // does not reject (§1.3): it is logged once, and the rule applies without its mark literals.
            if (!cond.isTrue) forms.errors.applied(rule, "pass rule with a mark literal (interpreter.md §4.2, D24): applied without it")
            for (a in rule.actionsAfter) {
                val (from, to) = forms.pass(this, rule, a) ?: continue                // null: not a pass action
                read += from; written += to
                if (ofRuleSet) passReads += from
            }
        }
        fun declared(b: AccessPathBase) = filter(types.resolve(PositionAccess.Simple(b)) as? JIRType)
        read.forEach { operandFilter(it, declared(it)) }                          // the from positions
        written.forEach { resultFilter(it, declared(it)) }                        // the to positions
    }.buildStage()
```

The external method tracker records the arrival of TAINT at an unresolved callee, as today (per caller fact and callee
position, :285-295), not the call itself. So the builder does not call it. The core calls a hook, `UnresolvedCallObserver`
(§22.2: an addition to `analyzer-core.md` §4.9):

```kotlin
// bidi.interp
/** The core calls it in run 1 for each new added-fact delta on `position` at the UNRESOLVED stages of `plan`. Run 1 is the
 *  one full forward pass, as the full scan of today. No effect on facts. */
fun interface UnresolvedCallObserver {
    fun reached(call: CommonCallExpr, position: AccessPathBase, plan: CallPlan)
}

// JIRInterpreter : Interpreter, UnresolvedCallObserver
override fun reached(call: CommonCallExpr, position: AccessPathBase, plan: CallPlan) {
    val t = externalMethodTracker ?: return
    val callee = (call as JIRCallExpr).method.method
    if (position == AccessPathBase.ClassStatic || JIRCallResolver.alwaysIgnoreMethod(callee)) return    // as :285-287
    // today `startFactBase in passEvaluator.relevantPositionBase`: a pass rule of the rule set reads this position.
    // Today the tracker runs BEFORE the default getter rules (JIRMethodCallFlowFunction.kt:282-302), so they do not count.
    val ruleApplied = position in plan.passReads
    t.trackExternalMethod("${callee.enclosingClass.name}#${callee.name}", callee.description, position.toString(), ruleApplied)
}
```

Today `relevantPositionBase` holds the from-base of a rule only when the condition of the rule holds on the fact
(`TaintConfigUtils.kt:48-60`); the hook reads `CallPlan.passReads`: the from-bases of the pass rules of the rule set whose
condition is not statically false. A pass rule with a mark literal applies without it (D24), so the hook counts its
from-base on every fact. Such a rule is rare, and `ruleApplied` only splits the report into two lists. The default
getter rules are not in `passReads`, as today: a library getter with no model stays in the list "without rules"
(`ExternalMethodTracker.kt:37-54`).

### 28.6 Aliases on call results (`interpreter.md` §3.8)

GENERALIZE `JIRAliasUtil.kt`: add the helper of `BWD/../analysis/JIRAliasUtil.kt:31-35`, and let
`forEachAliasAfterCallStatement` (:25-35) call it (no behaviour change).

```kotlin
// JVM/analysis/JIRAliasUtil.kt
fun JIRLocalAliasAnalysis.aliasesPersistedThroughCall(base: AccessPathBase.LocalVar, statement: JIRInst): List<AliasApInfo> {
    val before = findAlias(base, statement) ?: return emptyList()
    val after = findAliasAfterStatement(base, statement)?.toSet() ?: return emptyList()
    return before.filter { it in after }.mapNotNull { it.relevantApInfo() }            // constant bases skipped
}
```

```kotlin
    /** AC1, AC2: for each binding back `P.* -> x.*` with a local `x`, the edge `P.* -> b.q.*` per alias (b, q) of x that
     *  holds before and after the call. The stage has the guard `MemoryEffect` (AC3, AC4). The alias analysis is out of
     *  scope and not changed: no filter for a static alias base `b = S`, which does not occur under A1 (§32). */
    private fun aliases(back: StatementSummary): StatementSummary? {
        val aa = entry.aliasAnalysis ?: return null
        val b = MicroEdgeBuilder()
        for (e in back.edges) {
            val x = e.edge.to.base as? AccessPathBase.LocalVar ?: continue
            for (alias in aa.aliasesPersistedThroughCall(x, s)) {
                if (alias.base == x && alias.accessors.isEmpty()) continue
                b.edge(starEdge(e.edge.from.base, emptyList(), alias.base, alias.accessors.map { interp.idx(it.apAccessor()) }))
            }
        }
        return b.buildStage().takeIf { it.edges.isNotEmpty() }
    }
}
```

The core applies the edges of a `SOURCES` or `UNRESOLVED` stage, the keep edges of a `CleanStep.Kill`, and the read
and write of a `CleanStep.Field`, with
`ApOps.applyEdge(c, premise, e, statementEdge = true, mode, out)` on the caller edge `(i -> c)`: the static exception and
its position request go to the caller, on `i` (`interpreter.md` §2.1 step 4, `ap.md` §4.10 item 1). Every other stage
uses `statementEdge = false`.

### 28.7 The zero fact at a call (`interpreter.md` §3.3, §4.6)

No extra form. The plan has: the zero binding in `BIND_IN`; `ZERO_KEEP` in `SOURCES`; no cleaner on the zero base; no
zero edge in `UNRESOLVED`, `CONSTRUCTOR`, `BIND_BACK`, `ALIASES`. So the zero fact fires the unconditional sinks and the
sources at `BOUND`, enters every callee at `ADDED`, and never comes back by a binding (I11 (c)). It also passes over
the call (it is not in `touched`).

---

## 29. Entry rules and exit rules

Implements `interpreter.md` §4.3, §4.7, §4.4 item 3; `analyzer-core.md` §4.4.

```kotlin
internal object JIRBoundaryForms {
    /** §5.1 method-start filter. Copy of JIRMethodStartFlowFunction.locationClass (:64-84): the context type of
     *  `this` or the selected argument; without a context, `this` uses the enclosing class. */
    private fun MethodContext.locationClass(key: MethodKey, base: AccessPathBase): JIRClassOrInterface? = when (this) {
        is EmptyMethodContext -> if (base is AccessPathBase.This) (key.method as? JIRMethod)?.enclosingClass else null
        is JIRInstanceTypeMethodContext -> if (base is AccessPathBase.This) typeConstraint.type else null
        is JIRArgumentTypeMethodContext -> if (base is AccessPathBase.Argument && base.idx == argIdx) typeConstraint.type else null
        is CombinedMethodContext -> first.locationClass(key, base) ?: second.locationClass(key, base)
        else -> error("Unexpected method context: $this")
    }

    /** §4.3: zero keep, entry-point sources with a true condition, entry sinks (unconditional only), and the context
     *  filter of an initial fact. ADAPT of JIRMethodStartFlowFunction (:29-127). STATEMENT mode: a base with a
     *  context filter is touched with its identity edge, so the filter applies and the fact passes. */
    fun entryRules(interp: JIRInterpreter, entry: JIRMethodEntry, key: MethodKey): RuleStatement {
        val method = key.method as JIRMethod
        val s = key.statement as JIRInst
        val rw = calleeRewriter(interp, entry, method, s)
        val b = MicroEdgeBuilder().apply { keepZero() }
        val bases = listOf<AccessPathBase>(AccessPathBase.This) + method.parameters.indices.map { AccessPathBase.Argument(it) }
        for (base in bases) {
            val cls = key.context.locationClass(key, base) ?: continue                // copy of :64-84
            b.keep(base); b.operandFilter(base, interp.typeFilters.of(cls.toType()))
        }
        // §1.3. THE SPRING DTO SOURCE (§4.1 entry-point row, I14, D32): the Spring rule provider gives an entry-point rule
        // of a controller argument `arg` of a class type the two actions `AssignMark(m, arg)` and
        // `AssignMark(m, PositionWithAccess(arg, AnyFieldAccessor))` (core/src/main/kotlin/org/opentaint/jvm/sast/project/spring/
        // SpringRuleProvider.kt:61-76, `taintObjectFields`). `source` gives `zero.$ (zeroMark) -> arg.$ (m)` and the taint
        // edge `zero.$ (zeroMark) -> arg.[any-taint] (m)`: a normal `[any-taint]` fact at the start, so a finding through it
        // can be CONFIRMED (ap.md §4.9; run 1 when the sink reads the object in this method, run 3 through a getter:
        // AnyTaintExCases2.G); a setter keeps it normal with the written field in its exclusion (AnyTaintExCases.S). The
        // entry mark `m` of both actions goes at the normal exit at any depth, the `[any-taint]` leaf too (§4.7 step 4, D35).
        for (rule in interp.rules.entryPointRulesForMethod(method, s, fact = null).filter(interp.ruleForms::valid))
            if (rw.rewrite(rule.condition).isTrue) rule.actionsAfter.forEach { interp.ruleForms.source(b, rule, emptyList(), it) }
        val sinks = interp.rules.sinkRulesForMethodEntry(method, s, fact = null).filter(interp.ruleForms::valid).flatMap { rule ->
            val cond = rw.rewrite(rule.condition)
            if (cond.isTrue) interp.ruleForms.sinks(rule, cond) else emptyList()       // unconditional only, as today (:99-101)
        }
        return RuleStatement(b.build(), genSummary(sinks), sinks)
    }

    /** §4.3 step 3: the ENTRY MARKS. Today `taintMarksAssignedOnMethodEnter` (JIRMethodStartFlowFunction.kt:42-47). */
    fun entryMarks(rules: RuleStatement): Set<TaintMark> = rules.summary.edges
        .filter { it.isSource }.mapNotNullTo(HashSet()) { (it.edge.to.mark as? MarkSlot.Concrete)?.mark }

    /** §4.7 at both exits (`JMethodExitNormalInst`, `JMethodExitExceptionalInst`). ADAPT of
     *  JIRMethodSequentFlowFunction.propagateExitFact (:120-226; today it runs at both exits, :120-126). At the exceptional
     *  exit the rule position `Result` reads `exc` (`ruleFormsAtThrow`, Part II §25; today JIRSequentTaintUtil rebases the
     *  fact on `exc` to `Result`, :67, and back, :82). Steps 1 and 2 apply at both exits; steps 3 to 5 only at the normal
     *  exit. STATEMENT mode: the zero fact and the read bases keep themselves; every other fact passes (step 1).
     *  A CONJUNCTIVE EXIT SOURCE (an alternative with two or more positive literals) is an ND edge at the exit, as at a call
     *  (interpreter.md §4.7 step 1, §5.3, D31; ap-history.md F68 (4)): `ruleEdge` makes the `ConjunctiveEdge` in
     *  `summary.conjunctions`, and the core stores the input of each literal in the conjunction store of the method key
     *  (per edge, exit statement and literal index; ap.md §8.9). A full combination is an exit item with the union of the
     *  premise sets, after the field limit; it goes through steps 2 to 5 and becomes a summary at the normal exit, an ND
     *  summary if its premise set has two or more members (E6). Its target tail is `$`, or `[any-taint]` for an AnyField
     *  target (interpreter.md §4.7 step 1, I14: `source` gives it, a conjunctive edge is a source). An `[any-taint]`
     *  input of a literal that overlaps it (with its exclusion) keeps the result normal (ap.md §4.6;
     *  `MarkCheck.Holds.normalPart`, Part I §5.8).
     *  The plain exit sources are sources too: an AnyField target is `[any-taint]`. */
    fun exitRules(interp: JIRInterpreter, entry: JIRMethodEntry, key: MethodKey, exit: CommonInst, entryRules: RuleStatement): ExitRules {
        val normal = when (exit) {
            is JMethodExitNormalInst -> true
            is JMethodExitExceptionalInst -> false
            else -> error("not a boundary exit: $exit (the boundary feature, Part II §31.2)")   // exitNodes gives only these two
        }
        val forms = if (normal) interp.ruleForms else interp.ruleFormsAtThrow
        val method = key.method as JIRMethod
        val rw = calleeRewriter(interp, entry, method, exit)
        val b = MicroEdgeBuilder().apply { keepZero() }                              // I11 (d): both exits
        for (rule in interp.rules.exitSourceRulesForMethod(method, exit, fact = null).filter(forms::valid)) {   // §1.3
            for (cube in forms.cubes(rule, rw.rewrite(rule.condition))) {             // the empty cube too: both exits (D26)
                cube.forEach { b.keep(it.base) }                                       // every read base: f stays in the worklist
                rule.actionsAfter.forEach { forms.source(b, rule, cube, it) }          // two or more literals: a ConjunctiveEdge (D31)
            }
        }
        val sinks = interp.rules.sinkRulesForMethodExit(method, exit, fact = null, initialFacts = null)   // every fact (D21)
            .filter(forms::valid)
            .flatMap { forms.sinks(it, rw.rewrite(it.condition)) }   // no array alternative (§4.2); ZERO_PATTERN at both exits (D22, D26)
        return ExitRules(RuleStatement(b.build(), genSummary(sinks), sinks),
            globalStateDrop = normal && sinks.isNotEmpty(),                           // §4.7 step 3 (G2): normal exit only
            entryMarks = if (normal) entryMarks(entryRules) else emptySet())          // §4.7 step 4 (G2): normal exit only
    }

    /** The rewriter of the entry and exit rules: rule positions in the coordinates of the method (today
     *  JIRTaintAnalysisContext.prepareMethodRules, JVM/taint/JIRTaintAnalysisContext.kt:170-186). */
    private fun calleeRewriter(interp: JIRInterpreter, entry: JIRMethodEntry, method: JIRMethod, s: CommonInst) =
        JIRMarkAwareConditionRewriter(CalleePositionToJIRValueResolver(method), interp.checker, entry.aliasAnalysis, s)

    private fun genSummary(sinks: List<SinkRule>) =
        MicroEdgeBuilder().apply { sinks.forEach { sk -> sk.endFacts.forEach { edge(it.edge) } } }.buildGen()
}
```

The touched bases and the keep edges of the two boundary rule statements are the spec rows of `interpreter.md` §4.3
(THE RULE STATEMENT OF THE METHOD START) and §4.7 (the exit rule statement), `analyzer-core.md` §4.9 (STATEMENT mode):
the zero base with `ZERO_KEEP`; at the start each base with a context filter, with `b.keep(base)` and its operand filter;
at an exit the base of every literal of an exit source, with `b.keep(it.base)`; a source target is a gen-only target. The
code above is that form.

The core uses `ExitRules` as `interpreter.md` §4.7 says:

* step 1: `rules.summary` (STATEMENT) on each fact; the results keep the premise; a conjunctive exit source
  (`summary.conjunctions`, D31) stores the input of each literal in the conjunction store of the method key, and a full
  combination joins the worklist with the union of the premise sets, after the field limit (`interpreter.md` §5.3);
* step 2: `checkMark` of each pattern of each `SinkRule` on each item; on `MarkCheck.Holds` (for a conjunctive sink, a
  new full combination of the k-ary join), `sk.endFacts` (GEN) on the zero fact, with the layer of the sink edge; the
  field limit; the results join the worklist;
* step 2, a conjunctive exit sink: each `MarkCheck.Holds` of a literal is stored as the input of that literal in the
  conjunction store (`ConjunctionStore`, Part I §7.10), also when it completes no combination;
* step 3 (THE GLOBAL-STATE RULE): if `globalStateDrop`, the EVALUATED STATICS go, ONLY for an item on `S` whose premise
  is the zero fact (`premise.isZero`: a state that the method or its callees set; such an item is a TAINT tree, so the
  FLOW/concrete split never arises). For such an item, each part on which a mark literal (`ContainsMark`,
  `ContainsMarkOnAnyField`) of an exit sink holds (`MarkCheck.Holds.facts`) is dropped from the item before the summary
  edge (`ops.without`). This holds for a plain and for a conjunctive exit sink. The rest of the item stays. For a
  conjunctive sink the dropped part stays the stored input of its literal (step 2): the evaluated facts are an
  assumption for the next evaluation attempts of that sink, so a later item can complete the combination with them. A
  caller-set `S` fact (an item whose premise is not the zero fact) is evaluated in step 2 (it can report, D21; a
  conjunctive literal stores it) but NOT dropped: it returns to the caller through the callee summary, also the run-1
  FLOW summary and its record (`interpreter.md` §4.7 step 3, D30; `ap-history.md` F68 (3)). As today: today the exit
  sinks run only on zero-premise edges (`JIRMethodExitRuleProvider.kt:18-19`), and only a reached sink drops
  (`JIRSequentTaintUtil.kt:76-85`, `dropFinalFacts`, `JIRMethodSequentFlowFunction.kt:186-188, 271-278`; §34 SI17). The
  code: `analyzer-impl.md` `endAt` step 3 tests `pr.isZero`, and `NaiveClosure.end` tests `pr == setOf(zero)`;
* step 4 (D35): for a zero premise (`premise.isZero`; the item is a TAINT tree),
  `entryMarkRemoval(item.base)?.let { ops.withoutMarks(item, it) }`: every leaf with an entry mark goes, at any depth
  and with both tails (an `[any-taint]/E` leaf with its exclusion, the `AnyField` part of the Spring DTO source); the
  other leaves stay. Not as today: today's `TaintMarkRemover` removes only the root leaf `(b, [], $, m)` (D35, §32);
* step 5: the summary edge, only if `isSummaryBase`.

At the exceptional exit only steps 1 and 2 apply, and the results end there: it is not an end node and makes no summary
edge (`analyzer-core.md` §4.3, §4.4). The unconditional exit sources and the unconditional exit sinks (the empty cube,
`ZERO_PATTERN`) fire on the zero fact at both exits, so an unconditional exit sink can report at both exits: expected
and approved (D22, D26; §34 SI11, SI12). The backward run starts the zero fact at both exits; the start rules of each exit
are `exitRules(method, exit).reversed()` and the seeds of its exit sinks; a seed also takes the reversed exit sources of
that exit (`interpreter.md` §4.9 SEEDS; `DirectedForms.startRules`, Part II §23.7). A reversed end-fact edge of an exit
sink that applies to a requirement there also fires the seeds of that sink (THE TRIGGER OF AN END FACT, F70; Part II
§23.4); an entry sink is unconditional, so its trigger fires no seed. The reversed conjunctive exit source gives every
result in the demand layer (`MicroEdge.conjunctive`, Part II §23.1).

---

## 30. Boundaries: entry, exits, wired graph

Implements `analyzer-core.md` §4.4 and `interpreter.md` I11 (e).

```kotlin
// in JIRMethodEntry (Part II §31.2)
/** analyzer-core.md §4.4: the two boundary exits, `JMethodExitNormalInst` and `JMethodExitExceptionalInst`. Every
 *  analysed method has them: an empty method is never analysed (§28.4), and `JIRMethodEntry` asserts the boundary
 *  instructions (§31.2). */
fun exitNodes(key: MethodKey): List<ExitNode> =
    shared.graph.methodGraph(method).exitPoints()                            // JApplicationSingleExitGraph.kt:26-29
        .map { ExitNode(it, it is JMethodExitExceptionalInst) }.toList()
```

A summary edge exists for every base except a local or a cleaner temporary (`isSummaryBase`, §25; today
`JIRMethodCallFactMapper.isValidMethodExitFact`, `JIRMethodCallFactMapper.kt:207-208`). The zero base is a summary base.
A backward summary `jb -> zero` (a requirement that reaches an unconditional source) must exist. With a normal
layer and non-zero singleton `jb`, it is a record, and the next forward run reads its reversal, the source record
`zero -> jb` (Part I §7.8). A demand-layer result instead gives the forward demand `(D-c = zero, D-p = jb)`
(`analyzer-core.md` §7.4 item 3). Without the zero base the record and this demand are lost.

```kotlin
// JIRInterpreter (Part II §25)
override fun isSummaryBase(base: AccessPathBase): Boolean =
    base !is AccessPathBase.LocalVar && base !is AccessPathBase.CleanerTemporary
//   AccessPathBase.Zero      -> true   (backward `jb -> zero`; forward `{zero} -> zero` is harmless: no binding back)
//   this, arg(i), S, ret     -> true
//   exc, const               -> true   (no binding back takes them; as today)
//   local(i)                 -> false
//   cleaner temporary        -> false  (F74: projected away inside the action)
```

PORT of `BWD/JIRBackwardExitWiringGraph.kt:10-63`. The change: `JIRExitWiredGraph` makes the list of wired nodes once,
and `JIRMethodEntry` keeps the compact graph. So the BFS runs once per method (today: on each `methodGraph` call).

```kotlin
/** I11 (e): a node that reaches no forward exit gets a forward edge to the exceptional exit. Only the zero fact starts
 *  at the exceptional exit, so a caller requirement never enters this code. */
class JIRExitWiredGraph(private val forward: ApplicationGraph<CommonMethod, CommonInst>) :
    ApplicationGraph<CommonMethod, CommonInst> by forward {

    override fun methodGraph(method: CommonMethod): ApplicationGraph.MethodGraph<CommonMethod, CommonInst> {
        val graph = forward.methodGraph(method)
        val exceptionalExit = graph.exitPoints().firstOrNull { it is JMethodExitExceptionalInst } ?: return graph
        val exiting = BitSet()
        val queue = ArrayDeque<CommonInst>()
        for (exit in graph.exitPoints()) if (exiting.mark(exit)) queue.addLast(exit)
        while (queue.isNotEmpty()) for (p in graph.predecessors(queue.removeFirst())) if (exiting.mark(p)) queue.addLast(p)
        val wired = graph.statements().filter { !exiting[it.index] }.toList()
        if (wired.isEmpty()) return graph
        return Wired(this, graph, exceptionalExit, exiting, wired)
    }

    private class Wired(
        override val applicationGraph: ApplicationGraph<CommonMethod, CommonInst>,
        private val graph: ApplicationGraph.MethodGraph<CommonMethod, CommonInst>,
        private val exceptionalExit: CommonInst,
        private val exiting: BitSet,
        private val wired: List<CommonInst>,
    ) : ApplicationGraph.MethodGraph<CommonMethod, CommonInst> by graph {
        override fun successors(node: CommonInst) =
            if (exiting[node.index]) graph.successors(node) else graph.successors(node) + exceptionalExit
        override fun predecessors(node: CommonInst) =
            if (node != exceptionalExit) graph.predecessors(node) else graph.predecessors(node) + wired.asSequence()
    }

    private companion object {
        val CommonInst.index: Int get() = (this as JIRInst).location.index
        fun BitSet.mark(i: CommonInst): Boolean { if (get(i.index)) return false; set(i.index); return true }
    }
}
```

The backward graph is `JIRExitWiredGraph(forward).reversed` (`DF/graph/BackwardGraphs.kt:40-45`), as
`BWD/JIRBackwardAnalysisManager.kt:118`. `MethodInstGraph.build` reads it once per method.

---

## 31. The JIR method context

Implements `analyzer-core.md` §4.8 (the cached part) and §9 (the prescan values).

### 31.1 Prescan lambdas

GENERALIZE `JIRAnalysisManager`: export the lambda values of the prescan. The trackers keep their values after
`selectPhase` (`resetAnalysisCache` resets only the subscribers, `JIRMethodAnalysisContext.kt:78-85`).

```kotlin
/** Call-site index -> the lambda methods that the prescan found, per caller method. Read-only during a run (analyzer-core.md A4). */
typealias PrescanLambdas = Map<JIRMethod, Int2ObjectMap<Set<JIRMethod>>>

// JVM/analysis/JIRAnalysisManager.kt — new member; analyzer-core.md §9: copied once, before run 1.
fun prescanLambdas(): PrescanLambdas {
    val out = HashMap<JIRMethod, HashMap<Int, MutableSet<JIRMethod>>>()
    for (ctx in contexts) {                                                    // :76
        val caller = ctx.methodEntryPoint.method as JIRMethod
        for (e in ctx.lambdaCallResolution.int2ObjectEntrySet()) {
            e.value.forEachRegisteredLambda(object : JIRLambdaTracker.LambdaSubscriber {
                override fun newLambda(method: JIRMethod, lambdaClass: LambdaAnonymousClassFeature.JIRLambdaClass) {
                    val impl = lambdaClass.findMethodOrNull(method.name, method.description) ?: return  // JIRMethodCallResolver.kt:199-200
                    out.getOrPut(caller) { HashMap() }.getOrPut(e.intKey) { HashSet() } += impl
                }
            })
        }
    }
    return out.mapValues { (_, byIdx) -> Int2ObjectOpenHashMap<Set<JIRMethod>>().apply { byIdx.forEach { (i, m) -> put(i, m) } } }
}
```

### 31.2 Method entries and forms

Two levels (DD11):

* PER METHOD (`JIRMethodEntry`): the graphs, the alias analysis, the prescan lambdas, and the forms that do not read
  the context: the statement summaries and the exit rules. Every context of the method shares them (today the
  `EmptyMethodContext` twin, `DF/ap/ifds/MethodAnalyzerStorage.kt:38-47`). No liveness: the forms have no liveness step
  (D25); the alias analysis keeps its own reachability input, as today.
* PER METHOD KEY (`JIRMethodForms : MethodForms`): the forms that read the context of the key: the call plans (the
  callee resolution, `JVM/JIRCallResolver.kt:193-246`) and the entry rules (the start filter by the context type, §29).

`analyzer-core.md` §4.8 gives the same split (per method; per METHOD KEY: the call plans and the entry rules; SI16,
RESOLVED (F68)). The `Interpreter` calls take a `MethodKey`, so `MethodForms` has the same key. `MethodContextCache`
(`analyzer-impl.md` §3.4) gives these `MethodForms` and makes the `DirectedForms` of each run. It has no forms cache of
its own.

THE REQUIRED CLASSPATH FEATURES. Part II reads the JIR that these features make. Production installs all of them
(`core/src/main/kotlin/org/opentaint/jvm/sast/project/ProjectAnalysisContext.kt:122-136`). Part II asserts the first two
rows: `JIRMethodEntry` asserts the boundary feature per method, and `JIRMethodEntries` asserts the two lambda features
once (`interpreter.md` §3.7: they must be installed). `BasicTestUtils` installs only the boundary feature
(`core/opentaint-dataflow-core/opentaint-jvm-dataflow/src/test/kotlin/org/opentaint/dataflow/jvm/BasicTestUtils.kt:48`),
so `JIRInterpreterTestKit` (§33.2) adds the two lambda features to its classpath; a test that needs
`JStringConcatTransformer` installs it.

| Feature | Why Part II needs it | Without it |
|---|---|---|
| `JMethodBoundaryInstFeature` (`core/opentaint-utils/opentaint-jvm-util/src/main/kotlin/org/opentaint/jvm/graph/JMethodBoundaryInstFeature.kt:10-22`; the last feature) | The method key is the entry statement `JMethodEnterInst`; the exit nodes and the exit rules are the boundary exits `JMethodExitNormalInst` and `JMethodExitExceptionalInst` (§29, §30); `JIRExitWiredGraph` wires to the exceptional exit (I11 (e)) | the method key, the exit nodes and the exit rules have no statement, and no wiring is made. `JIRMethodEntry` asserts the feature (below), as today (`JIRAnalysisManager.kt:118-120`), and `exitRules` fails on an exit that is not a boundary exit (§29) |
| `LambdaAnonymousClassFeature` + `LambdaExpressionToAnonymousClassTransformerFeature` (`JVM/LambdaAnonymousClassFeature.kt:60`, `JVM/LambdaExpressionToAnonymousClassTransformerFeature.kt:28`) | A lambda value is an allocation of a `JIRLambdaClass`, so the prescan finds the lambda methods of a call (§31.1) and a lambda call has its callees (§3.9, D19) | a raw `JIRLambdaExpr` stays: the resolver gives no result for it (`JIRCallResolver.kt:98-101`), so the call has no callee stage and takes the unresolved path (§28.4; `interpreter.md` §3.7: an empty result is a failure), and the prescan knows no lambda. `JIRMethodEntries` asserts both features (below), so this does not occur |
| `JStringConcatTransformer` (`core/opentaint-utils/opentaint-jvm-util/src/main/kotlin/org/opentaint/jvm/transformer/JStringConcatTransformer.kt:32`) | `makeConcatWithConstants` becomes calls of `String.concat`, so each operand flows by the rules of `String.concat` (§3.7) | the `invokedynamic` call is an unresolved call with the coarse pass rule of `StringConcatRuleProvider` (`core/opentaint-jvm-sast-dataflow/src/main/kotlin/org/opentaint/jvm/sast/dataflow/StringConcatRuleProvider.kt:20-30`) |

```kotlin
/** One per analysis. JIRMethodContextCache (analyzer-impl.md §3.4) holds it. */
class JIRMethodEntries(
    val graph: JApplicationGraph,                    // the forward graph (JApplicationSingleExitGraph)
    val languageManager: JIRLanguageManager,
    val callResolver: JIRCallResolver,
    val rules: TaintRulesProvider,                   // the alias model reads the pass rules (JIRLocalAliasAnalysis.kt:71-115)
    val aliasParams: JIRLocalAliasAnalysis.Params,
    val cancellation: Cancellation,
    val prescanLambdas: PrescanLambdas,
) {
    init {                                                       // THE REQUIRED CLASSPATH FEATURES (above); interpreter.md §3.7
        val installed = graph.cp.features.orEmpty()
        check(installed.any { it is LambdaAnonymousClassFeature } &&
            installed.any { it is LambdaExpressionToAnonymousClassTransformerFeature }) {
            "the lambda features are not installed: a raw lambda would be an unresolved call (Part II §31.2)"
        }
    }
    private val byMethod = ConcurrentHashMap<JIRMethod, JIRMethodEntry>()
    private val byKey = ConcurrentHashMap<MethodKey, JIRMethodForms>()

    operator fun get(key: MethodKey): JIRMethodEntry = get(key.method as JIRMethod)
    operator fun get(m: JIRMethod): JIRMethodEntry = byMethod.computeIfAbsent(m) { JIRMethodEntry(it, this) }

    /** The forms of one method key (both directions). */
    fun forms(interp: JIRInterpreter, key: MethodKey): JIRMethodForms =
        byKey.computeIfAbsent(key) { JIRMethodForms(interp, get(it), it) }
}

/** analyzer-core.md §4.8 "per method": the parts that do not read the context. Only an analysed method has an entry: a
 *  method key needs an entry statement, and an empty method is never a callee (§28.4) and has no entry statement. */
class JIRMethodEntry internal constructor(val method: JIRMethod, internal val shared: JIRMethodEntries) {
    init {
        val instructions = method.instList.instructions
        check(instructions.isNotEmpty()) { "Method $method has no instruction: an empty method is never analysed" }   // I8, D28
        check(instructions.last() is JMethodExitExceptionalInst) {                            // as today, JIRAnalysisManager.kt:118-120
            "Method $method is analysed without method boundary instructions"
        }
    }
    internal val size = shared.languageManager.getMaxInstIndex(method) + 1
    fun index(s: CommonInst): Int = shared.languageManager.getInstIndex(s)

    @Suppress("UNCHECKED_CAST")
    private val common get() = shared.graph as ApplicationGraph<CommonMethod, CommonInst>

    /** The alias analysis keeps its own inputs, as today (JIRAnalysisManager.kt:122-135): the reachability of the locals
     *  is its input only. No form reads it (no liveness step, D25). The alias analysis is not changed (§32). */
    val aliasAnalysis: JIRLocalAliasAnalysis? by lazy {
        if (!shared.aliasParams.useAliasAnalysis) null
        else JIRLocalAliasAnalysis(shared.graph.methodGraph(method).entryPoints().first(), shared.graph,
            shared.callResolver, shared.rules, JIRLocalVariableReachability(method, shared.graph, shared.languageManager),
            shared.cancellation, shared.languageManager, shared.aliasParams)
    }
    val lambdas: Int2ObjectMap<Set<JIRMethod>> = shared.prescanLambdas[method] ?: Int2ObjectMaps.emptyMap()

    /** analyzer-core.md §4.4: the forward graph, and the wired reversed graph (Part II §30). */
    fun graph(direction: Direction): MethodInstGraph =
        if (direction == Direction.FORWARD) forwardGraph else backwardGraph
    private val forwardGraph by lazy { MethodInstGraph.build(shared.languageManager, common, method) }
    private val backwardGraph by lazy { MethodInstGraph.build(shared.languageManager, JIRExitWiredGraph(common).reversed, method) }

    /** The forms that do not read the context: one per method, for every key. */
    internal val statements = FormsCache<StatementSummary>(size) { it.reversed() }
    internal val exits = FormsCache<ExitRules>(size) { ExitRules(it.reversed(), globalStateDrop = false, entryMarks = emptySet()) }

    // fun exitNodes(key: MethodKey): List<ExitNode>: the code is in Part II §30
}

/** analyzer-core.md §4.8 "per METHOD KEY" (DD11, SI16): the forms that read the context, and the per-method forms of
 *  `e`. */
class JIRMethodForms internal constructor(
    private val interp: JIRInterpreter,
    private val e: JIRMethodEntry,
    private val key: MethodKey,
) : MethodForms {
    private val plans = FormsCache<CallPlan>(e.size) { it.reversed() }
    private val entry = FormsCache<RuleStatement>(1) { it.reversed() }

    override fun statement(direction: Direction, s: CommonInst) =
        e.statements.get(e.index(s), direction) { JIRStatementForms(interp, e).build(s as JIRInst) }
    override fun call(direction: Direction, s: CommonInst, call: CommonCallExpr) =
        plans.get(e.index(s), direction) { JIRCallPlanBuilder(interp, e, key, s as JIRInst, call as JIRCallExpr).build() }
    override fun entryRules(direction: Direction) =
        entry.get(0, direction) { JIRBoundaryForms.entryRules(interp, e, key) }
    override fun exitRules(direction: Direction, exit: CommonInst) =
        e.exits.get(e.index(exit), direction) { JIRBoundaryForms.exitRules(interp, e, key, exit, entryRules(Direction.FORWARD)) }
}
```

The exit rules read the key only for `entryMarks`, and the entry sources do not read the context (only the start filter
does), so the first key that builds them gives the same result as every other key.

MEMORY. The caches keep their forms with strong references for the whole analysis: the forward forms of every reached
method key, and the reversals after the first backward run. The forms are small against the run stores (one
`StatementSummary` per statement, with a few `PathEdge`s; one `CallPlan` per call). A soft reference as today
(`JVM/analysis/JIRMethodAnalysisContext.kt:53-85`) is not safe here. The core keys run state by the identity of form
objects: a `ConjunctiveEdge` or a conjunctive `SinkRule` is the `rule` key of `ConjunctionStore.add` (Part I §7.10),
`Guard.SinkTriggered` names its `SinkRule`, and so does the `trigger` of a reversed `END_FACTS` stage (the backward core
fires the trigger seeds once per statement and `SinkRule`, §23.4). A rebuilt form is a new key, and the stored literal
inputs are lost. The
caches have the lifetime of the analysis (`JIRMethodEntries` lives as long as the `IterationDriver`). The vulnerability
store does not read this identity: a witness names its sink alternative by `SinkRule.alternative` (§23.4), which a
rebuilt form keeps.

The run part of today's context (`JIRTaintAnalysisContext`, the sink tracker) is not here. The core owns the stores of a
run (`analyzer-core.md` §4.8).

### 31.3 The Spring dispatcher: no rule needs the fact

Implements `interpreter.md` §1.3 (`AnyClassStatic`), §1.4, §5.2 and D27. The generated Spring dispatcher calls a
controller, then `__cleanup__()`, and loops (`ndMethodDispatch`). Today the cleaner of `__cleanup__` is built from the
fact: for a fact on `S`, `RemoveAllMarks(ClassStatic(C))` for each class of the fact except the registry class
`__spring_registry__` (`SpringRuleProvider.kt:110-143`). The new core queries every rule with `fact = null` (§27), so
that cleaner would be lost, and static taint would cross the dispatched controllers. Three changes make the cleaner
fact-free. The effect stays the same: the static content outside the registry goes, and the registry stays.

1. THE GENERATOR saves and restores the registry around each `__cleanup__()` call. The registry class has one static
   field per component (`SpringWebProject.kt:160-200`); the components are registered before the dispatcher is generated
   (`createSpringProjectContext`, :97-127). The generator is shared with the old core (the prescan and the old full scan,
   `analyzer-impl.md` §8.1, run the new dispatcher too). Their results do not change: the fact-based cleaner of the old
   core keeps the registry facts across `__cleanup__` (`SpringRuleProvider.kt:110-125`), and the restore is a strong
   write of the same facts back. The new statements are generated locations, so no trace shows them
   (`core/src/main/kotlin/org/opentaint/jvm/sast/sarif/TraceMessageBuilder.kt:1130-1137`).

```kotlin
// core/src/main/kotlin/org/opentaint/jvm/sast/project/spring/SpringWebProject.kt, ndMethodDispatch: replaces the cleanup
// call (:308-313). `loopEnd` is the jump target of every block (:327) and of the switch default (:331), so it is the
// first save, or the cleanup call when the registry is empty.
val registry = componentRegistryField.values.sortedBy { it.name }                // the static fields of __spring_registry__
val saved = registry.mapIndexed { i, f -> JIRLocalVar(index = 1 + i, "%reg_${f.name}", f.type) }   // index 0 is %sel (:275)
var firstSave: JIRInstLocation? = null
for ((f, local) in registry.zip(saved)) instructions.addInstWithLocation(dispatcher) { loc ->
    if (firstSave == null) firstSave = loc
    JIRAssignInst(loc, local, JIRFieldRef(instance = null, f))                      // %reg_f = __spring_registry__.f
}
val cleanupLoc: JIRInstLocation
instructions.addInstWithLocation(dispatcher) { loc ->
    cleanupLoc = loc
    JIRCallInst(loc, JIRStaticCallExpr(cleanupMethod.staticMethodRef(), emptyList()))   // __cleanup__()
}
for ((f, local) in registry.zip(saved)) instructions.addInstWithLocation(dispatcher) { loc ->
    JIRAssignInst(loc, JIRFieldRef(instance = null, f), local)                      // __spring_registry__.f = %reg_f
}
val loopEnd: JIRInstLocation = firstSave ?: cleanupLoc
```

2. THE RULE POSITION `AnyClassStatic`: the whole static base. A new JVM rule position; Part II maps it to `S` with the
   empty path, only in a `RemoveAllMarks` action (`JIRRuleForms.pos`, §27.1; elsewhere a rule error), and the action is
   the whole-base cleaner `(S, atAndBelow, all)` (`JIRRuleForms.unconditional`, §27.5). The current core never applies
   it (item 3), so its readers only map it to `ClassStatic` with the empty path, and the resolvers give no value.

```kotlin
// core/opentaint-configuration-rules/configuration-rules-jvm/src/main/kotlin/org/opentaint/dataflow/configuration/jvm/Position.kt
/** The whole static base `S` (every class). Only in `RemoveAllMarks` (interpreter.md §1.3, §1.4). */
data object AnyClassStatic : Position {
    override fun toString(): String = javaClass.simpleName
}

// Every exhaustive `when` over `Position` gets a branch:
//   JVM/taint/TaintEvaluator.kt:73-79   resolveBaseAp:      is AnyClassStatic -> AccessPathBase.ClassStatic
//   JVM/taint/TaintEvaluator.kt:83-101  resolveAp:          is AnyClassStatic -> PositionAccess.Simple(baseAp)
//   JVM/JIRCallPositionResolver.kt:39-48, 56-69:           is AnyClassStatic -> CallPositionValue.None
//   JVM/JIRLocalAliasAnalysis.kt:90-104 toExternalObject:  is AnyClassStatic -> return null
//   core/opentaint-jvm-sast-dataflow/src/main/kotlin/org/opentaint/jvm/sast/dataflow/rules/MethodTaintConfigurationResolver.kt:499-505, 555-561: as ClassStatic
//   core/src/test/kotlin/org/opentaint/jvm/sast/project/tester/TestTraces.kt:41-48: as ClassStatic
```

3. THE RULE PROVIDER gives the whole-base cleaner for a query with no fact, and today's cleaner for a query with a fact.

```kotlin
// core/src/main/kotlin/org/opentaint/jvm/sast/project/spring/SpringRuleProvider.kt, cleanerRulesForMethod (:104-128)
if (method is SpringGeneratedMethod) {
    if (method.name != GeneratedSpringControllerDispatcherCleanupMethod) return emptyList()
    // The new core (fact == null): the unconditional cleaner of the whole static base. The dispatcher restores the
    // registry after the call (item 1), so no rule needs the fact.
    if (fact == null) return listOf(TaintCleaner(method, mkTrue(), listOf(RemoveAllMarks(AnyClassStatic)), info = null))
    // The current core of the prescan (with a fact): today's code, unchanged.
    val cleanupPositions = fact.cleanupPositions() ?: return emptyList()
    return listOf(TaintCleaner(method, mkTrue(), cleanupPositions.map { RemoveAllMarks(it) }, info = null))
}
```

The current core never applies the fact-free cleaner. It queries the cleaners with a fact (`cleanRulesForCallStatement`,
`JVM/taint/JIRTaintAnalysisContext.kt:85-94`, from `JIRMethodCallFlowFunction.kt:167`). It queries with `fact = null`
only outside the prescan (`allRelevantCleanRulesForCallStatement`, `JIRTaintAnalysisContext.kt:58-61`, which gives
nothing in the prescan), and its only user, the summary rewriter, skips a rule with no `UserDefinedRuleInfo`
(`JIRMethodCallRuleBasedSummaryRewriter.kt:77-78`). The new rewriter skips it too (§28.3).

In the new core, the plan of the call `__cleanup__()` (a static call with no argument: only `S` and the zero fact are
bound) has the step `CleanStep.Clean(Cleaner(S, [], AT_AND_BELOW, all))` at `BOUND -> ADDED`: every fact on `S` goes,
whole, with no request. `S` is always touched, so no `S` fact passes over the call. The restores then write the saved
registry facts back: `%reg_f.* -> S.<__spring_registry__>.f.*`. A mark on the bare class position
`S.<__spring_registry__>` is not restored; no rule makes one (the registry class is generated, and no rule names it).

---

## 32. Deviations from today (`implementation-background.md` → Interpreter migration differences → §6) in code

| # | Reused class | Today | New |
|---|---|---|---|
| D1 | `StatementSummaryBuilder.read` (:74-92) | `keepAllExcept(base, accessors)` + `edge(source, source)` | `MicroEdgeBuilder.read`: `keep(base)` (§24) |
| D2 | same, `base == to` branch (:86-88) | `edge(fact(base).exclude(accessors.first()), null)` | no edge; only `x.f.* -> x.*` |
| D3 | `MethodSequentFlowFunction.transfer` (:65-68) | `to == null -> refineInitial(effect.exclusion)` | no `to == null` edge exists; `ApOps.applyEdge` gives the `above` case in the demand layer |
| D4 | `JIRMethodCallFlowFunction.propagateFact` (:141-143) | `addSideEffectRequirement(factReader)` | no reader; `ApOut.markRequest` (Part I) |
| D5 | `JIRMethodCallSummaryHandler.handleSummary` (:40-69) | `MethodSummaryEdgeApplicationUtils` delta with refinement | `ApOps.satisfying` + `ApOps.applySummary` (Part I) |
| D6 | `JIRMethodCallFactMapper` (:141-242) | `rebase` per fact | `bindIn`/`bindBack` micro edges (§28.2); same mapping and filters |
| D8 | `JIRTaintCleanActionEvaluator` (`TaintEvaluator.kt:29-71`) | `FinalFactAp.clean` with `DeepAccessorExclusion` | `Cleaner` + `ApOps.clean` (F75 same-base abstract `*∖T`, the request in run 1 before path tests) |
| D9 | same, `RemoveMark(.., ExactAndAnyField)` | cleans through an `[any]` only | `CleanReach.AT_AND_BELOW` (§27.5) |
| D10 | same, `RemoveAllMarks(P.AnyField)` | removes an `[any]` child | `CleanReach.BELOW`, every mark |
| D11 | `JIRFactTypeChecker.AccessorFilter` (:84-136) | `FilterResult` over the fact tree, marks, `[value]` and type info as accessors | the same filter as `TypeFilter.may` on the path only (`localFilter`), whose accessors are fields, elements and `<C>` (`ap.md` W5); the mark policy is `TypeFilter.markPolicy`, at the root and below each `[e]` with the element type, as today (§26.2) |
| D12 | same, `AnyAccessor` case (:88-94) | `[any]` on a primitive base: Reject (keeps `$` only) | the filter never reads the tail: `[any]` and `[any-taint]` stay |
| D13 | `AccessTree.concat` filter | the caller delta filtered at `*` | none (Part I) |
| D14 | `JIRMethodSummaryEdgeProcessor.process` (:15-25) | compatibility filter at the exit | removed |
| D16 | `MethodAnalyzer` depth gate | `INITIAL_ALLOWED_FACT_DEPTH` | none (`analyzer-impl.md` §2.1) |
| D17 | rules on `S` | applied as written | the mapping of `interpreter.md` §1.4 (`JIRRuleForms.source`, `pass`, `unconditional`, `endFacts`) and its rule errors, each of which rejects the whole rule (`JIRRuleForms.valid`, §27.1) |
| D18 | `TaintPassActionEvaluator.copyAllFacts` (`DF/taint/Propagator.kt:55-87`) | `readPosition` + `mkAccessPath` copy the subtree below the any-field node | an `AnyField` target: `P.* -> Q.[any]`, `P.$ (T) -> Q.[any] (T)` (§27.4); every result in the demand layer (W6: a may), also every result of its reversal, a `$` result too (`MicroEdge.may`, §23.1); not the `[any-taint]` target of a source (D32); an `AnyField` premise is a rule error (D33) |
| D19 | `JIRMethodCallResolver.resolvedJirMethodCalls` (:184-209) | a `Lambda` result adds `ResolutionFailure` beside the lambda methods | the prescan lambda methods only; a resolution failure only with no known lambda (`JIRCallPlanBuilder.resolve`, §28.4) |
| D20 | `TaintConfigUtils.applyCleaner` (`JVM/TaintConfigUtils.kt:62-92`) | the condition is evaluated on the bound fact (`TaintFactAwareConditionEvaluator`) | only a cleaner with no mark literal left acts (`JIRRuleForms.cleanSteps`, §27.5) |
| D21 | `JIRMethodExitRuleProvider.sinkRulesForMethodExit` (`core/opentaint-jvm-sast-dataflow/src/main/kotlin/org/opentaint/jvm/sast/dataflow/JIRMethodExitRuleProvider.kt:18-19`) | exit sinks only on zero-premise edges | the rules are read with `initialFacts = null`: every fact (§29) |
| D22 | `JIRMethodSequentFlowFunction.applyUnconditionalSinks` (:191-200) | a stub | `ZERO_PATTERN` (§27.3): it fires on the zero fact at each exit |
| D23 | `JIRMethodCallSummaryHandler.prepareFactToFactSummary` (:71-79), `handleZeroToZero` (:29-38); `JIRMethodCallFlowFunction.unresolvedCallDefaultFactPropagation` (:330-339); `JIRMethodCallRuleBasedSummaryRewriter.userRuleDefinedActions` (:67-85) | the rewriter on fact-to-fact and ND summaries; the default identity in caller coordinates; a user cleaner rule is selected when its condition is not statically false (:80-81) | the stage `Actions(RETURNED -> REWRITTEN)` on every result at `RETURNED` (§28.1, §28.3); a user source rule with `!isFalse`, a user cleaner rule only with `isTrue` (`rewriterSteps`, §28.3) |
| D24 | `TaintConfigUtils.applyPassThrough` (`JVM/TaintConfigUtils.kt:48-60`) | the condition of a pass rule (`CopyAllMarks`, `CopyMark`) is evaluated on the fact | a pass rule with a mark literal is the one rule error that does not reject (`interpreter.md` §1.3): it is logged once (`RuleErrors.applied`) and applies without its mark literals (one check, `passRules`, §28.5); it makes no conjunctive edge (`pass`, §27.4) |
| D25 | `JIRAnalysisManager.isReachable` (`JVM/analysis/JIRAnalysisManager.kt:293-301`), called by the method analyzer (`DF/ap/ifds/MethodAnalyzer.kt:296`); `JIRLocalVariableReachability` (`JVM/JIRLocalVariableReachability.kt:27-41`) | a fact on a local that is dead at the statement is dropped; in code that reaches no exit every local is dead (the liveness goes backward from `exitPoints()` of the unwired graph) | no liveness member in `Interpreter`, `DirectedForms` or `JIRMethodEntry` (§23.1, §23.7, §31.2): the statement step keeps a fact on a dead local. The alias analysis keeps its own `JIRLocalVariableReachability` input, as today (§31.2) |
| D26 | `JIRMethodSequentFlowFunction.applyUnconditionalSources` (:228-233), `applyUnconditionalSinks` (:191-200) | the unconditional exit sources fire only at `JMethodExitNormalInst`; the unconditional exit sink is a stub | `exitRules` builds every cube, the empty cube too, and every sink (`ZERO_PATTERN`) at both exits (§29) |
| D27 | `SpringRuleProvider.cleanerRulesForMethod` (`core/src/main/kotlin/org/opentaint/jvm/sast/project/spring/SpringRuleProvider.kt:104-143`); `ndMethodDispatch` (`SpringWebProject.kt:308-313`) | the `__cleanup__` cleaner is built from the fact (`fact ?: return emptyList()`) | the dispatcher saves and restores the registry around `__cleanup__()`; with no fact the provider gives `RemoveAllMarks(AnyClassStatic)`, the whole-base cleaner `(S, atAndBelow, all)` (§31.3, §27.1, §27.5) |
| D28 | `EmptyMethodAnalyzer` (`DF/ap/ifds/MethodAnalyzerStorage.kt:19-31`) | JVM: an empty method has no instruction, so no entry statement (`JMethodBoundaryInstFeature.kt:13`) and no method key; the call enters nothing for it, and its bound facts are lost. Go: a resolved empty callee, whose analyzer publishes the identity summary (as `interpreter.md` D28) | `resolve()` drops an empty method; every result empty: the unresolved path (§28.4); a mixed call enters only the non-empty callee (`interpreter.md` G12). The empty-method branches go: `JIRMethodEntry` asserts that its method has instructions (§31.2) |
| D29 | `readPositionWithAnyAccessorSplit` (`DF/taint/FactReaderUtils.kt:54-138`) | a rule position with an inner or a repeated `AnyField` is read | `JIRRuleForms.pos` checks the two invariants; a rule error REJECTS THE WHOLE RULE (`JIRRuleForms.valid`: every action and every mark literal, negated too, before the static evaluation; logged once by `RuleErrors`): no form at any place, and the rewriter does not select it. It never throws (§27.1, §28.3) |
| D30 | `JIRMethodExitRuleProvider.sinkRulesForMethodExit` (`core/opentaint-jvm-sast-dataflow/src/main/kotlin/org/opentaint/jvm/sast/dataflow/JIRMethodExitRuleProvider.kt:18-19`); `JIRSequentTaintUtil.handleReachedSink` (`JVM/taint/JIRSequentTaintUtil.kt:76-85`), `dropFinalFacts` (`JVM/analysis/JIRMethodSequentFlowFunction.kt:186-188, 271-278`) | the exit sinks run only on zero-premise edges; the evaluated `S` facts of a REACHED exit sink are dropped | step 3 of §29: for a zero-premise item the evaluated `S` part goes also when a conjunctive exit sink is not complete, and stays the stored input of its literal; a caller-set `S` item is evaluated (D21) but not dropped (`ExitRules.globalStateDrop`; `analyzer-impl.md` `endAt` step 3, `pr.isZero`) |
| D31 | `JIRMethodSequentFlowFunction.applySourceRules` at the exit (`JVM/analysis/JIRMethodSequentFlowFunction.kt:211-219`), `TaintUtil.applySourceRules` (`DF/taint/TaintUtil.kt:97-104, 203-207`: the `createFinalFact` branch) | an exit source with two or more positive literals fires fact-locally from stored assumptions with the precondition `emptySet()`, under the premise of the fact that completes the combination | an ND edge at the exit: `exitRules` keeps every read base and `ruleEdge` makes the `ConjunctiveEdge` (§29); each literal stores its input in the conjunction store of the method key; a full combination is an exit item with the union of the premise sets, and at the normal exit a summary, an ND summary (E6) if its premise set has two or more members. Not a rule error (§34 SI4) |
| D32 | `TaintSourceActionEvaluator.evaluate` (`DF/taint/Source.kt:16-28`: `mkAccessPath` of the position, `:26`) on `AssignMarkOnAnyAccessor` / `AssignMark` on `PositionWithAccess(P, AnyField)` (the position accessor `AnyFieldAccessor -> AnyAccessor`, `JVM/taint/TaintEvaluator.kt:104`; the Spring DTO entry-point source, `core/src/main/kotlin/org/opentaint/jvm/sast/project/spring/SpringRuleProvider.kt:61-76`) | the source makes a fact with an `[any]` accessor below `P`; in the new AP before F69 it was an `[any]` tail, so W6 put every result in the demand layer and no such finding was confirmed | `RulePos.sourceFact` gives the target `P.[any-taint] (T)` with the Empty exclusion (§27.2) at a call, at the method start, at an exit and at a read, plain or conjunctive; the core keeps its layer (Part I §5.3), so a normal edge with it is complete and a finding through it can be CONFIRMED (run 1 when the sink reads the object in the method of the source, run 3 through a getter). A strong write into the object keeps it normal with the written field in its exclusion (`dto.setName(c)`: `(dto, ., [any-taint], {name}, T)`), a read through an excluded field gives nothing, and the cleaners `atAndBelow`/`below` one accessor below add the field to the exclusion (Part I §5.3, §5.6; `TaintTree.exclusion`); only the demotions of `ap.md` §2.2 make it `[any]` in the demand layer with no exclusion: the field-limit cut, a cleaner `part` row other than `atAndBelow`/`below` one accessor below the fact (the `exact` cleaner at or below it, a cleaner two or more accessors below it), a may target, a demand input (a demand fact, summary or record) and the must-record demotion (Part I §5.3, §5.4, §5.6, §5.7). At the normal exit of an entry point the entry mark of the source goes from every leaf, the `[any-taint]` leaf too (D35). FORWARD ONLY: the seed of an `[any]` sink pattern and the reversed `[any]` literal of a source are `[any]` in the demand layer, and a forward `[any-taint]` target is the reversed premise `[any]` (`SinkRule.seedPatterns`, `StatementSummary.reversed`, `revEdge`; §23.2, §23.4, Part I §6) |
| D33 | `TaintPassActionEvaluator.copyAllFacts`, `copyFinalFact` (`DF/taint/Propagator.kt:55-105`) on a pass rule with an `AnyField` FROM position (`CopyAllMarks(P.AnyField → Q)`, `CopyMark(T, P.AnyField → Q)`, also with an `AnyField` target) | the content below the any-field node of `P` is copied below `Q` (D18) | a rule error: `JIRRuleForms.valid` rejects the whole rule, logged once (§27.1, as D29); `pass` asserts it (§27.4). Today's rule base has no such rule (the only `AnyField` of a pass rule is a target; the JVM default getter rules have none, `JIRMethodGetDefault.kt:29-35`) |
| D34 | `JIRMethodCallRuleBasedSummaryRewriter.userRuleDefinedActions` (:54-88, the `RemoveMark(T, position, Exact)` of each action position, :105) on an `AnyField` action position of a selected source (`AssignMark` on `PositionWithAccess(P, AnyField)`) | `RemoveMark(T, P.AnyField, Exact)`: the `below` row of `interpreter.md` §5.2, so the mark of the callee at `P` itself stays | `clean(P, atAndBelow, T)`: `rewriterSteps` maps the position to `RemoveMark(T, P, ExactAndAnyField)` (§28.3); an `AnyField` position of a cleaner keeps `(P, below, T)` |
| D35 | `TaintMarkRemover` and `dropArgumentsLocalTaintMarks` (`JVM/analysis/JIRMethodSequentFlowFunction.kt:300-314`) through `filterFact` / `AccessTree.AccessNode.filterAccessNode` (`DF/ap/ifds/access/tree/AccessTree.kt:1031-1056`), at the normal exit on a zero-premise fact on `this` or `arg(i)` | the filter reads only the children of the root: a mark accessor of the entry-mark set is rejected, a non-mark accessor gets `Accept`, which keeps its whole subtree; so only `b.$ (m)` goes, and `b.f.$ (m)` and the any child `b.[any] (m)` (the `AnyField` part of the Spring DTO source) leak into the callers | every leaf with an entry mark goes, at any depth and with both tails, an `[any-taint]/E` leaf with its exclusion: `ExitRules.entryMarkRemoval` gives the marks (null: another base, or no entry mark) and `ApOps.withoutMarks` removes them in one leaf walk (§23.4, §29 step 4, Part I §5.8; `TaintMarkRemover` is not reused). Normal TAINT trees never absorb a `$` leaf under an `[any-taint]` leaf (Part I §4.3), so every leaf with the mark is there to remove |

D1 and D2 as code (the old builder stays for the prescan):

```kotlin
// Today, StatementSummaryBuilder.kt:83-88
if (base != to) { keepAllExcept(base, accessors); edge(source, source) }       // y\{f} -> y, y.f -> y.f
else { edge(fact(base).exclude(accessors.first()), null) }                     // x\{f} -> null (refine only)
// New, MicroEdgeBuilder.read (Part II §24)
if (base != to) keep(base)                                                     // y.* -> y.*
edge(starEdge(base, path, to, emptyList()))                                    // y.f.* -> x.*
```

D12 as code:

```kotlin
// Today, JIRFactTypeChecker.kt:88-94: an `[any]` node under a primitive type is rejected
is AnyAccessor -> if (actualType.unboxIfNeeded() is JIRPrimitiveType) return FilterResult.Reject
// New: the same AccessorFilter walks only the path (ApOps.filter, Part I §5.5); the `[any]` tail is not an accessor,
// so this case never runs and the `[any]` leaf stays. The policy reads a concrete mark at the root path and at each
// node of the `[e]` chain, with the type of that level (JIRTypeFilters.make, §26.2).
TypeFilter(may = checker.localFilter(t), markPolicy = /* one level per node of the `[e]` chain: JIRTypeFilters.make */)
```

D25 and D28 as code:

```kotlin
// Today, DF/ap/ifds/MethodAnalyzer.kt:296: the statement step runs only for a fact on a live base
if (edgeFactBase == null || analysisManager.isReachable(apManager, analysisContext, edgeFactBase, edge.statement)) { /* step */ }
// New: the step runs for every fact. JIRMethodEntry builds the reachability only as the input of the alias analysis (§31.2).

// Today, a JIR native method of a project class is a ConcreteMethod callee with no entry statement: no key, no stage
// New, JIRCallPlanBuilder.resolve (§28.4)
if (results.isEmpty()) return emptyList<MethodKey>() to true          // §3.7: an empty result is a failure
is ConcreteMethod -> if (!isEmptyMethod(res.method.method)) callees += keys(res.method)
if (results.all { it is ConcreteMethod && isEmptyMethod(it.method.method) }) unresolved = true
```

KEPT AS TODAY IN CODE (no row above). The gaps G9 and G10 of `interpreter.md` stay as today: a catch handler reads the
facts after each statement of the try range (the JIR graph adds the handlers to the successors of a throwing statement,
`core/opentaint-utils/opentaint-jvm-util/src/main/kotlin/org/opentaint/jvm/graph/JApplicationGraphImpl.kt:25-29`), and
the catch statement does not kill its local (`JIRStatementForms.build`: a
`catch` gives `StatementSummary.EMPTY`, §26.1); no class initializer `<clinit>` is analysed (no call site, and the entry
points exclude it). The alias analysis is out of scope: Part II does not change it. Its settings (A1: the
interprocedural depth 0, on or off, its time limit) are as configured (`JIRLocalAliasAnalysis.Params`). `aliases()`
(§28.6) has no filter for a static alias base: under A1 such an alias does not hold both before and after a call, so it
does not occur.

---

## 33. Test plan

Implements `validation-plan.md` → Interpreter tests → §7 in implementation terms. Proof first: each test names the Lean theorem or `example` that
it mirrors.

### 33.1 Test classes and the TDD order

In the column "Pins (`interpreter.md`)", a bare § number names a section of `interpreter.md`.

| # | Test class (module, package) | Needs (Part I) | Pins (`interpreter.md`) | Lean |
|---|---|---|---|---|
| 1 | `StatementSummaryReversalTest` (opentaint-dataflow, `bidi.interp`) | `Reference.kt` (`revEdge`) | §4.9 STATEMENTS: touched + targets; A5 identity for a gen-only target; a conjunction gives one edge per literal, each `conjunctive` with `MicroEdge.may` true (F70, THE REVERSAL OF A CONJUNCTION), and a plain edge is never `conjunctive`; no filter; `forward` kept; I14 and A1: no reversed form has `[any-taint]` (the reversed `[any]` literal of a source, also of a conjunctive edge, is `[any]`; a forward `[any-taint]` target is the premise `[any]`), and `MicroEdge.may` of the reversal of `P.$ (T) -> Q.[any] (T)` is true, of the source `P.$ (T) -> Q.[any-taint] (T)` false | `Reverse.Stmt.rev`, `rev_touched`, `revNoId_breaks`, `Stmt.rev_step_iff`, `AnyTaint.Sanity` |
| 2 | `RuleStatementReversalTest` (same) | `Reference.kt` | §4.9 RULE ROLES; end-fact targets pass; `ExitRules.reversed` drops G2; primitive cleaners unchanged; field actions reverse write, cleaner, read; THE TRIGGER OF AN END FACT (F70): the reversed `RuleStatement` keeps its `sinks`, and the forward form of each reversed end-fact edge is an end-fact edge of a sink of `sinks` (two exit sinks with the same end-fact action: one reversed edge, whose forward form names both) | `Reverse.revInstr` |
| 3 | `MicroEdgeBuilderTest` (same) | `Reference.kt`, `ExclusionSet.of` | the write rule: `C.s = x` gives two keep edges with one exclusion each; a weak write keeps the base; A2/A3 alias edges; STAGE and GEN modes; `TypeFilter.and` on one base | `Cases.lean` `storeF` |
| 4 | `CallPlanReversalTest` (same) | `Reference.kt` (`revEdge`, `concat`), `ApManager` | the step table of Part II §23.6; guards and filters go; `PASS_OVER` for alias bases; entry/exit swap; a non-forward plan fails; the zero fact passes over and enters (`FormsReference.run`); forward, `FormsReference.run` with `PlanHooks(guards = true)` on a hand-built plan: `END_FACTS` acts only on the zero fact and gives the layer of the sink that fired at `BOUND`, `ALIASES` skips only a normal candidate identity and keeps demand results from default identity and constructor pass-over (`CurrentAliasGuard.selection_iff`), each result gets `StageKind.identityCandidateOf`, a run from `RETURNED` starts at that point; F70 (§33.3, the third and fourth tests): THE TRIGGER OF AN END FACT: the reversed `END_FACTS` stage has no guard and `trigger` = the sink of its forward guard, and backward `FormsReference.run` calls `PlanHooks.trigger` once with that sink when a requirement on the end-fact target reaches the stage, and the seed items go on from `BOUND`; THE REVERSAL OF A CONJUNCTION (the call `ret = lib(p1, p2)` with the conjunctive source `ContainsMark(arg0, T1) ∧ ContainsMark(arg1, T2) → Result.$ (T)`): the reversed `SOURCES` stage takes the NORMAL requirement `(ret, ., $, T)` to `(p1, ., $, T1)` and `(p2, ., $, T2)`, both in the DEMAND layer (before F70 both normal) | `Reverse.Call.rev`, `bindRev_of_star` |
| 4a | `FormApplierTest` (same) | `Reference.kt`, `ApManager` | the three modes of `FormApplier` over `ReferenceAlgebra` and over a recording algebra: STATEMENT passes an untouched base and kills a touched one; the operand filter acts on the input before every edge, the result filter on each result; STAGE passes nothing by itself and gives the micro edge of each result; GEN adds results, applies no filter and is never a source-seed place; at a source-seed place the seed filter acts before a source edge and the hit is recorded only when a result exists (once per micro edge, before the result goes on); the static exception only with `Place.statementEdge` in run 1; `ReferenceAlgebra.passes` against `ApOps.filter` (Part I §5.5) | `Reverse.Stmt.rev` (the modes), `Statics.genFireB` (the static exception), `Core.filt_keeps` |
| 5 | `JIRStatementFormsTest` (opentaint-jvm-dataflow, `jvm.bidi.interp`; replaces the edge asserts of `JIRStatementSummaryTest`) | `ApManager` | one test per row of §2.2; §2.4 pinned rows; operand vs result filters; read source at `x = C.s` with `ZERO_KEEP`; a read source with a non-`Result` target is a rule error | `Cases.lean` `loadF`, `loadF'`, `storeF` |
| 6 | `JIRRuleFormsTest` (same) | `ApManager` | §4.1 rows, AnyField table; §4.2 negated literal, `Or`, cubes; §5.3 conjunctions (ND sources only); `pass`: a `CopyMark(T, P → Q)` gives the one edge `P.t (T) -> Q.t (T)`; §5.2 one test per mapping row with `<string-bytes>`; D20: a cleaner with a mark literal left gives no step and no request; §4.2 array elements: the alternatives of a call sink, with `SinkRule.alternative` = 0, 1, ... in the cube and array-choice order, the same for two builds; §1.3 the two invariants of a rule position: `[arg0, ".*", ".f"]` and `.*.*` in a source, a sink, a pass rule and a cleaner are each one rule error, logged once, and `pos` does not throw (D29); A RULE ERROR REJECTS THE WHOLE RULE (`valid`, Part II §27.1): the sink `Or(ContainsMark(Argument(0).AnyField.f, T), ContainsMark(Argument(1), T))` gives no `SinkRule` (not a sink on `arg1`), the cleaner `RemoveMark(T, Argument(0)), RemoveAllMarks(ClassStatic(C).AnyField)` gives no step (no clean of `arg0`), a bad position in a NEGATED literal rejects the rule too, and the other rules of the method get their forms; `AnyClassStatic` in `RemoveAllMarks` gives `CleanStep.Clean(Cleaner(S, [], AT_AND_BELOW, all))`, in any other rule element a rule error, also in a condition literal (`ContainsMark(AnyClassStatic, T)`, `ContainsMark(PositionWithAccess(AnyClassStatic, f), T)`); a pass rule with a mark literal is not rejected (`RuleErrors.applied`, D24); §1.4 rows and rule errors (`validation-plan.md` → Interpreter tests → §7.2 items 11, 19, 20; Part II §33.5 items 14, 16); THE TAINT ANNOTATION (I14, D32; `validation-plan.md` → Interpreter tests → §7.2 item 29): `AssignMarkOnAnyAccessor` and `AssignMark` on `PositionWithAccess(Argument(0), AnyField)` give the target `arg0.[any-taint] (T)` with the Empty exclusion (at a call, at the start, at an exit, at a read, and the target of a conjunctive source), `ContainsMarkOnAnyField(Q, T')` gives the premise `Q.[any] (T')`, `CopyMark(T, P → Q.AnyField)` gives `P.$ (T) -> Q.[any] (T)` (a may: `MicroEdge.may`), an end fact on a plain position gives `P.$ (T)` and on an AnyField position `P.[any-taint] (T)` (I14, SI19); an `[any-taint]` or `[any]` target on a class position is a rule error; D33: `CopyAllMarks(Argument(0).AnyField → Result)` and `CopyMark(T, Argument(0).AnyField → Result)` are rule errors, logged once, and give no pass edge (`validation-plan.md` → Interpreter tests → §7.2 item 19) | `Statics.SWF`, `Statics.CexAny`, `NDExact.LitConc`, `AnyTaint.TaintConc`, `AnyTaint.Sanity`, `AnyTaintEx.Vec.source_any_target` |
| 7 | `JIRCallPlanTest` (same) | `ApManager` | §3.1 bindings with filters; §3.3 S and zero; §3.5 constructor (normal identity has no alias; demand results take aliases: SI15); §3.7 two `UNRESOLVED` stages, the identity has no filter, pass rules, default getter; D24: a pass rule (`CopyAllMarks` or `CopyMark`) with a mark literal is logged once (`RuleErrors.applied`, not rejected) and gives its plain pass edge without the literal (a `CopyMark(T, P → Q)` with a literal on another position gives `P.$ (T) -> Q.$ (T)`), and the `UNRESOLVED` stages have no conjunctive edge; `UnresolvedCallObserver` calls the tracker with `ruleApplied`; §3.8 aliases + `MemoryEffect`; §3.9 prescan lambdas (D19: no `UNRESOLVED` stage when the prescan knows a lambda); the forward stage table (Part II §33.5 items 10, 14, 17); `passReads` holds the from-bases of the rule-set pass rules and not the `this` of a default getter rule (the tracker gets `ruleApplied = false` for a library getter with no model); §3.6, D28: a call to a native method of a project class has no `Callees` stage and has the `UNRESOLVED` stages, and a call with an empty and a non-empty callee has only the non-empty one (G12); §3.7: an empty resolution result (a stub `JIRCallResolver` that gives `emptyList()`) gives the `UNRESOLVED` stages and no `Callees` stage; Part II §28.1 a non-immediate call lhs fails (`error`), as today; §5.2, D23 THE REWRITER SELECTION (`rewriterSteps`; `validation-plan.md` → Interpreter tests → §7.2 item 26): at a call with a user source `if IsConstant(...) && ContainsMark(...)` (selected), a user cleaner `if ContainsMark(...)` (not selected) and an unconditional user cleaner (selected), the `Actions(RETURNED, REWRITTEN, …)` stage has exactly the cleaners of the two selected rules; D34: a selected source with the target `PositionWithAccess(Result, AnyField)` gives the cleaner `Cleaner(ret, [], AT_AND_BELOW, T)`, a cleaner `RemoveMark(T, Argument(0).AnyField)` keeps `(arg0, [], BELOW, T)`; a rule with no `UserDefinedRuleInfo` is not selected; a user source `AssignMark(T, Result) if ContainsMark(Argument(0).AnyField.f, U)` (a rule error) gives no source edge AND no rewriter action (`valid`, §1.3). The Spring checks of D27 are test 13 | `Reverse.BindTargetsStar`, `Backward.NoZeroBack` |
| 8 | `JIRBoundaryFormsTest` (same) | `ApManager` | §4.3 context filter; §4.7 `globalStateDrop`, `entryMarks` (the Spring DTO entry rule gives the mark `m` once for its two actions), `entryMarkRemoval` gives the entry marks on `this` and `arg(i)` and null on another base and at the exceptional exit; THE ENTRY-MARK REMOVAL (§4.7 step 4, D35; `validation-plan.md` → Interpreter tests → §7.2 item 35): `ops.withoutMarks` with `entryMarkRemoval(arg0)` on the zero-premise exit fact `{(arg0, [], $, m), (arg0, [], [any-taint], {name}, m), (arg0, [f], $, m), (arg0, [g], $, U)}` leaves only `(arg0, [g], $, U)` (every leaf with `m`, at any depth and with both tails, the `[any-taint]` leaf with its exclusion), and equals `clean` with `(arg0, [], atAndBelow, m)`; §4.7 at `JMethodExitExceptionalInst`: the exit sources and sinks with `Result` read as `exc`, `globalStateDrop = false`, no `entryMarks`; D22, D26: an unconditional exit sink has `ZERO_PATTERN` and an unconditional exit source has its zero edge at both exits; §4.7 step 3: `globalStateDrop` is true at a normal exit with a plain or a conjunctive exit sink (the zero-premise scope of D30 is a rule of the core: `ExitRulesTest`, `analyzer-impl.md` §9.1); §4.7 step 1, D31 (`validation-plan.md` → Interpreter tests → §7.2 item 28): the exit source `AssignMark(S2, ClassStatic(C)) if ContainsMark(ClassStatic(C), S1) && ContainsMark(Result, T)` gives one `ConjunctiveEdge` in `rules.summary.conjunctions` with the target `S.<C>.$ (S2)`, the keep edges `S.* → S.*` and `ret.* → ret.*`, and no rule error; THE BOUNDARY RULE STATEMENTS (`interpreter.md` §4.3, §4.7): the entry rules touch the zero base and each base with a context filter (its identity edge and filter), the exit rules the zero base and each literal base (its identity edge); a source target is not touched; `FormsReference.apply` in STATEMENT mode on the exit fact `arg(0).$ (T)` under `AssignMark(U, Result) if ContainsMark(Argument(0), T)` gives `arg(0).$ (T)` and `ret.$ (U)`; exit nodes (the two boundary exits); a method with no boundary instructions fails the `JIRMethodEntry` assert, and a classpath without the two lambda features fails the `JIRMethodEntries` assert (Part II §31.2); exit wiring of a loop that never returns | `Backward.ExitReach`, `Backward.ZeroKept` |
| 9 | `JIRFormsContractTest` (same) | `ApManager` | every method of the samples jar: I6, I7 (both `$` clauses), I11 (a)–(d), (f), I12 (a), (b), (c), (e), I14 (only a source edge has the target `[any-taint]`, with concrete marks and the Empty exclusion: no `UNRESOLVED` stage and no binding has it; no forward premise `[any-taint]`; no pass rule has an `AnyField` premise, D33; no binding has an any target) over `allForwardForms` (Part II §33.4; I11 (e) is test 8, I12 (d) a check of the run config) | `Backward.StmtsMarkRev`, `Reverse.BindTargetsStar`, `Backward.NoZeroBack`, `Backward.ZeroKept`, `Statics.SWF`, `Invariant.no_univ_star` |
| 10 | `JIRTypeFiltersTest` (same) | `ApOps.filter` | §5.1 rows; `*`/`[any]`/`[any-taint]` tails kept; the mark policy on `int` and `Integer`, a `%%primitive%%` mark kept; below `[e]`: on `int[]` and `Integer[]` the policy drops `a.[e].$ (T)` and keeps `a.[e].$ (T%%primitive%%)`, on `int[][]` level 2, on `Object[]` and `String[]` no policy; a leaf off the `[e]` chain stays; prefix-closed | `Exact.FiltValid`, `Core.filt_keeps` |
| 11 | `JIRStatementEffectTest` (same) | `ApOps` | the effect table of §2.4 and the table of `ap.md` §4.2; for each statement form and each mode, the core result (`Facts`) `agrees` with `FormsReference` (Part II §23.8) on FLOW, TAINT and `Reach` inputs, with the premise of each input (so the static exception agrees: `x = C.s` on `S.*` with the premise `S.*` in run 1 gives the position request `[<C>, s]` and no fact on `x` in both); a synthetic `a.f = b.g` (as the synthetic statement of Part II §33.2) fails the `to !is MemoryAccess` check (Part II §26.1), as today; `ops.withoutMarks` with `entryMarkRemoval(b)` removes `b.$ (T)`, `b.f.$ (T)`, `b.[any-taint] (T)` and `b.[any-taint]/{f} (T)` (every depth, both tails: D35) and keeps `b.g.$ (U)` in its layer; the zero binding: the `BIND_IN` stage of a `JIRCallPlanBuilder` plan, applied by `ops.applyEdge` (STAGE mode), gives `Reach.NORMAL` from `Reach.NORMAL` and `Reach.DEMAND` from `Reach.DEMAND`, and `FormsReference.run` agrees per path (the zero fact reaches `BOUND` and the entry of the callee) | `Cases.lean` examples at lines 61–89 |
| 12 | the analysis tests of `validation-plan.md` → Interpreter tests → §7.1 | Part I and `analyzer-impl.md` | end to end, through phase 3 (`analyzer-impl.md` §8.1) | — |
| 13 | `SpringDispatcherFormsTest` (module `core`, `core/src/test/kotlin/org/opentaint/jvm/sast/project/`, beside the Spring tests: `opentaint-jvm-dataflow` cannot see `SpringWebProject` and `SpringRuleProvider`) | `ApManager`, `JIRInterpreter` over a Spring project context | D27, Part II §31.3 (`validation-plan.md` → Interpreter tests → §7.2 item 20): the generated `__dispatch__` has the saves of the registry fields, the call `__cleanup__()` and the restores, and `loopEnd` is the first save; the plan of `__cleanup__()` (`SpringRuleProvider` over the rule set, `fact = null`) has the one step `Cleaner(S, [], AT_AND_BELOW, all)`; END TO END (through phase 3) with two dispatched controllers: a static field that the first one taints is clean in the second one, and a registry field keeps its fact across `__cleanup__()` | `Statics.SWF` (part `clean`) |
| 14 | `AnyTaintFormsTest` (opentaint-jvm-dataflow, `jvm.bidi.interp`) | `ApManager`, `ApOps`, `FormsReference` | THE TAIL `[any-taint]` IN THE FORMS (`validation-plan.md` → Interpreter tests → §7.2 items 29, 30, 34; I14, D32): the source and the pass rule of item 29 (`AssignMark(T, PositionWithAccess(Q, AnyField)) if ContainsMark(P, T)` and `CopyMark(T, P → Q.AnyField)`) give the forms `P.$ (T) -> Q.[any-taint] (T)` and `P.$ (T) -> Q.[any] (T)` (`may`); `JIRStatementEffectTest`-style on `ApOps`: the source result is normal, the pass result demand; the reversed forms (A1): the source `Q.[any] (T') -> P.$ (T)` reverses to `P.$ (T) -> Q.[any] (T')`, the pass rule `CopyMark(T, P → Q.AnyField)` to `Q.[any] (T) -> P.$ (T)` with `may`, and applied backward to the normal requirement `(Q, ., $, T)` it gives `(P, ., $, T)` in the DEMAND layer (RS-1), while the reversed source `zero.$ -> P.[any-taint] (T)` takes `(P, .f, $, T)` to the zero fact in the normal layer; `seedPatterns` of `sinkAny(x)` (`ContainsMarkOnAnyField(Argument(0), T)`) is `(arg0, [], [any], T)`, and `targetTree` of it is a DEMAND tree; the §2.4 rows of item 30 (the exclusion, A2): `x = y.f` on `(y, ., [any-taint], E, T)` gives `(x, ., [any-taint], {}, T)` normal when `f ∉ E` and nothing when `f ∈ E`, `y.f = x` gives `(y, ., [any-taint], E ∪ {f}, T)` NORMAL; the source `dto.f.g = srcAny()` under the field limit 1 is cut to `(dto, .f, [any], T)` demand with no exclusion | `AnyTaint.Sanity`, `AnyTaintExCases2.PassRule.source_vs_pass`, `AnyTaintCases.Cut.cut_transfer`, `AnyTaintEx.Vec.setter_keep`, `read_admitted`, `read_excluded`, `cut_drops` |
| 15 | the analysis tests of `validation-plan.md` → Interpreter tests → §7.2 items 30 to 33 and 35 (module `core`, through phase 3, `analyzer-impl.md` §8.1) | Part I and `analyzer-impl.md` | program G as a Spring controller with a DTO argument that passes a getter value to a sink: a CONFIRMED entry after run 3 (DEMAND after run 1); program C (the sink in the callee) CONFIRMED after run 3, also after a setter call on the DTO; program I (`x = id(dto)`, an identity callee): CONFIRMED in run 1, no DEMAND entry; the programs of item 30: S and SD (a setter, also one call deeper: `sink(dto.name)` no entry, `sink(dto.email)` CONFIRMED in run 1), B (run 1: `sink(d.name)` no entry, `sinkAny(e)` CONFIRMED; the iteration stops after run 1), `mk` (a factory with a setter: `sink(r.name)` no entry, `sink(r.email)` CONFIRMED in run 1), R (a read through the written field: no entry); X AS JIR (`x.f.g = c` is `t = x.f; t.g = c`: the weak alias write, gap G7, keeps `x` whole): `sink(x.f.g)` is the documented CONFIRMED false positive of the alias gap (`ap.md` §11.1), `sink(x.f.h)` and `sink(x.k)` CONFIRMED (the one-statement X with two results is Part I §8 test 23); THE CUT ON THE JVM (`L = 1`, a deeper write: the deep source of test 14, `dto.f.g = srcAny()` through the alias `dto.f` of the written local): `(dto, .f, [any], T)` in the demand layer, so a sink that reads `dto.f` is a DEMAND entry (`L = 0` is Part I §8 test 23 only: no run has it, `ApMode`); the conjunction of item 33 (an `[any-taint]` input that overlaps a `$` literal) CONFIRMED in run 1, and with the input `[any-taint]/{f}` and the literal on `Argument(0).f` no entry; item 34: the reversed pass rule keeps its vulnerability a DEMAND entry in run 3; item 35 (D35; the form check is test 8): an entry point `ep(dto)` whose entry-point source marks `dto` with `m` in the Spring DTO shape (`AssignMark(m, arg0)`, `AssignMark(m, PositionWithAccess(arg0, AnyField))`), called by a method that sinks `dto.f` after the call: no entry with `m` from that sink (every leaf with the entry mark goes at the normal exit of `ep`, the `[any-taint]` leaf too) | `AnyTaintExCases2.G.run3_confirmed`, `G.run1_not_confirmed`, `C.run3_confirmed`, `I.run1_confirmed`, `I.run1_no_demand`, `AnyTaintExCases.S.run1_name_not_reported`, `S.run1_email_confirmed`, `SD.run1_email_confirmed`, `B.run1_name_not_reported`, `B.run1_anyE_confirmed`, `R.y_not_reported`, `R.z_confirmed`, `AnyTaintEx.Vec.summary_ann` (`mk`), `AnyTaintCases.Cut.limitF_cut_demand`, `AnyTaintEx.Vec.cut_drops`, `AnyTaintND.Example.confirmed`; X as JIR and item 35 have no Lean program (gap G7, `interpreter.md` A3; D35) |

Order: by the "Needs" column. 1 → 2 → 3 → 4 → 4a (`Reference.kt` and `ApManager` only, no JIR) → 5 → 6 → 7 → 8 → 9
(JIR and `ApManager`) → 10 → 11 → 14 (Part I `ApOps`) → 12 → 13 → 15.

F74 adds these cases after the corresponding existing test class. The primitive `CleanerTest` and CL fixtures
still test `ops.clean` directly. They do not give the expected results of a named field action.

| Test class | Field-action checks | Lean scope |
|---|---|---|
| `MicroEdgeBuilderTest`, `CallPlanReversalTest` | Preserve the read identity and strong-write keep edges. Use a fresh temporary per cached action. The reversal shares that temporary and runs reversed write, root cleaner, reversed read. Reverse the order of two actions. Project the temporary away before the action returns. | `CleanerLowering.loweredWith_iff`, `lowerWitnessWith`, `backwardKeepWitnessWith`; all three reaches and one or all marks, one field, no aliases. |
| `JIRRuleFormsTest`, `JIRTypeFiltersTest` | A named field gives `CleanStep.Field` in the cleaners and rewriter `Actions` stages. AnyField changes the reach before lowering. Check root operand and final field-value filters. For `Root.child.value`, do not filter Root as Child; for a primitive value, apply its mark policy on the temporary. A nested String field also gives its `<string-bytes>` action. A static String field resolves through its field accessor and keeps the static cleaner forms. Static and whole-base mappings retain their existing forms. | Ordinary filter contract `Core.filt_keeps`; the JIR type mapping is an implementation check. |
| `AnyTaintFormsTest` | On normal `(x, [], [any-taint], {}, T)`, all reaches keep normal `(x, [], [any-taint], {f}, T)`. EXACT also gives demand `(x, [f], [any], T)`; BELOW also gives normal `(x, [f], $, T)`; AT_AND_BELOW gives only the keep result. An excluded field produces no temporary result. | `FieldCleanerX.exact_vector`, `below_vector`, `atAndBelow_vector`; the checked vectors use limit 3 and an empty input exclusion. |
| `JIRFormsContractTest` | Include both field statements and the temporary root cleaner in the contract scan. Reject a temporary as a summary base and in language position resolution, serialization and traces. | F74 construction condition, `ap.md` S16. |
| Analyzer regression fixture | Use the historical lowered-field program and tables in [`proof-history.md`](proof-history.md) with limits 1, 2, 3. Compare facts, demands, records and source hits with its finite-program reference. In the F72 fixture the backward keep branch retains `*`, the caller reaches source T, and run 3 reports normal `r.f.n.$ (T)`. | `ReviewFieldCleaner.exact1`, `exact2`, `exact3`, `demand3_all_exact`, `hits_exact`, `lowered_pipeline_preserves`. This is a finite-program F72 fixture; the general F75 X/driver proofs remain pending. |

`FormsFixtures` (test sources of `opentaint-dataflow`, `bidi.interp`): tests 1 to 4a have no JIR, because
`opentaint-dataflow/build.gradle.kts` has only `opentaint_ir_api_common`. `testCall()` gives a fake `CommonInst` call
statement with a location and a method, `testSink()` a fake `CommonTaintConfigurationSink` (the `rule` of a hand-made
`SinkRule`), and `testMethodKey(name)` the key `MethodEntryPoint(EmptyMethodContext, entry)`
of a fake `CommonMethod` with a fake entry statement, as the fake call statements of `ApFixtures` (`analyzer-impl.md`
§9.1). The JIR test kit (`JIRInterpreterTestKit`, §33.2) is a fixture of `opentaint-jvm-dataflow` and serves tests 5
to 11 only.

### 33.2 Example: the pinned rows of `interpreter.md` §2.4

`testInterpreter`, `testKey` and `TestRules` are helpers of `JIRInterpreterTestKit` (test sources): a `JIRMethodEntries`
over `cp` with `JApplicationSingleExitGraph`, a `TaintRulesProvider` made from lists, and the key
`MethodEntryPoint(EmptyMethodContext, entry statement)` of the method of a statement. `cp` is the classpath of
`BasicTestUtils` (`UnknownClasses`, `JMethodBoundaryInstFeature`: the boundary feature of §31.2) with the two lambda
features added (`JIRMethodEntries` asserts them, §31.2; the lambda tests of `validation-plan.md` → Interpreter tests → §7.2 item 17, §33.5, read
them).

```kotlin
class JIRStatementFormsTest : BasicTestUtils() {
    private val cls = "sample.sequent.StatementSummarySample"          // the sample of JIRStatementSummaryTest
    private val f = FieldAccessor(cls, "f", "java.lang.Object")
    private val next = FieldAccessor(cls, "next", cls)
    private val interp by lazy { testInterpreter(cp, rules = TestRules.EMPTY) }   // JIRMethodEntries over cp, no alias analysis

    private fun assigns(m: String) = findMethod(cls, m).instList.instructions.filterIsInstance<JIRAssignInst>()
    private fun base(v: JIRValue) = accessPathBase(v)!!
    private fun star(b: AccessPathBase, vararg p: Accessor) = starSide(b, p.map { interp.idx(it) })     // DD6
    private fun e(from: PathFact, to: PathFact, ex: Accessor? = null) =
        PathEdge(from, to, ex?.let { ExclusionSet.of(interp.idx(it)) } ?: ExclusionSet.Empty)      // ExclusionSet.of(a): Part I §3.2
    private fun summary(s: JIRInst) = interp.statementSummary(testKey(s), s)
    private fun edges(s: JIRInst) = summary(s).edges.map { it.edge }.toSet()

    @Test // §2.4 row 1, D1; Lean Cases.lean `loadF'`
    fun `x = y_f keeps y whole and reads y_f`() {
        val s = assigns("fieldRead").first { it.rhv is JIRFieldRef }
        val x = base(s.lhv); val y = base((s.rhv as JIRFieldRef).instance!!)
        assertEquals(setOf(e(star(y), star(y)), e(star(y, f), star(x))), edges(s))
        assertEquals(setOf(x, y), summary(s).touched)
        assertTrue(x in summary(s).resultFilters && y in summary(s).typeFilters)      // §2.1 steps 3 and 5
    }

    @Test // §2.4 rows 2 and 4; I3; Lean Cases.lean `storeF`
    fun `y_f = x and a_f = a are strong writes`() {
        val w = assigns("fieldWrite").first { it.lhv is JIRFieldRef }
        val y = base((w.lhv as JIRFieldRef).instance!!); val x = base(w.rhv as JIRValue)
        assertEquals(setOf(e(star(y), star(y), f), e(star(x), star(x)), e(star(x), star(y, f))), edges(w))
        val sw = assigns("selfWrite").first { it.lhv is JIRFieldRef }
        val a = base(sw.rhv as JIRValue)
        assertEquals(setOf(e(star(a), star(a), f), e(star(a), star(a, f))), edges(sw))
    }

    @Test // §2.4 row 5, D2: no refine-only edge
    fun `x = x_next has only the read edge`() {
        val read = assigns("selfRead").first { (it.rhv as? JIRFieldRef)?.field?.name == "next" }
        val rhv = read.rhv as JIRFieldRef
        val s = JIRAssignInst(read.location, rhv.instance!!, rhv)                        // as JIRStatementSummaryTest
        val x = base(rhv.instance!!)
        // A synthetic statement has the index of `read`: build it with no cache, or the cache gives the form of `read`.
        val summary = JIRStatementForms(interp, interp.entries[read.location.method]).build(s)
        assertEquals(setOf(e(star(x, next), star(x))), summary.edges.map { it.edge }.toSet())
    }

    @Test // §2.2 static write; strongKeep of a two-accessor path
    fun `C_s = x keeps S except the class and the class except the field`() {
        val s = assigns("staticWrite").first { it.lhv is JIRFieldRef }
        val x = base(s.rhv as JIRValue)
        val c = ClassStaticAccessor(cls); val sf = FieldAccessor(cls, "sField", "java.lang.Object")
        val st = AccessPathBase.ClassStatic
        assertEquals(setOf(e(star(st), star(st), c), e(star(st, c), star(st, c), sf),
            e(star(x), star(x)), e(star(x), star(st, c, sf))), edges(s))
    }
}
```

### 33.3 Example: the reversed call plan against the backward step table

```kotlin
class CallPlanReversalTest {
    private val manager = ApManager(Cancellation())
    private val r = AccessPathBase.LocalVar(1); private val a = AccessPathBase.LocalVar(2); private val b = AccessPathBase.LocalVar(3)
    private val S = AccessPathBase.ClassStatic; private val ret = AccessPathBase.Return; private val arg0 = AccessPathBase.Argument(0)
    private val Z = AccessPathBase.Zero
    private val f: AccessorIdx = manager.accessors.index(FieldAccessor("C", "f", "java.lang.Object"))   // DD6
    private val T = manager.marks.mark("T")                                                              // DD7
    private val m = testMethodKey("m")

    /** `r = m(a)`; `m` is a source on `Result`; `b.f` is an alias of `r` through the call. */
    private fun forward(): CallPlan {
        fun stage(vararg es: PathEdge) = MicroEdgeBuilder().apply { es.forEach { edge(it) } }.buildStage()
        val src = PathEdge(ZERO_FACT, PathFact(ret, emptyList(), Tail.EXACT, MarkSlot.Concrete(T)), ExclusionSet.Empty)
        return CallPlan(setOf(S, a, r), listOf(
            CallStage.Edges(BEFORE, BOUND, BIND_IN, stage(keepEdge(S), starEdge(Z, listOf(), Z, listOf()), starEdge(a, listOf(), arg0, listOf()))),
            CallStage.Edges(BOUND, REWRITTEN, SOURCES, stage(ZERO_KEEP, src)),
            CallStage.Actions(BOUND, ADDED, emptyList()),
            CallStage.Callees(ADDED, RETURNED, listOf(m)),
            CallStage.Actions(RETURNED, REWRITTEN, emptyList()),
            CallStage.Edges(REWRITTEN, AFTER, BIND_BACK, stage(keepEdge(S), starEdge(arg0, listOf(), a, listOf()), starEdge(ret, listOf(), r, listOf()))),
            CallStage.Edges(REWRITTEN, AFTER, ALIASES, stage(starEdge(ret, listOf(), b, listOf(f))), Guard.MemoryEffect),
        ), sinks = emptyList(), entry = BEFORE, exit = AFTER)
    }

    @Test // AC4; CurrentAliasGuard: candidate identity skips aliases only in the normal layer
    fun `demand default identity and constructor pass-over reach aliases`() {
        fun stage(vararg es: PathEdge) = MicroEdgeBuilder().apply { es.forEach { edge(it) } }.buildStage()
        val fact = PathFact(arg0, emptyList(), Tail.EXACT, MarkSlot.Concrete(T))
        val premise = setOf(Pattern(fact, ExclusionSet.Empty))
        val ref = FormsReference(ApOps(manager))
        val at = Place(testCall(), statementEdge = false, sources = false)
        for (kind in listOf(UNRESOLVED, CONSTRUCTOR)) {
            val plan = CallPlan(setOf(arg0), listOf(
                CallStage.Edges(ADDED, REWRITTEN, kind, stage(keepEdge(arg0))),
                CallStage.Edges(REWRITTEN, AFTER, ALIASES,
                    stage(starEdge(arg0, listOf(), b, listOf(f))), Guard.MemoryEffect),
            ), sinks = emptyList(), entry = ADDED, exit = AFTER)
            for (demand in listOf(false, true)) {
                val input = PlanItem(premise, Conclusion(fact, ExclusionSet.Empty, demand), identityCandidate = null)
                val trace = ref.run(plan, listOf(input), at, PlanHooks(guards = true))
                val alias = Conclusion(fact.copy(base = b, path = listOf(f)), ExclusionSet.Empty, demand)
                assertEquals(demand, trace.any { (p, i) -> p == AFTER && i.c == alias })
            }
        }
        // A cleaner or rewriter keeps the candidate flag; its current DEMAND layer still admits aliases.
        assertTrue(Guard.MemoryEffect.admits(true, Layer.DEMAND))
        assertFalse(Guard.MemoryEffect.admits(true, Layer.NORMAL))
    }

    @Test // analyzer-core.md §4.5 step table; Lean Reverse.Call.rev
    fun `the reversed plan has the stages of the backward call order`() {
        val rev = forward().reversed()
        assertEquals(AFTER to BEFORE, rev.entry to rev.exit)                              // steps 1 and 9
        assertEquals(setOf(S, a, r, b), rev.touched)                                     // step 1: + the alias base
        assertEquals(setOf(
            Triple(AFTER, REWRITTEN, BIND_BACK), Triple(AFTER, REWRITTEN, ALIASES),       // step 2
            Triple(AFTER, BEFORE, PASS_OVER),                                             // step 1, A5
            Triple(REWRITTEN, BOUND, SOURCES),                                            // step 3
            Triple(REWRITTEN, RETURNED, null),                                            // step 4 (Actions: rewriter)
            Triple(RETURNED, ADDED, null),                                                // step 5.1 (Callees)
            Triple(ADDED, BOUND, null),                                                   // step 6 (Actions: cleaners)
            Triple(BOUND, BEFORE, BIND_IN),                                               // step 8
        ), rev.stages.map { Triple(it.from, it.to, (it as? CallStage.Edges)?.kind) }.toSet())
        val edges = rev.stages.filterIsInstance<CallStage.Edges>()
        assertTrue(edges.all { it.guard == null && it.summary.typeFilters.isEmpty() && it.summary.resultFilters.isEmpty() })
        assertTrue(edges.single { it.kind == ALIASES }.summary.edges.any { it.edge == starEdge(b, listOf(f), ret, listOf()) })
    }

    @Test // the reversed plan run on requirements (FormsReference.run); the callee maps ret to arg0
    fun `a requirement on r reaches the source and the argument, and b passes over`() {
        val rev = forward().reversed()
        val hooks = PlanHooks(callees = { _, i ->                                         // the callee maps ret to arg0
            if (i.c.fact.base == ret) listOf(i.copy(c = i.c.copy(fact = i.c.fact.copy(base = arg0)))) else emptyList() })
        fun item(c: Conclusion) = listOf(PlanItem(emptySet(), c, identityCandidate = null))
        val req = Conclusion(PathFact(r, emptyList(), Tail.EXACT, MarkSlot.Concrete(T)), ExclusionSet.Empty, demand = false)
        val ref = FormsReference(ApOps(manager))
        val at = Place(testCall(), statementEdge = false, sources = false)            // a fake call statement (FormsFixtures)
        val trace = ref.run(rev, item(req), at, hooks)
        assertTrue(trace.any { (p, i) -> p == BOUND && i.c.fact.base == Z })             // step 3: the zero demand
        assertTrue(trace.any { (p, i) -> p == BEFORE && i.c.fact.base == a })             // steps 5.1, 6, 8
        val onB = Conclusion(PathFact(b, listOf(f), Tail.EXACT, MarkSlot.Concrete(T)), ExclusionSet.Empty, false)
        val tb = ref.run(rev, item(onB), at, hooks)
        assertTrue(tb.any { (p, i) -> p == BEFORE && i.c.fact.base == b })                // PASS_OVER (A5)
        assertTrue(tb.any { (p, i) -> p == REWRITTEN && i.c.fact.base == ret })           // reversed alias edge (AC5)
    }

    private val backward = FormsReference(ApOps(manager),
        ReferenceAlgebra(ApMode(run1 = false, Direction.BACKWARD, fieldLimit = Int.MAX_VALUE), manager = manager))
    private val call = Place(testCall(), statementEdge = false, sources = false)
    private fun conc(b: AccessPathBase, m: TaintMark) = PathFact(b, emptyList(), Tail.EXACT, MarkSlot.Concrete(m))
    private fun stage(vararg es: PathEdge) = MicroEdgeBuilder().apply { es.forEach { edge(it) } }.buildStage()

    /** F70, THE REVERSAL OF A CONJUNCTION (ap.md §9.2; analyzer-core.md §4.3), a regression test. `M(p1, p2) { r =
     *  lib(p1, p2); return r; }`, `lib`: the conjunctive source `ContainsMark(arg0, T1) ∧ ContainsMark(arg1, T2) ->
     *  Result.$ (T)`. A requirement that reaches one literal is not a converse flow of the conjunction, so every result is
     *  DEMAND: the backward summaries of `M` are no record (R1), and the hand-off
     *  gives them to the next forward run (case 3). Before F70 both results were NORMAL: the backward summary
     *  `(ret, ., $, T) -> (p1, ., $, T1)` of `M` was a record, forward run 3 crossed `M` by its reversal, a record of one
     *  literal, and `r2 = M(a, c); sinkT(r2)` was a false CONFIRMED (`c` has no `T2`; the analysis test:
     *  analyzer-impl.md §9.1). */
    @Test
    fun `the reversal of a conjunctive source gives demand results only`() {
        val p1 = AccessPathBase.LocalVar(4); val p2 = AccessPathBase.LocalVar(5); val arg1 = AccessPathBase.Argument(1)
        val t1 = manager.marks.mark("T1"); val t2 = manager.marks.mark("T2")
        val lib = ConjunctiveEdge(listOf(Pattern(conc(arg0, t1), ExclusionSet.Empty), Pattern(conc(arg1, t2), ExclusionSet.Empty)),
            conc(ret, T))
        val plan = CallPlan(setOf(p1, p2, r), listOf(
            CallStage.Edges(BEFORE, BOUND, BIND_IN, stage(starEdge(Z, listOf(), Z, listOf()),
                starEdge(p1, listOf(), arg0, listOf()), starEdge(p2, listOf(), arg1, listOf()))),
            CallStage.Edges(BOUND, REWRITTEN, SOURCES, MicroEdgeBuilder().apply { keepZero(); conjunction(lib) }.buildStage()),
            CallStage.Edges(REWRITTEN, AFTER, BIND_BACK, stage(starEdge(arg0, listOf(), p1, listOf()),
                starEdge(arg1, listOf(), p2, listOf()), starEdge(ret, listOf(), r, listOf()))),
        ), sinks = emptyList(), entry = BEFORE, exit = AFTER)
        val rev = plan.reversed()
        val lits = rev.stages.filterIsInstance<CallStage.Edges>().single { it.kind == SOURCES }.summary.edges.filter { it.conjunctive }
        assertEquals(2, lits.size)
        assertTrue(lits.all { it.may })                                                   // an OR, every result demand
        val req = Conclusion(conc(r, T), ExclusionSet.Empty, demand = false)               // the NORMAL requirement (r, ., $, T)
        val trace = backward.run(rev, listOf(PlanItem(emptySet(), req, identityCandidate = null)), call)
        val atEntry = trace.filter { (p, i) -> p == BEFORE && i.c.fact.base != Z }.map { it.second.c }
        assertEquals(setOf(conc(p1, t1), conc(p2, t2)), atEntry.map { it.fact }.toSet())
        assertTrue(atEntry.all { it.demand })                                              // before F70: both NORMAL
    }

    /** F70, THE TRIGGER OF AN END FACT (ap.md §9.2; analyzer-core.md §4.5). `r = sinkCall(a)`, the sink
     *  `ContainsMark(arg0, U)` with the end-fact action `AssignMark(T, Result)`. The end fact exists only after the sink
     *  triggers, so a requirement on it demands the trigger: the reversed stage fires the sink seeds of its sink. */
    @Test
    fun `a reversed end-fact stage keeps its sink and fires its seeds`() {
        val u = manager.marks.mark("U")
        val end = PathEdge(ZERO_FACT, conc(ret, T), ExclusionSet.Empty)
        val sk = SinkRule(testSink(), alternative = 0, patterns = listOf(Pattern(conc(arg0, u), ExclusionSet.Empty)),
            endFacts = listOf(MicroEdge.of(end)))
        val plan = CallPlan(setOf(a, r), listOf(
            CallStage.Edges(BEFORE, BOUND, BIND_IN, stage(starEdge(Z, listOf(), Z, listOf()), starEdge(a, listOf(), arg0, listOf()))),
            CallStage.Edges(BOUND, REWRITTEN, END_FACTS, stage(end), Guard.SinkTriggered(sk)),
            CallStage.Edges(REWRITTEN, AFTER, BIND_BACK, stage(starEdge(arg0, listOf(), a, listOf()), starEdge(ret, listOf(), r, listOf()))),
        ), sinks = listOf(sk), entry = BEFORE, exit = AFTER)
        val rev = plan.reversed()
        val revEnd = rev.stages.filterIsInstance<CallStage.Edges>().single { it.kind == END_FACTS }
        assertTrue(revEnd.guard == null && revEnd.trigger === sk)                          // no guard; the sink of the guard
        val fired = ArrayList<SinkRule>()
        val seed = PlanItem(setOf(ZERO_PATTERN), Conclusion(sk.seedPatterns().single().fact, ExclusionSet.Empty, demand = false), null)
        val hooks = PlanHooks(trigger = { s -> fired += s; listOf(seed) })                 // the closure: one seed per pattern
        val req = Conclusion(conc(r, T), ExclusionSet.Empty, demand = false)               // a requirement on the end fact
        val trace = backward.run(rev, listOf(PlanItem(emptySet(), req, identityCandidate = null)), call, hooks)
        assertEquals(listOf(sk), fired)                                                    // once
        assertTrue(trace.any { (p, i) -> p == BOUND && i.c.fact.base == Z })              // the zero fact, as before F70
        assertTrue(trace.any { (p, i) -> p == BEFORE && i.c.fact == conc(a, u) })         // the seed demands the trigger in the caller
    }
}
```

### 33.4 Contract test sketch (`JIRFormsContractTest`; I14 added for F69)

```kotlin
@TestFactory
fun `every form of every sample method meets I6, I7, I11, I12 and I14`() = sampleMethods().map { method ->
    DynamicTest.dynamicTest(method.toString()) {
        val key = testKey(method)
        val forms = allForwardForms(interp, key)                                    // statements, plans, entry/exit rules
        for (e in forms.statementEdges()) {
            assertTrue(revEdge(e) != null)                                            // I11 (b), Backward.StmtsMarkRev
            assertTrue(e.from.tail != Tail.EXACT || e.from.mark is MarkSlot.Concrete) // I7, S8: a `$` premise
            assertTrue(e.to.tail != Tail.EXACT || e.from.mark is MarkSlot.Concrete)   // I7, S8: a `$` target
            assertTrue(e.from.tail != Tail.EXACT || e.to.tail != Tail.STAR)           // I7, S8
            assertTrue(e.to.mark !is MarkSlot.Concrete || e.from.mark is MarkSlot.Concrete)  // I6, S7
        }
        for (e in forms.bindings()) assertEquals(MarkSlot.STAR, e.to.mark)          // I11 (a), Reverse.BindTargetsStar
        assertTrue(forms.bindings().none { it.to.tail.isAny })                      // I11 (a): no any target, AnyTaint.BindNoAny
        for (me in forms.microEdges()) {                                            // I14, ap.md S15, W8 (a)
            val e = me.edge
            assertTrue(e.from.tail != Tail.ANY_TAINT)                               // no forward `[any-taint]` premise
            assertTrue(e.to.tail != Tail.ANY_TAINT ||                               // a source target: concrete marks (AnyTaint.TaintConc)
                (e.to.mark is MarkSlot.Concrete && e.from.mark is MarkSlot.Concrete && e.toExclusion == ExclusionSet.Empty))
        }
        val pass = pathEdges(forms.stages(StageKind.UNRESOLVED))                    // the default identity and the pass rules
        assertTrue(pass.none { it.to.tail == Tail.ANY_TAINT })                      // a pass rule is a may (D18)
        assertTrue(pass.none { it.from.tail.isAny })                                // D33: no AnyField premise
        assertTrue(forms.bindBack().none { it.to.base == AccessPathBase.Zero })     // I11 (c), Backward.NoZeroBack
        // I11 (d) is about statements: the STATEMENT-mode summaries and the rule statement of a call (SOURCES). The
        // BIND_IN and END_FACTS stages touch zero with no keep edge, as they must (Part II §23.3).
        assertTrue((forms.statementModeSummaries() + forms.stages(StageKind.SOURCES))
            .filter { AccessPathBase.Zero in it.touched }.all { ZERO_KEEP in it.edges.map { e -> e.edge } })   // I11 (d)
        assertTrue(forms.cleaners().none { it.base == AccessPathBase.Zero })        // I11 (d); also Cleaner.init (Part I §6)
        assertTrue(forms.sinkPatterns().all { it.fact.tail != Tail.STAR })          // I11 (f)
        assertTrue(forms.staticWellFormed())                                        // I12 (a), (b), (c), (e), Statics.SWF
    }
}
```

The helpers (test sources, beside the test). `allForwardForms` lists the forward forms of ONE method key: the
statement summary of every non-call instruction, the call plan of every call instruction (the plans are per key, §28;
the key is `testKey(method)`, the `EmptyMethodContext` key of §33.2), the entry rules, and the exit rules at both exits.
The backward forms are their reversals (tests 1, 2, 4), so the contract over the forward forms covers them.

```kotlin
/** The forward forms of one method key. */
class ForwardForms(val statements: List<StatementSummary>, val plans: List<CallPlan>, val entry: RuleStatement,
                   val exits: List<ExitRules>)

fun allForwardForms(interp: JIRInterpreter, key: MethodKey): ForwardForms {
    val (calls, others) = (key.method as JIRMethod).instList.instructions.partition { it.callExpr != null }
    return ForwardForms(
        statements = others.map { interp.statementSummary(key, it) },
        plans = calls.map { interp.callPlan(key, it, it.callExpr!!) },
        entry = interp.entryRules(key),
        exits = interp.exitNodes(key).map { interp.exitRules(key, it.node) })
}

private fun pathEdges(ss: List<StatementSummary>): List<PathEdge> = ss.flatMap { s -> s.edges.map { it.edge } }
private fun ForwardForms.edgeStages(kind: StageKind) =
    plans.flatMap { p -> p.stages.filterIsInstance<CallStage.Edges>().filter { it.kind == kind } }
fun ForwardForms.stages(kind: StageKind): List<StatementSummary> = edgeStages(kind).map { it.summary }
private fun ForwardForms.cleanSteps(): List<CleanStep> = plans.flatMap { p -> p.stages.flatMap { st -> when (st) {
    is CallStage.Actions -> st.steps
    else -> emptyList()
} } }
private fun ForwardForms.kills(): List<CleanStep.Kill> = cleanSteps().filterIsInstance<CleanStep.Kill>()
private fun ForwardForms.fieldCleaners(): List<CleanStep.Field> = cleanSteps().filterIsInstance<CleanStep.Field>()

/** The STATEMENT-mode summaries (§23.3): the statement summaries, the rule statements of the entry and of the exits,
 *  the keep edges of the kills and the read/write statements of field cleaners (F74). */
fun ForwardForms.statementModeSummaries(): List<StatementSummary> =
    statements + entry.summary + exits.map { it.rules.summary } + kills().map { it.keepEdges } +
        fieldCleaners().flatMap { listOf(it.read, it.write) }
/** The STATEMENT MICRO EDGES (ap.md §4.10 item 1): the STATEMENT-mode summaries and the stages with
 *  `StageKind.statementEdges` (SOURCES, UNRESOLVED). */
fun ForwardForms.statementEdges(): List<PathEdge> =
    pathEdges(statementModeSummaries() + stages(StageKind.SOURCES) + stages(StageKind.UNRESOLVED))
fun ForwardForms.bindings(): List<PathEdge> = pathEdges(stages(StageKind.BIND_IN) + stages(StageKind.BIND_BACK))
/** Every forward micro edge (Part II §23.1): the STATEMENT-mode summaries and every `Edges` stage. */
fun ForwardForms.microEdges(): List<MicroEdge> = (statementModeSummaries() +
    plans.flatMap { p -> p.stages.filterIsInstance<CallStage.Edges>().map { it.summary } }).flatMap { it.edges }
fun ForwardForms.bindBack(): List<PathEdge> = pathEdges(stages(StageKind.BIND_BACK))
fun ForwardForms.aliasEdges(): List<PathEdge> = pathEdges(stages(StageKind.ALIASES))
/** Every cleaner: the steps of both `Actions` stages. */
fun ForwardForms.cleaners(): List<Cleaner> = cleanSteps().mapNotNull { step -> when (step) {
    is CleanStep.Clean -> step.cleaner
    is CleanStep.Field -> step.cleaner
    is CleanStep.Kill -> null
} }
fun ForwardForms.sinkPatterns(): List<Pattern> =
    (plans.flatMap { it.sinks } + entry.sinks + exits.flatMap { it.rules.sinks }).flatMap { it.patterns }

/** I12 (a), (b), (c), (e) (Lean `Statics.SWF`: `ss`, `toC`, `fromC`, `clean`). (d) is a check of the run config
 *  (`fieldLimit >= 1` in run 1), (f) holds by the type of `AccessPathBase`, (g) is a rule of the engine (ap.md §6.2).
 *  (b) is checked in its strong form: a target path on `S` with fewer than two accessors (the root or a class `[<C>]`)
 *  counts as strictly above a static position. The forms of §1.4 meet the strong form. */
fun ForwardForms.staticWellFormed(): Boolean {
    val s = AccessPathBase.ClassStatic
    fun dollarConcrete(f: PathFact) = f.tail == Tail.EXACT && f.mark is MarkSlot.Concrete
    // (a) S to S: an identity restriction `S.q.* ->_E S.q.*`, or a field-to-field edge (a pass rule between static fields)
    val a = statementEdges().filter { it.from.base == s && it.to.base == s }.all { e ->
        (e.from == e.to && e.from.tail == Tail.STAR && e.from.mark == MarkSlot.STAR) ||
            (e.from.path.size >= 2 && e.to.path.size >= 2)
    }
    // (b) another base into S above a static position: a `$` target and a `$` premise with a concrete mark; a
    //     conjunctive edge: each literal
    val b = statementEdges().filter { it.from.base != s && it.to.base == s && it.to.path.size < 2 }
        .all { dollarConcrete(it.to) && dollarConcrete(it.from) }
    val bConj = (statementModeSummaries() + stages(StageKind.SOURCES)).flatMap { it.conjunctions }
        .filter { it.target.base == s && it.target.path.size < 2 }
        .all { c -> dollarConcrete(c.target) && c.literals.all { dollarConcrete(it.fact) } }
    // (c) a call binds S only by `S.* -> S.*`; an alias edge never targets S (under A1 no static alias base occurs, §32)
    val c = (bindings() + aliasEdges()).filter { it.from.base == s || it.to.base == s }.all { it == keepEdge(s) }
    // (e) a cleaner on S names its mark, except the whole-base cleaner `(S, atAndBelow, all)` with the empty path
    val e = cleaners().filter { it.base == s }.all { it.mark != null || (it.path == null && it.reach == CleanReach.AT_AND_BELOW) }
    return a && b && bConj && c && e
}
```

### 33.5 The new tests of `validation-plan.md` → Interpreter tests → §7.2 (items 8 to 10 and 14 to 17)

Items 1 to 7 and 11 to 13 are rows of §33.1 (tests 1 to 11). Part of items 8 and 9 is also in `analyzer-impl.md`
(`ModesTest`: a request in a restricted run fails; `FactKindsTest`: no request from a TAINT or a `Reach` input).
Items 18 to 28 are rows of §33.1 or of `analyzer-impl.md` §9.1: 18 (no liveness) `DeltaWorklistTest`; 19 (rule
positions; a rule error rejects the whole rule) tests 6 and 7; 20 (the whole-base cleaner, the Spring dispatcher) tests
6 and 13; 21 (empty methods) test 7 and `ModesTest`; 22 (aliases on call results) and 25 (end facts)
`CallPlanRunnerTest`; 23 (the global-state rule, D30: the drop only on a zero-premise item, a caller-set state passes)
test 8 and `ExitRulesTest`; 24 (the mark policy below `[e]`) test 10; 26 (the summary rewriter) test 7 (the
selection) and `CallPlanRunnerTest` (the rewriter on a zero-premise summary result and on the default identity); 27
(the static exception at the exit rule statement) test 11 and the item 8 row below; 28 (a conjunctive exit source, D31)
test 8 (the form) and `ExitRulesTest` (the stored inputs, the union premise set in either order, the summary, with two
non-zero premises an ND summary and E6 at a caller). Items 29 to 34 (the tail `[any-taint]`, F69): 29 (the taint
annotation), 30 (the exclusion rows and the cut) and 34 (the backward run with no `[any-taint]`, the may of a reversed
pass rule) are test 14 (`AnyTaintFormsTest`, §33.7) with test 6 (the rule forms) and test 9 (the contract I14); the
programs of 30 (S, SD, B, R; X and CUT as JIR shapes: the alias-gap false positive and `L = 1`), 31 (program G, the
Spring DTO), 32 (program C) and 33 (the conjunction) are test 15 (the analysis tests) with Part I §8 test 23 (X as one
statement and CUT with `L = 0` are AP-level only). Item 35 (D35: the entry-mark removal at any depth) is test 8 (the
removal on the exit fact) with test 15. Item 19 (D33: a pass rule with an `AnyField` premise) is test 6; item 26
(D34: the rewriter on an `AnyField` source target, and a cleaner's `AnyField` position with `(P, below, T)`) is test 7.

| Item | Test (class) | Form check (Part II) | End to end (`analyzer-impl.md`, phase 3) |
|---|---|---|---|
| 8 requests (§5.4) | `JIRStatementEffectTest` (the forms of a JIR sample with `ApOps` in run 1), `JIRCallPlanTest` | one case per row of §5.4, each on a FLOW input with the mark `*`: a sink at a call and an exit sink: `checkMark` gives `MarkCheck.Request(T)`; a conjunctive sink: one request per literal; a conditional source at a call and an exit source, a `CopyMark(T)` pass rule, an ND source literal: the mark gate of `applyEdge` gives the mark request; a selected-mark cleaner action and the rewriter on every same-base abstract fact: `clean` requests T, also on disjoint paths and when T is already excluded; another-base facts pass unchanged; the static rows: `x = C.s` on `S.*` (premise `S.*`) gives the position request `[<C>, s]`, `C.s = x` the request `[<C>]` of the class keep edge, a sink on `S.<C>.f` the mark request, and an exit source with the literal on `S.<C>.f` (the rule statement of the exit, STATEMENT mode, `statementEdge = true`) on `S.*` with the premise `S.*` the position request `[<C>, f]` and no fact, while `S.*` stays by its keep edge (`validation-plan.md` → Interpreter tests → §7.2 item 27; ap.md §4.10 item 1); the entry rules and a read source: no request. The same cleaner forms in either restricted direction keep the exclusion and record no request. All restricted mark gates suppress requests (`validation-plan.md` → AP tests → §13 item 9) | the run-1 request reaches the caller premise (`interpreter.md` §5.4) |
| 9 ND (§5.3) | `JIRRuleFormsTest`, `JIRStatementEffectTest` | `AssignMark(T, Result) if ContainsMark(Argument(0), A) && ContainsMark(Argument(1), B)` gives one `ConjunctiveEdge` with two literals in the `SOURCES` stage; the two inputs in both orders give the same result (`ConjunctionStore`, Part I §7.10), with the union of the premise sets without the zero fact; a sink with the same condition is one `SinkRule` with two patterns: one `Holds` stores an input and gives no witness, the second gives one | the conjunctive source and the conjunctive sink act only when both arguments are tainted, in either order of arrival |
| 10 the rule statement of a call (§4.1) | `JIRCallPlanTest` | the `SOURCES` stage has `ZERO_KEEP` and the source edges only (no keep edge of another base); `FormsReference.run` on the plan: the zero fact reaches `REWRITTEN` by `ZERO_KEEP` and gives the target of an unconditional source; with `AssignMark(T, Result) if ContainsMark(Argument(0), T)`, a fact on `arg0` with `T` gives only `ret.$ (T)` through `SOURCES`, and `arg0` reaches `REWRITTEN` only through the callees or the `UNRESOLVED` stages | the read argument comes back only through the callee summary or the unresolved path |
| 14 array elements of a call sink (§4.2) | `JIRRuleFormsTest`, `JIRCallPlanTest` | a sink `ContainsMark(Argument(0), T)` on an `Object[]` argument gives two `SinkRule`s with the patterns `(arg0, [], $, T)` and `(arg0, [e], $, T)`, one `rule`; on a `String` argument one `SinkRule`; a conjunctive sink on two array arguments gives four alternatives; the receiver, `Result`, the entry and the exit sinks get none | `arg(0).[e].$ (T)` triggers the sink; the backward run seeds `seedPatterns()` of both alternatives |
| 15 exit rules at the exceptional exit (§4.7) | `JIRBoundaryFormsTest` | `exitRules(m, JMethodExitExceptionalInst)`: an exit sink on `Result` has the pattern on `exc`; an exit source on `Result` targets `exc`; `globalStateDrop` false, `entryMarks` empty; `reversed()` gives the backward start rules of that exit | an exit sink on `Result` triggers on the thrown tainted value; an exit source at the exceptional exit adds no summary edge |
| 16 cleaners (§4.2, D20) | `JIRRuleFormsTest` | `RemoveMark(T, P) if ContainsMark(P, T)` and `RemoveMark(T, arg0) if Not(ContainsMark(arg1, RAW))` give no `CleanStep`; the same rule with no condition gives `Cleaner(P, EXACT, T)` (and `<string-bytes>` on a `String` position) | the first two do not clean; the third cleans |
| 17 lambdas (§3.9, D19) | `JIRCallPlanTest` (a cp with the lambda features, §31.2) | a call through a FUNCTIONAL INTERFACE OF THE PROJECT (`sample.lambda.StringOp`, one abstract method): with prescan lambdas for the call, a `Callees` stage with the lambda methods and no `UNRESOLVED` stage; with none, no `Callees` stage for the lambda and the `UNRESOLVED` stages. A second case through a JDK interface (`java.util.function.Function`): the resolver adds its own `MethodResolutionFailed` (`JIRCallResolver.kt:160-163, 174-178`), so the `UNRESOLVED` stages stay beside the `Callees` stage | the known lambda of a project interface takes no unresolved path; the unknown one does; a JDK interface keeps the unresolved path |

```kotlin
@Test // `validation-plan.md` → Interpreter tests → §7.2 item 14; §4.2 ARRAY ELEMENTS OF A CALL SINK
fun `a call sink on an array argument has the element alternative`() {
    val rules = TestRules(sinks = listOf(sinkOn("sample.Sinks", "sink", ContainsMark(Argument(0), RuleMark("T")))))
    val interp = testInterpreter(cp, rules)
    val (key, s, call) = callOf("sample.ArraySink", "callWithArray")              // sink((Object[]) a)
    val plan = interp.callPlan(key, s, call)
    val t = interp.manager.marks.mark("T")
    val e = interp.idx(ElementAccessor)
    fun pat(vararg p: AccessorIdx) = Pattern(PathFact(AccessPathBase.Argument(0), p.toList(), Tail.EXACT, MarkSlot.Concrete(t)), ExclusionSet.Empty)
    assertEquals(setOf(listOf(pat()), listOf(pat(e))), plan.sinks.map { it.patterns }.toSet())
    assertTrue(plan.sinks.map { it.rule }.distinct().size == 1)                       // one vulnerability key
    assertEquals(listOf(0, 1), plan.sinks.map { it.alternative })                     // one witness per alternative
    val again = JIRCallPlanBuilder(interp, interp.entries[key], key, s as JIRInst, call as JIRCallExpr).build()   // no cache
    assertEquals(plan.sinks.map { it.alternative to it.patterns }, again.sinks.map { it.alternative to it.patterns })   // stable
    val (key2, s2, call2) = callOf("sample.ArraySink", "callWithString")             // sink((String) a)
    assertEquals(listOf(listOf(pat())), interp.callPlan(key2, s2, call2).sinks.map { it.patterns })
}
```

`TestRules`, `sinkOn` and `callOf` are helpers of `JIRInterpreterTestKit` (§33.2). `sample.ArraySink` is a new sample
class: `callWithArray(Object[] a) { Sinks.sink(a); }` and `callWithString(String a) { Sinks.sink(a); }`.

### 33.6 Example: `FormApplierTest`

```kotlin
class FormApplierTest {
    private val manager = ApManager(Cancellation())
    private val x = AccessPathBase.LocalVar(1); private val y = AccessPathBase.LocalVar(2); private val z = AccessPathBase.LocalVar(3)
    private val T = manager.marks.mark("T")
    private fun conc(b: AccessPathBase) = Conclusion(PathFact(b, emptyList(), Tail.EXACT, MarkSlot.Concrete(T)), ExclusionSet.Empty, false)
    private val hits = ArrayList<MicroEdge>()
    private val alg = ReferenceAlgebra(ApMode(run1 = true, Direction.FORWARD, fieldLimit = 4),
        allowsSource = { _, _ -> true }, sourceHit = { _, me -> hits += me }, manager = manager)
    private val applier = FormApplier(alg)
    private val at = Place(testCall(), statementEdge = false, sources = true)

    @Test // §23.3 STATEMENT: x = y passes z, kills the old x, moves y to x
    fun `statement passes an untouched base and kills a touched one`() {
        val s = MicroEdgeBuilder().apply { move(x, y) }.build()
        fun run(c: Conclusion) = buildList { applier.statement(s, emptySet(), c, at) { _, r -> add(r.fact.base) } }
        assertEquals(listOf(z), run(conc(z)))                                    // untouched: passes
        assertEquals(emptyList(), run(conc(x)))                                  // touched, no edge from x: the kill
        assertEquals(setOf(x, y), run(conc(y)).toSet())                          // y.* -> y.*, y.* -> x.*
    }

    @Test // §23.1 the source-seed places: a source hit in a source stage, never in GEN
    fun `a source edge records its hit only at a source-seed place`() {
        val src = MicroEdgeBuilder().apply { keepZero(); edge(PathEdge(ZERO_FACT, PathFact(x, emptyList(), Tail.EXACT, MarkSlot.Concrete(T)), ExclusionSet.Empty)) }
        val zero = Conclusion(ZERO_FACT, ExclusionSet.Empty, false)
        applier.statement(src.build(), emptySet(), zero, at) { _, _ -> }
        assertEquals(1, hits.size)                                               // the source, not the zero keep edge
        applier.gen(src.buildGen().edges, emptySet(), zero) { _, _ -> }
        assertEquals(1, hits.size)                                               // GEN: no hit
    }
}
```

### 33.7 Example: `AnyTaintFormsTest` (test 14; `validation-plan.md` → Interpreter tests → §7.2 items 29 and 34)

```kotlin
class AnyTaintFormsTest {
    private val manager = ApManager(Cancellation())
    private val ops = ApOps(manager)
    private val p = AccessPathBase.Argument(0); private val q = AccessPathBase.Argument(1)
    private val T = manager.marks.mark("T")
    private val run1 = ApMode(run1 = true, Direction.FORWARD, fieldLimit = 4)
    private val back = ApMode(run1 = false, Direction.BACKWARD, fieldLimit = 4)
    private fun side(b: AccessPathBase, tail: Tail) = PathFact(b, emptyList(), tail, MarkSlot.Concrete(T))
    private fun stage(e: PathEdge) = MicroEdgeBuilder().apply { edge(e) }.buildStage()
    private fun layer(me: MicroEdge, c: Facts, mode: ApMode) = CollectingOut().also {
        ops.applyEdge(c, manager.zero, me.edge, statementEdge = true, mode, it, may = me.may) }.results.single().layer

    @Test // I14, D32: a source and a pass rule differ only in the target tail (Lean AnyTaintExCases2.PassRule.source_vs_pass, AnyTaint.Sanity)
    fun `a source and a pass rule differ in the target tail, and only the reversed pass rule is a may`() {
        val onP = manager.factsOf(side(p, Tail.EXACT), ExclusionSet.Empty, Layer.NORMAL)
        // AssignMark(T, Q.AnyField) if ContainsMark(P, T)  and  CopyMark(T, P -> Q.AnyField)
        val source = stage(PathEdge(side(p, Tail.EXACT), side(q, Tail.ANY_TAINT), ExclusionSet.Empty))
        val pass = stage(PathEdge(side(p, Tail.EXACT), side(q, Tail.ANY), ExclusionSet.Empty))
        assertEquals(Layer.NORMAL to Layer.DEMAND, layer(source.edges.single(), onP, run1) to layer(pass.edges.single(), onP, run1))
        // A1: both reverse to `Q.[any] (T) -> P.$ (T)`; the forward target tells the may (interpreter.md §4.9; review RS-1)
        val rs = source.reversed().edges.single(); val rp = pass.reversed().edges.single()
        assertEquals(rs.edge, rp.edge)                                                   // no backward `[any-taint]`
        assertEquals(false to true, rs.may to rp.may)
        val onQ = manager.factsOf(side(q, Tail.EXACT), ExclusionSet.Empty, Layer.NORMAL)   // the requirement (Q, ., $, T)
        assertEquals(Layer.NORMAL to Layer.DEMAND, layer(rs, onQ, back) to layer(rp, onQ, back))   // the `$` result of a may: demand
    }

    @Test // ap.md §9.2 SEEDS; interpreter.md I11 (f), §4.9: the backward run has no `[any-taint]` (A1)
    fun `the seed of an any sink pattern is an any requirement in the demand layer`() {
        val rule = sinkOn("sample.Sinks", "sinkAny", ContainsMarkOnAnyField(Argument(0), RuleMark("T")))   // JIRInterpreterTestKit
        val sink = SinkRule(rule, alternative = 0, patterns = listOf(Pattern(side(p, Tail.ANY), ExclusionSet.Empty)), endFacts = emptyList())
        val seed = sink.seedPatterns().single()
        assertEquals(Tail.ANY, seed.fact.tail)
        assertEquals(Layer.DEMAND, ops.targetTree(seed.fact, Layer.NORMAL).layer)        // W6
        val cut = CollectingOut().also { ops.limit(manager.factsOf(PathFact(p, listOf(manager.accessors.index(
            FieldAccessor("C", "f", "C")), manager.accessors.index(FieldAccessor("C", "g", "C"))), Tail.ANY_TAINT,
            MarkSlot.Concrete(T)), ExclusionSet.Empty, Layer.NORMAL), fieldLimit = 1, it) }.results.single()
        assertEquals(Tail.ANY to Layer.DEMAND, ops.leaves(cut).single().fact.tail to cut.layer)   // the cut makes it `[any]`
        assertEquals(ExclusionSet.Empty, (cut as TaintTree).exclusion)                   // with no exclusion (AnyTaintEx.limitFX)
    }
}
```

---

## 34. Current decision index

The normative specs define these decisions. This index keeps the SI locators used above.
[ap-history.md](ap-history.md) records the earlier alternatives and user decisions.
[implementation-background.md](implementation-background.md) lists migration differences.
[proof-status.md](proof-status.md) states current proof limits.

| Id | Current behavior | Normative rule | Implementation |
|---|---|---|---|
| SI1 | A call sink on an array argument also has its element alternative. | `interpreter.md` §4.2 | §27.3, §28.1 |
| SI2 | Exit sinks check every fact. | `interpreter.md` §4.7, D21 | §29 |
| SI3 | A pass rule with a mark literal logs a rule error and applies without the literal. Pass rules produce no conjunction. | `interpreter.md` §4.2, §5.3, D24 | §27.4, §28.5 |
| SI4 | A conjunctive exit source joins inputs in its method key and emits the union of their premises. | `interpreter.md` §4.7, D31 | §29; `analyzer-impl.md` §4.4 |
| SI5 | Constructor pass-over keeps every bound position, including `S`. | `interpreter.md` §3.5 | §28.1 |
| SI6 | Binding back has no constant-base edge; no form produces such a callee exit fact. | `interpreter.md` §3.1 | §28.2 |
| SI7 | A lambda call with prescan targets uses those targets. With none, it is unresolved. | `interpreter.md` §3.9, D19 | §28.4 |
| SI8 | Operand filters and result filters are separate. Backward forms apply neither. | `interpreter.md` §2.1, §3.1; `analyzer-core.md` §4.9 | §23.2, §26.2 |
| SI9 | Reversed call stages add no statement identity. The call plan supplies `AFTER → BEFORE` pass-over. | `interpreter.md` §4.9; `analyzer-core.md` §4.5 | §23.2, §23.6 |
| SI10 | Each acting string cleaner includes its string-bytes position. | `interpreter.md` §5.2 | §27.5 |
| SI11 | Exit rules run at both exits. At an exceptional exit, Result is `exc`; no summary is emitted. | `interpreter.md` §4.7, D26 | §29 |
| SI12 | An unconditional exit sink checks the zero fact at both exits. | `interpreter.md` §4.7, D22 | §27.3, §29 |
| SI13 | A cleaner with a mark literal remaining after static evaluation does not act. | `interpreter.md` §4.2, D20 | §27.5 |
| SI14 | The rewriter acts on every returned result, including zero-premise summaries and default identity. | `interpreter.md` §3.7, D23 | §28.3 |
| SI15 | Normal default identity and constructor pass-over take no alias. Demand identity takes aliases. | `interpreter.md` §3.8, AC4 | §28.1; `analyzer-impl.md` §4.6 |
| SI16 | Call plans and entry rules are cached per method key; statements and exit rules per method. | `analyzer-core.md` §4.8 | §31.2 |
| SI17 | An exit sink consumes evaluated statics only on a zero-premise item. A conjunctive literal keeps its stored input. | `interpreter.md` §4.7, D30 | §29 |
| SI18 | Use structural trie equality, adapted accessor tables and `ConclusionGroup`. Do not use `AccessPathBaseStorage` or a walk memo. | `ap.md` §7.5, §7.6; `analyzer-core.md` §8 | §2, §4; `analyzer-impl.md` §2.1 |
| SI19 | An AnyField source or end-fact target uses `[any-taint]` with empty E. A demand-layer result demotes it to `[any]`. | `interpreter.md` §4.1, I14; `ap.md` W8 | §27.2, §27.3 |
| SI20 | `MicroEdge.may` reads the forward target tail. A reversed conjunction literal also sets it. Generic micro reversal is separate from native record views. | `interpreter.md` I14; `ap.md` §9.1 | §6, §23.1, §23.2 |
| SI21 | A backward demand-layer group stays in demand regardless of its reversed tails. | `ap.md` §9.2; `analyzer-core.md` §7.4 | §5.9, §6 |
| SI22 | Emission and reduction check marks and locations. Both hand-offs normalize abstract mark exclusions. | `ap.md` §6.3, §6.4, §9.2 | §5.9, §6; DD18 |
