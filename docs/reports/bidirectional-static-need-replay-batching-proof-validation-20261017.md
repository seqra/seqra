# Static-need replay batching: proof validation

Date: 17 October 2026. Status: **formal-model validation passed;
specification only**. Analyzer implementation is unchanged. No project run,
performance benchmark, heap capture or runtime test suite was run for this
documentation change.

Contract: [batched static-need replay](../../spec/run1-static-need-replay-batching.md).
The [proof dossier](../../spec/run1-static-need-replay-batching-proofs.md)
maps the constructive arguments to the model and states its runtime boundary.
Validation used HEAD `1bbdc7b305db4ee165404b83e294ef57dbeafa69` plus the
working specification and additive Lean sources, with
`leanprover/lean4:v4.33.1`.

## Results

Commands ran in `spec/lean` after adding the new module to the ApSpec import
list. The focused audit used a 1536 MiB Lean heap cap.

| Command | Result |
| --- | --- |
| `lake build` | Exit 0; 138 jobs; 1.847 s |
| `lake env lean -M1536 static-need-replay-batch-audit.lean` | Exit 0; 303 owned declarations, zero unexpected axioms; 28 controls; 3.088 s |

The focused audit checks every declaration owned by StaticNeedReplayBatch,
including private and generated helpers, and examines transitive axiom
dependencies. Only `propext` and `Quot.sound` are allowed. It rejects
`Classical.choice`, `sorryAx` and other axioms. The complete 28-control list
also has the kernel-checked `Controls.controls_pass` certificate.

The default build includes the new module. The focused axiom audit is not a
new all-module declaration audit and does not validate Kotlin behavior.

## Checked scope

The independent reference routes one source's marks along finite actual-input
paths. The optimized model groups equal mark sets and executes one multi-start
visited search, using a virtual seed only in the formal model. It proves
exact typed endpoint/mark membership and supplies executable path certificates.

The source/pending trace uses event lists as a union oracle, then proves
same-source union and fresh-delta decomposition. For every finite serialized
graph/source/flush trace, its invariant accounts for completed candidates or
pending replay. A final flush equals the reference closure for that prefix.
This does not assume that runtime owners take a global graph snapshot.
The separate live-graph design proof uses the latest insertion in a finite
source/path witness; actual overlapping Kotlin waves remain a refinement task.

Endpoint equality is defined before committed-R subtraction. The model proves
that filtering already committed members preserves the effective request
union, including unrelated ordinary requests. Neither raw delivery counts
nor final R alone are equated to static router candidates.

The model proves nonincreasing weighted static-node scan work on a fixed
graph. Base-node weights are zero for this runtime claim because Base offers
can repeat. It also proves a strict finite overlap example that pays the
declared grouping charge, a contrary example where grouping overhead exceeds
the saving, and stable-route replay arithmetic for fewer batches. These are
abstract work units, not JVM bytes or engine-time measurements.

Controls cover exact content equality with reordered/duplicate encodings,
empty sets, unequal sets, shared routes, cycles, unreachable nodes,
source-before-edge, edge-before-source, a late edge behind a visited node,
a later mark, fresh-source union, committed-R filtering and strict/no-gain
cost cases. A kernel-checked disconnected-start counterexample shows why
different mark sets cannot be merged into a cross product.

## Runtime obligations

The specification requires runner-owned pending state, registration before
dirty notification, source commit before delta recording, and a flush after
both event handling and incomplete/complete Work quanta. The flush must occur
before parent release or requeue. A shared visit must broadcast to all current
subscribers; future bindings start a fresh batch with fresh visited state.

Binding/compiler completeness, source/sink event coverage, concurrent owner
queues, pending-parent ownership, deferred callback accounting, cancellation,
joined release and the full coupled-engine observation/persistence bridge
remain implementation obligations. Existing abstract accounting lemmas cover
child-before-parent arithmetic; they do not prove these new Kotlin adapters.
No whole-engine termination or 60-second guarantee is claimed.

## Source-bound evidence

The final [receipt and logs](/drive-testcomp/opentaint-go-rules/proof-validation/run1-static-need-replay-batching-20261017T211235.944158Z/receipt.json)
record both commands, exit codes, elapsed times, complete log hashes, all
local Lean source hashes, toolchain/build inputs and the two specification
hashes. Source hashes match before and after validation. The receipt also
records an empty analyzer-core diff.

Receipt SHA-256:

```text
fe81dfa48a58369448742a9230944b914d695fd959d6f38dc26a1cde72709789
```

| Source | SHA-256 |
| --- | --- |
| `spec/lean/ApSpec/StaticNeedReplayBatch.lean` | `4bb09fb83caab1cba2e95775d5d70b7bbc9a74a42d5deef48a0cc7d32ecde04e` |
| `spec/lean/static-need-replay-batch-audit.lean` | `2424778e637e70747d55dd293e338d1c9a8161d70858e49124c73414258e3747` |
| `spec/lean/ApSpec.lean` | `469223cda4f162652df35066063bfb549fb3cd421985985215b67ca5a8a53ffa` |
| `spec/run1-static-need-replay-batching.md` | `8352b1a8c5f031a85ca6bd016951342f4a63609ddad60dbfab356c749cdb8422` |
| `spec/run1-static-need-replay-batching-proofs.md` | `355697befeeab3e3201b3fb4f53ba6c4b4f433d86e36c14d1dbd3a591870bbe8` |

## Latest analyzer iterations

These existing results use runtime
`6b9291d7dd69ae5f6a1260c7f1021c7500fa56b8`; they do not measure this proposal.

| Project | Latest reached | Latest completed | Accepted forward | Terminal status |
| --- | --- | --- | --- | --- |
| OWASP | run-8 BACKWARD | run-7 FORWARD | run-7 | TIMEOUT |
| conductor | run-1 FORWARD | none | none | OOM |
| ThingsBoard | run-1 FORWARD | none | none | OOM |
| Stirling-PDF | run-2 BACKWARD | run-1 FORWARD | run-1 | OOM |

The [current project report](bidirectional-run1-bottom-up-concrete-static-20261017.md)
has engine times, report comparisons and memory evidence. No project meets
the 60-second run-1 engine target on that runtime. Formal-model validation
does not change the failed project acceptance or resource results.
