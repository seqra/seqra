package org.opentaint.dataflow.ap.ifds.analysis

import org.opentaint.dataflow.ap.ifds.Edge

interface MethodSummaryEdgeProcessor {
    fun processSummaryEdge(edge: Edge): List<Edge>
}
