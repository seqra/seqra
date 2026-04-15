package org.opentaint.semgrep.pattern.conversion.taint

import org.opentaint.dataflow.configuration.jvm.serialized.PositionBaseWithModifiers
import org.opentaint.dataflow.configuration.jvm.serialized.SerializedCondition
import org.opentaint.semgrep.pattern.Mark.GeneratedMark

sealed interface TaintMarkCheckBuilder {
    fun build(position: PositionBaseWithModifiers, withAny: Boolean): SerializedCondition
}

data class TaintMarkLabelCheckBuilder(val label: GeneratedMark) : TaintMarkCheckBuilder {
    override fun build(position: PositionBaseWithModifiers, withAny: Boolean): SerializedCondition =
        if (withAny)
            label.mkContainsMarkOnAny(position)
        else
            label.mkContainsMark(position)
}

data class TaintMarkNotCheckBuilder(val arg: TaintMarkCheckBuilder): TaintMarkCheckBuilder {
    override fun build(position: PositionBaseWithModifiers, withAny: Boolean): SerializedCondition =
        SerializedCondition.not(arg.build(position, withAny))
}

data class TaintMarkAndCheckBuilder(
    val l: TaintMarkCheckBuilder,
    val r: TaintMarkCheckBuilder
) : TaintMarkCheckBuilder {
    override fun build(position: PositionBaseWithModifiers, withAny: Boolean): SerializedCondition =
        SerializedCondition.and(listOf(l.build(position, withAny), r.build(position, withAny)))
}

data class TaintMarkOrCheckBuilder(
    val l: TaintMarkCheckBuilder,
    val r: TaintMarkCheckBuilder
) : TaintMarkCheckBuilder {
    override fun build(position: PositionBaseWithModifiers, withAny: Boolean): SerializedCondition =
        serializedConditionOr(listOf(l.build(position, withAny), r.build(position, withAny)))
}

data object TaintMarkCheckNotRequiredBuilder : TaintMarkCheckBuilder {
    override fun build(position: PositionBaseWithModifiers, withAny: Boolean): SerializedCondition = SerializedCondition.True
}

fun TaintMarkCheckBuilder.collectLabels(dst: MutableSet<GeneratedMark>): Set<GeneratedMark> {
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
