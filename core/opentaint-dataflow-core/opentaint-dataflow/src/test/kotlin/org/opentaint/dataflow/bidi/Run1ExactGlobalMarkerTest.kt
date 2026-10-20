package org.opentaint.dataflow.bidi

import org.opentaint.dataflow.ap.ifds.AccessPathBase
import org.opentaint.dataflow.ap.ifds.ClassStaticAccessor
import org.opentaint.dataflow.bidi.ap.*
import org.opentaint.dataflow.bidi.engine.*
import org.opentaint.dataflow.bidi.fixtures.FiniteProgramFixtures
import org.opentaint.dataflow.bidi.fixtures.MethodAnalysisFixtures
import org.opentaint.dataflow.bidi.interp.*
import kotlin.test.*

/** A global C marker is an exact atom, separate from named static fields. */
class Run1ExactGlobalMarkerTest {
    private fun guardedLocal(f: MethodAnalysisFixtures, at: Int, query: Pattern,
                             local: AccessPathBase, full: TaintMark): StatementSummary {
        val form = FormKey(FormOwner(), f.key.method, f.key, f.inst(at))
        val id = RuleOccurrenceId(form, RuleKind.CALL_SOURCE, 0)
        val builder = RulePredicate.Builder(f.cancellation, f.manager.marks)
        val condition = builder.build(builder.leaf(query))
        val action = SourceAction(ActionId(id, ActionRole.SOURCE, 0), f.run1Pattern(local, full = full).fact)
        return MicroEdgeBuilder().apply {
            keepZero()
            source(SourceRuleOccurrence(id, condition, listOf(action), groupTransfers = true,
                unaryTransfers = listOf(SourceUnaryTransfer(condition, query))))
            staticOperation(StaticOperation.MarkInput(OperationId(form), query))
        }.build()
    }

    @Test fun `global marker replies preserve the exact point and never supply fields`() {
        for (supplyFirst in listOf(false, true)) FiniteProgramFixtures().use { f ->
            val owner = f.method("global marker", listOf(emptyList()), 0).key()
            val cls = f.ap.accessors.index(ClassStaticAccessor("C"))
            val g = f.ap.marks.groupOf(f.mark("T"))
            val marker = Pattern(PathFact(STATIC, listOf(cls), Tail.EXACT, MarkSlot.STAR), ExclusionSet.Empty)
            val field = marker.copy(fact = marker.fact.copy(path = listOf(cls, f.field("f")), tail = Tail.STAR))
            val service = Run1StaticService(f.ap)
            val supply = StaticBatch().apply { supplies += RegionGroups(marker, GroupSet.of(g)) }
            val queries = StaticBatch().apply {
                interests += StaticInterest(owner, 1, marker, GroupSet.of(g))
                interests += StaticInterest(owner, 2, field, GroupSet.of(g))
                reads += StaticReadRegistration(owner, 3, field)
            }
            assertTrue(service.commit(if (supplyFirst) supply else queries).isEmpty())
            val answers = service.commit(if (supplyFirst) queries else supply)
            assertTrue(answers.filterIsInstance<StaticOwnerEffect.ReadReady>().isEmpty())
            val reply = answers.filterIsInstance<StaticOwnerEffect.PointReady>().single()
            assertEquals(1L, reply.id)
            assertEquals(marker, reply.supply.region)
            val current = service.facts(reply.supply.region, reply.supply.groups)
            assertEquals(listOf(Tail.EXACT), f.ops.leaves(current).map { it.fact.tail }.toList())
            val leaf = f.ops.leaves(current).single()
            val initial = f.ap.initial(leaf)
            validateRun1Initial(initial, 1)
            assertEquals(Tail.EXACT, initial.tail)
            assertEquals(listOf(Tail.EXACT), f.ops.leaves(f.ops.startFact(initial)).map { it.fact.tail }.toList())
        }
    }

