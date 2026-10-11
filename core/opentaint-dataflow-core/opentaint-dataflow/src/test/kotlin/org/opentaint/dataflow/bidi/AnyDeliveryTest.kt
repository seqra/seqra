package org.opentaint.dataflow.bidi

import org.opentaint.dataflow.ap.ifds.AccessPathBase
import org.opentaint.dataflow.bidi.ap.*
import org.opentaint.dataflow.bidi.engine.*
import org.opentaint.dataflow.bidi.fixtures.PipelineFixtures
import org.opentaint.dataflow.bidi.store.Record
import kotlin.test.*

class AnyDeliveryTest {
    private val argument = AccessPathBase.Argument(0)
    private val result = AccessPathBase.Return

    private fun packed(f: PipelineFixtures, values: List<Facts>): Facts =
        values.reduce { a, b -> f.ap.mergeAddDelta(a, b).first }

    @Test fun `restricted native must records deduplicate candidates across added leaves and retain the complete matched input`(): Unit {
        PipelineFixtures(index = 3).use { f ->
            val t = MarkSlot.Concrete(f.store.mark("T"))
            val u = MarkSlot.Concrete(f.store.mark("U"))
            val field = f.store.field("f")
            val g = f.store.field("g")
            val blocked = f.store.field("blocked")
            val e = ExclusionSet.of(intArrayOf(blocked))
            val inputE = ExclusionSet.of(intArrayOf(f.store.field("inputBlocked")))
            val initial = f.ap.initial(f.store.pattern(argument, tail = Tail.ANY_TAINT, mark = t, exclusion = e))
            val conclusion = f.store.fact(result, listOf(g), Tail.ANY_TAINT, u, e)
            val record = Record(f.callee, Direction.FORWARD, initial, conclusion)
            f.records.add(record)
            val input = packed(f, listOf(
                f.store.fact(argument, listOf(field), Tail.ANY_TAINT, t, inputE),
                f.store.fact(argument, listOf(g), Tail.ANY_TAINT, t, inputE)))
            val sub = f.subscription(input)
            f.subscriptions.subscribe(sub)
            assertEquals(1, f.native.size)
            val call = f.native.single()
            assertEquals(f.caller, call.caller)
            assertSame(record, call.record)
            assertEquals(sub, call.part)
            assertSame(sub.ref, call.part.ref)
            assertEquals(setOf(Conclusion(PathFact(argument, listOf(field), Tail.ANY_TAINT, t), inputE, false),
                Conclusion(PathFact(argument, listOf(g), Tail.ANY_TAINT, t), inputE, false)),
                methodLeaves(f.ops, listOf(call.part.added)))
            assertEquals(e, (call.record.conclusion as TaintTree).exclusion)
            assertTrue(f.views.isEmpty())
            assertTrue(f.summaries.isEmpty())
            assertTrue(f.storage().candidates(sub, f.config.mode).isEmpty(), "Reading records adds no current publication or demand")
            f.subscriptions.subscribe(sub)
            assertEquals(1, f.native.size)
        }
    }

    @Test fun `opposite direction must views deduplicate across leaves and preserve concrete STAR exclusions and distinct marks`(): Unit {
        PipelineFixtures(index = 2).use { f ->
            val t = MarkSlot.Concrete(f.store.mark("output"))
            val u = MarkSlot.Concrete(f.store.mark("input"))
            val field = f.store.field("f")
            val g = f.store.field("g")
            val e = ExclusionSet.of(intArrayOf(f.store.field("blocked")))
            val initial = f.ap.initial(f.store.pattern(argument, listOf(field), Tail.EXACT, u))
            val record = Record(f.callee, Direction.FORWARD, initial, f.store.fact(result, tail = Tail.ANY_TAINT, mark = t, exclusion = e))
            f.records.add(record)
            val input = packed(f, listOf(f.store.fact(result, listOf(field), mark = t), f.store.fact(result, listOf(g), mark = t)))
            val sub = f.subscription(input)
            f.subscriptions.subscribe(sub)
            assertEquals(1, f.views.size)
            val call = f.views.single()
            assertEquals(f.caller, call.caller)
            assertEquals(sub, call.part)
            assertEquals(RecordLeafView(Pattern(PathFact(result, emptyList(), Tail.STAR, t), e),
                Conclusion(PathFact(argument, listOf(field), Tail.EXACT, u), ExclusionSet.Empty, false)), call.view)
            assertEquals(methodLeaves(f.ops, listOf(input)), methodLeaves(f.ops, listOf(call.part.added)))
            assertNotEquals(call.view.premise.fact.mark, call.view.result.fact.mark)
            assertTrue(f.native.isEmpty())
            assertTrue(f.summaries.isEmpty())
            assertTrue(f.storage().candidates(sub, f.config.mode).isEmpty())
            f.subscriptions.subscribe(sub)
            assertEquals(1, f.views.size)
        }
    }

