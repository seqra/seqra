Use ASD-STE100 Simplified Technical English language    

We are working on [bidirectional-task.md](bidirectional-task.md)
Currently we have a complete implemetation for the phases 1-3 which passes all the tests (no FN, few allowed FP).
Our current goal: implement optimization and repeat major testing on real projects.

# Optimization:
Implement optimization wrt the [bidirectional-o1-o3-implementation-plan.md](docs/design/bidirectional-o1-o3-implementation-plan.md)
Commit after implementation.  

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

Our goal: for each selected project we should understand:
1) Do we have FN, comparing to the main? If we have -- identify the root cause.
2) What about performance. We have performance improvement or degradation?
3) Can we optimize something? We have 3 main points here: 
   - How much time we spent on run-1.
   - Where we spent time in the code. Maybe we have non-optimal implementation? Or we can observe some unexpected workload pattern (we spent a lot of time in the operation, which shouldn't be so expensive), maybe we shouldn't tune the analysis algorithm itself. 
   - If we have an OOM, what is the source of memory consumption?

Use subagents.