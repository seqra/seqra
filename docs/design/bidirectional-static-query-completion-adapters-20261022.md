# Static query completion: production adapters

Date: 22 October 2026. This note records the implementation correspondence
for [the selected spec](../../spec/run1-static-summary-erasure.md),
sections 3 and 8. It does not extend the selected static grammar.

## Boundary and storage

`DemandStore.staticQueryPolicy` is one enum value. Its default is
`ORDINARY`. The completed run-1 `HandOff` selects
`COMPLETE_RUN1_STATIC_FIELDS` for the incoming run-2 demand. A later
handoff builds a new store with `ORDINARY`. The policy stores no method,
field, mark, or relation inventory.

`StrongQueries` receives this immutable policy from `RunConfig.demand`.
It first applies the existing field bound. For each actual FULL static
leaf, it checks the selected JVM grammar. The normal actual-input and
retained-guide starts remain. An actual `C.f/ANY/T` also supplies
`C.f/EXACT/T` through the existing scalar `emit` operation. An exact
field or exact class marker adds no second identity. FLOW supplies no
FULL mark. Two FULL marks from the same owner remain distinct.

All new identities are collected before the admitted set is changed.
The analyzer starts the returned fresh identities only after admission.
The existing `permits` check still requires every nonzero ND member.
Its ZERO branch only authorizes an already computed result.

## Producer inventory

| Producer or adapter | Current production evidence | Grammar consequence |
| --- | --- | --- |
| JVM static read/write | `MethodFlowFunctionUtils.mkAccess` creates `StaticRefAccess` with a class accessor and a named field. `JIRStatementForms.path` preserves both. | Direct JVM fields have an anchor before a field cut. |
| Local-write aliases | `JIRMethodForms` passes complete alias paths to `JIRStatementForms`. A static alias is appended before the written suffix. | A normal JVM field alias keeps its class and field anchor. The public fixture/custom alias interfaces can supply arbitrary raw paths; these do not establish a JVM profile theorem. |
| Call alias results | `JIRCallPlanBuilder.aliases` copies the complete persisted alias path. | The same rooted-alias assumption applies. No field is selected from an unrooted alias. |
| Source/GEN target | `JIRRuleForms.pos` requires a named field after a static class accessor. `validate.target` rejects an ANY class source target and an any-field conditional source into an exact class marker. | Named field targets remain anchored. Generated global-state targets remain exact class markers. |
| Generated global state | `TaintRuleGenerationCtx.stateVarPosition` is an exact `PositionBase.ClassStatic(...).base()`. Automata state assignment and checking use that position. | The generated global marker has no field descendants. |
| Caller bind-in/back | `JIRCallPlanBuilder.bindIn` and `bindBack` use an S-to-S keep edge. Other ordinary bindings retain the actual full field suffix or use a named static destination. | Static identity binding does not remove the anchor. A first ordinary binding still executes. |
| Field cut | `FieldLimitCut` consumes only counted accessors. A class consumes no budget. With limit 1, it cuts a descendant after the first field. | `C.f.g/T` becomes `C.f/ANY/T`, not `C/ANY/T`. The focused completion test executes this case. |
| Native record | `RecordStore` retains raw NORMAL singleton summaries. `reverseRecordLeaf` uses those existing endpoint paths; it does not invent a field. | Records preserve the producer's endpoint shape. The native record API alone does not prove that arbitrary supplied endpoints satisfy the JVM grammar. Erased run-1 rows do not enter the record owner. |
| Sink seed | `HandOff` preserves FULL sink predicates and occurrences. `RunMethodLocalServices.seedFact` materializes a predicate target and applies the normal seed cut. | A field seed keeps the field anchor. An exact class seed stays exact. The unsupported class-wide case below needs explicit diagnosis. |

`seedFact` validates each bounded output before it calls the execution
callback. This check is necessary because a sink seed starts at its
check statement. It can bypass the caller-input query boundary. Both
boundaries use the same shape checker. A lawful deep field seed is cut
first; its remaining named-field anchor is then checked.

The central `RunMethodAnalyzer.cut` also checks the static FULL anchor
after the existing field bound. `boundedSummaryResult` checks direct
result producers that bypass a new successor cut. These checks apply
only while the incoming run-1-to-run-2 policy is active. They do not
apply in run-1 or persist into run-3.

