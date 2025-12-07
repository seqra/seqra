@file:Suppress("ConstPropertyName")

import org.gradle.kotlin.dsl.PluginDependenciesSpecScope
import org.opentaint.common.ProjectPlugin
import org.opentaint.common.dep
import org.opentaint.common.id

object Versions {
    const val clikt = "5.0.0"
    const val commonsCli = "1.5.0"
    const val detekt = "1.23.7"
    const val ini4j = "0.5.4"
    const val logback = "1.4.8"
    const val mockk = "1.13.4"
    const val sarif4k = "0.5.0"
    const val shadow = "8.3.3"
    const val slf4j = "1.6.1"
    const val zt_exec = "1.12"
    const val fastutil = "8.5.13"
    const val burningwave = "12.62.7"
    const val jdot = "1.0"
    const val brics_automaton = "1.11-8"
    const val antlr = "4.9.3"

    const val opentaintUtil = "2025.07.29.6c2bc6d"
    const val opentaintConfig = "2025.07.29.6659284"
    const val opentaintProject = "2025.07.29.e0e1756"
    const val opentaintEngineApi = "2025.07.29.368835a"
    const val opentaintEngineApproximations = "2025.07.29.c05a72c"

    // versions for jvm samples
    object Samples {
        const val lombok = "1.18.20"
        const val slf4j = "1.7.36"
        const val javaxValidation = "2.0.0.Final"
        const val findBugs = "1.3.9-1"
        const val jetbrainsAnnotations = "16.0.2"
    }
}

object Libs {
    val jdot = dep(
        group = "info.leadinglight",
        name = "jdot",
        version = Versions.jdot
    )

    val brics_automaton = dep(
        group = "dk.brics.automaton",
        name = "automaton",
        version = Versions.brics_automaton
    )

    val antlr = dep(
        group = "org.antlr",
        name = "antlr4",
        version = Versions.antlr
    )

    val antlr_runtime = dep(
        group = "org.antlr",
        name = "antlr4-runtime",
        version = Versions.antlr
    )

    val burningwave_core = dep(
        group = "org.burningwave",
        name = "core",
        version = Versions.burningwave
    )

    val slf4j_api = dep(
        group = "org.slf4j",
        name = "slf4j-api",
        version = Versions.slf4j
    )

    // https://github.com/qos-ch/logback
    val logback = dep(
        group = "ch.qos.logback",
        name = "logback-classic",
        version = Versions.logback
    )

    // https://github.com/mockk/mockk
    val mockk = dep(
        group = "io.mockk",
        name = "mockk",
        version = Versions.mockk
    )

    // https://github.com/detekt/sarif4k
    val sarif4k = dep(
        group = "io.github.detekt.sarif4k",
        name = "sarif4k",
        version = Versions.sarif4k
    )

    // https://github.com/facebookarchive/ini4j
    val ini4j = dep(
        group = "org.ini4j",
        name = "ini4j",
        version = Versions.ini4j
    )

    // https://github.com/ajalt/clikt
    val clikt = dep(
        group = "com.github.ajalt.clikt",
        name = "clikt",
        version = Versions.clikt
    )

    val commonsCli = dep(
        group = "commons-cli",
        name = "commons-cli",
        version = Versions.commonsCli
    )

    val zt_exec = dep(
        group = "org.zeroturnaround",
        name = "zt-exec",
        version = Versions.zt_exec
    )

    val fastutil = dep(
        group = "it.unimi.dsi",
        name = "fastutil-core",
        version = Versions.fastutil,
    )

    val opentaintUtilJvm = dep(
        group = "org.opentaint.utils",
        name = "opentaint-jvm-util",
        version = Versions.opentaintUtil,
    )

    val opentaintUtilCli = dep(
        group = "org.opentaint.utils",
        name = "cli-util",
        version = Versions.opentaintUtil,
    )

    val opentaintRulesJvm = dep(
        group = "org.opentaint.configuration",
        name = "configuration-rules-jvm",
        version = Versions.opentaintConfig,
    )

    val opentaintProject = dep(
        group = "org.opentaint.project",
        name = "opentaint-project-model",
        version = Versions.opentaintProject
    )

    val opentaint_engine_api = dep(
        group = "org.opentaint.jvm.engine.api",
        name = "opentaint-jvm-engine-api",
        version = Versions.opentaintEngineApi
    )

    val opentaint_engine_approximations = dep(
        group = "org.opentaint.engine.jvm.approximations",
        name = "approximations",
        version = Versions.opentaintEngineApproximations,
    )
}

object Plugins {
    // https://github.com/GradleUp/shadow
    val Shadow = ProjectPlugin(
        id = "com.gradleup.shadow",
        version = Versions.shadow
    )
}

fun PluginDependenciesSpecScope.shadowPlugin() = id(Plugins.Shadow)
