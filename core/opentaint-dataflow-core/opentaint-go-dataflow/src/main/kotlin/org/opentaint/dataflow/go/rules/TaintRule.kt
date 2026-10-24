package org.opentaint.dataflow.go.rules

import org.opentaint.dataflow.configuration.CommonCondition
import org.opentaint.dataflow.configuration.CommonTaintAction
import org.opentaint.dataflow.configuration.CommonTaintConfigurationItem
import org.opentaint.dataflow.configuration.CommonTaintConfigurationSink
import org.opentaint.dataflow.configuration.CommonTaintConfigurationSinkMeta
import org.opentaint.dataflow.configuration.CommonTaintConfigurationSinkMeta.Severity
import org.opentaint.dataflow.configuration.CommonTaintConfigurationSource
import org.opentaint.dataflow.configuration.go.serialized.ItemInfo

sealed interface TaintRule: CommonTaintConfigurationItem {
    val info: ItemInfo?
    val serializedId: String?

    sealed interface GoSourceRule : TaintRule, CommonTaintConfigurationSource {
        val condition: CommonCondition<GoRuleCondition>
        val actionsAfter: List<GoAssignAction>
    }

    data class GlobalReadSource(
        val global: String,
        override val condition: CommonCondition<GoRuleCondition>,
        override val actionsAfter: List<GoAssignAction>,
        override val info: ItemInfo?,
        override val serializedId: String? = null,
    ) : GoSourceRule

    data class FieldReadSource(
        val field: String,
        override val condition: CommonCondition<GoRuleCondition>,
        override val actionsAfter: List<GoAssignAction>,
        override val info: ItemInfo?,
        override val serializedId: String? = null,
    ) : GoSourceRule

    data class Source(
        val function: String,
        override val condition: CommonCondition<GoRuleCondition>,
        override val actionsAfter: List<GoAssignAction>,
        override val info: ItemInfo?,
        override val serializedId: String? = null,
        val effectiveRelevantTaintMarks: Set<String>? = null,
    ) : GoSourceRule

    data class Sink(
        val function: String,
        val condition: CommonCondition<GoRuleCondition>,
        val trackFactsReachAnalysisEnd: List<GoAssignAction>,
        override val id: String,
        override val meta: CommonTaintConfigurationSinkMeta,
        override val info: ItemInfo?,
        override val serializedId: String? = null,
    ) : TaintRule, CommonTaintConfigurationSink {

        data class DefaultMeta(
            override val message: String,
            override val severity: Severity = Severity.Error,
        ) : CommonTaintConfigurationSinkMeta
    }

    data class PassThrough(
        val function: String,
        val actionsAfter: List<GoTaintAction>,
        override val info: ItemInfo?,
        override val serializedId: String? = null,
    ) : TaintRule, CommonTaintConfigurationItem, CommonTaintAction

    data class Cleaner(
        val function: String,
        val condition: CommonCondition<GoRuleCondition>,
        val actionsAfter: List<GoTaintAction>,
        override val info: ItemInfo?,
        override val serializedId: String? = null,
        val effectiveRelevantTaintMarks: Set<String>? = null,
    ) : TaintRule, CommonTaintConfigurationItem, CommonTaintAction
}
