package org.opentaint.ir.go.client

import it.unimi.dsi.fastutil.ints.Int2IntOpenHashMap
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap
import it.unimi.dsi.fastutil.ints.IntOpenHashSet
import org.opentaint.ir.go.api.GoIRField
import org.opentaint.ir.go.api.GoIRFreeVar
import org.opentaint.ir.go.api.GoIRInterfaceMethod
import org.opentaint.ir.go.api.GoIRPackage
import org.opentaint.ir.go.api.GoIRParameter
import org.opentaint.ir.go.api.GoIRPosition
import org.opentaint.ir.go.api.GoIRProgram
import org.opentaint.ir.go.api.GoIrFunctionReference
import org.opentaint.ir.go.cfg.GoIRCallInfo
import org.opentaint.ir.go.cfg.GoIRCallTarget
import org.opentaint.ir.go.cfg.GoIRSelectState
import org.opentaint.ir.go.expr.GoIRAllocExpr
import org.opentaint.ir.go.expr.GoIRBinOpExpr
import org.opentaint.ir.go.expr.GoIRBuiltinValueExpr
import org.opentaint.ir.go.expr.GoIRChangeInterfaceExpr
import org.opentaint.ir.go.expr.GoIRChangeTypeExpr
import org.opentaint.ir.go.expr.GoIRConvertExpr
import org.opentaint.ir.go.expr.GoIRExpr
import org.opentaint.ir.go.expr.GoIRExtractExpr
import org.opentaint.ir.go.expr.GoIRFieldAddrExpr
import org.opentaint.ir.go.expr.GoIRFieldExpr
import org.opentaint.ir.go.expr.GoIRFreeVarValueExpr
import org.opentaint.ir.go.expr.GoIRFunctionValueExpr
import org.opentaint.ir.go.expr.GoIRGlobalValueExpr
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
import org.opentaint.ir.go.inst.GoIRFieldStore
import org.opentaint.ir.go.inst.GoIRGlobalStore
import org.opentaint.ir.go.inst.GoIRGo
import org.opentaint.ir.go.inst.GoIRIf
import org.opentaint.ir.go.inst.GoIRIndexStore
import org.opentaint.ir.go.inst.GoIRInst
import org.opentaint.ir.go.inst.GoIRInstRef
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
import org.opentaint.ir.go.proto.ProtoModelProgram
import org.opentaint.ir.go.proto.ProtoNamedType
import org.opentaint.ir.go.proto.ProtoNamedTypeKind
import org.opentaint.ir.go.proto.ProtoPackage
import org.opentaint.ir.go.proto.ProtoPosition
import org.opentaint.ir.go.proto.ProtoProgram
import org.opentaint.ir.go.proto.ProtoTypeDefinition
import org.opentaint.ir.go.proto.ProtoUnaryOp
import org.opentaint.ir.go.proto.ProtoValueRef
import org.opentaint.ir.go.type.GoIRAnonymousInterfaceType
import org.opentaint.ir.go.type.GoIRAnonymousInterfaceTypeRef
import org.opentaint.ir.go.type.GoIRArrayType
import org.opentaint.ir.go.type.GoIRBasicType
import org.opentaint.ir.go.type.GoIRBasicTypeKind
import org.opentaint.ir.go.type.GoIRBinaryOp
import org.opentaint.ir.go.type.GoIRCallMode
import org.opentaint.ir.go.type.GoIRChanDirection
import org.opentaint.ir.go.type.GoIRChanType
import org.opentaint.ir.go.type.GoIRFuncType
import org.opentaint.ir.go.type.GoIRInterfaceMethodSig
import org.opentaint.ir.go.type.GoIRMapType
import org.opentaint.ir.go.type.GoIRNamedInterfaceType
import org.opentaint.ir.go.type.GoIRNamedTypeKind
import org.opentaint.ir.go.type.GoIRNamedTypeRef
import org.opentaint.ir.go.type.GoIRPointerType
import org.opentaint.ir.go.type.GoIRSliceType
import org.opentaint.ir.go.type.GoIRStructField
import org.opentaint.ir.go.type.GoIRStructType
import org.opentaint.ir.go.type.GoIRTupleType
import org.opentaint.ir.go.type.GoIRType
import org.opentaint.ir.go.type.GoIRTypeParamRef
import org.opentaint.ir.go.type.GoIRTypeParamType
import org.opentaint.ir.go.type.GoIRUnaryOp
import org.opentaint.ir.go.type.GoIRUnsafePointerType
import org.opentaint.ir.go.type.NamedTypeRef
import org.opentaint.ir.go.value.GoIRConstValue
import org.opentaint.ir.go.value.GoIRConstantValue
import org.opentaint.ir.go.value.GoIRParameterValue
import org.opentaint.ir.go.value.GoIRRegister
import org.opentaint.ir.go.value.GoIRValue

class GoIRDeserializer {
    private lateinit var packages: Array<GoIRPackageImpl?>
    private lateinit var types: Array<GoIRType?>
    private lateinit var namedTypes: Array<GoIRNamedTypeImpl?>
    private lateinit var functions: Array<GoIRFunctionImpl?>
    private val globalsById = Int2ObjectOpenHashMap<GoIRGlobalImpl>()

    private val errors = mutableListOf<String>()

    /** Time reported by the Go server for SSA build + serialization (ms). */
    var serverBuildTimeMs: Long = 0L
        private set

    fun deserialize(responses: Iterator<BuildProgramResponse>): GoIRProgram {
        var program: ProtoProgram? = null
        val models = mutableListOf<ProtoModelProgram>()
        for (r in responses) {
            when (r.payloadCase) {
                BuildProgramResponse.PayloadCase.PROGRAM -> {
                    program = r.program
                }

                BuildProgramResponse.PayloadCase.MODEL_PROGRAM -> models += r.modelProgram

                BuildProgramResponse.PayloadCase.SUMMARY -> serverBuildTimeMs = r.summary.buildTimeMs
                BuildProgramResponse.PayloadCase.ERROR -> handleError(r.error.message, r.error.fatal)
                BuildProgramResponse.PayloadCase.PAYLOAD_NOT_SET -> {}
                BuildProgramResponse.PayloadCase.TYPE_DEF,
                BuildProgramResponse.PayloadCase.PACKAGE_DEF,
                BuildProgramResponse.PayloadCase.FUNCTION_BODY -> error("Impossible payload")
            }
        }

        check(program != null) { "Unexpected IR build issue" }
        val mergedProgram = GoModelMerger().merge(program, models)

        val resultPackages = hashMapOf<String, GoIRPackage>()
        val anonymousInterfaces = Int2ObjectOpenHashMap<GoIRAnonymousInterfaceType>()
        val result = GoIRProgramImpl(resultPackages, anonymousInterfaces)

        val pkgInfo = deserializePackages(mergedProgram.packagesList)
        packages.filterNotNull().forEach {
            resultPackages[it.importPath] = it
        }

        TypeDeserializationCtx(result, mergedProgram.typesList, pkgInfo, anonymousInterfaces)
            .deserializeTypes()

        FunctionDeserializationCtx(pkgInfo, result)
            .deserializeFunctions()

        mergedProgram.packagesList.forEach { deserializePackageMembers(it) }
        mergedProgram.functionBodiesList.forEach { deserializeFunctionBody(it) }
        pkgInfo.namedTypes.forEach { resolveNamedTypeBindings(it) }

        return result
    }

