package org.opentaint.dataflow.bidi

import org.opentaint.dataflow.ap.ifds.AccessPathBase
import org.opentaint.dataflow.bidi.ap.*
import org.opentaint.dataflow.bidi.ap.ExclusionSet
import org.opentaint.dataflow.bidi.engine.*
import org.opentaint.dataflow.bidi.fixtures.MethodAnalysisFixtures
import org.opentaint.dataflow.bidi.interp.*
import org.opentaint.dataflow.bidi.store.*
import org.opentaint.dataflow.configuration.CommonTaintConfigurationSink
import org.opentaint.dataflow.configuration.CommonTaintConfigurationSinkMeta
import kotlin.test.*

class ExitRulesTest {
    private val argument = AccessPathBase.Argument(0)
    private val result = AccessPathBase.Return

    @Test
    fun `the ordinary transfer at an end runs before the exit source and sink worklist`(): Unit {
        val f = MethodAnalysisFixtures()
        val t = mark(f, "input")
        val u = mark(f, "exit-source")
        val v = mark(f, "end-fact")
        val j = initial(f, argument, emptyList(), Tail.EXACT, t)
        f.statements[2] = summary(PathEdge(PathFact(argument, emptyList(), Tail.EXACT, t),
            PathFact(result, emptyList(), Tail.EXACT, t), ExclusionSet.Empty))
        val generated = PathFact(AccessPathBase.Argument(1), emptyList(), Tail.EXACT, u)
        val endTarget = PathFact(AccessPathBase.This, emptyList(), Tail.EXACT, v)
        val exitSink = sink(listOf(Pattern(generated, ExclusionSet.Empty)),
            listOf(MicroEdge.of(PathEdge(ZERO_FACT, endTarget, ExclusionSet.Empty))))
        f.exitRules[2] = ExitRules(rule(summary(
            keepEdge(result),
            PathEdge(PathFact(result, emptyList(), Tail.EXACT, t), generated, ExclusionSet.Empty)),
            listOf(exitSink)), false, emptySet())
        val core = f.core()
        core.addInitial(j)
        f.drain(core)
        assertEquals(setOf(
            j to Conclusion(PathFact(result, emptyList(), Tail.EXACT, t), ExclusionSet.Empty, demand = false),
            j to Conclusion(generated, ExclusionSet.Empty, demand = false),
            f.manager.zero to Conclusion(endTarget, ExclusionSet.Empty, demand = false)), raw(core))
        assertEquals(1, f.sinkEvaluations.count { it.first === exitSink })
    }

    @Test
    fun `unconditional exit rules run at both forward exits but only the normal exit summarizes`(): Unit {
        val f = MethodAnalysisFixtures(listOf(listOf(1, 2), emptyList(), emptyList()), normalExit = 1, exceptionalExit = 2)
        val normalMark = mark(f, "normal")
        val exceptionalMark = mark(f, "exceptional")
        val normalTarget = PathFact(result, emptyList(), Tail.EXACT, normalMark)
        val exceptionalTarget = PathFact(AccessPathBase.Exception, emptyList(), Tail.EXACT, exceptionalMark)
        f.exitRules[1] = ExitRules(rule(summary(keepEdge(AccessPathBase.Zero),
            PathEdge(ZERO_FACT, normalTarget, ExclusionSet.Empty))), false, emptySet())
        f.exitRules[2] = ExitRules(rule(summary(keepEdge(AccessPathBase.Zero),
            PathEdge(ZERO_FACT, exceptionalTarget, ExclusionSet.Empty))), false, emptySet())
        val normalSink = sink(listOf(Pattern(normalTarget, ExclusionSet.Empty)), id = "normal")
        val exceptionalSink = sink(listOf(Pattern(exceptionalTarget, ExclusionSet.Empty)), id = "exceptional")
        f.exitRules[1] = ExitRules(rule(f.exitRules.getValue(1).rules.summary, listOf(normalSink)), false, emptySet())
        f.exitRules[2] = ExitRules(rule(f.exitRules.getValue(2).rules.summary, listOf(exceptionalSink)), false, emptySet())
        val core = f.core()
        core.addRootZero()
        f.drain(core)
        assertEquals(setOf(f.manager.zero to Conclusion(ZERO_FACT, ExclusionSet.Empty, demand = false),
            f.manager.zero to Conclusion(normalTarget, ExclusionSet.Empty, demand = false)), raw(core))
        assertEquals(setOf(normalSink, exceptionalSink), f.sinkEvaluations.map { it.first }.toSet())
        assertTrue(raw(core).none { it.second.fact.mark == exceptionalMark })
    }

