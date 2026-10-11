# J03: JVM acceptance through the optional entry

All enabled phase-3 acceptance cases pass through the new entry. The clean
closing gate passes 1,226 common, 361 JVM, 1,313 Go, 941 application and
68 selected query-language tests, with no failures or errors. Application
skips are the nine legacy skips and the inherited disabled nested-collection
case. Query skips are its two existing disabled cases.

## Entry and observations

AnalysisTest defaults to its old entry. Additive bidi subclasses use the same
rule loading, graph, timeout and real joined/released prescan. The bidi sample
unit also admits generated lambda methods. Both paths use the same Params
and graph; legacy tests retain their original configurations. The query test
helpers have their own nullable policy because ExampleTest uses an anonymous
TaintAnalyzer. Its outer graph reference is qualified to avoid hiding it with
the new protected graph cache. No project or CLI policy is added.

JIRBidiAnalysis keeps its public seven-argument constructor. Its internal
factory constructor lets the test bridge observe actual IterationDriver,
RunManager, handlers and the existing barrier memory guard. It captures
initials/links/requests before joined actor release, then copies raw/selected
summaries, native source hits, persisted records and post-confirmation full
rule/site/context witness headers. It retains exact tails, layers, E, mark
exclusions and ordered literals. Actual actor/result counters agree with the
actual Frontier values. It does not fabricate a run result or recompute
support/hand-off. Terminal Next.End exposes no outgoing stores; those two
snapshot fields are null, with actual terminal counts kept in Frontier.

## Enabled acceptance

| Suite | Cases | Scope |
|---|---:|---|
| JIRBidiSequenceAcceptanceTest | 13 | Getter/callee F1-B2-F3, identity/setter first-run stop, native factory and replay, source/pass, subtree/EXACT cleaners, same-call ND, root/deep entry marks, primitive tracking and rejection. |
| BidiTreeCleanerFieldSensitivityAnalysisTest | 16 | Named/base/star cleaners, sibling/no-clean controls, depth 1–3. |
| BidiTreeDeepCleanSummaryAnalysisTest | 11 | Summary returns, helper/nested clean/read and branch control. |
| BidiTreeCleanerDslAnalysisTest | 20 | Mark/path/call-depth matrices, Result actions, returning residual states. |
| BidiTreeCleanerDslControlFlowAnalysisTest | 8 | Order, joins, recursion, loop/call cleaning and exact checkpoint keys. |
| BidiTreeAnyFieldPrimitiveAnalysisTest | 2 | Base-only/whole-array primitive sources. |
| BidiJavaDataFlowReachabilityTest | 40 | Collections, linked lambdas/captures, strings, branches and calls; one inherited skip. |
| BidiKotlinDataFlowReachabilityTest | 27 | Kotlin lambda/inline/collection reachability. |
| BidiMultiReturnDataFlowTest | 11 | All/one/no return, guards, nesting, loops and exceptional negative. |
| SpringDispatcherAcceptanceTest | 2 | Actual generated initializer/dispatcher/cleanup, component registry preservation and ordinary static removal. |
| BidiExampleTest | 5 | Query ND, unification, ellipsis, matching/taint joins and tag unions. |
| ExampleTest | 63 | Null-entry query control; two existing skips. |

The eight inherited application variants exercise 135 cases, including one
skip. With the 13 sequence and two Spring cases, the opted-in application
gate has 150 cases. Spring findings are exactly direct, inside-static and
registry, all CONFIRMED; outside is absent. The registry gets taint through
its real component constructor. These minimal typed controller wrappers
cover dispatch and cleanup, not annotation discovery or full HTTP-wrapper
construction. The existing provider/forms vectors remain their controls.

## Tests first and precise fixture corrections

The API RED fails at missing runBidiAcceptance. After installing the real
harness, all 13 seam RED cases fail at the missing internal driver factory
constructor. The first actual engine gate passes ten cases. All three failed
intermediate assertions are broader than the Java/spec geometry:

- G2 removes entry marks only on zero-premise this/argument leaves at any
  depth. Normal returned must facts remain. The corrected test runs root
  and named-field sources, checks the positive in-method sink, removes the
  argument marks and keeps the exact normal returned fact.
- Object-typed serialized BaseOnly positions include element alternatives.
  Call-sink expansion adds another element alternative. B2 limit 2 cuts
  email[e][e].EXACT to email[e].ANY; F3 emission produces zero, email.EXACT,
  email[e].EXACT and email[e].ANY_TAINT, all with Empty E. The test pins that
  complete set and retains same-site NORMAL link/support checks.
- The Object-return source also emits an element fact. At limit 1, writing
  k gives the extra raw k.ANY(U)/DEMAND. The native factory R1 return leaves
  remain exactly normal must/E{name,k}(T) and k.EXACT(U). The zero-to-zero
  forward record also remains admitted; the return assertion filters RET.

The broader first gate has one enabled cleaner failure. Primitive EXACT
cleaning of a normal root must uses the AP §4.7 PART row: it keeps a DEMAND
residual. The bidi test now observes exact states: unrelated CONFIRMED and
cleaned-any DEMAND. Its legacy null-entry expectation stays unchanged. No
case is disabled to close this difference. All 13 current sequences and
the observed returning-cleaner states then pass. Initial limits remain 1;
all runtime limits are positive, and all exclusion vectors follow the tables.

## Clean gate

Run from core:

```sh
./gradlew --continue :opentaint-dataflow-core:opentaint-dataflow:cleanTest :opentaint-dataflow-core:opentaint-dataflow:test :opentaint-dataflow-core:opentaint-jvm-dataflow:cleanTest :opentaint-dataflow-core:opentaint-jvm-dataflow:test :opentaint-dataflow-core:opentaint-go-dataflow:cleanTest :opentaint-dataflow-core:opentaint-go-dataflow:test :opentaint-jvm-sast-dataflow:compileTestKotlin :cleanTest :test :opentaint-java-querylang:cleanTest :opentaint-java-querylang:test --tests '*ExampleTest' --console=plain
```

Build succeeds in 4 minutes 14 seconds. Log: /tmp/bidi-j03-full-gate.log. Fresh XML copies are under
/tmp/bidi-j03/final/{opentaint-dataflow,opentaint-jvm-dataflow,
opentaint-go-dataflow,application,query}/xml. All 1,078 common, 247 JVM,
36 application and five query new annotated tests compile to void and
occur in fresh XML. The eight inherited bidi variants also have fresh
class XML with the counts above. RED/intermediate logs and XML remain
under /tmp/bidi-j03. git diff --check passes.

The legacy full application suite passes after the helper changes. These
phase-3 suites assert finding identity, report state and bounded run facts;
full trace reconstruction and trace span/path parity remain phase 5. Full
project benchmarks remain phase 4. No new Lean theorem is claimed.
