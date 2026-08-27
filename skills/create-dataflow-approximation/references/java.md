# create-dataflow-approximation — Java / JVM

The JVM procedure, keyed to the body's steps.

## Workflow

### 1. Find the method's source

An app-internal method sits in the project's own sources under `<project-root>`. For a library method, the resolved jars are in `.opentaint/project/dependencies` — locate the class with `unzip -l <jar> | grep <class-as-path>` (dotted class name with `.` → `/`) and decompile it for readable source, or disassemble it with `javap -c -p -classpath <jar> <fully.qualified.ClassName>` when no decompiler is available.

### 2. Write the approximation source

The models live in the batch's own approximation project. Scaffold it once, passing each library the batch's models will reference at the exact version the batch file's `dependencies` records — the same pins the test project took. Those pins are the models' compile environment: nothing about the project under analysis affects it, so the models compile the same way at test time and at scan time.

```bash
opentaint approximation init .opentaint/dataflow/<batch> \
  --language java \
  --dependency "io.projectreactor:reactor-core:3.8.5"
```

Re-invoked for a batch whose project already exists, leave the build file alone unless a model needs a library that isn't pinned yet — then add it with a second `init` carrying the full dependency set, or edit `build.gradle.kts` directly.

Create one Java model for each target class under `.opentaint/dataflow/<batch>/src/main/java`. Add `opentaint.` before the exact target class name. For example, `opentaint.java.util.Map` models `java.util.Map`. Model the exact class or interface from the dropped method report. Use `(TargetClass) (Object) this` to access the real object. Add `@ArgumentTypeContext` to functional-interface parameters. Use `OpentaintNdUtil.nextBool()` when the analyzer must follow two paths. Do not use an empty body.

```java
package opentaint.com.example.lib;

import org.opentaint.jvm.dataflow.approximations.ArgumentTypeContext;
import org.opentaint.jvm.dataflow.approximations.OpentaintNdUtil;

import java.util.function.Function;

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

Wrapper-returning operators (a `Mono`/`Flux`, `Optional`, `Stream`, a builder — anything where the taint stays inside a container): declare the real concrete return type, not `Object`; in the `nextBool()` branch `return self`, not `null`; and extract → apply → re-wrap so a downstream extractor (`block`, `get`, …) can pull the tainted value back out:

```java
package opentaint.reactor.core.publisher;

public class Mono {
    public reactor.core.publisher.Mono map(@ArgumentTypeContext Function fn) throws Throwable {
        reactor.core.publisher.Mono self = (reactor.core.publisher.Mono) (Object) this;
        if (OpentaintNdUtil.nextBool()) return self;
        Object up = self.block();                                  // extract upstream element
        return reactor.core.publisher.Mono.justOrEmpty(fn.apply(up)); // apply mapper, re-wrap
    }
}
```

### 3. Test against the test project

Run `test approximation run` over the compiled test project applying this batch's sources, iterate the sources until the samples pass:

```bash
opentaint test approximation run .opentaint/test-compiled/<batch> \
  -o .opentaint/test-results/<batch> \
  --dataflow-approximations .opentaint/dataflow/<batch>
```

`test approximation run` applies its bundled source-to-sink rule. The CLI builds the approximation project with its pinned dependencies. The project `libs` directory supplies the name-patch annotations, `OpentaintNdUtil`, and `ArgumentTypeContext`. A build failure stops the test and reports the compiler errors. OpenTaint rebuilds only when the source or dependency pins change. Use this command to compile the project without a test:

```bash
opentaint compile approximations .opentaint/dataflow/<batch>
```

A positive sample is a `falseNegative` until the model propagates taint. Read the result with the bundled script — it prints the pass/fail counts and names each failing sample, so you don't parse the JSON by hand:

```bash
uv run <skill-dir>/scripts/check-test-result.py <batch>
```

Fix by the verdict it reports:

- still `falseNegative` means that the target class, signature, or taint path is incorrect. Use `opentaint.<exact-dropped-FQN>`. Make the cast use the same target class.
- `falsePositive` (a negative sample fired) → the model is over-broad: it taints a read it shouldn't, e.g. a field it wasn't stored under. Narrow the propagation until the negative stays non-firing while the positive passes

## Key patterns

| Pattern | Usage |
|---|---|
| `opentaint.<target-package>.<TargetClass>` | The model name. Remove `opentaint.` to get the target class name. |
| `@ApproximatedClassName("actual-name")` | Set a target JVM class name that Java source cannot express. The target package still comes from the prefix. |
| `(TargetClass) (Object) this` | Cast to reach the real object's methods |
| `@ArgumentTypeContext` | On lambda / functional-interface parameters |
| `OpentaintNdUtil.nextBool()` | Non-deterministic branch — the analyzer considers both paths |

## Constraints

- Java 8 source compatibility
- A model may only reference libraries its own project pins. A model that needs a type from an unpinned library is a missing `--dependency`, not a reason to weaken the model to `Object`
- Use `opentaint.<exact-target-package>` as the package. The class name must match the target binary name. Use `@ApproximatedClassName` only when Java cannot express the JVM name.
- Every target has one canonical model class. Do not model the same target in more than one batch.
- One approximation class per target class — a strict global bijection enforced at load: each approximation FQCN maps to exactly one target and vice-versa; duplicates or a reused FQCN across batches throw `IllegalArgumentException`
- Method signatures must match the target class methods exactly
- Built-in dataflow approximations are first-priority and presumed correct — you cannot override them locally
- A custom dataflow approximation overrides a passThrough for the same method — use dataflow as the fallback when a faithful passThrough cannot express or restore the propagation
- Built-in passThrough approximations, by contrast, a user config on the same method overrides. So a coarse built-in on a *related* method — a driver that invokes the callback you modelled, a wrapper the flow passes through — can silently suppress an otherwise-correct model. When a faithful model doesn't fire, suspect a built-in on a neighbouring method and model/override that method instead; debug-rule shows which model actually applied
- Don't unpack or grep the analyzer JAR for built-in models or signatures — its internals aren't a stable API; go through the CLI
