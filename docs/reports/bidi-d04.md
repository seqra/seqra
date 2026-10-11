# D04 current iteration acceptance

Current sequence tests use joined native run managers, actors, forms, request
joins and summary services. The driver applies the complete barrier after
all runs, including its last run. Run 1 starts at limit 1. No current test
uses a zero limit. Raw facts, requests, publications, selected summaries,
links, records, source hits, demands, seeds, witnesses and frontiers are
checked at their specified boundaries.

## Fixed current programs

| Tests | Checked behavior |
|---|---|
| `AnyTaintIterationTest` | G/C/I, S/SD, F76 factory, source versus pass and AP5 root EXACT cleaner. Getter run 3 uses its normal backward record with zero only. Caller support confirms the callee sink. Setter excludes name and admits email. Factory native must/E and exact U leaves stay separate; replay creates no source hit. |
| `CurrentModeIterationTest` | Two marks share one backward FLOW initial; normal backward records give zero-only run 3. Cleaner raw mark exclusion stays in the summary while hand-off normalizes it to STAR. |
| `CurrentModeActionsTest` | Mark-changing pass asks once in run 1; its concrete record finds U. Named partial field cleaner lowers to read, temporary clean and strong write. It removes f and preserves g/k. |
| `CurrentSequenceTest` | WRAP uses five positive limits, a normal raw return record and zero-only wrapper after run 1. END retains the early confirmation and carries a later sink requirement through the triggered end fact. CONJ reverses literal branches in DEMAND. Making those branches NORMAL produces the false positive control. A nonreturning worker reports without a normal summary. Incomplete run 3 retains the completed mixed report. |

The named field cleaner retains a normal outside STAR/E{f} branch. Its normal
backward FLOW record can supply g and k. A separate exact T(g) record also
exists. Selected f pieces normalize to abstract f demands; they do not meet
run-3 exact g/k inputs. These checks use the normative F74/F75 tables. They
do not infer that g can only come from one concrete record.

Identity and setter stop in run 1 at limit 1. Their policy variants change
only unused later limits. Generic AP tests cover positive limits 2 and 3.
DLINK's actual five-run demand-link control remains in D03.

## Generated programs and labelled historical data

`GeneratedCurrentProgramTest` has 120 program/mode cells. Each cell runs with
same-unit and cross-unit schedules: 240 executions. Seeds vary event order,
work quanta and duplicate delivery. The programs cover reads, cleaner bases,
three reaches, marks, depths, static class prefixes and conjunctions. Actual
processed objects, raw/selected summaries and full witness headers agree
with a direct per-path closure. Bounded stored-union comparisons use depth 2;
long source and read paths are also checked directly. Synthetic multi-field
edges are generic AP tests.

`ConcreteDesignDifferentialTest` uses an independently scanned current
barrier and a labelled F71 comparator from proof snapshot fb789d0ab. Current
actual report states, outgoing demand unions, exact seeds and direction-
separated raw native records agree at every complete generated barrier,
including the final barrier. Twenty-four generated iterations keep every
historical finding with an equal or better state. Fixed exact-source getter, mark-changing pass, primitive partial-cleaner,
cleaner normalization, WRAP and CONJ programs have the same comparison. Historical equations remain explicit and do not use the current
DemandStore, selector, HandOff or Support to compute expected reports.

The executable old backend has exact sources and excludes must sources,
F76 must views, alias guards, END_FACTS GEN and named field-action stages.
Current full-engine tests cover these boundaries separately. V4 closes the fixed G/C/I/S/SD historical golden comparisons and separately
labelled END/factory boundaries. Their version labels and expected states
remain separate from the executable exact-source comparator.
Neither the finite generator nor these vectors proves R6 for all programs.

## Corrections and gate

Saved RED traces identify fixture expectations and test-only oracle defects.
Backward zero-to-zero selection has no explicit demand insertion. R1 also
stores WRAP's argument identity record. Backward frontiers count outgoing
source seeds. Conjunction answers below the root policy can start in DEMAND.
Read-only CONJ calls need input pass-over to reach both inputs at the second
call. The partial cleaner uses shared U FLOW and selected f pieces.

The direct barriers now count raw demand summaries for NO_DEMAND_EDGE.
Historical Cross reads the ordinary reversed premise with empty field E;
its reversed result owns the shared edge E. Above-path historical emission
keeps the demand STAR exclusion. Version guards reject forms outside the
declared comparator domain. New executable controls precede these fixes.
Production code needs no D04 correction.


The fresh gate passes 1,217 common tests in 134 classes, with no failures,
errors or skips. All 37 D04 tests pass. JVM, Go, JVM SAST and application test
sources compile. The gate takes 38 seconds; its log is
`/tmp/bidi-d04-full-gate.log`. Fresh XML is saved under
`/tmp/bidi-d04/opentaint-dataflow/xml`. All 1,069 new common annotated tests
compile to void and appear in this XML
(`/tmp/bidi-d04-common-discovery.txt`).

Run the gate from `core`:

```bash
./gradlew --continue :opentaint-dataflow-core:opentaint-dataflow:cleanTest :opentaint-dataflow-core:opentaint-dataflow:test :opentaint-dataflow-core:opentaint-jvm-dataflow:compileTestKotlin :opentaint-dataflow-core:opentaint-go-dataflow:compileTestKotlin :opentaint-jvm-sast-dataflow:compileTestKotlin :compileTestKotlin --console=plain
```

The historical domain RED has six expected failures
(`/tmp/bidi-d04-domain-red.log`, XML in `/tmp/bidi-d04/domain-red-xml`). All
15 historical/domain/direct-summary tests then pass in three seconds.
All 11 fixed/generated differential tests pass in two seconds
(`/tmp/bidi-d04-fixed-second.log`). Earlier fixture RED paths are recorded in
[the plan](../../spec/plan.md). `git diff --check` passes.
