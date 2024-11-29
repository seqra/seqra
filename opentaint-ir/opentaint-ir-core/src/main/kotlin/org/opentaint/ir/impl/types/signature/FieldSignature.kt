package org.opentaint.opentaint-ir.impl.types.signature

import org.objectweb.asm.signature.SignatureReader
import org.opentaint.opentaint-ir.api.FieldResolution
import org.opentaint.opentaint-ir.api.JIRField
import org.opentaint.opentaint-ir.api.Pure
import org.opentaint.opentaint-ir.impl.bytecode.kmType
import org.opentaint.opentaint-ir.impl.types.allVisibleTypeParameters
import org.opentaint.opentaint-ir.impl.types.substition.JvmTypeVisitor
import org.opentaint.opentaint-ir.impl.types.substition.fixDeclarationVisitor

internal class FieldSignature(private val field: JIRField?) : TypeRegistrant {

    private lateinit var fieldType: JvmType

    override fun register(token: JvmType) {
        fieldType = field?.kmType?.let { token.relaxWithKmType(it) } ?: token
    }

    fun resolve(): FieldResolution {
        return FieldResolutionImpl(fieldType)
    }

    companion object {

        private fun FieldResolutionImpl.apply(visitor: JvmTypeVisitor) =
            FieldResolutionImpl(visitor.visitType(fieldType))

        fun of(field: JIRField): FieldResolution {
            return of(field.signature, field.enclosingClass.allVisibleTypeParameters(), field)
        }

        fun of(signature: String?, declarations: Map<String, JvmTypeParameterDeclaration>, field: JIRField?): FieldResolution {
            signature ?: return Pure
            val signatureReader = SignatureReader(signature)
            val visitor = FieldSignature(field)
            return try {
                signatureReader.acceptType(TypeExtractor(visitor))
                val result = visitor.resolve()
                result.let {
                    if (it is FieldResolutionImpl) {
                        it.apply(declarations.fixDeclarationVisitor)
                    } else {
                        it
                    }
                }
            } catch (ignored: RuntimeException) {
                throw ignored
            }
        }
    }
}