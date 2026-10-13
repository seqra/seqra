# Bidirectional analyzer core: memory and allocation design

Status: superseded. Original date: 12 October 2026. Use the
[new proposal based on the latest measurements](bidirectional-core-optimization-20261013.md).
The priorities and memory estimates below describe the older, pre-O1–O3 analyzer. They are stale for
current optimization selection. They remain here as historical design evidence.

The main changes are to keep source and sink conditions in NNF and to make method-form caches reclaimable through managed soft references. These changes remove eager condition products and let the memory manager discard compiled forms that can be rebuilt. Smaller containers and shared payloads follow. Keep the selected project settings and the analysis effects. Apply each change separately, so its result can be measured and its cause can be identified.

This proposal covers the analyzer core, its JVM interpreter and graph adapters, and rule compilation and resolution. IR storage, IR tables, decoded IR objects and changes inside `core/opentaint-ir` are out of scope. Graph reuse below occurs in an analyzer-owned adapter. It does not change the IR provider.

O1, O2 and O3 are selected for implementation. Use the [detailed implementation proposal](bidirectional-o1-o3-implementation-proposal.md) and the [implementation plan](bidirectional-o1-o3-implementation-plan.md) for that work. O4–O12 remain outside the selected scope.

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

The following order is the expected retained-memory impact after the preceding changes. It is a design estimate, not a measured savings table. The order within a band can change by project. CPU and allocation savings are shown separately because they do not establish a retained-memory saving.

| Rank / ID | Optimization | Expected retained-memory impact | Evidence and limits |
| --- | --- | --- | --- |
| O1 | NNF predicates for both source and sink rules; no compiled cubes | Very high | Removes eager sink products in OWASP and source cube/action products in ThingsBoard and PDF. Active support combinations can still be large. |
| O2 | Managed soft references for rebuildable method forms | Very high, depends on the live working set | Targets the 2.41–6.46 GB strong method-cache owners. Current application state and necessary supports remain strong. |
| O3 | Compact summary and call-plan containers | High across projects | Five collection classes account for 1.36 GB in Conductor and 3.16 GB in ThingsBoard forms. Those totals are not fully recoverable. |
| O4 | Shared immutable predicate, action and path payloads | High, less after O1 | Removes copies across source/sink rules, targets and call sites. Managed caches must also make payload pools reclaimable. |
| O5 | Shared rule templates, validation and static matching | High in ThingsBoard; medium across projects | ThingsBoard's rule-configuration union retains 1.35 GB. Keep broad rule applicability and method binding. |
| O6 | Lazy fact-group containers and reach bits | Medium, mainly PDF | About 167.52 MB of group maps and 41.88 MB of reach arrays, plus eager layer lists. |
| O7 | Shared cleaner tuples and gated action reduction | Low to medium | Large repeated step counts in sampled plans, but source forms dominate those plans. |
| O8 | Compact residual reverse transfers | Low after O1; conditional | NNF backward leaf traversal removes the main expanded-conjunction cost. Do this only for measured remaining legacy/custom transfers. |
| O9 | Trie reuse, owned builders and direct traversal | Retained saving unmeasured; allocation benefit measured | PDF has 2.42 million active trie nodes. Subtraction has 9.92% / 13.70% sampled allocation shares in Conductor / PDF. |
| O10 | Alias membership indexes and direct iteration | Retained saving unmeasured; mainly allocation | 7.52% of ThingsBoard allocation weight; an index can add retained memory. |
| O11 | Analyzer-owned graph reuse | Mainly CPU/allocation; can add retained memory | Repeated graph construction is a measured hot path. Reuse one required view instead of adding graph copies. |
| O12 | Primitive queue comparison | Negligible retained saving; allocation benefit | Boxing contributes 7.64% / 6.92% of allocation weight in Conductor / ThingsBoard. |

O1, O2 and O4 overlap. Do not add their expected effects. In particular, the current reverse-branch saving is largely part of O1 once cubes are removed. Dense per-premise arrays are a conditional follow-up under O6; their measured size does not justify changing them first. Exit source-location mapping remains a separate reporting defect.

## Contracts for every change

Use the [interpreter specification](../../spec/interpreter.md), [AP specification](../../spec/ap.md), [core specification](../../spec/analyzer-core.md) and [validation plan](../../spec/validation-plan.md) for observable behavior. Two implementation contracts need explicit updates: numbered expanded alternatives become stable expression/proof handles, and strongly cached form-object identity becomes stable logical identity across cache rebuilds. Update the relevant sections of [AP implementation](../../spec/ap-impl.md) with those changes. This proposal does not yet edit the specifications.

