package org.opentaint.dataflow.bidi

import org.opentaint.dataflow.ap.ifds.AccessPathBase
import org.opentaint.dataflow.ap.ifds.ClassStaticAccessor
import org.opentaint.dataflow.bidi.ap.*
import org.opentaint.dataflow.bidi.engine.*
import org.opentaint.dataflow.bidi.fixtures.MethodAnalysisFixtures
import org.opentaint.dataflow.bidi.interp.*
import org.opentaint.dataflow.bidi.store.Record
import org.opentaint.dataflow.util.Cancellation
import kotlin.test.*

class StaticPointImagePackingTest {
    private class Fixture : AutoCloseable {
        val f = MethodAnalysisFixtures()
        val resumed = ArrayList<OperationInput>()
        val actor = f.core(callFactory = MethodCallServicesFactory {
            object : MethodCallServices {
                override fun resumePoint(premise: PremiseKey, facts: Facts, site: ResumeSite, state: CallResumeState?) {
                    resumed += OperationInput(site, premise, facts, state)
                }
                override fun process(item: EdgeDelta): Unit = error("Unexpected call processing")
                override fun applySummary(part: Subscription, pub: Publication, member: Int): Unit = error("Unexpected summary")
                override fun applyRecord(part: Subscription, record: Record): Unit = error("Unexpected record")
                override fun applyRecordView(part: Subscription, view: RecordLeafView): Unit = error("Unexpected record view")
                override fun freeze() = Unit
            }
        })
        // Test the real directory and scalar AP transfer without a call-plan
        // interpreter rewriting the observations that arrive at the resume site.
        val directory = Run1PointResume(actor)
        val service = checkNotNull(actor.runnerPort.staticService)
        val staticPath = listOf(f.manager.accessors.index(ClassStaticAccessor("C")), f.field("f"))
        val original = f.manager.initial(STATIC, f.manager.path(staticPath), Tail.STAR,
            ExclusionSet.Empty, MarkSlot.STAR)
        val operation = OperationId(FormKey(FormOwner(), f.key.method, f.key, f.inst(1)))
        val site = ResumeSite.CleanerInput(operation, 4, CleanerPhase.CLEAN)
        val group = f.manager.marks.groupOf(f.mark("T"))
        val otherGroup = f.manager.marks.groupOf(f.mark("U"))
        val local = AccessPathBase.LocalVar(0)

        fun flow(paths: List<List<Int>>, mx: MarkExclusions = MarkExclusions.EMPTY): FlowTree {
            require(paths.isNotEmpty())
            // Construct independent shared child geometry; the directory is the
            // only code under test that unions the submitted roots.
            fun root(prefix: List<Int>): FlowNode {
                val children = paths.filter { it.size > prefix.size && it.take(prefix.size) == prefix }
                    .map { it[prefix.size] }.distinct().sorted()
                return FlowAlgebra.node(if (prefix in paths) FlowLeaf.LEAF else FlowLeaf.NONE,
                    children.toIntArray(), children.map { root(prefix + it) }.toTypedArray())
            }
            return f.manager.flowTree(local, Layer.DEMAND, ExclusionSet.Empty, mx, root(emptyList()), Tail.STAR)
        }

        fun input(flow: FlowTree, site: ResumeSite = this.site, state: CallResumeState? = null,
                  initial: InitialAp = original): OperationInput = OperationInput(site, initial, flow, state)

        fun requests(): List<StaticOwnerEffect.PointReady> {
            val batch = StaticBatch().apply { interests += actor.staticBatch.interests }
            actor.staticBatch.interests.clear()
            return service.commit(batch).filterIsInstance<StaticOwnerEffect.PointReady>()
        }

        fun supply(group: GroupId, other: GroupId? = null): List<StaticOwnerEffect.PointReady> =
            service.commit(StaticBatch().apply {
                supplies += RegionGroups(original.toPattern(), GroupSet.of(listOfNotNull(group, other)))
            }).filterIsInstance<StaticOwnerEffect.PointReady>()

        fun answer(effects: List<StaticOwnerEffect.PointReady>) {
            effects.forEach(directory::onEffect)
            f.drain(actor)
        }

