package org.opentaint.dataflow.jvm.bidi

import org.opentaint.dataflow.ap.ifds.EmptyMethodContext
import org.opentaint.dataflow.ap.ifds.MethodWithContext
import org.opentaint.dataflow.ap.ifds.ResolvedPrescanCall
import org.opentaint.dataflow.ap.ifds.TaintAnalysisUnitRunnerManager
import org.opentaint.dataflow.jvm.bidi.fixtures.JIRPrescanFixtures
import org.opentaint.ir.api.jvm.ext.cfg.callExpr
import kotlin.test.*
import kotlin.time.Duration.Companion.seconds

class ResolvedPrescanGraphTest : BidiJIRTestUtils() {
    @Test
    fun `resolved subscription export includes real calls and both frozen lambda targets after release`(): Unit {
        JIRPrescanFixtures(cp, graph).use { f ->
            f.run("rootFirst", "rootSecond")
            val copied = f.engine.prescanResolvedGraph()
            val invoke = f.method("invoke")
            val call = f.call(invoke)
            assertEquals(setOf(f.implementation("first"), f.implementation("second")),
                copied.calls.filter { it.caller == invoke && it.statement == call }.map { it.callee }.toSet())
            val root = f.method("rootFirst")
            val toInvoke = root.instList.instructions.single { it.callExpr?.method?.method == invoke }
            assertTrue(ResolvedPrescanCall(root, toInvoke, invoke) in copied.calls)
            assertTrue(root in copied.methods)
            val calls = copied.calls.toSet()
            f.manager.releasePrescan()
            f.engine.releasePrescan()
            assertEquals(calls, copied.calls)
            assertFailsWith<UnsupportedOperationException> { (copied.calls as MutableSet<ResolvedPrescanCall>).clear() }
            assertFailsWith<UnsupportedOperationException> { (copied.methods as MutableSet<org.opentaint.ir.api.common.CommonMethod>).clear() }
        }
    }

    @Test
    fun `same call in distinct caller contexts is projected to one method edge and sites stay distinct`(): Unit {
        JIRPrescanFixtures(cp, graph).use { f ->
            val caller = f.method("ruleCalls")
            val callee = f.method("target")
            val at = caller.instList.instructions.single { it.callExpr != null }
            val context = org.opentaint.dataflow.jvm.ap.ifds.JIRArgumentTypeMethodContext(0,
                org.opentaint.dataflow.jvm.ap.ifds.TypeConstraintInfo(checkNotNull(cp.findClassOrNull("java.lang.Object")), false))
            val another = f.method("alias")
            val otherCallee = f.method("normal")
            val otherAt = another.instList.instructions.single { it.callExpr?.method?.method == otherCallee }
            f.engine.runAnalysis(listOf(MethodWithContext(caller, EmptyMethodContext),
                MethodWithContext(caller, context), MethodWithContext(another, EmptyMethodContext)),
                timeout = 5.seconds, cancellationTimeout = 5.seconds)
            val visited = f.contexts().filter { it.methodEntryPoint.method == caller }
                .map { it.methodEntryPoint.context }.toSet()
            assertTrue(EmptyMethodContext in visited)
            assertTrue(context in visited, "Both real caller contexts must exist before exporting the relation")
            val copied = f.engine.prescanResolvedGraph()
            assertTrue(ResolvedPrescanCall(caller, at, callee) in copied.calls)
            assertTrue(ResolvedPrescanCall(another, otherAt, otherCallee) in copied.calls)
            assertEquals(setOf(callee), copied.adjacency.getValue(caller))
            assertEquals(1, copied.calls.count { it.caller == caller && it.statement == at && it.callee == callee })
            val savedTargets = copied.adjacency.mapValues { (_, targets) -> targets.toSet() }
            f.manager.releasePrescan()
            f.engine.releasePrescan()
            assertEquals(savedTargets, copied.adjacency)
            assertTrue(ResolvedPrescanCall(another, otherAt, otherCallee) in copied.calls)
        }
    }

    @Test
    fun `empty completed prescan exports empty graph but failed joined prescan cannot export`(): Unit {
        JIRPrescanFixtures(cp, graph).use { f ->
            assertFailsWith<IllegalStateException> { f.engine.prescanResolvedGraph() }
            f.run()
            assertTrue(f.engine.prescanResolvedGraph().methods.isEmpty())
            assertTrue(f.engine.prescanResolvedGraph().calls.isEmpty())
            f.engine.status.set(TaintAnalysisUnitRunnerManager.Status.EXCEPTION)
            assertFailsWith<IllegalStateException> { f.engine.prescanResolvedGraph() }
        }
    }

    @Test
    fun `unresolved interface branch adds no fabricated declared edge`(): Unit {
        JIRPrescanFixtures(cp, graph).use { f ->
            f.run("invokeOther")
            val caller = f.method("invokeOther")
            assertTrue(caller in f.engine.prescanResolvedGraph().methods)
            assertTrue(f.engine.prescanResolvedGraph().calls.none { it.caller == caller })
        }
    }

    @Test
    fun `actual call plan rejects a missing catalog target and releases the setup adjacency`(): Unit {
        val caller = method("ruleCalls", JIRPrescanFixtures.SAMPLE)
        val callee = method("target", JIRPrescanFixtures.SAMPLE)
        val s = caller.instList.instructions.single { it.callExpr != null }
        val entries = org.opentaint.dataflow.jvm.bidi.interp.JIRMethodEntries(graph, languageManager,
            callResolver, org.opentaint.dataflow.jvm.bidi.fixtures.NoTaintRules,
            org.opentaint.dataflow.jvm.ap.ifds.JIRLocalAliasAnalysis.Params(useAliasAnalysis = false),
            manager.cancellation, emptyMap(), run1Adjacency = mapOf(caller to emptySet()))
        val owner = org.opentaint.dataflow.jvm.bidi.interp.JIRInterpreter(manager,
            org.opentaint.dataflow.jvm.bidi.fixtures.NoTaintRules, checker, callResolver, epResolver,
            entries, null, null)
        assertFailsWith<IllegalStateException> {
            owner.forms(key(caller)).call(org.opentaint.dataflow.bidi.ap.Direction.FORWARD, s, checkNotNull(s.callExpr))
        }
        entries.releaseRun1CatalogCoverage()
        val plan = owner.forms(key(caller)).call(org.opentaint.dataflow.bidi.ap.Direction.FORWARD, s, checkNotNull(s.callExpr))
        assertEquals(listOf(callee), plan.stages.filterIsInstance<org.opentaint.dataflow.bidi.interp.CallStage.Callees>()
            .single().callees.map { it.method })
    }
}
