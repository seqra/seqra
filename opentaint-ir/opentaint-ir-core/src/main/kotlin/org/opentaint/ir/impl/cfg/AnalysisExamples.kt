package org.opentaint.opentaint-ir.impl.cfg

import org.opentaint.opentaint-ir.api.JIRClassType
import org.opentaint.opentaint-ir.api.PredefinedPrimitives
import org.opentaint.opentaint-ir.api.cfg.BsmStringArg
import org.opentaint.opentaint-ir.api.cfg.DefaultJIRInstVisitor
import org.opentaint.opentaint-ir.api.cfg.JIRAssignInst
import org.opentaint.opentaint-ir.api.cfg.JIRBasicBlock
import org.opentaint.opentaint-ir.api.cfg.JIRCatchInst
import org.opentaint.opentaint-ir.api.cfg.JIRDynamicCallExpr
import org.opentaint.opentaint-ir.api.cfg.JIRGotoInst
import org.opentaint.opentaint-ir.api.cfg.JIRIfInst
import org.opentaint.opentaint-ir.api.cfg.JIRInst
import org.opentaint.opentaint-ir.api.cfg.JIRInstRef
import org.opentaint.opentaint-ir.api.cfg.JIRLocal
import org.opentaint.opentaint-ir.api.cfg.JIRStaticCallExpr
import org.opentaint.opentaint-ir.api.cfg.JIRStringConstant
import org.opentaint.opentaint-ir.api.cfg.JIRSwitchInst
import org.opentaint.opentaint-ir.api.cfg.JIRValue
import org.opentaint.opentaint-ir.api.cfg.JIRVirtualCallExpr
import org.opentaint.opentaint-ir.api.ext.autoboxIfNeeded
import org.opentaint.opentaint-ir.api.ext.findTypeOrNull
import java.util.*
import kotlin.collections.ArrayDeque
import kotlin.collections.set

class ReachingDefinitionsAnalysis(val blockGraph: JIRBlockGraphImpl) {
    val jIRGraph get() = blockGraph.jIRGraph

    private val nDefinitions = jIRGraph.instructions.size
    private val ins = mutableMapOf<JIRBasicBlock, BitSet>()
    private val outs = mutableMapOf<JIRBasicBlock, BitSet>()
    private val assignmentsMap = mutableMapOf<JIRValue, MutableSet<JIRInstRef>>()

    init {
        initAssignmentsMap()
        val entry = blockGraph.entry
        for (block in blockGraph)
            outs[block] = emptySet()

        val queue = ArrayDeque<JIRBasicBlock>().also { it += entry }
        val notVisited = blockGraph.toMutableSet()
        while (queue.isNotEmpty() || notVisited.isNotEmpty()) {
            val current = when {
                queue.isNotEmpty() -> queue.removeFirst()
                else -> notVisited.random()
            }
            notVisited -= current

            ins[current] = fullPredecessors(current).map { outs[it]!! }.fold(emptySet()) { acc, bitSet ->
                acc.or(bitSet)
                acc
            }

            val oldOut = outs[current]!!.clone() as BitSet
            val newOut = gen(current)

            if (oldOut != newOut) {
                outs[current] = newOut
                for (successor in fullSuccessors(current)) {
                    queue += successor
                }
            }
        }
    }

    private fun initAssignmentsMap() {
        for (inst in jIRGraph) {
            if (inst is JIRAssignInst) {
                assignmentsMap.getOrPut(inst.lhv, ::mutableSetOf) += jIRGraph.ref(inst)
            }
        }
    }

    private fun emptySet(): BitSet = BitSet(nDefinitions)

    private fun gen(block: JIRBasicBlock): BitSet {
        val inSet = ins[block]!!.clone() as BitSet
        for (inst in blockGraph.instructions(block)) {
            if (inst is JIRAssignInst) {
                for (kill in assignmentsMap.getOrDefault(inst.lhv, mutableSetOf())) {
                    inSet[kill] = false
                }
                inSet[jIRGraph.ref(inst)] = true
            }
        }
        return inSet
    }

    private fun fullPredecessors(block: JIRBasicBlock) = blockGraph.predecessors(block) + blockGraph.throwers(block)
    private fun fullSuccessors(block: JIRBasicBlock) = blockGraph.successors(block) + blockGraph.catchers(block)

    private operator fun BitSet.set(ref: JIRInstRef, value: Boolean) {
        this.set(ref.index, value)
    }

    fun outs(block: JIRBasicBlock): List<JIRInst> {
        val defs = outs.getOrDefault(block, emptySet())
        return (0 until nDefinitions).filter { defs[it] }.map { jIRGraph.instructions[it] }
    }
}

