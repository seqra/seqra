# Run-1 static request state and immutable GROUP identities

Date: 20 October 2026. Status: selected implementation contract.
Runtime comparison base: `029ee41fd6de17388c2f61d0f900cd00f6bd33f8`.
Follow [proof-guide.md](proof-guide.md). Use ASD-STE100 Simplified Technical
English. Code names keep their defined meanings.

**Resolved class contract, 20 October:** `S.C / EXACT / Group(g)` is an
exact global-mark carrier. Its actual fact and initial remain exact. There
is no class-wide mark fact, `S.C.*` initial or abstract class initial.
Preserve an existing exact class marker through the ordinary propagation
boundary. A field fact cannot supply it, and it cannot supply a field.
Class ANY query metadata keeps the existing exact-point-plus-fields meaning.
Query metadata is not an actual class-wide fact or initial.

This contract selects exactly the three changes in the
[implementation proposal](../docs/design/bidirectional-run1-next-iteration-20261020.md).
It replaces the static request scheduling and answer retention in sections
9, 10 and 14 of [the combined spec](grouped-run1-statics.md), and the alias
target exception stated below. The combined spec and
[immutable mark contract](run1-group-policy-and-ownership-census.md) retain
all other behavior. Earlier optimization lists do not add requirements.

## 1. Semantic change ledger

| Mechanism | Current | Selected change |
| --- | --- | --- |
| Enum alias targets | Admit static enum-constant targets | Remove resolved static `ACC_ENUM` targets before edge creation, in the new core only |
| Static group answer | Retain and answer each compatible supplied region | Answer a field request with its canonical `C.f.* / Group(g)` DEMAND descriptor |
| Static writer scheduling | Join all-static FLOW writers on every supplied group | Join a FLOW writer with groups requested for its output field |
| Request retention | Keep answered interests and delivered tuples | Keep unanswered memberships and replies in flight; release completed continuations |
| Immutable mark projection | Normalize already normalized values again | Return the same value when its typed catalog invariant proves the result cannot change |
| Class marker invariant | The historical grammar also admitted a concrete class STAR family | Reject that family; preserve exact global markers and existing predicate/cleaner reach |

The enum filter is a **DIRTY HACK**. It can remove an alias-mediated flow
through mutable enum state. Later runs cannot guarantee recovery. Do not
claim unrestricted soundness for this restriction. Apply the same filter
to both algorithms when proving the other changes.

The class row makes the user's intended invariant explicit. The current
grammar has eight families. The older nine-family grammar is a historical
comparison, not permission to create a class-wide fact. This clarification
does not select new global-mark routing, field transfer or rule semantics.

The canonical answer can add field candidates. This is an explicit run-1
DEMAND approximation. It does not change FLOW read-admission geometry,
ordinary transfer, or later FULL matching.

Keep field kills and transfers, empty run-1 field exclusions, layer-only
demotion, rule identities and conditions, ordinary caller/ND matching,
ordinary summaries and selected cleaner repair unchanged. Keep
`trackFactsReachAnalysisEnd` empty. Keep run-1 GROUP/forward/limit 1,
run-2 FULL/backward/limit 1 and the existing later limits. ZERO satisfies
a static premise only on run-1. It supplies no group.

Do not add GroundingSupport, field validation, new barriers, new reference
management, IR changes, owner-free writer sharing, or backward batching.

## 2. Enum alias target selection

An alias target is rejected if and only if all these tests succeed:

```text
base is STATIC
path starts with ClassStaticAccessor, FieldAccessor
declaring field resolves by owner, name and type
resolved field is static and has ACC_ENUM
```

Reject descendants rooted at that constant too. Failed resolution keeps
the alias. An enum-typed variable, an ordinary static field in an enum,
or a final reference to a mutable object is not sufficient for rejection.

Apply the filter at both new-core alias adapters: statement aliases in
`JIRMethodForms` and call aliases in `JIRCallPlanBuilder`, including injected
alias providers. Apply it in GROUP and FULL forms. Keep the shared alias
analysis and the legacy core unchanged. Keep explicit field reads, direct
assignments, initializer stores and rule positions. Keep a `DIRTY HACK`
comment beside the implementation.

## 3. Field state and owner separation

For an ordinary field, use key `(class accessor, field accessor)`. Groups
use merged `GroupSet` values. The service owns three disjoint sets:

```text
req       = requested and not written
reach     = written and not requested
processed = requested and written

everRequested = req union processed
everWritten   = reach union processed
```

`processed` records group state, not completion of every writer. Each FLOW
writer has a separate admitted group set.

The service stores field state, pending owner/ID references and FLOW writer
references. A method actor stores the complete resume payload: operation
input, original premise, current image/layer and call state. Service effects
are immutable data. They do not contain actor callbacks or cached forms.

## 4. Events and closure

Commit membership changes before sending effects. Send effects outside the
service lock. Merge groups for the same complete continuation.

| Event | State update | Effects |
| --- | --- | --- |
| First request for unwritten g | Add g to req; save pending reference | Ask existing FLOW writers for g |
| Request for g in req | Merge pending reference | No writer rebroadcast |
| First request for g in reach | Move g to processed | Answer immediately and ask existing FLOW writers |
| Request for g in processed | No set change | Answer immediately; retain no answered interest |
| First write of g in req | Move g to processed | Answer and remove all pending references for g |
| First write of unrequested g | Add g to reach | Do not ask existing FLOW writers |
| Repeated group write | No set change | No repeated effects |
| New FLOW writer | Retain its complete selector and empty admitted set | Ask for req union processed |

For every offered writer/group pair, subtract its admitted mask first. Add
fresh groups to that mask before enqueue. Admit each semantic pair once.
A replacement summary has a new selector. An obsolete selector may stutter.

