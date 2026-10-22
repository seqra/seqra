### 1. Run the scan

Scan the pre-built model at `.opentaint/project`. Write the report to `.opentaint/results/report.sarif` and load both the built-in ruleset and the project's own rules under `.opentaint/rules`:

```bash
opentaint scan --project-model .opentaint/project \
  -o .opentaint/results/report.sarif \
  --ruleset builtin --ruleset .opentaint/rules \
  --track-external-methods
```

- `--rule-id <full-id>` — restrict to specific executable rules (repeatable, one per input rule ID); referenced library rules remain available without listing their IDs separately. Omit to run all loaded rules
- `--passthrough-approximations .opentaint/pass-through` — add when that directory exists: passThrough configs override built-ins at the rule level, a provided rule overriding a built-in only when it matches one
- For Java and Kotlin, add `--dataflow-approximations .opentaint/dataflow` when that directory has approximation projects. Each project pins its dependencies and builds on demand when its sources change
- For Go, find each `go.mod` under `.opentaint/dataflow`. Add one `--go-models <module-directory>` option for each file. Do not use `--dataflow-approximations` for a Go model

The pass-through and JVM approximation options read their directory trees. A Go model option must name one Go module. The module can model more than one target package.

The scan can take a long time. Run it in the background and wait for it to finish. Keep `--timeout` at the engine default of 900 seconds. The CLI ends the analysis and writes the available SARIF report.

### 2. Retry once on out-of-memory

Start at the 8G default. An out-of-memory failure at 8G — even when the engine still wrote a partial SARIF — is not an acceptable result: retry once at `--max-memory 16G` before collecting anything. Never higher (more RAM won't improve results), one bump only. When the caller passed `max-memory`, run at it from the first attempt instead

### 3. Collect the report, or escalate

A SARIF is complete enough to use even alongside the two normal scan errors — a timeout (the CLI ended the analysis and wrote what it had), or an out-of-memory at 16G (after §2's bump), take it as-is. The other two outcomes are not results to accept. A config-load failure on a malformed approximation is fixed, not bumped: report the engine error and the offending file under `.opentaint/pass-through`/`.opentaint/dataflow` per Output so it can be repaired and rescanned. No SARIF at all, even at 16G, is a plain failure: report it per Output with the scan setup.
