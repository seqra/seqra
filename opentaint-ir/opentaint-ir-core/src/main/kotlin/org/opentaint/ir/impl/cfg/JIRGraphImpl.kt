package org.opentaint.ir.impl.cfg

import org.opentaint.ir.api.jvm.JIRClassType
import org.opentaint.ir.api.jvm.JIRClasspath
import org.opentaint.ir.api.jvm.JIRMethod
import org.opentaint.ir.api.jvm.cfg.JIRBranchingInst
import org.opentaint.ir.api.jvm.cfg.JIRCatchInst
import org.opentaint.ir.api.jvm.cfg.JIRGraph
import org.opentaint.ir.api.jvm.cfg.JIRInst
import org.opentaint.ir.api.jvm.cfg.JIRInstRef
import org.opentaint.ir.api.jvm.cfg.JIRInstVisitor
import org.opentaint.ir.api.jvm.cfg.JIRTerminatingInst
import org.opentaint.ir.api.jvm.ext.isSubClassOf
import java.util.BitSet

class JIRGraphImpl(
    override val method: JIRMethod,
    override val instructions: List<JIRInst>,
) : JIRGraph {

    override val classpath: JIRClasspath get() = method.enclosingClass.classpath

    private val predecessorMap = arrayOfNulls<BitSet>(instructions.size)
    private val successorMap = arrayOfNulls<BitSet>(instructions.size)

    private val throwPredecessors = hashMapOf<JIRCatchInst, BitSet>()
    private val throwSuccessors = arrayOfNulls<BitSet>(instructions.size)
    private val _throwExits = hashMapOf<JIRClassType, BitSet>()

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
            val exits = mutableListOf<JIRInst>()
            refs.forEach { exits.add(instructions[it]) }
            exits
        }

    init {
        for (inst in instructions) {
            val successors = when (inst) {
                is JIRTerminatingInst -> BitSet()
                is JIRBranchingInst ->  inst.successors.toBitSet { it.index }
                else -> BitSet().also { it.set(index(next(inst))) }
            }

            val instIdx = index(inst)
            successorMap[instIdx] = successors

            successors.forEach {
                predecessorMap.add(it, instIdx)
            }

            if (inst is JIRCatchInst) {
                val throwers = inst.throwers.toBitSet { it.index }
                throwPredecessors[inst] = throwers
                throwers.forEach {
                    throwSuccessors.add(it, instIdx)
                }
            }
        }

        for (inst in instructions) {
            for (throwableType in inst.accept(exceptionResolver)) {
                if (!catchers(inst).any { throwableType.jIRClass isSubClassOf (it.throwable.type as JIRClassType).jIRClass }) {
                    _throwExits.add(throwableType, index(inst))
                }
            }
        }
    }

    override fun index(inst: JIRInst): Int = inst.location.index

    override fun ref(inst: JIRInst): JIRInstRef = JIRInstRef(index(inst))
    override fun inst(ref: JIRInstRef): JIRInst = instructions[ref.index]

    override fun previous(inst: JIRInst): JIRInst = instructions[ref(inst).index - 1]
    override fun next(inst: JIRInst): JIRInst = instructions[ref(inst).index + 1]

    /**
     * `successors` and `predecessors` represent normal control flow
     */
    override fun successors(node: JIRInst): Set<JIRInst> =
        successorMap[index(node)]?.toSet { instructions[it] } ?: emptySet()

    override fun predecessors(node: JIRInst): Set<JIRInst> =
        predecessorMap[index(node)]?.toSet { instructions[it] } ?: emptySet()

    /**
     * `throwers` and `catchers` represent control flow when an exception occurs
     * `throwers` returns an empty set for every instruction except `JIRCatchInst`
     */
    override fun throwers(node: JIRInst): Set<JIRInst> =
        throwPredecessors[node]?.toSet { instructions[it] } ?: emptySet()

    override fun catchers(node: JIRInst): Set<JIRCatchInst> =
        throwSuccessors[index(node)]?.toSet { instructions[it] as JIRCatchInst } ?: emptySet()

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

    private fun <KEY> MutableMap<KEY, BitSet>.add(key: KEY, value: Int) {
        val valueSet = getOrPut(key, ::BitSet)
        valueSet.set(value)
    }

    private fun Array<BitSet?>.add(key: Int, value: Int) {
        val valueSet = this[key] ?: BitSet().also { this[key] = it }
        valueSet.set(value)
    }

    private inline fun BitSet.forEach(body: (Int) -> Unit) {
        var node = nextSetBit(0)
        while (node >= 0) {
            body(node)
            node = nextSetBit(node + 1)
        }
    }

    private inline fun <T> Iterable<T>.toBitSet(map: (T) -> Int): BitSet =
        BitSet().also { s -> forEach { s.set(map(it)) } }

    private inline fun <T> BitSet.toSet(map: (Int) -> T): Set<T> {
        val result = hashSetOf<T>()
        forEach { result.add(map(it)) }
        return result
    }
}


fun JIRGraph.filter(visitor: JIRInstVisitor<Boolean>) =
    JIRGraphImpl(method, instructions.filter { it.accept(visitor) })

fun JIRGraph.filterNot(visitor: JIRInstVisitor<Boolean>) =
    JIRGraphImpl(method, instructions.filterNot { it.accept(visitor) })

fun JIRGraph.map(visitor: JIRInstVisitor<JIRInst>) =
    JIRGraphImpl(method, instructions.map { it.accept(visitor) })

fun JIRGraph.mapNotNull(visitor: JIRInstVisitor<JIRInst?>) =
    JIRGraphImpl(method, instructions.mapNotNull { it.accept(visitor) })

fun JIRGraph.flatMap(visitor: JIRInstVisitor<Collection<JIRInst>>) =
    JIRGraphImpl(method, instructions.flatMap { it.accept(visitor) })
