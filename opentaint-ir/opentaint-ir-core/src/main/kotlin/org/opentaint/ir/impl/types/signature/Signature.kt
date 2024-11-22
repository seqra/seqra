
package org.opentaint.ir.impl.types.signature

import org.objectweb.asm.signature.SignatureReader
import org.objectweb.asm.signature.SignatureVisitor
import org.opentaint.ir.api.JIRAccessible
import org.opentaint.ir.api.Resolution

internal abstract class Signature<T : Resolution>(val owner: JIRAccessible) :
    TypeRegistrant.RejectingSignatureVisitor(), TypeRegistrant {

    protected val typeVariables = ArrayList<JvmTypeParameterDeclaration>()
    protected var currentTypeParameter: String? = null
    protected var currentBounds: MutableList<JvmType>? = null

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

    override fun register(token: JvmType) {
        checkNotNull(currentBounds) { "Did not expect $token before finding formal parameter" }
        currentBounds!!.add(token)
    }

    protected fun collectTypeParameter() {
        val current = currentTypeParameter
        if (current != null) {
            typeVariables.add(JvmTypeParameterDeclarationImpl(current, owner, currentBounds))
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