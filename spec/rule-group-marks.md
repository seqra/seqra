# Rule groups, full marks and the first two runs

> Superseded for implementation by [Mark groups and direct static handling](grouped-run1-statics.md),
> based on pinned analyzer `a80d5c580`. This file keeps historical design and
> local proof evidence. It does not add runtime requirements to that spec.

The current member domain and census/primitive implementation are defined by
[the selected next-iteration spec](run1-group-policy-and-ownership-census.md).
Use all known owned full IDs, including inactive declarations. Original
source/rule admission remains separate from catalog membership.

Date: 18 October 2026. Status: **selected specification; analyzer unchanged**.
Language: ASD-STE100 Simplified Technical English. Defined technical terms,
code names and mathematical notation keep their stated meanings.

This specification follows [proof-guide.md](proof-guide.md). The
[proof dossier](rule-group-mark-proofs.md) gives the constructive Lean models,
their checked claims and the remaining adapter obligations.

This file takes precedence for mark representation, run-1 selected-mark
cleaners, mark exclusions, group requests, mark-dependent record admission
and the numbered run schedule.
Other AP, field-write, call, support and report rules keep their current
contracts. This file does not implement the proposed shared static pool.
A static-pool adapter must meet the mark and demand contracts here.

The separate [grouped static-pool proposal](run1-group-static-pool.md) now
specifies that adapter, its FLOW/group interests and its full-mark handoff.
It remains an unimplemented proposal; it does not change this mark contract.
Its revised static path requires a source/writer derivation for every
supplied group. A request or hypothetical grouped initial cannot ground
static group availability. FLOW retains a conditional static premise.
The static adapter uses ordinary forward edges and direct current-point
refinement. A static read waits for a compatible published FLOW or group
write; late writes wake pending reads. Write presence supplies no group.
The adapter adds no backward provider propagation inside run-1.

Measured runtime remains `63d5481e4e4b69ecf042c1149bb574ad40fd1624`.
Latest reached / completed engine / accepted forward report: OWASP
**run-7 / run-6 / run-5**; conductor **run-1 / none / none**;
ThingsBoard **run-1 / none / none**; Stirling-PDF **run-2 / run-1 / run-1**.
These measurements use the previous mark and run-limit rules. They do not
measure this specification. IR storage and reference-manager changes are
outside scope.

## 1. Terms and identities

| Term | Meaning |
| --- | --- |
| Full mark | The complete current mark identity, including generated rule namespace, classifier, variables, state/label and mode. |
| Group | One top-level rule ID. The entire join rule has one group. |
| Exact mark | The pair `(group, full mark)`. |
| Group mark | `(group, *)`. It permits any full mark owned by that group. |
| FLOW | The existing abstract pass-through mark `*`, possibly with mark exclusions. It is not a group mark. |
| Group request | An independent interest in `(group, *)` at an original input slot. It is not evidence of a taint mark. |
| Repair | The DEMAND branch for a group removed from a NORMAL FLOW branch. |
| Group demand | A next-run pattern that accepts any full mark in one group. |

```kotlin
data class ExactMark(val group: GroupId, val full: FullMarkId)

sealed interface TaintSelector {
    data class Full(val mark: ExactMark) : TaintSelector
    data class Group(val group: GroupId) : TaintSelector
}

sealed interface MarkExclusion {
    data class Full(val mark: ExactMark) : MarkExclusion
    data class Group(val group: GroupId) : MarkExclusion
}
```

ZERO remains separate control information. It has no taint group. A `*` in
the second part of a group mark is not a path accessor or an AP tail.

Every full mark has exactly one owner group. Preserve its owner when a rule
checks, copies or removes that mark. Assigning a different full mark U uses
owner(U). Full mark identity remains available in rule descriptions in all
runs, including run-1. Group projection must not change those descriptions.

## 2. Top-level ownership and the provider boundary

Use the outer `TaintRuleFromSemgrep.ruleId` as the group. All join operands,
sink branches and generated automaton states share that group. Internal
`#source` and `#sink` namespaces remain in the full mark only.

