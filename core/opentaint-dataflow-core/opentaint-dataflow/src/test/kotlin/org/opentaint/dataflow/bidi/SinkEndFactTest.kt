package org.opentaint.dataflow.bidi

import org.opentaint.dataflow.ap.ifds.AccessPathBase
import org.opentaint.dataflow.ap.ifds.ClassStaticAccessor
import org.opentaint.dataflow.bidi.ap.*
import org.opentaint.dataflow.bidi.engine.*
import org.opentaint.dataflow.bidi.fixtures.MethodAnalysisFixtures
import org.opentaint.dataflow.bidi.interp.*
import org.opentaint.dataflow.bidi.store.*
import org.opentaint.dataflow.configuration.CommonTaintConfigurationSink
import org.opentaint.dataflow.configuration.CommonTaintConfigurationSinkMeta
import kotlin.test.*

internal fun localCore(f: MethodAnalysisFixtures, index: Int = 1, limit: Int = if (index == 1) 1 else 3,
                       key: MethodKey = f.key): RunMethodAnalyzer =
    f.core(index = index, L = limit, key = key, localFactory = MethodLocalServicesFactory(::RunMethodLocalServices))

internal fun localSink(patterns: List<Pattern>, endFacts: List<MicroEdge> = emptyList(),
                       id: String = "local-sink", alternative: Int = 0,
                       rule: CommonTaintConfigurationSink? = null): SinkRule =
    SinkRule(rule ?: object : CommonTaintConfigurationSink {
        override val id = id
        override val meta = object : CommonTaintConfigurationSinkMeta {
            override val message = "local sink"
            override val severity = CommonTaintConfigurationSinkMeta.Severity.Warning
        }
    }, alternative, patterns, endFacts)

internal fun localRules(sinks: List<SinkRule>, summary: StatementSummary = StatementSummary.EMPTY): RuleStatement =
    RuleStatement(summary, StatementSummary(emptySet(), sinks.flatMap { it.endFacts }, emptyList(), emptyMap()), sinks)

class SinkEndFactTest {
    private val argument = AccessPathBase.Argument(0)
    private val result = AccessPathBase.Return

    @Test fun `a concrete sink preserves its complete premise and generates end facts under zero`(): Unit {
        MethodAnalysisFixtures().use { f ->
            val t = MarkSlot.Concrete(f.mark("T"))
            val u = MarkSlot.Concrete(f.mark("U"))
            val initial = f.manager.initial(f.pattern(argument, mark = t))
            val target = PathFact(result, listOf(f.field("end")), Tail.EXACT, u)
            val end = MicroEdge.of(PathEdge(ZERO_FACT, target, ExclusionSet.Empty))
            val sink = localSink(listOf(f.pattern(argument, mark = t)), listOf(end))
            f.exitRules(2, ExitRules(localRules(listOf(sink)), false, emptySet()))
            val core = localCore(f)
            val input = f.fact(argument, mark = t)
            core.emitAfter(initial, f.inst(2), input)
            f.drain(core)
            val (key, witness) = f.vulnerabilities.witnessesOf(1).single()
            assertEquals(VulnerabilityKey(sink.rule, f.key.method, f.inst(2)), key)
            assertEquals(f.key, witness.methodKey)
            assertEquals(0, witness.alternative)
            assertEquals(1, witness.run)
            assertEquals(listOf(target), witness.endFacts)
            assertEquals(1, witness.edges.size)
            assertSame(initial, witness.edges.single().premise)
            assertEquals(Layer.NORMAL, witness.edges.single().layer)
            assertEquals(setOf(Conclusion(PathFact(argument, emptyList(), Tail.EXACT, t), ExclusionSet.Empty, false)),
                methodLeaves(f.ops, listOf(witness.edges.single().facts)))
            assertEquals(setOf(initial to Conclusion(PathFact(argument, emptyList(), Tail.EXACT, t), ExclusionSet.Empty, false),
                f.manager.zero to Conclusion(target, ExclusionSet.Empty, false)), methodEntries(f.ops, core.summaries.all().withoutPrecomputedStaticDefault()))
            assertTrue(f.events.isEmpty())
            assertEquals(0L, core.triggerSeeds)
        }
    }

