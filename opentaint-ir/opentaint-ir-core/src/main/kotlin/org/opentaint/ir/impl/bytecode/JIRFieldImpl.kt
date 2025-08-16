package org.opentaint.ir.impl.bytecode

import org.opentaint.ir.api.jvm.JIRAnnotation
import org.opentaint.ir.api.jvm.JIRClassOrInterface
import org.opentaint.ir.api.jvm.JIRDeclaration
import org.opentaint.ir.api.jvm.JIRField
import org.opentaint.ir.api.jvm.TypeName
import org.opentaint.ir.impl.types.AnnotationInfo
import org.opentaint.ir.impl.types.FieldInfo
import org.opentaint.ir.impl.types.TypeNameImpl
import org.objectweb.asm.TypeReference
import kotlin.LazyThreadSafetyMode.PUBLICATION

class JIRFieldImpl(
    override val enclosingClass: JIRClassOrInterface,
    private val info: FieldInfo,
) : JIRField {

    override val name: String
        get() = info.name

    override val declaration: JIRDeclaration = JIRDeclarationImpl.of(enclosingClass.declaration.location, this)

    override val access: Int
        get() = info.access

    override val type: TypeName = TypeNameImpl.fromTypeName(info.type)

    override val signature: String?
        get() = info.signature

    override val annotations: List<JIRAnnotation> by lazy(PUBLICATION) {
        info.annotations
            .filter { it.typeRef == null } // Type annotations are stored with fields in bytecode, but they are not a part of field in language
            .map { JIRAnnotationImpl(it, enclosingClass.classpath) }
    }

    internal val typeAnnotationInfos: List<AnnotationInfo> by lazy(PUBLICATION) {
        info.annotations.filter {
            it.typeRef != null && TypeReference(it.typeRef).sort == TypeReference.FIELD
        }
    }

    override fun equals(other: Any?): Boolean {
        if (other == null || other !is JIRFieldImpl) {
            return false
        }
        return other.name == name && other.enclosingClass == enclosingClass
    }

    override fun hashCode(): Int {
        return 31 * enclosingClass.hashCode() + name.hashCode()
    }

    override fun toString(): String {
        return "$enclosingClass#$name"
    }
}