Build the owner map before the loader flattens a top-level rule into its
serialized items. Use creation provenance, not string splitting or the ID
of a rule that later consumes the mark.

```kotlin
for ((rule, _) in loaded.rulesWithMeta) {
    val group = groupIds.intern(rule.ruleId)
    rule.forEachGeneratedMarkDefinition { full ->
        owners.registerUnique(full, group)
    }
}
```

A full mark with two different declared owners is an input-contract error.
Do not silently create two exact identities for the same old full mark.
Imported references keep their registered owner.

Use one new-core adapter around the complete existing provider chain. It
must cover source, entry, exit, static-field, sink, cleaner, rewriter and
pass-through roles. Keep the legacy provider unchanged. The adapter must
use typed rule views or the new core's single mark-intern gateway.

Plain model/configuration marks also need an owner. Use a declared stable
owner where one exists. Otherwise use one reserved, tagged `SharedModel`
group. This group is different from every user rule ID. Copy-all and
remove-all actions do not create groups. Method or argument specialization,
context changes and cache rebuilds do not create groups.

The same owner registry serves prescan and every numbered run. The closed
known-owned full-mark catalog must cover every full mark that a rule/model
can produce, test or remove. Active observation is not a membership gate.
A late provider extension must update matching group-demand
queries before a complete barrier; it cannot silently add an unseen member.
The finite Lean decoder uses an explicit closed catalog.

## 3. Run schedule

Run numbers start at 1. Run 0 and field limit 0 are invalid.

| Run | Direction | Concrete mark precision | Field limit |
| --- | --- | --- | ---: |
| run-1 | FORWARD | Group marks | 1 |
| run-2 | BACKWARD | Full marks | 1 |
| run-3 | FORWARD | Full marks | 2 |
| run-4 | BACKWARD | Full marks | 3 |
| run-5 | FORWARD | Full marks | 4 |

```text
direction(r) = FORWARD when r is odd; BACKWARD when r is even
fieldLimit(r) = max(1, r - 1), for r >= 1
markMode(1) = GROUP
markMode(r) = FULL, for r >= 2
```

The first handoff increases mark precision. It does not increase the field
limit. Do not stop because those two limits are equal. The field limit
increases at every later handoff. The existing stop, budget, barrier and
report rules otherwise stay in effect.

FULL mode does not remove the existing F72 FLOW forms. It means that each
concrete taint mark in a value, initial or rule transfer has its full
identity. Group selectors can remain in read-only demands and in old
record exclusions. They are not propagated group-taint values after run-1.

## 4. Legal marks and layers

| Object | Run-1 | Run-2 and later |
| --- | --- | --- |
| Concrete taint initial/value/summary | `(g, *)`, always DEMAND | `(g, T)`, ordinary layer rules |
| Generic FLOW | `*` or FLOW with group exclusions | Existing FLOW forms |
| New selected-mark FLOW exclusion | Whole group `(g, *)` | Exact `(g, T)` as in the current full-mark rules |
| Persisted run-1 FLOW exclusion | Keep the whole-group meaning | Match by the full mark's owner group |
| Mark request | Group request, run-1 only | No run-1 request protocol |
| Forward demand consumed by run-2 | Group selector | Selection covers every known owned full member |

No producer may put an exact full taint value into run-1 state. Sources,
initial emission, calls, rule outputs, ND and replay all use the group
selector for that value. All such edges are DEMAND. Changing the current
base, original slot, rule action or method does not make them NORMAL.

Generic FLOW edges can be NORMAL when their ordinary proof permits it.
In particular, a FLOW edge with a whole-group mark exclusion can be NORMAL.
This permission does not promote a DEMAND input, a pooled static dependency
or a result with an unresolved guard/support obligation.

The mark change does not change AP geometry. Keep the current legal paths,
tails and field limits. Run-1 initial tails remain EXACT or STAR, never ANY
or must. Replace T with `(g, *)` in the legal taint initial families; their
layer is now DEMAND, including a nonstatic exact initial. Generic Base/field
FLOW initials and the ban on an S-root initial keep their existing rules.
Do not infer a spatial tail conversion from group projection or demotion.

