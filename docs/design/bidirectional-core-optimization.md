# Bidirectional analyzer core: memory and allocation design

Status: proposal. Date: 12 October 2026. This document proposes changes; it does not report an optimized analyzer.

The first goal is to reduce the retained size of method forms. The second goal is to remove repeated graph, queue, alias and trie work. Keep the current analysis semantics and the selected project settings. Apply each change separately, so its result can be measured and its cause can be identified.

This proposal covers the analyzer core, its JVM interpreter and graph adapters, and rule compilation and resolution. IR storage, IR tables, decoded IR objects and changes inside `core/opentaint-ir` are out of scope. Graph reuse below occurs in an analyzer-owned adapter. It does not change the IR provider.

## Evidence and coverage

The [memory report](../reports/bidirectional-project-memory.md) identifies the retained owners. The [project report](../reports/bidirectional-project-testing.md) gives CPU profiles, allocation profiles, project settings and completion results. The [input inventory](../reports/bidirectional-project-inputs.md) identifies the fixed inputs.

All four main scans complete. All four bidi scans stop at the memory guard. The heaps show the current run's actors and one current run manager. They do not show a leak of old actors or old run managers. Method forms intentionally remain in the cache across direction changes.

| Project | Live heap, GB | Method cache, GB | Lowered forms, GB | Main core cost found in the heap |
| --- | ---: | ---: | ---: | --- |
| OWASP | 7.769 | 6.459 | 6.357 | 10.64 million sink alternatives and repeated owned literal payloads |
| Conductor | 7.674 | 2.412 | 2.219 | Small form containers; five collection classes use 61.12% of the form union |
| ThingsBoard | 11.484 | 5.622 | 5.154 | Small containers, broad resolved exit rules and source conjunctions |
| Stirling-PDF | 7.672 | 4.361 | 4.183 | Expanded reverse conjunctions and eager containers in current fact groups |

GB means 10^9 bytes. Retained owner unions overlap. Do not add these columns or use them as an estimate of recoverable memory. A new cache can also retain memory, so measure its complete retained cost.

The proposal covers each core optimization mentioned in the reports:

| ID | Optimization | Design below |
| --- | --- | --- |
| O1 | Reduce condition expansion and duplicate array products | Rule-kind policy, shared condition recipes and gated positive absorption |
| O2 | Share paths, facts, patterns and repeated lowered forms | Owned immutable payloads with separate rule occurrences |
| O3 | Reduce small summary and call-plan collections | Empty/singleton containers and compact indexes |
| O4 | Reduce reverse conjunction branch objects | Blocks that refer to the original conjunction and literal slots |
| O5 | Share resolved rule templates, validation and matching work | Configuration-scoped templates and complete binding keys |
| O6 | Share cleaner tuples and normalize repeated generated actions | Payload reuse first; action removal only after an effect-equivalence check |
| O7 | Reduce current fact-group containers | Reach bits, lazy kind maps and lazy layer storage |
| O8 | Reuse graph construction and traversal data | Analyzer-owned method graph snapshots |
| O9 | Remove queue comparator boxing | Primitive comparison and direct priority-key construction |
| O10 | Remove alias-set copies | Stable views and direct iteration |
| O11 | Reduce trie copies, views and callback objects | Owned-node builders, unchanged-result reuse and bounded local memoization |

Dense per-premise statement arrays are included in O7 as a conditional follow-up. Their measured size does not justify changing them first. Exit source-location mapping is a separate reporting defect and is outside this optimization proposal.

## Contracts for every change

The [interpreter specification](../../spec/interpreter.md), [AP specification](../../spec/ap.md), [core specification](../../spec/analyzer-core.md) and [validation plan](../../spec/validation-plan.md) remain the contracts.

1. Run 1 has field limit 1. Later runs use increasing positive limits. There is no limit-0 run. Preserve the configured heap, timeout, run direction and barrier rules.
2. Apply the specified exclusion table without change. Preserve NORMAL and DEMAND layers, FLOW and TAINT kinds, premise sets, type filters, mark policies and F74/F75/F76 behavior. Class accessors retain their specified field-limit treatment.
3. Keep distinct logical joins. A conjunction occurrence and each of its literal slots have separate standing entries. Equal payloads do not permit two alternatives to share those entries.
4. Keep sink rule ownership, alternative numbers, ordered literal slots and witness support. Seed and report keys must refer to the same logical occurrence in both directions and across runs.
5. Preserve source admission and source hits by full `(MethodKey, statement, original forward PathEdge)` value. End-fact actions keep their own admission and trigger rules. A reversed end fact fires the owning alternative's seeds once per method key and statement, including for a CONFIRMED result.
6. Keep public defensive ownership. A caller cannot change a cached form by changing an input array, list, set or map. Internal factories can reuse a value only after it is frozen and owned.
7. Keep the required relevant-rule lookup and mark universe. In particular, retain `allRelevant=true` for rewriter rules. Do not introduce an alternative cap, a queue cap, a selected-mark shortcut or an arbitrary field-limit change.

