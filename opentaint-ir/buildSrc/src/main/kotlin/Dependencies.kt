@file:Suppress("PublicApiImplicitType", "MemberVisibilityCanBePrivate", "unused", "ConstPropertyName")

import org.gradle.plugin.use.PluginDependenciesSpec

object Versions {
    const val asm = "9.7.1"
    const val dokka = "1.9.20" // note: must be compatible with kotlin version
    const val gradle_download = "5.3.0"
    const val gradle_versions = "0.47.0"

    // hikaricp version compatible with Java 8
    const val hikaricp = "4.0.3"

    const val guava = "31.1-jre"
    const val javax_activation = "1.1"
    const val javax_mail = "1.4.7"
    const val javax_servlet_api = "2.5"
    const val jdot = "1.0"
    const val jetbrains_annotations = "20.1.0"
    const val jmh = "1.21"
    const val joda_time = "2.12.5"
    const val jooq = "3.14.16"
    const val juliet = "1.3.2"
    const val junit = "5.9.2"
    const val kotlin = "2.1.0"
    const val kotlin_logging = "1.8.3"
    const val kotlinx_benchmark = "0.4.6"
    const val kotlinx_cli = "0.3.5"
    const val kotlinx_collections_immutable = "0.3.5"
    const val kotlinx_coroutines = "1.6.4"
    const val kotlin_metadata = kotlin
    const val kotlinx_serialization = "1.8.0"
    const val licenser = "0.6.1"
    const val mockk = "1.13.3"
    const val sarif4k = "0.5.0"
    const val shadow = "8.1.1"
    const val slf4j = "1.7.36"
    const val soot_opentaint_fork = "4.4.0-FORK-2"
    const val sootup = "1.0.0"
    const val sqlite = "3.41.2.2"
    const val xodus = "2.0.1"
    const val rocks_db = "9.1.1"
    const val lmdb_java = "0.9.0"

    // libs for tests only
    const val jgit_test_only_version = "5.9.0.202009080501-r"
    const val commons_compress_test_only_version = "1.21"
}

fun dep(group: String, name: String, version: String): String = "$group:$name:$version"

object Libs {
    // https://github.com/junit-team/junit5
    const val junit_bom = "org.junit:junit-bom:${Versions.junit}"
    const val junit_jupiter = "org.junit.jupiter:junit-jupiter"
    val junit_jupiter_api = dep(
        group = "org.junit.jupiter",
        name = "junit-jupiter-api",
        version = Versions.junit
    )
    val junit_jupiter_engine = dep(
        group = "org.junit.jupiter",
        name = "junit-jupiter-engine",
        version = Versions.junit
    )
    val junit_jupiter_params = dep(
        group = "org.junit.jupiter",
        name = "junit-jupiter-params",
        version = Versions.junit
    )

    // https://github.com/MicroUtils/kotlin-logging
    val kotlin_logging = dep(
        group = "io.github.microutils",
        name = "kotlin-logging",
        version = Versions.kotlin_logging
    )

    // https://github.com/qos-ch/slf4j
    val slf4j_simple = dep(
        group = "org.slf4j",
        name = "slf4j-simple",
        version = Versions.slf4j
    )

    // https://github.com/google/guava
    val guava = dep(
        group = "com.google.guava",
        name = "guava",
        version = Versions.guava
    )

    // https://github.com/Kotlin/kotlinx.coroutines
    val kotlinx_coroutines_core = dep(
        group = "org.jetbrains.kotlinx",
        name = "kotlinx-coroutines-core",
        version = Versions.kotlinx_coroutines
    )
    val kotlinx_coroutines_jdk8 = dep(
        group = "org.jetbrains.kotlinx",
        name = "kotlinx-coroutines-jdk8",
        version = Versions.kotlinx_coroutines
    )
    val kotlinx_coroutines_reactor = dep(
        group = "org.jetbrains.kotlinx",
        name = "kotlinx-coroutines-reactor",
        version = Versions.kotlinx_coroutines
    )

