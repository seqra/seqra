package org.opentaint.ir.impl.types.signature

import mu.KLogging
import org.opentaint.ir.api.FieldResolution
import org.opentaint.ir.api.JIRField
import org.opentaint.ir.api.Malformed
import org.opentaint.ir.api.Pure
import org.opentaint.ir.impl.bytecode.JIRFieldImpl
import org.opentaint.ir.impl.bytecode.kmType
import org.opentaint.ir.impl.types.allVisibleTypeParameters
import org.opentaint.ir.impl.types.substition.RecursiveJvmTypeVisitor
import org.opentaint.ir.impl.types.substition.fixDeclarationVisitor
import org.objectweb.asm.signature.SignatureReader

internal class FieldSignature(private val field: JIRField?) : TypeRegistrant {

    private lateinit var fieldType: JvmType

    override fun register(token: JvmType) {
        fieldType = field?.kmType?.let { JvmTypeKMetadataUpdateVisitor.visitType(token, it) } ?: token
        (field as? JIRFieldImpl)?.let {
            fieldType = fieldType.withTypeAnnotations(it.typeAnnotationInfos, it.enclosingClass.classpath)
        }
    }

    fun resolve(): FieldResolution {
        return FieldResolutionImpl(fieldType)
    }

    companion object : KLogging() {

        private fun FieldResolutionImpl.apply(visitor: RecursiveJvmTypeVisitor) =
            FieldResolutionImpl(visitor.visitType(fieldType))

        fun of(field: JIRField): FieldResolution {
            return of(field.signature, field.enclosingClass.allVisibleTypeParameters(), field)
        }

        fun of(
            signature: String?,
            declarations: Map<String, JvmTypeParameterDeclaration>,
            field: JIRField?
        ): FieldResolution {
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
                logger.warn(ignored) { "Can't parse signature '$signature' of field $field" }
                Malformed
            }
        }
    }
}