Representation changes must preserve effects exactly. An effect includes output facts, layer, exclusion, premise support, requests, source hits, end triggers, raw summaries and report state. Boolean equivalence alone does not establish this contract for a rule compiler.

## O1. Compile conditions before allocating alternatives

Change [JIRRuleForms](../../core/opentaint-dataflow-core/opentaint-jvm-dataflow/src/main/kotlin/org/opentaint/dataflow/jvm/bidi/interp/JIRRuleForms.kt) to use explicit compiler stages:

```text
raw rule
  -> whole-rule validation
  -> per-place rewrite of static non-mark conditions
  -> mark policy for this rule kind
  -> frozen condition recipe
  -> normalized expression, where effect equivalence is established
  -> cube and array-choice iteration
  -> owned literal payload + logical occurrence
```

Validate all raw actions and all mark literals, including negative literals, before simplification can remove them. Invalid positions or actions must still produce the current rule error. Validation and a condition that rewrites to false are different results.

### Rule-kind policy

Sources and sinks replace a negated mark literal with true before positive normalization. For example, `P AND NOT P` becomes `P` under the current policy. Applying ordinary contradiction elimination first would incorrectly produce false.

Cleaners retain signed conditions and the cleaner normalization already taken from `saloed/cleaner-action-normalisation`. Preserve its action-specific assumptions: exact named removal can assume its exact mark; terminal `AnyField` removal can assume its field mark; a non-`AnyField` `ExactAndAnyField` action cannot assume a root mark. A remaining mark-dependent cleaner condition contributes no cleaner and raises no request. Pass rules retain their current logged mark-condition exception. Do not apply the source/sink positive policy to these kinds.

Relevant-rule resolution has its own policy for negative method, class and arity conditions. Keep that policy and distinguish it from ordinary rule resolution in all cache keys.

### Shared recipe and expansion

Store the rewritten condition as an immutable expression DAG. Reuse equal subexpressions. Flatten AND and OR nodes without changing their first-occurrence order. Give atoms stable symbolic keys that include position, path, mark and condition kind.

Use a cube iterator instead of building a complete list at each intermediate product. Deduplicate a candidate before creating `Pattern`, `PathFact` and sink wrapper objects. Keep cancellation checks in long products. This removes intermediate allocation; it does not make a genuinely exponential condition small.

A canonical set signature identifies equal positive literal sets. Keep a separate ordered slot vector and origin mapping. Current deduplication of ordered lists can retain equal sets with a different slot order. Removing those occurrences requires the semantic gate below; a set signature alone must not change the current occurrence inventory or repeated slots in a public form.

For a call sink, apply array expansion only to positive argument literals whose actual static argument can be an array. Add the original path choice and one element-prefix choice. Receiver, result, entry and exit positions keep their current policy. A resolved condition can already mention an element path; adding another element prefix is still required by the call policy. Do not remove it as a duplicate prefix. Deduplicate final shapes before copying payloads, while preserving each required logical occurrence and its origin.

### Positive absorption: a separate semantic gate

The measured OWASP expression `(W OR B) AND W AND B` expands to 210 raw combinations. Its Boolean reduction `W AND B` has 14 choices. This makes absorption a strong candidate. Those numbers are a condition-size estimate, not evidence that 196 analyzer alternatives can safely be deleted.

Compute a positive normalized expression before Cartesian expansion. Use duplicate removal and absorption such as `A OR (A AND B) -> A`. Keep the original frozen recipe alongside it. Add an `AlternativeOrigin` mapping from a normalized shape to the original rule, placement, alternative number, ordered slots and action targets. Keep logical occurrence IDs separate from compact payload IDs. A normalized cube's ordinal cannot replace the original seed or witness key. Build the origin table once with the form and use that same table in forward and backward forms.

Enable occurrence removal only for a rule kind and placement that passes full effect-equivalence tests. The important cases are:

- A source clause carries premise support and can produce a different layer when its literals come from NORMAL and DEMAND inputs. A removed clause must not remove a required support or change a summary header.
- A sink alternative carries witness and seed support. Its end-fact trigger must still demand the correct original supports. Mapping only to a rule ID is insufficient.
- An exit sink can read and drop static state before its conjunction completes. Under G2/D30, `A OR (A AND B)` can store and drop a zero-premise `B` even if no `A` arrives. Reducing the expression to `A` changes the raw summary.

For exit sinks, keep the current alternatives in the first implementation. A later absorption implementation must first separate the original post-static positive read/drop footprint from the normalized firing predicate. That footprint must still evaluate the original literals and retain their standing support and triggers. Test delayed completion as well as a conjunction that never completes. Retaining the footprint is necessary; it does not by itself prove equivalence of all witnesses and seeds.

If a removed alternative still has an observable effect, retain its logical occurrence using shared storage. Do not merge its join with another occurrence. This is the fallback design, not a reason to weaken the specification. A fully lazy alternative representation can be considered if expansion is still large after sharing; it must enumerate every required occurrence and use stable IDs in both directions. A cap or fact-dependent early omission is excluded.

## O2. Share immutable payloads and keep occurrence identity

Introduce internal owned values for paths, literal vectors, targets and end-fact vectors. Freeze a value once at the compiler boundary. Empty and singleton vectors use compact representations. Their elements do not carry a reference back to the pool that created them.

Use two levels of ownership:

| Level | Shared data | Data that remains separate |
| --- | --- | --- |
| Symbolic rule | Position/path descriptors, mark names, expression nodes, action recipes | Original rule identity, metadata, binding and placement |
| Runtime form | Manager-owned path/fact/pattern payloads and ordered vectors | `SinkRule`, `ConjunctiveEdge`, alternative number, statement and trigger ownership |

Each `SinkRule` references a frozen literal vector and end-fact vector. Each `ConjunctiveEdge` references a frozen literal vector and target. Keep their wrappers stable: the conjunction store uses them to separate standing joins. Source cubes used by several actions can share the literal storage and target storage, but keep one logical join occurrence per original action/alternative. Sharing one execution join across those actions would need a separate specification change.

Runtime value keys include base, complete path, tail, mark and exclusion. They belong to one AP manager and one analysis. Symbolic keys use symbolic positions and mark names, not manager-local numeric IDs. In particular, `PathEdge.compiled` is mutable and manager-bound. Do not put a compiled `PathEdge` in a global structural pool. Keep compiled transfer state in its current owner or an explicitly manager-owned descriptor.

Start with compiler-local or method-local payload pools. Use an analysis-wide pool only for payload families with measured reuse across methods. Keep no actors, subscriptions or run stores in these pools. Track unique values, references, lookup cost and retained pool bytes. A bounded pool may stop retaining new values when its measured budget is reached; a miss produces a normal owned value and changes no semantics.

Reuse already-owned paths when creating sink seeds and reversal descriptors. Public constructors still snapshot caller-owned data. Add a private trusted construction path; do not expose a public flag that lets a caller skip ownership checks.

Complete rewritten form sharing uses a different key from leaf-value sharing. Include the original rule owner, rule kind, method binding, rewritten condition, action specialization, result base, relevant/ordinary mode, configuration generation and actual call array mask. Include context-dependent filters or alias bindings when the payload contains them. Normal and exceptional results remain distinct. Share the payload under this key; retain occurrence wrappers at each placement.

The existing `FormsCache` keeps stable forward and backward slots across runs. Preserve that contract. Dropping a direction's forms at a barrier and rebuilding wrappers later would change identity and provenance.

## O3. Use compact immutable form containers

Change [StatementSummary](../../core/opentaint-dataflow-core/opentaint-dataflow/src/main/kotlin/org/opentaint/dataflow/bidi/interp/StatementSummary.kt) and [CallPlan](../../core/opentaint-dataflow-core/opentaint-dataflow/src/main/kotlin/org/opentaint/dataflow/bidi/interp/CallPlan.kt) internally. Preserve their read-only collection interfaces during migration.

Use frozen vectors with empty, singleton and array-backed forms. Use compact base-key tables for touched bases, targets and filters. A zero-entry table has no backing map. A one-entry table stores its key/value directly. Larger tables keep ordered keys and values; use linear lookup for small tables and a lookup index only above a threshold selected by a lookup/allocation benchmark. Do not allocate a `LinkedHashMap` and wrapper for each absent field.

