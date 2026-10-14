package org.opentaint.dataflow.bidi

import org.opentaint.dataflow.ap.ifds.AccessPathBase
import org.opentaint.dataflow.ap.ifds.ClassStaticAccessor
import org.opentaint.dataflow.ap.ifds.access.util.AccessorInterner.Companion.ELEMENT_ACCESSOR_IDX
import org.opentaint.dataflow.bidi.ap.*
import org.opentaint.dataflow.bidi.fixtures.StoreFixtures
import org.opentaint.dataflow.bidi.interp.*
import org.opentaint.dataflow.bidi.ap.ReferenceRequestKind as RequestKind
import org.opentaint.ir.api.common.cfg.CommonInst
import kotlin.test.*

/** Expected conclusions come from interpreter §2, independently of the generated edge list. */
class BuilderTransferVectorsTest {
    private val fixture = StoreFixtures()
    private val m = fixture.manager
    private val x = AccessPathBase.LocalVar(0)
    private val y = AccessPathBase.LocalVar(1)
    private val z = AccessPathBase.LocalVar(2)
    private val a = AccessPathBase.LocalVar(3)
    private val f = fixture.field("f")
    private val g = fixture.field("g")
    private val h = fixture.field("h")
    private val cls = m.accessors.index(ClassStaticAccessor("Box"))
    private val otherClass = m.accessors.index(ClassStaticAccessor("Other"))
    private val t = fixture.mark("T")
    private val u = fixture.mark("U")
    private val mt = MarkSlot.Concrete(t)
    private val mu = MarkSlot.Concrete(u)
    private val mx = MarkSlot.Star(MarkSet(intArrayOf(t.id)))
    private val premise = setOf(fixture.pattern(AccessPathBase.Argument(0), mark = mt))
    private val at = Place(fixture.inst(1), statementEdge = true, sources = true)
    private fun p(vararg ids: Int) = ids.toList()
    private fun ex(vararg ids: Int) = ExclusionSet.of(ids)
    private fun c(base: AccessPathBase, path: List<Int> = emptyList(), tail: Tail = Tail.EXACT,
                  mark: MarkSlot = mt, exclusion: ExclusionSet = ExclusionSet.Empty,
                  demand: Boolean = tail == Tail.ANY) = Conclusion(PathFact(base, path, tail, mark), exclusion, demand)
    private fun flow(base: AccessPathBase, path: List<Int> = emptyList(),
                     exclusion: ExclusionSet = ExclusionSet.Empty, mark: MarkSlot = MarkSlot.STAR) =
        c(base, path, Tail.STAR, mark, exclusion)
    private fun must(base: AccessPathBase, path: List<Int> = emptyList(),
                     exclusion: ExclusionSet = ExclusionSet.Empty, mark: MarkSlot = mt) =
        c(base, path, Tail.ANY_TAINT, mark, exclusion)
    private fun write(base: AccessPathBase, path: List<Int>, weak: Boolean = false,
                      values: List<AccessPathBase> = listOf(x),
                      aliases: List<Pair<AccessPathBase, List<Int>>> = emptyList()) =
        MicroEdgeBuilder().apply { write(base, path, weak, values, aliases) }.build()

    @Test fun `moves retain source and replace target for every admitted mark tail and layer`(): Unit {
        val summary = MicroEdgeBuilder().apply { move(y, x, p(h)) }.build()
        val inputs = listOf(
            c(x, p(f, g)), c(x, p(f, g), mark = mu), c(x, p(f, g), demand = true),
            flow(x, p(f, g), ex(h)), flow(x, p(f, g), ex(h), mx),
            c(x, p(f, g), Tail.ANY), c(x, p(f, g), Tail.ANY, mu),
            c(x, p(f, g), Tail.ANY, MarkSlot.STAR), c(x, p(f, g), Tail.ANY, mx),
            must(x, p(f, g), ex(h)), must(x, p(f, g), ex(h), mu),
        )
        for (input in inputs) {
            val moved = c(y, p(h, f, g), input.fact.tail, input.fact.mark, input.exclusion, input.demand)
            assertExpected(summary, input, setOf(input, moved))
        }
        assertExpected(summary, c(y, p(g)), emptySet())
        val untouched = must(z, p(f), ex(g), mu)
        assertExpected(summary, untouched, setOf(untouched))
    }

