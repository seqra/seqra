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
import org.opentaint.ir.api.MethodResolution
import org.opentaint.ir.api.ext.findClass
import org.opentaint.ir.api.isStatic
import org.opentaint.ir.api.throwClassNotFound
import org.opentaint.ir.impl.suspendableLazy
import org.opentaint.ir.impl.types.signature.FieldResolutionImpl
import org.opentaint.ir.impl.types.signature.FieldSignature
import org.opentaint.ir.impl.types.signature.MethodResolutionImpl
import org.opentaint.ir.impl.types.signature.MethodSignature
import org.opentaint.ir.impl.types.substition.JIRSubstitutor

class JIRTypedMethodImpl(
    override val enclosingType: JIRRefType,
    override val method: JIRMethod,
    private val parentSubstitutor: JIRSubstitutor
) : JIRTypedMethod {

    private class TypedMethodInfo(
        val substitutor: JIRSubstitutor,
        private val resolution: MethodResolution
    ) {
        val impl: MethodResolutionImpl? get() = resolution as? MethodResolutionImpl
    }

    private val classpath = method.enclosingClass.classpath

    private val infoGetter = suspendableLazy {
        val signature = MethodSignature.withDeclarations(method)
        val impl = signature as? MethodResolutionImpl
        val substitutor = if (!method.isStatic) {
            parentSubstitutor.newScope(impl?.typeVariables.orEmpty())
        } else {
            JIRSubstitutor.empty.newScope(impl?.typeVariables.orEmpty())
        }

        TypedMethodInfo(
            substitutor = substitutor,
            resolution = MethodSignature.withDeclarations(method)
        )
    }

    override val name: String
        get() = method.name

    override suspend fun typeParameters(): List<JIRTypeVariableDeclaration> {
        val impl = infoGetter().impl ?: return emptyList()
        return impl.typeVariables.map { it.asJcDeclaration(method) }
    }

    override suspend fun exceptions(): List<JIRClassOrInterface> {
        val impl = infoGetter().impl ?: return emptyList()
        return impl.exceptionTypes.map {
            classpath.findClass(it.name)
        }
    }

    override suspend fun typeArguments(): List<JIRRefType> {
        return emptyList()
    }

    override suspend fun parameters(): List<JIRTypedMethodParameter> {
        val methodInfo = infoGetter()
        return method.parameters.mapIndexed { index, jirParameter ->
            JIRTypedMethodParameterImpl(
                enclosingMethod = this,
                substitutor = methodInfo.substitutor,
                parameter = jirParameter,
                jvmType = methodInfo.impl?.parameterTypes?.get(index)
            )
        }
    }

    override suspend fun returnType(): JIRType {
        val typeName = method.returnType.typeName
        val info = infoGetter()
        val impl = info.impl ?: return classpath.findTypeOrNull(typeName)
            ?: throw IllegalStateException("Can't resolve type by name $typeName")
        return classpath.typeOf(info.substitutor.substitute(impl.returnType))
    }

    override suspend fun typeOf(inst: LocalVariableNode): JIRType {
        val variableSignature = FieldSignature.of(inst.signature, method.allVisibleTypeParameters()) as? FieldResolutionImpl
        if (variableSignature == null) {
            val type = Type.getType(inst.desc)
            return classpath.findTypeOrNull(type.className) ?: type.className.throwClassNotFound()
        }
        val info = infoGetter()
        return classpath.typeOf(info.substitutor.substitute(variableSignature.fieldType))
    }

}