package org.opentaint.ir.go.client

import org.opentaint.ir.go.api.*
import org.opentaint.ir.go.type.*
import java.util.IdentityHashMap

/** A lazy, cached IR view. Every reference exposed by the view uses the same transformer chain. */
class GoIRTransformerChain(
    private val source: GoIRProgram,
    private val transformers: List<GoIRTransformer>,
) : GoIRProgram {
    override val originalProgram: GoIRProgram get() = source.originalProgram
    private val types = LookupTypes(this, source.anonymousInterfaces)
    private val functionCache = IdentityHashMap<GoIRFunction, GoIRFunction>()
    private val globalCache = IdentityHashMap<GoIRGlobal, GoIRGlobal>()
    private val packageCache = mutableMapOf<String, GoIRPackage>()
    private val additionalPackages by lazy {
        transformers.flatMap { it.additionalPackages }.associateBy { it.importPath }
    }

    override val packages: Map<String, GoIRPackage> by lazy {
        (source.packages.keys + additionalPackages.keys).associateWith { findPackage(it)!! }
    }
    override val anonymousInterfaces: Map<Int, GoIRAnonymousInterfaceType>
        get() = types.anonymousInterfaces

    override fun findPackage(importPath: String): GoIRPackage? = synchronized(packageCache) {
        packageCache[importPath]?.let { return it }
        val original = source.findPackage(importPath) ?: additionalPackages[importPath] ?: return null
        val transformed = transformers.fold(original) { pkg, transformer -> transformer.transformPackage(pkg) }
        PackageView(transformed).also { packageCache[importPath] = it }
    }

    override fun allFunctions(): List<GoIRFunction> = packages.values.flatMap { it.functions }
    override fun allNamedTypes(): List<GoIRNamedType> = packages.values.flatMap { it.namedTypes }
    override fun mainPackage(): GoIRPackage? = packages.values.find { it.name == "main" }

    private fun function(source: GoIRFunction): GoIRFunction = synchronized(functionCache) {
        functionCache[source]?.let { return it }
        val transformed = transformers.fold(source) { fn, transformer -> transformer.transformFunction(fn) }
        functionCache[transformed]?.let { functionCache[source] = it; return it }
        val view = when (transformed) {
            is GoIRApproximatedFunction -> ApproximationView(transformed)
            is GoIRModelFunction -> ModelFunctionView(transformed)
            else -> FunctionView(transformed)
        }
        functionCache[source] = view
        functionCache[transformed] = view
        view
    }

    private fun global(source: GoIRGlobal): GoIRGlobal = synchronized(globalCache) {
        globalCache[source]?.let { return it }
        val resolved = (findPackage(source.pkg.importPath) as? PackageView)
            ?.source?.findGlobal(source.name) ?: source
        globalCache[resolved]?.let { globalCache[source] = it; return it }
        val view = object : GoIRGlobal by resolved {
            override val pkg get() = findPackage(resolved.pkg.importPath)!!
            override val type by lazy { types(resolved.type) }
        }
        globalCache[source] = view
        globalCache[resolved] = view
        view
    }

    private fun reference(ref: GoIrFunctionReference): GoIrFunctionReference =
        ref.copy(signature = types(ref.signature) as GoIRFuncType).also { it.program = this }

    private open inner class FunctionView(protected val source: GoIRFunction) : GoIRFunction by source {
        override val pkg: GoIRPackage? get() = source.pkg?.importPath?.let(::findPackage)
        override val signature by lazy { types(source.signature) as GoIRFuncType }
        override val params by lazy { source.params.map { it.copy(type = types(it.type)) } }
        override val freeVars by lazy { source.freeVars.map { it.copy(type = types(it.type)) } }
        override val typeParams by lazy { source.typeParams.map { it.copy(constraint = types(it.constraint)) } }
        override val typeArgs by lazy { source.typeArgs.map(types::invoke) }
        override val receiverType get() = signature.recv
        override val parent by lazy { source.parent?.let(::reference) }
        override val anonymousFunctions by lazy { source.anonymousFunctions.map(::reference) }
        override val body by lazy { source.body?.let { transformGoIRBody(it, this, types, ::function, ::global) } }
        override val hasBody get() = source.hasBody
        override val bodyAvailable get() = source.bodyAvailable
        override val parameters get() = if (freeVars.isEmpty()) params
            else params + freeVars.map { GoIRParameter(it.name, it.type, params.size + it.index) }
        override val returnType get() = signature.results.first()
        override fun flowGraph() = body?.instGraph ?: error("Function $name has no body")
        override fun equals(other: Any?): Boolean = other is GoIRFunction && fullName == other.fullName
        override fun hashCode(): Int = fullName.hashCode()
        override fun toString(): String = source.toString()
    }

    private inner class ModelFunctionView(source: GoIRModelFunction) : FunctionView(source), GoIRModelFunction
    private inner class ApproximationView(source: GoIRApproximatedFunction) :
        FunctionView(source), GoIRApproximatedFunction {
        override val original: GoIRFunction = source.original
    }

    private inner class PackageView(val source: GoIRPackage) : GoIRPackage by source {
        override val functions by lazy { source.functions.map(::function) }
        override val namedTypes by lazy { source.namedTypes.map { NamedTypeView(it, this) } }
        override val globals by lazy { source.globals.map(::global) }
        override val constants by lazy {
            source.constants.map { constant ->
                object : GoIRConst by constant {
                    override val pkg get() = this@PackageView
                    override val type by lazy { types(constant.type) }
                }
            }
        }
        override fun findGlobal(name: String) = globals.find { it.name == name }
        override fun findConstant(name: String) = constants.find { it.name == name }
        override val imports by lazy { source.imports.map { findPackage(it.importPath) ?: it } }
        override val initFunction get() = source.initFunction?.let(::function)
        override fun findNamedType(name: String) = namedTypes.find { it.name == name }
        override fun allMethods() = namedTypes.flatMap { it.allMethods() }
    }

    private inner class NamedTypeView(private val source: GoIRNamedType, override val pkg: GoIRPackage) :
        GoIRNamedType by source {
        override val underlying by lazy { types(source.underlying) }
        override val fields by lazy { source.fields.map { it.copy(type = types(it.type), enclosingType = this) } }
        override val methods by lazy { source.methods.map(::function) }
        override val pointerMethods by lazy { source.pointerMethods.map(::function) }
        override val typeParams by lazy { source.typeParams.map { it.copy(constraint = types(it.constraint)) } }
        override val interfaceMethods by lazy {
            source.interfaceMethods.map { it.copy(signature = types(it.signature) as GoIRFuncType, enclosingInterface = this) }
        }
        override val embeddedInterfaces by lazy {
            source.embeddedInterfaces.map { findPackage(it.pkg.importPath)?.findNamedType(it.name) ?: it }
        }
        override fun allMethods() = methods + pointerMethods
        override fun methodByName(name: String) = allMethods().find { it.name == name }
    }
}

