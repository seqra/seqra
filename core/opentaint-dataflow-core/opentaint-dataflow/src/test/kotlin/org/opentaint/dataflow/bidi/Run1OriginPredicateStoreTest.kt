package org.opentaint.dataflow.bidi

import org.opentaint.dataflow.ap.ifds.AccessPathBase
import org.opentaint.dataflow.bidi.ap.*
import org.opentaint.dataflow.bidi.fixtures.StoreFixtures
import org.opentaint.dataflow.bidi.interp.*
import org.opentaint.dataflow.bidi.store.ConjunctionStore
import org.opentaint.dataflow.bidi.store.Run1OriginPredicateStore
import kotlin.random.Random
import kotlin.test.*

class Run1OriginPredicateStoreTest {
    private val fixture = StoreFixtures()
    private val manager = fixture.manager
    private val statement = fixture.inst(1)
    private val rule = RuleOccurrenceId(FormKey(FormOwner(), fixture.commonMethod, fixture.method, statement),
        RuleKind.CALL_SOURCE, 0)
    private fun pattern(i: Int) = fixture.pattern(base = AccessPathBase.Argument(i))
    private fun origin(i: Int) = manager.initial(pattern(i))
    private fun input(i: Int, layer: Layer = Layer.NORMAL) = ConjunctionStore.Input(origin(i), layer)
    private fun build(root: (RulePredicate.Builder, List<Int>) -> Int): RulePredicate {
        val builder = RulePredicate.Builder(fixture.cancellation)
        val leaves = List(4) { builder.leaf(pattern(it)) }
        return builder.build(root(builder, leaves))
    }
    private fun ordinal(predicate: RulePredicate, i: Int) = predicate.leaves.indexOf(pattern(i))
    private data class Arm(val proof: ProofKey, val layer: Layer, val initial: InitialAp)
    private fun Run1OriginPredicateStore.accept(predicate: RulePredicate, leaf: Int, value: ConjunctionStore.Input,
                                                id: RuleOccurrenceId = rule, action: Any = "action"): List<Arm> =
        ArrayList<Arm>().also { arms ->
            add(id, statement, predicate, ordinal(predicate, leaf), value) { cube ->
                cube.emitOrigins(action) { arms += Arm(cube.proof, cube.layer, it) }
            }
        }

    /** Small independent scalar interpreter: enumerate complete accepted bindings, then split origins.
     * It uses no result-store helpers or premise-combination policy. It also keeps full OR choice IDs. */
    private data class Binding(val inputs: Map<Int, ConjunctionStore.Input>, val choices: Set<Pair<Int, Int>>)
    private fun scalar(predicate: RulePredicate, pool: Map<Int, List<ConjunctionStore.Input>>): Set<Arm> {
        fun compatible(a: Binding, b: Binding): Binding? {
            val inputs = a.inputs.toMutableMap()
            for ((leaf, value) in b.inputs) {
                val previous = inputs[leaf]
                if (previous != null && (previous.premise !== value.premise || previous.layer != value.layer ||
                        previous.facts?.groupKey != value.facts?.groupKey)) return null
                inputs[leaf] = value
            }
            return Binding(inputs, a.choices + b.choices)
        }
        val evaluated = HashMap<Int, List<Binding>>()
        fun eval(node: Int): List<Binding> = evaluated.getOrPut(node) {
            val children = ArrayList<Int>().also { predicate.childrenOf(node, it::add) }
            when (predicate.kind(node)) {
                RulePredicate.Kind.FALSE -> emptyList()
                RulePredicate.Kind.TRUE -> listOf(Binding(emptyMap(), emptySet()))
                RulePredicate.Kind.LEAF -> {
                    val leaf = predicate.leafOrdinal(node)
                    pool[leaf].orEmpty().map { Binding(mapOf(leaf to it), emptySet()) }
                }
                RulePredicate.Kind.OR -> children.flatMap { child ->
                    eval(child).map { Binding(it.inputs, it.choices + (node to child)) }
                }
                RulePredicate.Kind.AND -> children.fold(listOf(Binding(emptyMap(), emptySet()))) { bindings, child ->
                    bindings.flatMap { left -> eval(child).mapNotNull { right -> compatible(left, right) } }
                }
            }
        }
        return eval(predicate.root).flatMapTo(HashSet()) { binding ->
            val proof = ProofKey(rule, binding.inputs.keys.sorted(), binding.choices
                .sortedWith(compareBy<Pair<Int, Int>> { it.first }.thenBy { it.second })
                .flatMap { listOf(it.first, it.second) })
            val layer = if (binding.inputs.values.any { it.layer == Layer.DEMAND }) Layer.DEMAND else Layer.NORMAL
            val nonzero = binding.inputs.values.map { it.premise as InitialAp }.filterNot { it.isZero }.toSet()
            (nonzero.ifEmpty { setOf(manager.zero) }).map { Arm(proof, layer, it) }
        }
    }

