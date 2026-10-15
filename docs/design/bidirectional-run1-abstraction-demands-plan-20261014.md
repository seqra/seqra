# Implementation plan: run-1 abstraction owns demands

Date: 14 October 2026; updated 15 October 2026. Status: steps 1–6 implemented and validated; step 7 measurements complete.
Step 7 scans, strict comparisons, recording reviews and four guard-heap analyses are complete.
The supplementary container and initial census is complete; occupied statement cells remain
unavailable. The command diagnostic identifies a specified source-rewriter cut and preserves
its coverage limits; its final output comparison passes. Time and finding acceptance still fail.
Use the [redesign](bidirectional-run1-abstraction-demands-20261014.md) as the behavior contract.
This replaces the earlier request-management candidates for the next iteration.

The tested runtime is `85a7c0e367d9a2dc6360b03c2feb5e07b1583b66`: the selected implementation
plus the designed receiver-class cache repair. The fresh corrected control is
`77959b0d8ad870f2bc4fd2f8eaf38044bd302b3c`, with the same repair. The
[current report](../reports/bidirectional-run1-abstraction-demands-performance-20261015.md)
contains all twelve terminal main/control/optimized cases. Latest optimized reached:
OWASP **run-3 / forward / limit 3**; Conductor **run-2 / backward / limit 2**, OOM;
ThingsBoard **run-1 / forward / limit 1**, OOM; Stirling-PDF **run-4 / backward / limit 4**, timeout.
Last completed and accepted: run-3 for OWASP/PDF, run-1 for Conductor, none for ThingsBoard.
Control reaches the same iterations. The target is complete run-1 engine time at most
60 seconds on each fixed project; only optimized Stirling-PDF meets it.

## Implementation notes

The implementation follows the seven steps below. The old indexed request store and position
join now belong to the test-only scalar reference. Production abstraction keeps compact mark
policies and bounded supplier scans. Root emission is immediate; precise structural and concrete
emissions use the method barrier and its bounded quantum. The supplier cursor checks only the
root, class and field levels and does not expand concrete marks to collect positions. Static
mark interests with the same full path, tail, exclusion and role share one persistent mark set
and one supplier scan. Query geometry uses the abstract mark placeholder; the set stores the
actual declared concrete marks. Actual source/sink NNF literals keep their concrete marks.

The metadata collector reuses effective forward rule forms and copies neutral descriptors. It
does not resolve calls again. If prescan has not computed a local alias result, collection treats
alias-dependent atoms as potential in either polarity after the existing contradiction fold.
Known false predicates and `P AND NOT P` remain false. This approximation affects potential
catalog needs only; runtime rule binding and predicate proofs keep their existing behavior.
Call metadata also includes potential default-pass branches when the exported graph does not
record an unresolved branch. Both costs must be visible in setup counters and later measurements.

Rule selection occurs once, before metadata collection. The caller charges gathering against
the total budget; `run` deducts remapping and closure from its remaining budget. The catalog and
runtime coverage graph are released only after run-1 workers join. The report above gives the
current fixed-project results, including setup, each engine and barrier, incomplete progress,
strict main losses and the paired control measurements. Historical measurements do not replace
the current evidence.

## Implementation validation

The latest common-core and JVM validation passes: 1,406 and 395 tests, with no failures, errors
or skips. Common sources are unchanged and their results are up to date; the JVM suite executes
with the receiver-class repair and four new regression tests. The current batch's
`checks/validation.json` binds these results to the tested revision.
Go, JVM SAST and root test sources compile; `:classes` passes. The catalog closure controls include
chain, diamond, recursion, additive mark emission, source/sink NNF, complete witness support and
run-1/run-2/run-3 handoffs. Restricted run-2/run-3 summaries use strict comparison. Run-1 eager
static refinement uses explicit supplier checks and an independent scalar reference.

The Lean build and audit pass: 19,472 declarations, no unexpected axioms. These existing proofs
cover the scalar/local contracts, not the new full catalog closure. The benchmark tools pass
36 tests. The preparation frame exits before engine execution, including duplicate lambda and
rule-ID snapshots. IR storage and reference-manager internals are unchanged.

Fresh fixed-project measurements and strict main/control comparisons are complete. The
implementation does not pass the fixed-project completion and finding gates. Step 7 includes
all twelve scans, profiles, four guard heaps, supplementary census and the isolated command
diagnostic. The latter identifies a specified source-rewriter cut on the main URL receiver
route, with capped-lane limits and exact final output parity. This completes the measurement
work; it does not fix the strict losses or establish a successful optimization outcome.

## Step 1. Specify the new emission policy and oracle

Extend the implementation specification with catalog-selected position emission, mark-interest
static emission and abstraction-owned dynamic mark interests. Keep the current scalar position
algorithm as a reference. Its proofs establish the old contract; they do not prove this redesign.

Define the compared observations: required reachable facts, source/sink triggers, witnesses,
support, summaries, native records and F1/B2/F3 handoffs. Identify extra eager initial/summary
roots explicitly. Compare their supplied effects and refinement results; do not require identical
request traffic or silently remove failing assertions.

