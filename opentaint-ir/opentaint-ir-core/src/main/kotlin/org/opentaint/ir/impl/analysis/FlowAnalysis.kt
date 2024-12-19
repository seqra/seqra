package org.opentaint.ir.impl.analysis

import org.opentaint.ir.api.cfg.DefaultJIRExprVisitor
import org.opentaint.ir.api.cfg.DefaultJIRInstVisitor
import org.opentaint.ir.api.cfg.JIRAssignInst
import org.opentaint.ir.api.cfg.JIRCallExpr
import org.opentaint.ir.api.cfg.JIRCallInst
import org.opentaint.ir.api.cfg.JIRCatchInst
import org.opentaint.ir.api.cfg.JIRExpr
import org.opentaint.ir.api.cfg.JIRGraph
import org.opentaint.ir.api.cfg.JIRInst
import org.opentaint.ir.api.cfg.JIRLocal
import org.opentaint.ir.api.cfg.JIRLocalVar
import org.opentaint.ir.api.cfg.JIRThrowInst
import org.opentaint.ir.impl.cfg.collect

interface FlowAnalysis<T> {

    val ins: MutableMap<JIRInst, T>
    val outs: MutableMap<JIRInst, T>

    val graph: JIRGraph

    val isForward: Boolean

    fun newFlow(): T

    fun newEntryFlow(): T

    fun merge(in1: T, in2: T, out: T)

    fun copy(source: T?, dest: T)

    fun run()
}

object LocalResolver : DefaultJIRInstVisitor<Sequence<JIRLocal>>, DefaultJIRExprVisitor<Sequence<JIRLocal>> {

    override val defaultInstHandler: (JIRInst) -> Sequence<JIRLocal>
        get() = { emptySequence() }
    override val defaultExprHandler: (JIRExpr) -> Sequence<JIRLocal>
        get() = { emptySequence() }

    override fun visitJIRLocalVar(value: JIRLocalVar): Sequence<JIRLocal> {
        return sequenceOf(value)
    }

    override fun visitJIRArgument(value: JIRArgument): Sequence<JIRLocal> {
        return sequenceOf(value)
    }

    private fun visitCallExpr(expr: JIRCallExpr): Sequence<JIRLocal> {
        return expr.operands.asSequence().flatMap { it.accept(this@LocalResolver) }
    }

    override fun visitJIRDynamicCallExpr(expr: JIRDynamicCallExpr): Sequence<JIRLocal> = visitCallExpr(expr)

    override fun visitJIRSpecialCallExpr(expr: JIRSpecialCallExpr): Sequence<JIRLocal> = visitCallExpr(expr)

    override fun visitJIRVirtualCallExpr(expr: JIRVirtualCallExpr): Sequence<JIRLocal> = visitCallExpr(expr)

    override fun visitJIRStaticCallExpr(expr: JIRStaticCallExpr): Sequence<JIRLocal> = visitCallExpr(expr)

    override fun visitJIRAssignInst(inst: JIRAssignInst): Sequence<JIRLocal> {
        return sequence {
            yieldAll(inst.lhv.accept(this@LocalResolver))
            yieldAll(inst.rhv.accept(this@LocalResolver))
        }
    }

    override fun visitJIRThrowInst(inst: JIRThrowInst): Sequence<JIRLocal> {
        return inst.throwable.accept(this)
    }

    override fun visitJIRCatchInst(inst: JIRCatchInst): Sequence<JIRLocal> {
        return inst.throwable.accept(this)
    }

    override fun visitJIRCallInst(inst: JIRCallInst): Sequence<JIRLocal> {
        return inst.callExpr.accept(this)
    }

    override fun visitJIRReturnInst(inst: JIRReturnInst): Sequence<JIRLocal> {
        return inst.returnValue?.accept(this) ?: emptySequence()
    }
}

val JIRGraph.locals: Set<JIRLocal>
    get() {
        return collect(LocalResolver).flatMap { it.toList() }.toSet()
    }

//val JIRInst.locals: List<JIRLocal>
//    get() {
//        return collect(LocalResolver)
//    }