    @Test fun `native exact may and distinct must exclusions at one path replay their own complete matched subsets`(): Unit {
        PipelineFixtures(index = 3).use { f ->
            val t = MarkSlot.Concrete(f.store.mark("T"))
            val u = MarkSlot.Concrete(f.store.mark("U"))
            val field = f.store.field("f")
            val g = f.store.field("g")
            val h = f.store.field("h")
            val rows = listOf(
                Tail.EXACT to ExclusionSet.Empty,
                Tail.ANY to ExclusionSet.Empty,
                Tail.ANY_TAINT to ExclusionSet.of(intArrayOf(g)),
                Tail.ANY_TAINT to ExclusionSet.of(intArrayOf(h)),
            ).map { (tail, e) ->
                val initial = f.ap.initial(f.store.pattern(argument, listOf(field), tail, t, e))
                Record(f.callee, Direction.FORWARD, initial, f.store.fact(result, mark = u))
            }
            rows.forEach(f.records::add)

            val exactSub = f.subscription(f.store.fact(argument, listOf(field), Tail.EXACT, t))
            f.subscriptions.subscribe(exactSub)
            assertEquals(listOf(rows[0]), f.native.map { it.record })
            assertEquals(setOf(Conclusion(PathFact(argument, listOf(field), Tail.EXACT, t), ExclusionSet.Empty, false)),
                methodLeaves(f.ops, listOf(f.native.single().part.added)))
            assertSame(exactSub.ref, f.native.single().part.ref)
            f.native.clear()

            // Keep the root and descendants in distinct subscriptions: a root must leaf can subsume its children.
            val rootE = ExclusionSet.of(intArrayOf(g, h))
            val rootSub = f.subscription(f.store.fact(argument, listOf(field), Tail.ANY_TAINT, t, rootE), call = 2)
            f.subscriptions.subscribe(rootSub)
            assertEquals(rows.toSet(), f.native.map { it.record }.toSet())
            assertEquals(4, f.native.size)
            for (call in f.native) {
                assertEquals(setOf(Conclusion(PathFact(argument, listOf(field), Tail.ANY_TAINT, t), rootE, false)),
                    methodLeaves(f.ops, listOf(call.part.added)), call.record.premise.toPattern().toString())
                assertSame(rootSub.ref, call.part.ref)
                assertSame(rows.single { it == call.record }.premise, call.record.premise)
            }
            f.native.clear()

            val deepPaths = listOf(listOf(field, g), listOf(field, h))
            val deepE = ExclusionSet.of(intArrayOf(f.store.field("inputBlocked")))
            val deepSub = f.subscription(packed(f, deepPaths.map {
                f.store.fact(argument, it, Tail.ANY_TAINT, t, deepE)
            }), call = 3)
            f.subscriptions.subscribe(deepSub)
            assertEquals(rows.drop(1).toSet(), f.native.map { it.record }.toSet())
            assertEquals(3, f.native.size)
            for (call in f.native) {
                val j = call.record.premise
                val expectedPaths = when (j.tail) {
                    Tail.EXACT -> error("An exact root premise rejects descendants")
                    Tail.ANY -> deepPaths
                    Tail.ANY_TAINT -> deepPaths.filter { j.exclusion.admits(it[1]) }
                    Tail.STAR -> error("This native matrix has concrete premises")
                }
                assertEquals(expectedPaths.map { Conclusion(PathFact(argument, it, Tail.ANY_TAINT, t), deepE, false) }.toSet(),
                    methodLeaves(f.ops, listOf(call.part.added)), j.toPattern().toString())
                assertSame(deepSub.ref, call.part.ref)
                assertSame(rows.single { it == call.record }.premise, call.record.premise)
            }
            f.native.clear()

            val exactChildren = f.subscription(packed(f, deepPaths.map { f.store.fact(argument, it, mark = t) }), call = 4)
            f.subscriptions.subscribe(exactChildren)
            assertTrue(f.native.isEmpty(), "Exact descendants satisfy neither applicability's any-tail guard nor inside the root")
            assertTrue(f.summaries.isEmpty())
            assertTrue(f.views.isEmpty())
        }
    }

