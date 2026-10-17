# Run-1 bottom-up static FLOW: proof validation

Date: 17 October 2026. Status: constructive formal-model validation passed.
Analyzer implementation, IR storage and cache/reference managers are unchanged.
No analyzer test, project run or benchmark was performed for this update.

The [specification](../../spec/run1-bottom-up-static-propagation.md) and
[proof dossier](../../spec/run1-bottom-up-static-proofs.md) describe the selected
contract and theorem map. Validation ran at HEAD
`8f833f514a84e410aa11b3009b596d4b063f69bc` plus the working proof/spec changes.
The toolchain was `leanprover/lean4:v4.33.1`.

## Validation results

All commands ran in `spec/lean`. Audits used a 2048 MiB Lean heap cap.

| Command | Result |
| --- | --- |
| `lake build` | Exit 0; 135 jobs |
| `lake env lean -M2048 audit.lean` | Exit 0; all 26,066 imported ApSpec declarations; zero unexpected axioms; existing executable certificates passed |
| `lake env lean -M2048 bottom-up-static-audit.lean` | Exit 0; all 2,413 declarations from the six new modules; zero unexpected axioms; all six selected executable groups passed |

The declaration scans include private and generated helpers and inspect
transitive dependencies. Only `propext` and `Quot.sound` are allowed.
No `Classical.choice`, `sorryAx` or additional axiom is accepted. Historical
modules remain imported; their successful controls do not establish the
new policy. The selected audit explicitly checks the new model's controls.

| New module | Audited declarations |
| --- | ---: |
| BottomUpStaticDomain | 535 |
| BottomUpStaticApplication | 284 |
| BottomUpStaticProtocol | 300 |
| BottomUpStaticTransport | 819 |
| BottomUpStaticRequests | 310 |
| BottomUpStaticModel | 165 |

Selected controls distinguish illegal ANY/root initials, actual concrete
answers versus mere interest, strong kills/disjoint writes/self-assignment,
eventful cleaners versus transparent relays, broad-supplier denotation versus
literal AP lists, full call/ND support, late input/effect orders, recursive
paths, unchanged versus changed-origin frozen marks, ordinary persistence,
and event-only request results after a fact kill.

The scalar transport explicitly normalizes static FLOW before primitive
evaluation and on output admission. Selected controls include a nonstatic
NORMAL FLOW first moving to STATIC through an AP effect. Pre-store result
supports and opaque fallback/ND layer joins remain explicit adapter contracts.

The user resolved the observation-policy question: preserve semantic report,
source and sink identities and rule occurrences; allow batch-dependent
diagnostic counts to vary. The executable witness has equal final cells with
callback counts 1 versus 2. `ExternalMethodRecord.callSites` is such a count.

## Checked scope and limits

The model proves the initial grammar, actual-supplier admission, sticky
static FLOW demotion, executed scalar relay laws, lossless full-port call
coding, whole-premise combinations, bidirectional finite-derivation transport,
snapshot-round equality, positive leastness, arbitrary-supplier identity
containment, symbolic frozen-mark coverage, two-sided incremental joins,
counted-work invariants and NORMAL persistence/compaction commutation.
Executable witness transformations support the constructive existence claims.

A concrete transparent-chain family has `(n+1)*m` ordinary storage cells
versus `n+2*m` shared-route cells, with proved strict improvement for
`n>=3,m>=2`. This counts a declared executable layout, not JVM bytes.
Neither finite graph shape nor the ghost trace model proves termination of
an arbitrary engine workload, and no 60-second guarantee follows.

Frontend/model/alias and local-contributor completeness, packed transfer and
compaction, actual NNF/ND/call compilation, concurrent queues/publication,
cancellation and complete persistence/handoff/driver refinement remain
implementation obligations. The proofs do not certify current Kotlin behavior
or a new running optimized engine. The dossier states each adapter explicitly.

## Reproducible evidence

Final evidence directory:

```text
/drive-testcomp/opentaint-go-rules/proof-validation/run1-bottom-up-static-20261017T103350.787206Z
```

Its [receipt](/drive-testcomp/opentaint-go-rules/proof-validation/run1-bottom-up-static-20261017T103350.787206Z/receipt.json)
records exact commands, exit codes, elapsed times, toolchain, HEAD, hashes
of every local Lean source and hashes of all three complete logs.
Receipt SHA-256:

```text
ac49ad7d7162cb3a2bfc12410da096ceb442ac7f08276c57dc1a74e8376e153d
```

Final selected source hashes:

| File in `spec/lean` | SHA-256 |
| --- | --- |
| ApSpec/BottomUpStaticDomain.lean | `547c18e8b52fbaedb42d07209c65dbefce4f1fc4352af310221a6a68fc088d70` |
| ApSpec/BottomUpStaticApplication.lean | `331088aac388129d1abacb2397d215cfd8f53ad5bfe0664e610813ddfad3773b` |
| ApSpec/BottomUpStaticProtocol.lean | `5bf90d32043eedeefb381edf3cba604df6db6a1945f5aff3ab75749ada8b0ee8` |
| ApSpec/BottomUpStaticTransport.lean | `c48c2d7a18965227dc1b76817fa189b166ad011f6255e9baaebbf399cada63f4` |
| ApSpec/BottomUpStaticRequests.lean | `81e70f1e1a4181070e7ddec66ed37bba4d90c965c5e2526ac343a08728e452ea` |
| ApSpec/BottomUpStaticModel.lean | `f459e5679c102601660d3adbfa8294c509d8ac1ff648f5e1587d17a0a9e75757` |
| ApSpec.lean | `a993898594cdccab898e4a9f8b0b5809bfb76a9184825bb46214da385fddaa6c` |
| bottom-up-static-audit.lean | `89a96e53e0f8bc70aa8142356e61e7a285f0fdd1efe5a2c933501f5c5948ee33` |

An earlier combined audit failed: `no_early_completion` and its generated
helper depended on classical choice through an arithmetic proof. The direct
natural-number case proof replaced that dependency without changing its
statement. The failed receipt/logs are retained under
`run1-bottom-up-static-20261017T102650.569769Z` beside the final directory.
Only the final receipt certifies the source hashes above. Selected development
checks also removed classical library dependencies in the graph path proof.

## Latest analyzer measurements

No measurements changed. Current measured runtime is
`28a123f0eac937e8f2feca9ee5dd84e38fa8f979`.

| Project | Latest reached | Latest completed | Latest accepted forward report | Run-1 engine seconds |
| --- | --- | --- | --- | ---: |
| OWASP | run-7 FORWARD; timeout | run-6 BACKWARD | run-5 FORWARD | 263.421, complete |
| conductor | run-2 BACKWARD; OOM | run-1 FORWARD | run-1 FORWARD | 49.567, complete |
| ThingsBoard | run-1 FORWARD; OOM | none | none | 176.913, incomplete |
| Stirling-PDF | run-2 BACKWARD; OOM | run-1 FORWARD | run-1 FORWARD | 36.586, complete |

ThingsBoard's incomplete run-1 time includes 37.94 seconds of heap capture.
Engine times exclude prescan/report barriers. The
[closed measurement report](bidirectional-run1-selective-preservation-20261016.md)
contains configurations, comparison evidence and failure scope. No memory or
time improvement is claimed for an unimplemented design.
