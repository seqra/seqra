# create-dataflow-approximation — Java / JVM

The JVM procedure, keyed to the body's steps.

## Workflow

### 1. Find the method's source

An app-internal method sits in the project's own sources under `<project-root>`. For a library method, the resolved jars are in `.opentaint/project/dependencies` — locate the class with `unzip -l <jar> | grep <class-as-path>` (dotted class name with `.` → `/`) and decompile it for readable source, or disassemble it with `javap -c -p -classpath <jar> <fully.qualified.ClassName>` when no decompiler is available.

### 2. Write the approximation source

The models live in the batch's own approximation project. Scaffold it once, passing each library the batch's models will reference at the exact version the batch file's `dependencies` records — the same pins the test project took. Those pins are the models' compile environment: nothing about the project under analysis affects it, so the models compile the same way at test time and at scan time.

```bash
opentaint approximation init .opentaint/dataflow/<batch> \
  --dependency "io.projectreactor:reactor-core:3.8.5"
```

Re-invoked for a batch whose project already exists, leave the build file alone unless a model needs a library that isn't pinned yet — then add it with a second `init` carrying the full dependency set, or edit `build.gradle.kts` directly.

Create Java files under `.opentaint/dataflow/<batch>/src/main/java` — one `@Approximate` class per target class. `@Approximate(TargetClass.class)` binds a model to exactly that class, so target the EXACT class the analyzer dropped — the dropped FQN reflects how the call resolved: an interface-typed receiver (`Map m = ...; m.computeIfAbsent(...)`) drops `java.util.Map#computeIfAbsent`, a concrete one (`new HashMap<>()`) drops `java.util.HashMap#computeIfAbsent`. An interface is a valid target — write `@Approximate` on it exactly as on a concrete class, modelling whichever FQN the analyzer dropped. Reach the real object with `(TargetClass) (Object) this`, put functional-interface parameters behind `@ArgumentTypeContext`, and branch with `OpentaintNdUtil.nextBool()` so the analyzer walks both paths. Never leave a body empty.

```java
package com.example.approximations.batchpkg;   // per-batch package (e.g. ...approximations.cn_hutool_001) — see the globally-unique rule below

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

Wrapper-returning operators (a `Mono`/`Flux`, `Optional`, `Stream`, a builder — anything where the taint stays inside a container): declare the real concrete return type, not `Object`; in the `nextBool()` branch `return self`, not `null`; and extract → apply → re-wrap so a downstream extractor (`block`, `get`, …) can pull the tainted value back out:

```java
@Approximate(reactor.core.publisher.Mono.class)
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

`test approximation run` applies its own bundled fixed source→sink rule automatically — you don't author or pass one. The CLI builds the approximation project against the dependencies it pins (the approximation API — `@Approximate`, `OpentaintNdUtil`, `ArgumentTypeContext` — comes from the project's own `libs/`); if compilation fails it reports the errors and aborts before the tests. A rebuild happens only when the sources or the pins changed, so re-running an unedited batch skips straight to the tests. To see compilation errors on their own, compile the project alone:

```bash
opentaint compile approximations .opentaint/dataflow/<batch>
```

A positive sample is a `falseNegative` until the model propagates taint. Read the result with the bundled script — it prints the pass/fail counts and names each failing sample, so you don't parse the JSON by hand:

```bash
uv run <skill-dir>/scripts/check-test-result.py <batch>
```

Fix by the verdict it reports:

- still `falseNegative` → the `@Approximate(...)` target class or a method signature doesn't match what the analyzer sees, or the body doesn't route taint from the real source to the modeled result/argument; diagnose the mismatch, don't rationalize a non-result. Most common: target-class mismatch with the dropped FQN — re-target the exact dropped class and match the cast (`(java.util.HashMap) (Object) this`)
- `falsePositive` (a negative sample fired) → the model is over-broad: it taints a read it shouldn't, e.g. a field it wasn't stored under. Narrow the propagation until the negative stays non-firing while the positive passes

## Key patterns

| Pattern | Usage |
|---|---|
| `@Approximate(TargetClass.class)` | Link the approximation to its target class. Must be on the compile classpath (a project dependency or a JDK type) |
| `(TargetClass) (Object) this` | Cast to reach the real object's methods |
| `@ArgumentTypeContext` | On lambda / functional-interface parameters |
| `OpentaintNdUtil.nextBool()` | Non-deterministic branch — the analyzer considers both paths |

## Constraints

- Java 8 source compatibility
- A model may only reference libraries its own project pins. A model that needs a type from an unpinned library is a missing `--dependency`, not a reason to weaken the model to `Object`
- Put the `@Approximate` classes in a neutral package (e.g. `com.example.approximations`) — never the target library's own package. Inside the library's package every bare FQN resolves to your approximation's non-generic class instead of the real type, breaking compilation wholesale
- Namespace the package PER BATCH (`com.example.approximations.<batch>`, e.g. `...approximations.cn_hutool_001`). Every batch is loaded together into one shared index, and approximation class FQCNs must be GLOBALLY unique across all batches — two batches that both model a `…$Nested` (or any recurring simple name) in the bare `com.example.approximations` package collide on `com.example.approximations.Nested` and crash the whole scan at config-load. A per-batch package guarantees uniqueness.
- One approximation class per target class — a strict global bijection enforced at load: each approximation FQCN maps to exactly one target and vice-versa; duplicates or a reused FQCN across batches throw `IllegalArgumentException`
- Method signatures must match the target class methods exactly
- Built-in dataflow approximations are first-priority and presumed correct — you cannot override them locally
- A custom dataflow approximation overrides a passThrough for the same method — use dataflow as the fallback when a faithful passThrough cannot express or restore the propagation
- Built-in passThrough approximations, by contrast, a user config on the same method overrides. So a coarse built-in on a *related* method — a driver that invokes the callback you modelled, a wrapper the flow passes through — can silently suppress an otherwise-correct model. When a faithful model doesn't fire, suspect a built-in on a neighbouring method and model/override that method instead; debug-rule shows which model actually applied
- Don't unpack or grep the analyzer JAR for built-in models or signatures — its internals aren't a stable API; go through the CLI