The ordinary entry adapter has the following exhaustive initial families.
Here b is nonstatic/nonzero, S is the static base, and C is a class accessor.
The class accessor does not consume the field limit. All initial E and mx
are empty. `Group(g)` means `(g, *)`; FLOW's `*` is a separate mark kind.

| Initial position | Initial tail | Mark | Started current fact / layer |
| --- | --- | --- | --- |
| ZERO | EXACT | Control | REACH / NORMAL |
| b | STAR | FLOW | STAR FLOW / NORMAL |
| S.C.f | STAR | FLOW | STAR FLOW / DEMAND |
| b | EXACT | Group(g) | EXACT group / DEMAND |
| b | STAR | Group(g) | ANY group / DEMAND |
| S.C.f | EXACT | Group(g) | EXACT group / DEMAND |
| S.C.f | STAR | Group(g) | ANY group / DEMAND |
| S.C | EXACT | Group(g) | EXACT group / DEMAND |
| S.C | STAR | Group(g) | ANY group / DEMAND |

An EXACT supplied point selects an EXACT taint initial. A compatible broad
or descendant supply selects STAR. The started ANY current fact of a STAR
taint initial does not change its initial tag to ANY. Preserve ordinary
geometry admission and the initial-emission supply contract; replace the
request/taint member with its group. There is no S-root, abstract class,
nonstatic field-path, second-field, ANY or must initial. The optional shared
static-pool design would replace its own entry lane through a separate spec.

## 5. Mark sets and exclusions

Run-1 taint sets contain GroupIds. Join them by group-set union. Do not
retain or expand a full-member set at each AP or request row. A full-mark
catalog is metadata, not a per-fact payload.

For an exact later mark m:

```text
excluded(Full(t), m)  iff m = t
excluded(Group(g), m) iff owner(m) = g
```

Compose exclusion filters by union of their excluded sets. When otherwise
compatible FLOW alternatives are unioned, an excluded mark remains excluded
only if every alternative excludes it. For run-1 group sets this is group
intersection. Never intersect unrelated premises/support to manufacture a
new NORMAL edge.

Later joins can mix old group exclusions and new full-mark exclusions.
Use intersection of their **denotations**, not intersection of typed IDs:

```text
Group(g) intersect Full(g,U) = Full(g,U)
Group(g) intersect Full(h,V) = empty, when g != h
Group(g) union Full(g,U)     = Group(g)
Full(g,T) intersect Full(g,U) = empty, when T != U
```

Apply these laws to the excluded sets of otherwise compatible alternatives.
For example, FLOW-except-g joined with FLOW-except-U must still exclude U
when owner(U) = g. Dropping that exclusion would invent a NORMAL U result.

Keep exclusion selectors typed. A GroupId and a FullMarkId can have the same
integer encoding without being the same selector. An old run-1 group
exclusion must not become an exact exclusion for one chosen member when
the record is read in a later run.

## 6. Selected-mark cleaner in run-1

Let T be the full mark named by a selected-mark cleaner, and g = owner(T).
Use the existing cleaner geometry, action order and F74/F75 lowering.

### 6.1 FLOW input

For an applicable FLOW preservation result:

```text
input:    original I -> current x, FLOW except X, layer L
interest: request (g, *) on the original abstract slot of I
output:   I -> current x, FLOW except (X union {g}), layer L
repair:   the requested (g, *) branch is DEMAND
```

Emit the interest before testing whether the value delta is empty. Emit it
even if g is already excluded. A duplicate request can then merge with an
existing request. FLOW mark exclusions do not filter this interest.

The output is NORMAL only if the incoming FLOW derivation and its ordinary
preservation proof permit NORMAL. Excluding more marks restricts that valid
relation. It does not prove the missing group has been cleaned completely.

### 6.2 Group-taint input

```text
input (g, *) + selected cleaner for T in g -> same group value, DEMAND
input (h, *) + selected cleaner for T in g, h != g -> unchanged, DEMAND
```

