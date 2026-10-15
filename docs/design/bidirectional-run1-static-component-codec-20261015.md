# Run-1 static component implementation clarification

Date: 15 October 2026. Status: implemented; core validation passes. Project measurements are pending.

This document fills the packed AP representation obligations in F78 section 5 and
section 8 of the [implementation proposal](bidirectional-run1-mark-request-implementation-20261015.md).
It adds no request kind, request index, scheduler, IR representation or reference
manager. The [validation report](../reports/bidirectional-run1-mark-request-validation-20261015.md)
records the current checks. No new fixed-project measurement is available. The last
measured ThingsBoard engine reached run-1 and ended with OOM; run-1 did not complete.

A scalar component partition is not a sufficient implementation of a packed tree.
Three cases need explicit code and tests before the static-root guard can land.

## Separate class points from field cylinders

A class point C and the field cylinder C.f are different components. A query or
cleaner at the exact class point owns that point. It does not own all fields of C.
Collect point ownership from effective source and target positions, conditions,
cleaners and generated effects, then use the existing resolved-call closure.
Keep a shared finite class universe as well as the field universe.

```kotlin
class StaticFootprint(
    val fields: Set<StaticField>,
    val points: Set<Class>,
    val wideClasses: Set<Class>,
    val allStatic: Boolean,
    val fieldUniverse: Set<StaticField>,
    val classUniverse: Set<Class>,
)
```

The current draft frame returns every class point. That restores T after a
class-point cleaner removes T. Stop framing owned points. Use their direct class
analyses, just as owned fields use their field analyses.

There is a second case. An incoming broad C component carries T and U. The method
cleans T at C and does not mention U. A direct class start filtered to the metadata
mark T loses U if the owned point has no frame. Select the *class* through structural
metadata, then analyze every actual supplied mark for that selected point:

```kotlin
// Exact supplied point: unchanged direct path, independent of requests.
for (mark in exactClassLeaf.actualMarks) emit(C.exactInitial(mark))

// Broad supplied component: select owned points, not a requested mark mask.
for (cls in pointFootprint.matchingClasses(broadLeaf)) {
    for (mark in broadLeaf.actualMarks) emit(cls.chainInitial(mark))
}
```

This changes the earlier `actualMarks intersect need.marks` snippet for class
components. Normal field answers still require R(slot,T). No class request slot
or abstract class initial is introduced. Nonexact class starts remain STAR with
concrete marks and start as ANY/DEMAND. No NORMAL exact assumption is invented.

Required controls: clean exact point T and preserve U at an owned point; an unowned point uses
only the return-gated frame; an owned point uses no root frame; an exact class
query does not select unrelated field cylinders; both direct class branches are
independent of normal-slot mark requests.

This does not promise exact cleaning of every broad class candidate. A concrete
STAR class initial starts as DEMAND ANY. Cleaning its exact point is an ordinary
PART operation: the current AP cleaner can retain a DEMAND ANY residual for T.
That conservative residual is separate from an invalid raw root-frame return.
Do not change AP cleaner semantics to satisfy a stronger precision assertion.
The actual-driver broad-input control requires U to survive, identifies T's real
chain/PART residual as DEMAND, and requires that the owned point receives no root
frame. The valid exact class-source control requires T to disappear and U to
survive. The initial broad no-T assertion failed in the recorded common test gate;
its counterexample is preserved rather than reported as a passed property.

## Preserve each exclusion header

A packed input can contain a broad ancestor and an explicit field branch. Its E
has a different position after each projection. For example, NORMAL root
`[any-taint](T)` with E that admits C supplies C.f after the C prefix is consumed.
An explicit NORMAL `C.f.[any-taint](U)`
keeps E at its field tip. These pieces cannot in general share one E header.

```kotlin
fun prepareStaticSummaryInputs(input: Facts, initial: Pattern): List<Facts> {
    // Direct field subtree: keep its original E.
    // Proper broad ancestor: test E before consuming its prefix; use empty E
    // for the resulting field cylinder.
    // Keep different headers in separate transient Facts.
}
```

