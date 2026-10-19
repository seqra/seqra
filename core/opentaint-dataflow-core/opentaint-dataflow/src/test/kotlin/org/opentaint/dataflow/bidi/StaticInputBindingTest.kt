package org.opentaint.dataflow.bidi

import org.opentaint.dataflow.ap.ifds.AccessPathBase
import org.opentaint.dataflow.bidi.ap.*
import org.opentaint.dataflow.bidi.engine.*
import org.opentaint.dataflow.bidi.fixtures.FiniteProgramFixtures
import org.opentaint.dataflow.bidi.store.CallerRef
import kotlin.test.*

class StaticInputBindingTest {
    @Test fun `only a new actual AP membership refreshes an input row`() {
        FiniteProgramFixtures().use { f ->
            val caller = f.method("caller", listOf(emptyList()), 0).key()
            val callee = f.method("callee", listOf(emptyList()), 0).key()
            val full = f.mark("T")
            f.ap.marks.finishFixtureOwnershipCensus()
            val group = f.ap.marks.group(f.ap.marks.groupOf(full))
            fun fact(path: List<Int>) = f.fact(AccessPathBase.Argument(0), path,
                mark = MarkSlot.Concrete(group), layer = Layer.DEMAND)
            val firstFact = fact(listOf(f.field("left")))
            val secondFact = fact(listOf(f.field("right")))
            val part = Subscription(callee, CallerRef(caller, f.ap.zero, Layer.NORMAL, f.inst(caller, 0)),
                Layer.DEMAND, firstFact)
            val directory = CalleeSubscriptions(f.ops)
            val first = assertNotNull(directory.admit(part))
            assertFalse(first.growth)
            assertNull(directory.admit(part))
            val changed = assertNotNull(directory.admit(part.copy(added = secondFact)))
            assertTrue(changed.growth)
            assertEquals((f.ops.leaves(firstFact) + f.ops.leaves(secondFact)).toSet(),
                f.ops.leaves(changed.current.added).toSet())
            assertNull(directory.admit(part))
            assertNull(directory.admit(part.copy(added = secondFact)))
            directory.clear()
        }
    }

    @Test fun `static control ports require the original ZERO and exclude backward zero-only subscriptions`() {
        FiniteProgramFixtures().use { f ->
            val caller = f.method("caller", listOf(listOf(1), listOf(2), emptyList()), 2).key()
            val callee = f.method("callee", listOf(emptyList()), 0).key()
            val ordinary = Subscription(callee, CallerRef(caller, f.ap.zero, Layer.NORMAL, f.inst(caller, 0)), Layer.NORMAL, Reach.NORMAL)
            val nonzero = f.ap.initial(f.pattern(AccessPathBase.Argument(0)))
            val fromNonzero = ordinary.copy(ref = CallerRef(caller, nonzero, Layer.NORMAL, f.inst(caller, 1)))
            val backwardOnly = ordinary.copy(ref = ordinary.ref.copy(call = f.inst(caller, 2)), zeroOnly = true)
            val directory = CalleeSubscriptions(f.ops)
            directory.add(ordinary)
            directory.add(fromNonzero)
            directory.add(backwardOnly)
            assertEquals(listOf(ordinary), directory.staticControlCandidates())
            directory.clear()
        }
    }
}