    @Test
    fun `backward zero starts at both exits while a nonzero requirement starts only at normal exits`(): Unit {
        val f = MethodAnalysisFixtures(listOf(listOf(1, 2), emptyList(), emptyList()), normalExit = 1, exceptionalExit = 2)
        val core = f.core(index = 2)
        core.addZeroEntry()
        f.drain(core)
        assertEquals(setOf(0, 1, 2), f.processed.map { it.node.location.index }.toSet())
        assertEquals(setOf(f.manager.zero to Conclusion(ZERO_FACT, ExclusionSet.Empty, demand = false)), raw(core))
        f.processed.clear()
        val t = mark(f, "requirement")
        val j = initial(f, result, emptyList(), Tail.EXACT, t)
        core.addInitial(j)
        f.drain(core)
        assertEquals(setOf(0, 1), f.processed.map { it.node.location.index }.toSet())
        assertTrue(f.processed.all { it.premise === j })
    }

    @Test
    fun `wired unreachable forward nodes are reached only from the backward zero exceptional start`(): Unit {
        // Node 2 reaches no normal exit; the fixture supplies the real directed wired edge 2 -> exceptional 3.
        val f = MethodAnalysisFixtures(listOf(listOf(1, 2), emptyList(), listOf(3), emptyList()),
            normalExit = 1, exceptionalExit = 3)
        val core = f.core(index = 2)
        core.addZeroEntry()
        f.drain(core)
        assertEquals(setOf(0, 1, 2, 3), f.processed.map { it.node.location.index }.toSet())
        f.processed.clear()
        core.addInitial(initial(f, result, emptyList(), Tail.EXACT, mark(f, "nonzero")))
        f.drain(core)
        assertEquals(setOf(0, 1), f.processed.map { it.node.location.index }.toSet())
    }

    @Test
    fun `entry marks disappear at every exact and must depth only for zero premise this and arguments`(): Unit {
        for (base in listOf(AccessPathBase.This, argument, AccessPathBase.Argument(3), result, AccessPathBase.ClassStatic)) {
            for (zeroPremise in listOf(true, false)) {
                val f = MethodAnalysisFixtures()
                val entry = mark(f, "entry")
                val retained = mark(f, "retained")
                val a = field(f, "a")
                val b = field(f, "b")
                val excluded = field(f, "excluded")
                val e = ExclusionSet.of(intArrayOf(excluded))
                val all = listOf(PathFact(base, emptyList(), Tail.EXACT, entry),
                    PathFact(base, listOf(a, b), Tail.EXACT, entry),
                    PathFact(base, listOf(b), Tail.ANY_TAINT, entry),
                    PathFact(base, listOf(a), Tail.EXACT, retained),
                    PathFact(base, listOf(b), Tail.ANY_TAINT, retained))
                val input = taint(f, base, all, e)
                f.exitRules[2] = ExitRules(RuleStatement.EMPTY, false, setOf(entry.mark))
                val core = f.core(L = 2)
                val premise = if (zeroPremise) f.manager.zero else initial(f, argument, emptyList(), Tail.EXACT, retained)
                core.emitAfter(premise, f.inst(2), input)
                f.drain(core)
                val drop = zeroPremise && (base == AccessPathBase.This || base is AccessPathBase.Argument)
                val expected = decode(input).filter { !drop || it.fact.mark != entry }.map { premise to it }.toSet()
                assertEquals(expected, raw(core), "$base, zero=$zeroPremise")
                assertEquals(if (expected.isEmpty()) emptySet() else setOf(e), core.summaries.all()
                    .map { (it.second as TaintTree).exclusion }.toSet(), "$base, zero=$zeroPremise")
            }
        }
    }