    @Test fun `factored NNF matches independent scalar bindings after every arrival`() {
        val predicates = listOf(
            build { b, l -> b.and(listOf(l[0], l[1], l[2])) },
            build { b, l -> b.or(listOf(b.and(listOf(l[0], l[1])), b.and(listOf(l[2], l[3])))) },
            build { b, l -> b.and(listOf(b.or(listOf(l[0], l[1])), l[0])) },
            build { b, l ->
                val shared = b.or(listOf(l[0], l[1]))
                b.and(listOf(shared, b.or(listOf(shared, l[2]))))
            },
            build { b, l -> b.and(listOf(b.or(listOf(l[0], l[1])), b.or(listOf(l[0], l[2])))) },
        )
        for ((predicateIndex, predicate) in predicates.withIndex()) repeat(6) { order ->
            val events = predicate.leaves.indices.flatMap { leaf ->
                listOf(input(10 + leaf), input(20 + leaf, Layer.DEMAND), ConjunctionStore.Input(manager.zero, Layer.NORMAL))
                    .map { leaf to it }
            }.shuffled(Random(100 * predicateIndex + order))
            val store = Run1OriginPredicateStore(manager)
            val pool = HashMap<Int, MutableList<ConjunctionStore.Input>>()
            val observed = HashSet<Arm>()
            for ((leaf, value) in events) {
                pool.getOrPut(leaf) { ArrayList() }.add(value)
                store.add(rule, statement, predicate, leaf, value) { cube ->
                    cube.emitOrigins("action") { observed.add(Arm(cube.proof, cube.layer, it)) }
                }
                assertEquals(scalar(predicate, pool), observed, "predicate=$predicateIndex order=$order leaf=$leaf")
            }
        }
    }

    @Test fun `zero requires one complete cube and never pools unrelated OR leaves`() {
        val predicate = build { b, l -> b.or(listOf(b.and(listOf(l[0], l[1])), b.and(listOf(l[2], l[3])))) }
        val store = Run1OriginPredicateStore(manager)
        val zero = ConjunctionStore.Input(manager.zero, Layer.NORMAL)
        assertTrue(store.accept(predicate, 0, zero).isEmpty())
        assertEquals(setOf(origin(1)), store.accept(predicate, 1, input(1)).map { it.initial }.toSet())
        assertTrue(store.accept(predicate, 3, zero).isEmpty())
        assertEquals(setOf(origin(2)), store.accept(predicate, 2, input(2)).map { it.initial }.toSet())
        assertEquals(setOf(manager.zero), store.accept(predicate, 1, zero).map { it.initial }.toSet())
        assertTrue(store.accept(predicate, 1, zero).isEmpty())
    }

