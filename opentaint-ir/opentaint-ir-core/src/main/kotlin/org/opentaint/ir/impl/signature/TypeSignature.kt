package org.opentaint.ir.impl.signature

import org.objectweb.asm.signature.SignatureVisitor
import org.opentaint.ir.api.ClasspathSet

class TypeSignature(cp: ClasspathSet) : Signature<TypeResolution>(cp) {

    private val interfaceTypes = ArrayList<GenericType>()
    private lateinit var superClass: GenericType

    override fun visitSuperclass(): SignatureVisitor {
        collectTypeParameter()
        return GenericTypeExtractor(cp, SuperClassRegistrant())
    }

    override fun visitInterface(): SignatureVisitor {
        return GenericTypeExtractor(cp, InterfaceTypeRegistrant())
    }

    override fun resolve(): TypeResolution {
        return TypeResolutionImpl(superClass, interfaceTypes, typeVariables)
    }

    private inner class SuperClassRegistrant : GenericTypeRegistrant {

        override fun register(token: GenericType) {
            superClass = token
        }
    }

    private inner class InterfaceTypeRegistrant : GenericTypeRegistrant {

        override fun register(token: GenericType) {
            interfaceTypes.add(token)
        }
    }


    companion object {
        fun of(signature: String?, cp: ClasspathSet): TypeResolution {
            return try {
                if (signature == null) Raw else of(signature, TypeSignature(cp))
            } catch (ignored: RuntimeException) {
                Malformed
            }
        }
    }
}