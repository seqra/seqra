# Run-1 static field diagnostic flag

Date: 20 October 2026. Status: selected diagnostic behavior.
Implementation base: `6ad77c6b1a968d8f8c308ca05b02555ea8ad3203`.
Follow [proof-guide.md](proof-guide.md).

This permanent flag is a **diagnostic ablation**. It removes real static
field flows from run-1. It is intentionally unsound for a program that uses
these flows. It is not an optimization. It is off by default. A result from
an enabled run is diagnostic evidence, not an acceptance result.

## Selected semantic change

| Case | Flag off | Flag on, run-1 |
| --- | --- | --- |
| Real static write, including a rule result with a field conclusion | Use the normal write, summary and service path | Drop the static write effect |
| Real static read | Use the normal read registration and injection | Do not register or inject the field read |
| Configured source attached to a static read | Permit its `ZERO -> lhs` injection | Drop this injection too |
| Static field service work | Use the normal field request, writer and reply path | Do not submit real-field work |
| A summary or call injection that requires a real static-field premise | Use the current path | Drop the field-dependent effect, even if its conclusion is local or a class marker |
| Independent exact global marker `S.C / EXACT / Group(g)` | Use the current path | Use the same current path |
| Ordinary nonstatic transfer or field overwrite | Use the current path | Use the same current path |
| Any later run | Use the current path | Use the same current path |

A real static field has the path prefix `S.C.f`. The exact global marker
`S.C.$` has no field accessor. It is not a real static field. It must not be
removed by a test for `base == STATIC` alone. If its derivation itself
requires a real static field, that dependency is part of the ablation. No
new recovery route is added. This flag creates no class-wide
fact, class STAR initial, or new marker route.

Class ANY query metadata can refer to the exact marker and to fields. Keep
its existing exact-marker branch. Do not use its field branch to inject a
real static field while the flag is active. Keep the current difference
between ANY/AT_AND_BELOW and strict BELOW.

Do not change group projection, conditions, cleaners, field exclusions,
field overwrite rules, layer rules, nonstatic requests, caller/ND joins,
ordinary sources, sinks or later-run demand matching. Keep the existing
universal static default summary setup. No backward repair or later-run
recovery guarantee is added.

## Setting and activation

The analyzer JVM system property is:

```text
opentaint.bidi.disableRun1Statics
```

Read it once when `RunConfig` is made. Store the effective value in that
configuration. Activate it only for run-1:

```kotlin
val disableRun1Statics = index == 1 && requestedDisableRun1Statics
```

There is no run-0. A missing property means `false`. Keep the Java boolean
property rule: only a value equal to `true`, without letter-case limits,
enables the requested flag. Do not reread the mutable system property for
each fact or operation.

For `runProjectAnalyzer`, the Gradle project property must be forwarded to
the analyzer JVM. A property on the Gradle JVM alone is not sufficient:

```kotlin
systemProperty(
    "opentaint.bidi.disableRun1Statics",
    providers.gradleProperty("opentaint.bidi.disableRun1Statics")
        .orElse("false").get()
)
```

Reuse the full approved project command. Add this one argument to enable
the ablation:

```text
-Popentaint.bidi.disableRun1Statics=true
```

Use `false` for the matching control, or omit the property. A direct JVM
launch uses `-Dopentaint.bidi.disableRun1Statics=true`. Log the effective
run-1 setting. Do not describe an enabled run as a normal analyzer run.

## Effect boundary

Use the current operation pipeline. Add a gate before the affected work can
enter a summary, a publication, a field service batch or a read injection:

```text
active = requestedFlag AND run == run-1

keep(effect):
    if not active:
        return effect
    if effect is a real static-field read injection:
        return none
    if effect is a real static-field write:
        return none
    if effect is real static-field service work:
        return none
    if effect requires a real static-field premise:
        return none
    return effect
```

A configured static-field source can emit `ZERO -> lhs`. Its result has a
local base. Detect it by its existing `FIELD_SOURCE` operation identity;
checking the result base alone cannot remove this injection.

A packed STATIC result can contain several fields and an exact class
marker. Select its leaves. Remove the field leaves. Keep each exact class
marker with the same premise, path, mark, exclusions and layer at this gate.
Do not discard the complete packed result if it contains a marker.

