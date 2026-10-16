package org.opentaint.dataflow.bidi

import org.opentaint.dataflow.ap.ifds.AccessPathBase
import org.opentaint.dataflow.ap.ifds.ClassStaticAccessor
import org.opentaint.dataflow.bidi.ap.*
import org.opentaint.dataflow.bidi.engine.*
import org.opentaint.dataflow.bidi.fixtures.StoreFixtures
import org.opentaint.dataflow.bidi.interp.starEdge
import org.opentaint.dataflow.bidi.store.*
import kotlin.test.*

class Run1RequestMatchingTest {
    @Test fun `actual requested marks produce all exact and STAR alternatives never ANY`() {
        val f = StoreFixtures(); val m = f.manager
        val cls = m.accessors.index(ClassStaticAccessor("Box")); val field = f.field("f")
        val t = f.mark("T"); val u = f.mark("U"); val absent = f.mark("absent")
        val slot = Run1Slot.StaticField(cls,field)
        val marks = m.markSetOf(t) + u
        val leaves = m.taintLeaves(marks,m.markSetOf(t))
        val input = m.taintTree(STATIC,Layer.NORMAL,
            m.taintAlg.prepend(m,m.path(slot.pathData),m.taintAlg.leafNode(leaves)))
        val answers = linkedSetOf<InitialAp>()
        forEachConcreteAnswer(m,input,listOf(RequestedRow(slot,RequestRowView(
            RequestMarkSet.fromIds(intArrayOf(t.id,u.id,absent.id)),RequestMarkSet.EMPTY)))) { answers += it }
        assertEquals(setOf(Tail.EXACT to t,Tail.EXACT to u,Tail.STAR to t), answers.map {
            it.tail to (it.mark as MarkSlot.Concrete).mark }.toSet())
        assertTrue(answers.all { it.path == m.path(slot.pathData) && it.exclusion == ExclusionSet.Empty })
        answers.forEach { validateRun1Initial(it,1) }
    }

    @Test fun `request geometry ignores E and mx while ordinary projection retains E headers`() {
        val f = StoreFixtures(); val m = f.manager
        val cls = m.accessors.index(ClassStaticAccessor("Box")); val field = f.field("f"); val g = f.field("g")
        val t = f.mark("T"); val u = f.mark("U"); val slot = Run1Slot.StaticField(cls,field)
        val flow = f.fact(STATIC,tail=Tail.STAR,mark=MarkSlot.Star(m.markSetOf(t)),
            exclusion=ExclusionSet.of(cls))
        assertTrue(slotMatches(slot,flow))
        assertTrue(staticFieldInputs(m,flow,slot).isEmpty(),"Ordinary projection still honors E")
        val exactField = m.taintAlg.prepend(m,m.path(slot.pathData),m.taintAlg.leafNode(m.taintLeaves(MarkSet.EMPTY,m.markSetOf(u))))
        val root = m.taintAlg.withLeaf(exactField,m.taintLeaves(MarkSet.EMPTY,m.markSetOf(t)))!!
        val mixed = m.taintTree(STATIC,Layer.NORMAL,root,ExclusionSet.of(g))
        val parts = staticFieldInputs(m,mixed,slot)
        assertEquals(2,parts.size)
        assertEquals(setOf(ExclusionSet.Empty,ExclusionSet.of(g)),parts.map { (it as TaintTree).exclusion }.toSet())
    }

    @Test fun `owned broad class point starts every actual mark independently of named needs`() {
        val f = StoreFixtures(); val m = f.manager
        val cls = m.accessors.index(ClassStaticAccessor("Box")); val t = f.mark("T"); val u = f.mark("U")
        val need = StaticMarkInterest(f.pattern(STATIC,listOf(cls),Tail.EXACT,MarkSlot.Concrete(t)),
            StaticMarkRole.INPUT_GATE,RequestMarkSet.of(t))
        val profile = Run1StaticProfiles.build(m,emptyMap(),mapOf(f.commonMethod to
            Run1StaticLocalProfile(marks=listOf(need))))[f.commonMethod]
        assertTrue(t in profile.classPointMarks(cls)); assertFalse(u in profile.classPointMarks(cls))
        val supplied = m.markSetOf(t) + u
        for (path in listOf(emptyList(),listOf(cls))) {
            val tree = m.taintAlg.prepend(m,m.path(path),
                m.taintAlg.leafNode(m.taintLeaves(MarkSet.EMPTY,supplied)))
            val answers = linkedSetOf<InitialAp>()
            forEachDirectClassInitial(m,m.taintTree(STATIC,Layer.NORMAL,tree),profile) { answers += it }
            assertEquals(setOf(t,u),answers.map { (it.mark as MarkSlot.Concrete).mark }.toSet(),
                "Owning point C for a T operation must not discard unrequested U")
            assertTrue(answers.all { it.path == m.path(listOf(cls)) && it.tail == Tail.STAR })
            assertTrue(answers.map(f.ops::startFact).all { it is TaintTree && it.layer == Layer.DEMAND && it.root.hasAny })
        }
        val noNeeds = Run1StaticProfiles.build(m,emptyMap(),mapOf(f.commonMethod to
            Run1StaticLocalProfile(classPoints=setOf(cls))))[f.commonMethod]
        val input = f.fact(STATIC,listOf(cls),Tail.ANY,MarkSlot.Concrete(u))
        val answers = arrayListOf<InitialAp>()
        forEachDirectClassInitial(m,input,noNeeds,answers::add)
        assertEquals(listOf(u),answers.map { (it.mark as MarkSlot.Concrete).mark })
        assertTrue(noNeeds.precomputedRequests.isEmpty,"Class point ownership creates no request key")
    }

