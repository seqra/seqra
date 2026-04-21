package org.opentaint.jvm.sast.dataflow.rules

import org.opentaint.dataflow.configuration.jvm.ConditionNameMatcher
import org.opentaint.dataflow.configuration.jvm.serialized.SerializedSimpleNameMatcher
import org.opentaint.dataflow.configuration.jvm.serialized.SerializedTypeNameMatcher
import org.opentaint.dataflow.configuration.jvm.serialized.SerializedTypeNameMatcher.ClassPattern
import org.opentaint.dataflow.configuration.jvm.serialized.SerializedSimpleNameMatcher.Pattern
import org.opentaint.dataflow.configuration.jvm.serialized.SerializedSimpleNameMatcher.Simple
import java.util.BitSet

fun SerializedSimpleNameMatcher.toConditionNameMatcher(patternManager: PatternManager): ConditionNameMatcher.Simple? {
    return when (this) {
        is Simple -> ConditionNameMatcher.Concrete(value)
        is Pattern -> {
            if (isAny()) return null

            ConditionNameMatcher.Pattern(patternManager.compilePattern(pattern))
        }
    }
}

fun SerializedTypeNameMatcher.toConditionNameMatcher(patternManager: PatternManager): ConditionNameMatcher? {
    return when (this) {
        is Simple -> ConditionNameMatcher.Concrete(value)
        is Pattern -> {
            if (isAny()) return null
            createPattern(pattern, patternManager)
        }

        is ClassPattern -> {
            when (val pkgMatcher = `package`) {
                is Simple -> conditionNameMatcherWithSimplePackage(patternManager, `class`, pkgMatcher)
                is Pattern -> conditionNameMatcherWithPatternPackage(patternManager, `class`, pkgMatcher)
            }
        }

        is SerializedTypeNameMatcher.Array -> {
            element.toConditionNameMatcher(patternManager)?.addSuffix("[]", patternManager)
        }

        // A wildcard matcher has no erased-name projection; there is no
        // meaningful class-name `ConditionNameMatcher` to produce.
        is SerializedTypeNameMatcher.Wildcard -> null
    }
}

private fun conditionNameMatcherWithSimplePackage(
    patternManager: PatternManager,
    clsMatcher: SerializedSimpleNameMatcher,
    pkgMatcher: Simple
): ConditionNameMatcher {
    return when (clsMatcher) {
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
}

private fun conditionNameMatcherWithPatternPackage(
    patternManager: PatternManager,
    clsMatcher: SerializedSimpleNameMatcher,
    pkgMatcher: Pattern
): ConditionNameMatcher {
    return when (clsMatcher) {
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

private fun createPattern(pattern: String, patternManager: PatternManager): ConditionNameMatcher {
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

private fun ConditionNameMatcher.addSuffix(
    suffix: String,
    patternManager: PatternManager,
): ConditionNameMatcher = when (this) {
    is ConditionNameMatcher.AnyName -> ConditionNameMatcher.PatternEndsWith(suffix)
    is ConditionNameMatcher.Concrete -> ConditionNameMatcher.Concrete(name + suffix)
    is ConditionNameMatcher.PatternEndsWith -> ConditionNameMatcher.PatternEndsWith(this.suffix + suffix)
    is ConditionNameMatcher.PatternStartsWith -> {
        ConditionNameMatcher.Pattern(patternManager.compilePattern("${prefix}.*$suffix"))
    }

    is ConditionNameMatcher.Pattern -> {
        val pattern = this.pattern.pattern
        val patternWithSuffix = if (!pattern.endsWith('$')) {
            "${pattern}.*$suffix"
        } else {
            val beforeDollar = pattern.removeSuffix("$")
            "${beforeDollar}.*$suffix$"
        }
        ConditionNameMatcher.Pattern(patternManager.compilePattern(patternWithSuffix))
    }
}
