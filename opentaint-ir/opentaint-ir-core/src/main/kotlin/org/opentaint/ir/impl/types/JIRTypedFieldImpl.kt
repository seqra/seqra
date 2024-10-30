package org.opentaint.ir.impl.types

import org.opentaint.ir.api.JIRField
import org.opentaint.ir.api.JIRRefType
import org.opentaint.ir.api.JIRType
import org.opentaint.ir.api.JIRTypedField
import org.opentaint.ir.api.throwClassNotFound
import org.opentaint.ir.impl.signature.FieldResolutionImpl
import org.opentaint.ir.impl.signature.FieldSignature
import org.opentaint.ir.impl.suspendableLazy

class JIRTypedFieldImpl(
    override val enclosingType: JIRRefType,
    override val field: JIRField,
    private val classBindings: JIRTypeBindings = JIRTypeBindings()
) : JIRTypedField {

    private val resolution = FieldSignature.of(field.signature)
    private val classpath = field.enclosingClass.classpath

    override val name: String get() = this.field.name

    private val fieldTypeGetter = suspendableLazy {
        val typeName = field.type.typeName
        ifSignature {
            classpath.typeOf(it.fieldType.apply(classBindings))
        } ?: classpath.findTypeOrNull(field.type.typeName) ?: typeName.throwClassNotFound()
    }

    override suspend fun fieldType(): JIRType = fieldTypeGetter()

    private suspend fun <T> ifSignature(map: suspend (FieldResolutionImpl) -> T?): T? {
        return when (resolution) {
            is FieldResolutionImpl -> map(resolution)
            else -> null
        }
    }

}