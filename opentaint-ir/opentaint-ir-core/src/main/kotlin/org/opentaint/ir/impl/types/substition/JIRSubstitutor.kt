package org.opentaint.ir.impl.types.substition

import org.opentaint.ir.api.JIRClassOrInterface
import org.opentaint.ir.impl.types.signature.JvmType
import org.opentaint.ir.impl.types.signature.JvmTypeParameterDeclaration
import org.opentaint.ir.impl.types.typeParameters

interface JIRSubstitutor {

    companion object {
        val empty = JIRSubstitutorImpl()
    }

    /**
     * Returns a mapping that this substitutor contains for a given type parameter.
     * Does not perform bounds promotion
     *
     * @param typeParameter the parameter to return the mapping for.
     * @return the mapping for the type parameter, or `null` for a raw type.
     */
    fun substitution(typeParameter: JvmTypeParameterDeclaration): JvmType?

    /**
     * Substitutes type parameters occurring in `type` with their values.
     * If value for type parameter is `null`, appropriate erasure is returned.
     *
     * @param type the type to substitute the type parameters for.
     * @return the result of the substitution.
     */
    fun substitute(type: JvmType): JvmType

    fun fork(explicit: Map<JvmTypeParameterDeclaration, JvmType>): JIRSubstitutor

    fun newScope(declarations: List<JvmTypeParameterDeclaration>): JIRSubstitutor

    fun newScope(explicit: Map<JvmTypeParameterDeclaration, JvmType>): JIRSubstitutor

    val substitutions: Map<JvmTypeParameterDeclaration, JvmType>

}

fun JIRClassOrInterface.substitute(parameters: List<JvmType>, outer: JIRSubstitutor?): JIRSubstitutor {
    val params = typeParameters
    require(params.size == parameters.size) {
        "Incorrect parameters specified for class $name: expected ${params.size} found ${parameters.size}"
    }
    val substitution = params.mapIndexed { index, declaration ->
        declaration to parameters[index]
    }.toMap()
    return (outer ?: JIRSubstitutor.empty).newScope(substitution)
}

private suspend fun composeSubstitutors(
    outer: JIRSubstitutor,
    inner: JIRSubstitutor,
    onClass: JIRClassOrInterface
): JIRSubstitutor {
//    var answer: JIRSubstitutor = JIRSubstitutor.empty
//    val outerMap = outer.substitutions
//    val innerMap = inner.substitutions
//    for (parameter in onClass.typeParameters()) {
//        if (outerMap.containsKey(parameter) || innerMap.containsKey(parameter)) {
//            val innerType = inner.substitute(parameter)!!
//            val paramCandidate =
//                innerType as? JIRClassType //if (PsiCapturedWildcardType.isCapture()) (innerType as? JIRClassType)?.jirClass else null
//            var targetType: JIRType?
//            if (paramCandidate != null && paramCandidate !== parameter) {
//                targetType = outer.substitute(paramCandidate)
//            } else {
//                targetType = outer.substitute(innerType)
//            }
//            answer = answer.put(parameter, targetType)
//        }
//    }
//    return answer
    TODO()
}