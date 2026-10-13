package org.opentaint.dataflow.bidi

import org.opentaint.dataflow.ap.ifds.AccessPathBase
import org.opentaint.dataflow.bidi.ap.*
import org.opentaint.dataflow.bidi.fixtures.StoreFixtures
import org.opentaint.dataflow.bidi.interp.*
import org.opentaint.ir.api.common.cfg.CommonCallExpr
import org.opentaint.ir.api.common.cfg.CommonInst
import org.opentaint.ir.api.common.cfg.CommonValue
import org.opentaint.dataflow.util.RefManager
import org.opentaint.dataflow.util.SoftReferenceManager
import java.lang.ref.Reference
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReferenceArray
import kotlin.test.*

class FormsCacheTest {
    private data class Form(val value: Int, val direction: Direction = Direction.FORWARD)

    @Test fun `managed cleanup evicts one forward reverse pair and rebuilds equal values`(): Unit {
        val refs = SoftReferenceManager(RefManager())
        var builds = 0
        var reversals = 0
        val cache = FormsCache<Form>(1, refs) { reversals++; it.copy(direction = Direction.BACKWARD) }
        val build = { builds++; Form(17) }
        val forward = cache.get(0, Direction.FORWARD, build)
        val backward = cache.get(0, Direction.BACKWARD, build)
        assertSame(forward, cache.get(0, Direction.FORWARD, build))
        assertSame(backward, cache.get(0, Direction.BACKWARD, build))
        assertEquals(1, refs.cleanup())
        refs.enable()
        val rebuiltBackward = cache.get(0, Direction.BACKWARD, build)
        val rebuiltForward = cache.get(0, Direction.FORWARD, build)
        assertNotSame(forward, rebuiltForward)
        assertNotSame(backward, rebuiltBackward)
        assertEquals(forward, rebuiltForward)
        assertEquals(backward, rebuiltBackward)
        assertEquals(2, builds)
        assertEquals(2, reversals)
        assertEquals(1, refs.cleanup())
    }

    @Test fun `disabled caching returns transient forms without a strong fallback`(): Unit {
        val refs = SoftReferenceManager(RefManager())
        assertEquals(0, refs.cleanup())
        var builds = 0
        val cache = FormsCache<Form>(1, refs) { it.copy(direction = Direction.BACKWARD) }
        val build = { builds++; Form(23) }
        val first = cache.get(0, Direction.FORWARD, build)
        val second = cache.get(0, Direction.FORWARD, build)
        val backward = cache.get(0, Direction.BACKWARD, build)
        assertNotSame(first, second)
        assertEquals(first, second)
        assertEquals(Form(23, Direction.BACKWARD), backward)
        assertEquals(3, builds)
        assertNull(payloadReference(cache, 0))
        refs.enable()
        val retained = cache.get(0, Direction.FORWARD, build)
        assertSame(retained, cache.get(0, Direction.FORWARD, build))
        assertEquals(4, builds)
        assertEquals(1, refs.cleanup())
    }

    @Test fun `a failed forward build publishes no payload and permits retry`(): Unit {
        val refs = SoftReferenceManager(RefManager())
        val cache = FormsCache<Form>(1, refs) { it.copy(direction = Direction.BACKWARD) }
        assertFailsWith<IllegalStateException> {
            cache.withForm(0, Direction.BACKWARD, { error("forward failed") }) { error("Used failed form") }
        }
        assertNull(payloadReference(cache, 0))
        val forward = cache.get(0, Direction.FORWARD) { Form(7) }
        assertEquals(Form(7), forward)
        assertSame(forward, cache.get(0, Direction.FORWARD) { error("Unexpected rebuild") })
        assertEquals(1, refs.cleanup())
    }

    @Test fun `managed reversal failure leaves the complete forward payload usable`(): Unit {
        val refs = SoftReferenceManager(RefManager())
        var fail = true
        var builds = 0
        val cache = FormsCache<Form>(1, refs) {
            if (fail) error("reverse failed") else it.copy(direction = Direction.BACKWARD)
        }
        assertFailsWith<IllegalStateException> {
            cache.withForm(0, Direction.BACKWARD, { builds++; Form(11) }) { error("Used failed reversal") }
        }
        val forward = cache.get(0, Direction.FORWARD) { error("Lost forward payload") }
        assertEquals(Form(11), forward)
        fail = false
        val backward = cache.get(0, Direction.BACKWARD) { error("Lost forward payload") }
        assertEquals(Form(11, Direction.BACKWARD), backward)
        assertEquals(1, builds)
        assertEquals(1, refs.cleanup())
    }

