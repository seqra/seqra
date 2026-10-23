package org.opentaint.dataflow.bidi

import org.opentaint.dataflow.ap.ifds.AccessPathBase
import org.opentaint.dataflow.ap.ifds.ClassStaticAccessor
import org.opentaint.dataflow.bidi.ap.*
import org.opentaint.dataflow.bidi.driver.SeedIndex
import org.opentaint.dataflow.bidi.engine.*
import org.opentaint.dataflow.bidi.fixtures.MethodAnalysisFixtures
import org.opentaint.dataflow.bidi.interp.*
import org.opentaint.dataflow.bidi.store.CallerRef
import org.opentaint.dataflow.bidi.store.DemandStore
import kotlin.test.*

/** A deliberately unsound diagnostic removes FIELD flows, never exact global markers. */
class Run1StaticFieldAblationTest {
    private fun config(f: MethodAnalysisFixtures, disabled: Boolean, index: Int = 1) = RunConfig(
        index, maxOf(1, index - 1), if (index == 1) null else DemandStore.Builder(f.manager).build(),
        f.records, SeedIndex.EMPTY, listOf(f.key), disableRun1Statics = disabled, run1MarkMode = MarkMode.GROUP)

    private fun operation(f: MethodAnalysisFixtures, index: Int = 0) =
        OperationId(FormKey(FormOwner(), f.key.method, f.key, f.inst(index)))

    private fun field(f: MethodAnalysisFixtures) =
        listOf(f.manager.accessors.index(ClassStaticAccessor("C")), f.field("f"))

    private fun actor(f: MethodAnalysisFixtures, disabled: Boolean, index: Int = 1) = f.core(
        config(f, disabled, index), localFactory = MethodLocalServicesFactory(::RunMethodLocalServices),
        callFactory = MethodCallServicesFactory(::RunMethodCallServices))

    private fun writes(f: MethodAnalysisFixtures, edge: PathEdge, at: Int = 0) = MicroEdgeBuilder().apply {
        keepZero(); edge(edge)
        staticOperation(StaticOperation.Write(operation(f, at), Pattern(edge.to, ExclusionSet.Empty), edge))
    }.buildStage()

    @Test fun `the immutable injected switch is effective only in run one`() {
        MethodAnalysisFixtures().use { f ->
            assertTrue(config(f, true).disableRun1Statics)
            assertFalse(config(f, false).disableRun1Statics)
            for (index in 2..5) assertFalse(config(f, true, index).disableRun1Statics)
        }
    }

    @Test fun `field writes and read injections disappear while the off control retains the flow`() {
        for (disabled in listOf(false, true)) MethodAnalysisFixtures().use { f ->
            val path = field(f)
            val source = PathEdge(ZERO_FACT, f.run1Pattern(STATIC, path).fact, ExclusionSet.Empty)
            f.statement(0, writes(f, source))
            val read = starEdge(STATIC, path, AccessPathBase.Return, emptyList())
            f.statement(1, MicroEdgeBuilder().apply {
                keepZero(); edge(read)
                staticOperation(StaticOperation.Read(operation(f, 1), read))
            }.buildStage())
            val core = actor(f, disabled)
            core.addRootZero(); f.drain(core, quantum = 1)
            val summaries = core.summaries.all().toList()
            if (disabled) {
                assertTrue(summaries.all { (_, facts) -> facts is Reach })
                val stats = checkNotNull(core.runnerPort.staticService).statistics()
                assertEquals(0, stats.writeRegions)
                assertEquals(0, stats.suppliedRegions)
                assertEquals(0, stats.readDescriptors)
                assertEquals(0, stats.pointSubscriptions)
                assertEquals(0, stats.writerRows)
                assertEquals(0, stats.flowWriterRows)
                assertTrue(f.processed.none { it.premise.members.any { member -> member.base == STATIC } })
            } else {
                assertTrue(summaries.any { (_, facts) -> facts.base == STATIC })
                assertTrue(summaries.any { (premise, facts) -> premise.members.any { it.base == STATIC } &&
                    facts.base == AccessPathBase.Return })
            }
        }
    }