    @Test fun `null move kills only its target and self move preserves the complete fact`(): Unit {
        val kill = MicroEdgeBuilder().apply { move(x, null) }.build()
        val self = MicroEdgeBuilder().apply { move(x, x) }.build()
        for (input in listOf(c(x), flow(x, p(f), ex(g), mx), must(x, exclusion = ex(g)),
            c(x, p(f), Tail.ANY, mu), c(x, demand = true))) {
            assertExpected(kill, input, emptySet())
            assertExpected(self, input, setOf(input))
        }
        val other = flow(z, exclusion = ex(f))
        assertExpected(kill, other, setOf(other))
        val rootCopy = MicroEdgeBuilder().apply { move(y, x) }.build()
        assertExpected(rootCopy, c(x, p(f)), setOf(c(x, p(f)), c(y, p(f))))
    }

    @Test fun `field read keeps the original flow without refining E and transfers at and below the field`(): Unit {
        val read = MicroEdgeBuilder().apply { read(x, y, p(f)) }.build()
        for (mark in listOf(MarkSlot.STAR, mx)) {
            val above = flow(y, exclusion = ex(g), mark = mark)
            assertExpected(read, above, setOf(above, c(x, tail = Tail.ANY, mark = mark)))
            val atField = flow(y, p(f), ex(g), mark)
            assertExpected(read, atField, setOf(atField, flow(x, exclusion = ex(g), mark = mark)))
            val below = flow(y, p(f, h), ex(g), mark)
            assertExpected(read, below, setOf(below, flow(x, p(h), ex(g), mark)))
        }
        val exact = c(y, p(f, h), mark = mu, demand = true)
        assertExpected(read, exact, setOf(exact, c(x, p(h), mark = mu, demand = true)))
        for (input in listOf(c(y), c(y, p(g)), flow(y, exclusion = ex(f))))
            assertExpected(read, input, setOf(input))
        assertExpected(read, c(x, p(g)), emptySet())
    }

    @Test fun `field read of normal must reads only the first relative accessor exclusion`(): Unit {
        val read = MicroEdgeBuilder().apply { read(x, y, p(f, g)) }.build()
        val admitted = must(y, exclusion = ex(g), mark = mu)
        assertExpected(read, admitted, setOf(admitted, must(x, mark = mu)))
        val blocked = must(y, exclusion = ex(f), mark = mu)
        assertExpected(read, blocked, setOf(blocked))
        val atField = must(y, p(f, g), ex(h), mu)
        assertExpected(read, atField, setOf(atField, must(x, exclusion = ex(h), mark = mu)))
        val prefixBlocked = must(y, p(f), ex(g))
        assertExpected(read, prefixBlocked, setOf(prefixBlocked))
        val may = c(y, tail = Tail.ANY, mark = mu)
        assertExpected(read, may, setOf(may, c(x, tail = Tail.ANY, mark = mu)))
    }

    @Test fun `self read has no root identity or refine only output`(): Unit {
        val read = MicroEdgeBuilder().apply { read(x, x, p(f)) }.build()
        assertExpected(read, flow(x, exclusion = ex(g), mark = mx), setOf(c(x, tail = Tail.ANY, mark = mx)))
        assertExpected(read, must(x, exclusion = ex(g)), setOf(must(x)))
        assertExpected(read, must(x, exclusion = ex(f)), emptySet())
        assertExpected(read, c(x, p(f, g)), setOf(c(x, p(g))))
        assertExpected(read, flow(x, p(f), ex(g), mx), setOf(flow(x, exclusion = ex(g), mark = mx)))
        assertExpected(read, c(x), emptySet())
        assertExpected(read, c(x, p(g)), emptySet())
    }

    @Test fun `strong field write adds E at the base keeps siblings and removes the overwritten subtree`(): Unit {
        val strong = write(y, p(f))
        for (mark in listOf(MarkSlot.STAR, mx)) {
            assertExpected(strong, flow(y, exclusion = ex(g), mark = mark),
                setOf(flow(y, exclusion = ex(f, g), mark = mark)))
            assertExpected(strong, flow(y, p(h), ex(g), mark), setOf(flow(y, p(h), ex(g), mark)))
            assertExpected(strong, flow(y, p(f), ex(g), mark), emptySet())
        }
        assertExpected(strong, must(y, exclusion = ex(g), mark = mu),
            setOf(must(y, exclusion = ex(f, g), mark = mu)))
        assertExpected(strong, must(y, p(h), ex(f), mu), setOf(must(y, p(h), ex(f), mu)))
        assertExpected(strong, c(y), setOf(c(y)))
        assertExpected(strong, c(y, p(f, h)), emptySet())
        assertExpected(strong, must(y, p(f)), emptySet())
        val demand = c(y, tail = Tail.ANY, mark = mu)
        assertExpected(strong, demand, setOf(demand))
    }

