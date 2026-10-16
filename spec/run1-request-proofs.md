# Run-1 request proof dossier

This document proves the semantic laws used by the selected
[run-1 request design](../docs/design/bidirectional-run1-mark-request-redesign-20261015.md).
The normative construction rules are in [run1-requests.md](run1-requests.md).
The measured implementation is `4c64c579f4c4a10fc8ffcc5bcd0bf1bcd45cf877`. The
[implementation validation](../docs/reports/bidirectional-run1-mark-request-validation-20261015.md)
records its core checks. The [fresh project report](../docs/reports/bidirectional-run1-mark-request-performance-20261015.md)
shows OWASP reaching run-5, completing run-4 and accepting run-3. Conductor,
ThingsBoard and Stirling-PDF fail in run-1 with no complete/accepted iteration.
Their static-class guard failures falsify that implementation's frontend domain
bridge. Section 1.3 now gives the selected repaired constructor argument and the
archived real-producer and symbolic-inventory regression evidence. The focused
121-test and full 408-test JVM gates pass; repaired project validation remains
pending. The finite Lean laws do not prove the JVM
collector or extend a receipt through these documentation changes.

The selected [domain/frozen-base implementation](../docs/design/bidirectional-static-domain-and-frozen-requests-20261016.md)
adds §1.3's source-level frontend bridge and §4.2's representation laws. The latter are
finite set arguments in this dossier, not new checked Lean statements or a JVM
layout proof. The source repair's regression scope is stated separately; no
successful repaired project result or new Lean receipt is claimed here.

The proofs below concern a finite, accepted program and its request/initial and
component-summary semantics. They do not prove a JVM implementation, a physical
set layout, a 60-second bound, or exact confirmation of pooled ND candidates.
Request membership is not evidence that a source or sink predicate holds. The
existing occurrence, proof, premise, layer and report rules still apply.
Pooled ND candidates and any zero-GEN end actions keep their separately specified
candidate policy. The component/request laws do not certify those effects as a
same-call concrete trace and do not repair the measured precision regressions.

## 1. Definitions and explicit assumptions

Let `Method` be the finite set of methods in the complete resolved prescan graph.
Let `Key` be a full runtime MethodKey, including its method, entry and context.
No theorem below replaces a full Key by a method name. Effective forms and call
bindings are fixed for each Key during a run. Their call targets belong to the
prescan graph. Context-sensitive plans may be conservatively joined in prescan;
the runtime retains its actual contexts and full caller premises.

`Sigma` consists of finite class coordinates Sigma.classes and paired static
field coordinates Sigma.fields from the accepted IR, effective rules, native
models and canonical accessors. Each `(C,f)` requires C in Sigma.classes, but a
class point need not have any field. This is not a claim about fields that
arbitrary Java reflection could create. Every accepted static input coordinate
must belong to Sigma by the frontend construction proof in §1.3. The measured
guard failures show that this bridge is not established for `4c64c579f`.

The normal abstract initials have exactly these shapes:

```text
B(b)    = (nonStaticBase b, root, STAR, empty E, abstract mark)
F(C,f)  = (STATIC, [C,f], STAR, empty E, abstract mark)
```

The special abstract STATIC root is a summary seed, not a request slot. It does
ordinary identity/keep transfer but no rule evaluation. Its concrete summary
read is governed by the component composition in section 6, not its raw root
output. An exact concrete empty-root STATIC fact is outside the accepted input
grammar. Root/class ANY facts and exact concrete class-point facts remain legal.

There are two instances of the same RequestStorage class per Key. `R` is the
authoritative cumulative relation in SummaryStorage. `W` is the actor-owned
write-only temporary relation. Both map a normal slot to a finite set of concrete
marks. R borrows the method/SCC plan's frozen precomputed base `B` by reference
and owns only a disjoint dynamic residual `D`; its effective value is `B union D`.
W uses the same class with an empty base. It contains only pending contributions,
not a copy of B. Producers subtract current effective R before unioning into W.
At a method-queue-empty barrier, commit reads W
synchronously, installs fresh memberships in R, reserves queued notifications,
and returns. The actor then replaces W with an empty instance. No callback may
reenter the actor during this operation. R remains active after an answer.

The proof uses these external construction assumptions, which are not request
coverage hypotheses. The frontend-to-enumerated-producer bridge in §1.3 remains
a separate implementation obligation:

1. Prescan includes every effective form and resolved target used by the run.
2. The closed shape construction below, first field limit exactly 1, validated
   JVM path layout, and the impossible exact STATIC empty-root input case hold.
3. Each AP primitive uses the stated scalar reference semantics. Ordinary
   summary matching retains its original field E and full premise checks.
4. Incoming call input admission precedes callee input delivery. Request commit
   precedes its notification. All resulting deliveries are counted work.
5. For eventual closure, the accepted run has a finite reachable abstract state
   and fairly services its existing work. A cancelled or timed-out run need not
   reach that closure. There is no wall-time or bounded-delay theorem.

The following sections construct coverage from the finite forms, graph and
primitive cases. They do not assume a predicate called “the plan covers all needs”.

### 1.1 All legal F78 run-1 initial shapes

Write an initial as `(base, path, tail, E, mark)`. Here b is a non-static,
nonzero, non-temporary base. T is any frontend-admitted concrete mark, **including
numeric mark ZERO where that frontend admits it**. Do not add a T≠ZERO condition.
Only the Zero **base** has a unique prescribed mark and shape.
`ApManager.premiseOf` removes canonical Zero-base members when forming a joint
premise; it does not remove a nonzero-base member merely because its mark ID is 0.

| Family | Base/path | Tail | Mark | Initial role |
| --- | --- | --- | --- | --- |
| Canonical zero | Zero/root | EXACT | ZERO | Root/callee zero start |
| Normal non-static abstract | b/root | STAR | abstract STAR | Normal BaseSlot analysis |
| Special static abstract root | S/root | STAR | abstract STAR | Rule-free summary/control seed |
| Normal static-field abstract | S/[C,f] | STAR | abstract STAR | Normal StaticSlot analysis |
| Non-static concrete point | b/root | EXACT | T | Requested supplied exact root |
| Non-static concrete chain | b/root | STAR | T | Requested supplied non-root/broad shape |
| Static-field concrete point | S/[C,f] | EXACT | T | Supplied exact field |
| Static-field concrete chain | S/[C,f] | STAR | T | Supplied field ANY/must or broad ancestor projected to field |
| Class-point concrete point | S/[C] | EXACT | T | Direct supplied exact class point |
| Class-point concrete chain | S/[C] | STAR | T | Nonexact same-class supplier or broad root projected to class |

E is empty in **every** row. Abstract marks have no mark exclusions. There is
no ANY or ANY_TAINT initial of any mark, abstract class initial, non-static
field initial, concrete empty-S-root initial, or cleaner-temporary initial. A
PremiseSet is a summary/rule support header, not an additional initial shape.
Exact and chain alternatives for the same mark remain distinct.
Legal shape is a necessary condition, not an emission command. A requested
concrete initial additionally needs admitted membership and an actual matching
concrete supplier carrying T. Class projections need their actual direct or
metadata-selected supplier. A request-only actor does not start every legal
initial, invent a supplier, or add a synthetic zero.

This is the complete selected emitter grammar, not a claim that every generic
AP constructor enforces it. Broad concrete root/class ANY inputs
remain legal even though no concrete empty-S-root **initial** is emitted. The
separate impossible-input condition excludes only exact empty-S-root taint.

**Closed-initial theorem.** The selected production initial-producing routes emit
only these rows, and no legal route needs an extra abstract/request shape.

**Proof.** Root Start and incoming Reach choose canonical zero. A nonzero policy
start chooses the base-root STAR abstract row; on S this is the special summary
seed. The structural field emitter chooses only `[C,f]/STAR/abstract`. The
request projector's non-static cases choose exact root or STAR root. Its static
cases choose the field EXACT or STAR rows. The class direct projector chooses
class EXACT or STAR, with nonexact choices starting as demand. All these
constructors explicitly use empty E. A fresh and a late answer use the same
projector, so asynchronous delivery adds no family. Source/GEN and native model
outputs are ordinary statement facts, not a separate direct initial route. A
later call input goes through this same emitter. Restricted demand emission and
native/opposite record reads do not supply run-1 initials. This enumerates the
production route grammar; section 1.2 states its actual JVM boundary conditions.

### 1.2 Source audit and the bounded-input bridge

The source audit distinguishes generic AP constructors from selected admission:

- [Premise](../core/opentaint-dataflow-core/opentaint-dataflow/src/main/kotlin/org/opentaint/dataflow/bidi/ap/Premise.kt)
  enforces canonical Zero base, no temporary base, no excluded premise mark, and
  exact/must concrete marks. It permits other generic shapes. It does **not**
  forbid ZERO on nonzero bases or establish the F78 closed list.
- [AccessorTable](../core/opentaint-dataflow-core/opentaint-dataflow/src/main/kotlin/org/opentaint/dataflow/bidi/ap/AccessorTable.kt)
  reserves `<zero>` at ID 0; `mark("<zero>")` returns that ID. This is not a global
  ban on that admitted mark elsewhere. Field and element accessors are counted;
  class accessors are not. Arbitrary generic path factories do not prove the
  selected JVM layout with a single leading class accessor.
- [Demand](../core/opentaint-dataflow-core/opentaint-dataflow/src/main/kotlin/org/opentaint/dataflow/bidi/ap/Demand.kt)
  `policyInitial` chooses zero or abstract base root. `initialFacts` makes exact
  concrete starts NORMAL, concrete STAR/ANY starts DEMAND, and zero Reach.NORMAL.
  The supplier's real layer is still used by ordinary summary application.
- At historical revision `27e22bb1e`, `Run1Abstraction`, `StaticSupplierCursor`
  and `RequestGroupCursor` could emit abstract class, concrete root and static
  concrete ANY initials. F78 removes those routes rather than claiming they
  never existed. The new [projectors](../core/opentaint-dataflow-core/opentaint-dataflow/src/main/kotlin/org/opentaint/dataflow/bidi/engine/Run1RequestMatching.kt)
  construct only the selected rows.
- [RunMethodAnalyzer](../core/opentaint-dataflow-core/opentaint-dataflow/src/main/kotlin/org/opentaint/dataflow/bidi/engine/RunMethodAnalyzer.kt)
  `addInitial` now validates the full run-1 inventory, including the no-ANY rule.
  `addLink` and the emitter do not perform a fresh field cut. Incoming facts must
  already satisfy the selected bound, and InitialAnswersIn carries validated
  projector outputs.
