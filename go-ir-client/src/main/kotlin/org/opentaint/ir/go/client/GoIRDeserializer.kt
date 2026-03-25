package org.opentaint.ir.go.client

import org.opentaint.ir.go.api.*
import org.opentaint.ir.go.cfg.GoIRBasicBlock
import org.opentaint.ir.go.cfg.GoIRCallInfo
import org.opentaint.ir.go.cfg.GoIRSelectState
import org.opentaint.ir.go.impl.*
import org.opentaint.ir.go.inst.*
import org.opentaint.ir.go.proto.*
import org.opentaint.ir.go.type.*
import org.opentaint.ir.go.value.*

/**
 * Deserializes a stream of BuildProgramResponse messages into a GoIRProgram.
 */
class GoIRDeserializer {

    // ID resolution maps populated during deserialization
    private val typesById = mutableMapOf<Int, GoIRType>()
    private val lazyNamedTypeRefs = mutableMapOf<Int, GoIRLazyNamedTypeRef>() // type ID -> lazy ref
    private val packagesById = mutableMapOf<Int, GoIRPackageImpl>()
    private val functionsById = mutableMapOf<Int, GoIRFunctionImpl>()
    private val namedTypesById = mutableMapOf<Int, GoIRNamedTypeImpl>()
    private val globalsById = mutableMapOf<Int, GoIRGlobalImpl>()
    private val constsById = mutableMapOf<Int, GoIRConstImpl>()

    private val errors = mutableListOf<String>()

    fun deserialize(responses: Iterator<BuildProgramResponse>): GoIRProgram {
        for (response in responses) {
            when (response.payloadCase) {
                BuildProgramResponse.PayloadCase.TYPE_DEF ->
                    deserializeType(response.typeDef)
                BuildProgramResponse.PayloadCase.PACKAGE_DEF ->
                    deserializePackage(response.packageDef)
                BuildProgramResponse.PayloadCase.FUNCTION_BODY ->
                    deserializeFunctionBody(response.functionBody)
                BuildProgramResponse.PayloadCase.SUMMARY -> {
                    // Summary is the last message — nothing to do
                }
                BuildProgramResponse.PayloadCase.ERROR -> {
                    val err = response.error
                    if (err.fatal) {
                        throw RuntimeException("Fatal error from Go server: ${err.message}")
                    }
                    errors.add(err.message)
                }
                else -> {} // ignore unknown
            }
        }

        // Resolve cross-references
        resolveReferences()

        // Build program
        val packages = packagesById.values.associateBy { it.importPath }
        return GoIRProgramImpl(packages)
    }

    // ─── Types ──────────────────────────────────────────────────────

