# Request-gated statics: proof validation

Date: 16 October 2026. Scope: documentation and Lean reference model.
Analyzer implementation is unchanged. No analyzer, heap or profile job ran.

Latest measured iteration: **run-1, FORWARD, limit 1; OOM**. No completed
iteration or accepted forward report. These remain the source-bound
[ThingsBoard measurements](bidirectional-thingsboard-static-workload-20261016.md),
not a performance result for this design.

## Checked result

From `spec/lean`, with `leanprover/lean4:v4.33.1`:

```sh
lake build
lake env lean -M2048 audit.lean
```

| Check | Result |
| --- | --- |
| Aggregate build | PASS, exit 0, 126 jobs |
| Aggregate all-declaration axiom audit | PASS, exit 0, 22,798 declarations, zero unexpected axioms |
| New module local audit | PASS, 972 declarations, including private/generated declarations |
| Executable certificates | PASS, including nine families, separate gates, backward returning/kill/nonreturn queries and unproductive recursion |

The only allowed axioms are `propext` and `Quot.sound`. The audit rejects
Classical.choice, sorryAx and every other unexpected axiom. The new module's
generic-state balanced-call evaluator has checked soundness, completeness,
monotone approximants and leastness for finite balanced derivations.

The [static proof dossier](../../spec/static-handling-proofs.md) states the
constructive arguments and exact scope. Packed AP refinement, frontend
coverage, ND confirmation, real publisher/scheduler integration, synthetic
exit classification and the complete iteration driver remain separate
implementation obligations. Passing local proofs does not discharge them.

## Source and log binding

HEAD before these uncommitted documentation/model changes:
`64f5e3db4228179f57d103b0e6f66a249a75d3ed`. Its `core` tree is
`532824864eae1bafdf51b8e4bcb3e78be4eae367`, equal to the measured runtime's
core tree. No core file changes accompany this receipt.

| File | SHA-256 |
| --- | --- |
| `spec/lean/ApSpec/RequestGatedStatics.lean` | `50d70e18d1e317a028206df9a2ac8e104170590bc560117a6983e3edde9e565e` |
| `spec/lean/ApSpec.lean` | `a4f141d4cea9e08e0f8430344943aecc9619b40a824a9b14ae1bbb9336782c05` |
| `spec/lean/audit.lean` | `d7dbf759067edeab597a6446135d66d5d2a16b39b8a4e76f1354f798a4bdd40e` |
| `spec/lean/lean-toolchain` | `3aac669c7a910ec2389f4e4f921b605adf6ebf2d1e0c9b9cd0be4d33f3f5db71` |

Aggregate evidence directory:

```text
/drive-testcomp/opentaint-go-rules/bidi-o1-o3-20261012/static-domain-frozen-requests/20261016T063718.860848Z/preparation/request-gated-statics-proof-validation-20261016T150850Z/
```

`receipt.json` records commands, source hashes and exit codes. The first
aggregate audit passed its axiom scan but failed on an ambiguous `.fact`
type in the new executable certificate. An explicit Point annotation fixed
that certificate; `aggregate-audit-final.log` records the complete PASS.
No theorem or runtime semantics changed for this repair. The original log
and receipt remain alongside the final ones.

`aggregate-build.log` SHA-256:
`2357a9cb510248d2a27a63e6ad9faff22356141011a3e6418a44682fe614ebf7`.
`aggregate-audit-final.log` SHA-256:
`0e5ee6a15c9bfb52da86f6863c44cb34456479dadf13bd8917119aa89c83c34a`.

The separate local receipt is under the same capture root:
`validation/request-gated-statics-lean-local-20261016T150537.129769Z/receipt.json`.
It preserves the local proof-development attempts and final constructive audit.
