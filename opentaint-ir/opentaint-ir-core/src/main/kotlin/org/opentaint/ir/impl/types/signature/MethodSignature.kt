package org.opentaint.ir.impl.types.signature

import mu.KLogging
import org.opentaint.ir.api.JIRMethod
import org.opentaint.ir.api.Malformed
import org.opentaint.ir.api.MethodResolution
import org.opentaint.ir.api.Pure
import org.opentaint.ir.impl.bytecode.JIRMethodImpl
import org.opentaint.ir.impl.bytecode.kmFunction
import org.opentaint.ir.impl.bytecode.kmReturnType
import org.opentaint.ir.impl.bytecode.kmType
import org.opentaint.ir.impl.types.allVisibleTypeParameters
import org.opentaint.ir.impl.types.substition.RecursiveJvmTypeVisitor
import org.opentaint.ir.impl.types.substition.fixDeclarationVisitor
import org.objectweb.asm.signature.SignatureVisitor

val logger = object : KLogging() {}.logger

internal class MethodSignature(private val method: JIRMethod) :
    Signature<MethodResolution>(method, method.kmFunction?.typeParameters) {

    private val parameterTypes = ArrayList<JvmType>()
    private val exceptionTypes = ArrayList<JvmRefType>()
    private lateinit var returnType: JvmType

    override fun visitParameterType(): SignatureVisitor {
        return TypeExtractor(ParameterTypeRegistrant())
    }

    override fun visitReturnType(): SignatureVisitor {
        collectTypeParameter()
        return TypeExtractor(ReturnTypeTypeRegistrant())
    }

    override fun visitExceptionType(): SignatureVisitor {
        return TypeExtractor(ExceptionTypeRegistrant())
    }

    override fun resolve(): MethodResolution {
        return MethodResolutionImpl(
            returnType,
            parameterTypes,
            exceptionTypes,
            typeVariables
        )
    }

    private inner class ParameterTypeRegistrant : TypeRegistrant {
        override fun register(token: JvmType) {
            var outToken = method.parameters[parameterTypes.size].kmType?.let {
                JvmTypeKMetadataUpdateVisitor.visitType(token, it)
            } ?: token

            (method as? JIRMethodImpl)?.let {
                outToken = outToken.withTypeAnnotations(it.parameterTypeAnnotationInfos(parameterTypes.size), it.enclosingClass.classpath)
            }

            parameterTypes.add(outToken)
        }
    }

    private inner class ReturnTypeTypeRegistrant : TypeRegistrant {
        override fun register(token: JvmType) {
            returnType = method.kmReturnType?.let { JvmTypeKMetadataUpdateVisitor.visitType(token, it) } ?: token

            (method as? JIRMethodImpl)?.let {
                returnType = returnType.withTypeAnnotations(it.returnTypeAnnotationInfos, it.enclosingClass.classpath)
            }
        }
    }

    private inner class ExceptionTypeRegistrant : TypeRegistrant {
        override fun register(token: JvmType) {
            exceptionTypes.add(token as JvmRefType)
        }
    }

    companion object : KLogging() {

        private fun MethodResolutionImpl.apply(visitor: RecursiveJvmTypeVisitor) = MethodResolutionImpl(
            visitor.visitType(returnType),
            parameterTypes.map { visitor.visitType(it) },
            exceptionTypes,
            typeVariables.map { visitor.visitDeclaration(it) }
        )

        fun of(jIRMethod: JIRMethod): MethodResolution {
            val signature = jIRMethod.signature
            signature ?: return Pure
            return try {
                of(signature, MethodSignature(jIRMethod))
            } catch (ignored: RuntimeException) {
                Malformed
            }
        }

        fun withDeclarations(jIRMethod: JIRMethod): MethodResolution {
            val signature = jIRMethod.signature
            signature ?: return Pure
            return try {
                of(signature, MethodSignature(jIRMethod)).let {
                    if (it is MethodResolutionImpl) {
                        it.apply(jIRMethod.allVisibleTypeParameters().fixDeclarationVisitor)
                    } else {
                        it
                    }
                }
            } catch (ignored: RuntimeException) {
                logger.warn(ignored) { "Can't parse signature '$signature' of field $jIRMethod" }
                Malformed
            }
        }
    }
}