    private fun deserializeType(td: ProtoTypeDefinition) {
        val type = when (td.typeCase) {
            ProtoTypeDefinition.TypeCase.BASIC ->
                GoIRBasicType(basicKindFromProto(td.basic.kind))
            ProtoTypeDefinition.TypeCase.POINTER ->
                GoIRPointerType(resolveType(td.pointer.elemTypeId))
            ProtoTypeDefinition.TypeCase.ARRAY ->
                GoIRArrayType(resolveType(td.array.elemTypeId), td.array.length)
            ProtoTypeDefinition.TypeCase.SLICE ->
                GoIRSliceType(resolveType(td.slice.elemTypeId))
            ProtoTypeDefinition.TypeCase.MAP_TYPE ->
                GoIRMapType(resolveType(td.mapType.keyTypeId), resolveType(td.mapType.valueTypeId))
            ProtoTypeDefinition.TypeCase.CHAN_TYPE ->
                GoIRChanType(resolveType(td.chanType.elemTypeId), chanDirFromProto(td.chanType.direction))
            ProtoTypeDefinition.TypeCase.STRUCT_TYPE -> {
                val fields = td.structType.fieldsList.map { f ->
                    GoIRStructField(f.name, resolveType(f.typeId), f.embedded, f.tag)
                }
                GoIRStructType(fields, null) // namedType linked later
            }
            ProtoTypeDefinition.TypeCase.INTERFACE_TYPE -> {
                val methods = td.interfaceType.methodsList.map { m ->
                    GoIRInterfaceMethodSig(m.name, resolveType(m.signatureTypeId) as GoIRFuncType)
                }
                val embeds = td.interfaceType.embedTypeIdsList.map { resolveType(it) }
                GoIRInterfaceType(methods, embeds, null) // namedType linked later
            }
            ProtoTypeDefinition.TypeCase.FUNC_TYPE -> {
                val ft = td.funcType
                GoIRFuncType(
                    params = ft.paramTypeIdsList.map { resolveType(it) },
                    results = ft.resultTypeIdsList.map { resolveType(it) },
                    isVariadic = ft.variadic,
                    recv = if (ft.recvTypeId != 0) resolveType(ft.recvTypeId) else null,
                )
            }
            ProtoTypeDefinition.TypeCase.NAMED_REF -> {
                // Store as placeholder — will be resolved after named types are loaded
                lazyNamedTypeRefs[td.id] = GoIRLazyNamedTypeRef(td.namedRef.namedTypeId, td.namedRef.typeArgIdsList.toList())
                // Use a sentinel that will be replaced during resolution
                GoIRBasicType(GoIRBasicTypeKind.INT)
            }
            ProtoTypeDefinition.TypeCase.TYPE_PARAM ->
                GoIRTypeParamType(td.typeParam.name, td.typeParam.index, resolveType(td.typeParam.constraintTypeId))
            ProtoTypeDefinition.TypeCase.TUPLE ->
                GoIRTupleType(td.tuple.elementTypeIdsList.map { resolveType(it) })
            ProtoTypeDefinition.TypeCase.UNSAFE_POINTER ->
                GoIRUnsafePointerType
            else -> GoIRBasicType(GoIRBasicTypeKind.INT) // fallback
        }
        typesById[td.id] = type
    }

    // ─── Packages ───────────────────────────────────────────────────

    private fun deserializePackage(pp: ProtoPackage) {
        val pkg = GoIRPackageImpl(
            importPath = pp.importPath,
            name = pp.name,
        )
        packagesById[pp.id] = pkg

        // Named types
        for (nt in pp.namedTypesList) {
            val namedType = GoIRNamedTypeImpl(
                name = nt.name,
                fullName = nt.fullName,
                pkg = pkg,
                underlying = resolveType(nt.underlyingTypeId),
                kind = namedTypeKindFromProto(nt.kind),
                position = positionFromProto(nt.position),
            )
            namedTypesById[nt.id] = namedType

            // Fields
            for (fd in nt.fieldsList) {
                namedType.addField(org.opentaint.ir.go.api.GoIRField(
                    name = fd.name,
                    type = resolveType(fd.typeId),
                    index = fd.index,
                    isEmbedded = fd.embedded,
                    isExported = fd.exported,
                    tag = fd.tag,
                    enclosingType = namedType,
                ))
            }

            // Interface methods
            for (im in nt.interfaceMethodsList) {
                namedType.addInterfaceMethod(GoIRInterfaceMethod(
                    name = im.name,
                    signature = resolveType(im.signatureTypeId) as GoIRFuncType,
                    enclosingInterface = namedType,
                ))
            }

            // Store method IDs and embedded interface IDs for later resolution
            namedType.methodIds = nt.methodIdsList.toList()
            namedType.pointerMethodIds = nt.pointerMethodIdsList.toList()
            namedType.embeddedInterfaceIds = nt.embeddedInterfaceIdsList.toList()

            pkg.addNamedType(namedType)
        }

        // Functions
        for (pf in pp.functionsList) {
            val fn = deserializeFunction(pf, pkg)
            functionsById[pf.id] = fn
            if (!pf.isMethod) {
                pkg.addFunction(fn)
            }
        }

        // Globals
        for (pg in pp.globalsList) {
            val global = GoIRGlobalImpl(
                name = pg.name,
                fullName = pg.fullName,
                type = resolveType(pg.typeId),
                pkg = pkg,
                isExported = pg.isExported,
                position = positionFromProto(pg.position),
            )
            globalsById[pg.id] = global
            pkg.addGlobal(global)
        }

        // Constants
        for (pc in pp.constantsList) {
            val const = GoIRConstImpl(
                name = pc.name,
                fullName = pc.fullName,
                type = resolveType(pc.typeId),
                value = constValueFromProto(pc.value),
                pkg = pkg,
                isExported = pc.isExported,
                position = positionFromProto(pc.position),
            )
            constsById[pc.id] = const
            pkg.addConst(const)
        }

        // Store import IDs for later resolution
        pkg.importIds = pp.importIdsList.toList()
        pkg.initFunctionId = pp.initFunctionId
    }

