package org.opentaint.ir.impl.signature

import org.objectweb.asm.signature.SignatureReader

class FieldSignature : GenericTypeRegistrant {

    private lateinit var fieldType: GenericType

    override fun register(token: GenericType) {
        fieldType = token
    }

    protected fun resolve(): FieldResolution {
        return FieldResolutionImpl(fieldType)
    }

    companion object {
        fun extract(genericSignature: String?): FieldResolution {
            return if (genericSignature == null) {
                Raw
            } else {
                val signatureReader = SignatureReader(genericSignature)
                val visitor = FieldSignature()
                try {
                    signatureReader.acceptType(GenericTypeExtractor(visitor))
                    visitor.resolve()
                } catch (ignored: RuntimeException) {
                    Malformed
                }
            }
        }
    }
}