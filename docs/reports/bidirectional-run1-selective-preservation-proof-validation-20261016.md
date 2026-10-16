# Run-1 selective field preservation: proof validation

Date: 16 October 2026. Status: historical local formal-validation checkpoint.
The work recorded below passed local checks and made no analyzer change or
project measurement. Its then-latest project result was a historical
ThingsBoard **run-1, FORWARD, limit 1; OOM**, with no completed iteration or
accepted forward report.

Current measured runtime: `28a123f0e`. Reached/completed/accepted iterations:
OWASP **run-7 / run-6 / run-5**; conductor **run-2 / run-1 / run-1**;
ThingsBoard **run-1 / none / none**; Stirling-PDF **run-2 / run-1 / run-1**.
See the [project measurements](bidirectional-run1-selective-preservation-20261016.md).

The [selected field-write contract](../../spec/run1-field-writes.md) replaces
the former general weak-write design. For `x.f=y`, broad x.* demotes with
Empty E, x.f.* and descendants die, and disjoint x.g.* stays unchanged.
Only a surviving residual needing an exclusion update demotes. The
[unified proposal](../design/bidirectional-request-gated-static-handling-20261016.md)
retains ordinary default summary storage, subscription/application and handoff.
No S.* initial is emitted in F1; demotion retains known path/tail/mark data.

At HEAD `26ea71480` plus working documentation/proof changes:

| Check | Result |
| --- | --- |
| `lake build` in spec/lean | 129 jobs; exit 0 |
| `lake env lean -M2048 audit.lean` | 23,656 imported ApSpec declarations; zero unexpected axioms; exit 0 |
| New module-only declaration audit, 1536 MiB cap | All 183 declarations; zero unexpected axioms |
| Selected executable cases | 16 checks pass: normal/demand kills, partial ancestors, siblings, static fields, exact ancestors, aliases, mixed components, writeback and self-assignment |

Only `propext` and `Quot.sound` are permitted. No Classical.choice, sorryAx
or new axiom is accepted. The aggregate imports the new module and executes
its selected controls. The old blanket weak-write controls remain explicitly
historical; their passing result does not certify the new kill policy.

[Run1StrongPreservation.lean](../../spec/lean/ApSpec/Run1StrongPreservation.lean)
uses real base/path-prefix/tail classification. Complete overwritten regions
have no old survivors; unchanged components have no written location; partial
ancestor fallback covers every strong survivor. Values supplied from pre-store
work remain unchanged. Surviving payloads/origins remain identical and layers
never promote. Empty-E input and value hypotheses imply Empty-E write outputs.

The model uses an explicit definite/may-alias distinction. It does not prove
frontend write certainty, packed transfer distribution, JVM alias/array models,
actual RHS generation, cleaner lowering, queue/publication integration, full
ND/rule premises or the source-to-sink iteration driver. The separate
[layer-only default proof](bidirectional-ordinary-static-summary-proof-validation-20261016.md)
retains its local scope. Later strong queries and ordinary default summaries
still require their specified runtime refinements. No analyzer code changed.

Root validation evidence, including bound sources, logs, exit codes and receipt:

```text
/drive-testcomp/opentaint-go-rules/bidi-o1-o3-20261012/static-domain-frozen-requests/20261016T063718.860848Z/preparation/run1-selective-preservation-root-validation-20261016
```

Independent selected-module receipt:

```text
/drive-testcomp/opentaint-go-rules/bidi-o1-o3-20261012/static-domain-frozen-requests/20261016T063718.860848Z/validation/run1-strong-preservation-lean-local-20261016T200009.194809Z/receipt.json
```
