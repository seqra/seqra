package org.opentaint.ir.go.client

import org.opentaint.ir.go.api.*
import org.opentaint.ir.go.cfg.GoIRBasicBlock
import org.opentaint.ir.go.cfg.GoIRCallInfo
import org.opentaint.ir.go.cfg.GoIRSelectState
import org.opentaint.ir.go.expr.*
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
    private var stubPackage: GoIRPackageImpl? = null

    /** Time reported by the Go server for SSA build + serialization (ms). */
    var serverBuildTimeMs: Long = 0L
        private set

    private fun getOrCreateStubPackage(): GoIRPackageImpl {
        return stubPackage ?: GoIRPackageImpl(
            importPath = "_external_stubs_",
            name = "_stubs_",
        ).also { stubPackage = it }
    }

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
                    val summary = response.summary
                    serverBuildTimeMs = summary.buildTimeMs
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
                    val sigType = resolveType(m.signatureTypeId)
                    val funcType = sigType as? GoIRFuncType
                        ?: GoIRFuncType(emptyList(), emptyList(), false, null) // placeholder for lazy refs
                    GoIRInterfaceMethodSig(m.name, funcType)
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
                val sigType = resolveType(im.signatureTypeId)
                val funcType = sigType as? GoIRFuncType
                    ?: GoIRFuncType(emptyList(), emptyList(), false, null)
                namedType.addInterfaceMethod(GoIRInterfaceMethod(
                    name = im.name,
                    signature = funcType,
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

        // Build value map for this function's body using a two-pass approach.
        // We use a LazyValueMap that provides placeholders for forward references.
        val lazyValueMap = LazyValueMap()

        for ((blockIdx, pb) in fb.blocksList.withIndex()) {
            val block = blocks[blockIdx]
            val instructions = mutableListOf<GoIRInst>()

            for (pi in pb.instructionsList) {
                val inst = deserializeInstruction(pi, block, fn, lazyValueMap)
                instructions.add(inst)
                // Register the GoIRRegister (not the instruction!) in the value map
                if (pi.valueId > 0 && inst is GoIRDefInst) {
                    lazyValueMap.register(pi.valueId, inst.register)
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
        valueMap: LazyValueMap,
    ): GoIRInst {
        val pos = positionFromProto(pi.position)
        val idx = pi.index

        fun ref(vr: ProtoValueRef): GoIRValue = valueRefFromProto(vr, fn, valueMap)
        fun type(id: Int): GoIRType = resolveType(id)

        // Helper to create a register + assign instruction for expression-based instructions
        fun assign(expr: GoIRExpr): GoIRAssignInst {
            val reg = GoIRRegister(type(pi.typeId), pi.name)
            return GoIRAssignInst(idx, block, pos, reg, expr)
        }

        return when (pi.instCase) {
            // ─── Expression-based (wrapped in GoIRAssignInst) ───
            ProtoInstruction.InstCase.ALLOC -> assign(
                GoIRAllocExpr(type(pi.alloc.allocTypeId), pi.alloc.heap, pi.alloc.comment.ifEmpty { null })
            )
            ProtoInstruction.InstCase.BIN_OP -> assign(
                GoIRBinOpExpr(binOpFromProto(pi.binOp.op), ref(pi.binOp.x), ref(pi.binOp.y))
            )
            ProtoInstruction.InstCase.UN_OP -> assign(
                GoIRUnOpExpr(unOpFromProto(pi.unOp.op), ref(pi.unOp.x), pi.unOp.commaOk)
            )
            ProtoInstruction.InstCase.CHANGE_TYPE -> assign(
                GoIRChangeTypeExpr(ref(pi.changeType.x))
            )
            ProtoInstruction.InstCase.CONVERT -> assign(
                GoIRConvertExpr(ref(pi.convert.x))
            )
            ProtoInstruction.InstCase.MULTI_CONVERT -> assign(
                GoIRMultiConvertExpr(ref(pi.multiConvert.x), type(pi.multiConvert.fromTypeId), type(pi.multiConvert.toTypeId))
            )
            ProtoInstruction.InstCase.CHANGE_INTERFACE -> assign(
                GoIRChangeInterfaceExpr(ref(pi.changeInterface.x))
            )
            ProtoInstruction.InstCase.SLICE_TO_ARRAY_POINTER -> assign(
                GoIRSliceToArrayPointerExpr(ref(pi.sliceToArrayPointer.x))
            )
            ProtoInstruction.InstCase.MAKE_INTERFACE -> assign(
                GoIRMakeInterfaceExpr(ref(pi.makeInterface.x))
            )
            ProtoInstruction.InstCase.MAKE_CLOSURE -> {
                val closureFn = functionsById[pi.makeClosure.fnId]
                    ?: error("Unknown function ID: ${pi.makeClosure.fnId} in MakeClosure within ${fn.fullName}")
                assign(GoIRMakeClosureExpr(closureFn, pi.makeClosure.bindingsList.map { ref(it) }))
            }
            ProtoInstruction.InstCase.MAKE_MAP -> assign(
                GoIRMakeMapExpr(if (pi.makeMap.hasReserve) ref(pi.makeMap.reserve) else null)
            )
            ProtoInstruction.InstCase.MAKE_CHAN -> assign(
                GoIRMakeChanExpr(ref(pi.makeChan.size))
            )
            ProtoInstruction.InstCase.MAKE_SLICE -> assign(
                GoIRMakeSliceExpr(ref(pi.makeSlice.len), ref(pi.makeSlice.cap))
            )
            ProtoInstruction.InstCase.FIELD_ADDR -> assign(
                GoIRFieldAddrExpr(ref(pi.fieldAddr.x), pi.fieldAddr.fieldIndex, pi.fieldAddr.fieldName)
            )
            ProtoInstruction.InstCase.FIELD -> assign(
                GoIRFieldExpr(ref(pi.field.x), pi.field.fieldIndex, pi.field.fieldName)
            )
            ProtoInstruction.InstCase.INDEX_ADDR -> assign(
                GoIRIndexAddrExpr(ref(pi.indexAddr.x), ref(pi.indexAddr.index))
            )
            ProtoInstruction.InstCase.INDEX_INST -> assign(
                GoIRIndexExpr(ref(pi.indexInst.x), ref(pi.indexInst.index))
            )
            ProtoInstruction.InstCase.SLICE_INST -> assign(
                GoIRSliceExpr(
                    ref(pi.sliceInst.x),
                    if (pi.sliceInst.hasLow) ref(pi.sliceInst.low) else null,
                    if (pi.sliceInst.hasHigh) ref(pi.sliceInst.high) else null,
                    if (pi.sliceInst.hasMax) ref(pi.sliceInst.max) else null,
                )
            )
            ProtoInstruction.InstCase.LOOKUP -> assign(
                GoIRLookupExpr(ref(pi.lookup.x), ref(pi.lookup.index), pi.lookup.commaOk)
            )
            ProtoInstruction.InstCase.TYPE_ASSERT -> assign(
                GoIRTypeAssertExpr(ref(pi.typeAssert.x), type(pi.typeAssert.assertedTypeId), pi.typeAssert.commaOk)
            )
            ProtoInstruction.InstCase.RANGE_INST -> assign(
                GoIRRangeExpr(ref(pi.rangeInst.x))
            )
            ProtoInstruction.InstCase.NEXT -> assign(
                GoIRNextExpr(ref(pi.next.iter), pi.next.isString)
            )
            ProtoInstruction.InstCase.SELECT_INST -> assign(
                GoIRSelectExpr(
                    pi.selectInst.statesList.map { st ->
                        GoIRSelectState(
                            chanDirFromProto(st.direction), ref(st.chan),
                            if (st.hasSend) ref(st.send) else null,
                            positionFromProto(st.position),
                        )
                    },
                    pi.selectInst.blocking,
                )
            )
            ProtoInstruction.InstCase.EXTRACT -> assign(
                GoIRExtractExpr(ref(pi.extract.tuple), pi.extract.extractIndex)
            )

            // ─── Phi (separate instruction, not an expression) ───
            ProtoInstruction.InstCase.PHI -> {
                val reg = GoIRRegister(type(pi.typeId), pi.name)
                GoIRPhi(idx, block, pos, reg, pi.phi.edgesList.map { ref(it) }, pi.phi.comment.ifEmpty { null })
            }

            // ─── Call (separate instruction, not an expression) ───
            ProtoInstruction.InstCase.CALL -> {
                val reg = GoIRRegister(type(pi.typeId), pi.name)
                GoIRCall(idx, block, pos, reg, callInfoFromProto(pi.call.call, fn, valueMap))
            }

            // ─── Terminators ───
            ProtoInstruction.InstCase.JUMP -> GoIRJump(idx, block, pos)
            ProtoInstruction.InstCase.IF_INST -> GoIRIf(idx, block, pos, ref(pi.ifInst.cond))
            ProtoInstruction.InstCase.RETURN_INST -> GoIRReturn(
                idx, block, pos, pi.returnInst.resultsList.map { ref(it) }
            )
            ProtoInstruction.InstCase.PANIC_INST -> GoIRPanic(idx, block, pos, ref(pi.panicInst.x))

            // ─── Effect-only ───
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
        valueMap: LazyValueMap,
    ): GoIRValue {
        val type = resolveType(vr.typeId)
        return when (vr.refCase) {
            ProtoValueRef.RefCase.INST_VALUE_ID ->
                valueMap[vr.instValueId]
            ProtoValueRef.RefCase.PARAM_INDEX ->
                GoIRParameterValue(type, fn.params[vr.paramIndex].name, vr.paramIndex)
            ProtoValueRef.RefCase.FREE_VAR_INDEX ->
                GoIRFreeVarValue(type, fn.freeVars[vr.freeVarIndex].name, vr.freeVarIndex)
            ProtoValueRef.RefCase.CONST_VAL ->
                GoIRConstValue(type, constToName(vr.constVal), constValueFromProto(vr.constVal))
            ProtoValueRef.RefCase.GLOBAL_ID -> {
                val global = globalsById[vr.globalId]
                if (global != null) {
                    GoIRGlobalValue(type, global.fullName, global)
                } else {
                    // External global (e.g. from stdlib) not serialized — create stub
                    val stubPkg = getOrCreateStubPackage()
                    val stubGlobal = GoIRGlobalImpl(
                        name = "ext_global_${vr.globalId}",
                        fullName = "ext_global_${vr.globalId}",
                        type = type,
                        pkg = stubPkg,
                        isExported = true,
                        position = null,
                    )
                    globalsById[vr.globalId] = stubGlobal
                    GoIRGlobalValue(type, stubGlobal.fullName, stubGlobal)
                }
            }
            ProtoValueRef.RefCase.FUNCTION_ID -> {
                val func = functionsById[vr.functionId]
                if (func != null) {
                    GoIRFunctionValue(type, func.fullName, func)
                } else {
                    // External function (e.g. from stdlib) not serialized — create stub
                    val stubName = "ext_fn_${vr.functionId}"
                    val funcType = (type as? GoIRFuncType)
                        ?: GoIRFuncType(emptyList(), emptyList(), false, null)
                    val stubFunc = GoIRFunctionImpl(
                        name = stubName,
                        fullName = stubName,
                        pkg = null,
                        signature = funcType,
                        params = emptyList(),
                        freeVars = emptyList(),
                        position = null,
                        isMethod = false,
                        isPointerReceiver = false,
                        isExported = true,
                        isSynthetic = true,
                        syntheticKind = "external-stub",
                    )
                    functionsById[vr.functionId] = stubFunc
                    GoIRFunctionValue(type, stubFunc.fullName, stubFunc)
                }
            }
            ProtoValueRef.RefCase.BUILTIN_NAME ->
                GoIRBuiltinValue(type, vr.builtinName)
            else -> throw IllegalStateException("Unknown value ref type: ${vr.refCase}")
        }
    }

    private fun callInfoFromProto(
        ci: ProtoCallInfo,
        fn: GoIRFunctionImpl,
        valueMap: LazyValueMap,
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

/**
 * A value map that supports forward references during instruction deserialization.
 * When a value ID is not yet registered, it creates a [ForwardRefValue] placeholder
 * that delegates to the actual value once it's resolved.
 *
 * In the new model, registered values are always [GoIRRegister] instances
 * (not instruction objects).
 */
internal class LazyValueMap {
    private val resolved = mutableMapOf<Int, GoIRValue>()
    private val forwardRefs = mutableMapOf<Int, ForwardRefValue>()

    fun register(id: Int, value: GoIRValue) {
        resolved[id] = value
        // Resolve any forward references
        forwardRefs[id]?.resolve(value)
    }

    operator fun get(id: Int): GoIRValue {
        resolved[id]?.let { return it }
        // Create a forward reference placeholder
        return forwardRefs.getOrPut(id) { ForwardRefValue(id) }
    }
}

/**
 * Placeholder value for forward references in SSA (e.g., phi edges referencing
 * values defined in later blocks). Delegates to the actual value once resolved.
 */
internal class ForwardRefValue(private val valueId: Int) : GoIRValue {
    private var _delegate: GoIRValue? = null
    private val delegate: GoIRValue
        get() = _delegate ?: error("Forward reference to value ID $valueId was never resolved")

    fun resolve(actual: GoIRValue) { _delegate = actual }

    override val type: GoIRType get() = delegate.type
    override val name: String get() = delegate.name
    override fun <T> acceptValue(visitor: GoIRValueVisitor<T>): T = delegate.acceptValue(visitor)
    override fun toString(): String = _delegate?.toString() ?: "ForwardRef($valueId)"
}