    @Test fun `late global marker resumes an exact local check without a class FLOW initial`() {
        MethodAnalysisFixtures().use { f ->
            val cls = f.manager.accessors.index(ClassStaticAccessor("C"))
            val path = listOf(cls)
            val local = AccessPathBase.LocalVar(0)
            val full = f.mark("T")
            val marker = f.run1Pattern(STATIC, path, Tail.EXACT, full)
            fun operation(index: Int) = OperationId(FormKey(FormOwner(), f.key.method, f.key, f.inst(index)))
            f.statement(0, guardedLocal(f, 0, marker, local, full))
            val source = PathEdge(ZERO_FACT, marker.fact, ExclusionSet.Empty)
            f.statement(1, MicroEdgeBuilder().apply {
                keepZero(); keep(local); edge(source)
                staticOperation(StaticOperation.Write(operation(1), marker, source))
            }.build())
            val sink = localSink(listOf(f.pattern(local, mark = MarkSlot.Concrete(full))))
            f.exitRules(2, ExitRules(RuleStatement(StatementSummary.EMPTY, StatementSummary.EMPTY, listOf(sink)), false, emptySet()))
            val actor = f.core(localFactory = MethodLocalServicesFactory(::RunMethodLocalServices))
            actor.addRootZero()
            f.drain(actor, quantum = 1)
            assertTrue(f.vulnerabilities.witnessesOf(1).any { it.first.rule == sink.rule })
            val staticInitials = f.processed.flatMap { row -> row.premise.members.filter { it.base == STATIC } }
            assertTrue(staticInitials.isNotEmpty())
            assertTrue(staticInitials.all { it.pathData.contentEquals(path.toIntArray()) &&
                it.tail == Tail.EXACT && it.mark is MarkSlot.Concrete })
            assertEquals(0, checkNotNull(actor.runnerPort.staticService).statistics().flowWriterRows)
        }
    }
    @Test fun `class ANY query metadata selects exact global markers and fields in both orders`() {
        for (global in listOf(false, true)) for (sourceFirst in listOf(false, true))
            MethodAnalysisFixtures().use { f ->
                val cls = f.manager.accessors.index(ClassStaticAccessor("C"))
                val local = AccessPathBase.LocalVar(0)
                val full = f.mark("T")
                val query = f.run1Pattern(STATIC, listOf(cls), Tail.ANY, full)
                val target = if (global) listOf(cls) else listOf(cls, f.field("f"))
                val source = PathEdge(ZERO_FACT, f.run1Pattern(STATIC, target, Tail.EXACT, full).fact, ExclusionSet.Empty)
                val sourceAt = if (sourceFirst) 0 else 1
                val queryAt = 1 - sourceAt
                fun operation(index: Int) = OperationId(FormKey(FormOwner(), f.key.method, f.key, f.inst(index)))
                f.statement(sourceAt, MicroEdgeBuilder().apply {
                    keepZero(); keep(local); edge(source)
                    staticOperation(StaticOperation.Write(operation(sourceAt), Pattern(source.to, ExclusionSet.Empty), source))
                }.build())
                f.statement(queryAt, guardedLocal(f, queryAt, query, local, full))
                val sink = localSink(listOf(f.pattern(local, mark = MarkSlot.Concrete(full))))
                f.exitRules(2, ExitRules(RuleStatement(StatementSummary.EMPTY, StatementSummary.EMPTY, listOf(sink)), false, emptySet()))
                val actor = f.core(localFactory = MethodLocalServicesFactory(::RunMethodLocalServices))
                actor.addRootZero(); f.drain(actor, quantum = 1)
                assertTrue(f.vulnerabilities.witnessesOf(1).any { it.first.rule == sink.rule },
                    "ANY keeps its empty-suffix meaning and also selects field supplies")
                val classInitials = f.processed.flatMap { it.premise.members }.filter {
                    it.base == STATIC && it.pathData.size == 1
                }
                assertTrue(classInitials.all { it.tail == Tail.EXACT && it.mark is MarkSlot.Concrete })
                assertTrue(actor.summaries.all().flatMap { f.ops.leaves(it.second) }.none {
                    it.fact.base == STATIC && it.fact.path.size == 1 && it.fact.tail != Tail.EXACT
                })
            }
    }

    @Test fun `cleaner reach keeps the global exact branch separate from descendant field queries`() {
        for (reach in CleanReach.entries) MethodAnalysisFixtures().use { f ->
            val cls = f.manager.accessors.index(ClassStaticAccessor("C"))
            val full = f.mark("T")
            val marker = f.run1Pattern(STATIC, listOf(cls), Tail.EXACT, full)
            val source = PathEdge(ZERO_FACT, marker.fact, ExclusionSet.Empty)
            val id = OperationId(FormKey(FormOwner(), f.key.method, f.key, f.inst(0)))
            f.statement(0, MicroEdgeBuilder().apply {
                keepZero(); edge(source)
                staticOperation(StaticOperation.Write(id, marker, source))
            }.build())
            val pass = StatementSummary.identities(listOf(AccessPathBase.Zero, STATIC))
            f.call(1, CallPlan(setOf(AccessPathBase.Zero, STATIC), listOf(
                CallStage.Edges(CallPoint.BEFORE, CallPoint.BOUND, StageKind.BIND_IN, pass),
                CallStage.Actions(CallPoint.BOUND, CallPoint.ADDED, listOf(
                    CleanStep.Clean(Cleaner(STATIC, f.manager.path(listOf(cls)), reach, full)))),
                CallStage.Edges(CallPoint.ADDED, CallPoint.RETURNED, StageKind.UNRESOLVED, pass),
                CallStage.Edges(CallPoint.RETURNED, CallPoint.AFTER, StageKind.BIND_BACK, pass),
            ), emptyList(), CallPoint.BEFORE, CallPoint.AFTER))
            val actor = f.core(localFactory = MethodLocalServicesFactory(::RunMethodLocalServices),
                callFactory = MethodCallServicesFactory(::RunMethodCallServices))
            actor.addRootZero(); f.drain(actor, quantum = 1)
            val replies = f.events.filterIsInstance<RunEvent.StaticOwner>()
                .map { it.effect }.filterIsInstance<StaticOwnerEffect.PointReady>()
                .filter { it.supply.region.fact.path == listOf(cls) }
            assertEquals(reach != CleanReach.BELOW, replies.isNotEmpty())
            assertTrue(replies.all { it.supply.region.fact.tail == Tail.EXACT })
            val reads = checkNotNull(actor.runnerPort.staticService).statistics().readDescriptors
            assertEquals(if (reach == CleanReach.EXACT) 0 else 1, reads)
        }
    }

}
