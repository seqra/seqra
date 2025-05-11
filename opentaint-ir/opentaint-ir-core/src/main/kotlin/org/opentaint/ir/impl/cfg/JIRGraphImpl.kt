package org.opentaint.ir.impl.cfg

import kotlinx.collections.immutable.toPersistentSet
import org.opentaint.ir.api.JIRClassType
import org.opentaint.ir.api.JIRClasspath
import org.opentaint.ir.api.JIRMethod
import org.opentaint.ir.api.cfg.JIRBranchingInst
import org.opentaint.ir.api.cfg.JIRCatchInst
import org.opentaint.ir.api.cfg.JIRGraph
import org.opentaint.ir.api.cfg.JIRInst
import org.opentaint.ir.api.cfg.JIRInstRef
import org.opentaint.ir.api.cfg.JIRInstVisitor
import org.opentaint.ir.api.cfg.JIRTerminatingInst
import org.opentaint.ir.api.ext.isSubClassOf
import java.util.Collections.singleton

class JIRGraphImpl(
    override val method: JIRMethod,
    override val instructions: List<JIRInst>,
) : Iterable<JIRInst>, JIRGraph {

    override val classpath: JIRClasspath get() = method.enclosingClass.classpath

    private val predecessorMap = hashMapOf<JIRInst, Set<JIRInst>>()
    private val successorMap = hashMapOf<JIRInst, Set<JIRInst>>()

    private val throwPredecessors = hashMapOf<JIRCatchInst, Set<JIRInst>>()
    private val throwSuccessors = hashMapOf<JIRInst, Set<JIRCatchInst>>()
    private val _throwExits = hashMapOf<JIRClassType, Set<JIRInstRef>>()

    private val exceptionResolver = JIRExceptionResolver(classpath)

    override val entry: JIRInst get() = instructions.first()
    override val exits: List<JIRInst> by lazy { instructions.filterIsInstance<JIRTerminatingInst>() }

    /**
     * returns a map of possible exceptions that may be thrown from this method
     * for each instruction of in the graph in determines possible thrown exceptions using
     * #JIRExceptionResolver class
     */
    override val throwExits: Map<JIRClassType, List<JIRInst>>
        get() = _throwExits.mapValues { (_, refs) ->
            refs.map { instructions[it.index] }
        }

    init {
        for (inst in instructions) {
            val successors = when (inst) {
                is JIRTerminatingInst -> emptySet()
                is JIRBranchingInst -> inst.successors.map { instructions[it.index] }.toSet()
                else -> setOf(next(inst))
            }
            successorMap[inst] = successors

            for (successor in successors) {
                predecessorMap.add(successor, inst)
            }

            if (inst is JIRCatchInst) {
                throwPredecessors[inst] = inst.throwers.map { instructions[it.index] }.toPersistentSet()
                inst.throwers.forEach {
                    throwSuccessors.add(inst(it), inst)
                }
            }
        }

        for (inst in instructions) {
            for (throwableType in inst.accept(exceptionResolver)) {
                if (!catchers(inst).any { throwableType.jIRClass isSubClassOf (it.throwable.type as JIRClassType).jIRClass }) {
                    _throwExits.add(throwableType, ref(inst))
                }
            }
        }
    }

    override fun index(inst: JIRInst): Int {
        if (instructions.contains(inst)) {
            return inst.location.index
        }
        return -1
    }

    override fun ref(inst: JIRInst): JIRInstRef = JIRInstRef(index(inst))
    override fun inst(ref: JIRInstRef): JIRInst = instructions[ref.index]

    override fun previous(inst: JIRInst): JIRInst = instructions[ref(inst).index - 1]
    override fun next(inst: JIRInst): JIRInst = instructions[ref(inst).index + 1]

    /**
     * `successors` and `predecessors` represent normal control flow
     */
    override fun successors(node: JIRInst): Set<JIRInst> = successorMap[node] ?: emptySet()
    override fun predecessors(node: JIRInst): Set<JIRInst> = predecessorMap[node] ?: emptySet()

    /**
     * `throwers` and `catchers` represent control flow when an exception occurs
     * `throwers` returns an empty set for every instruction except `JIRCatchInst`
     */
    override fun throwers(node: JIRInst): Set<JIRInst> = throwPredecessors[node] ?: emptySet()
    override fun catchers(node: JIRInst): Set<JIRCatchInst> = throwSuccessors[node] ?: emptySet()

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
        inst.accept(exceptionResolver).filter { it in _throwExits }.toSet()

    override fun exceptionExits(ref: JIRInstRef): Set<JIRClassType> = exceptionExits(inst(ref))

    override fun blockGraph(): JIRBlockGraphImpl = JIRBlockGraphImpl(this)

    override fun toString(): String = instructions.joinToString("\n")

    override fun iterator(): Iterator<JIRInst> = instructions.iterator()

    private fun <KEY, VALUE> MutableMap<KEY, Set<VALUE>>.add(key: KEY, value: VALUE) {
        val current = this[key]
        if (current == null) {
            this[key] = singleton(value)
        } else {
            this[key] = current + value
        }
    }
}

fun JIRGraph.filter(visitor: JIRInstVisitor<Boolean>): JIRGraph =
    JIRGraphImpl(method, instructions.filter { it.accept(visitor) })

fun JIRGraph.filterNot(visitor: JIRInstVisitor<Boolean>): JIRGraph =
    JIRGraphImpl(method, instructions.filterNot { it.accept(visitor) })

fun JIRGraph.map(visitor: JIRInstVisitor<JIRInst>): JIRGraph =
    JIRGraphImpl(method, instructions.map { it.accept(visitor) })

fun JIRGraph.mapNotNull(visitor: JIRInstVisitor<JIRInst?>): JIRGraph =
    JIRGraphImpl(method, instructions.mapNotNull { it.accept(visitor) })

fun JIRGraph.flatMap(visitor: JIRInstVisitor<Collection<JIRInst>>): JIRGraph =
    JIRGraphImpl(method, instructions.flatMap { it.accept(visitor) })