    @Test
    fun `evaluated static parts are dropped for zero premise even when a conjunctive sink is incomplete`(): Unit {
        for (zeroPremise in listOf(true, false)) {
            val f = MethodAnalysisFixtures()
            val t = mark(f, "evaluated")
            val u = mark(f, "retained")
            val cls = f.manager.accessors.index(org.opentaint.dataflow.ap.ifds.ClassStaticAccessor("State"))
            val a = field(f, "a")
            val b = field(f, "b")
            val excluded = field(f, "excluded")
            val e = ExclusionSet.of(intArrayOf(excluded))
            val static = AccessPathBase.ClassStatic
            val consumed = PathFact(static, listOf(cls, a), Tail.EXACT, t)
            val retained = PathFact(static, listOf(cls, b), Tail.ANY_TAINT, u)
            val input = taint(f, static, listOf(consumed, retained), e)
            val incomplete = sink(listOf(Pattern(consumed, ExclusionSet.Empty),
                Pattern(PathFact(argument, emptyList(), Tail.EXACT, t), ExclusionSet.Empty)))
            f.exitRules[2] = ExitRules(rule(sinks = listOf(incomplete)), true, emptySet())
            var evaluations = 0
            f.sinkCheck = { _, _, value, sinks ->
                assertEquals(listOf(incomplete), sinks)
                val parts = ArrayList<Facts>()
                for (literal in incomplete.patterns) when (val answer = f.ops.checkMark(value, literal, ApMode(true, Direction.FORWARD, 2))) {
                    is MarkCheck.Holds -> parts += answer.facts
                    MarkCheck.None -> Unit
                    is MarkCheck.Request -> fail("A concrete static input raises no mark request")
                }
                assertEquals(1, parts.size)
                evaluations++
                Triggers(emptyList(), parts)
            }
            val core = f.core(L = 2)
            val premise = if (zeroPremise) f.manager.zero else initial(f, argument, emptyList(), Tail.EXACT, t)
            core.emitAfter(premise, f.inst(2), input)
            f.drain(core)
            assertEquals(1, evaluations)
            val expected = if (zeroPremise) setOf(Conclusion(retained, e, demand = false)) else decode(input)
            assertEquals(expected.map { premise to it }.toSet(), raw(core), "zero=$zeroPremise")
            assertEquals(e, (core.summaries.all().single().second as TaintTree).exclusion)
        }
    }

    @Test
    fun `raw summaries survive when current demand publishes no piece`(): Unit {
        val f = MethodAnalysisFixtures()
        val t = mark(f, "T")
        val j = initial(f, argument, emptyList(), Tail.EXACT, t)
        val target = fact(f, result, listOf(field(f, "off-spine")), Tail.EXACT, t, Layer.DEMAND)
        val core = f.core(index = 3)
        core.emitAfter(j, f.inst(2), target)
        f.drain(core)
        assertEquals(decode(target).map { j to it }.toSet(), raw(core))
        assertTrue(selected(core).isEmpty())
        assertTrue(f.publications.isEmpty())
        assertEquals(1L, core.demandLayerEdges)
    }

    @Test
    fun `member restriction narrows publications while retaining the full normal multi premise key`(): Unit {
        val f = MethodAnalysisFixtures()
        val t = mark(f, "T")
        val a = field(f, "selected")
        val b = field(f, "off-spine")
        val j1 = initial(f, argument, emptyList(), Tail.EXACT, t)
        val j2 = initial(f, AccessPathBase.Argument(1), emptyList(), Tail.EXACT, t)
        val premise = f.manager.premiseOf(listOf(j1, j2))
        val target = taint(f, result, listOf(PathFact(result, listOf(a), Tail.EXACT, t),
            PathFact(result, listOf(b), Tail.EXACT, t)))
        val d = DemandPattern(j1.toPattern(), Pattern(PathFact(result, listOf(a), Tail.EXACT, t), ExclusionSet.Empty))
        val demand = DemandStore.Builder(f.manager).apply { add(f.key, d) }.build()
        val core = f.core(index = 3, demand = demand)
        core.emitAfter(premise, f.inst(2), target)
        f.drain(core)
        assertEquals(decode(target).map { premise to it }.toSet(), raw(core))
        val expected = setOf(premise to Conclusion(PathFact(result, listOf(a), Tail.EXACT, t), ExclusionSet.Empty, demand = false))
        assertEquals(expected, selected(core))
        assertEquals(expected, f.publications.flatten().flatMap { pub -> decode(pub.conclusion).map { pub.premise to it } }.toSet())
        assertTrue(f.publications.flatten().all { it.premise === premise && it.layer == Layer.NORMAL })
    }

