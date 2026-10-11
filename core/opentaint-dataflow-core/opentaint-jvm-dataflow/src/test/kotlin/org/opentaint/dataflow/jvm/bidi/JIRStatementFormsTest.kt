package org.opentaint.dataflow.jvm.bidi

import org.opentaint.dataflow.ap.ifds.AccessPathBase
import org.opentaint.dataflow.ap.ifds.FactTypeChecker
import org.opentaint.dataflow.ap.ifds.FieldAccessor
import org.opentaint.dataflow.bidi.ap.*
import org.opentaint.dataflow.bidi.interp.*
import org.opentaint.dataflow.jvm.BasicTestUtils
import org.opentaint.dataflow.jvm.bidi.fixtures.JIRFormsTestFixtures
import org.opentaint.ir.api.jvm.cfg.*
import org.opentaint.ir.api.jvm.ext.cfg.callExpr
import org.opentaint.jvm.graph.JMethodEnterInst
import org.opentaint.jvm.graph.JMethodExitExceptionalInst
import org.opentaint.jvm.graph.JMethodExitNormalInst
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFails
import kotlin.test.assertFalse
import kotlin.test.assertSame
import kotlin.test.assertTrue

/** Literal indexed edges and named JVM rows supply construction expectations, independent of the new builder. */
class JIRStatementFormsTest : BasicTestUtils() {
    private val v by lazy { JIRFormsTestFixtures(cp) }
    private val empty = ExclusionSet.Empty
    private fun side(base: AccessPathBase, path: List<Int> = emptyList()) = PathFact(base, path, Tail.STAR, MarkSlot.STAR)
    private fun keep(base: AccessPathBase, path: List<Int> = emptyList(), e: ExclusionSet = empty) =
        PathEdge(side(base, path), side(base, path), e)
    private fun move(from: AccessPathBase, to: AccessPathBase, fp: List<Int> = emptyList(), tp: List<Int> = emptyList()) =
        PathEdge(side(from, fp), side(to, tp), empty)
    private fun form(s: JIRInst): StatementSummary = v.forms().build(s)
    private fun base(value: JIRValue) = v.base(value)

    private fun check(s: StatementSummary, touched: Set<AccessPathBase>, edges: List<PathEdge>) {
        assertEquals(touched, s.touched)
        assertEquals(edges, s.edges.map { it.edge })
        assertTrue(s.conjunctions.isEmpty())
        for (me in s.edges) {
            assertSame(me.edge, me.forward)
            assertFalse(me.conjunctive)
            assertTrue(me.edge.from.base in touched)
            assertEquals(Tail.STAR, me.edge.from.tail)
            assertEquals(Tail.STAR, me.edge.to.tail)
            assertEquals(MarkSlot.STAR, me.edge.from.mark)
            assertEquals(MarkSlot.STAR, me.edge.to.mark)
            assertTrue(me.edge.exclusion == empty || me.edge.from == me.edge.to)
        }
    }

    private fun copy(method: String): JIRAssignInst = v.assigns(method).last { it.rhv is JIRImmediate }

    private fun fieldFilter(s: StatementSummary, base: AccessPathBase, ref: JIRFieldRef) {
        val accessor = FieldAccessor(ref.field.field.enclosingClass.name, ref.field.name, ref.field.type.typeName)
        val before = v.checker.localFactsTotal.sum()
        assertEquals(FactTypeChecker.FilterResult.Accept, s.typeFilters.getValue(base).may.check(accessor))
        assertEquals(2L, v.checker.localFactsTotal.sum() - before, "Root and declaring-class filters both run")
    }

    @Test
    fun `root copies constants and nulls place operand and lhs result filters on separate maps`(): Unit {
        for (name in listOf("copy", "constant", "nullConstant", "copyByte", "copyBoxed")) {
            val inst = copy(name)
            val from = inst.rhv as JIRImmediate
            val target = base(inst.lhv)
            val source = base(from)
            val s = form(inst)
            check(s, setOf(target, source), listOf(keep(source), move(source, target)))
            assertEquals(setOf(source), s.typeFilters.keys, name)
            assertEquals(setOf(target), s.resultFilters.keys, name)
            assertSame(v.typeFilters.of(from.type), s.typeFilters[source], name)
            assertSame(v.typeFilters.of(inst.lhv.type), s.resultFilters[target], name)
        }
    }

