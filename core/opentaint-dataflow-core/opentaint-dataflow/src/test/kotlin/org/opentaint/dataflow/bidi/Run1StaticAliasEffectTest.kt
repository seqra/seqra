package org.opentaint.dataflow.bidi

import org.opentaint.dataflow.ap.ifds.AccessPathBase
import org.opentaint.dataflow.ap.ifds.ClassStaticAccessor
import org.opentaint.dataflow.bidi.ap.*
import org.opentaint.dataflow.bidi.engine.*
import org.opentaint.dataflow.bidi.fixtures.MethodAnalysisFixtures
import org.opentaint.dataflow.bidi.interp.*
import kotlin.test.*

/** Real actor protocol for the value-only descriptors supplied by JVM alias lowering. */
class Run1StaticAliasEffectTest {
    private val argument = AccessPathBase.Argument(0)
    private val local = AccessPathBase.LocalVar(7)

    private fun id(f: MethodAnalysisFixtures, node: Int, stage: Int = 0) =
        OperationId(FormKey(FormOwner(), f.key.method, f.key, f.inst(node)), stageOrdinal = stage)

    private fun pool(f: MethodAnalysisFixtures, actor: RunMethodAnalyzer, target: List<Int>): List<RegionGroups> {
        val batch = StaticBatch()
        batch.interests += StaticInterest(f.key, Long.MAX_VALUE,
            f.pattern(STATIC, target, Tail.STAR, MarkSlot.STAR),
            GroupSet.of(f.manager.marks.groupOf(f.mark("T"))))
        return checkNotNull(actor.runnerPort.staticService).commit(batch)
            .filterIsInstance<StaticOwnerEffect.PointReady>().filter { it.id == Long.MAX_VALUE }.map { it.supply }
    }

    @Test fun `local alias value preserves its conditional original and only ZERO supply grounds a group`() {
        for (rooted in listOf(false, true)) MethodAnalysisFixtures().use { f ->
            val cls = f.manager.accessors.index(ClassStaticAccessor("C"))
            val field = f.field("f")
            val target = listOf(cls, field)
            val input = f.run1Pattern(argument)
            val initial = if (rooted) f.manager.zero else f.manager.initial(input)
            f.statement(0, if (rooted) MicroEdgeBuilder().apply {
                keepZero()
                edge(PathEdge(ZERO_FACT, input.fact, ExclusionSet.Empty))
            }.build() else StatementSummary.EMPTY)
            val valueEdge = starEdge(argument, emptyList(), STATIC, target)
            f.statement(1, MicroEdgeBuilder().apply {
                write(local, listOf(field), weak = false, values = listOf(argument),
                    aliasPaths = listOf(STATIC to listOf(cls)))
                staticOperation(StaticOperation.Write(id(f, 1), Pattern(valueEdge.to, ExclusionSet.Empty), valueEdge))
            }.build())
            val actor = f.core(localFactory = MethodLocalServicesFactory(::RunMethodLocalServices))
            actor.addInitial(initial)
            f.drain(actor)
            val raw = actor.summaries.all().filter { it.second.base == STATIC }.toList()
            assertTrue(raw.isNotEmpty())
            assertTrue(raw.all { it.first === initial }, "An alias does not reset a hypothetical original to ZERO")
            assertTrue(raw.all { it.second is TaintTree && it.second.layer == Layer.DEMAND })
            assertEquals(setOf(target), raw.flatMap { f.ops.leaves(it.second).map { leaf -> leaf.fact.path }.toList() }.toSet())
            assertEquals(rooted, pool(f, actor, target).isNotEmpty(),
                "Only an actual ZERO-derived value supplies the global group pool")
            assertEquals(0, checkNotNull(actor.runnerPort.staticService).statistics().readDescriptors,
                "An alias value publication does not register a static read")
            assertTrue(f.processed.none { it.facts.base == STATIC }, "The effect leaves ordinary successor propagation")
        }
    }

