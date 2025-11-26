package org.opentaint.dataflow.configuration.jvm

import org.opentaint.ir.api.jvm.TypeName

fun TypeMatcher.match(type: TypeName, nameMatches: (NameMatcher, String) -> Boolean): Boolean =
    match(type.typeName, nameMatches)

fun TypeMatcher.match(type: String, nameMatches: (NameMatcher, String) -> Boolean): Boolean {
    return when (this) {
        AnyTypeMatcher -> true
        is JIRTypeNameMatcher -> this.typeName == type
        is PrimitiveNameMatcher -> this.name == type

        is ClassMatcher -> {
            val classSimpleName = type.substringAfterLast(DOT_DELIMITER)
            val packageName = type.substringBeforeLast(DOT_DELIMITER, missingDelimiterValue = "")
            nameMatches(classNameMatcher, classSimpleName) && nameMatches(pkg, packageName)
        }

        is JIRTypeNamePatternMatcher -> nameMatches(pattern, type)
    }
}