Apply the ordinary complete matcher to each piece. Use this path for summary
replay, summary notification, native records and reversed views. Candidate lookup
must include a broad ancestor that can supply an owned field. Keep the original
CallerRef and link/caller layers. No stored caller-input group is rewritten.

For NORMAL input, retain a compact frame where E can express the complement:
root with owned classes excluded, partial class with owned fields excluded, and
explicit unowned branches under their original E. If the point itself is owned,
return only its unowned field projections. Do not scan the global field universe
for an ordinary explicit unowned branch. For DEMAND TAINT, use finite required
field projections and exact DEMAND class-point views where E cannot express the
residual. Keep leaf mark sets packed.

Required controls compare decoded valid locations, marks and layers with a scalar
partition. Include mixed ancestor/tip E, multiple classes, owned points, an empty
footprint, a whole-static footprint, and both summary arrival orders. A reached
normal exit is required before any frame is emitted. A pending owned result does
not permit an identity fallback.

## Pin the selected JVM FLOW boundary

A class-point-only DEMAND FLOW cannot be encoded in the current AP grammar:
DEMAND FLOW has no field E. `C.[any]` includes fields and is not a point-only view.
Do not silently widen it or drop it. The selected JVM frontend must establish the
stronger boundary needed by this codec during run 1:

```text
A FLOW leaf at S root or at C:
    layer = NORMAL
    actual original premise = special abstract S-root initial.
```

The source argument must cover all constructors and transfers, not only the input
validator. Public static rule positions start with a class accessor. Pass rules
reject class endpoints, and cannot name empty S. JVM static code reads and writes
use C.f; the run-1 limit cuts below C.f because the class accessor is uncounted.
The static-root seed starts NORMAL. Its root rules are skipped. Ordinary keep,
kill, cleaner, call binding, field transfer, filter and summary routes must preserve
this boundary. Sources generate TAINT, not FLOW. Native models must use those same
validated effective forms.

Validate the proved boundary before emission. A legal generic AP operation that
constructs a counterexample remains legal as an AP operation, but does not certify
membership in this selected JVM frontend. No new emitter cut repairs an invalid
input. Add a negative adapter test for DEMAND broad FLOW and for a broad FLOW under
a non-static origin. Keep positive selected JVM code/rule/native tests. Do not
claim the implementation complete until the route audit and these tests pass.

Finally, applying an excluded abstract summary target can create an interest on
the caller origin. Deliver that creator side effect before the excluded output,
through the same R/P path. Ordinary field E, mark transfer, layers and rule checks
remain in force. The request join alone ignores E and FLOW mark exclusions.

## Validate the static field prefix before collecting a plan

The bound rule API previously accepted this position:

```kotlin
PositionWithAccess(ClassStatic("C"), PositionAccessor.ElementAccessor)
```

It is not a normal field slot. Its AP path is `[C, Element]`. A source action at
this position passed the old `JIRRuleForms.valid` checks, then failed the static
profile's named-field layout check. `JIRRuleForms.pos` now rejects a non-field
second static accessor before form or catalog construction. The valid position
`C.f.Element` is retained: f is a real static field, and the later element lies
below that field. Ordinary field-limit cuts still handle its depth.

The current YAML `PositionBase.deserialize` does not accept `class(...)` text.
The built-in Java rule compiler creates only a bare artificial class point for
its global state. Spring also creates bare class positions. JVM static code and
alias paths add a named field after the class. Thus the invalid bound example is
an API validation gap; this audit found no current built-in generator of it.
The resolver can fold an array modifier onto a programmatically supplied class
base, so this source check is needed even without a YAML spelling.

`JIRRuleValidationTest` checks rejection in source, pass, cleaner and sink roles,
including wrappers around the invalid position, while it accepts those roles at
`C.f.Element`. It does not reject valid incoming ANY or must facts.

