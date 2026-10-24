package org.opentaint.dataflow.configuration.jvm

data class ExpandedRuleActions<A>(val actions: List<A>, val specialization: RuleSpecializationIndices?)

/** Expand retained actions in the original bound action domain. For example,
 * dropping old action 0 must not turn old action 1's two expansions into 0/1.
 * The known wrapper applies the same deterministic expansion to the original
 * action and its retained value. This helper does not specialize a raw rule. */
fun <A> expandSpecializedActions(
    rule: TaintConfigurationItem,
    actions: List<A>,
    originalActions: (TaintConfigurationItem) -> List<A>,
    expand: (A) -> List<A>,
): ExpandedRuleActions<A> {
    val labels = rule.specialization
        ?: return ExpandedRuleActions(actions.flatMap(expand), null)
    val original = originalActions(labels.original)
    val offsets = IntArray(original.size + 1)
    for (index in original.indices) offsets[index + 1] = offsets[index] + expand(original[index]).size
    val result = ArrayList<A>()
    val ordinals = ArrayList<Int>()
    for ((index, action) in actions.withIndex()) {
        val ordinal = labels.actionOrdinals[index]
        val expanded = expand(action)
        check(ordinal in original.indices && expanded.size == offsets[ordinal + 1] - offsets[ordinal]) {
            "A rule wrapper changed the original action expansion domain"
        }
        result += expanded
        expanded.indices.mapTo(ordinals) { offsets[ordinal] + it }
    }
    return ExpandedRuleActions(result, labels.copy(actionOrdinals = ordinals))
}
