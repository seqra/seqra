package org.opentaint.dataflow.bidi.interp

import org.opentaint.dataflow.ap.ifds.AccessPathBase
import org.opentaint.dataflow.bidi.ap.ConjunctiveEdge
import org.opentaint.dataflow.bidi.ap.ExclusionSet
import org.opentaint.dataflow.bidi.ap.PathEdge
import org.opentaint.dataflow.bidi.ap.TypeFilter
import org.opentaint.dataflow.bidi.ap.revEdge
import java.util.Collections

/** Cached form containers own their contents and retain the identities of supplied forms and filters. */
class StatementSummary(
    touched: Set<AccessPathBase>,
    edges: List<MicroEdge>,
    conjunctions: List<ConjunctiveEdge>,
    typeFilters: Map<AccessPathBase, TypeFilter>,
    resultFilters: Map<AccessPathBase, TypeFilter> = emptyMap(),
) {
    val touched: Set<AccessPathBase> = Collections.unmodifiableSet(LinkedHashSet(touched))
    val edges: List<MicroEdge> = Collections.unmodifiableList(ArrayList(edges))
    val conjunctions: List<ConjunctiveEdge> = Collections.unmodifiableList(ArrayList(conjunctions))
    val typeFilters: Map<AccessPathBase, TypeFilter> = Collections.unmodifiableMap(LinkedHashMap(typeFilters))
    val resultFilters: Map<AccessPathBase, TypeFilter> = Collections.unmodifiableMap(LinkedHashMap(resultFilters))

    private val byBase: Map<AccessPathBase, List<MicroEdge>> = this.edges.groupBy { it.edge.from.base }
        .mapValues { (_, group) -> Collections.unmodifiableList(ArrayList(group)) }

    fun edgesOf(b: AccessPathBase): List<MicroEdge> = byBase[b].orEmpty()

    val targets: Set<AccessPathBase> = Collections.unmodifiableSet(LinkedHashSet<AccessPathBase>().also { bases ->
        this.edges.mapTo(bases) { it.edge.to.base }
        this.conjunctions.mapTo(bases) { it.target.base }
    })

    /** Conjunctive sources reverse into independent demand branches. Forward filters do not reverse. */
    fun reversed(): StatementSummary {
        val rev = ArrayList<MicroEdge>()
        for (e in edges) rev += MicroEdge(revOrFail(e.edge), e.forward)
        for (c in conjunctions) for (lit in c.literals) {
            val forward = PathEdge(lit.fact, c.target, ExclusionSet.Empty)
            rev += MicroEdge(revOrFail(forward), forward, conjunctive = true)
        }
        val back = StatementSummary(touched + targets, rev, emptyList(), emptyMap())
        return back.withIdentities(passOverBases(targets, touched))
    }

    fun withIdentities(bases: Collection<AccessPathBase>): StatementSummary =
        if (bases.isEmpty()) this
        else StatementSummary(touched + bases, edges + bases.map(::identityEdge), conjunctions, typeFilters, resultFilters)

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
