package org.opentaint.ir.analysis.paths

import org.opentaint.ir.api.jvm.JIRField

sealed interface Accessor

data class FieldAccessor(val field: JIRField) : Accessor {
    override fun toString(): String {
        return field.name
    }
}

object ElementAccessor : Accessor {
    override fun toString(): String {
        return "*"
    }
}