Add small program controls for:

- No request, precomputed request, dynamic request and several requested marks: always retain the
  eligible abstract `*` initial and add separate supplied concrete answers. A late request or
  duplicate registration must not remove the abstract initial or restart existing analysis.
- A caller/callee chain whose only field access is in the deepest callee; diamonds and recursion.
- `C.g -> local -> C.f -> static check` and `C.g -> local -> local sink` with an actual marked input.
- The same programs with an abstract `C.g` initial: both must request `T` on that original initial.
- An unchanged static check suppresses the ordinary request without supplying a concrete fact or
  a predicate proof; a later matching supplier still emits through catalog interest.
- Static root covering a changed field, different headers, and packed groups with eligible
  unchanged/changed facts: overlap alone never suppresses a required request.
- An argument/receiver origin written to a static field before a callee's static mark check.
- A callee with empty static profiles still emits `(S, [], STAR, Empty, STAR)` for an incoming static
  fact and preserves it for a caller's later read. Also check root summaries under static writes
  and cleaners, including their kill and exclusion effects.
- Late suppliers, a new caller for an existing fact, and reentrant demand growth during a scan.
- Excluded fields, root/class EXACT facts, deep static answers and must-to-ANY demotion.
- Source and sink NNF AND/OR, false predicates, repeated leaves and same-call support separation.
- F75 excluded/disjoint selected cleaners, lowered field cleaners and D23 return rewriting.

Use the existing `RequestActionTest`, `RequestJoinTest`, `EmissionTest`, `RulePredicateEngineTest`,
`PredicateClosureTest`, `ScheduleFuzzerTest` and `NaiveClosureTest` programs. Add catalog oracles
that derive expected transitive sets by an independent small graph fixed point. Do not replace
the old scalar reference by calls into the new collector.

**Gate:** the behavior contract and expected observable differences are explicit before engine edits.

## Step 2. Export resolved prescan calls and local static metadata

Add a read-only export of existing resolved summary subscriptions. Each legacy runner owns both
internal and external subscription managers; traverse both after joining. Their existing
`collectCallers(false, ...)` operation retains caller entry, call statement and callee entry.
Use that relation rather than `methodDependencies` or a new resolver sweep. Project context rows
to method-level adjacency for the catalog; preserve all observed targets in the union.

Extend the `BidiEntry`/JVM gather handoff to carry the copied adjacency and local metadata. Export
after `requirePrescanJoined()` and before either prescan release. Check successful completion,
not only joined workers. Keep no reference to a subscription manager or prescan context.

Collect transformed static field accesses with the current declaring-owner mapping. Collect
effective static rule positions and mark interests through shared validation/binding metadata
from `JIRStatementForms`, `JIRRuleForms`, `JIRBoundaryForms` and `JIRCallPlanBuilder`.
Include generated methods and native call cleaners. Preserve the existing normal/allRelevant
selection streams. Copy neutral static/mark descriptors; compile IDs with the new AP manager.

Test graph export for multiple contexts, virtual targets, frozen lambdas, unresolved branches,
generated Spring code and prescan release. Test inherited static owners, write keep prefixes,
rule-only positions, boundary predicates and false-rule footprints.

**Gate:** immutable copied metadata outlives released prescan state and covers runtime targets.

## Step 3. Build shared transitive profiles

Compute SCCs and propagate local access, abstraction-position, declared static-mark pairs and
base-wide cleaner interests from callees
to callers. Use one shared profile per component, with immutable sparse sets and shared unchanged
parts. No per-context flattening and no position-by-mark expansion are allowed.

Expose a read-only profile lookup by method. Add aggregate counters for direct/transitive logical
needs, physical cells/bytes, profile reuse, closure time and maximum profile size. Use cancellation
checkpoints during collection and unions. Include preparation in the existing total budget.

Test a sparse graph with many methods and positions but one actual incoming supplier. It must
create shared metadata and matched emission work, without actor-local copies of every potential
position. Check cycles, diamonds, distinct context unions and empty profiles.

**Gate:** compare the catalog with the independent fixed point and verify sharing and lifetime.

## Step 4. Move position refinement into abstraction

Add `Run1Abstraction` at the run-1 branch of `RunMethodAnalyzer.emit`; restricted-run emission
stays separate. Keep zero policy. For every admitted static input, emit the canonical root
`(S, [], STAR, Empty, STAR)` independently of the catalog, with existing initial deduplication.
Its normal FLOW analysis must produce the current keep/kill/exclusion summaries. On raw static
updates, also test the one or two supplier prefixes against structural needs, and emit the current position-answer STAR
initials through `addInitial`. Never emit a deeper position initial from an abstract ancestor.

During migration, use the old oracle to identify every position request answer/climb. Verify
that transitive profiles supply the required upstream refinement and that its facts reach the
same downstream uses. Check supplier matching, E/layer guards and full caller support.

