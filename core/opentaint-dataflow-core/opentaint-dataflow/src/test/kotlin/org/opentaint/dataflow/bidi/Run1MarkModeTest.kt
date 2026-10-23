package org.opentaint.dataflow.bidi

import org.opentaint.dataflow.ap.ifds.AccessPathBase
import org.opentaint.dataflow.ap.ifds.ClassStaticAccessor
import org.opentaint.dataflow.bidi.ap.*
import org.opentaint.dataflow.bidi.driver.SeedIndex
import org.opentaint.dataflow.bidi.engine.*
import org.opentaint.dataflow.bidi.fixtures.*
import org.opentaint.dataflow.bidi.interp.*
import org.opentaint.dataflow.bidi.store.DemandStore
import kotlin.test.*
import kotlin.time.Duration.Companion.seconds

/** Production controls for the mode-model adapters, including owner-equal FULL atoms. */
class Run1MarkModeTest {
    private val declarations = testDeclarations(
        TestMark("T", MarkGroupKey.Rule("one rule")),
        TestMark("U", MarkGroupKey.Rule("one rule")),
    )

    @Test fun `flag accepts exactly GROUP and FULL and each run keeps the selected immutable value`() {
        assertEquals(MarkMode.FULL, readRun1MarkMode(null))
        assertEquals(MarkMode.GROUP, readRun1MarkMode("GROUP"))
        assertEquals(MarkMode.FULL, readRun1MarkMode("FULL"))
        assertEquals(readRun1MarkMode("FULL"), readRun1MarkMode(null))
        assertEquals(MarkMode.FULL, ApMode(true, Direction.FORWARD, 1).markMode)
        assertEquals(MarkMode.FULL, org.opentaint.dataflow.bidi.driver.AnalysisEnd(
            RunStatus.COMPLETE, 1, Direction.FORWARD,
            org.opentaint.dataflow.bidi.driver.EndReason.POLICY).run1MarkMode)
        for (invalid in listOf("", "full", "GROUP ", "false")) {
            assertTrue(assertFailsWith<IllegalStateException> { readRun1MarkMode(invalid) }
                .message.orEmpty().contains("use GROUP or FULL"))
        }
        FiniteProgramFixtures().use { f ->
            for (selected in MarkMode.entries) for (index in 1..5) {
                val config = RunConfig(index, maxOf(1, index - 1),
                    if (index == 1) null else DemandStore.Builder(f.ap).build(),
                    f.records, SeedIndex.EMPTY, emptyList(), run1MarkMode = selected)
                assertEquals(if (index == 1) selected else MarkMode.FULL, config.mode.markMode)
                assertEquals(selected, config.run1MarkMode)
                assertEquals(index == 1, config.mode.run1)
            }
        }
    }

    @Test fun `default static service preserves same owner FULL identities`() {
        FiniteProgramFixtures(markDeclarations = declarations).use { f ->
            val t = f.mark("T"); val u = f.mark("U")
            assertEquals(f.ap.marks.groupOf(t), f.ap.marks.groupOf(u))
            val path = listOf(f.ap.accessors.index(ClassStaticAccessor("C")), f.field("f"))
            val fact = f.ap.factsOf(PathFact(STATIC, path, Tail.ANY,
                MarkSlot.Concrete(t)), ExclusionSet.Empty, Layer.DEMAND)
            val other = f.ap.factsOf(PathFact(STATIC, path, Tail.ANY,
                MarkSlot.Concrete(u)), ExclusionSet.Empty, Layer.DEMAND)
            fun atoms(service: Run1StaticService) = listOf(fact, other)
                .flatMap { service.slices(it) }.fold(MarkSet.EMPTY) { acc, slice -> acc + slice.groups }
            assertEquals(MarkSet.of(t) + u, atoms(Run1StaticService(f.ap)))
            assertEquals(1, atoms(Run1StaticService(f.ap, MarkMode.GROUP)).size)
        }
    }

