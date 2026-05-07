package org.opentaint.dataflow.configuration.jvm

import org.opentaint.dataflow.configuration.jvm.serialized.SerializedSimpleNameMatcher
import org.opentaint.dataflow.configuration.jvm.serialized.SerializedTypeNameMatcher
import org.opentaint.ir.api.jvm.JIRArrayType
import org.opentaint.ir.api.jvm.JIRClassType
import org.opentaint.ir.api.jvm.JIRType
import org.opentaint.ir.api.jvm.JIRTypeVariable
import org.opentaint.ir.api.jvm.JIRUnboundWildcard

fun SerializedTypeNameMatcher.matchType(
    erasedTypeName: String,
    resolveType: () -> JIRType,
    erasedMatch: SerializedTypeNameMatcher.(String) -> Boolean,
): Boolean {
    if (!erasedMatch(erasedTypeName)) return false
    return matchTypeArgs(resolveType, erasedMatch)
}

private fun SerializedTypeNameMatcher.matchTypeArgs(
    resolveType: () -> JIRType?,
    erasedMatch: SerializedTypeNameMatcher.(String) -> Boolean,
): Boolean {
    return when (this) {
        is SerializedSimpleNameMatcher -> true // no type args

        is SerializedTypeNameMatcher.ClassPattern -> {
            val args = typeArgs ?: return true

            val type = resolveType()
            if (type !is JIRClassType) return false

            if (args.size != type.typeArguments.size) return false

            args.zip(type.typeArguments).all { (m, a) ->
                // Raw class type arguments (`JIRTypeVariable`) and unbounded
                // wildcards (`<?>`) denote "any type", so any pattern matcher
                // accepts them — the unknown type could be whatever the
                // pattern requires.
                if (a is JIRTypeVariable || a is JIRUnboundWildcard) return@all true
                m.matchType(a.erasedName(), resolveType = { a }, erasedMatch)
            }
        }

        is SerializedTypeNameMatcher.Array -> element.matchTypeArgs(
            resolveType = { (resolveType() as? JIRArrayType)?.elementType },
            erasedMatch
        )
    }
}

/**
 * Erased class name for matching — drops any generic decoration that
 * [JIRType.typeName] may carry (e.g. `Map<String, Object>` → `java.util.Map`)
 * and reduces a type variable / unbound wildcard to its declared erasure
 * (e.g. `E` → `java.lang.Object`) so string-based matchers can match against
 * pass-through rules whose return/parameter types show up as type variables
 * when resolved via the declaring class (e.g. `List.get` returns `E`).
 */
fun JIRType.erasedName(): String = when (this) {
    is JIRClassType -> jIRClass.name
    is JIRTypeVariable -> jIRClass.name
    is JIRUnboundWildcard -> jIRClass.name
    is JIRArrayType -> {
        val el = elementType
        when (el) {
            is JIRClassType -> el.jIRClass.name + "[]"
            is JIRTypeVariable -> el.jIRClass.name + "[]"
            is JIRUnboundWildcard -> el.jIRClass.name + "[]"
            else -> typeName
        }
    }
    else -> typeName
}
