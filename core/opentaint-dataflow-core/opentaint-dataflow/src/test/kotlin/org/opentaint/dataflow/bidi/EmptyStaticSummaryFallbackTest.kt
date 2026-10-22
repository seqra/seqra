package org.opentaint.dataflow.bidi

import org.opentaint.dataflow.ap.ifds.AccessPathBase
import org.opentaint.dataflow.ap.ifds.Accessor
import org.opentaint.dataflow.ap.ifds.ClassStaticAccessor
import org.opentaint.dataflow.ap.ifds.FactTypeChecker
import org.opentaint.dataflow.bidi.ap.*
import org.opentaint.dataflow.bidi.engine.*
import org.opentaint.dataflow.bidi.fixtures.MethodAnalysisFixtures
import org.opentaint.dataflow.bidi.interp.*
import kotlin.test.*

/** Exercise the actual cleaner READ/WRITE fallback through the public actor services. */
class EmptyStaticSummaryFallbackTest {
    private val x = AccessPathBase.Argument(0)

    private fun summary(vararg edges: PathEdge,
                        inputs: Map<AccessPathBase, TypeFilter> = emptyMap(),
                        outputs: Map<AccessPathBase, TypeFilter> = emptyMap(),
                        operations: List<StaticOperation> = emptyList()) = StatementSummary(
        edges.mapTo(LinkedHashSet()) { it.from.base }, edges.map(MicroEdge::of), emptyList(),
        inputs, outputs, staticOperations = operations)

    private fun filter(label: String, trace: MutableList<String>) = TypeFilter(
        object : FactTypeChecker.FactApFilter {
            override fun check(accessor: Accessor): FactTypeChecker.FilterResult {
                trace += "$label:path"
                return FactTypeChecker.FilterResult.Accept
            }
        }, MarkPolicy { _, _ -> trace += "$label:mark"; true })

    private fun plan(f: MethodAnalysisFixtures, step: CleanStep.Field): CallPlan = CallPlan(
        setOf(x, STATIC), listOf(CallStage.Actions(CallPoint.RETURNED, CallPoint.AFTER, listOf(step))),
        emptyList(), CallPoint.RETURNED, CallPoint.AFTER).also { f.call(1, it) }

    private fun resume(f: MethodAnalysisFixtures, actor: RunMethodAnalyzer, services: RunMethodCallServices,
                       premise: PremiseKey, input: Facts) = services.resumePoint(premise, input,
        ResumeSite.CleanerInput(actor.operation(f.inst(1)), 0, CleanerPhase.INPUT),
        CallResumeState(Layer.NORMAL, true))

    @Test fun `empty nonstatic metadata retains read write filters cuts and ordinary output`() {
        MethodAnalysisFixtures().use { f ->
            val field = f.field("f")
            val temporary = AccessPathBase.CleanerTemporary()
            val trace = ArrayList<String>()
            val read = summary(starEdge(x, listOf(field), temporary, emptyList()),
                inputs = mapOf(x to filter("read-input", trace)),
                outputs = mapOf(temporary to filter("read-result", trace)))
            val write = summary(starEdge(temporary, emptyList(), x, listOf(field)),
                inputs = mapOf(temporary to filter("write-input", trace)),
                outputs = mapOf(x to filter("write-result", trace)))
            plan(f, CleanStep.Field(temporary, read,
                Cleaner(temporary, null, CleanReach.AT_AND_BELOW, f.groupMark(f.mark("U")).mark), write))
            val actor = f.core()
            val services = RunMethodCallServices(actor)
            val input = f.run1Fact(x, listOf(field))
            val original = f.manager.initial(f.run1Pattern(x))
            resume(f, actor, services, original, input)
            val output = actor.edges.edgesAt(f.inst(2), original).map { it.second }.toList()
            assertEquals(listOf(input), output)
            assertEquals(listOf("read-input:path", "read-result:mark", "write-input:mark", "write-result:path"), trace)
            assertEquals(listOf(Cut.STATEMENT, Cut.STATEMENT, Cut.CALL), f.cuts.map { it.first })
            assertTrue(f.requests.isEmpty())
            assertTrue(actor.staticBatch.isEmpty)
            assertTrue(read.staticOperations.isEmpty())
            assertTrue(write.staticOperations.isEmpty())
            services.freeze()
        }
    }

