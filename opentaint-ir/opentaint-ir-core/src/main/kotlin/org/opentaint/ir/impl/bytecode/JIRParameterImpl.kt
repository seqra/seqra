package org.opentaint.ir.impl.bytecode

import org.opentaint.ir.api.JIRAnnotation
import org.opentaint.ir.api.JIRDeclaration
import org.opentaint.ir.api.JIRMethod
import org.opentaint.ir.api.JIRParameter
import org.opentaint.ir.api.TypeName
import org.opentaint.ir.api.ext.kmConstructor
import org.opentaint.ir.api.ext.kmFunction
import org.opentaint.ir.impl.types.ParameterInfo
import org.opentaint.ir.impl.types.TypeNameImpl

class JIRParameterImpl(
    override val method: JIRMethod,
    private val info: ParameterInfo
) : JIRParameter {

    override val access: Int
        get() = info.access

    override val name: String? by lazy {
        if (info.name != null)
            return@lazy info.name

        method.kmFunction?.let {
            // Shift needed to properly handle extension functions
            val shift = if (it.receiverParameterType != null) 1 else 0
            if (index - shift < 0)
                return@lazy null

            return@lazy it.valueParameters[index - shift].name
        }

        method.kmConstructor?.let {
            it.valueParameters[index].name
        }
    }

    override val index: Int
        get() = info.index

    override val declaration: JIRDeclaration
        get() = JIRDeclarationImpl.of(method.enclosingClass.declaration.location, this)

    override val annotations: List<JIRAnnotation>
        get() = info.annotations.map { JIRAnnotationImpl(it, method.enclosingClass.classpath) }

    override val type: TypeName
        get() = TypeNameImpl(info.type)

}