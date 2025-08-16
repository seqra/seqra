package org.opentaint.ir.impl.types

import org.opentaint.ir.api.jvm.*
import org.opentaint.ir.impl.bytecode.isNullable
import org.opentaint.ir.impl.bytecode.JIRAnnotationImpl
import org.opentaint.ir.impl.bytecode.JIRMethodImpl

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
            } ?: classpath.findTypeOrNull(typeName)
                ?.copyWithAnnotations(
                    (enclosingMethod.method as? JIRMethodImpl)?.parameterTypeAnnotationInfos(parameter.index)?.map { JIRAnnotationImpl(it, classpath) } ?: listOf()
                ) ?: typeName.throwClassNotFound()

            return parameter.isNullable?.let {
                (type as? JIRRefType)?.copyWithNullability(it)
            } ?: type
        }

    override val name: String?
        get() = parameter.name
}
