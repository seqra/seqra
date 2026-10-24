package org.opentaint.dataflow.configuration.jvm

import org.opentaint.ir.api.common.CommonMethod
import org.opentaint.ir.api.jvm.JIRField
import org.opentaint.dataflow.configuration.CommonTaintConfigurationItem
import org.opentaint.dataflow.configuration.CommonTaintConfigurationSink
import org.opentaint.dataflow.configuration.CommonTaintConfigurationSinkMeta
import org.opentaint.dataflow.configuration.CommonTaintConfigurationSource
import org.opentaint.dataflow.configuration.jvm.serialized.ItemInfo

sealed interface TaintConfigurationItem : CommonTaintConfigurationItem {
    val info: ItemInfo?
    val serializedId: String?
    val specialization: RuleSpecializationIndices? get() = null
}

/** Original logical labels survive provider compaction. The retained original
 * row also lets context-dependent wrappers expand the original action skeleton. */
data class RuleSpecializationIndices(
    val ruleOrdinal: Int,
    val original: TaintConfigurationItem,
    val actionOrdinals: List<Int>,
    val resetMarkOrdinals: Set<Int>? = null,
    val ruleCount: Int = ruleOrdinal + 1,
)

class SpecializedRules<T>(private val rows: List<T>, val originalRuleCount: Int,
                          val originalConditions: List<Any?>? = null) : AbstractList<T>() {
    val originalConditionTokens: Set<Any?>? = originalConditions?.toSet()
    override val size get() = rows.size
    override fun get(index: Int): T = rows[index]
}

fun TaintConfigurationItem.relabelRuleOrdinal(ordinal: Int, count: Int): TaintConfigurationItem {
    val labels = specialization?.copy(ruleOrdinal = ordinal, ruleCount = count) ?:
        RuleSpecializationIndices(ordinal, this, when (this) {
            is TaintConfigurationSource -> actionsAfter.indices.toList()
            is TaintConfigurationSink -> trackFactsReachAnalysisEnd.indices.toList()
            is TaintCleaner -> actionsAfter.indices.toList()
            is TaintPassThrough -> actionsAfter.indices.toList()
        }, ruleCount = count)
    return when (this) {
        is TaintEntryPointSource -> copy(specialization = labels)
        is TaintMethodSource -> copy(specialization = labels)
        is TaintMethodExitSource -> copy(specialization = labels)
        is TaintStaticFieldSource -> copy(specialization = labels)
        is TaintMethodSink -> copy(specialization = labels)
        is TaintMethodEntrySink -> copy(specialization = labels)
        is TaintMethodExitSink -> copy(specialization = labels)
        is TaintCleaner -> copy(specialization = labels)
        is TaintPassThrough -> copy(specialization = labels)
    }
}

fun TaintConfigurationItem.originalCondition(): Condition {
    return when (val rule = specialization?.original ?: this) {
        is TaintConfigurationSource -> rule.condition
        is TaintConfigurationSink -> rule.condition
        is TaintCleaner -> rule.condition
        is TaintPassThrough -> rule.condition
    }
}

/** Known wrapper-generated rows occupy the same original combined list slots
 * even when the delegated executable list has been compacted. */
@Suppress("UNCHECKED_CAST")
fun <T : TaintConfigurationItem> appendSyntheticRules(original: Iterable<T>, extra: List<T>,
                                                    prepend: Boolean = false): List<T> {
    val values = original.toList()
    if (original !is SpecializedRules<*> && values.none { it.specialization != null })
        return if (prepend) extra + values else values + extra
    val conditions = (original as? SpecializedRules<*>)?.originalConditions ?: values.map { it.originalCondition() }
    val count = conditions.size + extra.size
    val originalOffset = if (prepend) extra.size else 0
    val extraOffset = if (prepend) 0 else conditions.size
    val kept = values.mapIndexed { index, row ->
        row.relabelRuleOrdinal(originalOffset + row.ruleOrdinal(index), count) as T
    }
    val generated = extra.mapIndexed { index, row -> row.relabelRuleOrdinal(extraOffset + index, count) as T }
    return SpecializedRules(if (prepend) generated + kept else kept + generated, count,
        if (prepend) extra.map { it.originalCondition() } + conditions else conditions + extra.map { it.originalCondition() })
}

fun Iterable<TaintConfigurationItem>.originalRuleCount(): Int =
    (this as? SpecializedRules<*>)?.originalRuleCount ?:
        maxOf(count(), maxOfOrNull { it.specialization?.ruleCount ?: 0 } ?: 0)

