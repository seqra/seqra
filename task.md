Use ASD-STE100 Simplified Technical English language    

We are working on [bidirectional-task.md](bidirectional-task.md)
Currently we have a complete implemetation for the phases 1-3 which passes all the tests (no FN, few allowed FP).
Our current goal: implement optimization and repeat major testing on real projects.

# Optimization:
Implement optimization wrt the [bidirectional-o1-o3-implementation-plan.md](docs/design/bidirectional-o1-o3-implementation-plan.md)
Commit after implementation.  

The old optimization priorities are stale. Use the [latest measurement-based proposal](docs/design/bidirectional-core-optimization-20261013.md)
for future selection. Restore the original `SoftReferenceManager` and remove `WeakIdentityCache` from the
current implementation. Their redesigns are possible future optimizations that need separate selection.

The next optimization iteration includes N1 and N2 only, as described in the
[detailed proposal](docs/design/bidirectional-n1-n3-implementation-proposal.md).
N3 is [deferred](docs/design/bidirectional-n3-deferred-proposal.md). Reconsider it only if fresh measurements
after N1/N2 show the same substantial queue/backlog pattern. Keep request transport and scheduling unchanged
in this iteration.

# Test
Take the latest commit. Ignore reports for the previous commits as staled.
1) Run on OWASP like we do it in .github/workflows/ci-analyzer-owasp.yaml
2) Run on selected real projects from `../opentaint-test`: conductor, thingsboard, Stirling-PDF. Use parameters, specified in the config (memory, timeout). Don't run on any other configuration. 

Use `runProjectAnalyzer` gradle task directly, don't use the CLI.

Measure:
1) Reported vulnerability location, comparing to the main. Use main as a baseline. We should have no FN. We expect few FP, but not really many.
2) Overall performance: OOM/timeout events, the time of each run iteration.
3) Capture profile under the workload, we should understand our hot spots (possible performance optimization points). It is a good idea to measure for 30 sec after 1 minute of analysis (normal behavior), and close to the time limit
4) Capture memory snapshots whenever MemoryManager reports OOM. Investigate the root cause of memory consumption

Every report must include the latest reached iteration as `run-N`, its direction and positive field limit,
including a failed or unfinished last run. Distinguish the last complete engine iteration and the last
accepted forward report. State the capture iteration separately for heaps and profiles. Use unavailable
when there is no evidence; main has no bidi iteration. Run-1 starts at limit 1; limit 0 is impossible.

Our goal: for each selected project we should understand:
1) Do we have FN, comparing to the main? If we have -- identify the root cause.
2) What about performance. We have performance improvement or degradation?
3) Can we optimize something? We have 3 main points here: 
   - How much time we spent on run-1.
   - Where we spent time in the code. Maybe we have non-optimal implementation? Or we can observe some unexpected workload pattern (we spent a lot of time in the operation, which shouldn't be so expensive), maybe we shouldn't tune the analysis algorithm itself. 
   - If we have an OOM, what is the source of memory consumption?

Use subagents.
