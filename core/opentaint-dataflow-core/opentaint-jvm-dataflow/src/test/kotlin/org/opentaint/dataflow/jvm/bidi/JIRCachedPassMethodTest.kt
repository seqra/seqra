package org.opentaint.dataflow.jvm.bidi

import org.opentaint.dataflow.ap.ifds.access.FactAp
import org.opentaint.dataflow.bidi.ap.Direction
import org.opentaint.dataflow.bidi.interp.*
import org.opentaint.dataflow.configuration.jvm.*
import org.opentaint.dataflow.configuration.mkTrue
import org.opentaint.dataflow.jvm.BasicTestUtils
import org.opentaint.dataflow.jvm.bidi.fixtures.JIRCallTestFixtures
import org.opentaint.dataflow.jvm.bidi.fixtures.NoTaintRules
import org.opentaint.dataflow.jvm.bidi.interp.JIRCallPlanBuilder
import org.opentaint.dataflow.jvm.ap.ifds.taint.TaintRulesProvider
import org.opentaint.ir.api.common.CommonMethod
import org.opentaint.ir.api.common.cfg.CommonInst
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** StringConcat can cache a rule against another signature. Lowering must use the actual call. */
class JIRCachedPassMethodTest : BasicTestUtils() {
    @Test fun `cached raw method does not change actual call transfers or type filters`(): Unit {
        val f = JIRCallTestFixtures(cp)
        val at = f.call("staticCall")
        val actual = f.expr(at).method.method
        val other = f.method("virtualCall")
        val owner = FormOwner()
        fun plan(rawMethod: CommonMethod): CallPlan {
            val pass = TaintPassThrough(rawMethod, mkTrue(),
                (0..20).map { CopyAllMarks(Argument(it), Result) }, null)
            val rules = object : TaintRulesProvider by NoTaintRules {
                override fun passTroughRulesForMethod(method: CommonMethod, statement: CommonInst?,
                    fact: FactAp?, allRelevant: Boolean): Iterable<TaintPassThrough> {
                    assertEquals(actual, method)
                    return listOf(pass)
                }
            }
            return JIRCallPlanBuilder(f.manager, f.forms(rules), f.checker, f.typeFilters,
                f.key(at.location.method), at, f.expr(at), { _, _, _ -> emptyList() }, f.epResolver,
                emptyMap(), formOwner = owner).build()
        }
        val cold = plan(actual)
        val warm = plan(other)
        assertEquals(cold.touched, warm.touched)
        assertEquals(cold.passReads, warm.passReads)
        for ((a, b) in cold.stages.zip(warm.stages)) {
            assertEquals(a::class, b::class)
            assertEquals(a.from, b.from)
            assertEquals(a.to, b.to)
            if (a is CallStage.Edges && b is CallStage.Edges) {
                assertEquals(a.kind, b.kind)
                assertEquals(a.summary.edges.map { Triple(it.edge, it.forward, it.actionId) },
                    b.summary.edges.map { Triple(it.edge, it.forward, it.actionId) })
                assertEquals(a.summary.typeFilters, b.summary.typeFilters)
                assertEquals(a.summary.resultFilters, b.summary.resultFilters)
                assertEquals(a.summary.staticOperations, b.summary.staticOperations)
            }
        }
        assertEquals(cold.stages.size, warm.stages.size)
        assertTrue(cold.stages.filterIsInstance<CallStage.Edges>().any { it.summary.edges.isNotEmpty() })
    }
}
