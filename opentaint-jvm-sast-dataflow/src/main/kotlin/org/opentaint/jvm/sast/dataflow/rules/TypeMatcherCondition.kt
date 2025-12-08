package org.opentaint.jvm.sast.dataflow.rules

import org.opentaint.dataflow.configuration.jvm.ConditionNameMatcher
import org.opentaint.dataflow.configuration.jvm.serialized.SerializedNameMatcher
import org.opentaint.dataflow.configuration.jvm.serialized.SerializedNameMatcher.ClassPattern
import org.opentaint.dataflow.configuration.jvm.serialized.SerializedNameMatcher.Pattern
import org.opentaint.dataflow.configuration.jvm.serialized.SerializedNameMatcher.Simple
import java.util.BitSet

fun SerializedNameMatcher.toConditionNameMatcher(patternManager: PatternManager): ConditionNameMatcher? {
    return when (this) {
        is Simple -> ConditionNameMatcher.Concrete(value)
        is Pattern -> {
            if (isAny()) return null
            createPattern(pattern, patternManager)
        }

        is ClassPattern -> {
            when (val pkgMatcher = `package`) {
                is ClassPattern -> error("impossible")

                is Simple -> when (val clsMatcher = `class`) {
                    is ClassPattern -> error("impossible")

                    is Simple -> {
                        val name = "${pkgMatcher.value}.${clsMatcher.value}"
                        ConditionNameMatcher.Concrete(name)
                    }

                    is Pattern -> {
                        if (clsMatcher.isAny()) {
                           return ConditionNameMatcher.PatternStartsWith(pkgMatcher.value)
                        }

                        val pkgPattern = nameToPattern(pkgMatcher.value)
                        val pattern = classNamePattern(pkgPattern, clsMatcher.pattern)
                        createPattern(pattern, patternManager)
                    }
                }

                is Pattern -> when (val clsMatcher = `class`) {
                    is ClassPattern -> error("impossible")
                    is Simple -> {
                        if (pkgMatcher.isAny()) {
                            return ConditionNameMatcher.PatternEndsWith(clsMatcher.value)
                        }

                        val clsPattern = nameToPattern(clsMatcher.value)
                        val pattern = classNamePattern(pkgMatcher.pattern, clsPattern)
                        createPattern(pattern, patternManager)
                    }

                    is Pattern -> {
                        val pattern = classNamePattern(pkgMatcher.pattern, clsMatcher.pattern)
                        createPattern(pattern, patternManager)
                    }
                }
            }
        }
    }
}

private fun createPattern(pattern: String, patternManager: PatternManager): ConditionNameMatcher? {
    if (pattern.startsWith(".*")) {
        val suffix = pattern.removePrefix(".*")
        val concreteSuffix = tryConcretizePattern(suffix)
        if (concreteSuffix != null) {
            return ConditionNameMatcher.PatternEndsWith(concreteSuffix)
        }
    }

    if (pattern.endsWith(".*")) {
        val prefix = pattern.removeSuffix(".*")
        val concretePrefix = tryConcretizePattern(prefix)
        if (concretePrefix != null) {
            return ConditionNameMatcher.PatternStartsWith(concretePrefix)
        }
    }

    return ConditionNameMatcher.Pattern(patternManager.compilePattern(pattern))
}

private fun tryConcretizePattern(pattern: String): String? {
    val escapedIndices = BitSet()
    val unescapedStr = StringBuilder()

    var idx = 0
    while (idx < pattern.length) {
        val c = pattern[idx]
        if (c == '\\') {
            if (idx == pattern.length - 1) return null
            escapedIndices.set(unescapedStr.length)
            unescapedStr.append(pattern[idx + 1])
            idx += 2
        } else {
            unescapedStr.append(c)
            idx++
        }
    }

    for ((i, ch) in unescapedStr.withIndex()) {
        if (ch.isLetterOrDigit()) continue
        if (escapedIndices.get(i)) continue

        return null
    }

    return unescapedStr.toString()
}