1. Run 1 has field limit 1. Later runs use increasing positive limits. There is no limit-0 run. Preserve the configured heap, timeout, run direction and barrier rules.
2. Apply the specified exclusion table without change. Preserve NORMAL and DEMAND layers, FLOW and TAINT kinds, premise sets, type filters, mark policies and F74/F75/F76 behavior. Class accessors retain their specified field-limit treatment.
3. Keep distinct rule/action and proof scopes. NNF can share pure leaf checks and immutable support data, but must not combine incompatible OR branches or supports from different rule occurrences, statements or method contexts.
4. Keep source and sink rule ownership, action ownership and witness support. Seed and report keys use stable logical rule/leaf/proof handles, independent of a compiled object's address or cache rebuild epoch. Preserve ordered support when existing confirmation or report code needs it.
5. Preserve source admission and source hits by full `(MethodKey, statement, original forward PathEdge)` value. End-fact actions keep their own admission and trigger rules. A reversed end fact fires the owning predicate's full positive leaf scope once per method key, statement and rule occurrence, including for a CONFIRMED result. GEN end facts carry no OR-proof correlation.
6. Keep public defensive ownership. A caller cannot change a cached form by changing an input array, list, set or map. Internal factories can reuse a value only after it is frozen and owned.
7. Keep the required relevant-rule lookup and mark universe. In particular, retain `allRelevant=true` for rewriter rules. Do not introduce an alternative cap, a queue cap, a selected-mark shortcut or an arbitrary field-limit change.

Representation changes must preserve effects exactly. An effect includes output facts, layer, exclusion, premise support, requests, source hits, end triggers, raw summaries and report state. Boolean equivalence alone does not establish this contract for a rule compiler.

## O1. Evaluate source and sink conditions in NNF

Change [JIRRuleForms](../../core/opentaint-dataflow-core/opentaint-jvm-dataflow/src/main/kotlin/org/opentaint/dataflow/jvm/bidi/interp/JIRRuleForms.kt) to use explicit compiler stages:

```text
raw rule
  -> whole-rule validation
  -> raw signed NNF and Boolean folding
  -> per-place rewrite of static non-mark conditions
  -> signed Boolean folding again, including resolved array contradictions
  -> mark policy for this rule kind
  -> owned NNF expression DAG and leaf index
  -> source actions or sink/end-fact actions bound to its root
```

Validate all raw actions and all mark literals, including negative literals, before simplification can remove them. Invalid positions or actions must still produce the current rule error. Validation and a condition that rewrites to false are different results.

### Rule-kind policy

Fold signed NNF before static rewriting and again before source/sink negative-mark approximation. `P AND NOT P -> false` is a correct optimization and is required. Fold constants, duplicate operands and complements; use non-distributing propagation to detect resolved array forms such as `(P0 OR P1) AND NOT P0 AND NOT P1`. Only afterward do remaining negative mark literals count as true for sources and sinks. Fold constants again after that projection. A known contradiction cannot become `P` by first removing its negative literal, and approximate static evaluation cannot erase its raw complement before checking.

A condition that folds to false has no source, sink, request, seed, trigger or exit read/drop effect. Validate its complete raw rule first. For a condition that remains active, retain its positive read/drop footprint before any optional positive absorption.

Cleaners retain signed conditions and the cleaner normalization already taken from `saloed/cleaner-action-normalisation`. Preserve its action-specific assumptions: exact named removal can assume its exact mark; terminal `AnyField` removal can assume its field mark; a non-`AnyField` `ExactAndAnyField` action cannot assume a root mark. A remaining mark-dependent cleaner condition contributes no cleaner and raises no request. Pass rules retain their current logged mark-condition exception. Do not apply the source/sink positive policy to these kinds.

Relevant-rule resolution has its own policy for negative method, class and arity conditions. Keep that policy and distinguish it from ordinary rule resolution in all cache keys.

### One predicate model for both rule kinds

NNF is sufficient for the compiled representation. Keep AND, OR, true/false and mark leaves; do not distribute AND over OR. Add a `RulePredicate` used by both source and sink rule occurrences:

```text
RulePredicate(root, frozen nodes, leaf descriptors, base/mark index)
SourceRuleOccurrence(id, predicate, ordered target/action descriptors)
SinkRuleOccurrence(id, predicate, report owner, end-fact descriptors)
```

`SinkRule` and `ConjunctiveEdge` describe the current lowering. They must not define the new shared predicate model. Source rules can have the same complex mark conditions as sinks. Evaluate a source predicate once for its bound rule and retain separate action handles when emitting its targets. Apply each target's filters and class/static rules separately.

For a call sink's eligible argument leaf, represent root versus element as a small OR node. There is no Cartesian product of choices across leaves. Keep the current array policy: only positive argument literals at calls whose actual static type can be an array. Receiver, result, entry and exit positions are unchanged. A resolved element path can receive one more element prefix as specified; do not erase that prefix.

Give nodes and leaf occurrences stable IDs in the frozen bound recipe. Sharing an equal leaf payload does not erase its logical occurrence. Index leaves by base and mark to avoid checking the whole expression for each incoming fact. FLOW has no mark: dispatch it to all required leaves on that base so their request policies still apply.

### Forward: incremental leaf evaluation with support

Evaluate relevant mark leaves against the incoming fact using the existing AP rules. A stateless evaluation of the whole expression against that fact is insufficient: `A@arg0 AND B@arg1` must fire after the two facts arrive separately, in either order.

Keep actor-owned predicate state keyed by rule occurrence, method context and statement. A leaf contribution contains its premise, effective layer, checked fact/exclusion and proof handle. Store contributions until the run ends. Propagate only new contributions upward:

| Node | Incremental operation |
| --- | --- |
| Leaf | Store the admitted checked support; FLOW raises the specified request and is not a satisfied mark. |
| OR | Forward each child proof separately, with its branch choice. Do not union unrelated branch supports. |
| AND | Join support from every child; union premises without zero, and use DEMAND if any contributing support is DEMAND. Keep each child's checked support and branch choices. |
| True | Use the existing zero/unconditional placement behavior. |
| Root | Emit the source action results or a sink witness and its end-fact actions. |

