# create-dataflow-approximation — Go

## Find the callable's source

Use the plan's exact function or receiver method name and its `args:<count>` signature. The count excludes the receiver. Run `go list -m -json all` in the target module to locate dependency sources and replacements, then read the actual implementation. Copy its dependency versions into the model module.

## Write the model module

```bash
opentaint approximation init .opentaint/dataflow/<batch> --language go \
  --dependency example.com/library@v1.2.3
```

The scaffold writes `go.mod` with `module opentaint`. For target package `example.com/library/text`, write `.opentaint/dataflow/<batch>/example.com/library/text/model.go`; its import path is `opentaint/example.com/library/text`. Keep the target package name and the callable's exact name, receiver, type parameters, parameter types and return types. A string transformation that preserves its input's provenance can be modeled as:

```go
package text

func Transform(value string) string {
    return value
}
```

Use the real propagation for the assigned method: return its input, store it in the correct field, or pass it to the correct callback. A constant or zero return cannot model input propagation. Use target types from their original imports. Receiver structs may declare just the fields the model uses; existing field names and types must match. Model helpers and additional fields are supported, but model `init` functions are ignored. Supply each target package from only one model directory across all batches.

Re-running `approximation init` preserves sources but replaces `go.mod`, so pass the full dependency list. Go models load as source in the same temporary workspace as the target modules; they do not use `compile approximations` or the Java support JAR.

## Verify on the fixed samples

Read [the Go test-project reference](../../create-test-project/references/go-approximation.md) for the custom Go harness and sample layout. The bundled `test approximation init/run` harness is Java-only. Use the prepared Go test project's harness with the rule-test runner:

```bash
opentaint test rule run .opentaint/test-compiled/<batch> \
  --ruleset .opentaint/test-projects/<batch>/test-rules \
  --go-models .opentaint/dataflow/<batch> \
  -o .opentaint/test-results/<batch>
```

Check `test-result.json`: positives must pass and negatives must remain clean. A skipped sample is a failure, not proof of coverage. Keep the test sources fixed while refining the model. Confirm target matching and trace where the fact disappears before changing propagation. Only record the model in `build.done` after this command passes.

Apply the same source module to the target scan with `opentaint scan <project> --go-models .opentaint/dataflow/<batch>`.
