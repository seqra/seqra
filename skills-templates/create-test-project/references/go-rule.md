# create-test-project — Go rule test project

The `test rule init` scaffold creates Java/Gradle projects. For Go, create the requested `sources/` or `sinks/` subdirectory as a Go module with the target application's dependency versions and local replacements. Use ordinary `.go` files and check them with `go build ./...`.

Use the marker, explicit Go harness layout, verdict file and compile procedure in `references/go-approximation.md`. For a source test, route the real source into `util.Sink`; for a sink test, route `util.Source` into the real sink. The counterpart stays a generic marker. Keep positive and negative samples tied to the exact assigned member and explicit argument count.

The test detector must combine the assigned library rule with the marker counterpart. Reuse library definitions with `mode: join` and exact `join.refs`; do not copy a real source or sink as a substitute for the marker. For a sink unit, store `test-rules/go/lib/marker-source.yaml`:

```yaml
rules:
  - id: marker-source
    languages: [go]
    options: {lib: true}
    mode: taint
    severity: NOTE
    message: Generic test input
    pattern-sources:
      - label: $UNTRUSTED
        patterns:
          - pattern-inside: |
              import "test/util"
              ...
          - pattern: util.Source()
```

Store the test detector in `test-rules/go/my-sinks.yaml` and replace the assigned sink reference and label with the exact values from the library rule:

```yaml
rules:
  - id: my-sinks
    languages: [go]
    mode: join
    severity: ERROR
    message: Test input reaches the assigned sink
    join:
      refs:
        - rule: go/lib/marker-source.yaml#marker-source
          as: src
        - rule: go/lib/my-sink.yaml#my-sink
          as: sink
      on:
        - 'src.$UNTRUSTED -> sink.$UNTRUSTED'
```

For a source unit, make the marker library a sink with `pattern-sinks`, matching `util.Sink($VALUE)` and focusing `$VALUE`; join the assigned source label to `sink.$VALUE`. Keep `options: {lib: true}` and the `test/util` import context. Register the test detector's relative path and short ID in `rule-test.yaml`, for example `go/my-sources.yaml#my-sources`.

Prepare each requested side with:

```bash
opentaint compile .opentaint/test-projects/<name>/sources \
  -o .opentaint/test-compiled/<name>/sources
```

Use `sinks` for a sink unit. Later rule verification runs `opentaint test rule run <project-model> --ruleset <test-rules-root> --ruleset .opentaint/rules` with the custom library rules and test detector available. Add `--go-models <model-module>` only when the test needs an existing Go model; do not add a model to force a rule to pass. Go project models refer to live source modules, so preserve those sources after preparation.