- [IterationDriver](../core/opentaint-dataflow-core/opentaint-dataflow/src/main/kotlin/org/opentaint/dataflow/bidi/driver/IterationDriver.kt)
  starts run 1 with no demand and `SeedIndex.EMPTY`.
  [IterationPolicy](../core/opentaint-dataflow-core/opentaint-dataflow/src/main/kotlin/org/opentaint/dataflow/bidi/driver/IterationPolicy.kt)
  starts the default increasing policy at limit 1. The driver now requires that
  exact first limit even for custom policies. Generic RunConfig still requires
  only a positive limit; that weaker API is not an F78 proof.

For the selected JVM workflow the **first limit is exactly 1**, and incoming
facts satisfy the validated JVM accessor grammar: a static class prefix occurs
only at the front, followed first by a static field and then any ordinary
field/element suffix; non-static paths contain counted accessors. An element
directly under a class is not a substitute static-field slot. A one-counted-accessor bound then permits only root,
class point and class/field static positions. A class accessor consumes no budget,
so a cut retains C before the first field. Positivity alone would not give this
bound, and a manual path containing repeated class accessors would violate the
layout premise.

The bound is established **before emission**, not by adding a new emitter cut:

1. Statement and method entry/exit RuleWorklist results use the existing cut.
2. In [RunMethodCallServices](../core/opentaint-dataflow-core/opentaint-dataflow/src/main/kotlin/org/opentaint/dataflow/bidi/engine/RunMethodCallServices.kt),
   F74 field read and write steps each use `cut(Cut.STATEMENT)`. Root cleaners and
   strong keeps do not grow an admitted input path.
3. [JIRCallPlanBuilder](../core/opentaint-dataflow-core/opentaint-jvm-dataflow/src/main/kotlin/org/opentaint/dataflow/jvm/bidi/interp/JIRCallPlanBuilder.kt)
   BIND_IN uses base-root bindings and S identity, so it does not grow the count.
   Source/end-GEN stages bypass CALLEES and reach the existing call-exit cut;
   returned rewriters also precede that cut. They are not an uncut new callee-input
   constructor. Native unresolved transfer returns through that same call path.
4. The accepted LinkIn boundary validates the resulting bounded layout. Direct
   manual inputs, custom deeper first limits and arbitrary custom growing call
   bindings are not silently certified by this route induction.

**Bounded-route proof.** Canonical zero starts have count 0. Each accepted initial
has count at most 1. Every path-growing ordinary statement/lowered field step is
cut before the next admitted input route; unchanged paths and BIND_IN preserve
the bound. Induct on that instruction/call route. The selected class-prefix
grammar plus count≤1 gives the three static input shapes. It does not bound the
number of sibling fields or concrete marks. The concrete empty-S-root input
assumption and finite field-universe completeness remain separate frontend checks.

Entry/global sources, sink end-GEN, trigger seeds and native actions can still
create ordinary facts. Do not confuse them with new initial constructors. The
production run-1 driver has no hand-off source/sink seeds; later restricted runs
use a different initial policy. A manual RunConfig that inserts such seeds is not
evidence that the accepted first-run constructor list has been proved for it.

### 1.3 Selected-JVM producer-domain construction

Distinguish Sigma from semantic ownership H[m] and frozen field selection S[m].
H contains transitive read/write/cleaner scopes. S contains resolved structural
and field-interest slots used to build B. A legal unowned coordinate in Sigma
adds no H/S member, request or initial. A wide operation can own a newly relevant
component, but that follows from its semantic scope, not domain membership.

The finite basis contains paired class/first-field symbols from static accesses
in the completed resolved method graph and from validated effective rule/model
targets and condition/cleaner scopes. Class points contribute a class even when
they have no field. Metadata retains neutral values; compilation remaps them
through the runtime AP manager. There is no scratch numeric-ID copy, arbitrary
classpath enumeration or Cartesian product of classes and fields. The complete
resolved graph and validated selected-JVM producer grammar are the scope of the
argument below. Additional frontend producers require their own constructor cases.

#### Declaring-owner equality and the observed mismatch

Direct access and AA access must use the resolved field's declaring class.
[MethodFlowFunctionUtils.mkFieldAccess](../core/opentaint-dataflow-core/opentaint-jvm-dataflow/src/main/kotlin/org/opentaint/dataflow/jvm/ap/ifds/MethodFlowFunctionUtils.kt)
constructs both ClassStaticAccessor and FieldAccessor from the resolved field.
The repaired
[JIRDSUAABuilder.evalRefValue](../core/opentaint-dataflow-core/opentaint-jvm-dataflow/src/main/kotlin/org/opentaint/dataflow/jvm/ap/ifds/alias/JIRDSUAABuilder.kt)
now uses `value.field.field.enclosingClass.name` for its static container instead
of the typed reference's `enclosingType`. `DSUAliasAnalysis.createFieldAlias`
already uses the same resolved field for the field label. Conversion and
`apAccessor` preserve those descriptors. Thus direct metadata and AA agree on
the pair even for inherited fields and dotted versus binary nested-class names.

This repairs a concrete constructor mismatch. The original `4c64c579f` failures
did not print the offending class. The later fixed-config PDF diagnostic did:
class `stirling.software.common.util.ProcessExecutor.Processes`, first field
`stirling.software.common.util.ProcessExecutor$Processes#WEASYPRINT`. The delivery
was DEMAND FLOW to `runCommandWithOutputHandling(List)`, entry 2 in the ArrayList
argument context, from `FileToPdf.convertHtmlToPdf` call 48, with the special
abstract S-root premise. The full key is in new evidence root J (defined below),
`diagnostic-pdf/projects/Stirling-PDF/bidi/analyzer.log:750`. This names the
observed mismatch, not its globally earliest producer or the other projects'
unprinted inputs. The real producer fixtures reproduce the constructor defect;
a successful repaired project result remains a separate measurement.

#### Static-alias first-pair lemma

An exposed static alias of a selected JVM local already has a named first field.
The synthetic `RefValue.Static(C)` container alone is not a local value.

**Proof.** `JIRDSUAABuilder.evalRefValue` creates a static container only inside
`Expr.FieldLoad(Static(resolvedOwner), resolvedField)`. In
[DSUAliasAnalysis](../core/opentaint-dataflow-core/opentaint-jvm-dataflow/src/main/kotlin/org/opentaint/dataflow/jvm/ap/ifds/alias/DSUAliasAnalysis.kt),
`evalFieldLoad` calls `evalHeapLoad`, which returns a HeapAlias with that field
appended. Immediate/copy/call values are locals, arguments, this or constants;
they do not name the synthetic static container. `WriteStatic` does not merge
it into a local. External AA call-model positions explicitly reject ClassStatic
in [JIRLocalAliasAnalysis.CallModelProvider](../core/opentaint-dataflow-core/opentaint-jvm-dataflow/src/main/kotlin/org/opentaint/dataflow/jvm/ap/ifds/JIRLocalAliasAnalysis.kt).
Converting the HeapAlias therefore appends Field(C,f) to Static(C) before any
further suffix. The class-only result of `convertBaseAccessor` is an intermediate
heap-parent conversion, not an exposed local-value constructor. Local copies,
DSU value unions and call-model remaps preserve these whole value aliases; they
do not strip the first field and return the synthetic container as a value.

A statement alias write can append g or an element after C.f, but cannot invent
a first field by appending to bare C. The first pair came from an actual static
JIR field reference in the resolved body basis. A relevant resolved callee body
is included in the complete graph basis; a custom AA call-body universe outside
that graph needs an explicit domain extension. The before/after intersection in
[aliasesPersistedThroughCall](../core/opentaint-dataflow-core/opentaint-jvm-dataflow/src/main/kotlin/org/opentaint/dataflow/jvm/ap/ifds/analysis/JIRAliasUtil.kt)
selects whole paths. Statement alias writes and `JIRCallPlanBuilder.aliases`
retain those paths as prefixes, so a residual suffix cannot change their first
pair. No prescan AA run, alias-payload copy or class-by-field expansion is needed
for this source-level prefix argument.

#### Validated rule and native/model target lemma

Copying an arbitrary residual beneath a bare class is a generic AP operation,
but is not a selected JVM pass-rule producer.

**Proof.** [JIRRuleForms.validate](../core/opentaint-dataflow-core/opentaint-jvm-dataflow/src/main/kotlin/org/opentaint/dataflow/jvm/bidi/interp/JIRRuleForms.kt)
rejects CopyAllMarks and CopyMark when either endpoint is a bare class position
(the pass branch near lines 298–307). `pass` checks this again before constructing
an edge. A static field target already fixes [C,f]; a copied residual follows f.
A non-static target creates no static prefix. Default static identity preserves
an admitted prefix. Thus the generic `CopyAllMarks(Arg0, ClassStatic(C))`
counterexample is rejected by this frontend. It does not justify a domain
widening, nor make that generic AP constructor universally invalid.

A class-point source or sink end-GEN is a different case. `markTarget` uses
`sourceFact`: a validated class target is EXACT. A zero/end GEN has no input
residual to append. Conditional SourceActions also use their declared target;
validation rejects the AnyField-premise/class-target combination. These produce
a class point, not an arbitrary field under that class. A field source target
fixes its first pair. Cleaners remove or annotate existing payloads, and a strong
write's static target comes from its declared position.

The same restrictions cover generated/default/native forms. Entry and exit rules
pass through `JIRBoundaryForms` validation. Call sources, sinks and cleaners use
these same `JIRRuleForms`. `visitStaticMetadata` visits their summaries, sinks,
bound cleaners, returned rewriters and pass rules. Generated native CallCleaners
are not raw rules: their lowered CleanSteps are visited directly, so the collector
reads their explicit affected scope. Both configured pass rules and
`defaultGetModel.defaultPropagationRules` are filtered by `forms.valid` before
pass compilation. Unresolved/native calls use those stages, not an independent
unvalidated static generator. Static-field read sources are validated and generate
at the local read result. Arbitrary hand-built AP edges remain outside this
selected-JVM producer argument.

#### Metadata predicate and ownership argument

The code under development has explicit `MetadataAliases.Disabled` and `Unknown`.
`JIRBidiAnalysis.gather` selects the policy from the actual runtime AA setting for
both retained contexts and graph-only fallback methods. Scratch forms disable AA
construction. Enabled runtime AA uses Unknown even when an earlier prescan AA
completed: there is no Known state without an outcome-coverage certificate. Thus
null, uncomputed, failed or independently timed enabled-AA states cannot silently
select Disabled. No cancelled prescan owner is borrowed or restarted.

Unknown requires two metadata views. A potential-effect view may replace an
unknown basic atom U by possible/true to collect a possible action scope. That
view cannot also supply the mark inventory: `U OR P` then becomes true and loses
the P interest needed when runtime U is false. `NOT U OR P` has the same problem.
The earlier 119-test focused and 406-test full JVM checkpoints passed without
detecting this gap. They remain evidence for their checked sources, not a proof
of complete metadata inventory.

