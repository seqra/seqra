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
) : TaintConfigurationSource

data class TaintMethodSource(
    val method: CommonMethod,
    override val condition: Condition,
    override val actionsAfter: List<AssignMark>,
    override val info: ItemInfo?,
) : TaintConfigurationSource

data class TaintMethodExitSource(
    val method: CommonMethod,
    override val condition: Condition,
    override val actionsAfter: List<AssignMark>,
    override val info: ItemInfo?,
) : TaintConfigurationSource

data class TaintStaticFieldSource(
    val field: JIRField,
    override val condition: Condition,
    override val actionsAfter: List<AssignMark>,
    override val info: ItemInfo?,
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
) : TaintConfigurationSink

data class TaintMethodExitSink(
    val method: CommonMethod,
    override val condition: Condition,
    override val trackFactsReachAnalysisEnd: List<AssignMark>,
    override val id: String,
    override val meta: CommonTaintConfigurationSinkMeta,
    override val info: ItemInfo?,
) : TaintConfigurationSink

data class TaintMethodEntrySink(
    val method: CommonMethod,
    override val condition: Condition,
    override val trackFactsReachAnalysisEnd: List<AssignMark>,
    override val id: String,
    override val meta: CommonTaintConfigurationSinkMeta,
    override val info: ItemInfo?,
) : TaintConfigurationSink

data class TaintPassThrough(
    val method: CommonMethod,
    val condition: Condition,
    val actionsAfter: List<Action>,
    override val info: ItemInfo?,
) : TaintConfigurationItem

data class TaintCleaner(
    val method: CommonMethod,
    val condition: Condition,
    val actionsAfter: List<Action>,
    override val info: ItemInfo?,
) : TaintConfigurationItem