The selected-mark cleaner cannot remove the group value. It cannot tell T
from a different full mark U in the same group. This rule also applies when
the current catalog happens to contain only one member of g.

For a matching group value, retain the cleaner's group-demand interest.
Record the local forward-demand obligation at the cleaner's required point
and geometry even when the value delta is empty. An existing DEMAND group
branch can satisfy the interest only if this obligation is retained. A
stored group value alone does not prove that it was retained. Do not
propagate a new request backwards through a taint-group original. Only an
abstract singleton FLOW original uses the original-slot request protocol.
A disjoint group does not need a repair for this cleaner.

### 6.3 Remove-all and structural kills

The user selected the following exception: **RemoveAllMarks can still kill**.
Apply its ordinary geometry to remove all marks at the selected locations.
It does not need a separate complement for an individual mark group.
Requests do not make values cross a real whole-value kill.

Keep ordinary cleaner-condition admission. A rewritten true condition can
enable its kill. Group possibility alone cannot enable a conditional
RemoveAll or prove that an unresolved mark-dependent cleaner can kill.

Keep ordinary definite field-overwrite kills and disjoint preservation.
When a surviving run-1 result would need a field-exclusion update, move it
to DEMAND and keep its field exclusion empty, as currently specified.
Do not erase a shared global static-pool union because one execution has
a local remove-all action; that is a separate pool-scope operation.

## 7. Why the NORMAL exclusion is sound

Consider one scalar concrete mark m and a selected cleaner for T in g.

```text
outside g:
  m != T; passing m through the NORMAL exclusion is a real cleaner result

inside g, m != T:
  the NORMAL exclusion omits m; the requested DEMAND group covers it

inside g, m = T:
  the concrete cleaner kills m; the DEMAND group may still include it
```

Thus the NORMAL branch is a sound restriction. It can be incomplete.
The NORMAL branch plus the DEMAND repair covers every concrete survivor.
The extra T branch is a DEMAND candidate, not a NORMAL proof.

This remains true for a sequence of selected cleaners at the same AP:
exclude their groups in NORMAL and request their group union. Repeated
selected marks in one group require only one stored group interest.
General AP paths, calls and field-cleaner lowering must preserve this split;
the local set proof alone does not establish those adapters.

## 8. Request handling and repair delivery

The request key is:

```kotlin
data class GroupRequest(val method: MethodKey,
                        val originalSlot: Run1Slot,
                        val group: GroupId)
```

Requests have no AP tail, field exclusion, mark exclusion or ND conjunction.
Resolve the destination from the original abstract slot, not the current
value's base. For `p -> x -> cleaner(T)`, request g on p.
Ignore incoming field exclusions and FLOW mark exclusions for this join.
Requests must not be dropped because g is excluded from the NORMAL channel.

Keep the selected merged P/R stores and ordinary commit barrier:

```kotlin
summaryStorage.commit(temporary)
temporary = new RequestStorage()
```

This spec changes their members from full-mark IDs to GroupIds. Preserve
both arrival orders: a new group interest joins old compatible inputs; a
new compatible input joins old interests. Group-taint inputs do not carry
requests backwards. An abstract singleton FLOW input can propagate a need
to its original slot. ZERO and complete ND originals cannot do that.

Materializing a repair uses compatible supplied group information under
the initial-emission contract. A request alone is not an actual taint source.
A catalog member alone is not supply either. Abstract supplied FLOW can
denote possible members under that contract; a group candidate from it
remains DEMAND and is not proof that a named full mark exists.
If supply has not arrived, retain the interest for a later join. Before a
complete barrier, commit the interests, join all available compatible
suppliers and drain the work that those joins create. An interest can remain
unanswered when no compatible supply exists. It must not fabricate a taint
answer or block completion forever. Do not publish a NORMAL exclusion and
then discard its required request or local forward-demand obligation.

