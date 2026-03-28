package org.opentaint.ir.go.client

import com.google.protobuf.type
import org.opentaint.ir.go.api.GoIRFreeVar
import org.opentaint.ir.go.api.GoIRInterfaceMethod
import org.opentaint.ir.go.api.GoIRParameter
import org.opentaint.ir.go.api.GoIRPosition
import org.opentaint.ir.go.api.GoIRProgram
import org.opentaint.ir.go.cfg.GoIRCallInfo
import org.opentaint.ir.go.cfg.GoIRSelectState
import org.opentaint.ir.go.expr.GoIRAllocExpr
import org.opentaint.ir.go.expr.GoIRBinOpExpr
import org.opentaint.ir.go.expr.GoIRChangeInterfaceExpr
import org.opentaint.ir.go.expr.GoIRChangeTypeExpr
import org.opentaint.ir.go.expr.GoIRConvertExpr
import org.opentaint.ir.go.expr.GoIRExpr
import org.opentaint.ir.go.expr.GoIRExtractExpr
import org.opentaint.ir.go.expr.GoIRFieldAddrExpr
import org.opentaint.ir.go.expr.GoIRFieldExpr
import org.opentaint.ir.go.expr.GoIRIndexAddrExpr
import org.opentaint.ir.go.expr.GoIRIndexExpr
import org.opentaint.ir.go.expr.GoIRLookupExpr
import org.opentaint.ir.go.expr.GoIRMakeChanExpr
import org.opentaint.ir.go.expr.GoIRMakeClosureExpr
import org.opentaint.ir.go.expr.GoIRMakeInterfaceExpr
import org.opentaint.ir.go.expr.GoIRMakeMapExpr
import org.opentaint.ir.go.expr.GoIRMakeSliceExpr
import org.opentaint.ir.go.expr.GoIRMultiConvertExpr
import org.opentaint.ir.go.expr.GoIRNextExpr
import org.opentaint.ir.go.expr.GoIRRangeExpr
import org.opentaint.ir.go.expr.GoIRSelectExpr
import org.opentaint.ir.go.expr.GoIRSliceExpr
import org.opentaint.ir.go.expr.GoIRSliceToArrayPointerExpr
import org.opentaint.ir.go.expr.GoIRTypeAssertExpr
import org.opentaint.ir.go.expr.GoIRUnOpExpr
import org.opentaint.ir.go.impl.GoIRBasicBlockImpl
import org.opentaint.ir.go.impl.GoIRBodyImpl
import org.opentaint.ir.go.impl.GoIRConstImpl
import org.opentaint.ir.go.impl.GoIRFunctionImpl
import org.opentaint.ir.go.impl.GoIRGlobalImpl
import org.opentaint.ir.go.impl.GoIRNamedTypeImpl
import org.opentaint.ir.go.impl.GoIRPackageImpl
import org.opentaint.ir.go.impl.GoIRProgramImpl
import org.opentaint.ir.go.inst.GoIRAssignInst
import org.opentaint.ir.go.inst.GoIRCall
import org.opentaint.ir.go.inst.GoIRDebugRef
import org.opentaint.ir.go.inst.GoIRDefer
import org.opentaint.ir.go.inst.GoIRGo
import org.opentaint.ir.go.inst.GoIRIf
import org.opentaint.ir.go.inst.GoIRInst
import org.opentaint.ir.go.inst.GoIRJump
import org.opentaint.ir.go.inst.GoIRMapUpdate
import org.opentaint.ir.go.inst.GoIRPanic
import org.opentaint.ir.go.inst.GoIRPhi
import org.opentaint.ir.go.inst.GoIRReturn
import org.opentaint.ir.go.inst.GoIRRunDefers
import org.opentaint.ir.go.inst.GoIRSend
import org.opentaint.ir.go.inst.GoIRStore
import org.opentaint.ir.go.inst.GoInstLocation
import org.opentaint.ir.go.proto.BuildProgramResponse
import org.opentaint.ir.go.proto.ProtoBasicTypeKind
import org.opentaint.ir.go.proto.ProtoBinaryOp
import org.opentaint.ir.go.proto.ProtoCallInfo
import org.opentaint.ir.go.proto.ProtoCallMode
import org.opentaint.ir.go.proto.ProtoChanDirection
import org.opentaint.ir.go.proto.ProtoConstValue
import org.opentaint.ir.go.proto.ProtoFunction
import org.opentaint.ir.go.proto.ProtoFunctionBody
import org.opentaint.ir.go.proto.ProtoInstruction
import org.opentaint.ir.go.proto.ProtoNamedTypeKind
import org.opentaint.ir.go.proto.ProtoPackage
import org.opentaint.ir.go.proto.ProtoPosition
import org.opentaint.ir.go.proto.ProtoTypeDefinition
import org.opentaint.ir.go.proto.ProtoUnaryOp
import org.opentaint.ir.go.proto.ProtoValueRef
import org.opentaint.ir.go.type.GoIRArrayType
import org.opentaint.ir.go.type.GoIRBasicType
import org.opentaint.ir.go.type.GoIRBasicTypeKind
import org.opentaint.ir.go.type.GoIRBinaryOp
import org.opentaint.ir.go.type.GoIRCallMode
import org.opentaint.ir.go.type.GoIRChanDirection
import org.opentaint.ir.go.type.GoIRChanType
import org.opentaint.ir.go.type.GoIRFuncType
import org.opentaint.ir.go.type.GoIRInterfaceMethodSig
import org.opentaint.ir.go.type.GoIRInterfaceType
import org.opentaint.ir.go.type.GoIRMapType
import org.opentaint.ir.go.type.GoIRNamedTypeKind
import org.opentaint.ir.go.type.GoIRNamedTypeRef
import org.opentaint.ir.go.type.GoIRPointerType
import org.opentaint.ir.go.type.GoIRSliceType
import org.opentaint.ir.go.type.GoIRStructField
import org.opentaint.ir.go.type.GoIRStructType
import org.opentaint.ir.go.type.GoIRTupleType
import org.opentaint.ir.go.type.GoIRType
import org.opentaint.ir.go.type.GoIRTypeParamType
import org.opentaint.ir.go.type.GoIRUnaryOp
import org.opentaint.ir.go.type.GoIRUnsafePointerType
import org.opentaint.ir.go.value.GoIRBuiltinValue
import org.opentaint.ir.go.value.GoIRConstValue
import org.opentaint.ir.go.value.GoIRConstantValue
import org.opentaint.ir.go.value.GoIRFreeVarValue
import org.opentaint.ir.go.value.GoIRFunctionValue
import org.opentaint.ir.go.value.GoIRGlobalValue
import org.opentaint.ir.go.value.GoIRParameterValue
import org.opentaint.ir.go.value.GoIRRegister
import org.opentaint.ir.go.value.GoIRValue

