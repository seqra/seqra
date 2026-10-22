# Run-1 mark mode: source adapter checks

Status: implementation and focused controls complete. Full project tests and
paired performance runs are outside this record. The focused tests reach and
complete **run-1**. The driver control also completes **run-3**.

## Selected change

The property `opentaint.bidi.run1MarkMode=GROUP|FULL` selects mark precision
in run-1. Its default is GROUP. FULL keeps each full mark. Thus T and U can
have the same owner and still be different request and supply keys.

Both modes keep the same run-1 field rules, DEMAND policy, contributing
origins, static service transitions and static-summary erasure. Later runs
use FULL. The flag does not select the old static propagation procedure.

## Model to source mapping

The constructive model is
[`Run1MarkModeExperiment.lean`](../../spec/lean/ApSpec/Run1MarkModeExperiment.lean).
Its statements describe local contracts. They do not prove the Kotlin engine.
The existing formal receipt remains separate from these source checks.

| Model contract | Production adapter | Control |
| --- | --- | --- |
| `parseFlag`, `default_is_group` | `readRun1MarkMode` accepts only absent/GROUP/FULL | `Run1MarkModeTest`: valid and invalid values |
| `selectedMode`, `later_runs_full` | `IterationDriver` captures once; `RunConfig` selects FULL after run-1 | Mode schedule test and `DriverTest` immutable capture control |
| `full_keys_injective`, `group_keys_merge_exactly_owners` | `MarkTable.runtimeAtom`; service and point resumes use `MarkSet` of executable `TaintMark` handles | Same-owner T/U field and exact class controls |
| `runtime_atom_idempotent`, `group_not_decoded_to_full` | Service `slices` uses the selected mode; `facts` preserves and validates selected atoms | FULL supply round trip and rejected GROUP materialization |
| `same_structural_policy` | Engine `forceRun1Layer` and `collectAfterRun1Policy`; run-1 origin stores; common static service | Static FLOW DEMAND control and real FULL engine all-origin control |
| `selectedCleanerKills`, `full_condition_has_actual_witness` | FULL rule view, ordinary `MarkGate` and cleaner; GROUP view and projection | Exact selected cleaner, condition and GEN controls |

## Runtime keys and transitions

`GroupSet` continues to contain only `GroupId` values. Static request
payloads no longer use that type. They use `MarkSet`, which contains
executable atom handles. The existing `RegionGroups` name is retained for
API continuity; its memberships can be GROUP or FULL atoms.

```text
GROUP: T -> g; U -> g
FULL:  T -> T; U -> U

FULL request {T,U}, then write {T}:
    processed = {T}; pending = {U}
FULL write {U}:
    processed = {T,U}; pending = {}
```

The three-set algorithm is unchanged. Field answers use the canonical
field descriptor. Exact class answers keep the exact class point and do
not supply descendant fields. Each point resume retains pending FLOW
images by the selected atom. A FULL reply cannot close a different FULL
mark request with the same owner.

Nonstatic request storage already uses executable mark handles. Its
ordinary pipeline is reused. The real FULL engine control sends two
inputs through a call. It checks that a two-input condition waits for both
inputs, emits each contributing origin, keeps T and U action results,
and creates only singleton DEMAND relations in run-1.

## Shared policy and rule views

`RunManager` selects `RUN1_GROUP` only when the selected mode is GROUP.
FULL uses the ordinary FULL method view and its existing cache. The
return group schedule remains GROUP-only. FULL executes the full action
sequence.

FULL AP primitives keep their ordinary raw layers. The engine applies
the shared run-1 policy: all concrete taint and static FLOW results are
DEMAND. Ordinary nonstatic FLOW can remain NORMAL. This preserves the
existing AP reference API and prevents FULL from enabling later-run field
or exit behavior in run-1.

`unchangedStaticInitial` still returns null. Entry-mark removal at method
exit remains disabled during run-1. No new mark member census, rule
specialization pass or GROUP-to-FULL decoding is added.

## Observations and validation

`RunResult.markMode` records the effective production mode. The driver
report records the selected run-1 mode. Start logs include the effective
mode on every run.

Static service membership counts use the same definition in both modes:
number of selected executable atoms. Existing `suppliedGroups` and
`staticGroup*` metric names are retained. Static work counters run in both
modes. Missing counters must not be reported as zero work.

The end snapshot also unions existing static service memberships in
temporary sets. It reports distinct requested atoms, distinct supplied
atoms, supplied owner groups and supplied FULL atoms. No new retained
index or per-node observer is used. These counts cover the static service,
not the complete engine. A GROUP snapshot has no executable FULL atoms;
the number of full source marks behind its groups is unavailable.

The combined focused invocation passed **122 tests** with no failures.
It includes `Run1MarkModeTest`, `DriverTest`, the static service and point
controls, static erasure controls, and run-2 query completion controls.
Its log is `/tmp/mark-mode-focused.log`. The broader module and project
results are recorded separately. The final common-module gate passed
**1,771 tests**, with no failures or skipped tests. Its receipt is
`/drive-testcomp/opentaint-go-rules/run1-static-summary-erasure-20261022/validation/common-final/receipt.json`.
The runtime source state stayed unchanged during that gate. This includes
the additional snapshot and producer-grammar controls. The final JVM
dataflow gate passed **438 tests**, with no failures or skipped tests,
on the same unchanged runtime source state. Its receipt is
`validation/jvm-final/receipt.json` under the same evidence root. These controls do not
establish a whole-engine equivalence theorem or a performance improvement.
