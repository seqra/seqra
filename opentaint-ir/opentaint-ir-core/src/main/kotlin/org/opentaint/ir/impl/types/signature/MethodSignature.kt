package org.opentaint.opentaint-ir.impl.types.signature

import org.objectweb.asm.signature.SignatureVisitor
import org.opentaint.opentaint-ir.api.JIRMethod
import org.opentaint.opentaint-ir.api.Malformed
import org.opentaint.opentaint-ir.api.MethodResolution
import org.opentaint.opentaint-ir.api.Pure
import org.opentaint.opentaint-ir.impl.bytecode.kmFunction
import org.opentaint.opentaint-ir.impl.bytecode.kmReturnType
import org.opentaint.opentaint-ir.impl.bytecode.kmType
import org.opentaint.opentaint-ir.impl.types.allVisibleTypeParameters
import org.opentaint.opentaint-ir.impl.types.substition.JvmTypeVisitor
import org.opentaint.opentaint-ir.impl.types.substition.fixDeclarationVisitor

internal class MethodSignature(private val method: JIRMethod) : Signature<MethodResolution>(method, method.kmFunction?.typeParameters) {

    private val parameterTypes = ArrayList<JvmType>()
    private val exceptionTypes = ArrayList<JvmClassRefType>()

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
            val outToken = method.parameters[parameterTypes.size].kmType?.let {
                token.relaxWithKmType(it)
            } ?: token
            parameterTypes.add(outToken)
        }
    }

    private inner class ReturnTypeTypeRegistrant : TypeRegistrant {
        override fun register(token: JvmType) {
            returnType = token
            method.kmReturnType?.let {
                returnType = returnType.relaxWithKmType(it)
            }
        }
    }

    private inner class ExceptionTypeRegistrant : TypeRegistrant {
        override fun register(token: JvmType) {
            exceptionTypes.add(token as JvmClassRefType)
        }
    }

    companion object {

        private fun MethodResolutionImpl.apply(visitor: JvmTypeVisitor) = MethodResolutionImpl(
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
                Malformed
            }
        }
    }
}