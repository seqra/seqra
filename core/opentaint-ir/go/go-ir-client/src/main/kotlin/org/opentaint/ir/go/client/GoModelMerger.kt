package org.opentaint.ir.go.client

import com.google.protobuf.Descriptors.FieldDescriptor
import com.google.protobuf.Message
import org.opentaint.ir.go.proto.ProtoConst
import org.opentaint.ir.go.proto.ProtoFieldDecl
import org.opentaint.ir.go.proto.ProtoFunction
import org.opentaint.ir.go.proto.ProtoFunctionBody
import org.opentaint.ir.go.proto.ProtoGlobal
import org.opentaint.ir.go.proto.ProtoModelProgram
import org.opentaint.ir.go.proto.ProtoNamedType
import org.opentaint.ir.go.proto.ProtoNamedTypeKind
import org.opentaint.ir.go.proto.ProtoPackage
import org.opentaint.ir.go.proto.ProtoProgram
import org.opentaint.ir.go.proto.ProtoStructField
import org.opentaint.ir.go.proto.ProtoStructType
import org.opentaint.ir.go.proto.ProtoTypeDefinition

internal class GoModelMerger {
    fun merge(base: ProtoProgram, models: List<ProtoModelProgram>): ProtoProgram {
        val state = ModelMergeState()
        return models.fold(base) { program, model ->
            mergeModel(program, model.program, state, model.source)
        }
    }

    private fun mergeModel(
        base: ProtoProgram,
        rawModel: ProtoProgram,
        state: ModelMergeState,
        source: String,
    ): ProtoProgram {
        val normalizer = try {
            ModelPathNormalizer.create(rawModel)
        } catch (error: IllegalArgumentException) {
            throw IllegalArgumentException("invalid Go model $source: ${error.message}", error)
        }
        normalizer.replacements.values.forEach { target ->
            val previous = state.modeledPackages.putIfAbsent(target, source)
            require(previous == null) {
                "Go package \"$target\" is modeled more than once ($previous and $source)"
            }
        }

        var model = rawModel
        var baseIndex = ProtoIndex(base)
        var modelIndex = ProtoIndex(model, normalizer)
        val fieldPlans = invalidModel(source) {
            planModelStructFields(baseIndex, modelIndex, normalizer)
        }
        model = applyModelStructFieldPlans(model, fieldPlans)
        modelIndex = ProtoIndex(model, normalizer)
        model = invalidModel(source) {
            remapModelStructFieldAccesses(model, modelIndex, fieldPlans)
        }
        modelIndex = ProtoIndex(model, normalizer)

        val ownedOldPackages = model.packagesList
            .filter { it.importPath in normalizer.replacements }
            .mapTo(mutableSetOf()) { it.id }
        val importedTargetPackages = model.packagesList
            .filter { it.id !in ownedOldPackages && it.importPath in normalizer.targets }
            .mapTo(mutableSetOf()) { it.id }
        val importedTargetFunctions = modelIndex.functions.values
            .filter { it.packageId in importedTargetPackages }
            .mapTo(mutableSetOf()) { it.id }
        val initOldFunctions = collectInitializerFunctions(model, ownedOldPackages)

        val basePackages = baseIndex.reversePackageKeys()
        model.packagesList.forEach { modelPackage ->
            val target = normalizer.replacements[modelPackage.importPath] ?: return@forEach
            val targetPackage = basePackages[target]?.let(baseIndex.packages::get) ?: return@forEach
            require(modelPackage.name == targetPackage.name) {
                "Go model package \"${modelPackage.importPath}\" has package name " +
                    "\"${modelPackage.name}\", want \"${targetPackage.name}\" " +
                    "(the modeled package name)"
            }
        }

        val counters = IdCounters.forProgram(base)
        val modelFunctionKeys = modelIndex.functionKeysById().toMutableMap()
        modelIndex.functions.forEach { (id, function) ->
            if (function.parentFunctionId != 0) {
                modelFunctionKeys[id] = modelFunctionKeys.getValue(id) +
                    "#opentaint-model-anonymous:$id"
            }
        }
        val remap = IdRemap(
            types = assignIds(modelIndex.typeKeysById(), baseIndex.reverseTypeKeys(), counters::nextType),
            packages = assignIds(
                modelIndex.packageKeys,
                baseIndex.reversePackageKeys(),
                counters::nextPackage,
            ),
            functions = assignIds(
                modelFunctionKeys,
                baseIndex.reverseFunctionKeys(),
                counters::nextFunction,
            ),
            named = assignIds(modelIndex.namedKeys, baseIndex.reverseNamedKeys(), counters::nextNamed),
            globals = assignIds(
                modelIndex.globalKeysById(),
                baseIndex.reverseGlobalKeys(),
                counters::nextGlobal,
            ),
            consts = assignIds(
                modelIndex.constKeysById(),
                baseIndex.reverseConstKeys(),
                counters::nextConst,
            ),
        )

        validateFunctionSignatures(
            baseIndex,
            modelIndex,
            normalizer,
            modelFunctionKeys,
            initOldFunctions,
        )

        model = normalizeModel(model, normalizer, ownedOldPackages)
        model = model.toBuilder()
            .clearFunctionBodies()
            .addAllFunctionBodies(
                model.functionBodiesList.filterNot { it.functionId in importedTargetFunctions },
            )
            .clearPackages()
            .addAllPackages(model.packagesList.map { pkg ->
                if (pkg.id !in importedTargetPackages) {
                    pkg
                } else {
                    pkg.toBuilder()
                        .setInitFunctionId(0)
                        .clearFunctions()
                        .clearGlobals()
                        .clearConstants()
                        .clearNamedTypes()
                        .addAllNamedTypes(pkg.namedTypesList.map { named ->
                            named.toBuilder()
                                .clearMethodIds()
                                .clearPointerMethodIds()
                                .build()
                        })
                        .build()
                }
            })
            .build()
        val ownedPackages = ownedOldPackages.mapTo(mutableSetOf()) { remap.packages.getValue(it) }
        val initFunctions = initOldFunctions.mapTo(mutableSetOf()) { remap.functions.getValue(it) }
        model = try {
            remapMessageIds(model, remap) as ProtoProgram
        } catch (error: IllegalArgumentException) {
            throw IllegalArgumentException("merging Go model $source: ${error.message}", error)
        }

        var result = mergeModelStructFields(base, model, fieldPlans, remap, counters, source)
        baseIndex = ProtoIndex(result)

        val missingTypes = model.typesList.filter { it.id !in baseIndex.types }
        if (missingTypes.isNotEmpty()) {
            result = result.toBuilder().addAllTypes(missingTypes).build()
        }

        result = mergePackages(result, model, ownedPackages, initFunctions)
        baseIndex = ProtoIndex(result)
        result = mergeFunctionBodies(result, model, baseIndex, ownedPackages, initFunctions, state, source)
        result = mergeGenericInstanceBodies(result, ownedPackages, state, source, counters)
        validateReferences(result, source)
        return result
    }

