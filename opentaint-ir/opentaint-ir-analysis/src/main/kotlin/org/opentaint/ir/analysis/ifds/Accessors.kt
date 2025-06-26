package org.opentaint.ir.analysis.ifds

sealed interface Accessor {
    fun toSuffix(): String
}

data class FieldAccessor(
    val name: String,
    val isStatic: Boolean = false,
) : Accessor {
    override fun toSuffix(): String = ".${name}"
    override fun toString(): String = name
}

object ElementAccessor : Accessor {
    override fun toSuffix(): String = "[*]"
    override fun toString(): String = "*"
}
