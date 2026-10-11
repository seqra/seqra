package org.opentaint.dataflow.jvm.bidi

import org.opentaint.dataflow.ap.ifds.AccessPathBase
import org.opentaint.dataflow.ap.ifds.FieldAccessor
import org.opentaint.dataflow.bidi.ap.*
import org.opentaint.dataflow.bidi.interp.*
import org.opentaint.dataflow.bidi.store.RequestKind
import org.opentaint.dataflow.jvm.BasicTestUtils
import org.opentaint.dataflow.jvm.bidi.fixtures.JIRFormsTestFixtures
import org.opentaint.dataflow.jvm.bidi.interp.JIRStatementForms
import org.opentaint.ir.api.common.cfg.CommonInst
import org.opentaint.ir.api.jvm.cfg.*
import org.opentaint.jvm.graph.JMethodEnterInst
import org.opentaint.jvm.graph.JMethodExitExceptionalInst
import org.opentaint.jvm.graph.JMethodExitNormalInst
import kotlin.test.*

/** Interpreter §2 effects use actual JIR values, with expected facts independent of the lowering. */
class JIRStatementEffectTest : BasicTestUtils() {
    private val fixture by lazy { JIRFormsTestFixtures(cp) }
    private val m get() = fixture.manager
    private val ops get() = fixture.ops
    private val t get() = m.marks.mark("T")
    private val u get() = m.marks.mark("U")
    private val primitive get() = m.marks.mark("T%%primitive%%")
    private val mt get() = MarkSlot.Concrete(t)
    private val mu get() = MarkSlot.Concrete(u)
    private val mp get() = MarkSlot.Concrete(primitive)
    private val mx get() = MarkSlot.Star(MarkSet(intArrayOf(t.id)))
    private val f get() = fixture.field("f")
    private val g get() = fixture.field("g")
    private val next get() = fixture.field("next", JIRFormsTestFixtures.SAMPLE_CLASS)
    private val element get() = fixture.element
    private fun p(vararg ids: Int) = ids.toList()
    private fun ex(vararg ids: Int) = ExclusionSet.of(ids)
    private fun base(v: JIRValue) = fixture.base(v)
    private fun c(base: AccessPathBase, path: List<Int> = emptyList(), tail: Tail = Tail.EXACT,
                  mark: MarkSlot = mt, exclusion: ExclusionSet = ExclusionSet.Empty,
                  demand: Boolean = tail == Tail.ANY) = Conclusion(PathFact(base, path, tail, mark), exclusion, demand)
    private fun flow(base: AccessPathBase, path: List<Int> = emptyList(),
                     exclusion: ExclusionSet = ExclusionSet.Empty, mark: MarkSlot = MarkSlot.STAR) =
        c(base, path, Tail.STAR, mark, exclusion)
    private fun must(base: AccessPathBase, path: List<Int> = emptyList(),
                     exclusion: ExclusionSet = ExclusionSet.Empty, mark: MarkSlot = mt) =
        c(base, path, Tail.ANY_TAINT, mark, exclusion)
    private fun immediateCopy(name: String) = fixture.assigns(name).last { it.rhv is JIRImmediate && it.rhv !is JIRConstant }
    private fun fieldRead(name: String = "fieldRead") = fixture.assign(name) { it.rhv is JIRFieldRef }
    private fun fieldWrite(name: String = "fieldWrite") = fixture.assign(name) { it.lhv is JIRFieldRef }
    private fun arrayRead(name: String = "arrayRead") = fixture.assign(name) { it.rhv is JIRArrayAccess }
    private fun arrayWrite(name: String = "arrayWrite") = fixture.assign(name) { it.lhv is JIRArrayAccess }
    private fun field(ref: JIRFieldRef) = m.accessors.index(FieldAccessor(
        ref.field.field.enclosingClass.name, ref.field.name, ref.field.type.typeName))