    @Test fun `callback keeps its selected payload usable across cleanup and replacement`(): Unit {
        val refs = SoftReferenceManager(RefManager())
        var builds = 0
        val cache = FormsCache<Form>(1, refs) { it.copy(direction = Direction.BACKWARD) }
        val build = { Form(++builds) }
        val result = cache.withForm(0, Direction.BACKWARD, build) { selected ->
            val oldReference = assertNotNull(payloadReference(cache, 0))
            assertEquals(Form(1, Direction.BACKWARD), selected)
            assertEquals(1, refs.cleanup())
            assertNull(oldReference.get())
            refs.enable()
            val replacement = cache.withForm(0, Direction.BACKWARD, build) { it }
            assertEquals(Form(2, Direction.BACKWARD), replacement)
            assertEquals(Form(1, Direction.BACKWARD), selected)
            selected.value
        }
        assertEquals(1, result)
        assertEquals(2, builds)
        assertEquals(1, refs.cleanup())
    }

    @Test fun `exceptional callback leaves the cache available for cleanup and reuse`(): Unit {
        val refs = SoftReferenceManager(RefManager())
        val cache = FormsCache<Form>(1, refs) { it.copy(direction = Direction.BACKWARD) }
        assertFailsWith<IllegalArgumentException> {
            cache.withForm(0, Direction.BACKWARD, { Form(9) }) { throw IllegalArgumentException("callback") }
        }
        assertEquals(Form(9), cache.get(0, Direction.FORWARD) { error("Rebuilt after callback failure") })
        assertEquals(1, refs.cleanup())
        refs.enable()
        assertEquals(Form(10), cache.get(0, Direction.FORWARD) { Form(10) })
        assertEquals(1, refs.cleanup())
    }

    @Test fun `concurrent directions publish one slot payload and reverse it once`(): Unit {
        val refs = SoftReferenceManager(RefManager())
        val builds = AtomicInteger()
        val reversals = AtomicInteger()
        val entered = CountDownLatch(1)
        val resume = CountDownLatch(1)
        val started = CountDownLatch(8)
        val cache = FormsCache<Form>(1, refs) {
            reversals.incrementAndGet()
            it.copy(direction = Direction.BACKWARD)
        }
        val executor = Executors.newFixedThreadPool(8)
        try {
            val results = (0 until 8).map { index ->
                val direction = Direction.entries[index % 2]
                direction to executor.submit<Form> {
                    started.countDown()
                    cache.withForm(0, direction, {
                        builds.incrementAndGet()
                        entered.countDown()
                        assertTrue(resume.await(5, TimeUnit.SECONDS))
                        Form(31)
                    }) { it }
                }
            }
            assertTrue(started.await(5, TimeUnit.SECONDS))
            assertTrue(entered.await(5, TimeUnit.SECONDS))
            resume.countDown()
            val retained = HashMap<Direction, Form>()
            for ((direction, result) in results) {
                val form = result.get(5, TimeUnit.SECONDS)
                assertEquals(Form(31, direction), form)
                val first = retained.putIfAbsent(direction, form)
                if (first != null) assertSame(first, form)
            }
            assertEquals(1, builds.get())
            assertEquals(1, reversals.get())
            assertEquals(1, refs.cleanup())
        } finally {
            resume.countDown()
            executor.shutdownNow()
            assertTrue(executor.awaitTermination(5, TimeUnit.SECONDS))
        }
    }

    @Test fun `same slot recursive forward construction fails without publishing a partial form`(): Unit {
        val refs = SoftReferenceManager(RefManager())
        val cache = FormsCache<Form>(2, refs) { it.copy(direction = Direction.BACKWARD) }
        assertFailsWith<IllegalStateException> {
            cache.get(0, Direction.FORWARD) {
                cache.get(0, Direction.BACKWARD) { error("Recursive builder ran") }
            }
        }
        assertNull(payloadReference(cache, 0))
        assertEquals(Form(3), cache.get(0, Direction.FORWARD) {
            assertEquals(Form(4), cache.get(1, Direction.FORWARD) { Form(4) })
            Form(3)
        })
        assertEquals(2, refs.cleanup())
    }