    @Test
    fun `self root copy has one keep and independent operand and result filters`(): Unit {
        val original = copy("copy")
        val value = original.rhv as JIRImmediate
        val inst = JIRAssignInst(original.location, value, value)
        val b = base(value)
        val s = form(inst)
        check(s, setOf(b), listOf(keep(b)))
        assertSame(v.typeFilters.of(value.type), s.typeFilters[b])
        assertSame(v.typeFilters.of(value.type), s.resultFilters[b])
    }

    @Test
    fun `cast copies the operand with the cast filter and filters its lhs only on results`(): Unit {
        val inst = v.assign("cast") { it.rhv is JIRCastExpr }
        val cast = inst.rhv as JIRCastExpr
        val source = base(cast.operand)
        val target = base(inst.lhv)
        val s = form(inst)
        check(s, setOf(source, target), listOf(keep(source), move(source, target)))
        assertSame(v.typeFilters.of(cast.type), s.typeFilters[source])
        assertSame(v.typeFilters.of(inst.lhv.type), s.resultFilters[target])
        assertEquals(setOf(source), s.typeFilters.keys)
        assertEquals(setOf(target), s.resultFilters.keys)
    }

    @Test
    fun `field read retains the whole source and has no refining edge or field exclusion`(): Unit {
        val inst = v.assign("fieldRead") { it.rhv is JIRFieldRef }
        val ref = inst.rhv as JIRFieldRef
        val source = base(ref.instance!!)
        val target = base(inst.lhv)
        val s = form(inst)
        check(s, setOf(source, target), listOf(keep(source), move(source, target, fp = listOf(v.field("f")))))
        assertTrue(s.edges.all { it.edge.exclusion == empty })
        assertEquals(setOf(source), s.typeFilters.keys)
        assertEquals(setOf(target), s.resultFilters.keys)
        fieldFilter(s, source, ref)
        assertSame(v.typeFilters.of(inst.lhv.type), s.resultFilters[target])
    }

    @Test
    fun `self field read has only the read transfer and separate input and lhs filters on its one base`(): Unit {
        val inst = v.selfRead()
        val ref = inst.rhv as JIRFieldRef
        val b = base(ref.instance!!)
        val s = form(inst)
        check(s, setOf(b), listOf(move(b, b, fp = listOf(v.field("next", JIRFormsTestFixtures.SAMPLE_CLASS)))))
        assertEquals(setOf(b), s.typeFilters.keys)
        assertEquals(setOf(b), s.resultFilters.keys)
        fieldFilter(s, b, ref)
        assertSame(v.typeFilters.of(inst.lhv.type), s.resultFilters[b])
    }

    @Test
    fun `inherited field reads combine the root type and the declaring class instead of filtering the lhs input`(): Unit {
        val inst = v.assign("inheritedRead") { it.rhv is JIRFieldRef }
        val ref = inst.rhv as JIRFieldRef
        val source = base(ref.instance!!)
        val target = base(inst.lhv)
        val owner = JIRFormsTestFixtures.SAMPLE_CLASS + "\$Parent"
        assertEquals(owner, ref.field.field.enclosingClass.name)
        assertEquals(JIRFormsTestFixtures.SAMPLE_CLASS + ".Child", ref.instance!!.type.typeName)
        val s = form(inst)
        check(s, setOf(source, target), listOf(keep(source), move(source, target, fp = listOf(v.field("inherited", owner = owner)))))
        fieldFilter(s, source, ref)
        assertFalse(target in s.typeFilters)
        assertSame(v.typeFilters.of(inst.lhv.type), s.resultFilters[target])
    }

    @Test
    fun `array reads keep the array and copy only the element with array operand and lhs result filters`(): Unit {
        for (method in listOf("arrayRead", "byteRead")) {
            val inst = v.assign(method) { it.rhv is JIRArrayAccess }
            val read = inst.rhv as JIRArrayAccess
            val source = base(read.array)
            val target = base(inst.lhv)
            val s = form(inst)
            check(s, setOf(source, target), listOf(keep(source), move(source, target, fp = listOf(v.element))))
            assertSame(v.typeFilters.of(read.array.type), s.typeFilters[source], method)
            assertSame(v.typeFilters.of(inst.lhv.type), s.resultFilters[target], method)
            assertEquals(setOf(source), s.typeFilters.keys)
            assertEquals(setOf(target), s.resultFilters.keys)
        }
    }