    private inline fun <T> invalidModel(source: String, action: () -> T): T {
        return try {
            action()
        } catch (error: IllegalArgumentException) {
            throw IllegalArgumentException("invalid Go model $source: ${error.message}", error)
        }
    }

    private fun collectInitializerFunctions(
        model: ProtoProgram,
        ownedPackages: Set<Int>,
    ): MutableSet<Int> {
        val functions = model.packagesList
            .filter { it.id in ownedPackages }
            .flatMap { it.functionsList }
        val result = functions
            .filter { it.name == "init" || it.name.startsWith("init#") }
            .mapTo(mutableSetOf()) { it.id }

        do {
            val size = result.size
            functions.forEach { function ->
                if (function.parentFunctionId in result) {
                    result += function.id
                }
            }
        } while (result.size != size)
        return result
    }

    private fun validateFunctionSignatures(
        baseIndex: ProtoIndex,
        modelIndex: ProtoIndex,
        normalizer: ModelPathNormalizer,
        modelFunctionKeys: Map<Int, String>,
        initFunctions: Set<Int>,
    ) {
        val baseFunctions = baseIndex.reverseFunctionKeys()
        modelFunctionKeys.forEach { (modelId, key) ->
            val baseId = baseFunctions[key] ?: return@forEach
            val modelFunction = modelIndex.functions[modelId] ?: return@forEach
            if (
                modelFunction.parentFunctionId != 0 ||
                modelId in initFunctions ||
                !modelIndex.isOwnedPackage(normalizer, modelFunction.packageId)
            ) {
                return@forEach
            }
            val baseFunction = baseIndex.functions.getValue(baseId)
            require(
                modelIndex.typeKey(modelFunction.signatureTypeId) ==
                    baseIndex.typeKey(baseFunction.signatureTypeId) &&
                    modelFunction.isMethod == baseFunction.isMethod &&
                    modelFunction.isPointerReceiver == baseFunction.isPointerReceiver,
            ) {
                "Go model function $key has a different signature from its target"
            }
        }
    }

    private fun normalizeModel(
        model: ProtoProgram,
        normalizer: ModelPathNormalizer,
        ownedPackages: Set<Int>,
    ): ProtoProgram {
        val packages = model.packagesList.map { pkg ->
            val builder = pkg.toBuilder()
            if (pkg.id in ownedPackages) {
                builder
                    .setInitFunctionId(0)
                    .setImportPath(normalizer.path(pkg.importPath))
                    .setIsDependency(true)
                    .setIsStdlib(false)
            }
            builder
                .clearNamedTypes()
                .addAllNamedTypes(pkg.namedTypesList.map { named ->
                    named.toBuilder().setFullName(normalizer.symbol(named.fullName)).build()
                })
                .clearFunctions()
                .addAllFunctions(pkg.functionsList.map { function ->
                    function.toBuilder().setFullName(normalizer.symbol(function.fullName)).build()
                })
                .clearGlobals()
                .addAllGlobals(pkg.globalsList.map { global ->
                    global.toBuilder().setFullName(normalizer.symbol(global.fullName)).build()
                })
                .clearConstants()
                .addAllConstants(pkg.constantsList.map { constant ->
                    constant.toBuilder().setFullName(normalizer.symbol(constant.fullName)).build()
                })
                .build()
        }
        return model.toBuilder().clearPackages().addAllPackages(packages).build()
    }

    private fun mergeModelStructFields(
        base: ProtoProgram,
        model: ProtoProgram,
        plans: Map<Int, ModelStructFieldPlan>,
        remap: IdRemap,
        counters: IdCounters,
        source: String,
    ): ProtoProgram {
        var types = base.typesList
        var packages = base.packagesList
        val modelIndex = ProtoIndex(model)

        plans.toSortedMap().forEach { (modelNamedId, plan) ->
            if (plan.extraFieldNames.isEmpty()) {
                return@forEach
            }
            val remappedNamedId = remap.named.getValue(modelNamedId)
            val modelNamed = modelIndex.named[remappedNamedId]
                ?: throw IllegalArgumentException(
                    "merging Go model $source: model named type $remappedNamedId is missing",
                )
            val extraFields = modelNamed.fieldsList.filter { it.name in plan.extraFieldNames }
            val baseIndex = ProtoIndex(
                base.toBuilder().clearTypes().addAllTypes(types).clearPackages().addAllPackages(packages).build(),
            )
            val targetNamed = baseIndex.named[plan.targetNamedId]
                ?: throw IllegalArgumentException(
                    "merging Go model $source: Go model target named type " +
                        "${plan.targetNamedId} is missing",
                )
            val underlying = baseIndex.types[targetNamed.underlyingTypeId]
                ?.takeIf { it.typeCase == ProtoTypeDefinition.TypeCase.STRUCT_TYPE }
                ?.structType
                ?: throw IllegalArgumentException(
                    "merging Go model $source: Go model target type " +
                        "${targetNamed.fullName} has no struct body",
                )

            val combinedFields = underlying.fieldsList.toMutableList()
            combinedFields += extraFields.map(::fieldDeclarationToStructField)
            val combined = ProtoTypeDefinition.newBuilder()
                .setId(counters.nextType())
                .setStructType(ProtoStructType.newBuilder().addAllFields(combinedFields))
                .build()
            types = types + combined
            packages = packages.map { pkg ->
                if (pkg.namedTypesList.none { it.id == targetNamed.id }) {
                    pkg
                } else {
                    pkg.toBuilder()
                        .clearNamedTypes()
                        .addAllNamedTypes(pkg.namedTypesList.map { named ->
                            if (named.id != targetNamed.id) {
                                named
                            } else {
                                named.toBuilder()
                                    .addAllFields(extraFields)
                                    .setUnderlyingTypeId(combined.id)
                                    .build()
                            }
                        })
                        .build()
                }
            }
        }
        return base.toBuilder().clearTypes().addAllTypes(types).clearPackages().addAllPackages(packages).build()
    }

