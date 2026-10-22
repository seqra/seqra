package org.opentaint.dataflow.bidi.interp

import org.opentaint.dataflow.ap.ifds.AccessPathBase
import org.opentaint.dataflow.bidi.ap.ConjunctiveEdge
import org.opentaint.dataflow.bidi.ap.TypeFilter
import org.opentaint.dataflow.bidi.ap.Layer
import org.opentaint.ir.api.common.cfg.CommonInst

/** The place controls statement-only requests and source-seed hooks. */
data class Place(val node: CommonInst, val statementEdge: Boolean, val sources: Boolean,
                 val site: ResumeSite? = null, val staticOperations: List<StaticOperation> = emptyList(),
                 val callerLayer: Layer? = null, val identityCandidate: Boolean? = null,
                 val pendingDemotion: Boolean = false,
                 val completedAction: ActionId? = null,
                 val completedConjunction: ConjunctiveEdge? = null)

/** One implementation of form application for tree and per-path fact algebras. */
interface FactAlgebra<P, F> {
    fun base(c: F): AccessPathBase
    fun filter(c: F, filter: TypeFilter): F?
    fun filtered(premise: P, before: F, after: F) = Unit
    fun operationResult(me: MicroEdge?, premise: P, input: F, result: F, at: Place, out: (P, F) -> Unit) = out(premise, result)
    fun applyEdge(me: MicroEdge, c: F, premise: P, statementEdge: Boolean, out: (P, F) -> Unit)
    fun reached(s: StatementSummary, premise: P, c: F, at: Place) = Unit
    fun applyEdgeAt(me: MicroEdge, c: F, premise: P, at: Place, out: (P, F) -> Unit) =
        applyEdge(me, c, premise, at.statementEdge, out)
    fun conjunction(cj: ConjunctiveEdge, premise: P, c: F, node: CommonInst, out: (P, F) -> Unit)
    fun conjunctionAt(cj: ConjunctiveEdge, premise: P, c: F, at: Place, out: (P, F) -> Unit) =
        conjunction(cj, premise, c, at.node, out)
    fun sourcePredicate(source: SourceRuleOccurrence, premise: P, c: F, node: CommonInst,
                        statementEdge: Boolean, out: (P, F) -> Unit) {
        error("This fact algebra does not implement rule predicates")
    }
    fun sourcePredicateAt(source: SourceRuleOccurrence, premise: P, c: F, at: Place, out: (P, F) -> Unit) =
        sourcePredicate(source, premise, c, at.node, at.statementEdge, out)
    /** Invocation-only action identity survives the result filter. Legacy
     * algebras keep their existing source callback and operation behavior. */
    fun sourcePredicateActionsAt(source: SourceRuleOccurrence, premise: P, c: F, at: Place,
                                 out: (P, F, ActionId?) -> Unit) =
        sourcePredicateAt(source, premise, c, at) { pr, result -> out(pr, result, null) }
    fun allowsSource(node: CommonInst, me: MicroEdge): Boolean
    fun sourceHit(node: CommonInst, me: MicroEdge)
}

class FormApplier<P, F>(private val alg: FactAlgebra<P, F>) {
    /** An untouched input passes unchanged; conjunction literals can still read it. */
    fun statement(s: StatementSummary, premise: P, c: F, at: Place, sink: (P, F) -> Unit,
                  untouched: (P, F) -> Unit = sink) {
        alg.reached(s, premise, c, at.copy(staticOperations = s.staticOperations))
        if (alg.base(c) !in s.touched) {
            for (cj in s.conjunctions) conjunction(s, cj, premise, c, at, sink)
            for (source in s.sources) source(s, source, premise, c, at, sink)
            untouched(premise, c)
            return
        }
        stage(s, premise, c, at) { pr, result, _ -> sink(pr, result) }
    }

    /** A stage emits generated results, with the edge that made each plain result. */
    fun stage(s: StatementSummary, premise: P, c: F, at: Place, emit: (P, F, MicroEdge?) -> Unit) {
        val place = at.copy(staticOperations = s.staticOperations)
        alg.reached(s, premise, c, place)
        val input = filterBy(s.typeFilters, premise, c) ?: return
        for (me in s.edgesOf(alg.base(input))) {
            micro(me, input, premise, place) { pr, result ->
                filterBy(s.resultFilters, pr, result)?.let { filtered ->
                    alg.operationResult(me, pr, input, filtered, place) { outputPremise, output -> emit(outputPremise, output, me) }
                }
            }
        }
        for (cj in s.conjunctions) conjunction(s, cj, premise, input, place) { pr, result -> emit(pr, result, null) }
        for (source in s.sources) source(s, source, premise, input, place) { pr, result -> emit(pr, result, null) }
    }

    /** GEN adds results. Its caller retains the input and handles end-fact triggers. */
    fun gen(edges: List<MicroEdge>, premise: P, c: F, sink: (P, F) -> Unit) {
        for (me in edges) if (me.edge.from.base == alg.base(c))
            alg.applyEdge(me, c, premise, statementEdge = false, sink)
    }

    private fun conjunction(s: StatementSummary, cj: ConjunctiveEdge, premise: P, c: F,
                            at: Place, sink: (P, F) -> Unit) {
        alg.conjunctionAt(cj, premise, c, at.copy(staticOperations = s.staticOperations)) { pr, result ->
            filterBy(s.resultFilters, pr, result)?.let { filtered ->
                alg.operationResult(null, pr, c, filtered,
                    at.copy(staticOperations = s.staticOperations, completedConjunction = cj), sink)
            }
        }
    }

    private fun source(s: StatementSummary, source: SourceRuleOccurrence, premise: P, c: F,
                       at: Place, sink: (P, F) -> Unit) {
        alg.sourcePredicateActionsAt(source, premise, c, at.copy(staticOperations = s.staticOperations)) { pr, result, action ->
            filterBy(s.resultFilters, pr, result)?.let { filtered ->
                alg.operationResult(null, pr, c, filtered,
                    at.copy(staticOperations = s.staticOperations, completedAction = action), sink)
            }
        }
    }

    private fun filterBy(filters: Map<AccessPathBase, TypeFilter>, premise: P, c: F): F? {
        val filter = filters[alg.base(c)] ?: return c
        return alg.filter(c, filter)?.also { alg.filtered(premise, c, it) }
    }

    /** A source hit precedes the first downstream callback, including result filtering. */
    private fun micro(me: MicroEdge, c: F, premise: P, at: Place, sink: (P, F) -> Unit) {
        val source = at.sources && me.isSource
        if (source && !alg.allowsSource(at.node, me)) return
        var hit = !source
        alg.applyEdgeAt(me, c, premise, at) { pr, result ->
            if (!hit) { hit = true; alg.sourceHit(at.node, me) }
            sink(pr, result)
        }
    }
}