Keep one ordered edge vector. Build a base-to-ordinal index over it, rather than separate list objects containing the same edges. A base lookup reuses a read-only view or directly iterates the indexed ordinals; it must not allocate a new view on every lookup. Do the same for `CallPlan.stagesFrom`, using the small fixed `CallPoint` set and stage ordinals. Preserve stage order and the rule that stages from one point read the same input.

Share individual empty fields. A summary is wholly empty only if every semantic field is empty, including touched bases. A kill summary with no edges still has an effect. GEN summaries, statement summaries, stage summaries and pass-over identities keep their separate construction rules. Filter combination retains the full type predicate and mark policy.

Use a private builder that owns its new arrays and can pass them to a frozen form without a second copy. The public constructor still makes one defensive copy. `withIdentities` can reuse existing frozen payloads and append a new owned segment, rather than copy all nested containers.

This change targets the 61% collection-class cost in Conductor and ThingsBoard. It also removes containers around empty end-fact lists. Do not predict that all of those bytes can be recovered: nonempty indexes and iteration views still have a cost.

## O4. Store reverse conjunctions as blocks

The current reversal creates a `MicroEdge`, two `PathEdge` objects and two endpoint facts per conjunction literal: 168 shallow bytes before indexes and compiled state. One measured Stirling-PDF source stage has 24,619 literal memberships and over 4.13 MB of these branch objects.

Keep ordinary plain edges as `MicroEdge` values initially. For reversed conjunctions, add an internal block:

```text
ReverseConjunctionBlock
  owner: original ConjunctiveEdge occurrence
  literals: original frozen vector, or a span of literal ordinals
  input: original target with the specified reversed tail
  outputs: derived from the original literal slots
  forceDemand: true
```

Index a block by the forward target base. Iterate literal slots directly when applying it. Reuse the original target, literal path and mark payloads. Apply the current tail and mark reversal rules; a forward must target reverses to ordinary ANY. Backward forms contain no must tail.

Add an internal transfer-descriptor entry point in [ApOps](../../core/opentaint-dataflow-core/opentaint-dataflow/src/main/kotlin/org/opentaint/dataflow/bidi/ap/ApOps.kt). It reads the block and slot without constructing synthetic forward/reverse `PathEdge` pairs. Its compiled side reuses manager-owned path data, with shared input-side compilation for a block. Preserve the same checkpoints, output flushing and application modes. A compatibility adapter can materialize an edge for a test or diagnostic; production application must not cache one full legacy edge graph per slot.

All reverse conjunction outputs are DEMAND, including exact-path outputs. They do not become unconditional sources or source hits. The descriptor retains forward classification data needed for `may`; ordinary reversed edges still retain their original forward edge for source admission and hit lookup. Keep forward ANY pass classification, A5 pass-over identities, touched/target bases and boundary behavior. Reversal drops forward filters and guards as specified.

Reversed end-fact transfers keep the owning `SinkRule` trigger. F76 native record views remain a separate path; do not implement this change by reusing interpreter reversal for record reads. The backward engine continues to have no ND conjunction join.

## O5. Share rule recipes, validation and matching

Change [TaintConfiguration](../../core/opentaint-jvm-sast-dataflow/src/main/kotlin/org/opentaint/jvm/sast/dataflow/rules/TaintConfiguration.kt), [MethodTaintConfigurationResolver](../../core/opentaint-jvm-sast-dataflow/src/main/kotlin/org/opentaint/jvm/sast/dataflow/rules/MethodTaintConfigurationResolver.kt) and the JVM form compiler to retain a frozen template plus a small method binding. This targets ThingsBoard's millions of resolved exit rules and the shared prescan resolution cost.

```text
RuleTemplate(template identity, raw condition, actions, metadata)
RuleBinding(template, method, AnyArgument assignment, resolution mode)
ResolvedPayload(rewritten condition, bound action descriptors)
RuleOwner(original rule identity and report metadata, payload)
ValidationCertificate(structural checks, required binding checks)
```

Use stable template identities from the configuration generation and original rule object or serialized occurrence. A displayed vulnerability rule ID is not a unique template key. Preserve distinct generated rules that have the same rule family. The converter's broad end-sink applicability remains unchanged; this proposal shares its recipes instead of reducing the rule set.

Split validation into template checks and binding checks. Cache structural results by template and kind. Cache position/action checks by the complete binding. Do not retain every large resolved candidate object only as the key of a Boolean validation map. A certificate must still reject every invalid raw alternative before static rewriting, including candidates that later become false.

