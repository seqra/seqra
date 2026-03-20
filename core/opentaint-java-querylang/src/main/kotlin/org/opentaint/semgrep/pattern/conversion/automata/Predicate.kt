package org.opentaint.semgrep.pattern.conversion.automata

import kotlinx.serialization.Serializable
import org.opentaint.semgrep.pattern.conversion.ParamCondition
import org.opentaint.semgrep.pattern.conversion.SemgrepPatternAction.ClassConstraint
import org.opentaint.semgrep.pattern.conversion.SemgrepPatternAction.SignatureModifier
import org.opentaint.semgrep.pattern.conversion.SemgrepPatternAction.SignatureName
import org.opentaint.semgrep.pattern.conversion.TypeNamePattern

typealias PredicateId = Int

@Serializable
data class Predicate(
    val signature: MethodSignature,
    val constraint: MethodConstraint?,
)

@Serializable
data class MethodSignature(
    val methodName: MethodName,
    val enclosingClassName: MethodEnclosingClassName,
)

@Serializable
sealed interface MethodConstraint

@Serializable
data class ParamConstraint(
    val position: Position,
    val condition: ParamCondition.Atom,
) : MethodConstraint

@Serializable
data class NumberOfArgsConstraint(val num: Int) : MethodConstraint

@Serializable
data class ClassModifierConstraint(val constraint: ClassConstraint) : MethodConstraint

@Serializable
data class MethodModifierConstraint(val modifier: SignatureModifier) : MethodConstraint

@Serializable
data class MethodName(val name: SignatureName)

@Serializable
data class MethodEnclosingClassName(val name: TypeNamePattern) {
    companion object {
        val anyClassName = MethodEnclosingClassName(name = TypeNamePattern.AnyType)
    }
}

@Serializable
sealed interface Position {
    @Serializable
    sealed interface ArgumentIndex {
        @Serializable
        data class Concrete(val idx: Int) : ArgumentIndex
        @Serializable
        data class Any(val paramClassifier: String) : ArgumentIndex
    }

    @Serializable
    data class Argument(val index: ArgumentIndex) : Position
    @Serializable
    data object Object : Position
    @Serializable
    data object Result : Position
}
