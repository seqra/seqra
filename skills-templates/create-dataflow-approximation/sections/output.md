### Artifacts

- `.opentaint/dataflow/<batch>` — the batch's approximation project: its pinned dependencies plus the language-specific code approximation artifacts that the scan consumes, following the language reference's artifact layout; report the path and the exact test command used
- the passing methods present in the batch file's `build.done` (new methods appended; repaired methods already recorded, per Tracking)

### Summary

- the methods modeled and the test status (passing / non-converging)
