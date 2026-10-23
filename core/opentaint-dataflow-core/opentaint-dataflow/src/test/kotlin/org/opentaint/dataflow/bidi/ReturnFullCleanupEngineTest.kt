package org.opentaint.dataflow.bidi

import org.opentaint.dataflow.ap.ifds.AccessPathBase
import org.opentaint.dataflow.ap.ifds.ClassStaticAccessor
import org.opentaint.dataflow.bidi.ap.*
import org.opentaint.dataflow.bidi.engine.*
import org.opentaint.dataflow.bidi.fixtures.MethodAnalysisFixtures
import org.opentaint.dataflow.bidi.interp.*
import kotlin.test.*

class ReturnFullCleanupEngineTest {
    private val x = AccessPathBase.Argument(0)
    private fun plan(f: MethodAnalysisFixtures, raw: List<CleanStep>, bulk: Boolean): CallPlan = CallPlan(
        setOf(x, STATIC), listOf(
            if (bulk) CallStage.Actions.run1FullReturnRewriter(raw, f.manager.marks)
            else CallStage.Actions(CallPoint.RETURNED, CallPoint.REWRITTEN, raw),
            CallStage.Edges(CallPoint.REWRITTEN, CallPoint.AFTER, StageKind.BIND_BACK,
                StatementSummary.identities(listOf(x, STATIC)))),
        emptyList(), CallPoint.RETURNED, CallPoint.AFTER)

    private fun fullConfig(f: MethodAnalysisFixtures) = RunConfig(1, 1, null, f.records,
        org.opentaint.dataflow.bidi.driver.SeedIndex.EMPTY, listOf(f.key), run1MarkMode = MarkMode.FULL)

    @Test fun `ordinary full mask and every raw middle entry preserve exact requests`() {
        for (start in listOf<Int?>(null, 0, 1, 2, 3)) MethodAnalysisFixtures().use { f ->
            val t = f.mark("T")
            val u = f.mark("U")
            val v = f.mark("V")
            val atoms = listOf(t, u, t, v)
            val raw = atoms.map { CleanStep.Clean(Cleaner(x, null, CleanReach.EXACT, it)) }
            f.call(1, plan(f, raw, bulk = true))
            val actor = f.core(fullConfig(f))
            val services = RunMethodCallServices(actor)
            val initial = f.manager.initial(f.pattern(x, tail = Tail.STAR, mark = MarkSlot.STAR))
            val input = f.fact(x, tail = Tail.STAR, mark = MarkSlot.STAR)
            if (start == null) services.process(EdgeDelta(initial, f.inst(1), input))
            else {
                val operation = OperationId(FormKey(FormOwner(), f.key.method, f.key, f.inst(1)))
                services.resumePoint(initial, input, ResumeSite.CleanerInput(operation, start, CleanerPhase.CLEAN),
                    CallResumeState(Layer.NORMAL, true))
            }
            val expected = atoms.drop(start ?: 0).toSet()
            assertEquals(expected, f.requests.map { (it.second as ReferenceRequestKind.Mark).mark }.toSet())
            val outputs = actor.edges.edgesAt(f.inst(2), initial).map { it.second }.toList()
            assertEquals(expected, (outputs.single() as FlowTree).markExclusion.ids.map(::TaintMark).toSet())
            services.freeze()
        }
    }

    @Test fun `late concrete static replies preserve raw suffix results and drop answered images`() {
        fun run(bulk: Boolean, start: Int, identity: Boolean?): Pair<Set<String>, Int> = MethodAnalysisFixtures().use { f ->
            val t = f.mark("T")
            val u = f.mark("U")
            val atoms = listOf(t, u, t, u, t)
            val raw = atoms.map { CleanStep.Clean(Cleaner(x, null, CleanReach.EXACT, it)) }
            f.call(1, plan(f, raw, bulk))
            lateinit var services: RunMethodCallServices
            val actor = f.core(fullConfig(f), callFactory = MethodCallServicesFactory {
                RunMethodCallServices(it).also { service -> services = service }
            })
            val path = listOf(f.manager.accessors.index(ClassStaticAccessor("C")), f.field("staticField"))
            val original = f.manager.initial(f.pattern(STATIC, path, Tail.STAR, MarkSlot.STAR))
            val image = f.fact(x, listOf(f.field("image")), Tail.STAR, MarkSlot.STAR, layer = Layer.DEMAND)
            val operation = OperationId(FormKey(FormOwner(), f.key.method, f.key, f.inst(1)))
            services.resumePoint(original, image, ResumeSite.CleanerInput(operation, start, CleanerPhase.CLEAN),
                CallResumeState(Layer.NORMAL, identity))
            f.drain(actor)
            val service = checkNotNull(actor.runnerPort.staticService)
            val pending = service.statistics().pointSubscriptions
            val expectedAtoms = atoms.drop(start).toSet()
            assertEquals(if (bulk) 1 else atoms.size - start, pending)
            assertEquals(if (bulk) expectedAtoms.size.toLong() else (atoms.size - start).toLong(),
                service.requestStatistics().pendingMemberships)
            val reply = service.commit(StaticBatch().apply {
                supplies += RegionGroups(original.toPattern(), MarkSet(expectedAtoms.map { it.id }.toIntArray()))
            })
            reply.filterIsInstance<StaticOwnerEffect.PointReady>().forEach(actor::onStaticEffect)
            f.drain(actor)
            assertEquals(0, service.statistics().pointSubscriptions)
            for (atom in expectedAtoms) {
                val initial = f.manager.initial(f.pattern(STATIC, path, Tail.STAR, MarkSlot.Concrete(atom)))
                assertTrue(actor.edges.edgesAt(f.inst(2), initial).any { (_, value) ->
                    value.layer == Layer.DEMAND && f.ops.leaves(value).any { it.fact.mark == MarkSlot.Concrete(atom) }
                })
            }
            fun encode(p: Pattern): String = listOf(p.fact.base,
                p.fact.path.map { f.manager.accessors.accessor(it) }, p.fact.tail, p.exclusion,
                when (val mark = p.fact.mark) {
                    is MarkSlot.Concrete -> f.manager.marks.name(mark.mark)
                    is MarkSlot.Star -> mark.excluded.ids.map { f.manager.marks.name(TaintMark(it)) }.sorted()
                }).toString()
            val summaries = actor.summaries.all().flatMap { (premise, facts) ->
                val initial = premise.members.map { encode(it.toPattern()) }.sorted()
                f.ops.leaves(facts).map { "$initial -> ${encode(it)} / ${facts.layer}" }
            }.toSet()
            summaries to pending
        }
        for (start in listOf(0, 1, 3, 4)) for (identity in listOf(null, false, true)) {
            val reference = run(false, start, identity)
            val selected = run(true, start, identity)
            assertEquals(reference.first, selected.first, "start=$start identity=$identity")
            assertTrue(selected.second <= reference.second)
        }
    }

