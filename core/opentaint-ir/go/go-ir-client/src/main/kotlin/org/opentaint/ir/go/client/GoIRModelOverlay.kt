package org.opentaint.ir.go.client

import org.opentaint.ir.go.api.GoIRConst
import org.opentaint.ir.go.api.GoIRField
import org.opentaint.ir.go.api.GoIRFunction
import org.opentaint.ir.go.api.GoIRGlobal
import org.opentaint.ir.go.api.GoIRInterfaceMethod
import org.opentaint.ir.go.api.GoIRNamedType
import org.opentaint.ir.go.api.GoIRPackage
import org.opentaint.ir.go.api.GoIRProgram
import org.opentaint.ir.go.api.GoIRTypeParamDecl
import org.opentaint.ir.go.impl.GoIRFunctionImpl
import org.opentaint.ir.go.impl.GoIRBasicBlockImpl
import org.opentaint.ir.go.impl.GoIRConstImpl
import org.opentaint.ir.go.impl.GoIRGlobalImpl
import org.opentaint.ir.go.impl.GoIRNamedTypeImpl
import org.opentaint.ir.go.expr.GoIRFieldAddrExpr
import org.opentaint.ir.go.expr.GoIRFieldExpr
import org.opentaint.ir.go.expr.GoIRFunctionValueExpr
import org.opentaint.ir.go.expr.GoIRGlobalValueExpr
import org.opentaint.ir.go.expr.GoIRMakeClosureExpr
import org.opentaint.ir.go.cfg.GoIRCallInfo
import org.opentaint.ir.go.cfg.GoIRCallTarget
import org.opentaint.ir.go.inst.GoIRAssignInst
import org.opentaint.ir.go.inst.GoIRCall
import org.opentaint.ir.go.inst.GoIRDefer
import org.opentaint.ir.go.inst.GoIRFieldStore
import org.opentaint.ir.go.inst.GoIRGlobalStore
import org.opentaint.ir.go.inst.GoIRGo
import org.opentaint.ir.go.type.GoIRAnonymousInterfaceType
import org.opentaint.ir.go.type.GoIRAnonymousInterfaceTypeRef
import org.opentaint.ir.go.type.GoIRArrayType
import org.opentaint.ir.go.type.GoIRBasicType
import org.opentaint.ir.go.type.GoIRChanType
import org.opentaint.ir.go.type.GoIRFuncType
import org.opentaint.ir.go.type.GoIRMapType
import org.opentaint.ir.go.type.GoIRNamedInterfaceType
import org.opentaint.ir.go.type.GoIRNamedTypeRef
import org.opentaint.ir.go.type.GoIRNamedTypeKind
import org.opentaint.ir.go.type.GoIRPointerType
import org.opentaint.ir.go.type.GoIRSliceType
import org.opentaint.ir.go.type.GoIRStructType
import org.opentaint.ir.go.type.GoIRStructField
import org.opentaint.ir.go.type.GoIRTupleType
import org.opentaint.ir.go.type.GoIRType
import org.opentaint.ir.go.type.GoIRTypeParamRef
import org.opentaint.ir.go.type.GoIRTypeParamType
import java.util.Collections
import java.util.IdentityHashMap

internal data class GoIRModelDefinition(
    val source: String,
    val program: GoIRProgram,
    val targetPaths: Set<String>,
)

