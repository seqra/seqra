package org.opentaint.ir.api.common.cfg

import org.opentaint.ir.api.common.CommonMethod
import org.opentaint.ir.api.common.CommonType

interface CommonExpr {
    val type: CommonType
    val operands: List<CommonValue>

    interface Visitor<out T> : CommonValue.Visitor<T> {
        fun visitExternalCommonExpr(expr: CommonExpr): T

        fun visitCommonCallExpr(expr: CommonExpr): T
        fun visitCommonInstanceCallExpr(expr: CommonExpr): T

        interface Default<out T> : Visitor<T>, CommonValue.Visitor.Default<T> {
            fun defaultVisitCommonExpr(expr: CommonExpr): T

            override fun defaultVisitCommonValue(value: CommonValue): T = defaultVisitCommonExpr(value)

            override fun visitExternalCommonExpr(expr: CommonExpr): T = defaultVisitCommonExpr(expr)

            override fun visitCommonCallExpr(expr: CommonExpr): T = defaultVisitCommonExpr(expr)
            override fun visitCommonInstanceCallExpr(expr: CommonExpr): T = defaultVisitCommonExpr(expr)
        }
    }

    fun <T> accept(visitor: Visitor<T>): T = acceptCommonExpr(visitor)
    fun <T> acceptCommonExpr(visitor: Visitor<T>): T {
        return visitor.visitExternalCommonExpr(this)
    }
}

interface CommonCallExpr : CommonExpr {
    // val method: CommonTypedMethod<*, *>
    val callee: CommonMethod<*, *>
    val args: List<CommonValue>

    // override val type: CommonType
    //     get() = method.returnType

    override val operands: List<CommonValue>
        get() = args

    override fun <T> acceptCommonExpr(visitor: CommonExpr.Visitor<T>): T {
        return visitor.visitCommonCallExpr(this)
    }
}

interface CommonInstanceCallExpr : CommonCallExpr {
    val instance: CommonValue

    override fun <T> acceptCommonExpr(visitor: CommonExpr.Visitor<T>): T {
        return visitor.visitCommonInstanceCallExpr(this)
    }
}