Represent support as a shared proof DAG: leaf support, OR choice and AND child proofs. Do not flatten it into an inventory of all possible cubes. Expand an ordered witness support vector only where confirmation or existing summary code needs it. Keep a stable structural proof key for an observed combination. Memoize exact repeated proofs, not different premise/layer/exclusion combinations.

Within one proof, repeated equal literal requirements bind the same support variable. When AND joins child proofs that mention the same requirement, their bindings must agree. `A AND A` must not combine two different inputs for `A` into a new two-premise source result. Canonicalize exact duplicate root support shapes reached through different OR paths, while retaining the branch provenance needed by seeds. This is deduplication of observed support, not enumeration of potential cubes.

NNF sharing removes static form multiplication, but an actual AND join can still have many distinct premise combinations. Do not cap them or promise linear active-state memory. Materialize only combinations needed by real arrivals and existing downstream summary semantics, with cancellation checks.

Preserve transfer policy as well as Boolean truth. A unary conditional source currently uses an ordinary statement micro edge, including path/suffix transfer, exclusion behavior and static POSITION requests. Apply that generic transfer to its supported input; do not replace it with unconditional target construction. A proof with two or more distinct positive literals uses conjunction MARK checks and the existing target construction. Annotate the expression's symbolic support classes and use the appropriate policy; a single generic leaf check cannot replace both. Derive those classes from distinct requirements, not the number of syntactic AND children. For a predicate that contains both classes, retain both applicable check modes.

At normal exits, retain the original post-static positive read/drop footprint independently of root satisfaction. Under G2/D30, an exit sink can store and drop zero-premise static `B` in `A OR (A AND B)` before any `A` arrives. The NNF evaluator performs those leaf effects even if no complete proof exists. It retains caller-set state according to the current rule. This does not require cubes.

### Backward: walk the expression leaves

Walk the applicable predicate/proof scope and emit its mentioned positive mark requirements with their full base, path and tail. OR does not require a backward join: visit its branches. AND emits its child requirements. Deduplicate equal requirement values for propagation while retaining their rule/proof provenance. The result is a leaf walk, not reverse construction of every DNF cube.

For a conditional source, start from the requirement on its action target and apply the existing target-to-premise reversal to the leaves. A unary proof uses ordinary reversal; a multi-literal proof forces every branch DEMAND. Thus `A OR (A AND B)` can require an ordinary `A` branch and DEMAND `A`/`B` branches. Mark names alone are not enough to encode this distinction. Preserve generic tail/exclusion rules and the backward no-must invariant.

For sinks, store a seed descriptor with logical condition lookup keys that emits its exact or may leaf requirements when visited. Handoff retains witnessed method contexts and seeds every active positive branch of each matching sink condition, including unwitnessed OR branches. Proof scope remains in witness confirmation; emitting OR requirements does not allow a later forward AND to fire with incomplete support. A reversed end fact also demands the owning predicate's full positive leaf scope, because GEN output carries no proof correlation. Fire that scope once, also for CONFIRMED results. End-fact handling remains separate from unconditional source-hit admission.

Use structural expression/proof handles instead of a prebuilt array of numbered alternatives. A proof's OR choices and leaf support order give its identity; no table of all combinations is needed. This requires coordinated updates to conjunction stores, witness shapes, seeds, triggers and the specification's current alternative-number contract. Keep a legacy cube evaluator only as a test oracle during migration.

### Optional simplification

The OWASP expression `(W OR B) AND W AND B` currently creates 210 raw DNF combinations. NNF stores that expression directly and shares `W` and `B`; saving its form multiplication does not depend on absorption. Positive absorption to `W AND B` is a later optimization.

Keep the cleaner and pass policies above. Before removing any positive branch, prove that requests, source support, witness support, end triggers and exit read/drop effects remain equivalent. A smaller Boolean expression alone is not sufficient. NNF already removes the need to compile cubes even when such a branch must remain.

## O2. Use managed soft references for method forms

Register a dedicated category through the existing [RefManager](../../core/opentaint-dataflow-core/opentaint-dataflow/src/main/kotlin/org/opentaint/dataflow/util/RefManager.kt): `refManager.softRefManager("bidi-method-forms")`. Use its [SoftReferenceManager](../../core/opentaint-dataflow-core/opentaint-dataflow/src/main/kotlin/org/opentaint/dataflow/util/SoftReferenceManager.kt) for rebuildable cache payloads. This connects form reclamation to the memory manager's existing cleanup and re-enable cycle. An ordinary JVM soft reference alone is not sufficient for that explicit cleanup path.

Keep `JIRMethodEntry` and `JIRMethodForms` as small strong owners of keys and rebuild recipes. Change statement, exit, entry and call-plan cache slots to managed references to a form payload containing the forward form and its lazy backward view. Do not place all method plans in one large strongly pinned bundle. A slot or small chunk lets unused forms be reclaimed independently.

```text
FormKey(method/context, statement or boundary kind, configuration/body version)
  -> managed Reference<FormPayload>
FormPayload
  -> forward form
  -> backward view, if built
```

