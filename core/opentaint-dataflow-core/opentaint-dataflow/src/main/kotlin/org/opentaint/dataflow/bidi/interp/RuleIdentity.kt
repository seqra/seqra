package org.opentaint.dataflow.bidi.interp

import org.opentaint.dataflow.bidi.ap.MethodKey
import org.opentaint.ir.api.common.CommonMethod
import org.opentaint.ir.api.common.cfg.CommonInst

/** One configuration/body generation. It owns no compiled forms or rule registry. */
class FormOwner

/** Physical method forms omit context; entry and call forms include the full method key. */
data class FormKey(
    val owner: FormOwner,
    val method: CommonMethod,
    val context: MethodKey?,
    val statement: CommonInst,
)

enum class RuleKind { CALL_SOURCE, CALL_SINK, ENTRY_SOURCE, ENTRY_SINK, EXIT_SOURCE, EXIT_SINK, CLEANER, REWRITER, PASS_THROUGH, FIELD_SOURCE }

/** The ordinal belongs to a frozen provider candidate order, before validation or filtering. */
data class RuleOccurrenceId(val form: FormKey, val kind: RuleKind, val ordinal: Int) {
    init { require(ordinal >= 0) }
}

/** The same configured action can have different expansions at different application stages. */
enum class ActionRole { SOURCE, END_FACT, BOUND_CLEANER, RETURN_REWRITER, GENERATED_CLEANER, MARK_INPUT, PASS_THROUGH }

data class ActionId(
    val occurrence: RuleOccurrenceId,
    val role: ActionRole,
    val ordinal: Int,
    val relevantMarkOrdinal: Int = 0,
    val generatedStep: Int = 0,
) {
    init { require(ordinal >= 0 && relevantMarkOrdinal >= 0 && generatedStep >= 0) }
}

/** Field/string-byte substeps are separate even when their concrete tuples are equal. */
data class TemporaryKey(val action: ActionId, val substep: Int = 0) {
    init { require(substep >= 0) }
}

/** A structural support shape, allocated only after facts support a predicate branch. */
class ProofKey(val rule: RuleOccurrenceId, leaves: List<Int>, choices: List<Int> = emptyList()) {
    private val leaves = leaves.toIntArray()
    private val choices = choices.toIntArray()
    private val hash = 31 * (31 * rule.hashCode() + this.leaves.contentHashCode()) + this.choices.contentHashCode()
    override fun equals(other: Any?): Boolean = other is ProofKey &&
        rule == other.rule && leaves.contentEquals(other.leaves) && choices.contentEquals(other.choices)
    override fun hashCode(): Int = hash
}
