package org.opentaint.ir.impl.bytecode

import org.opentaint.ir.api.JIRAnnotation
import org.opentaint.ir.api.JIRDeclaration
import org.opentaint.ir.api.JIRMethod
import org.opentaint.ir.api.JIRParameter
import org.opentaint.ir.api.TypeName
import org.opentaint.ir.impl.types.ParameterInfo
import org.opentaint.ir.impl.types.TypeNameImpl

class JIRParameterImpl(
    override val method: JIRMethod,
    private val info: ParameterInfo
) : JIRParameter {

    override val access: Int
        get() = info.access

    override val name: String?
        get() = info.name

    override val index: Int
        get() = info.index

    override val declaration: JIRDeclaration
        get() = JIRDeclarationImpl.of(method.jirClass.declaration.location, this)

    override val annotations: List<JIRAnnotation>
        get() = info.annotations.map { JIRAnnotationImpl(it, method.jirClass.classpath) }

    override val type: TypeName
        get() = TypeNameImpl(info.type)

}