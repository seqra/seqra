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
import org.opentaint.ir.api.isStatic
import org.opentaint.ir.api.throwClassNotFound
import org.opentaint.ir.impl.types.signature.FieldResolutionImpl
import org.opentaint.ir.impl.types.signature.FieldSignature
import org.opentaint.ir.impl.types.signature.MethodResolutionImpl
import org.opentaint.ir.impl.types.signature.MethodSignature
import org.opentaint.ir.impl.types.substition.JIRSubstitutor

class JIRTypedMethodImpl(
    override val enclosingType: JIRRefType,
    override val method: JIRMethod,
    jirSubstitutor: JIRSubstitutor
) : JIRTypedMethod {

    private val resolution = MethodSignature.of(method)
    private val impl = resolution as? MethodResolutionImpl
    private val classpath = method.enclosingClass.classpath

    override val name: String
        get() = method.name

    private val substitutor = resolveSubstitutor(jirSubstitutor)

    private fun resolveSubstitutor(parent: JIRSubstitutor): JIRSubstitutor {
        return if (!method.isStatic) {
            parent.newScope(impl?.typeVariables.orEmpty())
        } else {
            JIRSubstitutor.empty.newScope(impl?.typeVariables.orEmpty())
        }
    }

    override suspend fun typeParameters(): List<JIRTypeVariableDeclaration> {
        if (impl == null) {
            return emptyList()
        }
        return impl.typeVariables.map { it.asJcDeclaration(method) }
    }

    override suspend fun exceptions(): List<JIRClassOrInterface> = impl?.exceptionTypes?.map {
        classpath.findClass(it.name)
    } ?: emptyList()

    override suspend fun typeArguments(): List<JIRRefType> {
        return emptyList()
    }

    override suspend fun parameters(): List<JIRTypedMethodParameter> {
        return method.parameters.mapIndexed { index, jirParameter ->
            val stype = impl?.parameterTypes?.get(index)
            JIRTypedMethodParameterImpl(
                enclosingMethod = this,
                substitutor = substitutor,
                parameter = jirParameter,
                jvmType = stype
            )
        }
    }

    override suspend fun returnType(): JIRType {
        val typeName = method.returnType.typeName
        if (impl == null) {
            return classpath.findTypeOrNull(typeName)
                ?: throw IllegalStateException("Can't resolve type by name $typeName")
        }
        return classpath.typeOf(substitutor.substitute(impl.returnType))
    }

    override suspend fun typeOf(inst: LocalVariableNode): JIRType {
        val variableSignature = FieldSignature.of(inst.signature) as? FieldResolutionImpl
        if (variableSignature == null) {
            val type = Type.getType(inst.desc)
            return classpath.findTypeOrNull(type.className) ?: type.className.throwClassNotFound()
        }
        return classpath.typeOf(substitutor.substitute(variableSignature.fieldType))
    }

}