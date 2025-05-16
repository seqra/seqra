package org.opentaint.ir.api.common.cfg

interface InstList<INST> : Iterable<INST> {
    val instructions: List<INST>
    val size: Int
    val indices: IntRange
    val lastIndex: Int

    operator fun get(index: Int): INST
    fun getOrNull(index: Int): INST?

    fun toMutableList(): MutableInstList<INST>
}

interface MutableInstList<INST> : InstList<INST> {
    fun insertBefore(inst: INST, vararg newInstructions: INST)
    fun insertBefore(inst: INST, newInstructions: Collection<INST>)
    fun insertAfter(inst: INST, vararg newInstructions: INST)
    fun insertAfter(inst: INST, newInstructions: Collection<INST>)
    fun remove(inst: INST): Boolean
    fun removeAll(inst: Collection<INST>): Boolean
}
