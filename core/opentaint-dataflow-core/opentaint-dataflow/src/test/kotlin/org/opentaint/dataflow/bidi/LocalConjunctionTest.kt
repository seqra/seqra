package org.opentaint.dataflow.bidi

import org.opentaint.dataflow.ap.ifds.AccessPathBase
import org.opentaint.dataflow.bidi.ap.*
import org.opentaint.dataflow.bidi.driver.Seed
import org.opentaint.dataflow.bidi.driver.SeedIndex
import org.opentaint.dataflow.bidi.engine.*
import org.opentaint.dataflow.bidi.fixtures.MethodAnalysisFixtures
import org.opentaint.dataflow.bidi.fixtures.GroupedScalarMarks
import org.opentaint.dataflow.bidi.interp.*
import org.opentaint.dataflow.bidi.store.*
import kotlin.test.*

class LocalConjunctionTest {
    private val firstBase = AccessPathBase.Argument(0)
    private val secondBase = AccessPathBase.Argument(1)
    private val result = AccessPathBase.Return

    private fun initial(f: MethodAnalysisFixtures, pattern: Pattern, index: Int = 1): InitialAp =
        f.manager.initial(if (index == 1) GroupedScalarMarks(f.manager.marks).pattern(pattern) else pattern)

    // Synthetic fixture groups contain exactly one full mark; forms retain that full literal.
    private fun literal(f: MethodAnalysisFixtures, initial: InitialAp): Pattern {
        val p = initial.toPattern()
        val m = p.fact.mark as? MarkSlot.Concrete ?: return p
        val full = when (f.manager.marks.key(m.mark)) {
            is MarkAtomKey.Group -> listOf("T", "U", "V", "W")
                .map(f.manager.marks::full).single { f.manager.marks.groupOf(it) == f.manager.marks.groupOf(m.mark) }
            else -> m.mark
        }
        return p.copy(fact = p.fact.copy(mark = MarkSlot.Concrete(full)))
    }
    private fun output(f: MethodAnalysisFixtures, fact: PathFact, index: Int = 1): Conclusion =
        if (index == 1) GroupedScalarMarks(f.manager.marks).conclusion(Conclusion(fact, ExclusionSet.Empty, false))
        else Conclusion(fact, ExclusionSet.Empty, false)

    @Test fun `run1 statement conjunction waits for both inputs and emits every origin`(): Unit {
        for (reverse in listOf(false, true)) MethodAnalysisFixtures().use { f ->
            val t = MarkSlot.Concrete(f.mark("T"))
            val u = MarkSlot.Concrete(f.mark("U"))
            val v = MarkSlot.Concrete(f.mark("V"))
            val j1 = initial(f,f.pattern(firstBase, mark = t))
            val j2 = initial(f,f.pattern(secondBase, mark = u))
            val target = PathFact(result, emptyList(), Tail.ANY_TAINT, v)
            val edge = ConjunctiveEdge(listOf(literal(f, j1), literal(f, j2)), target)
            f.statement(1, StatementSummary(setOf(firstBase, secondBase), emptyList(), listOf(edge), emptyMap()))
            val core = localCore(f)
            val order = if (reverse) listOf(j2, j1) else listOf(j1, j2)
            core.addInitial(order[0])
            f.drain(core, quantum = 1)
            assertTrue(core.summaries.all().none())
            core.addInitial(order[1])
            f.drain(core, quantum = 1)
            assertEquals(setOf(j1 to output(f, target), j2 to output(f, target)),
                methodEntries(f.ops, core.summaries.all()))
            assertTrue(f.publications.flatten().all { it.premise in setOf(j1, j2) && it.layer == Layer.DEMAND })
            assertNotNull((core.localServices as RunMethodLocalServices).conjunctions)
            assertTrue(core.demandLayerEdges > 0L)
            assertTrue(f.events.isEmpty())
        }
    }

    @Test fun `zero contributors disappear from a mixed premise but all zero contributors retain zero`(): Unit {
        for (allZero in listOf(false, true)) MethodAnalysisFixtures().use { f ->
            val t = MarkSlot.Concrete(f.mark("T"))
            val u = MarkSlot.Concrete(f.mark("U"))
            val v = MarkSlot.Concrete(f.mark("V"))
            val first = initial(f,f.pattern(firstBase, mark = t))
            val edge = ConjunctiveEdge(listOf(literal(f, first), f.pattern(secondBase, mark = u)),
                PathFact(result, emptyList(), Tail.EXACT, v))
            f.statement(1, StatementSummary(setOf(firstBase, secondBase), emptyList(), listOf(edge), emptyMap()))
            val core = localCore(f)
            if (allZero) core.emitAfter(f.manager.zero, f.inst(0), f.fact(firstBase, mark = t))
            else core.addInitial(first)
            f.drain(core)
            assertTrue(core.summaries.all().none())
            core.emitAfter(f.manager.zero, f.inst(0), f.fact(secondBase, mark = u))
            f.drain(core)
            val premise = if (allZero) f.manager.zero else first
            assertEquals(setOf(premise to output(f, edge.target)),
                methodEntries(f.ops, core.summaries.all()))
            assertSame(premise, core.summaries.all().single().first)
        }
    }

