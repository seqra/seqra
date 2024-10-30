package org.opentaint.ir.impl.bytecode

import org.opentaint.ir.api.JIRAnnotation
import org.opentaint.ir.api.JIRClassOrInterface
import org.opentaint.ir.api.JIRDeclaration
import org.opentaint.ir.api.JIRField
import org.opentaint.ir.api.TypeName
import org.opentaint.ir.impl.suspendableLazy
import org.opentaint.ir.impl.types.FieldInfo
import org.opentaint.ir.impl.types.TypeNameImpl

class JIRFieldImpl(
    override val enclosingClass: JIRClassOrInterface,
    private val info: FieldInfo
) : JIRField {

    override val name: String
        get() = info.name

    override val declaration: JIRDeclaration
        get() = JIRDeclarationImpl.of(location = enclosingClass.declaration.location, this)

    private val lazyAnnotations = suspendableLazy {
        info.annotations.map { JIRAnnotationImpl(it, enclosingClass.classpath) }
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
        get() = info.annotations.map { JIRAnnotationImpl(it, enclosingClass.classpath) }


    override fun equals(other: Any?): Boolean {
        if (other == null || other !is JIRFieldImpl) {
            return false
        }
        return other.name == name && other.enclosingClass == enclosingClass
    }

    override fun hashCode(): Int {
        return 31 * enclosingClass.hashCode() + name.hashCode()
    }
}