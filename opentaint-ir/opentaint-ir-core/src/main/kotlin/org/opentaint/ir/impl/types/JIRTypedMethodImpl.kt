package org.opentaint.ir.impl.types

import org.objectweb.asm.tree.LocalVariableNode
import org.opentaint.ir.api.JIRClassOrInterface
import org.opentaint.ir.api.JIRMethod
import org.opentaint.ir.api.JIRRefType
import org.opentaint.ir.api.JIRType
import org.opentaint.ir.api.JIRTypeVariableDeclaration
import org.opentaint.ir.api.JIRTypedMethod
import org.opentaint.ir.api.JIRTypedMethodParameter
import org.opentaint.ir.api.ext.findClass
import org.opentaint.ir.impl.signature.Formal
import org.opentaint.ir.impl.signature.MethodResolutionImpl
import org.opentaint.ir.impl.signature.MethodSignature
import org.opentaint.ir.impl.suspendableLazy

class JIRTypedMethodImpl(
    override val enclosingType: JIRRefType,
    override val method: JIRMethod,
    private val classBindings: JIRTypeBindings = JIRTypeBindings(emptyMap(), emptyMap())
) : JIRTypedMethod {

    private val resolution = MethodSignature.of(method.signature)
    private val classpath = method.enclosingClass.classpath

    private val methodBindingsGetter = suspendableLazy {
        classBindings.override(ifSignature {
            it.typeVariables
        }.orEmpty())
    }

    override val name: String
        get() = method.name

    private suspend fun methodBindings() = methodBindingsGetter()

    override suspend fun originalParameterization(): List<JIRTypeVariableDeclaration> {
        return ifSignature {
            classpath.typeDeclarations(it.typeVariables.map { Formal(it.symbol, it.boundTypeTokens?.map { it.apply(methodBindings(), null) }) }, JIRTypeBindings.empty)
        } ?: emptyList()
    }

    override suspend fun exceptions(): List<JIRClassOrInterface> = ifSignature {
        it.exceptionTypes.map {
            classpath.findClass(it.name)
        }
    } ?: emptyList()

    override suspend fun parameterization(): Map<String, JIRRefType> {
        return emptyMap()
    }

    override suspend fun parameters(): List<JIRTypedMethodParameter> {
        val bindings = methodBindings()
        return method.parameters.mapIndexed { index, jirParameter ->
            val stype = ifSignature { it.parameterTypes[index] }
            JIRTypedMethodParameterImpl(
                enclosingMethod = this,
                bindings = bindings,
                parameter = jirParameter,
                stype = stype
            )
        }
    }

    override suspend fun returnType(): JIRType {
        val typeName = method.returnType.typeName
        val bindings = methodBindings()
        return ifSignature {
            classpath.typeOf(it.returnType.apply(bindings, null), bindings)
        } ?: classpath.findTypeOrNull(typeName) ?: throw IllegalStateException("Can't resolve type by name $typeName")
    }

    private suspend fun <T> ifSignature(map: suspend (MethodResolutionImpl) -> T): T? {
        return when (resolution) {
            is MethodResolutionImpl -> map(resolution)
            else -> null
        }
    }

    override suspend fun typeOf(inst: LocalVariableNode): JIRType {
        TODO("Not yet implemented")
    }

}