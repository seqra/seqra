package org.opentaint.ir.impl.types

import org.opentaint.ir.api.JIRParameter
import org.opentaint.ir.api.JIRType
import org.opentaint.ir.api.JIRTypedMethod
import org.opentaint.ir.api.JIRTypedMethodParameter
import org.opentaint.ir.api.isNullable
import org.opentaint.ir.impl.signature.SType

class JIRTypedMethodParameterImpl(
    override val ownerMethod: JIRTypedMethod,
    private val parameter: JIRParameter,
    private val stype: SType?,
    private val bindings: JIRTypeBindings
) : JIRTypedMethodParameter {

    override suspend fun type(): JIRType {
        val cp = ownerMethod.method.enclosingClass.classpath
        val st = stype ?: return cp.findTypeOrNull(parameter.type.typeName) ?: throw IllegalStateException("")
        return cp.typeOf(st.apply(bindings))
    }

    override val nullable: Boolean
        get() = parameter.isNullable //if (type != null && type.nullable) parameter.isNullable else false

    override val name: String?
        get() = parameter.name
}