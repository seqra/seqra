# Major JVM test findings

The user requested cleaner normalization from `saloed/cleaner-action-normalisation` on 12 October 2026. The shared JVM resolver now uses commit `25a5307cf`, with a guard for broad removal reach. The normalization and retest results are recorded below.

The latest full default runs pass. The latest bidirectional JVM run has 504 tests and no failure. The query run has 215 tests and 10 failures: seven sample methods and three existing probes. All 188 normal positive sample runs are found. Unexpected negative samples decrease from 28 to 12. Ten remaining samples require an end-fact check. Two report from an unannotated `clean()` callee. The failed-prescan defect in issue 1 also remains.

## Results before normalization: 11 October 2026

The full JVM application suite and the full Java query-language suite were run on 11 October 2026. The tests select the real bidirectional entry with `OPENTAINT_TEST_BIDI=true`. Run 1 uses limit 1. Later runs can use limits 2 through 9. Production files were not changed in that test session. The following counts and the 20-row failure table describe that session before normalization.

The normal test inputs have no missed positive samples. The query tests run 188 positive sample occurrences and 141 negative sample occurrences. They find 28 unexpected negative samples in 20 test methods. These samples produce 29 report entries, because `TrickyPatterNot$NegativeSimple` has two entries. All 29 entries are `CONFIRMED`. Nine more negative samples have existing ignore annotations. These counts describe executions, which include the five existing repeated bidirectional cases. They do not prove that disabled cases have no false negatives.

## 1. A failed prescan can return an OK empty result

Status: confirmed by a real fault test. This defect can cause false negatives.

`TaintAnalyzer.prescan()` logs and catches an exception from `runAnalysis()`. The bidi branch then checks only that the prescan jobs have stopped. It does not check the prescan status. `JIRBidiAnalysis` copies the rule IDs, selects those rules and starts a fresh driver. A complete driver report replaces the failed prescan status.

The new `BidiFailedPrescanTest` uses `example.Rule$PositiveSimple`. Its control finds one CONFIRMED sink. Its fault resolver throws once during the real prescan. The old engine records EXCEPTION and its jobs stop. The copied rule-ID set is empty. The real Semgrep provider removes all rule groups. The real bidi driver then returns OK with zero findings:

```text
control: prescan=OK        analysis=OK findings=1
fault:   prescan=EXCEPTION analysis=OK findings=0
```

The test requires the failed final status. It remains red. No fake report, actor or timeout is used. The entry must reject an incomplete prescan before it selects rules and starts a new driver. It must retain the failed status.

Owners: [prescan and entry transition](core/opentaint-jvm-sast-dataflow/src/main/kotlin/org/opentaint/common/sast/dataflow/TaintAnalyzer.kt), [joined-job check](core/opentaint-dataflow-core/opentaint-dataflow/src/main/kotlin/org/opentaint/dataflow/ap/ifds/TaintAnalysisUnitRunnerManager.kt), [rule selection](core/src/main/kotlin/org/opentaint/common/sast/rules/SemgrepRuleProvider.kt), and [fault test](core/opentaint-java-querylang/src/test/kotlin/org/opentaint/semgrep/BidiFailedPrescanTest.kt). TIMEOUT and OOM use the same unchecked status path in the source. The executed fault test covers EXCEPTION.

## 2. Generated conditional cleaners do not act

Status: observed before normalization. The 12 October retest below identifies the cases that remain.

Eighteen failed rule families, with 26 unexpected negative samples, generate 45 cleaner rules. Every cleaner has a positive `ContainsMark` state condition. The converter adds this check. The new interpreter applies a cleaner only when its condition is statically true. It also excludes a conditional cleaner from the summary rewriter. The old analyzer evaluates these cleaners on a tainted fact.

For `example.Rule`, the source marks a value with automata state 2. `clean(value)` must remove state 2 when that state is present. Its generated condition still contains `ContainsMark(state2, Argument0)`. The new call plan has no cleaner for it. The value keeps state 2 and reaches `sink(value)`. All three negative samples produce CONFIRMED findings with no end facts.

This is not evidence of a defect in the primitive AP cleaner. Before normalization, generated self-mark conditions reached the interpreter unchanged. [Interpreter §4.2](spec/interpreter.md) requires unconditional resolved cleaners. `JIRRuleForms.cleanSteps()` and `JIRCallPlanBuilder.rewriterSteps()` implement that rule. The shared resolver now simplifies each condition against its own action before these checks.

