# create-test-project — Go approximation test project

Go test projects are source modules. The CLI's `test approximation init` scaffold and `test approximation run` bundled rule currently use Java `Taint` markers and `languages: [java]`. Their `--go-models` flag does not supply a Go harness. Create the module and explicit Go harness below, then use `test rule run` to verify the models.

## Scaffold the sample module

Create `.opentaint/test-projects/<name>/go.mod` with `module test` and the target application's Go version. Pin each batch dependency at the target application's exact version, including any local `replace` directives. Write ordinary `.go` sample files so the Go project loader includes them; use `go build ./...` from this module to check types and dependency resolution.

Create `util/taint.go`:

```go
package util

func Source() string { return "" }
func Sink(string) {}
```

The rule marks the `Source` return as tainted; the marker does not simulate runtime input. Adapt the marker types when the modeled callable takes or returns another type.

Create `test-rules/go/approximation.yaml`:

```yaml
rules:
  - id: approximation
    languages: [go]
    mode: taint
    severity: ERROR
    message: Test input reaches the marker sink through a Go model
    pattern-sources:
      - patterns:
          - pattern-inside: |
              import "test/util"
              ...
          - pattern: util.Source()
    pattern-sinks:
      - patterns:
          - pattern-inside: |
              import "test/util"
              ...
          - pattern: util.Sink($VALUE)
          - focus-metavariable: $VALUE
```

## Write and register the samples

For target `example.com/library/text.Transform`, put a sample in `sample.go`:

```go
package test

import (
    "example.com/library/text"
    "test/util"
)

func Positive() { util.Sink(text.Transform(util.Source())) }
func Negative() {
    _ = text.Transform(util.Source())
    util.Sink("independent")
}
```

Exercise each assigned callable, including the real receiver type and callback behavior. Add negatives for independent fields, inputs and outputs whenever the method preserves state. Register all verdicts in `rule-test.yaml` beside `go.mod`:

```yaml
tests:
  - rule-id: go/approximation.yaml#approximation
    positive:
      - test.Positive
    negative:
      - test.Negative
```

The entrypoint is the exact Go function name: `<import-path>.<function>` or `(*<import-path>.<Type>).<method>` for a pointer receiver. Use the model's `args:<count>` signature to shape calls, but do not put that signature in the entrypoint name.

## Prepare and verify the project model

```bash
opentaint compile .opentaint/test-projects/<name> \
  -o .opentaint/test-compiled/<name>
opentaint test rule run .opentaint/test-compiled/<name> \
  --ruleset .opentaint/test-projects/<name>/test-rules \
  --go-models .opentaint/dataflow/<name> \
  -o .opentaint/test-results/<name>
```

For Go, `compile` writes a `project.yaml` pointing to the source module. It does not freeze compiled sample bytecode. Keep the sample sources fixed during later model repair. Require every positive and negative to pass; check skipped and disabled entries as well as false positives and false negatives.
