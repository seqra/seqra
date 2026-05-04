package org.opentaint.jvm.sast.dataflow.rules

import org.opentaint.dataflow.configuration.jvm.serialized.SerializedSimpleNameMatcher
import org.opentaint.dataflow.configuration.jvm.serialized.SerializedTypeNameMatcher
import org.opentaint.dataflow.configuration.jvm.serialized.SerializedTypeNameMatcher.ClassPattern
import org.opentaint.dataflow.configuration.jvm.serialized.SerializedSimpleNameMatcher.Pattern
import org.opentaint.dataflow.configuration.jvm.serialized.SerializedSimpleNameMatcher.Simple

private const val DOT_DELIMITER = "."

private const val INNER_CLASS_DELIMITER = "$"

fun Pattern.isAny(): Boolean = pattern == ".*"

fun SerializedTypeNameMatcher.normalizeAnyName(): SerializedTypeNameMatcher = when (this) {
    is SerializedSimpleNameMatcher -> normalizeAnyName()
    is ClassPattern -> ClassPattern(`package`.normalizeAnyName(), `class`.normalizeAnyName(), typeArgs?.map { it.normalizeAnyName() })
    is SerializedTypeNameMatcher.Array -> SerializedTypeNameMatcher.Array(element.normalizeAnyName())
}

fun SerializedSimpleNameMatcher.normalizeAnyName(): SerializedSimpleNameMatcher = when (this) {
    is Pattern -> this
    is Simple -> if (value == "*") anyNameMatcher() else this
}

fun nameToPattern(name: String): String = name.replace(DOT_DELIMITER, "\\.")

fun String.innerClassNameWithDots(): String? {
    val nameWithDots = replace(INNER_CLASS_DELIMITER, DOT_DELIMITER)
    return nameWithDots.takeIf { it != this }
}

// todo: check pattern for line start/end markers
fun classNamePattern(pkgPattern: String, clsPattern: String): String =
    "$pkgPattern\\.$clsPattern"

fun anyNameMatcher(): SerializedSimpleNameMatcher = Pattern(".*")

fun splitClassName(className: String): Pair<String, String> {
    val simpleName = className.substringAfterLast(DOT_DELIMITER)
    val pkgName = className.substringBeforeLast(DOT_DELIMITER, missingDelimiterValue = "")
    return pkgName to simpleName
}

fun joinClassName(pkgName: String, className: String): String =
    "${pkgName}$DOT_DELIMITER${className}"
