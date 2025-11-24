package org.opentaint.dataflow.configuration.jvm

sealed interface TypeMatcher

sealed interface TypePatternMatcher : TypeMatcher

data class PrimitiveNameMatcher(val name: String) : TypeMatcher

data class ClassMatcher(
    val pkg: NameMatcher,
    val classNameMatcher: NameMatcher,
) : TypePatternMatcher

data class JIRTypeNameMatcher(val typeName: String) : TypeMatcher

data class JIRTypeNamePatternMatcher(val pattern: NamePatternMatcher) : TypePatternMatcher

data object AnyTypeMatcher : TypeMatcher
