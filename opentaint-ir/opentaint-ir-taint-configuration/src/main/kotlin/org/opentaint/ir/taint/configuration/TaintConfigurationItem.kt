package org.opentaint.ir.taint.configuration

import org.opentaint.ir.api.JIRMethod

sealed interface TaintConfigurationItem

data class TaintEntryPointSource(
    val method: JIRMethod,
    val condition: Condition,
    val actionsAfter: List<Action>,
) : TaintConfigurationItem

data class TaintMethodSource(
    val method: JIRMethod,
    val condition: Condition,
    val actionsAfter: List<Action>,
) : TaintConfigurationItem

data class TaintMethodSink(
    val method: JIRMethod,
    val ruleNote: String,
    val cwe: List<Int>,
    val condition: Condition,
) : TaintConfigurationItem

data class TaintPassThrough(
    val method: JIRMethod,
    val condition: Condition,
    val actionsAfter: List<Action>,
) : TaintConfigurationItem

data class TaintCleaner(
    val method: JIRMethod,
    val condition: Condition,
    val actionsAfter: List<Action>,
) : TaintConfigurationItem
