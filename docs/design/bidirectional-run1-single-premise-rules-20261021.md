# Run-1 rule results with one original premise

Date: 21 October 2026. Status: design proposal; runtime unchanged.

Source base: `0f7feafd88d5ca84dd1ae12ef4b8a4920c3d1b48`. Documentation
HEAD is `bb020ae0c`; its runtime still matches that source base. Latest
measured thingsboard result with
`disableRun1Statics=true`: **run-1, FORWARD, limit 1, OOM**, 664.799 engine
seconds. This proposal has not been benchmarked.

This proposal follows [proof-guide.md](../../spec/proof-guide.md). It adds
one selected semantic approximation to the active grouped/static contract.
It does not change the analyzer implementation or replace the active spec.

## Selected change

Keep the current complete condition check. After a complete cube binding,
select one of its original initial facts. Use that initial as the premise
of the emitted edge.

“Cube” means a complete accepted branch of the existing NNF predicate.
This proposal does not expand conditions into DNF or change their compiler.

```text
Current:  {I1, I2, ..., Ik} -> Y / DEMAND
Proposed: representative(I1, I2, ..., Ik) -> Y / DEMAND
```

Here, an ordinary or “normal” edge means **one premise**, not `Layer.NORMAL`.
Run-1 GROUP results stay DEMAND. The result AP, mark, tail, exclusions and
effective layer stay unchanged. The representative is an original initial,
not a condition pattern or a new projection of the current fact.

This changes run-1 semantics. A caller that supplies only the selected
input can apply the new summary. The old summary required every member at
that same call. Full local admission before publication does not preserve
that later caller correlation. The extra results are DEMAND candidates.
Those results can also enable more local cube bindings. Keeping the check
does not mean that the set of admitted run-1 candidates stays equal.
Later FULL runs keep their complete condition and caller-binding rules.

| Mechanism | Current | Proposed |
| --- | --- | --- |
| Local condition | Complete compatible cube required | Same check, same leaves and branch choices |
| Output premise | Union of all original inputs | One selected original input |
| Run-1 method edges and summaries | Atomic or ND | Atomic only, including ZERO |
| Run-1 call application | E6 joins ND members | Existing ordinary singleton application |
| Run-1 candidate precision | Complete input correlation retained | Omitted input dependencies become conservative |
| Later FULL runs | Existing ND and exact rules | Unchanged |

No field transfer, source-action, cleaner, mark-group, static-flow,
primitive-tracking, IR, cache-manager or barrier change is selected.
`trackFactsReachAnalysisEnd` stays ignored under the existing policy.

## Representative order

Apply this order to nonzero originals:

```text
1. Argument
2. This
3. Other legal non-static initial bases
4. Static field
5. Exact static class point
```

Thus, every non-static initial precedes every static initial. An argument
precedes `this`. A static field precedes a static class point. This order
does not add any new legal initial shape.

Break ties with a fixed structural key:

```text
(category,
 base kind and numeric index or other stable base value,
 decoded path: class name, field owner/name/type, element tag,
 fixed tail tag,
 stable mark/group name)
```

Use semantic accessor and mark names, not allocation order. `InitialAp.id`,
accessor IDs, atom IDs, identity hashes and hash-map iteration order can
depend on scheduling. Do not use them as the final tie-breaker. Selection
does not change accessor or mark interning.

ZERO is neutral. If any input has a nonzero origin, select a nonzero origin.
Return canonical ZERO only when every input origin is ZERO.

Membership in the completed binding is the safety requirement for local
weakening. The preference order is a heuristic: it keeps a call-input
dependency when one exists, rather than choosing a global state marker.
It is not a theorem that this order always gives the fewest candidates.

```kotlin
fun representative(inputs: Iterable<InitialAp>): InitialAp {
    var best = ap.zero
    for (input in inputs) {
        if (input.isZero) continue
        if (best.isZero || order.compare(input, best) < 0) best = input
    }
    return best
}
```