    private fun mergePackages(
        base: ProtoProgram,
        model: ProtoProgram,
        ownedPackages: Set<Int>,
        initFunctions: Set<Int>,
    ): ProtoProgram {
        val packages = base.packagesList.toMutableList()
        val packageIndices = packages.mapIndexed { index, pkg -> pkg.id to index }.toMap().toMutableMap()

        model.packagesList.forEach { modelPackage ->
            val basePosition = packageIndices[modelPackage.id]
            if (basePosition == null) {
                val added = if (modelPackage.id in ownedPackages) {
                    modelPackage.toBuilder()
                        .clearFunctions()
                        .addAllFunctions(
                            modelPackage.functionsList
                                .filterNot { it.id in initFunctions }
                                .map(::asModelSupport),
                        )
                        .build()
                } else {
                    modelPackage
                }
                packageIndices[added.id] = packages.size
                packages += added
                return@forEach
            }

            val basePackage = packages[basePosition]
            val named = basePackage.namedTypesList.toMutableList()
            val namedIndices = named.mapIndexed { index, value -> value.id to index }.toMap().toMutableMap()
            modelPackage.namedTypesList.forEach { modelNamed ->
                val position = namedIndices[modelNamed.id]
                if (position == null) {
                    namedIndices[modelNamed.id] = named.size
                    named += modelNamed
                } else {
                    val current = named[position]
                    named[position] = current.toBuilder()
                        .clearMethodIds()
                        .addAllMethodIds(appendUniqueIds(current.methodIdsList, modelNamed.methodIdsList))
                        .clearPointerMethodIds()
                        .addAllPointerMethodIds(
                            appendUniqueIds(
                                current.pointerMethodIdsList,
                                modelNamed.pointerMethodIdsList,
                            ),
                        )
                        .build()
                }
            }

            val functions = basePackage.functionsList.toMutableList()
            val functionIndices = functions
                .mapIndexed { index, value -> value.id to index }
                .toMap()
                .toMutableMap()
            modelPackage.functionsList.forEach { modelFunction ->
                if (modelFunction.id in initFunctions) {
                    return@forEach
                }
                val position = functionIndices[modelFunction.id]
                if (position == null) {
                    functionIndices[modelFunction.id] = functions.size
                    functions += if (modelPackage.id in ownedPackages) {
                        asModelSupport(modelFunction)
                    } else {
                        modelFunction
                    }
                } else if (modelPackage.id in ownedPackages) {
                    functions[position] = functions[position].toBuilder()
                        .clearAnonFunctionIds()
                        .addAllAnonFunctionIds(modelFunction.anonFunctionIdsList)
                        .build()
                }
            }

            packages[basePosition] = basePackage.toBuilder()
                .clearImportIds()
                .addAllImportIds(appendUniqueIds(basePackage.importIdsList, modelPackage.importIdsList))
                .clearNamedTypes()
                .addAllNamedTypes(named)
                .clearFunctions()
                .addAllFunctions(functions)
                .clearGlobals()
                .addAllGlobals(mergeById(basePackage.globalsList, modelPackage.globalsList) { it.id })
                .clearConstants()
                .addAllConstants(mergeById(basePackage.constantsList, modelPackage.constantsList) { it.id })
                .build()
        }

        return base.toBuilder().clearPackages().addAllPackages(packages).build()
    }

    private fun mergeFunctionBodies(
        base: ProtoProgram,
        model: ProtoProgram,
        baseIndex: ProtoIndex,
        ownedPackages: Set<Int>,
        initFunctions: Set<Int>,
        state: ModelMergeState,
        source: String,
    ): ProtoProgram {
        val bodies = base.functionBodiesList.toMutableList()
        val bodyIndices = bodies.mapIndexed { index, body -> body.functionId to index }.toMap().toMutableMap()
        val functionsWithBodies = mutableSetOf<Int>()

        model.functionBodiesList.forEach { body ->
            if (body.functionId in initFunctions) {
                return@forEach
            }
            val function = baseIndex.functions[body.functionId] ?: return@forEach
            if (function.packageId !in ownedPackages) {
                return@forEach
            }
            val previous = state.modeledFunctions.putIfAbsent(body.functionId, source)
            require(previous == null) {
                "Go function \"${function.fullName}\" is modeled more than once ($previous and $source)"
            }
            val position = bodyIndices[body.functionId]
            if (position == null) {
                bodyIndices[body.functionId] = bodies.size
                bodies += body
            } else {
                bodies[position] = body
            }
            functionsWithBodies += body.functionId
        }

        val packages = base.packagesList.map { pkg ->
            if (pkg.functionsList.none { it.id in functionsWithBodies }) {
                pkg
            } else {
                pkg.toBuilder()
                    .clearFunctions()
                    .addAllFunctions(pkg.functionsList.map { function ->
                        if (function.id in functionsWithBodies) {
                            function.toBuilder()
                                .setHasBody(true)
                                .setIsSynthetic(true)
                                .setSyntheticKind(
                                    if (function.syntheticKind == MODEL_SUPPORT_KIND) {
                                        MODEL_SUPPORT_KIND
                                    } else {
                                        MODEL_KIND
                                    },
                                )
                                .build()
                        } else {
                            function
                        }
                    })
                    .build()
            }
        }
        return base.toBuilder()
            .clearPackages()
            .addAllPackages(packages)
            .clearFunctionBodies()
            .addAllFunctionBodies(bodies)
            .build()
    }