The selected forward JVM generators do not currently create broad class TAINT:
class source/end targets with AnyField are rejected, passes reject class
endpoints, code/aliases use C.f, and positive cuts cannot stop above C.f. Exact
class sources and GEN actions stay exact or are removed by class operations.
This is not a ban on broader facts in restricted runs. For example, the valid
conditional source `ContainsMarkOnAnyField(C,T) -> AssignMark(U,Result)` reverses
to `Result.$(U) -> C.[any](T)` in B2. A class-ANY sink seed can also introduce a
DEMAND class TAINT fact. These concrete TAINT routes do not contradict the FLOW
invariant below. Their broad reader codec remains required.

## Selected driver reads after run 1

The later-reader claim is stronger than NORMAL root/class FLOW: **the selected
restricted runs have no STATIC root/class FLOW leaf, in either layer**. This
claim concerns stored/delivered facts and final summary results. A transient
root wrapper inside summary application is grafted before it can be emitted.

The source argument is an induction over operations and completed handoffs:

1. The run-1 boundary above permits root/class FLOW only as NORMAL under the
   special root origin. Code static transfers use C.f. Public passes cannot
   target C/root. Sources, GEN and joint premises produce concrete TAINT, not
   FLOW. Cuts cannot stop above C.f. Normal field summaries and return-gated
   frames preserve those facts. Selected FLOW demand output is therefore never
   at C/root: `selectDemand` does not select a NORMAL abstract singleton.
2. `HandOff.toBackward` uses the selected output as the next demand **entry**.
   The original premise may appear as its exit restriction, but that exit does
   not start an initial. `Reference.emit` uses `flowForm` for an abstract demand;
   `flowForm` changes ANY to STAR and keeps the entry path. It does not widen a
   field entry to C/root. Source/sink seeds have concrete marks; direct root and
   backward zero entries start Reach.
3. Starting without broad STATIC FLOW, keeps retain an explicit field suffix.
   Reversing code swaps reads/writes but retains the C.f static endpoint.
   Reversed passes still have no C/root endpoint. Filters, cleaners, positive
   field cuts and joins cannot lift a field to C/root. `Reference.restrict`
   returns the old conclusion path q, or a demanded path p only when p extends
   q. It never lifts a field path to its parent. A final summary graft keeps the
   static conclusion prefix. The frame codec emits no root/class flag when the
   input has none.
4. Persistence accepts only NORMAL native singleton records and preserves kind.
   A restricted abstract record therefore has a nonstatic or field/deeper
   premise. Its reversed result targets that original premise. The only earlier
   broad abstract record is the run-1 special S-root record. Both by-entry and
   by-exit reads intercept that original record **before** `reversedAt`. They
   require an actual NORMAL STATIC root control conclusion, then return only
   the current input's footprint complement. They do not apply raw coarse root
   output. A root-origin DEMAND field conclusion is not return evidence.
5. `HandOff.toForward` and every later handoff use the same selected-output path
   rule. The preceding argument therefore supplies the induction step. Stable
   footprints survive release of the run-1 request catalog for these reads.

Do not replace this argument with a generic primitive layer claim. In restricted
mode, applying `keep(C.*, E{f})` to a NORMAL S-root FLOW can produce a DEMAND C
parent result: the old static parent suppression is run-1-only. The selected
handoff/native argument excludes that incoming root FLOW. The generic operation
remains legal, and its counterexample is a focused test.

`Run1StaticDriverSafetyTest` uses real `IterationDriver`/`RunManager` runs. Its
controls require: cleaner T removes T while U survives; a strong write of f
preserves g despite the raw root's class exclusion; a nonreturning callee emits
no frame; actual B2 opposite and F3 native root-record applications resume g
after catalog release with no nonzero callee demand. The latter also checks that
restricted processed FLOW has no C/root leaf in that program.

This is a source-level proof for the fresh paired JVM adapter and its validated
effective forms. It is not a formal JVM/Lean refinement theorem or certification
of arbitrary custom `RunConfig` inputs, injected ANY abstract initials, foreign
preloaded class/root records, custom alias paths or partial generated class
cleaners. Those generic APIs have a wider domain. The focused tests and complete
suites remain the implementation validation gates.