The repair uses a separate signed symbolic-needs view in
`JIRRuleForms.rewriteSourceOrSink`. It is built only when the metadata CALL
callback is nonnull. The existing static rewrite still computes each leaf's
runtime/effect result. The parallel view uses that signed result for known basic
atoms, retains mark atoms and retains both signs of an Unknown basic atom. It
does not query providers again, run AA, or replace the returned runtime predicate.
Its construction is:

```text
C0 = normalizeSigned(validated raw condition)
C1 = retain signed Unknown/mark atoms; fold other atoms with their computed result
C2 = normalizeSigned(C1)
C3 = replace only negative MARK atoms of C2 by true
C4 = normalizeSigned(C3)
metadata needs = positive mark atoms remaining in C4.
```

The first normalization establishes signed falsity before approximation. Do not
use a global negative-literal projection on C1: it would replace NOT U by true
and lose P again. The positive inventory callback currently serves only metadata
CALL source/sink sites using the default `projectNegatives=true` policy. This
argument does not certify a future callback under a different projection policy.

**Live-interest lemma.** For each possible runtime valuation of the retained
Unknown atoms, any positive mark on which that call's normalized source/sink
predicate can depend is included in the symbolic metadata inventory.

**Proof.** Known atoms use the same signed constant result as the ordinary
rewrite. Unknown atoms remain variables with their original sign, so any actual
valuation can be substituted into C1. Signed normalization preserves this
condition and cannot remove a genuinely dependent mark. Projection replaces
negative mark atoms only, as the current CALL policy does; it does not assign
Unknown variables. If a positive mark P were absent from C4, this projected
condition would be independent of P for every valuation of its remaining
variables. Substituting any actual Unknown valuation cannot introduce a new P
dependence, contradicting a live runtime P interest. The callback visits every
remaining positive mark with its existing position/mark lowering. Consequently
its path contributes Sigma, H and the normal-slot need just as an ordinary live
predicate leaf does. This is an inventory argument, not a predicate proof or a
conjunctive combination of different runtime valuations.

The concrete cases expose the required distinctions:

| Condition/view | Symbolic mark inventory |
| --- | --- |
| U OR P; NOT U OR P | P retained for the runtime branch that needs it |
| Known true OR P; known false AND P | No P: the predicate cannot depend on it |
| U AND NOT U AND P; P AND NOT P | No P: signed falsity is proved before approximation |
| Negative mark atom alone | No positive request introduced by the CALL negative-mark approximation |
| Empty-action call source | No symbolic source callback: no source action can consume a mark proof |
| Call sink with no end facts | Keep its symbolic sink needs: the finding still depends on its predicate |

Callbacks are absent from ordinary runtime compilation, ENTRY, EXIT and returned
rewriters. Entry-source, call-source/sink, returned-rewriter and cleaner
eligibility keep their distinct effective-form rules. Potential metadata scopes
and needs do not make a runtime rule unconditional or change occurrence/action
identity. The new source is covered by the focused and full JVM receipts below.
Fixed-project validation remains pending at this writing.
Entry positions resolve to arguments/this rather than locals, and the basic
evaluator consults AA only for locals. There is therefore no entry Unknown-local
witness to invent. The entry control keeps its own signed-isTrue rule; the
Unknown-local potential/runtime-conditionality witness belongs to a call site.

The inventory also establishes local H: direct static reads/writes name the field;
alias effects remain below the originating static first field; validated field
actions/cleaners name the affected field; root/class effects carry their wide
scope. The collector includes strong-write exclusions, generic excluded-target
needs and F74 original static read scopes. Resolved-call closure unions these
local effects into transitive H. Global Sigma can therefore contain a supplied
field outside callee H without making it owned or selected. An omitted actual
operation requires its H effects to be repaired as well as its coordinates before
the untouched-frame theorem applies; domain insertion alone does not prove safety.

#### Delivered-coordinate theorem

Every explicit static class/first-field delivered by the selected repaired JVM
constructors belongs to the finite collected basis.

**Proof.** Induct on a finite fact-production derivation. Zero and special-root
starts have no explicit class. Direct static access uses its collected resolved
pair. The alias lemma reduces statement and persisted-call outputs to the same
pair with possible suffixes. The rule lemma reduces generation to a collected
point or fixed field target, and copying to a fixed target or admitted identity.
The metadata policy includes each possible effective target without altering its
runtime gate. S binding and identity preserve an admitted pair. Non-static
binding changes a base, not a static coordinate; any later static target is one
of the fixed-target or alias cases above. Suffix cuts shorten paths without
inventing a different first pair. Initial emission uses actual supplied points
or selected fields; a request alone supplies no path or concrete mark. Summary
and record composition preserve admitted prefixes or apply the already covered
target constructors. Union contributes no coordinate absent from its parts.
These local cases compose through the complete resolved call graph. Each produced
fact has a finite derivation, so recursion needs no acyclic-call premise. Neutral
remapping preserves symbol pairs and their manager-local membership.

This is a source-level constructor argument for the stated frontend and repair.
The finite Lean receipts below do not machine-check JVM code, graph/metadata
extraction, the new AA policy or the packed codec. Keep domain/layout guards.
Dropping inputs, inserting runtime domain members, adding an emitter cut or using
an identity fallback is not the repair proved here.

#### Executable witnesses and validation scope

Let J be
`/drive-testcomp/opentaint-go-rules/bidi-o1-o3-20261012/static-domain-frozen-requests/20261016T063718.860848Z`.
The source inventory is `J/preparation/static-domain-source-review/inventory.md`.
`J/validation/producer-after/receipt.json` pins the repaired AA builder, the then
two-test producer fixture and sample source. Its archived result is 44 tests:
2 real static producers, 10 call adapters and 32 alias samples, with zero
failures/errors/skips. It links the preserved pre-repair red run. This validates
the nested-enum and inherited-owner reproductions; it is not a receipt for later
test edits, the new predicate policy or a successful fixed-project run.

The current [JIRStaticDomainProducerTest](../core/opentaint-dataflow-core/opentaint-jvm-dataflow/src/test/kotlin/org/opentaint/dataflow/jvm/bidi/JIRStaticDomainProducerTest.kt)
also adds class-pass rejection, actual local-write alias suffix preservation and
class-point GEN controls. The new
[JIRStaticMetadataFallbackTest](../core/opentaint-dataflow-core/opentaint-jvm-dataflow/src/test/kotlin/org/opentaint/dataflow/jvm/bidi/JIRStaticMetadataFallbackTest.kt)
checks signed source/sink potentials, actual runtime conditionality and real
gather fallback with a joined graph but no retained context for that method.
The repaired symbolic inventory has a source-held focused receipt at
`J/validation/symbolic-focused-1/receipt.json`, SHA-256
`a857bb445cc3b662c7a7b1dda7926ca71013cc4aad687069ec8f31430dd62c8d`:
121 tests in 8 suites, zero failures/errors/skips. This includes 5 producer
controls, 10 metadata controls and 6 fallback controls. The red symbolic cases
and the older 119/406 checkpoint erratum are preserved separately.
The subsequent full gate at `J/validation/symbolic-full-jvm-1/receipt.json`,
SHA-256 `dfa78d249597ac7dbd1e777815962ed6da80ade6f9aeb2fe619def520aefc5ea`,
passes 408 tests in 33 suites with zero failures/errors/skips, also builds the
module classes, and records unchanged source entries across the gate. These
receipts validate their pinned sources and controls, not a global JVM theorem
or a repaired project run. PDF's latest diagnostic still failed run-1 without a
completed iteration or accepted report; the corrected map control is pending.
Different AP interning orders,
a no-fields class point and a supplied domain field outside H remain separate
remapping/domain controls. A fixture that declares its expected coordinate does
not exercise the actual JVM producer bridge. Completed repaired map-control and
factored project results need their own bindings and reached/completed/accepted
iteration evidence.

## 2. Exact two-slot request relation

Use the already bounded incoming leaf from section 1.2. A valid static leaf has
path `root`, `[C]` or `[C,f]`. The earlier transfer cuts establish this input
domain; the projector does not introduce a second cut or silently truncate an
invalid incoming path.
The original leaf annotation and layer are retained for ordinary AP matching.
Only the request test erases field E and FLOW mark exclusions.

For a concrete request mark T define `Match(slot, leaf)`:

| Slot and leaf | Match |
| --- | --- |
| B(b), nonempty leaf on b | Yes |
| B(b), different base or Reach | No |
| F(C,f), concrete/abstract leaf at `[C,f]` | Yes |
| F(C,f), ANY/STAR leaf at root or `[C]` | Yes |
| F(C,f), exact leaf at root or `[C]` | No |
| F(C,f), another class/field or non-static base | No |

A FLOW leaf gives no concrete answer. For a TAINT leaf, T must be in that leaf's
actual exact/ANY mark set. R membership alone supplies no mark. The answer is:

| Concrete matching leaf | Answer initial for T |
| --- | --- |
| Non-static exact root | Exact root on the same base |
| Other non-static leaf | STAR root on the same base |
| Exact leaf at `[C,f]` | Exact `[C,f]` |
| ANY/must leaf at `[C,f]` | STAR `[C,f]` |
| Broad ANY ancestor of `[C,f]` | STAR `[C,f]` |

Every answer has empty E. No run-1 InitialAp has ANY or ANY_TAINT tail. An incoming
ANY/must supplier produces a concrete STAR initial, which starts as an ANY fact
in the DEMAND layer. InitialAp tail and starting/conclusion tail are separate
notions. Exact suppliers retain their exact initial. No nonexact row asserts a
NORMAL exact field fact.

**Matcher theorem.** A request produces exactly the above answer alternatives
for its slot and the concrete marks present in an input, independently of E,
request role and FLOW exclusions.

**Proof.** Case-split on the base and the three possible static path lengths.
Different bases fail. At the same path, exact takes EXACT and ANY/must takes STAR.
At a proper prefix, only the broad leaf denotes a possible continuation;
an exact prefix has no descendant. At a sibling, neither prefix relation holds.
The non-static slot has no path test beyond its base. Finally intersect the
selected leaf's actual concrete marks with the requested set. This enumerates
all accepted shapes; it creates no mark or conjunction. E is absent from each
test, so changing E cannot change this request result. FLOW has no concrete mark
set and cannot enter the answer branch. This is equality to the **new erased-E
request policy**, not equality to the old E-sensitive request cursor. Ordinary
summary guards are not erased by this theorem.

