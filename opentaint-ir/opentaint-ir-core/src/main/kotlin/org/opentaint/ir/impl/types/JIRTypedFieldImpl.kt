package org.opentaint.ir.impl.types

import org.opentaint.ir.api.JIRField
import org.opentaint.ir.api.JIRRefType
import org.opentaint.ir.api.JIRType
import org.opentaint.ir.api.JIRTypedField
import org.opentaint.ir.api.throwClassNotFound
import org.opentaint.ir.impl.types.signature.FieldResolutionImpl
import org.opentaint.ir.impl.types.signature.FieldSignature
import org.opentaint.ir.impl.types.substition.JIRSubstitutor

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

    override fun fieldType(): JIRType {
        val typeName = field.type.typeName
        return resolvedType?.let {
            classpath.typeOf(substitutor.substitute(it))
        } ?: classpath.findTypeOrNull(field.type.typeName) ?: typeName.throwClassNotFound()
    }


}