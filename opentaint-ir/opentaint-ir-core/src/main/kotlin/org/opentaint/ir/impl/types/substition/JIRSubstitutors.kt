package org.opentaint.ir.impl.types.substition

import org.opentaint.ir.api.jvm.*
import org.opentaint.ir.impl.cfg.util.OBJECT_CLASS
import org.opentaint.ir.impl.types.signature.JvmClassRefType
import org.opentaint.ir.impl.types.typeParameters

private fun List<JvmTypeParameterDeclaration>.substitute(
    parameters: List<JvmType>,
    outer: JIRSubstitutor?
): JIRSubstitutor {
    val substitution = mapIndexed { index, declaration ->
        declaration to parameters[index]
    }.toMap()
    return (outer ?: JIRSubstitutorImpl.empty).newScope(substitution)
}

object SafeSubstitution : JIRGenericsSubstitutionFeature {

    override fun substitute(
        clazz: JIRClassOrInterface,
        parameters: List<JvmType>,
        outer: JIRSubstitutor?
    ): JIRSubstitutor {
        val params = clazz.typeParameters
        require(params.size == parameters.size) {
            "Incorrect parameters specified for class ${clazz.name}: expected ${params.size} found ${parameters.size}"
        }
        return params.substitute(parameters, outer)
    }
}

object IgnoreSubstitutionProblems : JIRGenericsSubstitutionFeature {

    private val jvmObjectType = JvmClassRefType(OBJECT_CLASS, true, emptyList())

    override fun substitute(
        clazz: JIRClassOrInterface,
        parameters: List<JvmType>,
        outer: JIRSubstitutor?
    ): JIRSubstitutor {
        val params = clazz.typeParameters
        if (params.size == parameters.size) {
            return params.substitute(parameters, outer)
        }
        val substitution = params.associateWith { it.bounds?.first() ?: jvmObjectType }
        return (outer ?: JIRSubstitutorImpl.empty).newScope(substitution)
    }
}