An exact class point `[C]/EXACT/T` is a separate direct concrete input. Preserve
its exact shape and layer; never project it onto a field. A broad supplier also
has a possible class-point component. If an effective class-level operation can
read that component, the direct class-component path must preserve its may layer.
It must not invent NORMAL exact class taint. Section 6 includes both components.

**Local STAR coverage law.** Replacing a supplied nonexact field/class answer by
STAR keeps its base, projected position, concrete mark and empty E. Its startFact
is still ANY/DEMAND. No actual supplied mark or required local analysis disappears;
the abstract STAR analysis is emitted independently and remains additive. Here
“always abstract” applies to a selected supplied structural slot, chosen by the
closed plan or relevant wide activation. It does not enumerate all irrelevant
incoming fields or change the untouched frame. Missing R(T) must never suppress
that selected slot's abstract initial.

**Proof.** Split on exact versus nonexact supplier. Exact retains EXACT. Every
nonexact answer uses STAR and the existing concrete-STAR start row, hence starts
ANY/DEMAND with the same concrete mark. Request admission and the successful
supplier projection are still both required for a normal-slot concrete answer.
This proves local output/request coverage and preserves uncertainty. It does not
authorize an initial from legal shape or a request alone.

This is not a global ANY/STAR summary-equivalence theorem. In the measured
[Summary](../core/opentaint-dataflow-core/opentaint-dataflow/src/main/kotlin/org/opentaint/dataflow/bidi/ap/Summary.kt)
`applicablePart`, a concrete ANY premise selects only input ANY marks, while a
concrete STAR premise can also select exact marks. Another exact C.f/T input can
therefore satisfy the new STAR premise when it did not satisfy the old ANY one,
despite the same initial starting fact. Premise identity, full ordinary guards,
native/reversed record views, persisted summaries and hand-off/frontier behavior
still require their own refinement and regression checks. Equal seeds alone do
not prove those results or strict finding parity.

For that selected field slot the executable witness has this exact form:

```text
incoming C.f ANY/must carrying T; R lacks (full Key, F(C,f), T):
    [ abstract C.f STAR ]
same supplier; R contains (full Key, F(C,f), T):
    [ abstract C.f STAR, concrete C.f STAR/T ]
```

The second initial starts as ANY/T in DEMAND; neither list contains an ANY
InitialAp. If the supplier lacks T or does not match, a request alone cannot add
that concrete initial. Independent requested marks remain separate alternatives.
The direct exact-class path is an explicit existing exception to normal-slot
request gating; broad class handling requires its metadata need and actual
supplier, without inventing a class request key.

## 3. Obligation 1: complete request-producer inventory

The inventory is over effective operations, after native/generated/lowered form
construction. A surface action name is not a separate semantic producer.

| Effective operation on a FLOW fact | Request need |
| --- | --- |
| Source condition, sink condition, or conjunction ContainsMark literal | The literal's concrete input mark |
| ContainsMarkOnAnyField and its positive NNF leaf | The same concrete mark, with the ordinary predicate unchanged |
| Concrete input-mark gate of a source, pass or transfer microedge | The gate's input mark, not its generated output mark |
| Selected-mark cleaner, including generated returned-stage rewriters | Its selected mark on the original abstract initial |
| A primitive with an abstract target excluding marks | Every target-excluded mark, as an effective selected-cleaner need |
| Call/summary transfer of a preceding need | The same need, routed to the original caller slot |

The last exclusion-creator row is a required semantic side effect. The measured
[MarkGate](../core/opentaint-dataflow-core/opentaint-dataflow/src/main/kotlin/org/opentaint/dataflow/bidi/ap/MarkGate.kt)
can form an excluded target mark in a generic compiled edge without itself
emitting a request. The selected semantics adds a same-origin request for each
target-excluded mark, or normalizes that edge to an equivalent explicit
selected-exclusion operation with that request. Legal hand-built edges are not
rejected to make the theorem true. The side effect belongs to the exclusion-producing
operation, not to inspection of a final mx header. Summary mx carries the earlier creator obligation
through call replay; it does not need a runtime provenance store.

The measured source anchors for the inventory are:

- [MarkCheck](../core/opentaint-dataflow-core/opentaint-dataflow/src/main/kotlin/org/opentaint/dataflow/bidi/ap/MarkCheck.kt),
  `markCheckFacts`: ordinary concrete literal checking.
- [EdgeApplication](../core/opentaint-dataflow-core/opentaint-dataflow/src/main/kotlin/org/opentaint/dataflow/bidi/ap/EdgeApplication.kt),
  `flowHit`, and MarkGate: concrete microedge input gates.
- [Clean](../core/opentaint-dataflow-core/opentaint-dataflow/src/main/kotlin/org/opentaint/dataflow/bidi/ap/Clean.kt),
  `FlowClean.run`: selected cleaner adds mx and emits the selected mark request
  whenever its base matches, including a spatially unrelated field.
- [RunMethodLocalServices](../core/opentaint-dataflow-core/opentaint-dataflow/src/main/kotlin/org/opentaint/dataflow/bidi/engine/RunMethodLocalServices.kt),
  `conjunction`, `sourcePredicate`, `checkSinks`: legacy and positive NNF leaves.
- [FormApplier](../core/opentaint-dataflow-core/opentaint-dataflow/src/main/kotlin/org/opentaint/dataflow/bidi/interp/FormApplier.kt),
  `statement`, `stage`, `gen`, and
  [RunMethodCallServices](../core/opentaint-dataflow-core/opentaint-dataflow/src/main/kotlin/org/opentaint/dataflow/bidi/engine/RunMethodCallServices.kt),
  `applyStage`/`cleanChain`: effective entry, statement, bound/returned, native,
  generated, exit and end-GEN execution routes.
- [JIRRuleForms](../core/opentaint-dataflow-core/opentaint-jvm-dataflow/src/main/kotlin/org/opentaint/dataflow/jvm/bidi/interp/JIRRuleForms.kt)
  and [JIRCallPlanBuilder](../core/opentaint-dataflow-core/opentaint-jvm-dataflow/src/main/kotlin/org/opentaint/dataflow/jvm/bidi/interp/JIRCallPlanBuilder.kt):
  condition/action lowering, cleaner lowering and returned-stage rewriters.

**Producer theorem.** Each mark-dependent primitive that cannot decide its
concrete input on a FLOW fact falls into a row above.

**Proof.** Enumerate the effective form grammar: plain edges, conjunction/NNF
conditions, selected/all cleaners, type filters, keep/strong-write operations,
and GEN. A concrete edge premise uses its input mark; an abstract premise needs
none, except newly introduced exclusions as stated above. A condition reduces
to its active positive leaves; each concrete leaf uses MarkCheck. OR changes
which proof can complete; AND stores compatible bindings. Neither introduces a
new kind of mark input. A selected cleaner uses its selected mark. A remove-all
cleaner tests no concrete mark and requests none. A zero-generation edge has a
zero input and does not request the generated mark. Type filters inspect type
and geometry, not a concrete mark predicate. Keep/strong-write geometry adds no
mark need. Calls compose these operations and route their existing needs. This
is an exhaustive structural induction on the effective form, not an enumeration
of only sink rules. Repeated canonical leaves and separate occurrences remain
in the predicate stores; unioning their request marks does not merge their proofs.
No mark request does not mean no structural footprint: a zero-generation target
or remove-all target still contributes every field its operation can affect to H.

## 4. Obligations 2 and 3: construct the plan and its dynamic residual

For each method m enumerate its finite effective forms. Construct:

```text
localH[m]        normal static fields read, written or otherwise affected
localN[m,f]      unchanged-field concrete needs found by section 3
localWide[m]     selected-mark/base/class needs requiring input activation
localPoints[m]   legal concrete class-point operations
```

`localH` includes semantic scopes, not just literal read positions. A wide
operation can own every field in its class or in Sigma. A selected-mark cleaner's
base-wide abstract exclusion requires its mark for each relevant normal-field
analysis, even if the cleaner is on another field. Keep that finite wide rule as
shared metadata; do not build every mark/field/MethodKey product in advance.

Compute the least closure by starting at the local sets and repeatedly unioning
the current sets of each resolved callee. Every insertion is a member of a finite
form-derived universe, so iteration terminates. Share immutable closed metadata
per method/component. When an actual input activates a field in a wide scope,
instantiate its known marks into that Key's temporary RequestStorage.

The reference construction can return actual path data without choosing a witness
from an existential proposition:

```text
for (method, formIndex, need) in enumerated local members:
    insertIfAbsent(method, need, path=[], formIndex)
while an inserted (callee, need, path, formIndex) remains:
    for caller in resolvedCallers(callee):
        insertIfAbsent(caller, need, path=[caller -> callee] + path, formIndex)
```

Each successful insertion consumes one previously absent `(method, need)` pair.
There are finitely many pairs. Each record contains a concrete finite edge list
and the actual local form index. This is a proof/reference construction; runtime
metadata need not keep these paths. The Lean reference closure enumerates finite
closed subsets instead. Its seed, edge-closure and leastness laws identify the
same least set; the path-producing worklist supplies the constructive certificate.

**Path-witness lemma.** A closed-plan member has an executable witness consisting
of a finite resolved call path ending in an enumerated local form member.

**Proof.** Give each initially inserted member the empty path and its local form
index. When propagating from callee c to m, prepend the actual edge m→c to the
callee's existing witness. This construction proves sound membership. Conversely,
induct on the length of any such call path. A length-zero member is inserted at
initialization. For m→c followed by a shorter path, the induction hypothesis puts
the member in c's closure, and edge propagation puts it in m's closure. This proves
total path coverage and leastness. A recursive component needs no acyclic graph
assumption: every finite derivation has a finite path witness.

**Static coverage theorem.** Every unchanged-field need found by a producer is
precomputed, or is a precomputed wide need activated when its input becomes relevant.

**Proof.** The producer theorem supplies a local form index, position/scope and
mark. A direct unchanged field enters localN. A wide scope enters localWide and
the input activation computes its exact field intersection with Sigma. The path
lemma propagates that entry through every resolved caller, including recursive
paths. Storage creation installs resolved memberships before exposing subscribers.
Later activation writes the same member to W and uses the late-request law in
section 8. The proof constructs the member; it does not assume plan coverage.

### 4.1 Full precomputed workflow without a duplicate store or initial propagation wave

Use the closed normal-static slot→marks relation itself as the frozen base B of
authoritative R. R borrows the same immutable base value and mark segments owned
by the method/SCC plan. The selected representation can expose this relation
without a materialized row map, under §4.2's laws. Do not keep a separate
full-query/role catalog containing another copy of those memberships, copy B into
W, or populate D by repeatedly publishing B. Structural H and unresolved wide
descriptors are different metadata; they are not a second admitted membership
relation.