    @Test fun `a derived static READ still registers and resumes the addressed cleaner suffix`() {
        MethodAnalysisFixtures().use { f ->
            val path = listOf(f.manager.accessors.index(ClassStaticAccessor("C")), f.field("f"))
            val temporary = AccessPathBase.CleanerTemporary()
            val read = summary(starEdge(STATIC, path, temporary, emptyList()))
            val write = summary(starEdge(temporary, emptyList(), x, emptyList()))
            plan(f, CleanStep.Field(temporary, read,
                Cleaner(temporary, null, CleanReach.AT_AND_BELOW, f.groupMark().mark), write))
            lateinit var services: RunMethodCallServices
            val actor = f.core(callFactory = MethodCallServicesFactory { core ->
                RunMethodCallServices(core).also { services = it }
            })
            resume(f, actor, services, f.manager.zero, Reach.NORMAL)
            assertEquals(listOf(path), actor.staticBatch.reads.map { it.region.fact.path })
            f.drain(actor)
            val service = checkNotNull(actor.runnerPort.staticService)
            assertEquals(1, service.statistics().readDescriptors)
            val region = f.pattern(STATIC, path, Tail.STAR, MarkSlot.STAR)
            val supply = StaticBatch().also {
                it.writes += region
                it.supplies += RegionGroups(region, f.manager.marks.atomsFor(GroupSet.of(f.manager.marks.groupOf(f.mark("T")))))
            }
            service.commit(supply).filter { it.owner == actor.key }.forEach(actor::onStaticEffect)
            f.drain(actor)
            val concrete = actor.edges.edgesAt(f.inst(2)).filter { it.second is TaintTree && it.second.base == x }.toList()
            assertTrue(concrete.isNotEmpty(), "The real read answer must reach the cleaner suffix and ordinary successor")
            assertTrue(concrete.all { (premise, value) ->
                premise.members.all { it.base == STATIC } && value.layer == Layer.DEMAND &&
                    f.ops.leaves(value).all { it.fact.mark == f.groupMark() }
            })
            assertTrue(read.staticOperations.isEmpty(), "Fallback compilation must not mutate the owned cached form")
            assertEquals(1, service.statistics().readDescriptors)
        }
    }

    @Test fun `a derived static WRITE preserves conditional original and only rooted input supplies`() {
        for (rooted in listOf(false, true)) MethodAnalysisFixtures().use { f ->
            val field = f.field("f")
            val target = listOf(f.manager.accessors.index(ClassStaticAccessor("C")), f.field("g"))
            val temporary = AccessPathBase.CleanerTemporary()
            val read = summary(starEdge(x, listOf(field), temporary, emptyList()))
            val write = summary(starEdge(temporary, emptyList(), STATIC, target))
            plan(f, CleanStep.Field(temporary, read,
                Cleaner(temporary, null, CleanReach.AT_AND_BELOW, f.groupMark().mark), write))
            val actor = f.core()
            val services = RunMethodCallServices(actor)
            val original = if (rooted) f.manager.zero else f.manager.initial(f.run1Pattern(x))
            resume(f, actor, services, original, f.run1Fact(x, listOf(field)))
            val raw = actor.summaries.all().filter { it.second.base == STATIC }.toList()
            assertTrue(raw.isNotEmpty())
            assertTrue(raw.all { (premise, value) -> premise === original && value.layer == Layer.DEMAND })
            assertEquals(setOf(target), raw.flatMap { f.ops.leaves(it.second).map { leaf -> leaf.fact.path }.toList() }.toSet())
            assertEquals(rooted, actor.staticBatch.supplies.isNotEmpty())
            assertTrue(actor.staticBatch.reads.isEmpty(), "WRITE metadata does not create a static read")
            assertTrue(actor.edges.edgesAt(f.inst(2), original).none(), "A static effect does not enter ordinary successors")
            assertTrue(write.staticOperations.isEmpty())
            services.freeze()
        }
    }

    @Test fun `prepopulated READ metadata is retained instead of deriving extra descriptors`() {
        MethodAnalysisFixtures().use { f ->
            val c = f.manager.accessors.index(ClassStaticAccessor("C"))
            val first = listOf(c, f.field("f"))
            val second = listOf(c, f.field("g"))
            val temporary = AccessPathBase.CleanerTemporary()
            val firstEdge = starEdge(STATIC, first, temporary, emptyList())
            val secondEdge = starEdge(STATIC, second, temporary, emptyList())
            val descriptor = StaticOperation.Read(OperationId(
                FormKey(FormOwner(), f.key.method, f.key, f.inst(1))), secondEdge)
            val read = summary(firstEdge, secondEdge, operations = listOf(descriptor))
            plan(f, CleanStep.Field(temporary, read,
                Cleaner(temporary, null, CleanReach.AT_AND_BELOW, f.groupMark().mark),
                summary(starEdge(temporary, emptyList(), x, emptyList()))))
            val actor = f.core()
            val services = RunMethodCallServices(actor)
            resume(f, actor, services, f.manager.zero, Reach.NORMAL)
            assertEquals(listOf(second), actor.staticBatch.reads.map { it.region.fact.path })
            assertSame(descriptor, read.staticOperations.single())
            assertSame(descriptor.id, read.staticOperations.single().id)
            services.freeze()
        }
    }
}
