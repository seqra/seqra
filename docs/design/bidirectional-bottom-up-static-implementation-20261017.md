# Bottom-up static FLOW implementation

This document refines [the selected contract](../../spec/run1-bottom-up-static-propagation.md).
Status: implemented at `ab6c3cd00`, 17 October 2026; project measurements closed.
It adds no new approximation. The checked scalar evaluator remains the reference.

## Packed identity lane

Use an immutable field-only FLOW trie as an identity descriptor. Each leaf
`[C,f]` means exactly `i_f -> C.f.* [DEMAND]`. The descriptor is not an AP
fact with a common premise. In particular, decoding two leaves never creates
cross-field edges. Its header is fixed: STATIC, STAR, Empty E and empty mx.
The entry supply and each CFG/call-stage location merge fresh geometry with
the existing trie union/delta operations. Unchanged paths share trie nodes.

```kotlin
decode(entries, field) = Subscription(
    callee, CallerRef(caller, initial(field), DEMAND, call),
    DEMAND, startFact(initial(field)).withLayer(DEMAND))
```

An ordinary incoming supplier projects the same complete field domain as the
reference abstraction, once, into this lane. Concrete answers and direct
class points keep their existing projector. No field-request plan is used.

## Local operations

Use the same statement, entry/exit-rule and ordered call-stage interpreters.
A syntactically certified root keep with no relevant filter, predicate or
other operation passes the descriptor without scalar evaluation. At any
other operation, select a conservative set of possibly affected fields and
decode those fields into the ordinary evaluator. Unknown forms select all
fields. The remaining fields pass only when a root keep establishes identity.

```kotlin
for (initial in affectedEntries) evaluateOrdinary(initial, exactInput(initial))
continuePacked(certifiedUnchangedEntries)
```

Process operation requests and observations even when all value results die.
An excluded-target creator emits its original-slot request before its spatial
overlap test. Such an operation selects all same-base identity members, even
when its target field is disjoint. Actual broad static-root TAINT/FLOW data
uses ordinary operations. It is not the synthetic root default and must not
be filtered as a control carrier.
An output returns to the packed lane only when its full payload equals its
own canonical static original at DEMAND. W, transformed static, static-to-local,
concrete and ND outputs keep ordinary state and full call bindings. This
includes nontransparent prefixes, definite kills and self-assignment.

## Calls and requests

At the actual callee stage, register one relation for the full caller key,
call statement, full target key, and exact from/to call-plan stages. Merge its identity geometry. Register before
replaying stored publications and requests. Default publication is ordinary
storage publication; its application resumes the packed input after the
callee stage. Other summary rows decode only matching identity members and
then use ordinary summary application and the complete call suffix.

New identity geometry also supplies the callee's abstraction through counted
delivery. A new publication visits existing relations; a new relation replays
existing publications. No ZERO event substitutes for an input. Dynamic mark
rows use those same exact field descriptors and route to the original field
slot; inherited frozen rows need no duplicate upstream offer. Full inputs
retain ordinary request matching and concrete replay.

## Completion and validation

The actor's existing counted Work reservation covers its packed queue and
temporary request commit. All remote descriptor and summary deliveries are
counted before their parent reservation ends. Release descriptor state only
after the run joins. The ordinary summaries, default and owner remain visible
to typed handoff, including relay-only owners.

Required tests compare decoded optimized closure with an independent scalar
run-1 oracle on complete finite fixtures. Cover local writes, same-field
changes, calls with prefix/suffix effects, late input/effect/request orders,
recursion, actual concrete answers and whole ND conditions. Representation
tests must show that a transparent chain with many fields uses per-location
descriptor relations rather than per-field input subscriptions.

The implementation was committed before the fixed project measurements.
The [project report](../reports/bidirectional-run1-bottom-up-static-20261017.md)
records OWASP **run-7 / run-6 / run-5**, conductor **run-2 / run-1 / run-1**,
ThingsBoard **run-1 / none / none**, and Stirling-PDF **run-2 / run-1 / run-1**
as latest reached/completed/accepted iterations. Conductor and PDF complete
run-1 within 60 seconds. OWASP takes 265.789 seconds; ThingsBoard fails in
run-1. All four candidate invocations end abnormally. Physical factoring is
measured, but overall project performance/precision acceptance is not met.

## Code map and checked boundary

All production paths below are relative to
`core/opentaint-dataflow-core/opentaint-dataflow/src/main/kotlin/org/opentaint/dataflow/bidi/`.

| File | Responsibility |
| --- | --- |
| `engine/StaticIdentityEntries.kt` | Diagonal descriptor, fresh union/delta, conservative affected-field selection |
| `engine/Run1StaticFlow.kt` | Actor-owned entry/node descriptors, pending batches, scalar boundaries and release |
| `engine/Run1StaticFlowPolicy.kt` | Early sticky DEMAND and exact canonical identity-leaf compaction |
| `engine/RunMethodCallServices.kt` | Ordered real call stages, packed default resume and ordinary effect continuation |
| `engine/SubscriptionManager.kt` | Full-port relation, register-before-replay, later effects and original-slot requests |
| `engine/RunEvent.kt`, `engine/UnitRunner.kt` | Counted descriptor delivery, including same-unit delivery |
| `engine/Run1Abstraction.kt`, `engine/Run1StaticProfiles.kt` | Exhaustive actual supply, shared legal universe, local-only read metadata |
| `driver/IterationDriver.kt` | Ordinary NORMAL persistence at the complete run-1 barrier |

The existing run-1 all-summary typed handoff remains in place. A NORMAL row
can be persisted and also guide the next phase. This change does not make a
DEMAND row a reusable NORMAL record. The ordinary native/reverse record
admission guards remain in force.

Tests decode the packed lane only in test fixtures and compare all nine
semantic observation components against the independent ordinary scalar
oracle. Production keeps no decoded history. A 16-field transparent chain
has 32 diagonal call memberships in two physical static relation keys;
these are representation counts, not bytes or a project saving estimate.

The selected JVM call-plan builder has one ordinary callee stage. The new
packed relation retains its exact stage pair; the existing ordinary return
adapter selects the plan's ordinary callee stage. Arbitrary frontend plans
with several ordinary callee stages are outside this adapter check.

The unchanged constructive Lean modules prove the finite scalar concept and
transport equivalence. The runtime tests cover selected lowering/storage/
scheduler adapters. They do not constitute a formal verification of Kotlin,
all JVM forms, or every concurrent schedule. The [runtime validation report](../reports/bidirectional-run1-bottom-up-static-validation-20261017.md) records
executed gates and distinguishes strict application precision failures from
passed equivalence and persistence controls.