    private fun mergeGenericInstanceBodies(
        program: ProtoProgram,
        ownedPackages: Set<Int>,
        state: ModelMergeState,
        source: String,
        counters: IdCounters,
    ): ProtoProgram {
        val index = ProtoIndex(program)
        val genericOriginIds = index.functions.values
            .mapNotNullTo(mutableSetOf()) { it.originFunctionId.takeIf { id -> id != 0 } }
        val modeledGenericBodies = program.functionBodiesList.mapNotNull { body ->
            val function = index.functions[body.functionId] ?: return@mapNotNull null
            if (
                function.packageId !in ownedPackages ||
                function.id !in genericOriginIds ||
                state.modeledFunctions[function.id] != source ||
                function.syntheticKind != MODEL_KIND
            ) {
                return@mapNotNull null
            }
            function.id to ModeledGenericBody(function, body)
        }.toMap()
        if (modeledGenericBodies.isEmpty()) {
            return program
        }

        val bodies = program.functionBodiesList.toMutableList()
        val bodyIndices = bodies.mapIndexed { position, body -> body.functionId to position }
            .toMap()
            .toMutableMap()
        val bodiesByFunction = program.functionBodiesList.associateBy { it.functionId }
        val modeledInstances = mutableSetOf<Int>()
        val instanceAnonymousFunctions = mutableMapOf<Int, List<Int>>()
        val addedFunctions = mutableListOf<ProtoFunction>()
        val types = program.typesList.toMutableList()
        index.functions.values.forEach { function ->
            val modeled = modeledGenericBodies[function.originFunctionId] ?: return@forEach
            val previous = state.modeledFunctions.putIfAbsent(function.id, source)
            require(previous == null) {
                "Go function \"${function.fullName}\" is modeled more than once ($previous and $source)"
            }
            val typeArgumentsByIndex = modeled.function.typeParamsList.associate { typeParam ->
                require(typeParam.index in function.typeArgIdsList.indices) {
                    "Go generic instance ${function.fullName} has no type argument for " +
                        "parameter ${typeParam.name}[${typeParam.index}]"
                }
                typeParam.index to function.typeArgIdsList[typeParam.index]
            }
            val specializer = GenericTypeSpecializer(index.types, typeArgumentsByIndex, counters)
            val anonymousIds = collectAnonymousFunctionIds(modeled.function, index.functions)
            val functionIds = anonymousIds.associateWith { counters.nextFunction() }.toMutableMap()
            functionIds[modeled.function.id] = function.id
            val functionRemap = { id: Int -> functionIds[id] ?: id }
            val body = specializer.specialize(modeled.body, functionRemap)
            anonymousIds.forEach { anonymousId ->
                val original = index.functions.getValue(anonymousId)
                val suffix = function.name.removePrefix(modeled.function.name)
                    .ifEmpty { "#${function.id}" }
                val specializedFunction = specializer.specialize(original, functionRemap)
                    .toBuilder()
                    .setName(original.name + suffix)
                    .setFullName(original.fullName + suffix)
                    .setIsSynthetic(true)
                    .setSyntheticKind(MODEL_SUPPORT_KIND)
                    .build()
                addedFunctions += specializedFunction

                val originalBody = bodiesByFunction[anonymousId]
                require(originalBody != null) {
                    "Go model anonymous function ${original.fullName} has no body"
                }
                val specializedBody = specializer.specialize(originalBody, functionRemap)
                val anonymousBodyPosition = bodyIndices[specializedBody.functionId]
                if (anonymousBodyPosition == null) {
                    bodyIndices[specializedBody.functionId] = bodies.size
                    bodies += specializedBody
                } else {
                    bodies[anonymousBodyPosition] = specializedBody
                }
            }
            instanceAnonymousFunctions[function.id] = modeled.function.anonFunctionIdsList.map(functionRemap)
            types += specializer.addedTypes
            val position = bodyIndices[function.id]
            if (position == null) {
                bodyIndices[function.id] = bodies.size
                bodies += body
            } else {
                bodies[position] = body
            }
            modeledInstances += function.id
        }
        if (modeledInstances.isEmpty()) {
            return program
        }

        val packages = program.packagesList.map { pkg ->
            val packageAdditions = addedFunctions.filter { it.packageId == pkg.id }
            if (pkg.functionsList.none { it.id in modeledInstances } && packageAdditions.isEmpty()) {
                pkg
            } else {
                pkg.toBuilder()
                    .clearFunctions()
                    .addAllFunctions(pkg.functionsList.map { function ->
                        if (function.id !in modeledInstances) {
                            function
                        } else {
                            function.toBuilder()
                                .setHasBody(true)
                                .setIsSynthetic(true)
                                .setSyntheticKind(MODEL_KIND)
                                .clearAnonFunctionIds()
                                .addAllAnonFunctionIds(instanceAnonymousFunctions.getValue(function.id))
                                .build()
                        }
                    })
                    .addAllFunctions(packageAdditions)
                    .build()
            }
        }
        return program.toBuilder()
            .clearTypes()
            .addAllTypes(types)
            .clearPackages()
            .addAllPackages(packages)
            .clearFunctionBodies()
            .addAllFunctionBodies(bodies)
            .build()
    }

    private fun asModelSupport(function: ProtoFunction): ProtoFunction {
        return function.toBuilder()
            .setIsSynthetic(true)
            .setSyntheticKind(MODEL_SUPPORT_KIND)
            .build()
    }

    private data class ModeledGenericBody(
        val function: ProtoFunction,
        val body: ProtoFunctionBody,
    )

    private fun collectAnonymousFunctionIds(
        root: ProtoFunction,
        functions: Map<Int, ProtoFunction>,
    ): List<Int> {
        val result = linkedSetOf<Int>()
        val pending = ArrayDeque(root.anonFunctionIdsList)
        while (pending.isNotEmpty()) {
            val id = pending.removeFirst()
            if (!result.add(id)) {
                continue
            }
            val function = functions[id]
                ?: throw IllegalArgumentException("Go model anonymous function $id is missing")
            pending.addAll(function.anonFunctionIdsList)
        }
        return result.toList()
    }
}

private class ModelMergeState {
    val modeledPackages = mutableMapOf<String, String>()
    val modeledFunctions = mutableMapOf<Int, String>()
}