    @Test fun `static base mismatch promotes and consumes once and nonfixed inputs use scalar fallback`() {
        fun run(bulk: Boolean, kind: Int): Pair<Set<String>, Set<String>> = MethodAnalysisFixtures().use { f ->
            val raw = listOf(f.mark("T"), f.mark("U")).map { CleanStep.Clean(Cleaner(x, null, CleanReach.EXACT, it)) }
            f.call(1, plan(f, raw, bulk))
            val actor = f.core(fullConfig(f))
            val services = RunMethodCallServices(actor)
            // K6 requires a concrete original for a concrete TAINT input.
            val originalMark = when (kind) {
                2 -> MarkSlot.Concrete(f.mark("V"))
                3 -> MarkSlot.Concrete(f.mark("T"))
                else -> MarkSlot.STAR
            }
            val original = f.manager.initial(f.pattern(x, tail = Tail.STAR, mark = originalMark))
            val input = when (kind) {
                0 -> f.fact(STATIC, listOf(f.manager.accessors.index(ClassStaticAccessor("C")), f.field("staticField")),
                    Tail.STAR, MarkSlot.STAR, layer = Layer.DEMAND)
                1 -> f.fact(x, tail = Tail.STAR, mark = MarkSlot.STAR, exclusion = ExclusionSet.of(f.field("f")))
                2 -> f.fact(x, listOf(f.field("f")), mark = MarkSlot.Concrete(f.mark("V")))
                else -> f.fact(x, tail = Tail.ANY_TAINT, mark = MarkSlot.Concrete(f.mark("T")),
                    exclusion = ExclusionSet.of(f.field("f")), layer = Layer.DEMAND)
            }
            services.process(EdgeDelta(original, f.inst(1), input))
            f.drain(actor)
            fun encode(value: Facts) = f.ops.leaves(value).map { "${it.fact.base}/${it.fact.path}/${it.fact.tail}/${it.fact.mark}/${it.exclusion}/${value.layer}" }.toList()
            val edges = actor.edges.edgesAt(f.inst(2), original).flatMap { encode(it.second).asSequence() }.toSet()
            val summaries = actor.summaries.all().flatMap { (premise, value) ->
                encode(value).asSequence().map { "${premise.members.map { it.toPattern() }} -> $it" }
            }.toSet()
            if (kind == 0) assertTrue(edges.isEmpty(), "The first cleaner must consume a static mismatch")
            services.freeze()
            edges to summaries
        }
        for (kind in 0..3) assertEquals(run(false, kind), run(true, kind), "input kind=$kind")
    }

    @Test fun `collector fixed gate rejects E and normal taint without changing fields`() = MethodAnalysisFixtures().use { f ->
        val original = f.manager.initial(f.pattern(x, tail = Tail.STAR, mark = MarkSlot.STAR))
        val field = f.field("f")
        assertTrue(fullCleanupCollectorFixed(original, f.fact(x, tail = Tail.STAR, mark = MarkSlot.STAR)))
        assertFalse(fullCleanupCollectorFixed(original, f.fact(x, tail = Tail.STAR, mark = MarkSlot.STAR,
            exclusion = ExclusionSet.of(field))))
        assertFalse(fullCleanupCollectorFixed(original, f.fact(x)))
        assertTrue(fullCleanupCollectorFixed(original, f.fact(x, layer = Layer.DEMAND)))
        val staticOriginal = f.manager.initial(f.pattern(STATIC,
            listOf(f.manager.accessors.index(ClassStaticAccessor("C")), field), Tail.STAR, MarkSlot.STAR))
        assertFalse(fullCleanupCollectorFixed(staticOriginal, f.fact(x, tail = Tail.STAR, mark = MarkSlot.STAR)))
        assertTrue(fullCleanupCollectorFixed(staticOriginal,
            f.fact(x, tail = Tail.STAR, mark = MarkSlot.STAR, layer = Layer.DEMAND)))
    }
}