    @Test fun `object copy preserves source and complete suffix mark layer and exclusion`(): Unit {
        val inst = immediateCopy("copy")
        val x = base(inst.lhv)
        val y = base(inst.rhv as JIRValue)
        assertNotEquals(x, y)
        val inputs = listOf(c(y, p(f, g)), c(y, p(f), mark = mu, demand = true),
            flow(y, p(f), ex(g), mx), must(y, p(f), ex(g), mu),
            c(y, p(f), Tail.ANY, mu), c(y, p(f), Tail.ANY, mx))
        val self = JIRAssignInst(inst.location, inst.rhv as JIRValue, inst.rhv)
        for (input in inputs) {
            assertExpected(inst, input, setOf(input,
                c(x, input.fact.path, input.fact.tail, input.fact.mark, input.exclusion, input.demand)))
            assertExpected(self, input, setOf(input))
        }
        assertExpected(inst, c(x, p(f)), emptySet())
    }

    @Test fun `constant and null assignments copy their own constant base and kill old target`(): Unit {
        for (name in listOf("constant", "nullConstant")) {
            val inst = fixture.assign(name) { it.rhv is JIRConstant }
            val x = base(inst.lhv)
            val source = base(inst.rhv as JIRConstant)
            assertIs<AccessPathBase.Constant>(source)
            for (input in listOf(c(source), must(source, exclusion = ex(g), mark = mu)))
                assertExpected(inst, input, setOf(input, c(x, input.fact.path, input.fact.tail,
                    input.fact.mark, input.exclusion, input.demand)))
            assertExpected(inst, c(x), emptySet())
            assertExpected(inst, c(AccessPathBase.Argument(7), p(f)), setOf(c(AccessPathBase.Argument(7), p(f))))
        }
    }

    @Test fun `cast operand filter removes rejected source as well as target and retains admitted String geometry`(): Unit {
        val inst = fixture.assign("cast") { it.rhv is JIRCastExpr }
        val cast = inst.rhv as JIRCastExpr
        val source = base(cast.operand)
        val target = base(inst.lhv)
        assertExpected(inst, c(source), setOf(c(source), c(target)))
        assertExpected(inst, c(source, p(f)), emptySet())
        assertExpected(inst, c(source, p(element)), emptySet())
        val stringField = fixture.field("value", "byte[]", "java.lang.String")
        val admitted = must(source, p(stringField), ex(g), mu)
        assertExpected(inst, admitted, setOf(admitted, must(target, p(stringField), ex(g), mu)))
        assertExpected(inst, c(target), emptySet())
    }

    @Test fun `ordinary field read keeps original E and transfers admitted field suffixes`(): Unit {
        val inst = fieldRead()
        val ref = inst.rhv as JIRFieldRef
        val source = base(ref.instance!!)
        val target = base(inst.lhv)
        val field = field(ref)
        val abstract = flow(source, exclusion = ex(g), mark = mx)
        assertExpected(inst, abstract, setOf(abstract, c(target, tail = Tail.ANY, mark = mx)))
        val normal = must(source, exclusion = ex(g), mark = mu)
        assertExpected(inst, normal, setOf(normal, must(target, mark = mu)))
        val blocked = must(source, exclusion = ex(field), mark = mu)
        assertExpected(inst, blocked, setOf(blocked))
        val atField = flow(source, p(field), ex(g), mx)
        assertExpected(inst, atField, setOf(atField, flow(target, exclusion = ex(g), mark = mx)))
        val below = c(source, p(field, g), mark = mu, demand = true)
        assertExpected(inst, below, setOf(below, c(target, p(g), mark = mu, demand = true)))
        assertExpected(inst, c(target, p(g)), emptySet())
    }

    @Test fun `field operand rejects an unrelated declaring class before retaining the source`(): Unit {
        val inst = fieldRead()
        val source = base((inst.rhv as JIRFieldRef).instance!!)
        val unrelated = fixture.field("name", "java.lang.String", "java.lang.Thread")
        assertExpected(inst, c(source, p(unrelated)), emptySet())
        assertExpected(inst, must(source, p(unrelated), ex(g)), emptySet())
    }