Select once for each complete binding. Do not keep only the first binding
of a rule. Different complete bindings can select different originals.
Keep all resulting single-premise edges, subject to existing edge-store
deduplication. Do not retract an earlier edge when a later binding selects
a more preferred original.

The preference is local to the current method's original inputs. It is not
a minimum over the whole call chain. For example, callee `arg0` can map to
caller `arg5`, while callee `arg1` maps to caller `arg0`. Selecting callee
`arg0` then retains caller `arg5`. No omitted caller input history is kept
to reselect a global minimum.

## Admission and emission pipeline

```text
incoming edge with one original
  -> existing rule leaf check and requests
  -> existing complete compatible cube binding
  -> select one original directly
  -> existing action and fixed target
  -> store/enqueue ordinary edge
  -> publish ordinary summary
  -> ordinary caller application
```

Do not construct a `PremiseSet` and then select a member. `ApManager`
interns these sets, so that approach would keep the old storage growth.

Inject a run-local combination policy into the existing local stores:

```kotlin
interface PremiseCombination {
    fun combine(inputs: Iterable<PremiseKey>): PremiseKey
}

class Run1Premises(/* ap, fixed order */) : PremiseCombination {
    override fun combine(inputs: Iterable<PremiseKey>): InitialAp =
        representative(inputs.map {
            check(it is InitialAp) // inductive run-1 invariant
            it
        })
}

// Existing exact union remains the policy for later runs.
val combination = if (config.run1) Run1Premises(/* ... */)
                  else ExactPremises(ap)
```

`ApManager.union` stays unchanged. The manager is shared with later runs.
Do not install a mutable global run mode in it.

There are two active local producer boundaries:

```kotlin
// RulePredicateStore.combineFresh: after the complete NNF root is ready.
val ordered = completeInputsInExistingLeafOrder()
val premise = combination.combine(ordered.map { it.premise })
val layer = existingLayerJoin(ordered) // inspect ALL inputs
emit(Combination(proof, premise, layer, leaves, ordered))

// ConjunctionStore.combine: after every explicit slot is present.
val premise = combination.combine(inputs.map { it.premise })
return Combination(premise, existingLayerJoin(inputs), inputs)
```

Keep proof identity, OR choices, repeated-leaf compatibility, checked Facts
and all per-literal inputs. Keep the current local product enumeration for
this iteration. This proposal removes ND executable edges, not complete
condition evidence. A separate optimization can later reduce local products.

Add run-1 assertions at executable edge inputs, point installation,
subscriptions and summary publication:

```kotlin
if (config.run1) check(premise is InitialAp)
```

Initial creation already produces atomic originals. Unary transfers retain
one original. Both complete local join producers now return one original.
Ordinary call application retains the caller's one original. Therefore,
run-1 publications cannot enter E6:

```kotlin
when (val premise = publication.premise) {
    is InitialAp -> applyOrdinarySummary(/* existing path */)
    is PremiseSet -> {
        check(!config.run1)
        ndMatch(/* existing later-run path */)
    }
}
```

Keep E6 and exact unions for later forward runs. Do not allocate run-1
`NdBinding`, `NdSummaryJoin.full` or ND call-member products. A late collapse
inside `applyCombination` would preserve the expensive path and is not the
selected implementation.

## Source and sink behavior

Preserve the source transfer classification independently of premise size.
A source that uses a MULTI fixed target still uses that fixed target after
selection. Do not send it through the unary AP-transfer path just because
the result has one premise.

```kotlin
onCompleteSourceBinding { binding ->
    val original = combination.combine(binding.inputs.map { it.premise })
    val result = ops.targetTree(action.target, binding.layer, mode)
    emit(original, result) // existing MULTI target geometry
}
```

Preserve every source action ID, source occurrence and existing unary/GEN
transfer. In particular, do not reset a nonzero local cube to ZERO. An
ordinary caller can still have ZERO as its original if its selected actual
input came from a source. This is the usual caller origin; it does not
authorize new ZERO discovery or a new source action.

