package org.opentaint.ir.api.common

interface CommonTypedField {
    val field: CommonClassField
    val type: CommonType

    val name: String
        get() = this.field.name
}
