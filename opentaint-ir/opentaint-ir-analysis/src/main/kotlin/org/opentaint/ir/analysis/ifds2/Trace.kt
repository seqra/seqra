package org.opentaint.ir.analysis.ifds2

data class TraceGraph<Fact>(
    val sink: Vertex<Fact>,
    val sources: Set<Vertex<Fact>>,
    val edges: Map<Vertex<Fact>, Set<Vertex<Fact>>>,
)
