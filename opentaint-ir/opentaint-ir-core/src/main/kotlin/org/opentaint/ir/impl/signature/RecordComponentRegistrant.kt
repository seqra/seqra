package org.opentaint.ir.impl.signature

import org.objectweb.asm.signature.SignatureReader

class RecordComponentRegistrant : GenericTypeRegistrant, Resolution {

    private lateinit var recordComponentType: GenericType

    override fun register(token: GenericType) {
        recordComponentType = token
    }

    protected fun resolve(): RecordComponentResolution {
        return RecordComponentResolutionImpl(recordComponentType)
    }

    companion object {
        fun extract(genericSignature: String?): RecordComponentResolution {
            return if (genericSignature == null) {
                Raw
            } else {
                val signatureReader = SignatureReader(genericSignature)
                val visitor = RecordComponentRegistrant()
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