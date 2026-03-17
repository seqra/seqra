package org.opentaint.dataflow.jvm.ap.ifds.taint

import org.opentaint.dataflow.configuration.jvm.Condition
import org.opentaint.dataflow.configuration.jvm.ConditionVisitor
import org.opentaint.dataflow.configuration.jvm.Position
import org.opentaint.dataflow.configuration.jvm.TaintMark
import java.util.Objects

@Suppress("EqualsOrHashCode")
data class ContainsMarkOnAnyField(
    val position: Position,
    val mark: TaintMark,
) : Condition {
    override fun <R> accept(conditionVisitor: ConditionVisitor<R>): R {
        error("Condition visitor is not supported")
    }

    private val hash = Objects.hash(position, mark)
    override fun hashCode(): Int = hash
}