On access, read the referent once into a strong local lease. On a miss, rebuild from the stable recipe and store a managed reference. `createRef()` returns null while the manager is disabled; return the rebuilt form for this access without a strong cache fallback. Pin the payload only for construction, application and any queued continuation that directly uses it. Release that lease when the work completes. A live actor does not need to pin every form it has ever visited.

The cache change itself is small. The required identity fix is explicit: today standing joins key `SinkRule`/`ConjunctiveEdge` objects, trigger sets keep `SinkRule`, and sink guards compare object identity. Rebuilding those objects mid-run must not split a join or fire a trigger again. Replace these keys/comparisons with stable logical rule, action, node and proof handles from O1. The rebuild epoch is not part of a logical ID. Do not add a strong registry of all old wrappers to preserve identity; that would defeat reclamation.

For legacy forms during migration, derive occurrence IDs deterministically from the form key and source rule/action/slot recipe. Keep forward/backward object references consistent within one leased payload. Persistent source seeds retain a complete immutable forward-edge value without compiled state; source admission already uses edge value equality. Persistent witnesses retain report owner, proof/support headers and end-fact values, not a whole compiled form.

Field-action temporary identity also must survive rebuilds. Use a deterministic owner/action occurrence key for the temporary, distinct across separate actions and shared by both directions. Either give the temporary value equality by that key or retain a compact temporary token in the stable action recipe. Do not retain its read/write summaries just to keep the token alive. Compiled paths remain AP-manager-owned and can be rebuilt within that same manager.

Keep builds on the existing slot owner. For a cache shared by concurrent contexts, serialize construction per slot or publish only a complete payload; a losing build must not change logical IDs. Cleanup can clear the cached reference during application, but the local lease keeps the active payload valid. Never read `reference.get()` twice to obtain two parts of one operation.

Queued subscriptions keep call-site metadata and reacquire the payload when resumed. They keep a strong lease only while they directly use a compiled form. Preserve this rule at run barriers and cancellation. Make reference registration and the transition to disabled caching atomic, so a reference cannot be registered after the cleanup batch has already been drained. Prune cleared-reference bookkeeping; millions of empty reference wrappers must not become a new cache cost.

Audit all strong paths: cache builders/closures, validation maps, payload pools, guards, trigger sets, joins, witnesses and seeds. Stable logical metadata and live support remain strong; unused compiled forms and indexes become reclaimable. O4 pools use managed references or payload-scoped ownership too. A strong global intern table of all paths would retain much of the discarded form data.

The tradeoff is more compilation after eviction. Measure hits, misses, rebuild time, registered references and memory released after explicit cleanup. Frequent rebuilds can trade an OOM for a timeout, so keep the fixed time limits in the acceptance test. This change helps most when the compiled cache is larger than the current working set; its full owner size is not a guaranteed saving.

## O3. Use compact immutable form containers

Change [StatementSummary](../../core/opentaint-dataflow-core/opentaint-dataflow/src/main/kotlin/org/opentaint/dataflow/bidi/interp/StatementSummary.kt) and [CallPlan](../../core/opentaint-dataflow-core/opentaint-dataflow/src/main/kotlin/org/opentaint/dataflow/bidi/interp/CallPlan.kt) internally. Preserve their read-only collection interfaces during migration.

Use frozen vectors with empty, singleton and array-backed forms. Use compact base-key tables for touched bases, targets and filters. A zero-entry table has no backing map. A one-entry table stores its key/value directly. Larger tables keep ordered keys and values; use linear lookup for small tables and a lookup index only above a threshold selected by a lookup/allocation benchmark. Do not allocate a `LinkedHashMap` and wrapper for each absent field.

Keep one ordered edge vector. Build a base-to-ordinal index over it, rather than separate list objects containing the same edges. A base lookup reuses a read-only view or directly iterates the indexed ordinals; it must not allocate a new view on every lookup. Do the same for `CallPlan.stagesFrom`, using the small fixed `CallPoint` set and stage ordinals. Preserve stage order and the rule that stages from one point read the same input.

Share individual empty fields. A summary is wholly empty only if every semantic field is empty, including touched bases. A kill summary with no edges still has an effect. GEN summaries, statement summaries, stage summaries and pass-over identities keep their separate construction rules. Filter combination retains the full type predicate and mark policy.

Use a private builder that owns its new arrays and can pass them to a frozen form without a second copy. The public constructor still makes one defensive copy. `withIdentities` can reuse existing frozen payloads and append a new owned segment, rather than copy all nested containers.

This change targets the 61% collection-class cost in Conductor and ThingsBoard. It also removes containers around empty end-fact lists. Do not predict that all of those bytes can be recovered: nonempty indexes and iteration views still have a cost.

## O4. Share immutable source/sink and action payloads

Introduce internal owned values for paths, literal vectors, targets and end-fact vectors. Freeze a value once at the compiler boundary. Empty and singleton vectors use compact representations. Their elements do not carry a reference back to the pool that created them.

Use two levels of ownership:

| Level | Shared data | Data that remains separate |
| --- | --- | --- |
| Symbolic rule | Position/path descriptors, mark names, expression nodes, action recipes | Original rule identity, metadata, binding and placement |
| Runtime form | Manager-owned path/fact/pattern payloads, NNF nodes, action/target vectors and end-fact vectors | Source/sink rule handles, action handles, statement and proof/trigger ownership |

