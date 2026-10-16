# Run-1 weak field writes: proof validation

Date: 16 October 2026. Scope: specification and Lean reference model.
Analyzer implementation is unchanged. No analyzer, heap or profile run was made.

Latest measured iteration: **run-1, FORWARD, limit 1; OOM**. No completed
iteration or accepted forward report. The
[ThingsBoard measurements](bidirectional-thingsboard-static-workload-20261016.md)
remain the latest runtime evidence; this receipt does not measure the design.

## Result and scope

From `spec/lean`, using `leanprover/lean4:v4.33.1`:

```sh
lake build
lake env lean -M2048 audit.lean
```

| Check | Result |
| --- | --- |
| Aggregate build | PASS, exit 0, 127 jobs |
| Imported all-declaration axiom audit | PASS, exit 0, 23,337 declarations, zero unexpected axioms |
| All executable certificates | PASS, including affected/disjoint writes, independent new-value layers, partial-must cover, static defaults and actual backward authorization |

Only `propext` and `Quot.sound` are allowed. Classical.choice, sorryAx and
new unexpected axioms are rejected. Local source auditing also passed; its
separate receipt retains the selected-module development attempts.

The checked [model](../../spec/lean/ApSpec/Run1WeakFieldWrites.lean) proves
positive weak-write coverage, E-free publication, safe DEMAND normalization,
least static-write closure, actual-input default application and backward
authorization. [The proof dossier](../../spec/run1-field-write-proofs.md)
gives the full constructive arguments and theorem map. Earlier strong-query
and balanced-call models retain their stated scopes.

These are finite reference laws. JVM/model/alias inventory completeness,
packed AP refinement, actual queues/publication, ND support/confirmation and
the complete seed/iteration driver remain runtime implementation obligations.
In particular a default NORMAL schema is not a strong normal-return witness.

## Source and log binding

HEAD before these uncommitted specification/model changes:
`bcc435310698a0b60546da1c420d4d9271f49bae`. Its core tree is
`532824864eae1bafdf51b8e4bcb3e78be4eae367`, equal to the measured runtime.
No core file changes accompany this receipt.

| File | SHA-256 |
| --- | --- |
| `spec/lean/ApSpec/Run1WeakFieldWrites.lean` | `a179615be366555b73beaa93f902752a7a7843f208b74b63cc2900837ac3c602` |
| `spec/lean/ApSpec.lean` | `726cdbfd767b845b191a2b1cd28d317f5b731977a301daa8d30163a87a67fbd3` |
| `spec/lean/audit.lean` | `74298adf58e621059e01acab6cf9222090c79f2c5a3659af96343b396c4903e1` |
| `spec/lean/lean-toolchain` | `3aac669c7a910ec2389f4e4f921b605adf6ebf2d1e0c9b9cd0be4d33f3f5db71` |

Aggregate evidence directory:

```text
/drive-testcomp/opentaint-go-rules/bidi-o1-o3-20261012/static-domain-frozen-requests/20261016T063718.860848Z/preparation/run1-field-write-proof-validation-20261016T184655Z/
```

`receipt.json` binds commands, hashes and attempts. Initial aggregate audits
passed the axiom scan but rejected ambiguous dotted constructors in the new
executable certificate. Explicit MarkA/Kind/Excl names fixed those certificate
terms; no theorem or model semantics changed. `aggregate-audit-final.log`
records the full PASS. Prior failed logs remain in the directory.

Build log SHA-256:
`483cc0ac784d5c7c989a9aa4185016616313b14e950d7870af2b3bdf5a684f03`.
Final audit log SHA-256:
`df305c2b2a7e24a1e24d6c68a80474d22e307b255f5434433951453d95232d88`.

The selected-module receipt is under the same capture root:
`validation/run1-weak-field-writes-lean-local-20261016T184623.178460Z/receipt.json`.
The source review of actual E producers and effect inventories is in
`preparation/run1-empty-E-write-default-source-review-20261016/`.