    @Test fun `strong write regenerates value paths and keeps their marks layers and exclusions`(): Unit {
        val strong = write(y, p(f))
        val inputs = listOf(c(x, p(g), mark = mu), c(x, p(g), demand = true),
            flow(x, p(g), ex(h), mx), must(x, p(g), ex(h), mu), c(x, p(g), Tail.ANY, mu))
        for (input in inputs) {
            val generated = c(y, p(f, g), input.fact.tail, input.fact.mark, input.exclusion, input.demand)
            assertExpected(strong, input, setOf(input, generated))
        }
    }

    @Test fun `weak element write retains all old locations and generates value under the element`(): Unit {
        val weak = write(y, p(ELEMENT_ACCESSOR_IDX), weak = true)
        for (input in listOf(c(y, p(ELEMENT_ACCESSOR_IDX, f)), flow(y, exclusion = ex(g), mark = mx),
            must(y, p(ELEMENT_ACCESSOR_IDX), ex(h)), c(y, tail = Tail.ANY, mark = mu)))
            assertExpected(weak, input, setOf(input))
        val value = must(x, p(g), ex(h), mu)
        assertExpected(weak, value, setOf(value, must(y, p(ELEMENT_ACCESSOR_IDX, g), ex(h), mu)))
    }

    @Test fun `synthetic two field strong write keeps each proper prefix with its own exclusion`(): Unit {
        val strong = write(y, p(f, g), values = emptyList())
        assertExpected(strong, flow(y, exclusion = ex(h), mark = mx), setOf(
            flow(y, exclusion = ex(f, h), mark = mx), c(y, p(f), Tail.ANY, mx)))
        assertExpected(strong, must(y, exclusion = ex(h)), setOf(
            must(y, exclusion = ex(f, h)), must(y, p(f), ex(g))))
        assertExpected(strong, flow(y, p(f), ex(h), mx), setOf(flow(y, p(f), ex(g, h), mx)))
        assertExpected(strong, c(y, p(f)), setOf(c(y, p(f))))
        assertExpected(strong, c(y, p(f, h)), setOf(c(y, p(f, h))))
        assertExpected(strong, c(y, p(h, g)), setOf(c(y, p(h, g))))
        assertExpected(strong, c(y, p(f, g)), emptySet())
        assertExpected(strong, must(y, p(f, g, h)), emptySet())
        val firstExcluded = must(y, exclusion = ex(f))
        assertExpected(strong, firstExcluded, setOf(firstExcluded))
    }

    @Test fun `self writes copy the original value while strong and weak old content differ`(): Unit {
        val strong = write(y, p(f), values = listOf(y))
        val weak = write(y, p(f), weak = true, values = listOf(y))
        val overwritten = c(y, p(f))
        assertExpected(strong, overwritten, setOf(c(y, p(f, f))))
        assertExpected(weak, overwritten, setOf(overwritten, c(y, p(f, f))))
        val sibling = c(y, p(g), mark = mu)
        assertExpected(strong, sibling, setOf(sibling, c(y, p(f, g), mark = mu)))
        val root = must(y, exclusion = ex(g), mark = mu)
        assertExpected(strong, root, setOf(must(y, exclusion = ex(f, g), mark = mu), must(y, p(f), ex(g), mu)))
        assertExpected(weak, root, setOf(root, must(y, p(f), ex(g), mu)))
        val abstract = flow(y, exclusion = ex(g), mark = mx)
        assertExpected(strong, abstract, setOf(flow(y, exclusion = ex(f, g), mark = mx), flow(y, p(f), ex(g), mx)))
    }

    @Test fun `two accessor static write keeps other classes and sibling fields with class uncounted`(): Unit {
        val strong = write(AccessPathBase.ClassStatic, p(cls, f))
        assertExpected(strong, c(AccessPathBase.ClassStatic, p(otherClass, f)),
            setOf(c(AccessPathBase.ClassStatic, p(otherClass, f))))
        assertExpected(strong, c(AccessPathBase.ClassStatic, p(cls, g)),
            setOf(c(AccessPathBase.ClassStatic, p(cls, g))))
        assertExpected(strong, c(AccessPathBase.ClassStatic, p(cls, f, g)), emptySet())
        assertExpected(strong, must(AccessPathBase.ClassStatic), setOf(
            must(AccessPathBase.ClassStatic, exclusion = ex(cls)),
            must(AccessPathBase.ClassStatic, p(cls), ex(f))))
        val value = c(x, p(g), mark = mu)
        val written = c(AccessPathBase.ClassStatic, p(cls, f, g), mark = mu)
        assertExpected(strong, value, setOf(value, written), fieldLimit = 1)
        assertEquals(c(AccessPathBase.ClassStatic, p(cls, f), Tail.ANY, mu), limit(written, 1))
        assertEquals(written, limit(written, 2))
    }

