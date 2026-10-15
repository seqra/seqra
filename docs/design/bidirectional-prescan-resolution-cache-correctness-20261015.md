# Receiver class in the resolved-override cache key

Date: 15 October 2026. Status: implemented; four focused regression tests and broader validation pass.
Fresh optimized scans on revision `85a7c0e367d9a2dc6360b03c2feb5e07b1583b66` are terminal:
OWASP reached and accepted **run-3**; Conductor reached **run-2** with OOM after accepting run-1;
ThingsBoard stopped with OOM in **run-1**, before completion or acceptance; Stirling-PDF reached
**run-4** with timeout after accepting run-3. The current report below records the times and
finding limits. Control comparisons, recording reviews and all four guard-heap analyses are
complete. The command diagnostic identifies a specified source-rewriter cut on the main URL
route; its coverage limits and unchanged final outputs are in the report. The failure evidence
below concerns the prior revision's **run-1**, with no completion or acceptance.
This repairs a prerequisite of the selected run-1 static-catalog design. It introduces no
new cache type, reference manager, call-resolution algorithm or IR storage change.

## Failure and required behavior

Fresh revision `f965f814ba84083db9ff2b537ca0f706bce1ef10` reached **run-1, forward, limit 1**
on Conductor. It failed after 0.659760016 seconds; no iteration completed and no report was
accepted. Prescan had completed successfully. The catalog-coverage guard rejected this edge:

```text
PerplexityAI#getChatOptions(ChatCompletion) -> EmbeddingGenRequest#getModel()
```

`ChatCompletion` and `EmbeddingGenRequest` are sibling subclasses of `LLMWorkerInput`.
`ChatCompletion` inherits `getModel`; `EmbeddingGenRequest` declares its own model/getter.
The static catalog requires the prescan and runtime to resolve the same query consistently.
The guard must remain. A successful prescan join alone cannot repair an inconsistent resolver.

## Current, proposed and reason

| Current | Proposed | Reason |
| --- | --- | --- |
| `JIRCallResolver.methodOverrides(method, baseClass)` caches by `method` alone | Key the existing concurrent map by `(method, baseClass)` | Hierarchy traversal starts at `baseClass`; different receiver classes can have different overrides |
| A broad lookup can populate a result later reused for a narrow sibling branch | Memoize each complete lookup separately | Prevent unrelated sibling targets from entering a narrow query |
| A narrow lookup can hide targets from a later broad query | Keep the broad query's own result | Prevent cache order from removing valid targets |

The legacy prescan creates resolvers per unit runner. The new engine uses one shared resolver.
Their first lookup order can differ. The saved run does not contain the exact cache population
history. A real-bytecode regression independently reproduces the same catalog exception when
the fresh shared resolver visits the broad receiver before the narrow receiver. All four new
tests fail on the original method-only key and pass on the complete key, with no errors or skips.

## Implementation boundary

Use a small private value key with both existing semantic inputs. Preserve the current
`ConcurrentHashMap`, hierarchy traversal, allowed locations, unit filtering, exact receiver
handling, lambda handling and `MethodContextCreator`. Preserve empty-result caching too.
Do not add a second resolution pass, replay partial graph data, remove the catalog guard,
rewrite `SoftReferenceManager`, or introduce `WeakIdentityCache`.

The resolver already owns its classpath, unit resolver and allowed-location set for its
lifetime. The repair makes the key match the existing lookup inputs under that lifetime.
It does not prove that every possible graph/export mismatch has this cause.

## Validation and fresh measurements

1. Use real test bytecode with an inherited getter, a narrow receiver subclass and a sibling
   override. Compare broad-first and narrow-first query orders against separate fresh resolvers.
   Check exact target sets and repeated lookups, including an empty narrow override result.
2. Exercise the prescan-to-runtime graph handoff with a real virtual call. Keep the independent
   missing-target rejection control. A graph assembled directly from runtime CallPlans does
   not test this boundary.
3. Run the applicable common and JVM tests, with test compilation for supported front ends.
4. Commit the repair before a new fixed-project batch. Bind reports to that new revision and
   repeat the configured main comparisons. Preserve f965 failures as historical evidence;
   do not treat its OWASP timing or incomplete Conductor run as a new-revision result.

This is a correctness repair, not a measured performance optimization. More complete keys can
retain more cache entries; do not claim a memory reduction. Any target/workload reduction or
increase must be measured after the repair with the fixed project settings.

The [first measurement report](../reports/bidirectional-run1-abstraction-demands-performance-20261014.md)
records the original run-1 failure and the other measured outcomes.
The [repaired-revision report](../reports/bidirectional-run1-abstraction-demands-performance-20261015.md)
records the passing broader checks and the status of the fresh fixed-project batch.
