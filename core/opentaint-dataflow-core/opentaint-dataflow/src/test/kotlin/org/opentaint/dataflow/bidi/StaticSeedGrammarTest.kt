package org.opentaint.dataflow.bidi

import org.opentaint.dataflow.ap.ifds.ClassStaticAccessor
import org.opentaint.dataflow.bidi.ap.*
import org.opentaint.dataflow.bidi.driver.Seed
import org.opentaint.dataflow.bidi.driver.SeedIndex
import org.opentaint.dataflow.bidi.engine.*
import org.opentaint.dataflow.bidi.fixtures.MethodAnalysisFixtures
import org.opentaint.dataflow.bidi.store.DemandStore
import org.opentaint.dataflow.configuration.CommonTaintConfigurationSink
import org.opentaint.dataflow.configuration.CommonTaintConfigurationSinkMeta
import kotlin.test.*

class StaticSeedGrammarTest {
    private val rule = object : CommonTaintConfigurationSink {
        override val id = "static seed grammar"
        override val meta = object : CommonTaintConfigurationSinkMeta {
            override val message = id
            override val severity = CommonTaintConfigurationSinkMeta.Severity.Warning
        }
    }

    private fun fire(f: MethodAnalysisFixtures, requirement: Pattern, emit: (Facts) -> Unit) {
        val demand = DemandStore.Builder(f.manager).apply { completeRun1StaticFields() }.build()
        val seed = Seed.Sink(rule, f.key, f.inst(2), requirement)
        val core = f.core(f.config(index = 2, demand = demand, seeds = SeedIndex.of(listOf(seed))),
            localFactory = MethodLocalServicesFactory(::RunMethodLocalServices))
        core.localServices.fireSinkSeeds(f.inst(2), emit)
    }

    @Test fun `broad concrete static sink seeds fail before executable work is emitted`() {
        for (root in listOf(true, false)) MethodAnalysisFixtures().use { f ->
            val cls = f.manager.accessors.index(ClassStaticAccessor("C"))
            val path = if (root) emptyList() else listOf(cls)
            val requirement = f.pattern(STATIC, path, Tail.ANY)
            val emitted = ArrayList<Facts>()
            val failure = assertFailsWith<IllegalStateException> { fire(f, requirement, emitted::add) }
            assertContains(checkNotNull(failure.message), "Unsupported run-2 concrete static input")
            assertTrue(emitted.isEmpty(), "A configured unsupported input is diagnosed, not executed or dropped")
            assertTrue(f.processed.isEmpty())
        }
    }

    @Test fun `exact markers and exact or ANY named field seeds keep their actual full mark`() {
        for (shape in 0..2) MethodAnalysisFixtures().use { f ->
            val cls = f.manager.accessors.index(ClassStaticAccessor("C"))
            val path = if (shape == 0) listOf(cls) else listOf(cls, f.field("f"))
            val tail = if (shape == 2) Tail.ANY else Tail.EXACT
            val requirement = f.pattern(STATIC, path, tail)
            val emitted = ArrayList<Facts>()
            fire(f, requirement, emitted::add)
            assertEquals(listOf(requirement), emitted.flatMap { f.ops.leaves(it).toList() })
        }
    }

    @Test fun `a deep static seed is bounded before the selected grammar check`() {
        MethodAnalysisFixtures().use { f ->
            val cls = f.manager.accessors.index(ClassStaticAccessor("C"))
            val field = f.field("f")
            val requirement = f.pattern(STATIC, listOf(cls, field, f.field("nested")))
            val emitted = ArrayList<Facts>()
            fire(f, requirement, emitted::add)
            val bounded = emitted.flatMap { f.ops.leaves(it).toList() }
            assertEquals(listOf(f.pattern(STATIC, listOf(cls, field), Tail.ANY, requirement.fact.mark)), bounded)
            assertTrue(emitted.all { it.layer == Layer.DEMAND })
        }
    }
}
