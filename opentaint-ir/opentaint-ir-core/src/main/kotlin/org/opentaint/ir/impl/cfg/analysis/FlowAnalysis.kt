package org.opentaint.opentaint-ir.impl.cfg.analysis

import org.opentaint.opentaint-ir.api.cfg.DefaultJIRExprVisitor
import org.opentaint.opentaint-ir.api.cfg.DefaultJIRInstVisitor
import org.opentaint.opentaint-ir.api.cfg.JIRAssignInst
import org.opentaint.opentaint-ir.api.cfg.JIRCallExpr
import org.opentaint.opentaint-ir.api.cfg.JIRCallInst
import org.opentaint.opentaint-ir.api.cfg.JIRCatchInst
import org.opentaint.opentaint-ir.api.cfg.JIRExpr
import org.opentaint.opentaint-ir.api.cfg.JIRGraph
import org.opentaint.opentaint-ir.api.cfg.JIRInst
import org.opentaint.opentaint-ir.api.cfg.JIRLocal
import org.opentaint.opentaint-ir.api.cfg.JIRThrowInst
import org.opentaint.opentaint-ir.impl.cfg.collect

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

    override fun visitJIRLocal(value: JIRLocal): Sequence<JIRLocal> {
        return sequenceOf(value)
    }

    override fun visitJIRAssignInst(inst: JIRAssignInst): Sequence<JIRLocal> {
        val value = inst.lhv
        return sequence {
            if (value is JIRLocal) {
                yield(value)
            }
            if (inst.rhv is JIRCallExpr) {
                yieldAll(inst.lhv.accept(this@LocalResolver))
            }
        }
    }

    override fun visitJIRThrowInst(inst: JIRThrowInst): Sequence<JIRLocal> {
        val throwable = inst.throwable
        if (throwable is JIRLocal) {
            return sequenceOf(throwable)
        }
        return emptySequence()
    }

    override fun visitJIRCatchInst(inst: JIRCatchInst): Sequence<JIRLocal> {
        val throwable = inst.throwable
        if (throwable is JIRLocal) {
            return sequenceOf(throwable)
        }
        return emptySequence()
    }

    override fun visitJIRCallInst(inst: JIRCallInst): Sequence<JIRLocal> {
        return inst.callExpr.accept(this)
    }

}

val JIRGraph.locals: List<JIRLocal>
    get() {
        return collect(LocalResolver).flatMap { it.toList() }
    }

//val JIRInst.locals: List<JIRLocal>
//    get() {
//        return collect(LocalResolver)
//    }