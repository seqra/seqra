# Immediate static summaries: demand-completeness review

Date: 18 October 2026. Status: **counterexample review; analyzer unchanged**.
Language: ASD-STE100 Simplified Technical English. Defined technical terms,
code names and mathematical notation keep their stated meanings.

This review follows [proof-guide.md](proof-guide.md) and checks the
[clean static-summary design](run1-immediate-static-summaries.md).

The later selected [shared static demand](run1-universal-static-carry.md)
closes the concrete relay hole below. The counterexample applies to the
variant without that default. The shared default proves the local relay
permission result; it does not by itself prove the whole analysis pipeline.

## 1. Conclusion

The current specification does **not** support an unconditional theorem
that no program can lose a required backward demand.

The wrapper ZERO proof is valid within its stated scope. It does not cover
a nonzero static query entering an intervening callee. If the design keeps
the proposed strict per-method group gate and removes static carry
authorization, the finite normal-return example below is a counterexample.

The current Kotlin engine admits actual incoming queries independently of
old guides. It therefore avoids this particular loss. Do not report the
counterexample as a current runtime false negative. The selected shared GROUP
default also avoids this loss. A general proof must compose that permission
with productive demands and candidate creation.

## 2. A counterexample inside the normal-return scope

```text
main:
  set_f()
  pass_only()
  consume_f()

set_f:
  C.f = src_T()
  return

pass_only:
  C.g = null             // g differs from f
  return

consume_f:
  t = get_f()
  sink_T(t)

get_f:
  return C.f
```

Use resolved nonconstructor calls, with no external model or applicable
persisted record. The actual source produces full T; the sink demands T.
The write to g does not overwrite f. There is a finite real source-to-sink
flow. An empty `pass_only` method gives the same demand issue.

Let G = owner(T). Early run-1 publication gives:

| Method | Relevant run-1 information |
| --- | --- |
| set_f | `Z -> C.f/G` |
| main | `Z -> C.f/G` |
| get_f | `C.f/G -> return/G` |
| consume_f | Internal sink occurrence and group G |
| pass_only | No productive C.f read/write summary and no C.f/G demand |

The synthetic FLOW `S.* -> S.*` DEMAND default in `pass_only` is not a G
demand. It is not persisted as a NORMAL identity record either.

Run-2 reaches this state:

```text
consume_f:  Z -> C.f/T      // internal sink, getter reversal
main:       Z -> C.f/T      // returned before consume_f()

next backward call: pass_only()
actual callee input: C.f/T  // nonzero; this is not ZERO discovery
```

Under the strict local group gate:

```kotlin
fun admitStrict(method: MethodKey, query: FullQuery): Boolean =
    frozenGroupGuides.matches(method, query.geometry, query.mark.group)

// No matching C.f/G guide in pass_only: the actual query stops here.
```

No C.f/T result then returns from `pass_only`, so that requirement cannot
continue to `set_f`. The independent ZERO-to-ZERO traversal of `pass_only`
contains no internal sink seed. It returns control ZERO, not C.f/T.
The wrapper ZERO theorem does not convert control into this marked fact.

## 3. Why the real call plan does not bypass the callee

This example does not assume an artificial mandatory callee visit. The
current JVM call compiler has these rules:

| Implementation | Rule |
| --- | --- |
| `JIRCallPlanBuilder.touched` | Every call touches ClassStatic |
| `bindIn` and `bindBack` | Bind S unchanged in both directions |
| Resolved nonconstructor call | The added-to-returned path uses the callee; the unresolved and constructor identity alternatives do not apply |
| `CallPlan.reversed` and `passOverBases` | Add a bypass only for targets outside the touched set; touched S gets no such bypass |
| `RunMethodCallServices.process` | A touched nonzero static fact uses the ordinary call stages |

The relevant producers are the [JVM call builder](../core/opentaint-dataflow-core/opentaint-jvm-dataflow/src/main/kotlin/org/opentaint/dataflow/jvm/bidi/interp/JIRCallPlanBuilder.kt)
and the [call reversal](../core/opentaint-dataflow-core/opentaint-dataflow/src/main/kotlin/org/opentaint/dataflow/bidi/interp/CallPlan.kt).
Thus C.f/T must enter the resolved callee. The write to g preserves that
requirement if the body is analyzed. Refusing the input loses a needed
query before the disjoint write can be checked.

## 4. The two policies must remain distinct

The existing engine instead has this conceptual rule:

```kotlin
fun admitCurrent(added: ActualInput, oldGuides: GuideView) {
    startCanonicalQueriesFrom(added) // preserve actual full marks
    startAdditionalIntersections(added, oldGuides)
}
```

