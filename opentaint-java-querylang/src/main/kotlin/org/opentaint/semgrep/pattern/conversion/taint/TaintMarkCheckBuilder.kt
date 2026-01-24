package org.opentaint.semgrep.pattern.conversion.taint

import org.opentaint.dataflow.configuration.jvm.serialized.PositionBaseWithModifiers
import org.opentaint.dataflow.configuration.jvm.serialized.SerializedCondition

sealed interface TaintMarkCheckBuilder {
    fun build(position: PositionBaseWithModifiers): SerializedCondition
}

data class TaintMarkLabelCheckBuilder(val label: String) : TaintMarkCheckBuilder {
    override fun build(position: PositionBaseWithModifiers): SerializedCondition =
        SerializedCondition.ContainsMark(label, position)
}

data class TaintMarkNotCheckBuilder(val arg: TaintMarkCheckBuilder): TaintMarkCheckBuilder {
    override fun build(position: PositionBaseWithModifiers): SerializedCondition =
        SerializedCondition.not(arg.build(position))
}

data class TaintMarkAndCheckBuilder(
    val l: TaintMarkCheckBuilder,
    val r: TaintMarkCheckBuilder
) : TaintMarkCheckBuilder {
    override fun build(position: PositionBaseWithModifiers): SerializedCondition =
        SerializedCondition.and(listOf(l.build(position), r.build(position)))
}

data class TaintMarkOrCheckBuilder(
    val l: TaintMarkCheckBuilder,
    val r: TaintMarkCheckBuilder
) : TaintMarkCheckBuilder {
    override fun build(position: PositionBaseWithModifiers): SerializedCondition =
        serializedConditionOr(listOf(l.build(position), r.build(position)))
}

data object TaintMarkCheckNotRequiredBuilder : TaintMarkCheckBuilder {
    override fun build(position: PositionBaseWithModifiers): SerializedCondition = SerializedCondition.True
}

fun TaintMarkCheckBuilder.collectLabels(dst: MutableSet<String>): Set<String> {
    when (this) {
        is TaintMarkCheckNotRequiredBuilder -> {
            // no labels
        }

        is TaintMarkLabelCheckBuilder -> dst.add(label)
        is TaintMarkNotCheckBuilder -> arg.collectLabels(dst)

        is TaintMarkAndCheckBuilder -> {
            l.collectLabels(dst)
            r.collectLabels(dst)
        }

        is TaintMarkOrCheckBuilder -> {
            l.collectLabels(dst)
            r.collectLabels(dst)
        }
    }
    return dst
}