    @Test fun `sink admission uses the concrete mark and first step exclusions and keeps NORMAL must witnesses`(): Unit {
        for (shape in listOf("exact", "wrong mark", "must admitted", "must excluded"))
            for (layer in Layer.entries) MethodAnalysisFixtures().use { f ->
                val t = MarkSlot.Concrete(f.mark("T"))
                val u = MarkSlot.Concrete(f.mark("U"))
                val field = f.field("f")
                val excluded = f.field("g")
                val premise = f.manager.initial(f.pattern(argument, mark = t))
                val literal = f.pattern(argument, listOf(field), Tail.EXACT, t)
                val sink = localSink(listOf(literal))
                f.exitRules(2, ExitRules(localRules(listOf(sink)), false, emptySet()))
                val must = shape.startsWith("must")
                val inputTail = if (must) Tail.ANY_TAINT else Tail.EXACT
                val e = if (must) ExclusionSet.of(intArrayOf(if (shape == "must excluded") field else excluded)) else ExclusionSet.Empty
                val input = f.fact(argument, if (must) emptyList() else listOf(field), inputTail,
                    if (shape == "wrong mark") u else t, e, layer)
                val core = localCore(f)
                core.emitAfter(premise, f.inst(2), input)
                f.drain(core)
                val fires = shape != "wrong mark" // F1 covers external field E at DEMAND before admission.
                val witnesses = f.vulnerabilities.witnessesOf(1).toList()
                assertEquals(if (fires) 1 else 0, witnesses.size, "$shape/$layer")
                if (fires) {
                    val edge = witnesses.single().second.edges.single()
                    assertSame(premise, edge.premise)
                    val effectiveLayer = if (must && e != ExclusionSet.Empty) Layer.DEMAND else layer
                    assertEquals(effectiveLayer, edge.layer)
                    val tail = inputTail
                    val effectiveE = ExclusionSet.Empty
                    assertEquals(setOf(Conclusion(PathFact(argument, if (must) emptyList() else listOf(field), tail, t),
                        effectiveE, effectiveLayer == Layer.DEMAND)), methodLeaves(f.ops, listOf(edge.facts)))
                }
                assertTrue(f.events.isEmpty())
            }
    }

    @Test fun `a sink trigger joins its layer without changing the generated must target`(): Unit {
        for (layer in Layer.entries) MethodAnalysisFixtures().use { f ->
            val t = MarkSlot.Concrete(f.mark("T"))
            val u = MarkSlot.Concrete(f.mark("end"))
            val initial = f.manager.initial(f.pattern(argument, mark = t))
            val target = PathFact(result, emptyList(), Tail.ANY_TAINT, u)
            val end = MicroEdge.of(PathEdge(ZERO_FACT, target, ExclusionSet.Empty))
            val sink = localSink(listOf(initial.toPattern()), listOf(end))
            f.exitRules(2, ExitRules(localRules(listOf(sink)), false, emptySet()))
            val core = localCore(f)
            core.emitAfter(initial, f.inst(2), f.fact(argument, mark = t, layer = layer))
            f.drain(core)
            val tail = Tail.ANY_TAINT
            assertEquals(setOf(initial to Conclusion(initial.toPattern().fact, ExclusionSet.Empty, layer == Layer.DEMAND),
                f.manager.zero to Conclusion(target.copy(tail = tail), ExclusionSet.Empty, layer == Layer.DEMAND)),
                methodEntries(f.ops, core.summaries.all().withoutPrecomputedStaticDefault()))
            val witness = f.vulnerabilities.witnessesOf(1).single().second
            assertEquals(layer, witness.edges.single().layer)
            assertEquals(listOf(target), witness.endFacts)
            assertEquals(0L, core.triggerSeeds)
        }
    }