    @Test fun `same slot recursive reversal fails and can retry from its retained forward form`(): Unit {
        val refs = SoftReferenceManager(RefManager())
        var recursive = true
        lateinit var cache: FormsCache<Form>
        cache = FormsCache(1, refs) { form ->
            if (recursive) cache.get(0, Direction.BACKWARD) { error("Rebuilt during reversal") }
            else form.copy(direction = Direction.BACKWARD)
        }
        assertFailsWith<IllegalStateException> { cache.get(0, Direction.BACKWARD) { Form(5) } }
        assertEquals(Form(5), cache.get(0, Direction.FORWARD) { error("Lost forward form") })
        recursive = false
        assertEquals(Form(5, Direction.BACKWARD), cache.get(0, Direction.BACKWARD) { error("Lost forward form") })
        assertEquals(1, refs.cleanup())
    }

    @Test fun `unused form slots allocate no lock or payload`(): Unit {
        val refs = SoftReferenceManager(RefManager())
        val cache = FormsCache<Form>(1000, refs) { it.copy(direction = Direction.BACKWARD) }
        val slots = slots(cache)
        assertTrue((0 until slots.length()).all { slots.get(it) == null })
        cache.get(19, Direction.FORWARD) { Form(19) }
        cache.get(900, Direction.BACKWARD) { Form(900) }
        assertEquals(listOf(19, 900), (0 until slots.length()).filter { slots.get(it) != null })
        assertEquals(2, refs.cleanup())
    }

    @Test fun `optional statistics count hits rebuilds reversals and uncached successes`(): Unit {
        val refs = SoftReferenceManager(RefManager())
        val stats = FormCacheStatistics()
        val cache = FormsCache<Form>(1, refs, stats) { it.copy(direction = Direction.BACKWARD) }
        val first = cache.get(0, Direction.FORWARD) { Form(1) }
        assertSame(first, cache.get(0, Direction.FORWARD) { error("hit rebuilt") })
        cache.get(0, Direction.BACKWARD) { error("reverse rebuilt forward") }
        assertEquals(1, refs.cleanup())
        refs.enable()
        cache.get(0, Direction.BACKWARD) { Form(2) }
        assertEquals(1, refs.cleanup())
        cache.get(0, Direction.FORWARD) { Form(3) }
        cache.get(0, Direction.FORWARD) { Form(4) }
        val snapshot = stats.snapshot()
        assertEquals(6L, snapshot.accesses)
        assertEquals(2L, snapshot.hits)
        assertEquals(4L, snapshot.buildAttempts)
        assertEquals(4L, snapshot.buildSuccesses)
        assertEquals(0L, snapshot.buildFailures)
        assertEquals(2L, snapshot.reversals)
        assertEquals(0L, snapshot.reversalFailures)
        assertEquals(2L, snapshot.uncachedBuilds)
        assertEquals(0L, snapshot.activePins)
        assertEquals(1L, snapshot.peakPins)
        assertTrue(snapshot.buildNanos >= 0)
        assertTrue(snapshot.reversalNanos >= 0)
    }

    @Test fun `statistics release nested callback pins and distinguish build reversal and callback failures`(): Unit {
        val stats = FormCacheStatistics()
        var reverseFails = true
        val cache = FormsCache<Form>(2, statistics = stats) {
            if (reverseFails) error("reverse") else it.copy(direction = Direction.BACKWARD)
        }
        assertFailsWith<IllegalStateException> { cache.get(0, Direction.FORWARD) { error("build") } }
        assertFailsWith<IllegalStateException> { cache.get(0, Direction.BACKWARD) { Form(0) } }
        assertEquals(0L, stats.snapshot().activePins)
        reverseFails = false
        assertFailsWith<IllegalArgumentException> {
            cache.withForm(0, Direction.BACKWARD, { error("unexpected build") }) {
                assertEquals(1L, stats.snapshot().activePins)
                cache.withForm(1, Direction.FORWARD, { Form(1) }) {
                    assertEquals(2L, stats.snapshot().activePins)
                    throw IllegalArgumentException("callback")
                }
            }
        }
        val snapshot = stats.snapshot()
        assertEquals(4L, snapshot.accesses)
        assertEquals(1L, snapshot.hits)
        assertEquals(3L, snapshot.buildAttempts)
        assertEquals(2L, snapshot.buildSuccesses)
        assertEquals(1L, snapshot.buildFailures)
        assertEquals(2L, snapshot.reversals)
        assertEquals(1L, snapshot.reversalFailures)
        assertEquals(0L, snapshot.activePins)
        assertEquals(2L, snapshot.peakPins)
    }

