# Current proof status

## Selected: FULL return cleanup and temporary static supplies

The [selected specification](run1-full-return-cleanup-and-static-supply-batching.md)
combines canonical run-1 FULL supply merging and bulk equal-geometry FULL
return cleanup. The [proof dossier](run1-full-return-cleanup-and-static-supply-batching-proofs.md)
separates constructive local models, finite closure/handoff transport and
the mandatory source-to-runtime adapter checks. Actual implementation is
unchanged. GROUP, later runs, field transfer, source/exit behavior and raw
static-service ordering remain outside the selected execution changes.

The specification preserves all required requests and partial DEMAND
complements. It keeps the existing result dispatcher and raw suffix resume
addresses. Image-cohort storage, grounding and new field eligibility are
not selected. No new project performance result is claimed: the measured
Thingsboard FULL attempts reach run-1 OOM, with no COMPLETE or accepted
forward iteration.

The integrated Lean build passes 174 jobs. The combined audit checks 1,610
owned declarations and 71 controls; the standalone supply audit adds 24
integration controls, for 95 distinct executable controls. No forbidden
axioms occur; the permitted dependencies are `propext` and `Quot.sound`.
The [receipt](lean/run1-full-work-batching-checks.json) pins the final
sources. Concrete trie/collector/request-owner correspondence remains an
explicit implementation obligation, not a claimed checked Kotlin theorem.

## Selected design: run-1 static summary erasure

The [new specification](run1-static-summary-erasure.md) removes all run-1
static/static summary rows after their required producer effects. It
keeps local FLOW writers, first ordinary caller bindings, computed
supplies, retained mixed/nonstatic/ZERO relations and sink seeds. It
replaces the removed run-2 static guide starts with bounded actual-input
query completion, including same-field EXACT. The user also selected
named-field and EXACT class-marker concrete grammar in run-2. These are
explicit major changes; no field-transfer or source/exit-rule change is
selected. **The implementation is present.** See the
[implementation validation](../docs/reports/bidirectional-static-summary-erasure-validation-20261022.md).
The comparison runtime remains `30c1b84606695070a79d7ae467703267b9f931c1`.

The [proof dossier](run1-static-summary-erasure-proofs.md) separates
constructive finite producer/relay erasure, scalar AP projection, exact
query coverage and later FULL witness replay. Its
[audit](lean/run1-static-summary-erasure-audit.lean) checks the four owned
modules. Kotlin constructor closure, scalar/tree refinement, pending
payload ownership, queue accounting and observation/handoff adapters
have separate source reviews and focused controls. These checks do not
constitute a whole-engine refinement theorem. No whole-engine soundness, termination or
performance acceptance is claimed. The focused 53-job Lean build passes;
the final owned audit passes **1,822 declarations and 75 controls**, with
only `propext` and `Quot.sound` dependencies. The
[check receipt](lean/run1-static-summary-erasure-checks.json) pins the
final sources. Historical reference Thingsboard,
Conductor and PDF status is reached **run-2 OOM**, latest COMPLETE/accepted
**run-1/run-1**. OWASP reached **run-5 TIMEOUT**, latest COMPLETE **run-4**,
latest accepted **run-3**. No new project measurement was made for this design.