class StringConcatSimplifier(
    val jIRGraph: JIRGraphImpl
) : DefaultJIRInstVisitor<JIRInst> {
    override val defaultInstHandler: (JIRInst) -> JIRInst
        get() = { it }
    private val instructionReplacements = mutableMapOf<JIRInst, JIRInst>()
    private val instructions = mutableListOf<JIRInst>()
    private val catchReplacements = mutableMapOf<JIRInst, MutableList<JIRInst>>()
    private val instructionIndices = mutableMapOf<JIRInst, Int>()

    fun build(): JIRGraphImpl {
        var changed = false
        for (inst in jIRGraph) {
            if (inst is JIRAssignInst) {
                val lhv = inst.lhv
                val rhv = inst.rhv

                if (rhv is JIRDynamicCallExpr && rhv.callCiteMethodName == "makeConcatWithConstants") {
                    val stringType = jIRGraph.classpath.findTypeOrNull<String>() as JIRClassType

                    val (first, second) = when {
                        rhv.callCiteArgs.size == 2 -> rhv.callCiteArgs
                        rhv.callCiteArgs.size == 1 && rhv.bsmArgs.size == 1 && rhv.bsmArgs[0] is BsmStringArg -> listOf(
                            rhv.callCiteArgs[0],
                            JIRStringConstant((rhv.bsmArgs[0] as BsmStringArg).value, stringType)
                        )

                        else -> {
                            instructions += inst
                            continue
                        }
                    }
                    changed = true

                    val result = mutableListOf<JIRInst>()
                    val firstStr = stringify(first, result)
                    val secondStr = stringify(second, result)

                    val concatMethod = stringType.methods.first {
                        it.name == "concat" && it.parameters.size == 1 && it.parameters.first().type == stringType
                    }
                    val newConcatExpr = JIRVirtualCallExpr(concatMethod, firstStr, listOf(secondStr))
                    result += JIRAssignInst(lhv, newConcatExpr)
                    instructionReplacements[inst] = result.first()
                    catchReplacements[inst] = result
                    instructions += result
                } else {
                    instructions += inst
                }
            } else {
                instructions += inst
            }
        }

        if (!changed) return jIRGraph

        /**
         * after we changed the instruction list, we need to examine new instruction list and
         * remap all the old JIRInstRef's to new ones
         */
        instructionIndices.putAll(instructions.indices.map { instructions[it] to it })
        val mappedInstructions = instructions.map { it.accept(this) }
        return JIRGraphImpl(jIRGraph.classpath, mappedInstructions)
    }

    private fun stringify(value: JIRValue, instList: MutableList<JIRInst>): JIRValue {
        val cp = jIRGraph.classpath
        val stringType = cp.findTypeOrNull<String>()!!
        return when {
            PredefinedPrimitives.matches(value.type.typeName) -> {
                val boxedType = value.type.autoboxIfNeeded() as JIRClassType
                val method = boxedType.methods.first {
                    it.name == "toString" && it.parameters.size == 1 && it.parameters.first().type == value.type
                }
                val toStringExpr = JIRStaticCallExpr(method, listOf(value))
                val assignment = JIRLocal("${value}String", stringType)
                instList += JIRAssignInst(assignment, toStringExpr)
                assignment
            }

            value.type == stringType -> value
            else -> {
                val boxedType = value.type.autoboxIfNeeded() as JIRClassType
                val method = boxedType.methods.first {
                    it.name == "toString" && it.parameters.isEmpty()
                }
                val toStringExpr = JIRVirtualCallExpr(method, value, emptyList())
                val assignment = JIRLocal("${value}String", stringType)
                instList += JIRAssignInst(assignment, toStringExpr)
                assignment
            }
        }
    }

    private fun indexOf(instRef: JIRInstRef) = JIRInstRef(
        instructionIndices[instructionReplacements.getOrDefault(jIRGraph.inst(instRef), jIRGraph.inst(instRef))] ?: -1
    )

    private fun indicesOf(instRef: JIRInstRef) =
        catchReplacements.getOrDefault(jIRGraph.inst(instRef), listOf(jIRGraph.inst(instRef))).map {
            JIRInstRef(instructions.indexOf(it))
        }

    override fun visitJIRCatchInst(inst: JIRCatchInst): JIRInst = JIRCatchInst(
        inst.throwable,
        inst.throwers.flatMap { indicesOf(it) }
    )

    override fun visitJIRGotoInst(inst: JIRGotoInst): JIRInst = JIRGotoInst(indexOf(inst.target))

    override fun visitJIRIfInst(inst: JIRIfInst): JIRInst = JIRIfInst(
        inst.condition,
        indexOf(inst.trueBranch),
        indexOf(inst.falseBranch)
    )

    override fun visitJIRSwitchInst(inst: JIRSwitchInst): JIRInst = JIRSwitchInst(
        inst.key,
        inst.branches.mapValues { indexOf(it.value) },
        indexOf(inst.default)
    )
}
