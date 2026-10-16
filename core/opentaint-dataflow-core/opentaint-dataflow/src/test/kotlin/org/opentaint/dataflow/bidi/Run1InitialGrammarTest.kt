package org.opentaint.dataflow.bidi

import org.opentaint.dataflow.ap.ifds.AccessPathBase
import org.opentaint.dataflow.ap.ifds.ClassStaticAccessor
import org.opentaint.dataflow.bidi.ap.*
import org.opentaint.dataflow.bidi.engine.*
import org.opentaint.dataflow.bidi.fixtures.StoreFixtures
import kotlin.test.*

class Run1InitialGrammarTest {
    @Test fun `nine initial families have the selected starting kind and layer`() {
        val f = StoreFixtures(); val m = f.manager
        val cls = m.accessors.index(ClassStaticAccessor("Box")); val field = f.field("f")
        val t = f.mark("T"); val b = AccessPathBase.Argument(0)
        fun initial(base: AccessPathBase, path: List<Int>, tail: Tail, mark: MarkSlot) =
            m.initial(base, m.path(path), tail, ExclusionSet.Empty, mark)
        val abstracts = listOf(initial(b, emptyList(), Tail.STAR, MarkSlot.STAR),
            initial(STATIC, listOf(cls,field), Tail.STAR, MarkSlot.STAR))
        val exacts = listOf(b to emptyList(), STATIC to listOf(cls,field), STATIC to listOf(cls)).map {
            initial(it.first,it.second,Tail.EXACT,MarkSlot.Concrete(t)) }
        val chains = exacts.map { m.initial(it.base,it.path,Tail.STAR,ExclusionSet.Empty,it.mark) }
        val all = listOf(m.zero) + abstracts + exacts + chains
        assertEquals(9, all.size)
        for (i in all) validateRun1Initial(i, 1)
        assertTrue(f.ops.startFact(m.zero) is Reach)
        for (i in abstracts) assertEquals(Layer.NORMAL, (f.ops.startFact(i) as FlowTree).layer)
        for (i in exacts) {
            val start = f.ops.startFact(i) as TaintTree
            assertEquals(Layer.NORMAL,start.layer); assertFalse(start.root.hasAny)
        }
        for (i in chains) {
            val start = f.ops.startFact(i) as TaintTree
            assertEquals(Layer.DEMAND,start.layer); assertTrue(start.root.hasAny)
        }
        validateRun1Initial(initial(b, emptyList(), Tail.EXACT, MarkSlot.Concrete(TaintMark.ZERO)), 1)
    }

    @Test fun `ANY class abstract concrete root and deeper initials fail at admission`() {
        val f = StoreFixtures(); val m = f.manager
        val cls = m.accessors.index(ClassStaticAccessor("Box")); val field = f.field("f")
        val t = MarkSlot.Concrete(f.mark("T"))
        val cases = listOf(
            m.initial(STATIC,null,Tail.STAR,ExclusionSet.Empty,MarkSlot.STAR),
            m.initial(STATIC,m.path(listOf(cls,field)),Tail.ANY,ExclusionSet.Empty,t),
            m.initial(STATIC,m.path(listOf(cls)),Tail.STAR,ExclusionSet.Empty,MarkSlot.STAR),
            m.initial(STATIC,null,Tail.EXACT,ExclusionSet.Empty,t),
            m.initial(STATIC,null,Tail.STAR,ExclusionSet.Empty,t),
            m.initial(STATIC,m.path(listOf(cls,field,f.field("g"))),Tail.STAR,ExclusionSet.Empty,t),
            m.initial(AccessPathBase.Argument(0),m.path(listOf(field)),Tail.EXACT,ExclusionSet.Empty,t))
        for (i in cases) assertFailsWith<IllegalStateException> { validateRun1Initial(i,1) }
        assertFailsWith<IllegalStateException> { validateRun1Initial(m.zero,2) }
    }
}