private class GenericTypeSpecializer(
    private val definitions: Map<Int, ProtoTypeDefinition>,
    private val typeArgumentsByIndex: Map<Int, Int>,
    private val counters: IdCounters,
) {
    private val specialized = mutableMapOf<Int, Int>()
    private val active = mutableSetOf<Int>()
    val addedTypes = mutableListOf<ProtoTypeDefinition>()

    fun specialize(body: ProtoFunctionBody, remapFunction: (Int) -> Int): ProtoFunctionBody =
        remapMessageIdsOfClass(
            remapMessageIdsOfClass(body, MapName.TYPE, ::specializeType),
            MapName.FUNCTION,
            remapFunction,
        ) as ProtoFunctionBody

    fun specialize(function: ProtoFunction, remapFunction: (Int) -> Int): ProtoFunction =
        remapMessageIdsOfClass(
            remapMessageIdsOfClass(function, MapName.TYPE, ::specializeType),
            MapName.FUNCTION,
            remapFunction,
        ) as ProtoFunction

    private fun specializeType(id: Int): Int {
        if (id == 0) {
            return 0
        }
        specialized[id]?.let { return it }
        val definition = definitions[id]
            ?: throw IllegalArgumentException("Go model type $id is missing during generic specialization")
        if (definition.typeCase == ProtoTypeDefinition.TypeCase.TYPE_PARAM) {
            typeArgumentsByIndex[definition.typeParam.index]?.let { typeArgument ->
                specialized[id] = typeArgument
                return typeArgument
            }
        }
        require(active.add(id)) { "recursive Go model type $id cannot be specialized" }
        val rewritten = rewrite(definition)
        active -= id
        if (rewritten == definition) {
            specialized[id] = id
            return id
        }
        val result = rewritten.toBuilder().setId(counters.nextType()).build()
        addedTypes += result
        specialized[id] = result.id
        return result.id
    }

    private fun rewrite(definition: ProtoTypeDefinition): ProtoTypeDefinition {
        val builder = definition.toBuilder()
        when (definition.typeCase) {
            ProtoTypeDefinition.TypeCase.POINTER -> builder.setPointer(
                definition.pointer.toBuilder().setElemTypeId(specializeType(definition.pointer.elemTypeId)),
            )
            ProtoTypeDefinition.TypeCase.ARRAY -> builder.setArray(
                definition.array.toBuilder().setElemTypeId(specializeType(definition.array.elemTypeId)),
            )
            ProtoTypeDefinition.TypeCase.SLICE -> builder.setSlice(
                definition.slice.toBuilder().setElemTypeId(specializeType(definition.slice.elemTypeId)),
            )
            ProtoTypeDefinition.TypeCase.MAP_TYPE -> builder.setMapType(
                definition.mapType.toBuilder()
                    .setKeyTypeId(specializeType(definition.mapType.keyTypeId))
                    .setValueTypeId(specializeType(definition.mapType.valueTypeId)),
            )
            ProtoTypeDefinition.TypeCase.CHAN_TYPE -> builder.setChanType(
                definition.chanType.toBuilder().setElemTypeId(specializeType(definition.chanType.elemTypeId)),
            )
            ProtoTypeDefinition.TypeCase.STRUCT_TYPE -> builder.setStructType(
                definition.structType.toBuilder()
                    .clearFields()
                    .addAllFields(definition.structType.fieldsList.map { field ->
                        field.toBuilder().setTypeId(specializeType(field.typeId)).build()
                    }),
            )
            ProtoTypeDefinition.TypeCase.INTERFACE_TYPE -> builder.setInterfaceType(
                definition.interfaceType.toBuilder()
                    .clearMethods()
                    .addAllMethods(definition.interfaceType.methodsList.map { method ->
                        method.toBuilder()
                            .setSignatureTypeId(specializeType(method.signatureTypeId))
                            .build()
                    })
                    .clearEmbedTypeIds()
                    .addAllEmbedTypeIds(definition.interfaceType.embedTypeIdsList.map(::specializeType)),
            )
            ProtoTypeDefinition.TypeCase.FUNC_TYPE -> builder.setFuncType(
                definition.funcType.toBuilder()
                    .clearParamTypeIds()
                    .addAllParamTypeIds(definition.funcType.paramTypeIdsList.map(::specializeType))
                    .clearResultTypeIds()
                    .addAllResultTypeIds(definition.funcType.resultTypeIdsList.map(::specializeType))
                    .setRecvTypeId(specializeType(definition.funcType.recvTypeId)),
            )
            ProtoTypeDefinition.TypeCase.NAMED_REF -> builder.setNamedRef(
                definition.namedRef.toBuilder()
                    .clearTypeArgIds()
                    .addAllTypeArgIds(definition.namedRef.typeArgIdsList.map(::specializeType)),
            )
            ProtoTypeDefinition.TypeCase.TYPE_PARAM -> {}
            ProtoTypeDefinition.TypeCase.TUPLE -> builder.setTuple(
                definition.tuple.toBuilder()
                    .clearElementTypeIds()
                    .addAllElementTypeIds(definition.tuple.elementTypeIdsList.map(::specializeType)),
            )
            ProtoTypeDefinition.TypeCase.BASIC,
            ProtoTypeDefinition.TypeCase.UNSAFE_POINTER,
            ProtoTypeDefinition.TypeCase.TYPE_NOT_SET,
            -> {}
        }
        return builder.build()
    }
}

private data class ModelPathNormalizer(
    val replacements: Map<String, String>,
    val targets: Set<String>,
    private val orderedModelPaths: List<String>,
) {
    fun path(value: String): String = replacements[value] ?: value

    fun symbol(value: String): String {
        return orderedModelPaths.fold(value) { result, modelPath ->
            result.replace(modelPath, replacements.getValue(modelPath))
        }
    }

    companion object {
        fun create(program: ProtoProgram): ModelPathNormalizer {
            val replacements = program.packagesList
                .filterNot { it.isDependency || it.isStdlib || it.importPath == UNIVERSE_PACKAGE_PATH }
                .associate { pkg ->
                    require(pkg.importPath.startsWith(GO_MODEL_PREFIX)) {
                        "Go model package \"${pkg.importPath}\" is not model-addressed, " +
                            "its import path must be ${GO_MODEL_PREFIX}<target-import-path>"
                    }
                    val target = pkg.importPath.removePrefix(GO_MODEL_PREFIX)
                    require(target.isNotEmpty()) {
                        "Go model package \"${pkg.importPath}\" has an empty target package"
                    }
                    pkg.importPath to target
                }
            require(replacements.isNotEmpty()) {
                "Go model contains no model-addressed packages, expected at least one package " +
                    "under $GO_MODEL_PREFIX"
            }
            return ModelPathNormalizer(
                replacements,
                replacements.values.toSet(),
                replacements.keys.sortedByDescending(String::length),
            )
        }
    }
}

