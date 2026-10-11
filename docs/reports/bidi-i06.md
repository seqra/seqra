# I06 method boundaries, cache and Spring forms

The JVM facade now supplies cached statement, call, entry and exit forms.
Each method owns its graphs and alias data. Each method key owns its call
and entry forms. Both directions keep stable form identities. Method
entries check the complete final boundary triple. The graph adds an
exceptional exit edge only where the original graph cannot reach either
exit. It preserves catch timing and omits implicit class initializers.

Boundary forms apply context filters, entry sources and sinks, both-exit
rules and exceptional result coordinates. Only the normal forward exit
has the summary metadata. Entry-mark removal covers all eligible leaves.
The lambda snapshot owns all three collection levels. Detached primitive
entries prevent mutation through fastutil entry views while keeping hash
lookup. The unresolved-call observer now calls the existing tracker.

The Spring dispatcher saves its registry fields, calls the whole-static
cleaner and restores the fields. The generated cleanup method owns its
cleaner list. The existing fact-based Spring provider remains available.
These tests cover forms and pre-cut transfer. The complete two-controller
driver test remains assigned to J03.

## Tests and corrections

Tests preceded the implementation. The focused gate runs 22 cache tests,
12 boundary tests, seven graph tests, four real-form scans and 12 Spring
tests. It checks both graph directions, context identity, ownership,
actual alias paths, native cleanup, boundary rules and source/end forms.
The graph tests use an independent reachability oracle. A separate Java
17 sample supplies real StringConcatFactory input; existing Java 8 samples
keep their compiler setting.

The plan records each correction before the edit. Fixture corrections
use raw field declaration types, actual callee coordinates and the public
JIR list API. A Spring diagnostic established the raw nested-type mismatch;
the filters and complete restored-fact assertion remain in place. Both
primitive and generic lambda entry mutation regressions failed before
the detached-entry fix and pass after it. No new Lean proof was required.

Focused logs are `/tmp/bidi-i06-jvm-focused-6.log` and
`/tmp/bidi-i06-spring-focused-4.log`. The snapshot regression log is
`/tmp/bidi-i06-lambda-entry-red-2.log`.

## Final gate

The clean common/JVM and application test gate passed in 3 minutes,
4 seconds (`/tmp/bidi-i06-full-gate.log`). Go test sources and JVM SAST
sources also compile.

| Suite | Classes | Tests | Failures / errors / skips |
|---|---:|---:|---|
| Common dataflow | 72 | 755 | 0 / 0 / 0 |
| JVM dataflow | 20 | 331 | 0 / 0 / 0 |
| Application | 72 | 782 | 0 / 0 / 9 |

The nine application skips match the existing baseline. All 607 new
common, 217 new JVM and 12 new application annotated tests have void JVM
returns and appear in fresh XML. Discovery logs are
`/tmp/bidi-i06-{common,jvm,app}-discovery.txt`; XML copies are under
`/tmp/bidi-i06/{opentaint-dataflow,opentaint-jvm-dataflow,application}/xml`.
`git diff --check` passes. I06 is complete.