    @Test fun `DEMAND dependencies retain their real field origin and never use a root origin`() {
        val f = StoreFixtures(); val m = f.manager
        val cls = m.accessors.index(ClassStaticAccessor("Box")); val field = f.field("f")
        val root = FlowAlgebra.prepend(m,m.path(cls,null),FlowAlgebra.LEAF_NODE)
        val broad = m.flowTree(STATIC,Layer.DEMAND,ExclusionSet.Empty,MarkSet.EMPTY,root)
        validateRun1Input(m,broad) // accepted geometry alone is weaker than frontend reachability
        val special = m.initial(STATIC,null,Tail.STAR,ExclusionSet.Empty,MarkSlot.STAR)
        assertFailsWith<IllegalStateException> { validateRun1Supplier(m,special,broad) }
        assertFailsWith<IllegalStateException> {
            validateRun1Supplier(m,special,m.flowTree(STATIC,Layer.NORMAL,ExclusionSet.Empty,MarkSet.EMPTY,root))
        }
        val origin = Run1Slot.StaticField(cls, field).initial(m)
        validateRun1Supplier(m,origin,broad)
        validateRun1Supplier(m,origin,m.flowTree(STATIC,Layer.NORMAL,ExclusionSet.Empty,MarkSet.EMPTY,root))
        val cannotMask = m.flowTree(STATIC,Layer.DEMAND,ExclusionSet.of(field),MarkSet.EMPTY,root)
        assertEquals(ExclusionSet.Empty,cannotMask.exclusion,
            "DEMAND FLOW canonicalization discards E instead of encoding a point-only mask")
        assertFailsWith<IllegalStateException> { staticUntouchedInputs(m,cannotMask,
            Run1StaticFootprint(setOf(Run1Slot.StaticField(cls,field)))) }
        assertTrue(slotMatches(Run1Slot.StaticField(cls,field),broad),
            "Raw class ANY includes the owned field, so it is not an untouched-frame fallback")
    }

    @Test fun `a changed static conclusion routes through its original nonstatic slot`() {
        val f = StoreFixtures(); val m = f.manager
        val cls = m.accessors.index(ClassStaticAccessor("Box")); val b = AccessPathBase.Argument(0)
        val initial = m.initial(b,null,Tail.STAR,ExclusionSet.Empty,MarkSlot.STAR)
        val out = CollectingOut()
        f.ops.applyEdge(f.ops.startFact(initial),initial,starEdge(b,emptyList(),STATIC,listOf(cls)),
            false,ApMode(true,Direction.FORWARD,1),out)
        val input = out.results.single()
        validateRun1Input(m,input) // geometry alone does not prove JVM producer eligibility
        validateRun1Supplier(m,initial,input)
        assertNotNull(normalRun1Slot(initial))
        assertTrue(RequestStorage().borrowMatchingRows(input,{}).isEmpty())
        assertTrue(out.markRequests.isEmpty())
        // Public JVM pass rules reject this class endpoint; no class request slot is invented.
    }

    @Test fun `restricted abstract ANY demand emission starts NORMAL FLOW through STAR canonicalization`() {
        val f = StoreFixtures(); val m = f.manager
        val cls = m.accessors.index(ClassStaticAccessor("Box")); val field = f.field("f")
        for (path in listOf(emptyList(),listOf(cls),listOf(cls,field))) {
            val demand = DemandPattern(f.pattern(STATIC,path,Tail.ANY,MarkSlot.STAR),null)
            val supplied = f.fact(STATIC,path,Tail.STAR,MarkSlot.STAR)
            val initial = f.ops.emit(demand,supplied).single()
            assertEquals(Tail.STAR,initial.tail)
            assertEquals(ExclusionSet.Empty,initial.exclusion)
            assertEquals(Layer.NORMAL,assertIs<FlowTree>(f.ops.startFact(initial)).layer)
        }
        // The generic ANY factory remains wider than the selected emit route.
        val generic = m.initial(STATIC,null,Tail.ANY,ExclusionSet.Empty,MarkSlot.STAR)
        assertEquals(Layer.DEMAND,assertIs<FlowTree>(f.ops.startFact(generic)).layer)
    }
}
