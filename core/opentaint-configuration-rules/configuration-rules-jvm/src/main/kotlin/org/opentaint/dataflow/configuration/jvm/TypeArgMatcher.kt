package org.opentaint.dataflow.configuration.jvm

import org.opentaint.ir.api.jvm.JIRArrayType
import org.opentaint.ir.api.jvm.JIRClassType
import org.opentaint.ir.api.jvm.JIRType

/**
 * A type-argument matcher that has been pre-resolved during rule resolution:
 * the erased-name matchers are already compiled to [ConditionNameMatcher],
 * so runtime evaluation only needs to dispatch on the structure.
 */
sealed interface TypeArgMatcher {
    fun matchType(type: JIRType): Boolean

    data class Class(
        val name: ConditionNameMatcher,
        // null = no type-args constraint (matches raw / declared erasure).
        val typeArgs: List<TypeArgMatcher>?,
    ) : TypeArgMatcher {
        override fun matchType(type: JIRType): Boolean {
            if (!name.match(type.erasedName())) return false

            if (typeArgs == null) {
                return if (type is JIRClassType) type.isRawLike() else true
            }

            if (type !is JIRClassType) return true
            if (typeArgs.size != type.typeArguments.size) return false
            return typeArgs.zip(type.typeArguments).all { (m, a) -> m.matchType(a) }
        }
    }

    data class Array(val element: TypeArgMatcher) : TypeArgMatcher {
        override fun matchType(type: JIRType): Boolean =
            type is JIRArrayType && element.matchType(type.elementType)
    }
}