    @Test fun `a DEMAND contributor demotes a must target before the statement cut and NORMAL contributors cut only overflow`(): Unit {
        for (layer in Layer.entries) MethodAnalysisFixtures().use { f ->
            val t = MarkSlot.Concrete(f.mark("T"))
            val u = MarkSlot.Concrete(f.mark("U"))
            val v = MarkSlot.Concrete(f.mark("V"))
            val j1 = initial(f,f.pattern(firstBase, mark = t), index = 3)
            val j2 = initial(f,f.pattern(secondBase, mark = u), index = 3)
            val path = listOf(f.field("f"), f.field("g"), f.field("h"))
            val edge = ConjunctiveEdge(listOf(literal(f, j1), literal(f, j2)),
                PathFact(result, path, Tail.ANY_TAINT, v))
            f.statement(1, StatementSummary(setOf(firstBase, secondBase), emptyList(), listOf(edge), emptyMap()))
            val core = localCore(f, index = 3)
            core.addInitial(j1)
            f.drain(core)
            core.emitAfter(j2, f.inst(0), f.fact(secondBase, mark = u, layer = layer))
            f.drain(core)
            val premise = f.manager.union(j1, j2)
            val beforeCut = Conclusion(edge.target,
                ExclusionSet.Empty, layer == Layer.DEMAND)
            assertEquals(setOf(beforeCut), methodLeaves(f.ops, f.cuts.filter { it.first == Cut.STATEMENT && it.second.base == result }
                .map { it.second }))
            assertEquals(setOf(premise to Conclusion(PathFact(result, path.take(2), Tail.ANY, v), ExclusionSet.Empty, true)),
                methodEntries(f.ops, core.summaries.all()))
            assertTrue(core.summaries.all().all { it.second.layer == Layer.DEMAND })
            assertTrue(core.demandLayerEdges >= 2L)
        }
    }

    @Test fun `one satisfying fact fills both repeated literal slots without creating a second publication on replay`(): Unit {
        MethodAnalysisFixtures().use { f ->
            val t = MarkSlot.Concrete(f.mark("T"))
            val v = MarkSlot.Concrete(f.mark("V"))
            val j = initial(f,f.pattern(firstBase, mark = t))
            val literal = literal(f, j)
            val edge = ConjunctiveEdge(listOf(literal, literal), PathFact(result, emptyList(), Tail.EXACT, v))
            f.statement(1, StatementSummary(setOf(firstBase), emptyList(), listOf(edge), emptyMap()))
            val core = localCore(f)
            core.addInitial(j)
            f.drain(core, quantum = 1)
            assertEquals(setOf(j to output(f, edge.target)), methodEntries(f.ops, core.summaries.all()))
            val publications = f.publications.toList()
            val cuts = f.cuts.size
            core.emitAfter(j, f.inst(0), f.fact(firstBase, mark = t))
            f.drain(core)
            assertEquals(publications, f.publications)
            assertEquals(cuts, f.cuts.size)
            assertTrue(core.demandLayerEdges > 0L)
        }
    }

    @Test fun `conjunction alternatives at one statement keep their distinct literal requirements`(): Unit {
        MethodAnalysisFixtures().use { f ->
            val t = MarkSlot.Concrete(f.mark("T"))
            val u = MarkSlot.Concrete(f.mark("U"))
            val w = MarkSlot.Concrete(f.mark("W"))
            val v = MarkSlot.Concrete(f.mark("V"))
            val first = initial(f,f.pattern(firstBase, mark = t))
            val second = initial(f,f.pattern(secondBase, mark = u))
            val other = initial(f,f.pattern(secondBase, mark = w))
            val a = ConjunctiveEdge(listOf(literal(f, first), literal(f, second)), PathFact(result, emptyList(), Tail.EXACT, v))
            val b = ConjunctiveEdge(listOf(literal(f, first), literal(f, other)), PathFact(AccessPathBase.This, emptyList(), Tail.EXACT, v))
            f.statement(1, StatementSummary(setOf(firstBase, secondBase), emptyList(), listOf(a, b), emptyMap()))
            val core = localCore(f)
            core.addInitial(first)
            core.addInitial(second)
            f.drain(core)
            val firstResults = setOf(first to output(f, a.target), second to output(f, a.target))
            assertEquals(firstResults, methodEntries(f.ops, core.summaries.all()))
            core.addInitial(other)
            f.drain(core)
            assertEquals(firstResults + setOf(first to output(f, b.target), other to output(f, b.target)),
                methodEntries(f.ops, core.summaries.all()))
        }
    }