Owners: [state checks added by conversion](core/opentaint-java-querylang/src/main/kotlin/org/opentaint/semgrep/pattern/conversion/taint/AutomataToTaintRuleConversion.kt), [cleaner selection](core/opentaint-dataflow-core/opentaint-jvm-dataflow/src/main/kotlin/org/opentaint/dataflow/jvm/bidi/interp/JIRRuleForms.kt), and [call and summary cleaner selection](core/opentaint-dataflow-core/opentaint-jvm-dataflow/src/main/kotlin/org/opentaint/dataflow/jvm/bidi/interp/JIRCallPlanBuilder.kt).

## 3. Report confirmation does not verify end facts

Status: confirmed output gap, documented for phase 3.

Before normalization, twelve unexpected negative samples in seven failed test methods had nonempty `trackFactsReachAnalysisEnd` requirements. The local service stores these facts in each sink witness. `Support.confirm()` checks NORMAL layers and call support. It does not check that an end fact reaches the end of analysis. The report and the JVM output adapter return the confirmed witness without that check.

Ten of the twelve samples call a cleaner after the reported sink. `CleanerAfterSink0` is a direct example: `src(); sink(value); clean();` must fail the pattern with a suffix exclusion. The report still contains the sink. Two other samples, the ObjectMapper ellipsis case and the reverse artificial sequence, call the cleaner before the reported sink. They also have conditional cleaners. The end-fact check alone is not proved sufficient to correct any row whose conditional cleaner did not act.

[Interpreter §4.1](spec/interpreter.md) and [AP §11.1](spec/ap.md) explicitly defer this check. These twelve samples overlap issue 2. They are not twelve additional false positives. The sixteen samples with empty end facts cannot be explained by this output gap.

Owners: [witness end facts](core/opentaint-dataflow-core/opentaint-dataflow/src/main/kotlin/org/opentaint/dataflow/bidi/engine/RunMethodLocalServices.kt), [confirmation](core/opentaint-dataflow-core/opentaint-dataflow/src/main/kotlin/org/opentaint/dataflow/bidi/driver/Support.kt), [report](core/opentaint-dataflow-core/opentaint-dataflow/src/main/kotlin/org/opentaint/dataflow/bidi/driver/Report.kt), and [JVM output](core/opentaint-dataflow-core/opentaint-jvm-dataflow/src/main/kotlin/org/opentaint/dataflow/jvm/bidi/JIRBidiAnalysis.kt).

## 4. Method-exit rules report a nonzero callee premise

The negative samples of `example.RuleReturnWithNotInsideSignature` and `custom.RuleReturnWithNotInsideSignatureStrConcat` have no generated cleaner rule and no end fact. Their actual selected sinks are `method-exit-sink:3` and `method-exit-sink:5`. Both check the original state 2 with positive literals. Neither actual sink has a negated mark condition. Thus a direct negated-literal explanation is not valid for these two findings.

Status: confirmed rule compatibility gap. It follows the current spec.

Actual report owners locate both findings in the unannotated `clean()` callee. The returned Object case reports `RuleReturnWithNotInsideSignature#clean(Object)`. The concat case reports `RuleReturnWithNotInsideSignatureStrConcat#clean(String)`. The state-2 fact reaches that callee exit under a NORMAL, nonzero state-2 premise. Its caller supplies this premise, so joint support confirms the witness.

The new boundary forms ask the provider for exit sinks with `initialFacts=null`. The engine checks every exit premise. The old `JIRMethodExitRuleProvider` rejects exit sinks when the initial-fact set is nonempty. [Interpreter §4.7](spec/interpreter.md), decision D21, explicitly changes this rule. The generated exit sink has no annotation guard to stop it at the unannotated callee. It checks the original state at Result, so it fires there before the caller summary override.

The actual caller call plan contains the required state-2 cleaners on Argument0 and Result. The custom caller has no state-2 fact after `clean()`. Thus the source override is not the cause. The finding already exists in the callee. The TrickyPatterNot negative also has a confirmed `clean(String)` callee exit, plus its caller exit from issue 2. This gives three observed callee report entries across three negative samples. The issue groups overlap. Disabling alias analysis does not remove either source-only finding. Both alias settings find each positive control and each unwanted negative finding. The observed run manager, handlers and barrier remain the production implementations.

