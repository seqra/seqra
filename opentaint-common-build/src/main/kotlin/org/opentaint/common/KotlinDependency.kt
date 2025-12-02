@file:Suppress("ConstPropertyName")

package org.opentaint.common

object KotlinDependency {
    object Versions {
        const val kotlin = "2.1.0"
        const val kotlin_logging = "3.0.5"
        const val kotlinx_collections = "0.3.8"
        const val kotlinx_coroutines = "1.10.0"
        const val kotlinx_serialization = "1.7.3"
        const val kaml = "0.73.0"
    }

    object Libs {
        val reflect = dep(
            group = "org.jetbrains.kotlin",
            name = "kotlin-reflect",
            version = Versions.kotlin
        )

        // https://github.com/oshai/kotlin-logging
        val kotlin_logging = dep(
            group = "io.github.microutils",
            name = "kotlin-logging",
            version = Versions.kotlin_logging
        )

        // https://github.com/Kotlin/kotlinx.coroutines
        val kotlinx_coroutines_core = dep(
            group = "org.jetbrains.kotlinx",
            name = "kotlinx-coroutines-core",
            version = Versions.kotlinx_coroutines
        )

        // https://github.com/Kotlin/kotlinx.collections.immutable
        val kotlinx_collections = dep(
            group = "org.jetbrains.kotlinx",
            name = "kotlinx-collections-immutable-jvm",
            version = Versions.kotlinx_collections
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

        val kaml = dep(
            group = "com.charleskorn.kaml",
            name = "kaml",
            version = Versions.kaml
        )
    }

    object Plugins {
        val KotlinSerialization = ProjectPlugin(
            id = "org.jetbrains.kotlin.plugin.serialization",
            version = Versions.kotlin
        )
    }
}