    @Test fun `two method contexts cannot complete a conjunction from each others standing inputs`(): Unit {
        MethodAnalysisFixtures().use { f ->
            val t = MarkSlot.Concrete(f.mark("T"))
            val u = MarkSlot.Concrete(f.mark("U"))
            val v = MarkSlot.Concrete(f.mark("V"))
            val first = initial(f,f.pattern(firstBase, mark = t))
            val second = initial(f,f.pattern(secondBase, mark = u))
            val edge = ConjunctiveEdge(listOf(literal(f, first), literal(f, second)), PathFact(result, emptyList(), Tail.EXACT, v))
            f.statement(1, StatementSummary(setOf(firstBase, secondBase), emptyList(), listOf(edge), emptyMap()))
            val left = localCore(f, key = f.store.key(context = "left"))
            val right = localCore(f, key = f.store.key(context = "right"))
            left.addInitial(first)
            right.addInitial(second)
            f.drain(left)
            f.drain(right)
            assertTrue(left.summaries.all().none())
            assertTrue(right.summaries.all().none())
            right.addInitial(first)
            f.drain(right)
            assertTrue(left.summaries.all().none())
            assertEquals(setOf(first to output(f, edge.target), second to output(f, edge.target)),
                methodEntries(f.ops, right.summaries.all()))
        }
    }

    @Test fun `backward conjunction reversal emits independent DEMAND branches and allocates no forward join`(): Unit {
        MethodAnalysisFixtures().use { f ->
            val t = MarkSlot.Concrete(f.mark("T"))
            val u = MarkSlot.Concrete(f.mark("U"))
            val v = MarkSlot.Concrete(f.mark("V"))
            val edge = ConjunctiveEdge(listOf(f.pattern(firstBase, mark = t), f.pattern(secondBase, mark = u)),
                PathFact(result, emptyList(), Tail.ANY_TAINT, v))
            f.statement(1, StatementSummary(setOf(firstBase, secondBase), emptyList(), listOf(edge), emptyMap()))
            val core = localCore(f, index = 2)
            val initial = initial(f,f.pattern(result, mark = v), index = 2)
            core.addInitial(initial)
            f.drain(core, quantum = 1)
            assertEquals(setOf(initial to Conclusion(PathFact(result, emptyList(), Tail.EXACT, v), ExclusionSet.Empty, false),
                initial to Conclusion(PathFact(firstBase, emptyList(), Tail.EXACT, t), ExclusionSet.Empty, true),
                initial to Conclusion(PathFact(secondBase, emptyList(), Tail.EXACT, u), ExclusionSet.Empty, true)),
                methodEntries(f.ops, core.summaries.all()))
            assertNull((core.localServices as RunMethodLocalServices).conjunctions)
            assertTrue(f.vulnerabilities.witnessesOf(2).none())
            assertTrue(core.sourceHits!!.entries().none())
            assertTrue(f.events.isEmpty())
        }
    }

    @Test fun `a restricted normal must contributor retains its full excluded premise and blocks the excluded literal step`(): Unit {
        for (blocked in listOf(false, true)) MethodAnalysisFixtures().use { f ->
            val t = MarkSlot.Concrete(f.mark("T"))
            val u = MarkSlot.Concrete(f.mark("U"))
            val v = MarkSlot.Concrete(f.mark("V"))
            val field = f.field("f")
            val e = ExclusionSet.of(intArrayOf(if (blocked) field else f.field("g")))
            val first = initial(f,f.pattern(firstBase, tail = Tail.ANY_TAINT, mark = t, exclusion = e), index = 3)
            val second = initial(f,f.pattern(secondBase, mark = u), index = 3)
            val edge = ConjunctiveEdge(listOf(f.pattern(firstBase, listOf(field), Tail.EXACT, t), literal(f, second)),
                PathFact(result, emptyList(), Tail.ANY_TAINT, v))
            f.statement(1, StatementSummary(setOf(firstBase, secondBase), emptyList(), listOf(edge), emptyMap()))
            val core = localCore(f, index = 3)
            core.addInitial(first)
            core.addInitial(second)
            f.drain(core)
            val expected = if (blocked) emptySet() else setOf(f.manager.union(first, second) to output(f, edge.target, index = 3))
            assertEquals(expected, methodEntries(f.ops, core.summaries.all()))
            assertEquals(e, first.exclusion)
            assertEquals(0L, core.demandLayerEdges)
            assertTrue(f.events.isEmpty())
        }
    }

