# Ordinary static summaries: layer-only proof validation

Date: 16 October 2026. Status: local formal validation passed. Analyzer
implementation is unchanged. Latest reached project iteration: **run-1,
FORWARD, limit 1; OOM**. None completed; no accepted forward report. No new
project measurement or performance result was produced.

The [unified proposal](../design/bidirectional-request-gated-static-handling-20261016.md)
stores N/D defaults as ordinary summary edges, uses ordinary calls and handoff,
and emits no S.* initial in F1. Demotion changes only the edge layer: known
field paths and tails remain. [The selected specification](../../spec/run1-field-writes.md)
and [proof scope](../../spec/run1-field-write-proofs.md) supersede the earlier
automatic demand-to-ANY and custom default-call design.

Validation at HEAD `2d3436187491139c7ba60a49da0f417ee16972ec` plus the working
document/proof changes passed:

| Check | Result |
| --- | --- |
| `lake build` in spec/lean | 128 jobs; exit 0 |
| `lake env lean -M2048 audit.lean` | 23,473 imported ApSpec declarations; zero unexpected axioms; exit 0 |
| New module declaration audit | All 182 declarations; zero unexpected axioms |
| Selected executable controls | 16 payload/input-layer/edge-layer combinations; all pass |
| Historical negative controls | Old STAR-to-ANY demotion and early root-ANY suffix loss are explicitly demonstrated |

Only `propext` and `Quot.sound` are permitted. No Classical.choice, sorryAx
or new axiom is accepted. The new module is imported by the aggregate and
its selected controls are included in audit.lean.

[Run1DefaultSummaryApplication.lean](../../spec/lean/ApSpec/Run1DefaultSummaryApplication.lean)
reuses the ordinary scalar summary geometry with NORMAL transfer tags, then
attaches input/operation/summary layers independently. It proves full PFact
equality and layer OR for identity without invoking the old post-join tail
normalizer. The payload theorem covers exact/STAR/ANY, mark exclusions and
STAR field E. F1 itself requires Empty E. It does not assert that every raw
payload is legal or that the old normalizing kernel implements this policy.

These are local constructive AP laws. Kotlin's Facts-backed summary adapter,
packed geometry/layer representation, general normalization, publication
initialization, concurrency, ND, handoff and full-driver refinement remain
implementation obligations. The earlier weak-write model retains its
historical demotion-to-ANY/default-helper scope; its old receipt is unchanged.
No performance or whole-engine correctness claim follows from this build.

The root validation evidence is:

```text
/drive-testcomp/opentaint-go-rules/bidi-o1-o3-20261012/static-domain-frozen-requests/20261016T063718.860848Z/preparation/ordinary-static-summary-root-validation-20261016
```

It contains build/audit logs, exit codes, bound source copies and receipt.json.
The independent new-module receipt is:

```text
/drive-testcomp/opentaint-go-rules/bidi-o1-o3-20261012/static-domain-frozen-requests/20261016T063718.860848Z/validation/run1-default-summary-layer-only-lean-local-20261016T194057.960302Z/receipt.json
```

That receipt preserves an earlier 1536 MiB audit-cap failure. The final local
check reduced the import to ApSpec.Basic and passed at the same cap. The
aggregate audit passed at its existing 2048 MiB cap.
