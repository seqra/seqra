# Skill: Create Approximation

Create code-based approximations for complex library methods involving lambdas, async, or callbacks.

## When approximations are actually useful

Approximations (both code-based and YAML) only change the analysis of **external methods
with no existing model**. Concretely, this means the method the approximation targets must
appear in `<sarif-dir>/external-methods-without-rules.yaml` produced by the previous scan
(see `analyze-findings` skill). An entry there means the analyzer walked through that method
and **killed the dataflow facts** because it had no rule — that's the exact gap you can fill.

If the method is in `external-methods-with-rules.yaml`, it is already modeled. Writing
another approximation for it is a no-op at best and conflicts with a built-in rule at worst
(duplicate-target error). Skip it.

If the method is in neither list, the analyzer never reached it on a tainted path during
the scan. Adding an approximation will not change the result until the analyzer actually
observes a tainted argument flowing in.

**Rule of thumb**: approximate only methods that are in the `without-rules` list **and** lie
on a code path relevant to your vulnerability (reachable between a source and a sink).

## Prerequisites

- A baseline scan has been run with `--track-external-methods` (see `run-analysis` skill)
- `external-methods-without-rules.yaml` has been read and the target method is in it (see `analyze-findings` skill)
- The method involves lambdas/callbacks/functional interfaces (YAML cannot model these — otherwise prefer `create-yaml-config`)
- The target class must NOT already have a built-in approximation (would be listed under `external-methods-with-rules.yaml` if so)

## Procedure

### 1. Create approximation source

Create Java files in `agent-approximations/src/`:

```java
package agent.approximations;

import org.opentaint.ir.approximation.annotation.Approximate;
import org.opentaint.jvm.dataflow.approximations.ArgumentTypeContext;
import org.opentaint.jvm.dataflow.approximations.OpentaintNdUtil;

import java.util.function.Function;

@Approximate(com.example.lib.ReactiveProcessor.class)
public class ReactiveProcessor {

    // Model: taint on this flows through the function to the result
    public Object transform(@ArgumentTypeContext Function fn) throws Throwable {
        com.example.lib.ReactiveProcessor self =
            (com.example.lib.ReactiveProcessor) (Object) this;
        if (OpentaintNdUtil.nextBool()) return null;
        Object input = self.getValue();
        return fn.apply(input);
    }

    // Model: taint on this flows to the consumer argument
    public void subscribe(@ArgumentTypeContext java.util.function.Consumer consumer) {
        com.example.lib.ReactiveProcessor self =
            (com.example.lib.ReactiveProcessor) (Object) this;
        if (OpentaintNdUtil.nextBool()) {
            consumer.accept(self.getValue());
        }
    }
}
```

### 2. Run with approximations

Point `--dataflow-approximations` at the source directory. The CLI auto-compiles `.java`
files using the analyzer JAR (for `@Approximate`, `OpentaintNdUtil`, `ArgumentTypeContext`)
and the target project's dependencies, then forwards the compiled directory to the analyzer.
Manual `javac` invocation is not required.

```bash
opentaint scan --project-model ./opentaint-project \
  -o ./results/report.sarif \
  --ruleset builtin --ruleset ./agent-rules \
  --rule-id java/security/my-vuln.yaml:my-vulnerability \
  --dataflow-approximations ./agent-approximations/src
```

If `.java` compilation fails, the CLI reports the errors and aborts before the scan starts.
If the directory contains already-compiled `.class` files (no `.java` siblings), the CLI
passes it through unchanged.

## Key Patterns

| Pattern | Usage |
|---------|-------|
| `@Approximate(TargetClass.class)` | Link approximation to target class |
| `@ApproximateByName("fqn")` | Link by fully qualified name (when class not on compile classpath) |
| `(TargetClass) (Object) this` | Cast to access real object's methods |
| `@ArgumentTypeContext` | On lambda/functional interface parameters |
| `OpentaintNdUtil.nextBool()` | Non-deterministic branching (analyzer considers both paths) |

## Constraints

- Java 8 source compatibility
- One approximation class per target class (strict bijection)
- Must NOT target a class that already has a built-in approximation (will error at runtime). Verify by checking `external-methods-with-rules.yaml` — if the class appears there, it is already covered.
- Method signatures must match the target class methods exactly

## Validating the approximation had an effect

After re-running the scan with `--dataflow-approximations`, diff the before/after
`external-methods-without-rules.yaml`:

- The approximated method should disappear from `without-rules` (moves to `with-rules`)
- If it does not move, your `@Approximate(...)` target class or the method signature does not match what the analyzer sees
- If new findings appear in the SARIF after the approximation, they are likely true positives the kill-facts was hiding

## When to use code-based vs YAML

- Lambda/callback invocation -> **Code-based** (this skill)
- Non-deterministic branching (async paths) -> **Code-based**
- Complex internal state with multiple method interactions -> **Code-based**
- Simple from-to propagation -> **YAML** (create-yaml-config skill)
- Method is **not** in `external-methods-without-rules.yaml` -> **do nothing** (approximation will have no observable effect)