private class ProtoIndex(
    val program: ProtoProgram,
    private val normalizer: ModelPathNormalizer? = null,
) {
    val packages = program.packagesList.associateBy { it.id }
    val types = program.typesList.associateBy { it.id }
    val named = program.packagesList.flatMap { it.namedTypesList }.associateBy { it.id }
    val functions = program.packagesList.flatMap { it.functionsList }.associateBy { it.id }
    val globals = program.packagesList.flatMap { it.globalsList }.associateBy { it.id }
    val consts = program.packagesList.flatMap { it.constantsList }.associateBy { it.id }
    val packageKeys = program.packagesList.associate { it.id to path(it.importPath) }
    val namedKeys = program.packagesList.flatMap { pkg ->
        pkg.namedTypesList.map { named -> named.id to "${path(pkg.importPath)}.${named.name}" }
    }.toMap()

    private val typeMemo = mutableMapOf<Int, String>()
    private val activeTypes = mutableSetOf<Int>()

    fun typeKey(id: Int): String {
        if (id == 0) {
            return "void"
        }
        typeMemo[id]?.let { return it }
        if (!activeTypes.add(id)) {
            return "<recursive>"
        }
        val definition = types[id]
        val key = if (definition == null) {
            "<missing-type>"
        } else {
            when (definition.typeCase) {
                ProtoTypeDefinition.TypeCase.BASIC -> "basic:${definition.basic.kind.number}"
                ProtoTypeDefinition.TypeCase.POINTER -> "pointer:${typeKey(definition.pointer.elemTypeId)}"
                ProtoTypeDefinition.TypeCase.ARRAY ->
                    "array:${definition.array.length}:${typeKey(definition.array.elemTypeId)}"
                ProtoTypeDefinition.TypeCase.SLICE -> "slice:${typeKey(definition.slice.elemTypeId)}"
                ProtoTypeDefinition.TypeCase.MAP_TYPE ->
                    "map:${typeKey(definition.mapType.keyTypeId)}:" +
                        typeKey(definition.mapType.valueTypeId)
                ProtoTypeDefinition.TypeCase.CHAN_TYPE ->
                    "chan:${definition.chanType.direction.number}:" +
                        typeKey(definition.chanType.elemTypeId)
                ProtoTypeDefinition.TypeCase.STRUCT_TYPE -> buildString {
                    append("struct{")
                    definition.structType.fieldsList.forEach { field ->
                        append(field.name)
                        append(':')
                        append(typeKey(field.typeId))
                        append(':')
                        append(field.embedded)
                        append(':')
                        append(field.exported)
                        append(':')
                        append(field.tag)
                        append('|')
                    }
                    append('}')
                }
                ProtoTypeDefinition.TypeCase.INTERFACE_TYPE -> buildString {
                    append("interface{")
                    definition.interfaceType.methodsList.forEach { method ->
                        append(method.name)
                        append(':')
                        append(typeKey(method.signatureTypeId))
                        append('|')
                    }
                    definition.interfaceType.embedTypeIdsList.forEach { embedded ->
                        append("embed:")
                        append(typeKey(embedded))
                        append('|')
                    }
                    append('}')
                }
                ProtoTypeDefinition.TypeCase.FUNC_TYPE -> buildString {
                    append("func(")
                    definition.funcType.paramTypeIdsList.forEach { parameter ->
                        append(typeKey(parameter))
                        append(',')
                    }
                    append(")->(")
                    definition.funcType.resultTypeIdsList.forEach { result ->
                        append(typeKey(result))
                        append(',')
                    }
                    append("):variadic=")
                    append(definition.funcType.variadic)
                    append(":recv=")
                    append(typeKey(definition.funcType.recvTypeId))
                }
                ProtoTypeDefinition.TypeCase.NAMED_REF -> buildString {
                    append("named:")
                    append(namedKeys[definition.namedRef.namedTypeId])
                    append('[')
                    definition.namedRef.typeArgIdsList.forEach { argument ->
                        append(typeKey(argument))
                        append(',')
                    }
                    append(']')
                }
                ProtoTypeDefinition.TypeCase.TYPE_PARAM ->
                    "typeparam:${definition.typeParam.name}:${definition.typeParam.index}:" +
                        typeKey(definition.typeParam.constraintTypeId)
                ProtoTypeDefinition.TypeCase.TUPLE -> buildString {
                    append("tuple(")
                    definition.tuple.elementTypeIdsList.forEach { element ->
                        append(typeKey(element))
                        append(',')
                    }
                    append(')')
                }
                ProtoTypeDefinition.TypeCase.UNSAFE_POINTER -> "unsafe.Pointer"
                ProtoTypeDefinition.TypeCase.TYPE_NOT_SET -> "<unknown-type>"
            }
        }
        activeTypes -= id
        typeMemo[id] = key
        return key
    }

    fun reversePackageKeys(): MutableMap<String, Int> = packageKeys.reverse()
    fun reverseNamedKeys(): MutableMap<String, Int> = namedKeys.reverse()
    fun reverseTypeKeys(): MutableMap<String, Int> = typeKeysById().reverse()
    fun reverseFunctionKeys(): MutableMap<String, Int> = functionKeysById().reverse()
    fun reverseGlobalKeys(): MutableMap<String, Int> = globalKeysById().reverse()
    fun reverseConstKeys(): MutableMap<String, Int> = constKeysById().reverse()
    fun typeKeysById(): Map<Int, String> = types.keys.associateWith(::typeKey)
    fun functionKeysById(): Map<Int, String> = functions.mapValues { symbol(it.value.fullName) }
    fun globalKeysById(): Map<Int, String> = globals.mapValues { symbol(it.value.fullName) }
    fun constKeysById(): Map<Int, String> = consts.mapValues { symbol(it.value.fullName) }

    fun isOwnedPackage(normalizer: ModelPathNormalizer, packageId: Int): Boolean {
        val pkg = packages[packageId] ?: return false
        return pkg.importPath in normalizer.replacements
    }

    private fun path(value: String): String = normalizer?.path(value) ?: value
    private fun symbol(value: String): String = normalizer?.symbol(value) ?: value
}

private data class IdRemap(
    val types: Map<Int, Int>,
    val packages: Map<Int, Int>,
    val functions: Map<Int, Int>,
    val named: Map<Int, Int>,
    val globals: Map<Int, Int>,
    val consts: Map<Int, Int>,
) {
    fun values(name: MapName): Map<Int, Int> = when (name) {
        MapName.TYPE -> types
        MapName.PACKAGE -> packages
        MapName.FUNCTION -> functions
        MapName.NAMED -> named
        MapName.GLOBAL -> globals
        MapName.CONST -> consts
        MapName.NONE -> emptyMap()
    }
}

private class IdCounters(
    private var typeId: Int,
    private var packageId: Int,
    private var functionId: Int,
    private var namedId: Int,
    private var globalId: Int,
    private var constId: Int,
) {
    fun nextType(): Int = ++typeId
    fun nextPackage(): Int = ++packageId
    fun nextFunction(): Int = ++functionId
    fun nextNamed(): Int = ++namedId
    fun nextGlobal(): Int = ++globalId
    fun nextConst(): Int = ++constId

    companion object {
        fun forProgram(program: ProtoProgram): IdCounters = IdCounters(
            typeId = program.typesList.maxOfOrNull { it.id } ?: 0,
            packageId = program.packagesList.maxOfOrNull { it.id } ?: 0,
            functionId = program.packagesList.flatMap { it.functionsList }.maxOfOrNull { it.id } ?: 0,
            namedId = program.packagesList.flatMap { it.namedTypesList }.maxOfOrNull { it.id } ?: 0,
            globalId = program.packagesList.flatMap { it.globalsList }.maxOfOrNull { it.id } ?: 0,
            constId = program.packagesList.flatMap { it.constantsList }.maxOfOrNull { it.id } ?: 0,
        )
    }
}

private data class ModelStructFieldPlan(
    val targetNamedId: Int,
    val indexByModel: Map<Int, Int>,
    val extraFieldNames: Set<String>,
)