        /** The old scalar algorithm, without the new key or pending-image union. */
        fun scalarReference(at: OperationInput, group: GroupId): Set<OperationInput> {
            val actual = actor.staticFacts(RegionGroups(original.toPattern(), GroupSet.of(group)))
            val expected = HashSet<OperationInput>()
            for (image in f.ops.leaves(at.facts)) {
                val scalar = f.manager.factsOf(image.fact, image.exclusion, at.facts.layer) as FlowTree
                val relation = f.manager.flowTree(scalar.base, scalar.layer, scalar.exclusion,
                    MarkExclusions.EMPTY, scalar.root, scalar.tail)
                for (leaf in f.ops.leaves(actual)) {
                    // A supplier's ANY-valued TAINT leaf still uses the field
                    // STAR initial. State that fixture grammar independently;
                    // do not call the production concreteInitial helper.
                    val premise = f.manager.initial(STATIC, f.manager.path(staticPath), Tail.STAR,
                        ExclusionSet.Empty, leaf.fact.mark)
                    f.ops.applySummary(f.manager.factsOf(leaf.fact, leaf.exclusion, Layer.DEMAND),
                        at.premise as InitialAp, relation, actor.mode, object : ApOut {
                            override fun result(facts: Facts) {
                                expected += OperationInput(at.site, premise, facts.withLayer(Layer.DEMAND), at.callState)
                            }
                            override fun markRequest(mark: TaintMark): Unit = error("Scalar reply must clear current mx")
                        })
                }
            }
            return expected
        }

        override fun close() { directory.clear(); f.close() }
    }

    @Test fun `pack exact image paths per group and clear only the current mx`() = Fixture().use { t ->
        val a = t.f.field("a"); val b = t.f.field("b"); val q = t.f.field("q")
        val first = t.input(t.flow(listOf(listOf(a), listOf(a, q)),
            MarkExclusions.fromMarks(MarkSet(intArrayOf(t.f.manager.marks.group(t.group).id)))))
        val shared = FlowAlgebra.prepend(t.f.manager, t.f.manager.path(listOf(q)), FlowAlgebra.LEAF_NODE)
        val sharedRoot = FlowAlgebra.node(FlowLeaf.NONE, intArrayOf(a, b).sortedArray(), arrayOf(shared, shared))
        val second = t.input(t.f.manager.flowTree(t.local, Layer.DEMAND, ExclusionSet.Empty,
            MarkExclusions.EMPTY, sharedRoot, Tail.STAR))
        val onlyOther = t.input(t.flow(listOf(listOf(b))))
        t.directory.request(first, GroupSet.of(t.group))
        t.directory.request(second, GroupSet.of(t.group))
        t.directory.request(first, GroupSet.of(t.group))
        t.directory.request(onlyOther, GroupSet.of(t.otherGroup))
        val interests = t.actor.staticBatch.interests
        assertEquals(2, interests.size, "Only new row/group memberships register")
        assertEquals(1, interests.map { it.id }.distinct().size, "Current mx and path belong to the payload")
        t.requests()
        t.answer(t.supply(t.group, t.otherGroup))
        val expected = t.scalarReference(first, t.group) + t.scalarReference(second, t.group) +
            t.scalarReference(onlyOther, t.otherGroup)
        assertEquals(expected, t.resumed.toSet())
        assertEquals(expected.size, t.resumed.size, "Exact union removes duplicate paths and retains parent plus child")
        assertTrue(t.resumed.none { resumed ->
            t.f.ops.leaves(resumed.facts).any { leaf -> leaf.fact.path == listOf(a) &&
                leaf.fact.mark == MarkSlot.Concrete(t.f.manager.marks.group(t.otherGroup)) }
        }, "An answer must not cross a group's image set")
    }

    @Test fun `late images join queued answers and partial answers retain only their own groups`() = Fixture().use { t ->
        val a = t.input(t.flow(listOf(listOf(t.f.field("a")))))
        val b = t.input(t.flow(listOf(listOf(t.f.field("b")))))
        t.directory.request(a, GroupSet(intArrayOf(t.group.value, t.otherGroup.value)))
        val firstId = t.actor.staticBatch.interests.single().id
        t.requests()
        val queued = t.supply(t.group)
        assertEquals(1, queued.size)
        t.directory.request(b, GroupSet.of(t.group))
        assertTrue(t.actor.staticBatch.interests.isEmpty(), "In-flight answer still owns the pending group")
        t.answer(queued)
        assertEquals(t.scalarReference(a, t.group) + t.scalarReference(b, t.group), t.resumed.toSet())
        t.resumed.clear()
        t.directory.request(b, GroupSet.of(t.group))
        assertEquals(firstId, t.actor.staticBatch.interests.single().id, "The other pending group keeps the row alive")
        t.answer(t.requests()) // Supply already exists.
        assertEquals(t.scalarReference(b, t.group), t.resumed.toSet())
        t.resumed.clear()
        t.answer(t.supply(t.otherGroup))
        assertEquals(t.scalarReference(a, t.otherGroup), t.resumed.toSet())
        t.directory.request(a, GroupSet.of(t.group))
        assertTrue(t.actor.staticBatch.interests.single().id > firstId, "A drained row gets a fresh monotone ID")
        t.answer(t.requests())
        assertFailsWith<IllegalStateException> { t.directory.onEffect(queued.single()) }
        assertEquals(0, t.service.statistics().pointSubscriptions)
    }

