# A07 demand operations

`Demand.kt` adds zero, initial startup, policy emission, demand emission,
reduction, structural demand selection and request actions to `ApOps`.
Initial startup preserves normal must facts and their field exclusions.
The engine checks the restricted-forward mode before startup in E03.
Abstract demand entries share a FLOW initial across concrete marks and exits.
Concrete demand entries use the direct annotated meet and reject abstract
added marks. Emission creates no requests.

Reduction uses the direct per-leaf operation. It checks whole-premise
containment, then exit locations and marks. Raw results retain their layer.
Typed packing merges only exact group keys. Generic abstract exact results
remain available through `restrictReference`; typed packing rejects them.
Structural demand selection reads headers only and returns the original
value. It stays separate from raw record eligibility and native read views.

Request actions implement concrete answers, abstract mark climbs and static
position answers or climbs. They retain the original caller premise and
request on a climb. `CallerRef` retains caller context, premise, layer and
call statement; A08 adds its store.

## Tests before implementation

The first RED failed on the missing reduction and selection APIs
(`/tmp/bidi-a07-red.log`). The first compile check found an incorrect
`CommonInst` package. The plan was updated before fixing the import.
The focused run passed 36 of 37 tests. Its one failed test passed an
unmatched FLOW value to summary transfer. The plan was updated before
correcting that test to check satisfaction rejection and request climb.
No production transfer behavior changed for that test.

| Test class | Tests | Checks |
|---|---:|---|
| EmissionTest | 12 | 810 canonical comparisons, full startup annotations, zero/policy, shared abstract emission, exits, must meets, representation guards, static bases, depth 32,768 and cancellation. |
| RestrictionTest | 9 | 7,722 annotated reduction cells, whole-premise containment, exit marks/tails/E, exact-key packing, duplicate results, representability exceptions, depth 32,768 and cancellation. |
| DemandSelectionTest | 6 | Raw header truth tables, independent record eligibility, native read views, reduction independence, huge shared DAG and deep paths. |
| RequestActionTest | 10 | Explicit answer tables checked against the direct model, exclusions, caller metadata, static position rows, policy guards, answer startup and standing climbs. |

Read-only reviews found no discrepancy in these operations against the
specified contracts. No new Lean proof was needed.

## Final gate

```bash
./gradlew --console=plain --continue \
  :opentaint-dataflow-core:opentaint-dataflow:cleanTest \
  :opentaint-dataflow-core:opentaint-dataflow:test \
  :opentaint-dataflow-core:opentaint-jvm-dataflow:compileTestKotlin \
  :opentaint-dataflow-core:opentaint-go-dataflow:compileTestKotlin \
  :opentaint-jvm-sast-dataflow:compileKotlin compileKotlin
```

The repeated gate passed in 28 seconds (`/tmp/bidi-a07-final-gate.log`): 528 common tests
in 47 classes pass, with no failures, errors or skips. JVM and Go test
sources, SAST and application compilation pass. XML is saved in
`/tmp/bidi-a07/final-common/xml`. `git diff --check` passes. A07 is complete.
