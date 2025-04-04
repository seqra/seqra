package org.opentaint.ir.taint.configuration

import org.opentaint.ir.api.JIRMethod

sealed interface TaintConfigurationItem

data class TaintEntryPointSource(
    val methodInfo: JIRMethod,
    val condition: Condition,
    val actionsAfter: List<Action>,
) : TaintConfigurationItem

data class TaintMethodSource(
    val methodInfo: JIRMethod,
    val condition: Condition,
    val actionsAfter: List<Action>,
) : TaintConfigurationItem

data class TaintMethodSink(
    val condition: Condition,
    val methodInfo: JIRMethod,
) : TaintConfigurationItem

data class TaintPassThrough(
    val methodInfo: JIRMethod,
    val condition: Condition,
    val actionsAfter: List<Action>,
) : TaintConfigurationItem

data class TaintCleaner(
    val methodInfo: JIRMethod,
    val condition: Condition,
    val actionsAfter: List<Action>,
) : TaintConfigurationItem

