package org.opentaint.ir.impl.types.signature

import kotlin.metadata.KmTypeParameter
import org.opentaint.ir.api.jvm.JIRAccessible
import org.opentaint.ir.api.jvm.JvmType
import org.opentaint.ir.api.jvm.JvmTypeParameterDeclaration
import org.opentaint.ir.api.jvm.Resolution
import org.objectweb.asm.signature.SignatureReader
import org.objectweb.asm.signature.SignatureVisitor

internal abstract class Signature<T : Resolution>(
    val owner: JIRAccessible,
    private val kmTypeParameters: List<KmTypeParameter>?
) :
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
            val toAdd = JvmTypeParameterDeclarationImpl(current, owner, currentBounds)
            typeVariables.add(
                if (kmTypeParameters != null) {
                    JvmTypeKMetadataUpdateVisitor.visitDeclaration(toAdd, kmTypeParameters[typeVariables.size])
                } else {
                    toAdd
                }
            )
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