    @Test fun `FULL field requests keep T and U distinct in either arrival order and release each pending membership`() {
        for (supplyFirst in listOf(false, true)) FiniteProgramFixtures(markDeclarations = declarations).use { f ->
            val owner = f.method("reader", listOf(emptyList()), 0).key()
            val t = f.mark("T"); val u = f.mark("U")
            assertEquals(f.ap.marks.groupOf(t), f.ap.marks.groupOf(u))
            val position = Pattern(PathFact(STATIC,
                listOf(f.ap.accessors.index(ClassStaticAccessor("C")), f.field("f")),
                Tail.STAR, MarkSlot.STAR), ExclusionSet.Empty)
            val service = Run1StaticService(f.ap, MarkMode.FULL)
            val requests = StaticBatch().apply {
                interests += StaticInterest(owner, 1, position, MarkSet.of(t) + u)
                flowWriters += StaticFlowWriter(owner, 2, position)
            }
            val write = StaticBatch().apply { supplies += RegionGroups(position, MarkSet.of(t)) }
            assertTrue(service.commit(if (supplyFirst) write else requests)
                .filterIsInstance<StaticOwnerEffect.PointReady>().isEmpty())
            val replies = service.commit(if (supplyFirst) requests else write)
                .filterIsInstance<StaticOwnerEffect.PointReady>()
            assertEquals(listOf(MarkSet.of(t)), replies.map { it.supply.groups })
            assertEquals(1L, service.requestStatistics().pendingMemberships)
            assertEquals(1L, service.requestStatistics().processed)
            assertEquals(2L, service.requestStatistics().writerAdmissions)
            val actual = service.facts(replies.single().supply.region, replies.single().supply.groups)
            assertEquals(setOf(t), f.ops.leaves(actual).map { (it.fact.mark as MarkSlot.Concrete).mark }.toSet())
            assertEquals(MarkSet.of(t), service.slices(actual).single().groups)
            val second = service.commit(StaticBatch().apply { supplies += RegionGroups(position, MarkSet.of(u)) })
                .filterIsInstance<StaticOwnerEffect.PointReady>().single()
            assertEquals(MarkSet.of(u), second.supply.groups)
            assertEquals(0L, service.requestStatistics().pendingMemberships)
            assertEquals(0, service.statistics().pointSubscriptions)
            assertEquals(2L, service.requestStatistics().processed)
            assertTrue(service.commit(write).isEmpty())
        }
    }

    @Test fun `FULL exact class supply cannot answer another mark of the same owner or a descendant field`() {
        FiniteProgramFixtures(markDeclarations = declarations).use { f ->
            val owner = f.method("marker", listOf(emptyList()), 0).key()
            val t = f.mark("T"); val u = f.mark("U")
            val marker = Pattern(PathFact(STATIC, listOf(f.ap.accessors.index(ClassStaticAccessor("C"))),
                Tail.EXACT, MarkSlot.STAR), ExclusionSet.Empty)
            val field = marker.copy(fact = marker.fact.copy(path = marker.fact.path + f.field("f"), tail = Tail.STAR))
            val service = Run1StaticService(f.ap, MarkMode.FULL)
            val replies = service.commit(StaticBatch().apply {
                interests += StaticInterest(owner, 1, marker, MarkSet.of(u))
                interests += StaticInterest(owner, 2, marker, MarkSet.of(t))
                interests += StaticInterest(owner, 3, field, MarkSet.of(t))
                supplies += RegionGroups(marker, MarkSet.of(t))
            }).filterIsInstance<StaticOwnerEffect.PointReady>()
            assertEquals(listOf(2L), replies.map { it.id })
            assertEquals(marker, replies.single().supply.region)
            val actual = service.facts(marker, MarkSet.of(t))
            assertEquals(setOf(t), f.ops.leaves(actual).map { (it.fact.mark as MarkSlot.Concrete).mark }.toSet())
            assertEquals(2L, service.requestStatistics().pendingMemberships)
            assertFailsWith<IllegalArgumentException> {
                service.facts(marker, MarkSet.of(f.ap.marks.group(f.ap.marks.groupOf(t))))
            }
        }
    }

