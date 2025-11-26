package org.opentaint.dataflow.configuration.jvm

import org.opentaint.ir.api.common.CommonMethod
import org.opentaint.ir.api.jvm.JIRField
import org.opentaint.dataflow.configuration.CommonTaintConfigurationItem
import org.opentaint.dataflow.configuration.CommonTaintConfigurationSink
import org.opentaint.dataflow.configuration.CommonTaintConfigurationSinkMeta
import org.opentaint.dataflow.configuration.CommonTaintConfigurationSource

sealed interface TaintConfigurationItem: CommonTaintConfigurationItem

sealed interface TaintConfigurationSource : TaintConfigurationItem, CommonTaintConfigurationSource {
    val condition: Condition
    val actionsAfter: List<AssignMark>
}

data class TaintEntryPointSource(
    val method: CommonMethod,
    override val condition: Condition,
    override val actionsAfter: List<AssignMark>,
) : TaintConfigurationSource

data class TaintMethodSource(
    val method: CommonMethod,
    override val condition: Condition,
    override val actionsAfter: List<AssignMark>,
) : TaintConfigurationSource

data class TaintStaticFieldSource(
    val field: JIRField,
    override val condition: Condition,
    override val actionsAfter: List<AssignMark>,
) : TaintConfigurationSource

sealed interface TaintConfigurationSink : TaintConfigurationItem, CommonTaintConfigurationSink {
    val condition: Condition
}

data class TaintSinkMeta(
    override val message: String,
    override val severity: CommonTaintConfigurationSinkMeta.Severity,
    val cwe: List<Int>?
): CommonTaintConfigurationSinkMeta

data class TaintMethodSink(
    val method: CommonMethod,
    override val condition: Condition,
    override val id: String,
    override val meta: TaintSinkMeta,
) : TaintConfigurationSink

data class TaintMethodExitSink(
    val method: CommonMethod,
    override val condition: Condition,
    override val id: String,
    override val meta: CommonTaintConfigurationSinkMeta,
) : TaintConfigurationSink

data class TaintMethodEntrySink(
    val method: CommonMethod,
    override val condition: Condition,
    override val id: String,
    override val meta: CommonTaintConfigurationSinkMeta,
) : TaintConfigurationSink

data class TaintPassThrough(
    val method: CommonMethod,
    val condition: Condition,
    val actionsAfter: List<Action>,
) : TaintConfigurationItem

data class TaintCleaner(
    val method: CommonMethod,
    val condition: Condition,
    val actionsAfter: List<Action>,
) : TaintConfigurationItem
