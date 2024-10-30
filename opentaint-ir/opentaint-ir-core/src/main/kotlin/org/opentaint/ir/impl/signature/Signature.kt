package org.opentaint.ir.impl.signature

import org.objectweb.asm.signature.SignatureReader
import org.objectweb.asm.signature.SignatureVisitor
import org.opentaint.ir.api.Resolution

internal abstract class Signature<T : Resolution>() :
    TypeRegistrant.RejectingSignatureVisitor(), TypeRegistrant {

    protected val typeVariables = ArrayList<FormalTypeVariable>()
    protected var currentTypeParameter: String? = null
    protected var currentBounds: MutableList<SType>? = null

    override fun visitFormalTypeParameter(name: String) {
        collectTypeParameter()
        currentTypeParameter = name
        currentBounds = ArrayList()
    }

    override fun visitClassBound(): SignatureVisitor {
        return TypeExtractor(this)
    }

    override fun visitInterfaceBound(): SignatureVisitor {
        return TypeExtractor(this)
    }

    override fun register(token: SType) {
        checkNotNull(currentBounds) { "Did not expect $token before finding formal parameter" }
        currentBounds!!.add(token)
    }

    protected fun collectTypeParameter() {
        val current = currentTypeParameter
        if (current != null) {
            typeVariables.add(Formal(current, currentBounds))
        }
    }

    abstract fun resolve(): T

    companion object {
        fun <S : Resolution> of(genericSignature: String?, visitor: Signature<S>): S {
            val signatureReader = SignatureReader(genericSignature)
            signatureReader.accept(visitor)
            return visitor.resolve()
        }
    }
}