| Cache | Key and scope |
| --- | --- |
| Raw template / structural validation | Configuration generation, template identity, rule kind |
| Method specialization | Template, physical method, complete AnyArgument assignment, ordinary/relevant mode |
| Static condition matching | Condition node, method/statement properties, mode, configuration/body generation; only static inputs |
| Lowered payload | Rewritten condition/actions, bound positions, result base, actual array mask, AP owner and policy |
| Complete call plan | Full `MethodKey` and statement in the existing form cache |
| Entry form | Full `MethodKey` |
| Context-independent statement/exit form | Physical method, statement or normal/exceptional exit kind |

Keep alias-dependent condition checks at the statement/alias owner. Do not cache a fact-dependent match as a static Boolean. Matching caches contain no analysis facts or actors. Keep separate ordinary and all-relevant caches and clear them when the configuration/provider selection changes. The selection generation must include the prescan rule filter's state.

AnyArgument specialization still enumerates all required assignments of independent classifiers. A shared template does not collapse different argument bindings. Context-dependent callees, entry filters, operand/result filters and normal/exceptional `Result` mapping stay outside broad shared recipes unless their complete semantics are in the key.

Preserve null-fact queries, broad exit candidates, relevant-rule negative handling and the full mark universe. A recipe that does not match one method is not globally false. Share matching work with prescan only for identical static keys; prescan improvement alone does not solve post-prescan form memory.

## O6. Share cleaner values before removing actions

Use an owned immutable cleaner payload keyed by `(base, complete path, reach, mark-or-all-marks)`. Reference it from each ordered action occurrence. This targets the repeated tuples in [JIRCallPlanBuilder](../../core/opentaint-dataflow-core/opentaint-jvm-dataflow/src/main/kotlin/org/opentaint/dataflow/jvm/bidi/interp/JIRCallPlanBuilder.kt). The named ThingsBoard plan has 1,026 steps but 104 distinct tuples; the named Stirling-PDF plan has 1,460 steps but 452 tuples. Cleaner cost is secondary to the source forms in both sampled plans.

Share action recipes and immutable field read/write filter payloads. A field cleaner still receives a fresh temporary per cached action occurrence. Its reverse uses that same temporary. Do not share temporary identity across separate field actions. Kill steps retain their keep summaries.

Do not remove arbitrary repeated cleaners. For example, cleaning EXACT, then BELOW, then EXACT can have a different result if the last EXACT is removed. A prior step can change tail, layer or exclusion, so equality of tuple values does not establish redundant execution.

For a later generated-rewriter normalization, define a conservative block of mark-specific root cleaners with the same reach. Field steps, kill steps, all-mark steps and different reach values end the block. Within such a block, retain the first occurrence order and propose removal of repeated identical tuples only after tests prove full effect equivalence. Public action lists keep their supplied order and duplicates.

The proof must include F75: a selected cleaner in run 1 requests its mark on the same base FLOW even if that mark is already excluded, using the incoming premise. Requests, joins and publication deltas must have the same final effects; deleting an action cannot silently delete a required request. Keep the current `allRelevant=true` lookup and complete relevant mark set. Payload reuse is enabled independently of this action-removal gate.

## O7. Allocate fact-group storage on demand

Change [Groups](../../core/opentaint-dataflow-core/opentaint-dataflow/src/main/kotlin/org/opentaint/dataflow/bidi/ap/Groups.kt). Stirling-PDF contains 1,745,016 current conclusion groups. Their two maps use 167.52 MB shallow memory; their two-element reach arrays use 41.88 MB. These are concrete representation costs in live work.

Replace the reach array with two bits. Keep FLOW and TAINT storage separate, but allocate each base map on first use. Use a direct first base/group pair for the common single-base case, then promote to a map on a second distinct base. Preserve the current traversal order by kind and layer; define a stable spill-map order for any schedule-sensitive tie handling.

For each `FlowGroup` and `TaintGroup`, store NORMAL and DEMAND layers as separate empty/singleton/many states in direct fields. An empty layer requires no holder object. A first tree requires no list or backing array. Allocate a list on the second tree in that layer. Promotion, removal back to singleton, and empty state must leave no stale tree references.

Keep the existing group algorithms: match annotations, subsumption, exclusion re-keying, merge and delta publication. In particular, NORMAL exact taint and DEMAND exact/any taint retain their different merge rules. No new generic fact normalization is part of this change. Keep the store's soft interner and manager ownership unchanged.

