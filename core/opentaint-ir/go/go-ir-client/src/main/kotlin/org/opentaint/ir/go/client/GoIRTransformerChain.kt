package org.opentaint.ir.go.client

import org.opentaint.ir.go.api.*
import org.opentaint.ir.go.type.*

/** Lookup view. Only finite package members and individual lookup results retain resolved values. */
class GoIRTransformerChain(
    private val source: GoIRProgram,
    private val transformers: List<GoIRTransformer>,
) : GoIRProgram {
    override val originalProgram: GoIRProgram get() = source.originalProgram
    private val types = LookupTypes(this, source.anonymousInterfaces, ::path)
    private fun path(value: String) = transformers.fold(value) { v, transformer -> transformer.packagePath(v) }
    private fun symbol(value: String) = transformers.fold(value) { v, transformer -> transformer.symbolName(v) }
    private val additionalPackages by lazy {
        transformers.flatMap { it.additionalPackages }.associateBy { it.importPath }
    }
    override val lookup: GoIRLookup = GoIRProgramLookup(this)

    override val packages: Map<String, GoIRPackage> by lazy {
        (source.packages + additionalPackages).entries
            .sortedBy { if (path(it.key) == it.key) 0 else 1 }
            .associate { (key, original) ->
                val resolvedPath = path(key)
                resolvedPath to lazy {
                    fun resolve(index: Int): GoIRPackage? = if (index < 0) original else
                        transformers[index].resolvePackage(resolvedPath) { resolve(index - 1) }
                    PackageView(resolve(transformers.lastIndex) ?: original)
                }
            }.let { entries ->
            object : AbstractMap<String, GoIRPackage>() {
                override fun get(key: String): GoIRPackage? = entries[key]?.value
                override val entries: Set<Map.Entry<String, GoIRPackage>>
                    get() = entries.mapValues { it.value.value }.entries
            }
        }
    }
    override val anonymousInterfaces: Map<Int, GoIRAnonymousInterfaceType>
        get() = types.anonymousInterfaces

    override fun findPackage(importPath: String): GoIRPackage? = packages[importPath]

    override fun allFunctions(): List<GoIRFunction> = packages.values.flatMap { it.functions }
    override fun allNamedTypes(): List<GoIRNamedType> = packages.values.flatMap { it.namedTypes }
    override fun mainPackage(): GoIRPackage? = packages.values.find { it.name == "main" }

    private fun function(source: GoIRFunction): GoIRFunction {
        val reference = reference(source.reference())
        fun resolve(index: Int): GoIRFunction? = if (index < 0) source else
            transformers[index].resolveFunction(reference) { resolve(index - 1) }
        return when (val resolved = resolve(transformers.lastIndex) ?: source) {
            is GoIRApproximatedFunction -> ApproximationView(resolved)
            is GoIRModelFunction -> ModelFunctionView(resolved)
            else -> FunctionView(resolved, (resolved as? GoIRBodyReader) ?: (source as? GoIRBodyReader))
        }
    }

    private fun global(source: GoIRGlobal): GoIRGlobal = object : GoIRGlobal by source {
        override val pkg get() = findPackage(path(source.pkg.importPath))!!
        override val fullName get() = symbol(source.fullName)
        override val type by lazy { types(source.type) }
    }

    private fun reference(ref: GoIrFunctionReference): GoIrFunctionReference =
        ref.copy(pkg = path(ref.pkg), signature = types(ref.signature) as GoIRFuncType).also { it.program = this }

    private open inner class FunctionView(
        protected val source: GoIRFunction,
        private val reader: GoIRBodyReader? = source as? GoIRBodyReader,
    ) : GoIRFunction by source, GoIRBodyReader {
        override val fullName get() = symbol(source.fullName)
        override val originFullName get() = source.originFullName?.let(::symbol)
        override val pkg: GoIRPackage? get() = source.pkg?.importPath?.let { findPackage(path(it)) }
        override val signature by lazy { types(source.signature) as GoIRFuncType }
        override val params by lazy { source.params.map { it.copy(type = types(it.type)) } }
        override val freeVars by lazy { source.freeVars.map { it.copy(type = types(it.type)) } }
        override val typeParams by lazy { source.typeParams.map { it.copy(constraint = types(it.constraint)) } }
        override val typeArgs by lazy { source.typeArgs.map(types::invoke) }
        override val receiverType get() = signature.recv
        override val parent by lazy { source.parent?.let(::reference) }
        override val anonymousFunctions by lazy { source.anonymousFunctions.map(::reference) }
        override fun readBody(owner: GoIRFunction, context: GoIRBodyContext): GoIRBody? {
            val mapped = object : GoIRBodyContext() {
                override fun type(type: GoIRType) = context.type(types(type))
                override fun function(reference: GoIrFunctionReference) = context.function(reference(reference))
                override fun global(reference: GoIRGlobalReference) = context.global(
                    reference.copy(pkg = path(reference.pkg)).also { it.program = this@GoIRTransformerChain },
                )
                override fun fieldIndex(type: GoIRType, index: Int, name: String): Int {
                    val mappedType = types(type)
                    fun named(type: GoIRType): GoIRNamedType? = when (type) {
                        is GoIRPointerType -> named(type.elem)
                        is GoIRNamedTypeRef -> type.namedType
                        is GoIRStructType -> type.namedType
                        else -> null
                    }
                    val resolved = named(mappedType)?.fields?.firstOrNull { it.name == name }?.index ?: index
                    return context.fieldIndex(mappedType, resolved, name)
                }
            }
            return reader?.readBody(owner, mapped) ?: source.body
        }
        override val body by lazy { readBody(this, GoIRBodyContext()) }
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
        override val importPath get() = path(source.importPath)
        override val functions by lazy { source.functions.map(::function) }
        override val namedTypes by lazy { source.namedTypes.map { NamedTypeView(it, this) } }
        override val globals by lazy { source.globals.map(::global) }
        override val constants by lazy {
            source.constants.map { constant ->
                object : GoIRConst by constant {
                    override val pkg get() = this@PackageView
                    override val fullName get() = symbol(constant.fullName)
                    override val type by lazy { types(constant.type) }
                }
            }
        }
        override fun findGlobal(name: String) = globals.find { it.name == name }
        override fun findConstant(name: String) = constants.find { it.name == name }
        override val imports by lazy { source.imports.map { findPackage(path(it.importPath)) ?: it } }
        override val initFunction by lazy { source.initFunction?.let { reference(it.reference()).function } }
        override fun findNamedType(name: String) = namedTypes.find { it.name == name }
        override fun allMethods() = namedTypes.flatMap { it.allMethods() }
    }

    private inner class NamedTypeView(private val source: GoIRNamedType, override val pkg: GoIRPackage) :
        GoIRNamedType by source {
        override val fullName get() = symbol(source.fullName)
        override val underlying by lazy { types(source.underlying) }
        override val fields by lazy { source.fields.map { it.copy(type = types(it.type), enclosingType = this) } }
        override val methods by lazy { source.methods.map { reference(it.reference()).function } }
        override val pointerMethods by lazy { source.pointerMethods.map { reference(it.reference()).function } }
        override val typeParams by lazy { source.typeParams.map { it.copy(constraint = types(it.constraint)) } }
        override val interfaceMethods by lazy {
            source.interfaceMethods.map { it.copy(signature = types(it.signature) as GoIRFuncType, enclosingInterface = this) }
        }
        override val embeddedInterfaces by lazy {
            source.embeddedInterfaces.map { findPackage(path(it.pkg.importPath))?.findNamedType(it.name) ?: it }
        }
        override fun allMethods() = methods + pointerMethods
        override fun methodByName(name: String) = allMethods().find { it.name == name }
    }
}

