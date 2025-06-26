package org.opentaint.ir.analysis.ifds

import org.opentaint.ir.api.common.CommonMethod
import org.opentaint.ir.api.common.cfg.CommonInst

data class Vertex<out Fact, out Statement : CommonInst>(
    val statement: Statement,
    val fact: Fact,
) {
    val method: CommonMethod
        get() = statement.method

    override fun toString(): String {
        return "$fact at $statement in $method"
    }
}