Resolve the frozen base from actual closed descriptors, not from an arbitrary
runtime-active field set:

```text
slotsOfNeed(structural(C,f))       = [F(C,f)]
slotsOfNeed(marked(field(C,f),T))  = [F(C,f)]
slotsOfNeed(other)                = []
frozenSlots(needs) = flatMap(slotsOfNeed, needs)
frozenBase(needs)  = deduplicate(activate(needs, frozenSlots(needs)))
```

Thus a wide descriptor may supply its mark on an already resolved frozen slot,
but a wide descriptor alone does not enumerate new runtime fields. Class-point
metadata does not invent a field slot. A later input-only activation remains a
dynamic residual, not a mutation of B.

The workflow is:

1. Prescan enumerates effective needs and computes the least resolved-call closure.
   Resolved normal static needs become the shared immutable B. Unresolved wide
   activation rules stay compact metadata rather than an eager mark×field table.
2. Before a SummaryStorage is exposed to any subscriber or input, bind that B and
   create empty D. The actor's second same-class RequestStorage has an empty base
   and empty temporary contents. Effective `R(slot) = B(slot) union D(slot)`.
3. A new TAINT input is processed by the abstraction's current-R read. A new FLOW
   input/growth is joined with current R in its caller input store. These two
   existing input-arrival arms read B as well as D. No initial notification wave
   is needed to make a not-yet-existing subscriber discover B.
4. An ordinary producer or received proposal consults effective R first and adds
   only residual marks to W. It never reads W for membership. A matching installed
   B member therefore allocates no temporary request and creates no notification.
5. A later wide activation or a changed-origin residual may add a genuinely new
   member. It uses normal W→commit→fresh notification. B remains frozen; D stores
   only members absent from B. Request persistence and the late-input theorem apply.

**No initial-wave theorem.** Binding B before all subscriptions and input admissions
preserves all answers and climbs without notifying the initial B memberships.

**Proof.** Consider each actual input admission A. Its registration and input-read
ordering follows section 8. Since B is already installed, the TAINT emitter or FLOW
input arm reads its relevant B members. Thus no subscriber can predate B and wait
for a publication that will never occur. A later dynamic commit still notifies its
fresh marks. Input updates repeat their normal arrival arm. The proof fails if a
storage is exposed before B is bound, or if fresh FLOW reads only notifications.
It does not authorize removing either input-arrival arm.

**Unchanged precomputed-route theorem.** Suppose m calls n, n's precomputed need is
`F(C,f)/T`, and the matched caller FLOW has unchanged original normal slot F(C,f).
No request message for that target membership is needed.

**Proof.** A callee frozen-base member has two executable witnesses: its marked
scope descriptor and a descriptor that contributes the slot to frozenSlots. The
resolved edge m→n and the least-closure lemma carry both descriptors into m's
closed plan. `slotsOfNeed` is pointwise, so frozenSlots is monotone under that
inclusion. Scope membership then activates the same f/T member in frozen B[m].
This proof has no premise that an arbitrary runtime field is already active.
Full context/entry keys borrow that method's conservative B before use.
The target effective R already contains it, so subtraction returns the empty
contribution. Its permanence prevents a lost later answer. This proof is about
the actual unchanged origin and membership, not merely a STATIC current base.
For FLOW this can skip the entire precomputed **mark segment of that queried
slot** before geometry or per-mark callbacks. It does not skip TAINT answers or
other slots in the callee's base. For example, let a caller's original f analysis
send a packed FLOW containing f and g after a copy f→g. With callee B[f]={T} and
B[g]={U}, caller B inherits f/T and g/U, but need not contain f/U. The f/T segment
is redundant; the matching g/U segment must still route to original f/U.

The following routes still need ordinary matching or propagation:

| Route | Why precomputation does not remove it |
| --- | --- |
| C.g→C.f, or a non-static origin copied into C.f | The caller's required slot is g or its original base, not literal f |
| A precomputed callee f/T need matched by a FLOW with a changed original slot | CG closure contains the literal f/T; it need not contain the rewritten origin/T |
| A dynamic callee g/T need discovered from an f/T check | Plain literal closure does not compute every runtime provenance transformation |
| F75 selected cleaner or generic target exclusion on a different normal origin | The same-origin request is required for that origin; a plan declaration for the checked field is not its admission |
| A previously unresolved wide need activated by a new input field | That concrete field membership was not in B and must enter D through commit |

In each row, subtract the **actual destination's** effective R. If it already
contains the rewritten membership, propagation becomes a duplicate and is omitted.
Otherwise it remains required. Concrete suppliers answer directly; they do not
propagate requests to concrete origins. This is why “no duplicate store and no
initial propagation of B” does not mean “no request notifications or FLOW routing”.

**Physical ownership obligation.** An implementation must preserve the shared B
reference and disjoint residual representation. Logical set equality alone does
not prove physical sharing: copying the same values into another catalog, actor
map, W or D would satisfy membership laws but fail this optimization requirement.
Test object/reference sharing and allocation/census counters separately.

**Dynamic residual theorem.** A mark need under a normal abstract initial i is
not lost when its current checked position differs from i.

**Proof.** Normalize i only to B(b) or F(C,f), preserving the full Key. If the
required member is already in R, persistence discharges it. Otherwise union it
into W at that original slot. Do not test the current output path to choose an
origin. In `x=C.g; C.f=x; check(C.f,T)`, the residual is g/T, regardless of the
precomputed f/T member. The same argument covers argument→static, static→local,
and a caller FLOW input. A concrete current fact does not use this branch; its
actual T can answer directly. The abstract root seed uses section 6's field-owner
counterpart, not a guessed root request. Thus the two normal shapes exhaust the
remaining abstract-origin cases.

### 4.2 Virtual frozen-base refinement

Use the coverage-corrected materialized map as the reference control. The aborted
`4c64c579f` execution is not that control. The independent expected map expands
raw descriptors after a finite graph fixed point; it never calls the virtual
lookup, union cursor or matching implementation to compute expected values.

For a method's closed descriptors let S be its resolved selected field slots,
K[C] its existing common class marks (root marks included), and F[s] its sparse
field-specific marks. Define the map and virtual relations by:

```text
Bmap[s] = K[C] union F[s], if s = F(C,f) and s in S
          empty,          otherwise
Bvirtual[s] = distinct borrowed view of those same segments, under the same guard.
```

Class-point metadata is not F or a normal request slot. Its root class marks do
not include all of the field-request root's cleaner marks. Preserve it separately.

**Lookup/empty law.** Bvirtual[s] and Bmap[s] have the same members and are empty
for the same slots, including s outside S and ordinary non-static slots.

**Proof.** Outside S the guarded definitions both return empty. Inside S, a mark
is in either representation exactly when it is in K[C] or F[s]. Union duplicates
do not change membership. Emptiness is the conjunction of segment emptiness,
which requires no merged array or cardinality walk.

**Ordered union law.** Merge the two sorted source arrays by their smallest
remaining ID. Retain the last emitted ID and suppress a repeated ID. The cursor
emits the sorted distinct union and terminates after at most |K[C]|+|F[s]| consumed
source cells.

**Proof.** At each step the consumed source prefixes contain exactly the already
emitted union and any repeat of its last ID. The smallest next source cannot
precede the last output. Emit it only if different, preserving this invariant.
Every step consumes a source cell, including a suppressed duplicate. Thus a
duplicate-only step(1) advances without a callback, and finite exhaustion visits
every member once. Ordinary signed comparison handles ID 0 and boundary values;
no arithmetic difference or sentinel is needed. Cancellation interrupts work
without proving completed enumeration. This law is for B; the existing R cursor
concatenates distinct B and disjoint D and need not be globally sorted.

**Row-enumeration law.** For a broad static candidate pass let KB be the nonempty
B slots and KD the nonempty static D keys. Enumerate KB in the existing S order,
then `s in KD where B[s] is empty` in the existing D order. This gives every
effective static R row once. Non-static matching uses its direct base lookup.

**Proof.** The two enumerated sets are disjoint. Their union is KB union KD:
each D key either already occurs as a B row or occurs in the second pass. In
particular, `s in S, B[s]=empty, D[s]!=empty` belongs to the second pass. Testing
only `s not in S` would lose it. Each returned descriptor contains that B view
and the captured immutable D value; mutable directory iteration ends under the
existing storage lock before any callback.

**Storage refinement law.** Relate map and virtual states by equal D/W and the
lookup law. Every collection/commit step preserves equal effective R and fresh
notifications, with D and W disjoint from B.

**Proof.** Equal R membership gives equal `offered minus R`; an empty residual
returns before W insertion or new Work scheduling. The remaining contribution
unions into equal W. Commit computes the same `fresh = W minus R`, installs the
same immutable D union and queues the same logical fresh members. Each is absent
from full B, so the disjointness invariant remains. W replacement and the shared
base are unchanged. Binding B before input and the two current-R input arms
reuse §4.1's no-initial-wave argument. Full MethodKeys keep separate D/W; sharing
B does not relate one owner's mutable residual to another owner's residual.

**Answer/climb refinement law.** Equal row enumeration and membership give equal
requested concrete initial sets and climbed interests for each input/origin.

**Proof.** The slot geometry and actual supplied mark are independent of B's
representation. Their intersection with equal R therefore gives the same EXACT
and STAR answers. FLOW still gives no concrete answer and uses the same full
destination R difference. Caller inclusion and unchanged-slot B suppression are
§4.1's laws applied to the same sets; changed origins, dynamic D and packed f+g
still use their separate rows. Ordinary E/mx, premise, layer, predicate and record
guards are unchanged. Induction over finite input/delivery/commit derivations
lifts these steps to completed closure under the same candidate policy. It does
not establish equal timeout prefixes, a wall-time bound or a new ND certificate.

**Logical-count law.** Write S_C for selected slots in class C. Then:

```text
members(B) = sum_C |S_C| * |K[C]| + sum_(s in sparse F) |F[s] minus K[class(s)]|
rows(B) = sum_C ( |S_C| if K[C] is nonempty,
                  number of nonempty sparse field rows in C otherwise ).
```

**Proof.** For each selected slot, K and F-minus-K are disjoint and partition
K-union-F. Sum their cardinalities, grouping the repeated K term by class. If K
is nonempty every selected slot gives one row; otherwise exactly the nonempty
F rows do. Sparse F slots are selected by construction. This permits exact
logical totals without enumerating all virtual field/mark pairs. Use checked
arithmetic and cancellation. Physical descriptor/set/entry costs and temporary
view work remain separate measurements; this identity proves no saved bytes.