private class LookupTypes(
    private val program: GoIRProgram,
    originalInterfaces: Map<Int, GoIRAnonymousInterfaceType>,
) : (GoIRType) -> GoIRType {
    private val memo = IdentityHashMap<GoIRType, GoIRType>()

    // Anonymous-interface IDs are local to each deserialized program. Intern structural
    // definitions so model-only interfaces cannot collide with program interface IDs.
    private val interfaces = originalInterfaces.toMutableMap()
    private val interfaceIds = mutableMapOf<GoIRAnonymousInterfaceType, Int>().apply {
        originalInterfaces.forEach { (id, type) -> putIfAbsent(type, id) }
    }
    private var nextInterfaceId = (originalInterfaces.keys.maxOrNull() ?: -1) + 1

    val anonymousInterfaces: Map<Int, GoIRAnonymousInterfaceType> =
        object : AbstractMap<Int, GoIRAnonymousInterfaceType>() {
            override fun get(key: Int): GoIRAnonymousInterfaceType? = synchronized(memo) {
                interfaces[key]?.let { invoke(it) as GoIRAnonymousInterfaceType }
            }
            override val entries: Set<Map.Entry<Int, GoIRAnonymousInterfaceType>>
                get() = synchronized(memo) {
                    interfaces.toMap().mapValues { invoke(it.value) as GoIRAnonymousInterfaceType }.entries
                }
        }

    private fun interfaceId(type: GoIRAnonymousInterfaceType): Int = interfaceIds.getOrPut(type) {
        nextInterfaceId++.also { interfaces[it] = type }
    }

    private fun reference(ref: NamedTypeRef) = ref.copy().also { it.program = program }

    override fun invoke(type: GoIRType): GoIRType = synchronized(memo) {
        memo[type]?.let { return it }
        val result = when (type) {
            is GoIRTypeParamType -> type.copy(constraint = invoke(type.constraint))
            is GoIRTypeParamRef -> type
            is GoIRPointerType -> GoIRPointerType(invoke(type.elem))
            is GoIRArrayType -> GoIRArrayType(invoke(type.elem), type.length)
            is GoIRSliceType -> GoIRSliceType(invoke(type.elem))
            is GoIRMapType -> GoIRMapType(invoke(type.key), invoke(type.value))
            is GoIRChanType -> GoIRChanType(invoke(type.elem), type.direction)
            is GoIRStructType -> GoIRStructType(
                type.fields.map { GoIRStructField(it.name, invoke(it.type), it.isEmbedded, it.tag) },
                type.structTypeRef?.let(::reference),
            )
            is GoIRNamedTypeRef -> GoIRNamedTypeRef(reference(type.typeRef), type.typeArgs.map(::invoke))
            is GoIRNamedInterfaceType -> GoIRNamedInterfaceType(
                type.methods.map { GoIRInterfaceMethodSig(it.name, invoke(it.signature) as GoIRFuncType) },
                type.embeds.map(::invoke),
                reference(type.interfaceTypeRef),
            )
            is GoIRAnonymousInterfaceTypeRef -> GoIRAnonymousInterfaceTypeRef(interfaceId(type.interfaceType))
                .also { it.program = program }
            is GoIRAnonymousInterfaceType -> GoIRAnonymousInterfaceType(
                interfaceId(type),
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
        result
    }
}
