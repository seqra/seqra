package org.opentaint.common.sast.rules

import mu.KLogging
import org.opentaint.semgrep.pattern.TaintRuleFromSemgrep
import org.opentaint.dataflow.configuration.selection.MarkCatalog
import org.opentaint.dataflow.configuration.selection.ReducedMarkPlan
import org.opentaint.dataflow.configuration.selection.RuleMarkIdentity
import org.opentaint.dataflow.configuration.selection.RuleRole
import org.opentaint.dataflow.configuration.selection.RuleSelectionBoundary
import org.opentaint.dataflow.configuration.selection.RuleSelectionSnapshot
import org.opentaint.dataflow.configuration.selection.allReferencedMarks
import org.opentaint.dataflow.configuration.selection.reduceMarkCatalog
import java.util.concurrent.atomic.AtomicLong
import org.opentaint.semgrep.pattern.TaintRuleFromSemgrep.Structure
import org.opentaint.semgrep.pattern.TaintRuleFromSemgrep.TaintRuleGroup

abstract class SemgrepRuleProvider<RuleItem, ResolvedRule>(
    val rules: List<TaintRuleFromSemgrep<RuleItem>>
) {
    private data class Views(val ordinary: RuleSelectionSnapshot, val bidi: RuleSelectionSnapshot)
    @Volatile private var views: Views? = null
    private val viewId = nextView.incrementAndGet()
    private var generation = 0L
    private data class SelectionInput(
        val execution: Set<String>, val metadata: Set<String>, val known: Set<String>,
        val ordinaryCatalog: MarkCatalog?, val bidiCatalog: MarkCatalog?,
        val identities: Map<String, RuleMarkIdentity>, val ambiguous: Boolean,
    )
    private var lastInput: SelectionInput? = null

    protected val selectionSnapshot: RuleSelectionSnapshot? get() = views?.ordinary
    protected val bidiSelectionSnapshot: RuleSelectionSnapshot? get() = views?.bidi

    /** Complete loaded owner catalog, without resolving methods or fields.
     * Unknown/custom owners retain TOP and the old late-selection path. */
    protected open fun executableMarkCatalog(ignoreEndFacts: Boolean): MarkCatalog? = null

    /** Full raw references include CM, GEN, clean/copy and relevant mark info.
     * Null means unknown, never an empty set of ownership dependencies. */
    protected open fun ruleItemMarkReferences(item: RuleItem): Set<String>? = null

    /** Both public selection entrypoints reach the owned resolver install.
     * Identical policy publication still validates the installed owner/view,
     * while adapters keep its caches unchanged. */
    protected open fun selectionPublished() = Unit

    @Synchronized
    fun selectRelevantSemgrepRules(ruleIds: Set<String>) {
        val reductions = rules.map { reduce(it.root, ruleIds) }
        val execution = reductions.flatMapTo(linkedSetOf()) { it.retainedRuleIds }
        val known = rules.flatMapTo(linkedSetOf()) { graph ->
            graph.taintRules.flatMap { it.rules }.mapNotNull { it.ruleItemId() }
        }
        val completeBoundary = RuleSelectionBoundary.isComplete
        fun boundaryCatalog(catalog: MarkCatalog?): MarkCatalog? =
            if (completeBoundary || catalog == null) catalog else catalog.copy(protectAll = true)
        val ordinaryCatalog = boundaryCatalog(executableMarkCatalog(ignoreEndFacts = false))
        val bidiCatalog = boundaryCatalog(executableMarkCatalog(ignoreEndFacts = true))
        val catalogs = listOfNotNull(ordinaryCatalog, bidiCatalog)
        val referencesById = linkedMapOf<String, MutableSet<String>>()
        for (catalog in catalogs) for (row in catalog.rows) row.itemId?.let { id ->
            referencesById.getOrPut(id) { linkedSetOf() }.addAll(row.allReferencedMarks())
        }
        val owners = linkedMapOf<String, String>()
        val primitive = linkedMapOf<String, Boolean>()
        var ambiguous = false
        for (graph in rules) {
            for ((name, owner) in graph.markOwners) {
                val old = owners.putIfAbsent(name, owner)
                if (old != null && old != owner) ambiguous = true
            }
            for ((owner, flag) in graph.primitiveByOwner) {
                val old = primitive.putIfAbsent(owner, flag)
                if (old != null && old != flag) ambiguous = true
            }
        }
        val facts = rules.map { graph ->
            val names = linkedSetOf<String>()
            var uncertain = false
            val ids = linkedSetOf<String>()
            for (item in graph.taintRules.flatMap { it.rules }) {
                val id = item.ruleItemId()
                id?.let(ids::add)
                val references = ruleItemMarkReferences(item) ?: id?.let(referencesById::get)
                if (references == null) uncertain = true else names += references
            }
            names += graph.markOwners.keys
            val ownerKeys = linkedSetOf<String>()
            graph.markOwners.values.mapTo(ownerKeys) { "rule:$it" }
            graph.primitiveByOwner.keys.mapTo(ownerKeys) { "rule:$it" }
            names.mapTo(ownerKeys) { name -> owners[name]?.let { "rule:$it" } ?: "@shared-model" }
            GraphFacts(ids, names, ownerKeys, uncertain)
        }
        val externalReferences = linkedSetOf<String>()
        for (catalog in catalogs) {
            externalReferences += catalog.protectedProducers
            externalReferences += catalog.protectedConsumers
            for (row in catalog.rows) if (row.itemId == null || row.itemId !in known)
                externalReferences += row.allReferencedMarks()
        }
        if (owners.any { (name, owner) -> primitive[owner]?.let { it != name.endsWith("%%primitive%%") } == true })
            ambiguous = true
        val allMetadata = ambiguous || catalogs.isEmpty() || catalogs.any { catalog ->
            catalog.protectAll || catalog.rows.any { row -> row.protectAll &&
                (row.role == RuleRole.PASS || row.role == RuleRole.CLEANER || row.itemId == null || row.itemId in execution) }
        }
        val supported = metadataSupport(facts, reductions, execution, externalReferences, allMetadata)
        val metadata = linkedSetOf<String>()
        supported.forEach { index -> metadata += facts[index].ids }
        val names = linkedSetOf<String>()
        facts.forEach { names += it.marks }
        catalogs.forEach { catalog ->
            names += catalog.declaredMarks
            catalog.rows.forEach { names += it.allReferencedMarks() }
            names += catalog.protectedProducers
            names += catalog.protectedConsumers
        }
        val identities = names.associateWith { name ->
            RuleMarkIdentity(owners[name]?.let { "rule:$it" } ?: "@shared-model",
                name.endsWith("%%primitive%%"), name)
        }
        val input = SelectionInput(execution, metadata, known, ordinaryCatalog, bidiCatalog, identities, ambiguous)
        if (input == lastInput) {
            selectionPublished()
            return // same complete view, including the raw owner body generation
        }
        val selectedGeneration = ++generation
        val unnormalized = RuleSelectionSnapshot(selectedGeneration, execution, metadata, known,
            ReducedMarkPlan.ALL, viewId)
        fun plan(catalog: MarkCatalog?): ReducedMarkPlan = when {
            catalog == null || ambiguous -> ReducedMarkPlan(null, names)
            else -> reduceMarkCatalog(catalog,
                keepExecution = { unnormalized.keepsRaw(it.itemId, it.role, allRelevant = false) },
                identities = identities)
        }
        val ordinary = unnormalized.withMarkPlan(plan(ordinaryCatalog))
        val bidi = unnormalized.withMarkPlan(plan(bidiCatalog))
        // The caller joins old readers before this synchronous generation swap.
        views = Views(ordinary, bidi)
        lastInput = input
        selectionPublished()
        logger.info {
            fun stats(label: String, plan: ReducedMarkPlan) =
                "$label(rows=${plan.inputRows}->${plan.normalizedRows},assigned=${plan.assignedMarkCount}," +
                    "checked=${plan.checkedMarkCount},used=${plan.liveMarkNames?.size ?: -1}," +
                    "rounds=${plan.rounds},protectAll=${plan.protectAll})"
            "SEMGREP_SELECTION view=$viewId generation=$selectedGeneration " +
                "S=${execution.size} K=${known.size} M=${metadata.size} " +
                stats("ordinary", ordinary.markPlan) + " " + stats("bidi", bidi.markPlan)
        }
    }

    private data class GraphFacts(val ids: Set<String>, val marks: Set<String>, val owners: Set<String>, val uncertain: Boolean)

    /** Indexed finite support growth. A final closure check retains ALL on any
     * incomplete result; it does not guess an ownership boundary from a name. */
    private fun metadataSupport(
        facts: List<GraphFacts>, reductions: List<Reduction>, execution: Set<String>,
        external: Set<String>, all: Boolean,
    ): Set<Int> {
        if (all) return facts.indices.toSet()
        val byOwner = hashMapOf<String, MutableSet<Int>>()
        val byMark = hashMapOf<String, MutableSet<Int>>()
        val byId = hashMapOf<String, MutableSet<Int>>()
        facts.forEachIndexed { index, graph ->
            graph.owners.forEach { byOwner.getOrPut(it) { linkedSetOf() } += index }
            graph.marks.forEach { byMark.getOrPut(it) { linkedSetOf() } += index }
            graph.ids.forEach { byId.getOrPut(it) { linkedSetOf() } += index }
        }
        val seeds = facts.indices.filterTo(linkedSetOf()) { index ->
            reductions[index].applicable || facts[index].uncertain ||
                facts[index].ids.any { it in execution } || facts[index].marks.any { it in external }
        }
        val selected = LinkedHashSet(seeds)
        val pending = ArrayDeque(seeds)
        fun admit(indices: Set<Int>) {
            for (index in indices) if (selected.add(index)) pending.addLast(index)
        }
        while (pending.isNotEmpty()) {
            val graph = facts[pending.removeFirst()]
            graph.owners.forEach { byOwner[it]?.let(::admit) }
            graph.marks.forEach { byMark[it]?.let(::admit) }
            graph.ids.forEach { byId[it]?.let(::admit) }
        }
        val closed = seeds.all { it in selected } && selected.all { index ->
            val graph = facts[index]
            graph.owners.all { byOwner[it].orEmpty().all(selected::contains) } &&
                graph.marks.all { byMark[it].orEmpty().all(selected::contains) } &&
                graph.ids.all { byId[it].orEmpty().all(selected::contains) }
        }
        return if (closed) selected else facts.indices.toSet()
    }

    private data class Reduction(
        val applicable: Boolean,
        val retainedRuleIds: Set<String>,
    )

    private fun reduce(structure: Structure<RuleItem>, selectedRuleIds: Set<String>): Reduction =
        when (structure) {
            is Structure.Matching -> reduceMatching(structure.groups, selectedRuleIds)
            is Structure.Taint -> reduceTaint(structure, selectedRuleIds)
            is Structure.Join -> reduceJoin(structure, selectedRuleIds)
        }

    private fun reduceMatching(
        groups: List<TaintRuleGroup<RuleItem>>,
        selectedRuleIds: Set<String>,
    ): Reduction {
        val applicableGroups = groups.map { reduceGroup(it, selectedRuleIds) }.filter { it.applicable }
        return Reduction(
            applicable = applicableGroups.isNotEmpty(),
            retainedRuleIds = applicableGroups.flatMapTo(hashSetOf()) { it.retainedRuleIds },
        )
    }

    private fun reduceTaint(
        structure: Structure.Taint<RuleItem>,
        selectedRuleIds: Set<String>,
    ): Reduction {
        val sources = structure.sources.map { reduceGroup(it, selectedRuleIds) }.filter { it.applicable }
        if (structure.sources.isNotEmpty() && sources.isEmpty()) return Reduction(false, emptySet())

        val sinks = structure.sinks.map { reduceGroup(it, selectedRuleIds) }.filter { it.applicable }
        if (structure.sinks.isNotEmpty() && sinks.isEmpty()) return Reduction(false, emptySet())

        val otherGroups = (structure.propagators + structure.sanitizers)
            .map { reduceGroup(it, selectedRuleIds) }
            .filter { it.applicable }
        return Reduction(
            applicable = true,
            retainedRuleIds = (sources + sinks + otherGroups)
                .flatMapTo(hashSetOf()) { it.retainedRuleIds },
        )
    }

    private fun reduceJoin(
        structure: Structure.Join<RuleItem>,
        selectedRuleIds: Set<String>,
    ): Reduction {
        val retainedRuleIds = hashSetOf<String>()
        var applicable = false
        structure.branches.forEach { branch ->
            val right = reduce(branch.rightOperand.child, selectedRuleIds)
            if (!right.applicable) return@forEach

            val left = branch.leftOperands
                .map { reduce(it.child, selectedRuleIds) }
                .filter { it.applicable }
            if (left.isEmpty()) return@forEach

            applicable = true
            retainedRuleIds += right.retainedRuleIds
            left.forEach { retainedRuleIds += it.retainedRuleIds }
        }
        return Reduction(applicable, retainedRuleIds)
    }

    private fun reduceGroup(
        group: TaintRuleGroup<RuleItem>,
        selectedRuleIds: Set<String>,
    ): Reduction {
        val candidates = group.rules.mapNotNull { it.ruleItemId() }.filterTo(hashSetOf()) { it in selectedRuleIds }
        val applicableRuleIds = candidates
            .filterTo(hashSetOf()) { group.ruleDependencies[it].isNullOrEmpty() }
        val dependentsByRuleId = hashMapOf<String, MutableSet<String>>()
        candidates.forEach { ruleId ->
            group.ruleDependencies[ruleId].orEmpty().forEach { dependencyId ->
                dependentsByRuleId.getOrPut(dependencyId) { hashSetOf() }.add(ruleId)
            }
        }

        val worklist = ArrayDeque(applicableRuleIds)
        while (worklist.isNotEmpty()) {
            val applicableRuleId = worklist.removeFirst()
            dependentsByRuleId[applicableRuleId].orEmpty().forEach { dependentId ->
                if (applicableRuleIds.add(dependentId)) {
                    worklist.addLast(dependentId)
                }
            }
        }

        return Reduction(
            applicable = group.finalRuleIds.any { it in applicableRuleIds },
            retainedRuleIds = applicableRuleIds,
        )
    }

    abstract fun RuleItem.ruleItemId(): String?
    abstract fun ResolvedRule.resolvedRuleId(): String?

    fun <T : ResolvedRule> Iterable<T>.select(allRelevant: Boolean): Iterable<T> {
        val snapshot = selectionSnapshot ?: return this
        // This helper is used by the existing source/sink adapters. Cleaner and
        // pass execution continue their explicit broad routes.
        return filter { snapshot.keepsRaw(it.resolvedRuleId(), RuleRole.SOURCE, allRelevant) }
    }

    companion object {
        private val nextView = AtomicLong()
        private val logger = object : KLogging() {}.logger
    }
}