    @Test fun `all complete header partitions keep independent subscriptions`() = Fixture().use { t ->
        val original = t.input(t.flow(listOf(emptyList())))
        val flow = original.facts as FlowTree
        val otherInitial = t.f.manager.initial(STATIC,
            t.f.manager.path(listOf(t.staticPath[0], t.f.field("other"))), Tail.STAR,
            ExclusionSet.Empty, MarkSlot.STAR)
        val variants = listOf(
            original,
            original.copy(premise = otherInitial),
            original.copy(site = t.site.copy(step = t.site.step + 1)),
            original.copy(site = t.site.copy(phase = CleanerPhase.WRITE)),
            original.copy(site = t.site.copy(operation = t.operation.copy(substep = 1))),
            original.copy(site = t.site.copy(operation = t.operation.copy(stageOrdinal = 1))),
            original.copy(site = t.site.copy(operation = t.operation.copy(action = ActionId(
                RuleOccurrenceId(t.operation.form, RuleKind.REWRITER, 0), ActionRole.RETURN_REWRITER, 0)))),
            original.copy(site = ResumeSite.ReadOutput(t.operation, t.site)),
            original.copy(site = ResumeSite.ReadOutput(t.operation, t.site.copy(step = 9))),
            original.copy(site = ResumeSite.CallStageInput(t.operation)),
            original.copy(facts = t.f.manager.flowTree(AccessPathBase.LocalVar(1), flow.layer,
                flow.exclusion, flow.markExclusion, flow.root, flow.tail)),
            original.copy(facts = t.f.manager.flowTree(flow.base, flow.layer,
                flow.exclusion, flow.markExclusion, flow.root, Tail.ANY)),
            original.copy(facts = t.f.manager.flowTree(flow.base, flow.layer,
                ExclusionSet.of(t.f.field("excluded")), flow.markExclusion, flow.root, flow.tail)),
            original.copy(facts = flow.withLayer(Layer.NORMAL)),
        ) + Layer.entries.flatMap { layer -> listOf<Boolean?>(null, false, true).flatMap { identity ->
            listOf(false, true).map { demotion ->
                original.copy(callState = CallResumeState(layer, identity, demotion))
            }
        } }
        // Some headers are defensive storage controls, not new run-1 input shapes.
        variants.forEach { t.directory.request(it, GroupSet.of(t.group)) }
        assertEquals(variants.size, t.actor.staticBatch.interests.map { it.id }.distinct().size)
        variants.forEach { t.directory.request(it, GroupSet.of(t.group)) }
        assertEquals(variants.size, t.actor.staticBatch.interests.size)
    }

    @Test fun `a pre-existing supply answers every scalar image once and preserves full call state`() = Fixture().use { t ->
        t.supply(t.group)
        val flow = t.flow(listOf(emptyList(), listOf(t.f.field("x"))))
        val inputs = listOf<Boolean?>(null, false, true).flatMap { identity -> listOf(false, true).map { demotion ->
            t.input(flow, state = CallResumeState(Layer.NORMAL, identity, demotion))
        } }
        inputs.forEach { t.directory.request(it, GroupSet.of(t.group)) }
        t.answer(t.requests())
        assertEquals(inputs.flatMap { t.scalarReference(it, t.group) }.toSet(), t.resumed.toSet())
    }

    @Test fun `unanswered rows remain passive and clear keeps IDs monotone`() = Fixture().use { t ->
        val input = t.input(t.flow(listOf(emptyList())))
        t.directory.request(input, GroupSet.of(t.group))
        val old = t.actor.staticBatch.interests.single()
        t.requests()
        assertTrue(t.actor.work(1), "A pending reply does not create runnable work")
        t.directory.clear()
        assertFailsWith<IllegalStateException> {
            t.directory.onEffect(StaticOwnerEffect.PointReady(t.f.key, old.id,
                RegionGroups(t.original.toPattern(), GroupSet.of(t.group))))
        }
        t.directory.request(input, GroupSet.of(t.group))
        assertTrue(t.actor.staticBatch.interests.single().id > old.id)
    }

    @Test fun `cancellation is observed during pending union and answer drain`() = Fixture().use { t ->
        val input = t.input(t.flow(listOf(emptyList())))
        t.directory.request(input, GroupSet.of(t.group))
        t.requests()
        t.f.cancellation.cancel()
        assertFailsWith<Cancellation.Cancelled> { t.directory.request(input, GroupSet.of(t.group)) }
        t.f.cancellation.activate()
        val reply = t.supply(t.group).single()
        t.f.cancellation.cancel()
        assertFailsWith<Cancellation.Cancelled> { t.directory.onEffect(reply) }
        assertTrue(t.resumed.isEmpty())
        t.f.cancellation.activate()
        // Cancellation is terminal for this analysis. No rollback or retry is promised.
        t.directory.clear()
    }
}