    @Test
    fun `a native normal must singleton publication does not select callee demand`(): Unit {
        val f = MethodAnalysisFixtures()
        val t = mark(f, "T")
        val excluded = field(f, "excluded")
        val e = ExclusionSet.of(intArrayOf(excluded))
        val j = initial(f, argument, emptyList(), Tail.ANY_TAINT, t, e)
        val target = fact(f, result, emptyList(), Tail.ANY_TAINT, t, exclusion = e)
        val d = DemandPattern(Pattern(PathFact(argument, emptyList(), Tail.ANY, t), ExclusionSet.Empty),
            Pattern(PathFact(result, emptyList(), Tail.ANY, t), ExclusionSet.Empty))
        val demand = DemandStore.Builder(f.manager).apply { add(f.key, d) }.build()
        val core = f.core(index = 3, demand = demand)
        core.emitAfter(j, f.inst(2), target)
        f.drain(core)
        val expected = decode(target).map { j to it }.toSet()
        assertEquals(expected, raw(core))
        assertEquals(expected, f.publications.flatten().flatMap { pub -> decode(pub.conclusion).map { pub.premise to it } }.toSet())
        assertTrue(selected(core).isEmpty())
        assertEquals(0L, core.demandLayerEdges)
    }

    @Test
    fun `run one and backward zero publication bypass demand reduction without changing raw selection`(): Unit {
        for (index in listOf(1, 2)) {
            val f = MethodAnalysisFixtures()
            val t = mark(f, "T")
            val target = fact(f, result, listOf(field(f, "return")), Tail.EXACT, t)
            val core = f.core(index = index)
            val end = if (index == 1) f.inst(2) else f.inst(0)
            core.emitAfter(f.manager.zero, end, target)
            f.drain(core)
            val expected = decode(target).map { f.manager.zero to it }.toSet()
            assertEquals(expected, raw(core))
            assertEquals(expected, f.publications.flatten().flatMap { pub -> decode(pub.conclusion).map { pub.premise to it } }.toSet())
            assertEquals(if (index == 2) expected else emptySet(), selected(core))
        }
    }

    @Test
    fun `synchronous publication may add local work and another pending batch before completion`(): Unit {
        val f = MethodAnalysisFixtures()
        val first = fact(f, result, emptyList(), Tail.EXACT, mark(f, "first"))
        val follow = fact(f, AccessPathBase.Argument(1), emptyList(), Tail.EXACT, mark(f, "follow"))
        val queued = initial(f, argument, emptyList(), Tail.EXACT, mark(f, "queued"))
        val core = f.core()
        var injected = false
        f.onPublish = { _, _ ->
            if (!injected) {
                injected = true
                core.addInitial(queued)
                core.emitAfter(f.manager.zero, f.inst(2), follow)
            }
        }
        core.emitAfter(f.manager.zero, f.inst(2), first)
        val complete = core.work(1000)
        if (complete) {
            assertEquals(2, f.publications.size, "True requires both accepted publication batches to be flushed")
            assertTrue(f.processed.any { it.premise === queued }, "True requires callback-enqueued work to be processed")
            assertEquals(3, raw(core).size)
        }
        f.drain(core)
        assertTrue(core.work(1000))
        assertEquals(2, f.publications.size)
        assertSame(first, f.publications[0].single().conclusion)
        assertTrue(f.publications.flatten().any { it.conclusion === follow })
        assertTrue(f.processed.any { it.premise === queued })
        assertEquals(3, raw(core).size)
    }

