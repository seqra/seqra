@file:JvmName("JIRInstructions")

package org.opentaint.ir.api.jvm.ext.cfg

import org.opentaint.ir.api.core.cfg.InstList
import org.opentaint.ir.api.jvm.cfg.DefaultJIRExprVisitor
import org.opentaint.ir.api.jvm.cfg.DefaultJIRInstVisitor
import org.opentaint.ir.api.jvm.cfg.JIRArrayAccess
import org.opentaint.ir.api.jvm.cfg.JIRCallExpr
import org.opentaint.ir.api.jvm.cfg.JIRExpr
import org.opentaint.ir.api.jvm.cfg.JIRFieldRef
import org.opentaint.ir.api.jvm.cfg.JIRInst
import org.opentaint.ir.api.jvm.cfg.JIRLocal
import org.opentaint.ir.api.jvm.cfg.JIRRawExpr
import org.opentaint.ir.api.jvm.cfg.JIRRawExprVisitor
import org.opentaint.ir.api.jvm.cfg.JIRRawInst
import org.opentaint.ir.api.jvm.cfg.JIRRawInstVisitor
import org.opentaint.ir.api.jvm.cfg.JIRValue
import org.opentaint.ir.api.jvm.cfg.LocalResolver
import org.opentaint.ir.api.jvm.cfg.ValueResolver

fun InstList<JIRRawInst>.apply(visitor: JIRRawInstVisitor<Unit>): InstList<JIRRawInst> {
    instructions.forEach { it.accept(visitor) }
    return this
}

fun <R, E, T : JIRRawInstVisitor<E>> InstList<JIRRawInst>.applyAndGet(visitor: T, getter: (T) -> R): R {
    instructions.forEach { it.accept(visitor) }
    return getter(visitor)
}

fun <T> InstList<JIRRawInst>.collect(visitor: JIRRawInstVisitor<T>): Collection<T> {
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

object FieldRefVisitor : DefaultJIRExprVisitor<JIRFieldRef?>, DefaultJIRInstVisitor<JIRFieldRef?> {

    override val defaultExprHandler: (JIRExpr) -> JIRFieldRef?
        get() = { null }

    override val defaultInstHandler: (JIRInst) -> JIRFieldRef?
        get() = {
            it.operands.map { it.accept(this) }.firstOrNull { it != null }
        }

    override fun visitJIRFieldRef(value: JIRFieldRef): JIRFieldRef {
        return value
    }
}

object ArrayAccessVisitor : DefaultJIRExprVisitor<JIRArrayAccess?>, DefaultJIRInstVisitor<JIRArrayAccess?> {

    override val defaultExprHandler: (JIRExpr) -> JIRArrayAccess?
        get() = {
            it.operands.filterIsInstance<JIRArrayAccess>().firstOrNull()
        }

    override val defaultInstHandler: (JIRInst) -> JIRArrayAccess?
        get() = {
            it.operands.map { it.accept(this) }.firstOrNull { it != null }
        }

}

object CallExprVisitor : DefaultJIRInstVisitor<JIRCallExpr?> {

    override val defaultInstHandler: (JIRInst) -> JIRCallExpr?
        get() = {
            it.operands.filterIsInstance<JIRCallExpr>().firstOrNull()
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

val InstList<JIRInst>.locals: Set<JIRLocal>
    get() {
        val resolver = LocalResolver().also { res ->
            forEach { it.accept(res) }
        }
        return resolver.result
    }

val InstList<JIRInst>.values: Set<JIRValue>
    get() {
        val resolver = ValueResolver().also { res ->
            forEach { it.accept(res) }
        }
        return resolver.result
    }