    @Test fun `restricted record replay does not satisfy a concrete premise with FLOW or an excluded must location`(): Unit {
        PipelineFixtures(index = 3).use { f ->
            val t = MarkSlot.Concrete(f.store.mark("T"))
            val u = MarkSlot.Concrete(f.store.mark("U"))
            val blocked = f.store.field("blocked")
            val e = ExclusionSet.of(intArrayOf(blocked))
            val initial = f.ap.initial(f.store.pattern(argument, tail = Tail.ANY_TAINT, mark = t, exclusion = e))
            val record = Record(f.callee, Direction.FORWARD, initial, f.store.fact(result, mark = u))
            f.records.add(record)
            val flowPremise = f.ap.initial(f.store.pattern(argument, tail = Tail.STAR, mark = MarkSlot.STAR))
            f.subscriptions.subscribe(f.subscription(f.store.fact(argument, tail = Tail.STAR, mark = MarkSlot.STAR), premise = flowPremise))
            f.subscriptions.subscribe(f.subscription(f.store.fact(argument, listOf(blocked), Tail.ANY_TAINT, t), call = 2))
            f.subscriptions.subscribe(f.subscription(f.store.fact(argument, tail = Tail.ANY_TAINT, mark = u), call = 3))
            assertTrue(f.native.isEmpty())
            assertTrue(f.views.isEmpty())
            assertTrue(f.summaries.isEmpty())
            assertTrue(f.deliveries.isEmpty(), "Record reads never raise requests or publish new callee demands")
        }
    }

    @Test fun `all three F76 native forward shapes replay their explicit transient opposite views`(): Unit {
        for ((premiseMust, resultMust) in listOf(false to true, true to false, true to true))
            for (blocked in listOf(false, true)) PipelineFixtures(index = 2).use { f ->
                val t = MarkSlot.Concrete(f.store.mark("output"))
                val u = MarkSlot.Concrete(f.store.mark("input"))
                val field = f.store.field("f")
                val g = f.store.field("g")
                val h = f.store.field("h")
                val e = ExclusionSet.of(intArrayOf(h))
                val initial = f.ap.initial(f.store.pattern(argument, listOf(field),
                    if (premiseMust) Tail.ANY_TAINT else Tail.EXACT, u, if (premiseMust) e else ExclusionSet.Empty))
                val record = Record(f.callee, Direction.FORWARD, initial,
                    f.store.fact(result, listOf(g), if (resultMust) Tail.ANY_TAINT else Tail.EXACT, t,
                        if (resultMust) e else ExclusionSet.Empty))
                f.records.add(record)
                val inputPath = if (resultMust) listOf(g, if (blocked) h else field) else listOf(g)
                val inputMark = if (blocked && !resultMust) u else t
                val sub = f.subscription(f.store.fact(result, inputPath, mark = inputMark))
                f.subscriptions.subscribe(sub)
                if (blocked) assertTrue(f.views.isEmpty())
                else {
                    val expected = RecordLeafView(Pattern(PathFact(result, listOf(g), if (resultMust) Tail.STAR else Tail.EXACT, t),
                        if (resultMust) e else ExclusionSet.Empty),
                        Conclusion(PathFact(argument, listOf(field), if (premiseMust) Tail.ANY else Tail.EXACT, u),
                            ExclusionSet.Empty, premiseMust))
                    assertEquals(listOf(expected), f.views.map { it.view })
                    assertEquals(sub, f.views.single().part)
                    assertEquals(Layer.NORMAL, record.conclusion.layer, "A DEMAND view result does not demote the native record")
                    assertEquals(if (premiseMust) Layer.DEMAND else Layer.NORMAL,
                        if (f.views.single().view.result.demand) Layer.DEMAND else Layer.NORMAL)
                }
                assertTrue(f.native.isEmpty())
                assertTrue(f.summaries.isEmpty())
                assertTrue(f.storage().candidates(sub, f.config.mode).isEmpty())
            }
    }