    @Test fun `statistics default is absent and opt in is controlled by the documented property`(): Unit {
        val cache = FormsCache<Form>(1) { it }
        assertNull(cache.javaClass.getDeclaredField("statistics").apply { isAccessible = true }.get(cache))
        cache.get(0, Direction.FORWARD) { Form(1) }
        val property = FormCacheStatistics.PROPERTY
        val old = System.getProperty(property)
        try {
            System.clearProperty(property)
            assertNull(FormCacheStatistics.ifEnabled())
            System.setProperty(property, "false")
            assertNull(FormCacheStatistics.ifEnabled())
            System.setProperty(property, "true")
            val enabled = assertNotNull(FormCacheStatistics.ifEnabled())
            enabled.compiledPredicate(7, 4)
            enabled.compiledPredicate(1, 0)
            assertEquals(2L, enabled.snapshot().compiledPredicates)
            assertEquals(8L, enabled.snapshot().compiledNodes)
            assertEquals(4L, enabled.snapshot().compiledLeaves)
        } finally {
            if (old == null) System.clearProperty(property) else System.setProperty(property, old)
        }
    }

    @Test fun `aggregate statistics count overlapping callbacks without storing form references`(): Unit {
        val stats = FormCacheStatistics()
        val cache = FormsCache<Form>(1, statistics = stats) { it.copy(direction = Direction.BACKWARD) }
        val entered = CountDownLatch(4)
        val release = CountDownLatch(1)
        val pool = Executors.newFixedThreadPool(4)
        try {
            val tasks = List(4) {
                pool.submit<Int> {
                    cache.withForm(0, Direction.FORWARD, { Form(9) }) {
                        entered.countDown()
                        check(release.await(10, TimeUnit.SECONDS))
                        it.value
                    }
                }
            }
            assertTrue(entered.await(10, TimeUnit.SECONDS))
            val active = stats.snapshot()
            assertEquals(4L, active.accesses)
            assertEquals(3L, active.hits)
            assertEquals(1L, active.buildAttempts)
            assertEquals(4L, active.activePins)
            assertEquals(4L, active.peakPins)
            release.countDown()
            tasks.forEach { assertEquals(9, it.get(10, TimeUnit.SECONDS)) }
            assertEquals(0L, stats.snapshot().activePins)
            val fields = stats.javaClass.declaredFields.filterNot { java.lang.reflect.Modifier.isStatic(it.modifiers) }
            assertTrue(fields.all { it.type == java.util.concurrent.atomic.AtomicLong::class.java })
        } finally {
            release.countDown()
            pool.shutdownNow()
        }
    }

    private fun slots(cache: FormsCache<*>): AtomicReferenceArray<*> =
        cache.javaClass.getDeclaredField("slots").apply { isAccessible = true }.get(cache) as AtomicReferenceArray<*>

    private fun payloadReference(cache: FormsCache<*>, index: Int): Reference<*>? {
        val slot = assertNotNull(slots(cache).get(index))
        return slot.javaClass.getDeclaredField("payload").apply { isAccessible = true }.get(slot) as Reference<*>?
    }

    @Test fun `forward and backward forms are built once in either first direction for every slot`(): Unit {
        for (first in Direction.entries) {
            val builds = IntArray(3)
            var reversals = 0
            val cache = FormsCache<Any>(3) { reversals++; Any() }
            val retained = HashMap<Pair<Int, Direction>, Any>()
            repeat(20) { iteration ->
                for (slot in 0..2) for (direction in listOf(first, if (first == Direction.FORWARD) Direction.BACKWARD else Direction.FORWARD)) {
                    val result = cache.get(slot, direction) { builds[slot]++; Any() }
                    val old = retained.putIfAbsent(slot to direction, result)
                    if (iteration > 0) assertSame(old, result)
                }
            }
            assertContentEquals(intArrayOf(1, 1, 1), builds)
            assertEquals(3, reversals)
            for (slot in 0..2) assertNotSame(retained[slot to Direction.FORWARD], retained[slot to Direction.BACKWARD])
        }
    }

