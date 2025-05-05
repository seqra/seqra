package org.opentaint.ir.api.jvm.cfg

import org.opentaint.ir.api.core.cfg.ControlFlowGraph
import org.opentaint.ir.api.jvm.JIRMethod
import org.opentaint.ir.api.jvm.JIRClassType
import org.opentaint.ir.api.jvm.JIRProject

interface JIRBytecodeGraph<NODE> : ControlFlowGraph<NODE> {
    fun throwers(node: NODE): Set<NODE>
    fun catchers(node: NODE): Set<NODE>
}

interface JIRGraph : JIRBytecodeGraph<JIRInst> {

    val method: JIRMethod
    val classpath: JIRProject
    override val instructions: List<JIRInst>
    val entry: JIRInst
    override val exits: List<JIRInst>
    override val entries: List<JIRInst>
        get() = if (instructions.isEmpty()) listOf() else listOf(entry)

    /**
     * returns a map of possible exceptions that may be thrown from this method
     * for each instruction of in the graph in determines possible thrown exceptions using
     * #JIRExceptionResolver class
     */
    val throwExits: Map<JIRClassType, List<JIRInst>>

    fun index(inst: JIRInst): Int
    fun ref(inst: JIRInst): JIRInstRef
    fun inst(ref: JIRInstRef): JIRInst
    fun previous(inst: JIRInst): JIRInst
    fun next(inst: JIRInst): JIRInst

    /**
     * `successors` and `predecessors` represent normal control flow
     */
    override fun successors(node: JIRInst): Set<JIRInst>
    override fun predecessors(node: JIRInst): Set<JIRInst>

    /**
     * `throwers` and `catchers` represent control flow when an exception occurs
     * `throwers` returns an empty set for every instruction except `JIRCatchInst`
     */
    override fun throwers(node: JIRInst): Set<JIRInst>
    override fun catchers(node: JIRInst): Set<JIRCatchInst>
    fun previous(inst: JIRInstRef): JIRInst
    fun next(inst: JIRInstRef): JIRInst
    fun successors(inst: JIRInstRef): Set<JIRInst>
    fun predecessors(inst: JIRInstRef): Set<JIRInst>
    fun throwers(inst: JIRInstRef): Set<JIRInst>
    fun catchers(inst: JIRInstRef): Set<JIRCatchInst>

    /**
     * get all the exceptions types that this instruction may throw and terminate
     * current method
     */
    fun exceptionExits(inst: JIRInst): Set<JIRClassType>
    fun exceptionExits(ref: JIRInstRef): Set<JIRClassType>
    fun blockGraph(): JIRBlockGraph
}