# Rule-owned mark pairs and run-1 abstraction

Date: 18 October 2026. Status: **earlier design and catalog measurements**.
The [complete mark specification](../../spec/rule-group-marks.md) and its
[proof dossier](../../spec/rule-group-mark-proofs.md) supersede this sketch
for behavior. The catalog counts below retain their measured scope. There
are no analyzer changes. The selected rules use group-only run-1 taint
values, selected-cleaner non-kill and full-mark backward run-2 at limit 1.
This refines the mark domain in the
[shared static-pool proposal](bidirectional-run1-global-static-pool-20261018.md).
Run-1 may over-approximate static order/context and mark details; later
analysis needs the original concrete conditions and marks.
The user selected **one group for an entire top-level rule, including every
branch and internal namespace of a join rule**.

Measured runtime remains `63d5481e4e4b69ecf042c1149bb574ad40fd1624`.
Latest reached / completed engine / accepted forward report: OWASP
**run-7 / run-6 / run-5**; conductor **run-1 / none / none**;
ThingsBoard **run-1 / none / none**; Stirling-PDF **run-2 / run-1 / run-1**.
These results do not measure this proposed abstraction.

## 1. Exact identity and abstraction are separate

Each exact mark is the proposed pair:

```kotlin
data class OwnedMark(val group: GroupId, val full: FullMarkId)

sealed interface MarkValue {
    data class Exact(val mark: OwnedMark) : MarkValue
    data class AnyInGroup(val group: GroupId) : MarkValue
}

fun abstractRun1(mark: OwnedMark) = AnyInGroup(mark.group)
```

`full` is the complete current mark identity. Preserve classifier, variables,
state/label value and mode modifier. The pair alone preserves all existing
distinctions and does not reduce the number of marks. Projecting to
`AnyInGroup` removes the within-group distinction during the selected run-1
analysis. It is a DEMAND possibility, not concrete evidence.

Intern the pair once in the mark registry. Facts can store a compact mark ID
whose metadata contains the group; no pair object is needed per fact.
Grouped sets use compact GroupIds and remain grouped throughout local and
interprocedural propagation. Do not decode a group into all its concrete
members to store ordinary run-1 initials.

## 2. Group ownership

`Mark.GeneratedMark` already contains the full generated identity:

```kotlin
GeneratedMark(ruleId, ruleClassifier, variablesStr, value, modeModifier)
// full  = the complete original mark
// group = the outer TaintRuleFromSemgrep.ruleId that owns this mark
```

Use the mark's owner, not the rule currently inspecting or copying it.
Join branches can use internal `outerRule#operand` namespaces and consume
another branch's marks. Those internal IDs remain in `full`, but they do
not create separate groups. All branches of one loaded join share its
outer rule ID. Different top-level rules remain different groups.

Build ownership before flattening the loader result into serialized items:

```kotlin
for ((outerRule, _) in loaded.rulesWithMeta) {
    val group = groups.intern(GroupKey.Generated(outerRule.ruleId))
    outerRule.forEachGeneratedMarkDefinition { fullMark ->
        generatedCatalog.registerUniqueOwner(fullMark, group)
    }
}
```

Resolve references through this shared owner catalog. Referencing another
rule's mark does not change its owner. A conflicting owner for the same
full mark is a catalog-invariant failure, not permission to split that
mark's identity. Carry explicit creation provenance where necessary.
Do not recover the top-level owner by splitting strings at `#`.

```kotlin
fun resolveMark(raw: LegacyMark): OwnedMark = fullMarks.getOrPut(raw) {
    val group = generatedCatalog.ownerOf(raw.name)
        ?: groups.intern(GroupKey.SharedModel)
    OwnedMark(group, fullIds.intern(raw))
}
```

`SharedModel` is the proposed fallback for ungenerated model/configuration
marks. Exact full identities still distinguish them. Its run-1 wildcard
deliberately over-approximates them together. A per-raw-mark model group is
a more precise alternative if this fallback produces too much work.
ZERO is reserved control/reach information and is never a taint group.
Generated and SharedModel group keys are disjoint tagged identities.

Use catalog provenance for generated ownership. The current text parser
accepts four/five semicolon-separated parts; arbitrary configuration names
can imitate that format. Do not infer provenance from text shape alone.

Every full mark has one stable owner. Specializing a method, argument,
context, action or automaton state creates no new group. Generating an
output mark U uses owner(U), even when the input had a different owner.
Copy-all operations preserve every input pair; remove-all operations
introduce no group.

## 3. New-core provider boundary

Prefer a new-core adapter around the complete current provider chain.
The outer chain includes generated, model, method-exit, Spring and
string-concatenation rules. Changing only `MethodTaintConfigurationResolver`
would miss some of those effects and also change the legacy consumer.

```kotlin
val registry = OwnedMarkRegistry(generatedCatalog)
val bidiRules = GroupedRulesProvider(finalLegacyProvider, registry)

// Both the new-core prescan and execution use this registry.
fun adapt(rule: LegacyRule): BidiRuleView = BidiRuleView(
    original = rule,
    mark = registry::resolveMark,
)
```

