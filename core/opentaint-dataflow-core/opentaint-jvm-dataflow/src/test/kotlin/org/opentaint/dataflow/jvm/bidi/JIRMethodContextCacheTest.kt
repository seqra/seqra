package org.opentaint.dataflow.jvm.bidi

import org.opentaint.dataflow.ap.ifds.EmptyMethodContext
import org.opentaint.dataflow.bidi.ap.Direction
import org.opentaint.dataflow.bidi.driver.SeedIndex
import org.opentaint.dataflow.bidi.engine.MethodContextCache
import org.opentaint.dataflow.bidi.engine.RunConfig
import org.opentaint.dataflow.bidi.engine.RunMethodContext
import org.opentaint.dataflow.bidi.engine.SharedObjects
import org.opentaint.dataflow.bidi.store.ConcurrentVulnerabilityStore
import org.opentaint.dataflow.bidi.store.DemandStore
import org.opentaint.dataflow.bidi.store.PersistentRecordStore
import org.opentaint.dataflow.ifds.SingletonUnit
import org.opentaint.dataflow.ifds.UnitResolver
import org.opentaint.dataflow.jvm.ap.ifds.JIRArgumentTypeMethodContext
import org.opentaint.dataflow.jvm.ap.ifds.TypeConstraintInfo
import org.opentaint.dataflow.util.RefManager
import org.opentaint.ir.api.common.CommonMethod
import org.opentaint.ir.api.common.cfg.CommonInst
import org.opentaint.ir.api.jvm.cfg.JIRAssignInst
import org.opentaint.ir.api.jvm.cfg.JIRInst
import org.opentaint.ir.api.jvm.ext.cfg.callExpr
import kotlin.test.*

class JIRMethodContextCacheTest : BidiJIRTestUtils() {
    private fun source(): JIRMethodContextCache = JIRMethodContextCache(interp, entries)

    @Test fun `JVM source returns canonical context forms owned by the actual interpreter entries`(): Unit {
        val method = method("copy")
        val a = key(method, EmptyMethodContext)
        val b = key(method, JIRArgumentTypeMethodContext(0, TypeConstraintInfo(method.enclosingClass, exactType = false)))
        val source = source()
        assertSame(interp.forms(a), source.forms(a))
        assertSame(interp.forms(b), source.forms(b))
        assertSame(source.forms(a), source.forms(key(method)))
        assertNotSame(source.forms(a), source.forms(b))
    }

    @Test fun `JVM source shares method graphs across contexts and keeps direction specific graph owners`(): Unit {
        val method = method("fieldRead")
        val a = key(method)
        val b = key(method, JIRArgumentTypeMethodContext(0, TypeConstraintInfo(method.enclosingClass, exactType = false)))
        val source = source()
        for (direction in Direction.entries) {
            assertSame(entries[method].graph(direction), source.graph(a, direction))
            assertSame(source.graph(a, direction), source.graph(b, direction))
        }
        assertNotSame(source.graph(a, Direction.FORWARD), source.graph(a, Direction.BACKWARD))
        assertNotSame(source.graph(a, Direction.FORWARD), source.graph(key(method("copy")), Direction.FORWARD))
    }

    @Test fun `common cache reads actual JVM cached statement and call reversals`(): Unit {
        val cache = MethodContextCache(interp, source())
        val copy = method("copy")
        val copyKey = key(copy)
        val assignment = copy.instList.instructions.filterIsInstance<JIRAssignInst>().last()
        val caller = method("staticCall", "sample.bidi.BidiCallSample")
        val callerKey = key(caller)
        val call = caller.instList.instructions.first { it.callExpr != null }
        for (direction in Direction.entries) {
            val directed = cache.directed(direction)
            assertSame(interp.forms(copyKey).statement(direction, assignment), directed.statement(copyKey, assignment))
            assertSame(interp.forms(callerKey).call(direction, call, checkNotNull(call.callExpr)),
                directed.call(callerKey, call, checkNotNull(call.callExpr)))
            assertSame(entries[caller].graph(direction), cache.graph(callerKey, direction))
        }
    }

    @Test fun `run contexts distinguish actual JVM normal exceptional and backward entry boundaries`(): Unit {
        val method = method("throwValue")
        val key = key(method)
        val records = PersistentRecordStore(manager)
        val cache = MethodContextCache(interp, source())
        SharedObjects(manager, ops, interp, languageManager, cache, records, ConcurrentVulnerabilityStore(manager),
            UnitResolver<CommonMethod> { SingletonUnit }, RefManager(), manager.cancellation, threads = 1).use { shared ->
            val forward = RunConfig(1, 1, null, records, SeedIndex.EMPTY, listOf(key))
            val f = RunMethodContext(key, cache.directed(Direction.FORWARD), forward, shared)
            assertTrue(f.isEnd(normalExit(method)))
            assertFalse(f.isEnd(exceptionalExit(method)))
            assertTrue(f.isExceptionalExit(exceptionalExit(method)))
            val backward = RunConfig(2, 1, DemandStore.Builder(manager).build(), records, SeedIndex.EMPTY, listOf(key))
            val b = RunMethodContext(key, cache.directed(Direction.BACKWARD), backward, shared)
            assertTrue(b.isEnd(key.statement))
            assertFalse(b.isEnd(normalExit(method)))
            assertFalse(b.isExceptionalExit(exceptionalExit(method)))
        }
    }

    @Test fun `run context uses real JVM call lookup and forward graph successors`(): Unit {
        val method = method("staticCall", "sample.bidi.BidiCallSample")
        val key = key(method)
        val cache = MethodContextCache(interp, source())
        val records = PersistentRecordStore(manager)
        SharedObjects(manager, ops, interp, languageManager, cache, records, ConcurrentVulnerabilityStore(manager),
            UnitResolver<CommonMethod> { SingletonUnit }, RefManager(), manager.cancellation, threads = 1).use { shared ->
            val context = RunMethodContext(key, cache.directed(Direction.FORWARD),
                RunConfig(1, 1, null, records, SeedIndex.EMPTY, listOf(key)), shared)
            val call = method.instList.instructions.first { it.callExpr != null }
            assertSame(checkNotNull(call.callExpr), context.callAt(call))
            assertNull(context.callAt(normalExit(method)))
            val successors = arrayListOf<CommonInst>()
            context.forEachSuccessor(key.statement) { successors += it }
            assertEquals(graph.methodGraph(method).successors(assertIs<JIRInst>(key.statement)).toSet(), successors.toSet())
            assertTrue(successors.isNotEmpty())
        }
    }
}
