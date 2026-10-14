package org.opentaint.dataflow.bidi.fixtures

import org.opentaint.dataflow.bidi.ap.*
import org.opentaint.dataflow.bidi.ap.ReferenceRequestKind as RequestKind
import org.opentaint.dataflow.bidi.interp.ZERO_PATTERN
import kotlin.test.*

/** Explicit redesign scope, separate from strict stored-union equality. Eager static policy roots
 *  can add internal passthrough summaries. We retain every raw observation, compare all nonstatic
 *  work/effects and root-zero static effects, and keep complete witness/support/proof headers.
 *  Static registration traffic is deliberately replaced; nonstatic registrations stay compared. */
fun catalogObservables(value: RunObservation, roots: List<MethodKey>): RunObservation {
    fun keep(edge: ObservedEdge): Boolean = edge.conclusion.fact.base != STATIC ||
        (edge.method in roots && edge.premise == setOf(ZERO_PATTERN))
    return RunObservation(
        processed = value.processed.filter(::keep).toSet(),
        initials = value.initials.filter { it.initial.fact.base != STATIC }.toSet(),
        rawSummaries = value.rawSummaries.filter(::keep).toSet(),
        publications = value.publications.filter(::keep).toSet(),
        selectedDemand = value.selectedDemand,
        links = value.links.filter { it.added.fact.base != STATIC }.toSet(),
        requests = value.requests.filter { it.kind !is RequestKind.Position && it.premise.fact.base != STATIC }.toSet(),
        sourceHits = value.sourceHits, witnesses = value.witnesses,
    )
}

/** An interest is not a supplier. Check each eager initial against full raw incoming link leaves
 *  using independent per-path overlap and answer rules, without production indexes/selectors. */
fun assertCatalogSupplierAdmission(actual: RunObservation) {
    for (initial in actual.initials.filter { it.initial.fact.base == STATIC }) {
        val i = initial.initial
        val suppliers = actual.links.filter { it.callee == initial.method && it.added.fact.base == STATIC }.map { it.added }
        assertTrue(suppliers.isNotEmpty(), "A catalog is not a supplier: $initial")
        assertEquals(ExclusionSet.Empty, i.exclusion)
        assertTrue(i.fact.tail != Tail.ANY_TAINT, "Run 1 never admits a must premise")
        if (i.fact.mark is MarkSlot.Star) {
            assertEquals(MarkSlot.STAR, i.fact.mark)
            assertEquals(Tail.STAR, i.fact.tail)
            assertTrue(i.fact.path.size <= 2)
            assertTrue(i.fact.path.isEmpty() || suppliers.any { it.fact.path.startsWith(i.fact.path) },
                "An abstract proper ancestor cannot manufacture a deeper initial: $initial")
        } else {
            val mark = (i.fact.mark as MarkSlot.Concrete).mark
            assertTrue(suppliers.any { supplier ->
                if (supplier.fact.mark != MarkSlot.Concrete(mark)) false
                else {
                    val chains = (0..minOf(2, supplier.fact.path.size)).map { supplier.fact.path.take(it) } +
                        listOfNotNull(i.fact.path.takeIf { it.size <= 2 })
                    chains.any { path ->
                        val chain = Pattern(PathFact(STATIC, path, Tail.STAR, MarkSlot.STAR), ExclusionSet.Empty)
                        overlap(chain, supplier) && answer(chain, supplier, mark) == i
                    }
                }
            }, "Every concrete answer needs its actual same-mark supplier: $initial")
        }
    }
}

fun RunObservation.assertCatalogRefinement(actual: RunObservation, universe: BoundedDenotation,
                                          roots: List<MethodKey>) {
    catalogObservables(this, roots).assertEquivalent(catalogObservables(actual, roots), universe)
    assertCatalogSupplierAdmission(actual)
    assertTrue(actual.requests.none { it.kind is RequestKind.Position }, "No production Position protocol remains")
}