    @Test fun `conjunctive sink witnesses retain each input premise and layer in literal order`(): Unit {
        for (reverse in listOf(false, true)) MethodAnalysisFixtures().use { f ->
            val second = AccessPathBase.Argument(1)
            val t = MarkSlot.Concrete(f.mark("T"))
            val u = MarkSlot.Concrete(f.mark("U"))
            val v = MarkSlot.Concrete(f.mark("end"))
            val j1 = f.manager.initial(f.pattern(argument, mark = t))
            val j2 = f.manager.initial(f.pattern(second, mark = u))
            val target = PathFact(result, emptyList(), Tail.ANY_TAINT, v)
            val sink = localSink(listOf(j1.toPattern(), j2.toPattern()),
                listOf(MicroEdge.of(PathEdge(ZERO_FACT, target, ExclusionSet.Empty))))
            f.exitRules(2, ExitRules(localRules(listOf(sink)), false, emptySet()))
            val core = localCore(f)
            val contributions = listOf(j1 to f.fact(argument, mark = t), j2 to f.fact(second, mark = u, layer = Layer.DEMAND))
                .let { if (reverse) it.reversed() else it }
            core.emitAfter(contributions[0].first, f.inst(2), contributions[0].second)
            assertTrue(f.vulnerabilities.witnessesOf(1).none())
            core.emitAfter(contributions[1].first, f.inst(2), contributions[1].second)
            f.drain(core)
            val witness = f.vulnerabilities.witnessesOf(1).single().second
            assertEquals(listOf(j1, j2), witness.edges.map { it.premise })
            assertEquals(listOf(Layer.NORMAL, Layer.DEMAND), witness.edges.map { it.layer })
            assertEquals(setOf(Conclusion(j1.toPattern().fact, ExclusionSet.Empty, false)),
                methodLeaves(f.ops, listOf(witness.edges[0].facts)))
            assertEquals(setOf(Conclusion(j2.toPattern().fact, ExclusionSet.Empty, true)),
                methodLeaves(f.ops, listOf(witness.edges[1].facts)))
            assertSame(f.manager.union(j1, j2), witness.supportPremise(f.manager))
            assertEquals(setOf(j1 to Conclusion(j1.toPattern().fact, ExclusionSet.Empty, false),
                j2 to Conclusion(j2.toPattern().fact, ExclusionSet.Empty, true),
                f.manager.zero to Conclusion(target, ExclusionSet.Empty, true)),
                methodEntries(f.ops, core.summaries.all().withoutPrecomputedStaticDefault()))
        }
    }