Both `SourceRuleOccurrence` and `SinkRuleOccurrence` reference shared predicates. Source actions share target descriptors where equal; sink actions share end-fact descriptors where equal. Share leaf checks and support computation within the same bound rule where their AP policy is equal. Keep action results, rule report ownership and proof scopes distinct. There are no source cubes or sink-alternative vectors in the target model.

Runtime value keys include base, complete path, tail, mark and exclusion. They belong to one AP manager and one analysis. Symbolic keys use symbolic positions and mark names, not manager-local numeric IDs. In particular, `PathEdge.compiled` is mutable and manager-bound. Do not put a compiled `PathEdge` in a global structural pool. Keep compiled transfer state in its current owner or an explicitly manager-owned descriptor.

Start with compiler-local or method-local payload pools. Use an analysis-wide pool only for payload families with measured reuse across methods. Make its complete key/value table reclaimable through managed references, or use weak entries whose keys do not strongly retain large path graphs. Keep no actors, subscriptions or run stores in these pools. Track unique values, references, lookup cost and retained pool bytes. A miss produces a normal owned value and changes no semantics.

Reuse already-owned paths when creating sink seeds and reversal descriptors. Public constructors still snapshot caller-owned data. Add a private trusted construction path; do not expose a public flag that lets a caller skip ownership checks.

Complete rewritten form sharing uses a different key from leaf-value sharing. Include the original rule owner, rule kind, method binding, rewritten condition, action specialization, result base, relevant/ordinary mode, configuration generation and actual call array mask. Include context-dependent filters or alias bindings when the payload contains them. Normal and exceptional results remain distinct. Share the payload under this key; retain occurrence wrappers at each placement.

Both cache directions refer to the same stable logical recipe. Their compiled payloads can be reclaimed and rebuilt under O2. Object identity is local to a leased payload; cross-run provenance uses logical handles.

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

## O6. Allocate fact-group storage on demand

Change [Groups](../../core/opentaint-dataflow-core/opentaint-dataflow/src/main/kotlin/org/opentaint/dataflow/bidi/ap/Groups.kt). Stirling-PDF contains 1,745,016 current conclusion groups. Their two maps use 167.52 MB shallow memory; their two-element reach arrays use 41.88 MB. These are concrete representation costs in live work.

Replace the reach array with two bits. Keep FLOW and TAINT storage separate, but allocate each base map on first use. Use a direct first base/group pair for the common single-base case, then promote to a map on a second distinct base. Preserve the current traversal order by kind and layer; define a stable spill-map order for any schedule-sensitive tie handling.

For each `FlowGroup` and `TaintGroup`, store NORMAL and DEMAND layers as separate empty/singleton/many states in direct fields. An empty layer requires no holder object. A first tree requires no list or backing array. Allocate a list on the second tree in that layer. Promotion, removal back to singleton, and empty state must leave no stale tree references.

Keep the existing group algorithms: match annotations, subsumption, exclusion re-keying, merge and delta publication. In particular, NORMAL exact taint and DEMAND exact/any taint retain their different merge rules. No new generic fact normalization is part of this change. Keep the store's soft interner and manager ownership unchanged.

Keep dense per-premise statement arrays initially. They use only 14.65 MB in the measured PDF actor union. Add counters for occupied slots and length. A later sparse container is justified only if its total array/index/lookup cost is lower in a measured workload. Any replacement must keep direct statement identity, publication deltas and subscriptions unchanged.

## O7. Share cleaner values before removing actions

Use an owned immutable cleaner payload keyed by `(base, complete path, reach, mark-or-all-marks)`. Reference it from each ordered action occurrence. This targets the repeated tuples in [JIRCallPlanBuilder](../../core/opentaint-dataflow-core/opentaint-jvm-dataflow/src/main/kotlin/org/opentaint/dataflow/jvm/bidi/interp/JIRCallPlanBuilder.kt). The named ThingsBoard plan has 1,026 steps but 104 distinct tuples; the named Stirling-PDF plan has 1,460 steps but 452 tuples. Cleaner cost is secondary to the source forms in both sampled plans.

Share action recipes and immutable field read/write filter payloads. A field cleaner still receives a fresh temporary per cached action occurrence. Its reverse uses that same temporary. Do not share temporary identity across separate field actions. Kill steps retain their keep summaries.

Do not remove arbitrary repeated cleaners. For example, cleaning EXACT, then BELOW, then EXACT can have a different result if the last EXACT is removed. A prior step can change tail, layer or exclusion, so equality of tuple values does not establish redundant execution.

For a later generated-rewriter normalization, define a conservative block of mark-specific root cleaners with the same reach. Field steps, kill steps, all-mark steps and different reach values end the block. Within such a block, retain the first occurrence order and propose removal of repeated identical tuples only after tests prove full effect equivalence. Public action lists keep their supplied order and duplicates.

The proof must include F75: a selected cleaner in run 1 requests its mark on the same base FLOW even if that mark is already excluded, using the incoming premise. Requests, joins and publication deltas must have the same final effects; deleting an action cannot silently delete a required request. Keep the current `allRelevant=true` lookup and complete relevant mark set. Payload reuse is enabled independently of this action-removal gate.

## O8. Compact remaining reverse transfers only if measured

