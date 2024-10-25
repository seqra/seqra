package org.opentaint.ir.impl.signature

import org.objectweb.asm.signature.SignatureReader
import org.opentaint.ir.api.Malformed
import org.opentaint.ir.api.Pure
import org.opentaint.ir.api.RecordComponentResolution

internal class RecordSignature : TypeRegistrant {

    private lateinit var recordComponentType: SType

    override fun register(token: SType) {
        recordComponentType = token
    }

    protected fun resolve(): RecordComponentResolution {
        return RecordComponentResolutionImpl(recordComponentType)
    }

    companion object {
        fun of(signature: String?): RecordComponentResolution {
            signature ?: return Pure
            val signatureReader = SignatureReader(signature)
            val visitor = RecordSignature()
            return try {
                signatureReader.acceptType(TypeExtractor(visitor))
                visitor.resolve()
            } catch (ignored: RuntimeException) {
                Malformed
            }
        }
    }
}
