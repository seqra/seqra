package org.opentaint.dataflow.python.analysis

import org.opentaint.dataflow.ap.ifds.Accessor
import org.opentaint.dataflow.ap.ifds.ExclusionSet
import org.opentaint.dataflow.ap.ifds.access.FinalFactAp
import org.opentaint.dataflow.ap.ifds.access.InitialFactAp

/**
 * Accumulates refinement information during flow function evaluation.
 *
 * When an abstract fact (e.g., `arg(0).*` with empty exclusions) is checked for
 * a specific accessor (e.g., a taint mark), the accessor cannot be confirmed present.
 * Instead of over-approximating by assuming it IS present, we record the accessor
 * as a refinement. Later, `refine()` unions these refinements into the fact's
 * exclusion set, which triggers the framework's re-analysis mechanism to produce
 * more specific facts (e.g., `arg(0).![taint].*`).
 *
 * This mirrors the JVM engine's `FinalFactReader` refinement approach.
 *
 * @see org.opentaint.dataflow.jvm.ap.ifds.taint.FinalFactReader
 */
class PIRFactRefinement {
    private var refinement: ExclusionSet = ExclusionSet.Empty

    val hasRefinement: Boolean get() = refinement !is ExclusionSet.Empty

    /**
     * Records an accessor that was expected but not found (hit abstraction point instead).
     */
    fun add(accessor: Accessor) {
        refinement = refinement.add(accessor)
    }

    /**
     * Applies accumulated refinements to an initial fact by unioning the refinement
     * exclusions into the fact's existing exclusions.
     */
    fun refine(factAp: InitialFactAp): InitialFactAp {
        if (!hasRefinement) return factAp
        return factAp.replaceExclusions(factAp.exclusions.union(refinement))
    }

    /**
     * Applies accumulated refinements to a final fact by unioning the refinement
     * exclusions into the fact's existing exclusions.
     */
    fun refine(factAp: FinalFactAp): FinalFactAp {
        if (!hasRefinement) return factAp
        return factAp.replaceExclusions(factAp.exclusions.union(refinement))
    }
}