/** A read-only view that applies partial Go models when a package is looked up. */
internal class GoIRModelOverlayProgram(
    override val originalProgram: GoIRProgram,
    definitions: List<GoIRModelDefinition>,
) : GoIRProgram {
    private val definitionsByTarget = linkedMapOf<String, GoIRModelDefinition>()
    private val modelDependencies = linkedMapOf<String, GoIRPackage>()
    private val effectivePackages = mutableMapOf<String, GoIRPackage>()

    init {
        definitions.forEach { definition ->
            definition.targetPaths.forEach { target ->
                val previous = definitionsByTarget.putIfAbsent(target, definition)
                require(previous == null) {
                    "Go package \"$target\" is modeled more than once " +
                        "(${previous?.source} and ${definition.source})"
                }
            }
        }
        definitionsByTarget.forEach { (target, definition) ->
            validatePackage(target, definition)
        }
        definitions.forEach { definition ->
            definition.program.packages.forEach { (path, pkg) ->
                if (path !in definition.targetPaths && originalProgram.findPackage(path) == null) {
                    modelDependencies.putIfAbsent(path, pkg)
                }
            }
        }
    }

    override val packages: Map<String, GoIRPackage> by lazy {
        (originalProgram.packages.keys + modelDependencies.keys).associateWithTo(linkedMapOf()) { path ->
            findPackage(path) ?: error("Go package $path disappeared during model lookup")
        }
    }

    override val anonymousInterfaces: Map<Int, GoIRAnonymousInterfaceType>
        get() = originalProgram.anonymousInterfaces

    override fun findPackage(importPath: String): GoIRPackage? {
        val original = originalProgram.findPackage(importPath) ?: return modelDependencies[importPath]
        val definition = definitionsByTarget[importPath] ?: return original
        return effectivePackages.getOrPut(importPath) {
            createPackage(original, definition.program.findPackage(importPath)!!)
        }
    }

    override fun allFunctions(): List<GoIRFunction> = packages.values.flatMap { it.functions }

    override fun allNamedTypes(): List<GoIRNamedType> = packages.values.flatMap { it.namedTypes }

    override fun mainPackage(): GoIRPackage? = packages.values.find { it.name == "main" }

    private fun validatePackage(target: String, definition: GoIRModelDefinition) {
        val original = originalProgram.findPackage(target)
            ?: throw IllegalArgumentException(
                "invalid Go model ${definition.source}: target package \"$target\" was not found",
            )
        val model = definition.program.findPackage(target)
            ?: throw IllegalArgumentException(
                "invalid Go model ${definition.source}: model package \"$target\" was not found",
            )
        require(model.name == original.name) {
            "Go model package \"opentaint/$target\" has package name " +
                "\"${model.name}\", want \"${original.name}\" (the modeled package name)"
        }

        val originalFunctions = original.functions.associateBy { it.fullName }
        val initializers = initializerFunctions(model)
        model.functions.forEach { function ->
            if (function in initializers || function.parent != null) return@forEach
            val targetFunction = originalFunctions[function.fullName]
            if (targetFunction != null) {
                require(sameSignature(function, targetFunction)) {
                    "Go model function ${function.fullName} has a different signature from its target"
                }
            }
        }
        validateFields(original, model, definition.source)
    }

    private fun createPackage(original: GoIRPackage, model: GoIRPackage): GoIRPackage {
        val initializers = initializerFunctions(model)
        val modelFunctions = model.functions.filterNot { it in initializers }
        val modelByName = modelFunctions
            .filter { it.parent == null }
            .associateBy { it.fullName }
        val used = mutableSetOf<GoIRFunction>()
        val specializedAnonymousFunctions = mutableListOf<GoIRFunction>()
        val specializer = GoIRModelSpecializer(this)
        val effectiveFunctions = original.functions.map { target ->
            val replacement = modelByName[target.fullName]
            if (replacement != null && replacement.bodyAvailable && sameSignature(replacement, target)) {
                used += replacement
                (replacement as? GoIRFunctionImpl)?.markAsModel(MODEL_KIND)
                replacement
            } else {
                val genericModel = target.originFullName?.let(modelByName::get)
                if (genericModel != null && genericModel.bodyAvailable) {
                    val specialized = specializer.specialize(genericModel, target)
                    specializedAnonymousFunctions += specialized.anonymousFunctions
                    specialized.root
                } else {
                    target
                }
            }
        }.toMutableList()
        effectiveFunctions += specializedAnonymousFunctions
        modelFunctions.filterTo(effectiveFunctions) { function ->
            if (function in used) return@filterTo false
            (function as? GoIRFunctionImpl)?.markAsModel(MODEL_SUPPORT_KIND)
            true
        }

        val effective = GoIRModelOverlayPackage(original, model, effectiveFunctions)
        modelFunctions.forEach { (it as? GoIRFunctionImpl)?.rebindPackage(effective) }
        specializedAnonymousFunctions.forEach { (it as? GoIRFunctionImpl)?.rebindPackage(effective) }
        effectiveFunctions.filter { it.syntheticKind == MODEL_KIND }
            .forEach { (it as? GoIRFunctionImpl)?.rebindPackage(effective) }
        model.namedTypes.forEach { (it as? GoIRNamedTypeImpl)?.rebindPackage(effective) }
        model.globals.forEach { (it as? GoIRGlobalImpl)?.rebindPackage(effective) }
        model.constants.forEach { (it as? GoIRConstImpl)?.rebindPackage(effective) }
        rebindModelPackage(model)
        remapModelFieldAccesses(original, model)
        rebindModelEntities(
            effective,
            modelFunctions + specializedAnonymousFunctions +
                effectiveFunctions.filter { it.syntheticKind == MODEL_KIND },
        )
        return effective
    }

    private fun rebindModelEntities(
        effective: GoIRPackage,
        functions: List<GoIRFunction>,
    ) {
        fun resolve(function: GoIRFunction): GoIRFunction {
            if (function.pkg?.importPath != effective.importPath) return function
            return effective.functions.firstOrNull { candidate ->
                candidate.fullName == function.fullName && sameSignature(candidate, function)
            } ?: function
        }
        fun resolve(global: GoIRGlobal): GoIRGlobal {
            if (global.pkg.importPath != effective.importPath) return global
            return effective.globals.firstOrNull { it.fullName == global.fullName } ?: global
        }
        fun call(source: GoIRCallInfo): GoIRCallInfo {
            val target = source.target
            if (target !is GoIRCallTarget.Function) return source
            val rebound = resolve(target.function)
            return if (rebound === target.function) source else source.copy(
                target = GoIRCallTarget.Function(rebound),
            )
        }

        val visitedFunctions = Collections.newSetFromMap(IdentityHashMap<GoIRFunction, Boolean>())
        functions.filter(visitedFunctions::add).forEach { function ->
            val body = function.body ?: return@forEach
            body.blocks.forEach { block ->
                val mutableBlock = block as? GoIRBasicBlockImpl ?: return@forEach
                mutableBlock.setInstructions(block.instructions.map { instruction ->
                    when (instruction) {
                        is GoIRCall -> instruction.copy(call = call(instruction.call))
                        is GoIRGo -> instruction.copy(call = call(instruction.call))
                        is GoIRDefer -> instruction.copy(call = call(instruction.call))
                        is GoIRGlobalStore -> instruction.copy(global = resolve(instruction.global))
                        is GoIRAssignInst -> when (val expression = instruction.expr) {
                            is GoIRFunctionValueExpr -> instruction.copy(
                                expr = expression.copy(function = resolve(expression.function)),
                            )
                            is GoIRMakeClosureExpr -> instruction.copy(
                                expr = expression.copy(fn = resolve(expression.fn)),
                            )
                            is GoIRGlobalValueExpr -> instruction.copy(
                                expr = expression.copy(global = resolve(expression.global)),
                            )
                            else -> instruction
                        }
                        else -> instruction
                    }
                })
            }
        }
    }

    private fun rebindModelPackage(model: GoIRPackage) {
        val visited = Collections.newSetFromMap(IdentityHashMap<GoIRType, Boolean>())
        fun rebind(type: GoIRType) {
            if (!visited.add(type)) return
            when (type) {
                is GoIRPointerType -> rebind(type.elem)
                is GoIRArrayType -> rebind(type.elem)
                is GoIRSliceType -> rebind(type.elem)
                is GoIRMapType -> {
                    rebind(type.key)
                    rebind(type.value)
                }
                is GoIRChanType -> rebind(type.elem)
                is GoIRStructType -> {
                    type.structTypeRef?.program = this
                    type.fields.forEach { rebind(it.type) }
                }
                is GoIRNamedTypeRef -> {
                    type.typeRef.program = this
                    type.typeArgs.forEach(::rebind)
                }
                is GoIRNamedInterfaceType -> {
                    type.interfaceTypeRef.program = this
                    type.methods.forEach { rebind(it.signature) }
                    type.embeds.forEach(::rebind)
                }
                is GoIRAnonymousInterfaceTypeRef -> {}
                is GoIRAnonymousInterfaceType -> {
                    type.methods.forEach { rebind(it.signature) }
                    type.embeds.forEach(::rebind)
                }
                is GoIRFuncType -> {
                    type.params.forEach(::rebind)
                    type.results.forEach(::rebind)
                    type.recv?.let(::rebind)
                }
                is GoIRTupleType -> type.elements.forEach(::rebind)
                is GoIRTypeParamType -> rebind(type.constraint)
                else -> {}
            }
        }
        model.namedTypes.forEach { type ->
            rebind(type.underlying)
            type.fields.forEach { rebind(it.type) }
            type.typeParams.forEach { rebind(it.constraint) }
        }
        model.functions.forEach { function ->
            rebind(function.signature)
            function.params.forEach { rebind(it.type) }
            function.freeVars.forEach { rebind(it.type) }
            function.typeParams.forEach { rebind(it.constraint) }
            function.typeArgs.forEach(::rebind)
            function.parent?.program = this
            function.anonymousFunctions.forEach { it.program = this }
        }
        model.globals.forEach { rebind(it.type) }
        model.constants.forEach { rebind(it.type) }
    }

    private fun remapModelFieldAccesses(original: GoIRPackage, model: GoIRPackage) {
        val plans = model.namedTypes.mapNotNull { modelType ->
            val targetType = original.findNamedType(modelType.name) ?: return@mapNotNull null
            val targetByName = targetType.fields.associateBy { it.name }
            val extraByName = modelType.fields
                .filter { it.name !in targetByName }
                .mapIndexed { index, field -> field.name to targetType.fields.size + index }
                .toMap()
            val indexes = modelType.fields.associate { field ->
                field.index to (targetByName[field.name]?.index ?: extraByName.getValue(field.name))
            }
            modelType.fullName to indexes
        }.toMap()

        fun namedType(type: GoIRType): String? = when (type) {
            is GoIRPointerType -> namedType(type.elem)
            is GoIRNamedTypeRef -> type.typeRef.fullRefName
            is GoIRStructType -> type.structTypeRef?.fullRefName
            else -> null
        }
        fun index(type: GoIRType, oldIndex: Int): Int =
            namedType(type)?.let(plans::get)?.get(oldIndex) ?: oldIndex

        model.functions.forEach { function ->
            val body = function.body ?: return@forEach
            body.blocks.forEach { block ->
                val mutableBlock = block as? GoIRBasicBlockImpl ?: return@forEach
                mutableBlock.setInstructions(block.instructions.map { instruction ->
                    when (instruction) {
                        is GoIRAssignInst -> when (val expression = instruction.expr) {
                            is GoIRFieldAddrExpr -> instruction.copy(
                                expr = expression.copy(fieldIndex = index(expression.x.type, expression.fieldIndex)),
                            )
                            is GoIRFieldExpr -> instruction.copy(
                                expr = expression.copy(fieldIndex = index(expression.x.type, expression.fieldIndex)),
                            )
                            else -> instruction
                        }
                        is GoIRFieldStore -> instruction.copy(
                            fieldIndex = index(instruction.base.type, instruction.fieldIndex),
                        )
                        else -> instruction
                    }
                })
            }
        }
    }

    private fun validateFields(original: GoIRPackage, model: GoIRPackage, source: String) {
        model.namedTypes.forEach { modelType ->
            val targetType = original.findNamedType(modelType.name) ?: return@forEach
            val targetFields = targetType.fields.associateBy { it.name }
            modelType.fields.forEach { modelField ->
                val targetField = targetFields[modelField.name] ?: return@forEach
                require(sameType(modelField.type, targetField.type)) {
                    "invalid Go model $source: field ${targetType.fullName}.${modelField.name} " +
                        "has a different type from its target"
                }
            }
        }
    }

    private fun sameSignature(model: GoIRFunction, target: GoIRFunction): Boolean =
        sameType(model.signature, target.signature) &&
            model.isMethod == target.isMethod &&
            model.isPointerReceiver == target.isPointerReceiver

    private fun sameType(first: GoIRType, second: GoIRType): Boolean =
        sameType(first, second, mutableListOf())

    private fun sameType(
        first: GoIRType,
        second: GoIRType,
        active: MutableList<Pair<GoIRType, GoIRType>>,
    ): Boolean {
        if (first === second) return true
        if (active.any { (left, right) -> left === first && right === second }) return true
        active += first to second
        return when {
            first is GoIRBasicType && second is GoIRBasicType -> first.kind == second.kind
            first is GoIRPointerType && second is GoIRPointerType ->
                sameType(first.elem, second.elem, active)
            first is GoIRArrayType && second is GoIRArrayType ->
                first.length == second.length && sameType(first.elem, second.elem, active)
            first is GoIRSliceType && second is GoIRSliceType ->
                sameType(first.elem, second.elem, active)
            first is GoIRMapType && second is GoIRMapType ->
                sameType(first.key, second.key, active) && sameType(first.value, second.value, active)
            first is GoIRChanType && second is GoIRChanType ->
                first.direction == second.direction && sameType(first.elem, second.elem, active)
            first is GoIRStructType && second is GoIRStructType ->
                first.structTypeRef?.fullRefName == second.structTypeRef?.fullRefName &&
                    first.fields.size == second.fields.size &&
                    first.fields.zip(second.fields).all { (left, right) ->
                        left.name == right.name && left.isEmbedded == right.isEmbedded &&
                            left.tag == right.tag && sameType(left.type, right.type, active)
                    }
            first is GoIRNamedTypeRef && second is GoIRNamedTypeRef ->
                first.typeRef.fullRefName == second.typeRef.fullRefName &&
                    sameTypes(first.typeArgs, second.typeArgs, active)
            first is GoIRFuncType && second is GoIRFuncType ->
                first.isVariadic == second.isVariadic &&
                    nullableTypeEqual(first.recv, second.recv, active) &&
                    sameTypes(first.params, second.params, active) &&
                    sameTypes(first.results, second.results, active)
            first is GoIRTupleType && second is GoIRTupleType ->
                sameTypes(first.elements, second.elements, active)
            first is GoIRTypeParamType && second is GoIRTypeParamType ->
                first.paramIndex == second.paramIndex && first.name == second.name &&
                    sameType(first.constraint, second.constraint, active)
            first is GoIRTypeParamRef && second is GoIRTypeParamRef ->
                first.paramIndex == second.paramIndex && first.name == second.name
            first is GoIRAnonymousInterfaceTypeRef && second is GoIRAnonymousInterfaceTypeRef ->
                sameAnonymousInterface(first.interfaceType, second.interfaceType, active)
            first is GoIRAnonymousInterfaceType && second is GoIRAnonymousInterfaceType ->
                sameAnonymousInterface(first, second, active)
            first is GoIRNamedInterfaceType && second is GoIRNamedInterfaceType ->
                first.interfaceTypeRef.fullRefName == second.interfaceTypeRef.fullRefName
            else -> false
        }
    }

    private fun sameAnonymousInterface(
        first: GoIRAnonymousInterfaceType,
        second: GoIRAnonymousInterfaceType,
        active: MutableList<Pair<GoIRType, GoIRType>>,
    ): Boolean = first.methods.size == second.methods.size &&
        first.methods.zip(second.methods).all { (left, right) ->
            left.name == right.name && sameType(left.signature, right.signature, active)
        } && sameTypes(first.embeds, second.embeds, active)

    private fun sameTypes(
        first: List<GoIRType>,
        second: List<GoIRType>,
        active: MutableList<Pair<GoIRType, GoIRType>>,
    ): Boolean = first.size == second.size &&
        first.zip(second).all { (left, right) -> sameType(left, right, active) }

    private fun nullableTypeEqual(
        first: GoIRType?,
        second: GoIRType?,
        active: MutableList<Pair<GoIRType, GoIRType>>,
    ): Boolean = when {
        first == null -> second == null
        second == null -> false
        else -> sameType(first, second, active)
    }

    private fun initializerFunctions(pkg: GoIRPackage): Set<GoIRFunction> {
        val result = pkg.functions
            .filterTo(mutableSetOf()) { it.name == "init" || it.name.startsWith("init#") }
        do {
            val oldSize = result.size
            pkg.functions.forEach { function ->
                val parentName = function.parent?.fullName
                if (parentName != null && result.any { it.fullName == parentName }) {
                    result += function
                }
            }
        } while (result.size != oldSize)
        return result
    }
}