private class LookupTypes(
    private val program: GoIRProgram,
    originalInterfaces: Map<Int, GoIRAnonymousInterfaceType>,
    private val path: (String) -> String,
) : (GoIRType) -> GoIRType {
    private val lock = Any()

    // This namespace registry holds interface definitions from the finite input programs.
    // It does not memoize transformed type graphs. IDs are local to each wire program,
    // so model definitions must be interned before entering the shared lookup namespace.
    private val interfaces = originalInterfaces.toMutableMap()
    private val interfaceIds = mutableMapOf<GoIRAnonymousInterfaceType, Int>().apply {
        originalInterfaces.forEach { (id, type) -> putIfAbsent(type, id) }
    }
    private var nextInterfaceId = (originalInterfaces.keys.maxOrNull() ?: -1) + 1

    val anonymousInterfaces: Map<Int, GoIRAnonymousInterfaceType> =
        object : AbstractMap<Int, GoIRAnonymousInterfaceType>() {
            override fun get(key: Int): GoIRAnonymousInterfaceType? = synchronized(lock) {
                interfaces[key]?.let { invoke(it) as GoIRAnonymousInterfaceType }
            }
            override val entries: Set<Map.Entry<Int, GoIRAnonymousInterfaceType>>
                get() = synchronized(lock) {
                    interfaces.toMap().mapValues { invoke(it.value) as GoIRAnonymousInterfaceType }.entries
                }
        }

    private fun interfaceId(type: GoIRAnonymousInterfaceType): Int = interfaceIds.getOrPut(type) {
        nextInterfaceId++.also { interfaces[it] = type }
    }

    private fun reference(ref: NamedTypeRef) = ref.copy(refTypePkg = path(ref.refTypePkg)).also { it.program = program }

    override fun invoke(type: GoIRType): GoIRType = synchronized(lock) {
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
        result
    }
}