    @Test fun `a backward native record is read as a forward demand view without creating a current publication`(): Unit {
        PipelineFixtures(index = 3).use { f ->
            val t = MarkSlot.Concrete(f.store.mark("T"))
            val u = MarkSlot.Concrete(f.store.mark("U"))
            val field = f.store.field("f")
            val g = f.store.field("g")
            val initial = f.ap.initial(f.store.pattern(result, listOf(g), Tail.ANY, u))
            val record = Record(f.callee, Direction.BACKWARD, initial, f.store.fact(argument, listOf(field), mark = t))
            f.records.add(record)
            val sub = f.subscription(f.store.fact(argument, listOf(field), mark = t))
            f.subscriptions.subscribe(sub)
            assertEquals(listOf(RecordLeafView(Pattern(PathFact(argument, listOf(field), Tail.EXACT, t), ExclusionSet.Empty),
                Conclusion(PathFact(result, listOf(g), Tail.ANY, u), ExclusionSet.Empty, true))), f.views.map { it.view })
            assertEquals(sub, f.views.single().part)
            assertTrue(f.native.isEmpty())
            assertTrue(f.summaries.isEmpty())
            assertTrue(f.storage().candidates(sub, f.config.mode).isEmpty())
        }
    }

    @Test fun `zero only subscriptions use zero publications and skip every native record and view in every mode`(): Unit {
        for (index in listOf(1, 2, 3)) PipelineFixtures(index = index).use { f ->
            val t = MarkSlot.Concrete(f.store.mark("T"))
            val u = MarkSlot.Concrete(f.store.mark("U"))
            val initial = f.ap.initial(f.store.pattern(argument, mark = t))
            f.records.add(Record(f.callee, Direction.FORWARD, f.ap.zero, f.store.fact(result, mark = u)))
            f.records.add(Record(f.callee, Direction.FORWARD, initial, Reach.NORMAL))
            val zeroPub = Publication(f.ap.zero, f.store.fact(result, mark = t))
            val otherPub = Publication(initial, f.store.fact(result, mark = u))
            f.storage().publish(listOf(zeroPub, otherPub))
            val sub = f.subscription(Reach.NORMAL, zeroOnly = true)
            f.subscriptions.subscribe(sub)
            assertEquals(listOf(Triple(sub, zeroPub, 0)), f.summaries.map { Triple(it.part, it.publication, it.member) })
            assertTrue(f.native.isEmpty())
            assertTrue(f.views.isEmpty())
            f.subscriptions.onDelivery(f.callee, listOf(zeroPub, otherPub))
            assertEquals(setOf(Triple(sub, zeroPub, 0)), f.summaries.map { Triple(it.part, it.publication, it.member) }.toSet())
            assertEquals(2, f.summaries.size)
        }
    }

    @Test fun `run one skips records and applies only an actual current publication`(): Unit {
        PipelineFixtures(index = 1).use { f ->
            val t = MarkSlot.Concrete(f.store.mark("T"))
            val u = MarkSlot.Concrete(f.store.mark("U"))
            val initial = f.ap.initial(f.store.pattern(argument, mark = t))
            val resultInitial = f.ap.initial(f.store.pattern(result, mark = u))
            val conclusion = f.store.fact(result, mark = u)
            f.records.add(Record(f.callee, Direction.FORWARD, initial, conclusion))
            f.records.add(Record(f.callee, Direction.BACKWARD, resultInitial, f.store.fact(argument, mark = t)))
            val sub = f.subscription(f.store.fact(argument, mark = t))
            f.subscriptions.subscribe(sub)
            assertTrue(f.native.isEmpty())
            assertTrue(f.views.isEmpty())
            assertTrue(f.summaries.isEmpty())
            val pub = Publication(initial, conclusion)
            f.storage().publish(listOf(pub))
            f.deliverAll()
            assertEquals(listOf(Triple(sub, pub, 0)), f.summaries.map { Triple(it.part, it.publication, it.member) })
            assertTrue(f.native.isEmpty())
            assertTrue(f.views.isEmpty())
        }
    }