Keep dense per-premise statement arrays initially. They use only 14.65 MB in the measured PDF actor union. Add counters for occupied slots and length. A later sparse container is justified only if its total array/index/lookup cost is lower in a measured workload. Any replacement must keep direct statement identity, publication deltas and subscriptions unchanged.

## O8. Reuse graphs at the analyzer boundary

Repeated virtual graph construction takes 15.00% of ThingsBoard run-1 samples and 18.76% / 22.27% of PDF run-1 / run-2 samples. [JTryBoundaryExceptionsApplicationGraph](../../core/opentaint-jvm-sast-dataflow/src/main/kotlin/org/opentaint/jvm/sast/dataflow/JTryBoundaryExceptionsApplicationGraph.kt) currently requests `flowGraph()` during successor and predecessor lookup.

Give each analyzer-owned `CutMethodGraph` one lazy raw graph snapshot. Reuse it for catch-boundary indexing, successors and predecessors. Reuse the method wrapper for that physical method within the analyzer session. Initialize the snapshot after the body and synthetic boundaries are installed.

Use the same stable forward snapshot where alias analysis and method graph construction need that view. The wired backward graph keeps its own view. `JIRMethodEntry` already caches forward and backward method instruction graphs; do not add a second redundant global cache. Cache context-independent exit descriptors and reuse them when creating context services.

Freeze each method body and its installed boundaries from the first `JIRMethodEntry` through the rest of that analysis session. The cache key is method identity within this frozen owner. A virtual method first created later can obtain its own frozen entry. Verify this lifetime on virtual and lambda workloads before enabling reuse.

A replacement of an already-used body requires a fresh analysis session with new graph, form and alias owners. Clearing only the raw snapshot is insufficient: cached form slots and alias arrays still refer to the old instructions and indexes. If an integration cannot provide this frozen lifetime, leave reuse disabled for that mutable entry until coordinated invalidation has a separate design.

Preserve try/catch cuts, throws, class-initialization behavior, normal and exceptional exits, synthetic boundaries, exit seeding and instruction indexes. Release the adapter with the analysis session. It must not capture actors, run managers or per-context facts. All changes stay in graph consumers and adapters; no IR storage or IR graph-provider change is proposed.

## O9. Compare queue priorities as primitives

Keep the existing `PriorityQueue<Entry>` in [EventQueue](../../core/opentaint-dataflow-core/opentaint-dataflow/src/main/kotlin/org/opentaint/dataflow/bidi/engine/EventQueue.kt). Replace `compareBy` with sequential primitive comparisons:

```text
compare rank as Int
if equal, compare steps as Long
if equal, compare sequence as Long
```

Use `Integer.compare` and `Long.compare`, not subtraction that can overflow. Build rank and steps with local variables instead of a temporary `Pair`. The entry still snapshots actor state once when it is inserted. Keep the zero-work priority, non-work priority, ordinary-work priority and FIFO sequence tie break. Do not read mutable actor state from the comparator. Preserve `UnitRunner`'s existing W2 resampling when it requeues an actor after a partial step.

This targets 7.64% of Conductor run-1 allocation weight and 6.92% in ThingsBoard. These are sampled allocations, not a claim that queue entries retain that share of the heap.

## O10. Iterate aliases without copying complete sets

Alias collection accounts for 7.52% of ThingsBoard run-1 allocation weight. Add a direct iteration API over the stable method alias result. Consumers can visit each alias path without first creating a set and then a list. Remove the repeated whole-DSU `allElements()` allocation and scan in `IntDisjointSets.forEachElementInSet` by using a published-state membership index. Streaming only the final converted list would leave this cost in place.

For callers that need a collection, provide an owned immutable view over a published alias-analysis result. Key reuse by method body generation, alias-analysis configuration, statement, local identity, before/after state and DSU identity. Keep any membership index within `JIRLocalAliasAnalysis` and release it with that owner. Build it only for published result states; a live DSU that can union or remove members needs invalidation and cannot use that frozen index.

Stream converted alias members directly. Cache a persisted before/after intersection view only for the same call and local, where the existing consumer requires that intersection. Do not return a live mutable collection or let a caller retain a scratch buffer used by the next lookup.

Preserve all aliases, their full paths, local-write restrictions and required stable iteration order. Field cleaning retains the strong read/write sequence and its temporary. Copy only at a public ownership boundary or when a consumer must retain a distinct mutable result. This change does not narrow alias precision or filter aliases by selected marks.

## O11. Reduce trie copies and short-lived views

