# Immutable mark groups: implementation validation

Date: 19 October 2026. The selected implementation is complete.
The source commit is recorded in the measurement binding. Project measurements
are the next gate. No new project result is claimed here.

The implementation follows [revision 2.1](../../spec/run1-group-policy-and-ownership-census.md)
and the [implementation proposal](../design/bidirectional-immutable-mark-groups-implementation-20261019.md).
The runtime baseline was `b88c85d5b710d28293d7672af92b085a3838fb19`.

## Scope

Serialized configuration and owner sidecars now declare all mark names before
analysis. The complete provider chain merges these declarations. One finalizer
checks original ownership and uniform primitive tracking. A small interner
assigns stable handles to immutable keys on first use. Encoded group names
let the existing primitive policy test the group directly.

Static demand matching selects one implicit paired demand from the actual
FULL input or entry premise. The global group inventory and pattern map are
gone. The ownership-binding census and its retained name handoff are gone.
Normal prescan, resolved call-graph coverage, runtime provider factories and
ordinary rule caches remain.

These are the selected input-contract changes: an executable mark must be
declared; each group must have one primitive flag; custom providers must
supply complete declarations or an explicit empty declaration.

Field transfer, layer demotion, source/sink admission, condition compilation,
static writer/read/supply routing, base requests, typed exclusions and later
FULL runs retain their specified behavior. Sink end actions stay disabled.
There is no GroundingSupport, new field validator, IR change, reference-manager
rewrite, cache access mode or provider warmup.

## Selected gate

| Population | Tests | Failures / errors / skips |
| --- | ---: | --- |
| Common dataflow, unfiltered | 1,603 | 0 / 0 / 0 |
| JVM dataflow, unfiltered | 434 | 0 / 0 / 0 |
| Five selected application classes | 38 | 0 / 0 / 0 |
| RuleTagsAndJoinTest | 33 | 0 / 0 / 0 |
| Total | 2,108 | 0 / 0 / 0 |

The application classes are JIRBidiSequenceAcceptanceTest,
BidiSignedRuleResolverTest, BidiMarkDeclarationsTest, BidiEntryHookTest and
SpringDispatcherFormsTest. This is not an unfiltered application-suite result.
Tasks ran serially with Java 17 under the three workload locks. Unchanged
common/JVM task outputs were reused in the final gate after the last
application-test correction. Source hashes before and after the final gate
are equal: 3,206 core/rule files.

Receipt and exact commands, XML, logs and source maps:
`/drive-testcomp/opentaint-go-rules/immutable-mark-groups-20261019/validation/full-gate-4/receipt.json`.

New controls cover all nine serialized roles, signed/false branches,
relevance and disabled-action metadata, unused sidecars, original join/import
owners, conflicting declarations, mixed flags and undeclared names. Metadata
setup performs no bound-role callbacks and populates no binding cache.

Interner controls cover concurrent first use, stable FULL/GROUP/zero handles,
exact group projection and supplied GroupSet conversion. For 20 fixed GROUP
Facts checks at declaration sizes 51 and 1,172, the policy makes exactly
20 calls at each size. No full-member or global-group lookup API remains.
JVM primitive, boxed, Character, reference and array filters commute with
owning GROUP projection, including their existing conjunctions and complete
Facts/layers. Arbitrary FULL policy controls remain.

The static controls compare successful results with a finite all-groups
reference. They cover packed groups, late use of declared names, tails,
field/mark exclusions, layers, cross-group results, explicit exits and
FLOW/ZERO. Existing actual-query and ND publication controls pass.

Warm/cold StringConcat producer controls retain conditions, actions and
ordinals. The JVM lowering control keeps the actual callee and FormOwner
fixed and checks AP edges, filters and operation identities when a cached
raw rule names a different signature.

Early gate failures were incomplete test inventories, missing imports,
a generic assertion type and a loader fixture that created no marks.
The additional Spring preservation test used run-1 for a later-run strong
transfer assertion. It now selects the later-run mode explicitly. The
analyzer field kernel was not changed to satisfy that test.

## Proof boundary

The existing constructive UniformMarkGroups model and focused audit remain
unchanged. Their recorded result is 565 owned declarations, zero unexpected
axioms and 20 executable controls. No new Lean execution is claimed.
The current source hashes match the earlier checked receipt; see
`validation/proof-reference.json` in the same evidence directory.

These are local refinement and adapter checks. They do not prove whole-JVM
finding parity, termination, retained memory or the 60-second run-1 target.
The fixed project study must measure those results. Every project report
must name the latest reached, completed and accepted forward run.