    @Test fun `a configured source on a static field read cannot inject its local source fact`() {
        for (disabled in listOf(false, true)) MethodAnalysisFixtures().use { f ->
            val id = RuleOccurrenceId(operation(f).form, RuleKind.FIELD_SOURCE, 0)
            val source = PathEdge(ZERO_FACT, f.run1Pattern(AccessPathBase.Return).fact, ExclusionSet.Empty)
            val read = starEdge(STATIC, field(f), AccessPathBase.Return, emptyList())
            f.statement(0, MicroEdgeBuilder().apply {
                keepZero(); edge(read); keep(AccessPathBase.Return)
                sourceEdge(source, ActionId(id, ActionRole.SOURCE, 0))
                staticOperation(StaticOperation.Read(operation(f), read))
            }.buildStage())
            f.statement(1, StatementSummary.identities(listOf(AccessPathBase.Zero, AccessPathBase.Return)))
            val core = actor(f, disabled)
            core.addRootZero(); f.drain(core)
            assertEquals(!disabled, core.summaries.all().any { (_, facts) -> facts.base == AccessPathBase.Return })
            assertEquals(if (disabled) 0 else 1,
                checkNotNull(core.runnerPort.staticService).statistics().readDescriptors)
        }
    }

    @Test fun `packed summaries keep only exact global markers before any static indexing`() {
        MethodAnalysisFixtures().use { f ->
            val path = field(f)
            val marker = f.run1Fact(STATIC, path.take(1))
            val fieldValue = f.run1Fact(STATIC, path)
            val packed = f.manager.mergeAddDelta(marker, fieldValue).first
            val core = actor(f, true)
            core.summaryDelta(f.manager.zero, packed)
            f.drain(core)
            val retained = core.summaries.all().flatMap { f.ops.leaves(it.second) }.toList()
            assertEquals(1, retained.size)
            assertEquals(path.take(1), retained.single().fact.path)
            assertEquals(Tail.EXACT, retained.single().fact.tail)
            val published = f.published.flatten().flatMap { f.ops.leaves(it.conclusion) }
            assertEquals(retained, published)
            val stats = checkNotNull(core.runnerPort.staticService).statistics()
            assertEquals(1, stats.suppliedRegions)
            assertEquals(0, stats.writerRows)
            assertEquals(0, stats.flowWriterRows)
            assertEquals(0L, checkNotNull(core.runnerPort.staticService).requestStatistics().requested)
        }
    }

    @Test fun `source actions pass and static alias outputs are dropped before publication`() {
        MethodAnalysisFixtures().use { f ->
            val target = f.run1Pattern(STATIC, field(f)).fact
            val id = ActionId(RuleOccurrenceId(operation(f).form, RuleKind.CALL_SOURCE, 0), ActionRole.SOURCE, 0)
            f.statement(0, MicroEdgeBuilder().apply {
                keepZero(); sourceEdge(PathEdge(ZERO_FACT, target, ExclusionSet.Empty), id)
                staticOperation(StaticOperation.Write(operation(f).copy(action = id), Pattern(target, ExclusionSet.Empty), action = id))
            }.buildStage())
            val core = actor(f, true)
            core.addRootZero(); f.drain(core)
            assertTrue(core.summaries.all().none { (_, facts) -> facts.base == STATIC })
            assertEquals(0, checkNotNull(core.runnerPort.staticService).statistics().writeRegions)
        }
        MethodAnalysisFixtures().use { f ->
            val inputBase = AccessPathBase.Argument(0)
            val edge = starEdge(inputBase, emptyList(), STATIC, field(f))
            val pass = MicroEdgeBuilder().apply {
                keep(inputBase); edge(edge)
                staticOperation(StaticOperation.Write(operation(f), Pattern(edge.to, ExclusionSet.Empty), edge))
            }.buildStage()
            val core = actor(f, true)
            val out = arrayListOf<Facts>()
            core.applier.stage(pass, f.manager.initial(f.run1Pattern(inputBase)), f.run1Fact(inputBase),
                Place(f.inst(0), statementEdge = false, sources = false)) { _, facts, _ -> out += facts }
            assertTrue(out.any { it.base == inputBase })
            assertTrue(out.none { it.base == STATIC })
            assertTrue(core.staticBatch.isEmpty)
        }
        MethodAnalysisFixtures().use { f ->
            val x = AccessPathBase.Argument(0)
            val y = AccessPathBase.Argument(1)
            val path = field(f)
            f.statement(0, MicroEdgeBuilder().apply {
                keepZero()
                write(x, listOf(f.field("member")), weak = false, values = listOf(y),
                    aliasPaths = listOf(STATIC to path.dropLast(1)))
            }.buildStage())
            val core = actor(f, true)
            val input = f.run1Fact(y)
            val premise = f.manager.initial(f.run1Pattern(y))
            val out = arrayListOf<Facts>()
            core.applier.statement(f.statements.getValue(0), premise, input,
                Place(f.inst(0), statementEdge = true, sources = false), sink = { _, facts -> out += facts })
            assertTrue(out.any { it.base == x })
            assertTrue(out.none { it.base == STATIC })
        }
    }

