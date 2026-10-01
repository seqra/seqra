package org.opentaint.dataflow.ap.ifds.access

import org.opentaint.dataflow.ap.ifds.AccessPathBase
import org.opentaint.dataflow.ap.ifds.Accessor
import org.opentaint.dataflow.ap.ifds.ExclusionSet
import org.opentaint.dataflow.ap.ifds.FactTypeChecker
import org.opentaint.dataflow.ap.ifds.FieldAccessor
import org.opentaint.dataflow.ap.ifds.TaintMarkAccessor
import org.opentaint.dataflow.ap.ifds.access.automata.AutomataApManager
import org.opentaint.dataflow.ap.ifds.access.tree.TreeApManager
import org.opentaint.dataflow.util.Cancellation
import org.opentaint.dataflow.util.RefManager
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

class AnyFieldExclusionDepthContractTest {
    private val base = AccessPathBase.Argument(0)
    private val field = FieldAccessor("Box", "f", "Box")
    private val mark = TaintMarkAccessor("tainted")

    private object UnrollStrategy : AnyAccessorUnrollStrategy {
        override fun unrollAccessor(accessor: Accessor): Boolean = false
    }

    private fun managers(): List<ApManager> = listOf(
        TreeApManager(UnrollStrategy, RefManager(), Cancellation()),
        AutomataApManager(UnrollStrategy, Cancellation()),
    )

    private fun ApManager.abstractFact(vararg accessors: Accessor): FinalFactAp =
        accessors.foldRight(mostAbstractFinalAp(base)) { accessor, fact -> fact.prependAccessor(accessor) }

    private fun ApManager.delta(fact: FinalFactAp): FinalFactAp.Delta =
        fact.delta(mostAbstractInitialAp(base)).first { !it.isEmpty }

    private fun ApManager.markDelta(vararg prefix: Accessor): FinalFactAp.Delta =
        delta((prefix.toList() + mark).foldRight(createFinalAp(base, ExclusionSet.Empty)) { accessor, fact ->
            fact.prependAccessor(accessor)
        })

    private fun FinalFactAp?.hasMarkAt(vararg accessors: Accessor): Boolean {
        var fact = this ?: return false
        for (accessor in accessors) {
            fact = fact.readAccessor(accessor) ?: return false
        }
        return fact.startsWithAccessor(mark)
    }

    @Test
    fun `an any-field clean keeps the mark only at the level it starts from`() {
        for (manager in managers()) {
            val name = manager::class.simpleName
            val cleanedAtRoot = manager.abstractFact().clearAllAccessorOccurrences(mark, keepStartAccessor = true)
            assertNotNull(cleanedAtRoot, "$name removed an abstract fact")

            assertEquals(
                true,
                cleanedAtRoot.concat(FactTypeChecker.Dummy, manager.markDelta()).hasMarkAt(),
                "$name dropped the mark at the cleaned level",
            )
            assertEquals(
                false,
                cleanedAtRoot.concat(FactTypeChecker.Dummy, manager.markDelta(field)).hasMarkAt(field),
                "$name kept the mark below the cleaned level",
            )
        }
    }

    @Test
    fun `an any-field clean above the abstraction point removes the mark at the abstraction point`() {
        for (manager in managers()) {
            val name = manager::class.simpleName
            val cleaned = manager.abstractFact(field).clearAllAccessorOccurrences(mark, keepStartAccessor = true)
            assertNotNull(cleaned, "$name removed an abstract fact")

            assertEquals(
                false,
                cleaned.concat(FactTypeChecker.Dummy, manager.markDelta()).hasMarkAt(field),
                "$name kept the mark one level below the cleaned position",
            )
        }
    }

    @Test
    fun `a field delta moves the any-field clean below the level it starts from`() {
        for (manager in managers()) {
            val name = manager::class.simpleName
            val cleanedAtRoot = manager.abstractFact().clearAllAccessorOccurrences(mark, keepStartAccessor = true)
            assertNotNull(cleanedAtRoot, "$name removed an abstract fact")

            val extended = cleanedAtRoot.concat(FactTypeChecker.Dummy, manager.delta(manager.abstractFact(field)))
            assertNotNull(extended, "$name removed the extended fact")

            assertEquals(
                false,
                extended.concat(FactTypeChecker.Dummy, manager.markDelta()).hasMarkAt(field),
                "$name kept the mark one level below the cleaned position after a nested summary",
            )
        }
    }
}
