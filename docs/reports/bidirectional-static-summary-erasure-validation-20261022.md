# Run-1 static summary erasure: implementation validation

Date: 22 October 2026. Status: implementation complete. Project results
are in the [performance report](bidirectional-static-summary-erasure-performance-20261022.md).
Selected contract: [run1-static-summary-erasure.md](../../spec/run1-static-summary-erasure.md).
The implementation receipt records the immutable runtime revision. Project
measurements and a next-iteration proposal remain separate deliverables.

## Selected changes

Run-1 executes the required producer effects, then removes each nonzero
all-static premise with a static result. Such a relation enters no raw or
selected summary store, publication batch, caller relay, handoff pair or
native record. ZERO, nonstatic and mixed relations keep their existing
routes. A first ordinary setter binding still supplies its computed static
result before its caller relation is removed.

An unanswered fixed-mark join owns its immutable scalar payload. It does
not borrow the removed summary. The join key is deduplicated while pending
and is removed after the answer. No answered-summary archive replaces the
removed store.

Run-1 handoff attaches one static query-completion policy. Run-2 admits the
actual named-field FULL input. An ANY input also supplies its same-field
EXACT intersection. It keeps the full mark identity. The next handoff does
not retain this policy. Unsupported executable broad root or class marks
receive an explicit diagnostic; they are not dropped or mapped to a field.
Current named-field MUST and exclusion headers keep their existing rules.

`opentaint.bidi.run1MarkMode=GROUP|FULL` selects run-1 precision. GROUP is
the default. FULL keeps distinct full marks through rules, actions,
requests, service memberships and cleaners. Both modes keep the same
run-1 field transfer, DEMAND policy, contributing origins and static
procedure. Later runs remain FULL. The driver captures the setting once
and records it in logs and result metadata.

No field-transfer redesign, source/exit eligibility change, grounding
support, IR change, reference-manager change or weak cache is included.

## Formal and source checks

The focused Lean build passed 54 jobs before the runtime patch. The
static-erasure audit checked 1,822 owned declarations and 75 executable
controls. The mode audit checked 216 declarations and 30 controls. No
unexpected axiom occurred. The formal sources remain unchanged.

These models prove the stated finite producer, relay, query and replay
contracts. They do not compile Kotlin or prove arbitrary provider inputs.
Production correspondence is recorded in:

- [Static summary erasure adapters](../design/bidirectional-static-summary-erasure-adapters-20261022.md).
- [Mark-mode adapters](../design/bidirectional-run1-mark-mode-adapters-20261022.md).
- [Query and producer grammar adapters](../design/bidirectional-static-query-completion-adapters-20261022.md).

The controls exercise both arrival orders, changed marks, exact class
markers, field tails, complete mixed conditions, every contributing
origin, first caller bindings, native persistence in later runs, and a
looping writer with no retained summaries. The last control delivers
static work to a dead-local sink in both modes with one or two workers.
The immutable-mode driver control completes run-3.

## Executed gates

| Gate | Current result | Evidence |
| --- | --- | --- |
| Comparison and profile harness | PASS: 38 tests | `validation/harness.log` |
| Common dataflow | PASS: 1,771 tests; no failures or skips | `validation/common-final/receipt.json` |
| JVM dataflow | PASS: 438 tests; no failures or skips | `validation/jvm-final/receipt.json` |
| Application | 182 tests: 172 passed, nine failures, one skip | `validation/application-final-2/receipt.json` |
| Rule and join controls | 41 tests; two exit-scope failures | `validation/query-final/receipt.json` |
| Analyzer classes | PASS | `validation/classes-final/receipt.json` |

Evidence root:
`/drive-testcomp/opentaint-go-rules/run1-static-summary-erasure-20261022`.
The common and JVM gates record unchanged sources during execution.
Application assertion-only edits do not change production source bytes;
`validation/frozen-production-sources.json` binds those bytes.

The first application gate executed 181 tests. It had nine cleaner
failures, one obsolete S-to-S storage assertion and one skip. The revised
assertion retains the actual run-2 source action, exact run-3 seed and
sink-witness requirements. It now requires the erased relation to be
absent. A FULL-mode JVM positive case uses the same later-run checks.
Fresh controls at detached `d2ad8e335` reproduce all nine cleaner and two
exit-scope failures, with the same failed expected/observed results. Its
core tree is byte-identical to the `30c1b8460` comparison runtime. The
cleaner control executes 44 tests; the exit control executes two tests.
Both source-bound runs have no errors or skips. See
`validation/baseline-controls/comparison-final.json`. These remain failed
precision gates; the controls do not turn them into accepted results.

All six JVM static-writer acceptance cases pass, including the new FULL
case. They complete run-3 and keep the real run-2 source-action and exact
run-3 handoff checks. The full application rerun has no new failure after
the obsolete storage assertion is corrected.

## Project boundary

Fresh main at `f945f92c5ae554ff4f5fc7696ee5a269210478bb` completed all four
fixed project commands. OWASP produced the expected 4,112 trace attempts.
Main uses the legacy engine; a latest bidi iteration is not applicable.
Candidate project commands started after these implementation gates.
Their phases, findings and OOM analysis are in the separate performance
report. These unit controls alone do not establish project completion
or a performance gain.

The paired candidate campaign is now CLOSED24, with unchanged runtime
source and configured inputs. All 24 whole commands are incomplete:
18 end with OOM and six with timeout. Run-1 completes in 21 attempts;
12 meet the 60-second engine target. The latest OWASP commands reach
run-8 and retain an accepted run-7 report. Other projects end in run-1 or
run-2, as listed per command in the performance report. The two missing
Stirling-PDF baseline return locations and all eleven inherited precision
test failures remain explicit failed acceptance results.