    @Test fun `conditional static source results cannot bypass the final summary gate`() {
        MethodAnalysisFixtures().use { f ->
            val x = AccessPathBase.Argument(0)
            val query = f.run1Pattern(x)
            val form = operation(f).form
            val id = RuleOccurrenceId(form, RuleKind.CALL_SOURCE, 0)
            val b = RulePredicate.Builder(f.cancellation, f.manager.marks)
            val predicate = b.build(b.leaf(query))
            val action = SourceAction(ActionId(id, ActionRole.SOURCE, 0), f.run1Pattern(STATIC, field(f)).fact)
            val source = SourceRuleOccurrence(id, predicate, listOf(action), groupTransfers = true,
                unaryTransfers = listOf(SourceUnaryTransfer(predicate, query)))
            f.statement(0, MicroEdgeBuilder().apply {
                keep(x); source(source)
                staticOperation(StaticOperation.Write(OperationId(form, action = action.id),
                    Pattern(action.target, ExclusionSet.Empty), action = action.id))
            }.buildStage())
            val core = actor(f, true)
            val input = f.run1Fact(x)
            val premise = f.manager.initial(query)
            core.applier.statement(f.statements.getValue(0), premise, input,
                Place(f.inst(0), statementEdge = true, sources = true), sink = { _, _ -> })
            f.drain(core)
            assertTrue(core.summaries.all().none { (_, facts) -> facts.base == STATIC })
            assertEquals(0, checkNotNull(core.runnerPort.staticService).statistics().writeRegions)
        }
    }

    @Test fun `static call summaries and operation point requests cannot seed field origins`() {
        for (concrete in listOf(false, true)) MethodAnalysisFixtures().use { f ->
            val core = actor(f, true)
            val mark = if (concrete) f.groupMark() else MarkSlot.STAR
            val tail = if (concrete) Tail.EXACT else Tail.STAR
            val initial = f.manager.initial(f.pattern(STATIC, field(f), tail, mark))
            val current = f.fact(AccessPathBase.Return, tail = tail, mark = mark, layer = Layer.DEMAND)
            val site = ResumeSite.StatementInput(operation(f))
            core.installPoint(initial, current, site)
            core.needGroups(OperationInput(site, initial, current), f.mark("T"))
            core.summaryDelta(initial, current)
            val ref = CallerRef(f.key, f.manager.zero, Layer.NORMAL, f.inst(0))
            val part = Subscription(f.key, ref, Layer.NORMAL, Reach.NORMAL)
            core.applyStaticSummary(part, Publication(initial, current), 0)
            assertTrue(core.staticBatch.isEmpty)
            assertTrue(core.summaries.all().none())
            assertTrue(f.enqueued.isEmpty())
        }
    }

    @Test fun `callee zero static outputs are dropped before summary storage and service effects`() {
        MethodAnalysisFixtures().use { f ->
            val pass = StatementSummary.identities(listOf(AccessPathBase.Zero, STATIC))
            val plan = CallPlan(setOf(STATIC), listOf(
                CallStage.Callees(CallPoint.BEFORE, CallPoint.RETURNED, listOf(f.key)),
                CallStage.Edges(CallPoint.RETURNED, CallPoint.AFTER, StageKind.BIND_BACK, pass)),
                emptyList(), CallPoint.BEFORE, CallPoint.AFTER)
            f.call(0, plan)
            val core = actor(f, true)
            val ref = CallerRef(f.key, f.manager.zero, Layer.NORMAL, f.inst(0), 0)
            val part = Subscription(f.key, ref, Layer.NORMAL, Reach.NORMAL, zeroOnly = true)
            core.applySummary(part, Publication(f.manager.zero, f.run1Fact(STATIC, field(f))), 0)
            f.drain(core)
            assertTrue(core.summaries.all().none { (_, facts) -> facts.base == STATIC })
            assertEquals(0, checkNotNull(core.runnerPort.staticService).statistics().writeRegions)
        }
    }

    @Test fun `class query metadata retains only the exact marker branch in either arrival order`() {
        for (sourceFirst in listOf(false, true)) for (includeBase in listOf(false, true))
            MethodAnalysisFixtures().use { f ->
                val path = field(f).take(1)
                val source = PathEdge(ZERO_FACT, f.run1Pattern(STATIC, path).fact, ExclusionSet.Empty)
                val sourceAt = if (sourceFirst) 0 else 1
                f.statement(sourceAt, writes(f, source, sourceAt))
                val query = f.run1Pattern(STATIC, path, Tail.ANY)
                f.statement(1 - sourceAt, MicroEdgeBuilder().apply {
                    keepZero()
                    staticOperation(StaticOperation.MarkInput(operation(f, 1 - sourceAt), query, includeBase))
                }.buildStage())
                val core = actor(f, true)
                core.addRootZero(); f.drain(core, quantum = 1)
                val replies = f.events.filterIsInstance<RunEvent.StaticOwner>().map { it.effect }
                    .filterIsInstance<StaticOwnerEffect.PointReady>()
                assertEquals(includeBase, replies.isNotEmpty())
                assertTrue(replies.all { it.supply.region.fact.path == path && it.supply.region.fact.tail == Tail.EXACT })
                assertEquals(0, checkNotNull(core.runnerPort.staticService).statistics().readDescriptors)
            }
    }