A sink still needs its whole condition. Keep all literal `SinkEdge`s,
checked facts, layers, proof and occurrence in its witness. A witness is
not an executable edge. Do not replace a complete sink witness with its
representative or fire a sink when only that representative is available.
The old test/helper `SinkWitness.supportPremise` must not recreate executable
run-1 ND premises.

## Examples

```java
// Rule: CM(a,G) AND CM(b,G) -> AssignMark(result,G)
Object combine(Object a, Object b) { /* configured operation */ }
```

```text
Accepted literal inputs:
    Iarg0 -> a/G
    Iarg1 -> b/G

Before: {Iarg0,Iarg1} -> result/G / DEMAND
After:   Iarg0        -> result/G / DEMAND

Only a is accepted: no output, both before and after.
Later caller supplies only callee arg0/G:
    before: no ND application
    after:  ordinary DEMAND candidate; FULL analysis must refine it
```

For the measured synthetic-state pattern:

```text
Rule condition: CM(x,G) AND CM(S.C,G)
Originals:      Iarg0, Iclass
Fixed result:   x.f/[any]/G / DEMAND

Before: {Iarg0,Iclass} -> fixed result
After:   Iarg0        -> same fixed result
```

Adding other synthetic markers can no longer create executable origins
`{Iarg0,Iclass1,Iclass2,...}`. If a later loop pass gives the same result
under `Iarg0`, existing method-edge deduplication can reject it. The proposal
does not change `x.f` to `x`, alter `[any]`, or remove the static condition
from local admission.

For an all-static cube, choose a static field over an exact class marker.
The existing conditional static-summary path then has one premise. A mixed
cube selects its non-static original. Omitting the other static dependency
is part of the selected run-1 weakening; the static service itself is not
redesigned. Exact class markers remain exact.

## Handoff and FULL refinement

Current `HandOff.toBackward` emits a guide for each original premise
member. All those guides have the same entry: the run-1 result pattern.
Their exits identify the different original inputs. The new edge emits
one guide with the same result entry and the selected exit.

This reduction needs a separate argument from forward over-approximation:

1. `StrongQueries.admit` starts canonical queries from the actual incoming
   fact, independently of old guides.
2. Its extra concrete-guide emission uses `DemandPattern.entry`.
   `ops.emit` does not read the omitted exit. Keeping one guide for each
   covered result therefore keeps this entry permission.
3. A backward source still reverses every required original FULL leaf.
   It does not reverse only the selected run-1 original.
4. Results of an admitted actual query can be published without filtering
   them through an omitted guide exit.
5. Actual ZERO discovery and internal-sink seeds stay unchanged. A
   nonzero taint edge does not become a ZERO-to-ZERO control edge.

The two-argument source, static-writer helper and nested internal-sink source
traces have been checked against these production paths. No counterexample
was found in those cases. They are adapter evidence, not a whole-analyzer
equivalence theorem. End-to-end validation must check a discarded input
whose exact FULL mark is needed in run-2 and a FULL conjunction in run-3.
Do not restore omitted dependencies through a new unbounded support store.

## Proof scope and required validation

The new constructive model is
[Run1RepresentativePremises.lean](../../spec/lean/ApSpec/Run1RepresentativePremises.lean).
It treats local accepted literals as inputs and checks the representative
policy. The proof receipt and narrow audit list the exact proved statements.

The module and [narrow audit](../../spec/lean/run1-representative-premises-audit.lean)
pass with Lean 4.33.1. The 23 audited theorems use no `Classical.choice`,
`sorryAx` or native decision axiom. Standard `propext` and `Quot.sound`
remain in the dependency report. The
[receipt](/drive-testcomp/opentaint-go-rules/run1-single-premise-design-20261021/lean-receipt-v2.json)
pins the source, commands and final logs.

