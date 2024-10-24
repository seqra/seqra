package org.opentaint.ir.impl.bytecode

import org.opentaint.ir.api.JIRAnnotation
import org.opentaint.ir.api.JIRClasspath
import org.opentaint.ir.api.JIRDeclaration
import org.opentaint.ir.api.JIRParameter
import org.opentaint.ir.api.TypeName
import org.opentaint.ir.impl.suspendableLazy
import org.opentaint.ir.impl.types.ParameterInfo
import org.opentaint.ir.impl.types.TypeNameImpl

class JIRParameterImpl(private val info: ParameterInfo, private val classpath: JIRClasspath) :
    JIRParameter {

    override val access: Int
        get() = info.access

    override val name: String?
        get() = info.name

    override val index: Int
        get() = info.index

    override val declaration: JIRDeclaration
        get() = TODO("Not yet implemented")

    override val annotations: List<JIRAnnotation>
        get() = TODO("Not yet implemented")

    override val type: TypeName
        get() = TypeNameImpl(info.type)

    private val lazyAnnotations = suspendableLazy {
        info.annotations.map {
            JIRAnnotationImpl(info = it, classpath)
        }
    }


//    override suspend fun annotations() = lazyAnnotations().orEmpty()
}