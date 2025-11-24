package org.opentaint.dataflow.configuration.jvm

import org.opentaint.ir.api.jvm.JIRClasspath
import org.opentaint.ir.api.jvm.JIRPrimitiveType
import org.opentaint.ir.api.jvm.JIRRefType
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

        is JIRTypeNamePatternMatcher -> nameMatches(pattern, type.typeName)
    }
}

fun TypeMatcher.resolveTypeMatcherCondition(
    cp: JIRClasspath,
    baseTypes: Set<JIRType?>,
    nameMatches: (NameMatcher, String) -> Boolean
): Pair<List<JIRType>, Set<String>>? {
    when (this) {
        is AnyTypeMatcher -> return null

        is JIRTypeNameMatcher -> {
            val type = cp.findTypeOrNull(typeName) ?: return null
            return listOf(type) to emptySet()
        }

        is PrimitiveNameMatcher -> {
            val type = primitiveTypes(cp).first { name == it.typeName }
            return listOf(type) to emptySet()
        }

        is TypePatternMatcher -> {
            // todo: really no primitive types?
            val refTypes = baseTypes.filterIsInstanceTo<JIRRefType, _>(hashSetOf())
            return resolveTypePatternMatcherCondition(cp, refTypes, nameMatches)
        }
    }
}

fun TypePatternMatcher.resolveTypePatternMatcherCondition(
    cp: JIRClasspath,
    baseTypes: Set<JIRType?>,
    nameMatches: (NameMatcher, String) -> Boolean
): Pair<List<JIRType>, Set<String>> {
    if (baseTypes.isEmpty()) return (emptyList<JIRType>() to emptySet())

    val types = mutableListOf<JIRType>()

    val unresolvedExactClassNameToPkg = hashMapOf<String, MutableSet<NameMatcher>>()
    val unresolvedOther = hashSetOf<TypePatternMatcher>()

    when (this) {
        is ClassMatcher -> {
            val typeMatchers = extractAlternatives()

            for (matcher in typeMatchers) {
                val pkgMatcher = matcher.pkg
                val clsMatcher = matcher.classNameMatcher

                if (pkgMatcher is NameExactMatcher) {
                    if (clsMatcher is NameExactMatcher) {
                        val type = cp.findTypeOrNull("${pkgMatcher.name}$DOT_DELIMITER${clsMatcher.name}")
                            ?: continue

                        types.add(type)
                    } else {
                        unresolvedOther.add(matcher)
                    }
                } else {
                    if (clsMatcher is NameExactMatcher) {
                        unresolvedExactClassNameToPkg.getOrPut(clsMatcher.name, ::hashSetOf).add(pkgMatcher)
                    } else {
                        unresolvedOther.add(matcher)
                    }
                }
            }
        }

        is JIRTypeNamePatternMatcher -> {
            unresolvedOther.add(this)
        }
    }

    for ((cls, packages) in unresolvedExactClassNameToPkg) {
        val classNameIdx = cp.db.features.filterIsInstance<JIRClassNameFeature>().first()
        val matchedClassNames = mutableListOf<String>()
        classNameIdx.filterClassesTo(cp, matchedClassNames, className = cls)

        for (pkgMatcher in packages) {
            val matched = matchedClassNames.filter {
                nameMatches(pkgMatcher, it.substringBeforeLast(DOT_DELIMITER, missingDelimiterValue = ""))
            }
            matched.mapNotNullTo(types) { className ->
                cp.findTypeOrNull(className)
            }
        }
    }

    if (unresolvedOther.isEmpty()) return types to emptySet()

    val patterns = hashSetOf<String>()
    for (unresolved in unresolvedOther) {
        when (unresolved) {
            is ClassMatcher -> {
                val packageName = when (val pn = unresolved.pkg) {
                    AnyNameMatcher -> ".*"
                    is NameExactMatcher -> pn.name.replace(".", "\\.")
                    is NamePatternMatcher -> pn.pattern
                }

                val className = when (val cn = unresolved.classNameMatcher) {
                    AnyNameMatcher -> ".*"
                    is NameExactMatcher -> cn.name
                    is NamePatternMatcher -> cn.pattern
                }

                patterns += "$packageName\\.$className"
            }

            is JIRTypeNamePatternMatcher -> patterns.add(unresolved.pattern.pattern)
        }
    }

    return types to patterns
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
