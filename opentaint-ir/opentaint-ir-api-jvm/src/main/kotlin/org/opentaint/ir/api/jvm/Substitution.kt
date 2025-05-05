package org.opentaint.ir.api.jvm

interface JvmType {
    val displayName: String
    val isNullable: Boolean?
    val annotations: List<JIRAnnotation>
}

interface JvmTypeParameterDeclaration {
    val symbol: String
    val owner: JIRAccessible
    val bounds: List<JvmType>?
}

interface JIRSubstitutor {

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