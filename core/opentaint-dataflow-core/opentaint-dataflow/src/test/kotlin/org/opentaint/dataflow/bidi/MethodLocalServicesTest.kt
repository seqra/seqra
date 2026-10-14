package org.opentaint.dataflow.bidi

import org.opentaint.dataflow.ap.ifds.AccessPathBase
import org.opentaint.dataflow.bidi.ap.*
import org.opentaint.dataflow.bidi.driver.Seed
import org.opentaint.dataflow.bidi.driver.SeedIndex
import org.opentaint.dataflow.bidi.engine.*
import org.opentaint.dataflow.bidi.fixtures.MethodAnalysisFixtures
import org.opentaint.dataflow.bidi.interp.*
import org.opentaint.dataflow.bidi.store.*
import org.opentaint.dataflow.configuration.CommonTaintConfigurationSink
import org.opentaint.dataflow.configuration.CommonTaintConfigurationSinkMeta
import org.opentaint.dataflow.util.Cancellation
import kotlin.test.*

class MethodLocalServicesTest {
    private val argument = AccessPathBase.Argument(0)
    private val result = AccessPathBase.Return

    private fun core(f: MethodAnalysisFixtures, index: Int = 1,
                     seeds: SeedIndex = SeedIndex.EMPTY): Pair<RunMethodAnalyzer, RunMethodLocalServices> {
        lateinit var service: RunMethodLocalServices
        val actor = f.core(index = index, seeds = seeds, localFactory = MethodLocalServicesFactory { actor ->
            RunMethodLocalServices(actor).also { service = it }
        })
        return actor to service
    }

    @Test fun `only run one allocates requests and only forward allocates one conjunction store`(): Unit {
        for (index in 1..4) MethodAnalysisFixtures().use { f ->
            val (_, service) = core(f, index)
            assertEquals(index == 1, fieldOfType(service, RequestStore::class.java) != null, "run $index")
            assertEquals(index == 1, fieldNamed(service, "requestJoin") != null, "run $index")
            if (index % 2 == 1) {
                val conjunctions = assertNotNull(service.conjunctions)
                assertSame(conjunctions, service.conjunctions)
                assertSame(conjunctions, fieldOfType(service, ConjunctionStore::class.java))
            } else {
                assertNull(service.conjunctions)
                assertNull(fieldOfType(service, ConjunctionStore::class.java))
            }
        }
    }

    @Test fun `backward service has no sink evaluations witnesses or literal join state`(): Unit {
        MethodAnalysisFixtures().use { f ->
            val (actor, service) = core(f, 2)
            val premise = f.manager.initial(f.pattern())
            val input = f.fact()
            val sink = sink(listOf(f.pattern()))
            val answer = service.checkSinks(premise, f.inst(1), input, listOf(sink))
            assertTrue(answer.parts.isEmpty())
            assertTrue(answer.fired.isEmpty())
            assertTrue(f.vulnerabilities.witnessesOf(2).none())
            val edge = ConjunctiveEdge(listOf(f.pattern(), f.pattern()), f.pattern(result).fact)
            assertFails { service.conjunction(edge, premise, input, f.inst(1)) { _, _ ->
                fail("A backward form has independent branches rather than a conjunction")
            } }
            assertNull(service.conjunctions)
            assertTrue(actor.summaries.all().none())
        }
    }

    @Test fun `cancellation precedes standing request link literal and sink mutations`(): Unit {
        MethodAnalysisFixtures().use { f ->
            val (actor, service) = core(f)
            val policy = f.manager.initial(f.pattern(tail = Tail.STAR, mark = MarkSlot.STAR))
            val request = RequestKind.Mark(f.mark("T"))
            val premise = f.manager.initial(f.pattern())
            val input = f.fact()
            val ref = CallerRef(f.store.key("caller"), policy, Layer.NORMAL, f.inst(1))
            val sink = sink(listOf(f.pattern()))
            val conjunction = ConjunctiveEdge(listOf(f.pattern(argument), f.pattern(result)),
                f.pattern(AccessPathBase.This).fact)
            f.cancellation.cancel()
            assertFailsWith<Cancellation.Cancelled> { service.addRequest(policy, request) }
            assertFailsWith<Cancellation.Cancelled> { service.onAddedLink(ref, Layer.NORMAL, input) }
            assertFailsWith<Cancellation.Cancelled> {
                service.conjunction(conjunction, premise, input, f.inst(1)) { _, _ -> fail("Cancelled join") }
            }
            assertFailsWith<Cancellation.Cancelled> { service.checkSinks(premise, f.inst(1), input, listOf(sink)) }
            f.cancellation.activate()
            val requests = fieldOfType(service, RequestStore::class.java) as RequestStore
            assertTrue(requests.overlapping(f.pattern()).none())
            assertTrue(actor.initials.all().none())
            assertTrue(f.vulnerabilities.witnessesOf(1).none())
            val other = f.fact(result)
            val otherPremise = f.manager.initial(f.pattern(result))
            service.conjunction(conjunction, otherPremise, other, f.inst(1)) { _, _ ->
                fail("The cancelled first literal cannot remain in the store")
            }
        }
    }

