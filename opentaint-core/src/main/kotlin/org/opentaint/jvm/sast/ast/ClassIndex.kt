package org.opentaint.jvm.sast.ast

import java.nio.file.Path

interface ClassIndex {
    enum class Language {
        Java, Kotlin
    }

    val language: Language

    data class LookupResult(
        val priority: Int,
        val sources: Set<Path>
    )

    fun lookup(fqn: String): LookupResult?
}