    @Test fun `exact global marker call interests and return resumes still work in either order`() {
        for (supplyFirst in listOf(false, true)) MethodAnalysisFixtures().use { f ->
            val path = field(f).take(1)
            val initial = f.manager.initial(f.run1Pattern(STATIC, path))
            val result = f.run1Fact(AccessPathBase.Return)
            val pass = StatementSummary.identities(listOf(AccessPathBase.Zero, STATIC, AccessPathBase.Return))
            f.call(0, CallPlan(setOf(STATIC, AccessPathBase.Return), listOf(
                CallStage.Callees(CallPoint.BEFORE, CallPoint.RETURNED, listOf(f.key)),
                CallStage.Edges(CallPoint.RETURNED, CallPoint.AFTER, StageKind.BIND_BACK, pass)),
                emptyList(), CallPoint.BEFORE, CallPoint.AFTER))
            val core = actor(f, true)
            fun supply() { core.summaryDelta(f.manager.zero, f.run1Fact(STATIC, path)); f.drain(core) }
            if (supplyFirst) supply()
            val ref = CallerRef(f.key, f.manager.zero, Layer.NORMAL, f.inst(0), 0)
            core.applyStaticSummary(Subscription(f.key, ref, Layer.NORMAL, Reach.NORMAL),
                Publication(initial, result), 0)
            f.drain(core)
            if (!supplyFirst) supply()
            assertTrue(core.summaries.all().any { (premise, facts) ->
                premise == initial && facts.base == AccessPathBase.Return
            })
            assertTrue(core.summaries.all().flatMap { it.first.members.asSequence() }.filter { it.base == STATIC }
                .all { it.pathData.contentEquals(path.toIntArray()) && it.tail == Tail.EXACT })
            assertEquals(0, checkNotNull(core.runnerPort.staticService).statistics().pointSubscriptions)
        }
    }

    @Test fun `ordinary field overwrite behavior and static read assignment kills are unchanged`() {
        MethodAnalysisFixtures().use { f ->
            val x = AccessPathBase.Argument(0)
            val overwritten = f.field("f"); val other = f.field("g")
            val summary = MicroEdgeBuilder().apply {
                write(x, listOf(overwritten), weak = false, values = emptyList(), aliasPaths = emptyList())
            }.buildStage()
            val initial = f.manager.initial(f.pattern(x, tail = Tail.STAR, mark = MarkSlot.STAR))
            val cases = listOf(emptyList(), listOf(overwritten), listOf(other))
            val byMode = listOf(false, true).map { disabled ->
                val core = actor(f, disabled)
                cases.map { path ->
                    val input = f.fact(x, path, Tail.STAR, MarkSlot.STAR)
                    buildList {
                        core.applier.statement(summary, initial, input,
                            Place(f.inst(0), statementEdge = true, sources = false), sink = { _, facts -> add(facts) })
                    }
                }
            }
            assertEquals(byMode[0], byMode[1])
            assertTrue(byMode[1][0].isNotEmpty())
            assertTrue(byMode[1][0].all { it.layer == Layer.DEMAND })
            assertTrue(byMode[1][1].isEmpty())
            assertTrue(byMode[1][2].all { it.layer == Layer.NORMAL })
            val lhs = AccessPathBase.LocalVar(0)
            val read = MicroEdgeBuilder().apply { read(lhs, STATIC, field(f)) }.buildStage()
            val core = actor(f, true)
            val out = arrayListOf<Facts>()
            core.applier.statement(read, f.manager.zero, f.run1Fact(lhs),
                Place(f.inst(0), statementEdge = true, sources = false), sink = { _, facts -> out += facts })
            assertTrue(out.isEmpty(), "The static read still overwrites its lhs")
        }
    }

    @Test fun `an injected true setting has no effect on later FULL static summaries`() {
        for (index in 2..3) MethodAnalysisFixtures().use { f ->
            val core = actor(f, true, index)
            val value = f.fact(STATIC, field(f))
            core.summaryDelta(f.manager.zero, value)
            assertEquals(listOf(f.manager.zero to value), core.summaries.all().toList())
            assertNull(core.runnerPort.staticService)
        }
    }
}