    @Test fun `incomplete evaluated static input is removed only under zero and still completes a later sink`(): Unit {
        for (zeroPremise in listOf(false, true)) MethodAnalysisFixtures().use { f ->
            val static = AccessPathBase.ClassStatic
            val cls = f.manager.accessors.index(ClassStaticAccessor("State"))
            val a = f.field("a")
            val b = f.field("b")
            val e = ExclusionSet.of(intArrayOf(f.field("excluded")))
            val t = MarkSlot.Concrete(f.mark("evaluated"))
            val u = MarkSlot.Concrete(f.mark("retained"))
            val v = MarkSlot.Concrete(f.mark("second"))
            val w = MarkSlot.Concrete(f.mark("end"))
            val consumed = PathFact(static, listOf(cls, a), Tail.EXACT, t)
            val retained = PathFact(static, listOf(cls, b), Tail.ANY_TAINT, u)
            val exactNode = f.manager.taintAlg.leafNode(f.manager.taintLeaves(MarkSet(intArrayOf(t.mark.id)), MarkSet.EMPTY))
            val mustNode = f.manager.taintAlg.leafNode(f.manager.taintLeaves(MarkSet.EMPTY, MarkSet(intArrayOf(u.mark.id))))
            val classNode = f.manager.taintAlg.node(TaintLeaves.EMPTY, intArrayOf(a, b), arrayOf(exactNode, mustNode))
            val root = f.manager.taintAlg.node(TaintLeaves.EMPTY, intArrayOf(cls), arrayOf(classNode))
            val input = f.manager.taintTree(static, Layer.NORMAL, root, e)
            val callerInitial = f.manager.initial(Pattern(consumed, ExclusionSet.Empty))
            val premise = if (zeroPremise) f.manager.zero else callerInitial
            val second = f.manager.initial(f.pattern(argument, mark = v))
            val endTarget = PathFact(result, emptyList(), Tail.EXACT, w)
            val sink = localSink(listOf(Pattern(consumed, ExclusionSet.Empty), second.toPattern()),
                listOf(MicroEdge.of(PathEdge(ZERO_FACT, endTarget, ExclusionSet.Empty))))
            f.exitRules(2, ExitRules(localRules(listOf(sink)), true, emptySet()))
            val core = localCore(f)
            core.emitAfter(premise, f.inst(2), input)
            f.drain(core)
            assertTrue(f.vulnerabilities.witnessesOf(1).none())
            val expectedStatic = if (zeroPremise) setOf(Conclusion(retained, ExclusionSet.Empty, true))
                else setOf(Conclusion(consumed, ExclusionSet.Empty, false), Conclusion(retained, ExclusionSet.Empty, true))
            assertEquals(expectedStatic.map { premise to it }.toSet(), methodEntries(f.ops, core.summaries.all().withoutPrecomputedStaticDefault()))
            core.emitAfter(second, f.inst(2), f.fact(argument, mark = v))
            f.drain(core)
            val witness = f.vulnerabilities.witnessesOf(1).single().second
            assertEquals(listOf(premise, second), witness.edges.map { it.premise })
            assertEquals(setOf(Conclusion(consumed, ExclusionSet.Empty, false)), methodLeaves(f.ops, listOf(witness.edges[0].facts)))
            assertEquals(ExclusionSet.Empty, (witness.edges[0].facts as TaintTree).exclusion)
            assertEquals(setOf(Conclusion(second.toPattern().fact, ExclusionSet.Empty, false)),
                methodLeaves(f.ops, listOf(witness.edges[1].facts)))
            assertSame(if (zeroPremise) second else f.manager.union(callerInitial, second), witness.supportPremise(f.manager))
            assertEquals(expectedStatic.map { premise to it }.toSet() + setOf(
                second to Conclusion(second.toPattern().fact, ExclusionSet.Empty, false),
                f.manager.zero to Conclusion(endTarget, ExclusionSet.Empty, false)), methodEntries(f.ops, core.summaries.all().withoutPrecomputedStaticDefault()))
        }
    }

    @Test fun `sink alternatives share one vulnerability key while their contexts and literal facts remain separate`(): Unit {
        MethodAnalysisFixtures().use { f ->
            val t = MarkSlot.Concrete(f.mark("T"))
            val literal = f.pattern(argument, tail = Tail.ANY, mark = t)
            val first = localSink(listOf(literal), alternative = 0)
            val second = localSink(listOf(literal), alternative = 1, rule = first.rule)
            f.exitRules(2, ExitRules(localRules(listOf(first, second)), false, emptySet()))
            val initial = f.manager.initial(f.pattern(argument, mark = t))
            val leftKey = f.store.key(context = "left")
            val rightKey = f.store.key(context = "right")
            val leftFact = PathFact(argument, listOf(f.field("left")), Tail.EXACT, t)
            val rightFact = PathFact(argument, listOf(f.field("right")), Tail.EXACT, t)
            val left = localCore(f, key = leftKey)
            val right = localCore(f, key = rightKey)
            left.emitAfter(initial, f.inst(2), f.manager.factsOf(leftFact, ExclusionSet.Empty, Layer.NORMAL))
            right.emitAfter(initial, f.inst(2), f.manager.factsOf(rightFact, ExclusionSet.Empty, Layer.NORMAL))
            f.drain(left)
            f.drain(right)
            val witnesses = f.vulnerabilities.witnessesOf(1).toList()
            assertEquals(setOf(VulnerabilityKey(first.rule, f.key.method, f.inst(2))), witnesses.map { it.first }.toSet())
            assertEquals(setOf(leftKey to 0, leftKey to 1, rightKey to 0, rightKey to 1),
                witnesses.map { it.second.methodKey to it.second.alternative }.toSet())
            for ((_, witness) in witnesses) {
                assertSame(initial, witness.edges.single().premise)
                assertEquals(setOf(Conclusion(if (witness.methodKey == leftKey) leftFact else rightFact, ExclusionSet.Empty, false)),
                    methodLeaves(f.ops, listOf(witness.edges.single().facts)))
            }
        }
    }

