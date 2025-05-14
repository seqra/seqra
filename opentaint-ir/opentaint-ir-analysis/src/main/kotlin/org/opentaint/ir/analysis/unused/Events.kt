package org.opentaint.ir.analysis.unused

import org.opentaint.ir.analysis.ifds.Edge

sealed interface Event

data class NewSummaryEdge(
    val edge: Edge<Fact>,
) : Event

data class NewVulnerability(
    val vulnerability: Vulnerability,
) : Event
