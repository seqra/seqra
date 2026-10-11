# V2 interpreter integration gate

The form integration gate is complete. Actual conditional static exit
sources at both exits retain the original run-1 static identity and raise
the specified position request. Excluded paths block the request.
Restricted forward and backward controls make no request. All use limit 1.

Matched conditional source and pass plans use the same exact normal
input. The source gives a normal must result; the pass gives a demand may
result. Reversal of the source gives an exact normal requirement.
Reversal of the may pass forces an exact requirement into DEMAND. Full
raw and tree call walks agree at limits 1, 2 and 3.

The real-form scan now checks touched reads, concrete marks, zero rules,
binding tails and paths, exclusion keeps, source targets and generated
backward pass-over stages. It keeps statement, stage, binding and GEN
contexts distinct. Existing tests cover named field actions, primitive
cleaners, pending record paths, type filters and stable cached identities.
Whole-analysis validation remains assigned to the engine, driver and J03.

The plan records the one compile correction before its edit: the public
mark property needs a local value for a cross-module smart cast. No
production refinement or new Lean proof was required.

The focused 42-test gate passes (`/tmp/bidi-v2-focused-2.log`). The clean
common/JVM gate and Go test-source, JVM SAST and application compile
checks pass in 72 seconds (`/tmp/bidi-v2-full-gate.log`).

| Suite | Classes | Tests | Failures / errors / skips |
|---|---:|---:|---|
| Common dataflow | 72 | 755 | 0 / 0 / 0 |
| JVM dataflow | 20 | 334 | 0 / 0 / 0 |

All 607 new common and 220 new JVM annotated tests have void JVM returns
and appear in fresh XML. Discovery logs are
`/tmp/bidi-v2-{common,jvm}-discovery.txt`; XML copies are under
`/tmp/bidi-v2/{opentaint-dataflow,opentaint-jvm-dataflow}/xml`.
`git diff --check` passes.
