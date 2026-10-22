package org.opentaint.dataflow.go

import org.opentaint.dataflow.ifds.UnitResolver
import org.opentaint.dataflow.ifds.UnknownUnit
import org.opentaint.dataflow.util.copy
import org.opentaint.dataflow.util.forEach
import org.opentaint.ir.go.api.GoIRFunction
import org.opentaint.ir.go.api.GoIRNamedType
import org.opentaint.ir.go.api.GoIRProgram
import org.opentaint.ir.go.cfg.GoIRCallInfo
import org.opentaint.ir.go.cfg.GoIRCallTarget
import org.opentaint.ir.go.inst.GoIRInst
import org.opentaint.ir.go.type.GoIRCallMode
import org.opentaint.ir.go.type.GoIRNamedInterfaceType
import org.opentaint.ir.go.type.GoIRNamedTypeKind
import org.opentaint.ir.go.type.GoIRNamedTypeRef
import org.opentaint.ir.go.type.GoIRType
import org.opentaint.ir.go.value.GoIRRegister
import java.util.BitSet

/**
 * Low-level call resolver for Go. Handles DIRECT, INVOKE, and DYNAMIC call modes.
 */
class GoCallResolver(
    val cp: GoIRProgram,
    private val unitResolver: UnitResolver<GoIRFunction>
) {
    /**
     * Pre-computed: for each interface (by fullName), all concrete types implementing it.
     */
    private val interfaceImplementors: Map<String, List<GoIRNamedType>> by lazy {
        buildInterfaceImplementorsMap()
    }

    fun resolve(call: GoIRCallInfo, location: GoIRInst): List<GoIRFunction>? {
        val candidates = when (call.mode) {
            GoIRCallMode.DIRECT -> resolveDirect(call)
            GoIRCallMode.INVOKE -> resolveInvoke(call)
            GoIRCallMode.DYNAMIC -> resolveDynamic(call, location)
        }

        return candidates
            ?.filter { unitResolver.resolve(it) != UnknownUnit }
            ?.takeIf { it.isNotEmpty() }
    }

    private fun resolveDirect(call: GoIRCallInfo): List<GoIRFunction>? {
        val target = call.target as? GoIRCallTarget.Function ?: return null
        return listOf(target.function)
    }

    private fun resolveDynamic(call: GoIRCallInfo, location: GoIRInst): List<GoIRFunction>? {
        val target = call.target ?: return null
        when (target) {
            is GoIRCallTarget.Function -> return listOf(target.function)
            is GoIRCallTarget.Dynamic -> {
                val method = location.location.functionBody.function
                val funcValue = target.value as? GoIRRegister ?: return null

                val closure = GoFlowFunctionUtils.findMakeClosureExpr(funcValue, method)
                    ?: return null

                return listOf(closure.fn)
            }
            is GoIRCallTarget.Builtin -> return null
        }
    }

    private fun resolveInvoke(call: GoIRCallInfo): List<GoIRFunction>? {
        val methodName = call.methodName ?: return null
        val receiverType = call.receiver?.type ?: return null

        val interfaceFullName = resolveInterfaceFullName(receiverType) ?: return null
        val implementors = interfaceImplementors[interfaceFullName] ?: return null

        return implementors.mapNotNull { concreteType ->
            concreteType.methodByName(methodName)
        }
    }

    private fun resolveInterfaceFullName(type: GoIRType): String? {
        return when (type) {
            is GoIRNamedTypeRef -> {
                val named = type.namedType
                if (named.kind == GoIRNamedTypeKind.INTERFACE) named.fullName else null
            }
            is GoIRNamedInterfaceType -> type.namedType.fullName
            else -> null
        }
    }

    private data class MethodDescription(val name: String, val paramsCount: Int)

    private fun buildInterfaceImplementorsMap(): Map<String, List<GoIRNamedType>> = synchronized(cp) {
        val allTypes = cp.allNamedTypes().toList()
        val interfaces = allTypes.filter { it.kind == GoIRNamedTypeKind.INTERFACE }
        val concreteTypes = allTypes.filter { it.kind != GoIRNamedTypeKind.INTERFACE }

        val concreteTypesMethodsIndex by lazy {
            val methodTypeIndex = hashMapOf<MethodDescription, BitSet>()
            for ((i, type) in concreteTypes.withIndex()) {
                for (method in type.allMethods()) {
                    val methodDesc = MethodDescription(method.name, methodParamCount(method))
                    methodTypeIndex.getOrPut(methodDesc, ::BitSet).set(i)
                }
            }
            methodTypeIndex
        }

        interfaces.associate { iface ->
            val requiredMethods = collectInterfaceMethodSignatures(iface)
            if (requiredMethods.isEmpty()) {
                return@associate iface.fullName to emptyList()
            }

            val allRelevantConcreteTypeIndices = requiredMethods.map { (name, ifaceParamCount) ->
                concreteTypesMethodsIndex[MethodDescription(name, ifaceParamCount)]
                    ?: return@associate iface.fullName to emptyList()
            }

            val concreteTypeIndices = allRelevantConcreteTypeIndices.first().copy()
            allRelevantConcreteTypeIndices.forEach {
                concreteTypeIndices.and(it)
            }

            val implementors = mutableListOf<GoIRNamedType>()
            concreteTypeIndices.forEach {
                implementors.add(concreteTypes[it])
            }

            iface.fullName to implementors
        }
    }

    /**
     * Collects interface method signatures as (name, paramCount) pairs.
     * paramCount is the number of parameters EXCLUDING the receiver
     * (i.e., the interface method signature's params).
     */
    private fun collectInterfaceMethodSignatures(iface: GoIRNamedType): Set<Pair<String, Int>> {
        val methods = mutableSetOf<Pair<String, Int>>()
        for (m in iface.interfaceMethods.toList()) {
            methods.add(m.name to m.signature.params.size)
        }
        for (embed in iface.embeddedInterfaces.toList()) {
            methods += collectInterfaceMethodSignatures(embed)
        }
        return methods
    }

    /**
     * Returns the number of non-receiver parameters of a concrete method.
     * For methods (isMethod=true), params includes the receiver as first element,
     * so non-receiver param count = params.size - 1.
     * For non-method functions, it's just params.size.
     */
    private fun methodParamCount(fn: GoIRFunction): Int {
        return if (fn.isMethod) fn.params.size - 1 else fn.params.size
    }
}
