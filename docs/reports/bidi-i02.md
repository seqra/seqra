# I02 shared micro-edge builder

`MicroEdgeBuilder` now builds forward statement, stage and GEN forms.
Moves, reads and writes follow interpreter §2. Strong writes keep each
proper prefix with its next-accessor exclusion; weak writes keep the whole
base. A read keeps its source except when it replaces that same base. It
adds no exclusion or refinement form. Empty strong writes replace the root.
Aliases receive complete value-transfer paths and remain untouched.

The builder preserves insertion order, deduplicates equal plain edges and
retains conjunction object identities. It combines operand and result
filters in their own arrival order. Stage construction touches every edge
and literal endpoint; GEN retains plain edges alone. Returned summaries
own their collection containers, and helpers own all paths they create.
Injected edges and conjunctions keep the I01 producer ownership contract.

`fieldCleanStep` creates a fresh temporary, an ordinary read, a root
cleaner and a strong write with ordinary filters and aliases. Reversal
shares the temporary and cleaner. The forms walk projects the temporary
after write-back.

## Tests before implementation

Missing-API failures preceded production in
`/tmp/bidi-i02-builder-red.log` and `/tmp/bidi-i02-all-red.log`.
The review found three object-identity assertions for inline `TaintMark`
values. The plan was updated before changing them to value equality.
Identity checks remain for actual objects. No production defect, behavior
question or new Lean proof was found.

All 41 focused tests passed in two seconds (`/tmp/bidi-i02-focused.log`).

| Test class | Tests | Checks |
|---|---:|---|
| MicroEdgeBuilderTest | 17 | Exact move/kill/self/read/strong/weak/root/static/alias forms, source and zero keeps, deduplication, filter order, stage/GEN distinctions, owned paths and 32,768-accessor aliases. |
| FieldCleanStepTest | 7 | Fresh/shared temporary geometry, guards, 540 concrete nested geometry cases, 18 must/may reach cases, mark selection, F75 candidate split, ordinary filters and final temporary projection. |
| BuilderTransferVectorsTest | 17 | Independent complete conclusions for every builder row, per-path and tree agreement, first-accessor must exclusions, static class-aware positive cuts and requests, aliases, contributor union and composed statement cuts. |

The next JVM step has a dependency-order correction in the plan.
`JIRStatementForms` takes its direct inputs in I03; I06 wires the full
`JIRInterpreter` after rule, call and method-entry classes exist. No
placeholder behavior is needed.

## Final gate

The clean gate passed 748 common tests in 71 classes, with no failures,
errors or skips, in 31 seconds (`/tmp/bidi-i02-final-gate.log`). JVM and Go
test compilation, JVM SAST compilation and application compilation pass.
All 600 new common annotated tests have void JVM return types and occur
in their XML (`/tmp/bidi-i02-discovery.txt`). XML copies are in
`/tmp/bidi-i02/final-common/xml`. `git diff --check` passes. I02 is complete.
