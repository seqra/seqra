package org.opentaint.ir.impl.features.classpaths.virtual

import org.opentaint.ir.api.jvm.JIRAnnotation
import org.opentaint.ir.api.jvm.JIRClassOrInterface
import org.opentaint.ir.api.jvm.JIRDeclaration
import org.opentaint.ir.api.jvm.JIRField
import org.opentaint.ir.api.jvm.TypeName
import org.opentaint.ir.impl.bytecode.JIRDeclarationImpl
import org.objectweb.asm.Opcodes

interface JIRVirtualField : JIRField {
    fun bind(clazz: JIRClassOrInterface)
}

open class JIRVirtualFieldImpl(
    override val name: String,
    override val access: Int = Opcodes.ACC_PUBLIC,
    override val type: TypeName,
) : JIRVirtualField {

    override val declaration: JIRDeclaration
        get() = JIRDeclarationImpl.of(enclosingClass.declaration.location, this)

    override lateinit var enclosingClass: JIRClassOrInterface

    override fun bind(clazz: JIRClassOrInterface) {
        this.enclosingClass = clazz
    }

    override val signature: String?
        get() = null
    override val annotations: List<JIRAnnotation>
        get() = emptyList()

    override fun toString(): String {
        return "virtual $enclosingClass#$name"
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) {
            return true
        }

        if (other !is JIRVirtualFieldImpl) return false
        if (name != other.name) return false

        return enclosingClass == other.enclosingClass
    }

    override fun hashCode(): Int = enclosingClass.hashCode() * 31 + name.hashCode()
}
