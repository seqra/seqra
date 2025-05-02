package org.opentaint.ir.impl.cfg

import org.opentaint.ir.api.core.cfg.InstList
import org.opentaint.ir.api.core.cfg.MutableInstList
import org.opentaint.ir.api.jvm.cfg.JIRRawInst
import org.opentaint.ir.api.jvm.cfg.JIRRawInstVisitor
import org.opentaint.ir.api.jvm.cfg.JIRRawLabelInst

open class InstListImpl<INST>(
    instructions: List<INST>
) : Iterable<INST>, InstList<INST> {
    protected val _instructions = instructions.toMutableList()

    override val instructions: List<INST> get() = _instructions

    override val size get() = instructions.size
    override val indices get() = instructions.indices
    override val lastIndex get() = instructions.lastIndex

    override operator fun get(index: Int) = instructions[index]
    override fun getOrNull(index: Int) = instructions.getOrNull(index)
    fun getOrElse(index: Int, defaultValue: (Int) -> INST) = instructions.getOrElse(index, defaultValue)
    override fun iterator(): Iterator<INST> = instructions.iterator()

    override fun toMutableList() = MutableInstListImpl(_instructions)

    override fun toString(): String = _instructions.joinToString(separator = "\n") {
        when (it) {
            is JIRRawLabelInst -> "$it"
            else -> "  $it"
        }
    }

}

class MutableInstListImpl<INST>(instructions: List<INST>) : InstListImpl<INST>(instructions),
    MutableInstList<INST> {

    override fun insertBefore(inst: INST, vararg newInstructions: INST) = insertBefore(inst, newInstructions.toList())
    override fun insertBefore(inst: INST, newInstructions: Collection<INST>) {
        val index = _instructions.indexOf(inst)
        assert(index >= 0)
        _instructions.addAll(index, newInstructions)
    }

    override fun insertAfter(inst: INST, vararg newInstructions: INST) = insertBefore(inst, newInstructions.toList())
    override fun insertAfter(inst: INST, newInstructions: Collection<INST>) {
        val index = _instructions.indexOf(inst)
        assert(index >= 0)
        _instructions.addAll(index + 1, newInstructions)
    }

    override fun remove(inst: INST): Boolean {
        return _instructions.remove(inst)
    }

    override fun removeAll(inst: Collection<INST>): Boolean {
        return _instructions.removeAll(inst)
    }
}

fun InstList<JIRRawInst>.filter(visitor: JIRRawInstVisitor<Boolean>) =
    InstListImpl(instructions.filter { it.accept(visitor) })

fun InstList<JIRRawInst>.filterNot(visitor: JIRRawInstVisitor<Boolean>) =
    InstListImpl(instructions.filterNot { it.accept(visitor) })

fun InstList<JIRRawInst>.map(visitor: JIRRawInstVisitor<JIRRawInst>) =
    InstListImpl(instructions.map { it.accept(visitor) })

fun InstList<JIRRawInst>.mapNotNull(visitor: JIRRawInstVisitor<JIRRawInst?>) =
    InstListImpl(instructions.mapNotNull { it.accept(visitor) })

fun InstList<JIRRawInst>.flatMap(visitor: JIRRawInstVisitor<Collection<JIRRawInst>>) =
    InstListImpl(instructions.flatMap { it.accept(visitor) })
