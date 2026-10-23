# Run-1 FULL work batching: implementation validation

Date: 23 October 2026.
Measured runtime: `fb84969c0b91ccae0500389b5e25e045557b0eb3`.
Reference revision: `3179b9a22e296aa29147dc4d03dd5e8ab093bcf4`.
Its runtime is the [specified reference](../../spec/run1-full-return-cleanup-and-static-supply-batching.md),
`8ab1445b8167a4b2db73f97788653aa4e21a3933`.
The fixed campaign measures these committed runtimes. All 12 commands and
their CPU/allocation profile analyses are closed. Read the
[current performance report](bidirectional-full-work-batching-performance-20261023.md)
for iteration outcomes, location comparisons and completed heap-owner scopes.
Local validation does not establish project acceptance.

## Changes

Both changes execute only in forward run-1 FULL.

| Area | Previous behavior | New behavior |
| --- | --- | --- |
| Temporary static supplies | Append each bounded computed packet to a list | Union FULL atoms under an owned complete region key in each temporary batch |
| Return cleanup | Execute each selected-mark cleaner in order | Execute each eligible equal-geometry range with one exact FULL mask |

The supply gate stays after actual binding and field limiting. It covers
the three existing producer append sites. The direct raw API and GROUP
actors retain their ordered list. The actor retains its selected profile
after each queue-empty commit. No earlier barrier or batch limit is added.

The cleaner retains raw action indices. A middle entry selects only its
remaining suffix. The block has one cached mask and a linear index; it
does not cache all suffix masks. The TAINT operation uses the existing
spatial kernel. The FLOW operation shares the root, adds the selected
exclusions, and requests every selected atom. The existing collector and
static promotion dispatcher remain in use. A nonfixed input executes the
scalar fallback.

Ordinary requests retain their current owner. Static-origin requests use
the current per-atom pending image maps. No image cohort, answered archive
or grounding label is added. The overlap test checks different active
entries, images that arrive before a reply, and a new image after detach.
The driver test checks equal scalar run-1 summaries, run-2 source hits and
run-3 report identities through a static getter.

Field transfer, source and sink rules, origin selection, static-service
transitions, run-2 query completion, later runs and IR storage are unchanged.
FULL remains the production default. No new semantic approximation is selected.

## Checks

The [constructive proof dossier](../../spec/run1-full-return-cleanup-and-static-supply-batching-proofs.md)
states the local model and runtime boundary. The original implementation
delivery checked 1,610 selected declarations and 95 distinct executable
controls. Those counts describe the proof state at runtime delivery.

A later specification-only review adds the executable `CountedFlow` cost
model and its controls. It changes no Kotlin runtime or test expectation.
The current full Lean build passes all 174 jobs. The three selected audits
pass with 1,647 owned declarations: 877 supply, 690 cleanup and 80 closure.
The combined audit has 76 passing controls. The standalone supply audit
adds 24 distinct integration controls, for 100 distinct executable controls
in total. The selected audits find no unexpected axiom dependency. These
checks are not a mechanized proof of the Kotlin engine.

The current build and three audit logs are in
[spec-proof-review-20261023](../../../run1-full-work-batching-20261023/validation/spec-proof-review-20261023).
The runtime test results below remain the implementation-delivery results;
the later proof extension does not replace or clear their failed gates.

| Runtime check | Result |
| --- | --- |
| Common dataflow suite | 1,793 pass; no failures or skips |
| JVM dataflow suite | 438 pass; no failures or skips |
| JVM sequence acceptance after explicit GROUP selection | 13 pass |
| Project comparison/profile harness | 36 pass |
| Whole application suite before fixture repair | 962 tests; 17 failures; 13 skips |
| Current application precision control | 46 tests; 11 reproduced reference failures |

Fresh controls at the exact reference reproduce all 20 initial common
failures, both initial JVM failures, and all 17 initial application
failures. The historical GROUP tests had inherited the new FULL default.
Their fixtures now select GROUP. Their semantic assertions stay in place.
Expected `AnalysisEnd` values also state the selected GROUP mode. The
absent-flag FULL test and the new FULL integration tests remain unchanged.

Eleven application failures remain outside this selection:
five cleaner matrix cases, three control-flow cleaner cases, one field
cleaner case, and two Spring dispatcher setup failures. The latter lack
required provider mark metadata or a joined resolved prescan graph. They
do not establish an exit-transfer defect. The reference reproduces all
eleven cases. This iteration does not change their expectations or their
field and exit semantics. They remain failed gates, rather than performance
acceptance.
The current control retains the same eight complete expected/actual rule-ID
sets, the same field-case Boolean/count assertion, and the same two setup
errors. The saved receipt distinguishes those comparison scopes.

The first new fallback fixture used a concrete TAINT conclusion with a
FLOW premise. The scalar reference rejected it under K6. The corrected
fixture uses the matching concrete premise. No runtime change was needed.

The saved validation commands use the root composite build. They use one
compiler per worktree. An initial concurrent incremental build had invalid
Kotlin metadata; a single nonincremental build restored it.

## Measurement boundary

The aggregate counters distinguish supply offers, offered memberships,
locally fresh memberships, region rows and committed canonical batches.
Fresh memberships are unique within a batch, not globally unique writes.

`fullCleanupSelectedSteps` counts admitted input-step slots. It is not an
exact count of removed scalar calls: a STATIC mismatch can consume its
input at the first step, and a TAINT input can die early. Intermediate
deduplication can also reduce scalar work.

The stricter counters cover only a completed block with one same-base
FLOW input. Each old selected cleaner emits one FLOW result in this case.
For a suffix of k steps, the old loop executes k primitives and the new
loop executes one. Thus
`fullCleanupSingletonFlowSteps - fullCleanupSingletonFlowInputs` is the
exact removed call count for that subset. The counters update after the
bulk operation returns. They exclude an interrupted block.

Aggregate trie-node visit totals and physical header-copy totals were not
measured. Admitted step slots cannot replace node-visit counts. The
`CountedFlow` model and completed singleton FLOW counters count logical
operations, not physical header copies or object allocations. JFR samples
and allocation weights do not provide those missing exact totals. This is
a measurement limitation; no counts are inferred or invented. Neither the
local operation reduction nor these profiles establishes a whole-engine
2x gain or guarantees run-1 completion within 60 seconds.

The fixed project campaign is stored at
`/drive-testcomp/opentaint-go-rules/run1-full-work-batching-20261023`.
It has fresh main reports, fresh reference controls, and fixed FULL
reference/candidate commands for OWASP, Conductor, Thingsboard and
Stirling-PDF. It uses `runProjectAnalyzer` directly. All four main commands
and all eight paired commands are closed. All 12 profile analyses are
complete. All six OOM owner scopes and the stated targeted followups are
also closed. The linked performance report records the measured engine
results, owner evidence and explicit bounded-probe limits. Command,
profile and owner-scope closure do not mark project acceptance as passed.

Main uses the legacy engine, so bidirectional run labels do not apply.
Fresh main Thingsboard reaches a prescan timeout and retains 14 observed
sink locations; this is an incomplete precision baseline. Candidate
results in the performance report state the latest reached iteration,
latest COMPLETE iteration and latest accepted forward report. The run-1
target uses engine time.

Validation logs, copied JUnit XML, source hashes and exact-reference
failure receipts are under the campaign's `validation/` directory.
