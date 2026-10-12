package org.opentaint.dataflow.bidi.interp

import org.opentaint.dataflow.ap.ifds.AccessPathBase
import org.opentaint.dataflow.bidi.ap.ConjunctiveEdge
import org.opentaint.dataflow.bidi.ap.TypeFilter
import org.opentaint.ir.api.common.cfg.CommonInst

/** The place controls statement-only requests and source-seed hooks. */
data class Place(val node: CommonInst, val statementEdge: Boolean, val sources: Boolean)

/** One implementation of form application for tree and per-path fact algebras. */
interface FactAlgebra<P, F> {
    fun base(c: F): AccessPathBase
    fun filter(c: F, filter: TypeFilter): F?
    fun applyEdge(me: MicroEdge, c: F, premise: P, statementEdge: Boolean, out: (P, F) -> Unit)
    fun conjunction(cj: ConjunctiveEdge, premise: P, c: F, node: CommonInst, out: (P, F) -> Unit)
    fun sourcePredicate(source: SourceRuleOccurrence, premise: P, c: F, node: CommonInst,
                        statementEdge: Boolean, out: (P, F) -> Unit) {
        error("This fact algebra does not implement rule predicates")
    }
    fun allowsSource(node: CommonInst, me: MicroEdge): Boolean
    fun sourceHit(node: CommonInst, me: MicroEdge)
}

class FormApplier<P, F>(private val alg: FactAlgebra<P, F>) {
    /** An untouched input passes unchanged; conjunction literals can still read it. */
    fun statement(s: StatementSummary, premise: P, c: F, at: Place, sink: (P, F) -> Unit,
                  untouched: (P, F) -> Unit = sink) {
        if (alg.base(c) !in s.touched) {
            for (cj in s.conjunctions) conjunction(s, cj, premise, c, at.node, sink)
            for (source in s.sources) source(s, source, premise, c, at, sink)
            untouched(premise, c)
            return
        }
        stage(s, premise, c, at) { pr, result, _ -> sink(pr, result) }
    }

    /** A stage emits generated results, with the edge that made each plain result. */
    fun stage(s: StatementSummary, premise: P, c: F, at: Place, emit: (P, F, MicroEdge?) -> Unit) {
        val input = filterBy(s.typeFilters, c) ?: return
        for (me in s.edgesOf(alg.base(input))) {
            micro(me, input, premise, at) { pr, result ->
                filterBy(s.resultFilters, result)?.let { emit(pr, it, me) }
            }
        }
        for (cj in s.conjunctions) conjunction(s, cj, premise, input, at.node) { pr, result -> emit(pr, result, null) }
        for (source in s.sources) source(s, source, premise, input, at) { pr, result -> emit(pr, result, null) }
    }

    /** GEN adds results. Its caller retains the input and handles end-fact triggers. */
    fun gen(edges: List<MicroEdge>, premise: P, c: F, sink: (P, F) -> Unit) {
        for (me in edges) if (me.edge.from.base == alg.base(c))
            alg.applyEdge(me, c, premise, statementEdge = false, sink)
    }

    private fun conjunction(s: StatementSummary, cj: ConjunctiveEdge, premise: P, c: F,
                            node: CommonInst, sink: (P, F) -> Unit) {
        alg.conjunction(cj, premise, c, node) { pr, result ->
            filterBy(s.resultFilters, result)?.let { sink(pr, it) }
        }
    }

    private fun source(s: StatementSummary, source: SourceRuleOccurrence, premise: P, c: F,
                       at: Place, sink: (P, F) -> Unit) {
        alg.sourcePredicate(source, premise, c, at.node, at.statementEdge) { pr, result ->
            filterBy(s.resultFilters, result)?.let { sink(pr, it) }
        }
    }

    private fun filterBy(filters: Map<AccessPathBase, TypeFilter>, c: F): F? {
        val filter = filters[alg.base(c)] ?: return c
        return alg.filter(c, filter)
    }

    /** A source hit precedes the first downstream callback, including result filtering. */
    private fun micro(me: MicroEdge, c: F, premise: P, at: Place, sink: (P, F) -> Unit) {
        val source = at.sources && me.isSource
        if (source && !alg.allowsSource(at.node, me)) return
        var hit = !source
        alg.applyEdge(me, c, premise, at.statementEdge) { pr, result ->
            if (!hit) { hit = true; alg.sourceHit(at.node, me) }
            sink(pr, result)
        }
    }
}