    @Test fun `distinct caller references and link layers each replay a native record once with their complete metadata`(): Unit {
        PipelineFixtures(index = 3).use { f ->
            val initial = f.ap.initial(f.store.pattern())
            val record = Record(f.callee, Direction.FORWARD, initial, f.store.fact(result))
            f.records.add(record)
            val input = f.store.fact()
            val a = f.subscription(input, caller = f.store.key(context = "first"), callerLayer = Layer.NORMAL)
            val b = f.subscription(input, caller = f.store.key(context = "second"), callerLayer = Layer.DEMAND, call = 2)
            val c = a.copy(added = f.store.fact(layer = Layer.DEMAND), linkLayer = Layer.DEMAND)
            for (sub in listOf(a, b, c, a, b, c)) f.subscriptions.subscribe(sub)
            assertEquals(3, f.native.size)
            assertEquals(setOf(a, b, c), f.native.map { it.part }.toSet())
            for (call in f.native) {
                assertSame(record, call.record)
                assertEquals(call.part.caller, call.caller)
                assertEquals(call.part.linkLayer, call.part.added.layer)
                assertEquals(if (call.part.ref.caller == b.caller) Layer.DEMAND else Layer.NORMAL, call.part.ref.callerLayer)
            }
            assertTrue(f.summaries.isEmpty())
            assertTrue(f.views.isEmpty())
        }
    }

    @Test fun `an ordinary opposite direction read of a zero source is a transient zero result rather than a native replay`(): Unit {
        PipelineFixtures(index = 2).use { f ->
            val t = MarkSlot.Concrete(f.store.mark("T"))
            val field = f.store.field("f")
            val target = PathFact(result, listOf(field), Tail.EXACT, t)
            val record = Record(f.callee, Direction.FORWARD, f.ap.zero, f.ap.factsOf(target, ExclusionSet.Empty, Layer.NORMAL))
            f.records.add(record)
            val sub = f.subscription(f.store.fact(result, listOf(field), mark = t))
            f.subscriptions.subscribe(sub)
            assertEquals(listOf(RecordLeafView(Pattern(target, ExclusionSet.Empty), Conclusion(
                PathFact(AccessPathBase.Zero, emptyList(), Tail.EXACT, MarkSlot.Concrete(TaintMark.ZERO)), ExclusionSet.Empty, false))),
                f.views.map { it.view })
            assertEquals(sub, f.views.single().part)
            assertTrue(f.native.isEmpty())
            assertTrue(f.summaries.isEmpty())
        }
    }

    @Test fun `native and opposite FLOW record reads keep full field and mark exclusions without concrete requests`(): Unit {
        for (index in listOf(2, 3)) PipelineFixtures(index = index).use { f ->
            val t = f.store.mark("incoming exclusion")
            val u = f.store.mark("record exclusion")
            val field = f.store.field("f")
            val recordE = ExclusionSet.of(intArrayOf(f.store.field("record blocked")))
            val inputE = ExclusionSet.of(intArrayOf(f.store.field("input blocked")))
            val mx = MarkSlot.Star(f.ap.markSetOf(u))
            val initial = f.ap.initial(f.store.pattern(argument, tail = Tail.STAR, mark = MarkSlot.STAR))
            val conclusion = f.store.fact(result, tail = Tail.STAR, mark = mx, exclusion = recordE)
            val record = Record(f.callee, Direction.FORWARD, initial, conclusion)
            f.records.add(record)
            val inputBase = if (index == 3) argument else result
            val callerPremise = f.ap.initial(f.store.pattern(inputBase, tail = Tail.STAR, mark = MarkSlot.STAR))
            val input = f.store.fact(inputBase, listOf(field), Tail.STAR, MarkSlot.Star(f.ap.markSetOf(t)), inputE)
            val sub = f.subscription(input, premise = callerPremise)
            f.subscriptions.subscribe(sub)
            if (index == 3) {
                assertEquals(1, f.native.size)
                assertSame(record, f.native.single().record)
                assertEquals(sub, f.native.single().part)
                assertTrue(f.views.isEmpty())
            } else {
                assertEquals(listOf(RecordLeafView(Pattern(PathFact(result, emptyList(), Tail.STAR, MarkSlot.STAR), ExclusionSet.Empty),
                    Conclusion(PathFact(argument, emptyList(), Tail.STAR, mx), recordE, false))), f.views.map { it.view })
                assertEquals(sub, f.views.single().part)
                assertTrue(f.native.isEmpty())
            }
            assertEquals(setOf(Conclusion(PathFact(inputBase, listOf(field), Tail.STAR, MarkSlot.Star(f.ap.markSetOf(t))), inputE, false)),
                methodLeaves(f.ops, listOf(if (index == 3) f.native.single().part.added else f.views.single().part.added)))
            assertTrue(f.summaries.isEmpty())
            assertTrue(f.deliveries.isEmpty())
            assertTrue(f.storage().candidates(sub, f.config.mode).isEmpty())
        }
    }
}
