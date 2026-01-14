package org.opentaint.jvm.sast.dataflow.rules

import org.opentaint.dataflow.configuration.jvm.serialized.SerializedNameMatcher
import org.opentaint.dataflow.configuration.jvm.serialized.SerializedNameMatcher.ClassPattern
import org.opentaint.dataflow.configuration.jvm.serialized.SerializedNameMatcher.Pattern
import org.opentaint.dataflow.configuration.jvm.serialized.SerializedNameMatcher.Simple

private const val DOT_DELIMITER = "."

fun Pattern.isAny(): Boolean = pattern == ".*"

fun SerializedNameMatcher.normalizeAnyName(): SerializedNameMatcher = when (this) {
    is ClassPattern -> {
        ClassPattern(`package`.normalizeAnyName(), `class`.normalizeAnyName())
    }

    is Pattern -> this
    is Simple -> if (value == "*") anyNameMatcher() else this
    is SerializedNameMatcher.Array -> SerializedNameMatcher.Array(element.normalizeAnyName())
}

fun nameToPattern(name: String): String = name.replace(DOT_DELIMITER, "\\.")

// todo: check pattern for line start/end markers
fun classNamePattern(pkgPattern: String, clsPattern: String): String =
    "$pkgPattern\\.$clsPattern"

fun anyNameMatcher(): SerializedNameMatcher = Pattern(".*")

fun splitClassName(className: String): Pair<String, String> {
    val simpleName = className.substringAfterLast(DOT_DELIMITER)
    val pkgName = className.substringBeforeLast(DOT_DELIMITER, missingDelimiterValue = "")
    return pkgName to simpleName
}

fun joinClassName(pkgName: String, className: String): String =
    "${pkgName}$DOT_DELIMITER${className}"