    @Test
    fun `finite statement closure preserves independent path facts in both directions`(): Unit {
        for (index in listOf(1, 2)) {
            val f = MethodAnalysisFixtures()
            val a = field(f, "a")
            f.statements[1] = summary(keepEdge(argument),
                starEdge(argument, emptyList(), result, emptyList()))
            val base = if (index == 1) argument else result
            val j = initial(f, base, listOf(a), Tail.STAR, MarkSlot.STAR)
            val core = f.core(index = index)
            core.addInitial(j)
            f.drain(core)
            val expected = setOf(argument, result).map {
                j to Conclusion(PathFact(it, listOf(a), Tail.STAR, MarkSlot.STAR), ExclusionSet.Empty, demand = false)
            }.toSet()
            assertEquals(expected, raw(core), "run $index")
        }
    }

    @Test
    fun `freeze releases local stores while preserving the evidence needed by the complete barrier`(): Unit {
        for (index in listOf(1, 2)) {
            val f = MethodAnalysisFixtures()
            val t = mark(f, "T")
            val core = f.core(index = index)
            val j = initial(f, argument, emptyList(), Tail.EXACT, t)
            core.addInitial(j)
            val added = fact(f, argument, emptyList(), Tail.EXACT, t)
            val ref = CallerRef(f.key, f.manager.zero, Layer.NORMAL, f.inst(1))
            f.admit(core, ref, Layer.NORMAL, added)
            val source = PathEdge(ZERO_FACT, PathFact(argument, emptyList(), Tail.EXACT, t), ExclusionSet.Empty)
            if (index == 2) core.sourceHits!!.add(f.key, f.inst(1), source)
            f.drain(core)
            val summaries = raw(core)
            val steps = core.steps
            assertTrue(summaries.isNotEmpty())
            assertTrue(core.nonZeroInitial)
            core.freeze()
            assertFails { core.edges }
            assertFails { core.initials }
            assertEquals(summaries, raw(core))
            assertEquals(steps, core.steps)
            assertTrue(core.nonZeroInitial)
            if (index == 1) {
                val link = f.links(core).single()
                assertEquals(ref, link.caller)
                assertEquals(Pattern(PathFact(argument, emptyList(), Tail.EXACT, t), ExclusionSet.Empty), link.addedFact)
                assertNull(core.sourceHits)
            } else {
                assertNull(core.abstraction)
                val hit = core.sourceHits!!.entries().single()
                assertEquals(f.key, hit.first)
                assertSame(f.inst(1), hit.second)
                assertSame(source, hit.third)
            }
        }
    }

    private fun mark(f: MethodAnalysisFixtures, name: String) = MarkSlot.Concrete(f.manager.marks.mark(name))
    private fun field(f: MethodAnalysisFixtures, name: String) =
        f.manager.accessors.index(org.opentaint.dataflow.ap.ifds.FieldAccessor("Box", name, "Box"))
    private fun fact(f: MethodAnalysisFixtures, base: AccessPathBase, path: List<Int>, tail: Tail, mark: MarkSlot,
                     layer: Layer = Layer.NORMAL, exclusion: ExclusionSet = ExclusionSet.Empty): Facts =
        f.manager.factsOf(PathFact(base, path, tail, mark), exclusion, layer)
    private fun initial(f: MethodAnalysisFixtures, base: AccessPathBase, path: List<Int>, tail: Tail, mark: MarkSlot,
                        exclusion: ExclusionSet = ExclusionSet.Empty): InitialAp =
        f.manager.initial(base, f.manager.path(path), tail, exclusion, mark)
    private fun summary(vararg edges: PathEdge): StatementSummary = StatementSummary(
        edges.mapTo(LinkedHashSet()) { it.from.base }, edges.map(MicroEdge::of), emptyList(), emptyMap())
    private fun rule(summary: StatementSummary = StatementSummary.EMPTY, sinks: List<SinkRule> = emptyList()): RuleStatement =
        RuleStatement(summary, StatementSummary(emptySet(), sinks.flatMap { it.endFacts }, emptyList(), emptyMap()), sinks)
    private fun sink(patterns: List<Pattern>, endFacts: List<MicroEdge> = emptyList(), id: String = "boundary"): SinkRule =
        SinkRule(object : CommonTaintConfigurationSink {
            override val id = id
            override val meta = object : CommonTaintConfigurationSinkMeta {
                override val message = "boundary test"
                override val severity = CommonTaintConfigurationSinkMeta.Severity.Warning
            }
        }, 0, patterns, endFacts)

