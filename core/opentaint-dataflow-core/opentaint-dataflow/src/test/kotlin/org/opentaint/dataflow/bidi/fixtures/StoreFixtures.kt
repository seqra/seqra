package org.opentaint.dataflow.bidi.fixtures

import org.opentaint.dataflow.ap.ifds.*
import org.opentaint.dataflow.ap.ifds.serialization.MethodContextSerializer
import org.opentaint.dataflow.bidi.ap.*
import org.opentaint.dataflow.bidi.ap.ExclusionSet
import org.opentaint.dataflow.util.Cancellation
import org.opentaint.dataflow.util.RefManager
import org.opentaint.ir.api.common.*
import org.opentaint.ir.api.common.cfg.*

/** Common IR identity and instruction indexes for store and join tests. */
class StoreFixtures(val cancellation: Cancellation = Cancellation(), val maxIndex: Int = 127,
                    refManager: RefManager = RefManager()) {
    val manager = ApManager(cancellation, refManager).also { it.marks.finishFixtureOwnershipCensus() }
    val ops = ApOps(manager)
    data class Context(val name: String) : MethodContext
    val commonMethod = object : CommonMethod {
        override val name = "storeMethod"
        override val parameters: List<CommonMethodParameter> = emptyList()
        override val returnType = object : CommonTypeName { override val typeName = "void" }
        override fun flowGraph(): ControlFlowGraph<CommonInst> = error("unused")
    }
    private val instructions = HashMap<Int, CommonInst>()
    fun inst(index: Int): CommonInst = instructions.getOrPut(index) {
        object : CommonInst {
            override val location = object : CommonInstLocation {
                override val method = commonMethod
                override val index = index
            }
        }
    }
    fun key(context: String = "context", entry: Int = 0): MethodKey = MethodKey(Context(context), inst(entry))
    val method = key()
    val lm = object : LanguageManager {
        override fun getInstIndex(inst: CommonInst) = inst.location.index
        override fun getMaxInstIndex(method: CommonMethod) = maxIndex
        override fun getInstByIndex(method: CommonMethod, index: Int) = inst(index)
        override fun isEmpty(method: CommonMethod) = false
        override fun getCallExpr(inst: CommonInst): CommonCallExpr? = null
        override fun producesExceptionalControlFlow(inst: CommonInst) = false
        override fun getCalleeMethod(callExpr: CommonCallExpr): CommonMethod = error("unused")
        override val methodContextSerializer: MethodContextSerializer get() = error("unused")
    }
    fun field(name: String) = manager.accessors.index(FieldAccessor("Box", name, "Box"))
    fun mark(name: String) = manager.marks.mark(name)
    fun pattern(base: AccessPathBase = AccessPathBase.Argument(0), path: List<Int> = emptyList(),
                tail: Tail = Tail.EXACT, mark: MarkSlot = MarkSlot.Concrete(mark("T")),
                exclusion: ExclusionSet = ExclusionSet.Empty) = Pattern(PathFact(base, path, tail, mark), exclusion)
    fun fact(base: AccessPathBase = AccessPathBase.Argument(0), path: List<Int> = emptyList(),
             tail: Tail = Tail.EXACT, mark: MarkSlot = MarkSlot.Concrete(mark("T")),
             exclusion: ExclusionSet = ExclusionSet.Empty, layer: Layer = Layer.NORMAL): Facts =
        manager.factsOf(PathFact(base, path, tail, mark), exclusion, layer)
}
