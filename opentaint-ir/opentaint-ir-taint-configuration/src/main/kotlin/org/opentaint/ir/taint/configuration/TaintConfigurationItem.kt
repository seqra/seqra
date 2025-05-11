package org.opentaint.ir.taint.configuration

import org.opentaint.ir.api.JIRField
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

data class TaintFieldSource(
    val field: JIRField,
    val condition: Condition,
    val actionsAfter: List<Action>,
) : TaintConfigurationItem

data class TaintMethodSink(
    val method: JIRMethod,
    val ruleNote: String,
    val cwe: List<Int>,
    val condition: Condition,
) : TaintConfigurationItem

data class TaintFieldSink(
    val field: JIRField,
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

val TaintConfigurationItem.condition: Condition
    get() = when (this) {
        is TaintEntryPointSource -> condition
        is TaintMethodSource -> condition
        is TaintFieldSource -> condition
        is TaintMethodSink -> condition
        is TaintFieldSink -> condition
        is TaintPassThrough -> condition
        is TaintCleaner -> condition
    }

val TaintConfigurationItem.actionsAfter: List<Action>
    get() = when (this) {
        is TaintEntryPointSource -> actionsAfter
        is TaintMethodSource -> actionsAfter
        is TaintFieldSource -> actionsAfter
        is TaintMethodSink -> emptyList()
        is TaintFieldSink -> emptyList()
        is TaintPassThrough -> actionsAfter
        is TaintCleaner -> actionsAfter
    }