The selected [mark-mode extension](run1-static-summary-erasure.md#14-select-group-or-full-marks-in-run-1)
adds a GROUP/FULL run-1 flag. The 23 October default amendment selects
FULL for an absent flag; GROUP remains an explicit experiment. Exact
conditions, cleaners and request/service keys use the existing selected
mode. Structural run-1 policy and later FULL runs do not change.

The [local mode model](lean/ApSpec/Run1MarkModeExperiment.lean) checks
selection, key identity and FULL-guide query coverage. The original
[216-declaration/30-control receipt](lean/run1-mark-mode-experiment-checks.json)
remains a historical GROUP-default snapshot. The current
[FULL-default receipt](lean/run1-full-default-checks.json) records the
23-job build and audit of **218 declarations and 31 controls**, with no
unexpected axioms. The earlier 75-control erasure model keeps its original
scope. None of these local models proves the Kotlin engine.

The explicit-mode b68 paired campaign is complete. Its
[current report](../docs/reports/bidirectional-static-summary-erasure-performance-20261022.md)
keeps the actual iteration labels: Thingsboard/Conductor/PDF reach run-1
or run-2 OOM; the latest OWASP pairs reach run-8 TIMEOUT with run-7
COMPLETE and accepted. The FULL-default amendment does not add a project
measurement or make the failed acceptance gates pass.

## Selected 22 October specification: local writers and summary aliases

The [complete specification](run1-static-writers-and-summary-aliases.md)
selects local-transfer-only static writer registration and the run-1
no-memory-effect alias rule. Keep caller summary/demand publication and
computed ZERO/all-static group supplies. Local FLOW writers own their
immutable bounded relations. The selected alias rule has no DEMAND bypass
and ignores TAINT tails; it is not equality with the old alias closure.
No FULL/native/reversed, field, source or mark-policy change is selected.
**The selected changes are implemented.** See the
[implementation validation](../docs/reports/bidirectional-local-static-writers-aliases-validation-20261022.md).

The two constructive models pass the focused Lean 4.33.1 build and the
aggregate 171-job build. Their [audit](lean/run1-static-writers-and-summary-aliases-audit.lean)
passes 1,490 owned declarations and 46 executable controls with only
`propext` and `Quot.sound` dependencies. The
[proof dossier](run1-static-writers-and-summary-aliases-proofs.md) records
finite caller-job replay, rooted supply, independent local admission,
immutable coverage, exact packed selection and saved call-state laws.
The production controls now cover AP/trie selection, actual Base and point
replay, local producer admission and demand handoff. The common suite
passes 1,737 tests; the JVM suite passes 438. All five new JVM acceptance
tests pass. Existing cleaner and exit-scope precision failures remain
explicit failed gates in the validation. There is no whole-engine no-FN,
termination or performance theorem. The
[closed performance report](../docs/reports/bidirectional-local-static-writers-aliases-performance-20261022.md)
uses committed runtime `a4bf78986` and fresh main `f945f92c5`. OWASP reaches
run-8 TIMEOUT (latest complete/accepted run-7); conductor and Stirling-PDF
reach run-2 OOM (run-1/run-1); Thingsboard reaches run-1 OOM (none/none).
Only conductor and PDF meet 60 engine seconds in run-1. Strict finding
inclusion fails for Thingsboard and PDF. OWASP location parity does not
pass its exact occurrence-count CI assertion. These local proofs and
finite controls do not override the failed project gates. The
[next proposal](../docs/design/bidirectional-local-static-writers-aliases-next-iteration-20261022.md)
does not authorize another runtime change.

## Implemented: Thingsboard run-1 static GROUP protocol removal

The [complete specification](run1-static-group-protocol-removal.md) and
[proof dossier](run1-static-group-protocol-removal-proofs.md) select removal
of repeated call/supply requests for already-processed static GROUP originals.
Keep the same local scalar AP reducers and scalar caller suffixes. Packed
suffix execution and the other candidates in the
[next proposal](../docs/design/bidirectional-local-static-writers-aliases-next-iteration-20261022.md)
remain unselected. The scalar runtime adapters are implemented. Their
[validation report](../docs/reports/bidirectional-static-protocol-removal-validation-20261022.md)
records the checks and project campaign status.
The ZERO exit restriction and Spring rewrite are withdrawn/deferred;
controller annotation-based exit selection remains in place.

[Run1StaticPremiseAvailability.lean](lean/ApSpec/Run1StaticPremiseAvailability.lean)
passes the focused Lean 4.33.1 check and 24 executable controls. Its
owned-module audit checks 533 declarations with no unexpected axioms.
It constructively derives prior supplied/processed provenance from the
modeled production seams, preserves original identity, checks first and
repeated writer-request effects, and excludes unseeded GROUP cycles.
It assumes no new exit/GEN eligibility restriction. The new
[protocol model](lean/ApSpec/Run1StaticGroupProtocolRemoval.lean) reuses the
same reducer and canonical input. It proves finite method-owned demand
pair/seed equality, transports explicit/implicit run-2 guidance, proves
empty-E selector persistence and preserves finite positive supply/effect
closure. The proof dossier records the source correspondence and focused
check receipt: Lean 4.33.1 passes 1,441 owned declarations and 66 executable
controls, with no unexpected axioms. Duplicate call offers can change
physical suffix counts; the model proves semantic membership preservation,
not equality of scheduler callback counts. Raw-output AP identity and packed suffix additivity are
not needed by this selected contract. The edited Kotlin adapters pass
the specified focused production controls. Common dataflow passes
1,745 tests and JVM dataflow passes 438 tests. The application gate has
nine cleaner failures and the rule gate has two exit-scope failures.
Fresh exact-base controls reproduce the same selected findings. These
gates remain failed; project measurements remain separate. See the
validation report for their current state.
There is no whole-engine equivalence or causal patch-speedup claim. The
[current performance report](../docs/reports/bidirectional-static-protocol-removal-performance-20261022.md)
uses committed runtime `30c1b8460` and fresh main `f945f92c5`. All four run-1
engines complete. Thingsboard, Conductor and PDF reach run-2 OOM, with
run-1 complete and accepted. OWASP reaches run-5 TIMEOUT, with run-4
complete and run-3 accepted. Only Conductor and PDF meet 60 engine seconds
in run-1. The strict location gate fails on two PDF returns; original-path
coverage remains unproven. OWASP retains all main strict keys but fails
the separate exact trace-count criterion. The fresh source-bound heap
reviews are closed. Thingsboard's incoming run-1 demand store retains
2.683 GB in run-2; 84.68% of its physical pairs are static/static.
The [next proposal](../docs/design/bidirectional-static-protocol-removal-next-iteration-20261022.md)
marks relay elimination and replacement guidance as a major new contract.
The current preservation theorem still requires the existing complete
per-method demand relation. No further runtime change is selected. The
[earlier focused investigation](../docs/reports/bidirectional-thingsboard-run1-workload-investigation-20261022.md)
uses the pre-change Thingsboard run-1 OOM capture; its completed and
accepted iterations are both none. It is historical context, not a new
measurement of these adapters.

## Current iteration: all origins, routing and GROUP projection

The [implementation specification](../docs/design/bidirectional-run1-origins-routing-projection-implementation-20261021.md)
selects all contributing atomic run-1 origins, impossible exact-class/field
routing removal and canonical GROUP projection reuse. **Runtime changes
are implemented in `d7244c08c`; fixed-project runs are closed.** The first
item is a semantic correction to the earlier
representative-only result; the other two preserve the corrected contract.
ZERO requires a complete compatible all-ZERO cube. FULL ND, sink witnesses,
field/static transfer and the run schedule retain their current behavior.

[Run1AllOrigins.lean](lean/ApSpec/Run1AllOrigins.lean) and
[Run1RoutingProjection.lean](lean/ApSpec/Run1RoutingProjection.lean) pass the
focused Lean 4.33.1 build. Their
[audit](lean/run1-origins-routing-projection-audit.lean) covers 26 statements
and 20 executable controls. Only `propext` and `Quot.sound` remain; there
is no classical choice, placeholder or native decision axiom. Checked
scope: scalar/factored independent origins, exact ZERO, shared-leaf origin
and header agreement, complete handoff coordinates, fresh memberships,
arbitrary-length exact-class disjointness/prefix necessity and local
projection value identities. NNF/store/actor adapters, receiving-manager
canonical identities and the audited callback's cover closure remain
explicit implementation gates. This is not a whole-engine or termination
proof. The common suite passes 1,713 tests; the JVM suite passes 438. Query
controls pass 38 with three existing skips, and the consumer build passes.
The broader application suite has 166 passes, nine existing finding-set
failures and one skip. Its source-checked pre-iteration control has the
same 176 outcomes and the same 27 extra cleaner candidate IDs, with no
missing expected IDs. Each failing analysis completes and accepts run-9
and stops with `POLICY`. This comparison establishes no new regression
in these controls; it does not make the broader suite green. See the
[closed comparison](/drive-testcomp/opentaint-go-rules/run1-origins-routing-projection-20261021/validation/application-regressions/comparison.md).
The [current performance report](../docs/reports/bidirectional-run1-origins-routing-projection-performance-20261021.md)
compares this runtime with fresh main `f945f92c5`. Candidate OWASP reaches
**run-3 TIMEOUT** (latest COMPLETE run-2; accepted run-1); conductor reaches
**run-2 OOM** (run-1/run-1); thingsboard reaches **run-1 OOM** (none/none);
Stirling-PDF reaches **run-2 OOM** (run-1/run-1). All four commands fail a
resource gate; only Stirling-PDF completes run-1 within 60 engine seconds.
Strict location inclusion passes OWASP and conductor, and fails thingsboard
(14 omissions after run-1 OOM before report admission) and Stirling-PDF
(two unmatched earlier-return coordinates; synthetic-exit/Simple export
uses the last return).
All omissions have a source review; exact missing-flow coverage is not
proved. The current measurements do not establish overall performance or
finding parity. Formal local proofs do not override those integration limits.
All eight profile reads and all three live OOM heap reviews are closed.
Thingsboard run-1 retains large static recipient/dependency stores. The
conductor and Stirling-PDF run-2 class censuses contain no run-1 static
service or point-resume owners; they retain FULL edge state and pending
publications. See the report for overlapping retained-set sizes and query
limits. The [next proposal](../docs/design/bidirectional-retained-work-next-iteration-20261021.md)
contains no runtime change and has its own proof gates. Its layout models
are not measured savings or a whole-engine equivalence proof.
The representative contract below is historical. Its proofs do not replace
the current all-origin and complete-handoff-pair obligations.

## Historical 21 October run-1 representative premises

The [selected implementation contract](../docs/design/bidirectional-run1-single-premise-rules-20261021.md)
updates [ap.md §0.4](ap.md#04-run-1-all-contributing-origins-selected-21-october)
and [the grouped/static spec](grouped-run1-statics.md#01-run-1-all-origin-exception-21-october).
A complete local binding now emits one original in run-1. All GROUP results
stay DEMAND. Source geometry, complete sink evidence and later FULL ND
remain unchanged. This deliberately weakens caller/static correlation;
old exact-ND models below are reference/FULL evidence, not an equality
oracle for the new run-1 candidates.

The [new module](lean/ApSpec/Run1RepresentativePremises.lean) and
[narrow audit](lean/run1-representative-premises-audit.lean) pass with Lean
4.33.1: 23 audited statements, no `Classical.choice`, `sorryAx` or native
decision axiom. Only `propext` and `Quot.sound` remain. The
[final receipt](/drive-testcomp/opentaint-go-rules/run1-single-premise-design-20261021/lean-receipt-v2.json)
binds the constructive source/log hashes. Checked scope: complete cube
availability, stable member choice, fixed-clause positive-Horn inclusion,
atomic labels and equal projected handoff entries for corresponding outputs.
This is not whole-analyzer discovery, full backward recovery or a runtime
bound. The [proof map](grouped-run1-statics-proofs.md#historical-21-october-representative-update)
lists the remaining AP/producer/static/source/sink/handoff adapters.

Latest relevant measured source is `0f7feafd88d5ca84dd1ae12ef4b8a4920c3d1b48`.
ThingsBoard with `disableRun1Statics=true` reached **run-1**, OOM after
664.799 engine seconds; no run completed or was accepted. That measurement
predates this representative change. No fresh performance acceptance is
claimed here. Earlier checkpoints retain their own source/test scope.

## Selected 20 October request-state iteration

The [selected contract](run1-static-request-state-and-group-identities.md)
and [proof map](run1-static-request-state-proofs.md) control the enum alias
**DIRTY HACK**, three-set field requests and immutable GROUP identities.
Their semantic change ledger overrides the older scheduling and answered
image retention below. The final local gate passes 2,139 tests. The
168-job Lean build and the 943-declaration, five-module selected audit pass
with 67 executable controls. The user resolved the class invariant:
actual/initial `S.C` global-mark carriers are exact; no class abstraction or
class STAR family exists. The current parser has eight families. Class ANY
query metadata remains inclusive of the exact carrier and fields; strict
BELOW excludes the exact carrier. The focused class audit passes 339
declarations and 39 controls. The final stable-source gate includes the class
adapter edits: 1,632 common, 436 JVM, 38 application and 33 rule tests, with
no failures, errors or skips. Its receipt is
`validation/gate-final-exact-class-v2/receipt.json` in the study evidence root.
The [validation checkpoint](../docs/reports/bidirectional-run1-static-request-state-validation-20261020.md)
records fresh main references and the missing candidate measurements.
No other field, caller, rule, layer or run-schedule change is selected.

## Selected combined mark/static specification

[Mark groups and direct static handling](grouped-run1-statics.md) is the
combined contract, subject to the selected update above. Start from `a80d5c580b3b4715ab39679fbf66f0f190125fa0`,
whose core/rules equal measured `28a123f0`. The
[baseline manifest](baselines/pre-bottom-up-a80d5c580.json) pins the document
and runtime trees. The selected base predates the bottom-up static runtime.
The current workspace has not been reset or implemented from that pin.
The [implementation proposal](../docs/design/bidirectional-grouped-statics-implementation-20261019.md)
uses that pin. The current scope treats `trackFactsReachAnalysisEnd` as empty
for every run; it adds no pending end-GEN runtime component. Existing proofs
apply to the corresponding effective-rule reference subset.

The [combined proof dossier](grouped-run1-statics-proofs.md) separates the
concept, merged storage and finite inclusion claim. Run-1 uses owner-group
DEMAND taint, write-gated conditional reads, early static effects and direct
operation-input refinement. Run-2 uses full marks at limit 1. The implicit
all-method static GROUP demand preserves actual full queries. Baseline
StrongQueries already admits actual queries and their computed results;
no helper-permission builder or backward writer-search graph is required.

The constructive composition creates target permission and full positive
replay from a finite full reference tree. It retains all guards and exact
source leaves. The connected getter/wrapper/relay/setter control uses actual
port/body certificates. The current initial parser checks eight scalar
families and rejects the historical root and class-chain families. The
historical counts below keep their original checkpoint scope. Whole JVM/AP/
condition compilation and queue/record adapter correctness remain explicit
production gates. This is not a whole-engine proof or a runtime speed claim.

Earlier strict-guide counterexamples and guide-builder models remain local
or historical evidence. They do not add the removed runtime phases. The
unified audit and stable-source receipt are linked in the combined dossier.
Lean builds **159 jobs**; the audit checks **6,812 declarations**, passes
**461 controls**, and finds **zero unexpected axioms**. These totals include
the stated historical comparison models.
Runtime source and the measured project checkpoints below are unchanged.

## Latest measured branch: caller-owned subscriptions

The [detailed caller-owned contract](run1-caller-owned-subscriptions.md) and
its [proof dossier](run1-caller-owned-subscriptions-proofs.md) specify direct
canonical static descriptors, the existing caller input relation, shared
callee/field request views, callsite P/A frontiers and ordinary full-port
summary application. The measured branch implements that caller-owned design. The
[implementation map](../docs/design/bidirectional-caller-owned-subscriptions-implementation-20261018.md)
records the adapters. After the mixed-batch correction, the full common
gate passes **1,607 tests**, the JVM
gate passes **411 tests**, and the analyzer consumer jar builds. The fresh
application selection has **148 passes, nine precision failures and one skip**;
its acceptance gate fails. The corrected fixed-configuration project matrix
and 80-check/41-dataset input audit are closed; project acceptance also fails.

The constructive models build covered ordinary request endpoints and translate
finite ordinary analysis derivations to caller-owned derivations. The required
summary guarantee is one-way semantic inclusion: fixed prescan reachability
can add conservative interests. Actual mark guards and full ND bindings remain
required. Kotlin producer, packed-store, codec and scheduling refinements are
explicit obligations, not consequences of the finite calculus proof.

Measured runtime: `63d5481e4e4b69ecf042c1149bb574ad40fd1624`.
Latest reached / completed engine / accepted forward report: OWASP
**run-7 / run-6 / run-5**, conductor **run-1 / none / none**, ThingsBoard
**run-1 / none / none**, and Stirling-PDF **run-2 / run-1 / run-1**.
OWASP and ThingsBoard timed out; conductor and PDF stopped with OOM.
All four miss the 60-second run-1 engine target. The fixed matrix and
80-check/41-dataset input audit are closed. The
[current performance report](../docs/reports/bidirectional-caller-owned-subscriptions-performance-20261018.md)
records strict finding differences, CPU evidence and memory analysis.
The full core gate passes, but application precision and project no-loss
acceptance fail. These measurements do not prove a semantic transfer loss
or a universal saving. IR and cache/reference-manager rewrites remain outside
implementation scope.

Main uses the legacy engine and has no numbered bidirectional iteration.
Older checkpoints below retain their own source and measurement scope.

## Previous measured static-need replay batching implementation

The [batched replay contract](run1-static-need-replay-batching.md) extends
the [implemented concrete-static contract](run1-bottom-up-concrete-static-propagation.md).
It batches graph growth at each owner-turn boundary and shares a fresh
multi-start traversal only for equal selected mark sets. Source marks still
commit at method-empty. The
[implementation map](../docs/design/bidirectional-static-need-replay-batching-implementation-20261017.md)
and [runtime validation](../docs/reports/bidirectional-static-need-replay-batching-validation-20261017.md)
record the selected runtime changes and their checks.

The [proof dossier](run1-static-need-replay-batching-proofs.md) defines the
logical terminal candidate relation before committed-R filtering, gives
constructive source/path arguments, and separates finite-model checks from
the required owner-queue, cancellation and coupled-engine adapters.
The older checkpoints below retain their original scope; they do not certify
these new adapters.

The [focused validation](../docs/reports/bidirectional-static-need-replay-batching-proof-validation-20261017.md)
checks 303 new-module declarations with zero unexpected axioms and 28
kernel-certified executable controls. It proves finite endpoint/grouping
equivalence, source/delta union, serialized registration/flush coverage,
committed-R filtering and scoped cost laws. Live overlapping owner waves and
coroutine lifecycle refinement remain explicit implementation obligations.

The model was rerun: 303 declarations, 28 controls, zero unexpected axioms.
Runtime checks pass: 1,585 common tests, 411 JVM tests and the consumer jar.
Two later test-only additions pass in the final 19-case batching/runner
selection; production sources are unchanged. Application acceptance is
reported separately. The finite model does not certify the Kotlin scheduler.

Fresh measurements on `f18b1c5bb` are closed. Latest reached / completed /
accepted forward: OWASP **run-7 / run-6 / run-5**; conductor **run-1 / none /
none**; ThingsBoard **run-1 / none / none**; Stirling-PDF **run-2 / run-1 /
run-1**. All main invocations succeed; the candidate has one timeout and
three OOMs. None meets the 60-second run-1 engine target. The
[current project report](../docs/reports/bidirectional-static-need-replay-batching-20261017.md)
separates strict finding/location and resource failures from finite-model
proofs. These failed gates do not invalidate the local set laws, and those
laws do not prove project acceptance.

## Earlier bottom-up static FLOW proof checkpoint

The selected next-iteration contract is
[run-1 bottom-up static FLOW](run1-bottom-up-static-propagation.md). It overrides
the static transport/request/default/persistence parts of the
[measured unified contract](run1-field-writes.md). It keeps selective local
overwrites and layer-only demotion, removes field requests, makes static FLOW
sticky DEMAND, uses universal ordinary DEMAND defaults and ordinary NORMAL
persistence, and replaces transparent static carriers with exact shared transport.
No S.* initial or normal-exit gate is introduced.

The [new proof dossier](run1-bottom-up-static-proofs.md) maps every design
obligation to its constructive model and states the remaining runtime adapters.
It covers the nine initial families, executed relay checking, lossless full-port
input coding, ordinary/contracted finite derivations, mutual ND clauses,
symbolic mark lookup, two-sided replay, compaction, persistence and a strict
cost improvement on transparent chains. Semantic report/source/sink identities
and rule occurrences must agree; batch-dependent callback counts may differ,
as selected by the user. The
[validation report](../docs/reports/bidirectional-run1-bottom-up-static-proof-validation-20261017.md)
binds the final sources and audit commands. Analyzer implementation is unchanged.

This is a checked scalar/finite-derivation design, not a whole-engine proof.
Frontend/model/alias and whole NNF/ND compilation, packed representation,
actual scheduling/cancellation and complete persistence/handoff/driver refinement
remain implementation obligations. No prior receipt establishes those adapters.

Latest measured reached/completed/accepted iterations remain OWASP
**run-7 / run-6 / run-5**, conductor **run-2 / run-1 / run-1**, ThingsBoard
**run-1 / none / none**, and Stirling-PDF **run-2 / run-1 / run-1**, on runtime
`28a123f0e`. No project measurement was repeated for this proof update.

The foundation remains [ap.md](ap.md), [interpreter.md](interpreter.md),
[analyzer-core.md](analyzer-core.md), [run-1 requests](run1-requests.md), and the
selected overrides above. The older receipts below retain their original
scope/source hashes; their field gates, root/frame, weak-write or persistence
policies do not certify the new design.

## Earlier unified-contract checkpoint

This checkpoint and its normative references describe the earlier contract.
The selected contract and proof scope above take precedence.

The [unified proposal](../docs/design/bidirectional-request-gated-static-handling-20261016.md)
uses ordinary stored N/D static identity summaries. Normal publication,
subscription, application, demand selection and actor handoff handle them.
The abstraction emits no S.* initial in F1. Demotion changes only the edge
layer and preserves the complete AP payload, including its known tail.
The older W2/W8 demand-to-ANY rules are superseded for this operation.

The single [unified run-1 contract](run1-field-writes.md) includes the static
field/mark plans, R/B/dynamic/P lifecycle, matching, caller replay and nine
initial families, alongside that layer-only rule, complete overwrite kills,
disjoint preservation, Empty E, default summaries and strong later behavior. Only a
surviving E-requiring residual demotes; the general weak-write proposal is
superseded. The [proof dossier](run1-field-write-proofs.md)
separates the new independent-layer law from older coarser models. Existing
receipts retain their original source hashes and do not certify this correction.
Consolidating these rules changes documentation ownership only; the Lean
models and existing proof receipts are unchanged. The ordinary
actor/publication bridge is a source-bound design argument,
not a checked scheduler theorem. Runtime implementation is unchanged.

The unified specification supersedes F78's static root/frame and ten-initial
policy. [Static handling](static-handling.md) is now a compatibility index;
request/static rules are no longer a separate normative specification. Its new [proof dossier](static-handling-proofs.md)
separates constructive design proofs from the checked local module. The
earlier F78 receipts below remain valid evidence for their historical
ten-family/root-frame models, not for the new nine-family contract or the
new run-2 query/persistence boundary. Runtime implementation is unchanged.

The normative rules are [ap.md](ap.md), [interpreter.md](interpreter.md),
[analyzer-core.md](analyzer-core.md), [run-1 requests](run1-requests.md) and
the overriding [unified run-1 contract](run1-field-writes.md).
F78 replaces full-query/role requests, request exclusion tests and the callee
deferred-answer memo. It retains the exact caller input relation for summary
application and late requests. This file states proof scope and evidence; it adds
no analysis rule and makes no claim that the measured runtime implements F78.
[proof-history.md](proof-history.md) retains earlier
theorem catalogs; F70/F71 concrete-run results do not prove current restricted runs.

## Selective run-1 field preservation

[Run1StrongPreservation.lean](lean/ApSpec/Run1StrongPreservation.lean) replaces
the historical blanket weak-write reference for local field preservation.
It classifies actual base/path-prefix/tail geometry into complete kill,
unchanged and partial exclusion need. It proves strong-survivor coverage,
full surviving payload/origin, layer monotonicity and Empty E under Empty-E
input/value hypotheses. Definite and possible-alias writes are separate.
Controls cover both initial layers, descendants, exact ancestors, siblings,
mixed components, writeback and independent pre-store values.

Validation passed: **129 build jobs**, **23,656 imported declarations**
with zero unexpected axioms, and all 16 selected cases. The separate module
audit checks all 183 declarations with zero unexpected axioms. The
[new receipt](../docs/reports/bidirectional-run1-selective-preservation-proof-validation-20261016.md)
binds the final sources, commands, logs and scope. Packed/runtime refinement,
actual RHS/cleaner/alias classification and full scheduling/ND/driver proofs
remain obligations. The earlier default/layer-only proof below is unchanged.
Latest project measurement remains **run-1, FORWARD, limit 1; OOM**; none
completed and no accepted forward report. No analyzer run was repeated.

## Ordinary static summaries and layer-only demotion

[Run1DefaultSummaryApplication.lean](lean/ApSpec/Run1DefaultSummaryApplication.lean)
reuses ordinary scalar summary transfer geometry, attaches input/operation/edge
layers independently, and proves exact payload and tail preservation for
identity, layer monotonicity and unchanged requests. Executable controls include
concrete/excluded marks, spatial E, exact/STAR/ANY payloads and both layer bits.
Its Historical namespace records current-kernel normalization and a root-ANY
counterexample; those are not the selected layer-only semantics.

Validation passed: `lake build` completed **128 jobs**; the aggregate audit
checked **23,473 declarations** with zero unexpected axioms. The separate
new-module audit checked all **182 declarations**; 16 selected controls pass.
The [new receipt](../docs/reports/bidirectional-ordinary-static-summary-proof-validation-20261016.md)
binds commands, final sources, logs and audit scope. The new
module does not prove packed representation, publication initialization,
concurrent subscription delivery, ND, handoff or the full driver. Those are
explicit implementation refinements. No analyzer measurement was repeated:
latest reached **run-1, FORWARD, limit 1; OOM**, none completed/accepted.

## Earlier field-write, Empty-E and default-helper receipt

Validation on 16 October 2026 passed: `lake build` completed **127 jobs**;
`lake env lean -M2048 audit.lean` checked **23,337 imported declarations**,
with zero unexpected axioms. All executable certificates passed. The
[fresh receipt](../docs/reports/bidirectional-run1-field-writes-proof-validation-20261016.md)
binds final sources, commands, logs and certificate repairs. Only `propext`
and `Quot.sound` are allowed. No Classical.choice, sorryAx or new axiom is
accepted.

Latest measured iteration remains **run-1, FORWARD, limit 1; OOM**, with no
completed iteration or accepted forward report. Analyzer implementation and
project measurements are unchanged.

[Run1WeakFieldWrites.lean](lean/ApSpec/Run1WeakFieldWrites.lean) historically checks blanket weak
preservation's positive coverage of strong remove-destination/value outputs,
affected/disjoint layers, independent value layers, canonical demand, safe
Empty-E may covers, exact mark-cleaning laws, the closed finite producer
model, least recursive write closure, tagged default specialization, original
callee data and actual backward authorization. Controls use legal normal
STAR marks; normal concrete ANY in the wrapper denotes must, demand ANY may.

[New proofs](run1-field-write-proofs.md) gives the constructive producer,
handoff and strong-reconstruction arguments. The model does not certify the
actual JVM/model/alias inventory, packed codec, queue/publisher integration,
ND confirmation or the complete driver. Its tagged default-call helper and
automatic demand-to-ANY map are historical. Its retention of a fully
overwritten destination is also superseded by the selective-write rule.
F1 defaults are guidance,
not strong return certificates. The old static-only weak-store/bypass and
blanket root-pattern filter are superseded. The nine-family initial and
later strong-query/converse proofs below remain reusable in their scopes.

## Prior request-gated static and strong-query receipt

Validation on 16 October 2026 passed: `lake build` completed 126 jobs;
`lake env lean -M2048 audit.lean` checked **22,798 declarations**, with zero
unexpected axioms. The new module contributes 972 declarations, including
private/generated declarations. Only `propext` and `Quot.sound` are allowed;
no Classical.choice, sorryAx or new axiom is permitted. All executable
certificates passed, including the new backward helper/kill/nonreturn cases.
The [receipt](../docs/reports/bidirectional-static-handling-proof-validation-20261016.md)
binds sources, logs, commands and the repaired initial certificate attempt.

Latest measured iteration remains **run-1, FORWARD, limit 1; OOM**. No completed
iteration or accepted forward report. There is no analyzer implementation
change, new runtime measurement or full-engine correctness claim.

| Result | New checked scope |
| --- | --- |
| Read versus mark-only prescan needs | Effective positive inventory and inherited call paths; ContainsMark adds no field need |
| Nine initial families | Executable classifier/witnesses, exactly old grammar minus special root, Empty E, no ANY/must, unchanged start facts |
| Independent field/mark gates | Supplied normal static-field oracle; actual concrete leaf plus requested mark for concrete answers |
| Historical blanket weak versus strong transfers | Scalar keep/copy/kill/generation and sequence/body inclusion; does not prove selective overwrite kills |
| Strong S→S and converse | Finite returned bodies, ordinary spatial E, ZERO origin and actual modeled-return witnesses |
| Actual later queries | Admission independent of F1 hints, exact finite-candidate answers, pending distinct from empty/identity |
| Recursive balanced calls | Executable generic-state approximants sound/complete for finite balanced derivations, least relation, mixed-state and unproductive-recursion controls |
| Phase isolation | No F1 executable record, weak/strong cache separation, compatible later strong records may cross runs |

[Static proofs](static-handling-proofs.md) gives the constructive proofs for
closure, request/observation coverage, safe handoff, strong call discovery and
backward preimages. The module does not prove packed AP encoding, JVM producer
coverage, actual concurrent scheduling/publisher refinement, ND confirmation,
synthetic-exit classification or the full source-hit/iteration driver. Those
implementation bridges retain their explicit obligations. Historical root/frame
proofs cannot discharge them. Strong normal-return claims concern the selected
model and must not be inferred from virtual-exit reachability alone.

## Historical F78: merged run-1 requests

Validation on 15 October 2026 passed for the corrected no-ANY initial policy:
`lake build` completed 125 jobs; `lake env lean audit.lean` checked 21,826
declarations with zero unexpected axioms. All executable audit groups passed.
That model has EXACT or STAR tail only, with ten initial families.
An incoming ANY/must field supplier projects to concrete STAR; that initial
starts as an ANY fact in DEMAND. The same rule applies to nonexact class
projections. Exact suppliers keep EXACT. Abstract initials remain independently
additive. A requested normal-slot concrete answer requires both an admitted
request and an actual matching supplier carrying its mark; legal shape alone
does not authorize emission. Direct exact class inputs and metadata-selected
broad class components retain their separate paths, without a class request key.

The earlier 125-job /21,810-declaration PASS receipt checked the superseded
twelve-family policy, which allowed concrete field/class ANY initials. It remains
historical evidence and does not validate this correction. The
[proof dossier](run1-request-proofs.md#12-executable-witness-matrix-and-formal-scope)
retains those historical source/log bindings and the fresh full receipt for the
corrected sources, including no-ANY and request-gated additive emission controls.
No analyzer run or runtime change was made.

The [proof dossier](run1-request-proofs.md) gives the constructive arguments for
all nine original obligations and the storage, arrival and completion laws.
Its finite semantic domain includes all ten initial families for the selected
JVM run-1 domain (forward, limit exactly 1, empty handoff seeds and previous-run
records), the explicit impossible exact static-root supplier assumption, a complete
prescan field/form domain, broad suppliers and separate legal class points. The theorem statements
do not assume a predicate that already asserts request coverage.

| Result | Checked evidence | Scope |
| --- | --- | --- |
| Exhaustive run-1 initial grammar, supplied answers and start facts | [Run1InitialShapes.lean](lean/ApSpec/Run1InitialShapes.lean): `classification_exact`, `legal_no_any`, `legalAP_no_any`, `request_projector_no_any`, `class_projector_no_any`, `every_emitted_no_any`, `request_emission_exact`, `every_shape_has_supply_witness`, `started_layer`, `started_tail`, `started_FLOW_exactly_abstract` | Ten families in the selected limit-1 domain; no ANY/ANY_TAINT initial. Shape permission alone does not authorize emission. Generic runtime factories still need a conformance check. |
| Selected abstract field stays additive; concrete field requires its request and supplier | `supplier_starts_no_any`, `abstract_start_additive`, `no_request_abstract_only`, `nonexact_field_additive`, `nonexact_field_without_request`, `nonexact_field_with_request` | For each selected supplied structural slot, lack of R(T) removes only concrete T. It does not expand to irrelevant fields or authorize concrete T from a request alone. |
| Nonexact field/class projection uses STAR with local coverage | `Run1Requests.nonexact_field_chain`, `nonexact_class_chain`, `concrete_chain_any_start_equal`, `concrete_chain_any_same_coverage`, `concrete_chain_covers_exact_and_any`; denotational `concrete_chain_any_same_summary_relation`/`same_reversed_relation`; `replacement_changes_syntactic_summary_guard` | Same DEMAND/ANY start and ordinary denotational relation do not make native caller guards, initial identities, annotated records/reversal, hand-off or driver states equal. The explicit guard counterexample requires runtime refinement work. |
| Shared precomputed base, no duplicate memberships or initial notification wave | [Run1PrecomputedRequests.lean](lean/ApSpec/Run1PrecomputedRequests.lean): `initialise_shares_base`, `trace_no_base_copies`, `trace_preserves_base_reference`, `every_input_after_binding_sees_base`, `no_precomputed_delivery_after_any_trace` | Immutable base reference plus disjoint dynamic residual; temporary requests have an empty base. Logical lifecycle proof, not JVM allocation measurement. |
| Unchanged precomputed static routes need no propagation | `caller_frozen_base_inherits`, `frozen_batch_no_send`, `changed_origin_not_suppressed` | Actual frozen descriptors construct inherited slots and marks. Suppression is per queried slot and actual unchanged origin; changed-origin needs and late wide activations retain dynamic handling. |
| Exact two-slot projector, exclusion-insensitive request matching and separate class points | [Run1Requests.lean](lean/ApSpec/Run1Requests.lean): `project_exists_iff`, `projector_ignores_field_E`, `direct_class_preserved` | Finite scalar leaves; actual concrete marks are required for answers. Broad suppliers do not prove an exact NORMAL class point. |
| Effective producers, least finite graph closure and wide-scope activation | `effective_leaf_collected`, `closure_seed`, `closure_call`, `closure_least`, `recursive_call_coverage`, `wide_activation_complete` | Explicit effective-form vocabulary and complete canonical field domain. The finite oracle computes the least closure; SCC code must refine it. |
| Static/dynamic coverage and original-slot routing | `unchanged_static_plan`, `effective_static_dispatch`, `static_dynamic_cover`, `changed_slot_retains_dynamic` | Installed membership discharges a need; all other normal-origin needs retain their dynamic route. |
| Concrete initials and the special root generate no upstream requests | `concrete_never_requests`, `special_root_never_requests` | Actual premise, not the enclosing method analysis; concrete-answer handling stays separate. |
| Owned field partition and untouched identity frame | [Run1RequestsSafety.lean](lean/ApSpec/Run1RequestsSafety.lean): `finite_partition_exact`, `partition_disjoint`, `primitive_frame`, `frame_identity`, `root_field_composition` | Explicit finite component primitives/return semantics. This corrects unrestricted root-summary composition; it is not a proof of today's packed summary reader. |
| Cleaner/root counterexamples and native/opposite relation preservation | `Counterexamples.raw_root_cleaner_leak`, `Counterexamples.raw_root_write_loses_sibling`, `Counterexamples.constructive_frame_repairs_both`, `native_and_opposite_read_preserved` | Concrete counterexamples and component relation equality. Full F76/driver integration retains its separate scope. |
| One admitted relation plus temporary writes, exact fresh delta and commit/replacement | [Run1RequestProtocol.lean](lean/ApSpec/Run1RequestProtocol.lean): `commit_membership`, `commit_temporary_empty`, `fresh_nodup`, `every_prefix_retains`, `completed_admits_all` | Finite logical `(MethodKey,slot,mark)` cells, not a selected physical map representation. No callee supplier/memo field. |
| Batching, stale admitted reads and excluded-mark duplicates preserve the final union | `two_batches_one_barrier`, `barrier_placement_same_union`, `filter_destination_safe`, `excluded_has_emitted_need`, `derived_flow_exclusion_erasure` | Creator/transfer/call/join induction constructs the same-origin need; duplicates and pending requests are permitted. Event timing and timeout prefixes can differ. |
| Both input/request orders, registration race and local completion ownership | `answer_witness`, `both_arrival_orders`, `registration_race`, `completion_empty_owners` | A<E and Q<V, retained caller inputs, reliable counted queued effects. This is not a termination or 60-second theorem. |

Proof review required two explicit semantic clarifications: component-owned
summary reads plus an untouched identity frame, and same-origin request emission
for generic excluded-mark targets as well as cleaners. Both belong to F78 and the
[implementation proposal](../docs/design/bidirectional-run1-mark-request-implementation-20261015.md).
No Kotlin, IR storage or measured runtime change is included in this revision.

The remaining F78 implementation obligations are concrete refinements: complete
effective-form extraction; canonical AP-tree/component codecs preserving E and
uncertainty; ordinary/native/reversed reader guards; the existing caller index;
and actual lock, queue, cancellation and Work accounting. Finite semantic proofs
do not certify future code, pooled ND confirmation or the whole alternating driver.
Concrete STAR and the former ANY initial can start from the same DEMAND/ANY fact,
but their premise applicability and identity differ. The corrected policy's local
coverage/non-narrowing law does not establish summary, record/reversal, hand-off
or full-driver equivalence. Those bridges require explicit validation.
Historical memo proofs below are retained evidence for their old policy, not a
reason to keep a second request map, full query or deferred answer cache.

## Existing AP and driver proofs

The current general closure model has three tails: exact, star with field exclusion,
and may-any. It is single-premise and alias-free. It does not combine the full
must-tail/X, static, conjunction, end-fact, interpreter, and pipeline extensions.
`CurrentDefs.lean` defines run 1 `RC`, restricted forward `FC`, and backward `BC`.
All use `BaseCleaner.cleanRes`; only `RC` has request rules. `FW` and `BW` select
the current `emitW`, `satW`, and mark-aware `restrictI` operations.
These closures quantify over semantic input histories. They do not prove the selected emitter representation,
and their exact joint-support predicate does not validate a pooled candidate.
These generalized closures do not implement every W6 layer refinement. The
tail/mark shape proofs alone do not prove the typed FLOW any-demand/star-normal
flags of the full spec. These closures use the generic record reader. Their
alternating hand-off sequences still use the historical no-any selector.
F76 read views and F77 structural selection are checked separately below;
they are not an integrated current closure/iteration theorem.

| Current result | Evidence | Conditions and limits |
|---|---|---|
| F75 cleaner coverage with computed output-or-request witness; normal-result exactness | [BaseCleaner.lean](lean/ApSpec/BaseCleaner.lean): `cleanRes_sound_witness`, `cleanRes_exact` | Local operation, not full run coverage. Same-base abstract selected-mark facts always request in run 1, including an already excluded mark. Concrete/all-mark spatial behavior is unchanged. |
| C2/L6 emission produces an actual premise covering the pair, satisfying the added fact, and wholly inside the demand | [CurrentDemand.lean](lean/ApSpec/CurrentDemand.lean): `emitCertificate`; [AbsDefs.lean](lean/ApSpec/AbsDefs.lean): `emitW_contract` | Abstract pattern tail must be exact, any, or empty-exclusion star. A concrete pattern requires a concrete added mark. Demand and added fact cover the same marked location. Nonempty-star counterexample: `Abs.emitW_needs_patTail`. |
| C5 reduction computes a publication retaining the demanded pair, input mark and layer; no new pair | `CurrentDemand.restrictCertificate`, `reduceEmission` | Whole premise inside `D-c` in locations **and marks**; actual input denotation pair; `D-p` present and covers the exit **with its mark**. Premise overlap and location-only exit tests are insufficient. Exact demand intersection has AP §6.4's three representability limits. |
| Normalization computes the actual hand-off pattern and preserves location sets and covered marks | `CurrentDemand.normalizeCertificate` | Changes mark exclusions only. Star field exclusions stay. Must-tail forgetting is a separate X operation, outside this base certificate. |
| Current prefix index gives exactly the same reduction results as a full demand scan | `CurrentDemand.restrict_index_equiv`, `insidePrefixWitness` | Key is `base::path`. Lookup returns ancestors of the premise key; run full `restrictI` on each candidate. Equivalence is result membership, not order or duplicate multiplicity. |
| Per-leaf tree reduction and rebuilt tree have exactly the same whole facts, layers, and mark exclusions | [CurrentTreeReduction.lean](lean/ApSpec/CurrentTreeReduction.lean): `tree_reference_equiv`, `tree_reference_key`, `tree_reference_wf` | Executable reference rebuild. Generic model trees permit cells that canonical typed FLOW values exclude. No optimized structural-walk or full X-tree theorem is claimed. |
| Actual restricted closure initial facts and publications have computed demand certificates | [CurrentDemandClosure.lean](lean/ApSpec/CurrentDemandClosure.lean): `forwardEmission`, `backwardEmission`, `publishedReduction` | Uses the explicit entry-shape guard. These certify local constructors, not coverage of every real witness. Concrete seeds are needed for canonical hand-off/representation results below. |
| Current run-1 and restricted-forward normal edges/records are exact | [CurrentExact.lean](lean/ApSpec/CurrentExact.lean): `RC_edge_exact`, `FW_edge_exact`, `FW_records_exact`; valid variants | S7 mark well-formedness; upward-closed filter acceptance (`FiltUp`), or S13 filter/validity conditions. Restricted runs require exact input records. The proof inducts directly on F75 closures, not old-cleaner embeddings. |
| Current backward nonzero normal edges/records are exact | `Current.BW_edge_exact`, `BW_records_exact`; valid variants | Same mark/filter/record conditions on the backward program; no binding into callee zero. Zero-premise seeds are excluded. |
| Actual reversed backward normal record pairs are forward flows | `Current.BW_rev_record_exact`; [CurrentHandoff.lean](lean/ApSpec/CurrentHandoff.lean): `BW_rev_record_exact_shape` | Mark well-formedness, upward-closed forward filter acceptance (`FiltUp`), exact backward input records, no forward zero back-binding, mark-reversible/exact-shape statements and calls. Premise is nonzero and has empty exclusion for star; the hand-off shape derives the latter. Dropping real backward filters does not establish this theorem. |
| Historical alternating base hand-offs preserve the C2 entry-tail guard | `Current.shapeSeq_entry_shape`, `shapeSeq_PatTail` | Concrete non-star sink seeds and the earlier generic selector. This sequence does not implement F77. Arbitrary record sets suffice for this shape result; their exactness is separate. Current builders must enforce the guard. |
| Local paired demand guards prevent abstract exact FLOW entries and narrowed conclusions | [CurrentKinds.lean](lean/ApSpec/CurrentKinds.lean): `emitW_tree_init`, `restrictI_tree_flow`; historical sequence `shapeSeq_tree_shape` | S7/S8 and the stated paired guards. The sequence also needs concrete non-star seeds, `RecordKinds`, and the earlier selector. Abstract entry patterns are any; exact exit demand implies concrete entry mark. `EntryShape` alone is too weak. Full F77 guard preservation and W6/typed-tree packing remain open. |
| Native record-kind conditions survive raw insertion, guarded generic base reversal, and retained unions | `Current.native_record_kinds`, `revRec_kinds`, `reversed_record_kinds`, `record_kinds_union` | Derive native kinds from reached closure edges. Generic reversed reads use the historical eligible-premise predicate and concrete-premise preservation. This is a conditional generic/base result, not a completeness theorem for all native records or F76 concrete-star views. |
| Exact-premise forward must summary has a computed backward view with exactly its annotated converse pairs | [CurrentMustReverse.lean](lean/ApSpec/CurrentMustReverse.lean): `readView`, `exactCertificate_converse`, `transferCertificate`, `exact_transfer_rejects` | Native normal singleton forward record, concrete premise/target marks, exact premise with empty annotation. Preserve conclusion E on the star read premise; discard the incoming suffix at the exact target. Relation equality is local; S14 native-record exactness is required to infer real program flows. View is transient, never started or persisted. |
| Computed must-premise views cover every annotated converse pair and every application stays demand-layer | `CurrentMustReverse.mustAnyCertificate_covers`, `mustExactCertificate_covers`, `must_read_demand`, `must_application_demand` | Normal singleton native must record, concrete marks, must or exact conclusion. Keep conclusion E on the star input; forget old must-premise E in the may-any output. These are pair-inclusion and layer results, not pair-exactness or program-flow coverage. |
| Must-to-may copying preserves published pairs while forgetting must exclusions | [CurrentMustHandoff.lean](lean/ApSpec/CurrentMustHandoff.lean): `copyCertificate`, `handoff_retains_pair`, `x_any_restricted_demand` | Copy each selected published piece, including an exact narrowed piece. Must-to-may forgetting can add locations. `normal_any_selection` and `selectAny_equiv` describe the historical generic selector only. No full annotated hand-off coverage claim. |
| Exact-to-must reader guards compute a normal transient view | `CurrentMustReverse.exact_guards_eq_normal_read`, `exact_guards_iff_normal_view`, `normalMustWitness` | Native singleton forward record, normal must target and concrete marks, exact premise with empty annotation. Conclusion E is retained. These are reader guards, not callee-demand selection. |
| F76's star/E view matches each canonical concrete exact or may-any requirement covering an admitted marked location | `CurrentMustReverse.concrete_read_matches`, `normal_view_transfer` | Arbitrary E. Matching is `inside || applicable`; exact transfer returns the old premise without the suffix. Abstract marks are outside this contract. |
| Normal exact-to-must view returns a distinct upstream mark and reaches zero through an actual reversed source edge | `CurrentMustReverse.Certificate.upstreamSourceWitness`, `normal_read_reuses_distinct_mark`, `restricted_piece_normal` | Executable local chain with U ≠ T and nonempty E; annotated restriction computes an exact normal publication. No source-hit store or full iteration model. |
| F77 selector retains exactly demand-layer, multiple-premise, and backward-zero summaries | [CurrentSummarySelection.lean](lean/ApSpec/CurrentSummarySelection.lean): `demand_iff`, `omitted_iff`, `normal_forward_singleton_omitted`, `normal_backward_nonzero_omitted`, `restriction_preserves_selection` | Native summaries have nonempty premise sets and satisfy store-kind rules. The finite carrier conservatively keeps an invalid empty set. Actual annotated restriction preserves selection through the unchanged layer. No closure, persistence, or global no-loss theorem. |
| A must-premise backward view produces caller DEMAND without callee demand | `CurrentSummarySelection.must_view_omitted`, `Certificate.caller_demand_without_callee_demand` | Actual native record, read view, and summary application. The native normal singleton is omitted regardless of the view's output layer. |
| Forward conjunction joins literal contribution layers; normal hand-off selection preserves NORMAL; a split branch is not a sufficient forward summary | [CurrentConjunction.lean](lean/ApSpec/CurrentConjunction.lean): `part_factor`, `joined_normal_iff`, `joined_demand_iff`, `normal_publication_example`, `backward_split_example`, `ReverseWitness.both_support`, `ReverseWitness.a_alone_not_support` | Exact rewrite of the current binary `AnyTaintND.conjLayerT`. Local accepted-slot coordination, exact and empty-exclusion must examples, and a concrete support counterexample. No full standing-store, annotated restricted ND closure, or iteration theorem. Selection and persistence policies are unchanged. |
| A matched TAINT literal has the input layer; its unused coverage walk can be removed | [CurrentProposal.lean](lean/ApSpec/CurrentProposal.lean): `LiteralLayer.matched_taint_layer`, `LiteralLayer.Certificate.checked` | Concrete literal gate, annotated overlap, exact/any literal and exact/must/may TAINT input. A normal exact hit is covered; a normal must hit uses the must exception; a demand hit stays demand. FLOW requests and the generic literal rule remain separate. No full tree matching or ND closure theorem. |
| Applying a full concrete summary combination needs only a layer join | [CurrentSummaryApplication.lean](lean/ApSpec/CurrentSummaryApplication.lean): `uncorrelated_member_application`, `Certificate.native_answer_checked`, `Certificate.checked`; `CurrentProposal.Combination.joined_layers`, `Combination.Certificate.checked` | Each nonempty input already matches its concrete premise. Every premise tail, including concrete STAR/E; exact/must/may input and uncorrelated exact/must/may W7 target, with annotations. Each member returns the target with input/summary layer OR and no request; the two old output flags are complementary. The native run-1 answer witness starts DEMAND and applies its DEMAND summary with a distinct target mark. Local per-leaf application and Boolean accumulator equality, not an ND tree or pipeline theorem. |
| The proposal's per-leaf summary selectors equal the reference location and any-tail tests | `CurrentSummaryApplication.Selectors.applicable_at_exact`, `applicable_below_exact`, `inside_at_spatial`, `inside_must_flow_rejected`, `inside_must_excluded_flow_rejected`, `inside_above_exact`, `Certificate.checked` | All surface tails and field exclusions. May-tail exclusions are empty. A concrete must premise cannot match an abstract STAR input, so its differing spatial cell is rejected by the mark test. The proof models the selector rows, not the full Kotlin trie walk, marks traversal or store query. |
| T2 preserves union locations while mutual single-leaf coverage rejects the merged result | `CurrentProposal.T2Oracle.union_locations`, `T2Oracle.Certificate.checked` | Two normal must leaves with different exclusions merge to one empty-exclusion leaf. The union law holds at every concrete marked location for this witness. Compare raw operation outputs by full leaf identity before store reduction; compare stored results by union denotation. No general T2 tree or store equivalence theorem. |
| Must-record demotion does not commute with a general post-result field read | `CurrentProposal.RecordTiming.constructed_summary`, `applicable_only`, `timing_difference` | Actual annotated copy, strong-write keep, summary application, demotion and read. The read is a legal AP operation, but is not an ordinary JVM root return binding. This local boundary does not establish a whole call-plan counterexample or a timing equivalence. |
| Must-record demotion commutes with root mappings, filters, cuts and primitive root cleaners | [CurrentRecordTiming.lean](lean/ApSpec/CurrentRecordTiming.lean): `mapping_commutes`, `filter_commutes`, `cut_commutes`, `root_cleaner_commutes`, `Certificate.checked` | Concrete exact/any inputs with arbitrary annotations and layers for mappings and cleaners; arbitrary target prefixes cover forward alias application. Filters read the same location/mark fact, not its layer or annotation. No full F74 action-chain, alias-selection, optimized trie or call-plan theorem. No valid whole JVM timing counterexample is established. |
| Native must-record pending demotion preserves operation data and finishes after return mappings, before the call cut | `CurrentRecordTiming.LateReturn.pending_pipeline_data`, `split_eq_tagged`, `ordinary_finish`, `pending_finish`, `already_demand_finish`, `pending_pipeline_admitted`, `Certificate.checked` | Arbitrary sequences of per-leaf output operations. Separate inside/pending batches equal a per-member tagged reference; false pending candidates admit aliases before finalization. Surviving pending facts end DEMAND with Empty E; legal already-demand facts receive only the cut. Actual annotated summary/read, binding and alias witnesses check the local order. No early-demotion equivalence, request/premise handling, full F74/DAG, optimized trie or iteration theorem. |
| Below-case correlation-loss test can be removed without changing facts or requests | [CurrentSimplifications.lean](lean/ApSpec/CurrentSimplifications.lean): `LostCorr.applySimple_eq`, `applySimple_may_eq` | Legal STAR conclusions have abstract marks (W2); exact and must targets require concrete premise marks. Such a gate cannot propagate STAR. May-any targets already force DEMAND. Geometry before requests and the above case are unchanged. Local base transfer equality, not full annotated closure equivalence. |
| Historical raw-link confirmation: its separate member-admissibility check is implied by joint support | `CurrentSimplifications.Confirmation.confirm_equiv` | Keep admissibility inside the support's supplier test and zero-only root support. Supplier predicates stand for already computed normal links and caller support at one call. This remains a theorem of that historical support predicate; it does not confirm a pooled ND candidate after raw-store removal. No new fixed-point or vulnerability theorem. |
| Two side conversions equal the three current must-record view branches | `CurrentSimplifications.MustViews.readView_factored_equiv` | Equality for every `NativeRecord`, including rejected shapes. Preserve concrete marks, exact-side empty annotations, conclusion E, input-exclusion weakening and result layers. Generic reversal remains separate. |
| Canonical FLOW satisfaction is annotated location overlap | `CurrentSimplifications.Flow.canonical_flow_satisfaction_X` | Premise STAR with empty field exclusion and mark `*`. Equality uses existing `satX`, `applicable`, and `overlapX` for arbitrary added fact and added exclusion. Keep general matching for excluded or concrete reader premises. Per-leaf equality only; no new optimized tree/trie walk or iteration guard-preservation theorem. |
| One action-list stage and an identity-candidate flag preserve the local plan rules | `CurrentSimplifications.Analyzer.action_stage_merge`, `merge_reverse`, `origin_quotient`, `update_commutes` | Arbitrary ordered actions; reversal swaps endpoints and reverses actions. The alias guard observes identity candidacy and current layer only. Retain unset candidacy before initialization and independent `StageKind`. No whole call-plan, runtime benchmark or iteration claim. |
| One premise-key/layer group check equals per-leaf selection | `CurrentSummarySelection.group_selection_equiv`, `counted_selection_equiv`, `countedPacked_work` | All leaves share the premise key and layer. Equality preserves list order and duplicate multiplicity. Tails, marks, paths, bases, and exclusions do not affect selection. No packing or later-consumer cost claim. |
| Normalized TAINT destination lookup preserves unique exclusion keys and every retained annotated leaf | [CurrentTaintGroupKeys.lean](lean/ApSpec/CurrentTaintGroupKeys.lean): `insert_unique`, `insert_canonical`, `merge_normalize`, `place_reads` | Local subtraction/factory/destination model. Removing the last must leaf resets E to Empty; lookup uses that remainder key. Does not prove T2, full store delta or trie sharing. |
| Sink oracle joins positive literals only within one method key | [CurrentSinkJoin.lean](lean/ApSpec/CurrentSinkJoin.lean): `method_isolation`, `scoped_iff`, `same_method_retained` | Finite local join partition; computed counterexample rejects inputs from different contexts. Sink checks and confirmation are separate. |
| Hand-off counts equal accepted stored patterns, excluding duplicate and implicit-zero insertions | [CurrentDemandFrontier.lean](lean/ApSpec/CurrentDemandFrontier.lean): `method_count_delta`, `build_count` | Local builder/count interface. Pattern tags stand for already normalized canonical patterns; the model does not prove AP normalization. |
| Exact counted-depth metadata skips a subtree exactly when all paths fit the budget | [CurrentDepthCache.lean](lean/ApSpec/CurrentDepthCache.lean): `depthWitness`, `skip_iff_fits`, `stored_skip_iff_fits` | Counted and uncounted accessors, sibling maxima, signed Int representability. The 32,768/32,767 executable regression rejects the old Short saturation shortcut. Does not prove the whole field-limit tree operation. |
| Root EXACT cleaner produces the coarse demand link and preserves the precise descendant in the DLINK fixture | [CurrentDemandLink.lean](lean/ApSpec/CurrentDemandLink.lean): `precise_descendant_disjoint`, `Certificate.check` | Actual annotated read, binding, cut, concrete emission and cleaner vectors at limits 2–5. Local fact trace only; no CFG, source-hit, hand-off or full five-run confirmation theorem. A named field cleaner uses F74 lowering and cannot supply this demotion fixture. |
| Alias selection accepts every demand-layer result and skips only normal identity results | [CurrentAliasGuard.lean](lean/ApSpec/CurrentAliasGuard.lean): `demand_passes`, `normal_identity_skips`, `selection_iff` | Local origin/layer guard used by the engine and form reference. Computed regression retains demand default identity and constructor results. Does not prove alias analysis or the whole call plan. |
| Restricted runs have no request objects | `Current.FC_no_req`, `BC_no_req` | By closure definition, not by concreteness. Abstract facts can occur. |
| Cumulative subscription storage preserves logical P5 and the checked E6 binding quotient | [CumulativeSubscriptions.lean](lean/ApSpec/CumulativeSubscriptions.lean): `grow_encodes`, `growth_trace_encodes`, `accepted_persists`, `fresh_delta_union`, `index_refines`, `effects_refine`, `projected_choices`, `combination_root_insensitive`, `combination_effects_quotient`, `normal_result` | Constructive exact accepted-leaf encoding per full admitted key, finite growth and position/effect lifting; proof history is ghost data, not a retained runtime list. Ordered E6 member projection preserves full references/link layers and every result descriptor after matching. Target denotes the pre-factory conclusion root; equal joined layers give equal canonical factory results. Does not prove the full current AP matcher/transfer distribution, protocol implementation simulation or whole-engine closure. |
| Historical raw-link request phase includes crossed arrivals; a uniform FLOW group needs one geometric match | [RequestSets.lean](lean/ApSpec/RequestSets.lean): `frontier_pairs`, `frontier_arms_disjoint`, `flow_group_effects`, `work_complete_iff` | Exact-delta logical set laws and the extended Work completion predicate. Request and link types stand for full admitted keys. FLOW collapse requires a uniform target and mx in one raw group. The emitter now uses N2 query replay; this theorem does not certify that replacement or require a runtime raw store. These results do not prove the Kotlin path cursor, captured-view ownership, runner simulation, full NNF support or whole-engine closure. Independent scalar and schedule tests must cover those implementation obligations. |

## Historical added-fact-store removal: proof scope

F78 supersedes the request identity, full exclusion predicate and deferred memo
in this section. These statements describe the earlier measured emitter policy.
Their checked theorems remain historical results with their original hypotheses.

The current design is [initial emission without an added-fact store](../docs/design/bidirectional-added-facts-removal-20261015.md).
Caller N2 remains the input relation used for summary application and late mark queries. No theorem about an
input relation requires a second callee copy of that relation. Historical raw-link objects can be ghost inputs
in an independent reference model; they are not a runtime representation requirement.

The proof obligations are deliberately separate:

1. **Emitter refinement.** Run 1 always emits the abstract STAR initial, also for `S`. Concrete initials are
   additive supplier-backed answers. The deferred memo has only question-specific answer shapes and marks,
   with no CallerRef or raw supplier history. Full geometry, E and mx determine eligibility before projection.
   Requesting T clears T only from the matching question's memo rows; the question stays active for later inputs.
   Later runs emit directly against the fixed demand and retain no emitter input history.
2. **Two-sided replay.** Old input/new question and old question/new input produce the same answer/climb relation
   as a full logical supplier join. Subscriber registration also replays standing questions. N2's existing
   run-1 summary index is not sufficient: request queries include prefixes and extensions and then the full AP
   predicate. Persistent question state, admitted N2 headers and counted pending work are part of this contract.
3. **ND scope.** Source and summary results keep full joint premises. Caller application still requires all
   members at one caller/call scope. A pooled ND sink may remain a candidate even when different callers supplied
   its members. It is not confirmed by normal layers or pooled availability alone: without a valid support or
   concrete validation result, `confirmed=false` and a new report entry is DEMAND.
4. **End effects and reports.** Sink end actions still generate zero-premise facts. A pooled candidate can thus
   create derived candidates that ordinary full-ND-premise return matching does not reject. Later trace validation
   must check this trigger dependency too; a complete trace engine is deferred. `Simple` output is not a trace
   certificate. Sticky previously confirmed entries, hand-off selection and stop rules remain explicit driver
   contracts. Source-hit/seed identities stay separate from witness proof identities.
5. **Lifecycle.** Optional exact confirmation through N2 needs a successfully joined read-only window before
   subscriptions are released. Current frozen actor edges and released N2 are not usable after that release.
   Cancellation must not commit an incomplete report or retain a replacement raw-input history.

[AddedFactsRemoval.lean](lean/ApSpec/AddedFactsRemoval.lean) and
[AddedFactsRemovalMemo.lean](lean/ApSpec/AddedFactsRemovalMemo.lean) supply the constructive local models below.
Checked on 15 October 2026 from `spec/lean`: `lake build` passed all 120 jobs, and
`lake env lean audit.lean` audited 19,879 declarations with zero unexpected axiom dependencies. The executable
certificates passed. The 45 explicit new `#print axioms` entries use only `propext`, `Quot.sound`, or no axioms.
Both modules are imported by `ApSpec.lean` and included in the audit. The
[external receipt](/drive-testcomp/opentaint-go-rules/bidi-o1-o3-20261012/run1-abstraction-demands-v2/20261015T004517.956676Z/report-preparation/added-facts-removal-proof-validation/receipt.json)
binds the commands, source and logs; its SHA256 is `e6ce0433ab8abbb719af359faebc104e3f1f5daf5c9e0b6c90d477aff4286364`.
These are local proof gates, not completed Kotlin/runtime or fixed-project acceptance gates.

| Local result | Declarations in `ApSpec.AddedFactsRemoval` | Scope and limits |
|---|---|---|
| Finite question/answer transitions equal the ghost full-history output relation | `trace_refines`, `trace_outputs`, `output_iff`, `pending_iff`, `request_preserves_other_pending`, `request_is_persistent`, `late_supplier_emits`, `both_arrival_orders`, `duplicate_offer`, `origin_multiplicity_storage` | Offers are already eligible for the exact question, including its mark. Question-specific consumption and persistent interest hold for finite event sequences. This does not prove that Kotlin computes every eligible offer or bound the number of distinct answers. |
| Retained runtime state needs no supplier field or completed-output history | `Runtime`, `advance`, `erase_observation_step`, `advance_output_set`, `projected_output_iff` | `State.emitted` is logical observer output. The runtime erases it and sends transient outputs to ordinary projected-initial deduplication. It must not retain answered pairs merely to mirror the proof. No optimized packed-map implementation or heap-size result follows. |
| Packed answer rows refine scalar question/mark memberships | `memoCells_iff`, `packed_insert_refines`, `packed_consume_refines`, `putMark_idempotent`, `one_row_many_marks`; `Certificate.packed_consumption` in `AddedFactsRemovalMemo` | The decoder expands logical `(geometry, answer shape) -> marks` rows. Insertion and consumption preserve the exact scalar cell relation; consuming one geometry/mark leaves other marks and geometries. One fixed row can pack many marks. These are list/set laws, not a JVM persistent-map, primitive MarkSet, padding, allocation or byte-cost proof. |
| Run-1 admission always includes the abstract root and adds ready concrete answers | `run1_abstract_always`, `run1_abstract_shape`, `run1_concrete_additive`, `rc_unconditional_abstract`; `Certificate.run1_static_root_with_concrete` | Uses actual `policy1`; nonzero inputs have root STAR/Empty/*, including `S`. The structural list and ready concrete answers are supplied to this local adapter. Catalog completeness and concrete-answer eligibility are separate obligations, not inferred from interest membership. |
| A finite pooled ND candidate does not establish one-call confirmation | `Certificate.mixed_caller_is_not_confirmation` | Executable two-origin Boolean witness: pooled literals true, same-caller conjunction false. This is a distinguishing precision control, not a whole-engine ND sink, end-GEN or confirmation theorem. |
| Exact N2 replay and two-sided deltas lift the same effect relation | `exact_replay_refinement`, `n2_history_replay`, `n2_two_sided_frontier` | Conditional on exact replay/current cumulative N2 encoding; full origin/admitted keys remain until the effect test. The frontier identity covers crossed input/question arrivals before projection. No Kotlin prefix+extension index, registration race, concurrency or accounting simulation is proved. |
| Immediate restricted emission equals a ghost demand/input scan and instantiates the existing init rules | `restricted_immediate_exact`, `forward_immediate_init`, `backward_immediate_init`; `Certificate.immediate_emitW_values` | Uses actual base `emitW` and the current FC/BC init constructors. This is local membership/constructor coverage, not whole annotated-tree, record or iteration equivalence. |
| Historical per-leaf geometry checks precede answer projection | `checkedAnswer_iff`, `checkedAnswer_initial`, `rc_answer_from_checked`; `Certificate.geometry_distinguishes`, `projected_initial_is_not_supplier`, `exact_consumption_and_late_supplier`, `question_key_distinctions`, `original_slot_and_query_E_distinguish` | Annotated historical same-slot answer kernel. The RC bridge requires equal initial/pattern, empty query E and a nonstatic base. Distinct-slot static catalog geometry, current layer/ANY normalization, full typed trees and FLOW climb routing remain replay/AP obligations. |

Local finite emitter/replay proofs do not prove the Kotlin AP geometry kernel, N2 candidate-index completeness,
callback isolation, pending-work accounting, full NNF closure, source-seeded iteration or concrete trace validity.
No exact current pooled-ND confirmation or whole-engine no-loss theorem is claimed. A local emitter equivalence
uses fixed run inputs; a full iteration comparison must use the same candidate/confirmation policy on both sides.
Replacing raw Support with unverified DEMAND can add seeds and change later run configurations, so unchanged old
support-enabled frontiers are not a consequence of this local theorem. The strict project/main finding gate remains.

## Preserved caller input and local-operation evidence

For N2, P5 means that accepted logical leaves remain under their full caller/group keys, even when transient
replay delta objects are released. The admitted group is not reconstructed from a delta's normalized E.
The independent cumulative fixtures compare raw accepted history with current candidate selection and
summary transfer in run 1 and restricted modes, and compare ordered returned cleaners/aliases/cuts at
positive limits 1, 2 and 3. These are bounded implementation checks. The remaining proof obligation is to
instantiate `effects_refine` with every admitted current AP selector/transfer and to connect the cumulative
encoding to the generic reliable-join protocol under re-entry, records/read views and full current forms.
Existing base/concrete pipeline theorems do not discharge that obligation. N3 scheduler/accounting changes
are deferred and are not part of this proof module.

An existence result is not obtained by selecting data from a proposition. The
certificates above compute the relevant AP function first and carry its proof.
Runtime checks evaluate those data through Lean's compiler.

The maintained candidate index is a checked optimization. On the distinct
33-demand workload, `CurrentDemand.reductionWorkWitness` computes 33 full restriction
calls for a scan and 1 for indexed lookup. This counts full restriction calls;
it excludes index construction. Lookup also walks at most the key length plus
one path-map nodes. Both queries return the same result set. Cleaner batching
has a separate 33-to-1 base-guard check witness in `BaseCleaner.workWitness`;
it still maps the residual over all leaves. Neither result claims constant total
runtime or an optimized sharing theorem for the whole analyzer.

The group selector has a separate work certificate. On 33 sibling must leaves,
`CurrentSummarySelection.Certificate.workWitness` computes 33 selector calls and
33 leaf visits for the reference, versus one call and no leaf walk for the group
shortcut. `counted_selection_equiv` proves equal results; `countedPacked_work`
proves the group cost. These counts exclude premise equality, construction,
trie packing, and consumers.

The regression modules preserve their design versions:

| Module | What its executable witness establishes |
|---|---|
| `CleanerLowering`, `FieldCleanerX`, `ReviewFieldCleaner` | F74 read/clean/write lowering, reach policies, outside-field X fact, and reached 1/2/3 program source hit and finding |
| `ReviewRootCleaner` | Root-EXACT loss under the old spatial rule; historical counterexample, superseded by F75 |
| `ReviewBackwardCleaner` | Backward abstract pass-through can produce a reached stored record and false normal finding; the rejected experiment |
| `ReviewBaseCleaner.Repaired` | F75 produces the concrete request/hand-off, source hit, record, and normal finding for the root-EXACT example |
| `ReviewBaseCleaner.Blocked` | F75's actual stored record reverses with mark exclusion and rejects `T`, blocking that bad-record application |

The last check rejects the witnessed record. It does not enumerate every possible
finding. A reached local regression is not a global iteration proof.

The remaining current obligations are:

1. L1–L4 mode coverage: abstract forward/backward coverage, run-1 mode selection,
   and contract B through F74 lowered actions and F75 cleaners.
2. Full current no-loss iteration with F77 selection, source-seeded emission, the selected candidate/confirmation policy, method exclusion,
   and round-to-round narrowing. Mark normalization and AP §6.4 exceptions limit
   exact narrowing claims.
3. Combined must-tail/X and F76 read-view integration, statics, restricted conjunction support, aliases,
   end facts, and actual interpreter forms; their stronger hand-off guards.
4. Current pipeline/driver encoding and dominance under store subsumption.
   `Pipeline`'s generic reliable-join/quiescence theorem requires a correct encoding;
   historical concrete AP/driver instances do not supply the current instance.
5. Tree shortcuts beyond the proved per-leaf rebuild and candidate index. The
   old `restrictS/U` tree theorem does not prove the current mark-aware tail meet.
6. Connect F78's checked slot, component and no-memo protocol semantics to the
   actual AP-tree codecs, caller candidate visitor, native/reversed record reads
   and counted work. Preserve full ordinary summary premises without restoring a
   callee caller-separated input relation. Concrete verification of pooled ND and
   zero-GEN derived candidates remains a separate deferred task.

These obligations are explicit limits, not established invariants of every
implementation run. The proposals use the checked demand reference and enforce
its representation conditions; they do not claim these wider proofs.

Build and audit from the repository root:

```bash
cd spec/lean
lake build
lake env lean audit.lean
```

[audit.lean](lean/audit.lean) examines every declaration from every imported
`ApSpec` module, including private and generated declarations. It rejects transitive
axioms other than `propext` and `Quot.sound`; thus `sorryAx`, classical choice, and
native proof oracles fail the audit. It also executes the cleaner, lowering,
regression, emission, reduction, normalization, index-cost, tree, must-hand-off,
must-summary read-view, full-selector, destination-key, sink-join, frontier-count,
depth-cache, demand-link, alias-guard, conjunction-layer/split, matched-literal layer,
summary-combination layer, summary selectors, summary-union comparison, record-timing boundary,
root-mapping demotion commutation, late-return batch/finalization, and local-simplification witnesses
(including annotated FLOW overlap and its exclusion/mark boundaries).
The exact declaration count is reported by the audit and changes with the model.
