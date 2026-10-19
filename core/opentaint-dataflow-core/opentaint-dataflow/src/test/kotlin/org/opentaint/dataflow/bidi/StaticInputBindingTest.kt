package org.opentaint.dataflow.bidi

import org.opentaint.dataflow.ap.ifds.AccessPathBase
import org.opentaint.dataflow.bidi.ap.*
import org.opentaint.dataflow.bidi.engine.*
import org.opentaint.dataflow.bidi.fixtures.FiniteProgramFixtures
import org.opentaint.dataflow.bidi.store.CallerRef
import kotlin.test.*

class StaticInputBindingTest {
    @Test fun `a new guard support version refreshes the same qualified input row`() {
        FiniteProgramFixtures().use { f ->
            val caller = f.method("caller", listOf(emptyList()), 0).key()
            val callee = f.method("callee", listOf(emptyList()), 0).key()
            val full = f.mark("T")
            f.ap.marks.finishFixtureOwnershipCensus()
            val group = f.ap.marks.group(f.ap.marks.groupOf(full))
            val facts = f.fact(AccessPathBase.Argument(0), mark = MarkSlot.Concrete(group), layer = Layer.DEMAND)
            val part = Subscription(callee, CallerRef(caller, f.ap.zero, Layer.NORMAL, f.inst(caller, 0)), Layer.DEMAND, facts)
            val directory = CalleeSubscriptions(f.ops)
            val first = assertNotNull(directory.admit(part))
            assertFalse(first.supportOnly)
            assertNull(directory.admit(part))
            val freshSupport = assertNotNull(directory.admit(part.copy(supportRevision = 1)))
            assertTrue(freshSupport.supportOnly)
            assertEquals(first.handle, freshSupport.handle)
            assertEquals(f.ops.leaves(facts).toSet(), f.ops.leaves(freshSupport.currentParts.single().added).toSet())
            assertNull(directory.admit(part.copy(supportRevision = 1)))
            assertNull(directory.admit(part))
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
