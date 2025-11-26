package org.opentaint.org.opentaint.semgrep.pattern.conversion

import org.opentaint.org.opentaint.semgrep.pattern.conversion.SemgrepPatternAction.SignatureModifier

sealed interface TypeNamePattern {
    data class FullyQualified(val name: String) : TypeNamePattern
    data class ClassName(val name: String) : TypeNamePattern
    data class PrimitiveName(val name: String) : TypeNamePattern
    data class MetaVar(val metaVar: String) : TypeNamePattern
    data object AnyType : TypeNamePattern
}

sealed interface ParamPosition {
    data class Concrete(val idx: Int) : ParamPosition
    data class Any(val paramClassifier: String) : ParamPosition
}

sealed interface ParamCondition {
    data class And(val conditions: List<ParamCondition>) : ParamCondition

    data object True : ParamCondition

    sealed interface Atom : ParamCondition

    data class TypeIs(val typeName: TypeNamePattern) : Atom

    data object AnyStringLiteral : Atom

    data class StringValueMetaVar(val metaVar: String) : Atom

    data class ParamModifier(val modifier: SignatureModifier): Atom
}

data class SpecificBoolValue(val value: Boolean) : ParamCondition.Atom

data class SpecificStringValue(val value: String) : ParamCondition.Atom

data class IsMetavar(val metavar: String) : ParamCondition.Atom

fun ParamCondition.collectMetavarTo(dst: MutableSet<String>) {
    when (this) {
        is ParamCondition.And -> conditions.forEach { it.collectMetavarTo(dst) }
        is IsMetavar -> dst.add(metavar)
        else -> {
            // no metavars
        }
    }
}

fun mkAnd(conditions: Set<ParamCondition>): ParamCondition = when (conditions.size) {
    0 -> ParamCondition.True
    1 -> conditions.first()
    else -> ParamCondition.And(conditions.toList())
}

data class ParamPattern(val position: ParamPosition, val condition: ParamCondition)

sealed interface ParamConstraint {
    val conditions: List<ParamCondition>

    data class Concrete(val params: List<ParamCondition>) : ParamConstraint {
        override val conditions: List<ParamCondition> get() = params
    }

    data class Partial(val params: List<ParamPattern>) : ParamConstraint {
        override val conditions: List<ParamCondition> get() = params.map { it.condition }
    }
}
