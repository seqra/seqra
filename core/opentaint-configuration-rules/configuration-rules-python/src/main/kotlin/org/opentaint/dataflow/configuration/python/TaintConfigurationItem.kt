package org.opentaint.dataflow.configuration.python

import org.opentaint.dataflow.configuration.CommonTaintConfigurationItem
import org.opentaint.dataflow.configuration.CommonTaintConfigurationSink
import org.opentaint.dataflow.configuration.CommonTaintConfigurationSinkMeta
import org.opentaint.dataflow.configuration.CommonTaintConfigurationSource
import org.opentaint.dataflow.configuration.python.serialized.ItemInfo

sealed interface TaintConfigurationItem : CommonTaintConfigurationItem {
    val target: Target

    val condition: PIRCondition

    val info: ItemInfo?
}

sealed interface TaintConfigurationSource : TaintConfigurationItem, CommonTaintConfigurationSource {
    val taint: List<TaintAssignAction>
}

sealed interface TaintConfigurationSink : TaintConfigurationItem, CommonTaintConfigurationSink {
    override val meta: TaintSinkMeta

    val trackFactsReachAnalysisEnd: List<TaintAssignAction>
}

sealed interface TaintConfigurationPassThrough : TaintConfigurationItem {
    val copy: List<TaintPassAction>
}

sealed interface TaintConfigurationCleaner : TaintConfigurationItem {
    val cleans: List<TaintCleanAction>

    val forCategory: String?
}

data class TaintEntryPointSource(
    override val target: Target.Function,
    override val condition: PIRCondition,
    override val taint: List<TaintAssignAction>,
    override val info: ItemInfo? = null,
) : TaintConfigurationSource

data class TaintSource(
    override val target: Target,
    override val condition: PIRCondition,
    override val taint: List<TaintAssignAction>,
    override val info: ItemInfo? = null,
) : TaintConfigurationSource

data class TaintSinkMeta(
    override val message: String,
    override val severity: CommonTaintConfigurationSinkMeta.Severity,
    val cwe: List<Int>?,
    val note: String?,
) : CommonTaintConfigurationSinkMeta

data class TaintSink(
    override val target: Target,
    override val condition: PIRCondition,
    override val trackFactsReachAnalysisEnd: List<TaintAssignAction>,
    override val id: String,
    override val meta: TaintSinkMeta,
    override val info: ItemInfo? = null,
) : TaintConfigurationSink

data class TaintExitSink(
    override val target: Target.Function,
    override val condition: PIRCondition,
    override val trackFactsReachAnalysisEnd: List<TaintAssignAction>,
    override val id: String,
    override val meta: TaintSinkMeta,
    override val info: ItemInfo? = null,
) : TaintConfigurationSink

data class TaintPassThrough(
    override val target: Target,
    override val condition: PIRCondition,
    override val copy: List<TaintPassAction>,
    override val info: ItemInfo? = null,
) : TaintConfigurationPassThrough

data class TaintCleaner(
    override val target: Target,
    override val condition: PIRCondition,
    override val cleans: List<TaintCleanAction>,
    override val forCategory: String?,
    override val info: ItemInfo? = null,
) : TaintConfigurationCleaner
