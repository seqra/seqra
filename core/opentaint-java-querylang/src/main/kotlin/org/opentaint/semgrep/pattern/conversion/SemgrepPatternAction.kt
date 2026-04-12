package org.opentaint.semgrep.pattern.conversion

import kotlinx.serialization.Serializable

sealed interface SemgrepPatternAction {
    val metavars: List<MetavarAtom>
    val result: ParamCondition?
    fun setResultCondition(condition: ParamCondition): SemgrepPatternAction

    @Serializable
    sealed interface SignatureName {
        @Serializable
        data class Concrete(val name: String) : SignatureName {
            override fun toString(): String = name
        }

        @Serializable
        data class MetaVar(val metaVar: String) : SignatureName {
            override fun toString(): String = metaVar
        }

        @Serializable
        data object AnyName : SignatureName {
            override fun toString(): String = "*"
        }
    }

    data class MethodCall(
        val methodName: SignatureName,
        override val result: ParamCondition?,
        val params: ParamConstraint,
        val obj: ParamCondition?,
        val enclosingClassName: TypeNamePattern?,
    ) : SemgrepPatternAction {
        override val metavars: List<MetavarAtom>
            get() {
                val metavars = mutableSetOf<MetavarAtom>()
                params.conditions.forEach { it.collectMetavarTo(metavars) }
                obj?.collectMetavarTo(metavars)
                result?.collectMetavarTo(metavars)
                return metavars.toList()
            }

        override fun setResultCondition(condition: ParamCondition): SemgrepPatternAction {
            check(result == null) {
                "Cannot change existing metavar"
            }

            return MethodCall(methodName, condition, params, obj, enclosingClassName)
        }
    }

    data class ConstructorCall(
        val className: TypeNamePattern,
        override val result: ParamCondition?,
        val params: ParamConstraint,
    ) : SemgrepPatternAction {
        override val metavars: List<MetavarAtom>
            get() {
                val metavars = mutableSetOf<MetavarAtom>()
                params.conditions.forEach { it.collectMetavarTo(metavars) }
                result?.collectMetavarTo(metavars)
                return metavars.toList()
            }

        override fun setResultCondition(condition: ParamCondition): SemgrepPatternAction {
            check(result == null) {
                "Cannot change existing metavar"
            }

            return ConstructorCall(className, condition, params)
        }
    }

    @Serializable
    sealed interface SignatureModifierValue {
        @Serializable
        data object NoValue : SignatureModifierValue
        @Serializable
        data object AnyValue : SignatureModifierValue
        @Serializable
        data class StringValue(val paramName: String, val value: String) : SignatureModifierValue
        @Serializable
        data class StringPattern(val paramName: String, val pattern: String) : SignatureModifierValue
        @Serializable
        data class MetaVar(val paramName: String, val metaVar: String) : SignatureModifierValue
    }

    @Serializable
    sealed interface ClassConstraint {
        @Serializable
        data class Signature(val modifier: SignatureModifier) : ClassConstraint
        @Serializable
        data class TypeConstraint(val superType: TypeNamePattern) : ClassConstraint
    }

    @Serializable
    data class SignatureModifier(
        val type: TypeNamePattern,
        val value: SignatureModifierValue
    )

    data class MethodSignature(
        val methodName: SignatureName,
        val params: ParamConstraint.Partial,
        val returnType: TypeNamePattern? = null,
        val modifiers: List<SignatureModifier>,
        val enclosingClassMetavar: String?,
        val enclosingClassConstraints: List<ClassConstraint>,
    ): SemgrepPatternAction {
        override val metavars: List<MetavarAtom>
            get() {
                val metavars = mutableSetOf<MetavarAtom>()
                params.conditions.forEach { it.collectMetavarTo(metavars) }
                return metavars.toList()
            }

        override val result: ParamCondition? = null

        override fun setResultCondition(condition: ParamCondition): SemgrepPatternAction {
            error("Unsupported operation?")
        }
    }

    data class MethodExit(val retVal: ParamCondition) : SemgrepPatternAction {
        override val metavars: List<MetavarAtom>
            get() {
                val metavars = mutableSetOf<MetavarAtom>()
                retVal.collectMetavarTo(metavars)
                return metavars.toList()
            }

        override val result: ParamCondition? = null

        override fun setResultCondition(condition: ParamCondition): SemgrepPatternAction {
            error("Unsupported operation?")
        }
    }
}