    @Test fun `FULL run1 preserves exact condition cleaner and GEN identities`() {
        FiniteProgramFixtures(markDeclarations = declarations).use { f ->
            val x = AccessPathBase.Argument(0)
            val t = f.mark("T"); val u = f.mark("U")
            fun pattern(mark: TaintMark) = Pattern(PathFact(x, emptyList(), Tail.EXACT, MarkSlot.Concrete(mark)),
                ExclusionSet.Empty)
            for (selected in MarkMode.entries) {
                val mode = ApMode(true, Direction.FORWARD, 1, selected)
                val input = f.ops.runtimeFact(f.ap.factsOf(pattern(t).fact, ExclusionSet.Empty, Layer.DEMAND), mode)
                val initial = f.ap.initial(f.ops.leaves(input).single())
                val match = f.ops.checkMark(input, pattern(u), mode)
                if (selected == MarkMode.GROUP) assertIs<MarkCheck.Holds>(match)
                else assertSame(MarkCheck.None, match)
                val cleanT = CollectingOut(); val cleanU = CollectingOut()
                f.ops.clean(input, initial, Cleaner(x, null, CleanReach.EXACT, t), mode, cleanT)
                f.ops.clean(input, initial, Cleaner(x, null, CleanReach.EXACT, u), mode, cleanU)
                assertEquals(listOf(input), cleanU.results)
                if (selected == MarkMode.FULL) assertTrue(cleanT.results.isEmpty())
                else assertEquals(listOf(input), cleanT.results)
                val generated = CollectingOut()
                f.ops.applyEdge(Reach.NORMAL, f.ap.zero,
                    PathEdge(ZERO_FACT, pattern(t).fact,
                        ExclusionSet.Empty), false, mode, generated)
                assertTrue(generated.results.isNotEmpty())
                // FULL primitives retain their ordinary input layer. The engine
                // applies the shared run-1 DEMAND policy in forceRun1Layer.
                assertTrue(generated.results.all { it.layer == if (selected == MarkMode.GROUP) Layer.DEMAND else Layer.NORMAL })
                val atom = f.ap.marks.runtimeAtom(t, selected)
                assertEquals(setOf(atom), generated.results.flatMap { f.ops.leaves(it).toList() }
                    .map { (it.fact.mark as MarkSlot.Concrete).mark }.toSet())
            }
        }
    }

    @Test fun `FULL engine keeps all contributing origins and distinct same-owner action results`() {
        for (secondInput in listOf(false, true)) FiniteProgramFixtures(markDeclarations = declarations).use { f ->
            val main = f.method("caller", listOf(listOf(1), listOf(2), emptyList()), 2).key()
            val callee = f.method("condition", listOf(listOf(1), emptyList()), 1).key()
            val x = AccessPathBase.LocalVar(0); val y = AccessPathBase.LocalVar(1)
            val a = AccessPathBase.Argument(0); val b = AccessPathBase.Argument(1)
            val ret = AccessPathBase.Return
            val t = f.mark("T"); val u = f.mark("U")
            fun pattern(base: AccessPathBase, mark: TaintMark) = f.pattern(base, mark = MarkSlot.Concrete(mark))
            fun summary(vararg edges: PathEdge) = StatementSummary(edges.map { it.from.base }.toSet(),
                edges.map(MicroEdge::of), emptyList(), emptyMap())
            val sources = arrayListOf(ZERO_KEEP,
                PathEdge(ZERO_FACT, pattern(x, t).fact, ExclusionSet.Empty))
            if (secondInput) sources += PathEdge(ZERO_FACT, pattern(y, u).fact, ExclusionSet.Empty)
            f.entry(main, RuleStatement(summary(*sources.toTypedArray()), StatementSummary.EMPTY, emptyList()))
            f.call(main, 1, CallPlan(setOf(x, y, a, b, ret), listOf(
                CallStage.Edges(CallPoint.BEFORE, CallPoint.BOUND, StageKind.BIND_IN,
                    summary(ZERO_KEEP, starEdge(x, emptyList(), a, emptyList()),
                        starEdge(y, emptyList(), b, emptyList()))),
                CallStage.Actions(CallPoint.BOUND, CallPoint.ADDED, emptyList()),
                CallStage.Callees(CallPoint.ADDED, CallPoint.RETURNED, listOf(callee)),
                CallStage.Edges(CallPoint.RETURNED, CallPoint.AFTER, StageKind.BIND_BACK,
                    summary(ZERO_KEEP, keepEdge(ret))),
            ), emptyList(), CallPoint.BEFORE, CallPoint.AFTER))
            val builder = RulePredicate.Builder(f.cancellation, f.ap.marks)
            val predicate = builder.build(builder.and(listOf(builder.leaf(pattern(a, t)), builder.leaf(pattern(b, u)))))
            val occurrence = RuleOccurrenceId(FormKey(FormOwner(), callee.method, callee, f.inst(callee, 0)),
                RuleKind.ENTRY_SOURCE, 0)
            val actions = listOf(t, u).mapIndexed { index, mark ->
                SourceAction(ActionId(occurrence, ActionRole.SOURCE, index),
                    f.pattern(ret, tail = Tail.ANY_TAINT, mark = MarkSlot.Concrete(mark)).fact)
            }
            f.entry(callee, RuleStatement(StatementSummary(setOf(a, b), listOf(MicroEdge.of(ZERO_KEEP)),
                emptyList(), emptyMap(), sources = listOf(SourceRuleOccurrence(occurrence, predicate, actions))),
                StatementSummary.EMPTY, emptyList()))
            val config = RunConfig(1, 1, null, f.records, SeedIndex.EMPTY, listOf(main), run1MarkMode = MarkMode.FULL)
            val result = RunManager(config, f.shared, RunMethodHandlers(
                MethodLocalServicesFactory(::RunMethodLocalServices), MethodCallServicesFactory(::RunMethodCallServices)))
                .run(5.seconds)
            assertEquals(RunStatus.COMPLETE, result.status)
            assertEquals(MarkMode.FULL, result.markMode)
            val rows = result.analyzers.single { it.key == callee }.summaries.all()
                .filter { (_, value) -> value.base == ret && value is TaintTree }.toList()
            if (!secondInput) assertTrue(rows.isEmpty(), "Both exact full conditions must hold")
            else {
                assertEquals(setOf(a, b), rows.map { it.first.members.single().base }.toSet())
                assertTrue(rows.all { (premise, value) -> premise is InitialAp && !premise.isZero && value.layer == Layer.DEMAND })
                assertEquals(setOf(t, u), rows.flatMap { f.ops.leaves(it.second).toList() }
                    .map { (it.fact.mark as MarkSlot.Concrete).mark }.toSet())
            }
        }
    }

