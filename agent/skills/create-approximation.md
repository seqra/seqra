# Skill: Create Approximation

Create code-based approximations for complex library methods involving lambdas, async, or callbacks.

## Prerequisites

- External methods list analyzed (analyze-findings skill)
- The method involves lambdas/callbacks/functional interfaces (YAML cannot model these)
- The target class must NOT already have a built-in approximation

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

### 2. Compile approximations

Compile the Java sources against the analyzer JAR (contains the annotation classes) and the target project's dependencies:

```bash
javac -source 8 -target 8 \
  -cp "opentaint-project-analyzer.jar:target-project-deps/*" \
  -d agent-approximations/classes \
  agent-approximations/src/agent/approximations/*.java
```

### 3. Run with approximations

```bash
opentaint scan ./opentaint-project/project.yaml \
  -o ./results/report.sarif \
  --ruleset builtin --ruleset ./agent-rules \
  --rule-id java/security/my-vuln.yaml:my-vulnerability \
  --dataflow-approximations ./agent-approximations/classes
```

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
- Must NOT target a class that already has a built-in approximation (will error at runtime)
- Method signatures must match the target class methods exactly

## When to use code-based vs YAML

- Lambda/callback invocation -> **Code-based** (this skill)
- Non-deterministic branching (async paths) -> **Code-based**
- Complex internal state with multiple method interactions -> **Code-based**
- Simple from-to propagation -> **YAML** (create-yaml-config skill)