The adapter must expose typed new-core rule views or supply the single
mark-intern gateway used by new-core compilation. Merely returning the
unchanged legacy DTOs does not enforce the new pair contract.
The adapter borrows the ownership catalog built before flattening. Flat
rule metadata can contain an internal join ID instead of the top-level ID.

Apply the adapter to all source/entry/exit/static-field, sink, cleaner,
rewriter and pass-through roles. Preserve signed NNF, stable occurrences,
positions and action order. Do not retain another permanent cache of every
adapted bound rule. Reuse the current view/compiled-form lifetime.

If assignment is placed in `MethodTaintConfigurationResolver` instead,
it must still use the mark-owner registry above and cover outer providers.
Do not use per-item `serializedId`, generated sink display IDs or method
binding IDs as groups.

## 4. Operations on group wildcards

```text
Contains(Exact(g,T), T)       -> ordinary exact check
Contains(AnyInGroup(g), T)    -> possible iff owner(T) = g

copy(value)                  -> preserve mark/group payload
assign(U)                    -> output Exact(owner(U), U), or its F1 projection
clean(T, AnyInGroup(g))       -> retain g, DEMAND; record group interest
RemoveAllMarks               -> ordinary geometry can kill all groups
```

Normalize signed conditions with the original full mark identities before
abstract evaluation. T and U in the same group remain different literals.
Group matches produce DEMAND candidates and original concrete backward
obligations, including source guards and ND conditions. They cannot confirm
taint, ND compatibility or a NORMAL persistent record.

Unknown abstract RHS content may include marks from any group. Keep generic
FLOW or pool metadata for it until a rule/request names a group. The earlier
`AnyPossibleMark` option is not a run-1 taint value in the selected spec.
Do not eagerly emit one wildcard per group.
Group abstraction changes marks only, not AP fields, paths or tails.

## 5. Count definitions and available measurements

The requested count is:

```text
generatedGroups = distinct outer rule IDs that own at least one generated mark
totalGroups     = generatedGroups + (SharedModel is used ? 1 : 0)
```

Report mark-bearing groups separately from all registered top-level rule
IDs, prescan-selected groups and propagated groups. A rule with no named
marks does not need a value-bearing group. Standalone exports from the
tested analyzer jar, using the same rule inputs/severities, give:

| Project | Top-level rule-group budget | Groups containing generated marks | Distinct full generated marks |
| --- | ---: | ---: | ---: |
| OWASP | 83 | 59 | 1,528 |
| conductor | 79 | 58 | 1,486 |
| ThingsBoard | 76 | 55 | 1,460 |
| Stirling-PDF | 76 | 55 | 1,460 |

The registered rule-group budgets are therefore 83/79/76/76. Join operand
namespaces do not increase them. There are 24 markless OWASP rules and 21
in each other catalog. Active counts can be smaller still. Many serialized
items and specialized occurrences share one owner; those counts must not
be substituted for groups. The existing heap report's 367 concrete
MarkSlot objects are also not a group count.

The largest groups have 583 full marks for XSS, 359 for unsafe Jackson
deserialization and 56 for SSTI. Each becomes one group wildcard in the
selected abstraction. For conductor, the complete 1,486-mark catalog maps
to 58 occupied groups, about 25.6 marks per occupied group on average.
This is a catalog ratio, not a measured per-AP or heap reduction.

All exported full generated marks map to a unique containing top-level
parent, with no mismatches. The exported catalogs contain no plain named
marks, and the tested jar's default Java configuration contains none. A
SharedModel fallback is still required for additional custom/model inputs;
do not add an occupied fallback group to these measured catalog counts.

The export visits conditions, actions and relevant-mark metadata, including
cross-namespace join references. It reproduces the historical generated
serialized-item totals 6,255/6,185/6,137/6,137. Prescan selects
168/89/381/795 serialized item IDs respectively; those are not group counts.
The current run logs do not enumerate the selected mark catalog, so this
export cannot establish the active/propagated group count in a measured run.

Evidence:
[catalog counts and distributions](/drive-testcomp/opentaint-go-rules/caller-owned-subscriptions-retest-20261018/reviews/generated-mark-groups/whole-rule-group-review.json),
[loader receipt](/drive-testcomp/opentaint-go-rules/caller-owned-subscriptions-retest-20261018/reviews/generated-mark-groups/receipt.json).
Three standalone catalog loads completed. No project analyzer, prescan,
heap or profile read was run, and no analyzer source was changed.

## 6. Proof and measurement requirements

Before runtime implementation, follow [proof-guide.md](../../spec/proof-guide.md).
Prove exact-pair identity preservation and stable ownership through provider
adaptation, then group-wildcard coverage for transfer and candidate handling.
Include cross-owner joins, generated state marks, plain shared marks,
mark rewrites, partial cleaners, signed contradictions and conditional
source generation. The new backward handoff retains full concrete marks.

Measure the number of full marks per group and the groups actually present
per AP. The savings depend on those distributions, not only the total number
of configured groups. Compare local work and candidate counts as well as
retained state; collapsing automaton state marks can enable extra rule paths.
