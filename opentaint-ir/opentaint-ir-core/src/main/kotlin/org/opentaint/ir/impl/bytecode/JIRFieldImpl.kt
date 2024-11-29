package org.opentaint.opentaint-ir.impl.bytecode

import org.opentaint.opentaint-ir.api.JIRClassOrInterface
import org.opentaint.opentaint-ir.api.JIRField
import org.opentaint.opentaint-ir.impl.types.FieldInfo
import org.opentaint.opentaint-ir.impl.types.TypeNameImpl

class JIRFieldImpl(
    override val enclosingClass: JIRClassOrInterface,
    private val info: FieldInfo
) : JIRField {

    override val name: String
        get() = info.name

    override val declaration = JIRDeclarationImpl.of(location = enclosingClass.declaration.location, this)

    override val access: Int
        get() = info.access

    override val type = TypeNameImpl(info.type)

    override val signature: String?
        get() = info.signature

    override val annotations by lazy {
        info.annotations.map { JIRAnnotationImpl(it, enclosingClass.classpath) }
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
}