The central check is geometric. A current field fact keeps its existing
tail, layer and E. In particular, a named field with a MUST tail or a
field-exclusion header is not rejected or normalized by this check.
Only query and seed admission use the strict initial-header check.
No field is guessed, no fact is dropped, and no support label or extra
index is attached to facts.

The production method-start routes close as follows:

- `Start` and `ZeroIn` supply the canonical ZERO initial.
- `LinkIn` calls `StrongQueries.admit`. It bounds actual inputs, checks
  static FULL query shapes, and admits the whole batch before starting
  any fresh initial.
- Concrete `InitialAnswersIn` messages come from `joinRequests`, which
  requires run-1. They do not supply an alternative run-2 initial route.
- A sink starts at its check through the separately checked seed path.
- Entry/exit rule results and statement results pass through `cut`.
  Ordinary call and native-record results pass through the existing
  call cut before they enter method work. Direct summary admission uses
  `boundedSummaryResult`.

An unchanged work item retains its already checked shape. A new bounded
FULL static result must keep a named-field anchor or an exact class
point. Thus a producer cannot install a broad root/class result in the
method work or summary stores through these routes. Unbounded scalar
intermediates inside one existing AP/call operation are not new query
initials. The check does not change or restrict that AP operation.

The AP constructors remain general. A positive field limit cannot prove
that arbitrary AP inputs have the selected shape. The query boundary
does not silently drop, broaden, or pick a field for an unsupported FULL
static input. It reports the input and the required grammar before it
installs any query from that admission batch.

## Unsupported configured class-wide seed

There is a concrete configuration surface outside the selected grammar:

```text
ContainsMarkOnAnyField(ClassStatic(C), T)
    -> JIRRuleForms.literal: S.C/ANY/T
    -> sink seed: S.C/ANY/T
```

`JIRRuleForms.validate` rejects broad class source targets, but does not
reject this sink condition. The selected spec forbids its executable
FULL input. It must not be accepted as a named field or treated as an
exact marker. The seed boundary reports it as unsupported before it emits
executable work. The query boundary also checks actual caller inputs.
This is an explicit profile restriction, not a proof that
all raw rule-provider configurations satisfy the grammar.

An accepted custom conditional source can have the same unsupported
predicate and a local action target. In backward analysis, its source
transfer can produce `S.C/ANY/T` inside a method, without an added link
or a static seed. The central bounded-result check diagnoses this output
before it is enqueued or published. The check also covers ordinary
alias and record results through their existing cuts. Direct summary
result admission uses the same anchor check.

The standard generated global-state path uses an exact class check.
The Spring object unfolding path changes the return-position check,
not the class marker check. Source inspection establishes these two
paths. It does not certify an arbitrary third-party provider.

## Checks

The selected Lean modules built successfully before the runtime patch
(54 jobs). The static-erasure audit completed with exit code 0. These
checks establish the model contracts; they do not compile or execute
the Kotlin adapters.

The shared focused Kotlin run passed 87 tests with no failures. It
includes all three `StaticSeedGrammarTest` controls and the forced
run-4 native-record replay in `Run1StaticDriverSafetyTest`. That replay
now uses the actual run-3 handoff demand. It keeps its explicit sink
seeds and does not reuse the run-1-to-run-2 policy in run-4.

After the central producer check, the complete common-dataflow module
passed 1,771 tests with no failures or skipped tests. The three
`StaticProducerGrammarTest` controls also passed independently in the
focused batch. JVM integration checks and project measurements are
recorded in the parent implementation receipt.

Focused Kotlin controls are in `StrongQueriesTest`, `StaticSeedGrammarTest`,
`StaticProducerGrammarTest` and `HandOffTest`:

- Compare old guide-derived starts with the completed actual starts,
  including different FULL members of one group.
- Keep exact class identity and prevent FULL manufacture from FLOW.
- Keep the named field when the existing field limit cuts a descendant.
- Diagnose unsupported broad FULL inputs before query authorization.
- Diagnose broad static sink seeds before execution. Keep exact markers,
  named fields, and deep field seeds after the normal cut.
- Diagnose a backward conditional source which creates a broad static
  result inside its method. Keep supported source marker/field outputs
  and preserve current MUST/E field headers.
- Admit a complete ND premise only after all its members arrive.
- Attach the constant-size policy only at run-1-to-run-2 handoff.

The parent implementation receipt records the compiled test results and
the latest reached project run iteration. This note contains no new
project performance claim.
