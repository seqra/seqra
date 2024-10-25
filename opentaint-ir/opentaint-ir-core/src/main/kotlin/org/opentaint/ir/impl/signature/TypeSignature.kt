package org.opentaint.ir.impl.signature

import org.objectweb.asm.signature.SignatureVisitor
import org.opentaint.ir.api.Malformed
import org.opentaint.ir.api.Pure
import org.opentaint.ir.api.TypeResolution

internal class TypeSignature : Signature<TypeResolution>() {

    private val interfaceTypes = ArrayList<SType>()
    private lateinit var superClass: SType

    override fun visitSuperclass(): SignatureVisitor {
        collectTypeParameter()
        return TypeExtractor(SuperClassRegistrant())
    }

    override fun visitInterface(): SignatureVisitor {
        return TypeExtractor(InterfaceTypeRegistrant())
    }

    override fun resolve(): TypeResolution {
        return TypeResolutionImpl(superClass, interfaceTypes, typeVariables)
    }

    private inner class SuperClassRegistrant : TypeRegistrant {

        override fun register(token: SType) {
            superClass = token
        }
    }

    private inner class InterfaceTypeRegistrant : TypeRegistrant {

        override fun register(token: SType) {
            interfaceTypes.add(token)
        }
    }


    companion object {
        fun of(signature: String?): TypeResolution {
            return try {
                if (signature == null) Pure else of(signature, TypeSignature())
            } catch (ignored: RuntimeException) {
                Malformed
            }
        }
    }
}