A static adapter can resolve a group need directly from a conditional
edge's static initial, subscribe to actual writer supply and inject a
DEMAND reply at the current operation input. It must retain the same
group-demand coverage without a backward provider traversal. Unknown-group
supply can remain generic FLOW or abstract pool metadata until a rule/request
names a group. Do not invent
a group, use ZERO as one, or eagerly emit all groups as taint facts.

## 9. Source and sink conditions

Keep the original full marks and stable bound occurrence in the NNF DAG.
Perform signed folding on full literals **before** group projection.

```text
Contains(x,T) AND NOT Contains(x,T) -> false
Contains(x,T) AND NOT Contains(x,U), T != U -> distinct full literals
```

The second condition must not become a contradiction when T/U share a
group. Keep the current eligible residual-negative projection. Group
presence is neither concrete truth nor evidence of concrete absence.

A group value can make a positive full-mark check possible when its owner
matches. The result and every source/trigger effect that depends on it are
DEMAND. Keep the original condition for later full-mark queries. This
includes ZERO-to-value generation after a conditional source.

NNF and ND observations stay scoped to the original rule occurrence.
Do not use group equality to confirm full-mark equality or compatible
caller support. Multiple full literals can map to one group during run-1,
but their later concrete obligations must remain distinct. Use the NNF DAG;
do not expand all condition cubes.

## 10. Persistence and record use

Persist independently valid NORMAL summaries under the existing complete-
barrier rules. A NORMAL FLOW record with group exclusions remains a valid
record. Its domain is restricted by those exclusions.

```text
record excludes g; later query asks for (g,U)
  -> record does not answer that query
  -> do not mark the method/query complete
  -> retain ordinary demand-driven analysis for the query
```

Check group exclusions by owner(U) in native and reversed record readers.
Do not substitute a record for current analysis outside its admitted domain.
The DEMAND repair and concrete next-run queries remain eligible even when
the outside-group NORMAL part has been persisted.

Group-taint DEMAND summaries cannot enter executable NORMAL records.
Neither can source outputs or support-dependent results derived from them.
An incomplete run cannot persist a pair with an uncommitted interest or
undrained repair work as a complete analysis result. A committed interest
without compatible supply is not such a failure. Preserve the current
cancellation guards.

## 11. Handoff to backward run-2

Run-2 has field limit 1 and full concrete marks. It follows the forward
demand under the existing AP handoff rules. A demand for group G permits
any actual full mark owned by G; FLOW alone is insufficient to
emit a full-mark initial. The proposed static adapter must generate those
group demands at the matching method/port, not only at the final sink.
See [static demand coverage](run1-static-demand-coverage.md). A group demand is a predicate
over full marks, not one representative mark:

```kotlin
fun accepts(d: GroupDemand, m: ExactMark): Boolean = m.group == d.group

fun expand(d: GroupDemand, catalog: FullMarkCatalog): List<FullQuery> =
    catalog.members(d.group).map { full ->
        FullQuery(d.point, d.geometry, full,
                  d.originalCondition, d.supportObligation)
    }
```

Expand against the complete known-owned catalog, not only full marks observed
in run-1. The mathematical decoder enumerates all members. An implementation
can match the group predicate lazily while visiting full rule/operator marks,
if it produces the same actual concrete queries. Never choose one arbitrary
member or propagate `(g, *)` as a concrete run-2 taint value.

Preserve demand geometry, owner/context, endpoint, condition and support
when choosing each full member. Group expansion does not change an AP tail
or invent a must premise. ZERO/control and ordinary F72 FLOW emission keep
their existing separate rules.

