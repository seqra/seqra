package org.opentaint.ir.analysis.paths

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

// data class ElementAccessor(
//     val index: JIRValue?, // null if "any"
// ) : Accessor {
//     override fun toSuffix(): String {
//         return if (index == null) "[*]" else "[$index]"
//     }
//
//     override fun toString(): String {
//         return "[$index]"
//     }
// }

object ElementAccessor : Accessor {
    override fun toSuffix(): String {
        return "[*]"
    }

    override fun toString(): String {
        return "*"
    }
}

fun ElementAccessor(index: JIRValue?) = ElementAccessor
