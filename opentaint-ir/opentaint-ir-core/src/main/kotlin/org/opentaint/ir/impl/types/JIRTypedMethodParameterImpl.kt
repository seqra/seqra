package org.opentaint.ir.impl.types

import org.opentaint.ir.api.JIRParameter
import org.opentaint.ir.api.JIRType
import org.opentaint.ir.api.JIRTypedMethod
import org.opentaint.ir.api.JIRTypedMethodParameter
import org.opentaint.ir.api.isNullable
import org.opentaint.ir.api.throwClassNotFound
import org.opentaint.ir.impl.signature.SType

class JIRTypedMethodParameterImpl(
    override val enclosingMethod: JIRTypedMethod,
    private val parameter: JIRParameter,
    private val stype: SType?,
    private val bindings: JIRTypeBindings
) : JIRTypedMethodParameter {

    val classpath = enclosingMethod.method.enclosingClass.classpath

    override suspend fun type(): JIRType {
        val st = stype ?: return classpath.findTypeOrNull(parameter.type.typeName) ?: parameter.type.typeName.throwClassNotFound()
        return classpath.typeOf(st.apply(bindings, null), bindings)
    }

    override val nullable: Boolean
        get() = parameter.isNullable //if (type != null && type.nullable) parameter.isNullable else false

    override val name: String?
        get() = parameter.name
}