package org.opentaint.jvm.sast.project.spring

import org.objectweb.asm.Opcodes
import org.opentaint.dataflow.bidi.ap.Cleaner
import org.opentaint.dataflow.bidi.interp.CallCleaners
import org.opentaint.ir.api.jvm.TypeName
import org.opentaint.ir.api.jvm.cfg.JIRInst
import org.opentaint.ir.api.jvm.cfg.JIRInstList
import org.opentaint.ir.impl.features.classpaths.virtual.JIRVirtualMethodImpl
import org.opentaint.ir.impl.features.classpaths.virtual.JIRVirtualParameter
import org.opentaint.jvm.graph.JMethodBoundaryInstFeature
import java.util.Objects
import java.util.Collections

class SpringGeneratedMethod(
    name: String,
    returnType: TypeName,
    description: String,
    parameters: List<JIRVirtualParameter>,
    private val instructions: JIRInstList<JIRInst>,
    callCleaners: List<Cleaner> = emptyList(),
) : JIRVirtualMethodImpl(
    name,
    access = Opcodes.ACC_PUBLIC or Opcodes.ACC_STATIC,
    returnType = returnType,
    parameters = parameters,
    description = description
), CallCleaners {
    override val callCleaners: List<Cleaner> = Collections.unmodifiableList(ArrayList(callCleaners))

    override val instList: JIRInstList<JIRInst> by lazy {
        JMethodBoundaryInstFeature.transformInstList(this, instructions)
    }

    override fun hashCode(): Int = Objects.hash(name, enclosingClass)

    override fun equals(other: Any?): Boolean {
        if (this === other) return true

        return other is SpringGeneratedMethod && name == other.name && enclosingClass == other.enclosingClass
    }
}