    private fun deserializeFunction(pf: ProtoFunction, pkg: GoIRPackageImpl): GoIRFunctionImpl {
        return GoIRFunctionImpl(
            name = pf.name,
            fullName = pf.fullName,
            pkg = pkg,
            signature = resolveType(pf.signatureTypeId) as GoIRFuncType,
            params = pf.paramsList.map { p ->
                GoIRParameter(p.name, resolveType(p.typeId), p.index)
            },
            freeVars = pf.freeVarsList.map { fv ->
                GoIRFreeVar(fv.name, resolveType(fv.typeId), fv.index)
            },
            position = positionFromProto(pf.position),
            isMethod = pf.isMethod,
            isPointerReceiver = pf.isPointerReceiver,
            isExported = pf.isExported,
            isSynthetic = pf.isSynthetic,
            syntheticKind = pf.syntheticKind.ifEmpty { null },
            receiverTypeId = pf.receiverTypeId,
            parentFunctionId = pf.parentFunctionId,
            anonFunctionIds = pf.anonFunctionIdsList.toList(),
        )
    }

    // ─── Function Bodies ────────────────────────────────────────────

    private fun deserializeFunctionBody(fb: ProtoFunctionBody) {
        val fn = functionsById[fb.functionId] ?: return

        // First pass: create blocks (without instructions, to break circular refs)
        val blocks = mutableListOf<GoIRBasicBlockImpl>()
        for (pb in fb.blocksList) {
            blocks.add(GoIRBasicBlockImpl(
                index = pb.index,
                label = pb.label.ifEmpty { null },
            ))
        }

        // Build value map for this function's body
        val valueMap = mutableMapOf<Int, GoIRValue>()

        // Register parameters as values
        for (p in fn.params) {
            // Parameters use param_index referencing, not value IDs
        }

        // Second pass: deserialize instructions
        for ((blockIdx, pb) in fb.blocksList.withIndex()) {
            val block = blocks[blockIdx]
            val instructions = mutableListOf<GoIRInst>()

            for (pi in pb.instructionsList) {
                val inst = deserializeInstruction(pi, block, fn, valueMap)
                instructions.add(inst)
                if (pi.valueId > 0 && inst is GoIRValueInst) {
                    valueMap[pi.valueId] = inst
                }
            }

            block.setInstructions(instructions)

            // Set CFG edges (block indices)
            block.predIndices = pb.predIndicesList.toList()
            block.succIndices = pb.succIndicesList.toList()
            block.idomIndex = pb.idomIndex
            block.domineeIndices = pb.domineeIndicesList.toList()
        }

        // Third pass: resolve block edges
        for (block in blocks) {
            block.resolvePredecessors(blocks)
            block.resolveSuccessors(blocks)
            block.resolveIdom(blocks)
            block.resolveDominees(blocks)
        }

        // Create body
        val recoverBlock = if (fb.recoverBlockIndex >= 0) blocks[fb.recoverBlockIndex] else null
        val body = GoIRBodyImpl(fn, blocks, recoverBlock)
        fn.setBody(body)
    }

