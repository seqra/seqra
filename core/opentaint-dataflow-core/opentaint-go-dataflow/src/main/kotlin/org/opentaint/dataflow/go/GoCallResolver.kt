package org.opentaint.dataflow.go

import org.opentaint.ir.go.api.GoIRFunction
import org.opentaint.ir.go.api.GoIRNamedType
import org.opentaint.ir.go.api.GoIRProgram
import org.opentaint.ir.go.cfg.GoIRCallInfo
import org.opentaint.ir.go.inst.GoIRInst
import org.opentaint.ir.go.type.GoIRCallMode
import org.opentaint.ir.go.type.GoIRInterfaceType
import org.opentaint.ir.go.type.GoIRNamedTypeKind
import org.opentaint.ir.go.type.GoIRNamedTypeRef
import org.opentaint.ir.go.type.GoIRType
import org.opentaint.ir.go.value.GoIRFunctionValue
import org.opentaint.ir.go.value.GoIRRegister

/**
 * Low-level call resolver for Go. Handles DIRECT, INVOKE, and DYNAMIC call modes.
 */
class GoCallResolver(val cp: GoIRProgram) {

    /**
     * Pre-computed: for each interface (by fullName), all concrete types implementing it.
     */
    private val interfaceImplementors: Map<String, List<GoIRNamedType>> by lazy {
        buildInterfaceImplementorsMap()
    }

    fun resolve(call: GoIRCallInfo, location: GoIRInst): List<GoIRFunction> {
        return when (call.mode) {
            GoIRCallMode.DIRECT -> resolveDirect(call)
            GoIRCallMode.INVOKE -> resolveInvoke(call)
            GoIRCallMode.DYNAMIC -> resolveDynamic(call, location)
        }
    }

    private fun resolveDirect(call: GoIRCallInfo): List<GoIRFunction> {
        val funcValue = call.function as? GoIRFunctionValue ?: return emptyList()
        return listOf(funcValue.function)
    }

    private fun resolveDynamic(call: GoIRCallInfo, location: GoIRInst): List<GoIRFunction> {
        // Trace the called function value back to its defining instruction.
        // If it was produced by MakeClosureExpr, resolve to the closure's function.
        val funcValue = call.function
        if (funcValue is GoIRFunctionValue) {
            return listOf(funcValue.function)
        }
        if (funcValue is GoIRRegister) {
            val method = location.location.functionBody.function
            val closure = GoFlowFunctionUtils.findMakeClosureExpr(funcValue, method)
            if (closure != null) {
                return listOf(closure.fn)
            }
        }
        return emptyList()
    }

    private fun resolveInvoke(call: GoIRCallInfo): List<GoIRFunction> {
        val methodName = call.methodName ?: return emptyList()
        val receiverType = call.receiver?.type ?: return emptyList()

        val interfaceFullName = resolveInterfaceFullName(receiverType) ?: return emptyList()
        val implementors = interfaceImplementors[interfaceFullName] ?: return emptyList()

        return implementors.mapNotNull { concreteType ->
            concreteType.methodByName(methodName)
        }
    }

    private fun resolveInterfaceFullName(type: GoIRType): String? {
        return when (type) {
            is GoIRNamedTypeRef -> {
                if (type.namedType.kind == GoIRNamedTypeKind.INTERFACE) type.namedType.fullName
                else null
            }
            is GoIRInterfaceType -> type.namedType?.fullName
            else -> null
        }
    }

    private fun buildInterfaceImplementorsMap(): Map<String, List<GoIRNamedType>> {
        val allTypes = cp.allNamedTypes()
        val interfaces = allTypes.filter { it.kind == GoIRNamedTypeKind.INTERFACE }
        val concreteTypes = allTypes.filter { it.kind != GoIRNamedTypeKind.INTERFACE }

        return interfaces.associate { iface ->
            val requiredMethods = collectInterfaceMethodNames(iface)
            val implementors = if (requiredMethods.isEmpty()) {
                emptyList() // empty interface matches everything — skip for performance
            } else {
                concreteTypes.filter { concrete ->
                    val concreteMethods = concrete.allMethods().map { it.name }.toSet()
                    concreteMethods.containsAll(requiredMethods)
                }
            }
            iface.fullName to implementors
        }
    }

    private fun collectInterfaceMethodNames(iface: GoIRNamedType): Set<String> {
        val methods = mutableSetOf<String>()
        iface.interfaceMethods.mapTo(methods) { it.name }
        for (embed in iface.embeddedInterfaces) {
            methods += collectInterfaceMethodNames(embed)
        }
        return methods
    }
}