| Checked model result | Scope |
| --- | --- |
| `emission_requires_complete_cube`, `complete_cube_emits` | Accepted literal availability for one complete branch |
| `representative_member`, `choose_same_members` | Existing original selected, independent of list order/duplicates |
| `positive_horn_overapprox` | Conservative consequence inclusion for fixed discovered clauses |
| `singleton_invariant`, `linear_label_bound` | No powerset in the executable label domain |
| `handoff_entry_sets_equal` | Equal projected guide-entry membership for corresponding covered outputs |

Executable counterexamples prevent selecting only the first binding of a
cube, assuming that minimum commutes with parameter binding, or assuming
that the selected caller origin must stay nonzero.

The intended coverage relation is a weaker dependency, not equal edges:

```text
old support S -> Y
new support is ZERO, or {r} with r in S -> covering Y
```

Caller binding can change the preferred order and can map the retained
callee input to caller ZERO. Therefore, do not claim that every new caller
origin is the global minimum of the old caller union, or that it must always
be a nonzero member of that union.

Required proof/adapter checks before implementation acceptance:

- Complete local cubes still fire, and incomplete cubes do not fire.
- The selector returns a member, respects the order, treats ZERO as neutral
  and is independent of enumeration order for one fixed binding.
- Executable run-1 origins remain atomic by induction through all producers,
  requests, point resumes, calls and publication.
- Replacing a completed positive dependency with a selected member is a
  conservative approximation. Do not present it as semantic equivalence.
- Existing concrete fixed-target summary application preserves the old ND
  result AP and effective DEMAND layer when one selected member matches.
- FULL conditions, source actions, sink evidence, negative leaf acceptance,
  fields, exclusions, occurrence IDs and later-run ND behavior stay unchanged.
- Output coverage preserves needed run-2 entry guidance; actual FULL source
  reversal still visits every required discarded input and source identity.

The small model does not prove the whole mutable analyzer or every item in
this list. Its Horn inclusion theorem is for fixed discovered clauses. The
production pipeline, AP matching, NNF evidence and full refinement need their
adapter checks; those are not assumptions to hide inside a soundness claim.

Test both arrival orders, a missing leaf, duplicate/repeated leaves, OR
alternatives, same-origin MULTI, all-ZERO and mixed ZERO/nonzero inputs,
the preference order and shuffled allocation IDs. Check fixed source target
paths and tails, enabled/disabled static fields, complete sink evidence,
ordinary call matching and later FULL ND behavior. Compare old/new local
combination outputs with only their premise changed.

## Expected resource change

For N possible atomic originals at a fixed method/node/payload/layer, the
old label domain can contain `2^N - 1` nonempty subsets. The new executable
label domain contains at most N singleton originals plus ZERO. This does
not bound AP payloads, method count, local cube products or total time.

Run-1 E6 state should be zero by construction. The measured 8148066 retained
tuples and 8451997 slot bindings came from a path that the new design does
not enter. This does not predict equal savings in total heap or all work;
local conditions and more conservative singleton applications remain.

The first implementation should make this change alone. It should not also
change alias preparation, runtime GROUP projection or work queues. Retest
under the configured project settings, report the latest reached/completed
iteration, and check the no-FN requirement. No 60-second result is claimed.

## Spec and proof updates for implementation

Add a run-1 exception to the complete-premise contracts in `ap.md`,
`grouped-run1-statics.md` and their proof mappings. Replace run-1 ND support
closure claims with representative over-approximation and atomic-origin
invariants. Update the static conditional-supply and handoff proofs for
single-premise summaries. Keep the exact ND definitions and proofs for
later FULL forward runs. Update reference interpreters and test oracles
explicitly; do not weaken a test oracle without recording this semantic
change.

Source-bound audits are in
`/drive-testcomp/opentaint-go-rules/run1-statics-disabled-deep-20261021`:
`nd-elimination-source/run1-nd-elimination-source-boundary.md` and
`representative-run1-backward-recovery-source.md`. The measurement basis is
[the deep investigation](../reports/bidirectional-run1-statics-disabled-deep-investigation-20261021.md).