private class GoIRModelOverlayPackage(
    private val original: GoIRPackage,
    private val model: GoIRPackage,
    override val functions: List<GoIRFunction>,
) : GoIRPackage {
    override val importPath: String get() = original.importPath
    override val name: String get() = original.name
    override val isStdlib: Boolean get() = original.isStdlib
    override val isDependency: Boolean get() = original.isDependency

    private val functionsByName = functions.groupBy { it.fullName }

    override val namedTypes: List<GoIRNamedType> by lazy {
        val modelByName = model.namedTypes.associateBy { it.name }
        val result = original.namedTypes.map { target ->
            val modelType = modelByName[target.name]
            if (modelType == null) target else GoIRModelOverlayNamedType(target, modelType, this, functionsByName)
        }.toMutableList()
        val originalNames = original.namedTypes.mapTo(mutableSetOf()) { it.name }
        result += model.namedTypes.filter { it.name !in originalNames }
        result
    }

    override val globals: List<GoIRGlobal> by lazy {
        appendByName(original.globals, model.globals) { it.name }
    }
    override val constants: List<GoIRConst> by lazy {
        appendByName(original.constants, model.constants) { it.name }
    }
    override val imports: List<GoIRPackage> by lazy {
        appendByName(original.imports, model.imports) { it.importPath }
    }
    override val initFunction: GoIRFunction? get() = original.initFunction

    override fun findNamedType(name: String): GoIRNamedType? = namedTypes.find { it.name == name }
    override fun findGlobal(name: String): GoIRGlobal? = globals.find { it.name == name }
    override fun findConstant(name: String): GoIRConst? = constants.find { it.name == name }
    override fun allMethods(): List<GoIRFunction> = namedTypes.flatMap { it.allMethods() }

    private fun <T> appendByName(first: List<T>, second: List<T>, name: (T) -> String): List<T> {
        val names = first.mapTo(mutableSetOf(), name)
        return first + second.filter { names.add(name(it)) }
    }
}