    @Test fun `invalid cache slots do not invoke form builders or disturb valid cached identities`(): Unit {
        var builds = 0
        val cache = FormsCache<Any>(2) { Any() }
        for (slot in listOf(-1, 2, Int.MAX_VALUE)) for (direction in Direction.entries)
            assertFails { cache.get(slot, direction) { builds++; Any() } }
        assertEquals(0, builds)
        val value = cache.get(0, Direction.BACKWARD) { builds++; Any() }
        assertSame(value, cache.get(0, Direction.BACKWARD) { error("Rebuilt cached form") })
        assertEquals(1, builds)
    }

    @Test fun `failed reversal retains its forward form for a later successful attempt`(): Unit {
        var builds = 0
        var reversals = 0
        val cache = FormsCache<Any>(1) { reversals++; if (reversals == 1) error("reverse failure") else Any() }
        assertFails { cache.get(0, Direction.BACKWARD) { builds++; Any() } }
        val forward = cache.get(0, Direction.FORWARD) { error("Forward form was already built") }
        val backward = cache.get(0, Direction.BACKWARD) { error("Forward form was already built") }
        assertSame(forward, cache.get(0, Direction.FORWARD) { Any() })
        assertSame(backward, cache.get(0, Direction.BACKWARD) { Any() })
        assertEquals(1, builds)
        assertEquals(2, reversals)
    }

    private val fixture = StoreFixtures()
    private val entry = fixture.inst(0)
    private val normal = fixture.inst(10)
    private val exceptional = fixture.inst(11)
    private val calls = ArrayList<String>()
    private val callExpr = object : CommonCallExpr {
        override val typeName = "void"
        override val args: List<CommonValue> = emptyList()
    }
    private val rules = RuleStatement(StatementSummary.identities(listOf(AccessPathBase.Argument(0))),
        StatementSummary.EMPTY, emptyList())
    private val exits = ExitRules(rules, true, setOf(fixture.mark("entry")))
    private val plan = CallPlan(emptySet(), emptyList(), emptyList(), CallPoint.BEFORE, CallPoint.AFTER)
    private val interp = object : Interpreter {
        override fun entryNode(method: MethodKey) = entry
        override fun exitNodes(method: MethodKey) = listOf(ExitNode(normal, false), ExitNode(exceptional, true))
        override fun entryRules(method: MethodKey): RuleStatement = error("Use cached method forms")
        override fun exitRules(method: MethodKey, exit: CommonInst): ExitRules = error("Use cached method forms")
        override fun statementSummary(method: MethodKey, statement: CommonInst): StatementSummary = error("Use cached method forms")
        override fun callPlan(caller: MethodKey, statement: CommonInst, call: CommonCallExpr): CallPlan = error("Use cached method forms")
        override fun isSummaryBase(base: AccessPathBase) = true
    }
    private fun directed(direction: Direction) = DirectedForms(interp, direction) { method ->
        object : MethodForms {
            override fun statement(direction: Direction, s: CommonInst): StatementSummary {
                calls += "${method.context}:statement:$direction:${s.location.index}"
                return rules.summary
            }
            override fun call(direction: Direction, s: CommonInst, call: CommonCallExpr): CallPlan {
                assertSame(callExpr, call)
                calls += "${method.context}:call:$direction:${s.location.index}"
                return plan
            }
            override fun entryRules(direction: Direction): RuleStatement {
                calls += "entry:$direction"
                return rules
            }
            override fun exitRules(direction: Direction, exit: CommonInst): ExitRules {
                calls += "exit:$direction:${exit.location.index}"
                return exits
            }
        }
    }

    @Test fun `directed start and end nodes distinguish backward zero and normal exits`(): Unit {
        val forward = directed(Direction.FORWARD)
        val backward = directed(Direction.BACKWARD)
        for (zero in listOf(false, true)) assertEquals(listOf(entry), forward.startNodes(fixture.method, zero))
        assertEquals(listOf(normal), backward.startNodes(fixture.method, false))
        assertEquals(listOf(normal, exceptional), backward.startNodes(fixture.method, true))
        assertEquals(listOf(normal), forward.endNodes(fixture.method))
        assertEquals(listOf(entry), backward.endNodes(fixture.method))
    }

