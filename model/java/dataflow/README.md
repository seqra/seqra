# Built-in dataflow approximations

These code models describe how taint moves through methods that the analyzer cannot inspect.
Each directory is an independent Gradle module. Each module has its own pinned dependencies.
A model can reference the library that it models. Modules with conflicting libraries do not
share a class path.

| Module   | Models                                | Pins                             |
|----------|---------------------------------------|----------------------------------|
| `core`   | `OpentaintNdUtil` and `ArgumentTypeContext`, which support the other modules | none |
| `stdlib` | JDK types (`Stream`, `Optional`, `CompletableFuture`, executors, `Thread`) | none |
| `kotlin` | Kotlin coroutine builders             | kotlin-stdlib, kotlinx-coroutines |

The build puts the compiled classes from each module in
`opentaint-dataflow-approximations/` in the analyzer JAR. The analysis loader extracts these
classes before analysis.

## Adding a module

1. Create a directory for the library, such as `guava` or `jakarta-servlet`. Add a
   `build.gradle.kts`:

   ```kotlin
   plugins {
       id("dataflow-approximation-module")
   }

   dependencies {
       compileOnly("com.google.guava:guava:33.4.0-jre")
   }
   ```

   The convention plugin is at
   `core/opentaint-jvm-sast-dataflow/buildSrc/src/main/kotlin/dataflow-approximation-module.gradle.kts`.
   It supplies Java 8 compatibility, the name-patch annotations, and the `core` module. Pin
   exact dependency versions. These dependencies form the model compile class path.

2. Put each model under `src/main/java/opentaint/<target-package>/`. Add `opentaint.` before
   the exact target class name. For example, `opentaint.java.util.Optional` models
   `java.util.Optional`. Each target can have only one model. A duplicate model makes the
   `processResources` task fail.

You do not need to edit another build file. The
`core/opentaint-jvm-sast-dataflow/settings.gradle.kts` file finds each directory that has a
`build.gradle.kts` file.

## Writing a model

Use the same form for built-in and custom models. See
`skills/create-dataflow-approximation` for the full instructions. Use the `opentaint.`
prefix to address the target. Use `@ApproximatedClassName` when Java cannot express the
target JVM class name. Cast `(TargetClass) (Object) this` to access the real receiver. Add
`@ArgumentTypeContext` to functional-interface parameters. Use
`OpentaintNdUtil.nextBool()` when the analyzer must follow two paths. Do not use an empty
body.

OpenTaint analyzes model bodies. It does not run them. Do not add lambdas or anonymous
subclasses. Their constructors appear as dropped methods and do not carry taint.
