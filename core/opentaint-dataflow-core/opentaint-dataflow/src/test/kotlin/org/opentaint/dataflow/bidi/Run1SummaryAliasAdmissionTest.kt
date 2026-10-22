package org.opentaint.dataflow.bidi

import org.opentaint.dataflow.ap.ifds.AccessPathBase
import org.opentaint.dataflow.ap.ifds.ClassStaticAccessor
import org.opentaint.dataflow.bidi.ap.*
import org.opentaint.dataflow.bidi.engine.*
import org.opentaint.dataflow.bidi.fixtures.MethodAnalysisFixtures
import org.opentaint.dataflow.bidi.interp.*
import org.opentaint.dataflow.bidi.store.CallerRef
import kotlin.test.*

/** Actor controls keep the raw callee classification through the actual call walk. */
class Run1SummaryAliasAdmissionTest {
    private val argument = AccessPathBase.Argument(0)
    private val local = AccessPathBase.LocalVar(7)
    private val alias = AccessPathBase.Argument(7)

    private fun summary(vararg edges: PathEdge) = MicroEdgeBuilder().apply {
        edges.forEach(::edge)
    }.buildStage()

    private fun plan(f: MethodAnalysisFixtures, output: AccessPathBase = argument,
                     actions: List<CleanStep> = emptyList(), bindBack: StatementSummary? = null): CallPlan =
        CallPlan(setOf(local, argument, output), listOf(
            CallStage.Edges(CallPoint.BEFORE, CallPoint.BOUND, StageKind.BIND_IN,
                summary(starEdge(local, emptyList(), argument, emptyList()))),
            CallStage.Actions(CallPoint.BOUND, CallPoint.ADDED, emptyList()),
            CallStage.Callees(CallPoint.ADDED, CallPoint.RETURNED, listOf(f.store.key("callee"))),
            CallStage.Actions(CallPoint.RETURNED, CallPoint.REWRITTEN, actions),
            CallStage.Edges(CallPoint.REWRITTEN, CallPoint.AFTER, StageKind.BIND_BACK,
                bindBack ?: summary(starEdge(output, emptyList(), local, emptyList()))),
            CallStage.Edges(CallPoint.REWRITTEN, CallPoint.AFTER, StageKind.ALIASES,
                summary(starEdge(output, emptyList(), alias, emptyList())), Guard.MemoryEffect)),
            emptyList(), CallPoint.BEFORE, CallPoint.AFTER)

    private fun actor(f: MethodAnalysisFixtures) = f.core(
        localFactory = MethodLocalServicesFactory(::RunMethodLocalServices),
        callFactory = MethodCallServicesFactory(::RunMethodCallServices))

    private fun part(f: MethodAnalysisFixtures, callerInitial: InitialAp, added: Facts) =
        Subscription(f.store.key("callee"), CallerRef(f.key, callerInitial, Layer.NORMAL, f.inst(1), 2),
            added.layer, added)

    private fun leaves(f: MethodAnalysisFixtures, actor: RunMethodAnalyzer, initial: InitialAp): List<Pattern> =
        actor.edges.edgesAt(f.inst(2), initial).flatMap { f.ops.leaves(it.second) }.toList()

    @Test fun `raw STAR TAINT premise ignores result tails and DEMAND does not force aliasing`() {
        for (tail in listOf(Tail.EXACT, Tail.ANY)) MethodAnalysisFixtures().use { f ->
            f.call(1, plan(f, actions = listOf(CleanStep.Clean(
                Cleaner(argument, null, CleanReach.AT_AND_BELOW, f.mark("U"))))))
            val raw = f.manager.initial(f.run1Pattern(argument, tail = Tail.STAR))
            val caller = f.manager.initial(f.run1Pattern(AccessPathBase.Argument(3)))
            val actor = actor(f)
            actor.applySummary(part(f, caller, f.run1Fact(argument, tail = Tail.EXACT)),
                Publication(raw, f.run1Fact(argument, tail = tail)), 0)
            f.drain(actor)
            val result = leaves(f, actor, caller)
            assertTrue(result.any { it.fact.base == local })
            assertTrue(result.none { it.fact.base == alias }, "A cleaner and bind-back preserve the raw no-effect flag")
        }
    }

    @Test fun `FLOW STAR to STAR is no effect but STAR to ANY is effect even on DEMAND input`() {
        for (tail in listOf(Tail.STAR, Tail.ANY)) MethodAnalysisFixtures().use { f ->
            f.call(1, plan(f))
            val raw = f.manager.initial(f.pattern(argument, tail = Tail.STAR, mark = MarkSlot.STAR))
            val caller = f.manager.initial(f.pattern(AccessPathBase.Argument(3), tail = Tail.STAR, mark = MarkSlot.STAR))
            val actor = actor(f)
            actor.applySummary(part(f, caller, f.fact(argument, tail = Tail.STAR, mark = MarkSlot.STAR, layer = Layer.DEMAND)),
                Publication(raw, f.fact(argument, tail = tail, mark = MarkSlot.STAR)), 0)
            f.drain(actor)
            val result = leaves(f, actor, caller)
            assertTrue(result.any { it.fact.base == local })
            assertEquals(tail == Tail.ANY, result.any { it.fact.base == alias })
        }
    }