[TrieOps](../../core/opentaint-dataflow-core/opentaint-dataflow/src/main/kotlin/org/opentaint/dataflow/bidi/ap/TrieOps.kt) subtraction accounts for 9.92% of Conductor run-2 allocation weight and 13.70% in PDF run 2. Leaf views and callback objects add work.

Add an internal owned-node builder. It can adopt a newly created array or share an existing immutable child array. Public/raw constructors keep defensive copies. A builder must never publish a scratch array that a later operation changes.

Use identity fast paths for empty input, identical operands where the operation permits it, and an unchanged result. Keep explicit iterative postorder construction, including the existing safety for paths 32,768 levels deep. Return the existing node when every child and annotation remains unchanged. Use copy-on-first-change child builders, so a change detection pass does not evaluate a filter or mapping callback twice. Apply this to merge and subtraction without changing delta calculation. Replace temporary leaf-view collections with direct iteration where the consumer needs only one pass. Replace repeated capturing callbacks with a stable visitor or direct loop in the measured hot paths.

Retain or extend memoization within one trie operation when shared subtrees cause repeated work. Subtraction keys keep node identity, stored-node identity and the inherited `above` value. Filter keys keep node and filter identity; limit keys keep node identity and remaining budget. A mode-dependent operation also includes annotations/exclusion policy, layer and any other input that affects the result. Memoize each callback result once. Release the memo at operation completion. Do not add a strong cross-run cache of fact trees. Keep the store's soft interner and the existing checkpoint behavior. Measure retained memo bytes as well as allocation savings.

Distinguish a memoized null result from a missing entry, so an empty result does not cause repeated evaluation. Leaf enumeration remains once per path, even when several paths share one DAG node. The existing callback API gives the consumer an owned path array. Keep that ownership contract. A visitor that borrows a reusable path buffer needs a separate private API whose consumer cannot retain the buffer.

## Implementation order

Each row is a separate reviewable change or small series. Record the parent commit and exact patch for each measurement. Do not combine algorithm changes with representation changes.

| Phase | Work | Required result before the next dependent phase |
| --- | --- | --- |
| 1 | Add size/count counters; O2 ownership factories; O3 empty/singleton fields | Same form occurrences and effects; lower retained payload/container cost |
| 2 | O2 shared vectors and repeated rewritten payloads; O3 compact indexes; O7 lazy groups | Ownership, join, filter, merge and delta tests pass; no new finding difference |
| 3 | O4 compact reverse blocks and AP descriptor application | Reverse effects, source hits, end triggers and F76 controls match the reference |
| 4 | O5 templates, validation and static matching caches; O6 cleaner payload sharing | Complete keys and invalidation tests pass; broad and relevant rule behavior unchanged |
| 5 | O1 normalization by rule kind/placement; gated O6 action removal | Full effect-equivalence gate passes for each enabled policy; unsupported cases retain original occurrences |
| 6 | O10 alias iteration and O11 trie allocation work | Alias precision, ownership, deltas and publication closure match |

O8 graph reuse and O9 queue comparison can be implemented as separate changes from phase 1. They need no rule-normalization change. Re-profile after the memory phases before choosing a larger trie or sparse-array rewrite. Do not infer savings from a reduced object count alone.

## Validation and acceptance

### Core tests

Use the existing test suites and add cases for changed contracts. The tests must assert effects, not reproduce a container's implementation.