Reuse the existing forms and ordinary field transfer. Do not replace a
static write with a transfer from its right side to another position. Drop
only its static effect. Existing nonstatic continuation work stays in place.
Suppress field read registrations and field initial injection at the point
resume boundary too. An input that would otherwise produce no field result
must not leave a field request or writer behind.

## Diagnostic example

```java
void main() { static_set(); static_consume(); }
void static_set() { C.f = source(); }
void static_consume() { Object x = C.f; sink(x); }
```

With the flag off, run-1 can produce a group-valued writer for `C.f`, admit
the read, and produce demand guidance to the sink. With the flag on, it
produces no `C.f` writer effect or read injection. The static path can be
absent from the result. Later runs can lack its mark demand. Do not claim
that a later run must restore the missed flow.

An independent exact marker, for example `ZERO -> S.C.$ / Group(g)`, is outside
this ablation. Its current transfer and matching remain available. An
ordinary `x.f = y` also uses its current overwrite and transfer rules.

## Local proof contract

The constructive model is
[Run1StaticFieldAblation.lean](lean/ApSpec/Run1StaticFieldAblation.lean).
Its effects are typed as ordinary transfer, ordinary overwrite, exact
class carrier, field read injection, field write, field service work, or
an effect with a required real-field premise. An independent class carrier
uses the class constructor. A field-dependent result uses the field-dependent
constructor, including a local or class-marker conclusion. Each effect keeps an opaque payload for its premise, image, group and layer.
The model changes no payload. A class carrier has no field-tail constructor.

| Law | Lean theorem |
| --- | --- |
| Off keeps the complete supplied effect list | `off_identity` |
| Later runs keep the complete supplied effect list | `later_identity` |
| A retained effect is exactly a supplied effect that passes the gate | `member_exact` |
| A local filtered list is a subset and has no greater length | `local_output_subset`, `local_count_nonincrease` |
| Repeating the gate makes no change | `idempotent` |
| Batching does not change the local result | `append_exact` |
| Ordinary transfer and overwrite effects stay present with the same data | `ordinary_transfer_unchanged`, `ordinary_overwrite_unchanged` |
| Exact class carriers stay present with the same data | `exact_class_carrier_unchanged`, `singleton_class_identity` |
| Active run-1 has no real-field read, write or service effect | `no_read_injection`, `no_field_write`, `no_field_service_effect`, `no_field_dependent_effect`, `every_selected_effect_nonfield` |
| Enabling the gate is not equivalent to the normal field path | `Controls.intentionally_not_equivalent` |

The subset and count laws compare one supplied candidate list with its
filtered list. They are not a whole-engine report-inclusion theorem. Changed
demands, later scheduling and source/condition evaluation can change future
work. The model does not prove a speedup, lower peak heap, termination or
soundness for an enabled analyzer.

The runtime adapter must map **all** selected effects to the gate. In
particular, it must cover operation edges, the local result of a static-field
source, packed summary results, read registration and field initial resume.
Tests must check these boundaries, off/later identity, mixed packed results,
exact markers, and ordinary nonstatic transfer/overwrite. A proof of the
filter alone does not prove this adapter coverage.

Run the narrow proof gate:

```bash
cd spec/lean
lake build ApSpec.Run1StaticFieldAblation
lake env lean run1-static-field-ablation-audit.lean
```

The audit checks all declarations in the new module for unexpected axioms
and runs executable controls. No Classical or choice axiom is permitted.
The model also gives a concrete nonempty field-effect list that becomes
empty, so an equivalence claim fails by an executable witness.

## Measurement contract

Use the same committed analyzer version for A and B. Use the same project,
inputs, rules, entry points, memory, timeout, JVM options, profiler settings
and run command. Change only the flag from `false` to `true`. Keep the fixed
project configuration. Use separate output paths and the normal serial
workload/reader locks. Record the flag in each manifest and log.

Report engine time, outcome, profiles, heap evidence and latest reached,
completed and accepted run labels, for example `run-1`. Keep missing run
completion and uncovered profile time explicit. Do not call fewer findings
an improvement or use the enabled result to pass a no-FN check. An enabled
run measures the cost removed by this ablation; it does not measure an
implemented sound replacement for static handling.
