package org.opentaint.ir.impl.bytecode

import org.opentaint.ir.api.JIRClassOrInterface
import org.opentaint.ir.api.JIRField
import org.opentaint.ir.impl.types.FieldInfo
import org.opentaint.ir.impl.types.TypeNameImpl
import kotlin.LazyThreadSafetyMode.PUBLICATION

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

    override val annotations by lazy(PUBLICATION) {
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

    override fun toString(): String {
        return "$enclosingClass#$name"
    }
}