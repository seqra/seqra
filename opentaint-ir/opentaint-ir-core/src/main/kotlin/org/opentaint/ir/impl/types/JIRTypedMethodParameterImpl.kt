package org.opentaint.ir.impl.types

import org.opentaint.ir.api.JIRParameter
import org.opentaint.ir.api.JIRType
import org.opentaint.ir.api.JIRTypedMethod
import org.opentaint.ir.api.JIRTypedMethodParameter
import org.opentaint.ir.api.isNullable
import org.opentaint.ir.api.throwClassNotFound
import org.opentaint.ir.impl.types.signature.JvmType
import org.opentaint.ir.impl.types.substition.JIRSubstitutor

class JIRTypedMethodParameterImpl(
    override val enclosingMethod: JIRTypedMethod,
    private val parameter: JIRParameter,
    private val jvmType: JvmType?,
    private val substitutor: JIRSubstitutor
) : JIRTypedMethodParameter {

    val classpath = enclosingMethod.method.enclosingClass.classpath

    override suspend fun type(): JIRType {
        val typeName = parameter.type.typeName
        return jvmType?.let {
            classpath.typeOf(substitutor.substitute(jvmType))
        } ?: classpath.findTypeOrNull(typeName) ?: typeName.throwClassNotFound()
    }

    override val nullable: Boolean
        get() = parameter.isNullable //if (type != null && type.nullable) parameter.isNullable else false

    override val name: String?
        get() = parameter.name
}