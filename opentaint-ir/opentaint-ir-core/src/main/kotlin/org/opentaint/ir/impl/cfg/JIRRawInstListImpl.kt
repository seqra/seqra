
package org.opentaint.ir.impl.cfg

import org.opentaint.ir.api.JIRMethod
import org.opentaint.ir.api.cfg.JIRGraph
import org.opentaint.ir.api.cfg.JIRRawInst
import org.opentaint.ir.api.cfg.JIRRawInstList
import org.opentaint.ir.api.cfg.JIRRawInstVisitor
import org.opentaint.ir.api.cfg.JIRRawLabelInst

class JIRRawInstListImpl(
    instructions: List<JIRRawInst>
) : Iterable<JIRRawInst>, JIRRawInstList {
    private val _instructions = instructions.toMutableList()
    override val instructions: List<JIRRawInst> get() = _instructions

    override val size get() = instructions.size
    override val indices get() = instructions.indices
    override val lastIndex get() = instructions.lastIndex

    override operator fun get(index: Int) = instructions[index]
    override fun getOrNull(index: Int) = instructions.getOrNull(index)
    fun getOrElse(index: Int, defaultValue: (Int) -> JIRRawInst) = instructions.getOrElse(index, defaultValue)
    override fun iterator(): Iterator<JIRRawInst> = instructions.iterator()

    override fun toString(): String = _instructions.joinToString(separator = "\n") {
        when (it) {
            is JIRRawLabelInst -> "$it"
            else -> "  $it"
        }
    }

    override fun insertBefore(inst: JIRRawInst, vararg newInstructions: JIRRawInst) = insertBefore(inst, newInstructions.toList())
    override fun insertBefore(inst: JIRRawInst, newInstructions: Collection<JIRRawInst>) {
        val index = _instructions.indexOf(inst)
        assert(index >= 0)
        _instructions.addAll(index, newInstructions)
    }

    override fun insertAfter(inst: JIRRawInst, vararg newInstructions: JIRRawInst) = insertBefore(inst, newInstructions.toList())
    override fun insertAfter(inst: JIRRawInst, newInstructions: Collection<JIRRawInst>) {
        val index = _instructions.indexOf(inst)
        assert(index >= 0)
        _instructions.addAll(index + 1, newInstructions)
    }

    override fun remove(inst: JIRRawInst): Boolean {
        return _instructions.remove(inst)
    }

    override fun removeAll(inst: Collection<JIRRawInst>): Boolean {
        return _instructions.removeAll(inst)
    }

    override fun graph(method: JIRMethod): JIRGraph =
        JIRGraphBuilder(method.enclosingClass.classpath, this, method).build()
}


fun JIRRawInstList.filter(visitor: JIRRawInstVisitor<Boolean>) =
    JIRRawInstListImpl(instructions.filter { it.accept(visitor) })

fun JIRRawInstList.filterNot(visitor: JIRRawInstVisitor<Boolean>) =
    JIRRawInstListImpl(instructions.filterNot { it.accept(visitor) })

fun JIRRawInstList.map(visitor: JIRRawInstVisitor<JIRRawInst>) =
    JIRRawInstListImpl(instructions.map { it.accept(visitor) })

fun JIRRawInstList.mapNotNull(visitor: JIRRawInstVisitor<JIRRawInst?>) =
    JIRRawInstListImpl(instructions.mapNotNull { it.accept(visitor) })

fun JIRRawInstList.flatMap(visitor: JIRRawInstVisitor<Collection<JIRRawInst>>) =
    JIRRawInstListImpl(instructions.flatMap { it.accept(visitor) })
