package org.opentaint.ir.impl.types

import org.opentaint.ir.api.JIRClassOrInterface
import org.opentaint.ir.api.JIRMethod
import org.opentaint.ir.api.JIRRefType
import org.opentaint.ir.api.JIRType
import org.opentaint.ir.api.JIRTypeVariableDeclaration
import org.opentaint.ir.api.JIRTypedMethod
import org.opentaint.ir.api.JIRTypedMethodParameter
import org.opentaint.ir.api.MethodResolution
import org.opentaint.ir.api.ext.findClass
import org.opentaint.ir.api.ext.isNullable
import org.opentaint.ir.api.throwClassNotFound
import org.opentaint.ir.impl.types.signature.FieldResolutionImpl
import org.opentaint.ir.impl.types.signature.FieldSignature
import org.opentaint.ir.impl.types.signature.MethodResolutionImpl
import org.opentaint.ir.impl.types.signature.MethodSignature
import org.opentaint.ir.impl.types.substition.JIRSubstitutor
import org.objectweb.asm.Type
import org.objectweb.asm.tree.LocalVariableNode

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

    override val access: Int
        get() = this.method.access

    private val info by lazy(LazyThreadSafetyMode.PUBLICATION) {
        val signature = MethodSignature.withDeclarations(method)
        val impl = signature as? MethodResolutionImpl
        val substitutor = if (!method.isStatic) {
            parentSubstitutor.newScope(impl?.typeVariables.orEmpty())
        } else {
            JIRSubstitutor.empty.newScope(impl?.typeVariables.orEmpty())
        }

        TypedMethodInfo(
            substitutor = substitutor,
            resolution = signature
        )
    }

    override val name: String
        get() = method.name

    override val typeParameters: List<JIRTypeVariableDeclaration>
        get() {
            val impl = info.impl ?: return emptyList()
            return impl.typeVariables.map { it.asJIRDeclaration(method) }
        }

    override val exceptions: List<JIRClassOrInterface>
        get() {
            val impl = info.impl ?: return emptyList()
            return impl.exceptionTypes.map {
                classpath.findClass(it.name)
            }
        }

    override val typeArguments: List<JIRRefType>
        get() {
            return emptyList()
        }

    override val parameters: List<JIRTypedMethodParameter>
        get() {
            val methodInfo = info
            return method.parameters.mapIndexed { index, jIRParameter ->
                JIRTypedMethodParameterImpl(
                    enclosingMethod = this,
                    substitutor = methodInfo.substitutor,
                    parameter = jIRParameter,
                    jvmType = methodInfo.impl?.parameterTypes?.get(index)
                )
            }
        }

    override val returnType: JIRType by lazy(LazyThreadSafetyMode.PUBLICATION) {
        val typeName = method.returnType.typeName
        val info = info
        val impl = info.impl
        val type = if (impl == null) {
            classpath.findTypeOrNull(typeName)
                ?: throw IllegalStateException("Can't resolve type by name $typeName")
        } else {
            classpath.typeOf(info.substitutor.substitute(impl.returnType))
        }

        method.isNullable?.let {
            (type as? JIRRefType)?.copyWithNullability(it)
        } ?: type
    }

    override fun typeOf(inst: LocalVariableNode): JIRType {
        val variableSignature =
            FieldSignature.of(inst.signature, method.allVisibleTypeParameters(), null) as? FieldResolutionImpl
        if (variableSignature == null) {
            val type = Type.getType(inst.desc)
            return classpath.findTypeOrNull(type.className) ?: type.className.throwClassNotFound()
        }
        val info = info
        return classpath.typeOf(info.substitutor.substitute(variableSignature.fieldType))
    }

}