    private fun deserializeInstruction(
        pi: ProtoInstruction,
        block: GoIRBasicBlockImpl,
        fn: GoIRFunctionImpl,
        valueMap: Map<Int, GoIRValue>,
    ): GoIRInst {
        val pos = positionFromProto(pi.position)
        val idx = pi.index

        fun ref(vr: ProtoValueRef): GoIRValue = valueRefFromProto(vr, fn, valueMap)
        fun optRef(vr: ProtoValueRef?): GoIRValue? = vr?.let { ref(it) }
        fun type(id: Int): GoIRType = resolveType(id)

        return when (pi.instCase) {
            ProtoInstruction.InstCase.ALLOC -> GoIRAlloc(
                idx, block, pos, type(pi.alloc.allocTypeId), pi.alloc.heap,
                pi.alloc.comment.ifEmpty { null }, type(pi.typeId), pi.name
            )
            ProtoInstruction.InstCase.PHI -> GoIRPhi(
                idx, block, pos, pi.phi.edgesList.map { ref(it) },
                pi.phi.comment.ifEmpty { null }, type(pi.typeId), pi.name
            )
            ProtoInstruction.InstCase.BIN_OP -> GoIRBinOp(
                idx, block, pos, binOpFromProto(pi.binOp.op),
                ref(pi.binOp.x), ref(pi.binOp.y), type(pi.typeId), pi.name
            )
            ProtoInstruction.InstCase.UN_OP -> GoIRUnOp(
                idx, block, pos, unOpFromProto(pi.unOp.op),
                ref(pi.unOp.x), pi.unOp.commaOk, type(pi.typeId), pi.name
            )
            ProtoInstruction.InstCase.CALL -> GoIRCall(
                idx, block, pos, callInfoFromProto(pi.call.call, fn, valueMap),
                type(pi.typeId), pi.name
            )
            ProtoInstruction.InstCase.CHANGE_TYPE -> GoIRChangeType(
                idx, block, pos, ref(pi.changeType.x), type(pi.typeId), pi.name
            )
            ProtoInstruction.InstCase.CONVERT -> GoIRConvert(
                idx, block, pos, ref(pi.convert.x), type(pi.typeId), pi.name
            )
            ProtoInstruction.InstCase.MULTI_CONVERT -> GoIRMultiConvert(
                idx, block, pos, ref(pi.multiConvert.x),
                type(pi.multiConvert.fromTypeId), type(pi.multiConvert.toTypeId),
                type(pi.typeId), pi.name
            )
            ProtoInstruction.InstCase.CHANGE_INTERFACE -> GoIRChangeInterface(
                idx, block, pos, ref(pi.changeInterface.x), type(pi.typeId), pi.name
            )
            ProtoInstruction.InstCase.SLICE_TO_ARRAY_POINTER -> GoIRSliceToArrayPointer(
                idx, block, pos, ref(pi.sliceToArrayPointer.x), type(pi.typeId), pi.name
            )
            ProtoInstruction.InstCase.MAKE_INTERFACE -> GoIRMakeInterface(
                idx, block, pos, ref(pi.makeInterface.x), type(pi.typeId), pi.name
            )
            ProtoInstruction.InstCase.MAKE_CLOSURE -> {
                val closureFn = functionsById[pi.makeClosure.fnId]
                    ?: throw IllegalStateException("Unknown function ID: ${pi.makeClosure.fnId}")
                GoIRMakeClosure(
                    idx, block, pos, closureFn,
                    pi.makeClosure.bindingsList.map { ref(it) }, type(pi.typeId), pi.name
                )
            }
            ProtoInstruction.InstCase.MAKE_MAP -> GoIRMakeMap(
                idx, block, pos,
                if (pi.makeMap.hasReserve) ref(pi.makeMap.reserve) else null,
                type(pi.typeId), pi.name
            )
            ProtoInstruction.InstCase.MAKE_CHAN -> GoIRMakeChan(
                idx, block, pos, ref(pi.makeChan.size), type(pi.typeId), pi.name
            )
            ProtoInstruction.InstCase.MAKE_SLICE -> GoIRMakeSlice(
                idx, block, pos, ref(pi.makeSlice.len), ref(pi.makeSlice.cap),
                type(pi.typeId), pi.name
            )
            ProtoInstruction.InstCase.FIELD_ADDR -> GoIRFieldAddr(
                idx, block, pos, ref(pi.fieldAddr.x), pi.fieldAddr.fieldIndex,
                pi.fieldAddr.fieldName, type(pi.typeId), pi.name
            )
            ProtoInstruction.InstCase.FIELD -> org.opentaint.ir.go.inst.GoIRField(
                idx, block, pos, ref(pi.field.x), pi.field.fieldIndex,
                pi.field.fieldName, type(pi.typeId), pi.name
            )
            ProtoInstruction.InstCase.INDEX_ADDR -> GoIRIndexAddr(
                idx, block, pos, ref(pi.indexAddr.x), ref(pi.indexAddr.index),
                type(pi.typeId), pi.name
            )
            ProtoInstruction.InstCase.INDEX_INST -> GoIRIndex(
                idx, block, pos, ref(pi.indexInst.x), ref(pi.indexInst.index),
                type(pi.typeId), pi.name
            )
            ProtoInstruction.InstCase.SLICE_INST -> GoIRSlice(
                idx, block, pos, ref(pi.sliceInst.x),
                if (pi.sliceInst.hasLow) ref(pi.sliceInst.low) else null,
                if (pi.sliceInst.hasHigh) ref(pi.sliceInst.high) else null,
                if (pi.sliceInst.hasMax) ref(pi.sliceInst.max) else null,
                type(pi.typeId), pi.name
            )
            ProtoInstruction.InstCase.LOOKUP -> GoIRLookup(
                idx, block, pos, ref(pi.lookup.x), ref(pi.lookup.index),
                pi.lookup.commaOk, type(pi.typeId), pi.name
            )
            ProtoInstruction.InstCase.TYPE_ASSERT -> GoIRTypeAssert(
                idx, block, pos, ref(pi.typeAssert.x), type(pi.typeAssert.assertedTypeId),
                pi.typeAssert.commaOk, type(pi.typeId), pi.name
            )
            ProtoInstruction.InstCase.RANGE_INST -> GoIRRange(
                idx, block, pos, ref(pi.rangeInst.x), type(pi.typeId), pi.name
            )
            ProtoInstruction.InstCase.NEXT -> GoIRNext(
                idx, block, pos, ref(pi.next.iter), pi.next.isString, type(pi.typeId), pi.name
            )
            ProtoInstruction.InstCase.SELECT_INST -> GoIRSelect(
                idx, block, pos,
                pi.selectInst.statesList.map { st ->
                    GoIRSelectState(
                        chanDirFromProto(st.direction), ref(st.chan),
                        if (st.hasSend) ref(st.send) else null,
                        positionFromProto(st.position),
                    )
                },
                pi.selectInst.blocking, type(pi.typeId), pi.name
            )
            ProtoInstruction.InstCase.EXTRACT -> GoIRExtract(
                idx, block, pos, ref(pi.extract.tuple), pi.extract.extractIndex,
                type(pi.typeId), pi.name
            )
            // Effect-only
            ProtoInstruction.InstCase.JUMP -> GoIRJump(idx, block, pos)
            ProtoInstruction.InstCase.IF_INST -> GoIRIf(idx, block, pos, ref(pi.ifInst.cond))
            ProtoInstruction.InstCase.RETURN_INST -> GoIRReturn(
                idx, block, pos, pi.returnInst.resultsList.map { ref(it) }
            )
            ProtoInstruction.InstCase.PANIC_INST -> GoIRPanic(idx, block, pos, ref(pi.panicInst.x))
            ProtoInstruction.InstCase.STORE -> GoIRStore(
                idx, block, pos, ref(pi.store.addr), ref(pi.store.`val`)
            )
            ProtoInstruction.InstCase.MAP_UPDATE -> GoIRMapUpdate(
                idx, block, pos, ref(pi.mapUpdate.map), ref(pi.mapUpdate.key), ref(pi.mapUpdate.value)
            )
            ProtoInstruction.InstCase.SEND -> GoIRSend(
                idx, block, pos, ref(pi.send.chan), ref(pi.send.x)
            )
            ProtoInstruction.InstCase.GO_INST -> GoIRGo(
                idx, block, pos, callInfoFromProto(pi.goInst.call, fn, valueMap)
            )
            ProtoInstruction.InstCase.DEFER_INST -> GoIRDefer(
                idx, block, pos, callInfoFromProto(pi.deferInst.call, fn, valueMap)
            )
            ProtoInstruction.InstCase.RUN_DEFERS -> GoIRRunDefers(idx, block, pos)
            ProtoInstruction.InstCase.DEBUG_REF -> GoIRDebugRef(
                idx, block, pos, ref(pi.debugRef.x), pi.debugRef.isAddr
            )
            else -> throw IllegalStateException("Unknown instruction type: ${pi.instCase}")
        }
    }