    @Test fun `late origins and late zero emit once while checked annotation growth emits no fixed arm`() {
        val predicate = build { b, l -> b.and(listOf(l[0], l[1])) }
        val statistics = FormCacheStatistics()
        val store = Run1OriginPredicateStore(manager, statistics)
        val a = input(0).copy(facts = fixture.fact(base = AccessPathBase.Argument(0)))
        val b = input(1).copy(facts = fixture.fact(base = AccessPathBase.Argument(1)))
        assertTrue(store.accept(predicate, 0, a).isEmpty())
        assertEquals(setOf(origin(0), origin(1)), store.accept(predicate, 1, b).map { it.initial }.toSet())
        val growth = a.copy(facts = fixture.fact(base = AccessPathBase.Argument(0), path = listOf(fixture.field("child"))))
        assertTrue(store.accept(predicate, 0, growth).isEmpty())
        val afterGrowth = statistics.snapshot()
        assertTrue(store.accept(predicate, 0, growth).isEmpty())
        assertTrue(store.accept(predicate, 0, a).isEmpty())
        assertEquals(afterGrowth, statistics.snapshot(), "Unchanged accepted input cannot reschedule parents")
        assertEquals(listOf(origin(2)), store.accept(predicate, 0, a.copy(premise = origin(2))).map { it.initial })
        assertTrue(store.accept(predicate, 0, a.copy(premise = manager.zero)).isEmpty())
        assertEquals(listOf(manager.zero), store.accept(predicate, 1, b.copy(premise = manager.zero)).map { it.initial })
    }

    @Test fun `result action scopes and statement scopes do not share origin admission`() {
        val predicate = build { b, l -> b.and(listOf(l[0], l[1])) }
        val store = Run1OriginPredicateStore(manager)
        store.accept(predicate, 0, input(0))
        val byAction = HashMap<String, List<InitialAp>>()
        store.add(rule, statement, predicate, ordinal(predicate, 1), input(1)) { cube ->
            // An action rejected by the existing source-seed test does not call emitOrigins.
            for (action in listOf("first", "second")) {
                val emitted = ArrayList<InitialAp>()
                cube.emitOrigins(action, emitted::add)
                byAction[action] = emitted
            }
            cube.emitOrigins("first") { fail("A repeated action must not emit the same origin") }
        }
        assertEquals(setOf(origin(0), origin(1)), byAction.getValue("first").toSet())
        assertEquals(byAction["first"], byAction["second"])
        val other = fixture.inst(2)
        val arms = ArrayList<InitialAp>()
        store.add(rule, other, predicate, ordinal(predicate, 0), input(0)) { fail("Partial statement") }
        store.add(rule, other, predicate, ordinal(predicate, 1), input(1)) { it.emitOrigins("first", arms::add) }
        assertEquals(setOf(origin(0), origin(1)), arms.toSet())
        assertTrue(store.accept(predicate, 0, input(0), id = rule.copy(ordinal = 1)).isEmpty())
    }

    @Test fun `shared leaf must agree on full fact header and initial before a cube is ready`() {
        val predicate = build { b, l -> b.and(listOf(b.or(listOf(l[0], l[1])), l[0])) }
        val store = Run1OriginPredicateStore(manager)
        val a = input(0).copy(facts = fixture.fact(base = AccessPathBase.Argument(0)))
        val first = store.accept(predicate, 0, a)
        assertEquals(listOf(origin(0)), first.map { it.initial })
        val different = input(1, Layer.DEMAND).copy(facts = fixture.fact(base = AccessPathBase.Argument(1), layer = Layer.DEMAND))
        val second = store.accept(predicate, 0, different)
        assertEquals(listOf(origin(1)), second.map { it.initial }, "Different headers cannot bind repeated leaf across operands")
        val zero = a.copy(premise = manager.zero)
        val allZero = store.accept(predicate, 0, zero)
        assertEquals(listOf(manager.zero), allZero.map { it.initial })
    }

