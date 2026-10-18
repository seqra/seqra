# Run-1 static effects as immediate summaries

Date: 18 October 2026. Status: **design and ZERO-path review; analyzer unchanged**.
Language: ASD-STE100 Simplified Technical English. Defined technical terms,
code names and mathematical notation keep their stated meanings.

This revision refines [forward static handling](run1-group-static-pool.md).
It follows [proof-guide.md](proof-guide.md). It replaces the requirement for
extra static carry demands in **sink-origin wrapper paths**. It does not
remove the separate mark-group gate for a nonzero method input.

## 1. Decision for the wrapper example

**No extra run-1 demand edge is needed in `wrap` or `wrap_deep` for this
example.** Run-2 uses its existing ZERO-premise discovery and balanced-return
rules. It does not need a C.f/T initial in either wrapper.

```text
main:       static_set(); wrap()
wrap:       wrap_deep()
wrap_deep:  t = static_get(); sink(t)
static_set: t = src(); C.f = t
static_get: return C.f
```

Run-1 must still record the sink candidate and its group/occurrence. The
getter and setter need their productive summary demands. The absence of
a wrapper's C.f-premised method summary does not remove the internal sink
seed or the backward ZERO lane.

## 2. Current versus new static propagation

| Part | Previous proposal | This revision |
| --- | --- | --- |
| Read | Emit a conditional static-premised edge and propagate its nonstatic result to productive outputs/checks | Keep this rule |
| Write | Retain a writer continuation and static-output propagation machinery | Publish the reached static effect as a DEMAND summary immediately |
| Caller of a writer | Apply the writer result and handle a static current fact | Apply the summary through the real call bindings, then publish its static conclusion immediately |
| Local static current fact | Can require relay work through the remaining CFG | Do not enqueue the static conclusion through the remaining run-1 CFG |
| Dead-local sink in a wrapper | Previously treated as needing a field carry guide at each method | Use the existing backward ZERO-premise summary path |

```kotlin
fun reachedStaticWrite(input: ReachedWriteInput, output: StaticFact) {
    publishWritePresence(input.stage, output.scope)
    publishGroundedGroupsIfAvailable(input, output)
    publishStaticEffect(input.premise, output.asDemand(), input.support)
    // No enqueue of this static conclusion at local CFG successors.
}

fun returnedStaticEffect(call: ReachedCall, summary: StaticEffect) {
    for (result in applyOrdinaryCallBindingsAndActions(call, summary)) {
        if (result.current.isStatic)
            publishStaticEffect(result.premise, result.current.asDemand(), result.support)
        else
            enqueueOrdinaryResult(result)
    }
}
```

Use the existing summary store, deduplication and caller subscriptions.
Retain the actual writer/call stage and support. Conditional ordinary and ND
premises keep their real input bindings and complete support. ZERO cannot
satisfy an ordinary parameter or an ND guard.

Here, immediate means insertion at the reached write, without a walk to
the method exit. Keep the usual method-queue-empty batching of caller
notifications. It does not require a synchronous callback for each write.

An early static effect is a run-1 DEMAND candidate. It is not evidence that
the method returns normally, or a NORMAL identity record for a later run.
Do not emit a redundant `C.f -> C.f` identity. A real mark change or other
productive transfer at the same field is not such an identity.

Keep the published-write/read join and direct current-point group refinement.
Own-method reads and static rule checks must consume published effects too.
For `C.f = src(); sink(C.f)`, publishing only to callers would miss the
local sink candidate. A FLOW write admits a conditional read; only rooted
group supply can answer its group interest. Pending reads still wake on
late writes. The optimization cannot remove this local consumer contract.

## 3. The two meanings of a ZERO-premise edge

ZERO has no taint group. Its meaning depends on the analysis direction:

| Edge | Meaning |
| --- | --- |
| Run-1 `Z -> C.f/G` | A source-derived or otherwise grounded static effect candidate |
| Run-2 `Z -> C.f/T` | An internal sink needs C.f/T at the current point; there is no nonzero method-output premise |
| Run-2 `C.f/T -> Z` | This particular input query reached a source or another reversed operation that produces control ZERO |

The second edge does not assert that a source created C.f/T. It carries a
backward requirement. The third edge keeps its nonzero initial. Do not
replace it with `Z -> Z` or use it to start unrelated sink discovery.
Keep the existing record rule: a backward ZERO-premise sink path is not
persisted as a forward NORMAL source summary. Its requirement can still
participate in the next demand handoff.