    @Test fun `cancelled seed operations neither emit nor poison a later trigger`(): Unit {
        MethodAnalysisFixtures().use { f ->
            val requirement = f.pattern()
            val sink = sink(listOf(requirement))
            val seeds = SeedIndex.of(listOf(Seed.Sink(sink.rule, f.key, f.inst(1), requirement)))
            val (actor, service) = core(f, 2, seeds)
            val emitted = ArrayList<Facts>()
            f.cancellation.cancel()
            assertFailsWith<Cancellation.Cancelled> { service.fireSinkSeeds(f.inst(1), emitted::add) }
            assertFailsWith<Cancellation.Cancelled> { service.fireTriggerSeeds(f.inst(1), sink, emitted::add) }
            f.cancellation.activate()
            assertTrue(emitted.isEmpty())
            assertTrue(f.cuts.isEmpty())
            assertEquals(0L, actor.triggerSeeds)
            service.fireSinkSeeds(f.inst(1), emitted::add)
            service.fireTriggerSeeds(f.inst(1), sink, emitted::add)
            service.fireTriggerSeeds(f.inst(1), sink, emitted::add)
            assertEquals(2, emitted.size)
            assertEquals(listOf(Cut.SEED, Cut.SEED), f.cuts.map { it.first })
            assertEquals(1L, actor.triggerSeeds)
            assertEquals(setOf(Conclusion(requirement.fact, ExclusionSet.Empty, false)), methodLeaves(f.ops, emitted))
        }
    }

    @Test fun `service freeze drops request join conjunction and trigger state`(): Unit {
        MethodAnalysisFixtures().use { f ->
            val (actor, service) = core(f)
            populateState(f, actor, service)
            assertNotNull(fieldOfType(service, RequestStore::class.java))
            val requestJoin = service.javaClass.getDeclaredField("requestJoin").also { it.isAccessible = true }
            assertNotNull(requestJoin.get(service))
            assertNotNull(service.conjunctions)
            assertTrue(collectionFields(service).any { it.isNotEmpty() })
            service.freeze()
            assertNull(fieldOfType(service, RequestStore::class.java))
            assertNull(requestJoin.get(service))
            assertNull(service.conjunctions)
            assertTrue(collectionFields(service).all { it.isEmpty() })
            assertEquals(1L, actor.triggerSeeds)
        }
    }

    @Test fun `core freeze releases its concrete local service and its complete private state`(): Unit {
        MethodAnalysisFixtures().use { f ->
            val (actor, service) = core(f)
            populateState(f, actor, service)
            assertSame(service, actor.localServices)
            assertTrue(service.hasPendingRequests)
            assertFalse(actor.work(1), "One quantum starts the detached request phase and cannot complete it")
            assertFails { actor.freeze() }
            f.drain(actor, quantum = 1)
            assertFalse(service.hasPendingRequests)
            actor.freeze()
            assertFails { actor.localServices }
            assertFails { actor.runnerPort }
            assertFails { actor.context }
            assertNull(fieldOfType(service, RequestStore::class.java))
            assertNull(fieldNamed(service, "requestJoin"))
            assertNull(service.conjunctions)
            assertTrue(collectionFields(service).all { it.isEmpty() })
            assertEquals(1L, actor.triggerSeeds)
        }
    }

    @Test fun `a real reversed exit source records its forward object before the first boundary cut or store`(): Unit {
        MethodAnalysisFixtures().use { f ->
            val forward = PathEdge(ZERO_FACT, f.pattern(result, tail = Tail.ANY_TAINT).fact, ExclusionSet.Empty)
            val source = MicroEdge.of(forward)
            f.exitRules[2] = ExitRules(RuleStatement(
                StatementSummary(setOf(AccessPathBase.Zero), listOf(MicroEdge.of(ZERO_KEEP), source), emptyList(), emptyMap()),
                StatementSummary.EMPTY, emptyList()), false, emptySet())
            val (actor, _) = core(f, 2)
            var observed = false
            f.onCut = { point, facts ->
                if (point == Cut.EXIT_RULES && facts is Reach && !observed) {
                    observed = true
                    val hit = assertNotNull(actor.sourceHits).entries().single()
                    assertEquals(f.key, hit.first)
                    assertSame(f.inst(2), hit.second)
                    assertSame(forward, hit.third)
                    assertTrue(actor.edges.edgesAt(f.inst(2)).none())
                    assertTrue(actor.summaries.all().none())
                }
            }
            actor.addInitial(f.manager.initial(f.pattern(result)))
            assertTrue(observed)
            f.drain(actor)
            assertSame(forward, assertNotNull(actor.sourceHits).entries().single().third)
        }
    }