Owners: [new boundary sink lookup](core/opentaint-dataflow-core/opentaint-jvm-dataflow/src/main/kotlin/org/opentaint/dataflow/jvm/bidi/interp/JIRBoundaryForms.kt), [new exit worklist](core/opentaint-dataflow-core/opentaint-dataflow/src/main/kotlin/org/opentaint/dataflow/bidi/engine/RunMethodAnalyzer.kt), [old zero-premise guard](core/opentaint-jvm-sast-dataflow/src/main/kotlin/org/opentaint/jvm/sast/dataflow/JIRMethodExitRuleProvider.kt), and [exit-scope probes](core/opentaint-java-querylang/src/test/kotlin/org/opentaint/semgrep/BidiMethodExitScopeTest.kt).

## All unexpected negative samples

The table lists all 20 failed sample methods and all 28 negative children. Child names follow the rule-class prefix. Every observed report entry is CONFIRMED. The final column names the issues above.

| Test method | Rule class / failed children | Actual sink | End facts | Classification |
|---|---|---|---|---|
| `test cleaner after sink 0()` | `example.CleanerAfterSink0`: `NegativeBranch`, `NegativeMultipleFunctions`, `NegativeSimple` | `this.sink(o)`; `this.sink(%0)` | `[PathFact(base=<static>, path=[1], tail=EXACT, mark=Concrete(mark=TaintMark(id=9)))]` | 2 + 3 |
| `test cleaner after sink 1()` | `example.CleanerAfterSink1`: `NegativeBranch`, `NegativeMultipleFunctions`, `NegativeSimple` | `this.sink(o)`; `this.sink(%0)` | `[PathFact(base=arg(0), path=[], tail=EXACT, mark=Concrete(mark=TaintMark(id=7)))]` | 2 + 3 |
| `test rule pattern-not with signature()` | `example.RulePatternNotWithSignature`: `NegativeCleanSecond` | `this.f(%0)` | `[PathFact(base=arg(0), path=[], tail=EXACT, mark=Concrete(mark=TaintMark(id=1)))]` | 2 + 3 |
| `test rule return 2()` | `example.RuleReturn2`: `Negative` | `method exit` | `[]` | 2 |
| `test rule return 4()` | `example.RuleReturn4`: `Negative` | `method exit` | `[]` | 2 |
| `test rule return 5()` | `example.RuleReturn5`: `Negative` | `method exit` | `[]` | 2 |
| `test rule()` | `example.Rule`: `NegativeIterProc`, `NegativeSimple`, `NegativeWithEllipsis` | `this.sink(data)`; `this.sink(%0)` | `[]` | 2 |
| `test RuleReturnWithNotInsideSignature()` | `example.RuleReturnWithNotInsideSignature`: `Negative` | `method exit` | `[]` | 4 |
| `test tricky pattern not()` | `example.TrickyPatterNot`: `NegativeSimple` | `method exit` in both `clean(String)` and `NegativeSimple#method()` | `[]` | 2 + 4 |
| `test rule with artificial pattern-inside sequence()` | `example.RuleWithArtificialInsideSequence`: `NegativeWithClean` | `this.f(%0)` | `[]` | 2 |
| `test object mapper pattern-not full()` | `example.ObjectMapperPatternNotFull`: `Negative` | `%2.foo("JSON")` | `[]` | 2 |
| `test rule return not inside prefix()` | `example.RuleReturnNotInsidePrefix`: `Negative` | `method exit` | `[]` | 2 |
| `test object mapper pattern-not ellipsis()` | `example.ObjectMapperPatternNotEllipsis`: `Negative` | `%2.foo("JSON")` | `[PathFact(base=<this>, path=[], tail=EXACT, mark=Concrete(mark=TaintMark(id=3)))]` | 2 + 3 |
| `test rule with several suffix cleaners()` | `example.RuleWithSeveralSuffixCleaners`: `NegativeWithClean1`, `NegativeWithClean2` | `this.f(%0)` | `[PathFact(base=arg(0), path=[], tail=EXACT, mark=Concrete(mark=TaintMark(id=1)))]` | 2 + 3 |
| `test rule return multi A()` | `example.RuleReturnMultiInsideNotInsideA`: `Negative` | `method exit` | `[]` | 2 |
| `test rule return multi C()` | `example.RuleReturnMultiInsideNotInsideC`: `Negative`, `Negative2` | `method exit` | `[]` | 2 |
| `test rule return not inside()` | `example.RuleReturnNotInside`: `Negative` | `method exit` | `[]` | 2 |
| `test rule with artificial reverse pattern-inside sequence()` | `example.RuleWithArtificialInsideSequenceReverse`: `NegativeWithClean` | `this.h(%0)` | `[PathFact(base=arg(0), path=[], tail=EXACT, mark=Concrete(mark=TaintMark(id=5)))]` | 2 + 3 |
| `test rule with pattern-not-inside suffix()` | `example.RuleWithNotInsideSuffix`: `NegativeCleanSecond` | `this.sink(%0)` | `[PathFact(base=arg(0), path=[], tail=EXACT, mark=Concrete(mark=TaintMark(id=1)))]` | 2 + 3 |
| `test RuleReturnWithNotInsideSignature str concat()` | `custom.RuleReturnWithNotInsideSignatureStrConcat`: `Negative` | `method exit` | `[]` | 4 |