    private class Prefix {
        val exact = sortedSetOf<Int>()
        val any = sortedSetOf<Int>()
        val children = sortedMapOf<Int, Prefix>()
    }
    private fun taint(f: MethodAnalysisFixtures, base: AccessPathBase, leaves: List<PathFact>,
                      exclusion: ExclusionSet = ExclusionSet.Empty, layer: Layer = Layer.NORMAL): TaintTree {
        val root = Prefix()
        for (leaf in leaves) {
            var node = root
            for (accessor in leaf.path) node = node.children.getOrPut(accessor) { Prefix() }
            val id = (leaf.mark as MarkSlot.Concrete).mark.id
            if (leaf.tail == Tail.EXACT) node.exact += id else node.any += id
        }
        fun build(node: Prefix): TaintNode = f.manager.taintAlg.node(
            f.manager.taintLeaves(MarkSet(node.exact.toIntArray()), MarkSet(node.any.toIntArray())),
            node.children.keys.toIntArray(), node.children.values.map(::build).toTypedArray())
        return f.manager.taintTree(base, layer, build(root), exclusion)
    }

    /** The oracle reads sparse payloads directly, independently of the public leaf sequence. */
    private fun decode(facts: Facts): Set<Conclusion> = buildSet {
        val demand = facts.layer == Layer.DEMAND
        fun put(path: List<Int>, tail: Tail, mark: MarkSlot, exclusion: ExclusionSet = ExclusionSet.Empty) {
            add(Conclusion(PathFact(facts.base, path, tail, mark), exclusion, demand))
        }
        fun <P : TrieLeaf> walk(root: TrieNode<P>, visit: (List<Int>, P) -> Unit) {
            val pending = ArrayDeque<Pair<List<Int>, TrieNode<P>>>()
            pending += emptyList<Int>() to root
            while (pending.isNotEmpty()) {
                val (path, node) = pending.removeLast()
                if (!node.leaf.isEmpty) visit(path, node.leaf)
                val children = node.children
                val accessors = node.accessors
                if (children != null && accessors != null) for (i in accessors.indices)
                    pending += (path + accessors[i]) to children[i]
            }
        }
        when (facts) {
            is Reach -> put(emptyList(), Tail.EXACT, MarkSlot.Concrete(TaintMark.ZERO))
            is FlowTree -> walk(facts.root) { path, _ ->
                put(path, if (demand) Tail.ANY else Tail.STAR, MarkSlot.Star(facts.markExclusion), facts.exclusion)
            }
            is TaintTree -> walk(facts.root) { path, leaf ->
                for (id in leaf.exact.ids) put(path, Tail.EXACT, MarkSlot.Concrete(TaintMark(id)))
                for (id in leaf.any.ids) put(path, if (demand) Tail.ANY else Tail.ANY_TAINT,
                    MarkSlot.Concrete(TaintMark(id)), facts.exclusion)
            }
        }
    }

    private fun raw(core: RunMethodAnalyzer): Set<Pair<PremiseKey, Conclusion>> =
        core.summaries.all().flatMap { (premise, facts) -> decode(facts).asSequence().map { premise to it } }.toSet()
    private fun selected(core: RunMethodAnalyzer): Set<Pair<PremiseKey, Conclusion>> =
        core.summaries.demandEdges().flatMap { (premise, facts) -> decode(facts).asSequence().map { premise to it } }.toSet()
}
