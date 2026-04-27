package org.opentaint.ir.impl.python.converter

import org.opentaint.ir.api.python.*
import org.opentaint.ir.impl.python.builder.*

object ConstConverter {
    fun convert(c: FlatConst): PIRValue = when (c) {
        is FlatIntConst -> PIRIntConst(c.value)
        is FlatFloatConst -> PIRFloatConst(c.value)
        is FlatStrConst -> PIRStrConst(c.value)
        is FlatBoolConst -> PIRBoolConst(c.value)
        is FlatNoneConst -> PIRNoneConst
        is FlatEllipsisConst -> PIREllipsisConst
        is FlatBytesConst -> PIRBytesConst(c.value)
        is FlatComplexConst -> PIRComplexConst(c.real, c.imag)
    }
}