private class GoIRModelOverlayNamedType(
    private val original: GoIRNamedType,
    private val model: GoIRNamedType,
    override val pkg: GoIRPackage,
    private val functionsByName: Map<String, List<GoIRFunction>>,
) : GoIRNamedType {
    override val name: String get() = original.name
    override val fullName: String get() = original.fullName
    override val underlying: GoIRType by lazy {
        val originalStruct = original.underlying as? GoIRStructType
        if (originalStruct == null) {
            original.underlying
        } else {
            GoIRStructType(
                fields.map { field ->
                    GoIRStructField(
                        field.name,
                        field.type,
                        field.isEmbedded,
                        field.tag,
                    )
                },
                originalStruct.structTypeRef,
            )
        }
    }
    override val kind: GoIRNamedTypeKind get() = original.kind
    override val position get() = original.position
    override val typeParams: List<GoIRTypeParamDecl> get() = original.typeParams

    override val fields: List<GoIRField> by lazy {
        val targetByName = original.fields.associateBy { it.name }
        val result = original.fields.map { field -> field.copy(enclosingType = this) }.toMutableList()
        model.fields.filter { it.name !in targetByName }.forEach { field ->
            result += field.copy(index = result.size, enclosingType = this)
        }
        result
    }

    override val methods: List<GoIRFunction> by lazy { effectiveMethods(original.methods, model.methods) }
    override val pointerMethods: List<GoIRFunction> by lazy {
        effectiveMethods(original.pointerMethods, model.pointerMethods)
    }
    override val interfaceMethods: List<GoIRInterfaceMethod> get() = original.interfaceMethods
    override val embeddedInterfaces: List<GoIRNamedType> get() = original.embeddedInterfaces

    private fun effectiveMethods(
        targetMethods: List<GoIRFunction>,
        modelMethods: List<GoIRFunction>,
    ): List<GoIRFunction> {
        val targetNames = targetMethods.mapTo(mutableSetOf()) { it.fullName }
        val result = targetMethods.map { target ->
            functionsByName[target.fullName]
                ?.firstOrNull()
                ?: target
        }.toMutableList()
        modelMethods.filter { it.fullName !in targetNames }.forEach { modelMethod ->
            result += functionsByName[modelMethod.fullName]
                ?.firstOrNull()
                ?: modelMethod
        }
        return result
    }
}

private const val MODEL_SUPPORT_KIND = "opentaint model support"
private const val MODEL_KIND = "opentaint model"