    private fun handleError(message: String, fatal: Boolean) {
        if (fatal) {
            throw RuntimeException("Fatal error from Go server: $message")
        }
        errors.add(message)
    }

    private data class PackageInfo(
        val namedTypes: List<ProtoNamedType>,
        val namedTypePkgId: Int2IntOpenHashMap,
        val functions: List<ProtoFunction>,
        val functionPkgId: Int2IntOpenHashMap,
    )

    private fun deserializePackages(packagesList: List<ProtoPackage>): PackageInfo {
        val deserialized = arrayOfNulls<GoIRPackageImpl?>(packagesList.size + 1).also {
            packages = it
        }

        val namedTypePkgId = Int2IntOpenHashMap()
        val namedTypes = mutableListOf<ProtoNamedType>()

        val functions = mutableListOf<ProtoFunction>()
        val functionPkgId = Int2IntOpenHashMap()

        for (pkg in packagesList) {
            deserialized[pkg.id] = GoIRPackageImpl(
                importPath = pkg.importPath,
                name = pkg.name,
                isStdlib = pkg.isStdlib,
                isDependency = pkg.isDependency,
            )

            pkg.namedTypesList.forEach {
                namedTypes += it
                namedTypePkgId[it.id] = pkg.id
            }

            pkg.functionsList.forEach {
                functions += it
                functionPkgId[it.id] = pkg.id
            }
        }

        return PackageInfo(namedTypes, namedTypePkgId, functions, functionPkgId)
    }

    private class TypeDeserializationCtx(
        val program: GoIRProgram,
        typesList: List<ProtoTypeDefinition>,
        val pkgInfo: PackageInfo,
        val anonymousInterfaces: Int2ObjectOpenHashMap<GoIRAnonymousInterfaceType>,
    ) {
        val typeDefinitions: Array<ProtoTypeDefinition?> = typesList.toArrayById { id }
        val namedTypeDefs: Array<ProtoNamedType?> = pkgInfo.namedTypes.toArrayById { id }

        val deserialized = arrayOfNulls<GoIRType?>(typeDefinitions.size)
        val deserializedNamed = arrayOfNulls<GoIRNamedTypeImpl?>(namedTypeDefs.size)
        val namedRefs = arrayOfNulls<NamedTypeRef?>(namedTypeDefs.size)

        val typesOnStackOnce = IntOpenHashSet()
        val typesOnStackTwice = IntOpenHashSet()

        private inline fun <reified T> List<T>.toArrayById(id: T.() -> Int): Array<T?> {
            var result = arrayOfNulls<T?>(size + 128)
            for (it in this) {
                val id = it.id()
                if (id < result.size) {
                    result[id] = it
                    continue
                }

                val newSize = id + 128
                result = result.copyOf(newSize)
                result[id] = it
            }
            return result
        }
    }

    private fun TypeDeserializationCtx.deserializeTypes() {
        this.namedTypeDefs.forEachNonNull {
            deserializedNamed[it.id] = deserializeNamedTypeBody(it)
        }

        val unprocessedFunctions = mutableListOf<ProtoTypeDefinition>()
        this.typeDefinitions.forEachNonNull {
            if (!it.hasFuncType()) {
                deserializeType(it)
            } else {
                if (deserialized[it.id] == null) {
                    unprocessedFunctions.add(it)
                }
            }
        }

        unprocessedFunctions.forEach { deserializeType(it) }

        this@GoIRDeserializer.types = deserialized
        this@GoIRDeserializer.namedTypes = deserializedNamed
    }

    private fun TypeDeserializationCtx.resolveNamedRef(id: Int): NamedTypeRef? {
        if (id == 0) return null

        check(id < namedRefs.size) { "Named type was not serialized: $id" }

        val current = namedRefs[id]
        if (current != null) return current

        val typeDef = namedTypeDefs[id] ?: error("Unexpected named type id: $id")

        val pkg = namedTypePkg(typeDef)
        val result = NamedTypeRef(pkg.importPath, typeDef.name).also { it.program = program }
        namedRefs[id] = result
        return result
    }

    private fun TypeDeserializationCtx.resolveType(id: Int): GoIRType {
        check(id < typeDefinitions.size) { "Type was not serialized: $id" }

        if (id == 0) {
            TODO("No type id")
        }

        val current = deserialized[id]
        if (current != null) return current

        val typeDef = typeDefinitions[id]
            ?: error("Unexpected type id: $id")

        return deserializeType(typeDef)
    }

    private fun TypeDeserializationCtx.deserializeType(td: ProtoTypeDefinition): GoIRType {
        val id = td.id
        val current = deserialized[id]
        if (current != null) return current

        val stackTracker = when {
            typesOnStackOnce.add(id) -> typesOnStackOnce
            typesOnStackTwice.add(id) -> typesOnStackTwice
            else -> TODO("Recursive types")
        }

        val result = deserializeTypeBody(td).also {
            stackTracker.remove(id)
        }

        deserialized[id] = result
        return result
    }

    private fun TypeDeserializationCtx.saveTypeToAvoidRecursion(id: Int, type: GoIRType) {
        deserialized[id] = type
        typesOnStackTwice.remove(id)
    }

    private fun TypeDeserializationCtx.namedTypePkg(typeDef: ProtoNamedType): GoIRPackageImpl {
        val pkgId = pkgInfo.namedTypePkgId.get(typeDef.id)
        return getPackage(pkgId)
    }

    private fun getPackage(id: Int): GoIRPackageImpl =
        packages[id] ?: error("No package found for id: $id")

    private fun TypeDeserializationCtx.deserializeNamedTypeBody(nt: ProtoNamedType): GoIRNamedTypeImpl {
        val pkg = namedTypePkg(nt)

        val namedType = GoIRNamedTypeImpl(
            name = nt.name,
            fullName = nt.fullName,
            pkg = pkg,
            underlying = resolveType(nt.underlyingTypeId),
            kind = namedTypeKindFromProto(nt.kind),
            position = positionFromProto(nt.position),
        )

        for (fd in nt.fieldsList) {
            namedType._fields += GoIRField(
                name = fd.name,
                type = resolveType(fd.typeId),
                index = fd.index,
                isEmbedded = fd.embedded,
                isExported = fd.exported,
                tag = fd.tag,
                enclosingType = namedType,
            )
        }

        for (im in nt.interfaceMethodsList) {
            val funcType = resolveFunctionalType(im.signatureTypeId)
            namedType._interfaceMethods += GoIRInterfaceMethod(
                name = im.name,
                signature = funcType,
                enclosingInterface = namedType,
            )
        }

        pkg.addNamedType(namedType)
        return namedType
    }