    @Test fun `self field read has only selected field output and no refine only identity`(): Unit {
        val inst = fixture.selfRead()
        val source = base(inst.lhv)
        assertEquals(source, base((inst.rhv as JIRFieldRef).instance!!))
        assertExpected(inst, flow(source, exclusion = ex(g), mark = mx), setOf(c(source, tail = Tail.ANY, mark = mx)))
        assertExpected(inst, must(source, exclusion = ex(g)), setOf(must(source)))
        assertExpected(inst, must(source, exclusion = ex(next)), emptySet())
        assertExpected(inst, c(source, p(next, f)), setOf(c(source, p(f))))
        assertExpected(inst, c(source, p(g)), emptySet())
        assertExpected(inst, c(source), emptySet())
    }

    @Test fun `strong field write excludes only the written first step and regenerates complete value`(): Unit {
        val inst = fieldWrite()
        val ref = inst.lhv as JIRFieldRef
        val target = base(ref.instance!!)
        val value = base(inst.rhv as JIRValue)
        val field = field(ref)
        assertExpected(inst, flow(target, exclusion = ex(g), mark = mx),
            setOf(flow(target, exclusion = ex(field, g), mark = mx)))
        assertExpected(inst, must(target, exclusion = ex(g), mark = mu),
            setOf(must(target, exclusion = ex(field, g), mark = mu)))
        assertExpected(inst, c(target, p(g)), setOf(c(target, p(g))))
        assertExpected(inst, c(target, p(field, g)), emptySet())
        val input = must(value, p(g), ex(f), mu)
        assertExpected(inst, input, setOf(input, must(target, p(field, g), ex(f), mu)))
    }

    @Test fun `self field write copies prewrite content without an unrestricted base identity`(): Unit {
        val inst = fieldWrite("selfWrite")
        val source = base(inst.rhv as JIRValue)
        val field = field(inst.lhv as JIRFieldRef)
        assertEquals(source, base((inst.lhv as JIRFieldRef).instance!!))
        assertExpected(inst, c(source, p(field)), setOf(c(source, p(field, field))))
        val root = must(source, exclusion = ex(g), mark = mu)
        assertExpected(inst, root, setOf(must(source, exclusion = ex(field, g), mark = mu),
            must(source, p(field), ex(g), mu)))
        val sibling = c(source, p(g))
        assertExpected(inst, sibling, setOf(sibling, c(source, p(field, g))))
    }

    @Test fun `static read keeps static base and handles root identity request in run one`(): Unit {
        val inst = fieldRead("staticRead")
        val field = field(inst.rhv as JIRFieldRef)
        val target = base(inst.lhv)
        val stat = AccessPathBase.ClassStatic
        val abstract = flow(stat, exclusion = ex(g), mark = mx)
        assertExpected(inst, abstract, setOf(abstract, c(target, tail = Tail.ANY, mark = mx)))
        val identity = setOf(Pattern(PathFact(stat, emptyList(), Tail.STAR, MarkSlot.STAR), ExclusionSet.Empty))
        assertExpected(inst, abstract, setOf(abstract), run1 = true, inputPremise = identity,
            requests = listOf(RequestKind.Position(m.path(p(fixture.cls, field))!!)))
        val concrete = must(stat, exclusion = ex(g), mark = mu)
        assertExpected(inst, concrete, setOf(concrete, must(target, mark = mu)))
        val exact = c(stat, p(fixture.cls, field, g))
        assertExpected(inst, exact, setOf(exact, c(target, p(g))))
        val excluded = flow(stat, exclusion = ex(fixture.cls), mark = mx)
        assertExpected(inst, excluded, setOf(excluded), run1 = true, inputPremise = identity)
    }

    @Test fun `static write keeps two proper prefixes and cuts results without counting class`(): Unit {
        val inst = fieldWrite("staticWrite")
        val field = field(inst.lhv as JIRFieldRef)
        val value = base(inst.rhv as JIRValue)
        val stat = AccessPathBase.ClassStatic
        val cls = fixture.cls
        assertExpected(inst, must(stat), setOf(must(stat, exclusion = ex(cls)), must(stat, p(cls), ex(field))))
        assertExpected(inst, c(stat, p(cls, g)), setOf(c(stat, p(cls, g))))
        assertExpected(inst, c(stat, p(cls, field)), emptySet())
        val input = must(value, p(f, g), ex(next), mu)
        assertExpected(inst, input, setOf(input, must(stat, p(cls, field, f, g), ex(next), mu)))
        assertExpected(inst, input, setOf(c(value, p(f), Tail.ANY, mu), c(stat, p(cls, field), Tail.ANY, mu)), cut = 1)
        assertExpected(inst, input, setOf(input, c(stat, p(cls, field, f), Tail.ANY, mu)), cut = 2)
        assertExpected(inst, input, setOf(input, must(stat, p(cls, field, f, g), ex(next), mu)), cut = 3)
    }

