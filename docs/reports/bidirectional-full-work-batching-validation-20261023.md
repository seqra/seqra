# Run-1 FULL work batching: implementation validation

Date: 23 October 2026.
Reference revision: `3179b9a22e296aa29147dc4d03dd5e8ab093bcf4`.
Its runtime is the [specified reference](../../spec/run1-full-return-cleanup-and-static-supply-batching.md),
`8ab1445b8167a4b2db73f97788653aa4e21a3933`.
The implementation commit adds this record. The project campaign records
the full implementation commit ID before it starts the candidate.

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
states the local model and runtime boundary. The three selected Lean
modules build. The audits check 1,610 declarations and 95 distinct executable
controls. They find no forbidden axiom dependency. These checks are not a
mechanized proof of the Kotlin engine.

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

The fixed project campaign is stored at
`/drive-testcomp/opentaint-go-rules/run1-full-work-batching-20261023`.
It has fresh main reports, fresh reference controls, and fixed FULL
reference/candidate commands for OWASP, Conductor, Thingsboard and
Stirling-PDF. It uses `runProjectAnalyzer` directly. Candidate measurements
must use the committed implementation. No candidate engine performance
result is claimed by this validation record.

Main uses the legacy engine, so bidirectional run labels do not apply.
Fresh main Thingsboard reaches a prescan timeout and retains 14 observed
sink locations; this is an incomplete precision baseline. Candidate
reports must state the latest reached iteration, latest COMPLETE iteration
and latest accepted forward report. The run-1 target uses engine time.

Validation logs, copied JUnit XML, source hashes and exact-reference
failure receipts are under the campaign's `validation/` directory.
