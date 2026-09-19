package org.opentaint.dataflow.taint

import org.opentaint.dataflow.ap.ifds.AccessPathBase
import org.opentaint.dataflow.ap.ifds.Accessor
import org.opentaint.dataflow.ap.ifds.MethodEntryPoint
import java.util.concurrent.ConcurrentHashMap

/**
 * The accessors already demanded by answers to a [TaintMarkFieldUnfoldRequest].
 *
 * A request asks one question: is [mark] hidden under the `[any]` of this frame's initial fact?
 * Answering it asks the frame to split that abstraction on the accessors the answerer found. The
 * demand for a question therefore only grows, and an answer that contributes nothing new has not
 * failed to answer -- it has repeated an answer already given.
 *
 * Contributing an accessor a second time is not a refinement: the split it asks for produces a
 * fact that ends in `[any]` again, one accessor further down, which re-raises the same question.
 * On a self-similar shape -- `CharSequence#content`, or `MapKey`/`MapValue`/`Element` over erased
 * generics, where the type checker has nothing to stop on -- that loop has no fixed point, and
 * each round re-abstracts the whole accumulated fact tree of the base and re-broadcasts a side
 * effect requirement across the asking frame's transitive callers.
 *
 * One instance per analysed method, held on its
 * [org.opentaint.dataflow.ap.ifds.analysis.MethodAnalysisContext]. A request climbs through many
 * frames, and each frame it passes through filters against its own record -- so two frames are
 * never made to agree about a question, and nothing here is shared across the analysis.
 * `ConcurrentHashMap.newKeySet().add` is still the atomic step, so workers sharing one method's
 * context cannot both be told the same accessor is fresh.
 *
 * Within a method the question is keyed on the ASKING frame (the one the request came from, not
 * the one holding this map), the base the abstraction sits on, and the mark -- NOT on the
 * current refinement of the fact, which is itself the product of earlier answers. That is where
 * the precision goes: `arg0.*` and `arg0.x.*` are the same question here, so a frame already
 * asked to split on `y` is not asked again below `x`, and a flow needing `arg0.x.y` is not
 * reported. Keying on the refinement instead makes every round of the iteration its own question
 * and the filter a no-op -- which is exactly the loop it is here to cut, so the conflation is
 * load-bearing rather than an oversight.
 */
class MarkUnfoldDemand {
    private data class Question(
        /** The frame the request came from, which is not in general the frame holding this map. */
        val method: MethodEntryPoint,
        val base: AccessPathBase,
        val mark: Accessor,
    )

    private val demanded = ConcurrentHashMap<Question, MutableSet<Accessor>>()

    /**
     * Records [accessors] as demanded for the question and returns those that were not demanded
     * before. An empty result means this answer repeats one already given.
     */
    fun demand(
        method: MethodEntryPoint,
        base: AccessPathBase,
        mark: Accessor,
        accessors: Collection<Accessor>,
    ): List<Accessor> {
        if (accessors.isEmpty()) return emptyList()

        val alreadyDemanded = demanded.computeIfAbsent(Question(method, base, mark)) {
            ConcurrentHashMap.newKeySet()
        }

        return accessors.filter { alreadyDemanded.add(it) }
    }
}