    // ─── Value references ───────────────────────────────────────────

    private fun valueRefFromProto(
        vr: ProtoValueRef,
        fn: GoIRFunctionImpl,
        valueMap: Map<Int, GoIRValue>,
    ): GoIRValue {
        val type = resolveType(vr.typeId)
        return when (vr.refCase) {
            ProtoValueRef.RefCase.INST_VALUE_ID ->
                valueMap[vr.instValueId]
                    ?: throw IllegalStateException("Unknown value ID: ${vr.instValueId}")
            ProtoValueRef.RefCase.PARAM_INDEX ->
                GoIRParameterValue(type, fn.params[vr.paramIndex].name, vr.paramIndex)
            ProtoValueRef.RefCase.FREE_VAR_INDEX ->
                GoIRFreeVarValue(type, fn.freeVars[vr.freeVarIndex].name, vr.freeVarIndex)
            ProtoValueRef.RefCase.CONST_VAL ->
                GoIRConstValue(type, constToName(vr.constVal), constValueFromProto(vr.constVal))
            ProtoValueRef.RefCase.GLOBAL_ID -> {
                val global = globalsById[vr.globalId]
                    ?: throw IllegalStateException("Unknown global ID: ${vr.globalId}")
                GoIRGlobalValue(type, global.fullName, global)
            }
            ProtoValueRef.RefCase.FUNCTION_ID -> {
                val func = functionsById[vr.functionId]
                    ?: throw IllegalStateException("Unknown function ID: ${vr.functionId}")
                GoIRFunctionValue(type, func.fullName, func)
            }
            ProtoValueRef.RefCase.BUILTIN_NAME ->
                GoIRBuiltinValue(type, vr.builtinName)
            else -> throw IllegalStateException("Unknown value ref type: ${vr.refCase}")
        }
    }

