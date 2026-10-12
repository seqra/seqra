package org.opentaint.dataflow.bidi.interp

import org.opentaint.dataflow.ap.ifds.AccessPathBase
import org.opentaint.dataflow.bidi.ap.ConjunctiveEdge
import org.opentaint.dataflow.bidi.ap.ExclusionSet
import org.opentaint.dataflow.bidi.ap.PathEdge
import org.opentaint.dataflow.bidi.ap.TypeFilter
import org.opentaint.dataflow.bidi.ap.revEdge

/** Cached form containers own their contents and retain the identities of supplied forms and filters. */
class StatementSummary(
    touched: Set<AccessPathBase>,
    edges: List<MicroEdge>,
    conjunctions: List<ConjunctiveEdge>,
    typeFilters: Map<AccessPathBase, TypeFilter>,
    resultFilters: Map<AccessPathBase, TypeFilter> = emptyMap(),
    sources: List<SourceRuleOccurrence> = emptyList(),
) {
    val touched: Set<AccessPathBase> = ownedSet(touched)
    val edges: List<MicroEdge> = ownedList(edges)
    val conjunctions: List<ConjunctiveEdge> = ownedList(conjunctions)
    val typeFilters: Map<AccessPathBase, TypeFilter> = ownedMap(typeFilters)
    val resultFilters: Map<AccessPathBase, TypeFilter> = ownedMap(resultFilters)
    val sources: List<SourceRuleOccurrence> = ownedList(sources)

    private val byBase: Map<AccessPathBase, List<MicroEdge>> = ownedOrdinalGroups(this.edges) { it.edge.from.base }

    fun edgesOf(b: AccessPathBase): List<MicroEdge> = byBase[b].orEmpty()

    internal fun storage(counts: FormStorageCounts) {
        counts.statementSummaries++
        counts.include(touched)
        counts.include(edges)
        counts.include(conjunctions)
        counts.include(typeFilters)
        counts.include(resultFilters)
        counts.include(sources)
        counts.include(targets)
        counts.summaryBaseDirectoryEntries += byBase.size.toLong()
        ownedDirectoryStorage(byBase, counts)
        for (source in sources) counts.include(source.actions)
    }

    val targets: Set<AccessPathBase> = run {
        val sourceTargets = this.sources.flatMap { source -> source.actions.map { it.target.base } }
        ownedSet(ownedList(this.edges.size + this.conjunctions.size + sourceTargets.size) { index ->
            if (index < this.edges.size) this.edges[index].edge.to.base
            else if (index < this.edges.size + this.conjunctions.size) this.conjunctions[index - this.edges.size].target.base
            else sourceTargets[index - this.edges.size - this.conjunctions.size]
        })
    }

    /** Conjunctive sources reverse into independent demand branches. Forward filters do not reverse. */
    fun reversed(): StatementSummary {
        val rev = ArrayList<MicroEdge>()
        for (e in edges) rev += MicroEdge(revOrFail(e.edge), e.forward)
        for (c in conjunctions) for (lit in c.literals) {
            val forward = PathEdge(lit.fact, c.target, ExclusionSet.Empty)
            rev += MicroEdge(revOrFail(forward), forward, conjunctive = true)
        }
        val back = StatementSummary(touched + targets, rev, emptyList(), emptyMap(), sources = sources.map { it.reversed() })
        return back.withIdentities(passOverBases(targets, touched))
    }

    fun withIdentities(bases: Collection<AccessPathBase>): StatementSummary {
        if (bases.isEmpty()) return this
        val added = ownedList(bases)
        val combined = ownedList(edges.size + added.size) { index ->
            if (index < edges.size) edges[index] else identityEdge(added[index - edges.size])
        }
        return StatementSummary(touched + added, combined, conjunctions, typeFilters, resultFilters, sources)
    }

    companion object {
        val EMPTY = StatementSummary(emptySet(), emptyList(), emptyList(), emptyMap())
        fun identities(bases: Collection<AccessPathBase>): StatementSummary = EMPTY.withIdentities(bases)
    }
}

/** Every supplied statement edge must have an ordinary mark reversal (I11). */
internal fun revOrFail(e: PathEdge): PathEdge =
    revEdge(e) ?: error("not mark-reversible (interpreter.md I11 (b)): $e")

/** A forward target that was not killed keeps its requirement in the reversed form (A5). */
internal fun passOverBases(targets: Iterable<AccessPathBase>, touched: Set<AccessPathBase>): Set<AccessPathBase> =
    targets.filterTo(LinkedHashSet()) { it !in touched }

fun identityEdge(b: AccessPathBase): MicroEdge = MicroEdge.of(keepEdge(b))