A FLOW writer with an ordinary nonstatic origin uses existing Base requests
and caller bindings. A FLOW writer with a static origin requests that origin
field/group and resumes the selected ordinary summary when answered. Both
routes use the actual summary's AP relation and mark exclusions. Request
routing itself ignores field and mark exclusions. Point repair keeps its
existing complement behavior.

Conditional group-valued singleton/ND summaries announce computed write
presence, then wait for all fixed input groups. Use complete ordinary
matching before publishing an output group. Release a completed pending
join. Do not retain a non-FLOW writer descriptor in the service. Mixed
static/nonstatic premises keep ordinary caller joins. A ZERO-rooted computed
group result publishes directly. No request or unseeded cycle is a source.

This replaces the eager all-static `StaticWriterRegistration` join as well
as the smaller ordinary-origin `StaticFlowWriter` route. It does not remove
ordinary stored summaries or caller re-exports.

## 5. Answers and pending lifetime

For field f and group g, answer with this initial descriptor:

```text
initial: S.C.f / STAR / Group(g), E={}
layer:   DEMAND
```

Use existing `startFact` normalization for the current fact. A marked STAR
initial can produce an ANY current fact; ANY is never a new initial shape.
Apply the saved operation/call matcher at its original stage. Do not restart
the method entry or clear summary exclusions to force a match.

The actor records pending memberships before constructing service deltas.
Its waiting set includes replies in flight. A reply removes only its groups.
Release a row and its key only when its current waiting set is empty. A new
group can join while an earlier reply is queued. IDs are never reused. No
stale service completion flag can delete that new membership.

An exact class-marker request answers only from the corresponding exact
class/group supply. Its answer stays `S.C / EXACT / Group(g)`, in DEMAND.
It uses the same pending/in-flight lifetime rule. Do not canonicalize a
field reply into a class marker or broaden a class marker into `S.C.*`.

Preserve the existing query and cleaner reach:

| Metadata reach at S.C | Existing actual positions selected |
| --- | --- |
| AT | The exact global-mark carrier |
| ANY or AT_AND_BELOW | The exact global-mark carrier and matching fields |
| BELOW | Matching fields; exclude the exact global-mark carrier |

Keep late field matches for a class ANY query. One field answer must not
replace the query with a class-wide fact or hide another field image.
Metadata can select existing field facts without abstracting a class.
Do not expand every declared field. Keep the existing spatial boundary for
other broad patterns. FLOW read admission keeps its existing geometry;
canonical group answers do not broaden it. Existing explicit rule actions
on a class point keep their current semantics.

## 6. Completion and run-2

Keep the method-queue-empty barrier and commit-then-replace temporary batch.
Waiting requests are dormant. They own no runnable reservation and do not
reschedule themselves. Drain admitted replies and ordinary work before the
handoff. Unanswered requests can remain at quiescence; clear their run-scoped
state after workers join.

Keep `DemandStore.includeStaticGroups`, its query-dependent
`implicitStaticPair`, and intermediate nonstatic group demands. Run-2 uses
FULL marks and ordinary static premise matching. A group answer does not
create an executable root summary or enumerate FULL members.

For fixed finite field/group/writer universes, each requested writer/group
pair is admitted once. This is a protocol work bound, not an engine time
bound. For F fields, G groups, W FLOW writers and P pending memberships,
group state uses O(FG), pending state O(P) plus replies in flight, and writer
masks at most O(WG). This does not bound W independently of the program.

## 7. Immutable GROUP identities

Mark keys and group ownership cannot change after publication.

```text
runtimeAtom(GROUP, Group(g)) = the same atom
runtimeAtom(GROUP, Full(g,T)) = interned Group(g)
runtimeAtom(FULL, Group(g))  = the existing error
runtimeAtom(FULL, Full(g,T)) = the same atom
runtimeAtom(mode, ZERO)     = ZERO
```

Read an atom's key once. Map MarkSet lazily and allocate a replacement array
only after the first changed ID. Still intern unchanged public input sets.
Keep FULL runtime set/fact identity behavior.

`withCatalog` validates null/same catalog before its identity branch.
Same-catalog normalized exclusions can return themselves. GROUP projection
can return itself only after all atoms are known not to be FULL. Keep the
old fallback for null, mixed and foreign GROUP catalogs. FULL projection
still validates ownership. Preserve symbolic whole-group exclusion of FULL
members first used later. A finite FULL set is not a whole-group exclusion.

Keep output factory normalization and TAINT leaf traversal. A DEMAND layer
does not prove that its marks are GROUP. No fast path can skip field/tail/E
normalization, layer forcing or ordinary output adapters.

## 8. Required evidence

Constructive Lean models and executable reference tests must cover:

- Exact enum filter selection, with its explicit soundness limit.
- Three-set meanings and disjointness; both request/write orders; requested
  writer closure; no broadcast for an unrequested write.
- Pending reply ownership, including a new group while a reply is in flight.
- Complete fixed-group joins, no source-free cycles, canonical answer cover
  and intermediate run-2 marked-demand coverage within the selected scope.
- Equal mark normalization, validation and interning behavior, including
  delayed FULL use and foreign catalog fallback.
- Eight current initial families; exact class-marker preservation; no
  class abstraction; class/field supply separation; inclusive class ANY
  metadata and strict BELOW metadata kept distinct.

Proofs of finite protocol models are not proofs of the complete JVM engine.
Production gates must check both FLOW origins, complete ND/caller matching,
late broad supplies, class points, selector replacement, cleaners, and the
setter/wrapper/getter/dead-local sink path. Run all required local gates,
then the four fixed project workloads against fresh main results. Record
latest reached, completed and accepted run iterations in each report.