    private fun callInfoFromProto(
        ci: ProtoCallInfo,
        fn: GoIRFunctionImpl,
        valueMap: Map<Int, GoIRValue>,
    ): GoIRCallInfo {
        return GoIRCallInfo(
            mode = when (ci.mode) {
                ProtoCallMode.CALL_DIRECT -> GoIRCallMode.DIRECT
                ProtoCallMode.CALL_DYNAMIC -> GoIRCallMode.DYNAMIC
                ProtoCallMode.CALL_INVOKE -> GoIRCallMode.INVOKE
                else -> GoIRCallMode.DIRECT
            },
            function = if (ci.hasFunction()) valueRefFromProto(ci.function, fn, valueMap) else null,
            receiver = if (ci.hasReceiver()) valueRefFromProto(ci.receiver, fn, valueMap) else null,
            methodName = ci.methodName.ifEmpty { null },
            args = ci.argsList.map { valueRefFromProto(it, fn, valueMap) },
            resultType = resolveType(ci.resultTypeId),
        )
    }

    // ─── Reference resolution ───────────────────────────────────────

    private fun resolveReferences() {
        // Resolve lazy named type refs
        for ((typeId, lazyRef) in lazyNamedTypeRefs) {
            val named = namedTypesById[lazyRef.namedTypeId]
            if (named != null) {
                val typeArgs = lazyRef.typeArgIds.map { resolveType(it) }
                typesById[typeId] = GoIRNamedTypeRef(named, typeArgs)
            }
        }

        // Resolve method references on named types
        for (nt in namedTypesById.values) {
            nt.resolveMethods(functionsById)
            nt.resolveEmbeddedInterfaces(namedTypesById)
        }

        // Resolve package imports
        for (pkg in packagesById.values) {
            pkg.resolveImports(packagesById)
            pkg.resolveInitFunction(functionsById)
        }

        // Resolve function cross-references
        for (fn in functionsById.values) {
            fn.resolveReferences(functionsById, namedTypesById)
        }
    }

    // ─── Helpers ────────────────────────────────────────────────────

    private fun resolveType(id: Int): GoIRType {
        if (id == 0) return GoIRBasicType(GoIRBasicTypeKind.INT) // fallback for unset
        return typesById[id] ?: GoIRBasicType(GoIRBasicTypeKind.INT) // fallback
    }