    @Test
    fun `static reads keep the entire static base and retain the full class field path without a static filter`(): Unit {
        val inst = v.assign("staticRead") { it.rhv is JIRFieldRef }
        val target = base(inst.lhv)
        val s = form(inst)
        check(s, setOf(STATIC, target), listOf(keep(STATIC), move(STATIC, target, fp = listOf(v.cls, v.field("sField")))))
        assertTrue(s.typeFilters.isEmpty())
        assertEquals(setOf(target), s.resultFilters.keys)
        assertSame(v.typeFilters.of(inst.lhv.type), s.resultFilters[target])
    }

    @Test
    fun `field writes keep only siblings and place source and receiver filters on inputs`(): Unit {
        val inst = v.assign("fieldWrite") { it.lhv is JIRFieldRef }
        val ref = inst.lhv as JIRFieldRef
        val receiver = base(ref.instance!!)
        val value = base(inst.rhv as JIRValue)
        val s = form(inst)
        check(s, setOf(receiver, value), listOf(keep(receiver, e = ExclusionSet.of(v.field("f"))), keep(value),
            move(value, receiver, tp = listOf(v.field("f")))))
        assertEquals(setOf(receiver, value), s.typeFilters.keys)
        assertTrue(s.resultFilters.isEmpty())
        fieldFilter(s, receiver, ref)
        assertSame(v.typeFilters.of((inst.rhv as JIRValue).type), s.typeFilters[value])
    }

    @Test
    fun `self field writes use one excluded keep and one value edge without a whole base identity`(): Unit {
        val inst = v.assign("selfWrite") { it.lhv is JIRFieldRef }
        val ref = inst.lhv as JIRFieldRef
        val receiver = base(ref.instance!!)
        assertEquals(receiver, base(inst.rhv as JIRValue))
        val s = form(inst)
        check(s, setOf(receiver), listOf(keep(receiver, e = ExclusionSet.of(v.field("f"))),
            move(receiver, receiver, tp = listOf(v.field("f")))))
        assertEquals(setOf(receiver), s.typeFilters.keys)
        assertTrue(s.resultFilters.isEmpty())
        val before = v.checker.localFactsTotal.sum()
        assertEquals(FactTypeChecker.FilterResult.Accept, s.typeFilters.getValue(receiver).may.check(
            FieldAccessor(JIRFormsTestFixtures.SAMPLE_CLASS, "f", "java.lang.Object")))
        assertEquals(3L, v.checker.localFactsTotal.sum() - before)
    }

    @Test
    fun `array writes are weak and keep old elements with separate value and array operand filters`(): Unit {
        for (method in listOf("arrayWrite", "byteWrite")) {
            val inst = v.assign(method) { it.lhv is JIRArrayAccess }
            val array = (inst.lhv as JIRArrayAccess).array
            val receiver = base(array)
            val value = base(inst.rhv as JIRValue)
            val s = form(inst)
            check(s, setOf(receiver, value), listOf(keep(receiver), keep(value), move(value, receiver, tp = listOf(v.element))))
            assertEquals(setOf(receiver, value), s.typeFilters.keys)
            assertSame(v.typeFilters.of(array.type), s.typeFilters[receiver])
            assertSame(v.typeFilters.of((inst.rhv as JIRValue).type), s.typeFilters[value])
            assertTrue(s.resultFilters.isEmpty())
        }
    }

    @Test
    fun `static writes use two class field prefix keeps and no static filter or alias callback`(): Unit {
        val inst = v.assign("staticWrite") { it.lhv is JIRFieldRef }
        val value = base(inst.rhv as JIRValue)
        val forms = v.forms(aliasPaths = { _, _ -> error("A4: a static write has no aliases") })
        val s = forms.build(inst)
        check(s, setOf(STATIC, value), listOf(keep(STATIC, e = ExclusionSet.of(v.cls)),
            keep(STATIC, listOf(v.cls), ExclusionSet.of(v.field("sField"))), keep(value),
            move(value, STATIC, tp = listOf(v.cls, v.field("sField")))))
        assertEquals(setOf(value), s.typeFilters.keys)
        assertFalse(STATIC in s.resultFilters)
        assertTrue(s.resultFilters.isEmpty())
    }