    @Test fun `restricted entry and exit sources require the full forward source at their own boundary place`(): Unit {
        for (boundary in listOf(0, 2)) for (seedKind in listOf("none", "wrong-place", "wrong-edge", "matching")) {
            MethodAnalysisFixtures().use { f ->
                val source = MicroEdge.of(PathEdge(ZERO_FACT, f.pattern(result).fact, ExclusionSet.Empty))
                val rules = RuleStatement(StatementSummary(setOf(AccessPathBase.Zero),
                    listOf(MicroEdge.of(ZERO_KEEP), source), emptyList(), emptyMap()), StatementSummary.EMPTY, emptyList())
                if (boundary == 0) f.entryRules = rules else f.exitRules[2] = ExitRules(rules, false, emptySet())
                val seed = when (seedKind) {
                    "none" -> emptyList()
                    "wrong-place" -> listOf(Seed.Source(f.key, f.inst(1), source.forward))
                    "wrong-edge" -> listOf(Seed.Source(f.key, f.inst(boundary), PathEdge(ZERO_FACT,
                        f.pattern(result, mark = MarkSlot.Concrete(f.mark("other"))).fact, ExclusionSet.Empty)))
                    else -> listOf(Seed.Source(f.key, f.inst(boundary), source.forward))
                }
                val (actor, _) = core(f, 3, SeedIndex.of(seed))
                actor.addRootZero()
                f.drain(actor)
                val expected = buildSet {
                    add(f.manager.zero to Conclusion(ZERO_FACT, ExclusionSet.Empty, false))
                    if (seedKind == "matching") add(f.manager.zero to Conclusion(source.edge.to, ExclusionSet.Empty, false))
                }
                assertEquals(expected, methodEntries(f.ops, actor.summaries.all()), "boundary=$boundary, $seedKind")
                assertNull(actor.sourceHits)
                assertTrue(f.events.none { it is RunEvent.RequestIn || it is RunEvent.MarkRequestsIn })
            }
        }
    }

    private fun populateState(f: MethodAnalysisFixtures, actor: RunMethodAnalyzer, service: RunMethodLocalServices) {
        val policy = f.manager.initial(f.pattern(tail = Tail.STAR, mark = MarkSlot.STAR))
        service.addRequest(policy, RequestKind.Mark(f.mark("T")))
        val premise = f.manager.initial(f.pattern())
        val conjunction = ConjunctiveEdge(listOf(f.pattern(argument), f.pattern(result)), f.pattern(AccessPathBase.This).fact)
        service.conjunction(conjunction, premise, f.fact(), f.inst(1)) { _, _ -> fail("Only one literal is present") }
        service.fireTriggerSeeds(f.inst(1), sink(listOf(f.pattern()))) { facts ->
            assertEquals(setOf(Conclusion(f.pattern().fact, ExclusionSet.Empty, false)), methodLeaves(f.ops, listOf(facts)))
        }
        assertEquals(1L, actor.triggerSeeds)
    }

    private fun collectionFields(owner: Any): List<Collection<*>> = owner.javaClass.declaredFields
        .filter { Collection::class.java.isAssignableFrom(it.type) }.map { field ->
            field.isAccessible = true
            field.get(owner) as? Collection<*> ?: emptyList<Any>()
        }

    private fun fieldNamed(owner: Any, name: String): Any? = owner.javaClass.getDeclaredField(name).let {
        it.isAccessible = true
        it.get(owner)
    }

    private fun fieldOfType(owner: Any, type: Class<*>): Any? = owner.javaClass.declaredFields
        .single { it.type == type }.let { field -> field.isAccessible = true; field.get(owner) }

    private fun sink(patterns: List<Pattern>): SinkRule = SinkRule(object : CommonTaintConfigurationSink {
        override val id = "local-services"
        override val meta = object : CommonTaintConfigurationSinkMeta {
            override val message = "local service test"
            override val severity = CommonTaintConfigurationSinkMeta.Severity.Warning
        }
    }, 0, patterns, emptyList())
}
