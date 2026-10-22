package org.opentaint.ir.go.client

import java.util.IdentityHashMap
import org.opentaint.ir.go.api.GoIRBody
import org.opentaint.ir.go.api.GoIRFunction
import org.opentaint.ir.go.api.GoIRGlobal
import org.opentaint.ir.go.cfg.GoIRCallInfo
import org.opentaint.ir.go.cfg.GoIRCallTarget
import org.opentaint.ir.go.cfg.GoIRSelectState
import org.opentaint.ir.go.expr.*
import org.opentaint.ir.go.impl.GoIRBasicBlockImpl
import org.opentaint.ir.go.impl.GoIRBodyImpl
import org.opentaint.ir.go.inst.*
import org.opentaint.ir.go.type.*
import org.opentaint.ir.go.value.*

internal fun transformGoIRBody(
    source: GoIRBody,
    target: GoIRFunction,
    type: (GoIRType) -> GoIRType,
    functions: (GoIRFunction) -> GoIRFunction,
    globals: (GoIRGlobal) -> GoIRGlobal = { it },
): GoIRBody {
    val blocks = source.blocks.map { GoIRBasicBlockImpl(it.index, it.label) }
    val recover = source.recoverBlock?.let { blocks[it.index] }
    val body = GoIRBodyImpl(target, blocks, recover)
    val registers = IdentityHashMap<GoIRRegister, GoIRRegister>()
    source.instructions.filterIsInstance<GoIRDefInst>().forEach { definition ->
        registers[definition.register] = GoIRRegister(
            type(definition.register.type),
            definition.register.index,
            definition.register.name,
        )
    }
    val copier = BodyCopier(body, type, functions, globals, registers)
    source.blocks.forEach { oldBlock ->
        val block = blocks[oldBlock.index]
        block.setInstructions(oldBlock.instructions.map(copier::instruction))
        block.predIndices = oldBlock.predecessors.map { it.index }
        block.succIndices = oldBlock.successors.map { it.index }
        block.idomIndex = oldBlock.idom?.index ?: -1
        block.domineeIndices = oldBlock.dominatedBlocks.map { it.index }
    }
    blocks.forEach { block ->
        block.resolvePredecessors(blocks)
        block.resolveSuccessors(blocks)
        block.resolveIdom(blocks)
        block.resolveDominees(blocks)
    }
    return body
}