    @Test fun `static root identity raises only the class keep position request in run one`(): Unit {
        val strong = write(AccessPathBase.ClassStatic, p(cls, f))
        val initial = Pattern(PathFact(AccessPathBase.ClassStatic, emptyList(), Tail.STAR, MarkSlot.STAR), ExclusionSet.Empty)
        val input = flow(AccessPathBase.ClassStatic, exclusion = ex(g), mark = mx)
        assertExpected(strong, input, setOf(flow(AccessPathBase.ClassStatic, exclusion = ex(cls, g), mark = mx)),
            run1 = true, inputPremise = setOf(initial),
            requests = listOf(RequestKind.Position(m.path(p(cls))!!)), runtimeRequests = emptyList())
        val excluded = flow(AccessPathBase.ClassStatic, exclusion = ex(cls), mark = mx)
        assertExpected(strong, excluded, setOf(excluded), run1 = true, inputPremise = setOf(initial))
        assertExpected(strong, input, setOf(flow(AccessPathBase.ClassStatic, exclusion = ex(cls, g), mark = mx),
            c(AccessPathBase.ClassStatic, p(cls), Tail.ANY, mx)))
    }

    @Test fun `empty root write replaces the base and an empty self write regenerates it`(): Unit {
        val replace = write(y, emptyList())
        for (old in listOf(c(y), c(y, p(f)), flow(y, exclusion = ex(g), mark = mx), must(y, exclusion = ex(g))))
            assertExpected(replace, old, emptySet())
        val value = must(x, p(g), ex(h), mu)
        assertExpected(replace, value, setOf(value, must(y, p(g), ex(h), mu)))
        val self = write(y, emptyList(), values = listOf(y))
        val input = flow(y, p(f), ex(g), mx)
        assertExpected(self, input, setOf(input))
        val kill = write(y, emptyList(), values = emptyList())
        assertExpected(kill, input, emptySet())
    }

    @Test fun `untouched alias retains its original fact beside generated write targets`(): Unit {
        val strong = write(y, p(f), aliases = listOf(z to p(g)))
        assertFalse(z in strong.touched)
        val oldAlias = must(z, p(g, f), ex(h), mu)
        assertExpected(strong, oldAlias, setOf(oldAlias))
        assertSame(oldAlias, reference().apply(FormsReference.Mode.STATEMENT, strong, premise, oldAlias, at).single())
        val value = c(x, p(h))
        val expected = setOf(oldAlias, value, c(y, p(f, h)), c(z, p(g, f, h)))
        assertEquals(expected, applyAll(strong, listOf(oldAlias, value)))
        assertExpected(strong, value, expected - oldAlias)
    }

    @Test fun `alias paths remain complete until the positive statement result cut`(): Unit {
        val aliasPrefix = List(24) { if (it % 2 == 0) g else h }
        val writtenPath = p(f, g, h)
        val strong = write(y, writtenPath, aliases = listOf(z to aliasPrefix, y to p(h)))
        val value = must(x, p(h, f), ex(g), mu)
        val target = must(y, p(f, g, h, h, f), ex(g), mu)
        val alias = must(z, aliasPrefix + p(f, g, h, h, f), ex(g), mu)
        assertExpected(strong, value, setOf(value, target, alias), fieldLimit = 1)
        assertEquals(c(z, p(g), Tail.ANY, mu), limit(alias, 1))
        assertEquals(c(y, p(f), Tail.ANY, mu), limit(target, 1))
        assertEquals(29, alias.fact.path.size)
    }

    @Test fun `multiple contributors union their complete outputs and target old contents are killed`(): Unit {
        val summary = MicroEdgeBuilder().apply {
            move(x, a)
            move(x, z)
            move(x, a)
        }.build()
        val first = c(a, p(f), mark = mt)
        val second = must(z, p(g), ex(h), mu)
        val targetOld = c(x, p(h))
        val expected = setOf(first, c(x, p(f), mark = mt), second, must(x, p(g), ex(h), mu))
        assertExpected(summary, first, setOf(first, c(x, p(f), mark = mt)))
        assertExpected(summary, second, setOf(second, must(x, p(g), ex(h), mu)))
        assertExpected(summary, targetOld, emptySet())
        assertEquals(expected, applyAll(summary, listOf(first, second, targetOld)))
        assertEquals(expected, applyAll(summary, listOf(targetOld, second, first)))
    }

