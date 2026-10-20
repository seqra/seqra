# Static request state: local implementation validation

Date: 20 October 2026. Status: **implementation complete; local gates pass**.
This is the local validation record for the implementation commit. Project
measurements must use that commit. Follow the
[selected contract](../../spec/run1-static-request-state-and-group-identities.md)
and [proof map](../../spec/run1-static-request-state-proofs.md).

The enum alias filter and immutable GROUP fast paths are implemented.
Normal-field three-set requests, both FLOW writer routes, actor pending
release and fixed-group singleton/ND joins are implemented. Existing field
transfers, kills, layers, caller bindings and the run schedule are unchanged.
The enum filter retains its **DIRTY HACK** source comment. Mutable enum state
can lose a real alias-mediated flow; no unrestricted soundness claim applies.

## Exact class markers

The user resolved the class boundary: `S.C.$ / Group(g)` is an exact special
global mark. There is no class-wide mark abstraction. The current grammar
has eight families. Run-1 rejects a class STAR initial or a class ANY actual
input. Exact markers keep their class, group and exact tail.

Class ANY predicates remain metadata queries. They select both matching
fields and the exact empty-suffix marker. BELOW cleaners select fields only;
EXACT and AT_AND_BELOW retain their previous reach. The adapter separates
these query branches without creating a class FLOW initial. Later FULL
backward wildcard demands remain legal. No generic AP or field-transfer
rule was changed.

## Local validation

The tested checkout is based on `c1c42ed64`; its runtime comparison base is
`029ee41fd6de17388c2f61d0f900cd00f6bd33f8`.
The final local test receipt binds all 3,215 core/rule input files to
SHA-256 `d3b5cdf041ec2529b43a46abdc059142c270886ba181fecf54247e526997f3f8`.
Core/rule bytes stayed fixed during this gate.

| Gate | Tests | Failures / errors / skipped |
| --- | ---: | --- |
| Common dataflow | 1,632 | 0 / 0 / 0 |
| JVM dataflow | 436 | 0 / 0 / 0 |
| Application/sequence/declaration hooks | 38 | 0 / 0 / 0 |
| Rule tags and joins | 33 | 0 / 0 / 0 |
| Total | 2,139 | 0 / 0 / 0 |

The default Lean build passes 168 jobs. The selected audit checks 943
declarations in five modules and 67 executable controls. The focused class
audit checks 339 declarations and 39 controls. Both audits find no unexpected
axioms. These are local grammar, protocol and projection proofs, not a proof
of the whole JVM engine or a 60-second time bound.

Targeted tests first reproduced retained answered interests, noncanonical
field answers, eager all-static writer work and repeated immutable
normalization. Independent old-normalization references passed before the
optimization. The actor tests cover replies in flight, complete fixed-group
joins, source-free copy cycles, exclusions and the real setter/wrapper/getter/
dead-local path through FULL run-2. Targeted counts overlap the broad gate;
do not add them to the total above.

The first broad runs found five expected contract migrations: four exact
field-premise expectations and one enum alias endpoint. Those tests now
keep their paths, groups, conclusions, layers, source/ZERO and historical
controls while checking the selected contract. Two whole-static diagnostic
observers now query their known field. The cycle test makes that request
before propagation. A negative test that supplied a class-wide actual fact
now requires rejection and still checks that no ordinary request chain starts.
Two new synthetic class-copy fixtures had invalid transfer wiring. Valid
conditional source rules replaced them and retain their finding assertions.
Exact-global tests cover both supply orders, wildcard queries, cleaner reach
and absence of class abstractions.

## Fresh main references

The main reference is `origin/main` at
`f945f92c5ae554ff4f5fc7696ee5a269210478bb`. It has only the existing
MemoryManager snapshot hook. The four references ran serially through direct
`:runProjectAnalyzer`, with the pinned portable inputs and Java 17. Main/test
source, all input bytes, rule equality and runner/profiler controls match at
the before and after endpoints. Candidate source was not part of this main-
only measurement and no candidate result is certified by it.

| Project | Heap / IFDS timeout | Outcome | Invocation seconds | SARIF findings | Latest numbered iteration |
| --- | --- | --- | ---: | ---: | --- |
| OWASP | 8 GiB / 1,000 s | OK | 118.218 | 4,112 | Not applicable: legacy main |
| conductor | 8 GiB / 1,080 s | OK | 93.175 | 7 | Not applicable: legacy main |
| thingsboard | 12 GiB / 1,080 s | OK | 223.493 | 14 | Not applicable: legacy main |
| Stirling-PDF | 8 GiB / 1,080 s | OK | 49.120 | 17 | Not applicable: legacy main |

All outer limits remain 1,200 seconds. Invocation time includes startup and
export; it is not the BIDI run-1 engine metric. Continuous JFR is available.
No main OOM occurred. Old raw profiles, heaps and MAT indexes were cleaned
before this study: 40 files and 72,588,396,495 bytes. Logs, SARIF, manifests
and derived previous evidence remain.

The candidate's latest reached, completed and accepted project iterations
are **not started**. No post-optimization speedup, run-1 target result or
strict no-FN comparison is established yet.

## Evidence and remaining work

Evidence root:
`/drive-testcomp/opentaint-go-rules/run1-static-requests-20261020`.

- `validation/gate-final-exact-class-v2/receipt.json` and copied XML: final
  broad local gate on stable runtime source.
- `validation/exact-class-markers/receipt.json` and logs: final formal checks.
- `validation/enum`, `validation/group-fastpaths`,
  `validation/static-requests`, `validation/expectation-migration`: targeted
  references, expected failures and verification.
- `main-reference/equality.json`, endpoint snapshots and per-project
  manifests/summaries/SARIF/JFR: closed fresh main references.
- `preparation/old-raw-cleanup.json`: cleanup scope and removed byte counts.

The implementation is ready for commit. Bind the four fixed candidate
workloads to that commit and the same input bytes as these fresh main
references. Compare strict findings, inspect profiles and every MemoryManager
OOM capture, and record reached/completed/accepted iterations. The separate
performance report and next-iteration proposal must use those new results.
