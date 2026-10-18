# A shared static demand for backward run-2

Date: 18 October 2026. Status: **proposed addition; analyzer unchanged**.
Language: ASD-STE100 Simplified Technical English. Defined technical terms,
code names and mathematical notation keep their stated meanings.

This addition follows [proof-guide.md](proof-guide.md). It closes the
static-relay admission hole in the
[immediate-summary completeness review](run1-static-demand-completeness-review.md).
Keep [immediate run-1 static summaries](run1-immediate-static-summaries.md)
and the existing backward ZERO rules.

## 1. Meaning of the default

After run-1, give every method an implicit `S.* -> S.*` **demand**. For
marked run-2 queries, its typed meaning is the following pair for each
eligible group G:

```text
entry pattern: S / empty path / ANY  / Group(G)
exit pattern:  S / empty path / STAR / Group(G)
```

The group selector accepts every eligible full mark owned by G. The entry
and exit describe permissions. They do not state a computed identity flow.
ANY is a demand match pattern, not a run-1 initial fact.

This is different from a FLOW-only `S.* -> S.*` row. The current abstract
emitter converts that row to a FLOW initial. It does not retain actual T.
A GROUP selector must admit the actual C.f/T query with its full identity
and geometry. No new concrete mark is selected from a FLOW input.

## 2. Store the default once

Use a shared implicit demand view, like the existing implicit ZERO demand.
It applies to every eligible method key, including a run-2 actor created
later. Do not populate it by iterating only `result.analyzers`.

```kotlin
class FrozenBackwardDemand(
    val explicit: ExplicitDemandStore,
    val groups: FrozenGroupCatalog,
    val universalStaticCarry: Boolean,
) {
    fun matchStaticCarry(method: MethodKey,
                         actual: FullStaticQuery): GroupDemandPair? {
        if (!universalStaticCarry) return null
        if (!groups.contains(actual.mark.group)) return null
        return rootStaticPair(actual.mark.group)
    }
}

fun afterRun1(explicit: ExplicitDemandStore,
              groups: FrozenGroupCatalog): FrozenBackwardDemand =
    FrozenBackwardDemand(explicit, groups, universalStaticCarry = true)
```

There is no per-method, per-field or per-source product. The method argument
keeps the ordinary query owner; it does not restrict the default to methods
with an observed static initial. The group catalog must cover all eligible
full marks, not only groups observed at a field during run-1.

Do not add this permission to the executable summary or NORMAL record store.
Do not use it as write presence, group supply, source evidence or evidence
of normal return. No run-1 S-root fact is emitted.

## 3. Admission and publication

An actual incoming query C.f/T already identifies its field/path and full
mark. Match owner(T) against the shared group selector. Then retain the
actual bounded query instead of replacing it with a broad S-root initial:

```kotlin
val guide = demand.matchStaticCarry(method, incoming) ?: return
val initial = intersectActualQuery(incoming, guide.entry)
// Preserve incoming field/path, full T, tail and exclusions after the
// ordinary AP/field-limit normalization. Do not start a broad root query.
analyzeOrdinaryBody(initial)
```

The default also permits a computed static result in the matching group:

```kotlin
fun publishStaticResult(initial: FullStaticQuery, result: ActualBodyResult) {
    val carry = demand.matchStaticCarry(method, initial)
    if (carry != null && carry.exit.matches(result))
        publish(result) // the real body already derived this result
    publishOtherExplicitMatches(initial, result)
}
```

Admission and publication must be additive with explicit demands. An old
narrow exit hint must not discard a result admitted by this shared pair.
The default permits the root's compatible static descendants. It does not
discard their field information. A same-group full-mark change is permitted
only when an ordinary full-mark operation actually derives that result.

The body still executes all reverse assignments, call bindings, cleaners
and kills. For `clear() { C.f = null; }`, the default admits C.f/T but the
reverse overwrite kills that requirement. No identity answer is returned.

No actual marked input means no marked initial from this default. It does
not enumerate all marks at every method or turn ZERO into a static fact.

## 4. How it closes the counterexample

```text
main: set_f(); pass_only(); consume_f()
pass_only: return
```

The sink in consume_f produces its requirement under initial ZERO. Its
balanced return gives main a C.f/T requirement before consume_f(). At the
preceding resolved pass_only() call:

```text
actual incoming C.f/T
  -> match shared S/Group(G) entry, where owner(T) = G
  -> analyze pass_only with the actual C.f/T initial
  -> derive its ordinary C.f/T identity result
  -> match shared S/Group(G) exit
  -> resume main before pass_only()
  -> continue to set_f()
```