    @Test
    fun `binary statements copy both operands in order and self operands deduplicate to identity`(): Unit {
        val original = v.assign("binary") { it.rhv is JIRBinaryExpr }
        val binary = original.rhv as JIRBinaryExpr
        val target = base(original.lhv)
        val a = base(binary.lhv)
        val b = base(binary.rhv)
        val s = form(original)
        check(s, setOf(target, a, b), listOf(keep(a), move(a, target), keep(b), move(b, target)))
        assertEquals(setOf(a, b), s.typeFilters.keys)
        assertEquals(setOf(target), s.resultFilters.keys)
        val self = JIRAssignInst(original.location, binary.lhv, binary)
        check(form(self), setOf(a, b), listOf(keep(a), keep(b), move(b, a)))
    }

    @Test
    fun `new object arrays instanceof negation length and phi kill their lhs and do not read their operands`(): Unit {
        val rows = listOf(
            v.assign("newObject") { it.rhv is JIRNewExpr },
            v.assign("newArray") { it.rhv is JIRNewArrayExpr },
            v.assign("newMultiArray") { it.rhv is JIRNewArrayExpr },
            v.assign("instanceOf") { it.rhv is JIRInstanceOfExpr },
            v.assign("negate") { it.rhv is JIRNegExpr },
            v.assign("arrayLength") { it.rhv is JIRLengthExpr },
        )
        val original = copy("copy")
        val phi = JIRAssignInst(original.location, original.lhv,
            JIRPhiExpr(original.lhv.type, listOf(original.rhv as JIRValue), emptyList()))
        for (inst in rows + phi) {
            val target = base(inst.lhv)
            val s = form(inst)
            check(s, setOf(target), emptyList())
            assertTrue(s.typeFilters.isEmpty(), "$inst")
            assertEquals(setOf(target), s.resultFilters.keys)
            assertSame(v.typeFilters.of(inst.lhv.type), s.resultFilters[target])
        }
    }

    @Test
    fun `value returns throws and void returns use the stated copies or result kill without filters`(): Unit {
        val valueReturn = v.insts("copy").filterIsInstance<JIRReturnInst>().single()
        val value = base(valueReturn.returnValue!!)
        val ret = form(valueReturn)
        check(ret, setOf(AccessPathBase.Return, value), listOf(keep(value), move(value, AccessPathBase.Return)))
        val throwInst = v.insts("throwValue").filterIsInstance<JIRThrowInst>().single()
        val throwable = base(throwInst.throwable)
        val thrown = form(throwInst)
        check(thrown, setOf(AccessPathBase.Exception, throwable), listOf(keep(throwable), move(throwable, AccessPathBase.Exception)))
        val void = form(v.insts("voidReturn").filterIsInstance<JIRReturnInst>().single())
        check(void, setOf(AccessPathBase.Return), emptyList())
        for (s in listOf(ret, thrown, void)) {
            assertTrue(s.typeFilters.isEmpty())
            assertTrue(s.resultFilters.isEmpty())
        }
    }

    @Test
    fun `branch goto monitor catch and method boundary instructions return the shared empty form`(): Unit {
        val rows = (v.insts("unchanged") + v.insts("phi")).filter {
            it is JIRIfInst || it is JIRGotoInst || it is JIREnterMonitorInst || it is JIRExitMonitorInst || it is JIRCatchInst
        }
        assertTrue(rows.any { it is JIRIfInst })
        assertTrue(rows.any { it is JIRGotoInst })
        assertTrue(rows.any { it is JIREnterMonitorInst })
        assertTrue(rows.any { it is JIRExitMonitorInst })
        assertTrue(rows.any { it is JIRCatchInst })
        val location = rows.first().location
        for (inst in rows + listOf(JMethodEnterInst(location), JMethodExitNormalInst(location), JMethodExitExceptionalInst(location)))
            assertSame(StatementSummary.EMPTY, form(inst), "$inst")
    }

    @Test
    fun `call assignments and call instructions reject before any read source callback`(): Unit {
        val forms = v.forms(readSources = { _, _ -> error("A call must not run read-source lowering") })
        val assigned = v.insts("callAssign").first { it is JIRAssignInst && it.callExpr != null }
        val realCall = v.insts("callOnly").first { it.callExpr != null }
        val invoked = JIRCallInst(realCall.location, checkNotNull(realCall.callExpr))
        assertFails { forms.build(assigned) }
        assertFails { forms.build(invoked) }
    }