The current reversal creates a `MicroEdge`, two `PathEdge` objects and two endpoint facts per conjunction literal: 168 shallow bytes before indexes and compiled state. One measured PDF source stage has 24,619 literal memberships and over 4.13 MB of these branch objects. That is a current local example, not a measured whole-project saving.

O1 removes the main cause by traversing source conditions directly in backward mode. Do not build a new reverse-block subsystem first and then replace it with the NNF evaluator. This optimization is ranked below the shared cache, form, rule and group changes for the expected remaining workload.

After O1, profile any remaining legacy/custom conjunction forms and ordinary transfer reversal. If a significant cost remains, use a compact transfer descriptor that refers to owned forward endpoints and literal slots, indexed by target base. Apply it through [ApOps](../../core/opentaint-dataflow-core/opentaint-dataflow/src/main/kotlin/org/opentaint/dataflow/bidi/ap/ApOps.kt) without caching synthetic edge/fact objects per branch. Share manager-owned path compilation where valid.

Preserve force-DEMAND for multi-literal reversal, generic reversal for unary edges, forward `may` and source classification, the no-must invariant, A5 identities and filter/guard removal. Keep end-fact trigger handles. F76 native record views remain separate; the backward engine still has no ND conjunction join. If NNF removes this cost, omit this follow-up entirely. Re-rank it against cleaner storage if a new heap shows large residual transfers.

## O9. Reduce trie copies and short-lived views

[TrieOps](../../core/opentaint-dataflow-core/opentaint-dataflow/src/main/kotlin/org/opentaint/dataflow/bidi/ap/TrieOps.kt) subtraction accounts for 9.92% of Conductor run-2 allocation weight and 13.70% in PDF run 2. Leaf views and callback objects add work.

Add an internal owned-node builder. It can adopt a newly created array or share an existing immutable child array. Public/raw constructors keep defensive copies. A builder must never publish a scratch array that a later operation changes.

Use identity fast paths for empty input, identical operands where the operation permits it, and an unchanged result. Keep explicit iterative postorder construction, including the existing safety for paths 32,768 levels deep. Return the existing node when every child and annotation remains unchanged. Use copy-on-first-change child builders, so a change detection pass does not evaluate a filter or mapping callback twice. Apply this to merge and subtraction without changing delta calculation. Replace temporary leaf-view collections with direct iteration where the consumer needs only one pass. Replace repeated capturing callbacks with a stable visitor or direct loop in the measured hot paths.

Retain or extend memoization within one trie operation when shared subtrees cause repeated work. Subtraction keys keep node identity, stored-node identity and the inherited `above` value. Filter keys keep node and filter identity; limit keys keep node identity and remaining budget. A mode-dependent operation also includes annotations/exclusion policy, layer and any other input that affects the result. Memoize each callback result once. Release the memo at operation completion. Do not add a strong cross-run cache of fact trees. Keep the store's soft interner and the existing checkpoint behavior. Measure retained memo bytes as well as allocation savings.

Distinguish a memoized null result from a missing entry, so an empty result does not cause repeated evaluation. Leaf enumeration remains once per path, even when several paths share one DAG node. The existing callback API gives the consumer an owned path array. Keep that ownership contract. A visitor that borrows a reusable path buffer needs a separate private API whose consumer cannot retain the buffer.

## O10. Iterate aliases without copying complete sets

Alias collection accounts for 7.52% of ThingsBoard run-1 allocation weight. Add a direct iteration API over the stable method alias result. Consumers can visit each alias path without first creating a set and then a list. Remove the repeated whole-DSU `allElements()` allocation and scan in `IntDisjointSets.forEachElementInSet` by using a published-state membership index. Streaming only the final converted list would leave this cost in place.

For callers that need a collection, provide an owned immutable view over a published alias-analysis result. Key reuse by method body generation, alias-analysis configuration, statement, local identity, before/after state and DSU identity. Keep any membership index within `JIRLocalAliasAnalysis` and release it with that owner. Build it only for published result states; a live DSU that can union or remove members needs invalidation and cannot use that frozen index.

Stream converted alias members directly. Cache a persisted before/after intersection view only for the same call and local, where the existing consumer requires that intersection. Do not return a live mutable collection or let a caller retain a scratch buffer used by the next lookup.

Preserve all aliases, their full paths, local-write restrictions and required stable iteration order. Field cleaning retains the strong read/write sequence and its temporary. Copy only at a public ownership boundary or when a consumer must retain a distinct mutable result. This change does not narrow alias precision or filter aliases by selected marks.

## O11. Reuse graphs at the analyzer boundary

Repeated virtual graph construction takes 15.00% of ThingsBoard run-1 samples and 18.76% / 22.27% of PDF run-1 / run-2 samples. [JTryBoundaryExceptionsApplicationGraph](../../core/opentaint-jvm-sast-dataflow/src/main/kotlin/org/opentaint/jvm/sast/dataflow/JTryBoundaryExceptionsApplicationGraph.kt) currently requests `flowGraph()` during successor and predecessor lookup.

Give each analyzer-owned `CutMethodGraph` one lazy raw graph snapshot. Reuse it for catch-boundary indexing, successors and predecessors. Reuse the method wrapper for that physical method within the analyzer session. Initialize the snapshot after the body and synthetic boundaries are installed.

