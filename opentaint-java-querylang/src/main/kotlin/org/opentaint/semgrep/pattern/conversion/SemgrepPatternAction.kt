package org.opentaint.semgrep.pattern.conversion

sealed interface SemgrepPatternAction {
    val metavars: List<MetavarAtom>
    val result: ParamCondition?
    fun setResultCondition(condition: ParamCondition): SemgrepPatternAction

    sealed interface SignatureName {
        data class Concrete(val name: String) : SignatureName {
            override fun toString(): String = name
        }

        data class MetaVar(val metaVar: String) : SignatureName {
            override fun toString(): String = metaVar
        }

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

    sealed interface SignatureModifierValue {
        data object NoValue : SignatureModifierValue
        data object AnyValue : SignatureModifierValue
        data class StringValue(val paramName: String, val value: String) : SignatureModifierValue
        data class StringPattern(val paramName: String, val pattern: String) : SignatureModifierValue
        data class MetaVar(val paramName: String, val metaVar: String) : SignatureModifierValue
    }

    data class SignatureModifier(
        val type: TypeNamePattern,
        val value: SignatureModifierValue
    )

    data class MethodSignature(
        val methodName: SignatureName,
        val methodReturnTypeMetavar: String?,
        val params: ParamConstraint.Partial,
        val modifiers: List<SignatureModifier>,
        val enclosingClassMetavar: String?,
        val enclosingClassModifiers: List<SignatureModifier>,
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
