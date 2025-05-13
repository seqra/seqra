package org.opentaint.ir.analysis.ifds

import org.opentaint.ir.api.JIRField
import org.opentaint.ir.api.cfg.JIRValue

sealed interface Accessor {
    fun toSuffix(): String
}

data class FieldAccessor(
    val field: JIRField,
) : Accessor {
    override fun toSuffix(): String {
        return ".${field.name}"
    }

    override fun toString(): String {
        return field.name
    }
}

object ElementAccessor : Accessor {
    override fun toSuffix(): String {
        return "[*]"
    }

    override fun toString(): String {
        return "*"
    }
}

fun ElementAccessor(index: JIRValue?) = ElementAccessor
