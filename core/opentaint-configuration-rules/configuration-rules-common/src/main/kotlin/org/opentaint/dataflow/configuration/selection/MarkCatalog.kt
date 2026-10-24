package org.opentaint.dataflow.configuration.selection

/** The query token retains the complete raw CM variant and position. Other
 * atoms stay opaque to this pass; it evaluates no method/type/field condition. */
sealed interface MarkExpression {
    data class Truth(val value: Boolean) : MarkExpression
    data class ContainsMark(val name: String, val query: Any? = null) : MarkExpression
    data class Other(val atom: Any?) : MarkExpression
    data class Not(val argument: MarkExpression) : MarkExpression
    data class And(val arguments: List<MarkExpression>) : MarkExpression
    data class Or(val arguments: List<MarkExpression>) : MarkExpression
}

/** Original raw binding/action skeletons remain outside this compiler model.
 * references includes metadata-only keys for support sharing, never P or C. */
data class MarkCatalogRow(
    val itemId: String?,
    val role: RuleRole,
    val condition: MarkExpression,
    val assignments: List<String>,
    val otherActionCount: Int,
    val referencedMarks: Set<String> = emptySet(),
    val protectedProducers: Set<String> = emptySet(),
    val protectedConsumers: Set<String> = emptySet(),
    val protectAll: Boolean = false,
)

data class MarkCatalog(
    val rows: List<MarkCatalogRow>,
    val protectedProducers: Set<String> = emptySet(),
    val protectedConsumers: Set<String> = emptySet(),
    val protectAll: Boolean = false,
    val declaredMarks: Set<String> = emptySet(),
    val sourceGeneration: Long = 0,
)

fun MarkExpression.checkedMarks(destination: MutableSet<String>) {
    when (this) {
        is MarkExpression.ContainsMark -> destination += name
        is MarkExpression.Not -> argument.checkedMarks(destination)
        is MarkExpression.And -> arguments.forEach { it.checkedMarks(destination) }
        is MarkExpression.Or -> arguments.forEach { it.checkedMarks(destination) }
        is MarkExpression.Truth, is MarkExpression.Other -> Unit
    }
}

fun MarkCatalogRow.allReferencedMarks(): Set<String> = linkedSetOf<String>().also {
    condition.checkedMarks(it)
    it += assignments
    it += referencedMarks
    it += protectedProducers
    it += protectedConsumers
}

/** Run once on the raw symbolic catalogue before the decreasing loop.
 * This is Boolean NNF only: no cube/argument/method specialization. */
fun MarkExpression.simplify(): MarkExpression = toNnf(false)

private fun MarkExpression.toNnf(negative: Boolean): MarkExpression = when (this) {
    is MarkExpression.Truth -> MarkExpression.Truth(value != negative)
    is MarkExpression.Not -> argument.toNnf(!negative)
    is MarkExpression.And -> simplifyJunction(arguments.map { it.toNnf(negative) }, !negative)
    is MarkExpression.Or -> simplifyJunction(arguments.map { it.toNnf(negative) }, negative)
    is MarkExpression.ContainsMark, is MarkExpression.Other -> if (negative) MarkExpression.Not(this) else this
}

private fun simplifyJunction(arguments: List<MarkExpression>, and: Boolean): MarkExpression {
    val flattened = ArrayList<MarkExpression>()
    for (argument in arguments) when {
        and && argument is MarkExpression.And -> flattened += argument.arguments
        !and && argument is MarkExpression.Or -> flattened += argument.arguments
        else -> flattened += argument
    }
    val retained = linkedSetOf<MarkExpression>()
    for (argument in flattened) {
        if (argument is MarkExpression.Truth) {
            if (argument.value != and) return argument
            continue
        }
        val opposite = if (argument is MarkExpression.Not) argument.argument else MarkExpression.Not(argument)
        if (opposite in retained) return MarkExpression.Truth(!and)
        retained += argument
    }
    return when (retained.size) {
        0 -> MarkExpression.Truth(and)
        1 -> retained.single()
        else -> if (and) MarkExpression.And(retained.toList()) else MarkExpression.Or(retained.toList())
    }
}

/** Constant propagation is shared with the catalogue analysis, not a rewrite
 * of raw actions/AnyArgument binding contexts or relevant metadata guards. */
