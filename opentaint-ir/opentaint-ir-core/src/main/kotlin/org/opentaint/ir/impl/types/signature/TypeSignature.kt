package org.opentaint.ir.impl.types.signature

import org.objectweb.asm.signature.SignatureVisitor
import org.opentaint.ir.api.JIRClassOrInterface
import org.opentaint.ir.api.Malformed
import org.opentaint.ir.api.Pure
import org.opentaint.ir.api.TypeResolution
import org.opentaint.ir.impl.types.substition.JvmTypeVisitor
import org.opentaint.ir.impl.types.substition.VisitorContext

internal class TypeSignature(jirClass: JIRClassOrInterface) : Signature<TypeResolution>(jirClass) {

    private val interfaceTypes = ArrayList<JvmType>()
    private lateinit var superClass: JvmType

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

        override fun register(token: JvmType) {
            superClass = token
        }
    }

    private inner class InterfaceTypeRegistrant : TypeRegistrant {

        override fun register(token: JvmType) {
            interfaceTypes.add(token)
        }
    }


    companion object {

        private fun TypeResolutionImpl.apply(visitor: JvmTypeVisitor) = TypeResolutionImpl(
            visitor.visitType(superClass),
            interfaceType.map { visitor.visitType(it) },
            typeVariables.map { visitor.visitDeclaration(it) }
        )


        fun of(jirClass: JIRClassOrInterface): TypeResolution {
            val signature = jirClass.signature ?: return Pure
            return try {
                of(signature, TypeSignature(jirClass)).let {
                    if (it is TypeResolutionImpl) {
                        val declarations = it.typeVariables.associateBy { it.symbol }
                        val fixDeclarationVisitor = object : JvmTypeVisitor {

                            override fun visitTypeVariable(type: JvmTypeVariable, context: VisitorContext): JvmType {
                                type.declaration = declarations[type.symbol]!!
                                return type
                            }
                        }
                        it.apply(fixDeclarationVisitor)
                    } else {
                        it
                    }
                }
            } catch (ignored: RuntimeException) {
                Malformed
            }
        }
    }
}