package org.opentaint.jvm.sast.dataflow.rules

import org.opentaint.dataflow.configuration.jvm.serialized.SerializedSimpleNameMatcher
import org.opentaint.dataflow.configuration.jvm.serialized.SerializedSimpleNameMatcher.Pattern
import org.opentaint.dataflow.configuration.jvm.serialized.SerializedSimpleNameMatcher.Simple
import org.opentaint.dataflow.configuration.jvm.serialized.SerializedTypeNameMatcher
import org.opentaint.dataflow.configuration.jvm.serialized.SerializedTypeNameMatcher.ClassPattern

fun SerializedTypeNameMatcher.match(pm: PatternManager, name: String): Boolean {
    if (matchNormalizedTypeName(pm, name)) return true

    val nameWithDots = name.innerClassNameWithDots()
        ?: return false

    return matchNormalizedTypeName(pm, nameWithDots)
}

private fun SerializedTypeNameMatcher.matchNormalizedTypeName(pm: PatternManager, name: String): Boolean = when (this) {
    is SerializedSimpleNameMatcher -> match(pm, name)

    is ClassPattern -> {
        val (pkgName, clsName) = splitClassName(name)
        `package`.matchNormalizedTypeName(pm, pkgName) && `class`.matchNormalizedTypeName(pm, clsName)
    }

    is SerializedTypeNameMatcher.Array -> {
        val nameWithoutArrayModifier = name.removeSuffix("[]")
        name != nameWithoutArrayModifier && element.matchNormalizedTypeName(pm, nameWithoutArrayModifier)
    }
}

fun SerializedSimpleNameMatcher.match(patternManager: PatternManager, name: String): Boolean = when (this) {
    is Simple -> if (value == "*") true else value == name
    is Pattern -> isAny() || patternManager.matchPattern(pattern, name)
}
