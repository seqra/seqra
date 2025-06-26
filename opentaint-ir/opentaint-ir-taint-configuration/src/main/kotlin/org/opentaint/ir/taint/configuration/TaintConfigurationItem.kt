package org.opentaint.ir.taint.configuration

import org.opentaint.ir.api.common.CommonMethod

sealed interface TaintConfigurationItem

data class TaintEntryPointSource(
    val method: CommonMethod,
    val condition: Condition,
    val actionsAfter: List<Action>,
) : TaintConfigurationItem

data class TaintMethodSource(
    val method: CommonMethod,
    val condition: Condition,
    val actionsAfter: List<Action>,
) : TaintConfigurationItem

data class TaintMethodSink(
    val method: CommonMethod,
    val ruleNote: String,
    val cwe: List<Int>,
    val condition: Condition,
) : TaintConfigurationItem

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
