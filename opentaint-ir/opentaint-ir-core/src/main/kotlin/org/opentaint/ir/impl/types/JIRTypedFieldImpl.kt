package org.opentaint.ir.impl.types

import org.opentaint.ir.api.JIRField
import org.opentaint.ir.api.JIRRefType
import org.opentaint.ir.api.JIRType
import org.opentaint.ir.api.JIRTypedField
import org.opentaint.ir.api.ext.isNullable
import org.opentaint.ir.api.throwClassNotFound
import org.opentaint.ir.impl.bytecode.JIRAnnotationImpl
import org.opentaint.ir.impl.bytecode.JIRFieldImpl
import org.opentaint.ir.impl.types.signature.FieldResolutionImpl
import org.opentaint.ir.impl.types.signature.FieldSignature
import org.opentaint.ir.impl.types.substition.JIRSubstitutor
import kotlin.LazyThreadSafetyMode.PUBLICATION

class JIRTypedFieldImpl(
    override val enclosingType: JIRRefType,
    override val field: JIRField,
    private val substitutor: JIRSubstitutor
) : JIRTypedField {

    override val access: Int
        get() = this.field.access

    private val classpath = field.enclosingClass.classpath
    private val resolvedType by lazy(PUBLICATION) {
        val resolution = FieldSignature.of(field) as? FieldResolutionImpl
        resolution?.fieldType
    }

    override val name: String get() = this.field.name

    override val fieldType: JIRType by lazy {
        val typeName = field.type.typeName
        val type = resolvedType?.let {
            classpath.typeOf(substitutor.substitute(it))
        } ?: classpath.findTypeOrNull(field.type.typeName)?.copyWithAnnotations(
            (field as? JIRFieldImpl)?.typeAnnotationInfos?.map { JIRAnnotationImpl(it, field.enclosingClass.classpath) } ?: listOf()
        ) ?: typeName.throwClassNotFound()

        field.isNullable?.let {
            (type as? JIRRefType)?.copyWithNullability(it)
        } ?: type
    }

}