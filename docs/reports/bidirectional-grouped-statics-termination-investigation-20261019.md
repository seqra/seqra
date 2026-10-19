# Run-1 termination investigation

Date: 2026-10-19. Runtime: `ff2966455da2e5ffd0db655a1ff06c5954ed3c8a`.
The documentation HEAD at the start of this investigation was `c21c0d8cd`.
Runtime code has not changed. This investigation adds diagnostic tests.

**A termination defect is confirmed in the new support logic.**
The real method actor can process the same fact with one mark group,
under the same initial premise, and keep generating support-only work.
The fact set has reached its fixed point. The support expression has not.
This defect does not require static fields or multiple full-mark aliases.

Grouping also permits more full rule proofs in some cases. Thus fewer
runtime mark values do not establish less total analyzer work.
The investigation separates this finite increase from the termination bug.

## 1. Current project status and scope

No full project was rerun for this investigation. The latest project
measurements remain those in the [performance report](bidirectional-grouped-statics-performance-20261019.md):

| Project | Latest reached / completed / accepted | Run-1 engine | Result |
| --- | --- | ---: | --- |
| OWASP | run-1 / none / none | 908.767 s | TIMEOUT |
| conductor | run-1 / none / none | 986.838 s | TIMEOUT |
| ThingsBoard | run-1 / none / none | 700.235 s | TIMEOUT |
| Stirling-PDF | run-1 / none / none | 1,026.865 s | TIMEOUT |

The target remains a complete run-1 engine within 60 seconds.
The earlier pre-bottom-up core `a80d5c580` completed run-1 on some projects.
Those old times are historical observations. They are not a fresh
controlled project comparison for this investigation.

The new evidence is a bounded execution of production common-core code,
plus source comparison with `a80d5c580` and the existing project profiles.
It identifies a real engine defect. It does not identify the exact
method and loop responsible for each named project's timeout.

## 2. Executed production-core witness

The test models this transfer pattern:

```text
x receives mark T
y = copy_mark_T(x)
while (choice):
    y = copy_mark_T(y)
```

`copy_mark_T` is a positive marked transfer, such as a selected CopyMark
rule. It is not an ordinary unmarked assignment.
Both transfers use the same group `G = owner(T)` in run-1.
There is one loop-header AP leaf: `(local y, exact, G, DEMAND)`.
Field exclusions are empty. The field limit is 1.

[Run1LoopTerminationInvestigationTest](../../core/opentaint-dataflow-core/opentaint-dataflow/src/test/kotlin/org/opentaint/dataflow/bidi/Run1LoopTerminationInvestigationTest.kt)
uses the real `RunMethodAnalyzer`, form application, AP operations,
grounding, method edge storage and work queue.
It does not inject the problematic guard expressions.
The production transfer path creates them.
Three cases reproduce growth:

| Case | Loop visits | Loop-header fact leaves / premises / groups | Distinct semantic guard rows | Reachable support objects, first to tenth visit | Actor complete? |
| --- | ---: | --- | ---: | --- | --- |
| ZERO-origin conditional sources | 10 | 1 / 1 / 1 | 2 | 1, 4, 7, …, 28 | No |
| ZERO-origin positive CopyMark edges | 10 | 1 / 1 / 1 | 2 | 1, 4, 7, …, 28 | No |
| Legal nonzero exact initial, CopyMark edges | 10 | 1 / 1 / 1 | 2 | 2, 6, 10, …, 38 | No |

The second case has no source predicate, ND join, call routing or static
service participation. The third has fixed-image certificates as well as
logical support. The initial shape is admitted by the current run-1 grammar.
All three keep the same accepted AP leaf and premise at the loop header.
An independent Boolean oracle checks that all four valuations of the
two guard rows give the same logical and certified result before and
after growth. Separate store tests check the production eligibility
evaluator with complete input bindings.

Two controls close in ten actor steps: remove the repeated marked
condition, or explicitly store a TRUE cofactor for the same loop-header
fact through the ordinary changed-value path.
The latter is an owned TRUE cofactor. It is not a claim that every
independent transient TRUE path will close the loop.

These tests deliberately stop after a small number of visits.
They characterize the current defect. They are not passing assertions of
the desired termination behavior. A future fix must change these cases
into bounded-quiescence regressions.

## 3. The feedback path

Let `A` be the accepted guard for the first marked copy.
Let `B` be the accepted guard for the copy inside the loop.
These are two stable semantic rows. There is no new mark or rule occurrence
on the next trip around the loop.

The production path is:

```kotlin
// EngineAlgebra.applyEdgeAt, or RunMethodLocalServices.sourcePredicateAt
outgoingSupport = inheritedSupport.and(acceptedGuardB)

// MethodEdgeStore.addSupported
val factDelta = add(node, premise, facts) // null: the AP value is already stored
val after = before.or(outgoingSupport)
if (before != after) {
    rows[leaf] = after
    emit(theSameFacts, after)            // accepted even with no factDelta
}

// RunMethodAnalyzer.emitCovered -> push -> process
enqueue(theSamePremise, theSameNode, theSameFacts, after)
```

Relevant code:
[EngineAlgebra](../../core/opentaint-dataflow-core/opentaint-dataflow/src/main/kotlin/org/opentaint/dataflow/bidi/engine/EngineAlgebra.kt),
[RunMethodLocalServices](../../core/opentaint-dataflow-core/opentaint-dataflow/src/main/kotlin/org/opentaint/dataflow/bidi/engine/RunMethodLocalServices.kt),
[MethodEdgeStore](../../core/opentaint-dataflow-core/opentaint-dataflow/src/main/kotlin/org/opentaint/dataflow/bidi/store/MethodEdges.kt),
[RunMethodAnalyzer](../../core/opentaint-dataflow-core/opentaint-dataflow/src/main/kotlin/org/opentaint/dataflow/bidi/engine/RunMethodAnalyzer.kt).

At the loop header this gives:

```text
S0 = A
S1 = A OR (A AND B)
S2 = S1 OR (S1 AND B)
S(n+1) = S(n) OR (S(n) AND B)
```

For every valuation, `S(n)` means `A`.
However, [GroundingSupport](../../core/opentaint-dataflow-core/opentaint-dataflow/src/main/kotlin/org/opentaint/dataflow/bidi/engine/Run1Grounding.kt)
compares expression structure. Its containment checks only descend through
the same connective. OR membership does not recognize that `S AND B`
adds nothing to `S`.
The constructors therefore keep creating larger terms.
Structural equality reports a change and the store emits again.

This is not only a large finite number of facts.
For the displayed recurrence, the new AND term is larger than `S`.
It cannot equal any OR-descendant of `S`.
The existing membership checks cannot suppress the new OR term.
The same constructor branches apply again on the next trip.
There is no syntactic fixed point, even over two fixed guard atoms.
The bounded core execution confirms the transfer-to-store adapter for
the first ten visits. It does not run an infinite execution.

A constructive Lean model also checks the selected smart-constructor
fragment with two stable row atoms. It proves, for every `n`, that the
smart constructors produce the displayed recurrence, its Boolean value
remains `A`, and its syntax height is `2*n`.
Its unfolded size is `4*2^n - 3`.
Thus there is no syntactic fixed point in this restricted model.
All 22 theorems and 12 executable controls pass. The axiom audit has no
`Classical.choice` or `sorryAx`; it uses only the recorded foundational
`propext` and `Quot.sound` dependencies where needed.
Constants, certificate composition, Kotlin adapters and project execution
are outside that formal fragment. The actor and certificate tests supply
separate implementation evidence; they are not a full JVM refinement proof.

The representation shares earlier objects, so reachable DAG size can grow
linearly while an unfolded tree grows exponentially.
Each current expression is finite and acyclic. The work-list iteration
keeps creating larger expressions instead of reaching semantic closure.
Repeated equality, hashing and containment can revisit shared parts.
Faster equality can reduce that cost, but cannot close this recurrence.
Hash interning alone also cannot identify these different syntax trees.

## 4. Difference from the earlier core

At the same method-storage seam, `a80d5c580` used only AP deltas:

```kotlin
edges.add(successor, premise, facts)?.let { push(it) }
```

An identical fact returned no delta and caused no further stored work.
The new core must retain real eligibility and certificate changes for
direct static grounding. It now permits a support-only delta even when
the AP value is unchanged.
That is a new convergence dimension. The mark-group reduction does not
bound it. The current constructors do not provide a finite semantic
representation for this dimension.

The rule proof DAG and the grounding support graph are different objects.
The former already existed in the baseline. The new `GroundingSupport`
expressions carry accepted input eligibility and fixed-image evidence
through ordinary transfers. The confirmed cycle is in that new graph.
`trackFactsReachAnalysisEnd` remains empty and is not needed by the witness.

The method queue also controls publication and request barriers.
A method that keeps generating support-only work cannot reach its
queue-empty barrier. A work quantum can yield to the runner without
making the method complete. Thus one counted work reservation can remain
active while many facts are processed again. Queue size alone can hide
the problem.

## 5. A separate finite increase from group matching

[GroupPredicateWorkInvestigationTest](../../core/opentaint-dataflow-core/opentaint-dataflow/src/test/kotlin/org/opentaint/dataflow/bidi/GroupPredicateWorkInvestigationTest.kt)
uses the actual predicate dispatcher and proof store for:

```text
(P0(T0) OR P0(U0)) AND ... AND (Pk(Tk) OR Pk(Uk))
owner(Ti) = owner(Ui) = G
```

Each position receives only `Ti` in the explicit FULL comparison.
GROUP receives one group value for that position.
GROUP visits both original full aliases and retains their proof identities.

| Positions | FULL proofs | GROUP proofs | Groups | New proofs after identical replay |
| --- | ---: | ---: | ---: | ---: |
| 3 | 1 | 8 | 1 | 0 |
| 4 | 1 | 16 | 1 | 0 |

This is a primitive mode comparison, not a rerun of the historical engine.
The work reaches a finite fixed point. It shows why the mark count alone
does not prove a workload reduction. Full proof alternatives and source
actions remain distinct under the current specification.
Their multiplicity in the named projects is not measured here.

## 6. Relation to the project timeouts

The existing profiles fit the guard-cost mechanism for conductor and
Stirling-PDF. Conductor's retained final 174-second run-1 suffix has
87.70% of sampled allocation weight on strict support-class stacks.
Its sampled Trie-equality allocations have `GroundingRequirement.equals`
as an outward engine parent. Stirling-PDF has 52.76% support-class
allocation weight and 99.69% support-class inclusive CPU samples in its
near-limit window. Some recorded stacks have 1,024 nested `Or.equals`
frames. These shares are not exclusive CPU totals or retained heap bytes.

This is evidence that support comparison is a major observed cost.
The profiles do not contain the semantic keys needed to prove that a
specific project loop is the same repeated-state witness.
OWASP and ThingsBoard also have large normalization and cleaner costs.
It would be incorrect to attribute all four timeouts solely to this bug.

The source audit found finite admission gates on static group membership,
read delivery bits and FLOW relation changes. It did not find an independent
autonomous static-service cycle after those inputs stabilize.
Those services can still repeat work when support syntax changes.
This is a scoped audit, not a proof that no other termination defect exists.

## 7. Required correction

Restore a finite semantic progress rule for grounding support before
optimizing its comparison speed.
For Boolean support components, an offered guard `I` adds nothing to an
existing guard `S` when `I` implies `S`.
`I = S AND B` is exactly such a case.
Real new alternatives must still propagate without an AP delta.

Logical support and fixed-image support need separate checks:

```text
no new support iff
  incoming.logical implies stored.logical
  AND incoming.fixed implies stored.fixed
```

Do not apply whole-support Boolean absorption to `WithFixed`.
Plain TRUE has no fixed-image certificate. Receiving a TRUE certificate
can be a real change even when logical support is already TRUE.
The diagnostic tests retain this negative control.

Exact ZERO checks and local absorption can close specific cases.
The executed nonzero/certificate case shows why ZERO folding alone is
insufficient. A general termination claim needs a finite support domain
or a finite dependency graph and a proved monotone solving procedure.
The design must avoid unfolding derivation history around CFG loops.
Local simplifications and faster equality alone do not establish that
general claim. No runtime fix is made in this investigation.

## 8. Validation and evidence

The final focused pass has **10 tests, zero failures, errors or skips**:
five actor/CFG diagnostics, four support/store controls and one predicate
test with two widths. The two terminating actor controls take ten steps.
The growing cases stop at their explicit observation bound.
This is separate from the earlier 2,039-test validation of the runtime.

```bash
cd core
JAVA_HOME=/usr/lib/jvm/temurin-17-jdk-amd64 OPENTAINT_TEST_BIDI=true ./gradlew \
  :opentaint-dataflow-core:opentaint-dataflow:test \
  --tests '*Run1*TerminationInvestigationTest' \
  --tests '*GroupPredicateWorkInvestigationTest' \
  -PincludeBidi --offline --max-workers=2 --console=plain
```

External evidence:

```text
/drive-testcomp/opentaint-go-rules/grouped-statics-termination-20261019
```

`final-test-summary.json` has SHA-256
`1b2fbd78ca483019e9c4c110c33cbf67e977717a9cefd1339f2016b90123d23b`.
The folder has copied XML results, source hashes, the final test log,
source comparison and independent reviews.
`formal/GroundingSupportGrowth.lean` and `formal/checked.json` contain
the scoped Lean witness and audit. The model source SHA-256 is
`203b95b0195eefdac1f68aef09a4f73b858321cf4e6e7f55114b9f9dd679669b`.
The first attempted control incorrectly assumed that an independent
transient TRUE path creates an owned TRUE cofactor. That attempt did not
close; its owned test worker was stopped after a thread dump with 508
`Or.equals` frames. The final control explicitly stores TRUE and is bounded.
All attempt logs remain available. This was not a project OOM or a new
project performance measurement.