    @Test fun `generated end facts take the exit cut while the witness retains its full original path`(): Unit {
        MethodAnalysisFixtures().use { f ->
            val t = MarkSlot.Concrete(f.mark("T"))
            val u = MarkSlot.Concrete(f.mark("end"))
            val initial = f.manager.initial(f.pattern(argument, mark = t))
            val path = listOf(f.field("f"), f.field("g"), f.field("h"))
            val target = PathFact(result, path, Tail.ANY_TAINT, u)
            val sink = localSink(listOf(initial.toPattern()), listOf(MicroEdge.of(PathEdge(ZERO_FACT, target, ExclusionSet.Empty))))
            f.exitRules(2, ExitRules(localRules(listOf(sink)), false, emptySet()))
            val core = localCore(f, limit = 1)
            core.emitAfter(initial, f.inst(2), f.fact(argument, mark = t))
            f.drain(core)
            assertEquals(setOf(Conclusion(target, ExclusionSet.Empty, false)),
                methodLeaves(f.ops, f.cuts.filter { it.first == Cut.EXIT_RULES && it.second.base == result }.map { it.second }))
            assertEquals(setOf(initial to Conclusion(initial.toPattern().fact, ExclusionSet.Empty, false),
                f.manager.zero to Conclusion(PathFact(result, path.take(1), Tail.ANY, u), ExclusionSet.Empty, true)),
                methodEntries(f.ops, core.summaries.all().withoutPrecomputedStaticDefault()))
            assertEquals(listOf(target), f.vulnerabilities.witnessesOf(1).single().second.endFacts)
            assertEquals(1L, core.demandLayerEdges)
        }
    }

    @Test fun `repeated sink literals retain two witness slots and an unconditional sink holds only zero`(): Unit {
        MethodAnalysisFixtures().use { f ->
            val t = MarkSlot.Concrete(f.mark("T"))
            val initial = f.manager.initial(f.pattern(argument, mark = t))
            val repeated = localSink(listOf(initial.toPattern(), initial.toPattern()), id = "repeated")
            val unconditional = localSink(listOf(ZERO_PATTERN), id = "unconditional")
            f.exitRules(2, ExitRules(localRules(listOf(repeated, unconditional)), false, emptySet()))
            val core = localCore(f)
            core.addRootZero()
            core.addInitial(initial)
            f.drain(core, quantum = 1)
            val witnesses = f.vulnerabilities.witnessesOf(1).toList()
            assertEquals(2, witnesses.size)
            val repeatedWitness = witnesses.single { it.first.rule === repeated.rule }.second
            assertEquals(listOf(initial, initial), repeatedWitness.edges.map { it.premise })
            assertEquals(listOf(Layer.NORMAL, Layer.NORMAL), repeatedWitness.edges.map { it.layer })
            for (edge in repeatedWitness.edges)
                assertEquals(setOf(Conclusion(initial.toPattern().fact, ExclusionSet.Empty, false)), methodLeaves(f.ops, listOf(edge.facts)))
            assertSame(initial, repeatedWitness.supportPremise(f.manager))
            val zeroWitness = witnesses.single { it.first.rule === unconditional.rule }.second
            assertSame(f.manager.zero, zeroWitness.edges.single().premise)
            assertSame(Reach.NORMAL, zeroWitness.edges.single().facts)
            assertEquals(setOf(f.manager.zero to Conclusion(ZERO_FACT, ExclusionSet.Empty, false),
                initial to Conclusion(initial.toPattern().fact, ExclusionSet.Empty, false)), methodEntries(f.ops, core.summaries.all().withoutPrecomputedStaticDefault()))
        }
    }