    @Test fun `object array read adds no E while array write weakly preserves old element`(): Unit {
        val read = arrayRead()
        val array = base((read.rhv as JIRArrayAccess).array)
        val target = base(read.lhv)
        val old = flow(array, exclusion = ex(g), mark = mx)
        assertExpected(read, old, setOf(old, c(target, tail = Tail.ANY, mark = mx)))
        val atElement = must(array, p(element), ex(g), mu)
        assertExpected(read, atElement, setOf(atElement, must(target, exclusion = ex(g), mark = mu)))
        val blocked = must(array, exclusion = ex(element))
        assertExpected(read, blocked, setOf(blocked))
        val write = arrayWrite()
        val writtenArray = base((write.lhv as JIRArrayAccess).array)
        val value = base(write.rhv as JIRValue)
        val oldElement = c(writtenArray, p(element, f), mark = mu)
        assertExpected(write, oldElement, setOf(oldElement))
        val input = must(value, p(g), ex(f), mu)
        assertExpected(write, input, setOf(input, must(writtenArray, p(element, g), ex(f), mu)))
    }

    @Test fun `primitive and boxed copies keep only primitive tracking concrete marks`(): Unit {
        for (name in listOf("copyByte", "copyBoxed")) {
            val inst = immediateCopy(name)
            val source = base(inst.rhv as JIRValue)
            val target = base(inst.lhv)
            assertExpected(inst, c(source), emptySet())
            assertExpected(inst, must(source, mark = mu), emptySet())
            for (input in listOf(c(source, mark = mp), c(source, mark = mp, demand = true),
                must(source, exclusion = ex(g), mark = mp), c(source, tail = Tail.ANY, mark = mp)))
                assertExpected(inst, input, setOf(input, c(target, emptyList(), input.fact.tail,
                    input.fact.mark, input.exclusion, input.demand)))
            val abstract = flow(source, mark = mx)
            assertExpected(inst, abstract, setOf(abstract, flow(target, mark = mx)))
            assertExpected(inst, c(source, p(f), mark = mp), emptySet())
        }
    }

    @Test fun `byte array read runs array operand policy before primitive lhs policy`(): Unit {
        val inst = arrayRead("byteRead")
        val array = base((inst.rhv as JIRArrayAccess).array)
        val target = base(inst.lhv)
        // The array root admits T; only its generated primitive result loses T.
        val ordinaryRoot = must(array, exclusion = ex(g))
        assertExpected(inst, ordinaryRoot, setOf(ordinaryRoot))
        // At the element, the operand policy removes both the source and its copied result.
        assertExpected(inst, c(array, p(element)), emptySet())
        val trackedRoot = must(array, exclusion = ex(g), mark = mp)
        assertExpected(inst, trackedRoot, setOf(trackedRoot, must(target, mark = mp)))
        val trackedElement = c(array, p(element), mark = mp)
        assertExpected(inst, trackedElement, setOf(trackedElement, c(target, mark = mp)))
        val abstract = flow(array, mark = mx)
        assertExpected(inst, abstract, setOf(abstract, c(target, tail = Tail.ANY, mark = mx)))
        assertExpected(inst, c(target, mark = mp), emptySet())
    }