    @Test fun `mixed packed result aliases only changed paths and actual groups`() {
        MethodAnalysisFixtures().use { f ->
            f.call(1, plan(f))
            val field = f.field("f")
            val caller = f.manager.initial(f.run1Pattern(AccessPathBase.Argument(3)))
            val raw = f.manager.initial(f.run1Pattern(argument, tail = Tail.STAR))
            val same = f.run1Fact(argument, tail = Tail.EXACT)
            val changedMark = f.run1Fact(argument, tail = Tail.EXACT, full = f.mark("U"))
            val changedPath = f.run1Fact(argument, listOf(field), Tail.EXACT)
            val packed = f.manager.mergeAddDelta(f.manager.mergeAddDelta(same, changedMark).first, changedPath).first
            val actor = actor(f)
            actor.applySummary(part(f, caller, same), Publication(raw, packed), 0)
            f.drain(actor)
            val result = leaves(f, actor, caller)
            val localPayload = result.filter { it.fact.base == local }.map { it.fact.path to it.fact.mark }.toSet()
            val aliasPayload = result.filter { it.fact.base == alias }.map { it.fact.path to it.fact.mark }.toSet()
            assertEquals(setOf(emptyList<Int>() to f.groupMark(), emptyList<Int>() to f.groupMark(f.mark("U")),
                listOf(field) to f.groupMark()), localPayload)
            assertEquals(setOf(emptyList<Int>() to f.groupMark(f.mark("U")), listOf(field) to f.groupMark()), aliasPayload)
        }
    }

    @Test fun `callee argument to return remains effect when caller bindings collapse to the same local`() {
        MethodAnalysisFixtures().use { f ->
            f.call(1, plan(f, AccessPathBase.Return))
            val caller = f.manager.initial(f.run1Pattern(local))
            val raw = f.manager.initial(f.run1Pattern(argument, tail = Tail.STAR))
            val actor = actor(f)
            actor.applySummary(part(f, caller, f.run1Fact(argument)),
                Publication(raw, f.run1Fact(AccessPathBase.Return)), 0)
            f.drain(actor)
            val result = leaves(f, actor, caller)
            assertTrue(result.any { it.fact.base == local })
            assertTrue(result.any { it.fact.base == alias }, "The classifier must run before caller binding")
        }
    }

    @Test fun `ZERO callee summary remains an effect`() {
        MethodAnalysisFixtures().use { f ->
            f.call(1, plan(f))
            val actor = actor(f)
            val subscription = Subscription(f.store.key("callee"),
                CallerRef(f.key, f.manager.zero, Layer.NORMAL, f.inst(1), 2), Layer.NORMAL, Reach.NORMAL, zeroOnly = true)
            actor.applySummary(subscription, Publication(f.manager.zero, f.run1Fact(argument)), 0)
            f.drain(actor)
            val result = leaves(f, actor, f.manager.zero)
            assertTrue(result.any { it.fact.base == local })
            assertTrue(result.any { it.fact.base == alias })
        }
    }

    @Test fun `late cleaner and binding replies keep no effect caller layer and pending demotion`() {
        MethodAnalysisFixtures().use { f ->
            val group = f.groupMark()
            val bound = PathEdge(PathFact(argument, emptyList(), Tail.EXACT, group),
                PathFact(local, emptyList(), Tail.EXACT, group), ExclusionSet.Empty)
            f.call(1, plan(f, actions = listOf(CleanStep.Clean(
                Cleaner(argument, null, CleanReach.AT_AND_BELOW, group.mark))), bindBack = summary(bound)))
            val observed = ArrayList<Pair<ResumeSite, CallResumeState?>>()
            val actor = f.core(localFactory = MethodLocalServicesFactory(::RunMethodLocalServices),
                callFactory = MethodCallServicesFactory { core ->
                    val delegate = RunMethodCallServices(core)
                    object : MethodCallServices by delegate {
                        override fun resumePoint(premise: PremiseKey, facts: Facts, site: ResumeSite, state: CallResumeState?) {
                            observed += site to state
                            delegate.resumePoint(premise, facts, site, state)
                        }
                    }
                })
            val fields = listOf(f.manager.accessors.index(ClassStaticAccessor("C")), f.field("f"))
            val initial = f.manager.initial(f.pattern(STATIC, fields, Tail.STAR, MarkSlot.STAR))
            val site = ResumeSite.CleanerInput(OperationId(
                FormKey(FormOwner(), f.key.method, f.key, f.inst(1)), stageOrdinal = 3), 0, CleanerPhase.CLEAN)
            val saved = CallResumeState(Layer.NORMAL, identityCandidate = true, pendingDemotion = true)
            actor.installPoint(initial, f.fact(argument, tail = Tail.STAR, mark = MarkSlot.STAR, layer = Layer.DEMAND), site, saved)
            f.drain(actor)
            observed.clear()
            val supplied = f.pattern(STATIC, fields, Tail.STAR, MarkSlot.STAR)
            val effects = checkNotNull(actor.runnerPort.staticService).commit(StaticBatch().also {
                it.supplies += RegionGroups(supplied, f.manager.marks.atomsFor(GroupSet.of(f.manager.marks.groupOf(group.mark))))
            })
            assertTrue(effects.any { it is StaticOwnerEffect.PointReady })
            effects.filter { it.owner == actor.key }.forEach(actor::onStaticEffect)
            f.drain(actor)
            assertTrue(observed.any { it.first is ResumeSite.CleanerInput })
            assertTrue(observed.any { it.first is ResumeSite.CallStageInput })
            assertTrue(observed.all { it.second == saved }, "Every delayed operation resumes the same call arm")
            val result = leaves(f, actor, f.manager.initial(f.run1Pattern(STATIC, fields, Tail.STAR)))
            assertTrue(result.any { it.fact.base == local })
            assertTrue(result.none { it.fact.base == alias })
        }
    }
}
