package org.opentaint.ir.go.client

import org.opentaint.ir.go.api.GoIRFunction
import org.opentaint.ir.go.api.GoIRParameter
import org.opentaint.ir.go.api.GoIRFreeVar
import org.opentaint.ir.go.api.GoIRProgram
import org.opentaint.ir.go.api.GoIRTypeParamDecl
import org.opentaint.ir.go.api.GoIrFunctionReference
import org.opentaint.ir.go.impl.GoIRFunctionImpl
import org.opentaint.ir.go.type.*
import java.util.IdentityHashMap

internal data class SpecializedGoModelFunction(
    val root: GoIRFunction,
    val anonymousFunctions: List<GoIRFunction>,
)

/** Substitutes concrete type arguments and closure lookups while reading a generic model body. */
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
            syntheticKind = modelRoot.syntheticKind,
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
                syntheticKind = source.syntheticKind,
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
            val reader = source as? GoIRBodyReader
                ?: error("Go model ${source.fullName} has no body reader")
            copied.setBodyReader { owner, context ->
                reader.readBody(owner, object : GoIRBodyContext() {
                    override fun type(type: GoIRType) = context.type(specializeType(type))
                    override fun function(ref: GoIrFunctionReference): GoIrFunctionReference {
                        val replacement = functionMap[ref.function]
                        return context.function(if (replacement == null) ref else reference(replacement))
                    }
                    override fun global(ref: org.opentaint.ir.go.api.GoIRGlobalReference) = context.global(ref)
                    override fun fieldIndex(type: GoIRType, index: Int, name: String) =
                        context.fieldIndex(specializeType(type), index, name)
                }) ?: error("Go model ${source.fullName} has no body")
            }
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

}

private class TypeSpecializer(
    private val arguments: Map<Int, GoIRType>,
) : (GoIRType) -> GoIRType {
    override fun invoke(type: GoIRType): GoIRType {
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
        return result
    }
}
