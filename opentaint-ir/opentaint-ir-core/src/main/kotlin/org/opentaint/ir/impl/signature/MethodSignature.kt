package org.opentaint.ir.impl.signature

import org.objectweb.asm.signature.SignatureVisitor
import org.opentaint.ir.api.Malformed
import org.opentaint.ir.api.MethodResolution
import org.opentaint.ir.api.Raw

internal class MethodSignature : Signature<MethodResolution>() {

    private val parameterTypes = ArrayList<SType>()
    private val exceptionTypes = ArrayList<SClassRefType>()

    private lateinit var returnType: SType

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
        override fun register(token: SType) {
            parameterTypes.add(token)
        }
    }

    private inner class ReturnTypeTypeRegistrant : TypeRegistrant {
        override fun register(token: SType) {
            returnType = token
        }
    }

    private inner class ExceptionTypeRegistrant : TypeRegistrant {
        override fun register(token: SType) {
            exceptionTypes.add(token as SClassRefType)
        }
    }

    companion object {
        fun of(signature: String?): MethodResolution {
            signature ?: return Raw
            return try {
                of(signature, MethodSignature())
            } catch (ignored: RuntimeException) {
                Malformed
            }
        }
    }
}