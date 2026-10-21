package org.opentaint.dataflow.python.alias

import org.opentaint.dataflow.ap.ifds.analysis.alias.ContextInfo
import org.opentaint.ir.api.python.PIRLocalVar
import org.opentaint.ir.api.python.PIRParameterRef
import org.opentaint.ir.api.python.PIRValue

sealed interface RefValue : Comparable<RefValue> {
    val valueKind: Int

    override fun compareTo(other: RefValue): Int {
        val kindCmp = valueKind.compareTo(other.valueKind)
        if (kindCmp != 0) return kindCmp
        return compareValue(other)
    }

    fun compareValue(other: RefValue): Int

    data class Local(val idx: Int, val ctx: ContextInfo) : RefValue {
        override val valueKind: Int get() = 0

        override fun compareValue(other: RefValue): Int {
            other as Local
            val idxCmp = idx.compareTo(other.idx)
            if (idxCmp != 0) return idxCmp
            return ctx.compareTo(other.ctx)
        }
    }

    data class Arg(val idx: Int) : RefValue {
        override val valueKind: Int get() = 1

        override fun compareValue(other: RefValue): Int = idx.compareTo((other as Arg).idx)
    }
}

interface InstEvalContext {
    fun createArg(idx: Int): RefValue
    fun createLocal(idx: Int): RefValue.Local
}

fun InstEvalContext.refValue(value: PIRValue): RefValue? = when (value) {
    is PIRLocalVar -> createLocal(value.index)
    is PIRParameterRef -> createArg(value.index)
    else -> null
}