`StrongQueries.admit` derives its canonical query from the actual added
fact before it reads concrete guide intersections. `StrongQueries.permits`
also authorizes publication for that admitted current premise. A complete
policy must cover both admission and result publication.
See the [current query implementation](../core/opentaint-dataflow-core/opentaint-dataflow/src/main/kotlin/org/opentaint/dataflow/bidi/engine/StrongQueries.kt).

This rule does not create T from FLOW. T is already in the actual backward
requirement from the sink. An old guide can refine that query; an absent
old guide cannot erase it. In the example, C.f/T enters `pass_only`, its
body preserves f, and the resulting identity answer resumes the caller.

Two designs can close the demonstrated hole:

1. Preserve actual incoming-query admission and its result authorization.
2. Keep the strict local gate and retain explicit factored static carry
   permissions that admit both the input and its required result.

The later shared static default selects the second solution as an implicit
root GROUP relation, without a per-method/field product. Immediate-summary
publication alone would not have supplied that relation.

## 5. What a complete proof must establish

The useful target is relative completeness within the existing analyzer
contract, rather than a claim about all JVM executions:

```text
for each finite full-mark witness admitted by the reference semantics:
  run-1 records a supported sink candidate and its group demand;
  run-2 can start that sink requirement;
  every required reverse local/call step is admitted;
  its needed results survive publication and reach the real caller port;
  all required source hits and support dependencies remain available.
```

The proof must construct the new witness. It must not assume that every
required guide already exists. In particular, it must cover:

| Boundary | Required result |
| --- | --- |
| Source and writer groups | Every real full witness projects to rooted group evidence; hypothetical initials and requests cannot supply a group |
| Early effects | Capture the real static write before stopping local static propagation; caller application retains actual bindings and complete premises |
| Local reads/checks | Own-method and remote writes wake waiting consumers; direct static checks and conditional sources also retain their required candidates |
| Sink seeds | Preserve full bound occurrences, all required positive NNF branches, internal stages and group-to-full expansion |
| ZERO path | Keep the proved zero-only discovery/return rule and ordinary body kills |
| Nonzero path | State admission and publication rules that cover transparent calls as well as productive readers/writers |
| Rewrites/guards | Keep real group relations, full mark operations and every required ND/condition premise |
| Scheduler and handoff | Join old/new registrations and drain counted work before freezing; retained observations cannot depend on an event being early |

The existing positive group and interface models prove parts of this chain.
The ZERO wrapper model proves another part. None of these proofs, alone,
is an end-to-end compiler theorem for the new immediate-summary pipeline.
No second definite loss was found in the normal-return fragment with
actual-query admission and the stated read/source/call contracts. That
finding is not a universal proof.

The proof domain must also retain the reference interpreter's AP, source,
normal-call and exception contracts. Exceptional interprocedural effects
outside that contract are not an optimization-equivalence counterexample.
For JVM ZERO discovery, `JIRExitWiredGraph` already connects regions that
cannot reach an exit to the exceptional exit. A plain infinite loop is
therefore not the missing-demand counterexample used in this review.

## 6. Constructive model and validation

The [finite counterexample model](lean/ApSpec/Run1StaticEarlySummaryGap.lean)
implements both policies on the same source, empty resolved relay and sink.
The extra write to g in section 2 is not needed for the model's empty relay.
The code review establishes the required resolved-call shape for both cases.

`Counterexample.witness` contains executable program data and checked
results: the full reference flow exists, strict admission loses it, and
actual-input admission retains it. `strict_universal_no_loss_false` refutes
the universal strict-policy claim even in this small normal-return language.
It does not select a witness with classical choice.

`actual_query_chain_identity` proves that actual-input admission preserves
the exact actual query across any finite list of these empty relays. It
does not prove completeness of all early static summaries, arbitrary
callee bodies, NNF compilation or the JVM. `zero_child_cannot_answer_static`
also proves why independent ZERO control cannot repair the rejected query.

The focused module passes **25 controls** and an audit of **244 declarations**,
with **zero unexpected axioms**. These checks separate group matching from
exact source matching, reject fabrication without an actual input, and keep
the getter/sink prerequisites explicit.

The current runtime controls also pass: **4 CallZeroTest tests** and
**8 StrongQueriesTest tests**. They check actual queries without old guides,
complete current-premise authorization, no concrete mark from FLOW, and the
ZERO balanced-return distinction. They do not implement the proposed early
write publication or prove a whole JVM iteration.

## 7. Measurement scope

The analyzer implementation is unchanged. No project benchmarks were rerun.
Latest measured runtime remains `63d5481e4e4b69ecf042c1149bb574ad40fd1624`.
Latest reached / completed engine / accepted forward report: OWASP
**run-7 / run-6 / run-5**; conductor **run-1 / none / none**;
ThingsBoard **run-1 / none / none**; Stirling-PDF **run-2 / run-1 / run-1**.
