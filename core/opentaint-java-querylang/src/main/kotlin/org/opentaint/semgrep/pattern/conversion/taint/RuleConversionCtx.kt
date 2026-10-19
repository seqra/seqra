package org.opentaint.semgrep.pattern.conversion.taint

import org.opentaint.dataflow.configuration.jvm.serialized.SinkMetaData
import org.opentaint.semgrep.pattern.SemgrepRuleLoadStepTrace
import org.opentaint.semgrep.pattern.conversion.LanguageTypeOps
import org.opentaint.semgrep.pattern.Mark

class RuleConversionCtx(
    val ruleId: String,
    val modeModifier: String?,
    val meta: SinkMetaData,
    val trace: SemgrepRuleLoadStepTrace,
    val typeOps: LanguageTypeOps,
    val topLevelRuleId: String = ruleId,
    private val createdMarkOwners: MutableMap<String, String> = linkedMapOf(),
) {
    private var nextSerializedItemIndex: Int = 0

    fun nextSerializedItemId(type: String): String =
        "$ruleId:$type:${nextSerializedItemIndex++}"

    fun child(ruleId: String): RuleConversionCtx =
        RuleConversionCtx(ruleId, modeModifier, meta, trace, typeOps, topLevelRuleId, createdMarkOwners)

    fun markPrefix(idx: Int, classifier: String? = null): Mark.RuleUniqueMarkPrefix =
        Mark.RuleUniqueMarkPrefix(ruleId, modeModifier, idx, classifier) { mark ->
            val name = mark.taintMarkStr()
            val old = createdMarkOwners.putIfAbsent(name, topLevelRuleId)
            check(old == null || old == topLevelRuleId) { "Conflicting generated mark owner: $name" }
        }

    fun markOwners(): Map<String, String> = createdMarkOwners.toMap()
}