    // https://github.com/Kotlin/kotlinx.collections.immutable
    val kotlinx_collections_immutable = dep(
        group = "org.jetbrains.kotlinx",
        name = "kotlinx-collections-immutable-jvm",
        version = Versions.kotlinx_collections_immutable
    )

    val kotlin_metadata_jvm = dep(
        group = "org.jetbrains.kotlin",
        name = "kotlin-metadata-jvm",
        version = Versions.kotlin_metadata
    )

    val javax_activation = dep(
        group = "javax.activation",
        name = "activation",
        version = Versions.javax_activation
    )
    val javax_mail = dep(
        group = "javax.mail",
        name = "mail",
        version = Versions.javax_mail
    )
    val javax_servlet_api = dep(
        group = "javax.servlet",
        name = "servlet-api",
        version = Versions.javax_servlet_api
    )

    // https://github.com/JodaOrg/joda-time
    val joda_time = dep(
        group = "joda-time",
        name = "joda-time",
        version = Versions.joda_time
    )

    // https://github.com/Kotlin/kotlinx.serialization
    val kotlinx_serialization_core = dep(
        group = "org.jetbrains.kotlinx",
        name = "kotlinx-serialization-core",
        version = Versions.kotlinx_serialization
    )
    val kotlinx_serialization_json = dep(
        group = "org.jetbrains.kotlinx",
        name = "kotlinx-serialization-json",
        version = Versions.kotlinx_serialization
    )
    val kotlinx_serialization_cbor = dep(
        group = "org.jetbrains.kotlinx",
        name = "kotlinx-serialization-cbor",
        version = Versions.kotlinx_serialization
    )

    // https://github.com/Kotlin/kotlinx-benchmark
    val kotlinx_benchmark_runtime = dep(
        group = "org.jetbrains.kotlinx",
        name = "kotlinx-benchmark-runtime",
        version = Versions.kotlinx_benchmark
    )

    // https://github.com/Kotlin/kotlinx-cli
    val kotlinx_cli = dep(
        group = "org.jetbrains.kotlinx",
        name = "kotlinx-cli",
        version = Versions.kotlinx_cli
    )

    // https://github.com/brettwooldridge/HikariCP
    val hikaricp = dep(
        group = "com.zaxxer",
        name = "HikariCP",
        version = Versions.hikaricp
    )

    // https://github.com/xerial/sqlite-jdbc
    val sqlite = dep(
        group = "org.xerial",
        name = "sqlite-jdbc",
        version = Versions.sqlite
    )

    // https://github.com/soot-oss/SootUp
    val sootup_core = dep(
        group = "org.soot-oss",
        name = "sootup.core",
        version = Versions.sootup
    )
    val sootup_java_bytecode = dep(
        group = "org.soot-oss",
        name = "sootup.java.bytecode",
        version = Versions.sootup
    )

    // https://github.com/Opentaint/soot
    val soot_opentaint_fork = dep(
        group = "org.opentaint.soot",
        name = "soot-opentaint-fork",
        version = Versions.soot_opentaint_fork
    )

    // https://github.com/gboersma/jdot
    val jdot = dep(
        group = "info.leadinglight",
        name = "jdot",
        version = Versions.jdot
    )

    // https://github.com/mockk/mockk
    val mockk = dep(
        group = "io.mockk",
        name = "mockk",
        version = Versions.mockk
    )

    // https://github.com/JetBrains/java-annotations
    val jetbrains_annotations = dep(
        group = "org.jetbrains",
        name = "annotations",
        version = Versions.jetbrains_annotations
    )