    @Test
    fun `local write aliases receive full paths and remain untouched while nonlocal writes and reads skip lookup`(): Unit {
        val original = v.assign("fieldWrite") { it.lhv is JIRFieldRef }
        val oldRef = original.lhv as JIRFieldRef
        val local = JIRLocalVar(900, "localReceiver", oldRef.instance!!.type)
        val inst = JIRAssignInst(original.location, oldRef.copy(instance = local), original.rhv)
        val receiver = base(local)
        val value = base(original.rhv as JIRValue)
        val q = List(32767) { if (it % 2 == 0) v.field("g") else v.element }
        val seen = ArrayList<Pair<JIRInst, AccessPathBase>>()
        val s = v.forms(aliasPaths = { statement, b -> seen += statement to b; listOf(AccessPathBase.This to q) }).build(inst)
        assertEquals(listOf<Pair<JIRInst, AccessPathBase>>(inst to receiver), seen)
        assertEquals(setOf(receiver, value), s.touched)
        val alias = s.edges.single { it.edge.to.base == AccessPathBase.This }.edge
        assertEquals(q + v.field("f"), alias.to.path)
        assertEquals(32768, alias.to.path.size)
        assertEquals(move(value, AccessPathBase.This, tp = q + v.field("f")), alias)
        assertFalse(AccessPathBase.This in s.touched)
        val notLocal = JIRArgument(99, "nonLocalReceiver", oldRef.instance!!.type)
        val argumentWrite = JIRAssignInst(original.location, oldRef.copy(instance = notLocal), original.rhv)
        val skip = v.forms(aliasPaths = { _, _ -> error("A1/A4: no local write means no aliases") })
        assertTrue(skip.build(argumentWrite).edges.none { it.edge.to.base == AccessPathBase.This })
        skip.build(v.assign("fieldRead") { it.rhv is JIRFieldRef })
        skip.build(v.assign("staticWrite") { it.lhv is JIRFieldRef })
    }

    @Test
    fun `read source callback runs after ordinary construction once and supplied source identity is retained`(): Unit {
        val inst = v.assign("fieldRead") { it.rhv is JIRFieldRef }
        val target = base(inst.lhv)
        val receiver = base((inst.rhv as JIRFieldRef).instance!!)
        val source = PathEdge(ZERO_FACT, PathFact(target, emptyList(), Tail.ANY_TAINT,
            MarkSlot.Concrete(v.manager.marks.mark("SOURCE"))), empty)
        var calls = 0
        val s = v.forms(readSources = { b, statement ->
            assertSame(inst, statement)
            assertEquals(listOf(keep(receiver), move(receiver, target, fp = listOf(v.field("f")))), b.build().edges.map { it.edge })
            calls++
            b.keepZero(); b.edge(source)
        }).build(inst)
        assertEquals(1, calls)
        assertEquals(setOf(target, receiver, AccessPathBase.Zero), s.touched)
        assertEquals(listOf(keep(receiver), move(receiver, target, fp = listOf(v.field("f"))), ZERO_KEEP, source), s.edges.map { it.edge })
        assertSame(source, s.edges.last().edge)
        assertSame(source, s.edges.last().forward)
        assertTrue(s.edges.last().isSource)
        assertSame(v.typeFilters.of(inst.lhv.type), s.resultFilters[target])
        val binary = v.assign("binary") { it.rhv is JIRBinaryExpr }
        var binaryCalls = 0
        v.forms(readSources = { _, _ -> binaryCalls++ }).build(binary)
        assertEquals(1, binaryCalls)
    }

    @Test
    fun `real field to field assignment lowers through a local while a direct complex assignment rejects`(): Unit {
        val read = v.assign("fieldToField") { it.rhv is JIRFieldRef }
        val write = v.assign("fieldToField") { it.lhv is JIRFieldRef }
        assertTrue(write.rhv is JIRImmediate)
        assertTrue(read.lhv is JIRImmediate)
        assertEquals(base(read.lhv), base(write.rhv as JIRValue))
        val readSource = base((read.rhv as JIRFieldRef).instance!!)
        val temporary = base(read.lhv)
        check(form(read), setOf(readSource, temporary), listOf(keep(readSource), move(readSource, temporary, fp = listOf(v.field("g")))))
        val receiver = base((write.lhv as JIRFieldRef).instance!!)
        check(form(write), setOf(receiver, temporary), listOf(keep(receiver, e = ExclusionSet.of(v.field("f"))), keep(temporary),
            move(temporary, receiver, tp = listOf(v.field("f")))))
        val invalid = JIRAssignInst(read.location, write.lhv, read.rhv)
        assertFails { form(invalid) }
    }
}
