package org.opentaint.ir.impl.types.signature

import org.objectweb.asm.signature.SignatureReader
import org.opentaint.ir.api.FieldResolution
import org.opentaint.ir.api.JIRField
import org.opentaint.ir.api.JIRMethod
import org.opentaint.ir.api.Malformed
import org.opentaint.ir.api.Pure
import org.opentaint.ir.impl.types.substition.JvmTypeVisitor
import org.opentaint.ir.impl.types.substition.VisitorContext
import org.opentaint.ir.impl.types.typeParameters

internal class FieldSignature : TypeRegistrant {

    private lateinit var fieldType: JvmType

    override fun register(token: JvmType) {
        fieldType = token
    }

    fun resolve(): FieldResolution {
        return FieldResolutionImpl(fieldType)
    }

    companion object {

        private fun FieldResolutionImpl.apply(visitor: JvmTypeVisitor) =
            FieldResolutionImpl(visitor.visitType(fieldType))

        fun of(signature: String?, method: JIRMethod): FieldResolution {
            return of(signature, method.typeParameters)
        }

        fun of(field: JIRField): FieldResolution {
            return of(field.signature, field.enclosingClass.typeParameters)
        }

        fun of(signature: String?, paramDeclarations: List<JvmTypeParameterDeclaration>): FieldResolution {
            signature ?: return Pure
            val signatureReader = SignatureReader(signature)
            val visitor = FieldSignature()
            return try {
                signatureReader.acceptType(TypeExtractor(visitor))
                val result = visitor.resolve()
                result.let {
                    if (it is FieldResolutionImpl) {
                        val declarations = paramDeclarations.associateBy { it.symbol }
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