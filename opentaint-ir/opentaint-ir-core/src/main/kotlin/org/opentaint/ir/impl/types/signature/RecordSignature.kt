package org.opentaint.opentaint-ir.impl.types.signature

import org.objectweb.asm.signature.SignatureReader
import org.opentaint.opentaint-ir.api.Malformed
import org.opentaint.opentaint-ir.api.Pure
import org.opentaint.opentaint-ir.api.RecordComponentResolution

internal class RecordSignature : TypeRegistrant {

    private lateinit var recordComponentType: JvmType

    override fun register(token: JvmType) {
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