    @Test fun `a real conjunctive FLOW read requests its concrete link answer before combining with a zero source`(): Unit {
        for (index in listOf(1, 3)) MethodAnalysisFixtures().use { f ->
            val t = MarkSlot.Concrete(f.mark("T"))
            val u = MarkSlot.Concrete(f.mark("U"))
            val v = MarkSlot.Concrete(f.mark("V"))
            val policy = initial(f,f.pattern(firstBase, tail = Tail.STAR, mark = MarkSlot.STAR))
            val answer = initial(f,f.pattern(firstBase, mark = t), index)
            val sourceTarget = PathFact(secondBase, emptyList(), Tail.EXACT, u)
            val source = MicroEdge.of(PathEdge(ZERO_FACT, sourceTarget, ExclusionSet.Empty))
            f.entryRules(RuleStatement(StatementSummary(setOf(AccessPathBase.Zero), listOf(MicroEdge.of(ZERO_KEEP), source),
                emptyList(), emptyMap()), StatementSummary.EMPTY, emptyList()))
            val target = PathFact(result, emptyList(), Tail.ANY_TAINT, v)
            val edge = ConjunctiveEdge(listOf(literal(f, answer), Pattern(sourceTarget, ExclusionSet.Empty)), target)
            f.statement(1, StatementSummary(setOf(firstBase, secondBase), emptyList(), listOf(edge), emptyMap()))
            val sink = localSink(listOf(f.pattern(result, mark = v)))
            f.exitRules(2, ExitRules(localRules(listOf(sink)), false, emptySet()))
            val demand = if (index == 1) null else DemandStore.Builder(f.manager).apply {
                add(f.key, DemandPattern(f.pattern(firstBase, tail = Tail.ANY, mark = MarkSlot.STAR),
                    f.pattern(result, tail = Tail.ANY, mark = MarkSlot.STAR)))
            }.build()
            val seeds = SeedIndex.of(listOf(Seed.Source(f.key, f.inst(0), source.forward)))
            val core = f.core(index = index, L = maxOf(1, index - 1), demand = demand, seeds = seeds,
                localFactory = MethodLocalServicesFactory(::RunMethodLocalServices))
            core.addRootZero()
            f.admit(core, CallerRef(f.key, f.manager.zero, Layer.NORMAL, f.inst(1)), if (index == 1) Layer.DEMAND else Layer.NORMAL, if (index == 1) f.run1Fact(firstBase, full = t.mark) else f.fact(firstBase, mark = t))
            assertEquals(setOf(f.manager.zero, if (index == 1) policy else answer), core.initials.all().toSet())
            assertTrue(core.summaries.all().none())
            f.drain(core, quantum = 1)
            assertTrue(f.processed.any { it.premise === f.manager.zero && it.node === f.inst(1) && it.facts.base == secondBase },
                "The source contributes in both modes, including the restricted control")
            if (index == 1) assertTrue(f.processed.any { it.premise === policy && it.node === f.inst(1) && it.facts is FlowTree })
            else assertTrue(f.processed.none { it.facts is FlowTree })
            val zero = f.manager.zero to Conclusion(ZERO_FACT, ExclusionSet.Empty, false)
            if (index == 1) {
                assertEquals(setOf(f.manager.zero, policy, answer), core.initials.all().toSet())
                assertEquals(setOf(zero, answer to output(f, target, index)),
                    methodEntries(f.ops, core.summaries.all()))
                val witness = f.vulnerabilities.witnessesOf(1).single().second
                assertSame(answer, witness.edges.single().premise, "The zero-source contributor drops out of the union")
                assertEquals(Layer.DEMAND, witness.edges.single().layer)
                assertEquals(setOf(output(f, target, index)), methodLeaves(f.ops, listOf(witness.edges.single().facts)))
            } else {
                assertEquals(setOf(f.manager.zero, answer), core.initials.all().toSet())
                assertEquals(setOf(zero, answer to output(f, target, index)),
                    methodEntries(f.ops, core.summaries.all()))
                assertEquals(1, f.vulnerabilities.witnessesOf(3).count())
            }
            assertTrue(f.events.none { it is RunEvent.MarkRequestsIn })
            if (index == 1) assertTrue(f.events.any { it is RunEvent.InitialAnswersIn },
                "The late FLOW check recovers its concrete supplier through queued caller replay")
            if (index == 1) assertTrue(core.demandLayerEdges > 0L) else assertEquals(0L, core.demandLayerEdges)
        }
    }
}
