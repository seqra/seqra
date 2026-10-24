package org.opentaint.dataflow.configuration.selection

import java.util.Collections

/** Selection roles, independent of language binding and analyzer direction. */
enum class RuleRole { SOURCE, SINK, CLEANER, PASS }

/** A complete immutable mark identity. A group alone never replaces this key. */
data class RuleMarkIdentity(val owner: String, val primitive: Boolean, val name: String)

/** Compiler information only. This plan does not restrict legal runtime marks. */
class ReducedMarkPlan(
    liveMarkNames: Set<String>?,
    knownMarkNames: Set<String>,
    val rounds: Int = 0,
    val removedRows: Int = 0,
    val removedAssignments: Int = 0,
    val inputRows: Int = -1,
    val normalizedRows: Int = -1,
    val assignedMarkCount: Int = -1,
    val checkedMarkCount: Int = -1,
) {
    val liveMarkNames: Set<String>? = liveMarkNames?.frozen()
    val knownMarkNames: Set<String> = knownMarkNames.frozen()
    val protectAll: Boolean get() = liveMarkNames == null

    /** A genuinely unknown generated/custom name stays on the old path. */
    fun isLive(name: String): Boolean = liveMarkNames?.let { name !in knownMarkNames || name in it } ?: true

    companion object {
        val ALL = ReducedMarkPlan(null, emptySet())
    }
}

/** One provider view publishes one complete selection generation after joining readers. */
class RuleSelectionSnapshot(
    val generation: Long,
    executionIds: Set<String>,
    metadataIds: Set<String>,
    knownIds: Set<String>,
    val markPlan: ReducedMarkPlan,
    val viewId: Long = 0,
) {
    val executionIds: Set<String> = executionIds.frozen()
    val metadataIds: Set<String> = metadataIds.frozen()
    val knownIds: Set<String> = knownIds.frozen()

    fun keepsRaw(id: String?, role: RuleRole, allRelevant: Boolean): Boolean = when {
        role == RuleRole.PASS -> true
        role == RuleRole.CLEANER && !allRelevant -> true
        id == null -> true
        allRelevant -> id in metadataIds || id !in knownIds
        else -> id in executionIds
    }

    fun withMarkPlan(plan: ReducedMarkPlan): RuleSelectionSnapshot =
        RuleSelectionSnapshot(generation, executionIds, metadataIds, knownIds, plan, viewId)
}

internal fun <T> Set<T>.frozen(): Set<T> = Collections.unmodifiableSet(LinkedHashSet(this))
