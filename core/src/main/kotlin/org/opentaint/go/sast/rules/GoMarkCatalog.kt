package org.opentaint.go.sast.rules

import org.opentaint.dataflow.configuration.go.serialized.GoSerializedCondition
import org.opentaint.dataflow.configuration.go.serialized.GoSerializedFieldSource
import org.opentaint.dataflow.configuration.go.serialized.GoSerializedGlobalSource
import org.opentaint.dataflow.configuration.go.serialized.GoSerializedItem
import org.opentaint.dataflow.configuration.go.serialized.GoSerializedRule
import org.opentaint.dataflow.configuration.go.serialized.GoSerializedTaintConfig
import org.opentaint.dataflow.configuration.go.serialized.GoUserDefinedRuleInfo
import org.opentaint.dataflow.configuration.selection.MarkCatalog
import org.opentaint.dataflow.configuration.selection.MarkCatalogRow
import org.opentaint.dataflow.configuration.selection.MarkExpression
import org.opentaint.dataflow.configuration.selection.RuleRole

internal fun GoSerializedTaintConfig.markCatalog(): MarkCatalog = MarkCatalog(
    rows = buildList {
        globalSource.forEach { add(it.markCatalogRow()) }
        fieldSource.forEach { add(it.markCatalogRow()) }
        source.forEach { add(it.markCatalogRow()) }
        sink.forEach { add(it.markCatalogRow()) }
        passThrough.forEach { add(it.markCatalogRow()) }
        cleaner.forEach { add(it.markCatalogRow()) }
    },
)

internal fun GoSerializedItem.markCatalogRow(): MarkCatalogRow {
    val references = linkedSetOf<String>()
    references += (info as? GoUserDefinedRuleInfo)?.relevantTaintMarks.orEmpty()
    return when (this) {
        is GoSerializedGlobalSource -> MarkCatalogRow(serializedId, RuleRole.SOURCE, condition.markExpression(),
            taint.map { it.kind }, 0, references)
        is GoSerializedFieldSource -> MarkCatalogRow(serializedId, RuleRole.SOURCE, condition.markExpression(),
            taint.map { it.kind }, 0, references)
        is GoSerializedRule.Source -> MarkCatalogRow(serializedId, RuleRole.SOURCE, condition.markExpression(),
            taint.map { it.kind }, 0, references)
        is GoSerializedRule.Sink -> MarkCatalogRow(serializedId, RuleRole.SINK, condition.markExpression(),
            trackFactsReachAnalysisEnd.orEmpty().map { it.kind }, 0, references,
            protectedProducers = trackFactsReachAnalysisEnd.orEmpty().mapTo(linkedSetOf()) { it.kind },
            protectedConsumers = trackFactsReachAnalysisEnd.orEmpty().mapTo(linkedSetOf()) { it.kind })
        is GoSerializedRule.PassThrough -> {
            references += copy.mapNotNull { it.taintKind }
            MarkCatalogRow(serializedId, RuleRole.PASS, MarkExpression.Truth(true), emptyList(), copy.size, references)
        }
        is GoSerializedRule.Cleaner -> {
            references += cleans.mapNotNull { it.taintKind }
            MarkCatalogRow(serializedId, RuleRole.CLEANER, condition.markExpression(), emptyList(), cleans.size, references)
        }
    }
}

private fun GoSerializedCondition?.markExpression(): MarkExpression = when (this) {
    null, GoSerializedCondition.True -> MarkExpression.Truth(true)
    is GoSerializedCondition.ContainsMark -> MarkExpression.ContainsMark(tainted, this)
    is GoSerializedCondition.ContainsMarkOnAnyAccessor -> MarkExpression.ContainsMark(tainted, this)
    is GoSerializedCondition.And -> MarkExpression.And(allOf.map { it.markExpression() })
    is GoSerializedCondition.Or -> MarkExpression.Or(anyOf.map { it.markExpression() })
    is GoSerializedCondition.Not -> MarkExpression.Not(not.markExpression())
    else -> MarkExpression.Other(this)
}
