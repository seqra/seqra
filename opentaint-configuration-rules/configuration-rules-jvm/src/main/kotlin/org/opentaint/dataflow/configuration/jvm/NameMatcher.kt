package org.opentaint.dataflow.configuration.jvm

sealed interface NameMatcher

data class NameExactMatcher(
    val name: String,
) : NameMatcher

data class NamePatternMatcher(
    val pattern: String,
) : NameMatcher

object AnyNameMatcher : NameMatcher {
    override fun toString(): String = javaClass.simpleName
}
