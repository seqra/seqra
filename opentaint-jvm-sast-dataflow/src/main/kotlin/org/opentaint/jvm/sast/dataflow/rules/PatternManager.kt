package org.opentaint.jvm.sast.dataflow.rules

class PatternManager {
    private val compiledMatchers = hashMapOf<String, Regex>()

    fun compilePattern(pattern: String): Regex =
        compiledMatchers.getOrPut(pattern) { pattern.toRegex() }

    fun matchPattern(pattern: String, str: String): Boolean =
        compilePattern(pattern).containsMatchIn(str)
}