fun MarkExpression.foldAbsent(isLive: (String) -> Boolean): MarkExpression = when (this) {
    is MarkExpression.Truth, is MarkExpression.Other -> this
    is MarkExpression.ContainsMark -> if (isLive(name)) this else MarkExpression.Truth(false)
    is MarkExpression.Not -> when (val folded = argument.foldAbsent(isLive)) {
        is MarkExpression.Truth -> MarkExpression.Truth(!folded.value)
        else -> if (folded === argument) this else MarkExpression.Not(folded)
    }
    is MarkExpression.And -> foldJunction(arguments, isLive, and = true)
    is MarkExpression.Or -> foldJunction(arguments, isLive, and = false)
}

private fun foldJunction(
    arguments: List<MarkExpression>, isLive: (String) -> Boolean, and: Boolean,
): MarkExpression {
    val retained = ArrayList<MarkExpression>(arguments.size)
    for (argument in arguments) {
        val folded = argument.foldAbsent(isLive)
        if (folded is MarkExpression.Truth) {
            if (folded.value != and) return folded
        } else retained += folded
    }
    return when (retained.size) {
        0 -> MarkExpression.Truth(and)
        1 -> retained.single()
        else -> if (and) MarkExpression.And(retained) else MarkExpression.Or(retained)
    }
}

/** Pure executable fixed point. All assignments and checks use complete
 * canonical names with validated owner/primitive identity. Unknown scope uses
 * TOP. Metadata rows are not an input and cannot keep executable marks live. */
fun reduceMarkCatalog(
    catalog: MarkCatalog,
    keepExecution: (MarkCatalogRow) -> Boolean = { true },
    identities: Map<String, RuleMarkIdentity> = emptyMap(),
): ReducedMarkPlan {
    val known = linkedSetOf<String>().also { names ->
        names += catalog.declaredMarks
        names += catalog.protectedProducers
        names += catalog.protectedConsumers
        catalog.rows.forEach { names += it.allReferencedMarks() }
    }
    val selectedRows = catalog.rows.filter(keepExecution)
    if (catalog.protectAll || selectedRows.any { it.protectAll })
        return ReducedMarkPlan(null, known, inputRows = selectedRows.size, normalizedRows = selectedRows.size)
    // An identity map may be omitted for legacy full names. If supplied, a
    // name must denote that same complete key; no group/member enumeration.
    if (identities.any { (name, key) -> key.name != name }) return ReducedMarkPlan(null, known)
    fun key(name: String): RuleMarkIdentity = identities[name] ?:
        RuleMarkIdentity(SHARED_MODEL_OWNER, name.endsWith(PRIMITIVE_SUFFIX), name)

    var rows = selectedRows.map { row ->
        val simplified = row.condition.simplify()
        if (simplified == row.condition) row else row.copy(condition = simplified)
    }
    val originalRows = rows.size
    val originalAssignments = rows.sumOf { it.assignments.size }
    var rounds = 0
    while (true) {
        val assigned = linkedSetOf<RuleMarkIdentity>()
        catalog.protectedProducers.mapTo(assigned, ::key)
        val checked = linkedSetOf<RuleMarkIdentity>()
        catalog.protectedConsumers.mapTo(checked, ::key)
        for (row in rows) {
            if (row.condition != MarkExpression.Truth(false)) {
                row.assignments.mapTo(assigned, ::key)
                row.protectedProducers.mapTo(assigned, ::key)
                row.protectedConsumers.mapTo(checked, ::key)
            }
            val checks = linkedSetOf<String>()
            row.condition.checkedMarks(checks)
            checks.mapTo(checked, ::key)
        }
        val live = assigned.intersect(checked)
        val next = ArrayList<MarkCatalogRow>(rows.size)
        for (row in rows) {
            val condition = row.condition.foldAbsent { key(it) in live }
            if (condition == MarkExpression.Truth(false)) continue
            val assignments = row.assignments.filter { key(it) in live }
            if ((row.role == RuleRole.SOURCE || row.role == RuleRole.CLEANER) &&
                assignments.isEmpty() && row.otherActionCount == 0) continue
            next += if (condition == row.condition && assignments == row.assignments) row
                else row.copy(condition = condition, assignments = assignments)
        }
        rounds++
        if (next == rows) {
            return ReducedMarkPlan(
                live.mapTo(linkedSetOf()) { it.name }, known, rounds,
                originalRows - rows.size, originalAssignments - rows.sumOf { it.assignments.size },
                originalRows, rows.size, assigned.size, checked.size,
            )
        }
        // Every changed round removes a row/assignment or reduces expression
        // structure. The constructive structural proof supplies termination.
        rows = next
    }
}

private const val SHARED_MODEL_OWNER = "@shared-model"
private const val PRIMITIVE_SUFFIX = "%%primitive%%"
