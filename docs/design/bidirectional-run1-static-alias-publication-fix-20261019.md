# Publish reached static alias effects

Date: 19 October 2026. Status: implemented correction. The selected
2,063-test gate passes. Fresh project measurements are pending.
Language: ASD-STE100 Simplified Technical English.

## Problem and selected scope

The grouped-static specification requires a reached alias/model write to
publish its static effect. An alias declaration alone must not create a
read, mark interest, write presence or group supply.

Runtime `dac301f2e` has an adapter omission. The JVM lowerers already emit
value transfers to static aliases, but they do not attach a
`StaticOperation.Write` descriptor. The run-1 operation boundary therefore
discards their static results. This is a conformance bug. It is not an
additional selected approximation.

| Mechanism | Current | Corrected |
| --- | --- | --- |
| Local field-write alias value | Existing AP edge to the complete static alias path; missing write descriptor | Same AP edge; attach its write descriptor |
| Call ALIASES value | Existing receiver/argument/return-to-alias AP edge; missing write descriptor | Same AP edge; attach its write descriptor at the ALIASES stage |
| Reached result | Static operation boundary drops the unclassified result | Existing bounded early-summary path retains the reached effect |
| Alias without a reached value | No actual effect | Same behavior |

Keep all AP edges, field transfer, tails, exclusions, source actions,
conditions, complete premises, group ownership, layers and later FULL
behavior. Keep the engine, point-resume service, R/P requests, IR and
reference manager unchanged. No new static read is selected. No blanket
rule that treats a static destination as a write is selected.

## Lowering

In `JIRStatementForms.assign`, compute the existing alias list once. Pass
that same list to the existing `MicroEdgeBuilder.write`. For each actual
value alias whose base is STATIC, attach the exact generated transfer:

```kotlin
val writtenPath = path(to)
val aliasTargets = aliases(s, to.base)
write(to.base, writtenPath, weak, values, aliasTargets)

val source = from?.base
if (source != null) for ((base, prefix) in aliasTargets) {
    if (base != STATIC || base == to.base) continue
    val transfer = starEdge(source, emptyList(), base, prefix + writtenPath)
    staticOperation(StaticOperation.Write(operation(s),
        Pattern(transfer.to, ExclusionSet.Empty), transfer))
}
```

Keep the existing direct-static-write descriptor. The target above has the
full alias prefix and written field. Do not replace that path with the
local receiver's field alone. Do not change an alias to a strong write.

In `JIRCallPlanBuilder.aliases`, retain the exact existing value edge and
attach a descriptor only when its destination is STATIC:

```kotlin
val transfer = existingAliasValueTransfer(boundEdge, alias)
builder.edge(transfer)
if (alias.base == STATIC)
    builder.staticOperation(StaticOperation.Write(OperationId(formKey),
        Pattern(transfer.to, ExclusionSet.Empty), transfer))
```

The existing staged-plan pass assigns the actual `stageOrdinal`. The
operation keeps the existing form owner, method key and statement.
Distinct transfers remain distinct descriptor values. A rebuilt form has
the same logical operation identity.

Do not change `MicroEdgeBuilder.write`, `strongKeep` or the generic engine
classifier. A nonidentity static edge can be field preservation with an
exclusion. It is not necessarily an actual write. The ordinary S binding
also remains a binding, not a write.

## Existing publication and request pipeline

```text
reached alias value transfer
  -> EngineAlgebra matches its exact Write descriptor
  -> emitOperationResult keeps the original complete premise
  -> summaryDelta applies the existing run-1 cover and field limit
  -> fresh raw summary delta
  -> pointResumes.summary
       writes: announce the reached destination region
       ZERO TAINT: publish actual supplied groups
       all-static premise: use the complete existing writer join
       singleton nonstatic FLOW premise: register existing writer interest
       other conditional TAINT/ND premise: retain the conditional summary
  -> existing method-queue-empty commit and counted delivery
```

A consumer interest that meets a nonstatic FLOW writer uses the existing
Base/group P/R request. Its answer still needs an actual matching input.
The write descriptor cannot turn a hypothetical group initial into a
rooted supply. Mixed/nonstatic complete premises use ordinary caller N2
composition. Do not remove a premise or publish an independent group just
because an alias exists.

Static results still leave the run-1 CFG at the logical operation output.
Independent nonstatic outputs continue through their existing call or
statement suffix. The correction adds no static relay and no entry replay.

## Correctness obligations

1. **AP equality.** The old and corrected lowerers produce the same AP
   microedge sets, touched sets, filters, source actions and weak aliases.
   Only the descriptor vector grows.
2. **Descriptor coverage.** Each already-generated static alias value edge
   receives a Write descriptor with that exact transfer and target. Each
   ordinary alias receives none. Preservation and S bindings receive none.
3. **Reached-only publication.** A descriptor does not itself register a
   read, interest or supply. Publication still requires an actual nonempty
   operation result. The existing zero/reach traversal cannot fabricate a
   value from the descriptor.
4. **Premise and group validity.** Keep the complete original premise and
   existing layer. Only ZERO taint or a complete actual writer/input join
   supplies groups. FLOW writer interests use existing request ownership.
5. **Bounds and stages.** Use the existing summary cut before raw insertion.
   Retain the full alias path before that cut. Call descriptors use the
   actual ALIASES stage; replies do not restart entry analysis.
6. **Later-run behavior.** The run-1 publication branch is inactive in FULL
   runs. Their AP operations and strong field rules are unchanged.

These obligations discharge the existing frontend adapter requirement.
They do not change the finite semantic model or its theorems. The model
already assumes complete ordinary transfer publication. Its previous
proof does not establish that every JVM lowerer meets that assumption.
Keep that proof boundary explicit.

## Validation and measurement

The corrected RED gate has three JVM cases. The two real local/call static
alias cases pass their existing AP-edge assertions and fail because Write
metadata is absent. The ordinary alias control passes. Archive the earlier
test construction errors separately; they are not production evidence.

Add actor controls for conditional versus actual ZERO/group inputs,
FLOW-writer-to-Base requests, no fabricated supply, no alias reads and no
static CFG relay. Then run the required common/JVM/selected application
and rule-tag gate. Commit the correction before measurement.

All six focused controls pass. The selected four-task gate also passes:
1,591 common tests, 426 JVM tests, 15 selected application tests and 31
rule-tag/join tests. There are no failures, errors or skips. This is not
an unfiltered application-suite result or project finding acceptance.

The complete `dac301f2e` study remains diagnostic. It cannot establish
implementation acceptance after this omission is known. Analyze its actual
MemoryManager OOM snapshot before cleaning its raw artifacts. Use a fresh
fixed study for the corrected committed candidate. Preserve all strict
missing/added locations. Do not relax matching to hide the two independent
return-export location differences found in that study.