    @Test fun `a real FLOW sink request joins its concrete link and only its answer fires the sink`(): Unit {
        for (index in listOf(1, 3)) MethodAnalysisFixtures().use { f ->
            val t = MarkSlot.Concrete(f.mark("T"))
            val u = MarkSlot.Concrete(f.mark("end"))
            val policy = f.manager.initial(f.pattern(argument, tail = Tail.STAR, mark = MarkSlot.STAR))
            val answer = f.manager.initial(f.pattern(argument, mark = t))
            val target = PathFact(result, emptyList(), Tail.ANY_TAINT, u)
            val sink = localSink(listOf(answer.toPattern()), listOf(MicroEdge.of(PathEdge(ZERO_FACT, target, ExclusionSet.Empty))))
            f.exitRules(2, ExitRules(localRules(listOf(sink)), false, emptySet()))
            val demand = if (index == 1) null else DemandStore.Builder(f.manager).apply {
                add(f.key, DemandPattern(f.pattern(argument, tail = Tail.ANY, mark = MarkSlot.STAR),
                    f.pattern(argument, tail = Tail.ANY, mark = MarkSlot.STAR)))
            }.build()
            val core = f.core(index = index, L = 1, demand = demand,
                localFactory = MethodLocalServicesFactory(::RunMethodLocalServices))
            val ref = CallerRef(f.key, f.manager.zero, Layer.NORMAL, f.inst(1))
            f.admit(core, ref, Layer.NORMAL, f.fact(argument, mark = t))
            assertEquals(setOf(if (index == 1) policy else answer), core.initials.all().toSet())
            assertTrue(f.vulnerabilities.witnessesOf(index).none())
            f.drain(core, quantum = 1)
            val flow = policy to Conclusion(PathFact(argument, emptyList(), Tail.STAR, MarkSlot.STAR), ExclusionSet.Empty, false)
            if (index == 1) {
                assertEquals(setOf(policy, answer), core.initials.all().toSet())
                assertEquals(setOf(flow, answer to Conclusion(answer.toPattern().fact, ExclusionSet.Empty, false),
                    f.manager.zero to Conclusion(target, ExclusionSet.Empty, false)), methodEntries(f.ops, core.summaries.all().withoutPrecomputedStaticDefault()))
                val witness = f.vulnerabilities.witnessesOf(1).single().second
                assertSame(answer, witness.edges.single().premise)
                assertEquals(Layer.NORMAL, witness.edges.single().layer)
                assertEquals(setOf(Conclusion(answer.toPattern().fact, ExclusionSet.Empty, false)),
                    methodLeaves(f.ops, listOf(witness.edges.single().facts)))
            } else {
                assertEquals(setOf(answer), core.initials.all().toSet(), "The actual strong query carries T")
                assertTrue(f.processed.none { it.facts is FlowTree })
                assertEquals(setOf(answer to Conclusion(answer.toPattern().fact, ExclusionSet.Empty, false),
                    f.manager.zero to Conclusion(target, ExclusionSet.Empty, false)),
                    methodEntries(f.ops, core.summaries.all().withoutPrecomputedStaticDefault()))
                assertEquals(1, f.vulnerabilities.witnessesOf(3).count())
            }
            assertTrue(f.events.none { it is RunEvent.MarkRequestsIn },
                "TAINT replay answers locally; restricted mode raises no mark requests")
            if (index == 1) assertTrue(f.events.any { it is RunEvent.InitialAnswersIn },
                "The late request queues its answer from the retained caller input")
            assertEquals(0L, core.demandLayerEdges)
        }
    }
}
