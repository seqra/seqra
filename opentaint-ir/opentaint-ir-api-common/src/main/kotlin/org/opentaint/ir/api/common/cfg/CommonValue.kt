package org.opentaint.ir.api.common.cfg

import org.opentaint.ir.api.common.CommonTypedField

interface CommonValue : CommonExpr {
    interface Visitor<out T> {
        fun visitExternalCommonValue(value: CommonValue): T

        interface Default<out T> : Visitor<T> {
            fun defaultVisitCommonValue(value: CommonValue): T

            override fun visitExternalCommonValue(value: CommonValue): T = defaultVisitCommonValue(value)
        }
    }

    fun <T> accept(visitor: Visitor<T>): T = acceptCommonValue(visitor)
    fun <T> acceptCommonValue(visitor: Visitor<T>): T {
        return visitor.visitExternalCommonValue(this)
    }
}

interface CommonThis : CommonValue

interface CommonArgument : CommonValue {
    val index: Int
    val name: String
}

interface CommonFieldRef : CommonValue {
    val instance: CommonValue?
    val field: CommonTypedField
}

interface CommonArrayAccess : CommonValue {
    val array: CommonValue
    val index: CommonValue
}