## Existing allowances and test limits

These 9 findings are explicitly allowed by existing sample annotations. They are separate from the 28 unexpected samples:

* `example.CleanerAfterSink2$NegativeMultipleFunctions: IFDSFalsePositive(Cleaner requires 2 facts)`
* `example.CleanerAfterSink2$NegativeBranch: IFDSFalsePositive(Cleaner requires 2 facts)`
* `example.CleanerAfterSink2$NegativeSimple: IFDSFalsePositive(Cleaner requires 2 facts)`
* `example.RulePatternNotWithSignature$NegativeCleanFirst: TaintRuleFalsePositive(Cleaner captures data before sink)`
* `example.RuleReturn6$Negative: TaintRuleFalsePositive(Method exit cleaner required)`
* `example.R1$Negative: TaintRuleFalsePositive(Cleaner captures data before sink)`
* `example.RuleWithInterfaceType$NegativeSample: IFDSFalsePositive(Virtual call on unknown concrete type)`
* `example.RulePatternNotInsideWithSignature$NegativeSimple: TaintRuleFalsePositive(Cleaner captures data before sink)`
* `example.RuleWithNotInsidePrefix$NegativeCleanFirst: TaintRuleFalsePositive(Cleaner captures data before sink)`

The JVM suite keeps nine existing skips. Seven distinct dataflow shapes remain disabled: Java and Kotlin int/reference exception payloads (four cases), Java and Kotlin reflection (two cases), and Java nested stream flatMap (one case, also inherited by a bidi class). The ninth skip is the Kotlin AST case. Catch handling and reflection target recovery remain outside the implemented interpreter. A full green active suite cannot establish no false negatives for these disabled shapes.

The query suite keeps 23 existing skips: 17 sample methods and six methods in the disabled simplifier benchmark. Existing negative ignore annotations remain unchanged. Positive sample assertions prove a finding for each expected positive class; they do not prove every intended sink in a multi-sink class.

The new test mode corrects the initial coverage gap: all shared JVM analysis tests and all seven existing query sample suites can select bidi. The additive `BidiExampleTest` also stays active. SARIF cases still require findings and exact sink regions. They require Simple traces and no code-flow locations in the new mode. The old mode retains all full-path checks. The previously unclosed `DeepFieldStoreFnTest` now closes its test classpath and database.

No production correction was made in the 11 October test session. Enabled negative checks were not removed or weakened.

## Full-run results before normalization

Both requested suites were run again after the final test changes. The JVM filter selects every test under `core/src/test/kotlin/org/opentaint/jvm`. The query-language task has no test filter.

| Mode | Suite | Tests | Passed | Failed | Skipped |
|---|---|---:|---:|---:|---:|
| Default | JVM application | 496 | 487 | 0 | 9 |
| Default | Java query language | 215 | 189 | 0 | 26 |
| Bidirectional | JVM application | 496 | 487 | 0 | 9 |
| Bidirectional | Java query language | 215 | 169 | 23 | 23 |

All four results have zero test errors. The default build passed in 2 minutes 3 seconds. The bidirectional build failed in 2 minutes 8 seconds. Its 23 failed tests are the 20 original sample methods in the table above, the failed-prescan probe, and the two method-exit scope probes. The two exit probes repeat existing negative samples with alias analysis enabled and disabled. They do not add distinct false-positive sample classes. The default query run skips the three new probes, in addition to its 23 existing skips.

The final XML confirms all 188 normal positive sample runs and all 28 unexpected negative samples. It also confirms the separate failed-prescan result shown in issue 1. All 22 Java and Kotlin SARIF cases pass with exact sink-region checks. All three new probe methods appear in the test results.