**Remaining physical obligation.** Production keeps one virtual base, not a map
plus a descriptor cache or per-context expanded mirror. Borrowed views are
immutable, and release removes owner references rather than changing their
values. After run-1, the stable mark-free H/Sigma footprint still supports
return-guarded ordinary/native/opposite readers. Test shared-reference identity,
full-key isolation and release separately from these extensional set laws.

## 5. Obligations 4, 5 and 6: two counterexamples that fix root composition

The impossible exact STATIC root assumption alone does not justify reuse of a
raw abstract-root summary for concrete inputs. Both following inputs are legal.

**Counterexample A: cleaned taint is restored.** Let a method contain only
`Clean(C.f,T)` and receive exact `C.f/T`. Its concrete f/T analysis correctly
returns no f/T. A root summary that skips the cleaner is identity. Unioning that
identity result with the concrete result restores f/T. Adding a concrete analysis
does not repair an unrestricted root read.

**Counterexample B: an untouched sibling is lost.** Strongly write C.f and receive
exact `C.g/T`, g≠f. A coarse root keep can exclude the whole class C. Applying that
raw root summary drops g/T even though the write cannot change g. A literal
footprint `{f}` and a raw-root complement are therefore insufficient.

The selected repair is **structural ownership plus a proved untouched identity
frame**. It is not an extra request store or a supplier cache.

## 6. Constructive static component composition

Implementation exposed additional packed-codec obligations, specified in the
[codec clarification](../docs/design/bidirectional-run1-static-component-codec-20261015.md).
Class-point ownership is separate from field ownership; broad owned point starts
preserve every supplied mark. Mixed E headers stay separate. The selected JVM
broad-FLOW source-route invariant and Kotlin codec conformance are implementation
gates. The existing Lean receipts below do not certify those new source/codec
bridges; their scalar partition and initial-projector results retain their stated scope.

Partition a static input into these disjoint semantic components:

1. Exact class-point components, one per representable class.
2. Field components, one per `(C,f)` in Sigma, retaining their suffixes, actual
   marks, ordinary E and layer.
3. Any explicit residual outside the finite named prefix cells. Such a residual
   is permitted only when the accepted adapter proves no form can observe it.

For a broad root/class ANY leaf, compute the corresponding component restrictions.
This is a transient read-time partition. A field restriction of a broad may leaf
retains its uncertainty; it is not a new NORMAL exact leaf. Do not flatten every
method's input relation into a permanent method×field table.

Let H[m] be the closed semantic footprint from section 4, with wide scopes expanded
over Sigma and relevant input fields. It is a set of geometries, not request marks.
Use this ownership rule:

```text
field h in H[m]       use normal field analyses (abstract STAR always,
                     plus supplier-backed requested concrete analyses)
field h not in H[m]   use the untouched-component identity frame
class-point input    preserve its direct concrete component and effects
```

Do not additionally apply the raw root summary to either field class. A missing
result from an owned field branch is a real empty result; it is not a reason to
fall back to identity. Read-time ownership must also govern native and persisted
opposite-direction summary views. Otherwise either counterexample recurs later.
The identity frame is available only along an actual normal-return derivation.
It cannot invent a return when a path has no normal return. The root seed keeps
its ordinary control/summary role for this purpose; its raw data result is not
the proof of the frame.

**Partition lemma.** The components cover every accepted static input location
and do not assign a field location to two owners.

**Proof.** Every accepted static address is a class point, starts with exactly
one `(C,f)` from Sigma, or belongs to the explicitly unobservable residual. These
cases are disjoint by path length and first class/field accessors. H membership
is decidable, so each field has one owner. Restricting each concrete may-location
to its case and unioning the results recovers the input's location denotation.
This statement does not promote an existential/may leaf to certainty. Physical
tree encoding and layer annotations must implement these scalar restrictions.

**Untouched-frame lemma.** If h∉H[m], any finite normal-return derivation of m
and its resolved callees leaves h's input component unchanged and cannot read it
into a rule result. This lemma is conditional on the return derivation, not on
the existence of a raw root output.

**Proof.** A primitive that reads, writes, cleans or otherwise affects h would
place h in localH, contradicting h∉H[m]. A broad operation is not an exception:
its scope expansion would also insert h. An unrelated keep, transfer, type filter
or strong write is identity on h by its scalar AP row. A call has a static identity
binding. If a resolved callee could observe h, the path-witness lemma would insert
h in H[m]. Induct on a finite instruction/call derivation, using these primitive
cases; a recursive call is a smaller finite subderivation. Therefore its component
result is exactly identity. In particular, a coarse raw E{C} is not used to prove
identity on g: the primitive disjoint-write row proves it.

**Owned-component lemma.** For h∈H[m], normal field analysis plus the request
coverage of sections 3–4 and 8 supplies every concrete branch needed for h.

**Proof.** Start the abstract h initial unconditionally. Induct over its finite
transfer derivation. Mark-independent primitives preserve the scalar component
transfer. A concrete gate or predicate leaf supplies a request by section 3; an
already concrete branch evaluates its actual mark. A selected cleaner always
registers its selected T for the original normal h initial, even when its concrete
spatial effect is disjoint. Static plan/wide activation or dynamic residual keeps
that membership. The arrival theorem supplies the concrete h/T initial from any
matching supplier. Its concrete cleaner row decides inside/partial/disjoint and
therefore removes f/T but preserves disjoint g/T. A call uses the same argument in
its field-owner counterpart, with the full caller premise and layer intact.

Conjunctions require a closure induction, not distributivity of AND over a single
input. The existing predicate store joins the component branches under the same
occurrence/proof and compatible complete binding rules. Thus the individual leaf
inputs still produce every previously permitted complete binding. Their request
sets are independent alternatives and supply no new conjunction evidence.

**Static composition theorem.** The partition, owned-component lemma and identity
frame preserve the scalar static component transfer under the selected request
policy. They resolve both counterexamples.

**Proof.** Case-split each input component by its unique owner. For f/T in A,
the concrete owned branch is empty and no root result is admitted, so T cannot
reappear. For g/T in B, either g is owned and its disjoint concrete/abstract row
preserves it, or g is unowned and the frame returns it. The same case proof applies
to broad supplier components and to later summary reads. Exact class points keep
their direct input and legal class-level operations; they are neither dropped nor
broadened to fields. The forbidden exact empty root is not used in any case.

This theorem is about the specified component composition. It is not a theorem
about the measured root summary implementation. An implementation must refine the
transient partition, uncertainty annotations and owner selection. In particular,
old descendant-only summary candidate selection cannot silently exclude a projected
broad ancestor. The read adapter must pass the matching component to the full AP
guard. No new same-call confirmation theorem follows for pooled ND candidates.
The ANY-to-STAR correction has not acquired whole summary/record compatibility
from this frame theorem. Its local laws and the changed syntactic applicability
case in section 2 remain separate from that implementation bridge.

## 7. Obligation 8: concrete-initial safety

K6 gives a FLOW fact a single abstract-mark initial. A concrete fact retains its
actual zero, concrete, or multiple-concrete premise; an invalid pairing fails the
invariant rather than disappearing.

**Concrete safety theorem.** No upstream mark request is needed from a concrete
current fact or concrete-mark original initial, but concrete answers and ordinary
transfer must remain enabled.

**Proof.** A concrete predicate/gate reads the current exact/ANY mark sets. A
selected cleaner removes actual marks by its concrete spatial rows. Neither has
an unknown mark to obtain by restarting that already concrete initial. A current
concrete fact can still answer a callee request for T precisely when it carries T
and matches section 2. The caller's original concrete/ND premise is retained for
ordinary summary application. For an abstract FLOW premise the earlier producer
and call-path proofs continue to supply requests. These cases exhaust K6. A
source-generated concrete mark is in the concrete case, even when the method also
analyses abstract initials; it is not a reason to raise a request for
the source's output mark. Nothing here skips source actions, F75 residual effects,
predicate bindings, GEN, concrete summaries or cancellation checks.

## 8. Obligation 7: no-memo arrival theorem

Let A be the linearized admission/update of an actual caller input, E its callee
abstraction read of R, Q a request commit, and V the corresponding caller-input
visit. Registration precedes A; `A < E` and `Q < V`. Queued same-unit events obey
the same order. A caller visit observes its current immutable input value for
the accepted full header. No input is reconstructed from a request key.

**Arrival theorem.** Every accepted request/input pair is processed by E or V.

**Proof.** If Q<E, E reads persistent R and handles the input. If E<Q, then
`A<E<Q<V`, so V sees the admitted input. Equivalently, both misses require
`A<E<Q<V<A`, a strict-order cycle. Registration races do not add a third gap:
if an earlier commit missed the not-yet-registered subscriber, its later E read
sees R. A late input update is itself another A/E pair. A new request delta is
another Q/V pair. The proof covers different callers, contexts, cycles and
input growth because it is per full admitted input event, not per first caller.

For concrete TAINT, E emits requested answers and V handles late marks. The extra
fresh-TAINT question replay arm is redundant. For FLOW, the input-admission side
still joins current R and forwards a set of needs; it is not a concrete answer.
Remove no ordinary summary replay. InitialFactStore may deduplicate duplicate
answers. R is not consumed by one answer, so future inputs still see it.

No pending answer memo is needed. This conclusion fails if a test or adapter
delivers a nonzero incoming input without paired caller input admission. Such
delivery must provide the same order contract. Internal zero/GEN facts are normal
transfers, not an omitted incoming-supplier channel.

## 9. Obligation 9: ignored mark exclusions are redundant requests

Use a proof-only provenance relation `Need(Key, originalSlot, T)`. It records an
eventual request obligation, not another runtime store. Define its constructors:

1. A selected cleaner or generic abstract-target exclusion creator emits that same Key/slot/T need before
   publishing an output containing newly excluded T.
2. Mark-independent transfers retain the original Key/slot.
3. A callee obligation is routed over its full caller FLOW input to the caller's
   original normal slot. Root-seed duplicates use the proved field-owner
   counterpart, not a root request.
4. A union/subsumption of FLOW branches cannot introduce a mark exclusion that
   was absent from every contributing derivation. Dropping exclusions by
   intersection can remove obligations from a header, not invent a new T.

**Exclusion provenance lemma.** A reachable excluded T on a normal abstract-origin
FLOW route has a finite derivation of Need for the same original destination used
by an unfiltered climb. A special root route uses its field-owner counterpart;
it is not evidence of a nonexistent root request.

**Proof.** Initial FLOW exclusions are empty. At first creation, constructor 1
applies. Transfer and summary composition use constructors 2 and 3. Recurse through
the finite callee derivation until the creator is reached. Header merging uses
constructor 4. Native/generated cleaner steps are first creators, not unexplained
exceptions. A preloaded arbitrary mx value with no creator is outside this proof.

