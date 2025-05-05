# Module opentaint-ir-api

## Settings 

The [JIRSettings] class is used for creating a [JIRDatabase] instance.

Use custom Java runtime (not the current process) for bytecode analysis.

There are two shortcuts: [JIRSettings].[useProcessJavaRuntime] is for using current process runtime (the default option), and
[JIRSettings].[useJavaHomeRuntime] is for using Java runtime from the JAVA_HOME environment variable.

[JIRDatabase] instance is created based on settings. If an instance is not needed, the `close` method should be
called.

## Features 

[JIRFeature] is an additional feature which can collect data from bytecode, persist it in database and use it to find specific places.
[JIRFeature] has parameterization of request/response types.

A feature lifecycle:
- Pre-indexing — can be used to prepare a database scheme, for example.
- Indexing.
- Flushing indexed data to a persistent storage.
- Post-indexing step — can be used to set up the database-specific indexes.
- Updating indexes (if a bytecode location is outdated and removed).

Call on each step of a lifecycle with respected signal.

| property | type       | description                                                     |
|----------|------------|-----------------------------------------------------------------|
| signal   | [JIRSignal] | [BeforeIndexing], [AfterIndexing], [LocationRemoved], or [Drop] |

<!--- MODULE opentaint-ir-api -->
<!--- INDEX org.opentaint.ir.api -->

[JIRSettings]: https://opentaint-ir.org/docs/opentaint-ir-core/org.opentaint.ir.impl/-jIR-settings/index.html
[useProcessJavaRuntime]: https://opentaint-ir.org/docs/opentaint-ir-core/org.opentaint.ir.impl/-jIR-settings/use-process-java-runtime.html
[useJavaHomeRuntime]: https://opentaint-ir.org/docs/opentaint-ir-core/org.opentaint.ir.impl/-jIR-settings/use-java-home-runtime.html
[JIRDatabase]: https://opentaint-ir.org/docs/opentaint-ir-api/org.opentaint.ir.api/-jIR-database/index.html
[JIRDatabase]: https://opentaint-ir.org/docs/opentaint-ir-api/org.opentaint.ir.api/-jIR-database/index.html
[JIRFeature]: https://opentaint-ir.org/docs/opentaint-ir-api/org.opentaint.ir.api/-jIR-feature/index.html
[JIRSignal]: https://opentaint-ir.org/docs/opentaint-ir-api/org.opentaint.ir.api/-jIR-signal/index.html
[BeforeIndexing]: https://opentaint-ir.org/docs/opentaint-ir-api/org.opentaint.ir.api/-jIR-signal/-before-indexing/index.html
[AfterIndexing]: https://opentaint-ir.org/docs/opentaint-ir-api/org.opentaint.ir.api/-jIR-signal/-after-indexing/index.html
[LocationRemoved]: https://opentaint-ir.org/docs/opentaint-ir-api/org.opentaint.ir.api/-jIR-signal/-location-removed/index.html
[Drop]: https://opentaint-ir.org/docs/opentaint-ir-api/opentaint-ir-api/org.opentaint.ir.api/-jIR-signal/-drop/index.html