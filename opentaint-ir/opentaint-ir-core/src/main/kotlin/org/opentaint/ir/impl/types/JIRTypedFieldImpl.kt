package org.opentaint.opentaint-ir.impl.types

import org.opentaint.opentaint-ir.api.JIRField
import org.opentaint.opentaint-ir.api.JIRRefType
import org.opentaint.opentaint-ir.api.JIRType
import org.opentaint.opentaint-ir.api.JIRTypedField
import org.opentaint.opentaint-ir.api.ext.isNullable
import org.opentaint.opentaint-ir.api.throwClassNotFound
import org.opentaint.opentaint-ir.impl.types.signature.FieldResolutionImpl
import org.opentaint.opentaint-ir.impl.types.signature.FieldSignature
import org.opentaint.opentaint-ir.impl.types.substition.JIRSubstitutor

class JIRTypedFieldImpl(
    override val enclosingType: JIRRefType,
    override val field: JIRField,
    private val substitutor: JIRSubstitutor
) : JIRTypedField {

    private val classpath = field.enclosingClass.classpath
    private val resolvedType by lazy(LazyThreadSafetyMode.NONE) {
        val resolution = FieldSignature.of(field) as? FieldResolutionImpl
        resolution?.fieldType
    }

    override val name: String get() = this.field.name

    override val fieldType: JIRType by lazy {
        val typeName = field.type.typeName
        val type = resolvedType?.let {
            classpath.typeOf(substitutor.substitute(it))
        } ?: classpath.findTypeOrNull(field.type.typeName) ?: typeName.throwClassNotFound()

        field.isNullable?.let {
            (type as? JIRRefType)?.copyWithNullability(it)
        } ?: type
    }

}