    @Test fun `directed boundaries select cached rule orientation and remove backward exit cleanup`(): Unit {
        val forward = directed(Direction.FORWARD)
        val backward = directed(Direction.BACKWARD)
        assertSame(rules, forward.startRules(fixture.method, entry))
        assertSame(rules, backward.startRules(fixture.method, exceptional))
        assertSame(exits, forward.endRules(fixture.method, normal))
        val backwardEnd = backward.endRules(fixture.method, entry)
        assertSame(rules, backwardEnd.rules)
        assertFalse(backwardEnd.globalStateDrop)
        assertTrue(backwardEnd.entryMarks.isEmpty())
        assertEquals(listOf("entry:FORWARD", "exit:BACKWARD:11", "exit:FORWARD:10", "entry:BACKWARD"), calls)
    }

    @Test fun `directed statement and call lookup retain each method key and direction`(): Unit {
        val other = fixture.key("other", 1)
        for (direction in Direction.entries) for (method in listOf(fixture.method, other)) {
            val directed = directed(direction)
            assertSame(rules.summary, directed.statement(method, normal))
            assertSame(plan, directed.call(method, normal, callExpr))
        }
        assertEquals(Direction.entries.flatMap { direction -> listOf(fixture.method, other).flatMap { method ->
            listOf("${method.context}:statement:$direction:10", "${method.context}:call:$direction:10")
        } }, calls)
    }

    @Test fun `default method callbacks route all directed application scopes`(): Unit {
        for (direction in Direction.entries) {
            val directed = directed(direction)
            assertEquals(17, directed.withStatement(fixture.method, normal) {
                assertSame(rules.summary, it); 17
            })
            assertEquals(23, directed.withCall(fixture.method, normal, callExpr) {
                assertSame(plan, it); 23
            })
            assertEquals(31, directed.withStartRules(fixture.method, normal) {
                assertSame(rules, it); 31
            })
            assertEquals(41, directed.withEndRules(fixture.method, normal) {
                assertSame(rules, it.rules)
                assertEquals(direction == Direction.FORWARD, it.globalStateDrop)
                41
            })
        }
        val context = fixture.method.context
        assertEquals(listOf("$context:statement:FORWARD:10", "$context:call:FORWARD:10", "entry:FORWARD", "exit:FORWARD:10",
            "$context:statement:BACKWARD:10", "$context:call:BACKWARD:10", "exit:BACKWARD:10", "entry:BACKWARD"), calls)
    }

    @Test fun `directed callbacks use overriding scopes instead of compatibility getters`(): Unit {
        val scoped = object : MethodForms {
            override fun statement(direction: Direction, s: CommonInst): StatementSummary = error("Unpinned getter")
            override fun call(direction: Direction, s: CommonInst, call: CommonCallExpr): CallPlan = error("Unpinned getter")
            override fun entryRules(direction: Direction): RuleStatement = error("Unpinned getter")
            override fun exitRules(direction: Direction, exit: CommonInst): ExitRules = error("Unpinned getter")
            override fun <R> withStatement(direction: Direction, s: CommonInst, use: (StatementSummary) -> R): R {
                calls += "statement:$direction"; return use(rules.summary)
            }
            override fun <R> withCall(direction: Direction, s: CommonInst, call: CommonCallExpr, use: (CallPlan) -> R): R {
                calls += "call:$direction"; return use(plan)
            }
            override fun <R> withEntryRules(direction: Direction, use: (RuleStatement) -> R): R {
                calls += "entry:$direction"; return use(rules)
            }
            override fun <R> withExitRules(direction: Direction, exit: CommonInst, use: (ExitRules) -> R): R {
                calls += "exit:$direction"; return use(exits)
            }
        }
        for (direction in Direction.entries) {
            val directed = DirectedForms(interp, direction) { scoped }
            directed.withStatement(fixture.method, normal) { assertSame(rules.summary, it) }
            directed.withCall(fixture.method, normal, callExpr) { assertSame(plan, it) }
            directed.withStartRules(fixture.method, normal) { assertSame(rules, it) }
            directed.withEndRules(fixture.method, normal) { assertSame(rules, it.rules) }
        }
        assertEquals(listOf("statement:FORWARD", "call:FORWARD", "entry:FORWARD", "exit:FORWARD",
            "statement:BACKWARD", "call:BACKWARD", "exit:BACKWARD", "entry:BACKWARD"), calls)
    }
}
