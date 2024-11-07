package org.opentaint.ir.impl.types

import org.opentaint.ir.api.JIRField
import org.opentaint.ir.api.JIRRefType
import org.opentaint.ir.api.JIRType
import org.opentaint.ir.api.JIRTypedField
import org.opentaint.ir.api.throwClassNotFound
import org.opentaint.ir.impl.suspendableLazy
import org.opentaint.ir.impl.types.signature.FieldResolutionImpl
import org.opentaint.ir.impl.types.signature.FieldSignature
import org.opentaint.ir.impl.types.substition.JIRSubstitutor

class JIRTypedFieldImpl(
    override val enclosingType: JIRRefType,
    override val field: JIRField,
    private val substitutor: JIRSubstitutor
) : JIRTypedField {

    private val resolution = suspendableLazy { FieldSignature.of(field) as? FieldResolutionImpl }
    private val classpath = field.enclosingClass.classpath
    private val resolvedType = suspendableLazy { resolution()?.fieldType }

    override val name: String get() = this.field.name

    private val fieldTypeGetter = suspendableLazy {
        val typeName = field.type.typeName
        resolvedType()?.let {
            classpath.typeOf(substitutor.substitute(it))
        } ?: classpath.findTypeOrNull(field.type.typeName) ?: typeName.throwClassNotFound()
    }

    override suspend fun fieldType(): JIRType = fieldTypeGetter()

}