fun TaintConfigurationItem.ruleOrdinal(fallback: Int): Int = specialization?.ruleOrdinal ?: fallback
fun TaintConfigurationItem.actionOrdinal(fallback: Int): Int =
    specialization?.actionOrdinals?.getOrNull(fallback) ?: fallback

fun TaintConfigurationItem.resetMarks(): List<Pair<Int, String>> {
    val marks = (info as? org.opentaint.dataflow.configuration.jvm.serialized.UserDefinedRuleInfo)
        ?.relevantTaintMarks.orEmpty()
    val selected = specialization?.resetMarkOrdinals
    return marks.mapIndexedNotNull { index, mark ->
        if (selected == null || index in selected) index to mark else null
    }
}

sealed interface TaintConfigurationSource : TaintConfigurationItem, CommonTaintConfigurationSource {
    val condition: Condition
    val actionsAfter: List<AssignMark>
}

data class TaintEntryPointSource(
    val method: CommonMethod,
    override val condition: Condition,
    override val actionsAfter: List<AssignMark>,
    override val info: ItemInfo?,
    override val serializedId: String? = null,
    override val specialization: RuleSpecializationIndices? = null,
) : TaintConfigurationSource

data class TaintMethodSource(
    val method: CommonMethod,
    override val condition: Condition,
    override val actionsAfter: List<AssignMark>,
    override val info: ItemInfo?,
    override val serializedId: String? = null,
    override val specialization: RuleSpecializationIndices? = null,
) : TaintConfigurationSource

data class TaintMethodExitSource(
    val method: CommonMethod,
    override val condition: Condition,
    override val actionsAfter: List<AssignMark>,
    override val info: ItemInfo?,
    override val serializedId: String? = null,
    override val specialization: RuleSpecializationIndices? = null,
) : TaintConfigurationSource

data class TaintStaticFieldSource(
    val field: JIRField,
    override val condition: Condition,
    override val actionsAfter: List<AssignMark>,
    override val info: ItemInfo?,
    override val serializedId: String? = null,
    override val specialization: RuleSpecializationIndices? = null,
) : TaintConfigurationSource

sealed interface TaintConfigurationSink : TaintConfigurationItem, CommonTaintConfigurationSink {
    val condition: Condition
    val trackFactsReachAnalysisEnd: List<AssignMark>
}

data class TaintSinkMeta(
    override val message: String,
    override val severity: CommonTaintConfigurationSinkMeta.Severity,
    val cwe: List<Int>?
): CommonTaintConfigurationSinkMeta

data class TaintMethodSink(
    val method: CommonMethod,
    override val condition: Condition,
    override val trackFactsReachAnalysisEnd: List<AssignMark>,
    override val id: String,
    override val meta: TaintSinkMeta,
    override val info: ItemInfo?,
    override val serializedId: String? = null,
    override val specialization: RuleSpecializationIndices? = null,
) : TaintConfigurationSink

data class TaintMethodExitSink(
    val method: CommonMethod,
    override val condition: Condition,
    override val trackFactsReachAnalysisEnd: List<AssignMark>,
    override val id: String,
    override val meta: CommonTaintConfigurationSinkMeta,
    override val info: ItemInfo?,
    override val serializedId: String? = null,
    override val specialization: RuleSpecializationIndices? = null,
) : TaintConfigurationSink

data class TaintMethodEntrySink(
    val method: CommonMethod,
    override val condition: Condition,
    override val trackFactsReachAnalysisEnd: List<AssignMark>,
    override val id: String,
    override val meta: CommonTaintConfigurationSinkMeta,
    override val info: ItemInfo?,
    override val serializedId: String? = null,
    override val specialization: RuleSpecializationIndices? = null,
) : TaintConfigurationSink

data class TaintPassThrough(
    val method: CommonMethod,
    val condition: Condition,
    val actionsAfter: List<Action>,
    override val info: ItemInfo?,
    override val serializedId: String? = null,
    override val specialization: RuleSpecializationIndices? = null,
) : TaintConfigurationItem

data class TaintCleaner(
    val method: CommonMethod,
    val condition: Condition,
    val actionsAfter: List<Action>,
    override val info: ItemInfo?,
    override val serializedId: String? = null,
    override val specialization: RuleSpecializationIndices? = null,
) : TaintConfigurationItem