| Area | Required cases and existing suites |
| --- | --- |
| Ownership and form identity | Mutate caller inputs after construction; concurrent payload creation if supported; distinct equal-content joins; stable direction slots. Extend `FormsCacheTest`, `CallPlanTest`, `SummaryKindsTest`, `ManagerOwnershipTest` and JVM form tests. |
| Conditions | Invalid raw discarded branches; source/sink `P AND NOT P`; signed cleaner assumptions; independent AnyArgument bindings; ordered/repeated slots; array root, element and nested-element choices. Extend `JIRRuleFormsTest`, `JIRCallPlanTest`, `JIRCleanerMappingTest` and `JIRLoweredCleanerEffectTest`. |
| Absorption | Compare `A OR (A AND B)` with delayed/missing inputs in both layers and with distinct exclusions/supports. Test the exit `S.a/A OR (S.a/A AND S.b/B)` with only zero-premise `S.b/B`: its original read/drop effect must remain. Include later `A`, end triggers and source action multiplication. |
| Joins and reverse blocks | Delayed inputs, equal-content distinct occurrences, NORMAL/DEMAND mixtures, admitted/excluded must facts, forward ANY pass, all reverse branches DEMAND, A5 identities and backward no-must invariant. Extend `ConjunctionStoreTest`, `LocalConjunctionTest`, `StatementSummaryReversalTest` and `CallPlanReversalTest`. |
| Seeds and provenance | Original forward source edge value and statement/context; reversed end-fact seeds for CONFIRMED results; once-per-occurrence trigger; stable sink requirements. Extend `CallSourceHitTest`, `SourceHitStoreTest`, `SeedIndexTest` and `FrontierSeedIndexTest`. |
| Cleaners and groups | Every exclusion-table case, F75 requests on an already excluded mark with the incoming premise, repeated EXACT/BELOW/EXACT, singleton promotion/removal, layer-specific merging and delta publication. Extend `CleanerTest`, `GroupMergeTest` and `TaintGroupKeysTest`. |
| Graphs and queue | One snapshot per frozen body, fresh graph/form/alias owners in a new session after body replacement, virtual/lambda bodies, try/catch cuts and both exits; priority ties, large step values and mutable actor state after insertion. Extend graph/driver integration tests and `EventQueueTest`. |
| Aliases and tries | Complete alias path sets, field read/write effects, unchanged node reuse with no mutation, annotated merge/subtract, one callback evaluation per operation state, shared nodes under different inherited/filter/budget states, cached null results, retained callback paths, 32,768-level paths, delta and publication behavior. Extend alias tests, `TrieTest`, `TrieMergeDeltaTest`, `PathTrieTest` and summary storage tests. |

For each changed compiler policy, compare the legacy and proposed implementation on small complete engine workloads. Compare finite closures, requests, raw summaries, source hits, triggers, witness supports and report states under several delivery schedules. For representation changes, require exact logical equality. For alternative removal, document the origin mapping and prove that it preserves every required effect; differences cannot be waived as mere numbering changes.

Run the affected common/JVM suites and then the required broader analyzer/query suites. Record known failures from the control build. Do not add ignores or loosen assertions to pass an optimization. Keep F74/F75/F76 and driver/barrier controls in the gate.

### Measurements

Add opt-in counters for raw products, candidate/final shapes, logical occurrences, literal memberships, distinct/shared payloads, summary field sizes, index sizes, reverse branches, compiled descriptors, cleaner occurrences/unique tuples, group promotions, graph builds, alias copies and trie node reuse. Record pool/memo hits, misses and retained entries. Avoid expensive per-event logging in normal operation.

Run OWASP, Conductor, ThingsBoard and Stirling-PDF with the exact configurations in the input inventory, through the direct Gradle `runProjectAnalyzer` task. Keep the 8/8/12/8 GiB heaps and 1000/1080/1080/1080 second IFDS limits. Keep the rules, source heads and model inputs fixed. Keep run 1 at limit 1.

For each change, record completion, prescan time, each run and barrier time, GC pauses, peak heap/RSS, handled/pending events and finding comparison. Capture CPU and allocation profiles for seconds 60–90 after IFDS starts when the scan lasts that long, plus the final 30 seconds or the near-limit interval if it occurs. Record a shorter available interval explicitly.

Keep timing controls separate from live-heap capture runs. The memory dump forces GC and pauses the process. Compare live-heap owner unions, object counts and cache paths at comparable analysis stages. A completed scan may retain more useful work than an earlier failed scan; report progress and peak memory together. Include the new pool/index/descriptor owners in the heap analysis.

The final performance gate is completion of all four workloads within their existing budgets, with no new finding regression. Compare unique sink identities and retain strict source locations. Keep the two known PDF exit-location differences visible until a separate reporting fix resolves them. Inspect added findings after refinement completes; the current incomplete reports cannot establish a final false-positive rate.

If a change reduces a measured cost but a project still stops at the memory guard, report it as a partial improvement. Do not call a shorter failed run faster. Do not claim a fixed number of GB saved from overlapping retained unions. Use the next heap/profile to select the next change.

## Main risks and limits

The largest semantic risk is deleting alternatives based only on Boolean laws. The largest cache risk is sharing a value under a key that omits method binding, selection generation, result kind, alias context or manager ownership. The largest representation risk is reintroducing all branch objects through a compatibility getter or retaining scratch arrays after publication.

The design handles those risks by sharing immutable storage before changing logical occurrences, keeping complete scoped keys, and testing effects in both directions. The remaining IR cost is outside this scope. These core changes can reduce the measured owners, but only the fixed project repeats can establish that they remove the memory failures.
