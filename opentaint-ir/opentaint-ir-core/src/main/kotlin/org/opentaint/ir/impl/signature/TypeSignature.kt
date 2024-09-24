package org.opentaint.ir.impl.signature

import org.objectweb.asm.signature.SignatureVisitor

class TypeSignature : Signature<TypeResolution>() {

    private val interfaceTypes = ArrayList<GenericType>()
    private lateinit var superClassToken: GenericType

    override fun visitSuperclass(): SignatureVisitor {
        collectTypeParameter()
        return GenericTypeExtractor(SuperClassRegistrant())
    }

    override fun visitInterface(): SignatureVisitor {
        return GenericTypeExtractor(InterfaceTypeRegistrant())
    }

    override fun resolve(): TypeResolution {
        return TypeResolutionImpl(superClassToken, interfaceTypes, typeVariables)
    }

    private inner class SuperClassRegistrant : GenericTypeRegistrant {

        override fun register(token: GenericType) {
            superClassToken = token
        }
    }

    private inner class InterfaceTypeRegistrant : GenericTypeRegistrant {

        override fun register(token: GenericType) {
            interfaceTypes.add(token)
        }
    }


    companion object {
        fun extract( genericSignature: String?): TypeResolution {
            return try {
                if (genericSignature == null) Raw else extract(genericSignature, TypeSignature())
            } catch (ignored: RuntimeException) {
                Malformed
            }
        }
    }
}