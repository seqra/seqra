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
    val typeBindings: JIRTypeBindings = JIRTypeBindings.empty
) : JIRTypedField {

    private val resolution = FieldSignature.of(field.signature) as? FieldResolutionImpl
    private val classpath = field.enclosingClass.classpath
    private val resolvedType = resolution?.fieldType?.apply(typeBindings, null)

    override val name: String get() = this.field.name

    private val fieldTypeGetter = suspendableLazy {
        val typeName = field.type.typeName
        resolvedType?.let { classpath.typeOf(it, typeBindings) }
            ?: classpath.findTypeOrNull(field.type.typeName)
            ?: typeName.throwClassNotFound()
    }

    override suspend fun fieldType(): JIRType = fieldTypeGetter()

}