```kotlin
fun backwardReachAtCall(edge: BackwardEdge, call: ReachedCall) {
    passReachWithSameInitial(edge)
    if (!edge.initial.isZero) return
    fireRecordedSinkSeeds(call)
    for (callee in call.targets) {
        subscribeZeroPremiseSummaries(callee, call, callerInitial = ZERO)
        startBackwardZero(callee)
    }
}

fun publishBackward(summary: Summary) {
    if (summary.initial.isZero)
        publishWithoutOldDemandExitRestriction(summary)
    else
        publishWithOrdinaryNonzeroQueryRules(summary)
}
```

The ZERO exception does not manufacture a marked initial. The sink seed
introduces T from its actual demanded rule occurrence. Ordinary transfer
and call binding preserve that full mark. A newly entered nonzero callee
query is a separate operation with its own admission contract.

## 4. Complete trace for the example

For this positive trace, let the actual source and demanded sink literal
use full mark T. Both project to group G. Other eligible members of G can
be queried, but they need not match this source. Run-1 contains these rows:

```text
static_set: Z -> C.f/G             [early static effect, DEMAND]
main:       Z -> C.f/G             [apply setter; publish immediately]
static_get: C.f/G -> return/G      [group guide for conditional getter]
wrap_deep:  sink occurrence and group G at its actual statement
wrap:       no C.f-premised method summary required
wrap_deep:  no C.f-premised method summary required
```

The getter can retain a FLOW implementation row plus its frozen group
annotation. Run-1 need not emit full T. An internal sink point demand is
not an entry-to-exit method summary.

Run-2 then performs:

```text
1. Ordinary Z traversal reaches main, wrap and wrap_deep.
   Each real call registers a zero-only subscription.

2. At sink(t), the recorded sink seed creates:
       wrap_deep: Z -> t/T

3. Reverse the getter call:
       enter static_get with return/T
       use its return/G -> C.f/G demand
       compute return/T -> C.f/T
       return to wrap_deep under the caller's original Z:
           Z -> C.f/T

4. Reach wrap_deep's forward entry and publish its B2 summary:
       wrap_deep: Z -> C.f/T
   The backward ZERO exception permits this publication.

5. Apply it to wrap's zero-only subscription, resume before wrap_deep():
       wrap: Z -> C.f/T
   Reach wrap's entry and publish the same kind of B2 summary.

6. Apply it to main's zero-only subscription, resume before wrap():
       main: Z -> C.f/T

7. At the preceding static_set() call, enter its marked writer query.
   Reversal of the F1 Z -> C.f/G effect supplies:
       demand(entry = C.f/G, exit = CONTROL_ZERO)
   Analyze the real reverse write and source with full T.

8. Record the actual full source hit for the next forward run.
   The result has unmarked ZERO and retains its original query premise.
```

The writer demand has an **explicit ZERO exit**, not a missing exit pattern.
The latter does not authorize ordinary paired summary publication.
No group is attached to ZERO at either end.

The dead local t removes a run-1 method output; it does not remove the
run-2 internal sink seed. This is why the two wrapper summaries arise in
run-2 even when their corresponding run-1 summaries do not exist.

## 5. Proof target and limits

For a finite chain of real reached wrapper calls, assume that C.f/T survives
each wrapper's ordinary reverse CFG/call stages from the call to that
wrapper's entry. Then a backward sink-origin `Z -> C.f/T` summary lifts to
every caller's Z lane. Proof by induction:

1. A real Z-to-Z call installs a zero-only subscription and callee ZERO.
2. A sink seed and ordinary getter reversal produce the deepest requirement.
3. The deepest ZERO-premise summary needs no run-1 wrapper exit pattern.
4. Its zero-only return keeps caller initial Z and the full requirement T.
5. The real surviving local transfer reaches the caller's entry. Repeat at
   the next wrapper.

This construction needs no field/group carry demand and creates no
C.f/T initial in a wrapper. It preserves actual caller stages. It does not
replace call order, CFG transfers or kills with a global pool jump.
For `wrap() { C.f = null; wrap_deep(); }`, the reverse overwrite kills the
old C.f/T requirement. That wrapper must not publish a surviving C.f/T
summary. A source or non-null RHS can instead resolve the requirement by
the ordinary reverse writer path. ZERO publication does not decide either
case in advance.

