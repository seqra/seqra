package org.opentaint.dataflow.configuration.jvm

import org.opentaint.ir.api.jvm.JIRClasspath
import org.opentaint.ir.api.jvm.JIRPrimitiveType
import org.opentaint.ir.api.jvm.JIRType
import org.opentaint.ir.api.jvm.TypeName
import org.opentaint.ir.api.jvm.ext.boolean
import org.opentaint.ir.api.jvm.ext.byte
import org.opentaint.ir.api.jvm.ext.char
import org.opentaint.ir.api.jvm.ext.double
import org.opentaint.ir.api.jvm.ext.float
import org.opentaint.ir.api.jvm.ext.int
import org.opentaint.ir.api.jvm.ext.long
import org.opentaint.ir.api.jvm.ext.short

fun TypeMatcher.match(type: TypeName, nameMatches: (NameMatcher, String) -> Boolean): Boolean {
    return when (this) {
        AnyTypeMatcher -> true
        is JIRTypeNameMatcher -> this.typeName == type.typeName
        is PrimitiveNameMatcher -> this.name == type.typeName

        is ClassMatcher -> {
            val classSimpleName = type.typeName.substringAfterLast(DOT_DELIMITER)
            val packageName = type.typeName.substringBeforeLast(DOT_DELIMITER, missingDelimiterValue = "")
            nameMatches(classNameMatcher, classSimpleName) && nameMatches(pkg, packageName)
        }
    }
}

fun TypeMatcher.resolveTypeMatcherCondition(
    cp: JIRClasspath,
    nameMatches: (NameMatcher, String) -> Boolean
): (Position) -> Condition {
    if (this is AnyTypeMatcher) {
        return { ConstantTrue }
    }

    if (this is JIRTypeNameMatcher) {
        val type = cp.findTypeOrNull(typeName) ?: return { ConstantTrue }
        return { pos: Position -> TypeMatches(pos, type) }
    }

    if (this is PrimitiveNameMatcher) {
        val types = primitiveTypes(cp).filter { name == it.typeName }
        return { pos: Position -> mkOr(types.map { TypeMatches(pos, it) }) }
    }

    val typeMatchers = (this as ClassMatcher).extractAlternatives()
    val unresolvedMatchers = mutableListOf<ClassMatcher>()
    val types = mutableListOf<JIRType>()

    for (matcher in typeMatchers) {
        val pkgMatcher = matcher.pkg
        val clsMatcher = matcher.classNameMatcher

        if (pkgMatcher !is NameExactMatcher || clsMatcher !is NameExactMatcher) {
            unresolvedMatchers += matcher
            continue
        }

        val type = cp.findTypeOrNull("${pkgMatcher.name}$DOT_DELIMITER${clsMatcher.name}")
            ?: continue

        types.add(type)
    }

    if (unresolvedMatchers.isNotEmpty()) {
        val classNameIdx = cp.db.features.filterIsInstance<JIRClassNameFeature>().first()
        unresolvedMatchers.forEach { classMatcher ->
            val matchedClassNames = mutableListOf<String>()

            when (val name = classMatcher.classNameMatcher) {
                is NameExactMatcher -> {
                    classNameIdx.filterClassesTo(cp, matchedClassNames, name.name)
                }

                AnyNameMatcher -> {
                    classNameIdx.filterClassesTo(cp, matchedClassNames)
                }

                is NamePatternMatcher -> {
                    classNameIdx.filterClassesTo(cp, matchedClassNames)
                    matchedClassNames.removeAll { !nameMatches(name, it.substringAfterLast('.')) }
                }
            }

            when (val pkg = classMatcher.pkg) {
                AnyNameMatcher -> {}
                is NameExactMatcher,
                is NamePatternMatcher -> {
                    matchedClassNames.removeAll { !nameMatches(pkg, it.substringBeforeLast('.', "")) }
                }
            }

            matchedClassNames.mapNotNullTo(types) { className ->
                cp.findTypeOrNull(className)
            }
        }
    }

    return { pos: Position -> mkOr(types.map { TypeMatches(pos, it) }) }
}

private fun primitiveTypes(cp: JIRClasspath): Set<JIRPrimitiveType> = setOf(
    cp.boolean,
    cp.byte,
    cp.short,
    cp.int,
    cp.long,
    cp.char,
    cp.float,
    cp.double,
)
