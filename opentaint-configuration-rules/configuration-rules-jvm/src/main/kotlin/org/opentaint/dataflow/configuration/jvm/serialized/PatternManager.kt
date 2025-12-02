package org.opentaint.dataflow.configuration.jvm.serialized

class PatternManager {
    private val compiledMatchers = hashMapOf<String, Regex>()

    fun compilePattern(pattern: String): Regex =
        compiledMatchers.getOrPut(pattern) { pattern.toRegex() }

    fun matchPattern(pattern: String, str: String): Boolean =
        compilePattern(pattern).containsMatchIn(str)
}