    private fun resolveNamedTypeBindings(nt: ProtoNamedType) {
        val type = getNamedType(nt.id)

        nt.embeddedInterfaceIdsList.forEach {
            type._embeddedInterfaces += getNamedType(it)
        }

        nt.methodIdsList.forEach {
            type._methods += getFunction(it)
        }

        nt.pointerMethodIdsList.forEach {
            type._pointerMethods += getFunction(it)
        }
    }

    private fun TypeDeserializationCtx.resolveFunctionalType(id: Int): GoIRFuncType =
        resolveType(id) as? GoIRFuncType
            ?: error("Unexpected non-functional type")

    private fun TypeDeserializationCtx.deserializeTypeBody(td: ProtoTypeDefinition): GoIRType {
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
                val st = td.structType
                val structName = resolveNamedRef(st.namedTypeId)
                val fields = st.fieldsList.mapNonEmpty { f ->
                    GoIRStructField(f.name, resolveType(f.typeId), f.embedded, f.tag)
                }
                GoIRStructType(fields, structName)
            }

            ProtoTypeDefinition.TypeCase.INTERFACE_TYPE -> {
                val it = td.interfaceType
                val interfaceName = resolveNamedRef(it.namedTypeId)

                // note: interface method receiver points to this interfaces
                val interfaceRef = if (interfaceName != null) {
                    GoIRNamedTypeRef(interfaceName, emptyList())
                } else {
                    GoIRAnonymousInterfaceTypeRef(td.id).also { it.program = program }
                }
                saveTypeToAvoidRecursion(td.id, interfaceRef)

                val methods = it.methodsList.mapNonEmpty { m ->
                    val funcType = resolveFunctionalType(m.signatureTypeId)
                    GoIRInterfaceMethodSig(m.name, funcType)
                }
                val embeds = it.embedTypeIdsList.mapNonEmpty { resolveType(it) }

                if (interfaceName != null) {
                    GoIRNamedInterfaceType(methods, embeds, interfaceName)
                } else {
                    GoIRAnonymousInterfaceType(td.id, methods, embeds).also {
                        anonymousInterfaces.put(td.id, it)
                    }
                }
            }

            ProtoTypeDefinition.TypeCase.FUNC_TYPE -> {
                val ft = td.funcType
                GoIRFuncType(
                    params = ft.paramTypeIdsList.mapNonEmpty { resolveType(it) },
                    results = ft.resultTypeIdsList.mapNonEmpty { resolveType(it) },
                    isVariadic = ft.variadic,
                    recv = if (ft.recvTypeId != 0) resolveType(ft.recvTypeId) else null,
                )
            }

            ProtoTypeDefinition.TypeCase.NAMED_REF -> {
                val nr = td.namedRef
                val namedType = resolveNamedRef(nr.namedTypeId)
                    ?: error("Named type ref without ref")

                val typeArgs = nr.typeArgIdsList.mapNonEmpty { resolveType(it) }
                GoIRNamedTypeRef(namedType, typeArgs)
            }

            ProtoTypeDefinition.TypeCase.TYPE_PARAM -> {
                val tp = td.typeParam
                val ref = GoIRTypeParamRef(tp.name, tp.index)

                // note: type params may have recursive constraints
                saveTypeToAvoidRecursion(td.id, ref)

                val constraint = resolveType(tp.constraintTypeId)
                GoIRTypeParamType(ref, constraint)
            }

            ProtoTypeDefinition.TypeCase.TUPLE ->
                GoIRTupleType(td.tuple.elementTypeIdsList.mapNonEmpty { resolveType(it) })

            ProtoTypeDefinition.TypeCase.UNSAFE_POINTER ->
                GoIRUnsafePointerType