After that gate passes, remove `RequestKind.Position`, `PositionJoin`, position events/outputs,
position rows/lanes and the static exception's request emission. Keep that exception's no-result
parent branch. Runtime position-event counts must become zero.

**Gate:** common/JVM static closure controls, scheduling tests and F1/B2/F3 support/handoff pass.

## Step 5. Install static marks and gate unchanged static checks

Collect position-scoped static mark interests from source/sink leaves and ordinary input-mark
gates; keep base-wide F75 cleaner interests separate. Preserve full literal headers and roles
alongside class/field slots. Install interests independently of structural initial activation,
and process existing and future raw links. Reuse the static answer/start kernel for interested
marked suppliers: retain full paths and exact/any distinction; demote must to ANY/Empty in run-1.
Access collection alone emits an abstract slot and does not assume its required mark is known.
Always emit the eligible abstract-mark initial, whether or not an interest exists. Requested
concrete answers are additional initials. Use the same additive path for precomputed and dynamic
interest, with existing initial deduplication and separate concrete alternatives. Keep root/slot
abstract analysis active when concrete answers arrive. An unsupplied requested mark remains
pending interest and must not fabricate a concrete fact.

Add an ordinary check helper receiving the original initial and the eligible checked input facts.
After the normal geometry/mx gate, suppress the dynamic request only if the initial is STATIC
and every contributing abstract fact equals its started shape (full base/path/tail/E/mx/layer).
Otherwise register `T` on the original initial, even if the current check has a static base or
another catalog entry contains `T`. Do not compare with a literal or source action target.

Thread matched input scope through `MarkCheck`/predicate checks and the ordinary
`EdgeApplication` mark gates used by unary sources and pass rules. A filter in `raise`/`collect`
lacks that scope and would also affect cleaner interest. F75 bypasses the ordinary suppression
helper and retains its unconditional same-base registration, including disjoint/excluded inputs.
Shared preinstalled interest can deduplicate its delivery without dropping the obligation.

Process existing and future raw links under installed interests. Suppress demand delivery only
when the exact original caller abstraction is already covered. Keep delivery to argument/receiver
origins and uncovered chains. Test the argument-to-static case and deduplication after a policy
was preinstalled but before any supplier existed.

Test unchanged `C.g -> C.g`, changed `C.g -> C.f`, `C.g -> local x`, and `Arg0 -> C.f`. Check mixed
eligible groups and unrelated off-literal branches. A suppressed abstract check must never
return `Holds`. Verify that `C.g -> C.f` obtains a late concrete `C.g T` supplier and then satisfies
the original check through ordinary transfer, with the original support and NNF occurrence.

**Gate:** the unchanged/changed request split and required findings/support pass; quantify earlier
initials and body work caused by precomputed interests.

## Step 6. Move remaining mark ownership and remove N1 request indexing

Route dynamic mark checks to `Run1Abstraction.requireMarks(originalChain, markSet)`. The owner is
the incoming initial, not the current local/static literal. Keep one compact interest policy per
simple chain, and reuse precomputed mark sets. Process fresh marks against existing raw links,
and fresh links against prior interests with captured immutable phase views. Keep original
geometry, mx, layers and caller targets before merging outgoing cohorts.

Complete and release each delivery after its policy update is committed. Keep only the interest
needed for future emission and bounded pending work. Remove `RequestStore`, `RequestPathIndex`,
request origin directories, per-mark member/ordinal columns and the separate `RequestFrontier`
once all consumers use abstraction. Reuse the existing raw added-fact store; do not create a
second retained supplier relation or per-target sent-mark cache.

Integrate pending abstraction work into method Work completion and the existing method barrier,
with its bounded quantum fallback. Verify late callers, both arrival orders, new gate eligibility,
captured scans, reentrant updates, cancellation and joined teardown. Preserve scalar nonstatic
chain answers, including distinct EXACT and STAR/DEMAND answers for the same mark.

**Gate:** no request-specific spatial index remains; all fixed-point and lifetime controls pass.

## Step 7. Measure and retest the selected projects

Commit the implementation, then run the same frozen OWASP, Conductor, ThingsBoard and Stirling-PDF
configurations with Gradle `runProjectAnalyzer`. Preserve their memory and timeout settings.
Keep workload jobs serialized with the existing locks. Keep new outputs bound to the tested SHA;
use the current main/control inputs and retire stale capture sets according to the task workflow.

Report prescan/catalog setup, run-1 engine, reporting and later iterations separately. Record
latest reached, completed and accepted run-N in every report. Capture profiles and guard heaps
at the task's required windows/events.

Compare strict finding identities/locations and diagnose any difference. Measure position traffic
elimination, residual deliveries/policies, physical catalog storage, initial/fact growth, allocation,
GC and peak retained owners. Include catalog bytes when evaluating the N1 reduction. The redesign
passes the target only when run-1 completes within 60 seconds; earlier OOM or reduced coverage is
not success. Existing main-comparison losses remain explicit until fixed or otherwise resolved.

**Gate:** required checks and fixed-project evidence are complete before writing a performance claim.
