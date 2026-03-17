package org.opentaint.semgrep.pattern.conversion.taint

import org.opentaint.dataflow.configuration.jvm.serialized.SinkMetaData
import org.opentaint.semgrep.pattern.SemgrepRuleLoadStepTrace

class RuleConversionCtx(
    val ruleId: String,
    val meta: SinkMetaData,
    val trace: SemgrepRuleLoadStepTrace
)
