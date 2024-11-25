
package org.opentaint.ir.impl.cfg

import org.opentaint.ir.api.JIRClassType
import org.opentaint.ir.api.JIRClasspath
import org.opentaint.ir.api.cfg.JIRBranchingInst
import org.opentaint.ir.api.cfg.JIRCatchInst
import org.opentaint.ir.api.cfg.JIRGraph
import org.opentaint.ir.api.cfg.JIRInst
import org.opentaint.ir.api.cfg.JIRInstRef
import org.opentaint.ir.api.cfg.JIRInstVisitor
import org.opentaint.ir.api.cfg.JIRTerminatingInst
import org.opentaint.ir.api.isSubtypeOf

class JIRGraphImpl(
    override val classpath: JIRClasspath,
    override val instructions: List<JIRInst>,
) : Iterable<JIRInst>, JIRGraph {
    private val indexMap = instructions.mapIndexed { index, jirInst -> jirInst to index }.toMap()

    private val predecessorMap = mutableMapOf<JIRInst, MutableSet<JIRInst>>()
    private val successorMap = mutableMapOf<JIRInst, MutableSet<JIRInst>>()

    private val throwPredecessors = mutableMapOf<JIRCatchInst, MutableSet<JIRInst>>()
    private val throwSuccessors = mutableMapOf<JIRInst, MutableSet<JIRCatchInst>>()
    private val _throwExits = mutableMapOf<JIRClassType, MutableSet<JIRInstRef>>()

    override val entry: JIRInst get() = instructions.single { predecessors(it).isEmpty() && throwers(it).isEmpty() }
    override val exits: List<JIRInst> get() = instructions.filterIsInstance<JIRTerminatingInst>()

    /**
     * returns a map of possible exceptions that may be thrown from this method
     * for each instruction of in the graph in determines possible thrown exceptions using
     * #JIRExceptionResolver class
     */
    override val throwExits: Map<JIRClassType, List<JIRInst>> get() = _throwExits.mapValues { (_, refs) -> refs.map { inst(it) } }

    init {
        for (inst in instructions) {
            val successors = when (inst) {
                is JIRTerminatingInst -> mutableSetOf()
                is JIRBranchingInst -> inst.successors.map { inst(it) }.toMutableSet()
                else -> mutableSetOf(next(inst))
            }
            successorMap[inst] = successors

            for (successor in successors) {
                predecessorMap.getOrPut(successor, ::mutableSetOf) += inst
            }

            if (inst is JIRCatchInst) {
                throwPredecessors[inst] = inst.throwers.map { inst(it) }.toMutableSet()
                inst.throwers.forEach {
                    throwSuccessors.getOrPut(inst(it), ::mutableSetOf).add(inst)
                }
            }
        }

        for (inst in instructions) {
            for (throwableType in inst.accept(JIRExceptionResolver(classpath))) {
                if (!catchers(inst).any { throwableType.jirClass isSubtypeOf (it.throwable.type as JIRClassType).jirClass }) {
                    _throwExits.getOrPut(throwableType, ::mutableSetOf) += ref(inst)
                }
            }
        }
    }

    override fun index(inst: JIRInst) = indexMap.getOrDefault(inst, -1)

    override fun ref(inst: JIRInst): JIRInstRef = JIRInstRef(index(inst))
    override fun inst(ref: JIRInstRef): JIRInst = instructions[ref.index]

    override fun previous(inst: JIRInst): JIRInst = instructions[ref(inst).index - 1]
    override fun next(inst: JIRInst): JIRInst = instructions[ref(inst).index + 1]

    /**
     * `successors` and `predecessors` represent normal control flow
     */
    override fun successors(inst: JIRInst): Set<JIRInst> = successorMap.getOrDefault(inst, emptySet())
    override fun predecessors(inst: JIRInst): Set<JIRInst> = predecessorMap.getOrDefault(inst, emptySet())

    /**
     * `throwers` and `catchers` represent control flow when an exception occurs
     * `throwers` returns an empty set for every instruction except `JIRCatchInst`
     */
    override fun throwers(inst: JIRInst): Set<JIRInst> = throwPredecessors.getOrDefault(inst, emptySet())
    override fun catchers(inst: JIRInst): Set<JIRCatchInst> = throwSuccessors.getOrDefault(inst, emptySet())

    override fun previous(inst: JIRInstRef): JIRInst = previous(inst(inst))
    override fun next(inst: JIRInstRef): JIRInst = next(inst(inst))

    override fun successors(inst: JIRInstRef): Set<JIRInst> = successors(inst(inst))
    override fun predecessors(inst: JIRInstRef): Set<JIRInst> = predecessors(inst(inst))

    override fun throwers(inst: JIRInstRef): Set<JIRInst> = throwers(inst(inst))
    override fun catchers(inst: JIRInstRef): Set<JIRCatchInst> = catchers(inst(inst))

    /**
     * get all the exceptions types that this instruction may throw and terminate
     * current method
     */
    override fun exceptionExits(inst: JIRInst): Set<JIRClassType> =
        inst.accept(JIRExceptionResolver(classpath)).filter { it in _throwExits }.toSet()

    override fun exceptionExits(ref: JIRInstRef): Set<JIRClassType> = exceptionExits(inst(ref))

    override fun blockGraph(): JIRBlockGraphImpl = JIRBlockGraphImpl(this)

    override fun toString(): String = instructions.joinToString("\n")

    override fun iterator(): Iterator<JIRInst> = instructions.iterator()
}


fun JIRGraph.filter(visitor: JIRInstVisitor<Boolean>) =
    JIRGraphImpl(classpath, instructions.filter { it.accept(visitor) })

fun JIRGraph.filterNot(visitor: JIRInstVisitor<Boolean>) =
    JIRGraphImpl(classpath, instructions.filterNot { it.accept(visitor) })

fun JIRGraph.map(visitor: JIRInstVisitor<JIRInst>) =
    JIRGraphImpl(classpath, instructions.map { it.accept(visitor) })

fun JIRGraph.mapNotNull(visitor: JIRInstVisitor<JIRInst?>) =
    JIRGraphImpl(classpath, instructions.mapNotNull { it.accept(visitor) })

fun JIRGraph.flatMap(visitor: JIRInstVisitor<Collection<JIRInst>>) =
    JIRGraphImpl(classpath, instructions.flatMap { it.accept(visitor) })