The getter's nonzero return/T query still needs its group permission. The
setter's nonzero C.f/T query uses the reversed early effect. The ordinary
RHS/source path, AP bindings, complete sink-candidate collection, conditions
and ND support remain required. The proof is not a checked JVM interpreter.

This result does not settle a different example:

```text
main: static_set(); pass_only(); wrap()
```

A concrete C.f/T requirement can enter `pass_only` as a **nonzero** input.
The current engine admits actual incoming queries directly. A strict new
per-method group gate would instead need a local guide or factored carry
authorization. This is a separate admission-policy choice. No new policy
for this case is selected by the ZERO-wrapper proof.

Nonreturning methods still need the ordinary control discovery guarantee
or an explicit internal-point start. The proof does not infer normal exit
reachability from an early write effect.

## 6. Current code evidence and validation

The [constructive model](lean/ApSpec/Run1StaticEarlySummaryZero.lean)
uses real reached call ports and field-specific wrapper-body pass records.
`runChain` computes the child response, zero-only return and surviving local
transfer at each step. `runChain_result` proves the resulting ZERO-premise
summary; `runChain_matches_certificate` relates it to the retained real-port
certificate. No run-1 wrapper demand is a parameter of either theorem.
The deepest real sink/getter summary and the ordinary AP/body transfer
certificates remain the stated inputs. A killing body pass is rejected.

The same model checks early source-write group projection, caller lifting,
the explicit ZERO writer-demand exit and exact full-source matching. It
does not prove general JVM source/NNF compilation or the new runtime write
publication. The focused module passes **34 controls** and an audit of
**474 declarations**, with **zero unexpected axioms**.

The existing [backward rules](ap.md#92-the-backward-run) already specify the
ZERO balanced-return exception. The current implementation has these paths:

| Code | Relevant behavior |
| --- | --- |
| `RunMethodLocalServices.fireSinkSeeds` / `seedFact` | Emit a full sink requirement under initial Z |
| `RunMethodCallServices.zeroAtCall` | Only genuine Z-to-Z work starts zero-only discovery and sink seeding |
| `matches` in `SubscriptionManager.kt` | A zero-only subscription accepts only ZERO-premise publications |
| `RunMethodCallServices.applySummary` | Return that requirement under the caller's original Z |
| `RunMethodAnalyzer.published` | Backward ZERO-premise summaries bypass old-demand restriction |
| `HandOff.toBackward` | Reverse a selected Z-to-field effect to field-to-explicit-ZERO demand |
| `EngineAlgebra.sourceHit` / `HandOff.toForward` | Preserve actual full source provenance independently of summary filtering |

Existing runtime control:

```sh
cd core
./gradlew :opentaint-dataflow-core:opentaint-dataflow:test \
  --tests org.opentaint.dataflow.bidi.CallZeroTest --console=plain
```

Result: **4 tests pass**. They include unrestricted ZERO returns, exact
resume points, and the nonzero-initial/current-ZERO distinction. They do
not implement or test the proposed early static write publication.

The complete Lean build passes **155 jobs**. The combined audit passes
**255 controls** across **3,574 declarations**, with **zero unexpected
axioms**. The [validation receipt](/drive-testcomp/opentaint-go-rules/run1-immediate-static-summary-20261018/validation/20261018T233005.300214Z/receipt.json)
has SHA-256 `53edf2c8be0acda5b4d49e9fd1283506524e001785c67199c372bc478f14294f`.
It records stable proof/runtime source hashes, the
[build log](/drive-testcomp/opentaint-go-rules/run1-immediate-static-summary-20261018/validation/20261018T233005.300214Z/build.log),
the [axiom/control audit](/drive-testcomp/opentaint-go-rules/run1-immediate-static-summary-20261018/validation/20261018T233005.300214Z/audit.log)
and the [four runtime test results](/drive-testcomp/opentaint-go-rules/run1-immediate-static-summary-20261018/validation/20261018T233005.300214Z/CallZeroTest.xml).

No project measurements were rerun. Latest measured runtime remains
`63d5481e4e4b69ecf042c1149bb574ad40fd1624`. Latest reached / completed engine /
accepted forward report: OWASP **run-7 / run-6 / run-5**; conductor
**run-1 / none / none**; ThingsBoard **run-1 / none / none**; Stirling-PDF
**run-2 / run-1 / run-1**.
