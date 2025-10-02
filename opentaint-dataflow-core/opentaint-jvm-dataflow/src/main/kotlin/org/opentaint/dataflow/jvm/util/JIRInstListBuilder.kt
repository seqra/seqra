package org.opentaint.dataflow.jvm.util

import org.opentaint.ir.api.jvm.JIRMethod
import org.opentaint.ir.api.jvm.cfg.JIRInst
import org.opentaint.ir.api.jvm.cfg.JIRInstList
import org.opentaint.ir.api.jvm.cfg.JIRInstLocation
import org.opentaint.ir.api.jvm.cfg.JIRMutableInstList
import org.opentaint.ir.api.jvm.ext.jvmName
import org.opentaint.ir.impl.cfg.JIRInstLocationImpl
import org.opentaint.ir.impl.cfg.JIRMutableInstListImpl
import org.opentaint.ir.impl.types.TypeNameImpl

class JIRInstListBuilder : JIRInstList<JIRInst> {
    private val mutableInstructions = mutableListOf<JIRInst>()

    override val indices: IntRange get() = mutableInstructions.indices
    override val instructions: List<JIRInst> get() = mutableInstructions
    override val lastIndex: Int get() = mutableInstructions.lastIndex
    override val size: Int get() = mutableInstructions.size

    override fun get(index: Int): JIRInst = mutableInstructions[index]
    override fun getOrNull(index: Int): JIRInst? = mutableInstructions.getOrNull(index)
    override fun iterator(): Iterator<JIRInst> = mutableInstructions.iterator()
    override fun toMutableList(): JIRMutableInstList<JIRInst> = JIRMutableInstListImpl(mutableInstructions)

    fun addInst(buildInst: (Int) -> JIRInst) {
        val idx = mutableInstructions.size
        val inst = buildInst(idx)
        check(mutableInstructions.size == idx)
        mutableInstructions += inst
    }

    fun addInstWithLocation(method: JIRMethod, buildInst: (JIRInstLocation) -> JIRInst) = addInst { idx ->
        val location = JIRInstLocationImpl(method, idx, lineNumber = -1)
        buildInst(location)
    }

    override fun toString(): String = mutableInstructions.joinToString(separator = "\n") { "  $it" }
}

fun String.typeName() = TypeNameImpl(this.jvmName())
