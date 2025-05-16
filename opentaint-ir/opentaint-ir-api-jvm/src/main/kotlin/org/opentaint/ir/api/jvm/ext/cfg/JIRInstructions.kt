@file:JvmName("JIRInstructions")

package org.opentaint.ir.api.jvm.ext.cfg

import org.opentaint.ir.api.common.cfg.CommonExpr
import org.opentaint.ir.api.common.cfg.CommonInst
import org.opentaint.ir.api.jvm.cfg.JIRArrayAccess
import org.opentaint.ir.api.jvm.cfg.JIRCallExpr
import org.opentaint.ir.api.jvm.cfg.JIRExpr
import org.opentaint.ir.api.jvm.cfg.JIRExprVisitor
import org.opentaint.ir.api.jvm.cfg.JIRFieldRef
import org.opentaint.ir.api.jvm.cfg.JIRInst
import org.opentaint.ir.api.jvm.cfg.JIRInstList
import org.opentaint.ir.api.jvm.cfg.JIRInstVisitor
import org.opentaint.ir.api.jvm.cfg.JIRLocal
import org.opentaint.ir.api.jvm.cfg.JIRRawExpr
import org.opentaint.ir.api.jvm.cfg.JIRRawExprVisitor
import org.opentaint.ir.api.jvm.cfg.JIRRawInst
import org.opentaint.ir.api.jvm.cfg.JIRRawInstVisitor
import org.opentaint.ir.api.jvm.cfg.JIRValue
import org.opentaint.ir.api.jvm.cfg.LocalResolver
import org.opentaint.ir.api.jvm.cfg.ValueResolver

fun JIRInstList<JIRRawInst>.apply(visitor: JIRRawInstVisitor<Unit>): JIRInstList<JIRRawInst> {
    instructions.forEach { it.accept(visitor) }
    return this
}

fun <R, E, T : JIRRawInstVisitor<E>> JIRInstList<JIRRawInst>.applyAndGet(visitor: T, getter: (T) -> R): R {
    instructions.forEach { it.accept(visitor) }
    return getter(visitor)
}

fun <T> JIRInstList<JIRRawInst>.collect(visitor: JIRRawInstVisitor<T>): Collection<T> {
    return instructions.map { it.accept(visitor) }
}

fun <R, E, T : JIRRawInstVisitor<E>> JIRRawInst.applyAndGet(visitor: T, getter: (T) -> R): R {
    this.accept(visitor)
    return getter(visitor)
}

fun <R, E, T : JIRRawExprVisitor<E>> JIRRawExpr.applyAndGet(visitor: T, getter: (T) -> R): R {
    this.accept(visitor)
    return getter(visitor)
}

object FieldRefVisitor :
    JIRExprVisitor.Default<JIRFieldRef?>,
    JIRInstVisitor.Default<JIRFieldRef?> {

    override fun defaultVisitCommonExpr(expr: CommonExpr): JIRFieldRef? {
        TODO("Not yet implemented")
    }

    override fun defaultVisitCommonInst(inst: CommonInst<*, *>): JIRFieldRef? {
        TODO("Not yet implemented")
    }

    override fun defaultVisitJIRExpr(expr: JIRExpr): JIRFieldRef? {
        return expr.operands.filterIsInstance<JIRFieldRef>().firstOrNull()
    }

    override fun defaultVisitJIRInst(inst: JIRInst): JIRFieldRef? {
        return inst.operands.map { it.accept(this) }.firstOrNull { it != null }
    }

    override fun visitJIRFieldRef(value: JIRFieldRef): JIRFieldRef {
        return value
    }
}

object ArrayAccessVisitor :
    JIRExprVisitor.Default<JIRArrayAccess?>,
    JIRInstVisitor.Default<JIRArrayAccess?> {

    override fun defaultVisitCommonExpr(expr: CommonExpr): JIRArrayAccess? {
        TODO("Not yet implemented")
    }

    override fun defaultVisitCommonInst(inst: CommonInst<*, *>): JIRArrayAccess? {
        TODO("Not yet implemented")
    }

    override fun defaultVisitJIRExpr(expr: JIRExpr): JIRArrayAccess? {
        return expr.operands.filterIsInstance<JIRArrayAccess>().firstOrNull()
    }

    override fun defaultVisitJIRInst(inst: JIRInst): JIRArrayAccess? {
        return inst.operands.map { it.accept(this) }.firstOrNull { it != null }
    }

    override fun visitJIRArrayAccess(value: JIRArrayAccess): JIRArrayAccess {
        return value
    }
}

object CallExprVisitor : JIRInstVisitor.Default<JIRCallExpr?> {
    override fun defaultVisitCommonInst(inst: CommonInst<*, *>): JIRCallExpr? {
        TODO("Not yet implemented")
    }

    override fun defaultVisitJIRInst(inst: JIRInst): JIRCallExpr? {
        return inst.operands.filterIsInstance<JIRCallExpr>().firstOrNull()
    }
}

val JIRInst.fieldRef: JIRFieldRef?
    get() {
        return accept(FieldRefVisitor)
    }

val JIRInst.arrayRef: JIRArrayAccess?
    get() {
        return accept(ArrayAccessVisitor)
    }

val JIRInst.callExpr: JIRCallExpr?
    get() {
        return accept(CallExprVisitor)
    }

val JIRInstList<JIRInst>.locals: Set<JIRLocal>
    get() {
        val resolver = LocalResolver().also { res ->
            forEach { it.accept(res) }
        }
        return resolver.result
    }

val JIRInstList<JIRInst>.values: Set<JIRValue>
    get() {
        val resolver = ValueResolver().also { res ->
            forEach { it.accept(res) }
        }
        return resolver.result
    }