Use the same stable forward snapshot where alias analysis and method graph construction need that view. The wired backward graph keeps its own view. `JIRMethodEntry` already caches forward and backward method instruction graphs; do not add a second redundant global cache. Cache context-independent exit descriptors and reuse them when creating context services.

Freeze each method body and its installed boundaries from the first `JIRMethodEntry` through the rest of that analysis session. The cache key is method identity within this frozen owner. A virtual method first created later can obtain its own frozen entry. Verify this lifetime on virtual and lambda workloads before enabling reuse.

A replacement of an already-used body requires a fresh analysis session with new graph, form and alias owners. Clearing only the raw snapshot is insufficient: cached form slots and alias arrays still refer to the old instructions and indexes. If an integration cannot provide this frozen lifetime, leave reuse disabled for that mutable entry until coordinated invalidation has a separate design.

Preserve try/catch cuts, throws, class-initialization behavior, normal and exceptional exits, synthetic boundaries, exit seeding and instruction indexes. Release the adapter with the analysis session. It must not capture actors, run managers or per-context facts. All changes stay in graph consumers and adapters; no IR storage or IR graph-provider change is proposed.

## O12. Compare queue priorities as primitives

Keep the existing `PriorityQueue<Entry>` in [EventQueue](../../core/opentaint-dataflow-core/opentaint-dataflow/src/main/kotlin/org/opentaint/dataflow/bidi/engine/EventQueue.kt). Replace `compareBy` with sequential primitive comparisons:

```text
compare rank as Int
if equal, compare steps as Long
if equal, compare sequence as Long
```

Use `Integer.compare` and `Long.compare`, not subtraction that can overflow. Build rank and steps with local variables instead of a temporary `Pair`. The entry still snapshots actor state once when it is inserted. Keep the zero-work priority, non-work priority, ordinary-work priority and FIFO sequence tie break. Do not read mutable actor state from the comparator. Preserve `UnitRunner`'s existing W2 resampling when it requeues an actor after a partial step.

This targets 7.64% of Conductor run-1 allocation weight and 6.92% in ThingsBoard. These are sampled allocations, not a claim that queue entries retain that share of the heap.

## Implementation order

The document order ranks expected memory impact. Implementation also has dependencies: stable logical keys are needed before forms can be reclaimed safely. Add that foundation first, then start the two highest-impact changes. Each row is a separate reviewable change or small series. Record the parent commit and exact patch for each measurement.

| Phase | Work | Required result before the next dependent phase |
| --- | --- | --- |
| Foundation | Counters, stable rule/action/proof/temporary handles, and owned payload factories | Existing cube engine still matches its control after replacing reference-identity keys |
| 1 | O1 shared NNF evaluator for source and sink rules; expression-based backward seeds | No compiled cubes or action/array products; complete forward/backward effects match the test oracle |
| 2 | O2 managed soft form cache | Forced eviction during delayed joins, triggers and suspended calls preserves results; cache misses rebuild correctly |
| 3 | O3 compact containers and O4 payload sharing | Smaller retained form owners; no strong pool defeats O2 |
| 4 | O5 shared templates and validation; O6 lazy groups | Complete binding/invalidation and merge/delta checks pass |
| 5 | O7 cleaner payloads and proven action reduction | Cleaner requests, temporary identity and exclusion effects match |
| 6 | O8 residual transfers only if needed; O9/O10 trie and alias work | New profiles justify the remaining costs; ownership and complete closures match |

O2 can start immediately after the foundation, before O1 is complete; its managed-reference plumbing is small. Its rank does not require delaying a useful cache change behind the full predicate rewrite. O11 graph reuse and O12 queue comparison can also be separate early changes because of their low implementation cost, despite their low retained-memory rank. Re-profile after the memory phases before choosing a larger trie or sparse-array rewrite.

## Validation and acceptance

### Core tests

Use the existing test suites and add cases for changed contracts. The tests must assert effects, not reproduce a container's implementation.

