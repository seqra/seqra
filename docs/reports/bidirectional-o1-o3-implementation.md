# O1–O3 implementation and test record

Status: implementation in progress. This record does not claim complete optimization or complete project scans.
The selected scope is the [implementation plan](../design/bidirectional-o1-o3-implementation-plan.md).
IR storage and O4–O12 are outside this work.

## Control and evidence

The control revision is `2d83c92694072217fec6f9136e38c5d6232bfa5a`. The existing working patch has
SHA-256 `f1343858898f2c43850dbc5cb691fc3f629affe1b8a73119c0c87bdce6811c38`.
The control patch and manifest are in `/drive-testcomp/opentaint-go-rules/bidi-o1-o3-20261012`.
That directory also holds command logs and copied test XML. Existing diagnostic changes were preserved.

Remote main was checked with `git ls-remote` on 12 October 2026. Its head is
`f945f92c5ae554ff4f5fc7696ee5a269210478bb`. The fresh baseline scans will use that revision.
Previous project scan results do not prove the result of this implementation.

The control test command passed: 1,226 common tests and 247 selected JVM tests, with no failures or skipped tests.
The first complete common test run after integration found one failure in a negative-control fixture.
The fixture changed `MethodForms.call` through Kotlin delegation. The new delegated `withCall` method bypassed
that changed getter. The fixture now also changes `withCall`. Its original assertion was kept and passed.

## Verified work

The focused foundation command passed 61 tests. It covered signed folding, graph compilation, support binding,
logical keys, managed cleanup, cache callbacks, compact collections and indexes. These results precede later
integration changes; the complete suites must run again on the final implementation revision.

The focused engine integration command passed the predicate engine, predicate store, handoff and current-sequence
tests. It checks these effects through the real actor services:

- AND waits for every required leaf. Repeated leaves do not create a second premise.
- A source leaf can have both ordinary and DEMAND backward transfers.
- A form can be cleared and rebuilt between inputs without losing standing source support.
- Distinct rule occurrences keep separate sink proof scopes.
- Growing sink facts update existing support.
- A full-condition handoff handle resolves every positive OR branch within a witnessed context.
- Rebuilt end-fact triggers fire once for the logical rule occurrence.

The support store uses shared proof records and persistent binding maps. Its deep-chain test checks 32,768 nodes,
growth after an independent rebuild, and the absence of a retained compiled predicate graph.
The compiler stress test stores 193 nodes for an AND of 64 two-way OR conditions. It builds no cube inventory.

JVM production sources compiled after the NNF migration. A source search found no `explodeToDNF`, `cubes` or
Cartesian-product call in the JVM bidi production package. The complete JVM test gate is still pending.

## Remaining gates

Complete the independent reference evaluator and JVM fixture migration. Run the full common and JVM suites,
affected consumer compilation, application tests and query tests in both bidi modes. Finish the specification,
cache statistics and ownership checks. Commit the implementation before the fixed project runs.

Run OWASP, Conductor, ThingsBoard and Stirling-PDF through `runProjectAnalyzer`. Keep the selected configuration,
heaps and time limits. For each project, record strict finding-location comparison, each iteration time,
OOM/timeout status, CPU profiles and any OOM memory snapshot. Explain each missing baseline finding and each
remaining large memory owner. No fresh project scan has completed for this implementation yet.