    @Test fun `many origins retain one supported shape per header not an origin product`() {
        val predicate = build { b, l -> b.and(l) }
        val statistics = FormCacheStatistics()
        val store = Run1OriginPredicateStore(manager, statistics)
        val observed = HashSet<InitialAp>()
        val count = 64
        for (leaf in predicate.leaves.indices) repeat(count) { index ->
            store.add(rule, statement, predicate, leaf, input(index)) { cube ->
                cube.emitOrigins("action") { assertTrue(observed.add(it)) }
            }
        }
        assertEquals((0 until count).map(::origin).toSet(), observed)
        val counts = statistics.snapshot()
        assertEquals(5L, counts.observedSupportShapes, "Four leaf domains plus their AND root")
        assertEquals(5L, counts.observedProofRecords)
        val stable = statistics.snapshot()
        for (leaf in predicate.leaves.indices) repeat(count) { index ->
            store.add(rule, statement, predicate, leaf, input(index)) { fail("Duplicate input cannot publish") }
        }
        assertEquals(stable, statistics.snapshot())
        val sets = manager.javaClass.getDeclaredField("premiseSets").apply { isAccessible = true }.get(manager) as Map<*, *>
        assertTrue(sets.isEmpty(), "Factored roots cannot create executable ND premises")
    }

    @Test fun `fixed independent headers visit n plus m result memberships not n times m bindings`() {
        val predicate = build { b, l -> b.and(listOf(l[0], l[1])) }
        val store = Run1OriginPredicateStore(manager)
        val n = 80
        val m = 96
        val observed = HashSet<InitialAp>()
        repeat(n) { index ->
            store.add(rule, statement, predicate, ordinal(predicate, 0), input(index)) { fail("Missing second slot") }
        }
        assertEquals(0L, store.originMembershipVisits)
        repeat(m) { index ->
            store.add(rule, statement, predicate, ordinal(predicate, 1), input(n + index)) { cube ->
                cube.emitOrigins("action") { assertTrue(observed.add(it), "Fixed result arm can emit once") }
            }
        }
        assertEquals(n + m, observed.size)
        assertEquals((n + m).toLong(), store.originMembershipVisits)
        assertTrue(store.originMembershipVisits < n.toLong() * m)
    }

    @Test fun `deep shared proofs and equivalent predicate rebuilds use iterative propagation`() {
        fun predicate(): RulePredicate {
            val builder = RulePredicate.Builder(fixture.cancellation)
            val a = builder.leaf(pattern(0))
            val b = builder.leaf(pattern(1))
            var root = a
            repeat(4096) { depth ->
                root = if (depth % 2 == 0) builder.or(listOf(root, b)) else builder.and(listOf(root, a))
            }
            return builder.build(root)
        }
        val store = Run1OriginPredicateStore(manager)
        val first = predicate()
        assertEquals(listOf(origin(0)), store.accept(first, 0, input(0)).map { it.initial })
        val rebuilt = predicate()
        assertTrue(store.accept(rebuilt, 0, input(0)).isEmpty())
        assertEquals(listOf(origin(2)), store.accept(rebuilt, 0, input(2)).map { it.initial })
    }

    @Test fun `run1 predicate rejects ND inputs exclusions and other manager originals`() {
        val predicate = build { _, l -> l[0] }
        val store = Run1OriginPredicateStore(manager)
        assertFailsWith<IllegalStateException> {
            store.accept(predicate, 0, ConjunctionStore.Input(manager.union(origin(0), origin(1)), Layer.NORMAL))
        }
        val other = StoreFixtures().manager
        assertFailsWith<IllegalStateException> {
            store.accept(predicate, 0, ConjunctionStore.Input(other.initial(pattern(0)), Layer.NORMAL))
        }
        val excluded = manager.initial(fixture.pattern(tail = Tail.STAR,
            exclusion = ExclusionSet.of(fixture.field("child"))))
        assertFailsWith<IllegalStateException> { store.accept(predicate, 0, ConjunctionStore.Input(excluded, Layer.NORMAL)) }
    }
}