| Area | Required cases and existing suites |
| --- | --- |
| Ownership and form identity | Mutate caller inputs after construction; concurrent payload creation if supported; distinct equal-content rule occurrences; stable logical direction handles across rebuilds. Extend `FormsCacheTest`, `CallPlanTest`, `SummaryKindsTest`, `ManagerOwnershipTest` and JVM form tests. |
| Conditions | Invalid raw discarded branches; source/sink `P AND NOT P`; signed cleaner assumptions; independent AnyArgument bindings; ordered/repeated slots; array root, element and nested-element choices. Extend `JIRRuleFormsTest`, `JIRCallPlanTest`, `JIRCleanerMappingTest` and `JIRLoweredCleanerEffectTest`. |
| NNF source and sink evaluation | `A@arg0 AND B@arg1` arriving in both orders; `(A OR B) AND (C OR D)` without mixing proof scopes; `A AND A` and `(A OR B) AND A` with different inputs for `A`; duplicate OR paths; one predicate with multiple source targets; source filters; sink end facts. Assert compiled node counts follow expression size rather than its potential cube count. |
| Support and transfer policy | Delayed/missing inputs in both layers with distinct exclusions/supports; unary source path/suffix and exclusion transfer; unary POSITION requests versus conjunctive MARK requests; ordinary unary reversal versus forced-DEMAND multi-literal reversal; `A OR (A AND B)` producing both classes. Extend `ConjunctionStoreTest`, `LocalConjunctionTest`, `StatementSummaryReversalTest` and `CallPlanReversalTest`. |
| Exit read/drop footprint | `S.a/A OR (S.a/A AND S.b/B)` with only zero-premise `S.b/B`: its original read/drop effect remains even if the root never fires. Include delayed `A`, caller-set static state, seeds and optional absorption controls. |
| Seeds and provenance | Complete forward source edge value and statement/context; expression leaf requirements with proof scope; reversed end-fact seeds for CONFIRMED results; once-per-logical-proof trigger; stable handles across eviction. Extend `CallSourceHitTest`, `SourceHitStoreTest`, `SeedIndexTest` and `FrontierSeedIndexTest`. |
| Managed form caches | Explicit cleanup between conjunction inputs; sink guards and trigger-once across rebuild; forward/backward pair eviction; deterministic cleaner temporaries; suspended call resumption; disabled `createRef`; failed build; cleanup/build race; live lease survival and later reclamation; bounded reference bookkeeping. Use controlled cleanup and reachability checks instead of relying on a particular JVM soft-reference collection time. |
| Cleaners and groups | Every exclusion-table case, F75 requests on an already excluded mark with the incoming premise, repeated EXACT/BELOW/EXACT, singleton promotion/removal, layer-specific merging and delta publication. Extend `CleanerTest`, `GroupMergeTest` and `TaintGroupKeysTest`. |
| Graphs and queue | One snapshot per frozen body, fresh graph/form/alias owners in a new session after body replacement, virtual/lambda bodies, try/catch cuts and both exits; priority ties, large step values and mutable actor state after insertion. Extend graph/driver integration tests and `EventQueueTest`. |
| Aliases and tries | Complete alias path sets, field read/write effects, unchanged node reuse with no mutation, annotated merge/subtract, one callback evaluation per operation state, shared nodes under different inherited/filter/budget states, cached null results, retained callback paths, 32,768-level paths, delta and publication behavior. Extend alias tests, `TrieTest`, `TrieMergeDeltaTest`, `PathTrieTest` and summary storage tests. |

Compare the legacy cube evaluator and the NNF evaluator on small complete engine workloads for both source and sink rules. Compare finite closures, requests, raw summaries, source hits, triggers, witness supports and report states under several delivery schedules. Map a legacy alternative's selected leaves/OR choices to a structural proof handle only in the test oracle; do not retain that expanded mapping in production. Check each distinct support combination and its layer/exclusion. Re-run these cases with forced cache reclamation between inputs and at barriers. Document the intended internal key change while requiring equivalent analysis effects.

Run the affected common/JVM suites and then the required broader analyzer/query suites. Record known failures from the control build. Do not add ignores or loosen assertions to pass an optimization. Keep F74/F75/F76 and driver/barrier controls in the gate.

### Measurements

Add opt-in counters for expression nodes/leaves, rule/action occurrences, leaf checks, predicate-state entries, proof nodes and actual emitted support combinations. Count managed cache hits/misses, rebuild time, active leases and their bytes, registered/cleared references and retained logical metadata. Also count shared payloads, summary/index sizes, residual reverse transfers, cleaner tuples, group promotions, graph builds, alias copies and trie node reuse. Record pool/memo hits, misses and retained entries. Avoid expensive per-event logging in normal operation.

Run OWASP, Conductor, ThingsBoard and Stirling-PDF with the exact configurations in the input inventory, through the direct Gradle `runProjectAnalyzer` task. Keep the 8/8/12/8 GiB heaps and 1000/1080/1080/1080 second IFDS limits. Keep the rules, source heads and model inputs fixed. Keep run 1 at limit 1.

For each change, record completion, prescan time, each run and barrier time, GC pauses, peak heap/RSS, handled/pending events and finding comparison. Capture CPU and allocation profiles for seconds 60–90 after IFDS starts when the scan lasts that long, plus the final 30 seconds or the near-limit interval if it occurs. Record a shorter available interval explicitly.

Keep timing controls separate from live-heap capture runs. The memory dump forces GC and pauses the process. Compare live-heap owner unions, object counts and cache paths at comparable analysis stages. A completed scan may retain more useful work than an earlier failed scan; report progress and peak memory together. Include the new pool/index/descriptor owners in the heap analysis.

The final performance gate is completion of all four workloads within their existing budgets, with no new finding regression. Compare unique sink identities and retain strict source locations. Keep the two known PDF exit-location differences visible until a separate reporting fix resolves them. Inspect added findings after refinement completes; the current incomplete reports cannot establish a final false-positive rate.

If a change reduces a measured cost but a project still stops at the memory guard, report it as a partial improvement. Do not call a shorter failed run faster. Do not claim a fixed number of GB saved from overlapping retained unions. Use the next heap/profile to select the next change.

## Main risks and limits

NNF removes compiled cube growth; it does not remove the need for support, branch provenance or AP transfer policy. Managed soft references remove cache retention only where other strong owners and live leases permit it. The main risks are treating the predicate as a stateless Boolean, mixing OR supports, rebuilding identity-based joins, or retaining all payloads through a strong pool.

Use stable logical keys, scoped support, complete binding/cache keys and forced-reclamation tests to address those risks. Measure active-state growth and rebuild CPU after the change. The remaining IR cost is outside this scope. Only the fixed project repeats can establish that these core changes remove the memory failures.
