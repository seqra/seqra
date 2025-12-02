@file:Suppress("ConstPropertyName")

import org.gradle.kotlin.dsl.PluginDependenciesSpecScope
import org.opentaint.common.ProjectPlugin
import org.opentaint.common.dep
import org.opentaint.common.id

object Versions {
    const val shadow = "8.3.3"
    const val logback = "1.4.8"
    const val slf4j = "1.6.1"
    const val zt_exec = "1.12"
}

object Libs {
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

    val zt_exec = dep(
        group = "org.zeroturnaround",
        name = "zt-exec",
        version = Versions.zt_exec
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
