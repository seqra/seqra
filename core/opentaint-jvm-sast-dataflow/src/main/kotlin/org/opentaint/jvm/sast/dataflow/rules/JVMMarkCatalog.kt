package org.opentaint.jvm.sast.dataflow.rules

import org.opentaint.dataflow.configuration.jvm.serialized.*
import org.opentaint.dataflow.configuration.selection.*

/** Detached compiler rows from the complete loaded configuration. No method
 * binding, condition/type evaluation or runtime mark enumeration occurs here. */
fun TaintConfiguration.executableMarkCatalog(ignoreEndFacts: Boolean): MarkCatalog {
    return MarkCatalog(rawItems().map { it.markCatalogRow(ignoreEndFacts) },
        declaredMarks = rawMarkDeclarations().names, sourceGeneration = catalogGeneration)
}

fun SerializedItem.markCatalogRow(ignoreEndFacts: Boolean): MarkCatalogRow {
    val condition = when (this) {
        is SourceRule -> condition
        is SinkRule -> condition
        is SerializedRule.Cleaner -> condition
        is SerializedRule.PassThrough -> condition
    }.markExpression()
    val assigned = when (this) {
        is SourceRule -> taint.map { it.kind }
        is SinkRule -> if (ignoreEndFacts) emptyList() else trackFactsReachAnalysisEnd.orEmpty().map { it.kind }
        else -> emptyList()
    }
    val role = selectionRole()
    val otherActions = when (this) {
        is SerializedRule.Cleaner -> cleans.size
        is SerializedRule.PassThrough -> copy.size
        else -> 0
    }
    val references = linkedSetOf<String>()
    (info as? UserDefinedRuleInfo)?.relevantTaintMarks?.let(references::addAll)
    when (this) {
        is SerializedRule.Cleaner -> cleans.mapNotNullTo(references) { it.taintKind }
        is SerializedRule.PassThrough -> copy.mapNotNullTo(references) { it.taintKind }
        is SinkRule -> trackFactsReachAnalysisEnd.orEmpty().mapTo(references) { it.kind }
        else -> Unit
    }
    return MarkCatalogRow(serializedId, role, condition, assigned, otherActions, references,
        protectedConsumers = if (this is SinkRule && !ignoreEndFacts) assigned.toSet() else emptySet())
}

fun SerializedItem.selectionRole(): RuleRole = when (this) {
    is SourceRule -> RuleRole.SOURCE
    is SinkRule -> RuleRole.SINK
    is SerializedRule.Cleaner -> RuleRole.CLEANER
    is SerializedRule.PassThrough -> RuleRole.PASS
}

private fun SerializedCondition?.markExpression(): MarkExpression = when (this) {
    null, SerializedCondition.True -> MarkExpression.Truth(true)
    is SerializedCondition.ContainsMark -> MarkExpression.ContainsMark(tainted, pos)
    is SerializedCondition.Not -> MarkExpression.Not(not.markExpression())
    is SerializedCondition.And -> MarkExpression.And(allOf.map { it.markExpression() })
    is SerializedCondition.Or -> MarkExpression.Or(anyOf.map { it.markExpression() })
    else -> MarkExpression.Other(this)
}