**Erasure theorem.** Removing mx tests from request propagation leaves the eventual
cumulative request union unchanged, although it may add duplicate work.

**Proof.** The filtered request steps are a subset of the unfiltered steps. Each
extra step carries an excluded T. The provenance lemma supplies the same eventual
destination obligation. Sections 4 and 8 deliver it through the ordinary method
barrier. Therefore the added membership is already in the filtered closure. Union
idempotence makes the two least request relations equal. This is not equality of
event order, notification counts, temporary sizes or pre-timeout prefixes. Incoming
field E is erased by the new request definition; its erasure is not derived from
the mx provenance lemma. Ordinary E and mx transfer semantics remain unchanged.

## 10. Merged marks, batching and early admitted-set subtraction

All marks in one request row are independent. R consists of frozen B and residual
D with `B intersect D = empty`. W has no borrowed base. Local and received
contributions are filtered against current effective R before union into W; this
is not a read of W. Commit computes `fresh = W - (B union D)` and installs
`D' = D union fresh` before notification. Effective `R' = R union W`; replacement
clears only W. B is never copied, replaced or notified as fresh. No request is
removed from effective R.

**Batch theorem.** Any finite grouping/order of contributions gives the same
eventual R as immediate singleton admission.

**Proof.** Expand every finite set into its members. Union is associative,
commutative and idempotent, so the final relation is the initial R union all
members offered. Fresh notifications contain exactly first admitted memberships for
that commit. A delayed batch can change when a concrete branch starts; the arrival
theorem prevents loss. It does not prove equal scheduling or a time bound.

On a FLOW route use `offered - targetR[originalSlot]` before per-mark callbacks.
There is no mx filter in this selected request policy. A stale immutable R is a
subset of current R and can only allow duplicates. Subtraction is safe because
every removed membership remains persistent at that exact destination. A
different MethodKey, slot, or callee's R is not a valid subtraction owner. Do not
query W as a second membership oracle. A receiver re-filters a queued stale
proposal against its current R before writing W; sender filtering alone would
not establish disjoint W/D after intervening commits. Different matching inputs with the same
destination union their contributions. Do not subtract the union of exclusions,
and do not turn multiple requested marks into an AND condition.

Both storage instances have the same class. During commit only the method actor
can write W, and notifications are queued. Thus no actor write occurs between
the synchronous read of W and its replacement. A later event writes the new W.
Commit stores immutable admitted sets and does not retain the mutable W instance.
This is the required non-reentrance proof; no extra takeBatch protocol is assumed.

## 11. Method-empty completion and cancellation

Maintain the existing counted Work reservation while a method drains facts,
publishes summaries, commits W, replaces W and reserves notification children.
A request-only event also schedules that existing Work. It needs no synthetic
fact. Same-unit effects remain queued.

**Completion safety.** Global completion cannot occur while an accepted request
or its result is owned by W, commit or a queued notification.

**Proof.** Before commit the method's Work reservation owns W. During commit that
reservation remains live. Before it is released, every child delivery obtains a
counted reservation. Afterwards a child or subsequent method Work owns each effect.
No effect crosses a zero-reservation interval. A duplicate suppressed by R has no
new effect to own. Completion also requires the method's fact/summary work to be
empty. Request-only arrivals and writes queued during flush therefore cannot be
mistaken for an idle method.

With finite accepted work and fair service, a busy method eventually empties its
fact queue and submits W. A cycle/SCC changes the finite fixed point, not this
ownership argument. There is no maximum request-age guarantee while it is busy.
Cancellation or rejected delivery does not prove a completed closure. Preserve
the existing failure/cancellation path, release W/borrowed views, and do not publish
a false COMPLETE state. A fresh run starts with fresh stores and reservations.

## 12. Executable witness matrix and formal scope

### 12.1 Selected repair and factoring checks

The following checks are required for the selected 16 October implementation.
The mathematical set arguments in §§1.3 and 4.2 do not supply a new Lean receipt,
JVM conformance result, project result or physical-memory measurement.

| Check | Independent witness and acceptance |
| --- | --- |
| Producer-domain coverage | Exercise actual direct, alias-generated and rule-generated static endpoints. Include the real gather fallback with null prescan AA but runtime AA enabled, both source and sink signed predicates, class points with no fields, inherited declarations and different AP interning orders. Compare emitted class/field coordinates to Sigma; domain-only fields must not enter H or S. A fixture that supplies its expected coordinates as declared domain is not this bridge proof. |
| Virtual base oracle | Expand raw descriptors with ordinary sets after an independent finite graph fixed point. Compare every selected and nonselected slot to virtual membership, empty checks, distinct iteration, logical totals and answer/climb closure. Do not compute expectations with virtual lookup or the production union cursor. |
| Sparse and overlapping segments | Root/class/field overlap, duplicate IDs, empty common segments, slots selected with empty B then nonempty D, wide scope outside S, mark 0 and signed boundary IDs; cursor budget 1 must advance even when it suppresses a duplicate. |
| Protocol and original origin | Both arrival orders, late subscribers/input growth, mixed f→{f,g}, queued reentry, commit/install ordering and cancellation. Preserve exact initial shapes and full-key D/P isolation while contexts share only immutable B. |
| Physical and lifetime checks | Assert no production materialized-map mirror, no full field×mark construction or logical-count traversal, shared immutable segments, and retained mark-free H/Sigma after run-1 release. Logical member totals are not physical cell/byte totals. |

The existing common profile fixed-point oracle, request storage/pipeline tests,
initial grammar/matching tests and static summary/driver tests provide distinct
parts of this validation. The JVM metadata tests provide direct frontend cases.
They must be extended where needed; their earlier passing receipts do not prove
the new virtual representation or the null-AA producer bridge.

First diagnose the fixed-config failure and validate a coverage-corrected
materialized-map control. Then compare factoring to that corrected control under
the same candidate policy and full semantic observations. A resource-limited map
control can provide a labelled common prefix, but not a complete oracle result.
Strict project/main identity, span and occurrence comparison remains separate;
the known eight application precision regressions are not waived. Record actual
latest reached, completed and accepted iterations for every measured side.

### 12.2 Historical machine-checked model receipts

**Ten-family no-ANY models: full validation passed on 15 October 2026.** The
selected grammar has ten families and no ANY/ANY_TAINT InitialAp. `lake build`
completed 125 jobs; `lake env lean audit.lean` checked 21,826 declarations with
zero unexpected axioms. Only `propext` and `Quot.sound` were permitted. All
executable audit groups passed, including ten-family supply/start checks and
the nonexact field's abstract-only/requested-concrete-STAR cases. The immutable
source/log-hashed receipt is under the historical proof evidence root H:

```text
report-preparation/run1-no-any-initials-20261015/
  full-20261015T195138.576307Z/receipt.json
SHA-256 47c0aeb30ad6ec36134e025d70aaf680c4e47d78232991a3e0351cdaa2d52abb
```

The selected build also passed (6 jobs), with an independent audit of 1,250
declarations, zero unexpected axioms and the no-ANY/additive executable controls:

```text
report-preparation/run1-no-any-initials-20261015/
  lean-20261015T195126.868112Z/receipt.json
SHA-256 fe94157bfa9482e2b8133bcd5f2a7d59027722eb943696d4b516cb875f9d83c9
```

Here H is the fixed directory
`/drive-testcomp/opentaint-go-rules/bidi-o1-o3-20261012/added-facts-removal/20261015T090757.961708Z`.
All receipt paths in this section are relative to H. They do not refer to the
later run1-mark-requests performance batch.

These checks validate the ten-family finite models, not runtime implementation
conformance. They were proof checks before the later implementation and project
measurements. They do not validate the new producer-domain bridge in §1.3 or
the virtual frozen-base implementation in §4.2. The older receipts below bind
earlier sources and remain historical evidence only.

The constructive Lean work is split across
[Run1Requests](lean/ApSpec/Run1Requests.lean) (slot/projector and form/graph closure),
[Run1RequestsSafety](lean/ApSpec/Run1RequestsSafety.lean) (component ownership/frame
and cleaner counterexamples), and
[Run1RequestProtocol](lean/ApSpec/Run1RequestProtocol.lean) (storage/arrival/protocol
laws), with the selected extensions
[Run1InitialShapes](lean/ApSpec/Run1InitialShapes.lean) and
[Run1PrecomputedRequests](lean/ApSpec/Run1PrecomputedRequests.lean).
The earlier versions of the first two modules passed their selected build, declaration audit and
executable vectors on 15 October 2026:

```text
cd spec/lean && lake build ApSpec.Run1Requests ApSpec.Run1RequestsSafety
cd spec/lean && lake env lean /tmp/run1-requests-selected-audit.lean
cd spec/lean && lake env lean /tmp/run1-requests-selected-vectors.lean
```

The selected build completed 5 jobs. The audit checked 963 declarations with zero
unexpected axiom dependencies; allowed dependencies were only `propext` and
`Quot.sound`. Executable vectors passed. The source/log-hashed receipt is under
the same historical evidence root H:

```text
report-preparation/run1-request-proofs-20261015/
  lean-20261015T184059.935017Z/receipt.json
SHA-256 d3109371a385bf6d47419cdd2a9db04377cb6e39459bc41060a541867bc98d09
```

The earlier full-library check also passed on 15 October 2026, including the then-current three F78
modules and their executable audit controls:

```bash
cd spec/lean
lake build
lake env lean audit.lean
```

The build completed 123 jobs. The audit checked 21,118 declarations and found zero
unexpected axioms; only `propext` and `Quot.sound` were permitted. Both F78
executable audit groups passed. The full source/log-hashed receipt is:

```text
report-preparation/run1-request-proofs-20261015/
  full-20261015T184238.844907Z/receipt.json
SHA-256 b97dfe560d1be29db6cac603722abcdb7468e2a231189b5fe680db478296ea6b
```

Both receipts are under historical evidence root H. They are proof-build
evidence, not runtime tests or JVM conformance. At that proof checkpoint the
runtime still matched `27e22bb1e` for `core` and `rules`. That statement does not
describe the later measured `4c64c579f` implementation or the selected repair.

The superseded twelve-family shape extension passed its selected build and audit:

```text
cd spec/lean && lake build ApSpec.Run1InitialShapes
cd spec/lean && lake env lean <receipt-directory>/audit.lean
```

That build completed 5 jobs. Its audit checked 508 declarations, found zero
unexpected axiom dependencies, and passed the twelve-family executable supply
and existing AP start-fact certificates. Allowed dependencies were only `propext`
and `Quot.sound`. Its source/log-hashed receipt is:

```text
report-preparation/run1-initial-shapes-20261015/
  lean-20261015T192441.939809Z/receipt.json
SHA-256 db6afa4377f55b0b396b8b70eba4daa308d8dd6dab018271bc68e3d3be025b11
```

