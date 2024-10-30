package org.opentaint.ir.impl.types

import org.opentaint.ir.api.JIRField
import org.opentaint.ir.api.JIRRefType
import org.opentaint.ir.api.JIRType
import org.opentaint.ir.api.JIRTypedField

class JIRTypedFieldImpl(
    override val ownerType: JIRRefType,
    override val field: JIRField,
    override val fieldType: JIRType,
    override val name: String
) : JIRTypedField