    @Test fun `call alias value publishes at ALIASES without losing the original or registering a read`() {
        for (rooted in listOf(false, true)) MethodAnalysisFixtures().use { f ->
            val target = listOf(f.manager.accessors.index(ClassStaticAccessor("C")), f.field("f"))
            val input = f.run1Pattern(argument)
            val initial = if (rooted) f.manager.zero else f.manager.initial(input)
            f.statement(0, MicroEdgeBuilder().apply {
                keepZero()
                if (rooted) edge(PathEdge(ZERO_FACT, input.fact.copy(base = local), ExclusionSet.Empty))
                else move(local, argument)
            }.build())
            fun summary(vararg edges: PathEdge) = MicroEdgeBuilder().apply { edges.forEach(::edge) }.buildStage()
            val bind = summary(ZERO_KEEP, starEdge(local, emptyList(), AccessPathBase.This, emptyList()))
            val back = summary(ZERO_KEEP, starEdge(AccessPathBase.Return, emptyList(), local, emptyList()))
            val edge = starEdge(AccessPathBase.Return, emptyList(), STATIC, target)
            val stages = listOf(
                CallStage.Edges(CallPoint.BEFORE, CallPoint.BOUND, StageKind.BIND_IN, bind),
                CallStage.Actions(CallPoint.BOUND, CallPoint.ADDED, emptyList()),
                CallStage.Edges(CallPoint.ADDED, CallPoint.RETURNED, StageKind.UNRESOLVED,
                    summary(ZERO_KEEP, starEdge(AccessPathBase.This, emptyList(), AccessPathBase.Return, emptyList()))),
                CallStage.Actions(CallPoint.RETURNED, CallPoint.REWRITTEN, emptyList()),
                CallStage.Edges(CallPoint.REWRITTEN, CallPoint.AFTER, StageKind.BIND_BACK, back),
                CallStage.Edges(CallPoint.REWRITTEN, CallPoint.AFTER, StageKind.ALIASES,
                    summary(edge).withStaticOperations(listOf(StaticOperation.Write(id(f, 1, 5),
                        Pattern(edge.to, ExclusionSet.Empty), edge))), Guard.MemoryEffect),
            )
            f.call(1, CallPlan(setOf(local), stages, emptyList(), CallPoint.BEFORE, CallPoint.AFTER))
            val actor = f.core(localFactory = MethodLocalServicesFactory(::RunMethodLocalServices),
                callFactory = MethodCallServicesFactory(::RunMethodCallServices))
            actor.addInitial(initial)
            f.drain(actor)
            val raw = actor.summaries.all().filter { it.second.base == STATIC }.toList()
            assertTrue(raw.isNotEmpty())
            assertTrue(raw.all { it.first === initial && it.second.layer == Layer.DEMAND })
            assertEquals(setOf(target), raw.flatMap { f.ops.leaves(it.second).map { leaf -> leaf.fact.path }.toList() }.toSet())
            assertEquals(rooted, pool(f, actor, target).isNotEmpty())
            assertEquals(0, checkNotNull(actor.runnerPort.staticService).statistics().readDescriptors)
            assertTrue(f.processed.none { it.facts.base == STATIC })
        }
    }

    @Test fun `ordinary FLOW alias asks its original Base for a group and does not fabricate supply`() {
        MethodAnalysisFixtures().use { f ->
            val target = listOf(f.manager.accessors.index(ClassStaticAccessor("C")), f.field("f"))
            val initial = f.manager.initial(argument, null, Tail.STAR, ExclusionSet.Empty, MarkSlot.STAR)
            val edge = starEdge(argument, emptyList(), STATIC, target)
            f.statement(1, MicroEdgeBuilder().apply {
                write(local, listOf(target.last()), weak = false, values = listOf(argument),
                    aliasPaths = listOf(STATIC to listOf(target.first())))
                staticOperation(StaticOperation.Write(id(f, 1), Pattern(edge.to, ExclusionSet.Empty), edge))
            }.build())
            val actor = f.core(localFactory = MethodLocalServicesFactory(::RunMethodLocalServices))
            actor.addInitial(initial)
            f.drain(actor)
            val raw = actor.summaries.all().filter { it.second.base == STATIC }.toList()
            assertTrue(raw.any { it.first === initial && it.second is FlowTree })
            // The interest queues an owner callback; consume it through the same actor protocol.
            val wanted = StaticBatch().also { it.interests += StaticInterest(f.key, 999,
                f.pattern(STATIC, target, Tail.STAR, MarkSlot.STAR),
                GroupSet.of(f.manager.marks.groupOf(f.mark("T")))) }
            val effects = checkNotNull(actor.runnerPort.staticService).commit(wanted)
            assertTrue(effects.none { it is StaticOwnerEffect.PointReady }, "An ordinary FLOW is not group supply")
            for (effect in effects) if (effect.owner == actor.key) actor.onStaticEffect(effect)
            f.drain(actor)
            assertTrue(f.requestRow(actor, Run1Slot.Base(argument)).containsId(f.groupMark().mark.id))
            assertTrue(actor.summaries.all().none { it.first.isZero && it.second is TaintTree && it.second.base == STATIC })
            assertEquals(0, checkNotNull(actor.runnerPort.staticService).statistics().readDescriptors)
        }
    }
}
