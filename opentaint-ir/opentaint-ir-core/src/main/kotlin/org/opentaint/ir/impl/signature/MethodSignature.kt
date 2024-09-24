package org.opentaint.ir.impl.signature

import org.objectweb.asm.signature.SignatureVisitor

open class MethodSignature : Signature<MethodResolution>() {

    private val parameterTypes = ArrayList<GenericType>()
    private val exceptionTypes = ArrayList<GenericType>()

    private lateinit var returnType: GenericType

    override fun visitParameterType(): SignatureVisitor {
        return GenericTypeExtractor(ParameterTypeRegistrant())
    }

    override fun visitReturnType(): SignatureVisitor {
        collectTypeParameter()
        return GenericTypeExtractor(ReturnTypeTypeRegistrant())
    }

    override fun visitExceptionType(): SignatureVisitor {
        return GenericTypeExtractor(ExceptionTypeRegistrant())
    }

    override fun resolve(): MethodResolution {
        return MethodResolutionImpl(
            returnType,
            parameterTypes,
            exceptionTypes,
            typeVariables
        )
    }

    private inner class ParameterTypeRegistrant : GenericTypeRegistrant {
        override fun register(token: GenericType) {
            parameterTypes.add(token)
        }
    }

    private inner class ReturnTypeTypeRegistrant : GenericTypeRegistrant {
        override fun register(token: GenericType) {
            returnType = token
        }
    }

    private inner class ExceptionTypeRegistrant : GenericTypeRegistrant {
        override fun register(token: GenericType) {
            exceptionTypes.add(token)
        }
    }

    companion object {
        fun extract(genericSignature: String?): MethodResolution {
            return try {
                if (genericSignature == null) Raw else extract(genericSignature, MethodSignature())
            } catch (ignored: RuntimeException) {
                Malformed
            }
        }
    }
}