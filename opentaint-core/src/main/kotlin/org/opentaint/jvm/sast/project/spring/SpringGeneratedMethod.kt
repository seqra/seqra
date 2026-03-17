package org.opentaint.jvm.sast.project.spring

import org.objectweb.asm.Opcodes
import org.opentaint.ir.api.jvm.TypeName
import org.opentaint.ir.api.jvm.cfg.JIRInst
import org.opentaint.ir.api.jvm.cfg.JIRInstList
import org.opentaint.ir.impl.features.classpaths.virtual.JIRVirtualMethodImpl
import org.opentaint.ir.impl.features.classpaths.virtual.JIRVirtualParameter
import java.util.Objects

class SpringGeneratedMethod(
    name: String,
    returnType: TypeName,
    description: String,
    parameters: List<JIRVirtualParameter>,
    private val instructions: JIRInstList<JIRInst>
) : JIRVirtualMethodImpl(
    name,
    access = Opcodes.ACC_PUBLIC or Opcodes.ACC_STATIC,
    returnType = returnType,
    parameters = parameters,
    description = description
) {
    override val instList: JIRInstList<JIRInst> get() = instructions

    override fun hashCode(): Int = Objects.hash(name, enclosingClass)

    override fun equals(other: Any?): Boolean {
        if (this === other) return true

        return other is SpringGeneratedMethod && name == other.name && enclosingClass == other.enclosingClass
    }
}