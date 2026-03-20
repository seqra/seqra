package org.opentaint.dataflow.ap.ifds.analysis

import org.opentaint.dataflow.ap.ifds.Edge

interface MethodEdgePostProcessor {
    fun process(edge: Edge): List<Edge>
}
