package org.opentaint.ir.impl.cfg

import org.opentaint.ir.api.cfg.JIRInstList
import org.opentaint.ir.api.cfg.JIRMutableInstList
import org.opentaint.ir.api.cfg.JIRRawInst
import org.opentaint.ir.api.cfg.JIRRawInstVisitor
import org.opentaint.ir.api.cfg.JIRRawLabelInst

open class JIRInstListImpl<INST>(
    instructions: List<INST>,
) : Iterable<INST>, JIRInstList<INST> {
    protected val _instructions: MutableList<INST> = instructions.toMutableList()
    override val instructions: List<INST> get() = _instructions

    override val size: Int get() = instructions.size
    override val indices: IntRange get() = instructions.indices
    override val lastIndex: Int get() = instructions.lastIndex

    override operator fun get(index: Int): INST = instructions[index]
    override fun getOrNull(index: Int): INST? = instructions.getOrNull(index)

    override fun iterator(): Iterator<INST> = instructions.iterator()

    override fun toMutableList(): JIRMutableInstList<INST> = JIRMutableInstListImpl(_instructions)

    override fun toString(): String = _instructions.joinToString(separator = "\n") {
        when (it) {
            is JIRRawLabelInst -> "$it"
            else -> "  $it"
        }
    }
}

class JIRMutableInstListImpl<INST>(instructions: List<INST>) :
    JIRInstListImpl<INST>(instructions), JIRMutableInstList<INST> {

    override fun insertBefore(inst: INST, vararg newInstructions: INST) = insertBefore(inst, newInstructions.toList())
    override fun insertBefore(inst: INST, newInstructions: Collection<INST>) {
        val index = _instructions.indexOf(inst)
        assert(index >= 0)
        _instructions.addAll(index, newInstructions)
    }

    override fun insertAfter(inst: INST, vararg newInstructions: INST) = insertAfter(inst, newInstructions.toList())
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

fun JIRInstList<JIRRawInst>.filter(visitor: JIRRawInstVisitor<Boolean>): JIRInstList<JIRRawInst> =
    JIRInstListImpl(instructions.filter { it.accept(visitor) })

fun JIRInstList<JIRRawInst>.filterNot(visitor: JIRRawInstVisitor<Boolean>): JIRInstList<JIRRawInst> =
    JIRInstListImpl(instructions.filterNot { it.accept(visitor) })

fun JIRInstList<JIRRawInst>.map(visitor: JIRRawInstVisitor<JIRRawInst>): JIRInstList<JIRRawInst> =
    JIRInstListImpl(instructions.map { it.accept(visitor) })

fun JIRInstList<JIRRawInst>.mapNotNull(visitor: JIRRawInstVisitor<JIRRawInst?>): JIRInstList<JIRRawInst> =
    JIRInstListImpl(instructions.mapNotNull { it.accept(visitor) })

fun JIRInstList<JIRRawInst>.flatMap(visitor: JIRRawInstVisitor<Collection<JIRRawInst>>): JIRInstList<JIRRawInst> =
    JIRInstListImpl(instructions.flatMap { it.accept(visitor) })
