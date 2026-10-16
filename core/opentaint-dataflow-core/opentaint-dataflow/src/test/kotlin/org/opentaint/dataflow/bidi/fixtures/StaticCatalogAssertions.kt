package org.opentaint.dataflow.bidi.fixtures

import org.opentaint.dataflow.bidi.ap.*
import org.opentaint.dataflow.bidi.ap.ReferenceRequestKind as RequestKind
import kotlin.test.*

/** Selected semantics compares every static/nonstatic observation; no transport rows are hidden. */
@Suppress("UNUSED_PARAMETER")
fun catalogObservables(value: RunObservation, roots: List<MethodKey>): RunObservation = value

/** Check the independent supply precondition in addition to complete scalar closure equality. */
@Suppress("UNUSED_PARAMETER")
fun assertCatalogSupplierAdmission(actual: RunObservation,
                                   initialAdmission: NaiveClosure.InitialAdmission = NaiveClosure.InitialAdmission.HISTORICAL) {
    for (initial in actual.initials.filter { it.initial.fact.base == STATIC }) {
        val i = initial.initial
        val suppliers = actual.links.filter { it.callee == initial.method && it.added.fact.base == STATIC }.map { it.added }
        assertTrue(suppliers.isNotEmpty(), "A plan is not a supplier: $initial")
        assertEquals(ExclusionSet.Empty, i.exclusion)
        assertTrue(i.fact.tail == Tail.EXACT || i.fact.tail == Tail.STAR, "Run 1 has only EXACT/STAR initials")
        assertTrue(i.fact.path.size in 1..2, "No static root initial is emitted")
        if (i.fact.mark is MarkSlot.Star) {
            assertEquals(MarkSlot.STAR, i.fact.mark)
            assertEquals(Tail.STAR, i.fact.tail)
            assertEquals(2, i.fact.path.size, "Only a normal field can be an abstract static initial")
            assertTrue(suppliers.any { SelectedScalarPlan.geometry(i, it) },
                "Every selected field needs an actual geometric supplier: $initial")
        } else {
            assertTrue(suppliers.any { supplier ->
                supplier.fact.mark == i.fact.mark && SelectedScalarPlan.geometry(i, supplier) &&
                    i.fact.tail == (if (supplier.fact.tail == Tail.EXACT && supplier.fact.path == i.fact.path)
                        Tail.EXACT else Tail.STAR)
            }, "Every concrete answer needs its actual same-mark supplier: $initial")
        }
    }
}

fun RunObservation.assertCatalogRefinement(actual: RunObservation, universe: BoundedDenotation,
                                          roots: List<MethodKey>,
                                          initialAdmission: NaiveClosure.InitialAdmission = NaiveClosure.InitialAdmission.HISTORICAL) {
    assertEquivalent(actual, universe)
    assertCatalogSupplierAdmission(actual, initialAdmission)
    assertTrue(actual.requests.none { it.kind is RequestKind.Position }, "No selected Position protocol remains")
}