/**
 * Deserializes a stream of BuildProgramResponse messages into a GoIRProgram.
 */
class GoIRDeserializer {

    // ID resolution maps populated during deserialization
    private val typesById = mutableMapOf<Int, GoIRType>()
    private val lazyNamedTypeRefs = mutableMapOf<Int, GoIRLazyNamedTypeRef>() // type ID -> lazy ref
    private val registerTypeIds = mutableListOf<Pair<GoIRRegister, Int>>() // register + original typeId for re-resolution
    private val exprTypeIds = mutableListOf<Pair<GoIRExpr, Int>>() // expr + original typeId for re-resolution
    private val rawTypeDefinitions = mutableListOf<ProtoTypeDefinition>() // raw proto type defs for re-resolution
    private val placeholderNamedTypes = mutableMapOf<Int, GoIRNamedTypeImpl>() // namedTypeId -> placeholder
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
        val deferredBodies = mutableListOf<ProtoFunctionBody>()
        for (response in responses) {
            when (response.payloadCase) {
                BuildProgramResponse.PayloadCase.TYPE_DEF ->
                    deserializeType(response.typeDef)
                BuildProgramResponse.PayloadCase.PACKAGE_DEF ->
                    deserializePackage(response.packageDef)
                BuildProgramResponse.PayloadCase.FUNCTION_BODY ->
                    deferredBodies.add(response.functionBody) // defer until after type resolution
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

        // Resolve named type references — needed before deserializing function bodies
        // so that types like *MyStruct resolve to the correct named type, not a placeholder.
        resolveNamedTypeRefs()

        // Now deserialize function bodies with correct types
        for (fb in deferredBodies) {
            deserializeFunctionBody(fb)
        }

        // Resolve remaining cross-references (methods, imports, etc.)
        resolveReferences()

        // Build program
        val packages = packagesById.values.associateBy { it.importPath }
        return GoIRProgramImpl(packages)
    }

    // ─── Types ──────────────────────────────────────────────────────

    private fun deserializeType(td: ProtoTypeDefinition) {
        rawTypeDefinitions.add(td)
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
                // Create a placeholder named type that will be filled in during resolution
                val namedTypeId = td.namedRef.namedTypeId
                val typeArgIds = td.namedRef.typeArgIdsList.toList()
                lazyNamedTypeRefs[td.id] = GoIRLazyNamedTypeRef(namedTypeId, typeArgIds)
                // Check if named type is already available (it might be if sent before)
                val named = namedTypesById[namedTypeId]
                if (named != null) {
                    val typeArgs = typeArgIds.map { resolveType(it) }
                    GoIRNamedTypeRef(named, typeArgs)
                } else {
                    // Create placeholder with a stub named type — will be resolved later
                    val placeholder = placeholderNamedTypes.getOrPut(namedTypeId) {
                        GoIRNamedTypeImpl(
                            name = "?$namedTypeId",
                            fullName = "?$namedTypeId",
                            pkg = getOrCreateStubPackage(),
                            underlying = GoIRBasicType(GoIRBasicTypeKind.INT),
                            kind = GoIRNamedTypeKind.STRUCT,
                            position = null,
                        )
                    }
                    GoIRNamedTypeRef(placeholder, emptyList())
                }
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

            // Link the underlying struct/interface type back to this named type
            val underlying = namedType.underlying
            if (underlying is GoIRStructType && underlying.namedType == null) {
                underlying.namedType = namedType
            }
            if (underlying is GoIRInterfaceType && underlying.namedType == null) {
                underlying.namedType = namedType
            }

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

        // Create body early — GoIRBodyImpl.instructions is lazy, so it reads blocks later
        val recoverBlock = if (fb.recoverBlockIndex >= 0) blocks[fb.recoverBlockIndex] else null
        val body = GoIRBodyImpl(fn, blocks, recoverBlock)

        // Build value map for this function's body using a two-pass approach.
        // We use a LazyValueMap that provides placeholders for forward references.
        val lazyValueMap = ValueMap()

        fb.blocksList.forEach { block ->
            block.instructionsList.forEach { pi ->
                if (pi.valueId < 0) return@forEach

                val reg = GoIRRegister(resolveType(pi.typeId), pi.index, pi.name)
                lazyValueMap.register(pi.valueId, reg)
            }
        }

        for ((blockIdx, pb) in fb.blocksList.withIndex()) {
            val block = blocks[blockIdx]
            val instructions = mutableListOf<GoIRInst>()

            for (pi in pb.instructionsList) {
                val loc = GoInstLocation(body, pi.index, blockIdx, positionFromProto(pi.position))
                val inst = deserializeInstruction(pi, loc, fn, lazyValueMap)
                instructions.add(inst)
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

        fn.setBody(body)
    }

    private fun deserializeInstruction(
        pi: ProtoInstruction,
        loc: GoInstLocation,
        fn: GoIRFunctionImpl,
        valueMap: ValueMap,
    ): GoIRInst {
        fun ref(vr: ProtoValueRef): GoIRValue = valueRefFromProto(vr, fn, valueMap)
        fun type(id: Int): GoIRType = resolveType(id)

        // Helper to create a register + assign instruction for expression-based instructions
        val exprType = type(pi.typeId)
        fun assign(expr: GoIRExpr): GoIRAssignInst {
            val reg = GoIRRegister(exprType, pi.index, pi.name)
            registerTypeIds.add(reg to pi.typeId)
            exprTypeIds.add(expr to pi.typeId)
            return GoIRAssignInst(loc, reg, expr)
        }

        return when (pi.instCase) {
            // ─── Expression-based (wrapped in GoIRAssignInst) ───
            ProtoInstruction.InstCase.ALLOC -> assign(
                GoIRAllocExpr(exprType, type(pi.alloc.allocTypeId), pi.alloc.heap, pi.alloc.comment.ifEmpty { null })
            )
            ProtoInstruction.InstCase.BIN_OP -> assign(
                GoIRBinOpExpr(exprType, binOpFromProto(pi.binOp.op), ref(pi.binOp.x), ref(pi.binOp.y))
            )
            ProtoInstruction.InstCase.UN_OP -> assign(
                GoIRUnOpExpr(exprType, unOpFromProto(pi.unOp.op), ref(pi.unOp.x), pi.unOp.commaOk)
            )
            ProtoInstruction.InstCase.CHANGE_TYPE -> assign(
                GoIRChangeTypeExpr(exprType, ref(pi.changeType.x))
            )
            ProtoInstruction.InstCase.CONVERT -> assign(
                GoIRConvertExpr(exprType, ref(pi.convert.x))
            )
            ProtoInstruction.InstCase.MULTI_CONVERT -> assign(
                GoIRMultiConvertExpr(exprType, ref(pi.multiConvert.x), type(pi.multiConvert.fromTypeId), type(pi.multiConvert.toTypeId))
            )
            ProtoInstruction.InstCase.CHANGE_INTERFACE -> assign(
                GoIRChangeInterfaceExpr(exprType, ref(pi.changeInterface.x))
            )
            ProtoInstruction.InstCase.SLICE_TO_ARRAY_POINTER -> assign(
                GoIRSliceToArrayPointerExpr(exprType, ref(pi.sliceToArrayPointer.x))
            )
            ProtoInstruction.InstCase.MAKE_INTERFACE -> assign(
                GoIRMakeInterfaceExpr(exprType, ref(pi.makeInterface.x))
            )
            ProtoInstruction.InstCase.MAKE_CLOSURE -> {
                val closureFn = functionsById[pi.makeClosure.fnId]
                    ?: error("Unknown function ID: ${pi.makeClosure.fnId} in MakeClosure within ${fn.fullName}")
                assign(GoIRMakeClosureExpr(exprType, closureFn, pi.makeClosure.bindingsList.map { ref(it) }))
            }
            ProtoInstruction.InstCase.MAKE_MAP -> assign(
                GoIRMakeMapExpr(exprType, if (pi.makeMap.hasReserve) ref(pi.makeMap.reserve) else null)
            )
            ProtoInstruction.InstCase.MAKE_CHAN -> assign(
                GoIRMakeChanExpr(exprType, ref(pi.makeChan.size))
            )
            ProtoInstruction.InstCase.MAKE_SLICE -> assign(
                GoIRMakeSliceExpr(exprType, ref(pi.makeSlice.len), ref(pi.makeSlice.cap))
            )
            ProtoInstruction.InstCase.FIELD_ADDR -> assign(
                GoIRFieldAddrExpr(exprType, ref(pi.fieldAddr.x), pi.fieldAddr.fieldIndex, pi.fieldAddr.fieldName)
            )
            ProtoInstruction.InstCase.FIELD -> assign(
                GoIRFieldExpr(exprType, ref(pi.field.x), pi.field.fieldIndex, pi.field.fieldName)
            )
            ProtoInstruction.InstCase.INDEX_ADDR -> assign(
                GoIRIndexAddrExpr(exprType, ref(pi.indexAddr.x), ref(pi.indexAddr.index))
            )
            ProtoInstruction.InstCase.INDEX_INST -> assign(
                GoIRIndexExpr(exprType, ref(pi.indexInst.x), ref(pi.indexInst.index))
            )
            ProtoInstruction.InstCase.SLICE_INST -> assign(
                GoIRSliceExpr(
                    exprType,
                    ref(pi.sliceInst.x),
                    if (pi.sliceInst.hasLow) ref(pi.sliceInst.low) else null,
                    if (pi.sliceInst.hasHigh) ref(pi.sliceInst.high) else null,
                    if (pi.sliceInst.hasMax) ref(pi.sliceInst.max) else null,
                )
            )
            ProtoInstruction.InstCase.LOOKUP -> assign(
                GoIRLookupExpr(exprType, ref(pi.lookup.x), ref(pi.lookup.index), pi.lookup.commaOk)
            )
            ProtoInstruction.InstCase.TYPE_ASSERT -> assign(
                GoIRTypeAssertExpr(exprType, ref(pi.typeAssert.x), type(pi.typeAssert.assertedTypeId), pi.typeAssert.commaOk)
            )
            ProtoInstruction.InstCase.RANGE_INST -> assign(
                GoIRRangeExpr(exprType, ref(pi.rangeInst.x))
            )
            ProtoInstruction.InstCase.NEXT -> assign(
                GoIRNextExpr(exprType, ref(pi.next.iter), pi.next.isString)
            )
            ProtoInstruction.InstCase.SELECT_INST -> assign(
                GoIRSelectExpr(
                    exprType,
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
                GoIRExtractExpr(exprType, ref(pi.extract.tuple), pi.extract.extractIndex)
            )

            // ─── Phi (separate instruction, not an expression) ───
            ProtoInstruction.InstCase.PHI -> {
                val reg = GoIRRegister(type(pi.typeId), pi.index, pi.name)
                registerTypeIds.add(reg to pi.typeId)
                GoIRPhi(loc, reg, pi.phi.edgesList.map { ref(it) }, pi.phi.comment.ifEmpty { null })
            }

            // ─── Call (separate instruction, not an expression) ───
            ProtoInstruction.InstCase.CALL -> {
                val reg = GoIRRegister(type(pi.typeId), pi.index, pi.name)
                registerTypeIds.add(reg to pi.typeId)
                GoIRCall(loc, reg, callInfoFromProto(pi.call.call, fn, valueMap))
            }

            // ─── Terminators ───
            ProtoInstruction.InstCase.JUMP -> GoIRJump(loc)
            ProtoInstruction.InstCase.IF_INST -> GoIRIf(loc, ref(pi.ifInst.cond))
            ProtoInstruction.InstCase.RETURN_INST -> GoIRReturn(
                loc, pi.returnInst.resultsList.map { ref(it) }
            )
            ProtoInstruction.InstCase.PANIC_INST -> GoIRPanic(loc, ref(pi.panicInst.x))

            // ─── Effect-only ───
            ProtoInstruction.InstCase.STORE -> GoIRStore(
                loc, ref(pi.store.addr), ref(pi.store.`val`)
            )
            ProtoInstruction.InstCase.MAP_UPDATE -> GoIRMapUpdate(
                loc, ref(pi.mapUpdate.map), ref(pi.mapUpdate.key), ref(pi.mapUpdate.value)
            )
            ProtoInstruction.InstCase.SEND -> GoIRSend(
                loc, ref(pi.send.chan), ref(pi.send.x)
            )
            ProtoInstruction.InstCase.GO_INST -> GoIRGo(
                loc, callInfoFromProto(pi.goInst.call, fn, valueMap)
            )
            ProtoInstruction.InstCase.DEFER_INST -> GoIRDefer(
                loc, callInfoFromProto(pi.deferInst.call, fn, valueMap)
            )
            ProtoInstruction.InstCase.RUN_DEFERS -> GoIRRunDefers(loc)
            ProtoInstruction.InstCase.DEBUG_REF -> GoIRDebugRef(
                loc, ref(pi.debugRef.x), pi.debugRef.isAddr
            )
            else -> throw IllegalStateException("Unknown instruction type: ${pi.instCase}")
        }
    }

    // ─── Value references ───────────────────────────────────────────

    private fun valueRefFromProto(
        vr: ProtoValueRef,
        fn: GoIRFunctionImpl,
        valueMap: ValueMap,
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
        valueMap: ValueMap,
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

    /**
     * Phase 1: Resolve named type references.
     * Called BEFORE function body deserialization so that types are correct.
     *
     * During initial type deserialization, NAMED_REF types may reference named types
     * that haven't been loaded yet (from packages). We create placeholder GoIRNamedTypeRef
     * objects with stub named types. Now that packages are loaded, we:
     * 1. Replace placeholders with real named types in existing GoIRNamedTypeRef objects
     * 2. Re-deserialize all types to rebuild composite types (pointers, slices, etc.)
     */
    private fun resolveNamedTypeRefs() {
        // Step 1: Update placeholder GoIRNamedTypeRef objects to point to real named types
        for ((typeId, lazyRef) in lazyNamedTypeRefs) {
            val named = namedTypesById[lazyRef.namedTypeId]
            if (named != null) {
                val existing = typesById[typeId]
                if (existing is GoIRNamedTypeRef) {
                    // Update in-place — all references to this object will see the real named type
                    existing.namedType = named
                    existing.typeArgs = lazyRef.typeArgIds.map { resolveType(it) }
                } else {
                    typesById[typeId] = GoIRNamedTypeRef(named, lazyRef.typeArgIds.map { resolveType(it) })
                }
            }
        }

        // Step 2: Re-deserialize all types to rebuild composites (Pointer, Slice, etc.)
        // that reference now-resolved named types
        val savedTypeDefs = rawTypeDefinitions.toList()
        rawTypeDefinitions.clear()
        for (td in savedTypeDefs) {
            deserializeType(td)
        }
        // Re-apply named ref resolution (re-deserialization may have overwritten entries)
        for ((typeId, lazyRef) in lazyNamedTypeRefs) {
            val named = namedTypesById[lazyRef.namedTypeId]
            if (named != null) {
                val existing = typesById[typeId]
                if (existing is GoIRNamedTypeRef) {
                    existing.namedType = named
                    existing.typeArgs = lazyRef.typeArgIds.map { resolveType(it) }
                } else {
                    typesById[typeId] = GoIRNamedTypeRef(named, lazyRef.typeArgIds.map { resolveType(it) })
                }
            }
        }
    }

    /**
     * Phase 2: Resolve remaining cross-references (methods, imports, etc.)
     */
    private fun resolveReferences() {
        // Re-resolve register types (captured during function body deserialization)
        for ((reg, typeId) in registerTypeIds) {
            val resolved = resolveType(typeId)
            if (resolved != reg.type) {
                reg.type = resolved
            }
        }

        // Re-resolve expression types
        for ((expr, typeId) in exprTypeIds) {
            val resolved = resolveType(typeId)
            if (resolved != expr.type) {
                expr.updateType(resolved)
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

    /** Mutation helper for deserialization — updates the mutable [type] property on concrete expr classes. */
    private fun GoIRExpr.updateType(newType: GoIRType) {
        when (this) {
            is GoIRAllocExpr -> type = newType
            is GoIRBinOpExpr -> type = newType
            is GoIRUnOpExpr -> type = newType
            is GoIRChangeTypeExpr -> type = newType
            is GoIRConvertExpr -> type = newType
            is GoIRMultiConvertExpr -> type = newType
            is GoIRChangeInterfaceExpr -> type = newType
            is GoIRSliceToArrayPointerExpr -> type = newType
            is GoIRMakeInterfaceExpr -> type = newType
            is GoIRTypeAssertExpr -> type = newType
            is GoIRMakeClosureExpr -> type = newType
            is GoIRMakeMapExpr -> type = newType
            is GoIRMakeChanExpr -> type = newType
            is GoIRMakeSliceExpr -> type = newType
            is GoIRFieldAddrExpr -> type = newType
            is GoIRFieldExpr -> type = newType
            is GoIRIndexAddrExpr -> type = newType
            is GoIRIndexExpr -> type = newType
            is GoIRSliceExpr -> type = newType
            is GoIRLookupExpr -> type = newType
            is GoIRRangeExpr -> type = newType
            is GoIRNextExpr -> type = newType
            is GoIRSelectExpr -> type = newType
            is GoIRExtractExpr -> type = newType
        }
    }

    // (rebuildType removed — type re-resolution is handled by re-deserializing all type defs)

    // ─── Helpers ────────────────────────────────────────────────────

    private fun resolveType(id: Int): GoIRType {
        if (id == 0) return GoIRBasicType(GoIRBasicTypeKind.INT) // fallback for unset
        val cached = typesById[id]
        // If the type was a NAMED_REF placeholder (stored as INT), check if we can resolve it now
        if (cached is GoIRBasicType && cached.kind == GoIRBasicTypeKind.INT && id in lazyNamedTypeRefs) {
            val lazyRef = lazyNamedTypeRefs[id]!!
            val named = namedTypesById[lazyRef.namedTypeId]
            if (named != null) {
                val typeArgs = lazyRef.typeArgIds.map { resolveType(it) }
                val resolved = GoIRNamedTypeRef(named, typeArgs)
                typesById[id] = resolved
                return resolved
            }
        }
        return cached ?: GoIRBasicType(GoIRBasicTypeKind.INT) // fallback
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

class ValueMap {
    private val registers = mutableMapOf<Int, GoIRRegister>()

    fun register(id: Int, reg: GoIRRegister) {
        registers[id] = reg
    }

    operator fun get(id: Int): GoIRRegister {
        return registers[id] ?: error("Register for value $id not registered")
    }
}