No C.f/G row in pass_only's run-1 summaries is required. Run-1 performs no
static relay propagation to create that row. The same shared rule covers
any finite chain of transparent static relays. The proof must derive each
admission from the default rather than assume per-field guide membership.

## 5. Productive demands remain necessary

The default is a same-group static-to-static permission. It does not cover:

| Computed backward result | Required additional permission |
| --- | --- |
| Getter `return/T -> C.f/T` | The productive getter pair; its input is nonstatic |
| Writer `C.f/T -> p/U` | The early writer's reversed pair for the ordinary original slot |
| Source result `C.f/T -> CONTROL_ZERO` | The early ZERO-to-field writer's explicit control-ZERO exit |
| Static group rewrite `C.f/T[G] -> C.g/U[H]`, G differs from H | The actual productive pair with distinct group selectors |
| Conditional or ND effect | Its complete original bound support and productive pairs |

Keep the captured early writer rows, group-guide closure and source-hit
rules. Keep the internal sink seeds and existing ZERO balanced return.
The default is not an executable `S -> S` summary and cannot replace these
productive relations. It cannot confirm a source, a normal return or a
finding by itself.

## 6. Proof scope and expected effect

This addition removes the known missing permission at a static relay.
It is compatible with the strict group gate: every actual static T query
has a matching implicit group demand. It needs no broad change to ordinary
nonstatic admission and no per-field source-derived carry table.

The local proof target covers admission and paired publication for finite
actual same-group static relay paths. Real body steps remain separate.
Productive static-to-ordinary, source and group-changing steps use the
constructive guide witnesses from their own models. A complete theorem must
still compose run-1 source/read/candidate generation, productive guides,
run-2 actual ports and primitive AP/call/source semantics. The universal
default alone is not a whole-JVM or whole-iteration soundness proof.

Run-1 gains one shared demand view and no static CFG relay work. Run-2 can
perform necessary static callee queries that the strict gate would reject.
The current actual-input policy already admits these queries. No project
speedup, universal termination or 60-second bound follows from this rule.

## 7. Constructive validation

The [Lean model](lean/ApSpec/Run1UniversalStaticCarry.lean) separates demand
admission, ordinary body evaluation and result publication. Its shared
`FrozenDefaults` stores only an enabled flag and the complete group catalog.
`all_methods_same_view` proves that matching is independent of method keys.

`emits_actual_full` preserves the actual query. `every_finite_transport_path`
constructs admission and publication through any finite identity/static-copy
path without assuming individual field guides. `previous_gap_is_closed`
executes the earlier counterexample with this default and succeeds.
`universal_matches_current_identity_chain` compares its outputs with current
actual-input admission on the same identity-relay scope. `kill_still_kills`
rejects the identity bypass around a real clear.

The focused module passes **19 controls** and an audit of **383 declarations**,
with **zero unexpected axioms**. The complete Lean build passes **157 jobs**.
The combined audit passes **299 controls** across **4,201 declarations**,
with **zero unexpected axioms**. The twelve existing ZERO/current-query
runtime tests also pass. They do not implement this new default.

The [validation receipt](/drive-testcomp/opentaint-go-rules/run1-universal-static-carry-20261018/validation/20261018T235053.989525Z/receipt.json)
has SHA-256 `04545b213180c2c9556a42567923ba24775c6582d61c3a3e0548cd6a567e5403`.
It records stable proof/runtime source hashes, the
[build log](/drive-testcomp/opentaint-go-rules/run1-universal-static-carry-20261018/validation/20261018T235053.989525Z/build.log),
the [axiom/control audit](/drive-testcomp/opentaint-go-rules/run1-universal-static-carry-20261018/validation/20261018T235053.989525Z/audit.log)
and the existing [ZERO test results](/drive-testcomp/opentaint-go-rules/run1-universal-static-carry-20261018/validation/20261018T235053.989525Z/CallZeroTest.xml)
and [current-query test results](/drive-testcomp/opentaint-go-rules/run1-universal-static-carry-20261018/validation/20261018T235053.989525Z/StrongQueriesTest.xml).

## 8. Measurement scope

No analyzer implementation or project measurement changes in this proposal.
Latest measured runtime remains `63d5481e4e4b69ecf042c1149bb574ad40fd1624`.
Latest reached / completed engine / accepted forward report: OWASP
**run-7 / run-6 / run-5**; conductor **run-1 / none / none**;
ThingsBoard **run-1 / none / none**; Stirling-PDF **run-2 / run-1 / run-1**.