In particular, an internal backward sink seed has initial ZERO. Its marked
requirement can return through zero-only caller subscriptions under that
same ZERO initial, without a run-1 group carry guide in every wrapper.
Backward ZERO-premise publication keeps its existing exception. The sink's
demanded occurrence supplies T; ZERO receives no taint group. See the
[immediate-summary ZERO trace](run1-immediate-static-summaries.md#4-complete-trace-for-the-example).
This exception does not create a marked nonzero method initial or replace
its separate admission contract.

For the selected run-1 static design, the [shared static demand](run1-universal-static-carry.md)
provides root static GROUP entry/exit permissions in every method. It admits
an actual C.f/T query when owner(T) matches the group, preserving its full
mark and AP. It does not emit all full marks from FLOW or supply an executable
identity. Productive nonstatic, control-ZERO and cross-group outputs retain
their explicit paired demands.

All relevant source and sink NNF alternatives and ND members keep their full
mark identities. Backward analysis uses the original full-mark cleaner:
cleaning T preserves a different U in the same group. Current query demand
and valid record reads both apply; the old NORMAL group exclusion cannot
erase that U query.

Admit and analyze each decoded full query even when run-1 has no equal
full-mark guide or callee summary. Run-1 group guidance authorizes its full
members; an exact run-1 full-mark witness is not an additional gate. Apply
the ordinary query geometry, reachability and support checks to the query.

If the proposed static pool removes run-1 relay summaries, its separate
writer-discovery adapter is still required. Group expansion does not by
itself reconstruct missing CFG, call or static writer/read dependencies.
This mark specification does not claim that an unchanged run-2 implements
that separate static-pool proposal.

## 12. Later runs

Run-3 is FORWARD with full marks and field limit 2. Run-4 is BACKWARD with
full marks and field limit 3. Continue the existing alternating handoffs
with the schedule in section 3. New selected-mark cleaners/exclusions use
full marks in these runs. Keep the meaning of any older group exclusion
that remains in a valid persisted record.

Existing full-mark source permissions, strong field handling, AP limits,
must-tail rules, ND support and report confirmation still apply. A group
candidate or a group request cannot confirm a finding. Full-mark refinement
does not promise rejection of all extra paths, termination, or a complete
result before a timeout/OOM.

## 13. Concept, storage optimization and required proofs

The new concept is run-1 group projection with a NORMAL outside-group
restriction, a DEMAND repair, and a full-mark run-2 at equal field limit.
It deliberately changes old run-1 precision. Do not claim equal full-mark
run-1 summary sets with the previous analyzer.

Within this concept, compare a scalar group-request oracle with merged
group storage. They must give the same decoded group relation and actual
repair members. A cohort with m distinct full marks in one rule can use
one group membership instead of m memberships. This scoped cost law does
not prove a whole-project speedup or a 60-second bound.

The [proof dossier](rule-group-mark-proofs.md) separates:

1. Exact ownership and full-identity preservation.
2. Group membership, phase projection and layer invariants.
3. NORMAL exclusion validity and DEMAND survivor coverage.
4. Selected-cleaner non-kill and remove-all kill.
5. Request union, arrival-order and original-slot obligations.
6. Complete group-demand decoding with executable full-member witnesses.
7. Positive schedule and the equal-limit first boundary.
8. Representation equivalence and finite cost laws.
9. AP, provider, NNF/ND, record-reader, request and driver adapter obligations.

The selected next iteration also proves all-known scalar/metadata equivalence
and census observation/cache refinement. The old eligible and new all-known
domains are not equivalent. Existing finite decoders are catalog-parametric;
instantiate them with the new member domain and retain actual full witnesses.

## 14. Required controls

```text
T and U have group g; V has a different group h

clean T; sink U       -> NORMAL excludes g; DEMAND g keeps the U candidate
clean T; sink T       -> F1 can keep a D candidate; full cleaner kills T later
clean T; sink V       -> the outside-g NORMAL relation can carry V
clean T; clean U      -> one stored g request
RemoveAllMarks       -> ordinary local kill; no request revives the value
```

Also check source and sink signed NNF, same-group distinct literals,
conditional source generation, ND obligations, cross-join mark references,
old group exclusions in both record directions, late supply/request order,
unknown-group bootstrap, and all legal initial/AP-tail shapes.
Use the new spec's inactive-name, primitive-filter, late-owner and no-retain
census controls. A known declaration alone must not create a source or fact.
Check exactly `F1/GROUP/1`, `B2/FULL/1`, `F3/FULL/2`, `B4/FULL/3`, and reject
run/limit zero. Current analyzer tests do not validate an unimplemented
domain. Use the constructive model as the oracle before runtime changes.
