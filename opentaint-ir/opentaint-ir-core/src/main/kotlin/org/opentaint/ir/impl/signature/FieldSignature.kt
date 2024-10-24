package org.opentaint.ir.impl.signature

import org.objectweb.asm.signature.SignatureReader
import org.opentaint.ir.api.FieldResolution
import org.opentaint.ir.api.Malformed
import org.opentaint.ir.api.Raw

internal class FieldSignature : TypeRegistrant {

    private lateinit var fieldType: SType

    override fun register(token: SType) {
        fieldType = token
    }

    fun resolve(): FieldResolution {
        return FieldResolutionImpl(fieldType)
    }

    companion object {
        fun of(signature: String?): FieldResolution {
            signature ?: return Raw
            val signatureReader = SignatureReader(signature)
            val visitor = FieldSignature()
            return try {
                signatureReader.acceptType(TypeExtractor(visitor))
                visitor.resolve()
            } catch (ignored: RuntimeException) {
                Malformed
            }
        }
    }
}