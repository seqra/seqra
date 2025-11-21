package org.opentaint.dataflow.configuration.jvm

sealed interface TypeMatcher

data class PrimitiveNameMatcher(val name: String) : TypeMatcher

data class ClassMatcher(
    val pkg: NameMatcher,
    val classNameMatcher: NameMatcher,
) : TypeMatcher

data class JIRTypeNameMatcher(val typeName: String) : TypeMatcher

object AnyTypeMatcher : TypeMatcher {
    override fun toString(): String = javaClass.simpleName
}