The superseded twelve-family full-library check passed with both extensions: `lake build`
completed 125 jobs, and `lake env lean audit.lean` checked 21,810 declarations
with zero unexpected axioms. Only `propext` and `Quot.sound` were permitted.
All executable audit groups passed, including the shared-base and twelve-family
initial-supply controls. Its source/log-hashed receipt is under evidence root H:

```text
report-preparation/run1-request-shapes-precomputed-20261015/
  full-20261015T193531.045382Z/receipt.json
SHA-256 1334f8b247c8e94cf52186517b4a799c2efbffbdef89effb74d80ee5b8b36fa1
```

The statement map is below. Names are in the corresponding `ApSpec` namespace.
The first two receipts precede the shape and shared-base extensions; the later
receipts validate their earlier twelve-family sources only. The ten-family
receipts at the start of this section validate the corrected no-ANY models.
None of these checks proves that current generic factories obey the F78
boundary, that the frontend domain includes every delivered static coordinate,
or that a virtual base physically shares its payloads. The later runtime and
project evidence must be evaluated under their own source bindings.

| Obligation | Lean statements and their actual scope |
| --- | --- |
| Exact slot matcher | `Run1Requests.project_exists_iff`, `projector_ignores_field_E`, `projector_keeps_mark`; finite leaf projector, not Kotlin tree traversal |
| Complete initial grammar | `Run1InitialShapes.classification_exact`, `legal_empty_E`, `legal_no_any`, `legalAP_no_any`, `legal_no_must`, `legal_no_excluded_initial_mark`, `zero_base_canonical`, `no_concrete_empty_static_root`, `no_abstract_class`, `no_nonstatic_field_initial`, `no_static_deep_initial`; checked ten-family/no-ANY laws, not current generic InitialAp validation |
| Actual initial supply | `request_projector_legal`, `request_projector_no_any`, `direct_class_legal`, `class_projector_legal`, `class_projector_no_any`, `every_emitted_initial_legal`, `every_emitted_no_any`, `request_emission_exact`, `request_requires_matching_concrete_supplier`, `incoming_answers_legal`, `FLOW_never_answers`, `every_shape_has_supply_witness`; actual finite answer factories, not mere shape permission |
| Additive selected abstract slot and request-gated concrete mark | `supplier_starts_no_any`, `abstract_start_additive`, `no_request_abstract_only`, `nonexact_field_additive`, `nonexact_field_without_request`, `nonexact_field_with_request`; actual supplier and full logical Question membership; selected slot does not disappear when T is unrequested |
| Start facts | `started_layer`, `started_tail`, `started_geometry`, `started_zero_is_reach`, `started_FLOW_exactly_abstract`; existing scalar AP start adapter, not the packed Facts codec |
| Nonexact STAR answer coverage and limit | `Run1Requests.nonexact_field_chain`, `nonexact_class_chain`, `concrete_chain_any_start_equal`, `concrete_chain_any_same_coverage`, `concrete_chain_covers_exact_and_any`; local projection/start/coverage. `concrete_chain_any_same_summary_relation` and `concrete_chain_any_same_reversed_relation` are ordinary denotational location-pair equalities, not annotated native reader or driver refinement. `replacement_changes_syntactic_summary_guard` proves that the syntactic caller applicability can differ |
| 1: producer inventory | `effective_leaf_collected`, `excluded_target_member_collected`, `zero_and_removeAll_not_mark_producers`, `generated_targets_keep_structure`; effective finite form grammar, with frontend extraction as a bridge |
| 2: static plan | `closure_seed`, `closure_call`, `closure_least`, `recursive_call_coverage`, `unchanged_static_plan`, `wide_activation_complete`, `effective_static_dispatch`; executable finite graph/activation |
| 3: dynamic residual | `static_dynamic_cover`, `changed_slot_retains_dynamic`, `changed_static_origin`; full logical owner and original slot |
| 4: root safety | `Run1RequestsSafety.primitive_footprint_collected`, `reachable_primitive_footprint`, `primitive_frame`, `frame_identity`, `transitive_plan_frame`, `transitive_plan_composition`, `root_field_composition`, `returned_composition_exact`, `no_return_no_frame`; executable primitive traces tied to effective bodies and resolved call paths, with an explicit return guard |
| 5: broad/class input | `Run1Requests.broad_root_projects`, `broad_class_projects`, `exact_class_not_field`, `direct_class_preserved`, `broad_root_class_answer`, `broad_class_answer_starts_demand`, `static_root_unconditional`; `Run1RequestsSafety.finite_partition_exact`, `partition_disjoint`, `broad_partition`; scalar locations, not a full must/layer codec theorem |
| 6: summary safety | `cleaner_removes_target`, `cleaner_preserves_sibling`, `relation_composition`, `native_and_opposite_read_preserved`, `persisted_return_guard_preserved`; same finite relation/read view, not production RecordStore refinement |
| 7: arrival | `Run1RequestProtocol.answer_witness`, `both_arrival_orders`, `registration_race`; computed answer witness and logical linearization order |
| 8: concrete initial | `Run1Requests.concrete_never_requests`, `special_root_never_requests`; origin dispatch, with primitive concrete evaluation proved in section 7 |
| 9: exclusions | `Run1RequestProtocol.excluded_has_emitted_need`, `excludedNeedWitness`, `derived_flow_exclusion_erasure`, `excluded_forwarding_same_union`; executable normal-origin creator/transfer/call/join induction and resulting union law, not an assumed provenance predicate |
| Batching | `collect_holds`, `commit_holds`, `commit_membership`, `commit_temporary_empty`, `fresh_not_admitted`, `second_commit_no_notification`, `filter_destination_safe`, `two_batches_one_barrier`, `barrier_placement_same_union`, `post_commit_collect_uses_new_temporary` |
| Prefix/completion | `trace_encodes`, `every_prefix_retains`, `completed_admits_all`, `completion_empty_owners`; finite logical cells and explicit owner predicates, not runtime scheduler termination |
| Shared precomputed base | `Run1PrecomputedRequests.build_base_exact`, `initialise_shares_base`, `initial_read_is_precomputed`, `initialise_no_base_copies`, `collect_no_base_copies`, `commit_preserves_base_reference`, `commit_no_base_copies`, `trace_no_base_copies`, `trace_preserves_base_reference`; immutable pool reference and derived disjoint logical residuals, not JVM object-layout proof |
| No initial base notifications | `initial_commit_no_notification`, `notification_never_precomputed`, `new_input_sees_precomputed`, `every_input_after_binding_sees_base`, `no_precomputed_delivery_after_any_trace`; initialized-before-input lifecycle |
| No unchanged precomputed FLOW propagation | `caller_plan_inherits`, `frozen_slots_monotone`, `caller_frozen_base_inherits`, `frozen_batch_no_send`; actual frozen descriptors construct both inherited slots and marks without an arbitrary runtime-active premise. Helpers `caller_activated_inherits`, `identical_static_route_already_admitted`, `unchanged_precomputed_batch_no_send` expose their additional slot premise. Neither case covers changed origins |
| Residual collection | `precomputed_never_offered`, `entirely_precomputed_sends_nothing`, `fresh_never_precomputed`, `planned_membership_persists`, `commit_read_exact`, `changed_origin_not_suppressed`; effective B∪D read and current-R filter |

The executable Lean witnesses include
`Run1Requests.Examples.recursive_seed_reaches_caller`,
`class_exact_is_not_a_field_answer`,
`wide_cleaner_covers_unrelated_incoming_field`, and
`Run1RequestsSafety.Counterexamples.raw_root_cleaner_leak`,
`raw_root_write_loses_sibling`, `constructive_frame_repairs_both`,
`changed_g_to_f_keeps_original_input_owner`. The protocol's
`batch_vector_members` computes an actual merged set and empty replacement.

Each executable control must test the actual result below, not assume coverage:

| Control | Required witness/result |
| --- | --- |
| Mark T at exact non-static root; mark U below it | T gives exact root, U gives STAR root; no invented mark |
| Root/class ANY T; exact class point T | Broad field answer stays may; exact class point is direct and does not taint its fields |
| f/T cleaner; g/T and g/U input | f/T gone; g/T and g/U retained; unrestricted root composition fails |
| Strong write f with coarse root E{C}; g/T input | Identity frame or owned g preserves g; raw root read fails |
| Base-wide F75 from a cleaner on f | A relevant g analysis registers g/T; concrete g/T survives a disjoint clean |
| g→f, argument→static, static→local | Request targets the original slot, not the checked position |
| Source/sink NNF AND/OR, repeated canonical leaves | All required marks requested; complete bindings/proofs remain separate |
| Exact/ANY supplier; E changed; FLOW mx contains T | New matcher ignores E/mx, FLOW never answers, missing concrete T never appears |
| Generic target exclusion | Same-origin creator need, also for a legal hand-built edge; carried summary mx reuses that obligation |
| Diamond and recursive call graph | Closure contains exactly local members reachable by finite paths |
| Request first/input first; subscribe and update during queued flush | At least E or V handles each pair; duplicates do not restart initials |
| T answered, then a new caller/supplier U | R remains active; U independently answers if requested |
| Request-only method; final flush; cancellation | Counted ownership prevents early COMPLETE; cancelled run makes no closure claim |
| Native and opposite-direction persisted summary read | Same component ownership/frame, ordinary full guards preserved |
| Every closed initial family, including T=ZERO on a nonzero base | Exactly the ten rows in section 1.1; no ANY/ANY_TAINT initial and no extra initial from seeds, GEN or native results |
| Supplied field ANY/must with R lacking/containing T | Abstract field STAR in both cases; concrete field STAR only with actual matched T and admitted request; starts ANY/DEMAND |
| Exact versus nonexact class supplier | Direct exact stays EXACT; metadata-gated nonexact class becomes STAR; no class request key |
| First limit 1, class uncounted, field counted | Accepted cut route yields root/C/C.f; generic deeper/manual input is not silently truncated or certified |
| Shared precomputed B before subscriber/input | No copy into P/D, no initial notification wave; both fresh input arms read effective R |
| Unchanged versus changed caller origin for a B member | Unchanged route subtracts to empty; C.g→C.f and non-static origins retain missing residual interests |
| One origin f sends packed FLOW f+g; callee B[f]={T}, B[g]={U} | Skip only f/T segment; g/U still routes f/U unless target effectiveR already contains it |

The theorem family deliberately stops before physical AP tree/mask refinement,
whole-driver source/seed equality, pooled ND confirmation, and fixed-project
no-FN/precision/resource acceptance. Those are separate implementation and
validation gates. In particular, equal request closure does not imply equal
timeout prefixes or repaired findings in the measured runtime.
