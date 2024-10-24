package org.opentaint.ir.impl.bytecode

import org.opentaint.ir.api.JIRAnnotation
import org.opentaint.ir.api.JIRClassOrInterface
import org.opentaint.ir.api.JIRDeclaration
import org.opentaint.ir.api.JIRField
import org.opentaint.ir.api.TypeName
import org.opentaint.ir.impl.ClassIdService
import org.opentaint.ir.impl.suspendableLazy
import org.opentaint.ir.impl.types.FieldInfo
import org.opentaint.ir.impl.types.TypeNameImpl

class JIRFieldImpl(
    override val jirClass: JIRClassOrInterface,
    private val info: FieldInfo,
    private val classIdService: ClassIdService
) : JIRField {

    override val name: String
        get() = info.name

    override val declaration: JIRDeclaration
        get() = JIRDeclarationImpl.of(location = jirClass.declaration.location, this)

    private val lazyAnnotations = suspendableLazy {
        info.annotations.map { JIRAnnotationImpl(it, classIdService.cp) }
    }

//    override suspend fun resolution(): FieldResolution {
//        return FieldSignature.extract(info.signature, classId.classpath)
//    }

    override val access: Int
        get() = info.access

    override val type: TypeName
        get() = TypeNameImpl(info.type)

    override val signature: String?
        get() = info.signature

    override val annotations: List<JIRAnnotation>
        get() = info.annotations.map { JIRAnnotationImpl(it, classIdService.cp) }


    override fun equals(other: Any?): Boolean {
        if (other == null || other !is JIRFieldImpl) {
            return false
        }
        return other.name == name && other.jirClass == jirClass
    }

    override fun hashCode(): Int {
        return 31 * jirClass.hashCode() + name.hashCode()
    }
}