package org.opentaint.dataflow.bidi

import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import org.opentaint.dataflow.ap.ifds.AccessPathBase
import org.opentaint.dataflow.bidi.ap.*
import org.opentaint.dataflow.bidi.driver.SeedIndex
import org.opentaint.dataflow.bidi.engine.RunConfig
import org.opentaint.dataflow.bidi.engine.SharedObjects
import org.opentaint.dataflow.bidi.fixtures.EngineShellFixtures
import org.opentaint.dataflow.bidi.interp.Interpreter
import org.opentaint.dataflow.bidi.interp.UnresolvedCallObserver
import org.opentaint.dataflow.bidi.store.DemandStore
import org.opentaint.dataflow.bidi.store.Record
import java.util.concurrent.ExecutorService
import kotlin.test.*
import kotlin.time.Duration.Companion.seconds

class RunConfigTest {
    private val f = EngineShellFixtures()

    @Test fun `positive run rows choose the complete forward backward and seed modes`(): Unit {
        for (index in 1..6) for (limit in listOf(1, 2, 3)) {
            val config = f.config(index, limit)
            val direction = if (index % 2 == 1) Direction.FORWARD else Direction.BACKWARD
            assertEquals(direction, config.direction)
            assertEquals(index == 1, config.run1)
            assertEquals(index > 1, config.restricted)
            assertEquals(index > 1 && index % 2 == 1, config.seededSources)
            assertEquals(index == 1, config.mode.run1)
            assertEquals(direction, config.mode.direction)
            assertEquals(limit, config.mode.fieldLimit)
            assertEquals(index > 1, config.mode.restricted)
            assertEquals(index == 1, config.demand == null)
        }
    }

    @Test fun `nonpositive run indexes are rejected before a mode can be used`(): Unit {
        for (index in listOf(Int.MIN_VALUE, -1, 0))
            assertFailsWith<IllegalArgumentException> { f.config(index, demand = null) }
    }

    @Test fun `every run rejects nonpositive field limits`(): Unit {
        for (index in listOf(1, 2, 3)) for (limit in listOf(Int.MIN_VALUE, -1, 0))
            assertFailsWith<IllegalArgumentException> { f.config(index, limit) }
    }

    @Test fun `only run one has no demand snapshot`(): Unit {
        val demand = DemandStore.Builder(f.manager).build()
        assertFailsWith<IllegalArgumentException> { f.config(1, demand = demand) }
        for (index in listOf(2, 3, 4)) {
            assertFailsWith<IllegalArgumentException> { f.config(index, demand = null) }
            assertSame(demand, f.config(index, demand = demand).demand)
        }
    }

    @Test fun `roots are an owned immutable list with order and multiplicity intact`(): Unit {
        val roots = arrayListOf(f.a, f.b, f.a)
        val config = f.config(roots = roots)
        roots.clear()
        assertEquals(listOf(f.a, f.b, f.a), config.roots)
        assertFailsWith<UnsupportedOperationException> { (config.roots as MutableList).clear() }
        assertEquals(listOf(f.a, f.b, f.a), config.roots)
        assertSame(SeedIndex.EMPTY, config.seeds)
    }

    @Test fun `constructor wraps a mutable record port in a read only live view`(): Unit {
        val config = RunConfig(1, 1, null, f.records, SeedIndex.EMPTY, emptyList())
        val premise = f.manager.initial(f.pattern())
        val record = Record(f.a, Direction.FORWARD, premise, f.fact(AccessPathBase.Return))
        assertFailsWith<IllegalStateException> { config.records.add(record) }
        assertFailsWith<IllegalStateException> { config.records.persist(Direction.FORWARD, emptySequence()) }
        assertTrue(config.records.byEntry(f.a, premise.toPattern()).none())
        // A barrier write remains visible through the next run's read port; the view is not a deep copy.
        f.records.add(record)
        assertEquals(listOf(record), config.records.byEntry(f.a, premise.toPattern()).toList())
        assertSame(config.records, config.records.view())
    }

    @Test fun `shared services retain analysis owners and create one usable dispatcher`(): Unit = runBlocking {
        f.shared(threads = 2, cancellationTimeout = 2.seconds, diagnostics = true).use { shared ->
            assertSame(f.manager, shared.ap)
            assertSame(f.ops, shared.ops)
            assertSame(f.interpreter, shared.interpreter)
            assertSame(f.language, shared.language)
            assertSame(f.contexts, shared.contexts)
            assertSame(f.records, shared.records)
            assertSame(f.vulnerabilities, shared.vulnerabilities)
            assertSame(f.refManager, shared.refManager)
            assertSame(f.refManager.softRefManager("bidi"), f.manager.softRefs)
            assertSame(f.unitResolver, shared.unitResolver)
            assertSame(f.cancellation, shared.cancellation)
            assertEquals(2.seconds, shared.cancellationTimeout)
            assertTrue(shared.diagnostics)
            assertNull(shared.unresolvedObserver)
            assertTrue(withContext(shared.dispatcher) { Thread.currentThread().name.startsWith("bidi-worker") })
        }
    }

    @Test fun `closing shared objects shuts down the owned executor`(): Unit {
        val shared = f.shared()
        val executor = assertIs<ExecutorService>(shared.dispatcher.executor)
        assertFalse(executor.isShutdown)
        shared.close()
        assertTrue(executor.isShutdown)
    }

    @Test fun `shared construction does not reactivate analysis cancellation`(): Unit {
        f.cancellation.cancel()
        f.shared().use { assertFalse(it.cancellation.isActive()) }
    }

    @Test fun `unresolved observer is the exact optional interpreter hook`(): Unit {
        val seen = arrayListOf<AccessPathBase>()
        val interpreter = object : Interpreter by f.interpreter, UnresolvedCallObserver {
            override fun reached(call: org.opentaint.ir.api.common.cfg.CommonCallExpr, position: AccessPathBase,
                                 plan: org.opentaint.dataflow.bidi.interp.CallPlan) { seen += position }
        }
        SharedObjects(f.manager, f.ops, interpreter, f.language, f.contexts, f.records, f.vulnerabilities,
            f.unitResolver, f.refManager, f.cancellation, threads = 1).use { shared ->
            val observer: UnresolvedCallObserver = interpreter
            assertSame(observer, shared.unresolvedObserver)
            shared.unresolvedObserver!!.reached(f.callExpr, AccessPathBase.Argument(0),
                f.methodForms(f.a).call(Direction.FORWARD, f.inst(f.methodA, 2), f.callExpr))
            assertEquals(listOf<AccessPathBase>(AccessPathBase.Argument(0)), seen)
        }
    }
}
