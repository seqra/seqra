
package org.opentaint.ir.impl.types

import org.opentaint.ir.api.JIRParameter
import org.opentaint.ir.api.JIRRefType
import org.opentaint.ir.api.JIRType
import org.opentaint.ir.api.JIRTypedMethod
import org.opentaint.ir.api.JIRTypedMethodParameter
import org.opentaint.ir.api.ext.isNullable
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

    override val type: JIRType
        get() {
            val typeName = parameter.type.typeName
            val type = jvmType?.let {
                classpath.typeOf(substitutor.substitute(jvmType))
            } ?: classpath.findTypeOrNull(typeName) ?: typeName.throwClassNotFound()

            return parameter.isNullable?.let {
                (type as? JIRRefType)?.copyWithNullability(it)
            } ?: type
        }

    override val nullable: Boolean?
        get() = parameter.isNullable //if (type != null && type.nullable) parameter.isNullable else false

    override val name: String?
        get() = parameter.name
}