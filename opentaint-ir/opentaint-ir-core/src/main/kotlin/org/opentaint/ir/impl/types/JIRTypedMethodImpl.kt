package org.opentaint.ir.impl.types

import org.opentaint.ir.api.jvm.*
import org.opentaint.ir.api.jvm.ext.findTypeOrNull
import org.opentaint.ir.api.jvm.ext.isEnum
import org.opentaint.ir.api.jvm.ext.isNullable
import org.opentaint.ir.impl.bytecode.JIRAnnotationImpl
import org.opentaint.ir.impl.bytecode.JIRMethodImpl
import org.opentaint.ir.impl.types.signature.FieldResolutionImpl
import org.opentaint.ir.impl.types.signature.FieldSignature
import org.opentaint.ir.impl.types.signature.MethodResolutionImpl
import org.opentaint.ir.impl.types.signature.MethodSignature
import org.opentaint.ir.impl.types.substition.JIRSubstitutorImpl
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
            JIRSubstitutorImpl.empty.newScope(impl?.typeVariables.orEmpty())
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

    override val exceptions: List<JIRRefType>
        get() {
            val typesFromSignature = info.impl?.exceptionTypes?.map {
                classpath.typeOf(info.substitutor.substitute(it)) as JIRRefType
            } ?: emptyList()

            return typesFromSignature.ifEmpty {
                method.exceptions.map { classpath.findTypeOrNull(it) as JIRRefType }
            }
        }

    override val typeArguments: List<JIRRefType>
        get() {
            return emptyList()
        }

    override val parameters: List<JIRTypedMethodParameter>
        get() {
            val methodInfo = info
            if (method.isConstructor && method.enclosingClass.isEnum) {
                return method.parameters.map { jIRParameter ->
                    JIRTypedMethodParameterImpl(
                        enclosingMethod = this,
                        substitutor = methodInfo.substitutor,
                        parameter = jIRParameter,
                        jvmType = null
                    )
                }
            }

            return method.parameters.mapIndexed { index, jIRParameter ->
                JIRTypedMethodParameterImpl(
                    enclosingMethod = this,
                    substitutor = methodInfo.substitutor,
                    parameter = jIRParameter,
                    jvmType = methodInfo.impl?.parameterTypes?.getOrNull(index)
                )
            }
        }

    override val returnType: JIRType by lazy(LazyThreadSafetyMode.PUBLICATION) {
        val typeName = method.returnType.typeName
        val info = info
        val impl = info.impl
        val type = if (impl == null) {
            classpath.findTypeOrNull(typeName)
                ?.copyWithAnnotations(
                    (method as? JIRMethodImpl)?.returnTypeAnnotationInfos?.map { JIRAnnotationImpl(it, classpath) }
                        ?: listOf()
                )
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

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is JIRTypedMethodImpl) return false

        if (enclosingType != other.enclosingType) return false
        if (method != other.method) return false
        return typeArguments == other.typeArguments
    }

    override fun hashCode(): Int {
        var result = enclosingType.hashCode()
        result = 31 * result + method.hashCode()
        return result
    }

}