private fun planModelStructFields(
    baseIndex: ProtoIndex,
    modelIndex: ProtoIndex,
    normalizer: ModelPathNormalizer,
): Map<Int, ModelStructFieldPlan> {
    val plans = mutableMapOf<Int, ModelStructFieldPlan>()
    val baseNamed = baseIndex.reverseNamedKeys()
    modelIndex.program.packagesList.forEach { pkg ->
        if (pkg.importPath !in normalizer.replacements) {
            return@forEach
        }
        pkg.namedTypesList.forEach { modelNamed ->
            val targetId = baseNamed[modelIndex.namedKeys.getValue(modelNamed.id)] ?: return@forEach
            val targetNamed = baseIndex.named.getValue(targetId)
            if (modelNamed.kind != ProtoNamedTypeKind.NAMED_TYPE_STRUCT) {
                return@forEach
            }
            require(targetNamed.kind == ProtoNamedTypeKind.NAMED_TYPE_STRUCT) {
                "Go model type ${modelIndex.namedKeys.getValue(modelNamed.id)} is a struct, " +
                    "but its target is not a struct"
            }
            val targetFields = targetNamed.fieldsList.associateBy { it.name }
            var nextIndex = targetNamed.fieldsList.maxOfOrNull { it.index + 1 } ?: 0
            val indexByModel = mutableMapOf<Int, Int>()
            val extraFieldNames = mutableSetOf<String>()
            modelNamed.fieldsList.forEach { modelField ->
                val targetField = targetFields[modelField.name]
                if (targetField == null) {
                    indexByModel[modelField.index] = nextIndex++
                    extraFieldNames += modelField.name
                } else {
                    require(
                        modelIndex.typeKey(modelField.typeId) == baseIndex.typeKey(targetField.typeId),
                    ) {
                        "Go model field ${modelIndex.namedKeys.getValue(modelNamed.id)}." +
                            "${modelField.name} has a different type from its target"
                    }
                    indexByModel[modelField.index] = targetField.index
                }
            }
            plans[modelNamed.id] = ModelStructFieldPlan(targetId, indexByModel, extraFieldNames)
        }
    }
    return plans
}

private fun applyModelStructFieldPlans(
    model: ProtoProgram,
    plans: Map<Int, ModelStructFieldPlan>,
): ProtoProgram {
    val underlyingPlans = model.packagesList
        .flatMap { it.namedTypesList }
        .mapNotNull { named -> plans[named.id]?.let { named.underlyingTypeId to it } }
        .toMap()
    val types = model.typesList.map { type ->
        val plan = underlyingPlans[type.id]
        if (plan == null || type.typeCase != ProtoTypeDefinition.TypeCase.STRUCT_TYPE) {
            type
        } else {
            val fields = type.structType.fieldsList.map { field ->
                val index = plan.indexByModel[field.index] ?: field.index
                field.toBuilder().setIndex(index).build()
            }
            type.toBuilder().setStructType(type.structType.toBuilder().clearFields().addAllFields(fields)).build()
        }
    }
    val packages = model.packagesList.map { pkg ->
        pkg.toBuilder()
            .clearNamedTypes()
            .addAllNamedTypes(pkg.namedTypesList.map { named ->
                val plan = plans[named.id] ?: return@map named
                named.toBuilder()
                    .clearFields()
                    .addAllFields(named.fieldsList.map { field ->
                        field.toBuilder()
                            .setIndex(plan.indexByModel.getValue(field.index))
                            .build()
                    })
                    .build()
            })
            .build()
    }
    return model.toBuilder().clearTypes().addAllTypes(types).clearPackages().addAllPackages(packages).build()
}

private fun remapModelStructFieldAccesses(
    model: ProtoProgram,
    modelIndex: ProtoIndex,
    plans: Map<Int, ModelStructFieldPlan>,
): ProtoProgram {
    val underlyingToNamed = modelIndex.named.values.associate { it.underlyingTypeId to it.id }

    fun remap(typeId: Int, fieldIndex: Int, fieldName: String): Int {
        val namedId = modelNamedTypeForFieldAccess(modelIndex, underlyingToNamed, typeId, mutableSetOf())
        val plan = plans[namedId] ?: return fieldIndex
        return plan.indexByModel[fieldIndex]
            ?: throw IllegalArgumentException(
                "Go model field access $fieldName[$fieldIndex] has no target field mapping",
            )
    }

    val bodies = model.functionBodiesList.map { body ->
        body.toBuilder()
            .clearBlocks()
            .addAllBlocks(body.blocksList.map { block ->
                block.toBuilder()
                    .clearInstructions()
                    .addAllInstructions(block.instructionsList.map { instruction ->
                        when (instruction.instCase) {
                            org.opentaint.ir.go.proto.ProtoInstruction.InstCase.FIELD_ADDR -> {
                                val access = instruction.fieldAddr
                                instruction.toBuilder().setFieldAddr(
                                    access.toBuilder().setFieldIndex(
                                        remap(access.x.typeId, access.fieldIndex, access.fieldName),
                                    ),
                                ).build()
                            }
                            org.opentaint.ir.go.proto.ProtoInstruction.InstCase.FIELD -> {
                                val access = instruction.field
                                instruction.toBuilder().setField(
                                    access.toBuilder().setFieldIndex(
                                        remap(access.x.typeId, access.fieldIndex, access.fieldName),
                                    ),
                                ).build()
                            }
                            else -> instruction
                        }
                    })
                    .build()
            })
            .build()
    }
    return model.toBuilder().clearFunctionBodies().addAllFunctionBodies(bodies).build()
}

private fun modelNamedTypeForFieldAccess(
    index: ProtoIndex,
    underlyingToNamed: Map<Int, Int>,
    typeId: Int,
    active: MutableSet<Int>,
): Int? {
    if (typeId == 0 || !active.add(typeId)) {
        return null
    }
    val type = index.types[typeId] ?: return null
    val result = when (type.typeCase) {
        ProtoTypeDefinition.TypeCase.POINTER ->
            modelNamedTypeForFieldAccess(index, underlyingToNamed, type.pointer.elemTypeId, active)
        ProtoTypeDefinition.TypeCase.NAMED_REF -> type.namedRef.namedTypeId
        ProtoTypeDefinition.TypeCase.STRUCT_TYPE -> underlyingToNamed[typeId]
        else -> null
    }
    active -= typeId
    return result
}

private fun fieldDeclarationToStructField(field: ProtoFieldDecl): ProtoStructField {
    return ProtoStructField.newBuilder()
        .setName(field.name)
        .setTypeId(field.typeId)
        .setIndex(field.index)
        .setEmbedded(field.embedded)
        .setExported(field.exported)
        .setTag(field.tag)
        .build()
}

private fun <K> assignIds(
    source: Map<Int, K>,
    existing: MutableMap<K, Int>,
    next: () -> Int,
): Map<Int, Int> {
    return buildMap {
        source.toSortedMap().forEach { (oldId, key) ->
            put(oldId, existing[key] ?: next().also { existing[key] = it })
        }
    }
}