    @Test fun `byte array weak write filters primitive value and old element but retains array root`(): Unit {
        val inst = arrayWrite("byteWrite")
        val array = base((inst.lhv as JIRArrayAccess).array)
        val value = base(inst.rhv as JIRValue)
        val oldRoot = must(array, exclusion = ex(g))
        assertExpected(inst, oldRoot, setOf(oldRoot))
        assertExpected(inst, c(array, p(element)), emptySet())
        assertExpected(inst, c(value), emptySet())
        val tracked = must(value, exclusion = ex(g), mark = mp)
        assertExpected(inst, tracked, setOf(tracked, must(array, p(element), ex(g), mp)))
        val oldTracked = c(array, p(element), mark = mp)
        assertExpected(inst, oldTracked, setOf(oldTracked))
    }

    @Test fun `binary operators merge both admitted operands and self binary keeps its original once`(): Unit {
        val inst = fixture.assign("binary") { it.rhv is JIRBinaryExpr }
        val expr = inst.rhv as JIRBinaryExpr
        val left = base(expr.lhv)
        val right = base(expr.rhv)
        val target = base(inst.lhv)
        assertExpected(inst, c(left, mark = mp), setOf(c(left, mark = mp), c(target, mark = mp)))
        assertExpected(inst, c(right, mark = mp), setOf(c(right, mark = mp), c(target, mark = mp)))
        assertExpected(inst, c(left), emptySet())
        assertExpected(inst, c(target, mark = mp), emptySet())
        val self = JIRAssignInst(inst.location, expr.lhv, expr)
        assertExpected(self, c(left, mark = mp), setOf(c(left, mark = mp)))
        assertExpected(self, c(right, mark = mp), setOf(c(right, mark = mp), c(left, mark = mp)))
    }

    @Test fun `allocation newarray instanceof negation length and phi kill only their lhs`(): Unit {
        val rows: List<Pair<String, (JIRExpr) -> Boolean>> = listOf(
            "newObject" to { expr: JIRExpr -> expr is JIRNewExpr },
            "newArray" to { expr: JIRExpr -> expr is JIRNewArrayExpr },
            "newMultiArray" to { expr: JIRExpr -> expr is JIRNewArrayExpr },
            "instanceOf" to { expr: JIRExpr -> expr is JIRInstanceOfExpr },
            "negate" to { expr: JIRExpr -> expr is JIRNegExpr },
            "arrayLength" to { expr: JIRExpr -> expr is JIRLengthExpr },
        )
        for ((method, matches) in rows) {
            val inst = fixture.assign(method) { matches(it.rhv) }
            assertExpected(inst, c(base(inst.lhv), mark = mp), emptySet())
            for (operand in inst.rhv.operands) {
                val source = fixture.base(operand)
                if (source != base(inst.lhv)) {
                    val input = c(source, mark = mp)
                    assertExpected(inst, input, setOf(input))
                }
            }
        }
        val values = fixture.assigns("phi").mapNotNull { it.rhv as? JIRImmediate }
            .filter { it.type.typeName == "java.lang.Object" }.distinct()
        assertTrue(values.size >= 2)
        val location = fixture.assigns("phi").first().location
        val phi = JIRAssignInst(location, JIRLocalVar(901, "phiResult", values.first().type),
            JIRPhiExpr(values.first().type, values, emptyList()))
        assertExpected(phi, c(base(phi.lhv)), emptySet())
        for (value in values) {
            val input = must(base(value), exclusion = ex(g), mark = mu)
            assertExpected(phi, input, setOf(input))
        }
    }

    @Test fun `return and throw copy to separate virtual bases without operand type filtering`(): Unit {
        val ret = fixture.insts("copyByte").filterIsInstance<JIRReturnInst>().single()
        val value = base(ret.returnValue!!)
        val ordinaryPrimitive = must(value, exclusion = ex(g))
        assertExpected(ret, ordinaryPrimitive, setOf(ordinaryPrimitive, must(AccessPathBase.Return, exclusion = ex(g))))
        assertExpected(ret, c(AccessPathBase.Return), emptySet())
        val void = fixture.insts("voidReturn").filterIsInstance<JIRReturnInst>().single()
        assertExpected(void, c(AccessPathBase.Return), emptySet())
        val other = c(AccessPathBase.Argument(5))
        assertExpected(void, other, setOf(other))
        val throwing = fixture.insts("throwValue").filterIsInstance<JIRThrowInst>().single()
        val throwable = base(throwing.throwable)
        val input = c(throwable, p(f), mark = mu, demand = true)
        assertExpected(throwing, input, setOf(input, c(AccessPathBase.Exception, p(f), mark = mu, demand = true)))
        assertExpected(throwing, c(AccessPathBase.Exception), emptySet())
    }