private class BodyCopier(
    private val body: GoIRBody,
    private val type: (GoIRType) -> GoIRType,
    private val functions: (GoIRFunction) -> GoIRFunction,
    private val globals: (GoIRGlobal) -> GoIRGlobal,
    private val registers: IdentityHashMap<GoIRRegister, GoIRRegister>,
) {
    private fun value(value: GoIRValue): GoIRValue = when (value) {
        is GoIRRegister -> registers[value] ?: error("No copied register ${value.index}")
        is GoIRParameterValue -> GoIRParameterValue(type(value.type), value.name, value.paramIndex)
        is GoIRConstValue -> GoIRConstValue(type(value.type), value.name, value.value)
        else -> error("Unsupported Go IR value ${value::class.simpleName}")
    }

    private fun location(source: GoInstLocation) =
        GoInstLocation(body, source.index, source.blockIndex, source.position)

    private fun call(source: GoIRCallInfo): GoIRCallInfo = GoIRCallInfo(
        mode = source.mode,
        target = when (val target = source.target) {
            is GoIRCallTarget.Function -> GoIRCallTarget.Function(
                functions(target.function),
            )
            is GoIRCallTarget.Builtin -> GoIRCallTarget.Builtin(target.name, type(target.type))
            is GoIRCallTarget.Dynamic -> GoIRCallTarget.Dynamic(value(target.value))
            null -> null
        },
        receiver = source.receiver?.let(::value),
        methodName = source.methodName,
        args = source.args.map(::value),
        resultType = type(source.resultType),
    )

    private fun expression(source: GoIRExpr): GoIRExpr = when (source) {
        is GoIRAllocExpr -> source.copy(type = type(source.type), allocType = type(source.allocType))
        is GoIRBinOpExpr -> source.copy(type = type(source.type), x = value(source.x), y = value(source.y))
        is GoIRUnOpExpr -> source.copy(type = type(source.type), x = value(source.x))
        is GoIRChangeTypeExpr -> source.copy(type = type(source.type), x = value(source.x))
        is GoIRConvertExpr -> source.copy(type = type(source.type), x = value(source.x))
        is GoIRMultiConvertExpr -> source.copy(
            type = type(source.type),
            x = value(source.x),
            fromType = type(source.fromType),
            toType = type(source.toType),
        )
        is GoIRChangeInterfaceExpr -> source.copy(type = type(source.type), x = value(source.x))
        is GoIRSliceToArrayPointerExpr -> source.copy(type = type(source.type), x = value(source.x))
        is GoIRMakeInterfaceExpr -> source.copy(type = type(source.type), x = value(source.x))
        is GoIRTypeAssertExpr -> source.copy(
            type = type(source.type),
            x = value(source.x),
            assertedType = type(source.assertedType),
        )
        is GoIRMakeClosureExpr -> source.copy(
            type = type(source.type),
            fn = functions(source.fn),
            bindings = source.bindings.map(::value),
        )
        is GoIRMakeMapExpr -> source.copy(type = type(source.type), reserve = source.reserve?.let(::value))
        is GoIRMakeChanExpr -> source.copy(type = type(source.type), size = value(source.size))
        is GoIRMakeSliceExpr -> source.copy(
            type = type(source.type),
            len = value(source.len),
            cap = value(source.cap),
        )
        is GoIRFieldAddrExpr -> source.copy(type = type(source.type), x = value(source.x))
        is GoIRFieldExpr -> source.copy(type = type(source.type), x = value(source.x))
        is GoIRIndexAddrExpr -> source.copy(
            type = type(source.type), x = value(source.x), indexValue = value(source.indexValue),
        )
        is GoIRIndexExpr -> source.copy(
            type = type(source.type), x = value(source.x), indexValue = value(source.indexValue),
        )
        is GoIRSliceExpr -> source.copy(
            type = type(source.type),
            x = value(source.x),
            low = source.low?.let(::value),
            high = source.high?.let(::value),
            max = source.max?.let(::value),
        )
        is GoIRLookupExpr -> source.copy(
            type = type(source.type), x = value(source.x), indexValue = value(source.indexValue),
        )
        is GoIRRangeExpr -> source.copy(type = type(source.type), x = value(source.x))
        is GoIRNextExpr -> source.copy(type = type(source.type), iter = value(source.iter))
        is GoIRSelectExpr -> source.copy(
            type = type(source.type),
            states = source.states.map {
                GoIRSelectState(it.direction, value(it.chan), it.send?.let(::value), it.position)
            },
        )
        is GoIRExtractExpr -> source.copy(type = type(source.type), tuple = value(source.tuple))
        is GoIRGlobalValueExpr -> source.copy(type = type(source.type), global = globals(source.global))
        is GoIRFunctionValueExpr -> source.copy(
            type = type(source.type), function = functions(source.function),
        )
        is GoIRBuiltinValueExpr -> source.copy(type = type(source.type))
        is GoIRFreeVarValueExpr -> source.copy(type = type(source.type))
    }

    fun instruction(source: GoIRInst): GoIRInst {
        val location = location(source.location)
        return when (source) {
            is GoIRAssignInst -> GoIRAssignInst(location, registers.getValue(source.register), expression(source.expr))
            is GoIRPhi -> GoIRPhi(
                location,
                registers.getValue(source.register),
                source.edges.mapValues { value(it.value) },
                source.comment,
            )
            is GoIRCall -> GoIRCall(location, registers.getValue(source.register), call(source.call))
            is GoIRJump -> source.copy(location = location)
            is GoIRIf -> source.copy(location = location, cond = value(source.cond))
            is GoIRReturn -> source.copy(location = location, results = source.results.map(::value))
            is GoIRPanic -> source.copy(location = location, x = value(source.x))
            is GoIRFieldStore -> source.copy(
                location = location,
                addr = value(source.addr),
                base = value(source.base),
                value = value(source.value),
            )
            is GoIRIndexStore -> source.copy(
                location = location,
                addr = value(source.addr),
                base = value(source.base),
                index = value(source.index),
                value = value(source.value),
            )
            is GoIRStore -> source.copy(
                location = location, addr = value(source.addr), value = value(source.value),
            )
            is GoIRGlobalStore -> source.copy(location = location, global = globals(source.global), value = value(source.value))
            is GoIRMapUpdate -> source.copy(
                location = location,
                map = value(source.map),
                key = value(source.key),
                value = value(source.value),
            )
            is GoIRSend -> source.copy(location = location, chan = value(source.chan), x = value(source.x))
            is GoIRGo -> source.copy(location = location, call = call(source.call))
            is GoIRDefer -> source.copy(location = location, call = call(source.call))
            is GoIRRunDefers -> source.copy(location = location)
            is GoIRDebugRef -> source.copy(location = location, x = value(source.x))
        }
    }
}