    @Test fun `composed move read and write preserve suffix mark and normal must exclusion`(): Unit {
        val move = MicroEdgeBuilder().apply { move(x, a) }.build()
        val read = MicroEdgeBuilder().apply { read(y, x, p(f)) }.build()
        val write = write(z, p(h), values = listOf(y))
        val source = must(a, p(f, g), ex(h), mu)
        val afterMove = setOf(source, must(x, p(f, g), ex(h), mu))
        val afterRead = afterMove + must(y, p(g), ex(h), mu)
        val afterWrite = afterRead + must(z, p(h, g), ex(h), mu)
        assertExpected(move, source, afterMove)
        assertEquals(afterRead, applyAll(read, afterMove.toList()))
        assertEquals(afterWrite, applyAll(write, afterRead.toList()))
        val cutOutputs = LinkedHashSet<Conclusion>()
        for (input in afterRead) reference().applier.statement(write, premise, input, at,
            sink = { _, result -> cutOutputs += limit(result, 1) },
            untouched = { _, result -> cutOutputs += result })
        assertEquals(setOf(source, must(x, p(f, g), ex(h), mu), must(y, p(g), ex(h), mu),
            c(z, p(h), Tail.ANY, mu)), cutOutputs)
    }

    private fun reference(run1: Boolean = false, fieldLimit: Int = Int.MAX_VALUE,
                          requests: MutableList<RequestKind> = ArrayList()) =
        FormsReference(fixture.ops, ReferenceAlgebra(ApMode(run1, Direction.FORWARD, fieldLimit),
            request = { _, r -> requests += r }, manager = m))

    private fun applyAll(summary: StatementSummary, inputs: List<Conclusion>): Set<Conclusion> =
        inputs.flatMap { reference().apply(FormsReference.Mode.STATEMENT, summary, premise, it, at) }.toSet()

    /** Raw expectations are checked against both the per-path interpreter and the tree AP adapter. */
    private fun assertExpected(summary: StatementSummary, input: Conclusion, expected: Set<Conclusion>,
                               run1: Boolean = false, fieldLimit: Int = Int.MAX_VALUE,
                               inputPremise: Set<Pattern> = premise,
                               requests: List<RequestKind> = emptyList(),
                               runtimeRequests: List<RequestKind> = requests.onEach {
                                   require(it is RequestKind.Mark) { "Specify the runtime effects of a scalar Position row" }
                               }) {
        val rawRequests = ArrayList<RequestKind>()
        val oracle = reference(run1, fieldLimit, rawRequests)
        assertEquals(expected, oracle.apply(FormsReference.Mode.STATEMENT, summary, inputPremise, input, at).toSet(),
            "raw input=$input")
        assertEquals(requests, rawRequests, "raw requests for $input")
        val mode = ApMode(run1, Direction.FORWARD, fieldLimit)
        val treeRequests = ArrayList<RequestKind>()
        val output = ArrayList<Facts>()
        val adapter = FormApplier(object : FactAlgebra<Set<Pattern>, Facts> {
            override fun base(c: Facts) = c.base
            override fun filter(c: Facts, filter: TypeFilter) = fixture.ops.filter(c, filter)
            override fun applyEdge(me: MicroEdge, c: Facts, premise: Set<Pattern>, statementEdge: Boolean,
                                   out: (Set<Pattern>, Facts) -> Unit) {
                fixture.ops.applyEdge(c, m.premiseOf(premise.map(m::initial)), me.edge, statementEdge, mode,
                    object : ApOut {
                        override fun result(f: Facts) = out(premise, f)
                        override fun markRequest(mark: TaintMark) { treeRequests += RequestKind.Mark(mark) }
                    }, me.may)
            }
            override fun conjunction(cj: ConjunctiveEdge, premise: Set<Pattern>, c: Facts, node: CommonInst,
                                     out: (Set<Pattern>, Facts) -> Unit) = error("No conjunction in these vectors")
            override fun allowsSource(node: CommonInst, me: MicroEdge) = true
            override fun sourceHit(node: CommonInst, me: MicroEdge) = Unit
        })
        val facts = m.factsOf(input.fact, input.exclusion, if (input.demand) Layer.DEMAND else Layer.NORMAL)
        adapter.statement(summary, inputPremise, facts, at, { _, c -> output += c })
        assertEquals(expected, output.flatMap(oracle::conclusions).toSet(), "tree input=$input")
        assertEquals(runtimeRequests, treeRequests, "tree requests for $input")
    }
}