    // https://github.com/jOOQ/jOOQ
    val jooq = dep(
        group = "org.jooq",
        name = "jooq",
        version = Versions.jooq
    )
    val jooq_meta = dep(
        group = "org.jooq",
        name = "jooq-meta",
        version = Versions.jooq
    )
    val jooq_meta_extensions = dep(
        group = "org.jooq",
        name = "jooq-meta-extensions",
        version = Versions.jooq
    )
    val jooq_codegen = dep(
        group = "org.jooq",
        name = "jooq-codegen",
        version = Versions.jooq
    )
    val jooq_kotlin = dep(
        group = "org.jooq",
        name = "jooq-kotlin",
        version = Versions.jooq
    )

    // https://asm.ow2.io/
    val asm = dep(
        group = "org.ow2.asm",
        name = "asm",
        version = Versions.asm
    )
    val asm_tree = dep(
        group = "org.ow2.asm",
        name = "asm-tree",
        version = Versions.asm
    )
    val asm_commons = dep(
        group = "org.ow2.asm",
        name = "asm-commons",
        version = Versions.asm
    )
    val asm_util = dep(
        group = "org.ow2.asm",
        name = "asm-util",
        version = Versions.asm
    )

    // https://github.com/Opentaint/juliet-java-test-suite
    val juliet_support = dep(
        group = "com.github.Opentaint.juliet-java-test-suite",
        name = "support",
        version = Versions.juliet
    )

    val xodusUtils = dep(
        group = "org.jetbrains.xodus",
        name = "xodus-utils",
        version = Versions.xodus
    )

    val xodusApi = dep(
        group = "org.jetbrains.xodus",
        name = "xodus-openAPI",
        version = Versions.xodus
    )

    val xodusEnvironment = dep(
        group = "org.jetbrains.xodus",
        name = "xodus-environment",
        version = Versions.xodus
    )

    val lmdb_java = dep(
        group = "org.lmdbjava",
        name = "lmdbjava",
        version = Versions.lmdb_java
    )

    @Suppress("FunctionName")
    fun juliet_cwe(cweNum: Int) = dep(
        group = "com.github.Opentaint.juliet-java-test-suite",
        name = "cwe${cweNum}",
        version = Versions.juliet
    )

    // https://github.com/detekt/sarif4k
    val sarif4k = dep(
        group = "io.github.detekt.sarif4k",
        name = "sarif4k",
        version = Versions.sarif4k
    )

    val rocks_db = dep(
        group = "org.rocksdb",
        name = "rocksdbjni",
        version = Versions.rocks_db
    )

    val jgit_test_only_lib = dep(
        group = "org.eclipse.jgit",
        name = "org.eclipse.jgit",
        version = Versions.jgit_test_only_version
    )

    val commons_compress_test_only_lib = dep(
        group = "org.apache.commons",
        name = "commons-compress",
        version = Versions.commons_compress_test_only_version
    )
}

object Plugins {

    abstract class ProjectPlugin(val version: String, val id: String)

    // https://github.com/Kotlin/dokka
    object Dokka : ProjectPlugin(
        version = Versions.dokka,
        id = "org.jetbrains.dokka"
    )

    // https://github.com/michel-kraemer/gradle-download-task
    object GradleDownload : ProjectPlugin(
        version = Versions.gradle_download,
        id = "de.undercouch.download"
    )

    // https://github.com/ben-manes/gradle-versions-plugin
    object GradleVersions : ProjectPlugin(
        version = Versions.gradle_versions,
        id = "com.github.ben-manes.versions"
    )

    // https://github.com/Kotlin/kotlinx-benchmark
    object KotlinxBenchmark : ProjectPlugin(
        version = Versions.kotlinx_benchmark,
        id = "org.jetbrains.kotlinx.benchmark"
    )

    // https://github.com/CadixDev/licenser
    object Licenser : ProjectPlugin(
        version = Versions.licenser,
        id = "org.cadixdev.licenser"
    )

    // https://github.com/johnrengelman/shadow
    object Shadow : ProjectPlugin(
        version = Versions.shadow,
        id = "com.github.johnrengelman.shadow"
    )
}

fun PluginDependenciesSpec.id(plugin: Plugins.ProjectPlugin) {
    id(plugin.id).version(plugin.version)
}