    companion object {
        fun positionFromProto(pos: ProtoPosition?): GoIRPosition? {
            if (pos == null || pos.line == 0) return null
            return GoIRPosition(pos.filename, pos.line, pos.column)
        }

        fun constValueFromProto(cv: ProtoConstValue?): GoIRConstantValue {
            if (cv == null) return GoIRConstantValue.NilConst
            return when (cv.valueCase) {
                ProtoConstValue.ValueCase.INT_VALUE -> GoIRConstantValue.IntConst(cv.intValue)
                ProtoConstValue.ValueCase.FLOAT_VALUE -> GoIRConstantValue.FloatConst(cv.floatValue)
                ProtoConstValue.ValueCase.STRING_VALUE -> GoIRConstantValue.StringConst(cv.stringValue)
                ProtoConstValue.ValueCase.BOOL_VALUE -> GoIRConstantValue.BoolConst(cv.boolValue)
                ProtoConstValue.ValueCase.COMPLEX_VALUE -> GoIRConstantValue.ComplexConst(cv.complexValue.real, cv.complexValue.imag)
                ProtoConstValue.ValueCase.NIL_VALUE -> GoIRConstantValue.NilConst
                else -> GoIRConstantValue.NilConst
            }
        }

        fun constToName(cv: ProtoConstValue): String = when (cv.valueCase) {
            ProtoConstValue.ValueCase.INT_VALUE -> cv.intValue.toString()
            ProtoConstValue.ValueCase.FLOAT_VALUE -> cv.floatValue.toString()
            ProtoConstValue.ValueCase.STRING_VALUE -> "\"${cv.stringValue}\""
            ProtoConstValue.ValueCase.BOOL_VALUE -> cv.boolValue.toString()
            ProtoConstValue.ValueCase.NIL_VALUE -> "nil"
            else -> "?"
        }

        fun basicKindFromProto(kind: ProtoBasicTypeKind): GoIRBasicTypeKind = when (kind) {
            ProtoBasicTypeKind.BASIC_BOOL -> GoIRBasicTypeKind.BOOL
            ProtoBasicTypeKind.BASIC_INT -> GoIRBasicTypeKind.INT
            ProtoBasicTypeKind.BASIC_INT8 -> GoIRBasicTypeKind.INT8
            ProtoBasicTypeKind.BASIC_INT16 -> GoIRBasicTypeKind.INT16
            ProtoBasicTypeKind.BASIC_INT32 -> GoIRBasicTypeKind.INT32
            ProtoBasicTypeKind.BASIC_INT64 -> GoIRBasicTypeKind.INT64
            ProtoBasicTypeKind.BASIC_UINT -> GoIRBasicTypeKind.UINT
            ProtoBasicTypeKind.BASIC_UINT8 -> GoIRBasicTypeKind.UINT8
            ProtoBasicTypeKind.BASIC_UINT16 -> GoIRBasicTypeKind.UINT16
            ProtoBasicTypeKind.BASIC_UINT32 -> GoIRBasicTypeKind.UINT32
            ProtoBasicTypeKind.BASIC_UINT64 -> GoIRBasicTypeKind.UINT64
            ProtoBasicTypeKind.BASIC_FLOAT32 -> GoIRBasicTypeKind.FLOAT32
            ProtoBasicTypeKind.BASIC_FLOAT64 -> GoIRBasicTypeKind.FLOAT64
            ProtoBasicTypeKind.BASIC_COMPLEX64 -> GoIRBasicTypeKind.COMPLEX64
            ProtoBasicTypeKind.BASIC_COMPLEX128 -> GoIRBasicTypeKind.COMPLEX128
            ProtoBasicTypeKind.BASIC_STRING -> GoIRBasicTypeKind.STRING
            ProtoBasicTypeKind.BASIC_UINTPTR -> GoIRBasicTypeKind.UINTPTR
            ProtoBasicTypeKind.BASIC_UNTYPED_BOOL -> GoIRBasicTypeKind.UNTYPED_BOOL
            ProtoBasicTypeKind.BASIC_UNTYPED_INT -> GoIRBasicTypeKind.UNTYPED_INT
            ProtoBasicTypeKind.BASIC_UNTYPED_RUNE -> GoIRBasicTypeKind.UNTYPED_RUNE
            ProtoBasicTypeKind.BASIC_UNTYPED_FLOAT -> GoIRBasicTypeKind.UNTYPED_FLOAT
            ProtoBasicTypeKind.BASIC_UNTYPED_COMPLEX -> GoIRBasicTypeKind.UNTYPED_COMPLEX
            ProtoBasicTypeKind.BASIC_UNTYPED_STRING -> GoIRBasicTypeKind.UNTYPED_STRING
            ProtoBasicTypeKind.BASIC_UNTYPED_NIL -> GoIRBasicTypeKind.UNTYPED_NIL
            else -> GoIRBasicTypeKind.INT
        }

        fun chanDirFromProto(dir: ProtoChanDirection): GoIRChanDirection = when (dir) {
            ProtoChanDirection.CHAN_SEND_RECV -> GoIRChanDirection.SEND_RECV
            ProtoChanDirection.CHAN_SEND_ONLY -> GoIRChanDirection.SEND_ONLY
            ProtoChanDirection.CHAN_RECV_ONLY -> GoIRChanDirection.RECV_ONLY
            else -> GoIRChanDirection.SEND_RECV
        }

        fun binOpFromProto(op: ProtoBinaryOp): GoIRBinaryOp = when (op) {
            ProtoBinaryOp.BIN_ADD -> GoIRBinaryOp.ADD
            ProtoBinaryOp.BIN_SUB -> GoIRBinaryOp.SUB
            ProtoBinaryOp.BIN_MUL -> GoIRBinaryOp.MUL
            ProtoBinaryOp.BIN_DIV -> GoIRBinaryOp.DIV
            ProtoBinaryOp.BIN_REM -> GoIRBinaryOp.REM
            ProtoBinaryOp.BIN_AND -> GoIRBinaryOp.AND
            ProtoBinaryOp.BIN_OR -> GoIRBinaryOp.OR
            ProtoBinaryOp.BIN_XOR -> GoIRBinaryOp.XOR
            ProtoBinaryOp.BIN_SHL -> GoIRBinaryOp.SHL
            ProtoBinaryOp.BIN_SHR -> GoIRBinaryOp.SHR
            ProtoBinaryOp.BIN_AND_NOT -> GoIRBinaryOp.AND_NOT
            ProtoBinaryOp.BIN_EQ -> GoIRBinaryOp.EQ
            ProtoBinaryOp.BIN_NEQ -> GoIRBinaryOp.NEQ
            ProtoBinaryOp.BIN_LT -> GoIRBinaryOp.LT
            ProtoBinaryOp.BIN_LEQ -> GoIRBinaryOp.LEQ
            ProtoBinaryOp.BIN_GT -> GoIRBinaryOp.GT
            ProtoBinaryOp.BIN_GEQ -> GoIRBinaryOp.GEQ
            else -> GoIRBinaryOp.ADD
        }

        fun unOpFromProto(op: ProtoUnaryOp): GoIRUnaryOp = when (op) {
            ProtoUnaryOp.UN_NOT -> GoIRUnaryOp.NOT
            ProtoUnaryOp.UN_NEG -> GoIRUnaryOp.NEG
            ProtoUnaryOp.UN_XOR -> GoIRUnaryOp.XOR
            ProtoUnaryOp.UN_DEREF -> GoIRUnaryOp.DEREF
            ProtoUnaryOp.UN_ARROW -> GoIRUnaryOp.ARROW
            else -> GoIRUnaryOp.NOT
        }

        fun namedTypeKindFromProto(kind: ProtoNamedTypeKind): GoIRNamedTypeKind = when (kind) {
            ProtoNamedTypeKind.NAMED_TYPE_STRUCT -> GoIRNamedTypeKind.STRUCT
            ProtoNamedTypeKind.NAMED_TYPE_INTERFACE -> GoIRNamedTypeKind.INTERFACE
            ProtoNamedTypeKind.NAMED_TYPE_ALIAS -> GoIRNamedTypeKind.ALIAS
            ProtoNamedTypeKind.NAMED_TYPE_OTHER -> GoIRNamedTypeKind.OTHER
            else -> GoIRNamedTypeKind.OTHER
        }
    }
}

/** Placeholder type for named type references that are resolved later.
 * Uses GoIRBasicType as a wrapper since we can't extend sealed GoIRType from another module.
 * The actual type is stored in the namedTypeId/typeArgIds fields and resolved later.
 */
internal class GoIRLazyNamedTypeRef(
    val namedTypeId: Int,
    val typeArgIds: List<Int>,
) {
    val displayName: String get() = "lazy#$namedTypeId"
}
