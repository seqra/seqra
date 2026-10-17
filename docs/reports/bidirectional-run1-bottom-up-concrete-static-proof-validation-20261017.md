# Run-1 concrete static propagation: proof validation

Date: 17 October 2026. Status: **formal-model validation passed; proposal only**.
Analyzer implementation, IR storage and reference/cache managers are unchanged.
No analyzer project run or performance benchmark was made for this proposal.

This is the historical proof-only check at the revision below. The runtime
is now implemented at `6b9291d7d`; see the
[runtime validation](bidirectional-run1-bottom-up-concrete-static-validation-20261017.md)
and [current project measurements](bidirectional-run1-bottom-up-concrete-static-20261017.md).
Latest reached/completed/accepted forward: OWASP run-8/run-7/run-7;
conductor run-1/none/none; ThingsBoard run-1/none/none; Stirling-PDF
run-2/run-1/run-1. Resource and project acceptance gates failed.

The [specification](../../spec/run1-bottom-up-concrete-static-propagation.md)
defines the selected concept and optimization. The
[proof dossier](../../spec/run1-bottom-up-concrete-static-proofs.md) maps the
constructive arguments to checked theorems and states the runtime adapters.
Validation used HEAD `78ed76d130d9e0676a90748bed5df5fc635165ec` plus the
working formal/spec changes, with `leanprover/lean4:v4.33.1`.

## Results

All commands ran in `spec/lean`. Both audits used a 2048 MiB Lean heap cap.

| Command | Result |
| --- | --- |
| `lake build` | Exit 0; 137 jobs |
| `lake env lean -M2048 bottom-up-concrete-static-audit.lean` | Exit 0; 1,050 owned declarations, zero unexpected axioms; 46 executable controls passed |
| `lake env lean -M2048 audit.lean` | Exit 0; all 27,116 imported ApSpec declarations, zero unexpected axioms; existing executable certificates passed |

The selected audit checks private and generated declarations as well as
public theorems, including their transitive dependencies. Only `propext` and
`Quot.sound` are allowed. It rejects `Classical.choice`, `sorryAx` and any
additional axiom.

| New module | Audited declarations | Executable controls |
| --- | ---: | ---: |
| BottomUpConcreteStatic | 584 | 26 |
| BottomUpConcreteStaticNeeds | 466 | 20 |

The controls include actual exact/chain/class supply; a read to a nonstatic
local followed by a concrete check; cleaner/overwrite kills; changed marks,
fields and whole ND staying bound; exact versus STAR-to-ANY compaction;
same-port joins; source partition; W-to-Base request bootstrap; cross-field
origins; no concrete/ZERO/killed-input routes; ignored mx for interest;
BaseSlot-to-static reentry; cycles; late edges and late marks.

The need model proves literal equality of canonical fast and expanded
Base-request lists. Its executable visited search returns finite paths.
The concrete model constructs ordinary-to-contracted and contracted-to-ordinary
derivations with the same effect payload and event records. Separate new
theorems cover class DEMAND starts and concrete compaction commuting with
ordinary NORMAL persistence.

## Design findings and scope

The bootstrap is an interest path through an actual FLOW write/input binding:
`B` checks C.f/T, the fast route reaches A's W edge, and A requests its
nonstatic input p/T. Only actual p/T can replay the write and supply concrete
C.f/T. No conditional T seed or hypothetical satisfied rule is introduced.

A concrete `C.f.* (T) -> C.f.[any] (T)` summary is not covered by root identity.
The model gives a constructive witness with two different suffixes. The spec
therefore retains this effect, including in returning methods with no static
read opcode. Pure exact field identities have a separate containment proof.

Two declared storage cohorts have proved strict reductions for at least
three relays and two marks: concrete transport `(h+1)*m` versus `h+2*m`, and
expanded static need rows `h*m` versus source-once/shared-route `m+h`.
These are counts of model layout slots, not JVM bytes or measured time.
Eager admission can add concrete local work; fresh replay on graph growth can
revisit sources. Neither whole-project improvement nor the 60-second run-1
engine target is proved.

The checked scope is scalar AP supply/effects and finite route/transport
derivations. Frontend contributor/coordinate completeness, actual post-binding
input enumeration, full packed NNF/ND lowering, concurrent queues, barriers,
observation readers and driver persistence/handoff remain implementation
refinements. There is no whole-JVM correctness or coupled-engine termination
claim. Later-run behavior is specified to stay ordinary.

## Reproducible evidence

Final evidence directory:

```text
/drive-testcomp/opentaint-go-rules/proof-validation/run1-bottom-up-concrete-static-20261017T152230.794952Z
```

Its [receipt](/drive-testcomp/opentaint-go-rules/proof-validation/run1-bottom-up-concrete-static-20261017T152230.794952Z/receipt.json)
records all commands, exit codes, elapsed times, HEAD, hashes of the local
Lean sources/toolchain/build definition, and complete log hashes. It also
checks that the source hashes did not change during validation.

Receipt SHA-256:

```text
96bd57ffbac6f9bb813f2477055beddecb1eb0d27a9bc39063dbb1b412f70ed0
```

| File in `spec/lean` | SHA-256 |
| --- | --- |
| ApSpec/BottomUpConcreteStatic.lean | `20dcdf123eb72ceeb7e0120a020ce44cc019036ebd5f323550dc31801ba4a0b1` |
| ApSpec/BottomUpConcreteStaticNeeds.lean | `25ba37bb904c0f24465c4726c38af9812c2ff8e861f8d9e2ee4d7e9d0821461e` |
| ApSpec.lean | `ec9a26c69602f07e2cc71859355d182eff5f1f6bfda9631251b03de24bef89dd` |
| bottom-up-concrete-static-audit.lean | `78f6226e28a0bb08a227e26536dcc6e357e8f57470032ee6e036abdc560ea4a2` |

Two earlier combined attempts failed while elaborating the audit runner's
control-printing loop. The declaration audits already passed in both. The
runner now checks each control group directly. Failed receipts/logs remain
in sibling directories ending `20261017T152106.479536Z` and
`20261017T152133.696097Z`; only the final receipt above validates this version.

## Latest analyzer measurements

Existing measurements use runtime
`ab6c3cd006cb62e6a017c0e3c32d96573d2214e3`. They do not measure this proposal.

| Project | Latest reached | Latest completed | Latest accepted forward report | Run-1 engine seconds |
| --- | --- | --- | --- | ---: |
| OWASP | run-7 FORWARD; timeout | run-6 BACKWARD | run-5 FORWARD | 265.789, complete |
| conductor | run-2 BACKWARD; OOM | run-1 FORWARD | run-1 FORWARD | 44.385, complete |
| ThingsBoard | run-1 FORWARD; OOM | none | none | 322.250, incomplete |
| Stirling-PDF | run-2 BACKWARD; OOM | run-1 FORWARD | run-1 FORWARD | 39.951, complete |

ThingsBoard includes 41.830 seconds of heap capture and remains incomplete
before that capture. Engine times exclude prescan and report barriers.
The [project report](bidirectional-run1-bottom-up-static-20261017.md) has the
configuration, finding comparisons, profile and heap evidence.