    @Test fun `branch goto monitor catch and boundary statements preserve untouched facts without a cut`(): Unit {
        val statements = (fixture.insts("phi") + fixture.insts("unchanged")).filter {
            it !is JIRAssignInst && it !is JIRCallInst && it !is JIRReturnInst && it !is JIRThrowInst
        }
        assertTrue(statements.any { it is JIRIfInst })
        assertTrue(statements.any { it is JIRGotoInst })
        assertTrue(statements.any { it is JIREnterMonitorInst })
        assertTrue(statements.any { it is JIRExitMonitorInst })
        assertTrue(statements.any { it is JIRCatchInst })
        val input = must(AccessPathBase.Argument(0), p(f, g, next), ex(f), mu)
        val location = statements.first().location
        for (inst in statements + listOf(JMethodEnterInst(location), JMethodExitNormalInst(location),
            JMethodExitExceptionalInst(location))) assertExpected(inst, input, setOf(input), cut = 1)
    }

    @Test fun `lowered field to field transfer keeps the read base then writes through the real temporary`(): Unit {
        val read = fixture.assign("fieldToField") { it.rhv is JIRFieldRef }
        val write = fixture.assign("fieldToField") { it.lhv is JIRFieldRef }
        val sourceRef = read.rhv as JIRFieldRef
        val targetRef = write.lhv as JIRFieldRef
        val source = base(sourceRef.instance!!)
        val temporary = base(read.lhv)
        val target = base(targetRef.instance!!)
        assertEquals(temporary, base(write.rhv as JIRValue))
        val input = must(source, p(field(sourceRef), next), ex(g), mu)
        val temporaryFact = must(temporary, p(next), ex(g), mu)
        val written = must(target, p(field(targetRef), next), ex(g), mu)
        assertExpected(read, input, setOf(input, temporaryFact))
        assertExpected(write, temporaryFact, setOf(temporaryFact, written))
        assertExpected(write, input, setOf(input))
        assertExpected(write, c(target, p(field(targetRef), next)), emptySet())
    }

    @Test fun `local write aliases retain old alias facts and full generated paths`(): Unit {
        val original = fieldWrite()
        val ref = original.lhv as JIRFieldRef
        val local = JIRLocalVar(900, "writtenLocal", ref.instance!!.type)
        val inst = JIRAssignInst(original.location, JIRFieldRef(local, ref.field), original.rhv)
        val written = base(local)
        val value = base(inst.rhv as JIRValue)
        val alias = AccessPathBase.This
        val field = field(ref)
        val queries = ArrayList<Pair<JIRInst, AccessPathBase>>()
        val forms = fixture.forms(aliasPaths = { at, b ->
            queries += at to b
            listOf(alias to p(next, g, next))
        })
        val summary = forms.build(inst)
        assertEquals(listOf<Pair<JIRInst, AccessPathBase>>(inst to written), queries)
        assertFalse(alias in summary.touched)
        val oldAlias = must(alias, p(next, g, next, field), ex(g), mu)
        assertExpected(inst, oldAlias, setOf(oldAlias), forms = forms)
        val input = must(value, p(g), ex(next), mu)
        assertExpected(inst, input, setOf(input, must(written, p(field, g), ex(next), mu),
            must(alias, p(next, g, next, field, g), ex(next), mu)), forms = forms)
        assertExpected(inst, input, setOf(input, c(written, p(field), Tail.ANY, mu),
            c(alias, p(next), Tail.ANY, mu)), forms = forms, cut = 1)
        assertExpected(inst, input, setOf(input, must(written, p(field, g), ex(next), mu),
            c(alias, p(next, g), Tail.ANY, mu)), forms = forms, cut = 2)
        assertExpected(inst, input, setOf(input, must(written, p(field, g), ex(next), mu),
            c(alias, p(next, g, next), Tail.ANY, mu)), forms = forms, cut = 3)
    }