Run these commands from `core`, in order:

```bash
OPENTAINT_TEST_BIDI=false ./gradlew --continue \
  :cleanTest :test --tests 'org.opentaint.jvm.*' \
  :opentaint-java-querylang:cleanTest :opentaint-java-querylang:test \
  --console=plain

OPENTAINT_TEST_BIDI=true ./gradlew --continue \
  :cleanTest :test --tests 'org.opentaint.jvm.*' \
  :opentaint-java-querylang:cleanTest :opentaint-java-querylang:test \
  --console=plain
```

Local evidence for the 11 October test session:

* Full logs: `/tmp/bidi-major-final-default.log` and `/tmp/bidi-major-final-bidi.log`.
* Final XML, count summaries and failure lists: `/tmp/bidi-major/final-gates/{default,bidi}/{application,query}/xml`. Each mode also has `summary.json`. Each suite has `failures.json` beside its XML directory.
* Final normal sample counts, actual report entries and probe results: `/tmp/bidi-major/final-gates/bidi/sample-counts.json`.
* Actual call plans and fact flow: `/tmp/bidi-major-final-fp-owners/flow.txt`.
* Exact owners of all 29 unexpected report entries: `/tmp/bidi-major-query-owner-map.tsv`.
* Independent subagent audits: `/tmp/bidi-major-query-findings.md` and `/tmp/bidi-major-return-rootcause.md`.

## Cleaner normalization and retest: 12 October 2026

The resolver and its five tests were copied from commit `25a5307cf7b89ba4e54f1e210a9c8af09be65a01` on `origin/saloed/cleaner-action-normalisation`. Both files initially matched the branch byte for byte. The earlier any-field AP commit on that branch was not copied. The remote fetch could not authenticate; the available local origin ref contains this commit.

The resolver now splits each cleaner into resolved actions. For an exact `RemoveMark(T, P)` action, it assumes the action's own `ContainsMark(P, T)` literal while it simplifies the condition. A terminal AnyField action also assumes its own any-field containment literal. A false condition drops that action. Actions with equal remaining conditions are grouped. Unrelated mark conditions remain. Ordinary rule resolution and relevant-rule resolution both use this step. The interpreter still admits only resolved cleaners with a true condition.

### 5. Broad removal needs its original root condition

Status: a false negative in the imported branch code was reproduced and corrected in this port.

The imported `Action.assumedLiterals()` did not check removal reach. For `RemoveMark(T, Argument0, ExactAndAnyField)` with condition `ContainsMark(Argument0, T)`, it assumed the root condition true. But an absent root mark does not imply that a field mark is absent. The resulting unconditional at-and-below cleaner removed the field mark.

`CleanerNormalisationReachTest` uses the existing real `DeepCleanSummarySample.helperCleanReadFlow`. The entry marks only `Argument0.f`. A summarized helper calls `clean(box)` and then returns `box.f` to the sink. The exact-removal control finds the field sink. The unconditional broad-removal control clears it. With the unmodified imported normalization, the root-conditioned broad action also cleared it, although the root mark was absent. That expected positive test failed in the real bidirectional engine.

The port now retains the condition for a non-AnyField action whose reach is `ExactAndAnyField`. Terminal AnyField actions retain the branch's own containment assumptions. This guard is the only production difference from the normalization commit. All three real reach probes now pass. The five imported resolver tests also pass. The reach probes are enabled only in bidi test mode: the legacy engine did not pass the unconditional broad-removal control in this resolved-helper shape. That legacy result is not used to prove a normalization false negative.

Owners: [shared resolver](core/opentaint-jvm-sast-dataflow/src/main/kotlin/org/opentaint/jvm/sast/dataflow/rules/MethodTaintConfigurationResolver.kt), [imported resolver tests](core/src/test/kotlin/org/opentaint/jvm/sast/dataflow/CleanerNormalisationTest.kt), and [real reach controls](core/src/test/kotlin/org/opentaint/jvm/sast/dataflow/CleanerNormalisationReachTest.kt). [Interpreter §4.2](spec/interpreter.md) now states the normalization boundary and the reach guard. The AP cleaner, exclusion table and run limits are unchanged.

### Current full-run results

| Mode | Suite | Tests | Passed | Failed | Skipped |
|---|---|---:|---:|---:|---:|
| Default | JVM application | 504 | 492 | 0 | 12 |
| Default | Java query language | 215 | 189 | 0 | 26 |
| Bidirectional | JVM application | 504 | 495 | 0 | 9 |
| Bidirectional | Java query language | 215 | 182 | 10 | 23 |

