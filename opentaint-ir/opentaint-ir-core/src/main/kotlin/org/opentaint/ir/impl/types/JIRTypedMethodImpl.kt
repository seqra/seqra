package org.opentaint.ir.impl.types

import org.objectweb.asm.Type
import org.objectweb.asm.tree.LocalVariableNode
import org.opentaint.ir.api.JIRClassOrInterface
import org.opentaint.ir.api.JIRMethod
import org.opentaint.ir.api.JIRRefType
import org.opentaint.ir.api.JIRType
import org.opentaint.ir.api.JIRTypeVariableDeclaration
import org.opentaint.ir.api.JIRTypedMethod
import org.opentaint.ir.api.JIRTypedMethodParameter
import org.opentaint.ir.api.ext.findClass
import org.opentaint.ir.api.throwClassNotFound
import org.opentaint.ir.impl.signature.FieldResolutionImpl
import org.opentaint.ir.impl.signature.FieldSignature
import org.opentaint.ir.impl.signature.Formal
import org.opentaint.ir.impl.signature.MethodResolutionImpl
import org.opentaint.ir.impl.signature.MethodSignature

class JIRTypedMethodImpl(
    override val enclosingType: JIRRefType,
    override val method: JIRMethod,
    classBindings: JIRTypeBindings
) : JIRTypedMethod {

    private val resolution = MethodSignature.of(method.signature) as? MethodResolutionImpl
    private val classpath = method.enclosingClass.classpath

    override val name: String
        get() = method.name

    private val methodBindings = classBindings.override(resolution?.typeVariables.orEmpty())

    override suspend fun originalParameterization(): List<JIRTypeVariableDeclaration> {
        if (resolution == null) {
            return emptyList()
        }
        return classpath.typeDeclarations(resolution.typeVariables.map {
            Formal(it.symbol, it.boundTypeTokens?.map { it.apply(methodBindings, null) })
        }, JIRTypeBindings.empty)
    }

    override suspend fun exceptions(): List<JIRClassOrInterface> = resolution?.exceptionTypes?.map {
        classpath.findClass(it.name)
    } ?: emptyList()

    override suspend fun parameterization(): Map<String, JIRRefType> {
        return emptyMap()
    }

    override suspend fun parameters(): List<JIRTypedMethodParameter> {
        return method.parameters.mapIndexed { index, jirParameter ->
            val stype = resolution?.parameterTypes?.get(index)
            JIRTypedMethodParameterImpl(
                enclosingMethod = this,
                bindings = methodBindings,
                parameter = jirParameter,
                stype = stype
            )
        }
    }

    override suspend fun returnType(): JIRType {
        val typeName = method.returnType.typeName
        if(resolution == null) {
            return classpath.findTypeOrNull(typeName)
                ?: throw IllegalStateException("Can't resolve type by name $typeName")
        }
        return methodBindings.toJcRefType(resolution.returnType, classpath)
    }

    override suspend fun typeOf(inst: LocalVariableNode): JIRType {
        val variableSignature = FieldSignature.of(inst.signature) as? FieldResolutionImpl
        if (variableSignature == null) {
            val type = Type.getType(inst.desc)
            return classpath.findTypeOrNull(type.className) ?: type.className.throwClassNotFound()
        }
        return methodBindings.toJcRefType(variableSignature.fieldType, classpath)
    }

}