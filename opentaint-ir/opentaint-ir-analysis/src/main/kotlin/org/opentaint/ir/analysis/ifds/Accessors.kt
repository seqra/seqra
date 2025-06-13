package org.opentaint.ir.analysis.ifds

import org.opentaint.ir.api.common.CommonClassField

sealed interface Accessor {
    fun toSuffix(): String
}

data class FieldAccessor(
    val field: CommonClassField,
) : Accessor {
    override fun toSuffix(): String = ".${field.name}"
    override fun toString(): String = field.name
}

object ElementAccessor : Accessor {
    override fun toSuffix(): String = "[*]"
    override fun toString(): String = "*"
}

): Accessor {
    override fun toSuffix(): String = ".${field.name}"
    override fun toString(): String = field.name
}
