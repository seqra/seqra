package org.opentaint.dataflow.bidi

import org.opentaint.dataflow.ap.ifds.ClassStaticAccessor
import org.opentaint.dataflow.bidi.ap.*
import org.opentaint.dataflow.bidi.engine.*
import org.opentaint.dataflow.bidi.fixtures.StoreFixtures
import org.opentaint.dataflow.bidi.store.RequestMarkSet
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** Roles and deep condition paths remain in forms, but not in admitted requests. */
class CatalogMarkAnswerTest {
    private val f = StoreFixtures()
    private val ap = f.manager
    private val c = ap.accessors.index(ClassStaticAccessor("C"))
    private val g = f.field("g")
    private val h = f.field("h")
    private val t = f.mark("T")
    private val u = f.mark("U")
    private val slot = Run1Slot.StaticField(c, g)

    @Test
    fun `all rule roles and deep query paths collapse to one precomputed field row`() {
        val method = f.key("callee").method
        val interests = StaticMarkRole.entries.flatMap { role -> listOf(t, u).map { mark ->
            StaticMarkInterest(Pattern(PathFact(STATIC, listOf(c, g, h), Tail.EXACT,
                MarkSlot.Concrete(mark)), ExclusionSet.Empty), role, RequestMarkSet.of(mark))
        } }
        val profiles = Run1StaticProfiles.build(ap, mapOf(method to emptyList()),
            mapOf(method to Run1StaticLocalProfile(marks = interests)))
        val profile = profiles[method]
        assertEquals(1L, profile.precomputedRequests.logicalRows)
        assertEquals(RequestMarkSet.fromIds(intArrayOf(t.id, u.id)), profile.precomputedRequests.borrow(slot).toRequestMarkSet {})
        assertTrue(profile.markInterests.isEmpty(), "Prescan query/role scratch must not remain in the runtime plan")
        val input = ap.factsOf(PathFact(STATIC, listOf(c, g), Tail.EXACT, MarkSlot.Concrete(t)),
            ExclusionSet.Empty, Layer.NORMAL)
        val rows = RequestStorage(profile.precomputedRequests).borrowMatchingRows(input, {})
        val answers = linkedSetOf<InitialAp>()
        forEachConcreteAnswer(ap, input, rows) { answers += it }
        assertEquals(setOf(ap.initial(STATIC, ap.path(listOf(c, g)), Tail.EXACT, ExclusionSet.Empty,
            MarkSlot.Concrete(t))), answers)
    }

    @Test
    fun `field must and broad suppliers answer with a chain independent of field exclusion`() {
        val storage = RequestStorage(mapOf<Run1Slot, RequestMarkSet>(slot to RequestMarkSet.of(t)))
        for (path in listOf(emptyList(), listOf(c), listOf(c, g))) {
            val input = ap.factsOf(PathFact(STATIC, path, Tail.ANY_TAINT, MarkSlot.Concrete(t)),
                ExclusionSet.of(g), Layer.NORMAL)
            val answers = linkedSetOf<InitialAp>()
            forEachConcreteAnswer(ap, input, storage.borrowMatchingRows(input, {})) { answers += it }
            assertEquals(setOf(slot.initial(ap).let { ap.initial(it.base, it.path, Tail.STAR,
                ExclusionSet.Empty, MarkSlot.Concrete(t)) }), answers)
            assertEquals(Layer.DEMAND, f.ops.startFact(answers.single()).layer)
        }
    }

    @Test
    fun `exact class and other field suppliers cannot answer the requested field slot`() {
        val storage = RequestStorage(mapOf<Run1Slot, RequestMarkSet>(slot to RequestMarkSet.of(t)))
        for (path in listOf(listOf(c), listOf(c, h))) {
            val input = ap.factsOf(PathFact(STATIC, path, Tail.EXACT, MarkSlot.Concrete(t)),
                ExclusionSet.Empty, Layer.NORMAL)
            val answers = linkedSetOf<InitialAp>()
            forEachConcreteAnswer(ap, input, storage.borrowMatchingRows(input, {})) { answers += it }
            assertTrue(answers.isEmpty())
        }
    }
}
