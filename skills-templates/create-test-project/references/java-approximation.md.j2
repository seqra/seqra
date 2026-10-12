# create-test-project — Java approximation test project

Language reference for a `dataflow` type, keyed to the body's steps.

## Workflow

### 1. Scaffold the project

`opentaint test approximation init <project>` scaffolds the Gradle build plus `Taint.java` and the fixed `approximation-rule.yaml` the harness applies. Pass each lib as a `--dependency` at its exact version from the app's dependency management (`.opentaint/project/sources/**/pom.xml`) verbatim — including timestamped snapshots — not a guess. The approximation project scaffolded later takes the same pins, so the samples and the models they exercise compile against one version.

```bash
opentaint test approximation init .opentaint/test-projects/<name> \
  --dependency "io.projectreactor:reactor-core:3.8.5"
```

### 2. Write the samples

Write one sample per method in the batch's `dataflow` bucket, across all its classes. Each `dataflow` entry records the method's `signature` (JVM descriptor) — reuse it so your sample matches. Look at how the method is actually called in the project to shape the sample and pick the receiver type. Each sample is a plain public method under `src/main/java/test/` — you don't author a rule; the scaffold's fixed `approximation-rule.yaml` is what the harness applies. The sample just routes `Taint.source()` through the approximated method into `Taint.sink(...)`. Its verdict is recorded in `rule-test.yaml` (below), not on the method.

Mark each method's test-project status (per Tracking): `done` once its sample is in the project. If no sample can be written for a method, exclude it, mark it `failed`, and note it in the summary — don't let one unmodelable method block the rest.

Positive sample — routes `Taint.source()` through the approximated method into `Taint.sink(...)`, one per method being approximated. Start every sample with `String tainted = Taint.source();`. When the approximated method takes a non-String input, cast the tainted string to that type so the taint fact reaches the method as the right type — the analyzer tracks the fact, not the runtime value.

```java
package test;

import java.util.HashMap;
import java.util.Map;

public class ApproximationSamples {

    public void taintReachesSink() {
        String tainted = Taint.source();
        Map<String, String> cache = new HashMap<>();
        String routed = cache.computeIfAbsent(tainted, k -> k);   // the approximated method
        Taint.sink(routed);
    }
}
```

Negative sample — only when the approximated method stores its input in the object. The analyzer is field-sensitive: taint set on one field doesn't surface from another. Write a negative sample that stores tainted data into one field and reads a different one — a field-precise model keeps that read clean, so the sample must not fire. This is what forces a precise approximation instead of one that taints the whole object. For plain propagation the positive alone proves the model — skip the negative.

```java
    public void taintStaysInItsField() {
        UserBean bean = new UserBean();
        bean.setName(Taint.source());    // taint stored in the `name` field
        Taint.sink(bean.getEmail());     // a different field — must stay clean
    }
```

Register the verdicts — write `rule-test.yaml` at the test-project root (next to `build.gradle.kts`), one entry under the fixed rule, listing each sample method by `test.<Class>#<method>`. `positive` samples must flag, `negative` must not:

```yaml
tests:
  - rule-id: approximation-rule.yaml#approximation-rule
    positive:
      - test.ApproximationSamples#taintReachesSink
    negative:
      - test.ApproximationSamples#taintStaysInItsField
```

Note — a sample's receiver type fixes the dropped method's fully-qualified name, and the later approximation targets that exact class, so mirror the real call's receiver type. The same call off an interface-typed receiver (`Map<String,String> m`, e.g. a parameter) resolves to `java.util.Map#computeIfAbsent`, off a concrete one (`new HashMap<>()`) to `java.util.HashMap#computeIfAbsent` — match whichever the real flow uses.

When the approximated method hands the data to a callback instead of returning it, put the sink inside the callback body: `obj.method(c -> Taint.sink(tainted))`. Don't stash the value in a captured local or array and sink it after the call.

## Constraints

- The scaffold defaults to Java 8. A sample using a library that needs Java 17/21 must bump `source/targetCompatibility` and set `release` on the running JDK
- Diagnose a compile failure with `gradle compileJava --console=plain` in the sub-project — no gradlew is generated
