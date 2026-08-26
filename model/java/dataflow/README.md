# Builtin dataflow approximations

Code-based models of how taint moves through methods the analyzer cannot see into. Each
directory here is an independent Gradle module compiled against **its own pinned
dependencies**, so a model may reference the library type it models, and modules whose
libraries conflict — javax against jakarta, two majors of one artifact — coexist without
ever sharing a classpath.

| Module   | Models                                | Pins                             |
|----------|---------------------------------------|----------------------------------|
| `core`   | `OpentaintNdUtil`, `ArgumentTypeContext` — the support types every other module uses | none |
| `stdlib` | JDK types (`Stream`, `Optional`, `CompletableFuture`, executors, `Thread`) | none |
| `kotlin` | Kotlin coroutine builders             | kotlin-stdlib, kotlinx-coroutines |

The compiled classes of every module are merged into `opentaint-dataflow-approximations/`
inside the analyzer jar, which `DataFlowApproximationLoader` unpacks at analysis time.

## Adding a module

1. Create a directory named for the library (`guava`, `jakarta-servlet`, …) with a
   `build.gradle.kts`:

   ```kotlin
   plugins {
       id("dataflow-approximation-module")
   }

   dependencies {
       compileOnly("com.google.guava:guava:33.4.0-jre")
   }
   ```

   The convention plugin
   (`core/opentaint-jvm-sast-dataflow/buildSrc/src/main/kotlin/dataflow-approximation-module.gradle.kts`)
   supplies Java 8 compatibility, the `@Approximate` annotations and the `core` module. Pin
   exact versions — this classpath is the model's compile environment and nothing else
   influences it.

2. Put the models under
   `src/main/java/org/opentaint/jvm/dataflow/approximations/<module>/`. The per-module
   package keeps approximation class names globally unique: an approximation FQCN maps to
   exactly one target class, and a collision between two modules fails the build during
   `processResources`.

Nothing else needs editing — `core/opentaint-jvm-sast-dataflow/settings.gradle.kts`
discovers every directory here that has a `build.gradle.kts`.

## Writing a model

Same form as a custom model, so the reference in `skills/create-dataflow-approximation`
applies here too. In short: `@Approximate(TargetClass.class)` on one class per target,
reach the real receiver with `(TargetClass) (Object) this`, put functional-interface
parameters behind `@ArgumentTypeContext`, branch with `OpentaintNdUtil.nextBool()`, and
never leave a body empty. Approximation bodies are analyzed, never executed, and must not
introduce synthetic classes (lambdas, anonymous subclasses) — their constructors surface as
dropped methods and carry no taint.
