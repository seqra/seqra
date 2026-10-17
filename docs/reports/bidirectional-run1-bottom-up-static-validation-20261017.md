# Run-1 bottom-up static FLOW: runtime validation

Date: 17 October 2026. Scope: the implementation of the
[selected contract](../../spec/run1-bottom-up-static-propagation.md).
The implementation is committed at `ab6c3cd00`. The
[closed project report](bidirectional-run1-bottom-up-static-20261017.md)
records later performance, exact findings, profiles and retained owners.

Latest reached/completed/accepted iterations are OWASP **run-7 / run-6 / run-5**,
conductor **run-2 / run-1 / run-1**, ThingsBoard **run-1 / none / none**, and
Stirling-PDF **run-2 / run-1 / run-1**. Those measurements use `ab6c3cd00`.
Conductor and PDF meet the 60-second run-1 engine target; every candidate
invocation ends abnormally. The runtime checks below remain distinct from
project acceptance.

## Change and reference

The implementation shares unchanged static field identity carriers in an
immutable descriptor. Each field retains its own original. It decodes a
possibly affected member before an ordinary statement, rule or call effect.
Real writes, transformed static inputs, concrete TAINT, static-to-local
outputs and full ND support use ordinary propagation. The default is an
ordinary stored DEMAND summary. Only mark requests remain. Eligible NORMAL
run-1 summaries now pass through the ordinary record writer.

The [runtime refinement](../design/bidirectional-bottom-up-static-implementation-20261017.md)
records the code map and adapter boundary. The independent scalar oracle
uses the selected policies before processing; it does not relabel the final
rows of an old NORMAL analysis. Tests compare normalized logical observations,
including requests, source hits and witnesses. Physical descriptor counts
are checked separately.

## Evidence

The evidence root is:

```text
/drive-testcomp/opentaint-go-rules/bidi-o1-o3-20261012/
run1-bottom-up-static-propagation-preparation/verification-20261017T105047.277343Z
```

The `validation/` directory retains exact commands, source maps, JDK binding,
UTC times, logs, XML and exit codes. A copied XML file alone is not evidence
that its task ran. The core summary distinguishes executed tasks from reuse.

| Gate | Result | Evidence |
| --- | --- | --- |
| Common dataflow suite | 1,548 passed, 0 failed, 0 skipped | `validation/full-tests-5` |
| JVM dataflow suite | 411 passed, 0 failed, 0 skipped | Executed in `full-tests-3`; exact XML reused in `full-tests-5` |
| Analyzer consumer build | Passed | `validation/consumer-build-1` |
| Application bidirectional suite | 148 passed, 9 known strict precision failures, 1 skipped; 0 errors | `validation/application-2` |
| JVM sequence acceptance | 13 passed | `validation/application-sequence-1`, then the full selection |

The common/JVM command was:

```sh
OPENTAINT_TEST_BIDI=true ./gradlew \
  :opentaint-dataflow-core:opentaint-dataflow:test \
  :opentaint-dataflow-core:opentaint-jvm-dataflow:test \
  --continue --offline --max-workers=2 --console=plain
```

The final common gate read the same 3,160 source files before and after.
The JVM reuse is valid because only two common test files changed after its
successful execution. No production or JVM test source changed between those
gates. The later application fixture change is test-only. Its shared assertion now
requires exactly the eligible NORMAL singleton F1 records, including ZERO,
instead of requiring an empty record store. It keeps the finding, witness,
seed and demand assertions. Later snapshots account for those persisted rows.

The consumer build used `:projectAnalyzerJar` with the existing Go
server exclusion. These checks used Temurin 17.0.18.

The application command was `:test --tests '*Bidi*'` with `--offline`,
`--max-workers=2` and the same Go server exclusion. This gate is not green:
the nine strict precision failures remain failures. The final case statuses
match the previous selection. Eight assertions print full expected and actual
rule-ID counters: all counters match the previous result, with 26 extra
memberships and no missing expected membership. The ninth deep-clean case
still has zero expected findings and one observed finding. Its XML does not
print the full observed identity, so exact identity parity is not claimed for
that case. The unchanged skip is the stream scenario.

Initial application execution also failed 13 sequence cases at the obsolete
run-1 record-quarantine assertion. The fixture correction above exposed the
later assertions, and all 13 pass both alone and in the final selection.
No production change was needed for those assertion failures.

## Refinement controls

New controls cover field identity packing, exhaustive actual-supplier
projection, changed field/mark/tail effects, prefix kills, self-assignment,
call suffix effects, exact original-slot requests, recursion, full contexts,
whole ND support and the two-sided late-input/publication/request protocol.

An excluded-target creator can emit a request before testing spatial overlap.
The selector therefore sends all same-base originals through that operation.
Actual broad static-root data also takes ordinary rule and cleaner paths;
it must not be stripped as a control-root marker. Dedicated regressions cover
both cases. The static root default itself remains a summary constructor,
never an admitted run-1 initial.

A 16-field transparent chain uses two physical static call relations with
32 diagonal memberships. The test decoder proves which field-original pairs
those memberships represent. It creates no cross-field combinations. This
fixture demonstrates representation sharing, not a measured heap saving.

The tests retain finding identities, source identities, whole premise support,
seed handoff, field-E invariants and actual NORMAL witness requirements.
Batch-dependent callback counts can differ, as authorized by the selected
observation contract.

## Formal boundary

The six constructive Lean modules and their audited proof receipt are
unchanged. See the [proof validation report](bidirectional-run1-bottom-up-static-proof-validation-20261017.md).
The formal result covers the declared scalar calculus and finite derivations.
Runtime tests check storage, lowering and scheduler adapters within selected
fixtures. Neither result is a formal proof of every Kotlin execution or JVM
form. The selected JVM builder has one ordinary callee stage; arbitrary plans
with several ordinary callee stages are outside this adapter check.

## Fixed project measurements

The committed runtime was measured on the fixed OWASP, conductor,
ThingsBoard and Stirling-PDF configurations, with direct
`runProjectAnalyzer`, fresh main and the specified profiles/guard heaps.
The [project report](bidirectional-run1-bottom-up-static-20261017.md) keeps
engine time, prescan, metadata setup, report barriers and failed outcomes
separate. It records the latest reached/completed/accepted iterations and
16 missing main identities plus 342 additional unconfirmed candidates.