    @Test fun `FULL engine demotes static FLOW results without demoting ordinary nonstatic FLOW`() {
        MethodAnalysisFixtures().use { f ->
            val config = RunConfig(1, 1, null, f.records, SeedIndex.EMPTY, listOf(f.key), run1MarkMode = MarkMode.FULL)
            val actor = f.core(config)
            val argument = AccessPathBase.Argument(0)
            val initial = f.manager.initial(f.pattern(argument, tail = Tail.STAR, mark = MarkSlot.STAR))
            val normal = f.fact(argument, tail = Tail.STAR, mark = MarkSlot.STAR, layer = Layer.NORMAL)
            val path = listOf(f.manager.accessors.index(ClassStaticAccessor("C")), f.field("f"))
            val statics = f.fact(STATIC, path, Tail.STAR, MarkSlot.STAR, layer = Layer.NORMAL)
            assertEquals(Layer.NORMAL, actor.forceRun1Layer(initial, normal).layer)
            assertEquals(Layer.DEMAND, actor.forceRun1Layer(initial, statics).layer)
        }
    }

    @Test fun `static snapshot counts selected atoms and owners without decoding GROUP members`() {
        for (selected in MarkMode.entries) FiniteProgramFixtures(markDeclarations = declarations).use { f ->
            val owner = f.method("snapshot", listOf(emptyList()), 0).key()
            val t = f.ap.marks.runtimeAtom(f.mark("T"), selected)
            val u = f.ap.marks.runtimeAtom(f.mark("U"), selected)
            val marker = Pattern(PathFact(STATIC, listOf(f.ap.accessors.index(ClassStaticAccessor("C"))),
                Tail.EXACT, MarkSlot.STAR), ExclusionSet.Empty)
            val field = marker.copy(fact = marker.fact.copy(path = marker.fact.path + f.field("f"), tail = Tail.STAR))
            val service = Run1StaticService(f.ap, selected)
            service.commit(StaticBatch().apply {
                interests += StaticInterest(owner, 1, field, MarkSet.of(t) + u)
                interests += StaticInterest(owner, 2, marker, MarkSet.of(u))
                supplies += RegionGroups(field, MarkSet.of(t))
                supplies += RegionGroups(marker, MarkSet.of(u))
            })
            val snapshot = service.statistics()
            val expected = if (selected == MarkMode.GROUP) 1 else 2
            assertEquals(expected, snapshot.distinctRequestedAtoms)
            assertEquals(expected, snapshot.distinctSuppliedAtoms)
            assertEquals(1, snapshot.suppliedOwnerGroups)
            assertEquals(if (selected == MarkMode.GROUP) 0 else 2, snapshot.suppliedFullAtoms)
            assertEquals(snapshot, service.statistics(), "A snapshot does not mutate service state")
        }
    }
}