private enum class MapName {
    NONE,
    TYPE,
    PACKAGE,
    FUNCTION,
    NAMED,
    GLOBAL,
    CONST,
}

private fun remapMessageIds(message: Message, remap: IdRemap): Message {
    val builder = message.toBuilder()
    message.allFields.forEach { (field, value) ->
        if (field.isRepeated) {
            val values = value as List<*>
            if (field.javaType == FieldDescriptor.JavaType.MESSAGE) {
                builder.clearField(field)
                values.forEach { child ->
                    builder.addRepeatedField(field, remapMessageIds(child as Message, remap))
                }
            } else {
                val mapName = remapClass(message, field)
                if (mapName != MapName.NONE) {
                    values.forEachIndexed { index, oldId ->
                        builder.setRepeatedField(
                            field,
                            index,
                            remapValue(oldId as Int, remap.values(mapName), message, field),
                        )
                    }
                }
            }
        } else if (field.javaType == FieldDescriptor.JavaType.MESSAGE) {
            builder.setField(field, remapMessageIds(value as Message, remap))
        } else {
            val mapName = remapClass(message, field)
            if (mapName != MapName.NONE) {
                builder.setField(
                    field,
                    remapValue(value as Int, remap.values(mapName), message, field),
                )
            }
        }
    }
    return builder.build()
}

private fun remapMessageIdsOfClass(
    message: Message,
    mapName: MapName,
    remap: (Int) -> Int,
): Message {
    val builder = message.toBuilder()
    message.allFields.forEach { (field, value) ->
        if (field.isRepeated) {
            val values = value as List<*>
            if (field.javaType == FieldDescriptor.JavaType.MESSAGE) {
                builder.clearField(field)
                values.forEach { child ->
                    builder.addRepeatedField(
                        field,
                        remapMessageIdsOfClass(child as Message, mapName, remap),
                    )
                }
            } else if (remapClass(message, field) == mapName) {
                values.forEachIndexed { index, oldId ->
                    builder.setRepeatedField(field, index, remap(oldId as Int))
                }
            }
        } else if (field.javaType == FieldDescriptor.JavaType.MESSAGE) {
            builder.setField(field, remapMessageIdsOfClass(value as Message, mapName, remap))
        } else if (remapClass(message, field) == mapName) {
            builder.setField(field, remap(value as Int))
        }
    }
    return builder.build()
}

private fun remapValue(
    oldId: Int,
    values: Map<Int, Int>,
    message: Message,
    field: FieldDescriptor,
): Int {
    if (oldId == 0) {
        return 0
    }
    return values[oldId] ?: throw IllegalArgumentException(
        "remapping ${message.descriptorForType.name}.${field.name}: " +
            "wire id $oldId has no model remapping",
    )
}

private fun validateReferences(program: ProtoProgram, source: String) {
    val validIds = mapOf(
        MapName.TYPE to program.typesList.mapTo(mutableSetOf()) { it.id },
        MapName.PACKAGE to program.packagesList.mapTo(mutableSetOf()) { it.id },
        MapName.FUNCTION to program.packagesList
            .flatMapTo(mutableSetOf()) { pkg -> pkg.functionsList.map { it.id } },
        MapName.NAMED to program.packagesList
            .flatMapTo(mutableSetOf()) { pkg -> pkg.namedTypesList.map { it.id } },
        MapName.GLOBAL to program.packagesList
            .flatMapTo(mutableSetOf()) { pkg -> pkg.globalsList.map { it.id } },
        MapName.CONST to program.packagesList
            .flatMapTo(mutableSetOf()) { pkg -> pkg.constantsList.map { it.id } },
    )

    fun validate(message: Message, path: String) {
        message.allFields.forEach { (field, value) ->
            if (field.javaType == FieldDescriptor.JavaType.MESSAGE) {
                if (field.isRepeated) {
                    (value as List<*>).forEachIndexed { index, child ->
                        validate(child as Message, "$path.${field.name}[$index]")
                    }
                } else {
                    validate(value as Message, "$path.${field.name}")
                }
                return@forEach
            }
            val mapName = remapClass(message, field)
            if (mapName == MapName.NONE) {
                return@forEach
            }
            val idsInProgram = validIds.getValue(mapName)
            val ids = if (field.isRepeated) value as List<*> else listOf(value)
            ids.forEach { rawId ->
                val id = rawId as Int
                require(id == 0 || id in idsInProgram) {
                    "merging Go model $source: $path.${field.name} references missing " +
                        "${mapName.name.lowercase()} $id"
                }
            }
        }
    }

    validate(program, "ProtoProgram")
}

private fun remapClass(message: Message, field: FieldDescriptor): MapName {
    val name = field.name
    if (name == "id") {
        return when (message.descriptorForType.name) {
            "ProtoTypeDefinition" -> MapName.TYPE
            "ProtoPackage" -> MapName.PACKAGE
            "ProtoNamedType" -> MapName.NAMED
            "ProtoFunction" -> MapName.FUNCTION
            "ProtoGlobal" -> MapName.GLOBAL
            "ProtoConst" -> MapName.CONST
            else -> MapName.NONE
        }
    }
    return when {
        name == "named_type_id" || name == "embedded_interface_ids" -> MapName.NAMED
        name == "package_id" || name == "import_ids" -> MapName.PACKAGE
        name == "fn_id" || name == "method_ids" || name == "pointer_method_ids" ||
            "function_id" in name -> MapName.FUNCTION
        name == "global_id" -> MapName.GLOBAL
        name == "type_arg_ids" || name.endsWith("type_id") || name.endsWith("type_ids") -> MapName.TYPE
        else -> MapName.NONE
    }
}

private fun appendUniqueIds(base: List<Int>, added: List<Int>): List<Int> {
    val result = base.toMutableList()
    val seen = base.toMutableSet()
    added.forEach { value ->
        if (value != 0 && seen.add(value)) {
            result += value
        }
    }
    return result
}

private fun <T> mergeById(base: List<T>, added: List<T>, id: (T) -> Int): List<T> {
    val result = base.toMutableList()
    val seen = base.mapTo(mutableSetOf(), id)
    added.forEach { value ->
        if (seen.add(id(value))) {
            result += value
        }
    }
    return result
}

private fun <K, V> Map<K, V>.reverse(): MutableMap<V, K> {
    return entries.associateTo(mutableMapOf()) { (key, value) -> value to key }
}

private const val GO_MODEL_PREFIX = "opentaint/"
private const val UNIVERSE_PACKAGE_PATH = "<universe>"
private const val MODEL_SUPPORT_KIND = "opentaint model support"
private const val MODEL_KIND = "opentaint model"
