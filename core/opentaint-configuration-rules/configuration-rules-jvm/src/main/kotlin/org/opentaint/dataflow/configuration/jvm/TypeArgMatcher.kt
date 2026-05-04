package org.opentaint.dataflow.configuration.jvm

/**
 * A type-argument matcher that has been pre-resolved during rule resolution:
 * the erased-name matchers are already compiled to [ConditionNameMatcher],
 * so runtime evaluation only needs to dispatch on the structure.
 */
sealed interface TypeArgMatcher {

    data class Class(
        val name: ConditionNameMatcher,
        // null = no type-args constraint (matches raw / declared erasure).
        val typeArgs: List<TypeArgMatcher>?,
    ) : TypeArgMatcher

    data class Array(val element: TypeArgMatcher) : TypeArgMatcher
}
