package org.opentaint.ir.go.client

import org.opentaint.ir.go.api.GoIRBody
import org.opentaint.ir.go.api.GoIRFunction
import org.opentaint.ir.go.api.GoIRParameter
import org.opentaint.ir.go.api.GoIRFreeVar
import org.opentaint.ir.go.api.GoIRProgram
import org.opentaint.ir.go.api.GoIRTypeParamDecl
import org.opentaint.ir.go.api.GoIrFunctionReference
import org.opentaint.ir.go.cfg.GoIRCallInfo
import org.opentaint.ir.go.cfg.GoIRCallTarget
import org.opentaint.ir.go.cfg.GoIRSelectState
import org.opentaint.ir.go.expr.*
import org.opentaint.ir.go.impl.GoIRBasicBlockImpl
import org.opentaint.ir.go.impl.GoIRBodyImpl
import org.opentaint.ir.go.impl.GoIRFunctionImpl
import org.opentaint.ir.go.inst.*
import org.opentaint.ir.go.type.*
import org.opentaint.ir.go.value.*
import java.util.IdentityHashMap

internal data class SpecializedGoModelFunction(
    val root: GoIRFunction,
    val anonymousFunctions: List<GoIRFunction>,
)

/** Copies a generic model body and substitutes the target instance type arguments. */
internal class GoIRModelSpecializer(
    private val program: GoIRProgram,
) {
    fun specialize(modelRoot: GoIRFunction, target: GoIRFunction): SpecializedGoModelFunction {
        require(modelRoot.bodyAvailable) { "Go model function ${modelRoot.fullName} has no body" }
        require(modelRoot.typeParams.size == target.typeArgs.size) {
            "Go generic instance ${target.fullName} has ${target.typeArgs.size} type arguments, " +
                "want ${modelRoot.typeParams.size}"
        }
        val typeArguments = modelRoot.typeParams.associate { parameter ->
            parameter.index to target.typeArgs[parameter.index]
        }
        val specializeType = TypeSpecializer(typeArguments)
        val sourceFunctions = collectAnonymousFunctions(modelRoot)
        val suffix = target.name.removePrefix(modelRoot.name).ifEmpty {
            "#opentaint-model-${target.fullName.hashCode().toUInt()}"
        }
        val functionMap = IdentityHashMap<GoIRFunction, GoIRFunctionImpl>()
        val anonymousRefs = IdentityHashMap<GoIRFunction, MutableList<GoIrFunctionReference>>()

        fun reference(function: GoIRFunction): GoIrFunctionReference =
            GoIrFunctionReference(function.pkg?.importPath.orEmpty(), function.name, function.signature)
                .also { it.program = program }

        val rootRefs = mutableListOf<GoIrFunctionReference>()
        val root = GoIRFunctionImpl(
            name = target.name,
            fullName = target.fullName,
            pkg = target.pkg,
            signature = target.signature,
            params = target.params,
            freeVars = target.freeVars,
            position = modelRoot.position,
            isMethod = target.isMethod,
            isPointerReceiver = target.isPointerReceiver,
            isExported = target.isExported,
            isSynthetic = true,
            syntheticKind = MODEL_KIND_FOR_SPECIALIZER,
            declaredHasBody = true,
            parent = target.parent,
            anonymousFunctions = rootRefs,
            typeParams = target.typeParams,
            originFullName = target.originFullName,
            typeArgs = target.typeArgs,
        )
        functionMap[modelRoot] = root
        anonymousRefs[modelRoot] = rootRefs

        sourceFunctions.forEach { source ->
            val refs = mutableListOf<GoIrFunctionReference>()
            val parent = source.parent?.function?.let(functionMap::get)
                ?: error("Go model anonymous function ${source.fullName} has no copied parent")
            val signature = specializeType(source.signature) as GoIRFuncType
            val copied = GoIRFunctionImpl(
                name = source.name + suffix,
                fullName = source.fullName + suffix,
                pkg = target.pkg,
                signature = signature,
                params = source.params.map { GoIRParameter(it.name, specializeType(it.type), it.index) },
                freeVars = source.freeVars.map { GoIRFreeVar(it.name, specializeType(it.type), it.index) },
                position = source.position,
                isMethod = source.isMethod,
                isPointerReceiver = source.isPointerReceiver,
                isExported = source.isExported,
                isSynthetic = true,
                syntheticKind = MODEL_SUPPORT_KIND_FOR_SPECIALIZER,
                declaredHasBody = true,
                parent = reference(parent),
                anonymousFunctions = refs,
                typeParams = source.typeParams.map {
                    GoIRTypeParamDecl(it.name, it.index, specializeType(it.constraint))
                },
            )
            functionMap[source] = copied
            anonymousRefs[source] = refs
        }

        (listOf(modelRoot) + sourceFunctions).forEach { source ->
            val refs = anonymousRefs.getValue(source)
            refs += source.anonymousFunctions.map { child ->
                reference(functionMap[child.function] ?: child.function)
            }
        }

        (listOf(modelRoot) + sourceFunctions).forEach { source ->
            val copied = functionMap.getValue(source)
            copied.setBody(copyBody(source.body!!, copied, specializeType, functionMap))
        }
        return SpecializedGoModelFunction(root, sourceFunctions.map(functionMap::getValue))
    }

    private fun collectAnonymousFunctions(root: GoIRFunction): List<GoIRFunction> {
        val result = mutableListOf<GoIRFunction>()
        val pending = ArrayDeque(root.anonymousFunctions.map { it.function })
        while (pending.isNotEmpty()) {
            val function = pending.removeFirst()
            if (result.any { it === function }) continue
            result += function
            pending += function.anonymousFunctions.map { it.function }
        }
        return result
    }

    private fun copyBody(
        source: GoIRBody,
        target: GoIRFunctionImpl,
        type: TypeSpecializer,
        functions: IdentityHashMap<GoIRFunction, GoIRFunctionImpl>,
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
        val copier = BodyCopier(body, type, functions, registers)
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
}

private class TypeSpecializer(
    private val arguments: Map<Int, GoIRType>,
) : (GoIRType) -> GoIRType {
    private val memo = IdentityHashMap<GoIRType, GoIRType>()

    override fun invoke(type: GoIRType): GoIRType {
        memo[type]?.let { return it }
        val result = when (type) {
            is GoIRTypeParamType -> arguments[type.paramIndex] ?: type
            is GoIRTypeParamRef -> arguments[type.paramIndex] ?: type
            is GoIRPointerType -> GoIRPointerType(invoke(type.elem))
            is GoIRArrayType -> GoIRArrayType(invoke(type.elem), type.length)
            is GoIRSliceType -> GoIRSliceType(invoke(type.elem))
            is GoIRMapType -> GoIRMapType(invoke(type.key), invoke(type.value))
            is GoIRChanType -> GoIRChanType(invoke(type.elem), type.direction)
            is GoIRStructType -> GoIRStructType(
                type.fields.map { GoIRStructField(it.name, invoke(it.type), it.isEmbedded, it.tag) },
                type.structTypeRef,
            )
            is GoIRNamedTypeRef -> GoIRNamedTypeRef(type.typeRef, type.typeArgs.map(::invoke))
            is GoIRNamedInterfaceType -> GoIRNamedInterfaceType(
                type.methods.map { GoIRInterfaceMethodSig(it.name, invoke(it.signature) as GoIRFuncType) },
                type.embeds.map(::invoke),
                type.interfaceTypeRef,
            )
            is GoIRAnonymousInterfaceType -> GoIRAnonymousInterfaceType(
                type.id,
                type.methods.map { GoIRInterfaceMethodSig(it.name, invoke(it.signature) as GoIRFuncType) },
                type.embeds.map(::invoke),
            )
            is GoIRFuncType -> GoIRFuncType(
                type.params.map(::invoke),
                type.results.map(::invoke),
                type.isVariadic,
                type.recv?.let(::invoke),
            )
            is GoIRTupleType -> GoIRTupleType(type.elements.map(::invoke))
            else -> type
        }
        memo[type] = result
        return result
    }
}

private class BodyCopier(
    private val body: GoIRBody,
    private val type: TypeSpecializer,
    private val functions: IdentityHashMap<GoIRFunction, GoIRFunctionImpl>,
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
                functions[target.function] ?: target.function,
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
            fn = functions[source.fn] ?: source.fn,
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
        is GoIRGlobalValueExpr -> source.copy(type = type(source.type))
        is GoIRFunctionValueExpr -> source.copy(
            type = type(source.type), function = functions[source.function] ?: source.function,
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
            is GoIRGlobalStore -> source.copy(location = location, value = value(source.value))
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

private const val MODEL_KIND_FOR_SPECIALIZER = "opentaint model"
private const val MODEL_SUPPORT_KIND_FOR_SPECIALIZER = "opentaint model support"
