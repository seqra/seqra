package org.opentaint.dataflow.bidi.fixtures

import org.opentaint.dataflow.bidi.ap.*
import org.opentaint.dataflow.bidi.interp.*
import kotlin.test.*

/** Explicit operation metadata, not inference from a STATIC keep or alias destination. */
internal fun directStaticWrite(f: FiniteProgramFixtures, owner: MethodKey, node: Int,
                               edge: PathEdge, summary: StatementSummary): StatementSummary {
    val id = OperationId(FormKey(FormOwner(), owner.method, owner, f.inst(owner, node)))
    return summary.withStaticOperations(listOf(StaticOperation.Write(id, Pattern(edge.to, ExclusionSet.Empty), edge)))
}

internal fun directStaticRead(f: FiniteProgramFixtures, owner: MethodKey, node: Int,
                              edge: PathEdge, summary: StatementSummary): StatementSummary {
    val id = OperationId(FormKey(FormOwner(), owner.method, owner, f.inst(owner, node)))
    val operations = arrayListOf<StaticOperation>(StaticOperation.Read(id, edge))
    if (edge.to.base == STATIC) operations += StaticOperation.Write(id, Pattern(edge.to, ExclusionSet.Empty), edge)
    return summary.withStaticOperations(operations)
}

/** Representation and layer laws only. Positive effects and finding identities are pinned by each scenario. */
internal fun assertDirectStaticProtocol(actual: RunObservation, table: MarkTable) {
    assertTrue(actual.initials.none { it.initial.fact.base == STATIC }, "Static descriptions never start a method analysis")
    assertTrue(actual.processed.none { it.conclusion.fact.base == STATIC }, "Static effects never traverse ordinary successor queues")
    assertTrue(actual.requests.none { it.premise.fact.base == STATIC }, "Static interests do not enter ordinary R/P")
    assertTrue(actual.requests.none { it.kind is ReferenceRequestKind.Position })
    for (row in actual.processed + actual.rawSummaries + actual.publications) {
        val mark = row.conclusion.fact.mark as? MarkSlot.Concrete ?: continue
        if (mark.mark == TaintMark.ZERO) continue
        assertTrue(table.isGroup(mark.mark), "Run-1 data uses catalog groups: $row")
        assertTrue(row.conclusion.demand, "Group data remains DEMAND: $row")
    }
}