    @Test fun `positive field limits cut only generated touched outputs at one two and three`(): Unit {
        val inst = fieldWrite()
        val target = base((inst.lhv as JIRFieldRef).instance!!)
        val value = base(inst.rhv as JIRValue)
        val field = field(inst.lhv as JIRFieldRef)
        val input = must(value, p(f, g), ex(next), mu)
        assertExpected(inst, input, setOf(c(value, p(f), Tail.ANY, mu), c(target, p(field), Tail.ANY, mu)), cut = 1)
        assertExpected(inst, input, setOf(input, c(target, p(field, f), Tail.ANY, mu)), cut = 2)
        assertExpected(inst, input, setOf(input, must(target, p(field, f, g), ex(next), mu)), cut = 3)
    }

    private fun assertExpected(inst: JIRInst, input: Conclusion, expected: Set<Conclusion>,
                               forms: JIRStatementForms = fixture.forms(), cut: Int? = null,
                               run1: Boolean = false,
                               inputPremise: Set<Pattern> = setOf(Pattern(
                                   PathFact(AccessPathBase.Argument(7), emptyList(), Tail.EXACT, mt), ExclusionSet.Empty)),
                               requests: List<RequestKind> = emptyList()) {
        val summary = forms.build(inst)
        val mode = ApMode(run1, Direction.FORWARD, cut ?: Int.MAX_VALUE)
        val at = Place(inst, statementEdge = true, sources = true)
        val rawRequests = ArrayList<RequestKind>()
        val oracle = FormsReference(ops, ReferenceAlgebra(mode, request = { _, r -> rawRequests += r }, manager = m))
        val raw = LinkedHashSet<Conclusion>()
        oracle.applier.statement(summary, inputPremise, input, at,
            sink = { _, result -> raw += if (cut == null) result else limit(result, cut) },
            untouched = { _, result -> raw += result })
        assertEquals(expected, raw, "reference instruction=$inst input=$input")
        assertEquals(requests, rawRequests, "reference requests for $inst")

        val treeRequests = ArrayList<RequestKind>()
        val output = ArrayList<Facts>()
        val applier = FormApplier(object : FactAlgebra<Set<Pattern>, Facts> {
            override fun base(c: Facts) = c.base
            override fun filter(c: Facts, filter: TypeFilter) = ops.filter(c, filter)
            override fun applyEdge(me: MicroEdge, c: Facts, premise: Set<Pattern>, statementEdge: Boolean,
                                   out: (Set<Pattern>, Facts) -> Unit) {
                ops.applyEdge(c, m.premiseOf(premise.map(m::initial)), me.edge, statementEdge, mode,
                    object : ApOut {
                        override fun result(f: Facts) = out(premise, f)
                        override fun markRequest(mark: TaintMark) { treeRequests += RequestKind.Mark(mark) }
                        override fun positionRequest(position: PathNode) { treeRequests += RequestKind.Position(position) }
                    }, me.may)
            }
            override fun conjunction(cj: ConjunctiveEdge, premise: Set<Pattern>, c: Facts, node: CommonInst,
                                     out: (Set<Pattern>, Facts) -> Unit) = error("No conjunction in these vectors")
            override fun allowsSource(node: CommonInst, me: MicroEdge) = true
            override fun sourceHit(node: CommonInst, me: MicroEdge) = Unit
        })
        val facts = m.factsOf(input.fact, input.exclusion, if (input.demand) Layer.DEMAND else Layer.NORMAL)
        applier.statement(summary, inputPremise, facts, at,
            sink = { _, result ->
                if (cut == null) output += result else ops.limit(result, cut, object : ApOut {
                    override fun result(f: Facts) { output += f }
                    override fun markRequest(mark: TaintMark) = error("A field cut cannot request a mark")
                    override fun positionRequest(position: PathNode) = error("A field cut cannot request a position")
                })
            }, untouched = { _, result -> output += result })
        assertEquals(expected, output.flatMap(oracle::conclusions).toSet(), "tree instruction=$inst input=$input")
        assertEquals(requests, treeRequests, "tree requests for $inst")
    }
}
