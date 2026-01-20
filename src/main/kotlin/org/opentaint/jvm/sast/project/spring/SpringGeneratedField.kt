package org.opentaint.jvm.sast.project.spring

import org.objectweb.asm.Opcodes
import org.opentaint.ir.api.jvm.TypeName
import org.opentaint.ir.impl.features.classpaths.virtual.JIRVirtualFieldImpl
import java.util.Objects

class SpringGeneratedField(
    name: String,
    type: TypeName
) : JIRVirtualFieldImpl(name, access = Opcodes.ACC_PUBLIC or Opcodes.ACC_STATIC, type = type) {
    override fun hashCode(): Int = Objects.hash(name, enclosingClass)
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        return other is SpringGeneratedField && name == other.name && enclosingClass == other.enclosingClass
    }
}