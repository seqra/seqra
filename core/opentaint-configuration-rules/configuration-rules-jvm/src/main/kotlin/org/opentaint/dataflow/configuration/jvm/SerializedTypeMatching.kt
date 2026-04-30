package org.opentaint.dataflow.configuration.jvm

import org.opentaint.dataflow.configuration.jvm.serialized.SerializedTypeNameMatcher
import org.opentaint.ir.api.jvm.JIRArrayType
import org.opentaint.ir.api.jvm.JIRClassType
import org.opentaint.ir.api.jvm.JIRType
import org.opentaint.ir.api.jvm.JIRTypeVariable
import org.opentaint.ir.api.jvm.JIRUnboundWildcard

/**
 * A class type is "raw-like" when no concrete substitution has been applied to
 * its type arguments — either the list is empty, or every argument is still a
 * declared type variable / unbound wildcard. Matches a no-type-arg rule pattern.
 */
fun JIRClassType.isRawLike(): Boolean {
    if (typeArguments.isEmpty()) return true
    return typeArguments.all { it is JIRTypeVariable || it is JIRUnboundWildcard }
}

/**
 * Structural match of a serialized type-name matcher against a resolved
 * [JIRType], including recursion into generic type arguments.
 *
 * Erased-name matching is delegated to [erasedMatch] so each caller can plug in
 * its own name-matching primitive (e.g. a `PatternManager`-cached matcher vs.
 * a plain `Regex`). The matcher receiver on [erasedMatch] is the sub-pattern
 * being tested, not the root `this`.
 */
fun SerializedTypeNameMatcher.matchType(
    type: JIRType,
    erasedMatch: SerializedTypeNameMatcher.(String) -> Boolean,
): Boolean = when {
    this is SerializedTypeNameMatcher.ClassPattern && typeArgs == null && type is JIRClassType ->
        erasedMatch(type.erasedName()) && type.isRawLike()

    this is SerializedTypeNameMatcher.ClassPattern && typeArgs == null ->
        erasedMatch(type.erasedName())

    this is SerializedTypeNameMatcher.ClassPattern && type is JIRClassType -> {
        val args = typeArgs!!
        erasedMatch(type.erasedName()) &&
            args.size == type.typeArguments.size &&
            args.zip(type.typeArguments).all { (m, a) -> m.matchType(a, erasedMatch) }
    }

    this is SerializedTypeNameMatcher.Array && type is JIRArrayType ->
        element.matchType(type.elementType, erasedMatch)

    else -> erasedMatch(type.erasedName())
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