All four results have zero test errors. The default build passes in 2 minutes 8 seconds. The bidirectional build fails in 3 minutes 26 seconds because the query negative checks remain strict. The three query probes repeat the failed-prescan defect and the two existing callee-exit false positives. They do not add distinct normal false-positive samples. Default JVM mode skips the three new reach probes in addition to its nine existing skips. Default query mode skips its three existing bidi probes in addition to its 23 existing skips.

The query XML has 127 sample rows, with 188 positive and 141 negative sample runs. No positive is missed. The same nine annotated negative findings remain allowed. All 22 SARIF cases pass with exact sink-region checks. Both normalization test classes appear in the final JVM XML, and all eight methods pass in bidi mode.

### All remaining unexpected negative samples

The twelve samples produce twelve CONFIRMED report entries. Ten have nonempty end facts; two have no end facts. No new unexpected negative class appears.

| Rule class | Unexpected children | Current cause |
|---|---|---|
| `example.CleanerAfterSink0` | `NegativeBranch`, `NegativeMultipleFunctions`, `NegativeSimple` | 3: cleaner after sink; no end-fact check |
| `example.CleanerAfterSink1` | `NegativeBranch`, `NegativeMultipleFunctions`, `NegativeSimple` | 3: cleaner after sink; no end-fact check |
| `example.RulePatternNotWithSignature` | `NegativeCleanSecond` | 3: cleaner after sink; no end-fact check |
| `example.RuleWithSeveralSuffixCleaners` | `NegativeWithClean1`, `NegativeWithClean2` | 3: cleaner after sink; no end-fact check |
| `example.RuleWithNotInsideSuffix` | `NegativeCleanSecond` | 3: cleaner after sink; no end-fact check |
| `example.RuleReturnWithNotInsideSignature` | `Negative` | 4: sink in `clean(Object)`, under a nonzero premise |
| `custom.RuleReturnWithNotInsideSignatureStrConcat` | `Negative` | 4: sink in `clean(String)`, under a nonzero premise |

The ten end-fact samples report before their suffix cleaner. Normalization cannot retract a stored CONFIRMED witness; the missing end-fact verification remains the cause described in issue 3. The two callee-exit samples have no generated cleaner, so this normalization does not change them. Their fresh report owners and both alias-analysis controls still show the same D21 behavior described in issue 4. The failed-prescan probe again records `prescan=EXCEPTION analysis=OK findings=0`, with a positive control that finds one sink.

Normalization clears sixteen previously unexpected negative children in thirteen sample methods:

* `example.Rule`: `NegativeIterProc`, `NegativeSimple`, `NegativeWithEllipsis`.
* `example.RuleReturn2`, `example.RuleReturn4`, `example.RuleReturn5`: each `Negative`.
* `example.RuleReturnMultiInsideNotInsideA`: `Negative`.
* `example.RuleReturnMultiInsideNotInsideC`: `Negative`, `Negative2`.
* `example.RuleReturnNotInside`, `example.RuleReturnNotInsidePrefix`: each `Negative`.
* `example.ObjectMapperPatternNotFull`, `example.ObjectMapperPatternNotEllipsis`: each `Negative`.
* `example.RuleWithArtificialInsideSequence`, `example.RuleWithArtificialInsideSequenceReverse`: each `NegativeWithClean`.
* `example.TrickyPatterNot`: `NegativeSimple`; both its prior caller and callee entries are absent.

The disabled-case and multi-sink coverage limits above still apply. The broad-reach defect is fixed. The prescan status defect, deferred end-fact check and two callee-exit findings remain open.

Use the same full-suite commands shown above. New local evidence:

* Final logs: `/tmp/bidi-cleaner-normalisation-default-final.log` and `/tmp/bidi-cleaner-normalisation-bidi-final.log`.
* Final XML and summaries: `/tmp/bidi-cleaner-normalisation/{final-default,final-bidi}`. Each suite has an `xml` directory and `failures.json`. Each mode has `summary.json` and `sample-results.json`.
* Imported-code false-negative proof: `/tmp/bidi-cleaner-normalisation/reach-imported-bidi/xml` and `/tmp/bidi-cleaner-normalisation-reach-imported-bidi.log`.
* Guarded focused run: `/tmp/bidi-cleaner-normalisation-reach-fixed.log`.