            else -> GoIRBasicType(GoIRBasicTypeKind.INT) // fallback
        }
        return type
    }

    private fun getType(id: Int): GoIRType {
        return types[id]
            ?: error("No type found for $id")
    }

    private fun getNamedType(id: Int): GoIRNamedTypeImpl {
        return namedTypes[id]
            ?: error("No named type found for $id")
    }

    private fun getFunctionalType(id: Int): GoIRFuncType =
        getType(id) as? GoIRFuncType
            ?: error("Unexpected non-functional type")

    private class FunctionDeserializationCtx(
        val packageInfo: PackageInfo,
        val program: GoIRProgram,
    )

    private fun FunctionDeserializationCtx.deserializeFunctions() {
        val maxFunctionId = packageInfo.functions.maxOfOrNull { it.id } ?: return
        val functions = arrayOfNulls<ProtoFunction>(maxFunctionId + 1)
        packageInfo.functions.forEach { functions[it.id] = it }

        val deserialized = arrayOfNulls<GoIRFunctionImpl?>(maxFunctionId + 1).also {
            this@GoIRDeserializer.functions = it
        }

        for (pf in functions) {
            pf ?: continue

            val pkg = getPackage(packageInfo.functionPkgId.get(pf.id))

            var parentFunction: GoIrFunctionReference? = null
            if (pf.parentFunctionId != 0) {
                parentFunction = resolveFunctionReference(pf.parentFunctionId, functions)
            }

            val anonymousFunctions = pf.anonFunctionIdsList.map { resolveFunctionReference(it, functions) }

            deserialized[pf.id] = GoIRFunctionImpl(
                name = pf.name,
                fullName = pf.fullName,
                pkg = pkg,
                signature = getFunctionalType(pf.signatureTypeId),
                params = pf.paramsList.map { p -> GoIRParameter(p.name, getType(p.typeId), p.index) },
                freeVars = pf.freeVarsList.map { fv -> GoIRFreeVar(fv.name, getType(fv.typeId), fv.index) },
                position = positionFromProto(pf.position),
                isMethod = pf.isMethod,
                isPointerReceiver = pf.isPointerReceiver,
                isExported = pf.isExported,
                isSynthetic = pf.isSynthetic,
                syntheticKind = pf.syntheticKind.ifEmpty { null },
                declaredHasBody = pf.hasBody,
                parent = parentFunction,
                anonymousFunctions = anonymousFunctions,
            )
        }
    }

    private fun FunctionDeserializationCtx.resolveFunctionReference(
        fnId: Int, defs: Array<ProtoFunction?>
    ): GoIrFunctionReference {
        val fDef = defs[fnId]
            ?: error("Function $fnId not serialized")
        return resolveFunctionReference(fDef)
    }

    private fun FunctionDeserializationCtx.resolveFunctionReference(
        pf: ProtoFunction
    ): GoIrFunctionReference {
        val pkg = getPackage(packageInfo.functionPkgId.get(pf.id))
        val signature = getFunctionalType(pf.signatureTypeId)
        return GoIrFunctionReference(pkg.importPath, pf.name, signature).also { it.program = program }
    }

    private fun getFunction(id: Int): GoIRFunctionImpl =
        functions[id]
            ?: error("No function found for $id")

    private fun deserializePackageMembers(pp: ProtoPackage) {
        val pkg = getPackage(pp.id)

        for (pf in pp.functionsList) {
            val fn = getFunction(pf.id)
            pkg.addFunction(fn)
        }

        // Globals
        for (pg in pp.globalsList) {
            val global = GoIRGlobalImpl(
                name = pg.name,
                fullName = pg.fullName,
                type = getType(pg.typeId),
                pkg = pkg,
                isExported = pg.isExported,
                position = positionFromProto(pg.position),
            )
            globalsById.put(pg.id, global)
            pkg.addGlobal(global)
        }

        // Constants
        for (pc in pp.constantsList) {
            val const = GoIRConstImpl(
                name = pc.name,
                fullName = pc.fullName,
                type = getType(pc.typeId),
                value = constValueFromProto(pc.value),
                pkg = pkg,
                isExported = pc.isExported,
                position = positionFromProto(pc.position),
            )
            pkg.addConst(const)
        }

        if (pp.initFunctionId != 0) {
            val initFn = getFunction(pp.initFunctionId)
            pkg.initFunction = initFn
        }

        pp.importIdsList.forEach {
            val importPkg = getPackage(it)
            pkg._imports.add(importPkg)
        }
    }

    // ─── Function Bodies ────────────────────────────────────────────

    private fun deserializeFunctionBody(fb: ProtoFunctionBody) {
        val fn = getFunction(fb.functionId)

        val phiEdgeSyntheticLoads = collectPhiEdgeSyntheticLoads(fb, fn.fullName)
        val phiEdgeSyntheticLoadsByPred = phiEdgeSyntheticLoads.groupBy { it.predInstProtoIndex }
        val protoInstBlockIndices = mutableMapOf<Int, Int>()
        fb.blocksList.forEachIndexed { blockIndex, block ->
            block.instructionsList.forEach { protoInstBlockIndices[it.index] = blockIndex }
        }

        val protoToNew = Int2IntOpenHashMap()
        val blockStartProtoToNew = Int2IntOpenHashMap()
        var nextIndex = 0
        for (pb in fb.blocksList) {
            if (pb.instructionsList.isNotEmpty()) {
                blockStartProtoToNew.put(pb.instructionsList[0].index, nextIndex)
            }
            for (pi in pb.instructionsList) {
                phiEdgeSyntheticLoadsByPred[pi.index]?.forEach { load ->
                    load.index = nextIndex++
                }
                nextIndex += countSyntheticLoads(pi)
                protoToNew.put(pi.index, nextIndex)
                nextIndex++
            }
        }

        val blocks = mutableListOf<GoIRBasicBlockImpl>()
        for (pb in fb.blocksList) {
            blocks.add(GoIRBasicBlockImpl(
                index = pb.index,
                label = pb.label.ifEmpty { null },
            ))
        }

        val recoverBlock = if (fb.recoverBlockIndex >= 0) blocks[fb.recoverBlockIndex] else null
        val body = GoIRBodyImpl(fn, blocks, recoverBlock)

        val lazyValueMap = ValueMap()
        fb.blocksList.forEach { block ->
            block.instructionsList.forEach { pi ->
                if (pi.valueId <= 0) return@forEach
                val newIdx = protoToNew.get(pi.index)
                val reg = GoIRRegister(getType(pi.typeId), newIdx, pi.name)
                lazyValueMap.register(pi.valueId, reg)
            }
        }

        val phiEdgeSyntheticValues = mutableMapOf<PhiEdgeSyntheticLoadKey, GoIRRegister>()
        val phiEdgeSyntheticInstructions = mutableMapOf<PhiEdgeSyntheticLoadKey, GoIRAssignInst>()
        for (load in phiEdgeSyntheticLoads) {
            val predBlockIndex = protoInstBlockIndices[load.predInstProtoIndex]
                ?: error("No predecessor block for phi edge pred inst ${load.predInstProtoIndex} in ${fn.fullName}")
            val (reg, inst) = createSyntheticLoad(load.value, body, predBlockIndex, load.index)
            phiEdgeSyntheticValues[load.key] = reg
            phiEdgeSyntheticInstructions[load.key] = inst
        }

        val ctx = InstContext(body, blockIdx = 0, fn = fn, valueMap = lazyValueMap,
                              instructions = mutableListOf(), protoToNew = protoToNew,
                              blockStartProtoToNew = blockStartProtoToNew,
                              phiEdgeSyntheticValues = phiEdgeSyntheticValues)
        for ((blockIdx, pb) in fb.blocksList.withIndex()) {
            val block = blocks[blockIdx]
            ctx.blockIdx = blockIdx
            ctx.instructions = mutableListOf()
            for (pi in pb.instructionsList) {
                phiEdgeSyntheticLoadsByPred[pi.index]?.forEach { load ->
                    ctx.instructions.add(phiEdgeSyntheticInstructions.getValue(load.key))
                    ctx.nextIndex++
                }
                val inst = ctx.deserializeInstruction(pi)
                ctx.instructions.add(inst)
            }
            block.setInstructions(ctx.instructions)

            block.predIndices = pb.predIndicesList.toList()
            block.succIndices = pb.succIndicesList.toList()
            block.idomIndex = pb.idomIndex
            block.domineeIndices = pb.domineeIndicesList.toList()
        }

        for (block in blocks) {
            block.resolvePredecessors(blocks)
            block.resolveSuccessors(blocks)
            block.resolveIdom(blocks)
            block.resolveDominees(blocks)
        }

        specializeStores(blocks)
        fn.setBody(body)
    }

    /**
     * Recover field/index structure for pointer stores. Each store's address register is, in SSA,
     * defined exactly once; if that definition is a FieldAddr or IndexAddr expression we replace the
     * generic [GoIRStore] with a [GoIRFieldStore] / [GoIRIndexStore] that records the base and
     * field/index. Reads block instructions directly (never body.instructions, which is lazy and
     * would otherwise cache the pre-specialization list). Direct definitions only — addresses derived
     * through conversions etc. stay generic.
     */
    private fun specializeStores(blocks: List<GoIRBasicBlockImpl>) {
        val defByRegister = HashMap<GoIRRegister, GoIRExpr>()
        for (block in blocks) {
            for (inst in block.instructions) {
                if (inst is GoIRAssignInst) {
                    defByRegister[inst.register] = inst.expr
                }
            }
        }
        for (block in blocks) {
            var changed = false
            val newInstructions = block.instructions.map { inst ->
                if (inst !is GoIRStore) return@map inst
                val addr = inst.addr as? GoIRRegister ?: return@map inst
                when (val def = defByRegister[addr]) {
                    is GoIRFieldAddrExpr -> {
                        changed = true
                        GoIRFieldStore(inst.location, inst.addr, def.x, def.fieldIndex, def.fieldName, inst.value)
                    }
                    is GoIRIndexAddrExpr -> {
                        changed = true
                        GoIRIndexStore(inst.location, inst.addr, def.x, def.indexValue, inst.value)
                    }
                    else -> inst
                }
            }
            if (changed) block.setInstructions(newInstructions)
        }
    }

    private fun InstContext.deserializeInstruction(
        pi: ProtoInstruction,
    ): GoIRInst {
        fun ref(vr: ProtoValueRef): GoIRValue = valueRefFromProto(vr, this)
        fun type(id: Int): GoIRType = getType(id)
        fun translate(protoIdx: Int): GoIRInstRef = GoIRInstRef(
            protoToNew.get(protoIdx)
        )
        fun translateBranch(protoIdx: Int): GoIRInstRef = GoIRInstRef(
            blockStartProtoToNew.get(protoIdx)
        )

        val newIdx = protoToNew.get(pi.index)
        val loc = GoInstLocation(body, newIdx, blockIdx, positionFromProto(pi.position))

        fun exprType() = type(pi.typeId)

        fun assign(expr: GoIRExpr): GoIRAssignInst {
            val reg = valueMap[pi.valueId]
            return GoIRAssignInst(loc, reg, expr)
        }

        return when (pi.instCase) {
            ProtoInstruction.InstCase.ALLOC -> assign(
                GoIRAllocExpr(exprType(), type(pi.alloc.allocTypeId), pi.alloc.heap, pi.alloc.comment.ifEmpty { null })
            )
            ProtoInstruction.InstCase.BIN_OP -> assign(
                GoIRBinOpExpr(exprType(), binOpFromProto(pi.binOp.op), ref(pi.binOp.x), ref(pi.binOp.y))
            )
            ProtoInstruction.InstCase.UN_OP -> assign(
                GoIRUnOpExpr(exprType(), unOpFromProto(pi.unOp.op), ref(pi.unOp.x), pi.unOp.commaOk)
            )
            ProtoInstruction.InstCase.CHANGE_TYPE -> assign(
                GoIRChangeTypeExpr(exprType(), ref(pi.changeType.x))
            )
            ProtoInstruction.InstCase.CONVERT -> assign(
                GoIRConvertExpr(exprType(), ref(pi.convert.x))
            )
            ProtoInstruction.InstCase.MULTI_CONVERT -> assign(
                GoIRMultiConvertExpr(exprType(), ref(pi.multiConvert.x), type(pi.multiConvert.fromTypeId), type(pi.multiConvert.toTypeId))
            )
            ProtoInstruction.InstCase.CHANGE_INTERFACE -> assign(
                GoIRChangeInterfaceExpr(exprType(), ref(pi.changeInterface.x))
            )
            ProtoInstruction.InstCase.SLICE_TO_ARRAY_POINTER -> assign(
                GoIRSliceToArrayPointerExpr(exprType(), ref(pi.sliceToArrayPointer.x))
            )
            ProtoInstruction.InstCase.MAKE_INTERFACE -> assign(
                GoIRMakeInterfaceExpr(exprType(), ref(pi.makeInterface.x))
            )
            ProtoInstruction.InstCase.MAKE_CLOSURE -> {
                val closureFn = getFunction(pi.makeClosure.fnId)
                assign(GoIRMakeClosureExpr(exprType(), closureFn, pi.makeClosure.bindingsList.map { ref(it) }))
            }
            ProtoInstruction.InstCase.MAKE_MAP -> assign(
                GoIRMakeMapExpr(exprType(), if (pi.makeMap.hasReserve) ref(pi.makeMap.reserve) else null)
            )
            ProtoInstruction.InstCase.MAKE_CHAN -> assign(
                GoIRMakeChanExpr(exprType(), ref(pi.makeChan.size))
            )
            ProtoInstruction.InstCase.MAKE_SLICE -> assign(
                GoIRMakeSliceExpr(exprType(), ref(pi.makeSlice.len), ref(pi.makeSlice.cap))
            )
            ProtoInstruction.InstCase.FIELD_ADDR -> assign(
                GoIRFieldAddrExpr(exprType(), ref(pi.fieldAddr.x), pi.fieldAddr.fieldIndex, pi.fieldAddr.fieldName)
            )
            ProtoInstruction.InstCase.FIELD -> assign(
                GoIRFieldExpr(exprType(), ref(pi.field.x), pi.field.fieldIndex, pi.field.fieldName)
            )
            ProtoInstruction.InstCase.INDEX_ADDR -> assign(
                GoIRIndexAddrExpr(exprType(), ref(pi.indexAddr.x), ref(pi.indexAddr.index))
            )
            ProtoInstruction.InstCase.INDEX_INST -> assign(
                GoIRIndexExpr(exprType(), ref(pi.indexInst.x), ref(pi.indexInst.index))
            )
            ProtoInstruction.InstCase.SLICE_INST -> assign(
                GoIRSliceExpr(
                    exprType(),
                    ref(pi.sliceInst.x),
                    if (pi.sliceInst.hasLow) ref(pi.sliceInst.low) else null,
                    if (pi.sliceInst.hasHigh) ref(pi.sliceInst.high) else null,
                    if (pi.sliceInst.hasMax) ref(pi.sliceInst.max) else null,
                )
            )
            ProtoInstruction.InstCase.LOOKUP -> assign(
                GoIRLookupExpr(exprType(), ref(pi.lookup.x), ref(pi.lookup.index), pi.lookup.commaOk)
            )
            ProtoInstruction.InstCase.TYPE_ASSERT -> assign(
                GoIRTypeAssertExpr(exprType(), ref(pi.typeAssert.x), type(pi.typeAssert.assertedTypeId), pi.typeAssert.commaOk)
            )
            ProtoInstruction.InstCase.RANGE_INST -> assign(
                GoIRRangeExpr(exprType(), ref(pi.rangeInst.x))
            )
            ProtoInstruction.InstCase.NEXT -> assign(
                GoIRNextExpr(exprType(), ref(pi.next.iter), pi.next.isString)
            )
            ProtoInstruction.InstCase.SELECT_INST -> assign(
                GoIRSelectExpr(
                    exprType(),
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
                GoIRExtractExpr(exprType(), ref(pi.extract.tuple), pi.extract.extractIndex)
            )
            ProtoInstruction.InstCase.PHI -> {
                val reg = valueMap[pi.valueId]
                val edges = linkedMapOf<GoIRInstRef, GoIRValue>()
                pi.phi.edgesList.forEachIndexed { edgeIndex, edge ->
                    check(edge.hasPredInstRef()) {
                        "Missing phi edge pred_inst_ref in function ${fn.fullName} at instruction ${pi.index}, edge $edgeIndex"
                    }
                    check(edge.hasValue()) {
                        "Missing phi edge value in function ${fn.fullName} at instruction ${pi.index}, edge $edgeIndex"
                    }
                    val predInstRef = translate(edge.predInstRef)
                    val value = if (isSyntheticLoadRef(edge.value)) {
                        phiEdgeSyntheticValues.getValue(PhiEdgeSyntheticLoadKey(pi.index, edgeIndex))
                    } else {
                        ref(edge.value)
                    }
                    check(edges.putIfAbsent(predInstRef, value) == null) {
                        "Duplicate phi edge pred_inst_ref ${predInstRef.index} in function ${fn.fullName} at instruction ${pi.index}"
                    }
                }
                GoIRPhi(loc, reg, edges, pi.phi.comment.ifEmpty { null })
            }
            ProtoInstruction.InstCase.CALL -> {
                val reg = valueMap[pi.valueId]
                GoIRCall(loc, reg, callInfoFromProto(pi.call.call, this))
            }
            ProtoInstruction.InstCase.JUMP -> {
                check(pi.jump.hasTarget()) {
                    "Missing jump target in function ${fn.fullName} at instruction ${pi.index}"
                }
                GoIRJump(loc, translateBranch(pi.jump.target))
            }
            ProtoInstruction.InstCase.IF_INST -> {
                check(pi.ifInst.hasTrueBranch()) {
                    "Missing if true_branch target in function ${fn.fullName} at instruction ${pi.index}"
                }
                check(pi.ifInst.hasFalseBranch()) {
                    "Missing if false_branch target in function ${fn.fullName} at instruction ${pi.index}"
                }
                GoIRIf(
                    loc,
                    ref(pi.ifInst.cond),
                    translateBranch(pi.ifInst.trueBranch),
                    translateBranch(pi.ifInst.falseBranch),
                )
            }
            ProtoInstruction.InstCase.RETURN_INST -> GoIRReturn(
                loc, pi.returnInst.resultsList.map { ref(it) }
            )
            ProtoInstruction.InstCase.PANIC_INST -> GoIRPanic(loc, ref(pi.panicInst.x))
            ProtoInstruction.InstCase.STORE -> {
                val addr = pi.store.addr
                when (addr.refCase) {
                    ProtoValueRef.RefCase.GLOBAL_ID -> GoIRGlobalStore(
                        loc,
                        resolveGlobal(addr.globalId),
                        ref(pi.store.`val`),
                    )
                    ProtoValueRef.RefCase.FUNCTION_ID,
                    ProtoValueRef.RefCase.BUILTIN_NAME -> throw IllegalStateException(
                        "Store with addr=${addr.refCase} is not valid Go SSA in ${fn.fullName} at inst ${pi.index}"
                    )
                    else -> GoIRStore(loc, ref(addr), ref(pi.store.`val`))
                }
            }
            ProtoInstruction.InstCase.MAP_UPDATE -> GoIRMapUpdate(
                loc, ref(pi.mapUpdate.map), ref(pi.mapUpdate.key), ref(pi.mapUpdate.value)
            )
            ProtoInstruction.InstCase.SEND -> GoIRSend(
                loc, ref(pi.send.chan), ref(pi.send.x)
            )
            ProtoInstruction.InstCase.GO_INST -> GoIRGo(
                loc, callInfoFromProto(pi.goInst.call, this)
            )
            ProtoInstruction.InstCase.DEFER_INST -> GoIRDefer(
                loc, callInfoFromProto(pi.deferInst.call, this)
            )
            ProtoInstruction.InstCase.RUN_DEFERS -> GoIRRunDefers(loc)
            ProtoInstruction.InstCase.DEBUG_REF -> GoIRDebugRef(
                loc, ref(pi.debugRef.x), pi.debugRef.isAddr
            )
            else -> throw IllegalStateException("Unknown instruction type: ${pi.instCase}")
        }.also {
            check(nextIndex == newIdx) {
                "Synth load count mismatch at ${fn.fullName} pi.index=${pi.index}: " +
                "expected nextIndex=$newIdx after refs, got ${nextIndex}. " +
                "countSyntheticLoads disagrees with valueRefFromProto."
            }
            nextIndex = newIdx + 1
        }
    }

    // ─── Value references ───────────────────────────────────────────

    private fun valueRefFromProto(vr: ProtoValueRef, ctx: InstContext): GoIRValue {
        val type = getType(vr.typeId)
        return when (vr.refCase) {
            ProtoValueRef.RefCase.INST_VALUE_ID ->
                ctx.valueMap[vr.instValueId]
            ProtoValueRef.RefCase.PARAM_INDEX ->
                GoIRParameterValue(type, ctx.fn.params[vr.paramIndex].name, vr.paramIndex)
            ProtoValueRef.RefCase.FREE_VAR_INDEX -> synthLoadFreeVar(vr.freeVarIndex, vr.typeId, ctx)
            ProtoValueRef.RefCase.CONST_VAL ->
                GoIRConstValue(type, constToName(vr.constVal), constValueFromProto(vr.constVal))
            ProtoValueRef.RefCase.GLOBAL_ID -> synthLoadGlobal(vr.globalId, vr.typeId, ctx)
            ProtoValueRef.RefCase.FUNCTION_ID -> synthLoadFunction(vr.functionId, vr.typeId, ctx)
            ProtoValueRef.RefCase.BUILTIN_NAME -> synthLoadBuiltin(vr.builtinName, vr.typeId, ctx)
            else -> throw IllegalStateException("Unknown value ref type: ${vr.refCase}")
        }
    }

    private fun callInfoFromProto(ci: ProtoCallInfo, ctx: InstContext): GoIRCallInfo {
        val target: GoIRCallTarget? = if (ci.hasFunction()) callTargetFromProto(ci.function, ctx) else null
        return GoIRCallInfo(
            mode = when (ci.mode) {
                ProtoCallMode.CALL_DIRECT -> GoIRCallMode.DIRECT
                ProtoCallMode.CALL_DYNAMIC -> GoIRCallMode.DYNAMIC
                ProtoCallMode.CALL_INVOKE -> GoIRCallMode.INVOKE
                else -> GoIRCallMode.DIRECT
            },
            target = target,
            receiver = if (ci.hasReceiver()) valueRefFromProto(ci.receiver, ctx) else null,
            methodName = ci.methodName.ifEmpty { null },
            args = ci.argsList.map { valueRefFromProto(it, ctx) },
            resultType = getType(ci.resultTypeId),
        )
    }

    private fun callTargetFromProto(vr: ProtoValueRef, ctx: InstContext): GoIRCallTarget {
        val type = getType(vr.typeId)
        return when (vr.refCase) {
            ProtoValueRef.RefCase.FUNCTION_ID ->
                GoIRCallTarget.Function(getFunction(vr.functionId))
            ProtoValueRef.RefCase.BUILTIN_NAME ->
                GoIRCallTarget.Builtin(vr.builtinName, type)
            ProtoValueRef.RefCase.GLOBAL_ID ->
                GoIRCallTarget.Dynamic(synthLoadGlobal(vr.globalId, vr.typeId, ctx))
            else ->
                GoIRCallTarget.Dynamic(valueRefFromProto(vr, ctx))
        }
    }

    private fun resolveGlobal(globalId: Int): GoIRGlobalImpl =
        globalsById.get(globalId)
            ?: error("Global was not found for $globalId")

    private fun synthLoadGlobal(globalId: Int, typeId: Int, ctx: InstContext): GoIRRegister {
        val idx = ctx.take()
        val (reg, inst) = createSyntheticLoadGlobal(globalId, typeId, ctx.body, ctx.blockIdx, idx)
        ctx.instructions.add(inst)
        return reg
    }

    private fun synthLoadFunction(functionId: Int, typeId: Int, ctx: InstContext): GoIRRegister {
        val idx = ctx.take()
        val (reg, inst) = createSyntheticLoadFunction(functionId, typeId, ctx.body, ctx.blockIdx, idx)
        ctx.instructions.add(inst)
        return reg
    }

    private fun synthLoadBuiltin(name: String, typeId: Int, ctx: InstContext): GoIRRegister {
        val idx = ctx.take()
        val (reg, inst) = createSyntheticLoadBuiltin(name, typeId, ctx.body, ctx.blockIdx, idx)
        ctx.instructions.add(inst)
        return reg
    }

    private fun synthLoadFreeVar(freeVarIndex: Int, typeId: Int, ctx: InstContext): GoIRRegister {
        val idx = ctx.take()
        val (reg, inst) = createSyntheticLoadFreeVar(freeVarIndex, typeId, ctx.body, ctx.blockIdx, idx)
        ctx.instructions.add(inst)
        return reg
    }

    private fun createSyntheticLoad(
        vr: ProtoValueRef,
        body: GoIRBodyImpl,
        blockIndex: Int,
        index: Int,
    ): Pair<GoIRRegister, GoIRAssignInst> = when (vr.refCase) {
        ProtoValueRef.RefCase.GLOBAL_ID -> createSyntheticLoadGlobal(vr.globalId, vr.typeId, body, blockIndex, index)
        ProtoValueRef.RefCase.FUNCTION_ID -> createSyntheticLoadFunction(vr.functionId, vr.typeId, body, blockIndex, index)
        ProtoValueRef.RefCase.BUILTIN_NAME -> createSyntheticLoadBuiltin(vr.builtinName, vr.typeId, body, blockIndex, index)
        ProtoValueRef.RefCase.FREE_VAR_INDEX -> createSyntheticLoadFreeVar(vr.freeVarIndex, vr.typeId, body, blockIndex, index)
        else -> error("${vr.refCase} is not a synthetic-load ref")
    }

    private fun createSyntheticLoadGlobal(
        globalId: Int,
        typeId: Int,
        body: GoIRBodyImpl,
        blockIndex: Int,
        index: Int,
    ): Pair<GoIRRegister, GoIRAssignInst> {
        val type = getType(typeId)
        val global = resolveGlobal(globalId)
        val reg = GoIRRegister(type, index, "_g$index")
        val expr = GoIRGlobalValueExpr(type, global)
        val loc = GoInstLocation(body, index, blockIndex, null)
        return reg to GoIRAssignInst(loc, reg, expr)
    }

    private fun createSyntheticLoadFunction(
        functionId: Int,
        typeId: Int,
        body: GoIRBodyImpl,
        blockIndex: Int,
        index: Int,
    ): Pair<GoIRRegister, GoIRAssignInst> {
        val type = getType(typeId)
        val func = getFunction(functionId)
        val reg = GoIRRegister(type, index, "_f$index")
        val expr = GoIRFunctionValueExpr(type, func)
        val loc = GoInstLocation(body, index, blockIndex, null)
        return reg to GoIRAssignInst(loc, reg, expr)
    }

    private fun createSyntheticLoadBuiltin(
        name: String,
        typeId: Int,
        body: GoIRBodyImpl,
        blockIndex: Int,
        index: Int,
    ): Pair<GoIRRegister, GoIRAssignInst> {
        val type = getType(typeId)
        val reg = GoIRRegister(type, index, "_b$index")
        val expr = GoIRBuiltinValueExpr(type, name)
        val loc = GoInstLocation(body, index, blockIndex, null)
        return reg to GoIRAssignInst(loc, reg, expr)
    }

    private fun createSyntheticLoadFreeVar(
        freeVarIndex: Int,
        typeId: Int,
        body: GoIRBodyImpl,
        blockIndex: Int,
        index: Int,
    ): Pair<GoIRRegister, GoIRAssignInst> {
        val type = getType(typeId)
        val freeVarName = body.function.freeVars[freeVarIndex].name
        val reg = GoIRRegister(type, index, "_v$index")
        val expr = GoIRFreeVarValueExpr(type, freeVarIndex, freeVarName)
        val loc = GoInstLocation(body, index, blockIndex, null)
        return reg to GoIRAssignInst(loc, reg, expr)
    }

    private fun isSyntheticLoadRef(vr: ProtoValueRef): Boolean = when (vr.refCase) {
        ProtoValueRef.RefCase.GLOBAL_ID,
        ProtoValueRef.RefCase.FUNCTION_ID,
        ProtoValueRef.RefCase.BUILTIN_NAME,
        ProtoValueRef.RefCase.FREE_VAR_INDEX -> true
        else -> false
    }

    private fun countSyntheticLoads(pi: ProtoInstruction): Int {
        var count = 0
        fun check(vr: ProtoValueRef) {
            if (isSyntheticLoadRef(vr)) count++
        }
        fun checkCall(ci: ProtoCallInfo) {
            if (ci.hasFunction()) {
                when (ci.function.refCase) {
                    ProtoValueRef.RefCase.GLOBAL_ID,
                    ProtoValueRef.RefCase.FREE_VAR_INDEX -> count++
                    ProtoValueRef.RefCase.FUNCTION_ID,
                    ProtoValueRef.RefCase.BUILTIN_NAME -> {}
                    else -> check(ci.function)
                }
            }
            if (ci.hasReceiver()) check(ci.receiver)
            ci.argsList.forEach { check(it) }
        }
        when (pi.instCase) {
            ProtoInstruction.InstCase.ALLOC -> {}
            ProtoInstruction.InstCase.BIN_OP -> { check(pi.binOp.x); check(pi.binOp.y) }
            ProtoInstruction.InstCase.UN_OP -> check(pi.unOp.x)
            ProtoInstruction.InstCase.CHANGE_TYPE -> check(pi.changeType.x)
            ProtoInstruction.InstCase.CONVERT -> check(pi.convert.x)
            ProtoInstruction.InstCase.MULTI_CONVERT -> check(pi.multiConvert.x)
            ProtoInstruction.InstCase.CHANGE_INTERFACE -> check(pi.changeInterface.x)
            ProtoInstruction.InstCase.SLICE_TO_ARRAY_POINTER -> check(pi.sliceToArrayPointer.x)
            ProtoInstruction.InstCase.MAKE_INTERFACE -> check(pi.makeInterface.x)
            ProtoInstruction.InstCase.MAKE_CLOSURE -> pi.makeClosure.bindingsList.forEach { check(it) }
            ProtoInstruction.InstCase.MAKE_MAP -> if (pi.makeMap.hasReserve) check(pi.makeMap.reserve)
            ProtoInstruction.InstCase.MAKE_CHAN -> check(pi.makeChan.size)
            ProtoInstruction.InstCase.MAKE_SLICE -> { check(pi.makeSlice.len); check(pi.makeSlice.cap) }
            ProtoInstruction.InstCase.FIELD_ADDR -> check(pi.fieldAddr.x)
            ProtoInstruction.InstCase.FIELD -> check(pi.field.x)
            ProtoInstruction.InstCase.INDEX_ADDR -> { check(pi.indexAddr.x); check(pi.indexAddr.index) }
            ProtoInstruction.InstCase.INDEX_INST -> { check(pi.indexInst.x); check(pi.indexInst.index) }
            ProtoInstruction.InstCase.SLICE_INST -> {
                check(pi.sliceInst.x)
                if (pi.sliceInst.hasLow) check(pi.sliceInst.low)
                if (pi.sliceInst.hasHigh) check(pi.sliceInst.high)
                if (pi.sliceInst.hasMax) check(pi.sliceInst.max)
            }
            ProtoInstruction.InstCase.LOOKUP -> { check(pi.lookup.x); check(pi.lookup.index) }
            ProtoInstruction.InstCase.TYPE_ASSERT -> check(pi.typeAssert.x)
            ProtoInstruction.InstCase.RANGE_INST -> check(pi.rangeInst.x)
            ProtoInstruction.InstCase.NEXT -> check(pi.next.iter)
            ProtoInstruction.InstCase.SELECT_INST -> pi.selectInst.statesList.forEach { st ->
                check(st.chan); if (st.hasSend) check(st.send)
            }
            ProtoInstruction.InstCase.EXTRACT -> check(pi.extract.tuple)
            ProtoInstruction.InstCase.PHI -> {}
            ProtoInstruction.InstCase.CALL -> checkCall(pi.call.call)
            ProtoInstruction.InstCase.GO_INST -> checkCall(pi.goInst.call)
            ProtoInstruction.InstCase.DEFER_INST -> checkCall(pi.deferInst.call)
            ProtoInstruction.InstCase.JUMP -> {}
            ProtoInstruction.InstCase.IF_INST -> check(pi.ifInst.cond)
            ProtoInstruction.InstCase.RETURN_INST -> pi.returnInst.resultsList.forEach { check(it) }
            ProtoInstruction.InstCase.PANIC_INST -> check(pi.panicInst.x)
            ProtoInstruction.InstCase.STORE -> {
                val addr = pi.store.addr
                when (addr.refCase) {
                    ProtoValueRef.RefCase.GLOBAL_ID,
                    ProtoValueRef.RefCase.FUNCTION_ID,
                    ProtoValueRef.RefCase.BUILTIN_NAME -> {}
                    else -> check(addr)
                }
                check(pi.store.`val`)
            }
            ProtoInstruction.InstCase.MAP_UPDATE -> { check(pi.mapUpdate.map); check(pi.mapUpdate.key); check(pi.mapUpdate.value) }
            ProtoInstruction.InstCase.SEND -> { check(pi.send.chan); check(pi.send.x) }
            ProtoInstruction.InstCase.RUN_DEFERS -> {}
            ProtoInstruction.InstCase.DEBUG_REF -> check(pi.debugRef.x)
            else -> {}
        }
        return count
    }

    private fun collectPhiEdgeSyntheticLoads(
        fb: ProtoFunctionBody,
        fnFullName: String,
    ): List<PhiEdgeSyntheticLoad> {
        val loads = mutableListOf<PhiEdgeSyntheticLoad>()
        for (pb in fb.blocksList) {
            for (pi in pb.instructionsList) {
                loads += phiSyntheticEdgeRefs(pi, fnFullName)
            }
        }
        return loads
    }

    private fun phiSyntheticEdgeRefs(
        pi: ProtoInstruction,
        fnFullName: String,
    ): List<PhiEdgeSyntheticLoad> {
        if (pi.instCase != ProtoInstruction.InstCase.PHI) return emptyList()
        return pi.phi.edgesList.mapIndexedNotNull { edgeIndex, edge ->
            if (!edge.hasValue() || !isSyntheticLoadRef(edge.value)) return@mapIndexedNotNull null
            check(edge.hasPredInstRef()) {
                "Missing phi edge pred_inst_ref in function $fnFullName at instruction ${pi.index}, edge $edgeIndex"
            }
            PhiEdgeSyntheticLoad(
                key = PhiEdgeSyntheticLoadKey(pi.index, edgeIndex),
                predInstProtoIndex = edge.predInstRef,
                value = edge.value,
                index = -1,
            )
        }
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

class ValueMap {
    private val registers = Int2ObjectOpenHashMap<GoIRRegister>()

    fun register(id: Int, reg: GoIRRegister) {
        registers.put(id, reg)
    }

    operator fun get(id: Int): GoIRRegister {
        return registers.get(id) ?: error("Register for value $id not registered")
    }
}

private data class PhiEdgeSyntheticLoadKey(
    val phiProtoIndex: Int,
    val edgeIndex: Int,
)

private data class PhiEdgeSyntheticLoad(
    val key: PhiEdgeSyntheticLoadKey,
    val predInstProtoIndex: Int,
    val value: ProtoValueRef,
    var index: Int,
)

private class InstContext(
    val body: GoIRBodyImpl,
    var blockIdx: Int,
    val fn: GoIRFunctionImpl,
    val valueMap: ValueMap,
    var instructions: MutableList<GoIRInst>,
    val protoToNew: Int2IntOpenHashMap,
    val blockStartProtoToNew: Int2IntOpenHashMap,
    val phiEdgeSyntheticValues: Map<PhiEdgeSyntheticLoadKey, GoIRRegister>,
) {
    var nextIndex: Int = 0
    fun take(): Int = nextIndex++
}

private inline fun <T> Array<T?>.forEachNonNull(body: (T) -> Unit) {
    for (it in this) {
        it?.apply(body)
    }
}

private inline fun <T, R> List<T>.mapNonEmpty(transform: (T) -> R): List<R> =
    if (isEmpty()) emptyList() else map(transform)
