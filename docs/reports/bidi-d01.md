# D01 support, confirmation and reports

The report accepts only complete forward runs. Confirmed entries retain their
first confirmed run and every witness from that run. The latest complete
forward run replaces the remaining demand set. An incomplete or backward
run leaves both sets unchanged. The builder stages both maps and commits one
state after all enumeration and allocation. Reports, entries and demand
snapshots own unmodifiable lists. They retain each complete witness object.

The first report API gate fails before implementation
(`/tmp/bidi-d01-report-api-red.log`). All 12 report tests then pass
(`/tmp/bidi-d01-report-first.log`). They cover context/alternative/entry and
literal support shapes, same-shape store unions, final confirmation, demand
replacement/refutation, every incomplete status, backward preservation,
incomplete run 1, empty complete runs, end metadata and snapshot ownership.
A read-only review finds no further report defect.

The joined-run support API gate also fails before implementation
(`/tmp/bidi-d01-support-api-red.log`). Those tests use actual method handlers,
the run manager and retained forward links after a successful join. They
pin complete caller and ordered literal headers before confirmation.

The plan records the proposal guard corrections before implementation:
support reads only COMPLETE forward results; report updates ignore incomplete
and backward runs; public support queries require nonempty premise members;
site, member, link and witness scans check cancellation. Run 1 starts at
positive limit 1. No limit-zero run is admitted.

Support computes a least fixed point from root zero over real retained forward
links. It checks both normal layers and complete caller headers. All members
must have suppliers at one call. Suppliers can have different supported caller
edges there. Each member must pass the exact or restricted must/E rule.
Confirmation reads only the sink-edge layers and joint support; a normal must
sink fact needs no extra exact-tail check.

The combined missing-API gate fails before Support is added
(`/tmp/bidi-d01-combined-api-red.log`). The first implementation gate finds a
missing `members` extension import (`/tmp/bidi-d01-focused-first.log`). After
that import is added, all 40 focused tests pass in 3 seconds
(`/tmp/bidi-d01-focused-second.log`). The 18 primitive support tests include
an independent exact least-fixed-point reference and actor-order permutations,
zero chains, unsupported cycles, joint support, complete context/entry/call
identity, both layer guards, FLOW/may rejection, witness union and cancellation.
Five must support tests cover 20 independent exclusion/mark supplier cells in
both run 1 and restricted run 3, and normal must sink confirmation. Five real
run tests confirm frozen links, same-call conjunctions, separate-call rejection,
alternative/context preservation and a demand cleaner result.

The clean common gate passes 1,128 tests in 117 classes, with no failure, error
or skip. JVM and Go test sources, JVM SAST and application test sources compile.
The command runs from `core`:

```bash
./gradlew --continue \
  :opentaint-dataflow-core:opentaint-dataflow:cleanTest \
  :opentaint-dataflow-core:opentaint-dataflow:test \
  :opentaint-dataflow-core:opentaint-jvm-dataflow:compileTestKotlin \
  :opentaint-dataflow-core:opentaint-go-dataflow:compileTestKotlin \
  :opentaint-jvm-sast-dataflow:compileTestKotlin :compileTestKotlin --console=plain
```

The gate takes 36 seconds (`/tmp/bidi-d01-full-gate.log`). All 980 new common
annotated tests have void returns and appear in fresh XML. The audit log is
`/tmp/bidi-d01-common-discovery.txt`; XML is saved under
`/tmp/bidi-d01/opentaint-dataflow/xml`. `git diff --check` passes. No new Lean
clarification is needed. D01 is complete.
